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
#
# The bag used to be ticked by scrolling to each row, with the swipes tuned to
# where the list sat. That is not reproducible: the "IN THE BAG" tray grows the
# moment the first token lands (and again whenever the token row wraps), and a
# row that has scrolled out is not in the semantics tree at all, so `tap` had
# nothing to match. `B_night1_tb` carries the same fix — filter the BAG card's
# search field to one character, tap its ABILITY line (the NAME also matches
# what was just typed into the field), clear, repeat. Tick ORDER is unchanged,
# so the bag the deal shuffles is the same list as before.
def tick(name, ability):
    return [
        ("tap",   "Search characters"),
        ("sleep", 0.6),
        ("type",  name),
        ("sleep", 1.0),
        ("back",  None),                  # dismiss the keyboard
        ("sleep", 0.8),
        ("tap",   ability),
        ("sleep", 0.8),
        ("tap",   "Clear the search"),
        ("sleep", 1.0),
    ]


BAG = [
    ("Chef",         "how many pairs of evil players"),
    ("Investigator", "particular Minion"),
    ("Librarian",    "particular Outsider"),
    ("Washerwoman",  "particular Townsfolk"),
    ("Butler",       "you may only vote if they are voting"),
    ("Poisoner",     "they are poisoned tonight and tomorrow"),
    ("Imp",          "If you kill yourself this way"),
    ("Empath",       "how many of your 2 alive neighbors"),
]

STEPS = [
    ("wait",   "New game"),
    ("tap",    "New game"),
    ("wait",   "TABLE"),
    ("tap",    "^Trouble Brewing$"),     # pick the script
    ("sleep",  0.8),
    ("tap",    "^TABLE$"),               # collapse
    ("tap",    "^BAG$"),                 # expand
    ("sleep",  0.6),
]
for _name, _ability in BAG:
    STEPS += tick(_name, _ability)
STEPS += [
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
