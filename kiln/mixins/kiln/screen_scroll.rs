// The wheel over the open screen: a mod's own screen Kiln shows takes it (horizon.rs, D-094).
fn kiln_screen_scroll(game: &crate::game::Game<'_>, screen: &horizon_client::mod_api::NativeScreenView<'_>, scroll: [f64; 2], modifiers: u32, canceled: &mut bool) {
    use crate::game::{kiln as m, kiln_horizon as h};
    let _ = modifiers;
    if game.native_on(m::ID) {
        let rest = [scroll[0].to_string(), scroll[1].to_string()];
        h::screen_input(game, screen, h::at_pointer("scroll", screen, &rest), canceled);
    }
}
