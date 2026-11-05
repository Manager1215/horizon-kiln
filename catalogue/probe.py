#!/usr/bin/env python3
"""Runs a Fabric mod on Kiln's test stand: Kiln's Java side, with a scripted session in place of the game.

    python3 catalogue/probe.py --runtime target/kiln-runtime.jar --jar some-mod.jar
    python3 catalogue/probe.py --runtime target/kiln-runtime.jar --all --max-minutes 240

Kiln starts `java kiln.Host` with its runtime and the mods' jars on the class path, sends the
font widths, asks for each client entry point (`load`), then sends a client tick 20 times a
second with the game's state (`tick`), plus key presses (`key`) and advancements (`adv`).
This script does the same with a scripted session: the title screen, a new world, an
advancement, a key press, a trip to the Nether and back, a pause, and leaving the world;
1,200 ticks, about a minute of play, with a player in the world who holds a pickaxe and a torch
and wears worn armour (a Kiln that reads the window's player gets them). It records what Java
answered: which entry points started, what went wrong during ticks, whether anything was drawn
on the HUD, the mod's own output, and what the mod reached that Kiln has only the name of (a
generated stand-in, which does nothing: that part of the mod may not work in the game). No game
is needed: the stand-ins in kiln-runtime.jar play the game's part.

With --all it takes the projects from scan.json that have a client entry point, downloads
their files (cached by hash) and runs them one by one within a time budget, writing runs.json,
which scan.py folds into the statuses: a passing run that reached nothing Kiln has only the name of
gives "Should work" to a mod without Mixin, and a line of evidence to one with Mixin (Kiln ignores
Mixins, so part of such a mod is missing).
"""

import argparse
import datetime
import json
import os
import queue
import re
import shutil
import subprocess
import sys
import tempfile
import threading
import time
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

HERE = os.path.dirname(os.path.abspath(__file__))
GUI = (427, 240)
TICKS = 1200
KEEP = 24  # lines of chat, log and stderr kept per run
NAME_ONLY_CAP = 40  # name-only calls kept per run
# What Kiln logs when a mod calls a generated stand-in: it has the name and nothing behind it.
NAME_ONLY = re.compile(r"Kiln has only the name of ([\w.$]+) so far")
# The stand's player, as Kiln's tick carries the window's player: uuid, name, dimension (the tick's), x, y, z,
# game mode (survival), health, the main hand's item and count, the off hand's, the attributes.
PLAYER = ["00000000-0000-0000-0000-000000c0ffee", "Stand", None, "0.5", "70.0", "0.5", "0", "20.0",
          "minecraft:iron_pickaxe", "1", "minecraft:torch", "16", "minecraft:max_health 20.0 20.0"]
# What the player wears and holds, with damage (slot item count damage max_damage), then F1 and F3: off.
EQUIPMENT = ";".join([
    "mainhand minecraft:iron_pickaxe 1 40 250", "offhand minecraft:torch 16 0 0",
    "feet minecraft:leather_boots 1 10 65", "chest minecraft:iron_chestplate 1 0 240",
    "head minecraft:iron_helmet 1 20 165"])


def name_only(logs):
    """The calls a run made that Kiln has only the name of (`Minecraft.getFrameTimeNs`), sorted."""
    return sorted({m.group(1) for line in logs for m in NAME_ONLY.finditer(line)})


def read_manifest(jar_path):
    try:
        with zipfile.ZipFile(jar_path) as jar:
            with jar.open("fabric.mod.json") as f:
                data = json.load(f)
        return data if isinstance(data, dict) else {}
    except (KeyError, ValueError, OSError, zipfile.BadZipFile):
        return {}


def entrypoints_of(manifest):
    """The entry points Kiln 0.2.0 runs, main first, then client: [(kind, class)], as Kiln reads them."""
    out = []
    for kind in ("main", "client"):
        entries = (manifest.get("entrypoints") or {}).get(kind) or []
        for entry in entries if isinstance(entries, list) else []:
            if isinstance(entry, dict):
                entry = entry.get("value")
            if isinstance(entry, str) and entry and (kind, entry) not in out:
                out.append((kind, entry))
    return out


def client_entrypoints(manifest):
    return [c for kind, c in entrypoints_of(manifest) if kind == "client"]


def escape(field):
    return field.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r")


def unescape(field):
    out = []
    i = 0
    while i < len(field):
        c = field[i]
        if c == "\\" and i + 1 < len(field):
            n = field[i + 1]
            out.append({"t": "\t", "n": "\n", "r": "\r"}.get(n, n))
            i += 2
        else:
            out.append(c)
            i += 1
    return "".join(out)


def java_program():
    home = os.environ.get("KILN_JAVA_HOME") or os.environ.get("JAVA_HOME")
    if home:
        return os.path.join(home, "bin", "java")
    return "java"


class Host:
    """`java kiln.Host`: commands in, replies out, with a reader thread so nothing blocks."""

    def __init__(self, runtime, jars, kiln_dir, log_path, sandbox):
        class_path = os.pathsep.join([runtime] + list(jars))
        command = [java_program(), "-Xmx512m", "-Djava.awt.headless=true", "-Dkiln.dir=" + kiln_dir,
                   "-cp", class_path, "kiln.Host"]
        if sandbox:
            command = ["unshare", "-rn", "--"] + command
        self.log = open(log_path, "wb")
        self.process = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                        stderr=self.log, cwd=kiln_dir)
        self.replies = queue.Queue()
        self.reader = threading.Thread(target=self._read, daemon=True)
        self.reader.start()

    def _read(self):
        for raw in self.process.stdout:
            line = raw.decode("utf-8", "replace").rstrip("\n")
            fields = line.split("\t")
            self.replies.put((fields[0], [unescape(f) for f in fields[1:]]))
        self.replies.put(("exited", []))

    def send(self, command, *fields):
        line = "\t".join([command] + [escape(str(f)) for f in fields]) + "\n"
        try:
            self.process.stdin.write(line.encode("utf-8"))
            self.process.stdin.flush()
            return True
        except (BrokenPipeError, OSError):
            return False

    def wait_done(self, command, timeout, sink):
        """Collects replies until `done <command>`; returns False on timeout or exit."""
        deadline = time.monotonic() + timeout
        while True:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                return False
            try:
                kind, fields = self.replies.get(timeout=remaining)
            except queue.Empty:
                return False
            if kind == "exited":
                sink.exited = True
                return False
            sink.take(kind, fields)
            if kind == "done" and fields and fields[0] == command:
                return True

    def close(self):
        try:
            self.process.stdin.close()
        except OSError:
            pass
        try:
            self.process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            self.process.kill()
            try:
                self.process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                pass
        self.log.close()


class Sink:
    """What Java said, sorted by kind."""

    def __init__(self):
        self.ready = ""
        self.loaded = []
        self.failed = {}
        self.chat = []
        self.logs = []
        self.draws = 0
        self.last_draw = []
        self.hud_seen = False
        self.exited = False

    def take(self, kind, fields):
        if kind == "ready":
            self.ready = fields[0] if fields else ""
        elif kind == "loaded" and fields:
            self.loaded.append(fields[0])
        elif kind == "failed" and fields:
            self.failed[fields[0]] = fields[1] if len(fields) > 1 else "failed"
        elif kind == "chat":
            self.chat.append(" ".join(fields))
        elif kind == "log":
            self.logs.append(" ".join(fields))
        elif kind == "draw":
            self.draws += 1
            self.last_draw = fields
            if fields:
                self.hud_seen = True


def session(ticks):
    """The scripted play: for each tick, (connected, in_world, paused, game_time, dimension, keys, advancements)."""
    for n in range(1, ticks + 1):
        keys, advancements = [], []
        if n <= 20:
            yield n, (0, 0, 0, 0, "", keys, advancements)
            continue
        if n <= 40:
            yield n, (1, 0, 0, 0, "", keys, advancements)
            continue
        game_time = n - 40
        dimension = "minecraft:overworld"
        paused = 0
        if n == 100:
            advancements = ["minecraft:story/root"]
        if n == 120:
            keys = ["key.keyboard.j"]
        if n == 300:
            advancements = ["minecraft:story/mine_stone"]
        if 600 < n <= 700:
            dimension = "minecraft:the_nether"
            if n == 601:
                advancements = ["minecraft:story/enter_the_nether"]
        if 900 < n <= 950:
            paused = 1
        if n > ticks - 20:
            yield n, (0, 0, 0, 0, "", keys, advancements)
            continue
        yield n, (1, 1, paused, game_time, dimension, keys, advancements)


def library_jars(folder):
    """The jars in Kiln's lib/ folder, sorted, or nothing when there is no folder."""
    if not folder or not os.path.isdir(folder):
        return []
    return sorted(os.path.join(folder, name) for name in os.listdir(folder) if name.endswith(".jar"))


def run_one(runtime, jar_path, deps=(), ticks=TICKS, timeout=120, sandbox=False):
    """Runs one mod jar (plus dependency jars) and returns the record for runs.json."""
    started = time.monotonic()
    manifest = read_manifest(jar_path)
    entrypoints = entrypoints_of(manifest)
    record = {
        "ran": False,
        "passed": False,
        "why": "",
        "mod": {"id": manifest.get("id", ""), "name": manifest.get("name", ""), "version": str(manifest.get("version", ""))},
        "entrypoints": {"wanted": [c for _kind, c in entrypoints], "kinds": {c: kind for kind, c in entrypoints},
                        "loaded": [], "failed": {}},
        "ticks": 0,
        "tick_errors": 0,
        "hud": False,
        "chat": [],
        "logs": [],
        "stderr": [],
        "exceptions": 0,
        "name_only": [],
        "java": "",
        "duration_ms": 0,
    }
    if not manifest:
        record["why"] = "no fabric.mod.json, not a Fabric mod"
        return record
    if not entrypoints:
        record["why"] = "no client or main entrypoint: no code for Kiln to run"
        return record

    work = tempfile.mkdtemp(prefix="kiln-probe-")
    kiln_dir = os.path.join(work, "kiln")
    os.makedirs(os.path.join(kiln_dir, "mods"))
    log_path = os.path.join(kiln_dir, "java.log")
    sink = Sink()
    host = None
    try:
        host = Host(runtime, [jar_path] + list(deps), kiln_dir, log_path, sandbox)
        # The host says "ready" first, by itself; then the font, then the entry points.
        if not host.send("font", ",".join(["6"] * 256)) or not host.wait_done("font", 30, sink):
            record["why"] = "Java did not start" if not sink.ready else "Java stopped during start"
            return record
        record["java"] = sink.ready
        for kind, entry in entrypoints:
            host.send("load", entry, kind)
            if not host.wait_done("load", timeout, sink):
                record["why"] = "no answer for " + entry + (" (Java stopped)" if sink.exited else " (timed out)")
                break
        record["ran"] = True
        record["entrypoints"]["loaded"] = sink.loaded
        record["entrypoints"]["failed"] = sink.failed
        if not record["why"] and not sink.failed:
            deadline = time.monotonic() + timeout
            for n, (connected, in_world, paused, game_time, dimension, keys, advancements) in session(ticks):
                if time.monotonic() > deadline:
                    record["why"] = "ticks took too long"
                    break
                ok = True
                for key in keys:
                    ok = ok and host.send("key", key) and host.wait_done("key", 10, sink)
                for advancement in advancements:
                    ok = ok and host.send("adv", advancement) and host.wait_done("adv", 10, sink)
                fields = [n, connected, in_world, paused, game_time, dimension, GUI[0], GUI[1], 60]
                if in_world:
                    fields += [dimension if v is None else v for v in PLAYER] + [EQUIPMENT, 0, 0]
                ok = ok and host.send("tick", *fields)
                ok = ok and host.wait_done("tick", 10, sink)
                if not ok:
                    record["why"] = "Java stopped at tick %d" % n if sink.exited else "no answer at tick %d" % n
                    break
                record["ticks"] = n
        record["tick_errors"] = sum(1 for line in sink.logs if "threw" in line or "failed:" in line)
        record["name_only"] = name_only(sink.logs)[:NAME_ONLY_CAP]
        record["hud"] = sink.hud_seen
        record["chat"] = sink.chat[:KEEP]
        record["logs"] = sink.logs[:KEEP]
        if sink.failed:
            record["why"] = "; ".join("%s did not start: %s" % (k.rsplit(".", 1)[-1], v) for k, v in sink.failed.items())[:400]
        elif not record["why"] and record["tick_errors"]:
            first = next(line for line in sink.logs if "threw" in line or "failed:" in line)
            record["why"] = ("%d tick%s with errors, first: %s" % (
                record["tick_errors"], "" if record["tick_errors"] == 1 else "s", first))[:400]
        record["passed"] = bool(record["ran"] and not sink.failed and not record["why"] and record["ticks"] >= ticks)
        if record["passed"]:
            record["why"] = "client entrypoint started, %d ticks without errors, %s" % (
                ticks, "HUD drawn" if record["hud"] else "nothing drawn on the HUD")
            if record["name_only"]:
                record["why"] += ", %d call%s to what Kiln has only the name of" % (
                    len(record["name_only"]), "" if len(record["name_only"]) == 1 else "s")
    finally:
        if host:
            host.close()
        try:
            with open(log_path, "rb") as f:
                lines = f.read().decode("utf-8", "replace").splitlines()
            record["exceptions"] = sum(1 for line in lines if re.search(r"(Exception|Error)\b", line))
            record["stderr"] = [line[:200] for line in lines[-KEEP:]]
        except OSError:
            pass
        shutil.rmtree(work, ignore_errors=True)
    record["duration_ms"] = int((time.monotonic() - started) * 1000)
    return record


def load_json(path, default):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError):
        return default


def write_json(path, data):
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=1, ensure_ascii=False)
        f.write("\n")
    os.replace(tmp, path)


PROVIDED = {"minecraft", "java", "fabricloader", "fabric-api", "fabric"}  # Kiln itself stands in for these


def provided_by_kiln(mod_id):
    """Fabric API modules and the loader are Kiln's stand-ins; Kotlin's runtime is a real dependency."""
    return mod_id in PROVIDED or (mod_id.startswith("fabric-") and mod_id != "fabric-language-kotlin")


def dependency_map(state, cache_dir):
    """mod id -> (slug, entry) for every scanned project, from the mod ids the scan kept in its cache."""
    out = {}
    for slug, entry in state["projects"].items():
        version = entry.get("version") or {}
        key = (version.get("file", {}).get("sha512") or version.get("id") or "")[:40]
        if not key or not version.get("file", {}).get("url"):
            continue
        needs = load_json(os.path.join(cache_dir, key + ".json"), None) or {}
        mod_id = needs.get("mod_id") or ""
        if mod_id and mod_id not in out:
            out[mod_id] = (slug, entry)
    return out


ALIASES = {"cloth-config2": "cloth-config", "balm-fabric": "balm"}  # ids mods declare for a project whose id differs


def dependencies_of(entry, by_mod_id, depth=3):
    """The scanned projects a mod depends on, transitively, as [(slug, entry)]; Kiln's own stand-ins left out."""
    found = []
    seen = {((entry.get("summary") or {}).get("mod_id") or "")}
    frontier = [entry]
    for _level in range(depth):
        next_frontier = []
        for current in frontier:
            for dep_id in (current.get("summary") or {}).get("depends", []):
                if dep_id in seen or provided_by_kiln(dep_id):
                    continue
                seen.add(dep_id)
                hit = by_mod_id.get(dep_id) or by_mod_id.get(ALIASES.get(dep_id, ""))
                if hit and hit[0] != entry.get("slug"):
                    found.append(hit)
                    next_frontier.append(hit[1])
        frontier = next_frontier
    return found


def stopped_at_another_mod(record, scope):
    """True when a failed run tripped over a class that is neither Kiln's business nor the JDK's."""
    from scan import symbols_in
    if not record or not record.get("ran") or record.get("passed"):
        return False
    failed = " ".join(list((record.get("entrypoints") or {}).get("failed", {}).values()) + [record.get("why", "")])
    return any(not s.startswith("java/") and not any(s.startswith(p) for p in scope) for s in symbols_in(failed))


def run_all(args):
    from scan import download, TooBig  # the same downloader and limits as the scan
    state = load_json(args.state, None)
    if not state or not state.get("projects"):
        print("scan.json is missing or empty:", args.state, file=sys.stderr)
        return 2
    runs = load_json(args.runs, {"schema": 1, "runs": {}})
    results = runs.setdefault("runs", {})
    own = {"schema": 1, "runs": {}}  # this run's records alone, when --runs-out is given
    rules = load_json(os.path.join(HERE, "rules.json"), {})
    max_bytes = rules.get("max_jar_bytes", 60000000)
    only = {s.strip() for s in args.only.split(",") if s.strip()}

    def key_of(entry):
        version = entry.get("version") or {}
        return (version.get("file", {}).get("sha512") or version.get("id") or "")[:40]

    todo = []
    for slug, entry in state["projects"].items():
        if only and slug not in only:
            continue
        summary = entry.get("summary") or {}
        version = entry.get("version") or {}
        if not version.get("file", {}).get("url") or not summary:
            continue
        kinds = summary.get("entrypoints") or {}
        if not (kinds.get("client") or kinds.get("main")):
            continue
        # Mods with Mixin run too: Kiln ignores their Mixins and runs their entry points as they are.
        key = key_of(entry)
        if args.only_needs_deps:
            if not stopped_at_another_mod(results.get(key), rules.get("scope", [])):
                continue
        elif key in results and not args.rerun:
            continue
        missing = (entry.get("missing") or {}).get("total", 0)
        todo.append((0 if entry.get("candidate") else 1, missing, slug, entry, key))
    todo.sort(key=lambda t: (t[0], t[1], t[2]))
    if args.shard:
        index, count = (int(x) for x in args.shard.split("/"))
        todo = todo[index::count]
    if args.limit:
        todo = todo[:args.limit]
    print("to run: %d mods (of %d in the scan)" % (len(todo), len(state["projects"])))

    libs = library_jars(args.libs)
    by_mod_id = {}
    if args.with_deps:
        by_mod_id = dependency_map(state, args.needs_cache or os.path.join(os.path.dirname(os.path.abspath(args.state)), "cache"))
        print("dependencies: %d mod ids known from the scan" % len(by_mod_id))
    started = time.monotonic()
    counts = {"ran": 0, "passed": 0, "failed": 0, "skipped": 0, "errors": 0}
    os.makedirs(args.cache, exist_ok=True)
    for _rank, _missing, slug, entry, key in todo:
        if (time.monotonic() - started) > args.max_minutes * 60:
            counts["skipped"] += 1
            continue
        version = entry["version"]
        jar_path = os.path.join(args.cache, key + ".jar")
        try:
            if not os.path.isfile(jar_path):
                if version["file"]["size"] > max_bytes:
                    raise TooBig()
                path, _digest = download(version["file"]["url"], max_bytes, version["file"]["sha512"])
                shutil.move(path, jar_path)
            dep_jars, dep_slugs = [], []
            if args.with_deps:
                for dep_slug, dep_entry in dependencies_of(entry, by_mod_id):
                    dep_version = dep_entry.get("version") or {}
                    dep_path = os.path.join(args.cache, key_of(dep_entry) + ".jar")
                    try:
                        if not os.path.isfile(dep_path):
                            if dep_version["file"]["size"] > max_bytes:
                                raise TooBig()
                            got, _digest = download(dep_version["file"]["url"], max_bytes, dep_version["file"]["sha512"])
                            shutil.move(got, dep_path)
                    except Exception:  # a dependency that cannot be fetched is simply absent, as it would be in the game
                        continue
                    dep_jars.append(dep_path)
                    dep_slugs.append(dep_slug)
            record = run_one(args.runtime, jar_path, deps=dep_jars + libs, ticks=args.ticks, timeout=args.timeout, sandbox=args.sandbox)
            if dep_slugs:
                record["with_deps"] = dep_slugs
        except TooBig:
            record = {"ran": False, "passed": False, "why": "file too big to run"}
            counts["errors"] += 1
        except Exception as error:  # one mod must not stop the night's work
            record = {"ran": False, "passed": False, "why": "could not run: %s" % str(error)[:200]}
            counts["errors"] += 1
        finally:
            if not args.keep_jars and os.path.isfile(jar_path):
                os.unlink(jar_path)
        record["slug"] = slug
        record["date"] = datetime.date.today().isoformat()
        record["kiln"] = args.kiln or state.get("kiln", "")
        results[key] = record
        counts["ran" if record.get("ran") else "errors" if not record.get("why") else "ran"] += 0
        if record.get("ran"):
            counts["ran"] += 1
            counts["passed" if record.get("passed") else "failed"] += 1
        if args.runs_out:
            own["runs"][key] = record
            write_json(args.runs_out, own)
        else:
            write_json(args.runs, runs)
        print("%-28s %-8s %s" % (slug[:28], "passed" if record.get("passed") else "failed" if record.get("ran") else "-", record.get("why", "")[:90]))
    runs["updated"] = datetime.date.today().isoformat()
    if args.runs_out:
        own["updated"] = runs["updated"]
        write_json(args.runs_out, own)
    else:
        write_json(args.runs, runs)
    print("ran %(ran)d: passed %(passed)d, failed %(failed)d; errors %(errors)d; out of time %(skipped)d" % counts)
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--runtime", required=True, help="kiln-runtime.jar")
    parser.add_argument("--jar", help="one mod jar to run")
    parser.add_argument("--deps", nargs="*", default=[], help="dependency jars to put on the class path")
    parser.add_argument("--libs", default="", help="folder of library jars Kiln puts on the class path beside its runtime (lib/)")
    parser.add_argument("--out", help="write the record here instead of stdout")
    parser.add_argument("--all", action="store_true", help="run the projects from scan.json")
    parser.add_argument("--state", default=os.path.join(HERE, "scan.json"))
    parser.add_argument("--runs", default=os.path.join(HERE, "runs.json"))
    parser.add_argument("--runs-out", default="", help="write only this run's records here (for shards run side by side; fold them into --runs afterwards)")
    parser.add_argument("--cache", default=os.path.join(HERE, "cache", "jars"))
    parser.add_argument("--kiln", default="")
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--shard", default="", help="run every n-th mod: i/n, for several probes side by side, each with its own --runs and --cache")
    parser.add_argument("--with-deps", action="store_true", help="put the jars of the mods a mod depends on (fabric.mod.json depends, found in the scan) on its class path")
    parser.add_argument("--only-needs-deps", action="store_true", help="only mods whose last run stopped at a class of another mod")
    parser.add_argument("--needs-cache", default="", help="scan.py's cache folder, for the mod ids behind dependencies (default: next to --state)")
    parser.add_argument("--only", default="")
    parser.add_argument("--rerun", action="store_true", help="run again what already has a result")
    parser.add_argument("--keep-jars", action="store_true")
    parser.add_argument("--max-minutes", type=int, default=240)
    parser.add_argument("--ticks", type=int, default=TICKS)
    parser.add_argument("--timeout", type=int, default=120, help="seconds per entry point and for all the ticks")
    parser.add_argument("--sandbox", action="store_true", help="run Java in a network-less namespace (Linux, unshare -rn)")
    args = parser.parse_args()
    if not os.path.isfile(args.runtime):
        print("kiln-runtime.jar not found:", args.runtime, file=sys.stderr)
        return 2
    if args.all:
        return run_all(args)
    if not args.jar:
        parser.error("--jar or --all")
    record = run_one(args.runtime, args.jar, list(args.deps) + library_jars(args.libs), args.ticks, args.timeout, args.sandbox)
    text = json.dumps(record, indent=1, ensure_ascii=False)
    if args.out:
        with open(args.out, "w", encoding="utf-8") as f:
            f.write(text + "\n")
    else:
        print(text)
    return 0 if record["passed"] else 1


if __name__ == "__main__":
    sys.exit(main())
