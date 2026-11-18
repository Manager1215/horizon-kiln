#!/usr/bin/env python3
"""JEI's census of mods (#222, #223, D-165): every mod jar that declares a JEI plugin (a `jei_mod_plugin`
entrypoint) runs with JEI under Kiln, one JEI each, in kiln/tests/jei_mods.rs, and the reports become one table: per
mod, its plugins and the ones that failed (with the error), its own categories and their recipes, and its recipe
files against the recipes of its namespaces JEI shows.

  python3 tools/jei-census.py run --jei JEI.jar --out DIR [--jobs 6] [--only id,id] [--also JAR ...] JAR_DIR...
  python3 tools/jei-census.py table DIR [--json FILE]
  python3 tools/jei-census.py works [--jei JEI.jar] --out DIR [--jobs 4] [--update] [--resume] [--floor FILE] [JAR_DIR ...]

`run` builds the test once (cargo test --no-run), then runs its binary for each mod; a mod's dependencies are found
among the jars of JAR_DIR by their mod ids (and `provides`), and a mod whose dependency isn't there is left out with
the reason. KILN_RESOURCES must name the game's prepared resources and `sh tools/build-java.sh` must have run. A
report already in DIR is kept, so a run cut short goes on where it stopped. `--also` adds jars to every run (a
library JEI's plugins need). `table` prints the reports in DIR as a table and, with --json, writes the rows.

`works` is the daily census of #222 (D-206): every mod of catalogue/tried.json whose try says it works and whose jar
has recipe files (`data/<ns>/recipe/*.json`) runs with JEI, one JEI each, as `run` does (a mod needs no JEI plugin:
JEI shows the game's recipes of its namespace). A jar is found by its sha256 among the jars of the given directories
(and DIR/jars), or downloaded from its entry's url into DIR/jars. It prints the table of recipe files and the recipes
JEI shows for each, and compares them with the floors in catalogue/jei-census.json: a mod whose shown recipes are
fewer than its floor, or that no longer starts JEI, turns the exit code to 1. `--update` raises the floors to what
this run saw (never lowers one) and adds the mods that were not in the file.
"""

import argparse
import concurrent.futures
import hashlib
import io
import shutil
import json
import os
import subprocess
import sys
import urllib.request
import zipfile

# Dependencies Kiln gives every mod, or that aren't mods: never looked for among the jars.
GIVEN = {"minecraft", "java", "fabricloader", "fabric", "fabric-api", "jei", "mezz_config"}


def mod_json(z):
    try:
        return json.loads(z.read("fabric.mod.json").decode("utf-8", "replace"), strict=False)
    except Exception:
        return None


def ids_of(z, out):
    """The mod ids a jar gives (its own, its `provides`, its nested jars')."""
    data = mod_json(z)
    if isinstance(data, dict):
        if data.get("id"):
            out.add(data["id"])
        for p in data.get("provides") or []:
            if isinstance(p, str):
                out.add(p)
    for name in z.namelist():
        if name.startswith("META-INF/jars/") and name.endswith(".jar"):
            try:
                ids_of(zipfile.ZipFile(io.BytesIO(z.read(name))), out)
            except Exception:
                pass
    return out


def index(dirs):
    """Every jar of the directories: path -> (its fabric.mod.json, the ids it gives)."""
    jars = {}
    for d in dirs:
        for f in sorted(os.listdir(d)):
            if not f.endswith(".jar"):
                continue
            path = os.path.join(d, f)
            try:
                with zipfile.ZipFile(path) as z:
                    data = mod_json(z)
                    if isinstance(data, dict):
                        jars[path] = (data, ids_of(z, set()))
            except Exception:
                pass
    return jars


def depends(data):
    deps = data.get("depends") or {}
    return [d for d in deps if isinstance(d, str)] if isinstance(deps, dict) else []


def plan(jars, paths=None):
    """Mods with a JEI plugin (or, given `paths`, those jars), each with the jars it needs, or why it can't run."""
    by_id = {}
    for path, (data, ids) in jars.items():
        for i in ids:
            by_id.setdefault(i, path)
    runs = []
    seen = set()
    for path, (data, ids) in sorted(jars.items(), key=lambda kv: kv[1][0].get("id", "")):
        ep = data.get("entrypoints") or {}
        if paths is not None:
            if path not in paths:
                continue
        elif not isinstance(ep, dict) or not ep.get("jei_mod_plugin"):
            continue
        mod_id = data.get("id", "")
        if mod_id in GIVEN or mod_id in seen:
            continue
        seen.add(mod_id)
        need, missing, todo = [path], [], list(depends(data))
        while todo:
            dep = todo.pop()
            if dep in GIVEN or dep.startswith("fabric-") or dep in ids:
                continue
            found = by_id.get(dep)
            if found is None:
                missing.append(dep)
            elif found not in need:
                need.append(found)
                todo.extend(depends(jars[found][0]))
        runs.append({"id": mod_id, "jars": need, "missing": sorted(set(missing))})
    return runs


def test_binary(repo):
    """kiln/tests/jei_mods.rs built, and its binary's path."""
    out = subprocess.run(
        ["cargo", "test", "-q", "-p", "horizon-kiln-mod", "--test", "jei_mods", "--no-run", "--message-format=json"],
        cwd=repo, check=True, capture_output=True, text=True).stdout
    for line in out.splitlines():
        try:
            msg = json.loads(line)
        except ValueError:
            continue
        if msg.get("reason") == "compiler-artifact" and msg.get("executable") and msg["target"]["name"] == "jei_mods":
            return msg["executable"]
    sys.exit("jei_mods's test binary wasn't found")


def run_one(binary, repo, jei, out, run, also):
    report = os.path.join(out, run["id"] + ".json")
    if os.path.exists(report):
        return run["id"], "kept"
    if run["missing"]:
        with open(report, "w") as f:
            json.dump({"id": run["id"], "skipped": "missing " + ", ".join(run["missing"])}, f)
        return run["id"], "skipped"
    env = dict(os.environ, KILN_JEI=jei, KILN_JEI_MODS=os.pathsep.join(run["jars"] + also),
               KILN_JEI_REPORT=report + ".part")
    with open(os.path.join(out, run["id"] + ".log"), "w") as log:
        try:
            code = subprocess.run([binary, "--ignored", "--nocapture", "--test-threads=1"], cwd=os.path.join(repo, "kiln"),
                                  env=env, stdout=log, stderr=subprocess.STDOUT, timeout=600).returncode
        except subprocess.TimeoutExpired:
            code = "timeout"
    if os.path.exists(report + ".part"):
        with open(report + ".part") as f:
            data = json.load(f)
        os.remove(report + ".part")
    else:
        data = {"jei_done": False, "mods": []}
    data["id"], data["exit"], data["jars"] = run["id"], code, [os.path.basename(j) for j in run["jars"]]
    with open(report, "w") as f:
        json.dump(data, f, indent=1)
    return run["id"], code


def rows(out):
    result = []
    for f in sorted(os.listdir(out)):
        if not f.endswith(".json") or f == "table.json":
            continue
        with open(os.path.join(out, f)) as fh:
            data = json.load(fh)
        row = {"id": data["id"]}
        if "skipped" in data:
            row["verdict"] = "skipped: " + data["skipped"]
            result.append(row)
            continue
        mod = next((m for m in data.get("mods", []) if m["id"] == data["id"]), None)
        if not data.get("jei_done") or mod is None:
            row["verdict"] = "JEI didn't start (exit %s)" % data.get("exit")
            row["log_tail"] = data.get("log_tail", [])
            row["not_started"] = [l for l in data.get("kiln", []) if "didn't start" in l]
            result.append(row)
            continue
        row.update({k: mod[k] for k in ("categories", "recipes_in_own_categories", "layouts_in_own_categories",
                                         "recipe_files", "recipes_shown")})
        row["plugins"] = len(mod["plugins"])
        row["plugins_failed"] = mod["plugins_failed"]
        errors = [e for c in mod["own"] for e in c["errors"]]
        empty = [c["uid"] for c in mod["own"] if c["recipes"] == 0]
        if mod["plugins_failed"]:
            row["verdict"] = "plugin failed"
        elif errors:
            row["verdict"] = "categories with errors"
        elif mod["categories"] and mod["recipes_in_own_categories"] == 0:
            row["verdict"] = "categories empty"
        else:
            row["verdict"] = "works"
        row["errors"] = errors[:5]
        # What Kiln said of the mods in the run that didn't start (their own entrypoints, apart from the plugin).
        row["not_started"] = [l for l in data.get("kiln", []) if "didn't start" in l]
        row["empty_categories"] = empty
        result.append(row)
    return result


def table(out, json_path=None):
    result = rows(out)
    counts = {}
    for r in result:
        key = r["verdict"].split(":")[0]
        counts[key] = counts.get(key, 0) + 1
    print("| mod | verdict | plugins (failed) | own categories | recipes in them | layouts | recipe files | shown |")
    print("|---|---|---|---|---|---|---|---|")
    for r in result:
        if "categories" not in r:
            print("| %s | %s | | | | | | |" % (r["id"], r["verdict"]))
            continue
        print("| %s | %s | %d (%d) | %d | %d | %d | %d | %d |" % (
            r["id"], r["verdict"], r["plugins"], len(r["plugins_failed"]), r["categories"],
            r["recipes_in_own_categories"], r["layouts_in_own_categories"], r["recipe_files"], r["recipes_shown"]))
    print()
    print("verdicts: " + ", ".join("%s %d" % kv for kv in sorted(counts.items())))
    files = sum(r.get("recipe_files", 0) for r in result)
    shown = sum(min(r.get("recipes_shown", 0), r.get("recipe_files", 0)) for r in result)
    print("recipe files of the mods that ran: %d, of which JEI shows (by id) at most %d" % (files, shown))
    for r in result:
        for p in r.get("plugins_failed", []):
            print("%s: plugin %s: %s" % (r["id"], p["plugin"], p["error"]))
        for e in r.get("errors", []):
            print("%s: %s" % (r["id"], e))
        for e in r.get("not_started", []):
            print("%s: %s" % (r["id"], e[:300]))
        for e in r.get("log_tail", []):
            print("%s: log: %s" % (r["id"], e[:300]))
    if json_path:
        with open(json_path, "w") as f:
            json.dump(result, f, indent=1)


FLOOR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "catalogue", "jei-census.json")


def recipe_files(path):
    """A jar's recipe files, `data/<ns>/recipe/**.json` (or `recipes`); -1 when it can't be read."""
    n = 0
    try:
        with zipfile.ZipFile(path) as z:
            for name in z.namelist():
                parts = name.split("/")
                if len(parts) > 3 and parts[0] == "data" and parts[2] in ("recipe", "recipes") and name.endswith(".json"):
                    n += 1
    except Exception:
        return -1
    return n


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def locate(m, by_sha, out):
    """The jar of a catalogue entry: found by its sha256 among `by_sha`, else downloaded into out/jars."""
    path = by_sha.get(m["jar"]["sha256"])
    if path is None:
        path = os.path.join(out, "jars", m["id"] + ".jar")
        if not os.path.exists(path):
            req = urllib.request.Request(m["jar"]["url"], headers={"User-Agent": "horizon-kiln-dev-census/1.0"})
            with open(path + ".part", "wb") as f:
                f.write(urllib.request.urlopen(req, timeout=120).read())
            os.rename(path + ".part", path)
    return path


def works_jars(repo, out, dirs):
    """Tried mods that work and have recipe files: id -> jar path; the JEI jar; what couldn't be had."""
    with open(os.path.join(repo, "catalogue", "tried.json")) as f:
        tried = json.load(f)["mods"]
    by_sha = {}
    for d in dirs + [os.path.join(out, "jars")]:
        if not os.path.isdir(d):
            continue
        for top, _, names in os.walk(d):  # a cache keeps each jar in a folder of its own
            for name in names:
                path = os.path.join(top, name)
                if name.endswith(".jar") and os.path.getsize(path) < 200_000_000:
                    by_sha.setdefault(sha256(path), path)
    os.makedirs(os.path.join(out, "jars"), exist_ok=True)
    found, gone, jei = {}, {}, None
    for m in tried:
        if m["id"] == "jei":
            jei = locate(m, by_sha, out)
        if (m.get("result") or {}).get("works") is not True:
            continue
        try:
            path = locate(m, by_sha, out)
        except Exception as e:
            gone[m["id"]] = "no jar: %s" % e
            continue
        if recipe_files(path) > 0:
            # One folder of them all, as `index` reads a folder (a mod's dependencies are found among its jars).
            link = os.path.join(out, "jars", m["id"] + ".jar")
            if not os.path.exists(link):
                os.symlink(os.path.abspath(path), link)
            found[m["id"]] = link
    return found, jei, gone


def works(args):
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    reports = os.path.join(args.out, "reports")
    if not args.resume:
        shutil.rmtree(reports, ignore_errors=True)  # a daily census counts this run, not an earlier one's reports
    os.makedirs(reports, exist_ok=True)
    found, jei, gone = works_jars(repo, args.out, args.dirs)
    jars = index(sorted({os.path.join(args.out, "jars")} | {d for d in args.dirs if os.path.isdir(d)}))
    runs = plan(jars, set(found.values()))
    # A report is named by the mod's id in its fabric.mod.json; the floors are by the catalogue's id.
    tried_of = {}
    for tried_id, path in found.items():
        if path in jars:
            tried_of[jars[path][0].get("id", "")] = tried_id
    print("%d mods that work have recipe files; %d run" % (len(found), len(runs)), flush=True)
    binary = test_binary(repo)
    jei = os.path.abspath(args.jei or jei)
    with concurrent.futures.ThreadPoolExecutor(args.jobs) as pool:
        for mod_id, code in pool.map(lambda run: run_one(binary, repo, jei, reports, run, []), runs):
            print(mod_id, code, flush=True)
    result = {tried_of.get(r["id"], r["id"]): r for r in rows(reports) if r["id"] in tried_of}
    floors = {}
    if os.path.exists(args.floor):
        with open(args.floor) as f:
            floors = json.load(f).get("mods", {})
    problems, files, shown = [], 0, 0
    print("| mod | verdict | recipe files | shown | floor |")
    print("|---|---|---|---|---|")
    for tried_id in sorted(found):
        r = result.get(tried_id)
        floor = floors.get(tried_id, {}).get("shown")
        if r is None:
            print("| %s | no report | | | %s |" % (tried_id, floor if floor is not None else ""))
            if floor:
                problems.append("%s: no report (floor %d shown)" % (tried_id, floor))
            continue
        if "recipes_shown" not in r:
            print("| %s | %s | | | %s |" % (tried_id, r["verdict"], floor if floor is not None else ""))
            if floor:
                problems.append("%s: %s (floor %d shown)" % (tried_id, r["verdict"], floor))
            continue
        files += r["recipe_files"]
        shown += min(r["recipes_shown"], r["recipe_files"])
        print("| %s | %s | %d | %d | %s |" % (tried_id, r["verdict"], r["recipe_files"], r["recipes_shown"],
                                              floor if floor is not None else ""))
        if floor is not None and r["recipes_shown"] < floor:
            problems.append("%s: JEI shows %d recipes, the floor is %d" % (tried_id, r["recipes_shown"], floor))
    for tried_id, why in sorted(gone.items()):
        print("| %s | %s | | | |" % (tried_id, why))
    print("\nrecipe files of %d mods: %d, of which JEI shows (by id) at most %d" % (len(result), files, shown))
    if args.update:
        for tried_id, r in result.items():
            if "recipes_shown" in r:
                old = floors.get(tried_id, {})
                floors[tried_id] = {"files": r["recipe_files"], "shown": max(r["recipes_shown"], old.get("shown", 0))}
        with open(args.floor, "w") as f:
            json.dump({"about": "Floors of JEI's recipe census (tools/jei-census.py works, D-206): per mod of "
                                "catalogue/tried.json that works, the recipe files in its jar and the recipes JEI shows "
                                "for its namespaces. The daily mod-checks go red when a mod shows fewer than its floor. "
                                "Raise them with --update, never lower one by hand.", "mods": dict(sorted(floors.items()))},
                      f, indent=1)
            f.write("\n")
        print("floors written to", args.floor)
    for p in problems:
        print("DROPPED:", p)
    sys.exit(1 if problems else 0)


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)
    r = sub.add_parser("run")
    r.add_argument("--jei", required=True)
    r.add_argument("--out", required=True)
    r.add_argument("--jobs", type=int, default=6)
    r.add_argument("--only", default="")
    r.add_argument("--also", nargs="*", default=[])
    r.add_argument("dirs", nargs="+")
    t = sub.add_parser("table")
    t.add_argument("out")
    t.add_argument("--json")
    w = sub.add_parser("works")
    w.add_argument("--jei", help="JEI's jar; by default the catalogue's `jei` entry's")
    w.add_argument("--out", required=True)
    w.add_argument("--jobs", type=int, default=4)
    w.add_argument("--resume", action="store_true", help="keep the reports a cut run left")
    w.add_argument("--floor", default=FLOOR)
    w.add_argument("--update", action="store_true")
    w.add_argument("dirs", nargs="*", default=[])
    args = p.parse_args()
    if args.cmd == "works":
        works(args)
        return
    if args.cmd == "table":
        table(args.out, args.json)
        return
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    os.makedirs(args.out, exist_ok=True)
    runs = plan(index(args.dirs))
    if args.only:
        wanted = set(args.only.split(","))
        runs = [r for r in runs if r["id"] in wanted]
    print("%d mods with a JEI plugin, %d with their dependencies at hand" % (len(runs), sum(not r["missing"] for r in runs)),
          flush=True)
    binary = test_binary(repo)
    jei = os.path.abspath(args.jei)
    with concurrent.futures.ThreadPoolExecutor(args.jobs) as pool:
        for mod_id, code in pool.map(lambda run: run_one(binary, repo, jei, args.out, run, args.also), runs):
            print(mod_id, code, flush=True)
    table(args.out, os.path.join(args.out, "table.json"))


if __name__ == "__main__":
    main()
