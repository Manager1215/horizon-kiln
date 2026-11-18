// A mod that ends Java (`System.exit`, `Runtime.halt`), with real Java in the game's process (D-112): Kiln's agent
// rewrites those calls to throw, so the mod's entrypoint fails and is named, and the game (here, the test's process)
// and the other mods go on. In a process of its own (KILN_JVM=process) Java would end instead: the test says so and
// stops. Needs a JDK and the jars from `sh tools/build-java.sh`.
//
//   sh tools/build-java.sh && cargo test -p horizon-kiln-mod --test exits -- --ignored --nocapture

use std::path::Path;
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;

#[test]
#[ignore = "needs a JDK and sh tools/build-java.sh"]
fn a_mod_that_exits_doesnt_end_the_game() {
    let built = Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln");
    let exits = built.join("test/kiln-test-exits.jar");
    assert!(exits.is_file(), "run sh tools/build-java.sh first");
    let game = std::env::temp_dir().join(format!("kiln-exits-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&game);
    let kiln = game.join("kiln");
    std::fs::create_dir_all(kiln.join("mods")).unwrap();
    std::fs::create_dir_all(kiln.join("lib")).unwrap();
    std::fs::copy(
        built.join("kiln-runtime.jar"),
        kiln.join("kiln-runtime.jar"),
    )
    .unwrap();
    for jar in m::jars(&built.join("lib")) {
        std::fs::copy(&jar, kiln.join("lib").join(jar.file_name().unwrap())).unwrap();
    }
    std::fs::copy(
        built.join("mods/kiln-hello.jar"),
        kiln.join("mods/kiln-hello.jar"),
    )
    .unwrap();
    std::fs::copy(&exits, kiln.join("mods/kiln-test-exits.jar")).unwrap();
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
        println!(
            "Java runs in a process of its own here, where a mod's exit ends only Java: nothing to check"
        );
        return;
    }
    let deadline = Instant::now() + Duration::from_secs(20);
    while !chat.iter().any(|l| l.contains("Java mods running")) {
        assert!(
            Instant::now() < deadline,
            "the mods' entrypoints never all answered: {chat:?}"
        );
        chat.extend(m::tick(&data, || None, || vec![6; 128], snapshot.clone()));
        std::thread::sleep(m::TICK);
    }
    println!("chat: {chat:#?}");
    // The mod that exits is named, with Kiln's reason; the other one runs.
    assert!(
        chat.iter()
            .any(|l| l.starts_with("Kiln: Kiln Test Exits didn't start: ")
                && l.contains("Kiln stopped it")),
        "{chat:?}"
    );
    // Where: the mod's own call, not Kiln's.
    assert!(
        chat.iter()
            .any(|l| l.contains("System.exit(3)") && l.contains("kiln.test.exits.Exits")),
        "{chat:?}"
    );
    assert!(
        chat.iter()
            .any(|l| l.contains("Runtime.halt(4)") && l.contains("kiln.test.exits.Halts")),
        "{chat:?}"
    );
    assert!(
        chat.iter()
            .any(|l| l.starts_with("Kiln: 1 of 2 Java mods running on Java ")),
        "{chat:?}"
    );
    // And Java still answers: ticks go on, nothing says it stopped.
    for _ in 0..20 {
        let more = m::tick(&data, || None, || vec![6; 128], snapshot.clone());
        assert!(!more.iter().any(|l| l.contains("Java stopped")), "{more:?}");
        std::thread::sleep(m::TICK);
    }
    let _ = std::fs::remove_dir_all(&game);
}
