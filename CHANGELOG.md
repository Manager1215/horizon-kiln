# Changelog

## 0.2.0 — 2026-11-04

Kiln 0.2.0 and Kiln Speedrun Timer 0.2.0, on Horizon Nightly 2026-11-01 · c9d474d: 0.2.0-beta.1 as testers ran it,
from a fresh install on Windows 11 (the build, the timer, Show FPS, Heart Crystals, Illager Warship), with these
changes:

- **Kiln:** its description in Options > Mods says what it runs now, not the prototype it was.
- **INSTALL.md:** Windows' build command in the launcher's folder and the archive's doubled folder after Extract All;
  the build goes into the instance you play; Kiln's folder on Windows; running the build again after "os error 3" or
  "32"; moving the timer away from Show FPS's corner.

## 0.2.0-beta.1 — 2026-11-03

Kiln 0.2.0 and Kiln Speedrun Timer 0.2.0-beta.1, on Horizon Nightly 2026-11-01 · c9d474d, for testers before 0.2.0.
Kiln now runs a mod from Modrinth unchanged (Show FPS), brings structure mods' structures into Horizon's worlds, and
runs Java mods' code on Horizon's single-player server next to the data Horizon's mod browser carries (Heart
Crystals): items, events, saved data, loot, and blocks that are used, tick, are placed and broken.

- **Timer:** the split key reaches Finish only with `finish=manual`; with `dragon` (the default) or `leave_end` it
  stops at the last split, and the dragon or the way back from the End finishes the run. Before, pressing J seven
  times finished any run, and a quick string of presses could become a personal best of a second: delete
  `records.txt` if that happened to you.
- **Kiln on the server (D-052):** the first Partly mod made whole. Installed through Horizon's mod browser (its data)
  with its jar in `kiln/mods` (its code), [Heart Crystals](https://modrinth.com/mod/heart-crystals) works: using a
  crystal runs the mod's own `Item.use` in Kiln's Java, and Horizon's single-player server does what it did (the
  player's maximum health, the crystal spent, its sound, "Heart limit reached" over the hotbar). Kiln asks Java only
  for items a Java mod gave a use of its own, and waits at most 250 ms; a mod that fails or is slow costs that one
  use, which goes on as Horizon's own.
- **Kiln on the server:** Java mods hear players come into a level (joining, changing dimension) and respawn
  (Fabric's `ServerEntityEvents.ENTITY_LOAD` for players, `ServerPlayerEvents.COPY_FROM` and `AFTER_RESPAWN`). Heart
  Crystals sets a new player up, and a respawned player gets their hearts back (less what the mod's settings take for
  dying).
- **Kiln:** attributes a Java mod changed stay on the player through portals and respawns, as in vanilla: Horizon
  gives a player a new body then, at the defaults.
- **Kiln on the server:** Java mods' changes to the game's loot tables (Fabric's `LootTableEvents.MODIFY`). When a
  world opens, Kiln runs them in a Java of its own (only when a mod uses Fabric's loot API and the mods changed) and
  Horizon adds the pools to its tables as they load. Proven with a mod of ours (`kiln/proof-mods/loot`); Heart
  Crystals' own loot code adds nothing on 26.3, on Fabric too.
- **Kiln on the server: blocks.** A Java mod's block runs its own code in Horizon: a click on it (`useItemOn`,
  `useWithoutItem`), its random tick (in place of its template's) and a player standing on it (`stepOn`); it can set
  blocks and drop items, and read the world while it runs (Java asks the waiting server). Stand-ins for blocks, their
  states and properties. Proven with a mod of ours (`kiln/proof-mods/blocks`): no downloaded block mod fits Horizon's
  import yet.
- **Kiln on the server: blocks, further.** A Java mod's block takes its scheduled ticks (and schedules them), hears
  its neighbours change, chooses the state it is placed in (`getStateForPlacement`: a block that faces the player),
  and hears being placed and broken (`setPlacedBy`, `playerWillDestroy`). Kiln's proof timer shows each in the game.
- **Kiln:** the game's items mods name most (`Items.DIAMOND`, `IRON_INGOT`...) are in `BuiltInRegistries.ITEM`, as in
  vanilla.
- **Kiln:** Java mods' saved data (`SavedData`) is kept with the world, as JSON in `<world>/kiln/data/`.
- **Kiln:** stand-ins for items, stacks, registries (`Registry.register`, `BuiltInRegistries.ITEM`), players and
  levels on the server, attributes, sounds, chat formatting and translated text (the mods' own English for
  `getString`; Horizon shows the player's language), Fabric's server and creative-tab events (registered, not fired
  yet) and loot table builders. `LocalPlayer` is a `Player`, as in vanilla.
- **Kiln:** Java runs in `<game folder>/kiln/`, so a mod writing `config/` by a relative path writes its config folder.
- `kiln/tests/any_mod.rs` uses items as a player would (`KILN_USE`), with a join and a respawn around
  (`KILN_EVENTS`); `kiln/proof.py` installs content mods (`KILN_PROOF_CONTENT`), the Java mods' settings
  (`KILN_PROOF_CONFIG`), has a player use items (`KILN_PROOF_USE`), die and respawn, rolls a dungeon chest's table
  with Kiln's proof mods in, and fails on any call that failed in Java.
- The testbench: 49 of 139 answer; 11 of them aren't counted (they read the window's player, a sparse registry or
  the window's blocks, which Kiln doesn't have yet): the measure is 38.
- **Structure mods:** a mod whose structures are data generates them in Horizon's worlds. Before a world opens Kiln
  copies the mods' world generation out of their jars into `<game folder>/kiln/generated/` (a data pack, and the
  structure pieces), adds `minecraft:` to biome and tag ids that name no namespace, and patches Horizon to read both:
  every world gets the pack, and pieces Horizon lacks come from Kiln's folder. First one: Illager Warship 1.0.0.
- **Kiln** now patches three of Horizon's functions besides its hook points, so it is tied to the Nightly it was made
  for (c9d474d); horizon-mixins moved to ddd9700 (it accepts functions with `format!`).
- `kiln/proof.py` checks extra mods' structures: `/locate` finds them, and the blocks of their pieces stand there.
- **Structure mods at scale:** Kiln brings only structures' data (not terrain, which can stop Horizon opening a
  dimension), makes mods' biomes optional in biome tags, strips byte order marks and leaves out unreadable JSON.
  Thirteen popular structure mods together generate in the Overworld, the Nether and the End.
- **First third-party mod:** [Show FPS](https://modrinth.com/mod/showfps) 2.0.0, downloaded from Modrinth as it
  is, runs in Horizon through Kiln: its FPS counter is on the HUD. Its settings screen doesn't open yet.
- **Kiln:** runs mods' `main` entrypoints too (Fabric's `ModInitializer`), every mod's before every `client` one,
  as Fabric does.
- **Kiln:** mods read the game's frame rate (`Minecraft.getFps`), counted from Horizon's frames.
- **Kiln:** stand-ins for `VanillaHudElements`, `Gui` and `Screen` (opening a screen is logged: Kiln shows no mods'
  screens yet), `DebugScreenOverlay`, `KeyMapping.Category`'s vanilla categories, `DeltaTracker`'s methods, and
  `GuiGraphicsExtractor.text` with a `Component`.
- **Kiln:** the mods' config folder exists before they write to it, as in Fabric.
- `tools/kiln-scan.py`: what a mod's jar uses from Minecraft, Mojang's libraries and the Fabric API that Kiln
  doesn't have. `kiln/tests/any_mod.rs` runs any mod's jar through Kiln with real Java (`KILN_MOD`), and
  `kiln/proof.py` runs extra mods in the game (`KILN_PROOF_MODS`).
- The testbench through Kiln: 32 of 139.
- **Kiln:** gives Java mods the open-source libraries Minecraft 26.3 ships, in the same versions: Gson, Guava,
  fastutil, SLF4J, JOML, and Mojang's Brigadier and DataFixerUpper (`<game folder>/kiln/lib/`, with their
  licenses), and Mojang's `LogUtils` as a stand-in over SLF4J. Mods' log lines go to `kiln/java.log`.
- `tools/build-java.sh` downloads the libraries (Maven Central; Mojang's library server for Mojang's two) and stops
  on any whose SHA-256 isn't the one in `kiln/lib/libraries.txt`; `tools/package.sh` puts them in the release.

## 0.1.1-alpha — 2026-11-02

Kiln 0.1.1 and Kiln Speedrun Timer 0.1.1-alpha: the fixes since the first alpha, proven on Horizon Nightly
2026-11-01 · c9d474d (0.1.0 was pinned to 2026-10-29 · 5e391eb).

- **Kiln:** a stuck Java mod no longer freezes the game: commands go to Java through a bounded queue, and
  Kiln stops Java with a line in chat when a mod hasn't read them for about a minute.
- **Kiln:** a mod calling a class or method Kiln has no stand-in for (`NoSuchMethodError`,
  `NoClassDefFoundError`) no longer stops Java and every other mod; the error is logged.
- **Kiln:** picks a Java at least as new as the mods' classes need (Java 25 for mods built for 26.3), not
  just any Java 21+.
- **Kiln:** names more keys as Minecraft does (the keypad's divide, subtract, period and decimal, Application and
  Menu), so mods hear them.
- **Timer:** leaving the world after a finished run resets the timer; before, the next world repeated the old
  run's final time in chat, recorded it again and didn't start a new run.
- **Kiln Testbench** (`mods/kiln-testbench`): a Fabric mod with 139 probes over Minecraft 26.3 and the Fabric API,
  each isolated in a class of its own, and a report of which pass. All pass in Minecraft with Fabric (its client
  gametest); 18 pass through Kiln (`kiln/tests/testbench.rs`, which fails if the number drops).
- `tools/package.sh` falls back to `sha256sum` where `shasum` is missing.
- `tools/build-java.sh` copies only the current version's jars (after a version change it used to copy the old jar
  too, and the release would have shipped two timers).
- Tests: the stand-in's key names are checked against Horizon's own table (`kiln/tests/key_names.rs`); the
  paused-ticks test is bounded by the time that passed, so a busy machine no longer fails it.
- Documents: fixes to README and INSTALL (build time, manual finish, keys under Kiln).
- **License:** GPL-3.0-only from this release, with a contributor license agreement (CLA.md). 0.1.0-alpha stays
  MIT.
- The project is in English only: INSTALL in English, the Russian README removed. The plan and what we learned
  about Horizon moved to docs/VISION.md; where Kiln goes next is in ROADMAP.md.
- Releases: stable releases are published to the public repository from a private development repository
  (CONTRIBUTING.md).

## 0.1.0-alpha — 2026-10-29

First alpha: Kiln 0.1.0 and Kiln Speedrun Timer 0.1.0-alpha. Install: INSTALL.md. Tested on macOS (Apple
Silicon) with Horizon Nightly 2026-10-29 · 5e391eb; the timer also in Minecraft 26.3 with Fabric
(`./gradlew runClientGameTest`).

- **Kiln 0.1.0** runs Fabric client mods in Horizon. Java runs in a process of its own (Horizon forbids `unsafe`
  code, so no JVM in the game's process); Kiln gives it a client tick 20 times a second, also paused and on the
  title screen, with the game's state, the keys pressed and the advancements made (from the single-player
  server, as Horizon's client view loses them between dimensions), and draws what the mods draw on the HUD.
  Stand-ins for the Minecraft 26.3 and Fabric API classes the mods call, with their exact signatures. With no
  Java mods yet, it makes its `kiln/mods` folder and names it in chat.
- **Kiln Speedrun Timer 0.1.0-alpha**, a Fabric mod for Minecraft 26.3: Any% with automatic splits (Nether,
  Bastion, Fortress, Blind, Stronghold, End, Finish) from advancements and dimensions, RTA and IGT without pauses
  and loading screens, personal best and best splits with deltas, a panel in a chosen corner, keys J (start /
  split), K (reset), U (hide), settings and records in its config folder. The same jar runs in Horizon through
  Kiln: proven in the game by `kiln/proof.py`.
- Spike: a JVM inside a Rust program through hand-written JNI (`spike/jvm-embed`), and the spike's Fabric mod.
- Project documents: README, the plan, the decisions.
