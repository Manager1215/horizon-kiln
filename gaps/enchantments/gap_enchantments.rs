// SPDX-License-Identifier: MIT
// Enchantments outside the game's own folder (horizon-gaps, `enchantments`).
//
// Horizon's enchantment registry reads only `data/minecraft/enchantment/*.json` of the resource root. This module adds
// the rest, as the game's registry loader does: a content mod's own namespace in the resource root
// (`data/<ns>/enchantment/**.json`), then the world's enabled data packs' files (`enchantment/<ns>:<path>` in the pack
// overlay, which `gap_enchantment_files` fills), a pack's file replacing an entry of the same id, the game's own too.
// The registry stays sorted by id, as the loader leaves it. A mod's `assets/<ns>/lang/en_us.json` gives its names.
//
// It is a public child module of `horizon_items::enchant`, so it builds entries as that module's own loader does. The
// HEAD hook on `native_tick` (gap_enchantments_hook.rs) calls `add` for a world's tables.

use super::{Cost, Enchantment, Enchantments, string_list};
use horizon_world::loot::PackOverlay;
use serde_json::Value;
use std::{collections::BTreeMap, path::Path, sync::Arc};

/// The entries Horizon's own load leaves out, by id: the resource root's in other namespaces than `minecraft`, then the
/// packs' (the last pack's file for an id), which replace them.
pub fn files(root: &Path, overlay: &PackOverlay) -> BTreeMap<String, Value> {
    let mut out = BTreeMap::new();
    let mut namespaces: Vec<String> = std::fs::read_dir(root.join("data"))
        .into_iter()
        .flatten()
        .flatten()
        .filter(|e| e.path().is_dir())
        .filter_map(|e| e.file_name().into_string().ok())
        .filter(|ns| ns != "minecraft")
        .collect();
    namespaces.sort();
    for ns in namespaces {
        let Ok(folder) =
            horizon_world::json_folder::read(&root.join("data").join(&ns).join("enchantment"))
        else {
            continue;
        };
        for file in &folder.files {
            match file.bytes().ok().map(serde_json::from_slice::<Value>) {
                Some(Ok(v)) => {
                    out.insert(format!("{ns}:{}", file.name), v);
                }
                _ => log::warn!("horizon-gaps: enchantment {ns}:{} is not read", file.name),
            }
        }
    }
    for (id, versions) in overlay.kind("enchantment") {
        match serde_json::from_slice::<Value>(PackOverlay::top(versions)) {
            Ok(v) => {
                out.insert(id.to_owned(), v);
            }
            Err(e) => log::warn!("horizon-gaps: data pack enchantment {id} is not read: {e}"),
        }
    }
    out
}

/// One entry as `Enchantments::load_with` builds it; `exclusive_set` as a tag reads the registry's tags.
fn entry(id: &str, v: &Value, registry: &Enchantments) -> Enchantment {
    let exclusive = match &v["exclusive_set"] {
        Value::String(s) if s.starts_with('#') => {
            registry.tags.get(&s[1..]).cloned().unwrap_or_default()
        }
        other => string_list(other)
            .into_iter()
            .map(|s| Arc::from(s.as_str()))
            .collect(),
    };
    let key = v["description"]["translate"].as_str().unwrap_or("");
    let name = id.split_once(':').map_or(id, |(_, path)| path);
    Enchantment {
        id: Arc::from(id),
        description: registry
            .lang
            .get(key)
            .cloned()
            .or_else(|| v["description"]["fallback"].as_str().map(str::to_owned))
            .or_else(|| v["description"].as_str().map(str::to_owned))
            .unwrap_or_else(|| name.replace('_', " ")),
        description_key: key.to_owned(),
        max_level: v["max_level"].as_u64().unwrap_or(1) as u32,
        weight: v["weight"].as_u64().unwrap_or(1) as u32,
        anvil_cost: v["anvil_cost"].as_u64().unwrap_or(0) as u32,
        min_cost: Cost::parse(&v["min_cost"]),
        max_cost: Cost::parse(&v["max_cost"]),
        supported: string_list(&v["supported_items"]),
        primary: v.get("primary_items").map(string_list),
        exclusive: exclusive.into_iter().collect(),
        slots: string_list(&v["slots"]),
        effects: v["effects"].clone(),
    }
}

/// `registry` with the entries `files` finds: added, or replacing one of the same id; sorted by id.
pub fn add(mut registry: Enchantments, root: &Path, overlay: &PackOverlay) -> Enchantments {
    let files = files(root, overlay);
    if files.is_empty() {
        return registry;
    }
    // The mods' names: `enchantment.<ns>.<path>` keys from each namespace's English file, under the game's own.
    let mut namespaces: Vec<&str> = files
        .keys()
        .filter_map(|id| id.split_once(':').map(|(ns, _)| ns))
        .collect();
    namespaces.dedup();
    for ns in namespaces.into_iter().filter(|ns| *ns != "minecraft") {
        let file = root.join(format!("assets/{ns}/lang/en_us.json"));
        let Some(lang) = horizon_core::prepared_files::read(&file)
            .ok()
            .and_then(|b| serde_json::from_slice::<BTreeMap<String, String>>(&b).ok())
        else {
            continue;
        };
        for (key, text) in lang
            .into_iter()
            .filter(|(k, _)| k.starts_with("enchantment."))
        {
            registry.lang.entry(key).or_insert(text);
        }
    }
    let count = files.len();
    for (id, v) in &files {
        let e = entry(id, v, &registry);
        match registry.list.iter_mut().find(|old| &*old.id == id.as_str()) {
            Some(old) => *old = e,
            None => registry.list.push(e),
        }
    }
    registry.list.sort_by(|a, b| a.id.cmp(&b.id));
    registry.index = registry
        .list
        .iter()
        .enumerate()
        .map(|(i, e)| (e.id.clone(), i))
        .collect();
    log::info!("horizon-gaps: {count} enchantments from mods' namespaces and data packs");
    registry
}

#[cfg(test)]
mod tests {
    use super::*;

    fn scratch(name: &str) -> std::path::PathBuf {
        let dir = std::env::temp_dir().join(format!(
            "horizon-gaps-enchantments-{name}-{}",
            std::process::id()
        ));
        let _ = std::fs::remove_dir_all(&dir);
        dir
    }
    fn write(root: &Path, path: &str, text: &str) {
        let file = root.join(path);
        std::fs::create_dir_all(file.parent().unwrap()).unwrap();
        std::fs::write(file, text).unwrap();
    }

    #[test]
    fn a_mods_namespace_and_a_packs_enchantments_join_the_registry() {
        let root = scratch("join");
        write(
            &root,
            "data/minecraft/enchantment/lunge.json",
            r#"{"max_level": 3, "weight": 5, "description": {"translate": "enchantment.minecraft.lunge"}}"#,
        );
        write(
            &root,
            "data/ghast/enchantment/aerial_swiftness.json",
            r##"{"max_level": 3, "slots": ["body"], "supported_items": "#minecraft:harnesses",
                "description": {"translate": "enchantment.ghast.aerial_swiftness"},
                "min_cost": {"base": 10, "per_level_above_first": 10},
                "effects": {"minecraft:attributes": [{"attribute": "minecraft:flying_speed", "id": "ghast:a",
                  "amount": {"type": "minecraft:linear", "base": 0.01, "per_level_above_first": 0.02},
                  "operation": "add_value"}]}}"##,
        );
        write(&root, "data/ghast/enchantment/broken.json", "{not json");
        write(
            &root,
            "assets/ghast/lang/en_us.json",
            r#"{"enchantment.ghast.aerial_swiftness": "Aerial Swiftness", "item.ghast.x": "X"}"#,
        );
        let pack = [
            ("enchantment/minecraft:lunge".to_owned(), vec![
                br#"{"max_level": 1}"#.to_vec(),
                br#"{"max_level": 4, "description": {"translate": "enchantment.minecraft.lunge"}}"#.to_vec(),
            ]),
            ("enchantment/pack:sub/zap".to_owned(), vec![br#"{"max_level": 2}"#.to_vec()]),
            ("recipe/pack:zap".to_owned(), vec![b"{}".to_vec()]),
        ];
        let overlay = PackOverlay { files: &pack };
        let registry = add(Enchantments::load_with(&root, &overlay), &root, &overlay);
        let ids: Vec<&str> = registry.list.iter().map(|e| &*e.id).collect();
        assert_eq!(
            ids,
            ["ghast:aerial_swiftness", "minecraft:lunge", "pack:sub/zap"]
        );
        let swift = registry
            .get("ghast:aerial_swiftness")
            .expect("the mod's own namespace");
        assert_eq!(swift.description, "Aerial Swiftness");
        assert_eq!((swift.max_level, swift.min_cost.calculate(2)), (3, 20));
        assert_eq!(swift.slots, ["body"]);
        assert_eq!(swift.supported, ["#minecraft:harnesses"]);
        assert!(swift.effects["minecraft:attributes"].is_array());
        assert_eq!(
            registry.get("minecraft:lunge").unwrap().max_level,
            4,
            "the last pack's file replaces the game's"
        );
        assert_eq!(registry.get("pack:sub/zap").unwrap().description, "sub/zap");
        assert!(registry.get("ghast:broken").is_none());
        let _ = std::fs::remove_dir_all(root);
    }

    #[test]
    fn without_other_enchantments_the_registry_is_horizons() {
        let root = scratch("none");
        write(
            &root,
            "data/minecraft/enchantment/lunge.json",
            r#"{"max_level": 3}"#,
        );
        write(&root, "data/ghast/recipe/x.json", "{}");
        let registry = add(
            Enchantments::load_with(&root, &PackOverlay::NONE),
            &root,
            &PackOverlay::NONE,
        );
        assert_eq!(registry.list.len(), 1);
        assert_eq!(registry.get("minecraft:lunge").unwrap().max_level, 3);
        let _ = std::fs::remove_dir_all(root);
    }
}
