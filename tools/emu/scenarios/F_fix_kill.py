# F_fix_kill — playtest C-17: the kill sheet's primary action is visible and
# tappable the moment the sheet opens.
#
#   ./emu.sh launch emulator-5556 --fresh
#   ./scenario.py emulator-5556 F_fix_kill
#
# Before the fix the whole sheet — cause radios, killer chips, the funnel's
# verdict, the protection lists, the override switches AND the buttons — was one
# scrolling column, so [Record the death] opened below the fold and `audit`
# reported "#82 … CENTRE UNTAPPABLE: under the bottom navigation / gesture
# inset (y >= 2316)". `ui.py tap` refuses an off-screen tap, so every `tap
# "Record the death"` step below is itself the assertion.
#
# Evidence in the screenshots:
#   step 13  the sheet as it opens: [Record the death] and [Cancel] pinned
#   step 15  audit — safe area OK, no overlaps
#   step 19  the same two buttons at the SAME y with "Demon attack" chosen,
#            which adds the killer chips and the "Uncertain Demon kill" switch
#            and pushes the sheet to full height — the body scrolls, they do not
#   step 23  seat 1 reads "killed N0" in its history

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
    ("wait", "Before the first night"),
    ("tap", "^Close$"),
    ("sleep", 1.0),

    # --- the sheet as it opens --------------------------------------------
    ("tap", "^Seat 1,"),
    ("wait", "Kill…"),
    ("tap", "Kill…"),
    ("wait", "Record the death"),
    ("audit", None),

    # --- and with the tallest body the sheet has --------------------------
    ("tap", "Demon attack"),
    ("sleep", 1.5),
    ("find", "Record the death"),
    ("audit", None),

    # `tap` refuses an off-screen centre, so this step passing IS the fix.
    ("tap", "Record the death"),
    ("sleep", 2.0),
    ("wait", "killed N"),
]
