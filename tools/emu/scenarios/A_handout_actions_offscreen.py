# A-2: re-entering hand-out mode from the overflow menu pushes its action row
#      off the bottom of the screen.
#
# Entered straight from "Deal & hand out tokens" the screen lays out correctly
# (Next / Start over / Finish later all inside the safe area). Re-entered from
# the game's overflow menu -> "Reveal characters to players...", the whole
# column is ~200 px lower: "Next" crosses the gesture inset and "Start over" /
# "Finish later" sit entirely below y=2316 with their labels clipped to
# bounds [0,0][0,0]. The only way out of the screen is the hardware Back key
# (which drops you on the privacy cover).
#
# Reproduces:
#   * P1  hand-out mode's "Start over" / "Finish later" are unreachable
#   * P2  12 seat chips in the roster grid overlap each other by up to 36 %
#
# Run with:
#   ./emu.sh launch emulator-5554 --fresh
#   ./scenario.py emulator-5554 A_handout_actions_offscreen

STEPS = [
    ("wait",       "New game"),
    ("tap",        "New game"),
    ("wait",       "SCRIPT"),
    ("tap",        "^Trouble Brewing$"),
    ("sleep",      1.0),
    ("swipe",      ["up", "1200"]),
    ("sleep",      0.8),
    ("tap",        "Paste list"),
    ("wait",       "Paste the table"),
    ("tapxy",      ["540", "1214"]),
    ("type",       "Uri,Dana,Ari,Sam,Mia,Jon,Lea,Tom,Ben,Ivy,Max,Zoe"),
    ("sleep",      1.0),
    ("tap",        "Use these 12 seats"),
    ("sleep",      1.2),
    ("back",       None),                 # dismiss the keyboard
    ("sleep",      0.8),
    ("swipe",      ["down", "1200"]),
    ("swipe",      ["down", "1200"]),
    ("sleep",      0.8),
    ("tap",        "12 seats"),           # collapse TABLE
    ("sleep",      0.8),
    ("tap",        "^BAG$"),
    ("sleep",      1.2),
    ("tap",        "^Randomize$"),
    ("sleep",      1.5),
    ("tap",        "Deal & hand out"),
    ("wait",       "HAND OUT TOKENS"),
    ("sleep",      1.0),
    ("audit",      None),                 # first entry: layout OK, chips overlap
    ("tap",        "Finish later"),
    ("sleep",      1.5),
    ("tap",        "^Close$"),
    ("wait",       "Grimoire"),
    ("sleep",      1.0),
    ("tap",        "^Menu$"),
    ("sleep",      1.0),
    ("tap",        "Reveal characters to players"),
    ("wait",       "HAND OUT TOKENS"),
    ("sleep",      1.2),
    ("audit",      None),                 # <- 3 SAFE-AREA VIOLATIONS here
    ("screenshot", None),
]
