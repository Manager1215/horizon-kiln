// After a server level's tick, with that level in place: Fabric's END_LEVEL_TICK for the Java mods that hear it
// (horizon.rs).
fn kiln_level_tick_post(game: &mut crate::game::Game<'_>, dimension: &str) {
    use crate::game::kiln as m;
    if game.native_on(m::ID) {
        crate::game::kiln_horizon::level_tick_post(game, dimension);
    }
}
