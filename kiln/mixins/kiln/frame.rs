// Every frame, in a world or not: while no game tick comes (paused, on the title screen), Kiln still gives
// its Java mods a client tick 20 times a second, as vanilla's client ticks there too.
fn kiln_frame(game: &mut crate::game::Game<'_>, elapsed: std::time::Duration) {
    use crate::game::{kiln as m, kiln_horizon as h};
    let _ = elapsed;
    if !game.native_on(m::ID) || !game.native_window() {
        return;
    }
    let lines = m::frame(|| h::snapshot(game));
    for line in lines {
        game.native_chat(&line);
    }
}
