# G_fix_secret_votes — the secret-vote UI, reached for the first time (G-2).
#
#   ./emu.sh launch emulator-5558 --fresh
#   ./scenario.py emulator-5558 G_fix_secret_votes
#
# Playtest C could not reach any of this: `DayRules.secretVoting` was true only
# for a living, unimpaired Organ Grinder, which is on no base script and cannot
# be picked from the seat sheet (it searches the script only). So the hold-to-
# peek tally, the hidden on-block banner, "Lock in silently" and the eyes-closed
# standing fact shipped never having been rendered once.
#
# `GameState.houseRules.secretVotes` is the hand switch `ux/day-screen.md` §F
# asked for, and card 4 of the setup screen — now "FABLED & HOUSE RULES" (A-14)
# — is where it is set.
#
# What the screenshots have to show:
#   ~step 10  the card is called FABLED & HOUSE RULES and its summary reads
#             "no fabled · by the book"                                 [A-14]
#   ~step 13  the switch reads "Secret votes" and turns CHECKED
#   ~step 26  the day's stat strip: "Secret voting — a house rule for this
#             game", no "N to beat", "Nobody is about to die."
#   ~step 29  MORNING BRIEFING: "Eyes closed for every vote today — the tally
#             is secret. (House rule.)" — and it does NOT name an Organ
#             Grinder, because there is not one in the game
#   ~step 38  the vote panel: SECRET, "Eyes closed, everyone. (House rule:
#             every vote is secret.)", the tally as •••
#   ~step 41  hold-to-peek reveals the count, "Lock in silently" is the button
#   ~step 45  after locking in, the strip says "Someone is about to die." and
#             the recorded row reads "••• votes · •••"

STEPS = [
    ("wait", "New game"),
    ("tap", "New game"),
    ("wait", "SCRIPT"),
    ("tap", "Trouble Brewing"),
    ("sleep", 1.5),
    ("tap", "^Collapse$"),
    ("sleep", 1.0),

    # --- card 4: the house rule ------------------------------------------
    ("wait", "FABLED & HOUSE RULES"),
    ("find", "no fabled · by the book"),
    ("tap", "FABLED & HOUSE RULES"),
    ("sleep", 1.0),
    ("wait", "Secret votes"),
    ("screenshot", None),
    ("tap", "Secret votes"),
    ("sleep", 1.0),
    ("audit", None),
    ("screenshot", None),

    # --- straight into a game --------------------------------------------
    ("tap", "Start empty"),
    ("wait", "Before the first night"),
    ("tap", "^Close sheet$"),
    ("sleep", 1.2),
    ("tap", "Begin night"),
    ("sleep", 1.0),
    ("tap", "Start the night anyway"),
    ("sleep", 1.5),
    ("tap", "^Dawn$"),
    ("sleep", 1.0),
    ("tap", "Dawn anyway"),
    ("sleep", 1.5),
    ("tap", "OPEN DAY 1"),
    ("sleep", 1.5),

    # --- the day tab knows the eyes are closed ---------------------------
    ("wait", "Secret voting — a house rule"),
    ("find", "Nobody is about to die"),
    ("screenshot", None),
    ("tap", "MORNING BRIEFING"),
    ("sleep", 1.0),
    ("find", "Eyes closed for every vote today"),
    ("find", "House rule"),
    ("screenshot", None),

    # --- a nomination, counted with the eyes closed ----------------------
    ("tap", "Nominate"),
    ("sleep", 1.2),
    ("tap", "^Player 1$"),
    ("sleep", 0.8),
    ("tap", "^Player 5$"),
    ("sleep", 1.2),
    ("wait", "SECRET"),
    ("find", "House rule: every vote is secret"),
    ("find", "hold to peek"),
    ("screenshot", None),
    ("audit", None),

    # Four hands up, and the tally still says nothing out loud.
    ("swipe", ["up", "400"]),
    ("sleep", 0.8),
    ("tapxy", [176, 1620]),
    ("tapxy", [407, 1620]),
    ("tapxy", [638, 1620]),
    ("tapxy", [869, 1620]),
    ("sleep", 1.0),
    ("find", "•••"),
    ("screenshot", None),
    ("hold", ["hold to peek", "900"]),
    ("sleep", 0.5),
    ("screenshot", None),
    ("swipe", ["up", "500"]),
    ("sleep", 0.8),
    ("wait", "Lock in silently"),
    ("screenshot", None),
    ("tap", "Lock in silently"),
    ("sleep", 1.5),
    ("find", "Someone is about to die"),
    ("find", "••• votes"),
    ("screenshot", None),
    ("audit", None),

    # --- and the rule is reachable mid-game, from Menu → Fabled ------------
    # A table that agrees a rule after the first night should not have to start
    # a new game (A-14). Turning it off here re-opens every eye at once.
    ("tap", "^Menu$"),
    ("wait", "Fabled…"),
    ("tap", "Fabled…"),
    ("wait", "Fabled & house rules"),
    ("find", "Secret votes"),
    ("screenshot", None),
    ("tap", "Secret votes"),
    ("sleep", 1.0),
    ("back", None),
    ("sleep", 1.2),
    ("find", "On the block: Player 5"),
    ("screenshot", None),
]
