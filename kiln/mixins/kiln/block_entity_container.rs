// A block entity's item slots: a Java mod's container's, as its code says them (horizon.rs).
fn kiln_block_entity_container(
    game: &crate::game::Game<'_>,
    block: &str,
    container: &mut Option<horizon_client::containers::ModContainer>,
) {
    use crate::game::kiln as m;
    if game.native_on(m::ID) {
        crate::game::kiln_horizon::block_entity_container(block, container);
    }
}
