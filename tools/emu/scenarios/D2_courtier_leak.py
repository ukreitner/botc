# Re-test D2-2 (P0) — the Courtier's night step hands the Courtier the whole
# grimoire: every character in play, and which seat holds it.
#
#   ./emu.sh launch emulator-5558 --fresh
#   ./scenario.py emulator-5558 D2_bmr8_setup
#   zsh scenarios/D2_bmr8_assign.sh emulator-5558
#   ./scenario.py emulator-5558 D2_courtier_leak
#
# Picks up on the "Before the first night" checklist that the last assignment
# raises (one row: Demon bluffs), satisfies it, and walks night 1 to the
# Courtier at step 4 of 5.
#
# What it proves:
#   * the card's headline is "Whoever they name: these seats hold the
#     characters in play" with the full seat->character map underneath;
#   * the gold PRIMARY is
#     SHOW "GOSSIP, INNKEEPER, COURTIER, MINSTREL, FOOL, TINKER, ASSASSIN,
#     ZOMBUUL" TO CY  — i.e. the default action is to show the Courtier the
#     Assassin and the Zombuul;
#   * pressing it opens a full-screen "THESE CHARACTERS" card with all eight.
#
# The Courtier learns NOTHING in Bad Moon Rising ("Once per game, at night,
# choose a character: they are drunk for 3 nights"). InfoCalc.courtier's own
# KDoc calls this "for the Courtier's pick" — a storyteller aid — but nothing
# marks the InfoResult storyteller-only, so NightCard renders it as SHOW-to-holder.
STEPS = [
    ("wait",  "Before the first night"),
    ("tap",   "Demon bluffs"),
    ("sleep", 2.0),
    ("tap",   "Suggest 3"),
    ("sleep", 1.5),
    ("back",  None),
    ("sleep", 1.5),
    ("tap",   "^Close$"),
    ("sleep", 1.5),
    ("back",  None),
    ("sleep", 1.5),

    ("tap",   "Begin night"),
    ("sleep", 2.5),
    ("tap",   "DONE — NEXT STEP"),      # 1 Dusk
    ("sleep", 2.0),
    ("tap",   "DONE — NEXT STEP"),      # 2 Minion info
    ("sleep", 2.0),
    ("tap",   "DONE — NEXT STEP"),      # 3 Demon info
    ("sleep", 2.0),

    ("find",  "step 4 / 5"),
    # the leak block sits BELOW the character grid
    ("swipe", ["up", "900"]),
    ("sleep", 0.8),
    ("swipe", ["up", "900"]),
    ("sleep", 0.8),
    ("swipe", ["up", "900"]),
    ("sleep", 1.0),
    ("find",  "these seats hold the characters in play"),
    ("find",  "SHOW: GOSSIP, INNKEEPER, COURTIER"),
    ("find",  "SHOW “GOSSIP, INNKEEPER, COURTIER"),
    ("screenshot", None),
    ("audit", None),
]
