# Scapegoat (scapegoat) — Trouble Brewing Traveller

## Official rules (sources)

Sources (fetched 2026-08-25):
- <https://wiki.bloodontheclocktower.com/Scapegoat>
- <https://wiki.bloodontheclocktower.com/Travellers>
- <https://wiki.bloodontheclocktower.com/Glossary> ("Execution: The group decision
  to kill a player other than a Traveller during the day."; "About to die: The
  player who has enough votes to be executed and more votes than any other player
  today.")
- Official rulebook, "Travelers" chapter (mirror
  <https://www.web3us.com/sites/default/files/Rulebook.pdf>).

Current ability text (matches `characters.json` exactly):

> "If a player of your alignment is executed, you might be executed instead."

How to run (wiki, verbatim):

- "**When a player of matching alignment faces execution, the Storyteller may
  choose to execute the Scapegoat instead. The Scapegoat dies and that execution
  ends voting for the day.**"
- Key mechanics from the summary:
  - "**Alignment matters: evil Scapegoats protect evil players; good Scapegoats
    protect good players.**"
  - "**The Storyteller determines when this ability activates.**" (it is a
    Storyteller choice, never automatic and never the Scapegoat's choice)
  - "**Only executions trigger this ability, not other forms of death.**"
  - "**When the Scapegoat dies, it counts as an execution, halting further
    nominations that day.**"
  - "**Players remain unaware of the Scapegoat's alignment upon their death.**"
- Examples:
  - "A Fortune Teller is about to be executed. The Storyteller chooses to execute
    the good Scapegoat instead. **The Fortune Teller survives; the Scapegoat dies.
    The Undertaker later learns a Scapegoat was executed.**"
  - "A Poisoner faces execution. The Storyteller chooses to execute the evil
    Scapegoat instead, **though allowing the Poisoner's death was also
    possible**."
  - "A Spy is about to be executed when the good Scapegoat dies instead." — note
    the Spy is **evil** but registers as good; the wiki pairs it with a **good**
    Scapegoat, i.e. the trigger follows **registration**, and the Storyteller may
    use a misregistering character either way.
- Storyteller-facing strategy notes: "Using this ability before the final day
  proves most effective, as late-game Scapegoats face almost certain exile";
  "The Storyteller typically activates your ability when the Demon is executed,
  though Minion executions also create valuable opportunities."

Traveller framework (rulebook, verbatim):

- "**Choose Alignment. Tell the Traveler player in private whether they are good
  or evil. If you made the Traveler evil, they learn which player is the Demon…**"
  and "**Inform Group… (Do not declare their alignment.)**"
- "Travelers… **lose their abilities when dead or drunk or poisoned**." — a
  poisoned Scapegoat cannot be substituted in.
- "**Travelers are exiled, not executed.**" — the Scapegoat is the one deliberate
  exception: this ability *executes* a Traveller. That is why the Undertaker sees
  a Scapegoat, and why the app must not assume "traveller ⇒ exile" in the death
  path.
- Exile: "**If at least half of the players support the exile, it succeeds**…
  total number of players in the game." · "Any player, even dead ones, may support
  the exile."

Jinxes: none for the Scapegoat in `night_and_jinxes.json` or on the wiki.

Night order: the Scapegoat never acts at night; correctly absent from both lists.

## What the app does today

Data
- `characters.json` — `scapegoat`, team `traveler`, ability text correct,
  `reminders: []`, no night reminders.
- `night_guide.json` — **no `scapegoat` entry**.
- `night_and_jinxes.json` — absent from both orders. Correct.

Engine
- `scapegoat` is referenced nowhere in production code. `grep -rn "scapegoat"
  engine/src app/src` matches only `characters.json`.
- Execution is a plain `GameActions.kill(state, id, DeathCause.EXECUTION)`
  (`GameActions.kt:136-156`). There is no substitution concept, no link between
  the executed player and the recorded death, and no "the day ends now" state.
- `GameActions.aboutToDie` (`GameActions.kt:296-306`) derives the block from
  nominations; executing a **different** player leaves the block pointing at the
  original nominee, so the banner keeps offering to execute them too.
- `InfoCalc.undertaker` (`InfoCalc.kt:281-290`) reads the last
  `DeathCause.EXECUTION` death of the day — so if the ST kills the Scapegoat with
  "Executed", the Undertaker correctly learns *Scapegoat*. That part works, once
  the ST does it by hand.
- `StatusEffects.deathNotes` (`StatusEffects.kt:52-129`) lists protections and
  triggers for a seat, but has **no Scapegoat entry** — nothing warns the ST at
  the moment of an execution that a substitution is available.

UI
- Execution is offered in three places, all of which kill the on-block player and
  only the on-block player:
  - the Day tab block banner, `DayScreen.kt:93-115` (`Execute` →
    `viewModel.kill(onBlock.id, DeathCause.EXECUTION)`);
  - the nomination row, `DayScreen.kt:350-357`;
  - the dusk guard, `GameShell.kt:592-616` ("Execute & begin night").
- `NominationRow` chooses `DeathCause.EXILE` when `nomination.isExile`
  (`DayScreen.kt:353-355`) — correct for an ordinary traveller exile, and exactly
  the assumption the Scapegoat breaks (a Scapegoat dies by **execution**).
- The seat sheet has an "Executed" button (`SeatSheet.kt:271-273`) which is the
  only route to a Scapegoat substitution today, and it is available on the
  Scapegoat's own seat with no context.
- No day-start briefing, no reminder token, no linkage of "X survived because the
  Scapegoat died".
- Alignment defaults to good like every traveller (`Character.kt:16`,
  `GameState.kt:45-51`); bare "Flip alignment" (`SeatSheet.kt:315`). **The
  Scapegoat's entire trigger condition depends on this unrecorded fact.**

Storyteller experience today: at the moment of execution the app shows a big red
"Execute <nominee>" button and says nothing about the Scapegoat sitting three
seats away. To substitute, the ST must ignore that button, navigate to the
Scapegoat's seat, press "Executed", then return to the Day tab where the banner
still says "On the block: <original nominee>" with an Execute button, and remember
not to press it, and remember that the day is now over.

## Defects and gaps

1. **P0** · No substitution flow · Rules: "the Storyteller may choose to execute
   the Scapegoat instead. The Scapegoat dies…" App: every execution affordance
   kills the on-block player · `DayScreen.kt:111-114`, `DayScreen.kt:350-357`,
   `GameShell.kt:598-604` · Repro: nominate a Fortune Teller, pass the vote, press
   Execute — the Fortune Teller dies with no Scapegoat prompt anywhere.

2. **P0** · The trigger is unknowable to the app because traveller alignment is
   never recorded · Rules: "If a player of **your alignment** is executed." App:
   `Team.TRAVELLER.isEvil == false` (`Character.kt:16`), so every Scapegoat is
   "good" unless the ST used the unlabelled "Flip alignment" button ·
   `GameShell.kt:663-682`, `SeatSheet.kt:315`, `GameState.kt:45-51`.

3. **P0** · Registration is not consulted · The wiki's own third example pairs a
   **good** Scapegoat with an executed **Spy** (an evil player registering good).
   Any implementation must compare the Scapegoat's alignment against the executed
   player's **registered** alignment, with a Storyteller override for Spy/Recluse ·
   no code path exists; `InfoCalc` has the misregistration vocabulary
   (`InfoCalc.kt`) but it is not reachable from the day flow.

4. **P0** · After a substitution the block is stale · `aboutToDie`
   (`GameActions.kt:296-306`) still names the original nominee, so the banner and
   the dusk guard keep offering to execute them — a second, illegal execution is
   one tap away · `DayScreen.kt:93-115`, `GameShell.kt:592-616`.

5. **P1** · "That execution ends voting for the day" is unmodelled · Rules: the
   substitution is an execution and executions end the day. App: nominations
   remain open after any execution · `DayScreen.kt:126-255`,
   `GameActions.kt:285-289`.

6. **P1** · Nothing surfaces the option at the decision point ·
   `StatusEffects.deathNotes` is the app's existing "things to weigh before this
   death" mechanism and is already rendered on the seat sheet and behind the kill
   confirmation (`SeatSheet.kt:242-296`), but it has no Scapegoat case ·
   `StatusEffects.kt:52-129`.

7. **P1** · No day-start briefing · Nothing reminds the ST that a Scapegoat is in
   play and which alignment they may cover · `DayScreen.kt:85-124`.

8. **P2** · The saved player is not recorded · Both wiki strategy sections turn on
   "you both know each other's alignment" — the ST needs a log line "Scapegoat
   died instead of the Fortune Teller (day 3)" to adjudicate later claims ·
   `GameExtras.kt:44-90`.

9. **P2** · The Scapegoat is a Traveller who dies by **execution**, and the app's
   traveller death path assumes exile · `DayScreen.kt:353-355` picks
   `DeathCause.EXILE` for any traveller nominee. A substitution must bypass that
   path entirely and record `DeathCause.EXECUTION` so the Undertaker sees it
   (`InfoCalc.kt:281-290`).

10. **P2** · Loss of ability is unmodelled · A poisoned or drunk Scapegoat cannot
    be substituted in (travellers lose abilities when drunk or poisoned); a dead
    one obviously cannot.

11. **P2** · Multiple Scapegoats are possible (multiple travellers) and the app
    would have no way to choose between them.

12. **P3** · No day-guide entry, so the Scapegoat's how-to-run text is nowhere in
    the app.

## Proposed behaviour (spec)

### Trigger point

Introduce a single **execution resolution** action that every execution
affordance routes through, instead of three separate `kill(..., EXECUTION)` call
sites:

```kotlin
/** Everything the Storyteller must weigh at the moment of an execution. */
data class ExecutionOptions(
    val nomineeId: Long,
    val protections: List<String>,        // from StatusEffects.deathNotes
    val scapegoats: List<Long>,           // legal substitutes, see below
    val consequences: List<String>,       // Saint, Fearmonger, Mastermind, Undertaker…
)

fun executionOptions(state: GameState, lookup: (String) -> Character?, nomineeId: Long): ExecutionOptions

fun executeWithScapegoat(state: GameState, nomineeId: Long, scapegoatId: Long): GameState
```

`scapegoats` = every seat where **all** of:
- `characterId == "scapegoat"`;
- `alive`;
- `!StatusEffects.isImpaired(...)` (travellers lose abilities when drunk/poisoned);
- the Scapegoat's alignment **equals the nominee's registered alignment**.

Registration: default to `Player.isEvil(lookup)`, but when the nominee is a
`spy` or `recluse` (or any character the app knows misregisters), offer the ST a
two-button choice "<Name> registers as GOOD / EVIL for this" and use the answer —
mirroring the wiki's Spy example. Record the choice in the log.

### `executeWithScapegoat` effects

- `kill(scapegoatId, DeathCause.EXECUTION)` — **not** `EXILE`. The Undertaker
  must learn *Scapegoat* (`InfoCalc.kt:281-290`).
- The nominee is **not** killed and stays alive.
- Mark the day resolved: set the same `executionLocked` flag specified in
  `judge.md` (derived from "an execution death exists today"), so:
  - the block banner changes to "Executed today: Scapegoat (in place of Fortune
    Teller) — the day is over";
  - `DayScreen`'s nominator/nominee pickers are disabled;
  - the dusk guard (`GameShell.kt:592-616`) no longer offers to execute anyone and
    goes straight to "Begin night".
- Log two linked lines:
  `"Scapegoat executed in place of Fortune Teller (day 3)"` and
  `"Fortune Teller survived the execution (Scapegoat)"`.
- Optionally place a `scapegoat:"Used"` reminder — not required by the rules (the
  Scapegoat is dead and has no further ability), but useful when a Professor or
  similar brings them back. Skip it; the death record is enough.

### Where the option appears

1. **Day tab block banner** (`DayScreen.kt:93-115`): the `Execute` button opens an
   execution sheet rather than killing immediately. The sheet lists, in order:
   protections that apply, the Scapegoat option (if any), the consequences, and
   two buttons `Execute <nominee>` / `Execute the Scapegoat instead`.
2. **Nomination row** (`DayScreen.kt:350-357`) — same sheet.
3. **Dusk guard** (`GameShell.kt:592-616`) — same sheet, launched from
   "Execute & begin night".
4. **Judge forced pass** (`judge.md`) — a forced pass is an execution, so it must
   route through the same sheet and offer the substitution.

### Day-start briefing (shared panel)

> **Scapegoat in play (good).** If a **good** player is executed today, you may
> execute *<Scapegoat name>* instead — the nominated player survives, the
> Scapegoat dies as an execution (the Undertaker sees *Scapegoat*), and the day
> ends. It is entirely your call. A misregistering player (Spy, Recluse) may count
> as either alignment.

When the Scapegoat is dead or impaired, the panel line disappears (or reads
"Scapegoat — no ability (poisoned)").

### Interactions to handle explicitly

- **Undertaker** — learns *Scapegoat*; already correct once the death is recorded
  with `DeathCause.EXECUTION`.
- **Saint** — a Saint who is *saved* by the Scapegoat did not die by execution, so
  good does not lose (`WinCheck.kt:51-68`). This is a major reason to route
  through one action rather than two manual kills.
- **Fearmonger** — "if executed from this nomination, their team loses"
  (`StatusEffects.kt:158-160`). If the Fearmonger's target is saved by the
  Scapegoat, the loss does not trigger; state this in the sheet.
- **Mastermind** — the extra day is triggered by the **Demon's** execution; a
  Scapegoat substituting for the Demon means the Demon was not executed
  (`WinCheck.kt:28-49`).
- **Devil's Advocate** ("Survives execution", `StatusEffects.kt:68`) — resolve
  protections first: if the nominee already survives, no Scapegoat is needed.
- **Virgin / Witch / Golem** — nomination-time effects, unaffected.
- **Gunslinger** — a shot is not an execution, so it never triggers the Scapegoat.
- **Judge** — a forced pass does trigger it (see above).
- **Exile** — an exile is not an execution, so an exiled traveller never triggers
  the Scapegoat, and the Scapegoat can itself be exiled normally (traveller rules)
  in which case the death is `DeathCause.EXILE` and the Undertaker sees nothing.
- **Multiple Scapegoats** — list each legal one in the sheet with its alignment.

### UI text

- Sheet title: `Execute <nominee>?`
- Option: `Execute the Scapegoat instead — <nominee> survives, the day ends`
- Registration prompt: `<Spy> registers as GOOD or EVIL for the Scapegoat?`
- After: `Scapegoat executed in place of <nominee>. The day is over.`
- Briefing: `Scapegoat (good) — you may swap in for any good player's execution.`

### Data changes

- `characters.json`: none.
- Add a day-guide entry for `scapegoat`.
- `StatusEffects.deathNotes`: add a case that, when the dying player is being
  executed and a legal Scapegoat exists, emits
  "Scapegoat (<name>) shares this player's alignment — you may execute them
  instead."

## Tests to add

1. `Given` a good Scapegoat (alive, unimpaired) and a good Fortune Teller on the
   block, `then` `executionOptions(...).scapegoats` contains the Scapegoat.
2. `Given` an **evil** Scapegoat and a good Fortune Teller on the block, `then`
   `scapegoats` is empty.
3. `Given` a good Scapegoat and an executed **Spy** (evil, registers good), `when`
   the ST chooses "registers GOOD", `then` the Scapegoat is offered; `when` they
   choose "registers EVIL", `then` it is not.
4. `Given` `executeWithScapegoat(state, fortuneTellerId, scapegoatId)`, `then` the
   Fortune Teller is alive, the Scapegoat is dead with
   `DeathCause.EXECUTION`, and `InfoCalc.compute(..., "undertaker", ...)` names
   the Scapegoat.
5. Same setup, `then` `executionLocked(state)` is true and no further nomination
   can be recorded today.
6. `Given` a **Saint** on the block and a good Scapegoat substituted in, `then`
   `WinCheck.check` does **not** return the Saint advisory.
7. `Given` the **Demon** on the block with a Mastermind in play and an evil
   Scapegoat substituted in, `then` `state.mastermindDayActive` is not set and the
   Demon is alive.
8. `Given` a poisoned Scapegoat, `then` `scapegoats` is empty.
9. `Given` a dead Scapegoat, `then` `scapegoats` is empty.
10. `Given` a Scapegoat exiled by the group, `then` the death cause is
    `DeathCause.EXILE` and the Undertaker learns nothing that day.
11. `Given` a Gunslinger shot on a player of the Scapegoat's alignment, `then` no
    substitution is offered (only executions trigger it).
