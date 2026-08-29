# Re-test B2 — a 15-seat Trouble Brewing game that carries EVERY info role.
#
#     ./emu.sh launch emulator-5554 --fresh
#     ./scenario.py emulator-5554 B2_tb15_setup
#
# 15 players is the smallest TB table whose distribution (9/2/3/1) holds all
# nine waking Townsfolk at once, so one night 1 exercises every info-card shape:
#
#   9 Townsfolk  Washerwoman · Librarian · Investigator · Chef · Empath ·
#                Fortune Teller · Undertaker · Monk · Ravenkeeper
#   2 Outsiders  Drunk · Recluse          (misregistration + the drunk gate)
#   3 Minions    Poisoner · Spy · Scarlet Woman
#   1 Demon      Imp
#
# Seats are added one at a time from the TABLE card (8 → 15). The bag is ticked
# through the BAG card's search field, the idiom `B_night1_tb` settled on: the
# tray grows as tokens land, so any fixed swipe below it goes stale, and a row
# that has scrolled out is not in the semantics tree at all. Filter to one
# character, tap its ABILITY line (the NAME also matches what was just typed
# into the field), clear, repeat.
#
# Leaves you on HAND OUT TOKENS with the bag dealt.


def tick(name, ability):
    return [
        ("tap",   "Search characters"),
        ("sleep", 0.6),
        ("type",  name),
        ("sleep", 1.0),
        ("back",  None),                      # dismiss the keyboard
        ("sleep", 0.8),
        ("tap",   ability),
        ("sleep", 0.8),
        ("tap",   "Clear the search"),
        ("sleep", 1.0),
    ]


BAG = [
    ("Washerwoman",    "particular Townsfolk"),
    ("Librarian",      "particular Outsider"),
    ("Investigator",   "particular Minion"),
    ("Chef",           "how many pairs of evil players"),
    ("Empath",         "how many of your 2 alive neighbors"),
    ("Fortune",       "you learn if either is a Demon"),
    ("Undertaker",     "which character died by execution"),
    ("Monk",           "they are safe from the Demon tonight"),
    ("Ravenkeeper",    "If you die at night"),
    ("Drunk",          "You do not know you are the Drunk"),
    ("Recluse",        "might register as evil"),
    ("Poisoner",       "they are poisoned tonight and tomorrow"),
    ("Spy",            "you see the Grimoire"),
    ("Scarlet",       "you become the Demon"),
    ("Imp",            "If you kill yourself this way"),
]

STEPS = [
    ("wait",   "New game"),
    ("tap",    "New game"),
    ("wait",   "TABLE"),
    ("tap",    "^Trouble Brewing$"),
    ("sleep",  1.2),

    # ---- 8 seats -> 15 --------------------------------------------------
    # Choosing the script collapses SCRIPT and expands TABLE by itself — do NOT
    # tap the TABLE row here, that COLLAPSES it again.
    ("find",   "Add seat"),
]
for _ in range(7):
    STEPS += [
        ("swipe", ["up", "400"]),
        ("sleep", 0.4),
        ("tap",   "Add seat"),
        ("sleep", 0.8),
    ]
STEPS += [
    ("swipe",  ["down", "900"]),
    ("swipe",  ["down", "900"]),
    ("swipe",  ["down", "900"]),
    ("swipe",  ["down", "900"]),
    ("swipe",  ["down", "900"]),
    ("swipe",  ["down", "900"]),
    ("swipe",  ["down", "900"]),
    ("swipe",  ["down", "900"]),
    ("sleep",  0.8),
    ("find",   "15 seats · 9/2/3/1"),

    # ---- the bag --------------------------------------------------------
    ("tap",    "15 seats"),                   # collapse TABLE
    ("sleep",  1.2),
    ("tap",    "^BAG$"),                      # expand BAG
    ("sleep",  1.2),
]
for _name, _ability in BAG:
    STEPS += tick(_name, _ability)
STEPS += [
    ("wait",   "IN THE BAG · 15 / 15"),
    ("audit",  None),
    ("tap",    "Deal & hand out"),
    ("wait",   "HAND OUT TOKENS"),
    ("audit",  None),
]
