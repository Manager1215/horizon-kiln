// Kiln's overlay (D-113, #135) with real Java and the soft switch's test mod (kiln/test-mods/soft), as the game drives
// it: its key opens it over the game (a screen of Kiln's own, shown as a mod's is, D-094); the Mods tab lists the mod
// with its version, "running", "off now: yes" and the catalogue's word; its switch, clicked, turns the mod off in
// Options > Mods' switch and at once (its tick, HUD element and mixin stop); the Can turn off now tab, the search,
// "Open the mods' folder", and the key again closing it. And (D-134) its Crashes tab, and Restart Java now, which
// starts Java again (in a process of its own when it ran in the test's). Needs a JDK and the jars from
// `sh tools/build-java.sh`.
//
//   sh tools/build-java.sh && cargo test -p horizon-kiln-mod --test overlay -- --ignored --nocapture

use std::collections::BTreeSet;
use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;
use horizon_kiln_mod::game::kiln_jvm::HudOp;

const ID: &str = "kiln_test_soft";

/// The tests here take turns: Options > Mods' switches (`m::set_off`) are the whole process's, and each test flips them.
static TURN: std::sync::Mutex<()> = std::sync::Mutex::new(());

fn turn() -> std::sync::MutexGuard<'static, ()> {
    TURN.lock().unwrap_or_else(|e| e.into_inner())
}
const F7: &str = "key.keyboard.f7";

/// A game folder with Kiln, the libraries and the test mod; returns the game folder and the mod's data folder.
fn game_folder(name: &str) -> (PathBuf, PathBuf) {
    let built = Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln");
    let jar = built.join("test/kiln-test-soft.jar");
    assert!(jar.is_file(), "run sh tools/build-java.sh first");
    let game = std::env::temp_dir().join(format!("kiln-{name}-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&game);
    let kiln = game.join("kiln");
    std::fs::create_dir_all(kiln.join("mods")).unwrap();
    std::fs::create_dir_all(kiln.join("lib")).unwrap();
    std::fs::copy(built.join("kiln-runtime.jar"), kiln.join("kiln-runtime.jar")).unwrap();
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

/// Ticks at 20 a second for `ticks` ticks, or until `done`; the chat it made.
fn tick(data: &Path, ticks: usize, done: impl Fn(&[String]) -> bool) -> Vec<String> {
    let snapshot = in_world();
    let mut chat = Vec::new();
    for _ in 0..ticks {
        chat.extend(m::tick(data, || None, || vec![6; 128], snapshot.clone()));
        if done(&chat) {
            break;
        }
        std::thread::sleep(m::TICK);
    }
    chat
}

/// The texts the overlay draws now, each with where it starts.
fn texts() -> Vec<(f32, f32, String)> {
    m::mod_screen(|s| {
        s.ops
            .iter()
            .filter_map(|op| match op {
                HudOp::Text { x, y, text, .. } => Some((*x, *y, text.clone())),
                _ => None,
            })
            .collect()
    })
}

/// Ticks until the overlay draws a text `done` accepts (at most 5 s); all its texts then.
fn until(data: &Path, what: &str, done: impl Fn(&[(f32, f32, String)]) -> bool) -> Vec<(f32, f32, String)> {
    let deadline = Instant::now() + Duration::from_secs(5);
    loop {
        tick(data, 1, |_| false);
        let now = texts();
        if done(&now) {
            return now;
        }
        assert!(Instant::now() < deadline, "{what}: the overlay draws {now:?}");
    }
}

fn has(texts: &[(f32, f32, String)], text: &str) -> bool {
    texts.iter().any(|(_, _, t)| t == text)
}

/// A click at the text `text` the overlay draws (a button's label): pressed and released there.
fn click(texts: &[(f32, f32, String)], text: &str) {
    let (x, y, _) = texts
        .iter()
        .find(|(_, _, t)| t == text)
        .unwrap_or_else(|| panic!("no {text:?} in {texts:?}"));
    let (x, y) = (m::num(x + 2.0), m::num(y + 2.0));
    for kind in ["press", "release"] {
        assert!(m::mod_screen(|s| s.input(vec![kind.into(), x.clone(), y.clone(), "0".into(), "0".into()])));
    }
}

/// Ticks until the overlay asked the game something (at most 5 s): Java's answer comes a tick or two later.
fn asks(data: &Path) -> m::OverlayAsks {
    let deadline = Instant::now() + Duration::from_secs(5);
    loop {
        tick(data, 1, |_| false);
        let asks = m::take_overlay_asks();
        if !asks.switched.is_empty() || asks.open.is_some() || asks.restart_game || Instant::now() >= deadline {
            return asks;
        }
    }
}

fn input(fields: &[&str]) {
    assert!(m::mod_screen(|s| s.input(fields.iter().map(|f| (*f).to_owned()).collect())));
}

#[test]
#[ignore = "needs a JDK and sh tools/build-java.sh"]
fn the_overlay_lists_the_mods_and_its_switch_turns_one_off_now() {
    let _turn = turn();
    let (game, data) = game_folder("overlay");
    let kiln = game.join("kiln");
    m::set_off(BTreeSet::new());
    let chat = tick(&data, 400, |c| c.iter().any(|l| l.contains("Java mods running")));
    assert!(chat.iter().any(|l| l.starts_with("Kiln: 1 of 1 Java mods running")), "{chat:?}");
    let deadline = Instant::now() + Duration::from_secs(10);
    while counts().is_none_or(|(t, m)| t < 5 || m < 5) && Instant::now() < deadline {
        tick(&data, 1, |_| false);
    }
    let (ticks, mixin) = counts().expect("the mod's HUD is drawn while it is on");

    // The key opens it: Java says a screen opened, which the game shows over the world.
    assert!(m::toggle_overlay(&kiln, F7));
    let deadline = Instant::now() + Duration::from_secs(5);
    let mut wanted = None;
    while wanted.is_none() && Instant::now() < deadline {
        tick(&data, 1, |_| false);
        wanted = m::mod_screen(|s| s.take_wanted());
    }
    assert_eq!(wanted, Some(true), "the overlay didn't open");
    m::mod_screen(|s| s.show(m::Shown::Own));

    // The Mods tab: the mod, its version, its state, what a switch does now, the catalogue's word, its switch.
    let shown = until(&data, "the row", |t| has(t, "off now: yes") && has(t, "running"));
    for text in ["Kiln", "> Mods (1)", "Off now (1)", "Kiln Test Soft Switch 0.0.1", "not tried", "ON",
                 "Open the mods' folder", "Done"] {
        assert!(has(&shown, text), "no {text:?} in {shown:?}");
    }
    println!("Mods tab: Kiln Test Soft Switch 0.0.1, running, off now: yes, not tried, ON");

    // Its switch: off in Options > Mods' switch (the game's), then at once.
    click(&shown, "ON");
    let first = asks(&data);
    assert_eq!(first.switched, [(ID.to_owned(), false)]);
    // As the game's frame does: the switch off in Options > Mods, so Kiln's soft switch with the next tick.
    m::set_off(BTreeSet::from([ID.to_owned()]));
    let off = until(&data, "the switch off", |t| has(t, "OFF") && has(t, "off now"));
    assert!(has(&off, "Off now (0)"), "{off:?}");
    tick(&data, 3, |_| false);
    assert_eq!(counts(), None, "its HUD is still drawn: {:?}", m::hud_ops());
    tick(&data, 40, |_| false);
    println!("switch: off, the mod's tick, HUD and mixin stopped");

    // The Can turn off now tab: nothing running to stop now; Mods again.
    click(&off, "Off now (0)");
    let tab = until(&data, "the second tab", |t| has(t, "> Off now (0)"));
    assert!(has(&tab, "No mod here") && !has(&tab, "Kiln Test Soft Switch 0.0.1"), "{tab:?}");
    click(&tab, "Mods (1)");
    let mods = until(&data, "the Mods tab", |t| has(t, "> Mods (1)"));

    // On again from the overlay: it goes on from where it stopped.
    click(&mods, "OFF");
    assert_eq!(asks(&data).switched, [(ID.to_owned(), true)]);
    m::set_off(BTreeSet::new());
    let deadline = Instant::now() + Duration::from_secs(10);
    while counts().is_none() && Instant::now() < deadline {
        tick(&data, 1, |_| false);
    }
    let (after, mixin_after) = counts().expect("its HUD is drawn again");
    assert!(after > ticks && mixin_after > mixin, "{ticks}/{mixin} before, {after}/{mixin_after} after");
    println!("switch: on again, the mod goes on");

    // The search: a word no mod has leaves none; erased, the mod again.
    let now = until(&data, "on again", |t| has(t, "ON") && has(t, "running"));
    click(&now, "Search");
    for c in "zzz".chars() {
        input(&["char", &u32::from(c).to_string(), "0"]);
    }
    until(&data, "the search", |t| has(t, "No mod here"));
    for _ in 0..3 {
        input(&["key", "key.keyboard.backspace", "0"]);
    }
    let found = until(&data, "the search erased", |t| has(t, "Kiln Test Soft Switch 0.0.1"));
    println!("search: zzz finds none, erased finds the mod");

    // The mods' folder: the game shows it.
    click(&found, "Open the mods' folder");
    assert_eq!(asks(&data).open, Some(kiln.join("mods")));
    println!("Open the mods' folder: asked for {}", kiln.join("mods").display());

    // The key over it closes it, the search box focused or not.
    input(&["key", F7, "0"]);
    let deadline = Instant::now() + Duration::from_secs(5);
    let mut wanted = None;
    while wanted.is_none() && Instant::now() < deadline {
        tick(&data, 1, |_| false);
        wanted = m::mod_screen(|s| s.take_wanted());
    }
    assert_eq!(wanted, Some(false), "F7 didn't close the overlay");
    println!("F7: closed");

    let log = std::fs::read_to_string(game.join("kiln/java.log")).unwrap_or_default();
    assert!(!log.contains("Exception"), "java.log: {log}");
    let _ = std::fs::remove_dir_all(&game);
}

fn starts(texts: &[(f32, f32, String)], text: &str) -> bool {
    texts.iter().any(|(_, _, t)| t.starts_with(text))
}

/// Ticks until the overlay opened (at most 5 s), and shows it as the game does.
fn opened(data: &Path, kiln: &Path) {
    assert!(m::toggle_overlay(kiln, F7));
    let deadline = Instant::now() + Duration::from_secs(5);
    let mut wanted = None;
    while wanted.is_none() && Instant::now() < deadline {
        tick(data, 1, |_| false);
        wanted = m::mod_screen(|s| s.take_wanted());
    }
    assert_eq!(wanted, Some(true), "the overlay didn't open");
    m::mod_screen(|s| s.show(m::Shown::Own));
}

#[test]
#[ignore = "needs a JDK and sh tools/build-java.sh"]
fn the_crashes_tab_lists_the_crashes_and_restart_java_now_starts_java_again() {
    let _turn = turn();
    let (game, data) = game_folder("restart");
    let kiln = game.join("kiln");
    m::set_off(BTreeSet::new());
    // Two crashes recorded before (kiln/java/kiln/Crashes.java's format): the mod's, and one in no mod's call.
    std::fs::write(
        kiln.join("crashes"),
        format!(
            "1762000000000\tstopped\t-\tbridge tick\tSIGSEGV (0xb)\n1763000000000\tmemory\t{ID}\tEND_CLIENT_TICK\tjava.lang.OutOfMemoryError: Java heap space\n"
        ),
    )
    .unwrap();
    let chat = tick(&data, 400, |c| c.iter().any(|l| l.contains("Java mods running")));
    assert!(chat.iter().any(|l| l.starts_with("Kiln: 1 of 1 Java mods running")), "{chat:?}");
    let first_in_process = m::java_in_process();
    println!("Java {}", if first_in_process { "in the test's process" } else { "in a process of its own" });

    // The Crashes tab: newest first, the mod by its name, what happened, the call, what Java said.
    opened(&data, &kiln);
    let shown = until(&data, "the tabs", |t| has(t, "Crashes (2)"));
    click(&shown, "Crashes (2)");
    let crashes = until(&data, "the Crashes tab", |t| has(t, "> Crashes (2)"));
    for text in ["Kiln Test Soft Switch ran Java out of memory", "java.lang.OutOfMemoryError",
                 "The game stopped, in no Java mod's call",
                 "SIGSEGV (0xb)", "Restart Java now"] {
        assert!(starts(&crashes, text), "no {text:?} in {crashes:?}");
    }
    // The second line: when (this computer's time), then Kiln's call and the mod's switch.
    for text in [", in Kiln's call END_CLIENT_TICK; it is on", ", in Kiln's call bridge tick"] {
        assert!(crashes.iter().any(|(_, _, t)| t.starts_with("2025-11-") && t.ends_with(text)), "no {text:?} in {crashes:?}");
    }
    let newest = crashes.iter().position(|(_, _, t)| t.starts_with("Kiln Test Soft Switch"));
    let oldest = crashes.iter().position(|(_, _, t)| t.starts_with("The game stopped"));
    assert!(newest < oldest, "newest first: {crashes:?}");
    assert_eq!(first_in_process, starts(&crashes, "Restart Java now starts Java again"), "{crashes:?}");
    println!("Crashes tab: 2 crashes, newest first");

    // Restart Java now: the first click asks again and says what stays; the second starts Java again.
    let deadline = Instant::now() + Duration::from_secs(10);
    while counts().is_none() && Instant::now() < deadline {
        tick(&data, 1, |_| false);
    }
    let (ticks, _) = counts().expect("the mod's HUD is drawn");
    click(&crashes, "Restart Java now");
    let armed = until(&data, "armed", |t| has(t, "Click again to restart"));
    let note = if first_in_process { "Java starts again in a process of its own" } else { "Java starts again" };
    assert!(starts(&armed, note), "{armed:?}");
    click(&armed, "Click again to restart");
    let chat = tick(&data, 400, |c| c.iter().any(|l| l.contains("Java mods running")));
    assert!(chat.iter().any(|l| l.starts_with("Kiln: Java starts again")), "{chat:?}");
    assert!(chat.iter().any(|l| l.starts_with("Kiln: 1 of 1 Java mods running")), "{chat:?}");
    assert!(!m::java_in_process(), "Java started again in a process of its own");
    assert_eq!(m::restarted_from_process(), first_in_process);
    if first_in_process {
        // The banner, once: the game goes on, the first Java's memory as it measured itself, nothing broken.
        let deadline = Instant::now() + Duration::from_secs(5);
        while m::mod_screen(|s| s.take_wanted()) != Some(true) {
            assert!(Instant::now() < deadline, "no banner");
            tick(&data, 1, |_| false);
        }
        m::mod_screen(|s| s.show(m::Shown::Own));
        let banner = until(&data, "the banner", |t| has(t, "Java restarted, the game goes on"));
        let text: String = banner.iter().map(|(_, _, t)| t.as_str()).collect::<Vec<_>>().join(" ");
        for words in ["Nothing is broken.", "stays in the game's memory until you close the game: at least",
                      "the game only, not the computer"] {
            assert!(text.contains(words), "no {words:?} in {text}");
        }
        let mb: u64 = text.split("at least ").nth(1).and_then(|r| r.split(' ').next()).and_then(|n| n.parse().ok())
            .expect("a size");
        assert!(mb > 16, "{mb} MB");
        println!("banner: the first Java holds at least {mb} MB, as it measured itself");
        // In a world, "Restart the game" asks again first; then the game is asked to start itself again.
        click(&banner, "Restart the game");
        let asking = until(&data, "asking", |t| has(t, "Restart now"));
        assert!(asking.iter().any(|(_, _, t)| t.contains("may be lost")), "{asking:?}");
        click(&asking, "Restart now");
        assert!(asks(&data).restart_game, "the game wasn't asked to start again");
        let back = until(&data, "back", |t| has(t, "Back"));
        click(&back, "Back");
        let banner = until(&data, "the banner again", |t| has(t, "Got it"));
        click(&banner, "Got it");
        let deadline = Instant::now() + Duration::from_secs(5);
        while m::mod_screen(|s| s.take_wanted()) != Some(false) {
            assert!(Instant::now() < deadline, "Got it didn't close the banner");
            tick(&data, 1, |_| false);
        }
        println!("banner: Restart the game asks first in a world; Got it closes it");
    } else {
        // The overlay closed with the Java that drew it.
        assert_eq!(m::mod_screen(|s| s.take_wanted()), Some(false));
    }
    m::mod_screen(|s| s.show(m::Shown::No));
    if first_in_process {
        assert!(kiln.join("java-restarted.log").is_file(), "the restarted Java's log of its own");
    }
    // The mod runs again in the new Java, from its start.
    let deadline = Instant::now() + Duration::from_secs(10);
    while counts().is_none_or(|(t, _)| t < 5) && Instant::now() < deadline {
        tick(&data, 1, |_| false);
    }
    let (again, _) = counts().expect("the mod's HUD is drawn by the new Java");
    println!("Restart Java now: Java started again, the mod's ticks {ticks} before, {again} in the new Java");

    // The overlay of the new Java: the Crashes tab says the first Java's memory stays taken.
    opened(&data, &kiln);
    let shown = until(&data, "the tabs again", |t| has(t, "Crashes (2)"));
    click(&shown, "Crashes (2)");
    let crashes = until(&data, "the Crashes tab again", |t| has(t, "> Crashes (2)"));
    assert_eq!(first_in_process, starts(&crashes, "Java was started again in this game"), "{crashes:?}");
    assert_eq!(first_in_process, has(&crashes, "2 Javas: restart the game when it suits you"), "{crashes:?}");
    let _ = std::fs::remove_dir_all(&game);
}

/// This process's threads now: id, name, CPU time used (user and system, in clock ticks). Linux only.
fn threads() -> Vec<(u32, String, u64)> {
    let mut all = Vec::new();
    for entry in std::fs::read_dir("/proc/self/task").into_iter().flatten().flatten() {
        let Ok(tid) = entry.file_name().to_string_lossy().parse::<u32>() else { continue };
        let name = std::fs::read_to_string(entry.path().join("comm")).unwrap_or_default().trim().to_owned();
        let stat = std::fs::read_to_string(entry.path().join("stat")).unwrap_or_default();
        // After the name's closing parenthesis: field 14 (utime) and 15 (stime) of stat are the 12th and 13th here.
        let rest: Vec<&str> = stat.rsplit_once(')').map_or("", |(_, r)| r).split_whitespace().collect();
        let cpu = rest.get(11).and_then(|u| u.parse::<u64>().ok()).unwrap_or(0)
            + rest.get(12).and_then(|s| s.parse::<u64>().ok()).unwrap_or(0);
        all.push((tid, name, cpu));
    }
    all
}

/// This process's resident memory, in MiB.
fn rss_mib() -> u64 {
    let statm = std::fs::read_to_string("/proc/self/statm").unwrap_or_default();
    let pages: u64 = statm.split_whitespace().nth(1).and_then(|p| p.parse().ok()).unwrap_or(0);
    pages * 4096 / (1 << 20)
}

/// The CPU the threads in `ids` used over `secs` seconds of ticking, in clock ticks (100 a second), with each busy
/// thread's name and share.
fn cpu_over(data: &Path, ids: &BTreeSet<u32>, secs: u64) -> (u64, Vec<(String, u64)>) {
    let before: std::collections::BTreeMap<u32, (String, u64)> =
        threads().into_iter().filter(|t| ids.contains(&t.0)).map(|(id, n, c)| (id, (n, c))).collect();
    tick(data, (secs * 20) as usize, |_| false);
    let mut busy = Vec::new();
    let mut total = 0;
    for (id, name, cpu) in threads() {
        if let Some((_, was)) = before.get(&id) {
            total += cpu - was;
            if cpu > *was {
                busy.push((name, cpu - was));
            }
        }
    }
    busy.sort_by(|a, b| b.1.cmp(&a.1));
    (total, busy)
}

/// D-134's costs, measured: the Java in the test's process before and after Restart Java now moves Java out of it
/// (memory, CPU, which of its threads still run), and a mod turned off before the restart stays off. Run with
/// `KILN_JVM=inprocess` on Linux; `KILN_RESTART_MODS` may name a folder of more mods to run beside the test's (they keep
/// a new Java running when the test's mod is off).
#[test]
#[ignore = "needs a JDK, sh tools/build-java.sh and Linux; measures, KILN_JVM=inprocess"]
fn restart_java_now_costs_and_leaves_an_off_mod_off() {
    let _turn = turn();
    if !cfg!(target_os = "linux") || std::env::var("KILN_JVM").as_deref() != Ok("inprocess") {
        println!("skipped: Linux with KILN_JVM=inprocess only");
        return;
    }
    let (game, data) = game_folder("restart-cost");
    let kiln = game.join("kiln");
    let more: Vec<PathBuf> = std::env::var_os("KILN_RESTART_MODS").map(|d| m::jars(Path::new(&d))).unwrap_or_default();
    for jar in &more {
        std::fs::copy(jar, kiln.join("mods").join(jar.file_name().unwrap())).unwrap();
    }
    let all = 1 + more.len();
    m::set_off(BTreeSet::new());
    let me = threads().into_iter().map(|t| t.0).collect::<BTreeSet<u32>>();
    let rss_before = rss_mib();
    let chat = tick(&data, 600, |c| c.iter().any(|l| l.contains("Java mods running")));
    assert!(chat.iter().any(|l| l.contains("Java mods running")), "{chat:?}");
    println!("{}", chat.iter().find(|l| l.contains("Java mods running")).unwrap());
    assert!(m::java_in_process());
    tick(&data, 200, |_| false);
    let java: BTreeSet<u32> = threads().into_iter().map(|t| t.0).filter(|t| !me.contains(t)).collect();
    let rss_running = rss_mib();
    let (cpu_running, busy_running) = cpu_over(&data, &java, 20);
    println!("before: RSS {rss_before} MiB without Java, {rss_running} MiB with Java running ({} threads of Java's)", java.len());
    println!("before: Java's threads used {} ms of CPU in 20 s of ticking; busiest {:?}", cpu_running * 10,
        &busy_running[..busy_running.len().min(6)]);

    // The test's mod off now, so the new Java must leave it out; the others (if any) keep a new Java running.
    m::set_off(BTreeSet::from([ID.to_owned()]));
    tick(&data, 20, |_| false);
    opened(&data, &kiln);
    let shown = until(&data, "the overlay", |t| has(t, "Restart Java now"));
    click(&shown, "Restart Java now");
    let armed = until(&data, "armed", |t| has(t, "Click again to restart"));
    click(&armed, "Click again to restart");
    let chat = tick(&data, 600, |c| c.iter().any(|l| l.contains("Java mods running") || l.contains("Java starts again"))
        && (more.is_empty() || c.iter().any(|l| l.contains("Java mods running"))));
    println!("restart: {chat:?}");
    assert!(chat.iter().any(|l| l.starts_with("Kiln: Java starts again, in a process of its own")), "{chat:?}");
    if !more.is_empty() {
        let line = format!("Kiln: {} of {} Java mods running", all - 1, all - 1);
        assert!(chat.iter().any(|l| l.starts_with(&line)), "the new Java left the mod out: {chat:?}");
    }
    tick(&data, 200, |_| false);
    assert_eq!(counts(), None, "the mod turned off before the restart draws again: {:?}", m::hud_ops());
    let old: BTreeSet<u32> = threads().into_iter().map(|t| t.0).filter(|t| java.contains(t)).collect();
    let rss_after = rss_mib();
    let (cpu_after, busy_after) = cpu_over(&data, &old, 30);
    // What the banner says the old Java holds, against what this process's resident memory grew by with it.
    opened(&data, &kiln);
    let shown = until(&data, "the new overlay", |t| has(t, "Done"));
    click(&shown, "Crashes (0)");
    let tab = until(&data, "the Crashes tab", |t| has(t, "> Crashes (0)"));
    let text: String = tab.iter().map(|(_, _, t)| t.as_str()).collect::<Vec<_>>().join(" ");
    let said: u64 = text.split("(at least ").nth(1).and_then(|r| r.split(' ').next()).and_then(|n| n.parse().ok())
        .unwrap_or_else(|| panic!("no size in {text}"));
    println!("after: the overlay says the old Java holds at least {said} MB; the process grew by {} MiB with it",
        rss_running - rss_before);
    assert!(said <= (rss_running - rss_before) * 1049 / 1000 + 1, "the banner says more than the Java took");
    let names: Vec<String> = threads().into_iter().filter(|t| old.contains(&t.0)).map(|t| t.1).collect();
    println!("after: RSS {rss_after} MiB in the test's process (the old Java stays; the new one is a process of its own)");
    println!("after: the old Java's threads still there: {} of {}: {names:?}", old.len(), java.len());
    println!("after: they used {} ms of CPU in 30 s of ticking; busy {busy_after:?}", cpu_after * 10);
    assert_eq!(counts(), None, "the mod turned off before the restart draws again");
    let _ = std::fs::remove_dir_all(&game);
}
