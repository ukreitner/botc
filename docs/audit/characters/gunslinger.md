# Gunslinger (gunslinger) — Trouble Brewing Traveller

## Official rules (sources)

Sources (fetched 2026-08-25):
- <https://wiki.bloodontheclocktower.com/Gunslinger>
- <https://wiki.bloodontheclocktower.com/Thief> (the exile-vote-counts-positive
  example is stated on the Gunslinger's exile there)
- <https://wiki.bloodontheclocktower.com/Travellers>
- <https://wiki.bloodontheclocktower.com/Glossary> ("Execution: The group decision
  to kill a player other than a Traveller during the day.")
- Official rulebook, "Travelers" chapter (mirror
  <https://www.web3us.com/sites/default/files/Rulebook.pdf>).

Current ability text (matches `characters.json` exactly):

> "Each day, after the 1st vote has been tallied, you may choose a player that
> voted: they die."

How to run (wiki, near-verbatim):

- "**Immediately after the first execution vote is tallied each day, the
  Gunslinger may announce their intention to activate their ability. If exercised,
  they point to any player who participated in that vote, and that player dies
  immediately.**"
- "For newer players, consider reminding them of their available power."
- "**Note: If clarification is needed, ask voting players to raise their hands
  again so the Gunslinger can accurately identify their target.**"
- Summary: "**Each day, following the initial execution vote tally, this character
  may designate any player who voted to die immediately. The Gunslinger has
  discretion whether to use this power and can only employ it once per day.**"
  And from the How to Run: "**The Gunslinger cannot kill further that day, whether
  they used the ability or not.**" — the *opportunity* is consumed by the first
  tally, used or not.
- Key rules:
  - "**Deaths from this ability are not executions, so the day continues and the
    Undertaker doesn't see them.**"
  - "**Exiles prevent the Gunslinger's ability from working on supporting
    players.**" — supporting an exile is not a vote, so exile supporters are not
    legal targets and an exile is not "the 1st vote".
  - "**Only the first vote for execution per day qualifies.**"
- Examples:
  - "An Imp receives five votes from ten alive players. The Gunslinger kills one
    voting player, who dies. **The Imp remains the execution target.**" (the shot
    does not clear the block)
  - "**Following a Thief exile**, a Butler receives one execution nomination
    vote — **the day's first**. The Gunslinger kills that voter. Later, a Saint
    nomination receives six votes, but **the Gunslinger cannot act, as this isn't
    the first nomination**." — confirms both that an exile does not count as the
    first vote and that the window is strictly the first *execution* tally.
- Storyteller-relevant tips: "Deal with protected characters like Saints through
  your unique execution-free method" (a shot Saint does not lose the game for
  good); "Exercise caution with final three-player scenarios."

Traveller framework (rulebook, verbatim):

- "**Choose Alignment. Tell the Traveler player in private whether they are good
  or evil. If you made the Traveler evil, they learn which player is the Demon…**"
- "Travelers… **lose their abilities when dead or drunk or poisoned**."
- Exile: "**Any player, even dead ones, may support the exile… The process to
  exile a Traveler is not affected by abilities.**" · "**If at least half of the
  players support the exile, it succeeds**… total number of players in the game."

Jinxes: none for the Gunslinger in `night_and_jinxes.json` or on the wiki.

Night order: the Gunslinger never acts at night; correctly absent from both lists.

Death-cause reading: the shot is neither an execution nor a night death. In the
app's vocabulary it is `DeathCause.STORYTELLER` (surfaced as "Other death"), which
is what keeps it out of `InfoCalc.undertaker` (`InfoCalc.kt:281-286`, which filters
`DeathCause.EXECUTION`). That much is already right; nothing else is.

## What the app does today

Data
- `characters.json` — `gunslinger`, team `traveler`, ability text correct,
  `reminders: []`, no night reminders.
- `night_guide.json` — **no `gunslinger` entry**.
- `night_and_jinxes.json` — absent from both orders. Correct.

Engine
- Nothing references `gunslinger`. `grep -rn "gunslinger" engine/src app/src`
  matches only `characters.json`.
- `Nomination.voterIds` (`GameState.kt:65`) already records who raised a hand, in
  clock order, and `Nomination.isExile` (`GameState.kt:71`) already distinguishes
  exiles — so the raw material for "players who voted on the first execution
  tally today" is present and correct.
- `GameActions.kill(state, id, DeathCause.STORYTELLER)` exists
  (`GameActions.kt:136-156`) and does not disturb the block
  (`GameActions.aboutToDie` derives from nominations only,
  `GameActions.kt:296-306`) — matching the wiki's "the Imp remains the execution
  target".
- `StatusEffects.deathNotes` (`StatusEffects.kt:52-129`) surfaces protections and
  on-death triggers; the seat sheet already gates a kill behind a confirmation
  when protections apply (`SeatSheet.kt:262-296`).

UI
- No Gunslinger affordance anywhere. `DayScreen.kt:161-251` records a nomination
  and moves on; nothing is offered after the first tally.
- To resolve a shot the ST must: notice the moment themselves, remember whether
  this was the day's first execution tally, remember (or scroll back to) who
  voted, open the target's seat sheet, press "Other death"
  (`SeatSheet.kt:274-276`), and then remember for the rest of the day that the
  Gunslinger is spent.
- Nothing marks the once-per-day use. The night tray's "Mark spent" chip
  (`NightScreen.kt:263-279`) is night-only and keyed to abilities whose text
  starts with "Once per game", so it would not apply anyway.
- `ReminderPicker` (`SeatSheet.kt:489-560`) offers no traveller tokens
  (`GameData.kt:39`), so even a manual "Used" marker must come from the generic
  list (`SeatSheet.kt:502`).
- Alignment defaults to good like every traveller (`Character.kt:16`,
  `GameState.kt:45-51`); bare "Flip alignment" (`SeatSheet.kt:315`).

Storyteller experience today: the app records the vote, then falls silent at
exactly the moment the rules require the ST to prompt the Gunslinger. Every part
of the ruling — is this the first execution vote, was that person a voter, does the
shot end the day (it does not), does the Undertaker see it (they do not) — is left
to the ST's memory.

## Defects and gaps

1. **P0** · No Gunslinger prompt after the first execution tally · Rules:
   "Immediately after the first execution vote is tallied each day, the Gunslinger
   may announce their intention…" and "For newer players, consider reminding them
   of their available power." App: nothing · `DayScreen.kt:217-251` · Repro: put a
   Gunslinger in play, record the day's first nomination — the app shows the
   result and the empty nomination form again.

2. **P0** · Legal targets are not constrained to that vote's voters · Rules: "they
   point to any player **who participated in that vote**". App: any seat can be
   killed from its seat sheet with no check · `SeatSheet.kt:262-276`.

3. **P0** · "Only the first vote for execution per day qualifies" is not tracked ·
   Rules, with the wiki's own worked example (Thief exile, then a first execution
   nomination, then a later six-vote nomination the Gunslinger **cannot** act on).
   App: no concept of the day's first execution tally · `GameActions.kt:274-306`.

4. **P0** · Exile supporters are wrongly available as targets, and an exile can be
   mistaken for the first vote · Rules: "Exiles prevent the Gunslinger's ability
   from working on supporting players" — supporting an exile is not a vote. App:
   exiles are recorded in the same `nominations` list with the same `voterIds`
   shape, differing only by `isExile` · `DayScreen.kt:220-229`,
   `GameState.kt:60-72`.

5. **P1** · The once-per-day opportunity is not marked · Rules: "The Gunslinger
   cannot kill further that day, **whether they used the ability or not**." App:
   no token, no state, and the `Used` generic token would have to be removed by
   hand each dawn · `SeatSheet.kt:502`, `GameActions.kt:218-242`.

6. **P1** · The death cause is left to the ST to choose correctly · If they pick
   "Executed" instead of "Other death", the Undertaker wrongly learns the target
   (`InfoCalc.kt:281-286`), the Saint fires (`WinCheck.kt:51-68`), the Mastermind
   day triggers (`WinCheck.kt:28-49`) and the day should have ended · `SeatSheet.kt:271-273`.

7. **P1** · Nothing states that the day continues and the block is unchanged ·
   Rules: "the day continues"; "The Imp remains the execution target". The app
   happens to behave correctly (`aboutToDie` ignores deaths) but tells the ST
   nothing, so they may re-derive the block by hand · `GameActions.kt:296-306`.

8. **P1** · No day-start briefing · Nothing reminds the ST at dawn that a
   Gunslinger is in play and will need prompting after the first tally ·
   `DayScreen.kt:85-124`.

9. **P2** · The "ask voters to raise hands again" affordance is missing · The app
   has `voterIds` in clock order and could simply re-display them as a target
   picker, which is strictly better than asking the table again ·
   `GameState.kt:65`.

10. **P2** · Protections are not surfaced at the moment of the shot · The seat
    sheet does show `deathNotes` (`SeatSheet.kt:242-260`), but only if the ST
    navigates there; a purpose-built shot panel should show them inline (Sailor
    "can't die", Tea Lady "can't die", Fool "the first time they die, they
    don't", Lleech host, Zombuul's first death). Note that Soldier/Monk/Innkeeper
    protections are **Demon/night** specific and do **not** stop a Gunslinger shot
    — the panel must say which apply · `StatusEffects.kt:52-129`.

11. **P2** · Loss of ability is unmodelled · A dead, exiled or poisoned Gunslinger
    cannot shoot; nothing prevents it because nothing implements it.

12. **P3** · No day-guide entry, so the Gunslinger's how-to-run text is nowhere in
    the app.

## Proposed behaviour (spec)

### Derived state

```kotlin
/** The day's first EXECUTION tally, or null if none has been recorded yet. */
fun firstExecutionVoteToday(state: GameState): Nomination? =
    state.nominations.firstOrNull { it.day == state.cycle && !it.isExile }

fun gunslingerActive(state: GameState, lookup: (String) -> Character?): Player? =
    state.players.firstOrNull {
        it.characterId == "gunslinger" && it.alive && !StatusEffects.isImpaired(state, lookup, it)
    }
```

Once-per-day marker: an exclusive reminder `gunslinger:"No ability"` placed on the
Gunslinger's seat the moment the first execution tally is recorded (used or not),
and added to `EXPIRES_AT_DAWN` (`GameActions.kt:218-225`) so it clears with the
new day. This gives the ST a visible grimoire marker and needs no new state field.
(Alternative: derive it entirely — `gunslingerOpportunityOpen(state)` is true only
while the first execution nomination of the day is the most recent one and no
`gunslinger` death has been logged today. The token is preferable because it also
survives the ST closing and reopening the app.)

### The prompt

- **when:** DAY, an active Gunslinger exists, and a nomination has just been
  recorded with `isExile == false` **and** it is the day's first such nomination.
- **where:** a card that appears immediately below the just-recorded nomination in
  `DayScreen`, and again as a banner at the top of the Day tab until dismissed:

  > **Gunslinger — <name> may shoot now.** After the day's first execution vote,
  > they may choose one player **who voted** on it. Ask them now; the chance is
  > gone either way once you move on.

- **targets:** the picker is exactly `nomination.voterIds`, in clock order,
  rendered with character tokens and alive/dead state. No other seat is
  selectable. If `voterIds` is empty, show "Nobody voted — the Gunslinger has no
  legal target today."
- **immediate effects on confirm:**
  - show `StatusEffects.deathNotes(state, lookup, targetId)` **filtered to
    non-night protections**, with an explicit line for each Demon/night-only
    protection that does *not* apply ("Soldier: safe from the Demon — does not
    stop a Gunslinger shot");
  - `GameActions.kill(state, targetId, DeathCause.STORYTELLER)`;
  - place `gunslinger:"No ability"` on the Gunslinger;
  - log `"Gunslinger shot <name> (day N) — not an execution"`.
- **immediate effects on decline (`Not this time`):** place the same token and log
  `"Gunslinger declined to shoot (day N)"`. The rules consume the opportunity
  either way.
- **what does NOT happen:** the day does not end; `aboutToDie` is untouched; the
  Undertaker does not see it; further nominations proceed normally. Say so in the
  confirmation toast: "The day continues. <Blocked player> is still about to die."
- **expiry:** `gunslinger:"No ability"` in `EXPIRES_AT_DAWN`.

### Explicitly excluded triggers

- An **exile** nomination never opens the window and its supporters are never
  targets. `firstExecutionVoteToday` filters `!it.isExile`, which reproduces the
  wiki's Thief-exile example exactly.
- A nomination the **Judge** forced to fail still counts as the day's first
  execution vote, and its `voterIds` are still legal targets — the hands went up.
  (This is why `judge.md` specifies keeping `voterIds` when zeroing `votes`.)
- Under a **Voudon**, only the dead and the Voudon may vote, so the target pool is
  those players; nothing else changes.

### Day-start briefing (shared panel)

> **Gunslinger in play.** After today's **first execution vote** is tallied, ask
> <name> whether they want to shoot. They may pick any player who voted; that
> player dies immediately. It is **not** an execution — the day continues and the
> Undertaker learns nothing. The chance is used up after that first tally whether
> they shoot or not. Exile supporters are never targets.

When the Gunslinger is dead, exiled or poisoned:
> **Gunslinger has no ability** (dead/poisoned) — no shot today.

### Day-time inputs the app must record

- Whether the Gunslinger shot, whom, and on which day — into the log, with the
  "not an execution" annotation so a later Undertaker dispute is settleable.

### Interactions to handle explicitly

- **Saint** — a shot Saint does **not** lose the game for good
  (`WinCheck.kt:51-68` filters `DeathCause.EXECUTION`; verify with a test).
- **Undertaker** — sees nothing (`InfoCalc.kt:281-286`; verify).
- **Mastermind** — the extra day is triggered by the Demon's *execution*, not a
  shot (`WinCheck.kt:28-49`; verify).
- **Devil's Advocate** ("Survives execution") — does not stop a shot; say so.
- **Sailor / Tea Lady / Fool / Lleech / Zombuul** — do apply; surface via
  `deathNotes`.
- **Soldier / Monk / Innkeeper "Protected" / Grandmother** — Demon- or
  night-specific; must be shown as *not* applying.
- **Moonchild / Sweetheart / Barber / Ravenkeeper / Farmer / Poppy Grower** —
  on-death triggers fire normally on a shot; `deathNotes` already lists them
  (`StatusEffects.kt:94-103`).
- **Thief / Bureaucrat** — a marked player who voted is still a voter and a legal
  target, regardless of weight.
- **Scapegoat** — the Scapegoat substitutes for *executions* only; a shot is not
  an execution, so no substitution is offered.

### UI text

- Banner: `Gunslinger — <name> may shoot a voter now`
- Picker header: `Who did the Gunslinger point at? (voters on the first vote only)`
- Decline: `Not this time (the chance is used up either way)`
- Confirm toast: `<Target> is dead — not an execution. The day continues.`
- Spent: `Gunslinger — already had their chance today`

### Data changes

- `characters.json`: add `reminders: ["No ability"]` to `gunslinger` so the
  once-per-day marker has a proper token.
- `GameActions.EXPIRES_AT_DAWN`: add `"gunslinger" to "No ability"`.
- Add a day-guide entry for `gunslinger` with the How to Run text.

## Tests to add

1. `Given` day 2 with an exile recorded first and then an execution nomination,
   `then` `firstExecutionVoteToday(state)` returns the **execution** nomination
   (the exile is skipped) — the wiki's Thief-exile example.
2. `Given` two execution nominations today, `then`
   `firstExecutionVoteToday(state)` returns the first one, and the Gunslinger
   target pool is that nomination's `voterIds`.
3. `Given` an exile nomination with supporters, `then` no Gunslinger window opens
   and none of those supporters is a legal target.
4. `Given` the Gunslinger shoots a voter, `then` the death is recorded with
   `DeathCause.STORYTELLER`, `GameActions.aboutToDie(state)` is unchanged, and
   `InfoCalc.compute(..., "undertaker", ...)` reports no execution today.
5. `Given` the Gunslinger shoots the **Saint**, `then` `WinCheck.check` returns
   `null` (or no `goodWins = false` Saint advisory).
6. `Given` the Gunslinger shoots the **Demon** while a Mastermind is in play,
   `then` `state.mastermindDayActive` is not set by that death.
7. `Given` the Gunslinger declines, `then` the `gunslinger:"No ability"` reminder
   is placed and no further shot is offered today.
8. `Given` a `gunslinger:"No ability"` reminder, `when` `advancePhase` runs
   NIGHT→DAY, `then` the reminder is gone (dawn expiry).
9. `Given` a poisoned or dead Gunslinger, `then` `gunslingerActive(state)` is null
   and no window opens.
10. `Given` a Voudon in play, `when` the first execution vote is tallied, `then`
    the Gunslinger's target pool contains only the dead voters and the Voudon.
11. `Given` a Judge-forced-fail nomination as the day's first execution vote,
    `then` its `voterIds` are still the Gunslinger's target pool.
