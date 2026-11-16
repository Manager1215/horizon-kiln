"""What a soft switch can stop of each mod (D-113): read from its jar, nothing run.

    python3 -I tools/soft-off.py JAR [JAR ...]                     one line a jar: its class and why
    python3 -I tools/soft-off.py --json JAR ...                    the same as JSON, keyed by the jar's SHA-256
    python3 -I tools/soft-off.py --catalogue RESULT.json [--write] the catalogue's mods (catalogue/tried.json by jar
                                                                   hash, verified.json by mod id) with their class;
                                                                   --write puts it in both files as "softOff"

A mod turned off "now" (the soft switch, kiln/java/kiln/SoftSwitch.java) keeps its classes loaded: Kiln skips its
Fabric event listeners (its ticks among them), its HUD elements and its commands, and its mixins' merged handlers that
Kiln guards (MixinGuards) return at once, as if the handler did nothing. So:

- safe: the mod works only through those: events, HUD, keys, commands, @Inject and the @Modify* handlers Kiln guards.
- partial: it also leaves something a switch can't take back, inert or not: blocks, items or entities registered (a
  saved world needs them), its own threads, network channels, world generation, a data or resource pack's files.
- restart: a mixin into the game's classes that replaces or wraps the game's code (@Overwrite, @Redirect, MixinExtras'
  @WrapOperation and @WrapMethod, @ModifyArgs, a modifier Kiln can't guard): the game's own code is gone there until
  the next game.
"""
import hashlib
import importlib.util
import json
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
_spec = importlib.util.spec_from_file_location("kiln_scan", ROOT / "tools" / "kiln-scan.py")
scan = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(scan)

# The reading's version: raised whenever a change here can give a jar another class. Kiln's own reading
# (kiln/java/kiln/SoftOffScan.java) carries the same number, and reads a jar itself when the catalogue's copy it has
# (tools/catalogue-snapshot.py) was made by an older reading.
RULES = 1

GAME = ("net/minecraft/", "com/mojang/blaze3d/")
# The handlers MixinGuards guards (and the soft switch skips), by the census's kind.
GUARDED_VOID = {"inject"}
MODIFIERS = {"modify-arg", "modify-variable", "modify-constant", "extras:modify-return-value",
             "extras:modify-expression-value", "extras:modify-receiver"}
CONDITION = {"extras:wrap-with-condition"}
REPLACING = {"overwrite", "redirect", "modify-args", "extras:wrap-operation", "extras:wrap-method"}

# What the mod's code calls that a switch leaves behind.
REGISTERS = {("net/minecraft/core/Registry", "register"), ("net/minecraft/core/Registry", "registerForHolder"),
             ("net/minecraft/core/Registry", "registerReference")}
THREADS = {("java/lang/Thread", "start"), ("java/lang/Thread", "startVirtualThread"),
           ("java/lang/Thread$Builder", "start"), ("java/util/Timer", "<init>"),
           ("java/util/concurrent/Executors", "newSingleThreadExecutor"),
           ("java/util/concurrent/Executors", "newFixedThreadPool"),
           ("java/util/concurrent/Executors", "newCachedThreadPool"),
           ("java/util/concurrent/Executors", "newScheduledThreadPool"),
           ("java/util/concurrent/Executors", "newSingleThreadScheduledExecutor"),
           ("java/util/concurrent/Executors", "newVirtualThreadPerTaskExecutor"),
           ("java/util/concurrent/CompletableFuture", "runAsync"),
           ("java/util/concurrent/CompletableFuture", "supplyAsync")}
NETWORK_CLASSES = ("net/fabricmc/fabric/api/networking/v1/PayloadTypeRegistry",)
NETWORK_RECEIVERS = {("net/fabricmc/fabric/api/networking/v1/ServerPlayNetworking", "registerGlobalReceiver"),
                     ("net/fabricmc/fabric/api/client/networking/v1/ClientPlayNetworking", "registerGlobalReceiver")}
WORLDGEN_CLASSES = ("net/fabricmc/fabric/api/biome/v1/BiomeModifications",)
DATA_WORLDGEN = ("/worldgen/", "/structure/", "/structures/", "/dimension/", "/dimension_type/")


def descriptor_types(desc):
    """A method descriptor's argument types and return type, as descriptors."""
    args, i = [], 1
    while desc[i] != ")":
        j = i
        while desc[j] == "[":
            j += 1
        j = desc.index(";", j) + 1 if desc[j] == "L" else j + 1
        args.append(desc[i:j])
        i = j
    return args, desc[i + 1:]


def handler_stops(member):
    """Whether Kiln's guard is on this handler, so the soft switch stops it (MixinGuards.guard's rule)."""
    kind, desc = member["kind"], member.get("desc", "")
    if kind in GUARDED_VOID:
        return desc.endswith(")V")
    try:
        args, ret = descriptor_types(desc)
    except (ValueError, IndexError):
        return False
    if kind in MODIFIERS:
        return bool(args) and args[0] == ret
    if kind in CONDITION:
        return ret == "Z"
    return False


def classify_jar(path):
    """(class, reasons) for the mod in the jar and the jars it carries."""
    reasons = {"restart": [], "partial": []}
    with zipfile.ZipFile(path) as z:
        names = z.namelist()
        mixins = scan.read_jar_mixins(z, Path(path).name, set(), [])
        classes = [n for n in names if n.endswith(".class") and not n.startswith("META-INF/")]
        data = [n for n in names if n.startswith("data/") and not n.endswith("/")]
        assets = [n for n in names if n.startswith("assets/")
                  and any(d in n for d in ("/textures/", "/models/", "/sounds/", "/shaders/"))]
        refs = set()
        for name in classes:
            try:
                c = scan.parse_class(z.read(name))
            except (ValueError, IndexError, Exception):
                continue
            refs |= {(o, n) for o, n, _, _ in c["members"]}
            refs |= {(cl, None) for cl in c["classes"]}
        # The jars it carries: a library's own code counts as the mod's.
        for entry in names:
            if entry.startswith("META-INF/jars/") and entry.endswith(".jar"):
                import io
                try:
                    with zipfile.ZipFile(io.BytesIO(z.read(entry))) as inner:
                        for name in inner.namelist():
                            if name.endswith(".class"):
                                try:
                                    c = scan.parse_class(inner.read(name))
                                except Exception:
                                    continue
                                refs |= {(o, n) for o, n, _, _ in c["members"]}
                                refs |= {(cl, None) for cl in c["classes"]}
                except zipfile.BadZipFile:
                    pass
    replaced, unguarded = set(), set()
    for mod in mixins:
        for x in mod["mixins"]:
            if x.get("unreadable") or x["side"] == "server":
                continue
            if not any(t.startswith(GAME) for t in x["targets"]):
                continue
            for m in x["members"]:
                if m["kind"] in REPLACING:
                    replaced.add("@" + m["kind"].removeprefix("extras:"))
                elif m["kind"] in scan.INJECTORS and not handler_stops(m):
                    unguarded.add("@" + m["kind"].removeprefix("extras:"))
    if replaced:
        reasons["restart"].append(" and ".join(sorted(replaced)) + " into the game's classes")
    if unguarded:
        reasons["restart"].append(" and ".join(sorted(unguarded)) + " Kiln doesn't guard")
    names_called = {r for r in refs if r[1] is not None}
    if names_called & REGISTERS:
        reasons["partial"].append("registers blocks, items or entities (they stay, inert)")
    if names_called & THREADS:
        reasons["partial"].append("runs threads of its own")
    if names_called & NETWORK_RECEIVERS or any((c, None) in refs for c in NETWORK_CLASSES):
        reasons["partial"].append("has network channels")
    if any((c, None) in refs for c in WORLDGEN_CLASSES) or any(any(d in n for d in DATA_WORLDGEN) for n in data):
        reasons["partial"].append("generates in the world")
    elif data:
        reasons["partial"].append("data pack files (recipes, loot, tags) stay")
    if not classes and assets:
        reasons["partial"].append("resource pack files stay")
    if reasons["restart"]:
        return "restart", reasons["restart"] + reasons["partial"]
    if reasons["partial"]:
        return "partial", reasons["partial"]
    if not classes:
        return "safe", ["no code"]
    return "safe", ["only events, HUD, keys, commands and guarded mixin handlers"]


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def mod_id(path):
    try:
        with zipfile.ZipFile(path) as z:
            return json.loads(z.read("fabric.mod.json").decode("utf-8", "replace"), strict=False).get("id")
    except (KeyError, ValueError, zipfile.BadZipFile):
        return None


ALIASES = {"kiln-speedrun-timer": "kiln_timer", "armour-durablity-hud": "armour_durability"}


def norm(name):
    return "".join(c for c in name.lower() if c.isalnum())


def catalogue(result_file, write):
    """The class of each catalogue mod from RESULT.json (--json's output), by jar hash or mod id."""
    result = json.loads(Path(result_file).read_text())
    # A verified mod with no jar in tried.json: by its mod id, letters and digits only (better-than-mending is
    # betterthanmending), or as named here where the catalogue's id isn't the jar's.
    by_id = {norm(r["id"]): r for r in result.values() if r.get("id")}
    by_id.update({norm(k): by_id[norm(v)] for k, v in ALIASES.items() if norm(v) in by_id})
    counts = {}
    tried = {m["id"]: m for m in json.loads((ROOT / "catalogue" / "tried.json").read_text())["mods"]}
    for name, key in (("tried.json", "jar"), ("verified.json", None)):
        file = ROOT / "catalogue" / name
        doc = json.loads(file.read_text())
        for m in doc["mods"]:
            jar = m.get("jar") or (tried.get(m["id"]) or tried.get(m.get("modrinth", "")) or {}).get("jar") or {}
            found = result.get(jar.get("sha256", ""))
            found = found or by_id.get(norm(m["id"])) or by_id.get(norm(m.get("modrinth", "")))
            if found is None:
                continue
            m["softOff"] = {"class": found["class"], "why": "; ".join(found["why"])}
            works = m.get("status") == "works" if key is None else (m.get("result") or {}).get("works")
            counts.setdefault(name, {}).setdefault("works" if works else "other", {}).setdefault(found["class"], 0)
            counts[name]["works" if works else "other"][found["class"]] += 1
        if write:
            file.write_text(json.dumps(doc, indent=2, ensure_ascii=False) + "\n")
    print(json.dumps(counts, indent=1))


def main(argv):
    if argv[:1] == ["--catalogue"]:
        catalogue(argv[1], "--write" in argv)
        return
    as_json = argv[:1] == ["--json"]
    out = {}
    for jar in argv[1:] if as_json else argv:
        try:
            kind, why = classify_jar(jar)
        except (zipfile.BadZipFile, OSError) as e:
            print(f"{jar}: unreadable ({e})", file=sys.stderr)
            continue
        if as_json:
            out[sha256(jar)] = {"jar": Path(jar).name, "id": mod_id(jar), "class": kind, "why": why}
        else:
            print(f"{kind:8} {Path(jar).name}: {'; '.join(why)}")
    if as_json:
        print(json.dumps(out, indent=1))


if __name__ == "__main__":
    main(sys.argv[1:])
