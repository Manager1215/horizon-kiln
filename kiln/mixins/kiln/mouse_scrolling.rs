// The wheel turns while playing: the game's MouseHandler.onScroll, for the mods' mixins in it (Kiln's Mixin bridge,
// D-102). Only when a mod's mixin is there; Horizon scrolls as it does.
fn kiln_mouse_scrolling(game: &mut crate::game::Game<'_>, scroll: [f64; 2]) {
    use crate::game::kiln as m;
    if game.native_on(m::ID) && game.native_window() && m::bridged("mouse_scrolling") {
        m::hook("mouse_scrolling", &[scroll[0].to_string(), scroll[1].to_string()]);
    }
}
