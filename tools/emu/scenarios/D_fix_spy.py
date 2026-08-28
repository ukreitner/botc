# Playtest D P1-7: "Show the grimoire to a player…" — both stages.
#
#   ./emu.sh launch emulator-5560 --fresh
#   ./scenario.py emulator-5560 D_fix_spy
#
# Expected: `audit` reports no safe-area violation in either stage, `tap`
# reaches HAND IT OVER and DONE — BACK TO THE SHEET without `tapxy`, and
# `back` leaves stage 2 instead of being swallowed.
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

    ("tap",   "^Menu$"),
    ("sleep", 1.2),
    ("tap",   "Show the grimoire to a player"),
    ("sleep", 1.8),
    ("audit", None),                    # stage 1
    ("tap",   "HAND IT OVER"),          # refuses if it is still off-screen
    ("sleep", 1.5),
    ("audit", None),                    # stage 2, the player's view
    ("back",  None),                    # a player must be able to get out
    ("sleep", 1.5),
    ("find",  "grimoire is closed"),    # back lands on the re-armed cover

    # And the button works too, from a fresh open.
    ("hold",  ["press and hold to open", "1500"]),
    ("sleep", 1.2),
    ("tap",   "^Menu$"),
    ("sleep", 1.2),
    ("tap",   "Show the grimoire to a player"),
    ("sleep", 1.5),
    ("tap",   "HAND IT OVER"),
    ("sleep", 1.5),
    ("tap",   "DONE — BACK TO THE SHEET"),
    ("sleep", 1.5),
    ("find",  "grimoire is closed"),
]
