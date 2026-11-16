// The integrated server asks its mods for their commands: the Java mods' server commands (horizon.rs, D-107).
fn kiln_register_commands(
    game: &mut crate::game::Game<'_>,
    commands: &mut Vec<crate::mod_api::NativeCommand>,
) {
    use crate::game::kiln as m;
    if game.native_on(m::ID) {
        crate::game::kiln_horizon::register_commands(game, commands);
    }
}
