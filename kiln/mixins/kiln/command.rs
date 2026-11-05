// A command the window's player typed or clicked, without its slash, before the server and Horizon's commands see it:
// when its first word is one of the Java mods' client commands (ClientCommandRegistrationCallback), Java runs it with
// the next tick and nothing else does, as Fabric's client commands take the line first.
fn kiln_command(game: &mut crate::game::Game<'_>, command: &str, ran: &mut bool) {
    use crate::game::kiln as m;
    if *ran || !game.native_on(m::ID) || !game.native_window() {
        return;
    }
    *ran = m::client_command(command);
}
