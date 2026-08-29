# C3_fix_vortox_dusk — C2_vortox_dusk with the assertions inverted.
#
#   ./emu.sh launch emulator-5556 --fresh
#   ./scenario.py emulator-5556 C3_fix_vortox_dusk
#
# Same game: Sects & Violets, 8 seats, seat 1 = Vortox, straight through night 1
# to day 1, then close the day with NO EXECUTION.
#
# What it proves:
#   C2-2  the advisory is printed ONCE — `find` on the sentence returns a single
#         node where C2 measured two, ~200 px apart, one of them bulleted.
#   C2-1  the ending is OFFERED where it fires: [Declare evil victory] is on the
#         dusk sheet, and tapping it lands on the reveal ("EVIL WINS") instead of
#         beginning night 2 with the advisory thrown away.
#   C2-13 the button row no longer splits "Not yet" across two lines: each
#         dismissal has its own row.

STEPS = [
    ("wait", "New game"),
    ("tap", "New game"),
    ("wait", "SCRIPT"),
    ("tap", "Sects & Violets"),
    ("sleep", 2.0),
    ("tap", "^Collapse$"),
    ("sleep", 1.2),
    ("wait", "Start empty"),
    ("tap", "Start empty"),
    ("wait", "Before the first night"),
    ("tap", "^Close$"),
    ("sleep", 1.2),

    # --- seat 1 = Vortox --------------------------------------------------
    ("tap", "^Seat 1,"),
    ("wait", "Change"),
    ("tap", "Change…"),
    ("wait", "Search characters"),
    ("tap", "Search characters"),
    ("type", "Vortox"),
    ("sleep", 0.9),
    ("back", None),
    ("sleep", 0.6),
    ("tap", "Townsfolk abilities"),
    # The setup checklist raises itself over the open seat sheet (SETUP only —
    # and in SETUP it is still titled "Before the first night", C2-7).
    ("wait", "Before the first night"),
    ("tap", "^Close$"),
    ("sleep", 1.2),
    ("back", None),
    ("wait", "^Seat 1,"),
    ("sleep", 1.0),

    # --- straight through night 1 ----------------------------------------
    ("tap", "Begin night"),
    ("wait", "Start the night anyway"),
    ("tap", "Start the night anyway"),
    ("wait", "Demon bluffs"),
    ("tap", "^Close$"),
    ("sleep", 1.5),
    ("tap", "^Dawn$"),
    ("wait", "Dawn anyway"),
    ("tap", "Dawn anyway"),
    ("wait", "OPEN DAY 1"),
    ("tap", "OPEN DAY 1"),
    ("sleep", 2.0),

    # --- dusk with no execution ------------------------------------------
    ("tap", "^DUSK$"),
    ("sleep", 1.5),
    ("tap", "Everyone, eyes closed"),
    ("sleep", 2.0),
    ("screenshot", None),
    ("audit", None),
    # C2-2: ONE copy. `find` prints every match, so the step's own output is
    # the evidence — two nodes here is the bug coming back.
    ("find", "No execution today and the Vortox"),
    # C2-1: the ending is actionable on the sheet that announced it.
    ("find", "Declare evil victory"),
    ("screenshot", None),
    ("tap", "Declare evil victory"),
    ("sleep", 2.5),
    ("screenshot", None),
    ("find", "EVIL WINS"),
    # …and the game did NOT quietly begin night 2 instead.
    ("absent", "Everyone closes their eyes"),
    ("audit", None),
]
