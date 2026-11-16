// A player's command on the integrated server, for a command a mod registered: one of the Java mods' runs in Java
// (horizon.rs, D-107).
fn kiln_perform_command(
    game: &mut crate::game::Game<'_>,
    player: &crate::mod_api::NativeServerPlayer,
    command: &str,
    ran: &mut bool,
) {
    use crate::game::kiln as m;
    if game.native_on(m::ID) {
        crate::game::kiln_horizon::perform_command(game, player, command, ran);
    }
}
