# C3_fix_day — the day-screen half of fix wave 3 C, on one Trouble Brewing game.
#
#   ./emu.sh launch emulator-5556 --fresh
#   ./scenario.py emulator-5556 C3_fix_day
#
# Trouble Brewing, 8 seats, nothing assigned — every finding here is about the
# screen, not about a character.
#
# What it proves, in order:
#   C2-8   a recorded statement OPENS: the row is a tap target with a ✎, and
#          the dialog carries the words, a verdict where one is wanted and a
#          two-step delete. The row used to have no click flag at all.
#   C2-9   after the two ring taps the ring collapses to the pair and the whole
#          countable half of the card is on screen — tally, every chip in full,
#          Lock in — with no swipe. C2 measured the first chip row at 13 visible
#          pixels of 126, with Lock in not rendered at all.
#   C2-11  [Seat them] is tapped with the SOFT KEYBOARD STILL UP and the
#          traveller is actually seated. As an AlertDialog the button sat under
#          the IME at y 1522..1648 while uiautomator still called it tappable,
#          so the tap landed on the keyboard and nothing happened.
#   C2-7   the checklist that raises for the new traveller's alignment is
#          titled for the phase — "Setup still owed" on day 1, not "Before the
#          first night", and its footer does not mention the begin-night guard.
#   C2-3   an exile the table voted for and nobody carried out is named on the
#          stat strip, on the DUSK card and on the dusk sheet, with the button
#          right there. All three used to say nothing at all.

STEPS = [
    ("wait", "New game"),
    ("tap", "New game"),
    ("wait", "SCRIPT"),
    ("tap", "Trouble Brewing"),
    ("sleep", 2.0),
    ("wait", "Start empty"),
    ("tap", "Start empty"),
    ("wait", "Before the first night"),
    ("tap", "^Close$"),
    ("sleep", 1.2),

    # --- straight through night 1 ----------------------------------------
    ("tap", "Begin night"),
    ("wait", "Start the night anyway"),
    ("tap", "Start the night anyway"),
    ("sleep", 2.5),
    ("tap", "^Dawn$"),
    ("wait", "Dawn anyway"),
    ("tap", "Dawn anyway"),
    ("wait", "OPEN DAY 1"),
    ("tap", "OPEN DAY 1"),
    ("sleep", 2.0),

    # --- C2-8: a statement, then the row that opens it --------------------
    ("tap", "\\+ Say"),
    ("wait", "Who said it"),
    ("tap", "^Player 3$"),
    ("sleep", 0.8),
    ("type", "Player 6 is the Imp"),
    ("sleep", 0.8),
    ("tap", "^Add$"),
    ("sleep", 2.0),
    # The composer leaves WHAT WAS SAID expanded; tapping it would close it.
    ("screenshot", None),
    ("find", "Player 6 is the Imp"),
    # The ✎ is the affordance; the whole row is the target.
    ("find", "✎"),
    ("tap", "Player 6 is the Imp"),
    ("sleep", 1.5),
    ("screenshot", None),
    ("find", "In their words"),
    ("find", "Delete"),
    ("tap", "^Cancel$"),
    ("sleep", 1.2),

    # --- C2-9: the vote panel after two ring taps -------------------------
    ("tap", "^Nominate$"),
    ("sleep", 1.5),
    # By NAME: no vote chips exist until both halves are picked, so
    # "Player N" is unambiguously the ring seat for both taps.
    ("tap", "^Player 3$"),
    ("sleep", 1.2),
    ("tap", "^Player 1$"),
    ("sleep", 2.5),
    ("screenshot", None),
    ("find", "^Change$"),                    # the ring collapsed to the pair
    ("find", "Lock in"),                     # …and the primary is on screen
    ("find", "^Player 8$"),                  # …with the last chip in full
    ("audit", None),
    ("tap", "^Cancel$"),
    ("sleep", 1.5),

    # --- C2-11 + C2-7: a traveller joins, with the keyboard up ------------
    ("tap", "^Menu$"),
    ("wait", "A traveller joins"),
    ("tap", "A traveller joins"),
    ("wait", "^Name$"),
    ("tap", "^Name$"),
    ("type", "Begged"),
    ("sleep", 1.0),
    ("screenshot", None),
    # NO `back` here: the keyboard stays up, which is the whole point.
    ("tap", "Seat them"),
    ("sleep", 2.5),
    ("screenshot", None),
    # It actually happened, and the checklist that raises is titled for DAY 1.
    ("wait", "Begged's alignment"),
    ("find", "Setup still owed"),
    ("absent", "Before the first night$"),
    ("absent", "Begin night\" still works"),
    ("audit", None),
    ("tap", "^Close$"),
    ("sleep", 1.5),

    # --- C2-3: an exile the table votes for and nobody carries out --------
    ("tap", "^Day$"),
    ("sleep", 1.5),
    ("tap", "^Nominate$"),
    ("sleep", 1.5),
    ("tap", "^Player 3$"),
    ("sleep", 1.2),
    ("tap", "^Begged$"),
    ("sleep", 2.0),
    ("screenshot", None),
    ("find", "this is an exile call"),
    # Five of nine is the exile threshold. The chips are the vote panel's now.
    ("tap", "^Player 1$"),
    ("sleep", 0.5),
    ("tap", "^Player 2$"),
    ("sleep", 0.5),
    ("tap", "^Player 4$"),
    ("sleep", 0.5),
    ("tap", "^Player 5$"),
    ("sleep", 0.5),
    ("tap", "^Player 6$"),
    ("sleep", 0.8),
    ("screenshot", None),
    # Nine seats plus the exile caution make this card taller than the eight-
    # seat one asserted above; one swipe brings the primary fully in.
    ("swipe", ["up", "300"]),
    ("sleep", 1.0),
    ("tap", "Lock in"),
    ("sleep", 2.5),
    ("screenshot", None),
    # The strip says so — where C2 read "No one is about to die."
    ("find", "Begged was exiled and has not left the game"),
    # …and so does the DUSK card, with the button right there.
    ("tap", "^DUSK$"),
    ("sleep", 1.5),
    ("screenshot", None),
    ("find", "Exile Begged"),
    ("audit", None),
    # …and so does the dusk sheet, in BEFORE YOU MOVE ON, with the exile named
    # on the primary.
    ("tap", "Everyone, eyes closed"),
    ("sleep", 2.0),
    ("screenshot", None),
    ("find", "Begged was exiled and has not left the game"),
    ("find", "EXILE BEGGED"),
    ("audit", None),
]
