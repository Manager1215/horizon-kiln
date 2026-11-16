// How JEI's item list reads under Kiln (D-172): its order is the game's creative tabs' (Building Blocks first, oak logs
// first of all), and the same in every game. JEI makes its list from the creative tabs in the registry's order and
// sorts it by mod, then by type, then by that order (its default stages). Needs a JDK, `sh tools/build-java.sh`, JEI's
// jar in KILN_JEI and the game's prepared resources in KILN_RESOURCES:
//
//   KILN_JEI=jei-26.3-fabric-31.17.0.113.jar KILN_RESOURCES=~/horizon-resources/26.3 \
//     cargo test -p horizon-kiln-mod --test jei_look -- --ignored --nocapture

use std::path::Path;
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;

#[test]
#[ignore = "needs a JDK, sh tools/build-java.sh, KILN_JEI and KILN_RESOURCES"]
fn jeis_list_is_in_the_creative_tabs_order() {
    let (Some(jei), Some(resources)) = (std::env::var_os("KILN_JEI"), std::env::var_os("KILN_RESOURCES")) else {
        println!("KILN_JEI or KILN_RESOURCES isn't set: nothing to run");
        return;
    };
    let built = Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln");
    assert!(built.join("kiln-runtime.jar").is_file(), "run sh tools/build-java.sh first");
    horizon_kiln_mod::game::kiln_jvm::set_resources(Path::new(&resources));
    let game = std::env::temp_dir().join(format!("kiln-jei-look-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&game);
    let kiln = game.join("kiln");
    std::fs::create_dir_all(kiln.join("mods")).unwrap();
    std::fs::create_dir_all(kiln.join("lib")).unwrap();
    std::fs::copy(built.join("kiln-runtime.jar"), kiln.join("kiln-runtime.jar")).unwrap();
    for jar in m::jars(&built.join("lib")) {
        std::fs::copy(&jar, kiln.join("lib").join(jar.file_name().unwrap())).unwrap();
    }
    let jei = Path::new(&jei);
    std::fs::copy(jei, kiln.join("mods").join(jei.file_name().unwrap())).unwrap();
    looker(&kiln, jei);
    let data = game.join("horizon-mods/.horizon-mod-data/kiln");
    std::fs::create_dir_all(&data).unwrap();

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
    let mut frame = 0u64;
    while started.elapsed() < Duration::from_secs(40)
        && !std::fs::read_to_string(&log).unwrap_or_default().contains("Kiln's JEI list: ")
    {
        if frame % 3 == 0 {
            m::tick(&data, || None, || vec![6; 128], in_world.clone());
        }
        m::frame(|| in_world.clone());
        frame += 1;
        std::thread::sleep(Duration::from_millis(16));
    }
    let text = std::fs::read_to_string(&log).unwrap_or_default();
    let line = |name: &str| -> Vec<String> {
        let line = text.lines().find(|l| l.contains(name)).unwrap_or_else(|| panic!("no \"{name}\" in java.log:\n{text}"));
        let rest = &line[line.find(name).unwrap() + name.len()..];
        println!("== {name}{}", rest.chars().take(300).collect::<String>());
        rest.split(' ').nth(1).unwrap_or("").split(',').map(str::to_owned).collect()
    };
    let made = line("Kiln's JEI made: ");
    let list = line("Kiln's JEI list: ");
    // The game's creative tabs' order (creative-inventory.json): Building Blocks first, its first row the oak's.
    let first = ["oak_log", "oak_wood", "stripped_oak_log", "stripped_oak_wood", "oak_planks", "oak_stairs"];
    assert_eq!(&made[..first.len()], &first, "JEI made its ingredients in another order");
    assert_eq!(&list[..first.len()], &first, "JEI's list isn't in the creative tabs' order");
    // Every item of the game's category tabs, in their order, each where it first shows (JEI keeps a potion of each
    // kind and the like apart, by their components, so the ids are compared at their first place).
    let tabs: serde_json::Value =
        serde_json::from_slice(&std::fs::read(Path::new(&resources).join("creative-inventory.json")).unwrap()).unwrap();
    let mut wanted = Vec::new();
    for tab in tabs["tabs"].as_array().unwrap().iter().filter(|t| t["type"] == "CATEGORY") {
        for snbt in tab["items"].as_array().into_iter().flatten() {
            let snbt = snbt.as_str().unwrap();
            // The stack's own id is its last (its components, a stew's effects' ids among them, come first).
            let id = snbt.rsplit("id:\"minecraft:").next().and_then(|r| r.split('"').next()).unwrap_or("").to_owned();
            if !id.is_empty() && !wanted.contains(&id) {
                wanted.push(id);
            }
        }
    }
    let mut shown = Vec::new();
    for id in &list {
        if !shown.contains(id) {
            shown.push(id.clone());
        }
    }
    let missing: Vec<&String> = wanted.iter().filter(|id| !shown.contains(id)).collect();
    println!("== the tabs' {} items, JEI's list {} ({} not in it: {missing:?})", wanted.len(), shown.len(), missing.len());
    let in_order: Vec<&String> = shown.iter().filter(|id| wanted.contains(id)).collect();
    let wanted_shown: Vec<&String> = wanted.iter().filter(|id| shown.contains(id)).collect();
    assert_eq!(in_order, wanted_shown, "JEI's list isn't in the creative tabs' order");
    // The kinds JEI tells apart by a component Kiln keeps as the saved value (D-172): each of the tabs' goat horns,
    // ominous bottles and paintings is an entry of its own, as in the game's creative tabs.
    let count = |id: &str| list.iter().filter(|i| *i == id).count();
    assert_eq!(count("goat_horn"), 8, "a goat horn of each instrument");
    assert_eq!(count("ominous_bottle"), 5, "an ominous bottle of each level");
    assert!(count("painting") > 40, "a painting of each variant: {}", count("painting"));
}

/// kiln/tests/JeiLook.java built against JEI's jar into a mod of its own, `kiln_jei_look`, which prints JEI's item
/// list's order once its runtime is up.
fn looker(kiln: &Path, jei: &Path) {
    let classes = kiln.parent().unwrap().join("jei-look");
    std::fs::create_dir_all(&classes).unwrap();
    let mut path = vec![jei.to_path_buf(), kiln.join("kiln-runtime.jar")];
    path.extend(m::jars(&kiln.join("lib")));
    let javac = std::process::Command::new("javac")
        .arg("-nowarn")
        .arg("-cp")
        .arg(std::env::join_paths(&path).unwrap())
        .arg("-d")
        .arg(&classes)
        .arg(Path::new(env!("CARGO_MANIFEST_DIR")).join("tests/JeiLook.java"))
        .status()
        .expect("javac on PATH: a JDK, as tools/build-java.sh needs");
    assert!(javac.success(), "kiln/tests/JeiLook.java didn't compile");
    std::fs::write(
        classes.join("fabric.mod.json"),
        r#"{"schemaVersion": 1, "id": "kiln_jei_look", "version": "1", "name": "Kiln's JEI look",
  "environment": "client", "entrypoints": {"jei_mod_plugin": ["kiln.test.JeiLook"]}}"#,
    )
    .unwrap();
    let jar = std::process::Command::new("jar")
        .arg("--create")
        .arg("--file")
        .arg(kiln.join("mods/kiln-jei-look.jar"))
        .arg("-C")
        .arg(&classes)
        .arg(".")
        .status()
        .expect("jar on PATH: a JDK");
    assert!(jar.success(), "kiln-jei-look.jar wasn't made");
}
