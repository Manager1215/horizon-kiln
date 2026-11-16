// A mob is about to tick on the integrated server: kept for one call of the game's LivingEntity.tick for each of the
// level's mobs after its tick, for the mods' mixins in it (Kiln's Mixin bridge, D-145; horizon.rs).
fn kiln_entity_tick_pre(game: &mut crate::game::Game<'_>, dimension: &str, entity: u32, kind: &str) {
    use crate::game::kiln as m;
    if game.native_on(m::ID) && !game.native_window() {
        crate::game::kiln_horizon::entity_tick_pre(dimension, entity, kind);
    }
}
