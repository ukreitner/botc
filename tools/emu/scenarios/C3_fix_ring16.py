# C3_fix_ring16 — the sixteen-seat ring, un-zoomed and at 2x zoom (D2-7).
#
#   ./emu.sh launch emulator-5556 --fresh
#   ./scenario.py emulator-5556 C3_fix_ring16
#
# Split out of `C3_fix_safe_area` because the crowded grimoire is where
# `uiautomator dump` goes quiet for a while (README, "After an install, the tree
# can come back empty"); a flake here must not cost the twelve-seat audits.
# Re-launch WITHOUT `--fresh` and run it again if a step reports
# "0 interesting of N nodes" — that is the empty tree, not the app.
#
# D2 measured seven overlapping pairs here at 2x zoom, worst 47 % between the
# Search field and a seat. The un-zoomed audit is the one that must be clean.

NAMES_16 = "B1,B2,B3,B4,B5,B6,B7,B8,B9,B10,B11,B12,B13,B14,B15,B16"

STEPS = [
    ("wait",  "New game"),
    ("tap",   "New game"),
    ("wait",  "TABLE"),
    ("tap",   "Bad Moon Rising"),
    ("sleep", 1.0),
    ("swipe", ["up", "900"]),
    ("tap",   "Paste list"),
    ("sleep", 1.0),
    ("tapxy", ["540", "1214"]),
    ("type",  NAMES_16),
    ("sleep", 1.0),
    ("tap",   "Use these 16 seats"),
    ("sleep", 1.0),
    ("tap",   "Start empty"),
    ("sleep", 3.0),
    ("wait",  "Before the first night"),
    ("tap",   "^Close$"),
    ("sleep", 2.5),
    ("wait",  ["^Seat 1,", "45"]),
    ("audit", None),
    ("screenshot", None),
    ("tap",   "Zoom in"),
    ("sleep", 1.0),
    ("tap",   "Zoom in"),
    ("sleep", 1.5),
    ("screenshot", None),
    ("audit", None),
]
