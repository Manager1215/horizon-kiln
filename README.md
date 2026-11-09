# horizon-kiln

**Kiln runs Java mods in [Horizon](https://github.com/horizon-rust/horizon)**, the Rust rewrite of
Minecraft: Java Edition 26.3, the way Proton runs Windows games on Linux: the mod's code runs as it is, and
its calls to Minecraft's classes and the Fabric API become calls into Horizon. Nothing in a mod is decompiled,
translated or patched: the jar runs as it is.

> **Status: 0.4.1** ([install](INSTALL.md)), on Horizon Nightly 2026-11-04. Kiln runs Fabric client mods that use
> what it stands in for, their mixins applied by the Mixin library itself; structure mods' structures; and the code
> of mods Horizon's mod browser marks Partly, next to their data: Heart Crystals' items, SpongeSucc's lava sponge,
> Tech Reborn's machines on energy, their cables and their output into the next machine. Mods' own packets go
> between the player and the server, and to a LAN guest. Each Java mod has its own switch in Options > Mods, and its
> keys there. Not affiliated with Horizon, Mojang or Microsoft.

**Website:** [horizon-kiln.cc](https://horizon-kiln.cc): what Kiln runs, the mods known to work, what's new.

**Made by** Nikita Pavelko ([Manager1215](https://github.com/Manager1215)) with Egor Novikov
([GiWeFiWe](https://github.com/GiWeFiWe)).

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

## Layout

```
kiln/                      the native Horizon mod
├─ mixins/kiln/            the bundle the launcher builds into the game
├─ java/                   Kiln's Java side (kiln-runtime.jar) and its Minecraft and Fabric stand-ins
├─ lib/                    the open-source libraries Minecraft and Fabric Loader give mods: list and licenses
├─ src/, tests/            cargo test (tests/: with real Java)
├─ proof-mods/, test-mods/ small Java mods of Kiln's own: for proof.py in the game, and for tests/
├─ proof.py                Kiln in a running game, driven with horizon-ctl
└─ compatibility.json      the Horizon commit and hook points it builds on
kiln/java-gen/             stand-ins generated from Minecraft 26.3's names and signatures (tools/kiln-stubs.py)
mods/kiln-testbench/       139 probes over Minecraft and the Fabric API: Kiln's coverage, measured
spike/                     experiments: a JVM inside a Rust program, the spike's Fabric mod
tools/build-java.sh        builds target/kiln/: kiln-runtime.jar and the Java mods
tools/check-mods.py        checks every mod Kiln says works (catalogue/checks.json): pass or fail for each
tools/kiln-scan.py         what a mod's jar uses from Minecraft and Fabric that Kiln doesn't have yet
tools/kiln-stubs.py        generates stand-ins for what mods use: names and signatures only, no code
```

## Building and trying it

You need Rust, Git, Python 3.11+, rsync, JDK 25 and a clone of
[horizon-mods](https://github.com/horizon-rust/horizon-mods) for its `native/tools/native.py`:

```sh
cargo test                                            # unit tests, no game
sh tools/build-java.sh                                # target/kiln/: Kiln's Java side and the mods
cargo test -p horizon-kiln-mod -- --ignored --nocapture   # Kiln with real Java, no game; prints the testbench report
python3 -I tools/check-mods.py                            # every mod Kiln says works, each for its own feature (docs/MOD-CHECKS.md)
python3 -I tools/kiln-scan.py some-mod.jar                # what that mod needs that Kiln doesn't have
KILN_MOD=some-mod.jar cargo test -p horizon-kiln-mod --test any_mod -- --ignored --nocapture   # try it, no game
git clone https://github.com/horizon-rust/horizon-mods .deps/horizon-mods
ln -s ../../../kiln .deps/horizon-mods/native/kiln
cd .deps/horizon-mods
python3 native/tools/native.py check kiln             # apply on Horizon's source, cargo check the game
python3 native/tools/native.py proof --resources <prepared 26.3 files> kiln   # Kiln in a running game
```

## How much of Minecraft Kiln covers

`mods/kiln-testbench` is a Fabric mod with 139 small probes over Minecraft 26.3 and the Fabric API: events, the
client, the player, the level, items and blocks, text, the HUD, and the libraries Minecraft ships. Each probe is a
class of its own, so a missing piece breaks only its probe, and the report says which passed.

| | Probes that pass |
|---|---|
| Minecraft 26.3 with Fabric (`./gradlew runClientGameTest` in `mods/kiln-testbench`) | 139 of 139 |
| Horizon through Kiln, in the game (`kiln/proof.py` with the testbench in `KILN_PROOF_MODS`) | 139 of 139 (0.2.0: 38) |

Every probe answers from Horizon's game, with nothing a stand-in makes up; where Kiln can't do what vanilla does,
the probe would fail. The number only goes up: Kiln's test (`cargo test -p horizon-kiln-mod --test testbench --
--ignored --nocapture`) fails if fewer probes pass than before. Without the game it passes 136: three probes read the
game's own text and files, which the test has only with `KILN_RESOURCES` (the game's prepared resources).

To play with it, follow [INSTALL.md](INSTALL.md); `sh tools/package.sh` builds the release files into `dist/`.

## Which mods work

The mods that work with Kiln, each tried in the game, and those that should work, are on
[horizon-kiln.cc](https://horizon-kiln.cc/mods.html). That list is updated as each mod is tried, between
releases, so this repository keeps none of its own.

Structure mods work when their structures are data (pieces, pools, sets) built of vanilla blocks: Kiln hands their
data to Horizon's world generator before a world opens. Pieces with the mod's own blocks, or placed by its code, come
later.

A mod Horizon's mod browser marks **Partly** needs its jar in `kiln/mods` too: Horizon brings the data (items,
recipes, textures), Kiln runs the code. The blocks the browser leaves out (no vanilla block has their state
properties, as Tech Reborn's machines), and the items it names after their models' folders
(`techreborn:nugget/tin_nugget`, where the mod's code uses `techreborn:tin_nugget`), Kiln declares when the game
starts, from the jar, the way Horizon's own native mods declare blocks and items in code: Horizon checks them, adds
them to the mod's content, and lets a LAN guest join only with the same ones, which a guest with the same Kiln and
mods has. Kiln answers Horizon's single-player server when a player uses an item a Java
mod gave a use of its own, when a player joins, changes dimension or respawns, and when a mod's block is clicked,
ticks, is stood on, sees a neighbour change, is placed or broken (its code may read the world meanwhile); a mod's
block entities (a machine's) tick each server tick and keep their data with the world, as vanilla keeps it, and their
slots are filled and emptied by the game's hoppers, and their code finds the game's cooking recipes and fuels
(Tech Reborn's iron furnace smelts); a mod's
changes to the game's loot tables run when a world opens.

## Contributing

This repository gets each stable release. Development, beta builds and the issue triage happen in a private
repository; [CONTRIBUTING.md](CONTRIBUTING.md) says how to join, report a bug or send a change.

## License

[GPL-3.0-only](LICENSE) from 0.1.1: use it, change it and share it freely, as long as what you share stays under the
GPL with its source. Release 0.1.0-alpha was MIT and stays MIT.

The GPL is there to keep Kiln open, not to keep people away. If you'd like to use Kiln's work in your own project, under
other terms, or just talk an idea over, write: horizonkiln@gmail.com, or Discord `manager1215`. We're glad to hear from
you and happy to find a way. Contributions come under the [CLA](CLA.md), which lets the project offer such terms.

Copyright © 2026 Nikita Pavelko and the horizon-kiln contributors. The repository holds no Minecraft code, assets or
files: Kiln's stand-ins share only the names of the classes and methods mods call. Releases include the open-source
libraries Minecraft ships (Gson, Guava, fastutil, SLF4J, JOML, Brigadier, DataFixerUpper) and ASM, which Fabric Loader
ships, under their own licenses: see `kiln/lib/`. Not
affiliated with Horizon, Mojang or Microsoft.
