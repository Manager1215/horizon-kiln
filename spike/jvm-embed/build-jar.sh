#!/bin/sh
# Builds the spike's jar (target/hello.jar) from java/ with the JDK on PATH.
set -eu
here=$(cd "$(dirname "$0")" && pwd)
out="$here/target/java"
rm -rf "$out" && mkdir -p "$out"
javac --release 21 -d "$out" $(find "$here/java" -name '*.java')
jar cf "$here/target/hello.jar" -C "$out" .
echo "$here/target/hello.jar"
