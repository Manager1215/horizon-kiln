// Drawing a mod block's screen: a Java mod's machine's energy and progress (horizon.rs).
fn kiln_screen_draw(
    game: &crate::game::Game<'_>,
    block: &str,
    data: &[i32],
    batch: &mut horizon_client::hud::HudBatch,
    origin: [f32; 2],
) {
    use crate::game::kiln as m;
    if game.native_on(m::ID) {
        crate::game::kiln_horizon::screen_draw(game, block, data, batch, origin);
    }
}
