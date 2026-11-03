#!/bin/sh
# Builds Kiln's Java side and the Java mods, with the JDK on PATH (Java 25 for the timer):
#   target/kiln/kiln-runtime.jar              Kiln's own classes and its Minecraft and Fabric stand-ins (kiln/java/)
#   target/kiln/mods/kiln-hello.jar           the spike's Fabric mod (spike/hello-mod/)
#   target/kiln/mods/kiln-timer-<version>.jar the speedrun timer, built by Gradle against Minecraft 26.3
#   target/kiln/testbench/kiln-testbench-<version>.jar  the testbench (mods/kiln-testbench), apart: put it in mods/
#                                             to measure Kiln
# Copy target/kiln/ to <game folder>/kiln/ to run them in Horizon.
set -eu
root=$(cd "$(dirname "$0")/.." && pwd)
out="$root/target/kiln"
classes="$root/target/java"
rm -rf "$out" "$classes"
mkdir -p "$out/mods" "$out/testbench" "$classes/runtime" "$classes/hello"

javac --release 21 -d "$classes/runtime" $(find "$root/kiln/java" -name '*.java')
jar cf "$out/kiln-runtime.jar" -C "$classes/runtime" .

hello="$root/spike/hello-mod/src/main"
javac --release 21 -cp "$classes/runtime" -d "$classes/hello" $(find "$hello/java" -name '*.java')
cp "$hello/resources/fabric.mod.json" "$classes/hello/"
jar cf "$out/mods/kiln-hello.jar" -C "$classes/hello" .

# build/libs keeps the jars of earlier versions: empty it, so only this version's jar is copied.
rm -rf "$root/mods/kiln-timer/build/libs" "$root/mods/kiln-testbench/build/libs"
(cd "$root/mods/kiln-timer" && ./gradlew build --quiet --console=plain)
cp "$root"/mods/kiln-timer/build/libs/kiln-timer-*.jar "$out/mods/"
(cd "$root/mods/kiln-testbench" && ./gradlew build --quiet --console=plain)
cp "$root"/mods/kiln-testbench/build/libs/kiln-testbench-*.jar "$out/testbench/"

echo "$out"
