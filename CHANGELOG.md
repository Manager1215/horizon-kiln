# Changelog

## Unreleased

## 0.4.1 — 2026-11-09

Kiln 0.4.1, on Horizon Nightly 2026-11-04 · 3fb4c31, as 0.4.0: fixes for 0.4.0's known issues. The testbench: 139 of
139 probes in the game (`kiln/proof.py`).

- **Kiln: a mod's translated chat lines show again.** Since 0.4.0 a mod's own translated chat line went to the game as
  its translation key, which the game has no text for: Quick Resource Pack's key (R) said nothing, and now says
  "No resource pack configured" again.

- **Kiln: a mod's packet answered late is handled.** On a busy computer, a mod's packet that Java answers after Kiln's
  250 ms is handled when the answer comes, even when its receiver looks up the players, as most do; it was dropped.

- **Not broken after all: five structure mods 0.4.0 listed.** Epic Structures Dungeons, Nice Mob Variants,
  Thirtyfangss Kingdom, Survivors' Elegy and MOTS Structures work on 0.4.0 and 0.4.1: tried one game at a time, their
  structures are there as on 0.3.0. The automatic try misjudged them with ten games at once; it now waits for each
  search's own answer and for a structure's chunks.

## 0.4.0 — 2026-11-08

Kiln 0.4.0, on Horizon Nightly 2026-11-04 · 3fb4c31, as 0.3.0: machines and networking, the roadmap's 0.4, and
Mixin through the Mixin library itself, the first part of 0.5. The testbench: 139 of 139 probes in the game
(`kiln/proof.py`); 135 in Kiln's test without the game's prepared resources. The mods that work, each tried in the
game, are on [horizon-kiln.cc](https://horizon-kiln.cc/mods.html).

- **Kiln: Mixin, every kind.** Kiln now applies Java mods' mixins with the Mixin library itself: FabricMC's Mixin
  0.17.5 and MixinExtras 0.5.5, the same jars Fabric Loader 0.20.1 gives mods, in `kiln/lib` beside ASM. Every kind a
  mod can use applies as it does under Fabric: `@Inject` anywhere in a method (at a call, a field, a `new`, with its
  shift, ordinal, slice and captured locals), `@Redirect`, `@ModifyArg`, `@ModifyArgs`, `@ModifyVariable`,
  `@ModifyConstant`, `@Overwrite`, accessors and invokers, MixinExtras' `@WrapOperation`, `@WrapWithCondition`,
  `@ModifyExpressionValue`, `@ModifyReturnValue`, `@WrapMethod`, `@Local` and `@Share`, a mixin's static initializer,
  and mixin config plugins. Kiln's own rules stay on top: a mixin Mixin can't apply is left out and said in Kiln's
  log, and the class keeps its other mixins (Mixin would load it with none); a field or method a mixin's `@Shadow` or
  accessor names and Kiln's stand-in hasn't is added to the stand-in; a mod's mixin code that fails doesn't stop the
  game. What still holds mixins back is where they go: Kiln's stand-ins of Minecraft's classes have the game's names,
  not its code, so a mixin that hooks a call inside one of the game's methods finds it only where Kiln's code makes
  the same call.

- **Kiln: what mixins go into.** A method a mixin overwrites, or injects into at its start or end, that Kiln's
  stand-in of the game's class hasn't is added to it as the class loads, so the mixin applies; one Kiln's stand-in has
  in a class above calls that one. And more of the game's methods mods hook run where the game runs them: the main
  loop (`Minecraft.run`, Fabric's template mixins among them), a single-player world loading
  (`MinecraftServer.loadLevel`), the HUD drawn (`Hud.extractRenderState`, the mods' HUD elements inside it), the
  keys handled each tick, the player's tick down through its classes, the options read. Of the mods the catalogue
  says wait only on Mixin, every mixin of 268 now applies, up from 69.

- **Kiln: the HUD's layers in the game's order.** A mod's HUD element goes where the mod puts it: first, last, before
  or after one of the game's layers (the hotbar, the armour, the hearts, the effects, the chat) or another mod's. The
  game's own methods for those layers run in that order, so the HUD mods' mixins into them run: Minimal Armor HUD,
  Key Overlay, Modern Armor Bar and others hook there.

- **Kiln: mods that are only mixins.** A mod with mixins and no entrypoint (Minimal Armor HUD, Sodium Fullbright,
  Hurtcam Off, Ladder Sprint and others) starts Java by itself, counts among the Java mods running, and its row in
  Options > Mods says "Mixins only". Before, its jar was taken for data and its mixins never applied.

- **Kiln: Fabric Loader's own mods, and mods in Kotlin.** A mod that asks Fabric Loader for `minecraft`, `java`,
  `fabricloader` or `fabric-api` finds them, with their versions. A mod written in Kotlin, whose entrypoint is a
  Kotlin object, starts (it still needs Fabric Language Kotlin installed, as under Fabric). A method of Minecraft's
  that Kiln has only the name of and that returns an `Optional` now gives an empty one, not nothing.

- **Kiln: energy and items between machines.** Fabric's server tick events run where Horizon's server ticks, so
  Tech Reborn's cables carry a solar panel's energy to the machine at their other end. A machine set to send its
  output to a side hands it to the machine there by itself (Fabric's item storage): a grinder's dust goes into the
  electric furnace beside it, which smelts it into ingots.

- **Kiln: mods' own payloads.** A Java mod's packets (Fabric's `PayloadTypeRegistry`, `ClientPlayNetworking` and
  `ServerPlayNetworking`) go between the player's side and the server's over Horizon's channel for mods: in single
  player, and to and from a friend who joins your world over LAN with the same Kiln and mods. Elytra Wind Brake's
  client, for one, now hears from the server that it has the mod (its brake itself doesn't work in Kiln yet).

- **Kiln: the server's players and their connections.** A Java mod on the server now finds the players it sends to
  (Fabric's `PlayerLookup`: everyone, a level's, the players who see a block, a machine or an entity, those around a
  place; and the server's player list), and hears a player join and leave (Fabric's `ServerPlayConnectionEvents`),
  where most mods send a joining player their first sync. A friend who joins your world over LAN gets that sync too,
  even though their mods start a moment after they arrive. Tech Reborn's storage units now send what they hold to
  the players near them.

- **Kiln: Java mods' keys can be bound.** Each Java mod's page in Options > Mods lists its keys as Key Binds rows,
  with the mod's own names for them: choose another key there, as for a native mod's, and the mod follows it (the
  game keeps the choice in `horizon-mods.txt`). A mod's keys show on its page from the first world it runs in.

- **Kiln: structures as Minecraft reads them.** A structure mod's ids without a namespace (`"type": "jigsaw"`) get
  `minecraft:`, its block states take 26.3's shape, and the folders of its pack made for 26.3 (overlays) replace the
  older files they stand for, as in Minecraft. Found on Mingen, 28 of whose 30 structures were never located, and on
  Small Structures, none of whose were.

- **Kiln: a mod's chat lines keep their colours.** A line a mod writes in the chat with a colour, bold or italic
  (`withStyle`) shows them, as Calc's red error does; `§` codes still work.

- **"No Java found" says what to do.** When Kiln finds no Java new enough, chat says which to install (Temurin, from
  adoptium.net) and names your own `java_home` file to point Kiln at one. When that file (or `KILN_JAVA_HOME`)
  points at a folder with no Java in it, chat says what it should hold: the folder that holds `bin/java`.

- **The testbench checks what it reads.** Each probe that returned a value without looking at it now checks it
  against what holds in any world, in Minecraft as under Kiln: the player's position against the block they stand in
  and their eyes, health against the max health attribute, food against `needsFood`, the window's GUI size against
  its pixels, the pointer in both, the player on the player list in their game mode, the inventory's 43 slots, the
  frame rate above 0, every block of the game in the registry. Breaking Kiln's Java stand-ins one change at a time
  (`sh tools/java-mutants.sh`) found the probes that would have passed with a stand-in broken. Two were passing on
  paper: Kiln's inventory had 41 slots, not 43, and its block registry only the blocks mods had named. Kiln now has
  both right. Still 139 of 139 in Minecraft with Fabric and under Kiln with the game's resources; 135 without them,
  where every block of the game can't be known.

- **Kiln comes without mods.** The release archive's `kiln/mods/` is empty: installing Kiln no longer installs the
  speedrun timer too.

- **The speedrun timer leaves Kiln's releases.** Kiln's repository and release no longer carry the timer: its source
  and its jar stay as they were in 0.3.0 (the `v0.3.0` tag), and Kiln's tools work without it.

**Known issues in 0.4.0.** Of the mods tried in the game and marked as working, 154 of 160 work on this build. These
worked with Kiln 0.3.0 and don't with 0.4.0, to be fixed in 0.4.1:
- Quick Resource Pack: its key (R) does nothing.
- Four structure mods build one of their structures empty or nearly so: Epic Structures Dungeons (its large sand
  dungeon), Nice Mob Variants (its nether well), Thirtyfangss Kingdom (its warped temple) and Survivors' Elegy (its
  fortress; its mesa mineshaft isn't found).
- With MOTS Structures the game stops responding in a new world.
- On a heavily loaded computer, a mod's packet that Java answers late (after Kiln's 250 ms) can be dropped instead of
  being handled late.

## 0.3.0 — 2026-11-04

Kiln 0.3.0 and Kiln Speedrun Timer 0.2.0, on Horizon Nightly 2026-11-04 · 3fb4c31: code next to Horizon's import,
the whole of the roadmap's 0.3. The testbench: 139 of 139 probes in the game (`kiln/proof.py`), every one answering
from Horizon's game (0.2.0: 38); 136 in Kiln's test without the game's prepared resources. Mods known to run, each
tried in the game on this build: the timer, Show FPS, Illager Warship, Heart Crystals, Armour Durability HUD, Better
Than Mending and SpongeSucc; which more work or should work is on [horizon-kiln.cc](https://horizon-kiln.cc/mods.html).

- **Kiln on Horizon Nightly 2026-11-04 · 3fb4c31.** Kiln builds on this Nightly now, and no longer on 2026-11-01:
  build it with `--version nightly-2026-11-04-3fb4c31` (INSTALL.md). Everything below was proven on it.

- **Kiln: each Java mod in Options > Mods.** Below Kiln's own row, every jar in `kiln/mods` has a row with its name,
  version and description, and a switch. A mod turned off is left out as if its jar weren't there: its code, its
  structures, the blocks Kiln brings for it. Kiln runs all Java mods in one Java, so a switch changed in a world takes
  effect from the next world you enter: back on the title screen, Kiln stops Java, and the next world starts it with
  the mods that are on.

- **Kiln: the blocks and items Horizon's import leaves out are declared, not written.** Kiln no longer writes Tech
  Reborn's machines and items into the content Horizon's mod browser made: it declares them when the game starts, the
  way Horizon's own native mods declare blocks and items in code, and Horizon checks them and adds them to that
  content. A friend joining your world over LAN, with the same Kiln and mods, joins with them and sees them. The
  files Kiln 0.2.0 wrote there are taken away.

- **Kiln: a block mod from Modrinth.** [SpongeSucc (Lava Sponge)](https://modrinth.com/mod/spongesucc), installed
  through Horizon's mod browser (its blocks) with its jar in `kiln/mods` (its code): a lava sponge placed beside lava
  takes it up and turns into a hot lava sponge. Its lava dripping onto dripstone is a Mixin, which Kiln doesn't apply
  yet.

- **Kiln: Mixin, the first kinds.** Kiln applies a Java mod's mixins to its stand-ins as they load: accessors and
  invokers (a mod reads and calls what is private), and `@Inject` at the start of a method, at its returns and at
  its end, with what the mixin adds to the class (its fields, methods and interfaces). A mixin that needs a kind Kiln
  doesn't apply yet (`@Redirect`, `@Overwrite`, MixinExtras and others) is left out whole, and Kiln's log says which
  and why. Most mods' mixins still need more: what the stand-ins lack (the members and methods mixins name) comes
  next. Kiln now ships ASM, which Fabric Loader gives mods too.

- **Kiln: mixins into the client's tick, a world joined and left, a screen opened.** The game's methods that mods'
  mixins inject into most now run where the game runs them: the client's tick and the player's (`Minecraft.tick`,
  `Player.tick`), a world's level set (`Minecraft.setLevel`), the world left (`Minecraft.disconnect`), a screen opened
  or closed (`Gui.setScreen`), each with Fabric's events of that moment inside it, so a mixin's code comes just
  before or just after Fabric's. RebornCore's mixin on the player's tick applies. A mixin's code that fails is said
  once in Kiln's log, and the game goes on without it.

- **Kiln: a mod's items under their own ids.** Horizon's mod browser names a Java mod's items after the folders
  their models sit in (`techreborn:nugget/tin_nugget`), while the mod's code, recipes and tags name them by their ids
  (`techreborn:tin_nugget`). Kiln now adds each item under its id when the game starts, from the mod's jar: the tin
  and nickel nuggets Tech Reborn's industrial grinder makes are real items, not empty slots, and 399 more of its
  crafting, smelting and blasting recipes are back (its ores and dusts into ingots, nuggets into ingots and back, its
  tools and armour). The items the browser named after their folders are still there beside them.

- **Kiln: Tech Reborn's fluids.** A bucket of lava or water goes into a Tech Reborn machine's tank: the thermal
  generator burns lava and powers the machine beside it, and the industrial grinder and sawmill, built in their
  casings with water, grind and saw by their own recipes. A machine's screen shows its tank (the amount as a tooltip),
  and a machine placed before its casings finds them once a player places a block near it. Opening a mod's machine
  with a bucket in hand no longer pours the bucket out beside it. Tech Reborn's cells and its own fluids in the world
  aren't there yet.

- **Kiln: effects, screens, chunks, chat messages, sounds, particles and toasts for client mods.** Java mods read the
  player's effects, which screen is open (and hear it open and close), the chunks the game has (and hear them come and
  go), and the game's messages as they arrive. What they play, the game plays: sounds, particles of the game's own
  kinds, system toasts. They read the game's and the mods' resources, cut what they draw on the HUD to a box, and wrap
  long text. Tech Reborn's entity types are now registered.

- **Kiln: Tech Reborn's own recipes.** Kiln reads a mod's recipes of its own types from its jar when Java starts, with
  the mod's own code, and asks the game for their item tags. Tech Reborn's grinder grinds iron ore into raw iron, and
  1,696 of its machine recipes are there for its compressor, extractor, centrifuge, chemical reactor and the rest,
  those with fluids too. The rolling machine's aren't read yet.

- **Kiln: client commands, the player list, the pointer and the game's language.** Java mods register client
  commands (Fabric's `ClientCommandRegistrationCallback`): typed in chat, they run in Java and never reach the server.
  Mods send commands as the player, read the players the connection lists, the pointer and the mouse buttons, the
  window's size and GUI scale, the game's options and keys held, the difficulty, the biome, the blocks around the
  player and their armour value, and read text and item names in the game's language.

- **Kiln: more of the window's world and player for client mods.** Java mods read the time of day, daylight, the
  weather and the world's height, the player's food, abilities, motion, rotation and inventory, and whether the
  world is single player; text styles and colours, keys by name, the chat, HUD lines, outlines and gradients work.

- **Kiln: Tech Reborn's machines open their screens.** A right-click on a Tech Reborn machine opens its own menu,
  laid out by its own code, on Horizon's mod screens: a player puts raw iron into the electric furnace by hand and
  takes the iron ingots out. Kiln draws the machine's energy bar (its energy as a tooltip) and its progress arrow.
  Tech Reborn's own textures, its side configuration (so hoppers don't reach its machines yet) and upgrade slots are
  not there yet, and a machine's output slot takes anything you put in it.

- **Kiln: Tech Reborn's machines on energy.** A Tech Reborn solar panel in sunlight makes energy and hands it to the
  electric furnace beside it, which smelts raw iron into ingots: both machines' own code, the energy going through
  Team Reborn Energy's API as on Fabric. A mod's code on Kiln's server side now sees the sky over a block, the sun's
  angle and the weather, from the game; and a machine's slots are Fabric item storages (`ContainerStorage`), as its
  battery slot needs. Tech Reborn's electric furnace, grinder and solar panels tick without failing; the grinder has
  no recipes yet (they are Tech Reborn's own kind), and the machines still have no screens.

- **Kiln: every item's use, and the player's experience.** Java mods hear every item a player uses (Fabric's
  `UseItemCallback`), read whether the player sneaks, their experience, and the items in their hands whole (damage,
  enchantments), and change them. [Better Than Mending](https://modrinth.com/mod/better-than-mending) from Modrinth
  works: sneaking, a right click with a worn item that has Mending repairs it with the player's experience.

- **Kiln: items on the HUD, and what the player wears.** Java mods draw item icons on the HUD, scale what they draw,
  and read the armour and items the player wears and holds, with how worn each is. [Armour Durability
  HUD](https://modrinth.com/mod/armour-durablity-hud) from Modrinth works: each piece of armour's icon and what is
  left of it, in the top left. Mods' maths (`Mth`) and colours (`ARGB`) are written out now, where they gave 0.

- **Kiln: Tech Reborn's iron furnace smelts.** Its own code runs in Horizon's world: given raw iron and coal, the
  furnace lights up and makes iron ingots by the game's recipe. A mod's code on Kiln's server side now finds the game's
  cooking recipes (smelting, blasting, smoking, campfire) and fuels, from the world's data, and a machine's items stay
  in its slots whatever name its mod saves them under. Kiln has no screen for it yet, and Tech Reborn's machines take
  nothing from hoppers until their screen opens a side, so for now it is filled by command (`/data merge block`).

- **Kiln: the blocks Horizon's import leaves out.** Horizon's mod browser leaves out a mod's block when no vanilla
  block has its state properties (all of Tech Reborn's 62 machines, cables, generators and panels). Kiln now writes
  those blocks into the mod's content when the game starts, if the mod's jar is in `kiln/mods`: whole blocks with a
  block entity and the mod's own properties, models, items and loot, and its crafting recipes for them (90 for Tech
  Reborn). They are blocks in the world now; their machines' code is the next step.

- **Kiln: the window's player.** Java mods' `Minecraft.player` is the player in Horizon's window: where they stand,
  their health, game mode, name and hands, each tick. Fabric's client events for starting, joining a world, a level
  changing, the player coming into it and a level's tick (`CLIENT_STARTED`, `ClientPlayConnectionEvents`,
  `AFTER_CLIENT_LEVEL_CHANGE`, `ClientEntityEvents`, `START_LEVEL_TICK`/`END_LEVEL_TICK`) fire.
- **Kiln: for Tech Reborn's code.** Jars a mod carries inside it (Fabric's jar-in-jar) load as mods of their own;
  `Class::member` entrypoints; item components; Fabric's Transfer API (transactions, storages, item variants) and API
  lookups; network codecs; the game's NBT classes (`CompoundTag`, `NbtOps`, `TagParser`); Fabric Loader's mod list and
  environment. Team Reborn Energy starts; RebornCore and Tech Reborn go further and stop at what comes next. The
  testbench: 61 of 139 (it was 38).
- **Kiln: generated stand-ins.** `tools/kiln-stubs.py` makes stand-ins from Minecraft 26.3's and the Fabric API's
  names and signatures (no code) for what given mods use and Kiln has no hand-written stand-in for; a call to one
  does nothing, gives back a default and says so once in `kiln/java.log`. Made for Tech Reborn: its code no longer
  stops at a missing class.
- **Kiln: Tech Reborn starts.** Tech Reborn, RebornCore and Team Reborn Energy run their entry points under Kiln.
  New by hand: logs' axis (`RotatedPillarBlock`), Fabric's own events for mods (`EventFactory`), fluid and item
  storage lookups, creative tabs, network codecs that dispatch and combine up to eight fields. Generated codecs can be
  built on and fail only when used. Its machines in a world are the next step.

- **Kiln on the server: block entities, the base of machines.** A Java mod's block with a block entity
  (`EntityBlock`, `BaseEntityBlock`, Fabric's `FabricBlockEntityTypeBuilder`) gets it in Horizon's world: its ticker
  runs each server tick, what it saves (`saveAdditional`, 26.3's `ValueOutput`) is kept with the world under the
  mod's names and types, as vanilla keeps it, and the block's code reaches it with `level.getBlockEntity`. Its data
  file's block must take a template that has a block entity (a furnace, a chest). Proven with a counter of ours
  (`kiln/proof-mods/blocks`). Not yet: their menus, and what players' clients see of them.
- **Kiln on the server: machines' slots.** A mod's block entity that is a container (`Container`,
  `WorldlyContainer`, `ContainerHelper`) has its slots in Horizon's world: the game's hoppers fill and empty them
  through the faces the mod says, and the mod's code sees the items as it saved them. Proven with a crusher of ours
  between two hoppers: cobblestone in from above, gravel out below.

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
