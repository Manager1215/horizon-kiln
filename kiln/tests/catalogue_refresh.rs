// The catalogue's copy, refreshed online (D-128): `kiln.CatalogueRefresh` fetches the copy the catalogue publishes at
// most once a day, keeps it in Kiln's cache folder when it is the catalogue's copy and newer than the one in use, and
// leaves the old one otherwise; its setting turns it off. Every test here asks a server on this machine, never the
// network. The tests need a JDK and the jars from `sh tools/build-java.sh`:
//
//   sh tools/build-java.sh && cargo test -p horizon-kiln-mod --test catalogue_refresh -- --ignored --nocapture

use std::io::{Read, Write};
use std::net::TcpListener;
use std::path::{Path, PathBuf};
use std::process::Command;
use std::sync::{Arc, Mutex};

use horizon_kiln_mod::game::kiln as m;
use serde_json::{Value, json};

const DAY: u64 = 24 * 60 * 60 * 1000;
const NOW: u64 = 1_800_000_000_000;

fn repo() -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR")).join("..")
}

fn java() -> PathBuf {
    let env = |key: &str| std::env::var(key).ok();
    let home = std::env::var_os("HOME").map(PathBuf::from);
    let java = m::find_java(None, &env, home.as_deref(), 21).expect("a JDK");
    horizon_kiln_mod::game::kiln_jvm::java_program(&java)
}

fn class_path() -> std::ffi::OsString {
    let built = repo().join("target/kiln");
    assert!(
        built.join("kiln-runtime.jar").is_file(),
        "run sh tools/build-java.sh first"
    );
    let mut cp = vec![built.join("kiln-runtime.jar")];
    cp.extend(m::jars(&built.join("lib")));
    std::env::join_paths(&cp).unwrap()
}

/// A server on this machine that answers every request with `answer` (status, body), and keeps what it was asked.
struct Server {
    url: String,
    answer: Arc<Mutex<(u16, Vec<u8>)>>,
    asked: Arc<Mutex<Vec<String>>>,
}

impl Server {
    fn start() -> Server {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let url = format!(
            "http://127.0.0.1:{}/catalogue.json",
            listener.local_addr().unwrap().port()
        );
        let answer = Arc::new(Mutex::new((404, Vec::new())));
        let asked = Arc::new(Mutex::new(Vec::new()));
        let (a, k) = (answer.clone(), asked.clone());
        std::thread::spawn(move || {
            for stream in listener.incoming() {
                let Ok(mut stream) = stream else { continue };
                let mut head = Vec::new();
                let mut byte = [0u8; 1];
                while !head.ends_with(b"\r\n\r\n")
                    && stream.read(&mut byte).map(|n| n == 1).unwrap_or(false)
                {
                    head.push(byte[0]);
                }
                k.lock()
                    .unwrap()
                    .push(String::from_utf8_lossy(&head).into_owned());
                let (status, body) = a.lock().unwrap().clone();
                let head = format!(
                    "HTTP/1.1 {status} X\r\nContent-Type: application/json\r\nContent-Length: {}\r\n",
                    body.len()
                );
                let _ = write!(stream, "{head}Connection: close\r\n\r\n");
                let _ = stream.write_all(&body);
            }
        });
        Server { url, answer, asked }
    }

    fn answer(&self, status: u16, body: &[u8]) {
        *self.answer.lock().unwrap() = (status, body.to_vec());
    }

    fn asked(&self) -> Vec<String> {
        self.asked.lock().unwrap().clone()
    }
}

/// Kiln's folder for one test, empty.
fn folder(name: &str) -> PathBuf {
    let dir = std::env::temp_dir().join(format!(
        "kiln-catalogue-refresh-{name}-{}",
        std::process::id()
    ));
    let _ = std::fs::remove_dir_all(&dir);
    std::fs::create_dir_all(&dir).unwrap();
    dir
}

/// One refresh at `now` with `kiln` as Kiln's folder, asking `url`: its outcome ("taken", "stale", ...).
fn refresh_with(kiln: &Path, url: &str, now: u64, env: Option<&str>) -> String {
    let mut command = Command::new(java());
    command
        .arg("-cp")
        .arg(class_path())
        .arg("kiln.CatalogueRefresh")
        .args([
            "--kiln",
            kiln.to_str().unwrap(),
            "--url",
            url,
            "--version",
            "9.9.9",
            "--now",
        ])
        .arg(now.to_string())
        .env_remove("KILN_CATALOGUE_REFRESH");
    if let Some(value) = env {
        command.env("KILN_CATALOGUE_REFRESH", value);
    }
    let out = command.output().expect("java");
    let stdout = String::from_utf8_lossy(&out.stdout).trim().to_owned();
    assert!(
        out.status.success(),
        "kiln.CatalogueRefresh: {stdout}\n{}",
        String::from_utf8_lossy(&out.stderr)
    );
    eprintln!(
        "{now}: {stdout} {}",
        String::from_utf8_lossy(&out.stderr).trim()
    );
    stdout
}

fn refresh(kiln: &Path, server: &Server, now: u64) -> String {
    refresh_with(kiln, &server.url, now, None)
}

/// The date of the copy `kiln.SoftOff` uses with `kiln` as Kiln's folder.
fn in_use(kiln: &Path) -> String {
    let out = Command::new(java())
        .arg("-cp")
        .arg(class_path())
        .arg("kiln.SoftOff")
        .arg("--kiln")
        .arg(kiln)
        .output()
        .expect("java");
    let head: Value =
        serde_json::from_str(String::from_utf8_lossy(&out.stdout).lines().next().unwrap()).unwrap();
    head["generated"].as_str().unwrap().to_owned()
}

fn shipped() -> String {
    let text = std::fs::read(repo().join("kiln/java/kiln/catalogue.json")).unwrap();
    let copy: Value = serde_json::from_slice(&text).unwrap();
    copy["generated"].as_str().unwrap().to_owned()
}

/// A copy in the catalogue's shape, made on `generated`.
fn copy(generated: &str) -> Vec<u8> {
    serde_json::to_vec(&json!({
        "about": "a test copy",
        "generated": generated,
        "rules": 1,
        "jars": {"00ff": {"mod": "testmod", "version": "1", "class": "safe", "why": "no content"}},
        "mods": {"testmod": {"name": "Test Mod", "version": "1", "tried": "works", "kiln": "9.9.9", "date": generated}},
    }))
    .unwrap()
}

/// A newer copy is taken: written to the cache folder, used by `kiln.SoftOff`; the request is a plain GET with no
/// query and no cookies, saying only that it is Kiln.
#[test]
#[ignore]
fn fresh_copy_taken() {
    let kiln = folder("fresh");
    let server = Server::start();
    let body = copy("2099-01-01");
    server.answer(200, &body);
    assert_eq!(in_use(&kiln), shipped());
    assert_eq!(refresh(&kiln, &server, NOW), "taken");
    assert_eq!(
        std::fs::read(kiln.join("cache/catalogue.json")).unwrap(),
        body
    );
    assert!(!kiln.join("cache/catalogue.json.part").exists());
    assert_eq!(in_use(&kiln), "2099-01-01");
    let asked = server.asked();
    assert_eq!(asked.len(), 1, "{asked:?}");
    let request = &asked[0];
    assert!(
        request.starts_with("GET /catalogue.json HTTP/1.1\r\n"),
        "{request}"
    );
    let lower = request.to_lowercase();
    assert!(
        lower.contains("\r\nuser-agent: kiln/9.9.9\r\n"),
        "{request}"
    );
    assert!(!lower.contains("cookie"), "{request}");
    assert!(!lower.contains("authorization"), "{request}");
    assert!(!request.lines().next().unwrap().contains('?'), "{request}");
}

/// A copy no newer than the one in use, one that isn't the catalogue's, and a server that fails: each leaves the
/// copy in use as it was.
#[test]
#[ignore]
fn stale_and_garbage_refused() {
    let kiln = folder("refused");
    let server = Server::start();
    // Older than the shipped copy: left alone, nothing written.
    server.answer(200, &copy("2000-01-01"));
    assert_eq!(refresh(&kiln, &server, NOW), "stale");
    assert!(!kiln.join("cache/catalogue.json").exists());
    // The same date as the shipped one: not newer.
    server.answer(200, &copy(&shipped()));
    assert_eq!(refresh(&kiln, &server, NOW + DAY), "stale");
    assert!(!kiln.join("cache/catalogue.json").exists());
    // A fresher copy in place; then everything after must leave it.
    let good = copy("2099-01-01");
    server.answer(200, &good);
    assert_eq!(refresh(&kiln, &server, NOW + 2 * DAY), "taken");
    let mut day = 3;
    let mut next = |server: &Server, status: u16, body: &[u8], want: &str| {
        server.answer(status, body);
        assert_eq!(
            refresh(&kiln, server, NOW + day * DAY),
            want,
            "{}",
            String::from_utf8_lossy(body)
        );
        assert_eq!(
            std::fs::read(kiln.join("cache/catalogue.json")).unwrap(),
            good
        );
        day += 1;
    };
    next(&server, 200, &copy("2098-01-01"), "stale");
    next(&server, 200, b"<html>not json</html>", "refused");
    next(&server, 200, b"[1, 2]", "refused");
    next(
        &server,
        200,
        &copy("2099-01-01").as_slice()[..40],
        "refused",
    );
    let mut odd: Value = serde_json::from_slice(&copy("2100-01-01")).unwrap();
    odd["jars"]["00ff"]["class"] = json!("sometimes");
    next(&server, 200, &serde_json::to_vec(&odd).unwrap(), "refused");
    let mut no_rules: Value = serde_json::from_slice(&copy("2100-01-01")).unwrap();
    no_rules.as_object_mut().unwrap().remove("rules");
    next(
        &server,
        200,
        &serde_json::to_vec(&no_rules).unwrap(),
        "refused",
    );
    let mut no_date: Value = serde_json::from_slice(&copy("2100-01-01")).unwrap();
    no_date["generated"] = json!("tomorrow");
    next(
        &server,
        200,
        &serde_json::to_vec(&no_date).unwrap(),
        "refused",
    );
    next(&server, 500, &copy("2100-01-01"), "failed");
    next(&server, 404, b"", "failed");
    assert_eq!(in_use(&kiln), "2099-01-01");
}

/// At most once a day: a start within the day sends nothing; a day later, one request again.
#[test]
#[ignore]
fn once_a_day() {
    let kiln = folder("daily");
    let server = Server::start();
    server.answer(200, &copy("2099-01-01"));
    assert_eq!(refresh(&kiln, &server, NOW), "taken");
    assert_eq!(refresh(&kiln, &server, NOW + 1), "not_due");
    assert_eq!(refresh(&kiln, &server, NOW + DAY - 1), "not_due");
    assert_eq!(server.asked().len(), 1);
    assert_eq!(refresh(&kiln, &server, NOW + DAY), "stale");
    assert_eq!(server.asked().len(), 2);
    // A failed request counts too: a server that is down isn't asked at every start.
    server.answer(503, b"");
    assert_eq!(refresh(&kiln, &server, NOW + 2 * DAY), "failed");
    assert_eq!(refresh(&kiln, &server, NOW + 2 * DAY + 60_000), "not_due");
    assert_eq!(server.asked().len(), 3);
    // A clock set back past the last request: due again.
    server.answer(200, &copy("2099-01-01"));
    assert_eq!(refresh(&kiln, &server, NOW - DAY), "stale");
    assert_eq!(server.asked().len(), 4);
}

/// The setting off (its file in Kiln's folder, or the environment variable): nothing is sent, nothing written; a
/// plain-HTTP address that isn't this machine is refused before any request.
#[test]
#[ignore]
fn setting_off_sends_nothing() {
    let server = Server::start();
    server.answer(200, &copy("2099-01-01"));
    let kiln = folder("off");
    for word in ["off", "OFF\n", "false", "0"] {
        std::fs::write(kiln.join("catalogue_refresh"), word).unwrap();
        assert_eq!(refresh(&kiln, &server, NOW), "off", "{word:?}");
    }
    let kiln = folder("env");
    assert_eq!(refresh_with(&kiln, &server.url, NOW, Some("off")), "off");
    assert!(server.asked().is_empty());
    assert!(!kiln.join("cache").exists());
    // On again, by the file: it goes.
    std::fs::write(kiln.join("catalogue_refresh"), "on\n").unwrap();
    assert_eq!(refresh(&kiln, &server, NOW), "taken");
    assert_eq!(server.asked().len(), 1);
    // Only HTTPS goes past this machine.
    let kiln = folder("plain");
    assert_eq!(
        refresh_with(&kiln, "http://10.255.255.1/catalogue.json", NOW, None),
        "failed"
    );
    assert!(!kiln.join("cache/catalogue.json").exists());
}
