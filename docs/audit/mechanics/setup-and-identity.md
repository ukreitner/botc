# Setup & Identity (mechanics) — the bag, the bluffs, who-is-what, and every "show them their new identity" moment

Scope: `Setup.kt`, `GameActions.validateBag/validateSetupState/deal/randomBag/setBluffs/suggestBluffs/assignCharacter/swapCharacters/starPass`,
`SetupScreen.kt`, `BluffsSheet.kt`, `RevealFlow.kt`, `ShowCards.kt`, the `Player.shownCharacterId`/`nightRoleId` model,
and the `MINION_INFO`/`DEMON_INFO` markers in `NightOrder.kt`.

This is the **cross-cutting model** the per-character audits keep asking for. It does not restate
their findings. Where a character doc already owns a defect it is cited as
`(see characters/<id>.md #N)` and only the *shared* mechanism is specified here.
Four character docs independently proposed four incompatible shapes for "this seat acts with
someone else's ability" (`philosopher.md:205-223` `actingCharacterId`, `alchemist.md:161-177`
`grantedAbilityId` + `nightSlots`, `boffin.md:167-171` `GameState.boffinGrantId` + `NightStep.abilityId`,
`cannibal.md:250` a fully derived `cannibalNightRole`). **§B below picks one and shows how all four
collapse into it.**

---

## Official rules (sources)

All quotes below were taken on **2026-08-25** either from raw wikitext
(`https://wiki.bloodontheclocktower.com/api.php?action=parse&page=<Page>&prop=wikitext`) or from
the Pandemonium Institute release data
(`https://raw.githubusercontent.com/ThePandemoniumInstitute/botc-release/main/resources/data/{roles,jinxes,nightsheet}.json`),
because the rendered pages lose the `'''TOKEN NAME'''` markup that identifies physical info tokens.

General rules pages: [Setup](https://wiki.bloodontheclocktower.com/Setup) ·
[Glossary](https://wiki.bloodontheclocktower.com/Glossary) ·
[Abilities](https://wiki.bloodontheclocktower.com/Abilities) ·
[Character Types](https://wiki.bloodontheclocktower.com/Character_Types) ·
[Travellers](https://wiki.bloodontheclocktower.com/Travellers) ·
[Storyteller Advice](https://wiki.bloodontheclocktower.com/Storyteller_Advice) ·
[Teensyville](https://wiki.bloodontheclocktower.com/Teensyville) ·
[Fabled](https://wiki.bloodontheclocktower.com/Fabled) ·
[Changelog](https://wiki.bloodontheclocktower.com/Changelog).
Character pages: `/Lunatic`, `/Snitch`, `/Marionette`, `/Magician`, `/Poppy_Grower`, `/Boffin`,
`/Alchemist`, `/Philosopher`, `/Cannibal`, `/Kazali`, `/Summoner`, `/Lil'_Monsta`, `/Legion`,
`/Lord_of_Typhon`, `/Atheist`, `/Riot`, `/Huntsman`, `/Damsel`, `/Village_Idiot`, `/Hermit`,
`/Sentinel`, `/Bootlegger`, `/Spirit_of_Ivory`, `/Xaan`, `/Balloonist`, `/Fang_Gu`, `/Godfather`,
`/Baron`, `/Heretic`, `/Drunk`, `/Evil_Twin`, `/Pixie`, `/Puzzlemaster`, `/Fortune_Teller`,
`/Widow`, `/Grandmother`, `/Apprentice`, `/Bone_Collector`, `/Plague_Doctor`, `/Bounty_Hunter`,
`/Scarlet_Woman`, `/Imp`, `/Pit-Hag`.

> **There is no `/Bluff` page** (HTTP 404), and the **character-distribution table is not on the
> wiki in text form** — the Setup page only says "as listed on the setup sheet". The table below is
> reconstructed from the physical Setup sheet and corroborated by official examples
> (Legion: "for a 10-player game, there are roughly seven Legion and three good players";
> Cult Leader: "In a 9 player game, there are 7 good players and only 2 evil players";
> Lord of Typhon: "There are ten players, and two Outsiders in play, due to the Lord of Typhon
> ability" and "There are 15 players, but zero Outsiders in play, since the Lord of Typhon removed
> one Outsider").

### 1. The setup procedure

Sources: <https://wiki.bloodontheclocktower.com/Setup>, <https://wiki.bloodontheclocktower.com/Night_Order>,
plus the per-character How-to-Run sections quoted below.

Official distribution (5–15 players; 16+ are Travellers, who are **outside** the table):

| Players | TF | OUT | MIN | DEM |
|---|---|---|---|---|
| 5 | 3 | 0 | 1 | 1 |
| 6 | 3 | 1 | 1 | 1 |
| 7 | 5 | 0 | 1 | 1 |
| 8 | 5 | 1 | 1 | 1 |
| 9 | 5 | 2 | 1 | 1 |
| 10 | 7 | 0 | 2 | 1 |
| 11 | 7 | 1 | 2 | 1 |
| 12 | 7 | 2 | 2 | 1 |
| 13 | 9 | 0 | 3 | 1 |
| 14 | 9 | 1 | 3 | 1 |
| 15 | 9 | 2 | 3 | 1 |

Order of operations: choose script → count players → take the base distribution → **apply every
bracketed setup modifier of every character you are putting in the bag** (they change *which tokens
go in the bag*, before anyone draws) → put the tokens in the bag → players draw and look → the
Storyteller collects the tokens and builds the grimoire → *then* the setup-only Storyteller choices
(red herring, Grandchild, Twin, KNOW, DRUNK, the Drunk's/Marionette's believed token, bluffs) →
first night.

Minion Info and Demon Info exist only at **7+ players**. In a Teensyville (5–6) the evil team does
not learn each other and the Demon gets **no bluffs**.

### 2. Bluffs — every recipient, and the exact rule for each

The one authoritative definition is the **Glossary**
(<https://wiki.bloodontheclocktower.com/Glossary>) — there is no `/Bluff` page:

> **Demon info:** "…the information that the Demon receives on the first night **if there are 7 or
> more players**. The Demon learns which players are the Minions, and learns **3 good characters
> that are not in play** to help them bluff."
>
> **Not in play:** "A character that does not exist in the current game, **but is on the character
> sheet**."

| Recipient | Rule | Count | May include in-play? | Where it is shown |
|---|---|---|---|---|
| **Demon** | Glossary: "3 good characters that are not in play" | 3 | No | `DEMON_INFO` |
| **Each Minion (Snitch)** | Snitch How to Run: *"wake a Minion. Show the '''THESE CHARACTERS ARE NOT IN PLAY''' info token, then show three not-in-play character tokens… Repeat this process until all Minions have learnt three not-in-play characters."* Summary: *"These characters may be the same three that the Demon learns, or different characters. Each Minion may learn different characters to each other."* | 3 each, **independent** | No | `snitch` step (after MINION INFO, before DEMON INFO) |
| **Lunatic** | Lunatic How to Run: *"Show **any three good character tokens** as bluffs. (These can even be characters that are in play.)"* | 3 | **Yes** | `lunatic` step |
| **Summoner** | *"You get 3 bluffs"*; `[No Demon]`, so there is no DEMON INFO at all. Alchemist jinx: *"The Alchemist-Summoner does not get bluffs"* | 3 | No | `summoner` step |
| **Demon, with Poppy Grower** | How to Run: *"wake the Demon. Show the '''THESE CHARACTERS ARE NOT IN PLAY''' info token, then **any three good character tokens that are not in play**… **Do not do the Minion Info and Demon Info steps.**"* | 3 | No | `poppygrower` step |
| **A Legion player, with a Snitch** | Legion page: *"Since Legion can potentially get 0 or 3 Bluffs as a whole (**possibly 6 bluffs if there is a Snitch in play**)…"* — Legion registers as a Minion too, so one seat can receive **two independent sets** | 3 + 3 | No | `DEMON_INFO` + `snitch` |
| **Lil' Monsta** | How to Run: *"On the first night, **skip the MINION INFO and DEMON INFO steps**."* → **nobody gets bluffs** | 0 | — | — |
| **Legion** | Summary: *"The Storyteller can decide **not** to give Legion players bluffs."* | 0–3 | No | `DEMON_INFO` |
| **Atheist** | `[No evil characters]` — no recipient exists | 0 | — | — |
| **Teensyville (<7)** | Glossary's "if there are 7 or more players" clause | 0 | — | — |
| **Evil Traveller** | Travellers / Character Types, verbatim: *"If they are evil, they learn who the Demon is; they do not learn any additional evil characters **or receive any bluffs**."* | 0 | — | — |
| **Marionette** | *"On the first night, the Marionette does not wake to learn the other evil players"*; *"not woken due to character abilities that would confirm that they are a Minion eg. **Snitch**, Preacher, Lil' Monsta, Poppy Grower, Hatter, Damsel"* | 0 | — | — |

> ⚠️ **The "Demon gets 3 extra bluffs" jinx is retired.** `night_and_jinxes.json` ships
> *"marionette × snitch: The Marionette does not learn 3 not in-play characters. The Demon learns
> an extra 3 instead."* A raw-wikitext sweep on 2026-08-25 found **no Snitch jinx section at all**,
> no Snitch entry on the Marionette page (which lists 9 jinxes), and **no `snitch` key anywhere in
> the official `jinxes.json`** (ThePandemoniumInstitute/botc-release). The current ruling is the
> Marionette almanac bullet quoted above — the Marionette simply is not woken. Treat the six-bluff
> case as **stale repo data** (a `mechanics/data-accuracy.md` item), but keep the model's per-set
> `size` configurable: the Legion + Snitch case above genuinely puts two sets on one seat, and
> homebrew/Bootlegger scripts will want it.

Candidate constraints for a normal (non-Lunatic) set: a character **on the script**, **good**
(Townsfolk or Outsider — the Glossary and the Poppy Grower both say "good"), **not in play**.
Note a real inconsistency in the official text: the Snitch and Summoner How-to-Run sections say only
"3 not-in-play character**s**", omitting "good". Follow the Glossary and require good.

Characters that are *in use but not in play* are explicitly legal:

- Boffin: *"The not-in-play character may be 1 of the Demon's 3 bluffs."*
- Kazali: *"The Storyteller can give the Minions' original good characters as bluffs to the Demon,
  since they are not in play."*
- Hermit: *"The Hermit may remove the Hermit from play during setup… If this happens, **the Hermit
  may still be a bluff given to the Demon**."*
- Drunk / Marionette believed tokens are genuinely not in play, so they are legal — but two players
  will then claim the same character, which is a Storyteller decision, not an accident
  (`drunk.md` #7).

Storyteller Advice adds one workflow rule the UI should support:

> "Waking the Demon and the Minions together… **You will also need to put the Minions back to sleep
> before showing the Demon the three not-in-play character tokens as bluffs.** … The Minions and
> Demon are normally woken separately to allow for characters such as the Lunatic and Magician to
> function, and to ensure that the Minions do not see the Demon's character bluffs."

### 3. Characters whose setup is *not* "put a token in the bag"

Quoted How-to-Run text, with the bag consequence spelled out.

| Character | Bracket | Official How to Run (quoted) | Bag consequence at 10 players (base 7/0/2/1) |
|---|---|---|---|
| **Kazali** | `[You choose which players are which Minions. -? to +? Outsiders]` | *"remove all Minion tokens and add Townsfolk or Outsider tokens"*; night 1: *"Repeat until the normal number of Minions exist."* | **9 good + Kazali**, `minions = 0`, outsiders free in `0..2` |
| **Lil' Monsta** | `[+1 Minion]` | *"During setup, remove Lil' Monsta and add a Minion token. On the first night, skip the MINION INFO and DEMON INFO steps."* | **7/0/3/0** and **no `lilmonsta` token in the bag** |
| **Lord of Typhon** | `[Evil characters are in a line. You are in the middle. +1 Minion. -? to +? Outsiders]` | *"remove all Minion tokens and add Townsfolk or Outsider tokens"*; night 1 the two neighbours are converted | `minions = 0` in the bag; 3 Minions created night 1; evil in a contiguous line, Typhon strictly interior |
| **Summoner** | `[No Demon]` | *"During the setup phase, remove the Demon and add a Townsfolk."* | **8/0/2/0** ✅ (the app already does this) |
| **Marionette** | `[You neighbor the Demon]` | *"remove the Marionette token and add any Townsfolk token. **If there are three Minions in play, remove another Minion token and add another Townsfolk token. During the first night, swap a good player's character token with a not-in-play Minion character token… This player is now an evil Minion. (This ensures that only one Minion token is in the bag, so at least one good player will neighbor the Demon.)**"*; then night 1 mark a good neighbour of the Demon IS THE MARIONETTE | Official bag 8/0/1/1 → final grimoire 7/0/2/1. The app's representation (Marionette in the bag + `shownCharacterId`) reaches the **same final grimoire** and is acceptable — **except in 3-Minion (13–15 player) games**, see below |
| **Drunk** | *(no bracket in official data)* | *"remove the Drunk token and add a Townsfolk character token… **Put the swapped Townsfolk character token in the bag, not the Drunk character token.**"* then *"while preparing the first night, put the '''IS THE DRUNK''' reminder token by any Townsfolk character token"* | no count change; same "final grimoire is what matters" argument as the Marionette |
| **Atheist** | `[No evil characters]` | *"remove all evil character tokens and add Townsfolk or Outsider character tokens to match the player count"* | 10 good, `minions = demons = 0` |
| **Legion** | `[Most players are Legion]` | *"roughly seven Legion to three good players in a 10-player game"*; the rest "Townsfolk or Outsiders, in any combination" | `legion` × k where k > n/2; `minions = 0` |
| **Riot** | **none — the bracket is retired** | Current official text (raw wikitext + `roles.json`, 2026-08-25): *"On day 3, Minions become Riot & nominees die but nominate an alive player immediately. This must happen."* — **no bracket, no setup section.** Minions start as ordinary Minions, get ordinary Minion Info, and are converted on night 3: *"On the 3rd night, wake each Minion. Show the '''YOU ARE''' info token, then the Riot token."* | **a completely ordinary bag.** `characters.json`'s `[All Minions are Riot]` and the `TEAM_WARPING_IDS`/`DUPLICABLE` entries for `riot` are stale |
| **Baron** | `[+2 Outsiders]` | *"remove any two Townsfolk character tokens and add any two Outsider character tokens"* | 5/2/2/1 |
| **Godfather** | `[-1 or +1 Outsider]` | *"either remove one Townsfolk and add one Outsider or remove one Outsider and add one Townsfolk"* | ±1 exactly |
| **Hermit** | `[-0 or -1 Outsider]` | *"you may remove an Outsider token and add a Townsfolk token"* | 0 or −1 |
| **Balloonist** | `[+0 or +1 Outsider]` | *"During setup, you may add an Outsider."* | 0 or +1 |
| **Village Idiot** | `[+0 to +2 Village Idiots. 1 of the extras is drunk]` | *"replace zero, one or two Townsfolk tokens with Village Idiot tokens. While preparing the first night, mark one Village Idiot with the DRUNK reminder."* — *"If there is only one Village Idiot in play, they are sober."* | 1–3 copies, all in the Townsfolk count |
| **Huntsman** | `[+the Damsel]` | Damsel joins as an Outsider | +1 Outsider, `damsel` required |
| **Choirboy** | `[+the King]` | King joins as a Townsfolk | no count change, `king` required |
| **Xaan** | `[X Outsiders]` | *"add or remove any number of Outsider tokens, including zero"*; **X is frozen at setup**; *"This overrides other characters that add or remove Outsiders, such as the Baron."* | outsiders = X, **and X must be stored** |
| **Bounty Hunter** | `[1 Townsfolk is evil]` | *"turn one Townsfolk character token upside down, to represent that they are evil. Mark one evil player with the KNOW reminder."* | no count change; **one Townsfolk seat's alignment is flipped** |
| **Boffin** | *(no bracket)* | *"If the Demon has an ability that modifies the setup, such as a Choirboy, these changes are made during setup, as normal."* | the **granted** character's bracket applies to the bag |
| **Alchemist** | *(no bracket)* | granted Minion is not in play, but an Alchemist-Baron still changes the bag | the **granted** Minion's bracket applies to the bag |
| **Sentinel** (Fabled) | — | *"There might be 1 extra or 1 fewer Outsider in play."* | ±1 Outsider relaxation |
| **Spirit of Ivory** (Fabled) | — | *"There can't be more than 1 extra evil player."* | caps mid-game alignment flips |
| **Bootlegger** (Fabled) | — | *"This script has homebrew characters or rules."* | disables strict bag validation by design |

**The Marionette's 3-Minion clause** deserves a call-out, because `marionette.md:34-41` saw this
paragraph, judged it contradictory and said *"Do not implement it."* A raw-wikitext fetch on
2026-08-25 confirms it is genuinely part of the Marionette How to Run, and the parenthetical
explains it: in a **13–15 player game (3 Minions)** you remove *two* Minion tokens and add two
Townsfolk, so only **one** Minion token is in the bag and a good player is guaranteed to neighbour
the Demon. On night 1 you then (a) convert one good player into the second real Minion — YOU ARE +
Minion token + thumbs down, they know they are evil — and (b) separately mark a good neighbour of
the Demon as the Marionette. Two different conversions, one evil-and-told, one evil-and-not-told.
`marionette.md`'s "do not implement" applies only to reading that paragraph as describing the
Marionette; the paragraph itself is real.

**Uncertain — flagged, not guessed:**

- **Legion's count.** The wiki gives a *recommendation* (*"roughly seven Legion to three good
  players in a 10-player game"*, *"the players that are not Legion may be Townsfolk or Outsiders,
  in any combination"*), not a hard bound. The `BagShape` below must therefore be **advisory**,
  never blocking.
- **The Drunk's bracket.** `drunk.md` #9 asks for `[+1 Outsider]` to be added to
  `characters.json`. Both official sources (wiki Summary and `roles.json`) have **no bracket**, and
  the arithmetic is already correct. Adding `[+1 Outsider]` would double-count. The right fix is
  display-only text; do not invent a delta.
- **The Drunk's believed character's setup bracket.** Explicit jinxes say the *Marionette's*
  believed Huntsman adds a Damsel and their believed Balloonist may add an Outsider. No jinx or
  wiki sentence says whether the same holds for the Drunk. Surface it as an advisory question at
  setup rather than enforcing either answer.

**Resolved since drafting:**

- **The Lunatic's fake-Minion count** — the wiki is explicit: *"point to a number of players
  equaling the number of Minions in play. (These can be any players, whether or not they are
  Minions.)"* A Marionette *is* a Minion in play, so it counts. `lunatic.md`'s proposal to exclude
  it is wrong; the exclusion at `NightOrder.kt:62` is about the **real** Demon's Minion list, not
  the Lunatic's fake one.
- **Riot** — no bracket, no setup effect (see the table above).

Marionette jinxes that reach back into the bag:
*"If the Marionette thinks that they are the Huntsman, the Damsel was added during setup."* and
*"If the Marionette thinks that they are the Balloonist, an Outsider might have been added during
setup."* → **the Marionette's believed character's setup bracket applies too.**

Marionette neighbour jinxes:
*"Kazali / Lil' Monsta / Summoner — If there would be a Marionette in play, they enter play after
the Demon & must start as their neighbor."* and *"summoner × marionette: The Marionette neighbors
the Summoner, not the Demon."* and *"marionette × lilmonsta: The Marionette neighbors a Minion, not
the Demon."*

### 4. What a player is SHOWN vs what they ARE vs what they ACT AS

Three different questions, answered by three different fields. The rules are explicit that they can
all differ at once:

- **IS** (registration): team, alignment, win conditions, and what *other* characters learn about
  this seat (Undertaker, Ravenkeeper, Librarian, Investigator, Virgin, Dreamer, Balloonist,
  Fortune Teller, `WinCheck`). The Drunk registers as the Drunk. The Marionette
  *"registers as evil, and as a Minion."*
- **SHOWN**: the token this player has physically seen. Drunk → a Townsfolk. Marionette → a good
  character. Lunatic → a Demon. **And, officially, the real Demon in a Lunatic game → the Lunatic
  token**: Lunatic How to Run, *"put the Lunatic and Demon tokens in the bag. Once all tokens have
  been returned to you, swap the positions of the Lunatic and Demon tokens in the Grimoire"*, then
  on night 1 *"wake the Demon. Show them the YOU ARE info token, then their Demon token."*
- **ACTS AS**: which ability's rules to run tonight, at which night-order slot, and whether the
  result is real:
  - Drunk → the believed Townsfolk, at that Townsfolk's slot, results always false.
  - Marionette → the believed good character, at that character's slot, results always false
    (*"It is just as if this player is the Drunk"*), and **never at MINION INFO**.
  - Lunatic → the **believed Demon's** rules (target count, first-night-or-not, self-targeting),
    but at the `lunatic` slot, results always fake.
  - Philosopher → the gained good character's ability, at that character's slot; the Philosopher
    *does not become* that character.
  - Alchemist → a not-in-play Minion ability; *"They are still a good Townsfolk. They register as
    good and as the Alchemist."*
  - Boffin's Demon → **both** their Demon ability **and** the granted good ability
    (*"The Demon also wakes at night at the time that the good character would normally wake"*),
    the granted one working *"even if drunk or poisoned"*, and failing only when the **Boffin** is
    drunk/poisoned/dead. *"If a new Demon is created… this new Demon has an ability from the
    Boffin."*
  - Cannibal → the ability of the most recently executed player.
  - Pixie → the marked Townsfolk's ability once they die and madness was kept.
  - Apprentice → a Townsfolk ability (if good) or Minion ability (if evil), from night 1.
  - Bone Collector → grants an ability **to another (dead) seat** until dusk.
  - Hermit → **all** Outsider abilities on the script at once.
  - Plague Doctor → the granted Minion ability is held by the **Storyteller**, not a seat.
    How to Run, verbatim: *"place a Minion character token in the center of the left side of the
    Grimoire and mark this with the Plague Doctor's '''STORYTELLER ABILITY''' reminder. Or, mark an
    in-play Minion with the '''STORYTELLER ABILITY''' reminder. **If applicable, add a night token
    to the night sheet.** … When this Minion would normally act, the relevant choices are made by
    the Storyteller."* — i.e. a **seatless night row at that Minion's own night-order position**.
    Summary: *"Nothing else changes for the Storyteller – they don't become evil, they don't become
    a player, they are not a legitimate player to be targeted by other abilities."* Nine jinxes
    redirect the gain instead (Baron → two players become Outsiders; Boomdandy/Evil Twin/Marionette
    → a player becomes that character; Fearmonger/Goblin/Scarlet Woman/Spy/Wraith → *"a Minion
    gains it, and learns this"*).
  - Evil **Traveller** → learns the Demon and nothing else. Travellers / Character Types, verbatim:
    *"If they are evil, they learn who the Demon is; they do not learn any additional evil
    characters or receive any bluffs."*

### 5. Mid-game identity changes

Every one of these needs (a) a grimoire change, (b) a "show the player their new identity" moment,
(c) a decision about re-running first-night info, (d) reminder cleanup, (e) an alignment ruling:

| Source | Rule |
|---|---|
| Pit-Hag | new character only *"if not in play"*; **alignment does not change** unless the ability says so |
| Barber | Demon swaps two players' character tokens; both are woken and shown YOU ARE |
| Engineer | chooses which Minions or which Demon are in play; each changed player is woken and shown YOU ARE |
| Hatter | Minions/Demon each choose new characters of their own type, no duplicates |
| Snake Charmer | characters **and alignments** swap; the new Snake Charmer is poisoned |
| Imp star pass | *"choose an alive Minion and replace their character token with a spare Imp token"*; *"This new Imp does not act that same night"*; Scarlet Woman **must** catch it at 5+ alive |
| Fang Gu | the chosen Outsider becomes an evil Fang Gu and the Fang Gu dies |
| Scarlet Woman | becomes the Demon **that died**, told that night |
| Kazali | creates the normal number of Minions on night 1 |
| Summoner | creates one evil Demon on night 3; *"The newly created Demon acts on the same night that it is created."* |
| Lord of Typhon | converts both neighbours to Minions on night 1 |
| Huntsman | the Damsel becomes a not-in-play Townsfolk |
| Bounty Hunter / Ogre / Cult Leader / Mezepheles / Bone Collector | **alignment or ability changes with no character change** |
| Professor | resurrection → the user's own complaint: *"When Professor brings someone back it should remind in the morning and rerun the 1st night for that"* |

---

## What the app does today

### Bluffs — one global list of three, Demon-only

- `GameState.demonBluffIds: List<String>` — `GameState.kt:102`. One list for the whole game.
- `GameActions.setBluffs` — `GameActions.kt:208-209` — `state.copy(demonBluffIds = bluffIds.take(3))`.
  **A hard cap of three, in the engine.**
- `GameActions.suggestBluffs` — `GameActions.kt:121-127` — 2 Townsfolk + 1 Outsider, excluding
  `players.mapNotNull { it.characterId }`.
- `BluffsSheet.kt:35-112` — titled "Demon bluffs" (`:56`), `"${state.demonBluffIds.size}/3 chosen"`
  (`:58`), candidates filtered to `TOWNSFOLK|OUTSIDER` **and** `it.id !in inPlay` (`:40-45`),
  refusing a fourth at `:83-88`.
- `ShowCard.BluffsCard` — `ShowCards.kt:70`, rendered `:143-155` as "THESE CHARACTERS ARE NOT IN PLAY".
- Reachable from: `NightScreen.kt:783-786` (gated on `step.id == NightMarkers.DEMON_INFO`),
  `ShowCards.kt:388-393`, `GrimoireScreen.kt:186-196`, menu `GameShell.kt:219-220`.
- `NightOrder.kt:90`, `:103-109` inlines the bluff names into the DEMON_INFO detail and nags
  *"no bluffs chosen yet! Pick them from the menu"* (`:107`).

Nothing anywhere knows about the Snitch, the Lunatic, the Summoner, the Poppy Grower, the
Marionette jinx, or "who was shown what".

### Identity — two ad-hoc special cases hard-coded into a getter

```kotlin
// GameState.kt:33
val characterShownToPlayerId: String? get() = shownCharacterId ?: characterId
// GameState.kt:39-44
val nightRoleId: String?
    get() = if (characterId == "drunk" || characterId == "marionette") shownCharacterId ?: characterId
            else characterId
```

- `NightOrder.build` groups seats by `nightRoleId` (`NightOrder.kt:46-48`) and sets
  `playerIds = holders.map { it.id }` (`:177`) — one step row per *character*, however many holders.
- The UI then throws the list away:
  `QuickResolutions` uses `step.playerIds.firstOrNull()` (`NightScreen.kt:467`) and the info panel
  uses `val holderId = step.playerIds.firstOrNull()` (`NightScreen.kt:837`).
  `NightToolTray` re-derives holders itself with `state.players.filter { it.nightRoleId == character?.id }`
  (`NightScreen.kt:205`) and applies "Mark spent" to **all** of them (`:263-276`).
- There is no field for "this seat exercises someone else's ability". The only mechanism is
  `assignCharacter` (`GameActions.kt:46-53`), which rewrites `characterId` — i.e. it changes what
  the seat *registers as*, which is wrong for Philosopher, Alchemist, Boffin, Cannibal, Pixie,
  Apprentice, Bone Collector and Hermit.
- `StatusEffects.isImpaired` (`StatusEffects.kt:35-45`) special-cases `characterId == "drunk"` only.
- Works: Drunk and Marionette wake on the believed character's row; the Marionette is excluded from
  Minion enumeration (`NightOrder.kt:62`, `:83`); `isEvil` reads `characterId` (`GameState.kt:49-52`).

### Reveal / setup flow

- `SetupScreen.kt` is a three-stage wizard (`:58`): script → names (`:262-330`) → bag (`:337-502`).
  There is **no traveller seat** concept in the wizard; `names.size` is the whole table.
- `onStart` (`SetupScreen.kt:110-118`) calls `viewModel.startGame(...)` then `GameActions.deal(...)`.
  `deal` (`GameActions.kt:313-329`) assigns `characterId` to every non-traveller seat and clears
  `shownCharacterId`. It places **no** setup tokens and flips **no** alignments.
- After dealing, nothing prompts the reveal. `RevealFlow` is a menu item
  (`GameShell.kt:231-232`, `:333-341`).
- `RevealFlow.kt:45-59` walks every seat with a character, shows
  `player.characterShownToPlayerId`, and colours the name red when
  `if (player.shownCharacterId != null) character?.team?.isEvil == true else player.isEvil(...)`
  (`:55-59`). This is correct for the Drunk, Marionette and Lunatic and wrong for the Ogre
  (`ogre.md` #8).
- Setup choices are prompted by four ad-hoc dialogs in `GameShell.kt`: red herring (`:347-376`),
  Drunk (`:377-412`), Lunatic (`:413-438`), Marionette (`:439-478`) — each guarded by a
  `rememberSaveable` "prompt done" flag (`:105-107`) so "Later" silences them permanently.
- `SeatSheet.kt:76-95` "Change character" → `viewModel.assign(...)`; `:96-108` "Set shown identity"
  → `setShownCharacter`; `:310-315` "Flip alignment", "Swap characters". None of these show the
  player anything, log anything, clean up reminders, or offer a first-night re-run.

### Setup validation

- `Setup.distributionFor` (`Setup.kt:86-105`) reproduces the official table for 5–15 and extrapolates
  the repeating pattern to 20 (`MAX_PLAYERS = 20`, `:69`).
- `Setup.modifierFor` (`Setup.kt:121-232`) parses the bracket with three regexes (`:107-113`).
  `TEAM_WARPING_IDS = {atheist, legion, riot}` (`:72`) returns `choiceTeams = Team.entries.toSet()`,
  which switches **all** team checking off. `COMPANIONS` (`:75-78`) forces `huntsman→damsel`,
  `choirboy→king`.
- `validateBag` (`GameActions.kt:420-496`) compares team counts against
  `Setup.allowedDistributions`, relaxing teams with open-ended choices (`:432-442`), honours the
  Sentinel (`:444-455`), checks companions (`:480-485`) and duplicates (`:487-494`).
- `validateSetupState` (`GameActions.kt:503-561`) adds exactly four checks: Drunk shown token,
  Lunatic shown token, Marionette shown token + Demon adjacency, Fortune Teller red herring.
- `SetupScreen.kt:356` calls `validateBag(selected, playerCount, allowAnyDuplicates = allowDuplicates)`
  — **without `fabledIds`**, so the Sentinel relaxation is unreachable in the wizard.
- Escape hatches exist and are load-bearing: "Deal anyway (I know what I'm doing)"
  (`SetupScreen.kt:489-495`) and "Start the night anyway" (`GameShell.kt:551-591`).

---

## Defects and gaps

### Bluffs

1. **P0 · The bluff model cannot express any recipient but "the Demon".**
   `GameState.demonBluffIds` (`GameState.kt:102`) is one list and `setBluffs`
   (`GameActions.kt:208-209`) truncates to three. Therefore: the Lunatic cannot have their own set
   (`lunatic.md` #2), the Snitch cannot give each Minion a set (`snitch.md` #1), the
   Legion + Snitch case cannot hold its **six** (*"possibly 6 bluffs if there is a Snitch in
   play"*), and the Summoner's three cannot be distinguished from a Demon's (`summoner.md` #6).
   Repro: with a Snitch and two Minions, open the bluffs sheet — there is one list and no recipient
   selector. (`snitch.md` #3 frames this around the Marionette "+3" jinx; that jinx turns out to be
   retired — see the ⚠️ note under "Official rules" — but the size-3 cap is a real limitation
   regardless.)

2. **P0 · `BluffsSheet` structurally forbids the Lunatic's legal in-play bluffs.**
   `BluffsSheet.kt:44` filters `it.id !in inPlay`. The wiki says the Lunatic's three "may include
   in-play characters", and an impaired Snitch's Minions may legitimately be shown in-play
   characters as false bluffs. There is no toggle. Repro: try to show the Lunatic the in-play
   Empath — the Empath is not in the list.

3. **P1 · Nothing records what any recipient was actually shown.**
   `demonBluffIds` is mutable state, not a record. On day 3 the Storyteller cannot check a Minion's
   claim against the bluffs they gave, and the end-game reveal cannot list them.

4. **P1 · The bluff step is nagged for in games where bluffs must not be given.**
   `NightOrder.kt:107-111` appends *"no bluffs chosen yet! Pick them from the menu"* to DEMON_INFO
   unconditionally. In a Lil' Monsta game DEMON INFO must be skipped entirely; in a Legion game
   bluffs are optional; in a Poppy Grower game the bluffs belong on the Poppy Grower's row; in an
   Atheist or Summoner game there is no Demon at all.

5. **P1 · `suggestBluffs` does not know which characters are "in use".**
   `GameActions.kt:123` computes `inPlay` from `characterId`, so the Drunk's believed Townsfolk, the
   Marionette's believed good character, the Boffin's granted ability and the Alchemist's granted
   Minion are all silently suggestable. Each is *legal* but each is a deliberate Storyteller
   decision (`drunk.md` #7, `boffin.md` #7, `alchemist.md` #12).

6. **P2 · The bluff card is only reachable from DEMON_INFO.** `NightScreen.kt:783` gates the
   "Show bluffs full-screen" chip on `step.id == NightMarkers.DEMON_INFO`, so the Snitch, Lunatic,
   Summoner and Poppy Grower steps have no one-tap card.

7. **P2 · Bluffs are never re-validated after the evil team changes.** Engineer, Hatter, Pit-Hag,
   Kazali and Summoner can all put a bluffed character into play, or take one out. Nothing warns,
   and nothing offers a re-roll after a Kazali conversion (`kazali.md` #13, `engineer.md` #8).

### Identity

8. **P0 · `nightRoleId` is a two-character `if`, so every other "acts as" case is unrepresentable.**
   `GameState.kt:39-44`. Consequences already logged elsewhere: Philosopher registers as the gained
   character (`philosopher.md` #1); Alchemist would flip evil if implemented naively
   (`alchemist.md` #10); the Boffin's granted ability never appears on the night sheet
   (`boffin.md` #1); the Cannibal never wakes (`cannibal.md` #1); the Pixie never wakes for the
   gained ability (`pixie.md`); the Hermit needs *many* roles at once (`hermit.md:248`).
   This one field is the single blocker under all of them.

9. **P0 · `NightStep.playerIds` is a list and the UI reads element 0.**
   `NightScreen.kt:467` (`QuickResolutions`) and `NightScreen.kt:837` (`InfoCalc`) both call
   `step.playerIds.firstOrNull()`. Any night-order slot with two holders silently serves only one:
   Drunk-as-Empath + real Empath, two or three Village Idiots (one of whom must get *false* info),
   Philosopher duplicating an in-play character, a Marionette believing an in-play role, and — the
   worst case — after a star pass (see #10) the **dead** ex-Demon sorts first and the Demon loses
   its kill panel for the rest of the game. Repro: 10-player TB, Drunk shown "empath", real Empath
   at seat 5, open the Empath step — one answer, computed for whichever seat is earlier.

10. **P0 · `starPass` leaves two seats holding the Demon character.**
    `GameActions.kt:85-95` kills the Demon but keeps `characterId = demonCharacter` on the corpse
    *and* assigns it to the heir. From then on `NightOrder.build` groups two players under `"imp"`
    (`NightOrder.kt:46-48`), the step row lists both names (`NightScreen.kt:735-740`), and
    `QuickResolutions`'s `holder` (`NightScreen.kt:467`) may be the dead one, whose `holder.alive`
    check (`:519`) suppresses `DemonKillPanel` entirely. The same bug fires on the Fang Gu jump
    (`NightScreen.kt:495`). Official How to Run is a **token swap**: *"choose an alive Minion and
    replace their character token with a spare Imp token"* — the dead player's grimoire token
    becomes the Minion token. Repro: Imp targets self → pick a Minion heir → next night, the Imp
    step shows two names and no "who dies?" panel.

11. **P1 · `assignCharacter` is a blunt overwrite with no consequences.**
    `GameActions.kt:46-53` sets `characterId`, nulls `shownCharacterId`, and touches nothing else:
    not `alignmentFlipped` (so a Pit-Hag turning a Townsfolk into the Evil Twin silently makes them
    evil — `pithag.md` #1), not `reminders` (the old character's tokens stay, including "Is the
    Drunk"/"Is The Marionette" — `imp.md` #6), not `note` ("Believes they are the Chef" survives),
    not once-per-game markers, and there is no record that anything happened.

12. **P1 · There is no "show this player their new identity" moment anywhere.**
    Nine different sources create one (Pit-Hag, Barber, Engineer, Hatter, Snake Charmer, star pass,
    Fang Gu, Scarlet Woman, Kazali, Summoner, Lord of Typhon, Huntsman→Damsel) and every one of the
    character docs asks for the same thing independently. The only reveal UI, `RevealFlow.kt`, walks
    **all** seats and cannot be pointed at one.

13. **P1 · No first-night re-run mechanism.** This is the user's own reported complaint
    ("When Professor brings someone back it should remind in the morning and rerun the 1st night").
    `professor.md` #3 owns the Professor case; the same hook is demanded by Pit-Hag, Hatter,
    Engineer, Huntsman, Damsel, Steward, Clockmaker, Shugenja, Pixie, Snitch, Widow and Kazali.

14. **P1 · The real Demon in a Lunatic game is dealt the truth.**
    Official setup swaps the Lunatic and Demon tokens in the grimoire so the *Demon* draws the
    Lunatic token and is corrected on night 1 (*"wake the Demon. Show them the YOU ARE info token,
    then their Demon token"*). `deal` (`GameActions.kt:313-329`) leaves `shownCharacterId = null`
    on the Demon, so `RevealFlow.kt:54` hands them "YOU ARE Imp" at setup and the entire Lunatic
    misdirection is spent before night 1.

15. **P2 · `swapCharacters` transplants hidden identities.** `GameActions.kt:99-115` swaps
    `shownCharacterId` along with `characterId`, so a Barber swap can move "the token this player
    believes" onto a different player (`barber.md` #9). Locked in by
    `GameActionsTest.kt:467-478`, which asserts the current behaviour.

16. **P2 · No alignment reveal.** `ShowCard.AlignmentCard` always renders "YOU ARE GOOD/EVIL"
    (`ShowCards.kt:107-126`) regardless of the card's intended text, so it cannot be used for
    "THIS PLAYER IS EVIL" (Evil Twin, Godfather, Mezepheles) — `general.md` P0. And nothing in the
    deal flow tells the Bounty Hunter's evil Townsfolk, or an evil Traveller, that they are evil.

17. **P2 · Reminder label drift.** `GameShell.kt:460,466` places `PlacedReminder("marionette", "Is the Marionette")`
    while `characters.json` declares `"Is The Marionette"`, so the setup prompt and the night tray
    produce two different tokens on the same seat (`marionette.md` #4). The Drunk has the same
    latent mismatch pattern at `GameShell.kt:394-400`.

### Setup validation

18. **P0 · A legal Kazali bag is rejected and "Randomize" builds an illegal one.**
    `Setup.modifierFor` (`Setup.kt:150-153`) gives Kazali `choiceTeams = {OUTSIDER}` and zero
    deltas, so `validateBag` still checks `MINION` and `DEMON` against the base distribution
    (`GameActions.kt:456-478`). At 10 players a correct Kazali bag (`minions = 0`) reports
    *"Minion: 0 in bag, expected 2"*, and `randomBag` (`GameActions.kt:345-362`) draws 2 Minions
    because the Demon pass runs before the Minion pass. Owned in detail by `kazali.md` #1, #2 — the
    fix belongs to the shared `BagShape` model in §D. Repro: bag builder, 10 players, add Kazali +
    9 good → red error, and "Deal randomly & start" is disabled.

19. **P0 · The Lil' Monsta bag is wrong in two independent ways.**
    `[+1 Minion]` is parsed as `minionDelta = +1` only (`Setup.kt:203-215`), so `Distribution.plus`
    (`Setup.kt:21-32`) trades it against Townsfolk and *keeps* `demons = 1`. At 10 players the app
    demands **6/0/3/1** with the `lilmonsta` token dealt to a seat; the rules require **7/0/3/0**
    with Lil' Monsta held as a token by a Minion. `SetupTest.kt:64-71` and
    `GameActionsTest.kt:219-228` assert the wrong behaviour (`lilmonsta.md` #1, #2, #3, #18).

20. **P0 · Lord of Typhon's bag is wrong the same way as Kazali's.**
    *"remove all Minion tokens and add Townsfolk or Outsider tokens"* — the bag has `minions = 0`
    and the `+1 Minion` applies to the **night-1 conversion count**, not to the bag. The app applies
    `minionDelta = +1` to the bag (`Setup.kt:203-208`) and validates against it, so the correct bag
    is rejected. The seating rule (*"All evil characters sit next to each other in a continuous
    line… They cannot sit at the end"*) is not validated at all.

21. **P0 · `TEAM_WARPING_IDS` disables validation instead of replacing it.**
    `Setup.kt:127-129` returns `choiceTeams = Team.entries.toSet()` for Atheist/Legion/Riot, so
    `checkedTeams` becomes empty (`GameActions.kt:457-458`) and **any** bag of the right size passes
    — including an Atheist bag containing an Imp (`atheist.md` #1, #4) or a Legion bag with two
    Legion players. The rules for these three are precise and checkable (§D).

22. **P0 · `[X Outsiders]` discards X.** `Setup.kt:154-157` extracts only the *team* from Xaan's
    bracket. X — the night on which the Xaan poisons, *frozen at setup even if the Outsider count
    later changes* — is never stored anywhere (`xaan.md` #2). The same gap covers every stored setup
    choice: Godfather's ±1, Hermit's −0/−1, Balloonist's +0/+1, Village Idiot's count, Kazali's
    Outsider count.

23. **P0 · The Marionette setup guard blocks legal games.**
    `GameActions.kt:540-543` requires a `Team.DEMON` neighbour. In a Summoner game there is no
    Demon until night 3 (jinx: *"The Marionette neighbors the Summoner"*); in a Lil' Monsta game
    there is no Demon token at all (jinx: *"The Marionette neighbors a Minion"*); in a Kazali game
    the Marionette is created on night 1 (jinx: *"they must start as their neighbor"*). All three
    are unstartable. Owned by `marionette.md` #1 and `summoner.md` #1; the fix is the neighbour
    predicate in §D.

24. **P1 · Setup modifiers are computed only over bag characters.**
    `Setup.adjustedDistribution(playerCount, selected)` (`Setup.kt:252-255`) takes a
    `List<Character>` drawn from the bag. It must also fold in: the Boffin's granted good character
    (*"these changes are made during setup, as normal"* — `boffin.md` #6), the Alchemist's granted
    Minion (`alchemist.md` #3), and the Marionette's believed character (Huntsman and Balloonist
    jinxes). A legal Alchemist-Baron game is rejected today.

25. **P1 · Setup-time tokens and choices are four hard-coded dialogs.**
    Only Fortune Teller, Drunk, Lunatic and Marionette are prompted (`GameShell.kt:347-478`);
    only those four are validated (`GameActions.kt:513-559`). Missing entirely: Bounty Hunter's
    evil Townsfolk, Village Idiot's DRUNK, Puzzlemaster's DRUNK, Widow's KNOW, Grandmother's
    GRANDCHILD, Evil Twin's TWIN, Balloonist's KNOW, Snitch's per-Minion bluffs, Boffin's grant,
    Alchemist's grant, Xaan's X, Damsel-known-to-Minions, Steward/Noble/Knight/Lycanthrope/
    Mezepheles markers, traveller alignment. Each is owned by its character doc; what is missing
    *here* is the table-driven mechanism to declare them once.

26. **P1 · Travellers cannot be seated during setup.**
    `SetupScreen.kt:262-330` collects a flat list of names and `SetupScreen.kt:110-118` deals to all
    of them; a traveller can only be created afterwards via the grimoire seat sheet
    (`SeatSheet.kt:437-450`). `validateBag` and `validateSetupState` correctly exclude travellers
    (`GameActions.kt:507-511`), so the counts only work if you add travellers after dealing.

27. **P1 · Traveller alignment is never asked for.** `assignCharacter(..., isTraveller = true)`
    (`SeatSheet.kt:92`) leaves `alignmentFlipped = false` and `Team.TRAVELLER.isEvil == false`
    (`Character.kt:16`), so every traveller is silently good for Chef, Empath, Seamstress, Oracle,
    Shugenja, Town Crier and the Clockmaker's Minion exclusion (`oracle.md` #1, `seamstress.md` #5).

28. **P2 · `SetupScreen.kt:356` never passes `fabledIds`**, so the Sentinel's ±1 Outsider
    relaxation (`GameActions.kt:444-455`) is dead code during setup (`baron.md` #4). The Bootlegger
    and Spirit of Ivory have no effect on validation at all.

29. **P2 · `baron × heretic` ("The Baron might only add 1 Outsider, not 2") and
    `godfather|spy|widow × heretic` ("Only 1 jinxed character can be in play") are data-only.**
    `GameData.activeJinxes` (`GameData.kt:23`) is used for display (`SeatSheet.kt:222-231`,
    `GameExtras.kt`) but never feeds `validateBag`.

30. **P3 · The Drunk's bracket-less `setup: true` produces meaningless UI.**
    `Setup.modifierFor` falls through to `SetupModifier(id, "Modifies setup")` (`Setup.kt:123-124`),
    so the bag row shows an unexplained "modifies setup" chip (`SetupScreen.kt:535-542`) and the
    header prints `(after [Modifies setup])` (`SetupScreen.kt:373-375`). The **arithmetic is
    correct** — see "Uncertain" above; the fix is display text, not a delta.

31. **P2 · `distributionFor` extrapolates past the official table without saying so.**
    `Setup.kt:98-104` and `MAX_PLAYERS = 20` (`:69`) invent 16–20 player distributions.
    `SetupTest.kt:26-30` locks them in. Officially a 16th player is a Traveller.

32. **P3 · The bag builder shows the *unadjusted* base distribution on the players stage.**
    `SetupScreen.kt:282-286` calls `distributionLabel(names.size)` before any character is chosen,
    which is fine, but the same screen is where a Storyteller decides the player count for a
    Baron/Kazali game.

---

## Proposed behaviour (spec)

### A. Bluffs: `bluffSets`

#### A.1 Types

```kotlin
// GameState.kt

/**
 * Who a bluff set belongs to. Serialized as a plain string so the map is a
 * JSON object: "demon", or "seat:7".
 */
@Serializable
@JvmInline
value class BluffRecipient(val key: String) {
    val seatId: Long? get() = key.removePrefix("seat:").toLongOrNull().takeIf { key.startsWith("seat:") }
    companion object {
        val DEMON = BluffRecipient("demon")
        fun seat(playerId: Long) = BluffRecipient("seat:$playerId")
    }
}

@Serializable
data class BluffSet(
    val characterIds: List<String> = emptyList(),
    /** Which ability created this requirement: "demoninfo", "snitch", "lunatic", "summoner", "poppygrower". */
    val sourceId: String = "demoninfo",
    /** 3 normally; 6 for the Demon under the Snitch x Marionette jinx. */
    val size: Int = 3,
    /** Night on which the storyteller actually showed the card; null = not shown yet. */
    val shownCycle: Int? = null,
)
```

`GameState` gains:

```kotlin
val bluffSets: Map<String, BluffSet> = emptyMap(),
```

and keeps `demonBluffIds` as a **read-only derived property** so no call site breaks:

```kotlin
val demonBluffIds: List<String> get() = bluffSets[BluffRecipient.DEMON.key]?.characterIds.orEmpty()
```

**Migration.** `Json { ignoreUnknownKeys = true; encodeDefaults = true }`
(`app/.../data/Persistence.kt:15-18`) means an added field with a default is backwards compatible,
but an old save's `demonBluffIds` would be dropped. Keep the stored field under a new name and
migrate on load:

```kotlin
@Serializable
data class GameState(
    ...
    val bluffSets: Map<String, BluffSet> = emptyMap(),
    @Deprecated("migrated into bluffSets on load")
    @SerialName("demonBluffIds") val legacyDemonBluffIds: List<String> = emptyList(),
)

/** Called once by GameViewModel.setGame / SavedDataSerializer.readFrom. */
fun GameState.migrated(): GameState =
    if (legacyDemonBluffIds.isEmpty() || bluffSets.containsKey(BluffRecipient.DEMON.key)) this
    else copy(
        bluffSets = bluffSets + (BluffRecipient.DEMON.key to BluffSet(legacyDemonBluffIds)),
        legacyDemonBluffIds = emptyList(),
    )
```

#### A.2 Requirements planner

Replace the DEMON_INFO nag with a computed list of what this game owes whom.

```kotlin
// Setup.kt (or a new Bluffs.kt)
data class BluffRequirement(
    val recipient: BluffRecipient,
    /** "Demon bluffs", "Snitch bluffs — Ana (Poisoner)", "Lunatic bluffs — Bo". */
    val label: String,
    val size: Int,
    /** Only the Lunatic (and an impaired Snitch) may be shown in-play characters. */
    val allowInPlay: Boolean,
    /** Night-order step where the card is shown. */
    val stepId: String,
    val sourceId: String,
    /** Rules sentence surfaced under the picker. */
    val reason: String,
    /** false = offer it, never block on it (Legion). */
    val required: Boolean = true,
)

object Bluffs {
    fun requirements(state: GameState, lookup: (String) -> Character?): List<BluffRequirement>
    /** Ids that are legal picks for [requirement], with an "in use" annotation. */
    fun candidates(state: GameState, script: List<Character>, requirement: BluffRequirement): List<BluffCandidate>
    fun suggest(state: GameState, script: List<Character>, requirement: BluffRequirement, random: Random): List<String>
}

data class BluffCandidate(
    val character: Character,
    val inPlay: Boolean,
    /** "the Drunk believes this", "the Boffin gave the Demon this", "the Alchemist has this". */
    val inUseBy: String? = null,
)
```

`requirements` rules, in order:

```
residents = players.count { !isTraveller }
demonSeats = players.filter { lookup(characterId)?.team == DEMON }
minionSeats = players.filter { lookup(characterId)?.team == MINION && characterId != "marionette" }

// no bluffs at all
if ("lilmonsta" in inPlay)  -> emit nothing for DEMON (How to Run: skip MINION INFO and DEMON INFO)
if ("atheist"   in inPlay)  -> emit nothing
if (residents < 7 && "poppygrower" !in inPlay && "summoner" !in inPlay) -> emit nothing for DEMON

// the Demon set
if (demonSeats.isNotEmpty() && residents >= 7) {
    size   = 3
    stepId = if ("poppygrower" in inPlay) "poppygrower" else DEMON_INFO
    required = "legion" !in inPlay                                              // "Bluffs are optional"
    // NOTE: do NOT add +3 for Snitch+Marionette. That jinx is retired; the Marionette is simply
    // not woken. `size` stays configurable for Bootlegger scripts and for the Legion+Snitch seat,
    // which receives two SEPARATE requirements (below) rather than one enlarged one.
}

// the Summoner replaces the Demon set
if ("summoner" in inPlay && demonSeats.isEmpty())
    emit BluffRequirement(seat(summonerSeat), size = 3, stepId = "summoner", required = "alchemist" !in inPlay)
    // jinx: "The Alchemist-Summoner does not get bluffs"

// the Snitch: one independent set per Minion.
// minionSeats excludes the Marionette ("not woken due to character abilities that would confirm
// that they are a Minion eg. Snitch, Preacher, ...") and INCLUDES Legion seats, which register as
// Minions too — that is the official "possibly 6 bluffs" case, as two separate sets on one seat.
if ("snitch" in inPlay) for (m in minionSeats)
    emit BluffRequirement(seat(m.id), size = 3, stepId = "snitch",
                          allowInPlay = isImpaired(snitchSeat))   // an impaired Snitch may give false bluffs

// the Lunatic: their own set, in-play characters allowed
for (l in players.filter { it.characterId == "lunatic" })
    emit BluffRequirement(seat(l.id), size = 3, stepId = "lunatic", allowInPlay = true,
                          reason = "The Lunatic's bluffs may include in-play characters.")
```

`candidates`: script characters with `team in {TOWNSFOLK, OUTSIDER}`, minus in-play ids unless
`allowInPlay`, each annotated with `inUseBy` when the id is the Drunk's or Marionette's
`shownCharacterId`, the Boffin grant, the Alchemist grant, or a Pixie MAD token. **Annotate, never
exclude** — all of those are legal bluffs.

`suggest`: 2 Townsfolk + 1 Outsider, never repeating a character already suggested for the same
recipient, drawing **independently** per recipient (the Snitch examples show overlapping and
disjoint sets are both legal), and preferring `inUseBy == null`. For a Lunatic requirement,
deliberately include **at least one in-play character** and never the Lunatic's believed Demon.

#### A.3 Actions

```kotlin
fun setBluffs(state: GameState, recipient: BluffRecipient, ids: List<String>, size: Int): GameState
fun markBluffsShown(state: GameState, recipient: BluffRecipient): GameState  // stamps shownCycle
fun clearBluffs(state: GameState, recipient: BluffRecipient): GameState
/** Called after Kazali/Summoner conversions and Engineer/Hatter/Pit-Hag changes. */
fun bluffConflicts(state: GameState, lookup: (String) -> Character?): List<String>
```

`bluffConflicts` returns e.g. *"Fisherman is one of the Demon's bluffs and is now in play"* and
*"Ana's original Empath is no longer in play — it would make a good new bluff"*.

#### A.4 UI

- `BluffsSheet` gains a recipient chip row across the top (Demon · one chip per Minion · Lunatic ·
  Summoner), each chip badged `n/size`. Title follows the recipient
  (*"Demon bluffs"* / *"Snitch bluffs — Ana"* / *"Lunatic bluffs — Bo"* / *"Summoner bluffs"*).
- A per-recipient **"Suggest"** plus a global **"Suggest for everyone"** (independent draws).
- When `allowInPlay`, in-play characters appear with an "in play — allowed here" badge; otherwise
  they are absent. `inUseBy` candidates appear with an amber note.
- `NightScreen.kt:783` gate becomes: show the `ShowCard.BluffsCard` chip for **any** step that a
  `BluffRequirement` names as its `stepId`, labelled with the recipient's name, and tapping it also
  calls `markBluffsShown`.
- `NightOrder` stops inlining bluff names into `DEMON_INFO.detail` (`NightOrder.kt:90`, `:103-111`);
  the requirement list drives the row instead.

### B. Identity: grants, `actingRoles`, per-holder steps

#### B.1 Types — one model, replacing four proposals

```kotlin
// GameState.kt

@Serializable
enum class GrantMode {
    /** The seat no longer wakes for its own character (Philosopher, Alchemist, Apprentice, Cannibal, Drunk, Marionette, Lunatic). */
    REPLACE,
    /** The seat wakes for BOTH (Boffin's Demon, Pixie, Hermit, Bone Collector's target). */
    ADD,
}

@Serializable
data class AbilityGrant(
    /** The ability actually exercised: "chambermaid", "poisoner", "pukka". */
    val abilityId: String,
    /** Who granted it: "philosopher", "alchemist", "boffin", "apprentice", "cannibal",
     *  "pixie", "bonecollector", "hermit", "drunk", "marionette", "lunatic". */
    val sourceId: String,
    val mode: GrantMode = GrantMode.ADD,
    /** Night-order slot to wake at; null = the ability's own slot. The Lunatic wakes at "lunatic". */
    val slotId: String? = null,
    /** Boffin, Bone Collector, Ogre: works even while the holder is drunk or poisoned. */
    val worksWhileImpaired: Boolean = false,
    /** Drunk, Marionette, Lunatic: the ability NEVER works; every result is fabricated. */
    val alwaysFalse: Boolean = false,
    /** Cycle it was granted, for the log and for "acts tonight" decisions. */
    val cycle: Int = 0,
    /** Independent once-per-game state for a granted once-per-game ability. */
    val spent: Boolean = false,
)
```

`Player` gains `val grants: List<AbilityGrant> = emptyList()`. `GameState` gains

```kotlin
/** Grants whose holder is derived, not fixed to a seat. */
val floatingGrants: List<FloatingGrant> = emptyList(),

@Serializable
data class FloatingGrant(
    val abilityId: String,
    val sourceId: String,           // "boffin", "plaguedoctor"
    val holder: GrantHolder,        // ALIVE_DEMON, STORYTELLER
    val worksWhileImpaired: Boolean = false,
)
@Serializable enum class GrantHolder { ALIVE_DEMON, STORYTELLER }
```

This is deliberately the union of the four existing proposals:

| Existing proposal | Expressed here as |
|---|---|
| `philosopher.md` `actingCharacterId` / `actingSourceId` | `grants += AbilityGrant(gained, "philosopher", REPLACE)` |
| `alchemist.md` `grantedAbilityId` + `nightSlots` | `grants += AbilityGrant(minionId, "alchemist", REPLACE)`; `nightSlots` becomes `actingRoles(...).map { it.slotId }` |
| `boffin.md` `GameState.boffinGrantId` + `NightStep.abilityId` | `floatingGrants += FloatingGrant(goodId, "boffin", ALIVE_DEMON, worksWhileImpaired = true)`; `NightStep.abilityId` is adopted verbatim |
| `cannibal.md` derived `cannibalNightRole` | a `REPLACE` grant re-derived from the `Lunch` token by `Identity.reconcile` (below), so the same code path drives the night sheet |

**`shownCharacterId` keeps its exact current meaning** — the token this player has seen — and gains
two new users: the real Demon in a Lunatic game (set to `"lunatic"` at deal, cleared at
DEMON_INFO), and any player mid-change whose new token has not been shown yet. `nightRoleId` is
**deleted**; `characterShownToPlayerId` stays.

#### B.2 `actingRoles`

```kotlin
// new file engine/.../Identity.kt

data class ActingRole(
    val playerId: Long,
    /** Whose rules to run: night guide entry, InfoCalc key, target count, reminder tokens. */
    val abilityId: String,
    /** Which night-order slot it fires in (== abilityId except for the Lunatic). */
    val slotId: String,
    /** null when this is the seat's own character. */
    val sourceId: String?,
    val alwaysFalse: Boolean,
    val worksWhileImpaired: Boolean,
)

object Identity {

    /** The token this player has seen — what a "YOU ARE" card must show. */
    fun believedCharacterId(p: Player): String? = p.shownCharacterId ?: p.characterId

    /** What this seat registers as. ALWAYS the truth. Never shownCharacterId. */
    fun registersAs(p: Player): String? = p.characterId

    /** Everything this seat is woken for, own ability first. */
    fun actingRoles(state: GameState, lookup: (String) -> Character?, p: Player): List<ActingRole>

    /** Every acting role in the game, for NightOrder. */
    fun allActingRoles(state: GameState, lookup: (String) -> Character?): List<ActingRole>

    /**
     * Derives the grants that are implied by the grimoire rather than stored:
     *  - characterId == "drunk"      -> REPLACE(shownCharacterId, "drunk", alwaysFalse = true)
     *  - characterId == "marionette" -> REPLACE(shownCharacterId, "marionette", alwaysFalse = true)
     *  - characterId == "lunatic"    -> REPLACE(shownCharacterId, "lunatic", slotId = "lunatic", alwaysFalse = true)
     *  - characterId == "hermit"     -> ADD(every Outsider on the script, "hermit")
     *  - cannibal + a "Lunch" token  -> REPLACE(last executee's characterIdAtDeath, "cannibal")
     *  - floatingGrant(ALIVE_DEMON)  -> ADD on the single alive Demon seat
     * Called by actingRoles; nothing is stored twice.
     */
    fun derivedGrants(state: GameState, lookup: (String) -> Character?, p: Player): List<AbilityGrant>
}
```

`actingRoles` result table (this is the whole identity model on one page):

| Seat | Roles returned |
|---|---|
| plain character | `[own]` |
| Drunk (shown `X`) | `[X @slot X, source drunk, alwaysFalse]` |
| Marionette (shown `X`) | `[X @slot X, source marionette, alwaysFalse]` — and excluded from `MINION_INFO` |
| Lunatic (shown Demon `D`) | `[D @slot "lunatic", source lunatic, alwaysFalse]` |
| Philosopher, gained `G` | `[G @slot G, source philosopher]` |
| Alchemist, granted `M` | `[M @slot M, source alchemist]` |
| Apprentice, granted `A` | `[A @slot A, source apprentice]` |
| Cannibal, lunch `L` | `[L @slot L, source cannibal]` |
| Demon + Boffin grant `G` | `[own, G @slot G, source boffin, worksWhileImpaired]` |
| Pixie with HAS ABILITY `T` | `[own, T @slot T, source pixie]` |
| Bone Collector's target (dead seat) | `[own @slot own, source bonecollector, worksWhileImpaired]`, cleared at dusk |
| Hermit | `[own] + [each script Outsider, source hermit]` |
| Storyteller (Plague Doctor) | not a seat — rendered as a seatless `NightStep` with `holderId = null` |

`StatusEffects.isImpaired` must stop special-casing `"drunk"` (`StatusEffects.kt:37`) and instead
answer per-role: an `ActingRole` with `alwaysFalse` never works; one with `worksWhileImpaired`
ignores the seat's own impairment (but the Boffin's own impairment kills it). Add:

```kotlin
fun roleWorks(state: GameState, lookup: (String) -> Character?, role: ActingRole): Boolean
```

and make `InfoCalc.InfoResult` carry `val abilityMalfunctions: Boolean` (requested independently by
`marionette.md` #2) so the false-info UI stops string-matching caveats
(`NightScreen.kt:903-906`).

#### B.3 Per-holder night steps

> **Reconcile with `mechanics/night-engine.md`.** That doc independently reaches the same
> conclusion and proposes `NightStep.playerId: Long?` (plus a stopgap
> `playerIds.firstOrNull { canAct } ?: playerIds.firstOrNull()`). Same field, different name — pick
> one (`holderId` reads better next to `playerIds`, but either works) and take **`abilityId` and
> `key` from here**, since they are what make the Boffin/Philosopher/Alchemist/Lunatic rows
> resolvable at all. The stopgap is a good first wave; it fixes the star-pass symptom without
> fixing the Drunk/Village Idiot ones.
> **Reconcile with `mechanics/data-accuracy.md`** for the stale-data list under §F — it
> independently found the Riot drift.

```kotlin
// NightOrder.kt
@Serializable
data class NightStep(
    /** Night-order slot id, or a NightMarker. */
    val id: String,
    /** Which ability's guide / InfoCalc / targets to use. Defaults to [id]. */
    val abilityId: String = id,
    /** The single seat this row is for; null for group steps and markers. */
    val holderId: Long? = null,
    /** "boffin" / "philosopher" / "lunatic" / "drunk"… for the badge on the row. */
    val sourceId: String? = null,
    val title: String,
    val detail: String,
    /** Kept for group steps (MINION_INFO, DEMON_INFO, lilmonsta, legion, riot). */
    val playerIds: List<Long> = emptyList(),
) {
    /** Stable key for nightStepsDone. Two Village Idiots get two checkboxes. */
    val key: String get() = if (holderId == null) id else "$id#$holderId"
}
```

`NightOrder.build` changes from "group players by `nightRoleId`" to "for each slot in the order,
emit one row per `ActingRole` whose `slotId` matches", sorted by seat order. Group steps
(`MINION_INFO`, `DEMON_INFO`, `lilmonsta`, `legion`, `riot`, `poppygrower`'s reveal) keep
`holderId = null` and `playerIds`.

`GameActions.toggleNightStep` and `GameState.nightStepsDone` switch from `id` to `key`
(`GameActions.kt:265-272`, `GameState.kt:106`). `GameShell.kt:147-160`'s dawn guard follows.

`NightScreen` changes:
- `QuickResolutions(viewModel, state, step)` → `QuickResolutions(viewModel, state, step, holder: Player)`
  — delete `step.playerIds.firstOrNull()` at `NightScreen.kt:467`.
- `InfoCalc.compute(..., characterId = step.abilityId, holderId = step.holderId, ...)`
  — delete `playerIds.firstOrNull()` at `NightScreen.kt:837`.
- `NightGuide.forStep(step.abilityId, isFirstNight)` — so `boffin`-granted and Lunatic rows get the
  granted character's run-book.
- `NightToolTray` takes the row's `holder` instead of re-deriving with `nightRoleId`
  (`NightScreen.kt:205`), so "Mark spent" marks one seat (`:263-276`) and writes to the grant's
  `spent` flag when `sourceId != null`.
- Row badge: when `sourceId != null`, prefix the title, e.g.
  `"Chambermaid — Ana (via the Boffin)"`, `"Empath — Bo (thinks they are; give false info)"`,
  `"Pukka — Cai (LUNATIC — nothing actually happens)"`.

#### B.4 `starPass` fix

```kotlin
/**
 * Demon self-kill that passes the mantle. Official How to Run is a TOKEN SWAP:
 * the dying player's grimoire token becomes the heir's former character, so
 * exactly one seat holds the Demon character afterwards. The DeathRecord's
 * characterIdAtDeath snapshot preserves "they died as the Imp".
 */
fun starPass(
    state: GameState,
    demonPlayerId: Long,
    heirPlayerId: Long,
    lookup: (String) -> Character?,
    cause: DeathCause = DeathCause.DEMON,
): GameState
```

Contract:
1. `heir` must be alive and not a Traveller; otherwise return `state` unchanged.
2. `kill(demonPlayerId, cause, lookup)` — the `DeathRecord.characterIdAtDeath` snapshot
   (`GameActions.kt:152`) already captures the Demon character before any change.
3. `changeCharacter(heirPlayerId, demonCharacterId, ChangeReason.STAR_PASS, ...)` — see §C.
4. `changeCharacter(demonPlayerId, heirFormerCharacterId, ChangeReason.STAR_PASS_TOKEN_SWAP,
   suppressReveal = true)` — the corpse takes the heir's old token, so `NightOrder` sees one Demon.
5. Place `PlacedReminder(demonCharacterId, "Dead")` on the corpse when the character declares it.
6. Queue an `IdentityRecord` with `pendingReveal = true` for the heir and a step note
   *"the new Demon does not act tonight"*.

Precedence and heir eligibility stay in `imp.md` (#1, #2) — this spec only guarantees the
**one-Demon-seat** invariant, which `WinCheck` (`WinCheck.kt:21-22`), `NightOrder`, `StatusEffects`
and the night sheet all depend on. Same call for the Fang Gu jump (`NightScreen.kt:495`).

Add an invariant helper used by tests and by a debug assertion:

```kotlin
/** Ids that must never be held by two live seats: every Demon character, plus lilmonsta's token. */
fun GameState.duplicateLiveCharacterIds(lookup: (String) -> Character?): List<String>
```

### C. Reveal & `changeCharacter`

#### C.1 One action for every character change

```kotlin
@Serializable
enum class ChangeReason {
    DEAL, STAR_PASS, STAR_PASS_TOKEN_SWAP, FANG_GU_JUMP, SCARLET_WOMAN, PIT_HAG, BARBER,
    ENGINEER, HATTER, SNAKE_CHARMER, KAZALI, SUMMONER, LORD_OF_TYPHON, HUNTSMAN_DAMSEL,
    AMNESIAC, DEUS_EX_FIASCO, STORYTELLER,
}

@Serializable
data class IdentityRecord(
    val playerId: Long,
    val cycle: Int,
    val atNight: Boolean,
    val fromCharacterId: String?,
    val toCharacterId: String?,
    val fromEvil: Boolean,
    val toEvil: Boolean,
    val reason: ChangeReason,
    /** The player still has to be shown their new token. */
    val pendingReveal: Boolean = true,
    /** Their new character has first-night info that must be run for them. */
    val pendingFirstNightRerun: Boolean = false,
    /** Free-text obligations surfaced at dawn / day start. */
    val notes: List<String> = emptyList(),
)
```

`GameState` gains `val identityLog: List<IdentityRecord> = emptyList()`.

```kotlin
fun changeCharacter(
    state: GameState,
    playerId: Long,
    newCharacterId: String?,
    reason: ChangeReason,
    lookup: (String) -> Character?,
    /** null = keep the player's CURRENT alignment (the Pit-Hag rule). */
    newEvil: Boolean? = null,
    /** Drunk/Lunatic/Marionette: what token the player now believes. */
    shownCharacterId: String? = null,
    suppressReveal: Boolean = false,
): GameState
```

Consequences applied, in order (all pure, all inside one undo step):

1. **Alignment.** `wasEvil = player.isEvil(lookup)`; target `evil = newEvil ?: wasEvil`;
   set `alignmentFlipped = (newCharacter.team.isEvil != evil)`. This is the Pit-Hag rule
   ("alignment persists despite character changes") and it also makes Snake Charmer / star pass /
   Fang Gu / Summoner correct by passing `newEvil = true` explicitly.
2. **Reminders.** Remove every `PlacedReminder` in the **whole grimoire** whose
   `sourceId == oldCharacterId` (their tokens belong to an ability that no longer exists), and
   remove `"Is the Drunk"` / `"Is The Marionette"` / `"Is the Philosopher"` / `"Is The Alchemist"`
   from this seat. Keep foreign tokens (Poisoned, Safe, Mad, Red herring, Grandchild, Twin, KNOW).
3. **Shown identity.** `shownCharacterId = shownCharacterId` argument (usually `null`); clear
   `note` when it starts with `"Believes they are"`.
4. **Grants.** Drop this seat's `grants` whose `sourceId == oldCharacterId`; if the seat was the
   holder of a `FloatingGrant(ALIVE_DEMON)` the grant simply re-derives to the new alive Demon
   (Boffin: *"If a new Demon is created… this new Demon has an ability from the Boffin"*).
5. **Record.** Append an `IdentityRecord`, with
   `pendingReveal = !suppressReveal && newCharacterId != null` and
   `pendingFirstNightRerun = newCharacter has a first-night entry in the night order && reason != DEAL`.
6. **Notes** (surfaced at dawn / day start / on the row that created the change):
   - Demon created mid-game → *"A second Demon exists — deaths tonight are arbitrary."*
   - New character has `setup == true` → *"<X>'s [bracket] has no effect — square brackets only
     change the setup."* This is a **rule**, not a judgement call. Abilities page, verbatim:
     *"If a player becomes a character whose ability text has square brackets — [like this] — that
     portion of the ability has no effect. Square brackets indicate a different game setup, and
     this is not changed mid-game."* So `changeCharacter` must **never** alter team counts, and the
     step text should say so rather than asking the Storyteller to rule.
   - `bluffConflicts(state)` non-empty → the conflict lines from §A.3
   - Marionette adjacency / Lord of Typhon line broken → the §D predicate's message
   - `reason in {KAZALI, SUMMONER, LORD_OF_TYPHON}` → *"Do NOT show them the other evil players and
     do NOT give bluffs."*

`assignCharacter` (`GameActions.kt:46-53`) is kept **only** for setup-phase seat editing and
delegates to `changeCharacter(..., reason = STORYTELLER)` outside SETUP.
`swapCharacters` (`GameActions.kt:99-115`) becomes two `changeCharacter` calls and **stops swapping
`shownCharacterId`** (defect 15); `GameActionsTest.kt:467-478` must be rewritten.

#### C.2 Pending reveals

```kotlin
fun pendingReveals(state: GameState): List<IdentityRecord>
fun markRevealed(state: GameState, playerId: Long): GameState
fun pendingFirstNightReruns(state: GameState): List<IdentityRecord>
fun markRerunDone(state: GameState, playerId: Long): GameState
/** The first-night sheet, filtered to one seat's acting roles. */
fun firstNightRerunSteps(state: GameState, lookup: (String) -> Character?, playerId: Long): List<NightStep>
```

Surfacing:
- **On the night step that caused it**: an inline "Show <name> their new character" chip that opens
  `ShowCard.CharacterCard("YOU ARE", toCharacterId)` and then, when the alignment changed, the
  thumbs-down `ShowCard.AlignmentCard(evil = true)`; tapping through calls `markRevealed`.
- **At dawn** (`advancePhase` SETUP/NIGHT→DAY, `GameActions.kt:258-263`): a dawn briefing listing
  every unrevealed record and every pending first-night re-run. This is the user's stated
  requirement for the Professor.
- **In the grimoire**: an amber dot on any seat with `pendingReveal`.
- `RevealFlow` gains a `seats: List<Long>? = null` parameter; when non-null it walks only those
  seats and calls `markRevealed` per seat. `RevealFlow.kt:45` becomes
  `remember(state.players, seats) { seats?.mapNotNull(state::player) ?: state.players.filter { it.characterId != null } }`.

#### C.3 Deal → reveal, done properly

`GameActions.deal` (`GameActions.kt:313-329`) becomes:

```kotlin
fun deal(state, bag, random, lookup): GameState
```

and additionally:
1. Skips `isTraveller` seats (unchanged).
2. Places every declared setup token that is unambiguous:
   `drunk:"Is the Drunk"`, `marionette:"Is The Marionette"` (single canonical casing, matching
   `characters.json`), `lilmonsta:"Is The Demon"` where applicable.
3. **Lunatic token swap** (official How to Run, cited above): when a `lunatic` seat exists and a
   Demon seat exists, set `lunatic.shownCharacterId = <demonCharacterId>` and
   `demonSeat.shownCharacterId = "lunatic"`. Expose it as
   `GameActions.applyLunaticTokenSwap(state, lookup)` with a Storyteller toggle
   (default on) because the wiki also documents the two-Demon-tokens variant.
   The Demon's true identity is restored at DEMON_INFO by
   `GameActions.revealTrueIdentity(state, demonSeatId)` (clears `shownCharacterId`), driven by a
   one-tap "Show <name> YOU ARE <Demon>" chip on the DEMON_INFO row.
4. Appends one `IdentityRecord(reason = DEAL, pendingReveal = true)` per dealt seat, so
   `RevealFlow` becomes the natural next step and the Storyteller can re-open it for one seat later.
5. `SetupScreen.kt:110-118` chains straight into `RevealFlow` after `startGame` + `deal` instead of
   dropping the user on the grimoire.

Additional reveal cases the flow must handle:
- **Alignment-only reveals**: a seat whose `isEvil` differs from its character's natural alignment
  (Bounty Hunter's evil Townsfolk, an evil Traveller) gets a second card "YOU ARE EVIL" after the
  character card. Fix `ShowCards.kt:107-126` so `AlignmentCard` takes the caption
  (`AlignmentCard(evil: Boolean, caption: String = "YOU ARE")`) — this also repairs the Evil Twin,
  Godfather and Mezepheles cards (`general.md` P0).
- **Ogre**: `RevealFlow.kt:55-59` must never colour by `player.isEvil` for a seat with an
  `ogre:"Friend"` token — the Ogre does not learn their alignment. Use
  `Identity.believedCharacterId` for the token and the character's *natural* team for the colour,
  with an explicit alignment card only where the rules say so.
- **Evil Twin pair**: a two-card sequence bound to one action —
  `CharacterCard("THIS PLAYER IS", "eviltwin")` to the good twin, then
  `CharacterCard("YOUR TWIN IS", Identity.believedCharacterId(goodTwin))` to the Evil Twin (so a
  Drunk good twin shows their *believed* character, matching the physical grimoire).
- **Kazali / Summoner / Lord of Typhon**: those seats are dealt a good character and converted at
  their night step; `changeCharacter(..., reason = KAZALI, newEvil = true)` produces the
  YOU ARE + thumbs-down pair automatically. Record the pre-conversion character in the
  `IdentityRecord.fromCharacterId` so §A.3 can offer it as a bluff.

### D. Setup validation: bag shapes, the requirement table, stored choices

#### D.1 Bag shapes replace `TEAM_WARPING_IDS`

```kotlin
// Setup.kt
data class BagShape(
    val townsfolk: IntRange? = null,
    val outsiders: IntRange? = null,
    val minions: IntRange? = null,
    val demons: IntRange? = null,
    /** Ids that must be in the bag. */
    val requireInBag: Set<String> = emptySet(),
    /** Ids that must NOT be in the bag even though they are "in play". */
    val forbidInBag: Set<String> = emptySet(),
    /** Exact copy count for a duplicable id, e.g. riot -> minions+1. */
    val copies: Map<String, IntRange> = emptyMap(),
    val note: String = "",
)

/** Overrides computed from the base distribution for [playerCount]. */
fun bagShapeFor(characterId: String, base: Distribution, playerCount: Int): BagShape?
```

| Character | Shape (base = `distributionFor(n)`) |
|---|---|
| `kazali` | `minions = 0..0`, `demons = 1..1`, `outsiders = 0..(base.outsiders + base.minions)`, `townsfolk = (n - 1 - outsiders)`, note *"Minions are created on the first night."* |
| `lordoftyphon` | `minions = 0..0`, `demons = 1..1`, outsiders free, note *"The 3 Minions and the evil line are created on the first night."* |
| `lilmonsta` | `forbidInBag = {"lilmonsta"}`, `demons = 0..0`, `minions = (base.minions + 1)..(base.minions + 1)`, `townsfolk = base.townsfolk..base.townsfolk` |
| `summoner` | `demons = 0..0`, `minions = base.minions`, `townsfolk = base.townsfolk + 1` *(already correct)* |
| `atheist` | `minions = 0..0`, `demons = 0..0`, `townsfolk + outsiders = n` |
| `legion` | **advisory only** — `minions = 0..0` is firm; `copies = {"legion" to ((n / 2) + 1)..(n - 1)}` is a *suggestion* the validator warns about but never blocks, note *"About 7 Legion to 3 good at 10 players."* |
| `riot` | **no shape** — an ordinary Demon in an ordinary bag; delete `riot` from `TEAM_WARPING_IDS` (`Setup.kt:72`) and from `DUPLICABLE` (`GameActions.kt:413`), and drop `[All Minions are Riot]` from `characters.json` |
| `marionette` (3-Minion games only) | `minions = (base.minions - 1)..(base.minions - 1)` in the bag when `base.minions == 3`, with a night-1 requirement to create the missing real Minion — *"This ensures that only one Minion token is in the bag"* |
| `villageidiot` | `copies = {"villageidiot" to 1..3}` (townsfolk absorb the difference) |
| `xaan` | `outsiders = storedChoice("xaan.X")..storedChoice("xaan.X")` once X is chosen; free before |
| `bountyhunter` | no count change; adds a **requirement**, not a shape |

`validateBag` composes: start from `Setup.allowedDistributions`, then intersect each in-bag
character's `BagShape`. When a shape is present for a team, it **replaces** the distribution check
for that team instead of disabling it. `randomBag` (`GameActions.kt:338-402`) must draw from the
shape rather than from `distributionFor` — draw Demons first only when `shape.demons` allows it,
and skip the Minion pass entirely when `shape.minions == 0..0`.

Setup modifiers must be computed over `bagCharacters + virtualCharacters` where

```kotlin
/** Characters whose setup bracket applies even though they are not tokens in the bag. */
fun virtualSetupCharacters(state: GameState, lookup: (String) -> Character?): List<Character> =
    listOfNotNull(
        state.setupChoices["boffin.grant"]?.let(lookup),        // "these changes are made during setup, as normal"
        state.setupChoices["alchemist.grant"]?.let(lookup),
        state.players.firstOrNull { it.characterId == "marionette" }?.shownCharacterId?.let(lookup),
    )
```

(the Marionette case is mandated by the Huntsman and Balloonist jinxes; the Drunk's believed
Townsfolk is **flagged as uncertain** — no jinx says either way, so surface it as an advisory
question rather than enforcing it.)

Fabled: `SetupScreen.kt:356` must pass `state.fabledIds` — or, better, let the wizard pick Fabled
before the bag stage. `sentinel` keeps its ±1 relaxation; `bootlegger` downgrades all bag issues to
warnings; `spiritofivory` adds a **runtime** cap (`≤ 1 extra evil player` across
`changeCharacter` alignment flips), not a bag rule.

`distributionFor` should return the official table for 5–15 and mark 16–20 as an explicit house
extrapolation (`Distribution(..., official = false)` or a companion `isOfficial(playerCount)`), with
a one-line note in the wizard: *"Officially, a 16th player is a Traveller."*

#### D.2 A declarative setup-requirement table

`validateSetupState` (`GameActions.kt:503-561`) becomes a fold over one table, so a new character is
one data row instead of a new `when` branch and a new dialog.

```kotlin
// Setup.kt (or SetupRequirements.kt)
enum class RequirementKind {
    SHOWN_TOKEN,     // pick a character token this player believes
    REMINDER,        // place a token on some seat
    ALIGNMENT,       // flip a seat's alignment
    GRANT,           // pick an ability the seat holds
    NUMBER,          // store an integer choice (Xaan's X)
    PAIR,            // pick a partner seat (Evil Twin)
    BLUFFS,          // a BluffRequirement from §A
    SEATING,         // an adjacency/line constraint
    INFORM,          // "show every Minion the Damsel token"
}

data class SetupRequirement(
    /** Stable key, e.g. "drunk.token", "xaan.X", "snitch.bluffs:7". */
    val id: String,
    val characterId: String,
    val kind: RequirementKind,
    /** Storyteller-voice imperative for the prompt and the checklist. */
    val label: String,
    /** Message when unmet; empty for advisory-only rows. */
    val problem: String,
    val blocking: Boolean = true,
    val satisfied: (GameState, (String) -> Character?) -> Boolean,
)

object SetupRequirements {
    fun all(state: GameState, lookup: (String) -> Character?): List<SetupRequirement>
    fun unmet(state, lookup) = all(state, lookup).filterNot { it.satisfied(state, lookup) }
}
```

Initial table (each row's *content* is owned by the named character doc; the table is the
mechanism):

| id | kind | label | blocking |
|---|---|---|---|
| `drunk.token` | SHOWN_TOKEN | Choose the not-in-play Townsfolk token the Drunk sees | ✔ |
| `lunatic.token` | SHOWN_TOKEN | Choose the Demon token the Lunatic sees (default: the in-play Demon) | ✔ |
| `lunatic.minions` | REMINDER | Point out <k> players as the Lunatic's "Minions" | ✔ |
| `lunatic.bluffs` | BLUFFS | Choose the Lunatic's 3 bluffs (may include in-play characters) | ✔ |
| `marionette.token` | SHOWN_TOKEN | Choose the not-in-play good token the Marionette believes | ✔ |
| `marionette.seat` | SEATING | The Marionette must neighbour the Demon *(see predicate below)* | ✔ |
| `fortuneteller.herring` | REMINDER | Choose the good red herring | ✔ |
| `puzzlemaster.drunk` | REMINDER | Choose the player the Puzzlemaster makes drunk | ✔ |
| `villageidiot.drunk` | REMINDER | Mark one Village Idiot DRUNK (only when 2+ are in play) | ✔ |
| `pixie.mad` | REMINDER | Mark the in-play Townsfolk the Pixie is mad about | ✔ |
| `widow.know` | REMINDER | Mark the good player who knows a Widow is in play | ✔ |
| `grandmother.grandchild` | REMINDER | Mark the Grandchild | ✔ |
| `balloonist.know` | REMINDER | Mark the first player the Balloonist learns | ✔ |
| `eviltwin.twin` | PAIR | Choose the good twin | ✔ |
| `bountyhunter.evil` | ALIGNMENT | Turn one Townsfolk evil, and mark an evil player KNOW | ✔ |
| `snitch.bluffs:<seat>` | BLUFFS | Choose 3 bluffs for <Minion> | ✔ |
| `demon.bluffs` | BLUFFS | Choose the Demon's <3 or 6> bluffs | ✔ (advisory with Legion) |
| `summoner.bluffs` | BLUFFS | Choose the Summoner's 3 bluffs | ✔ |
| `boffin.grant` | GRANT | Choose the not-in-play good ability the Demon has | ✔ |
| `alchemist.grant` | GRANT | Choose the not-in-play Minion ability the Alchemist has | ✔ |
| `xaan.X` | NUMBER | Choose X — the Outsider count, and the night the Xaan poisons | ✔ |
| `damsel.minions` | INFORM | Show every Minion the Damsel token | ✔ |
| `mezepheles.word` | GRANT | Write the Mezepheles' secret word | ✔ |
| `traveller.alignment:<seat>` | ALIGNMENT | Set <name>'s alignment (Travellers are good or evil by choice) | ✔ |
| `godfather.choice` / `hermit.choice` / `balloonist.choice` / `kazali.outsiders` | NUMBER | Record the Outsider count you chose | ✖ advisory |
| `steward/noble/knight/lycanthrope.mark` | REMINDER | *(per that doc)* | ✔ |

Each row drives **both** the blocking check and a generic prompt, replacing the four bespoke
dialogs at `GameShell.kt:347-478`. The prompts run as a **checklist screen after dealing** (before
"Begin night") rather than as four modal interrupts, with a "skip for now" per row and a running
"N of M setup steps done" counter. `HiddenIdentityDialog` (`GameShell.kt:710`) becomes the
`SHOWN_TOKEN`/`GRANT` renderer.

**Marionette neighbour predicate** (fixes defect 23):

```kotlin
fun marionetteNeighbourOk(state: GameState, lookup: (String) -> Character?, seat: Player): Boolean {
    val neighbours = state.seatNeighbours(seat.id)          // over ALL seats, travellers included
    return when {
        state.players.any { it.characterId == "summoner" } ->
            neighbours.any { it.characterId == "summoner" }               // jinx
        state.players.any { it.characterId == "lilmonsta" } ->
            neighbours.any { lookup(it.characterId ?: "")?.team == Team.MINION }   // jinx
        state.players.any { it.characterId == "kazali" } -> true          // created on night 1
        state.players.none { lookup(it.characterId ?: "")?.team == Team.DEMON } -> true
        else -> neighbours.any { lookup(it.characterId ?: "")?.team == Team.DEMON }
    }
}
```

Add a companion `lordOfTyphonLineOk(state, lookup)` for the *"evil characters are in a line, you are
in the middle"* constraint, checked after the night-1 conversions.

Every requirement must be **re-checkable mid-game**, not only at SETUP: a Pit-Hag-created Fortune
Teller needs a red herring, a Kazali-created Widow needs a KNOW token, a mid-game Snitch gives every
Minion bluffs. `SetupRequirements.unmet` should therefore be surfaced as "Grimoire warnings" in the
day/dawn briefing, not only behind the phase button (`GameShell.kt:133-140`).

#### D.3 Stored setup choices

```kotlin
// GameState
/** Setup decisions that must survive the whole game. Key -> value, both stable strings. */
val setupChoices: Map<String, String> = emptyMap(),
```

with typed accessors:

```kotlin
fun GameState.setupInt(key: String): Int? = setupChoices[key]?.toIntOrNull()
fun GameActions.setSetupChoice(state: GameState, key: String, value: String): GameState
```

Keys used: `xaan.X`, `godfather.outsiders`, `hermit.outsiders`, `balloonist.outsiders`,
`kazali.outsiders`, `villageidiot.count`, `boffin.grant`, `alchemist.grant`, `mezepheles.word`,
`lunatic.demon`. Xaan's is the load-bearing one: *"If the number of Outsiders changes during the
game, the Xaan poisons on the night corresponding to the number of Outsiders during setup"* — X is
frozen, so it cannot be recomputed from the grimoire.

### E. UI text (storyteller voice, short, imperative)

- Bag stage, with a shape override:
  *"Kazali: 0 minions in the bag. The Kazali creates 2 minions on the first night — put 2 more good
  characters in instead. Outsiders: your choice, 0 to 2."*
- Bag stage, Lil' Monsta: *"Lil' Monsta is a token, not a seat. Put 3 minions and no demon in the
  bag."*
- Setup checklist header: *"4 of 7 setup steps done. Tap one to run it."*
- DEMON_INFO row, Lunatic swap: *"Show <Demon> the YOU ARE card and their own token — they drew the
  Lunatic token at setup."*
- DEMON_INFO row, bluffs: *"Show the 3 not-in-play good characters. Put the Minions back to sleep
  first — they must not see the bluffs."*
- MINION_INFO row, Marionette: *"Do NOT wake <name> — the Marionette is not woken for anything that
  would confirm they are a Minion."*
- Lunatic row: *"Bluffs for <name> — these MAY include characters that are in play."*
- Snitch row: *"Ana (Poisoner): Sage, Innkeeper, Golem. Bo (Witch): Fool, Monk, Saint."*
- Dawn briefing: *"Show <name> their new character (<X>) — they became it last night."* and
  *"Re-run <name>'s first-night information (<X>)."*
- Star pass: *"<heir> is the Imp from now on. They do NOT act tonight. <dead> now shows as the
  <heir's old character> in the grimoire."*
- Grimoire seat badge: *"believes: Empath"* / *"acts as: Chambermaid (Boffin)"* / *"to reveal"*.

### F. Data changes

- `characters.json`
  - `drunk`: give the bracket-less `setup: true` a display string (e.g. a new optional
    `setupNote` field, or `[A Townsfolk token goes in the bag]`) so the bag chip stops saying
    "Modifies setup" with no explanation. **Do not add `[+1 Outsider]`** — see "Uncertain" above;
    this contradicts `drunk.md` #9 deliberately.
  - `marionette`: normalise the reminder to a single casing and use it everywhere (defect 17).
  - `lunatic`: rename `"Attack 1/2/3"` → `"Chosen 1/2/3"` and add a non-expiring
    `"Fake minion"` global reminder (owned by `lunatic.md`, needed by §D's `lunatic.minions` row).
  - `boffin`: add `remindersGlobal: ["Demon has this ability"]`.
  - `snitch`: add `remindersGlobal: ["Bluffs given"]` so the Storyteller can see the step is done.
  - `alchemist` and `plaguedoctor`: **no drift** — both match the wiki verbatim
    (*"You have a Minion ability. When using this, the Storyteller may prompt you to choose
    differently."* and *"the Storyteller gains a Minion ability"*). Neither says "not-in-play"; see
    the note under §D.2 on the Alchemist's grant.
- `night_guide.json`
  - **Add `MINION_INFO`, `DEMON_INFO`, `DUSK`, `DAWN` entries.** `NightGuide.forStep` is keyed by
    step id and `NightMarkers.MINION_INFO == "MINION_INFO"` (`NightOrder.kt:8`), so this needs **no
    code change** and unblocks the Snitch, Damsel, Magician, Poppy Grower, Marionette, Summoner and
    Lunatic specs at once.
  - The `MINION_INFO` entry must carry the conditional lines: skip with Poppy Grower / Lil' Monsta;
    Magician → point at the Magician too; Damsel → show the Damsel token; Marionette → excluded.
  - Add a `kind: "bluffs"` show type so the bluff card is data-driven rather than gated on
    `step.id == DEMON_INFO` (`NightScreen.kt:783`).
- **Stale data found while writing this** (hand to `mechanics/data-accuracy.md`; each was checked
  against raw wikitext **and** `ThePandemoniumInstitute/botc-release/resources/data/{roles,jinxes,nightsheet}.json`
  on 2026-08-25):
  - `characters.json` `riot`: ability should be *"On day 3, Minions become Riot & nominees die but
    nominate an alive player immediately. This must happen."* — **no `[All Minions are Riot]`**, and
    `setup` should be `false`. Cascades into `Setup.TEAM_WARPING_IDS` and `GameActions.DUPLICABLE`.
  - `night_and_jinxes.json` `marionette × snitch` ("the Demon learns an extra 3"): **not in official
    `jinxes.json`**, and the Snitch wiki page has no jinx section at all. Superseded by the
    Marionette almanac bullet *"not woken due to character abilities that would confirm that they
    are a Minion eg. Snitch, Preacher, Lil' Monsta, Poppy Grower, Hatter, Damsel."*
  - `night_and_jinxes.json` `marionette × poppygrower` and `marionette × damsel`: likewise absent
    from the official Marionette jinx list (9 entries: Alchemist, Balloonist, Huntsman, Kazali,
    Lil' Monsta, Magician, Mathematician, Plague Doctor, Summoner) — same supersession.
  - `night_and_jinxes.json` `boffin × drunk`: repo says *"the Demon thinks they have been given a
    different not-in-play Townsfolk ability"*; official is the flat *"The Demon cannot have the
    Drunk ability."* Same shape for Ogre and Politician, which the repo is missing entirely
    (official Boffin jinxes: Alchemist, Cult Leader, Drunk, Goon, Heretic, Ogre, Politician,
    Village Idiot).
  - `night_and_jinxes.json` `magician × lilmonsta`: repo has the pre-2024 wording; official is
    *"If the Magician is alive, the Storyteller chooses which Minion babysits Lil' Monsta."*
  - `characters.json` `bonecollector`: missing the `*` — official is *"Once per game, at night\*"*.
  - `characters.json` `bootlegger`: typed `fabled`; the wiki now types it **Loric**. Cosmetic.
  - `characters.json` `alchemist` and `plaguedoctor`: **verified correct**, no drift — do not
    "fix" them to say "not-in-play".
- `night_and_jinxes.json` — no order changes needed; the first-night order already places
  `boffin, philosopher, alchemist, poppygrower, magician, MINION_INFO, snitch, lunatic, summoner,
  DEMON_INFO, marionette` exactly as the official sheet does.

---

## Tests to add

Engine tests, in the idiom of `GameActionsTest.kt` / `SetupTest.kt`. Every one fails today.

**Bluffs**

1. *Given* a 10-player game with a Snitch and two Minions, *when* `Bluffs.requirements`, *then* it
   returns 3 requirements (Demon + one per Minion), each `size = 3`, and the Minion ones have
   `stepId == "snitch"`.
2. *Given* a Snitch **and** a Marionette, *when* `Bluffs.requirements`, *then* the Demon requirement
   has `size == 3` and there is **no** requirement for the Marionette seat (the Marionette is not
   woken for the Snitch; the retired "+3" jinx must not be reintroduced).
3. *Given* a Snitch and a Legion game, *when* `Bluffs.requirements`, *then* one Legion seat carries
   **two** requirements (its `DEMON_INFO` set and its `snitch` set), and
   `setBluffs(..., size = 6)` on a single recipient is accepted by the engine — today
   `GameActions.kt:209` truncates every set to 3 unconditionally.
4. *Given* a Lunatic, *when* `Bluffs.candidates(...)` for the Lunatic requirement, *then* the
   in-play Empath is present and the Lunatic's believed Demon is absent.
5. *Given* a Lil' Monsta game, *when* `Bluffs.requirements`, *then* it is empty, and
   `NightOrder.firstNight` contains **no** `MINION_INFO` or `DEMON_INFO` step.
6. *Given* a Summoner game with no Demon, *then* the single requirement targets the Summoner seat
   with `stepId == "summoner"`, and there is no `DEMON_INFO` step.
7. *Given* a legacy save JSON containing `"demonBluffIds":["chef","monk","butler"]` and no
   `bluffSets`, *when* decoded and `migrated()`, *then*
   `bluffSets["demon"]!!.characterIds == listOf("chef","monk","butler")`.

**Identity**

8. *Given* a Drunk shown `empath` **and** a real Empath, *when* `NightOrder.firstNight`, *then*
   there are two `empath` steps with distinct `key`s and `holderId`s, and
   `InfoCalc.compute(..., holderId = drunkSeat)` returns a result whose `abilityMalfunctions` is
   true while the real Empath's is false.
9. *Given* three Village Idiots with one marked DRUNK, *then* `NightOrder.otherNight` emits three
   `villageidiot` rows, and only the DRUNK one reports `abilityMalfunctions`.
10. *Given* a Demon with `FloatingGrant("chambermaid", "boffin", ALIVE_DEMON)`, *then* the night
    sheet contains a `chambermaid` step with `holderId = demonSeat`, `abilityId = "chambermaid"`,
    `sourceId = "boffin"`, and `roleWorks` is true even when the Demon is poisoned but false when
    the **Boffin** is poisoned.
11. *Given* a Philosopher with `AbilityGrant("empath", "philosopher", REPLACE)`, *then*
    `Identity.registersAs(seat) == "philosopher"` (not `"empath"`), `player.isEvil` is unchanged,
    and the night sheet has an `empath` row for that seat and **no** `philosopher` row.
12. *Given* an Alchemist with `AbilityGrant("poisoner", "alchemist", REPLACE)`, *then* the seat is
    **not** listed in `MINION_INFO.playerIds` and `player.isEvil(lookup)` is false.
13. *Given* a Hermit on a script with 3 Outsiders, *then* `Identity.actingRoles` returns the
    Hermit's own role plus 3 granted roles.
14. *Given* an Imp and a Poisoner, *when* `starPass(state, imp, poisoner)`, *then* exactly one live
    seat has `characterId == "imp"`, the dead seat's `characterId == "poisoner"`,
    `deaths.last().characterIdAtDeath == "imp"`, and `NightOrder.otherNight` contains exactly one
    `imp` step whose `holderId` is the living heir.
15. *Given* the same, *when* the heir was the Marionette, *then* the heir has no
    `"Is The Marionette"` reminder and no `"Believes they are …"` note.
16. *Given* a Fang Gu jump, *then* `duplicateLiveCharacterIds` is empty.

**Reveal and change**

17. *Given* a good Chef, *when* `changeCharacter(seat, "eviltwin", PIT_HAG)`, *then*
    `player.isEvil(lookup)` is still **false** (`alignmentFlipped == true`), and an `IdentityRecord`
    with `pendingReveal == true` exists.
18. *Given* a Drunk seat with `"Is the Drunk"` and a "Believes they are the Chef" note, *when*
    `changeCharacter(seat, "undertaker", PIT_HAG)`, *then* both are gone and
    `shownCharacterId == null`.
19. *Given* a Professor resurrection of a Grandmother, *then* `pendingFirstNightReruns` contains
    that seat and `firstNightRerunSteps` returns exactly the `grandmother` first-night step.
20. *Given* a 10-player game with a Lunatic and an Imp, *when* `deal` then
    `applyLunaticTokenSwap`, *then* the Lunatic's `characterShownToPlayerId == "imp"` and the Imp
    seat's `characterShownToPlayerId == "lunatic"`; *when* `revealTrueIdentity(impSeat)`, *then* it
    is `"imp"` again and `characterId` never changed.
21. *Given* a Barber swap of a Drunk and a Chef, *then* the `"Is the Drunk"` seat keeps its own
    believed token — `shownCharacterId` is **not** transplanted (rewrites
    `GameActionsTest.kt:467-478`).

**Setup validation**

22. *Given* a 10-player Kazali bag of `kazali` + 9 Townsfolk/Outsiders, *then* `validateBag` is
    empty; *and* `randomBag` over a Kazali-containing script produces a bag with `minions == 0`.
23. *Given* a 10-player Lil' Monsta bag of 7 Townsfolk + 3 Minions and **no** `lilmonsta` token,
    *then* `validateBag` is empty; *and* a bag containing `lilmonsta` reports
    *"Lil' Monsta is a token, not a seat"* (rewrites `SetupTest.kt:64-71` and
    `GameActionsTest.kt:219-228`).
24. *Given* a 10-player Lord of Typhon bag with `minions == 0`, *then* `validateBag` is empty.
25. *Given* an Atheist bag containing an Imp, *then* `validateBag` reports the Demon — today it
    passes (`GameActionsTest.kt:409-416` only tests the legal direction).
26. *Given* a Legion bag with 2 Legion at 10 players, *then* `validateBag` returns **no blocking
    issue** but `validateBag(...).warnings` contains *"Legion: 2 in bag — most players should be
    Legion (about 6 at 10 players)"*. (This requires `validateBag` to return
    `BagIssues(blocking, warnings)` rather than a bare `List<String>` — a small signature change
    that also carries the Bootlegger's downgrade-everything mode.)
27. *Given* an ordinary 10-player Riot bag (7 Townsfolk, 0 Outsiders, 2 Minions, 1 Riot), *then*
    `validateBag` is empty — today `Setup.TEAM_WARPING_IDS` disables all checking, so a 4-Minion
    Riot bag also passes.
27b. *Given* a 14-player Marionette game (3 Minions at base), *then* `validateBag` requires **2**
    Minion tokens in the bag, not 3, and `validateSetupState` requires one night-1
    "create the third Minion" conversion — *"This ensures that only one Minion token is in the
    bag."*
28. *Given* a Marionette + Summoner game seated Marionette-next-to-Summoner, *then*
    `validateSetupState` is empty (today: *"the Marionette must neighbor the Demon"*).
29. *Given* a Marionette + Lil' Monsta game with the Marionette next to a Minion, *then*
    `validateSetupState` is empty.
30. *Given* an Alchemist with `setupChoices["alchemist.grant"] == "baron"` at 9 players (base
    `5/2/1/1`), *then* `validateBag` accepts `3/4/1/1` and rejects `5/2/1/1` — the granted Baron's
    `[+2 Outsiders]` is folded in via `virtualSetupCharacters`. Today the legal bag is rejected.
31. *Given* a Boffin with `setupChoices["boffin.grant"] == "choirboy"`, *then* `validateBag`
    requires the `king` in the bag.
32. *Given* a Marionette believing they are the Huntsman, *then* `validateBag` requires the
    `damsel` in the bag (Marionette × Huntsman jinx).
33. *Given* a Xaan game, *then* `validateSetupState` reports *"Xaan: choose X"* until
    `setupChoices["xaan.X"]` is set, and afterwards requires exactly X Outsiders in the bag.
34. *Given* a Bounty Hunter game, *then* `validateSetupState` reports *"turn one Townsfolk evil"*
    until exactly one Townsfolk seat has `alignmentFlipped == true`, and requires one
    `bountyhunter:"Know"` token on an evil seat.
35. *Given* two Village Idiots, *then* `validateSetupState` requires exactly one
    `villageidiot:"Drunk"` token; *given* one Village Idiot, *then* it requires none
    (*"If there is only one Village Idiot in play, they are sober"*).
36. *Given* a traveller seat with no alignment set, *then* `validateSetupState` reports it.
37. *Given* `fabledIds = ["sentinel"]`, *then* the bag validator accepts ±1 Outsider — exercised
    through the same call path the wizard uses (`SetupScreen.kt:356` must pass `fabledIds`).
38. *Given* a Baron and a Heretic in the same bag, *then* `validateBag` accepts **either** +1 or +2
    Outsiders (jinx: *"The Baron might only add 1 Outsider, not 2"*).
</content>
</invoke>
