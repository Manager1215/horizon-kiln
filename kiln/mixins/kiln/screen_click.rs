// A press on a mod block's screen over no slot: a Java machine's side panel (horizon.rs, D-214).
fn kiln_screen_click(
    game: &crate::game::Game<'_>,
    block: &str,
    data: &[i32],
    at: [f32; 2],
    right: bool,
    button: &mut Option<i32>,
) {
    use crate::game::kiln as m;
    if game.native_on(m::ID) {
        crate::game::kiln_horizon::screen_click(block, data, at, right, button);
    }
}
