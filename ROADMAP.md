# Roadmap

Where Kiln is going. The goal: [Tech Reborn](https://modrinth.com/mod/techreborn), an industrial Fabric mod with
machines, energy and menus, running unchanged in [Horizon](https://github.com/horizon-rust/horizon) through Kiln.
That is a long way off; the milestones on the way are below. Dates are not promised.

Every stable release lands here, in the public repository. Beta builds go to a small group of testers first.

## Done

**0.1.0-alpha** (2026-10-29). Kiln runs Fabric client mods in a Java process beside Horizon: ticks, keys,
advancements, the HUD. The first mod through it is our own speedrun timer, proven in the game.

**0.1.1-alpha.** Fixes (a stuck mod no longer freezes the game, a missing stand-in no longer stops every mod, Kiln
picks a Java new enough for the mods), proven on the Horizon Nightly of 2026-11-01. The first release under
GPL-3.0.

**0.2.0** (2026-11-04). Measured: a testbench of 139 probes and a jar scanner. Minecraft's libraries
for mods. The first mod from Modrinth, unchanged (Show FPS). Structure mods' structures in Horizon's worlds. And the
start of 0.3: Java mods' code on Horizon's single-player server next to the data Horizon's mod browser carries
(Heart Crystals), with items, events, saved data, loot, and blocks that are used, tick, are placed and broken.

**0.3.0.** Code next to Horizon's import, on Horizon Nightly 2026-11-04: a block mod from Modrinth whose blocks the
import carries, its code by Kiln (SpongeSucc's lava sponge takes up lava); the blocks and items the import leaves
out declared in code through Horizon's own API, so a LAN guest joins with them; each Java mod in Options > Mods with
a switch of its own; Tech Reborn's first machines on energy, with their screens, recipes and fluids; client mods
from Modrinth (Armour Durability HUD, Better Than Mending), and every testbench probe in the game.

**0.4.0.** Machines and networking, on the same Nightly: Tech Reborn's cables carry energy and a machine hands its
output to the next; mods' own packets between the player and the server and to a LAN guest, with the players a
server sends to and their joining and leaving; a mod's keys bound in Options > Mods. And the first part of 0.5:
mods' mixins applied by the Mixin library itself, every kind, into Kiln's stand-ins and the game's methods mods hook
most, the HUD's layers among them. The testbench still 139 of 139 in the game.

**0.4.1.** Fixes for 0.4.0's known issues: a mod's translated chat lines (Quick Resource Pack), a mod's packet that
Java answers late; and five structure mods 0.4.0 listed as broken weren't.

## 0.2: measure it

You can't improve what you don't measure.

- **kiln-testbench** (done): a Fabric mod with 139 probes over Minecraft and the Fabric API. All 139 pass in
  Minecraft with Fabric; **38 passed through Kiln** in 0.2.0 (18 in 0.1.1-alpha); all 139 pass in the game in 0.3.0
  and 0.4.0.
- **Minecraft's libraries** (done): Kiln gives mods Gson, Guava, fastutil, SLF4J, JOML, Brigadier and
  DataFixerUpper, the versions Minecraft ships; the testbench went from 18 to 26.
- **kiln-scan**: reads any mod's jar and lists what it calls in Minecraft and Fabric, and whether it uses Mixin; then
  says what Kiln is missing for it.
- **Generated stand-ins**: every class of Minecraft 26.3's client API, with its exact signatures, generated rather
  than written by hand. Methods Kiln can't serve yet fail with a clear message, so a mod links fully and stops only
  where it really needs something missing.
- Kiln tested on Windows (done: 0.2.0 was tested on Windows 11) and Linux.
- **Java without installing**: Kiln also looks in Horizon's own runtime folder, where the launcher keeps the Java it
  downloaded for 26.3; most players then install nothing.
- **A compatibility report per mod** at start, in chat and `java.log`: the calls Kiln served and the ones it only
  has the name of. An unserved call fails with a clear message and the mod keeps running.
- **The census**: kiln-scan over the most downloaded Fabric mods for 26.3 on Modrinth: the classes and methods mods
  call most, and how many of them Kiln serves. That number goes into the README next to the testbench's.
- **The corpus as a test**: every mod on the "runs today" list runs in a windowless game before a release, and its
  effect is checked.

## 0.3: code next to Horizon's import

Horizon's launcher installs Java mods from Modrinth by carrying their data into a Horizon content mod: blocks, items,
recipes, models, structures. What needs the mod's Java code, it marks **Partly**, and leaves out. Kiln runs that code,
next to the data Horizon carries: together, a Partly mod becomes one that works.

- Kiln on Horizon's single-player server, not only in the window, answering the game when it asks (an item used, a
  block placed or broken, a block clicked) (done).
- The first Partly mods made whole: items and blocks with behaviour of their own: Heart Crystals (a used item) and
  SpongeSucc, a block mod Horizon's import carries whole (done).
- Client mods keep running as they do now (Show FPS, the timer): HUD, keys, the frame rate, more of the player and
  the world (done: every testbench probe in the game).
- Content a mod declares only in code reaches Horizon too, through Horizon's own declarations for native mods, so ids,
  saves and a joining client's check are Horizon's; a LAN guest sees it (done for the blocks and items Horizon's
  import leaves out; a guest sees a Java mod's blocks, and its mods' payloads reach it; its screens come with 0.4's
  networking).
- Each Java mod listed in Options > Mods, with a switch of its own (done).

## 0.4: machines (done in 0.4.0)

- Block entities: blocks that keep state and tick (started in 0.3: a mod's block entity is made, ticks and is saved
  with the world), their inventories, the game's recipes and fuels for their code (Tech Reborn's iron furnace
  smelts, filled by command until its screen comes).
- Menus with slots and their screens, through Horizon's menus for mod blocks.
- Energy and item transfer between blocks (done): Fabric's server tick events where
  Horizon's server ticks, so Tech Reborn's cables carry a solar panel's energy to a machine; and Fabric's item
  storage for a mod's container, so one machine's output goes into the next by itself. The game's own chests and
  hoppers as storages for a mod's machine come later.
- Networking (done): mods' own payloads (Fabric's `PayloadTypeRegistry`,
  `ClientPlayNetworking` and `ServerPlayNetworking`) over Horizon's channel for mods, both ways, in single player and
  to and from a LAN guest (a real mod's, Elytra Wind Brake's handshake, in the game); the players a server sends to
  (`PlayerLookup`: everyone, a level's, those who see a block, a machine or an entity, those around a place), so a
  machine's updates reach whoever watches it; and the join and leave events (`ServerPlayConnectionEvents`), where most
  mods send a joining player their first sync.
- Next: Java in the game's process, instead of beside it. Measured: about 150 ns a call, against 9 µs for a message
  each way to a separate Java; garbage collection never stalls the game (with ZGC, every pause under 0.12 ms, and
  only Java's own threads wait). The server no longer waits on Java up to 250 ms, and mods' code can run where the
  game calls it often: every machine, every entity, every tick. A separate Java stays as the fallback.

## Across 0.3 to 0.5: Horizon's typed mod API

Horizon's native mods are getting a typed API: a `Hooks` trait, a `Host`, and handles for players, levels, block
entities and items. As each piece lands, Kiln reads the game through it instead of the game's own fields. With no
mixin left in Kiln, the launcher links it against the prebuilt game: an install in seconds instead of a rebuild, and
a new Nightly no longer breaks it.

## 0.5: Mixin

Most Fabric mods also change the game with Mixin. Kiln's Minecraft classes are its own stand-ins, so Mixin can apply
to them; injections then run when Horizon runs the matching code.

- Kiln applies the mods' mixins with the Mixin library itself (FabricMC's Mixin and MixinExtras, the jars Fabric
  Loader ships), to its stand-ins and to the mods' own classes as they load. **Done:** every kind, as under Fabric,
  config plugins too; a mixin that can't apply is left out and says so in Kiln's log, the class keeping its others;
  and the game's methods mods hook most run where the game runs them, the HUD's layers among them (in 0.4.0).
  **Next:** more of what mixins go into. Kiln calls the game's methods at Horizon's own hooks, by a table; next, the
  table's rows also inject into Horizon's functions themselves (through horizon-mixins, the way native mods do),
  written and checked by hand against Horizon's map of which function ports which vanilla method: entities' ticks
  first, with Java in the game's process. Generating them automatically was measured and doesn't work: Horizon ports
  vanilla's behaviour, not its methods one for one. Mixins into rendering stay out of reach.
- Mods' own screens and settings pages.

## 0.6: NeoForge

NeoForge mods, unchanged: `META-INF/neoforge.mods.toml`, `@Mod` entrypoints, the FML lifecycle, the NeoForge event
bus and `DeferredRegister`. Horizon's hook catalog is modelled on NeoForge 26.3's events, so Kiln's stand-ins for
them map one to one. It opens the big NeoForge mods (Create, Mekanism). Entrypoint discovery and the lifecycle
events can come earlier, as testbench probes.

## 1.0: Tech Reborn

Tech Reborn from Modrinth: its data through Horizon's import, its code through Kiln, unchanged. Build a few machines
and power them. Live, on stream.
