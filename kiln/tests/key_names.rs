// Kiln's InputConstants stand-in names key codes as Horizon does. A mod registers its keys by code (`KEY_J = 13`)
// and Kiln matches the name Horizon sends when one goes down (`key.keyboard.j`), so a wrong name is a key the mod
// never hears, and nothing fails. Horizon's table is generated from Minecraft 26.3 itself. Needs a JDK on PATH,
// the jars from `sh tools/build-java.sh` and Horizon's source, which `native.py check kiln` downloads; without
// that source the test says so and checks nothing:
//
//   sh tools/build-java.sh && cargo test -p horizon-kiln-mod --test key_names -- --ignored --nocapture

use std::collections::BTreeMap;
use std::path::Path;
use std::process::Command;

/// `KEYBOARD 13` or `MOUSE 1` to the key's name, from Horizon's key_names.rs at the commit Kiln is pinned to;
/// None when that source isn't downloaded.
fn horizon_names(repo: &Path) -> Option<BTreeMap<String, String>> {
    let compatibility = std::fs::read_to_string(repo.join("kiln/compatibility.json")).ok()?;
    let compatibility: serde_json::Value = serde_json::from_str(&compatibility).ok()?;
    let revision = compatibility["horizon_revision"].as_str()?;
    let file = repo
        .join(".deps/horizon-mods/native/target/horizon")
        .join(revision)
        .join("crates/horizon-client-input/src/key_names.rs");
    let source = std::fs::read_to_string(file).ok()?;
    let mut names = BTreeMap::new();
    let mut table = "";
    for line in source.lines().map(str::trim) {
        // `pub const KEYBOARD: &[(i32, &str)] = &[`, then `(99, "key.keyboard.keypad.period"),`
        if let Some(rest) = line.strip_prefix("pub const ") {
            table = rest.split(':').next().unwrap_or("");
            continue;
        }
        let entry = line
            .strip_prefix('(')
            .and_then(|l| l.strip_suffix("),"))
            .and_then(|l| l.split_once(", "));
        if let Some((code, name)) = entry {
            names.insert(format!("{table} {code}"), name.trim_matches('"').to_owned());
        }
    }
    Some(names)
}

/// The same, as Kiln's stand-in names them (kiln/tests/KeyNames.java).
fn kiln_names(repo: &Path, runtime: &Path) -> BTreeMap<String, String> {
    let out = Command::new("java")
        .arg("-cp")
        .arg(runtime)
        .arg(repo.join("kiln/tests/KeyNames.java"))
        .output()
        .expect("java on PATH: a JDK, as tools/build-java.sh needs");
    assert!(
        out.status.success(),
        "{}",
        String::from_utf8_lossy(&out.stderr)
    );
    String::from_utf8_lossy(&out.stdout)
        .lines()
        .filter_map(|l| {
            let (key, name) = l.rsplit_once(' ')?;
            Some((key.to_owned(), name.to_owned()))
        })
        .collect()
}

#[test]
#[ignore = "needs a JDK, sh tools/build-java.sh and Horizon's source"]
fn the_stand_in_names_keys_as_horizon_does() {
    let repo = Path::new(env!("CARGO_MANIFEST_DIR")).join("..");
    let runtime = repo.join("target/kiln/kiln-runtime.jar");
    assert!(runtime.is_file(), "run sh tools/build-java.sh first");
    let Some(horizon) = horizon_names(&repo) else {
        println!(
            "key names not checked: no Horizon source in .deps (python3 native/tools/native.py check kiln downloads it)"
        );
        return;
    };
    assert!(
        horizon.len() > 200,
        "key_names.rs changed its format: {} names read",
        horizon.len()
    );
    let kiln = kiln_names(&repo, &runtime);
    let wrong: Vec<String> = kiln
        .iter()
        .filter(|(key, name)| horizon.get(*key) != Some(*name))
        .map(|(key, name)| {
            let theirs = horizon.get(key).map_or("nothing", String::as_str);
            format!("{key}: Kiln says {name}, Horizon {theirs}")
        })
        .collect();
    println!("{} key names checked against Horizon's", kiln.len());
    assert!(kiln.len() > 100, "{kiln:?}");
    assert!(wrong.is_empty(), "{wrong:#?}");
}
