# C3_fix_day_closed — C2_day_closed with C2-6's assertion inverted.
#
#   ./emu.sh launch emulator-5556 --fresh
#   ./scenario.py emulator-5556 C3_fix_day_closed
#
# Same game: Trouble Brewing, 8 seats, seat 1 = Virgin, seat 2 = Chef, so day 1
# closes with no vote at all — the Chef nominates the Virgin, the interceptor
# executes the Chef, `nominationsClosed` becomes true.
#
# What it proves:
#   C2-6  the closed-day blocker is printed ONCE, with ONE [Allow anyway].
#         `DayRules.checkNomination` used to append the byte-identical sentence
#         from `canNominate` AND from `canBeNominated`. `find` lists every
#         match, so the step's own output is the evidence.
#   C-4   (unchanged) the ring is disabled with the reason and an explicit
#         [Nominate anyway]; the override is what re-opens the ring here.
#   C2-9  the nomination card is reachable: with a pair picked the ring
#         collapses to "Player 2 » Player 1" + [Change], and Lock in is on
#         screen without a swipe.

STEPS = [
    ("wait", "New game"),
    ("tap", "New game"),
    ("wait", "SCRIPT"),
    ("tap", "Trouble Brewing"),
    ("sleep", 2.0),
    ("wait", "Start empty"),
    ("tap", "Start empty"),
    ("wait", "Before the first night"),
    ("tap", "^Close$"),
    ("sleep", 1.2),

    # --- seat 1 = Virgin --------------------------------------------------
    ("tap", "^Seat 1,"),
    ("wait", "Change"),
    ("tap", "Change…"),
    ("wait", "Search characters"),
    ("tap", "Search characters"),
    ("type", "Virgin"),
    ("sleep", 0.9),
    ("back", None),
    ("sleep", 0.6),
    ("tap", "the 1st time you are nominated"),
    # Both seats take a TOWNSFOLK, which leaves the checklist's blocking row
    # ids unchanged, so the sheet does not re-raise over the seat sheet here.
    ("sleep", 2.0),
    ("back", None),
    ("wait", "^Seat 1,"),
    ("sleep", 1.0),

    # --- seat 2 = Chef (a Townsfolk nominator) ----------------------------
    ("tap", "^Seat 2,"),
    ("wait", "Change"),
    ("tap", "Change…"),
    ("wait", "Search characters"),
    ("tap", "Search characters"),
    ("type", "Chef"),
    ("sleep", 0.9),
    ("back", None),
    ("sleep", 0.6),
    ("tap", "you start knowing how many pairs"),
    # Both seats take a TOWNSFOLK, which leaves the checklist's blocking row
    # ids unchanged, so the sheet does not re-raise over the seat sheet here.
    ("sleep", 2.0),
    ("back", None),
    ("wait", "^Seat 1,"),
    ("sleep", 1.0),

    # --- straight through night 1 ----------------------------------------
    ("tap", "Begin night"),
    ("wait", "Start the night anyway"),
    ("tap", "Start the night anyway"),
    ("sleep", 2.5),
    ("tap", "^Dawn$"),
    ("wait", "Dawn anyway"),
    ("tap", "Dawn anyway"),
    ("wait", "OPEN DAY 1"),
    ("tap", "OPEN DAY 1"),
    ("sleep", 2.0),

    # --- the Chef nominates the Virgin -----------------------------------
    # Ring seats are addressed by coordinate: a ring token and a vote chip
    # share the text "Player N", and `tap` picks the ring's smaller box.
    ("tap", "^Nominate$"),
    ("sleep", 1.5),
    ("tapxy", ["846", "653"]),               # nominator = Player 2 (Chef)
    ("sleep", 1.5),
    ("tapxy", ["540", "580"]),               # nominee   = Player 1 (Virgin)
    ("sleep", 2.5),
    ("screenshot", None),
    ("find", "the Virgin's first nomination"),
    ("tap", "Execute Player 2"),
    ("sleep", 3.0),
    ("screenshot", None),
    ("find", "Player 2 was executed — the day is over"),
    # C-13: the strip carries the settled day, not a stale "On the block".
    ("absent", "On the block"),

    # --- nominations after the day is settled ----------------------------
    # The draft pair (the Chef nominating the Virgin) survives the execution,
    # so re-opening the stage shows the check card for it against a CLOSED day
    # — which is the state that printed the blocker twice.
    ("tap", "^Nominate$"),
    ("sleep", 2.5),
    ("screenshot", None),
    # C-4: the ring is disabled with the reason and an explicit override.
    ("find", "Nominate anyway"),
    # C2-6: ONE row and ONE override for a reason BOTH halves of the check
    # produce. `find` prints every match, so two nodes here is the bug coming
    # back — C2 measured two rows and two [Allow anyway].
    # Anchored at the start, so the vote panel's own "This will NOT be
    # recorded: …" line is not counted as a second copy of the row.
    ("find", "^Nominations are closed today"),
    ("find", "Allow anyway"),
    # C2-9: the ring has collapsed to the pair it was showing in its centre.
    ("find", "^Change$"),
    ("find", "Player 2 » Player 1"),
    # C-4 still holds: the vote panel refuses, and says so, with its own
    # explicit override.
    ("find", "This will NOT be recorded"),
    ("audit", None),
]
