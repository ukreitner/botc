# Playtest D P1-5: Remove / Suspend / Restore on a hand-placed token.
#
#   ./emu.sh launch emulator-5560 --fresh
#   ./scenario.py emulator-5560 D_fix_tokens
#
# Expected, step by step:
#   17 the token reads "Drunk (turned over)" and the button says "Restore"
#   21 it reads "Drunk" again and the button says "Suspend"
#   25 STATUS is back to "No tokens on this seat."
# NOTE: build with `-PbuildSha=dev`, or dismiss the sideload update
# banner first (`./ui.py <serial> tap "Dismiss update banner"`) — it is
# pinned to the bottom of every screen and covers the setup buttons.
STEPS = [
    ("wait",  "New game"),
    ("tap",   "New game"),
    ("wait",  "TABLE"),
    ("tap",   "Trouble Brewing"),
    ("tap",   "Start empty"),
    ("sleep", 1.5),
    ("tap",   "^Close$"),
    ("sleep", 1.0),

    ("tap",   "^Seat 1,"),
    ("sleep", 1.2),
    ("tap",   r"\+ Token"),
    ("sleep", 1.5),
    ("tap",   "^Drunk$"),
    ("sleep", 1.5),

    # Suspend turns it over: the rule stops, the token stays.
    ("tap",   "^Suspend$"),
    ("sleep", 1.2),
    ("find",  "Drunk \\(turned over\\)"),
    ("find",  "^Restore$"),

    # Restore puts it back.
    ("tap",   "^Restore$"),
    ("sleep", 1.2),
    ("find",  "^Suspend$"),

    # Remove takes it off for good.
    ("tap",   "^Remove$"),
    ("sleep", 1.2),
    ("find",  "No tokens on this seat"),
]
