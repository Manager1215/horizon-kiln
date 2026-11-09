// A mod's payload Java answers late, with real Java (D-088, D-109): on a loaded computer Java can take longer than the
// 250 ms Kiln's server waits for a call. A payload is a player's own input that nothing waits on, so Kiln gives up
// waiting, and what the mod does with it is done when Java's answer comes, at the player's next tick. A receiver that
// looks up the players (as most do, to send the others what changed) asks the game during the call, and the game
// gave up on it: Java used to stop the call there (`a late payload failed in Java: kiln.Host$GaveUp`), so the
// player's payload was lost. Kiln's test mod (kiln/test-mods/late) takes as long as its payload says, then looks up
// the players and answers. Needs a JDK and the jars from `sh tools/build-java.sh`.
//
//   sh tools/build-java.sh && cargo test -p horizon-kiln-mod --test late_payloads -- --ignored --nocapture

use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;

const PLAYER: u128 = 0x00c0_ffee;
/// Near the player, in the same level.
const GUEST: u128 = 0x0000_0bee;

fn game_folder() -> (PathBuf, PathBuf) {
    let built = Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln");
    let jar = built.join("test/kiln-test-late.jar");
    assert!(jar.is_file(), "run sh tools/build-java.sh first");
    let game = std::env::temp_dir().join(format!("kiln-late-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&game);
    let kiln = game.join("kiln");
    std::fs::create_dir_all(kiln.join("mods")).unwrap();
    std::fs::create_dir_all(kiln.join("lib")).unwrap();
    std::fs::copy(built.join("kiln-runtime.jar"), kiln.join("kiln-runtime.jar")).unwrap();
    for lib in m::jars(&built.join("lib")) {
        std::fs::copy(&lib, kiln.join("lib").join(lib.file_name().unwrap())).unwrap();
    }
    std::fs::copy(&jar, kiln.join("mods/kiln-test-late.jar")).unwrap();
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

/// The server's players as its tick began: a guest near the player.
fn listed() -> Vec<m::Online> {
    vec![m::Online {
        uuid: GUEST,
        name: "Guest".into(),
        dimension: "minecraft:overworld".into(),
        position: [40.0, 64.0, 30.0],
        game_mode: 0,
        health: 20.0,
        view_distance: 8,
    }]
}

/// The test mod's payload as its codec writes it: the type's id, then the line (a var-int length, then UTF-8).
fn slow(line: &str) -> Vec<u8> {
    let mut bytes = vec![19];
    bytes.extend(b"kiln_test_late:slow");
    bytes.push(line.len() as u8);
    bytes.extend(line.as_bytes());
    bytes
}

/// The payloads of `outcome`, with who each goes to; any other effect is left out.
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

#[test]
#[ignore = "needs a JDK and sh tools/build-java.sh"]
fn a_payload_java_answers_late_is_done_when_its_answer_comes() {
    let (game, data) = game_folder();
    let snapshot = m::Snapshot {
        connected: true,
        in_world: true,
        game_time: 10,
        dimension: "minecraft:overworld".into(),
        player: Some(player()),
        ..m::Snapshot::default()
    };
    let deadline = Instant::now() + Duration::from_secs(20);
    while !m::hears("payload") {
        assert!(Instant::now() < deadline, "the mod's receiver isn't heard of");
        m::tick(&data, || None, || vec![6; 128], snapshot.clone());
        std::thread::sleep(m::TICK);
    }
    let world = game.join("world");

    // In time: the game answers the receiver's question, the players on the server.
    let mut answer = |what: &[String]| match what {
        [kind] if kind == "players" => m::players_answer(&listed(), Some(&player()), 10),
        _ => String::new(),
    };
    let quick = m::from_client(&world, 20, &slow("0"), &player(), &mut answer).expect("an answer in time");
    assert_eq!(sent(&quick), [(PLAYER, slow("after 0 ms: 2 on the server, 1 near Kiln"))]);

    // Late: Java takes 400 ms, past the call's 250, and Kiln goes on without its answer.
    let mut unasked = |what: &[String]| -> String { panic!("a late call's question isn't answered: {what:?}") };
    let started = Instant::now();
    assert_eq!(m::from_client(&world, 21, &slow("400"), &player(), &mut unasked), None);
    assert!(started.elapsed() < Duration::from_millis(400), "Kiln didn't wait for Java");

    // At the player's next ticks, what the mod did is done once it came: its answer, with the last players known.
    let mut late = Vec::new();
    let deadline = Instant::now() + Duration::from_secs(10);
    while late.is_empty() {
        assert!(Instant::now() < deadline, "the late payload's answer never came");
        std::thread::sleep(m::TICK);
        late = m::late_payloads(PLAYER);
    }
    assert_eq!(late.len(), 1, "{late:?}");
    assert_eq!(sent(&late[0]), [(PLAYER, slow("after 400 ms: 2 on the server, 1 near Kiln"))]);

    // The next payload in time is as before: the game answers again.
    let again = m::from_client(&world, 22, &slow("0"), &player(), &mut answer).expect("an answer in time");
    assert_eq!(sent(&again), [(PLAYER, slow("after 0 ms: 2 on the server, 1 near Kiln"))]);
    let _ = std::fs::remove_dir_all(&game);
}
