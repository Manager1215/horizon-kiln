// The overlay's "Off now" column (D-113, internal/soft-switch-spike.md section 5): for a mod jar, whether the soft
// switch can turn it off now (safe, partial, restart) and whether horizon-kiln.cc lists it as tried. `kiln.SoftOff`
// answers from the catalogue's copy in kiln-runtime.jar (kiln/java/kiln/catalogue.json, by the jar's SHA-256), or
// reads a jar the copy hasn't itself (`kiln.SoftOffScan`, tools/soft-off.py's reading); a fresher copy in Kiln's
// cache folder wins over the shipped one, an older one doesn't, and a copy made by an older reading has its classes
// read again. The ignored tests need a JDK and the jars from `sh tools/build-java.sh`; with KILN_SOFT_OFF_JARS (or
// KILN_COVERAGE) and python3, Kiln's reading of every jar in that folder is checked against tools/soft-off.py's.
//
//   cargo test -p horizon-kiln-mod --test soft_off
//   sh tools/build-java.sh && cargo test -p horizon-kiln-mod --test soft_off -- --ignored --nocapture

use std::io::Write;
use std::path::{Path, PathBuf};
use std::process::Command;

use horizon_kiln_mod::game::kiln as m;
use serde_json::{Value, json};

fn repo() -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR")).join("..")
}

fn read_json(path: &Path) -> Value {
    serde_json::from_slice(
        &std::fs::read(path).unwrap_or_else(|e| panic!("{}: {e}", path.display())),
    )
    .unwrap()
}

/// The copy Kiln ships says what the catalogue says: every tried jar's class, by its SHA-256, and a date no older
/// than the catalogue's newest. (`python3 -I tools/catalogue-snapshot.py` writes it again.)
#[test]
fn shipped_copy_is_current() {
    let copy = read_json(&repo().join("kiln/java/kiln/catalogue.json"));
    let tried = read_json(&repo().join("catalogue/tried.json"));
    let mut newest = String::new();
    let mut checked = 0;
    for m in tried["mods"].as_array().unwrap() {
        if let Some(date) = m["result"]["date"].as_str() {
            newest = newest.max(date.to_string());
        }
        let (Some(sha), Some(class)) =
            (m["jar"]["sha256"].as_str(), m["softOff"]["class"].as_str())
        else {
            continue;
        };
        assert_eq!(
            copy["jars"][sha]["class"].as_str(),
            Some(class),
            "{}: the copy's class isn't the catalogue's; run python3 -I tools/catalogue-snapshot.py",
            m["id"]
        );
        checked += 1;
    }
    assert!(checked > 500, "only {checked} tried jars have a class");
    // The site's word on each verified mod, by its id as the copy keys it (letters and digits, lower case).
    let verified = read_json(&repo().join("catalogue/verified.json"));
    for m in verified["mods"].as_array().unwrap() {
        let key: String = m["id"]
            .as_str()
            .unwrap()
            .to_lowercase()
            .chars()
            .filter(|c| c.is_alphanumeric())
            .collect();
        assert_eq!(
            copy["mods"][&key]["tried"], m["status"],
            "{key}: the copy's status isn't verified.json's; run python3 -I tools/catalogue-snapshot.py"
        );
    }
    assert!(
        copy["generated"].as_str().unwrap() >= newest.as_str(),
        "the copy is older than the catalogue; run python3 -I tools/catalogue-snapshot.py"
    );
}

fn built() -> PathBuf {
    repo().join("target/kiln")
}

fn java() -> PathBuf {
    let env = |key: &str| std::env::var(key).ok();
    let home = std::env::var_os("HOME").map(PathBuf::from);
    let java = m::find_java(None, &env, home.as_deref(), 21).expect("a JDK");
    horizon_kiln_mod::game::kiln_jvm::java_program(&java)
}

/// `kiln.SoftOff`'s answers for the jars, with `kiln` as Kiln's folder: the copy in use, then one per jar.
fn answers(kiln: &Path, jars: &[PathBuf]) -> (Value, Vec<Value>) {
    let built = built();
    assert!(
        built.join("kiln-runtime.jar").is_file(),
        "run sh tools/build-java.sh first"
    );
    let mut cp = vec![built.join("kiln-runtime.jar")];
    cp.extend(m::jars(&built.join("lib")));
    let out = Command::new(java())
        .arg("-cp")
        .arg(std::env::join_paths(&cp).unwrap())
        .arg("kiln.SoftOff")
        .arg("--kiln")
        .arg(kiln)
        .args(jars)
        .output()
        .expect("java");
    let stdout = String::from_utf8_lossy(&out.stdout);
    assert!(
        out.status.success(),
        "kiln.SoftOff: {stdout}\n{}",
        String::from_utf8_lossy(&out.stderr)
    );
    let mut lines = stdout
        .lines()
        .map(|l| serde_json::from_str::<Value>(l).unwrap());
    let head = lines.next().unwrap();
    (head, lines.collect())
}

/// A jar of `entries` (name, bytes) at `path`.
fn jar(path: &Path, entries: &[(&str, &[u8])]) -> PathBuf {
    let mut zip = zip::ZipWriter::new(std::fs::File::create(path).unwrap());
    for (name, bytes) in entries {
        let stored = zip::write::SimpleFileOptions::default()
            .compression_method(zip::CompressionMethod::Stored);
        zip.start_file(*name, stored).unwrap();
        zip.write_all(bytes).unwrap();
    }
    zip.finish().unwrap();
    path.to_path_buf()
}

fn folder(name: &str) -> PathBuf {
    let dir = std::env::temp_dir().join(format!("kiln-soft-off-{name}-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&dir);
    std::fs::create_dir_all(dir.join("cache")).unwrap();
    dir
}

fn write_copy(kiln: &Path, copy: &Value) {
    std::fs::write(
        kiln.join("cache/catalogue.json"),
        serde_json::to_vec(copy).unwrap(),
    )
    .unwrap();
}

/// Jars the catalogue hasn't: Kiln reads them itself, as tools/soft-off.py does.
#[test]
#[ignore]
fn miss_then_scan() {
    let kiln = folder("scan");
    let soft = built().join("test/kiln-test-soft.jar");
    let mixins = built().join("proof/kiln-proof-mixins.jar");
    let manifest = br#"{"schemaVersion":1,"id":"kiln_test_content","version":"1"}"#;
    let content = jar(
        &kiln.join("content.jar"),
        &[
            ("fabric.mod.json", manifest),
            (
                "data/kiln_test_content/worldgen/structure/tower.json",
                b"{}",
            ),
        ],
    );
    let recipes = jar(
        &kiln.join("recipes.jar"),
        &[
            ("fabric.mod.json", manifest),
            ("data/kiln_test_content/recipe/a.json", b"{}"),
        ],
    );
    let pack = jar(
        &kiln.join("pack.jar"),
        &[
            ("fabric.mod.json", manifest),
            ("assets/kiln_test_content/textures/block/a.png", b"png"),
        ],
    );
    let (head, a) = answers(&kiln, &[soft, mixins, content, recipes, pack]);
    println!("copy {head}");
    for x in &a {
        println!("{x}");
        assert_eq!(x["source"], "scan", "{x}");
        assert_eq!(x["tried"], "not-tried", "{x}");
        assert_eq!(x["sha256"].as_str().unwrap().len(), 64, "{x}");
    }
    assert_eq!(a[0]["class"], "safe");
    assert_eq!(
        a[0]["why"],
        "only events, HUD, keys, commands and guarded mixin handlers"
    );
    assert_eq!(a[0]["offNow"], "yes");
    assert_eq!(a[1]["class"], "restart");
    assert!(
        a[1]["why"].as_str().unwrap().contains("@redirect"),
        "{}",
        a[1]
    );
    assert_eq!(a[1]["offNow"], "fully off after a restart");
    assert_eq!(a[2]["class"], "partial");
    assert_eq!(a[2]["why"], "generates in the world");
    assert_eq!(a[2]["offNow"], "partly");
    assert_eq!(a[3]["why"], "data pack files (recipes, loot, tags) stay");
    assert_eq!(a[4]["class"], "partial");
    assert_eq!(a[4]["why"], "resource pack files stay");
    let _ = std::fs::remove_dir_all(&kiln);
}

/// A jar the copy has, by its SHA-256: the copy's class and the catalogue's word, no reading. A fresher copy in the
/// cache folder is the one read; an older one is left for the shipped copy; a copy made by an older reading has its
/// class read again, its "tried" kept.
#[test]
#[ignore]
fn hit_fresher_and_stale() {
    let kiln = folder("hit");
    let soft = built().join("test/kiln-test-soft.jar");
    let (shipped, a) = answers(&kiln, std::slice::from_ref(&soft));
    let sha = a[0]["sha256"].as_str().unwrap().to_string();
    let shipped_date = shipped["generated"].as_str().unwrap().to_string();
    assert!(
        !shipped_date.is_empty(),
        "no copy in kiln-runtime.jar: {shipped}"
    );

    let copy = |generated: &str, rules: u32| {
        json!({
            "generated": generated,
            "rules": rules,
            "jars": {&sha: {"mod": "kilntestsoft", "version": "0.0.1", "class": "partial", "why": "the test's copy"}},
            "mods": {"kilntestsoft": {"name": "Kiln Test Soft Switch", "version": "0.0.1", "tried": "works",
                                      "kiln": "0.4.1", "date": generated}},
        })
    };

    // A hit in a fresher copy (a refresh online): its class (on purpose not the jar's), its word.
    write_copy(&kiln, &copy("9999-12-31", 1));
    let (head, a) = answers(&kiln, std::slice::from_ref(&soft));
    println!("fresher {head}\n{}", a[0]);
    assert_eq!(head["generated"], "9999-12-31");
    assert_eq!(a[0]["source"], "catalogue");
    assert_eq!(a[0]["class"], "partial");
    assert_eq!(a[0]["why"], "the test's copy");
    assert_eq!(a[0]["tried"], "works");
    assert_eq!(a[0]["sameJar"], true);
    assert_eq!(a[0]["version"], "0.0.1");
    assert_eq!(a[0]["kiln"], "0.4.1");

    // A stale copy, older than the shipped one: the shipped one is read, which hasn't the jar.
    write_copy(&kiln, &copy("2000-01-01", 1));
    let (head, a) = answers(&kiln, std::slice::from_ref(&soft));
    println!("older {head}\n{}", a[0]);
    assert_eq!(head["generated"], shipped_date.as_str());
    assert_eq!(a[0]["source"], "scan");
    assert_eq!(a[0]["class"], "safe");
    assert_eq!(a[0]["tried"], "not-tried");

    // A copy made by an older reading: the class read again, the catalogue's word kept.
    write_copy(&kiln, &copy("9999-12-31", 0));
    let (_, a) = answers(&kiln, std::slice::from_ref(&soft));
    println!("older reading {}", a[0]);
    assert_eq!(a[0]["source"], "scan");
    assert_eq!(a[0]["class"], "safe");
    assert_eq!(a[0]["tried"], "works");
    assert_eq!(a[0]["sameJar"], true);

    // Not a jar: unreadable, not a failure.
    let broken = kiln.join("broken.jar");
    std::fs::write(&broken, b"not a zip").unwrap();
    let (_, a) = answers(&kiln, &[broken]);
    assert_eq!(a[0]["class"], "unreadable", "{}", a[0]);
    let _ = std::fs::remove_dir_all(&kiln);
}

/// Kiln's reading of every jar in KILN_SOFT_OFF_JARS (else KILN_COVERAGE) is tools/soft-off.py's, class and why;
/// a jar the shipped copy has is answered from it, with the catalogue's word. Passes, saying so, without a folder or
/// without python3.
#[test]
#[ignore]
fn reading_matches_soft_off_py() {
    let Some(dir) =
        std::env::var_os("KILN_SOFT_OFF_JARS").or_else(|| std::env::var_os("KILN_COVERAGE"))
    else {
        println!("no KILN_SOFT_OFF_JARS or KILN_COVERAGE: nothing to compare");
        return;
    };
    let jars = m::jars(Path::new(&dir));
    assert!(!jars.is_empty(), "no jars in {}", Path::new(&dir).display());
    let Ok(py) = Command::new("python3")
        .arg("-I")
        .arg(repo().join("tools/soft-off.py"))
        .arg("--json")
        .args(&jars)
        .output()
    else {
        println!("no python3: nothing to compare");
        return;
    };
    assert!(
        py.status.success(),
        "soft-off.py: {}",
        String::from_utf8_lossy(&py.stderr)
    );
    let want: Value = serde_json::from_slice(&py.stdout).unwrap();

    // The shipped copy: a jar it has is a hit, with the catalogue's word.
    let shipped = folder("shipped");
    let (_, answered) = answers(&shipped, &jars);
    let hits = answered
        .iter()
        .filter(|a| a["source"] == "catalogue")
        .count();
    for a in answered.iter().filter(|a| a["source"] == "catalogue") {
        assert_ne!(a["tried"], "not-tried", "{a}");
        assert_eq!(a["sameJar"], true, "{a}");
    }
    // Every jar read by Kiln: a fresher copy with no jars makes each one a miss.
    let scan = folder("reading");
    write_copy(
        &scan,
        &json!({"generated": "9999-12-31", "rules": 1, "jars": {}, "mods": {}}),
    );
    let (_, read) = answers(&scan, &jars);
    let mut differ = Vec::new();
    let mut compared = 0;
    for (path, got) in jars.iter().zip(&read) {
        assert_eq!(got["source"], "scan");
        let sha = got["sha256"].as_str().unwrap();
        let Some(w) = want.get(sha) else { continue };
        compared += 1;
        let why = w["why"]
            .as_array()
            .unwrap()
            .iter()
            .map(|x| x.as_str().unwrap())
            .collect::<Vec<_>>()
            .join("; ");
        if got["class"] != w["class"] || got["why"] != why.as_str() {
            differ.push(format!(
                "{}: soft-off.py {} ({why}), Kiln {} ({})",
                path.display(),
                w["class"],
                got["class"],
                got["why"]
            ));
        }
    }
    println!(
        "{} jars: {} in the shipped copy, {compared} compared, {} differ from soft-off.py",
        jars.len(),
        hits,
        differ.len()
    );
    assert!(compared > 0, "soft-off.py read none of the jars");
    assert!(
        differ.is_empty(),
        "Kiln's reading differs from tools/soft-off.py's:\n{}",
        differ.join("\n")
    );
    let _ = std::fs::remove_dir_all(&shipped);
    let _ = std::fs::remove_dir_all(&scan);
}
