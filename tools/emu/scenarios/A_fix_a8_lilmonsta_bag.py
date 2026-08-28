# A-8 (P1) FIXED: the one-tap bag builders respect the acknowledgement they
# are shown beside — and A-5's second repro, the Lil' Monsta header.
#
# Before: ticking "Lil' Monsta is a token, not a seat…" switched the validator
# to "Minion: expected 2, Demon: expected 0" while the header still demanded
# "1 demon"; tapping Randomize then came back 5/1/1/1 — a Demon in a seat and a
# Minion short — rejected on the spot by the app's own checker.
#
# After: the header moves with the acknowledgement, Randomize rolls a bag the
# validator accepts, the centre token is put back in the tray, and the tray
# counts only the tokens that fill a seat.
#
# Run with:
#   ./emu.sh launch emulator-5554 --fresh
#   ./scenario.py emulator-5554 A_fix_a8_lilmonsta_bag

# Typed in chunks: `adb shell input text` drops characters somewhere past
# ~150 of them, and a mangled paste fails the import rather than the fix.
SCRIPT_CHUNKS = [
    "[{id:_meta,name:LM2},lilmonsta,washerwoman,librarian,",
    "investigator,chef,empath,fortuneteller,undertaker,monk,",
    "butler,recluse,poisoner,spy,scarletwoman]",
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
    ("sleep",      0.5),
    ("type",       SCRIPT_CHUNKS[1]),
    ("sleep",      0.5),
    ("type",       SCRIPT_CHUNKS[2]),
    ("sleep",      1.0),
    ("tap",        "^Import$"),
    ("sleep",      1.5),
    ("wait",       "Imported \"LM2\""),
    ("sleep",      0.8),
    # Importing selects the script without expanding TABLE, so card 3 is
    # already the next thing on screen.
    ("tap",        "^BAG$"),
    ("sleep",      1.2),

    # Eight players, nothing acknowledged: the ordinary distribution.
    ("find",       "Need: 5 townsfolk · 1 outsider · 1 minion · 1 demon"),
    ("screenshot", None),

    # Tick it, and the header moves with the validator (A-5's second repro:
    # the header used to keep demanding "1 demon").
    ("tap",        "Lil' Monsta is a token"),
    ("sleep",      1.2),
    ("find",       "Need: 5 townsfolk · 1 outsider · 2 minions · 0 demons"),
    ("screenshot", None),

    # And the builder aims at that bag rather than the one it used to.
    ("tap",        "^Randomize$"),
    ("sleep",      2.0),
    ("find",       "IN THE BAG · 8 / 8"),
    ("find",       "Deal & hand out tokens  \\(8 ready\\)"),
    ("find",       "^Lil' Monsta$"),      # the centre token, back in the tray
    ("audit",      None),
    ("screenshot", None),

    # The deal seats eight players and hands out no Demon token.
    ("tap",        "Deal & hand out"),
    ("wait",       "HAND OUT TOKENS"),
    ("find",       "0 / 8 — pass the phone"),
    ("screenshot", None),
]
