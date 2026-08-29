# Re-test D2 — the seat ring at the sizes the fleet had not audited: 5, 15
# and 16 seats. Trouble Brewing is used only because "Start empty" is the
# quickest route to a ring; the layout formula is edition-independent.
#
#   ./emu.sh launch emulator-5558 --fresh
#   ./scenario.py emulator-5558 D2_rings
#
# Each ring is audited unzoomed. `audit` findings are collected, not fatal,
# so the run reaches all three sizes. Check the node count on every audit —
# a "0 clickable node(s)" audit is the empty-tree flake, not a clean ring
# (tools/emu/README.md).
NAMES_5 = "A1,A2,A3,A4,A5"
NAMES_15 = "B1,B2,B3,B4,B5,B6,B7,B8,B9,B10,B11,B12,B13,B14,B15"
NAMES_16 = "C1,C2,C3,C4,C5,C6,C7,C8,C9,C10,C11,C12,C13,C14,C15,C16"


def ring(names, count):
    return [
        ("wait",  "New game"),
        ("tap",   "New game"),
        ("wait",  "TABLE"),
        ("tap",   "Trouble Brewing"),
        ("sleep", 1.0),
        ("swipe", ["up", "900"]),
        ("tap",   "Paste list"),
        ("sleep", 1.0),
        ("tapxy", ["540", "1214"]),
        ("type",  names),
        ("sleep", 1.0),
        ("tap",   "Use these %d seats" % count),
        ("sleep", 1.0),
        ("tap",   "Start empty"),
        ("sleep", 2.5),
        ("tapxy", ["540", "300"]),      # checklist scrim, or the inert header
        ("sleep", 1.5),
        ("wait",  ["^Seat 1,", "45"]),
        ("audit", None),
        ("screenshot", None),
        # back to Home for the next size
        ("tap",   "^Menu$"),
        ("sleep", 1.2),
        ("tap",   "Back to home"),
        ("sleep", 2.0),
        ("tap",   "End current game"),
        ("sleep", 1.5),
        ("tap",   "Archive & end game"),
        ("sleep", 2.0),
    ]


STEPS = ring(NAMES_5, 5) + ring(NAMES_15, 15) + ring(NAMES_16, 16)
