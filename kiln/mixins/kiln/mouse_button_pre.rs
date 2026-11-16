// A mouse button goes down or up (GLFW's numbers): the game's MouseHandler.onButton, for the mods' mixins in it (Kiln's
// Mixin bridge, D-102). Only when a mod's mixin is there.
fn kiln_mouse_button_pre(game: &mut crate::game::Game<'_>, button: i32, pressed: bool, modifiers: u32) {
    use crate::game::kiln as m;
    if game.native_on(m::ID) && game.native_window() && m::bridged("mouse_button_pre") {
        let pressed = if pressed { "1" } else { "0" };
        m::hook("mouse_button_pre", &[button.to_string(), pressed.to_owned(), modifiers.to_string()]);
    }
}
