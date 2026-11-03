// A living player's server tick ends, on the integrated server: a player new to a level is told to the Java mods
// (horizon.rs).
fn kiln_player_tick(game: &mut crate::game::Game<'_>) {
    use crate::game::kiln as m;
    if game.native_on(m::ID) {
        crate::game::kiln_horizon::player_tick(game);
    }
}
