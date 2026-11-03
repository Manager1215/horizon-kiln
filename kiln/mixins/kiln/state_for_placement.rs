// The state a block takes where it is placed, on the integrated server: a Java mod's block says (horizon.rs).
fn kiln_state_for_placement(
    level: &crate::game::GameLevel<'_>,
    dimension: horizon_world::level::Dimension,
    state: horizon_world::registry::BlockStateId,
    context: &horizon_world::level::PlacementContext,
    answer: &mut Option<Option<horizon_world::registry::BlockStateId>>,
) {
    crate::game::kiln_horizon::state_for_placement(level, dimension, state, context, answer);
}
