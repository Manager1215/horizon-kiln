// Tech Reborn's machines under Kiln with real Java, as Horizon's server ticks them, without the game: the iron
// furnace smelting raw iron with coal; the electric furnace and the grinder ticking; a solar panel charging an
// electric furnace beside it, which then smelts; a thermal generator burning lava from a bucket; the industrial grinder
// and sawmill on water, in their casings; a cable between a panel and a furnace (Fabric's ServerTickEvents, which its
// cable networks tick on); a grinder's output fed to an electric furnace (Fabric's ItemStorage); the game's own
// containers as storages, a double chest and a hopper filled by grinders, a furnace fed from a chest (D-144); its
// client entrypoint starting, the rolling machine by its shaped recipes, cells into and out of a tank unit (D-181,
// D-182, D-183). Needs a JDK, `sh tools/build-java.sh` and Tech Reborn's jar (Modrinth, MIT), named in
// KILN_TECH_REBORN; the game's answers
// (blocks, the game's containers, the smelting recipe for raw iron, coal's burn time, the sky, item tags) are the
// test's.
//
//   KILN_TECH_REBORN=TechReborn-6.2.1.jar cargo test -p horizon-kiln-mod --test tech_reborn -- --ignored --nocapture

use std::collections::BTreeMap;
use std::path::{Path, PathBuf};
use std::sync::Mutex;
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;

mod nbt;

/// Java is one per process: the tests take turns.
static JAVA: Mutex<()> = Mutex::new(());

/// Set in the process a test runs in on its own ([`in_own_process`]).
const OWN_PROCESS: &str = "KILN_TEST_OWN_PROCESS";

/// Each test with Java in a process of its own. A process has one JVM (D-112): in one test binary only its first
/// test would get Java in the game's process, and every other the separate process's fallback, whose server calls
/// give up after `CALL_TIMEOUT` (250 ms), so on a busy computer a call was lost and a test failed (D-266). The binary
/// runs itself again for the one test `name`; true in that process, false in the one that ran it (and saw it pass).
fn in_own_process(name: &str) -> bool {
    if std::env::var_os(OWN_PROCESS).is_some() {
        return true;
    }
    let status = std::process::Command::new(std::env::current_exe().unwrap())
        .args([
            name,
            "--exact",
            "--ignored",
            "--nocapture",
            "--test-threads=1",
        ])
        .env(OWN_PROCESS, "1")
        .status()
        .unwrap();
    assert!(status.success(), "{name} failed in its own process");
    false
}

/// What Kiln logs: on the test's output (a call that failed in Java, what Java said: tools/check-mods.py looks there
/// for failures), and every line kept for the tests to read (what Kiln says it read of a mod's recipes).
struct Said;

static SAID: Mutex<Vec<String>> = Mutex::new(Vec::new());

/// What Kiln put in the window's chat while Java started (which mods started, which didn't and why).
static CHAT: Mutex<Vec<String>> = Mutex::new(Vec::new());

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
    if !in_own_process(name) {
        return None;
    }
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
    // Only a hang fails on this: how fast Java starts on a busy computer is no matter here (D-266).
    let deadline = Instant::now() + Duration::from_secs(120);
    while m::blocks_announced() == before
        || m::block_entity_type("techreborn:iron_furnace").is_none()
    {
        assert!(
            Instant::now() < deadline,
            "Tech Reborn's blocks were never announced:\n{}",
            java_log(&game)
        );
        let chat = m::tick(&data, || None, || vec![6; 128], snapshot.clone());
        CHAT.lock().unwrap().extend(chat);
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
    /// The game's own containers (a chest, a hopper), which Horizon keeps: their slot count and slots (`Items`'s list,
    /// as vanilla saves them).
    containers: BTreeMap<[i32; 3], (usize, String)>,
    /// The game's own fluid holders (a cauldron, a native mod's tank: Horizon's API 54), one tank each: its fluid
    /// (empty for none), amount and capacity in millibuckets, through every side.
    tanks: BTreeMap<[i32; 3], (String, i32, i32)>,
    /// Positions that see the sky (the rest don't), and the time of day (vanilla's: 0 to 24000, day before 12000).
    sky: Vec<[i32; 3]>,
    day_time: u64,
    game_time: u64,
    /// The questions Java asked, and the sounds its code played, counted.
    asked: BTreeMap<String, usize>,
    sounds: BTreeMap<String, usize>,
    /// What each machine's screen shows, as its code last said (`MachineData`).
    data: BTreeMap<[i32; 3], Vec<i32>>,
    /// Fabric's tick events Java answered, by phase (`ServerTickEvents`), and the time they took, calls not made
    /// (nobody listening) included.
    events: BTreeMap<&'static str, usize>,
    events_took: Duration,
    /// The players on the server (`PlayerLookup`'s), and the payloads the mods sent each, by type, counted.
    players: Vec<m::Online>,
    sent: BTreeMap<(u128, String), usize>,
}

impl World {
    fn new(game: PathBuf) -> World {
        World {
            game,
            blocks: BTreeMap::new(),
            containers: BTreeMap::new(),
            tanks: BTreeMap::new(),
            sky: Vec::new(),
            day_time: 6000,
            game_time: 1000,
            asked: BTreeMap::new(),
            sounds: BTreeMap::new(),
            data: BTreeMap::new(),
            events: BTreeMap::new(),
            events_took: Duration::ZERO,
            players: Vec::new(),
            sent: BTreeMap::new(),
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

    /// One of the game's own containers at `at`: `state`, with `slots` slots holding `items` (`[]`, or vanilla's list).
    fn container(&mut self, at: [i32; 3], state: &str, slots: usize, items: &str) {
        self.blocks.insert(
            at,
            Placed {
                state: state.to_owned(),
                entity: None,
            },
        );
        self.containers.insert(at, (slots, items.to_owned()));
    }

    /// What the game's container at `at` holds.
    fn held(&self, at: [i32; 3]) -> &str {
        &self.containers[&at].1
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
                .map_or(String::new(), |(ty, saved)| {
                    format!("{ty} {}", m::nbt_field(&nbt::nbt(saved)))
                }),
            "container" => pos()
                .and_then(|p| self.containers.get(&p))
                .map_or(String::new(), |(slots, items)| format!("{slots} {{Items:{items}}}")),
            "fluid_tanks" => pos()
                .and_then(|p| self.tanks.get(&p))
                .map_or("none".to_owned(), |(fluid, amount, capacity)| {
                    let name = if fluid.is_empty() { "-" } else { fluid };
                    format!("{name} {amount} {capacity} 1 {}", u8::from(*amount > 0))
                }),
            "sky" => pos()
                .map(|p| self.sky.contains(&p).to_string())
                .unwrap_or_default(),
            "weather" => "0 0".to_owned(),
            "players" => m::players_answer(&self.players, None, 10),
            // Every chunk of the test's world is loaded.
            "loaded" => "true".to_owned(),
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

    /// Fabric's tick event of `phase` (the overworld's for a level's), when a mod hears it; what its code did is done.
    fn event(&mut self, phase: m::TickPhase) {
        let dimension = match phase {
            m::TickPhase::LevelStart | m::TickPhase::LevelEnd => "minecraft:overworld",
            _ => "",
        };
        let world = self.game.join("world");
        let game_time = self.game_time;
        let started = Instant::now();
        let outcome = m::server_tick(&world, game_time, phase, dimension, &mut |what| {
            self.answer([i32::MIN; 3], what)
        });
        self.events_took += started.elapsed();
        if let Some(outcome) = outcome {
            *self.events.entry(phase.names().1).or_default() += 1;
            self.effects("a tick event", outcome.effects);
        }
    }

    /// What a mod's code did, done in the world.
    fn effects(&mut self, what: &str, effects: Vec<m::Effect>) {
        for effect in effects {
            match effect {
                m::Effect::BlockEntity { pos, data } => {
                    self.blocks
                        .get_mut(&pos)
                        .unwrap()
                        .entity
                        .as_mut()
                        .unwrap()
                        .1 = nbt::snbt(&data);
                }
                m::Effect::Block { pos, state, .. } => {
                    self.blocks.get_mut(&pos).unwrap().state = state;
                }
                // The slots as Java read them come first, then as it left them: the game changes those that still
                // hold what Java read, here all of them, as nothing else moves items in the test.
                m::Effect::Container { pos, data } => {
                    let (before, items) = data
                        .strip_prefix("{Before:")
                        .and_then(|rest| rest.strip_suffix('}'))
                        .and_then(|rest| rest.split_once(",Items:"))
                        .unwrap_or_else(|| {
                            panic!("{what} changed the container at {pos:?} with {data}")
                        });
                    let held = &mut self.containers.get_mut(&pos).unwrap().1;
                    assert_eq!(
                        before, held,
                        "{what} read the container at {pos:?} other than it is"
                    );
                    *held = items.to_owned();
                }
                // Horizon fills and drains its tank (`native_fluid_insert`, `native_fluid_extract`): one fluid at a time.
                m::Effect::Fluid {
                    pos, fluid, amount, ..
                } => {
                    let tank = self.tanks.get_mut(&pos).unwrap_or_else(|| {
                        panic!("{what} moved {fluid} at {pos:?}, where no tank is")
                    });
                    assert!(
                        tank.0.is_empty() || tank.0 == fluid,
                        "{what} put {fluid} into {tank:?}"
                    );
                    tank.1 += amount;
                    assert!((0..=tank.2).contains(&tank.1), "{what} left {tank:?}");
                    tank.0 = if tank.1 == 0 { String::new() } else { fluid };
                }
                m::Effect::Sound { id, .. } => *self.sounds.entry(id).or_default() += 1,
                m::Effect::Data { pos, values } => {
                    self.data.insert(pos, values);
                }
                // A payload: its type's id comes first, after its length (one byte for these).
                m::Effect::Net { player, data } => {
                    let id = String::from_utf8_lossy(&data[1..=data[0] as usize]).into_owned();
                    *self.sent.entry((player, id)).or_default() += 1;
                }
                other => panic!("{what} did {other:?}"),
            }
        }
    }

    /// One server tick, as Horizon's server ticks: the server's tick events and the overworld's around each block
    /// entity that ticks, in order; what each one's code did is done before the next.
    fn tick(&mut self) {
        self.game_time += 1;
        self.day_time += 1;
        self.event(m::TickPhase::Start);
        self.event(m::TickPhase::LevelStart);
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
                    &nbt::nbt(&saved),
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
            self.effects(&format!("{state}'s tick"), outcome.effects);
        }
        self.event(m::TickPhase::LevelEnd);
        self.event(m::TickPhase::End);
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
    println!(
        "700 ticks of the iron furnace: {:?} each",
        started.elapsed() / 700
    );
    println!("asked: {:?}", world.asked);
    let saved = world.saved(at).to_owned();
    println!("saved: {saved}");
    // Coal lit it at once; each raw iron took 160 ticks (200, Tech Reborn's cooking scale 1.25); the coal burns 2000
    // (1600, its fuel scale 1.25) and is gone from its slot.
    assert_eq!(lit_at, Some(1));
    assert_eq!(
        world.state(at),
        format!("{FURNACE}[active=true,facing=north]")
    );
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
    world.machine(
        furnace,
        "techreborn:electric_furnace[active=false,facing=north]",
        raw_iron,
    );
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
    assert!(
        world
            .saved(furnace)
            .contains(raw_iron.trim_matches(['{', '}'])),
        "{}",
        world.saved(furnace)
    );
    // By day the open one makes energy and shows it (`active`); the roofed one makes none.
    let made = energy(world.saved(open));
    assert!(made > 100, "{}", world.saved(open));
    assert!(
        world
            .state(open)
            .starts_with("techreborn:basic_solar_panel[active=true"),
        "{}",
        world.state(open)
    );
    assert_eq!(energy(world.saved(roofed)), 0, "{}", world.saved(roofed));
    assert!(world.asked["sky 8 65 0"] >= 10, "{:?}", world.asked);
    assert!(
        world.asked.contains_key("sun_angle 8 64 0"),
        "{:?}",
        world.asked
    );
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
    world.machine(
        furnace,
        "techreborn:electric_furnace[active=false,facing=north]",
        two_raw_iron,
    );
    world.machine(
        away,
        "techreborn:electric_furnace[active=false,facing=north]",
        two_raw_iron,
    );
    world.machine(panel, "techreborn:basic_solar_panel[active=false]", "{}");
    world.sky.push([1, 65, 0]);
    let mut smelted_at = None;
    let started = Instant::now();
    for tick in 1..=1000u64 {
        world.tick();
        if smelted_at.is_none()
            && world
                .saved(furnace)
                .contains(r#"{Slot:1b,count:2,id:"minecraft:iron_ingot"}"#)
        {
            smelted_at = Some(tick);
        }
    }
    println!(
        "1000 ticks of three machines: {:?} each",
        started.elapsed() / 1000
    );
    println!("smelted at {smelted_at:?}; sounds {:?}", world.sounds);
    let saved = world.saved(furnace).to_owned();
    println!("furnace: {}", saved.split(",slotConfig").next().unwrap());
    println!("panel: {}", world.saved(panel));
    // Both raw iron became ingots by the panel's energy, and the furnace is full of it (1000) once done.
    assert!(smelted_at.is_some_and(|t| t < 1000), "{saved}");
    assert!(
        saved.contains(r#"Items:[{Slot:1b,count:2,id:"minecraft:iron_ingot"}]"#),
        "{saved}"
    );
    assert!(energy(&saved) > 0, "{saved}");
    assert_eq!(
        world.state(furnace),
        "techreborn:electric_furnace[active=false,facing=north]"
    );
    assert!(
        world
            .sounds
            .get("techreborn:machine_run")
            .is_some_and(|n| *n > 0),
        "{:?}",
        world.sounds
    );
    // The one a block away got none, and smelted nothing.
    assert_eq!(energy(world.saved(away)), 0, "{}", world.saved(away));
    assert!(
        world
            .saved(away)
            .contains(two_raw_iron.trim_matches(['{', '}'])),
        "{}",
        world.saved(away)
    );
}

#[test]
#[ignore = "needs a JDK, sh tools/build-java.sh and KILN_TECH_REBORN"]
fn a_cable_carries_a_panel_s_energy() {
    let _java = JAVA.lock().unwrap_or_else(|e| e.into_inner());
    let Some(game) = started("a_cable_carries_a_panel_s_energy") else {
        return;
    };
    let mut world = World::new(game);
    // A panel by day, two copper cables, then a furnace with two raw iron, three blocks from the panel: its energy
    // goes through the cables. Tech Reborn's CableTickManager ticks a network once a server tick, by a counter that
    // Fabric's START_SERVER_TICK moves on: the first cable to tick gathers the others through its neighbours and
    // shares the network's energy out to the machines beside it. A furnace as far from the panel with no cable gets
    // nothing.
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
    world.machine(
        furnace,
        "techreborn:electric_furnace[active=false,facing=north]",
        two_raw_iron,
    );
    world.machine(
        unlinked,
        "techreborn:electric_furnace[active=false,facing=north]",
        two_raw_iron,
    );
    let mut powered_at = None;
    let mut smelted_at = None;
    let started = Instant::now();
    for tick in 1..=1500u64 {
        world.tick();
        if powered_at.is_none() && energy(world.saved(furnace)) > 0 {
            powered_at = Some(tick);
        }
        if smelted_at.is_none()
            && world
                .saved(furnace)
                .contains(r#"{Slot:1b,count:2,id:"minecraft:iron_ingot"}"#)
        {
            smelted_at = Some(tick);
        }
    }
    println!(
        "1500 ticks of a panel, two cables and two furnaces: {:?} each",
        started.elapsed() / 1500
    );
    println!("energy reached the furnace at {powered_at:?}, smelted at {smelted_at:?}");
    let calls: usize = world.events.values().sum();
    println!(
        "tick events: {:?}, {:?} each call, {:?} a tick for all four phases",
        world.events,
        world.events_took / calls.max(1) as u32,
        world.events_took / 1500
    );
    for at in cables {
        println!("cable {at:?}: {}", world.saved(at));
    }
    println!("panel: {}", world.saved(panel));
    let saved = world.saved(furnace).to_owned();
    println!("furnace: {}", saved.split(",slotConfig").next().unwrap());
    assert!(
        powered_at.is_some(),
        "no energy came through the cables: {saved}"
    );
    assert!(
        smelted_at.is_some(),
        "the furnace didn't smelt on the cables' energy: {saved}"
    );
    assert!(
        saved.contains(r#"Items:[{Slot:1b,count:2,id:"minecraft:iron_ingot"}]"#),
        "{saved}"
    );
    assert_eq!(
        energy(world.saved(unlinked)),
        0,
        "{}",
        world.saved(unlinked)
    );
    assert!(
        world
            .saved(unlinked)
            .contains(two_raw_iron.trim_matches(['{', '}'])),
        "{}",
        world.saved(unlinked)
    );
    // START_SERVER_TICK once a tick from the first cable's tick on (its class registers the listener as it loads),
    // RebornCore's START_LEVEL_TICK every tick; nobody listens to the ends. A cold Java may miss a call or two at the
    // start, and Kiln skips a second of tick events after one (TICK_EVENTS_SKIPPED).
    let at_least = |n: Option<&usize>| n.is_some_and(|n| (1400..1500).contains(n));
    assert!(
        at_least(world.events.get("server_tick_start")),
        "{:?}",
        world.events
    );
    assert!(
        at_least(world.events.get("level_tick_start"))
            || world.events.get("level_tick_start") == Some(&1500)
    );
    assert_eq!(
        world.events.get("server_tick_end"),
        None,
        "{:?}",
        world.events
    );
    // The network's chunk asked once a tick at most, not once a cable.
    let loaded: usize = world
        .asked
        .iter()
        .filter(|(k, _)| k.starts_with("loaded"))
        .map(|(_, n)| n)
        .sum();
    assert!(loaded <= 1500, "{:?}", world.asked);
}

/// A player a block and a half west of `at`, in survival with empty hands.
fn beside(at: [i32; 3]) -> m::PlayerSnapshot {
    m::PlayerSnapshot {
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
    }
}

impl World {
    /// A player beside `at` presses menu button `id` on its screen (Horizon's `native_menu_button`).
    fn menu_button(&mut self, at: [i32; 3], id: i32) {
        let player = beside(at);
        let block = self.blocks[&at].state.split('[').next().unwrap().to_owned();
        let world = self.game.join("world");
        let game_time = self.game_time;
        // As a tick: a press Java answered past its time is tried again (each press names the mode it sets, so a
        // second one changes nothing more).
        for _ in 0..3 {
            let pressed = m::menu_button(&world, game_time, at, &block, id, &player, &mut |what| {
                self.answer([i32::MIN; 3], what)
            });
            if let Some(pressed) = pressed {
                self.effects("a menu button", pressed.effects);
                return;
            }
            std::thread::sleep(Duration::from_millis(300));
        }
        panic!(
            "menu button {id} of {block} failed:\n{}",
            java_log(&self.game)
        )
    }

    /// A player beside `at` right-clicks it with an empty hand, on its west face.
    fn use_block(&mut self, at: [i32; 3]) -> m::Outcome {
        let player = beside(at);
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
    assert_eq!(
        layout.slots,
        vec![(0, 55, 45), (1, 101, 45), (2, 8, 72)],
        "{layout:?}"
    );
    assert_eq!(layout.inventory, [8, 94]);
    // On a 3x3 grid (the furnace's 3 slots), its other 6 hidden; the energy bar left, the arrow between input and
    // output, where Tech Reborn draws them.
    assert_eq!(m::grid_menu(3), Some(("minecraft:generic_3x3", 9)));
    let places = layout.slot_places(9);
    assert_eq!(
        places[..3],
        [Some([55.0, 45.0]), Some([101.0, 45.0]), Some([8.0, 72.0])]
    );
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
    assert!(
        shown.iter().any(|d| d.1.is_some_and(|p| p > 0.0)),
        "the furnace's progress never showed"
    );
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
        if ground_at.is_none()
            && world
                .saved(grinder)
                .contains(r#"{Slot:1b,count:4,id:"minecraft:raw_iron"}"#)
        {
            ground_at = Some(tick);
        }
    }
    let saved = world.saved(grinder).to_owned();
    println!(
        "ground at {ground_at:?}: {}",
        saved.split(",slotConfig").next().unwrap()
    );
    println!(
        "asked: {:?}",
        world
            .asked
            .iter()
            .filter(|(k, _)| k.starts_with("tag"))
            .collect::<Vec<_>>()
    );
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
        read.split(&format!("{what}="))
            .nth(1)
            .and_then(|r| r.split([',', '}']).next()?.parse().ok())
            .unwrap_or(0)
    };
    assert_eq!(count("techreborn:grinder"), 172, "{read}");
    assert!(count("techreborn:chemical_reactor") > 500, "{read}");
    assert!(ground_at.is_some(), "{saved}");
    assert!(
        saved.contains(r#"Items:[{Slot:1b,count:4,id:"minecraft:raw_iron"}]"#),
        "{saved}"
    );
    assert!(active, "the grinder never showed it was running");
    // The tag was asked once for the ore, and kept.
    assert_eq!(
        world
            .asked
            .get("tag minecraft:iron_ores minecraft:iron_ore"),
        Some(&1),
        "{:?}",
        world.asked
    );
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
    println!(
        "burning at {burning_at:?}: {}",
        saved.split(",Upgrades").next().unwrap()
    );
    println!(
        "furnace: {}",
        world.saved(furnace).split(",Upgrades").next().unwrap()
    );
    assert!(
        saved.contains(r#"Items:[{Slot:1b,count:1,id:"minecraft:bucket"}]"#),
        "{saved}"
    );
    assert!(
        saved.contains(r#"fluid:{fluid:"minecraft:lava"}"#),
        "{saved}"
    );
    assert!(energy(&saved) > 1000, "{saved}");
    assert!(burning_at.is_some(), "the generator never burned");
    assert!(
        world
            .saved(furnace)
            .contains(r#"Items:[{Slot:1b,count:2,id:"minecraft:iron_ingot"}]"#),
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
                    self.blocks.insert(
                        [x, y, z],
                        Placed {
                            state,
                            entity: None,
                        },
                    );
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
    world.machine(
        grinder,
        "techreborn:industrial_grinder[active=false,facing=north]",
        ore,
    );
    world.machine(
        alone,
        "techreborn:industrial_grinder[active=false,facing=north]",
        ore,
    );
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
    println!(
        "sawmill: {}",
        world.saved(sawmill).split(",Upgrades").next().unwrap()
    );
    println!(
        "alone: {}",
        world.saved(alone).split(",Upgrades").next().unwrap()
    );
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
    assert!(
        alone.contains(r#"{Slot:0b,count:1,id:"minecraft:iron_ore"}"#),
        "{alone}"
    );
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
    assert!(
        !world.saved(grinder).contains("raw_iron"),
        "{}",
        world.saved(grinder)
    );
    // ...until a player's block placed nearby (the last casing) loads it again, as a chunk loading would. Its crafter
    // looks for its recipe when its slots change, or every 200 ticks; the grind takes 100.
    m::blocks_changed(
        &game.join("world"),
        world.game_time,
        "minecraft:overworld",
        [0, 65, 3],
    );
    for _ in 0..400 {
        world.tick();
    }
    let saved = world.saved(grinder);
    println!("grinder: {}", saved.split(",Upgrades").next().unwrap());
    assert!(
        saved.contains(r#"{Slot:2b,count:3,id:"minecraft:raw_iron"}"#),
        "{saved}"
    );
}

/// `saved` with slot `slot` of a Tech Reborn machine set to `io` (0 none, 1 input, 2 output) on face `side`
/// (`Direction`'s ordinal: down, up, north, south, west, east) and its automatic input and output (`slotConfig`, as its
/// screen sets it).
fn configured(
    saved: &str,
    slot: usize,
    side: usize,
    io: usize,
    auto_in: bool,
    auto_out: bool,
) -> String {
    let holder = format!("slot_{slot}:{{");
    let start = saved
        .find(&holder)
        .unwrap_or_else(|| panic!("no {holder} in {saved}"));
    // The holder's compound: up to its closing brace.
    let mut depth = 0;
    let mut end = start + holder.len() - 1;
    for (i, c) in saved[end..].char_indices() {
        match c {
            '{' => depth += 1,
            '}' => {
                depth -= 1;
                if depth == 0 {
                    end += i + 1;
                    break;
                }
            }
            _ => {}
        }
    }
    let flag = |b: bool| if b { "1b" } else { "0b" };
    let edited = saved[start..end]
        .replace(
            &format!("{{config:0}},side:{side}"),
            &format!("{{config:{io}}},side:{side}"),
        )
        .replace(
            &format!("side:{side},config:{{config:0}}"),
            &format!("side:{side},config:{{config:{io}}}"),
        )
        .replace("input:0b", &format!("input:{}", flag(auto_in)))
        .replace("output:0b", &format!("output:{}", flag(auto_out)));
    assert_ne!(
        edited,
        saved[start..end],
        "nothing to set in {}",
        &saved[start..end]
    );
    format!("{}{edited}{}", &saved[..start], &saved[end..])
}

#[test]
#[ignore = "needs a JDK, sh tools/build-java.sh and KILN_TECH_REBORN"]
fn a_grinder_feeds_an_electric_furnace() {
    let _java = JAVA.lock().unwrap_or_else(|e| e.into_inner());
    let Some(game) = started("a_grinder_feeds_an_electric_furnace") else {
        return;
    };
    let mut world = World::new(game);
    // A grinder grinding two iron ore into four raw iron, its output pushed east into an electric furnace beside it,
    // which smelts them: RebornCore's slot configuration moves a slot's items by Fabric's ItemStorage.SIDED and
    // StorageUtil.move, into the furnace as a Container seen from its west face. Each machine has a panel of its own
    // by day. Tech Reborn's machines take and give items through no face until their screen sets one: the test sets
    // them as a player would, the grinder's output (slot 1) to the east with automatic output, the furnace's input
    // (slot 0) from the west.
    let grinder = [0, 64, 0];
    let furnace = [1, 64, 0];
    world.machine(
        grinder,
        "techreborn:grinder[active=false,facing=north]",
        "{}",
    );
    world.machine(
        furnace,
        "techreborn:electric_furnace[active=false,facing=north]",
        "{}",
    );
    world.machine(
        [0, 64, 1],
        "techreborn:basic_solar_panel[active=false]",
        "{}",
    );
    world.machine(
        [1, 64, 1],
        "techreborn:basic_solar_panel[active=false]",
        "{}",
    );
    world.sky.extend([[0, 65, 1], [1, 65, 1]]);
    // Their slot settings as Tech Reborn writes them at their first tick, then set.
    world.tick();
    println!("grinder as made: {}", world.saved(grinder));
    println!("furnace as made: {}", world.saved(furnace));
    let ore = r#"{Slot:0b,count:2,id:"minecraft:iron_ore"}"#;
    let grinder_saved = configured(world.saved(grinder), 1, 5, 2, false, true)
        .replacen("Items:[]", &format!("Items:[{ore}]"), 1)
        .replace("PowerAcceptor:{energy:0L}", "PowerAcceptor:{energy:400L}");
    let furnace_saved = configured(world.saved(furnace), 0, 4, 1, false, false);
    world
        .blocks
        .get_mut(&grinder)
        .unwrap()
        .entity
        .as_mut()
        .unwrap()
        .1 = grinder_saved;
    world
        .blocks
        .get_mut(&furnace)
        .unwrap()
        .entity
        .as_mut()
        .unwrap()
        .1 = furnace_saved;
    let mut ground_at = None;
    let mut fed_at = None;
    let mut smelted_at = None;
    let started = Instant::now();
    for tick in 2..=2000u64 {
        world.tick();
        if ground_at.is_none() && world.saved(grinder).contains(r#"Slot:1b"#) {
            ground_at = Some(tick);
        }
        if fed_at.is_none() && world.saved(furnace).contains(r#"id:"minecraft:raw_iron""#) {
            fed_at = Some(tick);
        }
        if smelted_at.is_none()
            && world
                .saved(furnace)
                .contains(r#"{Slot:1b,count:4,id:"minecraft:iron_ingot"}"#)
        {
            smelted_at = Some(tick);
        }
    }
    println!(
        "2000 ticks of a grinder, a furnace and their panels: {:?} each",
        started.elapsed() / 2000
    );
    println!(
        "tick events: {:?}, {:?} a tick",
        world.events,
        world.events_took / 2000
    );
    println!("ground at {ground_at:?}, fed at {fed_at:?}, smelted at {smelted_at:?}");
    let grinder_now = world.saved(grinder).to_owned();
    let furnace_now = world.saved(furnace).to_owned();
    println!(
        "grinder: {}",
        grinder_now.split(",slotConfig").next().unwrap()
    );
    println!(
        "furnace: {}",
        furnace_now.split(",slotConfig").next().unwrap()
    );
    assert!(
        fed_at.is_some(),
        "no raw iron reached the furnace: {grinder_now}\n{furnace_now}"
    );
    assert!(
        smelted_at.is_some(),
        "the furnace didn't smelt the grinder's raw iron: {furnace_now}"
    );
    // Everything the grinder made went on: nothing is left in its output, nor in the furnace's input.
    assert!(!grinder_now.contains("raw_iron"), "{grinder_now}");
    assert!(
        furnace_now.contains(r#"Items:[{Slot:1b,count:4,id:"minecraft:iron_ingot"}]"#),
        "{furnace_now}"
    );
}

#[test]
#[ignore = "needs a JDK, sh tools/build-java.sh and KILN_TECH_REBORN"]
fn a_machines_sides_are_set_on_its_screen() {
    let _java = JAVA.lock().unwrap_or_else(|e| e.into_inner());
    let Some(game) = started("a_machines_sides_are_set_on_its_screen") else {
        return;
    };
    let mut world = World::new(game);
    // A grinder beside an electric furnace, as in a_grinder_feeds_an_electric_furnace, but set as a player sets them
    // on Kiln's side panel (D-214): each press is a menu button, which Kiln hands to RebornCore as the payload of its
    // own screen's tab (`SlotSavePayload`, `IoSavePayload`), through its codec and its server receiver.
    let grinder = [0, 64, 0];
    let furnace = [1, 64, 0];
    world.machine(
        grinder,
        "techreborn:grinder[active=false,facing=north]",
        "{}",
    );
    world.machine(
        furnace,
        "techreborn:electric_furnace[active=false,facing=north]",
        "{}",
    );
    world.machine(
        [0, 64, 1],
        "techreborn:basic_solar_panel[active=false]",
        "{}",
    );
    world.machine(
        [1, 64, 1],
        "techreborn:basic_solar_panel[active=false]",
        "{}",
    );
    world.sky.extend([[0, 65, 1], [1, 65, 1]]);
    world.tick();
    // The screen's data carries the configuration: the machine faces north, nothing is set yet.
    let sides = |world: &World, at: [i32; 3]| {
        m::machine_sides(
            world
                .data
                .get(&at)
                .unwrap_or_else(|| panic!("no data at {at:?}: {:?}", world.data)),
        )
        .unwrap_or_else(|| panic!("no sides in {:?}", world.data[&at]))
    };
    let made = sides(&world, grinder);
    println!("grinder's sides as made: {made:?}");
    assert_eq!(made.facing, Some(2));
    assert!(
        made.slots.len() >= 2 && made.slots.iter().all(|&v| v & 0xFFF == 0),
        "{made:?}"
    );
    // The panel's east square is the grinder's left, seen from its front (north).
    assert_eq!(made.face(2), 5);
    // The grinder's output (slot 1) to give out through the east, and to push by itself; the furnace's input (slot
    // 0) to take in through the west.
    world.menu_button(grinder, m::side_button_face(1, 5, 2));
    world.menu_button(grinder, m::side_button_switch(1, 1, true));
    world.menu_button(furnace, m::side_button_face(0, 4, 1));
    // The receiver's work is a task of the server's thread: it runs as its next call begins (a tick).
    world.tick();
    let set = sides(&world, grinder);
    println!("grinder's sides as set: {set:?}");
    assert_eq!(set.mode(1, 5), 2, "{set:?}");
    assert!(set.switch(1, 1) && !set.switch(1, 0), "{set:?}");
    assert_eq!(sides(&world, furnace).mode(0, 4), 1);
    // What RebornCore saved, as its own screen's change would leave it.
    assert!(
        world.saved(grinder).contains("output:1b"),
        "{}",
        world.saved(grinder)
    );
    // The game's hoppers then reach, by each machine's own setting: into the furnace through its west face only its
    // input; out of the grinder from below, nothing (its output gives only to the east).
    let announced = m::block_slots("techreborn:electric_furnace").expect("the furnace's slots");
    let reached = m::sided_slots(&announced, &sides(&world, furnace));
    assert_eq!(reached.faces[4], vec![0]);
    assert!(
        reached.faces[0].is_empty() && reached.faces[1].is_empty(),
        "{reached:?}"
    );
    assert_eq!(reached.inputs, vec![0]);
    assert!(
        m::sided_slots(&m::block_slots("techreborn:grinder").unwrap(), &set)
            .outputs
            .is_empty()
    );
    // And the machines do as set: the grinder's raw iron goes into the furnace.
    let ore = r#"{Slot:0b,count:2,id:"minecraft:iron_ore"}"#;
    let fed = world
        .saved(grinder)
        .replacen("Items:[]", &format!("Items:[{ore}]"), 1)
        .replace("PowerAcceptor:{energy:0L}", "PowerAcceptor:{energy:400L}");
    world
        .blocks
        .get_mut(&grinder)
        .unwrap()
        .entity
        .as_mut()
        .unwrap()
        .1 = fed;
    let mut fed_at = None;
    for tick in 0..1500u64 {
        world.tick();
        if world.saved(furnace).contains(r#"id:"minecraft:raw_iron""#)
            || world.saved(furnace).contains("iron_ingot")
        {
            fed_at = Some(tick);
            break;
        }
    }
    println!("fed at {fed_at:?}");
    assert!(
        fed_at.is_some(),
        "{}\n{}",
        world.saved(grinder),
        world.saved(furnace)
    );
}

#[test]
#[ignore = "needs a JDK, sh tools/build-java.sh and KILN_TECH_REBORN"]
fn a_machine_syncs_to_the_players_who_see_it() {
    let _java = JAVA.lock().unwrap_or_else(|e| e.into_inner());
    let Some(game) = started("a_machine_syncs_to_the_players_who_see_it") else {
        return;
    };
    let mut world = World::new(game);
    // RebornCore sends a machine's saved compound to the players that see it (`PlayerLookup.tracking` of the block
    // entity), at most once a second after it changed: one player beside the machine, one 2,000 blocks away, one in
    // the Nether.
    let player = |uuid: u128, name: &str, dimension: &str, x: f64| m::Online {
        uuid,
        name: name.into(),
        dimension: dimension.into(),
        position: [x, 64.0, 0.5],
        game_mode: 0,
        health: 20.0,
        view_distance: 8,
    };
    world.players = vec![
        player(1, "Near", "minecraft:overworld", 4.5),
        player(2, "Away", "minecraft:overworld", 2000.5),
        player(3, "Nether", "minecraft:the_nether", 4.5),
    ];
    // A storage unit with cobblestone in its input slot: it takes it in, and its stored stack (which the window draws on
    // its front) changed.
    let cobblestone = r#"{Items:[{Slot:0b,count:16,id:"minecraft:cobblestone"}]}"#;
    world.machine(
        [0, 64, 0],
        "techreborn:crude_storage_unit[active=false,facing=north]",
        cobblestone,
    );
    for _ in 0..60 {
        world.tick();
    }
    println!("sent: {:?}; saved: {}", world.sent, world.saved([0, 64, 0]));
    let to = |uuid: u128| -> usize {
        world
            .sent
            .iter()
            .filter(|((p, id), _)| *p == uuid && id == "reborncore:custom_description")
            .map(|(_, n)| n)
            .sum()
    };
    assert!(to(1) > 0, "{:?}", world.sent);
    assert_eq!((to(2), to(3)), (0, 0), "{:?}", world.sent);
}

/// A grinder at `at` with two iron ore, power to grind them and a panel of its own south of it by day, its output
/// (slot 1) pushed east by itself, after its first tick has written its slot settings.
fn grinder_pushing_east(world: &mut World, at: [i32; 3]) {
    let [x, y, z] = at;
    world.machine(at, "techreborn:grinder[active=false,facing=north]", "{}");
    world.machine(
        [x, y, z + 1],
        "techreborn:basic_solar_panel[active=false]",
        "{}",
    );
    world.sky.push([x, y + 1, z + 1]);
}

/// The grinders' slot settings, set after their first tick as their screens would set them, and their ore.
fn grinders_loaded(world: &mut World, grinders: &[[i32; 3]]) {
    let ore = r#"{Slot:0b,count:2,id:"minecraft:iron_ore"}"#;
    for at in grinders {
        let saved = configured(world.saved(*at), 1, 5, 2, false, true)
            .replacen("Items:[]", &format!("Items:[{ore}]"), 1)
            .replace("PowerAcceptor:{energy:0L}", "PowerAcceptor:{energy:400L}");
        world.blocks.get_mut(at).unwrap().entity.as_mut().unwrap().1 = saved;
    }
}

#[test]
#[ignore = "needs a JDK, sh tools/build-java.sh and KILN_TECH_REBORN"]
fn grinders_fill_a_double_chest_and_a_hopper() {
    let _java = JAVA.lock().unwrap_or_else(|e| e.into_inner());
    let Some(game) = started("grinders_fill_a_double_chest_and_a_hopper") else {
        return;
    };
    let mut world = World::new(game);
    // Two grinders pushing their raw iron east by RebornCore's slot settings (StorageUtil.move into
    // ItemStorage.SIDED.find), into the game's own containers, which Kiln's Java reaches as Fabric's fallback does
    // (D-144): one into the left half of a double chest facing north, whose right half (east of it) comes first, as in
    // vanilla's ChestBlock.getContainer; one into a hopper's five slots.
    let to_chest = [0, 64, 0];
    let left = [1, 64, 0];
    let right = [2, 64, 0];
    let to_hopper = [0, 64, 4];
    let hopper = [1, 64, 4];
    grinder_pushing_east(&mut world, to_chest);
    grinder_pushing_east(&mut world, to_hopper);
    world.container(
        left,
        "minecraft:chest[facing=north,type=left,waterlogged=false]",
        27,
        "[]",
    );
    world.container(
        right,
        "minecraft:chest[facing=north,type=right,waterlogged=false]",
        27,
        "[]",
    );
    world.container(
        hopper,
        "minecraft:hopper[enabled=true,facing=down]",
        5,
        "[]",
    );
    world.tick();
    grinders_loaded(&mut world, &[to_chest, to_hopper]);
    for _ in 2..=1000 {
        world.tick();
    }
    println!("left half: {}", world.held(left));
    println!("right half: {}", world.held(right));
    println!("hopper: {}", world.held(hopper));
    println!(
        "asked: {:?}",
        world
            .asked
            .iter()
            .filter(|(k, _)| k.starts_with("container"))
            .collect::<Vec<_>>()
    );
    let four = r#"[{Slot:0b,count:4,id:"minecraft:raw_iron"}]"#;
    assert_eq!(
        world.held(right),
        four,
        "the double chest's first slot is its right half's"
    );
    assert_eq!(world.held(left), "[]");
    assert_eq!(world.held(hopper), four);
    for grinder in [to_chest, to_hopper] {
        assert!(
            !world.saved(grinder).contains("raw_iron"),
            "{}",
            world.saved(grinder)
        );
    }
}

#[test]
#[ignore = "needs a JDK, sh tools/build-java.sh and KILN_TECH_REBORN"]
fn an_electric_furnace_takes_from_a_chest() {
    let _java = JAVA.lock().unwrap_or_else(|e| e.into_inner());
    let Some(game) = started("an_electric_furnace_takes_from_a_chest") else {
        return;
    };
    let mut world = World::new(game);
    // A chest of raw iron west of an electric furnace whose input (slot 0) takes from the west by itself: RebornCore
    // moves the iron out of the game's chest (D-144), and the furnace smelts it on a panel's energy.
    let chest = [0, 64, 0];
    let furnace = [1, 64, 0];
    world.container(
        chest,
        "minecraft:chest[facing=north,type=single,waterlogged=false]",
        27,
        r#"[{Slot:3b,count:2,id:"minecraft:raw_iron"}]"#,
    );
    world.machine(
        furnace,
        "techreborn:electric_furnace[active=false,facing=north]",
        "{}",
    );
    world.machine(
        [1, 64, 1],
        "techreborn:basic_solar_panel[active=false]",
        "{}",
    );
    world.sky.push([1, 65, 1]);
    world.tick();
    let saved = configured(world.saved(furnace), 0, 4, 1, true, false);
    world
        .blocks
        .get_mut(&furnace)
        .unwrap()
        .entity
        .as_mut()
        .unwrap()
        .1 = saved;
    let mut taken_at = None;
    for tick in 2..=1500u64 {
        world.tick();
        if taken_at.is_none() && world.held(chest) == "[]" {
            taken_at = Some(tick);
        }
    }
    let furnace_now = world.saved(furnace).to_owned();
    println!("taken at {taken_at:?}; chest: {}", world.held(chest));
    println!(
        "furnace: {}",
        furnace_now.split(",slotConfig").next().unwrap()
    );
    assert!(
        taken_at.is_some(),
        "the furnace took nothing from the chest: {}",
        world.held(chest)
    );
    assert!(
        furnace_now.contains(r#"{Slot:1b,count:2,id:"minecraft:iron_ingot"}"#),
        "{furnace_now}"
    );
}

impl World {
    /// The industrial blast furnace's casings for one at `at` facing north (its `writeMultiblock`): a 3 by 3 floor
    /// and roof of `casing` behind it, from its own height up, and two rings of it between round a middle of lava.
    /// Each casing has its block entity, which RebornCore's multiblock registry joins into one machine (D-161).
    fn blast_furnace_casings(&mut self, [mx, my, mz]: [i32; 3], casing: &str) {
        let state = format!("techreborn:{casing}_machine_casing");
        for x in mx - 1..=mx + 1 {
            for z in mz + 1..=mz + 3 {
                for y in my..=my + 3 {
                    if (x, z) == (mx, mz + 2) && (y == my + 1 || y == my + 2) {
                        // The hollow middle holds lava (its shape check), and lava over the floor's middle adds 500 heat.
                        self.blocks.insert(
                            [x, y, z],
                            Placed {
                                state: "minecraft:lava".to_owned(),
                                entity: None,
                            },
                        );
                        continue;
                    }
                    self.machine([x, y, z], &state, "{}");
                }
            }
        }
    }
}

#[test]
#[ignore = "needs a JDK, sh tools/build-java.sh and KILN_TECH_REBORN"]
fn the_blast_furnace_heats_by_its_casings() {
    let _java = JAVA.lock().unwrap_or_else(|e| e.into_inner());
    let Some(game) = started("the_blast_furnace_heats_by_its_casings") else {
        return;
    };
    let mut world = World::new(game.clone());
    // Two industrial blast furnaces with glass panes and energy: one in its casings, one without. The heat is the
    // casings' (RebornCore's multiblock registry, a Fabric data attachment on the level, ticked on START_LEVEL_TICK):
    // glass from glass panes needs 1000 (blast_furnace/glass_from_glass_pane: 128 E a tick for 150 ticks).
    let furnace = [0, 64, 0];
    let alone = [20, 64, 0];
    world.blast_furnace_casings(furnace, "advanced");
    let panes =
        r#"{Items:[{Slot:0b,count:10,id:"minecraft:glass_pane"}],PowerAcceptor:{energy:100000L}}"#;
    world.machine(
        furnace,
        "techreborn:industrial_blast_furnace[active=false,facing=north]",
        panes,
    );
    world.machine(
        alone,
        "techreborn:industrial_blast_furnace[active=false,facing=north]",
        panes,
    );
    let casings = world
        .blocks
        .iter()
        .filter(|(p, b)| p[2] > 0 && p[0] < 10 && b.state.contains("casing"))
        .count();
    assert_eq!(casings, 34);
    let mut glass_at = None;
    let mut took = Vec::new();
    for tick in 1..=400u64 {
        let asked_before: usize = world.asked.values().sum();
        let started = Instant::now();
        world.tick();
        took.push((
            started.elapsed(),
            world.asked.values().sum::<usize>() - asked_before,
        ));
        if glass_at.is_none() && world.saved(furnace).contains("minecraft:glass\"") {
            glass_at = Some(tick);
        }
    }
    let saved = world.saved(furnace).to_owned();
    println!("glass at tick {glass_at:?}");
    // What a tick costs once the casings are one machine (the last 100 ticks): the whole tick (two furnaces' calls,
    // the tick events) and the questions Java asked the game in it.
    let last = &took[took.len() - 100..];
    let mean = last.iter().map(|(t, _)| *t).sum::<Duration>() / last.len() as u32;
    let asked = last.iter().map(|(_, n)| n).sum::<usize>() as f64 / last.len() as f64;
    println!("a tick: {mean:?} on average, {asked:.1} questions to the game");
    let log = java_log(&game);
    assert!(!log.contains("MultiblockRegistry"), "{log}");
    assert!(
        glass_at.is_some(),
        "the furnace in its casings made no glass: {saved}"
    );
    assert!(saved.contains(r#"id:"minecraft:glass""#), "{saved}");
    let alone = world.saved(alone);
    assert!(
        alone.contains(r#"{Slot:0b,count:10,id:"minecraft:glass_pane"}"#),
        "{alone}"
    );
    assert!(!alone.contains(r#"id:"minecraft:glass""#), "{alone}");
    // The casings are one machine: each one's block entity says so in what it saves (RebornCore's multiblock data
    // in the one that keeps the machine's).
    let joined = world
        .blocks
        .values()
        .filter(|b| b.state.contains("casing"))
        .count();
    assert_eq!(joined, 34);
}

#[test]
#[ignore = "needs a JDK, sh tools/build-java.sh and KILN_TECH_REBORN"]
fn the_client_entrypoint_starts() {
    let _java = JAVA.lock().unwrap_or_else(|e| e.into_inner());
    let Some(game) = started("the_client_entrypoint_starts") else {
        return;
    };
    // Tech Reborn's client entrypoint registers its item models in `ItemModels.ID_MAPPER` (#213), then its screens,
    // renderers and networking: Kiln says in the chat when every mod's entrypoints ran, and which didn't start.
    let data = game.join("horizon-mods/.horizon-mod-data/kiln");
    let snapshot = m::Snapshot {
        connected: true,
        in_world: true,
        game_time: 10,
        dimension: "minecraft:overworld".into(),
        ..m::Snapshot::default()
    };
    let deadline = Instant::now() + Duration::from_secs(60);
    while !CHAT
        .lock()
        .unwrap()
        .iter()
        .any(|l| l.contains("Java mods running"))
    {
        assert!(
            Instant::now() < deadline,
            "Kiln never said the mods run:\n{}",
            java_log(&game)
        );
        let chat = m::tick(&data, || None, || vec![6; 128], snapshot.clone());
        CHAT.lock().unwrap().extend(chat);
        std::thread::sleep(Duration::from_millis(10));
    }
    let chat = CHAT.lock().unwrap().clone();
    println!("chat: {chat:?}");
    assert!(!chat.iter().any(|l| l.contains("didn't start")), "{chat:?}");
    assert!(
        chat.iter().any(|l| l.contains("3 of 3 Java mods running")),
        "{chat:?}"
    );
    let said = SAID.lock().unwrap().clone();
    assert!(
        said.iter().any(|l| l.contains("started Tech Reborn")),
        "{said:?}"
    );
}

#[test]
#[ignore = "needs a JDK, sh tools/build-java.sh and KILN_TECH_REBORN"]
fn the_rolling_machine_rolls_by_its_own_recipes() {
    let _java = JAVA.lock().unwrap_or_else(|e| e.into_inner());
    let Some(game) = started("the_rolling_machine_rolls_by_its_own_recipes") else {
        return;
    };
    let mut world = World::new(game);
    // Six iron ingots in the top two rows of a rolling machine's grid, a solar panel beside it by day: its own recipe,
    // `techreborn:rolling_machine` (rolling_machine/iron_bars.json: the game's shaped pattern `AAA`/`AAA`, 24 iron bars,
    // 5 E a tick for 250 ticks), read with the game's shaped codec and matched as the game matches a crafting grid
    // (the grid's empty bottom row trimmed off). Its output is slot 9.
    let rolling = [0, 64, 0];
    let panel = [1, 64, 0];
    let ingots: Vec<String> = (0..6)
        .map(|slot| format!(r#"{{Slot:{slot}b,count:1,id:"minecraft:iron_ingot"}}"#))
        .collect();
    world.machine(
        rolling,
        "techreborn:rolling_machine[active=false,facing=north]",
        &format!(
            "{{Items:[{}],PowerAcceptor:{{energy:400L}}}}",
            ingots.join(",")
        ),
    );
    world.machine(panel, "techreborn:basic_solar_panel[active=false]", "{}");
    world.sky.push([1, 65, 0]);
    let mut rolled_at = None;
    let mut active = false;
    for tick in 1..=1500u64 {
        world.tick();
        active |= world.state(rolling).contains("active=true");
        if rolled_at.is_none()
            && world
                .saved(rolling)
                .contains(r#"{Slot:9b,count:24,id:"minecraft:iron_bars"}"#)
        {
            rolled_at = Some(tick);
        }
    }
    let saved = world.saved(rolling).to_owned();
    println!(
        "rolled at {rolled_at:?}: {}",
        saved.split(",Upgrades").next().unwrap()
    );
    // Read at Java's start: all 14 of the rolling machine's, none refused.
    let read = SAID
        .lock()
        .unwrap()
        .iter()
        .find(|line| line.contains("Kiln read "))
        .cloned()
        .expect("Kiln said nothing of the mods' recipes");
    assert!(read.contains("techreborn:rolling_machine=14"), "{read}");
    assert!(!read.contains("rolling_machine refused"), "{read}");
    assert!(rolled_at.is_some(), "{saved}");
    assert!(
        saved.contains(r#"Items:[{Slot:9b,count:24,id:"minecraft:iron_bars"}]"#),
        "{saved}"
    );
    assert!(active, "the rolling machine never showed it was running");
}

/// `saved` with its `Items` list replaced by `items` (vanilla's list, `[...]`).
fn with_items(saved: &str, items: &str) -> String {
    let start = saved.find("Items:[").expect("no Items") + "Items:".len();
    let mut depth = 0;
    let end = saved[start..]
        .char_indices()
        .find(|(_, c)| {
            depth += match c {
                '[' | '{' => 1,
                ']' | '}' => -1,
                _ => 0,
            };
            depth == 0
        })
        .map(|(i, _)| start + i + 1)
        .unwrap();
    format!("{}{items}{}", &saved[..start], &saved[end..])
}

#[test]
#[ignore = "needs a JDK, sh tools/build-java.sh and KILN_TECH_REBORN"]
fn a_cell_empties_into_a_tank_unit_and_fills_again() {
    let _java = JAVA.lock().unwrap_or_else(|e| e.into_inner());
    let Some(game) = started("a_cell_empties_into_a_tank_unit_and_fills_again") else {
        return;
    };
    let mut world = World::new(game);
    // Tech Reborn's cells are items, one per fluid (`techreborn:water_cell`, the empty `techreborn:cell`), each with
    // Fabric's `SingleVariantItemStorage` on `FluidStorage.ITEM`. A tank unit drains a full cell from its input slot
    // into its tank and puts the empty one in its output; given an empty cell, it fills it from the tank.
    let tank = [0, 64, 0];
    world.machine(
        tank,
        "techreborn:basic_tank_unit[facing=north]",
        r#"{Items:[{Slot:0b,count:1,id:"techreborn:water_cell"}]}"#,
    );
    for _ in 0..40 {
        world.tick();
    }
    let drained = world.saved(tank).to_owned();
    println!("drained: {drained}");
    assert!(
        drained.contains(r#"Items:[{Slot:1b,count:1,id:"techreborn:cell"}]"#),
        "{drained}"
    );
    assert!(drained.contains(r#"fluid:"minecraft:water""#), "{drained}");
    // The empty cell back in the input: the tank fills it with its water.
    let refill = with_items(&drained, r#"[{Slot:0b,count:1,id:"techreborn:cell"}]"#);
    world.machine(tank, "techreborn:basic_tank_unit[facing=north]", &refill);
    for _ in 0..40 {
        world.tick();
    }
    let filled = world.saved(tank).to_owned();
    println!("filled: {filled}");
    assert!(
        filled.contains(r#"Items:[{Slot:1b,count:1,id:"techreborn:water_cell"}]"#),
        "{filled}"
    );
    assert!(!filled.contains(r#"fluid:"minecraft:water""#), "{filled}");
    // A cell of one of Tech Reborn's own fluids (RebornCore's, registered by its code): nitrogen into a second tank.
    let other = [2, 64, 0];
    world.machine(
        other,
        "techreborn:basic_tank_unit[facing=north]",
        r#"{Items:[{Slot:0b,count:1,id:"techreborn:nitrogen_cell"}]}"#,
    );
    for _ in 0..40 {
        world.tick();
    }
    let nitrogen = world.saved(other).to_owned();
    println!("nitrogen: {}", nitrogen.split(",Upgrades").next().unwrap());
    assert!(
        nitrogen.contains(r#"Items:[{Slot:1b,count:1,id:"techreborn:cell"}]"#),
        "{nitrogen}"
    );
    assert!(
        nitrogen.contains(r#"fluid:"techreborn:nitrogen""#),
        "{nitrogen}"
    );
}

/// A container's slots as the test keeps them (`[{Slot:0b,count:2,id:"minecraft:raw_iron"},...]`, items with no
/// components): slot, count, item.
fn slot_list(held: &str) -> Vec<(u8, u32, String)> {
    let inner = held.trim_start_matches('[').trim_end_matches(']');
    inner
        .split("},{")
        .filter(|e| !e.is_empty())
        .map(|entry| {
            let entry = entry.trim_matches(|c| c == '{' || c == '}');
            let mut slot = 0;
            let mut count = 0;
            let mut id = String::new();
            for field in entry.split(',') {
                match field.split_once(':') {
                    Some(("Slot", v)) => slot = v.trim_end_matches('b').parse().unwrap(),
                    Some(("count", v)) => count = v.parse().unwrap(),
                    Some(("id", v)) => id = v.trim_matches('"').to_owned(),
                    _ => panic!("a slot of {held}"),
                }
            }
            (slot, count, id)
        })
        .collect()
}

fn slot_text(slots: &[(u8, u32, String)]) -> String {
    let entries: Vec<String> = slots
        .iter()
        .map(|(slot, count, id)| format!(r#"{{Slot:{slot}b,count:{count},id:"{id}"}}"#))
        .collect();
    format!("[{}]", entries.join(","))
}

/// `saved` with a Tech Reborn machine's fluid configuration (`fluidConfig`, RebornCore's `FluidConfiguration`) set as
/// its screen sets it: face `side` (`Direction`'s ordinal: down 0, up 1, north 2, south 3, west 4, east 5) to `io`
/// (`ExtractConfig`'s ordinal: none 0, input 1, output 2, all 3), every other face to none, and its automatic input
/// and output. Saved as `fluidConfig:{input:0b,output:0b,side_0:{config:3,side:0},...}`.
fn fluid_configured(saved: &str, side: usize, io: u8, auto_in: bool, auto_out: bool) -> String {
    let start = saved
        .find("fluidConfig:{")
        .unwrap_or_else(|| panic!("no fluidConfig in {saved}"));
    let mut depth = 0;
    let mut end = start;
    for (i, c) in saved[start..].char_indices() {
        match c {
            '{' => depth += 1,
            '}' => {
                depth -= 1;
                if depth == 0 {
                    end = start + i + 1;
                    break;
                }
            }
            _ => {}
        }
    }
    let flag = |b: bool| if b { "1b" } else { "0b" };
    let sides = (0..6)
        .map(|s| {
            format!(
                "side_{s}:{{config:{},side:{s}}}",
                if s == side { io } else { 0 }
            )
        })
        .collect::<Vec<_>>()
        .join(",");
    let edited = format!(
        "fluidConfig:{{input:{},output:{},{sides}}}",
        flag(auto_in),
        flag(auto_out)
    );
    format!("{}{edited}{}", &saved[..start], &saved[end..])
}

#[test]
#[ignore = "needs a JDK, sh tools/build-java.sh and KILN_TECH_REBORN"]
fn a_grinder_feeds_the_games_furnace_and_a_compressor_empties_it() {
    let _java = JAVA.lock().unwrap_or_else(|e| e.into_inner());
    let Some(game) = started("a_grinder_feeds_the_games_furnace_and_a_compressor_empties_it")
    else {
        return;
    };
    let mut world = World::new(game);
    // The game's own furnace between two Tech Reborn machines (D-199): a grinder above it pushes its raw iron down
    // (RebornCore's slot settings: StorageUtil.move into ItemStorage.SIDED.find), which reaches the furnace's input
    // through its top, as vanilla's AbstractFurnaceBlockEntity.getSlotsForFace(UP) says; a compressor below takes the
    // furnace's result out through its bottom (slot 2, then the fuel's slot for an emptied bucket) as its own input.
    // The test stands in for the game's furnace: each tick it smelts what is in the input into iron ingots in the
    // result.
    let grinder = [0, 66, 0];
    let furnace = [0, 65, 0];
    let compressor = [0, 64, 0];
    world.machine(
        grinder,
        "techreborn:grinder[active=false,facing=north]",
        "{}",
    );
    world.machine(
        [0, 66, 1],
        "techreborn:basic_solar_panel[active=false]",
        "{}",
    );
    world.container(
        furnace,
        "minecraft:furnace[facing=north,lit=false]",
        3,
        "[]",
    );
    world.machine(
        compressor,
        "techreborn:compressor[active=false,facing=north]",
        "{}",
    );
    world.machine(
        [0, 64, 1],
        "techreborn:basic_solar_panel[active=false]",
        "{}",
    );
    world.sky.extend([[0, 67, 1], [0, 65, 1]]);
    world.tick();
    // The grinder's output (slot 1) down by itself, its ore and energy; the compressor's input (slot 0) from above by
    // itself.
    let ore = r#"{Slot:0b,count:2,id:"minecraft:iron_ore"}"#;
    let grinder_saved = configured(world.saved(grinder), 1, 0, 2, false, true)
        .replacen("Items:[]", &format!("Items:[{ore}]"), 1)
        .replace("PowerAcceptor:{energy:0L}", "PowerAcceptor:{energy:400L}");
    let compressor_saved = configured(world.saved(compressor), 0, 1, 1, true, false);
    world
        .blocks
        .get_mut(&grinder)
        .unwrap()
        .entity
        .as_mut()
        .unwrap()
        .1 = grinder_saved;
    world
        .blocks
        .get_mut(&compressor)
        .unwrap()
        .entity
        .as_mut()
        .unwrap()
        .1 = compressor_saved;
    let mut fed = 0;
    let mut smelted = 0;
    for _ in 2..=1500u64 {
        world.tick();
        let mut slots = slot_list(world.held(furnace));
        if let Some(i) = slots
            .iter()
            .position(|(slot, _, id)| *slot == 0 && id == "minecraft:raw_iron")
        {
            let (_, count, _) = slots.remove(i);
            fed += count;
            smelted += count;
            match slots.iter_mut().find(|(slot, _, _)| *slot == 2) {
                Some(result) => result.1 += count,
                None => slots.push((2, count, "minecraft:iron_ingot".to_owned())),
            }
            slots.sort();
            world.containers.get_mut(&furnace).unwrap().1 = slot_text(&slots);
        }
    }
    let compressor_now = world.saved(compressor).to_owned();
    println!("furnace: {}", world.held(furnace));
    println!(
        "compressor: {}",
        compressor_now.split(",slotConfig").next().unwrap()
    );
    println!(
        "asked: {:?}",
        world
            .asked
            .iter()
            .filter(|(k, _)| k.starts_with("container"))
            .collect::<Vec<_>>()
    );
    assert_eq!(
        fed, 4,
        "the grinder's four raw iron went into the furnace's input"
    );
    assert_eq!(smelted, 4);
    assert_eq!(
        world.held(furnace),
        "[]",
        "the compressor took the furnace's result"
    );
    assert!(
        !world.saved(grinder).contains("raw_iron"),
        "{}",
        world.saved(grinder)
    );
    assert!(
        compressor_now.contains("minecraft:iron_ingot")
            || compressor_now.contains("techreborn:iron_plate"),
        "{compressor_now}"
    );
}

#[test]
#[ignore = "needs a JDK, sh tools/build-java.sh and KILN_TECH_REBORN"]
fn the_ore_depths_reach_the_window_in_the_configuration() {
    let _java = JAVA.lock().unwrap_or_else(|e| e.into_inner());
    let Some(game) = started("the_ore_depths_reach_the_window_in_the_configuration") else {
        return;
    };
    let mut world = World::new(game.clone());
    // Tech Reborn's OreDepthSyncHandler sends the ore depths in CONFIGURE, through a packet the configuration's
    // connection sends (D-199); its client's ClientboundPacketHandlers keeps them for the ores' tooltips.
    let joining = m::PlayerSnapshot {
        uuid: 0x00c0_ffee,
        name: "Kiln".into(),
        dimension: "minecraft:overworld".into(),
        position: [0.5, 64.0, 0.5],
        game_mode: 0,
        health: 20.0,
        attributes: vec![("minecraft:max_health".into(), 20.0, 20.0)],
        ..Default::default()
    };
    world.players = vec![m::Online {
        uuid: joining.uuid,
        name: joining.name.clone(),
        dimension: joining.dimension.clone(),
        position: joining.position,
        game_mode: 0,
        health: 20.0,
        view_distance: 8,
    }];
    let world_dir = game.join("world");
    let game_time = world.game_time;
    let mut joined = None;
    for _ in 0..3 {
        joined = m::player_connected(&world_dir, game_time, &joining, &mut |what| {
            world.answer([i32::MIN; 3], what)
        });
        if joined.is_some() {
            break;
        }
        std::thread::sleep(Duration::from_millis(300));
    }
    let joined = joined.unwrap_or_else(|| panic!("the connect call failed:\n{}", java_log(&game)));
    let configuration: Vec<Vec<u8>> = joined
        .effects
        .iter()
        .filter_map(|e| match e {
            m::Effect::Net { data, .. } if data.starts_with(b"\x12kiln:configuration") => {
                Some(data.clone())
            }
            _ => None,
        })
        .collect();
    println!(
        "configuration: {} messages; effects: {}",
        configuration.len(),
        joined.effects.len()
    );
    assert!(
        configuration
            .iter()
            .any(|d| d[19..].windows(11).any(|w| w == b"techreborn:")),
        "no Tech Reborn configuration payload: {:?}",
        joined.effects
    );
    let said = SAID.lock().unwrap().clone();
    assert!(
        !said.iter().any(|l| l.contains("would disconnect")),
        "{said:?}"
    );

    // The window's receiver has them: Kiln says so once a type.
    for message in &configuration {
        m::from_server(message);
    }
    let data = game.join("horizon-mods/.horizon-mod-data/kiln");
    let snapshot = m::Snapshot {
        connected: true,
        in_world: true,
        game_time: 10,
        dimension: "minecraft:overworld".into(),
        ..m::Snapshot::default()
    };
    let deadline = Instant::now() + Duration::from_secs(20);
    while !SAID
        .lock()
        .unwrap()
        .iter()
        .any(|l| l.contains("configuration payload reached its mod"))
    {
        assert!(
            Instant::now() < deadline,
            "the window's receiver never had it:\n{}",
            java_log(&game)
        );
        let chat = m::tick(&data, || None, || vec![6; 128], snapshot.clone());
        CHAT.lock().unwrap().extend(chat);
        std::thread::sleep(Duration::from_millis(10));
    }
    let said = SAID.lock().unwrap().clone();
    assert!(
        !said
            .iter()
            .any(|l| l.contains("configuration payload from the server no mod receives")),
        "{said:?}"
    );
}

#[test]
#[ignore = "needs a JDK, sh tools/build-java.sh and KILN_TECH_REBORN"]
fn a_tank_unit_fills_and_drains_horizons_tank() {
    let _java = JAVA.lock().unwrap_or_else(|e| e.into_inner());
    let Some(game) = started("a_tank_unit_fills_and_drains_horizons_tank") else {
        return;
    };
    // Tech Reborn's liquids stand in the world as blocks of their own, which their buckets pour and the empty bucket
    // takes back (D-198): Java names each liquid block's bucket.
    assert_eq!(
        m::liquid_bucket("techreborn:nitrogen").as_deref(),
        Some("techreborn:nitrogen_bucket")
    );
    assert_eq!(
        m::bucket_liquid("techreborn:oil_bucket").as_deref(),
        Some("techreborn:oil")
    );
    assert_eq!(m::liquid_bucket("techreborn:basic_tank_unit"), None);
    let mut world = World::new(game);
    // A tank unit holding a bucket of water (from a water bucket in its input, as a player puts it), its east side
    // set to push out automatically: RebornCore's fluid configuration moves it by Fabric's FluidStorage.SIDED into the
    // block east of it, Horizon's own tank (a native mod's, or a cauldron), which Kiln reaches through Horizon's API 54.
    let tank = [0, 64, 0];
    let horizon = [1, 64, 0];
    world.machine(
        tank,
        "techreborn:basic_tank_unit[facing=north]",
        r#"{Items:[{Slot:0b,count:1,id:"minecraft:water_bucket"}]}"#,
    );
    // A block of the game's own holds it (Horizon answers for its position; air holds nothing).
    world.blocks.insert(
        horizon,
        Placed {
            state: "minecraft:iron_block".to_owned(),
            entity: None,
        },
    );
    world.tanks.insert(horizon, (String::new(), 0, 4000));
    for _ in 0..40 {
        world.tick();
    }
    let filled = world.saved(tank).to_owned();
    println!("tank unit as made: {filled}");
    assert!(
        filled.contains(r#"Items:[{Slot:1b,count:1,id:"minecraft:bucket"}]"#),
        "{filled}"
    );
    assert!(filled.contains(r#"fluid:"minecraft:water""#), "{filled}");
    assert_eq!(
        world.tanks[&horizon],
        (String::new(), 0, 4000),
        "the tank pushed before it was set to"
    );
    let pushing = fluid_configured(&filled, 5, 2, false, true);
    world.machine(tank, "techreborn:basic_tank_unit[facing=north]", &pushing);
    for _ in 0..200 {
        world.tick();
    }
    let pushed = world.saved(tank).to_owned();
    println!(
        "pushed: {pushed}\nHorizon's tank: {:?}",
        world.tanks[&horizon]
    );
    assert_eq!(
        world.tanks[&horizon],
        ("minecraft:water".to_owned(), 1000, 4000),
        "{pushed}"
    );
    assert!(!pushed.contains(r#"fluid:"minecraft:water""#), "{pushed}");
    // And back: the east side pulls automatically, and the tank unit drinks Horizon's tank dry.
    let pulling = fluid_configured(&pushed, 5, 1, true, false);
    world.machine(tank, "techreborn:basic_tank_unit[facing=north]", &pulling);
    world
        .tanks
        .insert(horizon, ("minecraft:lava".to_owned(), 2000, 4000));
    for _ in 0..200 {
        world.tick();
    }
    let pulled = world.saved(tank).to_owned();
    println!(
        "pulled: {pulled}\nHorizon's tank: {:?}",
        world.tanks[&horizon]
    );
    assert_eq!(world.tanks[&horizon], (String::new(), 0, 4000), "{pulled}");
    assert!(pulled.contains(r#"fluid:"minecraft:lava""#), "{pulled}");
    // Kiln asked Horizon only for the block beside the tank unit's configured side.
    assert!(
        world
            .asked
            .keys()
            .any(|k| k.starts_with("fluid_tanks 1 64 0 west")),
        "{:?}",
        world.asked
    );
}

/// Tech Reborn's loot in the game's chests (RebornCore's `ModLoot`, by Fabric's `LootTableEvents.MODIFY` with
/// `LootTable.Builder.pool` and `modifyPools`, #276): its change runs without a failure, and a dungeon chest gets its
/// pool of ingots, a frame and parts, each with its count.
#[test]
#[ignore = "needs a JDK, sh tools/build-java.sh and KILN_TECH_REBORN"]
fn its_loot_goes_into_the_games_chests() {
    use horizon_kiln_mod::game::kiln_loot as loot;
    let Some(jar) = std::env::var_os("KILN_TECH_REBORN") else {
        println!("KILN_TECH_REBORN names no jar: nothing to run");
        return;
    };
    let (game, _) = game_folder(Path::new(&jar), "loot");
    let kiln = game.join("kiln");
    assert!(
        loot::uses_loot_events(Path::new(&jar)),
        "Kiln doesn't see its loot change"
    );
    loot::prepare(&kiln);
    let file = kiln.join("generated").join(loot::FILE);
    let text = std::fs::read_to_string(&file)
        .unwrap_or_else(|e| panic!("its loot change didn't run ({e}): {}", file.display()));
    let java = std::fs::read_to_string(kiln.join("java-loot.log")).unwrap_or_default();
    println!("== java-loot.log\n{java}");
    // Kiln's own word (`kiln/Loot.java`); RebornCore's client mixins that miss their targets say "failed" too.
    assert!(
        !java.contains("change to loot table"),
        "its loot change failed: {java}"
    );
    assert!(!java.contains("can't write yet"), "{java}");
    let loaded: serde_json::Value = serde_json::from_str(&text).unwrap();
    let dungeon = &loaded["tables"]["minecraft:chests/simple_dungeon"];
    println!("== simple_dungeon\n{dungeon:#}");
    let entries = dungeon[0]["entries"]
        .as_array()
        .expect("no pool in the dungeon's chest");
    assert!(
        entries.iter().any(|e| e["name"] == "techreborn:tin_ingot"
            && e["functions"][0]["function"] == "minecraft:set_count"),
        "{dungeon}"
    );
    assert_eq!(
        dungeon[0]["rolls"]["type"], "minecraft:uniform",
        "{dungeon}"
    );
    // The nether's chests too, and the end's.
    assert!(
        loaded["tables"]["minecraft:chests/nether_bridge"].is_array(),
        "{text}"
    );
}
