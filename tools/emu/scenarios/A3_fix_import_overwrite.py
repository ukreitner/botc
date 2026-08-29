# A2-4 / A2-5 (P1 / P2) FIXED — two unnamed imports are two scripts.
#
# `A2_import_overwrite` is the reproduction: both bare id-arrays are called
# "Imported script", both were filed under `imported-importedscript`, and the
# second silently destroyed the first — no warning, no rename, no undo. The
# success banner was stale on top of it, still claiming the FIRST import's
# 22 characters over a 12-character script.
#
# Ids are minted from the name PLUS a fingerprint of the contents now, so both
# survive; the colliding NAME is suffixed rather than shadowing the first row;
# and the banner names whatever was actually just imported.
#
# Run with:
#   ./emu.sh launch emulator-5560 --fresh
#   ./scenario.py emulator-5560 A3_fix_import_overwrite

FIRST = [
    "[washerwoman,librarian,investigator,chef,empath,",
    "fortuneteller,undertaker,monk,ravenkeeper,virgin,",
    "slayer,soldier,mayor,butler,drunk,recluse,saint,",
    "poisoner,spy,scarletwoman,baron,imp]",
]
SECOND = "[chef,empath,fortuneteller,monk,slayer,soldier,mayor,butler,drunk,poisoner,baron,imp]"

STEPS = [
    ("wait",       "New game"),
    ("tap",        "New game"),
    ("wait",       "SCRIPT"),

    # --- first import: 22 characters --------------------------------------
    ("tap",        "Import script \\(paste"),
    ("wait",       "Import script"),
    ("tapxy",      ["540", "1264"]),
    ("sleep",      0.6),
    ("type",       FIRST[0]),
    ("sleep",      0.4),
    ("type",       FIRST[1]),
    ("sleep",      0.4),
    ("type",       FIRST[2]),
    ("sleep",      0.4),
    ("type",       FIRST[3]),
    ("sleep",      0.8),
    ("tap",        "^Import$"),
    ("sleep",      1.5),
    ("find",       "Imported \"Imported script\" — 22 characters"),
    ("find",       "^22 characters$"),
    ("screenshot", None),

    # --- second import: 12 characters, same auto-name ---------------------
    ("tap",        "Import script \\(paste"),
    ("wait",       "Import script"),
    ("tapxy",      ["540", "1264"]),
    ("sleep",      0.6),
    ("type",       SECOND),
    ("sleep",      0.8),
    ("tap",        "^Import$"),
    ("sleep",      1.5),

    # THE FIX. Both scripts are in the list: the first keeps its 22 characters,
    # the second takes a suffixed name rather than replacing it…
    ("find",       "^22 characters$"),
    ("find",       "^12 characters$"),
    ("find",       "Imported script \\(2\\)"),
    # …and the banner names what was just imported, not the previous one.
    ("find",       "Imported \"Imported script \\(2\\)\" — 12 characters"),
    ("absent",     "Imported \"Imported script\" — 22 characters"),
    ("audit",      None),
    ("screenshot", None),

    # --- re-importing the SAME script replaces itself, never piles up ------
    ("tap",        "Import script \\(paste"),
    ("wait",       "Import script"),
    ("tapxy",      ["540", "1264"]),
    ("sleep",      0.6),
    ("type",       SECOND),
    ("sleep",      0.8),
    ("tap",        "^Import$"),
    ("sleep",      1.5),
    ("absent",     "Imported script \\(3\\)"),
    ("find",       "^22 characters$"),
    ("find",       "^12 characters$"),
    ("audit",      None),
    ("screenshot", None),
]
