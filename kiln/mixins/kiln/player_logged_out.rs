// A player left the integrated server: the Java mods hear their connection closed, and Kiln forgets the level it last
// told them of (horizon.rs).
fn kiln_player_logged_out(game: &mut crate::game::Game<'_>, player: u128) {
    use crate::game::kiln as m;
    if game.native_on(m::ID) {
        crate::game::kiln_horizon::player_logged_out(game, player);
    }
}
