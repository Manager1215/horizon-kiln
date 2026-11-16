// A drag with the mouse reaches JEI (D-167, #203), with real Java, as the game drives it, without the game: two items
// of JEI's list bookmarked by A, then the first bookmark dragged onto the second over a second of frames, as a hand
// drags (JEI times a drag; the tries' horizon-ctl drags in one frame), and dropped there: JEI's bookmark list changes
// its order. Needs a JDK, `sh tools/build-java.sh`, JEI's jar in KILN_MOD and the game's prepared resources in
// KILN_RESOURCES.
//
//   KILN_MOD=jei.jar KILN_RESOURCES=... cargo test -p horizon-kiln-mod --test jei_drag -- --ignored --nocapture

use std::path::Path;
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;
use horizon_kiln_mod::game::kiln_jvm::HudOp;

struct Printed;

impl log::Log for Printed {
    fn enabled(&self, _: &log::Metadata) -> bool {
        true
    }

    fn log(&self, record: &log::Record) {
        if record.level() <= log::Level::Info {
            println!("  [{}] {}", record.level(), record.args());
        }
    }

    fn flush(&self) {}
}

/// The GUI's size: a 1920 by 1080 window at GUI scale 3.
const GUI: [f32; 2] = [640.0, 360.0];
/// Where the game draws its inventory's window: 176 by 166, in the middle.
const WINDOW: [f32; 4] = [232.0, 97.0, 176.0, 166.0];
const PLAYER: u128 = 0x00c0_ffee;

fn worn(item: &str, count: u32) -> m::Worn {
    m::Worn { slot: "inventory", item: item.into(), count, damage: 0, max_damage: 0 }
}

#[test]
#[ignore = "needs a JDK, sh tools/build-java.sh, JEI's jar in KILN_MOD and KILN_RESOURCES"]
fn jei_takes_a_drag_of_its_bookmarks() {
    let Some(mods) = std::env::var_os("KILN_MOD") else {
        println!("KILN_MOD names no jar: nothing to run");
        return;
    };
    let _ = log::set_logger(&Printed).map(|()| log::set_max_level(log::LevelFilter::Info));
    let built = Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln");
    assert!(built.join("kiln-runtime.jar").is_file(), "run sh tools/build-java.sh first");
    if let Some(resources) = std::env::var_os("KILN_RESOURCES") {
        horizon_kiln_mod::game::kiln_jvm::set_resources(Path::new(&resources));
    }
    let game = std::env::temp_dir().join(format!("kiln-jei-drag-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&game);
    let kiln = game.join("kiln");
    std::fs::create_dir_all(kiln.join("mods")).unwrap();
    std::fs::create_dir_all(kiln.join("lib")).unwrap();
    std::fs::copy(built.join("kiln-runtime.jar"), kiln.join("kiln-runtime.jar")).unwrap();
    for jar in m::jars(&built.join("lib")) {
        std::fs::copy(&jar, kiln.join("lib").join(jar.file_name().unwrap())).unwrap();
    }
    for jar in std::env::split_paths(&mods) {
        std::fs::copy(&jar, kiln.join("mods").join(jar.file_name().unwrap())).unwrap();
    }
    let data = game.join("horizon-mods/.horizon-mod-data/kiln");
    std::fs::create_dir_all(&data).unwrap();

    let player = m::PlayerSnapshot {
        uuid: PLAYER,
        name: "Kiln".into(),
        dimension: "minecraft:overworld".into(),
        position: [0.5, 64.0, 0.5],
        health: 20.0,
        attributes: vec![("minecraft:max_health".into(), 20.0, 20.0)],
        ..Default::default()
    };
    let mut snapshot = m::Snapshot {
        connected: true,
        in_world: true,
        paused: false,
        game_time: 100,
        dimension: "minecraft:overworld".into(),
        player: Some(player.clone()),
        ..m::Snapshot::default()
    };
    snapshot.state.window = [1920, 1080, 3];
    snapshot.state.cursor = [20.0, 20.0];
    // The player's hotbar: eight planks and a stick in the first two slots.
    snapshot.state.inventory = vec![(0, worn("minecraft:oak_planks", 8)), (1, worn("minecraft:stick", 3))];
    m::set_gui(GUI[0], GUI[1]);

    let wait: u64 = std::env::var("KILN_WAIT").ok().and_then(|s| s.parse().ok()).unwrap_or(30);
    let log = kiln.join("java.log");
    let started = Instant::now();
    let ready = |log: &Path| std::fs::read_to_string(log).is_ok_and(|l| l.contains("Starting JEI took"));
    let mut frame = 0u64;
    let mut run = |snapshot: &m::Snapshot, frames: u64| {
        for _ in 0..frames {
            if frame % 3 == 0 {
                for line in m::tick(&data, || None, || vec![6; 128], snapshot.clone()) {
                    println!("  chat: {line}");
                }
            }
            m::frame(|| snapshot.clone());
            frame += 1;
            std::thread::sleep(Duration::from_millis(16));
        }
    };
    while !ready(&log) && started.elapsed() < Duration::from_secs(wait) {
        run(&snapshot, 30);
    }
    println!("== JEI started: {} after {:?}", ready(&log), started.elapsed());

    snapshot.state.screen = "inventory".into();
    snapshot.state.cursor = [600.0, 150.0];
    run(&snapshot, 6);
    let view = vec![
        "inventory".to_owned(),
        "-".to_owned(),
        m::num(WINDOW[0]),
        m::num(WINDOW[1]),
        m::num(WINDOW[2]),
        m::num(WINDOW[3]),
        "46".to_owned(),
    ];
    m::mod_screen(|s| s.game_screen(Some(view.clone())));
    run(&snapshot, 60);
    let right = WINDOW[0] + WINDOW[2];

    // JEI's search bar: a click gives it the keys; "crafting table" filters the list to the table.
    let search = m::mod_screen(|s| {
        s.over_ops.iter().find_map(|op| match op {
            HudOp::Sprite { rect, name, .. } if name.contains("search_background") => Some(*rect),
            _ => None,
        })
    });
    let [x, y, w, h] = search.expect("JEI's search bar");
    let at = [x + w / 2.0, y + h / 2.0];
    snapshot.state.cursor = at;
    run(&snapshot, 6);
    let fields = |kind: &str, at: [f32; 2]| vec![kind.to_owned(), m::num(at[0]), m::num(at[1]), "0".to_owned(), "0".to_owned()];
    m::mod_screen(|s| {
        s.over_input(fields("press", at), at);
        s.over_input(fields("release", at), at);
    });
    run(&snapshot, 9);
    for c in "planks".chars() {
        m::mod_screen(|s| s.over_input(vec!["char".into(), (c as u32).to_string(), "0".into()], at));
        run(&snapshot, 3);
    }
    run(&snapshot, 12);
    run(&snapshot, 12);
    let away = [WINDOW[0] + 20.0, WINDOW[1] + 20.0];
    snapshot.state.cursor = away;
    m::mod_screen(|s| {
        s.over_input(fields("press", away), away);
        s.over_input(fields("release", away), away);
    });
    run(&snapshot, 9);

    // The items JEI drew: its list right of the window, its bookmarks left of it.
    let items = |left: bool| -> Vec<([f32; 2], String)> {
        let mut found: Vec<([f32; 2], String)> = m::mod_screen(|s| {
            s.over_ops
                .iter()
                .chain(s.ops.iter())
                .filter_map(|op| match op {
                    HudOp::Item { x, y, size, item } if (*x + size <= WINDOW[0]) == left && (left || *x >= right) => {
                        Some(([x + size / 2.0, y + size / 2.0], item.clone()))
                    }
                    HudOp::Stack { x, y, size, snbt } if (*x + size <= WINDOW[0]) == left && (left || *x >= right) => {
                        Some(([x + size / 2.0, y + size / 2.0], snbt.clone()))
                    }
                    _ => None,
                })
                .collect()
        });
        found.sort_by(|a, b| a.0[1].total_cmp(&b.0[1]).then(a.0[0].total_cmp(&b.0[0])));
        found.dedup_by(|a, b| a.1 == b.1);
        found
    };
    let list = items(false);
    println!("== JEI's list: {} items, first {:?}", list.len(), list.iter().take(3).collect::<Vec<_>>());
    assert!(list.len() >= 2, "JEI's list shows the planks");
    for (at, item) in list.iter().take(2) {
        snapshot.state.cursor = *at;
        m::mod_screen(|s| s.cursor(*at));
        run(&snapshot, 9);
        m::mod_screen(|s| s.over_input(vec!["key".into(), "key.keyboard.a".into(), "0".into()], *at));
        run(&snapshot, 9);
        println!("  A over {item} at {at:?}");
    }
    run(&snapshot, 12);
    let before = items(true);
    println!("== bookmarks before: {:?}", before.iter().map(|b| &b.1).collect::<Vec<_>>());
    assert_eq!(before.len(), 2, "two bookmarks");

    // The drag: pressed on the first bookmark, moved a step a frame for about a second onto the second, released there.
    let (from, to) = (before[0].0, before[1].0);
    let drag = |kind: &str, at: [f32; 2], d: [f32; 2]| {
        let mut f = vec![kind.to_owned(), m::num(at[0]), m::num(at[1]), "0".to_owned()];
        if kind == "drag" {
            f.extend([m::num(d[0]), m::num(d[1])]);
        }
        f.push("0".to_owned());
        f
    };
    snapshot.state.cursor = from;
    m::mod_screen(|s| s.cursor(from));
    run(&snapshot, 6);
    m::mod_screen(|s| s.over_input(drag("press", from, [0.0, 0.0]), from));
    let steps = 30;
    let mut at = from;
    for i in 1..=steps {
        let t = i as f32 / steps as f32;
        let next = [from[0] + (to[0] + 4.0 - from[0]) * t, from[1] + (to[1] - from[1]) * t];
        let d = [next[0] - at[0], next[1] - at[1]];
        at = next;
        snapshot.state.cursor = at;
        m::mod_screen(|s| {
            s.cursor(at);
            s.over_input(drag("drag", at, d), at);
        });
        run(&snapshot, 2);
    }
    run(&snapshot, 6);
    m::mod_screen(|s| s.over_input(drag("release", at, [0.0, 0.0]), at));
    run(&snapshot, 15);
    snapshot.state.cursor = away;
    m::mod_screen(|s| s.cursor(away));
    run(&snapshot, 9);
    let after = items(true);
    println!("== bookmarks after: {:?}", after.iter().map(|b| &b.1).collect::<Vec<_>>());

    println!("== kiln/java.log");
    let text = std::fs::read_to_string(&log).unwrap_or_default();
    for line in text.lines().filter(|l| l.contains("Exception") || l.contains("Error")).take(20) {
        println!("{line}");
    }
    let names = |v: &[([f32; 2], String)]| v.iter().map(|b| b.1.clone()).collect::<Vec<_>>();
    let mut sorted_before = names(&before);
    let mut sorted_after = names(&after);
    assert_ne!(names(&before), names(&after), "the drag moved the first bookmark past the second");
    sorted_before.sort();
    sorted_after.sort();
    assert_eq!(sorted_before, sorted_after, "the same two bookmarks");
    let _ = std::fs::remove_dir_all(&game);
}
