"""Where the mods' mixins hook a place inside a method (`@Inject` at INVOKE, FIELD or NEW, `@Redirect`,
`@WrapOperation`, `@ModifyArg`, `@ModifyExpressionValue` and the rest), and whether Kiln's stand-in has it: the
method, and in its body the call, field or `new` the point names.

    python3 -I tools/mixin-points.py OUT.json MOD.jar [MOD.jar ...]

It reads the mods' mixins as `tools/kiln-scan.py --mixins` does, keeping each `@At`'s target, and the stand-ins' code
from `target/kiln/kiln-runtime.jar` (`sh tools/build-java.sh` first) with the JDK's `javap`. Nothing from the jars
runs. OUT.json gets every point and the ones whose call is there.
"""
import collections
import fnmatch
import importlib.util
import json
import re
import subprocess
import sys
from pathlib import Path

if len(sys.argv) < 3:
    sys.exit(__doc__)
repo = str(Path(__file__).resolve().parent.parent)
out, jars = sys.argv[1], sys.argv[2:]
spec = importlib.util.spec_from_file_location("ks", repo + "/tools/kiln-scan.py")
ks = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ks)

WHOLE = {"HEAD", "RETURN", "TAIL", "(the whole method)"}

# Keep every @At element (value and target) next to what kiln-scan records.
plain = ks.mixin_member


def with_ats(m, owner):
    found = plain(m, owner)
    if found is None or found["kind"] not in ks.INJECTORS:
        return found
    for a in m["annotations"]:
        if ks.KINDS.get(a["@"]) == found["kind"]:
            found["ats"] = [{"value": x.get("value"), "target": x.get("target")}
                            for x in ks.as_list(a.get("at")) if isinstance(x, dict)]
            if found["kind"] == "modify-constant":
                found["ats"] = found["ats"] or [{"value": "CONSTANT", "target": None}]
    return found


ks.mixin_member = with_ats
census = ks.census(jars)
stand_ins = ks.kiln_stand_ins()

# The points to look for: every injector member whose @At is inside a method, on a class Kiln stands in for.
points = []
for mod in census["mods"]:
    for x in mod["mixins"]:
        if x.get("unreadable") or x["side"] == "server":
            continue
        for m in x["members"]:
            if m["kind"] not in ks.INJECTORS:
                continue
            for at in m.get("ats", []):
                if at["value"] in WHOLE or at["value"] is None:
                    continue
                for t in x["targets"]:
                    t = t.replace(".", "/")
                    points.append({"mod": mod["id"], "mixin": x["class"], "kind": m["kind"], "at": at["value"],
                                   "at_target": at["target"], "methods": m.get("methods", []), "class": t,
                                   "where": stand_ins[t][0] if t in stand_ins else None})

# The stand-ins' bodies: each method's calls, field uses and `new`s, from javap.
wanted = sorted({p["class"] for p in points if p["where"] in ("hand", "generated")})
jar = repo + "/target/kiln/kiln-runtime.jar"
bodies = {}  # class -> [(name, desc, {("method"|"field"|"new", owner, name, desc)})]
for i in range(0, len(wanted), 80):
    text = subprocess.run(["javap", "-c", "-p", "-s", "-cp", jar] + [c.replace("/", ".") for c in wanted[i:i + 80]],
                          capture_output=True, text=True).stdout
    cls = member = None
    for line in text.splitlines():
        head = re.match(r"^(?:(?:public|private|protected|final|abstract|static) )*(?:class|interface|enum) (\S+)",
                        line)
        if head and not line.startswith(" "):
            cls = head.group(1).split("<")[0].replace(".", "/")
            bodies[cls] = []
            continue
        if cls is None:
            continue
        if re.match(r"^  \S.*;$", line):
            decl = line.strip().rstrip(";")
            if decl == "static {}":
                name = "<clinit>"
            elif "(" in decl:
                before = decl.split("(")[0].split()[-1]
                own = before.replace(".", "/") == cls or before == cls.rsplit("/", 1)[-1]
                name = "<init>" if own else before.rsplit(".", 1)[-1]
            else:
                member = None
                continue
            member = [name, None, set()]
            bodies[cls].append(member)
            continue
        if member is not None and line.strip().startswith("descriptor:") and member[1] is None:
            member[1] = line.split(":", 1)[1].strip()
            continue
        ref = re.search(r"//\s+(Method|InterfaceMethod|Field|class)\s+(.+)$", line)
        if member is not None and ref:
            what, rest = ref.groups()
            if what == "class":
                if re.search(r"\bnew\b", line):
                    member[2].add(("new", rest.strip('"'), None, None))
                continue
            owner_name, desc = rest.rsplit(":", 1)
            owner, name = (owner_name.rsplit(".", 1) if "." in owner_name else (cls, owner_name))
            member[2].add(("field" if what == "Field" else "method", owner, name.strip('"'), desc))


def parse_target(s):
    """An @At target: (owner or None, name or None, desc or None, field?)."""
    if not s:
        return None
    s = s.strip()
    owner = None
    m = re.match(r"^L([^;]+);(.*)$", s)
    if m:
        owner, s = m.group(1), m.group(2)
    elif ";" in s:
        owner, s = s.split(";", 1)
    elif "." in s.split("(")[0] and "/" in s.split("(")[0]:
        owner, s = s.split("(")[0].rsplit(".", 1)[0], s[len(s.split("(")[0].rsplit(".", 1)[0]) + 1:]
    if not s:
        return owner, None, None, False
    if ":" in s and "(" not in s:
        name, desc = s.split(":", 1)
        return owner, name, desc, True
    if "(" in s:
        return owner, s[:s.index("(")], s[s.index("("):], False
    return owner, s, None, False


def methods_named(cls, selectors):
    found = []
    for sel in selectors or ["*"]:
        sel = re.sub(r"^L[^;]+;", "", sel)
        name, desc = (sel[:sel.index("(")], sel[sel.index("("):]) if "(" in sel else (sel, None)
        for b in bodies.get(cls, []):
            if fnmatch.fnmatchcase(b[0], name) and (desc is None or b[1] == desc):
                found.append(b)
    return found


def matches(ref, kind, at, target):
    what, owner, name, desc = ref
    if target is None:
        return False
    t_owner, t_name, t_desc, t_field = target
    if at == "NEW":
        want = (t_owner or t_name or "").strip("L;")
        return what == "new" and owner == want
    if at == "FIELD" or t_field:
        if what != "field":
            return False
    elif at in ("INVOKE", "INVOKE_ASSIGN", "INVOKE_STRING") or kind in ("redirect", "extras:wrap-operation"):
        if what != "method":
            return False
    if t_name is not None and name != t_name:
        return False
    if t_desc is not None and desc != t_desc and not (t_field and desc == t_desc):
        return False
    if t_owner is not None and owner != t_owner:
        return "owner"
    return True


rows = collections.Counter()
by_kind = collections.defaultdict(collections.Counter)
matched = []
for p in points:
    key = p["where"] or "no stand-in"
    rows[key, "points"] += 1
    by_kind[p["kind"] + "@" + str(p["at"])][key] += 1
    if p["where"] not in ("hand", "generated"):
        continue
    if p["at"] == "CONSTANT" or p["at"] in ("LOAD", "STORE", "JUMP") or (p["at"] or "").startswith("MIXINEXTRAS:"):
        rows[key, "not a call, field or new"] += 1
        continue
    ms = methods_named(p["class"], p["methods"])
    if not ms:
        rows[key, "method missing"] += 1
        continue
    rows[key, "method there"] += 1
    target = parse_target(p["at_target"])
    best = False
    for b in ms:
        for ref in b[2]:
            r = matches(ref, p["kind"], p["at"], target)
            if r is True:
                best = True
            elif r == "owner" and best is False:
                best = "owner"
    if best is True:
        rows[key, "call there"] += 1
        by_kind[p["kind"] + "@" + str(p["at"])]["call there"] += 1
        matched.append(p)
    elif best == "owner":
        rows[key, "call there, other owner"] += 1

print(f"{len(points)} mid-method points in {len({(p['mod'], p['mixin']) for p in points})} mixins")
for where in ("hand", "generated", "library", "no stand-in"):
    keys = [k for k in rows if k[0] == where]
    print(where, {k[1]: rows[k] for k in sorted(keys, key=lambda k: k[1])})
print("by kind (points on: hand, generated, no stand-in; call there):")
for k, c in sorted(by_kind.items(), key=lambda kv: -sum(kv[1].values()))[:14]:
    print(f"  {k:44} hand {c['hand']:4} gen {c['generated']:4} none {c['no stand-in']:4} lib {c['library']:3}"
          f" call there {c['call there']:3}")
print("calls there, by target method:")
calls = collections.Counter((p["class"].rsplit("/", 1)[-1], ",".join(p["methods"]), p["at_target"]) for p in matched)
for (cls, meth, tgt), n in calls.most_common(25):
    print(f"  {n:3} {cls}.{meth}  @ {tgt}")
json.dump({"points": points, "matched": matched}, open(out, "w"), indent=1)
