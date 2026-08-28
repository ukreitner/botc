# Minimal from-scratch repro of the three grimoire/day defects that do not
# need the 12-player BMR game:
#
#   * step 06-09 : "+ Token" -> Drunk, then Remove twice -> the token stays
#   * step 14-15 : "Show the grimoire to a player..." -> both buttons are
#                  under the gesture inset (audit reports CENTRE UNTAPPABLE)
#   * step 22-24 : the nomination screen -> `audit` reports the seat circle
#                  overlapping the vote-chip row
#
#   ./emu.sh launch emulator-5560 --fresh
#   ./scenario.py emulator-5560 D_repro_grimoire
STEPS = [
    ("wait",  "New game"),
    ("tap",   "New game"),
    ("wait",  "TABLE"),
    ("tap",   "Trouble Brewing"),
    ("tap",   "Start empty"),
    ("sleep", 1.5),
    ("tap",   "^Close$"),
    ("sleep", 1.0),

    # --- Remove / Suspend on a hand-placed token are no-ops ---------------
    ("tap",   "^Seat 1,"),
    ("sleep", 1.2),
    ("tap",   r"\+ Token"),
    ("sleep", 1.5),
    ("tap",   "^Drunk$"),
    ("sleep", 1.5),
    ("tap",   "^Remove$"),     # nothing happens
    ("sleep", 1.5),
    ("tap",   "^Suspend$"),    # nothing happens either
    ("sleep", 1.5),
    ("tap",   "^Remove$"),     # still nothing: the token is still listed
    ("sleep", 1.5),
    ("back",  None),
    ("sleep", 1.2),

    # --- Spy read-only mode: the action row is off the bottom -------------
    ("tap",   "^Menu$"),
    ("sleep", 1.2),
    ("tap",   "Show the grimoire to a player"),
    ("sleep", 1.8),
    ("audit", None),           # SAFE-AREA VIOLATIONS: HAND IT OVER, Cancel
    ("tapxy", ["950", "2360"]),  # only reachable by hitting the 4px sliver
    ("sleep", 1.5),

    # --- Nomination: circle seats overlap the vote chips ------------------
    ("tap",   "Begin night"),
    ("sleep", 2.0),
    ("tap",   "^Dawn$"),
    ("sleep", 1.5),
    ("tap",   "Dawn anyway"),
    ("sleep", 1.5),
    ("tap",   "OPEN DAY 1"),
    ("sleep", 2.0),
    ("tap",   "Nominate"),
    ("sleep", 1.5),
    ("tapxy", ["540", "579"]),   # seat 1 nominates
    ("sleep", 1.2),
    ("tapxy", ["914", "958"]),   # seat 5 is nominated
    ("sleep", 1.5),
    ("swipe", ["up", "900"]),
    ("sleep", 1.0),
    ("audit", None),             # OVERLAPPING CLICKABLES: seats vs vote chips
]
