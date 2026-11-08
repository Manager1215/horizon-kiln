#!/usr/bin/env python3
"""Breaks for Kiln's Java (kiln/java), one small change to the source each: a method that does nothing or returns
0, false, null or empty, a condition turned round, a boundary moved, && for ||, + for -, a ! dropped, a literal
flipped, a call left out.

  python3 mutate.py gen <kiln/java> <coverage.xml> <mutants.jsonl>    every break, marked covered when JaCoCo's
                                                                       report says the testbench ran its line
  python3 mutate.py show <kiln/java> <mutants.jsonl> <id>             one break, before and after

Each mutant is one small change to one file: {id, file, line, op, what, start, end, text, covered}. `start`/`end`
are character offsets in the file, `text` what replaces them. Mutants that don't compile are dropped when run.
"""
import json
import os
import re
import sys
import xml.etree.ElementTree as ET

KEYWORDS = set("""abstract assert boolean break byte case catch char class const continue default do double else enum
extends final finally float for goto if implements import instanceof int interface long native new package private
protected public return short static strictfp super switch synchronized this throw throws transient try void volatile
while var record yield sealed permits non-sealed""".split())
MODIFIERS = set("public protected private static final abstract synchronized native default strictfp transient".split())
PRIMS = {"boolean", "byte", "short", "int", "long", "char", "float", "double"}
OPS3 = [">>>=", "<<=", ">>=", ">>>", "...", "->", "::", "++", "--", "&&", "||", "==", "!=", "<=", ">=", "+=", "-=",
        "*=", "/=", "&=", "|=", "^=", "%=", "<<"]


class Tok:
    __slots__ = ("kind", "text", "start", "end", "line")

    def __init__(self, kind, text, start, end, line):
        self.kind, self.text, self.start, self.end, self.line = kind, text, start, end, line

    def __repr__(self):
        return f"{self.kind}:{self.text}@{self.line}"


def lex(src):
    toks = []
    i, n, line = 0, len(src), 1
    while i < n:
        c = src[i]
        if c == "\n":
            line += 1
            i += 1
        elif c.isspace():
            i += 1
        elif src.startswith("//", i):
            j = src.find("\n", i)
            i = n if j < 0 else j
        elif src.startswith("/*", i):
            j = src.find("*/", i + 2)
            j = n if j < 0 else j + 2
            line += src.count("\n", i, j)
            i = j
        elif src.startswith('"""', i):
            j = src.find('"""', i + 3)
            while j > 0 and src[j - 1] == "\\":
                j = src.find('"""', j + 1)
            j = n if j < 0 else j + 3
            toks.append(Tok("str", src[i:j], i, j, line))
            line += src.count("\n", i, j)
            i = j
        elif c in "\"'":
            j = i + 1
            while j < n and src[j] != c:
                j += 2 if src[j] == "\\" else 1
            j += 1
            toks.append(Tok("str" if c == '"' else "chr", src[i:j], i, j, line))
            i = j
        elif c.isalpha() or c in "_$":
            j = i + 1
            while j < n and (src[j].isalnum() or src[j] in "_$"):
                j += 1
            w = src[i:j]
            toks.append(Tok("kw" if w in KEYWORDS else ("bool" if w in ("true", "false") else
                             ("null" if w == "null" else "id")), w, i, j, line))
            i = j
        elif c.isdigit() or (c == "." and i + 1 < n and src[i + 1].isdigit()):
            m = re.compile(r"0[xX][0-9a-fA-F_]+[lL]?|[0-9][0-9_]*\.?[0-9_]*([eE][+-]?[0-9]+)?[fFdDlL]?|\.[0-9]+[fFdD]?").match(src, i)
            j = m.end()
            toks.append(Tok("num", src[i:j], i, j, line))
            i = j
        else:
            for op in OPS3:
                if src.startswith(op, i):
                    toks.append(Tok("op", op, i, i + len(op), line))
                    i += len(op)
                    break
            else:
                toks.append(Tok("op", c, i, i + 1, line))
                i += 1
    return toks


def matching(toks, k, open_, close):
    """Index of the token closing toks[k] (an `open_`)."""
    depth = 0
    for j in range(k, len(toks)):
        if toks[j].text == open_:
            depth += 1
        elif toks[j].text == close:
            depth -= 1
            if depth == 0:
                return j
    return None


def matching_back(toks, k, open_, close):
    depth = 0
    for j in range(k, -1, -1):
        if toks[j].text == close:
            depth += 1
        elif toks[j].text == open_:
            depth -= 1
            if depth == 0:
                return j
    return None


def methods(toks):
    """(kind, name, return type text, body `{` index, body `}` index) for every method with a body, constructors
    included (kind "ctor"). Class bodies are found by their braces; a method is `type name(...) [throws ...] {` in one."""
    out = []
    stack = []  # "class" or "block" per open brace
    pending_class = False
    for k, t in enumerate(toks):
        if t.text in ("class", "interface", "enum", "record") and t.kind == "kw" and not (k and toks[k - 1].text == "."):
            pending_class = True
        if t.text == "{":
            kind = "block"
            if pending_class:
                kind = "class"
                pending_class = False
            elif stack and stack[-1] == "class":
                m = method_head(toks, k)
                if m:
                    mk, name, rtype = m
                    out.append((mk, name, rtype, k, matching(toks, k, "{", "}")))
                    kind = "method"
                elif toks[k - 1].text == ")":
                    # an enum constant's body: CONST(...) {
                    kind = "class"
                elif toks[k - 1].kind == "id" and k >= 2 and toks[k - 2].text in (",", ";", "{"):
                    kind = "class"
            elif toks[k - 1].text == ")":
                p = matching_back(toks, k - 1, "(", ")")
                j = p - 1 if p else -1
                # new Foo<...>(...) {  : an anonymous class
                if j >= 0 and toks[j].text == ">":
                    j = matching_back(toks, j, "<", ">")
                    j = j - 1 if j is not None else -1
                while j >= 1 and toks[j].kind == "id" and toks[j - 1].text == ".":
                    j -= 2
                if j >= 1 and toks[j].kind == "id" and toks[j - 1].text == "new":
                    kind = "class"
            stack.append(kind)
        elif t.text == "}":
            if stack:
                stack.pop()
        elif t.text == ";" and pending_class:
            pass
    return out


def method_head(toks, k):
    """If toks[k] (a `{` in a class body) opens a method's body: (kind, name, return type)."""
    j = k - 1
    if toks[j].kind == "id" or toks[j].text == ">":
        # throws A, B.C<..>
        q = j
        while q > 0 and toks[q].text != "throws" and toks[q].text not in (")", "{", "}", ";"):
            q -= 1
        if toks[q].text == "throws":
            j = q - 1
    if toks[j].text != ")":
        return None
    p = matching_back(toks, j, "(", ")")
    if p is None or p < 2:
        return None
    name = toks[p - 1]
    if name.kind != "id":
        return None
    before = toks[p - 2]
    if before.text == "void":
        return ("method", name.text, "void")
    if before.text in PRIMS:
        return ("method", name.text, before.text)
    if before.text == "]":
        return ("method", name.text, "array")
    if before.text == ">":
        q = matching_back(toks, p - 2, "<", ">")
        # <T> void m(...)  vs  List<String> m(...)
        if q is not None and toks[q - 1].kind == "id" and toks[q - 1].text not in MODIFIERS:
            return ("method", name.text, toks[q - 1].text)
        return None
    if before.kind == "id":
        return ("method", name.text, before.text)
    if before.text in MODIFIERS or before.text in (";", "}", "{") or before.text == ")":
        return ("ctor", name.text, None)
    return None


EMPTY = {
    "String": ['""'],
    "List": ["java.util.List.of()"],
    "Collection": ["java.util.List.of()"],
    "Iterable": ["java.util.List.of()"],
    "Set": ["java.util.Set.of()"],
    "Map": ["java.util.Map.of()"],
    "Optional": ["java.util.Optional.empty()"],
    "Stream": ["java.util.stream.Stream.empty()"],
}


def body_text(src, toks, a, b):
    return " ".join(t.text for t in toks[a + 1:b])


def gen_file(path, rel, covered):
    src = open(path, encoding="utf-8").read()
    toks = lex(src)
    muts = []

    def add(line, op, what, start, end, text):
        muts.append({"file": rel, "line": line, "op": op, "what": what, "start": start, "end": end, "text": text,
                     "covered": line in covered})

    ms = methods(toks)
    for kind, name, rtype, a, b in ms:
        if b is None or kind == "ctor":
            continue
        body = body_text(src, toks, a, b)
        line = toks[a].line
        # the first line of the body that has code: where coverage says whether the method ran
        cline = toks[a + 1].line if a + 1 < b else line
        start, end = toks[a].end, toks[b].start
        if rtype == "void":
            if body:
                add(cline, "void_empty", f"{name}(): does nothing", start, end, " ")
            continue
        if rtype == "boolean":
            vals = ["true", "false"]
        elif rtype in ("byte", "short", "char"):
            vals = [f"({rtype}) 0"]
        elif rtype in PRIMS:
            vals = ["0"]
        else:
            vals = ["null"] + EMPTY.get(rtype, [])
        for v in vals:
            if body.replace(" ", "") == f"return{v};".replace(" ", ""):
                continue
            add(cline, "return", f"{name}(): returns {v}", start, end, f" return {v}; ")

    # Expression-level changes, inside method bodies and lambdas alike.
    for k, t in enumerate(toks):
        prev = toks[k - 1] if k else None
        nxt = toks[k + 1] if k + 1 < len(toks) else None
        sp_before = prev is not None and src[prev.end:t.start] != ""
        sp_after = nxt is not None and src[t.end:nxt.start] != ""
        if t.kind == "op":
            swap = {"==": "!=", "!=": "==", "&&": "||", "||": "&&", "++": "--", "--": "++"}
            if t.text in swap:
                add(t.line, "negate" if t.text in ("==", "!=") else ("logic" if t.text in ("&&", "||") else "incr"),
                    f"{t.text} -> {swap[t.text]}", t.start, t.end, swap[t.text])
            elif t.text in ("<", ">", "<=", ">=") and sp_before and sp_after:
                neg = {"<": ">=", ">": "<=", "<=": ">", ">=": "<"}[t.text]
                bnd = {"<": "<=", ">": ">=", "<=": "<", ">=": ">"}[t.text]
                add(t.line, "negate", f"{t.text} -> {neg}", t.start, t.end, neg)
                add(t.line, "boundary", f"{t.text} -> {bnd}", t.start, t.end, bnd)
            elif t.text in ("+", "-", "*", "/", "%") and sp_before and sp_after and prev.kind != "str" and nxt.kind != "str":
                to = {"+": "-", "-": "+", "*": "/", "/": "*", "%": "*"}[t.text]
                add(t.line, "math", f"{t.text} -> {to}", t.start, t.end, to)
            elif t.text == "!" and nxt is not None:
                add(t.line, "not", "drops a !", t.start, t.end, "")
        elif t.kind == "bool":
            to = "false" if t.text == "true" else "true"
            add(t.line, "literal", f"{t.text} -> {to}", t.start, t.end, to)

    # Calls as statements, removed: `foo.bar(x);`
    for k, t in enumerate(toks):
        if t.kind not in ("id",) and t.text != "this":
            continue
        if k == 0 or toks[k - 1].text not in (";", "{", "}"):
            continue
        # only statements inside a method body or lambda: the enclosing braces are a method's (rough: a `;` before)
        j, depth, assign = k, 0, False
        while j < len(toks):
            x = toks[j].text
            if x in "([{":
                depth += 1
            elif x in ")]}":
                depth -= 1
                if depth < 0:
                    break
            elif depth == 0 and (x in ("=", "+=", "-=", "*=", "/=", "|=", "&=", "->", ":") or toks[j].kind == "kw"
                                 and x not in ("this", "new", "super")):
                assign = True
            elif x == ";" and depth == 0:
                break
            j += 1
        if j >= len(toks) or toks[j].text != ";" or assign or toks[j - 1].text != ")":
            continue
        if t.text in ("this", "super") and toks[k + 1].text == "(":
            continue
        # a declaration `Type name(...)` would be a method head, not a statement: those end in `{`, not `;`
        if toks[k + 1].kind == "id" and toks[k + 2].text == "(":
            continue  # `Type name(...)` : an abstract method
        stmt = src[t.start:toks[j].end]
        if "\n" in stmt:
            stmt = stmt.split("\n")[0] + " ..."
        add(t.line, "remove_call", f"removes {stmt[:80]}", t.start, toks[j].end, ";")
    return muts, ms


def covered_lines(coverage_xml):
    cov = {}
    root = ET.parse(coverage_xml).getroot()
    for pkg in root.iter("package"):
        for sf in pkg.findall("sourcefile"):
            rel = pkg.get("name") + "/" + sf.get("name")
            cov[rel] = {int(l.get("nr")) for l in sf.findall("line") if int(l.get("ci")) > 0}
    return cov


def main():
    if sys.argv[1] == "gen":
        java_dir, coverage_xml, out = sys.argv[2:5]
        cov = covered_lines(coverage_xml)
        n = 0
        with open(out, "w") as f:
            for dirpath, _, files in sorted(os.walk(java_dir)):
                for fn in sorted(files):
                    if not fn.endswith(".java"):
                        continue
                    path = os.path.join(dirpath, fn)
                    rel = os.path.relpath(path, java_dir)
                    muts, _ = gen_file(path, rel, cov.get(rel, set()))
                    for m in muts:
                        n += 1
                        m["id"] = n
                        f.write(json.dumps(m) + "\n")
        print(n, "mutants")
    elif sys.argv[1] == "methods":
        for path in sys.argv[2:]:
            toks = lex(open(path).read())
            for m in methods(toks):
                print(m[0], m[1], m[2], toks[m[3]].line)
    elif sys.argv[1] == "show":
        java_dir, mutants, mid = sys.argv[2], sys.argv[3], int(sys.argv[4])
        for l in open(mutants):
            m = json.loads(l)
            if m["id"] == mid:
                src = open(os.path.join(java_dir, m["file"])).read()
                s = src[:m["start"]] + m["text"] + src[m["end"]:]
                a = src.rfind("\n", 0, m["start"] - 1)
                print(m)
                print("-", src[a:src.find("\n", m["end"])])
                print("+", s[a:s.find("\n", m["start"] + len(m["text"]))])


if __name__ == "__main__":
    main()
