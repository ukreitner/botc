# Fix wave 3, agent B2 — B2-1 and B2-6, the star pass.
#
#   ./emu.sh launch emulator-5554 --fresh
#   ./scenario.py emulator-5554 B3_fix_starpass
#
# Self-contained (the same shape as `E_fix_starpass`, whose walk to the star
# pass this borrows): ticks `B_fix_night1`'s 8-player Trouble Brewing bag, deals
# it, skips night 1 through the dawn guard, closes day 1 from the Day tab's DUSK
# card, and drives night 2 to an Imp that kills itself.
#
# What it proves:
#
#   B2-1  the heir does NOT get a live kill row on the night of the pass. The
#         plan used to rebuild the heir's own `Imp — Player N` row from the base
#         list with a full picker, so holding its primary killed a SECOND player
#         on a night the Demon had already spent its attack — against the app's
#         own hand-over card, which says "The new Demon does not act tonight."
#         The row is now a marker: `⊘ skipped · they became the Demon tonight —
#         the new Demon does not act tonight`, with `[Run anyway]` for a
#         storyteller who overrules, and the header still reads 7/8 alive.
#
#   B2-6  while the hand-over card is open the sheet SAYS it is pinned
#         ("Finish this first — the rest of tonight's sheet is on hold…") and
#         the collapsed rows stop offering a tap that does nothing. Before this
#         every row was drawn normally, dumped as clickable, and silently did
#         nothing at all.


# The bag is ticked by hand rather than randomised: a random one deals a Scarlet
# Woman about half the time, and she catches the token by a DIFFERENT route
# (the promotion prompt, not the star-pass picker). Both end in the same marker
# row, but only a fixed bag makes the walk to it reproducible. Idiom borrowed
# from `B_fix_night1`: filter the BAG card's search field to one character, tap
# its ABILITY line, clear, repeat.
def tick(name, ability):
    return [
        ("tap",   "Search characters"),
        ("sleep", 0.6),
        ("type",  name),
        ("sleep", 1.0),
        ("back",  None),                  # dismiss the keyboard
        ("sleep", 0.8),
        ("tap",   ability),
        ("sleep", 0.8),
        ("tap",   "Clear the search"),
        ("sleep", 1.0),
    ]


BAG = [
    ("Chef",         "how many pairs of evil players"),
    ("Investigator", "particular Minion"),
    ("Librarian",    "particular Outsider"),
    ("Washerwoman",  "particular Townsfolk"),
    ("Butler",       "you may only vote if they are voting"),
    ("Poisoner",     "they are poisoned tonight and tomorrow"),
    ("Imp",          "If you kill yourself this way"),
    ("Empath",       "how many of your 2 alive neighbors"),
]

STEPS = [
    ("wait",   "New game"),
    ("tap",    "New game"),
    ("wait",   "TABLE"),
    ("tap",    "^Trouble Brewing$"),
    ("sleep",  0.8),
    ("tap",    "^TABLE$"),               # collapse the seat list
    ("tap",    "^BAG$"),                 # expand the bag
    ("sleep",  0.8),
]
for _name, _ability in BAG:
    STEPS += tick(_name, _ability)
STEPS += [
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
    ("wait",   "step 1 / "),
    ("tap",    "^Dawn$"),
    ("sleep",  1.5),
    ("tap",    "Dawn anyway"),
    ("sleep",  2.5),
    ("tap",    "OPEN DAY 1"),
    ("sleep",  2.5),
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

    # ---- night 2: the Imp kills itself ------------------------------------
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

    # ---- B2-6: the sheet says it is on hold, and means it -----------------
    ("find",   "a Minion becomes the Imp"),
    ("find",   "the rest of tonight's sheet is on hold"),
    ("absent", "\\[Run anyway\\]"),      # no control that would do nothing
    ("screenshot", None),

    # ---- the hand-over ----------------------------------------------------
    ("tap",    "· Poisoner"),
    ("sleep",  1.0),
    ("hold",   ["BECOMES THE IMP", "1400"]),
    ("sleep",  3.0),
    ("find",   "their new character \\(Imp\\)"),
    ("tap",    "DONE — THEY HAVE SEEN IT"),
    ("sleep",  2.0),

    # ---- B2-1: the heir's row is a marker, never a kill --------------------
    # The sheet has moved on to the next owed row, so open the whole list: the
    # heir's row can be several above the one that is now current.
    ("tap",    "whole sheet"),
    ("sleep",  1.5),
    ("swipe",  ["down", "900"]),         # the list is scrolled to the CURRENT row
    ("sleep",  0.8),
    ("find",   "they became the Demon tonight"),
    ("absent", "WHO DID THEY CHOOSE\\?.*Imp"),
    ("find",   "\\[Run anyway\\]"),      # the override is back once unpinned
    ("find",   "7/8 alive"),             # exactly one death tonight
    ("audit",  None),
    ("screenshot", None),
]
