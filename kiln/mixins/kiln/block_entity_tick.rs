// A block entity's server tick, on the integrated server: a Java mod's runs its ticker (horizon.rs).
fn kiln_block_entity_tick(
    game: &mut crate::game::Game<'_>,
    pos: [i32; 3],
    entity: &mut horizon_client::containers::ModBlockEntity,
) {
    use crate::game::kiln as m;
    if game.native_on(m::ID) {
        crate::game::kiln_horizon::block_entity_tick(game, pos, entity);
    }
}
