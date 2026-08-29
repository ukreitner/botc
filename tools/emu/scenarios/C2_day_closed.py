# C2_day_closed — what the nomination card says once the day is settled.
#
#   ./emu.sh launch emulator-5556 --fresh
#   ./scenario.py emulator-5556 C2_day_closed
#
# Trouble Brewing, 8 seats, seat 1 = Virgin and seat 2 = Chef, so day 1 can be
# closed with no vote at all: the Chef nominates the Virgin, the interceptor
# executes the Chef, and `nominationsClosed` becomes true.
#
# What the screenshots have to show:
#   ~step 44  the Virgin card and its three options — and, after [Execute
#             Player 2], the stat strip reads "Player 2 was executed — the day
#             is over." with no stale "On the block".                 [C-13]
#   ~step 50  the ring is DISABLED with the reason and an explicit
#             [Nominate anyway] — nothing is silently discarded.      [C-4]
#   ~step 52  the check card prints "Nominations are closed today — the day's
#             execution is settled." TWICE, each with its own [Allow anyway]:
#             `DayRules.checkNomination` (DayRules.kt:262-271) appends the
#             blocker from `canNominate` (kt:187) and again from
#             `canBeNominated` (kt:247) without de-duplicating.       [C2-6]

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
    ("tap", "^Nominate$"),
    ("sleep", 2.0),
    ("screenshot", None),
    ("find", "Nominate anyway"),
    ("tapxy", ["846", "824"]),               # a ring seat: it is DISABLED now
    ("sleep", 1.0),
    ("tapxy", ["540", "766"]),
    ("sleep", 2.0),
    ("screenshot", None),
    # C2-6: the same blocker twice, each with its own [Allow anyway].
    ("find", "Nominations are closed today"),
    ("find", "Allow anyway"),
    ("audit", None),
]
