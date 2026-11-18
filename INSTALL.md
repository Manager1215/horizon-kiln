# Installing horizon-kiln 0.4.6

Tested on Linux with Horizon Nightly 2026-11-04 · 3fb4c31. Since 0.4.5 Java runs inside the game, and that, the
overlay and the crash window have not run on macOS or Windows yet (Kiln 0.2.0 ran on an Apple Silicon Mac and on
Windows 11). If something below doesn't go as it says on your system, tell us:
[issues](https://github.com/Manager1215/horizon-kiln/issues).

## What's in the archive

| | |
|---|---|
| `kiln-native/` | Kiln, the native mod (an unpacked `.hnmod`) the launcher builds into the game |
| `kiln-0.4.6.hnmod` | the same, as one file |
| `kiln/` | for the game folder: `kiln-runtime.jar` (Kiln's Java side), `lib/` (open-source libraries mods use, with their licenses) and an empty `mods/` for the Java mods you add |

## Horizon + Kiln

You need the Horizon launcher with 26.3 prepared, and Java 25. Kiln finds it by itself (`JAVA_HOME`, the Minecraft
launcher's Java, installed JDKs); if you have none, install one, for example Temurin 25.

1. **Read Kiln's code.** Native mods run without a sandbox, with your rights; the launcher builds them only after you
   explicitly agree. The source is in `kiln-native/mixins/kiln/` (and in this repository).
2. **Build Kiln into the game** (in Terminal; the path is to the unpacked archive):
   ```sh
   /Applications/Horizon.app/Contents/MacOS/horizon-launcher native-build ~/Downloads/horizon-kiln-0.4.6/kiln-native --trust-native-code
   ```
   On Windows, in PowerShell, in the folder you unpacked the launcher into (or its shortcut > Open file location).
   Windows' Extract All puts the archive one folder deeper, so the path has its name twice; if you unpacked it
   another way, give the folder that holds `kiln-native`:
   ```powershell
   .\horizon-launcher.exe native-build "$env:USERPROFILE\Downloads\horizon-kiln-0.4.6\horizon-kiln-0.4.6\kiln-native" --trust-native-code
   ```
   The first time, the launcher downloads its own Rust (about 450 MB; on Windows MinGW too) and builds the game (10 to
   30 minutes); later builds take minutes. The launcher builds Kiln into the instance you play (in the launcher:
   "Native build · …"), with its worlds and options; older launchers made a separate **Native mods** instance. It
   builds on that instance's Nightly: add `--version nightly-2026-11-04-3fb4c31` to build on the one Kiln is tested on.
3. **Play that instance** and enter any world. Kiln names its folder in chat (for Horizon's own instance on Windows,
   `%APPDATA%\horizon\kiln`):
   `Kiln: no Java mods yet. Put .jar mods in …/kiln/mods and kiln-runtime.jar in …/kiln`. Quit the game.
4. **Copy** the contents of the archive's `kiln/` folder (`kiln-runtime.jar`, `lib/` and the empty `mods/`) into that `…/kiln` folder.
5. **Add mods:** Kiln comes with none. Put Java mods' `.jar` files in `…/kiln/mods`: Fabric mods for 26.3 that Kiln
   runs ([horizon-kiln.cc](https://horizon-kiln.cc/mods.html)).
6. **Play:** Options > Mods lists Kiln as "Kiln 0.4.6", and below it each Java mod in `…/kiln/mods` with a switch of
   its own: a mod turned off is left out from the next world you enter. A mod's page there lists its keys once it has
   run in a world: click one to choose another key.
7. **Kiln's overlay:** in a world, press **F7** (or type `/kiln` in the chat, or Options > Mods > Kiln > "Java
   mods..."). It lists your Java mods, whether each runs and whether turning it off stops it now; its switches turn
   a mod off at once. The Crashes tab lists what stopped or hung the game. **Restart Java now** starts the mods' Java
   again with the switches as they are, without leaving the world; the first Java stays in the game's memory until
   you close the game, and a banner says so. Choose another key for it on Kiln's page in Options > Mods.

### On a Mac

Not tried by us yet with 0.4.6 (see the top): these are the steps as they should go.

- **Java 25:** the Temurin 25 installer (`.pkg`) for your Mac from adoptium.net (Apple Silicon: aarch64; Intel:
  x64), or `brew install --cask temurin` (the newest, 25 or later). Kiln finds it in
  `/Library/Java/JavaVirtualMachines`, `~/Library/Java/JavaVirtualMachines` and Homebrew's `openjdk`.
- **F7:** a Mac keyboard's top row controls the Mac (F7 is the previous track) unless you hold **fn**: press
  **fn+F7**, or type `/kiln` in the chat, or bind another key on Kiln's page in Options > Mods.
- **Kiln's folder** is under the launcher's data folder (chat names it when you enter a world). In Finder, Go > Go
  to Folder (Cmd+Shift+G) opens a path; the overlay's "Open the mods' folder" opens `…/kiln/mods`.
- **If the game closes when you enter a world** (Java inside the game didn't start): write `process` in a file named
  `jvm` in Kiln's folder (beside `kiln-runtime.jar`), and Kiln starts Java as a program of its own, as 0.4.1 did;
  delete the file to go back. Tell us what `…/kiln/java.log` and the game's log say.
- **If the game crashes,** a small window "Kiln: the game stopped" names the mod and asks whether to start without
  it; it may open behind other windows (look in the Dock for "Kiln").

### If something goes wrong

- "Kiln: no Java 25 or newer found. Install Temurin 25 (adoptium.net), or write the path to your Java in
  …/horizon-mods/.horizon-mod-data/kiln/java_home": Kiln found no Java new enough for the mods (they need 25),
  and chat names your own `java_home` file. Install Java 25, for example Temurin 25 from adoptium.net, or point Kiln
  at one: that file holding the path to Java (the folder that holds `bin/java`, `bin\java.exe` on Windows), or the
  environment variable `KILN_JAVA_HOME`.
- "Kiln: no Java at …. …/java_home should hold the path to the folder that holds bin/java": the `java_home` file (or
  `KILN_JAVA_HOME`, when chat names it) points at a folder with no Java in it. Write in it the folder that holds
  `bin/java`, not `bin` or the `java` program itself: for example `/usr/lib/jvm/java-25-openjdk` on Linux,
  `/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home` on a Mac, or
  `C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot` on Windows.
- A mod didn't start: the reason is in chat, in the overlay (F7) and in `…/kiln/java.log`.
- Java inside the game can't start on your computer (the game's log says "Java runs in a process of its own"): Kiln
  runs it as a program of its own instead, and the mods work as in 0.4.1. To ask for that yourself, write `process` in a file
  named `jvm` in Kiln's folder, or set `KILN_JVM=process`.
- You can turn Kiln off in Options > Mods: it stops at once and leaves the build with the next one. A Java mod's own
  switch there (or in the overlay) stops the mod at once where it can (the overlay says "off now: yes" or "partly"),
  and leaves it out from the next game start; Restart Java now in the overlay does that without leaving the world.
- The build fails on another Horizon version: add `--version nightly-2026-11-04-3fb4c31` to the command (if that
  build is still installed in the launcher); Kiln 0.4.6 is tested on it, as 0.4.5, 0.4.1, 0.4.0 and 0.3.0 were. Kiln
  0.2.0 needs Nightly 2026-11-01 and doesn't build on newer ones.
- On Windows the build stops with "os error 3" or "os error 32" (a file not found, or in use): run the same command
  again; it goes on from where it stopped.

### Other Java mods

- **Fabric client mods** that use what Kiln stands in for: put the jar in `…/kiln/mods`. Known to run:
  [Show FPS](https://modrinth.com/mod/showfps) 2.0.0.
- **Structure mods** whose structures are data: put the jar in `…/kiln/mods`; new worlds generate their structures.
  Known to run: [Illager Warship](https://modrinth.com/mod/illager-warship) 1.0.0.
- **Mods Horizon's mod browser marks Partly:** install the mod in the launcher's mod browser (its data), and put the
  same jar in `…/kiln/mods` (its code). Known to run: [Heart Crystals](https://modrinth.com/mod/heart-crystals) and
  [SpongeSucc (Lava Sponge)](https://modrinth.com/mod/spongesucc), whose lava sponge takes up lava.
- **Client mods from Modrinth** known to run: [Armour Durability HUD](https://modrinth.com/mod/armour-durablity-hud)
  and [Better Than Mending](https://modrinth.com/mod/better-than-mending), each jar in `…/kiln/mods`.
- `tools/kiln-scan.py` in this repository says what a jar needs that Kiln doesn't have yet.
