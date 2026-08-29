# C2_grind_vizier — an IMPORTED script with a real Organ Grinder and a Vizier.
#
#   ./emu.sh launch emulator-5556 --fresh
#   ./scenario.py emulator-5556 C2_grind_vizier
#
# Playtest C could not reach the Organ Grinder at all (it is on no base script
# and the seat sheet searches the script only); Fix-G reached the secret-vote UI
# through the `secretVotes` HOUSE RULE. This drives it from a real, in-play,
# sober Organ Grinder — the condition `DayRules.secretVoting` was actually
# written for — on a script pasted into "Import script (paste link or JSON)".
#
# What the screenshots have to show:
#   ~step 12  the import lands: "C2 Grind · 10 characters".
#   ~step 44  the day's stat strip reads "Secret voting — the Organ Grinder is
#             sober; close eyes for the tally." and MORNING BRIEFING carries
#             "Player 2 cannot die during the day (Vizier)." plus the Vizier's
#             day-execution fact — no house rule anywhere.
#   ~step 52  the vote panel: the SECRET badge, "Eyes closed, everyone. (If
#             asked: an Organ Grinder is in play.)", the tally as "•••".
#   ~step 62  the execution sheet for the Vizier says the SAME fact three
#             times, one of them leaking the raw enum:
#               "Player 2 cannot die during the day."      (protection)
#               "! Player 2 carries DAY_IMMUNE — check …"  (Execution.kt:488,
#                                                  effect.label is empty)  [C2-4]
#               "! Player 2 cannot die during the day."    (consequence) [C2-5]

SCRIPT_JSON = (
    '[{"id":"_meta","name":"C2 Grind"},"virgin","mayor","butler","saint",'
    '"poisoner","organgrinder","vizier","psychopath","baron","imp"]'
)

STEPS = [
    ("wait", "New game"),
    ("tap", "New game"),
    ("wait", "SCRIPT"),

    # --- the imported script ---------------------------------------------
    ("tap", "Import script \\(paste"),
    ("wait", "Paste a share LINK"),
    ("tapxy", ["540", "1264"]),
    ("sleep", 0.8),
    ("type", SCRIPT_JSON),
    ("sleep", 1.2),
    ("tap", "^Import$"),
    ("sleep", 2.0),
    ("find", "C2 Grind"),
    ("screenshot", None),

    ("tap", "^Collapse$"),
    ("sleep", 1.2),
    ("wait", "Start empty"),
    ("tap", "Start empty"),
    ("wait", "Before the first night"),
    ("tap", "^Close$"),
    ("sleep", 1.2),

    # --- seat 1 = Organ Grinder ------------------------------------------
    ("tap", "^Seat 1,"),
    ("wait", "Change"),
    ("tap", "Change…"),
    ("wait", "Search characters"),
    ("tap", "Search characters"),
    ("type", "Organ"),
    ("sleep", 0.9),
    ("back", None),
    ("sleep", 0.6),
    ("tap", "All players keep their eyes closed when voting"),
    ("wait", "Before the first night"),
    ("tap", "^Close$"),
    ("sleep", 1.2),
    ("back", None),
    ("wait", "^Seat 1,"),
    ("sleep", 1.0),

    # --- seat 2 = Vizier --------------------------------------------------
    ("tap", "^Seat 2,"),
    ("wait", "Change"),
    ("tap", "Change…"),
    ("wait", "Search characters"),
    ("tap", "Search characters"),
    ("type", "Vizier"),
    ("sleep", 0.9),
    ("back", None),
    ("sleep", 0.6),
    ("tap", "All players know you are the Vizier"),
    ("wait", "Before the first night"),
    ("tap", "^Close$"),
    ("sleep", 1.2),
    ("back", None),
    ("wait", "^Seat 1,"),
    ("sleep", 1.0),

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

    ("find", "Secret voting — the Organ Grinder is sober"),
    ("tap", "MORNING BRIEFING"),
    ("sleep", 1.5),
    ("find", "cannot die during the day \\(Vizier\\)"),
    ("screenshot", None),
    ("audit", None),

    # --- nominate the Vizier ---------------------------------------------
    ("tap", "^Nominate$"),
    ("sleep", 1.5),

    # Player 3 nominates the Vizier (Player 2). Ring seats are addressed by
    # coordinate because a chip and a ring token share the text "Player N",
    # and `tap` picks the ring (the smaller box) every time.
    ("tapxy", ["973", "925"]),
    ("sleep", 1.5),
    ("tapxy", ["846", "746"]),
    ("sleep", 2.5),
    ("screenshot", None),                    # the Vizier's nomination row
    # (The "Eyes closed, everyone. (If asked: an Organ Grinder is in play.)"
    #  line and the "•••" tally sit below the fold at both scroll positions,
    #  so `find` skips them as scrolled out — the screenshots are the record.)
    ("swipe", ["up", "600"]),
    ("sleep", 1.5),
    ("screenshot", None),                    # SECRET · "•••" · hold to peek

    # four hands — 8 alive, so 4 is the threshold
    ("tapxy", ["407", "1541"]),
    ("tapxy", ["638", "1541"]),
    ("tapxy", ["869", "1541"]),
    ("tapxy", ["176", "1683"]),
    ("sleep", 1.5),
    ("tap", "Lock in silently"),
    ("sleep", 2.5),
    ("find", "Someone is about to die"),     # the strip conceals the name

    # --- the execution sheet ---------------------------------------------
    ("swipe", ["up", "600"]),
    ("sleep", 1.5),
    ("tap", "^DUSK$"),
    ("sleep", 2.0),
    ("tap", "Execute Player 2"),
    ("sleep", 2.5),
    ("screenshot", None),
    ("audit", None),
    # C2-4: a raw EffectKind enum name in storyteller-facing copy.
    ("find", "DAY_IMMUNE"),
    # C2-5: the same fact three times on one sheet.
    ("find", "cannot die during the day"),
]
