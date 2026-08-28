# Playtest B fix wave — the full-screen show card.
#
# Run straight after `B_fix_night1`, which leaves the sheet on a
# "start knowing" step:
#
#     ./scenario.py emulator-5556 B_fix_night1
#     ./scenario.py emulator-5556 B_fix_showcard
#
# Proves:
#   B-5  ⟳ FLIP and HOLD TO CLOSE are fully inside the safe area — `audit`
#        finds no clipped control, and `hold` (which refuses an off-screen
#        target) really closes the card onto the privacy cover
#   B-6  the primary that says SHOW puts the card on screen and writes the
#        `shown:` row, instead of silently ticking the step
STEPS = [
    # The chip: tap shows the card as it stands.
    ("tap",    "^SHOW: "),
    ("sleep",  1.2),
    ("audit",  None),
    ("find",   "HOLD TO CLOSE"),
    ("hold",   ["HOLD TO CLOSE", "1500"]),
    ("sleep",  1.2),
    ("find",   "press and hold to open"),
    ("hold",   ["press and hold to open", "1500"]),
    ("sleep",  1.2),

    # The primary states the same card AND shows it.
    ("find",   "^SHOW “"),
    ("tap",    "^SHOW “"),
    ("sleep",  1.5),
    ("audit",  None),
    ("find",   "HOLD TO CLOSE"),
    ("hold",   ["HOLD TO CLOSE", "1500"]),
    ("sleep",  1.2),
    ("hold",   ["press and hold to open", "1500"]),
    ("sleep",  1.2),

    # …and the row it ticked records WHAT was shown, character included.
    ("tap",    "whole sheet"),
    ("sleep",  0.8),
    ("swipe",  ["down", "700"]),
    ("find",   "shown: "),
]
