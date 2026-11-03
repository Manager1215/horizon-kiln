// A player left the integrated server: Kiln forgets the level it last told the Java mods of (horizon.rs).
fn kiln_player_logged_out(player: u128) {
    crate::game::kiln_horizon::player_logged_out(player);
}
