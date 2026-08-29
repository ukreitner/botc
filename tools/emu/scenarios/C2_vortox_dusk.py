# C2_vortox_dusk — the Vortox's "no execution = evil wins" ending at dusk.
#
#   ./emu.sh launch emulator-5556 --fresh
#   ./scenario.py emulator-5556 C2_vortox_dusk
#
# Sects & Violets, 8 seats, seat 1 = Vortox, straight through night 1 to day 1,
# then close the day with NO EXECUTION.
#
# What the screenshots have to show:
#   ~step 32  MORNING BRIEFING carries both Vortox standing facts —
#             "Someone must be executed today, or the Vortox wins for evil."
#             and "VORTOX ALIVE — every Townsfolk answer must be FALSE".
#   ~step 37  the dusk sheet prints
#             "No execution today and the Vortox is alive and sober — evil
#             wins." TWICE: once as the sheet's own headline (PhaseFlow.kt:484
#             renders `advisories`) and again as the BEFORE YOU MOVE ON bullet
#             the same WinCheck.duskCheck put in the briefing.        [C2-2]
#   ~step 41  after the primary, the game is at NIGHT 2 and NO "Is the game
#             over? / Declare evil victory" dialog was ever offered — the
#             `absent` step is the assertion.                         [C2-1]

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
    # The setup checklist raises itself over the open seat sheet (known,
    # fix-wave-3 queue); close it, then leave the seat sheet.
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

    # --- the two Vortox standing facts -----------------------------------
    ("tap", "MORNING BRIEFING"),
    ("sleep", 1.5),
    ("find", "Someone must be executed today"),
    ("find", "VORTOX ALIVE"),
    ("screenshot", None),

    # --- dusk with no execution ------------------------------------------
    ("tap", "^DUSK$"),
    ("sleep", 1.5),
    ("tap", "Everyone, eyes closed"),
    ("sleep", 2.0),
    ("screenshot", None),                    # advisory printed twice  [C2-2]
    ("audit", None),
    ("find", "No execution today and the Vortox"),

    # Anchor on the BUTTON: "NO EXECUTION" alone also matches the advisory
    # bullet, and a tap there is swallowed by the sheet's own text.
    ("tap", "NO EXECUTION — BEGIN"),
    ("sleep", 3.0),
    ("screenshot", None),
    # C2-1: the ending the sheet just announced is never offered as an action.
    ("absent", "Is the game over|Declare evil victory"),
    ("find", "Everyone closes their eyes"),
]
