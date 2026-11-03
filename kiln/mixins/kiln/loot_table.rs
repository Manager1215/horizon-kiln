// A loot table as Horizon loads it: the pools the Java mods add to it (loot.rs ran their changes before the world's
// packs were read, into kiln/generated/loot.json), read again when that file changes.
fn kiln_loot_table(id: &str, table: &mut serde_json::Value) {
    static ADDED: std::sync::Mutex<Option<(Option<std::time::SystemTime>, serde_json::Value)>> =
        std::sync::Mutex::new(None);
    let file = std::path::Path::new("kiln/generated/loot.json");
    let modified = std::fs::metadata(file).and_then(|m| m.modified()).ok();
    let mut added = ADDED.lock().unwrap_or_else(|e| e.into_inner());
    if added.as_ref().map(|(m, _)| *m) != Some(modified) {
        let tables = std::fs::read(file)
            .ok()
            .and_then(|b| serde_json::from_slice::<serde_json::Value>(&b).ok())
            .and_then(|mut v| v.get_mut("tables").map(serde_json::Value::take))
            .unwrap_or_default();
        *added = Some((modified, tables));
    }
    let Some(serde_json::Value::Array(pools)) = added.as_ref().and_then(|(_, t)| t.get(id)) else {
        return;
    };
    let Some(table) = table.as_object_mut() else {
        return;
    };
    if let serde_json::Value::Array(list) = table
        .entry("pools")
        .or_insert_with(|| serde_json::Value::Array(Vec::new()))
    {
        list.extend(pools.iter().cloned());
    }
}
