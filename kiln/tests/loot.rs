// A Java mod's changes to the game's loot tables, with real Java: Kiln's proof loot (kiln/proof-mods/loot) puts a
// nether star in every simple dungeon chest, run ahead of a world as Horizon's world loading asks Kiln (loot.rs).
// Needs a JDK and the jars from `sh tools/build-java.sh`.
//
//   sh tools/build-java.sh && cargo test -p horizon-kiln-mod --test loot -- --ignored --nocapture

use std::path::Path;

use horizon_kiln_mod::game::{kiln as m, kiln_loot as loot};

#[test]
#[ignore = "needs a JDK and sh tools/build-java.sh"]
fn a_mods_loot_changes_run_before_the_world() {
    let built = Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln");
    let proof = built.join("proof/kiln-proof-loot.jar");
    assert!(proof.is_file(), "run sh tools/build-java.sh first");
    let kiln = std::env::temp_dir().join(format!("kiln-loot-run-{}", std::process::id())).join("kiln");
    let _ = std::fs::remove_dir_all(&kiln);
    std::fs::create_dir_all(kiln.join("mods")).unwrap();
    std::fs::create_dir_all(kiln.join("lib")).unwrap();
    std::fs::copy(built.join("kiln-runtime.jar"), kiln.join("kiln-runtime.jar")).unwrap();
    for jar in m::jars(&built.join("lib")) {
        std::fs::copy(&jar, kiln.join("lib").join(jar.file_name().unwrap())).unwrap();
    }
    std::fs::copy(&proof, kiln.join("mods/kiln-proof-loot.jar")).unwrap();

    loot::prepare(&kiln);
    let generated = kiln.join("generated");
    let file: serde_json::Value =
        serde_json::from_slice(&std::fs::read(generated.join(loot::FILE)).expect("no loot.json")).unwrap();
    println!("{file:#}");
    // Only the table the mod changed, with its one pool: a nether star.
    let tables = file["tables"].as_object().unwrap();
    assert_eq!(tables.keys().collect::<Vec<_>>(), ["minecraft:chests/simple_dungeon"]);
    let pools = tables["minecraft:chests/simple_dungeon"].as_array().unwrap();
    assert_eq!(pools.len(), 1, "{pools:?}");
    assert_eq!(pools[0]["rolls"], 1);
    assert_eq!(pools[0]["entries"][0]["name"], "minecraft:nether_star");
    assert!(generated.join(loot::MARKER).is_file());
}
