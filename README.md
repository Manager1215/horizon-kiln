# horizon-kiln

[Русский](README.ru.md)

**Kiln runs Java mods in [Horizon](https://github.com/horizon-rust/horizon)**, the Rust rewrite of
Minecraft: Java Edition 26.3, the way Proton runs Windows games on Linux: the mod's code runs as it is, and
its calls to Minecraft's classes and the Fabric API become calls into Horizon.

> **Status: alpha 0.1.0** ([install](INSTALL.md)). Kiln runs Fabric client mods that use the few classes it stands in for; its first one
> is our own speedrun timer. Not affiliated with Horizon, Mojang or Microsoft, and not an official
> speedrun.com timer.

## The idea

1. Install **Kiln** once. Like every native Horizon mod, the launcher builds it into your game once (20 to 30
   minutes, after a warning).
2. Put `.jar` mods into `<game folder>/kiln/mods/`.
3. Play.

Kiln starts Java beside the game (Horizon forbids unsafe code, so not inside it), runs each mod's client
entrypoint from its `fabric.mod.json`, and from then on gives it a client tick 20 times a second with the
game's state, the keys you press and the advancements you make, and draws what it draws on the HUD.
[PROTON.md](PROTON.md) has the plan, the risks and what we learned about Horizon and Minecraft 26.3;
[DECISIONS.md](DECISIONS.md) has every decision and why.

## Kiln Speedrun Timer

A Fabric mod for Minecraft 26.3 (`mods/kiln-timer`), and the same jar runs in Horizon through Kiln. Both are
tested: `./gradlew runClientGameTest` plays a run in Minecraft with Fabric, `kiln/proof.py` in Horizon.

- **Any%** with automatic splits: Nether, Bastion, Fortress, Blind, Stronghold, End, Finish. They come from
  advancements and dimensions; each happens once, in any order.
- **RTA** and **IGT**: IGT is real time without the pause menu and loading screens, as SpeedRunIGT counts it.
- **Start:** by itself in a new world; in an old one, with the split key. Leaving the world ends an unfinished
  run.
- **Finish:** the dragon ("Free the End", the default), back in the Overworld from the End, or by hand.
- **Personal best** and best splits, with deltas to the PB (green ahead, red behind); "PB!" when you beat it.
- **Keys:** J start / split, K reset, U hide the panel.
- **Settings:** `<config>/kiln_timer/settings.properties`: `corner` (`top_left`, `top_right`, `bottom_left`,
  `bottom_right`), `finish` (`dragon`, `leave_end`, `manual`), `shown`, `chat_splits`. Records:
  `records.txt` beside it. Under Kiln the config folder is `<game folder>/kiln/config`.
- **Limits:** single player (advancements come from the single-player server); the panel updates 20 times a
  second; no chat command yet; not a speedrun.com-approved timer.

## Layout

```
kiln/                      the native Horizon mod
├─ mixins/kiln/            the bundle the launcher builds into the game
├─ java/                   Kiln's Java side (kiln-runtime.jar) and its Minecraft and Fabric stand-ins
├─ src/, tests/            cargo test (tests/: with real Java)
├─ proof.py                Kiln and the timer in a running game, driven with horizon-ctl
└─ compatibility.json      the Horizon commit and hook points it builds on
mods/kiln-timer/           the speedrun timer: a Fabric mod for Minecraft 26.3 (Gradle)
spike/                     experiments: a JVM inside a Rust program, the spike's Fabric mod
tools/build-java.sh        builds target/kiln/: kiln-runtime.jar and the Java mods
```

## Building and trying it

You need Rust, Git, Python 3.11+, rsync, JDK 25 and a clone of
[horizon-mods](https://github.com/horizon-rust/horizon-mods) for its `native/tools/native.py`:

```sh
cargo test                                            # unit tests, no game
sh tools/build-java.sh                                # target/kiln/: Kiln's Java side and the mods
cargo test -p horizon-kiln-mod -- --ignored           # Kiln with real Java, no game
git clone https://github.com/horizon-rust/horizon-mods .deps/horizon-mods
ln -s ../../../kiln .deps/horizon-mods/native/kiln
cd .deps/horizon-mods
python3 native/tools/native.py check kiln             # apply on Horizon's source, cargo check the game
python3 native/tools/native.py proof --resources <prepared 26.3 files> kiln   # the timer in a running game
```

To play with it, follow [INSTALL.md](INSTALL.md); `sh tools/package.sh` builds the release files into `dist/`.

## License

MIT. The repository holds no Minecraft code, assets or files: Kiln's stand-ins share only the names of the
classes and methods mods call.
