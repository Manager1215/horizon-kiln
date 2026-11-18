// The station's step after Horizon worked out an anvil's result (`prepare_anvil`): the game's AnvilMenu.createResult
// for the mods' mixins in it, with the inputs of the station's menu, which only `lan_stations` reads (D-202). What the
// mixins changed goes into the menu before it is sent to the player.
fn kiln_anvil_result(game: &mut crate::game::Game<'_>, gs: &mut crate::game::lan_stations::GuestStation) {
    use crate::game::kiln as m;
    let Some(uuid) = crate::game::kiln_horizon::anvil_prepared() else {
        return;
    };
    if !game.native_on(m::ID) || game.native_window() || gs.kind != crate::game::stations::Kind::Anvil {
        return;
    }
    let (left, right, result) = (gs.menu.slot(0).cloned(), gs.menu.slot(1).cloned(), gs.menu.slot(2).cloned());
    let cost = gs.menu.data.first().copied().unwrap_or(0);
    let changed =
        crate::game::kiln_horizon::anvil_result(game, uuid, [left.as_ref(), right.as_ref()], result.as_ref(), cost);
    if let Some((result, cost)) = changed {
        let env = game.menu_env();
        gs.menu.set_slot(&env, 2, result);
        if let Some(data) = gs.menu.data.first_mut() {
            *data = cost;
        }
    }
}
