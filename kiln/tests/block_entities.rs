// A Java mod's block entities on Kiln's server side, with real Java: the counter and the crusher of Kiln's proof blocks
// (kiln/proof-mods/blocks), ticked as Horizon's server ticks them, with the compound they saved handed back each time.
// Needs a JDK and the jars from `sh tools/build-java.sh`.
//
//   sh tools/build-java.sh && cargo test -p horizon-kiln-mod --test block_entities -- --ignored --nocapture

use std::cell::RefCell;
use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;

const COUNTER: &str = "kiln_proof_blocks:counter";

/// A game folder with Kiln, the libraries and the proof blocks; returns it and the mod's data folder inside it.
fn game_folder() -> (PathBuf, PathBuf) {
    let built = Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln");
    let proof = built.join("proof/kiln-proof-blocks.jar");
    assert!(proof.is_file(), "run sh tools/build-java.sh first");
    let game = std::env::temp_dir().join(format!("kiln-block-entities-{}", std::process::id()));
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
        position: [3.5, 65.0, 0.5],
        game_mode: 0,
        health: 20.0,
        main: None,
        off: None,
        attributes: vec![("minecraft:max_health".into(), 20.0, 20.0)],
        ..Default::default()
    }
}

#[test]
#[ignore = "needs a JDK and sh tools/build-java.sh"]
fn a_mods_block_entity_ticks_and_keeps_its_data() {
    let (game, data) = game_folder();
    let snapshot = m::Snapshot {
        connected: true,
        in_world: true,
        paused: false,
        game_time: 10,
        dimension: "minecraft:overworld".into(),
        advancements: None,
        player: None,
        ..m::Snapshot::default()
    };
    // Kiln starts at the window's first tick in a world; the mod's blocks are announced once its entrypoint ran.
    let deadline = Instant::now() + Duration::from_secs(20);
    while m::block_entity_type(COUNTER).is_none() {
        assert!(Instant::now() < deadline, "the counter was never announced");
        m::tick(&data, || None, || vec![6; 128], snapshot.clone());
        std::thread::sleep(Duration::from_millis(10));
    }
    assert_eq!(m::block_entity_type(COUNTER).as_deref(), Some(COUNTER));
    assert!(m::block_does(COUNTER, "ticks"));
    assert!(m::block_does(COUNTER, "use"));

    // The game as Kiln's Java reads it: the counter at `at`, its compound as it last saved it, air elsewhere.
    let world = game.join("world");
    let at = [3, 64, -2];
    let state = RefCell::new(format!("{COUNTER}[facing=north,lit=false]"));
    let saved = RefCell::new("{}".to_owned());
    let mut game_answers = |what: &[String]| {
        let here = what[1..] == at.map(|c| c.to_string());
        match what[0].as_str() {
            "block" if here => state.borrow().clone(),
            "block_entity" if here => format!("{COUNTER} {}", saved.borrow()),
            "block" => "minecraft:air".to_owned(),
            _ => String::new(),
        }
    };

    let mut lit_at = None;
    let started = Instant::now();
    for tick in 1..=45 {
        let now = state.borrow().clone();
        let before = saved.borrow().clone();
        let outcome = m::block_entity_tick(
            &world,
            1000 + tick,
            "minecraft:overworld",
            at,
            &now,
            COUNTER,
            &before,
            &mut game_answers,
        )
        .expect("the counter's tick answers");
        for effect in outcome.effects {
            match effect {
                m::Effect::BlockEntity { pos, data } => {
                    assert_eq!(pos, at);
                    *saved.borrow_mut() = data;
                }
                m::Effect::Block {
                    pos, state: lit, ..
                } => {
                    assert_eq!(pos, at);
                    *state.borrow_mut() = lit;
                    lit_at.get_or_insert(tick);
                }
                other => panic!("the counter's tick did {other:?}"),
            }
        }
    }
    println!("45 ticks of the counter: {:?} each", started.elapsed() / 45);
    // Loaded once: the same object ticked 45 times, its fields (a codec's string, a long) saved each time.
    assert_eq!(lit_at, Some(40));
    assert_eq!(*state.borrow(), format!("{COUNTER}[facing=north,lit=true]"));
    assert_eq!(
        *saved.borrow(),
        r#"{loads:1,placed_in:"minecraft:overworld",started:1001L,ticks:45}"#
    );

    // A click: the block reads its block entity from the game (getBlockEntity), says its count and starts again.
    let now = state.borrow().clone();
    let outcome = m::block_used(
        &world,
        2000,
        at,
        &now,
        "up",
        m::Hand::Main,
        &player(),
        &mut game_answers,
    )
    .expect("the click answers");
    assert_eq!(outcome.result, m::UseResult::Success);
    assert_eq!(
        outcome.effects,
        [
            m::Effect::Overlay(serde_json::json!({
                "text": "Counter at 45, loaded 1 time(s), in minecraft:overworld"
            })),
            m::Effect::Block {
                pos: at,
                flags: 3,
                state: format!("{COUNTER}[facing=north,lit=false]")
            },
            m::Effect::BlockEntity {
                pos: at,
                data: r#"{loads:1,placed_in:"minecraft:overworld",started:1001L,ticks:0}"#.into()
            },
        ]
    );

    // The game's compound changed behind Kiln's back (a command, another mod): Kiln loads the object again.
    *saved.borrow_mut() = r#"{loads:1,placed_in:"minecraft:the_nether",started:5L,ticks:7}"#.into();
    let now = state.borrow().clone();
    let before = saved.borrow().clone();
    let outcome = m::block_entity_tick(
        &world,
        3000,
        "minecraft:overworld",
        at,
        &now,
        COUNTER,
        &before,
        &mut game_answers,
    )
    .expect("the counter's tick answers");
    assert_eq!(
        outcome.effects,
        [m::Effect::BlockEntity {
            pos: at,
            data: r#"{loads:2,placed_in:"minecraft:the_nether",started:5L,ticks:8}"#.into()
        }]
    );

    crusher(&world);
    let _ = std::fs::remove_dir_all(&game);
}

/// The crusher: a block entity that is a container. Its slots go to Horizon as its container (what each face
/// reaches); its items come and go as the `Items` vanilla saves, which Horizon writes from the container's slots.
fn crusher(world: &Path) {
    const CRUSHER: &str = "kiln_proof_blocks:crusher";
    assert_eq!(
        m::block_slots(CRUSHER),
        Some(m::Slots {
            count: 2,
            faces: [vec![1], vec![0], vec![0], vec![0], vec![0], vec![0]],
            inputs: vec![0],
            outputs: vec![1],
        })
    );
    let at = [0, 64, 0];
    let mut state = format!("{CRUSHER}[facing=north,lit=false]");
    // Three cobblestone, as a hopper above left them: Horizon's own encoding of the slots.
    let mut saved = r#"{Items:[{Slot:0b,count:3,id:"minecraft:cobblestone"}]}"#.to_owned();
    let mut air = |_: &[String]| String::new();
    for _ in 0..10 {
        let outcome = m::block_entity_tick(
            world,
            100,
            "minecraft:overworld",
            at,
            &state,
            CRUSHER,
            &saved,
            &mut air,
        )
        .expect("the crusher's tick answers");
        for effect in outcome.effects {
            match effect {
                m::Effect::BlockEntity { data, .. } => saved = data,
                m::Effect::Block { state: lit, .. } => state = lit,
                other => panic!("the crusher's tick did {other:?}"),
            }
        }
    }
    assert_eq!(state, format!("{CRUSHER}[facing=north,lit=true]"));
    assert_eq!(
        saved,
        r#"{Items:[{Slot:0b,count:2,id:"minecraft:cobblestone"},{Slot:1b,count:1,id:"minecraft:gravel"}],crushed:1,progress:0}"#
    );
}
