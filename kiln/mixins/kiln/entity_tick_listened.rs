// Whether Horizon tells each entity's tick: it does while a mod's mixin is in LivingEntity.tick (Kiln's Mixin bridge,
// D-145); else the game keeps no list of them, as without mods.
fn kiln_entity_tick_listened(game: &mut crate::game::Game<'_>, listened: &mut bool) {
    use crate::game::kiln as m;
    if game.native_on(m::ID) && !game.native_window() && m::bridged("entity_tick_pre") {
        *listened = true;
    }
}
