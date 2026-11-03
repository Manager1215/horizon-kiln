// Java mods' changes to the game's loot tables (Fabric's LootTableEvents.MODIFY, D-052 step 4). Horizon loads its
// loot tables when a world opens, before Kiln's Java starts (with the window's first tick in the world), so Kiln
// runs the changes ahead: before a world's data packs are read (world_packs.rs), when a mod's jar uses Fabric's loot
// events and the mods changed since the last run, a Java of its own runs the mods' main entrypoints and fires MODIFY
// for the game's tables (kiln/java/kiln/Loot.java). The pools they add go to `kiln/generated/loot.json`, and
// Horizon's hook for a table as it loads (loot_table.rs) adds them. A table of Kiln's own in its data pack changes
// with them, so Horizon loads its tables again rather than keep ones it loaded before.

use std::collections::BTreeMap;
use std::io::Read;
use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

use serde_json::{Value, json};

use crate::game::kiln as m;
use crate::game::kiln_jvm::{JavaHost, Reply};
use crate::game::kiln_structures as structures;

/// What a run may take, the mods' start included.
pub const RUN: Duration = Duration::from_secs(30);
/// The pools the Java mods add, by table, in Kiln's generated folder.
pub const FILE: &str = "loot.json";
/// Kiln's own loot table in its data pack: its random sequence names the run, so new pools are new pack files to
/// Horizon.
pub const MARKER: &str = "datapack/data/kiln/loot_table/mods.json";
/// What a class that uses Fabric's loot events names.
const LOOT_EVENTS: &[u8] = b"net/fabricmc/fabric/api/loot/";

/// Whether any class in `jar` uses Fabric's loot events: only then is a run worth it.
pub fn uses_loot_events(jar: &Path) -> bool {
    let Ok(file) = std::fs::File::open(jar) else {
        return false;
    };
    let Ok(mut zip) = zip::ZipArchive::new(file) else {
        return false;
    };
    for i in 0..zip.len() {
        let Ok(mut entry) = zip.by_index(i) else {
            continue;
        };
        if !entry.name().is_ok_and(|n| n.ends_with(".class")) {
            continue;
        }
        let mut bytes = Vec::new();
        if entry.read_to_end(&mut bytes).is_ok()
            && bytes.windows(LOOT_EVENTS.len()).any(|w| w == LOOT_EVENTS)
        {
            return true;
        }
    }
    false
}

/// The pools in a run's `loot` replies, by table; a reply that isn't a JSON array is left out.
pub fn tables_of(replies: &[Reply]) -> BTreeMap<String, Value> {
    replies
        .iter()
        .filter_map(|r| match r {
            Reply::Loot(id, pools) => match serde_json::from_str::<Value>(pools) {
                Ok(pools @ Value::Array(_)) => Some((id.clone(), pools)),
                _ => {
                    log::warn!("Kiln: left out the Java mods' pools for {id}: not a JSON list");
                    None
                }
            },
            _ => None,
        })
        .collect()
}

/// Writes the pools (`generated`/loot.json, with the mods' `stamp`) and the marker table in Kiln's data pack; with
/// no pools, no marker.
pub fn write(
    generated: &Path,
    stamp: &str,
    tables: &BTreeMap<String, Value>,
) -> std::io::Result<()> {
    std::fs::create_dir_all(generated)?;
    let file = json!({ "stamp": stamp, "tables": tables });
    std::fs::write(generated.join(FILE), serde_json::to_vec_pretty(&file)?)?;
    let marker = generated.join(MARKER);
    if tables.is_empty() {
        let _ = std::fs::remove_file(&marker);
        return Ok(());
    }
    std::fs::create_dir_all(marker.parent().unwrap_or(generated))?;
    let sequence = format!("kiln:mods/{:016x}", fnv(&serde_json::to_vec(&file)?));
    let table = json!({ "type": "minecraft:empty", "pools": [], "random_sequence": sequence });
    std::fs::write(&marker, table.to_string())?;
    let mcmeta = generated.join("datapack/pack.mcmeta");
    if !mcmeta.is_file() {
        let pack = json!({"pack": {
            "description": "Kiln: the Java mods' data",
            "pack_format": structures::PACK_FORMAT,
            "min_format": structures::PACK_FORMAT,
            "max_format": structures::PACK_FORMAT,
        }});
        std::fs::write(mcmeta, pack.to_string())?;
    }
    Ok(())
}

/// A stable hash (the standard library's isn't promised to be).
fn fnv(bytes: &[u8]) -> u64 {
    bytes.iter().fold(0xcbf2_9ce4_8422_2325, |h, &b| {
        (h ^ u64::from(b)).wrapping_mul(0x0100_0000_01b3)
    })
}

/// The stamp written with the pools, if `generated` has them.
fn written_stamp(generated: &Path) -> Option<String> {
    let bytes = std::fs::read(generated.join(FILE)).ok()?;
    let file: Value = serde_json::from_slice(&bytes).ok()?;
    Some(file.get("stamp")?.as_str()?.to_owned())
}

/// Before a world's packs are read: the Java mods' loot changes, run again only when the mods or Kiln's runtime
/// changed. Runs after structures.rs's `prepare`, which can empty the generated folder.
pub fn prepare(kiln_dir: &Path) {
    // Several of Horizon's world loading functions ask, on more than one thread: one run at a time.
    static RUNNING: std::sync::Mutex<()> = std::sync::Mutex::new(());
    let _one = RUNNING.lock().unwrap_or_else(|e| e.into_inner());
    let jars = m::jars(&kiln_dir.join("mods"));
    let runtime = kiln_dir.join("kiln-runtime.jar");
    let stamp = structures::stamp(&[jars.clone(), vec![runtime.clone()]].concat());
    let generated = structures::generated_dir(kiln_dir);
    if written_stamp(&generated).as_deref() == Some(stamp.as_str()) {
        return;
    }
    let tables = if runtime.is_file() && jars.iter().any(|j| uses_loot_events(j)) {
        match run(kiln_dir, &jars) {
            Ok(tables) => tables,
            Err(why) => {
                // Not written: the next world tries again.
                log::warn!("Kiln: the Java mods' loot changes didn't run: {why}");
                return;
            }
        }
    } else {
        BTreeMap::new()
    };
    if !tables.is_empty() {
        log::info!("Kiln: the Java mods add to {} loot tables", tables.len());
    }
    if let Err(e) = write(&generated, &stamp, &tables) {
        log::warn!("Kiln: can't write the Java mods' loot changes: {e}");
    }
}

/// A Java of its own: every mod's main entrypoints, then `loot`; its answers, until it is done.
fn run(kiln_dir: &Path, jars: &[PathBuf]) -> Result<BTreeMap<String, Value>, String> {
    let mods: Vec<m::JavaMod> = jars.iter().filter_map(|j| m::read_jar(j).ok()).collect();
    let needed = mods.iter().map(|m| m.java).max().unwrap_or(0);
    let user_home = std::env::var_os("HOME").map(PathBuf::from);
    let env = |key: &str| std::env::var(key).ok();
    let home = m::find_java(None, &env, user_home.as_deref(), needed)?;
    let mut class_path = vec![kiln_dir.join("kiln-runtime.jar")];
    class_path.extend(m::jars(&kiln_dir.join("lib")));
    class_path.extend(jars.iter().cloned());
    let mut java = JavaHost::start_logged(&home, &class_path, kiln_dir, "java-loot.log")?;
    for class in mods.iter().flat_map(|m| &m.main) {
        java.send("load", &[class, "main"])?;
    }
    java.send("loot", &[])?;
    let deadline = Instant::now() + RUN;
    let mut replies = Vec::new();
    while Instant::now() < deadline {
        for reply in java.replies() {
            match reply {
                Reply::Done(command) if command == "loot" => return Ok(tables_of(&replies)),
                Reply::Exited(why) => return Err(format!("Java stopped: {why}")),
                Reply::Failed(class, why) => log::warn!("Kiln: {class} didn't start: {why}"),
                Reply::Log(text) => log::info!("Kiln: [java] {text}"),
                reply => replies.push(reply),
            }
        }
        std::thread::sleep(Duration::from_millis(10));
    }
    Err(format!("no answer from Java in {} s", RUN.as_secs()))
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;

    fn scratch(name: &str) -> PathBuf {
        let dir = std::env::temp_dir().join(format!("kiln-loot-{name}-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).unwrap();
        dir
    }

    fn jar(dir: &Path, name: &str, files: &[(&str, &[u8])]) -> PathBuf {
        let path = dir.join(name);
        let mut zip = zip::ZipWriter::new(std::fs::File::create(&path).unwrap());
        for (name, bytes) in files {
            zip.start_file(*name, zip::write::SimpleFileOptions::default())
                .unwrap();
            zip.write_all(bytes).unwrap();
        }
        zip.finish().unwrap();
        path
    }

    #[test]
    fn only_mods_with_loot_code_need_a_run() {
        let dir = scratch("uses");
        let loot = jar(
            &dir,
            "loot.jar",
            &[(
                "a/Mod.class",
                b"\xca\xfe\xba\xbe...net/fabricmc/fabric/api/loot/v3/LootTableEvents...",
            )],
        );
        let other = jar(
            &dir,
            "other.jar",
            &[
                (
                    "a/Mod.class",
                    b"\xca\xfe\xba\xbe...net/fabricmc/api/ModInitializer",
                ),
                ("notes.txt", b"net/fabricmc/fabric/api/loot/"),
            ],
        );
        assert!(uses_loot_events(&loot));
        assert!(!uses_loot_events(&other));
        assert!(!uses_loot_events(&dir.join("missing.jar")));
    }

    #[test]
    fn pools_and_a_marker_are_written() {
        let generated = scratch("write").join("generated");
        let replies = [
            Reply::Loot(
                "minecraft:chests/simple_dungeon".into(),
                r#"[{"rolls":{"type":"minecraft:uniform","min":0,"max":1},"entries":[{"type":"minecraft:item","name":"heart_crystals:heart_crystal_shard"}]}]"#.into(),
            ),
            Reply::Loot("minecraft:chests/igloo_chest".into(), "not json".into()),
            Reply::Log("other".into()),
        ];
        let tables = tables_of(&replies);
        assert_eq!(
            tables.keys().collect::<Vec<_>>(),
            ["minecraft:chests/simple_dungeon"]
        );
        write(&generated, "jars 1", &tables).unwrap();
        assert_eq!(written_stamp(&generated).as_deref(), Some("jars 1"));
        let file: Value =
            serde_json::from_slice(&std::fs::read(generated.join(FILE)).unwrap()).unwrap();
        assert_eq!(
            file["tables"]["minecraft:chests/simple_dungeon"][0]["entries"][0]["name"],
            "heart_crystals:heart_crystal_shard"
        );
        let marker: Value =
            serde_json::from_slice(&std::fs::read(generated.join(MARKER)).unwrap()).unwrap();
        let sequence = marker["random_sequence"].as_str().unwrap().to_owned();
        assert!(sequence.starts_with("kiln:mods/"), "{sequence}");
        assert!(generated.join("datapack/pack.mcmeta").is_file());
        // Other pools, another marker: Horizon loads the tables again.
        write(&generated, "jars 2", &tables).unwrap();
        let again: Value =
            serde_json::from_slice(&std::fs::read(generated.join(MARKER)).unwrap()).unwrap();
        assert_ne!(again["random_sequence"], sequence);
        write(&generated, "jars 3", &BTreeMap::new()).unwrap();
        assert!(!generated.join(MARKER).exists());
        assert_eq!(written_stamp(&generated).as_deref(), Some("jars 3"));
    }

    #[test]
    fn without_loot_code_nothing_runs() {
        let kiln = scratch("prepare");
        std::fs::create_dir_all(kiln.join("mods")).unwrap();
        std::fs::write(kiln.join("kiln-runtime.jar"), b"").unwrap();
        jar(
            &kiln.join("mods"),
            "plain.jar",
            &[(
                "fabric.mod.json",
                br#"{"id":"plain","entrypoints":{"main":["a.Mod"]}}"#,
            )],
        );
        prepare(&kiln);
        let generated = structures::generated_dir(&kiln);
        let file: Value =
            serde_json::from_slice(&std::fs::read(generated.join(FILE)).unwrap()).unwrap();
        assert_eq!(file["tables"], json!({}));
        assert!(!generated.join(MARKER).exists());
    }
}
