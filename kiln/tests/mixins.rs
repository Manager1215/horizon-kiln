// Kiln's Mixin with real Java: the proof mod's mixins (kiln/proof-mods/mixins), every kind of Mixin and MixinExtras
// (accessors, invokers, overwrites, every injector at the whole method and inside it, wrappers, initializers, a
// config's plugin), on Kiln's stand-ins and on the mod's own class, applied by the Mixin library through Kiln's agent
// (kiln/java/kiln/Mixins.java, D-089) as the classes load, and its injections into the game's methods mods inject into
// most, run where the game runs them as the game plays.
// Needs a JDK and the jars from `sh tools/build-java.sh`.
//
//   sh tools/build-java.sh && cargo test -p horizon-kiln-mod --test mixins -- --ignored --nocapture

use std::path::{Path, PathBuf};
use std::sync::Mutex;
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;

/// What Kiln logs (its Mixin's lines among them), kept for the test's checks.
struct Kept;

static KEPT: Mutex<Vec<String>> = Mutex::new(Vec::new());

impl log::Log for Kept {
    fn enabled(&self, _: &log::Metadata) -> bool {
        true
    }

    fn log(&self, record: &log::Record) {
        if record.level() <= log::Level::Info {
            KEPT.lock().unwrap().push(record.args().to_string());
        }
    }

    fn flush(&self) {}
}

/// A game folder with Kiln, the libraries and the proof mod; returns Kiln's folder and the mod's data folder.
fn game_folder() -> (PathBuf, PathBuf) {
    let built = Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln");
    let proof = built.join("proof/kiln-proof-mixins.jar");
    assert!(proof.is_file(), "run sh tools/build-java.sh first");
    let game = std::env::temp_dir().join(format!("kiln-mixins-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&game);
    let kiln = game.join("kiln");
    std::fs::create_dir_all(kiln.join("mods")).unwrap();
    std::fs::create_dir_all(kiln.join("lib")).unwrap();
    std::fs::copy(
        built.join("kiln-runtime.jar"),
        kiln.join("kiln-runtime.jar"),
    )
    .unwrap();
    for jar in m::jars(&built.join("lib")) {
        std::fs::copy(&jar, kiln.join("lib").join(jar.file_name().unwrap())).unwrap();
    }
    std::fs::copy(&proof, kiln.join("mods/kiln-proof-mixins.jar")).unwrap();
    let data = game.join("horizon-mods/.horizon-mod-data/kiln");
    std::fs::create_dir_all(&data).unwrap();
    (kiln, data)
}

/// The window's game in a world with its player, `screen` open (the game's name for it: `playing` for none).
fn in_world(screen: &str, paused: bool) -> m::Snapshot {
    m::Snapshot {
        connected: true,
        in_world: true,
        paused,
        game_time: 10,
        dimension: "minecraft:overworld".into(),
        advancements: None,
        player: Some(m::PlayerSnapshot {
            uuid: 0x00c0_ffee,
            name: "Kiln".into(),
            dimension: "minecraft:overworld".into(),
            position: [0.5, 70.0, 0.5],
            game_mode: 0,
            health: 20.0,
            main: None,
            off: None,
            attributes: Vec::new(),
            ..Default::default()
        }),
        state: m::WindowState {
            // A single-player world: its server loads it as it is joined (MinecraftServer.loadLevel, D-093).
            single_player: true,
            screen: screen.into(),
            ..m::WindowState::default()
        },
        ..m::Snapshot::default()
    }
}

#[test]
#[ignore = "needs a JDK and sh tools/build-java.sh"]
fn mixins_apply_and_fire_where_the_game_runs_them() {
    let _ = log::set_logger(&Kept).map(|()| log::set_max_level(log::LevelFilter::Info));
    let (kiln, data) = game_folder();
    let tick = |snapshot: m::Snapshot| m::tick(&data, || None, || vec![6; 128], snapshot);
    let deadline = Instant::now() + Duration::from_secs(20);
    let mut chat = Vec::new();
    // Into a world: Java starts and the mod with it; the world is joined with the first tick.
    while !chat
        .iter()
        .any(|l: &String| l.contains("Java mods running"))
    {
        assert!(
            Instant::now() < deadline,
            "Kiln never said the mods run: {chat:?}"
        );
        chat.extend(tick(in_world("playing", false)));
        std::thread::sleep(Duration::from_millis(10));
    }
    // The proof mod stops with what went wrong when a mixin isn't as Mixin makes it.
    assert!(
        chat.iter().any(|l| l.contains("1 of 1 Java mods running")),
        "{chat:?}"
    );
    // Then the game plays: a few ticks, the pause screen open (the game paused), closed, then the world left. Java
    // takes the ticks in order.
    for _ in 0..3 {
        tick(in_world("playing", false));
    }
    for _ in 0..3 {
        tick(in_world("paused", true));
    }
    for _ in 0..3 {
        tick(in_world("playing", false));
    }
    let left = m::Snapshot {
        connected: false,
        in_world: false,
        ..m::Snapshot::default()
    };
    let all = "Kiln's Mixin: 83 of 83 checks of accessors, invokers, injectors, overwrites and initializers passed";
    let read = || std::fs::read_to_string(kiln.join("java.log")).unwrap_or_default();
    let deadline = Instant::now() + Duration::from_secs(20);
    while !read().contains("checks of accessors, invokers, injectors, overwrites and initializers passed")
        && !read().contains("checks failed")
        && Instant::now() < deadline
    {
        tick(left.clone());
        std::thread::sleep(m::TICK);
    }
    // Java's log lines reach Kiln with a later tick's replies: when Java takes the ticks faster than they come, the
    // last ones (the handler that throws among them) are still on their way. A few more ticks out of the world, so
    // the test sees them.
    let drained = Instant::now() + Duration::from_secs(5);
    while !KEPT.lock().unwrap().iter().any(|l| l.contains(" threw ")) && Instant::now() < drained {
        tick(left.clone());
        std::thread::sleep(m::TICK);
    }
    let log = read();
    println!("{log}");
    // Kiln's log says which mixins each class got as it loaded (the proof mod's on the game's classes), and once that
    // a handler threw, the one that throws each tick.
    let kept = KEPT.lock().unwrap().join("\n");
    println!("{kept}");
    assert!(
        log.contains("Kiln's Mixin: 74 checks at start passed"),
        "{log}"
    );
    assert!(log.contains(all), "{log}");
    for (mixins, class) in [
        (
            "MinecraftMixin, MinecraftMomentsMixin, twin.MinecraftMixin",
            "net.minecraft.client.Minecraft",
        ),
        ("PlayerMixin", "net.minecraft.world.entity.player.Player"),
        ("GuiMixin", "net.minecraft.client.gui.Gui"),
        // In priority order (GadgetPriorityMixin's 1100 last), then as listed; NarrowOverwriteMixin left out.
        (
            "GadgetOverwriteMixin, GadgetSamePriorityMixin, GadgetValueMixin, GadgetWrapMixin, GadgetWrapOuterMixin, \
             GadgetFieldsMixin, GadgetConformMixin, GadgetPriorityMixin",
            "kiln.proof.mixins.Gadget",
        ),
        // MissingCallMixin left out, the class's other mixins kept.
        (
            "SlotMixin, RedirectMixin, MissingShadowMixin, SlotWholeMixin, MissingOverwriteMixin, AddedMethodMixin",
            "net.minecraft.world.inventory.Slot",
        ),
        ("HudMixin", "net.minecraft.client.gui.Hud"),
        ("ServerMixin", "net.minecraft.server.MinecraftServer"),
        ("LocalPlayerMixin", "net.minecraft.client.player.LocalPlayer"),
        ("OptionsMixin", "net.minecraft.client.Options"),
        ("EntityTickMixin", "net.minecraft.world.entity.Entity"),
        ("LivingEntityTickMixin", "net.minecraft.world.entity.LivingEntity"),
        // The plugin's RefusedMixin never comes.
        (
            "MachineInjectMixin, MachineRedirectMixin, MachineExtrasMixin, KeptMixin",
            "kiln.proof.mixins.Machine",
        ),
        ("ScreenMixin", "net.minecraft.client.gui.screens.Screen"),
    ] {
        assert!(
            kept.contains(&format!("Kiln's Mixin applies kiln_proof_mixins's {mixins} to {class}\n"))
                || kept.ends_with(&format!("Kiln's Mixin applies kiln_proof_mixins's {mixins} to {class}")),
            "{class}: {kept}"
        );
    }
    // Mixin's own rules, by its library: an @Overwrite that loses at the same priority is skipped, its mixin's rest
    // applied; a mixin whose @Overwrite is less visible than its target, or whose @Inject finds no call it requires,
    // is left out. Kiln adds what a mixin's @Shadow or @Accessor names and its own stand-in hasn't, and the methods
    // an @Overwrite or an injector at the method's start or end names (D-093), one the stand-in inherits calling it.
    for said in [
        "Mixin: Method overwrite conflict for twice in kiln_proof_mixins.mixins.json:GadgetSamePriorityMixin from mod \
         kiln_proof_mixins, previously written by kiln.proof.mixins.mixin.GadgetOverwriteMixin. Skipping method.",
        "Kiln's Mixin leaves out kiln_proof_mixins's kiln.proof.mixins.mixin.NarrowOverwriteMixin on \
         kiln.proof.mixins.Gadget: PROTECTED @Overwrite method size in",
        "Kiln's Mixin leaves out kiln_proof_mixins's kiln.proof.mixins.mixin.MissingCallMixin on \
         net.minecraft.world.inventory.Slot: Critical injection failure:",
        "Kiln's Mixin adds to Kiln's net.minecraft.world.inventory.Slot what the mods' mixins name and it hasn't: ",
        "noSuchField (a field, for kiln_proof_mixins's kiln.proof.mixins.mixin.MissingShadowMixin)",
        "getNoSuchThing()I (a method, for kiln_proof_mixins's kiln.proof.mixins.mixin.MissingOverwriteMixin)",
        "getAddedCount()I (a method, for kiln_proof_mixins's kiln.proof.mixins.mixin.AddedMethodMixin)",
        "Kiln's Mixin adds to Kiln's net.minecraft.client.player.LocalPlayer what the mods' mixins name and it hasn't: \
         getInventory()Lnet/minecraft/world/entity/player/Inventory; (calling net.minecraft.world.entity.player.Player's, \
         for kiln_proof_mixins's kiln.proof.mixins.mixin.LocalPlayerMixin)",
        "Kiln's Mixin adds to Kiln's net.minecraft.client.KeyMapping what the mods' mixins name and it hasn't: \
         noSuchField (a field, for kiln_proof_mixins's kiln.proof.mixins.mixin.KeyMappingAccessor)",
    ] {
        assert!(kept.contains(said), "{said}: {kept}");
    }
    let threw: Vec<&str> = kept.lines().filter(|l| l.contains(" threw ")).collect();
    assert_eq!(threw.len(), 1, "{kept}");
    assert!(
        threw[0].contains("kiln_proof_mixins's kiln.proof.mixins.mixin.MinecraftMomentsMixin.kiln$throws threw \
                           java.lang.IllegalStateException"),
        "{kept}"
    );
}
