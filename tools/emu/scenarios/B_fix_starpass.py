# Playtest B fix wave — the Imp's star pass.
#
# Prerequisite: a night-2 sheet open on step 1 (Dusk) of a Trouble Brewing game
# with a living Poisoner, i.e. `B_fix_night1` played out to the dawn and the day
# closed again:
#
#     ./scenario.py emulator-5556 B_fix_night1
#     …then, by hand, to night 2 — the top bar's Dusk button is gone (F-3 /
#       D77), so the day closes from the Day tab:
#         ./ui.py <serial> tap "^Dawn$" ; ./ui.py <serial> tap "Dawn anyway"
#         ./ui.py <serial> tap "OPEN DAY 1"
#         ./ui.py <serial> tap "^Day$"
#         …swipe up to the DUSK stage card…
#         ./ui.py <serial> tap "^DUSK$"
#         ./ui.py <serial> tap "Everyone, eyes closed"
#         ./ui.py <serial> tap "BEGIN NIGHT 2"
#     ./scenario.py emulator-5556 B_fix_starpass
#
# Proves:
#   B-2  night 2 opens on step 1 / 6 — Dusk, its first unfinished row — and NOT
#        on the leftover Dawn card whose primary skips the whole night
#   B-3  an Imp that kills itself is asked for an heir ON THE CARD, the heir
#        really becomes the Imp, and no "Declare good victory" dialog appears
#        while a Minion is alive
STEPS = [
    ("find",   "step 1 / 6"),
    ("find",   "^DONE — NEXT STEP"),
    ("tap",    "^DONE — NEXT STEP"),
    ("sleep",  1.2),

    # The Poisoner: any seat will do.
    ("find",   "WHO DID THEY CHOOSE"),
    ("tap",    "4  Player 4"),
    ("sleep",  0.8),
    ("tap",    "— POISONED"),
    ("sleep",  1.4),

    # The Imp chooses itself.
    ("find",   "◆ themselves"),
    ("tap",    "◆ themselves"),
    ("sleep",  1.0),
    ("find",   "DIES"),
    ("hold",   ["DIES", "1300"]),
    ("sleep",  2.0),

    # The question is asked HERE, on the card, with the legal heirs as chips —
    # and the primary states what answering it does.
    ("find",   "a Minion becomes the Imp"),
    ("find",   "PICK ONE"),
    ("tap",    "· Poisoner"),
    ("sleep",  0.8),
    ("find",   "BECOMES THE IMP"),
    ("hold",   ["BECOMES THE IMP", "1300"]),
    ("sleep",  2.0),

    # This used to be followed by dismissing the "Before the first night"
    # checklist, which any mid-game character change re-raised because
    # `SetupRequirements` still counted bag legality once the game was running.
    # Fix-E closed that (D81: bag rows exist only in SETUP), so a star pass
    # raises nothing and there is no sheet to close.
    ("absent", "Before the first night"),
    ("sleep",  1.0),

    # The grimoire really changed, and nobody was told good had won.
    ("tap",    "^Grimoire$"),
    ("sleep",  1.5),
    ("find",   "Imp, alive"),
]
