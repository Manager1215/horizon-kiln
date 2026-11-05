#!/usr/bin/env python3
"""Writes coverage.json: the classes and members Kiln's stand-ins declare.

    python3 catalogue/coverage.py --jar target/kiln/kiln-runtime.jar --out catalogue/coverage.json

The jar is Kiln's Java side (tools/build-java.sh builds it). Only classes in the
packages listed under "scope" in rules.json count.
"""

import argparse
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from classfile import Coverage  # noqa: E402


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--jar", required=True, nargs="+", help="kiln-runtime.jar, then the library jars Kiln puts beside it")
    parser.add_argument("--rules", default=os.path.join(os.path.dirname(__file__), "rules.json"))
    parser.add_argument("--out", required=True)
    parser.add_argument("--kiln", default="", help="Kiln version these stand-ins belong to")
    args = parser.parse_args()

    with open(args.rules, encoding="utf-8") as f:
        rules = json.load(f)
    coverage = Coverage.from_jar(args.jar[0], rules["scope"])
    for extra in args.jar[1:]:
        for name, info in Coverage.from_jar(extra, rules["scope"]).classes.items():
            coverage.classes.setdefault(name, info)
    if not coverage.classes:
        print("no classes in scope found in", " ".join(args.jar), file=sys.stderr)
        return 1
    data = coverage.to_dict()
    data["kiln"] = args.kiln
    members = sum(len(c["members"]) for c in data["classes"].values())
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=1, sort_keys=False)
        f.write("\n")
    print("coverage: %d classes, %d members -> %s" % (len(data["classes"]), members, args.out))
    return 0


if __name__ == "__main__":
    sys.exit(main())
