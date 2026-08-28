# Playtest D P0-2 / P0-3 / P0-4 / P1-9 — the user's Bad Moon Rising game.
#
#   ./emu.sh launch emulator-5560 --fresh
#   ./scenario.py emulator-5560 D_bmr_setup     # dismiss the update banner if
#                                               # the build carries a real sha
#   zsh scenarios/D_bmr_assign.sh emulator-5560
#   ./scenario.py emulator-5560 D_fix_bmr_night1
#
# The setup checklist raises itself once the last character is assigned; this
# picks up from there and finishes the Lunatic's rows, then walks night 1.
#
# What it proves, step by step:
#   * 12  the Lunatic's Demon token is the Po (the checklist row P1-10 made
#         reachable from the menu as well);
#   * 30  NIGHT 1 step 3 is a REAL row — "Po / Jonas · seat 10", the illusion
#         banner, HAND OVER THE ILLUSION naming the Po, the fake Minions and
#         the bluffs, and DONE — NEXT STEP rather than a skip (P0-2);
#   * 34  step 4, Demon info, carries "Also show the Demon who the LUNATIC is
#         (Jonas)" in ember and a SHOW: THE LUNATIC IS JONAS card (P0-3);
#   * 40  step 6, the Godfather's FIRST night, does show the Outsider block.
#
# The night-2 half (P0-4: the Outsider block gone; P1-9: "Ben dies now") is
# driven by hand from here — see the report; both are pinned by
# `RulesBadMoonRisingTest`.
STEPS = [
    # ---- the Lunatic believes they are the Po -------------------------
    ("wait",  "Which Demon token does the Lunatic see"),
    ("tap",   "Which Demon token does the Lunatic see"),
    ("sleep", 1.5),
    ("tapxy", ["597", "1055"]),          # the "Po" row of the token picker
    ("sleep", 1.5),
    ("find",  "The Lunatic believes"),

    # ---- their fake Minions: Ana and Lena ------------------------------
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

    # ---- both bluff sets ----------------------------------------------
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

    # ---- night 1 -------------------------------------------------------
    ("tapxy", ["540", "600"]),           # close the checklist sheet
    ("sleep", 1.2),
    ("back",  None),                     # whatever the scrim tap opened
    ("sleep", 1.2),
    ("tap",   "Begin night"),
    ("sleep", 2.0),
    # This scenario never marks the Grandchild, so the begin-night guard now
    # says so ("Setup isn't legal yet — Grandmother: mark the Grandchild") and
    # offers [Fix setup] / [Start the night anyway]. Nothing below depends on
    # the Grandmother, so take the override, as B_fix_night1 does.
    ("tap",   "Start the night anyway"),
    ("sleep", 2.0),

    ("tap",   "DONE — NEXT STEP"),       # 1 Dusk
    ("sleep", 1.5),
    ("swipe", ["up", "900"]),
    ("tap",   "DONE — NEXT STEP"),       # 2 Minion info
    ("sleep", 1.8),

    # P0-2: step 3 is the Lunatic's own row, and it RUNS.
    ("find",  "step 3 / 11"),
    ("find",  "Jonas · seat 10"),
    ("find",  "HAND OVER THE ILLUSION"),
    ("find",  "THESE ARE YOUR MINIONS"),
    ("find",  "SHOW: BLUFFS"),
    ("find",  "DONE — NEXT STEP"),
    ("screenshot", None),

    # P0-3: the Demon is told who the Lunatic is.
    ("tap",   "DONE — NEXT STEP"),
    ("sleep", 1.8),
    ("find",  "step 4 / 11"),
    ("find",  "Also show the Demon who the LUNATIC is"),
    ("swipe", ["up", "900"]),
    ("sleep", 0.8),
    ("find",  "SHOW: THE LUNATIC IS JONAS"),
    ("screenshot", None),
]
