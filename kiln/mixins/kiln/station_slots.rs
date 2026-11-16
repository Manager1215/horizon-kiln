// A station's own slots as the hub's menu holds them (an anvil's inputs and result, a smithing table's), for a recipe
// viewer's transfer into it (D-169): kept for Kiln, which asked. Placed in `lan_stations`, it reads the menu there.
fn kiln_station_slots(gs: &crate::game::lan_stations::GuestStation) {
    let own = gs.menu.slot_count().saturating_sub(36);
    crate::game::kiln_horizon::station_slots((0..own).map(|s| gs.menu.slot(s).cloned()).collect());
}
