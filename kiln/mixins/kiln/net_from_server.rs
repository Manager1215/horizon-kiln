// A message from the server on a channel for mods reaches the window: one on Kiln's goes to the Java mods' client
// receivers (horizon.rs).
fn kiln_net_from_server(game: &mut crate::game::Game<'_>, channel: &str, data: &[u8]) {
    use crate::game::kiln as m;
    if game.native_on(m::ID) {
        crate::game::kiln_horizon::net_from_server(channel, data);
    }
}
