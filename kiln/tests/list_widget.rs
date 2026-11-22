// Kiln's list widgets (ObjectSelectionList, ContainerObjectSelectionList and the AbstractSelectionList under them)
// on a mod's screen, as the game drives one (D-094), for the overlay's tabs (#135) and mods' own lists: rows of
// different heights laid out, scrolled by the wheel and by dragging the scrollbar, selected by a click and the arrow
// keys, and a switch in a row clicked. kiln/tests/ListWidget.java does it through Kiln's own input and drawing
// (kiln.Screens) and checks each step. Needs a JDK on PATH and the jars from `sh tools/build-java.sh`:
//
//   sh tools/build-java.sh && cargo test -p horizon-kiln-mod --test list_widget -- --ignored --nocapture

use std::path::Path;
use std::process::Command;

#[test]
#[ignore = "needs a JDK and sh tools/build-java.sh"]
fn a_list_lays_out_scrolls_selects_and_passes_clicks_to_its_rows() {
    let repo = Path::new(env!("CARGO_MANIFEST_DIR")).join("..");
    let runtime = repo.join("target/kiln/kiln-runtime.jar");
    assert!(runtime.is_file(), "run sh tools/build-java.sh first");
    // The libraries the stand-ins use (JOML for the drawing's pose), as Kiln's Java has them.
    let libs = std::fs::read_dir(repo.join("target/kiln/lib"))
        .into_iter()
        .flatten()
        .flatten()
        .map(|e| e.path())
        .filter(|p| p.extension().is_some_and(|e| e == "jar"));
    let class_path = std::env::join_paths(std::iter::once(runtime).chain(libs)).unwrap();
    let out = Command::new("java")
        // The JVM's own warnings (a busy computer's perf-data file locked by another JVM) on stderr: stdout is
        // what the program printed (D-266).
        .args(["-Xlog:disable", "-Xlog:all=warning:stderr"])
        .arg("-cp")
        .arg(&class_path)
        .arg(repo.join("kiln/tests/ListWidget.java"))
        .output()
        .expect("java on PATH: a JDK, as tools/build-java.sh needs");
    let stdout = String::from_utf8_lossy(&out.stdout);
    print!("{stdout}");
    assert!(
        out.status.success(),
        "{}",
        String::from_utf8_lossy(&out.stderr)
    );
    for step in [
        "laid out:",
        "wheel:",
        "scrollbar:",
        "child widget:",
        "selection:",
        "all checks passed",
    ] {
        assert!(stdout.contains(step), "no line {step:?} in {stdout}");
    }
}
