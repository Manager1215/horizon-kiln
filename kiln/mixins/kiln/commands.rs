// The Java mods' client commands, by name, for the chat box to colour, complete and list as the game's own. Asked
// when a connection starts and when the server sends its tree: those Java has registered by then.
fn kiln_commands(game: &mut crate::game::Game<'_>, commands: &mut Vec<&'static str>) {
    use crate::game::kiln as m;
    if !game.native_on(m::ID) || !game.native_window() {
        return;
    }
    commands.extend(m::client_commands());
}
