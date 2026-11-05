// A tooltip on a mod block's screen: a Java mod's machine's energy (horizon.rs).
fn kiln_screen_tooltip(
    game: &crate::game::Game<'_>,
    block: &str,
    data: &[i32],
    at: [f32; 2],
    lines: &mut Vec<String>,
) {
    use crate::game::kiln as m;
    if game.native_on(m::ID) {
        crate::game::kiln_horizon::screen_tooltip(block, data, at, lines);
    }
}
