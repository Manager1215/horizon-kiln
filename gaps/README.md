# horizon-gaps

Parts of Minecraft: Java Edition 26.3 that [Horizon](https://github.com/horizon-rust/horizon) doesn't do yet, filled
from outside Horizon as native-mod mixins, under the MIT license (`LICENSE`). Anyone may use them: Horizon's
developers, other mod authors, players. Nothing here changes Horizon's source or asks anything of its maintainers.

They are written for [horizon-kiln](https://github.com/Manager1215/horizon-kiln), which runs Java (Fabric) mods in
Horizon, wherever a mod stops at something Horizon itself lacks. Each module is meant to be useful without Kiln.

## Layout

One folder per gap. Its `README.md` is the module's note:

- **What Horizon lacks**, by behaviour, with the game version and the Horizon Nightly it was seen on;
- **how the module fills it**;
- **how to turn it on**;
- **its test**;
- **what is still missing**.

Every source file starts with an MIT header (`SPDX-License-Identifier: MIT`). No Horizon, Minecraft or Mojang files
are copied here. When Horizon does a gap itself, the module is switched off for that Nightly and its note says since
which commit.

## Modules

| Module | Gap | Status |
|---|---|---|
| [`dimensions`](dimensions/README.md) | A data pack's or a mod's dimensions don't open as levels | Horizon's own since d083cc60 (2026-11-07); flat generators in those levels still missing |
| [`enchantments`](enchantments/README.md) | A mod's own namespace's and a world's data packs' enchantments aren't in the registry | Filled here for 3fb4c31; Horizon's own since 4aeb14b3 and b7078824 (2026-11-07, 2026-11-14) |
