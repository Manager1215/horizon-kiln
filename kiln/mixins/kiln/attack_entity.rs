// A player attacks an entity on the integrated server: the game's Player.attack, for the mods' mixins in it (Kiln's
// Mixin bridge, D-106): one that cancels it cancels the blow (horizon.rs).
fn kiln_attack_entity(game: &mut crate::game::Game<'_>, entity: i32, kind: &str, canceled: &mut bool) {
    use crate::game::kiln as m;
    if game.native_on(m::ID) && !game.native_window() {
        crate::game::kiln_horizon::attack_entity(game, entity, kind, canceled);
    }
}
