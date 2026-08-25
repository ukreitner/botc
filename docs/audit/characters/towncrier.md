# Town Crier (towncrier) — Sects & Violets Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Town_Crier> (fetched 2026-08-25).

Current ability text:

> "Each night*, you learn if a Minion nominated today."

**How to run (quoted, as returned by the wiki):**

> - "Each dawn, mark the Town Crier with the **MINIONS NOT NOMINATED** reminder,
>   and remove the **MINION NOMINATED**" [reminder, if present]
> - "Each day, if any Minion makes a nomination, replace the **MINIONS NOT
>   NOMINATED** reminder with the" [**MINION NOMINATED** reminder]
> - "Each night except the first, wake the Town Crier. If the Town Crier is marked
>   **MINIONS NOT NOMINATED**," [shake your head no; if marked MINION NOMINATED,
>   nod yes, and remove the MINION NOMINATED reminder]
> - "If you forget whether a Minion made a nomination or not, wake each Minion at
>   night and ask by showing" [the **DID YOU NOMINATE TODAY?** token; they must
>   answer honestly]

**Examples (quoted):**

> - "Today, four players nominated. Two of them were Minions. Many players voted,
>   but there was no execution." [The Town Crier learns a "yes".]
> - "A Minion called for the exile of a Traveller, who was exiled. That night, the
>   Town Crier learns a 'no.' **(Exiles are never affected by character
>   abilities.)**"

**Clarifications:**

- What counts: *any* Minion nomination, whether or not it was seconded and
  whether or not it led to an execution.
- Exiles never count.
- No information on the first night (the Town Crier is a `night*` character).
- The page does not explicitly rule on dead Minions; under the core rules dead
  players cannot nominate at all, so the case cannot arise. The app already
  enforces that (see below).

**Night order.** Other nights only, position 78 (`night_and_jinxes.json:451`),
after Flowergirl 77 and before Duchess 79. No first-night entry. **Correct.**

**Jinxes.** None (checked against all 58 entries in `night_and_jinxes.json`).

## What the app does today

The information calculation itself is one of the better ones in the engine.

- `characters.json:945-959` — ability, `reminders: ["Minions not nominated",
  "Minion nominated"]`, empty `firstNightReminder`, and an `otherNightReminder`
  that reads "Nod 'yes' or shake head 'no' … Place the 'Minion not nominated'
  marker (remove 'Minion nominated', if any)."
- `night_and_jinxes.json:451` — other-night index 78. **Works.**
- `night_guide.json:593-598` — accurate prose including the Vortox/impaired
  inversion. No show cards.
- `InfoCalc.supports` includes `towncrier` (`InfoCalc.kt:31`), dispatch at
  `InfoCalc.kt:58`, computed at `InfoCalc.kt:295-305`:

  ```kotlin
  val today = ctx.state.nominations.filter { it.day == relevantDay(ctx.state) && !it.isExile }
  val minionNominators = today.mapNotNull { n ->
      ctx.state.player(n.nominatorId)?.takeIf { ctx.character(it)?.team == Team.MINION }
  }
  headline = if (minionNominators.isNotEmpty()) "YES — a Minion nominated today" else "NO — …"
  detail   = minionNominators.joinToString { ctx.name(it) }
  caveats  = misregistrations(ctx, today.mapNotNull { ctx.state.player(it.nominatorId) })
  ```

  - `relevantDay` (`InfoCalc.kt:117-118`) is `cycle - 1` during NIGHT — the right
    day. **Works.**
  - `!it.isExile` implements the wiki's exile rule. **Works**, and the exile flag
    is set correctly at record time from `nominee.isTraveller`
    (`DayScreen.kt:163`, `:228`).
  - The Marionette is a `minion`-team character in `characters.json`, so its
    nominations count. **Works.**
  - `commonCaveats` adds the Vortox and impairment notes
    (`InfoCalc.kt:158-166`), and `StepDetailPanel` renders a one-tap
    `Show NO`/`Show YES` inversion for an impaired holder
    (`NightScreen.kt:903-929`). **Works.**
- Dead players cannot be selected as nominators
  (`DayScreen.kt:131-140`: `p.alive && !GameActions.hasNominatedToday(state, p.id)`),
  matching the core rules. **Works.**
- `ShowCards.kt:374` already contains the phrase card
  `"DID YOU NOMINATE TODAY?"` — but only inside the global "All tokens" sheet
  (`ShowToolSheet`, reached from `NightScreen.kt:280` → `GameShell.kt:492-499`).

**The storyteller's experience.** The Town Crier row shows a correct YES/NO
derived from whatever nominations were typed into the Day tab, plus the names of
the Minion nominators. Two reminder tokens exist in the data and in the tray, and
the app's own instructions tell the ST to place them, but nothing places them and
nothing reads them.

## Defects and gaps

1. **P1 · The reminder tokens the app tells the ST to place are never placed, and
   never read.** `night_guide.json:595` says "Place the 'Minion nominated'
   reminder during the day when a Minion nominates so you remember at night", and
   `characters.json:951` repeats it. Nothing in `GameActions.recordNomination`
   (`GameActions.kt:274-275`) or in `advancePhase` (`GameActions.kt:258-263`) does
   it. Either automate both halves (dawn reset + on-nomination replace) or delete
   the instruction; leaving the ST an instruction the app ignores is the worst of
   both. Repro: record a Minion nomination on the Day tab, look at the Town
   Crier's seat — no token.
2. **P1 · The nominator's team is read from the CURRENT grimoire, not from when
   the nomination happened.** `InfoCalc.kt:297-299` resolves
   `ctx.state.player(n.nominatorId)` and reads its team *now*. Anything that
   changes a player's character between the nomination and the Town Crier's step
   silently flips the answer:
   - a Pit-Hag turns the nominating Minion into a Townsfolk (Pit-Hag acts at
     other-night index 25, long before the Town Crier at 78);
   - the Scarlet Woman becomes the **Demon** after the Demon is executed that same
     day — she was a Minion when she nominated, but reads as a Demon at night;
   - a Snake Charmer swap turns a Minion seat into something else;
   - the Philosopher/Alchemist mechanics of `assignCharacter`.
   `Nomination` (`GameState.kt:62-72`) has no character snapshot, unlike
   `DeathRecord` which does (`GameState.kt:84-85`). Repro: Minion nominates on day
   2; Pit-Hag turns them into the Chef on night 3; the Town Crier learns "NO".
3. **P1 · Misregistration is noisy and unrecorded.**
   `misregistrations(ctx, all today's nominators)` (`InfoCalc.kt:303`,
   `:121-130`) flags every Spy or Recluse who nominated, even when the answer
   cannot change — e.g. a real Minion also nominated, so the answer is YES under
   every reading. And when it *does* matter, the ST's decision ("tonight the
   Recluse registers as a Minion") is nowhere stored, so it can silently differ
   from the ruling they made for the Empath two rows earlier, and it is lost on
   undo/redo. Repro: Recluse nominates, real Minion nominates → the app prints a
   Recluse caveat that is irrelevant to the answer.
4. **P2 · No fallback when the ST did not type a nomination into the app.** The
   whole calculation depends on `state.nominations` being complete. Real games
   have fast nominations that never get recorded. The wiki's own fallback — wake
   each Minion and show DID YOU NOMINATE TODAY? — exists as a phrase card
   (`ShowCards.kt:374`) but is three taps deep in a global sheet and is not
   offered on the Town Crier step, where it is needed.
5. **P2 · The trigger is invisible at the moment it happens.**
   `StatusEffects.nominationWarnings` (`StatusEffects.kt:132-166`) is the
   established place for "things this nomination sets in motion" (Witch curse,
   Golem, Virgin, Fearmonger) and is rendered live as the ST builds a nomination
   (`DayScreen.kt:154-159`). A Minion nomination does not appear there, so the ST
   never notices during the day that tonight's Town Crier answer just became YES.
6. **P2 · No day-start briefing.** `advancePhase` NIGHT→DAY
   (`GameActions.kt:260`) only sweeps dawn tokens; `GameShell` has no morning
   dialog. There is nowhere the app says "Town Crier in play — watch who
   nominates today", which is the reminder the token system exists to provide.
7. **P2 · A withdrawn nomination still counts.** `NominationResult.WITHDRAWN`
   exists (`GameState.kt:59`) and is rendered in the log
   (`screens/GameExtras.kt:74`), but there is no UI to set it, and
   `InfoCalc.townCrier` counts every non-exile record regardless of result.
   *Uncertain:* the wiki says "any Minion nomination", which suggests a withdrawn
   nomination still counts (a nomination happened); flagging rather than guessing,
   and recommending the app state its choice on screen.
8. **P2 · A dead Town Crier still renders a full live answer.** The row shows
   "All holders are dead — usually skip." (`NightScreen.kt:751-757`) plus a
   confident YES/NO panel and a caveat "X is dead — they normally don't act"
   (`InfoCalc.kt:150`). The engine's own playtest fixture records this three times
   as `informationShown = "SKIPPED (dead)"`
   (`FullGamePlaytestTest.kt:943, :1009, :1049`). The row should collapse to a
   one-line "dead — no wake" state.
9. **P3 · Reminder-label drift.** `characters.json:947-950` declares
   `"Minions not nominated"` / `"Minion nominated"`, but `:951`'s instruction
   text says `'Minion not nominated'` (singular). The wiki uses MINIONS NOT
   NOMINATED / MINION NOMINATED. Align the prose to the labels.
10. **P3 · `relevantDay` during the DAY phase.** `InfoCalc.kt:117-118` returns
    `state.cycle` when the phase is DAY, so an ST who opens the Town Crier row
    from the Night tab during the day sees *today's* still-accumulating answer
    rather than last night's. Harmless in practice, confusing if noticed.

## Proposed behaviour (spec)

- **when**: other nights only (index 78, unchanged). Wake condition: a seat with
  `nightRoleId == "towncrier"` and **alive**. Never on night 1.
- **targets**: none.
- **immediate effects**: none beyond marking the step done and clearing the
  `("towncrier","Minion nominated")` token (see expiry).
- **information**:
  - Compute from `state.nominations` filtered to `day == cycle - 1`,
    `!isExile`, using a **snapshot** of the nominator's registration taken at
    record time (see the model change below), not the live grimoire.
  - Headline `YES — a Minion nominated today` / `NO — no Minion nominated today`.
  - Detail: the Minion nominators by name and character, plus the count of
    nominations considered, so the ST can spot a missing record.
  - **Misregistration handling**: compute the answer twice — once with each
    ambiguous nominator registering as a Minion and once not. If the two agree,
    show no caveat. If they differ, show
    `Depends on whether <Recluse> registers as a Minion tonight` with two buttons
    that record the ruling on the `Nomination` and produce a definite answer.
  - **Impaired / Vortox**: keep the existing inversion chips
    (`NightScreen.kt:903-929`) — they already work — and label the answer
    `TRUE: YES · GIVE: NO`.
  - **Fallback**: a step chip `Ask the Minions` that lists the living Minions by
    name and shows the `DID YOU NOMINATE TODAY?` card
    (`ShowCards.kt:374`) one seat at a time, with a yes/no toggle per Minion that
    overrides the derived answer for tonight and is stored.
- **model change (prerequisite for defect 2)**: extend `Nomination`
  (`GameState.kt:62-72`) with

  ```kotlin
  val nominatorCharacterId: String? = null,   // snapshot at record time
  val nominatorTeam: Team? = null,            // snapshot at record time
  val nominatorRegistersAsMinion: Boolean? = null,  // ST ruling for Spy/Recluse
  ```

  populated in `DayScreen`'s Record button (`DayScreen.kt:220-247`) and defaulted
  from the live grimoire for older saves (fall back to the current lookup when
  the snapshot is null, exactly as `WinCheck` does for `characterIdAtDeath`,
  `WinCheck.kt:36-39`).
- **deferred effects / tokens** (make the app's own instructions true):
  - In `GameActions.recordNomination` (`GameActions.kt:274-275`): if the
    (snapshotted) nominator registers as a Minion and the nomination is not an
    exile, and a `towncrier` seat exists, `placeExclusiveReminder(towncrierSeat,
    PlacedReminder("towncrier","Minion nominated"))` and remove
    `("towncrier","Minions not nominated")`.
  - In `advancePhase` NIGHT→DAY (`GameActions.kt:260`): place
    `("towncrier","Minions not nominated")` on the Town Crier and remove
    `("towncrier","Minion nominated")`.
  - Add `("towncrier","Minion nominated")` to `EXPIRES_AT_DAWN`
    (`GameActions.kt:218-225`) so the dawn reset is one table entry rather than
    special-case code, and place the "Minions not nominated" token in the same
    pass.
  - The tokens remain *display* state; the answer stays derived from the
    nomination records, so an undo can never desynchronise them.
- **expiry**: `("towncrier","Minion nominated")` expires at dawn;
  `("towncrier","Minions not nominated")` is replaced at dawn and on the first
  qualifying nomination. Neither survives into the next day.
- **visibility**: the Town Crier only; existing full-screen YES/NO card
  (`NightScreen.kt:896-901`) is sufficient. The `DID YOU NOMINATE TODAY?` card is
  shown to Minions in the fallback flow.
- **day-time inputs the app must let the ST record**: nothing new beyond the
  nomination itself — but the nomination flow must (a) snapshot the nominator's
  character, (b) surface the consequence live. Add to
  `StatusEffects.nominationWarnings` (`StatusEffects.kt:132-166`):

  ```
  if a living Town Crier is in play and the nominator registers as a Minion:
      "Town Crier: this nomination means tonight's answer is YES."
  if the nominator is a Spy or Recluse and a Town Crier is in play:
      "Town Crier: decide now whether <name> registers as a Minion."
  ```

  and to the day-start briefing: `Town Crier in play — note who nominates today.`
- **interactions to handle explicitly**:
  - **Exiles**: excluded (already correct). Keep the wiki quotation in the guide.
  - **Travellers**: an evil Traveller is not a Minion → NO. Correct today.
  - **Marionette**: is a Minion; counts. Correct today; add a regression test
    because the Marionette's `nightRoleId` is a good character
    (`GameState.kt:39-44`) and a future refactor could easily break this.
  - **Spy / Recluse**: ST ruling, recorded (above).
  - **Scarlet Woman / Pit-Hag / Snake Charmer mid-game changes**: snapshot solves
    all three.
  - **Vortox**: answer must be false (already caveated).
  - **Philosopher gaining Town Crier**: keys on `nightRoleId`, so it works once
    the acting-character model lands (see
    `docs/audit/characters/philosopher.md`).

### UI text the step should display

- Row body: `Nod yes or shake no: did a Minion nominate on day <n>?`
- Answer: `NO — no Minion nominated on day 2 (4 nominations recorded)` /
  `YES — Cleo (Witch) nominated on day 2`
- Ambiguous: `Depends on the Recluse — did Ivy register as a Minion when she
  nominated?` · buttons `Yes, she did` / `No, she didn't`.
- Missing records: `Only 1 nomination is recorded for day 2 — tap "Ask the
  Minions" if you're not sure.`
- Impaired: `TRUE: YES · GIVE: NO — Juno is poisoned.`

### Data changes

- `characters.json:951`: change "Place the 'Minion not nominated' marker" to
  "Place the 'Minions not nominated' marker" so the prose matches the token
  labels at `:947-950`.
- `night_guide.json:593-598`: add the exile rule ("Exiles are never affected by
  character abilities"), the "any nomination, seconded or not" clause, and the
  DID YOU NOMINATE TODAY? fallback. Add a show entry for that card.
- No night-order change.

## Tests to add

1. **Basic yes/no.** (Extend `InfoCalcTest.kt:105`.) Given day 2 with a Minion
   nomination and a Townsfolk nomination; When night 3's `towncrier` is computed;
   Then the headline starts with "YES" and the detail names the Minion.
2. **Exiles do not count.** Given the only day-2 nomination is a Minion nominating
   a Traveller with `isExile = true`; Then the night-3 answer is "NO".
   *(Asserts the wiki's second Example.)*
3. **Right day.** Given Minion nominations on day 1 but none on day 2; Then the
   night-3 answer is "NO" and the night-2 answer is "YES".
4. **Snapshot survives a character change.** Given a Minion nominated on day 2;
   When that seat is reassigned to `chef` (Pit-Hag) during night 3; Then the
   night-3 `towncrier` answer is still "YES". *(Fails today.)*
5. **Scarlet Woman promotion.** Given the Scarlet Woman nominated on day 2 and
   became the Demon when the Demon was executed that day; Then the night-3 answer
   is "YES".
6. **Marionette counts.** Given the `marionette` seat nominated on day 2; Then the
   answer is "YES" (guards `nightRoleId` refactors).
7. **Dead Minions cannot nominate.** Given a dead Minion; Then
   `DayScreen`'s nominator predicate rejects them (assert at the
   `GameActions`/predicate level).
8. **Misregistration only when it matters.** Given a Recluse nominated **and** a
   real Minion nominated; Then the result has no Recluse caveat. Given only the
   Recluse nominated; Then the result reports both readings and requires a ruling.
9. **Tokens follow the rules.** Given a Town Crier in play; When a Minion
   nomination is recorded; Then the Town Crier carries
   `("towncrier","Minion nominated")` and not `("towncrier","Minions not
   nominated")`. When `advancePhase` reaches the next dawn; Then the tokens are
   the other way round.
10. **Nomination warning.** Given a living Town Crier; When
    `StatusEffects.nominationWarnings(state, lookup, minionId, anyId)` is called;
    Then it contains a Town Crier line.
11. **Vortox inversion.** Given a living Vortox and a true answer of "NO"; Then the
    caveats contain the Vortox note (already true) and the false-answer chip
    offers "YES".
12. **First night.** Given `NightOrder.firstNight`; Then it contains no
    `towncrier` step.
