// A player respawned, on the integrated server: the Java mods hear it (horizon.rs).
fn kiln_player_respawned(game: &mut crate::game::Game<'_>) {
    use crate::game::kiln as m;
    if game.native_on(m::ID) {
        crate::game::kiln_horizon::player_respawned(game);
    }
}
