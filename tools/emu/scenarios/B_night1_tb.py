# Playtest B — night area.
# 8-player Trouble Brewing, info-heavy bag: Washerwoman, Librarian, Investigator,
# Chef, Empath / Butler / Poisoner / Imp.  Run from a FRESH app:
#     ./emu.sh launch emulator-5556 --fresh
#     ./scenario.py emulator-5556 B_night1_tb
# Leaves you on the "HAND OUT TOKENS" screen with the bag dealt.
STEPS = [
    ("wait",      "New game"),
    ("tap",       "New game"),
    ("wait",      "TABLE"),
    ("tap",       "Trouble Brewing"),          # script
    ("sleep",     0.8),
    # collapse TABLE, expand BAG
    ("tapxy",     ["984", "542"]),
    ("sleep",     0.8),
    ("tapxy",     ["984", "707"]),
    ("sleep",     0.8),
    # tick the eight characters (pick.py scrolls; here they are all reachable
    # by scrolling the character list, so tap what is on screen and swipe)
    ("tap",       "^Chef$"),
    ("tap",       "^Empath$"),
    ("swipe",     ["up", "600"]),
    ("tap",       "^Investigator$"),
    ("tap",       "^Librarian$"),
    ("swipe",     ["up", "600"]),
    ("tap",       "^Washerwoman$"),
    ("swipe",     ["up", "600"]),
    ("tap",       "^Butler$"),
    ("swipe",     ["up", "600"]),
    ("tap",       "^Poisoner$"),
    ("tap",       "^Imp$"),
    ("audit",     None),
    ("tap",       "Deal & hand out"),
    ("wait",      "HAND OUT TOKENS"),
    ("audit",     None),
]
