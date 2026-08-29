# A2-4 (P1): two imports with no `_meta` are both called "Imported script",
# and the second silently DESTROYS the first.
#
# Paste a bare id-array (the form the dialog's own placeholder advertises,
# `["washerwoman", …]`) and the script is filed under the fallback name
# "Imported script". Paste a different bare array and it overwrites the first
# by name: no warning, no "a script called X already exists", no rename, no
# undo. The script list ends with ONE "Imported script", holding the SECOND
# array's characters. The 22-character script imported first is gone.
#
# Run with:
#   ./emu.sh launch emulator-5560 --fresh
#   ./scenario.py emulator-5560 A2_import_overwrite

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

    # No collision warning of any kind: "Imported script" now holds the SECOND
    # array and the first is gone. And the confirmation banner is STALE —
    # `SetupScreen.kt:158` looks for a script whose id it has not seen, and an
    # overwrite reuses the id, so `added` is null, the notice is never rewritten
    # and it still claims 22 characters over a 12-character script (A2-5).
    ("find",       "Imported script · 12 characters"),
    ("find",       "^12 characters$"),
    ("absent",     "^22 characters$"),
    ("find",       "Imported \"Imported script\" — 22 characters"),
    ("audit",      None),
    ("screenshot", None),
]
