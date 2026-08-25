# Voudon (voudon) — Bad Moon Rising Traveller

## Official rules (sources)

Sources (fetched 2026-08-25):
- <https://wiki.bloodontheclocktower.com/Voudon>
- <https://wiki.bloodontheclocktower.com/Travellers>
- <https://wiki.bloodontheclocktower.com/Glossary>
- Official rulebook, "Travelers" chapter (mirror
  <https://www.web3us.com/sites/default/files/Rulebook.pdf>).

Current ability text (wiki):

> "Only you & the dead can vote. They don't need a vote token to do so. A 50%
> majority isn't required."

`characters.json` has "Only you and the dead can vote. They don't need a vote
token to do so. A 50% majority is not required." — `and`/`&` and `is not`/`isn't`
only. **No meaningful drift.**

How to run (wiki, near-verbatim):

- "**During execution votes, only dead players and Voudon may raise hands. Dead
  players need no vote token. Any nominee receiving at least one vote faces
  execution until another nominee receives more votes.**"
- Key mechanics from the summary:
  - "**Dead players and the Voudon may vote multiple times daily without vote
    tokens.**" — i.e. ghost votes are *not* spent while the Voudon is active, and
    a dead player may vote on every nomination.
  - "**Living players cannot vote during executions.**" ("Alive players cannot
    vote; their hands must remain down.")
  - "**A single vote suffices to execute someone; the person with the most votes
    dies.**" — the threshold is 1, and the "beats the previous highest today"
    rule still applies.
  - "**Voudon does not affect nomination rights (alive players nominate, dead
    cannot).**"
  - "**If Voudon is exiled after nominations begin, voting reverts to normal
    alive-player rules.**" And from the How to Run section: "If a player is about
    to die and then the Voudon is exiled, that player is still about to die" —
    the block is *not* recalculated, but from that point onward the ordinary
    threshold applies to new nominations.
- Examples:
  - "Twelve alive, three dead players. An Innkeeper nominates Moonchild; three
    voting players support this, **executing the Moonchild**." (3 votes with 12
    alive — no majority needed.)
  - "**Day one with only Voudon able to vote (but abstaining). Five players
    support exiling Voudon; seven oppose. Voudon survives.**" — this is the
    decisive confirmation that **exile support is not restricted by the Voudon**
    (all twelve players may support) and that the exile threshold is *at least
    half of all players*: 5 of 12 fails, 6 would pass.
  - "Two dead vote Mastermind; Voudon plus dead Fool and Zombuul vote Gossip.
    Gossip is executed." (3 beats 2.)

Traveller framework (rulebook, verbatim, decisive here):

- Exile: "**Any player, even dead ones, may support the exile of a Traveler. Dead
  players that support an exile do not lose their vote token.**" ·
  "**The process to exile a Traveler is not affected by abilities. It is purely a
  group decision. For example, characters that modify votes (Thief, Bureaucrat,
  etc.) do not modify support for exile. Even the Butler may raise their hand to
  support an exile without their Master raising their hand.**" ·
  "**If at least half of the players support the exile, it succeeds, and the
  exiled Traveller dies. This counts the total number of players in the game, not
  the number of alive players.**"
- "Travelers… **lose their abilities when dead or drunk or poisoned**." — a dead,
  exiled or poisoned Voudon stops changing the vote rules.
- "**Choose Alignment. Tell the Traveler player in private whether they are good
  or evil…**"

Jinxes: none for the Voudon in `night_and_jinxes.json` or on the wiki.

Night order: the Voudon never acts at night; correctly absent from both lists.

Open question flagged honestly: the wiki does not say what happens on a **tie**
under the Voudon. The ordinary rule ("more votes than any other nominated player
today") plus "until another nominee receives more votes" reads as: an equal tally
does **not** displace the current about-to-die player. That is the opposite of the
app's current general tie rule (`Voting.outcome` returns `TIED`, which *clears*
the block). This should be confirmed with the Pandemonium Institute before
shipping; the spec below implements the reading above and flags it.

## What the app does today

Data
- `characters.json` — `voudon`, team `traveler`, ability text as quoted,
  `reminders: []`. Correct.
- `night_guide.json` — **no `voudon` entry**.
- `night_and_jinxes.json` — absent from both orders. Correct.

Engine
- Nothing references `voudon`. `grep -rn "voudon" engine/src app/src` matches only
  `characters.json`.
- `GameState.executionThreshold` (`GameState.kt:122`) = `(alivePlayers.size + 1) / 2`.
- `Voting.executionThreshold(aliveCount)` / `Voting.exileThreshold(totalCount)`
  (`GameState.kt:135-139`) — the exile formula `(totalCount + 1) / 2` is **correct**
  ("at least half of the players… total number of players in the game").
- `Voting.outcome(votes, threshold, currentHighest)` (`GameState.kt:141-152`) —
  `< threshold` → SAFE; `> currentHighest` → ABOUT_TO_DIE; `== currentHighest` →
  TIED. No hook for a Voudon threshold of 1.
- `GameActions.toggleGhostVote` (`GameActions.kt:183-184`) flips
  `Player.ghostVoteUsed`.

UI (`DayScreen.kt`)
- `DayScreen.kt:71-72` — `threshold = Voting.executionThreshold(state.alivePlayers.size)`,
  used for every non-exile nomination.
- `DayScreen.kt:184` — `val canVote = p.alive || !p.ghostVoteUsed || isExile`.
  Every **alive** player is always votable; a dead player is votable only if their
  ghost vote is unspent (except on exiles).
- `DayScreen.kt:197-205` — result via `Voting.outcome(...)` for executions, or a
  bare `size >= voteThreshold` for exiles.
- `DayScreen.kt:232-240` — on Record, every dead voter's ghost vote is spent
  (`!isExile` guard only).
- `DayScreen.kt:131-140` — nominator must be alive: correct under the Voudon
  ("alive players nominate, dead cannot") and correct generally, but **wrong for
  exile callers** (see the exile section below).
- No day-start briefing, no vote-rule banner, no way to express "only these
  players may raise a hand today".
- Alignment defaults to good for travellers (`Character.kt:16`,
  `GameState.kt:45-51`); bare "Flip alignment" (`SeatSheet.kt:315`).

Storyteller experience today: with a Voudon in play, the vote panel is actively
misleading. It offers every living player as a votable chip, refuses dead players
who have already "spent" a ghost vote that the Voudon says they never needed, and
announces "needs 5" when the real answer is "needs 1 and must beat today's best".

## Defects and gaps

1. **P0** · The execution threshold is not reduced to 1 · Rules: "A 50% majority
   isn't required… Any nominee receiving at least one vote faces execution until
   another nominee receives more votes." App: always
   `(alive + 1) / 2` · `DayScreen.kt:71-72,164,176,204`, `GameState.kt:122,135-136`
   · Repro: with a Voudon and 12 alive, nominate and tap 3 dead voters — the app
   says "3 so far, needs 7 … <name> is safe", when the nominee should be about to
   die.

2. **P0** · Living players can still be tapped as voters · Rules: "Living players
   cannot vote during executions." App: `canVote = p.alive || …`
   (`DayScreen.kt:184`) makes every living player enabled, and there is no visual
   distinction · Repro: any execution vote with a Voudon in play.

3. **P0** · Dead players' ghost votes are wrongly consumed · Rules: "They don't
   need a vote token to do so" / "may vote multiple times daily without vote
   tokens." App: `DayScreen.kt:232-240` spends the ghost vote of every dead voter
   on every non-exile nomination, and `DayScreen.kt:184` then blocks them from all
   later votes · Repro: dead player votes on the first nomination of day 2 →
   greyed out for the rest of the game.

4. **P1** · The exile of the Voudon is only accidentally right · The exile path
   already ignores the vote-modifying rules (raw hand count, no ghost-vote spend),
   which matches "exiles are not affected by abilities". But the **caller** of an
   exile must be allowed to be a dead player, and today the Nominator chip row
   requires `p.alive` (`DayScreen.kt:137`) — so with a Voudon in play the dead
   cannot even call the exile that the wiki explicitly recommends as the
   counter-play ("consider self-exile") · `DayScreen.kt:131-140`.

5. **P1** · No handover when the Voudon loses their ability mid-day · Rules: "If a
   player is about to die and then the Voudon is exiled, that player is still
   about to die", and later nominations revert to normal alive-player voting. The
   app has no per-nomination record of which rule set applied, so an undo/redo or
   a recomputation would silently rewrite history · `GameActions.aboutToDie`
   (`GameActions.kt:296-306`) derives everything from stored `result`s, which is
   the right shape — but `highestVotesToday` mixes tallies from both regimes
   (`GameActions.kt:278-282`).

6. **P1** · No day-start briefing · The Voudon changes the single most public
   procedure in the game and the ST must announce it every day: "Hands down,
   living players. Only the dead and the Voudon vote." Nothing in the app says so ·
   `DayScreen.kt:85-124`.

7. **P2** · Tie behaviour under the Voudon is unspecified and the app's generic
   tie rule clears the block · `GameState.kt:141-152` returns `TIED` on an equal
   tally and `aboutToDie` then sets the block to null (`GameActions.kt:296-306`).
   Under "until another nominee receives **more** votes", an equal tally should
   leave the current nominee on the block. Flagged as needing confirmation.

8. **P2** · The vote panel gives no reason for a disabled chip · Even today a
   greyed chip ("ghost vote spent") has no explanation; under the Voudon the
   reason changes entirely · `DayScreen.kt:184-194`.

9. **P2** · Loss of ability by death/poison is unmodelled · A poisoned Voudon
   changes nothing about voting; the app has no such state to change.

10. **P3** · Ability text punctuation differs from the wiki (`and`/`&`) ·
    `characters.json` `voudon.ability`.

11. **P3** · No day-guide entry, so the Voudon's how-to-run text is nowhere in the
    app.

## Proposed behaviour (spec)

The Voudon is the clearest case in this scope for a **vote-rules object** computed
once per nomination and stored on it, rather than recomputed from live state.

### Engine

```kotlin
/** The rules in force for one nomination, resolved when it is opened. */
data class VoteRules(
    val isExile: Boolean,
    val eligibleVoterIds: Set<Long>,   // who may raise a hand
    val threshold: Int,                // votes needed to be about to die
    val spendsGhostVotes: Boolean,
    val weightOf: Map<Long, Int>,      // Bureaucrat ×3, Thief ×-1; empty for exiles
    val reasons: List<String>,         // storyteller-facing explanation
)

fun voteRules(state: GameState, lookup: (String) -> Character?, nomineeId: Long): VoteRules
```

Resolution order:

1. **Exile** (`nominee.isTraveller`) — *always wins, abilities never apply*:
   `eligibleVoterIds = every player id` (alive and dead),
   `threshold = Voting.exileThreshold(state.players.size)`,
   `spendsGhostVotes = false`, `weightOf = emptyMap()`,
   reason: "Exile — any player, alive or dead, may support. No ability changes an
   exile."
2. **Voudon active** (a seat with `characterId == "voudon"`, alive, not impaired):
   `eligibleVoterIds = dead players + the Voudon`,
   `threshold = 1`,
   `spendsGhostVotes = false`,
   reason: "Voudon: only the dead and the Voudon vote; no vote token needed; 1
   vote is enough, but it must beat today's best tally."
3. **Normal:** `eligibleVoterIds = alive players + dead players with an unspent
   ghost vote`, `threshold = Voting.executionThreshold(alive)`,
   `spendsGhostVotes = true`, plus Bureaucrat/Thief weights (see
   `bureaucrat.md`, `thief.md`).

Store the resolved rules on the `Nomination` record:

```kotlin
val thresholdUsed: Int = 0,
val ruleNote: String = "",     // e.g. "Voudon: 1 vote needed"
```

so that undo/redo, the log and `aboutToDie` replay stay faithful when the Voudon
is exiled mid-day.

`GameActions.highestVotesToday` must compare only tallies recorded under the same
regime — simplest correct rule: keep the existing "highest passing tally today"
but exclude nominations whose `thresholdUsed` differs from the current one, and
show the ST a note when the regime changed mid-day ("Voudon exiled — voting has
returned to normal; earlier tallies used the Voudon rules").

Tie handling under the Voudon: `Voting.outcome` gains an
`equalKeepsBlock: Boolean` parameter (true under the Voudon), so an equal tally
returns `SAFE` and leaves the existing block. Default `false` preserves today's
behaviour everywhere else. **Confirm this reading before shipping.**

### Day panel

- Vote header text becomes rules-driven:
  `Vote — only the dead and the Voudon may raise a hand (needs 1, best today: 3)`
- Ineligible chips are disabled with an inline reason chip:
  `Alive — Voudon` / `Ghost vote spent` / `Exile: everyone may support`.
- On Record, `spendsGhostVotes == false` skips the `toggleGhostVote` loop
  entirely (`DayScreen.kt:232-240`).

### Day-start briefing (shared panel)

> **Voudon in play.** Announce before nominations: *"Living players, your hands
> stay down. Only the dead — and the Voudon — vote today. Dead players do not need
> a vote token and may vote on every nomination."* One vote is enough to put
> someone on the block; the highest tally today wins.
> Alive players still nominate; dead players may not nominate (they may still call
> exiles).

When the Voudon is dead/exiled/poisoned:
> **Voudon has lost their ability.** Voting is normal again: half of the 8 living
> players (4) is needed, and dead players spend their vote token. Anyone already
> about to die stays about to die.

### Interactions to handle explicitly

- **Exile of the Voudon** — the single most common play. Must be callable by a
  dead player, supportable by everyone, threshold `(players.size + 1) / 2`, no
  ghost votes spent, and it must take effect for *subsequent* nominations only.
- **Bureaucrat / Thief** — their weights still apply to execution votes under the
  Voudon (the Voudon changes *who* votes and *how many* are needed, not what a
  vote is worth). They never apply to exiles.
- **Beggar** — the Beggar's "must use a vote token to vote" is an ability of the
  Beggar; under the Voudon an alive Beggar cannot vote at all (they are alive and
  not the Voudon), so the token question is moot; a dead Beggar votes freely.
- **Butler** — under the Voudon a living Butler cannot vote anyway.
- **Flowergirl** — `InfoCalc.flowergirl` (`InfoCalc.kt:296-313`) asks whether the
  Demon voted today. A living Demon cannot vote under the Voudon, so the answer is
  NO unless the Demon is dead. This falls out of `voterIds` and needs no change,
  but add a caveat line: "Voudon in play — living players could not vote today."
- **Mayor** ("if no execution happens and 3 players live, good wins") — the Voudon
  makes executions far easier; no code interaction, but worth a caution in the
  win advisory.

### UI text

- `Voudon: only the dead & the Voudon vote — 1 vote is enough`
- Disabled chip reason: `Alive — can't vote (Voudon)`
- Record button subtitle: `No vote tokens are spent under the Voudon`

### Data changes

- `characters.json`: normalise the ability text to the wiki's ("Only you & the
  dead can vote… A 50% majority isn't required.").
- Add a day-guide entry for `voudon` with the How to Run text and the daily
  announcement script.

## Tests to add

1. `Given` an alive, unimpaired Voudon and 12 alive / 3 dead players, `when`
   `voteRules(state, lookup, nomineeId)` is computed for a non-traveller nominee,
   `then` `threshold == 1`, `spendsGhostVotes == false`, and
   `eligibleVoterIds == deadIds + voudonId`.
2. Same setup, `when` 3 dead players vote, `then` the outcome is `ABOUT_TO_DIE`
   (was `SAFE` before the fix).
3. `Given` a dead player who voted on nomination 1 under the Voudon, `then`
   `player.ghostVoteUsed == false` and they are still in
   `eligibleVoterIds` for nomination 2.
4. `Given` a Voudon and a **traveller** nominee, `then` `voteRules` returns
   `isExile = true`, `eligibleVoterIds == all player ids` (including living
   ones), `threshold == (players.size + 1) / 2`, `spendsGhostVotes == false`.
5. Wiki example 2: `given` 12 players total with a Voudon, `when` 5 support the
   Voudon's exile, `then` the result is `SAFE` (5 < 6); `when` 6 support, `then`
   `ABOUT_TO_DIE`.
6. `Given` a Voudon exiled after player P was put on the block, `then`
   `GameActions.aboutToDie(state) == P` still, and the next nomination's
   `voteRules` returns the normal alive threshold with
   `spendsGhostVotes = true`.
7. `Given` a Voudon with a `Poisoned` reminder, `then` `voteRules` returns the
   normal regime (travellers lose abilities when poisoned).
8. `Given` a dead Voudon, `then` the same.
9. `Given` a Voudon regime and nominee A on the block with 3 votes, `when`
   nominee B also gets 3 votes, `then` A is still about to die
   (`equalKeepsBlock = true`). *(Confirm this ruling.)*
10. `Given` a Voudon in play, `when` a **dead** player is chosen as the caller of
    an exile, `then` the nomination is recordable (`isExile = true`).
