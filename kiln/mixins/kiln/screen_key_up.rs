// A key up over the open screen: a mod's own screen Kiln shows takes it (horizon.rs, D-094).
fn kiln_screen_key_up(game: &crate::game::Game<'_>, screen: &horizon_client::mod_api::NativeScreenView<'_>, key: &str, modifiers: u32, canceled: &mut bool) {
    use crate::game::{kiln as m, kiln_horizon as h};
    if game.native_on(m::ID) {
        // A modifier key over a screen is held for the mods' `hasShiftDown` and the like (D-167).
        if ["shift", "control", "alt", "win"].iter().any(|k| key.ends_with(&format!("left.{k}")) || key.ends_with(&format!("right.{k}"))) {
            m::key_held(key, false);
        }
        h::screen_input(game, screen, vec!["keyup".into(), key.into(), modifiers.to_string()], canceled);
    }
}
