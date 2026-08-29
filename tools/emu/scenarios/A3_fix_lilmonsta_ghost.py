# A2-3 (P0) FIXED — take Lil' Monsta out of the bag and it is out of the game.
#
# `A2_lilmonsta_ghost` is the reproduction: the acknowledgement was bound to the
# SCRIPT, so it survived the token leaving the bag. The app then called a
# Demon-less bag legal ("Need: 5 townsfolk · 1 outsider · 2 minions · 0 demons",
# "8 ready"), dealt eight seats with no Demon on any of them, reported
# "✓ Lil' Monsta is in play — Confirmed" and scheduled its first-night step.
#
# The decision comes off the bag now, so removing the token removes the seatless
# Demon, the confirmation and the night step — and the bag needs a Demon like
# any other.
#
# Run with:
#   ./emu.sh launch emulator-5560 --fresh
#   ./scenario.py emulator-5560 A3_fix_lilmonsta_ghost

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

    # 8 seats is the default.
    ("tap",        "^SCRIPT$"),
    ("sleep",      0.8),
    ("tap",        "^BAG$"),
    ("sleep",      1.2),
    ("find",       "Need: 5 townsfolk · 1 outsider · 1 minion · 1 demon"),

    # ---- Lil' Monsta in, "Fill the rest" ---------------------------------
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
    ("find",       "Deal & hand out tokens  \\(8 ready\\)"),
    ("screenshot", None),

    # ---- …and now take Lil' Monsta back OUT ------------------------------
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
    ("swipe",      ["down", "1500"]),
    ("swipe",      ["down", "1500"]),
    ("sleep",      0.8),

    # THE FIX: no token, no seatless Demon. The bag is short a Demon and the
    # primary cannot be pressed.
    ("find",       "Demon: 0 in bag, expected 1"),
    ("absent",     "Deal & hand out tokens  \\(8 ready\\)"),
    ("audit",      None),
    ("screenshot", None),

    # ---- and even FORCED through, no ghost Demon appears ----------------
    # "Deal anyway" is D54's override: the storyteller may deal a bag the
    # checker rejects. What must never happen is the app inventing the seatless
    # Demon for them — this is the exact table A2-3 dealt, and the checklist now
    # reports the missing Demon and leaves the Lil' Monsta row UNANSWERED
    # instead of "✓ Confirmed", with no first-night step for it.
    ("find",       "Deal anyway"),
    ("tap",        "Deal anyway"),
    ("wait",       "HAND OUT TOKENS"),
    ("sleep",      1.5),
    ("tap",        "Checklist"),
    ("wait",       "Before the first night"),
    ("sleep",      1.2),
    ("find",       "Demon: 0 in bag, expected 1"),
    ("find",       "Lil' Monsta is in play"),
    ("absent",     "Confirmed"),
    ("audit",      None),
    ("screenshot", None),

    ("tap",        "^Close$"),
    ("sleep",      1.5),
    ("tap",        "Finish later"),
    ("sleep",      2.5),
    # Leaving hand-out mode enters the game shell, whose own checklist has not
    # been dismissed yet — its FIRST raise, with nothing open, is correct.
    ("wait",       "Before the first night"),
    ("find",       "Lil' Monsta is in play"),
    ("absent",     "Confirmed"),
    ("tap",        "^Close$"),
    ("sleep",      1.5),
    ("tap",        "^Night$"),
    ("sleep",      2.5),
    ("absent",     "Lil' Monsta"),
    ("audit",      None),
    ("screenshot", None),
]
