#!/bin/sh
# Mutation testing of Kiln's Java (kiln/java) against the testbench: which probes check what Kiln does, and which
# would pass with Kiln's stand-ins broken ("on paper"). It breaks the stand-ins one small change at a time
# (tools/java-mutants/mutate.py), runs the testbench through Rust on each break (kiln/tests/testbench.rs), and counts
# per probe the breaks it caught and per class the breaks nothing caught (tools/java-mutants/report.py).
#
#   sh tools/java-mutants.sh <work folder> [workers] [all]
#
# Linux only: each run binds its own target/kiln over this tree's in a mount namespace of its own (unshare -rm), so
# several run at once. Build first (sh tools/build-java.sh) and set KILN_RESOURCES and KILN_JAVA_HOME as for the
# testbench test. Only the breaks on lines the testbench runs (JaCoCo's coverage of one run) are tried, unless `all`.
# Each takes about 5 s; a rerun goes on where it stopped. The report lands in <work folder>/report.md.
set -eu
root=$(cd "$(dirname "$0")/.." && pwd)
work=${1:?usage: java-mutants.sh <work folder> [workers] [all]}
workers=${2:-8}
mkdir -p "$work"
work=$(cd "$work" && pwd)
ln -sfn "$root" "$work/repo"
cd "$root"
cargo test -q -p horizon-kiln-mod --test testbench --no-run

# JaCoCo, to know which lines the testbench runs: checked against its SHA-256.
cache="$root/target/libs-cache"
mkdir -p "$cache"
if command -v shasum >/dev/null 2>&1; then sha256="shasum -a 256"; else sha256=sha256sum; fi
for pinned in "org.jacoco.agent 0.8.14 -runtime 3fb76eea65f81bd9415202bab34b6571728841dff1ab8e6bbe81adc2e299face" \
              "org.jacoco.cli 0.8.14 -nodeps 811c7f8c6b358c5d68a8973cfa867f6892be7a671b697a4b13c4b447e6daf75c"; do
    set -- $pinned
    jar="$1-$2$3.jar"
    [ -f "$cache/$jar" ] || curl -fsSL -o "$cache/$jar" "https://repo1.maven.org/maven2/org/jacoco/$1/$2/$jar"
    [ "$($sha256 "$cache/$jar" | cut -d ' ' -f 1)" = "$4" ] || { echo "$jar: not the pinned SHA-256" >&2; rm -f "$cache/$jar"; exit 1; }
done
agent="$cache/org.jacoco.agent-0.8.14-runtime.jar"
cli="$cache/org.jacoco.cli-0.8.14-nodeps.jar"

# One run unbroken, with JaCoCo in Java: Kiln stops Java without a clean exit, so the coverage is fetched from it while
# it runs. Its report is what each break's is held against.
if [ ! -f "$work/coverage.xml" ]; then
    rm -f "$work/jacoco.exec"
    JAVA_TOOL_OPTIONS="-javaagent:$agent=output=tcpserver,port=6399,includes=*" \
        cargo test -q -p horizon-kiln-mod --test testbench -- --ignored --nocapture > "$work/unbroken.log" 2>&1 &
    run=$!
    while kill -0 "$run" 2>/dev/null; do
        java -jar "$cli" dump --port 6399 --destfile "$work/jacoco.exec" --reset --retry 0 >/dev/null 2>&1 || sleep 0.2
    done
    wait "$run" || { echo "the testbench fails unbroken: $work/unbroken.log" >&2; exit 1; }
    cp target/kiln/testbench-report.txt "$work/baseline-report.txt"
    java -jar "$cli" report "$work/jacoco.exec" --classfiles target/java/runtime --sourcefiles kiln/java \
        --sourcefiles kiln/java-gen --xml "$work/coverage.xml" >/dev/null
fi
[ -f "$work/mutants.jsonl" ] || python3 tools/java-mutants/mutate.py gen kiln/java "$work/coverage.xml" "$work/mutants.jsonl"
if [ "${3:-}" = all ]; then which=all; else which=covered; fi
python3 tools/java-mutants/run.py "$work" "$work/mutants.jsonl" "$workers" "$which"
python3 tools/java-mutants/report.py "$work" "$work/baseline-report.txt" kiln/tests/testbench.rs > "$work/report.md"
echo "$work/report.md"
