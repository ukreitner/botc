#!/bin/zsh
# Assign the friction-log characters to seats 1..12 after D_bmr_setup.py.
#
#   ./scenario.py emulator-5560 D_bmr_setup
#   zsh scenarios/D_bmr_assign.sh emulator-5560
#
# Each seat: tap the seat -> "Change..." -> "Search characters" -> type the
# name -> tap the matching picker ROW. The row has to be found in a fresh
# dump because `ui.py tap` matches the search field first (it holds the same
# text), and because the on-screen keyboard covers the list.
set -u
cd "$(dirname "$0")/.."
S="${1:-emulator-5560}"
ADB="$(command -v adb)"

hide_ime () {
  if $ADB -s $S shell dumpsys input_method 2>/dev/null | grep -q "mInputShown=true"; then
    ./ui.py $S back >/dev/null 2>&1; sleep 0.5
  fi
}

row_xy () {  # $1 = exact character name; reads out/<serial>-dump.xml
  python3 - "$1" "out/$S-dump.xml" <<'PY'
import sys, re
import xml.etree.ElementTree as ET
name, path = sys.argv[1], sys.argv[2]
tree = ET.parse(path)
parents = {c: p for p in tree.iter() for c in p}
def bounds(n):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds", ""))
    return tuple(int(x) for x in m.groups()) if m else None
for n in tree.iter():
    if (n.get("text", "") or "").strip() != name: continue
    if n.get("class", "").endswith("EditText"): continue
    cur = n
    while cur is not None:
        if cur.get("clickable") == "true":
            b = bounds(cur)
            if b and (b[2] - b[0]) > 400 and (b[3] - b[1]) > 80:
                print((b[0] + b[2]) // 2, (b[1] + b[3]) // 2); sys.exit(0)
            break
        cur = parents.get(cur)
sys.exit(1)
PY
}

assign () {
  local seat="$1" search="$2" name="$3"
  hide_ime
  for _ in 1 2 3; do
    ./ui.py $S find "^Seat 1," >/dev/null 2>&1 && break
    ./ui.py $S back >/dev/null 2>&1; sleep 0.8
  done
  ./ui.py $S tap "^Seat $seat," >/dev/null || { echo "FAIL seat $seat"; return 1; }
  sleep 0.8
  ./ui.py $S tap "Change…" >/dev/null || return 1
  sleep 0.8
  ./ui.py $S tap "Search characters" >/dev/null || return 1
  sleep 0.5
  ./ui.py $S type "$search" >/dev/null || return 1
  sleep 1.0
  ./ui.py $S dump >/dev/null 2>&1
  local xy; xy=$(row_xy "$name") || { echo "FAIL no row for $name"; return 1; }
  ./ui.py $S tapxy ${=xy} >/dev/null
  sleep 0.9
  echo "seat $seat -> $name"
}

assign 1  "Devil"       "Devil's Advocate"
assign 2  "Sailor"      "Sailor"
assign 3  "Chambermaid" "Chambermaid"
assign 4  "Gossip"      "Gossip"
assign 5  "Grandmother" "Grandmother"
assign 6  "Professor"   "Professor"
assign 7  "Tea Lady"    "Tea Lady"
assign 8  "Exorcist"    "Exorcist"
assign 9  "Fool"        "Fool"
assign 10 "Lunatic"     "Lunatic"
assign 11 "Pukka"       "Pukka"
assign 12 "Godfather"   "Godfather"
hide_ime
./ui.py $S find "^Seat "
