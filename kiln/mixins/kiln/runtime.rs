// Kiln: runs Java mods (.jar) in Horizon. When the player first enters a world, Kiln starts Java in a
// process of its own (`crate::game::kiln_jvm`) with Kiln's Java classes (`<game folder>/kiln/kiln-runtime.jar`:
// stand-ins for the Minecraft and Fabric classes mods call) and the mods in `<game folder>/kiln/mods/*.jar`, and
// has it run each mod's client entrypoint from its fabric.mod.json. From then on it gives Java a client tick 20
// times a second with the game's state (`Snapshot`), the keys pressed and the advancements made, and draws what
// the mods draw on the HUD. The integrated server's game calls the same Java when a player uses an item a Java mod
// gave a use of its own (`use_item`), comes into a level (`player_joined`) or respawns (`player_respawned`), and when
// a mod's block is used, ticks, is stood on, has a neighbour change, is placed or broken (`block_used`, `random_tick`,
// `stepped_on`, `scheduled_tick`, `neighbor_changed`, `placement`, `placed`, `destroyed`); Java may ask it about the
// world meanwhile, and it does what the mods' code did. This file is both part of the mod's Cargo
// library (its tests: src/lib.rs) and the module the bundle adds to the game (`crate::game::kiln`); the game's own
// state is read and changed in horizon.rs.

use std::cell::{Cell, RefCell};
use std::collections::{BTreeMap, BTreeSet};
use std::io::Read;
use std::path::{Path, PathBuf};
use std::sync::Mutex;
use std::sync::atomic::{AtomicU64, Ordering};
use std::time::{Duration, Instant};

use crate::game::kiln_jvm::{
    HudOp, JavaHost, Play, Reply, Return, ServerLink, hex, java_program, unhex,
};

/// The mod's id (its settings folder and Options > Mods row), name and version.
pub const ID: &str = "kiln";
pub const NAME: &str = "Kiln";
pub const VERSION: &str = "0.4.8";
pub const DESCRIPTION: &str = "Runs Fabric mods (.jar) from <game folder>/kiln/mods/ in Horizon: client mods, structure mods, and the code of mods Horizon's mod browser imports only partly.";
/// The same box as Horizon's own HUD mods: translucent black, white text with a shadow.
pub const BACKGROUND: u32 = 0x7800_0000;
pub const INK: u32 = 0xFF_FFFF;
pub const TROUBLE: u32 = 0xFF_5555;
/// The oldest Java Kiln's own classes run on. Mods may need a newer one (those for 26.3 need 25): Kiln
/// reads it from their classes ([`JavaMod::java`]).
pub const MIN_JAVA: u32 = 21;
/// A client tick's length: with no game tick for this long (paused, on the title screen), Kiln makes one.
pub const TICK: Duration = Duration::from_millis(50);
/// How long Kiln's status shows after it starts, in ticks, when nothing is wrong.
pub const STATUS_TICKS: i64 = 100;
/// The GUI size until the HUD first draws.
pub const DEFAULT_GUI: (u32, u32) = (427, 240);
/// How long the integrated server waits for a Java mod's answer. A mod that takes longer costs that one action,
/// which goes on as Horizon's own.
pub const CALL_TIMEOUT: Duration = Duration::from_millis(250);
/// Horizon's channel for mods that carries the Java mods' own payloads, every type on it (kiln/java/kiln/Net.java).
pub const NET_CHANNEL: &str = "kiln:payload";

/// A Java mod as its fabric.mod.json describes it.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct JavaMod {
    pub id: String,
    pub name: String,
    pub version: String,
    /// Its `description`, for its row in Options > Mods.
    pub description: String,
    /// The classes of its `main` entrypoints: Fabric runs them on the client too, before every `client` one.
    pub main: Vec<String>,
    /// The classes of its `client` entrypoints.
    pub client: Vec<String>,
    /// The oldest Java its entrypoint classes run on (their class file version); 0 when unknown.
    pub java: u32,
    /// The jars inside it Fabric loads as mods of their own (`jars`: `META-INF/jars/lib.jar`).
    pub nested: Vec<String>,
    /// Its Mod Menu entrypoints (`modmenu`): its settings page (D-094).
    pub modmenu: Vec<String>,
    /// Whether it has mixin configs (`mixins`): Kiln's Java applies them even when it has no entrypoint (D-099).
    pub mixins: bool,
}

/// The game's state as Java mods see it, once a tick.
#[derive(Clone, Debug, Default, PartialEq)]
pub struct Snapshot {
    /// In a world, the player there or not yet (travelling between dimensions): not on the title screen.
    pub connected: bool,
    /// The player is in a world (Horizon has no player while it loads a dimension).
    pub in_world: bool,
    pub paused: bool,
    pub game_time: u64,
    /// `minecraft:overworld`, ...; empty outside a world.
    pub dimension: String,
    /// Every advancement the player has made, when that may have changed since the last snapshot.
    pub advancements: Option<Vec<String>>,
    /// The window's player, in a world: where they are, their health, game mode and hands.
    pub player: Option<PlayerSnapshot>,
    /// What the window's player wears and holds, with each item's damage: what HUD mods read.
    pub equipment: Vec<Worn>,
    /// The HUD is hidden (F1).
    pub hud_hidden: bool,
    /// The debug screen (F3) is open.
    pub debug_shown: bool,
    /// The window's world and player beyond the above, for client mods (`ClientLevel`, `LocalPlayer`).
    pub state: WindowState,
    /// The key of each Java mod's key Kiln knows ([`mod_keys`]), by its name: the one the player chose on the mod's
    /// page in Options > Mods, else the mod's own.
    pub keys: BTreeMap<String, String>,
}

/// What the window's game knows of its world and player that client mods read: time, weather, height, light where
/// the player stands, the player's food, abilities, motion, rotation and inventory, the options and the keys held.
#[derive(Clone, Debug, Default, PartialEq)]
pub struct WindowState {
    /// A world of this computer's own (`Minecraft.hasSingleplayerServer`).
    pub single_player: bool,
    /// That world's folder's name (`LevelStorageAccess.getLevelId`: a mod's files per world, D-167); empty otherwise.
    pub level: String,
    /// The server the window's game is on in multiplayer, as the player gave its address (`Minecraft.getCurrentServer`'s
    /// `ip`: a mod's files per server, JEI's bookmarks, D-178); empty in single player.
    pub server: String,
    /// The time of day in ticks, counting past 24000.
    pub day_time: i64,
    /// Rain and thunder, 0 to 1.
    pub weather: (f32, f32),
    /// The lowest block y and the world's height.
    pub height: (i32, i32),
    /// Block and sky light where the player stands, 0 to 15.
    pub light: (u8, u8),
    /// Food level and saturation.
    pub food: (u32, f32),
    pub flying: bool,
    pub may_fly: bool,
    pub invulnerable: bool,
    pub on_ground: bool,
    pub in_water: bool,
    pub sprinting: bool,
    /// Gliding with an elytra.
    pub fall_flying: bool,
    /// Blocks a tick.
    pub velocity: [f64; 3],
    /// Yaw and pitch in degrees.
    pub rotation: (f32, f32),
    /// The hotbar slot in hand, 0 to 8.
    pub selected: usize,
    /// The 36 main inventory slots that hold something: (slot, the item as `Worn`, its slot name unused), and the open
    /// crafting grid's cells as the game numbers them (100 and up, D-166).
    pub inventory: Vec<(usize, Worn)>,
    /// The options mods read: field of view in degrees, render distance in chunks, GUI scale and language
    /// (`en_us`).
    pub fov: f32,
    pub render_distance: i32,
    pub gui_scale: u32,
    /// The brightness option (`gamma`: 0 to 1 in the game's slider, more as a fullbright mod sets it, D-192).
    pub gamma: f32,
    /// Whether the game shows subtitles (an option a mod may change, D-105).
    pub show_subtitles: bool,
    /// The player's abilities' flying speed (vanilla's 0.05).
    pub flying_speed: f32,
    /// The player ran into a wall this tick (`Entity.horizontalCollision`), and their main arm is the left one (the
    /// Main Hand option).
    pub horizontal_collision: bool,
    pub left_handed: bool,
    /// The wall only brushed, not run into (`Entity.minorHorizontalCollision`: a sprint goes on through it).
    pub minor_horizontal_collision: bool,
    /// What the crosshair is on (`Minecraft.hitResult`): a block's position, the face's direction and the point hit;
    /// `None` for nothing within reach. An entity in front counts as nothing: Kiln's Java knows no entity but the
    /// player.
    pub hit: Option<([i32; 3], [i32; 3], [f64; 3])>,
    /// The camera (`CameraType`, F5): 0 first person, 1 third person behind, 2 third person in front.
    pub camera: u8,
    /// The debug screen's entries that aren't `never` (`DebugScreenEntryList`), as `<path>:<alwaysOn|inOverlay>`,
    /// and the paths of those that show now (`isCurrentlyEnabled`: by the F3 overlay and the world's reduced debug
    /// info).
    pub debug: Vec<String>,
    pub debug_on: Vec<String>,
    /// The window's player's fishing hook, when one is out: where it is, and how far it moved in the last tick.
    pub hook: Option<[f64; 6]>,
    pub language: String,
    /// The keys held down now, by name (`key.keyboard.space`).
    pub held: Vec<String>,
    /// The world's difficulty (`peaceful`, `easy`, `normal`, `hard`) and the biome where the player stands.
    pub difficulty: String,
    pub biome: String,
    /// Armour points: the `minecraft:armor` modifiers of what the player wears, added up.
    pub armor: f64,
    /// The blocks around the player's feet, 3 by 3 by 3 (x, then y, then z, from one below each), by name.
    pub blocks: Vec<String>,
    /// The players the connection lists (the player list, Tab), the window's own first in single player.
    pub players: Vec<Listed>,
    /// The window's size in its pixels and the GUI scale (window pixels to a GUI pixel).
    pub window: [u32; 3],
    /// The pointer, in GUI pixels from the window's top left.
    pub cursor: [f32; 2],
    /// The left, middle and right buttons held, and the pointer grabbed (playing, no screen open).
    pub mouse: [bool; 4],
    /// The player's effects (`LivingEntity.getActiveEffects`).
    pub effects: Vec<ActiveEffect>,
    /// The screen open, as the game names it (`playing` for none, `title`, `chat`, `inventory`, `paused`, ...).
    pub screen: String,
    /// The chunks of the player's level the window's game has, by chunk x and z: Kiln tells Java which came and
    /// went (`ClientChunkEvents`).
    pub chunks: Vec<[i32; 2]>,
    /// The blocks of chunks Java asked for (`wanted_chunks`, D-186), by chunk x and z: each section from the lowest,
    /// its palette of block states joined by `|`, then `#` and each cell's entry (`y << 8 | z << 4 | x`) as hex; the
    /// sections joined by `;`. Empty for a chunk the game hasn't.
    pub chunk_blocks: Vec<(i32, i32, String)>,
    /// The messages that came to the chat since the last snapshot, oldest first, as plain text: a player's (true)
    /// or the game's (false). Not the lines Kiln's mods wrote there themselves.
    pub messages: Vec<(bool, String)>,
    /// Each of `messages`' chat component as the game's JSON, by the same index (D-211): Java builds the mods'
    /// `Component` from it, translations kept (a death message is `death.attack.*`). Missing or empty: plain text.
    pub message_components: Vec<String>,
    /// The mobs and animals the window's game has near the player (`ClientLevel.entitiesForRendering`, D-211): the
    /// game's entity number, its type (`minecraft:cow`) and where it is. Within the render distance, the nearest
    /// first, at most `MOBS_SENT`.
    pub mobs: Vec<(u32, String, [f64; 3])>,
    /// The window doesn't have the system's focus (`Minecraft.isWindowActive`'s false, D-232): the player is in
    /// another window. False, the default, is a focused window.
    pub unfocused: bool,
    /// The volume sliders, 0 to 1, by `SoundSource`'s order (Horizon's): the master volume first (D-232).
    pub volumes: [f32; 11],
}

/// The most mobs a tick carries to Java (`WindowState::mobs`): the nearest, as a client mod sees the world around.
pub const MOBS_SENT: usize = 512;

/// An effect on the player: its id (`speed`, a mod's `<namespace>:<path>`), ticks left (-1 for ever), amplifier, and
/// whether it is ambient (a beacon's) and shows its particles.
#[derive(Clone, Debug, Default, PartialEq)]
pub struct ActiveEffect {
    pub id: String,
    pub duration: i32,
    pub amplifier: i32,
    pub ambient: bool,
    pub visible: bool,
}

/// A player the connection lists: who, their game mode (0 survival to 3 spectator) and latency in milliseconds
/// (negative when unknown).
#[derive(Clone, Debug, Default, PartialEq)]
pub struct Listed {
    pub uuid: u128,
    pub name: String,
    pub game_mode: i32,
    pub latency: i32,
}

/// The `chunks` command's fields: the chunks in `now` and not `before`, then those in `before` and not `now`, each
/// `x,z` joined by `;`.
pub fn chunk_changes(
    before: &BTreeSet<(i32, i32)>,
    now: &BTreeSet<(i32, i32)>,
) -> (String, String) {
    let list = |set: std::collections::btree_set::Difference<'_, (i32, i32)>| {
        set.map(|(x, z)| format!("{x},{z}"))
            .collect::<Vec<_>>()
            .join(";")
    };
    (list(now.difference(before)), list(before.difference(now)))
}

/// `name` with what isn't a letter, digit, `_`, `.` or `-` as `%XX` (UTF-8): a player's name in a tick field.
fn percent(name: &str) -> String {
    let mut out = String::with_capacity(name.len());
    for b in name.bytes() {
        if b.is_ascii_alphanumeric() || matches!(b, b'_' | b'.' | b'-') {
            out.push(b as char);
        } else {
            out.push_str(&format!("%{b:02X}"));
        }
    }
    out
}

impl WindowState {
    /// As the tick carries it: `key=value` pairs joined by `;` (Host.java reads them).
    pub fn field(&self) -> String {
        let flag = |b: bool| if b { "1" } else { "0" };
        [
            format!("single={}", flag(self.single_player)),
            format!("day={}", self.day_time),
            format!("rain={}", self.weather.0),
            format!("thunder={}", self.weather.1),
            format!("min_y={}", self.height.0),
            format!("height={}", self.height.1),
            format!("block_light={}", self.light.0),
            format!("sky_light={}", self.light.1),
            format!("food={}", self.food.0),
            format!("saturation={}", self.food.1),
            format!("flying={}", flag(self.flying)),
            format!("may_fly={}", flag(self.may_fly)),
            format!("invulnerable={}", flag(self.invulnerable)),
            format!("on_ground={}", flag(self.on_ground)),
            format!("in_water={}", flag(self.in_water)),
            format!("sprinting={}", flag(self.sprinting)),
            format!("fall_flying={}", flag(self.fall_flying)),
            format!(
                "velocity={} {} {}",
                self.velocity[0], self.velocity[1], self.velocity[2]
            ),
            format!("yaw={}", self.rotation.0),
            format!("pitch={}", self.rotation.1),
            format!("selected={}", self.selected),
            format!("fov={}", self.fov),
            format!("render_distance={}", self.render_distance),
            format!("gui_scale={}", self.gui_scale),
            format!("gamma={}", self.gamma),
            format!("subtitles={}", flag(self.show_subtitles)),
            format!("unfocused={}", flag(self.unfocused)),
            format!(
                "volumes={}",
                self.volumes
                    .iter()
                    .map(|v| v.to_string())
                    .collect::<Vec<_>>()
                    .join(" ")
            ),
            format!("flying_speed={}", self.flying_speed),
            format!("horizontal_collision={}", flag(self.horizontal_collision)),
            format!("left_handed={}", flag(self.left_handed)),
            format!("minor_collision={}", flag(self.minor_horizontal_collision)),
            match self.hit {
                Some((pos, face, at)) => format!(
                    "hit=block {} {} {} {} {} {} {} {} {}",
                    pos[0], pos[1], pos[2], face[0], face[1], face[2], at[0], at[1], at[2]
                ),
                None => "hit=miss".to_owned(),
            },
            format!("camera={}", self.camera),
            format!("debug={}", self.debug.join(",")),
            format!("debug_on={}", self.debug_on.join(",")),
            match self.hook {
                Some(h) => format!("hook={} {} {} {} {} {}", h[0], h[1], h[2], h[3], h[4], h[5]),
                None => "hook=".to_owned(),
            },
            format!("language={}", self.language),
            format!("held={}", self.held.join(",")),
            format!("difficulty={}", self.difficulty),
            format!("biome={}", self.biome),
            format!("armor={}", self.armor),
            format!("blocks={}", self.blocks.join(",")),
            format!(
                "players={}",
                self.players
                    .iter()
                    .map(|p| format!(
                        "{:032x}:{}:{}:{}",
                        p.uuid,
                        p.game_mode,
                        p.latency,
                        percent(&p.name)
                    ))
                    .collect::<Vec<_>>()
                    .join(",")
            ),
            format!(
                "window={},{},{}",
                self.window[0], self.window[1], self.window[2]
            ),
            format!("backs_taken={}", BACKS_TAKEN.get()),
            format!("cursor={},{}", self.cursor[0], self.cursor[1]),
            format!("mouse={}", self.mouse.map(flag).join(",")),
            format!(
                "effects={}",
                self.effects
                    .iter()
                    .map(|e| format!(
                        "{}:{}:{}:{}:{}",
                        percent(&e.id),
                        e.duration,
                        e.amplifier,
                        flag(e.ambient),
                        flag(e.visible)
                    ))
                    .collect::<Vec<_>>()
                    .join(",")
            ),
            format!("level={}", percent(&self.level)),
            format!("server={}", percent(&self.server)),
            format!("screen={}", percent(&self.screen)),
            format!(
                "mobs={}",
                self.mobs
                    .iter()
                    .map(|(id, kind, at)| format!(
                        "{id} {} {} {} {}",
                        percent(kind),
                        at[0],
                        at[1],
                        at[2]
                    ))
                    .collect::<Vec<_>>()
                    .join(",")
            ),
        ]
        .join(";")
    }

    /// The inventory as the tick carries it: `<slot> <item> <count> <damage> <max damage>`, joined by `;`.
    pub fn inventory_field(&self) -> String {
        self.inventory
            .iter()
            .map(|(slot, w)| {
                format!(
                    "{slot} {} {} {} {}",
                    w.item, w.count, w.damage, w.max_damage
                )
            })
            .collect::<Vec<_>>()
            .join(";")
    }
}

/// An item the window's player wears or holds.
#[derive(Clone, Debug, Default, PartialEq)]
pub struct Worn {
    /// Vanilla's `EquipmentSlot` name: `mainhand`, `offhand`, `feet`, `legs`, `chest`, `head`.
    pub slot: &'static str,
    pub item: String,
    pub count: u32,
    pub damage: u32,
    /// The item's durability; 0 when it has none.
    pub max_damage: u32,
}

/// A fabric.mod.json's id, name, version and main and client entrypoints (each a class name, or
/// `{"value": ...}`).
pub fn read_manifest(json: &str) -> Result<JavaMod, String> {
    let v: serde_json::Value =
        serde_json::from_str(json).map_err(|e| format!("fabric.mod.json: {e}"))?;
    let text = |key: &str| v.get(key).and_then(|x| x.as_str()).map(str::to_owned);
    let id = text("id").ok_or("fabric.mod.json has no id")?;
    let entrypoints = |kind: &str| -> Vec<String> {
        v.pointer(&format!("/entrypoints/{kind}"))
            .and_then(|e| e.as_array())
            .map(|entries| {
                entries
                    .iter()
                    .filter_map(|e| e.as_str().or_else(|| e.get("value")?.as_str()))
                    .map(str::to_owned)
                    .collect()
            })
            .unwrap_or_default()
    };
    Ok(JavaMod {
        name: text("name").unwrap_or_else(|| id.clone()),
        version: text("version").unwrap_or_default(),
        description: text("description").unwrap_or_default(),
        main: entrypoints("main"),
        client: entrypoints("client"),
        modmenu: entrypoints("modmenu"),
        nested: v
            .get("jars")
            .and_then(|j| j.as_array())
            .map(|jars| {
                jars.iter()
                    .filter_map(|j| j.get("file")?.as_str().map(str::to_owned))
                    .collect()
            })
            .unwrap_or_default(),
        mixins: v
            .get("mixins")
            .and_then(|m| m.as_array())
            .is_some_and(|m| !m.is_empty()),
        id,
        java: 0,
    })
}

impl JavaMod {
    /// Its entrypoints Kiln runs: `main` ones, then `client` ones.
    pub fn entrypoints(&self) -> usize {
        self.main.len() + self.client.len()
    }
}

/// The oldest Java a class file runs on, from its version: 69 is Java 25, 65 Java 21.
pub fn class_java(class: &[u8]) -> Option<u32> {
    if class.get(..4)? != [0xCA, 0xFE, 0xBA, 0xBE] {
        return None;
    }
    let major = u32::from(u16::from_be_bytes([*class.get(6)?, *class.get(7)?]));
    major.checked_sub(44).filter(|&java| java > 0)
}

/// The fabric.mod.json inside the jar `file`.
pub fn read_jar(file: &Path) -> Result<JavaMod, String> {
    let name = file
        .file_name()
        .map_or_else(String::new, |n| n.to_string_lossy().into_owned());
    let f = std::fs::File::open(file).map_err(|e| format!("{name}: {e}"))?;
    let mut jar = zip::ZipArchive::new(f).map_err(|e| format!("{name}: not a jar ({e})"))?;
    let mut entry = jar
        .by_name("fabric.mod.json")
        .map_err(|_| format!("{name}: no fabric.mod.json, not a Fabric mod"))?;
    let mut json = String::new();
    entry
        .read_to_string(&mut json)
        .map_err(|e| format!("{name}: {e}"))?;
    drop(entry);
    let mut m = read_manifest(&json).map_err(|e| format!("{name}: {e}"))?;
    // Only the head of each entrypoint class: its version (a missing class fails later, with a clear message).
    for class in m.main.iter().chain(&m.client) {
        let mut head = Vec::with_capacity(8);
        if let Ok(entry) = jar.by_name(&format!("{}.class", class.replace('.', "/"))) {
            let _ = entry.take(8).read_to_end(&mut head);
        }
        m.java = m.java.max(class_java(&head).unwrap_or(0));
    }
    Ok(m)
}

/// The jars `file` carries inside it (Fabric's jar-in-jar), and theirs, unpacked into `cache` (each written again
/// only when its size changed): each before the jar that carries it, as a library comes before the mod that uses it.
pub fn nested_jars(file: &Path, cache: &Path) -> Vec<PathBuf> {
    let Ok(m) = read_jar(file) else {
        return Vec::new();
    };
    if m.nested.is_empty() {
        return Vec::new();
    }
    let Some(mut jar) = std::fs::File::open(file)
        .ok()
        .and_then(|f| zip::ZipArchive::new(f).ok())
    else {
        return Vec::new();
    };
    let mut found = Vec::new();
    for name in &m.nested {
        let Ok(mut entry) = jar.by_name(name) else {
            continue;
        };
        let Some(file_name) = Path::new(name).file_name() else {
            continue;
        };
        let out = cache.join(file_name);
        let same = std::fs::metadata(&out).is_ok_and(|meta| meta.len() == entry.size());
        if !same {
            let mut bytes = Vec::new();
            if entry.read_to_end(&mut bytes).is_err()
                || std::fs::create_dir_all(cache).is_err()
                || std::fs::write(&out, bytes).is_err()
            {
                continue;
            }
        }
        drop(entry);
        found.extend(nested_jars(&out, cache));
        found.push(out);
    }
    found
}

/// The `.jar` files in `dir`, by name.
pub fn jars(dir: &Path) -> Vec<PathBuf> {
    let mut found: Vec<PathBuf> = children(dir)
        .into_iter()
        .filter(|p| p.extension().is_some_and(|e| e.eq_ignore_ascii_case("jar")))
        .collect();
    found.sort();
    found
}

/// A Java mod's row in Options > Mods, and its switch: `kiln_<its id>`, never a native or content mod's id (a
/// content mod of the mod's own id is what Horizon's import made of its data, with a switch of its own).
pub fn row_id(mod_id: &str) -> String {
    format!("kiln_{mod_id}")
}

/// The Java mods turned off in Options > Mods, by their own ids: the game's off switches (`mod.<id>:false` in
/// `horizon-mods.txt`, the game's `settings.mods.disabled`) that are Kiln's rows.
pub fn off_ids<'a>(disabled: impl IntoIterator<Item = &'a str>) -> BTreeSet<String> {
    disabled
        .into_iter()
        .filter_map(|id| id.strip_prefix("kiln_"))
        .map(str::to_owned)
        .collect()
}

/// The ids `horizon-mods.txt` turns off (`mod.<id>:false`), as Horizon reads them.
pub fn disabled_in_list(text: &str) -> Vec<&str> {
    text.lines()
        .filter_map(|l| l.split_once(':'))
        .filter(|(_, v)| v.trim() == "false")
        .filter_map(|(k, _)| k.trim().strip_prefix("mod."))
        .collect()
}

/// The Java mods the player turned off, as the game last said (`set_off`): read by every thread that reads the
/// mods' jars (the content, the world's generation and loot, Java's start).
static OFF: Mutex<BTreeSet<String>> = Mutex::new(BTreeSet::new());

/// The chunks whose blocks Java asked for (`Reply::WantChunk`), sent with the next ticks (D-186).
static WANTED_CHUNKS: Mutex<BTreeSet<(i32, i32)>> = Mutex::new(BTreeSet::new());

/// At most `n` of the chunks Java asked for, taken: the game reads their blocks for the next tick's state.
pub fn wanted_chunks(n: usize) -> Vec<(i32, i32)> {
    let mut wanted = WANTED_CHUNKS.lock().unwrap_or_else(|e| e.into_inner());
    let taken: Vec<(i32, i32)> = wanted.iter().take(n).copied().collect();
    for chunk in &taken {
        wanted.remove(chunk);
    }
    taken
}

/// The Java mods turned off now; true when that changed.
pub fn set_off(off: BTreeSet<String>) -> bool {
    let mut now = OFF.lock().unwrap_or_else(|e| e.into_inner());
    if *now == off {
        return false;
    }
    *now = off;
    true
}

/// `kiln off <mod id>` or `kiln on <mod id>`, a Java mod's switch typed in the chat: the mod and whether it goes on.
pub fn switch_command(command: &str) -> Option<(String, bool)> {
    let mut words = command.split_whitespace();
    if words.next() != Some("kiln") {
        return None;
    }
    let on = match words.next()? {
        "on" => true,
        "off" => false,
        _ => return None,
    };
    let id = words.next()?;
    words.next().is_none().then(|| (id.to_owned(), on))
}

/// `kiln` or `kiln mods` typed in the chat: Kiln's overlay (D-113), for a keyboard whose F7 doesn't reach the game
/// (a Mac's top row sends its media keys unless fn is held) and for a player who doesn't know the key.
pub fn overlay_command(command: &str) -> bool {
    let mut words = command.split_whitespace();
    words.next() == Some("kiln")
        && matches!(words.next(), None | Some("mods"))
        && words.next().is_none()
}

/// The mods turned off now, comma-separated, when that changed since `sent` (which becomes it).
fn soft_switches(sent: &mut BTreeSet<String>) -> Option<String> {
    let now = OFF.lock().unwrap_or_else(|e| e.into_inner()).clone();
    if *sent == now {
        return None;
    }
    let ids = now.iter().map(String::as_str).collect::<Vec<_>>().join(",");
    *sent = now;
    Some(ids)
}

/// Whether the Java mod `mod_id` is on in Options > Mods (every mod is, until turned off).
pub fn mod_on(mod_id: &str) -> bool {
    !OFF.lock()
        .unwrap_or_else(|e| e.into_inner())
        .contains(mod_id)
}

/// The jars in `kiln_dir/mods` of the mods that are on, by name: a mod turned off in Options > Mods is left out as
/// if its jar weren't there (its code, its structures, the blocks Kiln carries for it, its loot). A jar Kiln can't
/// read stays, so that what's wrong with it is said where it is read.
pub fn mod_jars(kiln_dir: &Path) -> Vec<PathBuf> {
    jars(&kiln_dir.join("mods"))
        .into_iter()
        .filter(|jar| read_jar(jar).map_or(true, |m| mod_on(&m.id)))
        .collect()
}

/// The mods' jars as Java's class path has them: each mod's jar-in-jar libraries (Tech Reborn carries RebornCore and
/// Energy, SpongeSucc Thighfigured) before it, unpacked into `kiln_dir/cache/jars`, each jar once. Every Java Kiln
/// starts takes them so: the game's and the one that runs the mods' loot changes before the world (D-101).
pub fn with_nested(files: &[PathBuf], kiln_dir: &Path) -> Vec<PathBuf> {
    let cache = kiln_dir.join("cache").join("jars");
    let mut ordered: Vec<PathBuf> = Vec::new();
    for file in files {
        for jar in nested_jars(file, &cache).into_iter().chain([file.clone()]) {
            let name = jar.file_name().map(|n| n.to_owned());
            if !ordered
                .iter()
                .any(|j| j.file_name().map(|n| n.to_owned()) == name)
            {
                ordered.push(jar);
            }
        }
    }
    ordered
}

/// Options > Mods' rows for the Java mods in `kiln_dir/mods`, after Kiln's own: (row id, title, description, keys),
/// one for each jar there (a jar it carries inside is part of it), each with its own switch, and its page the mod's
/// keys as Java last told of them ([`page_keys`]): the game lists the rows when it starts, before Java does.
pub fn mod_rows(kiln_dir: &Path) -> Vec<(String, String, String, Vec<[String; 3]>)> {
    load_keys(kiln_dir);
    let mut rows: Vec<(String, String, String, Vec<[String; 3]>)> = Vec::new();
    for jar in jars(&kiln_dir.join("mods")) {
        let Ok(m) = read_jar(&jar) else { continue };
        if rows.iter().any(|(id, ..)| *id == row_id(&m.id)) {
            continue;
        }
        let kind = if m.entrypoints() == 0 && m.mixins {
            "Mixins only, from kiln/mods: Kiln's Java applies them to the game's classes."
        } else if m.entrypoints() == 0 {
            "Data only, from kiln/mods: Kiln brings its structures into new worlds."
        } else {
            "A Java mod in kiln/mods, run by Kiln."
        };
        let own = m.description.trim();
        let description = if own.is_empty() {
            format!("{kind} Turned off, Kiln leaves it out from the next world you enter.")
        } else {
            format!("{own}\n{kind} Turned off, Kiln leaves it out from the next world you enter.")
        };
        let title = if m.version.is_empty() {
            m.name.clone()
        } else {
            format!("{} {}", m.name, m.version)
        };
        let id = row_id(&m.id);
        let keys = page_keys(&id);
        rows.push((id, title, description, keys));
    }
    rows
}

/// The Java mods' keys (`KeyMappingHelper.registerKeyMapping`), which the player can bind on each mod's page in
/// Options > Mods as Horizon's own native mods' keys: the game keeps the choice in `horizon-mods.txt`
/// (`key.kiln_<mod id>.<key's name>:<key>`), and Kiln tells Java (`bind`).
#[derive(Debug, Default)]
struct ModKeys {
    /// By the mod's row (`kiln_<mod id>`): each key's name, its group's name, its label and the key the mod set, in
    /// the order the mod registered them.
    rows: BTreeMap<String, Vec<[String; 4]>>,
    /// The rows Java told of since it started: what it says next for one adds to it; the first word replaced what
    /// Kiln kept from before.
    heard: BTreeSet<String>,
    /// Changed since the game's pages last took them ([`take_changed_keys`]).
    changed: bool,
    /// The keys the mods set themselves (`KeyMapping.setKey`) since the game last took them ([`take_chosen_keys`]):
    /// each key's name and the key.
    chosen: Vec<(String, String)>,
}

static MOD_KEYS: Mutex<ModKeys> = Mutex::new(ModKeys {
    rows: BTreeMap::new(),
    heard: BTreeSet::new(),
    changed: false,
    chosen: Vec::new(),
});

fn mod_keys_lock() -> std::sync::MutexGuard<'static, ModKeys> {
    MOD_KEYS.lock().unwrap_or_else(|e| e.into_inner())
}

/// Where Kiln keeps the mods' keys between games, so that their pages list them before Java starts.
fn keys_file(kiln_dir: &Path) -> PathBuf {
    kiln_dir.join("cache").join("keys.json")
}

/// The mods' keys Kiln kept (`keys_file`), for the rows Java hasn't told of since it started.
fn load_keys(kiln_dir: &Path) {
    let Ok(text) = std::fs::read_to_string(keys_file(kiln_dir)) else {
        return;
    };
    let Ok(kept) = serde_json::from_str::<BTreeMap<String, Vec<[String; 4]>>>(&text) else {
        log::warn!(
            "Kiln: {} didn't read: the mods' keys show once Java says them",
            keys_file(kiln_dir).display()
        );
        return;
    };
    let mut keys = mod_keys_lock();
    for (row, list) in kept {
        if !keys.heard.contains(&row) && keys.rows.get(&row) != Some(&list) {
            keys.rows.insert(row, list);
            keys.changed = true;
        }
    }
}

/// Java told of `keys` for the mod's row `row`: the first word since Java started replaces what Kiln kept for it
/// (a mod updated may have dropped one), the next ones add to it. Kept in `kiln_dir` when that changed anything.
fn learn_keys(kiln_dir: &Path, row: &str, keys: Vec<[String; 4]>) {
    let mut all = mod_keys_lock();
    let first = all.heard.insert(row.to_owned());
    let before = all.rows.get(row).cloned().unwrap_or_default();
    let mut now = if first { Vec::new() } else { before.clone() };
    for key in keys {
        match now.iter_mut().find(|k| k[0] == key[0]) {
            Some(known) => *known = key,
            None => now.push(key),
        }
    }
    if now == before {
        return;
    }
    if now.is_empty() {
        all.rows.remove(row);
    } else {
        all.rows.insert(row.to_owned(), now);
    }
    all.changed = true;
    let file = keys_file(kiln_dir);
    let written = serde_json::to_string_pretty(&all.rows)
        .map_err(|e| e.to_string())
        .and_then(|json| {
            std::fs::create_dir_all(file.parent().unwrap_or(kiln_dir))
                .map_err(|e| e.to_string())?;
            std::fs::write(&file, json).map_err(|e| e.to_string())
        });
    if let Err(e) = written {
        log::warn!("Kiln: can't keep the mods' keys in {}: {e}", file.display());
    }
}

/// The page's Key Binds rows for the mod's row `row` in Options > Mods: each key's name, label and the key the mod
/// set. A mod whose keys are in more than one group has each label after its group's name (`Camera: Snap`).
pub fn page_keys(row: &str) -> Vec<[String; 3]> {
    let all = mod_keys_lock();
    let Some(keys) = all.rows.get(row) else {
        return Vec::new();
    };
    let groups: BTreeSet<&str> = keys.iter().map(|k| k[1].as_str()).collect();
    keys.iter()
        .map(|[name, group, label, key]| {
            let label = if groups.len() > 1 {
                format!("{group}: {label}")
            } else {
                label.clone()
            };
            [name.clone(), label, key.clone()]
        })
        .collect()
}

/// Every mod key Kiln knows: (the mod's row, the key's name, the key the mod set), for the game to read the player's
/// choice (`native_mod_key`) into [`Snapshot::keys`].
pub fn mod_keys() -> Vec<(String, String, String)> {
    mod_keys_lock()
        .rows
        .iter()
        .flat_map(|(row, keys)| {
            keys.iter()
                .map(|k| (row.clone(), k[0].clone(), k[3].clone()))
        })
        .collect()
}

/// The pages' Key Binds rows by mod row ([`page_keys`]), when Java told of keys since the game last took them: the
/// game puts them on the pages it made, so a mod's keys can be bound in the game that first ran it.
pub fn take_changed_keys() -> Option<BTreeMap<String, Vec<[String; 3]>>> {
    let rows: Vec<String> = {
        let mut all = mod_keys_lock();
        if !std::mem::take(&mut all.changed) {
            return None;
        }
        all.rows
            .keys()
            .cloned()
            .chain(all.heard.iter().cloned())
            .collect()
    };
    Some(
        rows.into_iter()
            .map(|row| {
                let keys = page_keys(&row);
                (row, keys)
            })
            .collect(),
    )
}

/// A mod set its key `name` to `key` itself (`KeyMapping.setKey`): for the game to keep, with the next frame.
fn key_chosen(name: String, key: String) {
    mod_keys_lock().chosen.push((name, key));
}

/// The keys the mods set themselves since the last call, for the game to keep as the player's choice on the mods'
/// pages (`horizon-mods.txt`): each (the mod's row, the key's name, the key), with no key when it is the mod's own
/// again (the game forgets the choice). The last word on a key wins. A key of no mod's page (vanilla's own, which
/// Horizon's Controls keep, or one Java never told of) the game doesn't keep: Java follows it in this game only.
pub fn take_chosen_keys() -> Vec<(String, String, Option<String>)> {
    let mut all = mod_keys_lock();
    let chosen = std::mem::take(&mut all.chosen);
    let mut out: Vec<(String, String, Option<String>)> = Vec::new();
    for (name, key) in chosen {
        let Some((row, default)) = all
            .rows
            .iter()
            .find_map(|(row, keys)| keys.iter().find(|k| k[0] == name).map(|k| (row, &k[3])))
        else {
            log::info!(
                "Kiln: a mod set {name} to {key}, a key of no mod's page: kept for this game only"
            );
            continue;
        };
        let key = (key != *default).then_some(key);
        out.retain(|(r, n, _)| !(r == row && *n == name));
        out.push((row.clone(), name, key));
    }
    out
}

// --- Keys with modifiers (D-178) ----------------------------------------------------------------------------------
//
// A mod's mapping that keeps modifiers itself (JEI's: Ctrl+O toggles its list) comes from Java as a combination,
// `control+key.keyboard.o` (the modifiers in the order control, shift, alt, command; `+key.keyboard.r` for one with
// none): KeyMapping.kiln$combo. The game's page shows and keeps a key by a string it draws as it is when it is no key's
// name, so a combination is kept there as it is shown (`CTRL + O`, as JEI's own Controls row reads), and Kiln keeps
// what each shown string stands for (`cache/combos.json`). A key bound on the page with modifiers held (the player
// holds Ctrl and presses O) is that combination.

/// The modifiers of a combination, in its order, with how the page shows each.
const COMBO_MODIFIERS: [(&str, &str); 4] = [
    ("control", "CTRL"),
    ("shift", "SHIFT"),
    ("alt", "ALT"),
    ("command", "CMD"),
];
/// The order JEI shows them in (`FabricKeyModifier.getCombinedName`: Cmd outermost, Alt next to the key).
const SHOWN_ORDER: [&str; 4] = ["command", "control", "shift", "alt"];

/// A combination's modifiers and key (`control+key.keyboard.o`: control, `key.keyboard.o`); None for a key's name alone
/// (a mapping that keeps no modifiers).
pub fn split_combo(key: &str) -> Option<(Vec<&str>, &str)> {
    let at = key.rfind('+')?;
    let (modifiers, name) = (&key[..at], &key[at + 1..]);
    if !name.starts_with("key.") {
        return None;
    }
    let modifiers: Vec<&str> = modifiers.split('+').filter(|m| !m.is_empty()).collect();
    modifiers
        .iter()
        .all(|m| COMBO_MODIFIERS.iter().any(|(c, _)| c == m))
        .then_some((modifiers, name))
}

/// The combination of `modifiers` (any order, unknown ones left out) with the key `key`.
pub fn combo(modifiers: &[&str], key: &str) -> String {
    let known: Vec<&str> = COMBO_MODIFIERS
        .iter()
        .map(|(m, _)| *m)
        .filter(|m| modifiers.contains(m))
        .collect();
    format!("{}+{key}", known.join("+"))
}

/// Whether `key` (`key.keyboard.left.control`) is a modifier key itself: a combination's key, never its modifier.
pub fn is_modifier_key(key: &str) -> bool {
    ["shift", "control", "alt", "win"].iter().any(|m| {
        key == format!("key.keyboard.left.{m}") || key == format!("key.keyboard.right.{m}")
    })
}

/// What each shown combination stands for (`CTRL + O` → `control+key.keyboard.o`), as Kiln keeps it.
static COMBOS: Mutex<BTreeMap<String, String>> = Mutex::new(BTreeMap::new());

fn combos_file(kiln_dir: &Path) -> PathBuf {
    kiln_dir.join("cache").join("combos.json")
}

/// The combinations Kiln kept from earlier games, once.
fn load_combos(kiln_dir: &Path) {
    static LOADED: std::sync::Once = std::sync::Once::new();
    LOADED.call_once(|| {
        if let Ok(text) = std::fs::read_to_string(combos_file(kiln_dir))
            && let Ok(kept) = serde_json::from_str::<BTreeMap<String, String>>(&text)
        {
            COMBOS
                .lock()
                .unwrap_or_else(|e| e.into_inner())
                .extend(kept);
        }
    });
}

/// How the page shows the key or combination `key`: a key's name as it is (the game draws it by its own name), a
/// combination as `CTRL + SHIFT + O` with `label` the game's name of its key (`O`), kept in `kiln_dir` so that the
/// choice the game keeps by that string reads back ([`combo_chosen`]).
pub fn combo_shown(kiln_dir: &Path, key: &str, label: &dyn Fn(&str) -> String) -> String {
    let Some((modifiers, name)) = split_combo(key) else {
        return key.to_owned();
    };
    if modifiers.is_empty() {
        return name.to_owned();
    }
    let mut shown: Vec<&str> = SHOWN_ORDER
        .iter()
        .filter(|m| modifiers.contains(m))
        .filter_map(|m| {
            COMBO_MODIFIERS
                .iter()
                .find(|(c, _)| c == m)
                .map(|(_, s)| *s)
        })
        .collect();
    let key_label = label(name);
    shown.push(&key_label);
    let shown = shown.join(" + ");
    load_combos(kiln_dir);
    let mut combos = COMBOS.lock().unwrap_or_else(|e| e.into_inner());
    let canonical = combo(&modifiers, name);
    if combos.get(&shown) != Some(&canonical) {
        combos.insert(shown.clone(), canonical);
        let file = combos_file(kiln_dir);
        let written = serde_json::to_string_pretty(&*combos)
            .map_err(|e| e.to_string())
            .and_then(|json| {
                std::fs::create_dir_all(file.parent().unwrap_or(kiln_dir))
                    .map_err(|e| e.to_string())?;
                std::fs::write(&file, json).map_err(|e| e.to_string())
            });
        if let Err(e) = written {
            log::warn!(
                "Kiln: can't keep the keys' combinations in {}: {e}",
                file.display()
            );
        }
    }
    shown
}

/// The key or combination for Java of what the game keeps for a mod's key (`value`: the player's choice on the page,
/// else `default`, the mod's own as Java said it). A mapping that keeps no modifiers: `value` as it is. One that does:
/// a combination, its default; a key's name chosen on the page is that key with no modifier (`+key.keyboard.p`); a
/// shown combination (`CTRL + P`) what Kiln kept for it, else read back from its words (`CTRL`, `SHIFT`, `ALT`,
/// `CMD`, and a one-letter key); what can't be read, the mod's own.
pub fn combo_chosen(kiln_dir: &Path, value: &str, default: &str) -> String {
    if split_combo(default).is_none() || split_combo(value).is_some() {
        return value.to_owned();
    }
    if value.starts_with("key.") {
        return format!("+{value}");
    }
    load_combos(kiln_dir);
    if let Some(kept) = COMBOS.lock().unwrap_or_else(|e| e.into_inner()).get(value) {
        return kept.clone();
    }
    let mut rest = value;
    let mut modifiers = Vec::new();
    'words: loop {
        for (name, shown) in COMBO_MODIFIERS {
            if let Some(after) = rest.strip_prefix(shown).and_then(|r| r.strip_prefix(" + ")) {
                modifiers.push(name);
                rest = after;
                continue 'words;
            }
        }
        break;
    }
    let mut chars = rest.chars();
    match (chars.next(), chars.next()) {
        (Some(c), None) if c.is_alphanumeric() => {
            combo(&modifiers, &format!("key.keyboard.{}", c.to_lowercase()))
        }
        _ => {
            log::warn!(
                "Kiln: the key {value:?} chosen on a mod's page doesn't read: the mod's own ({default}) for now"
            );
            default.to_owned()
        }
    }
}

/// What the page's rows held as the frame before last looked, by (row, key's name): a choice that changed since is
/// one the player just made ([`fresh_choice`]).
static SEEN_CHOICES: Mutex<Option<BTreeMap<(String, String), String>>> = Mutex::new(None);

/// Whether the player just chose `value` on row `row` for the key `name`: it changed since the last call for that
/// key. The first look at a key isn't a choice (the game read it from its file).
pub fn fresh_choice(row: &str, name: &str, value: Option<&str>) -> bool {
    let mut seen = SEEN_CHOICES.lock().unwrap_or_else(|e| e.into_inner());
    let seen = seen.get_or_insert_with(BTreeMap::new);
    let key = (row.to_owned(), name.to_owned());
    let value = value.unwrap_or_default().to_owned();
    match seen.insert(key, value.clone()) {
        Some(before) => before != value,
        None => false,
    }
}

/// Whose keys Java told of, by the mods' rows: the ones the entrypoint class `owner` registered are its mod's, even
/// none (it has none, or none any longer); with no owner (keys registered later), each key is the mod's whose id its
/// name holds (`key.<mod id>.<what>`). A key of no mod Kiln can tell keeps the mod's own key (logged).
fn keys_by_row(
    mods: &[&JavaMod],
    owner: &str,
    keys: Vec<[String; 4]>,
) -> Vec<(String, Vec<[String; 4]>)> {
    if !owner.is_empty() {
        return match mods
            .iter()
            .find(|m| m.main.iter().chain(&m.client).any(|c| c == owner))
        {
            Some(m) => vec![(row_id(&m.id), keys)],
            None => {
                log::warn!("Kiln: keys from {owner}, which is no mod's entrypoint");
                Vec::new()
            }
        };
    }
    let mut by_row: Vec<(String, Vec<[String; 4]>)> = Vec::new();
    for key in keys {
        let id = key[0].split('.').nth(1).unwrap_or_default();
        let Some(m) = mods.iter().find(|m| m.id == id) else {
            log::info!(
                "Kiln: {} is no mod's key Kiln can tell: it keeps the mod's own key",
                key[0]
            );
            continue;
        };
        let row = row_id(&m.id);
        match by_row.iter_mut().find(|(r, _)| *r == row) {
            Some((_, list)) => list.push(key),
            None => by_row.push((row, vec![key])),
        }
    }
    by_row
}

/// The bind lines Java needs for the keys `now` (name → key), after it heard `bound`: the ones it hasn't heard.
fn rebinds(
    bound: &mut BTreeMap<String, String>,
    now: &BTreeMap<String, String>,
) -> Vec<(String, String)> {
    let mut lines = Vec::new();
    for (name, key) in now {
        if bound.get(name) != Some(key) {
            bound.insert(name.clone(), key.clone());
            lines.push((name.clone(), key.clone()));
        }
    }
    lines
}

/// Kiln's folder beside options.txt, `<game folder>/kiln/`, from the mod's data folder
/// (`<game folder>/horizon-mods/.horizon-mod-data/kiln`).
pub fn kiln_dir(data_dir: &Path) -> PathBuf {
    data_dir.ancestors().nth(3).unwrap_or(data_dir).join("kiln")
}

/// Java's major version from a Java home's `release` file: `JAVA_VERSION="25.0.2"` is 25, `"1.8.0_402"` 8.
pub fn release_major(release: &str) -> Option<u32> {
    let line = release
        .lines()
        .find_map(|l| l.strip_prefix("JAVA_VERSION="))?;
    let version = line.trim().trim_matches('"');
    let mut parts = version.split(['.', '_', '-', '+']);
    let first: u32 = parts.next()?.parse().ok()?;
    if first == 1 {
        parts.next()?.parse().ok()
    } else {
        Some(first)
    }
}

fn children(dir: &Path) -> Vec<PathBuf> {
    std::fs::read_dir(dir)
        .map(|entries| entries.filter_map(|e| e.ok().map(|e| e.path())).collect())
        .unwrap_or_default()
}

/// Java homes that might be installed, in no order: JAVA_HOME, the Minecraft launcher's own runtimes and
/// the system's JDKs.
fn installed_javas(env: &dyn Fn(&str) -> Option<String>, user_home: Option<&Path>) -> Vec<PathBuf> {
    java_homes(kiln_jni::Os::HERE, env, user_home, &children)
}

/// The folders where Java homes may be on `os`, in no order (some hold no Java: [`find_java`] keeps those with a
/// `java` and a `release`), the folders' entries listed by `children`: JAVA_HOME; the Minecraft launcher's own
/// runtimes, `runtime/<component>/<platform>/<component>` (on macOS, its `jre.bundle/Contents/Home`; on Windows the
/// old launcher's in APPDATA, the Microsoft Store's and the installer's); the system's JDKs (on Windows the vendors'
/// folders in Program Files, where Temurin's installer puts it without setting JAVA_HOME unless asked; on macOS the
/// system's and the user's `JavaVirtualMachines` and Homebrew's).
fn java_homes(
    os: kiln_jni::Os,
    env: &dyn Fn(&str) -> Option<String>,
    user_home: Option<&Path>,
    children: &dyn Fn(&Path) -> Vec<PathBuf>,
) -> Vec<PathBuf> {
    use kiln_jni::Os;
    let join = |base: &Path, relative: &str| os.join(base, relative);
    let var = |name: &str| {
        env(name)
            .filter(|v| !v.trim().is_empty())
            .map(PathBuf::from)
    };
    let mut homes: Vec<PathBuf> = var("JAVA_HOME").into_iter().collect();
    // On macOS a JDK's bundle may be named for its home: `temurin-21.jdk` holds `Contents/Home`.
    if os == Os::MacOs {
        homes.extend(var("JAVA_HOME").map(|h| join(&h, "Contents/Home")));
    }
    let mut runtimes: Vec<PathBuf> = match os {
        Os::MacOs => user_home
            .map(|h| join(h, "Library/Application Support/minecraft/runtime"))
            .into_iter()
            .collect(),
        Os::Windows => [
            var("APPDATA").map(|a| join(&a, ".minecraft/runtime")),
            var("LOCALAPPDATA").map(|l| {
                join(
                    &l,
                    "Packages/Microsoft.4297127D64EC6_8wekyb3d8bbwe/LocalCache/Local/runtime",
                )
            }),
            var("ProgramFiles(x86)").map(|p| join(&p, "Minecraft Launcher/runtime")),
        ]
        .into_iter()
        .flatten()
        .collect(),
        Os::Linux => user_home
            .map(|h| join(h, ".minecraft/runtime"))
            .into_iter()
            .collect(),
    };
    // Horizon's own launcher keeps the Java it downloaded from Mojang (java-runtime-epsilon is Java 25) in the same
    // layout, in its data folder: HORIZON_LAUNCHER_HOME, else the system's data folder's `horizon-launcher` (D-221).
    let launcher_data: Option<PathBuf> = var("HORIZON_LAUNCHER_HOME").or_else(|| match os {
        Os::MacOs => user_home.map(|h| join(h, "Library/Application Support/horizon-launcher")),
        Os::Windows => var("APPDATA").map(|a| join(&a, "horizon-launcher")),
        Os::Linux => var("XDG_DATA_HOME")
            .filter(|x| x.is_absolute())
            .or_else(|| user_home.map(|h| join(h, ".local/share")))
            .map(|d| join(&d, "horizon-launcher")),
    });
    runtimes.extend(launcher_data.map(|d| join(&d, "minecraft/runtime")));
    for component in runtimes.iter().flat_map(|r| children(r)) {
        for platform in children(&component) {
            for home in children(&platform) {
                if os == Os::MacOs {
                    homes.push(join(&home, "jre.bundle/Contents/Home"));
                }
                homes.push(home);
            }
        }
    }
    match os {
        Os::MacOs => {
            let mut bundles = vec![PathBuf::from("/Library/Java/JavaVirtualMachines")];
            bundles.extend(user_home.map(|h| join(h, "Library/Java/JavaVirtualMachines")));
            for bundle in bundles.iter().flat_map(|b| children(b)) {
                homes.push(join(&bundle, "Contents/Home"));
            }
            // Homebrew's `openjdk`, `openjdk@21` and the like (Apple Silicon's prefix, then Intel's).
            for opt in ["/opt/homebrew/opt", "/usr/local/opt"] {
                for formula in children(Path::new(opt)) {
                    if formula
                        .file_name()
                        .is_some_and(|n| n.to_string_lossy().starts_with("openjdk"))
                    {
                        homes.push(join(&formula, "libexec/openjdk.jdk/Contents/Home"));
                    }
                }
            }
        }
        Os::Windows => {
            let vendors = [
                "Eclipse Adoptium",
                "Java",
                "Microsoft",
                "Zulu",
                "BellSoft",
                "Amazon Corretto",
            ];
            if let Some(files) = var("ProgramFiles") {
                for vendor in vendors {
                    homes.extend(children(&join(&files, vendor)));
                }
            }
        }
        Os::Linux => homes.extend(children(Path::new("/usr/lib/jvm"))),
    }
    homes
}

/// Where the `java` program sits in a Java home, as a player reads it.
const BIN_JAVA: &str = if cfg!(windows) {
    r"bin\java.exe"
} else {
    "bin/java"
};

/// Why [`find_java`] found no Java. Its text is what Kiln logs (and INSTALL.md quotes); [`NoJava::advice`] adds what
/// the player can do about it, for chat.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum NoJava {
    /// The Java home chosen by hand holds no `java`: chosen in the `java_home` setting (`setting`), or else in
    /// KILN_JAVA_HOME.
    At { home: PathBuf, setting: bool },
    /// No installed Java is `min` or newer.
    Older { min: u32 },
}

impl std::fmt::Display for NoJava {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::At { home, .. } => write!(f, "no Java at {}", home.display()),
            Self::Older { min } => write!(f, "no Java {min} or newer found"),
        }
    }
}

impl From<NoJava> for String {
    fn from(e: NoJava) -> Self {
        e.to_string()
    }
}

impl NoJava {
    /// The chat's line: what is wrong, then what to install or what to write where. `setting` is the player's own
    /// `java_home` file, in Kiln's data folder.
    pub fn advice(&self, setting: &Path) -> String {
        let file = setting.display();
        match self {
            Self::Older { min } => {
                format!(
                    "{self}. Install Temurin {min} (adoptium.net), or write the path to your Java in {file}"
                )
            }
            Self::At { setting: true, .. } => {
                format!("{self}. {file} should hold the path to the folder that holds {BIN_JAVA}")
            }
            Self::At { setting: false, .. } => {
                format!(
                    "{self}. KILN_JAVA_HOME should hold the path to the folder that holds {BIN_JAVA}"
                )
            }
        }
    }
}

/// The Java home Kiln runs: the `java_home` setting or KILN_JAVA_HOME when set, else the newest installed
/// Java, at least `min` (the mods' own need, never under [`MIN_JAVA`]).
pub fn find_java(
    setting: Option<&str>,
    env: &dyn Fn(&str) -> Option<String>,
    user_home: Option<&Path>,
    min: u32,
) -> Result<PathBuf, NoJava> {
    let min = min.max(MIN_JAVA);
    // An empty setting or variable is no choice.
    let chosen = match setting {
        Some(home) => Some((home.to_owned(), true)),
        None => env("KILN_JAVA_HOME").map(|home| (home, false)),
    }
    .filter(|(h, _)| !h.trim().is_empty());
    if let Some((home, setting)) = chosen {
        let home = PathBuf::from(home.trim());
        return if java_program(&home).is_file() {
            Ok(home)
        } else {
            Err(NoJava::At { home, setting })
        };
    }
    installed_javas(env, user_home)
        .into_iter()
        .filter(|home| java_program(home).is_file())
        .filter_map(|home| {
            let release = std::fs::read_to_string(home.join("release")).ok()?;
            Some((release_major(&release)?, home))
        })
        .filter(|(major, _)| *major >= min)
        .max_by_key(|(major, _)| *major)
        .map(|(_, home)| home)
        .ok_or(NoJava::Older { min })
}

/// The status box's first line.
pub fn status_line(running: Option<usize>, trouble: Option<&str>) -> String {
    match (running, trouble) {
        (_, Some(trouble)) => format!("{NAME} {VERSION}: {trouble}"),
        (Some(1), None) => format!("{NAME} {VERSION}: 1 Java mod"),
        (Some(n), None) if n > 1 => format!("{NAME} {VERSION}: {n} Java mods"),
        _ => format!("{NAME} {VERSION}: no Java mods"),
    }
}

/// The `tick` command's fields for client tick `n` (Host.java reads them): the window's state, then its player as a
/// server call carries one, what they wear and hold, and whether the HUD is hidden and the debug screen open.
pub fn tick_fields(n: i64, s: &Snapshot, gui: (u32, u32), fps: u32) -> Vec<String> {
    let flag = |b: bool| if b { "1" } else { "0" }.to_owned();
    let mut fields = vec![
        n.to_string(),
        flag(s.connected),
        flag(s.in_world),
        flag(s.paused),
        s.game_time.to_string(),
        s.dimension.clone(),
        gui.0.to_string(),
        gui.1.to_string(),
        fps.to_string(),
    ];
    if let Some(player) = &s.player {
        fields.extend(player_fields(player));
        fields.push(equipment_field(&s.equipment));
        fields.push(flag(s.hud_hidden));
        fields.push(flag(s.debug_shown));
        fields.push(s.state.field());
        fields.push(s.state.inventory_field());
    }
    fields
}

/// What the player wears and holds, for the tick: `<slot> <item> <count> <damage> <max damage>`, joined by `;`.
pub fn equipment_field(equipment: &[Worn]) -> String {
    equipment
        .iter()
        .map(|w| {
            format!(
                "{} {} {} {} {}",
                w.slot, w.item, w.count, w.damage, w.max_damage
            )
        })
        .collect::<Vec<_>>()
        .join(";")
}

/// The frames drawn in the last whole second, as vanilla counts them for `Minecraft.getFps`.
#[derive(Clone, Copy, Debug, Default)]
pub struct FrameRate {
    since: Option<Instant>,
    frames: u32,
    fps: u32,
}

impl FrameRate {
    /// A frame drawn at `now`; once a second has passed, its frames become the rate.
    pub fn frame(&mut self, now: Instant) {
        let since = *self.since.get_or_insert(now);
        self.frames += 1;
        if now.duration_since(since) >= Duration::from_secs(1) {
            self.fps = self.frames;
            self.frames = 0;
            self.since = Some(now);
        }
    }

    pub fn fps(&self) -> u32 {
        self.fps
    }
}

/// The `font` command's field: each character's width, by code.
pub fn font_field(widths: &[u32]) -> String {
    widths
        .iter()
        .map(u32::to_string)
        .collect::<Vec<_>>()
        .join(",")
}

/// Each game mob's size as a baby, for Java's `babies` line (D-180): horizon.rs's `babies_field`, given before Kiln
/// starts (tick.rs); none in a test that gives none, where a baby has its adult's box.
static BABIES: std::sync::OnceLock<String> = std::sync::OnceLock::new();

/// Gives the `babies` line's field once (`field` runs the first time only).
pub fn set_babies(field: impl FnOnce() -> String) {
    BABIES.get_or_init(field);
}

/// Kiln in this thread's game (the window's): not started yet, running Java mods, or stopped (with why).
enum State {
    Idle,
    Running(Box<Running>),
    Stopped(String),
}

struct Running {
    java: JavaHost,
    started: Instant,
    /// The mods, by entrypoint class: how many entrypoints each still waits for, and whether one failed.
    mods: Vec<(JavaMod, usize, bool)>,
    /// Entrypoints Java hasn't answered for yet; at 0 the player hears how many mods run.
    awaiting: usize,
    version: String,
    ticks: i64,
    last_tick: Instant,
    /// Kiln's folder (`<game folder>/kiln`): where the mods' keys are kept.
    dir: PathBuf,
    /// Keys pressed since the last tick, for Java.
    keys: Vec<String>,
    /// The key Java has for each mod key the player can bind, by the key's name: what Kiln last told it (`bind`).
    bound: BTreeMap<String, String>,
    /// The player's commands for the mods' client commands since the last tick, without their slash.
    commands: Vec<String>,
    /// Advancements Java has heard of in this world.
    advancements: BTreeSet<String>,
    /// The chunks of the player's level Java has heard of (`chunks`), and that level: in another, Java's chunks
    /// went with the one it left.
    chunks: BTreeSet<(i32, i32)>,
    chunk_dimension: String,
    draw: Vec<HudOp>,
    /// The Java mods Java was last told are turned off now (`soft_off`, D-113): Options > Mods' switches, which
    /// stop a running mod at once and take it out from the next world. At first those off as Java started (their
    /// jars left out); None where Kiln's own tests script Java and the switches aren't theirs.
    soft_off: Option<BTreeSet<String>>,
    /// The overlay's "Restart Java now" was clicked (D-134): Java starts again after this tick's replies. The memory
    /// the Java in the game's process said it holds, in bytes.
    restart: Option<u64>,
}

impl Running {
    fn running(&self) -> usize {
        self.mods
            .iter()
            .filter(|(_, left, failed)| *left == 0 && !failed)
            .count()
    }

    /// A client tick for Java: the keys and new advancements first, then the tick with the snapshot.
    fn tick(&mut self, s: Snapshot, gui: (u32, u32), fps: u32) -> Result<(), String> {
        self.ticks += 1;
        self.last_tick = Instant::now();
        // Out of the world, Java forgets the advancements (a new connection); a new world sends them all.
        if !s.connected {
            self.advancements.clear();
        }
        // The keys the player chose for the mods' keys before the presses, which go by them.
        for (name, key) in rebinds(&mut self.bound, &s.keys) {
            self.java.send("bind", &[&name, &key])?;
        }
        // A switch in Options > Mods turns a running mod off at once (Java stops calling it) or on again.
        if let Some(off) = self.soft_off.as_mut().and_then(soft_switches) {
            self.java.send("soft_off", &[&off])?;
        }
        for (command, fields) in SCREEN.with(|m| m.borrow_mut().take_commands()) {
            let fields: Vec<&str> = fields.iter().map(String::as_str).collect();
            self.java.send(&command, &fields)?;
        }
        for key in std::mem::take(&mut self.keys) {
            self.java.send("key", &[&key])?;
        }
        for command in std::mem::take(&mut self.commands) {
            self.java.send("command", &[&command])?;
        }
        for id in s.advancements.iter().flatten() {
            if self.advancements.insert(id.clone()) {
                self.java.send("adv", &[id])?;
            }
        }
        // The chunks that came and went since the last tick, before the tick (a level left takes its chunks along).
        let now: BTreeSet<(i32, i32)> = if s.in_world {
            s.state.chunks.iter().map(|c| (c[0], c[1])).collect()
        } else {
            BTreeSet::new()
        };
        if s.in_world && s.dimension != self.chunk_dimension {
            self.chunks.clear();
            self.chunk_dimension.clone_from(&s.dimension);
        }
        let (loaded, unloaded) = chunk_changes(&self.chunks, &now);
        if !loaded.is_empty() || !unloaded.is_empty() {
            self.java.send("chunks", &[&loaded, &unloaded])?;
            self.chunks = now;
        }
        for (x, z, sections) in &s.state.chunk_blocks {
            self.java
                .send("chunk_blocks", &[&x.to_string(), &z.to_string(), sections])?;
        }
        for (i, (chat, text)) in s.state.messages.iter().enumerate() {
            let kind = if *chat { "chat" } else { "game" };
            match s.state.message_components.get(i).filter(|j| !j.is_empty()) {
                Some(json) => self.java.send("message", &[kind, text, json])?,
                None => self.java.send("message", &[kind, text])?,
            }
        }
        let fields = tick_fields(self.ticks, &s, gui, fps);
        let fields: Vec<&str> = fields.iter().map(String::as_str).collect();
        self.java.send("tick", &fields)
    }

    /// Java's replies since the last call; returns lines for the player's chat, and why Java stopped.
    fn take_replies(&mut self) -> (Vec<String>, Option<String>) {
        let mut chat = Vec::new();
        for reply in self.java.replies() {
            match reply {
                Reply::Ready(version) => {
                    log::info!(
                        "Kiln: Java {version} ready in {} ms",
                        self.started.elapsed().as_millis()
                    );
                    self.version = version;
                    // Only mods with mixins and no entrypoint: nothing to answer for, they run as Java starts.
                    if self.awaiting == 0 {
                        chat.push(format!(
                            "Kiln: {} of {} Java mods running on Java {}",
                            self.running(),
                            self.mods.len(),
                            self.version
                        ));
                    }
                }
                Reply::Loaded(class) | Reply::Failed(class, _) if self.awaiting == 0 => {
                    log::warn!("Kiln: unexpected answer for {class}");
                }
                Reply::Loaded(class) => self.answered(&class, None, &mut chat),
                Reply::Failed(class, why) => self.answered(&class, Some(why), &mut chat),
                Reply::Draw(ops) => self.draw = ops,
                Reply::Screen(open) => SCREEN.with(|m| m.borrow_mut().java_says(open.is_some())),
                Reply::ScreenDraw(ops) => SCREEN.with(|m| m.borrow_mut().ops = ops),
                Reply::OverDraw(ops, keys, claims) => {
                    SCREEN.with(|m| m.borrow_mut().over(ops, keys, claims))
                }
                Reply::Settings(ids) => {
                    SCREEN.with(|m| m.borrow_mut().settings = ids.into_iter().collect())
                }
                Reply::Items(ids) => set_server_items(ids),
                Reply::Listens(events) => set_server_events(events),
                Reply::Blocks(blocks) => set_server_blocks(blocks),
                Reply::Bridge(hooks) => set_bridged(hooks),
                Reply::Back(values) => back(values),
                // The server's own channel has them (kiln_jvm).
                Reply::Ask(..) => {}
                // Only Kiln's loot run asks (kiln_loot).
                Reply::Loot(..) => {}
                // The server's own channel has them (kiln_jvm).
                Reply::Return(_) => {}
                Reply::Log(text) => log::info!("Kiln: [java] {text}"),
                Reply::Chat(text) => chat.push(text),
                Reply::Trip(id) => TRIPPED.lock().unwrap_or_else(|e| e.into_inner()).push(id),
                Reply::Switch(id, on) => OVERLAY.with(|o| o.borrow_mut().switched.push((id, on))),
                // Only the mods' folder: a folder Java names is never opened.
                Reply::Open(what) if what == "mods" => {
                    OVERLAY.with(|o| o.borrow_mut().open = Some(self.dir.join("mods")))
                }
                // The Crashes tab's "Open the log" (D-191): Kiln's folder, which holds java.log and the crashes' logs.
                Reply::Open(what) if what == "log" => {
                    OVERLAY.with(|o| o.borrow_mut().open = Some(self.dir.clone()))
                }
                Reply::Open(what) => {
                    log::warn!("Kiln: Java asked to open {what:?}, which isn't a folder of Kiln's")
                }
                Reply::ChatComponent(component) => {
                    CHAT_COMPONENTS.with(|c| c.borrow_mut().push(component))
                }
                Reply::Commands(names) => CLIENT_COMMANDS.with(|c| *c.borrow_mut() = names),
                Reply::Keys(owner, keys) => self.keys_heard(&owner, keys),
                Reply::KeyChosen(name, key) => key_chosen(name, key),
                Reply::Send(attended, command) => {
                    SENT.with(|c| c.borrow_mut().push((attended, command)))
                }
                Reply::SendChat(message) => SENT_CHAT.with(|c| c.borrow_mut().push(message)),
                Reply::Clipboard(text) => COPIED.with(|c| *c.borrow_mut() = Some(text)),
                Reply::ThrowSlot(slot, button) => {
                    THROWN.with(|c| c.borrow_mut().push((slot, button)))
                }
                Reply::WantChunk(x, z) => {
                    let mut wanted = WANTED_CHUNKS.lock().unwrap_or_else(|e| e.into_inner());
                    // A scan asks for a few hundred at most; more would only wait.
                    if wanted.len() < 4096 {
                        wanted.insert((x, z));
                    }
                }
                Reply::Play(play) => PLAYED.with(|p| p.borrow_mut().push(play)),
                Reply::Net(data) => NET_OUT.with(|n| n.borrow_mut().push(data)),
                Reply::Done(_) => {}
                Reply::Restart(bytes) => self.restart = Some(bytes),
                Reply::RestartGame => OVERLAY.with(|o| o.borrow_mut().restart_game = true),
                Reply::Sprite(name, width, height, rgba) => {
                    crate::game::kiln_sprites::add(&name, width, height, &rgba)
                }
                Reply::Exited(why) => {
                    SCREEN.with(|m| m.borrow_mut().java_says(false));
                    log::warn!("Kiln: Java stopped: {why}");
                    chat.push("Kiln: Java stopped (kiln/java.log says why)".to_owned());
                    return (chat, Some(why));
                }
            }
        }
        (chat, None)
    }

    /// Java told of mods' keys ([`keys_by_row`]): kept, for their mods' pages. Each is bound again with the next tick:
    /// a key Kiln knew from before may have been bound before its mod registered it.
    fn keys_heard(&mut self, owner: &str, keys: Vec<[String; 4]>) {
        for key in &keys {
            self.bound.remove(&key[0]);
        }
        let mods: Vec<&JavaMod> = self.mods.iter().map(|(m, ..)| m).collect();
        for (row, keys) in keys_by_row(&mods, owner, keys) {
            learn_keys(&self.dir, &row, keys);
        }
    }

    fn answered(&mut self, class: &str, failed: Option<String>, chat: &mut Vec<String>) {
        self.awaiting -= 1;
        if let Some(entry) = self
            .mods
            .iter_mut()
            .find(|(m, left, _)| *left > 0 && m.main.iter().chain(&m.client).any(|c| c == class))
        {
            entry.1 -= 1;
            match failed {
                Some(why) => {
                    entry.2 = true;
                    chat.push(format!("Kiln: {} didn't start: {why}", entry.0.name));
                }
                None if entry.1 == 0 && !entry.2 => {
                    log::info!(
                        "Kiln: started {} {} ({})",
                        entry.0.name,
                        entry.0.version,
                        entry.0.id
                    )
                }
                None => {}
            }
        }
        if self.awaiting == 0 {
            chat.push(format!(
                "Kiln: {} of {} Java mods running on Java {}",
                self.running(),
                self.mods.len(),
                self.version
            ));
        }
    }
}

thread_local! {
    static STATE: RefCell<State> = const { RefCell::new(State::Idle) };
    /// The Java mods that were off when Kiln last started Java (or found nothing to run), to see a switch change.
    static STARTED_OFF: RefCell<Option<BTreeSet<String>>> = const { RefCell::new(None) };
    static HELD: RefCell<BTreeSet<String>> = const { RefCell::new(BTreeSet::new()) };
    /// The client commands the mods registered, by name, as Java last said.
    static CLIENT_COMMANDS: RefCell<Vec<String>> = const { RefCell::new(Vec::new()) };
    /// A mod's own screen and the mods' settings pages (D-094).
    static SCREEN: RefCell<ModScreen> = RefCell::new(ModScreen::default());
    /// Commands the mods sent as the player, for the game to send: (attended, the command without its slash).
    static SENT: RefCell<Vec<(bool, String)>> = const { RefCell::new(Vec::new()) };
    /// Chat messages the mods sent as the player (`ClientPacketListener.sendChat`), for the game to send.
    static SENT_CHAT: RefCell<Vec<String>> = const { RefCell::new(Vec::new()) };
    /// What a mod last copied, for the game to put on the system's clipboard (D-174).
    static COPIED: RefCell<Option<String>> = const { RefCell::new(None) };
    /// The slots of the open menu the mods threw the stack of, for the game to send.
    static THROWN: RefCell<Vec<(usize, i32)>> = const { RefCell::new(Vec::new()) };
    /// What the mods played for the window's player since the game last took it: sounds, particles, toasts.
    static PLAYED: RefCell<Vec<Play>> = const { RefCell::new(Vec::new()) };
    /// The mods' lines for the window's chat since the game last took them, as vanilla's JSON with their style.
    static CHAT_COMPONENTS: RefCell<Vec<serde_json::Value>> = const { RefCell::new(Vec::new()) };
    /// What the mods changed of the game's state Horizon owns, for the window's next tick to set (D-105).
    static BACK: RefCell<Vec<(String, String)>> = const { RefCell::new(Vec::new()) };
    /// How many of Java's `back` messages reached the window's thread, and how many of them the window's tick has
    /// taken (`take_back`): Java's patience with a change Horizon hasn't taken counts its ticks only while every
    /// message it sent was taken (Java answers late while it warms up and frames make its ticks while the game's
    /// tick stands, D-192).
    static BACKS_RECEIVED: Cell<u64> = const { Cell::new(0) };
    static BACKS_TAKEN: Cell<u64> = const { Cell::new(0) };
    /// The field of view the mods' mixins make the view take (`Camera.calculateFov`), in degrees: Horizon's view is
    /// scaled by it over its option (D-105).
    static VIEW_FOV: Cell<Option<f32>> = const { Cell::new(None) };
    /// The brightness a mod's mixin makes the gamma option give (`OptionInstance.get`), for Horizon's renderer over
    /// the option, which stays the player's (D-192); and whether one was there and went, for the renderer to take
    /// the option's again.
    static VIEW_GAMMA: Cell<Option<f32>> = const { Cell::new(None) };
    static VIEW_GAMMA_ENDED: Cell<bool> = const { Cell::new(false) };
    /// Vanilla's HUD layers Horizon hides (`native_gui_layer_pre`'s ids): those the mods' mixins or HUD elements kept
    /// from drawing in Java's last HUD (D-145).
    static HUD_HIDDEN: RefCell<Vec<String>> = const { RefCell::new(Vec::new()) };
    /// The tooltips Java made (`ItemStack.getTooltipLines`, D-145), by the stack's SNBT: its name when the mods' code
    /// changed it, the lines past it (vanilla's JSON), and when the window last asked for it.
    static TOOLTIPS: RefCell<std::collections::HashMap<String, Tooltip>> =
        RefCell::new(std::collections::HashMap::new());
    /// The mods' payloads for the server since the game last took them, each a message for `NET_CHANNEL`.
    static NET_OUT: RefCell<Vec<Vec<u8>>> = const { RefCell::new(Vec::new()) };
    /// The window's thread: messages from the server that came before Java started (`from_server`).
    static NET_EARLY: RefCell<Vec<Vec<u8>>> = const { RefCell::new(Vec::new()) };
    static GUI: std::cell::Cell<(u32, u32)> = const { std::cell::Cell::new(DEFAULT_GUI) };
    static FRAMES: std::cell::Cell<FrameRate> = std::cell::Cell::new(FrameRate::default());
}

/// The player's made advancements as the single-player server's own game last published them (its thread
/// writes, the window's reads: horizon.rs says why), and how many publications there have been in all.
static SERVER_ADVANCEMENTS: Mutex<(u64, Option<Vec<String>>)> = Mutex::new((0, None));

/// The server's thread: the player's made advancements changed; `made` is all of them.
pub fn publish_server_advancements(made: Vec<String>) {
    if let Ok(mut published) = SERVER_ADVANCEMENTS.lock() {
        published.0 += 1;
        published.1 = Some(made);
    }
}

/// The window's thread: the server's list when it published one since publication `seen` (which moves on);
/// `seen` stays 0 while the server has published nothing in this world.
pub fn server_advancements(seen: &mut u64) -> Option<Vec<String>> {
    let published = SERVER_ADVANCEMENTS.lock().ok()?;
    let made = published.1.as_ref()?;
    if published.0 == *seen {
        return None;
    }
    *seen = published.0;
    Some(made.clone())
}

/// Out of the world: the server's list was that world's.
pub fn forget_server_advancements() {
    if let Ok(mut published) = SERVER_ADVANCEMENTS.lock() {
        published.1 = None;
    }
}

/// A hand, as `InteractionHand`.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Hand {
    Main,
    Off,
}

impl Hand {
    fn field(self) -> &'static str {
        match self {
            Hand::Main => "main",
            Hand::Off => "off",
        }
    }
}

/// A player as a server call carries them to Java (kiln/java/kiln/Server.java reads the fields).
#[derive(Clone, Debug, Default, PartialEq)]
pub struct PlayerSnapshot {
    pub uuid: u128,
    pub name: String,
    pub dimension: String,
    pub position: [f64; 3],
    /// Survival 0, creative 1, adventure 2, spectator 3.
    pub game_mode: u8,
    pub health: f32,
    /// Each hand's item and count; None when it holds nothing.
    pub main: Option<(String, u32)>,
    pub off: Option<(String, u32)>,
    /// Attributes, by id: the base value and the value with the player's modifiers.
    pub attributes: Vec<(String, f64, f64)>,
    /// Holding Shift (`isShiftKeyDown`).
    pub sneaking: bool,
    /// Experience: the level, the progress towards the next (0 to 1) and the total points.
    pub experience: (i32, f32, i32),
    /// Each hand's stack whole, as vanilla saves an item (SNBT: id, count, components); empty for nothing.
    pub main_stack: String,
    pub off_stack: String,
}

/// A player on the integrated server, as Kiln's Java asks for them (`ask <call> players`, kiln/java/kiln/Players.java):
/// for Fabric's `PlayerLookup` and the server's player list.
#[derive(Clone, Debug, Default, PartialEq)]
pub struct Online {
    pub uuid: u128,
    pub name: String,
    pub dimension: String,
    pub position: [f64; 3],
    /// Survival 0, creative 1, adventure 2, spectator 3.
    pub game_mode: u8,
    pub health: f32,
    /// The radius of chunks the player is sent (what they track).
    pub view_distance: i32,
}

impl Online {
    /// `player` as the server has them now, sent chunks `view_distance` around.
    pub fn of(player: &PlayerSnapshot, view_distance: i32) -> Self {
        Online {
            uuid: player.uuid,
            name: player.name.clone(),
            dimension: player.dimension.clone(),
            position: player.position,
            game_mode: player.game_mode,
            health: player.health,
            view_distance,
        }
    }
}

/// The answer to `players`: the server's players as they were at its tick's start (`listed`), with `now` (the call's
/// own player) as they are now, added if `listed` hadn't them, sent the server's `view_distance` then. Each as
/// `<uuid> <name> <dimension> <x> <y> <z> <game mode> <health> <view distance>`, joined by `;`.
pub fn players_answer(
    listed: &[Online],
    now: Option<&PlayerSnapshot>,
    view_distance: i32,
) -> String {
    let mut players: Vec<Online> = listed.to_vec();
    if let Some(now) = now {
        match players.iter_mut().find(|p| p.uuid == now.uuid) {
            Some(p) => *p = Online::of(now, p.view_distance),
            None => players.push(Online::of(now, view_distance)),
        }
    }
    players
        .iter()
        .map(|p| {
            format!(
                "{} {} {} {} {} {} {} {} {}",
                uuid_text(p.uuid),
                p.name,
                p.dimension,
                p.position[0],
                p.position[1],
                p.position[2],
                p.game_mode,
                p.health,
                p.view_distance
            )
        })
        .collect::<Vec<_>>()
        .join(";")
}

/// What using an item did, as `InteractionResult`.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum UseResult {
    Pass,
    Fail,
    Success,
    SuccessServer,
    Consume,
}

impl UseResult {
    /// Anything but PASS takes the use: the hand swings, Horizon's own use of the item doesn't run.
    pub fn takes_use(self) -> bool {
        self != UseResult::Pass
    }
}

/// Something a Java mod's code did on the server, for the game to do, in order.
#[derive(Clone, Debug, PartialEq)]
pub enum Effect {
    /// The player's attribute has a new base value.
    Attribute {
        id: String,
        base: f64,
    },
    Health(f32),
    /// The hand holds `count` of `item` now (0: nothing).
    Hand {
        hand: Hand,
        item: String,
        count: u32,
    },
    /// The hand holds this stack now, whole (SNBT, as vanilla saves an item): its components changed (its damage).
    Held {
        hand: Hand,
        stack: String,
    },
    /// What a payload's code moved among the player's 36 inventory slots and the open crafting grid (D-166): SNBT
    /// with `Items` (the slots as they are now: 0 to 35, then the grid's cells from 36) and `Before` (as the game
    /// had them); a slot changes only where it still holds what Java read.
    PlayerSlots {
        data: String,
    },
    /// What a mod's code moved in or out of the player's ender chest (`getEnderChestInventory`, D-209): SNBT with its
    /// 27 slots as Java read them (`Before`) and as it left them (`Items`); a slot changes only where it still holds
    /// what Java read.
    EnderChest {
        data: String,
    },
    /// A menu a mod's command opened, to be shown as the game's own (D-216): `open_menu <kind> <title>`, the kind one
    /// of the game's menus Kiln opens without their block (`ender_chest`, `crafting`), the title the menu provider's
    /// name as a text component.
    OpenMenu {
        kind: String,
        title: serde_json::Value,
    },
    /// The player moved by a mod's code (`ServerPlayer.teleportTo`, D-231): to `pos` in `dimension`, facing `yaw` and
    /// `pitch` in degrees (`teleport <dimension> <x> <y> <z> <yaw> <pitch>`).
    Teleport {
        dimension: String,
        pos: [f64; 3],
        yaw: f32,
        pitch: f32,
    },
    /// The player's motion in blocks a tick (`motion x y z`): a mod sent the player its motion
    /// (`ClientboundSetEntityMotionPacket`, launch-command, #220, D-187).
    Motion([f64; 3]),
    /// The player's experience: its level, the progress towards the next and the total points.
    Experience {
        level: i32,
        progress: f32,
        total: i32,
    },
    /// A world clock's new total ticks (`ServerClockManager.setTotalTicks`, D-210): `minecraft:overworld`'s are the
    /// time of day.
    Clock {
        clock: String,
        ticks: i64,
    },
    /// Heard near `at`; `source` is the volume slider's index (`SoundSource`'s ordinal, Horizon's order too).
    Sound {
        id: String,
        source: usize,
        at: [f64; 3],
        volume: f32,
        pitch: f32,
    },
    /// A text component (vanilla's JSON) over the player's hotbar, or in their chat.
    Overlay(serde_json::Value),
    Tell(serde_json::Value),
    /// `Level.setBlock`: the block at `pos` is `state` now (`mymod:lamp[lit=true]`; properties left out keep theirs).
    Block {
        pos: [i32; 3],
        flags: i32,
        state: String,
    },
    /// `Block.popResource`, `Player.drop`: `stack` dropped at `pos`, whole, as vanilla saves an item (SNBT: its id,
    /// count and components, D-207).
    Drop {
        pos: [i32; 3],
        stack: String,
    },
    /// `Level.destroyBlock`: the block at `pos` broken as the game breaks one, dropping what it drops when `drop`.
    Destroy {
        pos: [i32; 3],
        drop: bool,
    },
    /// What a mod's code moved into (positive) or out of (negative) the game's own fluid holder at `pos` through its
    /// side `face` (a cauldron, a native mod's tanks: Horizon's API 54, D-198), in millibuckets of `fluid`
    /// (`minecraft:water`, `minecraft:lava`).
    Fluid {
        pos: [i32; 3],
        face: String,
        fluid: String,
        amount: i32,
    },
    /// `Block.dropResources`: what `state` drops by its loot table, dropped at `pos`.
    Drops {
        pos: [i32; 3],
        state: String,
    },
    /// `scheduleTick`: a tick of `block` at `pos` in `delay` game ticks.
    Schedule {
        pos: [i32; 3],
        block: String,
        delay: i32,
    },
    /// `getStateForPlacement`'s answer: the state a placed block takes, or None for no placement.
    Placement(Option<String>),
    /// What the mod's block entity at `pos` saves now: its compound, as the network's binary NBT (D-140).
    BlockEntity {
        pos: [i32; 3],
        data: Vec<u8>,
    },
    /// What a mod's code moved in or out of the game's own container at `pos` (a chest, a hopper: D-144): a compound
    /// in SNBT with the slots as Java read them (`Before`) and as it left them (`Items`), as vanilla saves slots.
    Container {
        pos: [i32; 3],
        data: String,
    },
    /// What the screen of the mod's block entity at `pos` shows (`MachineData`): its menu's data slots.
    Data {
        pos: [i32; 3],
        values: Vec<i32>,
    },
    /// The player opened a menu the mod's code built: where its slots of the block's container are on the screen.
    Menu(MenuLayout),
    /// A mod's payload for `player` (`ServerPlayNetworking.send`): the message for Horizon's channel for mods.
    Net {
        player: u128,
        data: Vec<u8>,
    },
    /// What a hook of Kiln's Mixin bridge gives back (D-102): `canceled 1`, `amount 3.5`, by the hook's parameter.
    Out {
        name: String,
        value: String,
    },
    /// One of the mods' server commands as a usage for Horizon's tree (`pay <player:player> <amount:integer>`), and the
    /// operator level a player needs for it (D-107).
    Usage {
        level: u8,
        usage: String,
    },
    /// A line of a server command's answer (vanilla's JSON), in the player's chat after the command.
    Feedback(serde_json::Value),
}

/// A mod's menu as its code lays it out (`Slot.x`, `Slot.y`): each slot of the block's container it shows
/// (container slot, x, y), and the top left of the player's three inventory rows (the hotbar 58 below).
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct MenuLayout {
    pub slots: Vec<(usize, i32, i32)>,
    pub inventory: [i32; 2],
}

/// The game's grid menus a mod's container opens in, by how many slots they have: the smallest that holds the
/// container, its other slots hidden (the screen shows the mod's layout).
pub const GRID_MENUS: [(&str, usize); 6] = [
    ("minecraft:generic_3x3", 9),
    ("minecraft:generic_9x2", 18),
    ("minecraft:generic_9x3", 27),
    ("minecraft:generic_9x4", 36),
    ("minecraft:generic_9x5", 45),
    ("minecraft:generic_9x6", 54),
];

/// The grid menu for a container of `slots`, and its size; None past 54.
pub fn grid_menu(slots: usize) -> Option<(&'static str, usize)> {
    GRID_MENUS.into_iter().find(|&(_, size)| size >= slots)
}

impl MenuLayout {
    /// The screen's size: vanilla's width, and the player's inventory and hotbar below the machine.
    pub fn size(&self) -> [f32; 2] {
        [176.0, (self.inventory[1] + 82) as f32]
    }

    /// Where each of a menu's `n` container slots is on the screen; None for those the mod's menu doesn't show.
    pub fn slot_places(&self, n: usize) -> Vec<Option<[f32; 2]>> {
        let mut places = vec![None; n];
        for &(slot, x, y) in &self.slots {
            if let Some(place) = places.get_mut(slot) {
                *place = Some([x as f32, y as f32]);
            }
        }
        places
    }

    /// Where the inventory's label goes (its top, as vanilla's: 11 above the inventory), unless a slot of the
    /// machine is there (Tech Reborn's battery slot, its inventory being 10 lower than vanilla's).
    pub fn inventory_label(&self) -> Option<f32> {
        let top = self.inventory[1] - 11;
        let clear = self
            .slots
            .iter()
            .all(|&(_, x, y)| y + 17 <= top || y - 1 >= top + 9 || x - 1 >= 8 + 60);
        clear.then_some(top as f32)
    }

    /// Where the screen draws the energy bar, as Tech Reborn's screens do (`[x, y, w, h]`, left of the machine's
    /// slots): None when a slot is there.
    pub fn energy_bar(&self) -> Option<[f32; 4]> {
        let bar = [9, 19, 14, 50];
        let clear = self.slots.iter().all(|&(_, x, y)| {
            x + 16 <= bar[0] || x >= bar[0] + bar[2] || y + 16 <= bar[1] || y >= bar[1] + bar[3]
        });
        clear.then(|| bar.map(|v| v as f32))
    }

    /// Where the progress arrow goes (`[x, y, w, h]`): into the rightmost column of slots (a machine's outputs), from
    /// the nearest slot left of one of them on about its row (half a slot up or down), with room between them; None
    /// without such a pair.
    pub fn progress_arrow(&self) -> Option<[f32; 4]> {
        let right = self.slots.iter().map(|&(_, x, _)| x).max()?;
        self.slots
            .iter()
            .filter(|&&(_, x, _)| x == right)
            .flat_map(|&(_, to, to_y)| {
                self.slots
                    .iter()
                    .filter(move |&&(_, x, y)| to >= x + 16 + 24 && (to_y - y).abs() <= 9)
                    .map(move |&(_, x, y)| (to - x, y, x, to))
            })
            .min()
            .map(|(_, y, x, to)| [(x + 21) as f32, (y + 3) as f32, (to - x - 24) as f32, 10.0])
    }

    /// Where a machine's tank gauge goes (`[x, y, w, h]`, as tall as the energy bar): the first place right of the
    /// energy bar clear of the slots and the arrow, two pixels around (Tech Reborn draws its tanks beside the slots
    /// that fill and empty them); None without room.
    pub fn tank_gauge(&self) -> Option<[f32; 4]> {
        let arrow = self.progress_arrow();
        let apart = |[ax, ay, aw, ah]: [i32; 4], [bx, by, bw, bh]: [i32; 4]| {
            ax + aw + 2 <= bx || bx + bw + 2 <= ax || ay + ah + 2 <= by || by + bh + 2 <= ay
        };
        (26..=150).find_map(|x| {
            let gauge = [x, 19, 20, 50];
            let clear = self
                .slots
                .iter()
                .all(|&(_, sx, sy)| apart(gauge, [sx - 1, sy - 1, 18, 18]))
                && arrow.is_none_or(|a| apart(gauge, a.map(|v| v as i32)));
            clear.then(|| gauge.map(|v| v as f32))
        })
    }
}

/// What a machine's screen shows, from its data slots (`MachineData`): the energy's share of its capacity (0 to 1),
/// the progress (0 to 1, None without one), and the energy and capacity.
pub fn machine_data(data: &[i32]) -> (f32, Option<f32>, u64, u64) {
    let at = |i: usize| data.get(i).copied().unwrap_or(0);
    let progress = (at(1) >= 0 && data.len() > 1).then(|| at(1).min(100) as f32 / 100.0);
    (
        at(0).clamp(0, 1000) as f32 / 1000.0,
        progress,
        halves(data, 2),
        halves(data, 4),
    )
}

/// A machine's tank, from its data slots after the energy (`MachineData`): how full it is (0 to 1), its fluid's colour
/// (`0xRRGGBB`, black when empty), and the amount and capacity in millibuckets; None for a machine without one.
pub fn machine_tank(data: &[i32]) -> Option<(f32, u32, u64, u64)> {
    // Six zeros (no capacity) stand for no tank when a side configuration follows (D-214).
    if data.len() < 12 || halves(data, 10) == 0 {
        return None;
    }
    let colour = data[7] as u32 & 0xFFF;
    // 4 bits a channel, widened as #RGB is: 0xA becomes 0xAA.
    let rgb = [8, 4, 0].into_iter().fold(0, |rgb, shift| {
        (rgb << 8) | ((colour >> shift) & 0xF) * 0x11
    });
    Some((
        data[6].clamp(0, 1000) as f32 / 1000.0,
        rgb,
        halves(data, 8),
        halves(data, 10),
    ))
}

/// A machine's side configuration (RebornCore's `SlotConfiguration`, D-214), from its data slots after the tank's
/// six (`MachineSides`): its facing and, for each slot it configures, the mode of each face and its switches.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct MachineSides {
    /// The machine's front (`Direction`'s 3D value: down 0, up 1, north 2, south 3, west 4, east 5), if it has one.
    pub facing: Option<usize>,
    /// Per slot: two bits a face (by its 3D value), then auto input (bit 12), auto output (13) and the filter (14).
    pub slots: Vec<u16>,
}

/// The side panel's modes, as RebornCore's `ExtractConfig`: none, input, output, both.
pub const SIDE_MODES: [&str; 4] = ["none", "input", "output", "input and output"];

/// The faces by their 3D value, as the game names them.
pub const FACES: [&str; 6] = ["down", "up", "north", "south", "west", "east"];

impl MachineSides {
    /// Slot `slot`'s mode on face `face` (3D value): 0 none, 1 input, 2 output, 3 both.
    pub fn mode(&self, slot: usize, face: usize) -> u8 {
        (self.slots.get(slot).copied().unwrap_or(0) >> (2 * face)) as u8 & 3
    }

    /// Slot `slot`'s switch `which`: 0 auto input, 1 auto output, 2 the filter.
    pub fn switch(&self, slot: usize, which: usize) -> bool {
        self.slots
            .get(slot)
            .is_some_and(|v| v >> (12 + which) & 1 == 1)
    }

    /// The face (3D value) that is `relative` to the machine's front, as RebornCore's screen lays the faces out:
    /// 0 its front, 1 its back, 2 its left, 3 its right (seen from the front, as the player stands), 4 up, 5 down.
    /// A machine with no front takes north as its front.
    pub fn face(&self, relative: usize) -> usize {
        // Horizontal faces clockwise seen from above: north, east, south, west.
        const AROUND: [usize; 4] = [2, 5, 3, 4];
        let front = self.facing.filter(|f| *f >= 2).unwrap_or(2);
        let at = AROUND.iter().position(|&f| f == front).unwrap_or(0);
        match relative {
            0 => front,
            1 => AROUND[(at + 2) % 4],
            // Facing the machine's front, its left is the front turned clockwise (from above).
            2 => AROUND[(at + 1) % 4],
            3 => AROUND[(at + 3) % 4],
            4 => 1,
            _ => 0,
        }
    }
}

/// The menu button a press on the side panel sends (`MachineSides.java`): face `face` (3D value) of slot `slot` set
/// to mode `mode`, or switch `which` of slot `slot` turned `on`.
pub fn side_button_face(slot: usize, face: usize, mode: u8) -> i32 {
    1_000_000 + slot as i32 * 100 + face as i32 * 10 + i32::from(mode)
}

/// See [`side_button_face`].
pub fn side_button_switch(slot: usize, which: usize, on: bool) -> i32 {
    2_000_000 + slot as i32 * 100 + which as i32 * 10 + i32::from(on)
}

/// A machine's side configuration from its data slots, None for a block entity without one.
pub fn machine_sides(data: &[i32]) -> Option<MachineSides> {
    let count = usize::try_from(*data.get(13)?).ok()?;
    let values = data.get(14..14 + count)?;
    Some(MachineSides {
        facing: usize::try_from(data[12]).ok().filter(|f| *f < 6),
        slots: values.iter().map(|&v| v as u16 & 0x7FFF).collect(),
    })
}

/// The item slots the game's hoppers reach in a machine with a side configuration of its own (D-214), from the slots
/// its block announced (`base`): a hopper fills through a face only the slots set to take input there, and one below
/// it empties only the slots set to give output through the bottom (the game's hoppers take from a container above
/// them only). Horizon asks a container's rules by block, so Kiln answers them by the machine in place.
pub fn sided_slots(base: &Slots, sides: &MachineSides) -> Slots {
    let configured = (0..base.count.min(sides.slots.len())).collect::<Vec<_>>();
    let mut faces: [Vec<usize>; 6] = Default::default();
    for (face, reached) in faces.iter_mut().enumerate() {
        let wanted = if face == 0 { 2 } else { 1 };
        *reached = configured
            .iter()
            .copied()
            .filter(|&s| sides.mode(s, face) & wanted != 0)
            .collect();
    }
    let inputs = configured
        .iter()
        .copied()
        .filter(|&s| (1..6).any(|f| sides.mode(s, f) & 1 != 0))
        .collect();
    let outputs = faces[0].clone();
    Slots {
        count: base.count,
        faces,
        inputs,
        outputs,
    }
}

/// A number the game sent as two 15-bit data slots from `at`, low first.
fn halves(data: &[i32], at: usize) -> u64 {
    let half = |i: usize| data.get(i).copied().unwrap_or(0) as u64 & 0x7FFF;
    half(at) | (half(at + 1) << 15)
}

/// A block about to be placed, as Horizon describes it (`BlockPlaceContext`).
#[derive(Clone, Debug, PartialEq)]
pub struct Placement {
    /// Where it goes, and its block's default state there.
    pub pos: [i32; 3],
    pub state: String,
    /// The face clicked (`up`), where the click landed, and the block clicked.
    pub face: String,
    pub hit: [f64; 3],
    pub clicked: [i32; 3],
    /// The placer's rotation, in degrees, and whether they sneak.
    pub yaw: f32,
    pub pitch: f32,
    pub sneaking: bool,
    /// The item placed (`mymod:lamp`), and whether the clicked block is replaced.
    pub item: String,
    pub replacing: bool,
}

/// A server call's answer.
#[derive(Clone, Debug, PartialEq)]
pub struct Outcome {
    pub result: UseResult,
    pub effects: Vec<Effect>,
}

/// A UUID as Java writes it: `00000000-0000-0000-0000-000000000000`.
pub fn uuid_text(uuid: u128) -> String {
    let h = format!("{uuid:032x}");
    format!(
        "{}-{}-{}-{}-{}",
        &h[..8],
        &h[8..12],
        &h[12..16],
        &h[16..20],
        &h[20..]
    )
}

/// A UUID as `uuid_text` (and Java's `UUID.toString`) writes it; None for anything else.
pub fn parse_uuid(text: &str) -> Option<u128> {
    let parts: Vec<&str> = text.split('-').collect();
    let lengths: Vec<usize> = parts.iter().map(|p| p.len()).collect();
    if lengths != [8, 4, 4, 4, 12]
        || !parts
            .iter()
            .all(|p| p.chars().all(|c| c.is_ascii_hexdigit()))
    {
        return None;
    }
    u128::from_str_radix(&parts.concat(), 16).ok()
}

/// How many fields a player takes in a call (`Server.PLAYER`).
pub const PLAYER_FIELDS: usize = 19;

/// A player's fields in a server call (kiln/java/kiln/Server.java reads them): 19, the attributes joined by `;`,
/// then sneaking, the experience (level, progress, total) and each hand's stack whole.
pub fn player_fields(player: &PlayerSnapshot) -> Vec<String> {
    let stack = |s: &Option<(String, u32)>| match s {
        Some((item, count)) => [item.clone(), count.to_string()],
        None => [String::new(), "0".to_owned()],
    };
    let mut fields = vec![
        uuid_text(player.uuid),
        player.name.clone(),
        player.dimension.clone(),
        player.position[0].to_string(),
        player.position[1].to_string(),
        player.position[2].to_string(),
        player.game_mode.to_string(),
        player.health.to_string(),
    ];
    fields.extend(stack(&player.main));
    fields.extend(stack(&player.off));
    fields.push(
        player
            .attributes
            .iter()
            .map(|(id, base, value)| format!("{id} {base} {value}"))
            .collect::<Vec<_>>()
            .join(";"),
    );
    let (level, progress, total) = player.experience;
    fields.extend([
        if player.sneaking { "1" } else { "0" }.to_owned(),
        level.to_string(),
        progress.to_string(),
        total.to_string(),
        player.main_stack.clone(),
        player.off_stack.clone(),
    ]);
    debug_assert_eq!(fields.len(), PLAYER_FIELDS);
    fields
}

/// A server call's first fields: its number, the world's folder and the game time.
fn call_fields(call: u64, world: &Path, game_time: u64) -> Vec<String> {
    vec![
        call.to_string(),
        world.to_string_lossy().into_owned(),
        game_time.to_string(),
    ]
}

/// The `use` command's fields: call `call`, in the world in `world`, `player` uses `item` with `hand`.
pub fn use_fields(
    call: u64,
    world: &Path,
    game_time: u64,
    item: &str,
    hand: Hand,
    player: &PlayerSnapshot,
) -> Vec<String> {
    let mut fields = call_fields(call, world, game_time);
    fields.extend([item.to_owned(), hand.field().to_owned()]);
    fields.extend(player_fields(player));
    fields
}

/// The `join` command's fields: `player` came into a level.
pub fn join_fields(
    call: u64,
    world: &Path,
    game_time: u64,
    player: &PlayerSnapshot,
) -> Vec<String> {
    let mut fields = call_fields(call, world, game_time);
    fields.extend(player_fields(player));
    fields
}

/// The `respawn` command's fields: `old` (as they last were) respawned as `new`.
pub fn respawn_fields(
    call: u64,
    world: &Path,
    game_time: u64,
    alive: bool,
    old: &PlayerSnapshot,
    new: &PlayerSnapshot,
) -> Vec<String> {
    let mut fields = call_fields(call, world, game_time);
    fields.push(if alive { "1" } else { "0" }.to_owned());
    fields.extend(player_fields(old));
    fields.extend(player_fields(new));
    fields
}

/// The `use_block` command's fields: `player` right clicks the block `state` at `pos` on its `face` (`up`) with
/// `hand`.
#[allow(clippy::too_many_arguments)]
pub fn use_block_fields(
    call: u64,
    world: &Path,
    game_time: u64,
    pos: [i32; 3],
    state: &str,
    face: &str,
    hand: Hand,
    player: &PlayerSnapshot,
) -> Vec<String> {
    let mut fields = call_fields(call, world, game_time);
    fields.extend(pos.map(|c| c.to_string()));
    fields.extend([state.to_owned(), face.to_owned(), hand.field().to_owned()]);
    fields.extend(player_fields(player));
    fields
}

/// The `random_tick` command's fields: the block `state` at `pos` in `dimension` ticks at random.
pub fn random_tick_fields(
    call: u64,
    world: &Path,
    game_time: u64,
    dimension: &str,
    pos: [i32; 3],
    state: &str,
) -> Vec<String> {
    let mut fields = call_fields(call, world, game_time);
    fields.push(dimension.to_owned());
    fields.extend(pos.map(|c| c.to_string()));
    fields.push(state.to_owned());
    fields
}

/// The `step_on` command's fields: `player` stands on the block `state` at `pos`.
pub fn step_on_fields(
    call: u64,
    world: &Path,
    game_time: u64,
    pos: [i32; 3],
    state: &str,
    player: &PlayerSnapshot,
) -> Vec<String> {
    let mut fields = call_fields(call, world, game_time);
    fields.extend(pos.map(|c| c.to_string()));
    fields.push(state.to_owned());
    fields.extend(player_fields(player));
    fields
}

/// A block call's fields: the call's first, then `rest`.
fn block_call_fields(call: u64, world: &Path, game_time: u64, rest: Vec<String>) -> Vec<String> {
    let mut fields = call_fields(call, world, game_time);
    fields.extend(rest);
    fields
}

/// The `placement` command's fields after the call's: `placement` in `dimension`.
pub fn placement_fields(dimension: &str, placement: &Placement) -> Vec<String> {
    let flag = |b: bool| if b { "1" } else { "0" }.to_owned();
    let mut fields = vec![dimension.to_owned()];
    fields.extend(placement.pos.map(|c| c.to_string()));
    fields.extend([placement.state.clone(), placement.face.clone()]);
    fields.extend(placement.hit.map(|c| c.to_string()));
    fields.extend(placement.clicked.map(|c| c.to_string()));
    fields.extend([
        placement.yaw.to_string(),
        placement.pitch.to_string(),
        flag(placement.sneaking),
        placement.item.clone(),
        flag(placement.replacing),
    ]);
    fields
}

/// An effect as Java writes it (kiln/java/kiln/Server.java); None for one Kiln doesn't know.
pub fn parse_effect(effect: &str) -> Option<Effect> {
    let (kind, rest) = effect.split_once(' ')?;
    if kind == "out" {
        let (name, value) = rest.split_once(' ').unwrap_or((rest, ""));
        return Some(Effect::Out {
            name: name.to_owned(),
            value: value.to_owned(),
        });
    }
    if kind == "block_entity" || kind == "container" {
        // A container's compound is SNBT whose strings may hold spaces: it is all the rest.
        let mut parts = rest.splitn(4, ' ');
        let mut coordinate = || parts.next()?.parse::<i32>().ok();
        let pos = [coordinate()?, coordinate()?, coordinate()?];
        let data = parts.next()?;
        return Some(if kind == "block_entity" {
            Effect::BlockEntity {
                pos,
                data: from_nbt_field(data)?,
            }
        } else {
            Effect::Container {
                pos,
                data: data.to_owned(),
            }
        });
    }
    if kind == "slots" {
        // The slots' compound is SNBT whose strings may hold spaces: it is all the rest.
        return Some(Effect::PlayerSlots {
            data: rest.to_owned(),
        });
    }
    if kind == "ender_chest" {
        // As the slots': SNBT, all the rest.
        return Some(Effect::EnderChest {
            data: rest.to_owned(),
        });
    }
    if kind == "open_menu" {
        let (menu, title) = rest.split_once(' ')?;
        return Some(Effect::OpenMenu {
            kind: menu.to_owned(),
            title: serde_json::from_str(title).ok()?,
        });
    }
    if kind == "menu" {
        // `menu <slot>:<x>:<y>,... <x> <y>`, `-` for no slots.
        let [slots, x, y] = rest.split(' ').collect::<Vec<_>>().try_into().ok()?;
        let slots = slots
            .split(',')
            .filter(|s| !s.is_empty() && *s != "-")
            .map(|s| {
                let [slot, x, y] = s.split(':').collect::<Vec<_>>().try_into().ok()?;
                Some((slot.parse().ok()?, x.parse().ok()?, y.parse().ok()?))
            })
            .collect::<Option<Vec<_>>>()?;
        return Some(Effect::Menu(MenuLayout {
            slots,
            inventory: [x.parse().ok()?, y.parse().ok()?],
        }));
    }
    if kind == "data" {
        let numbers: Vec<i32> = rest
            .split(' ')
            .map(|n| n.parse().ok())
            .collect::<Option<_>>()?;
        let [x, y, z, ..] = numbers[..] else {
            return None;
        };
        return Some(Effect::Data {
            pos: [x, y, z],
            values: numbers[3..].to_vec(),
        });
    }
    if kind == "net" {
        let [player, data] = rest.split(' ').collect::<Vec<_>>().try_into().ok()?;
        return Some(Effect::Net {
            player: parse_uuid(player)?,
            data: unhex(data)?,
        });
    }
    if kind == "usage" {
        let (level, usage) = rest.split_once(' ')?;
        return Some(Effect::Usage {
            level: level.parse::<u8>().ok()?.min(4),
            usage: usage.to_owned(),
        });
    }
    if kind == "drop" {
        // The stack's strings may hold spaces: it is all the rest.
        let mut parts = rest.splitn(4, ' ');
        let mut number = || parts.next()?.parse::<i32>().ok();
        let pos = [number()?, number()?, number()?];
        let stack = parts.next().filter(|s| !s.is_empty())?;
        return Some(Effect::Drop {
            pos,
            stack: stack.to_owned(),
        });
    }
    if kind == "held" {
        // The stack's strings may hold spaces: it is all the rest.
        let (hand, stack) = rest.split_once(' ')?;
        let hand = match hand {
            "main" => Hand::Main,
            "off" => Hand::Off,
            _ => return None,
        };
        return Some(Effect::Held {
            hand,
            stack: stack.to_owned(),
        });
    }
    let words: Vec<&str> = rest.split(' ').collect();
    let number = |i: usize| words.get(i)?.parse::<f64>().ok();
    Some(match (kind, words.as_slice()) {
        ("attribute", [id, _]) => Effect::Attribute {
            id: (*id).to_owned(),
            base: number(1)?,
        },
        ("health", [_]) => Effect::Health(number(0)? as f32),
        ("motion", [_, _, _]) => {
            let v = [number(0)?, number(1)?, number(2)?];
            if !v.iter().all(|n| n.is_finite()) {
                return None;
            }
            Effect::Motion(v)
        }
        ("teleport", [dimension, _, _, _, _, _]) => {
            let pos = [number(1)?, number(2)?, number(3)?];
            let (yaw, pitch) = (number(4)? as f32, number(5)? as f32);
            if !pos.iter().all(|n| n.is_finite())
                || !yaw.is_finite()
                || !pitch.is_finite()
                || !dimension.contains(':')
            {
                return None;
            }
            Effect::Teleport {
                dimension: (*dimension).to_owned(),
                pos,
                yaw,
                pitch,
            }
        }
        ("xp", [level, _, total]) => Effect::Experience {
            level: level.parse().ok()?,
            progress: number(1)? as f32,
            total: total.parse().ok()?,
        },
        ("clock", [clock, ticks]) => Effect::Clock {
            clock: (*clock).to_owned(),
            ticks: ticks.parse().ok()?,
        },
        ("hand", [hand, item, count]) => Effect::Hand {
            hand: match *hand {
                "main" => Hand::Main,
                "off" => Hand::Off,
                _ => return None,
            },
            item: (*item).to_owned(),
            count: count.parse().ok()?,
        },
        ("sound", [id, source, ..]) if words.len() == 7 => Effect::Sound {
            id: (*id).to_owned(),
            source: source.parse().ok()?,
            at: [number(2)?, number(3)?, number(4)?],
            volume: number(5)? as f32,
            pitch: number(6)? as f32,
        },
        ("block", [x, y, z, flags, state]) => Effect::Block {
            pos: [x.parse().ok()?, y.parse().ok()?, z.parse().ok()?],
            flags: flags.parse().ok()?,
            state: (*state).to_owned(),
        },
        ("destroy", [x, y, z, drop]) => Effect::Destroy {
            pos: [x.parse().ok()?, y.parse().ok()?, z.parse().ok()?],
            drop: match *drop {
                "1" => true,
                "0" => false,
                _ => return None,
            },
        },
        ("fluid", [x, y, z, face, fluid, amount]) => Effect::Fluid {
            pos: [x.parse().ok()?, y.parse().ok()?, z.parse().ok()?],
            face: (*face).to_owned(),
            fluid: (*fluid).to_owned(),
            amount: amount.parse().ok()?,
        },
        ("drops", [x, y, z, state]) => Effect::Drops {
            pos: [x.parse().ok()?, y.parse().ok()?, z.parse().ok()?],
            state: (*state).to_owned(),
        },
        ("schedule", [x, y, z, block, delay]) => Effect::Schedule {
            pos: [x.parse().ok()?, y.parse().ok()?, z.parse().ok()?],
            block: (*block).to_owned(),
            delay: delay.parse().ok()?,
        },
        ("placement", ["none"]) => Effect::Placement(None),
        ("placement", [state]) => Effect::Placement(Some((*state).to_owned())),
        ("overlay", _) => Effect::Overlay(serde_json::from_str(rest).ok()?),
        ("tell", _) => Effect::Tell(serde_json::from_str(rest).ok()?),
        ("feedback", _) => Effect::Feedback(serde_json::from_str(rest).ok()?),
        _ => return None,
    })
}

/// Java's answer as an outcome; Err with why when the mod's code failed.
pub fn outcome(answer: Return) -> Result<Outcome, String> {
    let result = match answer.result.as_str() {
        "pass" => UseResult::Pass,
        "fail" => UseResult::Fail,
        "success" => UseResult::Success,
        "success_server" => UseResult::SuccessServer,
        "consume" => UseResult::Consume,
        "error" => return Err(answer.effects.into_iter().next().unwrap_or_default()),
        other => return Err(format!("unknown result {other}")),
    };
    let mut effects = Vec::new();
    for e in &answer.effects {
        match parse_effect(e) {
            Some(effect) => effects.push(effect),
            None => log::warn!("Kiln: unknown effect from Java: {e}"),
        }
    }
    Ok(Outcome { result, effects })
}

/// The integrated server's side of Kiln: the window's Java (the server's thread asks it), the items the Java mods
/// gave a use of their own, the server events they hear, and the last call's number.
struct ServerSide {
    link: ServerLink,
    items: BTreeSet<String>,
    events: BTreeSet<String>,
    /// Block id → what its code does (`use`, `random`, `step`).
    blocks: BTreeMap<String, BTreeSet<String>>,
    /// Block id → the type of the block entity it makes (`mymod:counter`).
    entities: BTreeMap<String, String>,
    /// Block id → its block entity's item slots, when it is a container.
    slots: BTreeMap<String, Slots>,
    calls: u64,
    /// The roots of the mods' server commands, as Java last told them (D-107).
    commands: BTreeSet<String>,
    /// How many mods registered server commands when Java last told them; None before it has.
    commands_told: Option<usize>,
    /// When the server's commands may be asked for again after a call that failed.
    commands_retry: Option<Instant>,
    /// A player's payload calls that gave up waiting (Java was busy), by call, with the player: their answers are
    /// done at that player's next ticks (`late_payloads`).
    late: BTreeMap<u64, u128>,
}

/// How many calls later a payload call's answer is no longer waited for.
const LATE_CALLS: u64 = 1000;

/// Set by the window's thread when Java starts and stops; the server's thread calls through it.
static SERVER: Mutex<Option<ServerSide>> = Mutex::new(None);

fn set_server(side: Option<ServerSide>) {
    if let Ok(mut server) = SERVER.lock() {
        *server = side;
    }
}

fn set_server_items(ids: Vec<String>) {
    if let Ok(mut server) = SERVER.lock()
        && let Some(side) = server.as_mut()
    {
        log::info!("Kiln: Java mods' items with a use: {}", ids.join(", "));
        side.items = ids.into_iter().collect();
    }
}

fn set_server_events(events: Vec<String>) {
    if let Ok(mut server) = SERVER.lock()
        && let Some(side) = server.as_mut()
    {
        log::info!("Kiln: server events Java mods hear: {}", events.join(", "));
        side.events = events.into_iter().collect();
    }
}

/// The menus Java mods' blocks opened, by block id: learned when a player first opens one (the mod's own code lays it
/// out), kept while the game runs.
static MENUS: Mutex<BTreeMap<String, MenuLayout>> = Mutex::new(BTreeMap::new());

/// Either thread: block `id` opened the menu `layout`; whether that is new (its container's table is to be built
/// again: the menu's grid holds it from now on).
pub fn set_block_menu(id: &str, layout: MenuLayout) -> bool {
    let Ok(mut menus) = MENUS.lock() else {
        return false;
    };
    menus.insert(id.to_owned(), layout.clone()).as_ref() != Some(&layout)
}

/// Either thread: the menu block `id` opens, once a player has opened it.
pub fn block_menu(id: &str) -> Option<MenuLayout> {
    MENUS.lock().ok()?.get(id).cloned()
}

/// How often the Java mods' blocks were announced: a server level whose block entity ticking table is older builds it
/// again (`native_block_entity_ticks` answers from the announcement).
static BLOCKS_ANNOUNCED: AtomicU64 = AtomicU64::new(0);

/// `blocks <id> <what>[,<what>] [<block entity type>]...`
fn set_server_blocks(blocks: Vec<String>) {
    if let Ok(mut server) = SERVER.lock()
        && let Some(side) = server.as_mut()
    {
        log::info!("Kiln: Java mods' blocks with code: {}", blocks.join("; "));
        let announced = parse_blocks(&blocks);
        side.blocks = announced.blocks;
        side.entities = announced.entities;
        side.slots = announced.slots;
        BLOCKS_ANNOUNCED.fetch_add(1, Ordering::Relaxed);
    }
}

/// What the `blocks` reply says, by block id.
#[derive(Debug, Default, PartialEq)]
struct Announced {
    /// What its code does.
    blocks: BTreeMap<String, BTreeSet<String>>,
    /// The type of its block entity.
    entities: BTreeMap<String, String>,
    /// Its block entity's item slots.
    slots: BTreeMap<String, Slots>,
}

/// A Java mod's block entity's item slots, as Horizon's mod containers say them: how many, the slots each face
/// reaches (down, up, north, south, west, east), and the slots hoppers may put into and take from.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct Slots {
    pub count: usize,
    pub faces: [Vec<usize>; 6],
    pub inputs: Vec<usize>,
    pub outputs: Vec<usize>,
}

/// `<count>;<down>;<up>;<north>;<south>;<west>;<east>;<in>;<out>`, each list comma-separated.
fn parse_slots(text: &str) -> Option<Slots> {
    let parts: Vec<&str> = text.split(';').collect();
    let [count, faces @ .., inputs, outputs] = parts.as_slice() else {
        return None;
    };
    let list = |l: &str| -> Option<Vec<usize>> {
        l.split(',')
            .filter(|n| !n.is_empty())
            .map(|n| n.parse().ok())
            .collect()
    };
    let faces: Vec<Vec<usize>> = faces.iter().map(|f| list(f)).collect::<Option<_>>()?;
    Some(Slots {
        count: count.parse().ok()?,
        faces: faces.try_into().ok()?,
        inputs: list(inputs)?,
        outputs: list(outputs)?,
    })
}

/// The `blocks` reply's fields: `<id> <what>[,<what>] [<block entity type> [<slots>]]`.
fn parse_blocks(blocks: &[String]) -> Announced {
    let mut announced = Announced::default();
    for block in blocks {
        let mut words = block.split(' ');
        let (Some(id), Some(what)) = (words.next(), words.next()) else {
            continue;
        };
        announced
            .blocks
            .insert(id.to_owned(), what.split(',').map(str::to_owned).collect());
        if let Some(entity) = words.next() {
            announced.entities.insert(id.to_owned(), entity.to_owned());
        }
        if let Some(slots) = words.next().and_then(parse_slots) {
            announced.slots.insert(id.to_owned(), slots);
        }
    }
    announced
}

/// Either thread: the item slots of the block entity a Java mod's block `id` makes, when it is a container.
pub fn block_slots(id: &str) -> Option<Slots> {
    SERVER.lock().ok()?.as_ref()?.slots.get(id).cloned()
}

/// How often the Java mods' blocks were announced (0: not yet, in this run of the game).
pub fn blocks_announced() -> u64 {
    BLOCKS_ANNOUNCED.load(Ordering::Relaxed)
}

/// Either thread: the type of the block entity a Java mod's block `id` makes (`EntityBlock.newBlockEntity`).
pub fn block_entity_type(id: &str) -> Option<String> {
    SERVER.lock().ok()?.as_ref()?.entities.get(id).cloned()
}

/// Either thread: whether `entity` is the type of a Java mod's block entity.
pub fn java_block_entity(entity: &str) -> bool {
    SERVER.lock().ok().is_some_and(|s| {
        s.as_ref()
            .is_some_and(|side| side.entities.values().any(|e| e == entity))
    })
}

/// The server's thread: whether a Java mod's block `id` has code for `what` (`use`, `random`, `step`).
pub fn block_does(id: &str, what: &str) -> bool {
    SERVER
        .lock()
        .ok()
        .and_then(|s| {
            s.as_ref()
                .map(|side| side.blocks.get(id).is_some_and(|w| w.contains(what)))
        })
        .unwrap_or(false)
}

/// Horizon's hooks Kiln hands Java for its Mixin bridge (D-102): those of the game's methods some mod's mixin goes
/// into, as Java said (kiln/java/kiln/bridge.json). Either thread reads it.
static BRIDGED: Mutex<BTreeSet<String>> = Mutex::new(BTreeSet::new());

fn set_bridged(hooks: Vec<String>) {
    log::info!(
        "Kiln: Horizon's hooks the mods' mixins need: {}",
        hooks.join(", ")
    );
    if let Ok(mut bridged) = BRIDGED.lock() {
        *bridged = hooks.into_iter().collect();
    }
}

/// Either thread: whether Kiln's Mixin bridge hands Java Horizon's hook `hook` (a mod's mixin is in a method there).
pub fn bridged(hook: &str) -> bool {
    BRIDGED.lock().is_ok_and(|b| b.contains(hook))
}

/// The window's thread: Horizon's hook `hook` fired with these fields (its parameters, in its order), for the game's
/// methods there a mod's mixin goes into. Java runs them with its next command; nothing waits for it.
pub fn hook(hook: &str, fields: &[String]) {
    if !bridged(hook) {
        return;
    }
    STATE.with(|s| {
        if let State::Running(r) = &mut *s.borrow_mut() {
            let mut all: Vec<&str> = vec![hook];
            all.extend(fields.iter().map(String::as_str));
            if let Err(why) = r.java.send("hook", &all) {
                log::warn!("Kiln: the {hook} hook wasn't handed to Java: {why}");
            }
        }
    });
}

/// The `bridge` call's fields: the hook, how many fields it has, they, then the player when the hook is about one.
pub fn bridge_fields(
    call: u64,
    world: &Path,
    game_time: u64,
    hook: &str,
    fields: &[String],
    player: Option<&PlayerSnapshot>,
) -> Vec<String> {
    let mut all = call_fields(call, world, game_time);
    all.push(hook.to_owned());
    all.push(fields.len().to_string());
    all.extend(fields.iter().cloned());
    if let Some(player) = player {
        all.extend(player_fields(player));
    }
    all
}

/// The server's thread: Horizon's hook `hook` fired with these fields, for the game's methods there a mod's mixin goes
/// into (Kiln's Mixin bridge, D-102); `player` when the hook is about one. The game waits for Java's answer: its
/// `Effect::Out`s are what the hook takes back (`canceled`...). None when no mixin is there, or the call failed.
pub fn bridge(
    world: &Path,
    game_time: u64,
    hook: &str,
    fields: &[String],
    player: Option<&PlayerSnapshot>,
    answer: &mut dyn FnMut(&[String]) -> String,
) -> Option<Outcome> {
    if !bridged(hook) {
        return None;
    }
    server_call(
        "bridge",
        |_| true,
        |call| bridge_fields(call, world, game_time, hook, fields, player),
        answer,
        &format!("the mods' mixins at Horizon's {hook} hook"),
    )
}

/// The server's thread: whether a Java mod hears `event` (`join`, `respawn`, `connect`, `disconnect`...).
pub fn hears(event: &str) -> bool {
    SERVER
        .lock()
        .ok()
        .and_then(|s| s.as_ref().map(|side| side.events.contains(event)))
        .unwrap_or(false)
}

/// The server's thread: whether a Java mod gave `item` a use of its own (only those are worth a call).
/// A Java mod's liquid block (D-198): the bucket of its fluid, as Java announced it (`bucket=<item>`).
pub fn liquid_bucket(block: &str) -> Option<String> {
    let server = SERVER.lock().ok()?;
    let side = server.as_ref()?;
    side.blocks
        .get(block)?
        .iter()
        .find_map(|w| w.strip_prefix("bucket=").map(str::to_owned))
}

/// The Java mod's liquid block a bucket item holds (D-198), as Java announced it.
pub fn bucket_liquid(item: &str) -> Option<String> {
    let server = SERVER.lock().ok()?;
    let side = server.as_ref()?;
    let token = format!("bucket={item}");
    side.blocks
        .iter()
        .find(|(_, what)| what.contains(&token))
        .map(|(id, _)| id.clone())
}

pub fn has_use(item: &str) -> bool {
    SERVER
        .lock()
        .ok()
        .and_then(|s| s.as_ref().map(|side| side.items.contains(item)))
        .unwrap_or(false)
}

/// The server's thread: `player` uses `item` with `hand`, in the world in `world`. Asks the Java mod whose item it
/// is and waits for its answer; None when no Java mod's item has a use, or the call failed (logged): the use then
/// goes on as Horizon's own.
pub fn use_item(
    world: &Path,
    game_time: u64,
    item: &str,
    hand: Hand,
    player: &PlayerSnapshot,
    answer: &mut dyn FnMut(&[String]) -> String,
) -> Option<Outcome> {
    server_call(
        "use",
        |side| side.items.contains(item) || side.events.contains("use_item"),
        |call| use_fields(call, world, game_time, item, hand, player),
        answer,
        &format!("using {item}"),
    )
}

/// The server's thread: `player` came into a level (joined the game, or changed dimension). None when no Java mod
/// hears it, or the call failed (logged).
pub fn player_joined(
    world: &Path,
    game_time: u64,
    player: &PlayerSnapshot,
    answer: &mut dyn FnMut(&[String]) -> String,
) -> Option<Outcome> {
    server_call(
        "join",
        |side| side.events.contains("join"),
        |call| join_fields(call, world, game_time, player),
        answer,
        &format!("{} coming into {}", player.name, player.dimension),
    )
}

/// How long the server waits for the mods' server commands: they are made once a world, as its server starts (D-107).
const COMMANDS_TIMEOUT: Duration = Duration::from_secs(2);
/// How long after a failed ask the mods' server commands are asked for again.
const COMMANDS_RETRY: Duration = Duration::from_secs(5);

/// How many mods register server commands, as Java says among the events they hear (`commands:<n>`).
fn command_mods(side: &ServerSide) -> usize {
    side.events
        .iter()
        .find_map(|e| e.strip_prefix("commands:")?.parse().ok())
        .unwrap_or(0)
}

/// The server's thread: whether the mods' server commands are to be asked for again. Horizon asks once a world, when
/// the server starts, which can be before Java has started or loaded every mod: so again when more mods register
/// commands than Java last told of, a few seconds after an ask that failed.
pub fn server_commands_stale() -> bool {
    let Ok(server) = SERVER.lock() else {
        return false;
    };
    let Some(side) = server.as_ref() else {
        return false;
    };
    let mods = command_mods(side);
    mods > 0
        && side.commands_told != Some(mods)
        && side.commands_retry.is_none_or(|at| Instant::now() >= at)
}

/// The server's thread, as Horizon's server asks its mods for their commands: the Java mods register theirs, told as
/// usages for Horizon's tree, each with the operator level it needs. None when no Java mod registers commands, or the
/// call failed (logged; `server_commands_stale` says when to ask again).
pub fn server_commands(
    world: &Path,
    game_time: u64,
    answer: &mut dyn FnMut(&[String]) -> String,
) -> Option<Vec<(String, u8)>> {
    let mut server = SERVER.lock().ok()?;
    let side = server.as_mut()?;
    if command_mods(side) == 0 {
        return None;
    }
    let outcome = side_call(
        side,
        "server_commands",
        |call| call_fields(call, world, game_time),
        answer,
        "the Java mods' server commands",
        None,
        COMMANDS_TIMEOUT,
    );
    let Some(outcome) = outcome else {
        side.commands_retry = Some(Instant::now() + COMMANDS_RETRY);
        return None;
    };
    let usages: Vec<(String, u8)> = outcome
        .effects
        .into_iter()
        .filter_map(|e| match e {
            Effect::Usage { level, usage } => Some((usage, level)),
            _ => None,
        })
        .collect();
    side.commands = usages
        .iter()
        .filter_map(|(usage, _)| usage.split(' ').next())
        .map(str::to_owned)
        .collect();
    side.commands_told = Some(command_mods(side));
    side.commands_retry = None;
    let names: Vec<String> = side.commands.iter().map(|n| format!("/{n}")).collect();
    if names.is_empty() {
        log::info!("Kiln: the Java mods registered no server command Horizon can take");
    } else {
        log::info!("Kiln: Java mods' server commands: {}", names.join(", "));
    }
    Some(usages)
}

/// The server's thread: whether `line` (a command without its slash) is one of the Java mods' server commands.
pub fn is_server_command(line: &str) -> bool {
    let name = line.split(' ').next().unwrap_or("");
    SERVER
        .lock()
        .ok()
        .and_then(|s| s.as_ref().map(|side| side.commands.contains(name)))
        .unwrap_or(false)
}

/// The server's thread: `player` typed `line` (without its slash), one of the Java mods' server commands, at operator
/// level `permission`. Java runs it with Brigadier as that player; its answer is `Effect::Feedback` lines and what it
/// did to them. None when it isn't one of theirs, or the call failed (logged).
pub fn server_command(
    world: &Path,
    game_time: u64,
    permission: u8,
    line: &str,
    player: &PlayerSnapshot,
    answer: &mut dyn FnMut(&[String]) -> String,
) -> Option<Outcome> {
    let name = line.split(' ').next().unwrap_or("");
    server_call(
        "server_command",
        |side| side.commands.contains(name),
        |call| {
            let mut fields = call_fields(call, world, game_time);
            fields.push(permission.to_string());
            fields.push(line.to_owned());
            fields.extend(player_fields(player));
            fields
        },
        answer,
        &format!("/{line}"),
    )
}

/// The server's thread, at `player`'s first tick on the server: their connection is ready (Fabric's
/// `ServerPlayConnectionEvents` INIT and JOIN, where mods send a joining player their first sync). None when no Java mod
/// hears it, or the call failed (logged). Nothing in the game waits on it: when Java is busy past the call's time (a
/// world just opened), its answer is still done when it comes, at the player's next tick (`late_payloads`).
pub fn player_connected(
    world: &Path,
    game_time: u64,
    player: &PlayerSnapshot,
    answer: &mut dyn FnMut(&[String]) -> String,
) -> Option<Outcome> {
    server_call_for(
        "connect",
        |side| side.events.contains("connect"),
        |call| join_fields(call, world, game_time, player),
        answer,
        &format!("{} connecting", player.name),
        Some(player.uuid),
    )
}

/// The server's thread: `player` (as they last were) left the server (Fabric's `ServerPlayConnectionEvents`
/// DISCONNECT). None when no Java mod hears it, or the call failed (logged).
pub fn player_disconnected(
    world: &Path,
    game_time: u64,
    player: &PlayerSnapshot,
    answer: &mut dyn FnMut(&[String]) -> String,
) -> Option<Outcome> {
    server_call(
        "disconnect",
        |side| side.events.contains("disconnect"),
        |call| join_fields(call, world, game_time, player),
        answer,
        &format!("{} leaving", player.name),
    )
}

/// The `from_client` command's fields: call `call`, in the world in `world`, `player`'s message `data` (for
/// `NET_CHANNEL`).
pub fn from_client_fields(
    call: u64,
    world: &Path,
    game_time: u64,
    data: &[u8],
    player: &PlayerSnapshot,
) -> Vec<String> {
    let mut fields = call_fields(call, world, game_time);
    fields.push(hex(data));
    fields.extend(player_fields(player));
    fields
}

/// The server's thread, at `player`'s tick: their message `data` on `NET_CHANNEL` reaches the mods' server receivers
/// (`ServerPlayNetworking`). None when no Java mod receives payloads, or the call failed (logged).
/// A payload is a player's own input, which the game doesn't wait on: when Java is busy past the call's time, its answer
/// is still done when it comes (`late_payloads`).
pub fn from_client(
    world: &Path,
    game_time: u64,
    data: &[u8],
    player: &PlayerSnapshot,
    answer: &mut dyn FnMut(&[String]) -> String,
) -> Option<Outcome> {
    server_call_for(
        "from_client",
        |side| side.events.contains("payload"),
        |call| from_client_fields(call, world, game_time, data, player),
        answer,
        &format!("a payload from {}", player.name),
        Some(player.uuid),
    )
}

/// The server's thread, at `player`'s tick: what the mods did with the player's payloads (and connection) whose calls gave up waiting,
/// once Java answered them, oldest first; a call that failed in Java is logged.
pub fn late_payloads(player: u128) -> Vec<Outcome> {
    let Ok(mut server) = SERVER.lock() else {
        return Vec::new();
    };
    let Some(side) = server.as_mut() else {
        return Vec::new();
    };
    let calls = side.calls;
    side.late.retain(|call, _| calls - call < LATE_CALLS);
    if !side.late.values().any(|p| *p == player) {
        return Vec::new();
    }
    let late = &side.late;
    let answers = side.link.take_late(|call| late.get(&call) == Some(&player));
    let mut outcomes = Vec::new();
    for answer in answers {
        side.late.remove(&answer.call);
        match outcome(answer) {
            Ok(outcome) => outcomes.push(outcome),
            Err(why) => log::warn!("Kiln: a late payload failed in Java: {why}"),
        }
    }
    outcomes
}

/// The most messages from the server the window keeps for a Java not started yet (`from_server`).
pub const NET_EARLY_MAX: usize = 64;

/// The window's thread: a message from the server on `NET_CHANNEL`, for the mods' client receivers
/// (`ClientPlayNetworking`), handed to Java now. Kiln's Java starts with the window's first tick in a world, and a
/// server sends a joining player their first sync at once (Fabric's JOIN): what comes before Java started waits for it
/// (`NET_EARLY_MAX` at most), and is handed over as it starts. Nothing once Java stopped. Never waits.
pub fn from_server(data: &[u8]) {
    STATE.with(|s| match &mut *s.borrow_mut() {
        State::Running(r) => to_java(r, data),
        State::Idle => NET_EARLY.with(|e| {
            let mut early = e.borrow_mut();
            if early.len() < NET_EARLY_MAX {
                early.push(data.to_vec());
            } else {
                log::warn!(
                    "Kiln: a payload from the server dropped: {NET_EARLY_MAX} wait for Java already"
                );
            }
        }),
        State::Stopped(_) => {}
    });
}

/// A message from the server, to Java.
fn to_java(r: &mut Running, data: &[u8]) {
    if let Err(why) = r.java.send("from_server", &[&hex(data)]) {
        log::warn!("Kiln: a payload from the server didn't reach Java: {why}");
    }
}

/// The messages from the server that came before Java started, now that it has (or nothing, when it couldn't).
fn early_payloads(state: &mut State) {
    let early = NET_EARLY.with(|e| std::mem::take(&mut *e.borrow_mut()));
    if let State::Running(r) = state {
        for data in early {
            to_java(r, &data);
        }
    }
}

/// The window's thread: the mods' payloads for the server since the last call, each a message for `NET_CHANNEL`.
pub fn take_net() -> Vec<Vec<u8>> {
    NET_OUT.with(|n| std::mem::take(&mut *n.borrow_mut()))
}

/// The server's thread: `old` (as they last were) respawned as `new`; `alive` when they didn't die (vanilla's way
/// back from the End). None when no Java mod hears it, or the call failed (logged).
pub fn player_respawned(
    world: &Path,
    game_time: u64,
    alive: bool,
    old: &PlayerSnapshot,
    new: &PlayerSnapshot,
    answer: &mut dyn FnMut(&[String]) -> String,
) -> Option<Outcome> {
    server_call(
        "respawn",
        |side| side.events.contains("respawn"),
        |call| respawn_fields(call, world, game_time, alive, old, new),
        answer,
        &format!("{} respawning", new.name),
    )
}

/// The block id of a state as the game writes it (`mymod:lamp[lit=true]` is `mymod:lamp`).
fn block_id(state: &str) -> &str {
    state.split_once('[').map_or(state, |(id, _)| id)
}

/// The server's thread: `player` right clicks the block `state` at `pos` (`face` the face, `up`) with `hand`. None
/// when neither a Java mod's block has a use nor a mod hears every block's use (Fabric's `UseBlockCallback`), or the
/// call failed (logged).
#[allow(clippy::too_many_arguments)]
pub fn block_used(
    world: &Path,
    game_time: u64,
    pos: [i32; 3],
    state: &str,
    face: &str,
    hand: Hand,
    player: &PlayerSnapshot,
    answer: &mut dyn FnMut(&[String]) -> String,
) -> Option<Outcome> {
    let id = block_id(state);
    server_call(
        "use_block",
        |side| {
            side.blocks.get(id).is_some_and(|w| w.contains("use"))
                || side.events.contains("use_block")
        },
        |call| use_block_fields(call, world, game_time, pos, state, face, hand, player),
        answer,
        &format!("using {id}"),
    )
}

/// The server's thread: the block `state` at `pos` in `dimension` ticks at random. None when no Java mod's block
/// ticks so, or the call failed (logged).
pub fn random_tick(
    world: &Path,
    game_time: u64,
    dimension: &str,
    pos: [i32; 3],
    state: &str,
    answer: &mut dyn FnMut(&[String]) -> String,
) -> Option<Outcome> {
    let id = block_id(state);
    server_call(
        "random_tick",
        |side| side.blocks.get(id).is_some_and(|w| w.contains("random")),
        |call| random_tick_fields(call, world, game_time, dimension, pos, state),
        answer,
        &format!("a random tick of {id}"),
    )
}

/// The server's thread: `player` stands on the block `state` at `pos`. None when no Java mod's block has a step, or
/// the call failed (logged).
pub fn stepped_on(
    world: &Path,
    game_time: u64,
    pos: [i32; 3],
    state: &str,
    player: &PlayerSnapshot,
    answer: &mut dyn FnMut(&[String]) -> String,
) -> Option<Outcome> {
    let id = block_id(state);
    server_call(
        "step_on",
        |side| side.blocks.get(id).is_some_and(|w| w.contains("step")),
        |call| step_on_fields(call, world, game_time, pos, state, player),
        answer,
        &format!("{} standing on {id}", player.name),
    )
}

/// A call about the block `state` (`mymod:lamp[lit=true]`) when its mod's code does `kind`: `command` with the call's
/// first fields and `rest`.
fn block_call(
    command: &str,
    kind: &str,
    state: &str,
    world: &Path,
    game_time: u64,
    rest: Vec<String>,
    answer: &mut dyn FnMut(&[String]) -> String,
) -> Option<Outcome> {
    let id = block_id(state);
    server_call(
        command,
        |side| side.blocks.get(id).is_some_and(|w| w.contains(kind)),
        |call| block_call_fields(call, world, game_time, rest),
        answer,
        &format!("{command} of {id}"),
    )
}

/// The server's thread: `player` pressed menu button `id` on the screen of the block entity at `pos`, whose block is
/// `block` (Horizon's `native_menu_button`): Kiln's side panel of a machine (D-214). None for a block with no Java
/// block entity, or a call that failed.
pub fn menu_button(
    world: &Path,
    game_time: u64,
    pos: [i32; 3],
    block: &str,
    id: i32,
    player: &PlayerSnapshot,
    answer: &mut dyn FnMut(&[String]) -> String,
) -> Option<Outcome> {
    let mut rest: Vec<String> = pos.map(|c| c.to_string()).into();
    rest.push(id.to_string());
    rest.extend(player_fields(player));
    // Longer than a tick's call: the first press loads the mod's payload classes and its codec.
    let mut server = SERVER.lock().ok()?;
    let side = server.as_mut()?;
    if !side.entities.contains_key(block) {
        return None;
    }
    side_call(
        side,
        "menu_button",
        |call| block_call_fields(call, world, game_time, rest),
        answer,
        &format!("a menu button of {block}"),
        None,
        MENU_BUTTON_TIMEOUT,
    )
}

/// How long a menu button's call is waited for (D-214).
const MENU_BUTTON_TIMEOUT: Duration = Duration::from_secs(3);

/// The server's thread: the block `state` at `pos` in `dimension` takes its scheduled tick (`tick`).
pub fn scheduled_tick(
    world: &Path,
    game_time: u64,
    dimension: &str,
    pos: [i32; 3],
    state: &str,
    answer: &mut dyn FnMut(&[String]) -> String,
) -> Option<Outcome> {
    let mut rest = vec![dimension.to_owned()];
    rest.extend(pos.map(|c| c.to_string()));
    rest.push(state.to_owned());
    block_call("block_tick", "tick", state, world, game_time, rest, answer)
}

/// The server's thread: a neighbour of the block `state` at `pos` in `dimension` changed (`neighborChanged`);
/// `moved` when a piston moved it.
pub fn neighbor_changed(
    world: &Path,
    game_time: u64,
    dimension: &str,
    pos: [i32; 3],
    state: &str,
    moved: bool,
    answer: &mut dyn FnMut(&[String]) -> String,
) -> Option<Outcome> {
    let mut rest = vec![dimension.to_owned()];
    rest.extend(pos.map(|c| c.to_string()));
    rest.extend([state.to_owned(), if moved { "1" } else { "0" }.to_owned()]);
    block_call(
        "neighbor", "neighbor", state, world, game_time, rest, answer,
    )
}

/// The server's thread: a player placed or broke a block at `pos` in `dimension`, near a mod's block entity. Java
/// loads the block entities within 16 blocks of it again when next met, as a chunk loading would: Kiln hears no
/// other change to the game's blocks, and a mod that checks the blocks around its block entity (a multiblock's
/// casings) does it when it loads.
pub fn blocks_changed(world: &Path, game_time: u64, dimension: &str, pos: [i32; 3]) {
    let mut rest = vec![dimension.to_owned()];
    rest.extend(pos.map(|c| c.to_string()));
    server_call(
        "changed",
        |_| true,
        |call| block_call_fields(call, world, game_time, rest),
        &mut |_| String::new(),
        "blocks changed",
    );
}

/// The server's thread: the state a mod's block takes where it is placed (`getStateForPlacement`).
pub fn placement(
    world: &Path,
    game_time: u64,
    dimension: &str,
    placement: &Placement,
    answer: &mut dyn FnMut(&[String]) -> String,
) -> Option<Outcome> {
    let rest = placement_fields(dimension, placement);
    block_call(
        "placement",
        "placement",
        &placement.state,
        world,
        game_time,
        rest,
        answer,
    )
}

/// The server's thread: `player` placed the block `state` at `pos` from `count` of `item` (`setPlacedBy`).
#[allow(clippy::too_many_arguments)]
pub fn placed(
    world: &Path,
    game_time: u64,
    pos: [i32; 3],
    state: &str,
    item: &str,
    count: u32,
    player: &PlayerSnapshot,
    answer: &mut dyn FnMut(&[String]) -> String,
) -> Option<Outcome> {
    let mut rest: Vec<String> = pos.map(|c| c.to_string()).into();
    rest.extend([state.to_owned(), item.to_owned(), count.to_string()]);
    rest.extend(player_fields(player));
    block_call("placed", "placed", state, world, game_time, rest, answer)
}

/// The server's thread: the server tick of the block entity of type `entity` at `pos` in `dimension`, whose block is
/// `state` and whose saved compound is `saved` (the network's binary NBT).
///
/// The compound goes to Java only when it isn't the one Java last had of it (D-140): Kiln keeps, for each block
/// entity that ticks, the compound the game had after its last tick and a token Java knows it by, and while the
/// game's is still that one the tick sends the token alone. Java asks for the compound (`ticking`) when it no longer
/// keeps the object it ticked (another world, a block changed near it, a call that failed). A tick's own saved
/// compound is the one kept next; [`block_entity_kept`] says when the game keeps it in another form.
#[allow(clippy::too_many_arguments)]
pub fn block_entity_tick(
    world: &Path,
    game_time: u64,
    dimension: &str,
    pos: [i32; 3],
    state: &str,
    entity: &str,
    saved: &[u8],
    answer: &mut dyn FnMut(&[String]) -> String,
) -> Option<Outcome> {
    let key = (dimension.to_owned(), pos);
    let (token, whole) = {
        let mut ticked = TICKED.lock().unwrap_or_else(|e| e.into_inner());
        match ticked.get(&key) {
            Some(t) if t.saved == saved => (t.token, false),
            _ => {
                if ticked.len() >= TICKED_KEPT {
                    ticked.clear();
                }
                let token = TOKENS.fetch_add(1, Ordering::Relaxed) + 1;
                let saved = saved.to_vec();
                ticked.insert(key.clone(), Ticked { token, saved });
                (token, true)
            }
        }
    };
    count_ticked(whole);
    let mut rest = vec![dimension.to_owned()];
    rest.extend(pos.map(|c| c.to_string()));
    rest.extend([
        state.to_owned(),
        entity.to_owned(),
        token.to_string(),
        if whole {
            nbt_field(saved)
        } else {
            String::new()
        },
    ]);
    let mut answer = |what: &[String]| match what {
        [ticking] if ticking == "ticking" => nbt_field(saved),
        _ => answer(what),
    };
    let outcome = block_call(
        "block_entity_tick",
        "ticks",
        state,
        world,
        game_time,
        rest,
        &mut answer,
    )?;
    let now = outcome.effects.iter().rev().find_map(|e| match e {
        Effect::BlockEntity { pos: at, data } if *at == pos => Some(data),
        _ => None,
    });
    if let Some(now) = now {
        let mut ticked = TICKED.lock().unwrap_or_else(|e| e.into_inner());
        if let Some(t) = ticked.get_mut(&key) {
            t.saved.clone_from(now);
        }
    }
    Some(outcome)
}

/// The game keeps the compound of the block entity at `pos` in `dimension` as `saved` (the network's binary NBT), as
/// Java's code saved it in a call (its own tick, or another's that reached it: a cable's giving it energy), in the form
/// the game keeps it (a container's slots as the game keeps them): its next tick compares the game's compound with
/// this one, and sends only its token while they are the same.
pub fn block_entity_kept(dimension: &str, pos: [i32; 3], saved: &[u8]) {
    let mut ticked = TICKED.lock().unwrap_or_else(|e| e.into_inner());
    if let Some(t) = ticked.get_mut(&(dimension.to_owned(), pos)) {
        t.saved.clear();
        t.saved.extend_from_slice(saved);
    }
}

/// Block entities' ticks so far: those whose compound went whole, and all of them. At each 2^n-th tick (from 1,024)
/// the game's log says how many of the ticks since the last line sent it whole (D-140).
static TICKS_WHOLE: Mutex<(u64, u64, u64)> = Mutex::new((0, 0, 0));

fn count_ticked(whole: bool) {
    let mut counts = TICKS_WHOLE.lock().unwrap_or_else(|e| e.into_inner());
    counts.0 += 1;
    counts.1 += 1;
    counts.2 += u64::from(whole);
    let (all, since, sent) = *counts;
    if all >= 1024 && all.is_power_of_two() {
        log::info!(
            "Kiln: block entities' ticks up to the {all}th: {sent} of the last {since} sent the compound whole"
        );
        counts.1 = 0;
        counts.2 = 0;
    }
}

/// The game didn't take what Java saved of the block entity at `pos` in `dimension`: its next tick sends its compound
/// whole, so Java's object is loaded again from the game's.
pub fn block_entity_forget(dimension: &str, pos: [i32; 3]) {
    let mut ticked = TICKED.lock().unwrap_or_else(|e| e.into_inner());
    ticked.remove(&(dimension.to_owned(), pos));
}

/// A block entity's compound as the game last had it after a tick, and the token Java knows it by.
struct Ticked {
    token: u64,
    saved: Vec<u8>,
}

/// The block entities that tick, by dimension and position ([`block_entity_tick`]).
static TICKED: Mutex<BTreeMap<(String, [i32; 3]), Ticked>> = Mutex::new(BTreeMap::new());

/// How many block entities [`TICKED`] keeps: past that it starts again, each one's compound sent whole once more.
const TICKED_KEPT: usize = 10_000;

/// The last token given a compound sent whole; 0 is none.
static TOKENS: AtomicU64 = AtomicU64::new(0);

const BASE64: &[u8; 64] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

/// Binary NBT as a line's field: base64, as Java's `Base64.getEncoder` writes it (padded).
pub fn nbt_field(bytes: &[u8]) -> String {
    let mut out = String::with_capacity(bytes.len().div_ceil(3) * 4);
    for chunk in bytes.chunks(3) {
        let b = [
            chunk[0],
            *chunk.get(1).unwrap_or(&0),
            *chunk.get(2).unwrap_or(&0),
        ];
        let n = u32::from(b[0]) << 16 | u32::from(b[1]) << 8 | u32::from(b[2]);
        for k in 0..4 {
            if k <= chunk.len() {
                out.push(BASE64[(n >> (18 - 6 * k) & 63) as usize] as char);
            } else {
                out.push('=');
            }
        }
    }
    out
}

/// A line's field of base64 (padded or not) as the bytes it holds; None for anything else.
pub fn from_nbt_field(field: &str) -> Option<Vec<u8>> {
    let field = field.trim_end_matches('=');
    let mut out = Vec::with_capacity(field.len() * 3 / 4);
    let (mut n, mut bits) = (0u32, 0u32);
    for c in field.bytes() {
        let value = match c {
            b'A'..=b'Z' => c - b'A',
            b'a'..=b'z' => c - b'a' + 26,
            b'0'..=b'9' => c - b'0' + 52,
            b'+' => 62,
            b'/' => 63,
            _ => return None,
        };
        n = n << 6 | u32::from(value);
        bits += 6;
        if bits >= 8 {
            bits -= 8;
            out.push((n >> bits) as u8);
            n &= (1 << bits) - 1;
        }
    }
    // A last group of one character holds no whole byte.
    (bits < 6).then_some(out)
}

/// Where in a server tick Fabric's `ServerTickEvents` fire: the server's start, before its levels tick; each level's
/// start and end, around that level's tick (its block entities in it); the server's end, after the last level.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum TickPhase {
    Start,
    LevelStart,
    LevelEnd,
    End,
}

impl TickPhase {
    /// The phase as the `server_tick` call names it, and the event a mod listens to for it (`listens`).
    pub fn names(self) -> (&'static str, &'static str) {
        match self {
            TickPhase::Start => ("start", "server_tick_start"),
            TickPhase::LevelStart => ("level_start", "level_tick_start"),
            TickPhase::LevelEnd => ("level_end", "level_tick_end"),
            TickPhase::End => ("end", "server_tick_end"),
        }
    }
}

/// The `server_tick` command's fields: call `call` at `phase`, in `dimension` for a level's phase (empty for the
/// server's).
pub fn server_tick_fields(
    call: u64,
    world: &Path,
    game_time: u64,
    phase: TickPhase,
    dimension: &str,
) -> Vec<String> {
    let mut fields = call_fields(call, world, game_time);
    fields.extend([phase.names().0.to_owned(), dimension.to_owned()]);
    fields
}

/// How many of the server's tick events are skipped after one Java didn't answer: a second's worth, so a Java that
/// hangs costs the game one call's wait a second, not every tick's.
pub const TICK_EVENTS_SKIPPED: u32 = 20 * 4;

/// The tick events still to skip after a failed one (`TICK_EVENTS_SKIPPED`).
static TICK_SKIP: AtomicU64 = AtomicU64::new(0);

/// The server's thread: the server's tick, or the tick of the level `dimension`, is at `phase`: Fabric's tick event
/// for it, when a Java mod listens to it (a mod whose class registers one as it loads is heard from the call it loads
/// in). None when none does, or the call failed (logged): the next `TICK_EVENTS_SKIPPED` tick events are skipped
/// then, whatever their phase.
pub fn server_tick(
    world: &Path,
    game_time: u64,
    phase: TickPhase,
    dimension: &str,
    answer: &mut dyn FnMut(&[String]) -> String,
) -> Option<Outcome> {
    let event = phase.names().1;
    if !hears(event) {
        return None;
    }
    // Only the server's thread fires them.
    let skip = TICK_SKIP.load(Ordering::Relaxed);
    if skip > 0 {
        TICK_SKIP.store(skip - 1, Ordering::Relaxed);
        return None;
    }
    let outcome = server_call(
        "server_tick",
        |side| side.events.contains(event),
        |call| server_tick_fields(call, world, game_time, phase, dimension),
        answer,
        event,
    );
    if outcome.is_none() {
        TICK_SKIP.store(u64::from(TICK_EVENTS_SKIPPED), Ordering::Relaxed);
    }
    outcome
}

/// The events Java says the mods listen to now, when a call's effects say so (`listens <event>...`, when a mod began
/// to listen during the call): taken out of `effects`.
pub fn take_listens(effects: &mut Vec<String>) -> Option<Vec<String>> {
    let mut heard = None;
    effects.retain(|e| match e.strip_prefix("listens") {
        Some(rest) if rest.is_empty() || rest.starts_with(' ') => {
            heard = Some(rest.split_whitespace().map(str::to_owned).collect());
            false
        }
        _ => true,
    });
    heard
}

/// The server's thread: `player` is breaking the block `state` at `pos` (`playerWillDestroy`).
pub fn destroyed(
    world: &Path,
    game_time: u64,
    pos: [i32; 3],
    state: &str,
    player: &PlayerSnapshot,
    answer: &mut dyn FnMut(&[String]) -> String,
) -> Option<Outcome> {
    let mut rest: Vec<String> = pos.map(|c| c.to_string()).into();
    rest.push(state.to_owned());
    rest.extend(player_fields(player));
    block_call("destroy", "destroy", state, world, game_time, rest, answer)
}

/// A server call when `wanted`: `command` with the `fields` for the call's number, waited for, Java's questions
/// meanwhile given `answer`'s answers; `what` names it in the log when it fails.
fn server_call(
    command: &str,
    wanted: impl FnOnce(&ServerSide) -> bool,
    fields: impl FnOnce(u64) -> Vec<String>,
    answer: &mut dyn FnMut(&[String]) -> String,
    what: &str,
) -> Option<Outcome> {
    server_call_for(command, wanted, fields, answer, what, None)
}

/// As `server_call`; with `late_for`, a call that gave up waiting keeps its answer for that player's next ticks.
fn server_call_for(
    command: &str,
    wanted: impl FnOnce(&ServerSide) -> bool,
    fields: impl FnOnce(u64) -> Vec<String>,
    answer: &mut dyn FnMut(&[String]) -> String,
    what: &str,
    late_for: Option<u128>,
) -> Option<Outcome> {
    let mut server = SERVER.lock().ok()?;
    let side = server.as_mut()?;
    if !wanted(side) {
        return None;
    }
    side_call(side, command, fields, answer, what, late_for, CALL_TIMEOUT)
}

/// A server call on `side`, waited for up to `timeout`.
fn side_call(
    side: &mut ServerSide,
    command: &str,
    fields: impl FnOnce(u64) -> Vec<String>,
    answer: &mut dyn FnMut(&[String]) -> String,
    what: &str,
    late_for: Option<u128>,
    timeout: Duration,
) -> Option<Outcome> {
    side.calls += 1;
    let fields = fields(side.calls);
    let fields: Vec<&str> = fields.iter().map(String::as_str).collect();
    let mut answer = match side
        .link
        .call(side.calls, command, &fields, timeout, answer)
    {
        Ok(answer) => answer,
        Err(why) => {
            match late_for {
                Some(player) if why.starts_with("no answer") => {
                    side.late.insert(side.calls, player);
                    log::warn!("Kiln: {what}: {why}; done when it comes");
                }
                _ => log::warn!("Kiln: {what}: {why}"),
            }
            return None;
        }
    };
    if let Some(events) = take_listens(&mut answer.effects) {
        log::info!("Kiln: server events Java mods hear: {}", events.join(", "));
        side.events = events.into_iter().collect();
    }
    match outcome(answer) {
        Ok(outcome) => Some(outcome),
        Err(why) => {
            log::warn!("Kiln: {what} failed in Java: {why}");
            None
        }
    }
}

/// Ticks Kiln, if running: `snapshot` once, then Java's replies. Returns chat lines.
fn tick_running(state: &mut State, snapshot: Snapshot) -> Vec<String> {
    let State::Running(r) = state else {
        return Vec::new();
    };
    let sent = r.tick(snapshot, GUI.get(), FRAMES.get().fps());
    let (mut chat, exited) = r.take_replies();
    if let Some(why) = exited.or(sent.err()) {
        if chat.is_empty() {
            chat.push(format!("Kiln: Java stopped: {why}"));
        }
        set_server(None);
        *state = State::Stopped("Java stopped".into());
    } else if let Some(bytes) = r.restart {
        chat.push(restart_java(state, bytes));
    }
    chat
}

thread_local! {
    /// The window's Java was started again by the overlay's "Restart Java now" (D-134) after it ran in the game's
    /// process: it runs in a process of its own from then on, its output in
    /// [`RESTARTED_LOG`](crate::game::kiln_jvm::RESTARTED_LOG), as that JVM stays loaded. A separate Java started again
    /// keeps its usual log.
    static RESTARTED_FROM_PROCESS: Cell<bool> = const { Cell::new(false) };
    /// The memory the Java left in the game's process said it held, in bytes; and whether the banner that says so was
    /// shown (once a game, D-134).
    static OLD_JAVA: Cell<(u64, bool)> = const { Cell::new((0, false)) };
    /// Safe mode (D-134): no Java mods in this game. Offered after one crash, the player's choice; the window's game
    /// only, for this process.
    static SAFE_MODE: Cell<bool> = const { Cell::new(false) };
}

/// Whether the Java running now was started again after Java in the game's process (D-134).
pub fn restarted_from_process() -> bool {
    RESTARTED_FROM_PROCESS.get()
}

/// The overlay's "Restart Java now" (D-134, the owner's choice): the running Java goes and the next tick starts Java
/// again with the mods whose switches are on now. A process has one JVM (D-112), so Java in the game's process can't
/// be made again: it is wound down instead, every mod turned off in it (its listeners, HUD elements, commands and
/// guarded mixin handlers stop, D-116) and Kiln's thread in it ended (`kiln.Host` returns at the end of its input),
/// and the new Java runs in a process of its own. The old JVM stays loaded, idle, until the game closes: its memory
/// stays taken, and what its mods run on threads of their own goes on. A Java in a process of its own just stops.
/// The chat line for the player.
fn restart_java(state: &mut State, bytes: u64) -> String {
    let State::Running(r) = state else {
        return String::new();
    };
    let in_process = r.java.in_process();
    if in_process {
        let every: Vec<&str> = r.mods.iter().map(|(m, ..)| m.id.as_str()).collect();
        if let Err(e) = r.java.send("soft_off", &[&every.join(",")]) {
            log::warn!("Kiln: the old Java didn't take its mods' switches off: {e}");
        }
        RESTARTED_FROM_PROCESS.set(true);
        OLD_JAVA.set((bytes, false));
    }
    log::info!(
        "Kiln: Java starts again (Restart Java now){}",
        if in_process {
            "; the first Java stays loaded, idle, in the game's process until the game closes"
        } else {
            ""
        }
    );
    // The overlay closes with the Java that drew it; what was queued for that Java goes with it.
    SCREEN.with(|m| {
        let mut screen = m.borrow_mut();
        screen.java_says(false);
        screen.commands.clear();
    });
    // The server's link goes first: it holds Java's queue too, and the queue's end is Kiln's Java's end of input.
    set_server(None);
    *state = State::Idle;
    if in_process {
        "Kiln: Java starts again, in a process of its own. The first Java's memory stays taken until the game closes."
            .to_owned()
    } else {
        "Kiln: Java starts again".to_owned()
    }
}

/// A tick of the window's game in a world: Kiln starts on the first one, then ticks its Java mods.
/// `java_home` reads the `java_home` setting and `font` measures the game font's characters (only when
/// starting). Returns lines for the player's chat. Never waits for Java: what it answers shows from the
/// next tick.
pub fn tick(
    data_dir: &Path,
    java_home: impl FnOnce() -> Option<String>,
    font: impl FnOnce() -> Vec<u32>,
    snapshot: Snapshot,
) -> Vec<String> {
    STATE.with(|s| {
        let mut state = s.borrow_mut();
        if matches!(*state, State::Idle) {
            let (next, mut chat) = start(
                &kiln_dir(data_dir),
                java_home(),
                &data_dir.join("java_home"),
                font,
            );
            *state = next;
            early_payloads(&mut state);
            chat.extend(tick_running(&mut state, snapshot));
            return chat;
        }
        tick_running(&mut state, snapshot)
    })
}

/// A frame of the window's game: when no game tick came for a tick's length (paused, on the title screen),
/// Kiln makes one, as vanilla's client ticks there too. `snapshot` reads the game only when it does.
pub fn frame(snapshot: impl FnOnce() -> Snapshot) -> Vec<String> {
    FRAMES.with(|f| {
        let mut rate = f.get();
        rate.frame(Instant::now());
        f.set(rate);
    });
    STATE.with(|s| {
        let mut state = s.borrow_mut();
        match &*state {
            State::Running(r) if r.last_tick.elapsed() >= TICK => {
                tick_running(&mut state, snapshot())
            }
            _ => Vec::new(),
        }
    })
}

/// Whether this thread's Kiln runs its Java in the game's process (D-112), where a server call is answered on the call.
pub fn java_in_process() -> bool {
    STATE.with(|s| matches!(&*s.borrow(), State::Running(r) if r.java.in_process()))
}

/// Outside a world (the title screen): when a Java mod was turned on or off in Options > Mods since Kiln started
/// Java, Kiln stops it, and the next world starts it again with the mods that are on. True when it stopped Java.
pub fn restart_for_switches() -> bool {
    let in_process = java_in_process();
    STATE.with(|s| {
        let mut state = s.borrow_mut();
        if matches!(*state, State::Idle) {
            return false;
        }
        let now = OFF.lock().unwrap_or_else(|e| e.into_inner()).clone();
        let changed = STARTED_OFF.with(|o| o.borrow().as_ref().is_some_and(|off| *off != now));
        if !changed {
            return false;
        }
        // Java in the game's process can't start again (a process has one JVM, D-112): a mod turned off stops being
        // called at once (the soft switch, D-113); the rest applies from the next game start.
        if in_process {
            log::info!("Kiln: Java mods were turned on or off; with Java in the game's process, that applies from the next game start");
            STARTED_OFF.with(|o| *o.borrow_mut() = Some(now));
            return false;
        }
        log::info!("Kiln: Java mods were turned on or off; Java starts again with the next world");
        SCREEN.with(|m| m.borrow_mut().java_says(false));
        if matches!(*state, State::Running(_)) {
            set_server(None);
        }
        // Dropping the running Java stops its process.
        *state = State::Idle;
        STARTED_OFF.with(|o| *o.borrow_mut() = None);
        true
    })
}

/// A key went down while playing (`key.keyboard.j`): Java hears of it with the next tick.
pub fn key(name: &str) {
    STATE.with(|s| {
        if let State::Running(r) = &mut *s.borrow_mut() {
            r.keys.push(name.to_owned());
        }
    });
}

/// The window's thread: a key went down or up (`key.keyboard.space`), for `KeyMapping.isDown`.
pub fn key_held(name: &str, down: bool) {
    HELD.with(|h| {
        let mut h = h.borrow_mut();
        if down {
            h.insert(name.to_owned());
        } else {
            h.remove(name);
        }
    });
}

/// The keys held down now, by name, sorted.
pub fn held_keys() -> Vec<String> {
    HELD.with(|h| h.borrow().iter().cloned().collect())
}

/// The window's thread: the client commands the mods registered, by name (the chat box completes them). Each
/// name is kept for the game's life, once: the game's tree holds names that live as long as it.
pub fn client_commands() -> Vec<&'static str> {
    static KEPT: Mutex<BTreeSet<&'static str>> = Mutex::new(BTreeSet::new());
    let mut kept = KEPT.lock().unwrap_or_else(|e| e.into_inner());
    CLIENT_COMMANDS.with(|c| {
        c.borrow()
            .iter()
            .map(|name| match kept.get(name.as_str()) {
                Some(kept) => *kept,
                None => {
                    let leaked: &'static str = Box::leak(name.clone().into_boxed_str());
                    kept.insert(leaked);
                    leaked
                }
            })
            .collect()
    })
}

/// The window's thread: the player typed (or clicked) `command`, without its slash. When its first word is one of the
/// mods' client commands, Java runs it with the next tick and the server never sees it: true. As Fabric's client
/// commands take the line before the server.
pub fn client_command(command: &str) -> bool {
    let name = command.split(' ').next().unwrap_or("");
    if !CLIENT_COMMANDS.with(|c| c.borrow().iter().any(|n| n == name)) {
        return false;
    }
    STATE.with(|s| match &mut *s.borrow_mut() {
        State::Running(r) => {
            r.commands.push(command.to_owned());
            true
        }
        _ => false,
    })
}

/// The window's thread: the commands the mods sent as the player since the last call (attended or not, without
/// their slash), for the game to send.
pub fn take_sent() -> Vec<(bool, String)> {
    SENT.with(|c| std::mem::take(&mut *c.borrow_mut()))
}

/// The window's thread: the text a mod copied since the last call, for the game's clipboard (D-174).
pub fn take_copied() -> Option<String> {
    COPIED.with(|c| c.borrow_mut().take())
}

/// The window's thread: the chat messages the mods sent as the player since the last call, for the game to send as
/// typed in the chat.
pub fn take_sent_chat() -> Vec<String> {
    SENT_CHAT.with(|c| std::mem::take(&mut *c.borrow_mut()))
}

/// The window's thread: the slots of the open menu the mods threw the stack out of since the last call.
pub fn take_thrown() -> Vec<(usize, i32)> {
    THROWN.with(|c| std::mem::take(&mut *c.borrow_mut()))
}

/// The window's thread: the sounds, particles and toasts the mods played since the last call, for the game to play.
pub fn take_played() -> Vec<Play> {
    PLAYED.with(|p| std::mem::take(&mut *p.borrow_mut()))
}

/// The window's thread: the mods' chat lines since the last call, as vanilla's JSON with their style (a colour,
/// bold, italic), for the game's chat. Kiln's own lines are plain text, which `tick` and `frame` return.
/// The window's thread: Java's `back` values. The view's field of view is kept for each frame; the rest wait for the
/// window's next tick (`take_back`).
fn back(values: Vec<(String, String)>) {
    BACKS_RECEIVED.set(BACKS_RECEIVED.get() + 1);
    for (key, value) in values {
        if key == "view_fov" {
            VIEW_FOV.set(
                value
                    .parse::<f32>()
                    .ok()
                    .filter(|v| v.is_finite() && *v > 0.0),
            );
        } else if key == "view_gamma" {
            let now = value
                .parse::<f32>()
                .ok()
                .filter(|v| v.is_finite() && *v >= 0.0);
            if now.is_none() && VIEW_GAMMA.get().is_some() {
                VIEW_GAMMA_ENDED.set(true);
            }
            VIEW_GAMMA.set(now);
        } else if key == "hud_hidden" {
            HUD_HIDDEN.with(|h| *h.borrow_mut() = hud_layers(&value));
        } else if key == "tooltip" {
            tooltip_answer(&value);
        } else {
            BACK.with(|b| b.borrow_mut().push((key, value)));
        }
    }
}

/// The window's thread: what the mods changed of the game's state Horizon owns since the last tick, by key (`fov`,
/// `render_distance`, `show_subtitles`, `flying_speed`: kiln/java/kiln/Back.java), in order.
pub fn take_back() -> Vec<(String, String)> {
    BACKS_TAKEN.set(BACKS_RECEIVED.get());
    BACK.with(|b| std::mem::take(&mut *b.borrow_mut()))
}

/// A tooltip Java made for a stack (D-145).
#[derive(Clone, Debug, Default)]
pub struct Tooltip {
    /// Its first line when the mods' code changed the stack's name.
    pub name: Option<serde_json::Value>,
    /// The lines past the name the mods' code gave it, as vanilla's JSON.
    pub lines: Vec<serde_json::Value>,
    /// Whether `lines` stand for Horizon's past the name: the mods' code changed or removed one of the game's (D-218).
    pub whole: bool,
    /// Whether Java answered.
    answered: bool,
    /// When the window last asked Java for it.
    asked: Option<std::time::Instant>,
}

/// The most tooltips kept; past it, they are all asked again.
const TOOLTIPS_MAX: usize = 512;
/// How long a tooltip's lines stand before the window asks again while it shows (a line that changes with Shift held).
const TOOLTIP_AGAIN: Duration = Duration::from_millis(250);

/// Java's `tooltip` answer: the stack's key (after a `!` when its lines stand for Horizon's, D-218), its name or
/// `null`, then its lines, separated by U+001F.
fn tooltip_answer(value: &str) {
    let mut parts = value.split('\u{1f}');
    let Some(key) = parts.next() else {
        return;
    };
    let (whole, key) = match key.strip_prefix('!') {
        Some(key) => (true, key),
        None => (false, key),
    };
    let json = |p: &str| {
        serde_json::from_str::<serde_json::Value>(p)
            .ok()
            .filter(|v| !v.is_null())
    };
    let name = parts.next().and_then(json);
    let lines = parts.filter_map(json).collect();
    TOOLTIPS.with(|t| {
        let mut t = t.borrow_mut();
        let entry = t.entry(key.to_owned()).or_default();
        entry.name = name;
        entry.lines = lines;
        entry.whole = whole;
        entry.answered = true;
    });
}

/// The window's thread: the tooltip Java made for the stack `key` (its SNBT), as of its last answer; and whether to
/// ask Java for it (again): the first time it shows, then every quarter of a second while it does.
pub fn tooltip(key: &str) -> (Option<Tooltip>, bool) {
    TOOLTIPS.with(|t| {
        let mut t = t.borrow_mut();
        if t.len() >= TOOLTIPS_MAX && !t.contains_key(key) {
            t.clear();
        }
        let entry = t.entry(key.to_owned()).or_default();
        let now = std::time::Instant::now();
        let ask = entry
            .asked
            .is_none_or(|at| now.duration_since(at) >= TOOLTIP_AGAIN);
        if ask {
            entry.asked = Some(now);
        }
        (entry.answered.then(|| entry.clone()), ask)
    })
}

/// The layers a `hud_hidden` value names: comma-separated, `-` for none.
fn hud_layers(value: &str) -> Vec<String> {
    value
        .split(',')
        .map(str::trim)
        .filter(|l| !l.is_empty() && *l != "-")
        .map(str::to_owned)
        .collect()
}

/// The window's thread: whether Horizon hides vanilla's HUD layer `layer` (`minecraft:crosshair`), as the mods'
/// mixins or HUD elements kept it from drawing in Java's last HUD (D-145).
pub fn hud_hidden(layer: &str) -> bool {
    HUD_HIDDEN.with(|h| {
        let h = h.borrow();
        !h.is_empty() && h.iter().any(|l| l == layer)
    })
}

/// The window's thread: the view's field of view over `option` degrees, as the mods' mixins in `Camera.calculateFov`
/// make it (`degrees` scaled by it), or `degrees` as it was when no mixin is there.
pub fn view_fov(degrees: f32, option: f32) -> f32 {
    match VIEW_FOV.get() {
        Some(fov) if option > 0.0 => degrees * fov / option,
        _ => degrees,
    }
}

/// The window's thread: the brightness the mods' mixins make the gamma option give, for the renderer over the option
/// (D-192); `Err(())` once when one went, for the renderer to take the option's again; `Ok(None)` otherwise.
pub fn view_gamma() -> Result<Option<f32>, ()> {
    match VIEW_GAMMA.get() {
        Some(gamma) => Ok(Some(gamma)),
        None if VIEW_GAMMA_ENDED.replace(false) => Err(()),
        None => Ok(None),
    }
}

pub fn take_chat_components() -> Vec<serde_json::Value> {
    CHAT_COMPONENTS.with(|c| std::mem::take(&mut *c.borrow_mut()))
}

/// The window's GUI size, as Horizon last drew it.
pub fn gui_size() -> (u32, u32) {
    GUI.get()
}

/// The HUD's size in GUI pixels, as the game draws it.
pub fn set_gui(width: f32, height: f32) {
    GUI.set((width.max(0.0) as u32, height.max(0.0) as u32));
}

// --- D-112's crash policy: the crash window started with Java, and the next start's question -------------------------
//
// Kiln catches what it can in Java (a mod's exception, its run out of Java's heap or stack: the mod is turned off and
// the game goes on, kiln.SoftSwitch.trip). What it can't is a crash of the game's process itself (a native fault, Java's
// own included, with Java in the game's process) or a kill: the process is gone, Kiln with it. So with Java, the game
// starts a small Java process of its own, `kiln.CrashWatch`, which waits for the game's process to end. The game notes
// `running` in Kiln's folder as Java starts and removes it as the process exits cleanly; still there after, the game
// stopped without closing, and the watcher shows a window naming the mod and call D-112's breadcrumb (`last-call`)
// names, asking whether to start again without it. An answer there clears its note (`crashed`); otherwise the next
// start's title screen asks.

/// Whether the game starts the crash window with its Java ([`set_crash_watch`]): the window's game does; Kiln's own
/// tests, which start Java many times in a process, don't.
static CRASH_WATCH: std::sync::atomic::AtomicBool = std::sync::atomic::AtomicBool::new(false);
/// Whether this process started its crash window.
static WATCHING: std::sync::atomic::AtomicBool = std::sync::atomic::AtomicBool::new(false);
/// The mods Java turned off because they ran it out of its heap or stack (`trip`), for their switches in Options > Mods.
static TRIPPED: Mutex<Vec<String>> = Mutex::new(Vec::new());

/// The window's game: start the crash window with Kiln's Java.
pub fn set_crash_watch(on: bool) {
    CRASH_WATCH.store(on, Ordering::Relaxed);
}

/// The mods Java turned off since the last call because they ran it out of its heap or stack: the game turns their
/// switches in Options > Mods off too, so they stay off from the next start until the player turns them back on.
pub fn take_tripped() -> Vec<String> {
    std::mem::take(&mut *TRIPPED.lock().unwrap_or_else(|e| e.into_inner()))
}

/// The text of `running`: the game's folder, then its program and its arguments, one a line (a line break in one
/// becomes a space), for the crash window to start the game again as it was.
pub fn running_text(folder: &Path, program: &Path, args: &[std::ffi::OsString]) -> String {
    let line = |s: &std::ffi::OsStr| s.to_string_lossy().replace(['\n', '\r'], " ");
    let mut text = String::new();
    for part in [folder.as_os_str(), program.as_os_str()]
        .into_iter()
        .chain(args.iter().map(|a| a.as_os_str()))
    {
        text.push_str(&line(part));
        text.push('\n');
    }
    text
}

/// The crash window's command: Java from `home` (`javaw` on Windows, which opens no console), a small heap, Kiln's
/// runtime alone, `kiln.CrashWatch <the game's pid> <Kiln's folder>`.
pub fn watcher_command(home: &Path, dir: &Path, pid: u32) -> std::process::Command {
    let java = if cfg!(windows) {
        home.join("bin").join("javaw.exe")
    } else {
        java_program(home)
    };
    let mut command = std::process::Command::new(java);
    command.args([
        "-Xmx64m",
        "-XX:+UseSerialGC",
        "-XX:TieredStopAtLevel=1",
        "-Xss512k",
    ]);
    if cfg!(target_os = "macos") {
        command.args(["-Xdock:name=Kiln", "-Dapple.awt.application.name=Kiln"]);
    }
    command
        .arg("-cp")
        .arg(dir.join("kiln-runtime.jar"))
        .arg("kiln.CrashWatch")
        .arg(pid.to_string())
        .arg(dir);
    command
}

/// Starts the crash window for this game, once a process: `running` written (removed when the process exits cleanly),
/// then `kiln.CrashWatch` on its own, in a process group of its own (Ctrl+C in the game's terminal doesn't stop it; on
/// Windows, out of the launcher's job when it may). Its output goes to `crash-watch.log`.
fn start_crash_watch(home: &Path, dir: &Path) -> Result<(), String> {
    if WATCHING.swap(true, Ordering::SeqCst) {
        return Ok(());
    }
    let dir = std::path::absolute(dir).unwrap_or_else(|_| dir.to_path_buf());
    // The last game's note, read before this game's takes its place.
    last_crash_once(&dir);
    let folder = std::env::current_dir().map_err(|e| format!("no current folder: {e}"))?;
    let program = std::env::current_exe().map_err(|e| format!("no program path: {e}"))?;
    let args: Vec<std::ffi::OsString> = std::env::args_os().skip(1).collect();
    let running = dir.join("running");
    std::fs::write(&running, running_text(&folder, &program, &args))
        .map_err(|e| format!("{}: {e}", running.display()))?;
    kiln_jni::remove_at_exit(running);
    let log_file = dir.join("crash-watch.log");
    let log =
        std::fs::File::create(&log_file).map_err(|e| format!("{}: {e}", log_file.display()))?;
    let err = log.try_clone().map_err(|e| e.to_string())?;
    let mut command = watcher_command(home, &dir, std::process::id());
    command
        .stdin(std::process::Stdio::null())
        .stdout(log)
        .stderr(err);
    #[cfg(unix)]
    {
        use std::os::unix::process::CommandExt;
        command.process_group(0);
    }
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        // DETACHED_PROCESS | CREATE_NEW_PROCESS_GROUP, and CREATE_BREAKAWAY_FROM_JOB where the job allows it.
        command.creation_flags(0x0000_0008 | 0x0000_0200 | 0x0100_0000);
        if command.spawn().is_ok() {
            return Ok(());
        }
        command.creation_flags(0x0000_0008 | 0x0000_0200);
    }
    command
        .spawn()
        .map(|_| ())
        .map_err(|e| format!("can't run Java's crash window: {e}"))
}

/// A Java mod that stopped the last game (D-112), or was stuck when it closed: for the title screen's question.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct LastCrash {
    pub mod_id: String,
    /// Its name, from its jar; its id when no jar in `kiln/mods` names it.
    pub name: String,
    /// Kiln's call it was in ("" when not known).
    pub call: String,
    /// Stuck (the watchdog's note: the game was closed while the mod hung), not stopped.
    pub stuck: bool,
    /// Turned off by Java in the last game for running it out of what it can give (`SoftSwitch.trip`): "memory" or
    /// "stack"; "" for a stop or a hang. Its switch is off already, so the question's second answer turns it back on.
    pub tripped: String,
}

/// The `crashed` note (`mod:`, `call:`, `why:` lines, kiln/java/kiln/CrashWatch.java): the mod, its call, whether it
/// was stuck. A note with no mod but Kiln's call (the crash window's for a game that died in no mod's call) is asked
/// about too, with safe mode (D-134). None with neither.
pub fn parse_crash_note(text: &str) -> Option<LastCrash> {
    let mut crash = LastCrash {
        mod_id: String::new(),
        name: String::new(),
        call: String::new(),
        stuck: false,
        tripped: String::new(),
    };
    for line in text.lines() {
        let Some((key, value)) = line.split_once(':') else {
            continue;
        };
        let value = value.trim();
        let value = if value == "-" { "" } else { value };
        match key.trim() {
            "mod" => crash.mod_id = value.to_owned(),
            "call" => crash.call = value.to_owned(),
            "why" => {
                crash.stuck = value == "stuck";
                crash.tripped = if matches!(value, "memory" | "stack") {
                    value.to_owned()
                } else {
                    String::new()
                };
            }
            _ => {}
        }
    }
    (!crash.mod_id.is_empty() || !crash.call.is_empty()).then_some(crash)
}

/// D-112's breadcrumb (`last-call`, kiln/java/kiln/Breadcrumb.java): the mod and call of the record in a call
/// (`busy: 1`) that names a mod, else of one that names only Kiln's call; "" for none.
pub fn parse_last_call(text: &str) -> (String, String) {
    let mut best: Option<(String, String)> = None;
    let mut record: Option<(bool, String, String)> = None;
    let done = |record: Option<(bool, String, String)>, best: &mut Option<(String, String)>| {
        if let Some((true, call, m)) = record {
            let better = match best {
                None => true,
                Some((known, _)) => known.is_empty() && !m.is_empty(),
            };
            if better {
                *best = Some((m, call));
            }
        }
    };
    for line in text.lines() {
        let Some((key, value)) = line.split_once(':') else {
            continue;
        };
        let value = value.trim();
        let value = if value == "-" { "" } else { value }.to_owned();
        match key.trim() {
            "busy" => {
                done(record.take(), &mut best);
                record = Some((value == "1", String::new(), String::new()));
            }
            "call" => {
                if let Some(r) = &mut record {
                    r.1 = value;
                }
            }
            "mod" => {
                if let Some(r) = &mut record {
                    r.2 = value;
                }
            }
            _ => {}
        }
    }
    done(record, &mut best);
    best.unwrap_or_default()
}

/// The last game's stop, from Kiln's folder `dir`: the `crashed` note (the crash window's, unanswered, or the
/// watchdog's), else a `running` note left behind (the game stopped without closing and no crash window took it) read
/// with the breadcrumb. Both notes go: the question is asked once.
pub fn take_last_crash(dir: &Path) -> Option<LastCrash> {
    let note = dir.join("crashed");
    let running = dir.join("running");
    let mut crash = std::fs::read_to_string(&note)
        .ok()
        .and_then(|t| parse_crash_note(&t));
    if crash.is_none() && running.is_file() {
        let last = std::fs::read(dir.join(crate::game::kiln_jvm::LAST_CALL)).unwrap_or_default();
        let (mod_id, call) = parse_last_call(&String::from_utf8_lossy(&last));
        // No crash window wrote the Crashes tab's line (D-134): the game does.
        if !mod_id.is_empty() || !call.is_empty() {
            record_crash(dir, "stopped", &mod_id, &call, "");
        }
        crash = (!mod_id.is_empty() || !call.is_empty()).then_some(LastCrash {
            mod_id,
            name: String::new(),
            call,
            stuck: false,
            tripped: String::new(),
        });
    }
    let _ = std::fs::remove_file(&note);
    let _ = std::fs::remove_file(&running);
    let mut crash = crash?;
    if crash.mod_id.is_empty() {
        return Some(crash);
    }
    crash.name = jars(&dir.join("mods"))
        .iter()
        .filter_map(|jar| read_jar(jar).ok())
        .find(|m| m.id == crash.mod_id)
        .map_or_else(|| crash.mod_id.clone(), |m| m.name);
    Some(crash)
}

/// The last game's stop, read once a process ([`take_last_crash`]), before this game's Java notes its own.
fn last_crash_once(dir: &Path) {
    static READ: std::sync::atomic::AtomicBool = std::sync::atomic::AtomicBool::new(false);
    if READ.swap(true, Ordering::SeqCst) {
        return;
    }
    take_safe_mode_file(dir);
    let crash = take_last_crash(dir);
    if let Some(crash) = crash.as_ref().filter(|c| c.mod_id.is_empty()) {
        log::warn!(
            "Kiln: the last game stopped in Kiln's call {}, in no Java mod's",
            crash.call
        );
    } else if let Some(crash) = &crash {
        log::warn!(
            "Kiln: the Java mod {} {} the last game{}",
            crash.mod_id,
            if crash.stuck {
                "was stuck when it closed"
            } else if crash.tripped == "memory" {
                "was turned off for running Java out of memory in"
            } else if crash.tripped == "stack" {
                "was turned off for overflowing Java's stack in"
            } else {
                "stopped"
            },
            if crash.call.is_empty() {
                String::new()
            } else {
                format!(" (in {})", crash.call)
            }
        );
    }
    *LAST_STOP.lock().unwrap_or_else(|e| e.into_inner()) = crash.as_ref().map(|c| {
        let how = if c.stuck {
            "stuck"
        } else if c.tripped.is_empty() {
            "stopped"
        } else {
            c.tripped.as_str()
        };
        (c.mod_id.clone(), how.to_owned())
    });
    *QUESTION.lock().unwrap_or_else(|e| e.into_inner()) = crash;
}

/// Safe mode, chosen in the last game's crash window (D-134): its file in Kiln's folder `dir` goes, and this game
/// starts no Java mods.
fn take_safe_mode_file(dir: &Path) {
    if std::fs::remove_file(dir.join(SAFE_MODE_FILE)).is_ok() {
        log::warn!(
            "Kiln: safe mode, chosen after the last game's crash: no Java mods in this game"
        );
        set_safe_mode();
    }
}

/// The title screen's question, until the player answers it or Java starts.
static QUESTION: Mutex<Option<LastCrash>> = Mutex::new(None);
/// The player's answer: the mod (empty for a crash that named none), and what they chose.
static ANSWER: Mutex<Option<(String, CrashChoice)>> = Mutex::new(None);

/// An answer to the title screen's question about the last game's stop.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum CrashChoice {
    /// Start without the mod: its switch goes off, until the player turns it back on.
    Without,
    /// Keep it on (a stop or a hang), or turn it back on (a mod Java turned off); for a crash that named no mod, start
    /// as usual.
    Keep,
    /// Safe mode (D-134): this game starts no Java mods; every switch stays as it is.
    Safe,
}

/// The file in Kiln's folder that starts the next game in safe mode: the crash window's "Start in safe mode" writes it
/// (kiln/java/kiln/CrashWatch.java `SAFE_MODE`), the game's start reads and removes it.
pub const SAFE_MODE_FILE: &str = "safe-mode";

/// This game starts in safe mode: Java doesn't start, so no Java mod runs; their switches stay as they are.
pub fn set_safe_mode() {
    SAFE_MODE.set(true);
}

/// Whether this game is in safe mode.
pub fn safe_mode() -> bool {
    SAFE_MODE.get()
}

/// The Crashes tab's line for a crash (kiln/java/kiln/Crashes.java has the format): now, `why`, the mod, Kiln's call,
/// what Java said; the list keeps its last [`CRASHES_KEPT`]. A list that can't be written only loses the line.
pub fn record_crash(dir: &Path, why: &str, mod_id: &str, call: &str, detail: &str) {
    let field = |v: &str| {
        let v = v.replace(['\t', '\n', '\r'], " ");
        let v = v.trim();
        if v.is_empty() {
            "-".to_owned()
        } else {
            v.to_owned()
        }
    };
    let when = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map_or(0, |d| d.as_millis());
    let file = dir.join(CRASHES_FILE);
    let text = std::fs::read_to_string(&file).unwrap_or_default();
    let mut lines: Vec<String> = text
        .lines()
        .filter(|l| !l.trim().is_empty())
        .map(str::to_owned)
        .collect();
    let when_of = |line: &str| line.split('\t').next().and_then(|w| w.parse::<u128>().ok());
    // As Crashes.java: a millisecond later than the last, so each crash's log tail has a file of its own.
    let when = lines
        .iter()
        .filter_map(|l| when_of(l))
        .map(|w| w + 1)
        .fold(when, u128::max);
    // The Java log's last lines as they are now, with the crash (D-191): the log of the Java that died with the game.
    let tail = log_tail(&dir.join(crate::game::kiln_jvm::JAVA_LOG), CRASH_TAIL);
    if !tail.is_empty() {
        let logs = dir.join(CRASH_LOGS);
        let kept = std::fs::create_dir_all(&logs)
            .and_then(|()| std::fs::write(logs.join(format!("{when}.log")), tail));
        if let Err(e) = kept {
            log::warn!("Kiln: the crash's log tail wasn't kept: {e}");
        }
    }
    lines.push(format!(
        "{when}\t{}\t{}\t{}\t{}",
        field(why),
        field(mod_id),
        field(call),
        field(detail)
    ));
    let gone = lines.len().saturating_sub(CRASHES_KEPT);
    for line in &lines[..gone] {
        if let Some(w) = when_of(line) {
            let _ = std::fs::remove_file(dir.join(CRASH_LOGS).join(format!("{w}.log")));
        }
    }
    let kept = &lines[gone..];
    let part = dir.join(format!("{CRASHES_FILE}.part"));
    let written =
        std::fs::write(&part, kept.join("\n") + "\n").and_then(|()| std::fs::rename(&part, &file));
    if let Err(e) = written {
        log::warn!(
            "Kiln: the Crashes tab's list ({}) didn't take a crash: {e}",
            file.display()
        );
    }
}

/// The Crashes tab's list in Kiln's folder, and how many crashes it keeps (as kiln/java/kiln/Crashes.java).
pub const CRASHES_FILE: &str = "crashes";
pub const CRASHES_KEPT: usize = 50;
/// The folder of the crashes' log tails, and how many of the log's last lines each keeps (as Crashes.java).
pub const CRASH_LOGS: &str = "crash-logs";
pub const CRASH_TAIL: usize = 100;

/// The last `n` lines of the file `log`, read from its last 64 KiB, each with its line end; empty when there is none.
pub fn log_tail(log: &Path, n: usize) -> String {
    use std::io::{Read, Seek, SeekFrom};
    let Ok(mut file) = std::fs::File::open(log) else {
        return String::new();
    };
    let length = file.metadata().map_or(0, |m| m.len());
    let start = length.saturating_sub(64 << 10);
    let mut bytes = Vec::new();
    if file
        .seek(SeekFrom::Start(start))
        .and_then(|_| file.read_to_end(&mut bytes))
        .is_err()
    {
        return String::new();
    }
    let text = String::from_utf8_lossy(&bytes);
    let mut lines: Vec<&str> = text.lines().collect();
    if start > 0 && !lines.is_empty() {
        // The first line read is most likely a part of one.
        lines.remove(0);
    }
    lines[lines.len().saturating_sub(n)..]
        .iter()
        .map(|l| format!("{l}\n"))
        .collect()
}
/// The mods the player chose to turn back on, answering the question about one Java turned off (`LastCrash::tripped`).
static TURN_ON: Mutex<Vec<String>> = Mutex::new(Vec::new());
/// When the question was last drawn: a click is the question's only while it shows.
static QUESTION_DRAWN: Mutex<Option<Instant>> = Mutex::new(None);

/// The window's game on the title screen: the last game's stop, read once, from Kiln's folder `dir`.
pub fn load_crash_question(dir: &Path) {
    last_crash_once(dir);
}

/// The question the title screen asks now, if any.
pub fn crash_question() -> Option<LastCrash> {
    QUESTION.lock().unwrap_or_else(|e| e.into_inner()).clone()
}

/// The player's answer to the question, once: the mod (empty when the crash named none) and their choice.
pub fn take_crash_answer() -> Option<(String, CrashChoice)> {
    ANSWER.lock().unwrap_or_else(|e| e.into_inner()).take()
}

/// The mods the player turned back on at the title screen's question since the last call: the game turns their
/// switches in Options > Mods on, and in `horizon-mods.txt` ([`turn_on_in_list`]).
pub fn take_turn_on() -> Vec<String> {
    std::mem::take(&mut *TURN_ON.lock().unwrap_or_else(|e| e.into_inner()))
}

/// The question as the title screen draws it, in GUI pixels: a dark panel in the middle, its lines, and two buttons.
#[derive(Clone, Debug, PartialEq)]
pub struct CrashDialog {
    pub panel: [f32; 4],
    pub lines: Vec<(String, [f32; 2], u32)>,
    /// Each button's label, rectangle, and the answer it gives.
    pub buttons: Vec<(String, [f32; 4], CrashChoice)>,
}

/// The dialog for `crash` on a screen of `size` GUI pixels; `width` measures a text in the game's font.
pub fn crash_dialog(crash: &LastCrash, size: [f32; 2], width: impl Fn(&str) -> f32) -> CrashDialog {
    let (texts, labels) = if crash.mod_id.is_empty() {
        no_mod_question(crash)
    } else {
        mod_question(crash)
    };
    layout_dialog(texts, labels, size, width)
}

/// The question's lines and buttons for a crash that named a mod: without it, keep it, or safe mode (D-134).
fn mod_question(crash: &LastCrash) -> (Vec<(String, u32)>, Vec<(String, CrashChoice)>) {
    let what = if crash.stuck {
        format!("{} was stuck when the last game closed", crash.name)
    } else if crash.tripped == "memory" {
        format!("{} ran Java out of memory in the last game", crash.name)
    } else if crash.tripped == "stack" {
        format!("{} overflowed Java's stack in the last game", crash.name)
    } else {
        format!("{} stopped the last game", crash.name)
    };
    let mut texts = vec![(what, INK)];
    if !crash.call.is_empty() {
        texts.push((format!("(in {})", crash.call), 0xA0_A0A0));
    }
    texts.push((format!("Start the game without {}?", crash.name), INK));
    texts.push((
        "It stays off until you turn it back on in Options > Mods.".to_owned(),
        0xA0_A0A0,
    ));
    texts.push((
        "Safe mode: no Java mods in this game only.".to_owned(),
        0xA0_A0A0,
    ));
    let keep = if crash.tripped.is_empty() {
        "Keep it on"
    } else {
        "Turn it back on"
    };
    let labels = vec![
        (
            format!("Start without {}", crash.name),
            CrashChoice::Without,
        ),
        (keep.to_owned(), CrashChoice::Keep),
        ("Safe mode".to_owned(), CrashChoice::Safe),
    ];
    (texts, labels)
}

/// The question for a crash that named no Java mod, only Kiln's call: safe mode, or start as usual (D-134).
fn no_mod_question(crash: &LastCrash) -> (Vec<(String, u32)>, Vec<(String, CrashChoice)>) {
    let texts = vec![
        (
            format!("The last game stopped in Kiln's call {}", crash.call),
            INK,
        ),
        (
            "No Java mod was running then, so Kiln can't say which one it was.".to_owned(),
            0xA0_A0A0,
        ),
        (
            "Start this game in safe mode, without Java mods?".to_owned(),
            INK,
        ),
        (
            "Their switches stay as they are; the next game has them again.".to_owned(),
            0xA0_A0A0,
        ),
    ];
    let labels = vec![
        ("Safe mode".to_owned(), CrashChoice::Safe),
        ("Start as usual".to_owned(), CrashChoice::Keep),
    ];
    (texts, labels)
}

/// The panel in the middle of the screen with `texts` and a row of `labels`' buttons under them.
fn layout_dialog(
    texts: Vec<(String, u32)>,
    labels: Vec<(String, CrashChoice)>,
    size: [f32; 2],
    width: impl Fn(&str) -> f32,
) -> CrashDialog {
    let n = labels.len() as f32;
    let gap = 8.0;
    // As wide as the widest label, or narrower when the row wouldn't fit the screen.
    let widest = labels
        .iter()
        .map(|(l, _)| width(l))
        .fold(60.0_f32, f32::max)
        + 16.0;
    let button_w = widest.min((size[0] - 8.0 - 24.0 - gap * (n - 1.0)) / n);
    let row_w = n * button_w + gap * (n - 1.0);
    let text_w = texts.iter().map(|(t, _)| width(t)).fold(0.0_f32, f32::max);
    let panel_w = (text_w + 24.0).max(row_w + 24.0).min(size[0] - 8.0);
    let panel_h = 12.0 + 12.0 * texts.len() as f32 + 8.0 + 20.0 + 10.0;
    let x = ((size[0] - panel_w) / 2.0).max(4.0);
    let y = ((size[1] - panel_h) / 2.0).max(4.0);
    let lines = texts
        .into_iter()
        .enumerate()
        .map(|(i, (text, rgb))| {
            let left = x + (panel_w - width(&text)).max(0.0) / 2.0;
            (text, [left, y + 12.0 + 12.0 * i as f32], rgb)
        })
        .collect::<Vec<_>>();
    let buttons_y = y + 12.0 + 12.0 * lines.len() as f32 + 8.0;
    let left = x + (panel_w - row_w) / 2.0;
    let buttons = labels
        .into_iter()
        .enumerate()
        .map(|(i, (label, choice))| {
            let bx = left + i as f32 * (button_w + gap);
            (label, [bx, buttons_y, button_w, 20.0], choice)
        })
        .collect();
    CrashDialog {
        panel: [x, y, panel_w, panel_h],
        lines,
        buttons,
    }
}

/// The title screen drew `dialog` now; the first time, Kiln's log says where its buttons are (in GUI pixels).
pub fn crash_question_drawn(dialog: &CrashDialog) {
    let first = QUESTION_DRAWN
        .lock()
        .unwrap_or_else(|e| e.into_inner())
        .replace(Instant::now())
        .is_none();
    if first {
        for (label, [x, y, w, h], _) in &dialog.buttons {
            log::info!(
                "Kiln: the title screen asks about the last game's stop: [{label}] at {x} {y}, {w}x{h}"
            );
        }
    }
}

/// A click on the title screen at `at` while `dialog` shows: a button's answer is kept for the game
/// ([`take_crash_answer`]) and the question goes. True when the click is the question's (anywhere, while it showed in
/// the last half second): the screen under it doesn't take it.
pub fn crash_click(dialog: &CrashDialog, at: [f32; 2]) -> bool {
    let shown = QUESTION_DRAWN
        .lock()
        .unwrap_or_else(|e| e.into_inner())
        .is_some_and(|t| t.elapsed() < Duration::from_millis(500));
    let mut question = QUESTION.lock().unwrap_or_else(|e| e.into_inner());
    let Some(crash) = question.as_ref() else {
        return false;
    };
    if !shown {
        return false;
    }
    let inside =
        |r: [f32; 4]| at[0] >= r[0] && at[0] < r[0] + r[2] && at[1] >= r[1] && at[1] < r[1] + r[3];
    if let Some((_, _, choice)) = dialog.buttons.iter().find(|(_, r, _)| inside(*r)) {
        match choice {
            CrashChoice::Without => {
                log::info!("Kiln: the player starts the game without {}", crash.mod_id)
            }
            CrashChoice::Keep => {
                log::info!("Kiln: the player starts the game with {}", crash.mod_id)
            }
            CrashChoice::Safe => {
                log::info!(
                    "Kiln: the player starts the game in safe mode: no Java mods in this game"
                );
                set_safe_mode();
            }
        }
        if *choice == CrashChoice::Keep && !crash.tripped.is_empty() && !crash.mod_id.is_empty() {
            TURN_ON
                .lock()
                .unwrap_or_else(|e| e.into_inner())
                .push(crash.mod_id.clone());
        }
        *ANSWER.lock().unwrap_or_else(|e| e.into_inner()) = Some((crash.mod_id.clone(), *choice));
        *question = None;
    }
    true
}

/// Mod `mod_id`'s switch in Options > Mods turned off in the game folder `game`'s `horizon-mods.txt`
/// (`mod.kiln_<id>:false`), as the crash window does, so it holds from the next start whatever the game saves.
pub fn turn_off_in_list(game: &Path, mod_id: &str) -> std::io::Result<()> {
    let list = game.join("horizon-mods.txt");
    let text = std::fs::read_to_string(&list).unwrap_or_default();
    let key = format!("mod.{}", row_id(mod_id));
    let mut lines: Vec<&str> = text
        .lines()
        .filter(|l| l.split_once(':').is_none_or(|(k, _)| k != key))
        .collect();
    let off = format!("{key}:false");
    lines.push(&off);
    std::fs::write(&list, lines.join("\n") + "\n")
}

/// Mod `mod_id`'s switch in the game folder `game`'s `horizon-mods.txt` turned back on ([`turn_off_in_list`]'s
/// opposite), for a mod Java turned off that the player chose to keep.
pub fn turn_on_in_list(game: &Path, mod_id: &str) -> std::io::Result<()> {
    let list = game.join("horizon-mods.txt");
    let text = std::fs::read_to_string(&list).unwrap_or_default();
    let key = format!("mod.{}", row_id(mod_id));
    let mut lines: Vec<&str> = text
        .lines()
        .filter(|l| l.split_once(':').is_none_or(|(k, _)| k != key))
        .collect();
    let on = format!("{key}:true");
    lines.push(&on);
    std::fs::write(&list, lines.join("\n") + "\n")
}

/// Starts Java with Kiln's runtime and the mods in `dir`/mods, and asks it to run their client entrypoints.
/// `java_home` is the `java_home` setting, which the file `java_home_file` holds: chat names it when no Java is found.
fn start(
    dir: &Path,
    java_home: Option<String>,
    java_home_file: &Path,
    font: impl FnOnce() -> Vec<u32>,
) -> (State, Vec<String>) {
    let stop = |why: &str, detail: String| {
        log::warn!("Kiln: {detail}");
        (
            State::Stopped(why.to_owned()),
            vec![format!("Kiln: {detail}")],
        )
    };
    // Safe mode, the player's choice after a crash (D-134): no Java at all in this game.
    if safe_mode() {
        log::info!("Kiln: safe mode: no Java mods in this game");
        return (
            State::Stopped("safe mode, no Java mods in this game".into()),
            vec!["Kiln: safe mode: no Java mods in this game. The next start has them again, their switches as they were."
                .to_owned()],
        );
    }
    let files = mod_jars(dir);
    STARTED_OFF
        .with(|s| *s.borrow_mut() = Some(OFF.lock().unwrap_or_else(|e| e.into_inner()).clone()));
    if files.is_empty() && !jars(&dir.join("mods")).is_empty() {
        log::info!(
            "Kiln: every Java mod in {} is turned off",
            dir.join("mods").display()
        );
        return (State::Stopped("no Java mods".into()), Vec::new());
    }
    if files.is_empty() {
        // The folder is made for the player, and named once in chat: it is where Java mods go.
        let _ = std::fs::create_dir_all(dir.join("mods"));
        log::info!("Kiln: no Java mods in {}", dir.join("mods").display());
        let hint = format!(
            "Kiln: no Java mods yet. Put .jar mods in {} and kiln-runtime.jar in {}",
            dir.join("mods").display(),
            dir.display()
        );
        return (State::Stopped("no Java mods".into()), vec![hint]);
    }
    let runtime = dir.join("kiln-runtime.jar");
    if !runtime.is_file() {
        return stop(
            "kiln-runtime.jar missing",
            format!("{} is missing", runtime.display()),
        );
    }
    let files = with_nested(&files, dir);
    let mut chat = Vec::new();
    let mut mods: Vec<JavaMod> = Vec::new();
    for file in &files {
        match read_jar(file) {
            // The same mod twice (a library a jar carries, and on its own too): the first.
            Ok(m) if mods.iter().any(|known| known.id == m.id) => {}
            // A data-only mod (structures, loot): structures.rs hands its data to the game; Java has nothing to run.
            // One with mixins and no entrypoint runs in Java all the same: its mixins change the game's classes there.
            Ok(m) if m.entrypoints() == 0 && !m.mixins => {
                log::info!("Kiln: {} is data only", m.name)
            }
            Ok(m) => mods.push(m),
            Err(e) => chat.push(format!("Kiln: {e}")),
        }
    }
    if mods.is_empty() {
        return (State::Stopped("no Java mods".into()), chat);
    }
    let user_home = std::env::var_os("HOME").map(PathBuf::from);
    let env = |key: &str| std::env::var(key).ok();
    let needed = mods.iter().map(|m| m.java).max().unwrap_or(0);
    let home = match find_java(java_home.as_deref(), &env, user_home.as_deref(), needed) {
        Ok(home) => home,
        Err(e) => return stop("no Java", e.advice(java_home_file)),
    };
    // Kiln's runtime, then the libraries Minecraft ships (lib/), then the mods: as in the game, a mod sees Minecraft's
    // versions of them.
    let mut class_path = vec![runtime];
    class_path.extend(jars(&dir.join("lib")));
    class_path.extend(files);
    // The mods' keys as Kiln kept them, till this Java tells of them again (it tells the game which the player chose).
    mod_keys_lock().heard.clear();
    load_keys(dir);
    let started = Instant::now();
    let log = if restarted_from_process() {
        crate::game::kiln_jvm::RESTARTED_LOG
    } else {
        crate::game::kiln_jvm::JAVA_LOG
    };
    let mut java = match JavaHost::start_logged(&home, &class_path, dir, log) {
        Ok(java) => java,
        Err(e) => return stop("Java didn't start", e),
    };
    // D-190: Java that couldn't start in the game's process runs in one of its own; the player hears it once.
    if let Some(why) = java.fell_back.take() {
        chat.push(format!(
            "Kiln: Java couldn't start in the game's process ({why}). It runs in a process of its own: \
             the mods work as before, their calls a little slower"
        ));
    }
    log::info!("Kiln: starting Java from {}", home.display());
    // D-112: the crash window watches this game from now on; the last game's question is moot once Java runs.
    if CRASH_WATCH.load(Ordering::Relaxed) {
        if let Err(e) = start_crash_watch(&home, dir) {
            log::warn!("Kiln: no crash window for this game: {e}");
        }
    }
    *QUESTION.lock().unwrap_or_else(|e| e.into_inner()) = None;
    set_server(java.take_server_link().map(|link| ServerSide {
        link,
        items: BTreeSet::new(),
        events: BTreeSet::new(),
        blocks: BTreeMap::new(),
        entities: BTreeMap::new(),
        slots: BTreeMap::new(),
        calls: 0,
        commands: BTreeSet::new(),
        commands_told: None,
        commands_retry: None,
        late: BTreeMap::new(),
    }));
    let mut awaiting = 0;
    let mut sent = java
        .send("font", &[&font_field(&font())])
        .and_then(|()| java.send("babies", &[BABIES.get().map_or("", String::as_str)]));
    // Started again after Java in the game's process (D-134): the overlay says that Java's memory stays taken.
    if restarted_from_process() {
        let (bytes, shown) = OLD_JAVA.get();
        OLD_JAVA.set((bytes, true));
        sent = sent.and_then(|()| {
            java.send(
                "restarted",
                &[&bytes.to_string(), if shown { "0" } else { "1" }],
            )
        });
    }
    // As Fabric: every mod's main entrypoints, then every mod's client ones.
    let main = mods.iter().flat_map(|m| m.main.iter().map(|c| (c, "main")));
    let client = mods
        .iter()
        .flat_map(|m| m.client.iter().map(|c| (c, "client")));
    for (class, kind) in main.chain(client) {
        sent = sent.and_then(|()| java.send("load", &[class, kind]));
        awaiting += 1;
    }
    // Then their settings pages: Mod Menu's entrypoints, made after the mods' own (D-094).
    for m in &mods {
        for class in &m.modmenu {
            sent = sent.and_then(|()| java.send("modmenu", &[&m.id, class]));
        }
    }
    // D-128: the window's game has Java refresh the catalogue's copy online, on a thread of its own, at most once a
    // day (its setting, `catalogue_refresh` in Kiln's folder, can turn it off); Kiln's own tests never send it.
    if CRASH_WATCH.load(Ordering::Relaxed) {
        sent = sent.and_then(|()| java.send("catalogue", &[VERSION]));
    }
    if let Err(e) = sent {
        return stop("Java didn't start", e);
    }
    let running = Running {
        java,
        started,
        mods: mods
            .into_iter()
            .map(|m| {
                let n = m.entrypoints();
                (m, n, false)
            })
            .collect(),
        awaiting,
        version: String::new(),
        ticks: 0,
        last_tick: Instant::now(),
        dir: dir.to_owned(),
        keys: Vec::new(),
        bound: BTreeMap::new(),
        commands: Vec::new(),
        advancements: BTreeSet::new(),
        chunks: BTreeSet::new(),
        chunk_dimension: String::new(),
        draw: Vec::new(),
        soft_off: Some(OFF.lock().unwrap_or_else(|e| e.into_inner()).clone()),
        restart: None,
    };
    (State::Running(Box::new(running)), chat)
}

/// Kiln's own status box: its lines and their colours, while it starts, for a few seconds after, and
/// whenever something is wrong; none otherwise.
pub fn status_lines() -> Vec<(String, u32)> {
    STATE.with(|s| match &*s.borrow() {
        State::Idle => vec![(status_line(None, None), INK)],
        State::Stopped(why) if why == "no Java mods" => Vec::new(),
        State::Stopped(why) => vec![(status_line(None, Some(why)), TROUBLE)],
        State::Running(r) if r.awaiting > 0 => {
            vec![(status_line(None, Some("starting Java")), INK)]
        }
        State::Running(r) if r.ticks <= STATUS_TICKS => {
            vec![(status_line(Some(r.running()), None), INK)]
        }
        State::Running(_) => Vec::new(),
    })
}

/// How the game shows a mod's own screen (D-094): not at all, as Horizon's screen of a mod's own over the world
/// (`native_mod_screen_open`), or drawn over the game's screen open (Options > Mods, for a settings page), whose
/// input Kiln takes.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub enum Shown {
    #[default]
    No,
    Own,
    Over,
}

/// A mod's own screen as the window's thread keeps it: whether Java wants one open (said since the game last
/// looked), how the game shows it, what it draws, the commands for Java with the next tick (its input), and the mods
/// with a settings page.
#[derive(Debug, Default)]
pub struct ModScreen {
    wanted: Option<bool>,
    pub shown: Shown,
    pub ops: Vec<HudOp>,
    commands: Vec<(String, Vec<String>)>,
    settings: BTreeSet<String>,
    cursor: Option<[f32; 2]>,
    /// The game's container screen open (D-156), as last told to Java: `<id> <menu> <x> <y> <w> <h> <slots>`.
    game: Option<Vec<String>>,
    /// What the mods draw over the game's container screen (JEI's item list), as of the last tick Java answered.
    pub over_ops: Vec<HudOp>,
    /// Where that drawing takes the mouse from the game (`[x, y, w, h]` in GUI pixels), and whether it takes the keys
    /// (a text field of the mods' has the focus).
    claims: Vec<[f32; 4]>,
    keys: bool,
    /// Whether the mouse button held went down in a claimed place: its drag and release are the mods' too.
    pressed_over: bool,
    /// A click in a claimed place Java hasn't answered yet (D-178): when it was, and whether it went to Java. A click
    /// on a mod's text field gives it the keys only once Java has handled it and drawn; the keys typed right after
    /// (a try's `type` at once, a quick typist's first letter) are the mods' meanwhile, not the game's too (an E
    /// closing the inventory, a horizon-ctl `type` refused for no field of the game's).
    click_unanswered: Option<(Instant, bool)>,
    /// The keys whose press went to Java and whose release it hasn't heard yet, each with whether over the game's
    /// screen: a release the screen hooks didn't hand over (the control socket's tap) goes to Java too (D-174).
    keys_down: BTreeMap<String, bool>,
}

impl ModScreen {
    /// Java opened (true) or closed a mod's screen.
    fn java_says(&mut self, open: bool) {
        self.wanted = Some(open);
        if !open {
            self.ops.clear();
        }
    }

    /// A command for Java with the next tick, from the window's other news (the maps it knows, D-177).
    pub fn tell_java(&mut self, command: &str, fields: Vec<String>) {
        self.commands.push((command.to_owned(), fields));
    }

    /// What Java asked since the game last looked: open, close, or nothing new.
    pub fn take_wanted(&mut self) -> Option<bool> {
        self.wanted.take()
    }

    /// The game shows it (`Own`, `Over`) or not: closed by Java, or refused.
    pub fn show(&mut self, shown: Shown) {
        self.shown = shown;
        self.cursor = None;
        if shown == Shown::No {
            self.ops.clear();
        }
    }

    /// The game closed it by itself (another screen came, the world went): Java hears so with the next tick.
    pub fn closed_by_game(&mut self) {
        self.show(Shown::No);
        self.commands.push(("sclosed".into(), Vec::new()));
    }

    /// Input over it for Java (`sin <kind> ...`), only while the game shows it.
    pub fn input(&mut self, fields: Vec<String>) -> bool {
        if self.shown == Shown::No {
            return false;
        }
        self.key_heard(&fields, false);
        self.commands.push(("sin".into(), fields));
        true
    }

    /// A key's press or release handed to Java, kept (`keys_down`).
    fn key_heard(&mut self, fields: &[String], over: bool) {
        match fields {
            [kind, key, ..] if kind == "key" => {
                self.keys_down.insert(key.clone(), over);
            }
            [kind, key, ..] if kind == "keyup" => {
                self.keys_down.remove(key);
            }
            _ => {}
        }
    }

    /// The key `key` (`key.keyboard.a`) went up: if its press went to Java and its release not, the release goes too,
    /// as the window's would (a recipe viewer stops ignoring the characters a key it took would type then, D-174).
    pub fn released(&mut self, key: &str) {
        if let Some(over) = self.keys_down.remove(key) {
            let mut fields = if over {
                vec!["over".to_owned()]
            } else {
                Vec::new()
            };
            fields.extend(["keyup".to_owned(), key.to_owned(), "0".to_owned()]);
            self.commands.push(("sin".into(), fields));
        }
    }

    /// Where the pointer is over it: Java hears when it moved.
    pub fn cursor(&mut self, at: [f32; 2]) {
        if self.shown != Shown::No && self.cursor != Some(at) {
            self.cursor = Some(at);
            self.commands
                .push(("sin".into(), vec!["move".into(), num(at[0]), num(at[1])]));
        }
    }

    /// The game's container screen the hooks fire for (D-156): Java hears of it when it changed (`sin game ...`, or
    /// `sin game -` when the game shows none); the mods' drawing over it goes with it.
    pub fn game_screen(&mut self, view: Option<Vec<String>>) {
        if self.game != view {
            if view.is_none() {
                self.over(Vec::new(), false, Vec::new());
            }
            let mut fields = vec!["game".to_owned()];
            fields.extend(view.clone().unwrap_or_else(|| vec!["-".to_owned()]));
            self.commands.push(("sin".into(), fields));
            self.game = view;
        }
    }

    /// Whether the game shows a container screen Java knows of.
    pub fn over_game(&self) -> bool {
        self.game.is_some()
    }

    /// Where the mods' drawing over the game's container screen takes the mouse (`[x, y, w, h]`), and whether it takes
    /// the keys, as of the last tick Java answered. For the tests (`kiln/tests/jei_overlay.rs`): the game reads them in
    /// [`ModScreen::over_input`], so in the game's build nothing calls this.
    #[allow(dead_code)]
    pub fn over_claims(&self) -> (&[[f32; 4]], bool) {
        (&self.claims, self.keys)
    }

    /// What Java drew over the game's container screen, where it takes the mouse, and whether it takes the keys.
    fn over(&mut self, ops: Vec<HudOp>, keys: bool, claims: Vec<[f32; 4]>) {
        self.over_ops = ops;
        self.keys = keys;
        self.claims = claims;
        // Drawn after it handled the click: from now its own word on the keys holds.
        if self.click_unanswered.is_some_and(|(_, sent)| sent) {
            self.click_unanswered = None;
        }
    }

    /// The commands for Java with this tick, taken: a click among them has gone to Java (D-178).
    fn take_commands(&mut self) -> Vec<(String, Vec<String>)> {
        if let Some((_, sent)) = self.click_unanswered.as_mut() {
            *sent = true;
        }
        std::mem::take(&mut self.commands)
    }

    /// Whether the mods have the keys: a text field of theirs has the focus, or a click on their drawing Java hasn't
    /// answered yet came less than a third of a second ago (D-178). Java draws after every tick that changed what it
    /// draws, so a click that focused nothing lets the keys go back to the game with that time.
    fn has_keys(&self) -> bool {
        self.keys
            || self
                .click_unanswered
                .is_some_and(|(at, _)| at.elapsed() < std::time::Duration::from_millis(330))
    }

    /// Input over the game's container screen (`sin over <kind> ...`), for Java's mods (Fabric's `allow*` events):
    /// whether the game is to leave it alone, as it landed where the mods' drawing takes the mouse (a click, the wheel;
    /// a drag or release of a button that went down there), or the mods have the keys (a key, a character).
    pub fn over_input(&mut self, fields: Vec<String>, at: [f32; 2]) -> bool {
        if self.game.is_none() {
            return false;
        }
        let claimed = match fields.first().map(String::as_str) {
            Some("press") => {
                self.pressed_over = self.claims.iter().any(|r| inside(*r, at));
                if self.pressed_over {
                    self.click_unanswered = Some((Instant::now(), false));
                }
                self.pressed_over
            }
            Some("drag") => self.pressed_over,
            Some("release") => std::mem::take(&mut self.pressed_over),
            Some("scroll") => self.claims.iter().any(|r| inside(*r, at)),
            _ => self.has_keys(),
        };
        self.key_heard(&fields, true);
        let mut sent = vec!["over".to_owned()];
        sent.extend(fields);
        self.commands.push(("sin".into(), sent));
        claimed
    }

    /// Whether a paste key waits for Java among the input (`key key.keyboard.v` with Control, or on macOS Command,
    /// held, GLFW's bits 2 and 8): the game reads its clipboard for it then (D-174), not with every key.
    pub fn wants_clipboard(&self) -> bool {
        self.commands
            .iter()
            .any(|(command, fields)| command == "sin" && is_paste(fields))
    }

    /// The system's clipboard's text, for Java just before the first paste key waiting (`sin clip <text>`).
    pub fn clipboard(&mut self, text: String) {
        if let Some(at) = self
            .commands
            .iter()
            .position(|(command, fields)| command == "sin" && is_paste(fields))
        {
            self.commands
                .insert(at, ("sin".into(), vec!["clip".into(), text]));
        }
    }

    /// Whether row `row` of Options > Mods (`kiln_<mod id>`) has a settings page.
    pub fn has_settings(&self, row: &str) -> bool {
        row.strip_prefix("kiln_")
            .is_some_and(|id| self.settings.contains(id))
    }

    /// The player pressed the settings button on row `row`: Java opens its page with the next tick.
    pub fn open_settings(&mut self, row: &str) {
        if let Some(id) = row
            .strip_prefix("kiln_")
            .filter(|id| self.settings.contains(*id))
        {
            self.commands.push(("settings".into(), vec![id.to_owned()]));
        }
    }
}

/// Whether a screen's input (`key <name> <modifiers>`, or `over key ...`) is a paste: V with Control or Command.
fn is_paste(fields: &[String]) -> bool {
    let fields = if fields.first().is_some_and(|f| f == "over") {
        &fields[1..]
    } else {
        fields
    };
    matches!(fields, [kind, key, mods, ..] if kind == "key" && key == "key.keyboard.v"
        && mods.parse::<u32>().is_ok_and(|m| m & 0b1010 != 0))
}

/// Whether point `at` is inside `[x, y, w, h]`.
fn inside(r: [f32; 4], at: [f32; 2]) -> bool {
    at[0] >= r[0] && at[0] < r[0] + r[2] && at[1] >= r[1] && at[1] < r[1] + r[3]
}

/// The button on a Java mod's page in Options > Mods that opens its settings page.
pub const SETTINGS_BUTTON: &str = "Settings...";

// --- Kiln's overlay (D-113) ---------------------------------------------------------------------------------------
//
// A screen of Kiln's own in Java (kiln/java/kiln/Overlay.java), shown as a mod's screen is (D-094): every Java mod in
// kiln/mods with its state, what the soft switch can stop now, the catalogue's word, and its switch. Its key (F7 by
// default) is bound on Kiln's page in Options > Mods, where a button opens it too.

/// The overlay's key: its name, as Kiln's page in Options > Mods lists it, and in `horizon-mods.txt`.
pub const OVERLAY_KEY: &str = "key.kiln.overlay";
/// Its label on Kiln's page.
pub const OVERLAY_KEY_LABEL: &str = "Open Kiln's overlay (the Java mods)";
/// Its key until the player binds another.
pub const OVERLAY_KEY_DEFAULT: &str = "key.keyboard.f7";
/// The button on Kiln's page in Options > Mods that opens it.
pub const OVERLAY_BUTTON: &str = "Java mods...";

/// What the overlay asked of the game since the game last looked: the switches it flipped, a folder to show.
#[derive(Debug, Default)]
pub struct OverlayAsks {
    pub switched: Vec<(String, bool)>,
    pub open: Option<PathBuf>,
    /// The banner after Restart Java now asked for the game to start itself again and close ([`restart_game`]).
    pub restart_game: bool,
}

thread_local! {
    static OVERLAY: RefCell<OverlayAsks> = RefCell::new(OverlayAsks::default());
}

/// The overlay's asks since the last call, for the game to do: each switch flipped in Options > Mods, a folder shown.
pub fn take_overlay_asks() -> OverlayAsks {
    OVERLAY.with(|o| std::mem::take(&mut *o.borrow_mut()))
}

/// The Java mod that stopped the last game, and how (`stopped`, `stuck`, `memory`, `stack`): the one the title screen
/// asks about (D-112), for the overlay's state.
static LAST_STOP: Mutex<Option<(String, String)>> = Mutex::new(None);

/// The overlay's rows: for each Java mod in `kiln_dir`/mods, its id, name, version, jar, what it holds (`code`,
/// `mixins`, `data`), how it stopped the last game ("" when it didn't) and whether its switch is off (`1`), joined
/// by U+001F, as kiln.Overlay.Jar reads them. `off` are the mods whose switch is off.
pub fn overlay_rows(kiln_dir: &Path, off: &BTreeSet<String>) -> Vec<String> {
    let last = LAST_STOP.lock().unwrap_or_else(|e| e.into_inner()).clone();
    let mut seen = BTreeSet::new();
    let mut rows = Vec::new();
    for jar in jars(&kiln_dir.join("mods")) {
        let Ok(m) = read_jar(&jar) else { continue };
        if !seen.insert(m.id.clone()) {
            continue;
        }
        let kind = if m.entrypoints() > 0 {
            "code"
        } else if m.mixins {
            "mixins"
        } else {
            "data"
        };
        let stop = match &last {
            Some((id, how)) if *id == m.id => how.as_str(),
            _ => "",
        };
        let clean = |s: &str| s.replace(['\u{1f}', '\n', '\r', '\t'], " ");
        rows.push(
            [
                clean(&m.id),
                clean(if m.name.is_empty() { &m.id } else { &m.name }),
                clean(&m.version),
                jar.to_string_lossy().into_owned(),
                kind.to_owned(),
                stop.to_owned(),
                if off.contains(&m.id) { "1" } else { "0" }.to_owned(),
            ]
            .join("\u{1f}"),
        );
    }
    rows
}

/// The player asked for the overlay (its key, or the button on Kiln's page): Java opens it with the next tick, or
/// closes it when it is open. `key` is the overlay's key as bound, so the overlay closes on it too. False when Java
/// isn't running (it starts with the first world), so there is no overlay to open.
pub fn toggle_overlay(kiln_dir: &Path, key: &str) -> bool {
    if !java_running() {
        return false;
    }
    let off = OFF.lock().unwrap_or_else(|e| e.into_inner()).clone();
    let mut fields = vec![key.to_owned()];
    fields.extend(overlay_rows(kiln_dir, &off));
    SCREEN.with(|m| m.borrow_mut().commands.push(("overlay".into(), fields)));
    true
}

/// Whether Java runs (it starts with the first world the player enters).
pub fn java_running() -> bool {
    STATE.with(|s| matches!(&*s.borrow(), State::Running(_)))
}

/// The game starts itself again and closes (D-134, the banner's "Restart the game"): its program with its arguments,
/// in its folder, as the crash window starts it ([`running_text`]), in a process group of its own; then this process
/// exits through `exit`, which removes the `running` note (a clean exit: the crash window says nothing). Returns only
/// when the new game couldn't start.
pub fn restart_game() -> Result<(), String> {
    let program = std::env::current_exe().map_err(|e| format!("no program path: {e}"))?;
    let folder = std::env::current_dir().map_err(|e| format!("no current folder: {e}"))?;
    let mut command = std::process::Command::new(&program);
    command
        .args(std::env::args_os().skip(1))
        .current_dir(&folder)
        .stdin(std::process::Stdio::null());
    #[cfg(unix)]
    {
        use std::os::unix::process::CommandExt;
        command.process_group(0);
    }
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        // DETACHED_PROCESS | CREATE_NEW_PROCESS_GROUP
        command.creation_flags(0x0000_0008 | 0x0000_0200);
    }
    command
        .spawn()
        .map_err(|e| format!("can't start {}: {e}", program.display()))?;
    log::info!(
        "Kiln: the game starts again (the player's choice after Restart Java now); this one closes"
    );
    std::process::exit(0);
}

/// Shows `folder` in the system's file manager, without waiting for it.
pub fn show_folder(folder: &Path) {
    let program = if cfg!(target_os = "macos") {
        "open"
    } else if cfg!(windows) {
        "explorer"
    } else {
        "xdg-open"
    };
    if let Err(e) = std::fs::create_dir_all(folder) {
        log::warn!("Kiln: can't make {}: {e}", folder.display());
    }
    match std::process::Command::new(program).arg(folder).spawn() {
        // Waited for on a thread of its own, so it leaves no zombie behind.
        Ok(mut child) => {
            let _ = std::thread::spawn(move || {
                let _ = child.wait();
            });
        }
        Err(e) => log::warn!("Kiln: can't show {} with {program}: {e}", folder.display()),
    }
}

/// A sprite `size` pixels big drawn into `rect` (`[x, y, w, h]`) with its edges `border` wide kept: the pieces to draw,
/// each its part of the sprite and its box (`[x, y, w, h]`): corners as they are, edges stretched along, the middle
/// stretched both ways, as vanilla's nine-sliced GUI sprites. With no border, or a box too small for one, the whole
/// sprite stretched.
pub fn nine_slice(size: [f32; 2], rect: [f32; 4], border: f32) -> Vec<([f32; 4], [f32; 4])> {
    let [sw, sh] = size;
    let [x, y, w, h] = rect;
    let b = border.min(w / 2.0).min(h / 2.0).min(sw / 2.0).min(sh / 2.0);
    if b <= 0.0 {
        return vec![([0.0, 0.0, sw, sh], rect)];
    }
    let src_x = [0.0, b, sw - b, sw];
    let src_y = [0.0, b, sh - b, sh];
    let dst_x = [x, x + b, x + w - b, x + w];
    let dst_y = [y, y + b, y + h - b, y + h];
    let mut pieces = Vec::new();
    for row in 0..3 {
        for column in 0..3 {
            let src = [
                src_x[column],
                src_y[row],
                src_x[column + 1] - src_x[column],
                src_y[row + 1] - src_y[row],
            ];
            let dst = [
                dst_x[column],
                dst_y[row],
                dst_x[column + 1] - dst_x[column],
                dst_y[row + 1] - dst_y[row],
            ];
            if src[2] > 0.0 && src[3] > 0.0 && dst[2] > 0.0 && dst[3] > 0.0 {
                pieces.push((src, dst));
            }
        }
    }
    pieces
}

/// A number as Java reads it: whole ones without a fraction.
pub fn num(v: f32) -> String {
    if v.fract() == 0.0 && v.abs() < 1e9 {
        format!("{}", v as i64)
    } else {
        v.to_string()
    }
}

/// The window's thread: a mod's own screen and the settings pages, to read or change.
pub fn mod_screen<R>(f: impl FnOnce(&mut ModScreen) -> R) -> R {
    SCREEN.with(|m| f(&mut m.borrow_mut()))
}

/// What the Java mods draw on the HUD, as of the last tick Java answered.
pub fn hud_ops() -> Vec<HudOp> {
    STATE.with(|s| match &*s.borrow() {
        State::Running(r) => r.draw.clone(),
        _ => Vec::new(),
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn java_hides_hud_layers_and_makes_tooltips() {
        assert_eq!(hud_layers("-"), Vec::<String>::new());
        assert_eq!(
            hud_layers("minecraft:crosshair,minecraft:hotbar"),
            ["minecraft:crosshair", "minecraft:hotbar"]
        );
        back(vec![("hud_hidden".into(), "minecraft:crosshair".into())]);
        assert!(hud_hidden("minecraft:crosshair") && !hud_hidden("minecraft:hotbar"));
        back(vec![("hud_hidden".into(), "-".into())]);
        assert!(!hud_hidden("minecraft:crosshair"));
        let key = "{count:1,id:\"minecraft:stick\"}";
        let (made, ask) = tooltip(key);
        assert!(made.is_none() && ask, "asked the first time it shows");
        assert!(!tooltip(key).1, "not again at once");
        back(vec![(
            "tooltip".into(),
            format!("{key}\u{1f}null\u{1f}{{\"text\":\"a\"}}\u{1f}{{\"text\":\"b\"}}"),
        )]);
        let made = tooltip(key).0.expect("answered");
        assert!(made.name.is_none());
        assert_eq!(made.lines.len(), 2);
        back(vec![(
            "tooltip".into(),
            format!("{key}\u{1f}{{\"text\":\"Stick!\"}}"),
        )]);
        let made = tooltip(key).0.unwrap();
        assert_eq!(made.name.unwrap()["text"], "Stick!");
        assert!(made.lines.is_empty() && !made.whole);
        // A mod changed one of the game's lines: Java's lines stand for Horizon's (D-218).
        back(vec![(
            "tooltip".into(),
            format!("!{key}\u{1f}null\u{1f}{{\"text\":\"one line\"}}"),
        )]);
        let made = tooltip(key).0.unwrap();
        assert!(made.whole && made.lines.len() == 1 && made.lines[0]["text"] == "one line");
    }

    fn scratch(name: &str) -> PathBuf {
        let dir = std::env::temp_dir().join(format!("kiln-test-{}-{name}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).unwrap();
        dir
    }

    /// A fake Java home: its `release` file and an empty `java` program.
    fn fake_java(home: &Path, version: &str) {
        std::fs::create_dir_all(java_program(home).parent().unwrap()).unwrap();
        std::fs::write(java_program(home), b"").unwrap();
        std::fs::write(
            home.join("release"),
            format!("JAVA_VERSION=\"{version}\"\n"),
        )
        .unwrap();
    }

    fn crystal_user() -> PlayerSnapshot {
        PlayerSnapshot {
            uuid: 0x0123_4567_89ab_cdef_0123_4567_89ab_cdef,
            name: "Alex".into(),
            dimension: "minecraft:overworld".into(),
            position: [1.5, 64.0, -2.25],
            game_mode: 0,
            health: 20.0,
            main: Some(("heart_crystals:heart_crystal".into(), 3)),
            off: None,
            attributes: vec![("minecraft:max_health".into(), 20.0, 20.0)],
            sneaking: true,
            experience: (5, 0.25, 80),
            main_stack: r#"{count:3,id:"heart_crystals:heart_crystal"}"#.into(),
            off_stack: String::new(),
        }
    }

    #[test]
    fn a_use_carries_the_player_for_java() {
        assert_eq!(
            uuid_text(0x0123_4567_89ab_cdef_0123_4567_89ab_cdef),
            "01234567-89ab-cdef-0123-456789abcdef"
        );
        assert_eq!(
            use_fields(
                9,
                Path::new("/saves/New World"),
                1200,
                "heart_crystals:heart_crystal",
                Hand::Main,
                &crystal_user()
            ),
            [
                "9",
                "/saves/New World",
                "1200",
                "heart_crystals:heart_crystal",
                "main",
                "01234567-89ab-cdef-0123-456789abcdef",
                "Alex",
                "minecraft:overworld",
                "1.5",
                "64",
                "-2.25",
                "0",
                "20",
                "heart_crystals:heart_crystal",
                "3",
                "",
                "0",
                "minecraft:max_health 20 20",
                "1",
                "5",
                "0.25",
                "80",
                r#"{count:3,id:"heart_crystals:heart_crystal"}"#,
                "",
            ]
        );
    }

    #[test]
    fn events_carry_their_players() {
        let mut new = crystal_user();
        new.health = 20.0;
        new.attributes
            .push(("minecraft:movement_speed".into(), 0.1, 0.13));
        let old = PlayerSnapshot {
            health: 0.0,
            ..crystal_user()
        };
        let fields = respawn_fields(4, Path::new("/w"), 7, false, &old, &new);
        assert_eq!(fields.len(), 4 + 2 * PLAYER_FIELDS);
        assert_eq!(fields[..4], ["4", "/w", "7", "0"]);
        assert_eq!(fields[4 + 7], "0");
        assert_eq!(
            fields[4 + PLAYER_FIELDS + 12],
            "minecraft:max_health 20 20;minecraft:movement_speed 0.1 0.13"
        );
        let joined = join_fields(5, Path::new("/w"), 7, &new);
        assert_eq!(joined.len(), 3 + PLAYER_FIELDS);
        assert_eq!(
            joined[3..6],
            [
                uuid_text(new.uuid),
                "Alex".into(),
                "minecraft:overworld".into()
            ]
        );
        let no_attributes = PlayerSnapshot {
            attributes: Vec::new(),
            ..crystal_user()
        };
        assert_eq!(player_fields(&no_attributes)[12], "");
    }

    #[test]
    fn block_calls_carry_the_block() {
        let fields = use_block_fields(
            3,
            Path::new("/w"),
            5,
            [1, -64, 2],
            "mymod:lamp[lit=false]",
            "up",
            Hand::Off,
            &crystal_user(),
        );
        assert_eq!(
            fields[..9],
            [
                "3",
                "/w",
                "5",
                "1",
                "-64",
                "2",
                "mymod:lamp[lit=false]",
                "up",
                "off"
            ]
        );
        assert_eq!(fields.len(), 9 + PLAYER_FIELDS);
        assert_eq!(
            random_tick_fields(
                4,
                Path::new("/w"),
                5,
                "minecraft:overworld",
                [0, 70, 0],
                "mymod:sprout[age=0]"
            ),
            [
                "4",
                "/w",
                "5",
                "minecraft:overworld",
                "0",
                "70",
                "0",
                "mymod:sprout[age=0]"
            ]
        );
        assert_eq!(
            step_on_fields(
                6,
                Path::new("/w"),
                5,
                [0, 63, 0],
                "mymod:pad",
                &crystal_user()
            )[..7],
            ["6", "/w", "5", "0", "63", "0", "mymod:pad"]
        );
        assert_eq!(block_id("mymod:lamp[lit=true]"), "mymod:lamp");
        assert_eq!(block_id("minecraft:stone"), "minecraft:stone");
        assert_eq!(
            parse_effect("block 1 -64 2 3 mymod:lamp[lit=true]"),
            Some(Effect::Block {
                pos: [1, -64, 2],
                flags: 3,
                state: "mymod:lamp[lit=true]".into()
            })
        );
        assert_eq!(
            parse_effect(r#"drop 1 64 2 {count:2,id:"minecraft:wheat"}"#),
            Some(Effect::Drop {
                pos: [1, 64, 2],
                stack: r#"{count:2,id:"minecraft:wheat"}"#.into()
            })
        );
        // A stack with components goes whole, spaces in its strings too (a head's profile, D-207).
        let head = r#"{components:{"minecraft:profile":{id:[I;1,2,3,4],properties:[{name:"textures",value:"e30="}]},"minecraft:custom_name":"Kiln head"},count:1,id:"minecraft:player_head"}"#;
        assert_eq!(
            parse_effect(&format!("drop -3 70 8 {head}")),
            Some(Effect::Drop {
                pos: [-3, 70, 8],
                stack: head.into()
            })
        );
        assert_eq!(parse_effect("drop 1 64 2"), None);
        // `Level.destroyBlock` and `Block.dropResources` (#149): the game breaks the block, or drops what a state
        // drops by its loot table.
        assert_eq!(
            parse_effect("destroy 0 200 2 1"),
            Some(Effect::Destroy {
                pos: [0, 200, 2],
                drop: true
            })
        );
        assert_eq!(
            parse_effect("destroy 0 200 2 0"),
            Some(Effect::Destroy {
                pos: [0, 200, 2],
                drop: false
            })
        );
        assert_eq!(parse_effect("destroy 0 200 2 yes"), None);
        assert_eq!(
            parse_effect("drops 0 200 2 minecraft:wheat[age=7]"),
            Some(Effect::Drops {
                pos: [0, 200, 2],
                state: "minecraft:wheat[age=7]".into()
            })
        );
    }

    #[test]
    fn tick_events_carry_their_phase_and_level() {
        assert_eq!(
            server_tick_fields(7, Path::new("/w"), 5, TickPhase::Start, ""),
            ["7", "/w", "5", "start", ""]
        );
        assert_eq!(
            server_tick_fields(
                8,
                Path::new("/w"),
                5,
                TickPhase::LevelStart,
                "minecraft:the_nether"
            ),
            ["8", "/w", "5", "level_start", "minecraft:the_nether"]
        );
        assert_eq!(TickPhase::LevelEnd.names(), ("level_end", "level_tick_end"));
        assert_eq!(TickPhase::End.names(), ("end", "server_tick_end"));
        assert_eq!(TickPhase::Start.names().1, "server_tick_start");
        assert_eq!(TickPhase::LevelStart.names().1, "level_tick_start");
    }

    #[test]
    fn a_call_says_when_the_mods_began_to_listen() {
        let mut effects = vec![
            "block_entity 1 2 3 {energy:5L}".to_owned(),
            "listens join server_tick_start".to_owned(),
            "listensfor 1".to_owned(),
        ];
        assert_eq!(
            take_listens(&mut effects),
            Some(vec!["join".to_owned(), "server_tick_start".to_owned()])
        );
        assert_eq!(effects, ["block_entity 1 2 3 {energy:5L}", "listensfor 1"]);
        // None any more, said as a bare word; and a call with nothing new says nothing.
        let mut effects = vec!["listens".to_owned()];
        assert_eq!(take_listens(&mut effects), Some(Vec::new()));
        assert!(effects.is_empty());
        assert_eq!(
            take_listens(&mut vec!["sound a 0 0 0 0 1 1".to_owned()]),
            None
        );
    }

    #[test]
    fn what_mods_change_goes_back_to_horizon() {
        // The view's field of view is kept for every frame; the rest waits for the window's next tick, in order.
        assert_eq!(view_fov(80.0, 70.0), 80.0, "no mixin: the view as it was");
        back(vec![
            ("fov".into(), "30".into()),
            ("view_fov".into(), "35".into()),
            ("flying_speed".into(), "0.1".into()),
        ]);
        assert_eq!(
            take_back(),
            [
                ("fov".to_owned(), "30".to_owned()),
                ("flying_speed".to_owned(), "0.1".to_owned())
            ]
        );
        assert!(take_back().is_empty());
        // A zoom to half the option halves Horizon's view, its own effects (sprinting) kept.
        assert_eq!(view_fov(70.0, 70.0), 35.0);
        assert_eq!(view_fov(80.0, 70.0), 40.0);
        assert_eq!(view_fov(80.0, 0.0), 80.0, "no option to scale over");
        back(vec![("view_fov".into(), "NaN".into())]);
        assert_eq!(
            view_fov(80.0, 70.0),
            80.0,
            "a value that isn't one undoes it"
        );
        // The brightness a mixin makes the option give (D-192): kept for the renderer, the option untouched; when it
        // goes, the renderer is told once to take the option's again.
        assert_eq!(view_gamma(), Ok(None));
        back(vec![("view_gamma".into(), "16.0".into())]);
        assert!(take_back().is_empty());
        assert_eq!(view_gamma(), Ok(Some(16.0)));
        assert_eq!(view_gamma(), Ok(Some(16.0)));
        back(vec![("view_gamma".into(), "-".into())]);
        assert_eq!(view_gamma(), Err(()));
        assert_eq!(view_gamma(), Ok(None));
        // The window tells Java the options and the flying speed mods may change.
        let field = WindowState {
            show_subtitles: true,
            flying_speed: 0.05,
            gamma: 0.7,
            gui_scale: 3,
            ..WindowState::default()
        }
        .field();
        assert!(
            field.contains("subtitles=1") && field.contains("flying_speed=0.05"),
            "{field}"
        );
        assert!(
            field.contains("gamma=0.7") && field.contains("gui_scale=3"),
            "{field}"
        );
        // The focus and the volume sliders (D-232): a focused window says so by default.
        assert!(field.contains("unfocused=0"), "{field}");
        let mut volumes = [1.0; 11];
        volumes[0] = 0.25;
        let field = WindowState {
            unfocused: true,
            volumes,
            ..WindowState::default()
        }
        .field();
        assert!(
            field.contains("unfocused=1") && field.contains("volumes=0.25 1 1 1 1 1 1 1 1 1 1;"),
            "{field}"
        );
    }

    #[test]
    fn the_window_tells_java_the_crosshair_camera_debug_entries_and_hook() {
        let field = WindowState {
            minor_horizontal_collision: true,
            hit: Some(([1, 64, -2], [0, 1, 0], [1.5, 65.0, -1.5])),
            camera: 2,
            debug: vec!["entity_hitboxes:alwaysOn".into(), "fps:inOverlay".into()],
            debug_on: vec!["entity_hitboxes".into()],
            hook: Some([1.0, 62.5, 3.0, 0.0, -0.25, 0.0]),
            ..WindowState::default()
        }
        .field();
        for part in [
            "minor_collision=1",
            "hit=block 1 64 -2 0 1 0 1.5 65 -1.5",
            "camera=2",
            "debug=entity_hitboxes:alwaysOn,fps:inOverlay",
            "debug_on=entity_hitboxes",
            "hook=1 62.5 3 0 -0.25 0",
        ] {
            assert!(field.split(';').any(|p| p == part), "{part} in {field}");
        }
        let nothing = WindowState::default().field();
        assert!(
            nothing.split(';').any(|p| p == "hit=miss") && nothing.split(';').any(|p| p == "hook="),
            "{nothing}"
        );
    }

    #[test]
    fn the_window_tells_java_the_mobs_near_the_player() {
        let field = WindowState {
            mobs: vec![
                (7, "minecraft:cow".into(), [1.5, 64.0, -2.25]),
                (9, "minecraft:zombie".into(), [0.0, 63.0, 4.0]),
            ],
            ..WindowState::default()
        }
        .field();
        assert!(
            field
                .split(';')
                .any(|p| p == "mobs=7 minecraft%3Acow 1.5 64 -2.25,9 minecraft%3Azombie 0 63 4"),
            "{field}"
        );
        assert!(
            WindowState::default()
                .field()
                .split(';')
                .any(|p| p == "mobs=")
        );
    }

    #[test]
    fn the_mixin_bridge_hands_hooks_on_and_takes_outs_back() {
        // A hook's outs come back as effects, by its parameter's name; the value is all the rest.
        assert_eq!(
            parse_effect("out canceled 1"),
            Some(Effect::Out {
                name: "canceled".into(),
                value: "1".into()
            })
        );
        assert_eq!(
            parse_effect("out amount 2.5"),
            Some(Effect::Out {
                name: "amount".into(),
                value: "2.5".into()
            })
        );
        assert_eq!(
            parse_effect("out note a b"),
            Some(Effect::Out {
                name: "note".into(),
                value: "a b".into()
            })
        );
        // A server command's usage (its level first, the usage all the rest) and its answer's line.
        assert_eq!(
            parse_effect("usage 2 eco give <player:player> <amount:integer>"),
            Some(Effect::Usage {
                level: 2,
                usage: "eco give <player:player> <amount:integer>".into()
            })
        );
        assert_eq!(
            parse_effect("usage 9 op"),
            Some(Effect::Usage {
                level: 4,
                usage: "op".into()
            })
        );
        assert_eq!(parse_effect("usage x op"), None);
        assert_eq!(
            parse_effect(r#"feedback {"text":"Hi there"}"#),
            Some(Effect::Feedback(serde_json::json!({"text": "Hi there"})))
        );
        // The call: the hook, how many fields it has, they, then the player when it is about one.
        let world = Path::new("/saves/New World");
        let fields = ["7".to_owned(), "minecraft:zombie".to_owned()];
        assert_eq!(
            bridge_fields(3, world, 1200, "living_incoming_damage", &fields, None),
            [
                "3",
                "/saves/New World",
                "1200",
                "living_incoming_damage",
                "2",
                "7",
                "minecraft:zombie"
            ]
        );
        let player = crystal_user();
        let with = bridge_fields(4, world, 1200, "player_tick", &[], Some(&player));
        assert_eq!(
            with[..5],
            ["4", "/saves/New World", "1200", "player_tick", "0"]
        );
        assert_eq!(with[5], "01234567-89ab-cdef-0123-456789abcdef");
        assert_eq!(with.len(), 5 + PLAYER_FIELDS);
        // Only the hooks Java asked for are handed on; with none, a hook is nothing, and no server call is made.
        set_bridged(vec![
            "mouse_scrolling".into(),
            "living_incoming_damage".into(),
        ]);
        assert!(bridged("mouse_scrolling") && bridged("living_incoming_damage"));
        assert!(!bridged("player_tick"));
        hook("player_tick", &[]);
        assert!(
            bridge(world, 1200, "player_tick", &[], Some(&player), &mut |_| {
                String::new()
            })
            .is_none()
        );
        // Java isn't running on this thread: a forwarded hook goes nowhere.
        hook("mouse_scrolling", &["0".into(), "1".into()]);
        set_bridged(Vec::new());
        assert!(!bridged("mouse_scrolling"));
    }

    #[test]
    fn payloads_cross_as_effects_calls_and_uuids() {
        let uuid = 0x0123_4567_89ab_cdef_0123_4567_89ab_cdef;
        assert_eq!(parse_uuid(&uuid_text(uuid)), Some(uuid));
        assert_eq!(
            parse_uuid("01234567-89AB-CDEF-0123-456789ABCDEF"),
            Some(uuid)
        );
        assert_eq!(
            parse_uuid("00000000-0000-0000-0000-000000c0ffee"),
            Some(0xc0ffee)
        );
        assert_eq!(
            parse_uuid("0123456789abcdef0123456789abcdef"),
            None,
            "no dashes"
        );
        assert_eq!(
            parse_uuid("01234567-89ab-cdef-0123-456789abcde"),
            None,
            "a digit short"
        );
        assert_eq!(parse_uuid("01234567-89ab-cdef-0123-456789abcdeg"), None);
        assert_eq!(
            parse_uuid("+1234567-89ab-cdef-0123-456789abcdef"),
            None,
            "a sign isn't a digit"
        );
        assert_eq!(
            parse_effect("net 01234567-89ab-cdef-0123-456789abcdef 0a6b696c6e3a70696e6703"),
            Some(Effect::Net {
                player: uuid,
                data: b"\x0akiln:ping\x03".to_vec()
            })
        );
        assert_eq!(
            parse_effect("net 01234567-89ab-cdef-0123-456789abcdef 0a6"),
            None,
            "odd hex"
        );
        assert_eq!(parse_effect("net someone 0a"), None);
        assert_eq!(
            parse_effect("net 01234567-89ab-cdef-0123-456789abcdef"),
            None
        );
        let fields = from_client_fields(
            7,
            Path::new("/saves/New World"),
            1200,
            &[0x0a, 0xff],
            &crystal_user(),
        );
        assert_eq!(
            fields[..5],
            [
                "7",
                "/saves/New World",
                "1200",
                "0aff",
                "01234567-89ab-cdef-0123-456789abcdef"
            ]
        );
        assert_eq!(fields.len(), 4 + PLAYER_FIELDS);
        // Nothing waits before Java runs, and a message from the server then goes nowhere.
        from_server(b"\x0akiln:pong");
        assert!(take_net().is_empty());
    }

    #[test]
    fn placements_and_new_effects_parse() {
        let placement = Placement {
            pos: [1, 65, 2],
            state: "mymod:timer[facing=north,lit=false]".into(),
            face: "up".into(),
            hit: [1.5, 65.0, 2.25],
            clicked: [1, 64, 2],
            yaw: 180.0,
            pitch: 30.0,
            sneaking: false,
            item: "mymod:timer".into(),
            replacing: false,
        };
        assert_eq!(
            placement_fields("minecraft:overworld", &placement),
            [
                "minecraft:overworld",
                "1",
                "65",
                "2",
                "mymod:timer[facing=north,lit=false]",
                "up",
                "1.5",
                "65",
                "2.25",
                "1",
                "64",
                "2",
                "180",
                "30",
                "0",
                "mymod:timer",
                "0"
            ]
        );
        assert_eq!(
            parse_effect("schedule 1 65 2 mymod:timer 20"),
            Some(Effect::Schedule {
                pos: [1, 65, 2],
                block: "mymod:timer".into(),
                delay: 20
            })
        );
        assert_eq!(
            parse_effect("placement none"),
            Some(Effect::Placement(None))
        );
        assert_eq!(
            parse_effect("block_entity 1 65 -2 CgMABWNvdW50AAAABQA="),
            Some(Effect::BlockEntity {
                pos: [1, 65, -2],
                data: b"\n\x03\x00\x05count\x00\x00\x00\x05\x00".to_vec()
            })
        );
        assert!(parse_effect("block_entity 1 65").is_none());
        assert!(parse_effect("block_entity 1 65 -2 {count:5}").is_none());
        for bytes in [
            &b""[..],
            b"a",
            b"ab",
            b"abc",
            b"abcd",
            &[0, 255, 128, 7, 63],
        ] {
            assert_eq!(from_nbt_field(&nbt_field(bytes)).as_deref(), Some(bytes));
        }
        assert_eq!(nbt_field(b"ab"), "YWI=");
        assert_eq!(from_nbt_field("YWI").as_deref(), Some(&b"ab"[..]));
        assert_eq!(from_nbt_field("Y"), None);
        assert_eq!(from_nbt_field("YW I="), None);
        assert_eq!(
            parse_effect(
                "container 1 65 -2 {Before:[],Items:[{Slot:0b,count:4,id:\"minecraft:raw_iron\"}]}"
            ),
            Some(Effect::Container {
                pos: [1, 65, -2],
                data: "{Before:[],Items:[{Slot:0b,count:4,id:\"minecraft:raw_iron\"}]}".into()
            })
        );
        assert!(parse_effect("container 1 65").is_none());
        assert_eq!(
            parse_effect("slots {Before:[],Items:[{Slot:36b,count:1,id:\"minecraft:stick\"}]}"),
            Some(Effect::PlayerSlots {
                data: "{Before:[],Items:[{Slot:36b,count:1,id:\"minecraft:stick\"}]}".into()
            })
        );
        assert_eq!(
            parse_effect(
                "ender_chest {Before:[],Items:[{Slot:3b,count:2,id:\"minecraft:ender_pearl\"}]}"
            ),
            Some(Effect::EnderChest {
                data: "{Before:[],Items:[{Slot:3b,count:2,id:\"minecraft:ender_pearl\"}]}".into()
            })
        );
        assert_eq!(
            parse_effect("open_menu ender_chest {\"translate\":\"container.enderchest\"}"),
            Some(Effect::OpenMenu {
                kind: "ender_chest".into(),
                title: serde_json::json!({"translate": "container.enderchest"}),
            })
        );
        assert!(parse_effect("open_menu crafting").is_none());
        assert!(parse_effect("open_menu crafting {").is_none());
        assert_eq!(
            parse_effect("menu 0:55:45,1:101:45,2:8:72 8 94"),
            Some(Effect::Menu(MenuLayout {
                slots: vec![(0, 55, 45), (1, 101, 45), (2, 8, 72)],
                inventory: [8, 94],
            }))
        );
        assert_eq!(
            parse_effect("menu - 8 84"),
            Some(Effect::Menu(MenuLayout {
                slots: vec![],
                inventory: [8, 84],
            }))
        );
        assert!(parse_effect("menu 0:55 8 94").is_none());
        assert_eq!(
            parse_effect("data 1 64 -2 562 39 562 0 1000 0"),
            Some(Effect::Data {
                pos: [1, 64, -2],
                values: vec![562, 39, 562, 0, 1000, 0],
            })
        );
        assert_eq!(
            parse_effect("data 1 64 -2"),
            Some(Effect::Data {
                pos: [1, 64, -2],
                values: vec![],
            })
        );
        assert_eq!(
            parse_effect("placement mymod:timer[facing=south,lit=false]"),
            Some(Effect::Placement(Some(
                "mymod:timer[facing=south,lit=false]".into()
            )))
        );
    }

    #[test]
    fn java_answers_become_effects() {
        let answer = Return {
            call: 9,
            result: "success".into(),
            effects: vec![
                "attribute minecraft:max_health 22.0".into(),
                "health 22.0".into(),
                "hand main heart_crystals:heart_crystal 2".into(),
                "sound minecraft:entity.zombie_villager.cure 7 1.0 64.5 -2.0 0.5 2.0".into(),
                r#"overlay {"translate":"heart_crystals.limit_reached","color":"dark_red"}"#.into(),
                "tell {\"text\":\"hi\"}".into(),
                "explode 1 2 3".into(),
            ],
        };
        let done = outcome(answer).unwrap();
        assert_eq!(done.result, UseResult::Success);
        assert!(done.result.takes_use());
        assert_eq!(
            done.effects,
            [
                Effect::Attribute {
                    id: "minecraft:max_health".into(),
                    base: 22.0
                },
                Effect::Health(22.0),
                Effect::Hand {
                    hand: Hand::Main,
                    item: "heart_crystals:heart_crystal".into(),
                    count: 2
                },
                Effect::Sound {
                    id: "minecraft:entity.zombie_villager.cure".into(),
                    source: 7,
                    at: [1.0, 64.5, -2.0],
                    volume: 0.5,
                    pitch: 2.0
                },
                Effect::Overlay(serde_json::json!({
                    "translate": "heart_crystals.limit_reached",
                    "color": "dark_red"
                })),
                Effect::Tell(serde_json::json!({ "text": "hi" })),
            ]
        );
        let pass = |result: &str, effects: Vec<String>| {
            outcome(Return {
                call: 1,
                result: result.into(),
                effects,
            })
        };
        assert!(!pass("pass", Vec::new()).unwrap().result.takes_use());
        assert_eq!(pass("fail", Vec::new()).unwrap().result, UseResult::Fail);
        assert_eq!(
            pass("error", vec!["java.lang.NoSuchMethodError: x".into()]),
            Err("java.lang.NoSuchMethodError: x".into())
        );
        assert!(parse_effect("hand both a:b 1").is_none());
        assert!(parse_effect("health").is_none());
        // A hand's stack whole (its strings may hold spaces), and the player's experience.
        assert_eq!(
            parse_effect(
                r#"held main {components:{"minecraft:custom_name":"A b"},count:1,id:"minecraft:stick"}"#
            ),
            Some(Effect::Held {
                hand: Hand::Main,
                stack:
                    r#"{components:{"minecraft:custom_name":"A b"},count:1,id:"minecraft:stick"}"#
                        .into()
            })
        );
        assert_eq!(
            parse_effect("clock minecraft:overworld 13000"),
            Some(Effect::Clock {
                clock: "minecraft:overworld".to_owned(),
                ticks: 13000
            })
        );
        assert_eq!(parse_effect("clock minecraft:overworld noon"), None);
        assert_eq!(
            parse_effect("xp 9 0.2 140"),
            Some(Effect::Experience {
                level: 9,
                progress: 0.2,
                total: 140
            })
        );
        assert!(parse_effect("held both {}").is_none());
        // The player moved (D-231): a level and five finite numbers.
        assert_eq!(
            parse_effect("teleport minecraft:the_nether 1.5 70.0 -3.25 90.0 -10.0"),
            Some(Effect::Teleport {
                dimension: "minecraft:the_nether".into(),
                pos: [1.5, 70.0, -3.25],
                yaw: 90.0,
                pitch: -10.0
            })
        );
        assert_eq!(
            parse_effect("teleport minecraft:overworld 1 NaN 3 0 0"),
            None
        );
        assert_eq!(parse_effect("teleport overworld 1 2 3 0 0"), None);
        assert_eq!(parse_effect("teleport minecraft:overworld 1 2 3"), None);
        // A motion sent to the player (#220): three finite numbers.
        assert_eq!(
            parse_effect("motion 0.0 1.5 -0.25"),
            Some(Effect::Motion([0.0, 1.5, -0.25]))
        );
        assert!(parse_effect("motion 0 NaN 0").is_none());
        assert!(parse_effect("motion 0 1").is_none());
        // The off hand, and a sound with a word too many.
        assert_eq!(
            parse_effect("held off {}"),
            Some(Effect::Held {
                hand: Hand::Off,
                stack: "{}".into()
            })
        );
        assert_eq!(
            parse_effect("hand off minecraft:torch 3"),
            Some(Effect::Hand {
                hand: Hand::Off,
                item: "minecraft:torch".into(),
                count: 3
            })
        );
        assert!(parse_effect("sound a:b 7 1 2 3 0.5 2 3").is_none());
    }

    #[test]
    fn blocks_name_their_block_entities() {
        let announced = parse_blocks(&[
            "mymod:lamp use,step".into(),
            "mymod:counter use,entity,ticks mymod:counter_entity".into(),
            "mymod:crusher entity,ticks mymod:crusher 2;1;0;0;0;0;0;0;1".into(),
            "broken".into(),
        ]);
        assert_eq!(announced.blocks.len(), 3);
        assert!(announced.blocks["mymod:counter"].contains("ticks"));
        assert_eq!(announced.entities["mymod:counter"], "mymod:counter_entity");
        assert_eq!(
            announced.slots,
            BTreeMap::from([(
                "mymod:crusher".into(),
                Slots {
                    count: 2,
                    faces: [vec![1], vec![0], vec![0], vec![0], vec![0], vec![0]],
                    inputs: vec![0],
                    outputs: vec![1],
                }
            )])
        );
        assert_eq!(parse_slots("2;1;0"), None);
        assert_eq!(parse_slots("x;;;;;;;;"), None);
    }

    #[test]
    fn a_switch_typed_in_the_chat() {
        assert_eq!(
            switch_command("kiln off zoomly"),
            Some(("zoomly".into(), false))
        );
        assert_eq!(
            switch_command("kiln  on  showfps"),
            Some(("showfps".into(), true))
        );
        assert_eq!(switch_command("kiln off"), None);
        assert_eq!(switch_command("kiln off a b"), None);
        assert_eq!(switch_command("kiln reload"), None);
        assert_eq!(switch_command("kilnoff zoomly"), None);
        assert!(
            overlay_command("kiln")
                && overlay_command("kiln mods")
                && overlay_command(" kiln  mods ")
        );
        assert!(
            !overlay_command("kiln off zoomly")
                && !overlay_command("kiln mods x")
                && !overlay_command("kilns")
        );
    }

    #[test]
    fn the_overlay_lists_each_mod_with_its_switch_and_last_stop() {
        use std::io::Write;
        let dir = scratch("overlay").join("kiln");
        std::fs::create_dir_all(dir.join("mods")).unwrap();
        let jar = |name: &str, json: &str, mixins: bool| {
            let file = std::fs::File::create(dir.join("mods").join(name)).unwrap();
            let mut zip = zip::ZipWriter::new(file);
            let options = zip::write::SimpleFileOptions::default();
            zip.start_file("fabric.mod.json", options).unwrap();
            zip.write_all(json.as_bytes()).unwrap();
            if mixins {
                zip.start_file("m.mixins.json", options).unwrap();
                zip.write_all(b"{}").unwrap();
            }
            zip.finish().unwrap();
        };
        jar(
            "a.jar",
            r#"{"id": "zoomly", "name": "Zoomly", "version": "2.1.0", "entrypoints": {"client": ["z.Client"]}}"#,
            false,
        );
        jar(
            "b.jar",
            r#"{"id": "paintings", "name": "Spooky\tPaintings"}"#,
            false,
        );
        jar("c.jar", r#"{"id": "zoomly", "name": "Again"}"#, false);
        jar(
            "d.jar",
            r#"{"id": "tweak", "mixins": ["m.mixins.json"]}"#,
            true,
        );
        *LAST_STOP.lock().unwrap() = Some(("zoomly".into(), "stopped".into()));
        let rows = overlay_rows(&dir, &BTreeSet::from(["paintings".to_owned()]));
        let rows: Vec<Vec<&str>> = rows.iter().map(|r| r.split('\u{1f}').collect()).collect();
        assert_eq!(rows.len(), 3, "the same mod twice is one row: {rows:?}");
        // Joined a part at a time, as `jars` lists it: `mods/a.jar` would keep its slash on Windows.
        let a = dir.join("mods").join("a.jar");
        assert_eq!(
            rows[0],
            [
                "zoomly",
                "Zoomly",
                "2.1.0",
                &*a.to_string_lossy(),
                "code",
                "stopped",
                "0"
            ]
        );
        // A tab in a name is a space; a mod with no name shows its id; a data-only mod switched off.
        assert_eq!(rows[1][..3], ["paintings", "Spooky Paintings", ""]);
        assert_eq!(rows[1][4..], ["data", "", "1"]);
        assert_eq!(rows[2][1], "tweak");
        assert_eq!(rows[2][4], "mixins");
        *LAST_STOP.lock().unwrap() = None;

        // Before Java runs there is no overlay to open; nothing goes to Java.
        assert!(!java_running());
        assert!(!toggle_overlay(&dir, OVERLAY_KEY_DEFAULT));
        assert!(SCREEN.with(|m| m.borrow().commands.is_empty()));
        // What the overlay asks of the game, once.
        OVERLAY.with(|o| {
            let mut o = o.borrow_mut();
            o.switched.push(("zoomly".into(), false));
            o.open = Some(dir.join("mods"));
        });
        let asks = take_overlay_asks();
        assert_eq!(asks.switched, [("zoomly".to_owned(), false)]);
        assert_eq!(asks.open, Some(dir.join("mods")));
        let again = take_overlay_asks();
        assert!(again.switched.is_empty() && again.open.is_none());
    }

    #[test]
    fn each_java_mod_has_a_row_and_a_switch() {
        use std::io::Write;
        let dir = scratch("rows").join("kiln");
        std::fs::create_dir_all(dir.join("mods")).unwrap();
        let jar = |name: &str, json: &str| {
            let file = std::fs::File::create(dir.join("mods").join(name)).unwrap();
            let mut zip = zip::ZipWriter::new(file);
            let options = zip::write::SimpleFileOptions::default();
            zip.start_file("fabric.mod.json", options).unwrap();
            zip.write_all(json.as_bytes()).unwrap();
            zip.finish().unwrap();
        };
        jar(
            "a.jar",
            r#"{"id": "switch-a", "name": "Switch A", "version": "1.2", "description": "Shows A.",
                "entrypoints": {"client": ["a.Client"]}}"#,
        );
        jar("b.jar", r#"{"id": "switch_b"}"#);
        // The same mod twice is one row; a jar that isn't a mod has none.
        jar("c.jar", r#"{"id": "switch-a", "name": "Again"}"#);
        std::fs::write(dir.join("mods").join("broken.jar"), b"not a jar").unwrap();
        let rows = mod_rows(&dir);
        assert_eq!(rows.len(), 2, "{rows:?}");
        assert_eq!(rows[0].0, "kiln_switch-a");
        assert_eq!(rows[0].1, "Switch A 1.2");
        let a = "Shows A.\nA Java mod in kiln/mods, run by Kiln.";
        assert!(rows[0].2.starts_with(a), "{}", rows[0].2);
        assert_eq!(rows[1].0, "kiln_switch_b");
        assert_eq!(rows[1].1, "switch_b");
        assert!(rows[1].2.starts_with("Data only"), "{}", rows[1].2);

        // horizon-mods.txt's off switches that are Kiln's rows, by the mods' own ids.
        let list = "mod.coords_hud:true\nmod.kiln_switch-a:false\nmod.zoom:false\nkey.kiln_x.y:key.keyboard.j\n";
        assert_eq!(disabled_in_list(list), ["kiln_switch-a", "zoom"]);
        let off = off_ids(disabled_in_list(list));
        assert_eq!(off.iter().collect::<Vec<_>>(), ["switch-a"]);

        // A mod turned off is left out as if its jar weren't there; the jar Kiln can't read stays.
        assert!(set_off(off.clone()));
        assert!(!set_off(off));
        assert!(!mod_on("switch-a") && mod_on("switch_b"));
        let names: Vec<_> = mod_jars(&dir)
            .iter()
            .map(|p| p.file_name().unwrap().to_string_lossy().into_owned())
            .collect();
        assert_eq!(names, ["b.jar", "broken.jar"]);

        // Java started with A off; nothing changes, nothing restarts. Turning it on restarts Java off the world.
        STATE.with(|s| *s.borrow_mut() = State::Stopped("no Java mods".into()));
        STARTED_OFF.with(|o| *o.borrow_mut() = Some(["switch-a".to_owned()].into()));
        assert!(!restart_for_switches());
        assert!(set_off(BTreeSet::new()));
        assert!(restart_for_switches());
        assert!(STATE.with(|s| matches!(*s.borrow(), State::Idle)));
        assert!(!restart_for_switches());
    }

    #[test]
    fn jars_inside_jars_come_out_before_theirs() {
        use std::io::Write;
        let dir = std::env::temp_dir().join(format!("kiln-nested-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).unwrap();
        let jar = |entries: &[(&str, &[u8])]| -> Vec<u8> {
            let mut zip = zip::ZipWriter::new(std::io::Cursor::new(Vec::new()));
            for (name, bytes) in entries {
                zip.start_file(*name, zip::write::SimpleFileOptions::default())
                    .unwrap();
                zip.write_all(bytes).unwrap();
            }
            zip.finish().unwrap().into_inner()
        };
        let energy = jar(&[("fabric.mod.json", br#"{"id": "energy"}"#)]);
        let core = jar(&[
            (
                "fabric.mod.json",
                br#"{"id": "core", "jars": [{"file": "META-INF/jars/energy.jar"}]}"#,
            ),
            ("META-INF/jars/energy.jar", &energy),
        ]);
        let tech = jar(&[
            (
                "fabric.mod.json",
                br#"{"id": "tech", "jars": [{"file": "META-INF/jars/core.jar"}, {"file": "META-INF/jars/energy.jar"}]}"#,
            ),
            ("META-INF/jars/core.jar", &core),
            ("META-INF/jars/energy.jar", &energy),
        ]);
        std::fs::write(dir.join("tech.jar"), tech).unwrap();
        let cache = dir.join("cache");
        let found = nested_jars(&dir.join("tech.jar"), &cache);
        assert_eq!(
            found,
            [
                cache.join("energy.jar"),
                cache.join("core.jar"),
                cache.join("energy.jar")
            ]
        );
        assert_eq!(
            read_jar(&cache.join("core.jar")).unwrap().nested,
            ["META-INF/jars/energy.jar"]
        );
        assert!(nested_jars(&cache.join("energy.jar"), &cache).is_empty());
        // Every Java Kiln starts takes them so (D-101): the libraries before the mod, each once, from
        // kiln_dir/cache/jars.
        let kiln = dir.join("kiln");
        std::fs::create_dir_all(&kiln).unwrap();
        std::fs::copy(dir.join("tech.jar"), kiln.join("tech.jar")).unwrap();
        let in_kiln = kiln.join("cache").join("jars");
        assert_eq!(
            with_nested(&[kiln.join("tech.jar")], &kiln),
            [
                in_kiln.join("energy.jar"),
                in_kiln.join("core.jar"),
                kiln.join("tech.jar")
            ]
        );
        // A new version of the mod, its library changed: the cache's copy is the new one.
        let energy2 = jar(&[("fabric.mod.json", br#"{"id": "energy", "version": "2"}"#)]);
        let tech2 = jar(&[
            (
                "fabric.mod.json",
                br#"{"id": "tech", "jars": [{"file": "META-INF/jars/energy.jar"}]}"#,
            ),
            ("META-INF/jars/energy.jar", &energy2),
        ]);
        std::fs::write(dir.join("tech.jar"), tech2).unwrap();
        assert_eq!(
            nested_jars(&dir.join("tech.jar"), &cache),
            [cache.join("energy.jar")]
        );
        assert_eq!(std::fs::read(cache.join("energy.jar")).unwrap(), energy2);
        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn without_java_no_item_has_a_use() {
        let _alone = SERVER_TEST.lock().unwrap_or_else(|e| e.into_inner());
        assert!(!has_use("heart_crystals:heart_crystal"));
        assert!(!hears("join"));
        assert!(!block_does("mymod:lamp", "use"));
        let mut air = |_: &[String]| "minecraft:air".to_owned();
        assert_eq!(
            server_tick(Path::new("/w"), 0, TickPhase::Start, "", &mut air),
            None
        );
        assert_eq!(
            player_joined(Path::new("/w"), 0, &crystal_user(), &mut air),
            None
        );
        assert_eq!(
            use_item(
                Path::new("/w"),
                0,
                "heart_crystals:heart_crystal",
                Hand::Main,
                &crystal_user(),
                &mut air
            ),
            None
        );
    }

    #[test]
    fn status_counts_java_mods() {
        assert_eq!(status_line(None, None), "Kiln 0.4.8: no Java mods");
        assert_eq!(status_line(Some(0), None), "Kiln 0.4.8: no Java mods");
        assert_eq!(status_line(Some(1), None), "Kiln 0.4.8: 1 Java mod");
        assert_eq!(status_line(Some(3), None), "Kiln 0.4.8: 3 Java mods");
        assert_eq!(status_line(Some(3), Some("no Java")), "Kiln 0.4.8: no Java");
    }

    #[test]
    fn ticks_and_fonts_encode_for_java() {
        let s = Snapshot {
            connected: true,
            in_world: true,
            paused: false,
            game_time: 42,
            dimension: "minecraft:the_nether".into(),
            advancements: None,
            player: None,
            ..Snapshot::default()
        };
        assert_eq!(
            tick_fields(7, &s, (427, 240), 60),
            [
                "7",
                "1",
                "1",
                "0",
                "42",
                "minecraft:the_nether",
                "427",
                "240",
                "60"
            ]
        );
        // The window's player follows, as a server call carries a player.
        let with_player = Snapshot {
            player: Some(crystal_user()),
            ..s
        };
        let fields = tick_fields(9, &with_player, (427, 240), 60);
        let after = 9 + PLAYER_FIELDS;
        assert_eq!(fields.len(), after + 5);
        assert_eq!(fields[9..after], player_fields(&crystal_user())[..]);
        assert_eq!(fields[after..after + 3], ["", "0", "0"]);
        assert!(
            fields[after + 3].starts_with("single=0;day=0;"),
            "{}",
            fields[after + 3]
        );
        assert_eq!(fields[after + 4], "");
        // What they wear, with damage, and the window's F1 and F3.
        let dressed = Snapshot {
            equipment: vec![
                Worn {
                    slot: "head",
                    item: "minecraft:diamond_helmet".into(),
                    count: 1,
                    damage: 120,
                    max_damage: 363,
                },
                Worn {
                    slot: "mainhand",
                    item: "minecraft:torch".into(),
                    count: 12,
                    damage: 0,
                    max_damage: 0,
                },
            ],
            hud_hidden: true,
            ..with_player
        };
        let fields = tick_fields(10, &dressed, (427, 240), 60);
        assert_eq!(
            fields[after..after + 3],
            [
                "head minecraft:diamond_helmet 1 120 363;mainhand minecraft:torch 12 0 0",
                "1",
                "0"
            ]
        );
        // The inventory last, by slot.
        let mut carrying = dressed.clone();
        carrying.state.inventory = vec![
            (0, dressed.equipment[1].clone()),
            (9, dressed.equipment[0].clone()),
        ];
        assert_eq!(
            tick_fields(11, &carrying, (427, 240), 60)[after + 4],
            "0 minecraft:torch 12 0 0;9 minecraft:diamond_helmet 1 120 363"
        );
        let title = tick_fields(8, &Snapshot::default(), DEFAULT_GUI, 0);
        assert_eq!(title[1..6], ["0", "0", "0", "0", ""]);
        assert_eq!(font_field(&[0, 6, 4]), "0,6,4");
    }

    #[test]
    fn the_server_publishes_advancements_for_the_window() {
        let mut seen = 0;
        forget_server_advancements();
        assert_eq!(server_advancements(&mut seen), None);
        publish_server_advancements(vec!["minecraft:story/root".into()]);
        assert_eq!(
            server_advancements(&mut seen),
            Some(vec!["minecraft:story/root".into()])
        );
        assert_eq!(server_advancements(&mut seen), None);
        publish_server_advancements(vec!["a".into(), "b".into()]);
        assert_eq!(server_advancements(&mut seen).map(|m| m.len()), Some(2));
        // A new world: the window starts over at 0 and takes the next list, whatever its number; the last world's
        // isn't one.
        forget_server_advancements();
        seen = 0;
        assert_eq!(server_advancements(&mut seen), None);
        publish_server_advancements(Vec::new());
        assert_eq!(server_advancements(&mut seen), Some(Vec::new()));
    }

    #[test]
    fn the_running_note_restarts_the_game_as_it_was() {
        let text = running_text(
            Path::new("/games/horizon"),
            Path::new("/opt/horizon/horizon-client"),
            &["--world".into(), "New\nWorld".into()],
        );
        assert_eq!(
            text,
            "/games/horizon\n/opt/horizon/horizon-client\n--world\nNew World\n"
        );
        let command = watcher_command(Path::new("/jdk"), Path::new("/games/horizon/kiln"), 4242);
        let args: Vec<String> = command
            .get_args()
            .map(|a| a.to_string_lossy().into_owned())
            .collect();
        let tail = &args[args.len() - 5..];
        assert_eq!(tail[0], "-cp");
        assert_eq!(
            Path::new(&tail[1]),
            Path::new("/games/horizon/kiln/kiln-runtime.jar")
        );
        assert_eq!(
            &tail[2..],
            ["kiln.CrashWatch", "4242", "/games/horizon/kiln"]
        );
        assert!(args.iter().any(|a| a == "-Xmx64m"), "{args:?}");
    }

    #[test]
    fn the_breadcrumb_names_the_busy_mod_first() {
        // As kiln/java/kiln/Breadcrumb.java writes it: records padded with spaces, "-" for none.
        let record = |busy: u8, thread: &str, call: &str, m: &str| {
            format!("busy: {busy}\nthread: {thread}   \ncall: {call}      \nmod: {m}    \n")
        };
        let file = [
            record(0, "Kiln", "tick", "zoomly"),
            record(1, "Server thread", "server server_tick", "-"),
            record(1, "Kiln", "END_CLIENT_TICK", "kiln_test_crash"),
            record(0, "-", "-", "-"),
        ]
        .concat();
        assert_eq!(
            parse_last_call(&file),
            ("kiln_test_crash".into(), "END_CLIENT_TICK".into())
        );
        // Only Kiln's own call in progress: no mod, its call.
        let kiln_only = [
            record(1, "Server thread", "bridge tick", "-"),
            record(0, "Kiln", "x", "zoomly"),
        ]
        .concat();
        assert_eq!(
            parse_last_call(&kiln_only),
            (String::new(), "bridge tick".into())
        );
        assert_eq!(
            parse_last_call(&record(0, "Kiln", "tick", "zoomly")),
            (String::new(), String::new())
        );
        assert_eq!(parse_last_call(""), (String::new(), String::new()));
    }

    #[test]
    fn the_last_games_stop_is_asked_about_once() {
        let dir = scratch("last-crash");
        assert_eq!(take_last_crash(&dir), None);
        // The crash window's note.
        std::fs::write(
            dir.join("crashed"),
            "mod: zoomly\ncall: END_CLIENT_TICK\nwhy: stopped\n",
        )
        .unwrap();
        let crash = take_last_crash(&dir).unwrap();
        assert_eq!(
            crash,
            LastCrash {
                mod_id: "zoomly".into(),
                name: "zoomly".into(),
                call: "END_CLIENT_TICK".into(),
                stuck: false,
                tripped: String::new(),
            }
        );
        assert!(!dir.join("crashed").exists());
        assert_eq!(take_last_crash(&dir), None);
        // No window took it: `running` left, read with the breadcrumb.
        std::fs::write(dir.join("running"), "/games\n/games/horizon-client\n").unwrap();
        std::fs::write(
            dir.join(crate::game::kiln_jvm::LAST_CALL),
            "busy: 1\nthread: Server thread\ncall: START_SERVER_TICK\nmod: techreborn\n",
        )
        .unwrap();
        let crash = take_last_crash(&dir).unwrap();
        assert_eq!(
            (crash.mod_id.as_str(), crash.call.as_str()),
            ("techreborn", "START_SERVER_TICK")
        );
        assert!(!dir.join("running").exists());
        // The watchdog's note: stuck, not stopped.
        std::fs::write(dir.join("crashed"), "mod: hangs\ncall: -\nwhy: stuck\n").unwrap();
        let crash = take_last_crash(&dir).unwrap();
        assert!(crash.stuck && crash.call.is_empty(), "{crash:?}");
        // Java's own turning off (out of memory, out of stack): asked about too, and the dialog says why.
        std::fs::write(
            dir.join("crashed"),
            "mod: zoomly\ncall: END_CLIENT_TICK\nwhy: memory\n",
        )
        .unwrap();
        let crash = take_last_crash(&dir).unwrap();
        assert_eq!(crash.tripped, "memory");
        let dialog = crash_dialog(&crash, [427.0, 240.0], |t| 6.0 * t.chars().count() as f32);
        assert_eq!(
            dialog.lines[0].0,
            "zoomly ran Java out of memory in the last game"
        );
        assert_eq!(dialog.buttons[0].0, "Start without zoomly");
        assert_eq!(dialog.buttons[1].0, "Turn it back on");
        std::fs::write(dir.join("crashed"), "mod: zoomly\ncall: -\nwhy: stack\n").unwrap();
        let crash = take_last_crash(&dir).unwrap();
        assert_eq!(crash.tripped, "stack");
        assert!(
            crash_dialog(&crash, [427.0, 240.0], |_| 6.0).lines[0]
                .0
                .contains("overflowed Java's stack")
        );
        // A note with no mod but Kiln's call asks about safe mode (D-134); one with neither asks nothing.
        std::fs::write(dir.join("crashed"), "mod: -\ncall: tick\nwhy: stopped\n").unwrap();
        let crash = take_last_crash(&dir).unwrap();
        assert_eq!(
            (
                crash.mod_id.as_str(),
                crash.name.as_str(),
                crash.call.as_str()
            ),
            ("", "", "tick")
        );
        let dialog = crash_dialog(&crash, [427.0, 240.0], |t| 6.0 * t.chars().count() as f32);
        assert_eq!(
            dialog.lines[0].0,
            "The last game stopped in Kiln's call tick"
        );
        let answers: Vec<(&str, CrashChoice)> = dialog
            .buttons
            .iter()
            .map(|(l, _, c)| (l.as_str(), *c))
            .collect();
        assert_eq!(
            answers,
            [
                ("Safe mode", CrashChoice::Safe),
                ("Start as usual", CrashChoice::Keep)
            ]
        );
        std::fs::write(dir.join("crashed"), "mod: -\ncall: -\nwhy: stopped\n").unwrap();
        assert_eq!(take_last_crash(&dir), None);
        // A `running` left whose breadcrumb names something: the game writes the Crashes tab's line itself.
        let _ = std::fs::remove_file(dir.join(CRASHES_FILE));
        std::fs::write(dir.join("running"), "/games\n/games/horizon-client\n").unwrap();
        take_last_crash(&dir).unwrap();
        let list = std::fs::read_to_string(dir.join(CRASHES_FILE)).unwrap();
        let line: Vec<&str> = list.trim_end().split('\t').collect();
        assert_eq!(
            &line[1..],
            ["stopped", "techreborn", "START_SERVER_TICK", "-"],
            "{list}"
        );
    }

    #[test]
    fn safe_mode_starts_no_java() {
        let dir = scratch("safe-mode");
        std::fs::create_dir_all(dir.join("mods")).unwrap();
        std::fs::write(dir.join("mods/a.jar"), b"not read in safe mode").unwrap();
        // Chosen in the crash window: the file goes, and this game is in safe mode.
        std::fs::write(dir.join(SAFE_MODE_FILE), "on\n").unwrap();
        assert!(!safe_mode());
        std::thread::spawn(move || {
            take_safe_mode_file(&dir);
            assert!(safe_mode());
            assert!(!dir.join(SAFE_MODE_FILE).exists());
            let (state, chat) = start(&dir, None, &dir.join("java_home"), Vec::new);
            assert!(matches!(state, State::Stopped(ref why) if why.starts_with("safe mode")));
            assert!(chat[0].starts_with("Kiln: safe mode"), "{chat:?}");
        })
        .join()
        .unwrap();
    }

    #[test]
    fn the_crashes_list_keeps_the_last_fifty() {
        let dir = scratch("crashes-list");
        for i in 0..CRASHES_KEPT + 5 {
            record_crash(&dir, "stuck", &format!("mod{i}"), "a\tcall\n", "");
        }
        let list = std::fs::read_to_string(dir.join(CRASHES_FILE)).unwrap();
        let lines: Vec<&str> = list.lines().collect();
        assert_eq!(lines.len(), CRASHES_KEPT);
        assert!(
            lines[0].ends_with("\tstuck\tmod5\ta call\t-"),
            "{}",
            lines[0]
        );
        assert!(lines[CRASHES_KEPT - 1].contains(&format!("\tmod{}\t", CRASHES_KEPT + 4)));
        // Each crash keeps the log's tail as it was (D-191): none here, no log.
        assert!(!dir.join(CRASH_LOGS).exists());
    }

    #[test]
    fn each_crash_keeps_the_logs_last_lines_and_they_go_with_it() {
        let dir = scratch("crash-tails");
        let log = dir.join(crate::game::kiln_jvm::JAVA_LOG);
        let mut whens = Vec::new();
        for i in 0..CRASHES_KEPT + 3 {
            // A log longer than the tail, its last line naming this crash.
            let text: String = (0..CRASH_TAIL + 20)
                .map(|n| format!("crash {i} line {n}\n"))
                .collect();
            std::fs::write(&log, text).unwrap();
            record_crash(&dir, "stopped", &format!("mod{i}"), "tick", "");
            let list = std::fs::read_to_string(dir.join(CRASHES_FILE)).unwrap();
            whens.push(
                list.lines()
                    .last()
                    .unwrap()
                    .split('\t')
                    .next()
                    .unwrap()
                    .to_owned(),
            );
        }
        // Each one a file of its own, all in the same millisecond or not.
        assert_eq!(whens.iter().collect::<BTreeSet<_>>().len(), whens.len());
        let last = std::fs::read_to_string(
            dir.join(CRASH_LOGS)
                .join(format!("{}.log", whens.last().unwrap())),
        )
        .unwrap();
        let lines: Vec<&str> = last.lines().collect();
        assert_eq!(lines.len(), CRASH_TAIL);
        assert_eq!(lines[0], format!("crash {} line 20", CRASHES_KEPT + 2));
        assert_eq!(
            lines[CRASH_TAIL - 1],
            format!("crash {} line {}", CRASHES_KEPT + 2, CRASH_TAIL + 19)
        );
        // The tails of crashes no longer listed go.
        assert_eq!(
            std::fs::read_dir(dir.join(CRASH_LOGS)).unwrap().count(),
            CRASHES_KEPT
        );
        assert!(
            !dir.join(CRASH_LOGS)
                .join(format!("{}.log", whens[2]))
                .exists()
        );
        assert!(
            dir.join(CRASH_LOGS)
                .join(format!("{}.log", whens[3]))
                .exists()
        );
    }

    #[test]
    fn a_logs_tail_is_read_from_its_end() {
        let dir = scratch("log-tail");
        let log = dir.join("big.log");
        // 200 KiB of lines: only the last 64 KiB are read, the cut line left out.
        let text: String = (0..20_000).map(|n| format!("line {n:05}\n")).collect();
        std::fs::write(&log, &text).unwrap();
        let tail = log_tail(&log, 3);
        assert_eq!(tail, "line 19997\nline 19998\nline 19999\n");
        assert_eq!(log_tail(&log, 100_000).lines().count(), (64 << 10) / 11);
        assert_eq!(log_tail(&dir.join("none.log"), 3), "");
    }

    #[test]
    fn the_title_screens_question_takes_a_click_only_while_it_shows() {
        let crash = LastCrash {
            mod_id: "zoomly".into(),
            name: "Zoomly".into(),
            call: "END_CLIENT_TICK".into(),
            stuck: false,
            tripped: String::new(),
        };
        let dialog = crash_dialog(&crash, [427.0, 240.0], |t| 6.0 * t.chars().count() as f32);
        assert_eq!(dialog.lines[0].0, "Zoomly stopped the last game");
        assert_eq!(dialog.buttons[0].0, "Start without Zoomly");
        let choices: Vec<CrashChoice> = dialog.buttons.iter().map(|b| b.2).collect();
        assert_eq!(
            choices,
            [CrashChoice::Without, CrashChoice::Keep, CrashChoice::Safe]
        );
        let [px, py, pw, ph] = dialog.panel;
        for (_, [x, y, w, h], _) in &dialog.buttons {
            assert!(
                *x >= px && x + w <= px + pw && *y >= py && y + h <= py + ph,
                "{dialog:?}"
            );
        }
        let centre = |r: [f32; 4]| [r[0] + r[2] / 2.0, r[1] + r[3] / 2.0];
        *QUESTION.lock().unwrap() = Some(crash.clone());
        // Not drawn yet: the title screen keeps its clicks.
        *QUESTION_DRAWN.lock().unwrap() = None;
        assert!(!crash_click(&dialog, centre(dialog.buttons[0].1)));
        crash_question_drawn(&dialog);
        // Beside the buttons: the question's, no answer.
        assert!(crash_click(&dialog, [px + 1.0, py + 1.0]));
        assert_eq!(take_crash_answer(), None);
        assert!(crash_click(&dialog, centre(dialog.buttons[0].1)));
        assert_eq!(
            take_crash_answer(),
            Some(("zoomly".into(), CrashChoice::Without))
        );
        assert_eq!(crash_question(), None);
        assert!(
            !crash_click(&dialog, centre(dialog.buttons[1].1)),
            "answered: the screen's again"
        );
    }

    #[test]
    fn a_mod_java_turned_off_is_turned_back_on_by_the_answer() {
        let crash = LastCrash {
            mod_id: "zoomly".into(),
            name: "Zoomly".into(),
            call: String::new(),
            stuck: false,
            tripped: "stack".into(),
        };
        let dialog = crash_dialog(&crash, [427.0, 240.0], |t| 6.0 * t.chars().count() as f32);
        *QUESTION.lock().unwrap() = Some(crash);
        crash_question_drawn(&dialog);
        let _ = take_turn_on();
        assert!(crash_click(
            &dialog,
            [dialog.buttons[1].1[0] + 1.0, dialog.buttons[1].1[1] + 1.0]
        ));
        assert_eq!(
            take_crash_answer(),
            Some(("zoomly".into(), CrashChoice::Keep))
        );
        assert_eq!(take_turn_on(), ["zoomly".to_owned()]);
        let game = scratch("turn-on");
        std::fs::write(
            game.join("horizon-mods.txt"),
            "mod.sponge:true\nmod.kiln_zoomly:false\n",
        )
        .unwrap();
        turn_on_in_list(&game, "zoomly").unwrap();
        let text = std::fs::read_to_string(game.join("horizon-mods.txt")).unwrap();
        assert_eq!(text, "mod.sponge:true\nmod.kiln_zoomly:true\n");
        assert!(off_ids(disabled_in_list(&text)).is_empty());
    }

    #[test]
    fn a_mod_started_without_is_off_in_the_games_list() {
        let game = scratch("turn-off");
        std::fs::write(
            game.join("horizon-mods.txt"),
            "mod.sponge:true\nmod.kiln_zoomly:true\n",
        )
        .unwrap();
        turn_off_in_list(&game, "zoomly").unwrap();
        let text = std::fs::read_to_string(game.join("horizon-mods.txt")).unwrap();
        assert_eq!(text, "mod.sponge:true\nmod.kiln_zoomly:false\n");
        assert_eq!(
            off_ids(disabled_in_list(&text)),
            BTreeSet::from(["zoomly".to_owned()])
        );
        let empty = scratch("turn-off-new");
        turn_off_in_list(&empty, "x").unwrap();
        assert_eq!(
            std::fs::read_to_string(empty.join("horizon-mods.txt")).unwrap(),
            "mod.kiln_x:false\n"
        );
    }

    #[test]
    fn with_no_mods_kiln_makes_its_folder_and_names_it() {
        let dir = scratch("empty").join("kiln");
        let (state, chat) = start(&dir, None, &dir.join("java_home"), Vec::new);
        assert!(matches!(state, State::Stopped(ref why) if why == "no Java mods"));
        assert!(dir.join("mods").is_dir());
        assert_eq!(chat.len(), 1);
        assert!(
            chat[0].contains(&dir.join("mods").display().to_string()),
            "{chat:?}"
        );
    }

    #[test]
    fn manifest_gives_id_name_and_entrypoints() {
        let m = read_manifest(
            r#"{"schemaVersion": 1, "id": "timer", "version": "0.1.0", "name": "Timer",
                "entrypoints": {"main": ["a.Main"], "client": ["a.Client", {"value": "a.Other"}]}}"#,
        )
        .unwrap();
        assert_eq!(m.id, "timer");
        assert_eq!(m.name, "Timer");
        assert_eq!(m.version, "0.1.0");
        assert_eq!(m.main, ["a.Main"]);
        assert_eq!(m.client, ["a.Client", "a.Other"]);
        assert_eq!(m.entrypoints(), 3);
        let bare = read_manifest(r#"{"id": "bare"}"#).unwrap();
        assert_eq!(
            (bare.name.as_str(), bare.entrypoints(), bare.mixins),
            ("bare", 0, false)
        );
        assert!(!m.mixins);
        let mixins = read_manifest(r#"{"id": "hud", "mixins": ["hud.mixins.json"]}"#).unwrap();
        assert_eq!((mixins.entrypoints(), mixins.mixins), (0, true));
        assert!(read_manifest(r#"{"name": "no id"}"#).is_err());
        assert!(read_manifest("not json").is_err());
    }

    #[test]
    fn the_frame_rate_is_the_frames_of_the_last_second() {
        let start = Instant::now();
        let mut rate = FrameRate::default();
        // 60 frames a second: the 60th lands at 0.983 s, the 61st after the second is over.
        for i in 0..60 {
            rate.frame(start + Duration::from_micros(i * 16_667));
        }
        assert_eq!(rate.fps(), 0, "not a whole second yet");
        rate.frame(start + Duration::from_millis(1001));
        assert_eq!(rate.fps(), 61);
        // Then about 30 a second.
        for i in 1..30 {
            rate.frame(start + Duration::from_millis(1001 + i * 34));
        }
        assert_eq!(rate.fps(), 61, "the next second isn't over");
        rate.frame(start + Duration::from_millis(2010));
        assert_eq!(rate.fps(), 30);
    }

    #[test]
    fn kiln_dir_sits_beside_options() {
        let data = Path::new("/games/horizon/horizon-mods/.horizon-mod-data/kiln");
        assert_eq!(kiln_dir(data), Path::new("/games/horizon/kiln"));
    }

    #[test]
    fn the_status_box_says_what_stopped_kiln() {
        // Before Kiln starts, its name; stopped by trouble, why, in red. (Running, java_mods.rs.)
        assert_eq!(status_lines(), [(status_line(None, None), INK)]);
        let game = scratch("status");
        std::fs::create_dir_all(game.join("kiln/mods")).unwrap();
        std::fs::write(game.join("kiln/mods/a.jar"), b"").unwrap();
        tick(
            &game.join("horizon-mods/.horizon-mod-data/kiln"),
            || None,
            Vec::new,
            Snapshot::default(),
        );
        assert_eq!(
            status_lines(),
            [(status_line(None, Some("kiln-runtime.jar missing")), TROUBLE)]
        );
    }

    #[test]
    fn without_java_mods_the_status_box_stays_away() {
        let game = scratch("status-none");
        tick(
            &game.join("horizon-mods/.horizon-mod-data/kiln"),
            || None,
            Vec::new,
            Snapshot::default(),
        );
        assert_eq!(status_lines(), []);
    }

    #[test]
    fn a_data_only_mod_is_no_java_mod() {
        use std::io::Write;
        let dir = scratch("data-only").join("kiln");
        std::fs::create_dir_all(dir.join("mods")).unwrap();
        std::fs::write(dir.join("kiln-runtime.jar"), b"").unwrap();
        let mut zip =
            zip::ZipWriter::new(std::fs::File::create(dir.join("mods/ships.jar")).unwrap());
        zip.start_file("fabric.mod.json", zip::write::SimpleFileOptions::default())
            .unwrap();
        zip.write_all(br#"{"id": "ships"}"#).unwrap();
        zip.finish().unwrap();
        // Its structures go to the game (structures.rs); Java isn't looked for, let alone started.
        let (state, _) = start(
            &dir,
            Some("/no/java/here".into()),
            &dir.join("java_home"),
            Vec::new,
        );
        assert!(matches!(state, State::Stopped(ref why) if why == "no Java mods"));
    }

    #[test]
    fn a_mods_keys_go_on_its_page_and_the_key_chosen_there_goes_to_java() {
        use std::io::Write;
        let dir = scratch("mod-keys").join("kiln");
        std::fs::create_dir_all(dir.join("mods")).unwrap();
        let manifest = r#"{"id": "fovtoggle", "name": "FOV Toggle", "entrypoints": {"client": ["fov.Client"]}}"#;
        let mut zip = zip::ZipWriter::new(std::fs::File::create(dir.join("mods/fov.jar")).unwrap());
        zip.start_file("fabric.mod.json", zip::write::SimpleFileOptions::default())
            .unwrap();
        zip.write_all(manifest.as_bytes()).unwrap();
        zip.finish().unwrap();
        let fov = read_manifest(manifest).unwrap();
        let other =
            read_manifest(r#"{"id": "other", "entrypoints": {"main": ["o.Main"]}}"#).unwrap();
        let mods = [&fov, &other];
        let key = |name: &str, group: &str, label: &str, default: &str| {
            [
                name.to_owned(),
                group.to_owned(),
                label.to_owned(),
                default.to_owned(),
            ]
        };
        let toggle = key(
            "key.fovtoggle.toggle",
            "FOV Toggle",
            "Toggle FOV",
            "key.keyboard.v",
        );
        let zoom = key(
            "key.fovtoggle.zoom",
            "Camera",
            "Zoom",
            "key.keyboard.unknown",
        );

        // Java names the entrypoint that registered them: its mod's. One with none says so; a class no mod has, nothing.
        assert_eq!(
            keys_by_row(&mods, "fov.Client", vec![toggle.clone()]),
            [("kiln_fovtoggle".to_owned(), vec![toggle.clone()])]
        );
        assert_eq!(
            keys_by_row(&mods, "o.Main", Vec::new()),
            [("kiln_other".to_owned(), Vec::new())]
        );
        assert!(keys_by_row(&mods, "nobody.Main", vec![toggle.clone()]).is_empty());
        // Registered later, with no entrypoint: by the mod id in the key's name.
        let later = key("key.other.jump", "Other", "Jump", "key.keyboard.j");
        let stray = key("key.unknown_mod.x", "?", "X", "key.keyboard.x");
        assert_eq!(
            keys_by_row(&mods, "", vec![zoom.clone(), later.clone(), stray]),
            [
                ("kiln_fovtoggle".to_owned(), vec![zoom.clone()]),
                ("kiln_other".to_owned(), vec![later])
            ]
        );

        // Java started: what it says replaces what was kept from before, and is kept for the next game.
        *mod_keys_lock() = ModKeys::default();
        learn_keys(&dir, "kiln_fovtoggle", vec![toggle.clone()]);
        assert_eq!(
            page_keys("kiln_fovtoggle"),
            [[
                "key.fovtoggle.toggle".to_owned(),
                "Toggle FOV".to_owned(),
                "key.keyboard.v".to_owned()
            ]]
        );
        assert_eq!(
            mod_keys(),
            [(
                "kiln_fovtoggle".to_owned(),
                "key.fovtoggle.toggle".to_owned(),
                "key.keyboard.v".to_owned()
            )]
        );
        // The game's pages take what changed once.
        let changed = take_changed_keys().unwrap();
        assert_eq!(changed["kiln_fovtoggle"], page_keys("kiln_fovtoggle"));
        assert_eq!(take_changed_keys(), None);
        // A key in another group, from the same Java: added, and each label after its group's name.
        learn_keys(&dir, "kiln_fovtoggle", vec![zoom.clone()]);
        assert_eq!(
            page_keys("kiln_fovtoggle"),
            [
                [
                    "key.fovtoggle.toggle".to_owned(),
                    "FOV Toggle: Toggle FOV".to_owned(),
                    "key.keyboard.v".to_owned()
                ],
                [
                    "key.fovtoggle.zoom".to_owned(),
                    "Camera: Zoom".to_owned(),
                    "key.keyboard.unknown".to_owned()
                ],
            ]
        );

        // The next game lists them from what Kiln kept, before Java starts.
        *mod_keys_lock() = ModKeys::default();
        let rows = mod_rows(&dir);
        assert_eq!(rows.len(), 1);
        assert_eq!(rows[0].0, "kiln_fovtoggle");
        assert_eq!(rows[0].3.len(), 2);
        assert_eq!(rows[0].3[1][0], "key.fovtoggle.zoom");
        // A new version of the mod dropped the zoom: its Java's first word replaces what was kept.
        learn_keys(&dir, "kiln_fovtoggle", vec![toggle]);
        *mod_keys_lock() = ModKeys::default();
        assert_eq!(mod_rows(&dir)[0].3.len(), 1);
        assert_eq!(page_keys("kiln_fovtoggle")[0][1], "Toggle FOV");
        // An entrypoint with no keys at all: the mod has none, and its page none.
        learn_keys(&dir, "kiln_fovtoggle", Vec::new());
        assert!(page_keys("kiln_fovtoggle").is_empty());
        assert_eq!(
            take_changed_keys().unwrap()["kiln_fovtoggle"],
            Vec::<[String; 3]>::new()
        );

        // The player chose B on the page: Java hears it once, before the presses, and again only when it changes.
        let mut bound = BTreeMap::new();
        let now: BTreeMap<String, String> = [(
            "key.fovtoggle.toggle".to_owned(),
            "key.keyboard.b".to_owned(),
        )]
        .into();
        assert_eq!(
            rebinds(&mut bound, &now),
            [(
                "key.fovtoggle.toggle".to_owned(),
                "key.keyboard.b".to_owned()
            )]
        );
        assert!(rebinds(&mut bound, &now).is_empty());
        // A mod set its keys itself: the game keeps each (the last word on a key wins), forgets one set back to the
        // mod's own, and keeps none for a key of no mod's page.
        learn_keys(
            &dir,
            "kiln_fovtoggle",
            vec![key(
                "key.fovtoggle.toggle",
                "FOV Toggle",
                "Toggle FOV",
                "key.keyboard.v",
            )],
        );
        key_chosen("key.fovtoggle.toggle".into(), "key.keyboard.n".into());
        key_chosen("key.fovtoggle.toggle".into(), "key.keyboard.b".into());
        key_chosen("key.jump".into(), "key.keyboard.j".into());
        assert_eq!(
            take_chosen_keys(),
            [(
                "kiln_fovtoggle".to_owned(),
                "key.fovtoggle.toggle".to_owned(),
                Some("key.keyboard.b".to_owned())
            )]
        );
        assert!(take_chosen_keys().is_empty());
        key_chosen("key.fovtoggle.toggle".into(), "key.keyboard.v".into());
        assert_eq!(
            take_chosen_keys(),
            [(
                "kiln_fovtoggle".to_owned(),
                "key.fovtoggle.toggle".to_owned(),
                None
            )]
        );
        let back: BTreeMap<String, String> = [(
            "key.fovtoggle.toggle".to_owned(),
            "key.keyboard.v".to_owned(),
        )]
        .into();
        assert_eq!(
            rebinds(&mut bound, &back),
            [(
                "key.fovtoggle.toggle".to_owned(),
                "key.keyboard.v".to_owned()
            )]
        );
        *mod_keys_lock() = ModKeys::default();
    }

    #[test]
    fn a_key_with_modifiers_shows_as_its_combination_and_reads_back() {
        let dir = scratch("combos").join("kiln");
        let label = |key: &str| key.rsplit('.').next().unwrap_or_default().to_uppercase();
        // Java's combinations, in their order, and the key's name alone of a mapping without modifiers.
        assert_eq!(
            split_combo("control+key.keyboard.o"),
            Some((vec!["control"], "key.keyboard.o"))
        );
        assert_eq!(
            split_combo("+key.keyboard.r"),
            Some((vec![], "key.keyboard.r"))
        );
        assert_eq!(split_combo("key.keyboard.r"), None);
        assert_eq!(split_combo("key.keyboard.keypad.add"), None);
        assert_eq!(split_combo("hyper+key.keyboard.o"), None);
        assert_eq!(
            combo(&["shift", "control"], "key.keyboard.o"),
            "control+shift+key.keyboard.o"
        );
        assert_eq!(combo(&[], "key.keyboard.r"), "+key.keyboard.r");
        // The page shows them as JEI's Controls row does, and a key's name as it is.
        assert_eq!(
            combo_shown(&dir, "control+key.keyboard.o", &label),
            "CTRL + O"
        );
        assert_eq!(
            combo_shown(&dir, "control+shift+alt+command+key.keyboard.f", &label),
            "CMD + CTRL + SHIFT + ALT + F"
        );
        assert_eq!(
            combo_shown(&dir, "+key.keyboard.r", &label),
            "key.keyboard.r"
        );
        assert_eq!(
            combo_shown(&dir, "key.keyboard.k", &label),
            "key.keyboard.k"
        );
        assert!(
            std::fs::read_to_string(dir.join("cache/combos.json"))
                .unwrap()
                .contains("control+key.keyboard.o")
        );
        // What the game keeps reads back for Java: the default, a shown combination, a key chosen alone (no modifiers),
        // a combination Kiln never showed but whose words read, and a mapping without modifiers as it is.
        let default = "control+key.keyboard.o";
        assert_eq!(combo_chosen(&dir, default, default), default);
        assert_eq!(
            combo_chosen(&dir, "CTRL + O", default),
            "control+key.keyboard.o"
        );
        assert_eq!(
            combo_chosen(&dir, "key.keyboard.p", default),
            "+key.keyboard.p"
        );
        assert_eq!(
            combo_chosen(&dir, "SHIFT + ALT + Q", default),
            "shift+alt+key.keyboard.q"
        );
        assert_eq!(combo_chosen(&dir, "who knows", default), default);
        assert_eq!(
            combo_chosen(&dir, "key.keyboard.p", "key.keyboard.k"),
            "key.keyboard.p"
        );
        // A shown label kept for a key whose name is long (a layout's or the language's) reads back by what Kiln kept.
        assert_eq!(
            combo_shown(&dir, "alt+key.keyboard.left", &|_| "Left Arrow".into()),
            "ALT + Left Arrow"
        );
        assert_eq!(
            combo_chosen(&dir, "ALT + Left Arrow", default),
            "alt+key.keyboard.left"
        );
        assert!(
            is_modifier_key("key.keyboard.left.control") && !is_modifier_key("key.keyboard.left")
        );
        // The page's choice: the first look is what the game read, a change after it is the player's.
        assert!(!fresh_choice("kiln_jei", "key.jei.toggleOverlay", None));
        assert!(!fresh_choice("kiln_jei", "key.jei.toggleOverlay", None));
        assert!(fresh_choice(
            "kiln_jei",
            "key.jei.toggleOverlay",
            Some("key.keyboard.p")
        ));
        assert!(!fresh_choice(
            "kiln_jei",
            "key.jei.toggleOverlay",
            Some("key.keyboard.p")
        ));
    }

    #[test]
    fn held_keys_are_the_ones_down() {
        key_held("key.keyboard.space", true);
        key_held("key.keyboard.a", true);
        key_held("key.keyboard.space", false);
        assert_eq!(held_keys(), ["key.keyboard.a"]);
    }

    #[test]
    fn a_blocks_menu_is_learned_once() {
        let press = MenuLayout {
            slots: vec![(0, 56, 17)],
            inventory: [8, 84],
        };
        assert_eq!(block_menu("test:press"), None);
        assert!(set_block_menu("test:press", press.clone()));
        assert!(!set_block_menu("test:press", press.clone()));
        assert_eq!(block_menu("test:press"), Some(press));
        // A new version of the mod lays it out anew.
        let moved = MenuLayout {
            slots: vec![(0, 60, 17)],
            inventory: [8, 84],
        };
        assert!(set_block_menu("test:press", moved.clone()));
        assert_eq!(block_menu("test:press"), Some(moved));
    }

    #[test]
    fn release_files_give_the_major_version() {
        assert_eq!(
            release_major("IMPLEMENTOR=\"x\"\nJAVA_VERSION=\"25.0.2\"\n"),
            Some(25)
        );
        assert_eq!(release_major("JAVA_VERSION=\"21\""), Some(21));
        assert_eq!(release_major("JAVA_VERSION=\"1.8.0_402\""), Some(8));
        assert_eq!(release_major("NOTHING=1"), None);
    }

    #[test]
    fn jars_are_found_by_extension_in_name_order() {
        let dir = scratch("jars");
        for name in ["b.jar", "a.JAR", "notes.txt"] {
            std::fs::write(dir.join(name), b"").unwrap();
        }
        let names: Vec<_> = jars(&dir)
            .iter()
            .map(|p| p.file_name().unwrap().to_owned())
            .collect();
        assert_eq!(names, ["a.JAR", "b.jar"]);
        assert!(jars(&dir.join("missing")).is_empty());
    }

    #[test]
    fn the_newest_java_wins_unless_one_is_chosen() {
        let root = scratch("java");
        let (old, new, chosen) = (root.join("old"), root.join("new"), root.join("chosen"));
        fake_java(&old, "17.0.1");
        fake_java(&new, "25.0.2");
        fake_java(&chosen, "21.0.7");
        let env_old = |k: &str| (k == "JAVA_HOME").then(|| old.to_string_lossy().into_owned());
        let nobody = Some(Path::new("/nonexistent-home"));
        // Java 17 alone is too old; the system's JDKs may still be found, so only check it isn't 17.
        if let Ok(found) = find_java(None, &env_old, nobody, 0) {
            assert_ne!(found, old);
        }
        let env_new = |k: &str| (k == "JAVA_HOME").then(|| new.to_string_lossy().into_owned());
        let found = find_java(None, &env_new, nobody, 0).unwrap();
        let major = release_major(&std::fs::read_to_string(found.join("release")).unwrap());
        assert!(major.unwrap() >= 25);
        let chosen_path = chosen.to_string_lossy().into_owned();
        assert_eq!(
            find_java(Some(&chosen_path), &env_new, nobody, 0).unwrap(),
            chosen
        );
        assert!(find_java(Some("/no/java/here"), &env_new, nobody, 0).is_err());
        // An empty setting chooses nothing.
        assert!(find_java(Some(" "), &env_new, nobody, 0).is_ok());
        // Mods built for a newer Java than any installed: Kiln says which it needs.
        let none = find_java(None, &env_new, nobody, 99).unwrap_err();
        assert_eq!(none.to_string(), "no Java 99 or newer found");
    }

    /// A file system's folders as their paths, for [`java_homes`]: a folder's children are the listed paths one name
    /// below it (`os`'s separator).
    fn listing(os: kiln_jni::Os, folders: &[&str]) -> impl Fn(&Path) -> Vec<PathBuf> {
        let separator = if os == kiln_jni::Os::Windows {
            '\\'
        } else {
            '/'
        };
        let folders: Vec<String> = folders.iter().map(|f| f.to_string()).collect();
        move |dir: &Path| {
            let dir = dir.to_string_lossy().into_owned();
            folders
                .iter()
                .filter(|f| {
                    f.strip_prefix(&dir)
                        .and_then(|rest| rest.strip_prefix(separator))
                        .is_some_and(|name| !name.is_empty() && !name.contains(separator))
                })
                .map(PathBuf::from)
                .collect()
        }
    }

    fn has(homes: &[PathBuf], home: &str) -> bool {
        homes.iter().any(|h| h == Path::new(home))
    }

    #[test]
    fn windows_java_homes_are_the_launchers_runtimes_and_the_vendors_folders() {
        use kiln_jni::Os::Windows;
        let env = |k: &str| match k {
            "APPDATA" => Some(r"C:\Users\Никита\AppData\Roaming".to_owned()),
            "LOCALAPPDATA" => Some(r"C:\Users\Никита\AppData\Local".to_owned()),
            "ProgramFiles" => Some(r"C:\Program Files".to_owned()),
            "ProgramFiles(x86)" => Some(r"C:\Program Files (x86)".to_owned()),
            // Set but empty: no Java home.
            "JAVA_HOME" => Some(String::new()),
            _ => None,
        };
        let store = r"C:\Users\Никита\AppData\Local\Packages\Microsoft.4297127D64EC6_8wekyb3d8bbwe\LocalCache\Local\runtime";
        let folders = [
            r"C:\Users\Никита\AppData\Roaming\.minecraft\runtime\java-runtime-delta",
            r"C:\Users\Никита\AppData\Roaming\.minecraft\runtime\java-runtime-delta\windows-x64",
            r"C:\Users\Никита\AppData\Roaming\.minecraft\runtime\java-runtime-delta\windows-x64\java-runtime-delta",
            &format!(r"{store}\java-runtime-gamma"),
            &format!(r"{store}\java-runtime-gamma\windows-x64"),
            &format!(r"{store}\java-runtime-gamma\windows-x64\java-runtime-gamma"),
            r"C:\Program Files (x86)\Minecraft Launcher\runtime\java-runtime-delta",
            r"C:\Program Files (x86)\Minecraft Launcher\runtime\java-runtime-delta\windows-x64",
            r"C:\Program Files (x86)\Minecraft Launcher\runtime\java-runtime-delta\windows-x64\java-runtime-delta",
            r"C:\Program Files\Eclipse Adoptium\jdk-21.0.5.11-hotspot",
            r"C:\Program Files\Microsoft\jdk-21.0.6.7-hotspot",
            r"C:\Program Files\Other\jdk-99",
        ];
        let homes = java_homes(
            Windows,
            &env,
            Some(Path::new(r"C:\Users\Никита")),
            &listing(Windows, &folders),
        );
        for home in [
            r"C:\Users\Никита\AppData\Roaming\.minecraft\runtime\java-runtime-delta\windows-x64\java-runtime-delta",
            &format!(r"{store}\java-runtime-gamma\windows-x64\java-runtime-gamma"),
            r"C:\Program Files (x86)\Minecraft Launcher\runtime\java-runtime-delta\windows-x64\java-runtime-delta",
            r"C:\Program Files\Eclipse Adoptium\jdk-21.0.5.11-hotspot",
            r"C:\Program Files\Microsoft\jdk-21.0.6.7-hotspot",
        ] {
            assert!(has(&homes, home), "{home} not in {homes:#?}");
        }
        assert!(!has(&homes, r"C:\Program Files\Other\jdk-99"), "{homes:#?}");
        assert!(
            !homes
                .iter()
                .any(|h| h.as_os_str().is_empty() || h.to_string_lossy().contains('/')),
            "{homes:#?}"
        );
        assert_eq!(
            crate::game::kiln_jvm::java_program_on(
                Windows,
                Path::new(r"C:\Program Files\Java\jdk-21")
            ),
            Path::new(r"C:\Program Files\Java\jdk-21\bin\java.exe")
        );
    }

    #[test]
    fn macos_java_homes_are_bundles_contents_home() {
        use kiln_jni::Os::MacOs;
        let env = |k: &str| (k == "JAVA_HOME").then(|| "/Users/a/jdks/zulu-21.jdk".to_owned());
        let runtime = "/Users/a/Library/Application Support/minecraft/runtime";
        let folders = [
            &format!("{runtime}/java-runtime-delta"),
            &format!("{runtime}/java-runtime-delta/mac-os-arm64"),
            &format!("{runtime}/java-runtime-delta/mac-os-arm64/java-runtime-delta"),
            "/Library/Java/JavaVirtualMachines/temurin-21.jdk",
            "/Users/a/Library/Java/JavaVirtualMachines/corretto-21.0.5",
            "/opt/homebrew/opt/openjdk@21",
            "/opt/homebrew/opt/openssl@3",
            "/usr/local/opt/openjdk",
        ];
        let homes = java_homes(
            MacOs,
            &env,
            Some(Path::new("/Users/a")),
            &listing(MacOs, &folders),
        );
        for home in [
            "/Users/a/jdks/zulu-21.jdk",
            "/Users/a/jdks/zulu-21.jdk/Contents/Home",
            &format!(
                "{runtime}/java-runtime-delta/mac-os-arm64/java-runtime-delta/jre.bundle/Contents/Home"
            ),
            "/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home",
            "/Users/a/Library/Java/JavaVirtualMachines/corretto-21.0.5/Contents/Home",
            "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home",
            "/usr/local/opt/openjdk/libexec/openjdk.jdk/Contents/Home",
        ] {
            assert!(has(&homes, home), "{home} not in {homes:#?}");
        }
        assert!(
            !homes
                .iter()
                .any(|h| h.starts_with("/opt/homebrew/opt/openssl@3")),
            "{homes:#?}"
        );
    }

    #[test]
    fn linux_java_homes_are_the_launchers_runtimes_and_usr_lib_jvm() {
        use kiln_jni::Os::Linux;
        let env = |k: &str| (k == "JAVA_HOME").then(|| "/opt/jdk-25".to_owned());
        let folders = [
            "/home/a/.minecraft/runtime/java-runtime-delta",
            "/home/a/.minecraft/runtime/java-runtime-delta/linux",
            "/home/a/.minecraft/runtime/java-runtime-delta/linux/java-runtime-delta",
            "/usr/lib/jvm/java-21-openjdk",
        ];
        let homes = java_homes(
            Linux,
            &env,
            Some(Path::new("/home/a")),
            &listing(Linux, &folders),
        );
        assert_eq!(
            homes,
            [
                PathBuf::from("/opt/jdk-25"),
                PathBuf::from(
                    "/home/a/.minecraft/runtime/java-runtime-delta/linux/java-runtime-delta"
                ),
                PathBuf::from("/usr/lib/jvm/java-21-openjdk"),
            ]
        );
    }

    /// Horizon's launcher keeps the Mojang Java it downloaded in its data folder (D-221); the folder is found on each
    /// system, and HORIZON_LAUNCHER_HOME moves it.
    #[test]
    fn the_launchers_own_runtime_folder_is_searched() {
        use kiln_jni::Os::{Linux, MacOs, Windows};
        let found = |os, env: &dyn Fn(&str) -> Option<String>, home: &str, folders: &[&str]| {
            java_homes(os, env, Some(Path::new(home)), &listing(os, folders))
        };
        let none = |_: &str| None;
        let epsilon = "java-runtime-epsilon";
        let layout = |data: &str, platform: &str, sep: &str| {
            let run = format!("{data}{sep}minecraft{sep}runtime");
            [
                format!("{run}{sep}{epsilon}"),
                format!("{run}{sep}{epsilon}{sep}{platform}"),
                format!("{run}{sep}{epsilon}{sep}{platform}{sep}{epsilon}"),
            ]
        };
        let l = layout("/home/a/.local/share/horizon-launcher", "linux", "/");
        let refs: Vec<&str> = l.iter().map(String::as_str).collect();
        assert!(has(&found(Linux, &none, "/home/a", &refs), &l[2]));
        // XDG_DATA_HOME moves it; a relative one is ignored.
        let xdg = |k: &str| (k == "XDG_DATA_HOME").then(|| "/data".to_owned());
        let l = layout("/data/horizon-launcher", "linux", "/");
        let refs: Vec<&str> = l.iter().map(String::as_str).collect();
        assert!(has(&found(Linux, &xdg, "/home/a", &refs), &l[2]));
        let rel = |k: &str| (k == "XDG_DATA_HOME").then(|| "data".to_owned());
        let l = layout("/home/a/.local/share/horizon-launcher", "linux", "/");
        let refs: Vec<&str> = l.iter().map(String::as_str).collect();
        assert!(has(&found(Linux, &rel, "/home/a", &refs), &l[2]));
        // HORIZON_LAUNCHER_HOME beats them all.
        let own = |k: &str| (k == "HORIZON_LAUNCHER_HOME").then(|| "/made/up".to_owned());
        let l = layout("/made/up", "linux", "/");
        let refs: Vec<&str> = l.iter().map(String::as_str).collect();
        assert!(has(&found(Linux, &own, "/home/a", &refs), &l[2]));
        // macOS: the bundle's Contents/Home too.
        let l = layout(
            "/Users/a/Library/Application Support/horizon-launcher",
            "mac-os-arm64",
            "/",
        );
        let refs: Vec<&str> = l.iter().map(String::as_str).collect();
        let homes = found(MacOs, &none, "/Users/a", &refs);
        assert!(has(&homes, &l[2]));
        assert!(has(&homes, &format!("{}/jre.bundle/Contents/Home", l[2])));
        // Windows: APPDATA's.
        let appdata = |k: &str| (k == "APPDATA").then(|| r"C:\Users\a\AppData\Roaming".to_owned());
        let l = layout(
            r"C:\Users\a\AppData\Roaming\horizon-launcher",
            "windows-x64",
            "\\",
        );
        let refs: Vec<&str> = l.iter().map(String::as_str).collect();
        assert!(has(&found(Windows, &appdata, r"C:\Users\a", &refs), &l[2]));
    }

    /// With no JAVA_HOME and no system Java, a Java 25 in a made-up launcher folder is what Kiln runs.
    #[test]
    fn find_java_uses_the_launchers_java_when_nothing_else_is_there() {
        let dir = std::env::temp_dir().join(format!("kiln-launcher-java-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        let epsilon = "java-runtime-epsilon";
        let platform = if cfg!(target_os = "windows") {
            "windows-x64"
        } else if cfg!(target_os = "macos") {
            "mac-os-arm64"
        } else {
            "linux"
        };
        let home = dir
            .join("minecraft/runtime")
            .join(epsilon)
            .join(platform)
            .join(epsilon);
        std::fs::create_dir_all(home.join("bin")).unwrap();
        std::fs::write(home.join("release"), "JAVA_VERSION=\"99.0.1\"\n").unwrap();
        std::fs::write(java_program(&home), "").unwrap();
        let env =
            |k: &str| (k == "HORIZON_LAUNCHER_HOME").then(|| dir.to_string_lossy().into_owned());
        let nobody = Some(Path::new("/nonexistent-home"));
        // Version 99 so that a real Java on the machine running the test never wins; with no JAVA_HOME set.
        assert_eq!(find_java(None, &env, nobody, 25).unwrap(), home);
        // Without the variable the made-up folder is not looked at.
        let no_env = |_: &str| None;
        assert!(find_java(None, &no_env, nobody, 99).is_err());
        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn no_java_says_what_to_install_and_where_to_point_kiln() {
        let setting = Path::new("/games/horizon/horizon-mods/.horizon-mod-data/kiln/java_home");
        let nobody = Some(Path::new("/nonexistent-home"));
        let no_env = |_: &str| None;
        // None new enough: which Java to install, or the player's own file to name one in. "Java" and its version
        // come first: INSTALL.md's troubleshooting quotes them.
        let older = find_java(None, &no_env, nobody, 99).unwrap_err();
        assert_eq!(
            older.advice(setting),
            "no Java 99 or newer found. Install Temurin 99 (adoptium.net), or write the path to your Java in \
             /games/horizon/horizon-mods/.horizon-mod-data/kiln/java_home"
        );
        // The file names a folder with no Java in it: what the file should hold.
        let wrong = find_java(Some("/no/java/here"), &no_env, nobody, 0).unwrap_err();
        assert_eq!(
            wrong.advice(setting),
            format!(
                "no Java at /no/java/here. /games/horizon/horizon-mods/.horizon-mod-data/kiln/java_home should hold \
                 the path to the folder that holds {BIN_JAVA}"
            )
        );
        // KILN_JAVA_HOME names it: the variable is what to fix, not the file.
        let env = |k: &str| (k == "KILN_JAVA_HOME").then(|| "/no/java/there".to_owned());
        let wrong = find_java(None, &env, nobody, 0).unwrap_err();
        assert_eq!(
            wrong.advice(setting),
            format!(
                "no Java at /no/java/there. KILN_JAVA_HOME should hold the path to the folder that holds {BIN_JAVA}"
            )
        );
    }

    #[test]
    fn without_java_chat_names_the_players_own_java_home_file() {
        use std::io::Write;
        let game = scratch("no-java");
        let dir = game.join("kiln");
        let data = game.join("horizon-mods/.horizon-mod-data/kiln");
        std::fs::create_dir_all(dir.join("mods")).unwrap();
        std::fs::write(dir.join("kiln-runtime.jar"), b"").unwrap();
        let mut zip =
            zip::ZipWriter::new(std::fs::File::create(dir.join("mods/timer.jar")).unwrap());
        zip.start_file("fabric.mod.json", zip::write::SimpleFileOptions::default())
            .unwrap();
        zip.write_all(br#"{"id": "timer", "entrypoints": {"client": ["timer.Timer"]}}"#)
            .unwrap();
        zip.finish().unwrap();
        let (state, chat) = start(
            &dir,
            Some("/no/java/here".into()),
            &data.join("java_home"),
            Vec::new,
        );
        assert!(matches!(state, State::Stopped(ref why) if why == "no Java"));
        assert_eq!(
            chat,
            [format!(
                "Kiln: no Java at /no/java/here. {} should hold the path to the folder that holds {BIN_JAVA}",
                data.join("java_home").display()
            )]
        );
    }

    #[test]
    fn a_jar_says_which_java_its_entrypoints_need() {
        use std::io::Write;
        let file = scratch("jar-java").join("timer.jar");
        let mut jar = zip::ZipWriter::new(std::fs::File::create(&file).unwrap());
        let options = zip::write::SimpleFileOptions::default();
        jar.start_file("fabric.mod.json", options).unwrap();
        jar.write_all(br#"{"id": "t", "entrypoints": {"client": ["a.Client", "a.Missing"]}}"#)
            .unwrap();
        jar.start_file("a/Client.class", options).unwrap();
        jar.write_all(&[0xCA, 0xFE, 0xBA, 0xBE, 0, 0, 0, 69, 0, 1])
            .unwrap();
        jar.finish().unwrap();
        let m = read_jar(&file).unwrap();
        assert_eq!((m.id.as_str(), m.java), ("t", 25));
    }

    #[test]
    fn class_files_give_the_java_they_need() {
        let class = |major: u8| [0xCA, 0xFE, 0xBA, 0xBE, 0, 0, 0, major];
        assert_eq!(class_java(&class(69)), Some(25));
        assert_eq!(class_java(&class(65)), Some(21));
        assert_eq!(class_java(&class(52)), Some(8));
        // Java 1.0's own version, and anything older, isn't one Kiln picks a Java by.
        assert_eq!(class_java(&class(45)), Some(1));
        assert_eq!(class_java(&class(44)), None);
        assert_eq!(class_java(&[0xCA, 0xFE, 0xBA, 0xBE, 0, 0]), None);
        assert_eq!(class_java(b"PK\x03\x04\0\0\0\x45"), None);
        assert_eq!(class_java(&[]), None);
    }

    #[test]
    fn a_mods_menu_opens_in_a_grid_with_its_own_layout() {
        assert_eq!(grid_menu(0), Some(("minecraft:generic_3x3", 9)));
        assert_eq!(grid_menu(3), Some(("minecraft:generic_3x3", 9)));
        assert_eq!(grid_menu(10), Some(("minecraft:generic_9x2", 18)));
        assert_eq!(grid_menu(54), Some(("minecraft:generic_9x6", 54)));
        assert_eq!(grid_menu(55), None);
        // Tech Reborn's electric furnace: input, output, battery; the inventory 10 lower than vanilla's.
        let furnace = MenuLayout {
            slots: vec![(0, 55, 45), (1, 101, 45), (2, 8, 72)],
            inventory: [8, 94],
        };
        assert_eq!(furnace.size(), [176.0, 176.0]);
        let places = furnace.slot_places(9);
        assert_eq!(places[1], Some([101.0, 45.0]));
        assert_eq!(places.iter().filter(|p| p.is_none()).count(), 6);
        assert_eq!(furnace.energy_bar(), Some([9.0, 19.0, 14.0, 50.0]));
        assert_eq!(furnace.progress_arrow(), Some([76.0, 48.0, 22.0, 10.0]));
        // Its battery slot (8, 72 to 88) is where the inventory's label would be (83): none.
        assert_eq!(furnace.inventory_label(), None);
        let vanilla = MenuLayout {
            slots: vec![(0, 56, 17), (1, 116, 35)],
            inventory: [8, 84],
        };
        assert_eq!(vanilla.inventory_label(), Some(73.0));
        // A slot where the bar goes: no bar. Slots too close, or on other rows: no arrow.
        let crowded = MenuLayout {
            slots: vec![(0, 8, 30), (1, 30, 30), (2, 50, 60)],
            inventory: [8, 84],
        };
        assert_eq!(crowded.energy_bar(), None);
        assert_eq!(crowded.progress_arrow(), None);
        // A slot past the menu's grid is left out.
        assert_eq!(furnace.slot_places(2).len(), 2);
        // Its tank gauge: right of the energy bar, clear of the input slot (54 to 72), left of it.
        assert_eq!(furnace.tank_gauge(), Some([26.0, 19.0, 20.0, 50.0]));
    }

    #[test]
    fn a_machine_with_a_tank_shows_it_beside_its_fluid_slots() {
        // Tech Reborn's industrial grinder: a cell or bucket in (34, 35) and out (34, 55), the ore at (84, 43), four
        // outputs down the right, a battery at the bottom left. The arrow runs from the ore to the output on about its
        // row (not along the bottom from the battery); the tank right of the fluid slots, at 53 as Tech Reborn's.
        let grinder = MenuLayout {
            slots: vec![
                (1, 34, 35),
                (0, 84, 43),
                (2, 126, 18),
                (3, 126, 36),
                (4, 126, 54),
                (5, 126, 72),
                (6, 34, 55),
                (7, 8, 72),
            ],
            inventory: [8, 94],
        };
        assert_eq!(grinder.progress_arrow(), Some([105.0, 46.0, 18.0, 10.0]));
        assert_eq!(grinder.energy_bar(), Some([9.0, 19.0, 14.0, 50.0]));
        assert_eq!(grinder.tank_gauge(), Some([53.0, 19.0, 20.0, 50.0]));
        // The thermal generator: lava in (25, 35), the empty bucket out (25, 55), no arrow; its tank at 44 as Tech
        // Reborn's.
        let generator = MenuLayout {
            slots: vec![(0, 25, 35), (1, 25, 55)],
            inventory: [8, 94],
        };
        assert_eq!(generator.progress_arrow(), None);
        assert_eq!(generator.tank_gauge(), Some([44.0, 19.0, 20.0, 50.0]));
    }

    /// A menu with the inventory where vanilla's is and these slots, at (x, y).
    fn menu(slots: &[(i32, i32)]) -> MenuLayout {
        MenuLayout {
            slots: slots
                .iter()
                .enumerate()
                .map(|(i, &(x, y))| (i, x, y))
                .collect(),
            inventory: [8, 84],
        }
    }

    #[test]
    fn the_inventory_label_moves_out_of_a_slots_way_only_when_touched() {
        // The label's line, 73 to 82, left of 68: a slot (16 tall, a pixel around) right above it, right below it or
        // right of it leaves it; one a pixel nearer covers it.
        for (slot, label) in [
            ((8, 56), Some(73.0)),
            ((8, 57), None),
            ((8, 83), Some(73.0)),
            ((8, 82), None),
            ((69, 70), Some(73.0)),
            ((68, 70), None),
        ] {
            assert_eq!(menu(&[slot]).inventory_label(), label, "a slot at {slot:?}");
        }
    }

    #[test]
    fn the_energy_bar_gives_way_to_a_slot_on_it() {
        // The bar, 9 to 23 and 19 to 69: a slot touching it from any side leaves it; one overlapping it, by a pixel or
        // more, takes its place.
        let bar = Some([9.0, 19.0, 14.0, 50.0]);
        for (slot, shown) in [
            ((15, 3), bar),
            ((15, 4), None),
            ((23, 30), bar),
            ((22, 30), None),
            ((15, 69), bar),
            ((15, 68), None),
            ((0, 30), None),
        ] {
            assert_eq!(menu(&[slot]).energy_bar(), shown, "a slot at {slot:?}");
        }
    }

    #[test]
    fn the_progress_arrow_needs_room_and_starts_at_the_nearest_slot() {
        // 40 pixels from slot to slot (the slot, and 24 for the arrow): an arrow 16 long; a pixel less, none.
        assert_eq!(
            menu(&[(20, 30), (60, 30)]).progress_arrow(),
            Some([41.0, 33.0, 16.0, 10.0])
        );
        assert_eq!(menu(&[(21, 30), (60, 30)]).progress_arrow(), None);
        // Two inputs on the output's row: from the nearer.
        assert_eq!(
            menu(&[(70, 43), (84, 43), (126, 43)]).progress_arrow(),
            Some([105.0, 46.0, 18.0, 10.0])
        );
        // Half a slot up or down is the output's row; more is another.
        assert_eq!(
            menu(&[(20, 39), (60, 30)]).progress_arrow(),
            Some([41.0, 42.0, 16.0, 10.0])
        );
        assert_eq!(menu(&[(20, 40), (60, 30)]).progress_arrow(), None);
    }

    #[test]
    fn the_tank_gauge_keeps_two_pixels_from_a_slot() {
        // The gauge at 26 (20 wide, 19 to 69) unless a slot's box (a pixel around it) comes within two pixels; then
        // past the slot.
        for (slot, x) in [
            ((49, 30), 26.0),
            ((48, 30), 67.0),
            ((30, 72), 26.0),
            ((30, 71), 49.0),
            ((30, 0), 26.0),
            ((30, 1), 49.0),
        ] {
            assert_eq!(
                menu(&[slot]).tank_gauge(),
                Some([x, 19.0, 20.0, 50.0]),
                "a slot at {slot:?}"
            );
        }
    }

    #[test]
    fn a_machines_data_reads_back() {
        assert_eq!(
            machine_data(&[562, 39, 562, 0, 1000, 0]),
            (0.562, Some(0.39), 562, 1000)
        );
        // Past 15 bits, the high half; no progress (-1); nothing at all.
        assert_eq!(
            machine_data(&[1000, -1, 0, 2, 0, 2]),
            (1.0, None, 65536, 65536)
        );
        assert_eq!(machine_data(&[]), (0.0, None, 0, 0));
        // The energy alone: no progress either.
        assert_eq!(machine_data(&[500]), (0.5, None, 0, 0));
        // A tank after the energy: a quarter full of lava (0xF60), 2500 of 10000 millibuckets; none without it.
        let tank = [0, -1, 0, 0, 0, 0, 250, 0xF60, 2500, 0, 10000, 0];
        assert_eq!(machine_tank(&tank), Some((0.25, 0xFF_6600, 2500, 10000)));
        assert_eq!(machine_data(&tank), (0.0, None, 0, 0));
        assert_eq!(machine_tank(&[562, 39, 562, 0, 1000, 0]), None);
    }

    #[test]
    fn a_machines_sides_read_back_and_set_the_hoppers_slots() {
        // No tank (six zeros), facing east (5), two slots: slot 0 input on the top (up, 1) and west (4) with auto
        // input; slot 1 output down (0) and both on the east (5).
        let slot0 = (1 << 2) | (1 << 8) | (1 << 12);
        let slot1 = 2 | (3 << 10);
        let data = [0, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 5, 2, slot0, slot1];
        assert_eq!(machine_tank(&data), None);
        let sides = machine_sides(&data).unwrap();
        assert_eq!(sides.facing, Some(5));
        assert_eq!(
            (sides.mode(0, 1), sides.mode(0, 4), sides.mode(0, 0)),
            (1, 1, 0)
        );
        assert_eq!((sides.mode(1, 0), sides.mode(1, 5)), (2, 3));
        assert!(sides.switch(0, 0) && !sides.switch(0, 1) && !sides.switch(1, 0));
        // Its faces as its screen names them: front east, back west; seen from the front, left south, right north.
        assert_eq!(
            (0..6).map(|r| sides.face(r)).collect::<Vec<_>>(),
            [5, 4, 3, 2, 1, 0]
        );
        assert_eq!(machine_sides(&data[..12]), None);
        // Hoppers: into slot 0 from above and the west; slot 1 also from the east (both there); out of slot 1 below.
        let base = Slots {
            count: 9,
            ..Slots::default()
        };
        let reached = sided_slots(&base, &sides);
        assert_eq!(
            reached.faces,
            [vec![1], vec![0], vec![], vec![], vec![0], vec![1]]
        );
        assert_eq!(
            (reached.inputs, reached.outputs, reached.count),
            (vec![0, 1], vec![1], 9)
        );
        assert_eq!(side_button_face(1, 5, 2), 1_000_152);
        assert_eq!(side_button_switch(3, 1, true), 2_000_311);
    }

    #[test]
    fn sprites_keep_their_edges_as_they_stretch() {
        let pieces = nine_slice([200.0, 20.0], [10.0, 5.0, 100.0, 20.0], 3.0);
        assert_eq!(pieces.len(), 9);
        assert_eq!(
            pieces[0],
            ([0.0, 0.0, 3.0, 3.0], [10.0, 5.0, 3.0, 3.0]),
            "a corner as it is"
        );
        assert_eq!(
            pieces[4],
            ([3.0, 3.0, 194.0, 14.0], [13.0, 8.0, 94.0, 14.0]),
            "the middle stretched"
        );
        assert_eq!(
            pieces[8],
            ([197.0, 17.0, 3.0, 3.0], [107.0, 22.0, 3.0, 3.0])
        );
        assert_eq!(
            nine_slice([17.0, 17.0], [0.0, 0.0, 17.0, 17.0], 0.0),
            vec![([0.0, 0.0, 17.0, 17.0], [0.0, 0.0, 17.0, 17.0])]
        );
        assert_eq!(
            nine_slice([8.0, 20.0], [0.0, 0.0, 2.0, 20.0], 3.0).len(),
            6,
            "a thin box keeps no middle column"
        );
    }

    #[test]
    fn the_mods_take_input_over_the_games_container_screen_only_where_they_drew() {
        let mut s = ModScreen::default();
        let press = |x: &str| {
            vec![
                "press".to_owned(),
                x.to_owned(),
                "20".to_owned(),
                "0".to_owned(),
                "0".to_owned(),
            ]
        };
        assert!(
            !s.over_input(press("310"), [310.0, 20.0]),
            "no container screen, nothing taken"
        );
        assert!(s.commands.is_empty(), "and nothing told");
        let view: Vec<String> = ["inventory", "-", "125", "37", "176", "166", "46"]
            .map(String::from)
            .to_vec();
        s.game_screen(Some(view.clone()));
        s.game_screen(Some(view));
        assert_eq!(s.commands.len(), 1, "the same screen is told once");
        assert_eq!(s.commands[0].1[..3], ["game", "inventory", "-"]);
        s.over(
            vec![HudOp::Clip(None)],
            false,
            vec![[300.0, 10.0, 120.0, 200.0]],
        );
        assert!(
            s.over_input(press("310"), [310.0, 20.0]),
            "a click on the mods' drawing is theirs"
        );
        assert!(
            s.over_input(vec!["drag".into()], [200.0, 20.0]),
            "its drag too, wherever it goes"
        );
        assert!(
            s.over_input(vec!["release".into()], [200.0, 20.0]),
            "and its release"
        );
        assert!(
            !s.over_input(press("150"), [150.0, 20.0]),
            "a click on the game's window is the game's"
        );
        assert!(!s.over_input(vec!["release".into()], [150.0, 20.0]));
        // A key right after the click on the mods' drawing, before Java answered it (D-178): theirs, as the click may
        // have focused a text field of theirs; once Java drew after it, its word holds.
        assert!(
            s.over_input(vec!["char".into(), "101".into(), "0".into()], [0.0, 0.0]),
            "typed at once after a click"
        );
        s.over(
            vec![HudOp::Clip(None)],
            false,
            vec![[300.0, 10.0, 120.0, 200.0]],
        );
        assert!(
            s.over_input(vec!["char".into(), "101".into(), "0".into()], [0.0, 0.0]),
            "a drawing from before it went"
        );
        assert_eq!(s.take_commands().len(), 8);
        s.over(
            vec![HudOp::Clip(None)],
            false,
            vec![[300.0, 10.0, 120.0, 200.0]],
        );
        assert!(
            !s.over_input(
                vec!["key".into(), "key.keyboard.e".into(), "0".into()],
                [0.0, 0.0]
            ),
            "keys the game's"
        );
        s.over(Vec::new(), true, Vec::new());
        assert!(
            s.over_input(
                vec!["key".into(), "key.keyboard.e".into(), "0".into()],
                [0.0, 0.0]
            ),
            "a mod's field's"
        );
        assert_eq!(
            s.commands.last().unwrap().1[..2],
            ["over", "key"],
            "Java hears every one"
        );
        s.over(vec![HudOp::Clip(None)], true, vec![[0.0, 0.0, 1.0, 1.0]]);
        s.game_screen(None);
        assert_eq!(s.commands.last().unwrap().1, ["game", "-"]);
        assert!(
            s.over_ops.is_empty() && !s.over_input(press("0"), [0.0, 0.0]),
            "gone with the screen"
        );
    }

    #[test]
    fn a_mods_screen_takes_input_only_while_shown() {
        let mut s = ModScreen::default();
        assert!(!s.input(vec!["press".into()]), "no screen, no input");
        s.cursor([1.0, 2.0]);
        assert!(s.commands.is_empty());
        s.java_says(true);
        assert_eq!(s.take_wanted(), Some(true));
        assert_eq!(s.take_wanted(), None, "asked once");
        s.show(Shown::Own);
        s.cursor([1.0, 2.5]);
        s.cursor([1.0, 2.5]);
        assert!(s.input(vec![
            "press".into(),
            "1".into(),
            "2.5".into(),
            "0".into(),
            "0".into()
        ]));
        assert_eq!(
            s.commands,
            vec![
                ("sin".into(), vec!["move".into(), "1".into(), "2.5".into()]),
                (
                    "sin".into(),
                    vec![
                        "press".into(),
                        "1".into(),
                        "2.5".into(),
                        "0".into(),
                        "0".into()
                    ]
                ),
            ],
            "a pointer that didn't move says nothing"
        );
        s.ops = vec![HudOp::Clip(None)];
        s.closed_by_game();
        assert_eq!(s.shown, Shown::No);
        assert!(s.ops.is_empty());
        assert_eq!(s.commands.last(), Some(&("sclosed".into(), Vec::new())));
        assert_eq!(num(3.0), "3");
        assert_eq!(num(2.5), "2.5");
    }

    #[test]
    fn a_key_java_heard_go_down_is_heard_going_up_and_a_paste_gets_the_clipboard() {
        let fields = |f: &[&str]| f.iter().map(|s| s.to_string()).collect::<Vec<String>>();
        let mut s = ModScreen::default();
        s.show(Shown::Own);
        assert!(s.input(fields(&["key", "key.keyboard.a", "0"])));
        s.released("key.keyboard.a");
        s.released("key.keyboard.a");
        s.released("key.keyboard.b");
        assert!(s.input(fields(&["key", "key.keyboard.c", "0"])));
        assert!(s.input(fields(&["keyup", "key.keyboard.c", "0"])));
        s.released("key.keyboard.c");
        assert_eq!(
            s.commands.iter().filter(|(_, f)| f[0] == "keyup").count(),
            2,
            "one release each, never twice"
        );
        assert_eq!(
            s.commands[1],
            ("sin".into(), fields(&["keyup", "key.keyboard.a", "0"]))
        );
        assert!(!s.wants_clipboard());
        s.commands.clear();
        assert!(s.input(fields(&["key", "key.keyboard.v", "0"])), "V alone");
        assert!(!s.wants_clipboard(), "V alone is no paste");
        assert!(
            s.input(fields(&["key", "key.keyboard.v", "2"])),
            "Control+V"
        );
        assert!(s.wants_clipboard());
        s.clipboard("diamond".into());
        assert_eq!(
            s.commands[1],
            ("sin".into(), fields(&["clip", "diamond"])),
            "just before the paste"
        );
        assert!(
            is_paste(&fields(&["over", "key", "key.keyboard.v", "8"])),
            "Command+V over the game's screen"
        );
    }

    #[test]
    fn settings_pages_belong_to_their_mods_rows() {
        let mut s = ModScreen::default();
        s.settings = ["showfps".to_owned()].into();
        assert!(s.has_settings("kiln_showfps"));
        assert!(!s.has_settings("showfps"), "rows are Kiln's");
        assert!(!s.has_settings("kiln_other"));
        s.open_settings("kiln_other");
        assert!(s.commands.is_empty());
        s.open_settings("kiln_showfps");
        assert_eq!(
            s.commands,
            vec![("settings".into(), vec!["showfps".into()])]
        );
    }

    #[test]
    fn a_manifest_names_its_settings_page() {
        let m =
            read_manifest(r#"{"id":"x","entrypoints":{"client":["a.B"],"modmenu":["a.Menu"]}}"#)
                .unwrap();
        assert_eq!(m.modmenu, vec!["a.Menu".to_owned()]);
        assert_eq!(
            m.entrypoints(),
            1,
            "Mod Menu's entrypoint is no Fabric one Kiln waits for"
        );
    }

    #[test]
    fn chunks_that_came_and_went() {
        let before: BTreeSet<(i32, i32)> = [(0, 0), (0, 1), (-1, 0)].into();
        let now: BTreeSet<(i32, i32)> = [(0, 0), (1, 0), (2, -3)].into();
        assert_eq!(
            chunk_changes(&before, &now),
            ("1,0;2,-3".into(), "-1,0;0,1".into())
        );
        assert_eq!(chunk_changes(&now, &now), (String::new(), String::new()));
    }

    #[test]
    fn the_window_state_carries_effects_and_the_screen() {
        let state = WindowState {
            effects: vec![
                ActiveEffect {
                    id: "speed".into(),
                    duration: 200,
                    amplifier: 1,
                    ambient: false,
                    visible: true,
                },
                ActiveEffect {
                    id: "mymod:glow up".into(),
                    duration: -1,
                    amplifier: 0,
                    ambient: true,
                    visible: false,
                },
            ],
            screen: "inventory".into(),
            ..WindowState::default()
        };
        let field = state.field();
        assert!(
            field.contains(";effects=speed:200:1:0:1,mymod%3Aglow%20up:-1:0:1:0;"),
            "{field}"
        );
        assert!(field.split(';').any(|p| p == "screen=inventory"), "{field}");
    }

    /// The server's Java is the process's (`SERVER`): the tests that set it, or need it unset, take turns.
    static SERVER_TEST: Mutex<()> = Mutex::new(());

    /// A "Java" for the tests: `script`, the program of a Java home in a scratch folder that is Java's folder too (its
    /// working directory), started as soon as Linux lets it (`start_written`). Returns Kiln's side of it and the folder.
    #[cfg(unix)]
    fn scripted_java(name: &str, script: &str) -> (JavaHost, PathBuf) {
        use std::os::unix::fs::PermissionsExt;
        let home = scratch(name);
        let program = java_program(&home);
        std::fs::create_dir_all(program.parent().unwrap()).unwrap();
        std::fs::write(&program, format!("#!/bin/sh\n{script}")).unwrap();
        std::fs::set_permissions(&program, std::fs::Permissions::from_mode(0o755)).unwrap();
        (crate::game::kiln_jvm::start_written(&home), home)
    }

    /// A scripted Java that only listens: what it hears goes to `heard.txt` in its folder. Its output stays open (as fd
    /// 3), or Kiln would take it for stopped.
    #[cfg(unix)]
    const LISTENS: &str = "exec cat 3>&1 > heard.txt\n";

    /// Kiln running `mods` on `java`, as `start` leaves it: every entrypoint waited for.
    #[cfg(unix)]
    fn running(java: JavaHost, mods: Vec<JavaMod>) -> Running {
        Running {
            java,
            started: Instant::now(),
            awaiting: mods.iter().map(JavaMod::entrypoints).sum(),
            mods: mods
                .into_iter()
                .map(|m| {
                    let n = m.entrypoints();
                    (m, n, false)
                })
                .collect(),
            version: String::new(),
            ticks: 0,
            last_tick: Instant::now(),
            dir: std::env::temp_dir().join(format!("kiln-test-{}-running", std::process::id())),
            keys: Vec::new(),
            bound: BTreeMap::new(),
            commands: Vec::new(),
            advancements: BTreeSet::new(),
            chunks: BTreeSet::new(),
            chunk_dimension: String::new(),
            draw: Vec::new(),
            soft_off: None,
            restart: None,
        }
    }

    /// What a `LISTENS` Java in `dir` heard, once it has heard `ticks` ticks: the commands before each tick, as their
    /// lines (fields apart by tabs), the tick's own left out.
    #[cfg(unix)]
    fn heard(dir: &Path, ticks: usize) -> Vec<Vec<String>> {
        let deadline = Instant::now() + Duration::from_secs(10);
        loop {
            let text = std::fs::read_to_string(dir.join("heard.txt")).unwrap_or_default();
            let mut by_tick = vec![Vec::new()];
            for line in text.lines() {
                if line.starts_with("tick\t") {
                    by_tick.push(Vec::new());
                } else {
                    by_tick.last_mut().unwrap().push(line.to_owned());
                }
            }
            if by_tick.len() > ticks {
                by_tick.truncate(ticks);
                return by_tick;
            }
            assert!(Instant::now() < deadline, "Java heard only {text:?}");
            std::thread::sleep(Duration::from_millis(5));
        }
    }

    /// Mods' entrypoints as Java answers for them: a class two mods name (a mod's copy under another id) counts for
    /// each, once each; a failure names its mod and leaves it out of the count; an answer nobody waits for (Java's
    /// third for that class) is logged, not counted.
    #[cfg(unix)]
    #[test]
    fn each_mod_counts_once_java_answered_for_its_entrypoints() {
        let script = "printf 'ready\\t25\\nloaded\\ta.Client\\nfailed\\tb.Client\\tit broke\\n'\n\
                      printf 'loaded\\ta.Client\\nloaded\\ta.Client\\nchat\\tend\\n'\n\
                      exec cat 3>&1 > /dev/null\n";
        let (java, _) = scripted_java("answers", script);
        let client = |id: &str, class: &str| {
            read_manifest(&format!(
                r#"{{"id": "{id}", "entrypoints": {{"client": ["{class}"]}}}}"#
            ))
            .unwrap()
        };
        let mut r = running(
            java,
            vec![
                client("a", "a.Client"),
                client("b", "b.Client"),
                client("a_copy", "a.Client"),
            ],
        );
        let deadline = Instant::now() + Duration::from_secs(10);
        let mut chat = Vec::new();
        while !chat.iter().any(|l| l == "end") {
            assert!(Instant::now() < deadline, "{chat:?}");
            chat.extend(r.take_replies().0);
            std::thread::sleep(Duration::from_millis(5));
        }
        assert_eq!(
            chat,
            [
                "Kiln: b didn't start: it broke",
                "Kiln: 2 of 3 Java mods running on Java 25",
                "end"
            ]
        );
    }

    /// A mod with mixins and no entrypoint runs as Java starts (D-099): with nothing to answer for, Kiln says the mods
    /// run once Java is ready.
    #[cfg(unix)]
    #[test]
    fn mods_with_mixins_only_run_once_java_is_ready() {
        let script = "printf 'ready\\t25\\nchat\\tend\\n'\nexec cat 3>&1 > /dev/null\n";
        let (java, _) = scripted_java("mixins-only", script);
        let hud = read_manifest(r#"{"id": "hud", "mixins": ["hud.mixins.json"]}"#).unwrap();
        let mut r = running(java, vec![hud]);
        let deadline = Instant::now() + Duration::from_secs(10);
        let mut chat = Vec::new();
        while !chat.iter().any(|l| l == "end") {
            assert!(Instant::now() < deadline, "{chat:?}");
            chat.extend(r.take_replies().0);
            std::thread::sleep(Duration::from_millis(5));
        }
        assert_eq!(chat, ["Kiln: 1 of 1 Java mods running on Java 25", "end"]);
    }

    /// Java hears each advancement once a connection: those it heard aren't sent again, and out of the world (a new
    /// connection after) Kiln forgets them, so the next world's are all sent.
    #[cfg(unix)]
    #[test]
    fn java_hears_each_advancement_once_a_connection() {
        let (java, dir) = scripted_java("advancements", LISTENS);
        let mut r = running(java, Vec::new());
        let world = |made: Option<&[&str]>| Snapshot {
            connected: true,
            in_world: true,
            dimension: "minecraft:overworld".into(),
            advancements: made.map(|m| m.iter().map(|a| a.to_string()).collect()),
            ..Snapshot::default()
        };
        for s in [
            world(Some(&["a"])),
            world(Some(&["a", "b"])),
            world(None),
            Snapshot::default(),
            world(Some(&["a", "b"])),
        ] {
            r.tick(s, DEFAULT_GUI, 60).unwrap();
        }
        assert_eq!(
            heard(&dir, 5),
            [
                vec!["adv\ta"],
                vec!["adv\tb"],
                vec![],
                vec![],
                vec!["adv\ta", "adv\tb"]
            ]
        );
    }

    /// Java hears the chunks that came and went, when some did: a level left takes its chunks along (Java forgets them
    /// with it, so only the new level's come), and out of the world the last ones go.
    #[cfg(unix)]
    #[test]
    fn java_hears_the_chunks_that_came_and_went() {
        let (java, dir) = scripted_java("chunks", LISTENS);
        let mut r = running(java, Vec::new());
        let at = |dimension: &str, chunks: &[[i32; 2]]| Snapshot {
            connected: true,
            in_world: true,
            dimension: dimension.into(),
            state: WindowState {
                chunks: chunks.to_vec(),
                ..WindowState::default()
            },
            ..Snapshot::default()
        };
        for s in [
            at("minecraft:overworld", &[[0, 0], [1, 0]]),
            at("minecraft:overworld", &[[0, 0], [1, 0]]),
            at("minecraft:overworld", &[[0, 0]]),
            at("minecraft:the_nether", &[[5, 5]]),
            Snapshot::default(),
        ] {
            r.tick(s, DEFAULT_GUI, 60).unwrap();
        }
        assert_eq!(
            heard(&dir, 5),
            [
                vec!["chunks\t0,0;1,0\t"],
                vec![],
                vec!["chunks\t\t1,0"],
                vec!["chunks\t5,5\t"],
                vec!["chunks\t\t5,5"]
            ]
        );
    }

    /// Keys pressed while playing reach Java with the next tick, before it, once.
    #[cfg(unix)]
    #[test]
    fn keys_reach_java_with_the_next_tick() {
        // A tick whose Java stopped unsets the server's.
        let _alone = SERVER_TEST.lock().unwrap_or_else(|e| e.into_inner());
        let (java, dir) = scripted_java("keys", LISTENS);
        STATE.with(|s| *s.borrow_mut() = State::Running(Box::new(running(java, Vec::new()))));
        key("key.keyboard.j");
        key("key.keyboard.k");
        for _ in 0..2 {
            STATE.with(|s| tick_running(&mut s.borrow_mut(), Snapshot::default()));
        }
        let heard = heard(&dir, 2);
        STATE.with(|s| *s.borrow_mut() = State::Idle);
        assert_eq!(
            heard,
            [vec!["key\tkey.keyboard.j", "key\tkey.keyboard.k"], vec![]]
        );
    }

    /// A server call Kiln gave up on answers late, when the next call goes out: each call has a number of its own, so
    /// the late answer isn't taken for the next call's.
    #[cfg(unix)]
    #[test]
    fn a_late_answer_is_not_the_next_calls() {
        let _alone = SERVER_TEST.lock().unwrap_or_else(|e| e.into_inner());
        struct Unset;
        impl Drop for Unset {
            fn drop(&mut self) {
                set_server(None);
            }
        }
        // The first call's answer comes with the second call, before the second's own.
        let script = "first=\n\
                      tab=\"$(printf '\\t')\"\n\
                      while IFS=\"$tab\" read -r command call rest; do\n  \
                      if [ -z \"$first\" ]; then first=\"$call\"; continue; fi\n  \
                      printf 'return\\t%s\\tsuccess\\thealth 1.0\\n' \"$first\"\n  \
                      printf 'return\\t%s\\tpass\\n' \"$call\"\n\
                      done\n";
        let (mut java, _) = scripted_java("late", script);
        let _unset = Unset;
        set_server(Some(ServerSide {
            link: java.take_server_link().unwrap(),
            items: ["test:charm".to_owned()].into(),
            events: BTreeSet::new(),
            blocks: BTreeMap::new(),
            entities: BTreeMap::new(),
            slots: BTreeMap::new(),
            calls: 0,
            commands: BTreeSet::new(),
            commands_told: None,
            commands_retry: None,
            late: BTreeMap::new(),
        }));
        let mut nothing = |_: &[String]| String::new();
        let world = Path::new("/w");
        let first = use_item(
            world,
            0,
            "test:charm",
            Hand::Main,
            &crystal_user(),
            &mut nothing,
        );
        assert_eq!(first, None, "the first call is answered too late");
        let second = use_item(
            world,
            1,
            "test:charm",
            Hand::Main,
            &crystal_user(),
            &mut nothing,
        );
        assert_eq!(
            second,
            Some(Outcome {
                result: UseResult::Pass,
                effects: Vec::new()
            })
        );
    }

    /// A player's payload is their own input: a call Java answers too late is still done, at that player's next tick,
    /// whether its answer came during another call or while none waited; once, and only for that player.
    #[cfg(unix)]
    #[test]
    fn a_late_payload_is_done_when_its_answer_comes() {
        let _alone = SERVER_TEST.lock().unwrap_or_else(|e| e.into_inner());
        struct Unset;
        impl Drop for Unset {
            fn drop(&mut self) {
                set_server(None);
            }
        }
        // The first call is answered with the second; the third, after the game gave up on it. It says when it is up
        // (a script just written may take a while to start): the calls' times are what the test is about.
        let script = "printf 'log\\tup\\n'\n\
                      n=0\n\
                      tab=\"$(printf '\\t')\"\n\
                      while IFS=\"$tab\" read -r command call rest; do\n  \
                      n=$((n + 1))\n  \
                      if [ $n = 1 ]; then first=\"$call\"; continue; fi\n  \
                      if [ $n = 2 ]; then\n    \
                      printf 'return\\t%s\\tpass\\tnet 01234567-89ab-cdef-0123-456789abcdef 0a6b\\n' \"$first\"\n    \
                      printf 'return\\t%s\\tpass\\n' \"$call\"\n    \
                      continue\n  \
                      fi\n  \
                      sleep 0.5\n  \
                      printf 'return\\t%s\\tpass\\ttell {\"text\":\"late\"}\\n' \"$call\"\n\
                      done\n";
        let (mut java, _) = scripted_java("late-payload", script);
        let up = Instant::now() + Duration::from_secs(20);
        while !java.replies().contains(&Reply::Log("up".into())) {
            assert!(Instant::now() < up, "the scripted Java never started");
            std::thread::sleep(Duration::from_millis(10));
        }
        let _unset = Unset;
        set_server(Some(ServerSide {
            link: java.take_server_link().unwrap(),
            items: BTreeSet::new(),
            events: ["payload".to_owned()].into(),
            blocks: BTreeMap::new(),
            entities: BTreeMap::new(),
            slots: BTreeMap::new(),
            calls: 0,
            commands: BTreeSet::new(),
            commands_told: None,
            commands_retry: None,
            late: BTreeMap::new(),
        }));
        let mut nothing = |_: &[String]| String::new();
        let (world, player) = (Path::new("/w"), crystal_user());
        assert_eq!(
            from_client(world, 0, b"\x01", &player, &mut nothing),
            None,
            "no answer in time"
        );
        assert!(late_payloads(player.uuid).is_empty(), "nothing came yet");
        let second = from_client(world, 1, b"\x02", &player, &mut nothing);
        assert_eq!(
            second,
            Some(Outcome {
                result: UseResult::Pass,
                effects: Vec::new()
            })
        );
        assert!(
            late_payloads(0xc0ffee).is_empty(),
            "another player's tick doesn't take it"
        );
        let late = Outcome {
            result: UseResult::Pass,
            effects: vec![Effect::Net {
                player: player.uuid,
                data: vec![0x0a, 0x6b],
            }],
        };
        assert_eq!(late_payloads(player.uuid), [late]);
        assert!(late_payloads(player.uuid).is_empty(), "done once");
        assert_eq!(from_client(world, 2, b"\x03", &player, &mut nothing), None);
        let deadline = Instant::now() + Duration::from_secs(5);
        let mut taken = Vec::new();
        while taken.is_empty() && Instant::now() < deadline {
            std::thread::sleep(Duration::from_millis(50));
            taken = late_payloads(player.uuid);
        }
        let told = Outcome {
            result: UseResult::Pass,
            effects: vec![Effect::Tell(serde_json::json!({"text": "late"}))],
        };
        assert_eq!(taken, [told], "an answer that came while no call waited");
        drop(java);
    }

    #[test]
    fn the_players_answer_has_the_calls_player_as_they_are_now() {
        let guest = Online {
            uuid: 7,
            name: "Steve".into(),
            dimension: "minecraft:the_nether".into(),
            position: [0.5, 70.0, -8.0],
            game_mode: 1,
            health: 18.5,
            view_distance: 12,
        };
        let alex = Online {
            position: [100.0, 60.0, 100.0],
            view_distance: 6,
            ..Online::of(&crystal_user(), 6)
        };
        // Nobody asked for now: the players as listed, in order.
        assert_eq!(
            players_answer(&[alex.clone(), guest.clone()], None, 10),
            "01234567-89ab-cdef-0123-456789abcdef Alex minecraft:overworld 100 60 100 0 20 6;\
             00000000-0000-0000-0000-000000000007 Steve minecraft:the_nether 0.5 70 -8 1 18.5 12"
        );
        // The call's player where they are now, keeping the view distance they are sent.
        assert_eq!(
            players_answer(&[alex.clone(), guest.clone()], Some(&crystal_user()), 10),
            "01234567-89ab-cdef-0123-456789abcdef Alex minecraft:overworld 1.5 64 -2.25 0 20 6;\
             00000000-0000-0000-0000-000000000007 Steve minecraft:the_nether 0.5 70 -8 1 18.5 12"
        );
        // A player who joined during this tick isn't listed yet: added, at the server's view distance.
        assert_eq!(
            players_answer(&[guest], Some(&crystal_user()), 10),
            "00000000-0000-0000-0000-000000000007 Steve minecraft:the_nether 0.5 70 -8 1 18.5 12;\
             01234567-89ab-cdef-0123-456789abcdef Alex minecraft:overworld 1.5 64 -2.25 0 20 10"
        );
        assert_eq!(players_answer(&[], None, 10), "");
    }

    /// A player's connection made or closed is a call only when a Java mod hears it, with the player's fields.
    #[cfg(unix)]
    #[test]
    fn a_connection_is_told_only_when_a_java_mod_hears_it() {
        let _alone = SERVER_TEST.lock().unwrap_or_else(|e| e.into_inner());
        struct Unset;
        impl Drop for Unset {
            fn drop(&mut self) {
                set_server(None);
            }
        }
        // Java says which call it had, and the player's name (the call's fifth field after the command). It says when
        // it is up (a script just written may take a while to start), as a call waits only 250 ms.
        let script = "printf 'log\\tup\\n'\n\
                      tab=\"$(printf '\\t')\"\n\
                      while IFS=\"$tab\" read -r command call world time uuid name rest; do\n  \
                      printf 'return\\t%s\\tpass\\ttell {\"text\":\"%s %s\"}\\n' \"$call\" \"$command\" \"$name\"\n\
                      done\n";
        let (mut java, _) = scripted_java("connect", script);
        let up = Instant::now() + Duration::from_secs(20);
        while !java.replies().contains(&Reply::Log("up".into())) {
            assert!(Instant::now() < up, "the scripted Java never started");
            std::thread::sleep(Duration::from_millis(10));
        }
        let _unset = Unset;
        set_server(Some(ServerSide {
            link: java.take_server_link().unwrap(),
            items: BTreeSet::new(),
            events: ["connect".to_owned()].into(),
            blocks: BTreeMap::new(),
            entities: BTreeMap::new(),
            slots: BTreeMap::new(),
            calls: 0,
            commands: BTreeSet::new(),
            commands_told: None,
            commands_retry: None,
            late: BTreeMap::new(),
        }));
        let mut nothing = |_: &[String]| String::new();
        let world = Path::new("/w");
        let joined = player_connected(world, 3, &crystal_user(), &mut nothing);
        assert_eq!(
            joined,
            Some(Outcome {
                result: UseResult::Pass,
                effects: vec![Effect::Tell(serde_json::json!({"text": "connect Alex"}))]
            })
        );
        assert_eq!(
            player_disconnected(world, 4, &crystal_user(), &mut nothing),
            None,
            "nobody hears it"
        );
    }
}
