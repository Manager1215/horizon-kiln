"""Kiln in a running modded game, with Kiln's runtime and two Java mods in <game folder>/kiln/: the spike's mod and
the speedrun timer, the same jar Gradle builds against Minecraft 26.3. Entering a new world starts Java and both
mods; the timer starts by itself; advancements granted by command, trips between dimensions and a pause make its
splits, the dragon's advancement finishes the run, and the finish is a personal best (screenshots and chat).

    sh tools/build-java.sh
    cd .deps/horizon-mods && python3 native/tools/native.py proof --resources PATH kiln

KILN_PROOF_MODS (jars, separated as in PATH) adds other mods: the proof checks they start too, and 1-running.png
shows what they draw. For each structure they define, `/locate` must find it in the world and the blocks there must
include those of its pieces (5-<structure>.png).

Kiln's proof mods (kiln/proof-mods/, target/kiln/proof/) run too, with their data for Horizon (their horizon/): a
mod's loot change puts a nether star in every simple dungeon chest, so five rolls of the table with `/loot` give five;
a mod's switch turns on and off when clicked, saying what it stands on, and on when stood on; a mod's sprout, on
farmland, ripens (`ripe`, which vanilla's wheat hasn't) by its own random tick, which stands in for its template's
(its `age` stays 0); a mod's timer, placed from the hand, faces the player, lights by its scheduled tick a second
later, goes out when a block is set beside it, and says it is broken (dropping a nether star) when broken.

Kiln's server side (D-052): KILN_PROOF_CONTENT names the folders Horizon's import made of those mods' data
(`horizon-port-pack check <jar> <out>`, its <out>/carry), installed in the game and turned on; KILN_PROOF_CONFIG a
folder copied into <game folder>/kiln/config (the Java mods' settings); KILN_PROOF_USE items (comma-separated) a
player in survival is given three of and uses one at a time. Each use's answer from the mod is the server's: what the
player's maximum health, the item's count and the action bar say after each (6-<item>.png). Then the player dies and
respawns: their maximum health and health after it. The mods' saved data right after the player joined is in the
results too, and no call to Java may have failed.
"""
import gzip
import io
import json
import os
import re
import shutil
import struct
import time
import tomllib
import zipfile
from pathlib import Path

from game import Game, difference

BUILT = Path(__file__).resolve().parent.parent / "target" / "kiln"
PROOF_MODS = sorted((BUILT / "proof").glob("*.jar"))
# Their data for Horizon, as its import would carry it (kiln/proof-mods/<name>/horizon/).
PROOF_CONTENT = sorted(p.parent for p in (BUILT.parent.parent / "kiln" / "proof-mods").glob("*/horizon/*.toml"))
EXTRA = [Path(p) for p in os.environ.get("KILN_PROOF_MODS", "").split(os.pathsep) if p]
CONTENT = [Path(p) for p in os.environ.get("KILN_PROOF_CONTENT", "").split(os.pathsep) if p]
CONFIG = os.environ.get("KILN_PROOF_CONFIG")
USE = [i for i in os.environ.get("KILN_PROOF_USE", "").split(",") if i]


def runs_java(jar):
    """Whether a mod has entrypoints for Java; data-only mods (structures) have none."""
    with zipfile.ZipFile(jar) as z:
        if "fabric.mod.json" not in z.namelist():
            return False
        entrypoints = json.loads(z.read("fabric.mod.json").decode("utf-8", "replace"), strict=False).get("entrypoints")
    return bool((entrypoints or {}).get("main") or (entrypoints or {}).get("client"))
# The timer says each split in chat (`chat_splits`), so the proof can read them.
SETTINGS = "corner=top_right\nchat_splits=true\n"
TOP_RIGHT = (0.75, 0.0, 1.0, 0.45)
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
    return ids, {b for b in blocks - NATURAL if not b.endswith(("_log", "_leaves", "_wood"))}


def structure_found(g, structure, blocks):
    """`/locate`s the structure, flies there and counts its pieces' blocks; the count, or why there is none."""
    g.run("command", f"locate structure {structure}")
    time.sleep(4)
    said = json.dumps(g.run("messages", "3"), ensure_ascii=False)
    at = re.search(r"nearest " + re.escape(structure) + r" is at \[(-?\d+), [^,]+, (-?\d+)\]", said)
    if not at:
        return 0, "not located"
    x, z = int(at.group(1)), int(at.group(2))
    g.run("command", f"tp @s {x} 150 {z}")
    g.run("wait-ready", "90")
    time.sleep(3)
    found = 0
    for bx in range(x - 48, x + 48, 16):
        for bz in range(z - 48, z + 48, 16):
            # A 16 x 81 x 16 box: horizon-ctl takes at most 32768 blocks a request.
            counts = g.run("blocks", str(bx), "40", str(bz), str(bx + 15), "120", str(bz + 15)).get("counts", {})
            found += sum(n for name, n in counts.items()
                         if name.split("[")[0].removeprefix("minecraft:") in blocks)
    g.run("command", f"tp @s {x - 40} 110 {z - 40}")
    g.run("wait-ready", "60")
    g.run("look-at", str(x), "70", str(z))
    time.sleep(2)
    g.shot(f"5-{structure.replace(':', '-').replace('/', '-')}")
    return found, f"at {x} {z}"


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
        return super().run(*args)


def chat(g):
    """The timer's and Kiln's chat lines, oldest first."""
    text = json.dumps(g.run("messages", "60"), ensure_ascii=False)
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
    the game says."""
    g.run("command", "clear @s")
    g.run("command", f"give @s {item} 3")
    time.sleep(1)
    g.run("select", "1")
    uses = [{"max_health": max_health(g), "count": count(g, item)}]
    for _ in range(3):
        g.run("use-item")
        time.sleep(1)
        bar = g.run("messages", "1").get("actionbar")
        uses.append({"max_health": max_health(g), "count": count(g, item), "action_bar": bar})
    g.shot(f"6-{item.replace(':', '-')}")
    return uses


def loot_rolled(g, table, item, times=5):
    """How many of `item` rolling `table` `times` times gives."""
    g.run("command", "clear @s")
    for _ in range(times):
        g.run("command", f"loot give @s loot {table}")
    time.sleep(1)
    return count(g, item)


def block(g, x, y, z):
    return g.run("block", str(x), str(y), str(z)).get("state")


def proof_blocks(g):
    """Kiln's proof blocks on a stone floor at y 150, the player on it: the switch (on gold, which it reads) clicked
    twice and stood on, the sprout (on farmland, water beside it) ticked at random, the timer placed from the hand,
    left to tick, a block set beside it, broken."""
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
    time.sleep(1.5)
    g.run("interact", str(sx), str(y), str(sz), "up")
    time.sleep(1)
    found["switch_clicked"] = block(g, sx, y, sz)
    found["switch_said"] = g.run("messages", "1").get("actionbar")
    g.run("interact", str(sx), str(y), str(sz), "up")
    time.sleep(1)
    found["switch_clicked_again"] = block(g, sx, y, sz)
    g.run("command", f"tp @s {sx + 0.5} {y + 1} {sz + 0.5}")
    time.sleep(2)
    found["switch_stood_on"] = block(g, sx, y, sz)
    fx, fz = x - 2, z
    g.run("command", f"setblock {fx} {y} {fz} minecraft:farmland")
    g.run("command", f"setblock {fx - 1} {y} {fz} minecraft:water")
    time.sleep(1)
    g.run("command", f"setblock {fx} {y + 1} {fz} kiln_proof_blocks:sprout")
    time.sleep(1)
    found["sprout_planted"] = block(g, fx, y + 1, fz)
    g.run("command", "gamerule random_tick_speed 1000")
    time.sleep(3)
    found["sprout_ticked"] = block(g, fx, y + 1, fz)
    g.run("command", "gamerule random_tick_speed 3")
    # The timer, placed from the hand two blocks north of the player: it faces them.
    g.run("command", f"tp @s {x + 0.5} {y + 1} {z + 0.5}")
    g.run("command", "clear @s")
    g.run("command", "give @s kiln_proof_blocks:timer 1")
    time.sleep(1)
    g.run("select", "1")
    tx, ty, tz = x, y + 1, z - 2
    # Turned to the north first: `place` turns the player and clicks at once, before the server hears the turn.
    g.run("look", "180", "40")
    time.sleep(0.5)
    g.run("place", str(tx), str(ty), str(tz))
    time.sleep(0.3)
    found["timer_placed"] = block(g, tx, ty, tz)
    time.sleep(2)
    found["timer_ticked"] = block(g, tx, ty, tz)
    g.run("command", f"setblock {tx + 1} {ty} {tz} minecraft:stone")
    time.sleep(1)
    found["timer_beside_a_change"] = block(g, tx, ty, tz)
    g.run("command", "gamemode creative")
    g.run("break", str(tx), str(ty), str(tz))
    time.sleep(1)
    found["timer_broken"] = block(g, tx, ty, tz)
    found["timer_said"] = g.run("messages", "1").get("actionbar")
    found["timer_dropped_a_star"] = "minecraft:nether_star" in json.dumps(g.run("entities", "8", "minecraft:item"))
    return found


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
        while time.time() < deadline and "Kiln: started Kiln Speedrun Timer" not in log:
            time.sleep(1)
            log = json.dumps(g.run("logs", "--lines", "400"))
        time.sleep(2)
        g.shot("1-running")
        # Structure mods: their structures generate in the world by themselves. Checked before the run takes the
        # player to the End, as a spectator (who doesn't fall into the sea the structure stands in).
        structures_found, structures_checked_in = {}, None
        if EXTRA:
            g.run("command", "gamemode spectator")
            structures_checked_in = g.run("state").get("dimension")
            for jar in EXTRA:
                ids, blocks = structures(jar)
                for structure in ids:
                    found, where = structure_found(g, structure, blocks)
                    structures_found[structure] = {"blocks_of_its_pieces": found, "where": where}
            g.run("command", "gamemode creative")
            g.run("command", "tp @s 0 120 0")
            g.run("wait-ready", "90")
        stars = loot_rolled(g, "minecraft:chests/simple_dungeon", "minecraft:nether_star")
        blocks = proof_blocks(g)
        data_after_join = saved_data(g)
        used, respawned = {}, None
        if USE:
            g.run("command", "gamemode survival")
            for item in USE:
                used[item] = use_three(g, item)
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
        java = 2 + sum(runs_java(j) for j in EXTRA + PROOF_MODS)
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
            "respawned": respawned,
            "data_at_the_end": saved_data(g),
            "failed_in_java": re.findall(r"Kiln: [^\"]* failed in Java: [^\"]*",
                                         json.dumps(g.run("logs", "--lines", "2000"))),
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
        # A Java mod's item did something the game shows: the player's maximum health, or the item's count, changed.
        assert all(any((u["max_health"], u["count"]) != (uses[0]["max_health"], uses[0]["count"]) for u in uses[1:])
                   for uses in results["used"].values()), results
        assert results["splits_said"] == ["Nether", "Bastion", "Fortress", "Blind", "Stronghold", "End"], results
        assert "New personal best" in final, results
        assert results["rta_minus_igt"] is not None and results["rta_minus_igt"] >= 1.5, results
        assert results["records_saved"], results
        assert not results["deltas_on_first_run"], results
        assert results["panel_hidden_by_key"] > 4, results
        print(json.dumps({"proof": "kiln", "ok": True}, indent=2))
