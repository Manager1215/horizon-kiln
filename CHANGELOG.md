# Changelog

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
