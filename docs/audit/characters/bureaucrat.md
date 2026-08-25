# Bureaucrat (bureaucrat) — Trouble Brewing Traveller

## Official rules (sources)

Sources (fetched 2026-08-25):
- <https://wiki.bloodontheclocktower.com/Bureaucrat>
- <https://wiki.bloodontheclocktower.com/Thief> (the Bureaucrat×Thief stacking
  ruling is stated there)
- <https://wiki.bloodontheclocktower.com/Travellers>
- Official rulebook, "Travelers" chapter (mirror
  <https://www.web3us.com/sites/default/files/Rulebook.pdf>).

Current ability text (matches `characters.json` exactly):

> "Each night, choose a player (not yourself): their vote counts as 3 votes
> tomorrow."

How to run (wiki, verbatim):

- "**Each night, wake the Bureaucrat. They point at any player. Mark the chosen
  player with the Bureaucrat's 3 VOTES reminder. Put the Bureaucrat to sleep.**"
- "**Each time you tally the vote of a player marked 3 VOTES, count it as three
  votes instead of one. (Count this out loud, as normal.)**"
- Key mechanics:
  - "**The tripled vote applies every time that marked player votes during the
    day.**" (not once — every nomination that day)
  - "**The effect ends immediately if the Bureaucrat dies or is exiled.**"
  - "**Exiles are unaffected by abilities, so a marked player can only vote once
    for exiles, not three times.**"
  - "**All players learn who received the bonus votes, as the Storyteller
    announces the vote count aloud.**" — the tripling is *public information*, and
    the ST is required to count out loud so the group can hear the jump.
  - "**Dead players with triple votes can use their single vote token with
    amplified effect.**" (a dead marked player's one ghost vote counts as 3)
- Storyteller guidance: "Announce vote tallies loudly so players understand the
  Bureaucrat's influence. The ability requires transparent vote counting to
  function within the social game."
- Bureaucrat × Thief (from the Thief page): "if both coordinate on same player,
  produces **3 negative votes**."

Traveller framework (rulebook, verbatim):

- "**Choose Alignment. Tell the Traveler player in private whether they are good
  or evil. If you made the Traveler evil, they learn which player is the Demon…**"
- "Travelers… may act at night… **lose their abilities when dead or drunk or
  poisoned**."
- Exile: "**The process to exile a Traveler is not affected by abilities. It is
  purely a group decision. For example, characters that modify votes (Thief,
  Bureaucrat, etc.) do not modify support for exile.**" ·
  "**If at least half of the players support the exile, it succeeds**… total
  number of players in the game." · "Any player, even dead ones, may support the
  exile… Dead players that support an exile do not lose their vote token."

Jinxes: none for the Bureaucrat in `night_and_jinxes.json` or on the wiki.

Night order: `firstNight` and `otherNight` both, very early — right after
`barista`, before `thief`. The app's data matches (`firstNight[5]`,
`otherNight[2]`).

Timing note: the token is placed at night and governs **tomorrow**. In practice
it sits on the target through that day and is moved on the next night, which is
what the app's exclusive-token behaviour already produces.

## What the app does today

Data
- `characters.json` — `bureaucrat`, team `traveler`, ability text correct,
  `reminders: ["3 votes"]`, and both night reminders are the official strings
  ("The Bureaucrat points to a player. Put the Bureaucrat's '3 votes' reminder by
  the chosen player's character token."). Correct.
- `night_and_jinxes.json` — `firstNight[5]`, `otherNight[2]`. **Correct.**
- `night_guide.json` — `bureaucrat.first` and `bureaucrat.other` both present,
  with good text; `other` even says "Remove the 3 votes reminder from the
  previously chosen player." `shows: []`. Correct as prose.

Engine
- Nothing in production code references `bureaucrat`. `grep -rn "bureaucrat"
  engine/src app/src` matches only the data files.
- `EXPIRES_AT_DAWN` / `EXPIRES_AT_DUSK` (`GameActions.kt:218-242`) do **not**
  include `("bureaucrat","3 votes")`.
- The vote tally is a plain count of selected ids: `orderedVoterIds.size`
  (`DayScreen.kt:172,176,198,204,225`). There is no weighting anywhere in the
  engine or the UI.
- `Voting.outcome` (`GameState.kt:141-152`) takes an `Int` tally, so a weighted
  tally slots in without signature change.

UI
- The night step **works**: `NightOrder` emits a `Bureaucrat` row on every night
  with the official reminder text (`NightOrder.kt:142-178`); the step panel shows
  the night-guide prose (`NightScreen.kt:792-800`); the night tray offers the
  `3 votes` token and, because `characters.json` declares exactly one copy, taps
  route through `GameActions.placeExclusiveReminder`
  (`NightScreen.kt:314-345`), so the token **moves** rather than accumulating.
  This is the one traveller in this scope whose night handling is essentially
  right.
- The day is where it fails: the vote panel counts hands
  (`DayScreen.kt:161-247`) and knows nothing about the token.
- `ReminderPicker` (`SeatSheet.kt:489-560`) cannot offer the `3 votes` token
  outside the night tray, because built-in scripts exclude travellers
  (`GameData.kt:39`). If the ST wants to place or fix the token during the day
  they must use a generic token.
- Nothing removes the token when the Bureaucrat dies or is exiled.
- Alignment defaults to good like every traveller (`Character.kt:16`,
  `GameState.kt:45-51`); bare "Flip alignment" (`SeatSheet.kt:315`).

Storyteller experience today: the token gets placed correctly at night, and then
during the day the app tells them "4 so far, needs 5 … <name> is safe" while the
true tally is 6 and the nominee is about to die. The ST has to do the arithmetic
in their head on every nomination, and remember to strip the token the moment the
Bureaucrat is exiled — which, for a traveller with a public and hated ability, is
often the same day.

## Defects and gaps

1. **P0** · The ×3 multiplier is never applied to the tally · Rules: "Each time
   you tally the vote of a player marked 3 VOTES, count it as three votes instead
   of one." App: `orderedVoterIds.size` · `DayScreen.kt:172,176,198,204,225` ·
   Repro: place the `3 votes` token, open a nomination with 9 alive (threshold 5),
   tap 3 voters including the marked one — the app reports "3 so far, needs 5 …
   safe"; the correct tally is 5 and the nominee is about to die.

2. **P0** · The recorded `Nomination.votes` is the hand count, so the game log,
   the tally-to-beat and the on-block derivation are all wrong for the rest of the
   day · `DayScreen.kt:225`, `GameActions.highestVotesToday`
   (`GameActions.kt:278-282`), `GameActions.aboutToDie` (`GameActions.kt:296-306`),
   `GameExtras.kt:74-79`.

3. **P0** · Exiles would be multiplied too, once weighting exists · Rules:
   "Exiles are unaffected by abilities, so a marked player can only vote once for
   exiles, not three times." The exile branch shares the same counter
   (`DayScreen.kt:197-202`), so any naive fix would break this · must be specified
   now, before the multiplier is added.

4. **P1** · The token is not removed when the Bureaucrat loses their ability ·
   Rules: "The effect ends immediately if the Bureaucrat dies or is exiled" (and
   travellers lose abilities when drunk or poisoned) · App: `GameActions.kill`
   (`GameActions.kt:136-156`) touches no reminders; nothing watches for the
   Bureaucrat's death · Repro: mark a player, exile the Bureaucrat at noon, the
   `3 votes` token stays on the target all day.

5. **P1** · The token never expires · It governs "tomorrow" only and should be
   swept at dusk so a night on which the Bureaucrat is dead/absent does not leave
   a stale multiplier · `GameActions.kt:231-242` (`EXPIRES_AT_DUSK` lacks it).

6. **P1** · The "not yourself" constraint is unenforced · The night tray's seat
   picker lists every player including the Bureaucrat
   (`NightScreen.kt:313-352`) · `characters.json` night reminder says "points to a
   player" but the ability says "not yourself".

7. **P1** · The public count-out-loud is not supported · Rules: "Count this out
   loud, as normal… All players learn who received the bonus votes." The app shows
   only a total; it should show the arithmetic so the ST can narrate it ·
   `DayScreen.kt:174-178`.

8. **P2** · No day-start briefing · Nothing tells the ST at dawn "Alice's vote
   counts as 3 today" — the single fact they must not forget · `DayScreen.kt:85-124`.

9. **P2** · The `3 votes` token is unreachable from the seat sheet ·
   `ReminderPicker` never lists traveller reminders · `GameData.kt:33-43`,
   `SeatSheet.kt:489-560`.

10. **P2** · A dead marked player's amplified ghost vote is not handled · Rules:
    "Dead players with triple votes can use their single vote token with amplified
    effect." Once weighting exists, the ghost-vote spend must still be exactly one
    token while the tally gains three · `DayScreen.kt:232-240`.

11. **P2** · The `night_guide` "other" text tells the ST to remove the previous
    token by hand, but the tray already moves it — the instruction is now stale
    and mildly misleading · `night_guide.json` `bureaucrat.other`.

12. **P3** · The night step is offered even when the Bureaucrat is dead. Dead
    travellers have no ability; `NightOrder` emits a step for any holder
    regardless of `alive` (`NightOrder.kt:142-178`), with only the generic
    "dead players usually don't act" footnote (`NightScreen.kt:160-168`).

## Proposed behaviour (spec)

### Night step

- **when:** both first and other nights, at the existing order position (after
  `barista`, before `thief`).
- **wake condition:** the Bureaucrat seat exists, is **alive**, and is not
  impaired. If dead or impaired, render the step greyed with
  "Bureaucrat is dead / poisoned — no ability tonight; remove the 3 votes token."
- **targets:** exactly 1 player, **not the Bureaucrat**. May be alive or dead
  (nothing forbids marking a dead player; their single ghost vote is then worth
  3). May be the same player as last night. Picker: sort living first, disable the
  Bureaucrat's own chip with the reason "not yourself".
- **immediate effects:** `placeExclusiveReminder(target, PlacedReminder("bureaucrat","3 votes"))`
  — already what the tray does; make it the step's one-tap action instead of a
  token-then-seat two-tap.
- **deferred effects:** tomorrow, every vote by the marked player in an
  **execution** tally counts 3.
- **expiry:** add `"bureaucrat" to "3 votes"` to `EXPIRES_AT_DUSK`
  (`GameActions.kt:231-242`), matching the Poisoner pattern — swept at dusk, then
  re-placed on the following night.
- **on loss of ability:** when the Bureaucrat dies, is exiled, or gains a
  Drunk/Poisoned reminder, remove every `bureaucrat:"3 votes"` token immediately.
  Implement as a general rule rather than a special case:
  `GameActions.removeTokensOfSource(state, sourceId)` called from `kill`, from the
  exile path, and whenever an impairment token lands on a source character whose
  effect is "ends immediately".
- **information / visibility:** the Bureaucrat learns nothing. The *group* learns
  who is marked as soon as they vote, because the ST counts aloud — so nothing is
  hidden after the first tally.

### Vote weighting (shared with Thief)

Extend the `VoteRules` object described in `voudon.md`:

```kotlin
val weightOf: Map<Long, Int>   // default 1
```

- `isExile == true` → `weightOf` is **empty** (every supporter counts 1).
  "Characters that modify votes (Thief, Bureaucrat, etc.) do not modify support
  for exile."
- Otherwise, for each player: start at 1; ×3 if they carry
  `bureaucrat:"3 votes"` **and** a living unimpaired Bureaucrat is in play;
  ×(−1) if they carry `thief:"Negative vote"` under the same condition
  (see `thief.md`). Both ⇒ −3 ("3 negative votes", per the wiki).
- Tally = `Σ weightOf[voterId]`, floored at nothing (a negative total is legal and
  simply never reaches the threshold).
- `Nomination` gains `val weightedVotes: Int` (or `votes` becomes the weighted
  tally and a new `handCount: Int` keeps the raw count). Prefer: `votes` = the
  **weighted** tally, because `highestVotesToday` and `aboutToDie` must compare
  weighted values; add `handCount` for the record.
- **Ghost votes:** the spend is per *player*, not per vote weight — a dead marked
  player spends one token and contributes 3.

### Day panel presentation

The vote header must show the arithmetic so the ST can count out loud:

```
Vote — 4 hands = 6 votes (needs 5, best today 3)
  Alice ×3 (Bureaucrat)   Bob   Cara   Dan
```

and each weighted chip carries a small `×3` / `−1` badge.

### Day-start briefing (shared panel)

> **Bureaucrat:** *Alice*'s vote counts as **3 votes** today. Count it out loud so
> the group hears it. Exiles are unaffected — Alice supports an exile only once.
> If the Bureaucrat dies or is exiled, the effect ends immediately and the token
> comes off.

### Interactions to handle explicitly

- **Thief** on the same player ⇒ −3.
- **Voudon** — the Voudon changes *who* may vote and the threshold; the
  Bureaucrat's ×3 still applies to whoever legally votes. A living marked player
  cannot vote at all under an active Voudon.
- **Butler** — a marked Butler still needs their Master to vote; if they do, it is
  3 votes.
- **Beggar** — a marked Beggar spends one token and contributes 3.
- **Gunslinger** — a marked player who votes is "a player that voted", once, for
  Gunslinger purposes regardless of weight.
- **Flowergirl** (`InfoCalc.kt:296-313`) — reads `voterIds`, so weights are
  irrelevant. Correct as written.
- **Judge** — a forced fail zeroes the tally but keeps `voterIds` (see
  `judge.md`); the weight map is irrelevant to that.
- **Organ Grinder / eyes-closed voting** — out of scope here, but the weight map
  is the right place to keep it working.

### UI text

- Night step: `Bureaucrat — who gets 3 votes tomorrow? (not themselves)`
- Token badge on a seat: `×3`
- Day header: `4 hands = 6 votes · needs 5`
- Loss of ability toast: `Bureaucrat is out — the 3 votes token has been removed.`

### Data changes

- `night_guide.json` `bureaucrat.other`: drop "Remove the 3 votes reminder from
  the previously chosen player" (the app moves it) and add "The token comes off by
  itself at dusk and if the Bureaucrat dies or is exiled." Add a `shows` entry?
  No — the Bureaucrat shows the player nothing.
- `characters.json`: change the first/other night reminders to say "points to a
  player **other than themselves**".
- `GameActions.EXPIRES_AT_DUSK`: add `"bureaucrat" to "3 votes"`.

## Tests to add

1. `Given` 9 alive (threshold 5) and a player marked
   `PlacedReminder("bureaucrat","3 votes")` with a living unimpaired Bureaucrat,
   `when` 3 players including the marked one vote, `then` the weighted tally is 5
   and the outcome is `ABOUT_TO_DIE`.
2. `Given` the same marked player, `when` the nominee is a **traveller** (exile),
   `then` the tally is 3 (weights ignored) and
   `threshold == Voting.exileThreshold(players.size)`.
3. `Given` a marked **dead** player with one vote token, `when` they vote on an
   execution, `then` the tally gains 3 and exactly one vote token is spent.
4. `Given` a marked player who also carries `thief:"Negative vote"`, `then` their
   weight is −3.
5. `Given` a `3 votes` token in play, `when` the Bureaucrat is killed
   (`DeathCause.EXILE` or `DEMON`), `then` no player carries a `bureaucrat` token
   afterwards and the next tally is unweighted.
6. `Given` a Bureaucrat carrying a `Poisoned` reminder, `then` the weight map is
   empty even though the `3 votes` token is still on the board (and the UI shows
   "no effect — Bureaucrat is poisoned").
7. `Given` a `3 votes` token placed on night 2, `when` `advancePhase` runs
   DAY→NIGHT, `then` the token is gone (dusk expiry).
8. `Given` the Bureaucrat's night step, `when` the target picker is built, `then`
   the Bureaucrat's own seat is not selectable.
9. `Given` a dead Bureaucrat, `when` the other-night sheet is built, `then` the
   Bureaucrat step is marked "no ability tonight".
10. `Given` a weighted nomination recorded with 3 hands and 5 weighted votes,
    `then` `GameActions.highestVotesToday` returns 5 and the log line reads
    "3 hands / 5 votes".
