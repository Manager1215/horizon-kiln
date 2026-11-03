// The testbench (mods/kiln-testbench) under Kiln with real Java, as the game drives it: how many of its probes Kiln
// serves. In Minecraft with Fabric every probe passes (its gametest), so the rest is what Kiln doesn't do yet. Needs a
// JDK and the jars from `sh tools/build-java.sh`; the report lands in target/kiln/testbench-report.txt.
//
//   sh tools/build-java.sh && cargo test -p horizon-kiln-mod --test testbench -- --ignored --nocapture

use std::path::Path;
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;

/// Probes that pass under Kiln today. Raise it when Kiln learns more; it never goes down.
const PASSING: usize = 18;

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
    std::fs::copy(
        built.join("kiln-runtime.jar"),
        game.join("kiln/kiln-runtime.jar"),
    )
    .unwrap();
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
    };
    let deadline = Instant::now() + Duration::from_secs(30);
    let mut chat = Vec::new();
    while !report.is_file() {
        assert!(Instant::now() < deadline, "no report; chat: {chat:?}");
        chat.extend(m::tick(&data, || None, || vec![6; 128], in_world.clone()));
        std::thread::sleep(Duration::from_millis(5));
    }
    std::thread::sleep(Duration::from_millis(200));
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
    assert!(
        passing >= PASSING,
        "{passing} probes pass under Kiln, {PASSING} did before"
    );
}
