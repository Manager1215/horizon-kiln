// Tech Reborn's machines under Kiln with real Java, as Horizon's server ticks them, without the game: the iron
// furnace smelting raw iron with coal; the electric furnace and the grinder ticking; a solar panel charging an
// electric furnace beside it, which then smelts; a thermal generator burning lava from a bucket; the industrial grinder
// and sawmill on water, in their casings; a cable between a panel and a furnace (not yet: Kiln doesn't fire Fabric's
// ServerTickEvents, which its cable networks tick on). Needs a JDK, `sh tools/build-java.sh` and Tech Reborn's jar
// (Modrinth, MIT), named in KILN_TECH_REBORN; the game's answers (blocks, the smelting recipe for raw iron, coal's
// burn time, the sky, item tags) are the test's.
//
//   KILN_TECH_REBORN=TechReborn-6.2.1.jar cargo test -p horizon-kiln-mod --test tech_reborn -- --ignored --nocapture

use std::collections::BTreeMap;
use std::path::{Path, PathBuf};
use std::sync::Mutex;
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;

/// Java is one per process: the tests take turns.
static JAVA: Mutex<()> = Mutex::new(());

/// What Kiln logs: on the test's output (a call that failed in Java, what Java said: tools/check-mods.py looks there
/// for failures), and every line kept for the tests to read (what Kiln says it read of a mod's recipes).
struct Said;

static SAID: Mutex<Vec<String>> = Mutex::new(Vec::new());

impl log::Log for Said {
    fn enabled(&self, _: &log::Metadata) -> bool {
        true
    }

    fn log(&self, record: &log::Record) {
        if record.level() <= log::Level::Info {
            println!("{}: {}", record.level(), record.args());
        }
        if let Ok(mut said) = SAID.lock() {
            said.push(record.args().to_string());
        }
    }

    fn flush(&self) {}
}

/// A game folder of its own for test `name`, with Kiln, the libraries and Tech Reborn (Tech Reborn writes its settings
/// there at its first start, as at a player's first launch); returns it and Kiln's data folder inside it.
fn game_folder(jar: &Path, name: &str) -> (PathBuf, PathBuf) {
    let built = Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln");
    assert!(
        built.join("kiln-runtime.jar").is_file(),
        "run sh tools/build-java.sh first"
    );
    let game = std::env::temp_dir().join(format!("kiln-tech-reborn-{}-{name}", std::process::id()));
    let kiln = game.join("kiln");
    let data = game.join("horizon-mods/.horizon-mod-data/kiln");
    let _ = std::fs::remove_dir_all(&game);
    std::fs::create_dir_all(kiln.join("mods")).unwrap();
    std::fs::create_dir_all(kiln.join("lib")).unwrap();
    std::fs::copy(
        built.join("kiln-runtime.jar"),
        kiln.join("kiln-runtime.jar"),
    )
    .unwrap();
    for lib in m::jars(&built.join("lib")) {
        std::fs::copy(&lib, kiln.join("lib").join(lib.file_name().unwrap())).unwrap();
    }
    std::fs::copy(jar, kiln.join("mods").join(jar.file_name().unwrap())).unwrap();
    std::fs::create_dir_all(&data).unwrap();
    (game, data)
}

/// Java started with Tech Reborn for test `name`, its blocks announced; None when KILN_TECH_REBORN names no jar.
fn started(name: &str) -> Option<PathBuf> {
    let Some(jar) = std::env::var_os("KILN_TECH_REBORN") else {
        println!("KILN_TECH_REBORN names no jar: nothing to run");
        return None;
    };
    let _ = log::set_logger(&Said).map(|()| log::set_max_level(log::LevelFilter::Info));
    let (game, data) = game_folder(Path::new(&jar), name);
    let snapshot = m::Snapshot {
        connected: true,
        in_world: true,
        paused: false,
        game_time: 10,
        dimension: "minecraft:overworld".into(),
        advancements: None,
        player: None,
        ..m::Snapshot::default()
    };
    // Java is the thread's (as the window's): each test starts its own and waits for its blocks.
    let before = m::blocks_announced();
    let deadline = Instant::now() + Duration::from_secs(60);
    while m::blocks_announced() == before || m::block_entity_type("techreborn:iron_furnace").is_none() {
        assert!(
            Instant::now() < deadline,
            "Tech Reborn's blocks were never announced:\n{}",
            java_log(&game)
        );
        m::tick(&data, || None, || vec![6; 128], snapshot.clone());
        std::thread::sleep(Duration::from_millis(10));
    }
    Some(game)
}

fn java_log(game: &Path) -> String {
    std::fs::read_to_string(game.join("kiln/java.log")).unwrap_or_default()
}

/// A block of the test's world, and its block entity (type, saved compound) when it has one.
struct Placed {
    state: String,
    entity: Option<(String, String)>,
}

/// A few blocks in the overworld, ticked as Horizon's server ticks them, and the game's answers to their code.
struct World {
    game: PathBuf,
    blocks: BTreeMap<[i32; 3], Placed>,
    /// Positions that see the sky (the rest don't), and the time of day (vanilla's: 0 to 24000, day before 12000).
    sky: Vec<[i32; 3]>,
    day_time: u64,
    game_time: u64,
    /// The questions Java asked, and the sounds its code played, counted.
    asked: BTreeMap<String, usize>,
    sounds: BTreeMap<String, usize>,
    /// What each machine's screen shows, as its code last said (`MachineData`).
    data: BTreeMap<[i32; 3], Vec<i32>>,
}

impl World {
    fn new(game: PathBuf) -> World {
        World {
            game,
            blocks: BTreeMap::new(),
            sky: Vec::new(),
            day_time: 6000,
            game_time: 1000,
            asked: BTreeMap::new(),
            sounds: BTreeMap::new(),
            data: BTreeMap::new(),
        }
    }

    /// A machine at `at`, whose block entity Tech Reborn's code makes, with `saved` as its compound.
    fn machine(&mut self, at: [i32; 3], state: &str, saved: &str) {
        let id = state.split('[').next().unwrap();
        let entity = m::block_entity_type(id).unwrap_or_else(|| panic!("{id} has no block entity"));
        self.blocks.insert(
            at,
            Placed {
                state: state.to_owned(),
                entity: Some((entity, saved.to_owned())),
            },
        );
    }

    fn state(&self, at: [i32; 3]) -> &str {
        &self.blocks[&at].state
    }

    fn saved(&self, at: [i32; 3]) -> &str {
        &self.blocks[&at].entity.as_ref().unwrap().1
    }

    /// The game's answer to Kiln's Java.
    fn answer(&mut self, ticking: [i32; 3], what: &[String]) -> String {
        *self.asked.entry(what.join(" ")).or_default() += 1;
        let pos = || -> Option<[i32; 3]> {
            Some([
                what.get(1)?.parse().ok()?,
                what.get(2)?.parse().ok()?,
                what.get(3)?.parse().ok()?,
            ])
        };
        match what[0].as_str() {
            "block" => pos()
                .and_then(|p| self.blocks.get(&p))
                .map_or("minecraft:air".to_owned(), |b| b.state.clone()),
            // The one ticking is out of the world meanwhile, as in Horizon: Java has it.
            "block_entity" => pos()
                .filter(|p| *p != ticking)
                .and_then(|p| self.blocks.get(&p)?.entity.as_ref())
                .map_or(String::new(), |(ty, saved)| format!("{ty} {saved}")),
            "sky" => pos()
                .map(|p| self.sky.contains(&p).to_string())
                .unwrap_or_default(),
            "weather" => "0 0".to_owned(),
            // The overworld's sun: overhead at noon (6000), 180 degrees from there at midnight.
            "sun_angle" => {
                let from_noon = (self.day_time + 24000 - 6000) % 24000;
                (from_noon as f32 / 24000.0 * 360.0).to_string()
            }
            // The game's own: vanilla's iron_ingot_from_smelting_raw_iron, and coal's 1600 ticks.
            "recipe" if what[1..] == ["minecraft:smelting", "minecraft:raw_iron"] => {
                "minecraft:iron_ingot_from_smelting_raw_iron 0.7 200 minecraft:iron_ingot 1 minecraft:raw_iron"
                    .to_owned()
            }
            "fuel" if what[1] == "minecraft:coal" => "1600 1".to_owned(),
            // The game's item tags, as far as the tests go: the iron ores, the oak logs.
            "tag" => (what[1..] == ["minecraft:iron_ores", "minecraft:iron_ore"]
                || what[1..] == ["minecraft:iron_ores", "minecraft:deepslate_iron_ore"]
                || what[1..] == ["minecraft:oak_logs", "minecraft:oak_log"])
                .to_string(),
            _ => String::new(),
        }
    }

    /// One server tick: each block entity that ticks, in order; what its code did is done before the next.
    fn tick(&mut self) {
        self.game_time += 1;
        self.day_time += 1;
        let ticking: Vec<[i32; 3]> = self
            .blocks
            .iter()
            .filter(|(_, b)| {
                let id = b.state.split('[').next().unwrap();
                b.entity.is_some() && m::block_does(id, "ticks")
            })
            .map(|(at, _)| *at)
            .collect();
        for at in ticking {
            let state = self.blocks[&at].state.clone();
            let (entity, saved) = self.blocks[&at].entity.clone().unwrap();
            let world = self.game.join("world");
            let game_time = self.game_time;
            // A call Java answers past its time (a cold JVM loading a machine's classes) is lost, as in the game,
            // whose next tick tries again: twice more here before it counts as a failure.
            let mut outcome = None;
            for _ in 0..3 {
                outcome = m::block_entity_tick(
                    &world,
                    game_time,
                    "minecraft:overworld",
                    at,
                    &state,
                    &entity,
                    &saved,
                    &mut |what| self.answer(at, what),
                );
                if outcome.is_some() {
                    break;
                }
                std::thread::sleep(Duration::from_millis(300));
            }
            let Some(outcome) = outcome else {
                panic!(
                    "{state} at {at:?} failed its tick {game_time}:\n{}",
                    java_log(&self.game)
                );
            };
            for effect in outcome.effects {
                match effect {
                    m::Effect::BlockEntity { pos, data } => {
                        self.blocks.get_mut(&pos).unwrap().entity.as_mut().unwrap().1 = data;
                    }
                    m::Effect::Block { pos, state, .. } => {
                        self.blocks.get_mut(&pos).unwrap().state = state;
                    }
                    m::Effect::Sound { id, .. } => *self.sounds.entry(id).or_default() += 1,
                    m::Effect::Data { pos, values } => {
                        self.data.insert(pos, values);
                    }
                    other => panic!("{state}'s tick did {other:?}"),
                }
            }
        }
    }
}

#[test]
#[ignore = "needs a JDK, sh tools/build-java.sh and KILN_TECH_REBORN"]
fn the_iron_furnace_smelts() {
    let _java = JAVA.lock().unwrap_or_else(|e| e.into_inner());
    let Some(game) = started("the_iron_furnace_smelts") else {
        return;
    };
    const FURNACE: &str = "techreborn:iron_furnace";
    assert!(m::block_does(FURNACE, "ticks"));
    // Three slots (input, output, fuel), which no face reaches: Tech Reborn's machines take and give items through
    // the sides their screen sets, none by default.
    let slots = m::block_slots(FURNACE).expect("the iron furnace is a container");
    assert_eq!(slots.count, 3);
    assert!(slots.faces.iter().all(Vec::is_empty), "{slots:?}");

    let mut world = World::new(game);
    let at = [0, 64, 0];
    // Three raw iron and a coal, as the game keeps a container's slots.
    world.machine(
        at,
        &format!("{FURNACE}[active=false,facing=north]"),
        r#"{Items:[{Slot:0b,count:3,id:"minecraft:raw_iron"},{Slot:2b,count:1,id:"minecraft:coal"}]}"#,
    );
    let mut lit_at = None;
    let started = Instant::now();
    for tick in 1..=700u64 {
        world.tick();
        if world.state(at).contains("active=true") {
            lit_at.get_or_insert(tick);
        }
    }
    println!("700 ticks of the iron furnace: {:?} each", started.elapsed() / 700);
    println!("asked: {:?}", world.asked);
    let saved = world.saved(at).to_owned();
    println!("saved: {saved}");
    // Coal lit it at once; each raw iron took 160 ticks (200, Tech Reborn's cooking scale 1.25); the coal burns 2000
    // (1600, its fuel scale 1.25) and is gone from its slot.
    assert_eq!(lit_at, Some(1));
    assert_eq!(world.state(at), format!("{FURNACE}[active=true,facing=north]"));
    assert!(
        saved.contains(r#"Items:[{Slot:1b,count:3,id:"minecraft:iron_ingot"}]"#),
        "{saved}"
    );
    assert!(saved.contains("Experience:2.1"), "{saved}");
    assert!(saved.contains("TotalBurnTime:2000"), "{saved}");
    // The game's recipe is asked again only when the input changes: Tech Reborn keeps it in a field of the object
    // Kiln keeps between ticks.
    assert!(
        world.asked["recipe minecraft:smelting minecraft:raw_iron"] < 20,
        "{:?}",
        world.asked
    );
    assert_eq!(world.asked["fuel minecraft:coal"], 1, "{:?}", world.asked);
}

/// The energy a Tech Reborn machine holds, from its saved compound (`PowerAcceptor:{energy:163L}`).
fn energy(saved: &str) -> u64 {
    let at = saved.find("PowerAcceptor:{energy:").expect(saved) + "PowerAcceptor:{energy:".len();
    saved[at..].split('L').next().unwrap().parse().unwrap()
}

#[test]
#[ignore = "needs a JDK, sh tools/build-java.sh and KILN_TECH_REBORN"]
fn machines_on_energy_tick() {
    let _java = JAVA.lock().unwrap_or_else(|e| e.into_inner());
    let Some(game) = started("machines_on_energy_tick") else {
        return;
    };
    // Each with a battery slot (Fabric's ContainerStorage over the machine's slots); no energy, so nothing runs.
    let mut world = World::new(game);
    let furnace = [0, 64, 0];
    let grinder = [4, 64, 0];
    let raw_iron = r#"{Items:[{Slot:0b,count:3,id:"minecraft:raw_iron"}]}"#;
    world.machine(furnace, "techreborn:electric_furnace[active=false,facing=north]", raw_iron);
    world.machine(
        grinder,
        "techreborn:grinder[active=false,facing=north]",
        r#"{Items:[{Slot:0b,count:3,id:"minecraft:iron_ore"}]}"#,
    );
    // Two solar panels, one under the open sky and one under a roof; it is night first, then day.
    let open = [8, 64, 0];
    let roofed = [12, 64, 0];
    world.machine(open, "techreborn:basic_solar_panel[active=false]", "{}");
    world.machine(roofed, "techreborn:basic_solar_panel[active=false]", "{}");
    world.sky.push([8, 65, 0]);
    world.day_time = 18000;
    for _ in 0..200 {
        world.tick();
    }
    assert_eq!(energy(world.saved(open)), 0, "{}", world.saved(open));
    world.day_time = 5000;
    for _ in 0..200 {
        world.tick();
    }
    println!("asked: {:?}", world.asked);
    for at in [furnace, grinder] {
        let saved = world.saved(at);
        assert_eq!(energy(saved), 0, "{saved}");
        assert!(saved.contains("Slot:0b,count:3"), "{saved}");
    }
    assert!(world.saved(furnace).contains(raw_iron.trim_matches(['{', '}'])), "{}", world.saved(furnace));
    // By day the open one makes energy and shows it (`active`); the roofed one makes none.
    let made = energy(world.saved(open));
    assert!(made > 100, "{}", world.saved(open));
    assert!(world.state(open).starts_with("techreborn:basic_solar_panel[active=true"), "{}", world.state(open));
    assert_eq!(energy(world.saved(roofed)), 0, "{}", world.saved(roofed));
    assert!(world.asked["sky 8 65 0"] >= 10, "{:?}", world.asked);
    assert!(world.asked.contains_key("sun_angle 8 64 0"), "{:?}", world.asked);
}

#[test]
#[ignore = "needs a JDK, sh tools/build-java.sh and KILN_TECH_REBORN"]
fn a_solar_panel_powers_the_electric_furnace() {
    let _java = JAVA.lock().unwrap_or_else(|e| e.into_inner());
    let Some(game) = started("a_solar_panel_powers_the_electric_furnace") else {
        return;
    };
    let mut world = World::new(game);
    // A panel by day beside a furnace with two raw iron (Team Reborn Energy's EnergyStorage.SIDED, found through
    // Fabric's BlockApiLookup at the neighbour's position); a second furnace a block away from it gets nothing.
    let furnace = [0, 64, 0];
    let panel = [1, 64, 0];
    let away = [3, 64, 0];
    let two_raw_iron = r#"{Items:[{Slot:0b,count:2,id:"minecraft:raw_iron"}]}"#;
    world.machine(furnace, "techreborn:electric_furnace[active=false,facing=north]", two_raw_iron);
    world.machine(away, "techreborn:electric_furnace[active=false,facing=north]", two_raw_iron);
    world.machine(panel, "techreborn:basic_solar_panel[active=false]", "{}");
    world.sky.push([1, 65, 0]);
    let mut smelted_at = None;
    let started = Instant::now();
    for tick in 1..=1000u64 {
        world.tick();
        if smelted_at.is_none() && world.saved(furnace).contains(r#"{Slot:1b,count:2,id:"minecraft:iron_ingot"}"#) {
            smelted_at = Some(tick);
        }
    }
    println!("1000 ticks of three machines: {:?} each", started.elapsed() / 1000);
    println!("smelted at {smelted_at:?}; sounds {:?}", world.sounds);
    let saved = world.saved(furnace).to_owned();
    println!("furnace: {}", saved.split(",slotConfig").next().unwrap());
    println!("panel: {}", world.saved(panel));
    // Both raw iron became ingots by the panel's energy, and the furnace is full of it (1000) once done.
    assert!(smelted_at.is_some_and(|t| t < 1000), "{saved}");
    assert!(saved.contains(r#"Items:[{Slot:1b,count:2,id:"minecraft:iron_ingot"}]"#), "{saved}");
    assert!(energy(&saved) > 0, "{saved}");
    assert_eq!(world.state(furnace), "techreborn:electric_furnace[active=false,facing=north]");
    assert!(world.sounds.get("techreborn:machine_run").is_some_and(|n| *n > 0), "{:?}", world.sounds);
    // The one a block away got none, and smelted nothing.
    assert_eq!(energy(world.saved(away)), 0, "{}", world.saved(away));
    assert!(world.saved(away).contains(two_raw_iron.trim_matches(['{', '}'])), "{}", world.saved(away));
}

/// Whether Kiln fires Fabric's ServerTickEvents.START_SERVER_TICK. Not yet: Tech Reborn's cable networks tick by a
/// counter that event moves on, so each cable ticks once (the counter stays at 0) and the energy stops in the first
/// cable. Until it does, the test below asserts that; when Kiln fires it, it fails: set this to true.
const KILN_FIRES_SERVER_TICK_EVENTS: bool = false;

#[test]
#[ignore = "needs a JDK, sh tools/build-java.sh and KILN_TECH_REBORN"]
fn a_cable_carries_a_panel_s_energy() {
    let _java = JAVA.lock().unwrap_or_else(|e| e.into_inner());
    let Some(game) = started("a_cable_carries_a_panel_s_energy") else {
        return;
    };
    let mut world = World::new(game);
    // A panel by day, two copper cables, then a furnace with two raw iron, three blocks from the panel: its energy
    // goes through the cables (Tech Reborn's CableTickManager, through the cables' neighbours). A furnace as far
    // from the panel with no cable gets nothing.
    let panel = [0, 64, 0];
    let cables = [[1, 64, 0], [2, 64, 0]];
    let furnace = [3, 64, 0];
    let unlinked = [0, 64, 3];
    let two_raw_iron = r#"{Items:[{Slot:0b,count:2,id:"minecraft:raw_iron"}]}"#;
    world.machine(panel, "techreborn:basic_solar_panel[active=false]", "{}");
    world.sky.push([0, 65, 0]);
    for at in cables {
        world.machine(
            at,
            "techreborn:copper_cable[down=false,east=true,north=false,south=false,up=false,waterlogged=false,west=true]",
            "{}",
        );
    }
    world.machine(furnace, "techreborn:electric_furnace[active=false,facing=north]", two_raw_iron);
    world.machine(unlinked, "techreborn:electric_furnace[active=false,facing=north]", two_raw_iron);
    let mut powered_at = None;
    let mut smelted_at = None;
    for tick in 1..=1500u64 {
        world.tick();
        if powered_at.is_none() && energy(world.saved(furnace)) > 0 {
            powered_at = Some(tick);
        }
        if smelted_at.is_none() && world.saved(furnace).contains(r#"{Slot:1b,count:2,id:"minecraft:iron_ingot"}"#) {
            smelted_at = Some(tick);
        }
    }
    println!("energy reached the furnace at {powered_at:?}, smelted at {smelted_at:?}");
    for at in cables {
        println!("cable {at:?}: {}", world.saved(at));
    }
    println!("panel: {}", world.saved(panel));
    let saved = world.saved(furnace).to_owned();
    println!("furnace: {}", saved.split(",slotConfig").next().unwrap());
    // A cable keeps its energy as `{energy:512L}`.
    let held = |saved: &str| -> u64 {
        let at = saved.find("energy:").expect(saved) + "energy:".len();
        saved[at..].split('L').next().unwrap().parse().unwrap()
    };
    if !KILN_FIRES_SERVER_TICK_EVENTS {
        assert!(
            powered_at.is_none() && held(world.saved(cables[0])) > 0 && held(world.saved(cables[1])) == 0,
            "energy came through the cables: Kiln fires ServerTickEvents now? Set KILN_FIRES_SERVER_TICK_EVENTS"
        );
        return;
    }
    assert!(powered_at.is_some(), "no energy came through the cables: {saved}");
    assert!(smelted_at.is_some(), "the furnace didn't smelt on the cables' energy: {saved}");
    assert_eq!(energy(world.saved(unlinked)), 0, "{}", world.saved(unlinked));
    assert!(world.saved(unlinked).contains(two_raw_iron.trim_matches(['{', '}'])), "{}", world.saved(unlinked));
}

impl World {
    /// A player beside `at` right-clicks it with an empty hand, on its west face.
    fn use_block(&mut self, at: [i32; 3]) -> m::Outcome {
        let player = m::PlayerSnapshot {
            uuid: 0x00c0_ffee,
            name: "Kiln".into(),
            dimension: "minecraft:overworld".into(),
            position: [at[0] as f64 - 1.5, at[1] as f64, at[2] as f64 + 0.5],
            game_mode: 0,
            health: 20.0,
            main: None,
            off: None,
            attributes: vec![("minecraft:max_health".into(), 20.0, 20.0)],
            ..Default::default()
        };
        let state = self.blocks[&at].state.clone();
        let world = self.game.join("world");
        let game_time = self.game_time;
        // As a tick: a click Java answered past its time is tried again.
        for _ in 0..3 {
            let used = m::block_used(
                &world,
                game_time,
                at,
                &state,
                "west",
                m::Hand::Main,
                &player,
                &mut |what| self.answer([i32::MIN; 3], what),
            );
            if let Some(used) = used {
                return used;
            }
            std::thread::sleep(Duration::from_millis(300));
        }
        panic!("using {state} failed:\n{}", java_log(&self.game))
    }
}

#[test]
#[ignore = "needs a JDK, sh tools/build-java.sh and KILN_TECH_REBORN"]
fn a_machine_opens_its_menu_and_shows_its_energy() {
    let _java = JAVA.lock().unwrap_or_else(|e| e.into_inner());
    let Some(game) = started("a_machine_opens_its_menu_and_shows_its_energy") else {
        return;
    };
    let mut world = World::new(game);
    let furnace = [0, 64, 0];
    let panel = [1, 64, 0];
    world.machine(
        furnace,
        "techreborn:electric_furnace[active=false,facing=north]",
        r#"{Items:[{Slot:0b,count:2,id:"minecraft:raw_iron"}]}"#,
    );
    world.machine(panel, "techreborn:basic_solar_panel[active=false]", "{}");
    world.sky.push([1, 65, 0]);
    // A click on the furnace runs Tech Reborn's own code, which opens its menu (`Player.openMenu`): Kiln says where
    // the menu's slots are, as its code lays them out (RebornCore's builder: input, output, battery; the player's
    // inventory below), and the click is the mod's.
    let used = world.use_block(furnace);
    println!("used: {used:?}");
    assert!(used.result.takes_use(), "{used:?}");
    let layout = used
        .effects
        .iter()
        .find_map(|e| match e {
            m::Effect::Menu(layout) => Some(layout.clone()),
            _ => None,
        })
        .expect("the furnace's code opened no menu");
    assert_eq!(layout.slots, vec![(0, 55, 45), (1, 101, 45), (2, 8, 72)], "{layout:?}");
    assert_eq!(layout.inventory, [8, 94]);
    // On a 3x3 grid (the furnace's 3 slots), its other 6 hidden; the energy bar left, the arrow between input and
    // output, where Tech Reborn draws them.
    assert_eq!(m::grid_menu(3), Some(("minecraft:generic_3x3", 9)));
    let places = layout.slot_places(9);
    assert_eq!(places[..3], [Some([55.0, 45.0]), Some([101.0, 45.0]), Some([8.0, 72.0])]);
    assert!(places[3..].iter().all(Option::is_none));
    assert_eq!(layout.size(), [176.0, 176.0]);
    assert_eq!(layout.energy_bar(), Some([9.0, 19.0, 14.0, 50.0]));
    assert_eq!(layout.progress_arrow(), Some([76.0, 48.0, 22.0, 10.0]));
    // Each tick, what its screen shows goes to the game with its block entity: energy against capacity, progress.
    let mut shown = Vec::new();
    for _ in 0..300 {
        world.tick();
        if let Some(data) = world.data.get(&furnace) {
            shown.push(m::machine_data(data));
        }
    }
    let last = *shown.last().expect("the furnace's screen showed nothing");
    println!("shown: first {:?}, last {last:?}", shown.first());
    assert!(last.3 == 1000, "{last:?}");
    assert!(last.2 > 0 && last.0 > 0.0, "{last:?}");
    assert!(shown.iter().any(|d| d.1.is_some_and(|p| p > 0.0)), "the furnace's progress never showed");
}

#[test]
#[ignore = "needs a JDK, sh tools/build-java.sh and KILN_TECH_REBORN"]
fn the_grinder_grinds_by_its_own_recipes() {
    let _java = JAVA.lock().unwrap_or_else(|e| e.into_inner());
    let Some(game) = started("the_grinder_grinds_by_its_own_recipes") else {
        return;
    };
    let mut world = World::new(game.clone());
    // Two iron ore in a grinder with some energy, a solar panel beside it by day. Tech Reborn's own recipe,
    // `techreborn:grinder` (grinder/raw_iron.json: `#minecraft:iron_ores`, two raw iron, 270 ticks), read from its jar
    // with its own codec; the ore's tag asked of the game.
    let grinder = [0, 64, 0];
    let panel = [1, 64, 0];
    world.machine(
        grinder,
        "techreborn:grinder[active=false,facing=north]",
        r#"{Items:[{Slot:0b,count:2,id:"minecraft:iron_ore"}],PowerAcceptor:{energy:400L}}"#,
    );
    world.machine(panel, "techreborn:basic_solar_panel[active=false]", "{}");
    world.sky.push([1, 65, 0]);
    let mut ground_at = None;
    let mut active = false;
    for tick in 1..=1500u64 {
        world.tick();
        active |= world.state(grinder).contains("active=true");
        if ground_at.is_none() && world.saved(grinder).contains(r#"{Slot:1b,count:4,id:"minecraft:raw_iron"}"#) {
            ground_at = Some(tick);
        }
    }
    let saved = world.saved(grinder).to_owned();
    println!("ground at {ground_at:?}: {}", saved.split(",slotConfig").next().unwrap());
    println!("asked: {:?}", world.asked.iter().filter(|(k, _)| k.starts_with("tag")).collect::<Vec<_>>());
    // Read at Java's start: the grinder's 175 recipes but the 3 for Applied Energistics 2 (their load conditions).
    let read = SAID
        .lock()
        .unwrap()
        .iter()
        .find(|line| line.contains("Kiln read "))
        .cloned()
        .expect("Kiln said nothing of the mods' recipes");
    println!("{}", read.split("; first refused").next().unwrap());
    let count = |what: &str| -> usize {
        read.split(&format!("{what}=")).nth(1).and_then(|r| r.split([',', '}']).next()?.parse().ok()).unwrap_or(0)
    };
    assert_eq!(count("techreborn:grinder"), 172, "{read}");
    assert!(count("techreborn:chemical_reactor") > 500, "{read}");
    assert!(ground_at.is_some(), "{saved}");
    assert!(saved.contains(r#"Items:[{Slot:1b,count:4,id:"minecraft:raw_iron"}]"#), "{saved}");
    assert!(active, "the grinder never showed it was running");
    // The tag was asked once for the ore, and kept.
    assert_eq!(world.asked.get("tag minecraft:iron_ores minecraft:iron_ore"), Some(&1), "{:?}", world.asked);
}

#[test]
#[ignore = "needs a JDK, sh tools/build-java.sh and KILN_TECH_REBORN"]
fn a_thermal_generator_burns_lava_from_a_bucket() {
    let _java = JAVA.lock().unwrap_or_else(|e| e.into_inner());
    let Some(game) = started("a_thermal_generator_burns_lava_from_a_bucket") else {
        return;
    };
    let mut world = World::new(game);
    // A lava bucket in the generator's slot, an electric furnace with two raw iron beside it. Its code empties the
    // bucket into its tank through Fabric's FluidStorage (the game's buckets, which Kiln's stand-in knows), burns the
    // lava by its own recipe (`techreborn:thermal_generator`, `minecraft:lava`) and hands the energy to the furnace.
    let generator = [0, 64, 0];
    let furnace = [1, 64, 0];
    world.machine(
        generator,
        "techreborn:thermal_generator[active=false,facing=north]",
        r#"{Items:[{Slot:0b,count:1,id:"minecraft:lava_bucket"}]}"#,
    );
    world.machine(
        furnace,
        "techreborn:electric_furnace[active=false,facing=north]",
        r#"{Items:[{Slot:0b,count:2,id:"minecraft:raw_iron"}]}"#,
    );
    // Its menu: the bucket in, the empty one out; its tank gauge beside them, where Tech Reborn draws its tank.
    let layout = world
        .use_block(generator)
        .effects
        .into_iter()
        .find_map(|e| match e {
            m::Effect::Menu(layout) => Some(layout),
            _ => None,
        })
        .expect("the generator's code opened no menu");
    assert_eq!(layout.slots, vec![(0, 25, 35), (1, 25, 55)]);
    assert_eq!(layout.tank_gauge(), Some([44.0, 19.0, 20.0, 50.0]));
    let mut burning_at = None;
    for tick in 1..=600u64 {
        world.tick();
        if burning_at.is_none() && world.state(generator).contains("active=true") {
            burning_at = Some(tick);
        }
    }
    let saved = world.saved(generator).to_owned();
    println!("burning at {burning_at:?}: {}", saved.split(",Upgrades").next().unwrap());
    println!("furnace: {}", world.saved(furnace).split(",Upgrades").next().unwrap());
    assert!(saved.contains(r#"Items:[{Slot:1b,count:1,id:"minecraft:bucket"}]"#), "{saved}");
    assert!(saved.contains(r#"fluid:{fluid:"minecraft:lava"}"#), "{saved}");
    assert!(energy(&saved) > 1000, "{saved}");
    assert!(burning_at.is_some(), "the generator never burned");
    assert!(
        world.saved(furnace).contains(r#"Items:[{Slot:1b,count:2,id:"minecraft:iron_ingot"}]"#),
        "{}",
        world.saved(furnace)
    );
    // Its screen shows the lava: orange, most of a bucket left of its 10.
    let (full, colour, amount, most) =
        m::machine_tank(&world.data[&generator]).expect("the generator's screen shows no tank");
    println!("tank: {amount} / {most} mB");
    assert_eq!(colour, 0xFF_6600);
    assert_eq!(most, 10_000);
    assert!(amount > 500 && amount < 1000, "{amount}");
    assert!(full > 0.05 && full < 0.1, "{full}");
}

impl World {
    /// The casings a Tech Reborn multiblock machine at `at` facing north needs behind it (the industrial grinder's and
    /// sawmill's): basic ones below and above, a ring of advanced ones around water between.
    fn casings(&mut self, [mx, my, mz]: [i32; 3]) {
        for x in mx - 1..=mx + 1 {
            for z in mz + 1..=mz + 3 {
                for (y, casing) in [(my - 1, "basic"), (my, "advanced"), (my + 1, "basic")] {
                    let state = if (x, y, z) == (mx, my, mz + 2) {
                        "minecraft:water[level=0]".to_owned()
                    } else {
                        format!("techreborn:{casing}_machine_casing")
                    };
                    self.blocks.insert([x, y, z], Placed { state, entity: None });
                }
            }
        }
    }
}

#[test]
#[ignore = "needs a JDK, sh tools/build-java.sh and KILN_TECH_REBORN"]
fn the_industrial_grinder_and_sawmill_work_with_water() {
    let _java = JAVA.lock().unwrap_or_else(|e| e.into_inner());
    let Some(game) = started("the_industrial_grinder_and_sawmill_work_with_water") else {
        return;
    };
    let mut world = World::new(game);
    // An industrial grinder and an industrial sawmill, each with its casings behind it (their code checks the blocks,
    // the water in the middle too, through the level as a `BlockGetter`), a water bucket in its fluid slot and energy;
    // a third grinder without casings beside them.
    let grinder = [0, 64, 0];
    let sawmill = [10, 64, 0];
    let alone = [20, 64, 0];
    world.casings(grinder);
    world.casings(sawmill);
    let ore = r#"{Items:[{Slot:0b,count:1,id:"minecraft:iron_ore"},{Slot:1b,count:1,id:"minecraft:water_bucket"}],PowerAcceptor:{energy:10000L}}"#;
    world.machine(grinder, "techreborn:industrial_grinder[active=false,facing=north]", ore);
    world.machine(alone, "techreborn:industrial_grinder[active=false,facing=north]", ore);
    world.machine(
        sawmill,
        "techreborn:industrial_sawmill[active=false,facing=north]",
        r#"{Items:[{Slot:0b,count:1,id:"minecraft:oak_log"},{Slot:1b,count:1,id:"minecraft:water_bucket"}],PowerAcceptor:{energy:10000L}}"#,
    );
    let mut water_shown = false;
    for _ in 0..400 {
        world.tick();
        water_shown |= world
            .data
            .get(&grinder)
            .and_then(|d| m::machine_tank(d))
            .is_some_and(|(full, colour, ..)| full > 0.0 && colour == 0x33_77EE);
    }
    let saved = world.saved(grinder).to_owned();
    println!("grinder: {}", saved.split(",Upgrades").next().unwrap());
    println!("sawmill: {}", world.saved(sawmill).split(",Upgrades").next().unwrap());
    println!("alone: {}", world.saved(alone).split(",Upgrades").next().unwrap());
    // Its own recipe (industrial_grinder/raw_iron_from_iron_ore_with_water: the iron ores' tag and a bucket of water,
    // 64 E a tick for 100 ticks): the bucket's water went into its tank and was used; the empty bucket is in its slot.
    assert!(
        saved.contains(
            r#"Items:[{Slot:2b,count:3,id:"minecraft:raw_iron"},{Slot:3b,count:3,id:"techreborn:tin_nugget"},{Slot:4b,count:3,id:"techreborn:nickel_nugget"},{Slot:6b,count:1,id:"minecraft:bucket"}]"#
        ),
        "{saved}"
    );
    assert!(saved.contains(r#"amount:{value:0L}"#), "{saved}");
    assert_eq!(energy(&saved), 10_000 - 64 * 100, "{saved}");
    assert!(water_shown, "the grinder's screen never showed its water");
    // The sawmill's: oak logs and a bucket of water, four planks and three sawdust.
    let sawn = world.saved(sawmill);
    assert!(
        sawn.contains(r#"{Slot:2b,count:4,id:"minecraft:oak_planks"},{Slot:3b,count:3,id:"techreborn:saw_dust"}"#),
        "{sawn}"
    );
    // Without its casings, the third took the water but ground nothing.
    let alone = world.saved(alone);
    assert!(alone.contains(r#"{Slot:0b,count:1,id:"minecraft:iron_ore"}"#), "{alone}");
    assert!(!alone.contains("raw_iron"), "{alone}");
    assert_eq!(energy(alone), 10_000, "{alone}");
}

#[test]
#[ignore = "needs a JDK, sh tools/build-java.sh and KILN_TECH_REBORN"]
fn a_grinder_finds_the_casings_built_after_it() {
    let _java = JAVA.lock().unwrap_or_else(|e| e.into_inner());
    let Some(game) = started("a_grinder_finds_the_casings_built_after_it") else {
        return;
    };
    let mut world = World::new(game.clone());
    // Placed first, it ticks without its casings: its code checks them as it loads, and finds none.
    let grinder = [0, 64, 0];
    world.machine(
        grinder,
        "techreborn:industrial_grinder[active=false,facing=north]",
        r#"{Items:[{Slot:0b,count:1,id:"minecraft:iron_ore"},{Slot:1b,count:1,id:"minecraft:water_bucket"}],PowerAcceptor:{energy:10000L}}"#,
    );
    for _ in 0..40 {
        world.tick();
    }
    // The casings built: RebornCore hears a block change through its Mixin into the game's chunks, which Kiln has
    // not, so the grinder doesn't see them...
    world.casings(grinder);
    for _ in 0..150 {
        world.tick();
    }
    assert!(!world.saved(grinder).contains("raw_iron"), "{}", world.saved(grinder));
    // ...until a player's block placed nearby (the last casing) loads it again, as a chunk loading would. Its crafter
    // looks for its recipe when its slots change, or every 200 ticks; the grind takes 100.
    m::blocks_changed(&game.join("world"), world.game_time, "minecraft:overworld", [0, 65, 3]);
    for _ in 0..400 {
        world.tick();
    }
    let saved = world.saved(grinder);
    println!("grinder: {}", saved.split(",Upgrades").next().unwrap());
    assert!(saved.contains(r#"{Slot:2b,count:3,id:"minecraft:raw_iron"}"#), "{saved}");
}
