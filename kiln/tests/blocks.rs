// A Java mod's blocks on Kiln's server side, with real Java: the switch, the sprout and the timer of Kiln's proof
// blocks (kiln/proof-mods/blocks), clicked, stood on, ticked at random and on schedule, placed, changed beside and
// broken, as Horizon's server asks Kiln; and what their code did, as the game would do it. Needs a JDK and the jars
// from `sh tools/build-java.sh`.
//
//   sh tools/build-java.sh && cargo test -p horizon-kiln-mod --test blocks -- --ignored --nocapture

use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;

const SWITCH: &str = "kiln_proof_blocks:switch";
const SPROUT: &str = "kiln_proof_blocks:sprout";
const TIMER: &str = "kiln_proof_blocks:timer";

/// A game folder with Kiln, the libraries and the proof blocks; returns it and the mod's data folder inside it.
fn game_folder() -> (PathBuf, PathBuf) {
    let built = Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln");
    let proof = built.join("proof/kiln-proof-blocks.jar");
    assert!(proof.is_file(), "run sh tools/build-java.sh first");
    let game = std::env::temp_dir().join(format!("kiln-blocks-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&game);
    let kiln = game.join("kiln");
    std::fs::create_dir_all(kiln.join("mods")).unwrap();
    std::fs::create_dir_all(kiln.join("lib")).unwrap();
    std::fs::copy(built.join("kiln-runtime.jar"), kiln.join("kiln-runtime.jar")).unwrap();
    for jar in m::jars(&built.join("lib")) {
        std::fs::copy(&jar, kiln.join("lib").join(jar.file_name().unwrap())).unwrap();
    }
    std::fs::copy(&proof, kiln.join("mods/kiln-proof-blocks.jar")).unwrap();
    let data = game.join("horizon-mods/.horizon-mod-data/kiln");
    std::fs::create_dir_all(&data).unwrap();
    (game, data)
}

fn player() -> m::PlayerSnapshot {
    m::PlayerSnapshot {
        uuid: 0x00c0_ffee,
        name: "Kiln".into(),
        dimension: "minecraft:overworld".into(),
        position: [0.5, 65.0, 2.5],
        game_mode: 0,
        health: 20.0,
        attributes: vec![("minecraft:max_health".into(), 20.0, 20.0)],
        ..Default::default()
    }
}

/// The states the call set (`Level.setBlock`), in order.
fn set(outcome: &m::Outcome) -> Vec<String> {
    outcome
        .effects
        .iter()
        .filter_map(|e| match e {
            m::Effect::Block { state, .. } => Some(state.clone()),
            _ => None,
        })
        .collect()
}

/// What the call said over the player's hotbar.
fn overlays(outcome: &m::Outcome) -> Vec<String> {
    outcome
        .effects
        .iter()
        .filter_map(|e| match e {
            m::Effect::Overlay(text) => Some(text["text"].as_str().unwrap_or_default().to_owned()),
            _ => None,
        })
        .collect()
}

#[test]
#[ignore = "needs a JDK and sh tools/build-java.sh"]
fn a_mods_blocks_do_what_their_code_says() {
    let (game, data) = game_folder();
    let snapshot = m::Snapshot {
        connected: true,
        in_world: true,
        game_time: 10,
        dimension: "minecraft:overworld".into(),
        ..m::Snapshot::default()
    };
    // Kiln starts at the window's first tick in a world, ticking at the game's pace; the mod's blocks are announced
    // once its entrypoint ran (once: the game's levels build their tables of ticking block entities again from it),
    // and the status box says one mod runs.
    assert_eq!(m::blocks_announced(), 0, "nothing is announced before Java starts");
    let deadline = Instant::now() + Duration::from_secs(20);
    while !m::block_does(SWITCH, "use") {
        assert!(Instant::now() < deadline, "the switch was never announced");
        m::tick(&data, || None, || vec![6; 128], snapshot.clone());
        std::thread::sleep(m::TICK);
    }
    assert_eq!(m::blocks_announced(), 1);
    assert_eq!(m::status_lines(), [(m::status_line(Some(1), None), m::INK)]);
    for (block, what) in [(SWITCH, "step"), (SPROUT, "random"), (TIMER, "tick"), (TIMER, "neighbor")] {
        assert!(m::block_does(block, what), "{block} doesn't say it does {what}");
    }
    assert!(!m::block_does(SPROUT, "use"));

    // The world as Kiln's Java reads it: stone everywhere but where the block is.
    let world = game.join("world");
    let at = [0, 64, 0];
    let mut stone = |what: &[String]| match what[0].as_str() {
        "block" => "minecraft:stone".to_owned(),
        _ => String::new(),
    };

    // The switch: a click turns it on and names what it stands on; standing on it turns it on, and on again, nothing.
    let off = format!("{SWITCH}[lit=false]");
    let click = m::block_used(&world, 100, at, &off, "up", m::Hand::Main, &player(), &mut stone)
        .expect("the switch takes the click");
    println!("click: {click:?}");
    assert_eq!(click.result, m::UseResult::Success);
    assert_eq!(set(&click), [format!("{SWITCH}[lit=true]")]);
    assert_eq!(overlays(&click), ["Switch on, on minecraft:stone"]);
    let step = m::stepped_on(&world, 101, at, &off, &player(), &mut stone).expect("the switch has a step");
    assert_eq!(set(&step), [format!("{SWITCH}[lit=true]")]);
    let again = m::stepped_on(&world, 102, at, &format!("{SWITCH}[lit=true]"), &player(), &mut stone)
        .expect("the switch has a step");
    assert_eq!(set(&again), Vec::<String>::new());

    // The sprout ripens on its random tick.
    let ripe = m::random_tick(&world, 103, "minecraft:overworld", at, &format!("{SPROUT}[ripe=false]"), &mut stone)
        .expect("the sprout ticks at random");
    assert_eq!(set(&ripe), [format!("{SPROUT}[ripe=true]")]);

    // The timer: placed by a player looking north, it faces them (south) and asks for its tick in a second, which
    // lights it; a change beside puts it out; broken, it says so and drops a nether star.
    let placement = m::Placement {
        pos: at,
        state: format!("{TIMER}[facing=north,lit=false]"),
        face: "up".into(),
        hit: [0.5, 64.0, 0.5],
        clicked: [0, 63, 0],
        yaw: 180.0,
        pitch: 30.0,
        sneaking: false,
        item: TIMER.into(),
        replacing: false,
    };
    let placing = m::placement(&world, 104, "minecraft:overworld", &placement, &mut stone)
        .expect("the timer has a placement");
    assert_eq!(placing.effects, [m::Effect::Placement(Some(format!("{TIMER}[facing=south,lit=false]")))]);
    let facing = format!("{TIMER}[facing=south,lit=false]");
    let placed = m::placed(&world, 105, at, &facing, TIMER, 1, &player(), &mut stone).expect("the timer hears it");
    assert_eq!(
        placed.effects,
        [m::Effect::Schedule {
            pos: at,
            block: TIMER.into(),
            delay: 20
        }]
    );
    let lit = m::scheduled_tick(&world, 125, "minecraft:overworld", at, &facing, &mut stone)
        .expect("the timer has a scheduled tick");
    assert_eq!(set(&lit), [format!("{TIMER}[facing=south,lit=true]")]);
    let lit = format!("{TIMER}[facing=south,lit=true]");
    let out = m::neighbor_changed(&world, 126, "minecraft:overworld", at, &lit, false, &mut stone)
        .expect("the timer hears its neighbours");
    assert_eq!(set(&out), [facing.clone()]);
    let broken = m::destroyed(&world, 127, at, &facing, &player(), &mut stone).expect("the timer hears it break");
    assert_eq!(overlays(&broken), ["Timer broken"]);
    assert!(
        broken.effects.contains(&m::Effect::Drop {
            pos: at,
            item: "minecraft:nether_star".into(),
            count: 1
        }),
        "{broken:?}"
    );
}
