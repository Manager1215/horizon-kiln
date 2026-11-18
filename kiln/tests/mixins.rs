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
    // Java says which of Horizon's hooks to hand it as the mod loads, after it said the mod runs.
    let deadline = Instant::now() + Duration::from_secs(20);
    while !m::bridged("mouse_scrolling") && Instant::now() < deadline {
        tick(in_world("playing", false));
        std::thread::sleep(m::TICK);
    }
    bridge_hooks(&kiln);
    // What the mods change goes back to Horizon (D-105): since the wheel by 7.5 the proof mod has set the field of view
    // option to 30, its mixin in Camera.calculateFov halves the view, and its mixin in Player.getFlyingSpeed gives
    // 0.2 while the player flies.
    let mut flying = in_world("playing", false);
    flying.state.flying = true;
    let mut back = std::collections::BTreeMap::new();
    let deadline = Instant::now() + Duration::from_secs(20);
    while !(back.contains_key("fov") && back.contains_key("flying_speed"))
        && Instant::now() < deadline
    {
        tick(flying.clone());
        back.extend(m::take_back());
        std::thread::sleep(m::TICK);
    }
    assert_eq!(back.get("fov").map(String::as_str), Some("30"), "{back:?}");
    assert_eq!(
        back.get("flying_speed").map(String::as_str),
        Some("0.2"),
        "{back:?}"
    );
    assert_eq!(
        m::view_fov(70.0, 30.0),
        35.0,
        "the view halved over the option"
    );
    // The proof mod's mixin cancels Hud.extractCrosshair at its head: Horizon hides its crosshair, and only it (D-145).
    let deadline = Instant::now() + Duration::from_secs(20);
    while !m::hud_hidden("minecraft:crosshair") && Instant::now() < deadline {
        tick(in_world("playing", false));
        m::take_back();
        std::thread::sleep(m::TICK);
    }
    assert!(
        m::hud_hidden("minecraft:crosshair"),
        "the crosshair a mixin cancelled isn't hidden"
    );
    for layer in [
        "minecraft:hotbar",
        "minecraft:player_health",
        "minecraft:chat",
        "minecraft:effects",
    ] {
        assert!(!m::hud_hidden(layer), "{layer} hidden");
    }
    // An item's tooltip (D-145): the window asks, Java makes it with the next command, and its lines past the name
    // come back with a tick's replies: the proof mod's mixin at getTooltipLines' return adds one.
    let stick = r#"{count:3,id:"minecraft:stick"}"#;
    assert!(
        m::bridged("tooltip"),
        "the tooltip row runs whenever a Java mod is in"
    );
    let deadline = Instant::now() + Duration::from_secs(20);
    let mut made = None;
    while made.is_none() && Instant::now() < deadline {
        let (answer, ask) = m::tooltip(stick);
        if ask {
            m::hook("tooltip", &[stick.to_owned()]);
        }
        made = answer;
        tick(in_world("playing", false));
        std::thread::sleep(m::TICK);
    }
    let made = made.expect("Java never made the stick's tooltip");
    assert!(made.name.is_none(), "{made:?}");
    assert_eq!(made.lines.len(), 1, "{made:?}");
    assert!(
        made.lines[0].to_string().contains("Kiln bridge tooltip 3"),
        "{made:?}"
    );
    // A message come to the window's chat goes through ChatComponent.addMessage (D-106).
    let mut said = in_world("playing", false);
    said.state.messages = vec![(false, "kiln bridge chat hello".into())];
    tick(said);
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
    let all = "Kiln's Mixin: 88 of 88 checks of accessors, invokers, injectors, overwrites and initializers passed";
    let read = || std::fs::read_to_string(kiln.join("java.log")).unwrap_or_default();
    let deadline = Instant::now() + Duration::from_secs(20);
    while !read()
        .contains("checks of accessors, invokers, injectors, overwrites and initializers passed")
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
        log.contains("Kiln's Mixin: 78 checks at start passed"),
        "{log}"
    );
    assert!(log.contains(all), "{log}");
    assert!(
        log.contains("Kiln's Mixin bridge: MinecraftServer.tickServer ran"),
        "{log}"
    );
    for (mixins, class) in [
        (
            "MinecraftMixin, MinecraftMomentsMixin, MinecraftInitMixin, twin.MinecraftMixin, BridgeUseMixin",
            "net.minecraft.client.Minecraft",
        ),
        (
            "PlayerMixin, BridgeFlyMixin",
            "net.minecraft.world.entity.player.Player",
        ),
        ("BridgeZoomMixin", "net.minecraft.client.Camera"),
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
        (
            "ServerMixin, BridgeServerTickMixin",
            "net.minecraft.server.MinecraftServer",
        ),
        (
            "LocalPlayerMixin",
            "net.minecraft.client.player.LocalPlayer",
        ),
        ("OptionsMixin", "net.minecraft.client.Options"),
        ("EntityTickMixin", "net.minecraft.world.entity.Entity"),
        (
            "LivingEntityTickMixin, BridgeHurtMixin, BridgeMobTickMixin",
            "net.minecraft.world.entity.LivingEntity",
        ),
        ("BridgeTooltipMixin", "net.minecraft.world.item.ItemStack"),
        // Loaded by bridge_hooks' calls, which come first.
        (
            "BridgePickupMixin",
            "net.minecraft.world.entity.item.ItemEntity",
        ),
        ("BridgeOrbMixin", "net.minecraft.world.entity.ExperienceOrb"),
        (
            "BridgeAnvilMixin",
            "net.minecraft.world.inventory.AnvilMenu",
        ),
        ("BridgeBlastMixin", "net.minecraft.world.level.block.Block"),
        (
            "BridgeDestroyMixin",
            "net.minecraft.server.level.ServerPlayerGameMode",
        ),
        // Kiln's Mixin bridge's (D-102).
        ("BridgeInputMixin", "net.minecraft.client.MouseHandler"),
        ("BridgeKeyMixin", "net.minecraft.client.KeyboardHandler"),
        (
            "BridgeServerMixin, BridgeAttackMixin",
            "net.minecraft.server.level.ServerPlayer",
        ),
        (
            "BridgeChatMixin",
            "net.minecraft.client.gui.components.ChatComponent",
        ),
        (
            "BridgeLoginMixin",
            "net.minecraft.client.multiplayer.ClientPacketListener",
        ),
        ("BridgeJoinMixin", "net.minecraft.server.players.PlayerList"),
        // The plugin's RefusedMixin never comes.
        (
            "MachineInjectMixin, MachineRedirectMixin, MachineExtrasMixin, KeptMixin",
            "kiln.proof.mixins.Machine",
        ),
        ("ScreenMixin", "net.minecraft.client.gui.screens.Screen"),
    ] {
        assert!(
            kept.contains(&format!(
                "Kiln's Mixin applies kiln_proof_mixins's {mixins} to {class}\n"
            )) || kept.ends_with(&format!(
                "Kiln's Mixin applies kiln_proof_mixins's {mixins} to {class}"
            )),
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
    // Kiln's Mixin bridge (D-102): the window's hooks ran their methods' mixins in the order they fired, and Kiln's log
    // says which methods it runs at which hook for which mixins.
    assert!(
        log.contains("Kiln's Mixin bridge: the window's hooks ran their methods' mixins in order"),
        "{log}"
    );
    assert!(
        log.contains(
            "Kiln's Mixin bridge: chat addMessage kiln bridge chat hello, the chat's messages kept"
        ),
        "{log}"
    );
    for said in [
        "Kiln's Mixin bridge runs MouseHandler.onScroll at Horizon's mouse_scrolling hook for kiln_proof_mixins's \
         BridgeInputMixin\n",
        "Kiln's Mixin bridge runs LivingEntity.hurtServer at Horizon's living_incoming_damage hook for \
         kiln_proof_mixins's BridgeHurtMixin\n",
        "Kiln's Mixin bridge runs ServerPlayer.tick at Horizon's player_tick hook for kiln_proof_mixins's \
         BridgeMobTickMixin, kiln_proof_mixins's BridgeServerMixin, kiln_proof_mixins's EntityTickMixin, \
         kiln_proof_mixins's LivingEntityTickMixin, kiln_proof_mixins's PlayerMixin\n",
        // A batch's receiver is Kiln's living entity itself: the player's mixins below LivingEntity aren't its (D-145).
        "Kiln's Mixin bridge runs LivingEntity.tick at Horizon's entity_tick_pre hook for kiln_proof_mixins's \
         BridgeMobTickMixin, kiln_proof_mixins's EntityTickMixin, kiln_proof_mixins's LivingEntityTickMixin\n",
        "Kiln's Mixin bridge runs PlayerList.placeNewPlayer at connect for kiln_proof_mixins's BridgeJoinMixin\n",
    ] {
        assert!(kept.contains(said), "{said}: {kept}");
    }
    let threw: Vec<&str> = kept.lines().filter(|l| l.contains(" threw ")).collect();
    assert_eq!(threw.len(), 1, "{kept}");
    assert!(
        threw[0].contains(
            "kiln_proof_mixins's kiln.proof.mixins.mixin.MinecraftMomentsMixin.kiln$throws threw \
                           java.lang.IllegalStateException"
        ),
        "{kept}"
    );
}

/**
 * Kiln's Mixin bridge (D-102): Horizon's hooks, handed to Java because the proof mod's mixins go into the game's methods
 * there, run those methods with the mixins in them. On the window: the wheel, a key down and up, a mouse button, the
 * attack and use keys (the use once, for the main hand; pick block has no row), then the wheel by 7.5, after which the
 * mod checks their order. On the server, which waits: a mob's fall cancelled at `hurtServer`'s head, another hit
 * halved by a mixin that changes the amount, a player's hit as it was; the player's server tick.
 */
fn bridge_hooks(kiln: &Path) {
    for hook in [
        "mouse_scrolling",
        "mouse_button_pre",
        "key",
        "interaction_key",
        "living_incoming_damage",
        "player_tick",
        "attack_entity",
        "mob_killed",
        "player_died",
        "entity_tick_pre",
        "item_picked_up",
        "experience_orb_pickup",
        "prepare_anvil",
        "explosion_detonate",
    ] {
        assert!(
            m::bridged(hook),
            "{hook} isn't handed to Java: {}",
            KEPT.lock().unwrap().join("\n")
        );
    }
    assert!(!m::bridged("tick"), "a hook no row of the table is for");
    let strings = |s: &[&str]| s.iter().map(|f| (*f).to_owned()).collect::<Vec<_>>();
    m::hook("mouse_scrolling", &strings(&["0", "1"]));
    m::hook("key", &strings(&["key.keyboard.j", "1"]));
    m::hook("key", &strings(&["key.keyboard.j", "0"]));
    m::hook("mouse_button_pre", &strings(&["1", "1", "0"]));
    m::hook("interaction_key", &strings(&["0", "key.attack", "main"]));
    m::hook("interaction_key", &strings(&["1", "key.use", "main"]));
    m::hook("interaction_key", &strings(&["1", "key.use", "off"]));
    m::hook("interaction_key", &strings(&["2", "key.pickItem", "main"]));
    m::hook("mouse_scrolling", &strings(&["0", "7.5"]));

    let world = kiln.parent().unwrap().join("world");
    std::fs::create_dir_all(&world).unwrap();
    let mut nothing = |_: &[String]| String::new();
    let outs = |outcome: &m::Outcome| {
        outcome
            .effects
            .iter()
            .filter_map(|e| match e {
                m::Effect::Out { name, value } => Some(format!("{name} {value}")),
                _ => None,
            })
            .collect::<Vec<_>>()
    };
    let fall = m::bridge(
        &world,
        11,
        "living_incoming_damage",
        &strings(&["7", "minecraft:zombie", "minecraft:fall", "0", "0", "4"]),
        None,
        &mut nothing,
    )
    .expect("the hit is Java's to answer");
    assert_eq!(outs(&fall), ["canceled 1"], "{fall:?}");
    let cactus = m::bridge(
        &world,
        12,
        "living_incoming_damage",
        &strings(&["7", "minecraft:zombie", "minecraft:cactus", "0", "0", "4"]),
        None,
        &mut nothing,
    )
    .expect("the hit is Java's to answer");
    assert_eq!(outs(&cactus), ["canceled 0", "amount 2.0"], "{cactus:?}");
    let player = m::PlayerSnapshot {
        uuid: 0x00c0_ffee,
        name: "Kiln".into(),
        dimension: "minecraft:overworld".into(),
        position: [0.5, 70.0, 0.5],
        health: 20.0,
        ..Default::default()
    };
    let hit = m::bridge(
        &world,
        13,
        "living_incoming_damage",
        &strings(&[
            "1",
            "minecraft:player",
            "minecraft:mob_attack",
            "7",
            "7",
            "3",
        ]),
        Some(&player),
        &mut nothing,
    )
    .expect("the hit is Java's to answer");
    assert_eq!(outs(&hit), ["canceled 0", "amount 3.0"], "{hit:?}");
    let told = |outcome: &m::Outcome, text: &str| {
        outcome
            .effects
            .iter()
            .any(|e| matches!(e, m::Effect::Tell(json) if json.to_string().contains(text)))
    };
    assert!(told(&hit, "hurtServer mob_attack 3.0 by 7"), "{hit:?}");
    let ticked = m::bridge(&world, 14, "player_tick", &[], Some(&player), &mut nothing)
        .expect("the tick is Java's");
    assert!(
        told(&ticked, "ServerPlayer.tick on the server"),
        "{ticked:?}"
    );
    // Kiln's own calls for a mixin there: a player joining is told by the mixin in PlayerList.placeNewPlayer.
    assert!(
        m::hears("connect"),
        "Kiln calls for a player's connection when a mixin is in placeNewPlayer"
    );
    let joined =
        m::player_connected(&world, 15, &player, &mut nothing).expect("the connection is Java's");
    assert!(told(&joined, "placeNewPlayer Kiln"), "{joined:?}");
    // D-106: a blow on entity 99 cancelled at Player.attack's head, another not; a mob the player killed and the
    // player's own death told, with their causes.
    let blow = m::bridge(
        &world,
        16,
        "attack_entity",
        &strings(&["99", "minecraft:zombie"]),
        Some(&player),
        &mut nothing,
    )
    .expect("the blow is Java's");
    assert_eq!(outs(&blow), ["canceled 1"], "{blow:?}");
    let blow = m::bridge(
        &world,
        17,
        "attack_entity",
        &strings(&["98", "minecraft:cow"]),
        Some(&player),
        &mut nothing,
    )
    .expect("the blow is Java's");
    assert_eq!(outs(&blow), ["canceled 0"], "{blow:?}");
    let uuid = m::uuid_text(player.uuid);
    let killed = m::bridge(
        &world,
        18,
        "mob_killed",
        &[uuid.clone(), "7".into(), "minecraft:zombie".into()],
        Some(&player),
        &mut nothing,
    )
    .expect("the kill is Java's");
    assert!(told(&killed, "killed 7 with player_attack"), "{killed:?}");
    let died = m::bridge(
        &world,
        19,
        "player_died",
        &[uuid.clone(), "death.attack.inFire".into()],
        Some(&player),
        &mut nothing,
    )
    .expect("the death is Java's");
    assert!(told(&died, "died of in_fire"), "{died:?}");
    // D-145: a level's mobs ticked in one call after its tick, LivingEntity.tick for each: the proof mod's mixin there
    // plays a sound at x = each one's id.
    let ticks = m::bridge(
        &world,
        20,
        "entity_tick_pre",
        &strings(&[
            "minecraft:overworld",
            "42",
            "minecraft:zombie",
            "43",
            "minecraft:cow",
        ]),
        None,
        &mut nothing,
    )
    .expect("the mobs' ticks are Java's");
    let at: Vec<f64> = ticks
        .effects
        .iter()
        .filter_map(|e| match e {
            m::Effect::Sound { id, at, .. } if id == "minecraft:block.anvil.use" => Some(at[0]),
            _ => None,
        })
        .collect();
    assert_eq!(at, [42.0, 43.0], "{ticks:?}");
    // D-200: pickups at Horizon's hooks, ItemEntity.playerTouch and ExperienceOrb.playerTouch on stand-ins with the
    // item or the orb: the proof mod leaves a stick on the ground and an orb worth more than 10.
    let stick = m::bridge(
        &world,
        21,
        "item_picked_up",
        &[uuid.clone(), "minecraft:stick".into(), "3".into()],
        Some(&player),
        &mut nothing,
    )
    .expect("the pickup is Java's");
    assert_eq!(outs(&stick), ["canceled 1"], "{stick:?}");
    assert!(told(&stick, "touched minecraft:stick x3"), "{stick:?}");
    // D-213: vanilla's calls in the body, where mods inject (pickup-notifications at awardStat and take); a pickup
    // cancelled at the head never reaches them.
    assert!(!told(&stick, "picked up minecraft:stick"), "{stick:?}");
    let apple = m::bridge(
        &world,
        22,
        "item_picked_up",
        &[uuid.clone(), "minecraft:apple".into(), "1".into()],
        Some(&player),
        &mut nothing,
    )
    .expect("the pickup is Java's");
    assert_eq!(outs(&apple), ["canceled 0"], "{apple:?}");
    assert!(told(&apple, "picked up minecraft:apple x1"), "{apple:?}");
    let orb = |call: u64, value: &str| {
        let fields = [
            uuid.clone(),
            "minecraft:overworld".into(),
            "5".into(),
            value.to_owned(),
            "1".into(),
        ];
        m::bridge(
            &world,
            call,
            "experience_orb_pickup",
            &fields,
            Some(&player),
            &mut |_: &[String]| String::new(),
        )
        .expect("the orb is Java's")
    };
    let (big, small) = (orb(23, "20"), orb(24, "3"));
    assert_eq!(outs(&big), ["canceled 1"]);
    assert!(!told(&big, "took an orb"), "{big:?}");
    assert_eq!(outs(&small), ["canceled 0"]);
    assert!(told(&small, "took an orb worth 3"), "{small:?}");
    // D-202: an anvil's result at Horizon's prepare_anvil, AnvilMenu.createResult on Kiln's menu with the inputs and
    // Horizon's result and cost. A stick with a prior work of 45 renamed "Kiln": the game caps a rename's cost at 39,
    // the proof mod's mixin moves the game's 40 out of reach, so the cost is 45 + 1 and Horizon takes it.
    let anvil = |call: u64, left: &str, right: &str, result: &str, cost: &str| {
        let fields = [
            uuid.clone(),
            left.to_owned(),
            right.to_owned(),
            result.to_owned(),
            cost.to_owned(),
        ];
        outs(
            &m::bridge(
                &world,
                call,
                "prepare_anvil",
                &fields,
                Some(&player),
                &mut |_: &[String]| String::new(),
            )
            .expect("the anvil is Java's"),
        )
    };
    let named = r#"{id:"minecraft:stick",count:1,components:{"minecraft:custom_name":"Kiln","minecraft:repair_cost":45}}"#;
    let left = r#"{id:"minecraft:stick",count:1,components:{"minecraft:repair_cost":45}}"#;
    let renamed = anvil(26, left, "", named, "39");
    assert!(renamed.contains(&"changed 1".to_owned()), "{renamed:?}");
    assert!(renamed.contains(&"cost 46".to_owned()), "{renamed:?}");
    let result = renamed
        .iter()
        .find(|o| o.starts_with("result "))
        .expect("a result");
    assert!(
        result.contains("minecraft:stick") && result.contains("Kiln") && result.contains("45"),
        "{result}"
    );
    // Nothing in either input: no result, and nothing changed for Horizon to take.
    assert!(anvil(27, "", "", "", "0").contains(&"changed 0".to_owned()));
    // D-203: an explosion's blocks at Horizon's explosion_detonate, Block.getExplosionResistance of each in a batch:
    // the proof mod's mixin makes a glass pane blast-proof, stone keeps Horizon's 6.
    let blast = m::bridge(
        &world,
        28,
        "explosion_detonate",
        &strings(&[
            "minecraft:overworld",
            "1",
            "64",
            "1",
            "minecraft:glass_pane",
            "0.3",
            "2",
            "64",
            "1",
            "minecraft:stone",
            "6.0",
        ]),
        None,
        &mut nothing,
    )
    .expect("the explosion is Java's");
    assert_eq!(outs(&blast), ["resistance 0=3600000.0,1=6.0"], "{blast:?}");
    // D-204: a player's break at Horizon's destroy_block, ServerPlayerGameMode.destroyBlock: the proof mod's mixin
    // keeps the blocks at x = 7.
    let broken = |call: u64, x: &str| {
        let fields = [
            x.to_owned(),
            "64".to_owned(),
            "1".to_owned(),
            "minecraft:stone".to_owned(),
        ];
        outs(
            &m::bridge(
                &world,
                call,
                "destroy_block",
                &fields,
                Some(&player),
                &mut |_: &[String]| String::new(),
            )
            .expect("the break is Java's"),
        )
    };
    assert_eq!(broken(29, "7"), ["canceled 1"]);
    assert_eq!(broken(30, "8"), ["canceled 0"]);
    // D-200: the server's tick through MinecraftServer.tickServer, called for its mixin with no tick event heard.
    assert!(
        m::hears("server_tick_start"),
        "Kiln calls the server's tick when a mixin is in tickServer"
    );
    m::server_tick(&world, 25, m::TickPhase::Start, "", &mut nothing)
        .expect("the server's tick is Java's");
}
