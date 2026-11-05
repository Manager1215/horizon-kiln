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
    // The libraries Minecraft ships, as an install has them: Kiln's stand-ins use some (DataFixerUpper's codecs).
    std::fs::create_dir_all(game.join("kiln/lib")).unwrap();
    for jar in m::jars(&built.join("lib")) {
        std::fs::copy(&jar, game.join("kiln/lib").join(jar.file_name().unwrap())).unwrap();
    }
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
        player: None,
        ..m::Snapshot::default()
    }
}

/// The text the Java mods drew.
fn texts() -> Vec<String> {
    m::hud_ops()
        .into_iter()
        .filter_map(|op| match op {
            HudOp::Text { text, .. } => Some(text),
            HudOp::Fill { .. } | HudOp::Item { .. } | HudOp::Clip(_) => None,
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
    // The game's HUD, smaller than Kiln's default: the mod lays its line out by it.
    m::set_gui(320.0, 180.0);
    let started = Instant::now();
    // The first tick starts Java; till the mods' entrypoints ran, Kiln's status box says so.
    let mut chat = m::tick(&data, || None, || vec![6; 128], in_world(false));
    assert_eq!(
        m::status_lines(),
        [(m::status_line(None, Some("starting Java")), m::INK)]
    );
    chat.extend(tick_until(&data, || hello_ticks().is_some()));
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
    assert_eq!(*x, (320 - 6 * line.len() as i32 - 4) as f32);
    assert_eq!(*argb, 0xFFFF_FFFF);

    // A tick costs the game a few lines queued for Java and the replies already read: it never waits. A
    // burst under the queue's size (jvm.rs), so Java isn't taken for stuck.
    let burst = horizon_kiln_mod::game::kiln_jvm::QUEUE as u32 / 2;
    let started = Instant::now();
    for _ in 0..burst {
        m::tick(&data, || None, Vec::new, in_world(false));
    }
    let per_tick = started.elapsed() / burst;
    println!("one tick of Kiln, for the game: {per_tick:?}");
    assert!(per_tick.as_micros() < 200, "{per_tick:?} a tick");
    // Long after the start, with nothing wrong: no status box.
    assert_eq!(m::status_lines(), []);
    // Java answers the burst at its own pace (more slowly on a busy machine): the pause starts once it has, when
    // the mod's count grows only by the ticks paused frames make (one a frame here, 100 ms apart).
    let deadline = Instant::now() + Duration::from_secs(10);
    let mut counted = hello_ticks().unwrap();
    loop {
        std::thread::sleep(Duration::from_millis(100));
        m::frame(|| in_world(true));
        let now = hello_ticks().unwrap();
        if now <= counted + 2 {
            break;
        }
        assert!(Instant::now() < deadline, "Java never caught up with the burst: {now} ticks");
        counted = now;
    }

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
    // Time for Java to answer the last ticks; the frame after reads the answers (and makes one more tick).
    std::thread::sleep(Duration::from_millis(200));
    m::frame(|| in_world(true));
    let took = frames_started.elapsed();
    let paused_ticks = hello_ticks().unwrap() - answered;
    println!("ticks made by frames in {took:?} of pause: {paused_ticks}");
    // Never more than one a tick's length (a tick each frame would be about 100). On a busy machine frames come
    // late and Java answers late, so fewer; but not under a quarter of the rate.
    let most = (took.as_millis() / m::TICK.as_millis()) as u64 + 1;
    assert!(
        (most / 4..=most).contains(&paused_ticks),
        "{paused_ticks} ticks in {took:?}"
    );
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
