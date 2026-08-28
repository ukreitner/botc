# A-5 (P1) FIXED: the bag header speaks the validator's own answer.
#
# Before: the header called `allowedDistributions(playerCount, selected)` with
# neither the Fabled ids nor the acknowledgements, while `validateBag` got both.
# With the Sentinel in play the validator accepted "3 or 4 or 5 outsiders" and
# the header still demanded "4 outsiders", with bars reading TF 6/5 and OUT 3/4
# for a bag the app then happily dealt.
#
# After: choosing the Sentinel widens the header and the bars together, and the
# label under each bar lists exactly the counts the validator will accept.
#
# Run with:
#   ./emu.sh launch emulator-5554 --fresh
#   ./scenario.py emulator-5554 A_fix_a5_sentinel_header

STEPS = [
    ("wait",       "New game"),
    ("tap",        "New game"),
    ("wait",       "SCRIPT"),
    ("tap",        "^Trouble Brewing$"),
    ("sleep",      1.0),
    ("tap",        "8 seats"),            # collapse TABLE
    ("sleep",      0.8),
    ("tap",        "^BAG$"),
    ("sleep",      1.2),
    # 8 players, no Fabled: 5/1/1/1, and the line says so in English.
    ("find",       "Need: 5 townsfolk · 1 outsider · 1 minion · 1 demon"),
    ("screenshot", None),

    ("tap",        "^BAG$"),              # collapse
    ("sleep",      0.8),
    ("tap",        "FABLED"),
    ("sleep",      1.0),
    ("swipe",      ["up", "700"]),
    ("swipe",      ["up", "700"]),
    ("sleep",      0.8),
    ("tap",        "^Sentinel$"),
    ("sleep",      1.0),
    # Card 4 grew a HOUSE RULES section (A-14 / G-2), so the two swipes above
    # now leave its header off the top of the screen — and a header scrolled
    # out of view is not in the semantics tree at all, so `tap "FABLED"` had
    # nothing to match. Scroll back to it before collapsing.
    ("swipe",      ["down", "900"]),
    ("swipe",      ["down", "900"]),
    ("sleep",      0.8),
    ("tap",        "FABLED"),             # collapse
    ("sleep",      0.8),
    ("tap",        "^BAG$"),
    ("sleep",      1.2),
    # The Sentinel is "1 extra or 1 fewer Outsider": the header now offers the
    # same three branches the validator does, and the Townsfolk count moves with
    # it because that is what pays for the Outsider.
    ("find",       "Need: 4 or 5 or 6 townsfolk · 0 or 1 or 2 outsiders"),
    ("audit",      None),
    ("screenshot", None),
]
