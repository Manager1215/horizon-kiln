// A Java mod turned off now, while the world goes on (D-113's soft switch): the test mod (kiln/test-mods/soft) counts
// its client ticks and its mixin's calls in Minecraft.tick, draws both on the HUD and says them for its command.
// Turned off in Options > Mods, Kiln stops calling it at once: the HUD goes, the counts stop, the command is refused.
// Turned on again, they go on from where they stopped.
// Needs a JDK and the jars from `sh tools/build-java.sh`.
//
//   sh tools/build-java.sh && cargo test -p horizon-kiln-mod --test soft_switch -- --ignored --nocapture

use std::collections::BTreeSet;
use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;
use horizon_kiln_mod::game::kiln_jvm::HudOp;

const ID: &str = "kiln_test_soft";

/// How long a wait for Java may take before the test calls it hung. Only a hang fails on it: how fast Java answers
/// on a busy computer is no matter here (D-266).
const HANG: Duration = Duration::from_secs(120);

/// A game folder with Kiln, the libraries and the test mod; returns the game folder and the mod's data folder.
fn game_folder() -> (PathBuf, PathBuf) {
    let built = Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln");
    let jar = built.join("test/kiln-test-soft.jar");
    assert!(jar.is_file(), "run sh tools/build-java.sh first");
    let game = std::env::temp_dir().join(format!("kiln-soft-{}", std::process::id()));
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
    std::fs::copy(&jar, kiln.join("mods/kiln-test-soft.jar")).unwrap();
    let data = game.join("horizon-mods/.horizon-mod-data/kiln");
    std::fs::create_dir_all(&data).unwrap();
    (game, data)
}

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

/// The counts the mod's HUD element shows (its ticks, its mixin's calls), if it is drawn.
fn counts() -> Option<(u32, u32)> {
    m::hud_ops().iter().find_map(|op| match op {
        HudOp::Text { text, .. } => {
            let mut words = text.strip_prefix("Soft ")?.split(' ');
            Some((words.next()?.parse().ok()?, words.next()?.parse().ok()?))
        }
        _ => None,
    })
}

/// Java's answers to every tick sent to it, read (`m::settle`): what the mod's HUD shows is then what it made of them
/// all, however far behind the game a busy computer let it fall. The chat they made.
fn settled() -> Vec<String> {
    let mut chat = m::settle(HANG);
    assert_eq!(m::java_behind(), 0, "Java hung: {chat:?}");
    chat.extend(m::take_chat_components().iter().map(|c| c.to_string()));
    chat
}

/// Ticks at 20 a second for `ticks` ticks, or until `done`; the chat it made.
fn tick(data: &Path, ticks: usize, done: impl Fn(&[String]) -> bool) -> Vec<String> {
    let snapshot = in_world();
    let mut chat = Vec::new();
    for _ in 0..ticks {
        chat.extend(m::tick(data, || None, || vec![6; 128], snapshot.clone()));
        // What the mods' commands say comes as components (their styles kept): their text is enough here.
        chat.extend(m::take_chat_components().iter().map(|c| c.to_string()));
        if done(&chat) {
            break;
        }
        std::thread::sleep(m::TICK);
    }
    chat
}

#[test]
#[ignore = "needs a JDK and sh tools/build-java.sh"]
fn a_mod_turned_off_now_stops_and_comes_back() {
    let (game, data) = game_folder();
    m::set_off(BTreeSet::new());
    let chat = tick(
        &data,
        (HANG.as_millis() / m::TICK.as_millis()) as usize,
        |c| c.iter().any(|l| l.contains("Java mods running")),
    );
    assert!(
        chat.iter()
            .any(|l| l.starts_with("Kiln: 1 of 1 Java mods running")),
        "{chat:?}"
    );
    let deadline = Instant::now() + HANG;
    while counts().is_none_or(|(t, m)| t < 5 || m < 5) && Instant::now() < deadline {
        tick(&data, 1, |_| false);
    }
    settled();
    let (ticks, mixin) = counts().expect("the mod's HUD is drawn while it is on");
    assert!(
        ticks >= 5 && mixin >= 5,
        "{ticks} ticks, {mixin} mixin calls"
    );

    // Off now: nothing of it runs; the world goes on.
    m::set_off(BTreeSet::from([ID.to_owned()]));
    tick(&data, 3, |_| false);
    settled();
    assert_eq!(counts(), None, "its HUD is still drawn: {:?}", m::hud_ops());
    tick(&data, 40, |_| false);
    assert!(
        m::client_command("softtest"),
        "its command is still the mod's while it is off"
    );
    let mut chat = tick(&data, 1, |_| false);
    chat.extend(settled());
    assert!(
        chat.iter().any(|l| l.contains("which is turned off")),
        "{chat:?}"
    );
    assert!(
        !chat.iter().any(|l| l.contains("Soft command")),
        "its command ran while off: {chat:?}"
    );

    // On again: it goes on from where it stopped, 40 ticks later.
    m::set_off(BTreeSet::new());
    let deadline = Instant::now() + HANG;
    while counts().is_none() && Instant::now() < deadline {
        tick(&data, 1, |_| false);
    }
    settled();
    let (after, mixin_after) = counts().expect("its HUD is drawn again");
    assert!(
        after > ticks && after < ticks + 20,
        "ticks went from {ticks} to {after} over 40 ticks off"
    );
    assert!(
        mixin_after > mixin && mixin_after < mixin + 20,
        "mixin calls went from {mixin} to {mixin_after} over 40 ticks off"
    );
    assert!(m::client_command("softtest"));
    let mut chat = tick(&data, 1, |_| false);
    chat.extend(settled());
    assert!(
        chat.iter().any(|l| l.starts_with("Soft command")),
        "{chat:?}"
    );
    let log = std::fs::read_to_string(game.join("kiln/java.log")).unwrap_or_default();
    assert!(!log.contains("Exception"), "java.log: {log}");
    let _ = std::fs::remove_dir_all(&game);
}
