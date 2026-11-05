"""The support list of a release (docs/MODS.md) from the catalogue scanned against that release (D-078).

    python3 -I catalogue/support-list.py --site SITE --kiln 0.3.0 --out docs/MODS.md

SITE is scan.py's --out folder for the release channel: mods.json and mods/<id>.json. The list has levels, never
added up into one number:

- Works: tried in the game (verified.json), on this release.
- Should work, its code ran: Kiln's test stand ran it on this release's runtime for a scripted minute of play and
  nothing failed, and it reached nothing Kiln has only the name of. Not tried in the game one by one.
- Should work, data only: a mod with no code whose structures Kiln brings into new worlds. Not run, not tried one
  by one.

The rest (planned, no plans yet, not checked) is the catalogue on the site. Reads the files only; writes one.
"""
import argparse
import json
import os

MODRINTH = "https://modrinth.com/mod/"


def cell(text):
    """Text for a table cell: one line, no pipes."""
    return " ".join(str(text or "").replace("|", "/").split())


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--site", required=True, help="scan.py's --out folder (release channel)")
    parser.add_argument("--kiln", required=True, help="the release, 0.3.0")
    parser.add_argument("--out", required=True)
    args = parser.parse_args()
    index = json.load(open(os.path.join(args.site, "mods.json"), encoding="utf-8"))
    rows = index["mods"]

    def page(row):
        path = os.path.join(args.site, "mods", row["id"] + ".json")
        return json.load(open(path, encoding="utf-8")) if os.path.exists(path) else {}

    works = [r for r in rows if r["status"] == "works"]
    ran, data, unrun = [], [], []
    for r in rows:
        if r["status"] != "may-work":
            continue
        if r.get("run") == "passed":
            ran.append(r)
        elif r.get("data") and (page(r).get("evidence") or "").startswith("Data only"):
            data.append(r)
        else:
            unrun.append(r)
    key = lambda r: (r["name"] or r["id"]).lower()
    out = [
        f"# Mods for Kiln {args.kiln}",
        "",
        f"Fabric mods for Minecraft 26.3 on Modrinth, as Kiln {args.kiln} runs them in Horizon, counted on the "
        f"{args.kiln} build on {index.get('updated', '')} by the catalogue (`catalogue/`, D-078). Three levels, "
        "never added up into one number: what was tried in the game, what Kiln's test stand ran, and what is only "
        "data. Everything else is in the catalogue on [horizon-kiln.cc](https://horizon-kiln.cc/mods.html).",
        "",
        f"| Level | Mods |",
        "|---|---|",
        f"| **Works**: tried in the game | {len(works)} |",
        f"| **Should work**, its code ran on Kiln's test stand | {len(ran)} |",
        f"| **Should work**, data only (structures) | {len(data)} |",
        "",
        "## Works",
        "",
        "Tried in Horizon through Kiln, each feature seen working (README, \"Mods known to run\").",
        "",
        "| Mod | Version | What works |",
        "|---|---|---|",
    ]
    for r in sorted(works, key=key):
        url = page(r).get("url") or MODRINTH + r["id"]
        out.append(f"| [{cell(r['name'])}]({url}) | {cell(r.get('version'))} | {cell(r.get('works'))} |")
    out += [
        "",
        "## Should work: its code ran on Kiln's test stand",
        "",
        f"The stand runs a mod's own code on Kiln {args.kiln}'s Java side with a scripted minute of play in place of "
        "the game (the title screen, a new world, an advancement, a key, the Nether and back, a pause, leaving): its "
        "entry points started, 1,200 ticks went by without an error, and it reached nothing Kiln has only the name "
        "of. That is not the same as each of its features working in the game. A feature behind a screen, a key or "
        "an item may never have run: a mod with only a `main` entry point (its items and blocks) is started and then "
        "used by nothing on the stand. \"Kiln lacks\" counts what the mod's code refers to that Kiln doesn't have, on "
        "paths the minute didn't reach. Nobody tried these one by one. Mods with Mixin aren't here: Kiln doesn't "
        "apply Mixin yet.",
        "",
        "| Mod | Version | On the stand | Entry points | Kiln lacks |",
        "|---|---|---|---|---|",
    ]
    for r in sorted(ran, key=key):
        detail = page(r)
        run = detail.get("run") or {}
        seen = "drew on the HUD" if run.get("hud") else "started and ticked"
        kinds = ", ".join(k for k in ("client", "main") if k in (detail.get("entrypoints") or {}))
        lacks = detail.get("lacks") or 0
        out.append(f"| [{cell(r['name'])}]({MODRINTH}{r['id']}) | {cell(r.get('version'))} | {seen} | {kinds} | "
                   f"{lacks or 'nothing'} |")
    out += [
        "",
        "## Should work: data only",
        "",
        "Mods without code whose structures (pieces, pools, sets of vanilla blocks) Kiln hands to Horizon's world "
        "generator, as it does Illager Warship's (README). Not run and not tried one by one; a piece with a block of "
        "the mod's own isn't generated.",
        "",
        "| Mod | Version |",
        "|---|---|",
    ]
    for r in sorted(data, key=key):
        out.append(f"| [{cell(r['name'])}]({MODRINTH}{r['id']}) | {cell(r.get('version'))} |")
    out += [
        "",
        f"Not listed: {len(unrun)} more mods in which the scan finds nothing Kiln lacks but which the stand didn't "
        "run (server-side mods, mods without an entry point it runs); the catalogue shows them.",
        "",
    ]
    with open(args.out, "w", encoding="utf-8") as f:
        f.write("\n".join(out))
    print(json.dumps({"works": len(works), "ran": len(ran), "data": len(data), "not_run": len(unrun)}))


if __name__ == "__main__":
    main()
