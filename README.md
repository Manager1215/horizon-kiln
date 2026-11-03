# horizon-kiln

**Kiln runs Java mods in [Horizon](https://github.com/horizon-rust/horizon)**, the Rust rewrite of
Minecraft: Java Edition 26.3, the way Proton runs Windows games on Linux: the mod's code runs as it is, and
its calls to Minecraft's classes and the Fabric API become calls into Horizon.

> **Status: 0.2.0** ([install](INSTALL.md)). Kiln runs Fabric client mods that use what it stands in for (our
> speedrun timer, Show FPS from Modrinth), structure mods' structures, and the code of mods Horizon's mod browser
> marks Partly, next to their data (Heart Crystals). Not affiliated with Horizon, Mojang or Microsoft, and not an
> official speedrun.com timer.

## The idea

1. Install **Kiln** once. Like every native Horizon mod, the launcher builds it into your game once, after a
   warning (it says 20 to 30 minutes; the alpha's first build took about 10 on an Apple Silicon Mac).
2. Put `.jar` mods into `<game folder>/kiln/mods/`.
3. Play.

Kiln starts Java beside the game (Horizon forbids unsafe code, so not inside it), runs each mod's client
entrypoint from its `fabric.mod.json`, and from then on gives it a client tick 20 times a second with the
game's state, the keys you press and the advancements you make, and draws what it draws on the HUD.
[docs/VISION.md](docs/VISION.md) has how it works, the risks and what we learned about Horizon and Minecraft 26.3;
[ROADMAP.md](ROADMAP.md) has where it goes next: the goal is Tech Reborn, an industrial mod, running unchanged.

### Kiln and Horizon's mod browser

Horizon's launcher installs Java mods by carrying their data (blocks, items, recipes, structures) into a Horizon
content mod, and marks a mod whose features need its Java code as **Partly**. Kiln runs that code. The two are
meant to work together: Horizon brings a mod's data, Kiln its code.

## Kiln Speedrun Timer

A Fabric mod for Minecraft 26.3 (`mods/kiln-timer`), and the same jar runs in Horizon through Kiln. Both are
tested: `./gradlew runClientGameTest` plays a run in Minecraft with Fabric, `kiln/proof.py` in Horizon.

- **Any%** with automatic splits: Nether, Bastion, Fortress, Blind, Stronghold, End, Finish. They come from
  advancements and dimensions; each happens once, in any order.
- **RTA** and **IGT**: IGT is real time without the pause menu and loading screens, as SpeedRunIGT counts it.
- **Start:** by itself in a new world; in an old one, with the split key. Leaving the world resets the timer: an
  unfinished run is dropped (its splits still count for the best splits), a finished one is already saved.
- **Finish:** the dragon ("Free the End", the default), back in the Overworld from the End, or by hand
  (`manual`: the split key, once every other split is reached; each press reaches the next one in order).
- **Personal best** and best splits, with deltas to the PB (green ahead, red behind); "PB!" when you beat it.
- **Keys:** J start / split, K reset, U hide the panel.
- **Settings:** `<config>/kiln_timer/settings.properties`: `corner` (`top_left`, `top_right`, `bottom_left`,
  `bottom_right`), `finish` (`dragon`, `leave_end`, `manual`), `shown`, `chat_splits`. Records:
  `records.txt` beside it. Under Kiln the config folder is `<game folder>/kiln/config`.
- **Limits:** single player (advancements come from the single-player server); the panel updates 20 times a
  second; no chat command yet; under Kiln the keys can't be rebound (Kiln listens for the default ones); not a
  speedrun.com-approved timer.

## Layout

```
kiln/                      the native Horizon mod
├─ mixins/kiln/            the bundle the launcher builds into the game
├─ java/                   Kiln's Java side (kiln-runtime.jar) and its Minecraft and Fabric stand-ins
├─ lib/                    the open-source libraries Minecraft ships, which Kiln gives mods: list and licenses
├─ src/, tests/            cargo test (tests/: with real Java)
├─ proof.py                Kiln and the timer in a running game, driven with horizon-ctl
└─ compatibility.json      the Horizon commit and hook points it builds on
mods/kiln-timer/           the speedrun timer: a Fabric mod for Minecraft 26.3 (Gradle)
mods/kiln-testbench/       139 probes over Minecraft and the Fabric API: Kiln's coverage, measured
spike/                     experiments: a JVM inside a Rust program, the spike's Fabric mod
tools/build-java.sh        builds target/kiln/: kiln-runtime.jar and the Java mods
tools/kiln-scan.py         what a mod's jar uses from Minecraft and Fabric that Kiln doesn't have yet
```

## Building and trying it

You need Rust, Git, Python 3.11+, rsync, JDK 25 and a clone of
[horizon-mods](https://github.com/horizon-rust/horizon-mods) for its `native/tools/native.py`:

```sh
cargo test                                            # unit tests, no game
sh tools/build-java.sh                                # target/kiln/: Kiln's Java side and the mods
cargo test -p horizon-kiln-mod -- --ignored --nocapture   # Kiln with real Java, no game; prints the testbench report
python3 -I tools/kiln-scan.py some-mod.jar                # what that mod needs that Kiln doesn't have
KILN_MOD=some-mod.jar cargo test -p horizon-kiln-mod --test any_mod -- --ignored --nocapture   # try it, no game
git clone https://github.com/horizon-rust/horizon-mods .deps/horizon-mods
ln -s ../../../kiln .deps/horizon-mods/native/kiln
cd .deps/horizon-mods
python3 native/tools/native.py check kiln             # apply on Horizon's source, cargo check the game
python3 native/tools/native.py proof --resources <prepared 26.3 files> kiln   # the timer in a running game
```

## How much of Minecraft Kiln covers

`mods/kiln-testbench` is a Fabric mod with 139 small probes over Minecraft 26.3 and the Fabric API: events, the
client, the player, the level, items and blocks, text, the HUD, and the libraries Minecraft ships. Each probe is a
class of its own, so a missing piece breaks only its probe, and the report says which passed.

| | Probes that pass |
|---|---|
| Minecraft 26.3 with Fabric (`./gradlew runClientGameTest` in `mods/kiln-testbench`) | 139 of 139 |
| Horizon through Kiln (`cargo test -p horizon-kiln-mod --test testbench -- --ignored --nocapture`) | 38 of 139 (0.1.1: 18) |

The number only goes up: the Kiln test fails if fewer probes pass than before. The report says 49; 11 aren't counted:
9 read the window's player (position, health, hands), whose values Kiln doesn't send Java yet, `registry.size` answers
false (Kiln's registries hold the mods' entries and the game's it has met, not all of the game's), and
`level.blockKey` reads air (Kiln's window side knows no blocks yet; its server side does).

To play with it, follow [INSTALL.md](INSTALL.md); `sh tools/package.sh` builds the release files into `dist/`.

## Mods known to run

Downloaded as they are, run through Kiln in Horizon (`kiln/proof.py`; others with `KILN_PROOF_MODS`):

| Mod | Version | What works | Not yet |
|---|---|---|---|
| Kiln Speedrun Timer (ours) | 0.2.0 | everything | its chat command |
| [Show FPS](https://modrinth.com/mod/showfps) | 2.0.0 | the FPS counter, its colours and background, its settings file | its settings screen (N): Kiln shows no mods' screens yet |
| [Illager Warship](https://modrinth.com/mod/illager-warship) | 1.0.0 | its warship generates in the oceans of new worlds | |
| [Heart Crystals](https://modrinth.com/mod/heart-crystals) | for 26.3 | with its data from Horizon's mod browser: a heart crystal raises the player's maximum health, is spent, plays its sound, says "Heart limit reached" at the limit; each player's hearts are kept with the world, set up when they join and given back when they respawn | crystal shards in chests: its own loot code adds none on 26.3, on Fabric too |

Structure mods work when their structures are data (pieces, pools, sets) built of vanilla blocks: Kiln hands their
data to Horizon's world generator before a world opens. Pieces with the mod's own blocks, or placed by its code, come
later.

A mod Horizon's mod browser marks **Partly** needs its jar in `kiln/mods` too: Horizon brings the data (items,
recipes, textures), Kiln runs the code. Kiln answers Horizon's single-player server when a player uses an item a Java
mod gave a use of its own, when a player joins, changes dimension or respawns, and when a mod's block is clicked,
ticks, is stood on, sees a neighbour change, is placed or broken (its code may read the world meanwhile); a mod's
changes to the game's loot tables run when a world opens.

## Contributing

This repository gets each stable release. Development, beta builds and the issue triage happen in a private
repository; [CONTRIBUTING.md](CONTRIBUTING.md) says how to join, report a bug or send a change.

## License

[GPL-3.0-only](LICENSE) from 0.1.1: use it, change it and share it freely, as long as what you share stays under the
GPL with its source. To use Kiln's code under other terms (in a project with another license, say), ask the
maintainer. Contributions come under the [CLA](CLA.md), which allows that. Release 0.1.0-alpha was MIT and stays
MIT.

Copyright © 2026 Nikita Pavelko and the horizon-kiln contributors. The repository holds no Minecraft code, assets or
files: Kiln's stand-ins share only the names of the classes and methods mods call. Releases include the open-source
libraries Minecraft ships (Gson, Guava, fastutil, SLF4J, JOML, Brigadier, DataFixerUpper), under their own licenses:
see `kiln/lib/`. Not
affiliated with Horizon, Mojang or Microsoft.
