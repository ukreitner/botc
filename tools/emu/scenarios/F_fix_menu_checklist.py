# F_fix_menu_checklist — playtest A-4: the "Before the first night" checklist
# has a permanent entry point in the overflow menu.
#
#   ./emu.sh launch emulator-5556 --fresh
#   ./scenario.py emulator-5556 F_fix_menu_checklist
#
# Before the fix the 15-item overflow menu had no row that reached the
# checklist, so once the auto-raised sheet was closed the storyteller's setup
# contract could only be reopened by the side effect of assigning a character to
# a seat. (Fix-D had added a "Setup checklist" row that raised a SECOND sheet of
# GameShell's own; F-1 renamed it to the name the sheet itself wears and routed
# it through `SetupChecklist.open()`, the same opener the begin-night guard's
# "Fix setup" button uses.)
#
# Evidence in the screenshots:
#   step 13  the menu, with "Before the first night… · 5 to do" at the top
#   step 15  the sheet itself: "Before the first night" / "0 of 5 done"
#   step 16  audit — safe area OK, no overlaps

STEPS = [
    ("wait", "New game"),
    ("tap", "New game"),
    ("wait", "TABLE"),
    ("tap", "Trouble Brewing"),
    ("sleep", 2.0),
    ("wait", "Add seat"),
    ("tap", "^TABLE$"),                       # collapse the seat editor again
    ("sleep", 1.0),
    ("tap", "Start empty"),

    # The sheet auto-raises on a fresh game. Close it — this is the state A-4
    # says there is no way back from.
    ("wait", "Before the first night"),
    ("tap", "^Close$"),
    ("sleep", 1.0),

    # …and back in, from the menu.
    ("tap", "^Menu$"),
    ("wait", "Before the first night… · 5 to do"),
    ("tap", "Before the first night"),
    ("wait", "0 of 5 done"),
    ("audit", None),
    ("tap", "^Close$"),
    ("sleep", 1.0),
    ("wait", "^Seat 1,"),
]
