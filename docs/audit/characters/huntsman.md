# Huntsman (huntsman) — Experimental (Carousel) Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Huntsman> (fetched 2026-08-25), plus
the Damsel page for the paired jinx: <https://wiki.bloodontheclocktower.com/Damsel>.

**Current ability text (wiki):**
> "Once per game, at night, choose a living player: the Damsel, if chosen, becomes a not-in-play Townsfolk. [+the Damsel]"

`characters.json:1408` carries exactly this text — **no drift**.

**How to Run (quoted from the wiki):**
> "While setting up the game, before putting character tokens in the bag, if the Damsel is not already in play, remove a Townsfolk character token and add the Damsel character token.
>
> Each night, wake the Huntsman.
>
> If they shake their head no, put the Huntsman to sleep.
>
> If they point to a player, put the Huntsman to sleep. **The Huntsman loses their ability** — mark them with the **NO ABILITY** reminder and remove their night token from the night sheet. If they chose the Damsel, wake the Damsel, show the **YOU ARE** info token, then a not-in-play Townsfolk token, then put the Damsel to sleep. The Damsel now has this Townsfolk ability, so replace the Damsel character token with this Townsfolk character token."

Timing / edge cases that matter to the storyteller:

- **Both nights.** The Huntsman is on the first-night *and* other-night sheets, and may
  decline every night until they are ready. Official night positions: first night 43,
  other nights 65, with the **Damsel** step immediately after (44 / 66) — that second
  step exists purely to run the transformation.
- **The ability is spent on *any* point**, right or wrong. Declining costs nothing.
  The Huntsman does **not** learn whether the guess was right.
- **The Storyteller chooses the replacement Townsfolk.** It must be a Townsfolk that is
  **not in play**. (The wiki does not restrict it further; it is the ST's choice, exactly
  like the Drunk's shown token.)
- **Drunk/poisoned:** the wiki states the *inverse* case explicitly —
  > "If the Damsel is drunk or poisoned but the Huntsman is sober and healthy, the Damsel can still become a Townsfolk."
  So Damsel impairment does **not** block the change. A drunk/poisoned **Huntsman** has
  no ability, so the Damsel does not change; the standard once-per-game convention (and
  the app's own guide text) is that the use is still spent.
- **Damsel jinx text (from the Damsel page, verbatim):**
  > "If there is a Huntsman in play, and the Huntsman chooses the Damsel at night, the Damsel becomes a not-in-play Townsfolk, and is no longer the Damsel. The Damsel learns which Townsfolk and has that Townsfolk ability from then on."
  Consequence: **after the change, a Minion's public guess of that player does nothing** —
  they are no longer the Damsel, so the evil team's instant win is gone.
- **Jinx (Marionette):**
  > "If the Marionette thinks that they are the Huntsman, the Damsel was added during setup."
  (`night_and_jinxes.json:103-106` renders this as "…the Damsel was added." — acceptable.)
- **Examples (wiki):** Huntsman waits until night 2, chooses the Damsel, who becomes the
  Undertaker. / Huntsman chooses Lachlan (a General); nothing happens and the ability is lost.

**Unresolved by the wiki (flagged, not guessed):** whether the transformed Damsel is
given the first-night-only information of their new character (e.g. if they become a
Washerwoman or a Noble). The Huntsman page does not say. The app should *ask* the ST
rather than silently decide.

## What the app does today

Setup — **works**:
- `Setup.kt:76` `COMPANIONS["huntsman"] = "damsel"`, `Setup.kt:219` `if (character.id == "huntsman") outsiders += 1`.
  So `[+the Damsel]` correctly converts one Townsfolk slot into an Outsider slot.
- `GameActions.randomBag` (`GameActions.kt:364-395`) forces the Damsel in, and
  `validateBag` (`GameActions.kt:480-485`) reports "huntsman requires the damsel in the
  bag" for hand-built games, which `validateSetupState` (`GameActions.kt:503-511`) reuses
  at the SETUP→NIGHT boundary (`GameShell.kt:132-139`). Covered by
  `SetupTest.kt:114-118` and `GameActionsTest.kt:241-255`.

Night:
- `characters.json:1408-1421`: `setup: true`, `reminders: ["No Ability"]`, first/other
  night reminder text present.
- Night order: `night_and_jinxes.json:338` (first night, index 43) and `:438` (other
  nights, index 65). Damsel sits immediately after at 44 / 66.
- `NightOrder.build` (`NightOrder.kt:130-181`) emits a plain row: title "Huntsman",
  detail = the `firstNightReminder`/`otherNightReminder` string.
- `NightGuide` prose + one show card at `night_guide.json:1036-1059`. The card is
  `{"label":"Damsel becomes","kind":"token","text":"YOU ARE","token":"pick"}`, rendered
  by `StepDetailPanel` (`NightScreen.kt:801-833`) into `GuideShowDialog`
  (`NightScreen.kt:366-451`).
- `QuickResolutions` (`NightScreen.kt:462-527`) has **no** `"huntsman"` branch, so the
  step offers no target picker and no resolution.
- `InfoCalc.supports("huntsman")` is false (`InfoCalc.kt:30-35`), so the entire
  `if (InfoCalc.supports(step.id))` block (`NightScreen.kt:835-933`) — including every
  drunk / poisoned / Marionette / Vortox caveat — is skipped for this step.
- `NightToolTray` (`NightScreen.kt:193-352`) offers a "Mark spent" chip because the
  ability text starts with "Once per game" (`NightScreen.kt:207`, `:259-273`); tapping it
  places `PlacedReminder("huntsman", "No ability")`.

Storyteller experience today: read the prose, wake the Huntsman, remember whether they
pointed, tap "Mark spent", decide by eye whether the seat they pointed at is the Damsel,
scroll to the Damsel step, open its "New character" card, type/search a Townsfolk from a
list that puts **in-play** characters first, show it, then close the night sheet, open the
Damsel's seat sheet and change their character by hand.

## Defects and gaps

1. **P1 · No Huntsman guess resolver — the whole interaction is manual.**
   The rules define a single atomic action (point → ability spent → if it was the Damsel,
   she becomes a chosen not-in-play Townsfolk and her grimoire token is replaced). The app
   offers none of it: `QuickResolutions` (`NightScreen.kt:462-527`) has no `"huntsman"`
   case, so the ST performs 5+ manual steps across two screens.
   *Repro:* Night 1, expand the Huntsman step. There is no player picker at all.

2. **P1 · The replacement-Townsfolk picker sorts and filters exactly backwards.**
   `GuideShowDialog` (`NightScreen.kt:400-425`) builds candidates from every non-Fabled
   script character and applies `.sortedByDescending { it.id in inPlayIds }`, then labels
   the first section "In play". The Huntsman needs a **not-in-play Townsfolk**; the dialog
   presents in-play characters first and includes Outsiders, Minions and Demons.
   *Repro:* Huntsman step → "» Damsel becomes" → the first chips offered are the
   characters already in the grimoire.

3. **P1 · The Huntsman keeps a night row after the ability is spent.**
   The wiki says "remove their night token from the night sheet". `NightOrder.build`
   (`NightOrder.kt:130-181`) emits the row for any holder with no check for a
   `("huntsman","No ability")` reminder, so on every subsequent night the ST sees an
   unchecked Huntsman step — and the Dawn guard (`GameShell.kt:145-158`) refuses to
   advance until it is ticked.
   *Repro:* Night 1 tap "Mark spent"; Dawn; Dusk → Night 2 still lists "Huntsman".

4. **P1 · No impairment warning on this step at all.**
   `InfoCalc.impairments()` (`InfoCalc.kt:132-153`) already knows about poison, drunkenness,
   "No ability", the Drunk and the Marionette, but it is only reachable via
   `InfoCalc.compute` → `commonCaveats` (`InfoCalc.kt:157-166`), which returns null unless
   `supports(id)`. The Huntsman is not supported, so a **poisoned Huntsman** or a
   **Marionette who thinks they are the Huntsman** produces a step that reads like a
   working ability.
   *Repro:* Poison the Huntsman with the Poisoner tray token, then expand the Huntsman
   step — no red warning, and the guide still says "the Damsel becomes…".

5. **P1 · The character change loses nothing but keeps everything.**
   `GameActions.assignCharacter` (`GameActions.kt:46-53`) resets `shownCharacterId` but
   leaves `reminders` untouched. Changing the Damsel to a Townsfolk therefore leaves the
   Damsel's own tokens on the seat — `("damsel","Guess Used")`, and any poison marker
   placed for the Spy/Widow-Damsel jinx (`night_and_jinxes.json` spy+damsel / widow+damsel).
   The ex-Damsel then reads as poisoned for the rest of the game.
   *Repro:* Place "Guess Used" on the Damsel, then Seat sheet → Change character →
   Undertaker. The token is still there.

6. **P1 · Nothing tells the ST that the Damsel's loss condition is gone.**
   After the change, a Minion's public guess of that player is inert (Damsel jinx text).
   `StatusEffects.nominationWarnings` (`StatusEffects.kt:143-167`) has no Damsel handling
   at all, and there is no day-time place to record a Minion guess, so the ST is carrying
   this in their head.

7. **P2 · The Damsel step is not linked to the Huntsman's choice.**
   `night_and_jinxes.json:339/439` always emits a Damsel row whose guide (`night_guide.json:1253`)
   begins "Only act if the Huntsman chose the Damsel tonight." The app knows nothing about
   tonight's Huntsman choice, so the row is unconditional noise every night and easy to
   tick past on the night it matters.

8. **P2 · The guess is never recorded.**
   There is no log entry, no seat note, no "Huntsman guessed Lachlan on night 2". This
   matters when the Huntsman claims publicly ("I've used it, I got nothing"), and when the
   ST needs to confirm the ability really is spent after an undo/redo.

9. **P2 · The wiki's Damsel-impairment clarification is contradicted by the guide.**
   `night_guide.json:1038` says "If the Huntsman is drunk or poisoned, the Damsel does not
   change character, but the use is still spent" (correct) but never states the wiki's
   explicit inverse: a drunk/poisoned **Damsel** still changes. An ST who has poisoned the
   Damsel (Spy/Widow jinx!) will very plausibly get this wrong.

10. **P3 · Step title reads as a wake for a spent ability.**
    Once spent the row should render as "Huntsman — ability used (night 2, guessed Lachlan)"
    and auto-tick, not as an actionable step.

## Proposed behaviour (spec)

**Structured night step**

- **when:** both first and other nights.
  Wake condition: holder alive **and** holder has no `("huntsman","No ability")` reminder.
  If the reminder is present, emit no row at all (or a collapsed, auto-done "ability used"
  row showing night and target — preferred, so the ST can undo).
- **targets:** 0 or 1. Constraint: **alive**, may be self. Picker default sort: alive
  players in seat order; do **not** highlight the Damsel (it must not leak on screen next
  to the ST). Offer an explicit "Declined — no guess" button that ticks the step and
  leaves the ability intact.
- **immediate effects (on any guess):**
  - place exclusive `("huntsman","No ability")` on the Huntsman;
  - record `huntsmanGuess = (cycle, targetPlayerId)` in game state (new field, or a
    structured log entry) so the row can say what was guessed;
  - if the Huntsman **is impaired** (`StatusEffects.isImpaired`, or is the Drunk /
    Marionette / holds a "No ability" token): stop here, show
    "The Huntsman is drunk/poisoned — the Damsel does **not** change. The use is spent.";
  - else if the target's `characterId == "damsel"`: continue to the transformation.
- **transformation (sober Huntsman chose the Damsel):**
  - open a picker restricted to `team == TOWNSFOLK && id !in inPlayIds` from the resolved
    script, sorted alphabetically, **not-in-play only** (there is no in-play section);
  - on confirm, in one undoable action:
    1. show the full-screen card `YOU ARE` + chosen token (existing `ShowCard.CharacterCard`);
    2. `assignCharacter(damselSeat, chosenId)`;
    3. **drop every reminder whose `sourceId == "damsel"`**, plus any reminder the ST
       marked as Damsel-jinx poison (offer them as tick-boxes rather than guessing);
    4. append a log entry "Night N — Huntsman (Ana) chose Bo; the Damsel became the Undertaker".
  - The Damsel's impairment is **irrelevant** — the change happens anyway (wiki).
  - If the new character has first-night-only information, prompt once:
    "Bo is now the Undertaker. Give them their character's first-night information now?"
    with Yes / No. (The wiki does not settle this; make it the ST's explicit choice and
    record the answer.) If Yes and `InfoCalc.supports(newId)`, jump straight into that
    character's info panel for this seat.
  - **Marionette safeguard:** if the "Huntsman" is a Marionette (`characterId == "marionette"`,
    `shownCharacterId == "huntsman"`), run the whole flow as theatre — spend nothing, change
    nothing — and label the step "Marionette believing they are the Huntsman — no effect".
- **deferred effects:** none. Nothing to expire, nothing at dawn.
- **expiry:** `("huntsman","No ability")` never expires. Add nothing to `EXPIRES_AT_DAWN`/
  `EXPIRES_AT_DUSK` (`GameActions.kt:218-242`).
- **information:** the Huntsman learns **nothing** — the step must say so in one line so the
  ST does not invent feedback. The Damsel learns their new character (YOU ARE + token).
- **visibility:** nothing is shown to the Demon or Minions. Note in the step that the
  Minions were told a Damsel is in play on night 1 and are *not* told she changed.
- **day-time inputs:** add a day-screen "Minion guessed the Damsel" recorder (shared with
  the Damsel audit). Once the Damsel has transformed, that recorder must answer
  "no effect — that player is no longer the Damsel", and `nominationWarnings` should stop
  warning about the Damsel.
- **interactions/jinxes:**
  - **Marionette** — jinx already in data (`night_and_jinxes.json:103-106`); it only means
    the Damsel was added at setup, which the bag builder already does. Surface it in the
    setup screen so an ST hand-building a Marionette-Huntsman game adds the Damsel.
  - **Spy / Widow + Damsel** — the Damsel is poisoned; after transformation she is not the
    Damsel, so the jinx poison must be cleared (see defect 5).
  - **Pit-Hag + Damsel** — "the Storyteller chooses which player it is"; a Pit-Hag-created
    Damsel becomes a legal Huntsman target from that night on.
  - The chosen Townsfolk's **setup ability does not apply** (no bag change, no companion
    added) — if the ST picks e.g. the Choirboy, do not add a King. Warn instead:
    "Setup abilities don't apply to a mid-game character change."

**UI text for the step**

- Not spent: `Huntsman — Ana. Point to a player, or decline. Any guess spends the ability.`
- Buttons: `Declined` · player chips · after pick: `Ana guessed Bo — ability spent`.
- If the target was the Damsel: `Bo is the Damsel. Choose a not-in-play Townsfolk for them to become.`
- If not: `Bo is not the Damsel — nothing happens. The Huntsman's ability is spent.`
- Impaired banner: `! Ana is POISONED (Poisoner) — the Damsel does NOT change. The use is still spent.`

**Data changes**

- `night_guide.json:1036-1059` — add the wiki clarification "A drunk or poisoned **Damsel**
  still becomes the Townsfolk; only an impaired **Huntsman** stops the change", and
  "The Huntsman learns nothing about whether the guess was right."
- No `characters.json` change needed (text and reminders already match the wiki).

## Tests to add

1. `GIVEN` a Huntsman and a Damsel in play `WHEN` the Huntsman guesses the Damsel while
   sober `THEN` the Damsel's `characterId` is the chosen not-in-play Townsfolk, the
   Huntsman holds `("huntsman","No ability")`, and no reminder with `sourceId == "damsel"`
   remains on that seat.
2. `GIVEN` a Huntsman marked `("huntsman","No ability")` `WHEN` the night sheet is built
   for cycle 2 `THEN` no `huntsman` step is produced (or the step is flagged spent).
   *Fails today* — `NightOrder.build` ignores the reminder.
3. `GIVEN` a poisoned Huntsman `WHEN` they guess the Damsel `THEN` the Damsel's
   `characterId` is still `"damsel"` and the Huntsman is still marked spent.
4. `GIVEN` a sober Huntsman and a **poisoned** Damsel `WHEN` the Huntsman guesses her
   `THEN` the change still happens (wiki clarification).
5. `GIVEN` a Huntsman guessing a non-Damsel `THEN` no character changes anywhere and the
   Huntsman is marked spent.
6. `GIVEN` a Damsel who has become a Townsfolk `WHEN` `StatusEffects.nominationWarnings`
   (or the new Damsel-guess recorder) is asked about a Minion guessing that player
   `THEN` the result is "no effect", not an evil win.
7. `GIVEN` a Marionette whose `shownCharacterId == "huntsman"` `WHEN` the huntsman
   resolver runs `THEN` state is unchanged (no spend, no transformation).
8. `GIVEN` a 12-player bag containing the Huntsman `THEN` the adjusted distribution has
   one more Outsider and one fewer Townsfolk than the base, and the Damsel is present.
   *(Already covered by `SetupTest.kt:114` / `GameActionsTest.kt:241` — keep.)*
