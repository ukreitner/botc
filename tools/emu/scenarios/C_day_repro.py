# C_day_repro — minimal from-scratch reproduction of three day-screen findings.
#
#   ./emu.sh launch emulator-5558 --fresh
#   ./scenario.py emulator-5558 C_day_repro
#
# Trouble Brewing, 8 seats, ONE character assigned by hand (seat 1 = Butler)
# plus a hand-placed butler:Master reminder on seat 2.
#
# Evidence in the screenshots:
#   step 23   the "Before the first night" setup checklist re-opens itself on
#             top of the seat sheet after a character assignment.        [C-9]
#   ~step 44  "Did Player 1 claim to be the Goblin?" — the Goblin nomination
#             check fires in Trouble Brewing, a script with no Goblin.   [C-1]
#   ~step 48  "Player 1's Master is not voting — tally it anyway, then check."
#             and the running tally reads 1 of 4 — a hand the app itself
#             calls illegal is still added to the tally that decides the
#             outcome and the Lock-in label.                             [C-3]

STEPS = [
    ("wait", "New game"),
    ("tap", "New game"),
    ("wait", "TABLE"),
    ("tap", "Trouble Brewing"),
    ("sleep", 2.0),
    ("wait", "Add seat"),
    ("tap", "^TABLE$"),                     # collapse the seat editor again
    ("sleep", 1.0),
    ("tap", "Start empty"),
    ("wait", "Before the first night"),
    ("tap", "^Close$"),
    ("sleep", 1.0),

    # --- seat 1 = Butler -------------------------------------------------
    ("tap", "^Seat 1,"),
    ("wait", "Change"),
    ("tap", "Change…"),
    ("wait", "Search characters"),
    ("tap", "Search characters"),
    ("type", "Butler"),
    ("sleep", 0.9),
    ("back", None),                          # hide the soft keyboard
    ("sleep", 0.6),
    ("tap", "you may only vote if they are voting"),
    # C-9: the setup checklist re-opens itself over the seat sheet.
    ("wait", "Before the first night"),
    ("tap", "^Close$"),
    ("sleep", 1.2),
    ("back", None),
    ("wait", "^Seat 1,"),
    ("sleep", 1.0),

    # --- seat 2 carries the Butler's Master reminder ----------------------
    ("tap", "^Seat 2,"),
    ("wait", "\\+ Token"),
    ("tap", "\\+ Token"),
    ("wait", "^Master$"),
    ("tap", "^Master$"),
    ("wait", "Kill…"),
    ("sleep", 1.5),
    ("back", None),
    ("wait", "^Seat 1,"),
    ("sleep", 1.0),

    # --- straight through night 1 ----------------------------------------
    ("tap", "Begin night"),
    ("wait", "Start the night anyway"),
    ("tap", "Start the night anyway"),
    ("sleep", 2.0),
    ("tap", "^Dawn$"),
    ("wait", "Dawn anyway"),
    ("tap", "Dawn anyway"),
    ("wait", "OPEN DAY 1"),
    ("tap", "OPEN DAY 1"),
    ("sleep", 2.0),
    ("audit", None),

    # --- C-1: the Goblin check fires on an ordinary TB nomination ---------
    ("tap", "Nominate"),
    ("sleep", 1.5),
    ("tap", "^Player 3$"),
    ("sleep", 1.5),
    ("tap", "^Player 1$"),
    ("sleep", 2.0),
    ("swipe", ["up", "350"]),
    ("sleep", 1.2),
    ("screenshot", None),                    # "Did Player 1 claim to be the Goblin?"
    ("audit", None),

    # --- C-3: the Butler's illegal hand is added to the tally -------------
    # The Butler is the LAST vote chip (clockwise from their own left).
    # tapxy, not tap: "Player 1" also matches the pinned seat ring above.
    ("swipe", ["up", "600"]),
    ("sleep", 1.5),
    ("tapxy", [869, 1419]),
    ("sleep", 2.5),
    ("screenshot", None),                    # "…Master is not voting" + "1 of 4"
]
