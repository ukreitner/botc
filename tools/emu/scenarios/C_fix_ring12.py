# C_fix_ring12 — the nomination ring at TWELVE seats, which is where it used to
# overlap itself and the vote chips (playtest D finding 6).
#
#   ./emu.sh launch emulator-5558 --fresh
#   ./scenario.py emulator-5558 C_fix_ring12
#
# The last step is the one that matters: `audit` with the ring, the check card
# and the vote chips all on screen must report
#   "overlap: OK — no two clickable nodes partially overlap"
# Before the fix a 12-seat ring reported ten overlapping pairs, worst 41 %.

STEPS = [
    ("wait", "New game"),
    ("tap", "New game"),
    ("wait", "TABLE"),
    ("tap", "Trouble Brewing"),
    ("sleep", 2.0),
    ("tap", "Dismiss update banner"),
    ("sleep", 1.0),

    # 8 seats by default; four more. TABLE opens itself once the script is
    # chosen, so it must NOT be tapped — that collapses it.
    # Each new seat pushes the button down, so scroll to it every time.
    ("wait", "Add seat"),
    ("tap", "Add seat"),
    ("sleep", 0.8),
    ("swipe", ["up", "400"]),
    ("sleep", 0.8),
    ("tap", "Add seat"),
    ("sleep", 0.8),
    ("swipe", ["up", "400"]),
    ("sleep", 0.8),
    ("tap", "Add seat"),
    ("sleep", 0.8),
    ("swipe", ["up", "400"]),
    ("sleep", 0.8),
    ("tap", "Add seat"),
    ("sleep", 1.2),

    ("wait", "Start empty"),
    ("tap", "Start empty"),
    ("wait", "Before the first night"),
    ("tap", "^Close$"),
    ("sleep", 1.2),

    ("tap", "Begin night"),
    ("wait", "Start the night anyway"),
    ("tap", "Start the night anyway"),
    ("sleep", 2.0),
    ("tap", "^Dawn$"),
    ("wait", "Dawn anyway"),
    ("tap", "Dawn anyway"),
    ("wait", "OPEN DAY 1"),
    ("tap", "OPEN DAY 1"),
    ("sleep", 2.0),

    ("tap", "Nominate"),
    ("sleep", 1.5),
    ("audit", None),                 # the ring alone, twelve seats
    ("tap", "^Player 3$"),
    ("sleep", 1.5),
    ("tap", "^Player 9$"),
    ("sleep", 2.5),
    ("screenshot", None),
    ("audit", None),                 # ring + check card + vote chips
    ("swipe", ["up", "400"]),
    ("sleep", 1.5),
    ("audit", None),                 # and with the list scrolled under it
]
