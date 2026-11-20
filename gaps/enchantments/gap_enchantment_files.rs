// SPDX-License-Identifier: MIT
// A world's data packs' enchantment files (horizon-gaps, `enchantments`).
//
// Horizon's `content_overlay` gathers the content files of a world's enabled packs (recipes, loot tables, tags...),
// which the game's tables read as their pack overlay; enchantments are not among them. This module adds each enabled
// pack's `data/<ns>/enchantment/**.json` as `enchantment/<ns>:<path>`, each id's files in pack order, so the overlay
// carries them; `gap_enchantments` reads them into the enchantment registry. Below them go the resources' own files in
// other namespaces than `minecraft` (a content mod's): the game builds a world's tables only when the overlay has
// files, and those tables are where `gap_enchantments` adds a mod's enchantments.
//
// It is a child module of `horizon_client::game::functions`, which keeps the world's pack stack private; the RETURN
// hook on `content_overlay` calls `add`.

use std::collections::BTreeMap;
use std::path::Path;

/// `enchantment/<ns>:<path>` for a pack file `data/<ns>/enchantment/<path>.json`.
fn enchantment_id(file: &str) -> Option<String> {
    let rest = file.strip_prefix("data/")?;
    let (ns, rest) = rest.split_once('/')?;
    let path = rest.strip_prefix("enchantment/")?.strip_suffix(".json")?;
    Some(format!("enchantment/{ns}:{path}"))
}

/// `files` (what `content_overlay` returned) with the world's enabled packs' enchantment files.
pub(super) fn add(
    mut files: Vec<(String, Vec<Vec<u8>>)>,
    world_root: &Path,
    resources: &Path,
    vanilla_disabled: bool,
    enabled_features: &[String],
) -> Vec<(String, Vec<Vec<u8>>)> {
    let stack = super::pack_stack(world_root, resources, vanilla_disabled, enabled_features);
    let mut found: BTreeMap<String, Vec<Vec<u8>>> = BTreeMap::new();
    let mut namespaces: Vec<String> = std::fs::read_dir(resources.join("data"))
        .into_iter()
        .flatten()
        .flatten()
        .filter(|e| e.path().is_dir())
        .filter_map(|e| e.file_name().into_string().ok())
        .filter(|ns| ns != "minecraft")
        .collect();
    namespaces.sort();
    for ns in namespaces {
        let folder = resources.join("data").join(&ns).join("enchantment");
        let Ok(folder) = horizon_world::json_folder::read(&folder) else {
            continue;
        };
        for file in &folder.files {
            if let Ok(bytes) = file.bytes() {
                found
                    .entry(format!("enchantment/{ns}:{}", file.name))
                    .or_default()
                    .push(bytes.to_vec());
            }
        }
    }
    for pack in stack.enabled_packs() {
        for file in pack.list("data/") {
            let Some(id) = enchantment_id(&file) else {
                continue;
            };
            if let Some(bytes) = pack.read(&file) {
                found.entry(id).or_default().push(bytes);
            }
        }
    }
    files.retain(|(id, _)| !found.contains_key(id));
    files.extend(found);
    files
}

#[cfg(test)]
mod tests {
    #[test]
    fn a_worlds_packs_enchantments_reach_the_overlay_in_pack_order() {
        let dir = std::env::temp_dir().join(format!(
            "horizon-gaps-enchantment-files-{}",
            std::process::id()
        ));
        let _ = std::fs::remove_dir_all(&dir);
        let files = [
            (
                "a",
                "data/minecraft/enchantment/lunge.json",
                r#"{"max_level": 4}"#,
            ),
            ("a", "data/zap/enchantment/sub/zap.json", "{}"),
            ("a", "data/zap/enchantment/readme.txt", "no"),
            (
                "b",
                "data/minecraft/enchantment/lunge.json",
                r#"{"max_level": 5}"#,
            ),
            ("off", "data/off/enchantment/off.json", "{}"),
        ];
        for (pack, path, text) in files {
            let pack = dir.join("datapacks").join(pack);
            let file = pack.join(path);
            std::fs::create_dir_all(file.parent().unwrap()).unwrap();
            std::fs::write(pack.join("pack.mcmeta"), "{}").unwrap();
            std::fs::write(file, text).unwrap();
        }
        let resources = dir.join("resources");
        std::fs::create_dir_all(resources.join("data/ghast/enchantment")).unwrap();
        std::fs::write(resources.join("data/ghast/enchantment/swift.json"), "{}").unwrap();
        std::fs::create_dir_all(resources.join("data/minecraft/enchantment")).unwrap();
        std::fs::write(
            resources.join("data/minecraft/enchantment/lunge.json"),
            "{}",
        )
        .unwrap();
        let json = serde_json::json!({"enabled": ["file/a", "file/b"], "disabled": ["file/off"]});
        std::fs::write(dir.join("datapacks.json"), json.to_string()).unwrap();
        // Through the hook: `content_overlay` returns the files `add` made.
        let files = super::super::content_overlay(&dir, &resources, true, &[]);
        let get = |id: &str| files.iter().find(|(k, _)| k == id).map(|(_, v)| v.clone());
        assert_eq!(
            get("enchantment/minecraft:lunge").unwrap(),
            [
                br#"{"max_level": 4}"#.to_vec(),
                br#"{"max_level": 5}"#.to_vec()
            ]
        );
        assert!(get("enchantment/zap:sub/zap").is_some());
        assert_eq!(
            get("enchantment/ghast:swift").unwrap(),
            [b"{}".to_vec()],
            "a content mod's, below the packs"
        );
        assert!(get("enchantment/off:off").is_none(), "a disabled pack's");
        assert_eq!(
            files
                .iter()
                .filter(|(k, _)| k.starts_with("enchantment/"))
                .count(),
            3
        );
        let _ = std::fs::remove_dir_all(&dir);
    }
}
