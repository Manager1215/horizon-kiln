// The blocks Horizon's import leaves out of a Java mod's content mod, carried by Kiln (D-055). The import copies a
// vanilla block whose state properties are exactly the block's, and leaves a block out when none has them (a machine's
// `active` and `facing`, a cable's six sides): its blockstates, models, item and loot come over, its block file
// doesn't. Before Horizon reads the content mods, Kiln declares those blocks the way Horizon's native mods declare
// blocks in code (`native_blocks`, API 145, D-078), from the blockstates the import carried and the jar the mod came
// from: the spawner as template (a block entity, a whole block, no state properties) and every property of the block
// declared as its own. Horizon checks them as it checks a content mod's files, adds them to the import's content mod
// (whose models, textures and loot they use), and a client joining a world must have the same ones. A template's own properties would have to
// come first in the alphabet, as Horizon builds a content block's states, so a machine's `active` can't join a
// furnace's `facing`.
//
// Items too (D-074): without the facts of a running game, the import names a mod's items after their models'
// folders (`models/item/nugget/tin_nugget.json` gives `techreborn:nugget/tin_nugget`), while the mod's code, recipes,
// tags and loot tables name them by their registry ids (`techreborn:tin_nugget`). The jar's item definitions
// (`assets/<ns>/items/<name>.json`, one for each item its code registers, as 26.3 draws them) are those ids: Kiln
// declares each the content doesn't (`native_items`), as a copy of the import's item for the model it draws.
//
// The mod's crafting recipes that make or use those blocks and items, which the import left out with them, come back
// from the jar as files in the import's folder (they are data). `kiln-carried.json` there lists what Kiln wrote, so it
// can write it again or take it away: Kiln 0.2 wrote the blocks and items as files too, which go now.
//
// It names none of the game's types: `cargo test` runs it.

use std::collections::{BTreeMap, BTreeSet};
use std::io::Read;
use std::path::{Path, PathBuf};

use serde_json::{Value, json};

/// The list of what Kiln wrote into a content mod, in the mod's folder.
pub const MARK: &str = "kiln-carried.json";

/// States a carried block may have, all its properties' values multiplied: a block with more is left out.
const MAX_STATES: usize = 4096;

/// The template of every block Kiln carries: a block entity, a whole block, no state properties of its own.
const TEMPLATE: &str = "minecraft:spawner";

/// An item Kiln carries whose model the import made no item of: the template the import gives every item it knows
/// nothing of.
const ITEM_TEMPLATE: &str = "minecraft:stick";

/// What Kiln declares for a content mod, and wrote into it.
#[derive(Debug, Default, PartialEq)]
pub struct Carried {
    /// The blocks, by id (`techreborn:grinder`).
    pub blocks: Vec<String>,
    /// The items, by id (`techreborn:tin_nugget`).
    pub items: Vec<String>,
    /// Each block's declaration, as a content mod's `data/<ns>/horizon/block/<path>.json` would hold it.
    pub block_files: Vec<Value>,
    /// Each item's declaration, as `data/<ns>/horizon/item/<path>.json` would hold it.
    pub item_files: Vec<Value>,
    /// The recipes, by file.
    pub recipes: Vec<String>,
    /// Blocks it couldn't carry, and why.
    pub skipped: Vec<String>,
}

/// A block's state properties as its blockstates file names them: each property's values that file mentions.
pub fn properties(blockstates: &Value) -> BTreeMap<String, BTreeSet<String>> {
    let mut found: BTreeMap<String, BTreeSet<String>> = BTreeMap::new();
    if let Some(variants) = blockstates.get("variants").and_then(Value::as_object) {
        for key in variants.keys() {
            for pair in key.split(',').filter(|p| !p.is_empty()) {
                if let Some((name, value)) = pair.split_once('=') {
                    found
                        .entry(name.to_owned())
                        .or_default()
                        .insert(value.to_owned());
                }
            }
        }
    }
    if let Some(parts) = blockstates.get("multipart").and_then(Value::as_array) {
        for part in parts {
            if let Some(when) = part.get("when") {
                conditions(when, &mut found);
            }
        }
    }
    found
}

/// A multipart `when`: `{"north": "true|side"}`, or `OR`/`AND` lists of those.
fn conditions(when: &Value, found: &mut BTreeMap<String, BTreeSet<String>>) {
    let Some(object) = when.as_object() else {
        return;
    };
    for (name, value) in object {
        match value {
            Value::Array(list) if name == "OR" || name == "AND" => {
                list.iter().for_each(|w| conditions(w, found));
            }
            Value::String(values) => {
                let entry = found.entry(name.clone()).or_default();
                for v in values.split('|') {
                    entry.insert(v.trim_start_matches('!').to_owned());
                }
            }
            Value::Bool(b) => {
                found.entry(name.clone()).or_default().insert(b.to_string());
            }
            _ => {}
        }
    }
}

/// The block file Kiln gives a block with these properties, or why it can't. Each property defaults to `false`,
/// `north`, its lowest number or its first value, in that order of preference.
pub fn block_file(properties: &BTreeMap<String, BTreeSet<String>>) -> Result<Value, String> {
    let mut declared = serde_json::Map::new();
    let mut defaults = serde_json::Map::new();
    let mut states = 1usize;
    for (name, values) in properties {
        if !state_name(name) || !values.iter().all(|v| state_name(v)) {
            return Err(format!(
                "its {name} isn't named as Horizon names properties"
            ));
        }
        let boolean = values.iter().all(|v| v == "true" || v == "false");
        let mut ordered: Vec<&String> = values.iter().collect();
        if ordered.iter().all(|v| v.parse::<i64>().is_ok()) {
            ordered.sort_by_key(|v| v.parse::<i64>().unwrap_or(0));
        }
        let (property, first) = if boolean {
            (json!({"type": "boolean"}), "false".to_owned())
        } else {
            let first = ordered
                .iter()
                .find(|v| v.as_str() == "north")
                .unwrap_or(&ordered[0]);
            (json!({"type": "enum", "values": ordered}), (*first).clone())
        };
        states = states.saturating_mul(if boolean { 2 } else { ordered.len() });
        declared.insert(name.clone(), property);
        defaults.insert(name.clone(), Value::String(first));
    }
    if states > MAX_STATES {
        return Err(format!("it would have {states} states"));
    }
    let mut file = json!({ "template": TEMPLATE });
    if !declared.is_empty() {
        file["state_properties"] = Value::Object(declared);
        file["default_state"] = Value::Object(defaults);
    }
    Ok(file)
}

/// The mod's item models an item definition draws (`{"model": {"type": "minecraft:model", "model": "ns:item/a/b"}}`
/// gives `a/b`), in the order it names them: those of `select` and `condition` cases too.
fn drawn(definition: &Value, ns: &str) -> Vec<String> {
    let mut models = Vec::new();
    let mut stack = vec![definition];
    while let Some(v) = stack.pop() {
        match v {
            Value::Object(o) => {
                for (key, x) in o.iter().rev() {
                    match x.as_str() {
                        Some(model) if key == "model" => {
                            let path = model
                                .split_once(':')
                                .filter(|(n, _)| n == &ns)
                                .and_then(|(_, p)| p.strip_prefix("item/"));
                            models.extend(path.map(str::to_owned));
                        }
                        _ => stack.push(x),
                    }
                }
            }
            Value::Array(a) => stack.extend(a.iter().rev()),
            _ => {}
        }
    }
    models
}

/// A name Horizon takes for a state property or value: lower case letters, digits and underscores.
fn state_name(s: &str) -> bool {
    !s.is_empty()
        && s.bytes()
            .all(|b| b.is_ascii_lowercase() || b.is_ascii_digit() || b == b'_')
}

/// What the content mod in `dir`, Horizon's import of the Java mod in `jar`, lacks for its `namespaces` (its id first,
/// the only one Horizon takes blocks and items in): a block for each of the mod's blocks with a blockstates file and
/// no block file, an item for each item the jar defines that the content doesn't declare (both to declare in code),
/// and the mod's vanilla-type recipes the import left out that make or use one of them, written into `dir`. What Kiln
/// wrote before is written again; what it no longer writes goes.
pub fn fill(dir: &Path, namespaces: &[String], jar: &Path) -> Result<Carried, String> {
    let mut zip = zip::ZipArchive::new(std::fs::File::open(jar).map_err(|e| e.to_string())?)
        .map_err(|e| format!("{}: {e}", jar.display()))?;
    let in_jar: BTreeSet<String> = zip
        .file_names()
        .filter_map(Result::ok)
        .map(|n| n.into_owned())
        .collect();
    let before: BTreeSet<String> = std::fs::read_to_string(dir.join(MARK))
        .ok()
        .and_then(|t| serde_json::from_str::<Vec<String>>(&t).ok())
        .unwrap_or_default()
        .into_iter()
        .collect();
    let mut carried = Carried::default();
    let mut written = BTreeSet::new();
    // Horizon takes a mod's blocks and items in its id's namespace only.
    for ns in namespaces.iter().take(1) {
        let blockstates = dir.join("assets").join(ns).join("blockstates");
        for path in json_files(&blockstates) {
            let Some(name) = path.file_stem().and_then(|n| n.to_str()) else {
                continue;
            };
            let file = format!("data/{ns}/horizon/block/{name}.json");
            let theirs = dir.join(&file).is_file() && !before.contains(&file);
            if theirs || !in_jar.contains(&format!("assets/{ns}/blockstates/{name}.json")) {
                continue;
            }
            let states: Value = match std::fs::read_to_string(&path)
                .ok()
                .and_then(|t| serde_json::from_str(&t).ok())
            {
                Some(v) => v,
                None => continue,
            };
            match block_file(&properties(&states)) {
                Ok(block) => {
                    carried.block_files.push(block);
                    carried.blocks.push(format!("{ns}:{name}"));
                }
                Err(why) => carried.skipped.push(format!("{ns}:{name}: {why}")),
            }
        }
        // The items, under the ids the mod's code registers them by (D-074).
        let prefix = format!("assets/{ns}/items/");
        let definitions: Vec<(String, String)> = in_jar
            .iter()
            .filter_map(|n| {
                let path = n.strip_prefix(&prefix)?.strip_suffix(".json")?;
                Some((n.clone(), path.to_owned()))
            })
            .collect();
        for (name, path) in definitions {
            let file = format!("data/{ns}/horizon/item/{path}.json");
            let theirs = dir.join(&file).is_file() && !before.contains(&file);
            // A block's item comes with its block, the import's or Kiln's, or stays out with it.
            let a_block = dir
                .join(format!("data/{ns}/horizon/block/{path}.json"))
                .is_file()
                || in_jar.contains(&format!("assets/{ns}/blockstates/{path}.json"));
            if theirs || a_block {
                continue;
            }
            let mut text = String::new();
            let Some(definition) = zip
                .by_name(&name)
                .ok()
                .and_then(|mut e| e.read_to_string(&mut text).ok())
                .and_then(|_| serde_json::from_str::<Value>(&text).ok())
            else {
                continue;
            };
            let item = drawn(&definition, ns)
                .into_iter()
                .map(|model| format!("data/{ns}/horizon/item/{model}.json"))
                .filter(|f| !before.contains(f))
                .find_map(|f| {
                    let text = std::fs::read_to_string(dir.join(f)).ok()?;
                    serde_json::from_str::<Value>(&text).ok()
                })
                .unwrap_or_else(|| json!({ "template": ITEM_TEMPLATE }));
            carried.item_files.push(item);
            carried.items.push(format!("{ns}:{path}"));
        }
    }
    let quoted: Vec<String> = carried
        .blocks
        .iter()
        .chain(&carried.items)
        .map(|id| format!("\"{id}\""))
        .collect();
    let recipes: Vec<String> = in_jar
        .iter()
        .filter(|n| {
            n.ends_with(".json")
                && namespaces
                    .iter()
                    .any(|ns| n.starts_with(&format!("data/{ns}/recipe/")))
        })
        .cloned()
        .collect();
    for name in recipes {
        if dir.join(&name).is_file() && !before.contains(&name) {
            continue;
        }
        let mut text = String::new();
        if zip
            .by_name(&name)
            .map_err(|e| e.to_string())?
            .read_to_string(&mut text)
            .is_err()
        {
            continue;
        }
        let vanilla = serde_json::from_str::<Value>(&text).ok().is_some_and(|r| {
            r.get("type")
                .and_then(Value::as_str)
                .is_some_and(|t| t.starts_with("minecraft:"))
        });
        if vanilla && quoted.iter().any(|id| text.contains(id.as_str())) {
            write(dir, &name, &text)?;
            written.insert(name.clone());
            carried.recipes.push(name);
        }
    }
    for gone in before.difference(&written) {
        let _ = std::fs::remove_file(dir.join(gone));
    }
    if written.is_empty() {
        let _ = std::fs::remove_file(dir.join(MARK));
    } else {
        let list: Vec<&String> = written.iter().collect();
        write(
            dir,
            MARK,
            &serde_json::to_string_pretty(&list).unwrap_or_default(),
        )?;
    }
    Ok(carried)
}

/// Writes `text` to `dir`/`name` when it isn't there already, so an unchanged mod is left as it was.
fn write(dir: &Path, name: &str, text: &str) -> Result<(), String> {
    let path = dir.join(name);
    if std::fs::read_to_string(&path).is_ok_and(|t| t == text) {
        return Ok(());
    }
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent).map_err(|e| format!("{}: {e}", parent.display()))?;
    }
    std::fs::write(&path, text).map_err(|e| format!("{}: {e}", path.display()))
}

/// The `.json` files directly in `dir`, by name.
fn json_files(dir: &Path) -> Vec<PathBuf> {
    let mut files: Vec<PathBuf> = std::fs::read_dir(dir)
        .into_iter()
        .flatten()
        .flatten()
        .map(|e| e.path())
        .filter(|p| p.extension().is_some_and(|e| e == "json"))
        .collect();
    files.sort();
    files
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;

    fn props(pairs: &[(&str, &[&str])]) -> BTreeMap<String, BTreeSet<String>> {
        pairs
            .iter()
            .map(|(n, v)| (n.to_string(), v.iter().map(|s| s.to_string()).collect()))
            .collect()
    }

    #[test]
    fn a_machine_declares_all_its_properties_on_the_spawner() {
        let states = json!({"variants": {
            "active=false,facing=east": {"model": "m:block/press", "y": 90},
            "active=true,facing=north": {"model": "m:block/press_on"}
        }});
        let found = properties(&states);
        assert_eq!(
            found,
            props(&[
                ("active", &["false", "true"]),
                ("facing", &["east", "north"])
            ])
        );
        assert_eq!(
            block_file(&found).unwrap(),
            json!({"template": "minecraft:spawner",
                   "state_properties": {"active": {"type": "boolean"},
                                        "facing": {"type": "enum", "values": ["east", "north"]}},
                   "default_state": {"active": "false", "facing": "north"}})
        );
    }

    #[test]
    fn a_cable_declares_its_six_sides() {
        let states = json!({"multipart": [
            {"apply": {"model": "m:block/cable_core"}},
            {"apply": {"model": "m:block/cable_side"}, "when": {"north": "true"}},
            {"apply": {"model": "m:block/cable_side"}, "when": {"OR": [{"east": "true"}, {"west": "true|false"}]}},
            {"apply": {"model": "m:block/cable_side"}, "when": {"south": "true", "up": "true", "down": "true"}}
        ]});
        let file = block_file(&properties(&states)).unwrap();
        assert_eq!(file["state_properties"].as_object().unwrap().len(), 6);
        assert_eq!(file["default_state"]["north"], "false");
    }

    #[test]
    fn every_condition_names_a_property() {
        // AND lists as OR ones, true written as JSON's own; a list under any other name is no condition.
        let states = json!({"multipart": [
            {"apply": {"model": "m:block/pipe"}, "when": {"AND": [{"up": "true"}, {"down": "false"}]}},
            {"apply": {"model": "m:block/pipe_lit"}, "when": {"lit": true}},
            {"apply": {"model": "m:block/pipe"}, "when": {"odd": [{"north": "true"}]}}
        ]});
        assert_eq!(
            properties(&states),
            props(&[("down", &["false"]), ("lit", &["true"]), ("up", &["true"])])
        );
    }

    #[test]
    fn numbers_keep_their_order_and_start_at_the_lowest() {
        let found = props(&[("level", &["10", "2", "0"]), ("mode", &["fast", "slow"])]);
        let file = block_file(&found).unwrap();
        assert_eq!(
            file["state_properties"]["level"]["values"],
            json!(["0", "2", "10"])
        );
        assert_eq!(file["default_state"], json!({"level": "0", "mode": "fast"}));
        assert_eq!(
            block_file(&BTreeMap::new()).unwrap(),
            json!({"template": "minecraft:spawner"})
        );
    }

    #[test]
    fn what_horizon_would_refuse_is_left_out() {
        assert!(block_file(&props(&[("Mode", &["a"])])).is_err());
        assert!(block_file(&props(&[("lit", &["Dim"])])).is_err());
        let many: Vec<String> = (0..100).map(|i| i.to_string()).collect();
        let many: Vec<&str> = many.iter().map(String::as_str).collect();
        assert!(block_file(&props(&[("a", &many), ("b", &many)])).is_err());
        // 4096 states at most: 64 by 64 is all right.
        assert!(block_file(&props(&[("a", &many[..64]), ("b", &many[..64])])).is_ok());
    }

    #[test]
    fn a_changed_file_is_written_again() {
        let dir = std::env::temp_dir().join(format!("kiln-carry-write-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        write(&dir, "a/b.json", "1").unwrap();
        write(&dir, "a/b.json", "2").unwrap();
        assert_eq!(std::fs::read_to_string(dir.join("a/b.json")).unwrap(), "2");
        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn a_content_mod_gets_its_left_out_blocks_and_their_recipes() {
        let dir = std::env::temp_dir().join(format!("kiln-carry-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        let content = dir.join("m");
        let press = r#"{"variants": {"active=false,facing=north": {"model": "m:block/press"}}}"#;
        let ore = r#"{"variants": {"": {"model": "m:block/ore"}}}"#;
        write(&content, "assets/m/blockstates/press.json", press).unwrap();
        write(&content, "assets/m/blockstates/ore.json", ore).unwrap();
        write(
            &content,
            "data/m/horizon/block/ore.json",
            r#"{"template": "minecraft:stone"}"#,
        )
        .unwrap();
        let jar = dir.join("m.jar");
        let mut zip = zip::ZipWriter::new(std::fs::File::create(&jar).unwrap());
        let options = zip::write::SimpleFileOptions::default();
        for (name, text) in [
            ("assets/m/blockstates/press.json", press),
            ("assets/m/blockstates/ore.json", ore),
            (
                "data/m/recipe/press.json",
                r#"{"type": "minecraft:crafting_shaped", "result": {"id": "m:press"}}"#,
            ),
            (
                "data/m/recipe/pressing/plate.json",
                r#"{"type": "m:pressing", "result": {"id": "m:plate"}}"#,
            ),
            (
                "data/m/recipe/ore_block.json",
                r#"{"type": "minecraft:crafting_shapeless", "result": {"id": "m:ore"}}"#,
            ),
            // Names the press too, but isn't a recipe.
            (
                "data/m/loot_table/blocks/press.json",
                r#"{"type": "minecraft:block", "pools": [{"entries": [{"name": "m:press"}]}]}"#,
            ),
        ] {
            zip.start_file(name, options).unwrap();
            zip.write_all(text.as_bytes()).unwrap();
        }
        zip.finish().unwrap();

        // Kiln 0.2 wrote the press's block file; it goes now, and the block is declared in code.
        let old = r#"{"template": "minecraft:spawner"}"#;
        write(&content, "data/m/horizon/block/press.json", old).unwrap();
        write(&content, MARK, r#"["data/m/horizon/block/press.json"]"#).unwrap();
        let carried = fill(&content, &["m".into()], &jar).unwrap();
        assert_eq!(carried.blocks, ["m:press"]);
        assert_eq!(carried.recipes, ["data/m/recipe/press.json"]);
        assert_eq!(carried.block_files.len(), 1);
        assert_eq!(carried.block_files[0]["template"], "minecraft:spawner");
        assert!(!content.join("data/m/horizon/block/press.json").exists());
        // The import's own block file is left as it was; a second start writes the same again.
        assert!(
            std::fs::read_to_string(content.join("data/m/horizon/block/ore.json"))
                .unwrap()
                .contains("stone")
        );
        assert_eq!(fill(&content, &["m".into()], &jar).unwrap(), carried);
        let mark: Vec<String> =
            serde_json::from_str(&std::fs::read_to_string(content.join(MARK)).unwrap()).unwrap();
        assert_eq!(mark, ["data/m/recipe/press.json"]);
        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn a_mods_items_are_declared_under_the_ids_its_code_names() {
        let dir = std::env::temp_dir().join(format!("kiln-carry-items-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        let content = dir.join("m");
        let press = r#"{"variants": {"active=false,facing=north": {"model": "m:block/press"}}}"#;
        let ore = r#"{"variants": {"": {"model": "m:block/ore"}}}"#;
        // Drawn in code: its base model only places it in the hand.
        let spinner = r#"{"model": {"type": "minecraft:special", "base": "m:item/parts/plate",
            "model": {"type": "m:spinner"}}}"#;
        // A block whose property Horizon can't name: Kiln doesn't carry it, and its item stays out with it.
        let lamp = r#"{"variants": {"LIT=true": {"model": "m:block/lamp"}}}"#;
        let drawn = |model: &str| {
            format!(r#"{{"model": {{"type": "minecraft:model", "model": "m:item/{model}"}}}}"#)
        };
        // The import's items, named after their models' folders.
        for (name, text) in [
            ("assets/m/blockstates/press.json", press),
            ("assets/m/blockstates/ore.json", ore),
            ("assets/m/blockstates/lamp.json", lamp),
            (
                "data/m/horizon/block/ore.json",
                r#"{"template": "minecraft:stone"}"#,
            ),
            (
                "data/m/horizon/item/parts/plate.json",
                r#"{"template": "minecraft:paper"}"#,
            ),
            (
                "data/m/horizon/item/power/battery.json",
                r#"{"template": "minecraft:clock"}"#,
            ),
        ] {
            write(&content, name, text).unwrap();
        }
        let battery = r#"{"model": {"type": "minecraft:select", "property": "m:active",
            "cases": [{"when": "on", "model": {"type": "minecraft:model", "model": "m:item/power/battery"}}],
            "fallback": {"type": "minecraft:model", "model": "m:item/power/battery_empty"}}}"#;
        let jar = dir.join("m.jar");
        let mut zip = zip::ZipWriter::new(std::fs::File::create(&jar).unwrap());
        let options = zip::write::SimpleFileOptions::default();
        for (name, text) in [
            ("assets/m/blockstates/press.json", press.to_owned()),
            ("assets/m/blockstates/ore.json", ore.to_owned()),
            ("assets/m/blockstates/lamp.json", lamp.to_owned()),
            ("assets/m/items/plate.json", drawn("parts/plate")),
            ("assets/m/items/battery.json", battery.to_owned()),
            ("assets/m/items/spinner.json", spinner.to_owned()),
            ("assets/m/items/lamp.json", drawn("lamp")),
            ("assets/m/items/cell.json", r#"{"model": {"type": "m:model/cell"}}"#.to_owned()),
            ("assets/m/items/ore.json", drawn("ore")),
            ("assets/m/items/press.json", drawn("press")),
            ("assets/other/items/thing.json", drawn("thing")),
            (
                "data/m/recipe/plate.json",
                r#"{"type": "minecraft:crafting_shapeless", "ingredients": ["m:ore"], "result": {"id": "m:plate"}}"#
                    .to_owned(),
            ),
            (
                "data/m/recipe/pressing/plate.json",
                r#"{"type": "m:pressing", "result": {"id": "m:plate"}}"#.to_owned(),
            ),
        ] {
            zip.start_file(name, options).unwrap();
            zip.write_all(text.as_bytes()).unwrap();
        }
        zip.finish().unwrap();

        let carried = fill(&content, &["m".into(), "other".into()], &jar).unwrap();
        // The press's item comes with the block Kiln carries, the ore's with the import's; another namespace
        // declares nothing.
        assert_eq!(carried.blocks, ["m:press"]);
        assert_eq!(carried.skipped, ["m:lamp: its LIT isn't named as Horizon names properties"]);
        assert_eq!(carried.items, ["m:battery", "m:cell", "m:plate", "m:spinner"]);
        let item = |path: &str| -> Value {
            serde_json::from_str(
                &std::fs::read_to_string(content.join(format!("data/m/horizon/item/{path}.json")))
                    .unwrap(),
            )
            .unwrap()
        };
        let declared = |id: &str| -> Value {
            let at = carried.items.iter().position(|i| i == id).unwrap();
            carried.item_files[at].clone()
        };
        // A copy of the import's item for the model it draws (a select's first model the import has, a case's here),
        // else the stick.
        assert_eq!(declared("m:plate"), json!({"template": "minecraft:paper"}));
        assert_eq!(declared("m:battery"), json!({"template": "minecraft:clock"}));
        assert_eq!(declared("m:cell"), json!({"template": "minecraft:stick"}));
        assert_eq!(declared("m:spinner"), json!({"template": "minecraft:stick"}));
        assert!(!content.join("data/m/horizon/item/plate.json").exists());
        assert!(!content.join("data/m/horizon/item/ore.json").exists());
        assert!(!content.join("data/other").exists());
        // The import's own item stays; the recipe that makes the plate comes back, the mod's own type doesn't.
        assert_eq!(item("parts/plate"), json!({"template": "minecraft:paper"}));
        assert_eq!(carried.recipes, ["data/m/recipe/plate.json"]);
        // Written again the same.
        assert_eq!(
            fill(&content, &["m".into(), "other".into()], &jar).unwrap(),
            carried
        );
        // An import that declares an item under its id itself (a reinstall: Kiln's files gone) keeps its own.
        let mark: Vec<String> =
            serde_json::from_str(&std::fs::read_to_string(content.join(MARK)).unwrap()).unwrap();
        for file in mark.iter().chain([&MARK.to_owned()]) {
            std::fs::remove_file(content.join(file)).unwrap();
        }
        write(
            &content,
            "data/m/horizon/item/plate.json",
            r#"{"template": "minecraft:book"}"#,
        )
        .unwrap();
        let again = fill(&content, &["m".into()], &jar).unwrap();
        assert_eq!(again.items, ["m:battery", "m:cell", "m:spinner"]);
        assert_eq!(item("plate"), json!({"template": "minecraft:book"}));
        let _ = std::fs::remove_dir_all(&dir);
    }
}
