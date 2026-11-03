// Any Fabric mod's jar under Kiln with real Java, as the game drives it, without the game: a first look at whether
// it starts and what it draws. Needs a JDK and `sh tools/build-java.sh`; the jars are named in KILN_MOD. With
// KILN_USE (item ids, comma-separated), a player then uses three of each, one at a time, as the integrated server
// asks Kiln, and the effects are done to the player as the game would. With KILN_EVENTS, the player comes into the
// world first (at half health) and respawns after. With KILN_BLOCKS (block states, `;`-separated:
// `mymod:lamp[lit=false]`), each is clicked, ticked at random and stood on, as far as its mod's code does; the world
// around is grass. The mods' changes to the game's loot tables run last, as before a world opens
// (kiln/generated/loot.json).
//
//   KILN_MOD=path/to/mod.jar cargo test -p horizon-kiln-mod --test any_mod -- --ignored --nocapture
//   KILN_MOD=HeartCrystals.jar KILN_USE=heart_crystals:heart_crystal cargo test ...

use std::path::Path;
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;
use horizon_kiln_mod::game::kiln_jvm::HudOp;

#[test]
#[ignore = "needs a JDK, sh tools/build-java.sh and KILN_MOD"]
fn a_mod_runs_under_kiln() {
    let Some(mods) = std::env::var_os("KILN_MOD") else {
        println!("KILN_MOD names no jar: nothing to run");
        return;
    };
    let built = Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln");
    assert!(
        built.join("kiln-runtime.jar").is_file(),
        "run sh tools/build-java.sh first"
    );
    let game = std::env::temp_dir().join(format!("kiln-any-mod-{}", std::process::id()));
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

    // Two seconds in a world at 60 frames a second, a tick every third frame.
    let in_world = m::Snapshot {
        connected: true,
        in_world: true,
        paused: false,
        game_time: 100,
        dimension: "minecraft:overworld".into(),
        advancements: None,
    };
    let mut chat = Vec::new();
    let started = Instant::now();
    for frame in 0..120 {
        if frame % 3 == 0 {
            chat.extend(m::tick(&data, || None, || vec![6; 128], in_world.clone()));
        }
        chat.extend(m::frame(|| in_world.clone()));
        std::thread::sleep(Duration::from_millis(16));
    }
    std::thread::sleep(Duration::from_millis(300));
    chat.extend(m::frame(|| in_world.clone()));
    println!("== after {:?}", started.elapsed());
    println!("== chat");
    for line in &chat {
        println!("  {line}");
    }
    println!("== HUD");
    for op in m::hud_ops() {
        match op {
            HudOp::Text {
                x, y, argb, text, ..
            } => println!("  text at {x},{y} {argb:08x}: {text}"),
            HudOp::Fill { x, y, w, h, argb } => println!("  box at {x},{y} {w}x{h} {argb:08x}"),
        }
    }
    let events = std::env::var_os("KILN_EVENTS").is_some();
    // Kiln's Java asks about blocks during a call; here the world is all air.
    let mut air = |_: &[String]| "minecraft:air".to_owned();
    let world = game.join("world");
    if events {
        let player = m::PlayerSnapshot {
            health: 10.0,
            ..player(None)
        };
        show("join", m::player_joined(&world, 100, &player, &mut air));
    }
    for item in std::env::var("KILN_USE")
        .unwrap_or_default()
        .split(',')
        .filter(|i| !i.is_empty())
    {
        use_three(&world, item);
    }
    if events {
        let old = m::PlayerSnapshot {
            health: 0.0,
            ..player(None)
        };
        show(
            "respawn",
            m::player_respawned(&world, 200, false, &old, &player(None), &mut air),
        );
    }
    for file in walk(&world.join("kiln/data")) {
        println!("== {}", file.display());
        println!("{}", std::fs::read_to_string(&file).unwrap_or_default());
    }
    for state in std::env::var("KILN_BLOCKS")
        .unwrap_or_default()
        .split(';')
        .filter(|b| !b.is_empty())
    {
        blocks(&world, state);
    }
    let started = Instant::now();
    horizon_kiln_mod::game::kiln_loot::prepare(&kiln);
    let loot = kiln
        .join("generated")
        .join(horizon_kiln_mod::game::kiln_loot::FILE);
    println!("== loot changes ({:?})", started.elapsed());
    print!("{}", std::fs::read_to_string(&loot).unwrap_or_default());
    println!();
    println!("== kiln/java.log");
    print!(
        "{}",
        std::fs::read_to_string(kiln.join("java.log")).unwrap_or_default()
    );
}

/// The test's player, at full health with vanilla's maximum, holding three of `item`.
fn player(item: Option<&str>) -> m::PlayerSnapshot {
    m::PlayerSnapshot {
        uuid: 0x00c0_ffee,
        name: "Kiln".into(),
        dimension: "minecraft:overworld".into(),
        position: [0.5, 64.0, 0.5],
        game_mode: 0,
        health: 20.0,
        main: item.map(|i| (i.to_owned(), 3)),
        off: None,
        attributes: vec![("minecraft:max_health".into(), 20.0, 20.0)],
    }
}

/// A server call's outcome, for the test's output.
fn show(what: &str, outcome: Option<m::Outcome>) {
    println!("== {what}");
    match outcome {
        Some(outcome) => {
            println!("  {:?}", outcome.result);
            for effect in outcome.effects {
                println!("  {effect:?}");
            }
        }
        None => println!("  no answer"),
    }
}

/// A player with three of `item` uses them one at a time; each use's effects change the player for the next.
fn use_three(world: &Path, item: &str) {
    let mut player = player(Some(item));
    let mut air = |_: &[String]| "minecraft:air".to_owned();
    for _ in 0..3 {
        let started = Instant::now();
        let outcome = m::use_item(world, 100, item, m::Hand::Main, &player, &mut air);
        println!("== use {item} ({:?})", started.elapsed());
        let Some(outcome) = outcome else {
            println!("  no answer: the use goes on as Horizon's own");
            return;
        };
        println!("  {:?}", outcome.result);
        for effect in outcome.effects {
            println!("  {effect:?}");
            match effect {
                m::Effect::Attribute { id, base } => {
                    for a in player.attributes.iter_mut().filter(|a| a.0 == id) {
                        a.2 += base - a.1;
                        a.1 = base;
                    }
                }
                m::Effect::Health(health) => player.health = health,
                m::Effect::Hand { item, count, .. } => {
                    player.main = (count > 0).then_some((item, count));
                }
                _ => {}
            }
        }
    }
}

fn walk(dir: &Path) -> Vec<std::path::PathBuf> {
    let mut found = Vec::new();
    for entry in std::fs::read_dir(dir).into_iter().flatten().flatten() {
        let path = entry.path();
        if path.is_dir() {
            found.extend(walk(&path));
        } else {
            found.push(path);
        }
    }
    found
}

/// The block `state` at 0 64 0, on grass: clicked, ticked, stood on, its neighbour changed, placed and broken, as far
/// as its mod's code does.
fn blocks(world: &Path, state: &str) {
    let mut asked = Vec::new();
    let mut grass = |what: &[String]| {
        asked.push(what.join(" "));
        "minecraft:grass_block[snowy=false]".to_owned()
    };
    let at = [0, 64, 0];
    show(
        &format!("click {state}"),
        m::block_used(
            world,
            100,
            at,
            state,
            "up",
            m::Hand::Main,
            &player(None),
            &mut grass,
        ),
    );
    show(
        &format!("random tick {state}"),
        m::random_tick(world, 100, "minecraft:overworld", at, state, &mut grass),
    );
    show(
        &format!("standing on {state}"),
        m::stepped_on(world, 100, at, state, &player(None), &mut grass),
    );
    show(
        &format!("scheduled tick {state}"),
        m::scheduled_tick(world, 100, "minecraft:overworld", at, state, &mut grass),
    );
    show(
        &format!("neighbour changed {state}"),
        m::neighbor_changed(
            world,
            100,
            "minecraft:overworld",
            at,
            state,
            false,
            &mut grass,
        ),
    );
    let placement = m::Placement {
        pos: at,
        state: state.to_owned(),
        face: "up".into(),
        hit: [0.5, 64.0, 0.5],
        clicked: [0, 63, 0],
        // Facing north.
        yaw: 180.0,
        pitch: 30.0,
        sneaking: false,
        item: state.split('[').next().unwrap_or(state).to_owned(),
        replacing: false,
    };
    show(
        &format!("placing {state}, facing north"),
        m::placement(world, 100, "minecraft:overworld", &placement, &mut grass),
    );
    show(
        &format!("placed {state}"),
        m::placed(
            world,
            100,
            at,
            state,
            &placement.item,
            1,
            &player(None),
            &mut grass,
        ),
    );
    show(
        &format!("breaking {state}"),
        m::destroyed(world, 100, at, state, &player(None), &mut grass),
    );
    println!("== Java asked: {asked:?}");
}
