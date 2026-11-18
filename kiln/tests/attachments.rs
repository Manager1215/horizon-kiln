// Fabric's data attachments with real Java (D-161): Kiln's test mod (kiln/test-mods/attachments) counts each level
// tick in a level's attachment kept with the world and in one kept only while the world is open, a player's visits
// (gone when they die) and keepsakes (kept through death). Kiln's levels and players are new objects each call: the
// values live on by the level's dimension and the player's UUID, and the persistent ones are in the world's files
// (`<world>/kiln/data/fabric/attachments/...`), read again when the world opens again. Needs a JDK and the jars from
// `sh tools/build-java.sh`.
//
//   sh tools/build-java.sh && cargo test -p horizon-kiln-mod --test attachments -- --ignored --nocapture

use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;
use serde_json::{Value, json};

fn game_folder() -> (PathBuf, PathBuf) {
    let built = Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln");
    let jar = built.join("test/kiln-test-attachments.jar");
    assert!(jar.is_file(), "run sh tools/build-java.sh first");
    let game = std::env::temp_dir().join(format!("kiln-attachments-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&game);
    let kiln = game.join("kiln");
    std::fs::create_dir_all(kiln.join("mods")).unwrap();
    std::fs::create_dir_all(kiln.join("lib")).unwrap();
    std::fs::copy(
        built.join("kiln-runtime.jar"),
        kiln.join("kiln-runtime.jar"),
    )
    .unwrap();
    for lib in m::jars(&built.join("lib")) {
        std::fs::copy(&lib, kiln.join("lib").join(lib.file_name().unwrap())).unwrap();
    }
    std::fs::copy(&jar, kiln.join("mods/kiln-test-attachments.jar")).unwrap();
    let data = game.join("horizon-mods/.horizon-mod-data/kiln");
    std::fs::create_dir_all(&data).unwrap();
    (game, data)
}

fn player(health: f32) -> m::PlayerSnapshot {
    m::PlayerSnapshot {
        uuid: 0x00c0_ffee,
        name: "Kiln".into(),
        dimension: "minecraft:overworld".into(),
        position: [0.5, 65.0, 0.5],
        game_mode: 0,
        health,
        attributes: vec![("minecraft:max_health".into(), 20.0, 20.0)],
        ..Default::default()
    }
}

fn answer(what: &[String]) -> String {
    match what[0].as_str() {
        "players" => m::players_answer(&[], Some(&player(20.0)), 10),
        "loaded" => "true".to_owned(),
        _ => String::new(),
    }
}

/// The JSON Kiln saved under `<world>/kiln/data/fabric/attachments/<path>.json`.
fn saved(world: &Path, path: &str) -> Value {
    let file = world
        .join("kiln/data/fabric/attachments")
        .join(format!("{path}.json"));
    let text = std::fs::read_to_string(&file).unwrap_or_else(|e| panic!("{}: {e}", file.display()));
    serde_json::from_str(&text).unwrap()
}

/// `n` level ticks the mod's code ran in. Kiln skips a second of tick events after one Java answered late (a cold
/// JVM): those are tried again.
fn level_ticks(world: &Path, game_time: &mut u64, n: usize) {
    let deadline = Instant::now() + Duration::from_secs(20);
    let mut ran = 0;
    while ran < n {
        assert!(Instant::now() < deadline, "{ran} of {n} level ticks ran");
        *game_time += 1;
        match m::server_tick(
            world,
            *game_time,
            m::TickPhase::LevelStart,
            "minecraft:overworld",
            &mut answer,
        ) {
            Some(_) => ran += 1,
            None => std::thread::sleep(Duration::from_millis(100)),
        }
    }
}

#[test]
#[ignore = "needs a JDK and sh tools/build-java.sh"]
fn a_mods_attachments_live_on_between_calls_and_with_the_world() {
    let (game, data) = game_folder();
    let snapshot = m::Snapshot {
        connected: true,
        in_world: true,
        game_time: 10,
        dimension: "minecraft:overworld".into(),
        ..m::Snapshot::default()
    };
    let deadline = Instant::now() + Duration::from_secs(20);
    while !m::hears("level_tick_start") || !m::hears("connect") {
        assert!(
            Instant::now() < deadline,
            "the mod's events were never announced"
        );
        m::tick(&data, || None, || vec![6; 128], snapshot.clone());
        std::thread::sleep(m::TICK);
    }
    let world = game.join("world");
    let mut game_time = 100;

    // Twenty level ticks, each a call with a level of its own: both counts went on from one call to the next, and
    // the one kept with the world is in its file at each call's end; the other one isn't.
    level_ticks(&world, &mut game_time, 20);
    let level = saved(&world, "level/minecraft/overworld");
    assert_eq!(
        level,
        json!({"kiln_test_attachments:ticks": 20, "kiln_test_attachments:report": [20, 20]}),
        "{level}"
    );

    // Another world opens, then this one again (the game's world closed and opened): the persistent count is read
    // from the file and goes on; the other one starts again.
    level_ticks(&game.join("other-world"), &mut game_time, 1);
    assert_eq!(
        saved(&game.join("other-world"), "level/minecraft/overworld")["kiln_test_attachments:ticks"],
        1
    );
    level_ticks(&world, &mut game_time, 5);
    let level = saved(&world, "level/minecraft/overworld");
    assert_eq!(
        level["kiln_test_attachments:report"],
        json!([25, 5]),
        "{level}"
    );

    // A player connects twice: their visits and keepsakes are kept by their UUID, in a file of the player's.
    for _ in 0..2 {
        game_time += 1;
        m::player_connected(&world, game_time, &player(20.0), &mut answer)
            .expect("the mod hears JOIN");
    }
    let players = world.join("kiln/data/fabric/attachments/player");
    let files: Vec<_> = std::fs::read_dir(&players)
        .unwrap()
        .map(|e| e.unwrap().path())
        .collect();
    assert_eq!(files.len(), 1, "{files:?}");
    let uuid = files[0].file_stem().unwrap().to_str().unwrap().to_owned();
    let saved_player = saved(&world, &format!("player/{uuid}"));
    assert_eq!(
        saved_player,
        json!({"kiln_test_attachments:visits": 2, "kiln_test_attachments:keepsakes": 2}),
        "{saved_player}"
    );

    // They die and respawn: the new player keeps only the keepsakes (copyOnDeath), and the old one still had both
    // during the call (Fabric's COPY_FROM and AFTER_RESPAWN read it).
    game_time += 1;
    m::player_respawned(
        &world,
        game_time,
        false,
        &player(0.0),
        &player(20.0),
        &mut answer,
    )
    .expect("the mod hears a respawn");
    let saved_player = saved(&world, &format!("player/{uuid}"));
    assert_eq!(
        saved_player,
        json!({"kiln_test_attachments:keepsakes": 2, "kiln_test_attachments:after_death": [0, 2, 2]}),
        "{saved_player}"
    );
}
