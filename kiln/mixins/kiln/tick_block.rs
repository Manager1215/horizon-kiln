// A block's scheduled tick, on the integrated server: a Java mod's block's own tick runs instead of its template's
// (horizon.rs).
fn kiln_tick_block(
    level: &mut crate::game::GameLevel<'_>,
    ticks: &mut horizon_world::level::Ticks,
    dimension: horizon_world::level::Dimension,
    pos: horizon_core::position::BlockPos,
    state: horizon_world::registry::BlockStateId,
    handled: &mut bool,
) {
    crate::game::kiln_horizon::tick_block(level, ticks, dimension, pos, state, handled);
}
