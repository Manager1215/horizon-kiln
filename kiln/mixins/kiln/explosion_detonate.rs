// An explosion on the integrated server is about to break its blocks: the game's Block.getExplosionResistance of each,
// for the mods' mixins in it; the ones they made too strong for it are spared (Kiln's Mixin bridge, D-203; horizon.rs).
fn kiln_explosion_detonate(
    game: &mut crate::game::Game<'_>,
    dimension: &str,
    center: [f64; 3],
    blocks: &mut Vec<[i32; 3]>,
) {
    use crate::game::kiln as m;
    if game.native_on(m::ID) && !game.native_window() {
        crate::game::kiln_horizon::explosion_detonate(game, dimension, center, blocks);
    }
}
