"""Writes catalogue/jdk.json: every public or protected class of the JDK's java.base module under java/, with its
superclass, interfaces and public or protected members (names and descriptors only), so the scan finds a member a
Kiln stand-in gets from a JDK class it extends or implements (Iterable.iterator, AbstractList.stream, Map.Entry.getKey).

    python3 -I catalogue/jdk-members.py [JAVA_HOME]      (default: the java on PATH's home)

It reads the class files of java.base.jmod and runs nothing. Run it again for a new JDK; the scan reads the file.
"""
import io
import json
import os
import shutil
import subprocess
import sys
import tempfile
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from classfile import parse_class  # noqa: E402

PUBLIC, PROTECTED, SYNTHETIC = 0x0001, 0x0004, 0x1000


def class_files(home):
    """java.base's class files under java/: from jmods/java.base.jmod, or (a JDK without jmods, 24 on) from
    lib/modules by the JDK's own jimage tool, into a temporary folder."""
    jmod = os.path.join(home, "jmods", "java.base.jmod")
    if os.path.exists(jmod):
        with open(jmod, "rb") as f:
            data = f.read()
        with zipfile.ZipFile(io.BytesIO(data[4:])) as z:
            for entry in z.namelist():
                if entry.startswith("classes/java/") and entry.endswith(".class"):
                    yield z.read(entry)
        return
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.run([os.path.join(home, "bin", "jimage"), "extract", "--include", "regex:/java.base/java/.*",
                        "--dir", tmp, os.path.join(home, "lib", "modules")], check=True)
        for root, _dirs, files in os.walk(os.path.join(tmp, "java.base", "java")):
            for name in sorted(files):
                if name.endswith(".class"):
                    with open(os.path.join(root, name), "rb") as f:
                        yield f.read()


def main():
    if len(sys.argv) > 1:
        home = sys.argv[1]
    else:
        home = os.path.dirname(os.path.dirname(os.path.realpath(shutil.which("java"))))
    classes = {}
    for data in class_files(home):
        if True:
            parsed = parse_class(data)
            if not parsed["access"] & PUBLIC and not parsed["this"].count("$"):
                continue
            members = sorted({n + " " + d for n, d, a in parsed["fields"] + parsed["methods"]
                              if a & (PUBLIC | PROTECTED) and not a & SYNTHETIC})
            classes[parsed["this"]] = {"super": parsed["super"] or "", "interfaces": parsed["interfaces"],
                                       "members": members}
    out = os.path.join(HERE, "jdk.json")
    with open(out, "w") as f:
        json.dump({"jdk": os.path.basename(home), "classes": dict(sorted(classes.items()))}, f, separators=(",", ":"))
        f.write("\n")
    print(len(classes), "classes,", sum(len(c["members"]) for c in classes.values()), "members ->", out)


if __name__ == "__main__":
    main()
