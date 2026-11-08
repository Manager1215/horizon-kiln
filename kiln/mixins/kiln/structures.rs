// Structure mods as Horizon reads them (D-048, D-049). A Java mod's world generation is data Horizon's generator
// reads (structures, structure sets, template pools, processor lists, their `.nbt` pieces, worldgen tags, loot), but
// not from a jar. Before a world opens, Kiln copies it out of the mods' jars into `<game folder>/kiln/generated/`:
// `datapack/`, a data pack every world gets (datapack.rs), and `structure/<namespace>/<path>.nbt`, the pieces, where
// structure_template.rs finds them. Ids without a namespace get `minecraft:` and block states take 26.3's shape, as
// Minecraft reads them and Horizon doesn't; a pack overlay made for 26.3 replaces the files it has. Only reads jars
// and writes files: no game types, so `cargo test` covers it.

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
    let jars = crate::game::kiln::mod_jars(kiln_dir);
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
        for (name, i) in entries(&mut zip) {
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

/// A jar's entries by the name they have in the pack, with their index: an overlay of `pack.mcmeta` made for 26.3's
/// format (`overlay_26_3/data/...`) replaces the base file of the same name, a later overlay an earlier one, as
/// Minecraft applies them. Mods write 26.3's changes there (block states as `id`) and keep the base for older games.
fn entries<R: std::io::Read + std::io::Seek>(zip: &mut zip::ZipArchive<R>) -> Vec<(String, usize)> {
    let overlays = active_overlays(zip);
    let mut by_name = std::collections::BTreeMap::new();
    for i in 0..zip.len() {
        let Some(Ok(name)) = zip.name_for_index(i) else {
            continue;
        };
        let (layer, name) = match name.split_once('/') {
            Some((dir, rest)) if rest.starts_with("data/") => {
                match overlays.iter().position(|o| o == dir) {
                    Some(layer) => (layer + 1, rest.to_owned()),
                    None => continue,
                }
            }
            _ => (0, name.into_owned()),
        };
        // Older games' `structures/` and 26.3's `structure/` hold the same pieces (`place` puts both in one folder):
        // one name, so an overlay's piece replaces the base's.
        let name = match name.split('/').collect::<Vec<_>>().as_slice() {
            ["data", namespace, "structures", rest @ ..] => {
                format!("data/{namespace}/structure/{}", rest.join("/"))
            }
            _ => name,
        };
        let kept = by_name.entry(name).or_insert((layer, i));
        if layer >= kept.0 {
            *kept = (layer, i);
        }
    }
    by_name
        .into_iter()
        .map(|(name, (_, i))| (name, i))
        .collect()
}

/// The overlay folders of a jar's `pack.mcmeta` whose formats take in 26.3's, in their order.
fn active_overlays<R: std::io::Read + std::io::Seek>(zip: &mut zip::ZipArchive<R>) -> Vec<String> {
    let Ok(mut mcmeta) = zip.by_name("pack.mcmeta") else {
        return Vec::new();
    };
    let mut bytes = Vec::new();
    if mcmeta.read_to_end(&mut bytes).is_err() {
        return Vec::new();
    }
    let bytes = bytes.strip_prefix(b"\xef\xbb\xbf").unwrap_or(&bytes);
    let Ok(json) = serde_json::from_slice::<serde_json::Value>(bytes) else {
        return Vec::new();
    };
    let Some(list) = json["overlays"]["entries"].as_array() else {
        return Vec::new();
    };
    list.iter()
        .filter(|o| {
            formats(o).is_some_and(|(min, max)| (min..=max).contains(&f64::from(PACK_FORMAT)))
        })
        .filter_map(|o| o["directory"].as_str())
        .filter(|d| {
            !d.is_empty()
                && Path::new(d)
                    .components()
                    .all(|c| matches!(c, Component::Normal(_)))
        })
        .map(str::to_owned)
        .collect()
}

/// An overlay's formats, lowest and highest: `min_format`/`max_format` (a number, or `[major, minor]`), or the older
/// `formats` (a number, `[min, max]` or `{"min_inclusive", "max_inclusive"}`).
fn formats(overlay: &serde_json::Value) -> Option<(f64, f64)> {
    let major = |v: &serde_json::Value| {
        v.as_f64()
            .or_else(|| v.get(0).and_then(serde_json::Value::as_f64))
    };
    if let (Some(min), Some(max)) = (major(&overlay["min_format"]), major(&overlay["max_format"])) {
        return Some((min, max));
    }
    let f = &overlay["formats"];
    if let Some(one) = f.as_f64() {
        return Some((one, one));
    }
    if let (Some(min), Some(max)) = (
        f.get(0).and_then(|v| v.as_f64()),
        f.get(1).and_then(|v| v.as_f64()),
    ) {
        return Some((min, max));
    }
    Some((f["min_inclusive"].as_f64()?, f["max_inclusive"].as_f64()?))
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

/// A JSON file as Horizon reads it, which is stricter than Minecraft: without a byte order mark; in structures'
/// world generation, block states in 26.3's shape and `minecraft:` on ids that name no namespace (`"type":
/// "jigsaw"`, `ocean`, `#is_ocean`), as in a tag's values. None when it isn't JSON at all.
fn json_for_horizon(name: &str, bytes: &[u8]) -> Option<Vec<u8>> {
    let bytes = bytes.strip_prefix(b"\xef\xbb\xbf").unwrap_or(bytes);
    let mut json: serde_json::Value = serde_json::from_slice(bytes).ok()?;
    // Tags first: `data/<ns>/tags/worldgen/...` is a tag, not world generation.
    let mut changed = if name.contains("/tags/") {
        json.get_mut("values").is_some_and(namespace_ids)
    } else if name.contains("/worldgen/") {
        block_states(&mut json) | namespace_fields(&mut json)
    } else {
        return Some(bytes.to_vec());
    };
    if name.contains("/tags/worldgen/biome/") {
        changed |= json.get_mut("values").is_some_and(mod_biomes_optional);
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

/// The fields of structures' world generation that hold ids (of structures, pools, pieces, types, processors,
/// blocks, biomes), which Minecraft reads with `minecraft:` when they name no namespace. Not the fields that hold
/// names of their own (`projection`, `step`, `terrain_adaptation`, heightmaps).
const ID_FIELDS: [&str; 18] = [
    "type",
    "biomes",
    "start_pool",
    "start_jigsaw_name",
    "structure",
    "other_set",
    "fallback",
    "element_type",
    "location",
    "feature",
    "processors",
    "processor_type",
    "predicate_type",
    "rottable_blocks",
    "block",
    "tag",
    "Name",
    "id",
];

/// [`namespace_ids`] on every [`ID_FIELDS`] field, at any depth. True when it changed any.
fn namespace_fields(value: &mut serde_json::Value) -> bool {
    match value {
        serde_json::Value::Object(fields) => fields.iter_mut().fold(false, |any, (key, v)| {
            let id = ID_FIELDS.contains(&key.as_str())
                && match v {
                    serde_json::Value::String(_) => namespace_ids(v),
                    serde_json::Value::Array(items)
                        if items.iter().all(serde_json::Value::is_string) =>
                    {
                        namespace_ids(v)
                    }
                    _ => false,
                };
            namespace_fields(v) | id | any
        }),
        serde_json::Value::Array(items) => items
            .iter_mut()
            .fold(false, |any, v| namespace_fields(v) | any),
        _ => false,
    }
}

/// Block and fluid states in 26.3's shape: older games write `{"Name": ..., "Properties": {...}}`, 26.3's codec
/// reads `{"id": ..., "properties": {...}}` (Horizon's carry-over does the same, `horizon-port-pack`'s `worldgen.rs`).
/// Horizon refuses a processor list with an old one, and with it every pool that names the list. True when it
/// changed any.
fn block_states(value: &mut serde_json::Value) -> bool {
    match value {
        serde_json::Value::Object(o) => {
            let state = o.get("Name").is_some_and(serde_json::Value::is_string)
                && o.keys().all(|k| k == "Name" || k == "Properties");
            if state {
                if let Some(name) = o.remove("Name") {
                    o.insert("id".into(), name);
                }
                if let Some(properties) = o.remove("Properties") {
                    o.insert("properties".into(), properties);
                }
                return true;
            }
            o.values_mut().fold(false, |any, v| block_states(v) | any)
        }
        serde_json::Value::Array(items) => {
            items.iter_mut().fold(false, |any, v| block_states(v) | any)
        }
        _ => false,
    }
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
                ("data/ship/structure/hull/notes.txt", b"not a piece"),
                ("data/ship/loot_table/chests/hold.json", br#"{"pools": []}"#),
                ("data/ship/loot_table/readme.md", b"# not a table"),
                ("data/ship/tags/item/sails.json", br#"{"values": ["ship:sail"]}"#),
                (
                    "data/ship/tags/worldgen/biome/has_wreck.json",
                    br#"{"values": ["ship:reef", {"id": "ship:lagoon"}]}"#,
                ),
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
                data: 6,
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
        // A tag of the mod's biomes only, one of them an entry of its own: both optional.
        let wreck: serde_json::Value = serde_json::from_slice(
            &std::fs::read(pack.join("data/ship/tags/worldgen/biome/has_wreck.json")).unwrap(),
        )
        .unwrap();
        assert_eq!(
            wreck["values"],
            serde_json::json!([{"id": "ship:reef", "required": false}, {"id": "ship:lagoon", "required": false}])
        );
        // Structures' loot comes; other tags don't.
        assert!(pack.join("data/ship/loot_table/chests/hold.json").is_file());
        assert!(!pack.join("data/ship/tags/item").exists());
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
        assert!(dir.join("generated/datapack/data/ship/worldgen/structure/ship.json").is_file());
        assert_eq!(prepare(&dir), None);
        std::fs::remove_file(dir.join("mods/ship.jar")).unwrap();
        assert_eq!(prepare(&dir), Some(Generated::default()));
    }

    fn read_json(path: PathBuf) -> serde_json::Value {
        serde_json::from_slice(&std::fs::read(path).unwrap()).unwrap()
    }

    // Mingen 7.3 writes `"type": "jigsaw"`: Horizon knew no such structure type, and 28 of its 30 structures were
    // never located; the two with `minecraft:jigsaw` were (#80).
    #[test]
    fn ids_without_a_namespace_get_minecraft_in_structures_world_generation() {
        let dir = scratch("ids");
        let mod_jar = jar(
            &dir,
            "mingen.jar",
            &[
                (
                    "data/mingen/worldgen/structure/well.json",
                    br#"{"type": "jigsaw", "biomes": ["forest"], "start_pool": "mingen:well", "step": "surface_structures",
                        "start_height": {"type": "uniform", "min_inclusive": {"absolute": 0}}, "project_start_to_heightmap": "WORLD_SURFACE_WG"}"#,
                ),
                (
                    "data/mingen/worldgen/structure_set/well.json",
                    br#"{"structures": [{"structure": "mingen:well", "weight": 1}], "placement": {"type": "random_spread",
                        "spacing": 30, "separation": 10, "salt": 1, "exclusion_zone": {"other_set": "villages", "chunk_count": 4}}}"#,
                ),
                (
                    "data/mingen/worldgen/template_pool/well.json",
                    br#"{"fallback": "empty", "elements": [{"weight": 1, "element": {"element_type": "single_pool_element",
                        "location": "mingen:well", "projection": "rigid", "processors": "empty"}}]}"#,
                ),
                (
                    "data/mingen/worldgen/processor_list/mossy.json",
                    br#"{"processors": [{"processor_type": "rule", "rules": [{"input_predicate": {"predicate_type":
                        "random_block_match", "block": "cobblestone", "probability": 0.3}, "location_predicate":
                        {"predicate_type": "always_true"}, "output_state": {"id": "mossy_cobblestone"}}]}]}"#,
                ),
            ],
        );
        let out = dir.join("generated");
        assert_eq!(generate(&[mod_jar], &out).data, 4);
        let wg = out.join("datapack/data/mingen/worldgen");
        let structure = read_json(wg.join("structure/well.json"));
        assert_eq!(structure["type"], "minecraft:jigsaw");
        assert_eq!(structure["biomes"], serde_json::json!(["minecraft:forest"]));
        assert_eq!(structure["start_height"]["type"], "minecraft:uniform");
        assert_eq!(
            structure["step"], "surface_structures",
            "a name of its own, not an id"
        );
        assert_eq!(structure["project_start_to_heightmap"], "WORLD_SURFACE_WG");
        let set = read_json(wg.join("structure_set/well.json"));
        assert_eq!(set["placement"]["type"], "minecraft:random_spread");
        assert_eq!(
            set["placement"]["exclusion_zone"]["other_set"],
            "minecraft:villages"
        );
        assert_eq!(set["structures"][0]["structure"], "mingen:well");
        let pool = read_json(wg.join("template_pool/well.json"));
        assert_eq!(pool["fallback"], "minecraft:empty");
        let element = &pool["elements"][0]["element"];
        assert_eq!(element["element_type"], "minecraft:single_pool_element");
        assert_eq!(element["processors"], "minecraft:empty");
        assert_eq!(element["projection"], "rigid");
        let rule = &read_json(wg.join("processor_list/mossy.json"))["processors"][0];
        assert_eq!(rule["processor_type"], "minecraft:rule");
        assert_eq!(
            rule["rules"][0]["input_predicate"]["predicate_type"],
            "minecraft:random_block_match"
        );
        assert_eq!(
            rule["rules"][0]["input_predicate"]["block"],
            "minecraft:cobblestone"
        );
        assert_eq!(
            rule["rules"][0]["output_state"]["id"],
            "minecraft:mossy_cobblestone"
        );
    }

    #[test]
    fn block_states_take_26_3s_shape() {
        let mut list = serde_json::json!({"processors": [
            {"processor_type": "minecraft:block_ignore", "blocks": [{"Name": "minecraft:structure_void"}]},
            {"processor_type": "minecraft:rule", "rules": [{"output_state":
                {"Name": "minecraft:oak_stairs", "Properties": {"facing": "east"}}}]},
        ]});
        assert!(block_states(&mut list));
        assert_eq!(
            list["processors"][0]["blocks"][0],
            serde_json::json!({"id": "minecraft:structure_void"})
        );
        assert_eq!(
            list["processors"][1]["rules"][0]["output_state"],
            serde_json::json!({"id": "minecraft:oak_stairs", "properties": {"facing": "east"}})
        );
        let mut other = serde_json::json!({"Name": "a pool's name", "elements": []});
        assert!(
            !block_states(&mut other),
            "not a state: it has more than Name and Properties"
        );
        assert!(!block_states(&mut list), "already 26.3's");
    }

    // Small Structures 0.6 keeps its pools for older games in `data/` and 26.3's (block states as `id`) in the
    // overlay `overlay_26_3` of its pack.mcmeta; Kiln read only `data/`, and none of its 22 structures was located.
    #[test]
    fn an_overlay_made_for_26_3_replaces_the_base_files() {
        let dir = scratch("overlay");
        let pool = |state: &str| {
            format!(
                r#"{{"elements": [{{"weight": 1, "element": {{"element_type": "minecraft:single_pool_element", "location": "small:well",
                "processors": [{{"processor_type": "minecraft:block_ignore", "blocks": [{{"{state}": "minecraft:structure_void"}}]}}]}}}}]}}"#
            )
        };
        let (old, new) = (pool("Name"), pool("id"));
        let mod_jar = jar(
            &dir,
            "small.jar",
            &[
                (
                    "pack.mcmeta",
                    br#"{"pack": {"pack_format": 121.0, "min_format": 88, "max_format": 121.0}, "overlays": {"entries": [
                        {"directory": "overlay_26_3", "min_format": 121, "max_format": 121},
                        {"directory": "overlay_old", "formats": [48, 57]},
                        {"directory": "../escape", "min_format": 121, "max_format": 121}]}}"#,
                ),
                ("data/small/worldgen/template_pool/well.json", old.as_bytes()),
                ("overlay_26_3/data/small/worldgen/template_pool/well.json", new.as_bytes()),
                ("data/small/loot_table/chests/well.json", br#"{"pools": [], "from": "base"}"#),
                ("overlay_old/data/small/loot_table/chests/well.json", br#"{"pools": [], "from": "old"}"#),
                ("overlay_26_3/data/small/loot_table/chests/camp.json", br#"{"pools": []}"#),
                ("__MACOSX/data/small/worldgen/template_pool/._well.json", b"\x00\x05junk"),
                // Mingen 7.3: the base's pieces in older games' `structures/`, the overlay's in `structure/`.
                ("data/small/structures/well.nbt", b"old piece"),
                ("overlay_26_3/data/small/structure/well.nbt", b"new piece"),
            ],
        );
        let out = dir.join("generated");
        let generated = generate(&[mod_jar], &out);
        assert_eq!(generated.data, 3);
        assert_eq!(generated.pieces, 1, "one piece, in two folders");
        assert_eq!(
            std::fs::read(out.join("structure/small/well.nbt")).unwrap(),
            b"new piece"
        );
        assert!(generated.skipped.is_empty(), "{:?}", generated.skipped);
        let pack = out.join("datapack/data/small");
        let well = read_json(pack.join("worldgen/template_pool/well.json"));
        assert_eq!(
            well["elements"][0]["element"]["processors"][0]["blocks"][0],
            serde_json::json!({"id": "minecraft:structure_void"}),
            "the overlay's file"
        );
        assert_eq!(
            read_json(pack.join("loot_table/chests/well.json"))["from"],
            "base",
            "not for 26.3"
        );
        assert!(
            pack.join("loot_table/chests/camp.json").is_file(),
            "only in the overlay"
        );
        assert!(
            !out.join("datapack/data/small/worldgen/template_pool/._well.json")
                .exists()
        );
    }

    #[test]
    fn overlay_formats_in_each_way_pack_mcmeta_writes_them() {
        let f = |v: serde_json::Value| formats(&v);
        assert_eq!(
            f(serde_json::json!({"min_format": 121, "max_format": 121.0})),
            Some((121., 121.))
        );
        assert_eq!(
            f(serde_json::json!({"min_format": [94, 1], "max_format": [121, 0]})),
            Some((94., 121.))
        );
        assert_eq!(f(serde_json::json!({"formats": 121})), Some((121., 121.)));
        assert_eq!(
            f(serde_json::json!({"formats": [48, 57]})),
            Some((48., 57.))
        );
        assert_eq!(
            f(serde_json::json!({"formats": {"min_inclusive": 100, "max_inclusive": 130}})),
            Some((100., 130.))
        );
        assert_eq!(f(serde_json::json!({"directory": "x"})), None);
    }
}
