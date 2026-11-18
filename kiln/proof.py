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
in the grinder (16-transfer.png). With KILN_PROOF_CHEST (the same three), the game's own containers as Fabric storages
(D-144): a grinder pushing east into a hopper, which hands its items on to a chest east of it, west of an electric
furnace that takes its input from the west by itself; a panel south of each machine, two iron ore in the grinder: its
four raw iron must go into the hopper, the chest and the furnace, and come out as four iron ingots (17-chest.png).

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
["key", "shift", "down"] or ["key", "k", "double"] (two quick taps), ["key", "backspace", 3] (three taps),
["key", "ctrl+a"] or ["key", "ctrl+shift+left", 2] (with the modifiers held), ["key", "backspace", "repeat", 5] (held,
auto-repeating), ["type", "/calc 2+2"] in the chat, ["use"], ["select", 1], ["look", 0, 90],
["wait", 1.5], ["play"] back in the world from any screen, ["shot", "name"], ["hud", "name"] with the chat hidden, ["mark", "name"] where the chat is now,
["hover", x, y] the pointer to GUI pixel x y, ["click", x, y] a left click there (["click", x, y, "right"] a right one),
["scroll", x, y, steps] the wheel turned there (positive scrolls up),
["drag", x1, y1, x2, y2, ...] the left button pressed at the first point, moved through the others, released at the last,
["text", "planks"] typed into the open screen's focused field (not the chat), ["restart"] the game quit and started again in the same folders and world (what a mod saved must come back),
["ui", "name"] the open screen's widgets read (and kept, try-<name>.json), ["clipboard", "name"] the text the game last copied read,
["ctl", "options", "keybinds"] any horizon-ctl command, ["play"] back to the world, ["labels", "name"] the open screen's labels, ["inventory", "name"]
the player's slots, ["xkey", "ctrl+a"] or ["xkey", "shift+left", 2] or ["xkey", "backspace", "hold", 1.2] and
["xtext", "oak planks"] through the game's window as a real keyboard types (XTest on the try's X server, D-178),
["guest", "join"] a second game joining the host's world opened to LAN (the steps after it are the guest's),
["guest", "rejoin"] its game quit and started again, joining again, ["guest", "leave"] back to the host,
["file", "name", "glob"] the files the mods wrote in this game's folder (each its path, then its text), for a "read",
["craft", "ns:recipe"] a crafting recipe of the jar's made by hand in survival: its ingredients put into the
inventory's 2x2 grid, or a 3x3 crafting table's set beside the player, and its result taken with a shift-click, with
{"#tag": "item"} after it to choose an item for a tag (or {"grid": [items, row by row, "" empty], "makes": [item,
count]} for a recipe of the mod's own type, matched by its Java code, D-212); a smithing, stonecutting or cooking recipe at its station, set
beside the player: the smithing table's or stonecutter's slots, a furnace's input with coal as its fuel, D-196);
`options`, the game's options in options.txt before the game starts ({"tutorialStep": "5"}: no tutorial toast);
`keys`, the keys bound on the mods' pages in Options > Mods before the game starts, by row ({"kiln_fovtoggle":
{"key.fovtoggle": "key.keyboard.b"}}), as the player binds a mod's key there (D-096);
`expect`, what must show: {"said": regex} in the chat or the action bar ({"unsaid": regex} not; either between two
marks with "from" and "to"), {"logged": regex} in the game's log or Kiln's java.log (the mods' own loggers),
{"crafted": "ns:recipe"} its result in the result slot and then in the inventory, as many as it makes,
{"changed": ["a", "b"],
"box": [left, top, right, bottom], "over": n} between two screenshots (try-<name>.png), {"same": ...} for under n,
{"labeled": regex, "in": "name"} a label a "labels" step read, {"holds": "ns:item", "count": n, "most": m, "in": "name"}
in the slots and on the cursor an "inventory" step read (at least n, 1 by default, and at most m), {"file": "glob", "has": regex} a file the mods wrote in the game's folder (a glob) holding the text,
{"read": regex, "in": "name"} in what a "ui", "clipboard" or "file" step read.
The proof's game has the tutorial turned off (options.txt's tutorialStep:none): its toast would cover the top right.
"""
import collections
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
import zlib
from pathlib import Path

from game import Game, difference

BUILT = Path(__file__).resolve().parent.parent / "target" / "kiln"
# KILN_PROOF_LEAVE_OUT (comma-separated jar names' starts) leaves proof mods out: a try judged by its screenshots leaves
# out kiln-proof-mixins, whose line "Kiln bridge tooltip <count>" is in every item tooltip (D-172).
LEAVE_OUT = [p for p in os.environ.get("KILN_PROOF_LEAVE_OUT", "").split(",") if p]
PROOF_MODS = [j for j in sorted((BUILT / "proof").glob("*.jar")) if not any(j.name.startswith(p) for p in LEAVE_OUT)]
# The mods tools/build-java.sh builds into the game: the spike's, and the timer where mods/kiln-timer is.
BUILT_MODS = sorted((BUILT / "mods").glob("*.jar"))
TIMER = any(j.name.startswith("kiln-timer-") for j in BUILT_MODS)
# Their data for Horizon, as its import would carry it (kiln/proof-mods/<name>/horizon/).
PROOF_CONTENT = sorted(p.parent for p in (BUILT.parent.parent / "kiln" / "proof-mods").glob("*/horizon/*.toml"))
EXTRA = [Path(p) for p in os.environ.get("KILN_PROOF_MODS", "").split(os.pathsep) if p]
CONTENT = [Path(p) for p in os.environ.get("KILN_PROOF_CONTENT", "").split(os.pathsep) if p]
CONFIG = os.environ.get("KILN_PROOF_CONFIG")
# A proof never goes online: the game's Kiln refreshes the catalogue's copy only when this isn't "off" (D-128).
os.environ.setdefault("KILN_CATALOGUE_REFRESH", "off")
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
CHEST = [b for b in os.environ.get("KILN_PROOF_CHEST", "").split(",") if b]
# A Tech Reborn machine's slot settings, as its screen sets them (`slotConfig`, merged into what its code wrote): the
# grinder's output (slot 1) out of its east face by itself, the furnace's input (slot 0) from its west face.
SLOT_OUT_EAST = "{slot_1:{side_5:{config:{config:2}},output:1b}}"
SLOT_IN_WEST = "{slot_0:{side_4:{config:{config:1}}}}"
# The furnace's input from its west face, taken by itself (what a chest there holds).
SLOT_TAKE_WEST = "{slot_0:{side_4:{config:{config:1}},input:1b}}"
SPONGE = [b for b in os.environ.get("KILN_PROOF_SPONGE", "").split(",") if b]
LAN = [b for b in os.environ.get("KILN_PROOF_LAN", "").split(",") if b]
SWITCH = os.environ.get("KILN_PROOF_SWITCH")
# A mod tried in the game by itself (KILN_PROOF_ONLY=try, D-084): what the player does and what must show, as JSON.
TRY = json.loads(os.environ.get("KILN_PROOF_TRY") or "{}")
# The game's options file's version (horizon-core's DATA_VERSION for 26.3): an options.txt with an older one is
# updated by the game's fixers first.
OPTIONS_VERSION = 5023
# The overlay's key in its proof (KILN_PROOF_ONLY=overlay): F7 by default, which horizon-ctl can't press.
OVERLAY_KEY = "f6"


def runs_java(jar):
    """Whether a mod runs in Kiln's Java: it has entrypoints, or mixins (a mod with mixins and no entrypoint runs too,
    D-099); data-only mods (structures) have neither."""
    with zipfile.ZipFile(jar) as z:
        if "fabric.mod.json" not in z.namelist():
            return False
        manifest = json.loads(z.read("fabric.mod.json").decode("utf-8", "replace"), strict=False)
    entrypoints = manifest.get("entrypoints") or {}
    return bool(entrypoints.get("main") or entrypoints.get("client") or manifest.get("mixins"))


def java_mods(jars):
    """The ids of the Java mods the jars bring as Kiln starts them: each jar and every jar its fabric.mod.json nests
    ("jars": JEI's mezz_config), each id once, those that run Java (`runs_java`)."""
    ids = set()

    def add(jar):
        with zipfile.ZipFile(jar) as z:
            if "fabric.mod.json" not in z.namelist():
                return
            manifest = json.loads(z.read("fabric.mod.json").decode("utf-8", "replace"), strict=False)
            for nested in manifest.get("jars") or []:
                name = nested.get("file") if isinstance(nested, dict) else None
                if name and name in z.namelist():
                    add(io.BytesIO(z.read(name)))
        if runs_java(jar):
            ids.add(manifest.get("id"))

    for jar in jars:
        add(jar)
    return ids
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


def nbt(raw):
    """A structure piece (`.nbt`, gzip) as Python values: compounds as dicts, lists as lists; arrays are skipped."""
    s = io.BytesIO(gzip.decompress(raw))

    def string():
        (n,) = struct.unpack(">H", s.read(2))
        return s.read(n).decode("utf-8", "replace")

    def payload(tag):
        sizes = {1: ">b", 2: ">h", 3: ">i", 4: ">q", 5: ">f", 6: ">d"}
        if tag in sizes:
            (value,) = struct.unpack(sizes[tag], s.read(struct.calcsize(sizes[tag])))
            return value
        if tag in (7, 11, 12):
            (n,) = struct.unpack(">i", s.read(4))
            s.read(n * {7: 1, 11: 4, 12: 8}[tag])
            return None
        if tag == 8:
            return string()
        if tag == 9:
            item = s.read(1)[0]
            (n,) = struct.unpack(">i", s.read(4))
            return [payload(item) for _ in range(n)]
        values = {}
        while (t := s.read(1)[0]) != 0:
            key = string()
            values[key] = payload(t)
        return values

    tag = s.read(1)[0]
    string()
    return payload(tag)


def piece_blocks(raw):
    """A piece's blocks by name (without `minecraft:`), and the template pools its jigsaws name. A palette entry names
    its block as `Name` (older pieces) or `id` (26.3's, as the game's own: The Ice Warrior's tower read as none, #80)."""
    root = nbt(raw)
    palette = root.get("palette") or (root.get("palettes") or [[]])[0]
    names = [(p.get("Name") or p.get("id") or "").removeprefix("minecraft:") for p in palette]
    counts, pools = collections.Counter(), set()
    for b in root.get("blocks") or []:
        if 0 <= b.get("state", -1) < len(names):
            counts[names[b["state"]]] += 1
        pool = (b.get("nbt") or {}).get("pool")
        if isinstance(pool, str) and pool not in ("minecraft:empty", "empty"):
            pools.add(pool)
    return counts, pools


def palette(raw):
    """Block names in a structure piece's palette (`.nbt`, gzip)."""
    return {"minecraft:" + n if ":" not in n else n for n in piece_blocks(raw)[0]}


def namespaced(i):
    return i if ":" in i else "minecraft:" + i


class StructureData:
    """A mod's structures as the game reads them: the jar's data (a pack overlay made for 26.3 over its base, as
    structures.rs copies them), and the game's own under it (`resources`: its data and its pieces)."""

    def __init__(self, jar, resources=None):
        self.zip, self.resources = zipfile.ZipFile(jar), Path(resources) if resources else None
        overlays = []
        try:
            meta = json.loads(self.zip.read("pack.mcmeta").decode("utf-8-sig"))
            for o in meta.get("overlays", {}).get("entries", []):
                f, low, high = o.get("formats"), o.get("min_format"), o.get("max_format")
                if low is not None and high is not None:
                    span = [v if isinstance(v, (int, float)) else v[0] for v in (low, high)]
                elif isinstance(f, (int, float)):
                    span = [f, f]
                elif isinstance(f, list):
                    span = f[:2]
                elif isinstance(f, dict):
                    span = [f.get("min_inclusive"), f.get("max_inclusive")]
                else:
                    continue
                if None not in span and span[0] <= 121 <= span[1]:
                    overlays.append(o.get("directory"))
        except (KeyError, ValueError, TypeError, IndexError):
            pass
        self.files = {}
        for n in self.zip.namelist():
            layer, name = 0, n
            top, _, rest = n.partition("/")
            if rest.startswith("data/"):
                if top not in overlays:
                    continue
                layer, name = overlays.index(top) + 1, rest
            name = re.sub(r"^data/([^/]+)/structures/", r"data/\1/structure/", name)
            if name not in self.files or layer >= self.files[name][0]:
                self.files[name] = (layer, n)
        self.pieces = {}

    def own(self, path):
        return path in self.files

    def json(self, kind, ident, vanilla=True):
        """`data/<ns>/<kind>/<path>.json`: the jar's, else the game's (None if neither, or not JSON)."""
        ns, path = namespaced(ident).split(":", 1)
        name = f"data/{ns}/{kind}/{path}.json"
        try:
            if name in self.files:
                return json.loads(self.zip.read(self.files[name][1]).decode("utf-8-sig"))
            game = self.resources / name if self.resources and vanilla else None
            return json.loads(game.read_text()) if game and game.is_file() else None
        except (ValueError, OSError):
            return None

    def piece(self, ident, vanilla=True):
        """A piece's blocks and its jigsaws' pools (piece_blocks): the jar's, else the game's; None if neither."""
        ns, path = namespaced(ident).split(":", 1)
        key = (namespaced(ident), vanilla)
        if key not in self.pieces:
            name = f"data/{ns}/structure/{path}.nbt"
            game = self.resources / "worldgen" / "structure" / ns / f"{path}.nbt" if self.resources else None
            try:
                if name in self.files:
                    self.pieces[key] = piece_blocks(self.zip.read(self.files[name][1]))
                elif vanilla and game and game.is_file():
                    self.pieces[key] = piece_blocks(game.read_bytes())
                else:
                    self.pieces[key] = None
            except (OSError, EOFError, struct.error, IndexError, zlib.error, gzip.BadGzipFile):
                self.pieces[key] = None
        return self.pieces[key]

    def biomes(self, spec, seen=None):
        """The game's biomes `spec` (an id, a tag, or a list) names, and the names that are none of the game's."""
        seen = set() if seen is None else seen
        found, others = set(), set()
        for item in spec if isinstance(spec, list) else [spec]:
            item = item.get("id", "") if isinstance(item, dict) else item
            if not isinstance(item, str) or not item:
                continue
            if item.startswith("#"):
                tag = namespaced(item[1:])
                if tag in seen:
                    continue
                seen.add(tag)
                ns, path = tag.split(":", 1)
                values = []
                mine = self.json("tags/worldgen/biome", tag, vanilla=False)
                game = self.json("tags/worldgen/biome", tag) if ns == "minecraft" and not self.own(
                    f"data/{ns}/tags/worldgen/biome/{path}.json") else None
                if game and not (mine and mine.get("replace")):
                    values += game.get("values", [])
                if mine:
                    values += mine.get("values", [])
                if mine is None and game is None:
                    others.add(item)
                f, o = self.biomes(values, seen)
                found, others = found | f, others | o
            elif namespaced(item).startswith("minecraft:") and self.resources and \
                    (self.resources / "data/minecraft/worldgen/biome" / f"{namespaced(item)[10:]}.json").is_file():
                found.add(namespaced(item))
            else:
                others.add(namespaced(item))
        return found, others

    def placed(self, structure):
        """The structure sets that place it (the jar's, and the game's for its own structures)."""
        sets = []
        names = [n for n in self.files if re.match(r"data/[^/]+/worldgen/structure_set/.+\.json$", n)]
        if self.resources and structure.startswith("minecraft:"):
            folder = self.resources / "data/minecraft/worldgen/structure_set"
            names += [f"data/minecraft/worldgen/structure_set/{p.stem}.json" for p in folder.glob("*.json")
                      if f"data/minecraft/worldgen/structure_set/{p.stem}.json" not in self.files]
        for n in names:
            ns, path = re.match(r"data/([^/]+)/worldgen/structure_set/(.+)\.json$", n).groups()
            s = self.json("worldgen/structure_set", f"{ns}:{path}") or {}
            listed = [e.get("structure") if isinstance(e, dict) else e for e in s.get("structures", [])]
            if structure in {namespaced(e) for e in listed if isinstance(e, str)}:
                sets.append(s)
        return sets

    def elements(self, pool_id, vanilla=True):
        """A template pool's elements that place something: for each, its pieces' blocks and the pools its jigsaws
        name."""
        pool = self.json("worldgen/template_pool", pool_id, vanilla) or {}
        found = []
        for entry in pool.get("elements", []):
            element = entry.get("element", {})
            if entry.get("weight", 1) <= 0 or element.get("element_type") == "minecraft:empty_pool_element":
                continue
            here, pools = collections.Counter(), set()
            for part in element.get("elements", []) if "elements" in element else [element]:
                location = part.get("location")
                location = location.get("id") if isinstance(location, dict) else location
                piece = self.piece(location, vanilla) if isinstance(location, str) else None
                if piece:
                    here.update(piece[0])
                    pools |= piece[1]
            found.append((here, pools))
        return found, pool.get("fallback")

    def pools_blocks(self, start, vanilla=True):
        """The blocks of every piece a template pool and the pools its jigsaws name lead to, and, for each piece the
        start pool may begin with, the blocks the jigsaws build from it on average (a start piece is often only a
        connector)."""
        blocks, todo, seen = collections.Counter(), [start], set()
        while todo:
            pool_id = namespaced(todo.pop())
            if pool_id in seen or len(seen) > 2000:
                continue
            seen.add(pool_id)
            elements, fallback = self.elements(pool_id, vanilla)
            todo += [fallback] if fallback else []
            for here, pools in elements:
                blocks.update(here)
                todo.extend(pools)
        typical = {}

        def typical_from(pool_id, depth):
            # What a pool adds, as its elements do on average with what their jigsaws add (a pool with nothing to
            # place, or one deeper than eight or met again, adds nothing): a jigsaw picks one of them.
            if depth > 8 or pool_id in typical:
                return typical.get(pool_id) or collections.Counter()
            typical[pool_id] = None
            options = [built(here, pools, depth) for here, pools in self.elements(pool_id, vanilla)[0]]
            total = sum(options, collections.Counter())
            typical[pool_id] = collections.Counter({b: n / len(options) for b, n in total.items()}) if options \
                else collections.Counter()
            return typical[pool_id]

        def built(here, pools, depth):
            total = collections.Counter(here)
            for p in pools:
                total.update(typical_from(namespaced(p), depth + 1))
            return total

        first = {i: built(here, pools, 0) for i, (here, pools) in enumerate(self.elements(namespaced(start),
                                                                                           vanilla)[0])}
        return blocks, first


# The blocks of the game's structures that aren't built of pieces (their code places them), as their types; and the
# folders of the pieces of those that are, under the game's `worldgen/structure/minecraft/`.
BUILT_BY_TYPE = {
    "minecraft:swamp_hut": {"spruce_planks", "spruce_stairs", "oak_fence", "potted_red_mushroom", "cauldron",
                            "crafting_table"},
    "minecraft:desert_pyramid": {"cut_sandstone", "chiseled_sandstone", "orange_terracotta", "blue_terracotta", "tnt",
                                 "stone_pressure_plate", "suspicious_sand"},
    "minecraft:jungle_temple": {"mossy_cobblestone", "cobblestone", "chiseled_stone_bricks", "sticky_piston",
                                "tripwire_hook", "dispenser", "lever", "repeater", "redstone_wire"},
    "minecraft:mineshaft": {"rail", "oak_planks", "oak_fence", "dark_oak_planks", "dark_oak_fence", "cobweb"},
    "minecraft:stronghold": {"stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "infested_stone_bricks",
                             "iron_bars", "end_portal_frame", "stone_brick_stairs", "bookshelf", "iron_door"},
    "minecraft:fortress": {"nether_bricks", "nether_brick_fence", "nether_brick_stairs", "nether_wart"},
    "minecraft:ocean_monument": {"prismarine", "prismarine_bricks", "dark_prismarine", "sea_lantern", "wet_sponge"},
    "minecraft:buried_treasure": {"chest"},
}
PIECES_BY_TYPE = {"minecraft:ocean_ruin": "underwater_ruin", "minecraft:shipwreck": "shipwreck",
                  "minecraft:igloo": "igloo", "minecraft:ruined_portal": "ruined_portal",
                  "minecraft:nether_fossil": "nether_fossils", "minecraft:end_city": "end_city",
                  "minecraft:woodland_mansion": "woodland_mansion"}
# A structure is told from the land by the blocks of its pieces no world has anyway; it needs at least this many of
# them, or the try can't tell it (a marker piece whose boss or tree a function makes, a piece of one block).
FEWEST_OWN = 4


def own_blocks(blocks):
    """The blocks of a structure's pieces no world has anyway. Logs and leaves grow in any world; wood (bark on every
    side) only where something built it."""
    return {b for b in blocks if b not in NATURAL and not b.endswith(("_log", "_leaves"))}


def structure_blocks(data, structure, definition, vanilla=True):
    """The blocks of a structure's pieces (by name, counted), and those of each piece it may begin with."""
    kind = namespaced(definition.get("type", ""))
    if definition.get("start_pool"):
        return data.pools_blocks(definition["start_pool"], vanilla)
    if kind in BUILT_BY_TYPE:
        blocks = collections.Counter(dict.fromkeys(BUILT_BY_TYPE[kind], 1000))
        return blocks, {kind: blocks}
    folder = data.resources / "worldgen" / "structure" / "minecraft" / PIECES_BY_TYPE.get(kind, "-") \
        if data.resources else None
    blocks, first = collections.Counter(), {}
    for p in sorted(folder.rglob("*.nbt")) if folder and folder.is_dir() else []:
        counted = piece_blocks(p.read_bytes())[0]
        blocks.update(counted)
        first[p.stem] = counted
    return blocks, first


def structure_plan(jar, resources=None):
    """What the try checks of each structure a mod's jar defines, from its data and the game's (`resources`):
    {"id", "check", "why", "blocks", "least"}. `check` is "count" (located, with at least `least` of `blocks`, the
    blocks of its own pieces no world has anyway; for a structure of the game's the mod reworks, only those the game's
    version lacks, #83), "off" (the mod turns it off: it must not be located), "skip" (it can't generate in a world
    of the game's alone, as without Kiln: no structure set places it, or only other mods' biomes have it), or "fail"
    and "cannot" (why it can't be built, or can't be told from the land)."""
    data = StructureData(jar, resources)
    plan = []
    own_biomes = {re.sub(r"^data/([^/]+)/worldgen/biome/(.+)\.json$", r"\1:\2", n) for n in data.files
                  if re.match(r"^data/[^/]+/worldgen/biome/.+\.json$", n)}
    least_wanted = TRY.get("pieces", 100)
    for name in sorted(data.files):
        m = re.match(r"^data/([^/]+)/worldgen/structure/(.+)\.json$", name)
        if not m:
            continue
        structure = f"{m[1]}:{m[2]}"
        entry = {"id": structure, "check": "count", "why": None, "blocks": set(), "least": least_wanted}
        plan.append(entry)
        definition = data.json("worldgen/structure", structure) or {}
        kind = namespaced(definition.get("type", ""))
        sets = data.placed(structure)
        placements = {namespaced(s.get("placement", {}).get("type", "")) for s in sets}
        biomes, others = data.biomes(definition.get("biomes", []))
        library = [k for k in [kind, *placements] if not k.startswith("minecraft:")]
        if not definition:
            entry.update(check="fail", why="its file isn't JSON the game reads")
        elif library:
            entry.update(check="fail", why=f"its {'type' if kind in library else 'placement'} {library[0]} is a "
                         "library's (its code), which Kiln doesn't bring")
        elif not sets:
            entry.update(check="skip", why="no structure set places it (another structure, a command or code does)")
        elif not biomes and not others:
            entry.update(check="off", why="the mod turns it off (no biome has it)")
        elif not biomes and others & own_biomes:
            entry.update(check="fail", why=f"only in the mod's own biomes ({', '.join(sorted(others & own_biomes)[:3])}"
                         "), and Kiln doesn't bring biomes (D-050)")
        elif not biomes:
            entry.update(check="skip", why=f"only in other mods' biomes ({', '.join(sorted(others)[:3])})")
        if entry["check"] != "count":
            continue
        blocks, first = structure_blocks(data, structure, definition)
        mine = own_blocks(blocks)
        if structure.startswith("minecraft:") and data.resources and \
                (data.resources / f"data/minecraft/worldgen/structure/{m[2]}.json").is_file():
            # A structure of the game's the mod reworks (#83): told by the blocks only the mod's pieces have.
            original = json.loads((data.resources / f"data/minecraft/worldgen/structure/{m[2]}.json").read_text())
            plain = StructureData.__new__(StructureData)
            plain.zip, plain.resources, plain.files, plain.pieces = data.zip, data.resources, {}, {}
            mine -= own_blocks(structure_blocks(plain, structure, original)[0])
            if not mine:
                entry.update(check="cannot", why="its pieces have no block the game's own version lacks: the try "
                             "can't tell the mod's version from the game's")
                continue
        # The smallest it is built, on average, from any piece it may begin with: a quarter of that must stand there
        # (the land cuts a structure into a cliff, a processor rots some of it), and at most `pieces`.
        firsts = [round(sum(n for b, n in counted.items() if b in mine)) for counted in first.values()]
        smallest = min(firsts) if firsts else 0
        if smallest < FEWEST_OWN:
            entry.update(check="cannot", why=f"its pieces have {smallest} block{'s' * (smallest != 1)} the land "
                         "hasn't anyway (the rest is a function's, a feature's or entities): the try can't tell it")
            continue
        entry.update(blocks=mine, least=min(least_wanted, max(FEWEST_OWN, smallest // 4)))
    return plan


def structures(jar, resources=None):
    """The structures of a mod's jar the try locates and counts, and the blocks of all their pieces (for older
    tooling: structure_plan has each one's own)."""
    plan = [p for p in structure_plan(jar, resources) if p["check"] == "count"]
    return [p["id"] for p in plan], set().union(*(p["blocks"] for p in plan)) if plan else set()


# Where a structure may be: the Overworld first, then the Nether and the End (a mod's structures say which by their
# biomes; /locate finds them in the dimension it runs in).
DIMENSIONS = ("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end")
# Heights searched, in 81-block layers (horizon-ctl takes at most 32768 blocks a request): from below the deepslate's
# top (buried halls) to the build limit (Sky Temple's temple starts 200 blocks over the sea, #80; a layer past the
# dimension's top counts nothing there).
LAYERS = (-59, 22, 103, 184, 265)


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


def counted_there(g, dimension, x, z, blocks, least=None):
    """Flies to the structure at x, z and counts its pieces' blocks (`pieces_counted`). The chunks around it can come
    seconds after the game is ready there (more on a busy computer): counted again while there are too few and the
    count still grows."""
    g.run("command", f"execute in {dimension} run tp @s {x} 150 {z}")
    g.run("wait-ready", "90")
    time.sleep(3)
    found, census, by_layer = pieces_counted(g, x, z, blocks)
    least = TRY.get("pieces", 100) if least is None else least
    for _ in range(COUNT_AGAIN):
        if found >= least:
            break
        time.sleep(5)
        again = pieces_counted(g, x, z, blocks)
        if again[0] <= found and found > 0:
            break
        found, census, by_layer = max((found, census, by_layer), again, key=lambda c: c[0])
    # The control (D-120): the same blocks CONTROL blocks away, in two directions, the fewer of each; only what stands
    # at the structure beyond that is its (The Ice Warrior's tower stands in ice spikes, its packed and blue ice the
    # biome's too).
    controls = []
    for cx, cz in ((x + CONTROL, z), (x, z + CONTROL)):
        g.run("command", f"execute in {dimension} run tp @s {cx} 150 {cz}")
        g.run("wait-ready", "90")
        time.sleep(3)
        controls.append(pieces_counted(g, cx, cz, blocks)[1])
    control = {b: min(c.get(b, 0) for c in controls) for b in blocks}
    found = sum(max(0, census.get(b, 0) - control[b]) for b in blocks)
    return found, census, by_layer


# How far from a structure its control is counted: past the 96 blocks counted around it, and seldom where the next
# one of a mod's structure set stands (two directions, the fewer counting).
CONTROL = 192


# How far from a structure found with too few of its pieces' blocks the next one is looked for, in four directions:
# the world cuts one into a cliff or a lava sea now and then, and the mod is judged on its structure, not on that one.
ELSEWHERE = 1500


def structure_found(g, structure, blocks, places=None, least=None):
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
    least = TRY.get("pieces", 100) if least is None else least
    found, census, by_layer = counted_there(g, dimension, x, z, blocks, least)
    for dx, dz in ((ELSEWHERE, 0), (-ELSEWHERE, 0), (0, ELSEWHERE), (0, -ELSEWHERE)):
        if found >= least:
            break
        other = located(g, structure, (dimension, at[1] + dx, at[2] + dz))
        if other and other[1:] != at[1:] and other[1:] != (x, z):
            again = counted_there(g, *other, blocks, least)
            if again[0] > found:
                (dimension, x, z), (found, census, by_layer) = other, again
    if found:
        places.append((dimension, x, z))
    g.run("command", f"execute in {dimension} run tp @s {x} 150 {z}")
    g.run("wait-ready", "90")
    # What it holds: the containers and spawners among its blocks, and the mobs it came with (a structure's pieces
    # carry them), around it.
    kept = {name: census.get(name, 0) for name in ("chest", "trapped_chest", "barrel", "spawner")}
    # Back from the control (D-142), the server sends the mobs around it again a while after the game is ready: read
    # until some came (15 s at most, a structure may have none), then once more a little later, the more counting.
    def mobs_around():
        seen = {}
        for mob in g.run("entities", "128").get("entities", []):
            seen[mob.get("type")] = seen.get(mob.get("type"), 0) + 1
        return seen
    mobs = until(mobs_around, lambda seen: bool(seen), limit=15.0, every=1.0)
    if mobs:
        time.sleep(3)
        mobs = max(mobs, mobs_around(), key=lambda seen: sum(seen.values()))
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

    def __exit__(self, *exc):
        # The game's log and Java's beside the screenshots: what a run measured (D-112's calls into Java) stays.
        try:
            (self.out / "game-log.json").write_text(json.dumps(super().run("logs", "--lines", "5000")))
            if (self.home / "kiln" / "java.log").is_file():
                shutil.copy(self.home / "kiln" / "java.log", self.out / "java.log")
        except (subprocess.CalledProcessError, OSError) as e:
            print(f"the game's log wasn't kept: {e}", file=sys.stderr)
        return super().__exit__(*exc)

    def run(self, *args):
        if args[:1] == ("launch",):
            # The launcher writes native-mods.json beside a game it built with native mods; Horizon gives such a game
            # the mods' image slot in its GUI atlas (horizon/modimage), where Kiln puts the Java mods' own GUI
            # pictures (D-160). native.py's build writes none: without it every mod's picture draws as a box.
            built = self.ctl.with_name("native-mods.json")
            if not built.is_file():
                built.write_text(json.dumps({"mods": [{"id": "kiln", "version": "proof"}]}))
        if args[:1] == ("launch",) and not (self.home / "options.txt").exists():
            # No tutorial: its toast ("Move with W, A, S and D") would cover the top right of every screenshot
            # (JEI's page arrows). The rest of the options are the game's defaults (D-170).
            (self.home / "options.txt").write_text(f"version:{OPTIONS_VERSION}\ntutorialStep:none\n")
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
            # The keys the try binds on the mods' pages in Options > Mods first, as the player would there: the
            # game's key.<row>.<key's name> lines (a Java mod's row is kiln_<mod id>).
            for row, keys in TRY.get("keys", {}).items():
                on += [f"key.{row}.{name}:{key}\n" for name, key in keys.items()]
            if ONLY == "overlay":
                # The overlay's key bound as Kiln's page in Options > Mods binds it: horizon-ctl presses no F7.
                on.append(f"key.kiln.key.kiln.overlay:key.keyboard.{OVERLAY_KEY}\n")
            if on:
                (self.home / "horizon-mods.txt").write_text("".join(on))
            # The game's options the try sets before the game starts, as options.txt holds them
            # ({"tutorialStep": "5"}: no tutorial toast over the top right, where a recipe viewer's page arrows are).
            if TRY.get("options"):
                with open(self.home / "options.txt", "a", encoding="utf-8") as f:
                    f.write("".join(f"{k}:{v}\n" for k, v in TRY["options"].items()))
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


def mixin_bridge(g):
    """What the mixins proof mod said from the game's methods Kiln's Mixin bridge calls at Horizon's hooks (D-102)."""
    text = json.dumps(g.run("messages", "300"), ensure_ascii=False)
    return sorted(set(re.findall(r"Kiln's Mixin bridge: [^\"\\]*", text)))


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


def chained(g, x, y, z):
    """The grinder, the electric furnace and the solar panel KILN_PROOF_CHEST names, with the game's own containers
    between them (D-144): at noon, in clear weather, the grinder at x y z, a hopper east of it facing east, a chest east
    of that, the furnace east of the chest, a panel south of each machine, nothing above. By command, as a player would
    on their screens: the grinder's output (slot 1) goes out east by itself, the furnace takes its input (slot 0) from
    the west by itself; two iron ore into the grinder. RebornCore pushes the raw iron into the hopper by Fabric's
    ItemStorage.SIDED, Horizon's hopper moves it into the chest, RebornCore takes it from the chest into the furnace:
    the furnace must smelt all four into iron ingots (or 180 s pass)."""
    grinder, furnace, panel = CHEST
    step(g, "time set 6000", "weather clear")
    g.run("command", f"fill {x} {y} {z} {x + 3} {y + 30} {z + 1} air")
    time.sleep(0.5)
    g.run("command", f"setblock {x} {y} {z} {grinder}[facing=west]")
    g.run("command", f"setblock {x + 1} {y} {z} minecraft:hopper[facing=east]")
    g.run("command", f"setblock {x + 2} {y} {z} minecraft:chest[facing=north]")
    g.run("command", f"setblock {x + 3} {y} {z} {furnace}[facing=west]")
    g.run("command", f"setblock {x} {y} {z + 1} {panel}")
    g.run("command", f"setblock {x + 3} {y} {z + 1} {panel}")
    time.sleep(2)
    g.run("command", f"data merge block {x} {y} {z} {{slotConfig:{SLOT_OUT_EAST}}}")
    g.run("command", f"data merge block {x + 3} {y} {z} {{slotConfig:{SLOT_TAKE_WEST}}}")
    time.sleep(0.5)
    g.run("command", f'data merge block {x} {y} {z} {{Items:[{{Slot:0b,id:"minecraft:iron_ore",count:2}}]}}')
    px, _, pz = g.run("state")["player"]["block_pos"]
    yaw = -math.degrees(math.atan2(x + 1.5 - px, z + 0.5 - pz))
    g.run("look", f"{yaw:.1f}", "15")
    found = {"hopper": block(g, x + 1, y, z), "chest": block(g, x + 2, y, z), "chest_held": []}
    deadline = time.time() + 180
    while True:
        found["furnace"] = data(g, x + 3, y, z) or ""
        held = data(g, x + 2, y, z) or ""
        if "raw_iron" in held and not found["chest_held"]:
            found["chest_held"] = held
        if smelted_into(found["furnace"]) == 4 or time.time() > deadline:
            break
        time.sleep(3)
    g.shot("17-chest")
    found["grinder"] = data(g, x, y, z) or ""
    found["ingots"] = smelted_into(found["furnace"])
    found["left"] = [what for what, at in (("grinder", x), ("hopper", x + 1), ("chest", x + 2))
                     if "raw_iron" in (data(g, at, y, z) or "")]
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
    there, goes back to the title screen and into the world again.

    With Java in the game's process (D-112) Java can't start again: the switch is soft (D-113), so the mod stops being
    called at once (Show FPS's counter is no longer drawn), the title screen says the rest applies from the next game
    start, and the next world has the mod still off, with no Java started again. With Java in a process of its own,
    Kiln starts Java again with the next world, without the mod."""
    def log():
        return json.dumps(g.run("logs", "--lines", "4000"))

    def running(text):
        counts = re.findall(r"Kiln: (\d+) of \d+ Java mods running", text)
        return int(counts[-1]) if counts else None

    def counter():
        # How much Show FPS draws top left (fps_on_hud), when `name` is Show FPS; None for other mods.
        return fps_on_hud(g)["counter_drawn"] if name == "ShowFPS" else None

    before = log()
    found = {"running_before": running(before), "started_before": f"Kiln: started {name}" in before,
             "in_process": "Kiln: Java in the game's process" in before}
    if found["in_process"]:
        found["counter_before"] = counter()
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
    if found["in_process"]:
        found["soft_off_said"] = bool(re.search(r"Kiln: turned \S+ off now", log()))
        found["counter_off"] = counter()
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
        if found["in_process"] and "applies from the next game start" in after:
            break
        if after.count("Java mods running") > before.count("Java mods running"):
            break
    found["restarted"] = "Java starts again with the next world" in after
    later = after.split("Java starts again with the next world", 1)[-1] if found["restarted"] else ""
    found["running_after"] = running(later)
    found["started_after"] = f"Kiln: started {name}" in later
    if found["in_process"]:
        found["applies_next_start"] = "with Java in the game's process, that applies from the next game start" in after
        found["java_started_again"] = after.count("Java mods running") > before.count("Java mods running")
        time.sleep(2)
        found["counter_next_world"] = counter()
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


def mod_screen_size(g):
    """The GUI size the mod's own screen just opened at, as Kiln's log says (D-094); None when none opened."""
    found = re.findall(r"a mod's screen opens [a-z' ]+, (\d+)x(\d+) GUI pixels", json.dumps(g.run("logs", "--lines", "400")))
    return (int(found[-1][0]), int(found[-1][1])) if found else None


def fps_settings(g):
    """Show FPS's settings screen (D-094): N opens it over the world (the game's screen is a mod's), a click on Show
    Background turns the counter's box off, Done saves it to showfps.json and goes back to the game; N and Done again
    put it back."""
    found = {}
    g.run("look", "0", "-90")
    g.run("key", "n", "tap")
    time.sleep(2)
    found["screen"] = g.run("screen").get("screen")
    size = mod_screen_size(g)
    found["size"] = size
    if found["screen"] != "mod" or not size:
        return found
    before = g.shot("13-fps-settings")
    middle = size[0] // 2
    g.run("click-at", str(middle), "85")
    time.sleep(1.5)
    after = g.shot("13-fps-settings-clicked")
    found["button_changed"] = round(difference(before, after, (0.3, 0.3, 0.7, 0.4)), 1)
    g.run("click-at", str(middle), "145")
    time.sleep(1.5)
    found["after_done"] = g.run("screen").get("screen")
    config = g.home / "kiln" / "config" / "showfps.json"
    found["saved"] = json.loads(config.read_text()) if config.is_file() else None
    time.sleep(1)
    found["no_box"] = round(difference(g.shot("13-fps-no-box"), before, COUNTER), 1)
    g.run("key", "n", "tap")
    time.sleep(2)
    g.run("click-at", str(middle), "85")
    time.sleep(1)
    g.run("click-at", str(middle), "145")
    time.sleep(1.5)
    found["saved_again"] = json.loads(config.read_text()) if config.is_file() else None
    return found


def armour_settings(g):
    """Armour Durability HUD's settings screen (D-094): O opens it, a click on Helmet unticks it, Save saves it to
    armour_durability.json and the HUD leaves the helmet out; O, Helmet and Save again put it back."""
    found = {}
    g.run("key", "o", "tap")
    time.sleep(2)
    found["screen"] = g.run("screen").get("screen")
    size = mod_screen_size(g)
    found["size"] = size
    if found["screen"] != "mod" or not size:
        return found
    g.shot("14-armour-settings")
    # Its layout, from its screen's code: the helmet's box at the middle + 10, 78 down; Save at the middle - 150, 42 up
    # from the bottom.
    middle, save = size[0] // 2, size[1] - 32
    g.run("click-at", str(middle + 18), "86")
    time.sleep(1)
    g.shot("14-armour-settings-clicked")
    g.run("click-at", str(middle - 110), str(save))
    time.sleep(1.5)
    found["after_save"] = g.run("screen").get("screen")
    config = g.home / "kiln" / "config" / "armour_durability.json"
    found["saved"] = json.loads(config.read_text()) if config.is_file() else None
    g.shot("14-armour-no-helmet")
    g.run("key", "o", "tap")
    time.sleep(2)
    g.run("click-at", str(middle + 18), "86")
    time.sleep(1)
    g.run("click-at", str(middle - 110), str(save))
    time.sleep(1.5)
    found["saved_again"] = json.loads(config.read_text()) if config.is_file() else None
    return found


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


def mod_recipe(recipe_id):
    """The recipe `recipe_id` (namespace:path) as a tried mod's jar (KILN_PROOF_MODS) has it, with its jar's item tags
    (for ingredients given by tag); None when no jar has it."""
    namespace, path = recipe_id.split(":", 1)
    for jar in EXTRA:
        with zipfile.ZipFile(jar) as z:
            names = set(z.namelist())
            for folder in ("recipe", "recipes"):
                name = f"data/{namespace}/{folder}/{path}.json"
                if name in names:
                    tags = {}
                    for n in names:
                        m = re.fullmatch(r"data/([^/]+)/tags/items?/(.+)\.json", n)
                        if m:
                            tags[f"{m[1]}:{m[2]}"] = json.loads(z.read(n)).get("values", [])
                    return json.loads(z.read(name)), tags
    return None


def ingredient_item(ingredient, tags, chosen):
    """One item that fits a recipe's ingredient: an id, a list of them, `#tag` (from `chosen`, the step's own choice
    for that tag, else the jar's tag), or the older {"item"} / {"tag"} objects. None when none can be named."""
    if isinstance(ingredient, list):
        ingredient = ingredient[0] if ingredient else None
    if isinstance(ingredient, dict):
        ingredient = ingredient.get("item") or (f"#{ingredient['tag']}" if "tag" in ingredient else None)
    if not isinstance(ingredient, str):
        return None
    if ingredient.startswith("#"):
        tag = ingredient[1:]
        if ingredient in chosen:
            return chosen[ingredient]
        values = [v if isinstance(v, str) else v.get("id") for v in tags.get(tag, [])]
        return ingredient_item(values[0], tags, chosen) if values else None
    return ingredient


def recipe_grid(recipe, tags, chosen):
    """Where a crafting recipe's ingredients go: {grid slot: item}, the grid's width (2 for the inventory's, 3 for a
    crafting table's), and what it makes (id, count); or a reason it can't be laid out."""
    kind = recipe.get("type", "").removeprefix("minecraft:")
    result = recipe.get("result") or {}
    made = (result if isinstance(result, str) else result.get("id") or result.get("item"),
            1 if isinstance(result, str) else result.get("count", 1))
    # A result the recipe names (Flashbangs' snowball "Flashbang"): it must have that name too.
    name = ((result.get("components") or {}) if isinstance(result, dict) else {}).get("minecraft:custom_name")
    made = made + (name,) if isinstance(name, str) else made
    if kind in STATIONS:
        return recipe_station(kind, recipe, tags, chosen, made)
    if kind == "crafting_shaped":
        # Without its empty rows and columns, as the game matches it: a 1x2 pattern written in three rows fits the
        # inventory's grid.
        pattern = [r for r in recipe.get("pattern") or [] if r.strip()]
        cols = [c for c in range(max((len(r) for r in pattern), default=0))
                if any(c < len(r) and r[c] != " " for r in pattern)]
        pattern = ["".join(r[c] if c < len(r) else " " for c in range(cols[0], cols[-1] + 1)) for r in pattern] \
            if cols else []
        width = 2 if len(pattern) <= 2 and max((len(r) for r in pattern), default=0) <= 2 else 3
        cells = {}
        for r, row in enumerate(pattern):
            for c, key in enumerate(row):
                if key != " ":
                    cells[100 + r * width + c] = ingredient_item((recipe.get("key") or {}).get(key), tags, chosen)
    elif kind == "crafting_shapeless":
        ingredients = recipe.get("ingredients") or []
        width = 2 if len(ingredients) <= 4 else 3
        cells = {100 + i: ingredient_item(one, tags, chosen) for i, one in enumerate(ingredients)}
    else:
        return None, None, made, f"a {kind or 'typeless'} recipe, not a crafting grid's"
    unnamed = sorted(s for s, item in cells.items() if not item)
    if unnamed:
        return None, None, made, f"no item for the ingredients in grid slots {unnamed} (a tag the step must choose for)"
    return cells, width, made, None


# The stations a recipe of a kind is made at (D-196): the block, and the screen's slots. A smithing table's and a
# stonecutter's inputs are the station's (600 on), their result 650; a furnace's are its container's (300 the input,
# 301 the fuel, 302 the result).
STATIONS = {
    "smithing_transform": "minecraft:smithing_table",
    "smithing_trim": "minecraft:smithing_table",
    "stonecutting": "minecraft:stonecutter",
    "smelting": "minecraft:furnace",
    "blasting": "minecraft:blast_furnace",
    "smoking": "minecraft:smoker",
}


def recipe_station(kind, recipe, tags, chosen, made):
    """Where a station recipe's ingredients go, as recipe_grid answers for a grid: {slot: item}, the station's block
    (in place of the grid's width), and what it makes; or a reason it can't be laid out."""
    block = STATIONS[kind]
    if kind.startswith("smithing"):
        # A smithing recipe without a template (26.3's is optional) leaves the template slot empty.
        parts = {600: "template", 601: "base", 602: "addition"}
        cells = {slot: ingredient_item(recipe.get(part), tags, chosen) for slot, part in parts.items()
                 if recipe.get(part) is not None}
    elif kind == "stonecutting":
        cells = {600: ingredient_item(recipe.get("ingredient"), tags, chosen)}
    else:
        cells = {300: ingredient_item(recipe.get("ingredient"), tags, chosen), 301: "minecraft:coal"}
    unnamed = sorted(s for s, item in cells.items() if not item)
    if unnamed:
        return None, None, made, f"no item for the ingredients in slots {unnamed} (a tag the step must choose for)"
    return cells, block, made, None


def result_components(recipe):
    """The components a recipe's result carries besides its name, as {component: value}: the item made must carry
    them too (a painting cut to a variant)."""
    result = recipe.get("result")
    components = (result.get("components") or {}) if isinstance(result, dict) else {}
    return {k: v for k, v in components.items() if k != "minecraft:custom_name"}


def crafted(g, recipe_id, chosen=None):
    """A player in survival crafts `recipe_id`, a tried mod's crafting recipe, by hand: its ingredients given by
    command, then put one by one into the crafting grid (the inventory's 2x2, or a 3x3 crafting table set beside the
    player and opened with a click), what the result slot (200) then shows, and what a shift-click on it gives the
    player. The recipe book and /recipe aren't used: Horizon's index of them misses content mods' recipes on
    the Nightly Kiln is built on (#100), and the player's crafting doesn't."""
    found = {"recipe": recipe_id}
    chosen = dict(chosen or {})
    grid = chosen.pop("grid", None)
    if grid:
        # A recipe of the mod's own type, matched by its Java code (Sherd Duplication's copy_sherd, D-212): no JSON
        # says its ingredients, so the step names them, row by row ("" an empty slot), and what it makes.
        width = 3 if len(grid) > 4 else 2
        cells = {100 + i: item for i, item in enumerate(grid) if item}
        made, error = tuple(chosen.pop("makes")), None
    else:
        read = mod_recipe(recipe_id)
        if read is None:
            return {**found, "error": "no tried mod's jar has this recipe"}
        cells, width, made, error = recipe_grid(*read, chosen)
    made, name = made[:2], (made[2] if len(made) > 2 else None)
    found["makes"] = list(made)
    if error:
        return {**found, "error": error}
    found["grid"] = {str(s): item for s, item in sorted(cells.items())}
    if isinstance(width, str):
        return {**found, **at_station(g, recipe_id, read[0], cells, width, made)}
    try:
        step(g, "gamemode survival", "clear @s", wait=0.5)
        for item, n in collections.Counter(cells.values()).items():
            g.run("command", f"give @s {item} {n}")
        time.sleep(1)
        if width == 2:
            g.run("open-inventory")
        else:
            # A crafting table two blocks east of the player, on a stone floor, the player facing it.
            x, y, z = g.run("state")["player"]["block_pos"]
            g.run("command", f"fill {x - 1} {y - 1} {z - 1} {x + 3} {y - 1} {z + 1} minecraft:stone")
            g.run("command", f"fill {x - 1} {y} {z - 1} {x + 3} {y + 2} {z + 1} air")
            g.run("command", f"setblock {x + 2} {y} {z} minecraft:crafting_table")
            step(g, f"tp @s {x + 0.5} {y} {z + 0.5} facing {x + 2.5} {y + 0.5} {z + 0.5}", wait=1)
            block_until(g, x + 2, y, z, named("minecraft:crafting_table"), 10)
            g.run("interact", str(x + 2), str(y), str(z), "west")
        opened = until(lambda: g.run("screen"), lambda s: s.get("craft_size") == width, 10)
        found["opened"] = [opened.get("screen"), opened.get("craft_size")]
        if opened.get("craft_size") != width:
            return {**found, "error": f"no {width}x{width} crafting grid opened"}
        for item in dict.fromkeys(cells.values()):
            held = None
            # An item that doesn't stack (leggings in a horse armour's recipe) is given as one per slot: a stack is
            # picked up again whenever the cursor is empty.
            for slot in (s for s, i in cells.items() if i == item):
                if held is None or not g.run("screen").get("carried"):
                    slots = g.run("screen").get("slots") or []
                    held = next((s["slot"] for s in slots if s.get("item") == item and s["slot"] < 100), None)
                    if held is None:
                        return {**found, "error": f"{item} given, but not in the screen's slots", "slots": slots}
                    g.run("click-slot", str(held))
                    time.sleep(0.2)
                g.run("click-slot", str(slot), "right")
                time.sleep(0.2)
            if g.run("screen").get("carried"):
                g.run("click-slot", str(held))
                time.sleep(0.2)
        # The result slot once the game has matched the grid (or after 10 s).
        screen = until(lambda: g.run("screen"),
                       lambda s: any(o.get("slot") == 200 and o.get("item") for o in s.get("slots") or []), 10)
        found["grid_placed"] = {str(s["slot"]): s.get("item") for s in screen.get("slots") or []
                                if 100 <= s["slot"] < 200}
        result = next((s for s in screen.get("slots") or [] if s.get("slot") == 200), None)
        found["result_slot"] = [result.get("item"), result.get("count")] if result and result.get("item") else None
        before = count(g, made[0])
        g.run("click-slot", "200", "shift")
        time.sleep(1)
        # The server's answer: what the player holds after the shift-click (the result slot is the window's own
        # match).
        found["held"] = until(lambda: count(g, made[0]), lambda n: n >= before + made[1], 5) - before
        if name:
            held = g.run("inventory")
            found["named"] = any(s.get("item") == made[0] and name in (s.get("components") or "")
                                 for s in (held.get("hotbar") or []) + (held.get("main") or []))
    finally:
        # Whatever happened: the screen closed, the player as the try found them.
        try:
            g.run("close")
        except subprocess.CalledProcessError:
            pass
        step(g, "clear @s", "gamemode creative", wait=0.5)
    return found


def at_station(g, recipe_id, recipe, cells, block, made):
    """A player in survival makes a station recipe (D-196): the station set two blocks east and opened with a click,
    each ingredient clicked into its slot (a smithing table's template, base and addition, a stonecutter's input, a
    furnace's input and coal as its fuel), on a stonecutter the recipe's button clicked, then what the result slot
    shows (a furnace's after it has cooked) and what a shift-click on it gives the player."""
    found = {}
    furnace = 302 if 301 in cells else None
    result_slot = furnace or 650
    wants = result_components(recipe)
    try:
        step(g, "gamemode survival", "clear @s", wait=0.5)
        for item, n in collections.Counter(cells.values()).items():
            g.run("command", f"give @s {item} {n}")
        time.sleep(1)
        x, y, z = g.run("state")["player"]["block_pos"]
        g.run("command", f"fill {x - 1} {y - 1} {z - 1} {x + 3} {y - 1} {z + 1} minecraft:stone")
        g.run("command", f"fill {x - 1} {y} {z - 1} {x + 3} {y + 2} {z + 1} air")
        g.run("command", f"setblock {x + 2} {y} {z} {block}")
        step(g, f"tp @s {x + 0.5} {y} {z + 0.5} facing {x + 2.5} {y + 0.5} {z + 0.5}", wait=1)
        block_until(g, x + 2, y, z, named(block), 10)
        g.run("interact", str(x + 2), str(y), str(z), "west")
        opened = until(lambda: g.run("screen"),
                       lambda s: any(o.get("slot") == result_slot for o in s.get("slots") or []), 10)
        found["opened"] = [opened.get("screen"), opened.get("station"), opened.get("container")]
        if not any(o.get("slot") == result_slot for o in opened.get("slots") or []):
            return {**found, "error": f"no {block} screen opened"}
        slots = opened.get("slots") or []
        for slot, item in sorted(cells.items()):
            held = next((s["slot"] for s in slots if s.get("item") == item and s["slot"] < 100), None)
            if held is None:
                return {**found, "error": f"{item} given, but not in the screen's slots", "slots": slots}
            g.run("click-slot", str(held))
            time.sleep(0.2)
            g.run("click-slot", str(slot), "right")
            time.sleep(0.2)
            if g.run("screen").get("carried"):
                g.run("click-slot", str(held))
                time.sleep(0.2)
            slots = g.run("screen").get("slots") or []
        if block == "minecraft:stonecutter":
            # The recipe's button: StonecutterScreen's 4 a row, 16x18 from (52, 14), the first 12 shown.
            menu = until(lambda: g.run("menu"), lambda m: recipe_id in ((m.get("info") or {}).get("recipes") or []), 5)
            listed = (menu.get("info") or {}).get("recipes") or []
            found["listed"] = len(listed)
            if recipe_id not in listed:
                return {**found, "error": "the stonecutter doesn't list the recipe", "recipes": listed[:20]}
            i = listed.index(recipe_id)
            if i >= 12:
                return {**found, "error": f"the recipe is button {i + 1}, under the first 12 the step clicks"}
            g.run("click-gui", str(52 + (i % 4) * 16 + 8), str(14 + (i // 4) * 18 + 9))
            time.sleep(0.5)
        cook = (recipe.get("cookingtime") or 200) / 20 + 5 if furnace else 10
        screen = until(lambda: g.run("screen"),
                       lambda s: any(o.get("slot") == result_slot and o.get("item") for o in s.get("slots") or []),
                       cook)
        found["inputs_placed"] = {str(s["slot"]): s.get("item") for s in screen.get("slots") or []
                                  if s["slot"] in cells}
        result = next((s for s in screen.get("slots") or [] if s.get("slot") == result_slot), None)
        found["result_slot"] = [result.get("item"), result.get("count")] if result and result.get("item") else None
        before = count(g, made[0])
        g.run("click-slot", str(result_slot), "shift")
        time.sleep(1)
        found["held"] = until(lambda: count(g, made[0]), lambda n: n >= before + made[1], 5) - before
        if wants:
            # Each component value the recipe gives its result, in what the player now holds.
            held = g.run("inventory")
            stacks = [json.dumps(s.get("components") or "") for s in (held.get("hotbar") or []) +
                      (held.get("main") or []) if s.get("item") == made[0]]
            found["components"] = {k: any(json.dumps(v).strip('"') in st for st in stacks) for k, v in wants.items()}
            found["named"] = all(found["components"].values())
    finally:
        try:
            g.run("close")
        except subprocess.CalledProcessError:
            pass
        step(g, "clear @s", "gamemode creative", wait=0.5)
    return found


class XKeyboard:
    """A keyboard on the X server the game's window is on (the try's Xvfb), through XTest (D-178): the keys reach the
    game as a real keyboard's do, through its window (winit's key and text events, the modifiers' state, the server's
    own auto-repeat), not through horizon-ctl's socket. The game's window is shown and given the keyboard's focus first:
    an offscreen game's window is never mapped, and an unmapped window gets no keys. An offscreen game's window unmaps
    itself as soon as it is shown (winit does on its VisibilityNotify), so the keys go while this client holds the X
    server (XGrabServer): the window stays mapped and focused, the keys queue up for the game in their order with their
    repeats, and the game reads them all once the server is let go (their timing is lost, not their order)."""

    NAMES = {"ctrl": "Control_L", "shift": "Shift_L", "alt": "Alt_L", "home": "Home", "end": "End",
             "delete": "Delete", "backspace": "BackSpace", "left": "Left", "right": "Right", "up": "Up",
             "down": "Down", "enter": "Return", "escape": "Escape", " ": "space", "space": "space", "tab": "Tab"}

    def __init__(self):
        import ctypes
        c = ctypes
        self.c = c
        self.x = c.CDLL("libX11.so.6")
        self.t = c.CDLL("libXtst.so.6")
        x = self.x
        x.XOpenDisplay.restype = c.c_void_p
        x.XOpenDisplay.argtypes = [c.c_char_p]
        x.XDefaultRootWindow.restype = c.c_ulong
        x.XDefaultRootWindow.argtypes = [c.c_void_p]
        x.XStringToKeysym.restype = c.c_ulong
        x.XStringToKeysym.argtypes = [c.c_char_p]
        x.XKeysymToKeycode.restype = c.c_ubyte
        x.XKeysymToKeycode.argtypes = [c.c_void_p, c.c_ulong]
        x.XInternAtom.restype = c.c_ulong
        x.XInternAtom.argtypes = [c.c_void_p, c.c_char_p, c.c_int]
        x.XQueryTree.argtypes = [c.c_void_p, c.c_ulong, c.POINTER(c.c_ulong), c.POINTER(c.c_ulong),
                                 c.POINTER(c.POINTER(c.c_ulong)), c.POINTER(c.c_uint)]
        x.XGetWindowProperty.argtypes = [c.c_void_p, c.c_ulong, c.c_ulong, c.c_long, c.c_long, c.c_int, c.c_ulong,
                                         c.POINTER(c.c_ulong), c.POINTER(c.c_int), c.POINTER(c.c_ulong),
                                         c.POINTER(c.c_ulong), c.POINTER(c.POINTER(c.c_ubyte))]
        x.XMapRaised.argtypes = [c.c_void_p, c.c_ulong]
        x.XSetInputFocus.argtypes = [c.c_void_p, c.c_ulong, c.c_int, c.c_ulong]
        x.XSync.argtypes = [c.c_void_p, c.c_int]
        x.XFlush.argtypes = [c.c_void_p]
        x.XGrabServer.argtypes = [c.c_void_p]
        x.XUngrabServer.argtypes = [c.c_void_p]
        self.t.XTestFakeKeyEvent.argtypes = [c.c_void_p, c.c_uint, c.c_int, c.c_ulong]
        self.d = x.XOpenDisplay(None)
        if not self.d:
            raise AssertionError("xkey: no X display (DISPLAY)")

    def pid_of(self, w):
        c, x = self.c, self.x
        kind, fmt, n, after = c.c_ulong(), c.c_int(), c.c_ulong(), c.c_ulong()
        data = c.POINTER(c.c_ubyte)()
        atom = x.XInternAtom(self.d, b"_NET_WM_PID", 0)
        if x.XGetWindowProperty(self.d, w, atom, 0, 1, 0, 0, c.byref(kind), c.byref(fmt), c.byref(n), c.byref(after),
                                c.byref(data)) != 0 or n.value == 0:
            return None
        return c.cast(data, c.POINTER(c.c_ulong))[0]

    def windows(self):
        c = self.c
        root, parent, n = c.c_ulong(), c.c_ulong(), c.c_uint()
        children = c.POINTER(c.c_ulong)()
        self.x.XQueryTree(self.d, self.x.XDefaultRootWindow(self.d), c.byref(root), c.byref(parent),
                          c.byref(children), c.byref(n))
        return [children[i] for i in range(n.value)]

    def focus(self, g):
        """The window of the game whose folder is g.home shown and focused (another game on the same display, a
        guest's, keeps its own)."""
        pids = set()
        for proc in Path("/proc").iterdir():
            try:
                if f"HORIZON_HOME={g.home}".encode() in (proc / "environ").read_bytes().split(b"\0"):
                    pids.add(int(proc.name))
            except (OSError, ValueError):
                continue
        mine = [w for w in self.windows() if self.pid_of(w) in pids] or self.windows()[-1:]
        if not mine:
            raise AssertionError("xkey: the game has no window on this display")
        self.x.XGrabServer(self.d)
        for w in mine:
            self.x.XMapRaised(self.d, w)
        self.x.XSync(self.d, 0)
        self.x.XSetInputFocus(self.d, mine[-1], 2, 0)
        self.x.XSync(self.d, 0)
        time.sleep(0.2)
        return mine[-1]

    def release(self):
        """The X server let go after the keys: the game reads them (and hides its window again)."""
        time.sleep(0.2)
        self.x.XUngrabServer(self.d)
        self.x.XSync(self.d, 0)

    def code(self, name):
        keysym = self.x.XStringToKeysym(self.NAMES.get(name, name).encode())
        code = self.x.XKeysymToKeycode(self.d, keysym)
        if not code:
            raise AssertionError(f"xkey: no key {name}")
        return code

    def send(self, code, down):
        self.t.XTestFakeKeyEvent(self.d, code, 1 if down else 0, 0)
        self.x.XFlush(self.d)

    def chord(self, chord, hold=0.05):
        """`ctrl+shift+left`: the modifiers go down, the key is pressed (held `hold` s), all go up."""
        codes = [self.code(k) for k in chord.split("+")]
        for code in codes:
            self.send(code, True)
            time.sleep(0.03)
        time.sleep(hold)
        for code in reversed(codes):
            self.send(code, False)
            time.sleep(0.03)

    def text(self, text):
        for ch in text:
            name = "space" if ch == " " else ch.lower() if ch.isalpha() else ch
            self.chord(f"shift+{name}" if ch.isupper() else name)


def joined(g):
    """A guest's game in the host's world with its Java mods running (D-178)."""
    deadline = time.time() + 120
    while time.time() < deadline:
        time.sleep(2)
        try:
            if (g.run("state").get("player") or {}).get("block_pos"):
                break
        except subprocess.CalledProcessError:
            continue
    else:
        raise AssertionError("the guest didn't join the host's world")
    java = len(BUILT_MODS) + len(java_mods(EXTRA + PROOF_MODS))
    deadline = time.time() + 90
    while time.time() < deadline and f"Kiln: {java} of {java} Java mods running" not in " ".join(chat(g)):
        time.sleep(1)
    time.sleep(3)


def restarted(g):
    """The game quit (it saves) and started again in the same folders, back in the same world, with the Java mods
    running again: what a mod saved for the next game (JEI's bookmarks) must come back. Java's log of the first game
    stays beside it (java-1.log)."""
    java_log = g.home / "kiln" / "java.log"
    if java_log.is_file():
        shutil.copy(java_log, g.out / "java-1.log")
    subprocess.run([str(g.ctl), "--socket", g.socket, "quit"], env=g.env, cwd=g.home, capture_output=True)
    until(lambda: subprocess.run([str(g.ctl), "--socket", g.socket, "status"], env=g.env, cwd=g.home,
                                 capture_output=True).returncode, lambda failed: failed != 0, limit=60.0, every=0.5)
    time.sleep(2)
    g.run("launch", "--world", "Proof", "--creative", "--offscreen", "--wait", "--resources", str(g.resources))
    g.run("wait-ready", "90")
    java = len(BUILT_MODS) + len(java_mods(EXTRA + PROOF_MODS))
    deadline = time.time() + 90
    while time.time() < deadline and f"Kiln: {java} of {java} Java mods running" not in " ".join(chat(g)):
        time.sleep(1)
    time.sleep(3)


def component_line(component):
    """A chat component's texts in the order the line shows them: its text (a translation's arguments' for a
    translation, which the game's messages give untranslated), then its siblings'."""
    if isinstance(component, str):
        return component
    if isinstance(component, list):
        return "".join(component_line(part) for part in component)
    if not isinstance(component, dict):
        return "" if component is None else str(component)
    text = component.get("text")
    line = text if isinstance(text, str) else ""
    if "translate" in component and text is None:
        line = "".join(component_line(arg) for arg in component.get("with") or [])
    return line + "".join(component_line(part) for part in component.get("extra") or [])


def tried(g):
    """KILN_PROOF_TRY's steps played and its expectations read, with what every try checks: the mods started, nothing
    failed, the jar's structures found."""
    shots, seen, marks, crafts = {}, {"said": [], "actionbar": []}, {}, {}
    labels, holds, read = {}, {}, {}
    # The window's keyboard (xkey steps) and the host while a guest plays (guest steps), when a step needs them.
    xkeys, host = None, None

    def heard():
        said = g.run("messages", "100")
        # Players' chat is `chat`; the game's and the mods' messages (a command's answer) are `styled`, each a
        # component whose parts' texts make its line: its own text, then its `extra`'s (the game writes the keys
        # sorted, `extra` before `text`, so the JSON's order is not the line's).
        seen["said"] = list(said.get("chat") or []) + [component_line(line) for line in said.get("styled") or []]
        if said.get("actionbar"):
            seen["actionbar"].append(said["actionbar"])

    for name, *args in TRY.get("steps", []):
        if name == "craft":
            # ["craft", "<recipe id>"] or ["craft", "<recipe id>", {"<tag>": "<item>"}] (an item for a tag), or
            # ["craft", "<recipe id>", {"grid": [items, row by row], "makes": ["<item>", count]}] for a recipe whose
            # matching is the mod's Java code.
            crafts[args[0]] = crafted(g, args[0], args[1] if len(args) > 1 else None)
            heard()
            continue
        args = [str(a) for a in args]
        if name == "command":
            g.run("command", args[0])
            time.sleep(0.7)
        elif name == "key" and len(args) > 1 and args[1] == "double":
            # Two quick taps, 0.1 s apart (a mod that wants a double tap within 400 ms: Inventory Drop's K).
            for i in range(2):
                g.run("key", args[0], "down")
                g.run("key", args[0], "up")
                if i == 0:
                    time.sleep(0.1)
            time.sleep(0.5)
        elif name == "key" and "+" in args[0]:
            # A key with modifiers held (["key", "ctrl+a"], ["key", "ctrl+shift+left"]): each modifier down, the key
            # tapped (["key", "shift+left", 3] three times), the modifiers up again in reverse (D-174).
            *held, last = args[0].split("+")
            for k in held:
                g.run("key", k, "down")
            for _ in range(int(args[1]) if len(args) > 1 else 1):
                g.run("key", last, "tap")
                time.sleep(0.2)
            for k in reversed(held):
                g.run("key", k, "up")
            time.sleep(0.5)
        elif name == "key" and len(args) > 1 and args[1].isdigit():
            # A key tapped n times (["key", "backspace", 3]).
            for _ in range(int(args[1])):
                g.run("key", args[0], "tap")
                time.sleep(0.2)
            time.sleep(0.3)
        elif name == "key" and len(args) > 2 and args[1] == "repeat":
            # The key held down with the system's auto-repeat: its press, n repeats, its release (["key",
            # "backspace", "repeat", 5]: Backspace held, erasing six characters).
            g.run("key", args[0], "down")
            for _ in range(int(args[2])):
                g.run("key", args[0], "repeat")
                time.sleep(0.05)
            g.run("key", args[0], "up")
            time.sleep(0.5)
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
        elif name == "hover":
            # The pointer moved there and the wheel not turned: what a screen draws under the pointer (D-163).
            g.run("scroll-at", args[0], args[1], "0")
            time.sleep(0.7)
        elif name == "click":
            g.run("click-at", *args[:3])
            time.sleep(0.7)
        elif name == "drag":
            # A press at the first GUI point, moves through the others, the release at the last (D-167).
            g.run("drag-at", *args)
            time.sleep(0.7)
        elif name == "scroll":
            g.run("scroll-at", args[0], args[1], args[2])
            time.sleep(0.7)
        elif name == "text":
            # Again after a moment when the game had no field focused yet (a click just gave it the focus).
            patient(g, "type", args[0])
            time.sleep(0.7)
        elif name == "wait":
            time.sleep(float(args[0]))
        elif name == "ctl":
            # Any horizon-ctl command (["ctl", "options", "keybinds"]): what the player does by a menu of the game's.
            g.run(*args)
            time.sleep(1)
        elif name == "play":
            # Back to the world from whatever screen is open (Escape until it is).
            g.play()
            time.sleep(0.5)
        elif name == "ui":
            # The open screen's widgets as the game reads them (a field's value, a slot's stack with its components),
            # for a "read" expectation (D-174).
            # Kept beside the screenshots too (try-<name>.json, D-170).
            ui = g.run("ui")
            read[args[0]] = json.dumps(ui.get("widgets", []))
            (g.out / f"try-{args[0]}.json").write_text(json.dumps(ui, indent=1, ensure_ascii=False))
        elif name == "clipboard":
            # The text the game last put on the system's clipboard, for a "read" expectation (D-174).
            read[args[0]] = str((g.run("status").get("debug") or {}).get("clipboard"))
        elif name == "labels":
            # The labels of the screen open now, for a "labeled" expectation.
            labels[args[0]] = [l for l in g.labels() if l]
        elif name == "inventory":
            # The player's slots now, for a "holds" expectation.
            held = g.run("inventory")
            holds[args[0]] = [s for s in (held.get("hotbar") or []) + (held.get("main") or []) + [held.get("carried")]
                              if s and s.get("item")]
        elif name in ("xkey", "xtext"):
            # The window's own keyboard (D-178): ["xkey", "ctrl+a"], ["xkey", "shift+left", 2] (twice),
            # ["xkey", "backspace", "hold", 1.2] (held that long: the X server's auto-repeat), ["xtext", "oak planks"].
            xkeys = xkeys or XKeyboard()
            xkeys.focus(g)
            try:
                if name == "xtext":
                    xkeys.text(args[0])
                elif len(args) > 2 and args[1] == "hold":
                    xkeys.chord(args[0], hold=float(args[2]))
                else:
                    for _ in range(int(args[1]) if len(args) > 1 else 1):
                        xkeys.chord(args[0])
            finally:
                xkeys.release()
            time.sleep(1)
        elif name == "guest":
            # A second game with the same Kiln and mods on the host's world opened to LAN (D-178): ["guest", "join"]
            # and the steps after it are the guest's, ["guest", "rejoin"] the guest's game quit and started again in
            # its folders, joining again (what a mod kept per server must come back), ["guest", "leave"] back to the
            # host. A "file" step reads the guest's files while it plays.
            if args[0] == "join":
                host = g
                opened = host.run("lan", "open", "0", "commands")
                port = (opened or {}).get("port") if isinstance(opened, dict) else None
                port = port or (host.run("lan", "status") or {}).get("port")
                assert port, f"the host's world didn't open to LAN: {opened}"
                guest = GuestGame(host.ctl, host.resources, host.out / "guest", f"localhost:{port}")
                guest.__enter__()
                g = guest
                joined(g)
            elif args[0] == "rejoin":
                subprocess.run([str(g.ctl), "--socket", g.socket, "quit"], env=g.env, cwd=g.home, capture_output=True)
                until(lambda: subprocess.run([str(g.ctl), "--socket", g.socket, "status"], env=g.env, cwd=g.home,
                                             capture_output=True).returncode, lambda failed: failed != 0,
                      limit=60.0, every=0.5)
                time.sleep(2)
                g.run("launch", "--offscreen", "--resources", str(g.resources), "--server", g.address)
                joined(g)
            elif args[0] == "leave":
                g.__exit__(None, None, None)
                g = host
        elif name == "file":
            # ["file", "name", "glob"]: the files the mods wrote in this game's folder, each its path in the folder on a
            # line and its text after it, for a "read".
            read[args[0]] = "".join(f"{f.relative_to(g.home)}\n{f.read_text(errors='replace')}\n"
                                    for f in sorted(g.home.glob(args[1])) if f.is_file())
        elif name == "restart":
            restarted(g)
        elif name == "mark":
            # Where the chat and the action bar are now, for a "said" or "unsaid" from or to it.
            heard()
            marks[args[0]] = collections.Counter(seen["said"] + seen["actionbar"])
        elif name == "shot":
            drawn(g)
            shots[args[0]] = g.shot(f"try-{args[0]}")
        elif name == "hud":
            shots[args[0]] = hud_shot(g, f"try-{args[0]}")
        else:
            raise AssertionError(f"KILN_PROOF_TRY: no step {name}")
        heard()
    if host is not None and g is not host:
        # A guest still playing at the end: it leaves, and the rest reads the host's game.
        g.__exit__(None, None, None)
        g = host
    results = {"steps": len(TRY.get("steps", [])), "expected": []}
    log = json.dumps(g.run("logs", "--lines", "4000"), ensure_ascii=False)
    # The mods' own loggers write to Kiln's java.log, not the game's log (vanilla has one log for both).
    if (g.home / "kiln" / "java.log").is_file():
        log += "\n" + (g.home / "kiln" / "java.log").read_text(errors="replace")
    end = collections.Counter(seen["said"] + seen["actionbar"])
    for e in TRY.get("expect", []):
        if "said" in e or "unsaid" in e:
            # Between two marks when it names them ("from", "to"): the lines the later mark has more of (the game
            # lists the chat's last lines, not in one order); else the whole try.
            a, b = marks.get(e.get("from"), collections.Counter()), marks.get(e.get("to"), end)
            text = list((b - a).elements())
            kind = "said" if "said" in e else "unsaid"
            found = any(re.search(e[kind], line) for line in text)
            ok = found if kind == "said" else not found
            results["expected"].append({kind: e[kind], "from": e.get("from"), "to": e.get("to"), "ok": ok,
                                        "chat": text[-6:] if not ok else None})
        elif "logged" in e:
            results["expected"].append({"logged": e["logged"], "ok": bool(re.search(e["logged"], log))})
        elif "read" in e:
            # What a "ui" or "clipboard" step read.
            found = read.get(e["in"], "")
            ok = bool(re.search(e["read"], found))
            results["expected"].append({"read": e["read"], "in": e["in"], "ok": ok, "text": None if ok else found[:400]})
        elif "labeled" in e:
            # A label of the screen a "labels" step read.
            found = labels.get(e["in"], [])
            ok = any(re.search(e["labeled"], label) for label in found)
            results["expected"].append({"labeled": e["labeled"], "in": e["in"], "ok": ok, "labels": None if ok else found[:40]})
        elif "holds" in e:
            # At least `count` of an item in the player's slots, as an "inventory" step read them.
            item, count, most = e["holds"], e.get("count", 1), e.get("most", math.inf)
            n = sum(s.get("count", 1) for s in holds.get(e["in"], []) if s.get("item") == item)
            results["expected"].append({"holds": item, "in": e["in"], "count": n, "ok": count <= n <= most})
        elif "file" in e:
            # A file the mods wrote in the game's folder (a glob there), holding the text ("has", a regex).
            found = sorted(g.home.glob(e["file"]))
            text = "".join(f.read_text(errors="replace") for f in found if f.is_file())
            ok = bool(found) and bool(re.search(e.get("has", ""), text))
            results["expected"].append({"file": e["file"], "has": e.get("has"), "ok": ok,
                                        "found": [str(f.relative_to(g.home)) for f in found][:5],
                                        "text": text[:400] if not ok else None})
        elif "crafted" in e:
            # The recipe's result in the result slot, as many as it makes, and in the player's inventory after.
            c = crafts.get(e["crafted"]) or {"error": "no craft step for it"}
            made = c.get("makes")
            ok = not c.get("error") and c.get("result_slot") == made and c.get("held", 0) >= made[1] and \
                c.get("named", True)
            results["expected"].append({"crafted": e["crafted"], "ok": ok, "craft": c})
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


def mod_id(jar):
    """A Fabric jar's mod id and name, from its fabric.mod.json."""
    with zipfile.ZipFile(jar) as z:
        meta = json.loads(z.read("fabric.mod.json").decode("utf-8", "replace"), strict=False)
    return meta["id"], meta.get("name", meta["id"])


def overlay(g):
    """Kiln's overlay (D-113, D-127, D-134), as a player meets it, at the proof's 1280x720 and GUI scale 3 (426x240 GUI
    pixels; Overlay.java's layout): its key (F6 here, bound on Kiln's page as a player would rebind F7) opens it over the world (21-overlay-mods.png); the Can turn off now and Crashes
    tabs (22-, 23-), the Crashes tab with a crash of the first extra mod's written as the crash window writes it; the
    search finds that mod and its switch turns it off (24-overlay-switched.png, horizon-mods.txt); its key closes it. Then
    Restart Java now: a first click asks for a second (25-overlay-restart-armed.png), the second starts Java again, in
    a process of its own after Java in the game's process; the banner says so (26-banner.png), Got it closes it; the
    overlay has its badge (27-overlay-badge.png) and the Crashes tab its line about two Javas (28-). `/kiln` in the
    chat opens it too (29-), and Done closes it. Options > Mods' Kiln page has "Java mods..." (30-)."""
    found = {}
    jar = next(j for j in EXTRA if runs_java(j))
    mod, name = mod_id(jar)
    found["mod"] = [mod, name]
    kiln = g.home / "kiln"
    # A crash an hour ago, as the crash window writes it (Crashes.java): the Crashes tab lists it with its switch.
    (kiln / "crashes").write_text(f"{int(time.time() * 1000) - 3_600_000}\tstopped\t{mod}\tclient_tick\t"
                                  "java.lang.IllegalStateException: written by the proof in client_tick\n")

    def screen():
        return g.run("screen").get("screen")

    def log():
        return json.dumps(g.run("logs", "--lines", "4000"))

    def at(x, y, wait=1.0):
        g.run("click-at", str(x), str(y))
        time.sleep(wait)

    g.play()
    g.run("look", "0", "-30")
    g.run("key", OVERLAY_KEY, "tap")
    time.sleep(2.5)
    found["key_opens"] = screen()
    found["mods"] = round(difference(g.shot("21-overlay-mods"), out_of(g, "1-running"), (0.0, 0.0, 1.0, 1.0)), 1)
    at(169, 14)
    g.shot("22-overlay-off-now")
    at(268, 14)
    g.shot("23-overlay-crashes")
    at(75, 14)
    # The search (top right), then the mod's switch, at its row's right.
    at(367, 14, 0.5)
    g.run("type", mod)
    time.sleep(1)
    g.shot("24-overlay-search")
    mods_txt = g.home / "horizon-mods.txt"
    found["list_before"] = mods_txt.read_text() if mods_txt.is_file() else None
    at(396, 40, 2.0)
    g.shot("24-overlay-switched")
    found["list_after"] = mods_txt.read_text() if mods_txt.is_file() else None
    g.run("key", OVERLAY_KEY, "tap")
    time.sleep(1.5)
    found["key_closes"] = screen()
    # Restart Java now, two clicks.
    g.run("key", OVERLAY_KEY, "tap")
    time.sleep(2)
    before = log()
    at(207, 224, 0.7)
    g.shot("25-overlay-restart-armed")
    at(207, 224, 0.5)
    deadline = time.time() + 60
    after = before
    while time.time() < deadline:
        time.sleep(1)
        after = log()
        if after.count("Java mods running") > before.count("Java mods running"):
            break
    found["restarted"] = "Java starts again (Restart Java now)" in after
    later = after.split("Java starts again (Restart Java now)", 1)[-1]
    counts = re.findall(r"Kiln: (\d+) of (\d+) Java mods running", later)
    found["running_after"] = counts[-1] if counts else None
    found["started_mod_after"] = f"Kiln: started {name}" in later
    time.sleep(3)
    found["banner"] = screen()
    g.shot("26-banner")
    found["chat_after"] = [l for l in chat(g) if l.startswith("Kiln:")][-6:]
    # Got it: the left of the banner's two buttons (RestartBanner.java: the middle - 124, 40 under the middle).
    at(149, 170, 1.5)
    found["after_got_it"] = screen()
    g.run("key", OVERLAY_KEY, "tap")
    time.sleep(2.5)
    found["key_after_restart"] = screen()
    g.shot("27-overlay-badge")
    at(268, 14)
    g.shot("28-overlay-crashes-two-javas")
    at(388, 224, 1.5)
    found["done_closes"] = screen()
    # /kiln in the chat, as a player types it.
    g.run("key", "t", "tap")
    time.sleep(0.5)
    g.run("type", "/kiln")
    g.run("key", "enter", "tap")
    time.sleep(2.5)
    found["kiln_command"] = screen()
    g.shot("29-overlay-from-chat")
    at(388, 224, 1.5)
    # Options > Mods: Kiln's page and its button.
    g.run("options", "mods")
    time.sleep(1.5)
    found["options_mods"] = [l for l in g.labels() if l]
    g.shot("30-options-mods")
    g.play()
    found["failed_in_java"] = kiln_failures(g, r" (?:failed in Java|threw) ")
    return found


def out_of(g, name):
    """A screenshot this game took before."""
    return g.out / f"{name}.png"


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
            java = len(BUILT_MODS) + len(java_mods(EXTRA + PROOF_MODS))
            deadline = time.time() + 60
            while time.time() < deadline and f"Kiln: {java} of {java} Java mods running" not in " ".join(chat(g)):
                time.sleep(1)
            results = {"all_mods_started": f"Kiln: {java} of {java} Java mods running" in " ".join(chat(g)),
                       "kiln_said": [l for l in chat(g) if l.startswith("Kiln:")], "structures": {}}
            # Each structure as its plan says (structure_plan): counted where /locate finds it, not located when the mod
            # turns it off; the others' reasons beside them.
            plans = [p for j in EXTRA for p in structure_plan(j, resources)]
            results["structures_not_counted"] = {p["id"]: {"check": p["check"], "why": p["why"]} for p in plans
                                                 if p["check"] not in ("count", "off")}
            if any(p["check"] in ("count", "off") for p in plans):
                g.run("command", "gamemode spectator")
                places = []
                for plan in plans:
                    if plan["check"] == "off":
                        at = located(g, plan["id"])
                        results["structures_off"] = {**results.get("structures_off", {}), plan["id"]: {
                            "located": at is not None, "where": f"at {at[1]} {at[2]} in {at[0]}" if at else None,
                            "why": plan["why"]}}
                    elif plan["check"] == "count":
                        found, where, holds = structure_found(g, plan["id"], plan["blocks"], places, plan["least"])
                        results["structures"][plan["id"]] = {"blocks_of_its_pieces": found, "least": plan["least"],
                                                             "where": where, "holds": holds}
                g.run("command", "gamemode creative")
                g.run("command", "execute in minecraft:overworld run tp @s 0 120 0")
                g.run("wait-ready", "90")
            results.update(tried(g))
            print(json.dumps(results, indent=2, ensure_ascii=False))
            assert results["all_mods_started"], results
            assert not results["trouble"] and not results["java_log_exceptions"], results
            assert all(s["blocks_of_its_pieces"] >= s["least"] for s in results["structures"].values()), results
            assert not any(s["located"] for s in results.get("structures_off", {}).values()), results
            # A structure that can't be built here, or that the try can't tell from the land: not "works" (D-120).
            assert not any(s["check"] in ("fail", "cannot") for s in results["structures_not_counted"].values()), \
                results
            # A mod of structures none of which the try could check isn't shown to work by it.
            assert results["structures"] or results.get("structures_off") or not plans, results
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
            # Their settings screens (D-094), with the armour still on.
            if "showfps" in names:
                results["fps_settings"] = fps_settings(g)
            if "armour-durability" in names:
                results["armour_settings"] = armour_settings(g)
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
            if "fps_settings" in results:
                f = results["fps_settings"]
                assert f["screen"] == "mod" and f["after_done"] == "playing", results
                assert f["button_changed"] > 1 and f["no_box"] > 1, results
                assert f["saved"]["showBackground"] is False and f["saved_again"]["showBackground"] is True, results
            if "armour_settings" in results:
                a = results["armour_settings"]
                assert a["screen"] == "mod" and a["after_save"] == "playing", results
                assert a["saved"]["showHelmet"] is False and a["saved_again"]["showHelmet"] is True, results
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
        if ONLY == "overlay":
            results = {"overlay": overlay(g)}
            print(json.dumps(results, indent=2, ensure_ascii=False))
            o = results["overlay"]
            assert o["key_opens"] == "mod" and o["key_closes"] == "playing", results
            # The switch is Options > Mods' own: off in horizon-mods.txt (mod.<row>:false).
            assert ":false" not in (o["list_before"] or ""), results
            assert ":false" in (o["list_after"] or ""), results
            assert o["restarted"] and o["running_after"] and not o["started_mod_after"], results
            assert o["banner"] == "mod" and o["after_got_it"] == "playing", results
            assert o["key_after_restart"] == "mod" and o["done_closes"] == "playing", results
            assert o["kiln_command"] == "mod", results
            assert not o["failed_in_java"], results
            print(json.dumps({"proof": "kiln", "overlay": True, "ok": True}, indent=2))
            return
        if ONLY == "switch":
            results = {"switch": switched(g, SWITCH)}
            print(json.dumps(results, indent=2, ensure_ascii=False))
            s = results["switch"]
            assert s["row_before"].endswith(": ON") and s["row_after"].endswith(": OFF"), results
            assert s["started_before"], results
            if s["in_process"]:
                # The soft switch (D-113): off at once, no Java started again, the rest from the next game start.
                assert s["soft_off_said"] and s["applies_next_start"], results
                assert not s["restarted"] and not s["java_started_again"], results
                if SWITCH == "ShowFPS":
                    # Drawn as the mods proof has it (over 10), and gone (under 10: the sky's own change is ~2-4).
                    assert s["counter_before"] > 10, results
                    assert s["counter_off"] < 10 and s["counter_next_world"] < 10, results
            else:
                assert s["restarted"] and not s["started_after"], results
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
            if CHEST:
                x, y, z = g.run("state")["player"]["block_pos"]
                results["chest"] = chained(g, x + 2, y, z - 18)
            # The machines' own code: the other carried machines may need what Kiln hasn't yet.
            ran = [SMELT] * bool(SMELT) + POWER + SCREEN + GRIND + FLUIDS + CABLE + TRANSFER + CHEST
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
            if CHEST:
                assert results["chest"]["ingots"] == 4, results
                assert not results["chest"]["left"], results
            if SMELT or POWER or SCREEN or GRIND or FLUIDS or CABLE or TRANSFER or CHEST:
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
                ids, blocks = structures(jar, resources)
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
        bridged = mixin_bridge(g)
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
        java = len(BUILT_MODS) + len(java_mods(EXTRA + PROOF_MODS))
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
            "mixin_bridge": bridged,
            "failed_in_java": kiln_failures(g, r" failed in Java: ", 2000),
        }
        print(json.dumps(results, indent=2, ensure_ascii=False))
        assert results["all_mods_started"], results
        assert all(s["blocks_of_its_pieces"] > 100 for s in results["structures"].values()), results
        assert not EXTRA or results["structures_checked_in"] == "minecraft:overworld", results
        assert not results["failed_in_java"], results
        assert results["nether_stars_from_five_dungeon_chests"] == 5, results
        # Kiln's Mixin bridge (D-102): the proof mod's mixins ran in the game's methods at Horizon's hooks, the
        # player's server tick and their joining.
        assert any(l.endswith("ServerPlayer.tick on the server") for l in results["mixin_bridge"]), results
        assert any("placeNewPlayer" in l for l in results["mixin_bridge"]), results
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
