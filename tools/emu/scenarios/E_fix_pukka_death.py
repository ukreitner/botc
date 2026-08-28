# Fix wave 2, agent E — E-4: the Pukka's deferred kill reaches the button.
#
#   ./scenario.py emulator-5554 E_fix_night_pointer      # leaves night 1 at step 9
#   ./scenario.py emulator-5554 E_fix_pukka_death
#
# Finishes night 1 of the same Bad Moon Rising game (the Pukka poisoned Finn on
# night 1), opens day 1, and goes straight to the Pukka's night-2 card.
#
# What it proves:
#   * 12  E-6 again — resolving the Chambermaid at step 10 with the Devil's
#         Advocate still owed at step 7 wraps back to step 7 rather than opening
#         the DAWN card, whose primary would open the day;
#   * 32  E-4 — the night-2 Pukka card carries "Finn dies now — poisoned by the
#         Pukka last night", the consequence line "Finn: Nothing stops it — they
#         die." ABOVE the button, and a press-and-hold primary reading
#         DEV — POISONED · FINN DIES. It used to read `DEV — POISONED`, with
#         Finn's name nowhere on the card, and one tap killed him in silence.
STEPS = [
    # ---- night 1: the Grandmother, then the Chambermaid ------------------
    ("wait",  "SHOW “FOOL” TO ERIN"),
    ("tap",   "SHOW “FOOL” TO ERIN"),
    ("sleep", 2.0),
    ("hold",  ["HOLD TO CLOSE", "1600"]),
    ("sleep", 1.5),
    ("hold",  ["The grimoire is closed", "1200"]),
    ("sleep", 1.5),
    ("find",  "step 10 / 11"),

    ("tap",   "6  Gita"),
    ("sleep", 0.8),
    ("tap",   "8  Hal"),
    ("sleep", 0.8),
    ("tap",   "SHOW “0” TO CLEO"),
    ("sleep", 2.0),
    ("hold",  ["HOLD TO CLOSE", "1600"]),
    ("sleep", 1.5),
    ("hold",  ["The grimoire is closed", "1200"]),
    ("sleep", 1.5),

    # E-6: the row still owed is step 7, not the closing card.
    ("find",  "step 7 / 11"),
    ("tap",   "They chose nobody"),
    ("sleep", 0.8),
    ("tap",   "THEY CHOSE NOBODY"),
    ("sleep", 2.0),
    ("find",  "step 11 / 11"),

    # ---- dawn, day 1, dusk, night 2 ---------------------------------------
    ("tap",   "OPEN THE DAY"),
    ("sleep", 2.5),
    ("tap",   "OPEN DAY 1"),
    ("sleep", 2.5),
    ("tap",   "^Dusk$"),
    ("sleep", 2.0),
    ("tap",   "BEGIN NIGHT 2"),
    ("sleep", 2.5),
    ("find",  "step 1 / 12"),

    # ---- E-4: the Pukka's night-2 card ------------------------------------
    ("tap",   "whole sheet"),
    ("sleep", 1.5),
    ("tap",   "Pukka — Kai"),
    ("sleep", 1.5),
    ("tap",   "hide sheet"),
    ("sleep", 1.2),
    ("find",  "step 6 / 12"),
    ("find",  "Finn dies now — poisoned by the Pukka last night"),
    ("tap",   "4  Dev"),
    ("sleep", 1.2),
    ("swipe", ["up", "900"]),
    ("sleep", 0.8),
    ("find",  "Finn: Nothing stops it — they die."),
    ("find",  "DEV — POISONED · FINN DIES"),
    ("find",  "press and hold"),
    ("audit", None),
    ("screenshot", None),
]
