// JEI's overlay over the game's inventory screen (D-156, #203), with real Java, as the game drives it, without the
// game: the window's game opens its inventory, Horizon's screen hooks say where it drew the window, and JEI draws its
// item list beside it, which takes the mouse there from the game. Needs a JDK, `sh tools/build-java.sh`, JEI's jar in
// KILN_MOD and the game's prepared resources in KILN_RESOURCES (JEI's list is the game's items).
//
//   KILN_MOD=jei.jar KILN_RESOURCES=... cargo test -p horizon-kiln-mod --test jei_overlay -- --ignored --nocapture

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

#[test]
#[ignore = "needs a JDK, sh tools/build-java.sh, JEI's jar in KILN_MOD and KILN_RESOURCES"]
fn jei_draws_its_list_over_the_inventory() {
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
    let game = std::env::temp_dir().join(format!("kiln-jei-overlay-{}", std::process::id()));
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

    // In the world first, until JEI's runtime started (it says so in its log), at most KILN_WAIT seconds (30).
    let wait: u64 = std::env::var("KILN_WAIT").ok().and_then(|s| s.parse().ok()).unwrap_or(30);
    let log = kiln.join("java.log");
    let started = Instant::now();
    let ready = |log: &Path| {
        std::fs::read_to_string(log).is_ok_and(|l| l.contains("Starting JEI took"))
    };
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

    // The player opens the inventory: the tick says so, the screen hooks say where the window is.
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

    let (ops, claims, keys) = m::mod_screen(|s| {
        let (claims, keys) = s.over_claims();
        (s.over_ops.clone(), claims.to_vec(), keys)
    });
    let items = ops.iter().filter(|op| matches!(op, HudOp::Item { .. } | HudOp::Stack { .. })).count();
    println!("== over the inventory: {} ops, {items} items, claims {claims:?}, keys {keys}", ops.len());
    // Every op but the items past the first ten.
    let mut shown = 0;
    for op in &ops {
        if matches!(op, HudOp::Item { .. } | HudOp::Stack { .. }) {
            shown += 1;
            if shown > 10 {
                continue;
            }
        }
        match op {
            HudOp::Text { x, y, argb, text, .. } => println!("  text at {x},{y} {argb:08x}: {text}"),
            HudOp::Fill { x, y, w, h, argb } => println!("  box at {x},{y} {w}x{h} {argb:08x}"),
            HudOp::Item { x, y, size, item } => println!("  item at {x},{y} {size}px: {item}"),
            HudOp::Stack { x, y, size, snbt } => println!("  stack at {x},{y} {size}px: {snbt}"),
            HudOp::Styled { x, y, text, .. } => println!("  styled text at {x},{y}: {text}"),
            HudOp::Clip(rect) => println!("  cut to {rect:?}"),
            HudOp::Sprite { rect, name, .. } => println!("  sprite {name} at {rect:?}"),
            HudOp::Entity { rect, id, .. } => println!("  entity {id} at {rect:?}"),
        }
    }

    // A click on the list is the mods' (the game leaves it), one in the window the game's.
    let right = WINDOW[0] + WINDOW[2];
    let on_list = claims.iter().find(|r| r[0] >= right).map(|r| [r[0] + r[2] / 2.0, r[1] + r[3] / 2.0]);
    let clicked = on_list.map(|at| {
        let fields = |kind: &str| vec![kind.to_owned(), m::num(at[0]), m::num(at[1]), "0".to_owned(), "0".to_owned()];
        let claimed = m::mod_screen(|s| s.over_input(fields("press"), at));
        let released = m::mod_screen(|s| s.over_input(fields("release"), at));
        (claimed, released)
    });
    let in_window = [WINDOW[0] + 10.0, WINDOW[1] + 10.0];
    let game_click = m::mod_screen(|s| {
        s.over_input(
            vec!["press".into(), m::num(in_window[0]), m::num(in_window[1]), "0".into(), "0".into()],
            in_window,
        )
    });
    run(&snapshot, 12);
    println!("== click on the list at {on_list:?}: {clicked:?}; in the window: {game_click}");
    // That click opened JEI's recipes of the item there, maybe: shut it, the next steps open it themselves.
    run(&snapshot, 12);
    let _ = recipe_screen(&mut run, &snapshot);
    if m::mod_screen(|s| s.shown) != m::Shown::No {
        m::mod_screen(|s| s.input(vec!["key".into(), "key.keyboard.escape".into(), "0".into()]));
        run(&snapshot, 12);
        m::mod_screen(|s| {
            if s.take_wanted() == Some(false) {
                s.show(m::Shown::No);
            }
        });
    }

    // JEI's search bar: a click there gives it the keys, and what is typed filters the list (planks: each has a
    // recipe and uses), so the items the next steps point at are known.
    let search = m::mod_screen(|s| {
        s.over_ops.iter().find_map(|op| match op {
            HudOp::Sprite { rect, name, .. } if name.contains("search_background") => Some(*rect),
            _ => None,
        })
    });
    let mut typed = false;
    if let Some([x, y, w, h]) = search {
        let at = [x + w / 2.0, y + h / 2.0];
        snapshot.state.cursor = at;
        run(&snapshot, 6);
        let fields = |kind: &str| vec![kind.to_owned(), m::num(at[0]), m::num(at[1]), "0".to_owned(), "0".to_owned()];
        m::mod_screen(|s| {
            s.over_input(fields("press"), at);
            s.over_input(fields("release"), at);
        });
        run(&snapshot, 9);
        typed = m::mod_screen(|s| s.over_claims().1);
        for c in "planks".chars() {
            m::mod_screen(|s| s.over_input(vec!["char".into(), (c as u32).to_string(), "0".into()], at));
            run(&snapshot, 3);
        }
        run(&snapshot, 12);
        // The keys back to the game: Escape over the inventory is the game's (it would close it), so a click
        // in the window drops the field's focus.
        let window = [WINDOW[0] + 20.0, WINDOW[1] + 20.0];
        let fields = |kind: &str| {
            vec![kind.to_owned(), m::num(window[0]), m::num(window[1]), "0".to_owned(), "0".to_owned()]
        };
        m::mod_screen(|s| {
            s.over_input(fields("press"), window);
            s.over_input(fields("release"), window);
        });
        run(&snapshot, 9);
    }
    let searched = m::mod_screen(|s| texts(&s.over_ops));
    println!("== typed into JEI's search ({typed}): {searched:?}");

    // The pointer over an item of the list: JEI draws its tooltip there (the item's name in white, its mod's in blue).
    let list: Vec<[f32; 3]> = m::mod_screen(|s| {
        s.over_ops
            .iter()
            .filter_map(|op| match op {
                HudOp::Item { x, y, size, .. } if *x >= right => Some([*x, *y, *size]),
                _ => None,
            })
            .collect()
    });
    let item_at = |i: usize| list.get(i).map(|[x, y, size]| [x + size / 2.0, y + size / 2.0]);
    let first = item_at(0).expect("an item in JEI's list");
    snapshot.state.cursor = first;
    run(&snapshot, 12);
    let hovered = m::mod_screen(|s| texts(&s.over_ops));
    println!("== the pointer at {first:?} over the list: texts {hovered:?}");
    let tooltip = hovered.iter().any(|(at, text)| at[0] >= first[0] && at[1] < first[1] + 4.0 && text.contains("Planks"));
    // Away from the list: no tooltip.
    snapshot.state.cursor = [WINDOW[0] - 100.0, 20.0];
    run(&snapshot, 12);
    let away = m::mod_screen(|s| texts(&s.over_ops));

    // A left click on the item: JEI's recipe screen for it opens, over the inventory.
    snapshot.state.cursor = first;
    run(&snapshot, 6);
    let click = |kind: &str, at: [f32; 2], button: &str| {
        vec![kind.to_owned(), m::num(at[0]), m::num(at[1]), button.to_owned(), "0".to_owned()]
    };
    m::mod_screen(|s| {
        s.over_input(click("press", first, "0"), first);
        s.over_input(click("release", first, "0"), first);
    });
    let recipes = recipe_screen(&mut run, &snapshot);
    println!("== after a click on {first:?}: {}", describe(&recipes));
    // The pointer over an ingredient of the recipe: its tooltip, as JEI writes it (with its key hints).
    let slot = m::mod_screen(|s| {
        s.ops.iter().find_map(|op| match op {
            HudOp::Item { x, y, size, .. } if *x < WINDOW[0] + WINDOW[2] => Some([x + size / 2.0, y + size / 2.0]),
            _ => None,
        })
    });
    let mut slot_tooltip = Vec::new();
    if let Some(at) = slot {
        let before: Vec<String> = m::mod_screen(|s| texts(&s.ops)).into_iter().map(|(_, t)| t).collect();
        snapshot.state.cursor = at;
        m::mod_screen(|s| s.cursor(at));
        run(&snapshot, 12);
        slot_tooltip = m::mod_screen(|s| texts(&s.ops)).into_iter().filter(|(_, t)| !before.contains(t)).collect();
        println!("== the pointer over the recipe's ingredient at {at:?}: new texts {slot_tooltip:?}");
    }
    let shut = close(&mut run, &snapshot);
    println!("== Escape: the recipe screen closed {shut}");

    // R and U on an item of the list: its recipes, its uses.
    let mut keys_open = Vec::new();
    let mut pages = None;
    for key in ["r", "u"] {
        let at = item_at(1).unwrap_or(first);
        snapshot.state.cursor = at;
        run(&snapshot, 9);
        m::mod_screen(|s| s.over_input(vec!["key".into(), format!("key.keyboard.{key}"), "0".into()], at));
        let screen = recipe_screen(&mut run, &snapshot);
        println!("== {key} over {at:?}: {}", describe(&screen));
        // A screen of more than one page: its next page, then the next category, by the arrows JEI drew (its
        // buttons' sprites; the page's arrow is under the category's, left of the window's right edge).
        if let Some(Drawn { page: Some(page), next, texts, .. }) = &screen
            && !page.ends_with("/1")
            && next.len() >= 2
        {
            let mut arrows: Vec<[f32; 4]> = next.iter().copied().filter(|r| r[0] < right).collect();
            arrows.sort_by(|a, b| a[1].total_cmp(&b[1]));
            let mut press = |arrow: [f32; 4], snapshot: &mut m::Snapshot| {
                let at = [arrow[0] + arrow[2] / 2.0, arrow[1] + arrow[3] / 2.0];
                // JEI reads the pointer from the game's mouse too (MouseUtil), as the window's game moves it.
                snapshot.state.cursor = at;
                m::mod_screen(|s| s.cursor(at));
                run(snapshot, 6);
                m::mod_screen(|s| {
                    s.input(click("press", at, "0"));
                    s.input(click("release", at, "0"));
                });
                run(snapshot, 15);
                let now = drawn(&m::mod_screen(|s| s.ops.clone()));
                println!("== the arrow at {at:?}: {}", describe(&Some(now.clone())));
                now
            };
            let paged = press(arrows[1], &mut snapshot);
            let category = press(arrows[0], &mut snapshot);
            pages = Some((page.clone(), paged.page.unwrap_or_default(), texts[0].clone(), category.texts.first().cloned()));
        }
        keys_open.push(screen.is_some_and(|r| r.items > 0));
        close(&mut run, &snapshot);
    }

    println!("== kiln/java.log");
    let text = std::fs::read_to_string(&log).unwrap_or_default();
    // Its own lines, without the traces and the details JEI gives with each error.
    for line in text.lines().filter(|l| l.starts_with('[') && !l.contains("enchanted_book")) {
        println!("{line}");
    }

    assert!(items > 20, "JEI's item list over the inventory: {items} items drawn");
    assert!(
        claims.iter().any(|r| r[0] >= right && r[2] > 16.0 && r[3] > 16.0),
        "the list takes the mouse right of the window: {claims:?}"
    );
    assert_eq!(clicked, Some((true, true)), "a click on the list is the mods'");
    assert!(!game_click, "a click in the window is the game's");
    assert!(typed, "a click on JEI's search bar gives it the keys");
    assert!(tooltip, "a tooltip at the pointer over the list: {hovered:?}");
    assert!(away.len() < hovered.len(), "no tooltip away from the list: {away:?}");
    let recipes = recipes.expect("a click on an item of the list opens JEI's recipe screen");
    assert!(recipes.items > 0 && recipes.sprites > 0, "the recipe screen draws a category: {}", describe(&Some(recipes.clone())));
    let (before, after, was, now) = pages.expect("a recipe screen of more than one page (U over an item)");
    assert!(before != after && after.starts_with("2/"), "the next page arrow turns the page: {before} then {after}");
    assert!(now.as_ref().is_some_and(|now| *now != was), "the category's arrow shows the next category: {was} then {now:?}");
    assert!(!slot_tooltip.is_empty(), "a tooltip over the recipe's ingredient");
    assert!(shut, "Escape closes the recipe screen");
    assert_eq!(keys_open, [true, true], "R and U over an item open its recipes and its uses");
}

/// What a screen drew, as far as the test looks: its items, sprites, texts, page number and next page arrow.
#[derive(Clone, Debug, Default)]
struct Drawn {
    items: usize,
    sprites: usize,
    texts: Vec<String>,
    page: Option<String>,
    /// JEI's "next" arrows on it, top first: the category's, then the page's.
    next: Vec<[f32; 4]>,
}

fn drawn(ops: &[HudOp]) -> Drawn {
    let mut d = Drawn::default();
    for op in ops {
        match op {
            HudOp::Item { .. } => d.items += 1,
            HudOp::Sprite { rect, name, .. } => {
                d.sprites += 1;
                if name.contains("arrow_next") {
                    d.next.push(*rect);
                }
            }
            HudOp::Text { text, .. } => {
                let page = text.split_once('/').is_some_and(|(a, b)| {
                    !a.is_empty() && !b.is_empty() && a.chars().chain(b.chars()).all(|c| c.is_ascii_digit())
                });
                if page && d.page.is_none() {
                    d.page = Some(text.clone());
                }
                d.texts.push(text.clone());
            }
            _ => {}
        }
    }
    d
}

fn describe(d: &Option<Drawn>) -> String {
    match d {
        None => "no recipe screen".into(),
        Some(d) => format!(
            "{} items, {} sprites, page {:?}, arrows {:?}, texts {:?}",
            d.items,
            d.sprites,
            d.page,
            d.next,
            d.texts.iter().take(30).collect::<Vec<_>>()
        ),
    }
}

/// The texts drawn, with where.
fn texts(ops: &[HudOp]) -> Vec<([f32; 2], String)> {
    ops.iter()
        .filter_map(|op| match op {
            HudOp::Text { x, y, text, .. } => Some(([*x, *y], text.clone())),
            _ => None,
        })
        .collect()
}

/// Waits for a mod's screen to open (Java says so within a few ticks), shows it over the inventory as the game does,
/// and gives what it drew; none if no screen opened.
fn recipe_screen(run: &mut impl FnMut(&m::Snapshot, u64), snapshot: &m::Snapshot) -> Option<Drawn> {
    for _ in 0..10 {
        run(snapshot, 3);
        match m::mod_screen(|s| s.take_wanted()) {
            Some(true) => {
                m::mod_screen(|s| s.show(m::Shown::Over));
                run(snapshot, 15);
                return Some(drawn(&m::mod_screen(|s| s.ops.clone())));
            }
            Some(false) => m::mod_screen(|s| s.show(m::Shown::No)),
            None => {}
        }
    }
    None
}

/// Escape on the mod's screen shown: whether Java closed it.
fn close(run: &mut impl FnMut(&m::Snapshot, u64), snapshot: &m::Snapshot) -> bool {
    if m::mod_screen(|s| s.shown) == m::Shown::No {
        return false;
    }
    m::mod_screen(|s| s.input(vec!["key".into(), "key.keyboard.escape".into(), "0".into()]));
    for _ in 0..10 {
        run(snapshot, 3);
        if m::mod_screen(|s| s.take_wanted()) == Some(false) {
            m::mod_screen(|s| s.show(m::Shown::No));
            return true;
        }
    }
    false
}
