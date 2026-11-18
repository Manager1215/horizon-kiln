// A player picks up an experience orb on the integrated server: the game's ExperienceOrb.playerTouch, for the mods'
// mixins in it (Kiln's Mixin bridge, D-200): one that cancels it leaves the orb (horizon.rs).
fn kiln_experience_orb_pickup(
    game: &mut crate::game::Game<'_>,
    player: u128,
    dimension: &str,
    orb: u32,
    value: u32,
    count: u32,
    canceled: &mut bool,
) {
    use crate::game::kiln as m;
    if game.native_on(m::ID) && !game.native_window() {
        crate::game::kiln_horizon::experience_orb_pickup(game, player, dimension, orb, value, count, canceled);
    }
}
