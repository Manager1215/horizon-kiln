// The mouse moved with a button held over the open screen: a mod's own screen Kiln shows takes it (horizon.rs, D-094).
fn kiln_screen_drag(
    game: &crate::game::Game<'_>,
    screen: &horizon_client::mod_api::NativeScreenView<'_>,
    button: i32,
    delta: [f32; 2],
    modifiers: u32,
    canceled: &mut bool,
) {
    use crate::game::{kiln as m, kiln_horizon as h};
    if game.native_on(m::ID) {
        let rest = [button.to_string(), m::num(delta[0]), m::num(delta[1]), modifiers.to_string()];
        h::screen_input(game, screen, h::at_pointer("drag", screen, &rest), canceled);
    }
}
