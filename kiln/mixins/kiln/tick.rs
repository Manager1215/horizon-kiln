// Each game tick. In the window's game, in a world: Kiln starts on the first one, then gives its Java mods a
// client tick with the game's state. In the single-player server's game: it publishes the player's
// advancements for the window's (horizon.rs says why).
fn kiln_tick(game: &mut crate::game::Game<'_>) {
    use crate::game::{kiln as m, kiln_horizon as h};
    if !game.native_on(m::ID) {
        return;
    }
    if !game.native_window() {
        h::publish_server_advancements(game);
        return;
    }
    if game.native_player().is_none() {
        return;
    }
    let data_dir = game.native_data_dir(m::ID);
    let snapshot = h::snapshot(game);
    let lines = m::tick(
        &data_dir,
        || game.native_setting(m::ID, "java_home"),
        || h::font_widths(game),
        snapshot,
    );
    for line in lines {
        game.native_chat(&line);
    }
}
