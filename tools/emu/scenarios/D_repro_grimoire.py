# The three grimoire/day defects that do not need the 12-player BMR game.
#
# It began as a REPRO — every step below was written to walk into the bug and
# leave a screenshot of it. All three are fixed now, so the same walk asserts
# the fix instead:
#
#   * "+ Token" -> Drunk -> Remove really removes it (it used to be a no-op,
#     and so was Suspend)
#   * "Show the grimoire to a player…" -> [HAND IT OVER] / [Cancel] are inside
#     the safe area and tappable by NAME (they used to sit under the gesture
#     inset, reachable only by hitting a 4 px sliver with `tapxy`)
#   * the nomination screen -> `audit` finds no overlap between the seat circle
#     and the vote-chip row
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

    # --- Remove really removes a hand-placed token ------------------------
    ("tap",   "^Seat 1,"),
    ("sleep", 1.2),
    ("tap",   r"\+ Token"),
    ("sleep", 1.5),
    ("tap",   "^Drunk$"),
    ("sleep", 1.5),
    ("find",  "^Remove$"),     # the token is on the seat, with its controls
    ("tap",   "^Remove$"),
    ("sleep", 1.5),
    ("absent", "^Remove$"),    # …and now it is gone, controls and all
    ("back",  None),
    ("sleep", 1.2),

    # --- Spy read-only mode: the action row is inside the safe area -------
    ("tap",   "^Menu$"),
    ("sleep", 1.2),
    ("tap",   "Show the grimoire to a player"),
    ("sleep", 1.8),
    ("audit", None),           # no CENTRE UNTAPPABLE on HAND IT OVER / Cancel
    ("find",  "HAND IT OVER"),
    ("tap",   "^Cancel$"),     # by name: it used to need a `tapxy` sliver
    ("sleep", 1.5),
    # Leaving read-only mode lands on the privacy cover, by design.
    ("hold",  ["press and hold to open", "1600"]),
    ("sleep", 1.5),

    # --- Nomination: circle seats overlap the vote chips ------------------
    ("tap",   "Begin night"),
    ("sleep", 2.0),
    # An empty-start game has no bag, so the begin-night guard says so and
    # offers the override. Nothing below needs a legal bag.
    ("tap",   "Start the night anyway"),
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
