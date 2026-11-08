# Kiln: Proton for Minecraft

What Kiln is for, how it works, the risks, and what we have learned about Horizon on the way. Where it goes next:
[ROADMAP.md](../ROADMAP.md).

## The idea

A player downloads a Java mod (a `.jar`), drops it into a folder, starts [Horizon](https://github.com/horizon-rust/horizon)
(Minecraft: Java Edition 26.3 rewritten in Rust), and the mod works. Like Proton running Windows games on Linux: the
mod's code runs as it is, and its calls to the "system" (here, Minecraft's classes and the Fabric API) become calls
into Horizon.

For the player:
1. Install **Kiln** once from the Horizon launcher. The launcher rebuilds the game once (it warns about 20 to 30
   minutes): that is how every native Horizon mod works.
2. Put `.jar` mods into Kiln's folder.
3. Play. New jar mods need no rebuild.

## Where it stands

| | What | State |
|---|---|---|
| 0 | Scaffold: Kiln builds as a native mod and shows up in the game | done |
| 1 | Spike "Hello, jar": a tiny jar writes a line on Horizon's HUD through Kiln | done, in the game (Java in a process of its own) |
| 2 | The bridge to Horizon: the game's state, ticks while paused, keys, advancements, the HUD | done |
| 3 | A speedrun timer as a Fabric mod in Java | done; built against Minecraft 26.3 and tested in Minecraft itself (`./gradlew runClientGameTest`) |
| 4 | Kiln runs the timer in Horizon (stand-ins for exactly the classes it needs) | done, proven in the game (`kiln/proof.py`) |
| 5 | Alpha 0.1.0, then 0.1.1 on the current Horizon | released: package, [INSTALL.md](../INSTALL.md) |
| 6 | 0.2.0: a coverage testbench, a jar scanner, Minecraft's libraries, a real mod from Modrinth, structure mods, mods' code on the server next to Horizon's import | released |
| 7 | 0.3.0: Horizon Nightly 2026-11-04; a block mod Horizon's import carries whole (SpongeSucc); blocks and items declared in code, a LAN guest joins; each Java mod's switch; Tech Reborn's first machines; the testbench 139 of 139 in the game | released |
| 8 | 0.4.0: machines (energy through cables, a machine's output into the next), mods' own packets and the server's players, Mixin through the Mixin library, mods' keys; the testbench 139 of 139 in the game | released |
| next | Mods' own screens and settings pages; more of the game's methods mixins go into | see [ROADMAP.md](../ROADMAP.md) |
| goal | Mixin, blocks, block entities, menus, networking: Tech Reborn running unchanged | see [ROADMAP.md](../ROADMAP.md) |

## Why Kiln is a native mod

- **Horizon removed WebAssembly mods** (issues #3350, #3351, #3353, #3371). Content mods (data only) and native
  mods remain.
- **The WebAssembly sandbox was too narrow anyway.** Its API had HUD text, chat, the player's position, storage and
  commands. Java mods need the world, entities, blocks, rendering, screens and networking.
- **A native mod sees the whole game.** A helper gets `&mut Game`, and a bundle's modules are child modules of
  `crate::game`, so they can see its fields. horizon-mixins is close to a Rust Mixin: HEAD, RETURN, BEFORE_CALL,
  MODIFY_ARG and OVERWRITE on the game's functions.

The price: the player plays the build the launcher made with the mods, not the official build.

## Why this can work at all

- **Minecraft is not obfuscated since 26.1** (Horizon's `docs/version-ports.md`). Mods for 26.x call classes by their
  real names: `net.minecraft.client.Minecraft`, `PlayerList`…
- **Horizon's source is annotated with the same names:** comments like `Minecraft.tick`, `LevelLoadingScreen.tick`,
  `PlayerList.broadcastSystemMessage`. A table from "Java class and method" to "Horizon function" is nearly
  mechanical.
- **Horizon already reads zip files** (the `zip` crate is a dependency of `horizon-client`), so it can read jars.

## How Kiln runs Java

The options we weighed:

| | How | For | Against |
|---|---|---|---|
| 1 | **A real JVM inside the game** through JNI | Full Java: reflection, JIT, Minecraft's libraries as they are | Impossible without changing Horizon: it forbids `unsafe` code |
| 1b | **A real JVM beside the game**, talking over pipes | Full Java; a crash in Java doesn't take the game down; one code path on every OS | Answers arrive a tick later; Java can only ask the game about what Kiln sends it |
| 2 | **A bytecode interpreter in Rust** | Everything in Rust, no outside JVM | The Java standard library and Minecraft's libraries to interpret or rewrite; slower |
| 3 | **A jar-to-Rust compiler**, built by the launcher as a native mod | Fast at run time; fits the launcher's pipeline | The hardest to build; a game rebuild for every mod |

Kiln uses **1b**.

### The spike: a JVM inside a Rust program (`spike/jvm-embed/`)

A standalone program under the same limits as a native mod's module: only `std`, JNI by hand (`dlopen` of libjvm,
functions by their index in `JNINativeInterface_`). Mac mini, Apple Silicon, release build.

| | Temurin 25.0.2 | Java from the Minecraft launcher (Mojang 21.0.7) |
|---|---|---|
| Starting the JVM | 20 ms | 236 ms (cold) |
| A mod's `init`: reflection, a lambda, a stream, a call from Java into Rust | 9 ms | 20 ms |
| A call Rust → Java → Rust with a string | 286 ns | 308 ns |
| 100,000 NullPointerExceptions in a hot loop (JIT, implicit null checks through SIGSEGV) | 55 ms, no crash | 53 ms, no crash |

So a JVM fits into a Rust process, calls go both ways, reflection works, HotSpot's signal handlers live with Rust's,
and a call costs hundreds of times less than a frame's budget.

### Inside Horizon `unsafe` is forbidden, so Java runs beside the game

`crates/horizon-client/src/main.rs` and `lib.rs` start with `#![forbid(unsafe_code)]`. A native mod's modules are
built into that crate, and `forbid` can't be lifted by an `#[allow]` on our own functions. Without changing Horizon
there is no `dlopen`, no JNI and no FFI in the game's process.

So Kiln starts Java as a process of its own (`std::process::Command`, safe Rust):
`java -cp kiln-runtime.jar:<mods> kiln.Host`, and they talk in lines over stdin and stdout (the protocol is in
`kiln/java/kiln/Host.java`). A thread reads Java's answers into a channel; another writes to Java from a bounded
queue. On each tick the game only queues a line and takes the answers that are ready: **it never waits for Java**.

Measured (`kiln/tests/java_mods.rs`, Temurin 25, Apple Silicon):

| | |
|---|---|
| From the first tick to the mod's line on the HUD (Java starts, `onInitializeClient`) | 42 ms, the game doesn't wait |
| A tick's cost to the game | 1.7 µs |
| Java's answer to a tick (tick sent → HUD line back) | median 0.03 ms, worst 1.2 ms |

## The bridge to Horizon

Three layers:
- **Rust, the game's state** (`kiln/mixins/kiln/horizon.rs`): pause, game time, dimension, advancements, the font's
  glyph widths. Read only.
- **Rust, the protocol** (`runtime.rs`, `jvm.rs`): every tick sends `key`, `adv`, `chunks` (the level's chunks that
  came and went), `message` (what came to the chat) and `tick` lines to Java and takes back `draw` (what the mods
  draw), `chat`, `log`, `commands` (the mods' client commands, which the game hands Kiln when typed: `command` lines),
  `send` (commands a mod sends as the player), and `sound`, `particle` and `toast` (what the game plays for them).
  Ticks also run while paused and on the title screen (from `native_frame`), as in the vanilla client.
- **Java, the stand-ins** (`kiln/java/`): `Minecraft`, `ClientLevel`, `LocalPlayer`, `ClientPacketListener`,
  `ClientAdvancements`, `Font`, `GuiGraphicsExtractor`, `KeyMapping`, `InputConstants`, `Identifier`, `ResourceKey`,
  `Component`; from Fabric, `ClientModInitializer`, `Event`, `ClientTickEvents`, `HudElementRegistry`, `HudElement`,
  `KeyMappingHelper`, `FabricLoader`. They repeat 26.3's names and signatures exactly.

The hook points have no helpers for most of this, so Kiln reads `Game`'s own fields (read only, no code changed):
- `game.adv.tree.by_id` → index → `game.adv.progress.done(&game.adv.tree.list[i])`: whether an advancement is made;
- `game.pause`, `game.title`, `game.loading_reason()`: paused, title screen, loading;
- `game.world.game_time`: the world's game time.

The risk: a new Nightly can rename them, and Kiln won't build until it is fixed. Checking against the newest Nightly
(`native.py check --horizon latest`) catches it early.

### What we learned

- **Key codes in Minecraft 26.x are USB HID scancodes** (`KEY_A = 4`, `KEY_F1 = 58`), not GLFW. Mods compile these
  constants into their bytecode, so the stand-in must match.
- **In 26.3 `ClientAdvancements.progress()` is public:** a mod can read advancements without Mixin.
- **`GuiGraphics` is `GuiGraphicsExtractor` in 26.x**, and Fabric's HUD elements use `HudElement.extractRenderState`.
  Text color is ARGB; alpha 0 draws nothing.
- **Vanilla ticks the client while paused; Horizon doesn't.** Kiln sends Java mods ticks from `native_frame`, 20 a
  second. That is the translator's job.
- **Horizon's client view of advancements is nearly empty after a trip between dimensions.** The single-player server
  keeps each player's progress with the player (`lan.hub.guests`), so Kiln reads it there.
- **While a dimension loads, Horizon has no player** in a live session; vanilla has one. Kiln tells mods "connected,
  no player".
- **Checking the stand-ins' coverage:** compile the mod's sources against `kiln-runtime.jar`. Jars without sources
  need a bytecode scanner.

### Limits today

- A mod's HUD updates 20 times a second (once a tick).
- No Brigadier (mods' chat commands), screens, world rendering, entities or networking.
- No Mixin.
- Tested only on macOS (Apple Silicon).

## Known risks

1. **The mod registry's review.** Maintainers read native mods line by line, and starting an outside `java` process
   may not pass. The fallback is GitHub releases and a local build by the player.
2. **Java on the player's machine.** Kiln looks for a Java at least as new as the mods need (25 for 26.3 mods):
   the `java_home` setting, `KILN_JAVA_HOME`, `JAVA_HOME`, the Minecraft launcher's runtimes, installed JDKs.
3. **Horizon's internal fields change** with every Nightly. Reading them stays in one place, checked against the
   newest Nightly.
4. **Most Fabric mods use Mixin.** Without it only a small share of mods can run. Mixin support is the goal, not a
   promise.
5. **Build time for the player:** the launcher warns about 20 to 30 minutes; the alpha's first build took about 10
   on an Apple Silicon Mac, later ones a few minutes.

## What we learned about Horizon's API

### Native mods (what Kiln is built on)

- A bundle is `horizon-mixin.toml` plus `.rs` files. `[[module]]` adds a file to the game as
  `crate::game::<name>`; `[[injection]]` hangs a helper on a game function (`kind = "HEAD"` and others) with
  `bindings`.
- Modules and helpers can't have inner attributes, `//!` comments, `mod x;` or `include!`. Outside crates aren't
  available: only `horizon-client`'s dependencies (`serde_json`, `zip`, `ureq`, `chrono`, `sha2`, `log`, …).
- **Hook points v1:** `native_frame(elapsed)`, `native_tick()`, `native_hud(batch, corners)`,
  `native_key(key, pressed)`, `native_wheel(steps, claimed)`, `native_command(command, ran)`, `native_zoom`,
  `native_minimap`, `native_mods`, `native_settings`, `native_settings_click`. **v2:** the item panel and recipes.
- **Helpers:** `native_on`, `native_playing`, `native_window`, `native_player` (x y z, yaw, pitch, dimension),
  `native_frame_rate`, `native_clicks`, `native_mapping`, `native_mod_key`, `native_corner_shown`,
  `native_text_width`, `native_fill`, `native_text`, `native_chat`, `native_setting` / `native_set_setting` (a file
  per key in `horizon-mods/.horizon-mod-data/<id>/`), `native_data_dir`.
- **Missing for Kiln:** hooks for incoming chat lines, advancements made, dimension changes, joining and leaving a
  world; helpers for the game time and the pause and loading state. Kiln reads `Game`'s fields instead.
- A mod disabled in Options > Mods stays in the build until the next one, so every hook checks `native_on` first.
- The window and the single-player server have their own threads and their own `Game`; each hook must know which
  one it is in.
- **Tools:** `horizon-mods/native/tools/native.py`: `check` (apply, then `cargo check` on the commit in
  `compatibility.json`), `build` (the game plus `horizon-ctl`, fast profile), `package` (the `.hnmod`), `proof` (a
  windowless game on temporary data, driven through `horizon-ctl`; needs `--resources` with prepared 26.3 files).

### Time, pause, ticks

- While paused, the game doesn't tick at all (`if self.title.is_none() && !self.pause { self.tick() }`); frames
  always run.
- Under lag, ticks are dropped (`FixedStep::tick_fits`), so "ticks × 50 ms" falls behind real time.

### Structure mods (data packs)

- A world's data packs (`<world>/datapacks`) change its generation: Horizon reads their structures, structure sets,
  template pools and processor lists, and `/place structure` and `/locate` see them.
- Structure pieces (`.nbt`) come only from the prepared files (`worldgen/structure/<namespace>/`), not from data
  packs: a pack's structure generates empty.
- Biome lists without a namespace (`"ocean"`) match nothing in Horizon, where Minecraft reads `minecraft:ocean`.
- With both bridged, a structure mod's ship from Modrinth generates by itself in Horizon's oceans.

### Advancements

- Progress lives in `Game.adv.progress`; `Game::award` in `game/advancements.rs` grants them.
- The chat message is built from `chat.type.advancement.{task,goal,challenge}` and `chat.square_brackets` in the
  player's language, only with the `show_advancement_messages` game rule on.
