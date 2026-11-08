// A player's message on a channel for mods reaches the integrated server: one on Kiln's waits for the player's next
// tick, for the Java mods' server receivers (horizon.rs).
fn kiln_net_from_client(game: &mut crate::game::Game<'_>, player: u128, channel: &str, data: &[u8]) {
    use crate::game::kiln as m;
    if game.native_on(m::ID) {
        crate::game::kiln_horizon::net_from_client(player, channel, data);
    }
}
