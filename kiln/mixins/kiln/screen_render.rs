// The open screen drew itself: a mod's own screen Kiln shows, drawn over it (horizon.rs, D-094); on the title screen,
// Kiln's question about the last game's stop by a Java mod (D-112).
fn kiln_screen_render_post(
    game: &crate::game::Game<'_>,
    batch: &mut horizon_client::hud::HudBatch,
    screen: &horizon_client::mod_api::NativeScreenView<'_>,
) {
    use crate::game::kiln as m;
    if game.native_on(m::ID) {
        crate::game::kiln_horizon::screen_render_post(game, batch, screen);
        // The title screen's question about the last game's stop (D-112).
        crate::game::kiln_horizon::crash_question(game, batch, screen);
    }
}
