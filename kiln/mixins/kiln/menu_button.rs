// A menu button pressed on a mod block entity's screen, on the integrated server: a Java machine's side panel
// (horizon.rs, D-214).
fn kiln_menu_button(game: &crate::game::Game<'_>, pos: [i32; 3], id: i32) {
    use crate::game::kiln as m;
    if game.native_on(m::ID) {
        crate::game::kiln_horizon::menu_button(game, pos, id);
    }
}
