# Re-test D2 — an EIGHT-seat Bad Moon Rising game for the characters the
# user's twelve-seat bag does not contain (Innkeeper, Courtier, Minstrel,
# Tinker, Assassin, Zombuul) and for the Gossip's "Was it true?" gate, which
# the first tester could not separate from the dead-Gossip gate.
#
#   ./emu.sh launch emulator-5558 --fresh
#   ./scenario.py emulator-5558 D2_bmr8_setup
#   zsh scenarios/D2_bmr8_assign.sh emulator-5558
#
# Leaves the app on an 8-seat BMR grimoire with the seats named. If the
# accessibility tree goes quiet (README, "After an install, the tree can come
# back empty"), `./emu.sh launch emulator-5558` — no --fresh — and re-run.
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
    ("type", "Amy,Bo,Cy,Di,Eli,Fay,Gus,Hana"),
    ("sleep", 1.0),
    ("tap",  "Use these 8 seats"),
    ("sleep", 1.0),
    ("audit", None),
    ("tap",  "Start empty"),
    ("sleep", 2.5),
    ("audit", None),
    ("tapxy", ["540", "300"]),   # scrim of the checklist, or the inert header
    ("sleep", 1.5),
    ("wait", ["^Seat 1,", "45"]),
    ("audit", None),             # the 8-seat circle
]
