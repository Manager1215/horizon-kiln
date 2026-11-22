// A Java mod setting the game's options, with real Java, as the game drives it: Kiln's test mod switches the field of
// view between 30 and 70 degrees on its key, V, as FOV Toggle does (kiln/test-mods/fov). What it sets goes to the game
// (`take_back`'s `fov`, D-105, which the game puts in its options for the camera); the mod reads its own value while the game's
// ticks still say the old one, and the game's own after that. And (D-192) the brightness and the GUI scale, on G and U,
// as fullbright mods and GUI scale mods set them (`gamma`, `gui_scale`). Needs a JDK and the jars from `sh tools/build-java.sh`.
//
//   sh tools/build-java.sh && cargo test -p horizon-kiln-mod --test options -- --ignored --nocapture

use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;

/// A game folder with Kiln's runtime, the libraries and the test mod; the mod's data folder in it.
fn game_folder() -> (PathBuf, PathBuf) {
    let built = Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln");
    let jar = built.join("test/kiln-test-fov.jar");
    assert!(jar.is_file(), "run sh tools/build-java.sh first");
    let game = std::env::temp_dir().join(format!("kiln-options-{}", std::process::id()));
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
    std::fs::copy(&jar, kiln.join("mods/kiln-test-fov.jar")).unwrap();
    let data = game.join("horizon-mods/.horizon-mod-data/kiln");
    std::fs::create_dir_all(&data).unwrap();
    (game, data)
}

/// The window's game in a world, its player in it, the game's field of view `fov`.
fn in_world(fov: f32) -> m::Snapshot {
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
            fov,
            render_distance: 12,
            ..Default::default()
        },
        ..m::Snapshot::default()
    }
}

/// Ticks at the game's pace until a chat line passes `done`, or for `at_most`; the chat heard.
fn tick_for(
    data: &Path,
    snapshot: &m::Snapshot,
    at_most: Duration,
    done: impl Fn(&str) -> bool,
) -> Vec<String> {
    let deadline = Instant::now() + at_most;
    let mut chat = Vec::new();
    while Instant::now() < deadline && !chat.iter().any(|l: &String| done(l)) {
        chat.extend(m::tick(data, || None, || vec![6; 128], snapshot.clone()));
        std::thread::sleep(m::TICK);
    }
    chat
}

/// `n` of the game's ticks, at its pace.
fn ticks(data: &Path, snapshot: &m::Snapshot, n: usize) {
    for _ in 0..n {
        m::tick(data, || None, || vec![6; 128], snapshot.clone());
        std::thread::sleep(m::TICK);
    }
}

/// Presses the mod's key while the game says `fov`; the mod's chat line.
fn toggle(data: &Path, fov: f32) -> String {
    m::key("key.keyboard.v");
    let chat = tick_for(data, &in_world(fov), Duration::from_secs(5), |l| {
        l.starts_with("Test FOV: ")
    });
    chat.into_iter()
        .find(|l| l.starts_with("Test FOV: "))
        .expect("the mod took no press")
}

/// The fields of view the mods set since the last look, as the game takes them (`back fov <degrees>`, D-105).
fn fovs() -> Vec<String> {
    m::take_back()
        .into_iter()
        .filter(|(key, _)| key == "fov")
        .map(|(_, value)| value)
        .collect()
}

#[test]
#[ignore = "needs a JDK and sh tools/build-java.sh"]
fn a_mods_field_of_view_goes_to_the_game() {
    let (game, data) = game_folder();
    let chat = tick_for(&data, &in_world(70.0), Duration::from_secs(20), |l| {
        l.contains("Java mods running")
    });
    assert!(
        chat.iter()
            .any(|l| l.starts_with("Kiln: 1 of 1 Java mods running")),
        "{chat:?}"
    );
    assert!(fovs().is_empty());

    // The mod reads the game's 70 and sets 30: it goes to the game.
    assert_eq!(toggle(&data, 70.0), "Test FOV: 70 to 30");
    assert_eq!(fovs(), ["30"]);
    // A few of the game's ticks still say 70 (it hasn't taken the mod's yet): the mod reads its own 30.
    ticks(&data, &in_world(70.0), 5);
    assert_eq!(toggle(&data, 70.0), "Test FOV: 30 to 70");
    assert_eq!(fovs(), ["70"]);

    // The game took it, and then the player chose 90 in the game's options: the mod reads 90.
    ticks(&data, &in_world(70.0), 2);
    ticks(&data, &in_world(90.0), 2);
    assert_eq!(toggle(&data, 90.0), "Test FOV: 90 to 30");
    assert_eq!(fovs(), ["30"]);
    // A game that never takes the mod's value: after a second of its ticks, the mod reads the game's again.
    ticks(&data, &in_world(90.0), 25);
    assert_eq!(toggle(&data, 90.0), "Test FOV: 90 to 30");
    // The same Java, on: the brightness and the GUI scale (two tests would race for Kiln's one Java and its folder).
    brightness_and_gui_scale(&data);
    let _ = std::fs::remove_dir_all(&game);
}

/// The window's game in a world with the brightness `gamma` and the GUI scale `scale`.
fn with_view(gamma: f32, scale: u32) -> m::Snapshot {
    let mut snapshot = in_world(70.0);
    snapshot.state.gamma = gamma;
    snapshot.state.gui_scale = scale;
    snapshot
}

/// Presses `key` while the game says `snapshot`; the mod's chat line starting with `said`.
fn press(data: &Path, key: &str, snapshot: &m::Snapshot, said: &str) -> String {
    m::key(key);
    let mut chat = tick_for(data, snapshot, Duration::from_secs(120), |l| {
        l.starts_with(said)
    });
    // The rest of what Java answered that tick (the values the mod set, after its chat line), however late it comes on
    // a busy computer (D-266).
    chat.extend(m::settle(Duration::from_secs(120)));
    chat.into_iter()
        .find(|l| l.starts_with(said))
        .expect("the mod took no press")
}

/// The values the mods set for `key` since the last look.
fn backs(key: &str) -> Vec<String> {
    m::take_back()
        .into_iter()
        .filter(|(k, _)| k == key)
        .map(|(_, value)| value)
        .collect()
}

/// A mod's brightness and GUI scale go to the game, with the Java `a_mods_field_of_view_goes_to_the_game` started.
fn brightness_and_gui_scale(data: &Path) {
    ticks(data, &with_view(0.5, 0), 2);
    let _ = m::take_back();

    // Fullbright: the game's 0.5 to 15, past the slider's end, as vanilla's lightmap takes it.
    assert_eq!(
        press(data, "key.keyboard.g", &with_view(0.5, 0), "Test gamma: "),
        "Test gamma: 0.5 to 15.0"
    );
    assert_eq!(backs("gamma"), ["15.0"]);
    // The game took it: the mod reads 15, and gives back the game's 0.5.
    ticks(data, &with_view(15.0, 0), 2);
    assert_eq!(
        press(data, "key.keyboard.g", &with_view(15.0, 0), "Test gamma: "),
        "Test gamma: 15.0 to 0.5"
    );
    assert_eq!(backs("gamma"), ["0.5"]);
    // A brightness the player set in the game's options (0.7, a float there): the mod reads it as the game said it.
    ticks(data, &with_view(0.5, 0), 2);
    ticks(data, &with_view(0.7, 0), 2);
    assert_eq!(
        press(data, "key.keyboard.g", &with_view(0.7, 0), "Test gamma: "),
        "Test gamma: 0.7 to 15.0"
    );
    assert_eq!(backs("gamma"), ["15.0"]);

    // The GUI scale: auto (0) to 2, then the game's 2 back to auto.
    ticks(data, &with_view(15.0, 0), 2);
    assert_eq!(
        press(
            data,
            "key.keyboard.u",
            &with_view(15.0, 0),
            "Test GUI scale: "
        ),
        "Test GUI scale: 0 to 2"
    );
    assert_eq!(backs("gui_scale"), ["2"]);
    ticks(data, &with_view(15.0, 2), 2);
    assert_eq!(
        press(
            data,
            "key.keyboard.u",
            &with_view(15.0, 2),
            "Test GUI scale: "
        ),
        "Test GUI scale: 2 to 0"
    );
    assert_eq!(backs("gui_scale"), ["0"]);
}
