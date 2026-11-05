#!/bin/sh
# Mutation testing of Kiln's Rust (cargo-mutants, https://mutants.rs): it breaks Kiln's code a little at a time (a
# `<` for a `<=`, a function that returns nothing) and runs the tests on each break. A break no test notices is a test
# to write, or, when the code does the same with it, a line in .cargo/mutants.toml saying why.
#
#   sh tools/mutants.sh        the unit tests, on every break (minutes)
#   sh tools/mutants.sh java   then the breaks they missed, against the tests with real Java too (a JDK; set
#                              KILN_COVERAGE and KILN_RESOURCES as for those tests, README "Building and trying it")
#
# What no test caught is in mutants.out/missed.txt, each break's diff and test output in mutants.out/. Needs
# `cargo install --locked cargo-mutants`.
set -eu
root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root"
if ! command -v cargo-mutants >/dev/null; then
    echo "needs cargo-mutants: cargo install --locked cargo-mutants" >&2
    exit 1
fi
case "${1:-}" in
"")
    cargo mutants -p horizon-kiln-mod --jobs 4
    ;;
java)
    # The tests find Kiln's jars in target/kiln: each copy of the tree takes target/ along. Two at a time, so Java
    # answers in time on a laptop.
    sh tools/build-java.sh
    cargo mutants -p horizon-kiln-mod --iterate --copy-target=true --jobs 2 -- -- --include-ignored
    ;;
*)
    echo "usage: sh tools/mutants.sh [java]" >&2
    exit 2
    ;;
esac
