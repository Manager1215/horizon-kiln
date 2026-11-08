// Kiln's row in Options > Mods, then one for each Java mod in kiln/mods, each with its own switch (`kiln_<mod id>`):
// a mod turned off is left out from the next world the player enters, as if its jar weren't there (runtime.rs). Each
// mod's page lists its keys as Java last told of them, which the player can bind there.
fn kiln_listed(game: &mut crate::game::Game<'_>, mods: &mut Vec<crate::game::native_hooks::NativeMod>) {
    use crate::game::kiln as m;
    mods.push(crate::game::native_hooks::NativeMod {
        id: m::ID.into(),
        title: format!("{} {}", m::NAME, m::VERSION),
        description: m::DESCRIPTION.into(),
        keys: Vec::new(),
    });
    m::set_off(m::off_ids(game.settings.mods.disabled.iter().map(String::as_str)));
    let kiln_dir = m::kiln_dir(&game.native_data_dir(m::ID));
    for (id, title, description, keys) in m::mod_rows(&kiln_dir) {
        mods.push(crate::game::native_hooks::NativeMod {
            id,
            title,
            description,
            keys,
        });
    }
}
