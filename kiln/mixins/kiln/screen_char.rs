// A character typed at the open screen: a mod's own screen Kiln shows takes it (horizon.rs, D-094).
fn kiln_screen_char(game: &crate::game::Game<'_>, screen: &horizon_client::mod_api::NativeScreenView<'_>, c: char, modifiers: u32, canceled: &mut bool) {
    use crate::game::{kiln as m, kiln_horizon as h};
    if game.native_on(m::ID) {
        let fields = vec!["char".into(), u32::from(c).to_string(), modifiers.to_string()];
        h::screen_input(game, screen, fields, canceled);
    }
}
