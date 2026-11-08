// A Java mod's block that the game's registry report lists too, with real Java. With content mods on, Horizon's
// prepared resources (its mod-content cache) list each content mod's blocks in `registries.json`, a Java mod's among
// them (Sponge Succ's lava sponge). Kiln's registry takes only the game's own from there, and a block's states are its
// own: the mod's code runs for its block, here the test pad's `onPlace` (kiln/test-mods/blocks), a block with no
// properties, whose one state is written as its id alone (D-103). Needs a JDK and the jars from
// `sh tools/build-java.sh`.
//
//   sh tools/build-java.sh && cargo test -p horizon-kiln-mod --test content_blocks -- --ignored --nocapture

use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;

const PAD: &str = "kiln_test_blocks:pad";

/// A game folder with Kiln, the libraries and the test blocks, and prepared resources whose registry report lists
/// the pad beside the game's own blocks, as Horizon's mod-content cache does; returns the game folder, the mod's data
/// folder inside it and the resources.
fn game_folder() -> (PathBuf, PathBuf, PathBuf) {
    let built = Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln");
    let jar = built.join("test/kiln-test-blocks.jar");
    assert!(jar.is_file(), "run sh tools/build-java.sh first");
    let game = std::env::temp_dir().join(format!("kiln-content-blocks-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&game);
    let kiln = game.join("kiln");
    std::fs::create_dir_all(kiln.join("mods")).unwrap();
    std::fs::create_dir_all(kiln.join("lib")).unwrap();
    std::fs::copy(built.join("kiln-runtime.jar"), kiln.join("kiln-runtime.jar")).unwrap();
    for jar in m::jars(&built.join("lib")) {
        std::fs::copy(&jar, kiln.join("lib").join(jar.file_name().unwrap())).unwrap();
    }
    std::fs::copy(&jar, kiln.join("mods/kiln-test-blocks.jar")).unwrap();
    let data = game.join("horizon-mods/.horizon-mod-data/kiln");
    std::fs::create_dir_all(&data).unwrap();
    let resources = game.join("mod-content");
    std::fs::create_dir_all(&resources).unwrap();
    let mut entries = serde_json::Map::new();
    for (n, id) in ["minecraft:air", "minecraft:stone", "minecraft:glowstone", PAD].iter().enumerate() {
        entries.insert((*id).to_owned(), serde_json::json!({ "protocol_id": n }));
    }
    let report = serde_json::json!({ "minecraft:block": { "entries": entries } });
    std::fs::write(resources.join("registries.json"), report.to_string()).unwrap();
    (game, data, resources)
}

#[test]
#[ignore = "needs a JDK and sh tools/build-java.sh"]
fn a_mods_block_the_registry_report_lists_runs_its_code() {
    let (game, data, resources) = game_folder();
    horizon_kiln_mod::game::kiln_jvm::set_resources(&resources);
    let snapshot = m::Snapshot {
        connected: true,
        in_world: true,
        game_time: 10,
        dimension: "minecraft:overworld".into(),
        ..m::Snapshot::default()
    };
    let deadline = Instant::now() + Duration::from_secs(20);
    while !m::block_does(PAD, "placed") {
        assert!(Instant::now() < deadline, "the pad was never announced");
        m::tick(&data, || None, || vec![6; 128], snapshot.clone());
        std::thread::sleep(m::TICK);
    }
    let player = m::PlayerSnapshot {
        uuid: 0x00c0_ffee,
        name: "Kiln".into(),
        dimension: "minecraft:overworld".into(),
        position: [0.5, 65.0, 2.5],
        health: 20.0,
        ..Default::default()
    };
    let at = [0, 64, 0];
    let mut stone = |what: &[String]| match what[0].as_str() {
        "block" => "minecraft:stone".to_owned(),
        _ => String::new(),
    };
    // Placed, the pad's own code turns the stone over it into glowstone.
    let placed = m::placed(&game.join("world"), 100, at, PAD, PAD, 1, &player, &mut stone).expect("the pad hears it");
    println!("placed: {placed:?}");
    assert_eq!(
        placed.effects,
        [m::Effect::Block {
            pos: [0, 65, 0],
            flags: 3,
            state: "minecraft:glowstone".into()
        }]
    );
}
