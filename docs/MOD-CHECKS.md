# Checking the mods Kiln says work

A mod Kiln says works must keep working. Each one is checked on its own, for two things: that nothing crashes, and
that the mod's own feature works (Show FPS's counter follows the frame rate and changes colour; Better Than
Mending's pickaxe is repaired and the player's experience spent), not only that the mod starts.

## The list

- `catalogue/verified.json` says which mods work: those marked `works` (tried in the game, on a release or on main:
  its `checked`, D-070) or `in-development` (Kiln is being built with it). The site shows this list.
- `catalogue/checks.json` says how each of them is checked:
  - `feature`: what the mod is for, in a sentence;
  - `jar`: where the jar comes from, pinned (a download and its SHA-256; jars never go into the repository), or
    built here (our timer);
  - `checks`: without the game, the tests in `kiln/tests/` that run its jar under Kiln with real Java and assert its
    feature, and the lines they must print;
  - `proof`: in the game, the settings for `kiln/proof.py` with that mod, what its screenshots show, and the numbers
    proof.py reads off them (how much the part of the screen the feature changes did change).
  - `known`: problems Kiln's log shows for it that aren't fixed yet, each a pattern of the line and why (Tech
    Reborn's client entrypoint: `ItemModels.ID_MAPPER` is a generated stand-in, null). They are reported, not
    failed; one no longer seen is reported too, so its entry can go. Anything else in the log fails the mod.
- A mod marked working without an entry in `checks.json` fails the run. So does an entry for a mod not marked
  working, and a download in `tools/stub-mods.txt` that no entry pins.

## One command

```sh
sh tools/build-java.sh
python3 -I tools/check-mods.py
```

It downloads each jar into a cache outside the repository (`~/Library/Caches/horizon-kiln-checks` on macOS,
`~/.cache/horizon-kiln-checks` elsewhere, or `KILN_CHECK_CACHE`), checks its SHA-256, and runs for each mod:

1. **Without the game:** its checks with its jar alone (`kiln/tests/coverage.rs` prints `check <id>: passed`;
   `kiln/tests/tech_reborn.rs` has a test for each of Tech Reborn's machines).
2. **Crashes:** what Kiln logged meanwhile must not say that a call failed in Java, that a HUD element or a client
   tick threw, that a mod didn't start (`2 of 3 Java mods running`), that a block entity's ticker failed or that
   Java stopped. Calls that reached a stand-in Kiln has only the name of are listed (they did nothing), not failed.
3. **Together:** every mod's jar in one game, as a player has them (Tech Reborn's too): every one must start, and
   every check of `coverage.rs` runs again.

A failed mod's checks, and a failed run of them together, run once more: on a computer busy with other work Kiln's
Java can answer late (Kiln waits a quarter of a second for an answer). The second run counts, and the report says
the first one failed, with its error and logs (`*.first.log`): a check that passes only the second time is unstable.

It prints a table, pass or fail for each mod with the first error, and writes `report.txt`, `report.json` and every
log beside the downloads (`reports/<time>/`). The exit status is 1 when anything failed. `--only showfps,tech-reborn`
checks some; `--no-together` skips step 3.

## In the game, in the background

The game can't run on GitHub's machines. On a computer with Horizon, `--proof` also runs `kiln/proof.py` with each
mod after its checks:

```sh
sh tools/build-java.sh
nohup python3 -I tools/check-mods.py --proof > ~/Library/Logs/kiln-mod-checks.log 2>&1 &
```

- The game runs offscreen: no window, nothing takes the keyboard or the screen, so you can keep working. It uses the
  GPU for a few minutes per mod. On a busy computer the game answers late and the proof's own blocks, timed in
  seconds, tick fewer times than they should: a failed proof runs once more, and the report says so with the first
  error (a proof that passes only the second time is unstable). Before each proof the runner waits, up to 20
  minutes, for the computer's load to drop under its number of cores, and the report gives the load it started at.
- `--proof` alone takes the prepared resources of the newest game the launcher installed; `--proof PATH` names them
  (`.../horizon-launcher/versions/<version>/.local/horizon-client/26.3`).
- It needs the clone of horizon-mods the README describes (`.deps/horizon-mods`, its `native/kiln` linked to this
  repository's `kiln/`; another with `--horizon-mods`). The first proof builds the game with Kiln (several minutes),
  the next ones only what changed. One run at a time per clone: the proof writes into its `native/target/proof/`.
- Heart Crystals, SpongeSucc and Tech Reborn need what Horizon's import makes of their data, as a player installs
  them through Horizon's mod browser. The runner makes it with `horizon-port-pack check <jar> <out>` (built with the
  game by the first proof), once per jar, in the cache; `--content ID=FOLDER` gives a folder of your own instead.
- The report lists, for each mod, the numbers proof.py read off its screenshots, and copies the screenshots beside
  it (`reports/<time>/<mod>/`). Look at them, not only at the numbers: a screenshot shows what a number can miss (the
  feature in the wrong place, a missing texture, an error in chat). They show game textures, so they stay there,
  never in the repository.

## On GitHub

`.github/workflows/mod-checks.yml` runs the part without the game on GitHub's Linux machines: `cargo test`,
`sh tools/build-java.sh`, Kiln's tests with real Java and `tools/check-mods.py`. The table goes to the run's summary,
the logs to its artifact (`mod-checks`). It has no schedule.

A run started by hand (Actions, Mod checks, Run workflow) always runs. On pull requests that touch Kiln, the mods,
the catalogue's lists or the tools it runs only once the owner turns it on, as this repository is private and each
run takes Actions minutes from the account's monthly allowance:
`gh variable set MOD_CHECKS --body on --repo Manager1215/horizon-kiln-dev` (off:
`gh variable delete MOD_CHECKS --repo Manager1215/horizon-kiln-dev`).

## Adding a mod

When a mod starts working under Kiln:

1. Write its check in `kiln/tests/coverage.rs`: a function that drives the mod as the game would and asserts what it
   is for, run from `working_mods_work` with `check("<id>", "<jar name's start>", ...)`. A mod with a world of its
   own (Tech Reborn's machines) gets a test file of its own instead.
2. Add it to `catalogue/checks.json`: its feature, its download and SHA-256, its checks, and its proof (what to set
   for `kiln/proof.py`, and what on screen shows its feature; add that to proof.py if it isn't there).
3. Mark it in `catalogue/verified.json`, and run `python3 -I tools/check-mods.py --only <id> --proof`.
