// A Java mod's key the player binds, with real Java, as the game drives it: Kiln's test mod with one key, V as the mod
// sets it (kiln/test-mods/keys). Java tells Kiln of the key, Kiln lists it on the mod's page in Options > Mods and keeps
// it for the next game; the key the player chose there (the game's `native_mod_key`) reaches Java, and the mod's
// mapping follows it: V no longer counts, B does, and `isDown` reads B. Needs a JDK and the jars from
// `sh tools/build-java.sh`.
//
//   sh tools/build-java.sh && cargo test -p horizon-kiln-mod --test keys -- --ignored --nocapture

use std::collections::BTreeMap;
use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;

const ROW: &str = "kiln_kiln_test_keys";
const NAME: &str = "key.kiln_test_keys.press";

/// A game folder with Kiln's runtime, the libraries and the test mod; the mod's data folder in it.
fn game_folder() -> (PathBuf, PathBuf) {
    let built = Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln");
    let jar = built.join("test/kiln-test-keys.jar");
    assert!(jar.is_file(), "run sh tools/build-java.sh first");
    let game = std::env::temp_dir().join(format!("kiln-keys-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&game);
    let kiln = game.join("kiln");
    std::fs::create_dir_all(kiln.join("mods")).unwrap();
    std::fs::create_dir_all(kiln.join("lib")).unwrap();
    std::fs::copy(built.join("kiln-runtime.jar"), kiln.join("kiln-runtime.jar")).unwrap();
    for lib in m::jars(&built.join("lib")) {
        std::fs::copy(&lib, kiln.join("lib").join(lib.file_name().unwrap())).unwrap();
    }
    std::fs::copy(&jar, kiln.join("mods/kiln-test-keys.jar")).unwrap();
    let data = game.join("horizon-mods/.horizon-mod-data/kiln");
    std::fs::create_dir_all(&data).unwrap();
    (game, data)
}

/// The window's game in a world, its player in it, the keys `held`, and the mod's key bound to `bound`.
fn in_world(bound: &str, held: &[&str]) -> m::Snapshot {
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
        state: m::WindowState {
            held: held.iter().map(|k| (*k).to_owned()).collect(),
            ..Default::default()
        },
        keys: BTreeMap::from([(NAME.to_owned(), bound.to_owned())]),
        ..m::Snapshot::default()
    }
}

/// Ticks at the game's pace until a chat line passes `done`, or for `at_most`; the chat heard.
fn tick_for(data: &Path, snapshot: &m::Snapshot, at_most: Duration, done: impl Fn(&str) -> bool) -> Vec<String> {
    let deadline = Instant::now() + at_most;
    let mut chat = Vec::new();
    while Instant::now() < deadline && !chat.iter().any(|l: &String| done(l)) {
        chat.extend(m::tick(data, || None, || vec![6; 128], snapshot.clone()));
        std::thread::sleep(m::TICK);
    }
    chat
}

#[test]
#[ignore = "needs a JDK and sh tools/build-java.sh"]
fn a_mods_key_the_player_binds_follows_their_choice() {
    let (game, data) = game_folder();
    let mod_key = in_world("key.keyboard.v", &[]);

    // Java starts the mod and tells of its key: on the mod's page, labelled as its language file says, V by default.
    let chat = tick_for(&data, &mod_key, Duration::from_secs(20), |l| l.contains("Java mods running"));
    assert!(chat.iter().any(|l| l.starts_with("Kiln: 1 of 1 Java mods running")), "{chat:?}");
    assert_eq!(
        m::page_keys(ROW),
        [[NAME.to_owned(), "Count a press".to_owned(), "key.keyboard.v".to_owned()]]
    );
    assert_eq!(m::mod_keys(), [(ROW.to_owned(), NAME.to_owned(), "key.keyboard.v".to_owned())]);
    let changed = m::take_changed_keys().expect("the mod's page hears of its key");
    assert_eq!(changed[ROW], m::page_keys(ROW));
    // Kept for the next game, whose pages are made before Java starts.
    let kept = std::fs::read_to_string(game.join("kiln/cache/keys.json")).unwrap();
    assert!(kept.contains(NAME), "{kept}");
    let rows = m::mod_rows(&game.join("kiln"));
    assert_eq!(rows[0].0, ROW);
    assert_eq!(rows[0].3, m::page_keys(ROW));

    // Its own key: V counts.
    m::key("key.keyboard.v");
    let chat = tick_for(&data, &mod_key, Duration::from_secs(5), |l| l.starts_with("Test Keys: press"));
    assert!(chat.iter().any(|l| l == "Test Keys: press 1"), "{chat:?}");

    // The player chose B on the mod's page: V no longer counts...
    let chosen = in_world("key.keyboard.b", &[]);
    m::key("key.keyboard.v");
    let chat = tick_for(&data, &chosen, Duration::from_secs(1), |l| l.starts_with("Test Keys: press"));
    assert!(!chat.iter().any(|l| l.starts_with("Test Keys: press")), "V still counts: {chat:?}");
    // ...B does, and the mod reads B held.
    let held = in_world("key.keyboard.b", &["key.keyboard.b"]);
    m::key("key.keyboard.b");
    let chat = tick_for(&data, &held, Duration::from_secs(5), |l| l.starts_with("Test Keys: press"));
    assert!(chat.iter().any(|l| l == "Test Keys: press 2, held"), "{chat:?}");

    // Back to the mod's own key: V counts again, B no longer.
    m::key("key.keyboard.b");
    let chat = tick_for(&data, &mod_key, Duration::from_secs(1), |l| l.starts_with("Test Keys: press"));
    assert!(!chat.iter().any(|l| l.starts_with("Test Keys: press")), "B still counts: {chat:?}");
    m::key("key.keyboard.v");
    let chat = tick_for(&data, &mod_key, Duration::from_secs(5), |l| l.starts_with("Test Keys: press"));
    assert!(chat.iter().any(|l| l == "Test Keys: press 3"), "{chat:?}");
    let _ = std::fs::remove_dir_all(&game);
}
