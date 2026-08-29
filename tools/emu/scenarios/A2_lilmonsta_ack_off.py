# A2-2 (P0): the "Lil' Monsta is a token, not a seat" tick-box is INERT on the
# setup screen, and the game it deals is illegal by the app's own checklist.
#
# `Setup.shapesFor` reads the shapes off `bag.map { it.id } + inPlayIds`, so a
# Lil' Monsta ALREADY IN THE BAG raises its shape whether or not the box beside
# it is ticked. Untick it and nothing on the setup screen moves: "Need:" still
# says 3 minions / 0 demons, all four bars stay at target, card 3 still badges
# OK and the primary still reads "Deal & hand out tokens  (12 ready)".
#
# But the tick is the ONLY thing that writes
# `SetupRequirements.LILMONSTA_NO_DEMON_SEAT` into the game
# (`SetupScreen.kt:567-570`), and `Setup.seatlessInPlayIds(state)` reads exactly
# that decision. So the dealt game does NOT know Lil' Monsta is seatless, and
# the checklist that opens two taps later lists FOUR blocking bag problems for
# a bag the setup screen had just called legal:
#
#     Minion: 3 in bag, expected 2         Demon: 0 in bag, expected 1
#
# …plus, whenever "Fill the rest" happens to draw the Baron, "Townsfolk: 7 in
# bag, expected 5" and "Outsider: 2 in bag, expected 4" — the Baron the setup
# screen had ignored (A2-1).
#
# Run with:
#   ./emu.sh launch emulator-5560 --fresh
#   ./scenario.py emulator-5560 A2_lilmonsta_ack_off

SCRIPT_CHUNKS = [
    "[{id:_meta,name:A2Monsta,author:ReTesterA2},",
    "washerwoman,librarian,investigator,chef,empath,",
    "fortuneteller,undertaker,monk,ravenkeeper,virgin,",
    "slayer,soldier,mayor,butler,drunk,recluse,saint,",
    "lunatic,ogre,poisoner,spy,scarletwoman,baron,",
    "organgrinder,marionette,imp,lilmonsta]",
]

STEPS = [
    ("wait",       "New game"),
    ("tap",        "New game"),
    ("wait",       "SCRIPT"),
    ("tap",        "Import script \\(paste"),
    ("wait",       "Import script"),
    ("tapxy",      ["540", "1264"]),
    ("sleep",      0.6),
    ("type",       SCRIPT_CHUNKS[0]),
    ("sleep",      0.4),
    ("type",       SCRIPT_CHUNKS[1]),
    ("sleep",      0.4),
    ("type",       SCRIPT_CHUNKS[2]),
    ("sleep",      0.4),
    ("type",       SCRIPT_CHUNKS[3]),
    ("sleep",      0.4),
    ("type",       SCRIPT_CHUNKS[4]),
    ("sleep",      0.4),
    ("type",       SCRIPT_CHUNKS[5]),
    ("sleep",      0.8),
    ("tap",        "^Import$"),
    ("wait",       "Imported \"A2Monsta\""),
    ("sleep",      1.0),

    ("tap",        "^SCRIPT$"),
    ("sleep",      0.8),
    ("tap",        "^TABLE$"),
    ("sleep",      1.0),
    ("tap",        "Paste list"),
    ("wait",       "Paste the table"),
    ("tapxy",      ["540", "1214"]),
    ("sleep",      0.6),
    ("type",       "Uri,Dana,Ari,Sam,Mia,Jon,Lea,Tom,Ben,Ivy,Max,Zoe"),
    ("sleep",      1.0),
    ("tap",        "Use these 12 seats"),
    ("sleep",      1.5),
    ("swipe",      ["down", "1500"]),
    ("swipe",      ["down", "1500"]),
    ("sleep",      0.8),
    ("tap",        "^TABLE$"),
    ("sleep",      0.8),
    ("tap",        "^BAG$"),
    ("sleep",      1.2),

    # Lil' Monsta by hand, then fill: "Fill the rest" ticks the box for us,
    # which is the A-8 fix working.
    ("tap",        "Search characters and abilities"),
    ("sleep",      0.6),
    ("type",       "monsta"),
    ("sleep",      1.0),
    ("back",       None),
    ("sleep",      0.8),
    ("tap",        "Minions choose who babysits"),
    ("sleep",      1.0),
    ("tap",        "Clear the search"),
    ("sleep",      1.0),
    ("tap",        "Fill the rest"),
    ("sleep",      1.5),
    ("swipe",      ["down", "1500"]),
    ("swipe",      ["down", "1500"]),
    ("sleep",      0.8),
    ("screenshot", None),

    # ---- untick the acknowledgement -------------------------------------
    # The checkbox is at the head of its row; the whole row toggles it.
    ("tapxy",      ["128", "1136"]),
    ("sleep",      1.5),
    # NOTHING on the screen reacts. All three of these still hold:
    ("find",       "Need: 7 townsfolk · 2 outsiders · 3 minions · 0 demons"),
    ("find",       "IN THE BAG · 12 / 12"),
    ("find",       "Deal & hand out tokens  \\(12 ready\\)"),
    ("screenshot", None),

    # ---- and it deals an illegal game -----------------------------------
    ("tap",        "Deal & hand out"),
    ("wait",       "HAND OUT TOKENS"),
    ("sleep",      1.5),
    ("tap",        "Checklist"),
    ("wait",       "Before the first night"),
    ("sleep",      1.2),
    # "Fill the rest" is random, so the Townsfolk/Outsider rows depend on
    # whether it drew the Baron; these two are there on every roll.
    ("find",       "Minion: 3 in bag, expected 2"),
    ("find",       "Demon: 0 in bag, expected 1"),
    ("find",       "Lil' Monsta is in play"),
    ("audit",      None),
    ("screenshot", None),
]
