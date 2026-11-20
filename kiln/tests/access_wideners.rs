// A mod with an access widener (a class tweaker, D-228): Kiln's agent loads the classes it names with the access it
// asks for, the mod's own and Kiln's stand-ins alike, as Fabric Loader does. The test mod's entrypoint fails unless
// each entry is widened (kiln/test-mods/widener). Needs a JDK and the jars from `sh tools/build-java.sh`.
//
//   sh tools/build-java.sh && cargo test -p horizon-kiln-mod --test access_wideners -- --ignored --nocapture

use std::path::Path;
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;

#[test]
#[ignore = "needs a JDK and sh tools/build-java.sh"]
fn a_mods_access_widener_widens_the_classes_it_names() {
    let built = Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln");
    let widener = built.join("test/kiln-test-widener.jar");
    assert!(widener.is_file(), "run sh tools/build-java.sh first");
    let game = std::env::temp_dir().join(format!("kiln-widener-{}", std::process::id()));
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
    std::fs::copy(&widener, kiln.join("mods/kiln-test-widener.jar")).unwrap();
    let data = game.join("horizon-mods/.horizon-mod-data/kiln");
    std::fs::create_dir_all(&data).unwrap();

    let snapshot = m::Snapshot {
        connected: true,
        in_world: true,
        game_time: 10,
        dimension: "minecraft:overworld".into(),
        ..m::Snapshot::default()
    };
    let mut chat: Vec<String> = m::tick(&data, || None, || vec![6; 128], snapshot.clone());
    let deadline = Instant::now() + Duration::from_secs(20);
    while !chat.iter().any(|l| l.contains("Java mods running")) {
        assert!(
            Instant::now() < deadline,
            "the mod's entrypoint never answered: {chat:?}"
        );
        chat.extend(m::tick(&data, || None, || vec![6; 128], snapshot.clone()));
        std::thread::sleep(m::TICK);
    }
    println!("chat: {chat:#?}");
    assert!(
        !chat
            .iter()
            .any(|l| l.contains("Kiln Test Widener didn't start")),
        "a class tweaker entry wasn't applied: {chat:?}"
    );
    assert!(
        chat.iter()
            .any(|l| l.starts_with("Kiln: 1 of 1 Java mods running on Java ")),
        "{chat:?}"
    );
    let _ = std::fs::remove_dir_all(&game);
}
