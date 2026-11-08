#!/usr/bin/env python3
"""Scans Modrinth for Fabric mods and writes the catalogue the Kiln site shows.

    python3 catalogue/scan.py --coverage catalogue/coverage.json --kiln 0.1.1-alpha

For every Fabric mod for the game version in rules.json: the newest matching version's jar is
downloaded once (results are cached by file hash), its classes are read (never run), and what it
needs from Minecraft and the Fabric API is compared with what Kiln's stand-ins declare. Together
with verified.json (mods tried in the game by hand) this becomes:

    catalogue/scan.json        the state: every project, its file, what it lacks, its status (kept in CI's cache)
    catalogue/site/mods.json   the site's index
    catalogue/site/mods/*.json one file per mod for the site's mod pages
    catalogue/site/stats.json  counts and the symbols mods lack most often

Statuses: works and doesn't-work and in-development come from verified.json only. The scan gives
planned (lacks something on the roadmap), no-plans (lacks something that is not on the roadmap)
or not-checked (nothing found lacking, or the jar could not be read). "Should work" (`may-work`) needs a
passing run on Kiln's test stand (probe.py) that reached nothing Kiln has only the name of: an automatic test,
not a check by hand.
"""

import argparse
import concurrent.futures
import datetime
import hashlib
import json
import os
import re
import shutil
import sys
import tempfile
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from classfile import Coverage, collect_needs, missing  # noqa: E402

API = "https://api.modrinth.com/v2"
USER_AGENT = "horizon-kiln catalogue (https://github.com/Manager1215/horizon-kiln)"
HERE = os.path.dirname(os.path.abspath(__file__))

LABELS = {
    "works": "Works",
    "may-work": "Should work",
    "planned": "Planned",
    "no-plans": "No plans yet",
    "not-checked": "Not checked",
    "doesnt-work": "Doesn't work",
    "in-development": "In development",
}
MEANINGS = {
    "works": "Tried in the game with Kiln.",
    "may-work": "An automatic test ran it on Kiln's test stand (its own code, with a scripted minute of play in place of the game) and nothing broke, and everything it called is written in Kiln: it most likely works. Not checked in the game by hand yet.",
    "planned": "Calls something that is on Kiln's roadmap. Parts of it may already run.",
    "no-plans": "Calls something that is not on Kiln's roadmap yet. Parts of it may already run.",
    "not-checked": "In the catalogue, not checked yet.",
    "doesnt-work": "Tried in the game with Kiln; it does not work yet.",
    "in-development": "Kiln is being built with this mod. Not released.",
}
MISSING_CAP = 60  # symbols kept per mod in scan.json
NEEDS_CAP = 40    # symbols shown on a mod's page


class TooBig(Exception):
    pass


class Modrinth:
    """Modrinth's public API, politely: one user agent, the rate limit respected, retries on errors."""

    def __init__(self, delay=0.22):
        self.delay = delay
        self.calls = 0

    def get(self, path, params=None, retries=4):
        url = API + path
        if params:
            url += "?" + urllib.parse.urlencode(params)
        for attempt in range(retries):
            request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "application/json"})
            try:
                with urllib.request.urlopen(request, timeout=60) as response:
                    remaining = int(response.headers.get("X-Ratelimit-Remaining", "100") or 100)
                    reset = int(response.headers.get("X-Ratelimit-Reset", "0") or 0)
                    data = json.load(response)
                self.calls += 1
                time.sleep(reset + 1 if remaining < 5 else self.delay)
                return data
            except urllib.error.HTTPError as error:
                if error.code == 404:
                    return None
                if error.code == 429:
                    time.sleep(int(error.headers.get("X-Ratelimit-Reset", "60") or 60) + 1)
                    continue
                if error.code >= 500 and attempt < retries - 1:
                    time.sleep(5 * (attempt + 1))
                    continue
                raise
            except (urllib.error.URLError, TimeoutError, OSError):
                if attempt < retries - 1:
                    time.sleep(5 * (attempt + 1))
                    continue
                raise
        return None

    def search(self, rules, limit):
        """Every project matching the rules, most downloaded first; de-duplicated."""
        facets = [["versions:" + rules["game_version"]], ["categories:" + rules["loader"]], ["project_type:mod"]]
        hits = {}
        offset = 0
        page = 100
        while True:
            data = self.get("/search", {"facets": json.dumps(facets), "limit": page, "offset": offset, "index": "downloads"})
            if not data or not data.get("hits"):
                break
            for hit in data["hits"]:
                hits.setdefault(hit["project_id"], hit)
                if limit and len(hits) >= limit:
                    return list(hits.values())
            offset += page
            if offset >= data.get("total_hits", 0):
                break
        return list(hits.values())

    def newest_version(self, project_id, rules):
        """The newest version for the loader and game version, with its primary file."""
        versions = self.get("/project/%s/version" % project_id, {
            "loaders": json.dumps([rules["loader"]]),
            "game_versions": json.dumps([rules["game_version"]]),
        })
        for version in versions or []:
            if rules["game_version"] not in version.get("game_versions", []):
                continue
            if rules["loader"] not in version.get("loaders", []):
                continue
            files = version.get("files") or []
            chosen = next((f for f in files if f.get("primary")), None) or next((f for f in files if f.get("filename", "").endswith(".jar")), None)
            if not chosen:
                continue
            return {
                "number": version.get("version_number", ""),
                "id": version.get("id", ""),
                "published": (version.get("date_published") or "")[:10],
                "file": {
                    "name": chosen.get("filename", ""),
                    "size": int(chosen.get("size") or 0),
                    "sha512": (chosen.get("hashes") or {}).get("sha512", ""),
                    "url": chosen.get("url", ""),
                },
            }
        return None


def download(url, max_bytes, expected_sha512):
    """Downloads a jar to a temporary file; returns (path, sha512)."""
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    digest = hashlib.sha512()
    size = 0
    handle = tempfile.NamedTemporaryFile(delete=False, suffix=".jar")
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            while True:
                chunk = response.read(1 << 16)
                if not chunk:
                    break
                size += len(chunk)
                if size > max_bytes:
                    raise TooBig()
                digest.update(chunk)
                handle.write(chunk)
        handle.close()
        if expected_sha512 and digest.hexdigest() != expected_sha512:
            raise ValueError("the file's hash does not match Modrinth's")
        return handle.name, digest.hexdigest()
    except BaseException:
        handle.close()
        os.unlink(handle.name)
        raise


def clean_name(title):
    """A mod's name without the emoji and pictographs Modrinth titles carry: "Day Counter 🌞✅" is "Day Counter"."""
    out = []
    for ch in str(title or ""):
        code = ord(ch)
        if (unicodedata.category(ch) in ("So", "Cs", "Co", "Cn") or code == 0x200D or 0xFE00 <= code <= 0xFE0F
                or 0x1F000 <= code <= 0x1FAFF or 0x2600 <= code <= 0x27BF or 0x1F3FB <= code <= 0x1F3FF):
            out.append(" ")
        else:
            out.append(ch)
    text = re.sub(r"\s+", " ", "".join(out)).strip()
    if "".join(out) != str(title or ""):  # a symbol went: a separator it hung on goes too ("Day Counter -" -> "Day Counter")
        text = re.sub(r"[\s\-–—:|·]+$", "", text).strip()
    return text or str(title or "")


def dotted(internal_name):
    return internal_name.replace("/", ".").replace("$", ".")


def readable_member(entry):
    owner, name, desc = entry.split(" ", 2)
    simple = owner.rsplit("/", 1)[-1].replace("$", ".")
    return "%s.%s%s" % (simple, name, "()" if desc.startswith("(") else "")


def stage_for(symbol_class, rules):
    """The roadmap stage a class belongs to, by the longest matching prefix; None when unmapped."""
    best = None
    best_length = -1
    for stage in rules.get("roadmap", []):
        for prefix in stage.get("prefixes", []):
            if symbol_class.startswith(prefix) and len(prefix) > best_length:
                best, best_length = stage, len(prefix)
    return best


INITIALIZER_CLASSES = {
    "net/fabricmc/api/ModInitializer",
    "net/fabricmc/api/DedicatedServerModInitializer",
}


def derive_status(summary, missing_classes, missing_members, hit, rules, kiln):
    """Status, roadmap stage, the short needs line, the readable needs list and the evidence, from the scan alone."""
    if not summary.get("classes"):
        data = summary.get("data") or {}
        if data.get("structures"):
            return "may-work", "", "", [], (
                "Data only, with %d structure file%s: Kiln 0.2.0 generates structures from data in Horizon's worlds. "
                "Not tried in the game." % (data["structures"], "" if data["structures"] == 1 else "s"))
        if summary.get("nested_jars"):
            return "not-checked", "", "", [], "No classes at the top level of the file (only nested jars); not scanned."
        if data.get("data_files") or data.get("assets"):
            return "not-checked", "", "", [], "Data only, no Java code: Horizon's mod browser imports its data; nothing for Kiln to run."
        return "not-checked", "", "", [], "No classes and no data in the file; not scanned."

    stages = {}
    unmapped = []
    for symbol in missing_classes + [m.split(" ", 1)[0] for m in missing_members]:
        stage = stage_for(symbol, rules)
        if stage:
            stages[stage["stage"]] = stage["title"]
        else:
            unmapped.append(symbol)
    # Kiln applies every kind of Mixin (D-089) and adds the methods a mixin takes whole (D-093): what still waits on
    # Mixin is a mixin that hooks a point inside one of the game's methods, which Kiln's stand-ins have the names of,
    # not the code; and an access widener, which Kiln doesn't apply.
    uses_mixin = bool(summary.get("mixin_inside")) if "mixin_inside" in summary else bool(
        summary.get("mixins") or summary.get("mixin_annotations"))
    if uses_mixin or summary.get("access_widener"):
        for stage in rules.get("roadmap", []):
            if stage.get("mixins"):
                stages[stage["stage"]] = stage["title"]

    readable = [dotted(c) for c in missing_classes] + [readable_member(m) for m in missing_members]
    unmapped_readable = [dotted(c) for c in missing_classes if not stage_for(c, rules)] + \
        [readable_member(m) for m in missing_members if not stage_for(m.split(" ", 1)[0], rules)]
    total = len(readable)
    readable = readable[:NEEDS_CAP]
    if total > NEEDS_CAP:
        readable.append("and %d more" % (total - NEEDS_CAP))
    if uses_mixin:
        inside = summary.get("mixin_inside")
        readable.insert(0, "Mixin: %d point%s inside the game's methods" % (inside, "" if inside == 1 else "s")
                        if inside else "Mixin")
    if summary.get("access_widener"):
        readable.insert(0, "an access widener")
    stage_text = ", ".join("%s (%s)" % (title, number) for number, title in sorted(stages.items()))

    if hit.get("client_side") == "unsupported" and rules.get("server_only"):
        rule = rules["server_only"]
        return rule["status"], rule.get("stage", ""), rule.get("note", ""), readable, ""

    # A mod whose code is only an initializer: its content is data, which Horizon's launcher imports.
    initializer_only = (
        summary.get("root_kind") != "client" and summary.get("entrypoints")
        and not missing_members and all(c in INITIALIZER_CLASSES for c in missing_classes)
        and not uses_mixin
    )
    if initializer_only and rules.get("main_entrypoint_only"):
        rule = rules["main_entrypoint_only"]
        return rule["status"], rule.get("stage", ""), rule.get("note", ""), readable, ""

    if unmapped:
        example = unmapped_readable[0] if unmapped_readable else ""
        line = "Lacks %d thing%s Kiln does not cover yet" % (len(unmapped), "" if len(unmapped) == 1 else "s")
        if example:
            line += ", for example " + example
        line += "; not on the roadmap."
        if stage_text:
            line += " Also needs %s." % stage_text
        return rules.get("unmapped", {}).get("status", "no-plans"), stage_text, line, readable, ""

    if stages:
        return "planned", stage_text, "Needs %s." % stage_text, readable, ""

    if not summary.get("needs_classes") and not summary.get("needs_members"):
        evidence = "Its code calls nothing in Minecraft or the Fabric API that Kiln would have to provide. Not run yet."
    else:
        evidence = "Everything its code calls is covered by Kiln %s (%d classes, %d members). Not run yet." % (
            kiln or "", summary.get("needs_classes", 0), summary.get("needs_members", 0))
    return "not-checked", "", "", [], evidence


FAILED_SYMBOL = re.compile(r"NoClassDefFoundError: Could not initialize class ([\w/.$]+)|"
                           r"(?:NoClassDefFoundError|ClassNotFoundException): (?!Could\b)([\w/.$]+)|"
                           r"NoSuchMethodError: '?[\w.$\[\]]+ ([\w.$]+)\.[\w<>$]+\(|"
                           r"NoSuchFieldError: Class ([\w.$]+) does not have member field|"
                           r"NoSuchFieldError: (?:'?[\w.$\[\]]+ )?([\w.$]+)\.[\w$]+")


def symbols_in(text):
    """Classes a Java error names: NoClassDefFoundError: a/b/C, NoSuchMethodError: 'void a.b.C.m()'."""
    found = []
    for match in FAILED_SYMBOL.finditer(text or ""):
        name = next((g for g in match.groups() if g), "")
        if name:
            found.append(name.replace(".", "/"))
    return found


def hand_in_channel(hand, channel):
    """A hand check against a released Kiln (1.2.3) counts in both channels; one against main or a nightly only in
    dev; an entry with no check (a mod Kiln is being built with) shows in both."""
    kiln = str((hand.get("checked") or {}).get("kiln") or "")
    released = bool(re.match(r"^\d+\.\d+\.\d+(-(alpha|beta|rc)[\w.]*)?$", kiln))
    if channel == "release":
        return released or not kiln
    return True


def run_for(entry, runs):
    """The probe's record for the project's current file, if any."""
    version = entry.get("version") or {}
    key = (version.get("file", {}).get("sha512") or version.get("id") or "")[:40]
    return runs.get(key) if key else None


def load_json(path, default):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError):
        return default


def write_json(path, data):
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=1, ensure_ascii=False)
        f.write("\n")
    os.replace(tmp, path)


def safe_id(slug):
    slug = "".join(ch if ch.isalnum() or ch == "-" else "-" for ch in slug.lower()).strip("-")
    return slug[:80] or "mod"


def summarize(needs):
    return {
        "data": needs.get("data") or {},
        "nested_jars": needs.get("nested_jars", 0),
        "classes": needs.get("classes", 0),
        "reachable": needs.get("reachable", 0),
        "root_kind": needs.get("root_kind", ""),
        "entrypoints": needs.get("entrypoints", {}),
        "mixins": bool(needs.get("mixins")),
        "mixin_annotations": bool(needs.get("mixin_annotations")),
        "mixin_targets": len(needs.get("mixin_targets") or []),
        "mixin_inside": len(needs.get("mixin_inside") or []),
        "access_widener": bool(needs.get("access_widener")),
        "depends": needs.get("depends", []),
        "needs_classes": len(needs.get("needs_classes", [])),
        "needs_members": len(needs.get("needs_members", [])),
    }


def fetch_needs(version, rules, max_bytes, path_in_cache, keep_dir=""):
    """Downloads a file and reads what it needs; runs in a worker thread and touches nothing shared.
    With keep_dir the file stays there under the name probe.py looks for, so the stand need not download it again."""
    path, _digest = download(version["file"]["url"], max_bytes, version["file"]["sha512"])
    try:
        needs = collect_needs(path, rules["scope"], rules.get("ignore", []))
    finally:
        key = (version["file"].get("sha512") or version.get("id") or "")[:40]
        if keep_dir and key:
            os.makedirs(keep_dir, exist_ok=True)
            shutil.move(path, os.path.join(keep_dir, key + ".jar"))
        else:
            os.unlink(path)
    if path_in_cache:
        write_json(path_in_cache, needs)
    return needs


def settle(pending, stats, fresh, coverage, today, wait_for_one):
    """Takes finished downloads into their entries: the first one done when wait_for_one, else all of them."""
    if not pending:
        return
    when = concurrent.futures.FIRST_COMPLETED if wait_for_one else concurrent.futures.ALL_COMPLETED
    done, _rest = concurrent.futures.wait(list(pending), return_when=when)
    for future in done:
        slug, entry = pending.pop(future)
        try:
            needs = future.result()
        except TooBig:
            entry["error"] = "file too big to scan (%d bytes)" % entry["version"]["file"]["size"]
            entry["summary"] = None
            entry["missing"] = None
            stats["errors"] += 1
            continue
        except Exception as error:  # one broken mod must not stop the scan
            entry["error"] = "could not read the file: %s" % str(error)[:160]
            entry["summary"] = None
            entry["missing"] = None
            stats["errors"] += 1
            continue
        stats["fetched"] += 1
        entry["summary"] = summarize(needs)
        entry["scanned"] = today
        entry["error"] = ""
        fresh[slug] = missing(needs, coverage)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--rules", default=os.path.join(HERE, "rules.json"))
    parser.add_argument("--coverage", default=os.path.join(HERE, "coverage.json"))
    parser.add_argument("--verified", default=os.path.join(HERE, "verified.json"))
    parser.add_argument("--state", default=os.path.join(HERE, "scan.json"))
    parser.add_argument("--cache", default=os.path.join(HERE, "cache"), help="per-file needs, keyed by hash")
    parser.add_argument("--out", default=os.path.join(HERE, "site"), help="where the site's files go")
    parser.add_argument("--runs", default=os.path.join(HERE, "runs.json"), help="what probe.py found when running mods")
    parser.add_argument("--kiln", default="", help="the Kiln version the coverage belongs to")
    parser.add_argument("--channel", choices=("release", "dev"), default="release",
                        help="release: hand checks against a released Kiln count; dev: checks against main count too")
    parser.add_argument("--limit", type=int, default=0, help="projects to take from the search (0 = all)")
    parser.add_argument("--only", default="", help="comma-separated slugs to scan, for a look at a few")
    parser.add_argument("--max-minutes", type=int, default=300, help="stop fetching new files after this long")
    parser.add_argument("--offline", action="store_true", help="no network: rebuild the site files from the state")
    parser.add_argument("--jobs", type=int, default=6, help="files downloaded and read side by side")
    parser.add_argument("--keep-jars", default="", help="keep the downloaded files in this folder, named for probe.py")
    parser.add_argument("--delay", type=float, default=0.15, help="seconds between Modrinth API calls (its limit is 300 a minute)")
    args = parser.parse_args()

    rules = load_json(args.rules, None)
    if not rules:
        print("rules.json is missing or broken:", args.rules, file=sys.stderr)
        return 2
    coverage_data = load_json(args.coverage, None)
    if not coverage_data:
        print("coverage.json is missing or broken:", args.coverage, file=sys.stderr)
        return 2
    coverage = Coverage.from_dict(coverage_data)
    kiln = args.kiln or coverage_data.get("kiln", "")
    verified = load_json(args.verified, {"mods": []}).get("mods", [])
    verified = [v for v in verified if hand_in_channel(v, args.channel)]
    runs = load_json(args.runs, {}).get("runs", {})
    state = load_json(args.state, {"schema": 1, "projects": {}})
    projects = state.setdefault("projects", {})
    started = time.time()
    today = datetime.date.today().isoformat()

    api = Modrinth(delay=args.delay)
    stats = {"hits": 0, "fetched": 0, "cached": 0, "reused": 0, "errors": 0, "skipped": 0}
    max_bytes = rules.get("max_jar_bytes", 60000000)

    def cache_path_for(version):
        key = (version.get("file", {}).get("sha512") or version.get("id") or "")[:40]
        return os.path.join(args.cache, key + ".json") if key else ""


    fresh = {}  # slug -> (missing_classes, missing_members), computed this run
    pool = concurrent.futures.ThreadPoolExecutor(max_workers=max(1, args.jobs))
    pending = {}  # future -> (slug, entry): files being downloaded and read
    if not args.offline:
        hits = api.search(rules, args.limit)
        print("projects in search: %d" % len(hits), flush=True)
        only = {s.strip() for s in args.only.split(",") if s.strip()}
        if only:
            hits = [h for h in hits if h["slug"] in only]
        stats["hits"] = len(hits)
        seen = set()
        for hit in hits:
            slug = hit["slug"]
            seen.add(slug)
            if len(seen) % 250 == 0:
                print("%d/%d projects: fetched %d, from cache %d, errors %d, %.0f min" % (
                    len(seen), len(hits), stats["fetched"], stats["cached"], stats["errors"], (time.time() - started) / 60), flush=True)
            previous = projects.get(slug, {})
            entry = {
                "id": hit["project_id"],
                "slug": slug,
                "title": clean_name(hit.get("title") or slug),
                "description": (hit.get("description") or "").strip(),
                "author": hit.get("author", ""),
                "client_side": hit.get("client_side", ""),
                "server_side": hit.get("server_side", ""),
                "library": "library" in (hit.get("categories") or []),
                "date_modified": (hit.get("date_modified") or "")[:19],
                "version": previous.get("version"),
                "summary": previous.get("summary"),
                "missing": previous.get("missing"),
                "scanned": previous.get("scanned", ""),
                "error": previous.get("error", ""),
            }
            projects[slug] = entry
            out_of_time = (time.time() - started) > args.max_minutes * 60
            unchanged = previous.get("date_modified") == entry["date_modified"] and bool(previous.get("version"))
            try:
                if not unchanged:
                    if out_of_time:
                        stats["skipped"] += 1
                        if not entry["summary"]:
                            entry["error"] = "not scanned yet: out of time"
                        continue
                    version = api.newest_version(hit["project_id"], rules)
                    if not version:
                        entry.update(version=None, summary=None, missing=None,
                                     error="no file for %s with %s" % (rules["game_version"], rules["loader"]))
                        continue
                    entry["version"] = version
                    entry["error"] = ""
                version = entry["version"]
                path_in_cache = cache_path_for(version)
                needs = load_json(path_in_cache, None) if path_in_cache else None
                if needs and (needs.get("scope") != list(rules["scope"]) or needs.get("data") is None
                              or (needs.get("mixins") and needs.get("mixin_targets") is None)):
                    needs = None  # collected with another scope, before data facts or mixins' targets: read again
                if needs and needs.get("needs_classes") is not None:
                    stats["cached"] += 1
                elif out_of_time:
                    stats["skipped"] += 1
                    if not entry["summary"]:
                        entry["error"] = "not scanned yet: out of time"
                    continue
                else:
                    if version["file"]["size"] > max_bytes:
                        raise TooBig()
                    pending[pool.submit(fetch_needs, version, rules, max_bytes, path_in_cache, args.keep_jars)] = (slug, entry)
                    if len(pending) >= max(1, args.jobs) * 2:
                        settle(pending, stats, fresh, coverage, today, wait_for_one=True)
                    continue
                entry["summary"] = summarize(needs)
                entry["scanned"] = today
                entry["error"] = ""
                fresh[slug] = missing(needs, coverage)
            except TooBig:
                entry["error"] = "file too big to scan (%d bytes)" % entry["version"]["file"]["size"]
                entry["summary"] = None
                entry["missing"] = None
                stats["errors"] += 1
            except Exception as error:  # one broken mod must not stop the scan
                entry["error"] = "could not read the file: %s" % str(error)[:160]
                entry["summary"] = None
                entry["missing"] = None
                stats["errors"] += 1
        settle(pending, stats, fresh, coverage, today, wait_for_one=False)
        if not args.limit and not only:
            for slug in list(projects):
                if slug not in seen:
                    del projects[slug]
    pool.shutdown(wait=True)

    # Statuses and the site's files.
    verified_by_slug = {v["modrinth"]: v for v in verified if v.get("modrinth")}
    index = []
    details = {}
    counts = {}
    top_missing = {}
    used_ids = set()
    for slug, entry in sorted(projects.items()):
        mod_id = safe_id(slug)
        if mod_id in used_ids:  # two slugs that differ only in punctuation: the later one carries its project id
            mod_id = (safe_id(slug) + "-" + str(entry.get("id", "")).lower())[:80]
        used_ids.add(mod_id)
        summary = entry.get("summary") or {}
        version = entry.get("version") or {}
        if slug in fresh:
            missing_classes, missing_members = fresh[slug]
            entry["missing"] = {
                "classes": missing_classes[:MISSING_CAP],
                "members": missing_members[:MISSING_CAP],
                "unmapped": sum(1 for c in missing_classes if not stage_for(c, rules)) +
                            sum(1 for m in missing_members if not stage_for(m.split(" ", 1)[0], rules)),
                "total": len(missing_classes) + len(missing_members),
            }
        else:
            stored = entry.get("missing") or {}
            missing_classes, missing_members = list(stored.get("classes", [])), list(stored.get("members", []))
        for symbol in missing_classes:
            top_missing[symbol] = top_missing.get(symbol, 0) + 1
        for member in missing_members:
            owner, name, _desc = member.split(" ", 2)
            key = owner + "#" + name
            top_missing[key] = top_missing.get(key, 0) + 1
        run = run_for(entry, runs)
        # A run that passed but reached generated stand-ins (Kiln has the name, nothing behind it): those calls
        # count as lacking, so the mod is not "Should work"; the part of it that made them may not work.
        name_only = list(run.get("name_only") or []) if run and run.get("passed") else []
        for call in name_only:
            owner, _, name = call.rpartition(".")
            member = "%s %s (" % (owner.replace(".", "/"), name)
            if owner and member not in missing_members:
                missing_members.append(member)
                key = owner.replace(".", "/") + "#" + name
                top_missing[key] = top_missing.get(key, 0) + 1
        dependency_missing = []  # classes of other mods or libraries the stand did not have
        if run and run.get("ran") and not run.get("passed"):
            # What the run tripped over counts as lacking when it is Minecraft's, Fabric's or Mojang's;
            # a class from another mod means the stand ran without that dependency, which is not Kiln's gap.
            failed = " ".join(list((run.get("entrypoints") or {}).get("failed", {}).values()) + [run.get("why", "")])
            for symbol in symbols_in(failed):
                if symbol in missing_classes or coverage.has_class(symbol):
                    continue
                if any(symbol.startswith(prefix) for prefix in rules["scope"]):
                    missing_classes.append(symbol)
                elif not symbol.startswith("java/"):
                    dependency_missing.append(symbol)
        if summary:
            status, stage, needs_line, readable, evidence = derive_status(
                summary, missing_classes, missing_members, entry, rules, kiln)
        else:
            status, stage, needs_line, readable, evidence = "not-checked", "", "", [], entry.get("error", "")
        server_side = bool(summary) and entry.get("client_side") == "unsupported"
        if server_side:
            evidence = ("Server-side mod: Kiln runs mods' code on Horizon's single-player server, not on dedicated servers. "
                        + (evidence or "")).strip()
        if run and run.get("ran"):
            when = " (%s)" % run["date"] if run.get("date") else ""
            # Kiln applies mods' mixins on the stand as in the game (D-089): only points inside the game's methods and an
            # access widener stay in doubt.
            uses_mixin = bool(summary.get("access_widener") or (summary.get("mixin_inside") if "mixin_inside" in summary
                                                                 else summary.get("mixins") or summary.get("mixin_annotations")))
            if run.get("passed") and name_only:
                shown = ", ".join(".".join(n.split(".")[-2:]) for n in name_only[:3])
                evidence = ("Ran on Kiln's test stand%s: it started and ran %d ticks without errors, but reached "
                            "%d thing%s Kiln has only the name of (%s%s), which do nothing yet: those parts of it "
                            "may not work in the game." % (
                                when, run.get("ticks", 0), len(name_only), "" if len(name_only) == 1 else "s",
                                shown, ", ..." if len(name_only) > 3 else ""))
            elif run.get("passed") and not uses_mixin:
                status = "may-work"
                stage = ""
                evidence = "Ran on Kiln's test stand%s: %s." % (when, run.get("why", "it ran"))
            elif run.get("passed"):
                evidence = ("Ran on Kiln's test stand%s: %s. Its mixins that hook points inside the game's methods, or "
                            "its access widener, may not apply, so that part of it may be missing." % (
                                when, run.get("why", "it ran")))
            elif dependency_missing and summary.get("nested_jars"):
                evidence = ("Ran on Kiln's test stand%s: it stopped at %s, a class from a jar nested inside the mod; "
                            "Kiln does not unpack nested jars yet." % (when, dotted(dependency_missing[0])))
            elif dependency_missing:
                evidence = ("Ran on Kiln's test stand%s without the mods it depends on: it stopped at %s, "
                            "a class from another mod or library the stand did not have." % (when, dotted(dependency_missing[0])))
            else:
                evidence = "Ran on Kiln's test stand%s: %s." % (when, run.get("why", "it did not run"))
        elif run and run.get("why"):
            evidence = evidence or ("Not run: " + run["why"] + ".")
        entry["status"] = status
        entry["stage"] = stage
        entry["candidate"] = bool(summary.get("classes")) and status == "not-checked" and not (entry.get("missing") or {}).get("total")
        entry["run"] = {"passed": bool(run.get("passed")), "date": run.get("date", ""), "why": run.get("why", "")} if run else None

        detail = {
            "id": mod_id,
            "name": entry["title"],
            "version": version.get("number", ""),
            "status": status,
            "statusLabel": LABELS[status],
            "statusMeaning": MEANINGS[status],
            "url": "https://modrinth.com/mod/" + slug,
            "author": entry.get("author", ""),
            "description": entry.get("description", ""),
            "needs": readable,
            "roadmap": stage,
            "evidence": evidence or entry.get("error", ""),
            "library": bool(entry.get("library")),
            "scanned": {"date": entry.get("scanned", ""), "kiln": kiln, "file": version.get("file", {}).get("name", ""),
                        "published": version.get("published", "")},
            "reports": {"works": 0, "partly": 0, "broken": 0, "latest": []},
        }
        if run:
            detail["run"] = {"passed": bool(run.get("passed")), "date": run.get("date", ""), "kiln": run.get("kiln", ""),
                             "ticks": run.get("ticks", 0), "hud": bool(run.get("hud")), "why": run.get("why", "")}
        if summary:
            detail["entrypoints"] = summary.get("entrypoints", {})
            detail["mixins"] = bool(summary.get("mixins") or summary.get("mixin_annotations"))
            detail["depends"] = summary.get("depends", [])
        row = {"id": mod_id, "name": entry["title"], "version": detail["version"], "status": status}
        if server_side:
            row["note"] = "Server-side mod"
        if summary and not summary.get("classes") and status == "may-work":
            detail["statusMeaning"] = "Its content is data; Kiln 0.2.0 generates its structures in Horizon's worlds. Not tried in the game."
            row["data"] = True
        stored = entry.get("missing") or {}
        if run and run.get("ran"):
            row["run"] = ("passed" if run.get("passed") else
                          "nested" if dependency_missing and summary.get("nested_jars") else
                          "needs-deps" if dependency_missing else "failed")
        if status in ("planned", "no-plans") and stored.get("total"):
            row["lacks"] = stored.get("unmapped", 0) if status == "no-plans" else stored.get("total", 0)
        if stage:
            row["stage"] = stage
        if entry.get("candidate"):
            row["candidate"] = True
        if entry.get("library"):
            row["library"] = True
        detail["lacks"] = stored.get("total", 0)

        hand = verified_by_slug.get(slug)
        if hand:
            for key in ("id", "name", "version", "status", "works", "note", "url", "checked"):
                if hand.get(key):
                    detail[key] = hand[key]
            detail["statusLabel"] = LABELS.get(detail["status"], detail["status"])
            detail["statusMeaning"] = MEANINGS.get(detail["status"], "")
            detail["needs"] = []
            detail["roadmap"] = ""
            detail["evidence"] = ""
            row = {"id": detail["id"], "name": detail["name"], "version": detail["version"], "status": detail["status"]}
            if hand.get("works"):
                row["works"] = hand["works"]
            if hand.get("note"):
                row["note"] = hand["note"]
            entry["status"] = detail["status"]
        index.append(row)
        details[row["id"]] = detail
        counts[row["status"]] = counts.get(row["status"], 0) + 1

    for hand in verified:
        if hand.get("modrinth") and hand["modrinth"] in projects:
            continue
        mod_id = safe_id(hand["id"])
        status = hand.get("status", "works")
        detail = {
            "id": mod_id,
            "name": hand.get("name", mod_id),
            "version": hand.get("version", ""),
            "status": status,
            "statusLabel": LABELS.get(status, status),
            "statusMeaning": MEANINGS.get(status, ""),
            "needs": [],
            "reports": {"works": 0, "partly": 0, "broken": 0, "latest": []},
        }
        for key in ("works", "note", "url", "checked", "author", "description"):
            if hand.get(key):
                detail[key] = hand[key]
        row = {"id": mod_id, "name": detail["name"], "version": detail["version"], "status": status}
        if hand.get("works"):
            row["works"] = hand["works"]
        if hand.get("note"):
            row["note"] = hand["note"]
        index.append(row)
        details[mod_id] = detail
        counts[status] = counts.get(status, 0) + 1

    index.sort(key=lambda r: r["name"].lower())
    state["schema"] = 1
    state["updated"] = today
    state["kiln"] = kiln
    state["game_version"] = rules["game_version"]
    state["counts"] = counts
    state["top_missing"] = sorted(top_missing.items(), key=lambda kv: (-kv[1], kv[0]))[:200]
    write_json(args.state, state)

    mods_dir = os.path.join(args.out, "mods")
    if os.path.isdir(mods_dir):
        shutil.rmtree(mods_dir)
    os.makedirs(mods_dir)
    for mod_id, detail in details.items():
        write_json(os.path.join(mods_dir, mod_id + ".json"), detail)
    write_json(os.path.join(args.out, "stats.json"), {
        "updated": today,
        "kiln": kiln,
        "game": rules["game_version"],
        "channel": args.channel,
        "projects": len(projects),
        "counts": counts,
        "errors": sum(1 for e in projects.values() if e.get("error")),
        "top_missing": [{"symbol": symbol, "mods": n} for symbol, n in state["top_missing"]],
    })
    write_json(os.path.join(args.out, "mods.json"), {
        "schema": 2,
        "updated": today,
        "kiln": kiln,
        "game": rules["game_version"],
        "statuses": LABELS,
        "channel": args.channel,
        "mods": index,
    })

    print("projects in search: %d; files fetched: %d; from cache: %d; skipped: %d; errors: %d; API calls: %d; %.1f min" % (
        stats["hits"], stats["fetched"], stats["cached"], stats["skipped"], stats["errors"], api.calls, (time.time() - started) / 60))
    print("catalogue: %d mods" % len(index), ", ".join("%s %d" % (k, v) for k, v in sorted(counts.items())))
    print("most often lacking:")
    for symbol, n in state["top_missing"][:25]:
        print("  %4d  %s" % (n, symbol))
    return 0


if __name__ == "__main__":
    sys.exit(main())
