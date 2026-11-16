// A key going down while playing (no screen open): Kiln's Java mods hear of it with the next tick, for the
// keys they registered (vanilla's own mapping on that key still runs). Kiln keeps which keys are held too.
fn kiln_key(game: &mut crate::game::Game<'_>, key: &str, pressed: bool) {
    use crate::game::kiln as m;
    if !game.native_on(m::ID) || !game.native_window() {
        return;
    }
    // The game's KeyboardHandler.keyPress, for the mods' mixins in it (Kiln's Mixin bridge, D-102).
    m::hook("key", &[key.to_owned(), if pressed { "1" } else { "0" }.to_owned()]);
    // A release the screen hooks didn't hand to the mods' screen whose press they did (D-174).
    if !pressed {
        m::mod_screen(|s| s.released(key));
    }
    // Held or not, for KeyMapping.isDown; a press while playing, for consumeClick. The modifier keys count over a
    // screen too, as the game's own `hasShiftDown` reads the keyboard: a shift-click or a ctrl-click on a mod's screen
    // (a recipe viewer's "+" moving as many as it can, its cheat mode's toggle; D-167).
    let modifier = ["shift", "control", "alt", "win"].iter().any(|m| key.ends_with(&format!("left.{m}")) || key.ends_with(&format!("right.{m}")));
    m::key_held(key, pressed && (game.native_playing() || modifier));
    if pressed && game.native_playing() {
        // Kiln's overlay's key (D-113): F7 unless bound otherwise on Kiln's page in Options > Mods. Over the overlay,
        // the overlay itself takes it, and closes.
        if key == game.native_mod_key(m::ID, m::OVERLAY_KEY, m::OVERLAY_KEY_DEFAULT) {
            crate::game::kiln_horizon::toggle_overlay(game);
            return;
        }
        m::key(key);
    }
}
