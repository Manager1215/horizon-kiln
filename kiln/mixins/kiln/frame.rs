// Every frame, in a world or not: while no game tick comes (paused, on the title screen), Kiln still gives
// its Java mods a client tick 20 times a second, as vanilla's client ticks there too.
fn kiln_frame(game: &mut crate::game::Game<'_>, elapsed: std::time::Duration) {
    use crate::game::{kiln as m, kiln_horizon as h};
    let _ = elapsed;
    if !game.native_on(m::ID) || !game.native_window() {
        return;
    }
    // Options > Mods' switches for the Java mods, as the player leaves them; on the title screen, a change starts
    // Java again with the next world.
    m::set_off(m::off_ids(game.settings.mods.disabled.iter().map(String::as_str)));
    if game.title.is_some() {
        m::restart_for_switches();
    }
    let lines = m::frame(|| h::snapshot(game));
    let before = h::chat_count(game);
    for line in lines {
        game.native_chat(&line);
    }
    for component in m::take_chat_components() {
        h::chat_component(game, component);
    }
    h::chat_written(game, before);
    h::play(game, m::take_played());
}
