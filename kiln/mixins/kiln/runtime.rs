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

use std::cell::RefCell;
use std::collections::{BTreeMap, BTreeSet};
use std::io::Read;
use std::path::{Path, PathBuf};
use std::sync::Mutex;
use std::time::{Duration, Instant};

use crate::game::kiln_jvm::{HudOp, JavaHost, Reply, Return, ServerLink, java_program};

/// The mod's id (its settings folder and Options > Mods row), name and version.
pub const ID: &str = "kiln";
pub const NAME: &str = "Kiln";
pub const VERSION: &str = "0.2.0";
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

/// A Java mod as its fabric.mod.json describes it.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct JavaMod {
    pub id: String,
    pub name: String,
    pub version: String,
    /// The classes of its `main` entrypoints: Fabric runs them on the client too, before every `client` one.
    pub main: Vec<String>,
    /// The classes of its `client` entrypoints.
    pub client: Vec<String>,
    /// The oldest Java its entrypoint classes run on (their class file version); 0 when unknown.
    pub java: u32,
}

/// The game's state as Java mods see it, once a tick.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
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
        main: entrypoints("main"),
        client: entrypoints("client"),
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

/// The `.jar` files in `dir`, by name.
pub fn jars(dir: &Path) -> Vec<PathBuf> {
    let mut found: Vec<PathBuf> = children(dir)
        .into_iter()
        .filter(|p| p.extension().is_some_and(|e| e.eq_ignore_ascii_case("jar")))
        .collect();
    found.sort();
    found
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
    let mut homes: Vec<PathBuf> = env("JAVA_HOME").map(PathBuf::from).into_iter().collect();
    // The launcher's runtime/<component>/<platform>/<component>/ (on macOS, its jre.bundle).
    let minecraft = if cfg!(target_os = "macos") {
        user_home.map(|h| h.join("Library/Application Support/minecraft/runtime"))
    } else if cfg!(windows) {
        env("APPDATA").map(|a| PathBuf::from(a).join(".minecraft").join("runtime"))
    } else {
        user_home.map(|h| h.join(".minecraft/runtime"))
    };
    for component in minecraft.iter().flat_map(|r| children(r)) {
        for platform in children(&component) {
            for home in children(&platform) {
                homes.push(home.join("jre.bundle/Contents/Home"));
                homes.push(home);
            }
        }
    }
    if cfg!(target_os = "macos") {
        let system = Path::new("/Library/Java/JavaVirtualMachines");
        homes.extend(
            children(system)
                .into_iter()
                .map(|j| j.join("Contents/Home")),
        );
    } else if !cfg!(windows) {
        homes.extend(children(Path::new("/usr/lib/jvm")));
    }
    homes
}

/// The Java home Kiln runs: the `java_home` setting or KILN_JAVA_HOME when set, else the newest installed
/// Java, at least `min` (the mods' own need, never under [`MIN_JAVA`]).
pub fn find_java(
    setting: Option<&str>,
    env: &dyn Fn(&str) -> Option<String>,
    user_home: Option<&Path>,
    min: u32,
) -> Result<PathBuf, String> {
    let min = min.max(MIN_JAVA);
    // An empty setting or variable is no choice.
    let chosen = setting
        .map(str::to_owned)
        .or_else(|| env("KILN_JAVA_HOME"))
        .filter(|h| !h.trim().is_empty());
    if let Some(home) = chosen.map(|h| PathBuf::from(h.trim())) {
        return if java_program(&home).is_file() {
            Ok(home)
        } else {
            Err(format!("no Java at {}", home.display()))
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
        .ok_or_else(|| format!("no Java {min} or newer found"))
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

/// The `tick` command's fields for client tick `n` (Host.java reads them).
pub fn tick_fields(n: i64, s: &Snapshot, gui: (u32, u32), fps: u32) -> Vec<String> {
    let flag = |b: bool| if b { "1" } else { "0" }.to_owned();
    vec![
        n.to_string(),
        flag(s.connected),
        flag(s.in_world),
        flag(s.paused),
        s.game_time.to_string(),
        s.dimension.clone(),
        gui.0.to_string(),
        gui.1.to_string(),
        fps.to_string(),
    ]
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
    /// Keys pressed since the last tick, for Java.
    keys: Vec<String>,
    /// Advancements Java has heard of in this world.
    advancements: BTreeSet<String>,
    draw: Vec<HudOp>,
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
        for key in std::mem::take(&mut self.keys) {
            self.java.send("key", &[&key])?;
        }
        for id in s.advancements.iter().flatten() {
            if self.advancements.insert(id.clone()) {
                self.java.send("adv", &[id])?;
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
                }
                Reply::Loaded(class) | Reply::Failed(class, _) if self.awaiting == 0 => {
                    log::warn!("Kiln: unexpected answer for {class}");
                }
                Reply::Loaded(class) => self.answered(&class, None, &mut chat),
                Reply::Failed(class, why) => self.answered(&class, Some(why), &mut chat),
                Reply::Draw(ops) => self.draw = ops,
                Reply::Items(ids) => set_server_items(ids),
                Reply::Listens(events) => set_server_events(events),
                Reply::Blocks(blocks) => set_server_blocks(blocks),
                // The server's own channel has them (kiln_jvm).
                Reply::Ask(..) => {}
                // Only Kiln's loot run asks (kiln_loot).
                Reply::Loot(..) => {}
                // The server's own channel has them (kiln_jvm).
                Reply::Return(_) => {}
                Reply::Log(text) => log::info!("Kiln: [java] {text}"),
                Reply::Chat(text) => chat.push(text),
                Reply::Done(_) => {}
                Reply::Exited(why) => {
                    log::warn!("Kiln: Java stopped: {why}");
                    chat.push("Kiln: Java stopped (kiln/java.log says why)".to_owned());
                    return (chat, Some(why));
                }
            }
        }
        (chat, None)
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
    /// `Block.popResource`: `count` of `item` dropped at `pos`.
    Drop {
        pos: [i32; 3],
        item: String,
        count: u32,
    },
    /// `scheduleTick`: a tick of `block` at `pos` in `delay` game ticks.
    Schedule {
        pos: [i32; 3],
        block: String,
        delay: i32,
    },
    /// `getStateForPlacement`'s answer: the state a placed block takes, or None for no placement.
    Placement(Option<String>),
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

/// A player's fields in a server call (kiln/java/kiln/Server.java reads them): 13, the attributes joined by `;`.
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
    let words: Vec<&str> = rest.split(' ').collect();
    let number = |i: usize| words.get(i)?.parse::<f64>().ok();
    Some(match (kind, words.as_slice()) {
        ("attribute", [id, _]) => Effect::Attribute {
            id: (*id).to_owned(),
            base: number(1)?,
        },
        ("health", [_]) => Effect::Health(number(0)? as f32),
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
        ("drop", [x, y, z, item, count]) => Effect::Drop {
            pos: [x.parse().ok()?, y.parse().ok()?, z.parse().ok()?],
            item: (*item).to_owned(),
            count: count.parse().ok()?,
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
    calls: u64,
}

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

fn set_server_blocks(blocks: Vec<String>) {
    if let Ok(mut server) = SERVER.lock()
        && let Some(side) = server.as_mut()
    {
        log::info!("Kiln: Java mods' blocks with code: {}", blocks.join("; "));
        side.blocks = blocks
            .iter()
            .filter_map(|b| b.split_once(' '))
            .map(|(id, what)| (id.to_owned(), what.split(',').map(str::to_owned).collect()))
            .collect();
    }
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

/// The server's thread: whether a Java mod hears `event` (`join`, `respawn`).
pub fn hears(event: &str) -> bool {
    SERVER
        .lock()
        .ok()
        .and_then(|s| s.as_ref().map(|side| side.events.contains(event)))
        .unwrap_or(false)
}

/// The server's thread: whether a Java mod gave `item` a use of its own (only those are worth a call).
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
        |side| side.items.contains(item),
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
/// when no Java mod's block has a use, or the call failed (logged).
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
        |side| side.blocks.get(id).is_some_and(|w| w.contains("use")),
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
    let mut server = SERVER.lock().ok()?;
    let side = server.as_mut()?;
    if !wanted(side) {
        return None;
    }
    side.calls += 1;
    let fields = fields(side.calls);
    let fields: Vec<&str> = fields.iter().map(String::as_str).collect();
    let answer = match side
        .link
        .call(side.calls, command, &fields, CALL_TIMEOUT, answer)
    {
        Ok(answer) => answer,
        Err(why) => {
            log::warn!("Kiln: {what}: {why}");
            return None;
        }
    };
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
    }
    chat
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
            let (next, mut chat) = start(&kiln_dir(data_dir), java_home(), font);
            *state = next;
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

/// A key went down while playing (`key.keyboard.j`): Java hears of it with the next tick.
pub fn key(name: &str) {
    STATE.with(|s| {
        if let State::Running(r) = &mut *s.borrow_mut() {
            r.keys.push(name.to_owned());
        }
    });
}

/// The HUD's size in GUI pixels, as the game draws it.
pub fn set_gui(width: f32, height: f32) {
    GUI.set((width.max(0.0) as u32, height.max(0.0) as u32));
}

/// Starts Java with Kiln's runtime and the mods in `dir`/mods, and asks it to run their client entrypoints.
fn start(
    dir: &Path,
    java_home: Option<String>,
    font: impl FnOnce() -> Vec<u32>,
) -> (State, Vec<String>) {
    let stop = |why: &str, detail: String| {
        log::warn!("Kiln: {detail}");
        (
            State::Stopped(why.to_owned()),
            vec![format!("Kiln: {detail}")],
        )
    };
    let files = jars(&dir.join("mods"));
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
    let mut chat = Vec::new();
    let mut mods = Vec::new();
    for file in &files {
        match read_jar(file) {
            // A data-only mod (structures, loot): structures.rs hands its data to the game; Java has nothing to run.
            Ok(m) if m.entrypoints() == 0 => log::info!("Kiln: {} is data only", m.name),
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
        Err(e) => return stop("no Java", e),
    };
    // Kiln's runtime, then the libraries Minecraft ships (lib/), then the mods: as in the game, a mod sees Minecraft's
    // versions of them.
    let mut class_path = vec![runtime];
    class_path.extend(jars(&dir.join("lib")));
    class_path.extend(files);
    let started = Instant::now();
    let mut java = match JavaHost::start(&home, &class_path, dir) {
        Ok(java) => java,
        Err(e) => return stop("Java didn't start", e),
    };
    log::info!("Kiln: starting Java from {}", home.display());
    set_server(java.take_server_link().map(|link| ServerSide {
        link,
        items: BTreeSet::new(),
        events: BTreeSet::new(),
        blocks: BTreeMap::new(),
        calls: 0,
    }));
    let mut awaiting = 0;
    let mut sent = java.send("font", &[&font_field(&font())]);
    // As Fabric: every mod's main entrypoints, then every mod's client ones.
    let main = mods.iter().flat_map(|m| m.main.iter().map(|c| (c, "main")));
    let client = mods
        .iter()
        .flat_map(|m| m.client.iter().map(|c| (c, "client")));
    for (class, kind) in main.chain(client) {
        sent = sent.and_then(|()| java.send("load", &[class, kind]));
        awaiting += 1;
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
        keys: Vec::new(),
        advancements: BTreeSet::new(),
        draw: Vec::new(),
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
        assert_eq!(fields.len(), 4 + 2 * 13);
        assert_eq!(fields[..4], ["4", "/w", "7", "0"]);
        assert_eq!(fields[4 + 7], "0");
        assert_eq!(
            fields[4 + 13 + 12],
            "minecraft:max_health 20 20;minecraft:movement_speed 0.1 0.13"
        );
        let joined = join_fields(5, Path::new("/w"), 7, &new);
        assert_eq!(joined.len(), 3 + 13);
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
        assert_eq!(fields.len(), 9 + 13);
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
            parse_effect("drop 1 64 2 minecraft:wheat 2"),
            Some(Effect::Drop {
                pos: [1, 64, 2],
                item: "minecraft:wheat".into(),
                count: 2
            })
        );
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
    }

    #[test]
    fn without_java_no_item_has_a_use() {
        assert!(!has_use("heart_crystals:heart_crystal"));
        assert!(!hears("join"));
        assert!(!block_does("mymod:lamp", "use"));
        let mut air = |_: &[String]| "minecraft:air".to_owned();
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
        assert_eq!(status_line(None, None), "Kiln 0.2.0: no Java mods");
        assert_eq!(status_line(Some(0), None), "Kiln 0.2.0: no Java mods");
        assert_eq!(status_line(Some(1), None), "Kiln 0.2.0: 1 Java mod");
        assert_eq!(status_line(Some(3), None), "Kiln 0.2.0: 3 Java mods");
        assert_eq!(status_line(Some(3), Some("no Java")), "Kiln 0.2.0: no Java");
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
        // A new world: the window starts over at 0 and takes the next list, whatever its number.
        forget_server_advancements();
        assert_eq!(server_advancements(&mut seen), None);
        seen = 0;
        publish_server_advancements(Vec::new());
        assert_eq!(server_advancements(&mut seen), Some(Vec::new()));
    }

    #[test]
    fn with_no_mods_kiln_makes_its_folder_and_names_it() {
        let dir = scratch("empty").join("kiln");
        let (state, chat) = start(&dir, None, Vec::new);
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
        assert_eq!((bare.name.as_str(), bare.entrypoints()), ("bare", 0));
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
        assert_eq!(none, "no Java 99 or newer found");
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
        assert_eq!(class_java(&[0xCA, 0xFE, 0xBA, 0xBE, 0, 0]), None);
        assert_eq!(class_java(b"PK\x03\x04\0\0\0\x45"), None);
        assert_eq!(class_java(&[]), None);
    }
}
