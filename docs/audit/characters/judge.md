# Judge (judge) — Bad Moon Rising Traveller

## Official rules (sources)

Sources (fetched 2026-08-25):
- <https://wiki.bloodontheclocktower.com/Judge>
- <https://wiki.bloodontheclocktower.com/Travellers>
- <https://wiki.bloodontheclocktower.com/Glossary> (definitions of *execution*,
  *about to die*, *nomination*)
- Official rulebook, "Travelers" chapter (mirror
  <https://www.web3us.com/sites/default/files/Rulebook.pdf>).

Current ability text (matches `characters.json` exactly):

> "Once per game, if another player nominated, you may choose to force the
> current execution to pass or fail."

How to run (wiki, near-verbatim):

- "**During any nomination period — from declaration through before the next
  nomination — the Judge declares success or failure.** A successful declaration
  **executes the nominee and proceeds to night** (only one execution daily
  permitted). A failed declaration **removes execution threat, resets votes to
  zero, and continues nomination**. The Judge then loses their ability, marked
  with a **NO ABILITY** reminder token."
- Summary clarifications:
  - "The Judge determines whether an execution succeeds, **independent of voting
    outcomes**."
  - "A pardon means no execution occurs and **votes don't count**; a condemnation
    **executes immediately regardless of vote count**."
  - "**The ability functions during or after vote tallying, but only until
    another nomination occurs.**"
  - "**Usage is limited to once per game and only when someone else nominates.**"
- Examples:
  - "When the Slayer faced execution but Po gets nominated and receives all
    votes, an evil Judge can **fail Po's execution, keeping the Slayer as the
    execution target**." — i.e. a failed nomination reverts the block to whoever
    was previously about to die.
  - "A good Judge nominated the Professor (**no Judge ability usable**). Later,
    when the Grandmother nominates the Goon with minimal votes, the Judge
    **forces immediate execution**."
- Strategy notes that imply timing: "Lock in Demon executions when evil players
  might nominate alternatives" — a forced *pass* is final for the day; later
  nominations cannot displace it.

Reading of "force the current execution to pass":
- The nominee is executed. Per the wiki, this "proceeds to night" — the day ends,
  no further nominations, and it is a genuine **execution** (Undertaker sees it,
  Saint/Fearmonger/Devil's Advocate/Scapegoat all apply as normal).
- The Scapegoat interaction is worth stating explicitly: a forced pass is still an
  execution of a player, so a same-alignment Scapegoat may still be executed
  instead (Storyteller's choice).

Reading of "force the current execution to fail":
- The nominee's tally becomes zero; they are not about to die; the day continues
  and more nominations may follow. Any player who was on the block **before** this
  nomination goes back on the block (wiki Po/Slayer example).

Traveller framework (rulebook, verbatim; applies to all travellers):
- "**Choose Alignment. Tell the Traveler player in private whether they are good
  or evil. If you made the Traveler evil, they learn which player is the Demon…**"
- "Travelers… **lose their abilities when dead or drunk or poisoned**."
- Exile: "**If at least half of the players support the exile, it succeeds**…
  This counts the total number of players in the game, not the number of alive
  players." · "Any player, even dead ones, may support the exile… Dead players
  that support an exile do not lose their vote token." · "not affected by
  abilities" · "Calling for an exile is not a nomination, so a player who calls
  for an exile may also nominate someone on the same day."

Jinxes: none for the Judge in `night_and_jinxes.json` or on the wiki.

Night order: the Judge never acts at night; correctly absent from both lists.

## What the app does today

Data
- `characters.json` — `judge`, team `traveler`, ability text correct,
  `reminders: ["No ability"]`. Correct.
- `night_guide.json` — **no `judge` entry**.
- `night_and_jinxes.json` — absent from both orders. Correct.

Engine
- Nothing references `judge`. `grep -rn "judge" engine/src app/src` matches only
  `characters.json`.
- The execution model is entirely derived from the nomination sequence:
  - `Voting.outcome(votes, threshold, currentHighest)` (`GameState.kt:141-152`)
    → `SAFE` / `ABOUT_TO_DIE` / `TIED`.
  - `GameActions.aboutToDie(state)` (`GameActions.kt:296-306`) replays today's
    non-exile nominations: `ABOUT_TO_DIE` sets the block, `TIED` clears it,
    anything else is ignored. There is no way to say "this nomination's result is
    forced" or "the day is locked".
  - `GameActions.highestVotesToday` (`GameActions.kt:278-282`) is the
    tally-to-beat, taken from `votes` on passing/tied nominations.
- `NominationResult` (`GameState.kt:56`) has only `ABOUT_TO_DIE, SAFE, TIED,
  WITHDRAWN`.

UI
- `DayScreen.kt:161-251` computes the result purely from the tapped voter count
  and threshold and offers `Record`. There is no Judge control anywhere.
- `DayScreen.kt:93-115` renders the "On the block: X" banner with an `Execute`
  button; `NominationRow` (`DayScreen.kt:308-360`) shows an `Execute`/`Exile`
  button only when the stored result is `ABOUT_TO_DIE` **and** the nominee is
  still the derived on-block player.
- `GameShell.kt:592-616` — the dusk guard offers "Execute & begin night" for the
  on-block player.
- The `No ability` reminder token: the once-per-game "Mark spent" chip in the
  night tray (`NightScreen.kt:263-279`) only exists inside a night step, and the
  Judge has none. `ReminderPicker` (`SeatSheet.kt:489-560`) does not list
  traveller reminders at all, since built-in scripts are filtered to
  `team.isTownResident` (`GameData.kt:39`). So the Judge's own token is
  unreachable; the ST must use the generic `Used` token (`SeatSheet.kt:502`).
- Alignment: defaults to good for every traveller (`Character.kt:16`,
  `GameState.kt:45-51`); only the bare "Flip alignment" button
  (`SeatSheet.kt:315`).

Storyteller experience today: to force a pass with 2 votes when 5 are needed,
the ST must fake the tally — tap phantom voters until the count clears the
threshold — or record the nomination as `SAFE` and then go to the seat sheet and
press "Executed" by hand, leaving the day's record wrong and the on-block
derivation stale. To force a fail, they must record the nomination with the real
votes and then remember, unaided, that the previous player is back on the block —
`aboutToDie` will have already moved the block to the new nominee.

## Defects and gaps

1. **P0** · No way to force an execution to pass · Rules: the nominee is executed
   regardless of vote count and the day ends · App: `Voting.outcome` /
   `aboutToDie` are pure functions of vote counts; the only workaround is to
   record a false tally · `GameState.kt:141-152`, `GameActions.kt:296-306`,
   `DayScreen.kt:197-247` · Repro: 9 alive (threshold 5), nominate with 2 votes →
   the app says "safe" and offers no execute button.

2. **P0** · No way to force an execution to fail, and the previous block is not
   restored · Rules: votes reset to zero, the day continues, and whoever was
   about to die before this nomination is about to die again (wiki Po/Slayer
   example) · App: once a passing tally is recorded, `aboutToDie` moves the block
   permanently; deleting/undoing the nomination is the only recourse and the
   record is then lost · `GameActions.kt:296-306`.

3. **P0** · A forced pass does not end the day · Rules: "executes the nominee and
   proceeds to night… only one execution daily permitted." App: nothing stops
   further nominations after an execution is recorded, and `hasNominatedToday`
   still lets other players nominate · `DayScreen.kt:131-152`,
   `GameActions.kt:285-289`.

4. **P0** · The "another player nominated" precondition is unenforced and
   unshown · Rules: the Judge may not act on their own nomination · App: no
   check, no warning · `DayScreen.kt:154-159` (`nominationWarnings` has no Judge
   case, `StatusEffects.kt:132-166`).

5. **P1** · Once-per-game is not tracked · Rules: on use the Judge is marked NO
   ABILITY · App: the `No ability` token is unreachable from the seat sheet, and
   the night tray's "Mark spent" affordance is night-only ·
   `SeatSheet.kt:489-560`, `GameData.kt:33-43`, `NightScreen.kt:263-279`.

6. **P1** · No timing window model · Rules: the Judge may declare "during or after
   vote tallying, but only until another nomination occurs." · App: nominations
   are recorded atomically with no "current nomination still open" state, so the
   window cannot be presented or enforced · `DayScreen.kt:217-251`.

7. **P1** · No day-start briefing · Nothing reminds the ST at dawn that a Judge
   is in play and still has their ability — the single most-forgotten thing about
   this character.

8. **P2** · The game log cannot represent a forced result · A forced pass with 2
   votes will read "2 votes, safe" or "2 votes, reached the block" with no
   indication the Judge acted · `GameExtras.kt:65-79`.

9. **P2** · Downstream execution consequences are not linked · A forced pass is a
   real execution: Undertaker (`InfoCalc.kt:281-286` filters
   `DeathCause.EXECUTION` — fine once the kill is recorded with the right cause),
   Saint (`WinCheck.kt:51-68`), Fearmonger (`StatusEffects.kt:158-160`), Devil's
   Advocate ("Survives execution", `StatusEffects.kt:68`), Scapegoat, Mastermind
   (`WinCheck.kt:28-49`). None of these are surfaced at the moment of a forced
   pass because there is no such moment.

10. **P2** · No alignment on the traveller, so the ST gets no read on how likely
    the Judge is to be lying about their intent; the grimoire shows them as good
    by default (`GrimoireScreen.kt:387-395`).

11. **P3** · No `night_guide`/day-guide entry: the Judge's how-to-run text lives
    nowhere in the app.

## Proposed behaviour (spec)

The Judge needs an **explicit nomination outcome override** in the engine, plus a
day-phase control. Model it as data on the nomination, not as a fudged tally.

### State

- `NominationResult` gains `FORCED_PASS` and `FORCED_FAIL`, **or** (preferred,
  less churn for existing consumers) `Nomination` gains:
  ```kotlin
  val forcedBy: String? = null      // "judge"
  val forced: ForcedOutcome? = null // PASS | FAIL
  ```
  and `result` is set to `ABOUT_TO_DIE` / `SAFE` accordingly so existing
  consumers keep working.
- `GameState` gains `val executionLocked: Boolean` — derived: true when today has
  a nomination with `forced == PASS`, or an execution death has been recorded
  today.
- `Player.alignment` (explicit; see the shared arrival flow in `apprentice.md`).

### Engine rules

- `GameActions.aboutToDie` (`GameActions.kt:296-306`) replay becomes:
  - `forced == PASS` → block = this nominee, and **stop replaying** (nothing
    later can displace it);
  - `forced == FAIL` → **ignore this nomination entirely** when computing the
    block *and* the tally-to-beat, so the previously-blocked player is restored
    automatically;
  - otherwise as today.
- `GameActions.highestVotesToday` (`GameActions.kt:278-282`) must skip
  `forced == FAIL` nominations (their votes "don't count") and must not let a
  `forced == PASS` nomination with a low tally lower the bar for others — since
  replay stops at a forced pass, this falls out naturally.
- `duskBlockers` / day-end: with `executionLocked`, `DayScreen` disables the
  Nominator/Nominee pickers and shows "Execution locked by the Judge — the day
  ends now."

### Judge control (day phase)

- **when:** DAY, a Judge seat exists, alive, not impaired, and no
  `judge:"No ability"` reminder on it.
- **where:** two things.
  1. Inline, in the vote panel, next to the computed result, once a nominee and a
     nominator are selected and the nominator is **not** the Judge:
     `Judge: force PASS` · `Judge: force FAIL`.
     Disabled with the reason "The Judge cannot use their ability on their own
     nomination" when `nominatorId == judgeSeatId`.
  2. On the most recently recorded nomination row (`DayScreen.kt:308-360`), the
     same two buttons, available **until another nomination is recorded** —
     matching "from declaration through before the next nomination". Once a newer
     nomination exists, the buttons disappear from the older row.
- **effect of `force PASS`:** record/patch the nomination with
  `forced = PASS, result = ABOUT_TO_DIE`; place exclusive
  `judge:"No ability"` on the Judge; show a confirm sheet that first lists
  `StatusEffects.deathNotes(state, lookup, nomineeId)` (Devil's Advocate,
  Fool, Sailor, Tea Lady, Fearmonger, Saint, Scapegoat substitution) and then, on
  confirm, `kill(nominee, DeathCause.EXECUTION)` and offers "Begin night".
- **effect of `force FAIL`:** patch the nomination to `forced = FAIL,
  result = SAFE, votes = 0` (keep `voterIds` for the record so Gunslinger/
  Flowergirl history is intact — see below), place `judge:"No ability"`, and show
  "Nominations continue. <PreviousBlockName> is about to die again." (or
  "No one is about to die.")
  - **Keep `voterIds`.** The Gunslinger's "player that voted" and the
    Flowergirl's "did the Demon vote" both key off who raised a hand, which
    happened regardless of the Judge's override. Only `votes` (the tally) is
    zeroed. `InfoCalc.flowergirl` (`InfoCalc.kt:296-313`) reads `voterIds`, so
    this is the right split.
- **expiry:** `judge:"No ability"` never expires (once per game). Do **not** add
  it to `EXPIRES_AT_DAWN`/`EXPIRES_AT_DUSK`.
- **loss of ability:** if the Judge dies, is exiled, or is drunk/poisoned before
  using it, the controls disappear (travellers lose abilities when dead or
  impaired). The `No ability` token is not placed in that case — if they are
  later resurrected and still unspent, the ability is back.

### Day-start briefing (shared panel)

> **Judge in play — ability unspent.** Once per game, on someone else's
> nomination, they may force the execution to pass (nominee dies, day ends) or
> fail (votes reset to 0, day continues). They may not use it on their own
> nomination.

Once spent, the panel line becomes "Judge — ability spent (day N)".

### Day-time inputs the app must record

- Which nomination the Judge acted on, in which direction, on which day — into
  the game log: `"Judge forced Po's execution to FAIL (2 votes discarded)"`.

### Interactions to handle explicitly

- **Scapegoat:** a forced pass is an execution; the substitution offer must still
  appear.
- **Devil's Advocate / Fool / Sailor / Tea Lady:** protections still apply; show
  `deathNotes` before the kill.
- **Saint / Fearmonger / Mastermind:** win-condition consequences still apply.
- **Undertaker:** a forced pass is a normal execution and is seen.
- **Bishop:** a Storyteller nomination is not "another player nominated" in the
  ordinary sense but it certainly is not the Judge's own — treat a Bishop
  Storyteller nomination as usable by the Judge, and note this in the UI text.
- **Voudon:** with a Voudon in play the threshold is 1 vote; the Judge override
  is orthogonal and stacks normally.
- **Exile:** the Judge cannot force an exile. The controls must be hidden when
  `isExile == true` ("Exiles are not affected by abilities").

### UI text

- Buttons: `Judge — force PASS (nominee dies, day ends)` /
  `Judge — force FAIL (votes reset to 0)`
- Disabled reason: `Judge can't override their own nomination`
- After use: `Judge ability spent`
- Locked day: `Execution locked by the Judge — no more nominations today`

### Data changes

- `characters.json`: none.
- Add a day-guide entry for `judge` with the How to Run text above.

## Tests to add

1. `Given` day 2 with 9 alive (threshold 5) and a nomination of P with 2 votes,
   `when` the nomination is recorded with `forced = PASS`, `then`
   `GameActions.aboutToDie(state) == P` even though `votes < threshold`.
2. `Given` P is on the block from a passing 5-vote nomination, `when` a later
   nomination of Q with 6 votes is recorded with `forced = FAIL`, `then`
   `aboutToDie(state) == P` and `highestVotesToday(state) == 5` (Q's 6 votes are
   discarded).
3. `Given` a nomination recorded with `forced = FAIL`, `then`
   `nomination.votes == 0` and `nomination.voterIds` is unchanged, and
   `InfoCalc.compute(..., "flowergirl", ...)` still reports YES when the Demon's
   id is in those `voterIds`.
4. `Given` a `forced = PASS` nomination today, `then` `executionLocked(state)` is
   true and any subsequent `ABOUT_TO_DIE` nomination does not move
   `aboutToDie(state)`.
5. `Given` a Judge seat, `when` the force action is applied, `then` the Judge has
   exactly one `PlacedReminder("judge","No ability")` and a second force action is
   rejected.
6. `Given` a Judge with a `No ability` reminder, `when` `advancePhase` runs
   through dawn and dusk, `then` the reminder is still present (not in either
   expiry table).
7. `Given` a nomination whose `nominatorId` is the Judge's seat, `then`
   `judgeCanForce(state, nomination) == false`.
8. `Given` an exile nomination (`isExile = true`), `then`
   `judgeCanForce(state, nomination) == false`.
9. `Given` a Judge who is exiled before using the ability, `then`
   `judgeCanForce(...)` is false and no `No ability` token was placed.
10. `Given` a forced-pass execution of a Saint, `then` `WinCheck.check` returns
    `goodWins = false` (the forced pass is a genuine execution).
