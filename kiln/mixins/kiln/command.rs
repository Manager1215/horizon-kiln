// A command the window's player typed or clicked, without its slash, before the server and Horizon's commands see it:
// when its first word is one of the Java mods' client commands (ClientCommandRegistrationCallback), Java runs it with
// the next tick and nothing else does, as Fabric's client commands take the line first.
fn kiln_command(game: &mut crate::game::Game<'_>, command: &str, ran: &mut bool) {
    use crate::game::kiln as m;
    if *ran || !game.native_on(m::ID) || !game.native_window() {
        return;
    }
    // `/kiln off <mod id>`, `/kiln on <mod id>`: a Java mod's switch in Options > Mods from the chat, which turns a
    // running mod off or on at once (D-113's soft switch) and from the next world fully.
    if let Some((id, on)) = m::switch_command(command) {
        let row = m::row_id(&id);
        if on {
            game.settings.mods.disabled.remove(&row);
        } else {
            game.settings.mods.disabled.insert(row);
        }
        game.native_chat(&format!("Kiln: {id} is {} now", if on { "on" } else { "off" }));
        *ran = true;
        return;
    }
    // `/kiln` (or `/kiln mods`): the overlay, without its key.
    if m::overlay_command(command) {
        if m::java_running() {
            crate::game::kiln_horizon::toggle_overlay(game);
        } else {
            game.native_chat("Kiln: Java isn't running in this game (no Java mod is on, or safe mode), so the overlay can't open");
        }
        *ran = true;
        return;
    }
    *ran = m::client_command(command);
}
