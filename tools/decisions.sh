#!/bin/sh
# The development decisions live one file each in internal/decisions/D-<number>.md (#309), so two pull requests that
# each add a decision never touch the same file: no conflict. This script is the only thing that knows the layout.
#   sh tools/decisions.sh index        one line per decision: file:line:### D-nnn. Title (the old `grep '^### D-'`)
#   sh tools/decisions.sh show <n>     the text of D-<n>
#   sh tools/decisions.sh next         the next free number (the highest in this tree and in origin/main, plus one)
#   sh tools/decisions.sh new "Title"  creates the next file with the usual skeleton and prints its path
#   sh tools/decisions.sh build        every decision in order, as the old single file read (to stdout)
#   sh tools/decisions.sh check        each file is named for the decision it holds; no number twice; none missing
# A section heading (`## 2026-11-18: topic`) may be the first line of the first decision under it; `build` keeps it.
set -eu
cd "$(dirname "$0")/.."
dir=internal/decisions
files() { ls "$dir" | grep -E '^D-[0-9]+\.md$' | sort -V | sed "s|^|$dir/|"; }
numbers() {
  { files | sed -E 's|.*/D-0*([0-9]+)\.md|\1|'
    git ls-tree --name-only origin/main "$dir/" 2>/dev/null | sed -nE 's|.*/D-0*([0-9]+)\.md|\1|p'; } | sort -n | uniq
}
case ${1:-} in
  index) files | xargs grep -n '^### D-';;
  show) n=${2:?usage: decisions.sh show <n>}; cat "$dir/D-$(printf %03d "$n").md";;
  next) echo $(( $(numbers | tail -1) + 1 ));;
  new)
    t=${2:?usage: decisions.sh new \"Title\"}
    n=$(( $(numbers | tail -1) + 1 )); f="$dir/D-$(printf %03d "$n").md"
    printf '### D-%03d. %s\n- **Decision:** \n- **Why:** \n- **Who:** \n' "$n" "$t" > "$f"
    echo "$f";;
  build) for f in $(files); do cat "$f"; echo; done;;
  check)
    bad=0
    for f in $(files); do
      want=$(basename "$f" .md | sed -E 's/^D-0*//')
      got=$(grep -m1 '^### D-' "$f" | sed -E 's/^### D-0*([0-9]+).*/\1/')
      [ "$want" = "$got" ] || { echo "$f holds D-$got"; bad=1; }
      [ "$(grep -c '^### D-' "$f")" = 1 ] || { echo "$f holds more than one decision"; bad=1; }
    done
    last=$(files | wc -l)
    echo "$last decisions"; exit $bad;;
  *) sed -n '2,9p' "$0" | sed 's/^# \{0,1\}//'; exit 2;;
esac
