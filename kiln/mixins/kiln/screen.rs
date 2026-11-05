// A mod block's screen: a Java mod's menu, laid out as its code laid it out (horizon.rs).
fn kiln_screen(
    game: &crate::game::Game<'_>,
    block: &str,
    screen: &mut Option<crate::game::native_hooks::NativeScreen>,
) {
    use crate::game::kiln as m;
    if game.native_on(m::ID) {
        crate::game::kiln_horizon::screen(block, screen);
    }
}
