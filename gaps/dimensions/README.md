# dimensions: a data pack's levels

**Status:** Horizon does this itself since commit d083cc60 (2026-11-07). The module has no code: on Nightlies with
that commit there is nothing to fill, and on older ones it can't be filled from a mixin (below). What is left is
flat generation in those levels ("Still missing").

## What Horizon lacked

Game version 26.3, Horizon Nightly 2026-11-04 (3fb4c31).

A data pack (or a mod's data) adds a dimension with `data/<namespace>/dimension/<path>.json`, its level stem: a
dimension type and a generator. In the vanilla game the server opens it as one more level, and
`/execute in <namespace>:<path> run ...` reaches it. On this Nightly the server has exactly three levels, the
Overworld, the Nether and the End, and the command answers `Unknown dimension '<namespace>:<path>'`. The data pack's
stems are read only to decide whether the world asks the "experimental settings" question when it opens.

Mods seen stopping here: Superflat Dimension, Amplified Dimension, Desert Dimension, Large Biomes Dimension, The Void
Dimension and Mushroom Fields Dimension (each adds `<mod>:overworld` with a vanilla generator).

## Why a mixin can't fill it on that Nightly

A native mod's injection runs code at the head of one of the game's functions and may cancel it. The server's level
key there is a closed set of the three vanilla levels: every per-level table (the levels not in place, the tick
order, the save folder, the generator, the client's sky) is keyed by it, and any other id falls back to the
Overworld. An injection can't add a fourth key, and the parts that open a level are private to the module that
holds the levels. Hosting a pack's level in one of the three slots would stop the real level from ticking and mix
the two saves, which no module here should do.

## How it is filled now

By Horizon: d083cc60 ("Open a data pack's dimensions as levels") opens a world's enabled data pack's levels as the
Nether and the End are opened: they tick, close and save (in `dimensions/<namespace>/<path>/`), a player saved in
one comes back there, `execute in` reaches them, and the pack's dimension type goes to clients in the registry. Its
own notes say only noise generators and generators a native mod registered work there, and the sky and the fog are
the Overworld's.

## How to turn it on

Nothing to turn on: use a Horizon Nightly that contains d083cc60, and put the dimension's files in a data pack the
world has enabled.

## Test

None in this module, which has no code. Horizon's own tests cover d083cc60.

## Still missing

- **Flat levels.** A stem whose generator is `minecraft:flat` (Superflat Dimension, The Void Dimension) gets no
  terrain in a pack's level. A native mod may register a generator for those levels; a flat generator registered
  that way is this module's next work, written and tested against a Nightly that has d083cc60.
- **The sky and the fog** of a pack's level are the Overworld's, whatever its dimension type says.
