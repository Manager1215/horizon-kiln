// As the integrated server's tick starts and ends: Fabric's START_SERVER_TICK and END_SERVER_TICK for the Java mods
// that hear them (horizon.rs).
fn kiln_server_tick(game: &mut crate::game::Game<'_>, phase: crate::mod_api::ServerTickPhase) {
    use crate::game::kiln as m;
    if game.native_on(m::ID) {
        let start = matches!(phase, crate::mod_api::ServerTickPhase::ServerTickStart { .. });
        crate::game::kiln_horizon::server_tick(game, start);
    }
}
