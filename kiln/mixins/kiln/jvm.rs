// Kiln's JVM: Java in the game's process (D-112), or in a process of its own beside the game's. Either way Kiln talks
// to `kiln.Host` in lines, a command a line (kiln/java/kiln/Host.java has the protocol): in the game's process through
// two JNI calls (the crate kiln-jni, a `[[crate]]` of Kiln's, as horizon-client forbids `unsafe`), in a process of
// its own over its stdin and stdout. Nothing but this file knows which runs: the process is the fallback, with
// `KILN_JVM=process`, or when the JVM can't be loaded or made. The window's game never waits on Java: one thread writes
// the commands from a bounded queue, another reads the replies into a channel the game drains each tick, so a
// slow or stuck mod never stalls the game. A mod stuck for longer than the queue holds stops Java.
//
// The integrated server's game asks Java things and waits for the answer (`ServerLink::call`), at most a
// timeout: its answers (`return`) come on a channel of their own, and so do Java's questions about the world while a
// call runs (`ask`), which the waiting server answers.

use std::collections::VecDeque;
use std::ffi::OsString;
use std::io::{BufRead, BufReader, Write};
use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::sync::mpsc::{
    Receiver, RecvTimeoutError, Sender, SyncSender, TrySendError, channel, sync_channel,
};
use std::sync::{Arc, Condvar, Mutex};
use std::thread::ThreadId;
use std::time::{Duration, Instant};

/// Commands waiting for Java to read them: about a minute of ticks. A pipe holds only 64 KiB and a write
/// to a full one blocks, so the game never writes to Java's stdin itself.
pub const QUEUE: usize = 1200;

/// The log the game's Java writes the mods' own output to, in Kiln's folder.
pub const JAVA_LOG: &str = "java.log";

/// D-112's breadcrumb beside it: the mod and call running in each of Java's threads (kiln/java/kiln/Breadcrumb.java).
pub const LAST_CALL: &str = "last-call";

/// The log of the Java Kiln starts again with the overlay's "Restart Java now" (D-134), in a process of its own: not
/// `java.log`, which the game's first Java, still loaded and idle in the game's process, keeps open.
pub const RESTARTED_LOG: &str = "java-restarted.log";

/// The breadcrumb of the Java started again: a file of its own, so the two Javas never write one record. The crash
/// window reads only [`LAST_CALL`]: only Java in the game's process can take the game down with it.
pub const RESTARTED_LAST_CALL: &str = "last-call-restarted";

/// The options of D-112's crash policy for a Java whose log is `log` in `dir`: the breadcrumb for the game's Java only
/// (Kiln's loot run's Java has a log of its own and no breadcrumb; a Java started again has a breadcrumb of its own),
/// and Java's own crash report in Kiln's folder, where the crash window reads it, rather than in the game's folder.
pub fn crash_options(dir: &Path, log: &str) -> Vec<OsString> {
    let mut options = Vec::new();
    let breadcrumb = match log {
        JAVA_LOG => Some(LAST_CALL),
        RESTARTED_LOG => Some(RESTARTED_LAST_CALL),
        _ => None,
    };
    if let Some(file) = breadcrumb {
        let mut last_call = OsString::from("-Dkiln.last_call=");
        last_call.push(dir.join(file));
        options.push(last_call);
    }
    let mut error_file = OsString::from("-XX:ErrorFile=");
    error_file.push(dir.join("hs_err_pid%p.log"));
    options.push(error_file);
    options
}

/// The game's prepared resources folder, as the window said (`set_resources`); none in tests.
static RESOURCES: std::sync::Mutex<Option<PathBuf>> = std::sync::Mutex::new(None);

/// The window's game: where its prepared resources are, for Java started after.
pub fn set_resources(path: &Path) {
    if let Ok(mut r) = RESOURCES.lock()
        && r.as_deref() != Some(path)
    {
        *r = Some(path.to_path_buf());
    }
}

/// `-Dkiln.resources=<folder>`, empty when the window hasn't said.
fn resources_property() -> OsString {
    let mut property = OsString::from("-Dkiln.resources=");
    if let Some(path) = RESOURCES.lock().ok().and_then(|r| r.clone()) {
        property.push(path);
    }
    property
}

/// What Java said.
#[derive(Clone, Debug, PartialEq)]
pub enum Reply {
    /// It started: its version.
    Ready(String),
    /// A mod's entrypoint class ran.
    Loaded(String),
    /// A mod's entrypoint class didn't run, and why.
    Failed(String, String),
    /// What the Java mods draw on the HUD changed: all of it, in order.
    Draw(Vec<HudOp>),
    /// A mod's own screen opened (whether it pauses the game, its class) or closed (`screen open|close`, D-094).
    Screen(Option<(bool, String)>),
    /// What the mod's own screen draws changed: all of it, in order.
    ScreenDraw(Vec<HudOp>),
    /// What the mods draw over the game's container screen (D-156), whether they take the keys, and where they take
    /// the mouse (`odraw <keys 0|1> <x,y,w,h;...> <op>...`).
    OverDraw(Vec<HudOp>, bool, Vec<[f32; 4]>),
    /// The mods with a settings page (a Mod Menu config screen), by mod id: all of them, when that changed.
    Settings(Vec<String>),
    /// A line for the game's log.
    Log(String),
    /// A line for the player's chat.
    Chat(String),
    /// A mod ran Java out of its heap or stack and Java turned it off now (D-112, `kiln.SoftSwitch.trip`): its id, for
    /// the game to turn its switch in Options > Mods off too.
    Trip(String),
    /// Kiln's overlay turned a mod on (true) or off (D-113): its id, for the game to flip its switch in Options > Mods.
    Switch(String, bool),
    /// Kiln's overlay asks the game to show a folder of Kiln's: `mods` (the only one yet), in the system's file manager.
    Open(String),
    /// A mod's line for the player's chat as vanilla's JSON, with its style (colour, bold, italic...).
    ChatComponent(serde_json::Value),
    /// The mods' items with a use of their own: all of them, when that changed.
    Items(Vec<String>),
    /// The server events the mods hear (`join`, `respawn`): all of them, when that changed.
    Listens(Vec<String>),
    /// The pools the mods add to a loot table (its id, the pools as the loot table format's JSON array).
    Loot(String, String),
    /// The answer to a server call.
    Return(Return),
    /// During a server call (its number), Java asks the game something (`block <x> <y> <z>`).
    Ask(u64, Vec<String>),
    /// The mods' blocks with code of their own, each `<id> <what>[,<what>]`: all of them, when that changed.
    Blocks(Vec<String>),
    /// Horizon's hooks to hand Java, for the game's methods the mods' mixins go into (Kiln's Mixin bridge, D-102,
    /// kiln/java/kiln/bridge.json): all of them, when that changed.
    Bridge(Vec<String>),
    /// What the mods changed of the game's state Horizon owns, by key (`fov 30`, `flying_speed 0.1`): kiln/java/kiln/Back.java
    /// has the keys (D-105).
    Back(Vec<(String, String)>),
    /// The client commands the mods registered (`ClientCommandRegistrationCallback`), by name: all of them, when
    /// that changed.
    Commands(Vec<String>),
    /// The keys the mods registered (`KeyMappingHelper.registerKeyMapping`) since Java last said: the entrypoint
    /// class that registered them (empty for keys registered later), then each key's name, its group's name, its label
    /// and the key the mod set (`key.keyboard.v`). An entrypoint with none says so too.
    Keys(String, Vec<[String; 4]>),
    /// A mod set its key itself (`KeyMapping.setKey`): the key's name and the key (`key.keyboard.b`), which the game
    /// keeps as the player's choice on the mod's page in Options > Mods.
    KeyChosen(String, String),
    /// A command a mod sends as the window's player (`ClientPacketListener.sendCommand`), without its slash:
    /// attended (typed, signed) or not.
    Send(bool, String),
    /// A chat message a mod sends as the window's player (`ClientPacketListener.sendChat`): the game sends it as
    /// typed in the chat (a message starting with a slash is a command).
    SendChat(String),
    /// Text a mod copied (`KeyboardHandler.setClipboard`): onto the system's clipboard, through the game's (D-174).
    Clipboard(String),
    /// A stack a mod throws out of a slot of the open menu (`MultiPlayerGameMode.handleContainerInput` with THROW):
    /// the slot's number in the menu, and the button (0 one of it, 1 all of it).
    ThrowSlot(usize, i32),
    /// A chunk whose blocks a mod reads (`want_chunk x z`, kiln/java/kiln/ClientChunks.java, D-186): the game sends
    /// its sections with its next tick.
    WantChunk(i32, i32),
    /// A sound, particle or toast a mod played for the window's player.
    Play(Play),
    /// A mod's payload for the server (`ClientPlayNetworking.send`): the message for Horizon's channel for mods, its
    /// type's id then the payload (kiln/java/kiln/Net.java).
    Net(Vec<u8>),
    /// Kiln's overlay asks for Java to be started again now ("Restart Java now", D-134): the memory that Java measured
    /// itself to hold, in bytes, when it runs in the game's process (it stays there); 0 otherwise.
    Restart(u64),
    /// The banner after a restart asks the game to start itself again and close (D-134).
    RestartGame,
    /// A mod's GUI picture for the game's atlas (D-160): its name, width, height and RGBA pixels.
    Sprite(String, u32, u32, Vec<u8>),
    /// A command was done (its name).
    Done(String),
    /// The Java process ended: why.
    Exited(String),
}

/// What a mod plays for the window's player, for the game to play with the next tick.
#[derive(Clone, Debug, PartialEq)]
pub enum Play {
    /// A sound by its id (`minecraft:ui.button.click`): at a place in the level, or the player's own (the UI's)
    /// without one.
    Sound {
        id: String,
        at: Option<[f64; 3]>,
        volume: f32,
        pitch: f32,
    },
    /// A particle by its type's id (`minecraft:happy_villager`), at a place, moving.
    Particle {
        id: String,
        at: [f64; 3],
        velocity: [f64; 3],
    },
    /// A system toast: its title, its message (none when empty), how long it shows in milliseconds, and what it is
    /// about (its token): to `replace`, one of that token still shown takes the title and message instead.
    Toast {
        title: String,
        message: String,
        millis: f64,
        token: String,
        replace: bool,
    },
}

/// Java's answer to a server call: the call's number, the result and what the mod's code did (kiln/java/kiln/Server.java
/// has their forms).
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Return {
    pub call: u64,
    pub result: String,
    pub effects: Vec<String>,
}

/// `\`, tab, newline and carriage return escaped, as Host.java reads them.
pub fn escape(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    for c in s.chars() {
        match c {
            '\\' => out.push_str("\\\\"),
            '\t' => out.push_str("\\t"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            c => out.push(c),
        }
    }
    out
}

pub fn unescape(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    let mut chars = s.chars();
    while let Some(c) = chars.next() {
        if c != '\\' {
            out.push(c);
            continue;
        }
        match chars.next() {
            Some('t') => out.push('\t'),
            Some('n') => out.push('\n'),
            Some('r') => out.push('\r'),
            Some(other) => out.push(other),
            None => out.push('\\'),
        }
    }
    out
}

/// Bytes as lowercase hex, two digits each, as Java's `HexFormat` writes them.
pub fn hex(bytes: &[u8]) -> String {
    let mut out = String::with_capacity(bytes.len() * 2);
    for b in bytes {
        out.push_str(&format!("{b:02x}"));
    }
    out
}

/// Hex back into bytes; None for an odd length or a character that isn't a hex digit.
pub fn unhex(text: &str) -> Option<Vec<u8>> {
    if !text.len().is_multiple_of(2) {
        return None;
    }
    let digit = |c: u8| (c as char).to_digit(16).map(|d| d as u8);
    text.as_bytes()
        .chunks(2)
        .map(|pair| Some((digit(pair[0])? << 4) | digit(pair[1])?))
        .collect()
}

/// One thing a Java mod drew on the HUD, in GUI pixels (fractions where a mod scaled what it draws); colours are
/// ARGB.
#[derive(Clone, Debug, PartialEq)]
pub enum HudOp {
    Fill {
        x: f32,
        y: f32,
        w: f32,
        h: f32,
        argb: u32,
    },
    /// Text with its top left at (x, y), `scale` times the font's size.
    Text {
        x: f32,
        y: f32,
        scale: f32,
        argb: u32,
        shadow: bool,
        text: String,
    },
    /// An item's icon (`minecraft:diamond_helmet`), `size` pixels square, its top left at (x, y).
    Item {
        x: f32,
        y: f32,
        size: f32,
        item: String,
    },
    /// A whole stack's icon (its SNBT: id, count, components), `size` pixels square, its top left at (x, y): the game
    /// draws it as that stack, an enchanted book with its glint, a potion in its colour, armor with its trim (D-164).
    Stack {
        x: f32,
        y: f32,
        size: f32,
        snbt: String,
    },
    /// Text with formats (bold, italic, underlined, struck through, obfuscated) and colours, `scale` times the font's
    /// size: the game's text JSON whose parts are literal (Java resolved translations), `argb` the colour of a part
    /// without one (D-164). `text` is its plain text.
    Styled {
        x: f32,
        y: f32,
        scale: f32,
        argb: u32,
        shadow: bool,
        text: String,
        json: serde_json::Value,
    },
    /// What is drawn after it is cut to the box from (x0, y0) to (x1, y1) (the GUI scissor), or, with none, not cut.
    Clip(Option<[f32; 4]>),
    /// A sprite of the game's GUI (`gui/sprites/widget/button`) drawn into the box `[x, y, w, h]`, its edges `border`
    /// pixels wide kept (nine-sliced); a box of `fallback` where the game has no such sprite. Its pixels are
    /// multiplied by `tint` (ARGB; 0xFFFFFFFF none), as vanilla's `blitSprite(..., color)` (a fluid's, D-168).
    Sprite {
        rect: [f32; 4],
        border: f32,
        fallback: u32,
        tint: u32,
        name: String,
    },
    /// An entity type's model (`minecraft:cow`) drawn into the box `[x0, y0, x1, y1]`, `size` pixels a block, raised
    /// by `offset_y` blocks, turned toward the pointer at `mouse`: vanilla's `extractEntityInInventoryFollowsMouse`
    /// (Just Enough Breeding's mobs, D-173), built from `saved` (the entity as the game saves one, SNBT; empty for the
    /// type alone) as the game loads a saved mob: a sheep's colour, a baby, a variant (D-179).
    Entity {
        rect: [f32; 4],
        size: f32,
        offset_y: f32,
        mouse: [f32; 2],
        id: String,
        saved: String,
    },
}

/// A draw op as GuiGraphicsExtractor.java writes it: `f x y w h argb`, `t x y argb shadow text`, `s x y scale argb
/// shadow text` (scaled text), `j x y scale argb shadow json` (styled text), `i x y size item`, `I x y size snbt` (a
/// whole stack), `b x y w h border fallback sprite` (a GUI sprite), `B x y w h border fallback tint sprite` (one
/// tinted), or `c x0 y0 x1 y1` (cut what follows to the box)
/// and `c` (stop), and `e x0 y0 x1 y1 size offset_y mouse_x mouse_y entity_type [snbt]` (an entity's model in a box).
pub fn parse_op(op: &str) -> Option<HudOp> {
    if op == "c" {
        return Some(HudOp::Clip(None));
    }
    let (kind, rest) = op.split_once(' ')?;
    // The text is the last field and may hold spaces.
    let fields = match kind {
        "s" | "j" => 6,
        "b" => 7,
        "B" => 8,
        "e" => 10,
        _ => 5,
    };
    let mut parts = rest.splitn(fields, ' ');
    let mut number = || parts.next()?.parse::<f64>().ok();
    match kind {
        "f" => Some(HudOp::Fill {
            x: number()? as f32,
            y: number()? as f32,
            w: number()? as f32,
            h: number()? as f32,
            argb: number()? as u32,
        }),
        "t" | "s" => {
            let (x, y) = (number()? as f32, number()? as f32);
            let scale = if kind == "s" { number()? as f32 } else { 1.0 };
            let (argb, shadow) = (number()? as u32, number()? == 1.0);
            Some(HudOp::Text {
                x,
                y,
                scale,
                argb,
                shadow,
                text: parts.next().unwrap_or_default().to_owned(),
            })
        }
        "i" => {
            let (x, y, size) = (number()? as f32, number()? as f32, number()? as f32);
            let item = parts.next()?.to_owned();
            (!item.is_empty()).then_some(HudOp::Item { x, y, size, item })
        }
        "I" => {
            let (x, y, size) = (number()? as f32, number()? as f32, number()? as f32);
            let snbt = parts.next()?.to_owned();
            (!snbt.is_empty()).then_some(HudOp::Stack { x, y, size, snbt })
        }
        "j" => {
            let (x, y, scale) = (number()? as f32, number()? as f32, number()? as f32);
            let (argb, shadow) = (number()? as u32, number()? == 1.0);
            let json: serde_json::Value = serde_json::from_str(parts.next()?).ok()?;
            let text = plain_text(&json);
            Some(HudOp::Styled {
                x,
                y,
                scale,
                argb,
                shadow,
                text,
                json,
            })
        }
        "b" | "B" => {
            let rect = [
                number()? as f32,
                number()? as f32,
                number()? as f32,
                number()? as f32,
            ];
            let (border, fallback) = (number()? as f32, number()? as u32);
            let tint = if kind == "B" {
                number()? as u32
            } else {
                u32::MAX
            };
            let name = parts.next()?.to_owned();
            (!name.is_empty()).then_some(HudOp::Sprite {
                rect,
                border,
                fallback,
                tint,
                name,
            })
        }
        "e" => {
            let rect = [
                number()? as f32,
                number()? as f32,
                number()? as f32,
                number()? as f32,
            ];
            let (size, offset_y) = (number()? as f32, number()? as f32);
            let mouse = [number()? as f32, number()? as f32];
            let id = parts.next()?.to_owned();
            let saved = parts.next().unwrap_or_default().to_owned();
            (!id.is_empty()).then_some(HudOp::Entity {
                rect,
                size,
                offset_y,
                mouse,
                id,
                saved,
            })
        }
        "c" => Some(HudOp::Clip(Some([
            number()? as f32,
            number()? as f32,
            number()? as f32,
            number()? as f32,
        ]))),
        _ => None,
    }
}

/// The plain text of a text JSON whose parts are literal: its `text`, then its `extra`'s, in order.
fn plain_text(json: &serde_json::Value) -> String {
    match json {
        serde_json::Value::String(s) => s.clone(),
        serde_json::Value::Object(o) => {
            let mut out = o
                .get("text")
                .and_then(|t| t.as_str())
                .unwrap_or_default()
                .to_owned();
            for part in o
                .get("extra")
                .and_then(|e| e.as_array())
                .into_iter()
                .flatten()
            {
                out.push_str(&plain_text(part));
            }
            out
        }
        serde_json::Value::Array(parts) => parts.iter().map(plain_text).collect(),
        _ => String::new(),
    }
}

/// A reply line: a word and tab-separated, escaped fields.
pub fn parse(line: &str) -> Reply {
    let mut fields = line.split('\t');
    let kind = fields.next().unwrap_or("");
    match kind {
        "draw" => return Reply::Draw(fields.filter_map(|f| parse_op(&unescape(f))).collect()),
        "sdraw" => {
            return Reply::ScreenDraw(fields.filter_map(|f| parse_op(&unescape(f))).collect());
        }
        "odraw" => {
            let keys = fields.next() == Some("1");
            let claims = fields
                .next()
                .unwrap_or("")
                .split(';')
                .filter_map(|r| {
                    let v: Vec<f32> = r.split(',').filter_map(|n| n.parse().ok()).collect();
                    (v.len() == 4).then(|| [v[0], v[1], v[2], v[3]])
                })
                .collect();
            return Reply::OverDraw(
                fields.filter_map(|f| parse_op(&unescape(f))).collect(),
                keys,
                claims,
            );
        }
        "settings" => return Reply::Settings(fields.map(unescape).collect()),
        "items" => return Reply::Items(fields.map(unescape).collect()),
        "blocks" => return Reply::Blocks(fields.map(unescape).collect()),
        "ask" => {
            let call = fields.next().and_then(|c| c.parse().ok()).unwrap_or(0);
            return Reply::Ask(call, fields.map(unescape).collect());
        }
        "listens" => return Reply::Listens(fields.map(unescape).collect()),
        "bridge" => return Reply::Bridge(fields.map(unescape).collect()),
        "back" => {
            let fields: Vec<String> = fields.map(unescape).collect();
            return Reply::Back(
                fields
                    .chunks_exact(2)
                    .map(|p| (p[0].clone(), p[1].clone()))
                    .collect(),
            );
        }
        "commands" => return Reply::Commands(fields.map(unescape).collect()),
        "keys" => {
            let owner = fields.next().map(unescape).unwrap_or_default();
            let fields: Vec<String> = fields.map(unescape).collect();
            let keys = fields
                .chunks_exact(4)
                .map(|k| [k[0].clone(), k[1].clone(), k[2].clone(), k[3].clone()])
                .collect();
            return Reply::Keys(owner, keys);
        }
        "return" => {
            let call = fields.next().and_then(|c| c.parse().ok()).unwrap_or(0);
            let result = fields.next().map(unescape).unwrap_or_default();
            let effects = fields.map(unescape).collect();
            return Reply::Return(Return {
                call,
                result,
                effects,
            });
        }
        _ => {}
    }
    let mut next = || fields.next().map(unescape).unwrap_or_default();
    match kind {
        "ready" => Reply::Ready(next()),
        "loaded" => Reply::Loaded(next()),
        "failed" => {
            let class = next();
            Reply::Failed(class, next())
        }
        "loot" => {
            let id = next();
            Reply::Loot(id, next())
        }
        "chosen" => {
            let name = next();
            Reply::KeyChosen(name, next())
        }
        "log" => Reply::Log(next()),
        "sprite" => {
            let name = next();
            let (width, height) = (next().parse().unwrap_or(0), next().parse().unwrap_or(0));
            let rgba = crate::game::kiln_sprites::base64(&next()).unwrap_or_default();
            Reply::Sprite(name, width, height, rgba)
        }
        "screen" => match next().as_str() {
            "open" => {
                let pause = next() == "1";
                Reply::Screen(Some((pause, next())))
            }
            _ => Reply::Screen(None),
        },
        "chat" => Reply::Chat(next()),
        "trip" => Reply::Trip(next()),
        "switch" => {
            let id = next();
            Reply::Switch(id, next() == "1")
        }
        "open" => Reply::Open(next()),
        "chat_json" => {
            let json = next();
            match serde_json::from_str(&json) {
                Ok(component) => Reply::ChatComponent(component),
                Err(_) => Reply::Log(format!("unreadable chat component: {json}")),
            }
        }
        "send" => {
            let attended = next() == "1";
            Reply::Send(attended, next())
        }
        "send_chat" => Reply::SendChat(next()),
        "clip" => Reply::Clipboard(next()),
        "want_chunk" => match (next().parse(), next().parse()) {
            (Ok(x), Ok(z)) => Reply::WantChunk(x, z),
            _ => Reply::Log("unreadable chunk to send".into()),
        },
        "throw_slot" => match (next().parse(), next().parse()) {
            (Ok(slot), Ok(button)) => Reply::ThrowSlot(slot, button),
            _ => Reply::Log("unreadable slot to throw".into()),
        },
        // `sound <id> <x y z, or nothing> <volume> <pitch>`, `particle <id> <x y z> <dx dy dz>`, `toast <title>
        // <message> <milliseconds> <token> <replace 0|1>`.
        "sound" => {
            let id = next();
            let at = triple(&next());
            let (volume, pitch) = (next().parse().unwrap_or(1.0), next().parse().unwrap_or(1.0));
            Reply::Play(Play::Sound {
                id,
                at,
                volume,
                pitch,
            })
        }
        "particle" => {
            let id = next();
            match (triple(&next()), triple(&next())) {
                (Some(at), Some(velocity)) => Reply::Play(Play::Particle { id, at, velocity }),
                _ => Reply::Log(format!("unreadable particle: {line}")),
            }
        }
        "toast" => {
            let (title, message) = (next(), next());
            let millis = next().parse().unwrap_or(5000.0);
            let token = next();
            Reply::Play(Play::Toast {
                title,
                message,
                millis,
                token,
                replace: next() == "1",
            })
        }
        "net" => match unhex(&next()) {
            Some(data) => Reply::Net(data),
            None => Reply::Log(format!("unreadable payload: {line}")),
        },
        "done" => Reply::Done(next()),
        "restart" => Reply::Restart(next().parse().unwrap_or(0)),
        "restart_game" => Reply::RestartGame,
        _ => Reply::Log(format!("unknown reply: {line}")),
    }
}

/// Three finite numbers apart by spaces (a place, a motion); none otherwise.
fn triple(field: &str) -> Option<[f64; 3]> {
    let mut parts = field
        .split(' ')
        .map(|n| n.parse::<f64>().ok().filter(|v| v.is_finite()));
    let v = [parts.next()??, parts.next()??, parts.next()??];
    parts.next().is_none().then_some(v)
}

/// The `java` program inside a Java home.
pub fn java_program(home: &Path) -> PathBuf {
    java_program_on(kiln_jni::Os::HERE, home)
}

/// The `java` program inside a Java home on `os`.
pub fn java_program_on(os: kiln_jni::Os, home: &Path) -> PathBuf {
    let name = if os == kiln_jni::Os::Windows {
        "bin/java.exe"
    } else {
        "bin/java"
    };
    os.join(home, name)
}

/// Tests: Kiln's side of the "Java" a test just wrote to `home`. On Linux a fork in another test's thread can hold the
/// script open for writing for a moment, and starting it then fails with "Text file busy": it is tried again.
#[cfg(test)]
pub fn start_written(home: &Path) -> JavaHost {
    for _ in 0..100 {
        match JavaHost::start(home, &[], home) {
            Err(e) if e.contains("Text file busy") => std::thread::sleep(Duration::from_millis(10)),
            started => return started.unwrap(),
        }
    }
    panic!("{} stayed busy", java_program(home).display())
}

/// A command line: the command and its escaped fields, tab-separated.
pub fn command_line(command: &str, fields: &[&str]) -> String {
    let mut line = command.to_owned();
    for f in fields {
        line.push('\t');
        line.push_str(&escape(f));
    }
    line
}

/// Queues `line` for Java's stdin; never waits.
fn queue(commands: &SyncSender<String>, line: String) -> Result<(), String> {
    commands.try_send(line).map_err(|e| match e {
        TrySendError::Full(_) => {
            format!("Java hasn't read the last {QUEUE} commands: a mod is stuck")
        }
        TrySendError::Disconnected(_) => "Java isn't listening".to_owned(),
    })
}

/// How Kiln runs Java.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Transport {
    /// In the game's process, through JNI (D-112): once a process.
    InProcess,
    /// A process of its own.
    Process,
}

/// The way Kiln tries first: `KILN_JVM=process` for the separate process, otherwise in the game's process.
/// `KILN_JVM=inprocess` (the tests' check) allows no fallback. Without the variable, a file named `jvm` in Kiln's
/// folder `dir` holding `process` asks for the same: a game started from a launcher's window (a Mac's Dock, Finder)
/// gets no variable set in a terminal.
pub fn transport(dir: &Path) -> Transport {
    let file = || std::fs::read_to_string(dir.join(JVM_FILE)).ok();
    transport_of(std::env::var("KILN_JVM").ok().as_deref(), file)
}

/// The file in Kiln's folder that can ask for Java in a process of its own ([`transport`]).
pub const JVM_FILE: &str = "jvm";

/// [`transport`] from the variable's value, else the file's text (read only when the variable isn't set).
fn transport_of(variable: Option<&str>, file: impl FnOnce() -> Option<String>) -> Transport {
    let asked = match variable {
        Some(v) => v.trim().to_owned(),
        None => file().unwrap_or_default().trim().to_owned(),
    };
    if asked == "process" {
        Transport::Process
    } else {
        Transport::InProcess
    }
}

/// The heap's limit in MiB for Java in the game's process: `KILN_JVM_HEAP` (MiB) if set, otherwise 512 and 32 a jar,
/// at most a quarter of the machine's memory (`memory` bytes) and at least 256.
pub fn heap_mib(jars: usize, memory: Option<u64>, set: Option<&str>) -> u64 {
    if let Some(mib) = set
        .and_then(|s| s.trim().parse::<u64>().ok())
        .filter(|m| *m >= 64)
    {
        return mib;
    }
    let wanted = 512 + 32 * jars as u64;
    let quarter = memory.map_or(wanted, |bytes| bytes / 4 / (1 << 20));
    wanted.min(quarter).max(256)
}

/// Where Java's reply lines go: a server call's answers and Java's questions during one to the waiting server, the
/// rest to the game. False once the game let go of the replies.
struct Router {
    replies: Sender<Reply>,
    answers: Sender<Reply>,
}

impl Router {
    fn route(&self, line: &str) -> bool {
        match parse(line) {
            // Nobody waiting for an answer any more is no reason to stop.
            reply @ (Reply::Return(_) | Reply::Ask(..)) => {
                let _ = self.answers.send(reply);
                true
            }
            reply => self.replies.send(reply).is_ok(),
        }
    }
}

/// Java in the game's process runs one thread at a time, as the separate process does: Kiln's own Java thread from
/// one command to the next, or a game thread making a server call (Kiln's Java isn't made for both at once).
pub struct JavaTurn {
    state: Mutex<Turn>,
    free: Condvar,
}

struct Turn {
    /// Kiln's Java thread is starting (the agent, kiln.Host's start), until its first command.
    starting: bool,
    /// The thread in Java now.
    holder: Option<ThreadId>,
    /// `kiln.Host` ended: nobody gets a turn.
    ended: bool,
}

impl JavaTurn {
    /// Waits for Java to be free, then holds it for this thread until the guard goes. Fails when Java ended, or this
    /// thread is in Java already (a call made while answering Java's question would wait for itself).
    fn take(&self) -> Result<TurnGuard<'_>, String> {
        let me = std::thread::current().id();
        let mut state = self.state.lock().unwrap_or_else(|e| e.into_inner());
        loop {
            if state.ended {
                return Err("Java isn't listening".to_owned());
            }
            match state.holder {
                None if !state.starting => break,
                None => state = self.free.wait(state).unwrap_or_else(|e| e.into_inner()),
                Some(holder) if holder == me => {
                    return Err("a call into Java while this thread is in Java already".to_owned());
                }
                Some(_) => state = self.free.wait(state).unwrap_or_else(|e| e.into_inner()),
            }
        }
        state.holder = Some(me);
        Ok(TurnGuard(self))
    }

    fn give_back(&self) {
        let mut state = self.state.lock().unwrap_or_else(|e| e.into_inner());
        state.holder = None;
        state.starting = false;
        drop(state);
        self.free.notify_all();
    }

    fn end(&self) {
        let mut state = self.state.lock().unwrap_or_else(|e| e.into_inner());
        state.ended = true;
        state.holder = None;
        drop(state);
        self.free.notify_all();
    }
}

struct TurnGuard<'a>(&'a JavaTurn);

impl Drop for TurnGuard<'_> {
    fn drop(&mut self) {
        self.0.give_back();
    }
}

/// Java in the game's process: Kiln's Java thread takes the commands from the queue (waiting, not holding Java's
/// turn), and its replies are routed as the separate process's reader thread routes its lines.
struct InProcess {
    commands: Mutex<Receiver<String>>,
    router: Router,
    turn: Arc<JavaTurn>,
}

impl kiln_jni::Channel for InProcess {
    fn next(&self) -> Option<String> {
        // Between commands Java is free for a server call; Kiln's Java thread holds it from start to its first wait.
        self.turn.give_back();
        let line = self.commands.lock().ok()?.recv().ok()?;
        // A turn is refused only once Java ended, which this thread is the one to say.
        std::mem::forget(self.turn.take().ok()?);
        Some(line)
    }

    fn reply(&self, line: String) {
        self.router.route(&line);
    }

    fn ended(&self, why: String) {
        self.turn.end();
        let _ = self.router.replies.send(Reply::Exited(why));
    }
}

/// Java (in the game's process, or the process and its writer thread), the queue it takes commands from, the replies
/// collected, and the server's link until the game takes it.
pub struct JavaHost {
    /// The separate process; none in the game's process.
    child: Option<Child>,
    commands: SyncSender<String>,
    replies: Receiver<Reply>,
    server: Option<ServerLink>,
    /// Why Java runs in a process of its own when it was to run in the game's (D-190): the game says so in chat once.
    pub fell_back: Option<String>,
}

/// The integrated server's way to Java: the same command queue, and the answers to its calls (and Java's questions
/// during them). Any thread may hold it; one call at a time.
pub struct ServerLink {
    /// In the game's process: calls run on the calling thread, in Java's turn ([`JavaTurn`]).
    direct: Option<Arc<JavaTurn>>,
    cost: CallCost,
    commands: SyncSender<String>,
    returns: Receiver<Reply>,
    /// Answers that came after their calls gave up waiting, the newest `LATE_KEPT`, for `take_late`.
    late: Mutex<VecDeque<Return>>,
}

/// What server calls cost, for the game's log: at each 2^n-th call (from 1,024) the calls since the last line (so
/// the first calls, loading classes, weigh only in the first line): their average and slowest, overall and of the
/// most frequent command.
#[derive(Default)]
struct CallCost {
    /// Calls so far, and those since the last line by command ("all" for every one): how many, their time, the slowest.
    calls: Mutex<(u64, std::collections::BTreeMap<String, Window>)>,
}

type Window = (u64, Duration, Duration);

impl CallCost {
    fn count(&self, command: &str, took: Duration, in_process: bool) {
        let mut calls = self.calls.lock().unwrap_or_else(|e| e.into_inner());
        calls.0 += 1;
        let n = calls.0;
        for key in ["all", command] {
            let entry = calls.1.entry(key.to_owned()).or_default();
            entry.0 += 1;
            entry.1 += took;
            entry.2 = entry.2.max(took);
        }
        if n < 1024 || !n.is_power_of_two() {
            return;
        }
        let window = std::mem::take(&mut calls.1);
        let average = |(n, total, _): Window| total.as_nanos() / u128::from(n.max(1));
        let all = window["all"];
        let busiest = window
            .iter()
            .filter(|(k, _)| k.as_str() != "all")
            .max_by_key(|(_, v)| v.0)
            .map(|(k, v)| format!("; {k} {} of them, {} ns on average", v.0, average(*v)))
            .unwrap_or_default();
        log::info!(
            "Kiln: server calls into Java {} up to the {n}th: the last {}, {} ns on average, the slowest {} µs{busiest}",
            if in_process {
                "in the game's process"
            } else {
                "in its own process"
            },
            all.0,
            average(all),
            all.2.as_micros()
        );
    }
}

/// How many answers that came too late a link keeps.
pub const LATE_KEPT: usize = 64;

impl ServerLink {
    /// Sends `command` with `fields` (the first is the call's number, `call`) and waits for its answer, up to
    /// `timeout` after the call or Java's last question. Java's questions meanwhile (`ask`) get `answer`'s answers;
    /// answers to earlier calls, which came too late, are kept for `take_late`, and questions from them get `gave_up`,
    /// so Java never waits for a game that gave up, and stops that call rather than go on with no answers.
    pub fn call(
        &self,
        call: u64,
        command: &str,
        fields: &[&str],
        timeout: Duration,
        answer: &mut dyn FnMut(&[String]) -> String,
    ) -> Result<Return, String> {
        let started = Instant::now();
        let returned = match &self.direct {
            Some(turn) => direct_call(turn, call, command, fields, answer),
            None => self.queued_call(call, command, fields, timeout, answer),
        };
        self.cost
            .count(command, started.elapsed(), self.direct.is_some());
        returned
    }

    /// A server call through the queue to Kiln's Java thread (the separate process).
    fn queued_call(
        &self,
        call: u64,
        command: &str,
        fields: &[&str],
        timeout: Duration,
        answer: &mut dyn FnMut(&[String]) -> String,
    ) -> Result<Return, String> {
        queue(&self.commands, command_line(command, fields))?;
        let mut deadline = Instant::now() + timeout;
        loop {
            let left = deadline.saturating_duration_since(Instant::now());
            match self.returns.recv_timeout(left) {
                Ok(Reply::Return(answer)) if answer.call == call => return Ok(answer),
                Ok(Reply::Ask(asked, what)) => {
                    let line = if asked == call {
                        command_line("answer", &[&asked.to_string(), &answer(&what)])
                    } else {
                        command_line("gave_up", &[&asked.to_string()])
                    };
                    queue(&self.commands, line)?;
                    deadline = Instant::now() + timeout;
                }
                Ok(Reply::Return(late)) => self.keep_late(late),
                Ok(_) => {}
                Err(RecvTimeoutError::Timeout) => {
                    return Err(format!("no answer from Java in {} ms", timeout.as_millis()));
                }
                Err(RecvTimeoutError::Disconnected) => {
                    return Err("Java isn't listening".to_owned());
                }
            }
        }
    }
}

/// A server call in the game's process: in Java's turn, on this thread, Java's questions answered as they come. No
/// timeout: Java answers on the same call, so nothing comes late (a mod that hangs is the watchdog's).
fn direct_call(
    turn: &JavaTurn,
    call: u64,
    command: &str,
    fields: &[&str],
    answer: &mut dyn FnMut(&[String]) -> String,
) -> Result<Return, String> {
    let _turn = turn.take()?;
    let mut ask = |what: &str| answer(&what.split('\t').map(unescape).collect::<Vec<_>>());
    match kiln_jni::call(&command_line(command, fields), &mut ask)? {
        Some(line) => match parse(&line) {
            Reply::Return(returned) if returned.call == call => Ok(returned),
            _ => Err(format!("Java answered call {call} with {line}")),
        },
        None => Err(format!("Java gave no answer to call {call}")),
    }
}

impl ServerLink {
    fn keep_late(&self, late: Return) {
        let mut kept = self.late.lock().unwrap_or_else(|e| e.into_inner());
        if kept.len() == LATE_KEPT {
            kept.pop_front();
        }
        kept.push_back(late);
    }

    /// The answers that came after their calls gave up, of the calls `wanted` says, oldest first; never waits. What
    /// came since the last call is read first; a question from a call nobody waits for gets `gave_up`.
    pub fn take_late(&self, wanted: impl Fn(u64) -> bool) -> Vec<Return> {
        while let Ok(reply) = self.returns.try_recv() {
            match reply {
                Reply::Return(late) => self.keep_late(late),
                Reply::Ask(asked, _) => {
                    let _ = queue(
                        &self.commands,
                        command_line("gave_up", &[&asked.to_string()]),
                    );
                }
                _ => {}
            }
        }
        let mut kept = self.late.lock().unwrap_or_else(|e| e.into_inner());
        let (taken, left): (Vec<Return>, Vec<Return>) =
            kept.drain(..).partition(|r| wanted(r.call));
        kept.extend(left);
        taken
    }
}

impl JavaHost {
    /// Starts `java kiln.Host` from the Java home `home`, with `class_path` (Kiln's runtime jar first, then
    /// the libraries' and the mods' jars) and Kiln's folder `dir` (the mods' game folder); its stderr, the mods' own output,
    /// goes to `dir`/java.log. Only the tests start it so: the game starts Java with [`JavaHost::start_logged`].
    #[cfg(test)]
    pub fn start(home: &Path, class_path: &[PathBuf], dir: &Path) -> Result<JavaHost, String> {
        JavaHost::start_logged(home, class_path, dir, JAVA_LOG)
    }

    /// As [`JavaHost::start`], the mods' own output going to `dir`/`log`: in the game's process ([`transport`]) when it
    /// can, otherwise in a process of its own.
    pub fn start_logged(
        home: &Path,
        class_path: &[PathBuf],
        dir: &Path,
        log: &str,
    ) -> Result<JavaHost, String> {
        let mut fell_back = None;
        if transport(dir) == Transport::InProcess && !kiln_jni::started() {
            match JavaHost::start_in_process(home, class_path, dir, log) {
                Ok(java) => return Ok(java),
                Err(e) if std::env::var("KILN_JVM").as_deref() == Ok("inprocess") => {
                    return Err(format!(
                        "Java didn't start in the game's process (KILN_JVM=inprocess): {e}"
                    ));
                }
                Err(e) => {
                    log::warn!("Kiln: Java runs in a process of its own: not in the game's ({e})");
                    fell_back = Some(e);
                }
            }
        }
        let mut java = JavaHost::start_separate(home, class_path, dir, log)?;
        java.fell_back = fell_back;
        Ok(java)
    }

    /// Java in the game's process (D-112): the JVM made from `home` on a thread of its own, running `kiln.Host`.
    pub fn start_in_process(
        home: &Path,
        class_path: &[PathBuf],
        dir: &Path,
        log: &str,
    ) -> Result<JavaHost, String> {
        // Without Windows's verbatim prefix, which Java's paths refuse (kiln_jni::Os::plain).
        let absolute =
            |p: &Path| kiln_jni::plain(&std::path::absolute(p).unwrap_or_else(|_| p.to_path_buf()));
        let class_path: Vec<PathBuf> = class_path.iter().map(|p| absolute(p)).collect();
        let dir = absolute(dir);
        let heap = heap_mib(
            class_path.len(),
            kiln_jni::physical_memory(),
            std::env::var("KILN_JVM_HEAP").ok().as_deref(),
        );
        let options = vec![
            format!("-Xmx{heap}m"),
            // Generational ZGC (JDK 23 on; plain ZGC on 21): pauses under 0.12 ms against G1's 3.5 to 7.5 ms, which a
            // call into Java waits through, in the game's frame (D-110).
            "-XX:+UseZGC".to_owned(),
            // The game keeps its own signals (Ctrl+C, SIGTERM); and no AWT in the game's process.
            "-Xrs".to_owned(),
            "-Djava.awt.headless=true".to_owned(),
            "-Djoml.nounsafe=true".to_owned(),
            resources_property().to_string_lossy().into_owned(),
            format!("-Dkiln.dir={}", dir.display()),
            // Relative paths as a separate Java in `dir` reads them (for what Java resolves itself: File, Path).
            format!("-Duser.dir={}", dir.display()),
            "-Dkiln.channel=jni".to_owned(),
            format!("-Dkiln.log={}", dir.join(log).display()),
        ];
        let mut options = options;
        options.extend(
            crash_options(&dir, log)
                .into_iter()
                .map(|o| o.to_string_lossy().into_owned()),
        );
        let (commands, queue) = sync_channel::<String>(QUEUE);
        let (sender, replies) = channel();
        let (answers, returns) = channel();
        // Kiln's Java thread holds Java's turn from its start (the agent, kiln.Host's start) to its first command.
        let turn = Arc::new(JavaTurn {
            state: Mutex::new(Turn {
                starting: true,
                holder: None,
                ended: false,
            }),
            free: Condvar::new(),
        });
        let host = Arc::new(InProcess {
            commands: Mutex::new(queue),
            router: Router {
                replies: sender,
                answers,
            },
            turn: turn.clone(),
        });
        let config = kiln_jni::Config {
            home: home.to_path_buf(),
            class_path,
            options,
        };
        kiln_jni::start(config, host)?;
        log::info!("Kiln: Java in the game's process, heap {heap} MiB, ZGC");
        let mut java = JavaHost::made(None, commands, replies, returns);
        if let Some(link) = &mut java.server {
            link.direct = Some(turn);
        }
        Ok(java)
    }

    fn made(
        child: Option<Child>,
        commands: SyncSender<String>,
        replies: Receiver<Reply>,
        returns: Receiver<Reply>,
    ) -> JavaHost {
        let server = Some(ServerLink {
            direct: None,
            cost: CallCost::default(),
            commands: commands.clone(),
            returns,
            late: Mutex::new(VecDeque::new()),
        });
        JavaHost {
            child,
            commands,
            replies,
            server,
            fell_back: None,
        }
    }

    /// Whether this Java runs in the game's process.
    pub fn in_process(&self) -> bool {
        self.child.is_none()
    }

    /// Java in a process of its own (`java kiln.Host`), whatever [`transport`] says.
    pub fn start_separate(
        home: &Path,
        class_path: &[PathBuf],
        dir: &Path,
        log: &str,
    ) -> Result<JavaHost, String> {
        // Java runs in `dir`: relative paths would be read from there.
        // Without Windows's verbatim prefix, which Java's paths refuse (kiln_jni::Os::plain).
        let absolute =
            |p: &Path| kiln_jni::plain(&std::path::absolute(p).unwrap_or_else(|_| p.to_path_buf()));
        let class_path: Vec<PathBuf> = class_path.iter().map(|p| absolute(p)).collect();
        let dir = &absolute(dir);
        let joined = std::env::join_paths(&class_path).map_err(|e| e.to_string())?;
        let log_name = log;
        let log_file = dir.join(log);
        let log =
            std::fs::File::create(&log_file).map_err(|e| format!("{}: {e}", log_file.display()))?;
        // As an OsString, so a game folder whose name isn't UTF-8 reaches Java unchanged.
        let mut kiln_dir = OsString::from("-Dkiln.dir=");
        kiln_dir.push(dir);
        // Kiln's runtime is a Java agent too (kiln.Mixins, Kiln's Mixin): it patches the stand-ins with the mods'
        // mixins as they load. Only for Kiln's runtime jar, named so, which comes first.
        let mut agent = OsString::from("-javaagent:");
        if let Some(runtime) = class_path
            .first()
            .filter(|p| p.file_name() == Some("kiln-runtime.jar".as_ref()))
        {
            agent.push(runtime);
        }
        let home = &kiln_jni::plain(home);
        let mut command = Command::new(java_program(home));
        if agent.len() > "-javaagent:".len() {
            command.arg(agent);
        }
        // Java's lines go through pipes: no console window for `java.exe` on Windows, when the game has none.
        #[cfg(windows)]
        {
            use std::os::windows::process::CommandExt;
            const CREATE_NO_WINDOW: u32 = 0x0800_0000;
            command.creation_flags(CREATE_NO_WINDOW);
        }
        // In Kiln's folder, as vanilla runs in the game folder: a mod's relative `config/` is FabricLoader's.
        let mut child = command
            .current_dir(dir)
            .arg("-Xmx512m")
            // JOML (the mods' HUD poses) without sun.misc.Unsafe, which Java 25 warns about in the log.
            .arg("-Djoml.nounsafe=true")
            // The game's prepared resources (its language files, its assets), for the mods' text and resources.
            .arg(resources_property())
            .arg(kiln_dir)
            // Its log, which stderr writes here: the Crashes tab keeps its tail with a crash (D-191).
            .arg({
                let mut property = OsString::from("-Dkiln.log=");
                property.push(&log_file);
                property
            })
            .args(crash_options(dir, log_name))
            .arg("-cp")
            .arg(joined)
            .arg("kiln.Host")
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .stderr(log)
            .spawn()
            .map_err(|e| format!("can't run {}: {e}", java_program(home).display()))?;
        let mut stdin = child.stdin.take().ok_or("no stdin for Java")?;
        let stdout = child.stdout.take().ok_or("no stdout for Java")?;
        let (commands, queue) = sync_channel::<String>(QUEUE);
        // Ends when Java stops reading (its stdin breaks) or the game drops the JavaHost.
        std::thread::Builder::new()
            .name("kiln-java-in".into())
            .spawn(move || {
                for line in queue {
                    if writeln!(stdin, "{line}")
                        .and_then(|()| stdin.flush())
                        .is_err()
                    {
                        return;
                    }
                }
            })
            .map_err(|e| e.to_string())?;
        let (sender, replies) = channel();
        let (answers, returns) = channel();
        let router = Router {
            replies: sender,
            answers,
        };
        std::thread::Builder::new()
            .name("kiln-java".into())
            .spawn(move || {
                let mut why = "it closed its output".to_owned();
                for line in BufReader::new(stdout).lines() {
                    match line {
                        Ok(line) => {
                            if !router.route(&line) {
                                return;
                            }
                        }
                        Err(e) => {
                            why = e.to_string();
                            break;
                        }
                    }
                }
                let _ = router.replies.send(Reply::Exited(why));
            })
            .map_err(|e| e.to_string())?;
        Ok(JavaHost::made(Some(child), commands, replies, returns))
    }

    /// Queues a command (`load`, `font`, `key`, `adv`, `tick`) with its fields; never waits.
    pub fn send(&mut self, command: &str, fields: &[&str]) -> Result<(), String> {
        queue(&self.commands, command_line(command, fields))
    }

    /// The integrated server's link to this Java, once.
    pub fn take_server_link(&mut self) -> Option<ServerLink> {
        self.server.take()
    }

    /// The replies since the last call, oldest first; never waits.
    pub fn replies(&self) -> Vec<Reply> {
        self.replies.try_iter().collect()
    }
}

impl Drop for JavaHost {
    // In the game's process, the queue's end going is Java's end of input: `kiln.Host` returns (the JVM stays, idle).
    fn drop(&mut self) {
        if let Some(child) = &mut self.child {
            let _ = child.kill();
            let _ = child.wait();
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn java_has_one_thread_at_a_time_and_none_after_it_ended() {
        let turn = Arc::new(JavaTurn {
            state: Mutex::new(Turn {
                starting: true,
                holder: None,
                ended: false,
            }),
            free: Condvar::new(),
        });
        // Starting: a call waits until Kiln's Java thread is between commands.
        let waiting = {
            let turn = turn.clone();
            std::thread::spawn(move || turn.take().map(|_| ()).is_ok())
        };
        std::thread::sleep(Duration::from_millis(50));
        assert!(!waiting.is_finished());
        turn.give_back();
        assert!(waiting.join().unwrap());
        // A thread already in Java can't wait for itself.
        let held = turn.take().unwrap();
        assert!(turn.take().is_err());
        drop(held);
        turn.end();
        assert_eq!(turn.take().err().as_deref(), Some("Java isn't listening"));
    }

    #[test]
    fn only_the_games_java_keeps_a_breadcrumb() {
        let dir = Path::new("/games/horizon/kiln");
        let game: Vec<String> = crash_options(dir, JAVA_LOG)
            .iter()
            .map(|o| o.to_string_lossy().into_owned())
            .collect();
        assert_eq!(
            game,
            [
                format!("-Dkiln.last_call={}", dir.join(LAST_CALL).display()),
                format!("-XX:ErrorFile={}", dir.join("hs_err_pid%p.log").display()),
            ]
        );
        let loot = crash_options(dir, "java-loot.log");
        assert_eq!(loot.len(), 1);
        assert!(loot[0].to_string_lossy().starts_with("-XX:ErrorFile="));
        assert_eq!(parse("trip\tzoomly"), Reply::Trip("zoomly".into()));
        // Java started again (D-134) keeps a breadcrumb of its own, never the first Java's.
        let again = crash_options(dir, RESTARTED_LOG);
        assert_eq!(
            again[0].to_string_lossy(),
            format!(
                "-Dkiln.last_call={}",
                dir.join(RESTARTED_LAST_CALL).display()
            )
        );
        assert_eq!(parse("restart"), Reply::Restart(0));
        assert_eq!(parse("restart\t73400320"), Reply::Restart(73_400_320));
        assert_eq!(parse("restart_game"), Reply::RestartGame);
        // A mod's GUI picture (D-160): one pixel, 1 2 3 255.
        assert_eq!(
            parse("sprite\tjei:gui/sprites/slot\t1\t1\tAQID/w=="),
            Reply::Sprite("jei:gui/sprites/slot".into(), 1, 1, vec![1, 2, 3, 255])
        );
    }

    #[test]
    fn java_in_a_process_of_its_own_by_variable_or_by_file() {
        assert_eq!(transport_of(None, || None), Transport::InProcess);
        assert_eq!(transport_of(Some("process"), || None), Transport::Process);
        assert_eq!(
            transport_of(None, || Some("process\n".into())),
            Transport::Process
        );
        assert_eq!(
            transport_of(None, || Some("inprocess".into())),
            Transport::InProcess
        );
        // The variable wins over the file, either way.
        assert_eq!(
            transport_of(Some("inprocess"), || Some("process".into())),
            Transport::InProcess
        );
        // A folder with no such file: as the variable says, or in the game's process.
        let dir = std::env::temp_dir().join(format!("kiln-no-jvm-file-{}", std::process::id()));
        assert_eq!(
            transport(&dir),
            transport_of(std::env::var("KILN_JVM").ok().as_deref(), || None)
        );
    }

    #[test]
    fn the_overlay_flips_a_switch_and_opens_the_mods_folder() {
        assert_eq!(
            parse("switch\tzoomly\t0"),
            Reply::Switch("zoomly".into(), false)
        );
        assert_eq!(
            parse("switch\tzoomly\t1"),
            Reply::Switch("zoomly".into(), true)
        );
        assert_eq!(parse("open\tmods"), Reply::Open("mods".into()));
    }

    #[test]
    fn the_heap_grows_with_the_jars_within_the_machine() {
        let gib = 1u64 << 30;
        assert_eq!(heap_mib(0, Some(16 * gib), None), 512);
        assert_eq!(heap_mib(40, Some(16 * gib), None), 512 + 32 * 40);
        // A quarter of 4 GiB, and never under 256 MiB.
        assert_eq!(heap_mib(100, Some(4 * gib), None), 1024);
        assert_eq!(heap_mib(100, Some(gib / 2), None), 256);
        // Set by hand wins; nonsense doesn't.
        assert_eq!(heap_mib(100, Some(gib), Some("3000")), 3000);
        assert_eq!(heap_mib(0, None, Some("lots")), 512);
    }

    #[test]
    fn a_home_without_a_jvm_falls_back_on_the_separate_process() {
        let home = std::env::temp_dir().join(format!("kiln-no-libjvm-{}", std::process::id()));
        assert!(JavaHost::start_in_process(&home, &[], &home, "java.log").is_err());
    }

    #[test]
    fn fields_survive_escaping() {
        let text = "a\tb\nc\\d\re";
        assert_eq!(escape(text), "a\\tb\\nc\\\\d\\re");
        assert_eq!(unescape(&escape(text)), text);
        assert_eq!(unescape("trailing\\"), "trailing\\");
    }

    /// A "Java" that never reads its stdin, as a mod stuck in a tick: the game's sends return at once, and
    /// once the queue is full they fail instead of blocking the game.
    #[cfg(unix)]
    #[test]
    fn a_stuck_java_never_blocks_the_game() {
        use std::os::unix::fs::PermissionsExt;
        let home = std::env::temp_dir().join(format!("kiln-stuck-java-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&home);
        std::fs::create_dir_all(home.join("bin")).unwrap();
        let program = java_program(&home);
        std::fs::write(&program, "#!/bin/sh\nexec sleep 60\n").unwrap();
        std::fs::set_permissions(&program, std::fs::Permissions::from_mode(0o755)).unwrap();
        let mut java = start_written(&home);
        let line = "x".repeat(200);
        let started = std::time::Instant::now();
        let mut failed = None;
        for i in 0..QUEUE * 10 {
            if let Err(e) = java.send("tick", &[&line]) {
                failed = Some((i, e));
                break;
            }
        }
        assert!(
            started.elapsed() < std::time::Duration::from_secs(5),
            "{:?}",
            started.elapsed()
        );
        let (sent, why) = failed.expect("the queue should fill");
        // The writer thread takes a few commands into the pipe (64 KiB) before it blocks, then the queue fills.
        assert!(sent >= QUEUE, "{sent}");
        assert!(why.contains("stuck"), "{why}");
        drop(java);
        let _ = std::fs::remove_dir_all(&home);
    }

    /// A "Java" that answers each call twice, an old call's number first: the server's call gets its own
    /// answer, never the other, and the window's replies never see them.
    #[cfg(unix)]
    #[test]
    fn a_call_gets_its_own_answer() {
        use std::os::unix::fs::PermissionsExt;
        let home = std::env::temp_dir().join(format!("kiln-call-java-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&home);
        std::fs::create_dir_all(home.join("bin")).unwrap();
        let program = java_program(&home);
        let script = "#!/bin/sh\nwhile IFS=\"$(printf '\\t')\" read -r command call rest; do\n  \
                      printf 'return\\t0\\tpass\\n'\n  \
                      printf 'return\\t%s\\tsuccess\\thealth 24.0\\n' \"$call\"\n  \
                      printf 'done\\t%s\\n' \"$command\"\ndone\n";
        std::fs::write(&program, script).unwrap();
        std::fs::set_permissions(&program, std::fs::Permissions::from_mode(0o755)).unwrap();
        let mut java = start_written(&home);
        let link = java.take_server_link().unwrap();
        assert!(java.take_server_link().is_none());
        let timeout = Duration::from_secs(5);
        for call in [3, 4] {
            let answer = link
                .call(call, "use", &[&call.to_string()], timeout, &mut |_| {
                    unreachable!("nothing asked")
                })
                .unwrap();
            assert_eq!(answer.call, call);
            assert_eq!(answer.result, "success");
            assert_eq!(answer.effects, ["health 24.0"]);
        }
        std::thread::sleep(Duration::from_millis(100));
        assert_eq!(
            java.replies(),
            [Reply::Done("use".into()), Reply::Done("use".into())]
        );
        drop(java);
        assert!(
            link.call(5, "use", &["5"], timeout, &mut |_| String::new())
                .is_err()
        );
        let _ = std::fs::remove_dir_all(&home);
    }

    /// Kiln's runtime jar first on the class path makes it Java's agent (Kiln's Mixin); without it, Java gets no
    /// `-javaagent` at all, not an empty one (which Java refuses to start with).
    #[cfg(unix)]
    #[test]
    fn only_kilns_runtime_is_javas_agent() {
        use std::os::unix::fs::PermissionsExt;
        let home = std::env::temp_dir().join(format!("kiln-agent-java-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&home);
        std::fs::create_dir_all(home.join("bin")).unwrap();
        let program = java_program(&home);
        // Its arguments, one a line, then it waits for its input to close.
        std::fs::write(
            &program,
            "#!/bin/sh\nprintf '%s\\n' \"$@\" > args.txt\nexec cat > /dev/null\n",
        )
        .unwrap();
        std::fs::set_permissions(&program, std::fs::Permissions::from_mode(0o755)).unwrap();
        let args = |class_path: &[PathBuf]| -> Vec<String> {
            let _ = std::fs::remove_file(home.join("args.txt"));
            let java = (0..100)
                .find_map(|_| match JavaHost::start(&home, class_path, &home) {
                    Err(e) if e.contains("Text file busy") => {
                        std::thread::sleep(Duration::from_millis(10));
                        None
                    }
                    started => Some(started.unwrap()),
                })
                .expect("the script stayed busy");
            let deadline = std::time::Instant::now() + Duration::from_secs(10);
            let text = loop {
                let text = std::fs::read_to_string(home.join("args.txt")).unwrap_or_default();
                if text.contains("kiln.Host") {
                    break text;
                }
                assert!(std::time::Instant::now() < deadline, "Java got {text:?}");
                std::thread::sleep(Duration::from_millis(5));
            };
            drop(java);
            text.lines().map(str::to_owned).collect()
        };
        let runtime = home.join("kiln-runtime.jar");
        let with = args(&[runtime.clone(), home.join("mod.jar")]);
        assert_eq!(
            with[0],
            format!("-javaagent:{}", runtime.display()),
            "{with:?}"
        );
        let without = args(&[home.join("mod.jar")]);
        assert!(
            without.iter().all(|a| !a.starts_with("-javaagent")),
            "{without:?}"
        );
        let _ = std::fs::remove_dir_all(&home);
    }

    #[test]
    fn java_started_after_hears_where_the_games_resources_are() {
        set_resources(Path::new("/games/26.3"));
        assert_eq!(
            resources_property(),
            OsString::from("-Dkiln.resources=/games/26.3")
        );
    }

    /// A "Java" whose call asks about a block as an earlier call (one the game gave up on): it is told so
    /// (`gave_up`), not answered, and the call's own answer comes after.
    #[cfg(unix)]
    #[test]
    fn a_question_from_a_call_given_up_on_is_told_so() {
        use std::os::unix::fs::PermissionsExt;
        let home = std::env::temp_dir().join(format!("kiln-gave-up-java-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&home);
        std::fs::create_dir_all(home.join("bin")).unwrap();
        let program = java_program(&home);
        let script = "#!/bin/sh\nwhile IFS=\"$(printf '\\t')\" read -r command a b; do\n  \
                      case \"$command\" in\n    \
                      use_block) call=\"$a\"; printf 'ask\\t1\\tblock\\t1\\t2\\t3\\n' ;;\n    \
                      gave_up) printf 'return\\t%s\\tsuccess\\tgave up %s\\n' \"$call\" \"$a\" ;;\n    \
                      answer) printf 'return\\t%s\\tsuccess\\tanswered\\n' \"$call\" ;;\n  \
                      esac\ndone\n";
        std::fs::write(&program, script).unwrap();
        std::fs::set_permissions(&program, std::fs::Permissions::from_mode(0o755)).unwrap();
        let mut java = start_written(&home);
        let link = java.take_server_link().unwrap();
        let answer = link
            .call(2, "use_block", &["2"], Duration::from_secs(5), &mut |_| {
                unreachable!("an earlier call's question is not the game's to answer")
            })
            .unwrap();
        assert_eq!(answer.effects, ["gave up 1"]);
        drop(java);
        let _ = std::fs::remove_dir_all(&home);
    }

    /// A "Java" that asks about a block during each call and returns what it was told: the call's own `answer`
    /// answers it.
    #[cfg(unix)]
    #[test]
    fn java_asks_the_game_during_a_call() {
        use std::os::unix::fs::PermissionsExt;
        let home = std::env::temp_dir().join(format!("kiln-ask-java-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&home);
        std::fs::create_dir_all(home.join("bin")).unwrap();
        let program = java_program(&home);
        let script = "#!/bin/sh\nwhile IFS=\"$(printf '\\t')\" read -r command a b; do\n  \
                      case \"$command\" in\n    \
                      use_block) printf 'ask\\t%s\\tblock\\t1\\t2\\t3\\n' \"$a\" ;;\n    \
                      answer) printf 'return\\t%s\\tsuccess\\tgot %s\\n' \"$a\" \"$b\" ;;\n  \
                      esac\ndone\n";
        std::fs::write(&program, script).unwrap();
        std::fs::set_permissions(&program, std::fs::Permissions::from_mode(0o755)).unwrap();
        let mut java = start_written(&home);
        let link = java.take_server_link().unwrap();
        let mut asked = Vec::new();
        let answer = link
            .call(
                7,
                "use_block",
                &["7"],
                Duration::from_secs(5),
                &mut |what| {
                    asked.push(what.to_vec());
                    "minecraft:stone".to_owned()
                },
            )
            .unwrap();
        assert_eq!(asked, [["block", "1", "2", "3"]]);
        assert_eq!(answer.effects, ["got minecraft:stone"]);
        drop(java);
        let _ = std::fs::remove_dir_all(&home);
    }

    #[test]
    fn replies_parse() {
        assert_eq!(
            parse("ready\t25.0.2 (Eclipse)"),
            Reply::Ready("25.0.2 (Eclipse)".into())
        );
        assert_eq!(
            parse("draw\tf 1 2 30 40 2013265920\tt 5 6 4294967295 1 Hello\\tworld 1:02.345"),
            Reply::Draw(vec![
                HudOp::Fill {
                    x: 1.0,
                    y: 2.0,
                    w: 30.0,
                    h: 40.0,
                    argb: 0x7800_0000
                },
                HudOp::Text {
                    x: 5.0,
                    y: 6.0,
                    scale: 1.0,
                    argb: 0xFFFF_FFFF,
                    shadow: true,
                    text: "Hello\tworld 1:02.345".into()
                },
            ])
        );
        assert_eq!(parse("draw"), Reply::Draw(Vec::new()));
        assert_eq!(
            parse_op("t 1 2 3 0 "),
            Some(HudOp::Text {
                x: 1.0,
                y: 2.0,
                scale: 1.0,
                argb: 3,
                shadow: false,
                text: String::new()
            })
        );
        // Scaled by a mod's pose: fractions, and a scale before the colour.
        assert_eq!(
            parse_op("s 1.5 2.25 0.75 4278190335 1 50%"),
            Some(HudOp::Text {
                x: 1.5,
                y: 2.25,
                scale: 0.75,
                argb: 0xFF00_00FF,
                shadow: true,
                text: "50%".into()
            })
        );
        assert_eq!(
            parse_op("i 2 3 12 minecraft:diamond_helmet"),
            Some(HudOp::Item {
                x: 2.0,
                y: 3.0,
                size: 12.0,
                item: "minecraft:diamond_helmet".into()
            })
        );
        assert_eq!(parse_op("i 2 3 12 "), None);
        assert_eq!(parse_op("x 1"), None);
        assert_eq!(
            parse("failed\ta.B\tjava.lang.ClassNotFoundException: a.B"),
            Reply::Failed("a.B".into(), "java.lang.ClassNotFoundException: a.B".into())
        );
        assert_eq!(parse("done\ttick"), Reply::Done("tick".into()));
        assert_eq!(parse("loaded\ta.B"), Reply::Loaded("a.B".into()));
        assert_eq!(parse("log\tstarted"), Reply::Log("started".into()));
        assert_eq!(
            parse("items\theart_crystals:heart_crystal\ta:b"),
            Reply::Items(vec!["heart_crystals:heart_crystal".into(), "a:b".into()])
        );
        assert_eq!(parse("items"), Reply::Items(Vec::new()));
        assert_eq!(
            parse("ask\t9\tblock\t1\t-2\t3"),
            Reply::Ask(9, vec!["block".into(), "1".into(), "-2".into(), "3".into()])
        );
        assert_eq!(
            parse("bridge\tmouse_scrolling\tliving_incoming_damage"),
            Reply::Bridge(vec![
                "mouse_scrolling".into(),
                "living_incoming_damage".into()
            ])
        );
        assert_eq!(parse("bridge"), Reply::Bridge(Vec::new()));
        assert_eq!(
            parse("back\tfov\t30\tflying_speed\t0.1"),
            Reply::Back(vec![
                ("fov".into(), "30".into()),
                ("flying_speed".into(), "0.1".into())
            ])
        );
        assert_eq!(
            parse("blocks\tmymod:lamp use,step"),
            Reply::Blocks(vec!["mymod:lamp use,step".into()])
        );
        assert_eq!(
            parse("loot\tminecraft:chests/simple_dungeon\t[{\"rolls\":1,\"entries\":[]}]"),
            Reply::Loot(
                "minecraft:chests/simple_dungeon".into(),
                "[{\"rolls\":1,\"entries\":[]}]".into()
            )
        );
        assert_eq!(
            parse("listens\tjoin\trespawn"),
            Reply::Listens(vec!["join".into(), "respawn".into()])
        );
        assert_eq!(
            parse("return\t7\tsuccess\thealth 24.0\toverlay {\"text\":\"a\\tb\"}"),
            Reply::Return(Return {
                call: 7,
                result: "success".into(),
                effects: vec!["health 24.0".into(), "overlay {\"text\":\"a\tb\"}".into()],
            })
        );
        assert_eq!(parse("chat"), Reply::Chat(String::new()));
        // Calc's error (#82): red, as the mod styled it, and the escaped tab of the line back in its text.
        assert_eq!(
            parse(concat!(
                "chat_json\t",
                r#"{"text":"Division by zero\\t!","color":"red","bold":true,"italic":true}"#
            )),
            Reply::ChatComponent(serde_json::json!({
                "text": "Division by zero\t!", "color": "red", "bold": true, "italic": true
            }))
        );
        assert_eq!(
            parse("chat_json\t{not json"),
            Reply::Log("unreadable chat component: {not json".into())
        );
        assert_eq!(
            parse("commands\tkiln_testbench\tcalc"),
            Reply::Commands(vec!["kiln_testbench".into(), "calc".into()])
        );
        // A mod's keys, four fields each, for the entrypoint that registered them; one with none says so.
        assert_eq!(
            parse("keys\tfov.Client\tkey.fovtoggle.toggle\tFOV Toggle\tToggle FOV\tkey.keyboard.v"),
            Reply::Keys(
                "fov.Client".into(),
                vec![[
                    "key.fovtoggle.toggle".into(),
                    "FOV Toggle".into(),
                    "Toggle FOV".into(),
                    "key.keyboard.v".into()
                ]]
            )
        );
        assert_eq!(
            parse("keys\ta.Main"),
            Reply::Keys("a.Main".into(), Vec::new())
        );
        assert_eq!(parse("keys"), Reply::Keys(String::new(), Vec::new()));
        // A key a mod set itself.
        assert_eq!(
            parse("chosen\tkey.fovtoggle.toggle\tkey.keyboard.b"),
            Reply::KeyChosen("key.fovtoggle.toggle".into(), "key.keyboard.b".into())
        );
        assert_eq!(
            parse("send\t1\thelp help"),
            Reply::Send(true, "help help".into())
        );
        assert_eq!(
            parse("send\t0\tsay hi"),
            Reply::Send(false, "say hi".into())
        );
        assert_eq!(
            parse("send_chat\thello there"),
            Reply::SendChat("hello there".into())
        );
        assert_eq!(
            parse("clip\tdiamond\\tsword"),
            Reply::Clipboard("diamond\tsword".into())
        );
        assert_eq!(parse("throw_slot\t12\t1"), Reply::ThrowSlot(12, 1));
        assert_eq!(parse("want_chunk\t-3\t7"), Reply::WantChunk(-3, 7));
        assert_eq!(
            parse("sound\tminecraft:ui.button.click\t\t0.25\t1"),
            Reply::Play(Play::Sound {
                id: "minecraft:ui.button.click".into(),
                at: None,
                volume: 0.25,
                pitch: 1.0
            })
        );
        assert_eq!(
            parse("sound\tminecraft:entity.experience_orb.pickup\t1.5 70 -3\t0.2\t1"),
            Reply::Play(Play::Sound {
                id: "minecraft:entity.experience_orb.pickup".into(),
                at: Some([1.5, 70.0, -3.0]),
                volume: 0.2,
                pitch: 1.0
            })
        );
        assert_eq!(
            parse("particle\tminecraft:happy_villager\t1 2 3\t0 0.5 0"),
            Reply::Play(Play::Particle {
                id: "minecraft:happy_villager".into(),
                at: [1.0, 2.0, 3.0],
                velocity: [0.0, 0.5, 0.0]
            })
        );
        assert!(matches!(
            parse("particle\tminecraft:flame\t1 NaN 3\t0 0 0"),
            Reply::Log(_)
        ));
        assert_eq!(
            parse("toast\tKiln Testbench\ttoast probe\t10000\t1f2e\t1"),
            Reply::Play(Play::Toast {
                title: "Kiln Testbench".into(),
                message: "toast probe".into(),
                millis: 10000.0,
                token: "1f2e".into(),
                replace: true
            })
        );
        assert!(matches!(parse("what"), Reply::Log(_)));
    }

    #[test]
    fn an_entity_op_names_the_box_and_the_type() {
        assert_eq!(
            parse_op("e 10 20 60 80 25 0.0625 30 40 minecraft:cow"),
            Some(HudOp::Entity {
                rect: [10.0, 20.0, 60.0, 80.0],
                size: 25.0,
                offset_y: 0.0625,
                mouse: [30.0, 40.0],
                id: "minecraft:cow".to_owned(),
                saved: String::new(),
            })
        );
        assert_eq!(parse_op("e 10 20 60 80 25 0 30 40"), None);
        // The entity as saved, spaces in its text and all (D-179).
        assert_eq!(
            parse_op(
                r#"e 10 20 60 80 25 0 30 40 minecraft:sheep {Color:11,CustomName:"a b",id:"minecraft:sheep"}"#
            ),
            Some(HudOp::Entity {
                rect: [10.0, 20.0, 60.0, 80.0],
                size: 25.0,
                offset_y: 0.0,
                mouse: [30.0, 40.0],
                id: "minecraft:sheep".to_owned(),
                saved: r#"{Color:11,CustomName:"a b",id:"minecraft:sheep"}"#.to_owned(),
            })
        );
    }

    #[test]
    fn a_mods_screen_opens_draws_and_closes() {
        assert_eq!(
            parse("screen\topen\t1\tcom.example.Config"),
            Reply::Screen(Some((true, "com.example.Config".into())))
        );
        assert_eq!(
            parse("screen\topen\t0\tX"),
            Reply::Screen(Some((false, "X".into())))
        );
        assert_eq!(parse("screen\tclose"), Reply::Screen(None));
        assert_eq!(
            parse(
                "sdraw\tf 0 0 427 240 3222278160\tb 10 20 200 20 3 4284111450 gui/sprites/widget/button"
            ),
            Reply::ScreenDraw(vec![
                HudOp::Fill {
                    x: 0.0,
                    y: 0.0,
                    w: 427.0,
                    h: 240.0,
                    argb: 0xC010_1010
                },
                HudOp::Sprite {
                    rect: [10.0, 20.0, 200.0, 20.0],
                    border: 3.0,
                    fallback: 0xFF5A_5A5A,
                    tint: u32::MAX,
                    name: "gui/sprites/widget/button".into()
                },
            ])
        );
        assert_eq!(parse("sdraw"), Reply::ScreenDraw(Vec::new()));
        // What the mods draw over the game's container screen, where they take the mouse, and the keys (D-156).
        assert_eq!(
            parse("odraw\t1\t300,10,120,200;0,0,20,20\tf 300 10 120 200 3222278160"),
            Reply::OverDraw(
                vec![HudOp::Fill {
                    x: 300.0,
                    y: 10.0,
                    w: 120.0,
                    h: 200.0,
                    argb: 0xC010_1010
                }],
                true,
                vec![[300.0, 10.0, 120.0, 200.0], [0.0, 0.0, 20.0, 20.0]]
            )
        );
        assert_eq!(
            parse("odraw\t0\t"),
            Reply::OverDraw(Vec::new(), false, Vec::new())
        );
        assert_eq!(
            parse("settings\tarmour_durability\tshowfps"),
            Reply::Settings(vec!["armour_durability".into(), "showfps".into()])
        );
        assert_eq!(
            parse_op("b 1 2 3 4 1 0 a:gui/sprites/x y"),
            Some(HudOp::Sprite {
                rect: [1.0, 2.0, 3.0, 4.0],
                border: 1.0,
                fallback: 0,
                tint: u32::MAX,
                name: "a:gui/sprites/x y".into()
            }),
            "the sprite's name is the last field"
        );
        assert_eq!(
            parse_op("B 1 2 3 4 0 0 4282349284 kiln:atlas/minecraft/block/water_still"),
            Some(HudOp::Sprite {
                rect: [1.0, 2.0, 3.0, 4.0],
                border: 0.0,
                fallback: 0,
                tint: 0xFF3F_76E4,
                name: "kiln:atlas/minecraft/block/water_still".into()
            }),
            "a tinted sprite: its tint before its name"
        );
        assert_eq!(
            parse_op("B 1 2 3 4 0 0 4282349284"),
            None,
            "a tinted sprite has a name"
        );
        assert_eq!(parse_op("b 1 2 3 4 1 0 "), None, "a sprite has a name");
        assert_eq!(parse_op("b 1 2 3 4 1"), None);
        // D-164: a whole stack, its SNBT the last field (spaces and all), and styled text as text JSON.
        let book = r#"{components:{"minecraft:stored_enchantments":{"minecraft:sharpness":5}},count:1,id:"minecraft:enchanted_book"}"#;
        assert_eq!(
            parse_op(&format!("I 10 20 16 {book}")),
            Some(HudOp::Stack {
                x: 10.0,
                y: 20.0,
                size: 16.0,
                snbt: book.into()
            })
        );
        assert_eq!(parse_op("I 10 20 16 "), None, "a stack has its SNBT");
        let json = r##"{"text":"","extra":[{"text":"Sharp ","color":"#AAAAAA"},{"text":"V","bold":true}]}"##;
        assert_eq!(
            parse_op(&format!("j 1 2 1.5 4294967295 1 {json}")),
            Some(HudOp::Styled {
                x: 1.0,
                y: 2.0,
                scale: 1.5,
                argb: 0xFFFF_FFFF,
                shadow: true,
                text: "Sharp V".into(),
                json: serde_json::from_str(json).unwrap()
            })
        );
        assert_eq!(parse_op("j 1 2 1 4294967295 1 {not json"), None);
    }

    #[test]
    fn clip_ops_cut_what_follows() {
        assert_eq!(
            parse_op("c 4 40 64 90"),
            Some(HudOp::Clip(Some([4.0, 40.0, 64.0, 90.0])))
        );
        assert_eq!(parse_op("c"), Some(HudOp::Clip(None)));
        assert_eq!(parse_op("c 4 40"), None);
    }

    #[test]
    fn payloads_go_as_hex() {
        assert_eq!(hex(&[0x00, 0x0f, 0xa5, 0xff]), "000fa5ff");
        assert_eq!(hex(&[]), "");
        assert_eq!(unhex("000fa5ff"), Some(vec![0x00, 0x0f, 0xa5, 0xff]));
        assert_eq!(unhex("A5"), Some(vec![0xa5]), "Java may write capitals too");
        assert_eq!(unhex(""), Some(Vec::new()));
        assert_eq!(unhex("abc"), None, "an odd length");
        assert_eq!(unhex("0g"), None, "not a hex digit");
        assert_eq!(unhex("+1"), None, "a sign isn't a digit");
        let all: Vec<u8> = (0..=255).collect();
        assert_eq!(unhex(&hex(&all)), Some(all));
        assert_eq!(
            parse("net\t0a6b696c6e3a70696e67"),
            Reply::Net(b"\x0akiln:ping".to_vec())
        );
        assert!(
            matches!(parse("net\tzz"), Reply::Log(_)),
            "an unreadable payload is logged"
        );
    }
}
