# horizon-kiln

[Русский](README.ru.md)

**Kiln runs Java mods in [Horizon](https://github.com/horizon-rust/horizon)**, the Rust rewrite of
Minecraft: Java Edition 26.3, the way Proton runs Windows games on Linux: the mod's code runs as it is, and
its calls to Minecraft's classes and the Fabric API become calls into Horizon.

> **Status: pre-alpha.** Nothing runs yet. Kiln is an empty native mod while we find out how Java code can
> run inside Horizon. Not affiliated with Horizon, Mojang or Microsoft.

## The idea

1. Install **Kiln** once from the Horizon launcher. Like every native Horizon mod, the launcher builds it
   into your game once (20 to 30 minutes, after a warning).
2. Put `.jar` mods into Kiln's folder.
3. Play.

The first Java mod meant to run this way is our own speedrun timer: a Fabric mod for Minecraft 26.3 that
also runs, unchanged, in Horizon through Kiln. See [PROTON.md](PROTON.md) for the plan, the risks and what
we have learned about Horizon's mod API, and [DECISIONS.md](DECISIONS.md) for every decision and why.

## Layout

```
kiln/                      the native Horizon mod
├─ mixins/kiln/            the bundle the launcher builds into the game
├─ java/                   Kiln's Java side (kiln-runtime.jar): runs in a process of its own
├─ src/, tests/            cargo test (tests/: with real Java)
├─ proof.py                Kiln in a running game, driven with horizon-ctl
└─ compatibility.json      the Horizon commit and hook points it builds on
mods/kiln-timer/           the speedrun timer: a Fabric mod for Minecraft 26.3 (Gradle)
spike/                     experiments: a JVM inside a Rust program, the spike's Java mod
tools/build-java.sh        builds target/kiln/: kiln-runtime.jar and the spike's mod
```

In the game, Kiln reads `<game folder>/kiln/kiln-runtime.jar` and the Java mods in `<game folder>/kiln/mods/`.

## Building

You need Rust, Git, Python 3.11+ and rsync, and a clone of
[horizon-mods](https://github.com/horizon-rust/horizon-mods) for its `native/tools/native.py`:

```sh
cargo test                                            # unit tests, no game
git clone https://github.com/horizon-rust/horizon-mods .deps/horizon-mods
ln -s ../../../kiln .deps/horizon-mods/native/kiln
cd .deps/horizon-mods
python3 native/tools/native.py check kiln             # apply on Horizon's source, cargo check the game
python3 native/tools/native.py build kiln             # build the game with Kiln (fast profile)
```

## License

MIT. The repository holds no Minecraft code, assets or files.
