// JEI (#203) starts under Kiln, as it does when a world is joined: its two entrypoints, then, on joining, its runtime
// from the recipe sync (D-152): the item list from the game's creative tabs and the item registry, every plugin's
// categories, the recipes (crafting, cooking, fuel, smithing, brewing, anvil, grindstone...), the recipe registry and
// its GUI runtime, with no plugin failing. JEI's jar isn't in the repository (MIT, from Modrinth): KILN_JEI names it
// (with MezzConfig, which JEI's jar carries inside). Needs a JDK, `sh tools/build-java.sh` and the game's prepared
// resources in KILN_RESOURCES:
//
//   KILN_JEI=jei-26.3-fabric-31.17.0.113.jar KILN_RESOURCES=~/horizon-resources/26.3 \
//     cargo test -p horizon-kiln-mod --test jei_start -- --ignored --nocapture

use std::path::Path;
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;

#[test]
#[ignore = "needs a JDK, sh tools/build-java.sh, KILN_JEI and KILN_RESOURCES"]
fn jei_starts_with_every_plugin() {
    let (Some(jei), Some(resources)) = (
        std::env::var_os("KILN_JEI"),
        std::env::var_os("KILN_RESOURCES"),
    ) else {
        println!("KILN_JEI or KILN_RESOURCES isn't set: nothing to run");
        return;
    };
    let built = Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln");
    assert!(
        built.join("kiln-runtime.jar").is_file(),
        "run sh tools/build-java.sh first"
    );
    horizon_kiln_mod::game::kiln_jvm::set_resources(Path::new(&resources));
    let game = std::env::temp_dir().join(format!("kiln-jei-start-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&game);
    let kiln = game.join("kiln");
    std::fs::create_dir_all(kiln.join("mods")).unwrap();
    std::fs::create_dir_all(kiln.join("lib")).unwrap();
    std::fs::copy(
        built.join("kiln-runtime.jar"),
        kiln.join("kiln-runtime.jar"),
    )
    .unwrap();
    for jar in m::jars(&built.join("lib")) {
        std::fs::copy(&jar, kiln.join("lib").join(jar.file_name().unwrap())).unwrap();
    }
    let jei = Path::new(&jei);
    std::fs::copy(jei, kiln.join("mods").join(jei.file_name().unwrap())).unwrap();
    counter(&kiln, jei);
    let data = game.join("horizon-mods/.horizon-mod-data/kiln");
    std::fs::create_dir_all(&data).unwrap();

    // In a world, a tick every third frame, until JEI says it started (or 30 s).
    let in_world = m::Snapshot {
        connected: true,
        in_world: true,
        paused: false,
        game_time: 100,
        dimension: "minecraft:overworld".into(),
        ..m::Snapshot::default()
    };
    let log = kiln.join("java.log");
    let started = Instant::now();
    let mut chat = Vec::new();
    let mut frame = 0u64;
    while started.elapsed() < Duration::from_secs(30)
        && !std::fs::read_to_string(&log)
            .unwrap_or_default()
            .contains("Starting JEI took")
    {
        if frame % 3 == 0 {
            chat.extend(m::tick(&data, || None, || vec![6; 128], in_world.clone()));
        }
        chat.extend(m::frame(|| in_world.clone()));
        frame += 1;
        std::thread::sleep(Duration::from_millis(16));
    }
    let text = std::fs::read_to_string(&log).unwrap_or_default();
    println!("== after {:?}\n{text}", started.elapsed());
    assert!(
        chat.iter().any(|line| line.contains("Java mods running")) || text.contains("Starting JEI"),
        "JEI's entrypoints didn't start: {chat:?}"
    );
    for step in [
        "Registering ingredients took",
        "Registering categories took",
        "Registering recipe catalysts took",
        "Registering recipes took",
        "Building recipe registry took",
        "Registering Runtime took",
        "Starting JEI took",
    ] {
        assert!(text.contains(step), "JEI didn't get to \"{step}\"");
    }
    // The recipes JEI holds (kiln/tests/JeiCount.java's line), all its categories, hidden ones too (D-159). The
    // floor is what this commit's Kiln gives with 26.3's resources: it may grow, never fall.
    let line = text
        .lines()
        .find(|l| l.contains("Kiln's JEI count: "))
        .expect("JEI's runtime reached no plugin");
    let counts = &line[line.find("Kiln's JEI count: ").unwrap() + "Kiln's JEI count: ".len()..];
    let total: u64 = counts.split(' ').next().unwrap().parse().unwrap();
    println!("== JEI's recipes: {counts}");
    assert!(
        total >= JEI_RECIPES,
        "JEI holds {total} recipes, fewer than {JEI_RECIPES}: {counts}"
    );
    // The levels JEI's anvil page shows for each anvil recipe (D-176): never 0 or an error for a repair or a book.
    let line = text
        .lines()
        .find(|l| l.contains("Kiln's JEI anvil costs: "))
        .expect("JeiCount's anvil line");
    let costs =
        &line[line.find("Kiln's JEI anvil costs: ").unwrap() + "Kiln's JEI anvil costs: ".len()..];
    println!("== JEI's anvil costs: {costs}");
    let n: Vec<u64> = costs
        .split(' ')
        .take(4)
        .map(|n| n.parse().unwrap())
        .collect();
    assert!(n[0] >= 834, "JEI's anvil recipes: {costs}");
    assert_eq!(
        n[1..],
        [0, 0, 0],
        "anvil recipes without a cost, at 0 or failing: {costs}"
    );
    let failed: Vec<&str> = text
        .lines()
        .filter(|l| l.contains("Caught an error from mod plugin"))
        .collect();
    assert!(failed.is_empty(), "plugins failed: {failed:#?}");
    let uids = text
        .lines()
        .filter(|l| l.contains("Couldn't get unique name"))
        .count();
    assert_eq!(uids, 0, "items without a JEI uid");
}

/// What JEI holds with 26.3's resources (kiln/tests/JeiCount.java counts it).
const JEI_RECIPES: u64 = 5209;

/// kiln/tests/JeiCount.java built against JEI's jar into a mod of its own, `kiln_jei_count`, which prints how many
/// recipes JEI holds once its runtime is up.
fn counter(kiln: &Path, jei: &Path) {
    let classes = kiln.parent().unwrap().join("jei-count");
    std::fs::create_dir_all(&classes).unwrap();
    let mut path = vec![jei.to_path_buf(), kiln.join("kiln-runtime.jar")];
    path.extend(m::jars(&kiln.join("lib")));
    let javac = std::process::Command::new("javac")
        .arg("-nowarn")
        .arg("-cp")
        .arg(std::env::join_paths(&path).unwrap())
        .arg("-d")
        .arg(&classes)
        .arg(Path::new(env!("CARGO_MANIFEST_DIR")).join("tests/JeiCount.java"))
        .status()
        .expect("javac on PATH: a JDK, as tools/build-java.sh needs");
    assert!(javac.success(), "kiln/tests/JeiCount.java didn't compile");
    std::fs::write(
        classes.join("fabric.mod.json"),
        r#"{"schemaVersion": 1, "id": "kiln_jei_count", "version": "1", "name": "Kiln's JEI count",
  "environment": "client", "entrypoints": {"jei_mod_plugin": ["kiln.test.JeiCount"]}}"#,
    )
    .unwrap();
    let jar = std::process::Command::new("jar")
        .arg("--create")
        .arg("--file")
        .arg(kiln.join("mods/kiln-jei-count.jar"))
        .arg("-C")
        .arg(&classes)
        .arg(".")
        .status()
        .expect("jar on PATH: a JDK");
    assert!(jar.success(), "kiln-jei-count.jar wasn't made");
}
