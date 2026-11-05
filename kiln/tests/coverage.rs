// The mods Kiln says work (catalogue/checks.json) under Kiln with real Java, as the game drives them, without the
// game: every jar in the folder KILN_COVERAGE names runs together, as in a player's game, and each mod's check, when
// its jar is there, says that the mod's own feature works, not only that it starts. Needs a JDK and
// `sh tools/build-java.sh`. `python3 -I tools/check-mods.py` downloads the jars and runs this for each mod alone and
// for all of them together.
//
// One test runs them all, one after another: Kiln's Java belongs to the thread that started it (the window's, in
// the game), and cargo runs each test on a thread of its own. Each check says `check <id>: passed` or
// `check <id>: FAILED: <why>` (`<id>` as in catalogue/verified.json), and the test fails if any failed.
//
//   KILN_COVERAGE=folder cargo test -p horizon-kiln-mod --test coverage -- --ignored --nocapture

use std::path::{Path, PathBuf};
use std::sync::OnceLock;
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;
use horizon_kiln_mod::game::kiln_jvm::HudOp;

/// What Kiln logs (a mod's failure, what it reached that Kiln has only the name of), on the test's output.
struct Said;

impl log::Log for Said {
    fn enabled(&self, _: &log::Metadata) -> bool {
        true
    }

    fn log(&self, record: &log::Record) {
        if record.level() <= log::Level::Info {
            println!("{}: {}", record.level(), record.args());
        }
    }

    fn flush(&self) {}
}

/// The jar in KILN_COVERAGE whose name starts with `prefix`; None (the test says so and passes) without one.
fn jar(prefix: &str) -> Option<PathBuf> {
    let folder = PathBuf::from(std::env::var_os("KILN_COVERAGE")?);
    let found = std::fs::read_dir(&folder)
        .ok()?
        .filter_map(|e| e.ok().map(|e| e.path()))
        .find(|p| {
            p.file_name()
                .and_then(|n| n.to_str())
                .is_some_and(|n| n.starts_with(prefix))
        });
    if found.is_none() {
        println!("no {prefix}* jar in {}: nothing to run", folder.display());
    }
    found
}

/// The game folder the checks share: Kiln, the libraries and every jar in KILN_COVERAGE; it and Kiln's data folder
/// inside it. Made, and Java started in it, by the first check that asks, on the test's thread.
fn game() -> &'static (PathBuf, PathBuf) {
    static GAME: OnceLock<(PathBuf, PathBuf)> = OnceLock::new();
    // Set while starting: a start that failed fails the checks after it at once, without waiting again.
    static STARTING: AtomicBool = AtomicBool::new(false);
    assert!(
        GAME.get().is_some() || !STARTING.load(Ordering::SeqCst),
        "the game didn't start (the first check says why)"
    );
    GAME.get_or_init(|| {
        STARTING.store(true, Ordering::SeqCst);
        let _ = log::set_logger(&Said).map(|()| log::set_max_level(log::LevelFilter::Info));
        let built = Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln");
        assert!(
            built.join("kiln-runtime.jar").is_file(),
            "run sh tools/build-java.sh first"
        );
        let game = std::env::temp_dir().join(format!("kiln-coverage-{}", std::process::id()));
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
        let folder = PathBuf::from(std::env::var_os("KILN_COVERAGE").unwrap_or_default());
        for jar in m::jars(&folder) {
            std::fs::copy(&jar, kiln.join("mods").join(jar.file_name().unwrap())).unwrap();
        }
        let data = game.join("horizon-mods/.horizon-mod-data/kiln");
        std::fs::create_dir_all(&data).unwrap();
        // In a world until every mod's entrypoints ran: Kiln says how many run, in chat, and which didn't start and why
        // (on the test's output: tools/check-mods.py reads it).
        let deadline = Instant::now() + Duration::from_secs(90);
        let snapshot = in_world(Vec::new());
        loop {
            let chat = m::tick(&data, || None, || vec![6; 128], snapshot.clone());
            for line in &chat {
                println!("{line}");
            }
            if chat.iter().any(|l| l.contains("Java mods running")) {
                break;
            }
            assert!(Instant::now() < deadline, "the mods never all started");
            std::thread::sleep(Duration::from_millis(50));
        }
        STARTING.store(false, Ordering::SeqCst);
        (game, data)
    })
}

/// Ticks until `done` (what the mods' code announced to the server: the events they hear, their items with a use) or
/// ten seconds pass. Java announces it right after a mod's entrypoints answer, so it can come a tick after Kiln says
/// every mod is running.
fn announced(data: &Path, done: impl Fn() -> bool) {
    let deadline = Instant::now() + Duration::from_secs(10);
    while !done() && Instant::now() < deadline {
        m::tick(data, || None, || vec![6; 128], in_world(Vec::new()));
        std::thread::sleep(Duration::from_millis(50));
    }
}

/// The window's game in a world, its player standing in it.
fn in_world(equipment: Vec<m::Worn>) -> m::Snapshot {
    m::Snapshot {
        connected: true,
        in_world: true,
        paused: false,
        game_time: 1000,
        dimension: "minecraft:overworld".into(),
        advancements: None,
        player: Some(m::PlayerSnapshot {
            uuid: 0x00c0_ffee,
            name: "Kiln".into(),
            dimension: "minecraft:overworld".into(),
            position: [0.5, 70.0, 0.5],
            game_mode: 0,
            health: 20.0,
            main: None,
            off: None,
            attributes: Vec::new(),
            ..Default::default()
        }),
        equipment,
        ..m::Snapshot::default()
    }
}

fn worn(slot: &'static str, item: &str, damage: u32, max_damage: u32) -> m::Worn {
    m::Worn {
        slot,
        item: item.into(),
        count: 1,
        damage,
        max_damage,
    }
}

/// Ticks the game at 20 a second until `done` says what the HUD shows is enough, or `seconds` pass.
fn ticks_until(
    data: &Path,
    snapshot: &m::Snapshot,
    seconds: u64,
    done: impl Fn(&[HudOp]) -> bool,
) -> Vec<HudOp> {
    let deadline = Instant::now() + Duration::from_secs(seconds);
    loop {
        m::tick(data, || None, || vec![6; 128], snapshot.clone());
        let ops = m::hud_ops();
        if done(&ops) || Instant::now() > deadline {
            return ops;
        }
        std::thread::sleep(Duration::from_millis(50));
    }
}

fn print_ops(ops: &[HudOp]) {
    for op in ops {
        match op {
            HudOp::Text {
                x, y, scale, text, ..
            } => println!("  text at {x},{y} x{scale}: {text}"),
            HudOp::Fill { x, y, w, h, argb } => println!("  box at {x},{y} {w}x{h} {argb:08x}"),
            HudOp::Item { x, y, size, item } => println!("  item at {x},{y} {size}px: {item}"),
            HudOp::Clip(rect) => println!("  cut to {rect:?}"),
        }
    }
}

/// The text the HUD shows.
fn texts(ops: &[HudOp]) -> Vec<&str> {
    ops.iter()
        .filter_map(|op| match op {
            HudOp::Text { text, .. } => Some(text.as_str()),
            _ => None,
        })
        .collect()
}

/// Runs mod `id`'s check when a jar whose name starts with `prefix` is in KILN_COVERAGE, and says how it went; a
/// failed one is added to `failed`, so the checks after it still run.
fn check(id: &str, prefix: &str, failed: &mut Vec<String>, run: impl FnOnce(&Path)) {
    let Some(jar) = jar(prefix) else {
        println!("check {id}: no jar");
        return;
    };
    match std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| run(&jar))) {
        Ok(()) => println!("check {id}: passed"),
        Err(e) => {
            let why = e
                .downcast_ref::<String>()
                .cloned()
                .or_else(|| e.downcast_ref::<&str>().map(|s| (*s).to_owned()))
                .unwrap_or_default();
            println!(
                "check {id}: FAILED: {}",
                why.lines().next().unwrap_or_default()
            );
            failed.push(id.to_owned());
        }
    }
}

#[test]
#[ignore = "needs a JDK, sh tools/build-java.sh and KILN_COVERAGE"]
fn working_mods_work() {
    if std::env::var_os("KILN_COVERAGE").is_none() {
        println!("KILN_COVERAGE names no folder: nothing to run");
        return;
    }
    let mut failed = Vec::new();
    // First every jar in the folder starts, whether or not a check below is its own (Tech Reborn's are in
    // tech_reborn.rs): Kiln says how many run and which didn't start.
    check("start", "", &mut failed, |_| {
        game();
    });
    check(
        "armour-durablity-hud",
        "armour-durability",
        &mut failed,
        |_| armour_durability_shows_what_is_worn(),
    );
    check(
        "better-than-mending",
        "BetterThanMending",
        &mut failed,
        |_| better_than_mending_repairs_with_experience(),
    );
    check("showfps", "showfps", &mut failed, |_| {
        show_fps_counts_frames()
    });
    check("heart-crystals", "HeartCrystals", &mut failed, |jar| {
        heart_crystals_raise_max_health();
        heart_crystals_loot_as_on_fabric(jar);
    });
    check("illager-warship", "illagerwarship", &mut failed, |jar| {
        illager_warship_sails_the_oceans(jar)
    });
    check("spongesucc", "SpongeSucc", &mut failed, |_| {
        spongesucc_takes_up_lava()
    });
    // Last: it takes the window's player out of the world and into a new one.
    check("kiln-speedrun-timer", "kiln-timer", &mut failed, |_| {
        the_timer_times_a_run()
    });
    assert!(failed.is_empty(), "failed: {failed:?}");
}

/// SpongeSucc (Lava Sponge): a lava sponge a player places takes up the lava around it (its own `onPlace`, a walk
/// over the lava it touches, each source taken up as a bucket would) and turns into a hot lava sponge (D-078).
fn spongesucc_takes_up_lava() {
    const SPONGE: &str = "spongesucc:lava_sponge";
    let (game, data) = game();
    announced(data, || m::block_does(SPONGE, "placed"));
    assert!(m::block_does(SPONGE, "placed"), "the sponge doesn't hear its placing");
    let world = game.join("world-spongesucc");
    // A pool of lava sources three by three under the sponge, stone around it.
    let at = [0, 64, 0];
    let pool = |x: i32, y: i32, z: i32| y == 63 && (-1..=1).contains(&x) && (-1..=1).contains(&z);
    let mut lava = |what: &[String]| match what {
        [kind, x, y, z] if kind == "block" => {
            let [x, y, z] = [x, y, z].map(|c| c.parse::<i32>().unwrap_or(0));
            if pool(x, y, z) {
                "minecraft:lava[level=0]".to_owned()
            } else if [x, y, z] == at {
                SPONGE.to_owned()
            } else {
                "minecraft:stone".to_owned()
            }
        }
        _ => String::new(),
    };
    let player = server_player("Kiln", 0x00c0_ffee, 20.0, Some((SPONGE, 1)));
    let outcome = m::placed(&world, 100, at, SPONGE, SPONGE, 1, &player, &mut lava).expect("the sponge hears it");
    println!("== placed: {outcome:?}");
    let set: Vec<([i32; 3], &str)> = outcome
        .effects
        .iter()
        .filter_map(|e| match e {
            m::Effect::Block { pos, state, .. } => Some((*pos, state.as_str())),
            _ => None,
        })
        .collect();
    let emptied: Vec<[i32; 3]> = set
        .iter()
        .filter(|(_, state)| *state == "minecraft:air")
        .map(|(pos, _)| *pos)
        .collect();
    assert_eq!(emptied.len(), 9, "not the whole pool taken up: {set:?}");
    assert!(emptied.iter().all(|&[x, y, z]| pool(x, y, z)), "{set:?}");
    assert_eq!(set.last(), Some(&(at, "spongesucc:hot_lava_sponge")), "{set:?}");
}

/// Armour Durability HUD (MIT): the armour the player wears, each piece's icon and what is left of it, on the HUD.
fn armour_durability_shows_what_is_worn() {
    let (game, data) = game();
    let snapshot = in_world(vec![
        worn("head", "minecraft:diamond_helmet", 120, 363),
        worn("chest", "minecraft:iron_chestplate", 0, 240),
        worn("feet", "minecraft:leather_boots", 60, 65),
        m::Worn {
            slot: "mainhand",
            item: "minecraft:torch".into(),
            count: 12,
            damage: 0,
            max_damage: 0,
        },
    ]);
    let items = |ops: &[HudOp]| {
        ops.iter()
            .filter(|op| matches!(op, HudOp::Item { .. }))
            .count()
    };
    let ops = ticks_until(&data, &snapshot, 30, |ops| items(ops) >= 3);
    println!("== HUD");
    print_ops(&ops);
    println!(
        "== kiln/java.log\n{}",
        std::fs::read_to_string(game.join("kiln/java.log")).unwrap_or_default()
    );
    let drawn: Vec<&str> = ops
        .iter()
        .filter_map(|op| match op {
            HudOp::Item { item, .. } => Some(item.as_str()),
            _ => None,
        })
        .collect();
    for piece in [
        "minecraft:diamond_helmet",
        "minecraft:iron_chestplate",
        "minecraft:leather_boots",
    ] {
        assert!(drawn.contains(&piece), "{piece} not drawn: {ops:?}");
    }
    assert!(!drawn.contains(&"minecraft:torch"), "{ops:?}");
    // Each piece's durability left, beside its icon: 243 of 363, 240 of 240, 5 of 65. (Other mods running beside
    // it draw text too: the percentages are this mod's.)
    let percentages: Vec<&str> = texts(&ops)
        .into_iter()
        .filter(|t| t.ends_with('%'))
        .collect();
    assert_eq!(percentages, ["67%", "100%", "8%"], "{ops:?}");
}

/// Better Than Mending (GPL-3.0): sneaking, a right click with a worn item that has Mending repairs it with the
/// player's experience (20 points for 40 durability while they have 30 or more).
fn better_than_mending_repairs_with_experience() {
    let (game, data) = game();
    announced(data, || m::hears("use_item"));
    assert!(m::hears("use_item"), "the mod doesn't listen to item uses");
    let pickaxe = r#"{components:{"minecraft:damage":300,"minecraft:enchantments":{"minecraft:mending":1}},count:1,id:"minecraft:diamond_pickaxe"}"#;
    let player = |sneaking: bool| m::PlayerSnapshot {
        uuid: 0x00c0_ffee,
        name: "Kiln".into(),
        dimension: "minecraft:overworld".into(),
        position: [0.5, 70.0, 0.5],
        game_mode: 0,
        health: 20.0,
        main: Some(("minecraft:diamond_pickaxe".into(), 1)),
        off: None,
        attributes: Vec::new(),
        sneaking,
        // Level 10: 160 points.
        experience: (10, 0.0, 160),
        main_stack: pickaxe.into(),
        off_stack: String::new(),
    };
    // The game's answers: mending repairs two durability a point.
    let asked = std::cell::RefCell::new(Vec::new());
    let mut game_answers = |what: &[String]| {
        asked.borrow_mut().push(what[..2.min(what.len())].join(" "));
        match what[0].as_str() {
            "enchantment_has" if what[1] == "minecraft:repair_with_xp" => {
                if what[2].contains("minecraft:mending") {
                    "1"
                } else {
                    "0"
                }
                .to_owned()
            }
            "repair_from_xp" => (what[1].parse::<i32>().unwrap() * 2).to_string(),
            "item" if what[1] == "minecraft:diamond_pickaxe" => "1561 1".to_owned(),
            "item" => "0 64".to_owned(),
            _ => String::new(),
        }
    };
    let world = game.join("world");
    // Not sneaking: the mod passes, the item's own use goes on.
    let standing = m::use_item(
        &world,
        100,
        "minecraft:diamond_pickaxe",
        m::Hand::Main,
        &player(false),
        &mut game_answers,
    )
    .expect("the use answers");
    assert_eq!(standing.result, m::UseResult::Pass, "{standing:?}");
    assert!(standing.effects.is_empty(), "{standing:?}");
    let sneaking = m::use_item(
        &world,
        101,
        "minecraft:diamond_pickaxe",
        m::Hand::Main,
        &player(true),
        &mut game_answers,
    )
    .expect("the use answers");
    println!("{sneaking:?}");
    println!("asked: {:?}", asked.borrow());
    assert_eq!(sneaking.result, m::UseResult::Success, "{sneaking:?}");
    let held = sneaking.effects.iter().find_map(|e| match e {
        m::Effect::Held {
            hand: m::Hand::Main,
            stack,
        } => Some(stack.clone()),
        _ => None,
    });
    let held = held.unwrap_or_else(|| panic!("the pickaxe wasn't repaired: {sneaking:?}"));
    assert!(held.contains(r#""minecraft:damage":260"#), "{held}");
    assert!(held.contains("minecraft:mending"), "{held}");
    let xp = sneaking.effects.iter().find_map(|e| match e {
        m::Effect::Experience { level, total, .. } => Some((*level, *total)),
        _ => None,
    });
    let (level, total) = xp.unwrap_or_else(|| panic!("no experience spent: {sneaking:?}"));
    assert!(total < 160 && level < 10, "{sneaking:?}");
}

/// Frames drawn every `every` (none between, when zero) for `seconds`, the game ticking among them as the window's
/// does; what the HUD shows at the end.
fn frames(snapshot: &m::Snapshot, every: Duration, seconds: f32) -> Vec<HudOp> {
    let deadline = Instant::now() + Duration::from_secs_f32(seconds);
    while Instant::now() < deadline {
        m::frame(|| snapshot.clone());
        if !every.is_zero() {
            std::thread::sleep(every);
        }
    }
    m::hud_ops()
}

/// Show FPS's counter, as the HUD shows it: the number, its colour, and whether a box is behind it.
fn fps_counter(ops: &[HudOp]) -> Option<(u32, u32, bool)> {
    ops.iter().find_map(|op| match op {
        HudOp::Text {
            x, y, argb, text, ..
        } => {
            let fps = text.strip_suffix(" FPS")?.parse().ok()?;
            let boxed = ops.iter().any(|b| {
                matches!(b, HudOp::Fill { x: bx, y: by, w, h, argb: 0x8000_0000 }
                    if bx <= x && by <= y && bx + w > *x && by + h > *y)
            });
            Some((fps, *argb, boxed))
        }
        _ => None,
    })
}

/// Show FPS (MIT): the frames of the last second on the HUD, over a dark box, coloured by how many (red under 60,
/// green to 120, blue above), out of the way while the debug screen (F3) is open; its settings in its own file.
fn show_fps_counts_frames() {
    let (game, data) = game();
    let snapshot = in_world(Vec::new());
    // A second's frames count once the second is over: each pace runs for two and a half.
    let slow = frames(&snapshot, Duration::from_millis(40), 2.5);
    println!("== about 25 frames a second");
    print_ops(&slow);
    let (fps, argb, boxed) =
        fps_counter(&slow).unwrap_or_else(|| panic!("no FPS counter: {slow:?}"));
    assert!(
        (10..=30).contains(&fps),
        "{fps} FPS at 40 ms a frame: {slow:?}"
    );
    assert_eq!(argb, 0xffff_5555, "under 60, red: {slow:?}");
    assert!(boxed, "no box behind the counter: {slow:?}");
    let steady = frames(&snapshot, Duration::from_millis(10), 2.5);
    println!("== about 90 frames a second");
    print_ops(&steady);
    let (fps, argb, _) =
        fps_counter(&steady).unwrap_or_else(|| panic!("no FPS counter: {steady:?}"));
    assert!(
        (60..=120).contains(&fps),
        "{fps} FPS at 10 ms a frame: {steady:?}"
    );
    assert_eq!(argb, 0xff55_ff55, "60 to 120, green: {steady:?}");
    let fast = frames(&snapshot, Duration::ZERO, 2.5);
    println!("== as fast as frames go");
    print_ops(&fast);
    let (fps, argb, _) = fps_counter(&fast).unwrap_or_else(|| panic!("no FPS counter: {fast:?}"));
    assert!(fps > 120, "{fps} FPS with no wait between frames: {fast:?}");
    assert_eq!(argb, 0xff55_55ff, "over 120, blue: {fast:?}");
    let debug = m::Snapshot {
        debug_shown: true,
        ..snapshot.clone()
    };
    let with_f3 = ticks_until(data, &debug, 5, |ops| fps_counter(ops).is_none());
    assert!(
        fps_counter(&with_f3).is_none(),
        "the counter stays over the debug screen: {with_f3:?}"
    );
    let settings = std::fs::read_to_string(game.join("kiln/config/showfps.json"))
        .unwrap_or_else(|e| panic!("no settings file kiln/config/showfps.json: {e}"));
    println!("== kiln/config/showfps.json\n{settings}");
    assert!(
        settings.contains("\"enabled\": true"),
        "its settings file says it is off: {settings}"
    );
    // Back to the HUD as it was, for the checks after this one.
    ticks_until(data, &snapshot, 5, |ops| fps_counter(ops).is_some());
}

/// The test's player for the server's calls, at full health with a maximum of `max_health`, holding `held`.
fn server_player(
    name: &str,
    uuid: u128,
    max_health: f64,
    held: Option<(&str, u32)>,
) -> m::PlayerSnapshot {
    m::PlayerSnapshot {
        uuid,
        name: name.into(),
        dimension: "minecraft:overworld".into(),
        position: [0.5, 64.0, 0.5],
        game_mode: 0,
        health: max_health as f32,
        main: held.map(|(item, count)| (item.to_owned(), count)),
        off: None,
        attributes: vec![("minecraft:max_health".into(), max_health, max_health)],
        ..Default::default()
    }
}

/// A use's effects: the maximum health it set, the hand's item and count after it, the sounds, and what the player
/// was told (over the hotbar or in chat).
fn use_effects(
    outcome: &m::Outcome,
) -> (Option<f64>, Option<(String, u32)>, Vec<String>, Vec<String>) {
    let mut max_health = None;
    let mut hand = None;
    let mut sounds = Vec::new();
    let mut told = Vec::new();
    for effect in &outcome.effects {
        match effect {
            m::Effect::Attribute { id, base } if id == "minecraft:max_health" => {
                max_health = Some(*base)
            }
            m::Effect::Hand { item, count, .. } => hand = Some((item.clone(), *count)),
            m::Effect::Sound { id, .. } => sounds.push(id.clone()),
            m::Effect::Overlay(text) | m::Effect::Tell(text) => told.push(text.to_string()),
            _ => {}
        }
    }
    (max_health, hand, sounds, told)
}

/// Heart Crystals (GPL-3.0), its code (its items come from Horizon's mod browser): a heart crystal used raises the
/// player's maximum health by one (half a heart), is spent and plays its sound; at the limit (80, its settings) it
/// says "Heart limit reached" and raises nothing; each player's hearts are kept with the world and given back when
/// they respawn.
fn heart_crystals_raise_max_health() {
    let (game, data) = game();
    announced(data, || m::has_use("heart_crystals:heart_crystal"));
    let world = game.join("world-heart-crystals");
    let crystal = "heart_crystals:heart_crystal";
    let mut air = |_: &[String]| "minecraft:air".to_owned();
    let joined = m::player_joined(
        &world,
        100,
        &server_player("Kiln", 0x00c0_ffee, 20.0, None),
        &mut air,
    );
    println!("== joined: {joined:?}");
    let mut player = server_player("Kiln", 0x00c0_ffee, 20.0, Some((crystal, 3)));
    for (n, count) in [2, 1, 0].into_iter().enumerate() {
        let outcome = m::use_item(
            &world,
            101 + n as u64,
            crystal,
            m::Hand::Main,
            &player,
            &mut air,
        )
        .expect("the use answers");
        println!("== use {}: {outcome:?}", n + 1);
        assert!(
            outcome.result.takes_use(),
            "the mod didn't take the use: {outcome:?}"
        );
        let (max_health, hand, sounds, _) = use_effects(&outcome);
        let raised = 21.0 + n as f64;
        assert_eq!(
            max_health,
            Some(raised),
            "the maximum health after use {}: {outcome:?}",
            n + 1
        );
        let (item, left) = hand.unwrap_or_else(|| panic!("the crystal wasn't spent: {outcome:?}"));
        assert_eq!(
            left,
            count,
            "crystals left after use {}: {outcome:?}",
            n + 1
        );
        assert!(
            count > 0 || item == "minecraft:air",
            "the hand isn't empty after the last crystal: {outcome:?}"
        );
        assert!(!sounds.is_empty(), "no sound: {outcome:?}");
        player.attributes = vec![("minecraft:max_health".into(), raised, raised)];
        player.health = raised as f32;
        player.main = (count > 0).then(|| (crystal.to_owned(), count));
    }
    let saved = std::fs::read_to_string(world.join("kiln/data/heart_crystals/health_data.json"))
        .unwrap_or_else(|e| panic!("the hearts weren't kept with the world: {e}"));
    println!("== kiln/data/heart_crystals/health_data.json\n{saved}");
    assert!(
        saved.contains("\"Kiln\": 23"),
        "the world doesn't keep the 23: {saved}"
    );
    // Respawning (back at vanilla's 20, as the game makes a new player): the 23 come back.
    let dead = m::PlayerSnapshot {
        health: 0.0,
        ..player.clone()
    };
    let respawned = m::player_respawned(
        &world,
        200,
        false,
        &dead,
        &server_player("Kiln", 0x00c0_ffee, 20.0, None),
        &mut air,
    )
    .expect("the respawn answers");
    println!("== respawned: {respawned:?}");
    let (max_health, ..) = use_effects(&respawned);
    assert_eq!(
        max_health,
        Some(23.0),
        "the hearts weren't given back on respawn: {respawned:?}"
    );
    // Another player uses crystals until the mod says the limit is reached: 60 raise them from 20 to 80, the next
    // raises nothing and isn't spent. (The mod keeps each player's hearts in its own data, not in the attribute.)
    let mut full = server_player("Full", 0x0000_f011, 20.0, Some((crystal, 64)));
    m::player_joined(&world, 300, &full, &mut air);
    let mut highest = 20.0;
    let mut limit = None;
    for n in 0..70 {
        let outcome = m::use_item(&world, 301 + n, crystal, m::Hand::Main, &full, &mut air)
            .expect("the use answers");
        let (max_health, hand, _, told) = use_effects(&outcome);
        // Its own text, which the game shows from the mod's language file ("Heart limit reached").
        if told
            .iter()
            .any(|t| t.contains("\"heart_crystals.limit_reached\""))
        {
            limit = Some((n, max_health, hand, outcome));
            break;
        }
        let raised =
            max_health.unwrap_or_else(|| panic!("use {} raised nothing: {outcome:?}", n + 1));
        highest = raised;
        full.attributes = vec![("minecraft:max_health".into(), raised, raised)];
        full.main = Some((crystal.to_owned(), 64));
    }
    let (uses, max_health, hand, outcome) =
        limit.unwrap_or_else(|| panic!("no limit after 70 crystals, at {highest}"));
    println!("== at the limit, after {uses} uses: {outcome:?}");
    assert_eq!(
        (uses, highest),
        (60, 80.0),
        "the limit isn't its settings' 80"
    );
    assert!(
        max_health.is_none_or(|h| h <= 80.0),
        "raised past the limit: {outcome:?}"
    );
    assert!(
        hand.is_none_or(|(_, n)| n == 64),
        "a crystal spent at the limit: {outcome:?}"
    );
}

/// Heart Crystals' shards in the game's treasure chests, which it means to add (a pool of 0 to 4 shards in ten of the
/// game's tables, by Fabric's LootTableEvents.MODIFY) and doesn't, on Fabric too: it fills a set with the tables'
/// Identifiers and looks the event's ResourceKey up in it, which never matches. Kiln runs the mod's change before a
/// world opens, as Horizon loads its tables, and must do as Fabric does: run it without a failure, and add nothing.
fn heart_crystals_loot_as_on_fabric(jar: &Path) {
    use horizon_kiln_mod::game::kiln_loot as loot;
    game();
    // A Kiln folder of its own with only this mod, as a world about to open has it.
    let built = Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln");
    let kiln = std::env::temp_dir().join(format!("kiln-coverage-{}-loot/kiln", std::process::id()));
    let _ = std::fs::remove_dir_all(&kiln);
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
    std::fs::copy(jar, kiln.join("mods").join(jar.file_name().unwrap())).unwrap();
    assert!(
        loot::uses_loot_events(jar),
        "Kiln doesn't see its loot change"
    );
    loot::prepare(&kiln);
    let file = kiln.join("generated").join(loot::FILE);
    let text = std::fs::read_to_string(&file)
        .unwrap_or_else(|e| panic!("its loot change didn't run ({e}): {}", file.display()));
    let java = std::fs::read_to_string(kiln.join("java-loot.log")).unwrap_or_default();
    println!("== {}\n{text}\n== java-loot.log\n{java}", loot::FILE);
    assert!(!java.contains("failed"), "its loot change failed: {java}");
    assert!(
        !text.contains("heart_crystals:heart_crystal_shard"),
        "shards in a table, which Fabric doesn't give: {text}"
    );
}

/// Illager Warship (CC0): its warship in the oceans of new worlds, as data Kiln hands to Horizon's world generator
/// (the world generation is Horizon's; kiln/proof.py finds the ship in a world). Its code starts with the others.
fn illager_warship_sails_the_oceans(jar: &Path) {
    use horizon_kiln_mod::game::kiln_structures as structures;
    game();
    let out = std::env::temp_dir().join(format!("kiln-coverage-{}-warship", std::process::id()));
    let generated = structures::generate(&[jar.to_path_buf()], &out);
    println!("== world generation: {generated:?}");
    assert_eq!(
        generated.mods.len(),
        1,
        "Kiln found no world generation in the jar: {generated:?}"
    );
    assert!(
        generated.skipped.is_empty(),
        "files left out: {generated:?}"
    );
    assert!(
        generated.pieces > 0 && generated.data > 0,
        "no data or no pieces: {generated:?}"
    );
    let pack = out.join("datapack/data/illagerwarship/worldgen");
    let read = |path: &str| -> serde_json::Value {
        let file = pack.join(path);
        let text =
            std::fs::read_to_string(&file).unwrap_or_else(|e| panic!("{}: {e}", file.display()));
        serde_json::from_str(&text).unwrap_or_else(|e| panic!("{}: {e}", file.display()))
    };
    let structure = read("structure/warship.json");
    let biomes: Vec<&str> = structure["biomes"]
        .as_array()
        .map(|b| b.iter().filter_map(|b| b.as_str()).collect())
        .unwrap_or_default();
    println!("== its biomes: {biomes:?}");
    // The oceans, named as Horizon reads them (with `minecraft:`).
    assert!(
        biomes.contains(&"minecraft:ocean") && biomes.contains(&"minecraft:deep_cold_ocean"),
        "not in the oceans: {structure}"
    );
    assert!(
        biomes
            .iter()
            .all(|b| b.starts_with("minecraft:") && b.ends_with("ocean")),
        "a biome that isn't an ocean: {structure}"
    );
    let set = read("structure_set/warship.json");
    assert_eq!(
        set["structures"][0]["structure"], "illagerwarship:warship",
        "its structure set: {set}"
    );
    assert_eq!(
        structure["start_pool"], "illagerwarship:warship",
        "its start pool: {structure}"
    );
    let pool = read("template_pool/warship.json");
    let pieces: Vec<&str> = pool["elements"]
        .as_array()
        .map(|e| {
            e.iter()
                .filter_map(|e| e["element"]["location"].as_str())
                .collect()
        })
        .unwrap_or_default();
    assert!(!pieces.is_empty(), "a pool without pieces: {pool}");
    for piece in pieces {
        let (namespace, path) = piece.split_once(':').unwrap_or(("minecraft", piece));
        let file = out.join(format!("structure/{namespace}/{path}.nbt"));
        let size = std::fs::metadata(&file).map(|m| m.len()).unwrap_or(0);
        println!("== piece {piece}: {size} bytes");
        assert!(
            size > 1000,
            "the pool's piece {piece} isn't where Horizon finds pieces"
        );
    }
}

/// A split's delta to the personal best on the timer's panel (the text after its time, `-0.350` or `+1.200`), and
/// its colour.
fn timer_delta(ops: &[HudOp], label: &str) -> Option<(String, u32)> {
    let drawn: Vec<(&str, u32)> = ops
        .iter()
        .filter_map(|op| match op {
            HudOp::Text { text, argb, .. } => Some((text.as_str(), *argb)),
            _ => None,
        })
        .collect();
    let at = drawn.iter().position(|(t, _)| *t == label)?;
    let (text, argb) = drawn.get(at + 2)?;
    (text.starts_with('-') || text.starts_with('+')).then(|| ((*text).to_owned(), *argb))
}

/// The timer's panel rows, label then time, as its HUD element draws them (label, time and delta as texts).
fn timer_rows(ops: &[HudOp]) -> Vec<(String, String)> {
    let texts = texts(ops);
    let labels = [
        "RTA",
        "IGT",
        "Nether",
        "Bastion",
        "Fortress",
        "Blind",
        "Stronghold",
        "End",
        "Finish",
        "PB!",
        "Final",
    ];
    texts
        .iter()
        .enumerate()
        .filter(|(_, t)| labels.contains(t))
        .map(|(i, t)| {
            (
                (*t).to_owned(),
                texts.get(i + 1).map_or(String::new(), |t| (*t).to_owned()),
            )
        })
        .collect()
}

/// A time as the timer writes it (`1:02.345`, `0:05.000`), in seconds.
fn seconds(time: &str) -> Option<f64> {
    let (minutes, rest) = time.split_once(':')?;
    Some(minutes.parse::<f64>().ok()? * 60.0 + rest.parse::<f64>().ok()?)
}

/// Kiln Speedrun Timer (ours): a new world starts the run by itself; advancements and trips between dimensions make
/// the splits on its panel (Nether, Bastion, Fortress, Blind, Stronghold, End); the pause doesn't count for IGT; the
/// dragon finishes the run, a first finish is a personal best, said in chat and kept in records.txt; U hides the
/// panel and keeps that in its settings.
fn the_timer_times_a_run() {
    let (game, data) = game();
    let mut chat = Vec::new();
    // The world's age: a new world is one under 100 ticks old.
    let game_time = std::cell::Cell::new(0);
    let mut made: Vec<String> = Vec::new();
    let at =
        |dimension: &str, made: &[String], paused: bool, ticks: usize, chat: &mut Vec<String>| {
            for _ in 0..ticks {
                game_time.set(game_time.get() + 1);
                let mut snapshot = in_world(Vec::new());
                snapshot.game_time = game_time.get();
                snapshot.paused = paused;
                snapshot.dimension = dimension.into();
                snapshot.advancements = Some(made.to_vec());
                if let Some(p) = snapshot.player.as_mut() {
                    p.dimension = dimension.into();
                }
                chat.extend(m::tick(&data, || None, || vec![6; 128], snapshot));
                std::thread::sleep(Duration::from_millis(50));
            }
            m::hud_ops()
        };
    // Out of the world (the title screen), then into a new one.
    let out = || m::Snapshot {
        connected: false,
        in_world: false,
        dimension: String::new(),
        player: None,
        ..in_world(Vec::new())
    };
    let leave = |chat: &mut Vec<String>| {
        for _ in 0..5 {
            chat.extend(m::tick(&data, || None, || vec![6; 128], out()));
            std::thread::sleep(Duration::from_millis(50));
        }
    };
    leave(&mut chat);
    let started = at("minecraft:overworld", &made, false, 10, &mut chat);
    println!("== a new world");
    print_ops(&started);
    let rows = timer_rows(&started);
    let rta = |rows: &[(String, String)]| {
        rows.iter()
            .find(|r| r.0 == "RTA")
            .and_then(|r| seconds(&r.1))
    };
    let igt = |rows: &[(String, String)]| {
        rows.iter()
            .find(|r| r.0 == "IGT")
            .and_then(|r| seconds(&r.1))
    };
    let first = rta(&rows).unwrap_or_else(|| panic!("no RTA on the panel: {started:?}"));
    assert!(
        first > 0.0,
        "the run didn't start by itself in a new world: {rows:?}"
    );
    assert!(
        rows.iter().any(|r| r.0 == "Nether" && r.1 == "-"),
        "no splits on the panel: {rows:?}"
    );
    // Paused for a second: RTA goes on, IGT doesn't.
    at("minecraft:overworld", &made, true, 20, &mut chat);
    let mut split = |advancement: Option<&str>, dimension: &str, chat: &mut Vec<String>| {
        if let Some(a) = advancement {
            made.push(a.to_owned());
        }
        at(dimension, &made, false, 4, chat)
    };
    split(
        Some("minecraft:story/enter_the_nether"),
        "minecraft:the_nether",
        &mut chat,
    );
    split(
        Some("minecraft:nether/find_bastion"),
        "minecraft:the_nether",
        &mut chat,
    );
    split(
        Some("minecraft:nether/find_fortress"),
        "minecraft:the_nether",
        &mut chat,
    );
    split(None, "minecraft:overworld", &mut chat);
    split(
        Some("minecraft:story/follow_ender_eye"),
        "minecraft:overworld",
        &mut chat,
    );
    let before_dragon = split(None, "minecraft:the_end", &mut chat);
    println!("== in the End");
    print_ops(&before_dragon);
    let rows = timer_rows(&before_dragon);
    for label in [
        "Nether",
        "Bastion",
        "Fortress",
        "Blind",
        "Stronghold",
        "End",
    ] {
        let time = rows
            .iter()
            .find(|r| r.0 == label)
            .map(|r| r.1.clone())
            .unwrap_or_default();
        assert!(seconds(&time).is_some(), "{label} not split: {rows:?}");
    }
    let finished = split(
        Some("minecraft:end/kill_dragon"),
        "minecraft:the_end",
        &mut chat,
    );
    println!("== the dragon");
    print_ops(&finished);
    println!("== chat\n{}", chat.join("\n"));
    let rows = timer_rows(&finished);
    assert!(
        rows.iter().any(|r| r.0 == "PB!"),
        "a first finish is a personal best: {rows:?}"
    );
    let (rta, igt) = (rta(&rows).unwrap_or(0.0), igt(&rows).unwrap_or(0.0));
    assert!(
        rta - igt >= 0.8,
        "the pause counted for IGT: RTA {rta}, IGT {igt}"
    );
    let final_line = chat
        .iter()
        .find(|l| l.contains("Final IGT"))
        .cloned()
        .unwrap_or_default();
    assert!(
        final_line.starts_with("[Kiln Timer] Final IGT")
            && final_line.contains("New personal best"),
        "no personal best said in chat: {chat:?}"
    );
    let records = std::fs::read_to_string(game.join("kiln/config/kiln_timer/records.txt"))
        .unwrap_or_default();
    assert!(
        records.contains("pb.FINISH="),
        "the run wasn't kept: {records}"
    );
    // U: the panel goes, and stays gone (its settings say so); U again brings it back.
    m::key("key.keyboard.u");
    let hidden = at("minecraft:the_end", &made, false, 4, &mut chat);
    assert!(
        timer_rows(&hidden).is_empty(),
        "the panel stays after U: {hidden:?}"
    );
    let settings = std::fs::read_to_string(game.join("kiln/config/kiln_timer/settings.properties"))
        .unwrap_or_default();
    assert!(
        settings.contains("shown=false"),
        "the hidden panel isn't kept in its settings: {settings}"
    );
    m::key("key.keyboard.u");
    let shown = at("minecraft:the_end", &made, false, 4, &mut chat);
    assert!(
        !timer_rows(&shown).is_empty(),
        "the panel doesn't come back after U: {shown:?}"
    );
    // A second run, in another new world: before a split is reached the panel shows the personal best's time for it;
    // a split reached sooner than the personal best's shows how much sooner in green, one reached later how much later
    // in red; a slower finish isn't a personal best and leaves the kept one alone.
    let pb = |records: &str| {
        records
            .lines()
            .find(|l| l.starts_with("pb.FINISH="))
            .map(str::to_owned)
    };
    let kept = pb(&records);
    leave(&mut chat);
    game_time.set(0);
    let made: Vec<String> = Vec::new();
    chat.clear();
    let again = at("minecraft:overworld", &made, false, 4, &mut chat);
    println!("== a second run");
    print_ops(&again);
    let rows = timer_rows(&again);
    assert!(
        rows.iter()
            .any(|r| r.0 == "Nether" && seconds(&r.1).is_some()),
        "the personal best's splits aren't on the panel before they're reached: {rows:?}"
    );
    let mut made = made;
    made.push("minecraft:story/enter_the_nether".into());
    // The Nether at once (sooner than the first run's, which waited 10 ticks), the bastion after two seconds.
    let sooner = at("minecraft:the_nether", &made, false, 40, &mut chat);
    made.push("minecraft:nether/find_bastion".into());
    let later = at("minecraft:the_nether", &made, false, 4, &mut chat);
    println!("== the Nether sooner, the bastion later");
    print_ops(&later);
    let (nether, nether_colour) = timer_delta(&sooner, "Nether")
        .unwrap_or_else(|| panic!("no delta for the Nether: {sooner:?}"));
    assert!(
        nether.starts_with('-') && nether_colour & 0xff_ffff == 0x55_ff55,
        "the Nether, reached sooner, isn't a green minus: {nether} {nether_colour:08x}"
    );
    let (bastion, bastion_colour) = timer_delta(&later, "Bastion")
        .unwrap_or_else(|| panic!("no delta for the bastion: {later:?}"));
    assert!(
        bastion.starts_with('+') && bastion_colour & 0xff_ffff == 0xff_5555,
        "the bastion, reached later, isn't a red plus: {bastion} {bastion_colour:08x}"
    );
    for (advancement, dimension) in [
        ("minecraft:nether/find_fortress", "minecraft:the_nether"),
        ("minecraft:story/follow_ender_eye", "minecraft:overworld"),
        ("minecraft:end/kill_dragon", "minecraft:the_end"),
    ] {
        made.push(advancement.into());
        at(dimension, &made, false, 4, &mut chat);
    }
    let slower = at("minecraft:the_end", &made, false, 2, &mut chat);
    println!("== the second run finished\n{}", chat.join("\n"));
    let rows = timer_rows(&slower);
    assert!(
        rows.iter().any(|r| r.0 == "Final") && !rows.iter().any(|r| r.0 == "PB!"),
        "a slower finish shows as a personal best: {rows:?}"
    );
    assert!(
        chat.iter()
            .any(|l| l.contains("Final IGT") && !l.contains("New personal best")),
        "a slower finish is said to be a personal best: {chat:?}"
    );
    let records = std::fs::read_to_string(game.join("kiln/config/kiln_timer/records.txt"))
        .unwrap_or_default();
    assert_eq!(
        pb(&records),
        kept,
        "a slower run replaced the personal best"
    );
}
