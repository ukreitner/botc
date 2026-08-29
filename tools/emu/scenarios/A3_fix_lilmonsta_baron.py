# A2-1 (P0) FIXED — Lil' Monsta's bag shape composes with the Baron.
#
# `A2_lilmonsta_baron` is the reproduction; this is it with the right answers.
# 12 players on a script holding both:
#
#     base 7/2/2/1  ->  Lil' Monsta (token, +1 Minion, no Demon)  ->  7/2/3/0
#                   ->  Baron (+2 Outsiders, paid by Townsfolk)   ->  5/4/3/0
#
# The header used to stop after the first step and the validator agreed with it,
# so a Baron bag holding the PRINTED two Outsiders passed and was dealt. The
# shape now states its Demon-for-Minion swap as a DELTA on the distributions the
# other brackets allow, so both apply.
#
# Run with:
#   ./emu.sh launch emulator-5560 --fresh
#   ./scenario.py emulator-5560 A3_fix_lilmonsta_baron

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

    # ---- 12 seats -------------------------------------------------------
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
    ("find",       "12 seats · 7/2/2/1"),   # the printed distribution
    ("tap",        "^TABLE$"),
    ("sleep",      0.8),
    ("tap",        "^BAG$"),
    ("sleep",      1.2),

    # ---- Baron + Lil' Monsta by hand ------------------------------------
    ("tap",        "Search characters and abilities"),
    ("sleep",      0.6),
    ("type",       "baron"),
    ("sleep",      1.0),
    ("back",       None),
    ("sleep",      0.8),
    ("tap",        "There are extra Outsiders in play"),
    ("sleep",      0.8),
    ("tap",        "Clear the search"),
    ("sleep",      0.8),
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
    ("swipe",      ["down", "1500"]),
    ("swipe",      ["down", "1500"]),
    ("sleep",      0.8),

    # BOTH brackets apply the moment both tokens are in the bag.
    ("find",       "Need: 5 townsfolk · 4 outsiders · 3 minions · 0 demons"),
    ("absent",     "Need: 7 townsfolk · 2 outsiders · 3 minions · 0 demons"),
    ("screenshot", None),

    # ---- and the one-tap builder aims at the same numbers ----------------
    ("tap",        "Fill the rest"),
    ("sleep",      1.5),
    ("swipe",      ["down", "1500"]),
    ("swipe",      ["down", "1500"]),
    ("sleep",      0.8),
    ("find",       "Need: 5 townsfolk · 4 outsiders · 3 minions · 0 demons"),
    ("find",       "IN THE BAG · 12 / 12"),
    ("find",       "Deal & hand out tokens  \\(12 ready\\)"),
    ("audit",      None),
    ("screenshot", None),

    # ---- the dealt game agrees with the screen ---------------------------
    ("tap",        "Deal & hand out"),
    ("wait",       "HAND OUT TOKENS"),
    ("sleep",      1.5),
    ("tap",        "Checklist"),
    ("wait",       "Before the first night"),
    ("sleep",      1.2),
    ("absent",     "in bag, expected"),          # no bag row at all
    ("find",       "Lil' Monsta is in play"),
    ("audit",      None),
    ("screenshot", None),
]
