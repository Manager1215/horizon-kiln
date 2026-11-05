# Installing horizon-kiln 0.3.0

Tested on macOS (Apple Silicon) with Horizon Nightly 2026-11-04 · 3fb4c31; 0.2.0 on Nightly 2026-11-01 also on Windows 11.

## What's in the archive

| | |
|---|---|
| `kiln-native/` | Kiln, the native mod (an unpacked `.hnmod`) the launcher builds into the game |
| `kiln-0.3.0.hnmod` | the same, as one file |
| `kiln/` | for the game folder: `kiln-runtime.jar` (Kiln's Java side), `lib/` (open-source libraries mods use, with their licenses) and `mods/kiln-timer-0.2.0.jar` (the timer) |
| `kiln-timer-0.2.0.jar` (separate in the release) | the same timer for Minecraft 26.3 with Fabric |

## Horizon + Kiln + the timer

You need the Horizon launcher with 26.3 prepared, and Java 25. Kiln finds it by itself (`JAVA_HOME`, the Minecraft
launcher's Java, installed JDKs); if you have none, install one, for example Temurin 25.

1. **Read Kiln's code.** Native mods run without a sandbox, with your rights; the launcher builds them only after you
   explicitly agree. The source is in `kiln-native/mixins/kiln/` (and in this repository).
2. **Build Kiln into the game** (in Terminal; the path is to the unpacked archive):
   ```sh
   /Applications/Horizon.app/Contents/MacOS/horizon-launcher native-build ~/Downloads/horizon-kiln-0.3.0/kiln-native --trust-native-code
   ```
   On Windows, in PowerShell, in the folder you unpacked the launcher into (or its shortcut > Open file location).
   Windows' Extract All puts the archive one folder deeper, so the path has its name twice; if you unpacked it
   another way, give the folder that holds `kiln-native`:
   ```powershell
   .\horizon-launcher.exe native-build "$env:USERPROFILE\Downloads\horizon-kiln-0.3.0\horizon-kiln-0.3.0\kiln-native" --trust-native-code
   ```
   The first time, the launcher downloads its own Rust (about 450 MB; on Windows MinGW too) and builds the game (10 to
   30 minutes); later builds take minutes. The launcher builds Kiln into the instance you play (in the launcher:
   "Native build · …"), with its worlds and options; older launchers made a separate **Native mods** instance. It
   builds on that instance's Nightly: add `--version nightly-2026-11-04-3fb4c31` to build on the one Kiln is tested on.
3. **Play that instance** and enter any world. Kiln names its folder in chat (for Horizon's own instance on Windows,
   `%APPDATA%\horizon\kiln`):
   `Kiln: no Java mods yet. Put .jar mods in …/kiln/mods and kiln-runtime.jar in …/kiln`. Quit the game.
4. **Copy** the contents of the archive's `kiln/` folder (`kiln-runtime.jar`, `lib/` and `mods/`) into that `…/kiln` folder.
5. **Play:** in a new world the timer starts by itself. Options > Mods lists Kiln as "Kiln 0.3.0", and below it
   each Java mod in `…/kiln/mods` with a switch of its own: a mod turned off is left out from the next world you enter.

### The timer

- Keys: **J** start / split, **K** reset, **U** hide the panel.
- Splits: Nether, Bastion, Fortress, Blind, Stronghold, End, Finish, by themselves, from advancements and dimensions.
- Settings: `…/kiln/config/kiln_timer/settings.properties`
  (`corner=top_left|top_right|bottom_left|bottom_right`, `finish=dragon|leave_end|manual`, `shown=true|false`,
  `chat_splits=true|false`). Records: `records.txt` beside it. A fresh install has no settings file: write one with
  the lines you want to change, for example `corner=top_right`.
- Single player only. Not approved by speedrun.com.

### If something goes wrong

- "Kiln: no Java 25 or newer found": Kiln found no Java new enough for the mods (the timer needs 25). Install
  Java 25 or point Kiln at it: the file `horizon-mods/.horizon-mod-data/kiln/java_home` holding the path to Java (the
  folder that holds `bin/java`), or the environment variable `KILN_JAVA_HOME`.
- A mod didn't start: the reason is in chat and in `…/kiln/java.log`.
- You can turn Kiln off in Options > Mods: it stops at once and leaves the build with the next one. A Java mod's own
  switch there leaves that mod out: back on the title screen Kiln stops Java, and the next world starts it without it.
- The build fails on another Horizon version: add `--version nightly-2026-11-04-3fb4c31` to the command (if that
  build is still installed in the launcher); Kiln 0.3.0 is tested on it. Kiln 0.2.0 needs Nightly 2026-11-01 and
  doesn't build on newer ones.
- On Windows the build stops with "os error 3" or "os error 32" (a file not found, or in use): run the same command
  again; it goes on from where it stopped.

### Other Java mods

- **Fabric client mods** that use what Kiln stands in for: put the jar in `…/kiln/mods`. Known to run:
  [Show FPS](https://modrinth.com/mod/showfps) 2.0.0 (it draws in the top-left corner, where the timer's panel is
  by default: move the timer with `corner=top_right`).
- **Structure mods** whose structures are data: put the jar in `…/kiln/mods`; new worlds generate their structures.
  Known to run: [Illager Warship](https://modrinth.com/mod/illager-warship) 1.0.0.
- **Mods Horizon's mod browser marks Partly:** install the mod in the launcher's mod browser (its data), and put the
  same jar in `…/kiln/mods` (its code). Known to run: [Heart Crystals](https://modrinth.com/mod/heart-crystals) and
  [SpongeSucc (Lava Sponge)](https://modrinth.com/mod/spongesucc), whose lava sponge takes up lava.
- **Client mods from Modrinth** known to run: [Armour Durability HUD](https://modrinth.com/mod/armour-durablity-hud)
  and [Better Than Mending](https://modrinth.com/mod/better-than-mending), each jar in `…/kiln/mods`.
- `tools/kiln-scan.py` in this repository says what a jar needs that Kiln doesn't have yet.

## Just the timer, in Minecraft

`kiln-timer-0.2.0.jar` is an ordinary Fabric mod: Minecraft 26.3, Fabric Loader 0.20.1+, Fabric API. Put it
into your Fabric instance's `mods/` folder.
