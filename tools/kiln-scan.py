"""What a mod's jar needs from Kiln: every class, method and field it uses from Minecraft, Mojang's libraries and the
Fabric API, checked against Kiln's runtime and the libraries Kiln ships (target/kiln/ from tools/build-java.sh).

    python3 -I tools/kiln-scan.py MOD.jar [MOD.jar ...]
    python3 -I tools/kiln-scan.py --summary MOD.jar ...     one line a mod: what it is missing, by count

It reads class files only and runs nothing from the jars. A member counts as there when Kiln's class or one of its
superclasses or interfaces declares it; members of JDK classes are taken as there.
"""
import json
import struct
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BUILT = ROOT / "target" / "kiln"
# What Kiln has to stand in for or ship; anything else a mod calls is its own or the JDK's.
WATCHED = ("net/minecraft/", "com/mojang/", "net/fabricmc/")


def parse_class(data):
    """A class file's name, superclass, interfaces, declared members, and the members and classes it refers to."""
    if data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("not a class file")
    count = struct.unpack(">H", data[8:10])[0]
    pool = [None] * count
    i, pos = 1, 10
    while i < count:
        tag = data[pos]
        if tag == 1:
            n = struct.unpack(">H", data[pos + 1:pos + 3])[0]
            pool[i] = ("utf8", data[pos + 3:pos + 3 + n].decode("utf-8", "replace"))
            pos += 3 + n
        elif tag in (3, 4):
            pos += 5
        elif tag in (5, 6):
            pos += 9
            i += 1
        elif tag in (7, 8, 16, 19, 20):
            pool[i] = (tag, struct.unpack(">H", data[pos + 1:pos + 3])[0])
            pos += 3
        elif tag in (9, 10, 11, 12, 17, 18):
            pool[i] = (tag,) + struct.unpack(">HH", data[pos + 1:pos + 5])
            pos += 5
        elif tag == 15:
            pos += 4
        else:
            raise ValueError(f"constant pool tag {tag}")
        i += 1

    def utf8(k):
        return pool[k][1]

    def class_name(k):
        return utf8(pool[k][1])

    access, this, sup, n_ifaces = struct.unpack(">HHHH", data[pos:pos + 8])
    pos += 8
    interfaces = [class_name(struct.unpack(">H", data[pos + 2 * j:pos + 2 * j + 2])[0]) for j in range(n_ifaces)]
    pos += 2 * n_ifaces
    declared = set()
    for _ in range(2):  # fields, then methods
        (n,) = struct.unpack(">H", data[pos:pos + 2])
        pos += 2
        for _ in range(n):
            _, name, desc, attrs = struct.unpack(">HHHH", data[pos:pos + 8])
            pos += 8
            declared.add((utf8(name), utf8(desc)))
            for _ in range(attrs):
                pos += 6 + struct.unpack(">I", data[pos + 2:pos + 6])[0]

    classes, members = set(), set()
    for entry in pool:
        if entry is None or entry[0] == "utf8":
            continue
        if entry[0] == 7:
            name = utf8(entry[1])
            classes.add(name.lstrip("[").removeprefix("L").removesuffix(";") if name.startswith("[") else name)
        elif entry[0] in (9, 10, 11):
            owner = class_name(entry[1])
            nat = pool[entry[2]]
            members.add((owner, utf8(nat[1]), utf8(nat[2]), "field" if entry[0] == 9 else "method"))
    for owner, _, desc, _ in members:
        classes.update(types_in(desc))
    return {
        "name": class_name(this),
        "super": class_name(sup) if sup else None,
        "interfaces": interfaces,
        "declared": declared,
        "classes": classes,
        "members": members,
    }


def types_in(descriptor):
    """The class names in a field or method descriptor."""
    found, i = [], 0
    while i < len(descriptor):
        if descriptor[i] == "L":
            end = descriptor.index(";", i)
            found.append(descriptor[i + 1:end])
            i = end
        i += 1
    return found


def classes_in(jar):
    with zipfile.ZipFile(jar) as z:
        for name in z.namelist():
            if name.endswith(".class") and not name.startswith("META-INF/versions/"):
                try:
                    yield parse_class(z.read(name))
                except (ValueError, IndexError, struct.error):
                    continue


def kiln_classes():
    """Every class Kiln's runtime and its libraries define, by name."""
    jars = [BUILT / "kiln-runtime.jar"] + sorted((BUILT / "lib").glob("*.jar"))
    if not jars[0].is_file():
        sys.exit("run sh tools/build-java.sh first")
    return {c["name"]: c for jar in jars for c in classes_in(jar)}


# Methods a Kiln class gets from the JDK classes it extends or implements, by name.
JDK_METHODS = {
    "java/lang/Object": {"equals", "hashCode", "toString", "getClass", "notify", "notifyAll", "wait", "clone",
                         "finalize"},
    "java/lang/Enum": {"name", "ordinal", "compareTo", "getDeclaringClass", "describeConstable", "valueOf", "equals",
                       "hashCode", "toString"},
    "java/lang/Record": {"equals", "hashCode", "toString"},
    "java/lang/Comparable": {"compareTo"},
    "java/lang/Runnable": {"run"},
    "java/lang/Iterable": {"iterator", "forEach", "spliterator"},
}


def has_member(kiln, owner, name, desc, kind, seen=None):
    """Whether Kiln's `owner`, or what it extends or implements, declares the member (JDK parents: JDK_METHODS)."""
    seen = seen if seen is not None else set()
    if owner in seen:
        return False
    seen.add(owner)
    c = kiln.get(owner)
    if c is None:
        return kind == "method" and name in JDK_METHODS.get(owner, ())
    if kind == "field" and any(n == name and d == desc for n, d in c["declared"]):
        return True
    if kind == "method" and (name, desc) in c["declared"]:
        return True
    parents = ([c["super"]] if c["super"] else []) + c["interfaces"]
    return any(has_member(kiln, p, name, desc, kind, seen) for p in parents)


def scan(jar, kiln):
    with zipfile.ZipFile(jar) as z:
        manifest = json.loads(z.read("fabric.mod.json").decode("utf-8", "replace"), strict=False)
    own = list(classes_in(jar))
    own_names = {c["name"] for c in own}
    missing_classes, missing_members, used = set(), set(), set()
    for c in own:
        for name in c["classes"]:
            if name.startswith(WATCHED) and name not in own_names:
                used.add(name)
                if name not in kiln:
                    missing_classes.add(name)
        for owner, name, desc, kind in c["members"]:
            if owner.startswith(WATCHED) and owner not in own_names and owner in kiln:
                if not has_member(kiln, owner, name, desc, kind):
                    missing_members.add(f"{owner.replace('/', '.')}.{name} {desc}")
    return {
        "jar": Path(jar).name,
        "id": manifest.get("id"),
        "entrypoints": {k: len(v) for k, v in (manifest.get("entrypoints") or {}).items()},
        "mixins": bool(manifest.get("mixins")),
        "used": len(used),
        "missing_classes": sorted(c.replace("/", ".") for c in missing_classes),
        "missing_members": sorted(missing_members),
    }


def main(args):
    summary = "--summary" in args
    jars = [a for a in args if a != "--summary"]
    if not jars:
        sys.exit(__doc__)
    kiln = kiln_classes()
    results = [scan(j, kiln) for j in jars]
    if summary:
        for r in sorted(results, key=lambda r: len(r["missing_classes"]) + len(r["missing_members"])):
            print(f"{len(r['missing_classes']):>3} classes {len(r['missing_members']):>3} members missing"
                  f"  of {r['used']:>3} classes used  {r['id']}  entrypoints {r['entrypoints']}"
                  f"{'  MIXIN' if r['mixins'] else ''}")
        return
    for r in results:
        print(f"== {r['jar']} ({r['id']}): uses {r['used']} classes, entrypoints {r['entrypoints']}"
              f"{', declares Mixin' if r['mixins'] else ''}")
        print(f"   missing classes ({len(r['missing_classes'])}):")
        for c in r["missing_classes"]:
            print(f"     {c}")
        print(f"   missing members of classes Kiln has ({len(r['missing_members'])}):")
        for m in r["missing_members"]:
            print(f"     {m}")


if __name__ == "__main__":
    main(sys.argv[1:])
