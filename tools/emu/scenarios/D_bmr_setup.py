# The user's 12-player Bad Moon Rising game, set up exactly as
# engine/src/test/kotlin/com/clocktower/engine/BmrSessionPlaytestTest.kt.
#
#   ./emu.sh launch emulator-5560 --fresh
#   ./scenario.py emulator-5560 D_bmr_setup
#
# Leaves the app on the grimoire with 12 named seats and the BMR script
# chosen. Characters are then assigned seat by seat with
# `scenarios/D_bmr_assign.sh` (a scenario cannot do the search-and-tap
# loop, because the picker row has to be located in a fresh dump each time).
STEPS = [
    ("wait", "New game"),
    ("tap",  "New game"),
    ("wait", "TABLE"),
    ("tap",  "Bad Moon Rising"),
    ("sleep", 1.0),
    ("swipe", ["up", "900"]),
    ("tap",  "Paste list"),
    ("sleep", 1.0),
    ("tapxy", ["540", "1214"]),
    ("type", "Ana,Ben,Cleo,Dev,Erin,Gita,Iris,Hal,Finn,Jonas,Kai,Lena"),
    ("sleep", 1.0),
    ("tap",  "Use these 12 seats"),
    ("sleep", 1.0),
    ("audit", None),
    ("tap",  "Start empty"),
    ("sleep", 1.5),
    ("audit", None),          # the setup checklist sheet
    ("tap",  "^Close$"),
    ("sleep", 1.0),
    ("audit", None),          # the 12-seat circle
]
