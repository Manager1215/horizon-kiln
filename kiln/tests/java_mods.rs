// Kiln with real Java, as the game drives it: needs a JDK and the jars from `sh tools/build-java.sh`.
//
//   sh tools/build-java.sh && cargo test -p horizon-kiln-mod -- --ignored --nocapture

use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;

/// A game folder holding Kiln's folder (the built jars), and the mod's data folder inside it.
fn game_folder() -> PathBuf {
    let built = Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln");
    assert!(
        built.join("kiln-runtime.jar").is_file(),
        "run sh tools/build-java.sh first"
    );
    let game = std::env::temp_dir().join(format!("kiln-game-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&game);
    std::fs::create_dir_all(game.join("kiln/mods")).unwrap();
    std::fs::copy(
        built.join("kiln-runtime.jar"),
        game.join("kiln/kiln-runtime.jar"),
    )
    .unwrap();
    for jar in m::jars(&built.join("mods")) {
        std::fs::copy(&jar, game.join("kiln/mods").join(jar.file_name().unwrap())).unwrap();
    }
    let data = game.join("horizon-mods/.horizon-mod-data/kiln");
    std::fs::create_dir_all(&data).unwrap();
    data
}

/// Ticks every millisecond, as fast as a game never would, until `done` says so; the chat lines heard.
fn tick_until(data: &Path, done: impl Fn(&[(String, u32)]) -> bool) -> Vec<String> {
    let deadline = Instant::now() + Duration::from_secs(10);
    let mut chat = Vec::new();
    while !done(&m::hud_lines()) {
        assert!(Instant::now() < deadline, "no answer: {:?}", m::hud_lines());
        chat.extend(m::tick(data, || None));
        std::thread::sleep(Duration::from_millis(1));
    }
    chat
}

#[test]
#[ignore = "needs a JDK and sh tools/build-java.sh"]
fn a_java_mod_starts_ticks_and_sets_the_hud_line() {
    let data = game_folder();
    let started = Instant::now();
    let chat = tick_until(&data, |lines| lines.len() == 2);
    println!(
        "Java started and a mod set its line in {:?}",
        started.elapsed()
    );
    assert!(
        chat.iter()
            .any(|l| l.contains("1 of 1 Java mods running on Java")),
        "{chat:?}"
    );
    assert_eq!(m::hud_lines()[0].0, "Kiln 0.0.2: 1 Java mod");
    assert!(m::hud_lines()[1].0.starts_with("Hello from Java! tick "));

    // A tick costs the game a line written to Java and the replies already read: it never waits.
    let started = Instant::now();
    for _ in 0..10_000 {
        m::tick(&data, || None);
    }
    let per_tick = started.elapsed() / 10_000;
    println!("one tick of Kiln, for the game: {per_tick:?}");
    assert!(per_tick.as_micros() < 200, "{per_tick:?} a tick");

    // How long Java takes to answer a tick: from sending it to the HUD line it sets.
    let mut waits = Vec::new();
    for _ in 0..50 {
        let line = |lines: &[(String, u32)]| lines.get(1).map(|l| l.0.clone()).unwrap_or_default();
        let before = line(&m::hud_lines());
        let sent = Instant::now();
        m::tick(&data, || None);
        let deadline = sent + Duration::from_secs(1);
        while line(&m::hud_lines()) == before && Instant::now() < deadline {
            m::tick(&data, || None);
        }
        waits.push(sent.elapsed());
        std::thread::sleep(Duration::from_millis(50));
    }
    waits.sort();
    println!(
        "Java's answer to a tick: median {:?}, worst {:?}",
        waits[waits.len() / 2],
        waits[waits.len() - 1]
    );
}
