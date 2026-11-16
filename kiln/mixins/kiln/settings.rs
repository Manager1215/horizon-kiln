// A Java mod's page in Options > Mods: a button for its settings page (horizon.rs, D-094). Kiln's own page: a button
// for Kiln's overlay (D-113), once Java runs.
fn kiln_settings(game: &mut crate::game::Game<'_>, mod_id: &str, buttons: &mut Vec<String>) {
    use crate::game::kiln as m;
    if game.native_on(m::ID) {
        crate::game::kiln_horizon::settings_buttons(mod_id, buttons);
    }
}
