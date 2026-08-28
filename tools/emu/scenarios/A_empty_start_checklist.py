# A-1: a 6-row "Before the first night" sheet pushes its own Close button
#      under the home indicator.
#
# Trouble Brewing + the Sentinel + "Start empty" gives 5 bag rows + 1 Sentinel
# row = 6 rows. The sheet does not scroll, so "Close" lands at y=2349, i.e.
# 33 px below the safe-area bottom (2316) and inside the gesture strip.
#
# Reproduces:
#   * P1  sheet's only "Close" button is CENTRE UNTAPPABLE (ui.py tap refuses it)
#   * P2  five identical "The bag is not legal yet" row titles
#
# Run with:
#   ./emu.sh launch emulator-5554 --fresh
#   ./scenario.py emulator-5554 A_empty_start_checklist

STEPS = [
    ("wait",       "New game"),
    ("tap",        "New game"),
    ("wait",       "SCRIPT"),
    ("tap",        "^Trouble Brewing$"),
    ("sleep",      1.0),
    ("tap",        "8 seats"),            # collapse TABLE
    ("sleep",      0.8),
    ("tap",        "FABLED"),             # expand card 4
    ("sleep",      1.0),
    ("swipe",      ["up", "700"]),        # scroll the nested Fabled list
    # Two swipes since the fix wave: card 4 is titled "FABLED" rather than
    # "FABLED & HOUSE RULES" (A-14), so its header is a line shorter and the
    # nested list starts higher up.
    ("swipe",      ["up", "700"]),
    ("sleep",      0.8),
    ("tap",        "^Sentinel$"),
    ("sleep",      1.0),
    ("tap",        "Start empty"),
    ("wait",       "Before the first night"),
    ("sleep",      1.0),
    ("audit",      None),                 # <- Close is CENTRE UNTAPPABLE
    ("screenshot", None),
]
