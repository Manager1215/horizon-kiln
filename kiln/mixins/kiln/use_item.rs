// A player uses an item, on the integrated server: a Java mod's item does what its code says (horizon.rs).
fn kiln_use_item(
    game: &mut crate::game::Game<'_>,
    stack: &horizon_client::items::ItemStack,
    claimed: &mut bool,
) {
    use crate::game::kiln as m;
    if game.native_on(m::ID) {
        crate::game::kiln_horizon::use_item(game, stack, claimed);
    }
}
