// A selector's `type=#<namespace>:<tag>` (D-201): Horizon reads only `data/minecraft/tags/entity_type/<path>.json` of
// the game's resources, without the tags it includes, so a mod's tag (`#more_mobs:parsable`, `!#spawnanimations:exclude`)
// matched nothing. Kiln reads the tag in any namespace from the resources (content mods' data is there) and from Kiln's
// pack, both folder names, `replace` and included tags as the game's `TagLoader`, kept until the pack changes. A
// type it finds answers yes; else Horizon's own reading answers.
fn kiln_entity_tag_matches(
    game: &crate::game::Game<'_>,
    tag: &str,
    ty: &str,
) -> std::ops::ControlFlow<bool> {
    use std::collections::{HashMap, HashSet};
    use std::path::{Path, PathBuf};
    type Tags = HashMap<String, HashSet<String>>;
    static CACHE: std::sync::Mutex<Option<((PathBuf, Option<std::time::SystemTime>), Tags)>> =
        std::sync::Mutex::new(None);
    fn id(s: &str) -> String {
        if s.contains(':') {
            s.to_owned()
        } else {
            format!("minecraft:{s}")
        }
    }
    // The entries of `tag` in each root, lower roots first: `replace` drops what came before.
    fn raw(roots: &[PathBuf], tag: &str) -> Vec<String> {
        let (ns, path) = tag.split_once(':').unwrap_or(("minecraft", tag));
        let mut out = Vec::new();
        for root in roots {
            for folder in ["entity_type", "entity_types"] {
                let file = root.join(format!("data/{ns}/tags/{folder}/{path}.json"));
                let Some(v) = std::fs::read(&file)
                    .ok()
                    .and_then(|b| serde_json::from_slice::<serde_json::Value>(&b).ok())
                else {
                    continue;
                };
                if v["replace"].as_bool() == Some(true) {
                    out.clear();
                }
                out.extend(
                    v["values"]
                        .as_array()
                        .into_iter()
                        .flatten()
                        .filter_map(|e| e.as_str().or_else(|| e["id"].as_str()))
                        .map(str::to_owned),
                );
            }
        }
        out
    }
    fn resolve(
        roots: &[PathBuf],
        tag: &str,
        out: &mut HashSet<String>,
        seen: &mut HashSet<String>,
    ) {
        if !seen.insert(tag.to_owned()) {
            return;
        }
        for v in raw(roots, tag) {
            match v.strip_prefix('#') {
                Some(inner) => resolve(roots, &id(inner), out, seen),
                None => {
                    out.insert(id(&v));
                }
            }
        }
    }
    let pack = Path::new("kiln/generated/datapack");
    let stamp = (
        game.options.resources.clone(),
        std::fs::metadata(pack.join("pack.mcmeta"))
            .and_then(|m| m.modified())
            .ok(),
    );
    let tag = id(tag);
    let mut cache = CACHE.lock().unwrap_or_else(|e| e.into_inner());
    if cache.as_ref().is_none_or(|(s, _)| *s != stamp) {
        *cache = Some((stamp.clone(), HashMap::new()));
    }
    let Some((_, tags)) = cache.as_mut() else {
        return std::ops::ControlFlow::Continue(());
    };
    let found = tags
        .entry(tag.clone())
        .or_insert_with(|| {
            let roots = [stamp.0.clone(), pack.to_path_buf()];
            let mut out = HashSet::new();
            resolve(&roots, &tag, &mut out, &mut HashSet::new());
            out
        })
        .contains(ty);
    if found {
        std::ops::ControlFlow::Break(true)
    } else {
        std::ops::ControlFlow::Continue(())
    }
}
