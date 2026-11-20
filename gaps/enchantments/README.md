# enchantments: a mod's and a data pack's enchantments

**Status:** filled by this module on Horizon Nightly 2026-11-04 (3fb4c31). Horizon does it itself since 4aeb14b3
(2026-11-07, a world's packs' enchantments) and b7078824 (2026-11-14, every namespace of the resources, and the
synchronised registry): on a Nightly with both, switch the module off.

## What Horizon lacks

Game version 26.3, Horizon Nightly 2026-11-04 (3fb4c31).

Since 1.21 an enchantment is data: `data/<namespace>/enchantment/<path>.json` names its levels, costs, items, slots
and effects (`minecraft:attributes`, `minecraft:damage`, ...). In the vanilla game the server's registry holds every
such file of every namespace, from the game's data and from a world's data packs, a pack's file replacing the
game's of the same id.

On this Nightly the registry reads only `data/minecraft/enchantment/*.json` of the game's resources:

- a content mod's own enchantments (`data/<its namespace>/enchantment/`, which Horizon's import does carry into the
  resources) are not in it: `/enchant @s <mod>:<name>` answers that the enchantment isn't found, the enchanting table
  never offers it, and a book or item that has it shows nothing and does nothing;
- a world's data pack's enchantments are not read at all: the pack loader doesn't gather the `enchantment/` folder,
  so a new one is unknown and a replaced vanilla one (a pack's `data/minecraft/enchantment/lunge.json`) keeps the
  game's values. The packs' enchantment *tags* do join, which makes the gap easy to miss.

Mods seen stopping here: Happy Ghast Overdrive (a new enchantment, `aerial_swiftness`, `minecraft:attributes` on a
harness) and Directional Lunge (a replaced `minecraft:lunge`).

## How the module fills it

Two hooks and two support modules, no change to Horizon's source:

- `gap_enchantment_files.rs`, a child module of `horizon_client::game::functions`, with a RETURN hook on
  `content_overlay`: each enabled pack's `data/<ns>/enchantment/**.json` joins the files the game's tables load from
  as `enchantment/<ns>:<path>`, in pack order, above the resources' own files in other namespaces than `minecraft` (a
  content mod's), so a world's tables get built and carry them;
- `gap_enchantments.rs`, a public child module of `horizon_items::enchant`, called by a HEAD hook on `native_tick`
  (`gap_enchantments_hook.rs`): once a world's tables are built (its first open, each `/reload`), their registry is
  replaced by Horizon's own load plus the resources' other namespaces and the overlay's files (the last pack's file
  for an id), each read as Horizon reads the game's own (costs, items, `exclusive_set` and its tag, slots, effects);
  a file replaces an entry of the same id, the registry stays sorted by id, and a mod's names come from its
  `assets/<ns>/lang/en_us.json`. The functions that build the registry can't take a hook that changes their result on
  this Nightly's mixin tool (they return `Self`, or use `?`, closures or logging macros), hence the tick.

The registry is what `/enchant`, the enchanting table, the anvil, loot functions, tooltips and the effect code read,
so an added enchantment takes part wherever Horizon evaluates an effect of that kind.

## How to turn it on

Only on a Nightly without 4aeb14b3 and b7078824 (3fb4c31 and older); on a newer one, Horizon's own loading does
this and more, and the hooks would read the same files again.

This folder is a native-mod bundle (`horizon-mixin.toml`): build it into the game with Horizon's native-mod tools
like any other (`native.py build`, from the horizon-mods repository). It changes nothing for a world without mods'
or packs' enchantments. [horizon-kiln](https://github.com/Manager1215/horizon-kiln) carries the same files in its
own bundle; with Kiln in, don't add this one too (it would do the same work twice, harmlessly).

## Test

Unit tests inside the two modules, run by `cargo test -p horizon-items gap_enchantments` and
`cargo test -p horizon-client gap_enchantment_files` in the game's workspace with the bundle applied. The first adds
to a registry from a resources folder with a mod's namespace and its English file and an overlay with two packs' files
for one id; the second goes through the `content_overlay` hook with a world of three packs, one disabled.

## Still missing

- **Commands.** `/enchant` checks its argument against the command layer's own list of enchantment ids, the
  game's only, so it still refuses a mod's enchantment (seen in the game, 2026-11-19); the registry has it.

- **Effects Horizon doesn't evaluate.** The registry entry is complete, but an effect kind Horizon has no code for
  does nothing, for a game enchantment as for a mod's. Directional Lunge's `minecraft:lunge` uses
  `minecraft:post_piercing_attack` with `minecraft:apply_impulse`, which this Nightly doesn't run from data; a
  `minecraft:attributes` effect on a slot other than the player's own (a happy ghast's `body`) is applied only where
  Horizon applies enchantment attributes.
- **A client of another server.** The registry synchronised to a LAN guest or a vanilla client lists only the game's
  own enchantments (Horizon sends a pack's entries for `minecraft:dialog` only); this module changes the server's
  registry, not that list.
- **Languages other than English** for a mod's names (only `en_us.json` is read, as Horizon reads the game's).
