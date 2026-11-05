// Whether a block's block entity ticks: a Java mod's does when its block has a ticker (horizon.rs).
fn kiln_block_entity_ticks(game: &crate::game::Game<'_>, block: &str, ticks: &mut bool) {
    use crate::game::kiln as m;
    if game.native_on(m::ID) {
        crate::game::kiln_horizon::block_entity_ticks(block, ticks);
    }
}
