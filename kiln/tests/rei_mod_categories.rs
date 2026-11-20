// A mod's own recipe categories in Roughly Enough Items (D-234, #283), with real Java, as the game drives it: Tech
// Reborn's REI plugin makes its displays on REI's server side only (`ReiCommonPlugin.registerDisplays` over the
// server's recipes), and REI's server sends them to the player's window (`roughlyenoughitems:sync_displays`, through
// Architectury's packets, in parts over Kiln's channel). Here the player joins, the server ticks, its messages are
// handed to the window as Horizon would, and the window's REI shows them: a right click on the grinder in REI's list
// opens its uses with Tech Reborn's Grinder category, the rolling machine's its Rolling Machine category, and the
// planks' recipe (the game's, now from the server's displays too) still draws. Needs a JDK, `sh tools/build-java.sh`,
// REI's, Cloth Config's and Architectury's jars in KILN_MOD, Tech Reborn's in KILN_TECH_REBORN and the game's prepared
// resources in KILN_RESOURCES.
//
//   KILN_MOD=rei.jar:cloth.jar:architectury.jar KILN_TECH_REBORN=TechReborn-6.2.1.jar KILN_RESOURCES=... \
//     cargo test -p horizon-kiln-mod --test rei_mod_categories -- --ignored --nocapture

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

const GUI: [f32; 2] = [640.0, 360.0];
const WINDOW: [f32; 4] = [232.0, 97.0, 176.0, 166.0];
const PLAYER: u128 = 0x00c0_ffee;

fn player() -> m::PlayerSnapshot {
    m::PlayerSnapshot {
        uuid: PLAYER,
        name: "Kiln".into(),
        dimension: "minecraft:overworld".into(),
        position: [0.5, 64.0, 0.5],
        health: 20.0,
        attributes: vec![("minecraft:max_health".into(), 20.0, 20.0)],
        ..Default::default()
    }
}

fn answer(what: &[String]) -> String {
    match what[0].as_str() {
        "players" => m::players_answer(&[], Some(&player()), 10),
        "loaded" => "true".to_owned(),
        _ => String::new(),
    }
}

#[test]
#[ignore = "needs a JDK, sh tools/build-java.sh, REI's three jars in KILN_MOD, KILN_TECH_REBORN and KILN_RESOURCES"]
fn tech_reborns_categories_show_in_rei() {
    let (Some(mods), Some(tech_reborn)) = (
        std::env::var_os("KILN_MOD"),
        std::env::var_os("KILN_TECH_REBORN"),
    ) else {
        println!("KILN_MOD or KILN_TECH_REBORN names no jar: nothing to run");
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
    let game = std::env::temp_dir().join(format!("kiln-rei-categories-{}", std::process::id()));
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
    let tech_reborn = std::path::PathBuf::from(tech_reborn);
    for jar in std::env::split_paths(&mods).chain([tech_reborn]) {
        std::fs::copy(&jar, kiln.join("mods").join(jar.file_name().unwrap())).unwrap();
    }
    let data = game.join("horizon-mods/.horizon-mod-data/kiln");
    std::fs::create_dir_all(&data).unwrap();
    let world = game.join("world");

    let mut snapshot = m::Snapshot {
        connected: true,
        in_world: true,
        paused: false,
        game_time: 100,
        dimension: "minecraft:overworld".into(),
        player: Some(player()),
        ..m::Snapshot::default()
    };
    // A single-player world: the integrated server starts as it is joined (SERVER_STARTING, D-093), which is where
    // Architectury's GameInstance.getServer() gets the server that REI's server side reloads and sends with.
    snapshot.state.single_player = true;
    snapshot.state.window = [1920, 1080, 3];
    snapshot.state.cursor = [600.0, 150.0];
    m::set_gui(GUI[0], GUI[1]);

    // The server ticks with the window, as in the game: REI's server sends its displays once its plugins reloaded
    // (it skips its ticks while they reload), in parts (`kiln:part`), each a message Horizon carries, which the window
    // joins into Architectury's payload. The player connected and joined first.
    let mut game_time = 200;
    let mut sent: Vec<Vec<u8>> = Vec::new();
    // The most messages one tick sent the player: Horizon carries at most 32 a tick and drops the rest.
    let mut most_in_a_tick = 0usize;
    let mut frame = 0u64;
    let mut run = |snapshot: &m::Snapshot, frames: u64| {
        for _ in 0..frames {
            if frame % 3 == 0 {
                for line in m::tick(&data, || None, || vec![6; 128], snapshot.clone()) {
                    println!("  chat: {line}");
                }
                if m::hears("server_tick_end") {
                    game_time += 1;
                    let ticked = m::server_tick(
                        &world,
                        game_time,
                        m::TickPhase::End,
                        "minecraft:overworld",
                        &mut answer,
                    );
                    let mut in_this_tick = 0;
                    for effect in ticked.map(|o| o.effects).unwrap_or_default() {
                        if let m::Effect::Net { player, data } = effect {
                            assert_eq!(player, PLAYER);
                            m::from_server(&data);
                            sent.push(data);
                            in_this_tick += 1;
                        }
                    }
                    most_in_a_tick = most_in_a_tick.max(in_this_tick);
                }
            }
            m::frame(|| snapshot.clone());
            frame += 1;
            std::thread::sleep(Duration::from_millis(16));
        }
    };
    let started = Instant::now();
    while !(m::hears("connect") && m::hears("server_tick_end")) {
        assert!(
            started.elapsed() < Duration::from_secs(60),
            "REI's server events were never announced"
        );
        run(&snapshot, 3);
    }
    for joined in [
        m::player_connected(&world, 199, &player(), &mut answer),
        m::player_joined(&world, 199, &player(), &mut answer),
    ] {
        for effect in joined.map(|o| o.effects).unwrap_or_default() {
            if let m::Effect::Net { data, .. } = effect {
                m::from_server(&data);
            }
        }
    }

    // The window opens its inventory until REI's list shows (its reload runs in the background).
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
    // At most KILN_WAIT seconds (240): REI reloads its plugins twice (its local recipes), slowly on a busy machine.
    let wait: u64 = std::env::var("KILN_WAIT")
        .ok()
        .and_then(|s| s.parse().ok())
        .unwrap_or(240);
    let opening = Instant::now();
    let mut opened = 0;
    while m::mod_screen(|s| listed(&s.over_ops)) == 0
        && opening.elapsed() < Duration::from_secs(wait)
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
    run(&snapshot, 60);
    println!(
        "== the inventory opened {opened} times, {} items listed",
        m::mod_screen(|s| listed(&s.over_ops))
    );

    // A machine searched in REI's field, then a right click on it (its uses): REI's screen with its categories.
    let search = [GUI[0] / 2.0, GUI[1] - 12.0];
    // `tab`: a category's tab (its icon's item and its title) to click when the screen opened on another category.
    let mut uses = |name: &str, button: &str, tab: Option<(&str, &str)>| -> Option<Vec<HudOp>> {
        snapshot.state.cursor = search;
        run(&snapshot, 6);
        let press = |kind: &str, at: [f32; 2], button: &str| {
            vec![
                kind.to_owned(),
                m::num(at[0]),
                m::num(at[1]),
                button.to_owned(),
                "0".to_owned(),
            ]
        };
        m::mod_screen(|s| {
            s.over_input(press("press", search, "0"), search);
            s.over_input(press("release", search, "0"), search);
        });
        run(&snapshot, 6);
        for _ in 0..24 {
            m::mod_screen(|s| {
                s.over_input(
                    vec!["key".into(), "key.keyboard.backspace".into(), "0".into()],
                    search,
                )
            });
            run(&snapshot, 1);
        }
        for c in name.chars() {
            m::mod_screen(|s| {
                s.over_input(
                    vec!["char".into(), (c as u32).to_string(), "0".into()],
                    search,
                )
            });
            run(&snapshot, 2);
        }
        run(&snapshot, 30);
        let searched = m::mod_screen(|s| s.over_ops.clone());
        let found: Vec<String> = item_names(&searched).into_iter().take(8).collect();
        println!("== \"{name}\" typed: first items {found:?}");
        let [x, y, size] = items(&searched).into_iter().find(|[x, _, _]| *x >= right)?;
        let at = [x + size / 2.0, y + size / 2.0];
        snapshot.state.cursor = at;
        run(&snapshot, 6);
        m::mod_screen(|s| {
            s.over_input(press("press", at, button), at);
            s.over_input(press("release", at, button), at);
        });
        let mut shown = recipe_screen(&mut run, &snapshot);
        if let (Some(ops), Some((icon, title))) = (&shown, tab) {
            // REI's category tabs are drawn as their icons above the recipes, left of REI's list (right of the
            // inventory's window): the topmost of the icon's item there.
            let tab_at = ops
                .iter()
                .filter_map(|op| match op {
                    HudOp::Item { x, y, size, item } if item == icon && *x < right => {
                        Some([*x, *y, *size])
                    }
                    _ => None,
                })
                .min_by(|a, b| a[1].total_cmp(&b[1]));
            if let (false, Some([x, y, size])) =
                (texts(ops).iter().any(|(_, t)| t == title), tab_at)
            {
                let at = [x + size / 2.0, y + size / 2.0];
                snapshot.state.cursor = at;
                run(&snapshot, 6);
                m::mod_screen(|s| {
                    s.input(press("press", at, "0"));
                    s.input(press("release", at, "0"));
                });
                run(&snapshot, 30);
                println!("== {name}: the {icon} tab at {at:?} clicked");
                shown = Some(m::mod_screen(|s| s.ops.clone()));
            }
        }
        if let Some(ops) = &shown {
            println!(
                "== {name}, button {button}: REI's screen, texts {:?}",
                texts(ops)
                    .iter()
                    .map(|(_, t)| t.as_str())
                    .collect::<Vec<_>>()
            );
        } else {
            println!("== {name}, button {button}: no screen opened");
        }
        m::mod_screen(|s| s.input(vec!["key".into(), "key.keyboard.escape".into(), "0".into()]));
        run(&snapshot, 12);
        m::mod_screen(|s| s.show(m::Shown::No));
        run(&snapshot, 12);
        shown
    };
    let planks = uses("planks", "0", None);
    let grinder = uses("grinder", "1", Some(("techreborn:grinder", "Grinder")));
    let rolling = uses(
        "rolling machine",
        "1",
        Some(("techreborn:rolling_machine", "Rolling Machine")),
    );

    drop(uses);
    let largest = sent.iter().map(Vec::len).max().unwrap_or(0);
    let parts = sent
        .iter()
        .filter(|d| d.starts_with(b"\x09kiln:part"))
        .count();
    println!(
        "== the server sent {} messages ({parts} parts, the largest {largest} bytes, {} bytes in all, at most \
         {most_in_a_tick} a tick)",
        sent.len(),
        sent.iter().map(Vec::len).sum::<usize>()
    );
    let log = std::fs::read_to_string(kiln.join("java.log")).unwrap_or_default();
    let _ = std::fs::copy(
        kiln.join("java.log"),
        std::env::temp_dir().join("kiln-rei-categories-java.log"),
    );
    for line in log
        .lines()
        .filter(|l| l.contains("REI Server Display Sync") || l.contains("in parts"))
    {
        println!("  java: {line}");
    }
    let _ = std::fs::remove_dir_all(&game);

    assert!(parts > 1, "REI's displays go to the window in parts");
    assert!(
        most_in_a_tick <= 30,
        "at most 30 messages a tick, under Horizon's 32: {most_in_a_tick}"
    );
    assert!(
        largest <= 30_000,
        "no message is longer than Horizon carries: {largest}"
    );
    assert!(
        log.contains("Received server's request to"),
        "the window's REI received the server's displays"
    );
    let has = |ops: &Option<Vec<HudOp>>, title: &str| {
        ops.as_ref()
            .is_some_and(|ops| texts(ops).iter().any(|(_, t)| t == title))
    };
    assert!(
        has(&grinder, "Grinder"),
        "the grinder's uses show Tech Reborn's Grinder category"
    );
    assert!(
        has(&rolling, "Rolling Machine"),
        "the rolling machine's uses show Tech Reborn's Rolling Machine category"
    );
    assert!(
        has(&planks, "Crafting"),
        "the planks' crafting recipe still shows (the game's recipes among the server's displays)"
    );
}

/// Waits for a mod's screen to open, shows it over the inventory as the game does, and gives what it drew.
fn recipe_screen(
    run: &mut impl FnMut(&m::Snapshot, u64),
    snapshot: &m::Snapshot,
) -> Option<Vec<HudOp>> {
    // REI finds a screen's displays in the background: up to about 20 s on a busy machine.
    let started = Instant::now();
    while started.elapsed() < Duration::from_secs(20) {
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
