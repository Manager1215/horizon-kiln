// A key going down while playing (no screen open): Kiln's Java mods hear of it with the next tick, for the
// keys they registered (vanilla's own mapping on that key still runs).
fn kiln_key(game: &mut crate::game::Game<'_>, key: &str, pressed: bool) {
    use crate::game::kiln as m;
    if pressed && game.native_on(m::ID) && game.native_window() && game.native_playing() {
        m::key(key);
    }
}
