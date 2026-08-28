# Fix wave 2, agent E — the night sheet: E-2, E-3, E-5, E-6.
#
#   ./emu.sh launch emulator-5554 --fresh
#   ./scenario.py emulator-5554 D_bmr_setup
#   zsh scenarios/D_bmr_assign.sh emulator-5554
#   ./scenario.py emulator-5554 E_fix_night_pointer
#
# The same 12-player Bad Moon Rising game playtest D was driven on. It picks up
# on the setup checklist that raises itself once the last character is assigned.
#
# What it proves, step by step:
#   *  3  E-2 — the Grandchild picker offers Ben/Cleo/Dev/Gita/Iris/Hal/Finn and
#          NOT Erin, who is the Grandmother herself (evil seats Ana, Kai and Lena
#          were already filtered);
#   * 63  E-6 — resolving the night-1 Godfather at step 6 opens step 7. It used
#          to throw the sheet back to step 4;
#   * 72  E-6 — jumping ahead to the Pukka at step 8 (the Devil's Advocate at
#          step 7 deliberately left owed) and resolving it opens step 9, not the
#          row above it;
#   * 74  E-5 — step 9, the Grandmother, opens with the marked Grandchild
#          already picked: the card states "Finn is the Fool" and the primary is
#          ARMED, reading SHOW "FOOL" TO ERIN. It used to open with nothing
#          selected and DONE — NEXT STEP DISABLED;
#   * 80  E-3 — a finished row carries [Undo] in the collapsed list, which is
#          the only un-tick in the night UI (the card's drawer no longer has
#          one, so no "do it" control can un-do a step).
STEPS = [
    # ---- E-2: the Grandmother is not her own Grandchild -----------------
    ("wait",  "The Grandchild"),
    ("tap",   "The Grandchild"),
    ("sleep", 1.5),
    ("find",  "^Ben$"),
    ("find",  "^Finn$"),
    ("audit", None),
    ("screenshot", None),
    ("tap",   "^Finn$"),
    ("sleep", 1.5),

    # ---- the Lunatic believes they are the Po ---------------------------
    ("tap",   "Which Demon token does the Lunatic see"),
    ("sleep", 1.5),
    ("tapxy", ["597", "1055"]),          # the "Po" row of the token picker
    ("sleep", 1.5),
    ("find",  "The Lunatic believes"),

    # ---- their fake Minions: Ana and Lena --------------------------------
    ("tap",   "Point out players as Jonas"),
    ("sleep", 1.5),
    ("tapxy", ["540", "895"]),           # Ana
    ("sleep", 0.8),
    ("swipe", ["up", "700"]),
    ("sleep", 0.8),
    ("tapxy", ["540", "1609"]),          # Lena
    ("sleep", 0.8),
    ("tap",   "Place 2"),
    ("sleep", 1.5),

    # ---- both bluff sets -------------------------------------------------
    ("tap",   "Choose 3 bluffs"),
    ("sleep", 1.8),
    ("tap",   "Suggest 3"),
    ("sleep", 1.2),
    ("tap",   "Lunatic bluffs"),
    ("sleep", 1.5),
    ("tap",   "Suggest 3"),
    ("sleep", 1.2),
    ("back",  None),
    ("sleep", 1.5),

    # ---- night 1 ----------------------------------------------------------
    ("tapxy", ["540", "600"]),           # close the checklist sheet
    ("sleep", 1.2),
    ("back",  None),                     # whatever the scrim tap opened
    ("sleep", 1.2),
    ("tap",   "Begin night"),
    ("sleep", 2.0),

    ("tap",   "DONE — NEXT STEP"),       # 1 Dusk
    ("sleep", 1.5),
    ("swipe", ["up", "900"]),
    ("tap",   "DONE — NEXT STEP"),       # 2 Minion info
    ("sleep", 1.8),
    ("tap",   "DONE — NEXT STEP"),       # 3 the Lunatic's hand-over
    ("sleep", 1.8),
    ("tap",   "DONE — NEXT STEP"),       # 4 Demon info
    ("sleep", 1.8),
    ("find",  "step 5 / 11"),

    # 5 the Sailor picks Iris.
    ("tap",   "7  Iris"),
    ("sleep", 1.0),
    ("tap",   "IRIS — DRUNK"),
    ("sleep", 2.0),

    # ---- E-6: resolving step 6 opens step 7, not step 4 -------------------
    ("find",  "step 6 / 11"),
    ("find",  "Godfather"),
    ("tap",   "SHOW “LUNATIC” TO LENA"),
    ("sleep", 2.0),
    ("hold",  ["HOLD TO CLOSE", "1600"]),
    ("sleep", 1.5),
    ("hold",  ["The grimoire is closed", "1200"]),
    ("sleep", 1.5),
    ("find",  "step 7 / 11"),
    ("screenshot", None),

    # ---- E-6: and out of order, from the collapsed list -------------------
    # Step 7 (the Devil's Advocate) is deliberately left owed.
    ("tap",   "Pukka — Kai"),
    ("sleep", 1.5),
    ("find",  "step 8 / 11"),
    ("tap",   "9  Finn"),
    ("sleep", 1.0),
    ("tap",   "FINN — POISONED"),
    ("sleep", 2.0),
    ("find",  "step 9 / 11"),
    ("screenshot", None),

    # ---- E-5: the Grandmother opens on the marked Grandchild --------------
    # The seat marked at setup is already picked, so the card can state the
    # answer and the primary is ARMED. It used to open empty and DISABLED.
    ("find",  "Finn is the Fool"),
    ("find",  "SHOW “FOOL” TO ERIN"),
    ("screenshot", None),

    # ---- E-3: the only un-tick in the night UI is [Undo] on a done row ----
    ("tap",   "whole sheet"),
    ("sleep", 1.2),
    ("swipe", ["down", "900"]),
    ("sleep", 0.8),
    ("swipe", ["down", "900"]),
    ("sleep", 0.8),
    ("find",  "\\[Undo\\]"),
    ("audit", None),
    ("screenshot", None),
    ("tap",   "hide sheet"),
    ("sleep", 1.0),
]
