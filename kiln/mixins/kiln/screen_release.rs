// A mouse button up over the open screen: a mod's own screen Kiln shows takes it (horizon.rs, D-094).
fn kiln_screen_release(game: &crate::game::Game<'_>, screen: &horizon_client::mod_api::NativeScreenView<'_>, button: i32, modifiers: u32, canceled: &mut bool) {
    use crate::game::{kiln as m, kiln_horizon as h};
    if game.native_on(m::ID) {
        let fields = h::at_pointer("release", screen, &[button.to_string(), modifiers.to_string()]);
        h::screen_input(game, screen, fields, canceled);
    }
}
