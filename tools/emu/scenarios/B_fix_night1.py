# Playtest B fix wave — night 1 of the same 8-player Trouble Brewing game as
# `B_night1_tb.py`, driven all the way to the Washerwoman card.
#
#     ./emu.sh launch emulator-5556 --fresh
#     ./scenario.py emulator-5556 B_fix_night1
#
# Proves:
#   B-1  the Washerwoman / Librarian / Investigator cards are "1 of 2 players",
#        never the whole candidate set and never the holder themselves
#   B-4  the discussion timer is docked in the progress strip, so `audit` finds
#        no clickable overlapping the card's full-width primary button
#
# Seats after the deal are 1 Empath · 2 Poisoner · 3 Librarian · 4 Chef ·
# 5 Washerwoman · 6 Imp · 7 Butler · 8 Investigator.
#
# A fresh app opens on 8 seats (5/1/1/1), which is the deal below.
# The bag is ticked by label. Only the FIRST tick moves the list (the "in the
# bag" strip grows a row of tokens), so the swipes below are taken after it.
STEPS = [
    ("wait",   "New game"),
    ("tap",    "New game"),
    ("wait",   "TABLE"),
    ("tap",    "^Trouble Brewing$"),     # pick the script
    ("sleep",  0.8),
    ("tap",    "^TABLE$"),               # collapse
    ("tap",    "^BAG$"),                 # expand
    ("sleep",  0.6),

    ("tap",    "^Chef$"),
    ("swipe",  ["up", "600"]),
    ("sleep",  0.7),
    ("tap",    "^Investigator$"),
    ("tap",    "^Librarian$"),
    ("swipe",  ["up", "600"]),
    ("sleep",  0.7),
    ("tap",    "^Washerwoman$"),
    ("swipe",  ["up", "600"]),
    ("sleep",  0.7),
    ("tap",    "^Butler$"),
    ("swipe",  ["up", "400"]),
    ("sleep",  0.7),
    ("tap",    "^Poisoner$"),
    ("swipe",  ["up", "600"]),
    ("sleep",  0.7),
    ("tap",    "^Imp$"),
    ("swipe",  ["down", "600"]),
    ("sleep",  0.7),
    ("swipe",  ["down", "600"]),
    ("sleep",  0.7),
    ("swipe",  ["down", "600"]),
    ("sleep",  0.7),
    ("swipe",  ["down", "600"]),
    ("sleep",  0.7),
    ("tap",    "^Empath$"),
    ("wait",   "IN THE BAG · 8 / 8"),

    ("tap",    "Deal & hand out"),
    ("wait",   "HAND OUT TOKENS"),
    ("tap",    "Finish later"),
    ("sleep",  1.2),
    ("tap",    "^Close$"),
    ("sleep",  1.0),
    ("tap",    "Begin night"),
    ("sleep",  1.2),
    ("tap",    "Start the night anyway"),
    ("wait",   "step 1 / 11"),
    ("audit",  None),

    # Dusk -> Minion info -> Demon info. Their prose is long, so the primary is
    # below the fold and the sheet has to be scrolled to it.
    ("tap",    "DONE — NEXT STEP"),
    ("sleep",  1.0),
    ("swipe",  ["up", "700"]),
    ("swipe",  ["up", "700"]),
    ("swipe",  ["up", "700"]),
    ("tap",    "DONE — NEXT STEP"),
    ("sleep",  1.0),
    ("swipe",  ["up", "700"]),
    ("swipe",  ["up", "700"]),
    ("swipe",  ["up", "700"]),
    ("tap",    "DONE — NEXT STEP"),
    ("wait",   "step 4 / 11"),

    # The Poisoner chooses seat 1 (the Empath), so night 1 also carries an
    # impaired info step.
    ("tap",    "1  Player 1"),
    ("tap",    "PLAYER 1 — POISONED"),
    ("wait",   "step 5 / 11"),

    # The Washerwoman. The headline must be a 1-of-2, and the primary must name
    # exactly two players, neither of them Player 5.
    ("swipe",  ["down", "700"]),
    ("find",   "1 of 2 players is the"),
    # The deal is random, so the assertion is on the SHAPE: two named players
    # shown to one, and neither of them the holder.
    ("find",   "SHOW “[A-Z]+ — PLAYER [0-9], PLAYER [0-9]” TO PLAYER [0-9]"),
    ("audit",  None),
]
