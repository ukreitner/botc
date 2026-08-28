# Fix wave 2, agent E — E-1: a mid-game character change raises no checklist.
#
#   ./emu.sh launch emulator-5554 --fresh
#   ./scenario.py emulator-5554 E_fix_starpass
#
# Self-contained: randomises an 8-player Trouble Brewing bag, deals it, skips
# night 1 through the dawn guard, and drives night 2 to the Imp's star pass.
# Only the Demon matters, so the random Minion is matched by team, not by name.
#
# What it proves:
#   * 38  the Imp kills ITSELF and a Minion becomes the Imp — a mid-game
#         character change, which leaves the grimoire holding two Imps and one
#         fewer Minion. That is exactly what the rules ask for and exactly what
#         the bag distribution table rejects;
#   * 43  no "Before the first night" checklist is raised over it: the night
#         card is still the thing on screen and still tappable, and `audit`
#         lists the night sheet's controls rather than a modal sheet's.
#         `SetupRequirements` used to keep validating bag legality once the game
#         was running, so the whole setup sheet landed on top of whatever the
#         storyteller was doing — `B_fix_starpass.py` had to `tap "^Close$"`
#         right here to get past it, and playtest C-9 hit the same sheet on
#         day 4 of a running game.
#         (The negative — `./ui.py <serial> find "Before the first night"`
#         printing "no match" — cannot be a scenario step, since a scenario
#         stops on the first failing one; run it by hand here.)
STEPS = [
    ("wait",   "New game"),
    ("tap",    "New game"),
    ("wait",   "TABLE"),
    ("tap",    "^Trouble Brewing$"),
    ("sleep",  0.8),
    ("tap",    "^TABLE$"),               # collapse the seat list
    ("tap",    "^BAG$"),                 # expand the bag
    ("sleep",  0.8),
    ("tap",    "Randomize"),
    ("wait",   "IN THE BAG · 8 / 8"),

    ("tap",    "Deal & hand out"),
    ("wait",   "HAND OUT TOKENS"),
    ("tap",    "Finish later"),
    ("sleep",  1.5),
    ("tap",    "^Close$"),
    ("sleep",  1.2),

    # ---- straight past night 1: the dawn guard is the way through ---------
    ("tap",    "Begin night"),
    ("sleep",  1.5),
    ("tap",    "Start the night anyway"),
    ("wait",   "step 1 / "),          # the random bag decides how many rows
    ("tap",    "^Dawn$"),
    ("sleep",  1.5),
    ("find",   "Night checklist incomplete"),
    ("tap",    "Dawn anyway"),
    ("sleep",  2.5),
    ("tap",    "OPEN DAY 1"),
    ("sleep",  2.5),
    # The top bar's Dusk button is gone (F-3 / D77) — that coordinate is now
    # the Dawn/phase button — so the day closes from the Day tab's DUSK stage
    # card, the one path there is (F_fix_dusk drives it the same way).
    ("tap",    "^Day$"),
    ("sleep",  1.5),
    ("swipe",  ["up", "800"]),
    ("swipe",  ["up", "800"]),
    ("swipe",  ["up", "800"]),
    ("swipe",  ["up", "800"]),
    ("swipe",  ["up", "800"]),
    ("swipe",  ["up", "800"]),
    ("sleep",  0.8),
    ("tap",    "^DUSK$"),
    ("sleep",  1.2),
    ("tap",    "Everyone, eyes closed"),
    ("sleep",  2.0),
    ("tap",    "BEGIN NIGHT 2"),
    ("sleep",  2.5),

    # ---- night 2: straight to the Imp -------------------------------------
    ("tap",    "whole sheet"),
    ("sleep",  1.5),
    ("tap",    "^Imp — "),
    ("sleep",  1.5),
    ("tap",    "hide sheet"),
    ("sleep",  1.2),
    ("find",   "◆ themselves"),
    ("tap",    "◆ themselves"),
    ("sleep",  1.2),
    ("hold",   ["DIES", "1400"]),
    ("sleep",  2.5),

    # ---- E-1: the mid-game character change, and no checklist over it -----
    ("find",   "a Minion becomes the Imp"),
    ("tap",    "· (Poisoner|Spy|Scarlet Woman|Baron)"),
    ("sleep",  1.0),
    ("hold",   ["BECOMES THE IMP", "1400"]),
    ("sleep",  3.0),
    ("find",   "their new character \\(Imp\\)"),
    ("audit",  None),
    ("screenshot", None),
]
