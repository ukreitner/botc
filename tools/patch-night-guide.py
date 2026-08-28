#!/usr/bin/env python3
"""Apply the audited corrections to engine/.../botc/data/night_guide.json.

Unlike characters.json and night_and_jinxes.json (regenerated wholesale by
``tools/regen-data.py`` from the official data), the guide is hand-written
storyteller prose with no machine-readable upstream.  This script is the
record of what was changed and why, and is idempotent: every operation is
either a full replacement or is skipped when its result is already present,
so re-running it is a no-op.

Sources for every change:
  * docs/audit/mechanics/data-accuracy.md §5.1 — 136 defects (30 P0 / 51 P1 /
    55 P2) found by checking all 116 entries against the wiki page-by-page.
  * docs/audit/mechanics/data-accuracy.md §5.2/§5.3 — the `setup` / `day` /
    `reference` channels and the 55 characters with no entry at all.
  * docs/audit/digest/*.md `data:` lines.
  * docs/audit/characters/djinn.md.
  * Lead decisions D11, D23, D31.

Schema (ARCHITECTURE §2.14): each entry may carry `first`, `other`, `setup`,
`day` and `reference`, all of the same `GuideNight` shape.  The current Kotlin
parser uses `ignoreUnknownKeys`, so the three new channels are inert until WP0
adds them to `NightGuideEntry` — the app still loads this file today.

`GuideShow.kind` must stay inside `NightGuide.VALID_KINDS`
("message"/"token"/"good"/"evil") and `token` inside `VALID_TOKENS`
(""/"self"/"pick") — the renderer rejects anything else.

Usage:  python3 tools/patch-night-guide.py [--check]
"""

from __future__ import annotations

import argparse
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
DATA_DIR = os.path.join(ROOT, "engine", "src", "main", "resources", "botc", "data")
GUIDE = os.path.join(DATA_DIR, "night_guide.json")

CHANNELS = ("first", "other", "setup", "day", "reference")

# ---------------------------------------------------------------------------
# 1. Marker entries (lead D23)
#
# DUSK / MINION_INFO / DEMON_INFO / DAWN are rows on the night sheet with no
# character behind them, and today they are the only rows with no run-book.
# ---------------------------------------------------------------------------

MARKERS = {
    "DUSK": {
        "first": {
            "instructions": (
                "Announce that night is falling and wait for the table to go quiet and "
                "for every player to have their eyes closed. Before you wake anyone: place "
                "the setup tokens that must already be down (the Drunk's and Marionette's "
                "identity tokens, the Fortune Teller's Red Herring, the Steward's and "
                "Knight's and Noble's Know tokens, the No Dashii's two Poisoned tokens, the "
                "Lycanthrope's Faux Paw, the Toymaker's Final Night: No Attack). Then work "
                "down the night sheet in order."
            ),
            "shows": [],
        },
        "other": {
            "instructions": (
                "Dusk. Announce that night is falling and wait for quiet. Sweep the "
                "grimoire before any step runs: remove the tokens that expire at dusk "
                "(Poisoner's Poisoned, Devil's Advocate's Survives Execution, Butler's "
                "Master, Goon's and Organ Grinder's Drunk, the Barista's tokens, the Bone "
                "Collector's Has Ability, Xaan's X), advance the countdown tokens "
                "(Courtier's Drunk 1/2/3, Summoner's and Xaan's Night N, Leviathan's and "
                "Riot's Day N) and check whether a Minstrel's Everyone Is Drunk has now "
                "lasted its second dusk. Only then start the night sheet."
            ),
            "shows": [],
        },
    },
    "MINION_INFO": {
        "first": {
            "instructions": (
                "Only with 7 or more players — unless a Toymaker is in play, in which case "
                "run it at any player count. Wake all Minions together and let them make eye "
                "contact with each other: that is how they learn who their fellow Minions "
                "are. Show the THIS IS THE DEMON info token and point to the Demon. Then "
                "show any extra tokens this script demands: the Damsel token if a Damsel is "
                "in play, the Lil' Monsta token, the Storm Catcher's chosen character. Put "
                "them all back to sleep.\n\n"
                "Skip or alter this step for: a living Poppy Grower (nobody learns anyone — "
                "run the Poppy Grower's own step instead); a Marionette (never woken here, "
                "and never woken for any step that would confirm they are a Minion — Snitch, "
                "Preacher, Lil' Monsta, Poppy Grower, Hatter, Damsel); a Magician (the Demon "
                "step is the one that changes, not this one); a Summoner or Kazali script "
                "(there is no Demon yet); Lil' Monsta (skip both info steps on night 1); a "
                "Tor (skip both info steps); Legion (all evil are Legion, so there is no "
                "separate Minion group)."
            ),
            "shows": [
                {
                    "label": "To the Minions",
                    "kind": "message",
                    "text": "THIS IS THE DEMON",
                    "token": "",
                }
            ],
        }
    },
    "DEMON_INFO": {
        "first": {
            "instructions": (
                "Only with 7 or more players — unless a Toymaker is in play, in which case "
                "run it at any player count. Wake the Demon. Show the THESE ARE YOUR MINIONS "
                "info token and point to each Minion, then show the THESE CHARACTERS ARE NOT "
                "IN PLAY info token and 3 good character tokens that are not in play. Put "
                "the Demon back to sleep.\n\n"
                "Variations: with a Magician, point to the Magician among the Minions and "
                "point to the evil players in any order, so the Demon cannot tell which one "
                "is the Magician. With a Snitch, the Demon's bluffs come from the Snitch's "
                "step and each Minion gets 3 of their own. With a Marionette, point to the "
                "Marionette as a Minion (unless a Magician is alive, in which case do not "
                "reveal which neighbour it is). With Legion, let all Legion players make eye "
                "contact instead and point out the non-Legion players — with a Magician, wake "
                "Legion in separate groups. Skip entirely for a living Poppy Grower, for "
                "Lil' Monsta's first night, and for a Tor."
            ),
            "shows": [
                {
                    "label": "Your Minions",
                    "kind": "message",
                    "text": "THESE ARE YOUR MINIONS",
                    "token": "",
                },
                {
                    "label": "Bluffs",
                    "kind": "message",
                    "text": "THESE CHARACTERS ARE NOT IN PLAY",
                    "token": "",
                },
            ],
        }
    },
    "DAWN": {
        "first": {
            "instructions": (
                "Wait a few seconds after the last step, then wake everyone. Announce, in "
                "this order: any player who died in the night (name them, never the cause), "
                "any player who is alive again, and the public announcements this script "
                "owes the table — the Leviathan and the Vizier are announced now, a Storm "
                "Catcher's favoured character, a Gnome's Amigo, an Angel's or Buddhist's "
                "affected players. Compute the dawn report BEFORE sweeping any tokens, then "
                "remove the tokens that expire at dawn (Monk's and Innkeeper's Safe, "
                "Exorcist's Chosen, Lunatic's Chosen, the night-1 info tokens when you no "
                "longer need them)."
            ),
            "shows": [],
        },
        "other": {
            "instructions": (
                "Wait a few seconds, then wake everyone and announce who died in the night — "
                "names only, never the cause, and never why someone did not die. Then make "
                "the announcements the rules require even though they leak nothing: a player "
                "who is alive again (Professor, Shabaloth, Al-Hadikhia — 'do not say why'), a "
                "player executed yesterday who survived (Devil's Advocate, sober Sailor, "
                "Lleech host — 'they were executed but remain alive'), the Al-Hadikhia's 3 "
                "marked players and whether each is alive or dead even if nothing changed, "
                "and a Banshee killed by the Demon. Compute all of this BEFORE sweeping "
                "tokens, then remove the dawn-expiring tokens and place the new day's "
                "default tokens (Flowergirl's Demon Not Voted, Town Crier's Minions Not "
                "Nominated)."
            ),
            "shows": [],
        },
    },
}

# ---------------------------------------------------------------------------
# 2. Channel moves — a channel that must not exist any more
# ---------------------------------------------------------------------------

# gnome has no official night action at all (data-accuracy P2 #17): its whole
# run-book is a day procedure. Move it, rewritten, into `day`.
MOVES = [("gnome", "first", "day")]

# ---------------------------------------------------------------------------
# 3. Surgical replacements — the sentence the app states that the wiki denies.
#    (id, channel, old, new). `old` must appear exactly once.
# ---------------------------------------------------------------------------

SUBS = [
    # ---- §5.1 pass 1, the four P0s -------------------------------------
    (
        "undertaker",
        "other",
        "Only act if a player was executed today (marked Died today), even if they did not "
        "die from it.",
        "Only act if a player DIED BY execution today (marked Died Today). If the executed "
        "player survived - Fool, Devil's Advocate, sober Sailor, Pacifist, Lleech host, "
        "Mayor bounce - the Undertaker does not wake and learns nothing. Travellers are "
        "exiled, not executed, so an exile never triggers the Undertaker (the Scapegoat, "
        "who is executed in a resident's place, is the exception).",
    ),
    (
        "vortox",
        "other",
        "If the Vortox is drunk or poisoned, no one dies, but information is still false.",
        "If the VORTOX is drunk or poisoned it has no ability tonight: nobody dies, Townsfolk "
        "information is NOT forced false, and the no-execution loss condition does not apply "
        "at this dusk (lead D11).",
    ),
    (
        "vortox",
        "other",
        "Remember that while the Vortox lives, all Townsfolk abilities yield false "
        "information - every piece of Townsfolk info you give this game must be wrong.",
        "Remember that while the Vortox is alive and sober, every piece of information a "
        "Townsfolk ability produces must be false - false, not merely allowed to be false. "
        "This covers ability information only: it does not touch what you tell players when "
        "you explain a rule, or what they learn because their character or alignment changed.",
    ),
    (
        "shabaloth",
        "other",
        "place the Alive reminder token, and at dawn announce that they are alive again "
        "(they learn no new information and their once-per-game abilities remain as they "
        "were)",
        "remove their shroud, place the Alive reminder token, and at dawn announce that they "
        "are alive again (do not say why). The regurgitated player REGAINS their ability, "
        "including a once-per-game ability they had already spent - clear that mark. If "
        "their ability is first-night-only or 'you start knowing', re-run it now; otherwise "
        "they wake later tonight at their normal place in the night order",
    ),
    (
        "shabaloth",
        "other",
        "they point to two players, and both die - place Dead reminder tokens and announce "
        "the deaths at dawn. Protection (Innkeeper, sober Sailor, Devil's Advocate does not "
        "apply at night) prevents those deaths.",
        "they point to two players IN ORDER, and each dies in that order - resolve the first "
        "death fully before the second, because the first can change whether the second "
        "happens (the wiki's example: the Tea Lady's neighbour dies first, so the Tea Lady is "
        "no longer protected and dies too). Place Dead reminder tokens and announce the "
        "deaths at dawn. Check each victim against the protections actually in play rather "
        "than a fixed list - the app's death notes do this for you.",
    ),
    (
        "butler",
        "first",
        " This still applies even if the Butler is drunk or poisoned.",
        " A drunk or poisoned Butler has no ability and may vote freely. If the Butler votes "
        "illegally by mistake, tally the vote anyway - a vote that silently goes missing "
        "outs them.",
    ),
    # ---- §5.1 pass 1, the rest -----------------------------------------
    (
        "mezepheles",
        "first",
        "the first good player to say it publicly becomes evil that night.",
        "the first good player to say it - publicly OR privately, including privately to you "
        "- becomes evil that night.",
    ),
    (
        "mezepheles",
        "other",
        "Consider waking them alongside or before the Demon so the evil team can learn its "
        "new member; put everyone back to sleep afterwards.",
        "Turn the turned player's character token upside down in the grimoire and remove the "
        "Mezepheles' night token from the night sheet. If the MEZEPHELES is drunk or poisoned "
        "on the night a player would turn, the player stays good and the Mezepheles has still "
        "used their ability - mark No Ability; they may not turn anyone later. "
        "(Optional flavour, not a rule: you may wake the new evil player alongside the Demon "
        "so the evil team can learn its new member.)",
    ),
    (
        "harpy",
        "other",
        "If yesterday's madness was broken, one or both of yesterday's targets might die "
        "tonight.",
        "The penalty is a DAY-time decision, not a night kill: tomorrow, if the player marked "
        "Mad is not mad that the player marked 2nd is evil, you may kill one or both of them "
        "during the day.",
    ),
    (
        "harpy",
        "first",
        "They point to two players in order;",
        "They point to one player, THEN another - one at a time, not two at once;",
    ),
    (
        "harpy",
        "other",
        "They point to two players in order;",
        "They point to one player, THEN another - one at a time, not two at once (either may "
        "be a dead player, in which case only the living one can be killed);",
    ),
    (
        "philosopher",
        "first",
        "They either shake their head no or point to a good character on their sheet. If "
        "they choose a character, they gain that ability from now on: swap in that "
        "character's token, keep the 'Is the Philosopher' reminder by it, and if the chosen "
        "character is already in play, that player becomes drunk for as long as the "
        "Philosopher is sober and healthy - place the Drunk reminder by them.",
        "They either shake their head no or point to any Townsfolk OR Outsider icon on their "
        "sheet. If they choose a character, they gain that ability from now on, and the two "
        "branches differ: if the chosen character is NOT in play, swap the Philosopher token "
        "for the chosen character token and mark them with the Is The Philosopher reminder. "
        "If the chosen character IS in play, do not swap anything - the player who really "
        "holds that character becomes drunk for as long as the Philosopher is sober and "
        "healthy (place the Drunk reminder by them), and you may use that character's "
        "reminder tokens for the Philosopher.",
    ),
    (
        "philosopher",
        "other",
        "They either shake their head no or point to a good character on their sheet. If "
        "they choose a character, they gain that ability from now on: swap in that "
        "character's token, keep the 'Is the Philosopher' reminder by it, and if the chosen "
        "character is already in play, that player becomes drunk - place the Drunk reminder "
        "by them.",
        "They either shake their head no or point to any Townsfolk OR Outsider icon on their "
        "sheet. If the chosen character is NOT in play, swap the Philosopher token for it and "
        "mark them with the Is The Philosopher reminder; if it IS in play, swap nothing and "
        "the real holder becomes drunk - place the Drunk reminder by them. If the Philosopher "
        "dies, the player they made drunk becomes sober: remove that Drunk reminder. If the "
        "Philosopher regains their ability via the Bone Collector, or acts twice via the "
        "Barista, they may choose again - the same ability or a new one.",
    ),
    (
        "exorcist",
        "other",
        "the Demon does not act tonight and does not wake for their ability.",
        "the Demon does not use THIS ability tonight - it does not kill. Every other Demon "
        "ability still functions: a Zombuul still survives its first death, a Pukka still "
        "kills the player it poisoned on a previous night, a Shabaloth still regurgitates, "
        "and the Demon still wakes if another character's ability needs it to.",
    ),
    # ---- §5.1 pass 2 -----------------------------------------------------
    (
        "preacher",
        "other",
        "All Minions ever chosen by a sober, healthy Preacher have no ability.",
        "Minions marked No Ability lose it only WHILE THE PREACHER IS ALIVE, SOBER AND "
        "HEALTHY. If the Preacher dies or becomes drunk or poisoned, every preached Minion "
        "gets their ability back; if the Preacher recovers, they lose it again. Never wake a "
        "Marionette for this step.",
    ),
    (
        "fanggu",
        "other",
        "That player dies, unless they are an Outsider and no Outsider has jumped yet this "
        "game: in that case the Fang Gu dies instead,",
        "That player dies. If they are an Outsider who ACTUALLY DIES from this attack and no "
        "Outsider has jumped yet this game, the Fang Gu dies instead",
    ),
    (
        "fanggu",
        "other",
        "swap their token in the Grimoire and place the 'Once' reminder to mark that the "
        "jump has happened.",
        "swap their token in the Grimoire and put the Once reminder in the CENTRE of the "
        "grimoire, not on a seat - it stays there for the rest of the game even if the Fang "
        "Gu dies or changes character. The new Fang Gu does not learn who the Minions are. "
        "If protection stops the Outsider's death there is no jump at all and the Fang Gu "
        "lives.",
    ),
    (
        "king",
        "first",
        " Skip this step if a Poppy Grower is in play and alive.",
        " Run this even if a living Poppy Grower is in play: the Poppy Grower only suppresses "
        "the Minion Info and Demon Info steps, and there is no King-Poppy Grower jinx.",
    ),
    (
        "king",
        "other",
        "Only act if the dead players equal or outnumber the living.",
        "Only act once the dead players equal or outnumber the living - when that first "
        "happens, add a night token to the King's row on the night sheet. (If a King is "
        "created mid-game, the Demon learns who the King is that night.)",
    ),
    (
        "xaan",
        "other",
        "When the night number equals X, replace it with the X reminder: every Townsfolk is "
        "poisoned from that night until dusk,",
        "When the night number equals X, and only if the XAAN IS ALIVE, replace it with the X "
        "reminder: every Townsfolk is poisoned from that night until dusk,",
    ),
    (
        "xaan",
        "first",
        "You secretly chose X during setup (X equals the number of Outsiders in play).",
        "You secretly chose X during setup: X is the number of Outsiders in play DURING "
        "SETUP, and it never changes, even if the number of Outsiders changes later.",
    ),
    (
        "summoner",
        "other",
        "If the Summoner is dead or drunk/poisoned on night 3, no Demon is created (or you "
        "choose, per your ruling) - by default evil loses without a Demon.",
        "If the Summoner dies before creating the Demon, GOOD WINS IMMEDIATELY - at that "
        "moment, not on night 3. If the Summoner is alive but drunk or poisoned on night 3 "
        "so that no Demon can be created, good wins as well. This is a rule, not a "
        "storyteller judgement. The new Demon does not learn which players are the Minions, "
        "and the Minions do not learn the new Demon.",
    ),
    (
        "duchess",
        "other",
        "Only act if players visited the Duchess today.",
        "Only act if EXACTLY THREE players volunteered to visit the Duchess today. If more or "
        "fewer than three volunteered, place no reminders at all and skip the Duchess "
        "entirely.",
    ),
    (
        "duchess",
        "other",
        "Wake each player marked Visitor or False Info one at a time: show them the Duchess "
        "token,",
        "Wake each player marked Visitor or False Info one at a time: show them the THIS "
        "CHARACTER SELECTED YOU info token and the Duchess token,",
    ),
    (
        "courtier",
        "first",
        "that player becomes drunk: place the Drunk 3 reminder token by the chosen player "
        "and mark the Courtier with No Ability.",
        "that player becomes drunk for 3 nights and 3 days: place the Drunk 1 reminder by "
        "them (the official count goes UP - Drunk 1 tonight, Drunk 2 the next night, Drunk 3 "
        "the night after). Do NOT mark the Courtier with No Ability yet: that token goes on "
        "only when the three nights have finished, because the drunkenness is suspended while "
        "the Courtier is themselves drunk or poisoned and resumes if they recover.",
    ),
    (
        "courtier",
        "other",
        "If a player is marked drunk by the Courtier, reduce the counter (Drunk 3 to Drunk 2 "
        "to Drunk 1); after the third night and day the drunkenness ends and the reminder is "
        "removed.",
        "If a player is marked drunk by the Courtier, advance the counter (Drunk 1 to Drunk 2 "
        "to Drunk 3); after the third night and day the drunkenness ends, remove the reminder "
        "and mark the Courtier with No Ability.",
    ),
    (
        "courtier",
        "other",
        "if that character is in play, that player is drunk for 3 nights and 3 days (place "
        "Drunk 3, mark the Courtier with No Ability).",
        "if that character is in play, that player is drunk for 3 nights and 3 days (place "
        "Drunk 1; the Courtier's No Ability token goes on only when those three nights end).",
    ),
    (
        "cerenovus",
        "first",
        "if they do not make a convincing effort, you may execute them.",
        "if they do not make a convincing effort, you may execute them. Declare that "
        "execution publicly; it counts as that day's one execution, so if it happens before "
        "any other the day ends immediately.",
    ),
    (
        "cerenovus",
        "other",
        "if they do not make a convincing effort, you may execute them.",
        "if they do not make a convincing effort, you may execute them. Declare that "
        "execution publicly; it counts as that day's one execution, so if it happens before "
        "any other the day ends immediately.",
    ),
    (
        "lunatic",
        "first",
        "point to arbitrary players as their 'Minions' (as many as there are Minions in "
        "play), and show 3 good characters as their 'not in play' bluffs.",
        "show the THESE ARE YOUR MINIONS info token and point to arbitrary players (as many "
        "as there are real Minions in play, counting a Marionette), then show the THESE "
        "CHARACTERS ARE NOT IN PLAY info token and 3 good character tokens. The Lunatic gets "
        "its OWN set of 3 bluffs, chosen separately from the real Demon's, and they may "
        "include characters that are actually in play.",
    ),
    (
        "lunatic",
        "first",
        "Then wake the real Demon, show them the Demon's own character token, point to the "
        "Lunatic player,",
        "Then wake the real Demon, show them the Lunatic character token, point to the "
        "Lunatic player,",
    ),
    (
        "lunatic",
        "other",
        "wake the real Demon, point to each marked player so the Demon knows the Lunatic's "
        "choices,",
        "wake the real Demon, show them the Lunatic character token, point at the Lunatic, "
        "and then point to each marked player so the Demon knows the Lunatic's choices,",
    ),
    (
        "barista",
        "first",
        "ignore drunkenness and poisoning and give true info, or wake them twice or double "
        "their ability's effect.",
        "ignore drunkenness and poisoning and give true information (this player gets TRUE "
        "info even if a Vortox is in play), or run their ability twice. For a once-per-game "
        "ability, 'twice' means: if they have already spent it they may use it again; if they "
        "have not spent it they may use it twice before dusk.",
    ),
    (
        "barista",
        "other",
        "ignore drunkenness and poisoning and give true info, or wake them twice or double "
        "their ability's effect.",
        "ignore drunkenness and poisoning and give true information (this player gets TRUE "
        "info even if a Vortox is in play), or run their ability twice. For a once-per-game "
        "ability, 'twice' means: if they have already spent it they may use it again; if they "
        "have not spent it they may use it twice before dusk.",
    ),
    (
        "alchemist",
        "first",
        "Keep the 'Is The Alchemist' reminder by them so you remember which ability they "
        "hold.",
        "Mark them with the Is The Alchemist reminder AND swap the Alchemist token in the "
        "grimoire for that Minion token, turned upside down. The ability may duplicate a "
        "Minion that is in play, not only a not-in-play one.",
    ),
    (
        "alchemist",
        "first",
        "you may prompt them to choose differently if their choice would badly harm the good "
        "team.",
        "if their choice would badly harm the good team you may ask them to choose "
        "differently, and the Alchemist must do so.",
    ),
    (
        "bountyhunter",
        "other",
        "wake the Bounty Hunter and point to another evil player, moving the Know reminder "
        "to them,",
        "wake the Bounty Hunter and point to another evil player, moving the Know reminder to "
        "them - never a player the Bounty Hunter has already been shown, as they cannot learn "
        "the same evil player twice,",
    ),
    (
        "bountyhunter",
        "first",
        "Remember that with a Bounty Hunter in play, 1 Townsfolk is evil; the known player "
        "may be that evil Townsfolk.",
        "Remember that with a Bounty Hunter in play, 1 Townsfolk is evil: during setup, turn "
        "that Townsfolk's character token upside down to record it. The known player may be "
        "that evil Townsfolk.",
    ),
    (
        "farmer",
        "other",
        "Swap their character token for a Farmer in the Grimoire; they are now the Farmer "
        "with the full ability.",
        "Swap their character token for a Farmer in the Grimoire; they are now the Farmer "
        "with the full ability and are no longer their old character - remove that "
        "character's reminder tokens and end any ongoing effect it was causing immediately.",
    ),
    (
        "leviathan",
        "first",
        "Track executions of good players with the Good Player Executed reminder: if a "
        "second good player is ever executed, evil wins immediately.",
        "Track executions of good players with the Good Player Executed reminder: if a second "
        "good player is ever executed, evil wins immediately. ALL types of execution count, "
        "even when the player does not die - a Virgin execution, a revealed Mutant, a "
        "Pacifist save, a Devil's Advocate save all still count as executions.",
    ),
    (
        "plaguedoctor",
        "other",
        "Do not tell the players which ability you gained.",
        "Do not tell the players which ability you gained. If the gained ability acts at "
        "night, add a night token for it to the night sheet and run it at that character's "
        "usual position every night from now on.",
    ),
    (
        "dreamer",
        "first",
        "If the Dreamer is drunk or poisoned, or the Vortox is in play, neither token needs "
        "to be correct.",
        "If the Dreamer chooses a Townsfolk or Outsider, the false token must be a Minion or "
        "Demon; if they choose a Minion or Demon, the false token must be a Townsfolk or "
        "Outsider. If the Dreamer is drunk or poisoned you MAY make both tokens wrong; under "
        "a living sober Vortox the information MUST be false.",
    ),
    (
        "dreamer",
        "other",
        "If the Dreamer is drunk or poisoned, or the Vortox is in play, neither token needs "
        "to be correct.",
        "If the Dreamer chooses a Townsfolk or Outsider, the false token must be a Minion or "
        "Demon; if they choose a Minion or Demon, the false token must be a Townsfolk or "
        "Outsider. If the Dreamer is drunk or poisoned you MAY make both tokens wrong; under "
        "a living sober Vortox the information MUST be false.",
    ),
    (
        "nightwatchman",
        "first",
        "If the Nightwatchman is drunk or poisoned, do not wake the target; the target learns "
        "nothing, but the use is still spent.",
        "If the Nightwatchman is drunk or poisoned, still wake the target and still show them "
        "the THIS PLAYER IS token and the Nightwatchman token - but point at a DIFFERENT "
        "player. Not waking the target leaks the malfunction. The use is spent either way. "
        "Remove the Nightwatchman's night token from the night sheet once it is spent.",
    ),
    (
        "nightwatchman",
        "other",
        "If the Nightwatchman is drunk or poisoned, do not wake the target; the use is still "
        "spent.",
        "If the Nightwatchman is drunk or poisoned, still wake the target and show them the "
        "tokens, but point at a DIFFERENT player - not waking them leaks the malfunction. "
        "The use is spent either way; remove the Nightwatchman's night token from the night "
        "sheet once it is spent.",
    ),
    (
        "ojo",
        "other",
        "They choose a character (not a player) - point to tokens on a script sheet or let "
        "them name it. If that character is in play, that player dies; if it is not in play "
        "(or only registers as such), you choose any player to die instead",
        "They choose a character, not a player, by pointing at a character icon on their own "
        "character sheet. If that character is in play, that player dies - and if more than "
        "one player is that character, only one of them dies (you choose). If the named "
        "character is not in play, you choose any player to die instead, and you should "
        "almost always kill a living good player",
    ),
    (
        "villageidiot",
        "other",
        "The drunk Village Idiot (marked Drunk) or any poisoned Village Idiot may be given "
        "false information.",
        "The drunk Village Idiot (marked Drunk) or any poisoned Village Idiot may be given "
        "false information. The Drunk marker never moves: if every sober Village Idiot leaves "
        "play, or a sober one is made drunk by something else, the marked one stays the drunk "
        "one. If a Village Idiot is created mid-game, only one is created.",
    ),
    (
        "villageidiot",
        "first",
        "If there are multiple Village Idiots, one of the extras is drunk; make sure the "
        "Drunk reminder is placed.",
        "If there is only one Village Idiot in play, they are sober. If there are two or "
        "three, exactly one of them is drunk - place the Drunk reminder on them now, and "
        "never move it afterwards.",
    ),
    (
        "chef",
        "first",
        "If the Chef is drunk or poisoned, show a false number instead.",
        "If the Chef is drunk or poisoned you MAY show a false number - it is your choice, "
        "and usually a good one. Under a living sober Vortox the number MUST be false.",
    ),
    (
        "oracle",
        "other",
        "Show the hand signal for the number of dead players (0, 1, 2, etc.) that are evil, "
        "then put them back to sleep. If the Oracle is drunk or poisoned, or the Vortox is in "
        "play, give a false number.",
        "Show the hand signal for the number of dead players that are evil, then put them back "
        "to sleep. The count includes players who died tonight, evil Travellers, and any "
        "Townsfolk or Outsider who has turned evil - count every dead player's token that is "
        "upside down. If the Oracle is drunk or poisoned you MAY give a false number; under a "
        "living sober Vortox it MUST be false.",
    ),
    (
        "empath",
        "first",
        "If the Empath is drunk or poisoned, show a false number instead.",
        "If the Empath is drunk or poisoned you MAY show a false number; under a living sober "
        "Vortox it MUST be false.",
    ),
    (
        "empath",
        "other",
        "If the Empath is drunk or poisoned, show a false number instead.",
        "If the Empath is drunk or poisoned you MAY show a false number; under a living sober "
        "Vortox it MUST be false.",
    ),
    (
        "investigator",
        "first",
        "Show them the character token of a Minion that is in play,",
        "Show them the character token of any Minion on the script - it does not have to be "
        "in play, because a Recluse can register as a Minion who is not,",
    ),
    # ---- §5.1 pass 3 -----------------------------------------------------
    (
        "barber",
        "other",
        "The Demon either shakes their head no or points to two players (neither may be a "
        "Demon).",
        "The Demon either shakes their head no or points to two players. Either of them may "
        "be the Demon THEMSELF; what the Demon may not choose is another Demon player. If "
        "there is more than one living Demon, you choose which one makes the swap.",
    ),
    (
        "boffin",
        "first",
        "Both players now know which good ability the Demon carries; the Demon has this "
        "ability even while drunk or poisoned. Put both to sleep and run the granted ability "
        "at its usual place in the night order for the rest of the game.",
        "Both players now know which good ability the Demon carries. Put both to sleep and "
        "run the granted ability at its usual place in the night order for as long as the "
        "BOFFIN is alive, sober and healthy: if the Boffin dies or is drunk or poisoned, the "
        "Demon temporarily loses the good ability.",
    ),
    (
        "hatter",
        "other",
        "each may choose a new character of their own type (Minions choose Minion characters, "
        "the Demon chooses a Demon character), with no duplicates of in-play characters.",
        "each may keep their current character or point to any other character of their own "
        "type (Minions choose Minion characters, the Demon chooses a Demon character). If two "
        "of them would end up as the same character, shake your head at the LATER chooser and "
        "make them pick again - including a player who wanted to stay as they are.",
    ),
    (
        "hatter",
        "other",
        "For each player who changes, show the 'You are' info token and their new character "
        "token, swap their token in the Grimoire, then put everyone back to sleep.",
        "For each player who changes, show the THIS CHARACTER SELECTED YOU info token and the "
        "Hatter token, then their new character token, swap their token in the Grimoire, and "
        "put everyone back to sleep. Remove the Tea Party Tonight reminder afterwards.",
    ),
    (
        "lycanthrope",
        "other",
        "Remember one good player registers as evil (the Faux Paw reminder) to info abilities "
        "while the Lycanthrope lives;",
        "During setup, mark one good player with the Faux Paw reminder. While the Lycanthrope "
        "lives that player registers as evil to EVERYTHING, including the Lycanthrope's own "
        "choice - so choosing them kills nobody and the Demon still kills tonight. They can "
        "never be killed by the Lycanthrope.",
    ),
    (
        "toymaker",
        "other",
        "Track with the Final Night: No Attack reminder that the Demon has used their "
        "obligatory no-attack night.",
        "The Final Night: No Attack reminder means the obligation is still OUTSTANDING, not "
        "that it has been spent: place it on the Demon at the start of the game and remove it "
        "the first night the Demon declines to attack.",
    ),
    (
        "apprentice",
        "first",
        "then run them as that character from now on (including tonight if that character "
        "acts).",
        "then run that ABILITY for them from now on (including tonight if it acts). They do "
        "not become the character: every ability that detects characters still detects them "
        "as the Apprentice, and as a Traveller they can be exiled but never executed. Only "
        "abilities of characters on this script may be given.",
    ),
    (
        "kazali",
        "first",
        "they point to each player they want as a Minion and pick which Minion character each "
        "becomes (one at a time).",
        "they point to each player they want as a Minion and pick which Minion character each "
        "becomes, one at a time, until the NORMAL NUMBER OF MINIONS for this player count "
        "exists. Only Minions that are on the script may be chosen, and no two may be the "
        "same Minion.",
    ),
    (
        "lleech",
        "first",
        "this is the Lleech's host, poisoned for as long as the Lleech lives.",
        "this is the Lleech's host, poisoned for as long as the Lleech is alive and has its "
        "ability.",
    ),
    (
        "lleech",
        "other",
        "If anything would kill the Lleech while its host lives, the Lleech does not die;",
        "If anything would kill the Lleech while its host lives, the Lleech does not die - "
        "unless the Lleech is drunk or poisoned, in which case the host is not poisoned and "
        "the Lleech dies normally. If the Lleech survives an execution, tell the group the "
        "player lives, but not why.",
    ),
    (
        "pithag",
        "other",
        "If a new Demon is created, deaths tonight are arbitrary - you choose who dies, "
        "overriding the normal kills.",
        "If a new Demon is created, deaths tonight are arbitrary: you may choose any players "
        "to kill OR to protect through the night to keep the game balanced. Any extra deaths "
        "count as attacks from the PIT-HAG, not from a Demon - that is what a Sage, "
        "Ravenkeeper or Godfather trigger reads.",
    ),
    (
        "po",
        "other",
        "they must point to three players tonight, and all three die.",
        "they must point to three players tonight, and each dies IN THE ORDER CHOSEN - resolve "
        "each death fully before the next. Remove the 3 Attacks reminder afterwards.",
    ),
    (
        "professor",
        "other",
        "that player is resurrected - place the Alive reminder token, mark the Professor with "
        "No Ability, and at dawn announce that the player is now alive.",
        "that player is resurrected: REMOVE THEIR SHROUD, place the Alive reminder token, mark "
        "the Professor with No Ability, and at dawn announce that the player is alive again "
        "(do not say why). The resurrected player REGAINS their ability, including a "
        "once-per-game ability they had already spent. If their ability is first-night-only or "
        "'you start knowing', wake them now and run it; otherwise put them back into tonight's "
        "night order at their normal position, and add their night token back to the night "
        "sheet.",
    ),
    (
        "vigormortis",
        "other",
        "choose one of their Townsfolk neighbours to be poisoned, marking them with the "
        "Poisoned reminder.",
        "poison one Townsfolk neighbour of that Minion: the nearest Townsfolk clockwise or the "
        "nearest counter-clockwise, SKIPPING over Outsiders, Minions and Travellers whether "
        "alive or dead, so there is always exactly one Townsfolk per killed Minion. Mark them "
        "with a Poisoned reminder. If the Vigormortis dies or loses its ability, those players "
        "become healthy again.",
    ),
    (
        "yaggababble",
        "other",
        "Resolve the deaths at the Yaggababble's place in the night order and announce them "
        "at dawn.",
        "You may kill at the Yaggababble's place in the night order or at any point afterwards "
        "up until dawn. Announce the deaths at dawn. If the Yaggababble is drunk or poisoned "
        "at the moment the kill would happen, nobody dies - even if they were sober when they "
        "said the phrase - and the converse also holds.",
    ),
    (
        "bureaucrat",
        "first",
        "Tomorrow, that player's vote counts as 3 votes.",
        "Tomorrow, that player's vote counts as 3 votes - count it out loud, as normal. The "
        "triple vote is lost immediately if the Bureaucrat dies, including by exile.",
    ),
    (
        "bureaucrat",
        "other",
        "Tomorrow, that player's vote counts as 3 votes.",
        "Tomorrow, that player's vote counts as 3 votes - count it out loud, as normal. The "
        "triple vote is lost immediately if the Bureaucrat dies, including by exile.",
    ),
    (
        "cultleader",
        "other",
        "Remember that if every good player has publicly joined the cult, the Cult Leader's "
        "team wins.",
        "Once per day the Cult Leader may publicly call for the whole town to join the cult. "
        "Run it as a formal vote, exactly the way you would run an exile: if every good player "
        "raises their hand, declare which team has won. Keep the Cult Leader's grimoire token "
        "turned to their current alignment.",
    ),
    (
        "harlot",
        "other",
        "afterwards you may choose that both the Harlot and the chosen player die tonight.",
        "afterwards you may choose that both the Harlot and the chosen player die tonight "
        "(there are two Dead reminders for exactly this). If the player who revealed is the "
        "Demon, you should NOT kill them both - that would end the game.",
    ),
    (
        "juggler",
        "other",
        "If the Juggler is drunk or poisoned, or the Vortox is in play, give a false number.",
        "Only tonight's state matters: if the Juggler guessed while drunk or poisoned but is "
        "sober and healthy now, they still get the TRUE number; if they guessed while sober "
        "but are drunk or poisoned now, you may give a false one. Under a living sober Vortox "
        "the number must be false.",
    ),
    (
        "pixie",
        "first",
        "they gain that character's ability when that player dies (then place the Has Ability "
        "reminder).",
        "they gain that character's ability when that player dies: REPLACE the Mad reminder "
        "with the Has Ability reminder, say nothing - the Pixie is not told they have gained "
        "anything - and from then on wake them whenever that Townsfolk would normally wake.",
    ),
    (
        "poisoner",
        "other",
        "Remove the Poisoned reminder from the previously poisoned player.",
        "The previous target became healthy at DUSK, before any step ran tonight - their "
        "Poisoned reminder should already be gone, so no earlier step treated them as "
        "poisoned.",
    ),
    (
        "tinker",
        "other",
        "At any time, including tonight, you may decide the Tinker dies: place the Dead "
        "reminder token and announce the death at dawn.",
        "At any time you may decide the Tinker dies. During the DAY, declare the death "
        "immediately; during the NIGHT, mark them with the Dead reminder and announce it at "
        "dawn. The Tinker cannot die from their own ability while protected from death, and "
        "you should never kill the Tinker when doing so would end the game.",
    ),
    # ---- §5.1 pass 4 -----------------------------------------------------
    (
        "legion",
        "other",
        "During the day, remember executions fail (nobody dies) if only evil players voted, "
        "and Legion registers as a Minion as well as a Demon.",
        "During the day: if a nomination gathers enough votes to put a player on the block "
        "but ONLY EVIL PLAYERS VOTED, declare that its tally is ZERO - that is not the same "
        "as a failed execution, because a later nominee with fewer votes can still go to the "
        "block. Legion registers as a Minion as well as a Demon. Aim to reach three players "
        "alive; on the final day, if the town does not execute, kill a good player. If only "
        "one good player remains alive, you may declare that evil wins.",
    ),
    (
        "lordoftyphon",
        "first",
        "Wake each of the Lord of Typhon's two neighbors one at a time:",
        "During setup, remove all Minion tokens from the bag and add Townsfolk or Outsider "
        "tokens in their place; after the deal, replace the good character tokens of the "
        "players sitting outward from the Lord of Typhon with those Minion tokens - as many "
        "on each side as this player count needs (3 Minions at 10-12 players, 4 at 13-15), so "
        "it is not always two. Wake the appropriate number of players directly clockwise and "
        "anti-clockwise, one at a time:",
    ),
    (
        "lordoftyphon",
        "first",
        "Put each to sleep before waking the next. Do not wake the Lord of Typhon itself.",
        "Put each to sleep before waking the next. Do not wake the Lord of Typhon itself. "
        "Then run the Minion Info and Demon Info steps as normal.",
    ),
    (
        "organgrinder",
        "first",
        "Remember that all day: votes happen with eyes closed and you announce only whether "
        "the nomination has enough votes, never the tally.",
        "Remember that all day, but only while the Organ Grinder is SOBER: votes happen with "
        "eyes closed, and you say nothing at all when a vote ends - not the tally, and not "
        "whether the nominee is about to die. Mark the current front-runner with the About To "
        "Die reminder and only when nominations close do you declare that the marked player "
        "is executed. If the Organ Grinder is drunk, the vote happens with eyes open as "
        "normal and you make no comment about why.",
    ),
    (
        "organgrinder",
        "other",
        "During the day, run all votes with eyes closed and keep the tally secret, announcing "
        "only whether the vote succeeded.",
        "During the day, but only while the Organ Grinder is SOBER, run all votes with eyes "
        "closed and say nothing when a vote ends - not the tally, not whether the nominee is "
        "about to die. Track the front-runner with the About To Die reminder and declare the "
        "execution only when nominations close. A drunk Organ Grinder means an ordinary "
        "eyes-open vote, with no comment from you. Dead players may still vote once if they "
        "hold a vote token; take that token at the END OF THE DAY rather than after the vote, "
        "so nobody can count. Players may not use touch or sound to work out who is voting.",
    ),
    (
        "poppygrower",
        "other",
        "it happens only once, even if the Poppy Grower dies while drunk or poisoned it is "
        "your judgement whether evil learns each other.",
        "it happens only once. If the Poppy Grower was DRUNK OR POISONED when they died, skip "
        "this step entirely - they had no ability that night, so the evil team learns nothing. "
        "The Minions learn each other by making eye contact. If a Magician is in play, this "
        "mid-game reveal is a Demon Info step like any other, so point to the Magician among "
        "the Minions and point in an order that hides which one they are. An evil Traveller "
        "still learns who the Demon is when that Traveller enters play.",
    ),
    (
        "witch",
        "other",
        "If only 3 players live, the Witch loses their ability and does not wake;",
        "As soon as just three players are alive - including part-way through this very night, "
        "after a kill - the curse is removed IMMEDIATELY: take the Cursed token off at that "
        "moment, not at the next dusk, and the Witch acts no more. Abilities never affect "
        "exiles, so a cursed player who calls for an exile does not die.",
    ),
    (
        "flowergirl",
        "other",
        "During the day, place the 'Demon voted' reminder as soon as the Demon raises their "
        "hand to vote, and clear it each dawn. If the Flowergirl is drunk or poisoned, or the "
        "Vortox is in play, give the opposite (false) answer.",
        "Each DAWN, mark the Flowergirl with Demon Not Voted and remove Demon Voted if it is "
        "there - the default token is placed every day, before anything happens. During the "
        "day, if the Demon votes for any EXECUTION, replace Demon Not Voted with Demon Voted. "
        "A hand raised and lowered before the tally does not count, and a vote on an EXILE "
        "does not count. If the Flowergirl is drunk or poisoned you MAY give the false "
        "answer; under a living sober Vortox it MUST be false.",
    ),
    (
        "towncrier",
        "other",
        "Place the 'Minion nominated' reminder during the day when a Minion nominates so you "
        "remember at night. If the Town Crier is drunk or poisoned, or the Vortox is in play, "
        "give the opposite (false) answer.",
        "Each DAWN, mark the Town Crier with Minions Not Nominated and remove Minion Nominated "
        "if it is there. During the day, if a Minion nominates, replace it with Minion "
        "Nominated; remove that token after waking the Town Crier. If the Town Crier is drunk "
        "or poisoned you MAY give the false answer; under a living sober Vortox it MUST be "
        "false.",
    ),
    (
        "eviltwin",
        "first",
        "Both players therefore know who the other is, but the good twin does not learn which "
        "of them is evil beyond this.",
        "Both players therefore know who the other is, and the good twin is explicitly shown "
        "that their twin is EVIL.",
    ),
    (
        "eviltwin",
        "first",
        "remember good cannot win while both twins live, and evil wins if the good twin is "
        "executed.",
        "remember good cannot win while both twins live, and evil wins if the good twin is "
        "executed - but only while the EVIL TWIN IS ALIVE. A dead Evil Twin has no ability, "
        "so evil does not win if the good twin is executed later.",
    ),
    (
        "scarletwoman",
        "other",
        "update the Grimoire so they are now that Demon, then put them back to sleep.",
        "update the Grimoire so they are now that Demon, mark them with the Is The Demon "
        "reminder, and put them back to sleep. Then read that Demon's own How to Run - from "
        "tonight they follow it.",
    ),
    (
        "wizard",
        "other",
        "Once the wish has been made, the Wizard never acts again.",
        "If you DECLINE a wish, the ability is not spent: prompt the Wizard to wish again on "
        "a later night. When you grant one, signal it clearly ('Your wish is granted') and "
        "make the clue about its nature a PUBLIC declaration. Only a granted wish ends the "
        "Wizard's ability.",
    ),
    # ---- §5.1 pass 5 -----------------------------------------------------
    (
        "moonchild",
        "other",
        "If the Moonchild was drunk or poisoned when they made the choice, no one dies.",
        "Only TONIGHT'S state matters, not the state at the moment of the choice: a Moonchild "
        "who was drunk or poisoned when they chose but is sober and healthy now still kills; "
        "a Moonchild who chose while sober but is drunk or poisoned now kills nobody.",
    ),
    (
        "princess",
        "other",
        "If the Princess was drunk or poisoned during that day, the Demon kills as normal.",
        "Only TONIGHT'S state matters: a Princess who was drunk during the day but is sober at "
        "night DOES stop the kill, and one who was sober by day but is drunk or poisoned at "
        "night does not.",
    ),
    (
        "princess",
        "other",
        "place the Doesn't Kill reminder:",
        "mark THE DEMON with the Doesn't Kill reminder:",
    ),
    (
        "bonecollector",
        "other",
        "that player regains their ability until dusk and may need to be woken later tonight "
        "to use it (even if they are drunk or poisoned they have the ability; run it "
        "normally).",
        "that player regains their ability until the next dusk and may need to be woken later "
        "tonight to use it. The chosen player is not told the Bone Collector chose them. A "
        "drunk or poisoned BONE COLLECTOR grants nothing at all; a drunk or poisoned TARGET "
        "wakes but their ability malfunctions as normal. At the next dusk remove the Has "
        "Ability reminder, and if the Bone Collector dies that player loses the regained "
        "ability at once.",
    ),
    (
        "damsel",
        "first",
        "Remember each Minion may make one public guess of who the Damsel is during the game; "
        "if a Minion guesses correctly, evil wins (track with the Guess Used reminder). The "
        "Damsel themself is not woken.",
        "The evil team gets ONE guess in total, however many Minions are in play: the first "
        "time any Minion publicly guesses a player as the Damsel, mark Guess Used - right or "
        "wrong, that is their only guess. If the guess is correct, evil wins. The Damsel is "
        "not woken here, unless the Huntsman chose them tonight, in which case show them the "
        "YOU ARE info token and their new character token.",
    ),
    (
        "damsel",
        "other",
        "If the Damsel was drunk or poisoned, or the Huntsman was, follow the Huntsman's step: "
        "the change may not occur.",
        "Only the HUNTSMAN's state matters: if the Huntsman is sober and healthy, a drunk or "
        "poisoned Damsel still becomes a Townsfolk.",
    ),
    (
        "alhadikhia",
        "other",
        "they silently nod to live or shake their head to die, and you announce their choice "
        "aloud ('The first chooses to live') before putting them to sleep and waking the next. "
        "Kill each player who chose to die; if all three chose to live, all three die instead.",
        "they silently nod to live or shake their head to die - say NOTHING about their answer "
        "- then put them to sleep and wake the next. Resolve each answer as it is given: a "
        "player who chooses to live has their shroud REMOVED (this can bring a dead player "
        "back to life), a player who chooses to die is given a shroud. When all three have "
        "answered, if all three are alive - none has a shroud - then all three die. A player "
        "who chose to die but did not actually die counts as alive for that check. All "
        "players must be SILENT from the moment you declare that a player has been chosen "
        "until you declare that the silence has ended; declare both.",
    ),
    (
        "alhadikhia",
        "other",
        "At dawn everyone therefore knows who the 3 choices were - resolve deaths before other "
        "dawn announcements.",
        "At DAWN, declare for each of the players marked 1, 2 and 3 whether they are alive or "
        "dead - even if nothing changed for them.",
    ),
    (
        "lilmonsta",
        "first",
        "Wake all Minions together; they silently agree (pointing, gesturing) on one of them "
        "to babysit Lil' Monsta tonight.",
        "Skip the Minion Info and Demon Info steps entirely on the first night. Wake all "
        "Minions together; the majority silently point until they settle on ONE player to "
        "babysit Lil' Monsta tonight - it may be any player, not only a Minion, and a good "
        "player who babysits 'is the Demon' while remaining good.",
    ),
    (
        "lilmonsta",
        "other",
        "If the Minions cannot agree, the token stays where it is or you decide.",
        "If the Minions cannot reach a unanimous decision, YOU decide. The player marked Is "
        "The Demon registers as the Demon: if they die, declare that the game is over and "
        "good has won.",
    ),
    (
        "riot",
        "other",
        "On night 3, you may wake all Minion players one at a time and show the 'You are' "
        "info and the Riot token - all Minions are also Riot and tomorrow nominees must "
        "nominate again. During the day, each nominee dies immediately and may (on day 3, "
        "must) nominate another player straight away; if day 3 ends with good not having won, "
        "evil wins.",
        "On night 3, wake all Minion players one at a time, show the YOU ARE info token and "
        "the Riot token, and REPLACE their Minion tokens with Riot tokens in the grimoire. "
        "The nominee-dies rule applies on DAY 3 ONLY: each nominee dies immediately and must "
        "then nominate an alive player straight away - tell them to nominate again and count "
        "down '3... 2... 1...' out loud. If the town does not nominate at all, or a nominee "
        "runs out of time, nominate a player yourself. The day runs nomination to nomination "
        "until all Riot are dead (good wins) or just 2 players are alive (evil wins).",
    ),
    (
        "innkeeper",
        "other",
        "Remove the Protected and Drunk reminder tokens from the previous night.",
        "The Safe reminders came off at DAWN and the Drunk reminder at DUSK, so the grimoire "
        "should already be clear. The Innkeeper protects at night only, never during the day.",
    ),
    (
        "innkeeper",
        "other",
        "place the Protected reminder tokens by both,",
        "place a Safe reminder by each of them - there are two Safe tokens, and both stay on "
        "the board at the same time -",
    ),
    (
        "innkeeper",
        "other",
        "If the Innkeeper is drunk or poisoned, the chosen players are not actually protected "
        "(and are not made drunk).",
        "If the Innkeeper is drunk or poisoned, the chosen players are not actually protected "
        "and are not made drunk. An Innkeeper who chooses THEMSELF may be the one made drunk - "
        "in which case they have no ability, may die tonight, and the other player they chose "
        "is not safe either.",
    ),
    (
        "zombuul",
        "other",
        "Remember: the first time the Zombuul dies, they secretly remain alive but register "
        "as dead in every way - mark this in the grimoire,",
        "Each day, mark any player who dies with Died Today - INCLUDING the Zombuul itself if "
        "it 'dies' by execution, because it registers as dead. Declare that they died and "
        "flip their life token on the town square as normal, but do NOT add a shroud. "
        "Remember: the first time the Zombuul dies, they secretly remain alive but register as "
        "dead in every way,",
    ),
    (
        "nodashii",
        "other",
        "keep Poisoned reminders on them and update if seating deaths change nothing but "
        "characters change.",
        "keep the two Poisoned reminders on them regardless of whether those players are alive "
        "or dead, and move a reminder only when the nearest Townsfolk on that side actually "
        "changes. Place both tokens while preparing the FIRST night, before anyone acts. If "
        "the No Dashii dies or loses its ability, those two players become healthy.",
    ),
    (
        "amnesiac",
        "first",
        "you tell them how accurate the guess is (for example cold, warm, hot).",
        "you tell them how accurate the guess is: cold, warm, hot, or 'bingo' if it is spot "
        "on. If they describe the ability correctly but in different words, still tell them "
        "they guessed correctly.",
    ),
    (
        "choirboy",
        "other",
        "If the Choirboy is drunk or poisoned, point to a wrong player or do not wake them.",
        "Put the Demon back to sleep BEFORE waking the Choirboy. If the Choirboy is drunk or "
        "poisoned, still wake them and point at the WRONG player - not waking them leaks the "
        "malfunction.",
    ),
    (
        "vizier",
        "first",
        "At dawn (or at the start of the game), publicly announce which player is the Vizier.",
        "When the first night has ended, publicly announce which player is the Vizier.",
    ),
    (
        "vizier",
        "first",
        "if a good player voted on any nomination, the Vizier may reveal themselves to have "
        "that nominee executed immediately.",
        "after a vote is tallied, if at least one GOOD player voted on THAT nomination, the "
        "Vizier may choose to have the nominee executed immediately. It counts as the day's "
        "one execution, and no more nominations, votes or executions happen today.",
    ),
    (
        "marionette",
        "first",
        "Do not wake the Marionette - they think they are the good character they drew and "
        "get that character's fake info at the appropriate times.",
        "Do not wake the Marionette - they think they are the good character they drew and get "
        "that character's fake info at the appropriate times. They are also never woken for "
        "any step that would confirm they are a Minion: Minion Info, Snitch, Preacher, Lil' "
        "Monsta, Poppy Grower, Hatter and Damsel all skip them.",
    ),
    (
        "gossip",
        "other",
        "If the statement was false, or the Gossip is drunk or poisoned, no one dies from this "
        "ability.",
        "Only TONIGHT'S state matters: if the Gossip made a true statement while drunk or "
        "poisoned but is sober and healthy now, a player still dies. If the statement was "
        "false, or the Gossip is drunk or poisoned now, nobody dies from this ability.",
    ),
    (
        "gossip",
        "other",
        "choose any player who is not protected from dying tonight:",
        "choose any player - we recommend choosing one who will actually die, rather than a "
        "protected player -",
    ),
]

# ---------------------------------------------------------------------------
# 4. Additive corrections — a mandatory How-to-Run step the entry omits.
# ---------------------------------------------------------------------------

APPENDS = [
    (
        "librarian",
        "first",
        "Place both reminder tokens while preparing the first night, and remove them again "
        "whenever it is convenient.",
    ),
    (
        "washerwoman",
        "first",
        "Place both reminder tokens while preparing the first night. The decoy may be any "
        "other player, including the Demon. A Spy may be shown as the Townsfolk; the Drunk "
        "never can. Remove the tokens again whenever it is convenient.",
    ),
    (
        "investigator",
        "first",
        "Place both reminder tokens while preparing the first night and remove them whenever "
        "convenient.",
    ),
    (
        "devilsadvocate",
        "other",
        "If a player marked Survives Execution is executed, declare publicly that the player "
        "was executed but remains alive - and do not say why.",
    ),
    (
        "sailor",
        "first",
        "The Sailor cannot die while sober - that includes execution. If a sober Sailor is "
        "executed, declare that this player was executed but remains alive, and do not say "
        "why.",
    ),
    (
        "sailor",
        "other",
        "The Sailor cannot die while sober - that includes execution. If a sober Sailor is "
        "executed, declare that this player was executed but remains alive, and do not say "
        "why.",
    ),
    (
        "banshee",
        "other",
        "If every good player is dead the game CONTINUES: good can still win, because the "
        "Banshee can nominate.",
    ),
    (
        "chambermaid",
        "first",
        "Do not wake the Chambermaid at all if there are not two other players alive to "
        "choose between (Mastermind day, Zombuul endgame, and so on).",
    ),
    (
        "chambermaid",
        "other",
        "Do not wake the Chambermaid at all if there are not two other players alive to "
        "choose between.",
    ),
    (
        "magician",
        "first",
        "Point to the Magician and the evil players in ANY order, so the evil players cannot "
        "tell which one is the Magician. If a Poppy Grower dies later and the evil team learns "
        "each other mid-game, the Magician's ability applies to that reveal too, exactly as if "
        "it were the first night.",
    ),
    (
        "mathematician",
        "first",
        "Each time a character's ability works abnormally because of another character's "
        "ability, mark that player with an Abnormal reminder - there are five. Show fingers "
        "for the number of players currently marked, then remove all the Abnormal reminders.",
    ),
    (
        "mathematician",
        "other",
        "Each time a character's ability works abnormally because of another character's "
        "ability, mark that player with an Abnormal reminder - there are five. Show fingers "
        "for the number of players currently marked, then remove all the Abnormal reminders.",
    ),
    (
        "ogre",
        "first",
        "If the Ogre pointed to an evil player, turn the Ogre's character token upside down - "
        "they are now evil. The Friend reminder is an optional rule; under the base rule the "
        "Ogre's alignment simply becomes the chosen player's.",
    ),
    (
        "pukka",
        "other",
        "The poison ends either way: if protection stops the kill, the target still stops "
        "being poisoned. Victims are still poisoned AT THE MOMENT OF DEATH, so resolve any "
        "death-triggered ability (Ravenkeeper, Sage) as a poisoned one - keep the Poisoned "
        "reminder next to the Dead reminder until that ability is done, then remove it.",
    ),
    (
        "engineer",
        "first",
        "They must name the correct NUMBER of Minions for this player count, and only "
        "characters that are on the script. The number of evil players never changes. "
        "Choosing a character who is already in play does nothing - and still burns the use.",
    ),
    (
        "engineer",
        "other",
        "They must name the correct NUMBER of Minions for this player count, and only "
        "characters on the script; choosing an in-play character does nothing and still burns "
        "the use. Once the ability is spent, remove the Engineer's night token from the night "
        "sheet.",
    ),
    (
        "seamstress",
        "other",
        "Once the ability is spent, remove the Seamstress's night token from the night sheet.",
    ),
    (
        "huntsman",
        "other",
        "If the DAMSEL is drunk or poisoned but the Huntsman is sober and healthy, the Damsel "
        "can still become a Townsfolk. Once the ability is spent, remove the Huntsman's night "
        "token from the night sheet.",
    ),
    (
        "assassin",
        "other",
        "The Assassin's kill ignores every protection. Once it is spent, remove the Assassin's "
        "night token from the night sheet.",
    ),
    (
        "clockmaker",
        "first",
        "Travellers count as steps when you measure the distance, but an evil Traveller is not "
        "a Minion - the Clockmaker's number counts steps to the nearest MINION.",
    ),
    (
        "fearmonger",
        "other",
        "If the marked player is executed but does not die, their team still loses.",
    ),
    (
        "undertaker",
        "other",
        "In editions that allow more than one execution in a day, YOU choose which executed "
        "character to show. If the Drunk was executed, show the Drunk token. If the executed "
        "player was a Spy or Recluse, you may show the character they register as.",
    ),
    (
        "monk",
        "other",
        "The Monk's protection also blocks the other harmful effects of the DEMON's ability - "
        "No Dashii's poison, Vigormortis's poison, a Fang Gu jump, a Lord of Typhon's "
        "conversion - not just the kill.",
    ),
    (
        "imp",
        "other",
        "If the Imp chooses a DEAD player, let them: nothing happens. A newly created Imp does "
        "not act again that same night.",
    ),
    (
        "steward",
        "first",
        "While preparing the first night, put the Know reminder by any good character token - "
        "the token must already be placed before this step runs.",
    ),
    (
        "sweetheart",
        "other",
        "The drunkenness starts IMMEDIATELY, at the moment the Sweetheart died - so if they "
        "died by execution it covers the rest of that day too. If you have not placed the "
        "token yet, do it now.",
    ),
    (
        "thief",
        "first",
        "Count the negative vote out loud, as normal. The player changes back immediately if "
        "the Thief dies. Exiles are never affected by abilities, so the marked player can "
        "still support an exile normally.",
    ),
    (
        "thief",
        "other",
        "Count the negative vote out loud, as normal. The player changes back immediately if "
        "the Thief dies. Exiles are never affected by abilities, so the marked player can "
        "still support an exile normally.",
    ),
    (
        "barber",
        "other",
        "If the Barber dies, mark them with the Haircuts Tonight reminder. If a swapped "
        "player's alignment no longer matches the colour of their character token, turn that "
        "token upside down.",
    ),
    (
        "acrobat",
        "other",
        "The Chosen token covers tonight only.",
    ),
    (
        "stormcatcher",
        "first",
        "At the start of the game, declare publicly that the Storm Catcher is in play, add "
        "the Storm Catcher token to the grimoire, and declare which good character is "
        "favoured.",
    ),
    (
        "grandmother",
        "first",
        "Choose a good player - Townsfolk or Outsider - as the grandchild.",
    ),
]

# ---------------------------------------------------------------------------
# 5. Full channel rewrites, where the entry describes the wrong ability.
# ---------------------------------------------------------------------------

SETS = {
    ("stormcatcher", "first"): {
        "instructions": (
            "Announce which good character is STORMCAUGHT (you chose it before the game, and "
            "declared at the start of the game that a Storm Catcher is in play). Two "
            "branches, and the app must run whichever applies.\n\n"
            "If that character IS in play: mark that player with the Stormcaught reminder. "
            "Wake each evil player one at a time, show them the character token, then point "
            "at the marked player, and put them back to sleep. While the marked player has "
            "their ability they can only be killed by execution - and the Stormcaught token "
            "lasts all game, so do not sweep it at dawn.\n\n"
            "If that character is NOT in play: point at nobody. Wake each evil player one at "
            "a time, show them the THESE CHARACTERS ARE NOT IN PLAY info token and the "
            "favoured character token, then put them back to sleep.\n\n"
            "Source note for the lead: roles.json says the reminder is Stormcaught and the "
            "not-in-play branch shows THESE CHARACTERS ARE NOT IN PLAY, while the wiki's "
            "older How to Run still says a SAFE reminder and a THIS PLAYER IS token. "
            "roles.json is the newer source and matches the reclassification to Loric, so it "
            "is what this entry follows."
        ),
        "shows": [
            {
                "label": "In play: favoured character",
                "kind": "token",
                "token": "pick",
                "text": "THIS PLAYER IS",
            },
            {
                "label": "Not in play",
                "kind": "token",
                "token": "pick",
                "text": "THESE CHARACTERS ARE NOT IN PLAY",
            },
        ],
    },
    ("toymaker", "first"): {
        "instructions": (
            "Resolve the Minion Info and Demon Info steps even though there are fewer than 7 "
            "players - that is the Toymaker's whole first-night job, and it is easy to miss. "
            "Then mark the Demon with the Final Night: No Attack reminder: it records that "
            "the Demon still OWES the table one night with no attack, and comes off the first "
            "night they decline to attack."
        ),
        "shows": [],
    },
    ("gnome", "day"): {
        "instructions": (
            "As soon as the Gnome enters play, mark a player of the same alignment as the "
            "Gnome with the Amigo reminder and announce publicly which player that is. The "
            "Gnome never wakes at night.\n\n"
            "During the day, if anyone nominates the Amigo, the Gnome may choose to kill the "
            "nominator immediately - but it is the GNOME's responsibility to speak up, and "
            "you may not prompt them. They must do it before you start the voting process. "
            "The nominator dies at once, and the vote for the nominee still happens."
        ),
        "shows": [
            {
                "label": "Public announce",
                "kind": "message",
                "text": "THIS PLAYER SHARES THE GNOME'S ALIGNMENT...",
                "token": "",
            }
        ],
    },
}

# ---------------------------------------------------------------------------
# 6. Show cards to add (matched by label so re-running is a no-op).
# ---------------------------------------------------------------------------

SHOWS = [
    (
        "exorcist",
        "other",
        {
            "label": "Show the Demon",
            "kind": "token",
            "text": "THIS CHARACTER SELECTED YOU",
            "token": "self",
        },
        "replace",
    ),
    (
        "lunatic",
        "first",
        {
            "label": "Your Minions",
            "kind": "message",
            "text": "THESE ARE YOUR MINIONS",
            "token": "",
        },
        "add",
    ),
    (
        "lunatic",
        "first",
        {
            "label": "Lunatic's own bluffs",
            "kind": "message",
            "text": "THESE CHARACTERS ARE NOT IN PLAY",
            "token": "",
        },
        "add",
    ),
    (
        "duchess",
        "other",
        {
            "label": "To each visitor",
            "kind": "token",
            "text": "THIS CHARACTER SELECTED YOU",
            "token": "self",
        },
        "add",
    ),
    (
        "undertaker",
        "other",
        {
            "label": "Show a false character",
            "kind": "token",
            "token": "pick",
            "text": "This character was executed today",
        },
        "add",
    ),
    (
        "hatter",
        "other",
        {
            "label": "Hatter",
            "kind": "token",
            "text": "THIS CHARACTER SELECTED YOU",
            "token": "self",
        },
        "add",
    ),
    (
        "organgrinder",
        "first",
        {
            "label": "Vote result",
            "kind": "message",
            "text": "NOMINATIONS ARE CLOSED. THIS PLAYER IS EXECUTED.",
            "token": "",
        },
        "add",
    ),
    (
        "librarian",
        "first",
        {"label": "Show 0", "kind": "message", "text": "0", "token": ""},
        "add",
    ),
]

# ---------------------------------------------------------------------------
# 7. New `setup` / `day` / `reference` channels on entries that already exist.
#    (data-accuracy §5.2 — the storyteller work that happens outside a night
#    step has had nowhere to live.)
# ---------------------------------------------------------------------------

EXTRA_CHANNELS = {
    "marionette": {
        "setup": (
            "The Marionette must be sitting NEXT TO THE DEMON. Deal the bag as normal, then "
            "pick one of the Demon's two good neighbours, take their character token, and give "
            "them the Marionette token face down without telling them - they keep believing "
            "they are the good character they drew. Place the Is The Marionette reminder on "
            "that seat. They are evil and their ability does not work."
        )
    },
    "lunatic": {
        "setup": (
            "Before the game, decide WHICH DEMON the Lunatic believes they are, and pick the "
            "Lunatic's own set of 3 bluffs - separate from the real Demon's, and allowed to "
            "include characters that are in play. Hand the Lunatic that Demon's token."
        )
    },
    "godfather": {"setup": "[-1 or +1 Outsider] - decide the count before you fill the bag."},
    "baron": {"setup": "[+2 Outsiders] - two Townsfolk slots become Outsiders."},
    "xaan": {
        "setup": (
            "Secretly choose X = the number of OUTSIDERS IN PLAY DURING SETUP. Write it down. "
            "X never changes, however the Outsider count moves later. Place the Night 1 "
            "reminder to start the count."
        )
    },
    "kazali": {
        "setup": (
            "The bag contains NO Minions - the Kazali creates them on the first night. Fill "
            "those slots with Townsfolk or Outsiders, and decide the Outsider count you want "
            "the Kazali to be able to reach."
        )
    },
    "vigormortis": {"setup": "[-1 Outsider]."},
    "fanggu": {"setup": "[+1 Outsider]. There must be at least one Outsider for the jump."},
    "balloonist": {"setup": "[+0 or +1 Outsider] - your choice, before the bag is filled."},
    "huntsman": {
        "setup": "[+the Damsel] - the Damsel must be in play; add them to the bag."
    },
    "choirboy": {"setup": "[+the King] - the King must be in play; add them to the bag."},
    "legion": {
        "setup": (
            "[Most players are Legion] - fill most of the bag with Legion tokens and deal the "
            "rest as good characters. Every Legion player is evil and registers as a Minion as "
            "well as a Demon."
        ),
        "reference": (
            "Legion has no first-night step of its own: it is handled inside DEMON INFO. "
            "During that step, let all Legion players make eye contact with each other, and "
            "you may point to the non-Legion players so Legion knows who they are. If a "
            "Magician is in play, wake Legion in separate groups instead, and no group learns "
            "the Magician."
        ),
    },
    "villageidiot": {
        "setup": (
            "[+0 to +2 Village Idiots] - decide how many are in play. If there is more than "
            "one, mark exactly one of them Drunk now, and never move that token."
        )
    },
    "eviltwin": {
        "setup": (
            "Pick the good twin: a player of the opposite alignment sitting anywhere. Mark them "
            "with the Twin reminder before the first night."
        )
    },
    "mezepheles": {
        "setup": (
            "Write the secret word down before the game. Pick something a player will plausibly "
            "say out loud but not in the first minute."
        )
    },
    "snitch": {
        "setup": (
            "Each Minion gets 3 not-in-play character tokens of their own as bluffs - choose "
            "them before the first night. With a Marionette in play, the Marionette gets none "
            "and the Demon gets an extra 3 instead."
        )
    },
    "magician": {
        "setup": (
            "Nothing to place, but plan the Demon Info step now: you will point at the Magician "
            "among the Minions, in an order that hides which one they are."
        )
    },
    "boffin": {
        "setup": (
            "Choose which good ability the Demon will have - a Townsfolk or Outsider ability "
            "from this script. The Demon cannot be given the Drunk, Heretic, Ogre or Politician "
            "ability."
        )
    },
    "nodashii": {
        "setup": (
            "While preparing the first night, mark the two nearest Townsfolk - one clockwise, "
            "one anti-clockwise, skipping Outsiders, Minions and Travellers - with the two "
            "Poisoned reminders."
        )
    },
    "lycanthrope": {
        "setup": "Mark one good player with the Faux Paw reminder before the first night."
    },
    "fortuneteller": {
        "setup": (
            "Before the game, secretly choose a good player as the Red Herring and mark them "
            "with the Red Herring reminder. They register as the Demon to the Fortune Teller "
            "for the whole game."
        )
    },
    "steward": {"setup": "Put the Know reminder by any good character token before night 1."},
    "knight": {"setup": "Put the two Know reminders by two good character tokens."},
    "noble": {
        "setup": (
            "Put the three Know reminders by three players, exactly one of whom is evil."
        )
    },
    "bountyhunter": {
        "setup": (
            "One Townsfolk is evil: pick them and turn their character token upside down. Put "
            "the Know reminder by an evil player."
        )
    },
    "gossip": {
        "day": (
            "The Gossip makes ONE public statement each day. Record what they said and whether "
            "it is true - you need that at night, and you need it even on days when the Gossip "
            "is not the one who acts. Put the Gossip's Dead reminder in the centre of the "
            "grimoire while the statement is live. At night, only the Gossip's state AT THAT "
            "MOMENT matters."
        )
    },
    "juggler": {
        "day": (
            "On their FIRST day only, the Juggler may publicly guess up to 5 players as "
            "characters. Mark one Correct reminder per correct guess - there are five - and "
            "keep them until the next night, when the count is shown."
        )
    },
    "cerenovus": {
        "day": (
            "Watch the player marked Mad. If they do not make a convincing effort to be the "
            "character they were told, you may execute them. Declare it publicly; it counts as "
            "the day's one execution, so if it happens first the day ends immediately."
        )
    },
    "harpy": {
        "day": (
            "Watch the player marked Mad. If they are not mad that the player marked 2nd is "
            "evil, you may kill one or both of them - during the DAY, not at night."
        )
    },
    "damsel": {
        "day": (
            "The evil team has ONE guess in total. The first time any Minion publicly guesses a "
            "player as the Damsel, mark Guess Used. If the guess is right, evil wins; if it is "
            "wrong, they never get another."
        )
    },
    "pixie": {
        "day": (
            "The Pixie should be publicly mad that they are the character you showed them. If "
            "they are not, you may execute them."
        )
    },
    "mutant": {
        "day": (
            "If the Mutant is 'mad' about being an Outsider and breaks that madness - saying or "
            "strongly implying they are an Outsider - you MAY execute them immediately. It "
            "counts as the day's one execution."
        )
    },
    "amnesiac": {
        "day": (
            "Each day the Amnesiac may privately guess what their ability is. Answer cold, "
            "warm, hot, or 'bingo' if it is spot on; different wording of the right ability "
            "still counts as correct."
        )
    },
    "wizard": {
        "day": (
            "The clue about a granted wish is declared PUBLICLY. A declined wish does not spend "
            "the ability."
        )
    },
}

# ---------------------------------------------------------------------------
# 8. Entries that do not exist at all (data-accuracy §5.3 — 55 characters,
#    plus the 10 added by the official data).
# ---------------------------------------------------------------------------


def night(text, shows=None):
    return {"instructions": text, "shows": shows or []}


ENTRIES = {
    # ---- Trouble Brewing -------------------------------------------------
    "mayor": {
        "day": night(
            "If only 3 players are alive at the end of the day and NO execution happened, the "
            "Mayor's team wins - Travellers count towards that 3. Prompt the table before the "
            "last vote so nobody executes by accident."
        ),
        "reference": night(
            "If the Mayor would die at night, you may choose that another player dies instead. "
            "The Mayor is not told. Neither effect works while the Mayor is drunk or poisoned."
        ),
    },
    "slayer": {
        "day": night(
            "Once per game the Slayer may PUBLICLY choose a player. Give the group a minute to "
            "discuss first, then act like you are fiddling with tokens in the grimoire so the "
            "pause tells nobody anything. If the chosen player is the Demon, they die "
            "immediately; otherwise nothing happens. Either way the ability is spent - mark the "
            "Slayer with No Ability. A drunk or poisoned Slayer kills nobody.",
            [
                {"label": "Nothing happens", "kind": "message", "text": "NOTHING HAPPENS.", "token": ""},
                {"label": "They die", "kind": "message", "text": "THIS PLAYER DIES.", "token": ""},
            ],
        )
    },
    "soldier": {
        "reference": night(
            "The Soldier cannot be killed by the DEMON. Other kills - execution, Assassin, "
            "Gossip, Witch, Gunslinger - still work. A drunk or poisoned Soldier is not safe."
        )
    },
    "virgin": {
        "day": night(
            "The FIRST time the Virgin is nominated, if the nominator is a Townsfolk, that "
            "nominator is executed immediately - even though nobody voted, and even if they do "
            "not die. It counts as the day's one execution, so the day ends there. The ability "
            "is spent whether or not anyone was executed: mark No Ability. A drunk or poisoned "
            "Virgin does nothing, but still spends it. A Spy or Recluse nominating may register "
            "as a Townsfolk - your ruling."
        )
    },
    "drunk": {
        "setup": night(
            "The Drunk is set up before the first night: choose the seat, take their Townsfolk "
            "token out of the bag and hand it to them anyway, and put the Is The Drunk reminder "
            "on that seat. They believe they are that Townsfolk and get its (false) information "
            "all game. Do not add the Drunk to the bag."
        ),
        "reference": night(
            "The Drunk is an Outsider who thinks they are a Townsfolk. They have no ability, "
            "and every piece of information you give them may be false."
        ),
    },
    "recluse": {
        "reference": night(
            "The Recluse MIGHT register as evil, and as a Minion or Demon, to any ability, at "
            "any time - and might not. Decide per question, not once for the game. A drunk or "
            "poisoned Recluse always registers as themselves."
        )
    },
    "saint": {
        "reference": night(
            "If the Saint is EXECUTED, good loses immediately. Any other death is ordinary. A "
            "drunk or poisoned Saint does not end the game."
        )
    },
    "baron": {
        "setup": night("[+2 Outsiders] - two Townsfolk slots in the bag become Outsiders.")
    },
    "beggar": {
        "day": night(
            "The Beggar must hold a vote token to vote. A dead player may give theirs away; "
            "when they do, tell the Beggar that player's alignment."
        ),
        "reference": night("The Beggar is always sober and healthy - nothing can impair them."),
    },
    "gunslinger": {
        "day": night(
            "After the FIRST vote is tallied each day, the Gunslinger may choose a player who "
            "voted on that nomination: that player dies. The shot is blocked by a sober Sailor, "
            "the Tea Lady and the Fool - it is not a Demon kill, so the Monk, Soldier and "
            "Devil's Advocate do not stop it."
        )
    },
    "scapegoat": {
        "day": night(
            "If a player of the Scapegoat's own alignment is executed, you may execute the "
            "Scapegoat instead - they die and the other player lives. It still counts as the "
            "day's one execution."
        )
    },
    # ---- Bad Moon Rising -------------------------------------------------
    "fool": {
        "reference": night(
            "The first time the Fool would die, they do not - by any cause, including "
            "execution. Announce that the player was killed or executed but remains alive, and "
            "do not say why. Then mark the Fool with No Ability. A drunk or poisoned Fool dies "
            "normally."
        )
    },
    "minstrel": {
        "day": night(
            "When a MINION dies by execution, every other player except Travellers is drunk "
            "until dusk TOMORROW - place the Everyone Is Drunk reminder and keep it through one "
            "dusk, expiring at the next. Announce it: 'everyone except the Minstrel is drunk'. "
            "If the Minstrel dies mid-effect, decide whether the drunkenness continues and say "
            "so."
        )
    },
    "pacifist": {
        "day": night(
            "When a GOOD player is executed, you may choose that they do not die. Announce that "
            "the player was executed but remains alive, without saying why. It still counts as "
            "the day's execution."
        )
    },
    "tealady": {
        "reference": night(
            "While both of the Tea Lady's nearest ALIVE neighbours are good, neither of them "
            "can die - by any cause, execution included. Keep a Cannot Die reminder on each of "
            "them (there are two) and move them as the neighbours change. A drunk or poisoned "
            "Tea Lady protects nobody."
        )
    },
    "goon": {
        "reference": night(
            "The FIRST player each night to choose the Goon with their ability becomes drunk "
            "until dusk, and the Goon turns to that player's alignment. Place the Goon's Drunk "
            "reminder on the chooser and flip the Goon's token if the alignment changed."
        )
    },
    "mastermind": {
        "day": night(
            "If the DEMON is executed and that would end the game, play one more day instead. "
            "If good executes another player on that extra day, evil wins; if they do not, good "
            "wins. Announce that the game continues."
        )
    },
    "bishop": {
        "day": night(
            "Only the STORYTELLER may nominate, and you must nominate at least 1 player of the "
            "opposite alignment to the current nominee each day. Use the Nominate Good and "
            "Nominate Evil reminders to track what you owe."
        )
    },
    "judge": {
        "day": night(
            "Once per game, if ANOTHER player made the nomination, the Judge may publicly force "
            "the current execution to pass or fail - announce the outcome regardless of the "
            "tally, then mark the Judge with No Ability."
        )
    },
    "matron": {
        "day": night(
            "The Matron may make up to 3 swaps of neighbouring players' seats each day, and "
            "private conversations of more than 3 players are not allowed. Enforce it lightly - "
            "a nudge works better than a penalty."
        )
    },
    "voudon": {
        "day": night(
            "While the Voudon is alive, only the Voudon and DEAD players may vote, and they do "
            "not need a vote token. A 50% majority is not required - a single vote can execute."
        ),
        "reference": night(
            "Living players other than the Voudon simply cannot vote. Snapshot the vote rules "
            "when a nomination is made: a Voudon exiled mid-day must not rewrite a tally that "
            "already happened."
        ),
    },
    # ---- Sects & Violets --------------------------------------------------
    "artist": {
        "day": night(
            "Once per game the Artist may privately ask you ANY yes/no question. Answer "
            "truthfully unless they are drunk or poisoned (then you may lie) or a sober "
            "Vortox is alive (then you must). Take them somewhere private, answer, and mark "
            "them with No Ability."
        )
    },
    "savant": {
        "day": night(
            "Each day the Savant may privately visit you, and you tell them TWO things: one "
            "true, one false. Write both down before they arrive. If the Savant is drunk or "
            "poisoned, both may be false."
        )
    },
    "klutz": {
        "day": night(
            "As soon as the Klutz learns they are dead, they must PUBLICLY choose a living "
            "player. If that player is evil, good loses immediately. A drunk or poisoned Klutz "
            "does not end the game - but still has to choose."
        )
    },
    "mutant": {
        "day": night(
            "If the Mutant is 'mad' about being an Outsider and breaks that madness, you MAY "
            "execute them immediately. It counts as the day's one execution."
        )
    },
    "butcher": {
        "day": night(
            "After the first execution of the day, the Butcher may nominate again - a second "
            "nomination from the same player, which the normal one-nomination rule would "
            "forbid."
        )
    },
    "deviant": {
        "day": night(
            "If the Deviant is genuinely entertaining today, they cannot be exiled. It is your "
            "call, and the table should feel it was earned."
        )
    },
    "cannibal": {
        "day": night(
            "The Cannibal gains the ability of the most recently EXECUTED player, for as long "
            "as that is the last execution. If that player was evil, the Cannibal is poisoned "
            "instead and gets false information. Tell the Cannibal when they gain a Butler or "
            "Zealot ability, since it constrains their voting."
        )
    },
    "alsaahir": {
        "day": night(
            "Once per day, the Al-Saahir may publicly guess which players are the Minions and "
            "which is the Demon. If they name every one correctly, GOOD WINS immediately."
        )
    },
    "atheist": {
        "setup": night(
            "The bag contains NO evil characters at all - the Storyteller is the only 'evil'. "
            "Deal good characters into every seat."
        ),
        "reference": night(
            "You may break the rules, and if good has clearly worked out that there is no evil "
            "team, good wins. The Storyteller can be nominated and executed (seat id -1)."
        ),
    },
    "fisherman": {
        "day": night(
            "Once per game the Fisherman may visit you privately for advice that genuinely "
            "helps the good team win. Prepare something concrete, give it, and mark them with "
            "No Ability."
        )
    },
    "golem": {
        "day": night(
            "The Golem may nominate only ONCE all game. When they do, if the nominee is not the "
            "Demon, that player dies immediately. Mark May Not Nominate afterwards. A drunk or "
            "poisoned Golem kills nobody but still spends the nomination."
        )
    },
    "heretic": {
        "reference": night(
            "Whoever wins, LOSES, and whoever loses, wins - applied as the very last step of "
            "the win check. It works while the Heretic is dead, and is suppressed while the "
            "Heretic is drunk or poisoned."
        )
    },
    "hermit": {
        "setup": night(
            "[-0 or -1 Outsider] - decide which before filling the bag, and write it down."
        ),
        "reference": night(
            "The Hermit has ALL Outsider abilities on the script at once. Wake them at every "
            "in-play Outsider's night step and use those Outsiders' reminder tokens; if the "
            "Hermit duplicates an Outsider who is in play, use the Hermit's own 1, 2 and 3 "
            "reminders instead."
        ),
    },
    "politician": {
        "reference": night(
            "If the Politician was the player most responsible for their own team losing, they "
            "change alignment and win instead, even if dead. Decide it at the end of the game, "
            "not before."
        )
    },
    "puzzlemaster": {
        "day": night(
            "One player is drunk because of the Puzzlemaster - mark them. Once per game the "
            "Puzzlemaster may publicly guess who it is: tell them who the Demon is, but if the "
            "guess was wrong, name a wrong player instead. Mark Guess Used either way."
        )
    },
    "zealot": {
        "reference": night(
            "If 5 or more players are alive, the Zealot MUST vote on every nomination. Remind "
            "them at the table rather than penalising them."
        )
    },
    "boomdandy": {
        "day": night(
            "If the Boomdandy is executed, all but 3 players die IMMEDIATELY. Count down from "
            "10 out loud; on '1' every surviving player points at another player, and the "
            "player with the most fingers pointed at them is executed. Run it fast and loudly - "
            "the chaos is the point."
        )
    },
    "goblin": {
        "day": night(
            "If the Goblin publicly claims to be the Goblin, mark them with the Claimed "
            "reminder. If they are executed while that token is on them, EVIL WINS. The claim "
            "must be public, and a drunk or poisoned Goblin's claim does nothing."
        )
    },
    "psychopath": {
        "day": night(
            "Each day, BEFORE nominations, the Psychopath may publicly choose a player: that "
            "player dies. If the Psychopath is executed, they duel the executioner - both roll "
            "or guess a number; if the Psychopath wins, they do not die and the other player "
            "does."
        )
    },
    "gangster": {
        "day": night(
            "Once per day the Gangster may choose to kill one alive NEIGHBOUR, but only if "
            "their other alive neighbour agrees to it out loud. Both neighbours must be alive."
        )
    },
    # ---- Travellers / Experimental additions ------------------------------
    "wraith": {
        "first": night(
            "The Wraith may open their eyes at any point during the night, and wakes whenever "
            "any other evil player wakes - Minion Info, Demon Info, and every evil character's "
            "own step. Wake them alongside those steps so the evil team is never surprised by "
            "an open pair of eyes. They take no action of their own."
        ),
        "other": night(
            "Wake the Wraith whenever other evil players wake. They may open their eyes; they "
            "take no action."
        ),
    },
    "cacklejack": {
        "other": night(
            "Before dawn, choose a player who is NOT marked Not Me - the Cacklejack named a "
            "player during the day, and the target must be a different one. Wake the target, "
            "show the YOU ARE info token and their new character token, and swap their token in "
            "the grimoire.",
            [
                {
                    "label": "New character",
                    "kind": "token",
                    "text": "YOU ARE",
                    "token": "pick",
                }
            ],
        ),
        "day": night(
            "Each day the Cacklejack chooses a player and you mark them with the Not Me "
            "reminder - that player is the one who will NOT change character tonight; a "
            "different player does."
        ),
    },
    "tor": {
        "setup": night(
            "Nobody learns their character or alignment. Deal the bag as normal but hand out "
            "no tokens: keep every character token in the grimoire and tell the table what is "
            "happening."
        ),
        "first": night(
            "Skip the Minion Info and Demon Info steps entirely - nobody knows who they are, "
            "so there is nothing to reveal."
        ),
        "other": night(
            "If a player died tonight, wake them, show the YOU ARE info token and their "
            "character token, and give a thumbs up for good or a thumbs down for evil. They "
            "learn who they were only now.",
            [
                {"label": "You are", "kind": "token", "text": "YOU ARE", "token": "pick"},
                {"label": "Good", "kind": "good", "text": "YOU WERE GOOD", "token": ""},
                {"label": "Evil", "kind": "evil", "text": "YOU WERE EVIL", "token": ""},
            ],
        ),
    },
    # ---- Fabled ------------------------------------------------------------
    "angel": {
        "first": night(
            "Announce which players are protected by the Angel - the new players you chose - "
            "and mark them with the two Protected reminders."
        ),
        "setup": night(
            "Choose the new or inexperienced players the Angel protects and mark them "
            "Protected. If something bad happens to a protected player because of another "
            "player's actions, that player's team may suffer for it: place the Something Bad "
            "reminder and act on it."
        ),
        "reference": night(
            "Something bad happening to a protected player is a Storyteller judgement, and it "
            "is meant to be visible: say that the Angel is in play at the start of the game."
        ),
    },
    "buddhist": {
        "first": night("Announce which players are affected by the Buddhist."),
        "reference": night(
            "For the first 2 minutes of each day, the affected players may not talk. Call the "
            "start and the end of the silence out loud."
        ),
    },
    "djinn": {
        "setup": night(
            "Read out every jinx on this script BEFORE the game starts, whether or not the "
            "jinxed characters were dealt - reading them all is how the good team learns "
            "nothing from which ones you read. The app's Jinxes list is the script's jinxes, "
            "not the dealt ones. Write down any special rule you add so you can quote it "
            "verbatim later."
        ),
        "reference": night(
            "The Djinn's special rule is whatever the jinx says. If a jinx changes a night "
            "procedure, run the jinxed version, not the character's usual one."
        ),
    },
    "doomsayer": {
        "day": night(
            "If 4 or more players are alive, each living player may ONCE per game publicly "
            "choose a player of their own alignment: that player dies immediately. Announce "
            "the death and move on quickly."
        )
    },
    "ferryman": {
        "day": night(
            "On the FINAL day, all dead players regain their vote token. Declare the final day "
            "explicitly - the app asks you to at 3 players alive - and hand the tokens back "
            "before nominations open."
        )
    },
    "fibbin": {
        "reference": night(
            "Once per game you may give ONE good player a piece of incorrect information from "
            "any ability. Mark the Fibbin with No Ability afterwards so you remember it is "
            "spent."
        )
    },
    "fiddler": {
        "day": night(
            "Once per game, the Demon secretly chooses an opposing player. Then every player "
            "publicly chooses which of those two players they want to win, and that side wins "
            "the game. Run it as a formal vote with everyone pointing at once."
        )
    },
    "hellslibrarian": {
        "reference": night(
            "If a player talks when the Storyteller has said there must be silence, something "
            "bad happens to them - say so at the start of the game, and follow through the "
            "first time."
        )
    },
    "revolutionary": {
        "setup": night(
            "Pick 2 NEIGHBOURING players who are the same alignment, mark them with the two "
            "Aligned reminders, and announce publicly which pair it is. Decide, once, whether "
            "one of them registers falsely and mark Register Falsely? if so."
        ),
        "reference": night(
            "The pair is known to be the same alignment. Once per game one of them may register "
            "as the other alignment - that is the whole of the ability."
        ),
    },
    "sentinel": {
        "setup": night(
            "[There might be 1 extra or 1 fewer Outsider in play] - decide before you fill the "
            "bag, and do not tell anyone which way you went, or whether you used it at all."
        )
    },
    "spiritofivory": {
        "reference": night(
            "There cannot be more than 1 extra evil player - place the No More Evil reminder "
            "once that limit is reached and refuse any further conversions."
        )
    },
    "bootlegger": {
        "setup": night(
            "The Bootlegger carries this game's homebrew rules. Write them down, announce them "
            "before the game, and use the two ? reminders for anything that needs tracking."
        ),
        "reference": night("Whatever house rules you declared with the Bootlegger apply."),
    },
    "gardener": {
        "setup": night(
            "You assign ALL players' characters instead of dealing a bag. Build the whole grid "
            "deliberately - this is the Fabled that gives you total control of the setup."
        )
    },
    "deusexfiasco": {
        "reference": night(
            "At least once this game you will make a mistake, correct it, and publicly admit "
            "it. Place the Whoopsie reminder when it happens so the table can see the promise "
            "was kept."
        )
    },
    # ---- Loric --------------------------------------------------------------
    "bigwig": {
        "reference": night(
            "Each nominee chooses a player: until voting starts, only that player may speak, "
            "and they are mad that the nominee is good or they might die. Enforce the silence "
            "yourself."
        )
    },
    "godofug": {
        "reference": night(
            "One player wears the Ug hat (place the Hat reminder). While wearing it they may "
            "only speak one sound at a time, but their vote counts twice. If they fail, the hat "
            "passes to another player."
        )
    },
    "hindu": {
        "reference": night(
            "The first 4 players to die are immediately reincarnated as Travellers of the same "
            "alignment. Run the normal Traveller arrival for each of them, at the moment they "
            "die."
        )
    },
    "knaves": {
        "reference": night(
            "There are two Storytellers: one lies, one tells the truth. Once per game, at dusk, "
            "they may switch which is which - silently."
        )
    },
    "pope": {
        "setup": night(
            "There are duplicate GOOD characters in play - deal two copies of one or more good "
            "characters. Those characters may also appear among the Demon's bluffs."
        )
    },
    "ventriloquist": {
        "reference": night(
            "If a player is mad as a fresh character during their own nomination, you may "
            "decide they do not die if executed today. Mark them Mad; it expires at dusk."
        )
    },
    "zenomancer": {
        "reference": night(
            "One or more players each have a secret goal - mark them with the Goal reminders "
            "(there are three). When a player achieves their goal, tell them one piece of true "
            "information."
        )
    },
}


# ---------------------------------------------------------------------------
# 9. Wave 6C (FOLLOWUPS §"From Wave 4 registry agents").
#
#    (a) The four night-order rows `tools/app-overlay.json` adds because the
#        official sheet has none: a mid-game Widow / Ogre / Snitch and a
#        night-1 Plague Doctor death.  `regen-data.py` and `validate()` both
#        require a `first`/`other` channel exactly when the id is in the
#        matching order list, so these are not optional.
#    (b) The six characters WP7-EXP-O reported with no run-book for the
#        channel where their whole ability lives.  There is no `end` channel
#        in the schema (ARCHITECTURE §2.14 / `NightGuideEntry`), so the
#        end-of-game work goes in `day`, which is where the storyteller is
#        standing when it happens.
# ---------------------------------------------------------------------------

W6C = {
    "widow": {
        "other": night(
            "Only for a Widow who has just ENTERED PLAY - a Pit-Hag, Summoner or Kazali "
            "made one tonight. 'On your 1st night' means their first night as the Widow, "
            "so run the whole first-night step now: show them the Grimoire for as long as "
            "they need (cover anything a jinx hides - a fellow Widow's Know token, and the "
            "Demon's and Magician's tokens if a Magician is in play), let them point at a "
            "player, mark that player Poisoned, then wake the good player marked Know and "
            "show them the Widow token. A Widow who has already seen the Grimoire never "
            "sees it again.",
            [{"label": "To the Know player", "kind": "token", "text": "", "token": "self"}],
        )
    },
    "ogre": {
        "other": night(
            "Only for an Ogre who has just ENTERED PLAY. The Ogre points at a player who is "
            "NOT themself; mark that player Friend. If that player registers as evil, the "
            "Ogre becomes evil - turn the Ogre's character token upside down. The Ogre is "
            "never told which alignment they became, so give no signal either way. This "
            "works even if the Ogre is drunk or poisoned."
        )
    },
    "snitch": {
        "other": night(
            "Only when a Minion is still owed bluffs - a Snitch or a Minion entered play "
            "after the first night. Wake that Minion, show the THESE CHARACTERS ARE NOT IN "
            "PLAY token and three not-in-play character tokens of their own. The Marionette "
            "is never woken and gets nothing; the Demon gets an extra three instead."
        )
    },
    "plaguedoctor": {
        "first": night(
            "Almost always skipped: nobody has died yet on the first night. It is here for "
            "the one case that can happen - a Plague Doctor killed before the first night "
            "ended (an Angel's 'something bad', a storyteller death, a house rule). If the "
            "Plague Doctor is dead and you have not taken a Minion ability yet, choose one "
            "now and keep it secret forever. A Plague Doctor who died drunk or poisoned "
            "gives you nothing, even if they are cured later."
        )
    },
    "hermit": {
        "day": night(
            "The Hermit holds every Outsider ability on the script, including the ones that "
            "act in daylight: the Golem's single nomination, the Puzzlemaster's guess, the "
            "Damsel's guess, the Klutz's choice on death, the Politician's end-of-game "
            "switch and the Zealot's obligation to vote. Run each of them for the Hermit as "
            "if they were that Outsider, using the Hermit's own 1, 2 and 3 reminders where "
            "the real Outsider is also in play. A once-per-game ability the Hermit spends "
            "is spent for the Hermit only."
        )
    },
    "golem": {
        "reference": night(
            "The Golem's kill is NOT an execution: it does not use up the day's execution, "
            "and the Saint, Devil's Advocate, Fearmonger and Virgin all sit it out. The "
            "nomination itself still happens and is still voted on."
        )
    },
    "puzzlemaster": {
        "setup": night(
            "Before the first night, choose which player is drunk because of the "
            "Puzzlemaster and mark them with the Puzzlemaster's Drunk reminder. It may be "
            "the Puzzlemaster themself. They are drunk from now on and stay drunk even "
            "after the Puzzlemaster dies. Tell nobody."
        )
    },
    "politician": {
        "day": night(
            "At the END of the game, before you announce who won, decide whether the "
            "Politician was the player most responsible for their own team losing. If they "
            "were, they change alignment and win with the other team instead - even if they "
            "are dead, and even though nobody was told. Decide it then, not earlier, and "
            "say so out loud when you announce the result."
        )
    },
    "zealot": {
        "day": night(
            "While 5 or more players are alive, the Zealot MUST raise their hand on every "
            "nomination. Watch for it on each vote and remind them at the table rather than "
            "penalising them. The obligation holds even while the Zealot is drunk or "
            "poisoned - it is a rule about the player, not an ability."
        )
    },
    "heretic": {
        "day": night(
            "Apply the Heretic as the VERY LAST step of the win check, after every other "
            "ability has resolved: whoever has just won, loses, and whoever has just lost, "
            "wins. It works while the Heretic is dead and is suppressed only while the "
            "Heretic is drunk or poisoned. Work the answer out before you announce anything."
        )
    },
}


# ---------------------------------------------------------------------------
# Application
# ---------------------------------------------------------------------------


def apply(guide, problems):
    for cid, entry in MARKERS.items():
        guide.setdefault(cid, {})
        for channel, value in entry.items():
            guide[cid][channel] = value

    for cid, src, dst in MOVES:
        entry = guide.get(cid, {})
        if src in entry:
            entry.setdefault(dst, entry.pop(src))

    for cid, channel, old, new in SUBS:
        node = guide.get(cid, {}).get(channel)
        if node is None:
            problems.append(f"SUB target missing: {cid}.{channel}")
            continue
        text = node["instructions"]
        if new in text:
            continue  # already applied
        if text.count(old) != 1:
            problems.append(
                f"SUB anchor found {text.count(old)}x in {cid}.{channel}: {old[:60]!r}"
            )
            continue
        node["instructions"] = text.replace(old, new)

    for cid, channel, text in APPENDS:
        node = guide.get(cid, {}).get(channel)
        if node is None:
            problems.append(f"APPEND target missing: {cid}.{channel}")
            continue
        if text in node["instructions"]:
            continue
        node["instructions"] = node["instructions"].rstrip() + " " + text

    for (cid, channel), value in SETS.items():
        guide.setdefault(cid, {})[channel] = value

    for cid, channel, show, mode in SHOWS:
        node = guide.get(cid, {}).get(channel)
        if node is None:
            problems.append(f"SHOW target missing: {cid}.{channel}")
            continue
        shows = node.setdefault("shows", [])
        for i, existing in enumerate(shows):
            if existing["label"] == show["label"]:
                if mode == "replace":
                    shows[i] = show
                break
        else:
            shows.append(show)

    for cid, channels in EXTRA_CHANNELS.items():
        entry = guide.setdefault(cid, {})
        for channel, text in channels.items():
            entry.setdefault(channel, {"instructions": text, "shows": []})

    for cid, channels in ENTRIES.items():
        entry = guide.setdefault(cid, {})
        for channel, value in channels.items():
            entry.setdefault(channel, value)

    for cid, channels in W6C.items():
        entry = guide.setdefault(cid, {})
        for channel, value in channels.items():
            entry.setdefault(channel, value)

    # Stable key order: characters in characters.json order, markers first.
    return guide


def validate(guide, problems):
    chars = json.load(open(os.path.join(DATA_DIR, "characters.json"), encoding="utf-8"))
    nj = json.load(open(os.path.join(DATA_DIR, "night_and_jinxes.json"), encoding="utf-8"))
    ids = [c["id"] for c in chars]
    first, other = set(nj["firstNight"]), set(nj["otherNight"])
    markers = set(MARKERS)

    for key in guide:
        if key not in ids and key not in markers:
            problems.append(f"guide key is neither a character nor a marker: {key}")
    for cid in ids:
        entry = guide.get(cid)
        if not entry or not any(entry.get(c) for c in CHANNELS):
            problems.append(f"no guide channel at all for {cid}")
            continue
        if (cid in first) != bool(entry.get("first")):
            problems.append(f"first channel mismatch for {cid}")
        if (cid in other) != bool(entry.get("other")):
            problems.append(f"other channel mismatch for {cid}")
    for key, entry in guide.items():
        for channel in CHANNELS:
            node = entry.get(channel)
            if node is None:
                continue
            if not node.get("instructions", "").strip():
                problems.append(f"blank instructions in {key}.{channel}")
            for show in node.get("shows", []):
                if show.get("kind") not in ("message", "token", "good", "evil"):
                    problems.append(f"invalid show kind in {key}.{channel}: {show.get('kind')}")
                if show.get("token", "") not in ("", "self", "pick"):
                    problems.append(f"invalid show token in {key}.{channel}: {show.get('token')}")
                if not show.get("label"):
                    problems.append(f"show without a label in {key}.{channel}")

    # The four P0 sentences must be gone.
    banned = [
        ("undertaker", "even if they did not die from it"),
        ("vortox", "no one dies, but information is still false"),
        ("shabaloth", "their once-per-game abilities remain as they were"),
        ("butler", "This still applies even if the Butler is drunk or poisoned"),
    ]
    for cid, phrase in banned:
        blob = json.dumps(guide.get(cid, {}), ensure_ascii=False)
        if phrase in blob:
            problems.append(f"P0 sentence still present in {cid}: {phrase!r}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="validate only, write nothing")
    args = parser.parse_args()

    with open(GUIDE, encoding="utf-8") as handle:
        guide = json.load(handle)

    problems = []
    guide = apply(guide, problems)

    chars = json.load(open(os.path.join(DATA_DIR, "characters.json"), encoding="utf-8"))
    order = {c["id"]: i for i, c in enumerate(chars)}
    ordered = {}
    for marker in ("DUSK", "MINION_INFO", "DEMON_INFO", "DAWN"):
        if marker in guide:
            ordered[marker] = guide[marker]
    for cid in sorted(k for k in guide if k not in ordered):
        ordered[cid] = {c: guide[cid][c] for c in CHANNELS if c in guide[cid]}
    ordered = dict(
        sorted(
            ((k, v) for k, v in ordered.items()),
            key=lambda kv: (0, list(MARKERS).index(kv[0]))
            if kv[0] in MARKERS
            else (1, order.get(kv[0], 10**6)),
        )
    )

    validate(ordered, problems)

    if not args.check:
        with open(GUIDE, "w", encoding="utf-8") as handle:
            json.dump(ordered, handle, indent=1, ensure_ascii=False)
            handle.write("\n")

    channels = sum(1 for e in ordered.values() for c in CHANNELS if e.get(c))
    print(f"guide entries : {len(ordered)} ({len(MARKERS)} markers + {len(ordered) - len(MARKERS)} characters)")
    print(f"channels      : {channels}")
    for channel in CHANNELS:
        print(f"  {channel:<10s}: {sum(1 for e in ordered.values() if e.get(channel))}")
    print(f"show cards    : {sum(len(e[c].get('shows', [])) for e in ordered.values() for c in CHANNELS if e.get(c))}")

    if problems:
        print(f"\n{len(problems)} problem(s):", file=sys.stderr)
        for p in problems:
            print(f"  - {p}", file=sys.stderr)
        return 1
    print("\nall checks passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
