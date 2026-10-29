// Kiln with real Java, as the game drives it: needs a JDK and the jars from `sh tools/build-java.sh`.
//
//   sh tools/build-java.sh && cargo test -p horizon-kiln-mod -- --ignored --nocapture

use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;
use horizon_kiln_mod::game::kiln_jvm::HudOp;

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
    // Only the spike's mod: these tests read what it draws.
    for jar in m::jars(&built.join("mods"))
        .into_iter()
        .filter(|j| j.ends_with("kiln-hello.jar"))
    {
        std::fs::copy(&jar, game.join("kiln/mods").join(jar.file_name().unwrap())).unwrap();
    }
    let data = game.join("horizon-mods/.horizon-mod-data/kiln");
    std::fs::create_dir_all(&data).unwrap();
    data
}

fn in_world(paused: bool) -> m::Snapshot {
    m::Snapshot {
        connected: true,
        in_world: true,
        paused,
        game_time: 10,
        dimension: "minecraft:overworld".into(),
        advancements: None,
    }
}

/// The text the Java mods drew.
fn texts() -> Vec<String> {
    m::hud_ops()
        .into_iter()
        .filter_map(|op| match op {
            HudOp::Text { text, .. } => Some(text),
            HudOp::Fill { .. } => None,
        })
        .collect()
}

/// The hello mod's tick count, from its HUD line.
fn hello_ticks() -> Option<u64> {
    texts()
        .iter()
        .find_map(|t| t.strip_prefix("Hello from Java! tick ")?.parse().ok())
}

/// Ticks every millisecond, as fast as a game never would, until `done`; the chat lines heard.
fn tick_until(data: &Path, done: impl Fn() -> bool) -> Vec<String> {
    let deadline = Instant::now() + Duration::from_secs(10);
    let mut chat = Vec::new();
    while !done() {
        assert!(Instant::now() < deadline, "no answer: {:?}", m::hud_ops());
        chat.extend(m::tick(data, || None, || vec![6; 128], in_world(false)));
        std::thread::sleep(Duration::from_millis(1));
    }
    chat
}

#[test]
#[ignore = "needs a JDK and sh tools/build-java.sh"]
fn a_fabric_mod_ticks_and_draws_on_the_hud() {
    let data = game_folder();
    let started = Instant::now();
    let chat = tick_until(&data, || hello_ticks().is_some());
    println!(
        "Java started and the mod drew its line in {:?}",
        started.elapsed()
    );
    assert!(
        chat.iter()
            .any(|l| l.contains("1 of 1 Java mods running on Java")),
        "{chat:?}"
    );
    // The mod's box: a fill, then its text, right-aligned with the font widths Kiln sent (6 a character).
    let ops = m::hud_ops();
    assert!(
        matches!(
            ops[0],
            HudOp::Fill {
                argb: 0x7800_0000,
                ..
            }
        ),
        "{ops:?}"
    );
    let HudOp::Text { x, argb, .. } = &ops[1] else {
        panic!("{ops:?}")
    };
    let line = &texts()[0];
    assert_eq!(*x, m::DEFAULT_GUI.0 as i32 - 6 * line.len() as i32 - 4);
    assert_eq!(*argb, 0xFFFF_FFFF);

    // A tick costs the game a few lines written to Java and the replies already read: it never waits.
    let started = Instant::now();
    for _ in 0..10_000 {
        m::tick(&data, || None, Vec::new, in_world(false));
    }
    let per_tick = started.elapsed() / 10_000;
    println!("one tick of Kiln, for the game: {per_tick:?}");
    assert!(per_tick.as_micros() < 200, "{per_tick:?} a tick");

    // Paused, the game stops ticking: frames make a client tick every 50 ms, as vanilla's client does.
    let deadline = Instant::now() + Duration::from_secs(2);
    let mut answered = hello_ticks().unwrap();
    while Instant::now() < deadline && hello_ticks().unwrap() == answered {
        m::frame(|| in_world(true));
        std::thread::sleep(Duration::from_millis(1));
    }
    assert!(hello_ticks().unwrap() > answered, "no tick while paused");
    answered = hello_ticks().unwrap();
    let frames_started = Instant::now();
    let mut made = 0;
    while frames_started.elapsed() < Duration::from_millis(500) {
        made += m::frame(|| in_world(true)).len();
        std::thread::sleep(Duration::from_millis(5));
    }
    std::thread::sleep(Duration::from_millis(50));
    m::frame(|| in_world(true));
    let paused_ticks = hello_ticks().unwrap() - answered;
    println!("ticks made by frames in 0.5 s of pause: {paused_ticks}");
    assert!((8..=12).contains(&paused_ticks), "{paused_ticks} ticks");
    assert_eq!(made, 0);

    // Out of a world: no HUD.
    m::tick(&data, || None, Vec::new, m::Snapshot::default());
    let deadline = Instant::now() + Duration::from_secs(2);
    while Instant::now() < deadline && !m::hud_ops().is_empty() {
        m::frame(m::Snapshot::default);
        std::thread::sleep(Duration::from_millis(5));
    }
    assert!(m::hud_ops().is_empty());
}
