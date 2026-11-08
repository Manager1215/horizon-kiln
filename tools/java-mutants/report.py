#!/usr/bin/env python3
"""Sums up a mutation pass, as Markdown:

  python3 report.py <work folder> [the testbench's report unbroken] [kiln/tests/testbench.rs] > report.md

Per probe, the breaks it caught (red in the testbench's report, or failing the Rust test's own check of what that
probe did); per stand-in class, what its breaks came to; the covered breaks nothing caught, one a line.
"""
import collections
import glob
import json
import re
import sys

R = sys.argv[1]
ms = {}
for l in open(f"{R}/mutants.jsonl"):
    m = json.loads(l)
    ms[m["id"]] = m
res = {}
base = []
for p in glob.glob(f"{R}/results-*.jsonl"):
    for l in open(p):
        r = json.loads(l)
        if r.get("base"):
            base.append(r)
        else:
            res[r["id"]] = r

probes = []
for line in open(sys.argv[2] if len(sys.argv) > 2 else f"{R}/baseline-report.txt").read().splitlines()[3:]:
    mm = re.match(r"(\w+)\s+(\w+)\s+(\S+)\s?(.*)$", line)
    if mm:
        probes.append((mm.group(3), mm.group(2), mm.group(4).strip()))

flaky = collections.Counter()
volatile = collections.Counter()
for r in base:
    if r["red"] is None:
        flaky["<no report>"] += 1
        continue
    for p in r["red"]["red"]:
        flaky[p] += 1
    for p in r["red"]["changed"]:
        volatile[p] += 1

# The Rust test's own checks of what probes did, by the line its assert starts on (where it panics): found by what
# each checks, in the test's source.
MARKERS = {'client_command("kiln_testbench")': ["event.clientCommands"],
           "client_commands().contains": ["event.clientCommands"],
           '"help help".to_owned())': ["client.sendCommand"],
           "ui.button.click": ["client.sound"],
           "experience_orb.pickup": ["level.localSound"],
           "happy_villager": ["level.particle"],
           "toast probe": ["client.toast"],
           '== "Kiln Testbench").count()': ["client.overlayMessage", "player.overlayMessage"],
           "not in the chat": ["client.chat", "player.systemMessage"]}
HARNESS = {}
if len(sys.argv) > 3:
    lines = open(sys.argv[3]).read().splitlines()
    for marker, ids in MARKERS.items():
        for n, line in enumerate(lines):
            if marker in line:
                start = n
                while start > 0 and "assert" not in lines[start]:
                    start -= 1
                HARNESS[start + 1] = ids


def harness_probes(r):
    """The probes the Rust test's failed check is about, when a mutant failed it with every probe passing."""
    at = r.get("at", "")
    mm = re.search(r"testbench\.rs:(\d+)", at)
    if not mm:
        return []
    ids = HARNESS.get(int(mm.group(1)), [])
    if ids == ["client.chat", "player.systemMessage"]:
        return ["client.chat"] if "chat probe" in r.get("panic", "") else ["player.systemMessage"]
    return ids


caught = collections.Counter()      # probe -> mutants it turned red
alone = collections.Counter()       # ... where at most 3 probes turned red
seen = collections.Counter()        # probe -> mutants that changed its detail but left it passing
examples = collections.defaultdict(list)
seen_ex = collections.defaultdict(list)
per_class = collections.defaultdict(collections.Counter)
survivors = collections.defaultdict(list)
for mid, m in ms.items():
    cls = m["file"][:-5]
    r = res.get(mid)
    per_class[cls]["mutants"] += 1
    if r is None:
        # Not run: a line the testbench never reaches (by coverage) can't be caught by it.
        per_class[cls]["survived, not run by the testbench" if not m["covered"] else "not run"] += 1
        continue
    if r["outcome"] == "unviable":
        per_class[cls]["unviable"] += 1
        continue
    rr = r["red"]
    reds = [p for p in (rr["red"] if rr else {}) if flaky[p] == 0]
    if rr is None or r["exit"] == "timeout":
        per_class[cls]["no report (crash/hang)"] += 1
        continue
    if reds:
        per_class[cls]["caught by a probe"] += 1
        for p in reds:
            caught[p] += 1
            if len(reds) <= 3:
                alone[p] += 1
            if len(examples[p]) < 3:
                examples[p].append(f"{m['file']}:{m['line']} {m['what']}")
    elif r["exit"] != 0:
        per_class[cls]["caught by the Rust test only"] += 1
        for p in harness_probes(r):
            caught[p] += 1
            alone[p] += 1
            if len(examples[p]) < 3:
                examples[p].append(f"{m['file']}:{m['line']} {m['what']} (the Rust test's check)")
    else:
        per_class[cls]["survived, covered" if m["covered"] else "survived, not run by the testbench"] += 1
        survivors[cls].append(m)
        for p, d in rr["changed"].items():
            if volatile[p] == 0:
                seen[p] += 1
                if len(seen_ex[p]) < 3:
                    seen_ex[p].append(f"{m['file']}:{m['line']} {m['what']} -> `{d[:60]}`")

print(f"# Java mutation pass: {len(res)} of {len(ms)} mutants run, {len(base)} baseline runs\n")
print("Baseline runs with red probes:", dict(flaky) or "none", "; probes whose detail varies run to run:",
      sorted(volatile) or "none", "\n")
print("## Per probe\n")
print("| probe | phase | baseline detail | caught | caught with <=3 others | survivors it saw (detail changed) |")
print("|---|---|---|---:|---:|---:|")
for pid, phase, detail in sorted(probes, key=lambda p: (caught[p[0]], p[0])):
    print(f"| {pid} | {phase} | `{detail[:40]}` | {caught[pid]} | {alone[pid]} | {seen[pid]} |")
print("\n## Probes that caught nothing\n")
for pid, phase, detail in probes:
    if caught[pid] == 0:
        pair = caught.get(pid + ".fired", 0)
        note = f" (its `.fired` probe caught {pair})" if pid + ".fired" in dict((q[0], 1) for q in probes) else ""
        print(f"- {pid} (`{detail[:50]}`){note}" + ("; saw: " + "; ".join(seen_ex[pid]) if seen_ex[pid] else ""))
print("\n## What each probe caught (up to three)\n")
for pid, phase, detail in probes:
    if examples[pid]:
        print(f"- {pid}: " + "; ".join(examples[pid]))
print("\n## Per class\n")
keys = ["mutants", "unviable", "caught by a probe", "caught by the Rust test only", "no report (crash/hang)",
        "survived, covered", "survived, not run by the testbench"]
print("| class | " + " | ".join(keys) + " |")
print("|---|" + "---:|" * len(keys))
tot = collections.Counter()
for cls in sorted(per_class, key=lambda c: -per_class[c]["survived, covered"]):
    c = per_class[cls]
    tot.update(c)
    print(f"| {cls} | " + " | ".join(str(c[k]) for k in keys) + " |")
print("| **all** | " + " | ".join(str(tot[k]) for k in keys) + " |")
print("\n## Covered survivors\n")
for cls in sorted(survivors):
    cov = [m for m in survivors[cls] if m["covered"]]
    if cov:
        print(f"### {cls}")
        for m in cov:
            print(f"- {m['id']} :{m['line']} {m['op']}: {m['what']}")
