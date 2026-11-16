// The Java mods' server commands with real Java (D-107): Kiln's test mod (kiln/test-mods/commands) registers a Brigadier
// tree with Fabric's CommandRegistrationCallback; Horizon's server asks Kiln for its mods' commands as usages, and hands
// it a player's line for one of them, which Java runs as that player at their operator level. Needs a JDK and the jars
// from `sh tools/build-java.sh`.
//
//   sh tools/build-java.sh && cargo test -p horizon-kiln-mod --test commands -- --ignored --nocapture

use std::collections::BTreeSet;
use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;
use serde_json::json;

/// A game folder with Kiln, the libraries and the test mod; returns it and the mod's data folder inside it.
fn game_folder() -> (PathBuf, PathBuf) {
    let built = Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln");
    let jar = built.join("test/kiln-test-commands.jar");
    assert!(jar.is_file(), "run sh tools/build-java.sh first");
    let game = std::env::temp_dir().join(format!("kiln-commands-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&game);
    let kiln = game.join("kiln");
    std::fs::create_dir_all(kiln.join("mods")).unwrap();
    std::fs::create_dir_all(kiln.join("lib")).unwrap();
    std::fs::copy(built.join("kiln-runtime.jar"), kiln.join("kiln-runtime.jar")).unwrap();
    for lib in m::jars(&built.join("lib")) {
        std::fs::copy(&lib, kiln.join("lib").join(lib.file_name().unwrap())).unwrap();
    }
    std::fs::copy(&jar, kiln.join("mods/kiln-test-commands.jar")).unwrap();
    let data = game.join("horizon-mods/.horizon-mod-data/kiln");
    std::fs::create_dir_all(&data).unwrap();
    (game, data)
}

/// The player Kiln, at `health`.
fn player(health: f32) -> m::PlayerSnapshot {
    m::PlayerSnapshot {
        uuid: 0x00c0_ffee,
        name: "Kiln".into(),
        dimension: "minecraft:overworld".into(),
        position: [0.5, 65.0, 0.5],
        game_mode: 0,
        health,
        attributes: vec![("minecraft:max_health".into(), 20.0, 20.0)],
        ..Default::default()
    }
}

/// The lines a command's answer has, each as plain text (its parts joined), and whether all are red.
fn said(outcome: &m::Outcome) -> Vec<String> {
    fn text(v: &serde_json::Value) -> String {
        let mut s = v["text"].as_str().or(v["translate"].as_str()).unwrap_or("").to_owned();
        for part in v["extra"].as_array().into_iter().flatten() {
            s.push_str(&text(part));
        }
        s
    }
    outcome
        .effects
        .iter()
        .filter_map(|e| match e {
            m::Effect::Feedback(v) => Some(text(v)),
            _ => None,
        })
        .collect()
}

#[test]
#[ignore = "needs a JDK and sh tools/build-java.sh"]
fn a_mods_server_commands_are_told_as_usages_and_run_as_the_player() {
    let (game, data) = game_folder();
    let snapshot = m::Snapshot {
        connected: true,
        in_world: true,
        game_time: 10,
        dimension: "minecraft:overworld".into(),
        ..m::Snapshot::default()
    };
    // Horizon asked before Java ran: nothing to tell. Once the mod's entrypoint ran, Java says a mod registers server
    // commands, and the server is to ask again.
    let world = game.join("world");
    let mut nothing = |_: &[String]| String::new();
    assert_eq!(m::server_commands(&world, 5, &mut nothing), None);
    let deadline = Instant::now() + Duration::from_secs(20);
    while !m::server_commands_stale() {
        assert!(Instant::now() < deadline, "the mod's commands were never announced");
        m::tick(&data, || None, || vec![6; 128], snapshot.clone());
        std::thread::sleep(m::TICK);
    }

    // Each node that runs is a usage; the operator's branch needs level 2; a word Horizon's usages don't take ("Loud")
    // and the alias's redirect end in the rest of the line, which Java parses.
    let usages: BTreeSet<(String, u8)> = m::server_commands(&world, 11, &mut nothing)
        .expect("the mod's commands are told")
        .into_iter()
        .collect();
    let expected: BTreeSet<(String, u8)> = [
        ("kilntest hello", 0),
        ("kilntest add <a:integer> <b:integer>", 0),
        ("kilntest op", 2),
        ("kilntest heal", 0),
        ("kilntest whois <target:player>", 0),
        ("kilntest say <message:text>", 0),
        ("kilntest fail", 0),
        ("kilntest [kiln_rest:text]", 0),
        ("kt [kiln_rest:text]", 0),
        ("kilntest pos <at:block_pos>", 0),
        ("kilntest id <id:text>", 0),
        ("kilntest block <state:text>", 0),
        ("kilntest profile <who:player>", 0),
        ("kilntest dim <d:text>", 0),
        ("kilntest played", 0),
        ("kilntest creative", 0),
        ("kilntest hover", 0),
        ("kilntest contents", 0),
        ("kilntest rename <name:text>", 0),
        ("kilntest lore <line:text>", 0),
        ("kilntest shown", 0),
        ("kilntest xp <n:integer>", 0),
        ("kilntest tryid <ns:word> <path:word>", 0),
        ("kilntest color <c:text>", 0),
    ]
    .into_iter()
    .map(|(u, l)| (u.to_owned(), l))
    .collect();
    assert_eq!(usages, expected);
    assert!(!m::server_commands_stale(), "told: not asked again");
    assert!(m::is_server_command("kilntest hello"));
    assert!(m::is_server_command("kt"));
    assert!(!m::is_server_command("give @s minecraft:dirt"), "the game's own commands stay the game's");

    let mut run = |line: &str, level: u8, health: f32| {
        m::server_command(&world, 12, level, line, &player(health), &mut nothing).expect("the command answers")
    };
    let hello = run("kilntest hello", 0, 20.0);
    assert_eq!(hello.result, m::UseResult::Success);
    assert_eq!(hello.effects, [m::Effect::Feedback(json!({"text": "Hello, Kiln"}))]);
    assert_eq!(said(&run("kilntest add 2 3", 0, 20.0)), ["5"]);
    assert_eq!(said(&run("kilntest say hello  there", 0, 20.0)), ["hello  there"]);
    assert_eq!(said(&run("kt hello", 0, 20.0)), ["Hello, Kiln"], "the alias");
    assert_eq!(said(&run("kilntest Loud", 0, 20.0)), ["LOUD"]);

    // What the command did to its player comes back with its answer.
    let healed = run("kilntest heal", 0, 10.0);
    assert!(healed.effects.contains(&m::Effect::Health(14.0)), "{healed:?}");

    // An operator's branch: unknown below level 2, as the game says, with where the line went wrong ("kilntest op",
    // then "<--[HERE]" in the player's language).
    let denied = run("kilntest op", 0, 20.0);
    assert_eq!(denied.result, m::UseResult::Fail);
    assert_eq!(said(&denied), ["command.unknown.argument", "kilntest opcommand.context.here"]);
    assert_eq!(said(&run("kilntest op", 2, 20.0)), ["You are an operator"]);

    // A player by name; a selector only for an operator.
    assert_eq!(said(&run("kilntest whois Kiln", 0, 20.0)), ["Found Kiln"]);
    assert_eq!(said(&run("kilntest whois @s", 2, 20.0)), ["Found Kiln"]);
    assert_eq!(said(&run("kilntest whois @s", 0, 20.0)), ["argument.entity.selector.not_allowed"]);
    assert_eq!(said(&run("kilntest whois Nobody", 0, 20.0)), ["argument.entity.notfound.player"]);

    // The mod's own failure, in red; a bad number, with where.
    let failed = run("kilntest fail", 0, 20.0);
    assert_eq!(failed.result, m::UseResult::Fail);
    assert_eq!(said(&failed), ["Nope"]);
    match &failed.effects[0] {
        m::Effect::Feedback(v) => assert_eq!(v["color"], "red", "{v}"),
        other => panic!("{other:?}"),
    }
    let bad = run("kilntest add 2 500", 0, 20.0);
    assert_eq!(bad.result, m::UseResult::Fail);
    assert_eq!(said(&bad).len(), 2, "{bad:?}");

    // The game's argument types mods use most (#133): a position, absolute, relative to the player (at 0.5 65 0.5) and
    // local; an id; a block state; players by name and selector; a dimension.
    assert_eq!(said(&run("kilntest pos 1 2 3", 0, 20.0)), ["1.50 2.00 3.50"], "whole x and z: a block's middle");
    assert_eq!(said(&run("kilntest pos ~ ~1.5 ~-1", 0, 20.0)), ["0.50 66.50 -0.50"]);
    assert_eq!(said(&run("kilntest pos ^ ^ ^", 0, 20.0)), ["0.50 65.00 0.50"]);
    assert_eq!(said(&run("kilntest pos 1 2", 0, 20.0))[0], "argument.pos3d.incomplete");
    assert_eq!(said(&run("kilntest pos ~ ^ ~", 0, 20.0))[0], "argument.pos.mixed");
    assert_eq!(said(&run("kilntest id stone", 0, 20.0)), ["minecraft:stone"]);
    assert_eq!(said(&run("kilntest id mymod:tools/saw", 0, 20.0)), ["mymod:tools/saw"]);
    let block = said(&run("kilntest block minecraft:oak_stairs[facing=east]", 0, 20.0));
    assert!(block[0].contains("oak_stairs") && block[0].contains("facing=east"), "{block:?}");
    assert_eq!(
        said(&run("kilntest profile Kiln", 0, 20.0)),
        ["Kiln 00000000-0000-0000-0000-000000c0ffee"]
    );
    assert_eq!(said(&run("kilntest profile @s", 2, 20.0)), ["Kiln 00000000-0000-0000-0000-000000c0ffee"]);
    assert_eq!(said(&run("kilntest profile Nobody", 0, 20.0)), ["argument.player.unknown"]);
    assert_eq!(said(&run("kilntest dim minecraft:the_nether", 0, 20.0)), ["minecraft:the_nether"]);
    assert_eq!(said(&run("kilntest dim minecraft:moon", 0, 20.0)), ["argument.dimension.invalid"]);

    // The player's statistics are Horizon's, asked for during the command; their name and UUID; a game mode set.
    let mut stats = |what: &[String]| match what {
        [kind, uuid, ty, key] if kind == "stat" && uuid == "00000000-0000-0000-0000-000000c0ffee"
            && ty == "minecraft:custom" && key == "minecraft:play_time" => "1234".to_owned(),
        _ => String::new(),
    };
    let played = m::server_command(&world, 13, 0, "kilntest played", &player(20.0), &mut stats).unwrap();
    assert_eq!(said(&played), ["Kiln played 1234"]);
    assert_eq!(said(&run("kilntest creative", 0, 20.0)), ["changed"]);

    // What a component holds, and a hover on it: as vanilla's JSON.
    assert_eq!(said(&run("kilntest contents", 0, 20.0)), ["death.attack.mob"]);
    let hover = run("kilntest hover", 0, 20.0);
    match &hover.effects[0] {
        m::Effect::Feedback(v) => {
            assert_eq!(v["hover_event"], json!({"action": "show_text", "value": {"text": "hi"}}), "{v}")
        }
        other => panic!("{other:?}"),
    }

    // The held stack renamed and given lore, as item-rename mods do (#133): the stack goes back to the game whole, its
    // name and lore as vanilla saves them; a name the game saved is read back.
    let holding = |stack: &str| m::PlayerSnapshot {
        main: Some(("minecraft:diamond_sword".into(), 1)),
        main_stack: stack.into(),
        ..player(20.0)
    };
    let mut none = |_: &[String]| String::new();
    let mut item_run = |line: &str, stack: &str| {
        m::server_command(&world, 14, 0, line, &holding(stack), &mut none).expect("the command answers")
    };
    let sword = r#"{count:1,id:"minecraft:diamond_sword"}"#;
    let held = |outcome: &m::Outcome| {
        outcome
            .effects
            .iter()
            .find_map(|e| match e {
                m::Effect::Held {
                    hand: m::Hand::Main,
                    stack,
                } => Some(stack.clone()),
                _ => None,
            })
            .unwrap_or_else(|| panic!("the stack didn't change: {outcome:?}"))
    };
    let renamed = item_run("kilntest rename Kiln Blade", sword);
    assert_eq!(said(&renamed), ["Kiln Blade"]);
    let stack = held(&renamed);
    assert!(stack.contains(r#""minecraft:custom_name""#) && stack.contains("Kiln Blade") && stack.contains("gold"), "{stack}");
    let lore = item_run("kilntest lore Forged in Kiln", sword);
    assert_eq!(said(&lore), ["1 lines"]);
    let stack = held(&lore);
    assert!(stack.contains(r#""minecraft:lore""#) && stack.contains("Forged in Kiln"), "{stack}");
    let named = r#"{components:{"minecraft:custom_name":"Old Blade"},count:1,id:"minecraft:diamond_sword"}"#;
    assert_eq!(said(&item_run("kilntest rename New Blade", named)), ["New Blade"]);
    let shown = item_run("kilntest shown", named);
    match &shown.effects[0] {
        m::Effect::Feedback(v) => {
            assert_eq!(v["translate"], "chat.square_brackets", "{v}");
            assert_eq!(v["with"][0]["extra"][0]["text"], "Old Blade", "{v}");
            assert_eq!(v["with"][0]["italic"], true, "a custom name is italic: {v}");
            assert_eq!(v["hover_event"]["action"], "show_item", "{v}");
            assert_eq!(v["hover_event"]["id"], "minecraft:diamond_sword", "{v}");
        }
        other => panic!("{other:?}"),
    }

    // Experience given as vanilla counts it: 10 points from none are level 1 (7 points) and 3 of the next 9.
    let xp = run("kilntest xp 10", 0, 20.0);
    assert_eq!(said(&xp), ["level 1"]);
    let given = xp.effects.iter().find_map(|e| match e {
        m::Effect::Experience { level, progress, total } => Some((*level, *progress, *total)),
        _ => None,
    });
    let (level, progress, total) = given.unwrap_or_else(|| panic!("no experience: {xp:?}"));
    assert_eq!((level, total), (1, 10));
    assert!((progress - 3.0 / 9.0).abs() < 1e-4, "{progress}");

    // An id built only from allowed characters; a colour by name or #RRGGBB.
    assert_eq!(said(&run("kilntest tryid mymod saw.big", 0, 20.0)), ["mymod:saw.big"]);
    assert_eq!(said(&run("kilntest tryid My mod", 0, 20.0)), ["null"]);
    assert_eq!(said(&run("kilntest color dark_red", 0, 20.0)), ["dark_red"]);
    assert_eq!(said(&run("kilntest color #ff8800", 0, 20.0)), ["#FF8800"]);
    assert_eq!(said(&run("kilntest color mauve", 0, 20.0)), ["invalid"]);
    let _ = std::fs::remove_dir_all(&game);
}
