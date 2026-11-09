"""Kiln in a running modded game, with Kiln's runtime and its Java mods in <game folder>/kiln/: the spike's mod and,
where mods/kiln-timer is (public releases leave it out, D-097), the speedrun timer, the same jar Gradle builds against
Minecraft 26.3. Entering a new world starts Java and the mods; the timer starts by itself; advancements granted by
command, trips between dimensions and a pause make its splits, the dragon's advancement finishes the run, and the
finish is a personal best (screenshots and chat). Without the timer the same steps run, and its checks are left out.

    sh tools/build-java.sh
    cd .deps/horizon-mods && python3 native/tools/native.py proof --resources PATH kiln

KILN_PROOF_MODS (jars, separated as in PATH) adds other mods: the proof checks they start too, and 1-running.png
shows what they draw. For each structure they define, `/locate` must find it in the world and the blocks there must
include those of its pieces (5-<structure>.png); what it holds around it (chests, barrels, spawners, and the mobs its
pieces carry) is counted, and KILN_PROOF_STRUCTURE_HAS (`chest>=3,minecraft:pillager>=10`) names what it must hold.

Kiln's proof mods (kiln/proof-mods/, target/kiln/proof/) run too, with their data for Horizon (their horizon/): a
mod's loot change puts a nether star in every simple dungeon chest, so five rolls of the table with `/loot` give five;
a mod's switch turns on and off when clicked, saying what it stands on, and on when stood on; a mod's sprout, on
farmland, ripens (`ripe`, which vanilla's wheat hasn't) by its own random tick, which stands in for its template's
(its `age` stays 0); a mod's timer, placed from the hand, faces the player, lights by its scheduled tick a second
later, goes out when a block is set beside it, and says it is broken (dropping a nether star) when broken; a mod's
counter, on a furnace's template, gets the mod's block entity, which counts the server's ticks, lights the block at
40 and keeps its count in the world's data (`/data get block`), and a click reads it, says the count and starts again;
a mod's crusher between two of the game's hoppers takes cobblestone from the one above into its block entity's slot
and gives gravel to the one below.

Kiln's server side (D-052): KILN_PROOF_CONTENT names the folders Horizon's import made of those mods' data
(`horizon-port-pack check <jar> <out>`, its <out>/carry), installed in the game and turned on; KILN_PROOF_CONFIG a
folder copied into <game folder>/kiln/config (the Java mods' settings); KILN_PROOF_USE items (comma-separated) a
player in survival is given three of and uses one at a time. Each use's answer from the mod is the server's: what the
player's maximum health, the item's count and the action bar say after each, and the hearts and the first hotbar slot
must change on screen (6-before-<item>.png against 6-<item>.png, the chat hidden); KILN_PROOF_USE_SAID is what one of
the uses must say over the hotbar. Then the player dies and respawns: their maximum health after it must be the last
use's. The mods' saved data right after the player joined is in the results too, and no call to Java may have
failed.

Blocks Horizon's import leaves out (D-055): with KILN_PROOF_ONLY=carry, KILN_PROOF_CONTENT an import's content mod
and KILN_PROOF_MODS its jar, each block in KILN_PROOF_CARRIED (comma-separated ids) is set beside the player and must
be there (7-carried.png); Kiln must have said it carried them. Nothing else runs then. With KILN_PROOF_SMELT (a
carried furnace, `techreborn:iron_furnace`, whose slots are as Tech Reborn's: 0 the input, 1 the output, 2 the fuel),
that furnace is then set facing the player and given two raw iron and a coal by `/data merge` (it has no screen in
Kiln yet): its mod's code must light it (8-smelting.png) and smelt both into iron ingots, by the game's recipe. With
KILN_PROOF_POWER (an electric furnace and a solar panel, `techreborn:electric_furnace,techreborn:basic_solar_panel`),
at noon in clear weather the furnace is set with the panel beside it under the open sky and given two raw iron: the
panel's code must see the sky and the sun and make energy (`active`), its energy must reach the furnace through the
mods' own energy API, and the furnace must smelt both into iron ingots (9-powered.png). With KILN_PROOF_SCREEN (the
same pair), a player in survival with two raw iron right-clicks the furnace: its mod's code opens its menu, laid out
as its code lays it out, with its energy bar and progress arrow (10-screen.png); the player puts the raw iron into
its input by hand, and takes the two iron ingots it smelts from its output with a shift-click. With KILN_PROOF_GRIND
(a grinder and a solar panel, `techreborn:grinder,techreborn:basic_solar_panel`), the same with two iron ore in the
grinder: by its mod's own recipe (`techreborn:grinder`, read from its jar), they become four raw iron (11-grinder.png).
With KILN_PROOF_FLUIDS (a thermal generator and an industrial grinder, `techreborn:thermal_generator,
techreborn:industrial_grinder`), the grinder's casings and the water inside them are set by command, then the grinder,
the generator beside it; a player in survival clicks the generator holding a lava bucket, which opens its screen and
pours nothing, puts the lava in by hand (its tank fills and it burns: 12-thermal.png); once the grinder has charged
from it, a water bucket and an iron ore into the grinder through its screen (13-industrial-grinder.png): on the
generator's energy, by its own recipe with the water, the ore becomes three raw iron, three tin nuggets and three
nickel nuggets, items of Tech Reborn's that Kiln declares under the ids its code makes them by (D-074), which the player
takes out (14-ground.png). With KILN_PROOF_CABLE (an electric furnace, a solar panel and a cable,
`techreborn:electric_furnace,techreborn:basic_solar_panel,techreborn:copper_cable`), at noon the furnace, two cables
and the panel are set in a row and the furnace given two raw iron: the panel's energy must come through both cables,
whose network ticks on Fabric's START_SERVER_TICK, and the furnace smelt both (15-cable.png). With KILN_PROOF_TRANSFER
(a grinder, an electric furnace and a solar panel, `techreborn:grinder,techreborn:electric_furnace,
techreborn:basic_solar_panel`), the grinder with the furnace east of it and a panel beside each, its output set to go
east by itself and the furnace's input to come from the west, as their screens would set them, and two iron ore in the
grinder: its four raw iron must go into the furnace by Fabric's ItemStorage and come out as four iron ingots, none left
in the grinder (16-transfer.png).

A mod's own payloads (D-088): with KILN_PROOF_ONLY=net, the player types /kiln_proof_net, Kiln's proof mod's command:
its ping goes to the server over Horizon's channel for mods, whose receiver tells the player in chat and answers with a
pong that comes back into the chat (19-net.png); then the world opens to LAN, a second game joins, and its ping and
pong cross the network the same way, the pong counting the host's server's second ping (20-net-guest.png). The
server's players and connections (D-092): each player gets the mod's welcome as they join (Fabric's JOIN), with how
many are on the server; the host hears the guest join, ping near them and leave. With Saro's Sticky Leaves in
KILN_PROOF_MODS, the config its server sends each joining player reaches its client, the host's and the guest's.
Nothing else runs then.

Mods from Modrinth (tools/stub-mods.txt): with KILN_PROOF_ONLY=mods, each of these whose jar is in
KILN_PROOF_MODS does what it does in the game. Armour Durability HUD: the player looks at the ground and puts on worn
armour by command; its icons and percentages must appear in the top left (9-armour.png, against
9-before-armour.png). Better Than Mending: on a platform in the sky, with 10 levels and a diamond pickaxe with Mending
worn by 300, the player right-clicks the air while sneaking; the pickaxe must be repaired with experience, and the
experience level over the hotbar must change on screen (10-before-mending.png, 10-mended.png), and less standing
(10-standing.png). Show FPS: looking at the sky, its counter top left, gone with the HUD hidden (F1): the top left must
change between the two (12-fps.png, 12-fps-hidden.png). Kiln's
testbench (target/kiln/testbench): its report in the game, at least 100 of 139 probes answering, and the window's
world and player read from the game (the world's height, the player's food, the inventory). Nothing else runs then.

A mod tried by itself (D-084): with KILN_PROOF_ONLY=try and its jar in KILN_PROOF_MODS (its import in
KILN_PROOF_CONTENT if it has one), every Java mod must start, Kiln must log no failed call, nothing it threw and no
mod that didn't start, and java.log no uncaught exception; the calls that reached what Kiln has only the name of are
listed (they did nothing: that part of the mod doesn't work yet). Each structure its jar defines must be located and stood
in (5-<structure>.png, at least `pieces` blocks of its pieces, 100 by default). KILN_PROOF_TRY (JSON) says the rest:
`steps`, what the player does, each a list (["command", "give @s minecraft:stick"], ["key", "c"] or
["key", "shift", "down"], ["type", "/calc 2+2"] in the chat, ["use"], ["select", 1], ["look", 0, 90],
["wait", 1.5], ["shot", "name"], ["hud", "name"] with the chat hidden); `expect`, what must show:
{"said": regex} in the chat or the action bar, {"logged": regex} in the game's log, {"changed": ["a", "b"],
"box": [left, top, right, bottom], "over": n} between two screenshots (try-<name>.png), {"same": ...} for under n.
"""
import gzip
import io
import json
import math
import os
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import time
import tomllib
import zipfile
from pathlib import Path

from game import Game, difference

BUILT = Path(__file__).resolve().parent.parent / "target" / "kiln"
PROOF_MODS = sorted((BUILT / "proof").glob("*.jar"))
# The mods tools/build-java.sh builds into the game: the spike's, and the timer where mods/kiln-timer is.
BUILT_MODS = sorted((BUILT / "mods").glob("*.jar"))
TIMER = any(j.name.startswith("kiln-timer-") for j in BUILT_MODS)
# Their data for Horizon, as its import would carry it (kiln/proof-mods/<name>/horizon/).
PROOF_CONTENT = sorted(p.parent for p in (BUILT.parent.parent / "kiln" / "proof-mods").glob("*/horizon/*.toml"))
EXTRA = [Path(p) for p in os.environ.get("KILN_PROOF_MODS", "").split(os.pathsep) if p]
CONTENT = [Path(p) for p in os.environ.get("KILN_PROOF_CONTENT", "").split(os.pathsep) if p]
CONFIG = os.environ.get("KILN_PROOF_CONFIG")
USE = [i for i in os.environ.get("KILN_PROOF_USE", "").split(",") if i]
# What one of the uses must say over the hotbar (Heart Crystals at its limit: "Heart limit reached").
USE_SAID = os.environ.get("KILN_PROOF_USE_SAID")
# What a structure must hold around it, at least: blocks (chest, barrel, spawner) and mobs (minecraft:pillager),
# `name>=count`, comma-separated.
STRUCTURE_HAS = dict((k, int(n)) for k, n in (i.split(">=") for i in
                     os.environ.get("KILN_PROOF_STRUCTURE_HAS", "").split(",") if i))
CARRIED = [b for b in os.environ.get("KILN_PROOF_CARRIED", "").split(",") if b]
ONLY = os.environ.get("KILN_PROOF_ONLY")
SMELT = os.environ.get("KILN_PROOF_SMELT")
POWER = [b for b in os.environ.get("KILN_PROOF_POWER", "").split(",") if b]
SCREEN = [b for b in os.environ.get("KILN_PROOF_SCREEN", "").split(",") if b]
GRIND = [b for b in os.environ.get("KILN_PROOF_GRIND", "").split(",") if b]
FLUIDS = [b for b in os.environ.get("KILN_PROOF_FLUIDS", "").split(",") if b]
CABLE = [b for b in os.environ.get("KILN_PROOF_CABLE", "").split(",") if b]
TRANSFER = [b for b in os.environ.get("KILN_PROOF_TRANSFER", "").split(",") if b]
# A Tech Reborn machine's slot settings, as its screen sets them (`slotConfig`, merged into what its code wrote): the
# grinder's output (slot 1) out of its east face by itself, the furnace's input (slot 0) from its west face.
SLOT_OUT_EAST = "{slot_1:{side_5:{config:{config:2}},output:1b}}"
SLOT_IN_WEST = "{slot_0:{side_4:{config:{config:1}}}}"
SPONGE = [b for b in os.environ.get("KILN_PROOF_SPONGE", "").split(",") if b]
LAN = [b for b in os.environ.get("KILN_PROOF_LAN", "").split(",") if b]
SWITCH = os.environ.get("KILN_PROOF_SWITCH")
# A mod tried in the game by itself (KILN_PROOF_ONLY=try, D-084): what the player does and what must show, as JSON.
TRY = json.loads(os.environ.get("KILN_PROOF_TRY") or "{}")


def runs_java(jar):
    """Whether a mod runs in Kiln's Java: it has entrypoints, or mixins (a mod with mixins and no entrypoint runs too,
    D-099); data-only mods (structures) have neither."""
    with zipfile.ZipFile(jar) as z:
        if "fabric.mod.json" not in z.namelist():
            return False
        manifest = json.loads(z.read("fabric.mod.json").decode("utf-8", "replace"), strict=False)
    entrypoints = manifest.get("entrypoints") or {}
    return bool(entrypoints.get("main") or entrypoints.get("client") or manifest.get("mixins"))
# The timer says each split in chat (`chat_splits`), so the proof can read them.
SETTINGS = "corner=top_right\nchat_splits=true\n"
TOP_RIGHT = (0.75, 0.0, 1.0, 0.45)
# Parts of the HUD the mods' features change, as fractions of the screen: the hearts' first column (where an eleventh
# heart goes) and the first hotbar slot; the experience level's number and the middle of its bar; Show FPS's counter,
# top left.
HEARTS = (0.28, 0.78, 0.34, 0.99)
LEVEL = (0.42, 0.80, 0.58, 0.90)
COUNTER = (0.0, 0.0, 0.13, 0.09)
# Blocks a world has anyway: a structure's are the other blocks of its pieces.
NATURAL = {"air", "cave_air", "water", "lava", "stone", "dirt", "grass_block", "sand", "red_sand", "gravel", "clay",
           "sandstone", "deepslate", "kelp", "kelp_plant", "seagrass", "tall_seagrass", "short_grass", "tall_grass",
           "snow", "ice", "netherrack", "end_stone", "structure_void", "jigsaw", "structure_block"}


def palette(raw):
    """Block names in a structure piece's palette (`.nbt`, gzip): a small NBT reader."""
    s, names = io.BytesIO(gzip.decompress(raw)), set()

    def string():
        (n,) = struct.unpack(">H", s.read(2))
        return s.read(n).decode("utf-8", "replace")

    def payload(tag, key, in_palette):
        sizes = {1: 1, 2: 2, 3: 4, 4: 8, 5: 4, 6: 8}
        if tag in sizes:
            s.read(sizes[tag])
        elif tag in (7, 11, 12):
            (n,) = struct.unpack(">i", s.read(4))
            s.read(n * {7: 1, 11: 4, 12: 8}[tag])
        elif tag == 8:
            value = string()
            if key == "Name" and in_palette:
                names.add(value)
        elif tag == 9:
            item = s.read(1)[0]
            (n,) = struct.unpack(">i", s.read(4))
            for _ in range(n):
                payload(item, None, in_palette or key in ("palette", "palettes"))
        elif tag == 10:
            while (t := s.read(1)[0]) != 0:
                payload(t, string(), in_palette)

    tag = s.read(1)[0]
    string()
    payload(tag, None, False)
    return names


def structures(jar):
    """The structures a mod's jar defines (`namespace:name`), and the blocks of its pieces no world has anyway."""
    with zipfile.ZipFile(jar) as z:
        ids = [re.sub(r"^data/([^/]+)/worldgen/structure/(.+)\.json$", r"\1:\2", n) for n in z.namelist()
               if re.match(r"^data/[^/]+/worldgen/structure/.+\.json$", n)]
        blocks = set()
        for n in z.namelist():
            if re.match(r"^data/[^/]+/structures?/.+\.nbt$", n):
                blocks |= {b.removeprefix("minecraft:") for b in palette(z.read(n))}
    # Logs and leaves grow in any world; wood (bark on every side) only where something built it.
    return ids, {b for b in blocks - NATURAL if not b.endswith(("_log", "_leaves"))}


# Where a structure may be: the Overworld first, then the Nether and the End (a mod's structures say which by their
# biomes; /locate finds them in the dimension it runs in).
DIMENSIONS = ("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end")
# Heights searched, in 81-block layers (horizon-ctl takes at most 32768 blocks a request): from below the deepslate's
# top (buried halls) to over the mountains (a cherry grove's).
LAYERS = (-59, 22, 103, 184)


# What /locate says when a dimension has none (vanilla's commands.locate.structure.not_found), and how long a search
# may take to answer: a far one takes seconds, more on a busy computer.
LOCATE_NOT_FOUND = r"Could not find a structure of type"
LOCATE_WAIT = 60.0
# How long a search horizon-ctl stopped waiting for may still take to answer.
LOCATE_LATE = 240.0
# How many times a structure with too few of its pieces' blocks is counted again, five seconds apart.
COUNT_AGAIN = 4


def pieces_counted(g, x, z, blocks):
    """The blocks of the pieces (`blocks`) around x, z, in every layer: how many, every block by name, and by layer."""
    found, census, by_layer = 0, {}, dict.fromkeys(LAYERS, 0)
    for bx in range(x - 48, x + 48, 16):
        for bz in range(z - 48, z + 48, 16):
            for low in LAYERS:
                counts = g.run("blocks", str(bx), str(low), str(bz), str(bx + 15), str(low + 80),
                               str(bz + 15)).get("counts", {})
                for name, n in counts.items():
                    name = name.split("[")[0].removeprefix("minecraft:")
                    census[name] = census.get(name, 0) + n
                    if name in blocks:
                        found += n
                        by_layer[low] += n
    return found, census, by_layer


def located(g, structure, origin=None):
    """`/locate`s the structure from `origin` (dimension, x, z), else from the player, in the Overworld, else the
    Nether, else the End (from an origin, only in its dimension): (dimension, x, z), or None."""
    for dimension in DIMENSIONS if origin is None else (origin[0],):
        at_origin = "" if origin is None else f"positioned {origin[1]} 64 {origin[2]} "
        # This dimension's own answer, found or not, never an earlier one: on a busy computer a far search answers
        # after seconds, and a fixed wait read the Overworld's answer as the End's (0.4.0's sweep, D-109). When
        # horizon-ctl stops waiting for the command, the game's search goes on and answers later: that answer is still
        # this dimension's, waited for longer, so it is neither lost nor read as the next dimension's.
        before = chat_lines(g)
        try:
            g.run("command", f"execute in {dimension} {at_origin}run locate structure {structure}")
            limit = LOCATE_WAIT
        except subprocess.CalledProcessError:
            limit = LOCATE_LATE
        pattern = r"nearest " + re.escape(structure) + r" is at|" + LOCATE_NOT_FOUND
        said = until(lambda: next((l for l in reversed(new_lines(before, chat_lines(g))) if re.search(pattern, l)),
                                  None), lambda line: line is not None, limit, every=0.5)
        at = re.search(r"nearest " + re.escape(structure) + r" is at \[(-?\d+), [^,]+, (-?\d+)\]", said or "")
        if at:
            return dimension, int(at.group(1)), int(at.group(2))
    return None


def counted_there(g, dimension, x, z, blocks):
    """Flies to the structure at x, z and counts its pieces' blocks (`pieces_counted`). The chunks around it can come
    seconds after the game is ready there (more on a busy computer): counted again while there are too few and the
    count still grows."""
    g.run("command", f"execute in {dimension} run tp @s {x} 150 {z}")
    g.run("wait-ready", "90")
    time.sleep(3)
    found, census, by_layer = pieces_counted(g, x, z, blocks)
    for _ in range(COUNT_AGAIN):
        if found >= TRY.get("pieces", 100):
            break
        time.sleep(5)
        again = pieces_counted(g, x, z, blocks)
        if again[0] <= found and found > 0:
            break
        found, census, by_layer = max((found, census, by_layer), again, key=lambda c: c[0])
    return found, census, by_layer


# How far from a structure found with too few of its pieces' blocks the next one is looked for, in four directions:
# the world cuts one into a cliff or a lava sea now and then, and the mod is judged on its structure, not on that one.
ELSEWHERE = 1500


def structure_found(g, structure, blocks, places=None):
    """`/locate`s the structure, in the Overworld, else the Nether, else the End; flies there and counts its pieces'
    blocks: the count, where (or why there is none), and what it holds. `places` (dimension, x, z) are where the mod's
    other structures were: one not found from the player is looked for from there too (a rare biome's, far away), and
    one found there is added. One with too few blocks is looked for again around it, and the best one counts."""
    places = places if places is not None else []
    at = located(g, structure)
    for origin in ([] if at else list(places)):
        at = located(g, structure, origin)
        if at:
            break
    if not at:
        return 0, "not located", {}
    dimension, x, z = at
    found, census, by_layer = counted_there(g, dimension, x, z, blocks)
    for dx, dz in ((ELSEWHERE, 0), (-ELSEWHERE, 0), (0, ELSEWHERE), (0, -ELSEWHERE)):
        if found >= TRY.get("pieces", 100):
            break
        other = located(g, structure, (dimension, at[1] + dx, at[2] + dz))
        if other and other[1:] != at[1:] and other[1:] != (x, z):
            again = counted_there(g, *other, blocks)
            if again[0] > found:
                (dimension, x, z), (found, census, by_layer) = other, again
    if found:
        places.append((dimension, x, z))
    g.run("command", f"execute in {dimension} run tp @s {x} 150 {z}")
    g.run("wait-ready", "90")
    # What it holds: the containers and spawners among its blocks, and the mobs it came with (a structure's pieces
    # carry them), around it.
    kept = {name: census.get(name, 0) for name in ("chest", "trapped_chest", "barrel", "spawner")}
    mobs = {}
    for mob in g.run("entities", "128").get("entities", []):
        mobs[mob.get("type")] = mobs.get(mob.get("type"), 0) + 1
    # Seen from above and aside, over the layer most of it is in (in the Overworld, never lower than y 110).
    low = max(by_layer, key=by_layer.get) if found else 22
    up = max(110, low + 100) if dimension == "minecraft:overworld" else low + 60
    # Under the sea (water at sea level over it, most of it below): from under the water, close by.
    under = (dimension == "minecraft:overworld" and low < 63 and
             (block(g, x, 62, z) or "").startswith("minecraft:water") and by_layer[103] * 4 < found)
    # The picture is for whoever reads the results, the count is the try's: a game slow to be ready there (ten games
    # at once: MOTS Structures' 38th structure in 0.4.0's sweep) goes without the picture, not without the structure.
    try:
        if under:
            g.run("command", f"execute in {dimension} run tp @s {x - 20} 50 {z - 20}")
            g.run("wait-ready", "120")
            g.run("look-at", str(x), "35", str(z))
            g.run("command", "effect give @s minecraft:night_vision 60 0 true")
        else:
            g.run("command", f"execute in {dimension} run tp @s {x - 40} {up} {z - 40}")
            g.run("wait-ready", "120")
            g.run("look-at", str(x), str(up - 40), str(z))
        time.sleep(2)
        g.shot(f"5-{structure.replace(':', '-').replace('/', '-')}")
    except subprocess.CalledProcessError:
        print(f"{structure}: no picture, the game wasn't ready there in 120 s", file=sys.stderr)
    where = f"at {x} {z}" + ("" if dimension == "minecraft:overworld" else f" in {dimension}")
    return found, where, {**kept, **mobs}


class KilnGame(Game):
    """Game, with Kiln's folder copied into the fresh game folder before the game starts."""

    def run(self, *args):
        if args[:1] == ("launch",) and not (self.home / "kiln").exists():
            shutil.copytree(BUILT, self.home / "kiln")
            for jar in EXTRA + PROOF_MODS:
                shutil.copy(jar, self.home / "kiln" / "mods" / jar.name)
            settings = self.home / "kiln" / "config" / "kiln_timer" / "settings.properties"
            settings.parent.mkdir(parents=True)
            settings.write_text(SETTINGS)
            if CONFIG:
                shutil.copytree(CONFIG, self.home / "kiln" / "config", dirs_exist_ok=True)
            # Every mod is off until turned on (horizon-mods.txt), as the Mods page would.
            on = []
            for folder in CONTENT + PROOF_CONTENT:
                mod_id = tomllib.loads((folder / "horizon-mod.toml").read_text())["id"]
                shutil.copytree(folder, self.home / "horizon-mods" / mod_id)
                on.append(f"mod.{mod_id}:true\n")
            if on:
                (self.home / "horizon-mods.txt").write_text("".join(on))
        # With the testbench: the world opens without waiting, so the player can open a screen while the testbench
        # waits to report (it reports three seconds into the world, and the game's own wait takes longer).
        if args[:1] == ("launch",) and "--wait" in args and ONLY == "mods" and any("testbench" in j.name for j in EXTRA):
            done = super().run(*[a for a in args if a != "--wait"])
            screen_opened(self)
            super().run("wait-ready", "120")
            return done
        return super().run(*args)


def patient(g, *args, tries=3):
    """A horizon-ctl command, tried again when it gives up: on a busy computer the game answers late. Setting an
    option twice is the same as once."""
    for n in range(tries):
        try:
            return g.run(*args)
        except subprocess.CalledProcessError:
            if n == tries - 1:
                raise
            time.sleep(1)


def drawn(g, frames=3, limit=5.0):
    """Waits until the game has drawn a few more frames (a busy computer draws few a second), at most `limit` s."""
    until = time.time() + limit
    try:
        start = g.run("status").get("frames", 0)
        while time.time() < until and g.run("status").get("frames", 0) < start + frames:
            time.sleep(0.1)
    except subprocess.CalledProcessError:
        time.sleep(max(0.0, until - time.time()))


def hud_shot(g, name):
    """A screenshot with the chat hidden (Options > Chat Settings), for comparing the HUD under it; the chat comes back
    after. Without that option, after waiting for the chat to fade."""
    try:
        patient(g, "options-set", "chatVisibility", "hidden")
    except subprocess.CalledProcessError:
        time.sleep(11)
        return g.shot(name)
    try:
        time.sleep(0.3)
        drawn(g)
        return g.shot(name)
    finally:
        try:
            patient(g, "options-set", "chatVisibility", "full")
        except subprocess.CalledProcessError:
            pass


def chat(g):
    """The timer's and Kiln's chat lines, oldest first."""
    text = json.dumps(g.run("messages", "100"), ensure_ascii=False)
    return re.findall(r"(\[Kiln Timer\][^\"\\]*|Kiln: [^\"\\]*)", text)


def max_health(g):
    """The player's maximum health: the server's base value (`/attribute`) and the window's hearts' (`status`)."""
    g.run("command", "attribute @s minecraft:max_health base get")
    time.sleep(0.7)
    newest = (g.run("messages", "1").get("chat") or [""])[0]
    server = re.findall(r"(\d+(?:\.\d+)?)\s*$", newest)
    window = (g.run("status").get("player") or {}).get("max_health")
    return float(server[0]) if server else None, window


def count(g, item):
    """How many of `item` the player has."""
    return g.run("inventory").get("counts", {}).get(item, 0)


def use_three(g, item):
    """A player in survival with three of `item` (the first hotbar slot) uses them one at a time: after each, what
    the game says; and how much the hearts and the first hotbar slot changed on screen, after the three."""
    g.run("command", "clear @s")
    g.run("command", f"give @s {item} 3")
    time.sleep(1)
    g.run("select", "1")
    name = item.replace(':', '-')
    before = hud_shot(g, f"6-before-{name}")
    uses = [{"max_health": max_health(g), "count": count(g, item)}]
    for _ in range(3):
        g.run("use-item")
        time.sleep(1)
        bar = g.run("messages", "1").get("actionbar")
        uses.append({"max_health": max_health(g), "count": count(g, item), "action_bar": bar})
    after = hud_shot(g, f"6-{name}")
    return uses, {"hearts_changed": round(difference(before, after, HEARTS), 1)}


def loot_rolled(g, table, item, times=5):
    """How many of `item` rolling `table` `times` times gives: once the player has at least `times`, read again a
    second later (more would be wrong too)."""
    g.run("command", "clear @s")
    for _ in range(times):
        g.run("command", f"loot give @s loot {table}")
    until(lambda: count(g, item), lambda n: n >= times)
    time.sleep(1)
    return count(g, item)


def until(read, done, limit=30.0, every=0.1):
    """`read()` again and again until `done` holds for what it read, or `limit` seconds have passed: the last read. On
    a busy computer the game's ticks run late, so a fixed wait reads too early, or too late for a short state."""
    deadline = time.monotonic() + limit
    while True:
        value = read()
        if done(value) or time.monotonic() > deadline:
            return value
        time.sleep(every)


def block(g, x, y, z):
    return g.run("block", str(x), str(y), str(z)).get("state")


def block_until(g, x, y, z, done, limit=30.0):
    """The state of the block at x y z once `done` holds for it (or after `limit` s), as this game's client has it."""
    return until(lambda: block(g, x, y, z) or "", done, limit)


def named(name):
    """Whether a state is of the block `name` (any properties): the first state the client has of a block just set."""
    return lambda state: state.split("[")[0] == name


def lit(state):
    return "lit=true" in state


def dark(state):
    return "lit=false" in state


def action_bar(g, start, limit=30.0):
    """What the action bar says once it starts with `start` (or after `limit` s)."""
    return until(lambda: g.run("messages", "1").get("actionbar"), lambda bar: (bar or "").startswith(start), limit)


def chat_lines(g):
    """The chat's lines, newest first (the game keeps 100)."""
    return list(g.run("messages", "100").get("chat") or [])


def new_lines(before, after):
    """The lines of the chat read as `after` that it hadn't when read as `before` (both newest first)."""
    for k in range(len(after) + 1):
        if after[k:] == before[:len(after) - k]:
            return after[:k]
    return after


def answer(g, command, pattern, limit=30.0):
    """Runs a command; its answer in the chat: the first line after it matching `pattern`, never an earlier one (a
    busy computer answers late), or None after `limit` s."""
    before = chat_lines(g)
    g.run("command", command)
    return until(lambda: next((l for l in reversed(new_lines(before, chat_lines(g))) if re.search(pattern, l)), None),
                 lambda line: line is not None, limit, every=0.2)


def proof_blocks(g):
    """Kiln's proof blocks on a stone floor at y 150, the player on it: the switch (on gold, which it reads) clicked
    twice and stood on, the sprout (on farmland, water beside it) ticked at random, the timer placed from the hand,
    left to tick, a block set beside it, broken; the counter set, left to count, read, clicked; the crusher fed and
    emptied by hoppers.

    Each read waits for what it reads (the game's ticks run late on a busy computer): a block's first state once the
    client has it, a state the block comes to (lit, dark, ripe) once it has, an answer once it is there. A state that
    lasts only a while (the timer dark until its scheduled tick) is the last of every state the client had of the block
    before it changed (lit)."""
    x, _, z = g.run("state")["player"]["block_pos"]
    y = 150
    g.run("command", f"fill {x - 3} {y - 1} {z - 3} {x + 3} {y} {z + 3} minecraft:stone")
    g.run("command", "gamemode survival")
    g.run("command", f"tp @s {x + 0.5} {y + 1} {z + 0.5}")
    g.run("wait-ready", "60")
    time.sleep(1)
    found = {}
    sx, sz = x + 2, z
    g.run("command", f"setblock {sx} {y - 1} {sz} minecraft:gold_block")
    g.run("command", f"setblock {sx} {y} {sz} kiln_proof_blocks:switch")
    # Clicked once the player's game has it to click.
    block_until(g, sx, y, sz, named("kiln_proof_blocks:switch"))
    g.run("interact", str(sx), str(y), str(sz), "up")
    found["switch_clicked"] = block_until(g, sx, y, sz, lit)
    found["switch_said"] = action_bar(g, "Switch on")
    g.run("interact", str(sx), str(y), str(sz), "up")
    found["switch_clicked_again"] = block_until(g, sx, y, sz, dark)
    g.run("command", f"tp @s {sx + 0.5} {y + 1} {sz + 0.5}")
    found["switch_stood_on"] = block_until(g, sx, y, sz, lit)
    fx, fz = x - 2, z
    # No random ticks while it is planted and read: only the ones turned on after may ripen it.
    g.run("command", "gamerule random_tick_speed 0")
    g.run("command", f"setblock {fx} {y} {fz} minecraft:farmland")
    g.run("command", f"setblock {fx - 1} {y} {fz} minecraft:water")
    time.sleep(1)
    g.run("command", f"setblock {fx} {y + 1} {fz} kiln_proof_blocks:sprout")
    found["sprout_planted"] = block_until(g, fx, y + 1, fz, named("kiln_proof_blocks:sprout"))
    g.run("command", "gamerule random_tick_speed 1000")
    found["sprout_ticked"] = block_until(g, fx, y + 1, fz, lambda state: "ripe=true" in state)
    g.run("command", "gamerule random_tick_speed 3")
    # The timer, placed from the hand two blocks north of the player: it faces them.
    g.run("command", f"tp @s {x + 0.5} {y + 1} {z + 0.5}")
    g.run("command", "clear @s")
    g.run("command", "give @s kiln_proof_blocks:timer 1")
    until(lambda: count(g, "kiln_proof_blocks:timer"), lambda n: n >= 1)
    g.run("select", "1")
    tx, ty, tz = x, y + 1, z - 2
    # Turned to the north first: `place` turns the player and clicks at once, before the server hears the turn. So
    # placed once the server has the player facing north (yaw 180).
    g.run("look", "180", "40")
    until(lambda: server_yaw(g), lambda yaw: yaw is not None and abs(yaw % 360 - 180) < 1, every=0.5)
    # `place` answers with the block as the player's game first has it, a tick or two after the click; on a busy
    # computer that can be the client's own guess (the default state, facing north) before the server's answer. Every
    # state it then has is kept until it lights: the placed state is the last one before that, the server's, which
    # lasts until the timer's scheduled tick 20 ticks later (the client reads it many times a tick).
    placed = g.run("place", str(tx), str(ty), str(tz))
    seen = [(placed.get("block") or {}).get("state") or "" if isinstance(placed, dict) else ""]

    def timer():
        state = block(g, tx, ty, tz) or ""
        if state != seen[-1]:
            seen.append(state)
        return state

    until(timer, lit)
    found["timer_seen"] = seen
    before = next((i for i, state in enumerate(seen) if lit(state)), len(seen))
    found["timer_placed"] = seen[before - 1] if before else None
    found["timer_ticked"] = seen[before] if before < len(seen) else seen[-1]
    g.run("command", f"setblock {tx + 1} {ty} {tz} minecraft:stone")
    found["timer_beside_a_change"] = block_until(g, tx, ty, tz, dark)
    g.run("command", "gamemode creative")
    until(lambda: g.run("state").get("gamemode"), lambda mode: mode == "creative")
    g.run("break", str(tx), str(ty), str(tz))
    found["timer_said"] = action_bar(g, "Timer broken")
    # The star lies on the ground, or the player (beside it, in creative) has picked it up already.
    found["timer_dropped_a_star"] = until(
        lambda: "nether_star" in json.dumps(g.run("entities", "8", "minecraft:item")) + json.dumps(g.run("inventory")),
        bool, every=0.2)
    found["timer_broken"] = block(g, tx, ty, tz)
    # The counter, set beside the player: its block entity counts the server's ticks and lights it at 40, keeping its
    # count in the block entity's saved data; a click reads that, says it, and starts the count again.
    cx, cy, cz = x + 2, y + 1, z + 2
    g.run("command", f"setblock {cx} {cy} {cz} kiln_proof_blocks:counter")
    found["counter_set"] = block_until(g, cx, cy, cz, named("kiln_proof_blocks:counter"))
    found["counter_counted"] = block_until(g, cx, cy, cz, lit)
    found["counter_data"] = data(g, cx, cy, cz)
    g.run("interact", str(cx), str(cy), str(cz), "up")
    found["counter_clicked"] = block_until(g, cx, cy, cz, dark)
    found["counter_said"] = action_bar(g, "Counter at")
    # Gone, so that the next uses of an item don't click it.
    g.run("command", f"setblock {cx} {cy} {cz} minecraft:air")
    # The crusher, between two of the game's hoppers: the one above feeds it cobblestone, the one below takes the
    # gravel its block entity makes; the slots are the block entity's, as Horizon's hoppers reach them.
    kx, ky, kz = x - 2, y + 2, z + 2
    g.run("command", f"setblock {kx} {ky - 1} {kz} minecraft:hopper")
    g.run("command", f"setblock {kx} {ky} {kz} kiln_proof_blocks:crusher")
    g.run("command", f"setblock {kx} {ky + 1} {kz} minecraft:hopper")
    time.sleep(0.5)
    g.run("command", f"item replace block {kx} {ky + 1} {kz} container.0 with minecraft:cobblestone {COBBLESTONE}")
    # Lit while it has work: from the first cobblestone the hopper above hands it to the last one crushed (two
    # seconds of ticks for four), then dark.
    found["crusher_working"] = block_until(g, kx, ky, kz, lit)
    block_until(g, kx, ky, kz, dark)
    found["crusher_gave"] = until(lambda: data(g, kx, ky - 1, kz), lambda line: gravel(line) >= COBBLESTONE,
                                  limit=20, every=1)
    found["crusher_data"] = data(g, kx, ky, kz)
    found["crusher_fed_from"] = data(g, kx, ky + 1, kz)
    for dy in (1, 0, -1):
        g.run("command", f"setblock {kx} {ky + dy} {kz} minecraft:air")
    return found


# The cobblestone the crusher's hopper is given.
COBBLESTONE = 4


def gravel(said):
    """How much gravel `/data get block` says a container holds."""
    return sum(int(n) for item in re.findall(r"\{[^{}]*\}", said or "") if '"minecraft:gravel"' in item
               for n in re.findall(r"count: (\d+)", item))


def server_yaw(g):
    """Where the server has the player looking (`/data get entity @s Rotation`): the yaw, in degrees."""
    line = answer(g, "data get entity @s Rotation", r"has the following entity data: \[")
    yaw = re.search(r"\[(-?[\d.]+)f", line or "")
    return float(yaw[1]) if yaw else None


def data(g, x, y, z):
    """What `/data get block` says of the block entity at x y z (this command's answer, not an earlier one's); None
    when there is none."""
    line = answer(g, f"data get block {x} {y} {z}",
                  rf"^{x}, {y}, {z} has the following block data|^The target block is not a block entity")
    return line if line and "has the following block data" in line else None


def carried(g):
    """Blocks Horizon's import left out and Kiln carried into the mod's content (D-055): each set in a row east of the
    player and read back; and the files Kiln wrote, by content mod."""
    x, y, z = g.run("state")["player"]["block_pos"]
    g.run("command", "gamemode creative")
    g.run("look", "-90", "20")
    found = {}
    for i, block_id in enumerate(CARRIED):
        g.run("command", f"setblock {x + 2 + i} {y} {z} {block_id}")
        time.sleep(0.3)
        found[block_id] = block(g, x + 2 + i, y, z)
    time.sleep(1)
    g.shot("7-carried")
    written = {p.parent.name: len(json.loads(p.read_text()))
               for p in sorted((g.home / "horizon-mods").glob("*/kiln-carried.json"))}
    said = re.findall(r"Kiln: carried [^\"\\]*", json.dumps(g.run("logs", "--lines", "2000")))
    return {"blocks": found, "files_written": written, "log": said}


def smelted(g, x, y, z):
    """The furnace KILN_PROOF_SMELT names, set at x y z facing the player (west of it), given two raw iron and a coal
    in its slots by command: its state while it burns, and its data once both are iron ingots (or after 40 s)."""
    g.run("command", f"setblock {x} {y} {z} {SMELT}[facing=west]")
    time.sleep(1)
    g.run("command", f'data merge block {x} {y} {z} '
                     '{Items:[{Slot:0b,id:"minecraft:raw_iron",count:2},{Slot:2b,id:"minecraft:coal",count:1}]}')
    found = {"burning": block_until(g, x, y, z, lambda state: "active=true" in state)}
    g.shot("8-smelting")
    deadline = time.time() + 40
    while True:
        found["data"] = data(g, x, y, z) or ""
        if smelted_into(found["data"]) == 2 or time.time() > deadline:
            break
        time.sleep(3)
    found["ingots"] = smelted_into(found["data"])
    found["after"] = block(g, x, y, z)
    return found


def powered(g, x, y, z):
    """The electric furnace and the solar panel KILN_PROOF_POWER names: at noon, in clear weather, the furnace at x y z
    with the panel east of it, nothing above either, and two raw iron in the furnace's input by command. The panel's
    state once it has seen the sun, and the furnace's data once both are iron ingots (or after 60 s)."""
    furnace, panel = POWER
    step(g, "time set 6000", "weather clear")
    g.run("command", f"fill {x} {y} {z} {x + 1} {y + 30} {z} air")
    time.sleep(0.5)
    g.run("command", f"setblock {x} {y} {z} {furnace}[facing=west]")
    g.run("command", f"setblock {x + 1} {y} {z} {panel}")
    time.sleep(1)
    g.run("command", f'data merge block {x} {y} {z} {{Items:[{{Slot:0b,id:"minecraft:raw_iron",count:2}}]}}')
    # Facing the pair from where the player stands (two blocks west, three north of the furnace), once the panel
    # has seen the sun (it looks every second): read from the blocks, so no chat covers the shot.
    px, _, pz = g.run("state")["player"]["block_pos"]
    yaw = -math.degrees(math.atan2(x + 1 - px, z + 0.5 - pz))
    g.run("look", f"{yaw:.1f}", "15")
    found = {}
    deadline = time.time() + 20
    while time.time() < deadline and not block(g, x + 1, y, z).startswith(f"{panel}[active=true"):
        time.sleep(1)
    found["panel"] = block(g, x + 1, y, z)
    # The commands' lines fade from the chat after 10 s; the furnace is still at its first ore then.
    time.sleep(10)
    found["running"] = block(g, x, y, z)
    g.shot("9-powered")
    deadline = time.time() + 60
    while True:
        found["data"] = data(g, x, y, z) or ""
        if smelted_into(found["data"]) == 2 or time.time() > deadline:
            break
        time.sleep(3)
    found["ingots"] = smelted_into(found["data"])
    energy = re.search(r"PowerAcceptor: \{energy: (\d+)L\}", found["data"])
    found["furnace_energy"] = int(energy[1]) if energy else None
    panel_data = data(g, x + 1, y, z) or ""
    energy = re.search(r"PowerAcceptor: \{energy: (\d+)L\}", panel_data)
    found["panel_energy"] = int(energy[1]) if energy else None
    return found


def energy_of(saved):
    """The energy a Tech Reborn machine holds, from what `/data get block` says of it; None without one."""
    found = re.search(r"PowerAcceptor: \{energy: (\d+)L\}", saved)
    return int(found[1]) if found else None


def cabled(g, x, y, z):
    """The electric furnace, the solar panel and the cable KILN_PROOF_CABLE names: at noon, in clear weather, the
    furnace at x y z, two cables east of it, then the panel, nothing above any of them, and two raw iron in the
    furnace's input by command. Tech Reborn's cable network ticks by Fabric's START_SERVER_TICK: the panel's energy
    must come through both cables and smelt both into iron ingots (or 90 s pass)."""
    furnace, panel, cable = CABLE
    step(g, "time set 6000", "weather clear")
    g.run("command", f"fill {x} {y} {z} {x + 3} {y + 30} {z} air")
    time.sleep(0.5)
    g.run("command", f"setblock {x} {y} {z} {furnace}[facing=west]")
    for i in (1, 2):
        g.run("command", f"setblock {x + i} {y} {z} {cable}[east=true,west=true]")
    g.run("command", f"setblock {x + 3} {y} {z} {panel}")
    time.sleep(1)
    g.run("command", f'data merge block {x} {y} {z} {{Items:[{{Slot:0b,id:"minecraft:raw_iron",count:2}}]}}')
    px, _, pz = g.run("state")["player"]["block_pos"]
    yaw = -math.degrees(math.atan2(x + 1.5 - px, z + 0.5 - pz))
    g.run("look", f"{yaw:.1f}", "15")
    found = {"cables": [block(g, x + i, y, z) for i in (1, 2)]}
    deadline = time.time() + 90
    while True:
        found["data"] = data(g, x, y, z) or ""
        if smelted_into(found["data"]) == 2 or time.time() > deadline:
            break
        time.sleep(3)
    g.shot("15-cable")
    found["ingots"] = smelted_into(found["data"])
    found["furnace_energy"] = energy_of(found["data"])
    found["panel"] = block(g, x + 3, y, z)
    return found


def fed(g, x, y, z):
    """The grinder, the electric furnace and the solar panel KILN_PROOF_TRANSFER names: at noon, in clear weather, the
    grinder at x y z with the furnace east of it and a panel south of each, nothing above them. By command, as a
    player would on their screens: the grinder's output (slot 1) goes out east by itself, the furnace takes its input
    (slot 0) from the west; two iron ore into the grinder. RebornCore moves the grinder's raw iron into the furnace by
    Fabric's ItemStorage.SIDED: the furnace must smelt all four into iron ingots (or 120 s pass), with nothing left in
    the grinder."""
    grinder, furnace, panel = TRANSFER
    step(g, "time set 6000", "weather clear")
    g.run("command", f"fill {x} {y} {z} {x + 1} {y + 30} {z + 1} air")
    time.sleep(0.5)
    g.run("command", f"setblock {x} {y} {z} {grinder}[facing=west]")
    g.run("command", f"setblock {x + 1} {y} {z} {furnace}[facing=west]")
    g.run("command", f"setblock {x} {y} {z + 1} {panel}")
    g.run("command", f"setblock {x + 1} {y} {z + 1} {panel}")
    # Their slot settings once their code has written them (their first tick), then set.
    time.sleep(2)
    g.run("command", f"data merge block {x} {y} {z} {{slotConfig:{SLOT_OUT_EAST}}}")
    g.run("command", f"data merge block {x + 1} {y} {z} {{slotConfig:{SLOT_IN_WEST}}}")
    time.sleep(0.5)
    g.run("command", f'data merge block {x} {y} {z} {{Items:[{{Slot:0b,id:"minecraft:iron_ore",count:2}}]}}')
    px, _, pz = g.run("state")["player"]["block_pos"]
    yaw = -math.degrees(math.atan2(x + 1 - px, z + 0.5 - pz))
    g.run("look", f"{yaw:.1f}", "15")
    found = {}
    deadline = time.time() + 120
    while True:
        found["furnace"] = data(g, x + 1, y, z) or ""
        if smelted_into(found["furnace"]) == 4 or time.time() > deadline:
            break
        time.sleep(3)
    g.shot("16-transfer")
    found["grinder"] = data(g, x, y, z) or ""
    found["ingots"] = smelted_into(found["furnace"])
    found["left_in_grinder"] = "raw_iron" in found["grinder"]
    return found


def by_hand(g, x, y, z, machine, panel, given, made, made_count, shot):
    """A machine and a solar panel at noon, the machine 30 blocks above x y z with the panel east of it; a player in
    survival with two of `given` opens the machine's screen with a click, puts them into its input (the menu's first
    slot) by hand, and takes what it makes from its output with a shift-click once that holds `made_count` of `made`
    (or after 90 s). The screen's slots when it opened, after the input went in, and at the end; how many of `made`
    the player then holds."""
    # On a stone floor 30 blocks up, open to the sky, the player a block and a half west of the machine, facing it.
    y += 30
    step(g, "time set 6000", "weather clear")
    g.run("command", f"fill {x - 3} {y - 1} {z - 1} {x + 1} {y - 1} {z + 1} minecraft:stone")
    g.run("command", f"fill {x - 3} {y} {z - 1} {x + 1} {y + 3} {z + 1} air")
    time.sleep(0.5)
    g.run("command", f"setblock {x} {y} {z} {machine}[facing=west]")
    g.run("command", f"setblock {x + 1} {y} {z} {panel}")
    step(g, "gamemode survival", "clear @s", f"give @s {given} 2", f"tp @s {x - 1.5} {y} {z + 0.5} -90 25", wait=2)
    found = {}
    g.run("interact", str(x), str(y), str(z), "west")
    found["opened"] = menu_opened(g)
    slots = found["opened"].get("slots") or []
    held = next((s["slot"] for s in slots if s.get("item") == given), None)
    # The menu's container slots are 300 and up (a machine's input, output, battery); the player's 0 to 35.
    if held is not None:
        g.run("click-slot", str(held))
        time.sleep(0.3)
        g.run("click-slot", "300")
        time.sleep(1)
    found["filled"] = g.run("screen")
    time.sleep(4)
    g.shot(shot)
    deadline = time.time() + 90
    while time.time() < deadline:
        found["done"] = g.run("screen")
        if any(s.get("item") == made and s.get("count") == made_count
               for s in found["done"].get("slots") or [] if s.get("slot") == 301):
            break
        time.sleep(3)
    g.run("click-slot", "301", "shift")
    time.sleep(1)
    g.run("close")
    found["made_held"] = until(lambda: count(g, made), lambda n: n >= made_count)
    g.run("command", "gamemode creative")
    return found


def menu_opened(g, limit=30.0):
    """The screen once a machine's menu is open (its slots, 300 and up, beside the player's), or after `limit` s."""
    return until(lambda: g.run("screen"), lambda s: any(slot.get("slot", 0) >= 300 for slot in s.get("slots") or []),
                 limit)


def put(g, item, into):
    """The player puts the `item` they hold in the open screen into its slot `into` (300 and up are the machine's)."""
    slots = g.run("screen").get("slots") or []
    held = next((s["slot"] for s in slots if s.get("item") == item and s["slot"] < 300), None)
    if held is not None:
        g.run("click-slot", str(held))
        time.sleep(0.3)
        g.run("click-slot", str(into))
        time.sleep(1)
    return held is not None


def fluids(g, x, y, z):
    """The thermal generator and the industrial grinder KILN_PROOF_FLUIDS names, 30 blocks above x y z: the grinder's
    casings and the water in them set by command behind it (east: RebornCore's shape, basic casings below and above, a
    ring of advanced ones around the water), then the grinder facing the player, the generator north of it. A player
    in survival with a lava bucket, a water bucket and an iron ore puts the lava into the generator through its screen,
    then the water and the ore into the grinder through its, and takes out what it grinds once its first output holds
    three raw iron (or after 90 s). What the screens showed, the grinder's outputs, and how many of each the player
    then holds."""
    generator, grinder = FLUIDS
    y += 30
    step(g, "time set 6000", "weather clear")
    g.run("command", f"fill {x - 3} {y - 1} {z - 2} {x + 4} {y - 1} {z + 2} minecraft:stone")
    g.run("command", f"fill {x - 3} {y} {z - 2} {x + 4} {y + 3} {z + 2} air")
    g.run("command", f"fill {x + 1} {y - 1} {z - 1} {x + 3} {y - 1} {z + 1} techreborn:basic_machine_casing")
    g.run("command", f"fill {x + 1} {y} {z - 1} {x + 3} {y} {z + 1} techreborn:advanced_machine_casing")
    g.run("command", f"setblock {x + 2} {y} {z} minecraft:water")
    g.run("command", f"fill {x + 1} {y + 1} {z - 1} {x + 3} {y + 1} {z + 1} techreborn:basic_machine_casing")
    time.sleep(0.5)
    # Set after its casings: its code checks them as it loads.
    g.run("command", f"setblock {x} {y} {z} {grinder}[facing=west]")
    g.run("command", f"setblock {x} {y} {z - 1} {generator}[facing=west]")
    step(g, "gamemode survival", "clear @s", "give @s minecraft:lava_bucket", "give @s minecraft:water_bucket",
         "give @s minecraft:iron_ore", f"tp @s {x - 1.5} {y} {z + 0.5} -90 25", wait=2)
    found = {}
    # Holding the lava bucket: the click is the generator's (it opens its screen), so the bucket isn't poured out.
    g.run("select", "1")
    g.run("interact", str(x), str(y), str(z - 1), "west")
    found["generator_opened"] = menu_opened(g)
    found["lava_kept"] = any(s.get("item") == "minecraft:lava_bucket"
                             for s in found["generator_opened"].get("slots") or [])
    found["lava_in"] = put(g, "minecraft:lava_bucket", 300)
    time.sleep(4)
    found["generator"] = g.run("screen")
    g.shot("12-thermal")
    g.run("close")
    # The generator gives about 16 E a tick, the grind takes 64 E a tick for 100 ticks, and Tech Reborn's crafter starts
    # over when its machine runs dry: the grinder charges first, as a player would let it (or 60 s).
    deadline = time.time() + 60
    while time.time() < deadline:
        energy = re.search(r"PowerAcceptor: \{energy: (\d+)L\}", data(g, x, y, z) or "")
        found["grinder_energy"] = int(energy[1]) if energy else None
        if (found["grinder_energy"] or 0) >= 7000:
            break
        time.sleep(3)
    g.run("interact", str(x), str(y), str(z), "west")
    found["grinder_opened"] = menu_opened(g)
    found["water_in"] = put(g, "minecraft:water_bucket", 301)
    found["ore_in"] = put(g, "minecraft:iron_ore", 300)
    time.sleep(4)
    g.shot("13-industrial-grinder")
    deadline = time.time() + 90
    while time.time() < deadline:
        found["done"] = g.run("screen")
        if any(s.get("item") == "minecraft:raw_iron" and s.get("count") == 3
               for s in found["done"].get("slots") or [] if s.get("slot") == 302):
            break
        time.sleep(3)
    g.shot("14-ground")
    # Its outputs (302 and on): the raw iron, and the nuggets, which are air in the game unless it has their ids.
    ground = ("minecraft:raw_iron", "techreborn:tin_nugget", "techreborn:nickel_nugget")
    outputs = [s for s in found["done"].get("slots") or [] if s.get("slot", 0) >= 302 and s.get("count")]
    found["outputs"] = {s["slot"]: f"{s.get('count')} {s.get('item')}" for s in outputs}
    for s in outputs:
        if s.get("item") in ground:
            g.run("click-slot", str(s["slot"]), "shift")
            time.sleep(1)
    g.run("close")
    # Once the player holds what they took out.
    taken = {item: sum(s["count"] for s in outputs if s.get("item") == item) for item in ground}
    found["held"] = until(lambda: {item: count(g, item) for item in ground},
                          lambda held: all(held[item] >= n for item, n in taken.items()))
    found["raw_iron_held"] = found["held"]["minecraft:raw_iron"]
    found["generator_state"] = block(g, x, y, z - 1)
    g.run("command", "gamemode creative")
    return found


def smelted_into(said):
    """How many iron ingots `/data get block` says the output slot (1) holds."""
    for item in re.findall(r"\{[^{}]*\}", said):
        if '"minecraft:iron_ingot"' in item and re.search(r"Slot: 1b", item):
            count = re.search(r"count: (\d+)", item)
            return int(count[1]) if count else 1
    return 0


def sponged(g, sponge, hot):
    """A block mod Horizon's import carries whole, its code by Kiln (Sponge Succ): a pool of lava, three by three, sunk
    in the ground north of the player, who stands on its rim; the player places a lava sponge from the hand over the
    pool's middle (against a stone on its far side). Its own code (`onPlace`) takes up the lava around it and turns it
    into a hot lava sponge."""
    x, y, z = g.run("state")["player"]["block_pos"]
    g.run("command", "gamemode survival")
    g.run("command", "effect give @s minecraft:fire_resistance 120 0 true")
    g.run("command", f"fill {x - 3} {y} {z - 7} {x + 3} {y + 3} {z + 1} minecraft:air")
    g.run("command", f"fill {x - 2} {y - 2} {z - 6} {x + 2} {y - 1} {z - 1} minecraft:stone")
    g.run("command", f"fill {x - 1} {y - 1} {z - 5} {x + 1} {y - 1} {z - 3} minecraft:lava")
    # What the sponge is placed against: a stone over the pool's far side.
    g.run("command", f"setblock {x} {y} {z - 5} minecraft:stone")
    g.run("command", f"tp @s {x + 0.5} {y} {z - 1.5} 180 30")
    g.run("command", "clear @s")
    g.run("command", f"give @s {sponge} 1")
    pool = [(px, y - 1, pz) for px in range(x - 1, x + 2) for pz in range(z - 5, z - 2)]

    def lava():
        return sum(block(g, *at).startswith("minecraft:lava") for at in pool)

    # Once the player's game has the pool, and the sponge in hand.
    found = {"lava_before": until(lava, lambda n: n == len(pool))}
    until(lambda: count(g, sponge), lambda n: n >= 1)
    g.run("look", "180", "30")
    time.sleep(1)
    pool_shot = hud_shot(g, "15-lava-pool")
    g.run("select", "1")
    sx, sy, sz = x, y, z - 4
    g.run("place", str(sx), str(sy), str(sz))
    # Its code turns it hot once it has taken the lava up.
    found["placed"] = block_until(g, sx, sy, sz, named(hot))
    found["lava_after"] = until(lava, lambda n: n == 0)
    found["hot"] = found["placed"].split("[")[0] == hot
    g.run("look", "180", "30")
    time.sleep(0.5)
    sponged_shot = hud_shot(g, "16-sponged")
    # On screen: the pool in the middle, orange lava before, the sponge over an empty basin after.
    found["pool_changed"] = round(difference(pool_shot, sponged_shot, (0.3, 0.35, 0.7, 0.85)), 1)
    return found


class GuestGame(KilnGame):
    """A second game with the same Kiln, mods and content, that joins `address` instead of opening a world."""

    def __init__(self, ctl, resources, out, address):
        super().__init__(ctl, resources, out)
        self.address = address

    def __enter__(self):
        self.out.mkdir(parents=True, exist_ok=True)
        self.dirs = [tempfile.mkdtemp(prefix=f"native-proof-guest-{n}-") for n in ("home", "launcher", "data", "config")]
        self.home = Path(self.dirs[0])
        self.socket = str(Path(tempfile.mkdtemp(prefix="native-ctl-guest-")) / "ctl.sock")
        self.env = dict(os.environ, HORIZON_HOME=self.dirs[0], HORIZON_LAUNCHER_HOME=self.dirs[1],
                        XDG_DATA_HOME=self.dirs[2], XDG_CONFIG_HOME=self.dirs[3],
                        HORIZON_LAUNCHER_NO_SELF_UPDATE="1", HORIZON_NO_AUDIO="1")
        self.run("launch", "--offscreen", "--resources", str(self.resources), "--server", self.address)
        return self


def switched(g, name):
    """Each Java mod has its own row and switch in Options > Mods (D-078): the player turns the mod named `name` off
    there, goes back to the title screen and into the world again; Kiln starts Java again without it."""
    def log():
        return json.dumps(g.run("logs", "--lines", "4000"))

    def running(text):
        counts = re.findall(r"Kiln: (\d+) of \d+ Java mods running", text)
        return int(counts[-1]) if counts else None

    before = log()
    found = {"running_before": running(before), "started_before": f"Kiln: started {name}" in before}
    g.run("options", "mods")
    time.sleep(1)
    # The page lists Kiln, then every Java mod by name: with Kiln's own proof mods (five since 0.4) a mod's row can be
    # below the list's visible part, and `ui` gives the visible widgets only. Scroll down until it shows.
    row, seen = None, []
    for _ in range(12):
        rows = {w.get("id"): w.get("label") for w in g.run("ui").get("widgets", [])}
        seen += [label for label in rows.values() if label not in seen]
        row = next((i for i, label in rows.items() if (label or "").startswith(f"{name} ")), None)
        if row is not None:
            break
        g.run("scroll-at", "213", "120", "-2")  # the list's middle, in GUI pixels (1280x720 at GUI scale 3)
        time.sleep(0.5)
    assert row is not None, f"no row for {name} in Options > Mods: {seen}"
    found["rows"] = sorted(label for label in seen if label)
    found["row_before"] = rows[row]
    g.run("click", row)
    time.sleep(1)
    found["row_after"] = {w.get("id"): w.get("label") for w in g.run("ui").get("widgets", [])}[row]
    g.shot("18-mods-page")
    g.run("click-button", "Done")
    time.sleep(0.5)
    g.play()
    g.run("pause")
    time.sleep(0.5)
    g.run("click-button", "Save and Quit to Title")
    time.sleep(4)
    g.run("open", "Proof")
    g.run("wait-ready", "60")
    deadline = time.time() + 30
    after = ""
    while time.time() < deadline:
        time.sleep(1)
        after = log()
        if after.count("Java mods running") > before.count("Java mods running"):
            break
    found["restarted"] = "Java starts again with the next world" in after
    later = after.split("Java starts again with the next world", 1)[-1] if found["restarted"] else ""
    found["running_after"] = running(later)
    found["started_after"] = f"Kiln: started {name}" in later
    return found


def lan_guest(g, ctl, resources, out, blocks):
    """Content declared in code reaches a LAN guest (D-078): the host sets the mod's blocks Kiln declares (Tech
    Reborn's machines, which Horizon's import leaves out) and opens the world to LAN; a second game with the same
    Kiln, jar and import joins it. Horizon lets a guest in only with the same blocks as the host, so the guest is in,
    and sees each block by the mod's id."""
    x, y, z = g.run("state")["player"]["block_pos"]
    g.run("command", "gamemode creative")
    g.run("command", f"fill {x - 1} {y - 1} {z - 4} {x + len(blocks) + 2} {y + 3} {z + 2} minecraft:air")
    g.run("command", f"fill {x - 1} {y - 1} {z - 4} {x + len(blocks) + 2} {y - 1} {z + 2} minecraft:stone")
    at = {}
    for i, block_id in enumerate(blocks):
        bx, by, bz = x + 1 + i, y, z - 3
        g.run("command", f"setblock {bx} {by} {bz} {block_id}")
        at[block_id] = (bx, by, bz)
    time.sleep(1)
    found = {"host": {b: block(g, *pos) for b, pos in at.items()}}
    opened = g.run("lan", "open", "0", "commands")
    found["opened"] = opened
    port = (opened or {}).get("port") if isinstance(opened, dict) else None
    if not port:
        port = (g.run("lan", "status") or {}).get("port")
    assert port, found
    with GuestGame(ctl, resources, Path(out) / "guest", f"localhost:{port}") as guest:
        deadline = time.time() + 120
        state = {}
        while time.time() < deadline:
            time.sleep(2)
            try:
                state = guest.run("state")
            except subprocess.CalledProcessError:
                continue
            if (state.get("player") or {}).get("block_pos"):
                break
        found["guest_joined"] = bool((state.get("player") or {}).get("block_pos"))
        found["guests"] = g.run("lan", "status")
        if found["guest_joined"]:
            # The host moves the guest in front of the machines (the guest's own commands aren't the point here).
            name = state["player"].get("name") or next(
                (p.get("name") for p in (found["guests"] or {}).get("guests", []) if p.get("name")), None)
            g.run("command", f"tp {name} {x + 0.5} {y} {z + 1.5} 180 20")
            time.sleep(4)
            found["guest_at"] = guest.run("state")["player"].get("block_pos")
            found["guest"] = {b: block(guest, *pos) for b, pos in at.items()}
            guest.shot("17-lan-guest")
        found["guest_log"] = re.findall(r"(?:refused|missing|differ|disconnect)[^\"]{0,200}",
                                        json.dumps(guest.run("logs", "--lines", "600")), re.I)[:10]
    return found


def payloads(g, ctl, resources, out):
    """A mod's own payloads over Horizon's channel for mods (D-088): Kiln's proof mod (kiln/proof-mods/net) sends the
    server a ping when the player types /kiln_proof_net; the server's receiver tells the player in chat and answers
    with a pong, which the player's receiver writes in chat with what the server saw. First the host, in single player
    (the window joins its own server through memory); then the world opens to LAN and a second game joins it, whose
    ping goes to the host's server over the network: its pong comes back with the host's count (2 served).

    The server's players and connections (D-092): each player is welcomed as they join (Fabric's JOIN) with how many
    are on the server (PlayerLookup.all); the host hears the guest join, ping near them (PlayerLookup.tracking) and
    leave (DISCONNECT)."""
    server = r"Kiln Proof Net: the server got ping \d+ [^\"\\]*"
    pong = r"Kiln Proof Net: pong \d+ [^\"\\]*"
    welcome = r"Kiln Proof Net: welcome [^\"\\]*"
    joined = r"Kiln Proof Net: \S+ joined, \d+ on the server"
    pinged = r"Kiln Proof Net: \S+ pinged near you"
    left = r"Kiln Proof Net: \S+ left"

    def said(game, pattern):
        # Each line once: the game's messages list a system line in more than one place.
        return list(dict.fromkeys(re.findall(pattern, json.dumps(game.run("messages", "100"), ensure_ascii=False))))

    def log_lines(game):
        return json.dumps(game.run("logs", "--lines", "4000")).split("\\n")

    def troubles(game):
        # What Kiln says when a payload goes wrong: not sent, dropped, unread, or its receiver failing.
        return [l for l in log_lines(game) if re.search(
            r"Kiln: .*payload.*(?:wasn't sent|dropped|didn't reach|no mod receives|failed in Java|no answer)"
            r"|unreadable payload", l)][:10]

    def typed(game, text):
        # As the player would: open the chat, type, press Enter (the chat screen asks the mods first).
        game.run("key", "t", "tap")
        time.sleep(0.5)
        game.run("type", text)
        game.run("key", "enter", "tap")

    def started(game):
        return until(lambda: "Kiln: started Kiln Proof Net" in json.dumps(game.run("logs", "--lines", "4000")),
                     bool, 60, every=1)

    def firsts(game):
        # Kiln logs the first payload of each type a mod's receiver has, on each side.
        return [re.search(r"the (?:server's|window's) first .*", l).group(0) for l in log_lines(game)
                if re.search(r"the (?:server's|window's) first .* payload", l)]

    found = {"host_started": started(g)}
    found["host_welcome"] = until(lambda: said(g, welcome), bool, 30, every=0.5)
    typed(g, "/kiln_proof_net")
    found["host_server"] = until(lambda: said(g, server), bool, 30, every=0.5)
    found["host_pong"] = until(lambda: said(g, pong), bool, 30, every=0.5)
    g.shot("19-net")
    opened = g.run("lan", "open", "0", "commands")
    port = (opened or {}).get("port") if isinstance(opened, dict) else None
    if not port:
        port = (g.run("lan", "status") or {}).get("port")
    assert port, opened
    with GuestGame(ctl, resources, Path(out) / "guest", f"localhost:{port}") as guest:
        deadline = time.time() + 120
        state = {}
        while time.time() < deadline:
            time.sleep(2)
            try:
                state = guest.run("state")
            except subprocess.CalledProcessError:
                continue
            if (state.get("player") or {}).get("block_pos"):
                break
        found["guest_joined"] = bool((state.get("player") or {}).get("block_pos"))
        if found["guest_joined"]:
            found["guest_started"] = started(guest)
            found["guest_welcome"] = until(lambda: said(guest, welcome), bool, 30, every=0.5)
            found["host_joined"] = until(lambda: said(g, joined), bool, 30, every=0.5)
            typed(guest, "/kiln_proof_net")
            found["guest_server"] = until(lambda: said(guest, server), bool, 30, every=0.5)
            found["guest_pong"] = until(lambda: said(guest, pong), bool, 30, every=0.5)
            found["host_pinged"] = until(lambda: said(g, pinged), bool, 30, every=0.5)
            guest.shot("20-net-guest")
            found["guest_firsts"] = firsts(guest)
        # The server's line went to the guest, not to the host's chat.
        found["host_server_after"] = said(g, server)
        found["guest_log"] = troubles(guest)
    # The guest's game closed: the host's server heard them leave.
    found["host_left"] = until(lambda: said(g, left), bool, 60, every=1)
    found["host_firsts"] = firsts(g)
    found["host_log"] = troubles(g)
    return found


def armour_on_hud(g):
    """Armour Durability HUD: the player, looking down at the ground, puts on a worn diamond helmet, an iron
    chestplate and worn leather boots; how much the top left of the screen changed (its icons and percentages)."""
    g.run("command", "gamemode creative")
    g.run("look", "0", "90")
    time.sleep(1.5)
    before = g.shot("9-before-armour")
    for slot, item in (("head", "minecraft:diamond_helmet[damage=120]"), ("chest", "minecraft:iron_chestplate"),
                       ("feet", "minecraft:leather_boots[damage=60]")):
        g.run("command", f"item replace entity @s armor.{slot} with {item}")
    time.sleep(2)
    after = g.shot("9-armour")
    # The chat (the commands' answers) and advancement toasts change elsewhere; the top left is the mod's.
    return {"top_left_changed": round(difference(before, after, (0.0, 0.0, 0.2, 0.35)), 1)}


def fps_on_hud(g):
    """Show FPS: the player looks at the sky; its counter is top left, and with the HUD hidden (F1) it is gone: how
    much the top left changed between the two."""
    g.run("command", "gamemode creative")
    g.run("look", "0", "-90")
    time.sleep(2)
    shown = g.shot("12-fps")
    g.run("key", "f1", "tap")
    time.sleep(1.5)
    hidden = g.shot("12-fps-hidden")
    g.run("key", "f1", "tap")
    time.sleep(1)
    return {"counter_drawn": round(difference(shown, hidden, COUNTER), 1)}


def held_damage(g):
    """The damage of what the player holds, as the server's `/data` says."""
    g.run("command", "data get entity @s SelectedItem.components.\"minecraft:damage\"")
    time.sleep(0.7)
    said = next((l for l in g.run("messages", "3").get("chat", []) if "entity data" in l), "")
    found = re.findall(r"(\d+)\s*$", said)
    return int(found[0]) if found else None


def experience(g):
    """The player's experience: levels and the points into the next, as `/xp query` says."""
    found = []
    for kind in ("levels", "points"):
        g.run("command", f"xp query @s {kind}")
        time.sleep(0.7)
        said = (g.run("messages", "1").get("chat") or [""])[0]
        number = re.findall(r"has (\d+)", said)
        found.append(int(number[0]) if number else None)
    return found


def mended(g):
    """Better Than Mending: a player on a platform high up, looking at the sky, with 10 levels and a diamond pickaxe
    with Mending worn by 300, right-clicks while sneaking, then again standing: damage and experience after each."""
    x, _, z = g.run("state")["player"]["block_pos"]
    g.run("command", f"fill {x - 1} 199 {z - 1} {x + 1} 199 {z + 1} minecraft:stone")
    g.run("command", f"tp @s {x + 0.5} 200 {z + 0.5}")
    g.run("command", "gamemode survival")
    g.run("command", "clear @s")
    g.run("command", "xp set @s 0 points")
    g.run("command", "xp set @s 10 levels")
    g.run("command", 'give @s minecraft:diamond_pickaxe[damage=300,enchantments={"minecraft:mending":1}]')
    g.run("select", "1")
    g.run("look", "0", "-90")
    time.sleep(1.5)
    found = {"before": {"damage": held_damage(g), "experience": experience(g)}}
    before = hud_shot(g, "10-before-mending")
    g.run("key", "shift", "down")
    time.sleep(0.5)
    g.run("use-item")
    time.sleep(1)
    g.run("key", "shift", "up")
    time.sleep(0.5)
    found["sneaking"] = {"damage": held_damage(g), "experience": experience(g)}
    mended_shot = hud_shot(g, "10-mended")
    g.run("use-item")
    time.sleep(1)
    found["standing"] = {"damage": held_damage(g), "experience": experience(g)}
    standing = hud_shot(g, "10-standing")
    # On screen: the experience level over the hotbar (10, then 9), and nothing standing.
    found["hotbar_changed"] = round(difference(before, mended_shot, LEVEL), 1)
    found["hotbar_changed_standing"] = round(difference(mended_shot, standing, LEVEL), 1)
    return found


def testbench_report(g):
    """Kiln's testbench in the game: its score, and what some probes read from the game (waits up to 60 s for it)."""
    report = g.home / "kiln" / "config" / "kiln_testbench" / "report.txt"
    deadline = time.time() + 60
    while not report.is_file() and time.time() < deadline:
        time.sleep(2)
    if not report.is_file():
        return {"score": None}
    time.sleep(3)
    text = report.read_text()
    lines = {}
    for line in text.splitlines():
        parts = line.split(None, 3)
        if len(parts) == 4 and parts[0] in ("PASS", "FAIL", "MISSING"):
            lines[parts[2]] = (parts[0] + " " + parts[3])[:120]
    score = text.splitlines()[1] if len(text.splitlines()) > 1 else ""
    picked = {k: lines.get(k) for k in ("level.height", "level.clockTime", "level.daylight", "player.food",
                                         "player.inventory", "player.state", "player.abilities", "client.singleplayer",
                                         "client.onlinePlayers", "client.mouse", "client.window",
                                         "event.clientCommands.fired", "client.sendCommand", "level.chunk",
                                         "player.effects", "event.chunkLoad.fired", "event.screenAfterInit.fired",
                                         "event.receiveGameMessage.fired", "client.resource")}
    failing = [k + ": " + v for k, v in lines.items() if not v.startswith("PASS")]
    return {"score": score, "probes": picked, "failing": failing, "commands": typed_commands(g)}


def screen_opened(g):
    """Between the testbench's world probes (a second into the world, one writes "[Kiln Testbench] chat probe" in the
    chat; one expects no screen) and its report (three seconds in): the player opens their inventory and closes it, so
    the testbench hears a screen open (ScreenEvents.AFTER_INIT)."""
    deadline = time.time() + 120
    while time.time() < deadline:
        try:
            if "[Kiln Testbench] chat probe" in json.dumps(g.run("logs", "--lines", "200")):
                break
        except subprocess.CalledProcessError:
            pass  # The game isn't answering yet.
        time.sleep(0.1)
    g.run("key", "e", "tap")
    time.sleep(0.5)
    g.run("key", "escape", "tap")


def typed_commands(g):
    """The testbench's client command typed in chat, then one no one has: Kiln takes the first before the game's own
    commands (no answer from them), the game answers the second. And the chat after the testbench's sendCommand."""
    def lines():
        return list(g.run("messages", "20").get("chat") or [])

    def typed(text):
        # As the player would: open the chat, type, press Enter (the chat screen asks the mods first).
        g.run("key", "t", "tap")
        time.sleep(0.5)
        g.run("type", text)
        g.run("key", "enter", "tap")
        time.sleep(1.5)

    before = lines()
    typed("/kiln_testbench")
    ours = [l for l in lines() if l not in before]
    typed("/kiln_no_such_command")
    unknown = [l for l in lines() if l not in before and l not in ours]
    return {"ours": ours, "unknown": unknown, "chat": before[-8:]}


def saved_data(g):
    """The Java mods' saved data in the world, by file."""
    return {str(f.relative_to(f.parents[1])): json.loads(f.read_text())
            for f in sorted((g.home / "saves").glob("*/kiln/data/*/*.json"))}


def respawn(g):
    """The player dies and respawns: their maximum health and health after it."""
    g.run("command", "kill @s")
    time.sleep(2)
    g.run("respawn")
    g.run("wait-ready", "60")
    time.sleep(2)
    health = (g.run("state").get("player") or {}).get("health")
    return {"max_health": max_health(g), "health": health}


TROUBLE = re.compile(r"Kiln: [^\"\\]*(?:failed in Java|\bthrew\b|Java stopped|didn't start|fails in Kiln|"
                     r"unexpected answer)[^\"\\]*")


OWN_PROOF_LINES = re.compile(r"proof mod's handler throws|MinecraftMomentsMixin|server JOIN listener threw kiln\.Host\$GaveUp")


def kiln_failures(g, pattern, lines=4000):
    """Kiln's log lines saying a call failed in Java or something threw (`pattern`), one line each, without the ones
    Kiln's own proof mods log on purpose (OWN_PROOF_LINES)."""
    text = g.run("logs", "--lines", str(lines))
    text = text if isinstance(text, str) else json.dumps(text)
    return [l.strip() for l in text.splitlines()
            if "Kiln: " in l and re.search(pattern, l) and not OWN_PROOF_LINES.search(l)]


def tried(g):
    """KILN_PROOF_TRY's steps played and its expectations read, with what every try checks: the mods started, nothing
    failed, the jar's structures found."""
    shots, seen = {}, {"said": [], "actionbar": []}

    def heard():
        said = g.run("messages", "100")
        # Players' chat is `chat`; the game's and the mods' messages (a command's answer) are `styled`, each a
        # component whose parts' texts make its line.
        seen["said"] = list(said.get("chat") or []) + ["".join(re.findall(r'"text": "((?:[^"\\]|\\.)*)"',
                                                                          json.dumps(line, ensure_ascii=False)))
                                                        for line in said.get("styled") or []]
        if said.get("actionbar"):
            seen["actionbar"].append(said["actionbar"])

    for name, *args in TRY.get("steps", []):
        args = [str(a) for a in args]
        if name == "command":
            g.run("command", args[0])
            time.sleep(0.7)
        elif name == "key":
            g.run("key", args[0], args[1] if len(args) > 1 else "tap")
            time.sleep(0.5)
        elif name == "type":
            g.run("key", "t", "tap")
            time.sleep(0.5)
            g.run("type", args[0])
            g.run("key", "enter", "tap")
            time.sleep(1.5)
        elif name == "use":
            g.run("use-item")
            time.sleep(1)
        elif name in ("select", "look"):
            g.run(name, *args)
            time.sleep(1)
        elif name == "wait":
            time.sleep(float(args[0]))
        elif name == "shot":
            drawn(g)
            shots[args[0]] = g.shot(f"try-{args[0]}")
        elif name == "hud":
            shots[args[0]] = hud_shot(g, f"try-{args[0]}")
        else:
            raise AssertionError(f"KILN_PROOF_TRY: no step {name}")
        heard()
    results = {"steps": len(TRY.get("steps", [])), "expected": []}
    log = json.dumps(g.run("logs", "--lines", "4000"), ensure_ascii=False)
    for e in TRY.get("expect", []):
        if "said" in e:
            text = seen["said"] + seen["actionbar"]
            ok = any(re.search(e["said"], line) for line in text)
            results["expected"].append({"said": e["said"], "ok": ok, "chat": text[-6:] if not ok else None})
        elif "logged" in e:
            results["expected"].append({"logged": e["logged"], "ok": bool(re.search(e["logged"], log))})
        else:
            kind = "changed" if "changed" in e else "same"
            a, b = e[kind]
            value = round(difference(shots[a], shots[b], tuple(e.get("box", (0.0, 0.0, 1.0, 1.0)))), 1)
            ok = value > e["over"] if kind == "changed" else value < e["under"]
            results["expected"].append({kind: [a, b], "box": e.get("box"), "value": value, "ok": ok})
    # Kiln's own proof mods are in every game, and some of their lines are on purpose: kiln_proof_mixins' handler
    # throws each tick (Kiln must go on), and a server JOIN listener gives up on its first call. They are no tried mod's.
    results["trouble"] = sorted(t for t in set(TROUBLE.findall(log)) if not OWN_PROOF_LINES.search(t))
    # Not failed, but read: a part of the mod that calls what Kiln has only the name of does nothing (a texture it
    # blits, a screen it opens), and the mod's note says so.
    results["name_only"] = sorted(set(re.findall(r"Kiln has only the name of (\S+) so far", log)))
    java_log = g.home / "kiln" / "java.log"
    results["java_log_exceptions"] = re.findall(r"^(?:Exception in thread .*|.*Uncaught.*)$",
                                                java_log.read_text(errors="replace") if java_log.is_file() else "",
                                                re.M)[:10]
    return results


def step(g, *commands, wait=1.5):
    for c in commands:
        g.run("command", c)
    time.sleep(wait)


def run(ctl, resources, out):
    assert (BUILT / "kiln-runtime.jar").is_file(), "run sh tools/build-java.sh first"
    with KilnGame(ctl, resources, out) as g:
        g.run("wait-ready", "60")
        deadline = time.time() + 30
        log = ""
        started = "Kiln: started Kiln Speedrun Timer" if TIMER else "Kiln: started "
        while time.time() < deadline and started not in log:
            time.sleep(1)
            log = json.dumps(g.run("logs", "--lines", "400"))
        time.sleep(2)
        g.shot("1-running")
        if ONLY == "try":
            java = len(BUILT_MODS) + sum(runs_java(j) for j in EXTRA + PROOF_MODS)
            deadline = time.time() + 60
            while time.time() < deadline and f"Kiln: {java} of {java} Java mods running" not in " ".join(chat(g)):
                time.sleep(1)
            results = {"all_mods_started": f"Kiln: {java} of {java} Java mods running" in " ".join(chat(g)),
                       "kiln_said": [l for l in chat(g) if l.startswith("Kiln:")], "structures": {}}
            if any(structures(j)[0] for j in EXTRA):
                g.run("command", "gamemode spectator")
                for jar in EXTRA:
                    ids, blocks = structures(jar)
                    places = []
                    for structure in ids:
                        found, where, holds = structure_found(g, structure, blocks, places)
                        results["structures"][structure] = {"blocks_of_its_pieces": found, "where": where,
                                                            "holds": holds}
                g.run("command", "gamemode creative")
                g.run("command", "execute in minecraft:overworld run tp @s 0 120 0")
                g.run("wait-ready", "90")
            results.update(tried(g))
            print(json.dumps(results, indent=2, ensure_ascii=False))
            assert results["all_mods_started"], results
            assert not results["trouble"] and not results["java_log_exceptions"], results
            assert all(s["blocks_of_its_pieces"] >= TRY.get("pieces", 100) for s in results["structures"].values()), \
                results
            for structure in results["structures"].values():
                assert all(structure["holds"].get(name, 0) >= n for name, n in STRUCTURE_HAS.items()), results
            assert all(e["ok"] for e in results["expected"]), results
            print(json.dumps({"proof": "kiln", "try": True, "ok": True}, indent=2))
            return
        if ONLY == "mods":
            names = " ".join(j.name for j in EXTRA)
            results = {}
            # First: the armour's icons, put on next, would be top left too.
            if "showfps" in names:
                results["fps"] = fps_on_hud(g)
            if "armour-durability" in names:
                results["armour"] = armour_on_hud(g)
            if "BetterThanMending" in names:
                results["mending"] = mended(g)
            if "testbench" in names:
                results["testbench"] = testbench_report(g)
            results["failed_in_java"] = kiln_failures(g, r" (?:failed in Java|threw) ")
            print(json.dumps(results, indent=2, ensure_ascii=False))
            if "fps" in results:
                # The counter changes that corner by 40 to 50; the clouds behind it, by a few.
                assert results["fps"]["counter_drawn"] > 10, results
            if "armour" in results:
                assert results["armour"]["top_left_changed"] > 4, results
            if "mending" in results:
                m = results["mending"]
                # 20 points repair 40: the pickaxe goes from 300 to 260, ten levels to nine and a bit.
                assert m["before"]["damage"] == 300 and m["sneaking"]["damage"] == 260, results
                assert m["sneaking"]["experience"][0] == 9, results
                # Standing, the mod lets the use go on: nothing changes.
                assert m["standing"] == m["sneaking"], results
                # On screen too: the level over the hotbar changed sneaking, and less standing.
                assert m["hotbar_changed"] > 1 and m["hotbar_changed_standing"] < m["hotbar_changed"], results
            if "testbench" in results:
                t = results["testbench"]
                # Every probe passes in the game: what it reads of Horizon's game, and what it plays and draws.
                passing, total = (int(n) for n in t["score"].split()[0:3:2]) if t.get("score") else (0, 1)
                assert passing == total and not t["failing"], results
                assert t["probes"]["level.height"].startswith("PASS") and "-64..319" in t["probes"]["level.height"], results
                # The testbench's client command is Kiln's: the game's commands never answer it; they do answer one
                # no mod has.
                assert not any("Unknown or incomplete command" in l for l in t["commands"]["ours"]), results
                assert any("Unknown or incomplete command" in l for l in t["commands"]["unknown"]), results
            assert not results["failed_in_java"], results
            print(json.dumps({"proof": "kiln", "mods": sorted(k for k in results if k != "failed_in_java"),
                              "ok": True}, indent=2))
            return
        if ONLY == "switch":
            results = {"switch": switched(g, SWITCH)}
            print(json.dumps(results, indent=2, ensure_ascii=False))
            s = results["switch"]
            assert s["row_before"].endswith(": ON") and s["row_after"].endswith(": OFF"), results
            assert s["restarted"] and s["started_before"] and not s["started_after"], results
            assert s["running_after"] == s["running_before"] - 1, results
            print(json.dumps({"proof": "kiln", "switch": True, "ok": True}, indent=2))
            return
        if ONLY == "lan":
            results = {"lan": lan_guest(g, ctl, resources, out, LAN)}
            print(json.dumps(results, indent=2, ensure_ascii=False))
            r = results["lan"]
            assert r["guest_joined"], results
            assert all(r["host"][b].split("[")[0] == b for b in LAN), results
            assert all(r["guest"][b].split("[")[0] == b for b in LAN), results
            print(json.dumps({"proof": "kiln", "lan": True, "ok": True}, indent=2))
            return
        if ONLY == "net":
            results = {"net": payloads(g, ctl, resources, out)}
            print(json.dumps(results, indent=2, ensure_ascii=False))
            r = results["net"]
            got = "Kiln Proof Net: the server got ping 1 (hello) from "
            # Single player: the ping reached the host's server, the pong came back.
            assert len(r["host_server"]) == 1 and r["host_server"][0].startswith(got), results
            host = r["host_server"][0][len(got):]
            assert len(r["host_pong"]) == 1 and r["host_pong"][0].startswith(
                "Kiln Proof Net: pong 1 (hello) back, 1 served, at "), results
            # A LAN guest: its ping crossed the network to the host's server (its second), and the pong came back to
            # the guest, for the guest.
            assert r["guest_joined"], results
            assert len(r["guest_server"]) == 1 and r["guest_server"][0].startswith(got), results
            assert r["guest_server"][0][len(got):] != host, results
            assert len(r["guest_pong"]) == 1 and r["guest_pong"][0].startswith(
                "Kiln Proof Net: pong 1 (hello) back, 2 served, at "), results
            assert r["guest_pong"][0].split(" for ")[-1] != r["host_pong"][0].split(" for ")[-1], results
            assert r["host_server_after"] == r["host_server"], results
            assert not r["host_log"] and not r["guest_log"], results
            # Each joining player had the server's welcome as they joined, counting who was on it then; the host heard
            # the guest join, ping near them and leave (D-092).
            guest_name = r["guest_server"][0][len(got):]
            assert r["host_welcome"] == [f"Kiln Proof Net: welcome {host}, 1 on the server"], results
            assert r["guest_welcome"] == [f"Kiln Proof Net: welcome {guest_name}, 2 on the server"], results
            assert r["host_joined"] == [f"Kiln Proof Net: {guest_name} joined, 2 on the server"], results
            assert r["host_pinged"] == [f"Kiln Proof Net: {guest_name} pinged near you"], results
            assert r["host_left"] == [f"Kiln Proof Net: {guest_name} left"], results
            # A real mod's handshake, when its jar is in KILN_PROOF_MODS: Elytra Wind Brake's client asks the server
            # whether it has the mod as it joins (elytrawindbrake:check), and the server answers (present); on the
            # host, and on the guest from the host's server.
            if any("elytra" in j.name.lower() for j in EXTRA):
                check, present = "first elytrawindbrake:check payload", "first elytrawindbrake:present payload"
                assert any(check in l and f"from {host}," in l for l in r["host_firsts"]), results
                assert any(present in l for l in r["host_firsts"]), results
                assert any(present in l for l in r["guest_firsts"]), results
            # A real mod's first sync on JOIN, when its jar is in KILN_PROOF_MODS: Saro's Sticky Leaves' server sends
            # each joining player its config (sarosstickyleaves:config_sync), which its client takes; on the host,
            # and on the guest as it joins, before its own Java has started (D-092).
            if any("sticky-leaves" in j.name.lower() for j in EXTRA):
                synced = "the window's first sarosstickyleaves:config_sync payload"
                assert any(synced in l for l in r["host_firsts"]), results
                assert any(synced in l for l in r["guest_firsts"]), results
            print(json.dumps({"proof": "kiln", "net": True, "ok": True}, indent=2))
            return
        if ONLY == "sponge":
            # A block mod from Modrinth whose blocks Horizon's import carries whole: only its code is Kiln's.
            sponge, hot = SPONGE
            results = {"sponge": sponged(g, sponge, hot)}
            results["failed_in_java"] = kiln_failures(g, r" (?:failed in Java|threw) ")
            print(json.dumps(results, indent=2, ensure_ascii=False))
            s = results["sponge"]
            assert s["lava_before"] == 9 and s["lava_after"] == 0 and s["hot"], results
            assert not results["failed_in_java"], results
            print(json.dumps({"proof": "kiln", "sponge": True, "ok": True}, indent=2))
            return
        if ONLY == "carry":
            # Only the blocks Kiln carried: the mods they come from may not run under Kiln yet.
            results = carried(g)
            if SMELT:
                x, y, z = g.run("state")["player"]["block_pos"]
                results["smelted"] = smelted(g, x + 2, y, z)
            if POWER:
                x, y, z = g.run("state")["player"]["block_pos"]
                results["powered"] = powered(g, x + 2, y, z + 3)
            if SCREEN:
                x, y, z = g.run("state")["player"]["block_pos"]
                furnace, panel = SCREEN
                results["screen"] = by_hand(g, x + 2, y, z - 3, furnace, panel, "minecraft:raw_iron",
                                            "minecraft:iron_ingot", 2, "10-screen")
            if GRIND:
                x, y, z = g.run("state")["player"]["block_pos"]
                grinder, panel = GRIND
                results["grind"] = by_hand(g, x + 2, y, z - 7, grinder, panel, "minecraft:iron_ore",
                                           "minecraft:raw_iron", 4, "11-grinder")
            if FLUIDS:
                x, y, z = g.run("state")["player"]["block_pos"]
                results["fluids"] = fluids(g, x + 2, y, z + 8)
            if CABLE:
                x, y, z = g.run("state")["player"]["block_pos"]
                results["cable"] = cabled(g, x + 2, y, z + 14)
            if TRANSFER:
                x, y, z = g.run("state")["player"]["block_pos"]
                results["transfer"] = fed(g, x + 2, y, z - 12)
            # The machines' own code: the other carried machines may need what Kiln hasn't yet.
            ran = [SMELT] * bool(SMELT) + POWER + SCREEN + GRIND + FLUIDS + CABLE + TRANSFER
            if ran:
                results["failed_in_java"] = kiln_failures(g, rf" of (?:{'|'.join(ran)}) failed in Java: ")
            print(json.dumps(results, indent=2, ensure_ascii=False))
            assert results["log"], results
            assert all(state.split("[")[0] == block_id for block_id, state in results["blocks"].items()), results
            if SMELT:
                assert results["smelted"]["burning"] == f"{SMELT}[active=true,facing=west]", results
                assert results["smelted"]["ingots"] == 2, results
            if POWER:
                assert results["powered"]["panel"] == f"{POWER[1]}[active=true]", results
                assert results["powered"]["ingots"] == 2, results
                assert results["powered"]["furnace_energy"], results
            if SCREEN:
                assert results["screen"]["made_held"] == 2, results
            if GRIND:
                assert results["grind"]["made_held"] == 4, results
            if FLUIDS:
                assert results["fluids"]["lava_kept"], results
                assert results["fluids"]["lava_in"] and results["fluids"]["water_in"], results
                assert results["fluids"]["raw_iron_held"] == 3, results
                # Tech Reborn's own items, by the ids its code makes them by (D-074): three of each, none of them air.
                assert results["fluids"]["held"] == {"minecraft:raw_iron": 3, "techreborn:tin_nugget": 3,
                                                     "techreborn:nickel_nugget": 3}, results
                assert not any("minecraft:air" in o for o in results["fluids"]["outputs"].values()), results
            if CABLE:
                assert results["cable"]["ingots"] == 2, results
                assert results["cable"]["furnace_energy"] is not None, results
            if TRANSFER:
                assert results["transfer"]["ingots"] == 4, results
                assert not results["transfer"]["left_in_grinder"], results
            if SMELT or POWER or SCREEN or GRIND or FLUIDS or CABLE or TRANSFER:
                assert not results["failed_in_java"], results
            print(json.dumps({"proof": "kiln", "carry": True, "ok": True}, indent=2))
            return
        # Structure mods: their structures generate in the world by themselves. Checked before the run takes the
        # player to the End, as a spectator (who doesn't fall into the sea the structure stands in).
        structures_found, structures_checked_in = {}, None
        if EXTRA:
            g.run("command", "gamemode spectator")
            structures_checked_in = g.run("state").get("dimension")
            for jar in EXTRA:
                ids, blocks = structures(jar)
                places = []
                for structure in ids:
                    found, where, holds = structure_found(g, structure, blocks, places)
                    structures_found[structure] = {"blocks_of_its_pieces": found, "where": where, "holds": holds}
            g.run("command", "gamemode creative")
            g.run("command", "tp @s 0 120 0")
            g.run("wait-ready", "90")
        stars = loot_rolled(g, "minecraft:chests/simple_dungeon", "minecraft:nether_star")
        blocks = proof_blocks(g)
        data_after_join = saved_data(g)
        used, used_seen, respawned = {}, {}, None
        if USE:
            g.run("command", "gamemode survival")
            for item in USE:
                used[item], used_seen[item] = use_three(g, item)
            respawned = respawn(g)
            g.run("command", "gamemode creative")
            g.run("command", "clear @s")
        step(g, "advancement grant @s only minecraft:story/enter_the_nether")
        step(g, "execute in minecraft:the_nether run tp @s 0 100 0", wait=3)
        step(g, "advancement grant @s only minecraft:nether/find_bastion",
             "advancement grant @s only minecraft:nether/find_fortress")
        step(g, "execute in minecraft:overworld run tp @s 0 120 0", wait=3)
        step(g, "advancement grant @s only minecraft:story/follow_ender_eye")
        g.run("pause")
        # The timer sees the pause at its next client tick: two seconds from once the game has drawn a few frames
        # paused (on a busy computer a frame can take half a second).
        drawn(g)
        time.sleep(2)
        g.run("resume")
        g.play()
        step(g, "execute in minecraft:the_end run tp @s 0 100 0", wait=3)
        g.shot("2-splits")
        step(g, "advancement grant @s only minecraft:end/kill_dragon", wait=2)
        finished = g.shot("3-finished")
        # The timer's hide key (U), heard by Kiln and handed to the mod's KeyMapping: the panel goes.
        g.run("key", "u", "tap")
        time.sleep(1.5)
        hidden = g.shot("4-hidden")
        lines = chat(g)
        java = len(BUILT_MODS) + sum(runs_java(j) for j in EXTRA + PROOF_MODS)
        records = g.home / "kiln" / "config" / "kiln_timer" / "records.txt"
        final = next((l for l in lines if "Final IGT" in l), "")
        times = re.findall(r"(\d+):(\d\d)\.(\d\d\d)", final)
        seconds = [int(m) * 60 + int(s) + int(ms) / 1000 for m, s, ms in times]
        results = {
            "all_mods_started": f"Kiln: {java} of {java} Java mods running" in " ".join(lines),
            "chat": lines,
            "splits_said": [s for s in ("Nether", "Bastion", "Fortress", "Blind", "Stronghold", "End")
                            if any(l.startswith(f"[Kiln Timer] {s} ") for l in lines)],
            "final": final,
            "rta_minus_igt": round(seconds[1] - seconds[0], 3) if len(seconds) == 2 else None,
            "records_saved": records.is_file() and "pb.FINISH=" in records.read_text(),
            "panel_hidden_by_key": round(difference(finished, hidden, TOP_RIGHT), 1),
            # A first run has no personal best to compare to: no deltas.
            "deltas_on_first_run": [l for l in lines if l.startswith("[Kiln Timer]") and "(" in l],
            "structures": structures_found,
            "structures_checked_in": structures_checked_in,
            "nether_stars_from_five_dungeon_chests": stars,
            "blocks": blocks,
            "data_after_join": data_after_join,
            "used": used,
            "used_seen": used_seen,
            "respawned": respawned,
            "data_at_the_end": saved_data(g),
            "failed_in_java": kiln_failures(g, r" failed in Java: ", 2000),
        }
        print(json.dumps(results, indent=2, ensure_ascii=False))
        assert results["all_mods_started"], results
        assert all(s["blocks_of_its_pieces"] > 100 for s in results["structures"].values()), results
        assert not EXTRA or results["structures_checked_in"] == "minecraft:overworld", results
        assert not results["failed_in_java"], results
        assert results["nether_stars_from_five_dungeon_chests"] == 5, results
        b = results["blocks"]
        assert b["switch_clicked"] == "kiln_proof_blocks:switch[lit=true]", results
        assert b["switch_said"] == "Switch on, on minecraft:gold_block", results
        assert b["switch_clicked_again"] == "kiln_proof_blocks:switch[lit=false]", results
        assert b["switch_stood_on"] == "kiln_proof_blocks:switch[lit=true]", results
        assert b["sprout_planted"] == "kiln_proof_blocks:sprout[age=0,ripe=false]", results
        assert b["sprout_ticked"] == "kiln_proof_blocks:sprout[age=0,ripe=true]", results
        assert b["timer_placed"] == "kiln_proof_blocks:timer[facing=south,lit=false]", results
        assert b["timer_ticked"] == "kiln_proof_blocks:timer[facing=south,lit=true]", results
        assert b["timer_beside_a_change"] == "kiln_proof_blocks:timer[facing=south,lit=false]", results
        assert b["timer_broken"] == "minecraft:air", results
        assert b["timer_said"] == "Timer broken", results
        assert b["timer_dropped_a_star"], results
        assert b["counter_set"] == "kiln_proof_blocks:counter[facing=north,lit=false]", results
        assert b["counter_counted"] == "kiln_proof_blocks:counter[facing=north,lit=true]", results
        counted = re.search(r"ticks: (\d+), loads: 1, .*placed_in: \"minecraft:overworld\"", b["counter_data"] or "")
        assert counted and int(counted[1]) >= 40, results
        assert b["counter_clicked"] == "kiln_proof_blocks:counter[facing=north,lit=false]", results
        said = re.fullmatch(r"Counter at (\d+), loaded 1 time\(s\), in minecraft:overworld", b["counter_said"] or "")
        assert said and int(said[1]) >= int(counted[1]), results
        assert b["crusher_working"] == "kiln_proof_blocks:crusher[facing=north,lit=true]", results
        assert 'id: "minecraft:gravel"' in (b["crusher_gave"] or ""), results
        assert re.search(r"crushed: [1-4]\b", b["crusher_data"] or ""), results
        # A Java mod's item did something the game shows: the player's maximum health, or the item's count, changed.
        assert all(any((u["max_health"], u["count"]) != (uses[0]["max_health"], uses[0]["count"]) for u in uses[1:])
                   for uses in results["used"].values()), results
        # And it shows: the hearts or the item in the first hotbar slot changed on screen.
        assert all(seen["hearts_changed"] > 3 for seen in results["used_seen"].values()), results
        # What the uses gave is kept through a respawn (vanilla's player comes back at 20).
        assert not USE or results["respawned"]["max_health"] == next(iter(results["used"].values()))[-1]["max_health"], \
            results
        assert not USE_SAID or any(u.get("action_bar") == USE_SAID for uses in results["used"].values()
                                   for u in uses), results
        for structure in results["structures"].values():
            assert all(structure["holds"].get(name, 0) >= n for name, n in STRUCTURE_HAS.items()), results
        if TIMER:
            assert results["splits_said"] == ["Nether", "Bastion", "Fortress", "Blind", "Stronghold", "End"], results
            assert "New personal best" in final, results
            assert results["rta_minus_igt"] is not None and results["rta_minus_igt"] >= 1.5, results
            assert results["records_saved"], results
            assert not results["deltas_on_first_run"], results
            assert results["panel_hidden_by_key"] > 4, results
        print(json.dumps({"proof": "kiln", "ok": True}, indent=2))
