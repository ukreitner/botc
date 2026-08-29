# C3_fix_vizier — C2_grind_vizier with C2-4, C2-5 and C2-12 inverted.
#
#   ./emu.sh launch emulator-5556 --fresh
#   ./scenario.py emulator-5556 C3_fix_vizier
#
# Same game: an IMPORTED script with a real, in-play, sober Organ Grinder and a
# Vizier, so secret voting and day-immunity are driven by the characters rather
# than by a house rule.
#
# What it proves:
#   C2-4  no raw `EffectKind` constant reaches the storyteller: `absent
#         DAY_IMMUNE` on the execution sheet, where C2 read
#         "! Player 2 carries DAY_IMMUNE — check whether it stops this
#         execution."
#   C2-5  the protection is stated ONCE. The funnel's verdict line owns the
#         sentence; the row that repeated it and the row that printed the enum
#         are both gone.
#   C2-12 the vote panel's own outcome line is concealed with the tally — it
#         reads "••• — hold the tally to peek", not "Player N is about to die."
#
# Ring seats and vote chips are tapped BY NAME: no chips exist until both halves
# are picked, so "Player N" is unambiguously the ring seat for the two ring
# taps, and unambiguously a chip afterwards.

SCRIPT_JSON = (
    '[{"id":"_meta","name":"C2 Grind"},"virgin","mayor","butler","saint",'
    '"poisoner","organgrinder","vizier","psychopath","baron","imp"]'
)

STEPS = [
    ("wait", "New game"),
    ("tap", "New game"),
    ("wait", "SCRIPT"),

    # --- the imported script ---------------------------------------------
    ("tap", "Import script \\(paste"),
    ("wait", "Paste a share LINK"),
    ("tapxy", ["540", "1264"]),
    ("sleep", 0.8),
    ("type", SCRIPT_JSON),
    ("sleep", 1.2),
    ("tap", "^Import$"),
    ("sleep", 2.0),
    ("find", "C2 Grind"),
    ("screenshot", None),

    ("tap", "^Collapse$"),
    ("sleep", 1.2),
    ("wait", "Start empty"),
    ("tap", "Start empty"),
    ("wait", "Before the first night"),
    ("tap", "^Close$"),
    ("sleep", 1.2),

    # --- seat 1 = Organ Grinder ------------------------------------------
    ("tap", "^Seat 1,"),
    ("wait", "Change"),
    ("tap", "Change…"),
    ("wait", "Search characters"),
    ("tap", "Search characters"),
    ("type", "Organ"),
    ("sleep", 0.9),
    ("back", None),
    ("sleep", 0.6),
    ("tap", "All players keep their eyes closed when voting"),
    ("wait", "Before the first night"),
    ("tap", "^Close$"),
    ("sleep", 1.2),
    ("back", None),
    ("wait", "^Seat 1,"),
    ("sleep", 1.0),

    # --- seat 2 = Vizier --------------------------------------------------
    ("tap", "^Seat 2,"),
    ("wait", "Change"),
    ("tap", "Change…"),
    ("wait", "Search characters"),
    ("tap", "Search characters"),
    ("type", "Vizier"),
    ("sleep", 0.9),
    ("back", None),
    ("sleep", 0.6),
    ("tap", "All players know you are the Vizier"),
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
    ("sleep", 2.5),
    ("tap", "^Dawn$"),
    ("wait", "Dawn anyway"),
    ("tap", "Dawn anyway"),
    ("wait", "OPEN DAY 1"),
    ("tap", "OPEN DAY 1"),
    ("sleep", 2.0),

    ("find", "Secret voting — the Organ Grinder is sober"),
    ("tap", "MORNING BRIEFING"),
    ("sleep", 1.5),
    ("find", "cannot die during the day \\(Vizier\\)"),
    ("screenshot", None),
    ("audit", None),

    # --- nominate the Vizier ---------------------------------------------
    ("tap", "^Nominate$"),
    ("sleep", 1.5),

    ("tap", "^Player 3$"),
    ("sleep", 1.5),
    ("tap", "^Player 2$"),
    ("sleep", 2.5),
    ("screenshot", None),                    # the Vizier's nomination row
    # The ring has collapsed to the pair (C2-9), so the whole vote panel is on
    # screen and the chips are addressable.
    ("find", "SECRET"),
    ("find", "Eyes closed, everyone"),
    # C2-12: concealed, and NOT the name. Four hands take it over threshold.
    ("tap", "^Player 4$"),
    ("sleep", 0.4),
    ("tap", "^Player 5$"),
    ("sleep", 0.4),
    ("tap", "^Player 6$"),
    ("sleep", 0.4),
    # The nominator may vote; their chip is the first in clock order and is on
    # screen, where Player 7's is a row lower behind the Vizier's trigger card.
    ("tap", "^Player 3$"),
    ("sleep", 1.5),
    ("screenshot", None),
    # The Vizier's trigger card takes the top half, so the outcome line and the
    # primary are one swipe down on THIS game (the ordinary nomination without
    # a trigger fits whole — see `C3_fix_day`).
    ("swipe", ["up", "450"]),
    ("sleep", 1.2),
    ("screenshot", None),
    ("find", "hold the tally to peek"),
    # …and no seat is NAMED. (The strip's own "Nobody is about to die." is
    # concealment, not a leak, so the pattern asks for a name.)
    ("absent", "Player \\d+ is about to die"),
    ("tap", "Lock in silently"),
    ("sleep", 2.5),
    ("find", "Someone is about to die"),     # the strip conceals the name

    # --- the execution sheet ---------------------------------------------
    ("swipe", ["up", "600"]),
    ("sleep", 1.5),
    ("tap", "^DUSK$"),
    ("sleep", 2.0),
    ("tap", "Execute Player 2"),
    ("sleep", 2.5),
    ("screenshot", None),
    ("audit", None),
    # C2-4: no enum constant anywhere on the sheet.
    ("absent", "DAY_IMMUNE"),
    # C2-5: one statement of the protection — the funnel's verdict line — and
    # the script to read out with it. `find` prints every match.
    ("find", "cannot die during the day"),
]
