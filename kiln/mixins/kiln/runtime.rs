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
use std::sync::atomic::{AtomicU64, Ordering};
use std::time::{Duration, Instant};

use crate::game::kiln_jvm::{HudOp, JavaHost, Play, Reply, Return, ServerLink, hex, java_program, unhex};

/// The mod's id (its settings folder and Options > Mods row), name and version.
pub const ID: &str = "kiln";
pub const NAME: &str = "Kiln";
pub const VERSION: &str = "0.4.1";
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
    /// Blocks a tick.
    pub velocity: [f64; 3],
    /// Yaw and pitch in degrees.
    pub rotation: (f32, f32),
    /// The hotbar slot in hand, 0 to 8.
    pub selected: usize,
    /// The 36 main inventory slots that hold something: (slot, the item as `Worn`, its slot name unused).
    pub inventory: Vec<(usize, Worn)>,
    /// The options mods read: field of view in degrees, render distance in chunks, GUI scale and language
    /// (`en_us`).
    pub fov: f32,
    pub render_distance: i32,
    pub gui_scale: u32,
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
    /// The messages that came to the chat since the last snapshot, oldest first, as plain text: a player's (true)
    /// or the game's (false). Not the lines Kiln's mods wrote there themselves.
    pub messages: Vec<(bool, String)>,
}

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
pub fn chunk_changes(before: &BTreeSet<(i32, i32)>, now: &BTreeSet<(i32, i32)>) -> (String, String) {
    let list = |set: std::collections::btree_set::Difference<'_, (i32, i32)>| {
        set.map(|(x, z)| format!("{x},{z}")).collect::<Vec<_>>().join(";")
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
            format!("velocity={} {} {}", self.velocity[0], self.velocity[1], self.velocity[2]),
            format!("yaw={}", self.rotation.0),
            format!("pitch={}", self.rotation.1),
            format!("selected={}", self.selected),
            format!("fov={}", self.fov),
            format!("render_distance={}", self.render_distance),
            format!("gui_scale={}", self.gui_scale),
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
                    .map(|p| format!("{:032x}:{}:{}:{}", p.uuid, p.game_mode, p.latency, percent(&p.name)))
                    .collect::<Vec<_>>()
                    .join(",")
            ),
            format!("window={},{},{}", self.window[0], self.window[1], self.window[2]),
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
            format!("screen={}", percent(&self.screen)),
        ]
        .join(";")
    }

    /// The inventory as the tick carries it: `<slot> <item> <count> <damage> <max damage>`, joined by `;`.
    pub fn inventory_field(&self) -> String {
        self.inventory
            .iter()
            .map(|(slot, w)| format!("{slot} {} {} {} {}", w.item, w.count, w.damage, w.max_damage))
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

/// The Java mods turned off now; true when that changed.
pub fn set_off(off: BTreeSet<String>) -> bool {
    let mut now = OFF.lock().unwrap_or_else(|e| e.into_inner());
    if *now == off {
        return false;
    }
    *now = off;
    true
}

/// Whether the Java mod `mod_id` is on in Options > Mods (every mod is, until turned off).
pub fn mod_on(mod_id: &str) -> bool {
    !OFF.lock().unwrap_or_else(|e| e.into_inner()).contains(mod_id)
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
}

static MOD_KEYS: Mutex<ModKeys> = Mutex::new(ModKeys {
    rows: BTreeMap::new(),
    heard: BTreeSet::new(),
    changed: false,
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
        log::warn!("Kiln: {} didn't read: the mods' keys show once Java says them", keys_file(kiln_dir).display());
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
            std::fs::create_dir_all(file.parent().unwrap_or(kiln_dir)).map_err(|e| e.to_string())?;
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
            let label = if groups.len() > 1 { format!("{group}: {label}") } else { label.clone() };
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
        .flat_map(|(row, keys)| keys.iter().map(|k| (row.clone(), k[0].clone(), k[3].clone())))
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
        all.rows.keys().cloned().chain(all.heard.iter().cloned()).collect()
    };
    Some(rows.into_iter().map(|row| {
        let keys = page_keys(&row);
        (row, keys)
    }).collect())
}

/// Whose keys Java told of, by the mods' rows: the ones the entrypoint class `owner` registered are its mod's, even
/// none (it has none, or none any longer); with no owner (keys registered later), each key is the mod's whose id its
/// name holds (`key.<mod id>.<what>`). A key of no mod Kiln can tell keeps the mod's own key (logged).
fn keys_by_row(mods: &[&JavaMod], owner: &str, keys: Vec<[String; 4]>) -> Vec<(String, Vec<[String; 4]>)> {
    if !owner.is_empty() {
        return match mods.iter().find(|m| m.main.iter().chain(&m.client).any(|c| c == owner)) {
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
            log::info!("Kiln: {} is no mod's key Kiln can tell: it keeps the mod's own key", key[0]);
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
fn rebinds(bound: &mut BTreeMap<String, String>, now: &BTreeMap<String, String>) -> Vec<(String, String)> {
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

/// Where the `java` program sits in a Java home, as a player reads it.
const BIN_JAVA: &str = if cfg!(windows) { r"bin\java.exe" } else { "bin/java" };

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
                format!("{self}. Install Temurin {min} (adoptium.net), or write the path to your Java in {file}")
            }
            Self::At { setting: true, .. } => {
                format!("{self}. {file} should hold the path to the folder that holds {BIN_JAVA}")
            }
            Self::At { setting: false, .. } => {
                format!("{self}. KILN_JAVA_HOME should hold the path to the folder that holds {BIN_JAVA}")
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
        .map(|w| format!("{} {} {} {} {}", w.slot, w.item, w.count, w.damage, w.max_damage))
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
        for (chat, text) in &s.state.messages {
            self.java.send("message", &[if *chat { "chat" } else { "game" }, text])?;
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
                Reply::ChatComponent(component) => CHAT_COMPONENTS.with(|c| c.borrow_mut().push(component)),
                Reply::Commands(names) => CLIENT_COMMANDS.with(|c| *c.borrow_mut() = names),
                Reply::Keys(owner, keys) => self.keys_heard(&owner, keys),
                Reply::Send(attended, command) => SENT.with(|c| c.borrow_mut().push((attended, command))),
                Reply::Play(play) => PLAYED.with(|p| p.borrow_mut().push(play)),
                Reply::Net(data) => NET_OUT.with(|n| n.borrow_mut().push(data)),
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
    /// Commands the mods sent as the player, for the game to send: (attended, the command without its slash).
    static SENT: RefCell<Vec<(bool, String)>> = const { RefCell::new(Vec::new()) };
    /// What the mods played for the window's player since the game last took it: sounds, particles, toasts.
    static PLAYED: RefCell<Vec<Play>> = const { RefCell::new(Vec::new()) };
    /// The mods' lines for the window's chat since the game last took them, as vanilla's JSON with their style.
    static CHAT_COMPONENTS: RefCell<Vec<serde_json::Value>> = const { RefCell::new(Vec::new()) };
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
pub fn players_answer(listed: &[Online], now: Option<&PlayerSnapshot>, view_distance: i32) -> String {
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
    /// The player's experience: its level, the progress towards the next and the total points.
    Experience {
        level: i32,
        progress: f32,
        total: i32,
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
    /// What the mod's block entity at `pos` saves now: its compound, in SNBT (`{count:5}`).
    BlockEntity {
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
            let clear = self.slots.iter().all(|&(_, sx, sy)| apart(gauge, [sx - 1, sy - 1, 18, 18]))
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
    (at(0).clamp(0, 1000) as f32 / 1000.0, progress, halves(data, 2), halves(data, 4))
}

/// A machine's tank, from its data slots after the energy (`MachineData`): how full it is (0 to 1), its fluid's colour
/// (`0xRRGGBB`, black when empty), and the amount and capacity in millibuckets; None for a machine without one.
pub fn machine_tank(data: &[i32]) -> Option<(f32, u32, u64, u64)> {
    if data.len() < 12 {
        return None;
    }
    let colour = data[7] as u32 & 0xFFF;
    // 4 bits a channel, widened as #RGB is: 0xA becomes 0xAA.
    let rgb = [8, 4, 0].into_iter().fold(0, |rgb, shift| (rgb << 8) | ((colour >> shift) & 0xF) * 0x11);
    Some((data[6].clamp(0, 1000) as f32 / 1000.0, rgb, halves(data, 8), halves(data, 10)))
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
    if lengths != [8, 4, 4, 4, 12] || !parts.iter().all(|p| p.chars().all(|c| c.is_ascii_hexdigit())) {
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
    if kind == "block_entity" {
        // The compound's strings may hold spaces: it is all the rest.
        let mut parts = rest.splitn(4, ' ');
        let mut coordinate = || parts.next()?.parse::<i32>().ok();
        let pos = [coordinate()?, coordinate()?, coordinate()?];
        let data = parts.next()?.to_owned();
        return Some(Effect::BlockEntity { pos, data });
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
        let numbers: Vec<i32> = rest.split(' ').map(|n| n.parse().ok()).collect::<Option<_>>()?;
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
        ("xp", [level, _, total]) => Effect::Experience {
            level: level.parse().ok()?,
            progress: number(1)? as f32,
            total: total.parse().ok()?,
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
    /// Block id → the type of the block entity it makes (`mymod:counter`).
    entities: BTreeMap<String, String>,
    /// Block id → its block entity's item slots, when it is a container.
    slots: BTreeMap<String, Slots>,
    calls: u64,
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

/// The server's thread: whether a Java mod hears `event` (`join`, `respawn`, `connect`, `disconnect`...).
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
                log::warn!("Kiln: a payload from the server dropped: {NET_EARLY_MAX} wait for Java already");
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
/// `state` and whose saved compound is `saved` (SNBT).
#[allow(clippy::too_many_arguments)]
pub fn block_entity_tick(
    world: &Path,
    game_time: u64,
    dimension: &str,
    pos: [i32; 3],
    state: &str,
    entity: &str,
    saved: &str,
    answer: &mut dyn FnMut(&[String]) -> String,
) -> Option<Outcome> {
    let mut rest = vec![dimension.to_owned()];
    rest.extend(pos.map(|c| c.to_string()));
    rest.extend([state.to_owned(), entity.to_owned(), saved.to_owned()]);
    block_call(
        "block_entity_tick",
        "ticks",
        state,
        world,
        game_time,
        rest,
        answer,
    )
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
    side.calls += 1;
    let fields = fields(side.calls);
    let fields: Vec<&str> = fields.iter().map(String::as_str).collect();
    let mut answer = match side
        .link
        .call(side.calls, command, &fields, CALL_TIMEOUT, answer)
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

/// Outside a world (the title screen): when a Java mod was turned on or off in Options > Mods since Kiln started
/// Java, Kiln stops it, and the next world starts it again with the mods that are on. True when it stopped Java.
pub fn restart_for_switches() -> bool {
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
        log::info!("Kiln: Java mods were turned on or off; Java starts again with the next world");
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

/// The window's thread: the sounds, particles and toasts the mods played since the last call, for the game to play.
pub fn take_played() -> Vec<Play> {
    PLAYED.with(|p| std::mem::take(&mut *p.borrow_mut()))
}

/// The window's thread: the mods' chat lines since the last call, as vanilla's JSON with their style (a colour,
/// bold, italic), for the game's chat. Kiln's own lines are plain text, which `tick` and `frame` return.
pub fn take_chat_components() -> Vec<serde_json::Value> {
    CHAT_COMPONENTS.with(|c| std::mem::take(&mut *c.borrow_mut()))
}

/// The HUD's size in GUI pixels, as the game draws it.
pub fn set_gui(width: f32, height: f32) {
    GUI.set((width.max(0.0) as u32, height.max(0.0) as u32));
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
    let files = mod_jars(dir);
    STARTED_OFF.with(|s| *s.borrow_mut() = Some(OFF.lock().unwrap_or_else(|e| e.into_inner()).clone()));
    if files.is_empty() && !jars(&dir.join("mods")).is_empty() {
        log::info!("Kiln: every Java mod in {} is turned off", dir.join("mods").display());
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
            Ok(m) if m.entrypoints() == 0 && !m.mixins => log::info!("Kiln: {} is data only", m.name),
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
        entities: BTreeMap::new(),
        slots: BTreeMap::new(),
        calls: 0,
        late: BTreeMap::new(),
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
        dir: dir.to_owned(),
        keys: Vec::new(),
        bound: BTreeMap::new(),
        commands: Vec::new(),
        advancements: BTreeSet::new(),
        chunks: BTreeSet::new(),
        chunk_dimension: String::new(),
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
            parse_effect("drop 1 64 2 minecraft:wheat 2"),
            Some(Effect::Drop {
                pos: [1, 64, 2],
                item: "minecraft:wheat".into(),
                count: 2
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
            server_tick_fields(8, Path::new("/w"), 5, TickPhase::LevelStart, "minecraft:the_nether"),
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
        assert_eq!(take_listens(&mut vec!["sound a 0 0 0 0 1 1".to_owned()]), None);
    }

    #[test]
    fn payloads_cross_as_effects_calls_and_uuids() {
        let uuid = 0x0123_4567_89ab_cdef_0123_4567_89ab_cdef;
        assert_eq!(parse_uuid(&uuid_text(uuid)), Some(uuid));
        assert_eq!(parse_uuid("01234567-89AB-CDEF-0123-456789ABCDEF"), Some(uuid));
        assert_eq!(parse_uuid("00000000-0000-0000-0000-000000c0ffee"), Some(0xc0ffee));
        assert_eq!(parse_uuid("0123456789abcdef0123456789abcdef"), None, "no dashes");
        assert_eq!(parse_uuid("01234567-89ab-cdef-0123-456789abcde"), None, "a digit short");
        assert_eq!(parse_uuid("01234567-89ab-cdef-0123-456789abcdeg"), None);
        assert_eq!(parse_uuid("+1234567-89ab-cdef-0123-456789abcdef"), None, "a sign isn't a digit");
        assert_eq!(
            parse_effect("net 01234567-89ab-cdef-0123-456789abcdef 0a6b696c6e3a70696e6703"),
            Some(Effect::Net {
                player: uuid,
                data: b"\x0akiln:ping\x03".to_vec()
            })
        );
        assert_eq!(parse_effect("net 01234567-89ab-cdef-0123-456789abcdef 0a6"), None, "odd hex");
        assert_eq!(parse_effect("net someone 0a"), None);
        assert_eq!(parse_effect("net 01234567-89ab-cdef-0123-456789abcdef"), None);
        let fields = from_client_fields(7, Path::new("/saves/New World"), 1200, &[0x0a, 0xff], &crystal_user());
        assert_eq!(fields[..5], ["7", "/saves/New World", "1200", "0aff", "01234567-89ab-cdef-0123-456789abcdef"]);
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
            parse_effect("block_entity 1 65 -2 {count:5,name:\"a b\"}"),
            Some(Effect::BlockEntity {
                pos: [1, 65, -2],
                data: "{count:5,name:\"a b\"}".into()
            })
        );
        assert!(parse_effect("block_entity 1 65").is_none());
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
            parse_effect(r#"held main {components:{"minecraft:custom_name":"A b"},count:1,id:"minecraft:stick"}"#),
            Some(Effect::Held {
                hand: Hand::Main,
                stack: r#"{components:{"minecraft:custom_name":"A b"},count:1,id:"minecraft:stick"}"#.into()
            })
        );
        assert_eq!(
            parse_effect("xp 9 0.2 140"),
            Some(Effect::Experience {
                level: 9,
                progress: 0.2,
                total: 140
            })
        );
        assert!(parse_effect("held both {}").is_none());
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
            [in_kiln.join("energy.jar"), in_kiln.join("core.jar"), kiln.join("tech.jar")]
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
        assert_eq!(nested_jars(&dir.join("tech.jar"), &cache), [cache.join("energy.jar")]);
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
        assert_eq!(status_line(None, None), "Kiln 0.4.1: no Java mods");
        assert_eq!(status_line(Some(0), None), "Kiln 0.4.1: no Java mods");
        assert_eq!(status_line(Some(1), None), "Kiln 0.4.1: 1 Java mod");
        assert_eq!(status_line(Some(3), None), "Kiln 0.4.1: 3 Java mods");
        assert_eq!(status_line(Some(3), Some("no Java")), "Kiln 0.4.1: no Java");
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
        assert!(fields[after + 3].starts_with("single=0;day=0;"), "{}", fields[after + 3]);
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
        carrying.state.inventory = vec![(0, dressed.equipment[1].clone()), (9, dressed.equipment[0].clone())];
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
        assert_eq!((bare.name.as_str(), bare.entrypoints(), bare.mixins), ("bare", 0, false));
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
        tick(&game.join("horizon-mods/.horizon-mod-data/kiln"), || None, Vec::new, Snapshot::default());
        assert_eq!(
            status_lines(),
            [(status_line(None, Some("kiln-runtime.jar missing")), TROUBLE)]
        );
    }

    #[test]
    fn without_java_mods_the_status_box_stays_away() {
        let game = scratch("status-none");
        tick(&game.join("horizon-mods/.horizon-mod-data/kiln"), || None, Vec::new, Snapshot::default());
        assert_eq!(status_lines(), []);
    }

    #[test]
    fn a_data_only_mod_is_no_java_mod() {
        use std::io::Write;
        let dir = scratch("data-only").join("kiln");
        std::fs::create_dir_all(dir.join("mods")).unwrap();
        std::fs::write(dir.join("kiln-runtime.jar"), b"").unwrap();
        let mut zip = zip::ZipWriter::new(std::fs::File::create(dir.join("mods/ships.jar")).unwrap());
        zip.start_file("fabric.mod.json", zip::write::SimpleFileOptions::default()).unwrap();
        zip.write_all(br#"{"id": "ships"}"#).unwrap();
        zip.finish().unwrap();
        // Its structures go to the game (structures.rs); Java isn't looked for, let alone started.
        let (state, _) = start(&dir, Some("/no/java/here".into()), &dir.join("java_home"), Vec::new);
        assert!(matches!(state, State::Stopped(ref why) if why == "no Java mods"));
    }

    #[test]
    fn a_mods_keys_go_on_its_page_and_the_key_chosen_there_goes_to_java() {
        use std::io::Write;
        let dir = scratch("mod-keys").join("kiln");
        std::fs::create_dir_all(dir.join("mods")).unwrap();
        let manifest = r#"{"id": "fovtoggle", "name": "FOV Toggle", "entrypoints": {"client": ["fov.Client"]}}"#;
        let mut zip = zip::ZipWriter::new(std::fs::File::create(dir.join("mods/fov.jar")).unwrap());
        zip.start_file("fabric.mod.json", zip::write::SimpleFileOptions::default()).unwrap();
        zip.write_all(manifest.as_bytes()).unwrap();
        zip.finish().unwrap();
        let fov = read_manifest(manifest).unwrap();
        let other = read_manifest(r#"{"id": "other", "entrypoints": {"main": ["o.Main"]}}"#).unwrap();
        let mods = [&fov, &other];
        let key = |name: &str, group: &str, label: &str, default: &str| {
            [name.to_owned(), group.to_owned(), label.to_owned(), default.to_owned()]
        };
        let toggle = key("key.fovtoggle.toggle", "FOV Toggle", "Toggle FOV", "key.keyboard.v");
        let zoom = key("key.fovtoggle.zoom", "Camera", "Zoom", "key.keyboard.unknown");

        // Java names the entrypoint that registered them: its mod's. One with none says so; a class no mod has, nothing.
        assert_eq!(
            keys_by_row(&mods, "fov.Client", vec![toggle.clone()]),
            [("kiln_fovtoggle".to_owned(), vec![toggle.clone()])]
        );
        assert_eq!(keys_by_row(&mods, "o.Main", Vec::new()), [("kiln_other".to_owned(), Vec::new())]);
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
            [["key.fovtoggle.toggle".to_owned(), "Toggle FOV".to_owned(), "key.keyboard.v".to_owned()]]
        );
        assert_eq!(
            mod_keys(),
            [("kiln_fovtoggle".to_owned(), "key.fovtoggle.toggle".to_owned(), "key.keyboard.v".to_owned())]
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
                ["key.fovtoggle.toggle".to_owned(), "FOV Toggle: Toggle FOV".to_owned(), "key.keyboard.v".to_owned()],
                ["key.fovtoggle.zoom".to_owned(), "Camera: Zoom".to_owned(), "key.keyboard.unknown".to_owned()],
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
        assert_eq!(take_changed_keys().unwrap()["kiln_fovtoggle"], Vec::<[String; 3]>::new());

        // The player chose B on the page: Java hears it once, before the presses, and again only when it changes.
        let mut bound = BTreeMap::new();
        let now: BTreeMap<String, String> =
            [("key.fovtoggle.toggle".to_owned(), "key.keyboard.b".to_owned())].into();
        assert_eq!(
            rebinds(&mut bound, &now),
            [("key.fovtoggle.toggle".to_owned(), "key.keyboard.b".to_owned())]
        );
        assert!(rebinds(&mut bound, &now).is_empty());
        let back: BTreeMap<String, String> =
            [("key.fovtoggle.toggle".to_owned(), "key.keyboard.v".to_owned())].into();
        assert_eq!(
            rebinds(&mut bound, &back),
            [("key.fovtoggle.toggle".to_owned(), "key.keyboard.v".to_owned())]
        );
        *mod_keys_lock() = ModKeys::default();
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
            format!("no Java at /no/java/there. KILN_JAVA_HOME should hold the path to the folder that holds {BIN_JAVA}")
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
        let mut zip = zip::ZipWriter::new(std::fs::File::create(dir.join("mods/timer.jar")).unwrap());
        zip.start_file("fabric.mod.json", zip::write::SimpleFileOptions::default()).unwrap();
        zip.write_all(br#"{"id": "timer", "entrypoints": {"client": ["timer.Timer"]}}"#).unwrap();
        zip.finish().unwrap();
        let (state, chat) = start(&dir, Some("/no/java/here".into()), &data.join("java_home"), Vec::new);
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
            slots: slots.iter().enumerate().map(|(i, &(x, y))| (i, x, y)).collect(),
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
        assert_eq!(menu(&[(20, 30), (60, 30)]).progress_arrow(), Some([41.0, 33.0, 16.0, 10.0]));
        assert_eq!(menu(&[(21, 30), (60, 30)]).progress_arrow(), None);
        // Two inputs on the output's row: from the nearer.
        assert_eq!(
            menu(&[(70, 43), (84, 43), (126, 43)]).progress_arrow(),
            Some([105.0, 46.0, 18.0, 10.0])
        );
        // Half a slot up or down is the output's row; more is another.
        assert_eq!(menu(&[(20, 39), (60, 30)]).progress_arrow(), Some([41.0, 42.0, 16.0, 10.0]));
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
            assert_eq!(menu(&[slot]).tank_gauge(), Some([x, 19.0, 20.0, 50.0]), "a slot at {slot:?}");
        }
    }

    #[test]
    fn a_machines_data_reads_back() {
        assert_eq!(machine_data(&[562, 39, 562, 0, 1000, 0]), (0.562, Some(0.39), 562, 1000));
        // Past 15 bits, the high half; no progress (-1); nothing at all.
        assert_eq!(machine_data(&[1000, -1, 0, 2, 0, 2]), (1.0, None, 65536, 65536));
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
    fn chunks_that_came_and_went() {
        let before: BTreeSet<(i32, i32)> = [(0, 0), (0, 1), (-1, 0)].into();
        let now: BTreeSet<(i32, i32)> = [(0, 0), (1, 0), (2, -3)].into();
        assert_eq!(chunk_changes(&before, &now), ("1,0;2,-3".into(), "-1,0;0,1".into()));
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
        assert!(field.contains(";effects=speed:200:1:0:1,mymod%3Aglow%20up:-1:0:1:0;"), "{field}");
        assert!(field.ends_with(";screen=inventory"), "{field}");
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
            read_manifest(&format!(r#"{{"id": "{id}", "entrypoints": {{"client": ["{class}"]}}}}"#)).unwrap()
        };
        let mut r = running(java, vec![client("a", "a.Client"), client("b", "b.Client"), client("a_copy", "a.Client")]);
        let deadline = Instant::now() + Duration::from_secs(10);
        let mut chat = Vec::new();
        while !chat.iter().any(|l| l == "end") {
            assert!(Instant::now() < deadline, "{chat:?}");
            chat.extend(r.take_replies().0);
            std::thread::sleep(Duration::from_millis(5));
        }
        assert_eq!(
            chat,
            ["Kiln: b didn't start: it broke", "Kiln: 2 of 3 Java mods running on Java 25", "end"]
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
            [vec!["adv\ta"], vec!["adv\tb"], vec![], vec![], vec!["adv\ta", "adv\tb"]]
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
        assert_eq!(heard, [vec!["key\tkey.keyboard.j", "key\tkey.keyboard.k"], vec![]]);
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
            late: BTreeMap::new(),
        }));
        let mut nothing = |_: &[String]| String::new();
        let world = Path::new("/w");
        let first = use_item(world, 0, "test:charm", Hand::Main, &crystal_user(), &mut nothing);
        assert_eq!(first, None, "the first call is answered too late");
        let second = use_item(world, 1, "test:charm", Hand::Main, &crystal_user(), &mut nothing);
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
            late: BTreeMap::new(),
        }));
        let mut nothing = |_: &[String]| String::new();
        let (world, player) = (Path::new("/w"), crystal_user());
        assert_eq!(from_client(world, 0, b"\x01", &player, &mut nothing), None, "no answer in time");
        assert!(late_payloads(player.uuid).is_empty(), "nothing came yet");
        let second = from_client(world, 1, b"\x02", &player, &mut nothing);
        assert_eq!(second, Some(Outcome { result: UseResult::Pass, effects: Vec::new() }));
        assert!(late_payloads(0xc0ffee).is_empty(), "another player's tick doesn't take it");
        let late = Outcome {
            result: UseResult::Pass,
            effects: vec![Effect::Net { player: player.uuid, data: vec![0x0a, 0x6b] }],
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
        let told = Outcome { result: UseResult::Pass, effects: vec![Effect::Tell(serde_json::json!({"text": "late"}))] };
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
        assert_eq!(player_disconnected(world, 4, &crystal_user(), &mut nothing), None, "nobody hears it");
    }
}
