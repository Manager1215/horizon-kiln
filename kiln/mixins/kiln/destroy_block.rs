// A player breaks a block, on the integrated server: a Java mod's block hears it first (horizon.rs).
fn kiln_destroy_block(
    game: &mut crate::game::Game<'_>,
    pos: [i32; 3],
    state: horizon_world::registry::BlockStateId,
    canceled: &mut bool,
) {
    use crate::game::kiln as m;
    if game.native_on(m::ID) {
        crate::game::kiln_horizon::destroy_block(game, pos, state, canceled);
    }
}
