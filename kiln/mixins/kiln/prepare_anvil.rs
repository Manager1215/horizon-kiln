// Horizon worked out an anvil's result on the integrated server: the game's AnvilMenu.createResult runs for the mods'
// mixins in it at the station's next step, where its inputs are (Kiln's Mixin bridge, D-202; horizon.rs).
fn kiln_prepare_anvil(game: &mut crate::game::Game<'_>, player: u128) {
    use crate::game::kiln as m;
    if game.native_on(m::ID) && !game.native_window() {
        crate::game::kiln_horizon::prepare_anvil(player);
    }
}
