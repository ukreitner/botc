# F_fix_dusk — playtest C-18 and C-16: one path to dusk, and the dusk sheet
# leads with the button that records what happened.
#
#   ./emu.sh launch emulator-5556 --fresh
#   ./scenario.py emulator-5556 F_fix_dusk
#
# C-18: the moon icon between Redo and ⋮ opened the same dusk sheet as the Day
# tab's [Everyone, eyes closed ▸] — two paths to the most destructive control in
# the app, one of them unlabelled. ux/day-screen §I: the phase button moves off
# the top bar for the day. The assertion is made from the GRIMOIRE tab, because
# the Day tab composes a DUSK stage card whose header matches the same regex.
#
# C-16: the sheet used to offer [BEGIN NIGHT 2 →] — which records nothing — as
# its primary, with the NO_EXECUTION record the Mayor, the Vortox and the
# Zombuul all read demoted to a text button beside "Not yet".
#
# Evidence in the screenshots:
#   step 23  the grimoire during day 1: top bar is Hide / Undo / Redo / ⋮ only
#   step 29  the dusk sheet: [NO EXECUTION — BEGIN NIGHT 2 →] on its own row,
#            above [Begin night without recording] [Not yet]
#   step 35  after Undo, day 1's DUSK card reads "No execution today — recorded"

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

    # --- straight through night 1 -----------------------------------------
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

    # --- C-18: no Dusk control in the top bar -----------------------------
    ("tap", "^Grimoire$"),
    ("sleep", 1.5),
    ("absent", "^Dusk$"),
    ("audit", None),
    ("tap", "^Day$"),
    ("sleep", 1.5),
    ("audit", None),

    # --- C-16: the primary records the day's outcome ----------------------
    ("tap", "^DUSK$"),                        # expand the stage card
    ("sleep", 1.0),
    ("tap", "Everyone, eyes closed"),
    ("wait", "NO EXECUTION — BEGIN NIGHT 2 →"),
    ("find", "Begin night without recording"),
    ("audit", None),
    ("tap", "NO EXECUTION"),
    ("wait", "NIGHT 2"),

    # …and the record is real: undo only the phase advance, and day 1 says so.
    ("tap", "^Undo$"),
    ("sleep", 2.0),
    ("tap", "^Day$"),
    ("sleep", 1.5),
    ("wait", "No execution today — recorded"),
]
