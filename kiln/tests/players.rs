// A Java mod's player events and an item's own use on Kiln's server side, with real Java: Kiln's test mod
// (kiln/test-mods/player) heals with its charm, welcomes a player who comes into a level and speaks after a respawn,
// as Horizon's server asks Kiln. Needs a JDK and the jars from `sh tools/build-java.sh`.
//
//   sh tools/build-java.sh && cargo test -p horizon-kiln-mod --test players -- --ignored --nocapture

use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;
use serde_json::json;

const CHARM: &str = "kiln_test_player:charm";
const PEBBLE: &str = "kiln_test_player:pebble";

/// A game folder with Kiln, the libraries and the test mod; returns it and the mod's data folder inside it.
fn game_folder() -> (PathBuf, PathBuf) {
    let built = Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln");
    let jar = built.join("test/kiln-test-player.jar");
    assert!(jar.is_file(), "run sh tools/build-java.sh first");
    let game = std::env::temp_dir().join(format!("kiln-players-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&game);
    let kiln = game.join("kiln");
    std::fs::create_dir_all(kiln.join("mods")).unwrap();
    std::fs::create_dir_all(kiln.join("lib")).unwrap();
    std::fs::copy(built.join("kiln-runtime.jar"), kiln.join("kiln-runtime.jar")).unwrap();
    for lib in m::jars(&built.join("lib")) {
        std::fs::copy(&lib, kiln.join("lib").join(lib.file_name().unwrap())).unwrap();
    }
    std::fs::copy(&jar, kiln.join("mods/kiln-test-player.jar")).unwrap();
    let data = game.join("horizon-mods/.horizon-mod-data/kiln");
    std::fs::create_dir_all(&data).unwrap();
    (game, data)
}

/// The player, at `health`, holding `main` (an item and how many).
fn player(health: f32, main: Option<(&str, u32)>) -> m::PlayerSnapshot {
    m::PlayerSnapshot {
        uuid: 0x00c0_ffee,
        name: "Kiln".into(),
        dimension: "minecraft:overworld".into(),
        position: [0.5, 65.0, 0.5],
        game_mode: 0,
        health,
        main: main.map(|(item, count)| (item.to_owned(), count)),
        attributes: vec![("minecraft:max_health".into(), 20.0, 20.0)],
        main_stack: main
            .map(|(item, count)| format!(r#"{{count:{count},id:"{item}"}}"#))
            .unwrap_or_default(),
        ..Default::default()
    }
}

#[test]
#[ignore = "needs a JDK and sh tools/build-java.sh"]
fn a_mods_player_events_and_its_items_use_answer() {
    let (game, data) = game_folder();
    let snapshot = m::Snapshot {
        connected: true,
        in_world: true,
        game_time: 10,
        dimension: "minecraft:overworld".into(),
        ..m::Snapshot::default()
    };
    // Kiln starts at the window's first tick in a world, ticking at the game's pace. Once the mod's entrypoint ran,
    // Java says which items have a use of their own, then which server events the mods hear.
    let deadline = Instant::now() + Duration::from_secs(20);
    while !m::hears("join") {
        assert!(Instant::now() < deadline, "the mod's events were never announced");
        m::tick(&data, || None, || vec![6; 128], snapshot.clone());
        std::thread::sleep(m::TICK);
    }
    assert!(m::hears("respawn"));
    assert!(!m::hears("use_item"), "no mod hears every item's use");
    assert!(m::has_use(CHARM), "the charm's use is its own");
    assert!(!m::has_use(PEBBLE), "the pebble has the game's use");

    let world = game.join("world");
    let mut nothing = |_: &[String]| String::new();
    // The charm heals by 4 and is used up, one of three.
    let used = m::use_item(&world, 100, CHARM, m::Hand::Main, &player(10.0, Some((CHARM, 3))), &mut nothing)
        .expect("the charm's use answers");
    assert_eq!(used.result, m::UseResult::Success);
    assert_eq!(
        used.effects,
        [
            m::Effect::Health(14.0),
            m::Effect::Hand {
                hand: m::Hand::Main,
                item: CHARM.into(),
                count: 2
            }
        ]
    );
    // The pebble's use is the game's own: Java isn't asked.
    let pebble = m::use_item(&world, 101, PEBBLE, m::Hand::Main, &player(10.0, Some((PEBBLE, 1))), &mut nothing);
    assert_eq!(pebble, None);

    // A player comes into the world: the mod welcomes them in chat.
    let joined = m::player_joined(&world, 102, &player(20.0, None), &mut nothing).expect("the mod hears a join");
    assert_eq!(joined.effects, [m::Effect::Tell(json!({"text": "Welcome, Kiln"}))]);
    // Respawned after dying: the new player comes into the level (welcomed again), then the mod speaks over the
    // hotbar; what it would do to the old player goes nowhere.
    let respawned = m::player_respawned(&world, 103, false, &player(0.0, None), &player(20.0, None), &mut nothing)
        .expect("the mod hears a respawn");
    assert_eq!(
        respawned.effects,
        [
            m::Effect::Tell(json!({"text": "Welcome, Kiln"})),
            m::Effect::Overlay(json!({"text": "Back from the dead"}))
        ]
    );
    // Back from the End, alive.
    let back = m::player_respawned(&world, 104, true, &player(20.0, None), &player(20.0, None), &mut nothing)
        .expect("the mod hears a respawn");
    assert!(back.effects.contains(&m::Effect::Overlay(json!({"text": "Back from the End"}))), "{back:?}");
    let _ = std::fs::remove_dir_all(&game);
}
