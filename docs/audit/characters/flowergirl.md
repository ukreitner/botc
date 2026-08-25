# Flowergirl (flowergirl) — Sects & Violets Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Flowergirl> (fetched 2026-08-25);
Vortox rule from <https://wiki.bloodontheclocktower.com/Vortox>.

Current ability text:

> "Each night*, you learn if a Demon voted today."

**How to Run (verbatim):**

> "Each dawn, mark the Flowergirl with the **DEMON NOT VOTED** reminder, and remove the
> **DEMON VOTED** reminder, if any. Each day, if the Demon votes for any execution,
> replace the **DEMON NOT VOTED** reminder with the **DEMON VOTED** reminder. Each night
> except the first, wake the Flowergirl. If the Flowergirl is marked **DEMON NOT VOTED**,
> shake your head no. If the Flowergirl is marked **DEMON VOTED**, nod your head yes.
> Then, put the Flowergirl to sleep. If you forget whether the Demon voted or not, wake
> the Demon at night and ask by showing them the **DID YOU VOTE TODAY?** info token. They
> must answer honestly, then go to sleep."

**Examples (verbatim):**

> "There was one nomination today. Lots of players voted, the player was executed, but
> the Demon did not vote. That night, the Flowergirl learns that the Demon did not vote
> today.
>
> There were three nominations today. The Demon voted during the second nomination.
> Nobody was executed. That night, the Flowergirl learns that the Demon voted today.
>
> There were no nominations today. A Traveller was exiled, and all players raised their
> hand to support the exile. That night, the Flowergirl learns that the Demon did not
> vote today. **(Exiles are never affected by abilities.)**"

**Vote-counting rules from the page's clarifications**

- A Demon's vote counts **regardless of whether the execution passed** (example 2 — no
  one was executed and it still counts).
- **Exile votes never count** (example 3).
- With multiple Demons, **any** Demon voting triggers "yes", and **dead Demons' votes
  count** (a dead Demon spending a ghost vote is still a Demon voting).
- The **original** Demon is detected even if the Demon changes players after the vote —
  i.e. the question is "was the voter a Demon *at the time they voted*", not "is the voter
  a Demon now".
- **Vortox**: the Flowergirl is a Townsfolk getting information, so with an alive Vortox
  the answer **must** be inverted ("Even if they are drunk or poisoned, it must be
  false"). The wiki's Tips note the resulting tell: "detecting a Vortox occurs if everyone
  votes yet you learn the Demon didn't vote."
- **Drunk / poisoned Flowergirl**: the storyteller *may* invert the answer.
- **Misregistration**: a Recluse who voted may register as the Demon → a legitimate
  "yes". (Not stated on the Flowergirl page; it follows from the Recluse's own text
  "might register as evil & as a Minion or Demon". Flagged as inference.)
- **"Each night\*"** — not the first night, and only while the Flowergirl is alive.
- **Jinxes: none.**

## What the app does today

| path | what it holds |
|---|---|
| `engine/src/main/resources/botc/data/characters.json:823-837` | Ability text matches. `otherNightReminder`: "Nod 'yes' or shake head 'no' for whether the Demon voted today. Place the 'Demon not voted' marker (remove 'Demon voted', if any)." `reminders: ["Demon voted", "Demon not voted"]`. `firstNightReminder` empty. Correct data. |
| `engine/src/main/resources/botc/data/night_and_jinxes.json:450` | Other-night order index 77, immediately after `dreamer` and before `towncrier`. Absent from `firstNight`. Correct. |
| `engine/src/main/resources/botc/data/night_guide.json:449-454` | Prose: "…During the day, place the 'Demon voted' reminder as soon as the Demon raises their hand to vote, and clear it each dawn. If the Flowergirl is drunk or poisoned, or the Vortox is in play, give the opposite (false) answer." `shows: []` — no prepared card. |
| `engine/src/main/kotlin/com/clocktower/engine/InfoCalc.kt:31` | `flowergirl` in `supports()`. |
| `engine/src/main/kotlin/com/clocktower/engine/InfoCalc.kt:307-323` | The calculation — derived from the recorded nominations, **not** from the reminder tokens. |
| `engine/src/main/kotlin/com/clocktower/engine/InfoCalc.kt:117-118` | `relevantDay`: during NIGHT of cycle N, "today" is day N-1. Correct. |
| `app/src/main/java/com/clocktower/grimoire/ui/screens/DayScreen.kt:167-247` | Vote capture: seats are listed clockwise from the nominee's left, `voters` is a Set toggled by chip, `orderedVoterIds` is stored on the `Nomination` (`GameState.kt:63-72`), dead voters' ghost votes are spent on Record (`DayScreen.kt:233-240`). Dead players can be tapped (`canVote = p.alive \|\| !p.ghostVoteUsed \|\| isExile`, `DayScreen.kt:184`). |
| `app/src/main/java/com/clocktower/grimoire/ui/screens/NightScreen.kt:896-901, 923-928` | YES/NO full-screen card, plus a one-tap inverted card when a caveat mentions impairment or Vortox. |
| `app/src/main/java/com/clocktower/grimoire/ui/components/ShowCards.kt:367-377` | Phrase cards include "DID YOU NOMINATE TODAY?" (the Town Crier fallback) but **not** "DID YOU VOTE TODAY?" (the Flowergirl fallback the wiki names). |
| `engine/src/test/kotlin/com/clocktower/engine/InfoCalcTest.kt:97-109` | Existing test: town crier and flowergirl read today's nominations. |

The calculation (`InfoCalc.kt:307-323`):

```kotlin
val today = ctx.state.nominations.filter { it.day == relevantDay(ctx.state) && !it.isExile }
val demonIds = ctx.players.filter { ctx.character(it)?.team == Team.DEMON }.map { it.id }.toSet()
val voted = today.any { n -> n.voterIds.any { it in demonIds } }
```

**What already works — one line each:**

- Derives the answer from the actual vote record instead of asking the storyteller to
  remember — the right architecture, and better than the physical token.
- **Exiles are excluded** (`!it.isExile`, `InfoCalc.kt:311-312`), matching wiki example 3,
  with a good code comment explaining why.
- A vote on a nomination that failed still counts (no filter on `result`), matching
  example 2.
- **A dead Demon spending a ghost vote is counted**: dead players are tappable in the vote
  row and land in `voterIds`, and `demonIds` is not filtered by `alive`.
- Multiple Demons: `any { it in demonIds }` handles it.
- "Today" resolves to day N-1 during night N (`relevantDay`), and night 1 has no day 0
  nominations so the answer is correctly "NO" if the step were ever run.
- The YES/NO inversion chip for impaired/Vortox actually works for this character.

**Storyteller's experience today:** if you recorded every nomination and tapped every
raised hand on the Day tab, the Flowergirl step just tells you the answer and gives you a
one-tap YES/NO card. If you did *not* record a nomination — or recorded the tally but not
the individual hands — the answer is silently wrong. And the two reminder tokens the
official run-book (and the app's own guide text) tell you to place are pure manual labour
that the app never places, never moves and never clears.

## Defects and gaps

1. **P1 · Demon identity is evaluated at reveal time, not at vote time.**
   Rules: the question is whether a **Demon** voted, judged when the vote happened; the
   wiki states the original Demon is detected even if the Demon changes players
   afterwards. App: `demonIds` is computed from *current* `characterId`s at the moment the
   step is expanded (`InfoCalc.kt:314`).
   - **False positive:** the Fang Gu jumps into an Outsider on night N. `fanggu` is at
     other-night index 42, the Flowergirl at 77, so by the time the Flowergirl step is
     computed that seat is a Demon. If that player voted during day N-1 *as a good
     Outsider*, the Flowergirl now learns YES. **Repro:** day 2, Outsider Hana votes;
     night 2, resolve the Fang Gu jump onto Hana via `QuickResolutions`
     (`NightScreen.kt:483-498`); expand the Flowergirl step → "YES — the Demon voted today".
   - **False negative:** a Pit-Hag turns the Demon into a Townsfolk, or the storyteller
     corrects a mis-dealt seat, after the Demon voted → the YES silently becomes NO.
   Fix: snapshot the demon seat-ids on the `Nomination` record, or resolve the answer at
   dusk and store it.

2. **P1 · The `Demon voted` / `Demon not voted` tokens are never managed.**
   The official run-book is token-driven and the app's own guide text
   (`night_guide.json:449-454`) instructs the storyteller to place and clear them, but
   nothing in the code ever writes them: `grep -rn "Demon voted" engine/src app/src`
   returns only the InfoCalc headline string. They are not in `EXPIRES_AT_DAWN`
   (`GameActions.kt:218-225`) or `EXPIRES_AT_DUSK` (`GameActions.kt:231-242`), so a
   storyteller who does place them by hand accumulates them forever — and because
   `placeExclusiveReminder` keys on `(sourceId, label)` (`GameActions.kt:194-201`),
   `flowergirl:"Demon voted"` and `flowergirl:"Demon not voted"` are different tokens and
   **both can sit on the seat at once**, saying opposite things.

3. **P1 · Recluse misregistration is not surfaced.**
   `InfoCalc.kt:318` passes only the current Demon-team players to `misregistrations`, so
   a Recluse who voted today is never mentioned. A Recluse may register as the Demon,
   making YES a legal answer even when no real Demon voted. **Repro:** Recluse votes on
   day 2, no Demon votes; the Flowergirl step says "NO" with no caveat.

4. **P1 · A skipped or partially-recorded vote silently produces a wrong answer.**
   The whole calculation rests on `voterIds`, which only exist if the storyteller used the
   Day tab's vote row and pressed **Record** (`DayScreen.kt:220-247`). There is no
   day-side reminder that a Flowergirl is in play and hands must be captured, and no
   dawn/dusk check that a day with an execution has at least one recorded nomination. The
   defensive caveat at `InfoCalc.kt:319-321` ("Some votes were tallied without recording
   who voted") can never fire from the app's own UI, because `votes` is always set to
   `orderedVoterIds.size` (`DayScreen.kt:224-226`) — it only helps imported/legacy saves.

5. **P2 · No day-side Flowergirl status.**
   The storyteller cannot see, during the day, what the Flowergirl is currently going to
   learn. A live "Flowergirl: the Demon has **not** voted yet today" line on the Day tab
   would replace the physical token entirely and let the storyteller notice a missed
   nomination while it is still fixable.

6. **P2 · The official fallback card is missing.**
   `ShowCards.kt:367-377` has "DID YOU NOMINATE TODAY?" but no **"DID YOU VOTE TODAY?"**,
   which the Flowergirl How-to-Run names explicitly as the recovery path (wake the Demon
   and ask). `night_guide.json` gives the Flowergirl `shows: []`, so the step has no
   prepared card at all — not even YES/NO (those are synthesised from the headline).

7. **P2 · Dead Flowergirl.**
   "Each night\*" plus dead = does not wake. The step still appears every night with only
   the generic "All holders are dead — usually skip" line (`NightScreen.kt:751-757`) and
   still nags the dawn checklist guard (`GameShell.kt:147-161`).

8. **P2 · "Must lie" vs "may lie" indistinguishable.**
   `commonCaveats` (`InfoCalc.kt:158-166`) appends the Vortox note next to impairment
   notes and `NightScreen.kt:903-906` collapses them to one boolean. Under a Vortox the
   truthful card must not be the primary control.

9. **P2 · Traveller votes on an *execution* nomination.**
   Travellers vote on executions normally; that is handled. But a **Demon Traveller**
   cannot exist, so no issue — noting it only because the exile filter is easy to
   over-apply.

10. **P3 · The detail field is unused.** `InfoCalc.kt:316-322` returns no `detail`; naming
    *which* Demon voted on *which* nomination would let the storyteller sanity-check.

## Proposed behaviour (spec)

- **when:** `other` nights only (never first).
- **wake condition:** holder is **alive**. If dead, render the row collapsed with
  "Flowergirl is dead — skip" and auto-tick it in the checklist.
- **targets:** none.
- **immediate effects:** none at night.
- **deferred effects / the day side (this is the fix):**
  - **At dawn** (`advancePhase` NIGHT→DAY, `GameActions.kt:260`): if a Flowergirl is in
    play, place `PlacedReminder("flowergirl", "Demon not voted")` on the Flowergirl seat
    and remove `flowergirl:"Demon voted"` — i.e. exactly the official run-book, done for
    the storyteller. Both labels must be mutually exclusive: introduce
    `placeExclusiveGroupReminder(state, playerId, sourceId, groupLabels, chosenLabel)` so
    placing one removes the other.
  - **On every recorded non-exile nomination** (`GameActions.recordNomination`,
    `GameActions.kt:274-275`): if any voter was a Demon at that moment, swap the token to
    `flowergirl:"Demon voted"`.
  - **At dusk** (`advancePhase` DAY→NIGHT, `GameActions.kt:261`): freeze the answer —
    write `DayOutcome(day, demonVoted: Boolean?, uncertain: Boolean)` into state so later
    character changes cannot rewrite history.
- **expiry:** the two tokens are managed as a mutually exclusive pair, re-placed at every
  dawn; never left over from a previous day. Add to a new `RESET_AT_DAWN` table rather
  than `EXPIRES_AT_DAWN` (they are re-placed, not just removed).
- **information (structured):**

  ```
  today       = nominations where day == relevantDay && !isExile
  demonAtVote = nomination.demonIdsAtRecord   // NEW: snapshot on the Nomination record
  answer      = Answer.YesNo(today.any { n -> n.voterIds.any { it in n.demonIdsAtRecord } })
  ```
  `Nomination` (`GameState.kt:63-72`) gains
  `val demonIdsAtRecord: List<Long> = emptyList()`, filled by `DayScreen` at Record time.
  For saves without it, fall back to the current behaviour and add a caveat.

  `detail` should name the evidence: "Bo (Vigormortis) voted on nomination 2 of 3
  (Hana → Ari)". On NO, name the nominations that were recorded so the storyteller can
  spot a missing one: "3 nominations recorded today; the Demon voted in none."

- **misregistration handling:** include every **Recluse** who appears in any of today's
  non-exile `voterIds` → caveat "Priya (Recluse) voted today — she may register as the
  Demon, so YES is also a legal answer" **with a one-tap YES card**. Include a **Spy** who
  voted only if the Spy were somehow a Demon (n/a) — no Spy handling needed here.
- **impaired / false alternative:** via `InfoCalc.obligation` (see `artist.md`).
  `MUST_LIE` (alive Vortox) → the inverted card is the primary control and the truthful
  one is demoted behind a text button; `MAY_LIE` → both offered, truth first.
- **visibility:** nothing shown to Demon/Minions/Lunatic, except the recovery path: a
  **"Ask the Demon: DID YOU VOTE TODAY?"** button on the step that shows the full-screen
  card. Offer it automatically when `uncertain == true` (no nominations recorded on a day
  that had an execution, or a day the storyteller marked "votes not recorded").
- **day-time inputs the app must let the storyteller record:** the individual raised hands
  — already supported (`DayScreen.kt:179-196`) — plus:
  - a **"votes not recorded"** escape hatch on the Day tab that flags the day as uncertain
    rather than silently answering NO;
  - a persistent day-tab status line while a Flowergirl is alive:
    **"Flowergirl: the Demon has not voted today"** / **"…the Demon HAS voted today (Bo,
    nomination 2)"**, updating as nominations are recorded.
- **interactions/jinxes:** none. Explicitly handle: exiles excluded; failed nominations
  count; dead Demons' ghost votes count; multiple Demons; demon identity snapshotted at
  vote time; Recluse registration; Vortox mandatory inversion.

### UI text the step should display

> **Flowergirl — nod yes / shake no: did a Demon vote today?**
>
> **NO — the Demon did not vote today**
> 3 nominations recorded on day 2; Bo (Vigormortis) voted in none of them.
> `[ Show NO full-screen ]` `[ Ask the Demon: DID YOU VOTE TODAY? ]`
> `! Priya (Recluse) voted today — she may register as the Demon. [ Show YES instead ]`

Day tab, while an alive Flowergirl exists:

> **Flowergirl** · the Demon has **not** voted today. `[ Votes not recorded today ]`

### Data changes

- `night_guide.json:449-454` — split the obligation ("If the Vortox is in play the answer
  **must** be the opposite of the truth; if the Flowergirl is drunk or poisoned you **may**
  invert it"), and drop the instruction to hand-place tokens once the engine does it.
  Add shows:
  `[{"label":"YES","kind":"message","text":"YES"},{"label":"NO","kind":"message","text":"NO"},
    {"label":"Ask the Demon","kind":"message","text":"DID YOU VOTE TODAY?"}]`
- `ShowCards.kt:367-377` — add `"DID YOU VOTE TODAY?"` to the phrase list.
- `characters.json:823-837` — no change.
- `night_and_jinxes.json` — no change.

## Tests to add

1. **Failed nomination still counts (wiki example 2).**
   *Given* three nominations on day 1, the Demon in `voterIds` of the second, and no
   execution, *then* the Flowergirl answer on night 2 is YES.

2. **Exile support never counts (wiki example 3).**
   *Given* zero non-exile nominations and one exile with every player in `voterIds`
   including the Demon, *then* the answer is NO. (Currently passes — lock it in.)

3. **Dead Demon's ghost vote counts.**
   *Given* the Demon is dead and appears in `voterIds` of a recorded nomination,
   *then* the answer is YES.

4. **Demon identity is snapshotted (currently fails).**
   *Given* Outsider Hana votes on day 2 and then becomes the Fang Gu on night 2,
   *then* the night-2 Flowergirl answer is **NO**.
   *And*: *given* the Imp votes on day 2 and star-passes on night 2,
   *then* the answer is **YES**.

5. **Recluse voter produces a YES alternative (currently fails).**
   *Given* a `recluse` in `voterIds` and no Demon voter, *then* the answer is NO and the
   caveats name the Recluse as a legal YES.

6. **Dawn places the token, dusk freezes the answer (currently fails).**
   *Given* a Flowergirl in play, *when* `advancePhase` moves NIGHT→DAY,
   *then* the Flowergirl seat holds exactly one `flowergirl` token, labelled
   "Demon not voted"; *when* a Demon vote is recorded, *then* the seat holds exactly one
   `flowergirl` token, labelled "Demon voted"; *when* dusk falls, *then* a frozen
   `DayOutcome` for that day exists.

7. **The two labels are mutually exclusive (currently fails).**
   *Given* both `flowergirl:"Demon voted"` and `flowergirl:"Demon not voted"` are placed
   in sequence, *then* only the last one remains on the seat.

8. **Vortox must-lie (currently fails).**
   *Given* an alive Vortox and a true answer of YES, *then* `obligation == MUST_LIE` and
   the suggested card is NO.

9. **Uncertain day.**
   *Given* an execution happened on day 2 but no nomination was recorded,
   *then* the Flowergirl result carries an "uncertain — no votes recorded" caveat and the
   "ask the Demon" card is offered.

10. **Dead Flowergirl does not wake.**
    *Given* the Flowergirl died on day 2, *when* the night-3 sheet is built,
    *then* the Flowergirl step is marked skipped and does not block the dawn checklist.

11. **Night 1 never shows the step.**
    *Given* a Flowergirl in play, *when* `firstNight` is built, *then* no `flowergirl`
    step exists. (Currently passes — lock it in.)
