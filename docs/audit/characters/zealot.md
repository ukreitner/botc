# Zealot (zealot) — Experimental Outsider

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Zealot> (fetched 2026-08-25);
interaction cross-check at <https://botc.me/character/zealot>.

Current ability text: **"If there are 5 or more players alive, you must vote for every
nomination."** (`characters.json` matches exactly.)

How to Run (wiki, quoted):
- "During each nomination, if there are 5 or more players alive, the Zealot must raise
  their hand to vote."
- "If the Zealot accidentally forgets to vote, do not tally the Zealot's vote."
  → the Storyteller must **not** silently add the vote; doing so would both reveal the
  Zealot and unfairly punish evil.

Examples (wiki, quoted):
- "There are 7 players alive. The Zealot votes for the Alsaahir, the Summoner, Ogre, and
  the Banshee. The next day, there are 5 players alive. The Zealot votes for the
  Yaggababble, and the High Priestess. The next day, there are 3 players alive. The Zealot
  votes for the Yaggababble, but chooses not to vote for the High Priestess."
- "There are 9 players alive. The Zealot is dead. The Zealot doesn't vote for 3 days, and
  uses their vote token when just 3 players are alive to vote for the Farmer."

Key details:
- **Travellers count** toward the alive-player count.
- The Zealot **does not** have to vote on **exiles**.
- A **dead** Zealot votes like any other dead player — one ghost vote, no obligation.
- The obligation applies **even when the Zealot is drunk or poisoned** (the Zealot does
  not know, so they must behave as though the ability works). *(This is the wiki's
  wording; it is a behavioural instruction to the player rather than a rules edge case.)*
- The count is evaluated **per nomination**, at the moment of that nomination — a death
  mid-day changes the obligation for later nominations that day.
- Self-policing: the wiki says deliberately not voting is cheating, and that the
  Storyteller should not police it beyond a reminder.
- Storyteller tips: watch voting patterns to spot a Zealot; the Zealot is a common evil
  bluff, and a "votes on everything" pattern is the tell.

Jinxes / interactions:
- **Cannibal** (official jinx, present in this repo): "If the Cannibal gains the Zealot
  ability, the Cannibal learns this." → the Cannibal must be **told**, and from then on
  the *Cannibal* carries the must-vote obligation.
- **Legion** (botc.me, lower confidence than the wiki): "The Zealot might register as
  evil to Legion."
- **Vizier** (botc.me, lower confidence): "The Zealot might register as evil to the
  Vizier."

## What the app does today

Data:
- `engine/src/main/resources/botc/data/characters.json:1741-1751` — id, exp, outsider,
  correct ability text, `setup:false`, **no** night reminders, **no** reminder tokens.
- Not in `firstNight`/`otherNight` (correct — no night action).
- `engine/src/main/resources/botc/data/night_and_jinxes.json:18-22` — the
  `cannibal`/`zealot` jinx with correct text.
- No `night_guide.json` entry (correct for a character that never wakes — but see
  Defect 6, the app has nowhere else to put day-phase rules text).

Code: **no Kotlin file mentions `zealot`.** The day flow is entirely generic:
- `app/.../screens/DayScreen.kt:71-72` computes `aliveCount = state.alivePlayers.size`
  (which **does** include travellers — `GameState.alivePlayers`,
  `engine/.../GameState.kt:116` — matching the rule) and the execution threshold.
- `:131-152` picks nominator and nominee; `:154-159` renders
  `StatusEffects.nominationWarnings`.
- `engine/.../StatusEffects.kt:132-166` (`nominationWarnings`) covers Witch-cursed
  nominators, the Golem, the Virgin, the Fearmonger's "Fear" token and Cerenovus madness.
  **No Zealot branch.**
- `:161-196` renders one `FilterChip` per player in clockwise-from-nominee order; a chip
  is enabled when `p.alive || !p.ghostVoteUsed || isExile` (`:184`). Nothing distinguishes
  the Zealot.
- `:197-216` computes the outcome; `:217-247` records the `Nomination`
  (`engine/.../GameState.kt:63-72`, which stores `voterIds`) and spends ghost votes.
- `isExile` is derived from `nominee?.isTraveller` (`:163`) — the app already knows when a
  vote is an exile, so suppressing the obligation there is trivial.
- Nothing reviews a completed nomination for a missing obligatory vote.

Storyteller experience today: the Zealot is invisible to the app. The Storyteller must
themselves remember that a Zealot is in play, count the alive players before every single
nomination, notice whether that seat's hand went up, decide whether an exile counts, and
remember that a dead Zealot is free — all while running the vote. Nothing on the Day
screen mentions the character.

## Defects and gaps

1. **P1 · No nomination-time reminder that the Zealot must vote.**
   `StatusEffects.nominationWarnings` (`engine/.../StatusEffects.kt:132-166`) is the exact
   hook and has no Zealot branch, so `DayScreen.kt:154-159` prints nothing. Repro: 8
   players alive, Zealot in play, select a nominator and nominee — no notice appears.
   This is the single thing the storyteller most needs surfaced, at the exact moment they
   need it.
2. **P1 · The vote chips do not mark the Zealot's obligation.** In
   `app/.../screens/DayScreen.kt:183-196` every seat renders identically. The Zealot's
   chip should be visibly obligatory while 5+ are alive, so a missing hand is noticed
   *before* "Record" is tapped.
3. **P1 · No post-tally check.** When "Record" is pressed (`DayScreen.kt:217-247`) the app
   never notices that an alive Zealot is absent from `orderedVoterIds` while 5+ are alive.
   The correct behaviour is **warn, do not auto-add** (the wiki: "do not tally the
   Zealot's vote"), which the app is well placed to do and currently does not.
4. **P1 · The obligation does not follow the ability.** The Cannibal jinx
   (`night_and_jinxes.json:18-22`) means the *Cannibal* must vote after eating a Zealot,
   and must be told. The app has no ability-transfer model at all, so the jinx text is
   only ever displayed in the "Jinxes in play" dialog
   (`app/.../screens/GameExtras.kt:200-220`) if both characters happen to be in play —
   with no prompt to tell the Cannibal and no follow-through on voting.
5. **P2 · Exile votes are not distinguished for the obligation.** The Zealot need not vote
   on exiles. `isExile` already exists at `DayScreen.kt:163`; the warning must be
   suppressed there or it will train the Storyteller to ignore it.
6. **P2 · There is nowhere in the app to read the Zealot's rules during the day.**
   `NightGuide` is night-only (`engine/.../NightGuide.kt:56-59`), and the Script tab shows
   only the ability line. A Storyteller who forgets whether travellers count has to leave
   the app.
7. **P2 · No day-start briefing.** Nothing tells the Storyteller at dawn "Zealot is alive
   and 7 are alive — they must vote on everything today", which is exactly the kind of
   day-start briefing the audit brief asks for.
8. **P2 · The alive count is not shown against the threshold of 5.** `DayScreen.kt:87-92`
   prints "N alive · T votes to execute" but never flags the 5-alive boundary, which is
   the single number that turns the Zealot's ability on and off. The wiki example turns on
   exactly this transition (7 → 5 → 3 alive).
9. **P3 · The Legion / Vizier "might register as evil" jinxes are missing** from
   `night_and_jinxes.json`.
10. **P3 · No voting-pattern aid.** The app stores every `voterIds` list
    (`engine/.../GameState.kt:69`) but never derives "these seats voted on every
    nomination" — a one-line derivation that directly supports the wiki's Storyteller tip
    about spotting a Zealot (and about spotting an evil player bluffing Zealot).

## Proposed behaviour (spec)

The Zealot has **no night action, no tokens, no setup requirement, and no information**.
The entire spec is day-phase enforcement, expressed as a reusable *vote obligation*
concept so the Butler, Organ Grinder and the vote-restricting travellers can share it.

### New engine concept: vote obligations

```kotlin
// StatusEffects.kt
data class VoteObligation(
    val playerId: Long,
    /** MUST_VOTE, MAY_NOT_VOTE, CONDITIONAL … */
    val kind: Kind,
    val reason: String,          // storyteller-voice, shown at nomination time
)

fun voteObligations(
    state: GameState,
    lookup: (String) -> Character?,
    isExile: Boolean,
): List<VoteObligation>
```

Zealot rule inside it:

```
for each seat whose EFFECTIVE ability is the Zealot's
    (characterId == "zealot", or a Cannibal holding the Zealot ability):
  if (!isExile && seat.alive && state.alivePlayers.size >= 5)
    emit MUST_VOTE, reason = "<Name> is the Zealot — they must vote on every nomination
                              while 5 or more players are alive (travellers count)."
```

Notes for the implementer:
- `state.alivePlayers` (`engine/.../GameState.kt:116`) already includes travellers —
  use it, **not** `aliveNonTravellers`.
- Evaluate **per nomination**, not per day: a death between nominations changes the answer.
- Do **not** suppress the obligation when the Zealot is drunk or poisoned. Optionally add
  a storyteller-only footnote: *"<Name> is drunk/poisoned — their ability isn't working,
  but they don't know that; expect them to vote anyway and do not correct them."*

### Nomination-time surfacing

- `StatusEffects.nominationWarnings` (`engine/.../StatusEffects.kt:132-166`) gains the
  Zealot line via `voteObligations`, so `DayScreen.kt:154-159` picks it up with no UI
  change. It must fire **as soon as a nominee is chosen** (before hands go up), and must
  **not** fire when `isExile`.
- `DayScreen.kt:183-196`: a seat under a `MUST_VOTE` obligation renders its chip with a
  distinct leading marker (e.g. a small "!" badge and `AgedGold` outline) and the
  helper line above the chips gains **"<Name> must vote (Zealot)"**.

### Record-time guard (warn, never auto-vote)

In `DayScreen.kt:217-247`, before recording, if any `MUST_VOTE` obligation's player is
**not** in `orderedVoterIds`:

- Show a non-blocking confirm: title **"Zealot didn't vote"**, body
  **"<Name> is the Zealot and 7 players are alive, so they must vote. Do not add the vote
  for them — remind them, re-run the hand count if you want, then record what actually
  happened."**, buttons **"Record as counted"** (proceed unchanged) and **"Go back"**.
- Under no circumstances mutate `voters`. This is explicitly what the wiki forbids.

### Day-start briefing

At DAY start, if a live Zealot exists and `alivePlayers.size >= 5`, add a briefing line:
**"Zealot (<Name>) must vote on every nomination today — 7 alive."**
When the count is 4 or fewer: **"Only 4 alive — the Zealot is free to vote as they like."**
When the Zealot is dead: **"The Zealot is dead — their ghost vote is unconstrained."**

### Threshold visibility

`DayScreen.kt:87-92` — when a Zealot is in play, append the boundary to the header line:
**"7 alive · 4 votes to execute · Zealot must vote (5+ alive)"**, switching to
**"Zealot free (under 5 alive)"** below the boundary.

### Cannibal jinx

- When the Cannibal eats an executed Zealot, the Storyteller must be prompted at dawn:
  **"Cannibal jinx: <Cannibal> has the Zealot ability — wake them and show the Zealot
  token so they know they must vote."** This needs the general "gained ability" model
  (see the Plague Doctor spec's `StorytellerAbility`, generalised to a seat-held
  `GainedAbility(playerId, characterId, source)`); `voteObligations` should then read
  effective abilities rather than `characterId` alone.
- Add a `ShowCard.CharacterCard("YOU HAVE THIS ABILITY", "zealot")` to the Cannibal's
  night guide entry.

### Voting-pattern aid (optional, cheap)

Derive from `state.nominations` a per-seat "voted on N of M nominations" line in the game
log (`app/.../screens/GameExtras.kt` log dialog). Supports both the "spot the Zealot" and
"spot the Zealot bluff" tips without adding state.

### Data changes

- `characters.json:1741-1751` — no change; text is current.
- `night_and_jinxes.json` — add (lower confidence, mark as such if the project tracks
  provenance):
  ```
  legion/zealot  "The Zealot might register as evil to Legion."
  vizier/zealot  "The Zealot might register as evil to the Vizier."
  ```
- `night_guide.json` — no night entry. If a `"day"` channel is added to `NightGuideEntry`
  (`engine/.../NightGuide.kt:36-40`) — recommended, several day-only characters need it —
  add:
  ```json
  "zealot": { "day": { "instructions": "During each nomination, if 5 or more players are alive (travellers count), the Zealot must raise their hand to vote. They do not have to vote on exiles. A dead Zealot votes like any other dead player. If the Zealot forgets to vote, do not tally their vote — that would reveal them and punish evil unfairly.", "shows": [] } }
  ```

### UI text

- Nomination warning: **"! <Name> is the Zealot — must vote (5+ alive; travellers count)."**
- Exile: *(no warning)*
- Record guard title: **"Zealot didn't vote"**
- Record guard body: **"Do not add the vote for them. Remind the table and record what
  actually happened."**
- Day header suffix: **"Zealot must vote (5+ alive)"** / **"Zealot free (under 5 alive)"**

## Tests to add

1. *Given* 7 alive (5 residents + 2 travellers) and a live Zealot, *when*
   `voteObligations(isExile = false)` runs, *then* it returns one `MUST_VOTE` for the
   Zealot.
2. *Given* 4 alive and a live Zealot, *then* `voteObligations` returns nothing.
3. *Given* 5 alive including one traveller and a live Zealot, *then* the obligation is
   present (travellers count toward the 5).
4. *Given* an exile nomination against a traveller with 8 alive and a live Zealot, *then*
   `voteObligations(isExile = true)` returns nothing.
5. *Given* a **dead** Zealot with 9 alive, *then* no obligation is returned.
6. *Given* a Zealot carrying `poisoner:Poisoned` with 7 alive, *then* the obligation is
   still returned (with the drunk/poisoned footnote).
7. *Given* 6 alive at the start of a day and a death between nomination 1 and nomination
   2 taking it to 4, *then* nomination 1 carries the obligation and nomination 2 does not.
8. *Given* `nominationWarnings` with a Zealot nominee and a Zealot nominator, *then* the
   warning appears once, keyed to the Zealot's seat regardless of their role in the
   nomination.
9. *Given* a recorded nomination with 7 alive where the Zealot is absent from `voterIds`,
   *then* the record-time guard reports the omission **and** the stored `votes` count is
   unchanged.
10. *Given* a Cannibal that has gained the Zealot ability, *then* `voteObligations`
    returns `MUST_VOTE` for the **Cannibal**, not for the dead Zealot.
11. *Given* no Zealot in play, *then* `nominationWarnings` output is unchanged from today
    (regression guard).
