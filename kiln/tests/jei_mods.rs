// Mods' own JEI plugins under Kiln (#223), and how many of a mod's recipes JEI shows (#222), counted with no one
// looking: JEI and the mods named in KILN_JEI_MODS start as on joining a world (as kiln/tests/jei_start.rs does), then
// kiln/tests/JeiMods.java, a JEI plugin of the test's, goes through every category JEI holds: its recipes, the
// stations that make them (the icons beside the category), whether each recipe's layout can be made (the category's
// `setRecipe`) and drawn, its icon and title, and the namespaces of the recipes' ids. The test then sorts that by mod:
// a mod's own categories (their uid's namespace is the mod's), its recipes in any category, against the recipe files
// its jar carries, and the plugins of its that failed, with the error. A table on the output, and with
// KILN_JEI_REPORT a JSON file of it (tools/jei-census.sh runs it for many mods, one JEI each).
//
// Needs a JDK, `sh tools/build-java.sh`, JEI's jar (KILN_JEI, as for jei_start) and the game's prepared resources:
//
//   KILN_JEI=jei-26.3-fabric-31.17.0.113.jar KILN_RESOURCES=~/horizon-resources/26.3 \
//     KILN_JEI_MODS=TechReborn-6.2.1.jar:more-nuggets.jar \
//     cargo test -p horizon-kiln-mod --test jei_mods -- --ignored --nocapture
//
// KILN_JEI_MODS is a path list (`:`); a jar there that is only another's library (a mod's dependency) is listed too,
// and counted as a mod of its own. FLOORS below are what this commit's Kiln gives for a mod, when it is in the run:
// they may rise, never fall.

use std::collections::{BTreeMap, BTreeSet};
use std::io::Read;
use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;

/// What Kiln logs (what it read of the mods' recipes, a mod that didn't start), kept for the report.
struct Said;

static SAID: std::sync::Mutex<Vec<String>> = std::sync::Mutex::new(Vec::new());

impl log::Log for Said {
    fn enabled(&self, _: &log::Metadata) -> bool {
        true
    }

    fn log(&self, record: &log::Record) {
        if let Ok(mut said) = SAID.lock() {
            said.push(format!("{}: {}", record.level(), record.args()));
        }
    }

    fn flush(&self) {}
}

/// For a mod id: its own categories, the recipes JEI holds in them, and the recipes of its namespaces JEI shows in any
/// category, at the least (with 26.3's resources; D-165 names the jars: Tech Reborn 6.2.1, More Nuggets 26.3-0.1, The
/// Fletching Table Mod from the try jars).
const FLOORS: &[(&str, usize, u64, u64)] = &[
    ("techreborn", 25, 1709, 2602),
    ("the_fletching_table_mod", 1, 126, 0),
    ("morenuggets", 0, 0, 34),
    // D-171: Universal Enchantment Info and Block Variant Swapper (which starts now, with Fabric's resource loader
    // v0 and the data pack reload events).
    ("uei", 1, 43, 0),
    ("blockvariantswapper", 1, 121, 0),
    // D-173: Just Enough Breeding, its mobs drawn in its pages (spawn eggs, entity types, entity models in a box).
    ("justenoughbreeding", 6, 186, 0),
];

/// Categories whose drawn layouts must draw an entity's model (D-173): Just Enough Breeding's draw the mob.
const ENTITY_CATEGORIES: &[&str] = &["justenoughbreeding:breeding", "justenoughbreeding:transformation"];

/// JEI's own categories with the game's recipes: at least as many as jei_start counts (D-159).
const GAME_RECIPES: u64 = 5209;

#[derive(Default, Debug)]
struct Category {
    uid: String,
    recipes: u64,
    stations: u64,
    layouts: u64,
    drawn: String,
    icon: String,
    /// Entity models its drawn layouts drew (D-173).
    entities: u64,
    ids: BTreeMap<String, u64>,
    title: String,
    errors: Vec<String>,
    /// In the drawn layouts: fluids drawn as their sprites, and grey boxes where a picture was missing (D-168).
    fluids: u64,
    fallbacks: u64,
}

#[derive(Default, Debug)]
struct Mod {
    id: String,
    jar: String,
    namespaces: BTreeSet<String>,
    plugins: Vec<String>,
    /// Recipe files in the jar (and its nested jars), by namespace.
    recipe_files: BTreeMap<String, u64>,
}

#[test]
#[ignore = "needs a JDK, sh tools/build-java.sh, KILN_JEI and KILN_RESOURCES"]
fn mods_jei_plugins() {
    let (Some(jei), Some(resources)) = (std::env::var_os("KILN_JEI"), std::env::var_os("KILN_RESOURCES")) else {
        println!("KILN_JEI or KILN_RESOURCES isn't set: nothing to run");
        return;
    };
    let jars: Vec<PathBuf> = std::env::var_os("KILN_JEI_MODS")
        .map(|list| std::env::split_paths(&list).filter(|p| !p.as_os_str().is_empty()).collect())
        .unwrap_or_default();
    let _ = log::set_logger(&Said);
    log::set_max_level(log::LevelFilter::Info);
    let wait = std::env::var("KILN_JEI_WAIT").ok().and_then(|s| s.parse().ok()).unwrap_or(90);
    let built = Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln");
    assert!(built.join("kiln-runtime.jar").is_file(), "run sh tools/build-java.sh first");
    horizon_kiln_mod::game::kiln_jvm::set_resources(Path::new(&resources));
    let game = std::env::temp_dir().join(format!("kiln-jei-mods-{}", std::process::id()));
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
    let mut mods = Vec::new();
    for jar in &jars {
        std::fs::copy(jar, kiln.join("mods").join(jar.file_name().unwrap())).unwrap();
        mods.push(read_mod(jar));
    }
    walker(&kiln, jei);
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
    let mut chat = Vec::new();
    let mut frame = 0u64;
    let done = |text: &str| text.contains("Kiln's JEI mods done");
    while started.elapsed() < Duration::from_secs(wait) && !done(&std::fs::read_to_string(&log).unwrap_or_default()) {
        if frame % 3 == 0 {
            chat.extend(m::tick(&data, || None, || vec![6; 128], in_world.clone()));
        }
        chat.extend(m::frame(|| in_world.clone()));
        frame += 1;
        std::thread::sleep(Duration::from_millis(16));
    }
    let text = std::fs::read_to_string(&log).unwrap_or_default();
    let took = started.elapsed();
    if std::env::var_os("KILN_JEI_LOG").is_some() {
        println!("== java.log after {took:?}\n{text}");
    }
    let said = SAID.lock().map(|s| s.clone()).unwrap_or_default();
    if std::env::var_os("KILN_JEI_LOG").is_some() {
        println!("== Kiln said\n{}\n== chat\n{}", said.join("\n"), chat.join("\n"));
    }
    let running = chat.iter().find(|line| line.contains("Java mods running")).cloned().unwrap_or_default();
    let categories = categories(&text);
    let failed = failed_plugins(&text);
    let mut report = report(&mods, &categories, &failed, &running, done(&text), took);
    // What Kiln said of the mods: what it read of their recipes, and the mods that didn't start or threw.
    report["kiln"] = serde_json::json!(said
        .iter()
        .chain(chat.iter())
        .filter(|l| l.contains("recipes of the mods' own types")
            || l.contains("didn't start")
            || l.contains("couldn't")
            || l.contains("threw"))
        .map(|l| l.chars().take(2000).collect::<String>())
        .collect::<Vec<_>>());
    // Where JEI stopped, when it didn't reach the test's plugin: the last errors Java printed.
    if !done(&text) {
        let errors: Vec<&str> = text
            .lines()
            .filter(|l| !l.trim_start().starts_with("at ") && (l.contains("Exception") || l.contains("Error")))
            .collect();
        report["log_tail"] = serde_json::json!(errors[errors.len().saturating_sub(6)..]
            .iter()
            .map(|l| l.chars().take(400).collect::<String>())
            .collect::<Vec<_>>());
    }
    println!("{}", table(&report));
    if let Some(path) = std::env::var_os("KILN_JEI_REPORT") {
        std::fs::write(path, serde_json::to_string_pretty(&report).unwrap()).unwrap();
    }
    let _ = std::fs::remove_dir_all(&game);

    assert!(done(&text), "JEI's runtime didn't reach the test's plugin in {wait} s (KILN_JEI_LOG=1 prints the log)");
    let game_recipes: u64 = categories.iter().filter(|c| c.uid.starts_with("minecraft:")).map(|c| c.recipes).sum();
    assert!(game_recipes >= GAME_RECIPES, "JEI's own categories hold {game_recipes}, fewer than {GAME_RECIPES}");
    for (id, floor_categories, floor_recipes, floor_shown) in FLOORS {
        let Some(row) = report["mods"].as_array().unwrap().iter().find(|r| r["id"] == *id) else { continue };
        let own = row["categories"].as_u64().unwrap() as usize;
        let recipes = row["recipes_in_own_categories"].as_u64().unwrap();
        let layouts = row["layouts_in_own_categories"].as_u64().unwrap();
        let shown = row["recipes_shown"].as_u64().unwrap();
        assert!(own >= *floor_categories, "{id}: {own} categories, fewer than {floor_categories}");
        assert!(recipes >= *floor_recipes, "{id}: {recipes} recipes in its categories, fewer than {floor_recipes}");
        assert_eq!(layouts, recipes, "{id}: a layout for only {layouts} of its {recipes} recipes");
        assert!(shown >= *floor_shown, "{id}: JEI shows {shown} of its recipes, fewer than {floor_shown}");
        assert!(row["plugins_failed"].as_array().unwrap().is_empty(), "{id}: plugins failed: {}", row["plugins_failed"]);
    }
    // Tech Reborn's machines take and give fluids: its layouts draw them as their sprites, not as nothing (D-168).
    let tech_reborn: Vec<&Category> = categories.iter().filter(|c| c.uid.starts_with("techreborn:")).collect();
    if !tech_reborn.is_empty() {
        let fluids: u64 = tech_reborn.iter().map(|c| c.fluids).sum();
        println!("Tech Reborn's layouts drew {fluids} fluids");
        assert!(fluids > 0, "Tech Reborn's layouts drew no fluid");
    }
    // JEI's list: what it holds of the items that differ by their components, and its first items (D-168).
    let list = text.lines().find_map(|l| after(l, "Kiln's JEI list: ")).unwrap_or("none");
    println!("JEI's list: {list}");
    let by_mod = text.lines().find_map(|l| after(l, "Kiln's JEI list by mod: ")).unwrap_or("none");
    println!("JEI's list by mod: {by_mod}");
    fallbacks_check(&categories);
    for uid in ENTITY_CATEGORIES {
        let Some(c) = categories.iter().find(|c| c.uid == *uid) else { continue };
        assert!(c.entities > 0, "{uid}: its pages drew no entity");
        assert!(c.errors.is_empty(), "{uid}: {:?}", c.errors);
    }
}

fn fallbacks_check(categories: &[Category]) {
    let fallbacks: Vec<String> =
        categories.iter().filter(|c| c.fallbacks > 0).map(|c| format!("{} {}", c.uid, c.fallbacks)).collect();
    println!("categories whose layouts drew grey boxes for missing pictures: {fallbacks:?}");
    for line in SAID.lock().map(|s| s.clone()).unwrap_or_default().iter().filter(|l| l.contains("grey box")) {
        println!("  {line}");
    }
}

/// kiln/tests/JeiMods.java built against JEI's jar into a mod of its own, `kiln_jei_mods`.
fn walker(kiln: &Path, jei: &Path) {
    let classes = kiln.parent().unwrap().join("jei-mods");
    std::fs::create_dir_all(&classes).unwrap();
    let mut path = vec![jei.to_path_buf(), kiln.join("kiln-runtime.jar")];
    path.extend(m::jars(&kiln.join("lib")));
    let javac = std::process::Command::new("javac")
        .arg("-nowarn")
        .arg("-cp")
        .arg(std::env::join_paths(&path).unwrap())
        .arg("-d")
        .arg(&classes)
        .arg(Path::new(env!("CARGO_MANIFEST_DIR")).join("tests/JeiMods.java"))
        .status()
        .expect("javac on PATH: a JDK, as tools/build-java.sh needs");
    assert!(javac.success(), "kiln/tests/JeiMods.java didn't compile");
    std::fs::write(
        classes.join("fabric.mod.json"),
        r#"{"schemaVersion": 1, "id": "kiln_jei_mods", "version": "1", "name": "Kiln's JEI walk",
  "environment": "client", "entrypoints": {"jei_mod_plugin": ["kiln.test.JeiMods"]}}"#,
    )
    .unwrap();
    let jar = std::process::Command::new("jar")
        .arg("--create")
        .arg("--file")
        .arg(kiln.join("mods/kiln-jei-mods.jar"))
        .arg("-C")
        .arg(&classes)
        .arg(".")
        .status()
        .expect("jar on PATH: a JDK");
    assert!(jar.success(), "kiln-jei-mods.jar wasn't made");
}

/// A mod's id, namespaces, JEI plugins and recipe files, from its jar and the jars nested in it.
fn read_mod(jar: &Path) -> Mod {
    let mut out = Mod { jar: jar.file_name().unwrap().to_string_lossy().into_owned(), ..Mod::default() };
    let bytes = std::fs::read(jar).unwrap();
    read_zip(&bytes, &mut out, true);
    out
}

fn read_zip(bytes: &[u8], out: &mut Mod, top: bool) {
    let Ok(mut zip) = zip::ZipArchive::new(std::io::Cursor::new(bytes)) else { return };
    let mut nested = Vec::new();
    for i in 0..zip.len() {
        let Ok(mut entry) = zip.by_index(i) else { continue };
        let Ok(name) = entry.name().map(|n| n.to_string()) else { continue };
        if name == "fabric.mod.json" {
            let mut text = String::new();
            let _ = entry.read_to_string(&mut text);
            if let Ok(json) = serde_json::from_str::<serde_json::Value>(&text) {
                if top {
                    out.id = json["id"].as_str().unwrap_or_default().to_string();
                }
                if let Some(plugins) = json["entrypoints"]["jei_mod_plugin"].as_array() {
                    for plugin in plugins {
                        let class = plugin.as_str().or(plugin["value"].as_str()).unwrap_or_default();
                        out.plugins.push(class.to_string());
                    }
                }
            }
        } else if name.starts_with("META-INF/jars/") && name.ends_with(".jar") {
            let mut inner = Vec::new();
            if entry.read_to_end(&mut inner).is_ok() {
                nested.push(inner);
            }
        } else if let Some(rest) = name.strip_prefix("data/").or(name.strip_prefix("assets/")) {
            let mut parts = rest.split('/');
            let namespace = parts.next().unwrap_or_default();
            if !namespace.is_empty() && namespace != "minecraft" && namespace != "c" && namespace != "fabric" {
                if top {
                    out.namespaces.insert(namespace.to_string());
                }
                if name.starts_with("data/") && parts.next() == Some("recipe") && name.ends_with(".json") {
                    *out.recipe_files.entry(namespace.to_string()).or_default() += 1;
                }
            }
        }
    }
    for inner in nested {
        read_zip(&inner, out, false);
    }
    if top && !out.id.is_empty() {
        out.namespaces.insert(out.id.clone());
    }
}

/// JeiMods.java's lines.
fn categories(text: &str) -> Vec<Category> {
    let mut out: Vec<Category> = Vec::new();
    let mut errors: BTreeMap<String, Vec<String>> = BTreeMap::new();
    for line in text.lines() {
        if let Some(rest) = after(line, "Kiln's JEI category: ") {
            let mut c = Category::default();
            let (fields, title) = rest.split_once(" title=").unwrap_or((rest, ""));
            c.title = title.to_string();
            for field in fields.split(' ') {
                let (key, value) = field.split_once('=').unwrap_or((field, ""));
                match key {
                    "uid" => c.uid = value.to_string(),
                    "recipes" => c.recipes = value.parse().unwrap_or(0),
                    "stations" => c.stations = value.parse().unwrap_or(0),
                    "layouts" => c.layouts = value.split('/').next().unwrap().parse().unwrap_or(0),
                    "drawn" => c.drawn = value.to_string(),
                    "icon" => c.icon = value.to_string(),
                    "fluids" => c.fluids = value.parse().unwrap_or(0),
                    "fallbacks" => c.fallbacks = value.parse().unwrap_or(0),
                    "entities" => c.entities = value.parse().unwrap_or(0),
                    "ids" => {
                        for pair in value.split(',').filter(|p| *p != "-") {
                            let (ns, n) = pair.rsplit_once(':').unwrap_or((pair, "0"));
                            c.ids.insert(ns.to_string(), n.parse().unwrap_or(0));
                        }
                    }
                    _ => {}
                }
            }
            out.push(c);
        } else if let Some(rest) = after(line, "Kiln's JEI category error: ") {
            let (uid, error) = rest.split_once(' ').unwrap_or((rest, ""));
            errors.entry(uid.to_string()).or_default().push(error.to_string());
        }
    }
    for c in &mut out {
        c.errors = errors.remove(&c.uid).unwrap_or_default();
    }
    for (uid, errors) in errors {
        out.push(Category { uid, errors, ..Category::default() });
    }
    out
}

fn after<'a>(line: &'a str, mark: &str) -> Option<&'a str> {
    line.find(mark).map(|at| &line[at + mark.len()..])
}

/// JEI's "Caught an error from mod plugin: <class> <uid>" and the first line of the error under it.
fn failed_plugins(text: &str) -> Vec<(String, String)> {
    let lines: Vec<&str> = text.lines().collect();
    let mut out = Vec::new();
    for (i, line) in lines.iter().enumerate() {
        if let Some(rest) = after(line, "Caught an error from mod plugin: ") {
            let error = lines[i + 1..]
                .iter()
                .find(|l| !l.trim().is_empty() && !l.trim_start().starts_with("at "))
                .map(|l| l.trim().to_string())
                .unwrap_or_default();
            out.push((rest.trim().to_string(), error));
        }
    }
    out
}

fn report(
    mods: &[Mod],
    categories: &[Category],
    failed: &[(String, String)],
    running: &str,
    done: bool,
    took: Duration,
) -> serde_json::Value {
    let mut rows = Vec::new();
    for md in mods {
        let own: Vec<&Category> = categories
            .iter()
            .filter(|c| c.uid.split(':').next().is_some_and(|ns| md.namespaces.contains(ns)))
            .collect();
        let shown: u64 = categories
            .iter()
            .flat_map(|c| c.ids.iter())
            .filter(|(ns, _)| md.namespaces.contains(*ns))
            .map(|(_, n)| n)
            .sum();
        let files: u64 =
            md.recipe_files.iter().filter(|(ns, _)| md.namespaces.contains(*ns)).map(|(_, n)| n).sum();
        let plugins_failed: Vec<serde_json::Value> = failed
            .iter()
            .filter(|(plugin, _)| md.plugins.iter().any(|p| plugin.contains(p.as_str())))
            .map(|(plugin, error)| serde_json::json!({"plugin": plugin, "error": error}))
            .collect();
        rows.push(serde_json::json!({
            "id": md.id,
            "jar": md.jar,
            "plugins": md.plugins,
            "plugins_failed": plugins_failed,
            "categories": own.len(),
            "recipes_in_own_categories": own.iter().map(|c| c.recipes).sum::<u64>(),
            "layouts_in_own_categories": own.iter().map(|c| c.layouts).sum::<u64>(),
            "recipe_files": files,
            "recipes_shown": shown,
            "own": own.iter().map(|c| category_json(c)).collect::<Vec<_>>(),
        }));
    }
    serde_json::json!({
        "jei_done": done,
        "seconds": took.as_secs_f64(),
        "running": running,
        "game_recipes": categories.iter().filter(|c| c.uid.starts_with("minecraft:")).map(|c| c.recipes).sum::<u64>(),
        "categories": categories.len(),
        "plugins_failed": failed.iter().map(|(p, e)| serde_json::json!({"plugin": p, "error": e})).collect::<Vec<_>>(),
        "mods": rows,
        "all": categories.iter().map(category_json).collect::<Vec<_>>(),
    })
}

fn category_json(c: &Category) -> serde_json::Value {
    serde_json::json!({
        "uid": c.uid, "title": c.title, "recipes": c.recipes, "stations": c.stations, "layouts": c.layouts,
        "drawn": c.drawn, "icon": c.icon, "entities": c.entities, "ids": c.ids, "errors": c.errors,
    })
}

fn table(report: &serde_json::Value) -> String {
    let mut out = format!(
        "== JEI: {} categories, {} recipes in the game's, done {} in {:.1} s; {}\n",
        report["categories"], report["game_recipes"], report["jei_done"], report["seconds"].as_f64().unwrap(),
        report["running"].as_str().unwrap()
    );
    for failed in report["plugins_failed"].as_array().unwrap() {
        out += &format!("   plugin failed: {} -- {}\n", failed["plugin"], failed["error"]);
    }
    for row in report["mods"].as_array().unwrap() {
        out += &format!(
            "== {} ({}): {} plugins, {} failed; {} own categories, {} recipes in them, {} layouts; recipe files {}, \
             shown {}\n",
            row["id"],
            row["jar"],
            row["plugins"].as_array().unwrap().len(),
            row["plugins_failed"].as_array().unwrap().len(),
            row["categories"],
            row["recipes_in_own_categories"],
            row["layouts_in_own_categories"],
            row["recipe_files"],
            row["recipes_shown"],
        );
        for c in row["own"].as_array().unwrap() {
            out += &format!(
                "   {} \"{}\": {} recipes, {} layouts, drawn {}, {} stations, icon {}, {} entities drawn\n",
                c["uid"].as_str().unwrap(),
                c["title"].as_str().unwrap(),
                c["recipes"],
                c["layouts"],
                c["drawn"].as_str().unwrap(),
                c["stations"],
                c["icon"].as_str().unwrap(),
                c["entities"]
            );
            for e in c["errors"].as_array().unwrap() {
                out += &format!("      error: {}\n", e.as_str().unwrap());
            }
        }
    }
    for c in report["all"].as_array().unwrap() {
        if !c["errors"].as_array().unwrap().is_empty() && c["uid"].as_str().unwrap().starts_with("minecraft:") {
            out += &format!("   game category {}: {:?}\n", c["uid"], c["errors"]);
        }
    }
    out
}
