// The keyboard in a mod's text field (D-174): JEI's search box over the game's inventory, with real Java, as the game
// drives it, without the game. Characters, Backspace, Delete, the arrows, Home and End, Shift selecting with them,
// Control with the arrows and Backspace for words, Control+A, C, X and V with the game's clipboard, and a click
// dropping the selection. Needs a JDK, `sh tools/build-java.sh`, JEI's jar in KILN_MOD and the game's prepared resources in
// KILN_RESOURCES.
//
//   KILN_MOD=jei.jar KILN_RESOURCES=... cargo test -p horizon-kiln-mod --test jei_keys -- --ignored --nocapture

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
/// GLFW's modifier bits, as Horizon's screen hooks give them.
const SHIFT: u32 = 1;
const CONTROL: u32 = 2;

#[test]
#[ignore = "needs a JDK, sh tools/build-java.sh, JEI's jar in KILN_MOD and KILN_RESOURCES"]
fn keys_edit_jeis_search_box() {
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
    let game = std::env::temp_dir().join(format!("kiln-jei-keys-{}", std::process::id()));
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

    let mut snapshot = m::Snapshot {
        connected: true,
        in_world: true,
        paused: false,
        game_time: 100,
        dimension: "minecraft:overworld".into(),
        player: Some(m::PlayerSnapshot {
            uuid: 0x00c0_ffee,
            name: "Kiln".into(),
            dimension: "minecraft:overworld".into(),
            position: [0.5, 64.0, 0.5],
            health: 20.0,
            attributes: vec![("minecraft:max_health".into(), 20.0, 20.0)],
            ..Default::default()
        }),
        ..m::Snapshot::default()
    };
    snapshot.state.window = [1920, 1080, 3];
    snapshot.state.cursor = [20.0, 20.0];
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
    assert!(ready(&log), "JEI started");

    // The inventory open, JEI's list beside it.
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

    // The box's background sprite, whole: drawn nine-sliced (D-175), it comes as its pieces.
    let search = m::mod_screen(|s| {
        s.over_ops.iter().fold(None, |whole: Option<[f32; 4]>, op| match op {
            HudOp::Sprite { rect: [px, py, pw, ph], name, .. } if name.contains("search_background") => Some(match whole {
                None => [*px, *py, *px + *pw, *py + *ph],
                Some([x0, y0, x1, y1]) => [x0.min(*px), y0.min(*py), x1.max(*px + *pw), y1.max(*py + *ph)],
            }),
            _ => whole,
        })
    });
    let [x0, y0, x1, y1] = search.expect("JEI's search box drawn");
    let [x, y, w, h] = [x0, y0, x1 - x0, y1 - y0];
    let at = [x + w / 2.0, y + h / 2.0];
    snapshot.state.cursor = at;
    run(&snapshot, 6);
    let click = |kind: &str| vec![kind.to_owned(), m::num(at[0]), m::num(at[1]), "0".to_owned(), "0".to_owned()];
    m::mod_screen(|s| {
        s.over_input(click("press"), at);
        s.over_input(click("release"), at);
    });
    run(&snapshot, 9);
    assert!(m::mod_screen(|s| s.over_claims().1), "a click on JEI's search box gives it the keys");

    // What the box shows: the text drawn inside it (the caret's "_" left out), and whether a selection box is there.
    let shown = |ops: &[HudOp]| -> (String, bool) {
        let inside = |px: f32, py: f32| px >= x && px < x + w && py >= y - 2.0 && py < y + h;
        let text: String = ops
            .iter()
            .filter_map(|op| match op {
                HudOp::Text { x: tx, y: ty, text, .. } if inside(*tx, *ty) && text != "_" => Some(text.clone()),
                _ => None,
            })
            .collect::<Vec<_>>()
            .join("|");
        let selection = ops
            .iter()
            .any(|op| matches!(op, HudOp::Fill { x: fx, y: fy, argb, .. } if inside(*fx, *fy) && *argb == 0xFF00_00FF));
        (text, selection)
    };
    let mut steps: Vec<(String, String, bool)> = Vec::new();
    let mut act = |what: &str, inputs: Vec<Vec<String>>, run: &mut dyn FnMut(&m::Snapshot, u64), snapshot: &m::Snapshot| {
        for fields in inputs {
            let released = (fields[0] == "key").then(|| fields[1].clone());
            m::mod_screen(|s| s.over_input(fields, at));
            // The key's release, as the game's control socket gives it (no screen hook): Java hears it all the same.
            if let Some(key) = released {
                m::mod_screen(|s| s.released(&key));
            }
            run(snapshot, 2);
        }
        run(snapshot, 9);
        let (text, selection) = m::mod_screen(|s| shown(&s.over_ops));
        println!("== {what}: {text:?} selection {selection}");
        steps.push((what.to_owned(), text, selection));
    };
    let chars = |text: &str| -> Vec<Vec<String>> {
        text.chars().map(|c| vec!["char".to_owned(), (c as u32).to_string(), "0".to_owned()]).collect()
    };
    let key = |name: &str, mods: u32| vec!["key".to_owned(), format!("key.keyboard.{name}"), mods.to_string()];

    act("typed oak planks", chars("oak planks"), &mut run, &snapshot);
    act("control+a", vec![key("a", CONTROL)], &mut run, &snapshot);
    act("typed stick over the selection", chars("stick"), &mut run, &snapshot);
    act("shift+left twice", vec![key("left", SHIFT), key("left", SHIFT)], &mut run, &snapshot);
    act("backspace erases the selection", vec![key("backspace", 0)], &mut run, &snapshot);
    act("backspace", vec![key("backspace", 0)], &mut run, &snapshot);
    act("typed ick, home, delete", [chars("ick"), vec![key("home", 0), key("delete", 0)]].concat(), &mut run, &snapshot);
    act("end, typed ' oak'", [vec![key("end", 0)], chars(" oak")].concat(), &mut run, &snapshot);
    act("control+backspace", vec![key("backspace", CONTROL)], &mut run, &snapshot);
    act("control+left, shift+end", vec![key("left", CONTROL), key("end", SHIFT)], &mut run, &snapshot);
    act("control+x", vec![key("x", CONTROL)], &mut run, &snapshot);
    let cut = m::take_copied();
    // The paste: the game reads its clipboard when a paste key waits for Java (frame.rs), here by hand.
    m::mod_screen(|s| s.over_input(key("v", CONTROL), at));
    let wanted = m::mod_screen(|s| s.wants_clipboard());
    m::mod_screen(|s| {
        s.clipboard("diamond".to_owned());
        s.released("key.keyboard.v");
    });
    act("control+v (clipboard diamond)", vec![], &mut run, &snapshot);
    act("control+a, control+c", vec![key("a", CONTROL), key("c", CONTROL)], &mut run, &snapshot);
    let copied = m::take_copied();
    act("right, shift+home", vec![key("right", 0), key("home", SHIFT)], &mut run, &snapshot);
    // Two clicks on the box.
    m::mod_screen(|s| {
        for _ in 0..2 {
            s.over_input(click("press"), at);
            s.over_input(click("release"), at);
        }
    });
    act("two clicks", vec![], &mut run, &snapshot);
    println!("== cut {cut:?}, paste wanted {wanted}, copied {copied:?}");

    // Typing as the window types (D-178): each character's key goes down, its character comes, the key goes up, with
    // no frame between them, as GLFW's and winit's events arrive.
    let as_window = |text: &str| -> Vec<Vec<String>> {
        text.chars()
            .flat_map(|c| {
                let name = if c == ' ' { "space".to_owned() } else { c.to_string() };
                [
                    vec!["key".to_owned(), format!("key.keyboard.{name}"), "0".to_owned()],
                    vec!["char".to_owned(), (c as u32).to_string(), "0".to_owned()],
                    vec!["keyup".to_owned(), format!("key.keyboard.{name}"), "0".to_owned()],
                ]
            })
            .collect()
    };
    let type_now = |text: &str| {
        for fields in as_window(text) {
            m::mod_screen(|s| s.over_input(fields, at));
        }
    };
    let right = |kind: &str| vec![kind.to_owned(), m::num(at[0]), m::num(at[1]), "1".to_owned(), "0".to_owned()];
    let away = [GUI[0] / 2.0, GUI[1] / 2.0];
    let click_away = |kind: &str| vec![kind.to_owned(), m::num(away[0]), m::num(away[1]), "0".to_owned(), "0".to_owned()];
    // The box unfocused (a click on the game's window), then a right click on it and typing in the same frame.
    m::mod_screen(|s| {
        s.over_input(click_away("press"), away);
        s.over_input(click_away("release"), away);
    });
    run(&snapshot, 9);
    m::mod_screen(|s| {
        s.over_input(right("press"), at);
        s.over_input(right("release"), at);
    });
    type_now("crafting");
    act("unfocused, right click, typed crafting at once", vec![], &mut run, &snapshot);
    // The same with frames between the click and the typing, and a left click instead.
    m::mod_screen(|s| {
        s.over_input(click_away("press"), away);
        s.over_input(click_away("release"), away);
    });
    run(&snapshot, 9);
    m::mod_screen(|s| {
        s.over_input(right("press"), at);
        s.over_input(right("release"), at);
    });
    run(&snapshot, 9);
    type_now("table");
    act("unfocused, right click, frames, typed table", vec![], &mut run, &snapshot);
    m::mod_screen(|s| {
        s.over_input(click_away("press"), away);
        s.over_input(click_away("release"), away);
    });
    run(&snapshot, 9);
    m::mod_screen(|s| {
        s.over_input(click("press"), at);
        s.over_input(click("release"), at);
    });
    type_now(" oak");
    act("unfocused, left click, typed ' oak' at once", vec![], &mut run, &snapshot);

    // JEI's keys with their modifiers (D-178): Java says each as a combination, Ctrl+O's toggle of its list
    // `control+key.keyboard.o`; a combination bound on the page reaches JEI's own matching, modifiers and all.
    let combos: Vec<(String, String)> =
        m::mod_keys().into_iter().filter(|(_, n, _)| n.starts_with("key.jei.")).map(|(_, n, k)| (n, k)).collect();
    println!("== JEI's keys: {combos:?}");
    let combo_of = |name: &str| combos.iter().find(|(n, _)| n == name).map(|(_, k)| k.clone());
    m::mod_screen(|s| {
        s.over_input(click_away("press"), away);
        s.over_input(click_away("release"), away);
    });
    run(&snapshot, 9);
    let list_shown = || m::mod_screen(|s| s.over_ops.iter().any(|op| matches!(op, HudOp::Sprite { name, .. } if name.contains("search_background"))));
    let shown_before = list_shown();
    let mut toggled = Vec::new();
    for (bound, mods, key) in [("+key.keyboard.p", 0, "p"), ("shift+key.keyboard.p", 0, "p"), ("shift+key.keyboard.p", SHIFT, "p"),
        ("shift+key.keyboard.p", SHIFT, "p")]
    {
        snapshot.keys.insert("key.jei.toggleOverlay".into(), bound.into());
        run(&snapshot, 6);
        let before = list_shown();
        m::mod_screen(|s| {
            s.over_input(vec!["key".to_owned(), format!("key.keyboard.{key}"), mods.to_string()], at);
            s.released(&format!("key.keyboard.{key}"));
        });
        run(&snapshot, 9);
        println!("== bound {bound}, pressed {key} with {mods}: list {before} -> {}", list_shown());
        toggled.push(before != list_shown());
    }

    // Bookmarks per server (D-178): on a server the game names, an item bookmarked with A over JEI's list is kept in
    // JEI's folder for that server.
    snapshot.state.server = "play.example.org:25570".into();
    // The list shown again (Shift+P), the search emptied (a right click) and left (a click on the game's window), so the list shows everything.
    m::mod_screen(|s| {
        s.over_input(vec!["key".to_owned(), "key.keyboard.p".to_owned(), SHIFT.to_string()], at);
        s.released("key.keyboard.p");
    });
    run(&snapshot, 9);
    m::mod_screen(|s| {
        s.over_input(right("press"), at);
        s.over_input(right("release"), at);
    });
    run(&snapshot, 9);
    m::mod_screen(|s| {
        s.over_input(click_away("press"), away);
        s.over_input(click_away("release"), away);
    });
    run(&snapshot, 30);
    let item_at = m::mod_screen(|s| {
        s.over_ops.iter().find_map(|op| match op {
            HudOp::Item { x, y, size, .. } | HudOp::Stack { x, y, size, .. } if *x > WINDOW[0] + WINDOW[2] => {
                Some([x + size / 2.0, y + size / 2.0])
            }
            _ => None,
        })
    });
    let item_at = item_at.expect("an item in JEI's list");
    snapshot.state.cursor = item_at;
    run(&snapshot, 6);
    m::mod_screen(|s| {
        s.cursor(item_at);
        s.over_input(vec!["key".to_owned(), "key.keyboard.a".to_owned(), "0".to_owned()], item_at);
        s.released("key.keyboard.a");
    });
    // JEI saves its bookmarks a few seconds after the last change.
    run(&snapshot, 450);
    let saved: Vec<String> = walk(&kiln.join("config/jei"))
        .into_iter()
        .filter(|p| p.ends_with("bookmarks.json"))
        .map(|p| format!("{}: {}", p.strip_prefix(&kiln).unwrap().display(), std::fs::read_to_string(&p).unwrap_or_default().len()))
        .collect();
    println!("== bookmarks saved: {saved:?}");

    let text = |i: usize| steps[i].1.as_str();
    let selected = |i: usize| steps[i].2;
    assert_eq!(text(0), "oak planks", "characters typed");
    assert!(selected(1), "Control+A selects the text");
    assert_eq!(text(2), "stick", "what is typed replaces the selection");
    assert!(selected(3), "Shift with the arrows selects");
    assert_eq!(text(4), "sti", "Backspace erases the selection");
    assert_eq!(text(5), "st", "Backspace erases a character");
    assert_eq!(text(6), "tick", "Home, then Delete erases the first character");
    assert_eq!(text(7), "tick oak", "End, then typing at the end");
    assert_eq!(text(8), "tick ", "Control+Backspace erases a word");
    assert!(selected(9), "Control+Left, Shift+End select the word");
    assert_eq!(cut.as_deref(), Some("tick "), "Control+X puts the selection on the clipboard");
    assert_eq!(text(10), "", "Control+X erases it");
    assert!(wanted, "a paste key asks the game for its clipboard");
    assert_eq!(text(11), "diamond", "Control+V pastes the clipboard");
    assert_eq!(copied.as_deref(), Some("diamond"), "Control+A, Control+C copy the text");
    assert!(selected(13), "Shift+Home selects to the start");
    // A double click (Kiln hands the screen vanilla's flag) selects no word in JEI's box: JEI's Fabric events build
    // every click as a single one, as in vanilla's game.
    assert!(!selected(14), "a click on the box drops the selection");
    assert_eq!(text(15), "crafting", "a right click empties and focuses the box; what is typed at once is all there");
    assert_eq!(text(16), "table", "the same with frames between");
    assert_eq!(text(17), "table oak", "a left click focuses it; what is typed at once is all there");
    assert_eq!(combo_of("key.jei.toggleOverlay").as_deref(), Some("control+key.keyboard.o"), "JEI's Ctrl+O with its modifier");
    assert_eq!(combo_of("key.jei.showRecipe").as_deref(), Some("+key.keyboard.r"), "a JEI key with no modifier");
    assert!(shown_before, "JEI's list shown");
    assert_eq!(toggled, [true, false, true, true], "P alone toggles once bound alone; once bound with Shift only Shift+P does");
    assert!(
        saved.iter().any(|s| s.starts_with("config/jei/world/server/Minecraft Server (play.example.org 25570)/bookmarks.json")),
        "a bookmark on a server kept in JEI's folder for it: {saved:?}"
    );
    let _ = std::fs::remove_dir_all(&game);
}

/// Every file under `dir`, its folders' too.
fn walk(dir: &Path) -> Vec<std::path::PathBuf> {
    let mut files = Vec::new();
    for entry in std::fs::read_dir(dir).into_iter().flatten().flatten() {
        let path = entry.path();
        if path.is_dir() {
            files.extend(walk(&path));
        } else {
            files.push(path);
        }
    }
    files
}
