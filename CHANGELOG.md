# Changelog

## Unreleased

- Kiln 0.0.2: a native Horizon mod that runs Java mods. On entering a world it starts Java in a process of its
  own (`java kiln.Host`), loads `<game folder>/kiln/mods/*.jar` with Kiln's Java classes, runs each mod's client
  entrypoint from its `fabric.mod.json`, sends it every game tick and shows the HUD line the mods set. Proven in
  Horizon with the spike's Java mod (`kiln/proof.py`).
- Kiln Speedrun Timer: the Fabric mod's project for Minecraft 26.3 and its core (timer, Any% splits, IGT without
  pauses, personal best, HUD rows) with unit tests. Not wired to the game yet.
- Spike: a JVM inside a Rust program through hand-written JNI (`spike/jvm-embed`). It works, but Horizon forbids
  `unsafe` code, so Kiln can't do this inside the game.
- Project documents: DECISIONS.md, PROTON.md, README (en, ru).
