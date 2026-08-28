# C_setup10 — 10-player Trouble Brewing game, characters assigned by hand.
# Run from a FRESH app:  ./emu.sh launch emulator-5558 --fresh
#
# KNOWN BLOCKED (wave-2 polish, verified 2026-08-29; NOT caused by the shell
# inset change). It strands on "no node matches '^Seat 3,'" after seat 2.
#
# The loop below dismisses the seat sheet with `back`. During SETUP the
# "Before the first night" checklist raises ITSELF over the seat sheet
# whenever an assignment introduces a requirement id the list did not have —
# assigning the Butler adds "Outsider: 1 in bag, expected 0" and up it comes,
# so that seat's `back` closes the CHECKLIST and the seat sheet stays open.
# It does not happen on every seat (seat 3's Mayor adds no new row, and
# nothing pops), so no fixed sequence of `back`s is right for all ten, and the
# harness has no conditional step.
#
# Two ways out, neither this wave's: give `ui.py` a tolerant
# "dismiss whatever sheet is on top, if any" step, or stop the checklist
# re-raising itself over an open sheet (the same class of complaint as D78's
# "one checklist opener"). `C_day_repro` covers the day flow this fixture was
# built for and passes.
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
