# C_fix_day — the C_day_repro path, repaired for the current setup screen, and
# re-pointed at the fixes.
#
#   ./emu.sh launch emulator-5558 --fresh
#   ./scenario.py emulator-5558 C_fix_day
#
# `C_day_repro` was written against build 2020fec and no longer runs: the setup
# screen is now a numbered checklist whose TABLE section starts COLLAPSED (so
# "Add seat" is not on screen), and "Start empty · assign by hand" sits under
# the "New build available" banner until it is dismissed. Same game otherwise:
# Trouble Brewing, 8 seats, seat 1 = Butler, a hand-placed butler:Master on
# seat 2.
#
# What the screenshots have to show now:
#   ~step 40  the nomination check card carries NO "Did Player 1 claim to be
#             the Goblin?" — Trouble Brewing has no Goblin.            [C-2]
#   ~step 46  the Butler's chip is tapped and the tally stays 0 of 4, with
#             "Player 1's hand is up but does not count — their Master's hand
#             is down."                                                [C-1/C-3]
#   ~step 43  `audit` reports no overlapping clickables with the vote panel
#             in view.                                                 [C-6]

STEPS = [
    ("wait", "New game"),
    ("tap", "New game"),
    ("wait", "TABLE"),
    ("tap", "Trouble Brewing"),
    ("sleep", 2.0),
    # The update banner used to cover the setup screen's own primary action and
    # had to be dismissed here. A debug build no longer runs the update check on
    # an emulator at all (README, "The update banner is off on emulators"), so
    # there is nothing to dismiss and the step failed as a missing node.
    ("wait", "Start empty"),
    ("tap", "Start empty"),
    ("wait", "Before the first night"),
    ("tap", "^Close$"),
    ("sleep", 1.2),

    # --- seat 1 = Butler -------------------------------------------------
    ("tap", "^Seat 1,"),
    ("wait", "Change"),
    ("tap", "Change…"),
    ("wait", "Search characters"),
    ("tap", "Search characters"),
    ("type", "Butler"),
    ("sleep", 0.9),
    ("back", None),
    ("sleep", 0.6),
    ("tap", "you may only vote if they are voting"),
    # C-9 (Fix-A's): the setup checklist re-opens itself over the seat sheet.
    ("wait", "Before the first night"),
    ("tap", "^Close$"),
    ("sleep", 1.2),
    ("back", None),
    ("wait", "^Seat 1,"),
    ("sleep", 1.0),

    # --- seat 2 carries the Butler's Master reminder ----------------------
    ("tap", "^Seat 2,"),
    ("wait", "\\+ Token"),
    ("tap", "\\+ Token"),
    ("wait", "^Master$"),
    ("tap", "^Master$"),
    ("wait", "Kill…"),
    ("sleep", 1.5),
    ("back", None),
    ("wait", "^Seat 1,"),
    ("sleep", 1.0),

    # --- straight through night 1 ----------------------------------------
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

    # C-6: the Butler's Master is a standing fact of the morning briefing.
    ("tap", "MORNING BRIEFING"),
    ("sleep", 1.5),
    ("screenshot", None),

    # --- the nomination -------------------------------------------------
    ("tap", "Nominate"),
    ("sleep", 1.5),
    ("tap", "^Player 3$"),
    ("sleep", 1.5),
    ("tap", "^Player 1$"),
    ("sleep", 2.5),
    ("screenshot", None),                    # no Goblin question   [C-2]
    ("audit", None),                         # no overlaps          [C-6]

    # --- C-1 / C-3: the Butler's illegal hand ---------------------------
    # The vote panel scrolls itself into view now, so the chips are addressable
    # by name; "Player 1 " with the trailing space is the chip, not the ring.
    ("find", "^Player 1$"),
    ("sleep", 0.5),
    ("screenshot", None),
]
