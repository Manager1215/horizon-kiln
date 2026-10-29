#!/bin/sh
# Builds Kiln's Java side with the JDK on PATH:
#   target/kiln/kiln-runtime.jar      Kiln's own classes (kiln/java/)
#   target/kiln/mods/kiln-hello.jar   the spike's Java mod (spike/hello-mod/)
# Copy target/kiln/ to <game folder>/kiln/ to run them in Horizon.
set -eu
root=$(cd "$(dirname "$0")/.." && pwd)
out="$root/target/kiln"
classes="$root/target/java"
rm -rf "$out" "$classes"
mkdir -p "$out/mods" "$classes/runtime" "$classes/hello"

javac --release 21 -d "$classes/runtime" $(find "$root/kiln/java" -name '*.java')
jar cf "$out/kiln-runtime.jar" -C "$classes/runtime" .

hello="$root/spike/hello-mod/src/main"
javac --release 21 -cp "$classes/runtime" -d "$classes/hello" $(find "$hello/java" -name '*.java')
cp "$hello/resources/fabric.mod.json" "$classes/hello/"
jar cf "$out/mods/kiln-hello.jar" -C "$classes/hello" .

echo "$out"
