#!/bin/sh
# Builds Kiln's Java side and the Java mods, with the JDK on PATH (Java 25), and curl:
#   target/kiln/kiln-runtime.jar              Kiln's own classes and its Minecraft and Fabric stand-ins (kiln/java/,
#                                             and the generated ones in kiln/java-gen/)
#   target/kiln/lib/                          the open-source libraries Minecraft ships (kiln/lib/libraries.txt),
#                                             downloaded, checked against their SHA-256, with their licenses
#   target/kiln/mods/kiln-hello.jar           the spike's Fabric mod (spike/hello-mod/)
#   target/kiln/mods/kiln-timer-<version>.jar the speedrun timer, built by Gradle against Minecraft 26.3, where
#                                             mods/kiln-timer is (not in public releases, D-097)
#   target/kiln/testbench/kiln-testbench-<version>.jar  the testbench (mods/kiln-testbench), apart: put it in mods/
#                                             to measure Kiln
#   target/kiln/proof/kiln-proof-<name>.jar   Kiln's proof mods (kiln/proof-mods/), for kiln/proof.py
#   target/kiln/test/kiln-test-<name>.jar     the mods Kiln's own tests run (kiln/test-mods/), never a game
# Copy target/kiln/ to <game folder>/kiln/ to run them in Horizon.
set -eu
root=$(cd "$(dirname "$0")/.." && pwd)
out="$root/target/kiln"
classes="$root/target/java"
rm -rf "$out" "$classes"
mkdir -p "$out/mods" "$out/lib" "$out/testbench" "$classes/runtime" "$classes/hello"

# Downloads are kept in target/libs-cache; a jar whose SHA-256 isn't the pinned one stops the build.
cache="$root/target/libs-cache"
mkdir -p "$cache"
if command -v shasum >/dev/null 2>&1; then sha256="shasum -a 256"; else sha256=sha256sum; fi
grep -v '^#' "$root/kiln/lib/libraries.txt" | while read -r coordinates sum license repository; do
    [ -n "$coordinates" ] || continue
    group=${coordinates%%:*}
    rest=${coordinates#*:}
    artifact=${rest%%:*}
    version=${rest#*:}
    jar="$artifact-$version.jar"
    if [ ! -f "$cache/$jar" ]; then
        curl -fsSL -o "$cache/$jar.part" \
            "${repository:-https://repo1.maven.org/maven2}/$(echo "$group" | tr . /)/$artifact/$version/$jar"
        mv "$cache/$jar.part" "$cache/$jar"
    fi
    if [ "$($sha256 "$cache/$jar" | cut -d ' ' -f 1)" != "$sum" ]; then
        echo "$jar: not the SHA-256 in kiln/lib/libraries.txt" >&2
        rm -f "$cache/$jar"
        exit 1
    fi
    [ -f "$root/kiln/lib/licenses/$license" ] || { echo "$jar: no license $license" >&2; exit 1; }
    cp "$cache/$jar" "$out/lib/"
done
cp "$root/kiln/lib/libraries.txt" "$out/lib/"
cp -R "$root/kiln/lib/licenses" "$out/lib/"
libs=$(find "$out/lib" -name '*.jar' | sort | tr '\n' :)

# Kiln's hand-written stand-ins and the generated ones (kiln/java-gen, tools/kiln-stubs.py): thousands of files, so
# through an argument file. javac reads it as written, and on Windows Git Bash makes Windows paths of arguments only
# (`/c/Users/...` in the file is `\c\Users\...` to javac): there, its lines go through cygpath; elsewhere, as they are.
windows_paths() {
    if command -v cygpath >/dev/null 2>&1; then cygpath -w -f -; else cat; fi
}
find "$root/kiln/java" -name '*.java' | windows_paths > "$classes/sources.txt"
[ ! -d "$root/kiln/java-gen" ] || find "$root/kiln/java-gen" -name '*.java' | windows_paths >> "$classes/sources.txt"
javac --release 21 -nowarn -encoding UTF-8 -cp "$libs" -d "$classes/runtime" @"$classes/sources.txt"
# Its service files: Mixin's global properties, Kiln's (kiln/java/kiln/MixinService.java).
cp -R "$root/kiln/java/META-INF" "$classes/runtime/"
# The runtime is a Java agent too (Kiln's Mixin, kiln/java/kiln/Mixins.java): Kiln starts Java with -javaagent on it.
printf 'Premain-Class: kiln.Mixins\n' > "$classes/manifest.txt"
jar cfm "$out/kiln-runtime.jar" "$classes/manifest.txt" -C "$classes/runtime" .

hello="$root/spike/hello-mod/src/main"
javac --release 21 -cp "$classes/runtime" -d "$classes/hello" $(find "$hello/java" -name '*.java')
cp "$hello/resources/fabric.mod.json" "$classes/hello/"
jar cf "$out/mods/kiln-hello.jar" -C "$classes/hello" .

# Kiln's proof mods (kiln/proof-mods/): only kiln/proof.py puts them in a game.
for proof in "$root"/kiln/proof-mods/*/; do
  name=$(basename "$proof")
  mkdir -p "$classes/proof-$name" "$out/proof"
  javac --release 21 -cp "$classes/runtime:$libs" -d "$classes/proof-$name" $(find "$proof/java" -name '*.java')
  cp "$proof"/*.json "$classes/proof-$name/"
  jar cf "$out/proof/kiln-proof-$name.jar" -C "$classes/proof-$name" .
done

# The mods Kiln's tests run (kiln/test-mods/): a mod that fails to start, a mod's player events, a mod's key. Only
# kiln/tests/ puts them in a game folder.
for mod in "$root"/kiln/test-mods/*/; do
  name=$(basename "$mod")
  mkdir -p "$classes/test-$name" "$out/test"
  javac --release 21 -cp "$classes/runtime:$libs" -d "$classes/test-$name" $(find "$mod/java" -name '*.java')
  cp "$mod"/*.json "$classes/test-$name/"
  [ ! -d "$mod/assets" ] || cp -R "$mod/assets" "$classes/test-$name/"
  jar cf "$out/test/kiln-test-$name.jar" -C "$classes/test-$name" .
done

# build/libs keeps the jars of earlier versions: empty it, so only this version's jar is copied.
rm -rf "$root/mods/kiln-timer/build/libs" "$root/mods/kiln-testbench/build/libs"
if [ -d "$root/mods/kiln-timer" ]; then
  (cd "$root/mods/kiln-timer" && ./gradlew build --quiet --console=plain)
  cp "$root"/mods/kiln-timer/build/libs/kiln-timer-*.jar "$out/mods/"
fi
(cd "$root/mods/kiln-testbench" && ./gradlew build --quiet --console=plain)
cp "$root"/mods/kiln-testbench/build/libs/kiln-testbench-*.jar "$out/testbench/"

echo "$out"
