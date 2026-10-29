// Each game tick in a world, in the window's game (not the single-player server's): Kiln starts on the
// first one, then ticks its Java mods.
fn kiln_tick(game: &mut crate::game::Game<'_>) {
    use crate::game::kiln as m;
    if !game.native_on(m::ID) || !game.native_window() || game.native_player().is_none() {
        return;
    }
    let data_dir = game.native_data_dir(m::ID);
    let lines = m::tick(&data_dir, || game.native_setting(m::ID, "java_home"));
    for line in lines {
        game.native_chat(&line);
    }
}
