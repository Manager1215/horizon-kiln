// A player died on the integrated server: the game's ServerPlayer.die, for the mods' mixins in it (Kiln's Mixin
// bridge, D-106; horizon.rs).
fn kiln_player_died(game: &mut crate::game::Game<'_>, player: u128, cause: Option<&str>) {
    use crate::game::kiln as m;
    if game.native_on(m::ID) && !game.native_window() {
        crate::game::kiln_horizon::player_died(game, player, cause);
    }
}
