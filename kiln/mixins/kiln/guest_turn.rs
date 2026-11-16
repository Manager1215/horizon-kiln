// The end of a guest's turn on the integrated server (the window's player is one), with the hub in hand: what the Java
// mods moved in the player's crafting grid is moved in the game and the client told (D-166).
fn kiln_guest_turn(game: &mut crate::game::Game<'_>, host: &mut crate::game::lan::LanHost, conn: u32) {
    crate::game::kiln_horizon::guest_turn(game, host, conn);
}
