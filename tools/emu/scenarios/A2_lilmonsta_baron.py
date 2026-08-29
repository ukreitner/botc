# A2-1 (P0): Lil' Monsta's bag shape SWALLOWS the Baron's "+2 Outsiders".
#
# 12 players on a script holding both. The right answer is
#
#     base 7/2/2/1  ->  Lil' Monsta (token, +1 Minion, no Demon)  ->  7/2/3/0
#                   ->  Baron (+2 Outsiders, paid by Townsfolk)   ->  5/4/3/0
#
# The app stops after the first step: `Setup.builtInBagShape("lilmonsta")` pins
# townsfolk = base.townsfolk and outsiders = base.outsiders, and `bagTargets`
# treats a pinned range as REPLACING the distribution, so the Baron's modifier
# is never applied. "Fill the rest" builds a 7/2/3/0 bag with a Baron in it,
# the validator says OK, and the deal seats 12 players with two Outsiders on a
# Baron script.
#
# Run with:
#   ./emu.sh launch emulator-5560 --fresh
#   ./scenario.py emulator-5560 A2_lilmonsta_baron

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
    ("tap",        "^SCRIPT$"),            # collapse card 1
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
    ("find",       "12 seats · 7/2/2/1"),  # the printed distribution
    ("tap",        "^TABLE$"),             # collapse
    ("sleep",      0.8),
    ("tap",        "^BAG$"),
    ("sleep",      1.2),

    # ---- Baron + Lil' Monsta by hand, then fill --------------------------
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
    ("tap",        "Fill the rest"),
    ("sleep",      1.5),
    # The bag card's header scrolls out while the character list is in view.
    ("swipe",      ["down", "1500"]),
    ("swipe",      ["down", "1500"]),
    ("sleep",      0.8),
    ("screenshot", None),

    # THE BUG: the header should read
    #   "Need: 5 or 6 or 7 townsfolk · 2 or 3 or 4 outsiders · 3 minions · 0 demons"
    # (or plainly 5 / 4 / 3 / 0). It reads the PRINTED 7 / 2 and calls the bag OK.
    ("find",       "Need: 7 townsfolk · 2 outsiders · 3 minions · 0 demons"),
    ("find",       "Deal & hand out tokens  \\(12 ready\\)"),
    ("audit",      None),
    ("screenshot", None),
]
