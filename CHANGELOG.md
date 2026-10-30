# Changelog

## Unreleased

- **Kiln:** a stuck Java mod no longer freezes the game: commands go to Java through a bounded queue, and
  Kiln stops Java with a line in chat when a mod hasn't read them for about a minute.
- **Kiln:** a mod calling a class or method Kiln has no stand-in for (`NoSuchMethodError`,
  `NoClassDefFoundError`) no longer stops Java and every other mod; the error is logged.
- **Kiln:** picks a Java at least as new as the mods' classes need (Java 25 for mods built for 26.3), not
  just any Java 21+ (D-021).
- **Kiln:** names the keypad's divide, subtract and decimal keys, and Menu.
- **Timer:** leaving the world after a finished run resets the timer; before, the next world repeated the old
  run's final time in chat, recorded it again and didn't start a new run.
- `tools/package.sh` falls back to `sha256sum` where `shasum` is missing.
- Documents: fixes to README, INSTALL, PROTON and DECISIONS (build time, manual finish, keys under Kiln).

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
- Project documents: DECISIONS.md, PROTON.md, README (en, ru).
