# Playtest B — night area.
# 8-player Trouble Brewing, info-heavy bag: Washerwoman, Librarian, Investigator,
# Chef, Empath / Butler / Poisoner / Imp.  Run from a FRESH app:
#     ./emu.sh launch emulator-5556 --fresh
#     ./scenario.py emulator-5556 B_night1_tb
# Leaves you on the "HAND OUT TOKENS" screen with the bag dealt.

# Ticking the bag by scrolling to each row is not reproducible: the "IN THE
# BAG" tray grows the moment the first token is added (and again whenever the
# token row wraps), so every fixed swipe below it lands somewhere else — and a
# row that has scrolled out is not in the semantics tree at all, so `tap` has
# nothing to match. The BAG card's own search field is deterministic: filter to
# one character, tap its ABILITY line, clear, repeat. Same idiom as
# `A_fix_a1_drunk_gate`, whose bag pick has never needed re-tuning.
#
# The ability text, not the name: the name also matches what was just typed
# into the search field, and `tap` prefers that clickable box.
def tick(name, ability):
    return [
        ("tap",   "Search characters"),
        ("sleep", 0.6),
        ("type",  name),
        ("sleep", 1.0),
        ("back",  None),                      # dismiss the keyboard
        ("sleep", 0.8),
        ("tap",   ability),
        ("sleep", 0.8),
        ("tap",   "Clear the search"),
        ("sleep", 1.0),
    ]


BAG = [
    ("Chef",         "how many pairs of evil players"),
    ("Empath",       "how many of your 2 alive neighbors"),
    ("Investigator", "particular Minion"),
    ("Librarian",    "particular Outsider"),
    ("Washerwoman",  "particular Townsfolk"),
    ("Butler",       "you may only vote if they are voting"),
    ("Poisoner",     "they are poisoned tonight and tomorrow"),
    ("Imp",          "If you kill yourself this way"),
]

STEPS = [
    ("wait",      "New game"),
    ("tap",       "New game"),
    ("wait",      "TABLE"),
    ("tap",       "Trouble Brewing"),          # script
    ("sleep",     0.8),
    # collapse TABLE, expand BAG
    ("tapxy",     ["984", "542"]),
    ("sleep",     0.8),
    ("tapxy",     ["984", "707"]),
    ("sleep",     0.8),
]
for _name, _ability in BAG:
    STEPS += tick(_name, _ability)
STEPS += [
    ("wait",      "IN THE BAG · 8 / 8"),
    ("audit",     None),
    ("tap",       "Deal & hand out"),
    ("wait",      "HAND OUT TOKENS"),
    ("audit",     None),
]
