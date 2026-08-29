# A2-2 (P0) FIXED — the "Lil' Monsta is a token, not a seat" box drives the bag.
#
# `A2_lilmonsta_ack_off` is the reproduction: with the token already in the bag,
# unticking the box changed NOTHING on screen (same "Need:", same bars, same OK
# badge, same "Deal & hand out tokens (12 ready)") — yet the box was the only
# thing that wrote the decision into the dealt game, so the checklist opened two
# taps later on four blocking bag rows.
#
# The tick and the token are one bit now, and the bag holds it: the box puts the
# centre token into the bag and takes it out again, so everything on the screen
# moves on the same frame.
#
# Run with:
#   ./emu.sh launch emulator-5560 --fresh
#   ./scenario.py emulator-5560 A3_fix_lilmonsta_ack

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

    # Lil' Monsta in by hand, then "Fill the rest" — the box is ticked because
    # the token is in the bag (A-8's one-tap builders still honour it).
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
    ("find",       "Deal & hand out tokens  \\(12 ready\\)"),
    ("screenshot", None),

    # ---- untick the acknowledgement -------------------------------------
    # By coordinate: the note text appears twice (the box's own label and the
    # advisory line under the bag), and only the first is the control.
    ("tapxy",      ["128", "1135"]),
    ("sleep",      1.5),
    ("swipe",      ["down", "1500"]),
    ("swipe",      ["down", "1500"]),
    ("sleep",      0.8),

    # THE FIX: the whole screen moves. The token is out of the bag, so the
    # printed distribution is back and the bag is short a Demon and over on
    # Minions — and the primary can no longer be pressed.
    ("find",       "Minion: 3 in bag, expected 2"),
    ("find",       "Demon: 0 in bag, expected 1"),
    ("absent",     "Deal & hand out tokens  \\(12 ready\\)"),
    ("find",       "Deal anyway"),
    ("audit",      None),
    ("screenshot", None),

    # ---- tick it again and the game comes back --------------------------
    ("tapxy",      ["128", "1135"]),
    ("sleep",      1.5),
    ("swipe",      ["down", "1500"]),
    ("swipe",      ["down", "1500"]),
    ("sleep",      0.8),
    ("absent",     "Demon: 0 in bag, expected 1"),
    ("find",       "Deal & hand out tokens  \\(12 ready\\)"),
    ("audit",      None),
    ("screenshot", None),

    # ---- and what the screen says is what the dealt game believes --------
    ("tap",        "Deal & hand out"),
    ("wait",       "HAND OUT TOKENS"),
    ("sleep",      1.5),
    ("tap",        "Checklist"),
    ("wait",       "Before the first night"),
    ("sleep",      1.2),
    ("absent",     "in bag, expected"),
    ("find",       "Lil' Monsta is in play"),
    ("audit",      None),
    ("screenshot", None),
]
