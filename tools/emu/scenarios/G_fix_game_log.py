# G_fix_game_log — the game log is the engine's transcript now (G-1: C-10, C-11).
#
#   ./emu.sh launch emulator-5558 --fresh
#   ./scenario.py emulator-5558 G_fix_game_log
#
# `GameLogDialog` used to rebuild its own list from `deaths` + `nominations`, so
# a recorded statement never reached it (C-10) and an execution that killed
# nobody left no entry at all (C-11) — the record the Undertaker, the Mayor, the
# Vortox and the Zombuul all hinge on. It now renders `GameLog.rows`, grouped by
# phase.
#
# What the screenshots have to show:
#   ~step 40  the log opens under a DAY 1 heading with three rows:
#               Player 3 says: "Player 6 is the Imp"                   [C-10]
#               Player 1 nominates Player 5 — 4 votes (Player 6, …)
#               Player 5 is executed and survives (the storyteller)    [C-11]
#   ~step 41  `audit` is clean with the dialog open

STEPS = [
    ("wait", "New game"),
    ("tap", "New game"),
    ("wait", "SCRIPT"),
    ("tap", "Trouble Brewing"),
    ("sleep", 1.5),
    ("tap", "Start empty"),
    ("wait", "Before the first night"),
    ("tap", "^Close sheet$"),
    ("sleep", 1.2),
    ("tap", "Begin night"),
    ("sleep", 1.0),
    ("tap", "Start the night anyway"),
    ("sleep", 1.5),
    ("tap", "^Dawn$"),
    ("sleep", 1.0),
    ("tap", "Dawn anyway"),
    ("sleep", 1.5),
    ("tap", "OPEN DAY 1"),
    ("sleep", 1.5),

    # --- something said out loud -----------------------------------------
    ("tap", "\\+ Say"),
    ("wait", "Who said it"),
    ("tap", "^Player 3$"),
    ("sleep", 0.8),
    ("tap", "One line, in their words"),
    ("sleep", 0.8),
    ("type", "Player 6 is the Imp"),
    ("sleep", 0.8),
    ("tap", "^Add$"),
    ("sleep", 1.5),

    # --- a nomination and an execution that kills nobody ------------------
    ("tap", "Nominate"),
    ("sleep", 1.2),
    ("tap", "^Player 1$"),
    ("sleep", 0.8),
    ("tap", "^Player 5$"),
    ("sleep", 1.2),
    # By NAME, not by coordinate: since C2-9 the ring collapses to the pair
    # once both halves are picked, so the whole vote panel is on screen and the
    # old fixed chip coordinates addressed the card above it. The collapsed
    # pair reads "Player 1 » Player 5", which an anchored "^Player N$" does not
    # match, so the chips stay unambiguous.
    ("tap", "^Player 6$"),
    ("sleep", 0.4),
    ("tap", "^Player 7$"),
    ("sleep", 0.4),
    ("tap", "^Player 8$"),
    ("sleep", 0.4),
    ("tap", "^Player 1$"),
    ("sleep", 1.0),
    ("tap", "Lock in:"),
    ("sleep", 1.5),
    ("tap", "^Execute$"),
    ("wait", "Executed — but they don't die"),
    ("tap", "Executed — but they don't die"),
    ("sleep", 1.5),

    # --- the log carries all three ---------------------------------------
    ("tap", "^Menu$"),
    ("wait", "Game log"),
    ("tap", "Game log"),
    ("sleep", 1.2),
    ("find", "DAY 1"),
    ("find", "Player 3 says"),
    ("find", "Player 6 is the Imp"),
    ("find", "nominates Player 5"),
    ("find", "executed and survives"),
    ("screenshot", None),
    ("audit", None),
]
