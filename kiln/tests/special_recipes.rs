// The game's special crafting recipes crafted through Kiln's stand-ins, with real Java and the game's resources
// (D-197, #224): each one as Kiln reads it from the game's files must match the grids the game's matches and make what
// the game makes (dyed leather's mixed color, a firework star's explosion and fade, rockets' flight and bursts, a
// shield with a banner's patterns, a banner's and a written book's copies and what stays in the grid, two worn tools'
// durability and curses, a map cloned and zoomed out, a decorated pot's sides). Needs a JDK on PATH, the jars from
// `sh tools/build-java.sh` and the game's prepared resources in KILN_RESOURCES:
//
//   KILN_RESOURCES=~/horizon-resources/26.3 cargo test -p horizon-kiln-mod --test special_recipes -- --ignored --nocapture

use std::path::Path;
use std::process::Command;

#[test]
#[ignore = "needs a JDK, sh tools/build-java.sh and KILN_RESOURCES"]
fn the_games_special_recipes_craft_as_the_game_does() {
    let Some(resources) = std::env::var_os("KILN_RESOURCES") else {
        println!("KILN_RESOURCES names no resources: nothing to craft");
        return;
    };
    let repo = Path::new(env!("CARGO_MANIFEST_DIR")).join("..");
    let runtime = repo.join("target/kiln/kiln-runtime.jar");
    assert!(runtime.is_file(), "run sh tools/build-java.sh first");
    let libs = std::fs::read_dir(repo.join("target/kiln/lib"))
        .into_iter()
        .flatten()
        .flatten()
        .map(|e| e.path())
        .filter(|p| p.extension().is_some_and(|e| e == "jar"));
    let class_path = std::env::join_paths(std::iter::once(runtime).chain(libs)).unwrap();
    let mut resources_arg = std::ffi::OsString::from("-Dkiln.resources=");
    resources_arg.push(&resources);
    let out = Command::new("java")
        // The JVM's own warnings (a busy computer's perf-data file locked by another JVM) on stderr: stdout is
        // what the program printed (D-266).
        .args(["-Xlog:disable", "-Xlog:all=warning:stderr"])
        .arg(resources_arg)
        .arg("-cp")
        .arg(&class_path)
        .arg(repo.join("kiln/tests/SpecialRecipes.java"))
        .output()
        .expect("java on PATH: a JDK, as tools/build-java.sh needs");
    let stdout = String::from_utf8_lossy(&out.stdout);
    println!("{stdout}");
    let log = String::from_utf8_lossy(&out.stderr);
    let failed: Vec<&str> = stdout.lines().filter(|l| l.starts_with("FAILED")).collect();
    assert!(failed.is_empty(), "{}\n{log}", failed.join("\n"));
    assert!(out.status.success(), "{log}");
    // Every case ran: a case that threw would end the run early.
    let passed = stdout.lines().filter(|l| l.starts_with("ok ")).count();
    assert!(passed >= 49, "{passed} cases passed: {log}");
}
