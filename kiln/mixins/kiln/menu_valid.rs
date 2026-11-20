// A crafting table a mod's command opened (D-216) has no block: at Kiln's place for it (`MENU_Y`, where no block is),
// `CraftingMenu.stillValid` answers yes, as a menu made with no level access does; any other table is the game's to
// answer.
fn kiln_menu_valid(pos: [i32; 3]) -> std::ops::ControlFlow<bool> {
    if pos[1] == crate::game::kiln_horizon::MENU_Y {
        std::ops::ControlFlow::Break(true)
    } else {
        std::ops::ControlFlow::Continue(())
    }
}
