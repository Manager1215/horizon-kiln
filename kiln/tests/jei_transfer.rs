// JEI's "+" on its recipe screen (D-166, #203), with real Java, as the game drives it, without the game: the player has
// planks, opens the inventory, JEI's recipe screen for the crafting table opens over it, and a click on the "+" moves
// the recipe's ingredients from the inventory into the crafting grid. JEI's own network packet goes to the server's
// side (Fabric's payload, D-088), whose JEI code works on the menu's slots; what it did to them the game is told as
// effects, which the test applies to the inventory it gave. Needs a JDK, `sh tools/build-java.sh`, JEI's jar in
// KILN_MOD and the game's prepared resources in KILN_RESOURCES.
//
//   KILN_MOD=jei.jar KILN_RESOURCES=... cargo test -p horizon-kiln-mod --test jei_transfer -- --ignored --nocapture

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
fn jei_moves_a_recipe_into_the_crafting_grid() {
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
    let game = std::env::temp_dir().join(format!("kiln-jei-transfer-{}", std::process::id()));
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
    for c in "crafting table".chars() {
        m::mod_screen(|s| s.over_input(vec!["char".into(), (c as u32).to_string(), "0".into()], at));
        run(&snapshot, 3);
    }
    run(&snapshot, 12);
    let window = [WINDOW[0] + 20.0, WINDOW[1] + 20.0];
    m::mod_screen(|s| {
        s.over_input(fields("press", window), window);
        s.over_input(fields("release", window), window);
    });
    run(&snapshot, 9);

    // The inventory's 2x2 grid first, then the crafting table's 3x3: the game's screen is the inventory's for both,
    // and says which by its menu (D-166).
    let first = m::mod_screen(|s| {
        s.over_ops.iter().find_map(|op| match op {
            HudOp::Item { x, y, size, .. } if *x >= right => Some([x + size / 2.0, y + size / 2.0]),
            _ => None,
        })
    })
    .expect("an item in JEI's list");
    let mut results = Vec::new();
    for (menu, side) in [("-", 2usize), ("minecraft:crafting", 3)] {
        println!("== the menu {menu}");
        let mut view = view.clone();
        view[1] = menu.to_owned();
        m::mod_screen(|s| s.game_screen(Some(view)));
        snapshot.state.cursor = first;
        run(&snapshot, 30);
        // A left click on the list's first item (the table): JEI's recipe screen for it.
        snapshot.state.cursor = first;
        run(&snapshot, 6);
        m::mod_screen(|s| {
            s.over_input(fields("press", first), first);
            s.over_input(fields("release", first), first);
        });
        let shown = recipe_screen(&mut run, &snapshot).is_some();
        let ops = m::mod_screen(|s| s.ops.clone());
        // The "+": JEI's button with the recipe_transfer icon; a click on it sends JEI's packet to the server.
        let plus = ops.iter().find_map(|op| match op {
            HudOp::Sprite { rect, name, .. } if name.contains("recipe_transfer") => Some(*rect),
            _ => None,
        });
        let button = ops.iter().find_map(|op| match op {
            HudOp::Sprite { rect, name, .. }
                if name.contains("button_") && plus.is_some_and(|p| rect[0] < p[0] && rect[1] < p[1] && rect[0] + 13.0 > p[0]) =>
            {
                Some(name.clone())
            }
            _ => None,
        });
        println!("== the recipe screen {shown}; the + {plus:?} on {button:?}");
        if plus.is_none() {
            for op in &ops {
                match op {
                    HudOp::Sprite { rect, name, .. } => println!("  sprite {name} at {rect:?}"),
                    HudOp::Text { x, y, text, .. } => println!("  text at {x},{y}: {text}"),
                    _ => {}
                }
            }
            println!("{}", std::fs::read_to_string(&log).unwrap_or_default().lines().filter(|l| !l.contains("INFO") && !l.contains("Mixin")).collect::<Vec<_>>().join("\n"));
        }
        let plus = plus.map(|[x, y, w, h]| [x + w / 2.0, y + h / 2.0]).expect("JEI's transfer button");
        snapshot.state.cursor = plus;
        m::mod_screen(|s| s.cursor(plus));
        run(&snapshot, 9);
        m::take_net();
        m::mod_screen(|s| {
            s.input(fields("press", plus));
            s.input(fields("release", plus));
        });
        run(&snapshot, 15);
        let sent = m::take_net();
        println!("== a click on the + at {plus:?} sent {} messages", sent.len());
        let world = game.join("world");
        // The game's side of the server call: the player's menu, as Horizon answers `player_menu`, and the slots the
        // payload's code changed, as Horizon applies them.
        let mut slots: Vec<Option<(String, u32)>> = vec![None; 36 + side * side];
        slots[0] = Some(("minecraft:oak_planks".into(), 8));
        slots[1] = Some(("minecraft:stick".into(), 3));
        let mut effects = Vec::new();
        for (i, message) in sent.iter().enumerate() {
            let mut asked = |what: &[String]| {
                if what == ["player_menu"] {
                    let items: Vec<String> = slots
                        .iter()
                        .enumerate()
                        .filter_map(|(slot, s)| {
                            let (item, count) = s.as_ref()?;
                            Some(format!("{{Slot:{slot}b,count:{count},id:\"{item}\"}}"))
                        })
                        .collect();
                    format!("{side} 0 {{Items:[{}]}}", items.join(","))
                } else {
                    String::new()
                }
            };
            let heard = m::from_client(&world, 20 + i as u64, message, &player, &mut asked);
            println!("  server: {heard:?}");
            effects.extend(heard.into_iter().flat_map(|o| o.effects));
        }
        let moved: Vec<(usize, u32)> = effects
            .iter()
            .find_map(|e| match e {
                m::Effect::PlayerSlots { data } => Some(items_of(data, "Items")),
                _ => None,
            })
            .unwrap_or_default();
        let answered = effects.iter().any(|e| matches!(e, m::Effect::Net { data, .. } if data.starts_with(b"\x1ajei:recipe_transfer_result")));
        println!("== slots after: {moved:?}; JEI answered the player: {answered}");
        results.push((side, moved, answered));
        // Back to the inventory: Escape closes JEI's recipe screen.
        close(&mut run, &snapshot);
    }

    println!("== kiln/java.log");
    let text = std::fs::read_to_string(&log).unwrap_or_default();
    for line in text.lines().filter(|l| l.starts_with('[') && !l.contains("enchanted_book") && !l.contains("INFO")) {
        println!("{line}");
    }

    for (side, moved, answered) in results {
        let grid: Vec<usize> = moved.iter().filter(|(slot, _)| *slot >= 36).map(|(slot, _)| slot - 36).collect();
        let grid_planks: u32 = moved.iter().filter(|(slot, _)| *slot >= 36).map(|(_, n)| n).sum();
        let held: u32 = moved.iter().filter(|(slot, _)| *slot < 36).map(|(_, n)| n).sum();
        assert_eq!(grid_planks, 4, "{side}x{side}: the table's recipe, four planks, is in the grid: {moved:?}");
        assert_eq!(held, 4 + 3, "{side}x{side}: four planks and the sticks are left: {moved:?}");
        // The recipe is two cells wide and two high, laid in the grid's top left.
        let expect: Vec<usize> = if side == 2 { vec![0, 1, 2, 3] } else { vec![0, 1, 3, 4] };
        assert_eq!(grid, expect, "{side}x{side}: the cells the recipe fills");
        assert!(answered, "{side}x{side}: JEI's server answers the player");
    }
    let _ = std::fs::remove_dir_all(&game);
}

/// The slots of an SNBT compound's list as `[{Slot:0b,count:4,id:"..."}]` gives them: (slot, count), for the planks.
fn items_of(data: &str, key: &str) -> Vec<(usize, u32)> {
    let start = data.find(&format!("{key}:[")).expect("the list");
    let list = &data[start..];
    let end = list.find(']').unwrap_or(list.len());
    list[..end]
        .split('{')
        .skip(1)
        .filter_map(|entry| {
            let slot = entry.split("Slot:").nth(1)?.split('b').next()?.parse().ok()?;
            let count = entry.split("count:").nth(1)?.split([',', '}']).next()?.parse().ok()?;
            Some((slot, count))
        })
        .collect()
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

/// Waits for a mod's screen to open (Java says so within a few ticks), shows it over the inventory as the game does,
/// and gives whether it did.
fn recipe_screen(run: &mut impl FnMut(&m::Snapshot, u64), snapshot: &m::Snapshot) -> Option<()> {
    for _ in 0..10 {
        run(snapshot, 3);
        match m::mod_screen(|s| s.take_wanted()) {
            Some(true) => {
                m::mod_screen(|s| s.show(m::Shown::Over));
                run(snapshot, 15);
                return Some(());
            }
            Some(false) => m::mod_screen(|s| s.show(m::Shown::No)),
            None => {}
        }
    }
    None
}
