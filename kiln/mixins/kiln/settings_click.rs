// A button on a Java mod's page in Options > Mods: its settings page opens (horizon.rs, D-094). On Kiln's own page:
// Kiln's overlay opens (D-113).
fn kiln_settings_click(game: &mut crate::game::Game<'_>, mod_id: &str, button: usize) {
    use crate::game::{kiln as m, kiln_horizon as h};
    if !game.native_on(m::ID) {
        return;
    }
    if mod_id == m::ID {
        h::toggle_overlay(game);
    } else {
        h::settings_click(mod_id, button);
    }
}
