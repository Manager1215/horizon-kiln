#!/bin/sh
# Every ignored test in a process of its own, so each one has Java in its test's process (D-112: a process has one
# JVM, and only the first Java a process starts runs in it). Run from the repository, after sh tools/build-java.sh:
#   KILN_JVM=inprocess sh tools/test-each.sh [filter]
# Prints one line a test and ends with the count; exits 1 when one failed.
set -u
cargo test -p horizon-kiln-mod --no-run -q 2>/dev/null
failed=0 passed=0
for bin in $(cargo test -p horizon-kiln-mod --no-run --message-format=json -q 2>/dev/null \
    | sed -n 's/.*"executable":"\([^"]*\)".*/\1/p'); do
  for name in $("$bin" --list --ignored 2>/dev/null | sed -n 's/: test$//p' | grep -e "${1:-}"); do
    if (cd kiln && "$bin" --ignored --exact "$name" --nocapture) > "${TMPDIR:-/tmp}/test-each.log" 2>&1; then
      passed=$((passed + 1)); echo "ok   $(basename "$bin" | sed 's/-[0-9a-f]*$//') $name"
    else
      failed=$((failed + 1)); echo "FAIL $(basename "$bin" | sed 's/-[0-9a-f]*$//') $name"
      tail -30 "${TMPDIR:-/tmp}/test-each.log" | sed 's/^/     /'
    fi
  done
done
echo "$passed passed, $failed failed"
[ "$failed" -eq 0 ]
