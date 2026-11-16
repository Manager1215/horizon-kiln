// A Java mod's player events and an item's own use on Kiln's server side, with real Java: Kiln's test mod
// (kiln/test-mods/player) heals with its charm, welcomes a player who comes into a level, speaks after a respawn and
// names the block a sneaking player uses (Fabric's UseBlockCallback, the game's blocks too), harvests a grown crop,
// opens a door's neighbour and cuts cane or grows it with bone meal, reading the game's blocks as their classes (#149), as Horizon's server asks
// Kiln. Needs a JDK and the jars from `sh tools/build-java.sh`.
//
//   sh tools/build-java.sh && cargo test -p horizon-kiln-mod --test players -- --ignored --nocapture

use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;
use serde_json::json;

const CHARM: &str = "kiln_test_player:charm";
const PEBBLE: &str = "kiln_test_player:pebble";

/// A game folder with Kiln, the libraries and the test mod; returns it and the mod's data folder inside it.
fn game_folder() -> (PathBuf, PathBuf) {
    let built = Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln");
    let jar = built.join("test/kiln-test-player.jar");
    assert!(jar.is_file(), "run sh tools/build-java.sh first");
    let game = std::env::temp_dir().join(format!("kiln-players-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&game);
    let kiln = game.join("kiln");
    std::fs::create_dir_all(kiln.join("mods")).unwrap();
    std::fs::create_dir_all(kiln.join("lib")).unwrap();
    std::fs::copy(built.join("kiln-runtime.jar"), kiln.join("kiln-runtime.jar")).unwrap();
    for lib in m::jars(&built.join("lib")) {
        std::fs::copy(&lib, kiln.join("lib").join(lib.file_name().unwrap())).unwrap();
    }
    std::fs::copy(&jar, kiln.join("mods/kiln-test-player.jar")).unwrap();
    let data = game.join("horizon-mods/.horizon-mod-data/kiln");
    std::fs::create_dir_all(&data).unwrap();
    (game, data)
}

/// The player, at `health`, holding `main` (an item and how many).
fn player(health: f32, main: Option<(&str, u32)>) -> m::PlayerSnapshot {
    m::PlayerSnapshot {
        uuid: 0x00c0_ffee,
        name: "Kiln".into(),
        dimension: "minecraft:overworld".into(),
        position: [0.5, 65.0, 0.5],
        game_mode: 0,
        health,
        main: main.map(|(item, count)| (item.to_owned(), count)),
        attributes: vec![("minecraft:max_health".into(), 20.0, 20.0)],
        main_stack: main
            .map(|(item, count)| format!(r#"{{count:{count},id:"{item}"}}"#))
            .unwrap_or_default(),
        ..Default::default()
    }
}

#[test]
#[ignore = "needs a JDK and sh tools/build-java.sh"]
fn a_mods_player_events_and_its_items_use_answer() {
    let (game, data) = game_folder();
    let snapshot = m::Snapshot {
        connected: true,
        in_world: true,
        game_time: 10,
        dimension: "minecraft:overworld".into(),
        ..m::Snapshot::default()
    };
    // Kiln starts at the window's first tick in a world, ticking at the game's pace. Once the mod's entrypoint ran,
    // Java says which items have a use of their own, then which server events the mods hear.
    let deadline = Instant::now() + Duration::from_secs(20);
    while !m::hears("join") {
        assert!(Instant::now() < deadline, "the mod's events were never announced");
        m::tick(&data, || None, || vec![6; 128], snapshot.clone());
        std::thread::sleep(m::TICK);
    }
    assert!(m::hears("respawn"));
    assert!(!m::hears("use_item"), "no mod hears every item's use");
    assert!(m::hears("use_block"), "the mod hears every block's use");
    assert!(m::has_use(CHARM), "the charm's use is its own");
    assert!(!m::has_use(PEBBLE), "the pebble has the game's use");

    let world = game.join("world");
    let mut nothing = |_: &[String]| String::new();
    // The charm heals by 4 and is used up, one of three.
    let used = m::use_item(&world, 100, CHARM, m::Hand::Main, &player(10.0, Some((CHARM, 3))), &mut nothing)
        .expect("the charm's use answers");
    assert_eq!(used.result, m::UseResult::Success);
    assert_eq!(
        used.effects,
        [
            m::Effect::Health(14.0),
            m::Effect::Hand {
                hand: m::Hand::Main,
                item: CHARM.into(),
                count: 2
            }
        ]
    );
    // The pebble's use is the game's own: Java isn't asked.
    let pebble = m::use_item(&world, 101, PEBBLE, m::Hand::Main, &player(10.0, Some((PEBBLE, 1))), &mut nothing);
    assert_eq!(pebble, None);

    // A player comes into the world: the mod welcomes them in chat.
    let joined = m::player_joined(&world, 102, &player(20.0, None), &mut nothing).expect("the mod hears a join");
    assert_eq!(joined.effects, [m::Effect::Tell(json!({"text": "Welcome, Kiln"}))]);
    // Respawned after dying: the new player comes into the level (welcomed again), then the mod speaks over the
    // hotbar; what it would do to the old player goes nowhere.
    let respawned = m::player_respawned(&world, 103, false, &player(0.0, None), &player(20.0, None), &mut nothing)
        .expect("the mod hears a respawn");
    assert_eq!(
        respawned.effects,
        [
            m::Effect::Tell(json!({"text": "Welcome, Kiln"})),
            m::Effect::Overlay(json!({"text": "Back from the dead"}))
        ]
    );
    // Back from the End, alive.
    let back = m::player_respawned(&world, 104, true, &player(20.0, None), &player(20.0, None), &mut nothing)
        .expect("the mod hears a respawn");
    assert!(back.effects.contains(&m::Effect::Overlay(json!({"text": "Back from the End"}))), "{back:?}");

    // A sneaking player uses one of the game's blocks with an empty hand: the mod's UseBlockCallback takes the click
    // (Horizon's use of the shelf doesn't run) and reads the block from the game. Not sneaking, it passes.
    let shelf = "minecraft:oak_shelf[facing=north]";
    let mut level = |what: &[String]| match what[0].as_str() {
        "block" => shelf.to_owned(),
        _ => String::new(),
    };
    let sneaking = m::PlayerSnapshot { sneaking: true, ..player(20.0, None) };
    let at = [0, 200, 2];
    let used = m::block_used(&world, 105, at, shelf, "north", m::Hand::Main, &sneaking, &mut level)
        .expect("Java is asked about the game's block while a mod hears every block's use");
    assert_eq!(used.result, m::UseResult::Success);
    assert_eq!(used.effects, [m::Effect::Tell(json!({"text": "Used Block{minecraft:oak_shelf} at 0 200 2"}))]);
    let walking = m::block_used(&world, 106, at, shelf, "north", m::Hand::Main, &player(20.0, None), &mut level)
        .expect("Java is asked");
    assert_eq!(walking.result, m::UseResult::Pass);
    assert_eq!(walking.effects, []);

    // The game's blocks are their classes in Java (#149). Grown wheat on farmland is a CropBlock: the mod harvests
    // it (the game drops what the grown wheat drops, then breaks it without drops), replants it at age 0 with a seed
    // from the slot in hand, which the game's supports_crops tag lets stay there, and plays the crop's sound.
    let wheat = "minecraft:wheat[age=7]";
    let mut field = |what: &[String]| match what {
        [kind, x, y, z] if kind == "block" => match [x.as_str(), y.as_str(), z.as_str()] {
            ["0", "200", "2"] => wheat.to_owned(),
            ["0", "199", "2"] => "minecraft:farmland[moisture=7]".to_owned(),
            _ => "minecraft:air".to_owned(),
        },
        [kind, tag, block] if kind == "block_tag" => {
            (tag == "minecraft:supports_crops" && block == "minecraft:farmland").to_string()
        }
        _ => String::new(),
    };
    let farmer = player(20.0, Some(("minecraft:wheat_seeds", 4)));
    let harvested = m::block_used(&world, 107, at, wheat, "north", m::Hand::Main, &farmer, &mut field)
        .expect("Java is asked about the wheat");
    assert_eq!(harvested.result, m::UseResult::Success, "{harvested:?}");
    let drops = m::Effect::Drops { pos: at, state: wheat.into() };
    let destroy = m::Effect::Destroy { pos: at, drop: false };
    let replant = m::Effect::Block { pos: at, flags: 3, state: "minecraft:wheat[age=0]".into() };
    let place = |e: &m::Effect| harvested.effects.iter().position(|f| f == e);
    let (drops, destroy, replant) = (place(&drops), place(&destroy), place(&replant));
    assert!(drops.is_some() && drops < destroy && destroy < replant, "{harvested:?}");
    assert!(harvested.effects.contains(&m::Effect::Hand {
        hand: m::Hand::Main,
        item: "minecraft:wheat_seeds".into(),
        count: 3
    }), "{harvested:?}");
    assert!(harvested.effects.iter().any(|e| matches!(e, m::Effect::Sound { id, .. } if id == "minecraft:item.crop.plant")),
        "{harvested:?}");
    // In adventure mode the player may not change blocks: the mod passes.
    let visitor = m::PlayerSnapshot { game_mode: 2, ..farmer.clone() };
    let visited = m::block_used(&world, 108, at, wheat, "north", m::Hand::Main, &visitor, &mut field).expect("asked");
    assert_eq!((visited.result, visited.effects), (m::UseResult::Pass, vec![]));

    // A door is a DoorBlock with its halves, hinge and facing: the mod opens its neighbour (flags 10, the oak set's
    // sound) and passes, so the game opens the one clicked.
    let door = "minecraft:oak_door[facing=south,half=lower,hinge=left,open=false,powered=false]";
    let mut doors = |what: &[String]| match what {
        [kind, x, _, _] if kind == "block" && x == "0" => door.to_owned(),
        [kind, x, _, _] if kind == "block" && x == "-1" => door.replace("hinge=left", "hinge=right"),
        _ => "minecraft:air".to_owned(),
    };
    let opened = m::block_used(&world, 109, at, door, "north", m::Hand::Main, &player(20.0, None), &mut doors)
        .expect("Java is asked about the door");
    assert_eq!(opened.result, m::UseResult::Pass);
    assert!(opened.effects.contains(&m::Effect::Block {
        pos: [-1, 200, 2],
        flags: 10,
        state: "minecraft:oak_door[facing=south,half=lower,hinge=right,open=true,powered=false]".into()
    }), "{opened:?}");
    assert!(opened.effects.iter().any(|e| matches!(e, m::Effect::Sound { id, .. } if id == "minecraft:block.wooden_door.open")),
        "{opened:?}");

    // Sugar cane on sand (the game's supports_sugar_cane tag) with air above: the mod breaks it, with its drops.
    let cane = "minecraft:sugar_cane[age=0]";
    let mut shore = |what: &[String]| match what {
        [kind, _, y, _] if kind == "block" && y == "200" => cane.to_owned(),
        [kind, _, y, _] if kind == "block" && y == "199" => "minecraft:sand".to_owned(),
        [kind, tag, block] if kind == "block_tag" => {
            (tag == "minecraft:supports_sugar_cane" && block == "minecraft:sand").to_string()
        }
        _ => "minecraft:air".to_owned(),
    };
    let cut = m::block_used(&world, 110, at, cane, "north", m::Hand::Main, &player(20.0, None), &mut shore)
        .expect("Java is asked about the cane");
    assert_eq!((cut.result, cut.effects), (m::UseResult::Consume, vec![m::Effect::Destroy { pos: at, drop: true }]));

    // With bone meal in the hand the cane grows two blocks (`BlockPos.above(int)`), as Bonemeal Sugarcane's does.
    let meal = player(20.0, Some(("minecraft:bone_meal", 4)));
    let grown = m::block_used(&world, 111, at, cane, "north", m::Hand::Main, &meal, &mut shore)
        .expect("Java is asked about the cane");
    assert_eq!(grown.result, m::UseResult::Success, "{grown:?}");
    for y in [201, 202] {
        let block = m::Effect::Block { pos: [0, y, 2], flags: 3, state: cane.into() };
        assert!(grown.effects.contains(&block), "{y}: {grown:?}");
    }
    let _ = std::fs::remove_dir_all(&game);
}
