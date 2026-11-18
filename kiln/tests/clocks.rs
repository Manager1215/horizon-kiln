// The world's clocks and structures in a Java mod's server command (#133, D-210), with real Java: Kiln's test mod
// (kiln/test-mods/clocks) sets the time of day as Saro's Easy Time does (the dimension type's default clock,
// `ServerClockManager.setTotalTicks`) and lists the structures at the player as WITS does
// (`StructureManager.startsForStructure`, the starts' boxes, the ids in the level's `Registries.STRUCTURE`). The game's
// side is played here: it answers `clock` and `structures` as horizon.rs does. Needs a JDK and the jars from
// `sh tools/build-java.sh`.
//
//   sh tools/build-java.sh && cargo test -p horizon-kiln-mod --test clocks -- --ignored --nocapture

use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;
use serde_json::json;

/// A game folder with Kiln, the libraries and the test mod; returns it and the mod's data folder inside it.
fn game_folder() -> (PathBuf, PathBuf) {
    let built = Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln");
    let jar = built.join("test/kiln-test-clocks.jar");
    assert!(jar.is_file(), "run sh tools/build-java.sh first");
    let game = std::env::temp_dir().join(format!("kiln-clocks-{}", std::process::id()));
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
    std::fs::copy(&jar, kiln.join("mods/kiln-test-clocks.jar")).unwrap();
    let data = game.join("horizon-mods/.horizon-mod-data/kiln");
    std::fs::create_dir_all(&data).unwrap();
    (game, data)
}

/// The player Kiln in `dimension`, at 0.5 65 0.5: in the chunk 0 0.
fn player(dimension: &str) -> m::PlayerSnapshot {
    m::PlayerSnapshot {
        uuid: 0x00c0_ffee,
        name: "Kiln".into(),
        dimension: dimension.into(),
        position: [0.5, 65.0, 0.5],
        game_mode: 0,
        health: 20.0,
        attributes: vec![("minecraft:max_health".into(), 20.0, 20.0)],
        ..Default::default()
    }
}

/// A command's answers, each as the game gets it.
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

/// The clocks a command set.
fn clocks(outcome: &m::Outcome) -> Vec<(String, i64)> {
    outcome
        .effects
        .iter()
        .filter_map(|e| match e {
            m::Effect::Clock { clock, ticks } => Some((clock.clone(), *ticks)),
            _ => None,
        })
        .collect()
}

#[test]
#[ignore = "needs a JDK and sh tools/build-java.sh"]
fn a_command_sets_the_time_through_the_clock_and_finds_the_structures_at_the_player() {
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

    // The game: the overworld's clock at 13000 (night), and in the chunk 0 0 a village whose box holds the player and
    // a mineshaft whose box doesn't (it ends at y 40).
    let mut asked: Vec<Vec<String>> = Vec::new();
    let mut structures = "minecraft:village_plains 0 0 -20 60 -20 30 80 30,minecraft:mineshaft -1 0 -30 10 -5 -10 40 5";
    let run = |line: &str, dimension: &str, structures: &str, asked: &mut Vec<Vec<String>>| {
        let mut game = |what: &[String]| {
            asked.push(what.to_vec());
            match what {
                [kind, clock] if kind == "clock" && clock == "minecraft:overworld" => {
                    "13000 1 false".to_owned()
                }
                [kind, ..] if kind == "structures" => structures.to_owned(),
                _ => String::new(),
            }
        };
        m::server_command(&world, 12, 2, line, &player(dimension), &mut game)
            .expect("the command answers")
    };

    // The time of day is the overworld clock's, as the game has it; the level's type is the game's entry.
    let got = run(
        "clocktest get",
        "minecraft:overworld",
        structures,
        &mut asked,
    );
    assert_eq!(got.result, m::UseResult::Success, "{got:?}");
    assert_eq!(
        feedback(&got),
        [json!({"text": "minecraft:overworld 13000 minecraft:overworld"})]
    );
    assert!(
        asked.contains(&vec!["clock".to_owned(), "minecraft:overworld".to_owned()]),
        "{asked:?}"
    );

    // Set, as Saro's /day does: the game sets it once the command is done, and the command reads back what it set.
    let set = run(
        "clocktest set 1000",
        "minecraft:overworld",
        structures,
        &mut asked,
    );
    assert_eq!(set.result, m::UseResult::Success, "{set:?}");
    assert_eq!(clocks(&set), [("minecraft:overworld".to_owned(), 1000)]);
    assert_eq!(feedback(&set), [json!({"text": "Set 1000 1000"})]);

    // The next call asks the game again: the clock has gone on.
    let again = run(
        "clocktest get",
        "minecraft:overworld",
        structures,
        &mut asked,
    );
    assert_eq!(
        feedback(&again),
        [json!({"text": "minecraft:overworld 13000 minecraft:overworld"})]
    );

    // The Nether's type has no clock, as the game's: its time is 0, and a command that takes its clock fails as in
    // vanilla (Saro's /day there: "An unexpected error occurred"), setting nothing.
    let nether = run(
        "clocktest get",
        "minecraft:the_nether",
        structures,
        &mut asked,
    );
    assert_eq!(
        feedback(&nether),
        [json!({"text": "none 0 minecraft:the_nether"})]
    );
    let failed = run(
        "clocktest set 5",
        "minecraft:the_nether",
        structures,
        &mut asked,
    );
    assert_eq!(failed.result, m::UseResult::Fail, "{failed:?}");
    assert!(clocks(&failed).is_empty(), "{failed:?}");
    // The End's clock is the End's own: Horizon runs none, so its time is 0.
    let end = run("clocktest get", "minecraft:the_end", structures, &mut asked);
    assert_eq!(
        feedback(&end),
        [json!({"text": "minecraft:the_end 0 minecraft:the_end"})]
    );

    // The structures at the player, as WITS lists them: the chunk's starts, of which only the village's box holds the
    // player; its id from the level's registry, green in brackets, copied by a click, put in the chat box by a
    // shift-click, as vanilla's `ComponentUtils.copyOnClickText`.
    asked.clear();
    let here = run(
        "clocktest structures",
        "minecraft:overworld",
        structures,
        &mut asked,
    );
    assert_eq!(here.result, m::UseResult::Success, "{here:?}");
    assert!(
        asked.contains(
            &["structures", "minecraft:overworld", "0", "0"]
                .map(str::to_owned)
                .to_vec()
        ),
        "{asked:?}"
    );
    assert_eq!(
        feedback(&here),
        [json!({"text": "Here:", "extra": [
            {"text": " "},
            {"translate": "chat.square_brackets", "with": [{
                "text": "minecraft:village_plains",
                "color": "green",
                "hover_event": {"action": "show_text", "value": {"translate": "chat.copy.click"}},
                "click_event": {"action": "copy_to_clipboard", "value": "minecraft:village_plains"},
                "insertion": "minecraft:village_plains",
            }]},
        ]})]
    );
    // None where the game has none (a chunk it hasn't loaded answers so too).
    structures = "";
    let none = run(
        "clocktest structures",
        "minecraft:overworld",
        structures,
        &mut asked,
    );
    assert_eq!(feedback(&none), [json!({"text": "None"})]);
    let _ = std::fs::remove_dir_all(&game);
}
