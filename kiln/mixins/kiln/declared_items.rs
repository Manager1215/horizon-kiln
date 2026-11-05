// Horizon asks once at start for the items native mods declare in code (API 145): Kiln's are those Horizon's import
// left out of the Java mods' content mods, made just before (horizon.rs, carry.rs; D-078).
fn kiln_declared_items(items: &mut Vec<horizon_mods::content::Item>) {
    crate::game::kiln_horizon::declared_items(items);
}
