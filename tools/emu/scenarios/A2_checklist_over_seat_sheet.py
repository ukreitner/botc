# Fix-wave-3 queue item, CONFIRMED still open on 978692b, and characterised.
#
#   "During SETUP the 'Before the first night' checklist raises itself over the
#    OPEN seat sheet whenever an assignment introduces a new requirement row."
#
# Assign the Drunk to seat 1 from the seat sheet's own picker. The picker closes
# back onto the seat sheet (correct), and ~1 s later the checklist sheet slides
# up ON TOP of it because "The Drunk believes …" is a new row.
#
# Characterisation for the fix wave:
#   * the trigger is a NEW requirement row, not any assignment — a Butler on a
#     Sentinel table adds nothing and raises nothing;
#   * the seat sheet is not destroyed, only covered: `back` dismisses the
#     CHECKLIST and the seat sheet is still underneath, still on the seat you
#     were editing ("Drunk · Outsider"), so this costs a tap rather than the
#     work. It is not a dead end;
#   * `audit` on the stacked sheets is clean — nothing is off-screen or
#     overlapping, so only the covering itself is the bug.
#
# Run with:
#   ./emu.sh launch emulator-5560 --fresh
#   ./scenario.py emulator-5560 A2_checklist_over_seat_sheet

STEPS = [
    ("wait",       "New game"),
    ("tap",        "New game"),
    ("wait",       "SCRIPT"),
    ("tap",        "^Trouble Brewing$"),
    ("sleep",      1.2),
    ("tap",        "Start empty"),
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
    ("sleep",      2.5),

    # The checklist has raised itself over the still-open seat sheet.
    ("find",       "Before the first night"),
    ("find",       "The Drunk believes"),
    ("audit",      None),
    ("screenshot", None),

    # `back` closes the CHECKLIST; the seat sheet is still there, on the Drunk.
    ("back",       None),
    ("sleep",      1.5),
    ("absent",     "Before the first night"),
    ("find",       "Drunk · Outsider"),
    ("find",       "Change…"),
    ("screenshot", None),
]
