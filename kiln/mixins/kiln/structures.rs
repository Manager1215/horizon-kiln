// Structure mods as Horizon reads them (D-048, D-049). A Java mod's world generation is data Horizon's generator
// reads (structures, structure sets, template pools, processor lists, their `.nbt` pieces, worldgen tags, loot), but
// not from a jar. Before a world opens, Kiln copies it out of the mods' jars into `<game folder>/kiln/generated/`:
// `datapack/`, a data pack every world gets (datapack.rs), and `structure/<namespace>/<path>.nbt`, the pieces, where
// structure_template.rs finds them. Biome and tag ids without a namespace get `minecraft:`, as Minecraft reads them
// and Horizon doesn't. Only reads jars and writes files: no game types, so `cargo test` covers it.

use std::io::Read;
use std::path::{Component, Path, PathBuf};
use std::sync::Mutex;

/// The data pack format of Minecraft 26.3.
pub const PACK_FORMAT: u32 = 121;

/// The mods' jars (names, sizes, times) as last prepared: an unchanged folder isn't copied again.
static PREPARED: Mutex<Option<String>> = Mutex::new(None);

/// What Kiln made of the mods' world generation.
#[derive(Debug, Default, PartialEq, Eq)]
pub struct Generated {
    /// The jars that brought any, by file name.
    pub mods: Vec<String>,
    /// Files in the data pack, and structure pieces.
    pub data: usize,
    pub pieces: usize,
    /// JSON files left out because they don't parse, as `jar: path`: one broken file would stop the world.
    pub skipped: Vec<String>,
}

/// `<kiln dir>/generated`.
pub fn generated_dir(kiln_dir: &Path) -> PathBuf {
    kiln_dir.join("generated")
}

/// Copies the world generation of the Java mods in `kiln_dir/mods` into `kiln_dir/generated`, unless the mods are
/// the same as the last time this game did. None when nothing changed.
pub fn prepare(kiln_dir: &Path) -> Option<Generated> {
    let jars = crate::game::kiln::jars(&kiln_dir.join("mods"));
    let stamp = stamp(&jars);
    let mut prepared = PREPARED.lock().unwrap_or_else(|e| e.into_inner());
    if prepared.as_deref() == Some(stamp.as_str()) {
        return None;
    }
    let generated = generate(&jars, &generated_dir(kiln_dir));
    *prepared = Some(stamp);
    for skipped in &generated.skipped {
        log::warn!("Kiln: left out {skipped}: not JSON Horizon reads");
    }
    if !generated.mods.is_empty() {
        log::info!(
            "Kiln: world generation of {} ({} data files, {} structure pieces)",
            generated.mods.join(", "),
            generated.data,
            generated.pieces
        );
    }
    Some(generated)
}

pub fn stamp(jars: &[PathBuf]) -> String {
    jars.iter()
        .map(|j| {
            let meta = std::fs::metadata(j).ok();
            let len = meta.as_ref().map_or(0, std::fs::Metadata::len);
            let time = meta
                .and_then(|m| m.modified().ok())
                .and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok())
                .map_or(0, |d| d.as_secs());
            format!("{}:{len}:{time}", j.display())
        })
        .collect::<Vec<_>>()
        .join("\n")
}

/// Writes `out` afresh from `jars`: the data pack only when some jar has world generation.
pub fn generate(jars: &[PathBuf], out: &Path) -> Generated {
    let _ = std::fs::remove_dir_all(out);
    let pack = out.join("datapack");
    let mut generated = Generated::default();
    for jar in jars {
        let Ok(file) = std::fs::File::open(jar) else {
            continue;
        };
        let Ok(mut zip) = zip::ZipArchive::new(file) else {
            continue;
        };
        let before = (generated.data, generated.pieces);
        for i in 0..zip.len() {
            let Some(Ok(name)) = zip.name_for_index(i) else {
                continue;
            };
            let name = name.into_owned();
            let Some(target) = place(&name, out, &pack) else {
                continue;
            };
            let Ok(mut entry) = zip.by_index(i) else {
                continue;
            };
            let mut bytes = Vec::new();
            if entry.read_to_end(&mut bytes).is_err() {
                continue;
            }
            let piece = target.extension().is_some_and(|e| e == "nbt");
            let bytes = if piece {
                bytes
            } else {
                match json_for_horizon(&name, &bytes) {
                    Some(json) => json,
                    None => {
                        let jar = jar.file_name().unwrap_or_default().to_string_lossy();
                        generated.skipped.push(format!("{jar}: {name}"));
                        continue;
                    }
                }
            };
            if let Some(parent) = target.parent() {
                let _ = std::fs::create_dir_all(parent);
            }
            if std::fs::write(&target, bytes).is_ok() {
                if piece {
                    generated.pieces += 1;
                } else {
                    generated.data += 1;
                }
            }
        }
        if (generated.data, generated.pieces) != before {
            generated.mods.push(
                jar.file_name()
                    .map_or_else(String::new, |n| n.to_string_lossy().into_owned()),
            );
        }
    }
    if generated.data > 0 {
        let mcmeta = serde_json::json!({"pack": {
            "description": format!("Kiln: the world generation of {}", generated.mods.join(", ")),
            "pack_format": PACK_FORMAT, "min_format": PACK_FORMAT, "max_format": PACK_FORMAT,
        }});
        let _ = std::fs::write(pack.join("pack.mcmeta"), mcmeta.to_string());
    }
    generated
}

/// The world generation Kiln copies: what structures are made of. Not terrain (noise, biomes, dimensions, features):
/// Horizon can refuse a terrain mod's data, and then a whole dimension doesn't open (D-050).
const STRUCTURE_WORLDGEN: [&str; 4] = [
    "structure",
    "structure_set",
    "template_pool",
    "processor_list",
];

/// Where a jar's entry goes, if Kiln copies it: structures' world generation, their tags and loot into the data pack
/// (`data/<namespace>/...`), structure pieces to `<out>/structure/<namespace>/<path>.nbt`. Names that would leave
/// the folder are skipped.
fn place(name: &str, out: &Path, pack: &Path) -> Option<PathBuf> {
    let path = Path::new(name);
    if !path.components().all(|c| matches!(c, Component::Normal(_))) {
        return None;
    }
    let parts: Vec<&str> = name.split('/').collect();
    let ["data", namespace, kind, rest @ ..] = parts.as_slice() else {
        return None;
    };
    if rest.is_empty() {
        return None;
    }
    match *kind {
        "structure" | "structures" if name.ends_with(".nbt") => {
            Some(out.join("structure").join(namespace).join(rest.join("/")))
        }
        "worldgen" if STRUCTURE_WORLDGEN.contains(&rest[0]) && name.ends_with(".json") => {
            Some(pack.join(name))
        }
        "loot_table" if name.ends_with(".json") => Some(pack.join(name)),
        "tags"
            if matches!(rest, ["worldgen", "structure" | "biome", ..])
                && name.ends_with(".json") =>
        {
            Some(pack.join(name))
        }
        _ => None,
    }
}

/// A JSON file as Horizon reads it, which is stricter than Minecraft: without a byte order mark, and with
/// `minecraft:` on a structure's biomes and a tag's values that name no namespace (`ocean`, `#is_ocean`). None when
/// it isn't JSON at all.
fn json_for_horizon(name: &str, bytes: &[u8]) -> Option<Vec<u8>> {
    let bytes = bytes.strip_prefix(b"\xef\xbb\xbf").unwrap_or(bytes);
    let mut json: serde_json::Value = serde_json::from_slice(bytes).ok()?;
    let field = if name.contains("/worldgen/structure/") {
        "biomes"
    } else if name.contains("/tags/") {
        "values"
    } else {
        return Some(bytes.to_vec());
    };
    let mut changed = json.get_mut(field).is_some_and(namespace_ids);
    if name.contains("/tags/worldgen/biome/") {
        changed |= json.get_mut(field).is_some_and(mod_biomes_optional);
    }
    if changed {
        serde_json::to_vec(&json).ok()
    } else {
        Some(bytes.to_vec())
    }
}

/// A biome tag's entries naming a mod's biomes (or biome tags), made optional: Kiln doesn't bring mods' biomes, and a
/// required entry that names nothing drops the whole tag (vanilla ones too, such as `minecraft:is_nether`, when a
/// terrain mod adds its biomes to them). True when it changed any.
fn mod_biomes_optional(values: &mut serde_json::Value) -> bool {
    let serde_json::Value::Array(entries) = values else {
        return false;
    };
    let mut changed = false;
    for entry in entries {
        let id = match entry {
            serde_json::Value::String(id) => id.clone(),
            serde_json::Value::Object(o) => o
                .get("id")
                .and_then(|i| i.as_str())
                .unwrap_or_default()
                .to_owned(),
            _ => continue,
        };
        if id.trim_start_matches('#').starts_with("minecraft:") {
            continue;
        }
        *entry = serde_json::json!({"id": id, "required": false});
        changed = true;
    }
    changed
}

/// Adds `minecraft:` to ids that have no namespace, in a string, a list, or `{"id": ...}` entries. True when it did.
fn namespace_ids(value: &mut serde_json::Value) -> bool {
    match value {
        serde_json::Value::String(id) if !id.contains(':') => {
            *id = match id.strip_prefix('#') {
                Some(tag) => format!("#minecraft:{tag}"),
                None => format!("minecraft:{id}"),
            };
            true
        }
        serde_json::Value::Array(items) => items
            .iter_mut()
            .fold(false, |any, v| namespace_ids(v) | any),
        serde_json::Value::Object(entry) => entry.get_mut("id").is_some_and(namespace_ids),
        _ => false,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;

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

    fn scratch(name: &str) -> PathBuf {
        let dir =
            std::env::temp_dir().join(format!("kiln-structures-{name}-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).unwrap();
        dir
    }

    #[test]
    fn a_structure_mods_data_becomes_a_pack_and_pieces() {
        let dir = scratch("ship");
        let ship = jar(
            &dir,
            "ship.jar",
            &[
                ("fabric.mod.json", br#"{"id": "ship"}"#),
                ("com/ship/Ship.class", b"\xca\xfe\xba\xbe"),
                (
                    "data/ship/worldgen/structure/ship.json",
                    br##"{"type": "minecraft:jigsaw", "biomes": ["ocean", "minecraft:river", "#is_beach"]}"##,
                ),
                (
                    "data/ship/worldgen/structure_set/ship.json",
                    br#"{"structures": [{"structure": "ship:ship", "weight": 1}]}"#,
                ),
                (
                    "data/ship/tags/worldgen/biome/has_ship.json",
                    br##"{"values": ["ocean", {"id": "deep_ocean", "required": false}, "ship:reef", "#ship:seas"]}"##,
                ),
                ("data/ship/structure/hull/big.nbt", b"\x1f\x8b nbt"),
                (
                    "data/ship/worldgen/template_pool/bom.json",
                    b"\xef\xbb\xbf{\"elements\": []}",
                ),
                ("data/ship/worldgen/processor_list/broken.json", b"{not json"),
                ("data/minecraft/worldgen/noise_settings/end.json", b"{}"),
                ("data/ship/worldgen/biome/sea.json", b"{}"),
                ("data/ship/recipe/sail.json", b"{}"),
                ("assets/ship/icon.png", b"png"),
                ("data/ship/worldgen/../../escape.json", b"{}"),
            ],
        );
        let plain = jar(
            &dir,
            "timer.jar",
            &[("fabric.mod.json", br#"{"id": "timer"}"#)],
        );
        let out = dir.join("generated");
        let generated = generate(&[plain, ship], &out);
        assert_eq!(
            generated,
            Generated {
                mods: vec!["ship.jar".into()],
                data: 4,
                pieces: 1,
                skipped: vec!["ship.jar: data/ship/worldgen/processor_list/broken.json".into()],
            }
        );
        let pack = out.join("datapack");
        let structure: serde_json::Value = serde_json::from_slice(
            &std::fs::read(pack.join("data/ship/worldgen/structure/ship.json")).unwrap(),
        )
        .unwrap();
        assert_eq!(
            structure["biomes"],
            serde_json::json!(["minecraft:ocean", "minecraft:river", "#minecraft:is_beach"])
        );
        let tag: serde_json::Value = serde_json::from_slice(
            &std::fs::read(pack.join("data/ship/tags/worldgen/biome/has_ship.json")).unwrap(),
        )
        .unwrap();
        assert_eq!(tag["values"][0], "minecraft:ocean");
        assert_eq!(tag["values"][1]["id"], "minecraft:deep_ocean");
        assert_eq!(
            tag["values"][2],
            serde_json::json!({"id": "ship:reef", "required": false}),
            "a mod's biome Kiln doesn't bring is optional"
        );
        assert_eq!(
            tag["values"][3],
            serde_json::json!({"id": "#ship:seas", "required": false})
        );
        assert_eq!(
            std::fs::read(out.join("structure/ship/hull/big.nbt")).unwrap(),
            b"\x1f\x8b nbt"
        );
        assert_eq!(
            std::fs::read(pack.join("data/ship/worldgen/template_pool/bom.json")).unwrap(),
            br#"{"elements": []}"#,
            "without its byte order mark"
        );
        assert!(
            !pack
                .join("data/ship/worldgen/processor_list/broken.json")
                .exists()
        );
        assert!(
            !pack.join("data/minecraft/worldgen/noise_settings").exists(),
            "terrain stays out"
        );
        assert!(!pack.join("data/ship/worldgen/biome").exists());
        assert!(pack.join("pack.mcmeta").is_file());
        assert!(!pack.join("data/ship/recipe").exists());
        assert!(!dir.join("escape.json").exists() && !out.join("escape.json").exists());
    }

    #[test]
    fn without_world_generation_there_is_no_pack() {
        let dir = scratch("none");
        let plain = jar(
            &dir,
            "timer.jar",
            &[("fabric.mod.json", br#"{"id": "timer"}"#)],
        );
        let out = dir.join("generated");
        std::fs::create_dir_all(out.join("datapack")).unwrap();
        std::fs::write(out.join("datapack/pack.mcmeta"), "old").unwrap();
        assert_eq!(generate(&[plain], &out), Generated::default());
        assert!(
            !out.join("datapack/pack.mcmeta").exists(),
            "an old pack goes"
        );
    }

    #[test]
    fn unchanged_mods_are_not_copied_again() {
        let dir = scratch("again");
        std::fs::create_dir_all(dir.join("mods")).unwrap();
        jar(
            &dir.join("mods"),
            "ship.jar",
            &[(
                "data/ship/worldgen/structure/ship.json",
                br#"{"biomes": "ocean"}"#,
            )],
        );
        assert_eq!(prepare(&dir).map(|g| g.data), Some(1));
        assert_eq!(prepare(&dir), None);
        std::fs::remove_file(dir.join("mods/ship.jar")).unwrap();
        assert_eq!(prepare(&dir), Some(Generated::default()));
    }
}
