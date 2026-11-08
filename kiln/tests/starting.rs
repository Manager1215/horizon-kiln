// Kiln starting Java mods with real Java, as the game drives it: the spike's mod twice (its jar and a copy) beside Kiln's
// test mod whose client entrypoint fails (kiln/test-mods/fails). The mod that fails is named in chat and left out of
// the count, the copy is the same mod once, and Kiln's log names the mods that started. Needs a JDK and the jars from
// `sh tools/build-java.sh`.
//
//   sh tools/build-java.sh && cargo test -p horizon-kiln-mod --test starting -- --ignored --nocapture

use std::path::Path;
use std::sync::Mutex;
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;

/// The lines Kiln logged, kept for the test (and on its output).
static LOGGED: Mutex<Vec<String>> = Mutex::new(Vec::new());

struct Kept;

impl log::Log for Kept {
    fn enabled(&self, _: &log::Metadata) -> bool {
        true
    }

    fn log(&self, record: &log::Record) {
        if record.level() <= log::Level::Info {
            let line = record.args().to_string();
            println!("{}: {line}", record.level());
            LOGGED.lock().unwrap().push(line);
        }
    }

    fn flush(&self) {}
}

#[test]
#[ignore = "needs a JDK and sh tools/build-java.sh"]
fn a_mod_that_fails_is_named_and_a_copy_is_the_same_mod() {
    log::set_logger(&Kept)
        .map(|()| log::set_max_level(log::LevelFilter::Info))
        .unwrap();
    let built = Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln");
    let fails = built.join("test/kiln-test-fails.jar");
    assert!(fails.is_file(), "run sh tools/build-java.sh first");
    let game = std::env::temp_dir().join(format!("kiln-starting-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&game);
    let kiln = game.join("kiln");
    std::fs::create_dir_all(kiln.join("mods")).unwrap();
    std::fs::create_dir_all(kiln.join("lib")).unwrap();
    std::fs::copy(built.join("kiln-runtime.jar"), kiln.join("kiln-runtime.jar")).unwrap();
    for jar in m::jars(&built.join("lib")) {
        std::fs::copy(&jar, kiln.join("lib").join(jar.file_name().unwrap())).unwrap();
    }
    // The spike's mod, and its jar again under another name, as a player who put it in twice has it.
    let hello = built.join("mods/kiln-hello.jar");
    std::fs::copy(&hello, kiln.join("mods/kiln-hello.jar")).unwrap();
    std::fs::copy(&hello, kiln.join("mods/kiln-hello-again.jar")).unwrap();
    std::fs::copy(&fails, kiln.join("mods/kiln-test-fails.jar")).unwrap();
    let data = game.join("horizon-mods/.horizon-mod-data/kiln");
    std::fs::create_dir_all(&data).unwrap();

    let snapshot = m::Snapshot {
        connected: true,
        in_world: true,
        game_time: 10,
        dimension: "minecraft:overworld".into(),
        ..m::Snapshot::default()
    };
    // At the game's pace until every entrypoint answered: then Kiln says in chat how many mods run.
    let deadline = Instant::now() + Duration::from_secs(20);
    let mut chat: Vec<String> = Vec::new();
    while !chat.iter().any(|l| l.contains("Java mods running")) {
        assert!(Instant::now() < deadline, "the mods' entrypoints never all answered: {chat:?}");
        chat.extend(m::tick(&data, || None, || vec![6; 128], snapshot.clone()));
        std::thread::sleep(m::TICK);
    }
    println!("chat: {chat:#?}");
    // The test mod's main entrypoint ran and its client one threw: it is named with what went wrong, and isn't
    // counted. The spike's mod runs, once.
    assert!(
        chat.iter().any(|l| l.starts_with("Kiln: Kiln Test Fails didn't start: ")
            && l.contains("this mod fails on purpose")),
        "{chat:?}"
    );
    assert!(
        chat.iter().any(|l| l.starts_with("Kiln: 1 of 2 Java mods running on Java ")),
        "{chat:?}"
    );
    assert_eq!(m::status_lines(), [(m::status_line(Some(1), None), m::INK)]);
    // Kiln's log names the mod that started, once; not the one that failed, though its main entrypoint ran.
    let logged = LOGGED.lock().unwrap().clone();
    let started: Vec<&String> = logged.iter().filter(|l| l.starts_with("Kiln: started ")).collect();
    assert_eq!(started, ["Kiln: started Kiln Hello 0.0.1 (kiln_hello)"], "{logged:#?}");
    let _ = std::fs::remove_dir_all(&game);
}
