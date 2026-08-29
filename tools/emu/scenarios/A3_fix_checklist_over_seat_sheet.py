# FW3-1 (fix wave 3) — the "Before the first night" checklist no longer raises
# itself over an OPEN sheet, and raises itself the moment that sheet closes.
#
# `A2_checklist_over_seat_sheet` is the reproduction: assigning the Drunk from
# the seat sheet's own picker adds the requirement row "The Drunk believes …",
# and the checklist used to slide up ON TOP of the seat sheet ~1 s later. `back`
# then dismissed the CHECKLIST rather than the sheet, so every assignment that
# introduced a row cost an extra tap — and `C_setup10` / `C_setup_rest` stopped
# dead on it.
#
# This scenario is that one inverted:
#   * after the assignment the checklist must be ABSENT and the seat sheet must
#     still be the thing on screen, on the seat that was being edited;
#   * closing the seat sheet must then raise the checklist by itself, carrying
#     the row the assignment created — the raise is deferred, never dropped.
#
# Run with:
#   ./emu.sh launch emulator-5560 --fresh
#   ./scenario.py emulator-5560 A3_fix_checklist_over_seat_sheet

STEPS = [
    ("wait",       "New game"),
    ("tap",        "New game"),
    ("wait",       "SCRIPT"),
    ("tap",        "^Trouble Brewing$"),
    ("sleep",      1.2),
    ("tap",        "Start empty"),
    # Nothing is open when the game starts, so this raise is correct and stands.
    ("wait",       "Before the first night"),
    ("sleep",      1.2),
    ("tap",        "^Close$"),
    ("sleep",      1.5),

    ("tap",        "^Seat 1,"),
    ("wait",       "Change…"),
    ("sleep",      1.0),
    ("tap",        "Change…"),
    ("wait",       "Choose character"),
    ("sleep",      1.0),
    ("tap",        "Search characters"),
    ("sleep",      0.8),
    ("type",       "drunk"),
    ("sleep",      1.2),
    ("tap",        "You do not know you are the Drunk"),
    ("sleep",      3.0),

    # THE FIX: the new requirement row does NOT bury the sheet you are in.
    ("absent",     "Before the first night"),
    ("find",       "Drunk · Outsider"),
    ("find",       "Change…"),
    ("audit",      None),
    ("screenshot", None),

    # Close the seat sheet and the deferred raise arrives, with its new row.
    ("back",       None),
    ("wait",       "Before the first night"),
    ("sleep",      1.0),
    ("find",       "The Drunk believes"),
    ("audit",      None),
    ("screenshot", None),

    # And it is still an ordinary checklist: Close puts it away for good.
    ("tap",        "^Close$"),
    ("sleep",      1.5),
    ("absent",     "Before the first night"),
    ("screenshot", None),
]
