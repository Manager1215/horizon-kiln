// What mods find as they start, with real Java (kiln/test-mods/loader, D-098; a mod with mixins and no entrypoint
// beside it, kiln/test-mods/mixinonly, D-099): an entrypoint made as Fabric Language
// Kotlin makes an object's (its static INSTANCE, a private constructor, a class that isn't public), the game, Java,
// Fabric Loader and the Fabric API among Fabric Loader's mods, a registry's getOptional. The mod throws with what
// went wrong; Kiln says in chat whether it started. Needs a JDK and the jars from `sh tools/build-java.sh`.
//
//   sh tools/build-java.sh && cargo test -p horizon-kiln-mod --test loader -- --ignored --nocapture

use std::path::Path;
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;

#[test]
#[ignore = "needs a JDK and sh tools/build-java.sh"]
fn a_mod_finds_what_fabric_loader_gives() {
    let built = Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln");
    let jar = built.join("test/kiln-test-loader.jar");
    assert!(jar.is_file(), "run sh tools/build-java.sh first");
    let game = std::env::temp_dir().join(format!("kiln-loader-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&game);
    let kiln = game.join("kiln");
    std::fs::create_dir_all(kiln.join("mods")).unwrap();
    std::fs::create_dir_all(kiln.join("lib")).unwrap();
    std::fs::copy(built.join("kiln-runtime.jar"), kiln.join("kiln-runtime.jar")).unwrap();
    for lib in m::jars(&built.join("lib")) {
        std::fs::copy(&lib, kiln.join("lib").join(lib.file_name().unwrap())).unwrap();
    }
    std::fs::copy(&jar, kiln.join("mods/kiln-test-loader.jar")).unwrap();
    // A mod with mixins and no entrypoint (D-099): it runs in Java too, and its mixin applies.
    std::fs::copy(built.join("test/kiln-test-mixinonly.jar"), kiln.join("mods/kiln-test-mixinonly.jar")).unwrap();
    let data = game.join("horizon-mods/.horizon-mod-data/kiln");
    std::fs::create_dir_all(&data).unwrap();
    let snapshot = m::Snapshot {
        connected: true,
        in_world: true,
        game_time: 10,
        dimension: "minecraft:overworld".into(),
        ..m::Snapshot::default()
    };
    let deadline = Instant::now() + Duration::from_secs(20);
    let mut chat: Vec<String> = Vec::new();
    while !chat.iter().any(|l| l.contains("Java mods running")) {
        assert!(Instant::now() < deadline, "the mod's entrypoint never answered: {chat:?}");
        chat.extend(m::tick(&data, || None, || vec![6; 128], snapshot.clone()));
        std::thread::sleep(m::TICK);
    }
    println!("chat: {chat:#?}");
    assert!(chat.iter().any(|l| l.starts_with("Kiln: 2 of 2 Java mods running on Java ")), "{chat:?}");
    let java = std::fs::read_to_string(kiln.join("java.log")).unwrap_or_default();
    assert!(java.contains("Kiln Test Loader: its checks passed"), "{java}");
    let _ = std::fs::remove_dir_all(&game);
}
