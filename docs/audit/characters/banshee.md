# Banshee (banshee) — Experimental Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Banshee> (fetched via
`action=parse&prop=wikitext`, 2026-08-25).

Current ability text (verbatim):

> "If the Demon kills you, all players learn this. From now on, you may nominate twice per day and vote twice per nomination."

**Summary bullets (verbatim) — these are the day-rule changes:**

- "When alive, the Banshee nominates and votes as normal."
- "When dead, they may nominate twice per day, even though dead players may normally not nominate at all."
- "When dead, they may vote for any nomination they wish and **do not need a vote token to do so**. They may vote twice for the same nomination."
- "The Banshee only gains these powers if they were killed by the Demon. Dying by execution or to a non-Demon ability does not count."
- "To vote twice, the Banshee player raises both hands when votes are counted. If the player is unable to do this due to a disability, the Storyteller can count their normal vote twice."

**How to Run (verbatim):**

> If the Banshee is killed by the Demon, place the **HAS ABILITY** reminder token next to the Banshee and say "The Banshee has awoken" or something similarly dramatic. The Banshee may nominate twice per day, but it is the player's responsibility to remember how many times they have nominated. The Banshee may raise two hands when voting. When counting the votes, count each hand as a vote.
>
> If the Banshee is killed by the Demon but does not have their ability at that time or is killed by a non-Demon ability, then do not tell the group that the Banshee ability has been triggered. The Banshee may not nominate, and needs a vote token to vote, like a regular dead player.
>
> If all good players are dead, the game continues. Good can still win due to the Banshee being able to nominate.

**Examples (verbatim):**

1. "The Kazali kills the Banshee. All players learn that the Banshee has died. Tomorrow, the Banshee nominates the Village Idiot and votes twice, then nominates the Fearmonger and votes twice, then votes twice when the Shugenja is nominated. The next day, the Banshee doesn't nominate at all, but votes twice for the Kazali."
2. "The Banshee is poisoned. The Ojo kills the Banshee. Nobody learns that the Banshee has died, and for the rest of the game, the Banshee may not nominate, and has just one vote."
3. "The Lycanthrope kills the Banshee. The Banshee does not gain their additional powers and is not announced."

Example 1 is decisive on the vote rule: the Banshee votes twice on **three
separate nominations in a single day**, and again the next day. It is not one
double ghost vote — it is unlimited double votes.

**Jinxes (verbatim):**

| With | Text |
|---|---|
| Leviathan | "Each night\*, the Leviathan chooses an alive good player (different to previous nights): a chosen Banshee dies & gains their ability." |
| Riot | "Each night\*, Riot chooses an alive good player (different to previous nights): a chosen Banshee dies & gains their ability." |
| Vortox | "If the Vortox kills the Banshee, all players learn that the Banshee has died." |

**Night order:** other nights only, index 62 of 96
(`night_and_jinxes.json:435`, between Sage and Professor) — a checkpoint for
placing the token, with the public announcement happening at dawn.

## What the app does today

Data:
- `characters.json:1277` — ability text matches the wiki exactly. `setup: false`; `firstNightReminder: ""`; `otherNightReminder: "If the Banshee was killed by the Demon tonight, announce that the Banshee has died."`; `reminders: ["Has Ability"]`. **Correct.**
- `night_and_jinxes.json:435` — other-night index 62; correctly absent from the first night.
- `night_and_jinxes.json:185` — the Vortox jinx is present ("If the Vortox kills the Banshee, all players still learn that the Banshee has died"). The **Leviathan** and **Riot** jinxes are **missing**.
- `night_guide.json:883` — a good `other` entry covering the token, the dawn announcement, the double nomination/vote, and the drunk/poisoned exception.

Code: **no `banshee` string exists anywhere in `engine/src` or `app/src`.**
Consequences, precisely:

- **Nomination.** `DayScreen`'s nominator chip row is gated on
  `p.alive && !GameActions.hasNominatedToday(state, p.id)`
  (`DayScreen.kt:135–138`; `hasNominatedToday` at `GameActions.kt:285`). A dead
  Banshee is `alive == false`, so the chip is **disabled and unselectable**
  (`PlayerChipRow`, `DayScreen.kt:299`, enables only `enabled(p) || selected == p.id`).
  There is no override anywhere in the UI.
- **Second nomination.** Even for a living player, `hasNominatedToday` blocks a
  second nomination unconditionally.
- **Voting.** The tally is a `Set<Long>` (`DayScreen.kt:60`), so a player can
  contribute at most **1** to `orderedVoterIds.size` (`DayScreen.kt:172, 226`).
  There is no arithmetic path to a double vote.
- **Ghost vote.** `canVote = p.alive || !p.ghostVoteUsed || isExile`
  (`DayScreen.kt:184`) and the recorder spends the ghost vote
  (`DayScreen.kt:234–240`). A dead Banshee is therefore locked out after one
  vote for the rest of the game, contradicting "do not need a vote token".
- **Trigger.** `DemonKillPanel` (`NightScreen.kt:534`) kills with
  `DeathCause.DEMON` (`NightScreen.kt:629`) and does nothing else.
  `StatusEffects.deathNotes` (`StatusEffects.kt:94–127`) has no `banshee` case,
  so the "who dies?" panel gives no hint. Nothing places `banshee:Has Ability`,
  nothing checks impairment at the moment of death, nothing announces.
- **Announcement.** The DAWN step's detail is the generic "Wait a few seconds.
  Everyone opens their eyes. Announce who died." (`NightOrder.kt:59`).
- **Death cause is ambiguous.** `SeatSheet.kt:271` labels
  `DeathCause.DEMON` as the button **"Died at night"** — so the app's own
  "killed by the Demon" flag is set for *every* night death recorded from a
  seat, including Godfather, Assassin, Vigormortis, Lycanthrope and
  Storyteller-invented deaths. Any automation keyed naively on
  `DeathCause.DEMON` would wrongly trigger the Banshee (wiki Example 3 is the
  Lycanthrope case).

Storyteller's actual experience: on the night the Demon kills the Banshee, the
ST kills them from the Demon panel and nothing happens. If they remember the
character at all, they must find the Banshee's night row (62 of 96, after the
kill has already been recorded at row ~37–54), tap "Has Ability", tap the seat,
then remember at dawn to announce it. Then, for the rest of the game, they must
run the Day tab **against** the app: the Banshee's nominations cannot be
recorded at all, so the nomination history and the Town Crier / Flowergirl
calculations that read it (`InfoCalc.kt:295`, `:307`) become wrong; and every
tally the Banshee votes on is understated by one, which changes who is on the
block (`GameActions.aboutToDie`, `GameActions.kt:296`) and can hand the game to
the wrong team.

## Defects and gaps

1. **P0 · A dead Banshee cannot be recorded as a nominator at all.** Rules:
   "they may nominate twice per day, even though dead players may normally not
   nominate at all". App: `DayScreen.kt:137` requires `p.alive`. Repro: kill the
   Banshee with the Demon, go to Day, try to select them as Nominator — the chip
   is greyed out with no explanation and no override.
2. **P0 · Double votes are structurally impossible, so tallies and the block
   are wrong.** Rules: "vote twice per nomination", unlimited, no vote token.
   App: `voters: Set<Long>` (`DayScreen.kt:60`) and `votes = orderedVoterIds.size`
   (`DayScreen.kt:226`). Repro: dead Banshee raises both hands on a 4-vote
   nomination with a threshold of 5 — the app records 4 and says "safe" when the
   nominee is in fact about to die. This directly decides games.
3. **P0 · The dead Banshee is locked out after one vote by the ghost-vote
   rule.** Rules: "do not need a vote token to do so". App: `DayScreen.kt:184`
   and `:234–240`. Repro: Banshee votes on nomination 1; on nomination 2 their
   chip is disabled.
4. **P0 · The trigger is never detected and never announced.** Rules: on a Demon
   kill (with ability), place HAS ABILITY and tell **all players**. App: nothing
   in `DemonKillPanel` (`NightScreen.kt:534`), nothing in `deathNotes`
   (`StatusEffects.kt:52`), nothing at dawn. Repro: Demon kills the Banshee →
   tap Dawn → the app says nothing beyond the generic "announce who died".
5. **P0 · A second nomination by *anyone* is blocked, so even a manual
   workaround fails.** `GameActions.hasNominatedToday` (`GameActions.kt:285`) is
   applied as a hard gate rather than a warning.
6. **P1 · `DeathCause.DEMON` is overloaded with "died at night".**
   `SeatSheet.kt:271`. Automating the Banshee on `cause == DEMON` would fire on
   Lycanthrope, Assassin, Godfather and Storyteller kills. The enum needs a
   genuine "killed by the Demon" distinction (see spec) before any
   Demon-conditional character — Banshee, Sage, Choirboy, Grandmother's
   grandchild — can be automated. This is a cross-cutting engine defect.
7. **P1 · Impairment at the moment of death is not consulted.**
   `DeathRecord.abilityImpairedAtDeath` **already exists and is already
   populated** by `GameActions.kill` (`GameActions.kt:153`) — the Saint check
   uses it (`WinCheck.kt:56`). The Banshee needs exactly the same test (wiki
   Example 2) and nothing reads it.
8. **P1 · Leviathan and Riot jinxes are missing from `night_and_jinxes.json`.**
   Both give a night-kill mechanism to Demons that otherwise never kill, and
   both require a nightly "different to previous nights" target — real
   storytelling work with no app support.
9. **P2 · No day-start briefing.** Once the Banshee has awoken, every day the ST
   should be reminded: "Ana (Banshee, dead) may nominate twice and vote twice
   per nomination today."
10. **P2 · No nomination counter for the Banshee.** The wiki says it is "the
    player's responsibility", but a "1 of 2 nominations used" line costs
    nothing and prevents arguments.
11. **P2 · The "all good players dead" note is not surfaced.** "If all good
    players are dead, the game continues." `WinCheck` (`WinCheck.kt:88`) has no
    caution for an awoken Banshee.
12. **P3 · The Vortox jinx wording drifts** ("all players **still** learn" vs
    the wiki's "all players learn"). Harmless.
13. **P3 · Exile votes are unspecified.** "Vote twice per nomination" — whether
    a Banshee's double vote applies to a Traveller exile is not stated on the
    wiki page. Flag it in the UI rather than silently deciding; the app already
    treats exiles separately (`DayScreen.kt:163`, `:197`).

## Proposed behaviour (spec)

### Engine data

1. **Fix the death-cause enum first.** Split the overloaded value:
   ```kotlin
   enum class DeathCause { EXECUTION, DEMON, OTHER_NIGHT_DEATH, EXILE, STORYTELLER }
   ```
   Keep the values, but change `SeatSheet.kt:271` so "Died at night" writes
   `OTHER_NIGHT_DEATH` and add a distinct **"Killed by the Demon"** button that
   writes `DEMON`. `DemonKillPanel` continues to write `DEMON`. Migrate old
   saves by treating pre-migration `DEMON` records as `DEMON` (no change).
2. Add to `GameState`:
   ```kotlin
   /** Seats that have gained the Banshee's day powers. */
   val bansheeAwoken: Set<Long> = emptySet(),
   ```
   Derivable from the `banshee:Has Ability` reminder, so a reminder-only
   implementation is acceptable — but a set is cheaper for the Day screen to
   read and survives the ST tidying tokens.
3. Add to `Nomination`:
   ```kotlin
   /** Extra votes beyond one per raised hand, keyed by seat. */
   val extraVotes: Map<Long, Int> = emptyMap(),
   ```
   with `votes = voterIds.size + extraVotes.values.sum()`. This generalises to
   any future double-vote effect (Bounty Hunter's evil Townsfolk has an extra
   *nomination*, not an extra vote, so it does not need this).

### Trigger (night → dawn)

- **when:** any death is recorded with `cause == DeathCause.DEMON` on a seat
  whose `characterId == "banshee"`.
- **condition:** `deathRecord.abilityImpairedAtDeath == false`
  (`GameActions.kt:153` already computes it) **and** the seat held no
  `"No ability"` reminder at the time.
- **immediate effects:** place `banshee:Has Ability` on the seat;
  `bansheeAwoken += seatId`; clear `ghostVoteUsed` and mark the seat as
  never needing it again.
- **what the ST must be told, and when:**
  - *at the kill*, inside `DemonKillPanel`, via `StatusEffects.deathNotes`:
    **"Banshee: if the Demon kills them (and they have their ability), announce it publicly at dawn and they gain double nominations & votes."**
  - *at dawn*, in the dawn briefing:
    **"ANNOUNCE PUBLICLY: 'The Banshee has awoken.' Ana died to the Demon — from now on they may nominate twice per day and vote twice per nomination."**
  - when the kill happens but the Banshee was impaired:
    **"Ana was POISONED when the Demon killed them — say NOTHING. They are a normal dead player."** (wiki Example 2)
  - when a Banshee dies to a non-Demon cause:
    **"Ana died to the Lycanthrope, not the Demon — the Banshee ability does NOT trigger. Say nothing."** (wiki Example 3)
- **expiry:** `banshee:Has Ability` never expires. Do **not** add it to either
  table (`GameActions.kt:218/231`).
- **visibility:** the announcement is to **all players**, public, at dawn. The
  Demon/Minions learn it like everyone else.

### Day rules enforcement

In `DayScreen`:

- **Nominator eligibility** becomes
  ```kotlin
  val awoken = p.id in state.bansheeAwoken
  val limit  = if (awoken) 2 else 1
  val used   = state.nominations.count { it.day == state.cycle && it.nominatorId == p.id && !it.isExile }
  enabled = (p.alive || awoken) && used < limit
  ```
  and the chip for an awoken dead Banshee carries the sublabel
  **"Banshee — 1 of 2 nominations used"**.
- **Generalise `GameActions.hasNominatedToday`** into
  `nominationsToday(state, playerId): Int` and
  `nominationsAllowed(state, lookup, playerId): Int`, so the Bounty Hunter's
  evil Townsfolk, Travellers and future characters all fit.
- **Voting:** each voter chip becomes tri-state for an awoken Banshee —
  *no vote · one hand · both hands* — writing `extraVotes[banshee] = 1` on the
  third state. The tally line reads
  **"5 votes (Ana raised both hands)"**. For every other player the chip stays
  binary.
- **Ghost vote:** `canVote = p.alive || !p.ghostVoteUsed || isExile || p.id in state.bansheeAwoken`,
  and the ghost-vote spend loop (`DayScreen.kt:234–240`) skips awoken Banshees.
- **Day-start briefing:**
  **"Ana (Banshee) is awoken — they may nominate twice today and vote twice on every nomination, with no vote token."**
- **Exile votes:** show the double-vote control on exiles too, with a one-line
  caution: *"Rules don't state whether the Banshee's double vote applies to an
  exile — your call."*

### Jinxes to add (`night_and_jinxes.json`)

```json
{"id1":"leviathan","id2":"banshee","reason":"Each night*, the Leviathan chooses an alive good player (different to previous nights): a chosen Banshee dies & gains their ability."},
{"id1":"riot","id2":"banshee","reason":"Each night*, Riot chooses an alive good player (different to previous nights): a chosen Banshee dies & gains their ability."}
```
and correct the Vortox entry (`night_and_jinxes.json:185`) to the wiki wording.
Both new jinxes require a nightly "choose an alive good player, different from
previous nights" step on the Leviathan/Riot rows — the same
different-from-last-night machinery the Balloonist needs
(`NightAction` history), so build it once.

### WinCheck caution

In the `alive.size <= 2` branch (`WinCheck.kt:88`) and wherever "only evil
players remain" is judged, add:
`"An awoken Banshee can still nominate while dead — the game continues even with no good players alive."`

### UI text

- Demon-kill panel note: `! Banshee — if this kill lands and they are sober, announce it publicly at dawn.`
- Dawn briefing: `ANNOUNCE: "The Banshee has awoken." Ana may now nominate twice per day and vote twice per nomination.`
- Suppressed case: `Ana (Banshee) was poisoned when the Demon killed them — say nothing. Normal dead player.`
- Day banner: `Banshee awoken — Ana: 2 nominations, double votes, no vote token needed.`
- Vote chip states: `— · ✋ · ✋✋`

### Data changes

- `night_and_jinxes.json` — add Leviathan and Riot jinxes; fix Vortox wording.
- `characters.json:1277` — no change.
- `night_guide.json:883` — keep; add a `shows` entry with a ready
  `ShowCard.Message("THE BANSHEE HAS AWOKEN")` for the public announcement.

## Tests to add

`engine/src/test/kotlin/com/clocktower/engine/BansheeTest.kt`

1. **Given** a sober Banshee; **when** `kill(banshee, DeathCause.DEMON)`; **then** `bansheeAwoken` contains the seat and a `banshee:Has Ability` reminder is present.
2. **Given** a Banshee holding `poisoner:Poisoned`; **when** `kill(..., DEMON)`; **then** `bansheeAwoken` is empty and no reminder is placed (wiki Example 2). The stored `DeathRecord.abilityImpairedAtDeath` must be `true`.
3. **Given** a sober Banshee; **when** `kill(..., OTHER_NIGHT_DEATH)` (Lycanthrope / Assassin / Godfather); **then** nothing triggers (wiki Example 3).
4. **Given** a sober Banshee; **when** `kill(..., EXECUTION)`; **then** nothing triggers.
5. **Given** an awoken dead Banshee; **when** `nominationsAllowed`; **then** `2`; and after two recorded nominations that day, `0`.
6. **Given** an awoken dead Banshee on day 3 who nominated twice on day 2; **then** they have 2 nominations again on day 3.
7. **Given** a nomination with `voterIds = [a, b, c, banshee]` and `extraVotes = {banshee: 1}`; **then** `votes == 5`.
8. **Given** a threshold of 5 and that nomination; **then** `Voting.outcome` reports ABOUT_TO_DIE (fails today: the app computes 4 votes and says SAFE).
9. **Given** an awoken Banshee who has already voted on nomination 1; **when** nomination 2 is tallied; **then** they are still eligible and `ghostVoteUsed` is untouched.
10. **Given** a **non**-awoken dead Banshee (killed by execution); **then** normal dead-player rules apply: no nomination, one ghost vote, single value.
11. **Given** a Vortox killing the Banshee; **then** the trigger still fires (jinx) — i.e. the trigger must key on `DeathCause.DEMON`, not on the Demon's character id, and must not be suppressed by any Vortox info-falsification path.
12. **Given** an awoken Banshee alive again via `resurrect`; **then** they keep the ability (the ability text is "from now on") and the reminder stays — lock the intended behaviour with a test either way, because the app's `resurrect` (`GameActions.kt:173`) touches nothing else.
13. **Given** an awoken Banshee and 2 living players (a Demon and one other); **when** `WinCheck.check`; **then** the advisory carries the caution that an awoken Banshee can still nominate.
