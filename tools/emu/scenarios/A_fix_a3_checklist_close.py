# A-3 (P1) FIXED: the "Before the first night" sheet keeps its Close button.
#
# Before: a six-row checklist put Close at @(540,2349) — 84 px under the home
# indicator, CENTRE UNTAPPABLE — and `ui.py tap "^Close$"` refused with
# OFFSCREEN. Two causes: `ModalBottomSheet` opened PARTIALLY expanded and
# overflowed the bottom of the screen with its content, and the whole sheet was
# one `LazyColumn` whose last item was the button.
#
# This scenario builds the longest checklist the tester saw — the 25-character
# PlaytestA import, started empty — then audits it and presses Close.
#
# Run with:
#   ./emu.sh launch emulator-5554 --fresh
#   ./scenario.py emulator-5554 A_fix_a3_checklist_close

SCRIPT_JSON = (
    "[{id:_meta,name:PlaytestA},ogre,lilmonsta,marionette,washerwoman,librarian,"
    "investigator,chef,empath,fortuneteller,undertaker,monk,ravenkeeper,virgin,"
    "slayer,soldier,mayor,butler,drunk,recluse,saint,poisoner,spy,scarletwoman,"
    "baron,imp]"
)

STEPS = [
    ("wait",       "New game"),
    ("tap",        "New game"),
    ("wait",       "SCRIPT"),
    ("tap",        "Import script \\(paste"),
    ("wait",       "Import script"),
    ("tapxy",      ["540", "1264"]),
    ("sleep",      0.6),
    ("type",       SCRIPT_JSON),
    ("sleep",      1.0),
    ("tap",        "^Import$"),
    ("sleep",      1.5),
    # A-19: a successful import says so and selects the new script itself.
    ("wait",       "Imported \"PlaytestA\""),
    ("sleep",      1.0),
    ("tap",        "Start empty"),
    ("wait",       "Before the first night"),
    ("sleep",      1.2),

    # The audit used to report "CENTRE UNTAPPABLE: under the bottom navigation
    # / gesture inset" for the Close button.
    ("audit",      None),
    ("screenshot", None),
    # And this used to answer OFFSCREEN and stop the run.
    ("tap",        "^Close$"),
    ("sleep",      1.2),
    ("screenshot", None),
]
