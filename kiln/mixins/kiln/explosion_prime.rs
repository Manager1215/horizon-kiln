// An explosion begins on the integrated server: its radius is kept for the blocks it breaks (Kiln's Mixin bridge,
// D-203; horizon.rs).
fn kiln_explosion_prime(game: &mut crate::game::Game<'_>, center: [f64; 3], radius: f32) {
    use crate::game::kiln as m;
    if game.native_on(m::ID) && !game.native_window() {
        crate::game::kiln_horizon::explosion_prime(center, radius);
    }
}
