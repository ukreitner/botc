# Choirboy (choirboy) — experimental (Carousel) townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Choirboy>
(King: <https://wiki.bloodontheclocktower.com/King>)

Current ability text (verbatim):

> "If the Demon kills the King, you learn which player is the Demon.
> [+the King]"

`characters.json:1320` matches exactly.

### Setup (verbatim)

> "If the King is not already in play, remove a Townsfolk character token and
> add the King character token."

So the bag size and every team count are unchanged; one Townsfolk slot is
forced to be the King.

### How to run (verbatim)

> "Each night except the first, if the Demon kills the King, put the Demon to
> sleep then wake the Choirboy. Point to the Demon player then put the
> Choirboy to sleep."

### Clarifications (wiki)

- "If the Demon kills the King using their ability, the Choirboy learns which
  player is the Demon."
- The Choirboy learns **which player**, not which character.
- "If the Demon attacks the King but doesn't kill the King, the Choirboy
  doesn't learn who the Demon is." (Protection, Soldier, Monk, Sailor, a
  Zombuul's first death, etc.)
- "Minions that kill the King, such as the Assassin, don't count."
- A Demon **nominating and executing** the King does not count — that is not
  the Demon killing with their ability.
- Drunk/poisoned: the Choirboy still wakes and is given a **wrong** player.
  Wiki example, verbatim: "The Shabaloth kills the King. The drunk Choirboy
  wakes and wrongly learns that the General is the Demon."

### Examples (wiki)

- "The Imp attacks the Empath (dies). Next night, Imp attacks the King
  (protected by Monk, survives). Next night, Imp attacks the unprotected King
  (dies). Choirboy learns which player is the Imp."
- "The Shabaloth kills the King. The drunk Choirboy wakes and wrongly learns
  that the General is the Demon."

### Jinxes

- **Kazali**: "The Kazali can not choose the King to become a Minion."
  (present in the app's data, `night_and_jinxes.json:255`)

### Night order

Other nights only; between the Professor and the Huntsman. The app's ordering
matches the reference dataset (`townsquare` `roles.json`: professor 43,
choirboy 44, farmer 48). Never on the first night.

### Not settled by the wiki (flagged)

- Multiple Demons (Legion, Lord of Typhon, Kazali-created Demons): which
  player is pointed at, and whether a second Demon's kill counts.
- Whether a Demon that changed player *after* the kill (Imp star-pass, Fang Gu
  jump) means the Choirboy is shown the old or the new Demon player. The
  natural reading is "whoever is the Demon when the Choirboy wakes"; treat as
  a storyteller call and say so in the UI.

## What the app does today

Works, in one line each:

- Setup companion is correct. `Setup.kt:75-78` maps `"choirboy" to "king"`;
  `Setup.modifierFor` returns a modifier with `requiredCompanionId = "king"`
  and no team deltas (the `[+the King]` bracket matches no delta regex), which
  is exactly right. `GameActions.validateBag` (`GameActions.kt:480-485`) and
  `randomBag` (`GameActions.kt:366-373`) therefore force the King into the
  bag. Covered by `SetupTest.kt:118`.
- Night-order position is correct (`night_and_jinxes.json:437`, other nights
  only, after `professor`, before `huntsman`).
- `night_guide.json:899` has a good `other` run-book entry, including the
  drunk/poisoned instruction and a reminder about the King setup.
- `StatusEffects.deathNotes` (`StatusEffects.kt:102`) prints
  `"Choirboy (if in play) learns the Demon when the King dies to it."` when
  the storyteller is about to kill the King — this is the one genuinely
  helpful automation, and it fires both from the seat sheet
  (`SeatSheet.kt:238-252`) and from the Demon kill panel
  (`NightScreen.kt:588`).
- The Kazali jinx is in the data and renders in the seat sheet
  (`SeatSheet.kt:222-236`).

Storyteller experience:

- Every night from night 2 onward, a "Choirboy" row appears on the night sheet
  with the text from `characters.json` ("If the Demon killed the King, point to
  the Demon player.") plus the guide prose. It appears **whether or not the
  King died**, and the storyteller must check it off either way.
- `InfoCalc.supports` (`InfoCalc.kt:29-36`) does not include `choirboy`, so the
  expanded panel (`NightScreen.kt:836`) renders no computed answer, no target
  chips, **no impairment caveats**, and no full-screen card. The Demon's name
  is not shown anywhere in the step; the storyteller must scroll the grimoire.
- `night_guide.json` gives the Choirboy `shows: []`, so there is no prepared
  card at all.
- There is no way to record *how* the King died. `SeatSheet.kt:271-279` offers
  only "Died at night" → `DeathCause.DEMON`, "Executed" → `EXECUTION`, "Other
  death" → `STORYTELLER`. A Godfather kill, an Assassin kill, a Gossip kill and
  a Demon kill are all recorded as `DeathCause.DEMON`, and
  `DeathCause.OTHER_NIGHT_DEATH` is only ever produced by
  `GameActions.starPass` (`GameActions.kt:87`).

## Defects and gaps

1. **P0 · The Choirboy step fires every night, with no signal whether it should
   fire at all.** Rules: the Choirboy wakes only when the Demon killed the
   King that night. App: `NightOrder.build` emits the row whenever a Choirboy
   is seated (`NightOrder.kt:142-178`). Repro: seat Choirboy + King, run night
   3 with no deaths — the Choirboy row is still there, unqualified, and the
   dawn guard (`GameShell.kt:145-158`) *forces* the storyteller to tick it
   before advancing. Consequence: a storyteller under pressure ticks it and
   either wakes the Choirboy for nothing or trains themselves to ignore the
   row.

2. **P0 · The trigger cannot be derived because kill causes are not
   attributable.** `DeathCause.DEMON` is used as a catch-all for "died at
   night" (`SeatSheet.kt:271`, `NightScreen.kt:629`), so the engine cannot
   distinguish "the Demon killed the King" from "the Assassin killed the King"
   or "the Godfather killed the King". Rules explicitly exclude minion kills.
   `DeathRecord` (`GameState.kt:77-90`) has no killer field. Until this is
   fixed, no correct automation is possible for the Choirboy (nor for the
   Sage, the Grandmother's grandchild, or the Choirboy-adjacent Godfather
   rules).

3. **P1 · Nothing tells the storyteller who the Demon is at the step.** The
   grimoire knows. `InfoCalc` supports 30 characters and could support this
   one in five lines. Today the storyteller must switch tabs mid-night.

4. **P1 · No impairment warning on the step.** `StepDetailPanel` only renders
   caveats inside `if (InfoCalc.supports(step.id))` (`NightScreen.kt:836`).
   A poisoned Choirboy therefore gets no warning anywhere, and the wiki's
   explicit rule ("wakes and wrongly learns") is silently skipped. The
   `night_guide` prose mentions it, but as prose the storyteller must read
   rather than as a red flag.

5. **P1 · No wrong-player affordance for the impaired case.** The generic
   false-info UI (`NightScreen.kt:903-930`) only offers numbers 0–4 and
   YES/NO. For a "point at a player" ability there is nothing.

6. **P1 · No prompt when the attack fails.** The rules hinge on "attacked but
   didn't die". `deathNotes` warns before the kill, but if the storyteller
   chooses "Death prevented" (`SeatSheet.kt:296-306`) or "No kill"
   (`NightScreen.kt:634`), nothing records that the King was attacked and
   survived — which is exactly the state the storyteller must remember for the
   Choirboy row two steps later.

7. **P2 · The setup guard message is machine-readable, not human-readable.**
   `GameActions.kt:483` produces
   `"choirboy requires the king in the bag [+the King]"` — raw ids and the raw
   bracket, shown verbatim in the "Setup isn't legal yet" dialog
   (`GameShell.kt:551-590`). Should read: "The Choirboy adds the King — put the
   King in the bag in place of another Townsfolk."

8. **P2 · No setup briefing.** Nothing tells the storyteller at setup that the
   Choirboy is in play and therefore that the King exists and that the Demon
   knows the King (the King's own ability). The Drunk/Lunatic/Marionette get
   dedicated setup dialogs (`GameShell.kt:340-480`); the Choirboy/King pairing
   gets none.

9. **P2 · The Kazali jinx is never surfaced at the Kazali's step.** It appears
   in the seat sheet and the jinx dialog only, not in the Kazali's night-1
   minion-creation flow where it is needed.

10. **P2 · No `shows` card.** For a phone-first PWA a "point to a player"
    ability deserves a full-screen `THIS PLAYER IS THE DEMON`-style card, or at
    minimum a highlight of the seat in the grimoire, so the storyteller can
    hold the phone up instead of pointing across a dark room.

11. **P3 · The step's default detail text duplicates the guide.**
    `NightOrder` uses `otherNightReminder` as `detail`, and
    `StepDetailPanel` then prints the guide instructions too
    (`NightScreen.kt:792-801`), producing two near-identical paragraphs.

## Proposed behaviour (spec)

### A. Prerequisite: attributable night kills

Extend `DeathRecord` with `killerPlayerId: Long? = null` and
`killerCharacterId: String? = null`, and add a `DeathCause.MINION_KILL`
(or reuse `OTHER_NIGHT_DEATH` with the killer fields populated). The Demon
kill panel (`NightScreen.kt:625-635`) already knows the demon `holder`, so it
can populate them for free. The seat sheet's "Died at night" button should ask
"who killed them?" with the in-play evil characters plus "Storyteller /
other".

Also record failed attacks: `state.attacksTonight: List<Attack(targetId,
killerPlayerId, killed: Boolean)>`, cleared at dawn. "No kill"/"Death
prevented" writes `killed = false`.

### B. Night step (structured form)

- **when**: `other` only. Never on night 1.
- **wake condition**: a living Choirboy **and** tonight's records contain a
  death of a player whose `characterIdAtDeath` is `"king"` with
  `killerCharacterId` on the Demon team, and that death is not marked
  prevented. If the condition is false the row is **not emitted**; instead,
  emit nothing (the sheet is shorter, which is the point).
  - Storyteller override: a small "the King died to the Demon tonight — run
    the Choirboy" toggle in the step area for house rules / unusual demons, so
    the automation is never a cage.
- **targets**: none to pick. The app computes the answer.
- **immediate effects**: none. No tokens.
- **deferred effects**: none.
- **expiry**: none.
- **information**: `InfoCalc.supports("choirboy")` returns true;
  `choirboy(ctx)` returns
  `headline = "Point to <Name>"`,
  `detail = "<Name> is the <Demon character>. The Choirboy learns the player,
  not the character."`,
  `caveats = impairments(...) + misregistrations(...)`.
  - Multiple demons alive → `headline = "Point to one of: <names>"` plus the
    caveat "More than one Demon is alive — your call which player to show."
  - Demon changed player tonight (star pass / Fang Gu jump) → caveat
    "<Old> was the Demon when the King died; <New> is the Demon now. Show
    whoever is the Demon at this moment unless you rule otherwise."
- **impaired alternative**: when `isImpaired(choirboy)` is true, the step shows
  a `Show a WRONG player` chip row listing every non-Demon player as a
  one-tap "point at" card, sorted good-and-plausible first (alive good players
  with strong claims). Text: "The Choirboy is drunk/poisoned — they still
  wake, and learn a wrong player."
- **visibility**: nothing shown to the Demon or Minions.
- **shows**: add a `GuideShow(label = "The Demon", kind = "token", token =
  "pick")` card so a storyteller can hold the phone up. Prefer a new
  `ShowCard.SeatCard(playerId)` that renders the player's **name** in large
  type, since the Choirboy learns a *player*, not a character.

### C. Briefings

- **Setup**, when a Choirboy is in the bag: `"Choirboy is in play — the King
  is in the bag. The Demon learns who the King is on night 1."`
- **At the Demon's kill step**, if the target is the King: `"That's the King —
  the Choirboy learns you if this kill sticks."` (`deathNotes` already covers
  this; keep it, but make it fire from the attack, not only from the kill.)
- **At dawn**, if the King died to the Demon: `"Choirboy learned the Demon
  last night."` in the storyteller's own dawn briefing (not announced to
  players).

### D. UI text for the step

- Title: `Choirboy`
- Detail (firing): `The Demon killed the King. Wake the Choirboy and point to
  <Name>, then put them back to sleep.`
- Detail (impaired): `The Choirboy is POISONED — wake them and point to a
  wrong player.`
- When not firing, the row does not exist. If the storyteller expands the
  Choirboy from the grimoire instead, show: `The King is alive / the King died
  to <cause>. The Choirboy does not wake tonight.`

### E. Data changes

- `characters.json:1320`: no change to ability text. Consider changing
  `otherNightReminder` to the official phrasing "If the Demon killed the King,
  wake the Choirboy and point to the Demon player." (current text omits the
  wake).
- `night_guide.json:899`: add `shows` (see above); split the "remember the
  King is added at setup" sentence out of the night instructions into a setup
  briefing.
- `night_and_jinxes.json`: no change (Kazali jinx present and correctly
  worded).

## Tests to add

1. `choirboy step is absent when the king is alive`
   Given Choirboy + King seated and no deaths; When the night-2 sheet is
   built; Then no step with id `"choirboy"` exists.

2. `choirboy step is absent when a minion killed the king`
   Given Choirboy + King + Assassin; When the King dies at night with
   `killerCharacterId = "assassin"`; Then no `"choirboy"` step exists on that
   night's sheet.

3. `choirboy step is absent when the king survived the demon attack`
   Given Choirboy + King + Imp + Monk protecting the King; When the storyteller
   records an attack on the King with `killed = false`; Then no `"choirboy"`
   step exists.

4. `choirboy step fires on a demon kill of the king`
   Given Choirboy + King + Imp; When the King dies at night with
   `killerCharacterId = "imp"`; Then the night sheet contains a `"choirboy"`
   step positioned between the Professor's and the Huntsman's slots, and
   `InfoCalc.compute(data, state, "choirboy", choirboySeatId)?.headline`
   equals `"Point to <Imp player's name>"`.

5. `choirboy step is never on the first night`
   Given Choirboy + King; When `nightOrder.firstNight(...)` is built; Then no
   `"choirboy"` step exists (guards against someone adding it to the
   first-night list).

6. `poisoned choirboy still wakes but is flagged`
   Given the state from test 4 plus a `("poisoner","Poisoned")` reminder on
   the Choirboy; Then the step still exists and
   `InfoCalc.compute(...)!!.caveats` contains a POISONED entry.

7. `choirboy setup forces the king into a random bag`
   Given a script containing the Choirboy and 12+ townsfolk; When
   `randomBag(available, 12)` returns a bag containing the Choirboy; Then it
   also contains the King, and the team counts equal
   `Setup.distributionFor(12)` (no Outsider drift). (`SetupTest` covers
   `modifierFor`; this covers the bag builder end to end.)

8. `demon that star-passed after killing the king points at the new demon`
   Given Choirboy + King + Imp; When the Imp kills the King and later that
   night star-passes to a Minion; Then the computed headline names the **new**
   Demon player and the caveats mention the change.
