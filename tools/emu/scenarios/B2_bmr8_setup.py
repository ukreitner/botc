# Re-test B2 — an 8-seat Bad Moon Rising game for the roles playtest D's
# 12-player fixture does not carry: Innkeeper, Courtier, Zombuul.
#
#     ./emu.sh launch emulator-5554 --fresh
#     ./scenario.py emulator-5554 B2_bmr8_setup
#
# 8 players is BMR 5/1/1/1, which is exactly:
#
#   5 Townsfolk  Innkeeper · Courtier · Exorcist · Sailor · Professor
#   1 Outsider   Tinker
#   1 Minion     Godfather
#   1 Demon      Zombuul
#
# Same bag idiom as `B2_tb15_setup` / `B_night1_tb`: filter the BAG card's
# search field to one character, tap its ABILITY line (the NAME also matches
# what was just typed into the field), clear, repeat.
#
# Leaves you on HAND OUT TOKENS with the bag dealt.


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
    ("Innkeeper",  "they can't die tonight"),
    ("Courtier",   "they are drunk for 3 nights"),
    ("Exorcist",   "different to last night"),
    ("Sailor",     "either you or they are drunk until dusk"),
    ("Professor",  "choose a dead player"),
    ("Tinker",     "You might die at any time"),
    ("Godfather",  "which Outsiders are in play"),
    ("Zombuul",    "if no-one died today"),
]

STEPS = [
    ("wait",   "New game"),
    ("tap",    "New game"),
    ("wait",   "TABLE"),
    ("tap",    "^Bad Moon Rising$"),
    ("sleep",  1.2),
    # Choosing the script collapses SCRIPT and expands TABLE by itself; the
    # eight default seats are the whole table, so collapse it again and open
    # the bag.
    ("tap",    "8 seats"),
    ("sleep",  1.2),
    ("tap",    "^BAG$"),
    ("sleep",  1.2),
]
for _name, _ability in BAG:
    STEPS += tick(_name, _ability)
STEPS += [
    ("wait",   "IN THE BAG · 8 / 8"),
    ("audit",  None),
    ("tap",    "Deal & hand out"),
    ("wait",   "HAND OUT TOKENS"),
    ("audit",  None),
]
