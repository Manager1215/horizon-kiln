// A player picks up an item on the integrated server: the game's ItemEntity.playerTouch, for the mods' mixins in it
// (Kiln's Mixin bridge, D-200): one that cancels it keeps the item on the ground (horizon.rs).
fn kiln_item_picked_up(game: &mut crate::game::Game<'_>, player: u128, item: &str, count: u32, canceled: &mut bool) {
    use crate::game::kiln as m;
    if game.native_on(m::ID) && !game.native_window() {
        crate::game::kiln_horizon::item_picked_up(game, player, item, count, canceled);
    }
}
