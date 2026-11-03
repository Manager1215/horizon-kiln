# Installing horizon-kiln 0.1.1-alpha

Alpha: tested on macOS (Apple Silicon) with Horizon Nightly 2026-11-01 · c9d474d; not tried on Windows or Linux.

## What's in the archive

| | |
|---|---|
| `kiln-native/` | Kiln, the native mod (an unpacked `.hnmod`) the launcher builds into the game |
| `kiln-0.1.1.hnmod` | the same, as one file |
| `kiln/` | for the game folder: `kiln-runtime.jar` (Kiln's Java side) and `mods/kiln-timer-0.1.1-alpha.jar` (the timer) |
| `kiln-timer-0.1.1-alpha.jar` (separate in the release) | the same timer for Minecraft 26.3 with Fabric |

## Horizon + Kiln + the timer

You need the Horizon launcher with 26.3 prepared, and Java 25. Kiln finds it by itself (`JAVA_HOME`, the Minecraft
launcher's Java, installed JDKs); if you have none, install one, for example Temurin 25.

1. **Read Kiln's code.** Native mods run without a sandbox, with your rights; the launcher builds them only after you
   explicitly agree. The source is in `kiln-native/mixins/kiln/` (and in this repository).
2. **Build Kiln into the game** (in Terminal; the path is to the unpacked archive):
   ```sh
   /Applications/Horizon.app/Contents/MacOS/horizon-launcher native-build ~/Downloads/horizon-kiln-0.1.1-alpha/kiln-native --trust-native-code
   ```
   The first time, the launcher downloads its own Rust (about 450 MB) and builds the game (about ten minutes); later
   builds take minutes. The game with Kiln shows up in the launcher as the **Native mods** instance, with its own
   worlds and options.
3. **Play Native mods** and enter any world. Kiln names its folder in chat:
   `Kiln: no Java mods yet. Put .jar mods in …/kiln/mods and kiln-runtime.jar in …/kiln`. Quit the game.
4. **Copy** the contents of the archive's `kiln/` folder (`kiln-runtime.jar` and `mods/`) into that `…/kiln` folder.
5. **Play:** in a new world the timer starts by itself. Options > Mods lists Kiln as "Kiln 0.1.1".

### The timer

- Keys: **J** start / split, **K** reset, **U** hide the panel.
- Splits: Nether, Bastion, Fortress, Blind, Stronghold, End, Finish, by themselves, from advancements and dimensions.
- Settings: `…/kiln/config/kiln_timer/settings.properties`
  (`corner=top_left|top_right|bottom_left|bottom_right`, `finish=dragon|leave_end|manual`, `shown=true|false`,
  `chat_splits=true|false`). Records: `records.txt` beside it.
- Single player only. Not approved by speedrun.com.

### If something goes wrong

- "Kiln: no Java 25 or newer found": Kiln found no Java new enough for the mods (the timer needs 25). Install
  Java 25 or point Kiln at it: the file `horizon-mods/.horizon-mod-data/kiln/java_home` holding the path to Java (the
  folder that holds `bin/java`), or the environment variable `KILN_JAVA_HOME`.
- A mod didn't start: the reason is in chat and in `…/kiln/java.log`.
- You can turn Kiln off in Options > Mods: it stops at once and leaves the build with the next one.
- The build fails on another Horizon version: add `--version nightly-2026-11-01-c9d474d` to the command (if that
  build is still installed in the launcher); Kiln 0.1.1 is tested on it.

## Just the timer, in Minecraft

`kiln-timer-0.1.1-alpha.jar` is an ordinary Fabric mod: Minecraft 26.3, Fabric Loader 0.20.1+, Fabric API. Put it
into your Fabric instance's `mods/` folder.
