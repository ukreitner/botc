# A-9 (P1) FIXED: the bag list no longer jumps under your finger.
#
# Before: the "Need:" line, the four bars and the issue list all sat ABOVE the
# scrolling character list in the same scroll container, and their combined
# height changed by 40-160 px whenever the bag's legality changed. Ticking the
# Baron moved every row below it, so the NEXT tap landed on a different
# character.
#
# This scenario proves it by tapping the second character at coordinates read
# BEFORE the first one was ticked: if the list had moved, the tap would land on
# a neighbour and the bag would not hold exactly the two that were aimed at.
#
#   Scarlet Woman @(396,957) with the Baron unticked
#   -> tick Baron -> tapxy 396 957 -> "IN THE BAG · 2 / 8"
#
# Run with:
#   ./emu.sh launch emulator-5554 --fresh
#   ./scenario.py emulator-5554 A_fix_a9_stable_bag_list

STEPS = [
    ("wait",       "New game"),
    ("tap",        "New game"),
    ("wait",       "SCRIPT"),
    ("tap",        "^Trouble Brewing$"),
    ("sleep",      1.0),
    ("tap",        "8 seats"),            # collapse TABLE
    ("sleep",      0.8),
    ("tap",        "^BAG$"),
    ("sleep",      1.2),
    ("swipe",      ["up", "700"]),
    ("swipe",      ["up", "700"]),
    ("swipe",      ["up", "700"]),
    ("swipe",      ["up", "700"]),
    ("sleep",      1.0),
    ("find",       "^Baron$"),            # [274,642][369,685]
    ("find",       "^Scarlet Woman$"),    # [274,936][518,979] @(396,957)
    ("screenshot", None),

    # The Baron adds "+2 Outsiders", so the bag's legality — and with it the
    # header and the issue list — changes on this tap.
    ("tap",        "^Baron$"),
    ("sleep",      1.2),
    ("find",       "^Scarlet Woman$"),    # must still be [274,936][518,979]
    ("tapxy",      ["396", "957"]),       # the coordinates read BEFORE the tick
    ("sleep",      1.2),
    ("find",       "IN THE BAG · 2 / 8"),
    ("audit",      None),
    ("screenshot", None),
]
