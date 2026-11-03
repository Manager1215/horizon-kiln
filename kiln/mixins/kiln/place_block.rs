// A player placed a block, on the integrated server: a Java mod's block hears it (horizon.rs).
fn kiln_place_block(
    game: &mut crate::game::Game<'_>,
    stack: &horizon_client::items::ItemStack,
    pos: [i32; 3],
    state: horizon_world::registry::BlockStateId,
) {
    use crate::game::kiln as m;
    if game.native_on(m::ID) {
        crate::game::kiln_horizon::place_block(game, stack, pos, state);
    }
}
