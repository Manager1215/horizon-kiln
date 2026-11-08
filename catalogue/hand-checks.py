#!/usr/bin/env python3
"""The hand checks of catalogue/verified.json as the site lays them over its two layers (D-083).

    python3 -I catalogue/hand-checks.py --check                     # is every check honest about its Kiln?
    python3 -I catalogue/hand-checks.py --out hand-checks.json      # what the site reads

The site's release layer is scanned in the public repository from a release's own copy of verified.json, so a mod
tried in the game after a release would wait for the next one. This file travels on its own instead: every push to
main that changes verified.json sends it to the site (.github/workflows/hand-checks.yml), and the site's
build-data.py puts each entry over the layer it belongs to:

- `checked.kiln` a released version (`0.3.0`): the release layer, and the development layer too. Only honest if
  the Kiln that ran is the release's: when `checked.commit` is given, the files a player gets (RUNTIME below) must be
  the same at that commit as at the tag. From D-083's day on (2026-11-05) a check carries its commit.
- `checked.kiln` `main`: the development layer only, with `commit` (pushed to this repository) and `date`. At a
  release, the entries its build was checked with become that version, with the release's commit.
- no `checked`: a mod Kiln is being built with (`in-development`), in both layers.

The site is the one list of which mods work: the repository keeps none (D-086).
Exit status 1 when a check doesn't hold (with --check, or before writing --out).
"""

import argparse
import datetime
import importlib.util
import json
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)

# What a player of a release runs: Kiln's native mod, its Java side and libraries.
# kiln/proof.py and the tests are left out: a check run with a newer proof on the release's Kiln is still the release's.
RUNTIME = ["kiln/src", "kiln/java", "kiln/java-gen", "kiln/lib", "kiln/mixins", "kiln/Cargo.toml",
           "kiln/compatibility.json", "Cargo.lock"]
RELEASE = re.compile(r"^\d+\.\d+\.\d+(-(alpha|beta|rc)[\w.]*)?$")
DATE = re.compile(r"^\d{4}-\d{2}-\d{2}$")
STATUSES = ("works", "doesnt-work", "in-development")
COMMIT_REQUIRED_FROM = "2026-11-05"  # D-083
KEYS = ("name", "version", "status", "works", "note", "url", "checked")


def load(path, name):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    sys.path.insert(0, os.path.dirname(path))
    try:
        spec.loader.exec_module(module)
    finally:
        sys.path.pop(0)
    return module


def git(repo, *args):
    return subprocess.run(["git", "-C", repo, *args], capture_output=True, text=True)


def releases(repo):
    """Released versions, from the tags (v0.3.0 -> 0.3.0)."""
    out = git(repo, "tag", "--list", "v*").stdout.split()
    return {t[1:] for t in out if RELEASE.match(t[1:])}


def latest(versions):
    def key(v):
        base, _, pre = v.partition("-")
        return tuple(int(x) for x in base.split(".")), pre == "", pre
    final = [v for v in versions if "-" not in v]
    return max(final or versions, key=key) if versions else ""


def commit_of(repo, rev):
    result = git(repo, "rev-parse", "--verify", "--quiet", rev + "^{commit}")
    return result.stdout.strip() if result.returncode == 0 else ""


def same_runtime(repo, a, b):
    """The paths in RUNTIME the same at both commits; the differing files otherwise."""
    result = git(repo, "diff", "--name-only", a, b, "--", *RUNTIME)
    if result.returncode != 0:
        return ["(git diff failed: %s)" % result.stderr.strip()]
    return [line for line in result.stdout.splitlines() if line]


def layer_of(hand, repo, released, problems):
    """'release', 'dev' or 'both', with the problems of the entry's check appended."""
    name = hand.get("id", "?")
    checked = hand.get("checked")
    if not checked:
        if hand.get("status") != "in-development":
            problems.append("%s: status %s with no `checked`: say which Kiln it was tried with" % (name, hand.get("status")))
        return "both"
    kiln = str(checked.get("kiln") or "")
    date = str(checked.get("date") or "")
    commit = str(checked.get("commit") or "")
    if date and not DATE.match(date):
        problems.append("%s: checked.date %r is not YYYY-MM-DD" % (name, date))
    full = commit_of(repo, commit) if commit else ""
    if commit and not full:
        problems.append("%s: checked.commit %s is not a commit of this repository (pushed?)" % (name, commit))
    if RELEASE.match(kiln):
        if kiln not in released:
            problems.append("%s: checked.kiln %s, but there is no tag v%s" % (name, kiln, kiln))
        elif full:
            differ = same_runtime(repo, "v" + kiln, full)
            if differ:
                problems.append("%s: checked.kiln %s, but Kiln at %s is not the release's (%s differ%s); write \"main\""
                                % (name, kiln, commit, ", ".join(differ[:3]), " and more" if len(differ) > 3 else ""))
        elif date >= COMMIT_REQUIRED_FROM:
            problems.append("%s: a check from %s on needs checked.commit, the commit Kiln was built from" % (name, COMMIT_REQUIRED_FROM))
        return "release"
    if kiln == "main":
        if not commit or not date:
            problems.append("%s: checked.kiln main needs checked.commit and checked.date" % name)
        return "dev"
    problems.append("%s: checked.kiln %r is neither a released version nor \"main\"" % (name, kiln))
    return "dev"


def build(verified, repo):
    scan = load(os.path.join(HERE, "scan.py"), "kiln_scan")
    released = releases(repo)
    problems = []
    seen = set()
    mods = []
    for hand in verified.get("mods", []):
        mod_id = scan.safe_id(str(hand.get("id") or ""))
        if not hand.get("id") or mod_id != hand["id"]:
            problems.append("%r: id must be lowercase letters, digits and dashes" % hand.get("id"))
        if mod_id in seen:
            problems.append("%s: twice in verified.json" % mod_id)
        seen.add(mod_id)
        if hand.get("status") not in STATUSES:
            problems.append("%s: status %r is not one of %s" % (mod_id, hand.get("status"), ", ".join(STATUSES)))
        if not hand.get("name"):
            problems.append("%s: no name" % mod_id)
        layer = layer_of(hand, repo, released, problems)
        entry = {"id": mod_id, "layer": layer}
        if hand.get("modrinth"):
            entry["modrinth"] = hand["modrinth"]
            entry["scanId"] = scan.safe_id(hand["modrinth"])
        for key in KEYS:
            if hand.get(key):
                entry[key] = hand[key]
        entry["statusLabel"] = scan.LABELS.get(hand.get("status"), hand.get("status"))
        entry["statusMeaning"] = scan.MEANINGS.get(hand.get("status"), "")
        checked = hand.get("checked") or {}
        if layer == "dev" and checked.get("commit"):
            entry["build"] = "development build %s (%s)" % (checked.get("date", ""), str(checked["commit"])[:7])
        mods.append(entry)
    head = commit_of(repo, "HEAD")
    return {
        "schema": 1,
        "updated": datetime.date.today().isoformat(),
        "commit": head[:7],
        "release": latest(released),
        "mods": mods,
    }, problems


def list_problems():
    """check-mods.py's own rule: every mod marked working has its checks (D-076)."""
    try:
        return load(os.path.join(ROOT, "tools", "check-mods.py"), "kiln_check_mods").read_lists()[1]
    except (OSError, KeyError, ValueError) as error:
        return ["tools/check-mods.py could not read the lists: %s" % error]


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--verified", default=os.path.join(HERE, "verified.json"))
    parser.add_argument("--repo", default=ROOT, help="the git checkout the commits and tags are looked up in")
    parser.add_argument("--out", help="write the site's hand-checks.json here")
    parser.add_argument("--check", action="store_true", help="only check; exit status 1 on a problem")
    args = parser.parse_args()

    with open(args.verified, encoding="utf-8") as f:
        verified = json.load(f)
    data, problems = build(verified, args.repo)
    if args.verified == os.path.join(HERE, "verified.json"):
        problems += list_problems()
    for problem in problems:
        print("problem:", problem, file=sys.stderr)
    counts = {}
    for entry in data["mods"]:
        counts[entry["layer"]] = counts.get(entry["layer"], 0) + 1
    print("hand checks: %d (%s); latest release %s" % (
        len(data["mods"]), ", ".join("%s %d" % kv for kv in sorted(counts.items())), data["release"] or "none"))
    if problems:
        return 1
    if args.out and not args.check:
        tmp = args.out + ".tmp"
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
            f.write("\n")
        os.replace(tmp, args.out)
        print("written:", args.out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
