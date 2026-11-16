// The view's field of view each frame: scaled by what the mods' mixins in Camera.calculateFov make it give over the
// option (Kiln's Mixin bridge, D-105: a zoom mod's zoom). As it was when no mixin is there.
fn kiln_compute_fov(game: &crate::game::Game<'_>, degrees: &mut f32) {
    use crate::game::kiln as m;
    if game.native_on(m::ID) && game.native_window() {
        *degrees = m::view_fov(*degrees, game.settings.fov.round());
    }
}
