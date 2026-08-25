# Butcher (butcher) — Sects & Violets Traveller

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Butcher>
Exile / execution definitions: <https://wiki.bloodontheclocktower.com/Glossary>

Current ability text (wiki, matches `characters.json:1160`):

> "Each day, after the 1st execution, you may nominate again."

Summary clarifications (quoted):

> - "After the first executed player has died, the Butcher may nominate a second player for
>   execution. The Butcher may nominate a player that has already been nominated today, and
>   the Butcher may make a nomination even if the Butcher already made a nomination earlier
>   today."
> - "If a player is executed, even if they do not die, then the Butcher may use their
>   ability. The players may choose to vote or not to vote, so there is no guarantee that
>   this extra nomination will cause an execution—it still needs to get enough votes—but
>   this second nomination does not need to exceed the vote tally of the previous
>   nominations."
> - "If no execution occurs today, then the Butcher may not use their ability at all today."

How to Run (quoted in full):

> "Each day, immediately after a player is executed, the Butcher may nominate a player for
> execution. (*Remind them if needed.*) To succeed, this nomination must tally votes of at
> least half the alive players, as normal, but does not have to exceed the votes of the
> execution that prompted the Butcher ability. If this second execution succeeds, it does
> not allow the Butcher to nominate a third player."

Examples (quoted in full):

> "The Witch is executed and dies. The Butcher then nominates the Sage, who gets enough
> votes to be executed. The Sage dies too.
>
> The Bone Collector is exiled, and then the Harlot is exiled. There are no executions today.
> The Butcher does not get to nominate again, because exiles are not executions.
>
> The Butcher nominates the Town Crier, but the Town Crier is not executed. The Mathematician
> gets more votes and is executed today. The game continues, and the Butcher nominates the
> Town Crier again. This time, enough hands are raised, and the Town Crier is executed."

Glossary (relevant):

> "**Execution:** The group decision to kill a player other than a Traveller during the day.
> There is a maximum of one execution per day, but there may be none."
> "**Exile:** ... an exile is not an execution."

Consequences that matter for the app:

- **Trigger = an execution happening, not a death.** "If a player is executed, even if they
  do not die" — a Devil's Advocate-protected player, a Fool, a Sailor, a Tinker, a
  Pacifist-saved player, a Zombuul's first "death", a Mayor bounce all still count.
- **An exile is not an execution** and never enables the Butcher.
- **The Butcher's extra nomination bypasses three normal rules:**
  1. the nominee may already have been nominated today;
  2. the Butcher may nominate even if they already nominated today;
  3. the tally only has to reach the threshold — it does **not** have to beat the day's
     highest tally.
- **The threshold is recomputed on the reduced living count** ("at least half the alive
  players, as normal") — after the first execution, one fewer player is alive.
- **Once per day, and only one extra.** A successful second execution does not unlock a
  third nomination. (The wiki's third example shows that a *failed* Butcher nomination can
  be repeated later in the same day *after* an execution occurs — the ability is "after the
  1st execution, you may nominate again": the ability is spent by *using* it after the first
  execution, so the app should mark it used on the Butcher's post-execution nomination, not
  on a pre-execution one.)
- **A dead Butcher** cannot use it — *unless* the Bone Collector restored the ability, which
  the Bone Collector wiki page gives as an explicit example ("At night, the Bone Collector
  chooses the dead Butcher. The following day, after an execution has occurred, the
  Storyteller prompts the Butcher to nominate again.").
- **The ST should prompt** ("Remind them if needed") — unlike the Gnome, prompting is
  explicitly allowed and encouraged here.
- No jinxes on the Butcher page. No night action, no reminder tokens.

## What the app does today

Data:
- `characters.json:1155-1166` — correct ability text, empty first/other night reminders,
  **no reminder tokens** (matches the official token set: the Butcher has none).
- `night_and_jinxes.json` — correctly absent from both night orders.
- `night_guide.json` — **no entry at all**. There is no day-guide mechanism in the app, so
  the Butcher's how-to-run text appears nowhere.

Code: **no Butcher-specific code anywhere.** `grep -rn butcher engine/src app/src` returns
only `characters.json` and `raw_sv_travellers_fabled.json`.

Storyteller's actual experience: they select the Butcher as a traveller, and from then on
the app behaves as if the Butcher were an ordinary player. All Butcher bookkeeping is
manual, and three separate parts of the day engine actively *block* the correct behaviour
(see defects 1–3).

Works: nothing to report — the character is inert in the app.

Shared traveller-lifecycle defects **T1–T7** apply — see `barista.md`.

## Defects and gaps

1. **P0 · The Butcher cannot make a second nomination if they already nominated today.**
   `DayScreen.kt:135-138` gates the Nominator chip on
   `p.alive && !GameActions.hasNominatedToday(state, p.id)`, and
   `GameActions.hasNominatedToday` (`GameActions.kt:285-286`) counts every non-exile
   nomination by that player today. Rules: "the Butcher may make a nomination even if the
   Butcher already made a nomination earlier today."
   *Repro:* Butcher nominates in the morning; an execution happens; the Butcher's chip is
   now disabled and the ST has to run the vote outside the app.

2. **P0 · The Butcher cannot re-nominate a player already nominated today.**
   `DayScreen.kt:146` gates the Nominee chip on
   `p.alive && !GameActions.hasBeenNominatedToday(state, p.id)`
   (`GameActions.kt:288-289`). Rules: "The Butcher may nominate a player that has already
   been nominated today" — this is exactly the wiki's third example.

3. **P0 · The Butcher's nomination is scored against the day's highest tally.**
   `DayScreen.kt:204` calls `Voting.outcome(votes, threshold, highest)`
   (`GameState.kt:147-152`), which returns `SAFE` unless `votes > currentHighest`, with
   `highest = GameActions.highestVotesToday(state)` (`GameActions.kt:278-282`). Rules: the
   Butcher's second nomination "does not have to exceed the votes of the execution that
   prompted the Butcher ability" — it only needs to reach the threshold.
   *Repro:* first execution passed with 6 votes; Butcher's nominee gets 4 with 7 alive
   (threshold 4) → the app says "safe", the rules say executed.

4. **P0 · There is no record that an execution occurred.** The whole engine only records
   *deaths* (`DeathRecord`, `GameState.kt:77-90`; `DeathCause.EXECUTION`,
   `GameState.kt:75`). When an executed player does not die (Devil's Advocate "Survives
   execution" `GameActions.kt:236`, Fool, Sailor, Tinker, Pacifist, Mayor bounce, Zombuul
   first death), nothing at all is written to state. So the Butcher trigger is
   unrepresentable in the current model, and so are "no execution today" checks used
   elsewhere (Vortox, Pacifist, Mayor).
   *Repro:* execute a Devil's Advocate-protected player → `SeatSheet.kt:325-345` offers
   "Death prevented" → nothing is recorded → the app has no idea an execution happened.

5. **P1 · Nothing prompts the Butcher.** The How-to-Run says "Remind them if needed."
   The moment an execution resolves, the app should surface "Butcher: you may nominate
   again." There is no day-event/briefing surface at all (`DayScreen.kt:268-276` ends with a
   static "When dusk falls…" hint).

6. **P1 · Nothing prevents a third nomination or marks the ability used.** The Butcher has
   no reminder token in `characters.json:1163` (correct — the physical character has none),
   so the app must track "Butcher extra nomination used today" in state. Today nothing does.

7. **P1 · Exiles are silently conflated with the day's execution state in the ST's head.**
   The app does correctly keep exiles out of `highestVotesToday` and `aboutToDie`
   (`GameActions.kt:280`, `:298`) and prints "(exile)" in the record row
   (`DayScreen.kt:336`), but there is no statement anywhere that an exile does not enable
   the Butcher. Given the wiki calls this out with a dedicated example, it belongs in the
   day briefing.

8. **P1 · A Bone Collector-restored dead Butcher cannot nominate.** `DayScreen.kt:135-138`
   requires `p.alive`. See `bonecollector.md` defect 6.

9. **P2 · The execution threshold shown does not explain itself after an execution.**
   `DayScreen.kt:71-72, 88-89` recomputes `Voting.executionThreshold(alivePlayers.size)`
   live, which is correct, but the header still shows "N votes is the tally to beat" — which
   is wrong guidance for the Butcher nomination.

10. **P2 · No Butcher guidance text anywhere.** `night_guide.json` is night-only
    (`NightGuide.forStep`, `NightGuide.kt:56-59`), and there is no day equivalent. The
    Butcher's How-to-Run, its three examples, and the "exiles are not executions" rule have
    nowhere to live. The same hole affects the Deviant, Gangster and Gnome.

## Proposed behaviour (spec)

The Butcher has no night step. Expressed in the same structured terms:

- **when**: day phase only. Trigger: an **execution event** is recorded today.
- **targets**: 1 nominee, any **alive** player (travellers included — but nominating a
  traveller is an exile call, not an execution; keep the existing `isExile` branch,
  `DayScreen.kt:161-164`). No "different from…" constraints; explicitly *allow* a nominee
  already nominated today.
- **immediate effects**: none until the vote resolves. On use, set the day-scoped
  "Butcher extra nomination used" flag.
- **deferred effects**: none at night. The ability resets at dawn.
- **expiry**: the used-flag is day-scoped; cleared when a new day begins.
- **information**: none.
- **visibility**: public — everyone knows the Butcher is the Butcher.
- **day-time inputs the app must let the ST record**: the execution event itself
  (see the engine change below), and whether the Butcher has spent the extra nomination.
- **interactions/edge cases**:
  - Exile ⇒ no trigger.
  - Execution with no death ⇒ trigger.
  - Butcher dead ⇒ no ability, unless `("bonecollector","Has Ability")` is on the Butcher.
  - Butcher drunk/poisoned (Barista, Sailor, Innkeeper, Widow, Goon…) ⇒ the ST may let the
    nomination happen and simply not honour it; surface `isImpaired` on the Butcher in the
    prompt so the ST decides knowingly.
  - Barista ACTS TWICE on the Butcher ⇒ two extra nominations today.
  - Mastermind day (`GameState.mastermindDayActive`, `GameState.kt:111`): the Butcher's
    extra execution can end the game — surface the Mastermind caution.
  - Evil Twin / Saint / Fearmonger "Fear" nominee warnings must still be surfaced through
    `StatusEffects.nominationWarnings` (`StatusEffects.kt:131-166`).

### Engine change: an execution event, separate from a death (cross-cutting)

Add to `GameState`:

```
val executions: List<ExecutionRecord> = emptyList()
// ExecutionRecord(day: Int, playerId: Long, died: Boolean, reasonNotDied: String = "")
```

Written by every path that currently calls `viewModel.kill(..., DeathCause.EXECUTION)` —
`DayScreen.kt:111-114`, `DayScreen.kt:350-357`, `GameShell.kt:598-603`,
`SeatSheet.kt:274-276` — **and** by the "Death prevented" branch of the protection dialog
(`SeatSheet.kt:340-343`), which today writes nothing. Derived helpers:

```
fun executionsToday(state): List<ExecutionRecord>
fun anExecutionOccurredToday(state): Boolean
```

This single record also unblocks: the Vortox "no execution today ⇒ evil wins" check, the
Mayor "3 alive, no execution ⇒ good wins" caution (`WinCheck.kt:90-91`), the Pacifist,
the Devil's Advocate, and the Minstrel.

### Day engine change: nomination legality becomes a function, not three inline conditions

Replace the inline `enabled` lambdas at `DayScreen.kt:135-138` and `:146` with an engine
helper:

```
data class NominationLegality(
    val canNominate: Boolean,
    val allowedNominees: Set<Long>,
    val ignoreHighestTally: Boolean,   // Butcher's extra nomination
    val reason: String,                // shown as a chip caption
)
fun nominationLegality(state, lookup, nominatorId): NominationLegality
```

Butcher rule inside it:

```
val butcherExtra =
    player.characterId == "butcher" &&
    (player.alive || player.reminders.any { it.sourceId=="bonecollector" && it.label.equals("Has Ability", true) }) &&
    anExecutionOccurredToday(state) &&
    !state.butcherExtraUsedToday(player.id)
if (butcherExtra) {
    canNominate = true                                   // even if already nominated today
    allowedNominees = state.alivePlayers.map { it.id }   // even if already nominated today
    ignoreHighestTally = true
}
```

`DayScreen.kt:197-205` then becomes:

```
val result = when {
    isExile -> if (votes >= exileThreshold) ABOUT_TO_DIE else SAFE
    legality.ignoreHighestTally -> if (votes >= threshold) ABOUT_TO_DIE else SAFE
    else -> Voting.outcome(votes, threshold, highest)
}
```

and recording the nomination sets the day-scoped used-flag. `Nomination`
(`GameState.kt:62-72`) should gain `val butcherExtra: Boolean = false` so the log and
`aboutToDie` replay (`GameActions.kt:296-306`) stay faithful: a Butcher nomination that
passes puts its nominee on the block regardless of the previous tally.

### UI text

- Day banner, the moment an execution is recorded and a Butcher is eligible:
  `Butcher: <Name> may nominate again — even someone already nominated today, and the tally
  only needs <threshold> votes (it does not have to beat <highest>).`
  With a `Start Butcher nomination` button that pre-selects the Butcher as nominator and
  sets `ignoreHighestTally`.
- If today's only removals were exiles:
  `No execution today — exiles are not executions, so the Butcher may not nominate again.`
- After use: `Butcher's extra nomination used. No third nomination today.`
- Nominee chip caption when the Butcher is the nominator:
  `Butcher: already-nominated players are allowed.`

### Data changes

- `characters.json:1155-1166`: no change needed (text and empty token list are correct).
- Add a **day guide** dataset (new file, e.g. `day_guide.json`, or extend
  `night_guide.json` with a `"day"` key) so `butcher`, `deviant`, `gangster`, `gnome`,
  `scapegoat`, `gunslinger`, `judge`, `bishop`, `matron`, `voudon` can carry their
  How-to-Run prose and prompts. Butcher entry:
  > "Immediately after any execution — even one where the executed player did not die —
  > remind the Butcher that they may nominate again. Already-nominated players are legal
  > targets, and the Butcher may nominate even if they already nominated today. The tally
  > must reach half the (now smaller) living count but does not have to beat the earlier
  > tally. Exiles are not executions. Only one extra nomination per day."

## Tests to add

1. `Given` day 2, the Butcher nominated earlier today, and an execution recorded today
   `When` `nominationLegality(state, butcherId)` `Then` `canNominate == true`.
   *(Fails today: `hasNominatedToday` blocks it.)*

2. `Given` player X was nominated earlier today and an execution occurred
   `When` `nominationLegality(state, butcherId)`
   `Then` `X.id in allowedNominees`. *(Fails today.)*

3. `Given` 8 alive (threshold 4) and today's highest passing tally is 6, an execution has
   occurred, `When` the Butcher's nomination tallies 4
   `Then` the result is `ABOUT_TO_DIE`. *(Fails today: `SAFE`.)*

4. `Given` only exiles have happened today `When` `nominationLegality(state, butcherId)`
   `Then` `canNominate == false` and the reason mentions "exiles are not executions".

5. `Given` a Devil's Advocate-protected player is executed and does not die
   `When` the day engine is queried `Then` `anExecutionOccurredToday(state) == true` and the
   Butcher may nominate. *(Fails today: nothing is recorded.)*

6. `Given` the Butcher already used the extra nomination today and a second execution
   occurred `When` `nominationLegality` `Then` `canNominate == false`
   ("no third nomination").

7. `Given` an execution occurred and the Butcher is dead with no restored ability
   `Then` `canNominate == false`; `Given` the same Butcher holds
   `("bonecollector","Has Ability")` `Then` `canNominate == true`.

8. `Given` a Butcher extra nomination that passes `When` `GameActions.aboutToDie` is
   replayed `Then` the Butcher's nominee is on the block even though an earlier tally was
   higher.

9. `Given` a new day begins `When` `advancePhase` DAY→NIGHT→DAY
   `Then` the Butcher extra-nomination flag is cleared.
