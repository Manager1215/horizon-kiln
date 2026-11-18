// A mod that reads and writes its settings by relative paths (`config/x.json`), with real Java in the game's process
// (D-112, D-151): the game's process runs in the game's folder (here, the test's), yet the mod must find Kiln's
// `<game folder>/kiln/config`, as with Java in a process of its own. Kiln's agent makes the mod's relative paths
// absolute against Kiln's folder; without that the mod's java.io opens look in the wrong folder and it doesn't start.
// D-188 (#216): constructors with more arguments after the path, `getPath()` giving back the mod's text, and a
// process started with no folder of its own starting in Kiln's; the test mod checks each and doesn't start otherwise.
// Needs a JDK and the jars from `sh tools/build-java.sh`.
//
//   sh tools/build-java.sh && cargo test -p horizon-kiln-mod --test relative_paths -- --ignored --nocapture

use std::path::Path;
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;

#[test]
#[ignore = "needs a JDK and sh tools/build-java.sh"]
fn a_mods_relative_paths_are_kilns_folder() {
    let built = Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln");
    let paths = built.join("test/kiln-test-paths.jar");
    assert!(paths.is_file(), "run sh tools/build-java.sh first");
    let game = std::env::temp_dir().join(format!("kiln-paths-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&game);
    let kiln = game.join("kiln");
    std::fs::create_dir_all(kiln.join("mods")).unwrap();
    std::fs::create_dir_all(kiln.join("lib")).unwrap();
    std::fs::create_dir_all(kiln.join("config")).unwrap();
    std::fs::copy(
        built.join("kiln-runtime.jar"),
        kiln.join("kiln-runtime.jar"),
    )
    .unwrap();
    for jar in m::jars(&built.join("lib")) {
        std::fs::copy(&jar, kiln.join("lib").join(jar.file_name().unwrap())).unwrap();
    }
    std::fs::copy(&paths, kiln.join("mods/kiln-test-paths.jar")).unwrap();
    std::fs::write(
        kiln.join("config/kiln_test_paths.json"),
        "{\"kiln\": true}\n",
    )
    .unwrap();
    // The test's own folder (the game's process's) has no such file: a relative open there fails.
    assert!(!Path::new("config/kiln_test_paths.json").exists());
    let data = game.join("horizon-mods/.horizon-mod-data/kiln");
    std::fs::create_dir_all(&data).unwrap();

    let snapshot = m::Snapshot {
        connected: true,
        in_world: true,
        game_time: 10,
        dimension: "minecraft:overworld".into(),
        ..m::Snapshot::default()
    };
    let mut chat: Vec<String> = m::tick(&data, || None, || vec![6; 128], snapshot.clone());
    if !m::java_in_process() {
        println!("Java runs in a process of its own here, in Kiln's folder: nothing to check");
        return;
    }
    let deadline = Instant::now() + Duration::from_secs(20);
    while !chat.iter().any(|l| l.contains("Java mods running")) {
        assert!(
            Instant::now() < deadline,
            "the mod's entrypoint never answered: {chat:?}"
        );
        chat.extend(m::tick(&data, || None, || vec![6; 128], snapshot.clone()));
        std::thread::sleep(m::TICK);
    }
    println!("chat: {chat:#?}");
    assert!(
        chat.iter()
            .any(|l| l.starts_with("Kiln: 1 of 1 Java mods running on Java ")),
        "{chat:?}"
    );
    // What it wrote is in Kiln's config folder, not the game's.
    assert_eq!(
        std::fs::read_to_string(kiln.join("config/kiln_test_paths.written")).unwrap(),
        "written"
    );
    assert!(!Path::new("config/kiln_test_paths.written").exists());
    // D-188 (#216): a constructor with two arguments after the path writes there too.
    assert_eq!(
        std::fs::read_to_string(kiln.join("config/kiln_test_paths.three")).unwrap(),
        "three"
    );
    assert!(!Path::new("config/kiln_test_paths.three").exists());
    let _ = std::fs::remove_dir_all(&game);
}
