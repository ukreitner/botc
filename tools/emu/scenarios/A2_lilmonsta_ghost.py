# A2-3 (P0): take Lil' Monsta OUT of the bag and it stays in the game.
#
# `SetupScreen.kt:196-208` builds
#
#     seatlessCandidates = characters (the whole SCRIPT) with a forbidInBag shape
#     seatlessIds        = if (seatlessAck) seatlessCandidates.map { id } else []
#
# — the tick-box is bound to the SCRIPT, never to the bag. "Fill the rest" ticks
# it for you the moment a roll includes Lil' Monsta (the A-8 fix). Untick the
# Lil' Monsta ROW afterwards and the token leaves the bag while the tick stays,
# so the screen goes on demanding "2 Minions and no Demon", "Fill the rest"
# builds exactly that, and the deal writes `lilmonsta.noDemonSeat` into the game.
#
# The result on an 8-seat table: Scarlet Woman + Poisoner, five Townsfolk, one
# Outsider and NO Demon — no Demon seat, no Lil' Monsta chip in the tray — with
# card 3 badged OK, the primary reading "(8 ready)", the checklist reporting
# "Lil' Monsta is in play — Confirmed", and the first night still running a
# "Lil' Monsta · 1 pick" step for a token nobody was dealt.
#
# Run with:
#   ./emu.sh launch emulator-5560 --fresh
#   ./scenario.py emulator-5560 A2_lilmonsta_ghost

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

    # 8 seats is the default; collapse SCRIPT (TABLE is already collapsed
    # after an import) and open BAG.
    ("tap",        "^SCRIPT$"),
    ("sleep",      0.8),
    ("tap",        "^BAG$"),
    ("sleep",      1.2),
    ("find",       "Need: 5 townsfolk · 1 outsider · 1 minion · 1 demon"),

    # ---- Lil' Monsta in, "Fill the rest" ticks the acknowledgement -------
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

    # ---- …and now take Lil' Monsta back OUT -----------------------------
    ("tap",        "Search characters and abilities"),
    ("sleep",      0.6),
    ("type",       "monsta"),
    ("sleep",      1.0),
    ("back",       None),
    ("sleep",      0.8),
    ("tap",        "Minions choose who babysits"),   # untick the row
    ("sleep",      1.0),
    ("tap",        "Clear the search"),
    ("sleep",      1.0),
    ("tap",        "Fill the rest"),
    ("sleep",      1.5),
    ("swipe",      ["down", "1500"]),
    ("swipe",      ["down", "1500"]),
    ("sleep",      0.8),

    # THE BUG. The bag holds no Lil' Monsta any more, yet:
    ("find",       "Need: 5 townsfolk · 1 outsider · 2 minions · 0 demons"),
    ("find",       "Lil' Monsta is a token, not a seat"),
    ("find",       "Deal & hand out tokens  \\(8 ready\\)"),
    ("audit",      None),
    ("screenshot", None),

    # ---- deal: a table with no Demon anywhere ---------------------------
    ("tap",        "Deal & hand out"),
    ("wait",       "HAND OUT TOKENS"),
    ("sleep",      1.5),
    ("tap",        "Finish later"),
    ("wait",       "Before the first night"),
    ("sleep",      1.2),
    ("find",       "Lil' Monsta is in play"),
    ("find",       "Confirmed"),
    ("screenshot", None),
    ("tap",        "^Close$"),
    ("sleep",      1.5),
    # No seat holds a Demon — not the Imp, not Lil' Monsta.
    ("absent",     "Seat [0-9]+, [^,]+, (Imp|Lil' Monsta)"),
    ("screenshot", None),

    # …and the first night runs a Lil' Monsta step all the same.
    ("tap",        "^Night$"),
    ("sleep",      2.0),
    ("find",       "Lil' Monsta"),
    ("screenshot", None),
]
