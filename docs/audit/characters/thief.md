# Thief (thief) — Trouble Brewing Traveller

## Official rules (sources)

Sources (fetched 2026-08-25):
- <https://wiki.bloodontheclocktower.com/Thief>
- <https://wiki.bloodontheclocktower.com/Bureaucrat>
- <https://wiki.bloodontheclocktower.com/Travellers>
- Official rulebook, "Travelers" chapter (mirror
  <https://www.web3us.com/sites/default/files/Rulebook.pdf>).

Current ability text (matches `characters.json` exactly):

> "Each night, choose a player (not yourself): their vote counts negatively
> tomorrow."

How to run (wiki, verbatim):

- "**Wake the Thief nightly. They point at any player, who receives the NEGATIVE
  VOTE reminder token. When tallying that player's vote, count it as minus one
  instead of plus one (announce this aloud as normal).**"
- Examples:
  - "When Marianna has negative vote and votes, the Storyteller counts
    '1… 2… 3… 2… 3… 4… 5' instead of ascending normally." — the negative vote is
    subtracted *in clockwise order*, which is why the count audibly dips.
  - "Voting on executions differs from voting on exiles: '**Abdallah votes for an
    execution, and instead of the tally being six, it is four.**'" — note the swing
    of 2: a vote that would have added 1 instead subtracts 1.
  - "However, '**when the players are voting to exile the Gunslinger… his vote
    counts as positive**' since exiles aren't affected by abilities."
- Key mechanics:
  - "**Negative vote becomes positive immediately if Thief dies/exiles.**"
  - "**Dead players can be chosen, especially useful on final day.**"
  - "**Bureaucrat interaction: if both coordinate on same player, produces '3
    negative votes'.**"
- Storyteller tips: "Observe players' reactions to negative votes for alignment
  tells. **Pick different players each night to prevent weaponization.**"
  (advice, not a rule — the Thief *may* repeat a target)

Traveller framework (rulebook, verbatim, decisive here):

- Exile: "**The process to exile a Traveler is not affected by abilities. It is
  purely a group decision. For example, characters that modify votes (Thief,
  Bureaucrat, etc.) do not modify support for exile.**" ·
  "**If at least half of the players support the exile, it succeeds, and the
  exiled Traveller dies. This counts the total number of players in the game, not
  the number of alive players.**" · "Any player, even dead ones, may support the
  exile… Dead players that support an exile do not lose their vote token."
- "Travelers… may act at night… **lose their abilities when dead or drunk or
  poisoned**."
- "**Choose Alignment. Tell the Traveler player in private whether they are good
  or evil. If you made the Traveler evil, they learn which player is the Demon…**"

Jinxes: none for the Thief in `night_and_jinxes.json` or on the wiki.

Night order: `firstNight` and `otherNight` both, immediately after the Bureaucrat.
The app's data matches (`firstNight[6]`, `otherNight[3]`).

## What the app does today

Data
- `characters.json` — `thief`, team `traveler`, ability text correct,
  `reminders: ["Negative vote"]`, both night reminders are the official strings.
  Correct.
- `night_and_jinxes.json` — `firstNight[6]`, `otherNight[3]`. **Correct.**
- `night_guide.json` — `thief.first` and `thief.other` present, with clear text
  including "(subtract it from the tally)". `shows: []`. Correct as prose.

Engine
- Nothing in production code references `thief`. `grep -rn "thief" engine/src
  app/src` matches only the data files.
- `EXPIRES_AT_DAWN` / `EXPIRES_AT_DUSK` (`GameActions.kt:218-242`) do **not**
  include `("thief","Negative vote")`.
- The tally is `orderedVoterIds.size` (`DayScreen.kt:172,176,198,204,225`) — an
  unweighted count of selected chips. Nothing subtracts.
- `Voting.outcome(votes, threshold, currentHighest)` (`GameState.kt:141-152`)
  takes an `Int`, so a signed tally works without a signature change; the
  comparisons `votes < threshold` and `votes > currentHighest` behave correctly
  for negative totals.

UI
- The night step **works**, exactly as for the Bureaucrat: a `Thief` row on every
  night with the official reminder text (`NightOrder.kt:142-178`), the night-guide
  prose in the step panel (`NightScreen.kt:792-800`), and a `Negative vote` chip
  in the night tray whose seat tap routes through
  `GameActions.placeExclusiveReminder` because exactly one copy is declared
  (`NightScreen.kt:314-345`) — so the token moves rather than accumulating.
- The day is where it fails: the vote panel counts hands and knows nothing about
  the token (`DayScreen.kt:161-247`).
- `ReminderPicker` (`SeatSheet.kt:489-560`) cannot offer the `Negative vote`
  token, because built-in scripts exclude travellers (`GameData.kt:39`).
- Nothing removes the token when the Thief dies or is exiled.
- Alignment defaults to good like every traveller (`Character.kt:16`,
  `GameState.kt:45-51`); bare "Flip alignment" (`SeatSheet.kt:315`).

Storyteller experience today: the token is placed correctly at night, and then all
day the app reports a tally that is 2 too high whenever the marked player raises
their hand — and the on-block derivation, the "tally to beat" and the game log all
inherit the error.

## Defects and gaps

1. **P0** · The −1 weight is never applied · Rules: "When tallying that player's
   vote, count it as minus one instead of plus one." App: `orderedVoterIds.size` ·
   `DayScreen.kt:172,176,198,204,225` · Repro: place the `Negative vote` token,
   open a nomination with 11 alive (threshold 6), tap 6 voters including the
   marked one — the app says "6 so far, needs 6 … about to die"; the true tally is
   4 and the nominee is safe.

2. **P0** · The recorded `Nomination.votes` is the hand count, so the day's
   tally-to-beat, the on-block derivation and the log are all wrong afterwards ·
   `DayScreen.kt:225`, `GameActions.highestVotesToday` (`GameActions.kt:278-282`),
   `GameActions.aboutToDie` (`GameActions.kt:296-306`), `GameExtras.kt:74-79`.

3. **P0** · Exiles must never be modified · Rules, verbatim: "characters that
   modify votes (Thief, Bureaucrat, etc.) do not modify support for exile", and
   the Thief page's own Gunslinger example. The exile branch shares the same
   counter (`DayScreen.kt:197-202`), so any naive weighting fix breaks this ·
   must be specified before the weighting is added.

4. **P1** · The token is not removed when the Thief loses their ability · Rules:
   "Negative vote becomes positive immediately if Thief dies/exiles" (and
   travellers lose abilities when drunk or poisoned) · App: `GameActions.kill`
   (`GameActions.kt:136-156`) touches no reminders · Repro: mark a player, exile
   the Thief before the first nomination, the token stays and the ST must remember
   it is inert.

5. **P1** · The token never expires · It governs "tomorrow" only; it should be
   swept at dusk so a night on which the Thief is dead or absent leaves no stale
   penalty · `GameActions.kt:231-242`.

6. **P1** · The "not yourself" constraint is unenforced · The night tray's seat
   picker lists every player including the Thief (`NightScreen.kt:313-352`).

7. **P1** · The count-aloud is not supported · "announce this aloud as normal" and
   the wiki's "1… 2… 3… 2… 3… 4… 5" example both assume the ST narrates a running
   total in clockwise order. The app shows only a final number ·
   `DayScreen.kt:167-178`.

8. **P2** · No day-start briefing · Nothing tells the ST at dawn "Marianna's vote
   counts as −1 today" · `DayScreen.kt:85-124`.

9. **P2** · The `Negative vote` token is unreachable from the seat sheet ·
   `GameData.kt:33-43`, `SeatSheet.kt:489-560`.

10. **P2** · Bureaucrat stacking is unmodelled · "if both coordinate on same
    player, produces 3 negative votes" — with no weight model there is nothing to
    stack · `bureaucrat.md` covers the shared design.

11. **P2** · A dead marked player still spends exactly one vote token while
    contributing −1; the ghost-vote spend must not be skipped just because the
    contribution is negative · `DayScreen.kt:232-240`.

12. **P3** · The `night_guide` "other" text tells the ST to remove the previous
    token by hand, but the tray already moves it — stale instruction ·
    `night_guide.json` `thief.other`.

13. **P3** · The night step is offered even when the Thief is dead
    (`NightOrder.kt:142-178`), with only the generic "dead players usually don't
    act" footnote (`NightScreen.kt:160-168`).

## Proposed behaviour (spec)

The Thief shares the vote-weighting machinery specified in `bureaucrat.md` and the
`VoteRules` object specified in `voudon.md`. Only the Thief-specific parts are
repeated here.

### Night step

- **when:** both first and other nights, at the existing order position
  (immediately after `bureaucrat`).
- **wake condition:** the Thief seat exists, is **alive**, and is not impaired.
  If dead or impaired, render the step greyed with "Thief is dead / poisoned — no
  ability tonight; remove the Negative vote token."
- **targets:** exactly 1 player, **not the Thief**. **Dead players are explicitly
  allowed** ("Dead players can be chosen, especially useful on final day") — do
  not filter them out and do not sort them last on the final day. Repeat targets
  are legal; surface last night's choice as a hint ("Last night: Marianna —
  the wiki suggests varying the target") without blocking it.
- **immediate effects:**
  `placeExclusiveReminder(target, PlacedReminder("thief","Negative vote"))` — make
  this the step's one-tap action rather than a token-then-seat two-tap.
- **deferred effects:** tomorrow, every vote by the marked player in an
  **execution** tally counts −1.
- **expiry:** add `"thief" to "Negative vote"` to `EXPIRES_AT_DUSK`
  (`GameActions.kt:231-242`).
- **on loss of ability:** remove every `thief:"Negative vote"` token the moment the
  Thief dies, is exiled, or is marked drunk/poisoned — via the shared
  `GameActions.removeTokensOfSource(state, "thief")` proposed in `bureaucrat.md`.
- **information / visibility:** the Thief learns nothing. The group learns who is
  marked as soon as that player votes, because the tally audibly dips.

### Weighting rules (shared)

From `VoteRules.weightOf` (see `voudon.md` / `bureaucrat.md`):

- **exile ⇒ no weights at all.** Every supporter counts +1, including a marked
  one. This is the wiki's explicit Gunslinger-exile example.
- execution ⇒ per player: 1, ×3 if `bureaucrat:"3 votes"` with a live unimpaired
  Bureaucrat, ×(−1) if `thief:"Negative vote"` with a live unimpaired Thief.
  Both ⇒ **−3**.
- Tally = `Σ weight` over the raised hands, **signed**. Do not clamp at 0: a tally
  of −1 is a legal (and informative) result, it simply never reaches the
  threshold and never becomes the tally-to-beat.
  - `Voting.outcome` already behaves correctly for negative inputs:
    `votes < threshold → SAFE`.
  - `GameActions.highestVotesToday` (`GameActions.kt:278-282`) only considers
    nominations whose result was `ABOUT_TO_DIE`/`TIED`, so negative tallies can
    never lower the bar. Confirm with a test.
- **Ghost votes** are spent per raised hand, never per weight.

### Day panel presentation

Show a clockwise running total so the ST can narrate it, exactly as the wiki
example does:

```
Vote — 6 hands = 4 votes (needs 6, best today 5)
  Ali 1 · Bo 2 · Cara 3 · Marianna −1 → 2 · Dan 3 · Eve 4
```

Marked chips carry a `−1` badge (and `×3` / `−3` where the Bureaucrat also
applies).

### Day-start briefing (shared panel)

> **Thief:** *Marianna*'s vote counts as **−1** today. Count aloud as normal — the
> tally will dip when she raises her hand. Exiles are unaffected: for an exile her
> support counts +1 as usual. If the Thief dies or is exiled, the effect ends
> immediately and the token comes off.

### Interactions to handle explicitly

- **Bureaucrat** on the same player ⇒ −3.
- **Voudon** — the Voudon decides *who* votes and sets the threshold to 1; the
  Thief's −1 still applies to whoever legally votes. Marking a dead player under a
  Voudon is strong and legal.
- **Beggar** — a marked Beggar spends one token and contributes −1.
- **Butler** — a marked Butler still needs their Master; if they vote, −1.
- **Gunslinger** — a marked player who votes is still "a player that voted" and is
  a legal Gunslinger target.
- **Flowergirl** (`InfoCalc.kt:296-313`) — reads `voterIds`, unaffected by weight.
- **Judge** — a forced fail zeroes the tally but keeps `voterIds`; see `judge.md`.
- **Exile** — never weighted, per the rulebook quote above.

### UI text

- Night step: `Thief — whose vote counts as −1 tomorrow? (not themselves; dead players allowed)`
- Token badge on a seat: `−1`
- Day header: `6 hands = 4 votes · needs 6`
- Loss of ability toast: `Thief is out — the Negative vote token has been removed.`

### Data changes

- `night_guide.json` `thief.other`: drop "Remove the Negative vote reminder from
  the previously chosen player" (the app moves it); add "The token comes off by
  itself at dusk and if the Thief dies or is exiled. Exiles are never affected."
- `characters.json`: change the first/other night reminders to say "points to a
  player **other than themselves**".
- `GameActions.EXPIRES_AT_DUSK`: add `"thief" to "Negative vote"`.

## Tests to add

1. `Given` 11 alive (threshold 6) and a player marked
   `PlacedReminder("thief","Negative vote")` with a living unimpaired Thief,
   `when` 6 players including the marked one vote, `then` the weighted tally is 4
   and the outcome is `SAFE` (today: `ABOUT_TO_DIE`).
2. Wiki example: `given` a tally that would be 6 with the marked player voting,
   `then` the weighted tally is 4.
3. `Given` the same marked player, `when` the nominee is a **traveller** (exile),
   `then` their support counts **+1**, the tally is the raw hand count, and the
   threshold is `Voting.exileThreshold(players.size)`.
4. `Given` a marked player who also carries `bureaucrat:"3 votes"`, `then` their
   weight is −3.
5. `Given` a `Negative vote` token in play, `when` the Thief is killed
   (`DeathCause.EXILE` or `DEMON`), `then` no player carries a `thief` token and
   the next tally is unweighted.
6. `Given` a Thief carrying a `Poisoned` reminder, `then` the weight map is empty
   even though the token is on the board.
7. `Given` a `Negative vote` token placed on night 2, `when` `advancePhase` runs
   DAY→NIGHT, `then` the token is gone (dusk expiry).
8. `Given` a nomination whose weighted tally is −1, `then` the result is `SAFE`
   and `GameActions.highestVotesToday` is unchanged by it.
9. `Given` a marked **dead** player with one vote token, `when` they vote on an
   execution, `then` the tally decreases by 1 and exactly one vote token is spent.
10. `Given` the Thief's night step, `when` the target picker is built, `then` the
    Thief's own seat is not selectable and dead seats **are** selectable.
