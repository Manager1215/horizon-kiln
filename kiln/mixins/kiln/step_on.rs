// An entity stands on a mod's block, on the integrated server: a player's step waits for their tick, where Kiln can
// ask the Java mod (horizon.rs).
fn kiln_step_on(
    dimension: &str,
    entity: i32,
    kind: &str,
    state: horizon_world::registry::BlockStateId,
    position: [i32; 3],
) {
    crate::game::kiln_horizon::queue_step(dimension, entity, kind, state, position);
}
