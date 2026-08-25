# Bishop (bishop) — Bad Moon Rising Traveller

## Official rules (sources)

Sources (fetched 2026-08-25):
- <https://wiki.bloodontheclocktower.com/Bishop>
- <https://wiki.bloodontheclocktower.com/Travellers>
- Official rulebook, "Travelers" chapter (mirror
  <https://www.web3us.com/sites/default/files/Rulebook.pdf>) for arrival and exile.

Current ability text (matches `characters.json` exactly):

> "Only the Storyteller can nominate. At least 1 opposing player must be
> nominated each day."

How to run (wiki, near-verbatim):

- "**Each dawn, mark a good Bishop with their NOMINATE EVIL reminder, or mark an
  evil Bishop with their NOMINATE GOOD reminder.**"
- "During the nomination process, **players cannot make nominations, though you
  can**. Voting proceeds normally."
- "**When nominating a player with alignment opposite the Bishop's, remove the
  Bishop's reminder. The nomination process cannot end while the Bishop retains
  their reminder.**"
- "Typically nominate three to five players daily, including at least one evil
  player. You need not nominate the Demon daily, but **nominate all living
  players on the final day**. Nominate primarily evil players if the Bishop is
  good; nominate fewer evil players if the Bishop is evil."
- Summary clarifications: "The Bishop prevents standard player nominations…
  The Storyteller may nominate as many or as few players as desired, but must
  nominate at least one player whose alignment opposes the Bishop's alignment
  each day. **Voting remains unchanged for all players. Since Travellers are
  exiled rather than executed, any player may call for the Bishop or another
  Traveller's exile.**"
- Example (evil Bishop): "The Bishop is exiled that day, **allowing normal
  nomination processes to resume**." — i.e. the effect ends the moment the Bishop
  loses their ability (exiled/dead/drunk/poisoned).
- Good-side tip that the app should support: "Monitor carefully which players the
  Storyteller nominates each day… Track nominees across days to narrow down the
  evil pool." The Bishop's whole value is the *record* of who was nominated on
  each day.

Traveller framework (rulebook, verbatim, applies to every traveller):

- Arrival: "**Choose Alignment. Tell the Traveler player in private whether they
  are good or evil. If you made the Traveler evil, they learn which player is the
  Demon, but not which players are the Minions.**" and "**Inform Group. Declare
  that a Traveler is now in play, which player and which character it is, and what
  their ability is. (Do not declare their alignment.)**"
- "Travelers… may nominate, may vote, may act at night… **lose their abilities
  when dead or drunk or poisoned**, and even get a vote token when they die."
- Exile: "**If at least half of the players support the exile, it succeeds**…
  This counts the total number of players in the game, not the number of alive
  players." · "Any player, even dead ones, may support the exile… Dead players
  that support an exile do not lose their vote token." · "The process to exile a
  Traveler is **not affected by abilities**." · "Calling for an exile is not a
  nomination, so a player who calls for an exile may also nominate someone on the
  same day." · "each Traveler can only be called to exile once per day."

Note the interaction that follows directly: **the Bishop does not stop exiles.**
Players may still call exiles freely on any traveller, including the Bishop.

Jinxes: none for the Bishop in `night_and_jinxes.json` or on the wiki.

Night order: the Bishop never acts at night; correctly absent from both lists.

## What the app does today

Data
- `characters.json` — `bishop`, team `traveler`, ability text correct,
  `reminders: ["Nominate good", "Nominate evil"]`, no night reminders. Correct.
- `night_guide.json` — **no `bishop` entry** (correct: no night step, but it also
  means there is nowhere the app explains the Bishop to the ST at all).
- `night_and_jinxes.json` — absent from both orders. Correct.

Engine
- Nothing anywhere references `bishop`. `grep -rn "bishop" engine/src app/src`
  returns only the `characters.json` entry.
- `GameActions.hasNominatedToday` / `hasBeenNominatedToday`
  (`GameActions.kt:285-289`) enforce one nomination per nominator and one
  nomination per nominee per day, with exiles excluded — the "normal" rules.
  There is no notion of a Storyteller nominator.
- `StatusEffects.nominationWarnings` (`StatusEffects.kt:132-166`) handles Witch,
  Golem, Virgin, Fearmonger and Cerenovus. No Bishop hook.

UI
- `DayScreen.kt:131-140` — the **Nominator** chip row is `state.players` with
  `enabled = p.alive && !hasNominatedToday(...)`. Any alive player, including the
  Bishop, can be recorded as the nominator. There is no "Storyteller" chip and no
  block on player nominations.
- `DayScreen.kt:268-276` — the day footer says "When dusk falls, execute whoever
  is on the block… then advance to night." No condition prevents dusk.
- `GameShell.kt:126-146,592-616` — the dusk guard only checks whether someone is
  on the block; it never checks a Bishop obligation.
- `SeatSheet.kt:489-560` (`ReminderPicker`) lists reminders only for characters in
  `gameData.resolve(script)`; built-in scripts are filtered to
  `team.isTownResident` (`GameData.kt:39`), so **the Bishop's "Nominate good" /
  "Nominate evil" tokens can never be placed** — the ST is left with the generic
  `Good` / `Evil` tokens (`SeatSheet.kt:502`).
- The night tray route that *does* reach traveller reminders
  (`NightScreen.kt:283-313`) is unavailable, because the Bishop has no night step.
- Alignment: as for every traveller, defaults to good
  (`Character.kt:16`, `GameState.kt:45-51`) with only a bare "Flip alignment"
  button (`SeatSheet.kt:315`). The app therefore cannot know which of the two
  Bishop tokens applies.
- The Game log (`GameExtras.kt:65-79`) does record every nomination per day with
  nominator, nominee, votes and outcome — this is the raw material the Bishop
  player wants, and it works.

Storyteller experience today: the app is a passive spectator. The ST must
remember that only they may nominate, remember the Bishop's alignment, remember
the "at least one opposing player" obligation, remember not to close nominations
until it is met, and hand-record each of their own nominations by tapping their
own name (or an arbitrary player) into the Nominator chip row, which then
wrongly consumes that player's one nomination for the day.

## Defects and gaps

1. **P0** · No Storyteller-nominator mode · Rules: "Only the Storyteller can
   nominate." App: nominations are always attributed to a player, and recording
   one burns that player's single nomination for the day
   (`GameActions.hasNominatedToday`, `GameActions.kt:285-286`) and can trigger
   nomination-time abilities that should not fire (Witch "dies for nominating",
   Golem, Virgin — `StatusEffects.kt:142-157`) · `DayScreen.kt:131-140` ·
   Repro: put a Bishop in play, go to Day, try to record a Storyteller
   nomination — you must pick a player, and a Virgin nominee then shows the
   "if the nominator is a Townsfolk they are executed" warning.

2. **P0** · Player nominations are not blocked · Rules: while a sober, living
   Bishop is in play, players cannot nominate at all · App: every alive player is
   an enabled Nominator chip · `DayScreen.kt:135-138`.

3. **P0** · The "at least 1 opposing player" obligation is not tracked or
   enforced · Rules: "The nomination process cannot end while the Bishop retains
   their reminder." App: nothing places the token, nothing counts opposing
   nominees, and the Dusk button will happily end the day with none ·
   `GameShell.kt:126-146,592-616`, `DayScreen.kt:257-276`.

4. **P0** · Alignment unknown, so the app cannot even name the right obligation ·
   `NOMINATE EVIL` (good Bishop) vs `NOMINATE GOOD` (evil Bishop) depends on the
   alignment the ST chose privately at arrival, which the app never asks for ·
   `GameShell.kt:663-682`, `SeatSheet.kt:315`, `GameState.kt:45-51`.

5. **P1** · Bishop reminder tokens are unreachable · `ReminderPicker` never
   offers traveller tokens because built-in scripts exclude travellers, and the
   Bishop has no night step to open the night tray from · `GameData.kt:33-43`,
   `SeatSheet.kt:489-560`.

6. **P1** · No dawn hook to place the token · "Each dawn, mark a good Bishop with
   their NOMINATE EVIL reminder." The app has an `EXPIRES_AT_DAWN` sweep
   (`GameActions.kt:218-225,258-263`) but no *placement* at dawn for anything ·
   `GameActions.advancePhase`.

7. **P1** · No day-start briefing · The single most useful thing here is a Day-N
   panel: "You do all nominating today. You must still nominate at least one
   **evil** player before dusk. Nominated so far: none." Nothing of the kind
   exists — `DayScreen.kt:85-124` shows only alive count and threshold.

8. **P2** · The Bishop's own deduction material is not surfaced · The good Bishop
   wants "who did the ST nominate on each day". The log has it
   (`GameExtras.kt:65-79`) but buried in a modal mixed with deaths, and with no
   per-day grouping or alignment annotation for the ST's own reference.

9. **P2** · Final-day rule not surfaced · "nominate all living players on the
   final day" — no prompt when few players remain.

10. **P2** · The ability's end conditions are unmodelled · When the Bishop is
    exiled, dies, or is drunk/poisoned, normal nominations resume immediately
    (wiki example scenario 2; rulebook "lose their abilities when dead or drunk
    or poisoned"). Nothing changes in the app because nothing was ever restricted.

11. **P3** · No `night_guide.json` entry, and travellers have no equivalent
    "day guide", so the Bishop's how-to-run text lives nowhere in the app.

## Proposed behaviour (spec)

This character needs no night step; it needs a **day-phase rules engine hook**.
Express it as a day-scoped modifier so other day-active travellers (Judge,
Gunslinger, Matron, Voudon) can share the machinery.

### State

- `Player.alignment: Alignment` (explicit, set at traveller arrival — see the
  shared arrival flow in `apprentice.md`).
- `Nomination.nominatorId: Long?` becomes nullable, with `null` meaning **the
  Storyteller**. (Or add `Nomination.byStoryteller: Boolean`; nullable id is
  cleaner for the record and for the log.)
- Derived, not stored: `bishopActive(state) = state.players.any { it.characterId == "bishop" && it.alive && !isImpaired(it) }`.

### Day rules while a Bishop is active

- **Nominator picker:** replace the player chip row with a single pinned
  "Storyteller" chip, first in the row, selected by default; all player chips are
  disabled with the tooltip "Bishop: only the Storyteller can nominate."
  A Storyteller nomination:
  - does **not** consume any player's one-nomination-per-day
    (`hasNominatedToday` must ignore `nominatorId == null`);
  - does **not** fire nominator-side triggers (Witch curse, Golem, Cerenovus
    madness check) — `StatusEffects.nominationWarnings` must skip the nominator
    block when `nominatorId == null`;
  - **does** fire nominee-side triggers. Explicitly flag the Virgin: a Storyteller
    nomination has no Townsfolk nominator, so the Virgin does not trigger — show
    "Virgin: the Storyteller is not a player, so nothing happens" instead of the
    normal warning.
- **Obligation token:** at every dawn, while a Bishop is alive and unimpaired,
  auto-place the exclusive reminder
  `bishop:"Nominate evil"` (good Bishop) or `bishop:"Nominate good"` (evil Bishop)
  on the Bishop's own seat. Remove it the moment a nomination is recorded whose
  **nominee's alignment opposes the Bishop's**. Re-place it at the next dawn.
- **Dusk guard:** if the token is still present when the ST presses Dusk, block
  with a dialog: "Bishop: you have not yet nominated an **evil** player today.
  Nominations cannot end." with buttons `Back to nominations` and, for escape
  hatches, `Override (rule waived)` which logs the override.
- **Expiry:** add `bishop → "Nominate good"` and `bishop → "Nominate evil"` to
  `EXPIRES_AT_DUSK` (`GameActions.kt:231-242`) so a manually-placed token also
  clears, and re-place at dawn as above.
- **End conditions:** the moment the Bishop dies, is exiled or gains a
  Drunk/Poisoned reminder, drop the token, drop the dusk guard, and restore the
  normal nominator picker with an inline notice: "Bishop has lost their ability —
  players nominate normally again."
- **Exiles are unaffected:** the exile flow must keep letting **any player**
  (alive or dead) call for the exile of a traveller, including the Bishop, while
  the Bishop is active. This is the single most likely regression when
  implementing defect 2 — the exile caller picker must be exempt.

### Day-start briefing panel (new, shared)

At the top of `DayScreen` when a Bishop is active:

> **Bishop — you nominate today.** Players may not nominate (they may still call
> exiles). You must nominate at least one **evil** player before dusk.
> Nominated so far: *Alice (good), Bob (good)* — **still needed: 1 evil.**
> Suggested: 3–5 nominations. On the final day, nominate every living player.

Alignment labels here are storyteller-only (the grimoire is already
alignment-revealing at `GrimoireScreen.kt:387-395`).

### Day-time inputs the app must record

- Each Storyteller nomination, with day number, nominee and the nominee's true
  alignment. Surface a per-day "ST nominated:" list in the log, grouped by day,
  because the Bishop player is publicly reasoning about exactly this list.

### UI text

- Nominator row header while active: `Nominator — Bishop in play: Storyteller only`
- Chip: `Storyteller`
- Obligation banner: `Nominate evil — required before dusk` (or `Nominate good`).
- Dusk block: `Bishop: nominations can't end until you nominate an evil player.`

### Data changes

- `night_guide.json`: add a `bishop` entry under a new `day` key (or a parallel
  `day_guide.json`) with the How-to-Run text above, so the Day tab can render the
  same run-book the Night tab gets.
- `characters.json`: no change needed.

## Tests to add

1. `Given` a game with an alive, unimpaired good Bishop, `when` `advancePhase`
   moves NIGHT→DAY, `then` the Bishop's seat holds exactly one
   `PlacedReminder("bishop","Nominate evil")` and no `"Nominate good"`.
2. `Given` an alive evil Bishop, `when` dawn breaks, `then` the placed token is
   `"Nominate good"`.
3. `Given` a good Bishop and a recorded Storyteller nomination of a **good**
   player, `then` the `"Nominate evil"` token is still present and
   `bishopObligationMet(state) == false`; `when` an **evil** player is then
   nominated, `then` the token is gone and `bishopObligationMet(state) == true`.
4. `Given` an unmet Bishop obligation on day 2, `when` `advancePhase` is
   requested, `then` the engine-level `duskBlockers(state)` returns a
   Bishop entry (the UI turns this into the guard dialog).
5. `Given` a Bishop in play, `when` a nomination is recorded with
   `nominatorId = null`, `then` `hasNominatedToday` returns false for every
   player and `StatusEffects.nominationWarnings` emits no nominator-side warning
   (specifically: a Witch-cursed player is not flagged).
6. `Given` a Bishop in play and a Virgin nominee, `when` the nominator is the
   Storyteller, `then` the warning list contains the "Storyteller is not a
   player" note and not the "executed immediately" note.
7. `Given` an alive Bishop, `when` the Bishop is exiled, `then` the obligation
   token is removed, `duskBlockers(state)` is empty, and player nominations are
   allowed again.
8. `Given` an alive Bishop with a `Poisoned` reminder, `then` the same
   restoration happens (travellers lose abilities when drunk/poisoned).
9. `Given` a Bishop in play, `when` a dead player calls an exile on the Bishop,
   `then` the exile is recordable (the Bishop restriction must not leak into the
   exile path) and its threshold is `Voting.exileThreshold(players.size)`.
