"""Reads what a Java class file declares and what it refers to.

Only the constant pool and the field and method tables are read; no code runs.
Used for two things: the classes and members Kiln's stand-ins declare (coverage),
and the classes and members a mod's classes refer to (its needs).
"""

import re
import struct
import zipfile

_ARRAY_ELEMENT = re.compile(r"L([^;]+);")

# Members every class gets from java.lang.Object, java.lang.Enum or java.lang.Record.
JDK_MEMBERS = {
    ("toString", "()Ljava/lang/String;"),
    ("hashCode", "()I"),
    ("equals", "(Ljava/lang/Object;)Z"),
    ("getClass", "()Ljava/lang/Class;"),
    ("name", "()Ljava/lang/String;"),
    ("ordinal", "()I"),
    ("compareTo", "(Ljava/lang/Enum;)I"),
    ("compareTo", "(Ljava/lang/Object;)I"),
    ("getDeclaringClass", "()Ljava/lang/Class;"),
    ("notify", "()V"),
    ("notifyAll", "()V"),
    ("wait", "()V"),
    ("wait", "(J)V"),
}


def parse_class(data):
    """Returns a dict with this, super, interfaces, fields, methods, class_refs, member_refs, utf8s."""
    if data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("not a class file")
    pos = 8
    (count,) = struct.unpack_from(">H", data, pos)
    pos += 2
    cp = [None] * count
    i = 1
    while i < count:
        tag = data[pos]
        pos += 1
        if tag == 1:
            (length,) = struct.unpack_from(">H", data, pos)
            pos += 2
            cp[i] = ("utf8", data[pos:pos + length].decode("utf-8", "replace"))
            pos += length
        elif tag in (3, 4):
            pos += 4
        elif tag in (5, 6):
            pos += 8
            i += 1  # longs and doubles take two slots
        elif tag == 7:
            cp[i] = ("class", struct.unpack_from(">H", data, pos)[0])
            pos += 2
        elif tag == 8:
            pos += 2
        elif tag in (9, 10, 11):
            c, nt = struct.unpack_from(">HH", data, pos)
            cp[i] = ("member", c, nt)
            pos += 4
        elif tag == 12:
            n, d = struct.unpack_from(">HH", data, pos)
            cp[i] = ("nat", n, d)
            pos += 4
        elif tag == 15:
            pos += 3
        elif tag == 16:
            pos += 2
        elif tag in (17, 18):
            pos += 4
        elif tag in (19, 20):
            pos += 2
        else:
            raise ValueError("unknown constant pool tag %d" % tag)
        i += 1

    def utf8(index):
        entry = cp[index] if 0 < index < count else None
        return entry[1] if entry and entry[0] == "utf8" else ""

    def class_name(index):
        entry = cp[index] if 0 < index < count else None
        return utf8(entry[1]) if entry and entry[0] == "class" else ""

    access, this_index, super_index = struct.unpack_from(">HHH", data, pos)
    pos += 6
    this = class_name(this_index)
    superclass = class_name(super_index) if super_index else ""
    (n_interfaces,) = struct.unpack_from(">H", data, pos)
    pos += 2
    interfaces = [class_name(struct.unpack_from(">H", data, pos + 2 * k)[0]) for k in range(n_interfaces)]
    pos += 2 * n_interfaces

    def members():
        nonlocal pos
        (n,) = struct.unpack_from(">H", data, pos)
        pos += 2
        out = []
        for _ in range(n):
            member_access, name_index, desc_index = struct.unpack_from(">HHH", data, pos)
            pos += 6
            (n_attributes,) = struct.unpack_from(">H", data, pos)
            pos += 2
            for _attribute in range(n_attributes):
                (length,) = struct.unpack_from(">I", data, pos + 2)
                pos += 6 + length
            out.append((utf8(name_index), utf8(desc_index), member_access))
        return out

    fields = members()
    methods = members()

    class_refs = set()
    member_refs = set()
    utf8s = []
    for entry in cp:
        if not entry:
            continue
        if entry[0] == "class":
            name = utf8(entry[1])
            if name.startswith("["):
                found = _ARRAY_ELEMENT.search(name)
                name = found.group(1) if found else ""
            if name:
                class_refs.add(name)
        elif entry[0] == "member":
            owner = class_name(entry[1])
            nat = cp[entry[2]] if 0 < entry[2] < count else None
            if owner and nat and nat[0] == "nat":
                member_refs.add((owner, utf8(nat[1]), utf8(nat[2])))
        elif entry[0] == "utf8":
            utf8s.append(entry[1])

    return {
        "this": this,
        "super": superclass,
        "interfaces": interfaces,
        "fields": fields,
        "methods": methods,
        "class_refs": class_refs,
        "member_refs": member_refs,
        "utf8s": utf8s,
        "access": access,
    }


def iter_classes(jar_path):
    """Yields (entry name, parsed class) for every top-level class file in a jar. Nested jars are skipped."""
    with zipfile.ZipFile(jar_path) as jar:
        for info in jar.infolist():
            name = info.filename
            if not name.endswith(".class") or name.startswith("META-INF/"):
                continue
            try:
                yield name, parse_class(jar.read(info))
            except (ValueError, struct.error, IndexError):
                continue


class Coverage:
    """What Kiln's stand-ins declare: classes with their supertypes and members."""

    def __init__(self, classes):
        self.classes = classes  # name -> {"super": str, "interfaces": [str], "members": set((name, desc))}

    @classmethod
    def from_jar(cls, jar_path, scope):
        classes = {}
        for _entry, parsed in iter_classes(jar_path):
            name = parsed["this"]
            if not name or not any(name.startswith(prefix) for prefix in scope):
                continue
            members = {(n, d) for n, d, _a in parsed["fields"]} | {(n, d) for n, d, _a in parsed["methods"]}
            classes[name] = {"super": parsed["super"], "interfaces": parsed["interfaces"], "members": members}
        return cls(classes)

    @classmethod
    def from_dict(cls, data):
        classes = {}
        for name, info in data["classes"].items():
            classes[name] = {
                "super": info.get("super", ""),
                "interfaces": info.get("interfaces", []),
                "members": {tuple(m.split(" ", 1)) for m in info.get("members", [])},
            }
        return cls(classes)

    def to_dict(self):
        return {
            "classes": {
                name: {
                    "super": info["super"],
                    "interfaces": info["interfaces"],
                    "members": sorted(n + " " + d for n, d in info["members"]),
                }
                for name, info in sorted(self.classes.items())
            }
        }

    def has_class(self, name):
        return name in self.classes

    def has_member(self, owner, name, desc):
        """True when the class or one of its supertypes inside the coverage declares the member."""
        stack = [owner]
        seen = set()
        while stack:
            current = stack.pop()
            if not current or current in seen:
                continue
            seen.add(current)
            if current.startswith("java/"):
                if (name, desc) in JDK_MEMBERS:
                    return True
                continue
            info = self.classes.get(current)
            if not info:
                continue
            if (name, desc) in info["members"]:
                return True
            stack.append(info["super"])
            stack.extend(info["interfaces"])
        return False


def read_manifest(jar_path):
    """The mod's fabric.mod.json, or an empty dict."""
    import json
    try:
        with zipfile.ZipFile(jar_path) as jar:
            with jar.open("fabric.mod.json") as f:
                data = json.load(f)
        return data if isinstance(data, dict) else {}
    except (KeyError, ValueError, OSError, zipfile.BadZipFile):
        return {}


def entrypoint_classes(manifest):
    """Entry point kind -> class names, from fabric.mod.json ("a.b.C", "a.b.C::method" or {"value": ...})."""
    out = {}
    entrypoints = manifest.get("entrypoints") or {}
    if not isinstance(entrypoints, dict):
        return out
    for kind, values in entrypoints.items():
        names = []
        for value in values if isinstance(values, list) else [values]:
            if isinstance(value, dict):
                value = value.get("value", "")
            if not isinstance(value, str):
                continue
            name = value.split("::", 1)[0].strip().replace(".", "/")
            if name:
                names.append(name)
        if names:
            out[str(kind)] = names
    return out


def data_facts(jar_path):
    """What a jar carries as data: structure templates and definitions, data files, assets."""
    facts = {"structures": 0, "data_files": 0, "assets": False}
    try:
        with zipfile.ZipFile(jar_path) as jar:
            for name in jar.namelist():
                if name.startswith("assets/"):
                    facts["assets"] = True
                elif name.startswith("data/") and not name.endswith("/"):
                    facts["data_files"] += 1
                    rest = name.split("/", 2)[2] if name.count("/") >= 2 else ""
                    if (rest.startswith(("structure/", "structures/")) and name.endswith(".nbt")) or \
                            (rest.startswith("worldgen/structure/") and name.endswith(".json")):
                        facts["structures"] += 1
    except (OSError, zipfile.BadZipFile):
        pass
    return facts


def collect_needs(jar_path, scope, ignore=()):
    """What a mod jar needs from the classes in scope, following its own classes from its entry points.

    Kiln runs client entry points, and the JVM loads classes lazily, so only classes reachable
    (class by class) from the client entry points count. With no client entry point every
    entry point is a root; with none at all, every class is.
    """
    parsed_by_name = {}
    mixin_annotations = False
    for _entry, parsed in iter_classes(jar_path):
        parsed_by_name[parsed["this"]] = parsed
        if not mixin_annotations and any("Lorg/spongepowered/asm/mixin/" in u for u in parsed["utf8s"]):
            mixin_annotations = True
    own = set(parsed_by_name)
    manifest = read_manifest(jar_path)
    entrypoints = entrypoint_classes(manifest)
    client_roots = [c for c in entrypoints.get("client", []) if c in own]
    main_roots = [c for c in entrypoints.get("main", []) if c in own]
    roots = client_roots + [c for c in main_roots if c not in client_roots]
    root_kind = "client" if client_roots else "main" if main_roots else "none"
    if not roots:
        roots = sorted(own)

    reachable = set()
    stack = list(roots)
    while stack:
        name = stack.pop()
        if name in reachable:
            continue
        reachable.add(name)
        parsed = parsed_by_name.get(name)
        if not parsed:
            continue
        for ref in parsed["class_refs"]:
            if ref in own and ref not in reachable:
                stack.append(ref)
        # Inner classes travel with their outer class.
        for other in own:
            if other.startswith(name + "$") and other not in reachable:
                stack.append(other)

    def in_scope(name):
        return any(name.startswith(p) for p in scope) and not any(name.startswith(p) for p in ignore)

    classes = set()
    members = set()
    for name in reachable:
        parsed = parsed_by_name.get(name)
        if not parsed:
            continue
        classes |= {c for c in parsed["class_refs"] if c not in own and in_scope(c)}
        members |= {m for m in parsed["member_refs"] if m[0] not in own and in_scope(m[0])}

    mixins = manifest.get("mixins") or []
    mixin_configs = []
    for m in mixins if isinstance(mixins, list) else [mixins]:
        if isinstance(m, dict):
            m = m.get("config", "")
        if isinstance(m, str) and m:
            mixin_configs.append(m)
    depends = manifest.get("depends") or {}
    nested = manifest.get("jars") or []
    data = data_facts(jar_path)
    return {
        "data": data,
        "scope": list(scope),
        "mod_id": str(manifest.get("id", "")),
        "mod_version": str(manifest.get("version", "")),
        "environment": str(manifest.get("environment", "")),
        "classes": len(own),
        "reachable": len(reachable),
        "root_kind": root_kind,
        "entrypoints": {k: len(v) for k, v in entrypoints.items()},
        "mixins": mixin_configs,
        "mixin_annotations": mixin_annotations,
        "access_widener": bool(manifest.get("accessWidener")),
        "depends": sorted(str(k) for k in depends) if isinstance(depends, dict) else [],
        "nested_jars": len(nested) if isinstance(nested, list) else 0,
        "needs_classes": sorted(classes),
        "needs_members": sorted("%s %s %s" % m for m in members),
    }


def missing(needs, coverage):
    """Which of the needed classes and members the coverage lacks."""
    missing_classes = [c for c in needs["needs_classes"] if not coverage.has_class(c)]
    missing_members = []
    for entry in needs["needs_members"]:
        owner, name, desc = entry.split(" ", 2)
        if coverage.has_class(owner) and not coverage.has_member(owner, name, desc):
            missing_members.append(entry)
    return missing_classes, missing_members


def analyze_jar(jar_path, coverage, scope, ignore=()):
    """collect_needs and missing in one call, for a quick look at one jar."""
    needs = collect_needs(jar_path, scope, ignore)
    missing_classes, missing_members = missing(needs, coverage)
    needs["missing_classes"] = missing_classes
    needs["missing_members"] = missing_members
    return needs
