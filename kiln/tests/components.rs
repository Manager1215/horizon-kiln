// A stack's components as the game saves them, read through Kiln's stand-ins (#101, D-135): DuraHUD reads a potion's
// `potion_contents` for its effects, Usage Ticker Reloaded counts what a bundle (`bundle_contents`) and a shulker box
// (`container`) hold. Those types had no codec, so `ItemStack.get` gave null and the mods counted nothing. Needs a
// JDK on PATH and the jars from `sh tools/build-java.sh`:
//
//   sh tools/build-java.sh && cargo test -p horizon-kiln-mod --test components -- --ignored --nocapture

use std::path::Path;
use std::process::Command;

#[test]
#[ignore = "needs a JDK and sh tools/build-java.sh"]
fn potions_bundles_and_containers_are_read_as_the_game_saves_them() {
    let repo = Path::new(env!("CARGO_MANIFEST_DIR")).join("..");
    let runtime = repo.join("target/kiln/kiln-runtime.jar");
    assert!(runtime.is_file(), "run sh tools/build-java.sh first");
    // The libraries the stand-ins use (DataFixerUpper's codecs, Gson), as Kiln's Java has them.
    let libs = std::fs::read_dir(repo.join("target/kiln/lib"))
        .into_iter()
        .flatten()
        .flatten()
        .map(|e| e.path())
        .filter(|p| p.extension().is_some_and(|e| e == "jar"));
    let class_path = std::env::join_paths(std::iter::once(runtime).chain(libs)).unwrap();
    let out = Command::new("java")
        .arg("-cp")
        .arg(&class_path)
        .arg(repo.join("kiln/tests/Components.java"))
        .output()
        .expect("java on PATH: a JDK, as tools/build-java.sh needs");
    assert!(
        out.status.success(),
        "{}",
        String::from_utf8_lossy(&out.stderr)
    );
    let out = String::from_utf8_lossy(&out.stdout);
    println!("{out}");
    let lines: Vec<&str> = out.lines().collect();
    assert_eq!(
        lines,
        [
            // An id alone: vanilla's Strong Swiftness, Speed II for 1:30.
            "potion true minecraft:speed/1800/1",
            // The whole object: the potion's effects, then its own.
            "custom minecraft:fire_resistance/3600/0 minecraft:luck/200/2",
            "holder true",
            "bundle minecraft:arrowx12 minecraft:torchx3",
            // Six slots, the empty ones left out of what it holds.
            "container 6 minecraft:dirtx64 minecraft:arrowx7",
        ]
    );
}
