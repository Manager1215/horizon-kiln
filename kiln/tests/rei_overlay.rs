// Roughly Enough Items' overlay over the game's inventory screen (D-215, #283), with real Java, as the game drives it,
// without the game: REI draws through Architectury's screen events, which fire where Architectury's mixins wrap the
// game's own calls (the screen drawn by `Gui`, the window's contents, the mouse and the keys by their handlers). The
// window's game opens its inventory, REI draws its item list beside it, a click on its search field and keys typed
// filter the list, the pointer on an item draws its tooltip, and a click on it opens REI's recipe screen with its
// crafting recipe (D-217: REI's local recipes, from the recipes Kiln read). Needs a JDK, `sh tools/build-java.sh`, REI's, Cloth
// Config's and Architectury's jars in KILN_MOD and the game's prepared resources in KILN_RESOURCES.
//
//   KILN_MOD=rei.jar:cloth.jar:architectury.jar KILN_RESOURCES=... \
//     cargo test -p horizon-kiln-mod --test rei_overlay -- --ignored --nocapture

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
#[ignore = "needs a JDK, sh tools/build-java.sh, REI's, Cloth Config's and Architectury's jars in KILN_MOD and KILN_RESOURCES"]
fn rei_draws_its_list_over_the_inventory() {
    let Some(mods) = std::env::var_os("KILN_MOD") else {
        println!("KILN_MOD names no jar: nothing to run");
        return;
    };
    let _ = log::set_logger(&Printed).map(|()| log::set_max_level(log::LevelFilter::Info));
    let built = Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln");
    assert!(
        built.join("kiln-runtime.jar").is_file(),
        "run sh tools/build-java.sh first"
    );
    if let Some(resources) = std::env::var_os("KILN_RESOURCES") {
        horizon_kiln_mod::game::kiln_jvm::set_resources(Path::new(&resources));
    }
    let game = std::env::temp_dir().join(format!("kiln-rei-overlay-{}", std::process::id()));
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
    snapshot.state.cursor = [600.0, 150.0];
    m::set_gui(GUI[0], GUI[1]);

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
    run(&snapshot, 30);

    // The player opens the inventory: the tick says so, the screen hooks say where the window is. REI reloads its
    // plugins in the background, and puts its overlay on a screen as the screen opens after that (as in the game, where
    // the player opens it later): so the inventory is closed and opened again every few seconds until REI's list shows,
    // at most KILN_WAIT seconds (60).
    let view = vec![
        "inventory".to_owned(),
        "-".to_owned(),
        m::num(WINDOW[0]),
        m::num(WINDOW[1]),
        m::num(WINDOW[2]),
        m::num(WINDOW[3]),
        "46".to_owned(),
    ];
    let right = WINDOW[0] + WINDOW[2];
    let listed = |ops: &[HudOp]| items(ops).iter().filter(|[x, _, _]| *x >= right).count();
    let wait: u64 = std::env::var("KILN_WAIT")
        .ok()
        .and_then(|s| s.parse().ok())
        .unwrap_or(60);
    let started = Instant::now();
    let mut opened = 0;
    while m::mod_screen(|s| listed(&s.over_ops)) == 0
        && started.elapsed() < Duration::from_secs(wait)
    {
        if opened > 0 {
            snapshot.state.screen = String::new();
            m::mod_screen(|s| s.game_screen(None));
            run(&snapshot, 6);
        }
        snapshot.state.screen = "inventory".into();
        run(&snapshot, 6);
        m::mod_screen(|s| s.game_screen(Some(view.clone())));
        opened += 1;
        let tried = Instant::now();
        while m::mod_screen(|s| listed(&s.over_ops)) == 0
            && tried.elapsed() < Duration::from_secs(5)
        {
            run(&snapshot, 30);
        }
    }
    println!("== the inventory opened {opened} times");
    run(&snapshot, 30);
    let (ops, claims) = m::mod_screen(|s| (s.over_ops.clone(), s.over_claims().0.to_vec()));
    let first_list = listed(&ops);
    println!(
        "== over the inventory after {:?}: {} ops, {first_list} items right of the window, claims {claims:?}",
        started.elapsed(),
        ops.len()
    );
    for op in ops
        .iter()
        .filter(|op| !matches!(op, HudOp::Item { .. } | HudOp::Stack { .. }))
        .take(40)
    {
        println!("  {}", describe(op));
    }
    let names: Vec<String> = item_names(&ops).into_iter().take(12).collect();
    println!("  first items: {names:?}");

    // REI's search field, at the bottom in the middle: a click there gives it the keys, and "planks" typed filters the
    // list to the planks.
    let search = [GUI[0] / 2.0, GUI[1] - 12.0];
    snapshot.state.cursor = search;
    run(&snapshot, 6);
    let press = |kind: &str, at: [f32; 2]| {
        vec![
            kind.to_owned(),
            m::num(at[0]),
            m::num(at[1]),
            "0".to_owned(),
            "0".to_owned(),
        ]
    };
    let claimed = m::mod_screen(|s| {
        let a = s.over_input(press("press", search), search);
        let b = s.over_input(press("release", search), search);
        (a, b)
    });
    run(&snapshot, 9);
    for c in "planks".chars() {
        m::mod_screen(|s| {
            s.over_input(
                vec!["char".into(), (c as u32).to_string(), "0".into()],
                search,
            )
        });
        run(&snapshot, 3);
    }
    run(&snapshot, 30);
    let searched = m::mod_screen(|s| s.over_ops.clone());
    let planks = item_names(&searched)
        .into_iter()
        .filter(|n| n.contains("planks"))
        .count();
    let texts_now = texts(&searched);
    println!(
        "== search clicked (claimed {claimed:?}), \"planks\" typed: {} items right of the window, {planks} planks, texts {:?}",
        listed(&searched),
        texts_now
            .iter()
            .map(|(_, t)| t.as_str())
            .collect::<Vec<_>>()
    );

    // The pointer over the list's first item: REI draws its tooltip.
    let first = items(&searched).into_iter().find(|[x, _, _]| *x >= right);
    let mut tooltip = Vec::new();
    if let Some([x, y, size]) = first {
        let at = [x + size / 2.0, y + size / 2.0];
        snapshot.state.cursor = at;
        run(&snapshot, 12);
        tooltip = m::mod_screen(|s| texts(&s.over_ops))
            .into_iter()
            .filter(|(p, _)| p[1] < at[1] + 30.0 && p[1] > at[1] - 40.0)
            .map(|(_, t)| t)
            .collect();
        println!("== the pointer at {at:?}: texts near it {tooltip:?}");
    }

    // A left click on that item: REI's recipe screen for it (D-217), its displays from the recipes Kiln read, as REI
    // makes them on a server without REI (Horizon's). The planks' crafting recipe is drawn: the category's title, the
    // log and the planks.
    let mut recipe = None;
    if let Some([x, y, size]) = first {
        let at = [x + size / 2.0, y + size / 2.0];
        let click = |kind: &str| {
            vec![
                kind.to_owned(),
                m::num(at[0]),
                m::num(at[1]),
                "0".to_owned(),
                "0".to_owned(),
            ]
        };
        m::mod_screen(|s| {
            s.over_input(click("press"), at);
            s.over_input(click("release"), at);
        });
        recipe = recipe_screen(&mut run, &snapshot);
        match &recipe {
            Some(ops) => {
                println!(
                    "== after a click on {at:?}: REI's screen, {} ops, texts {:?}, items {:?}",
                    ops.len(),
                    texts(ops)
                        .iter()
                        .map(|(_, t)| t.as_str())
                        .collect::<Vec<_>>(),
                    item_names(ops).into_iter().take(20).collect::<Vec<_>>()
                );
            }
            None => println!("== after a click on {at:?}: no screen opened"),
        }
        if let Some(ops) = &recipe {
            for op in ops
                .iter()
                .filter(|op| !matches!(op, HudOp::Item { .. } | HudOp::Stack { .. }))
                .take(60)
            {
                println!("  {}", describe(op));
            }
        }
    }

    // Java's log, kept beside the test's folder for a reader (REI says there how its plugins loaded).
    let _ = std::fs::copy(
        kiln.join("java.log"),
        std::env::temp_dir().join("kiln-rei-overlay-java.log"),
    );
    let _ = std::fs::remove_dir_all(&game);
    assert!(
        first_list > 0,
        "REI draws its item list right of the inventory's window"
    );
    assert!(
        planks > 0 && listed(&searched) < first_list,
        "\"planks\" typed into REI's search filters its list"
    );
    assert!(
        tooltip.iter().any(|t| t.contains("Planks")),
        "the pointer on a listed item draws its tooltip"
    );
    let recipe = recipe.expect("a click on a listed item opens REI's recipe screen");
    assert!(
        texts(&recipe).iter().any(|(_, t)| t == "Crafting"),
        "REI's recipe screen shows the planks' crafting recipe (its category's title)"
    );
    assert!(
        item_names(&recipe).iter().any(|n| n.contains("acacia_log")),
        "the crafting recipe's ingredient, the log, is drawn"
    );
}

/// Waits for a mod's screen to open (Java says so within a few ticks), shows it over the inventory as the game does,
/// and gives what it drew; none if no screen opened.
fn recipe_screen(
    run: &mut impl FnMut(&m::Snapshot, u64),
    snapshot: &m::Snapshot,
) -> Option<Vec<HudOp>> {
    for _ in 0..20 {
        run(snapshot, 3);
        match m::mod_screen(|s| s.take_wanted()) {
            Some(true) => {
                m::mod_screen(|s| s.show(m::Shown::Over));
                run(snapshot, 30);
                return Some(m::mod_screen(|s| s.ops.clone()));
            }
            Some(false) => m::mod_screen(|s| s.show(m::Shown::No)),
            None => {}
        }
    }
    None
}

fn describe(op: &HudOp) -> String {
    match op {
        HudOp::Text {
            x, y, argb, text, ..
        } => format!("text at {x},{y} {argb:08x}: {text}"),
        HudOp::Fill { x, y, w, h, argb } => format!("box at {x},{y} {w}x{h} {argb:08x}"),
        HudOp::Item { x, y, size, item } => format!("item at {x},{y} {size}px: {item}"),
        HudOp::Stack { x, y, size, snbt } => format!("stack at {x},{y} {size}px: {snbt}"),
        HudOp::Styled { x, y, text, .. } => format!("styled text at {x},{y}: {text}"),
        HudOp::Clip(rect) => format!("cut to {rect:?}"),
        HudOp::Sprite { rect, name, .. } => format!("sprite {name} at {rect:?}"),
        HudOp::Entity { rect, id, .. } => format!("entity {id} at {rect:?}"),
    }
}

/// The items drawn, as x, y and size.
fn items(ops: &[HudOp]) -> Vec<[f32; 3]> {
    ops.iter()
        .filter_map(|op| match op {
            HudOp::Item { x, y, size, .. } | HudOp::Stack { x, y, size, .. } => {
                Some([*x, *y, *size])
            }
            _ => None,
        })
        .collect()
}

fn item_names(ops: &[HudOp]) -> Vec<String> {
    ops.iter()
        .filter_map(|op| match op {
            HudOp::Item { item, .. } => Some(item.clone()),
            HudOp::Stack { snbt, .. } => Some(snbt.clone()),
            _ => None,
        })
        .collect()
}

fn texts(ops: &[HudOp]) -> Vec<([f32; 2], String)> {
    ops.iter()
        .filter_map(|op| match op {
            HudOp::Text { x, y, text, .. } | HudOp::Styled { x, y, text, .. } => {
                Some(([*x, *y], text.clone()))
            }
            _ => None,
        })
        .collect()
}
