# G_fix_bag_tray — the setup bag list scrolls clear of the sticky tray (G-3, A-20).
#
#   ./emu.sh launch emulator-5558 --fresh
#   ./scenario.py emulator-5558 G_fix_bag_tray
#
# A-20: a character row clipped at the tray's top edge swallowed taps. The list
# ended flush against the tray, so the last rows could never be scrolled clear
# and a half-row's advertised centre could land on the tray — where the tap does
# nothing, because those pixels belong to the tray.
#
# The list now carries BAG_ROW_CLEARANCE_DP of bottom content padding and clips
# to its own bounds, so every row can be brought fully into the clear.
#
# What the screenshots have to show:
#   ~step 12  the bag is legal (8/8) and the tray lists eight tokens
#   ~step 20  scrolled to the end: the Demon rows and the FABLED & HOUSE RULES
#             card sit well clear of the tray, with dead space between
#   ~step 22  tapping the Imp row at its own centre removes it — 8/8 -> 7/8,
#             which is the tap A-20 reported as swallowed
#   ~step 24  `audit` clean: nothing clickable outside the safe area, nothing
#             overlapping

STEPS = [
    ("wait", "New game"),
    ("tap", "New game"),
    ("wait", "SCRIPT"),
    ("tap", "Trouble Brewing"),
    ("sleep", 1.5),
    ("tap", "^Collapse$"),
    ("sleep", 1.0),
    ("tap", "^BAG$"),
    ("sleep", 1.0),
    ("tap", "Randomize"),
    ("sleep", 1.2),
    ("find", "IN THE BAG · 8 / 8"),
    ("screenshot", None),

    # --- to the end of the character list ---------------------------------
    ("swipe", ["up", "700"]),
    ("swipe", ["up", "700"]),
    ("swipe", ["up", "700"]),
    ("swipe", ["up", "700"]),
    ("swipe", ["up", "700"]),
    ("swipe", ["up", "700"]),
    ("sleep", 1.0),
    ("find", "FABLED & HOUSE RULES"),
    ("screenshot", None),

    # The Demon row is the last character in the list — and it is tappable at
    # its own centre, clear of the tray.
    ("tap", "^Imp$"),
    ("sleep", 1.0),
    ("find", "IN THE BAG · 7 / 8"),
    ("screenshot", None),
    ("audit", None),
]
