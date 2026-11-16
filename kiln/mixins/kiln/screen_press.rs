// A mouse button down over the open screen: a mod's own screen Kiln shows takes it (horizon.rs, D-094).
fn kiln_screen_press(game: &crate::game::Game<'_>, screen: &horizon_client::mod_api::NativeScreenView<'_>, button: i32, modifiers: u32, canceled: &mut bool) {
    use crate::game::{kiln as m, kiln_horizon as h};
    if game.native_on(m::ID) {
        // The title screen's question about the last game's stop (D-112) takes the click while it shows.
        if h::crash_question_click(game, screen) {
            *canceled = true;
            return;
        }
        let fields = h::at_pointer("press", screen, &[button.to_string(), modifiers.to_string()]);
        h::screen_input(game, screen, fields, canceled);
    }
}
