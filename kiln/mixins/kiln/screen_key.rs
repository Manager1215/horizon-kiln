// A key down over the open screen (Escape too): a mod's own screen Kiln shows takes it (horizon.rs, D-094).
fn kiln_screen_key(
    game: &crate::game::Game<'_>,
    screen: &horizon_client::mod_api::NativeScreenView<'_>,
    key: &str,
    repeat: bool,
    modifiers: u32,
    canceled: &mut bool,
) {
    use crate::game::{kiln as m, kiln_horizon as h};
    let _ = repeat;
    if game.native_on(m::ID) {
        // A modifier key over a screen is held for the mods' `hasShiftDown` and the like (D-167).
        if ["shift", "control", "alt", "win"].iter().any(|k| key.ends_with(&format!("left.{k}")) || key.ends_with(&format!("right.{k}"))) {
            m::key_held(key, true);
        }
        h::screen_input(game, screen, vec!["key".into(), key.into(), modifiers.to_string()], canceled);
    }
}
