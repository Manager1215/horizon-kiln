#!/bin/sh
# Builds a release's files into dist/ (needs what tools/build-java.sh needs, zip and unzip, and
# .deps/horizon-mods with native/kiln linked to kiln/, as README says):
#   dist/horizon-kiln-<release>.zip   kiln-native/ (the unpacked .hnmod), kiln-<version>.hnmod, kiln/ (for the
#                                     game folder: kiln-runtime.jar, mods/kiln-timer-*.jar), INSTALL.md, LICENSE
#   dist/kiln-timer-<release>.jar     the timer alone, for Minecraft 26.3 with Fabric
#   dist/SHA256SUMS
set -eu
root=$(cd "$(dirname "$0")/.." && pwd)
release=${1:-0.1.0-alpha}
version=$(sed -n 's/^version = "\(.*\)"$/\1/p' "$root/kiln/mixins/kiln/horizon-mixin.toml")

sh "$root/tools/build-java.sh" >/dev/null
(cd "$root/.deps/horizon-mods" && python3 native/tools/native.py package kiln >/dev/null)

dist="$root/dist"
out="$dist/horizon-kiln-$release"
rm -rf "$dist"
mkdir -p "$out/kiln-native" "$out/kiln/mods"
cp "$root/.deps/horizon-mods/native/target/kiln-$version.hnmod" "$out/"
(cd "$out/kiln-native" && unzip -q "../kiln-$version.hnmod")
cp "$root/target/kiln/kiln-runtime.jar" "$out/kiln/"
cp "$root"/target/kiln/mods/kiln-timer-*.jar "$out/kiln/mods/"
cp "$root/INSTALL.md" "$root/LICENSE" "$out/"
(cd "$dist" && zip -qr "horizon-kiln-$release.zip" "horizon-kiln-$release")
cp "$root"/target/kiln/mods/kiln-timer-*.jar "$dist/"
# shasum comes with macOS; on Linux it may be missing where sha256sum (coreutils) is not.
if command -v shasum >/dev/null 2>&1; then sha256="shasum -a 256"; else sha256=sha256sum; fi
(cd "$dist" && $sha256 ./*.zip ./*.jar | sed 's| \./| |' > SHA256SUMS)
cat "$dist/SHA256SUMS"
