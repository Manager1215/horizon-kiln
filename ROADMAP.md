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

## 0.2: measure it

You can't improve what you don't measure.

- **kiln-testbench** (done): a Fabric mod with 139 probes over Minecraft and the Fabric API. All 139 pass in
  Minecraft with Fabric; **18 pass through Kiln** today. That number is what we push up.
- **Minecraft's libraries**: Gson, Guava, fastutil, SLF4J, JOML, Brigadier and DataFixerUpper are open source, and
  most mods need some of them. Kiln puts them on the mods' class path.
- **kiln-scan**: reads any mod's jar and lists what it calls in Minecraft and Fabric, and whether it uses Mixin; then
  says what Kiln is missing for it.
- **Generated stand-ins**: every class of Minecraft 26.3's client API, with its exact signatures, generated rather
  than written by hand. Methods Kiln can't serve yet fail with a clear message, so a mod links fully and stops only
  where it really needs something missing.
- Kiln tested on Windows and Linux.

## 0.3: someone else's mod

- One or two small client mods from Modrinth, without Mixin (HUD info mods are the likely first ones), downloaded
  and run as they are.
- More of the game for mods: the player's position and inventory, the frame rate, styled text, more of
  `GuiGraphicsExtractor`.
- Client commands through Brigadier.

## 0.4: Mixin

Most Fabric mods change the game with Mixin. Kiln's Minecraft classes are its own stand-ins, so Mixin can apply to
them; injections then run when Horizon runs the matching code.

- The Fabric Mixin library running over Kiln's stand-ins.
- The common injection kinds first (`@Inject` at HEAD and RETURN), then the rest.
- Screens: mods' own menus and settings pages.

## 0.5 and on: the world

What a content mod like Tech Reborn needs:

- Registries: blocks, items, block entities, recipes, tags.
- The server side: Kiln on Horizon's single-player server, not only the window.
- Menus with slots (screen handlers), networking between client and server.
- Rendering of blocks and items the mod adds.
- Energy and item transfer APIs.

## 1.0: Tech Reborn

Tech Reborn's jar from Modrinth, unchanged, in Horizon: build a few machines and power them. Live, on stream.
