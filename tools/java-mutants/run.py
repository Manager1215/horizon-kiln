#!/usr/bin/env python3
"""Runs the testbench once per Java mutant, several at once:

  python3 run.py <R> <mutants.jsonl> <workers> [all|covered|ids-file]

R is the work folder: R/repo is a built worktree (sh tools/build-java.sh, and the testbench test built), R/w/<i> a
worker's own copy of target/kiln, bound over R/repo/target/kiln in its own mount namespace (unshare), so each run
reads its own kiln-runtime.jar. Results go to R/results-<i>.jsonl, one line a mutant; a rerun skips what's there.
Every BASE-th run of a worker is the unchanged runtime, to see what's flaky under this load.
"""
import glob
import io
import json
import os
import re
import shutil
import subprocess
import sys
import time
import zipfile
from multiprocessing import Process

BASE = 40
TIMEOUT = 120


def report_of(text):
    probes = {}
    for line in text.splitlines()[3:]:
        m = re.match(r"(\w+)\s+(\w+)\s+(\S+)\s?(.*)$", line)
        if m:
            probes[m.group(3)] = (m.group(1), m.group(4).strip())
    return probes


def worker(i, n, R, jobs):
    repo = f"{R}/repo"
    W = f"{R}/w/{i}"
    out = f"{R}/results-{i}.jsonl"
    done = set()
    if os.path.exists(out):
        for l in open(out):
            try:
                done.add(json.loads(l)["id"])
            except Exception:
                pass
    if not os.path.isdir(f"{W}/kiln"):
        os.makedirs(W, exist_ok=True)
        subprocess.run(["cp", "-al", f"{repo}/target/kiln", f"{W}/kiln.part"], check=True)
        os.rename(f"{W}/kiln.part", f"{W}/kiln")
    jar = f"{repo}/target/kiln/kiln-runtime.jar"
    with zipfile.ZipFile(jar) as z:
        entries = [(info, z.read(info)) for info in z.infolist()]
    libs = ":".join(sorted(glob.glob(f"{repo}/target/kiln/lib/*.jar")))
    cp = f"{repo}/target/java/runtime:{libs}"
    test = sorted(p for p in glob.glob(f"{repo}/target/debug/deps/testbench-*") if not p.endswith(".d"))[-1]
    baseline_report = report_of(open(f"{R}/baseline-report.txt").read())
    sources = {}
    env = dict(os.environ, TMPDIR=f"{W}/tmp")
    f = open(out, "a")
    count = 0
    for m in jobs:
        if m["id"] in done:
            continue
        count += 1
        if count % BASE == 1:
            bid = f"base-{i}-{count}"
            if bid not in done:
                write_jar(W, entries, {})
                rec = run_test(W, repo, test, env)
                rec.update(id=bid, base=True)
                rec["red"] = red(rec.pop("report"), baseline_report)
                f.write(json.dumps(rec) + "\n")
                f.flush()
        rel = m["file"]
        if rel not in sources:
            sources[rel] = open(f"{repo}/kiln/java/{rel}", encoding="utf-8").read()
        src = sources[rel]
        mutated = src[:m["start"]] + m["text"] + src[m["end"]:]
        shutil.rmtree(f"{W}/src", ignore_errors=True)
        shutil.rmtree(f"{W}/classes", ignore_errors=True)
        os.makedirs(os.path.dirname(f"{W}/src/{rel}"), exist_ok=True)
        os.makedirs(f"{W}/classes")
        open(f"{W}/src/{rel}", "w", encoding="utf-8").write(mutated)
        c = subprocess.run(["javac", "--release", "21", "-nowarn", "-encoding", "UTF-8", "-implicit:none",
                            "-sourcepath", "", "-cp", cp, "-d", f"{W}/classes", f"{W}/src/{rel}"],
                           capture_output=True, text=True)
        rec = {"id": m["id"]}
        if c.returncode != 0:
            rec["outcome"] = "unviable"
            rec["why"] = c.stdout[-300:] + c.stderr[-300:]
        else:
            patch = {}
            for p in glob.glob(f"{W}/classes/**/*.class", recursive=True):
                patch[os.path.relpath(p, f"{W}/classes")] = open(p, "rb").read()
            write_jar(W, entries, patch)
            r = run_test(W, repo, test, env)
            rec.update(r)
            rec["red"] = red(rec.pop("report"), baseline_report)
        f.write(json.dumps(rec) + "\n")
        f.flush()


def red(report, baseline):
    """The probes not passing, and the ones passing with a detail other than the baseline's."""
    if report is None:
        return None
    reds, changed = {}, {}
    for pid, (status, detail) in report.items():
        if status != "PASS":
            reds[pid] = f"{status} {detail}"[:160]
        elif pid in baseline and baseline[pid][1] != detail:
            changed[pid] = detail[:160]
    return {"red": reds, "changed": changed, "probes": len(report)}


def write_jar(W, entries, patch):
    tmp = f"{W}/kiln/kiln-runtime.jar.tmp"
    seen = set()
    with zipfile.ZipFile(tmp, "w", zipfile.ZIP_STORED) as z:
        for info, data in entries:
            name = info.filename
            if name in patch:
                data = patch[name]
                seen.add(name)
            z.writestr(name, data)
        for name, data in patch.items():
            if name not in seen:
                z.writestr(name, data)
    os.replace(tmp, f"{W}/kiln/kiln-runtime.jar")


def run_test(W, repo, test, env):
    report = f"{W}/kiln/testbench-report.txt"
    if os.path.exists(report):
        os.remove(report)
    shutil.rmtree(f"{W}/tmp", ignore_errors=True)
    os.makedirs(f"{W}/tmp")
    start = time.time()
    script = f'mount --bind "{W}/kiln" "{repo}/target/kiln" && cd "{repo}/kiln" && exec "{test}" --ignored --nocapture --exact the_testbench_measures_kiln'
    try:
        p = subprocess.run(["unshare", "-rm", "sh", "-c", script], capture_output=True, text=True, env=env,
                           timeout=TIMEOUT)
        code, output = p.returncode, p.stdout + p.stderr
    except subprocess.TimeoutExpired as e:
        code, output = "timeout", (e.stdout or b"").decode(errors="replace") if isinstance(e.stdout, bytes) else (e.stdout or "")
        subprocess.run(["pkill", "-9", "-f", f"{W}/tmp/"], capture_output=True)
    took = round(time.time() - start, 1)
    panic, at = "", ""
    mm = re.search(r"panicked at ([^\n]*)\n([^\n]*)", output)
    if mm:
        at, panic = mm.group(1), mm.group(2)[:300]
    text = open(report).read() if os.path.exists(report) else None
    shutil.rmtree(f"{W}/tmp", ignore_errors=True)
    return {"outcome": "ran", "exit": code, "took": took, "panic": panic, "at": at,
            "report": report_of(text) if text is not None else None}


def main():
    R, mutants, n = sys.argv[1], sys.argv[2], int(sys.argv[3])
    which = sys.argv[4] if len(sys.argv) > 4 else "covered"
    ms = [json.loads(l) for l in open(mutants)]
    if which == "covered":
        ms = [m for m in ms if m["covered"]]
    elif which == "uncovered":
        ms = [m for m in ms if not m["covered"]]
    elif which != "all":
        ids = {int(x) for x in open(which).read().split()}
        ms = [m for m in ms if m["id"] in ids]
    procs = [Process(target=worker, args=(i, n, R, ms[i::n])) for i in range(n)]
    for p in procs:
        p.start()
    for p in procs:
        p.join()
    print("done")


if __name__ == "__main__":
    main()
