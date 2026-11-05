// Each game tick. In the window's game, in a world: Kiln starts on the first one, then gives its Java mods a
// client tick with the game's state. In the single-player server's game: it publishes the player's
// advancements for the window's (horizon.rs says why), and does what Java mods' block code did from hooks that
// couldn't (a neighbour change, a placement).
fn kiln_tick(game: &mut crate::game::Game<'_>) {
    use crate::game::{kiln as m, kiln_horizon as h};
    if !game.native_on(m::ID) {
        return;
    }
    if !game.native_window() {
        h::publish_server_advancements(game);
        h::apply_pending(game);
        return;
    }
    if game.native_player().is_none() {
        return;
    }
    let data_dir = game.native_data_dir(m::ID);
    crate::game::kiln_jvm::set_resources(&game.options.resources);
    let snapshot = h::snapshot(game);
    let lines = m::tick(
        &data_dir,
        || game.native_setting(m::ID, "java_home"),
        || h::font_widths(game),
        snapshot,
    );
    let before = h::chat_count(game);
    for line in lines {
        game.native_chat(&line);
    }
    h::chat_written(game, before);
    h::play(game, m::take_played());
    // What the mods sent as the player (ClientPacketListener.sendCommand): the mods' client commands take it first,
    // as Fabric's do, then the server, or the game's own commands without one.
    for (attended, command) in m::take_sent() {
        if game.client_mod_command(&command) {
            continue;
        }
        if game.remote.is_some() {
            if attended {
                game.send_command(&command);
            } else {
                game.send_unattended_command(&command);
            }
        } else {
            game.run_command(&command);
        }
    }
}
