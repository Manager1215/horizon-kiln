// Fabric's synced data attachments with real Java (D-199): Kiln's test mod (kiln/test-mods/attachments) sets a
// player's visits in a type synced to that player only, and a level's tick count in one synced to everyone in it. The
// server's calls give each as a message for Horizon's channel after Kiln's id `kiln:attachment`; the test hands it to
// the window as Horizon would, and the window's side of the mod reads it from its own player and level and writes it in
// the chat. Needs a JDK and the jars from `sh tools/build-java.sh`.
//
//   sh tools/build-java.sh && cargo test -p horizon-kiln-mod --test attachment_sync -- --ignored --nocapture

use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;

fn game_folder() -> (PathBuf, PathBuf) {
    let built = Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln");
    let jar = built.join("test/kiln-test-attachments.jar");
    assert!(jar.is_file(), "run sh tools/build-java.sh first");
    let game = std::env::temp_dir().join(format!("kiln-attachment-sync-{}", std::process::id()));
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
    std::fs::copy(&jar, kiln.join("mods/kiln-test-attachments.jar")).unwrap();
    let data = game.join("horizon-mods/.horizon-mod-data/kiln");
    std::fs::create_dir_all(&data).unwrap();
    (game, data)
}

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

fn answer(what: &[String]) -> String {
    match what[0].as_str() {
        "players" => m::players_answer(&[], Some(&player(20.0)), 10),
        "loaded" => "true".to_owned(),
        _ => String::new(),
    }
}

/// The `Net` effects of an outcome that are synced attachments (D-199): Kiln's id `kiln:attachment` first.
fn synced(outcome: &m::Outcome) -> Vec<(u128, Vec<u8>)> {
    outcome
        .effects
        .iter()
        .filter_map(|e| match e {
            m::Effect::Net { player, data } if data.starts_with(b"\x0fkiln:attachment") => {
                Some((*player, data.clone()))
            }
            _ => None,
        })
        .collect()
}

#[test]
#[ignore = "needs a JDK and sh tools/build-java.sh"]
fn a_synced_attachment_reaches_the_players_window() {
    let (game, data) = game_folder();
    let snapshot = m::Snapshot {
        connected: true,
        in_world: true,
        game_time: 10,
        dimension: "minecraft:overworld".into(),
        player: Some(player(20.0)),
        ..m::Snapshot::default()
    };
    let deadline = Instant::now() + Duration::from_secs(20);
    while !m::hears("level_tick_start") || !m::hears("connect") {
        assert!(
            Instant::now() < deadline,
            "the mod's events were never announced"
        );
        m::tick(&data, || None, || vec![6; 128], snapshot.clone());
        std::thread::sleep(m::TICK);
    }
    let world = game.join("world");
    let mut game_time = 100;

    // The player connects: the mod sets their synced visits (targetOnly) in JOIN, which goes to them alone, an entity's
    // message: the kind 1, their UUID, the type's id, present, then the value as the type's codec writes it.
    game_time += 1;
    let joined = m::player_connected(&world, game_time, &player(20.0), &mut answer)
        .expect("the mod hears JOIN");
    let sent = synced(&joined);
    assert_eq!(
        sent.len(),
        1,
        "one synced value, to its player: {:?}",
        joined.effects
    );
    let (to, message) = &sent[0];
    assert_eq!(*to, 0x00c0_ffee);
    let id = b"kiln_test_attachments:greeted";
    let at = 16;
    assert_eq!(message[at], 1, "an entity's: {message:?}");
    assert_eq!(
        &message[at + 1..at + 17],
        &0x00c0_ffee_u128.to_be_bytes(),
        "its UUID"
    );
    assert_eq!(message[at + 17] as usize, id.len());
    assert_eq!(&message[at + 18..at + 18 + id.len()], id);
    assert_eq!(
        &message[at + 18 + id.len()..],
        b"\x01\x01",
        "present, one visit"
    );

    // The window keeps it for its player, whose mod reads it there.
    m::from_server(message);
    let deadline = Instant::now() + Duration::from_secs(20);
    let mut chat = Vec::new();
    while !chat.iter().any(|l: &String| l.contains("greeted 1")) {
        assert!(
            Instant::now() < deadline,
            "the window's mod never read it; chat: {chat:?}"
        );
        chat.extend(m::tick(&data, || None, || vec![6; 128], snapshot.clone()));
        std::thread::sleep(m::TICK);
    }

    // A level tick sets the level's synced clock (all): it goes to everyone in the level, the player here.
    let mut ticked = None;
    let deadline = Instant::now() + Duration::from_secs(20);
    while ticked.is_none() {
        assert!(Instant::now() < deadline, "no level tick ran");
        game_time += 1;
        ticked = m::server_tick(
            &world,
            game_time,
            m::TickPhase::LevelStart,
            "minecraft:overworld",
            &mut answer,
        );
    }
    let sent = synced(&ticked.unwrap());
    assert_eq!(sent.len(), 1, "{sent:?}");
    assert_eq!(sent[0].1[at], 0, "a level's");
    m::from_server(&sent[0].1);
    let deadline = Instant::now() + Duration::from_secs(20);
    while !chat.iter().any(|l: &String| l.contains("clock 1")) {
        assert!(
            Instant::now() < deadline,
            "the window's mod never read the clock; chat: {chat:?}"
        );
        chat.extend(m::tick(&data, || None, || vec![6; 128], snapshot.clone()));
        std::thread::sleep(m::TICK);
    }
    let _ = std::fs::remove_dir_all(&game);
}
