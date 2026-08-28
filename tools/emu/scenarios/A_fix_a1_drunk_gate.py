# A-1 (P0) FIXED: the hand-out refuses a seat whose "believes" row is open.
#
# Before: "Deal & hand out tokens" walked the Drunk's own player through a card
# reading "YOU ARE / Drunk", while the same screen listed "The Drunk believes —
# … Which Townsfolk token do they see?" as outstanding.
#
# After: the roster opens with "1 seat cannot be handed out yet", the seat's
# chip is a red "!", tapping it shows NOT READY instead of a token, and
# "Answer now" opens the checklist. Answering the row releases the seat, and
# the card then names the seat the phone has to reach ("seat N of 8", A-6).
#
# Run with:
#   ./emu.sh launch emulator-5554 --fresh
#   ./scenario.py emulator-5554 A_fix_a1_drunk_gate

STEPS = [
    ("wait",       "New game"),
    ("tap",        "New game"),
    ("wait",       "SCRIPT"),
    ("tap",        "^Trouble Brewing$"),
    ("sleep",      1.0),
    ("tap",        "8 seats"),            # collapse TABLE
    ("sleep",      0.8),
    ("tap",        "^BAG$"),
    ("sleep",      1.2),
    # Down to the Outsiders and put the Drunk in the bag by hand, so the
    # scenario always reproduces the P0 rather than hoping Randomize draws it.
    ("swipe",      ["up", "700"]),
    ("swipe",      ["up", "700"]),
    ("swipe",      ["up", "700"]),
    ("sleep",      0.8),
    ("tap",        "^Drunk$"),
    ("sleep",      0.8),
    ("swipe",      ["down", "700"]),
    ("swipe",      ["down", "700"]),
    ("swipe",      ["down", "700"]),
    ("swipe",      ["down", "700"]),
    ("sleep",      0.8),
    ("tap",        "Fill the rest"),      # keeps the Drunk, fills the other 7
    ("sleep",      1.5),
    ("tap",        "Deal & hand out"),
    ("wait",       "HAND OUT TOKENS"),
    ("sleep",      1.2),

    # --- the gate ---------------------------------------------------------
    ("find",       "cannot be handed out yet"),
    ("find",       "is the Drunk. Which Townsfolk token"),
    ("audit",      None),
    ("screenshot", None),
    ("tap",        "^!$"),                # the blocked seat's chip
    ("wait",       "NOT READY"),
    ("screenshot", None),

    # --- the one-tap jump, and the answer ---------------------------------
    ("tap",        "Answer now"),
    ("wait",       "Before the first night"),
    ("sleep",      1.0),
    ("audit",      None),
    ("tap",        "The Drunk believes"),
    ("wait",       "Which Townsfolk token do they see"),
    ("sleep",      0.8),
    ("tapxy",      ["540", "936"]),       # the first not-in-play Townsfolk
    ("sleep",      1.5),
    ("tap",        "^Close$"),
    ("sleep",      1.5),

    # --- released ---------------------------------------------------------
    # Answering the row drops us straight onto the seat's own card, which now
    # names the SEAT it has to reach rather than the shuffled queue slot (A-6).
    ("wait",       "seat [0-9]+ of 8"),
    ("screenshot", None),
    ("tap",        "I'll do this later"),
    ("wait",       "Next: "),
    ("find",       "Checklist"),          # A-4: a permanent way back
    ("audit",      None),
    ("screenshot", None),
]
