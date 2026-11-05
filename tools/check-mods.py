"""Checks every mod Kiln says works, and says pass or fail for each.

The mods are those catalogue/verified.json marks `works` or `in-development`; how each is checked is in
catalogue/checks.json (a mod marked working with no entry there fails the run). For each mod:

  without the game  its checks (kiln/tests/coverage.rs, kiln/tests/tech_reborn.rs) with its jar alone: the mod's own
                    feature, as the checks' asserts say it (an FPS counter that follows the frame rate, a pickaxe
                    repaired with experience), not only that it starts;
  crashes           what Kiln logged meanwhile: a call that failed in Java, a HUD element that threw, a mod that
                    didn't start, Java stopping;
  together          then every mod's jar in one game, as a player has them, and every check again;
  in the game       with --proof, kiln/proof.py with that mod (screenshots, and the numbers proof.py reads off them).

Jars are downloaded into a cache outside the repository (each checked against its SHA-256 in checks.json); the
report goes beside them, and a summary to the terminal. Needs a JDK, cargo and `sh tools/build-java.sh` first.

    python3 -I tools/check-mods.py                        # every working mod, without the game
    python3 -I tools/check-mods.py --only showfps,heart-crystals
    python3 -I tools/check-mods.py --proof                # and in the game
    python3 -I tools/check-mods.py --proof "$R"           # with these prepared resources (.../horizon-client/26.3)

The game runs offscreen (no window, no keyboard), so --proof can run while you work. Mods whose proof needs
Horizon's import of their data (Heart Crystals, Tech Reborn) get it from horizon-port-pack, made once per jar;
--content ID=FOLDER gives one instead.

Exit status: 0 when every mod passed, 1 otherwise.
"""
import argparse
import datetime
import glob
import hashlib
import json
import os
import platform
import re
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WORKING = ("works", "in-development")
# Kiln's log lines that mean something broke (kiln/mixins/kiln/runtime.rs, kiln/java/kiln/Host.java, Server.java).
CRASHES = [
    re.compile(r"Kiln: .*failed in Java"),
    re.compile(r"Kiln: .*\bthrew\b"),
    re.compile(r"Kiln: .*Java stopped"),
    re.compile(r"Kiln: .*didn't start"),
    re.compile(r"Kiln: .*command \S+ failed:"),
    re.compile(r"Kiln: .*fails in Kiln"),
    re.compile(r"Kiln: .*makes no block entity"),
    re.compile(r"Kiln: .*unexpected answer"),
]
RUNNING = re.compile(r"Kiln: (\d+) of (\d+) Java mods running")
NAME_ONLY = re.compile(r"Kiln has only the name of (\S+) so far")
TEST_TIMEOUT = 20 * 60
PROOF_TIMEOUT = 40 * 60
# Before each proof, how long to wait for a busy computer to calm down (its load under its cores).
CALM_WAIT = 20 * 60


def default_cache():
    if os.environ.get("KILN_CHECK_CACHE"):
        return Path(os.environ["KILN_CHECK_CACHE"])
    if platform.system() == "Darwin":
        return Path.home() / "Library" / "Caches" / "horizon-kiln-checks"
    return Path(os.environ.get("XDG_CACHE_HOME") or Path.home() / ".cache") / "horizon-kiln-checks"


def read_lists():
    """The mods to check (verified.json's working ones, with checks.json's entry or None) and the lists' problems."""
    verified = json.loads((ROOT / "catalogue" / "verified.json").read_text())["mods"]
    checks = {m["id"]: m for m in json.loads((ROOT / "catalogue" / "checks.json").read_text())["mods"]}
    working = [v for v in verified if v.get("status") in WORKING]
    problems = []
    mods = []
    for v in working:
        entry = checks.get(v["id"])
        if entry is None:
            problems.append(f"{v['id']}: marked {v['status']} in catalogue/verified.json, but catalogue/checks.json "
                            "has no checks for it")
        mods.append((v, entry))
    for mod_id in checks:
        if mod_id not in {v["id"] for v in working}:
            problems.append(f"{mod_id}: in catalogue/checks.json, but catalogue/verified.json doesn't mark it working")
    # The generator's list (tools/stub-mods.txt) names mods Kiln was made to work for: each must be checked, from the
    # same download.
    urls = {e["jar"].get("url") for e in checks.values()}
    for line in (ROOT / "tools" / "stub-mods.txt").read_text().splitlines():
        line = line.strip()
        if line and not line.startswith("#") and line not in urls:
            problems.append(f"tools/stub-mods.txt names {line}, which no mod in catalogue/checks.json downloads")
    return mods, problems


def fetch(jar, cache):
    """The jar's path: built here (a glob under the repository) or downloaded into the cache and checked."""
    if "built" in jar:
        found = sorted(glob.glob(str(ROOT / jar["built"])))
        if not found:
            raise RuntimeError(f"no {jar['built']}: run sh tools/build-java.sh")
        return Path(found[-1])
    name = urllib.parse.unquote(jar["url"].rsplit("/", 1)[-1])
    if not name.endswith(".jar") or "/" in name or name.startswith("."):
        raise RuntimeError(f"not a jar's name: {name}")
    # Each download in a folder of its own, named by its hash.
    folder = cache / "jars" / jar["sha256"]
    path = folder / name
    if path.is_file() and sha256(path) == jar["sha256"]:
        return path
    folder.mkdir(parents=True, exist_ok=True)
    part = folder / (name + ".part")
    request = urllib.request.Request(jar["url"], headers={"User-Agent": "horizon-kiln-checks"})
    with urllib.request.urlopen(request, timeout=120) as response, open(part, "wb") as out:
        shutil.copyfileobj(response, out)
    got = sha256(part)
    if got != jar["sha256"]:
        part.unlink()
        raise RuntimeError(f"{jar['url']}: SHA-256 {got}, not the pinned {jar['sha256']}")
    part.rename(path)
    return path


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def crashes(text):
    """Kiln's log lines that say something broke, and whether every Java mod started."""
    found = [line.strip() for line in text.splitlines() if any(p.search(line) for p in CRASHES)]
    # Kiln says which mod didn't start, and why; the count alone only when it doesn't.
    if not any("didn't start" in line for line in found):
        for running, total in RUNNING.findall(text):
            if running != total:
                found.append(f"only {running} of {total} Java mods started")
    if "Exception in thread" in text:
        found += [line.strip() for line in text.splitlines() if "Exception in thread" in line]
    return list(dict.fromkeys(found))


def known_split(found, known):
    """The crash lines checks.json knows about (`known`: a pattern and why) apart from the others, and the known
    problems not seen this time (fixed, so their entries can go)."""
    seen, others = [], []
    for line in found:
        hit = next((k for k in known if re.search(k["says"], line)), None)
        (seen if hit else others).append(line)
    gone = [k["says"] for k in known if not any(re.search(k["says"], line) for line in found)]
    return others, seen, gone


def cargo_test(test, env, log):
    """Runs one test file of Kiln's with real Java; its output (and its games' java.log files) and exit status."""
    tmp = log.with_suffix(".tmp")
    shutil.rmtree(tmp, ignore_errors=True)
    tmp.mkdir(parents=True)
    command = ["cargo", "test", "-p", "horizon-kiln-mod", "--test", test, "--", "--ignored", "--nocapture"]
    started = time.time()
    try:
        done = subprocess.run(command, cwd=ROOT, env={**os.environ, **env, "TMPDIR": str(tmp)},
                              stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, errors="replace",
                              timeout=TEST_TIMEOUT)
        output, status = done.stdout, done.returncode
    except subprocess.TimeoutExpired as e:
        output, status = (e.stdout or b"").decode("utf-8", "replace") + "\n(timed out)", -1
    java_logs = ""
    for java_log in sorted(tmp.glob("*/kiln/java.log")):
        java_logs += f"\n== {java_log.relative_to(tmp)}\n" + java_log.read_text(errors="replace")
    log.write_text(f"$ {' '.join(command)}\n{output}\n{java_logs}")
    shutil.rmtree(tmp, ignore_errors=True)
    return output, java_logs, status, round(time.time() - started, 1)


def first_error(output, expected):
    """The first line that says why a check failed."""
    for pattern in (r"^check \S+: FAILED: .*", r"^thread '.*' panicked at .*", r"^error(\[E\d+\])?: .*",
                    r"^test \S+ \.\.\. FAILED"):
        found = re.search(pattern, output, re.M)
        if found:
            line = found.group(0)
            if "panicked at" in line:
                # The panic's message is on the next line.
                after = output[found.end():].lstrip("\n").split("\n", 1)[0]
                line = f"{line} {after}"
            return line[:300]
    missing = [e for e in expected if e not in output]
    if missing:
        return f"not printed: {missing[0]}"
    return output.strip().splitlines()[-1][:300] if output.strip() else "no output"


def run_checks(mod_id, entry, jar, work, report):
    """A mod's checks without the game, its jar alone; one result."""
    result = {"passed": True, "crashes": [], "known": [], "name_only": [], "seconds": 0, "error": None, "logs": []}
    for n, check in enumerate(entry["checks"]):
        env = {}
        if "folder" in check:
            folder = work / mod_id / f"jars-{n}"
            shutil.rmtree(folder, ignore_errors=True)
            folder.mkdir(parents=True)
            shutil.copy2(jar, folder / jar.name)
            env[check["folder"]] = str(folder)
        if "path" in check:
            env[check["path"]] = str(jar)
        log = report / f"{mod_id}.{check['test']}.log"
        output, java_logs, status, seconds = cargo_test(check["test"], env, log)
        result["seconds"] += seconds
        result["logs"].append(log.name)
        expected = check.get("expect", [])
        ok = status == 0 and all(e in output for e in expected)
        result["crashes"] += crashes(output + java_logs)
        result["name_only"] += NAME_ONLY.findall(output)
        if not ok and result["error"] is None:
            result["error"] = first_error(output, expected)
        result["passed"] &= ok
    result["name_only"] = sorted(set(result["name_only"]))
    result["crashes"], result["known"], result["known_gone"] = known_split(list(dict.fromkeys(result["crashes"])),
                                                                         entry.get("known", []))
    return result


def run_together(mods, jars, work, report):
    """Every working mod's jar in one game (kiln/tests/coverage.rs), as a player has them: every one starts, and
    every check of coverage.rs runs again. One result."""
    folder = work / "together" / "jars"
    shutil.rmtree(folder, ignore_errors=True)
    folder.mkdir(parents=True)
    expected, known = ["check start: passed"], []
    for v, entry in mods:
        if entry is None or v["id"] not in jars:
            continue
        shutil.copy2(jars[v["id"]], folder / jars[v["id"]].name)
        known += entry.get("known", [])
        for check in entry["checks"]:
            if check["test"] == "coverage":
                expected += [e for e in check.get("expect", []) if e not in expected]
    log = report / "together.coverage.log"
    output, java_logs, status, seconds = cargo_test("coverage", {"KILN_COVERAGE": str(folder)}, log)
    ok = status == 0 and all(e in output for e in expected)
    failed = re.findall(r"^check (\S+): FAILED", output, re.M)
    found, seen, _ = known_split(crashes(output + java_logs), known)
    return {"passed": ok, "crashes": found, "known": seen, "seconds": seconds,
            "error": None if ok else first_error(output, expected), "failed": failed, "logs": [log.name],
            "mods": sorted(j.name for j in folder.iterdir())}


def once_more(report, run, *args):
    """A run that failed, once more: on a computer busy with other work Kiln's Java can answer late (Kiln waits a
    quarter of a second for an answer), and a check that failed on that passes the second time. The second run's
    result counts; `first` keeps the first run's error and logs (renamed *.first.log), and the report shows it."""
    result = run(*args)
    if result["passed"] and not result["crashes"]:
        return result
    logs = []
    for name in result["logs"]:
        first = name[:-len(".log")] + ".first.log"
        (report / name).rename(report / first)
        logs.append(first)
    error = result["error"] or (result["crashes"] or [""])[0]
    print(f"    failed ({error[:120]}): once more", flush=True)
    again = run(*args)
    again["first"] = {"error": error, "logs": logs}
    again["logs"] = logs + again["logs"]
    return again


def json_objects(text):
    """The JSON objects proof.py prints (its results, then its verdict)."""
    decoder, found, i = json.JSONDecoder(), [], 0
    while True:
        i = text.find("\n{", i)
        if i < 0:
            return found
        try:
            value, end = decoder.raw_decode(text, i + 1)
            found.append(value)
            i = end
        except json.JSONDecodeError:
            i += 2


def run_proof(mod_id, entry, jar, args, report):
    """The mod in the game (kiln/proof.py): one result, its screenshots copied beside the report."""
    proof = entry.get("proof")
    if not proof:
        return {"passed": None, "error": "no proof in catalogue/checks.json"}
    env = dict(proof.get("env", {}))
    if proof.get("with_jar"):
        env["KILN_PROOF_MODS"] = str(jar)
    if proof.get("content"):
        content = imported(mod_id, jar, args)
        if not content:
            return {"passed": None, "error": "not run: needs Horizon's import of its jar, and horizon-port-pack isn't "
                    f"built yet (another mod's proof builds it with the game) or failed; or give --content {mod_id}=FOLDER"}
        env["KILN_PROOF_CONTENT"] = str(content)
    if proof.get("config"):
        config = report / f"{mod_id}.config"
        config.mkdir(exist_ok=True)
        for name, value in proof["config"].items():
            (config / name).write_text(json.dumps(value, indent=2) + "\n")
        env["KILN_PROOF_CONFIG"] = str(config)
    mods_dir = Path(args.horizon_mods).resolve()
    shots = mods_dir / "native" / "target" / "proof" / "kiln"
    command = [sys.executable, "native/tools/native.py", "proof", "--resources", str(Path(args.proof).resolve()), "kiln"]
    # Only the proof's own settings: none left over from the shell.
    clean = {k: v for k, v in os.environ.items() if not k.startswith("KILN_PROOF_")}
    started, logs, retried = time.time(), [], None
    # A failed proof runs once more: on a computer busy with other work the game answers late (horizon-ctl gives up on
    # a command), and the proof's own blocks, timed in seconds, tick fewer times than they should. The report says so,
    # with the first run's error.
    loads = []
    for run in (1, 2):
        loads.append(calm())
        shutil.rmtree(shots, ignore_errors=True)
        try:
            done = subprocess.run(command, cwd=mods_dir, env={**clean, **env}, stdout=subprocess.PIPE,
                                  stderr=subprocess.STDOUT, text=True, errors="replace", timeout=PROOF_TIMEOUT)
            output, status = done.stdout, done.returncode
        except subprocess.TimeoutExpired as e:
            output, status = (e.stdout or b"").decode("utf-8", "replace") + "\n(timed out)", -1
        log = report / (f"{mod_id}.proof.log" if run == 1 else f"{mod_id}.proof-again.log")
        log.write_text(f"$ {' '.join(f'{k}={v}' for k, v in env.items())} {' '.join(command)}\n{output}")
        logs.append(log.name)
        objects = json_objects(output)
        verdict = next((o for o in reversed(objects) if o.get("proof") == "kiln"), {})
        results = next((o for o in objects if o.get("proof") != "kiln"), {})
        passed = status == 0 and verdict.get("ok") is True
        if passed or run == 2:
            break
        retried = proof_error(output)
        print(f"    the proof failed ({retried[:120]}): once more", flush=True)
    pictures = report / mod_id
    shutil.rmtree(pictures, ignore_errors=True)
    if shots.is_dir():
        shutil.copytree(shots, pictures)
    error = None if passed else proof_error(output)
    return {"passed": passed, "error": error, "retried": retried, "load": loads, "seconds": round(time.time() - started, 1),
            "crashes": list(results.get("failed_in_java") or []) + crashes(output),
            "screenshots": sorted(str(p.relative_to(report)) for p in pictures.glob("*.png")),
            "visual": visual(results), "logs": logs}


def calm(limit=CALM_WAIT):
    """Waits, up to `limit` seconds, until the computer's load (the last minute's) is under its cores: the proof plays
    the game in real time, and on a computer busy with other work its timed steps go wrong. The load it starts at."""
    cores = os.cpu_count() or 1
    until = time.time() + limit
    load = os.getloadavg()[0]
    if load >= cores:
        print(f"    waiting for the computer to calm down (load {load:.0f} on {cores} cores)...", flush=True)
    while load >= cores and time.time() < until:
        time.sleep(30)
        load = os.getloadavg()[0]
    return round(load, 1)


def imported(mod_id, jar, args):
    """What Horizon's import makes of the jar's data (its carry/ folder), as a player's mod browser installs it: the
    folder --content gave, or horizon-port-pack's (built with the game by the first proof), made once per jar."""
    if mod_id in args.content:
        return Path(args.content[mod_id]).resolve()
    tools = glob.glob(str(Path(args.horizon_mods).resolve() / "native/target/horizon/*/target/release/horizon-port-pack"))
    if not tools:
        return None
    tool = Path(max(tools, key=os.path.getmtime))
    out = args.cache / "content" / tool.parents[2].name[:12] / jar.parent.name
    if not (out / "carry").is_dir():
        shutil.rmtree(out, ignore_errors=True)
        out.parent.mkdir(parents=True, exist_ok=True)
        done = subprocess.run([str(tool), "check", str(jar), str(out)], capture_output=True, text=True,
                              errors="replace")
        if done.returncode != 0 or not (out / "carry").is_dir():
            print(f"    horizon-port-pack check {jar.name}: {(done.stderr or done.stdout).strip()[-300:]}", flush=True)
            return None
    return (out / "carry").resolve()


def default_resources():
    """The prepared resources of the newest game the launcher installed."""
    if platform.system() == "Darwin":
        base = Path.home() / "Library" / "Application Support" / "horizon-launcher" / "versions"
    else:
        base = Path(os.environ.get("XDG_DATA_HOME") or Path.home() / ".local" / "share") / "horizon-launcher" / "versions"
    found = list(base.glob("*/.local/horizon-client/26.3"))
    if not found:
        sys.exit(f"no prepared resources under {base}: start the game once with the launcher, or give --proof PATH")
    return str(max(found, key=lambda p: p.stat().st_mtime))


def proof_error(output):
    """Why a proof failed: the assert of proof.py that failed (its source line, as the traceback shows it), or the
    last error the output names."""
    lines = [l for l in output.splitlines() if l.strip()]
    asserts = [l.strip() for l in lines if re.match(r"\s+assert ", l)]
    last = next((l for l in reversed(lines) if re.match(r"\w+(Error|Exception)\b", l)), None)
    if asserts and last and last.startswith("AssertionError"):
        return asserts[-1][:300]
    return (last or (lines[-1] if lines else "no output"))[:300]


def visual(results):
    """The numbers proof.py read off its screenshots (how much a part of the screen changed) and counted in the world
    (a structure's blocks, what it holds), by name."""
    found = {}

    def walk(value, path):
        if isinstance(value, dict):
            for k, v in value.items():
                walk(v, f"{path}.{k}" if path else k)
        elif isinstance(value, (int, float)) and not isinstance(value, bool) and \
                re.search(r"changed|hidden_by_key|drawn|blocks_of_its_pieces|\.holds\.", path):
            found[path] = value

    walk(results, "")
    return found


def table(rows):
    widths = [max(len(str(r[i])) for r in rows) for i in range(len(rows[0]))]
    return "\n".join("  ".join(str(c).ljust(w) for c, w in zip(r, widths)).rstrip() for r in rows)


def word(passed):
    return {True: "pass", False: "FAIL", None: "-"}[passed]


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--only", help="mod ids (catalogue/verified.json), comma-separated")
    parser.add_argument("--cache", type=Path, default=default_cache(),
                        help="downloads and reports (default: %(default)s; KILN_CHECK_CACHE)")
    parser.add_argument("--report", type=Path, help="where the report goes (default: CACHE/reports/<time>)")
    parser.add_argument("--no-together", action="store_true", help="skip the run with every mod together")
    parser.add_argument("--proof", metavar="RESOURCES", nargs="?", const="",
                        help="also run kiln/proof.py with each mod, offscreen; RESOURCES: the game's prepared resources "
                        "(.../.local/horizon-client/26.3; default: the newest game the launcher installed)")
    parser.add_argument("--horizon-mods", default=str(ROOT / ".deps" / "horizon-mods"),
                        help="the horizon-mods clone whose native/kiln is this repository's kiln (default: %(default)s)")
    parser.add_argument("--content", action="append", default=[], metavar="ID=FOLDER",
                        help="Horizon's import of a mod's data (its carry/ folder), for proofs that need it")
    args = parser.parse_args()
    args.content = dict(c.split("=", 1) for c in args.content)
    if args.proof == "":
        args.proof = default_resources()

    runtime = ROOT / "target" / "kiln" / "kiln-runtime.jar"
    if not runtime.is_file():
        sys.exit("no target/kiln/kiln-runtime.jar: run sh tools/build-java.sh first")
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%d-%H%M%S")
    report = (args.report or args.cache / "reports" / stamp).resolve()
    report.mkdir(parents=True, exist_ok=True)
    # A folder of its own for each run: two runs at once (from two clones, say) don't share one.
    args.cache.mkdir(parents=True, exist_ok=True)
    work = Path(tempfile.mkdtemp(prefix="work-", dir=args.cache))
    commit = subprocess.run(["git", "rev-parse", "--short", "HEAD"], cwd=ROOT, capture_output=True,
                            text=True).stdout.strip()

    mods, problems = read_lists()
    if args.only:
        wanted = set(args.only.split(","))
        unknown = wanted - {v["id"] for v, _ in mods}
        if unknown:
            sys.exit(f"not marked working in catalogue/verified.json: {', '.join(sorted(unknown))}")
        mods = [(v, e) for v, e in mods if v["id"] in wanted]
    print(f"Checking {len(mods)} mods at {commit}; report in {report}", flush=True)
    if args.proof:
        print(f"In the game too, offscreen, with {args.proof}", flush=True)
    built = subprocess.run(["cargo", "test", "-p", "horizon-kiln-mod", "--tests", "--no-run"], cwd=ROOT,
                           stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, errors="replace")
    if built.returncode != 0:
        (report / "build.log").write_text(built.stdout)
        sys.exit(f"Kiln's tests don't build (see {report / 'build.log'})")

    results, jars = {}, {}
    entries = {v["id"]: e for v, e in mods}
    for v, entry in mods:
        mod_id = v["id"]
        if entry is None:
            results[mod_id] = {"status": v["status"], "game": {"passed": False, "error": "no checks"}}
            continue
        try:
            jars[mod_id] = fetch(entry["jar"], args.cache)
        except (OSError, RuntimeError) as e:
            results[mod_id] = {"status": v["status"], "game": {"passed": False, "error": f"jar: {e}"}}
            print(f"  {mod_id}: {e}", flush=True)
            continue
        print(f"  {mod_id} without the game ({jars[mod_id].name})...", flush=True)
        results[mod_id] = {"status": v["status"], "feature": entry.get("feature"), "jar": jars[mod_id].name,
                           "game": once_more(report, run_checks, mod_id, entry, jars[mod_id], work, report)}
    together = None
    if not args.no_together and len(jars) > 1:
        print("  every mod together...", flush=True)
        together = once_more(report, run_together, mods, jars, work, report)
    if args.proof:
        for v, entry in mods:
            if entry and v["id"] in jars:
                print(f"  {v['id']} in the game...", flush=True)
                results[v["id"]]["proof"] = run_proof(v["id"], entry, jars[v["id"]], args, report)
    shutil.rmtree(work, ignore_errors=True)

    # A mod passes when its checks pass with no crash, and, when the game ran, its proof passed too.
    for r in results.values():
        game, proof = r["game"], r.get("proof", {})
        r["passed"] = (bool(game.get("passed")) and not game.get("crashes") and not proof.get("crashes")
                       and proof.get("passed") is not False)
    rows = [("mod", "without the game", "crashes", "in the game", "result", "first error")]
    for mod_id, r in results.items():
        game, proof = r["game"], r.get("proof", {})
        crashed = game.get("crashes", []) + proof.get("crashes", [])
        error = game.get("error") or (crashed or [None])[0] or proof.get("error") or ""
        rows.append((mod_id, word(game.get("passed")), len(crashed) or "none",
                     word(proof.get("passed")) if args.proof else "not run", word(r["passed"]), error[:140]))
    if together:
        together["passed_all"] = together["passed"] and not together["crashes"]
        rows.append(("(all together)", word(together["passed"]), len(together["crashes"]) or "none", "",
                     word(together["passed_all"]),
                     (together["error"] or (together["crashes"] or [""])[0])[:140]))
    ok = not problems and all(r["passed"] for r in results.values()) and (together is None or together["passed_all"])

    summary = [f"Kiln's working mods, checked at {commit} ({stamp} UTC): {'every one passed' if ok else 'FAILED'}", "",
               table(rows)]
    if problems:
        summary += ["", "The lists:"] + [f"  {p}" for p in problems]
    for mod_id, r in results.items():
        proof = r.get("proof") or {}
        if proof.get("visual") or proof.get("screenshots"):
            summary += ["", f"{mod_id} in the game: " + ", ".join(f"{k} {v}" for k, v in proof.get("visual", {}).items()),
                        "  screenshots: " + " ".join(proof.get("screenshots", []))]
        if r["game"].get("first"):
            summary += [f"{mod_id}: its checks failed the first time ({r['game']['first']['error'][:160]}); the second "
                        f"time they {'passed: unstable on a busy computer' if r['game'].get('passed') else 'failed too'}"]
        if proof.get("retried"):
            summary += [f"{mod_id}: its proof failed the first time ({proof['retried'][:160]}); the second time it "
                        f"{'passed: unstable on a busy computer' if proof.get('passed') else 'failed too'} "
                        f"(load at the start: {', '.join(map(str, proof.get('load', [])))} on {os.cpu_count()} cores)"]
        for line in r["game"].get("known") or []:
            why = next((k["why"] for k in (entries.get(mod_id) or {}).get("known", []) if re.search(k["says"], line)), "")
            summary += [f"{mod_id}: known, not failed ({why}): {line[:200]}"]
        for says in r["game"].get("known_gone") or []:
            summary += [f"{mod_id}: the known problem /{says}/ wasn't seen: fixed? Take it out of catalogue/checks.json"]
        names = r["game"].get("name_only") or []
        if names:
            summary += [f"{mod_id}: {len(names)} calls reached a stand-in Kiln has only the name of (they did "
                        f"nothing): {', '.join(names[:5])}{', ...' if len(names) > 5 else ''}"]
    if together and together.get("first"):
        summary += [f"(all together): failed the first time ({together['first']['error'][:160]}); the second time "
                    f"{'passed: unstable on a busy computer' if together['passed_all'] else 'failed too'}"]
    summary += ["", f"Logs: {report}"]
    text = "\n".join(summary)
    print("\n" + text)
    (report / "report.txt").write_text(text + "\n")
    (report / "report.json").write_text(json.dumps({"commit": commit, "time": stamp, "ok": ok, "problems": problems,
                                                    "mods": results, "together": together}, indent=2) + "\n")
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as f:
            f.write("```\n" + text + "\n```\n")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
