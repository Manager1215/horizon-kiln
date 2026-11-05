// Horizon asks once at start for the blocks native mods declare in code (API 145): Kiln's are those Horizon's import
// left out of the Java mods' content mods, made just before (horizon.rs, carry.rs; D-078).
fn kiln_declared_blocks(blocks: &mut Vec<horizon_mods::content::Block>) {
    crate::game::kiln_horizon::declared_blocks(blocks);
}
