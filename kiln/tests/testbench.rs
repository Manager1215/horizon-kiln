// The testbench (mods/kiln-testbench) under Kiln with real Java, as the game drives it: how many of its probes Kiln
// serves. In Minecraft with Fabric every probe passes (its gametest), so the rest is what Kiln doesn't do yet. Needs a
// JDK and the jars from `sh tools/build-java.sh`; the report lands in target/kiln/testbench-report.txt.
//
//   sh tools/build-java.sh && cargo test -p horizon-kiln-mod --test testbench -- --ignored --nocapture

use std::path::Path;
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;
use horizon_kiln_mod::game::kiln_jvm::Play;

/// Probes that pass under Kiln today without the game's resources. Raise it when Kiln learns more; it never goes down.
/// With them (KILN_RESOURCES), every probe passes: the four left read the game's own text, files and registries.
const PASSING: usize = 135;

#[test]
#[ignore = "needs a JDK and sh tools/build-java.sh"]
fn the_testbench_measures_kiln() {
    let built = Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln");
    let testbench = m::jars(&built.join("testbench"));
    assert!(
        built.join("kiln-runtime.jar").is_file() && testbench.len() == 1,
        "run sh tools/build-java.sh first"
    );
    let game = std::env::temp_dir().join(format!("kiln-testbench-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&game);
    std::fs::create_dir_all(game.join("kiln/mods")).unwrap();
    std::fs::create_dir_all(game.join("kiln/lib")).unwrap();
    std::fs::copy(
        built.join("kiln-runtime.jar"),
        game.join("kiln/kiln-runtime.jar"),
    )
    .unwrap();
    // The libraries Minecraft ships, as a release puts them beside Kiln's runtime.
    for jar in m::jars(&built.join("lib")) {
        std::fs::copy(&jar, game.join("kiln/lib").join(jar.file_name().unwrap())).unwrap();
    }
    std::fs::copy(
        &testbench[0],
        game.join("kiln/mods")
            .join(testbench[0].file_name().unwrap()),
    )
    .unwrap();
    let data = game.join("horizon-mods/.horizon-mod-data/kiln");
    std::fs::create_dir_all(&data).unwrap();
    let report = m::kiln_dir(&data).join("config/kiln_testbench/report.txt");

    // In a world from the start, ticking faster than a game would: the testbench reports after 60 ticks there.
    let in_world = m::Snapshot {
        connected: true,
        in_world: true,
        paused: false,
        game_time: 100,
        dimension: "minecraft:overworld".into(),
        advancements: None,
        // The window's player, as Horizon's game hands them over: not the stand-in's defaults.
        player: Some(m::PlayerSnapshot {
            uuid: 0x1234_5678_9abc_4ef0_9234_5678_9abc_def0,
            name: "Kiln".into(),
            dimension: "minecraft:overworld".into(),
            position: [10.5, 70.0, -3.25],
            game_mode: 1,
            health: 17.0,
            main: Some(("minecraft:diamond".into(), 3)),
            off: None,
            attributes: Vec::new(),
            experience: (5, 0.5, 63),
            ..Default::default()
        }),
        // The window's own state as Horizon's game hands it over: single player, the player list (the player alone, in
        // their world), a 1708x960 window at GUI scale 4, the pointer near the middle, no screen open, the chunks around
        // the player (render distance 2) and the player under Speed II for ten seconds. A creative player (who may fly
        // and takes no harm) standing on the ground in a morning's daylight, looking a little down and to the side,
        // with the diamonds in the first hotbar slot, the one in hand; the options' defaults, in English, on Normal.
        // Not zeros: what the testbench reads, it checks, and a stand-in answering 0 would pass on zeros.
        state: m::WindowState {
            single_player: true,
            day_time: 1000,
            height: (-64, 384),
            light: (0, 15),
            food: (18, 2.5),
            may_fly: true,
            invulnerable: true,
            on_ground: true,
            velocity: [0.0, -0.0784, 0.0],
            rotation: (30.0, -20.0),
            selected: 0,
            inventory: vec![(
                0,
                m::Worn {
                    slot: "mainhand",
                    item: "minecraft:diamond".into(),
                    count: 3,
                    damage: 0,
                    max_damage: 0,
                },
            )],
            fov: 70.0,
            render_distance: 2,
            gui_scale: 4,
            language: "en_us".into(),
            difficulty: "normal".into(),
            players: vec![m::Listed {
                uuid: 0x1234_5678_9abc_4ef0_9234_5678_9abc_def0,
                name: "Kiln".into(),
                game_mode: 1,
                latency: 0,
            }],
            window: [1708, 960, 4],
            cursor: [213.5, 120.0],
            screen: "playing".into(),
            chunks: (-2..=2).flat_map(|x| (-3..=1).map(move |z| [x, z])).collect(),
            effects: vec![m::ActiveEffect {
                id: "speed".into(),
                duration: 200,
                amplifier: 1,
                ambient: false,
                visible: true,
            }],
            ..m::WindowState::default()
        },
        // What the player holds, as the game hands it over: the diamonds in hand (the selected hotbar slot's).
        equipment: vec![m::Worn {
            slot: "mainhand",
            item: "minecraft:diamond".into(),
            count: 3,
            damage: 0,
            max_damage: 0,
        }],
        ..m::Snapshot::default()
    };
    // The game's prepared resources (its language files), when KILN_RESOURCES names them: the probes reading the
    // game's text answer then.
    if let Some(resources) = std::env::var_os("KILN_RESOURCES") {
        horizon_kiln_mod::game::kiln_jvm::set_resources(Path::new(&resources));
    }
    // The window draws frames from the start: a second of them before the world, so the frame rate is counted.
    let title = Instant::now();
    while title.elapsed() < Duration::from_millis(1100) {
        m::frame(|| in_world.clone());
        std::thread::sleep(Duration::from_millis(16));
    }
    let deadline = Instant::now() + Duration::from_secs(30);
    let mut chat = Vec::new();
    // What the game would do between ticks: send what the mods sent as the player (the server answers a command in
    // the chat, as a message of the game's), play what they played, draw what they drew.
    let (mut sent, mut played) = (Vec::new(), Vec::new());
    let mut answer = None;
    let mut ticks = 0;
    while !report.is_file() {
        assert!(Instant::now() < deadline, "no report; chat: {chat:?}");
        ticks += 1;
        let mut snapshot = in_world.clone();
        // The player opens their inventory for a few ticks, between the testbench's world probes and its report.
        if (40..44).contains(&ticks) {
            snapshot.state.screen = "inventory".into();
        }
        snapshot.state.messages.extend(answer.take());
        chat.extend(m::tick(&data, || None, || vec![6; 128], snapshot.clone()));
        // Frames between ticks, as the game draws them.
        for _ in 0..2 {
            chat.extend(m::frame(|| snapshot.clone()));
        }
        for (attended, command) in m::take_sent() {
            if command == "help help" {
                answer = Some((false, "/help [<command>]".to_owned()));
            }
            sent.push((attended, command));
        }
        played.extend(m::take_played());
        // At the game's pace: Java answers a tick before the next comes, so what the game answers a probe (the
        // server's reply to its command) reaches Java well before the LATE probes check it. Faster, a busy Java
        // falls behind the ticks queued for it, and LATE comes before the reply.
        std::thread::sleep(m::TICK);
    }
    std::thread::sleep(Duration::from_millis(200));
    // A tick more, for what Java said after the report.
    chat.extend(m::tick(&data, || None, || vec![6; 128], in_world.clone()));
    played.extend(m::take_played());
    let text = std::fs::read_to_string(&report).unwrap();
    std::fs::write(built.join("testbench-report.txt"), &text).unwrap();
    println!("{text}");

    let score = text.lines().nth(1).unwrap_or_default();
    let passing: usize = score
        .split(' ')
        .next()
        .and_then(|n| n.parse().ok())
        .unwrap_or_else(|| panic!("no score in the report: {score}"));
    println!("Kiln: {score}");
    // The testbench's client command is the game's to hand to Java when typed; the game's own commands aren't. What
    // it sent as the player (client.sendCommand) is the game's to send.
    assert!(m::client_command("kiln_testbench"), "the testbench's client command isn't known");
    assert!(!m::client_command("help"));
    // The chat box completes it.
    assert!(m::client_commands().contains(&"kiln_testbench"), "{:?}", m::client_commands());
    assert!(
        sent.contains(&(true, "help help".to_owned())),
        "the testbench's command wasn't sent"
    );
    // What the probes played is the game's to play: the UI's click (at the player), the orb's sound and a particle
    // where the player is, a system toast.
    let sound = |id: &str| {
        played.iter().find_map(|p| match p {
            Play::Sound { id: s, at, .. } if s == id => Some(*at),
            _ => None,
        })
    };
    assert_eq!(sound("minecraft:ui.button.click"), Some(None), "{played:?}");
    assert_eq!(
        sound("minecraft:entity.experience_orb.pickup"),
        Some(Some([10.5, 70.0, -3.25])),
        "{played:?}"
    );
    assert!(
        played.iter().any(|p| matches!(p, Play::Particle { id, at, .. }
            if id == "minecraft:happy_villager" && *at == [10.5, 72.0, -3.25])),
        "{played:?}"
    );
    assert!(
        played.iter().any(|p| matches!(p, Play::Toast { title, message, millis, .. }
            if title == "Kiln Testbench" && message == "toast probe" && *millis == 5000.0)),
        "{played:?}"
    );
    // What the probes wrote is the game's to show: the chat probe's line and the player's system message in the chat,
    // and the two overlay messages (Kiln shows them in the chat too).
    for line in ["[Kiln Testbench] chat probe", "[Kiln Testbench] player probe"] {
        assert!(chat.iter().any(|c| c == line), "{line:?} not in the chat: {chat:?}");
    }
    assert_eq!(chat.iter().filter(|c| *c == "Kiln Testbench").count(), 2, "{chat:?}");
    assert!(
        passing >= PASSING,
        "{passing} probes pass under Kiln, {PASSING} did before"
    );
    if std::env::var_os("KILN_RESOURCES").is_some() {
        assert_eq!(score.split(' ').nth(2), Some(passing.to_string().as_str()), "with the game's resources: {score}");
    }
}
