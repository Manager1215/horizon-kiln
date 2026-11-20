# Kiln user guide

For players: install Kiln, add Java mods, run JEI, read Kiln's overlay and know what to do after a crash. This page
is the short path; [INSTALL.md](../INSTALL.md) has every command and every error message, and the website
[horizon-kiln.cc](https://horizon-kiln.cc/mods.html) lists which mods are known to work.

Tested on Linux only. Java inside the game and the overlay have not run on macOS or Windows yet; if a step doesn't go
as written on your system, tell us in the issues of the public repository.

## 1. Install

You need the Horizon launcher with Minecraft 26.3 prepared, and Java 25 (Kiln finds an installed one by itself; if
you have none, install Temurin 25 from adoptium.net).

1. Unpack the release archive. Read `kiln-native/mixins/kiln/` if you wish: native mods run with your rights, and
   the launcher builds one only after you agree.
2. Build Kiln into the game, once (10 to 30 minutes the first time):
   `horizon-launcher native-build <path to the unpacked kiln-native> --trust-native-code`. INSTALL.md has the exact
   command for each system and the `--version` to use.
3. Play that instance and enter any world. Kiln says in chat where its folder is (`…/kiln`) and that it has no Java
   mods yet. Quit the game.
4. Copy the contents of the archive's `kiln/` folder (`kiln-runtime.jar`, `lib/`, an empty `mods/`) into that
   `…/kiln` folder.
5. Put Java mods (Fabric mods for 26.3 as `.jar` files) into `…/kiln/mods`. Kiln comes with none. A mod Horizon's
   mod browser marks **Partly** needs both: install it in the browser (its data) and put the same jar in
   `…/kiln/mods` (its code).
6. Play. Options > Mods lists "Kiln" and, below it, each Java mod with a switch of its own. A mod turned off there is
   left out from the next world you enter.

## 2. Run JEI

Just Enough Items is a Fabric mod like any other: put its jar (the build for 26.3) in `…/kiln/mods` and enter a
world. Its item list shows beside the inventory, in the creative tabs' order, with a search box.

- **Search:** click the box and type. Select all, copy, paste and the shift keys work; so do JEI's `@` (mod), `#`
  (tooltip), `$` (tooltip lines) and `%` search words.
- **Recipes and uses:** point at an item and press **R** (how it is made) or **U** (what it is used in), or click it.
  The recipe screen has tabs, pages and arrows (the mouse wheel turns them); **Escape** closes it.
- **A** bookmarks the item under the pointer. Bookmarks and the lookup history are kept for each world.
- **The "+" button** on a recipe moves its items from your inventory into the crafting grid, a furnace, the smithing
  table or another station; Shift does as many as fit.
- JEI's keys can be changed on its page in Options > Mods (a mod's page lists its keys once it has run in a world).
- JEI's settings screen opens and saves, but needs the mod Mezz Config GUI too, as under Fabric.

Not yet: dragging ingredients out of JEI and its cheat mode don't work in every case, and fluids draw nothing in some
recipe slots (the mod's catalogue entry says what is open: `catalogue/tried.json`, entry `jei`).

## 3. Read the overlay

In a world, press **F7** (on a Mac keyboard **fn+F7**), type `/kiln` in the chat, or use Options > Mods > Kiln >
"Java mods...". The same key closes it. You can bind another key on Kiln's page in Options > Mods.

The list shows each Java mod in `kiln/mods` with its version and:

- **its state:** "running", "running (mixins only)", "content only" (data, no code), "not started yet", "off", "on
  from the next start", or why it didn't start ("needs Cloth Config": that library is missing; put its jar in
  `kiln/mods` too);
- **whether its switch stops it now:** "off now: yes", "off now: partly" (its blocks and items stay in the world
  until the next game), "fully off after a restart" or "nothing to stop";
- **whether the site tried it:** "tried: works" or "not tried";
- **its switch,** ON or OFF: the same as in Options > Mods, and it acts at once where it can.

Tabs, with their counts:

- **Mods:** every Java mod.
- **Can turn off now:** the running mods whose switch stops them this minute.
- **Crashes:** what stopped or hung the game, newest first, with the mod's switch beside it. A row says "stopped the
  game", "hung the game", "ran Java out of memory" or "overflowed Java's stack", and which mod. **Copy** puts a report
  (the mod, what happened, Kiln's call, what Java said, and the tail of the Java log) onto the clipboard, ready to
  send; **Open the log** opens Kiln's folder.

Under the list: **Search**, **Open the mods' folder**, **Restart Java now** and **Done**. Restart Java now starts the
mods' Java again with the switches as they are, without leaving the world: a mod turned on starts, a mod turned off is
gone fully. It asks for a second click, because Java inside the game can't be made twice: the new Java runs as a
program of its own and the first one stays idle in the game's memory until you close the game. The overlay says "2
Javas: restart the game when it suits you" until then. Nothing is broken.

## 4. After a crash

1. A small window, "Kiln: the game stopped", names the mod and asks whether to start without it. It may open behind
   other windows (look for "Kiln" in the Dock or taskbar). The title screen of the next start offers the same: a
   **safe mode** that starts the game once with no Java mods. The switches stay as they are, and the start after it has
   the mods again.
2. In the game, open the overlay's **Crashes** tab to see the mod and what happened. Turn the mod OFF, or remove its
   jar from `kiln/mods`.
3. To report it: **Copy** the crash and paste it into an issue, with the mod's name and version. The log is
   `…/kiln/java.log`; the game's own log may help too.
4. If the game closes when you enter a world (Java inside the game didn't start), write `process` in a file named
   `jvm` in Kiln's folder: Kiln then starts Java as a program of its own. Delete the file to go back.

If a mod doesn't start, the reason is in chat, in the overlay and in `…/kiln/java.log`.
