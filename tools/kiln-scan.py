"""What a mod's jar needs from Kiln: every class, method and field it uses from Minecraft, Mojang's libraries and the
Fabric API, checked against Kiln's runtime and the libraries Kiln ships (target/kiln/ from tools/build-java.sh).

    python3 -I tools/kiln-scan.py MOD.jar [MOD.jar ...]
    python3 -I tools/kiln-scan.py --summary MOD.jar ...     one line a mod: what it is missing, by count
    python3 -I tools/kiln-scan.py --mixins MOD.jar ...      the mods' Mixins: each kind counted, their targets, and
                                                            whether Kiln has a stand-in for each (markdown)
    python3 -I tools/kiln-scan.py --mixins --json MOD.jar ...   the same, every mixin, as JSON

It reads class files only and runs nothing from the jars. A member counts as there when Kiln's class or one of its
superclasses or interfaces declares it; members of JDK classes are taken as there.
"""
import collections
import fnmatch
import io
import json
import re
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


# The Mixin census (--mixins): what kinds of Mixin the mods use, on which classes.

MIXIN = "Lorg/spongepowered/asm/mixin/"
EXTRAS = "Lcom/llamalad7/mixinextras/"
# A member annotation's kind, by its type. Injectors keep their @At too.
KINDS = {
    MIXIN + "gen/Accessor;": "accessor",
    MIXIN + "gen/Invoker;": "invoker",
    MIXIN + "injection/Inject;": "inject",
    MIXIN + "injection/Redirect;": "redirect",
    MIXIN + "injection/ModifyArg;": "modify-arg",
    MIXIN + "injection/ModifyArgs;": "modify-args",
    MIXIN + "injection/ModifyVariable;": "modify-variable",
    MIXIN + "injection/ModifyConstant;": "modify-constant",
    MIXIN + "Overwrite;": "overwrite",
    MIXIN + "Shadow;": "shadow",
    MIXIN + "Unique;": "unique",
    MIXIN + "Intrinsic;": "intrinsic",
    EXTRAS + "injector/ModifyReturnValue;": "extras:modify-return-value",
    EXTRAS + "injector/ModifyExpressionValue;": "extras:modify-expression-value",
    EXTRAS + "injector/ModifyReceiver;": "extras:modify-receiver",
    EXTRAS + "injector/wrapoperation/WrapOperation;": "extras:wrap-operation",
    EXTRAS + "injector/WrapWithCondition;": "extras:wrap-with-condition",
    EXTRAS + "injector/v2/WrapWithCondition;": "extras:wrap-with-condition",
    EXTRAS + "injector/wrapmethod/WrapMethod;": "extras:wrap-method",
}
INJECTORS = {"inject", "redirect", "modify-arg", "modify-args", "modify-variable", "modify-constant"} | {
    k for k in KINDS.values() if k.startswith("extras:")}


def read_annotated(data):
    """A class file's name, access flags, superclass, interfaces, and the annotations on it and on each field, method
    and method parameter: each annotation a dict of its elements with its type under "@"."""
    if data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("not a class file")
    (count,) = struct.unpack(">H", data[8:10])
    pool = [None] * count
    i, pos = 1, 10
    while i < count:
        tag = data[pos]
        if tag == 1:
            (n,) = struct.unpack(">H", data[pos + 1:pos + 3])
            pool[i] = data[pos + 3:pos + 3 + n].decode("utf-8", "replace")
            pos += 3 + n
        elif tag in (3, 4):
            pool[i] = struct.unpack(">i" if tag == 3 else ">f", data[pos + 1:pos + 5])[0]
            pos += 5
        elif tag in (5, 6):
            pool[i] = struct.unpack(">q" if tag == 5 else ">d", data[pos + 1:pos + 9])[0]
            pos += 9
            i += 1
        elif tag in (7, 8, 16, 19, 20):
            pool[i] = ("ref", struct.unpack(">H", data[pos + 1:pos + 3])[0])
            pos += 3
        elif tag in (9, 10, 11, 12, 17, 18):
            pos += 5
        elif tag == 15:
            pos += 4
        else:
            raise ValueError(f"constant pool tag {tag}")
        i += 1

    def u2(at):
        return struct.unpack(">H", data[at:at + 2])[0]

    def element(at):
        tag = chr(data[at])
        if tag in "BCDFIJSZs":
            return pool[u2(at + 1)], at + 3
        if tag == "e":
            return ("enum", pool[u2(at + 1)], pool[u2(at + 3)]), at + 5
        if tag == "c":
            return ("class", pool[u2(at + 1)]), at + 3
        if tag == "@":
            return annotation(at + 1)
        if tag == "[":
            values, at2 = [], at + 3
            for _ in range(u2(at + 1)):
                v, at2 = element(at2)
                values.append(v)
            return values, at2
        raise ValueError(f"element tag {tag}")

    def annotation(at):
        found = {"@": pool[u2(at)]}
        at2 = at + 4
        for _ in range(u2(at + 2)):
            name = pool[u2(at2)]
            found[name], at2 = element(at2 + 2)
        return found, at2

    def attributes(at):
        """The annotations and parameter annotations in the attributes at `at`, and where they end."""
        anns, params = [], []
        n, at = u2(at), at + 2
        for _ in range(n):
            name = pool[u2(at)]
            (length,) = struct.unpack(">I", data[at + 2:at + 6])
            body = at + 6
            if name in ("RuntimeVisibleAnnotations", "RuntimeInvisibleAnnotations"):
                at2 = body + 2
                for _ in range(u2(body)):
                    a, at2 = annotation(at2)
                    anns.append(a)
            elif name in ("RuntimeVisibleParameterAnnotations", "RuntimeInvisibleParameterAnnotations"):
                at2 = body + 1
                for _ in range(data[body]):
                    n = u2(at2)
                    at2 += 2
                    for _ in range(n):
                        a, at2 = annotation(at2)
                        params.append(a)
            at = body + length
        return anns, params, at

    def class_name(k):
        return pool[pool[k][1]]

    access, this, sup, n_ifaces = struct.unpack(">HHHH", data[pos:pos + 8])
    pos += 8
    interfaces = [class_name(u2(pos + 2 * j)) for j in range(n_ifaces)]
    pos += 2 * n_ifaces
    members = {"fields": [], "methods": []}
    for kind in ("fields", "methods"):
        n = u2(pos)
        pos += 2
        for _ in range(n):
            flags, name, desc = struct.unpack(">HHH", data[pos:pos + 6])
            anns, params, pos = attributes(pos + 6)
            members[kind].append({"access": flags, "name": pool[name], "desc": pool[desc], "annotations": anns,
                                  "parameters": params})
    anns, _, _ = attributes(pos)
    return {"name": class_name(this), "access": access, "super": class_name(sup) if sup else None,
            "interfaces": interfaces, "annotations": anns, **members}


def as_list(v):
    return v if isinstance(v, list) else [] if v is None else [v]


# An accessor's or invoker's prefixes, as Mixin's AccessorName and Kiln (MixinStandIns.NAME) read them.
PREFIXES = ("get", "is", "set", "call", "invoke", "new", "create")


def accessor_name(method):
    """The member an accessor or invoker names without a value, as Mixin derives it: `getFoo` -> `foo`, `isFOO` ->
    `FOO`; `None` when no prefix matches."""
    for prefix in PREFIXES:
        rest = method[len(prefix):]
        if method.startswith(prefix) and rest and rest[0].isupper():
            return rest if rest.isupper() else rest[0].lower() + rest[1:]
    return None


def selector_name(selector):
    """A target selector's member name, as Kiln's Mixin reads an accessor's or invoker's value: `Lowner;name(desc)`
    and `name:desc` give `name`."""
    if selector.startswith("L") and selector.find(";") > 0:
        selector = selector[selector.index(";") + 1:]
    for stop in "(:":
        selector = selector.split(stop, 1)[0]
    return selector


def mixin_member(m, owner):
    """The census's view of one member of a mixin class: its kind (and @At), and the target member it names, as Kiln's
    Mixin finds it (Mixin's AccessorInfo; kiln/java/kiln/MixinStandIns.java reads it so too)."""
    for a in m["annotations"]:
        kind = KINDS.get(a["@"])
        if kind is None:
            continue
        found = {"kind": kind, "name": m["name"], "desc": m["desc"], "static": bool(m["access"] & 0x0008)}
        if kind == "accessor":
            named = (as_list(a.get("value")) or [None])[0]
            found["target"] = selector_name(named) if named else accessor_name(m["name"])
            found["setter"] = m["desc"].endswith(")V")
        elif kind == "invoker":
            named = (as_list(a.get("value")) or [None])[0]
            simple = owner.rsplit("/", 1)[-1]
            if named:
                named = selector_name(named)
                if named in (owner, owner.replace("/", ".")):
                    named = "<init>"
            elif any(m["name"] in (p + simple, p + simple.rsplit("$", 1)[-1]) for p in ("new", "create")):
                # `newFoo` or `createFoo` on Foo: a constructor.
                named = "<init>"
            found["target"] = named or accessor_name(m["name"])
        elif kind in INJECTORS:
            ats = [x.get("value", "?") for x in as_list(a.get("at")) if isinstance(x, dict)]
            if kind == "modify-constant":
                ats = ats or ["CONSTANT"]
            if kind == "extras:wrap-method":
                ats = ["(the whole method)"]
            found["at"] = "+".join(sorted(set(ats))) or "?"
            found["cancellable"] = bool(a.get("cancellable"))
            found["methods"] = [s for s in as_list(a.get("method")) if isinstance(s, str)]
        return found
    return None


def read_jar_mixins(z, label, seen, skipped):
    """Every mod in the jar (and the jars it carries) with its mixin configs and mixin classes, each mod id once."""
    try:
        manifest = json.loads(z.read("fabric.mod.json").decode("utf-8", "replace"), strict=False)
    except KeyError:
        return []
    mods = []
    mod_id = manifest.get("id", "?")
    if mod_id in seen:
        return []
    seen.add(mod_id)
    # Kiln stands in for the Fabric API itself: what its modules patch, Kiln does its own way.
    if mod_id.startswith("fabric-") or mod_id == "fabricloader":
        skipped.append(mod_id)
    else:
        mod = {"jar": label, "id": mod_id, "version": manifest.get("version", ""), "configs": [], "mixins": []}
        names = set(z.namelist())
        for entry in as_list(manifest.get("mixins")):
            config, env = (entry, "*") if isinstance(entry, str) else (entry.get("config"), entry.get("environment", "*"))
            if config not in names:
                mod["configs"].append({"file": config, "missing": True})
                continue
            try:
                cfg = json.loads(z.read(config).decode("utf-8", "replace"), strict=False)
            except ValueError:
                mod["configs"].append({"file": config, "unreadable": True})
                continue
            package = cfg.get("package", "")
            mod["configs"].append({"file": config, "environment": env, "plugin": cfg.get("plugin"),
                                   "refmap": cfg.get("refmap"), "required": cfg.get("required"),
                                   "require": (cfg.get("injectors") or {}).get("defaultRequire")})
            for side in ("mixins", "client", "server"):
                for name in as_list(cfg.get(side)):
                    if not isinstance(name, str):
                        continue
                    path = (package + "." + name).replace(".", "/") + ".class"
                    try:
                        c = read_annotated(z.read(path))
                    except (KeyError, ValueError, IndexError, struct.error):
                        mod["mixins"].append({"class": name, "side": side, "unreadable": True})
                        continue
                    mod["mixins"].append(describe_mixin(c, side, env))
        if mod["configs"]:
            mods.append(mod)
    for nested in as_list(manifest.get("jars")):
        file = nested.get("file") if isinstance(nested, dict) else None
        if file and file in z.namelist():
            with zipfile.ZipFile(io.BytesIO(z.read(file))) as inner:
                mods.extend(read_jar_mixins(inner, f"{label}!{file.rsplit('/', 1)[-1]}", seen, skipped))
    return mods


def describe_mixin(c, side, env):
    """A mixin class: its targets, whether it is an interface, the interfaces it adds, each member by kind."""
    targets, priority = [], None
    for a in c["annotations"]:
        if a["@"] == MIXIN + "Mixin;":
            targets += [v[1][1:-1] for v in as_list(a.get("value")) if isinstance(v, tuple)]
            targets += [t.replace(".", "/") for t in as_list(a.get("targets")) if isinstance(t, str)]
            priority = a.get("priority")
    owner = targets[0] if targets else "?"
    members = []
    for kind in ("fields", "methods"):
        for m in c[kind]:
            found = mixin_member(m, owner)
            if found is None and m["name"] not in ("<init>", "<clinit>") and not m["access"] & 0x1000:
                # No annotation: merged into the target as it is (Mixin adds it, as @Unique without the renaming).
                found = {"kind": "merged", "name": m["name"], "desc": m["desc"]}
            if found is not None:
                found["member"] = kind[:-1]
                members.append(found)
            for p in m["parameters"]:
                if p["@"].startswith(EXTRAS + "sugar/"):
                    members.append({"kind": "extras:" + p["@"].rsplit("/", 1)[-1][:-1].lower(), "member": "parameter",
                                    "name": m["name"]})
    return {"class": c["name"], "side": side, "environment": env, "targets": targets, "priority": priority,
            "interface": bool(c["access"] & 0x0200),
            "adds": [i for i in c["interfaces"]], "members": members}


# Kiln's Mixin is the Mixin library (FabricMC's Mixin and MixinExtras, D-089): it applies every kind. Whether a mixin
# applies under Kiln is whether its targets are there: a stand-in for each of the game's classes it targets (a mod's
# or a library's own class is there as it is), and in it the methods its injectors and overwrites name. Kiln adds to
# its own stand-ins the fields and methods a @Shadow, @Accessor or @Invoker names (MixinStandIns), and since D-093 the
# methods an @Overwrite or an injector that takes a method as a whole (@Inject at HEAD, RETURN or TAIL,
# @ModifyReturnValue, @WrapMethod) names, so those don't hold a mixin back either. Points inside a method (a call, a field, a `new`) must be in the stand-in's code too, which only
# tools/mixin-points.py reads: a mixin with such a point on a stand-in is counted here as applying when its method is
# there, and Mixin leaves it out at load when its config requires the point and the code hasn't it.
GAME = ("net/minecraft/", "com/mojang/", "net/fabricmc/")
WHOLE_METHOD = {"HEAD", "RETURN", "TAIL", "(the whole method)"}


def selector(s):
    """A method selector's name and descriptor (None when it gives none), as Mixin reads `Lowner;name(desc)`."""
    s = re.sub(r"^L[^;]+;", "", s)
    if "(" in s:
        return s[:s.index("(")], s[s.index("("):]
    if ":" in s:
        return s.split(":", 1)[0], None
    return s, None


def blockers(x, kiln):
    """What keeps a mixin from applying under Kiln: (why, what) pairs, none when it applies."""
    found = []
    if x["side"] == "server":
        return [("a dedicated server's", "")]
    for t in x["targets"]:
        t = t.replace(".", "/")
        if not t.startswith(GAME):
            continue
        if t not in kiln:
            found.append(("no stand-in", t))
            continue
        declared = kiln[t][1]["declared"]
        methods = [(n, d) for n, d in declared if d.startswith("(")]
        for m in x["members"]:
            names = []
            if m["kind"] in INJECTORS:
                ats = set((m.get("at") or "").split("+"))
                if m["kind"] in ("inject", "extras:modify-return-value", "extras:wrap-method") and ats <= WHOLE_METHOD:
                    # Kiln adds the method (D-093), when the selector or the handler gives its descriptor.
                    names = [s for s in m.get("methods") or [] if "*" in s or s.startswith("/")]
                else:
                    names = m.get("methods") or []
            for sel in names:
                name, desc = selector(sel)
                if not any(fnmatch.fnmatchcase(n, name) and (desc is None or d == desc) for n, d in methods):
                    found.append(("method missing", t.rsplit("/", 1)[-1] + "." + name))
    return found


def kiln_applies(x, kiln=None):
    """Whether a mixin applies under Kiln: every kind does (D-089); its targets and their methods must be there."""
    if kiln is None:
        kiln = KILN_STAND_INS()
    return not blockers(x, kiln)


_stand_ins = None


def KILN_STAND_INS():
    global _stand_ins
    if _stand_ins is None:
        _stand_ins = kiln_stand_ins()
    return _stand_ins


def kiln_stand_ins():
    """Kiln's classes by name: where each is written (hand, generated), and what it declares."""
    kiln = kiln_classes()
    found = {}
    for name, c in kiln.items():
        outer = name.split("$", 1)[0]
        if (ROOT / "kiln" / "java" / (outer + ".java")).is_file():
            where = "hand"
        elif (ROOT / "kiln" / "java-gen" / (outer + ".java")).is_file():
            where = "generated"
        else:
            where = "library"
        found[name] = (where, c)
    return found


def census(jars):
    seen, skipped, mods = set(), [], []
    for jar in jars:
        with zipfile.ZipFile(jar) as z:
            mods.extend(read_jar_mixins(z, Path(jar).name, seen, skipped))
    return {"jars": len(jars), "mods": mods, "skipped": sorted(set(skipped))}


def member_there(stand_in, m):
    """Whether Kiln's stand-in declares what an accessor or invoker names (field by name and type, method by name and
    descriptor; a constructor by its parameters)."""
    if stand_in is None or m.get("target") is None:
        return False
    declared = stand_in[1]["declared"]
    if m["kind"] == "accessor":
        args, ret = m["desc"][1:].split(")")
        field = args if m["setter"] else ret
        return (m["target"], field) in declared
    if m["target"] == "<init>":
        return (m["target"], m["desc"].split(")")[0] + ")V") in declared
    return (m["target"], m["desc"]) in declared


def census_markdown(result, kiln):
    mods = [m for m in result["mods"] if m["mixins"]]
    mixins = [(mod, x) for mod in mods for x in mod["mixins"] if not x.get("unreadable")]
    unreadable = sum(1 for mod in mods for x in mod["mixins"] if x.get("unreadable"))
    configs = [c for mod in result["mods"] for c in mod["configs"]]
    out = []
    w = out.append
    w(f"{result['jars']} jars, {len(result['mods'])} mods with a mixin config ({len(mods)} with mixin classes), "
      f"{len(mixins)} mixin classes ({unreadable} named in a config but not in the jar), {len(configs)} configs, "
      f"{sum(1 for c in configs if c.get('plugin'))} with a plugin, "
      f"{sum(1 for c in configs if c.get('refmap'))} naming a refmap. Fabric API modules carried inside jars "
      f"are left out (Kiln stands in for the Fabric API): {len(result['skipped'])}.")
    w("")
    client = [(mod, x) for mod, x in mixins if x["side"] != "server"]
    applied = [(mod, x) for mod, x in client if kiln_applies(x, kiln)]
    why = collections.Counter(b[0] for _, x in client for b in {b[0]: b for b in blockers(x, kiln)}.values())
    whole = [m for m in mods if any(x["side"] != "server" for x in m["mixins"] if not x.get("unreadable"))]
    w(f"Kiln's Mixin is the Mixin library (D-089): it applies every kind. {len(applied)} of the {len(client)} mixins a"
      f" client applies have their targets there (a stand-in for each of the game's classes, in it the methods their"
      f" injectors that hook a point inside a method name; a mod's own class as it is; Kiln adds what @Shadow,"
      f" accessors, overwrites and the injectors that take a whole method name, D-093); {why['no stand-in']} target a"
      f" class Kiln has no stand-in for, {why['method missing']} hook a point in a method the stand-in hasn't. Every"
      f" mixin of"
      f" {sum(1 for m in whole if all(kiln_applies(x, kiln) for x in m['mixins'] if not x.get('unreadable') and x['side'] != 'server'))}"
      f" of the {len(whole)} mods applies. Points inside a method on a stand-in are counted by"
      f" `tools/mixin-points.py`.")
    w("")
    sides = collections.Counter(x["side"] for _, x in mixins)
    w(f"By side: common {sides['mixins']}, client {sides['client']}, server {sides['server']} (dedicated server only:"
      f" Kiln, a client with an integrated server, never applies those).")
    w("")
    # The kinds, by member.
    kinds, kind_mods = collections.Counter(), collections.defaultdict(set)
    ats = collections.Counter()
    for mod, x in mixins:
        for m in x["members"]:
            k = m["kind"]
            if k in ("shadow", "unique", "merged"):
                k = f"{k} {m['member']}"
            if k == "accessor":
                k = "accessor " + ("static " if m["static"] else "") + ("setter" if m["setter"] else "getter")
            if k == "invoker":
                k = "invoker " + ("static " if m["static"] else "") + ("constructor" if m["target"] == "<init>" else "method")
            kinds[k] += 1
            kind_mods[k].add(mod["id"])
            if m["kind"] in INJECTORS:
                ats[(m["kind"], m["at"])] += 1
    w("| Kind | Members | Mods |")
    w("|---|---:|---:|")
    for k, n in kinds.most_common():
        w(f"| {k} | {n} | {len(kind_mods[k])} |")
    w("")
    w("Injectors by `@At` (an injector with several gets them joined by `+`):")
    w("")
    w("| Injector | At | Members |")
    w("|---|---|---:|")
    for (k, at), n in ats.most_common():
        if n >= 3:
            w(f"| {k} | {at} | {n} |")
    rare = sum(n for n in ats.values() if n < 3)
    w(f"| (each fewer than 3) | | {rare} |")
    w("")
    # The mixin classes by shape.
    shapes = collections.Counter()
    for _, x in mixins:
        kinds_here = {m["kind"] for m in x["members"]} - {"shadow"}
        if not kinds_here:
            shapes["no members but @Shadow ones (it adds interfaces, or nothing)"] += 1
        elif x["interface"] and kinds_here <= {"accessor", "invoker"}:
            shapes["interface: accessors and invokers only"] += 1
        elif x["interface"]:
            shapes["interface: with other members"] += 1
        elif kinds_here <= {"accessor", "invoker"}:
            shapes["class: accessors and invokers only"] += 1
        else:
            shapes["class"] += 1
        if x["adds"]:
            shapes["adds an interface to its target (a duck interface)"] += 1
        if len(x["targets"]) > 1:
            shapes["more than one target"] += 1
        if not x["targets"]:
            shapes["no target Kiln can read"] += 1
    w("| Mixin classes | Count |")
    w("|---|---:|")
    for k, n in shapes.most_common():
        w(f"| {k} | {n} |")
    w("")
    # The targets, and Kiln's stand-ins for them.
    by_target = collections.defaultdict(lambda: {"mixins": 0, "mods": set(), "kinds": collections.Counter()})
    for mod, x in mixins:
        for t in x["targets"]:
            e = by_target[t]
            e["mixins"] += 1
            e["mods"].add(mod["id"])
            for m in x["members"]:
                if m["kind"] not in ("shadow", "merged", "unique"):
                    e["kinds"][m["kind"]] += 1

    def where(t):
        return kiln[t][0] if t in kiln else "none"

    areas = collections.defaultdict(collections.Counter)
    for t, e in by_target.items():
        area = ("net/minecraft/client" if t.startswith("net/minecraft/client/") else
                "net/minecraft (common)" if t.startswith("net/minecraft/") else
                "com/mojang" if t.startswith("com/mojang/") else
                "net/fabricmc" if t.startswith("net/fabricmc/") else "other (mods, libraries, the JDK)")
        areas[area][where(t)] += 1
        areas[area]["mixins"] += e["mixins"]
    w("| Targets in | Classes | Mixins on them | Kiln: hand-written | generated | none |")
    w("|---|---:|---:|---:|---:|---:|")
    for area, c in sorted(areas.items(), key=lambda kv: -kv[1]["mixins"]):
        total = c["hand"] + c["generated"] + c["none"] + c["library"]
        w(f"| {area} | {total} | {c['mixins']} | {c['hand']} | {c['generated']} | {c['none'] + c['library']} |")
    w("")
    # Accessors and invokers: does the stand-in have what they name?
    acc = collections.Counter()
    for _, x in mixins:
        for m in x["members"]:
            if m["kind"] in ("accessor", "invoker"):
                t = x["targets"][0] if x["targets"] else None
                state = where(t) if t else "none"
                there = member_there(kiln.get(t), m)
                acc[(m["kind"], state, there)] += 1
    w("| Accessors and invokers | On a hand-written stand-in | its member there | On a generated one | its member there |"
      " No stand-in |")
    w("|---|---:|---:|---:|---:|---:|")
    for k in ("accessor", "invoker"):
        hand = acc[(k, "hand", True)] + acc[(k, "hand", False)]
        gen = acc[(k, "generated", True)] + acc[(k, "generated", False)]
        none = sum(n for (kk, s, _), n in acc.items() if kk == k and s in ("none", "library"))
        w(f"| {k} | {hand} | {acc[(k, 'hand', True)]} | {gen} | {acc[(k, 'generated', True)]} | {none} |")
    w("")
    w("The targets most mixed into (Kiln: where its stand-in is written):")
    w("")
    w("| Target | Mixins | Mods | Kinds | Kiln |")
    w("|---|---:|---:|---|---|")
    for t, e in sorted(by_target.items(), key=lambda kv: (-len(kv[1]["mods"]), -kv[1]["mixins"], kv[0]))[:60]:
        top = ", ".join(f"{k} {n}" for k, n in e["kinds"].most_common(4))
        w(f"| `{t.replace('/', '.')}` | {e['mixins']} | {len(e['mods'])} | {top} | {where(t)} |")
    w("")
    w("Each mod (accessors and invokers / injectors / everything else annotated; targets with a stand-in):")
    w("")
    w("| Mod | Jar | Mixins | Kiln applies | Accessors, invokers | Injectors | Overwrites | Targets | With a stand-in |"
      " Plugin |")
    w("|---|---|---:|---:|---:|---:|---:|---:|---:|---|")
    for mod in sorted(result["mods"], key=lambda m: -len(m["mixins"])):
        xs = [x for x in mod["mixins"] if not x.get("unreadable")]
        ms = [m for x in xs for m in x["members"]]
        targets = {t for x in xs for t in x["targets"]}
        plugin = "yes" if any(c.get("plugin") for c in mod["configs"]) else ""
        w(f"| {mod['id']} | {mod['jar'].split('!')[-1]} | {len(xs)} | "
          f"{sum(1 for x in xs if x['side'] != 'server' and kiln_applies(x, kiln))} | "
          f"{sum(1 for m in ms if m['kind'] in ('accessor', 'invoker'))} | "
          f"{sum(1 for m in ms if m['kind'] in INJECTORS)} | {sum(1 for m in ms if m['kind'] == 'overwrite')} | "
          f"{len(targets)} | {sum(1 for t in targets if where(t) in ('hand', 'generated'))} | {plugin} |")
    return "\n".join(out)


def main(args):
    if "--mixins" in args:
        jars = [a for a in args if not a.startswith("--")]
        if not jars:
            sys.exit(__doc__)
        result = census(jars)
        if "--json" in args:
            json.dump(result, sys.stdout, indent=1, default=list)
        else:
            print(census_markdown(result, kiln_stand_ins()))
        return
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
