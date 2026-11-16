// The attack (0), use (1) or pick block (2) key acts on what the crosshair points at, for a hand: the game's
// Minecraft.startAttack or startUseItem, for the mods' mixins in them (Kiln's Mixin bridge, D-102).
fn kiln_interaction_key(
    game: &mut crate::game::Game<'_>,
    button: i32,
    key_mapping: &str,
    hand: horizon_client::first_person::Hand,
) {
    use crate::game::kiln as m;
    if game.native_on(m::ID) && game.native_window() && m::bridged("interaction_key") {
        let hand = match hand {
            horizon_client::first_person::Hand::Main => "main",
            horizon_client::first_person::Hand::Off => "off",
        };
        m::hook("interaction_key", &[button.to_string(), key_mapping.to_owned(), hand.to_owned()]);
    }
}
