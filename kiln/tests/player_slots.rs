// What a mod's server command reaches of its player (D-209), with real Java: Kiln's test mod
// (kiln/test-mods/player-slots) reads and changes the player's ender chest, saves their inventory, armour and ender chest
// as an inventory snapshot mod does, opens a chest's and a crafting table's menu, and asks who the player is. The game
// answers for the command's own player only. Needs a JDK and the jars from `sh tools/build-java.sh`.
//
//   sh tools/build-java.sh && cargo test -p horizon-kiln-mod --test player_slots -- --ignored --nocapture

use std::cell::RefCell;
use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;

/// A game folder with Kiln, the libraries and the test mod; returns it and the mod's data folder inside it.
fn game_folder() -> (PathBuf, PathBuf) {
    let built = Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln");
    let jar = built.join("test/kiln-test-player-slots.jar");
    assert!(jar.is_file(), "run sh tools/build-java.sh first");
    let game = std::env::temp_dir().join(format!("kiln-player-slots-{}", std::process::id()));
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
    std::fs::copy(&jar, kiln.join("mods/kiln-test-player-slots.jar")).unwrap();
    let data = game.join("horizon-mods/.horizon-mod-data/kiln");
    std::fs::create_dir_all(&data).unwrap();
    (game, data)
}

const KILN: &str = "00000000-0000-0000-0000-000000c0ffee";

/// The player Kiln (or another, by `uuid`), holding a diamond sword.
fn player(uuid: u128) -> m::PlayerSnapshot {
    m::PlayerSnapshot {
        uuid,
        name: "Kiln".into(),
        dimension: "minecraft:overworld".into(),
        position: [0.5, 65.0, 0.5],
        health: 20.0,
        attributes: vec![("minecraft:max_health".into(), 20.0, 20.0)],
        main: Some(("minecraft:diamond_sword".into(), 1)),
        main_stack: r#"{count:1,id:"minecraft:diamond_sword"}"#.into(),
        ..Default::default()
    }
}

/// The lines a command's answer says, as plain text.
fn said(outcome: &m::Outcome) -> Vec<String> {
    outcome
        .effects
        .iter()
        .filter_map(|e| match e {
            m::Effect::Feedback(v) => Some(v["text"].as_str().unwrap_or("").to_owned()),
            _ => None,
        })
        .collect()
}

/// What the command moved in the ender chest, if anything.
fn ender_chest(outcome: &m::Outcome) -> Option<String> {
    outcome.effects.iter().find_map(|e| match e {
        m::Effect::EnderChest { data } => Some(data.clone()),
        _ => None,
    })
}

#[test]
#[ignore = "needs a JDK and sh tools/build-java.sh"]
fn a_command_reaches_its_players_ender_chest_and_inventory_as_the_game_has_them() {
    let (game, data) = game_folder();
    let snapshot = m::Snapshot {
        connected: true,
        in_world: true,
        game_time: 10,
        dimension: "minecraft:overworld".into(),
        ..m::Snapshot::default()
    };
    let world = game.join("world");
    let mut nothing = |_: &[String]| String::new();
    let deadline = Instant::now() + Duration::from_secs(20);
    while !m::server_commands_stale() {
        assert!(
            Instant::now() < deadline,
            "the mod's commands were never announced"
        );
        m::tick(&data, || None, || vec![6; 128], snapshot.clone());
        std::thread::sleep(m::TICK);
    }
    let usages = m::server_commands(&world, 11, &mut nothing).expect("the mod's commands are told");
    assert!(
        usages.iter().any(|(u, _)| u == "kilnslots ender put"),
        "{usages:?}"
    );

    // The game's answers, for Kiln only: the hotbar's fifth slot in hand (the sword), five diamonds in the first, boots
    // and a helmet on; sixteen obsidian in the ender chest's fourth slot.
    let asked = RefCell::new(Vec::new());
    let mut game_answers = |what: &[String]| {
        asked.borrow_mut().push(what.join(" "));
        match what {
            [kind, uuid] if kind == "ender_chest" && uuid == KILN => {
                r#"{Items:[{Slot:3b,count:16,id:"minecraft:obsidian"}]}"#.to_owned()
            }
            [kind, uuid] if kind == "inventory" && uuid == KILN => concat!(
                r#"4 {Items:[{Slot:0b,count:5,id:"minecraft:diamond"},{Slot:4b,count:1,id:"minecraft:diamond_sword"},"#,
                r#"{Slot:36b,count:1,id:"minecraft:iron_boots"},{Slot:39b,count:1,id:"minecraft:iron_helmet"}]}"#
            )
            .to_owned(),
            [kind] if kind == "permission" => "2".to_owned(),
            _ => String::new(),
        }
    };
    let mut run = |line: &str, uuid: u128, level: u8| {
        asked.borrow_mut().clear();
        m::server_command(&world, 12, level, line, &player(uuid), &mut game_answers)
            .expect("the command answers")
    };

    // Read: the game's slots, asked once; nothing goes back.
    let read = run("kilnslots ender", 0x00c0_ffee, 0);
    assert_eq!(said(&read), ["3:obsidianx16"]);
    assert_eq!(ender_chest(&read), None, "nothing changed");
    assert_eq!(
        *asked.borrow(),
        [format!("ender_chest {KILN}")],
        "the inventory isn't asked for"
    );

    // Changed: what Java read and what it left, for the game to change only the slots that still hold what was read.
    let put = run("kilnslots ender put", 0x00c0_ffee, 0);
    assert_eq!(said(&put), ["0:ender_pearlx3,3:obsidianx16"]);
    assert_eq!(
        ender_chest(&put).as_deref(),
        Some(concat!(
            r#"{Before:[{Slot:3b,count:16,id:"minecraft:obsidian"}],"#,
            r#"Items:[{Slot:0b,count:3,id:"minecraft:ender_pearl"},{Slot:3b,count:16,id:"minecraft:obsidian"}]}"#
        ))
    );
    let take = run("kilnslots ender take", 0x00c0_ffee, 0);
    assert_eq!(said(&take), ["took 16"]);
    assert_eq!(
        ender_chest(&take).as_deref(),
        Some(r#"{Before:[{Slot:3b,count:16,id:"minecraft:obsidian"}],Items:[]}"#)
    );

    // Another player's ender chest: the game has only the command's player's, so it is empty and nothing goes back.
    let other = run("kilnslots ender put", 0xbeef, 0);
    assert_eq!(said(&other), ["0:ender_pearlx3"]);
    assert_eq!(ender_chest(&other), None);

    // A snapshot as invshots saves one: the 36 slots (the slot in hand the held sword), the armour by vanilla's
    // equipment slots, the ender chest; through a compressed file and back.
    let shot = run("kilnslots snapshot", 0x00c0_ffee, 0);
    assert_eq!(
        said(&shot),
        ["0:diamondx5,4:diamond_swordx1 | 102:iron_boots,105:iron_helmet | 3:obsidianx16"]
    );
    assert!(
        asked.borrow().contains(&format!("inventory {KILN}")),
        "{:?}",
        asked.borrow()
    );

    // The ender chest in a chest's menu (its 27 slots and the player's 36), as /ec opens it, then closed; a crafting
    // table's anywhere (its result, 9 cells and the player's 36), as /craft opens it.
    assert_eq!(
        said(&run("kilnslots chest", 0x00c0_ffee, 0)),
        ["chest 63 16, closed true"]
    );
    assert_eq!(
        said(&run("kilnslots craft", 0x00c0_ffee, 0)),
        ["crafting 46"]
    );

    // Who the player is: alive, at the command's level, no single-player owner here, one set of registries.
    assert_eq!(
        said(&run("kilnslots who", 0x00c0_ffee, 2)),
        ["Kiln alive true level 2 owner false registries true"]
    );
    let _ = std::fs::remove_dir_all(&game);
}
