# C3_fix_safe_area — the overlays and the zoomed ring, audited.
#
#   ./emu.sh launch emulator-5556 --fresh
#   ./scenario.py emulator-5556 C3_fix_safe_area
#
# Bad Moon Rising, twelve seats and then sixteen, so the ring is audited at both
# sizes D2 measured, un-zoomed and at 2× zoom. `audit` findings are collected
# rather than fatal, so the run reaches every screen — read the node count on
# each: "0 clickable node(s)" is the empty-tree flake, not a clean screen
# (tools/emu/README.md).
#
# What it proves:
#   D2-6  the seat sheet's `Change…` and `+ Token` pickers audit CLEAN. Their
#         scroll container is `[53,127][1027,2253]` where B2 and D2 measured
#         `[53,127][1027,2337]`: it stops at the safe edge and then keeps the
#         24 dp margin a finger needs, so no row is ever offered half-drawn
#         into the gesture strip. B2 measured "bottom 59px under the …inset" on
#         the last character row; D2 measured the Tea Lady's `Cannot Die ×2`
#         with its centre UNTAPPABLE.
#   D2-5  the bluffs sheet audits clean, container `[53,127][1027,2190]`. It
#         used to run to y=2400, the physical bottom of the display, with two
#         rows under the gesture strip and one centre untappable.
#   D2-7  the UN-ZOOMED ring audits clean at 12 and 16 seats, controls and all
#         — the zoom column has a lane of its own now, which is also the known
#         8-seat 5 % overlap's cause. ZOOMED it still reports overlaps: a
#         `graphicsLayer` scale moves a seat's reported bounds without the
#         canvas clip reaching them, so this is the same unclipped-bounds
#         artefact one order larger. Left visible here deliberately.

NAMES_12 = "A1,A2,A3,A4,A5,A6,A7,A8,A9,A10,A11,A12"


def seated(names, count):
    """A fresh Bad Moon Rising game with `count` seats, nothing assigned."""
    return [
        ("wait",  "New game"),
        ("tap",   "New game"),
        ("wait",  "TABLE"),
        ("tap",   "Bad Moon Rising"),
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
        # Dismiss the checklist by its own button rather than by tapping the
        # scrim: on a crowded ring the scrim tap left `uiautomator dump`
        # reporting the system windows and nothing else for minutes (README).
        ("wait",  "Before the first night"),
        ("tap",   "^Close$"),
        ("sleep", 2.0),
        ("wait",  ["^Seat 1,", "45"]),
    ]


def rings():
    """Un-zoomed, then two taps of zoom, audited at each stage."""
    return [
        ("audit", None),
        ("screenshot", None),
        ("tap",   "Zoom in"),
        ("sleep", 1.0),
        ("tap",   "Zoom in"),
        ("sleep", 1.5),
        ("screenshot", None),
        ("audit", None),
        ("tap",   "Reset zoom and recenter"),
        ("sleep", 1.5),
    ]


OVERLAYS = [
    # --- D2-6: the two seat-sheet pickers ---------------------------------
    ("tap",   "^Seat 3,"),
    ("wait",  "Change"),
    ("tap",   "Change…"),
    ("wait",  "Search characters"),
    ("sleep", 1.5),
    ("screenshot", None),
    ("audit", None),
    ("dump",  None),                    # the container's own bounds
    ("tap",   "^Back$"),
    ("sleep", 1.5),

    ("wait",  "\\+ Token"),
    ("tap",   "\\+ Token"),
    ("sleep", 1.5),
    ("screenshot", None),
    ("audit", None),
    ("dump",  None),                    # the container's own bounds
    ("back",  None),
    ("sleep", 1.5),

    # --- D2-5: the bluffs sheet, which needs a Demon to have candidates ----
    ("tap",   "^Seat 1,"),
    ("wait",  "Change"),
    ("tap",   "Change…"),
    ("wait",  "Search characters"),
    ("tap",   "Search characters"),
    ("type",  "Zombuul"),
    ("sleep", 1.0),
    ("back",  None),
    ("sleep", 0.8),
    ("tap",   "if no-one died today"),
    ("sleep", 2.5),
    ("wait",  "Choose 3 bluffs"),
    ("tap",   "Choose 3 bluffs"),
    ("sleep", 2.5),
    ("screenshot", None),
    ("audit", None),
    ("dump",  None),
    ("back",  None),
    ("sleep", 1.5),
    ("tap",   "^Close$"),
    ("sleep", 1.5),
    ("back",  None),                    # the seat sheet is still open
    ("sleep", 1.5),
]

# The sixteen-seat ring lives in `C3_fix_ring16`, on its own: the crowded
# grimoire is where `uiautomator dump` goes quiet (README, "After an install,
# the tree can come back empty"), and a flake there must not cost the twelve
# audits above.
STEPS = seated(NAMES_12, 12) + rings() + OVERLAYS
