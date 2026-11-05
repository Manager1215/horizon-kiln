// Before a server level's tick, with that level in place: its table of the block entities that tick follows the
// Java mods' blocks (horizon.rs).
fn kiln_level_tick(game: &mut crate::game::Game<'_>, dimension: &str) {
    use crate::game::kiln as m;
    if game.native_on(m::ID) {
        crate::game::kiln_horizon::level_tick(game, dimension);
    }
}
