// A player right clicks a block, on the integrated server: a Java mod's block does what its code says (horizon.rs).
fn kiln_right_click_block(
    game: &mut crate::game::Game<'_>,
    pos: [i32; 3],
    face: [i32; 3],
    canceled: &mut bool,
) {
    use crate::game::kiln as m;
    if game.native_on(m::ID) {
        crate::game::kiln_horizon::right_click_block(game, pos, face, canceled);
    }
}
