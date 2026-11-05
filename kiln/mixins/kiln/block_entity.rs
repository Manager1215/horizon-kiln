// The block entity a block gets, in either game: a Java mod's block gets the one its code makes (horizon.rs).
fn kiln_block_entity(
    game: &mut crate::game::Game<'_>,
    block: &str,
    entity: &mut Option<(String, horizon_world::nbt::Compound)>,
) {
    use crate::game::kiln as m;
    if game.native_on(m::ID) {
        crate::game::kiln_horizon::block_entity(block, entity);
    }
}
