// A real mod's own payloads with real Java (D-088): Elytra Wind Brake 1.1.0 (Modrinth, CC-BY-NC-SA 4.0; its jar
// stays out of the repository) asks the server whether it has the mod when its client joins a world
// (`elytrawindbrake:check`, from `ClientPlayConnectionEvents.JOIN`), and the server's receiver answers the player
// (`elytrawindbrake:present`), which the client's receiver takes. Its brake itself is a Mixin into the window's player,
// which Kiln doesn't move: only the handshake is proven here, and in the game by kiln/proof.py (KILN_PROOF_ONLY=net).
//
//   curl -fLo elytrawindbrake-1.1.0.jar https://cdn.modrinth.com/data/mdRxbZQw/versions/NdDriI5q/elytrawindbrake-1.1.0.jar
//   (SHA-256 80c0e50386de2fb8f0137645b7f3fccfe54cd7b621ad3764f1f4b44132aee7e5)
//   sh tools/build-java.sh && KILN_ELYTRA_WIND_BRAKE=elytrawindbrake-1.1.0.jar \
//     cargo test -p horizon-kiln-mod --test elytra_wind_brake -- --ignored --nocapture
//
// Without KILN_ELYTRA_WIND_BRAKE the test says so and passes.

use std::path::{Path, PathBuf};
use std::sync::Mutex;
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;

const PLAYER: u128 = 0x00c0_ffee;

/// What Kiln logs, kept: Java's lines come as `Kiln: [java] ...`.
struct Said;

static SAID: Mutex<Vec<String>> = Mutex::new(Vec::new());

impl log::Log for Said {
    fn enabled(&self, _: &log::Metadata) -> bool {
        true
    }

    fn log(&self, record: &log::Record) {
        if record.level() <= log::Level::Info {
            println!("{}: {}", record.level(), record.args());
            SAID.lock().unwrap().push(record.args().to_string());
        }
    }

    fn flush(&self) {}
}

fn said(text: &str) -> bool {
    SAID.lock().unwrap().iter().any(|l| l.contains(text))
}

/// A game folder with Kiln, the libraries and the mod; returns it and the mod's data folder inside it.
fn game_folder(jar: &Path) -> (PathBuf, PathBuf) {
    let built = Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln");
    assert!(built.join("kiln-runtime.jar").is_file(), "run sh tools/build-java.sh first");
    let game = std::env::temp_dir().join(format!("kiln-elytra-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&game);
    let kiln = game.join("kiln");
    std::fs::create_dir_all(kiln.join("mods")).unwrap();
    std::fs::create_dir_all(kiln.join("lib")).unwrap();
    std::fs::copy(built.join("kiln-runtime.jar"), kiln.join("kiln-runtime.jar")).unwrap();
    for lib in m::jars(&built.join("lib")) {
        std::fs::copy(&lib, kiln.join("lib").join(lib.file_name().unwrap())).unwrap();
    }
    std::fs::copy(jar, kiln.join("mods/elytrawindbrake.jar")).unwrap();
    let data = game.join("horizon-mods/.horizon-mod-data/kiln");
    std::fs::create_dir_all(&data).unwrap();
    (game, data)
}

#[test]
#[ignore = "needs a JDK, sh tools/build-java.sh and KILN_ELYTRA_WIND_BRAKE"]
fn elytra_wind_brakes_handshake_goes_both_ways() {
    let Some(jar) = std::env::var_os("KILN_ELYTRA_WIND_BRAKE") else {
        println!("KILN_ELYTRA_WIND_BRAKE names no jar: nothing to run");
        return;
    };
    let _ = log::set_logger(&Said);
    log::set_max_level(log::LevelFilter::Info);
    let (game, data) = game_folder(Path::new(&jar));
    let player = m::PlayerSnapshot {
        uuid: PLAYER,
        name: "Kiln".into(),
        dimension: "minecraft:overworld".into(),
        position: [0.5, 65.0, 0.5],
        health: 20.0,
        attributes: vec![("minecraft:max_health".into(), 20.0, 20.0)],
        ..Default::default()
    };
    let snapshot = m::Snapshot {
        connected: true,
        in_world: true,
        game_time: 10,
        dimension: "minecraft:overworld".into(),
        player: Some(player.clone()),
        ..m::Snapshot::default()
    };
    // The client joins the world: the mod asks the server, a message of the type's id alone (its payload is a unit).
    let deadline = Instant::now() + Duration::from_secs(30);
    let mut sent = Vec::new();
    while sent.is_empty() {
        assert!(Instant::now() < deadline, "no check sent; Kiln said {:?}", SAID.lock().unwrap());
        m::tick(&data, || None, || vec![6; 128], snapshot.clone());
        sent.extend(m::take_net());
        std::thread::sleep(m::TICK);
    }
    assert!(m::hears("payload"), "the server's mod receives payloads");
    assert_eq!(sent, [b"\x15elytrawindbrake:check".to_vec()]);

    // The server's receiver answers the player who asked.
    let mut nothing = |_: &[String]| String::new();
    let answer = m::from_client(&game.join("world"), 20, &sent[0], &player, &mut nothing).expect("an answer");
    assert_eq!(
        answer.effects,
        [m::Effect::Net {
            player: PLAYER,
            data: b"\x17elytrawindbrake:present".to_vec()
        }]
    );

    // The answer reaches the client's receiver.
    let m::Effect::Net { data: present, .. } = &answer.effects[0] else { unreachable!() };
    m::from_server(present);
    let deadline = Instant::now() + Duration::from_secs(20);
    // Java's log lines come with the window's ticks: the server's, then the window's.
    while !said("the window's first elytrawindbrake:present payload from the server reached its mod") {
        assert!(Instant::now() < deadline, "the answer never reached the mod; Kiln said {:?}", SAID.lock().unwrap());
        m::tick(&data, || None, || vec![6; 128], snapshot.clone());
        std::thread::sleep(m::TICK);
    }
    assert!(said("the server's first elytrawindbrake:check payload, from Kiln, reached its mod"));
    assert!(!said("threw") && !said("failed in Java"), "{:?}", SAID.lock().unwrap());
    let _ = std::fs::remove_dir_all(&game);
}
