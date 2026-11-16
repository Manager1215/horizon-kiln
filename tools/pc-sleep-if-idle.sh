#!/bin/sh
# Suspend the Linux PC when no Kiln work runs on it (D-130). Run it from the Mac, from the repository:
#   ssh manager@192.168.3.159 'sh -s' < tools/pc-sleep-if-idle.sh
# It suspends (not powers off): the owner wakes it with `wakeonlan 08:bf:b8:75:84:32`. If anything runs, it says what
# and exits 1 without suspending. The `[x]` in the patterns keeps grep from matching its own command line.
# With --check it only tests: exit 0 and "idle" when nothing runs (tools/ci/auto-sleep.sh uses it on the PC).
busy=""

# Builds, tests and the game by program name.
p=$(ps -eo comm= | grep -E '^(cargo|rustc|rustfmt|cargo-mutants|javac|gradle|horizon.*)$' | sort -u | tr '\n' ' ')
[ -n "$p" ] && busy="$busy programs: $p;"

# A java that is not a build tool's idle daemon (Gradle's and Kotlin's stay up for hours and do nothing).
j=$(ps -eo args= | grep -E '^[^ ]*/[j]ava ' | grep -Ev 'GradleDaemon|KotlinCompileDaemon' | cut -c1-80 | head -2 | tr '\n' '|')
[ -n "$j" ] && busy="$busy java: $j;"

# Kiln's own scripts by command line, a CI check (tools/ci/job.sh) and a daily job (daily.sh, D-147) too; not the CI's minute poll, which is short.
s=$(ps -eo args= | grep -E '[c]heck\.sh|[c]i/job\.sh|[c]i/daily\.sh run|[t]ry\.sh|[g]ame-build\.sh|[b]uild-java\.sh|[p]roof\.py|[c]atalogue/(scan|probe)\.py' | cut -c1-80 | head -3 | tr '\n' '|')
[ -n "$s" ] && busy="$busy scripts: $s;"

# The locks: the games' (D-150) and the Java mutation pass's.
for l in game-run.lock java-tests.lock $(cd "$HOME/kiln-runs" 2>/dev/null && ls game-*.lock 2>/dev/null); do
  if [ -e "$HOME/kiln-runs/$l" ] && ! flock -n "$HOME/kiln-runs/$l" true; then
    busy="$busy $l held;"
  fi
done

# A tmux pane running something but a shell; the Remote Control server's session "rc" is always there.
t=$(tmux list-panes -a -F '#{session_name} #{pane_current_command}' 2>/dev/null \
  | grep -v '^rc ' | grep -Ev ' (bash|zsh|sh|fish|dash)$' | head -3 | tr '\n' '|')
[ -n "$t" ] && busy="$busy tmux: $t;"

if [ -n "$busy" ]; then
  echo "busy, not suspending:$busy"
  exit 1
fi
[ "${1:-}" = --check ] && { echo idle; exit 0; }
echo "idle, suspending"
exec systemctl suspend
