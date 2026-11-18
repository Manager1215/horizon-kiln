// A JVM that gives up while starting in the game's process (D-190, #244): HotSpot ended the whole process then (its
// `os::abort`), so a player whose JVM couldn't start lost the game with no word. Kiln's abort hook turns it into an
// error: Java then runs in a process of its own, the chat says so, and the mods run. The failure here is a heap ZGC
// can't have (`KILN_JVM_HEAP` of 20 TiB; ZGC supports 16), which only Java in the game's process is given. Needs a JDK
// and the jars from `sh tools/build-java.sh`; it must be the first Java its test process starts (tools/test-each.sh
// runs each test in a process of its own).
//
//   sh tools/build-java.sh && cargo test -p horizon-kiln-mod --test jvm_fallback -- --ignored --nocapture

use std::path::Path;
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;

#[test]
#[ignore = "needs a JDK and sh tools/build-java.sh"]
fn a_jvm_that_cant_start_in_the_game_falls_back_and_says_so() {
    if std::env::var("KILN_JVM").as_deref() == Ok("process") {
        println!("KILN_JVM=process: Java never tries the game's process here, nothing to check");
        return;
    }
    // SAFETY: set before anything in this test process starts a thread that reads the environment.
    unsafe {
        // The game's own way, with its fallback: KILN_JVM=inprocess (the tests' check) allows none.
        std::env::remove_var("KILN_JVM");
        std::env::set_var("KILN_JVM_HEAP", (20u64 << 20).to_string());
    }
    let built = Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln");
    assert!(
        built.join("kiln-runtime.jar").is_file(),
        "run sh tools/build-java.sh first"
    );
    let game = std::env::temp_dir().join(format!("kiln-jvm-fallback-{}", std::process::id()));
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
    let data = game.join("horizon-mods/.horizon-mod-data/kiln");
    std::fs::create_dir_all(&data).unwrap();

    let snapshot = m::Snapshot {
        connected: true,
        in_world: true,
        game_time: 10,
        dimension: "minecraft:overworld".into(),
        ..m::Snapshot::default()
    };
    let mut chat: Vec<String> = Vec::new();
    let deadline = Instant::now() + Duration::from_secs(30);
    while !chat.iter().any(|l| l.contains("Java mods running")) {
        assert!(Instant::now() < deadline, "the mods never ran: {chat:?}");
        chat.extend(m::tick(&data, || None, || vec![6; 128], snapshot.clone()));
        std::thread::sleep(m::TICK);
    }
    println!("chat: {chat:#?}");
    // The game (this test's process) is alive, Java runs in a process of its own, and the player was told why.
    assert!(!m::java_in_process());
    assert!(
        chat.iter().any(
            |l| l.starts_with("Kiln: Java couldn't start in the game's process (")
                && l.contains("it gave up while starting")
                && l.contains("It runs in a process of its own")
        ),
        "{chat:?}"
    );
    assert!(
        chat.iter()
            .any(|l| l.starts_with("Kiln: 1 of 1 Java mods running")),
        "{chat:?}"
    );
    let _ = std::fs::remove_dir_all(&game);
}
