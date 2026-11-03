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

## 0.2: measure it

You can't improve what you don't measure.

- **kiln-testbench** (done): a Fabric mod with 139 probes over Minecraft and the Fabric API. All 139 pass in
  Minecraft with Fabric; **18 pass through Kiln** today. That number is what we push up.
- **Minecraft's libraries** (done): Kiln gives mods Gson, Guava, fastutil, SLF4J, JOML, Brigadier and
  DataFixerUpper, the versions Minecraft ships; the testbench went from 18 to 26.
- **kiln-scan**: reads any mod's jar and lists what it calls in Minecraft and Fabric, and whether it uses Mixin; then
  says what Kiln is missing for it.
- **Generated stand-ins**: every class of Minecraft 26.3's client API, with its exact signatures, generated rather
  than written by hand. Methods Kiln can't serve yet fail with a clear message, so a mod links fully and stops only
  where it really needs something missing.
- Kiln tested on Windows and Linux.

## 0.3: code next to Horizon's import

Horizon's launcher installs Java mods from Modrinth by carrying their data into a Horizon content mod: blocks, items,
recipes, models, structures. What needs the mod's Java code, it marks **Partly**, and leaves out. Kiln runs that code,
next to the data Horizon carries: together, a Partly mod becomes one that works.

- Kiln on Horizon's single-player server, not only in the window, answering the game when it asks (an item used, a
  block placed or broken, a block clicked).
- The first Partly mods made whole: items and blocks with behaviour of their own. First: Heart Crystals (a used item);
  blocks that are clicked, tick and are stood on work, waiting for a block mod Horizon's import carries whole.
- Client mods keep running as they do now (Show FPS, the timer): HUD, keys, the frame rate, more of the player and
  the world.

## 0.4: machines

- Block entities: blocks that keep state and tick, their inventories.
- Menus with slots and their screens, through Horizon's menus for mod blocks.
- Energy and item transfer between blocks.

## 0.5: Mixin

Most Fabric mods also change the game with Mixin. Kiln's Minecraft classes are its own stand-ins, so Mixin can apply
to them; injections then run when Horizon runs the matching code.

- The Fabric Mixin library running over Kiln's stand-ins, the common injection kinds first.
- Mods' own screens and settings pages.

## 1.0: Tech Reborn

Tech Reborn from Modrinth: its data through Horizon's import, its code through Kiln, unchanged. Build a few machines
and power them. Live, on stream.
