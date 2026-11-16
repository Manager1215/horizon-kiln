// What JEI draws that Kiln gives the game (D-179): the mod icons of JEI's config screen's sorting page, which JEI
// makes as DynamicTextures from each mod's icon file (fabric.mod.json's `icon`, found by `findPath`), go to the game
// as a mod's pictures do, and again in the same place when a texture's pixels change; and an entity a mod loaded from
// NBT (JEI's entity pages: a sheep's colour, a baby) is drawn from what it was loaded with, kept through a save and a
// load. Needs a JDK, `sh tools/build-java.sh`, JEI's jar in KILN_JEI and the game's prepared resources in
// KILN_RESOURCES:
//
//   KILN_JEI=jei-26.3-fabric-31.17.0.113.jar KILN_RESOURCES=~/horizon-resources/26.3 \
//     cargo test -p horizon-kiln-mod --test jei_draw -- --ignored --nocapture

use std::path::Path;
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;

#[test]
#[ignore = "needs a JDK, sh tools/build-java.sh, KILN_JEI and KILN_RESOURCES"]
fn jei_draws_its_dynamic_textures_and_entities_as_loaded() {
    let (Some(jei), Some(resources)) = (std::env::var_os("KILN_JEI"), std::env::var_os("KILN_RESOURCES")) else {
        println!("KILN_JEI or KILN_RESOURCES isn't set: nothing to run");
        return;
    };
    let built = Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln");
    assert!(built.join("kiln-runtime.jar").is_file(), "run sh tools/build-java.sh first");
    horizon_kiln_mod::game::kiln_jvm::set_resources(Path::new(&resources));
    let game = std::env::temp_dir().join(format!("kiln-jei-draw-{}", std::process::id()));
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
    drawer(&kiln, jei);
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
        && !std::fs::read_to_string(&log).unwrap_or_default().contains("Kiln's JEI entity plain: ")
    {
        if frame % 3 == 0 {
            m::tick(&data, || None, || vec![6; 128], in_world.clone());
        }
        m::frame(|| in_world.clone());
        frame += 1;
        std::thread::sleep(Duration::from_millis(16));
    }
    // What Java sent with it (the pictures) is read with the next frames.
    for _ in 0..30 {
        m::frame(|| in_world.clone());
        std::thread::sleep(Duration::from_millis(16));
    }
    let text = std::fs::read_to_string(&log).unwrap_or_default();
    let line = |name: &str| -> String {
        let line = text.lines().find(|l| l.contains(name)).unwrap_or_else(|| panic!("no \"{name}\" in java.log:\n{text}"));
        let rest = line[line.find(name).unwrap() + name.len()..].to_owned();
        println!("== {name}{}", rest.chars().take(600).collect::<String>());
        rest
    };
    assert!(!text.contains("Kiln's JEI icons failed") && !text.contains("Kiln's JEI entity failed"), "{text}");

    // JEI's mod icon (jei-icon.png), drawn from its DynamicTexture.
    let icons = line("Kiln's JEI icons: ");
    let mut fields = icons.splitn(3, ' ');
    let (values, drawn) = (fields.next().unwrap().parse::<usize>().unwrap(), fields.next().unwrap().parse::<usize>().unwrap());
    let ops = fields.next().unwrap_or("");
    let dynamic = ops.split('|').filter(|op| op.contains(" kiln:dyn/jei/")).count();
    println!("== {values} icon files of JEI's, {drawn} icons, {dynamic} drawn from a dynamic texture");
    assert!(values > 0 && drawn > 0, "JEI's icon file (its fabric.mod.json's) wasn't found: {icons}");
    assert!(dynamic >= 1, "no icon drawn from its dynamic texture (the mods' icon files found?): {ops}");
    assert!(!ops.split('|').any(|op| op.starts_with("f ")), "an icon drew as a box: {ops}");
    // Their pixels reached the game's sheet, under the names the ops draw.
    let names: Vec<String> = horizon_kiln_mod::game::kiln_sprites::take_upload(1).map(|u| u.names.into_iter().map(|(n, _)| n).collect()).unwrap_or_default();
    for op in ops.split('|').filter(|op| op.contains(" kiln:dyn/")) {
        let name = op.rsplit(' ').next().unwrap();
        assert!(names.iter().any(|n| n == name), "{name} isn't in the game's sheet: {names:?}");
    }

    // A texture the mod changed: drawn by the same name, its new pixels sent again.
    let own = line("Kiln's JEI own texture: ");
    let (first, second) = own.split_once(" then ").unwrap();
    let name = |ops: &str| ops.rsplit(' ').next().unwrap().to_owned();
    assert!(first.contains(" kiln:dyn/kiln_jei_draw/own/0_0_4_4"), "{first}");
    assert_eq!(name(first), name(second));

    // The sheep as loaded: its colour, shorn, a baby, kept through saving it and loading that into another.
    let sheep = line("Kiln's JEI entity: ");
    assert!(sheep.starts_with("e ") && sheep.contains(" minecraft:sheep {"), "{sheep}");
    for field in ["Color:11", "Sheared:1b", "Age:-24000", "id:\"minecraft:sheep\""] {
        assert!(sheep.contains(field), "no {field} in {sheep}");
    }
    let again = line("Kiln's JEI entity again: ");
    assert_eq!(again, sheep, "saved and loaded, the sheep isn't the same");
    // Its equipment's items by their full ids, as the game's item codec reads them.
    let horse = line("Kiln's JEI entity horse: ");
    assert!(horse.contains(r#"body:{id:"minecraft:netherite_horse_armor"}"#), "{horse}");
    // An owner given as a list of numbers is the UUID the game reads (four ints).
    let cat = line("Kiln's JEI entity cat: ");
    assert!(cat.contains("Owner:[I;0,0,0,0]") && cat.contains("CollarColor:9"), "{cat}");
    // An entity loaded from nothing draws its type, saved as a bare one.
    let plain = line("Kiln's JEI entity plain: ");
    assert!(plain.contains(" minecraft:cow"), "{plain}");
    // And the game reads the op back whole.
    let op = horizon_kiln_mod::game::kiln_jvm::parse_op(sheep.split('|').next().unwrap());
    match op {
        Some(horizon_kiln_mod::game::kiln_jvm::HudOp::Entity { id, saved, .. }) => {
            assert_eq!(id, "minecraft:sheep");
            assert!(saved.contains("Color:11"), "{saved}");
        }
        other => panic!("the sheep's op reads as {other:?}"),
    }
}

/// kiln/tests/JeiDraw.java built against JEI's jar into a mod of its own, `kiln_jei_draw`, which prints what it drew
/// once JEI's runtime is up.
fn drawer(kiln: &Path, jei: &Path) {
    let classes = kiln.parent().unwrap().join("jei-draw");
    std::fs::create_dir_all(&classes).unwrap();
    let mut path = vec![jei.to_path_buf(), kiln.join("kiln-runtime.jar")];
    path.extend(m::jars(&kiln.join("lib")));
    let javac = std::process::Command::new("javac")
        .arg("-nowarn")
        .arg("-cp")
        .arg(std::env::join_paths(&path).unwrap())
        .arg("-d")
        .arg(&classes)
        .arg(Path::new(env!("CARGO_MANIFEST_DIR")).join("tests/JeiDraw.java"))
        .status()
        .expect("javac on PATH: a JDK, as tools/build-java.sh needs");
    assert!(javac.success(), "kiln/tests/JeiDraw.java didn't compile");
    std::fs::write(
        classes.join("fabric.mod.json"),
        r#"{"schemaVersion": 1, "id": "kiln_jei_draw", "version": "1", "name": "Kiln's JEI draw",
  "environment": "client", "entrypoints": {"jei_mod_plugin": ["kiln.test.JeiDraw"]}}"#,
    )
    .unwrap();
    let jar = std::process::Command::new("jar")
        .arg("--create")
        .arg("--file")
        .arg(kiln.join("mods/kiln-jei-draw.jar"))
        .arg("-C")
        .arg(&classes)
        .arg(".")
        .status()
        .expect("jar on PATH: a JDK");
    assert!(jar.success(), "kiln-jei-draw.jar wasn't made");
}
