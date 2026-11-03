#!/bin/sh
# Builds Kiln's Java side and the Java mods, with the JDK on PATH (Java 25 for the timer), and curl:
#   target/kiln/kiln-runtime.jar              Kiln's own classes and its Minecraft and Fabric stand-ins (kiln/java/)
#   target/kiln/lib/                          the open-source libraries Minecraft ships (kiln/lib/libraries.txt),
#                                             downloaded, checked against their SHA-256, with their licenses
#   target/kiln/mods/kiln-hello.jar           the spike's Fabric mod (spike/hello-mod/)
#   target/kiln/mods/kiln-timer-<version>.jar the speedrun timer, built by Gradle against Minecraft 26.3
#   target/kiln/testbench/kiln-testbench-<version>.jar  the testbench (mods/kiln-testbench), apart: put it in mods/
#                                             to measure Kiln
#   target/kiln/proof/kiln-proof-<name>.jar   Kiln's proof mods (kiln/proof-mods/), for kiln/proof.py
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

javac --release 21 -cp "$libs" -d "$classes/runtime" $(find "$root/kiln/java" -name '*.java')
jar cf "$out/kiln-runtime.jar" -C "$classes/runtime" .

hello="$root/spike/hello-mod/src/main"
javac --release 21 -cp "$classes/runtime" -d "$classes/hello" $(find "$hello/java" -name '*.java')
cp "$hello/resources/fabric.mod.json" "$classes/hello/"
jar cf "$out/mods/kiln-hello.jar" -C "$classes/hello" .

# Kiln's proof mods (kiln/proof-mods/): only kiln/proof.py puts them in a game.
for proof in "$root"/kiln/proof-mods/*/; do
  name=$(basename "$proof")
  mkdir -p "$classes/proof-$name" "$out/proof"
  javac --release 21 -cp "$classes/runtime" -d "$classes/proof-$name" $(find "$proof/java" -name '*.java')
  cp "$proof/fabric.mod.json" "$classes/proof-$name/"
  jar cf "$out/proof/kiln-proof-$name.jar" -C "$classes/proof-$name" .
done

# build/libs keeps the jars of earlier versions: empty it, so only this version's jar is copied.
rm -rf "$root/mods/kiln-timer/build/libs" "$root/mods/kiln-testbench/build/libs"
(cd "$root/mods/kiln-timer" && ./gradlew build --quiet --console=plain)
cp "$root"/mods/kiln-timer/build/libs/kiln-timer-*.jar "$out/mods/"
(cd "$root/mods/kiln-testbench" && ./gradlew build --quiet --console=plain)
cp "$root"/mods/kiln-testbench/build/libs/kiln-testbench-*.jar "$out/testbench/"

echo "$out"
