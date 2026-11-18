// D-112's crash policy. In Java: a mod whose client tick overflows Java's stack, or runs it out of memory, is turned
// off at once and named in the chat, and Java and the other mods go on; a mod stuck in its tick is named by the
// watchdog while it hangs (the chat, the breadcrumb `last-call`, the `crashed` note), and the note goes once it comes
// back. Out of the game's process: the crash window (`kiln.CrashWatch`, headless here) notices a game that died without
// closing, names the mod from the breadcrumb or from Java's own crash report, and notes it for the next start; a game
// that closed cleanly gets nothing. Needs a JDK and the jars from `sh tools/build-java.sh`.
//
//   sh tools/build-java.sh && cargo test -p horizon-kiln-mod --test crash_policy -- --ignored --nocapture

use std::collections::BTreeSet;
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;
use horizon_kiln_mod::game::kiln_jvm::HudOp;

const ID: &str = "kiln_test_crash";

fn built() -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln")
}

/// A game folder with Kiln, the libraries, the crash test mod and the soft switch's test mod (which keeps counting on
/// its HUD while the game goes on); returns the game folder and the mod's data folder.
fn game_folder(name: &str) -> (PathBuf, PathBuf) {
    let built = built();
    let crash = built.join("test/kiln-test-crash.jar");
    assert!(crash.is_file(), "run sh tools/build-java.sh first");
    let game = std::env::temp_dir().join(format!("kiln-{name}-{}", std::process::id()));
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
    std::fs::copy(&crash, kiln.join("mods/kiln-test-crash.jar")).unwrap();
    std::fs::copy(
        built.join("test/kiln-test-soft.jar"),
        kiln.join("mods/kiln-test-soft.jar"),
    )
    .unwrap();
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

/// Ticks at 20 a second until `done` or `limit` passes; the chat it made (lines and the mods' components' text).
fn tick_until(data: &Path, limit: Duration, done: impl Fn(&[String]) -> bool) -> Vec<String> {
    let snapshot = in_world();
    let mut chat = Vec::new();
    let deadline = Instant::now() + limit;
    while Instant::now() < deadline {
        chat.extend(m::tick(data, || None, || vec![6; 128], snapshot.clone()));
        chat.extend(m::take_chat_components().iter().map(|c| c.to_string()));
        if done(&chat) {
            break;
        }
        std::thread::sleep(m::TICK);
    }
    chat
}

/// The soft switch test mod's tick count on the HUD: it goes up while Java goes on.
fn soft_ticks() -> Option<u32> {
    m::hud_ops().iter().find_map(|op| match op {
        HudOp::Text { text, .. } => text.strip_prefix("Soft ")?.split(' ').next()?.parse().ok(),
        _ => None,
    })
}

/// The crash mod's command, typed; what came of it, until `said` shows.
fn crash_test(data: &Path, command: &str, said: &str, limit: Duration) -> Vec<String> {
    assert!(
        m::client_command(command),
        "{command} isn't the mod's command"
    );
    let chat = tick_until(data, limit, |c| c.iter().any(|l| l.contains(said)));
    println!("{command}: {chat:#?}");
    chat
}

/// Java and the other mod go on: the soft mod's count moves on.
fn goes_on(data: &Path) {
    let before = soft_ticks();
    tick_until(data, Duration::from_secs(10), |_| {
        soft_ticks() > before.map(|n| n + 5)
    });
    assert!(
        soft_ticks() > before.map(|n| n + 5),
        "Java stopped: {before:?} then {:?}",
        soft_ticks()
    );
}

#[test]
#[ignore = "needs a JDK and sh tools/build-java.sh"]
fn a_mod_that_breaks_java_is_turned_off_or_named_and_the_game_goes_on() {
    let (game, data) = game_folder("crash");
    let kiln = game.join("kiln");
    m::set_off(BTreeSet::new());
    let chat = tick_until(&data, Duration::from_secs(30), |c| {
        c.iter().any(|l| l.contains("Java mods running"))
    });
    assert!(
        chat.iter()
            .any(|l| l.starts_with("Kiln: 2 of 2 Java mods running")),
        "{chat:?}"
    );
    tick_until(&data, Duration::from_secs(10), |_| {
        soft_ticks().is_some_and(|n| n > 5) && m::client_commands().contains(&"crashtest")
    });
    let _ = m::take_tripped();

    // A stack overflow in its tick: off at once, named with its call, and the game goes on.
    let chat = crash_test(
        &data,
        "crashtest overflow",
        "overflowed Java's stack",
        Duration::from_secs(20),
    );
    assert!(
        chat.iter().any(|l| l.starts_with(&format!(
            "Kiln: the mod {ID} overflowed Java's stack in END_CLIENT_TICK"
        )) && l.contains("/kiln on")),
        "{chat:?}"
    );
    assert_eq!(
        m::take_tripped(),
        [ID.to_owned()],
        "the game turns its switch off too"
    );
    // The next start's title screen asks about it, as it does after a crash.
    let note = std::fs::read_to_string(kiln.join("crashed")).expect("the trip's note");
    let asked = m::parse_crash_note(&note).expect(&note);
    assert_eq!(
        (
            asked.mod_id.as_str(),
            asked.call.as_str(),
            asked.tripped.as_str()
        ),
        (ID, "END_CLIENT_TICK", "stack")
    );
    assert!(!asked.stuck, "{note}");
    goes_on(&data);
    let chat = crash_test(
        &data,
        "crashtest overflow",
        "which is turned off",
        Duration::from_secs(10),
    );
    assert!(
        chat.iter().any(|l| l.contains("which is turned off")),
        "it is off: {chat:?}"
    );

    // Turned back on (as `/kiln on` does, through Options > Mods): it runs Java out of memory, and is off again.
    m::set_off(BTreeSet::from([ID.to_owned()]));
    tick_until(&data, Duration::from_millis(200), |_| false);
    m::set_off(BTreeSet::new());
    tick_until(&data, Duration::from_millis(500), |_| false);
    let chat = crash_test(
        &data,
        "crashtest oom",
        "ran Java out of memory",
        Duration::from_secs(60),
    );
    assert!(
        chat.iter().any(|l| l.starts_with(&format!(
            "Kiln: the mod {ID} ran Java out of memory in END_CLIENT_TICK"
        ))),
        "{chat:?}"
    );
    assert_eq!(m::take_tripped(), [ID.to_owned()]);
    let note = std::fs::read_to_string(kiln.join("crashed")).expect("the trip's note");
    assert_eq!(
        m::parse_crash_note(&note).expect(&note).tripped,
        "memory",
        "{note}"
    );
    goes_on(&data);

    // On again, it hangs its tick for 13 s: the watchdog names it at 10 s, in the chat, the breadcrumb and the note
    // the next start reads; when the tick returns, the game goes on and the note goes.
    m::set_off(BTreeSet::from([ID.to_owned()]));
    tick_until(&data, Duration::from_millis(200), |_| false);
    m::set_off(BTreeSet::new());
    tick_until(&data, Duration::from_millis(500), |_| false);
    let chat = crash_test(
        &data,
        "crashtest hang 13",
        "has been stuck",
        Duration::from_secs(20),
    );
    assert!(
        chat.iter().any(|l| l.starts_with(&format!(
            "Kiln: the mod {ID} has been stuck for 10 s in END_CLIENT_TICK"
        ))),
        "{chat:?}"
    );
    let last = std::fs::read(kiln.join("last-call")).expect("the breadcrumb is beside java.log");
    assert_eq!(
        m::parse_last_call(&String::from_utf8_lossy(&last)),
        (ID.to_owned(), "END_CLIENT_TICK".to_owned()),
        "{}",
        String::from_utf8_lossy(&last)
    );
    let note = std::fs::read_to_string(kiln.join("crashed")).expect("the watchdog's note");
    let crash = m::parse_crash_note(&note).expect(&note);
    assert!(
        crash.mod_id == ID && crash.stuck && crash.call == "END_CLIENT_TICK",
        "{note}"
    );
    let chat = tick_until(&data, Duration::from_secs(15), |c| {
        c.iter().any(|l| l.contains("the game goes on"))
    });
    assert!(
        chat.iter().any(|l| l.contains("the game goes on")),
        "{chat:?}"
    );
    // The hang's note goes; the earlier trip's comes back, so the next start still asks about the mod.
    let note = std::fs::read_to_string(kiln.join("crashed")).expect("the trip's note back");
    assert_eq!(
        m::parse_crash_note(&note).expect(&note).tripped,
        "memory",
        "{note}"
    );
    goes_on(&data);
    let _ = std::fs::remove_dir_all(&game);
}

/// The JDK the tests run with.
fn java() -> PathBuf {
    let env = |key: &str| std::env::var(key).ok();
    let home = std::env::var_os("HOME").map(PathBuf::from);
    let java = m::find_java(None, &env, home.as_deref(), 21).expect("a JDK");
    horizon_kiln_mod::game::kiln_jvm::java_program(&java)
}

/// A stand-in game: a process that waits, as the game runs, until killed (or for a minute).
fn stand_in() -> std::process::Child {
    Command::new("sleep").arg("60").spawn().expect("sleep")
}

/// The crash window, headless, watching `pid` with Kiln's folder `kiln`.
fn watcher(pid: u32, kiln: &Path) -> std::process::Child {
    Command::new(java())
        .args(["-Djava.awt.headless=true", "-cp"])
        .arg(kiln.join("kiln-runtime.jar"))
        .arg("kiln.CrashWatch")
        .arg(pid.to_string())
        .arg(kiln)
        .stdout(Stdio::piped())
        .spawn()
        .expect("java")
}

/// Kills `game` (as a crash ends it) and waits for the watcher: what it said.
fn crash(mut game: std::process::Child, watcher: std::process::Child) -> String {
    // Its wait has begun: give it a moment to find the game's process.
    std::thread::sleep(Duration::from_millis(800));
    game.kill().unwrap();
    game.wait().unwrap();
    let out = watcher.wait_with_output().unwrap();
    String::from_utf8_lossy(&out.stdout).into_owned()
}

#[test]
#[ignore = "needs a JDK and sh tools/build-java.sh"]
fn the_crash_window_names_the_mod_of_a_game_that_died() {
    let (game, _) = game_folder("crash-window");
    let kiln = game.join("kiln");
    let record = |busy: u8, call: &str, m: &str| {
        format!("busy: {busy}\nthread: Render thread\ncall: {call}\nmod: {m}\n")
    };

    // The breadcrumb names the mod; Java's own crash report says what it was.
    let dead = stand_in();
    let pid = dead.id();
    std::fs::write(
        kiln.join("running"),
        format!("{}\n/bin/true\n", game.display()),
    )
    .unwrap();
    std::fs::write(
        kiln.join("last-call"),
        [record(0, "tick", "-"), record(1, "END_CLIENT_TICK", ID)].concat(),
    )
    .unwrap();
    std::fs::write(
        kiln.join(format!("hs_err_pid{pid}.log")),
        "#\n# A fatal error has been detected by the Java Runtime Environment:\n#\n#  SIGSEGV (0xb) at pc=0x00007f, pid=1, tid=2\n#\n# Problematic frame:\n# C  [libcrash.so+0x10]  boom+0x4\n",
    )
    .unwrap();
    let said = crash(dead, watcher(pid, &kiln));
    println!("{said}");
    assert!(said.contains("SIGSEGV (0xb)"), "{said}");
    assert!(
        said.contains(&format!(
            "while the Java mod Kiln Test Crash ({ID}) was running (END_CLIENT_TICK)"
        )),
        "{said}"
    );
    assert!(
        said.contains("Start the game again without Kiln Test Crash?"),
        "{said}"
    );
    assert!(said.contains("libcrash.so"), "{said}");
    assert!(!kiln.join("running").exists(), "the watcher took the note");
    let crash_note =
        m::parse_crash_note(&std::fs::read_to_string(kiln.join("crashed")).unwrap()).unwrap();
    assert_eq!(
        (
            crash_note.mod_id.as_str(),
            crash_note.call.as_str(),
            crash_note.stuck
        ),
        (ID, "END_CLIENT_TICK", false)
    );
    std::fs::remove_file(kiln.join("crashed")).unwrap();
    // The Crashes tab's list has it (D-134): when, why, the mod, the call, Java's cause and frame.
    let list = std::fs::read_to_string(kiln.join("crashes")).expect("the crashes list");
    let line: Vec<&str> = list.lines().last().unwrap().split('\t').collect();
    assert_eq!(&line[1..4], ["stopped", ID, "END_CLIENT_TICK"], "{list}");
    assert!(
        line[4].starts_with("SIGSEGV (0xb)") && line[4].contains("libcrash.so"),
        "{list}"
    );

    // No breadcrumb names a mod: Java's crash report's Java frames do, by the jar that has their class.
    let dead = stand_in();
    let pid = dead.id();
    std::fs::write(
        kiln.join("running"),
        format!("{}\n/bin/true\n", game.display()),
    )
    .unwrap();
    std::fs::write(kiln.join("last-call"), record(1, "server server_tick", "-")).unwrap();
    std::fs::write(
        kiln.join(format!("hs_err_pid{pid}.log")),
        "#  SIGBUS (0xa) at pc=0x1, pid=1, tid=2\n\nJava frames: (J=compiled Java code, j=interpreted, Vv=VM code)\nj  jdk.internal.misc.Unsafe.putLong(JJ)V+0 java.base@25\nJ 12 c2 kiln.test.crash.TestCrash.deeper(I)I (10 bytes) @ 0x1 [0x2+0x3]\nj  kiln.Host.step(Ljava/lang/String;)V+5\n\n",
    )
    .unwrap();
    let said = crash(dead, watcher(pid, &kiln));
    println!("{said}");
    assert!(
        said.contains(&format!(
            "Kiln Test Crash ({ID}) was running (server server_tick)"
        )),
        "{said}"
    );
    assert!(m::parse_crash_note(&std::fs::read_to_string(kiln.join("crashed")).unwrap()).is_some());
    std::fs::remove_file(kiln.join("crashed")).unwrap();

    // Only Kiln's call named, no mod: the note has no mod, and the window and the next start offer safe mode (D-134).
    let dead = stand_in();
    let pid = dead.id();
    std::fs::write(
        kiln.join("running"),
        format!("{}\n/bin/true\n", game.display()),
    )
    .unwrap();
    std::fs::write(kiln.join("last-call"), record(1, "tick", "-")).unwrap();
    let said = crash(dead, watcher(pid, &kiln));
    println!("{said}");
    assert!(
        said.contains("in Kiln's call tick") && said.contains("safe mode"),
        "{said}"
    );
    let crash_note = m::parse_crash_note(&std::fs::read_to_string(kiln.join("crashed")).unwrap())
        .expect("a note");
    assert_eq!(
        (crash_note.mod_id.as_str(), crash_note.call.as_str()),
        ("", "tick")
    );
    std::fs::remove_file(kiln.join("crashed")).unwrap();
    assert_eq!(
        std::fs::read_to_string(kiln.join("crashes"))
            .unwrap()
            .lines()
            .count(),
        3
    );

    // A game that closed cleanly (its note gone as it exited): nothing.
    let dead = stand_in();
    let pid = dead.id();
    std::fs::write(kiln.join("running"), "x\n").unwrap();
    let watching = watcher(pid, &kiln);
    std::thread::sleep(Duration::from_millis(800));
    std::fs::remove_file(kiln.join("running")).unwrap();
    let said = crash(dead, watching);
    assert_eq!(said, "", "a clean exit said something");
    assert!(!kiln.join("crashed").exists());

    // Killed with no mod and no call of Kiln's in progress: not a Java mod's, no window and no note.
    let dead = stand_in();
    let pid = dead.id();
    std::fs::write(kiln.join("running"), "x\n").unwrap();
    std::fs::write(kiln.join("last-call"), record(0, "tick", ID)).unwrap();
    let said = crash(dead, watcher(pid, &kiln));
    assert!(
        said.contains("no Java mod or call of Kiln's was running"),
        "{said}"
    );
    assert!(!kiln.join("crashed").exists());
    let _ = std::fs::remove_dir_all(&game);
}
