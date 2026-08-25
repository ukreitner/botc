# Butler (butler) — Trouble Brewing Outsider

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Butler> (fetched 2026-08-25),
plus <https://wiki.bloodontheclocktower.com/Rules_Explanation> and
<https://wiki.bloodontheclocktower.com/Glossary> for the drunk/poisoned ruling.

Current ability text:

> "Each night, choose a player (not yourself): tomorrow, you may only vote if
> they are voting too."

How to run:

- Each night (first and every other night) wake the Butler; they point at a
  player **other than themselves**. Mark that player **MASTER**.
  *"This may be the same player as last night or a different one."*
- The restriction, verbatim: *"If the Master has their hand raised to vote, or if
  the Master's vote has already been counted, the Butler may raise their hand to
  vote."* Tally order does not matter — the Master's vote may be counted before
  or after the Butler's.
- The Butler must **lower their hand** if the Master lowers theirs before the
  tally reaches them.
- The Butler is never *forced* to vote, only limited.
- **Nominating is not voting** — the Butler may nominate freely. (The restriction
  is stated purely in terms of raising a hand to vote.)
- **Dead Master:** *"Dead players may only raise their hand to vote if they have
  a vote token. If the Butler chooses a dead player as their Master, this still
  applies."* — i.e. the Master must actually vote, which a dead Master can only
  do once.
- **Dead Butler:** *"The Butler is dead. Because dead players have no ability,
  the Butler may vote with their vote token at any time."*
- **Drunk or poisoned Butler:** the restriction does **not** apply — a drunk or
  poisoned Butler may vote as they please (the Storyteller simply allows the
  vote; the Butler player is not told). Sources: the wiki's Butler/Glossary
  treatment of "no ability" plus the exile clause below.
- **Exiles:** *"Because exiles are never affected by abilities, the Butler can
  vote freely for an exile."*
- The MASTER token is removed at dusk (it only governs "tomorrow").
- Enforcement is largely self-policing at a physical table; a **digital
  Storyteller tool should still flag it**, because the app is the vote tally.

Jinxes (from `night_and_jinxes.json`):
- `cannibal` × `butler`: "If the Cannibal gains the Butler ability, the Cannibal
  learns this."
- `butler` × `organgrinder`: "If the Organ Grinder is causing eyes closed voting,
  the Butler may raise their hand to vote but their vote is only counted if their
  master voted too."

Night order: `firstNight` index 52 (right after `fortuneteller`), `otherNight`
index 89 (near the end, after `cultleader`, before `spy`). Matches the official
sheet.

## What the app does today

Data
- `characters.json` — `butler`: ability text matches the wiki.
  `reminders: ["Master"]`. First/other night reminders are the official strings.
- `night_and_jinxes.json` — `firstNight[52]`, `otherNight[89]`. Correct.
- `night_guide.json` — `butler.first` and `.other` present with otherwise good
  prose. **The first-night entry ends with a false rule:** *"This still applies
  even if the Butler is drunk or poisoned."*

Engine
- `GameActions.kt:235` — `EXPIRES_AT_DUSK` contains `"butler" to "Master"`, and
  `advancePhase` (`GameActions.kt:261-262`) clears it on DAY → NIGHT. Correct.
- `GameActions.kt:194-201` — `placeExclusiveReminder` gives the single MASTER
  token move-not-accumulate semantics; the tray uses it because
  `butler.allReminders` has one copy (`NightScreen.kt:319-324`).
- **That is the entire Butler implementation.** `grep -w butler` over
  `engine/src/main` and `app/src/main` returns exactly one hit: the expiry table.

UI
- No Butler-specific anything. The night step is a plain row; the tool tray
  offers the "Master" chip while the step is expanded.
- `DayScreen.kt:183-196` — the vote tally. The only gating is
  `val canVote = p.alive || !p.ghostVoteUsed || isExile` (line 184). No Master
  check, no Butler warning, no colouring.
- `StatusEffects.nominationWarnings` (`StatusEffects.kt:132-166`) has no Butler
  branch.

Storyteller experience: place the Master token at night; next day, tap voter
chips with no assistance whatsoever. The Storyteller must remember who the
Butler is, who their Master is, watch both hands, and refuse the Butler's vote by
simply not tapping the chip — while the app happily records an illegal tally as a
lawful execution.

## Defects and gaps

1. **P1 · The vote tally does not know about the Butler at all.**
   `DayScreen.kt:183-196`. Repro: night 1 the Butler picks Ana as Master; day 1
   nominate someone, tap the Butler's chip but not Ana's, tap "Record" → the app
   records the vote and can put a player on the block on the strength of an
   illegal Butler vote. Nothing warns.

2. **P1 · No Master information is visible on the Day tab.**
   The Master token lives on the *Master's* seat in the grimoire
   (`GrimoireScreen.kt:471-491`) and is not surfaced on the Day screen at all.
   The Storyteller must switch tabs mid-nomination to find out who the Master is.

3. **P1 · `night_guide.json` states a wrong rule.**
   `butler.first.instructions`: "This still applies even if the Butler is drunk
   or poisoned." A drunk or poisoned Butler's ability does **not** work and they
   may vote freely. This is an app-authored sentence (it is not on the wiki), and
   it will make Storytellers rule incorrectly at the table.

4. **P1 · No Butler resolution panel.**
   `QuickResolutions` (`NightScreen.kt:462-525`) has no `butler` branch, so there
   is no target picker, no "not yourself" constraint, no "same as last night"
   hint. The Storyteller must use the tray. Because the tray places whatever chip
   is selected on whatever seat is tapped, **the Butler can be made their own
   Master**, which the rules forbid.

5. **P2 · Exile votes are not exempted for the Butler in any visible way.**
   `DayScreen.kt:184` allows every player to vote on an exile (`|| isExile`),
   which happens to be right — but only by accident, and there is no text saying
   so, so a Storyteller who has half-remembered the rule will second-guess it.

6. **P2 · A dead Butler / dead Master is not explained.**
   A dead Butler is unrestricted; a dead Master must spend their ghost vote for
   the Butler's vote to be legal. Neither is surfaced. The app already tracks
   `ghostVoteUsed` (`GameState.kt:28`, spent at `DayScreen.kt:232-240`), so both
   are computable.

7. **P2 · No day-start briefing.**
   The single most useful line the app could print on day N is "`<Butler>` may
   only vote today if `<Master>` votes." There is no day-start briefing anywhere
   in `GameShell.kt`.

8. **P2 · The `organgrinder` jinx is unimplemented** (eyes-closed voting: the
   Butler may raise their hand but the vote only counts if the Master voted).
   Only static jinx text in `SeatSheet.kt:222-235`.

9. **P3 · The other-night guide text duplicates work the engine did.**
   `butler.other.instructions` begins "Remove the Master reminder from the
   previous master" — `advancePhase` already cleared it at dusk.

## Proposed behaviour (spec)

### Night step
- **when:** first **and** other nights; wake condition = holder is **alive**.
  (Dead Butler has no ability, so no step.)
- **targets:** exactly 1, `alive || dead` (a dead Master is legal), **must not be
  the Butler themself**. Picker sorts alive first; annotates last night's Master
  with "Master last night (allowed again)"; annotates dead candidates with
  "dead — only counts if they spend their ghost vote"; the Butler's own chip is
  **disabled** with the label "not yourself".
- **immediate effects:** `placeExclusiveReminder(target,
  PlacedReminder("butler", "Master"))`.
- **deferred effects:** at **day start**, the briefing prints
  "`<Butler>` may only vote today if `<Master>` votes too." If the Butler is
  impaired, print instead (Storyteller-only) "`<Butler>` is drunk/poisoned — they
  may vote freely today; don't tell them."
- **expiry:** at **dusk** (already correct).
- **information:** none computed; nothing shown to the Butler.
- **visibility:** nothing shown to anyone.
- **day-time inputs the app must consume:** the vote tally — see below.

### Day-time enforcement (`DayScreen.kt`)

Add a pure helper so both the engine tests and the UI use one rule:

```kotlin
// StatusEffects.kt (or a new Voting rules object)
sealed interface VoteLegality {
    data object Allowed : VoteLegality
    data class NeedsMaster(val masterId: Long, val masterName: String) : VoteLegality
    data class MasterNotVoting(val masterId: Long, val masterName: String) : VoteLegality
}

fun butlerVoteLegality(
    state: GameState,
    lookup: (String) -> Character?,
    voterId: Long,
    currentVoterIds: Set<Long>,
    isExile: Boolean,
): VoteLegality
```
Rules encoded:
- `Allowed` if the voter is not a living, un-impaired Butler;
- `Allowed` if `isExile` (exiles are never affected by abilities);
- `Allowed` if the Butler is dead (dead players have no ability);
- `Allowed` if the Butler is drunk or poisoned (`StatusEffects.isImpaired`);
- otherwise find the seat holding `butler:"Master"`. If none, `NeedsMaster`
  (the Storyteller forgot to place it). If the Master's id is not in
  `currentVoterIds`, `MasterNotVoting`.

UI behaviour in the vote chip row (`DayScreen.kt:179-196`):
- keep the Butler's chip **tappable** (the Storyteller is always the final
  arbiter) but render it in `EmberRed` with a strike-through when the legality is
  not `Allowed`;
- print, under the row, "! `<Butler>` may only vote if `<Master>` does — `<Master>`
  is not voting." and recompute live as chips toggle;
- if the Butler is selected when "Record" is pressed and the legality is not
  `Allowed`, show a confirm dialog: "Count `<Butler>`'s illegal vote anyway?" /
  "Remove it".
- when the Master is dead and has spent their ghost vote, add
  "`<Master>` is dead with no vote token — they cannot vote, so `<Butler>` cannot
  either."

Also render a persistent line at the top of the Day tab, next to
"`N` alive · `T` votes to execute" (`DayScreen.kt:86-92`):
"Butler `<name>` → Master `<master>`".

### Nomination-time
`StatusEffects.nominationWarnings` gains nothing for the Butler — nominating is
unrestricted — but the day-start briefing above covers the recall problem.

### UI text
- Step: "Butler — who did `<name>` point to? (not themselves)"
- After placing: "`<Master>` is the Master. Tomorrow `<Butler>` may only vote if
  `<Master>` votes."
- Day header chip: "Butler → `<Master>`"
- Vote warning: "`<Butler>` can't vote — `<Master>` isn't voting."
- Impaired: "`<Butler>` is drunk/poisoned — their vote is free today."

### Data changes
- `night_guide.json` `butler.first.instructions`: **delete** "This still applies
  even if the Butler is drunk or poisoned." Replace with: "If the Butler is drunk
  or poisoned, they may vote freely tomorrow — allow the vote without telling
  them. The restriction never applies to Traveller exiles, and a dead Butler
  votes freely."
- `night_guide.json` `butler.other.instructions`: drop the "Remove the Master
  reminder…" opener (the app clears it at dusk); add the same drunk/poisoned and
  exile clauses.
- No `characters.json` or night-order changes.

## Tests to add

1. `Given` a living Butler with `butler:"Master"` on Ana and Ana not in the
   voter set, `When` `butlerVoteLegality(butler, voters, isExile = false)`,
   `Then` `MasterNotVoting(Ana)`.
2. Same, but Ana **is** in the voter set, `Then` `Allowed`.
3. `Given` a living Butler and **no** `butler:"Master"` token anywhere,
   `When` legality is computed, `Then` `NeedsMaster`.
4. `Given` a **dead** Butler with a ghost vote, `When` legality is computed,
   `Then` `Allowed` regardless of the Master.
5. `Given` a living Butler poisoned by the Poisoner, `When` legality is
   computed, `Then` `Allowed`.
6. `Given` a living Butler and `isExile = true`, `When` legality is computed,
   `Then` `Allowed`.
7. `Given` `butler:"Master"` on Ana at the end of DAY 1, `When` `advancePhase()`
   runs to NIGHT 2, `Then` no seat holds `butler:"Master"`.
8. `Given` `butler:"Master"` on Ana at the end of NIGHT 1, `When`
   `advancePhase()` runs to DAY 1, `Then` Ana still holds it.
9. `Given` the Butler picks Ana on night 1 and Ben on night 2, `When` the night-2
   placement runs, `Then` only Ben holds `butler:"Master"`.
10. `Given` a dead Master whose `ghostVoteUsed == true`, `When` legality is
    computed for a living Butler, `Then` the result is not `Allowed` and the
    reason mentions the spent ghost vote.
