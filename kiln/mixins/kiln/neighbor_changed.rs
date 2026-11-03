// A block's neighbour changed, on the integrated server: a Java mod's block hears it (horizon.rs).
fn kiln_neighbor_changed(
    level: &crate::game::GameLevel<'_>,
    dimension: horizon_world::level::Dimension,
    pos: horizon_core::position::BlockPos,
    state: horizon_world::registry::BlockStateId,
    moved: bool,
) {
    crate::game::kiln_horizon::neighbor_changed(level, dimension, pos, state, moved);
}
