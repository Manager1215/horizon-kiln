// A Java mod's client command that fails, with real Java, as the game drives it: Kiln's test mod's `/kiln_test_count`
// (kiln/test-mods/command-errors) throws errors made of components, as mods' commands do (in the game a component is
// brigadier's `Message`). The player reads the mod's own error, in red, with the colour the mod gave it; a typo says
// where; a count that works says so. Needs a JDK and the jars from `sh tools/build-java.sh`.
//
//   sh tools/build-java.sh && cargo test -p horizon-kiln-mod --test command_errors -- --ignored --nocapture

use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;
use serde_json::{Value, json};

/// A game folder with Kiln's runtime, the libraries and the test mod; the mod's data folder in it.
fn game_folder() -> (PathBuf, PathBuf) {
    let built = Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln");
    let jar = built.join("test/kiln-test-command-errors.jar");
    assert!(jar.is_file(), "run sh tools/build-java.sh first");
    let game = std::env::temp_dir().join(format!("kiln-command-errors-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&game);
    let kiln = game.join("kiln");
    std::fs::create_dir_all(kiln.join("mods")).unwrap();
    std::fs::create_dir_all(kiln.join("lib")).unwrap();
    std::fs::copy(built.join("kiln-runtime.jar"), kiln.join("kiln-runtime.jar")).unwrap();
    for lib in m::jars(&built.join("lib")) {
        std::fs::copy(&lib, kiln.join("lib").join(lib.file_name().unwrap())).unwrap();
    }
    std::fs::copy(&jar, kiln.join("mods/kiln-test-command-errors.jar")).unwrap();
    let data = game.join("horizon-mods/.horizon-mod-data/kiln");
    std::fs::create_dir_all(&data).unwrap();
    (game, data)
}

/// The window's game in a world, its player in it: the connection's client commands are made then.
fn in_world() -> m::Snapshot {
    m::Snapshot {
        connected: true,
        in_world: true,
        game_time: 10,
        dimension: "minecraft:overworld".into(),
        player: Some(m::PlayerSnapshot {
            uuid: 0x00c0_ffee,
            name: "Kiln".into(),
            dimension: "minecraft:overworld".into(),
            position: [0.5, 70.0, 0.5],
            health: 20.0,
            ..Default::default()
        }),
        ..m::Snapshot::default()
    }
}

/// Ticks at the game's pace until `done`, for 10 seconds at most; the plain chat lines heard.
fn tick_until(data: &Path, done: impl Fn() -> bool) -> Vec<String> {
    let deadline = Instant::now() + Duration::from_secs(10);
    let mut chat = Vec::new();
    while !done() {
        assert!(Instant::now() < deadline, "no answer; chat: {chat:?}");
        chat.extend(m::tick(data, || None, || vec![6; 128], in_world()));
        std::thread::sleep(m::TICK);
    }
    chat
}

/// Runs the player's `command` through the mod; the chat it makes: plain lines, and styled ones as vanilla's JSON.
fn run(data: &Path, command: &str) -> (Vec<String>, Vec<Value>) {
    assert!(m::client_command(command), "/{command} isn't the mod's");
    let (mut plain, mut styled) = (Vec::new(), Vec::new());
    let deadline = Instant::now() + Duration::from_secs(10);
    while plain.is_empty() && styled.is_empty() {
        assert!(Instant::now() < deadline, "/{command} answered nothing");
        plain.extend(m::tick(data, || None, || vec![6; 128], in_world()));
        styled.extend(m::take_chat_components());
        std::thread::sleep(m::TICK);
    }
    (plain, styled)
}

#[test]
#[ignore = "needs a JDK and sh tools/build-java.sh"]
fn a_mods_command_errors_made_of_components_reach_the_player() {
    let (game, data) = game_folder();
    let chat = tick_until(&data, || m::client_commands().contains(&"kiln_test_count"));
    println!("chat: {chat:?}");
    m::take_chat_components();

    // The mod's own error, a gold component, in the red the game gives errors: not Java's IncompatibleClassChangeError
    // ("does not implement the requested interface com.mojang.brigadier.Message").
    let (plain, styled) = run(&data, "kiln_test_count");
    assert!(plain.is_empty(), "{plain:?}");
    assert_eq!(
        styled,
        [json!({"text": "", "color": "red", "extra": [{"text": "Nothing to count", "color": "gold"}]})]
    );
    // One that takes the count.
    let (_, styled) = run(&data, "kiln_test_count 100");
    assert_eq!(styled, [json!({"text": "", "color": "red", "extra": [{"text": "Too many to count: 100"}]})]);
    // A typo: brigadier's text, and where (the game's "... at position 16: <--[HERE]", in the player's language).
    let (_, styled) = run(&data, "kiln_test_count lots");
    assert_eq!(styled.len(), 1, "{styled:?}");
    let error = &styled[0]["extra"][0];
    assert_eq!(error["translate"], "command.context.parse_error", "{styled:?}");
    assert_eq!(error["with"][0], json!({"text": "Expected integer"}), "{styled:?}");
    assert_eq!(error["with"][1], 16, "{styled:?}");
    assert!(error["with"][2].as_str().unwrap().ends_with("<--[HERE]"), "{styled:?}");
    // And a count that works.
    let (plain, styled) = run(&data, "kiln_test_count 5");
    assert_eq!(plain, ["Counted to 5"], "{styled:?}");
    let _ = std::fs::remove_dir_all(&game);
}
