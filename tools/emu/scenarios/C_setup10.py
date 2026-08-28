# C_setup10 — 10-player Trouble Brewing game, characters assigned by hand.
# Run from a FRESH app:  ./emu.sh launch emulator-5558 --fresh
#
# Seats: 1 Virgin, 2 Butler, 3 Mayor, 4 Saint, 5 Poisoner, 6 Imp,
#        7 Washerwoman, 8 Librarian, 9 Chef, 10 Empath

ASSIGN = [
    (1, "Virgin", "1st time you are nominated"),
    (2, "Butler", "you may only vote if they are voting"),
    (3, "Mayor", "If only 3 players live"),
    (4, "Saint", "If you die by execution, your team loses"),
    (5, "Poisoner", "they are poisoned tonight and tomorrow"),
    (6, "Imp", "If you kill yourself"),
    (7, "Washerwoman", "particular Townsfolk"),
    (8, "Librarian", "particular Outsider"),
    (9, "Chef", "how many pairs of evil players"),
    (10, "Empath", "how many of your 2 alive neighbors"),
]

STEPS = [
    ("wait", "New game"),
    ("tap", "New game"),
    ("wait", "TABLE"),
    ("tap", "Trouble Brewing"),
    ("sleep", 2.0),
    ("wait", "Add seat"),             # TABLE auto-expands once a script is picked
    ("swipe", ["up", "900"]),
    ("tap", "Add seat"),
    ("tap", "Add seat"),
    ("swipe", ["down", "1200"]),
    ("swipe", ["down", "1200"]),
    ("wait", "10 seats"),
    ("tap", "^TABLE$"),               # collapse TABLE
    ("sleep", 1.0),
    ("tap", "Start empty"),
    ("wait", "Before the first night"),
    ("tap", "^Close$"),
    ("sleep", 1.0),
]

for seat, name, desc in ASSIGN:
    STEPS += [
        ("tap", "^Seat %d," % seat),
        ("wait", "Change"),
        ("tap", "Change…"),
        ("wait", "Search characters"),
        ("tap", "Search characters"),
        ("type", name),
        ("sleep", 0.9),
        ("back", None),               # hide the soft keyboard first
        ("sleep", 0.6),
        ("tap", desc),
        ("sleep", 1.2),
        ("back", None),               # close the seat sheet
        ("sleep", 1.0),
    ]

STEPS += [
    ("screenshot", None),
]
