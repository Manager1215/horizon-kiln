// A player's blow killed a mob on the integrated server: the game's LivingEntity.die, for the mods' mixins in it
// (Kiln's Mixin bridge, D-106; horizon.rs).
fn kiln_mob_killed(game: &mut crate::game::Game<'_>, entity: u32, kind: &str) {
    use crate::game::kiln as m;
    if game.native_on(m::ID) && !game.native_window() {
        crate::game::kiln_horizon::mob_killed(game, entity, kind);
    }
}
