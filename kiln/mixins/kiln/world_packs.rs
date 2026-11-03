// Before Horizon reads a world's data packs: the Java mods' world generation, out of their jars (structures.rs), then
// their changes to the game's loot tables (loot.rs).
fn kiln_world_packs() {
    let kiln = std::path::Path::new("kiln");
    crate::game::kiln_structures::prepare(kiln);
    crate::game::kiln_loot::prepare(kiln);
}
