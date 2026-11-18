// Fabric's configuration phase with real Java (D-199): Kiln's test mod (kiln/test-mods/handshake) says hello to a
// joining player in BEFORE_CONFIGURE and in CONFIGURE (the second as Tech Reborn's ore depths go: a packet the
// connection sends), and welcomes them in JOIN. Horizon's configuration carries no mod's payloads, so Kiln runs the
// configuration's events at the player's connect, ahead of play's: the hellos leave first, after Kiln's id
// `kiln:configuration`. The window's receivers hear them before the welcome, and their thanks reach the server's
// configuration receiver. Needs a JDK and the jars from `sh tools/build-java.sh`.
//
//   sh tools/build-java.sh && cargo test -p horizon-kiln-mod --test handshake -- --ignored --nocapture

use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;
use serde_json::json;

const PLAYER: u128 = 0x00c0_ffee;
const HELLO: &[u8] = b"\x12kiln:configuration\x19kiln_test_handshake:hello";
const THANKS: &[u8] = b"\x12kiln:configuration\x1akiln_test_handshake:thanks";
const WELCOME: &[u8] = b"\x1bkiln_test_handshake:welcome";

fn game_folder() -> (PathBuf, PathBuf) {
    let built = Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln");
    let jar = built.join("test/kiln-test-handshake.jar");
    assert!(jar.is_file(), "run sh tools/build-java.sh first");
    let game = std::env::temp_dir().join(format!("kiln-handshake-{}", std::process::id()));
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
    std::fs::copy(&jar, kiln.join("mods/kiln-test-handshake.jar")).unwrap();
    let data = game.join("horizon-mods/.horizon-mod-data/kiln");
    std::fs::create_dir_all(&data).unwrap();
    (game, data)
}

fn player() -> m::PlayerSnapshot {
    m::PlayerSnapshot {
        uuid: PLAYER,
        name: "Kiln".into(),
        dimension: "minecraft:overworld".into(),
        position: [3.5, 65.0, -2.5],
        game_mode: 0,
        health: 20.0,
        attributes: vec![("minecraft:max_health".into(), 20.0, 20.0)],
        ..Default::default()
    }
}

fn answer(what: &[String]) -> String {
    match what[0].as_str() {
        "players" => m::players_answer(&[], Some(&player()), 10),
        _ => String::new(),
    }
}

/// Ticks the window's Java until `done` says what came is enough, or panics after 20 s; returns the chat lines.
fn tick_until(
    data: &Path,
    snapshot: &m::Snapshot,
    mut done: impl FnMut(&[String]) -> bool,
) -> Vec<String> {
    let deadline = Instant::now() + Duration::from_secs(20);
    let mut chat = Vec::new();
    while !done(&chat) {
        assert!(Instant::now() < deadline, "not yet; chat: {chat:?}");
        chat.extend(m::tick(data, || None, || vec![6; 128], snapshot.clone()));
        std::thread::sleep(m::TICK);
    }
    chat
}

#[test]
#[ignore = "needs a JDK and sh tools/build-java.sh"]
fn a_mods_configuration_comes_before_play_and_is_answered() {
    let (game, data) = game_folder();
    let snapshot = m::Snapshot {
        connected: true,
        in_world: true,
        game_time: 10,
        dimension: "minecraft:overworld".into(),
        player: Some(player()),
        ..m::Snapshot::default()
    };
    // The configuration's events ask for the connect call; its receiver for the players' messages.
    tick_until(&data, &snapshot, |_| {
        m::hears("connect") && m::hears("payload")
    });

    // The player connects: hello 1 (BEFORE_CONFIGURE), hello 7 (CONFIGURE), then play's welcome and JOIN's line.
    let world = game.join("world");
    let joined =
        m::player_connected(&world, 20, &player(), &mut answer).expect("the mod hears the connect");
    let sent: Vec<&Vec<u8>> = joined
        .effects
        .iter()
        .filter_map(|e| match e {
            m::Effect::Net { player, data } if *player == PLAYER => Some(data),
            _ => None,
        })
        .collect();
    assert_eq!(sent.len(), 3, "{:?}", joined.effects);
    assert_eq!(sent[0], &[HELLO, b"\x01"].concat());
    assert_eq!(sent[1], &[HELLO, b"\x07"].concat());
    assert_eq!(sent[2], &[WELCOME, b"\x07welcome"].concat());
    assert!(
        joined.effects.iter().any(|e| matches!(e, m::Effect::Tell(t)
            if *t == json!({"text": "Kiln Test Handshake: joined"}))),
        "{:?}",
        joined.effects
    );

    // The window hears them as they came: the hellos before the welcome, which says so in the chat.
    for message in &sent {
        m::from_server(message);
    }
    tick_until(&data, &snapshot, |chat| {
        chat.iter()
            .any(|l| l == "Kiln Test Handshake: welcome after hellos [1, 7]")
    });

    // Each hello was thanked over the configuration, its number doubled.
    let mut thanks = Vec::new();
    tick_until(&data, &snapshot, |_| {
        thanks.extend(m::take_net());
        thanks.len() >= 2
    });
    assert_eq!(
        thanks,
        vec![[THANKS, b"\x02"].concat(), [THANKS, b"\x0e"].concat()]
    );

    // The server's configuration receiver has the thanks and answers over the configuration too.
    let mut nothing = |_: &[String]| String::new();
    let heard = m::from_client(&world, 21, &thanks[1], &player(), &mut nothing)
        .expect("the receiver answers");
    let [
        m::Effect::Net {
            player: to,
            data: hello,
        },
    ] = &heard.effects[..]
    else {
        panic!("one payload back: {:?}", heard.effects);
    };
    assert_eq!(*to, PLAYER);
    assert_eq!(hello, &[HELLO, b"\x0f"].concat());
    let _ = std::fs::remove_dir_all(&game);
}
