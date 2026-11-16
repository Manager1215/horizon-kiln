// An item's tooltip on the window: the lines the Java mods' code gives it (its item's appendHoverText, Fabric's
// ItemTooltipCallback, the mods' mixins in ItemStack.getTooltipLines), as Java last made them (Kiln's Mixin bridge,
// D-145; horizon.rs).
fn kiln_tooltip(
    game: &crate::game::Game<'_>,
    stack: &horizon_client::items::ItemStack,
    lines: &mut Vec<(horizon_client::text::Text, u32)>,
) {
    use crate::game::kiln as m;
    if game.native_on(m::ID) && game.native_window() && m::bridged("tooltip") {
        crate::game::kiln_horizon::tooltip(stack, lines);
    }
}
