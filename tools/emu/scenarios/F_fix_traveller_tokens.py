# F_fix_traveller_tokens — playtest C-20: a Traveller sitting at the table
# contributes reminder tokens like everyone else.
#
#   ./emu.sh launch emulator-5556 --fresh
#   ./scenario.py emulator-5556 F_fix_traveller_tokens
#
# Before the fix the picker drew only from `gameData.resolve(state.script)`,
# which walks `script.characterIds` — and travellers are never in there. With a
# Voudon, Bureaucrat, Thief and Beggar seated the "In play" and "Rest of script"
# groups both ended at Scarlet Woman · Is The Demon, so `3 Votes` could only
# ever be placed by the Bureaucrat's own night step and a mid-day correction was
# impossible.
#
# Evidence in the screenshots:
#   step 25  the token picker on seat 9: a "Bureaucrat" row with its 3 Votes token
#   step 27  audit — safe area OK, no overlaps
#   step 30  the token is on the seat, and the picker has closed

STEPS = [
    ("wait", "New game"),
    ("tap", "New game"),
    ("wait", "TABLE"),
    ("tap", "Trouble Brewing"),
    ("sleep", 2.0),
    ("wait", "Add seat"),
    ("tap", "^TABLE$"),                       # collapse the seat editor again
    ("sleep", 1.0),
    ("tap", "Start empty"),
    ("wait", "Before the first night"),
    ("tap", "^Close$"),
    ("sleep", 1.0),

    # --- a Bureaucrat joins mid-game --------------------------------------
    ("tap", "^Menu$"),
    ("wait", "A traveller joins"),
    ("tap", "A traveller joins"),
    ("wait", "^Name$"),
    ("tap", "^Name$"),
    ("type", "Gus"),
    ("back", None),                           # hide the soft keyboard
    ("sleep", 0.8),
    ("swipe", ["up", "400"]),                 # the traveller chips are below the fold
    ("sleep", 1.0),
    ("tap", "^Bureaucrat$"),
    ("sleep", 1.0),
    ("tap", "Seat them"),
    # Seating a traveller raises a new blocking checklist row (their alignment).
    ("wait", "Gus's alignment"),
    ("tap", "^Close$"),
    ("sleep", 1.5),

    # --- C-20: their tokens are in the picker -----------------------------
    ("wait", "^Seat 9,"),
    ("tap", "^Seat 9,"),
    ("wait", "\\+ Token"),
    ("tap", "\\+ Token"),
    ("wait", "^Bureaucrat$"),
    ("find", "3 Votes"),
    ("audit", None),
    ("tap", "3 Votes"),
    ("sleep", 1.5),
    ("absent", "Search tokens"),               # the picker closed…
    ("find", "3 Votes"),                       # …and the token is on the seat
]
