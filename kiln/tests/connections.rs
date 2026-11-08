// The server's players and their connections with real Java (D-092): Kiln's proof mod (kiln/proof-mods/net) welcomes a
// joining player on Fabric's JOIN with how many are on the server (`PlayerLookup.all`), tells the others who joined
// and, on DISCONNECT, who left; when a player pings, the players who see them hear of it (`PlayerLookup.tracking`).
// Horizon's server and its players are played here as Horizon would: the players Java asks for are the server's
// listed at its tick's start, with the call's own player as they are now. The game proves it with a LAN guest
// (kiln/proof.py, KILN_PROOF_ONLY=net). Needs a JDK and the jars from `sh tools/build-java.sh`.
//
//   sh tools/build-java.sh && cargo test -p horizon-kiln-mod --test connections -- --ignored --nocapture

use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;

const PLAYER: u128 = 0x00c0_ffee;
/// Near the player, in the same level.
const GUEST: u128 = 0x0000_0bee;
/// In the same level, but two thousand blocks away: out of the player's sight.
const AWAY: u128 = 0x0000_0a0a;
/// In the Nether.
const NETHER: u128 = 0x0000_0f1e;

fn game_folder() -> (PathBuf, PathBuf) {
    let built = Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln");
    let jar = built.join("proof/kiln-proof-net.jar");
    assert!(jar.is_file(), "run sh tools/build-java.sh first");
    let game = std::env::temp_dir().join(format!("kiln-connections-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&game);
    let kiln = game.join("kiln");
    std::fs::create_dir_all(kiln.join("mods")).unwrap();
    std::fs::create_dir_all(kiln.join("lib")).unwrap();
    std::fs::copy(built.join("kiln-runtime.jar"), kiln.join("kiln-runtime.jar")).unwrap();
    for lib in m::jars(&built.join("lib")) {
        std::fs::copy(&lib, kiln.join("lib").join(lib.file_name().unwrap())).unwrap();
    }
    std::fs::copy(&jar, kiln.join("mods/kiln-proof-net.jar")).unwrap();
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

fn online(uuid: u128, name: &str, dimension: &str, position: [f64; 3]) -> m::Online {
    m::Online {
        uuid,
        name: name.into(),
        dimension: dimension.into(),
        position,
        game_mode: 0,
        health: 20.0,
        view_distance: 8,
    }
}

/// The server's players as its tick began: the player who is joining isn't listed yet.
fn listed() -> Vec<m::Online> {
    vec![
        online(GUEST, "Guest", "minecraft:overworld", [40.0, 64.0, 30.0]),
        online(AWAY, "Away", "minecraft:overworld", [2003.5, 64.0, -2.5]),
        online(NETHER, "Far", "minecraft:the_nether", [3.5, 65.0, -2.5]),
    ]
}

/// The game answering Java's questions during a call: the players, and nothing else asked here.
fn answer(what: &[String]) -> String {
    match what {
        [kind] if kind == "players" => m::players_answer(&listed(), Some(&player()), 10),
        _ => String::new(),
    }
}

/// The proof mod's news as its codec writes it: the type's id, then the line (a var-int length, then UTF-8).
fn news(line: &str) -> Vec<u8> {
    let mut bytes = vec![19];
    bytes.extend(b"kiln_proof_net:news");
    bytes.push(line.len() as u8);
    bytes.extend(line.as_bytes());
    bytes
}

/// Who each payload of `outcome` goes to, with what news, in order; any other effect is left out.
fn sent(outcome: &m::Outcome) -> Vec<(u128, Vec<u8>)> {
    outcome
        .effects
        .iter()
        .filter_map(|e| match e {
            m::Effect::Net { player, data } => Some((*player, data.clone())),
            _ => None,
        })
        .collect()
}

fn tick_until(data: &Path, snapshot: &m::Snapshot, mut done: impl FnMut(&[String]) -> bool) -> Vec<String> {
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
fn a_joining_player_gets_the_servers_first_sync_and_the_players_near_hear() {
    let (game, data) = game_folder();
    let snapshot = m::Snapshot {
        connected: true,
        in_world: true,
        game_time: 10,
        dimension: "minecraft:overworld".into(),
        player: Some(player()),
        ..m::Snapshot::default()
    };
    // A server's sync can come before the window's Java has started (a LAN guest's first tick): it waits for Java,
    // and for the window's player, then reaches the mod.
    m::from_server(&news("welcome early"));
    let chat = tick_until(&data, &snapshot, |chat| {
        m::hears("connect") && m::hears("disconnect") && chat.iter().any(|l| l.contains("welcome early"))
    });
    assert!(chat.contains(&"Kiln Proof Net: welcome early".to_owned()), "{chat:?}");

    // The player's first tick on the server: Fabric's JOIN. The joining player is on the server's list, the last.
    let world = game.join("world");
    let joined = m::player_connected(&world, 30, &player(), &mut answer).expect("the mod hears JOIN");
    assert_eq!(
        sent(&joined),
        [
            (PLAYER, news("welcome Kiln, 4 on the server")),
            (GUEST, news("Kiln joined, 4 on the server")),
            (AWAY, news("Kiln joined, 4 on the server")),
            (NETHER, news("Kiln joined, 4 on the server")),
        ]
    );

    // A ping: its pong goes back to the player, and only the player near them in their level sees it.
    let mut ping = vec![19];
    ping.extend(b"kiln_proof_net:ping\x01\x05hello");
    let pinged = m::from_client(&world, 31, &ping, &player(), &mut answer).expect("the server's receiver answers");
    let to: Vec<u128> = sent(&pinged).iter().map(|(p, _)| *p).collect();
    assert_eq!(to, [PLAYER, GUEST], "{pinged:?}");
    assert_eq!(sent(&pinged)[1].1, news("Kiln pinged near you"));

    // The player leaves: the others hear it, and nothing goes to the one who left.
    let mut left_answer = |what: &[String]| match what {
        [kind] if kind == "players" => m::players_answer(&listed(), Some(&player()), 10),
        _ => String::new(),
    };
    let left = m::player_disconnected(&world, 40, &player(), &mut left_answer).expect("the mod hears DISCONNECT");
    assert_eq!(
        sent(&left),
        [(GUEST, news("Kiln left")), (AWAY, news("Kiln left")), (NETHER, news("Kiln left"))]
    );

    // The welcome, once it reaches the window, is written in its chat.
    m::from_server(&sent(&joined)[0].1);
    let chat = tick_until(&data, &snapshot, |chat| chat.iter().any(|l| l.contains("on the server")));
    assert!(chat.contains(&"Kiln Proof Net: welcome Kiln, 4 on the server".to_owned()), "{chat:?}");
    let _ = std::fs::remove_dir_all(&game);
}
