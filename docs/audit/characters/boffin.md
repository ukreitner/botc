# Boffin (boffin) — Experimental Minion

## Official rules (sources)

Source: https://wiki.bloodontheclocktower.com/Boffin (Character Text, Summary,
How to Run, Examples, Tips & Tricks, Jinxes), fetched 2026-08-25.

**Current ability text (quote):**
> "The Demon (even if drunk or poisoned) has a not-in-play good character's ability. You both know which."

`characters.json:1754` matches this verbatim. No drift.

**Summary bullets (quotes):**
- "While the Boffin is alive, the Demon has a single Townsfolk ability or Outsider ability."
- "If the Demon is drunk or poisoned, the Demon keeps this good ability. If the Boffin is drunk or poisoned, the Demon temporarily loses this good ability."
- "If the Demon dies and has an ability that functions while dead, such as the Sweetheart, the Demon keeps this ability."
- "If a new Demon is created, such as via a Scarlet Woman or a Barber, this new Demon has an ability from the Boffin."
- "If there are multiple Demons alive, only one alive Demon has an ability from the Boffin."
- "If the Demon has an ability that modifies the setup, such as a Choirboy, these changes are made during setup, as normal."
- "Both the Demon and the Boffin learn which good ability the Demon has."
- "The not-in-play character may be 1 of the Demon's 3 bluffs."
- **"The Demon also wakes at night at the time that the good character would normally wake."**

**How to Run (paraphrase of the wiki section; the wiki page rejected a verbatim
dump but two independent fetches agree on the content):**
- During setup, apply any bracketed setup changes carried by the granted good
  character (and by the Demon), then place the second (good) character token
  next to the Demon's token in the grimoire.
- First night, at the Boffin's night-order position: wake the Boffin **and** the
  Demon together. Show THIS CHARACTER SELECTED YOU, then the Boffin token, then
  the granted good character token. Both players now know which ability it is.
- For the rest of the game, treat the Demon as having *both* their Demon ability
  and the granted good ability, **including waking the Demon at the granted
  character's own night-order slot**.
- Self-targeting drawbacks of the granted ability (e.g. Sailor's own
  drunkenness) apply only to the *granted ability*, not to the Demon's Demon
  ability.

**Choice of ability.** It is a Storyteller choice made at setup (the wiki's
Tips & Tricks treat it as coordinated with the evil team); it must be a
**not-in-play Townsfolk or Outsider**.

**Examples (from the wiki):** Imp with the Virgin ability executes an Alsaahir
who nominates it; a Lord of Typhon with the Chambermaid ability wakes nightly
until the Boffin is made drunk on night 4, at which point the Demon stops
waking; a dead Kazali with the Banshee ability keeps nominating/voting twice,
and when a Scarlet Woman becomes the new Kazali the ability transfers.

**Jinxes (wiki, verbatim):**
- Alchemist — "If the Alchemist has the Boffin ability, the Alchemist does not learn what ability the Demon has."
- Cult Leader — "If the Demon has the Cult Leader ability, they can't turn good due to this ability."
- **Drunk — "The Demon cannot have the Drunk ability."**
- Goon — "If the Demon has the Goon ability, they can't turn good due to this ability."
- Heretic — "The Demon cannot have the Heretic ability."
- Ogre — "The Demon cannot have the Ogre ability."
- Politician — "The Demon cannot have the Politician ability."
- Village Idiot — "If there is a spare token, the Boffin can give the Demon the Village Idiot ability."

**Night order.** First night only, position 7 of 76, between `thief` and
`stormcatcher`, i.e. *before* MINION_INFO — `night_and_jinxes.json:302`
(firstNight list). Correct. No other-night entry — also correct for the Boffin
itself, but the *granted* ability must run at its own slot every night.

## What the app does today

- `characters.json:1754` — ability text correct; `setup:false`;
  `firstNightReminder` = "Wake the Boffin and the Demon. Show the 'This
  character selected you' & Boffin tokens, then the not-in-play good character
  token."; `reminders: []`, `remindersGlobal: []` — **no token at all**.
- `night_guide.json:1315` — good first-night prose, ending "…run the granted
  ability at its usual place in the night order for the rest of the game." Two
  show-cards: "THIS CHARACTER SELECTED YOU" (self token) and "THE DEMON HAS
  THIS ABILITY" (`token: "pick"`, so the ST picks the granted character in
  `GuideShowDialog`, `NightScreen.kt:363-451`).
- `night_and_jinxes.json:302` — first-night order slot, correct.
- `night_and_jinxes.json:274-288` — three jinxes only (heretic, cultleader,
  drunk). The drunk jinx text is stale (see D8).
- Nothing else in the codebase mentions the Boffin. There is **no engine state**
  for the grant: no field on `GameState`/`Player`, no reminder, no note.

**Storyteller experience today:** on night 1 you get one step that tells you to
wake both players and a "pick a character" show card. Whatever you pick is not
recorded anywhere. From night 2 onward the Boffin has no night step and the
granted ability never appears on the night sheet, so you must remember, every
single night, that (say) the Vortox also wakes at the Chambermaid's slot, who
to wake, and what number to show. `InfoCalc` is never consulted for the Demon.
Nothing warns you when the Boffin dies or is poisoned that the Demon just lost
the ability, and nothing stops you granting the Drunk/Heretic/Ogre/Politician.

## Defects and gaps

1. **P0 · The granted ability never appears on the night sheet.**
   Rules: "The Demon also wakes at night at the time that the good character
   would normally wake." App: `NightOrder.build` groups seats by
   `Player.nightRoleId` (`GameState.kt:39-44`), which for the Demon is just the
   Demon's own id, so no extra step is ever produced
   (`NightOrder.kt:120-179`). Repro: Boffin + Imp, grant Empath; night 2 the
   night sheet has no Empath row and no prompt to wake the Imp for Empath info.
   Consequence: the storyteller silently drops the Demon's good ability — the
   exact class of failure the user reported for Pukka/Lunatic.

2. **P0 · The grant is never recorded, so nothing downstream can use it.**
   The ST picks a token inside a transient `GuideShowDialog`
   (`NightScreen.kt:363-451`) whose `tokenId` is local composable state and is
   discarded on dismiss. No `GameState` field, no reminder token, no seat note.
   Repro: pick "Chambermaid" on night 1, reopen the step — the picker is empty
   again. Undo/redo and app restart lose it too.

3. **P0 · No validation of illegal grants (jinxes + "not-in-play good").**
   Rules forbid Drunk, Heretic, Ogre, Politician; require a *not-in-play*
   Townsfolk/Outsider; Village Idiot only "if there is a spare token". The
   `GuideShowDialog` character list is every non-Fabled script character
   (`NightScreen.kt:404-407`), sorted so **in-play characters come first**
   (`NightScreen.kt:409`) — the app actively steers the ST toward an illegal
   choice. `validateSetupState` (`GameActions.kt:503-560`) has no `boffin` case.

4. **P1 · Boffin death / poisoning does not warn that the Demon loses the
   ability.** Rules: "While the Boffin is alive…"; "If the Boffin is drunk or
   poisoned, the Demon temporarily loses this good ability."
   `StatusEffects.deathNotes` (`StatusEffects.kt:52-129`) has no `boffin` case,
   and there is no poison-side notification anywhere. Repro: kill the Boffin on
   night 3 — no note, and the Demon's granted ability (which the app does not
   track anyway) silently should have stopped.

5. **P1 · New Demon does not inherit / re-roll the grant.**
   Rules: "If a new Demon is created… this new Demon has an ability from the
   Boffin" (and it may be a *different* ability). `GameActions.starPass`
   (`GameActions.kt:78-96`) and `swapCharacters` (`GameActions.kt:99-116`) copy
   characters only. Repro: Imp star-passes to the Poisoner — nothing tells the
   ST to re-grant or carry over the good ability.

6. **P1 · Setup consequences of the granted character are not applied.**
   Rules: "If the Demon has an ability that modifies the setup, such as a
   Choirboy, these changes are made during setup, as normal." So granting e.g.
   the Choirboy requires the King in the bag; granting the Huntsman requires the
   Damsel. `Setup.modifierFor` (`Setup.kt:121`) is only run over the *bag*
   (`GameActions.validateBag`), and the granted character is by definition not
   in the bag. Repro: grant Choirboy with no King in play — no warning.

7. **P2 · The grant is not offered as a Demon bluff.**
   Rules: "The not-in-play character may be 1 of the Demon's 3 bluffs."
   `BluffsSheet.kt` / `GameActions.suggestBluffs` (`GameActions.kt:121-128`)
   know nothing about it.

8. **P2 · Stale jinx text and 5 missing jinxes.**
   `night_and_jinxes.json:284` says the Drunk jinx is "If the Boffin gives the
   Demon the Drunk ability, the Demon thinks they have been given a different
   not-in-play Townsfolk ability." The current wiki text is "The Demon cannot
   have the Drunk ability." Missing entirely: Alchemist, Goon, Ogre, Politician,
   Village Idiot.

9. **P2 · The Boffin+Demon joint wake has no UI support.**
   The step lists only the Boffin as holder (`NightStep.playerIds`), so the seat
   chips, the "Show token" tray and the tray's reminder targets are all
   Boffin-centric; nothing names the Demon on the step row. On a phone the ST
   has to leave the step to find who the Demon is.

10. **P3 · Boffin has no reminder tokens.** The physical game marks the grant by
    placing the good character's token next to the Demon. `reminders: []` at
    `characters.json:1754` means the tray shows "No reminder tokens for this
    character" (`NightScreen.kt:224-231`).

## Proposed behaviour (spec)

### New engine state

Add to `GameState`:

```kotlin
/** Boffin: the not-in-play good character whose ability the Demon has. */
val boffinGrantId: String? = null,
```

(One field is enough: "only one alive Demon has an ability from the Boffin",
and the *current* holder is always derivable as the alive Demon.)

Add a derived helper in `StatusEffects` or a new `Grants.kt`:

```kotlin
/** The seat that currently exercises the Boffin's granted ability, or null. */
fun boffinGrantHolder(state, lookup): Player?   // alive Demon, if boffin alive & !impaired
fun boffinActive(state, lookup): Boolean        // boffin seat alive && !isImpaired(boffin)
```

### Setup

- **when:** SETUP phase, if any seat has `characterId == "boffin"`.
- Show a blocking-but-skippable `HiddenIdentityDialog` exactly like the Drunk /
  Lunatic / Marionette prompts (`GameShell.kt:378-479`), titled
  **"The Boffin is in play"**, explanation *"Which not-in-play good ability
  does the Demon have?"*.
- **options:** script characters with `team == TOWNSFOLK || team == OUTSIDER`,
  `id !in inPlayIds`, minus `drunk`, `heretic`, `ogre`, `politician`;
  `villageidiot` allowed (label it "needs a spare token").
  Sort alphabetically — never `sortedByDescending { in play }`.
- **on pick:** `state.copy(boffinGrantId = id)`; place
  `PlacedReminder("boffin", "Demon has <Name>")` on the **Demon's** seat
  (exclusive); set the Demon's seat note to "Boffin: has the <Name> ability".
- `validateSetupState` additions when `boffin` is in the bag:
  - `boffinGrantId == null` → "Boffin: choose the not-in-play good ability the Demon has".
  - granted id in play → "Boffin: the granted character must not be in play".
  - granted id in {drunk, heretic, ogre, politician} → "Boffin cannot grant the <Name> (jinx)".
  - granted character has a setup modifier (`Setup.modifierFor != null`) →
    surface its bracket text as an advisory issue, and enforce
    `requiredCompanionId` (Choirboy→King, Huntsman→Damsel) the same way the bag
    validator does (`GameActions.kt:483-488`).

### Night 1 (Boffin's own step)

- **when:** first night only; wake condition: Boffin seat exists (alive or not —
  it is night 1).
- **targets:** none (the grant is already chosen at setup; allow re-picking here).
- **immediate effects:** none beyond confirming the grant.
- **visibility:** wake the Boffin **and** the Demon together. Step row must list
  *both* seats: `playerIds = boffinSeats + demonSeats`. Show cards, in order:
  1. `THIS CHARACTER SELECTED YOU` + Boffin token,
  2. `THE DEMON HAS THIS ABILITY` + the granted character token, prefilled from
     `state.boffinGrantId` (no picker needed once set).
- **UI text:** `Wake {Boffin} and {Demon} together. Show SELECTED YOU + the
  Boffin token, then the {Granted} token. The Demon has this ability even while
  drunk or poisoned — but loses it while the Boffin is drunk, poisoned or dead.`

### Every night thereafter (the granted ability)

`NightOrder.build` must inject a synthetic step for `boffinGrantId`:

- Compute `granted = state.boffinGrantId` and `holder = alive Demon seat`.
- If `granted != null && holder != null`, and the granted character has a
  non-blank `firstNightReminder`/`otherNightReminder` **or** appears in the
  relevant order list, insert a step at the granted character's own index in
  that order list with:
  - `id = "boffin:" + granted` (distinct id so `nightStepsDone` and the
    `expandedId` machinery keep working, and so a real in-play copy of that
    character is not merged with it),
  - `title = "<Granted> (Demon, via Boffin)"`,
  - `detail` = the granted character's night reminder text,
  - `playerIds = listOf(demonSeatId)`.
- Suppress the step when `!boffinActive(state)` (Boffin dead or impaired) and
  instead, on the Demon's own step, append: `Boffin is dead/poisoned — the Demon
  does NOT have the <Granted> ability tonight.`
- `InfoCalc.compute` must be callable with `characterId = granted`,
  `holderId = demonSeatId` so the Demon gets real Chambermaid/Empath/etc.
  numbers. `StepDetailPanel` (`NightScreen.kt:836`) keys off `step.id`; change it
  to key off a new `NightStep.abilityId` field (defaulting to `id`) so
  `"boffin:chambermaid"` resolves to `chambermaid`.
- `NightGuide.forStep` likewise should fall back to `abilityId`.
- Caveat to add in `InfoCalc.impairments` when the holder is the Demon via
  Boffin: *"The Demon has this ability via the Boffin — it works even while the
  Demon is drunk or poisoned; it fails only if the Boffin is drunk, poisoned or
  dead."* This must **suppress** the normal "Demon is poisoned → give false
  info" reasoning for this step.

### Deferred effects / triggers

- **On Boffin death** (`StatusEffects.deathNotes`, `boffin` case):
  `"Boffin dies — the Demon (<name>) loses the <Granted> ability from now on."`
  Also drop the `boffin:<granted>` night step from that night onward.
- **On Boffin becoming drunk/poisoned:** the night sheet must show, on the
  Demon's own step, `"Boffin is <poisoned/drunk> — no <Granted> ability tonight."`
- **On a new Demon** (`starPass`, `swapCharacters`, Scarlet Woman, Pit-Hag,
  Barber, Kazali): if `boffinGrantId != null`, raise a prompt
  *"New Demon — the Boffin grants an ability again. Keep <Granted>, or choose a
  different not-in-play good ability?"* with Keep / Re-pick.
- **On Demon death:** keep the grant only if the granted character functions
  while dead (Banshee, Sweetheart, Ravenkeeper-on-death...). Simplest correct
  behaviour: keep `boffinGrantId`, stop generating the night step when the Demon
  is dead **unless** the granted id is in a small `WORKS_WHILE_DEAD` set
  (`banshee`, `sweetheart`, `moonchild`, `farmer`, `professor`? — list to be
  confirmed); always show the note "check whether <Granted> still functions
  while dead".

### Expiry

- `boffinGrantId` never expires. The `boffin:Demon has <Name>` reminder never
  expires (do **not** add it to `EXPIRES_AT_DAWN`/`EXPIRES_AT_DUSK`).

### Bluffs

`BluffsSheet` should offer `boffinGrantId` as a one-tap bluff candidate, tagged
"Boffin grant — legal bluff".

### Data changes

- `characters.json:1754`: add `"remindersGlobal": ["Demon has this ability"]`
  so the tray can place a marker on the Demon's seat.
- `night_and_jinxes.json` jinxes: correct the Drunk jinx to *"The Demon cannot
  have the Drunk ability."* and add Alchemist, Goon, Ogre, Politician, Village
  Idiot entries.
- `night_guide.json:1315`: change the second show card's `token` from `"pick"`
  to a new kind that reads `state.boffinGrantId`; add an `other` entry? No —
  the Boffin has no other-night action; the *granted* step supplies its own
  guide via `abilityId`.

### UI text for the step

- First night row: `Boffin — wake {Boffin} + {Demon}. Grant: {Granted}.`
- Injected nightly row: `{Granted} (Demon via Boffin) — {Demon}` with the
  granted character's normal instructions.
- Banner on the Demon's step when the Boffin is impaired/dead:
  `No Boffin ability tonight — {Boffin} is {dead/poisoned}.`

## Tests to add

1. *Given* a 10-player game with Boffin + Imp and `boffinGrantId = "chambermaid"`,
   *when* `NightOrder.otherNight` is built, *then* a step with
   `id == "boffin:chambermaid"` exists at the Chambermaid's index and
   `playerIds == listOf(impSeatId)`.
2. *Given* the same state, *when* the Boffin seat is killed, *then*
   `NightOrder.otherNight` contains no `boffin:*` step and
   `StatusEffects.deathNotes(boffinSeat)` contains "loses the Chambermaid ability".
3. *Given* the Boffin has a `Poisoned` reminder, *when* the night sheet is built,
   *then* no `boffin:*` step appears and the Imp's own step detail contains
   "no Chambermaid ability tonight".
4. *Given* the Imp is poisoned but the Boffin is healthy, *then* the
   `boffin:chambermaid` step still appears and
   `InfoCalc.compute(..., "chambermaid", impSeatId, targets)` returns the TRUE
   count with a caveat that the Boffin grant ignores the Demon's own poisoning.
5. *Given* `boffinGrantId = "drunk"`, *when* `validateSetupState` runs, *then*
   issues contain "Boffin cannot grant the Drunk".
6. *Given* `boffinGrantId = "empath"` and the Empath is already in play, *then*
   `validateSetupState` reports "the granted character must not be in play".
7. *Given* `boffinGrantId = "choirboy"` and no King in the bag, *then*
   `validateSetupState` reports the missing King.
8. *Given* Boffin + Imp + Scarlet Woman and `boffinGrantId = "empath"`, *when*
   `GameActions.starPass(imp -> scarletwoman)` runs, *then* `boffinGrantId` is
   preserved and a "re-grant" advisory is produced for the new Demon.
9. *Given* `boffinGrantId = "sailor"`, *then* the Demon does not register as
   drunk (`StatusEffects.isImpaired(demon) == false`) — the self-drunkening
   applies to the granted ability only.
