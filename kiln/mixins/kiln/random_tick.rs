// A block ticks at random, on the integrated server: a Java mod's block's own random tick runs instead of its
// template's (horizon.rs).
fn kiln_random_tick(
    level: &mut crate::game::GameLevel<'_>,
    ticks: &mut horizon_world::level::Ticks,
    dimension: horizon_world::level::Dimension,
    pos: horizon_core::position::BlockPos,
    state: horizon_world::registry::BlockStateId,
    handled: &mut bool,
) {
    crate::game::kiln_horizon::random_tick(level, ticks, dimension, pos, state, handled);
}
