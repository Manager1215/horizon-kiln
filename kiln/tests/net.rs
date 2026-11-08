// A mod's own payloads with real Java (D-088): Kiln's proof mod (kiln/proof-mods/net) registers a ping for the server
// and a pong for the client. The window's Java sends the ping when the player types the mod's command; the server's
// receiver tells the player in chat and answers with a pong for that player; the window's receiver writes the pong in
// the chat. Horizon's channel for mods carries both in the game, which kiln/proof.py proves (KILN_PROOF_ONLY=net);
// here the test hands each message from one side to the other as Horizon would. Needs a JDK and the jars from
// `sh tools/build-java.sh`.
//
//   sh tools/build-java.sh && cargo test -p horizon-kiln-mod --test net -- --ignored --nocapture

use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;
use serde_json::json;

const PLAYER: u128 = 0x00c0_ffee;

/// A game folder with Kiln, the libraries and the proof mod; returns it and the mod's data folder inside it.
fn game_folder() -> (PathBuf, PathBuf) {
    let built = Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln");
    let jar = built.join("proof/kiln-proof-net.jar");
    assert!(jar.is_file(), "run sh tools/build-java.sh first");
    let game = std::env::temp_dir().join(format!("kiln-net-{}", std::process::id()));
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

/// Ticks the window's Java until `done` says what came is enough, or panics after 20 s; returns the chat lines.
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
fn a_mods_payload_goes_to_the_server_and_its_answer_comes_back() {
    let (game, data) = game_folder();
    let snapshot = m::Snapshot {
        connected: true,
        in_world: true,
        game_time: 10,
        dimension: "minecraft:overworld".into(),
        player: Some(player()),
        ..m::Snapshot::default()
    };
    // Once the mod's entrypoints ran, Java says the server's mods receive payloads; the mod's client command comes
    // with the client's first ticks in a world.
    tick_until(&data, &snapshot, |_| m::hears("payload") && m::client_command("kiln_proof_net"));

    // The command the player typed sends a ping: a message for Horizon's channel, the type's id then the payload.
    let mut sent = Vec::new();
    tick_until(&data, &snapshot, |_| {
        sent.extend(m::take_net());
        !sent.is_empty()
    });
    assert_eq!(sent.len(), 1, "one command, one ping: {sent:?}");
    let ping = &sent[0];
    let id = b"kiln_proof_net:ping";
    assert_eq!(ping[0] as usize, id.len(), "the id's length, a var-int");
    assert_eq!(&ping[1..=id.len()], id);
    // The ping's number (a var-int) and its word, as the game's codecs write them.
    assert_eq!(&ping[id.len() + 1..], b"\x01\x05hello");

    // At the player's tick, the server's receiver has it: it tells the player and answers them.
    let world = game.join("world");
    let mut nothing = |_: &[String]| String::new();
    let heard = m::from_client(&world, 20, ping, &player(), &mut nothing).expect("the server's receiver answers");
    assert_eq!(heard.result, m::UseResult::Pass);
    let [m::Effect::Tell(told), m::Effect::Net { player: to, data: pong }] = &heard.effects[..] else {
        panic!("a line in chat, then a payload: {:?}", heard.effects);
    };
    assert_eq!(*told, json!({"text": "Kiln Proof Net: the server got ping 1 (hello) from Kiln"}));
    assert_eq!(*to, PLAYER, "the pong goes to the player who pinged");
    assert!(pong.starts_with(b"\x13kiln_proof_net:pong\x01\x01"), "{pong:?}");

    // A message of a type no mod registered, or one that doesn't read, does nothing on the server, which goes on.
    let unknown = m::from_client(&world, 21, b"\x0ekiln_proof:who", &player(), &mut nothing).expect("an answer");
    assert!(unknown.effects.is_empty(), "{unknown:?}");
    assert_eq!(m::from_client(&world, 22, b"\x13kiln_proof_net:ping", &player(), &mut nothing), None);

    // The pong comes to the window: its receiver writes in the chat what the server saw.
    m::from_server(pong);
    let chat = tick_until(&data, &snapshot, |chat| chat.iter().any(|l| l.contains("pong")));
    assert!(
        chat.contains(
            &"Kiln Proof Net: pong 1 (hello) back, 1 served, at 3 65 -3 for 00000000-0000-0000-0000-000000c0ffee"
                .to_owned()
        ),
        "{chat:?}"
    );
    // The next ping is the second, served second.
    assert!(m::client_command("kiln_proof_net"));
    let mut sent = Vec::new();
    tick_until(&data, &snapshot, |_| {
        sent.extend(m::take_net());
        !sent.is_empty()
    });
    let again = m::from_client(&world, 23, &sent[0], &player(), &mut nothing).expect("an answer");
    assert!(matches!(&again.effects[1], m::Effect::Net { data, .. } if data.starts_with(b"\x13kiln_proof_net:pong\x02\x02")));
    let _ = std::fs::remove_dir_all(&game);
}
