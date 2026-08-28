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
    ("sleep", 2.5),
    ("audit", None),          # the setup checklist sheet, when it raises
    # Two things made `tap "^Close$"` here unreliable, neither of them this
    # scenario's business: the checklist does not ALWAYS raise itself on this
    # path (measured: 4 runs in 6), and on the runs where it did, a fixed sleep
    # sometimes raced the first composition of a TWELVE-seat grimoire — the
    # dump came back with the system windows and nothing else.
    #
    # (540,300) needs neither to be true. On the checklist it is the sheet's
    # scrim, which dismisses it; on the bare grimoire it is the header's own
    # "Setup · 12 alive · 6 to execute" line, which is not clickable at all.
    ("tapxy", ["540", "300"]),
    ("sleep", 1.5),
    # 45 s, not the default 15: on a twelve-seat circle the app's accessibility
    # tree occasionally goes quiet for half a minute after a `--fresh` launch
    # (README, "After an install, the tree can come back empty") — the screen
    # is drawn, `uiautomator dump` just reports the system windows and nothing
    # else. Polling rides it out; a 15 s wait did not.
    ("wait", ["^Seat 1,", "45"]),
    ("audit", None),          # the 12-seat circle
]
