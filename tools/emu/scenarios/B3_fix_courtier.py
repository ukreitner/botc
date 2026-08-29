# Fix wave 3, agent B2 — B2-2 (with D2-2), the Courtier's storyteller-only answer.
#
#   ./emu.sh launch emulator-5554 --fresh
#   ./scenario.py emulator-5554 B2_bmr8_setup
#   ./scenario.py emulator-5554 B3_fix_courtier
#
# Picks up where `B2_bmr8_setup` leaves off (HAND OUT TOKENS, bag dealt) and
# walks to the Courtier's row on night 1.
#
# What it proves:
#
#   * the card still carries the storyteller's crib — the headline "Whoever they
#     name: these seats hold the characters in play" and the seat-by-seat list —
#     under a new line saying who it is for: "FOR YOU ONLY — this is not shown
#     to anybody, and there is no card to hold up";
#   * there is NO `SHOW: …` offer and NO `SHOW “TINKER, INNKEEPER, PROFESSOR,
#     SAILOR, ZOMBUUL, COURTIER, EXORCIST, GODFATHER” TO PLAYER n` primary. One
#     press of that button used to put every character in the game, Demon and
#     Minion included, in front of the Courtier;
#   * the primary states the CHOICE instead: naming the Tinker reads
#     "TINKER — DRUNK 1".
#
# The Exorcist's `SHOW “YES” TO <the Exorcist>` (B2-2 / D2-3) is the same engine
# flag on the same code path; `RulesBadMoonRisingTest` pins both.
STEPS = [
    ("tap",    "Deal anyway"),           # the Godfather's [±1 Outsider] warning
    ("sleep",  1.2),
    ("tap",    "Finish later"),
    ("sleep",  1.5),
    ("tap",    "Demon bluffs"),
    ("sleep",  1.0),
    ("tap",    "Suggest 3"),
    ("sleep",  1.0),
    ("back",   None),
    ("sleep",  1.0),
    ("tap",    "^Close$"),
    ("sleep",  1.2),

    ("tap",    "Begin night"),
    ("sleep",  1.5),
    ("tap",    "Start the night anyway"),
    ("sleep",  2.0),
    ("tap",    "whole sheet"),
    ("sleep",  1.5),
    ("tap",    "Courtier — Player"),
    ("sleep",  2.0),

    # The card is taller than the screen: scroll to its foot, where the crib,
    # the offers and the primary button live.
    ("swipe",  ["up", "900"]),
    ("sleep",  1.0),

    # The storyteller's crib is still there…
    ("find",   "these seats hold the characters in play"),
    ("find",   "FOR YOU ONLY"),
    # …and is no longer offered as a card, or as the button.
    ("absent", "SHOW: "),
    ("absent", "SHOW “"),
    ("screenshot", None),

    # The primary states the choice the row is actually for.
    ("swipe",  ["down", "900"]),
    ("sleep",  1.0),
    ("tap",    "^Tinker$"),
    ("sleep",  1.0),
    ("swipe",  ["up", "900"]),
    ("sleep",  1.2),
    ("find",   "TINKER — DRUNK 1"),
    ("audit",  None),
    ("screenshot", None),
]
