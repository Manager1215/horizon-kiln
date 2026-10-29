// Kiln: runs Java mods (.jar) in Horizon. When the player first enters a world, Kiln starts Java in a
// process of its own (`crate::game::kiln_jvm`) with Kiln's Java classes (`<game folder>/kiln/kiln-runtime.jar`)
// and the mods in `<game folder>/kiln/mods/*.jar`, has it run each mod's client entrypoint from its
// fabric.mod.json, then sends it every game tick and shows the HUD line the mods set in Kiln's box. This
// file is both part of the mod's Cargo library (its tests: src/lib.rs) and the module the bundle adds to
// the game (`crate::game::kiln`).

use std::cell::RefCell;
use std::io::Read;
use std::path::{Path, PathBuf};
use std::time::Instant;

use crate::game::kiln_jvm::{JavaHost, Reply, java_program};

/// The mod's id (its settings folder and Options > Mods row), name and version.
pub const ID: &str = "kiln";
pub const NAME: &str = "Kiln";
pub const VERSION: &str = "0.0.2";
pub const DESCRIPTION: &str = "Runs Java mods (.jar) in Horizon. Prototype: Java mods in <game folder>/kiln/mods/ get ticks and a HUD line.";
/// The same box as Horizon's own HUD mods: translucent black, white text with a shadow.
pub const BACKGROUND: u32 = 0x7800_0000;
pub const INK: u32 = 0xFF_FFFF;
pub const TROUBLE: u32 = 0xFF_5555;
/// The oldest Java Kiln's own classes run on.
pub const MIN_JAVA: u32 = 21;

/// A Java mod as its fabric.mod.json describes it.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct JavaMod {
    pub id: String,
    pub name: String,
    pub version: String,
    /// The classes of its `client` entrypoints.
    pub client: Vec<String>,
}

/// A fabric.mod.json's id, name, version and client entrypoints (each a class name, or `{"value": ...}`).
pub fn read_manifest(json: &str) -> Result<JavaMod, String> {
    let v: serde_json::Value =
        serde_json::from_str(json).map_err(|e| format!("fabric.mod.json: {e}"))?;
    let text = |key: &str| v.get(key).and_then(|x| x.as_str()).map(str::to_owned);
    let id = text("id").ok_or("fabric.mod.json has no id")?;
    let client = v
        .pointer("/entrypoints/client")
        .and_then(|e| e.as_array())
        .map(|entries| {
            entries
                .iter()
                .filter_map(|e| e.as_str().or_else(|| e.get("value")?.as_str()))
                .map(str::to_owned)
                .collect()
        })
        .unwrap_or_default();
    Ok(JavaMod {
        name: text("name").unwrap_or_else(|| id.clone()),
        version: text("version").unwrap_or_default(),
        id,
        client,
    })
}

/// The fabric.mod.json inside the jar `file`.
pub fn read_jar(file: &Path) -> Result<JavaMod, String> {
    let name = file
        .file_name()
        .map_or_else(String::new, |n| n.to_string_lossy().into_owned());
    let f = std::fs::File::open(file).map_err(|e| format!("{name}: {e}"))?;
    let mut jar = zip::ZipArchive::new(f).map_err(|e| format!("{name}: not a jar ({e})"))?;
    let mut entry = jar
        .by_name("fabric.mod.json")
        .map_err(|_| format!("{name}: no fabric.mod.json, not a Fabric mod"))?;
    let mut json = String::new();
    entry
        .read_to_string(&mut json)
        .map_err(|e| format!("{name}: {e}"))?;
    read_manifest(&json).map_err(|e| format!("{name}: {e}"))
}

/// The `.jar` files in `dir`, by name.
pub fn jars(dir: &Path) -> Vec<PathBuf> {
    let mut found: Vec<PathBuf> = children(dir)
        .into_iter()
        .filter(|p| p.extension().is_some_and(|e| e.eq_ignore_ascii_case("jar")))
        .collect();
    found.sort();
    found
}

/// Kiln's folder beside options.txt, `<game folder>/kiln/`, from the mod's data folder
/// (`<game folder>/horizon-mods/.horizon-mod-data/kiln`).
pub fn kiln_dir(data_dir: &Path) -> PathBuf {
    data_dir.ancestors().nth(3).unwrap_or(data_dir).join("kiln")
}

/// Java's major version from a Java home's `release` file: `JAVA_VERSION="25.0.2"` is 25, `"1.8.0_402"` 8.
pub fn release_major(release: &str) -> Option<u32> {
    let line = release
        .lines()
        .find_map(|l| l.strip_prefix("JAVA_VERSION="))?;
    let version = line.trim().trim_matches('"');
    let mut parts = version.split(['.', '_', '-', '+']);
    let first: u32 = parts.next()?.parse().ok()?;
    if first == 1 {
        parts.next()?.parse().ok()
    } else {
        Some(first)
    }
}

fn children(dir: &Path) -> Vec<PathBuf> {
    std::fs::read_dir(dir)
        .map(|entries| entries.filter_map(|e| e.ok().map(|e| e.path())).collect())
        .unwrap_or_default()
}

/// Java homes that might be installed, in no order: JAVA_HOME, the Minecraft launcher's own runtimes and
/// the system's JDKs.
fn installed_javas(env: &dyn Fn(&str) -> Option<String>, user_home: Option<&Path>) -> Vec<PathBuf> {
    let mut homes: Vec<PathBuf> = env("JAVA_HOME").map(PathBuf::from).into_iter().collect();
    // The launcher's runtime/<component>/<platform>/<component>/ (on macOS, its jre.bundle).
    let minecraft = if cfg!(target_os = "macos") {
        user_home.map(|h| h.join("Library/Application Support/minecraft/runtime"))
    } else if cfg!(windows) {
        env("APPDATA").map(|a| PathBuf::from(a).join(".minecraft").join("runtime"))
    } else {
        user_home.map(|h| h.join(".minecraft/runtime"))
    };
    for component in minecraft.iter().flat_map(|r| children(r)) {
        for platform in children(&component) {
            for home in children(&platform) {
                homes.push(home.join("jre.bundle/Contents/Home"));
                homes.push(home);
            }
        }
    }
    if cfg!(target_os = "macos") {
        let system = Path::new("/Library/Java/JavaVirtualMachines");
        homes.extend(
            children(system)
                .into_iter()
                .map(|j| j.join("Contents/Home")),
        );
    } else if !cfg!(windows) {
        homes.extend(children(Path::new("/usr/lib/jvm")));
    }
    homes
}

/// The Java home Kiln runs: the `java_home` setting or KILN_JAVA_HOME when set, else the newest installed
/// Java, at least [`MIN_JAVA`].
pub fn find_java(
    setting: Option<&str>,
    env: &dyn Fn(&str) -> Option<String>,
    user_home: Option<&Path>,
) -> Result<PathBuf, String> {
    let chosen = setting.map(str::to_owned).or_else(|| env("KILN_JAVA_HOME"));
    if let Some(home) = chosen.map(|h| PathBuf::from(h.trim())) {
        return if java_program(&home).is_file() {
            Ok(home)
        } else {
            Err(format!("no Java at {}", home.display()))
        };
    }
    installed_javas(env, user_home)
        .into_iter()
        .filter(|home| java_program(home).is_file())
        .filter_map(|home| {
            let release = std::fs::read_to_string(home.join("release")).ok()?;
            Some((release_major(&release)?, home))
        })
        .filter(|(major, _)| *major >= MIN_JAVA)
        .max_by_key(|(major, _)| *major)
        .map(|(_, home)| home)
        .ok_or_else(|| format!("no Java {MIN_JAVA} or newer found"))
}

/// The status box's first line.
pub fn status_line(running: Option<usize>, trouble: Option<&str>) -> String {
    match (running, trouble) {
        (_, Some(trouble)) => format!("{NAME} {VERSION}: {trouble}"),
        (Some(1), None) => format!("{NAME} {VERSION}: 1 Java mod"),
        (Some(n), None) if n > 1 => format!("{NAME} {VERSION}: {n} Java mods"),
        _ => format!("{NAME} {VERSION}: no Java mods"),
    }
}

/// Kiln in this thread's game (the window's): not started yet, running Java mods, or stopped (with why).
enum State {
    Idle,
    Running(Box<Running>),
    Stopped(String),
}

struct Running {
    java: JavaHost,
    started: Instant,
    /// The mods, by entrypoint class: how many entrypoints each still waits for, and whether one failed.
    mods: Vec<(JavaMod, usize, bool)>,
    /// Entrypoints Java hasn't answered for yet; at 0 the player hears how many mods run.
    awaiting: usize,
    version: String,
    ticks: i64,
    hud: String,
}

impl Running {
    fn running(&self) -> usize {
        self.mods
            .iter()
            .filter(|(_, left, failed)| *left == 0 && !failed)
            .count()
    }

    /// Java's replies since the last tick; returns lines for the player's chat, and why Java stopped.
    fn take_replies(&mut self) -> (Vec<String>, Option<String>) {
        let mut chat = Vec::new();
        for reply in self.java.replies() {
            match reply {
                Reply::Ready(version) => {
                    log::info!(
                        "Kiln: Java {version} ready in {} ms",
                        self.started.elapsed().as_millis()
                    );
                    self.version = version;
                }
                Reply::Loaded(class) | Reply::Failed(class, _) if self.awaiting == 0 => {
                    log::warn!("Kiln: unexpected answer for {class}");
                }
                Reply::Loaded(class) => self.answered(&class, None, &mut chat),
                Reply::Failed(class, why) => self.answered(&class, Some(why), &mut chat),
                Reply::Hud(text) => self.hud = text,
                Reply::Log(text) => log::info!("Kiln: [java] {text}"),
                Reply::Chat(text) => chat.push(text),
                Reply::Done(_) => {}
                Reply::Exited(why) => {
                    log::warn!("Kiln: Java stopped: {why}");
                    chat.push("Kiln: Java stopped (kiln/java.log says why)".to_owned());
                    return (chat, Some(why));
                }
            }
        }
        (chat, None)
    }

    fn answered(&mut self, class: &str, failed: Option<String>, chat: &mut Vec<String>) {
        self.awaiting -= 1;
        if let Some(entry) = self
            .mods
            .iter_mut()
            .find(|(m, left, _)| *left > 0 && m.client.iter().any(|c| c == class))
        {
            entry.1 -= 1;
            match failed {
                Some(why) => {
                    entry.2 = true;
                    chat.push(format!("Kiln: {} didn't start: {why}", entry.0.name));
                }
                None if entry.1 == 0 && !entry.2 => {
                    log::info!(
                        "Kiln: started {} {} ({})",
                        entry.0.name,
                        entry.0.version,
                        entry.0.id
                    )
                }
                None => {}
            }
        }
        if self.awaiting == 0 {
            chat.push(format!(
                "Kiln: {} of {} Java mods running on Java {}",
                self.running(),
                self.mods.len(),
                self.version
            ));
        }
    }
}

thread_local! {
    static STATE: RefCell<State> = const { RefCell::new(State::Idle) };
}

/// A tick of the window's game in a world: Kiln starts on the first one, then ticks its Java mods.
/// `java_home` reads the `java_home` setting (only when starting). Returns lines for the player's chat.
/// Never waits for Java: what it answers shows from the next tick.
pub fn tick(data_dir: &Path, java_home: impl FnOnce() -> Option<String>) -> Vec<String> {
    STATE.with(|s| {
        let mut state = s.borrow_mut();
        match &mut *state {
            State::Idle => {
                let (next, chat) = start(&kiln_dir(data_dir), java_home());
                *state = next;
                chat
            }
            State::Running(r) => {
                r.ticks += 1;
                let sent = r.java.send("tick", &r.ticks.to_string());
                let (mut chat, exited) = r.take_replies();
                if let Some(why) = exited.or(sent.err()) {
                    if chat.is_empty() {
                        chat.push(format!("Kiln: Java stopped: {why}"));
                    }
                    *state = State::Stopped("Java stopped".into());
                }
                chat
            }
            State::Stopped(_) => Vec::new(),
        }
    })
}

/// Starts Java with Kiln's runtime and the mods in `dir`/mods, and asks it to run their client entrypoints.
fn start(dir: &Path, java_home: Option<String>) -> (State, Vec<String>) {
    let stop = |why: &str, detail: String| {
        log::warn!("Kiln: {detail}");
        (
            State::Stopped(why.to_owned()),
            vec![format!("Kiln: {detail}")],
        )
    };
    let files = jars(&dir.join("mods"));
    if files.is_empty() {
        log::info!("Kiln: no Java mods in {}", dir.join("mods").display());
        return (State::Stopped("no Java mods".into()), Vec::new());
    }
    let runtime = dir.join("kiln-runtime.jar");
    if !runtime.is_file() {
        return stop(
            "kiln-runtime.jar missing",
            format!("{} is missing", runtime.display()),
        );
    }
    let user_home = std::env::var_os("HOME").map(PathBuf::from);
    let env = |key: &str| std::env::var(key).ok();
    let home = match find_java(java_home.as_deref(), &env, user_home.as_deref()) {
        Ok(home) => home,
        Err(e) => return stop("no Java", e),
    };
    let mut chat = Vec::new();
    let mut mods = Vec::new();
    for file in &files {
        match read_jar(file) {
            Ok(m) if m.client.is_empty() => {
                chat.push(format!("Kiln: {} has no client entrypoint", m.name))
            }
            Ok(m) => mods.push(m),
            Err(e) => chat.push(format!("Kiln: {e}")),
        }
    }
    let mut class_path = vec![runtime];
    class_path.extend(files);
    let started = Instant::now();
    let mut java = match JavaHost::start(&home, &class_path, &dir.join("java.log")) {
        Ok(java) => java,
        Err(e) => return stop("Java didn't start", e),
    };
    log::info!("Kiln: starting Java from {}", home.display());
    let mut awaiting = 0;
    for class in mods.iter().flat_map(|m| &m.client) {
        if let Err(e) = java.send("load", class) {
            return stop("Java didn't start", e);
        }
        awaiting += 1;
    }
    let running = Running {
        java,
        started,
        mods: mods
            .into_iter()
            .map(|m| {
                let n = m.client.len();
                (m, n, false)
            })
            .collect(),
        awaiting,
        version: String::new(),
        ticks: 0,
        hud: String::new(),
    };
    (State::Running(Box::new(running)), chat)
}

/// The box's lines and their colours: Kiln's status, then the Java mods' HUD line.
pub fn hud_lines() -> Vec<(String, u32)> {
    STATE.with(|s| match &*s.borrow() {
        State::Idle => vec![(status_line(None, None), INK)],
        State::Stopped(why) if why == "no Java mods" => vec![(status_line(None, None), INK)],
        State::Stopped(why) => vec![(status_line(None, Some(why)), TROUBLE)],
        State::Running(r) if r.awaiting > 0 => {
            vec![(status_line(None, Some("starting Java")), INK)]
        }
        State::Running(r) => {
            let mut lines = vec![(status_line(Some(r.running()), None), INK)];
            if !r.hud.is_empty() {
                lines.push((r.hud.clone(), INK));
            }
            lines
        }
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    fn scratch(name: &str) -> PathBuf {
        let dir = std::env::temp_dir().join(format!("kiln-test-{}-{name}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).unwrap();
        dir
    }

    /// A fake Java home: its `release` file and an empty `java` program.
    fn fake_java(home: &Path, version: &str) {
        std::fs::create_dir_all(java_program(home).parent().unwrap()).unwrap();
        std::fs::write(java_program(home), b"").unwrap();
        std::fs::write(
            home.join("release"),
            format!("JAVA_VERSION=\"{version}\"\n"),
        )
        .unwrap();
    }

    #[test]
    fn status_counts_java_mods() {
        assert_eq!(status_line(None, None), "Kiln 0.0.2: no Java mods");
        assert_eq!(status_line(Some(0), None), "Kiln 0.0.2: no Java mods");
        assert_eq!(status_line(Some(1), None), "Kiln 0.0.2: 1 Java mod");
        assert_eq!(status_line(Some(3), None), "Kiln 0.0.2: 3 Java mods");
        assert_eq!(status_line(Some(3), Some("no Java")), "Kiln 0.0.2: no Java");
    }

    #[test]
    fn manifest_gives_id_name_and_client_entrypoints() {
        let m = read_manifest(
            r#"{"schemaVersion": 1, "id": "timer", "version": "0.1.0", "name": "Timer",
                "entrypoints": {"main": ["a.Main"], "client": ["a.Client", {"value": "a.Other"}]}}"#,
        )
        .unwrap();
        assert_eq!(m.id, "timer");
        assert_eq!(m.name, "Timer");
        assert_eq!(m.version, "0.1.0");
        assert_eq!(m.client, ["a.Client", "a.Other"]);
        let bare = read_manifest(r#"{"id": "bare"}"#).unwrap();
        assert_eq!((bare.name.as_str(), bare.client.len()), ("bare", 0));
        assert!(read_manifest(r#"{"name": "no id"}"#).is_err());
        assert!(read_manifest("not json").is_err());
    }

    #[test]
    fn kiln_dir_sits_beside_options() {
        let data = Path::new("/games/horizon/horizon-mods/.horizon-mod-data/kiln");
        assert_eq!(kiln_dir(data), Path::new("/games/horizon/kiln"));
    }

    #[test]
    fn release_files_give_the_major_version() {
        assert_eq!(
            release_major("IMPLEMENTOR=\"x\"\nJAVA_VERSION=\"25.0.2\"\n"),
            Some(25)
        );
        assert_eq!(release_major("JAVA_VERSION=\"21\""), Some(21));
        assert_eq!(release_major("JAVA_VERSION=\"1.8.0_402\""), Some(8));
        assert_eq!(release_major("NOTHING=1"), None);
    }

    #[test]
    fn jars_are_found_by_extension_in_name_order() {
        let dir = scratch("jars");
        for name in ["b.jar", "a.JAR", "notes.txt"] {
            std::fs::write(dir.join(name), b"").unwrap();
        }
        let names: Vec<_> = jars(&dir)
            .iter()
            .map(|p| p.file_name().unwrap().to_owned())
            .collect();
        assert_eq!(names, ["a.JAR", "b.jar"]);
        assert!(jars(&dir.join("missing")).is_empty());
    }

    #[test]
    fn the_newest_java_wins_unless_one_is_chosen() {
        let root = scratch("java");
        let (old, new, chosen) = (root.join("old"), root.join("new"), root.join("chosen"));
        fake_java(&old, "17.0.1");
        fake_java(&new, "25.0.2");
        fake_java(&chosen, "21.0.7");
        let env_old = |k: &str| (k == "JAVA_HOME").then(|| old.to_string_lossy().into_owned());
        let nobody = Some(Path::new("/nonexistent-home"));
        // Java 17 alone is too old; the system's JDKs may still be found, so only check it isn't 17.
        if let Ok(found) = find_java(None, &env_old, nobody) {
            assert_ne!(found, old);
        }
        let env_new = |k: &str| (k == "JAVA_HOME").then(|| new.to_string_lossy().into_owned());
        let found = find_java(None, &env_new, nobody).unwrap();
        let major = release_major(&std::fs::read_to_string(found.join("release")).unwrap());
        assert!(major.unwrap() >= 25);
        let chosen_path = chosen.to_string_lossy().into_owned();
        assert_eq!(
            find_java(Some(&chosen_path), &env_new, nobody).unwrap(),
            chosen
        );
        assert!(find_java(Some("/no/java/here"), &env_new, nobody).is_err());
    }
}
