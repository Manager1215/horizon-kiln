# The mod catalogue

This folder goes to `catalogue/` in https://github.com/Manager1215/horizon-kiln, and
`.github/workflows/catalogue.yml` next to the repository's other workflows.

```
catalogue/classfile.py     reads class files: what Kiln's stand-ins declare, what a mod refers to
catalogue/coverage.py      writes coverage.json from kiln-runtime.jar
catalogue/scan.py          scans Modrinth and writes scan.json and site/ (the catalogue the site shows)
catalogue/probe.py         runs mods on Kiln's test stand (its Java side, a scripted session for the game); writes runs.json
catalogue/rules.json       game version, loader, which packages count, the roadmap mapping
catalogue/verified.json    mods tried in the game by hand: these override the scan
catalogue/checks.json      how each mod verified.json marks working is checked (tools/check-mods.py, docs/MOD-CHECKS.md)
.github/workflows/catalogue.yml   nightly and on release: scan, probe, publish
```

Add to `.gitignore`:

```
catalogue/cache/
catalogue/scan.json
catalogue/runs.json
catalogue/coverage.json
catalogue/site/
```

The scanner keeps what each jar needs in `catalogue/cache/` (keyed by the file's hash) and its state
in `scan.json`; GitHub Actions restores both between runs. The results go to a branch named
`catalogue`, not to `main`: `mods.json`, `mods/<id>.json`, `stats.json` and `coverage.json` at its
root, one commit per scan that changed something. Nine thousand small files have no business in `main`.

## What the scan does

1. `coverage.py` reads the compiled stand-ins (`kiln/java/`, built with `javac` and `jar`) and lists
   every class and member they declare, for the packages under `scope` in `rules.json`.
2. `scan.py` asks Modrinth for every Fabric mod with a file for the game version in `rules.json`,
   most downloaded first. For each project it takes the newest matching version's primary jar,
   downloads it once (checked against Modrinth's SHA-512), and reads its class files. No mod code runs.
3. Starting from the mod's client entry points (`fabric.mod.json`), it follows the mod's own classes
   and collects what they refer to in Minecraft, the Fabric API and Mojang's libraries. Kiln runs client
   entry points and the JVM loads classes lazily, so classes only a `main` entry point reaches do not count.
4. What is referred to but not declared by the stand-ins is the mod's "needs". Each need maps to a
   roadmap stage through the prefixes in `rules.json`, or to none.

Statuses from the scan: `planned` (every need is on the roadmap), `no-plans` (some need is not),
`not-checked` (nothing lacking as far as the scan can see, or the jar could not be read). The scan never
says `works`: that comes from `verified.json`, which wins over the scan for the same Modrinth slug.

The game's own libraries count as needs too (Gson, Guava, SLF4J, fastutil, JOML, Netty and the Mojang
libraries): Kiln's class path holds only its runtime and the mods' jars, so a mod that calls them fails to
start today. They map to roadmap stage 0.2, Minecraft's libraries.

## What changed with Kiln 0.2.0

Kiln 0.2.0 runs `main` entry points as well as `client` ones, ships Minecraft's libraries (Gson, Guava,
fastutil, SLF4J, JOML, Brigadier, DataFixerUpper) beside its runtime, and generates structure mods'
structures in Horizon's worlds. The catalogue follows:

- `coverage.py --jar kiln-runtime.jar lib/*.jar` counts the library jars as covered.
- `scan.py` follows a mod's classes from its `main` and `client` entry points, records what a jar carries
  as data (structure templates and definitions, data files, assets), and gives a mod with no Java code
  `may-work` when it has structures and `not-checked` otherwise. Server-side mods go through the scan like
  any other, with a note. `--jobs` downloads and reads files side by side; `--keep-jars <folder>` keeps
  every file where `probe.py` looks for it, so the stand need not download it again.
- `probe.py --libs lib/` puts the library jars on the class path; it loads `main` entry points first, then
  `client`, and sends the frame rate in each tick. `--shard i/n` runs every n-th mod, for several probes
  side by side; give each `--runs-out <file>` so it writes only its own records, then fold those files into `runs.json`.
- `probe.py --with-deps` puts the jars of the mods a mod depends on (`depends` in its `fabric.mod.json`,
  found among the scanned projects, three levels deep) on its class path, the way a player would put them in
  `kiln/mods`; Fabric API modules and the loader are left out, Kiln stands in for them. Nested jars inside a
  dependency are not unpacked, as Kiln does not unpack them either. `--only-needs-deps` picks the mods whose
  last run stopped at a class of another mod. A run that still stops at such a class is shown as "did not
  start without the mods it depends on", not as a gap in Kiln; a class of Minecraft's, Fabric's or Mojang's
  counts as lacking.
- `rules.json` maps Minecraft's client API (`net.minecraft.client`, `com.mojang.blaze3d`, `renderpearl`,
  Fabric's client API and loader API) to 0.2 (generated stand-ins), the rest of Minecraft and the Fabric API
  to 0.3, machines to 0.4, Mixin to 0.5. Nothing is left for a blanket rule about server-side or main-only mods.

## Running mods on Kiln's test stand

`probe.py` does what Kiln does in the game, with a scripted session in place of Horizon: it starts
`java kiln.Host` with the runtime and the mod's jar, sends the font widths, asks for each entry point, then
1,200 client ticks: the title screen, a new world, an advancement, a key press (J), the Nether and back, a
pause, and leaving the world. In the world there is a player, as Kiln's tick carries the window's player: in
survival at full health, holding an iron pickaxe and a torch, wearing a worn iron helmet, an iron chestplate
and worn leather boots (a Kiln before the window's player, 0.2.0, ignores them). It records whether the entry
points started, errors during ticks, whether anything was drawn on the HUD, the mod's own output (its stderr),
and the calls it made that Kiln has only the name of (`name_only`: a generated stand-in, which does nothing and
says so once in Kiln's log).

The mod's own code runs for real, in a real JVM; only the game is scripted. So a pass shows that the mod starts
and nothing breaks for a minute of play, not that each of its features shows in the game: a feature may need a
world, items or a server the session doesn't have, or reach a stand-in that does nothing.

A passing run that reached nothing Kiln has only the name of makes a mod without Mixin `may-work`, shown as
"Should work": an automatic test ran it and nothing broke, so it most likely works, and nobody has checked it
in the game by hand (that is "Works"). The run's facts are its evidence. A passing run that did reach such calls
counts them as lacking, like the scan's own needs (planned, or no plans), with a line saying that those parts
may not work. (A run on Kiln 0.2.0 can't reach any: 0.2.0 has no generated stand-ins, so a missing class stops
the run instead.) A mod with Mixin keeps its status (Kiln does not apply Mixins, so part of it is missing) and
gets the run as a line of evidence. A failing run names what the mod tripped over
(`NoClassDefFoundError: com/google/gson/JsonElement`, say), and scan.py counts that class as lacking, mapped to
the roadmap like anything the scan finds. Mods without an entry point are not run. Results live in `runs.json`,
keyed by the file's hash, so a file runs once.

Mod code runs on the CI runner: the probe job can only read the repository, and runs Java in a namespace
without network when the runner allows `unshare -rn`. For a look at one mod:

```sh
python3 catalogue/probe.py --runtime target/kiln/kiln-runtime.jar --libs target/kiln/lib --jar some-mod.jar
```

The scan is conservative: a mod can call something Kiln lacks on a path that never runs. Show FPS 2.0.0
refers to its settings screen and `Minecraft.getFps()`, which the stand-ins lack, and its FPS counter
still works; that is why the verified list exists and why the statuses say "parts of it may already run".

## Running it by hand

```sh
sh tools/build-java.sh                   # or just javac + jar as in the workflow
python3 catalogue/coverage.py --jar target/kiln/kiln-runtime.jar target/kiln/lib/*.jar --out catalogue/coverage.json --kiln 0.2.0
python3 catalogue/scan.py --kiln 0.2.0 --limit 50            # the fifty most downloaded, for a look
python3 catalogue/scan.py --kiln 0.2.0 --keep-jars jars      # everything (about an hour the first time)
python3 catalogue/probe.py --runtime target/kiln/kiln-runtime.jar --libs target/kiln/lib --all --cache jars --limit 20
python3 catalogue/probe.py --runtime target/kiln/kiln-runtime.jar --libs target/kiln/lib --all --cache jars --with-deps --only-needs-deps   # again, with dependencies
python3 catalogue/scan.py --offline                          # rebuild site/ after editing rules, verified or runs
```

The summary at the end lists the symbols most mods lack, counted across the catalogue: a measure of
what the next stand-ins should be.

## Two channels: the release and main

The site shows two layers for every mod (D-070): what the latest public release does, and what main does today.
`scan.py --channel release` (the default) is the release layer: the stand-ins of the released runtime, hand checks
against a released version. `scan.py --channel dev` is the development layer: main's runtime (with `kiln/java-gen`),
and hand checks on main too. `--kiln` names the build the layer belongs to ("0.2.0", or "development build
2026-11-04 (ccef3e4)"); it goes into every evidence line and the index.

## A release's support list

`support-list.py` writes `docs/MODS.md` from the catalogue scanned against a release (its `--out` folder, release
channel): the mods that work (tried in the game), those whose code ran on the stand with nothing failed and nothing
reached that Kiln has only the name of, and those that are only data (structures), each a table of its own, never
added up into one number (D-078). README quotes the three counts of the latest release.

```sh
python3 -I catalogue/support-list.py --site <scan.py --out folder> --kiln 0.3.0 --out docs/MODS.md
```

## Adding a mod you tried in the game

A mod marked `works` or `in-development` needs its checks in `catalogue/checks.json` too: what its feature is, its
pinned download, a check without the game that asserts the feature, and its proof in the game.
`tools/check-mods.py` runs them all and fails for a working mod without them (docs/MOD-CHECKS.md).

Append to `catalogue/verified.json`: `id`, `name`, `version`, `status` (`works`, `doesnt-work`,
`in-development`), `works` (one or two sentences, what you saw), `note`, `url`, and `checked`
(`kiln`, optionally `date` and `by`). `checked.kiln` says which layer the check belongs to: a released version
(`0.2.0`) counts in both layers; `main` with `commit` and `date` counts in the development layer only; an entry
with no `checked` (a mod Kiln is being built with, status `in-development`) shows in both. At a release, change
`main` to the release's version for the mods it carries. Give `modrinth` the project's slug when it is on Modrinth,
so the scan's entry for it takes your text and status. Push to main: the workflow rebuilds `site/`.

## Where the site takes it from

The `catalogue` branch: `mods.json` is the site's index and `mods/<id>.json` its mod pages, in exactly
the format the site already reads; `stats.json` has the counts and the symbols mods lack most often.
The site's own workflow checks that branch out and deploys it; reports from players are kept on the
site's server and merged in the browser. The publish job can tell the site at once: add a secret
`SITE_DISPATCH_TOKEN` here, a fine-grained GitHub token for the site repository with "Contents: read and
write" (repository_dispatch needs it). Without it the site still picks the catalogue up every morning.

## What the index row says

`status` plus facts the site turns into a line: `lacks` (how many things Kiln does not cover; for
`no-plans` only those off the roadmap), `stage` (the roadmap stages the needs map to), `candidate`
(nothing lacking, not run yet), `library` (Modrinth lists it as a library), and for verified mods
`works` and `note`.
