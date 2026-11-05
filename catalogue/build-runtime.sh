#!/bin/sh
# Builds kiln-runtime.jar and fetches the pinned library jars (kiln/lib/libraries.txt): the first part of
# tools/build-java.sh, without the timer, the hello mod and the proof mods. The stand-ins in kiln/java and the generated
# ones in kiln/java-gen (when present) go in.
#   sh catalogue/build-runtime.sh <repository> <out dir>      ->  <out dir>/kiln-runtime.jar and <out dir>/lib/
set -eu
R="${1:?repository path}"; out="${2:?out dir}"
classes="$out/.classes"; cache="$out/.libs-cache"
mkdir -p "$out/lib" "$classes" "$cache"
if command -v shasum >/dev/null 2>&1; then sha256="shasum -a 256"; else sha256=sha256sum; fi
grep -v '^#' "$R/kiln/lib/libraries.txt" | while read -r coordinates sum license repository; do
  [ -n "$coordinates" ] || continue
  group=${coordinates%%:*}; rest=${coordinates#*:}; artifact=${rest%%:*}; version=${rest#*:}
  jar="$artifact-$version.jar"
  if [ ! -f "$cache/$jar" ]; then
    curl -fsSL -o "$cache/$jar.part" "${repository:-https://repo1.maven.org/maven2}/$(echo "$group" | tr . /)/$artifact/$version/$jar"
    mv "$cache/$jar.part" "$cache/$jar"
  fi
  [ "$($sha256 "$cache/$jar" | cut -d ' ' -f 1)" = "$sum" ] || { echo "$jar: not the SHA-256 pinned in libraries.txt" >&2; exit 1; }
  cp "$cache/$jar" "$out/lib/"
done
libs=$(find "$out/lib" -name '*.jar' | sort | tr '\n' :)
find "$R/kiln/java" -name '*.java' > "$classes/sources.txt"
[ ! -d "$R/kiln/java-gen" ] || find "$R/kiln/java-gen" -name '*.java' >> "$classes/sources.txt"
javac --release 21 -nowarn -encoding UTF-8 -cp "$libs" -d "$classes" @"$classes/sources.txt"
jar cf "$out/kiln-runtime.jar" -C "$classes" .
echo "built $out/kiln-runtime.jar from $(wc -l < "$classes/sources.txt" | tr -d ' ') sources with $(find "$out/lib" -name '*.jar' | wc -l | tr -d ' ') library jars"
