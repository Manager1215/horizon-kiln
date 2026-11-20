// The server's levels and moving the player in a Java mod's server command (#133, D-231), with real Java: Kiln's test
// mod (kiln/test-mods/teleports) moves the player to a level the server names, as Simple Set Home's /home does
// (`MinecraftServer.getLevel`, `ServerPlayer.teleportTo`), moves them up in their own level (`Entity.teleportTo`) and
// lists the server's levels. The game's side is a `teleport` effect, which horizon.rs runs as the game's own `/tp`.
// Needs a JDK and the jars from `sh tools/build-java.sh`.
//
//   sh tools/build-java.sh && cargo test -p horizon-kiln-mod --test teleports -- --ignored --nocapture

use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;
use serde_json::json;

/// A game folder with Kiln, the libraries and the test mod; returns it and the mod's data folder inside it.
fn game_folder() -> (PathBuf, PathBuf) {
    let built = Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln");
    let jar = built.join("test/kiln-test-teleports.jar");
    assert!(jar.is_file(), "run sh tools/build-java.sh first");
    let game = std::env::temp_dir().join(format!("kiln-teleports-{}", std::process::id()));
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
    std::fs::copy(&jar, kiln.join("mods/kiln-test-teleports.jar")).unwrap();
    let data = game.join("horizon-mods/.horizon-mod-data/kiln");
    std::fs::create_dir_all(&data).unwrap();
    (game, data)
}

/// The player Kiln in the overworld at 0.5 65 0.5.
fn player() -> m::PlayerSnapshot {
    m::PlayerSnapshot {
        uuid: 0x00c0_ffee,
        name: "Kiln".into(),
        dimension: "minecraft:overworld".into(),
        position: [0.5, 65.0, 0.5],
        game_mode: 0,
        health: 20.0,
        attributes: vec![("minecraft:max_health".into(), 20.0, 20.0)],
        ..Default::default()
    }
}

fn feedback(outcome: &m::Outcome) -> Vec<serde_json::Value> {
    outcome
        .effects
        .iter()
        .filter_map(|e| match e {
            m::Effect::Feedback(v) => Some(v.clone()),
            _ => None,
        })
        .collect()
}

fn teleports(outcome: &m::Outcome) -> Vec<(String, [f64; 3], f32, f32)> {
    outcome
        .effects
        .iter()
        .filter_map(|e| match e {
            m::Effect::Teleport {
                dimension,
                pos,
                yaw,
                pitch,
            } => Some((dimension.clone(), *pos, *yaw, *pitch)),
            _ => None,
        })
        .collect()
}

#[test]
#[ignore = "needs a JDK and sh tools/build-java.sh"]
fn a_command_moves_the_player_to_a_level_the_server_names() {
    let (game, data) = game_folder();
    let snapshot = m::Snapshot {
        connected: true,
        in_world: true,
        game_time: 10,
        dimension: "minecraft:overworld".into(),
        ..m::Snapshot::default()
    };
    let world = game.join("world");
    let mut nothing = |_: &[String]| String::new();
    let deadline = Instant::now() + Duration::from_secs(20);
    while !m::server_commands_stale() {
        assert!(
            Instant::now() < deadline,
            "the mod's commands were never announced"
        );
        m::tick(&data, || None, || vec![6; 128], snapshot.clone());
        std::thread::sleep(m::TICK);
    }
    assert!(m::server_commands(&world, 11, &mut nothing).is_some_and(|u| !u.is_empty()));
    let run = |line: &str| {
        m::server_command(&world, 12, 2, line, &player(), &mut |_: &[String]| {
            String::new()
        })
        .expect("the command answers")
    };

    // The server's three levels, the overworld first, as Horizon opens them.
    let levels = run("tptest levels");
    assert_eq!(levels.result, m::UseResult::Success, "{levels:?}");
    assert_eq!(
        feedback(&levels),
        [
            json!({"text": "minecraft:overworld minecraft:the_nether minecraft:the_end minecraft:overworld"})
        ]
    );

    // Moved to the Nether: the game moves them once the command is done, and the mod reads the new place back.
    let nether = run("tptest go \"minecraft:the_nether\" 8.5 70 -3.5");
    assert_eq!(nether.result, m::UseResult::Success, "{nether:?}");
    assert_eq!(
        teleports(&nether),
        [(
            "minecraft:the_nether".to_owned(),
            [8.5, 70.0, -3.5],
            45.0,
            10.0
        )]
    );
    assert_eq!(
        feedback(&nether),
        [json!({"text": "minecraft:the_nether 8.5 70.0 -3.5 45.0 10.0"})]
    );

    // A level Horizon doesn't open is none, as the game's `getLevel` answers: nobody is moved.
    let unknown = run("tptest go \"kiln:nowhere\" 0 0 0");
    assert!(teleports(&unknown).is_empty(), "{unknown:?}");
    assert_eq!(
        feedback(&unknown),
        [json!({"text": "No level kiln:nowhere"})]
    );

    // Up in their own level, facing as they do.
    let up = run("tptest up");
    assert_eq!(
        teleports(&up),
        [("minecraft:overworld".to_owned(), [0.5, 75.0, 0.5], 0.0, 0.0)]
    );
    let _ = std::fs::remove_dir_all(&game);
}
