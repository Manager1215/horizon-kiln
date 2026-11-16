// Before one of vanilla's HUD layers is drawn: hidden when the mods' mixins or HUD elements kept its method in the
// game's Hud from drawing (a mixin cancelling Hud.extractCrosshair at its head, an element replacing the hotbar),
// as Java's last HUD said (Kiln's Mixin bridge, D-145).
fn kiln_gui_layer_pre(game: &crate::game::Game<'_>, layer: &str, canceled: &mut bool) {
    use crate::game::kiln as m;
    if !*canceled && game.native_on(m::ID) && game.native_window() && m::hud_hidden(layer) {
        *canceled = true;
    }
}
