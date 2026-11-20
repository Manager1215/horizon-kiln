// Kiln's bundle carries horizon-gaps' modules as copies (the game's build copies only `kiln/`): each copy must be the
// module's own source in gaps/, byte for byte (D-219, D-233).

use std::path::PathBuf;

fn root() -> PathBuf {
    PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("..")
}

#[test]
fn kilns_copies_of_the_gaps_modules_are_the_modules_own() {
    let files = [
        ("enchantments", "gap_enchantments.rs"),
        ("enchantments", "gap_enchantment_files.rs"),
        ("enchantments", "gap_enchantments_hook.rs"),
        ("enchantments", "gap_enchantment_files_hook.rs"),
    ];
    for (module, file) in files {
        let source = std::fs::read(root().join("gaps").join(module).join(file)).unwrap();
        let copy = std::fs::read(root().join("kiln/mixins/kiln").join(file)).unwrap();
        assert!(
            source == copy,
            "kiln/mixins/kiln/{file} differs from gaps/{module}/{file}: copy it again"
        );
    }
}
