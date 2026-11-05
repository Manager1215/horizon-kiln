// Before Horizon reads the content mods, once at start: the Java mods' content mods get the blocks Horizon's import
// left out (horizon.rs, carry.rs).
fn kiln_content_root(enabled: &std::collections::BTreeSet<String>) {
    crate::game::kiln_horizon::carry_blocks(enabled);
}
