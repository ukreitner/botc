# Atheist (atheist) — Experimental Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Atheist> (fetched via
`action=parse&prop=wikitext`, 2026-08-25).

Current ability text (verbatim):

> "The Storyteller can break the game rules, and if executed, good wins, even if you are dead. [No evil characters]"

("if executed" = if **the Storyteller** is executed; "even if you are dead" =
even if the Atheist is dead. The wiki's own summary bullet disambiguates it.)

**Summary bullets (verbatim) — these ARE the rules changes:**

- "With the Atheist in play, there are no evil players—no Minions and no Demons."
- "Good wins if the Storyteller is executed. Any living player may nominate the Storyteller, and the Storyteller is executed if 50% or more of the living players vote."
- "If the Atheist is not in play and the Storyteller is executed, evil wins."
- "Good loses if just two players are alive."
- "The Storyteller may break any of the game's rules. They may kill a player who nominated to simulate a Witch curse, kill players at night to simulate a Demon attacking, give players false information to simulate drunkenness, change characters at night to simulate a Pit-Hag, or even have the wrong number of Outsiders in play."

**How to Run (verbatim):**

> During setup, before giving the bag to the players, remove all evil character tokens and add Townsfolk or Outsider character tokens to match the player count.
>
> Break any game rules, as you see fit. Use evil reminder tokens if you need them.
>
> The Atheist is a complex character, and is recommended for experienced Storytellers. […] only break the game rules to give false information (as much or as little as you want) or simulate the actions of evil characters that could be in play, but aren't. […]
>
> Avoid creating arbitrary rules or changing the win conditions. In order to have a good time, the players need to know how they can win the game if an Atheist is in play.

**Examples (verbatim):**

1. "The Investigator learns that either the Grandmother or the Seamstress is the Boomdandy. The Sweetheart nominates, and dies, even though there is no Witch in play. The Slayer uses their ability on the Gossip, who dies."
2. "There are three Outsiders in play, when there should be two. The players execute the Storyteller. Good wins."

**Jinx (verbatim):**

| With | Text |
|---|---|
| Riot | "During a riot, if the Storyteller is nominated, players vote. If they are 'about to die', the game ends. If not, they nominate again." |

(Riot normally resolves nominations by immediate death without a vote; the jinx
carves out an exception so the Storyteller can still be executed.)

**Threshold note:** "50% or more of the living players" is exactly the app's
existing `Voting.executionThreshold(aliveCount) = (aliveCount + 1) / 2`
(`GameState.kt:136`) — ceil(n/2) equals "50% or more" for both parities. No new
maths is needed, only a new nominee.

## What the app does today

Data:
- `characters.json:1251` — ability text matches the wiki exactly. `setup: true`, no night reminders, no reminders. **Correct.**
- `night_and_jinxes.json` — correctly absent from both night orders. **But** the Atheist–Riot jinx is **not** in the `jinxes` array (a grep for `atheist` in that file finds nothing).
- `night_guide.json` — no entry (correct: the Atheist never wakes; but there is also no day-time run-book, see `alsaahir.md` for the schema gap).

Code — the Atheist is the one character in this scope with *some* engine
awareness, and it is exactly one line:
- `Setup.TEAM_WARPING_IDS = setOf("atheist", "legion", "riot")` (`Setup.kt:72`), consumed by `Setup.modifierFor` (`Setup.kt:127`), which returns `SetupModifier(id, "No evil characters", choiceTeams = Team.entries.toSet())`.
- Effect in `GameActions.validateBag` (`GameActions.kt:420`): `unboundedChoiceTeams` becomes **every** team, `relaxedTeams` becomes every team, `checkedTeams` becomes **empty**, and `matchesAllowedDistribution` is therefore vacuously `true`. Distribution checking is switched off entirely. `GameActionsTest.kt:410` (`atheist bag with no evil validates`) locks that in.
- `SetupScreen` (`SetupScreen.kt:375`) does show `(after [No evil characters])` next to the "Need: N townsfolk · N outsiders · N minions · 1 demon" line — a small win, though the numbers it qualifies are still the evil-inclusive ones.
- `GameDataTest.kt:83` asserts the Atheist has `setup == true`.
- `SetupTest.kt:107` asserts `modifierFor` produces a choice modifier.

Nothing else. In particular:
- `GameActions.randomBag` (`GameActions.kt:338`) draws **Demon first, then Minions**, from `Setup.distributionFor(playerCount)`, and only *then* folds in each drawn character's modifier. The Atheist is a Townsfolk drawn last, its deltas are all zero, and the reconciliation loop's `target` is the unmodified base distribution — so nothing removes the Demon and Minions it already picked. `validateBag` then passes because checking is switched off.
- `WinCheck.check` (`WinCheck.kt:18`) has no Atheist branch. Both of its endings require Demons: `demons.isNotEmpty() && aliveDemons.isEmpty()` (line 70) and `alive.size <= 2 && aliveDemons.isNotEmpty()` (line 88). In a correct Atheist game `demons` is empty, so **neither ever fires**.
- `DayScreen` (`DayScreen.kt:141`) restricts the nominee chip row to `state.players`; there is no way to nominate the Storyteller.

Storyteller's actual experience: they select the Atheist in the bag builder,
see "[No evil characters]" appended to a line that still asks for 1 demon and N
minions, and are given a "Randomize" button that will happily hand them a bag
containing an Imp and a Baron alongside the Atheist. Bag validation then says
nothing is wrong — including when something genuinely is. Through the game, the
app's whole safety net (`validateSetupState`, win advisories, protection
warnings, nomination warnings) is either silent or misleading. At the moment
the town nominates the Storyteller — the entire point of the character — the
app cannot record it, cannot count the votes against the right threshold, and
cannot end the game.

## Defects and gaps

1. **P0 · "Randomize" produces an illegal Atheist bag containing a Demon and
   Minions.** Rules: "remove all evil character tokens". App:
   `GameActions.randomBag` (`GameActions.kt:347–362`) draws
   `Team.DEMON` then `Team.MINION` before the Atheist is even considered, and
   the reconciliation target (`Setup.adjustedDistribution`, `GameActions.kt:374`)
   is unchanged by the Atheist's zero-delta modifier. Repro: script with the
   Atheist, 8 players, tap "Randomize" repeatedly — bags containing both the
   Atheist and a Demon appear, and `validateBag` reports **no** issues.
2. **P0 · The Storyteller cannot be nominated, voted on, or executed.** Rules:
   "Any living player may nominate the Storyteller, and the Storyteller is
   executed if 50% or more of the living players vote… Good wins."
   App: `DayScreen`'s nominee row is `players` only (`DayScreen.kt:141–152`),
   `Nomination.nomineeId` is a `Long` seat id (`GameState.kt:60`), and
   `GameActions.aboutToDie` (`GameActions.kt:296`) returns a seat id. The single
   win condition of the character is unreachable. Repro: play an Atheist game to
   the point where the town wants to execute the ST — the only recourse is
   menu → "Declare good victory", which records nothing.
3. **P0 · No win check at all in an Atheist game.** `WinCheck.check`
   (`WinCheck.kt:70` and `:88`) gates both endings on Demons existing. With no
   Demons: good never wins by killing the Demon (correct), evil never wins at 2
   alive (**incorrect** — "Good loses if just two players are alive"), and the
   Storyteller-executed ending is absent. Repro: play an Atheist game down to 2
   living players — no advisory appears.
4. **P0 · Setup validation is switched off completely, not narrowed.**
   `Setup.modifierFor` returns `choiceTeams = Team.entries.toSet()`
   (`Setup.kt:128`), which empties `checkedTeams` in `validateBag`
   (`GameActions.kt:457–463`). An Atheist game with 4 Outsiders and 2 Demons
   validates cleanly. The correct constraint is precise and checkable:
   **minions == 0 && demons == 0 && townsfolk + outsiders == playerCount**,
   with the Outsider/Townsfolk split left free (Example 2 relies on the ST being
   able to run the "wrong" number of Outsiders).
5. **P1 · The bag builder still offers and requests evil characters.**
   `SetupScreen` (`SetupScreen.kt:348`) lists every `isTownResident` character
   and the "Need:" line (`SetupScreen.kt:373`) still says "1 demon" with the
   bracket text tacked on. When the Atheist is in the bag, evil characters
   should be **greyed out with a reason** and the line should read
   "Need: 8 good characters (no Minions, no Demon) — [No evil characters]".
6. **P1 · No setup briefing for the ST.** This is the character the wiki itself
   calls "recommended for experienced Storytellers". The app should, once,
   state the four rule changes (no evil players; ST can be nominated and
   executed for a good win; good loses at 2 alive; you may break any rule) — the
   same treatment the Drunk/Lunatic/Marionette get (`GameShell.kt:383–478`).
7. **P1 · The Atheist–Riot jinx is missing.** With both on a script the ST must
   run nominations of the Storyteller differently during a riot. Repro: menu →
   "Jinxes in play" with Atheist + Riot → nothing.
8. **P1 · `DeathCause.STORYTELLER` exists but is labelled "Other death" and
   logged as "died (storyteller)".** (`SeatSheet.kt:277`, `GameExtras.kt:60`.)
   In an Atheist game the ST kills people constantly to simulate a Demon; those
   deaths should be recordable as "night kill (simulated Demon)" so the log
   reads like a normal game and so any Demon-conditional character (Sage,
   Choirboy, Banshee, Grandmother) can be simulated consistently.
9. **P2 · Nothing surfaces the "2 alive = good loses" clock.** The Day header
   shows "N alive · M votes to execute" (`DayScreen.kt:88`); in an Atheist game
   it should also warn at 3 alive: "one more death and good loses".
10. **P2 · `StatusEffects.deathNotes` and `nominationWarnings` go quiet.** They
    key off in-play evil characters (`StatusEffects.kt:104–127`,
    `:142–161`), of which there are none. The ST simulating a Witch or a
    Godfather has no checklist. A "simulate an evil ability" palette
    (Witch curse / Devil's Advocate / Godfather double kill / Poisoner) placing
    the corresponding reminder tokens would make the improvisation consistent.
11. **P3 · The Atheist gets no day-time run-book entry** (same schema gap as
    the Alsaahir).

## Proposed behaviour (spec)

### Setup

Replace the blunt `TEAM_WARPING_IDS` treatment for the Atheist with a precise
rule (Legion and Riot need their own, different, handling and should not share
this branch):

```kotlin
// Setup.kt
data class SetupModifier(
    …,
    /** Hard caps that override the distribution table entirely. */
    val forcedTeamCounts: Map<Team, Int> = emptyMap(),
    /** Teams whose exact split is the ST's free choice. */
    val freeTeams: Set<Team> = emptySet(),
)

"atheist" -> SetupModifier(
    characterId = "atheist",
    text = "No evil characters",
    forcedTeamCounts = mapOf(Team.MINION to 0, Team.DEMON to 0),
    freeTeams = setOf(Team.TOWNSFOLK, Team.OUTSIDER),
)
```

`validateBag` then enforces `minions == 0`, `demons == 0`,
`townsfolk + outsiders == playerCount`, and leaves the Townsfolk/Outsider split
alone. `randomBag` must consult `forcedTeamCounts` **before** its team loop
(`GameActions.kt:353`), so the Demon and Minion draws are skipped and the
remaining seats are filled from Townsfolk (and 0–3 Outsiders at random).

Blocking setup dialog when a bag contains the Atheist:

> **The Atheist is in play — the rules change**
> - There are **no evil players**: no Minions, no Demon. The bag is all Townsfolk and Outsiders.
> - **Good wins if you, the Storyteller, are executed.** Any living player may nominate you; you are executed on 50% or more of the living players' votes.
> - **Good loses when only two players are alive.**
> - You may break any rule: kill nominators, kill at night, give false information, change characters, run the wrong number of Outsiders. Do not invent new win conditions.
>
> [ Understood ]

### Day: nominating the Storyteller

- Add a sentinel nominee. Minimal-churn option:
  `const val STORYTELLER_SEAT_ID = -1L` and a `Nomination.nomineeId` allowed to
  hold it, with `Nomination.isStorytellerNomination` derived. (A nullable
  `nomineeId` would ripple through `aboutToDie`, `hasBeenNominatedToday` and
  the log.)
- `DayScreen`'s nominee row gains a distinct chip **"⟡ The Storyteller"**,
  shown only when the script contains `atheist` (never leaking whether the
  Atheist is actually in the bag). Nominator row is unchanged: living players
  only.
- Vote threshold: `Voting.executionThreshold(state.alivePlayers.size)` —
  identical to a normal execution.
- Result copy: `The Storyteller is about to die` / `The Storyteller is safe`.
- On execution:
  - if the Atheist **is** in play (alive **or** dead) and is **not** the Drunk
    or otherwise ability-less → **good wins**;
  - if the Atheist is **not** in play (or the "Atheist" is the Drunk) →
    **evil wins** ("If the Atheist is not in play and the Storyteller is
    executed, evil wins") — this is the trap the wiki's Tips section warns
    about, and the app should present it as a confirmation, not a surprise:
    > **Executing the Storyteller ends the game.** There is no Atheist in play — this is an **EVIL** win. Confirm?
- Log entry: `D3 Ana nominated the Storyteller — 5 votes, executed — good wins`.

### Win check

Add to `WinCheck.check`, before the Demon-based branches:

```kotlin
val atheistSeat = players.find { it.characterId == "atheist" }
val stExecuted = state.nominations.lastOrNull {
    it.nomineeId == STORYTELLER_SEAT_ID && it.result == ABOUT_TO_DIE && it.executed
}
if (stExecuted != null) return Advisory(
    goodWins = atheistSeat != null,
    reason = if (atheistSeat != null)
        "The Storyteller was executed and the Atheist is in play — good wins."
    else
        "The Storyteller was executed with no Atheist in play — evil wins.",
    cautions = listOfNotNull(
        "If the 'Atheist' is really the Drunk, there IS a hidden evil team and evil wins."
            .takeIf { players.any { p -> p.shownCharacterId == "atheist" } },
    ),
)
if (atheistSeat != null && demons.isEmpty()) {
    if (alive.size <= 2) return Advisory(
        goodWins = false,
        reason = "Only ${alive.size} players are alive — with an Atheist in play, good loses.",
    )
    if (alive.size == 3) return Advisory(
        goodWins = null,
        reason = "3 alive — one more death and good loses. Execute the Storyteller while you still can.",
    )
    return null   // suppress the two Demon-based branches entirely
}
```

### Simulating evil (the ST's toolkit)

The Atheist game is defined by the ST faking evil abilities, and the wiki
explicitly says "Use evil reminder tokens if you need them". Give the ST a
one-tap palette (in the Day screen and in the seat sheet):

| Simulate | App action |
|---|---|
| Demon attack | `kill(cause = DEMON)` + dawn announcement, so the log reads like a real game |
| Poisoner / Drunkenness | place `poisoner:Poisoned` / `sailor:Drunk` (feeds `isImpaired` and the false-info shortcuts in `NightScreen.kt:903`) |
| Witch curse | place `witch:Cursed`, which `nominationWarnings` (`StatusEffects.kt:144`) already reacts to |
| Devil's Advocate | place `devilsadvocate:Survives execution` (already in `deathNotes`, `StatusEffects.kt:68`, and in `EXPIRES_AT_DUSK`) |
| Cerenovus madness | place `cerenovus:Mad` (already in `nominationWarnings`) |
| Pit-Hag character change | the existing "Change character" flow (`SeatSheet.kt:310`) |

None of these need new engine code — they need to be *offered*, because in an
Atheist game the evil characters are not in play and therefore never appear in
any reminder picker keyed on in-play characters.

### UI text

- Bag builder line: `Need: 8 good characters — no Minions, no Demon [No evil characters]`
- Greyed evil character reason: `Not allowed — the Atheist is in the bag`
- Nominee chip: `⟡ The Storyteller`
- Day header addendum at 3 alive: `3 alive — with an Atheist in play, good loses at 2.`
- Execution confirmation (Atheist in play): `The Storyteller is executed — GOOD WINS.`
- Execution confirmation (no Atheist): `No Atheist is in play — executing the Storyteller means EVIL WINS. Confirm?`

### Data changes

- `night_and_jinxes.json` — add
  `{"id1":"atheist","id2":"riot","reason":"During a riot, if the Storyteller is nominated, players vote. If they are \"about to die\", the game ends. If not, they nominate again."}`.
- `Setup.kt:72` — remove `"atheist"` from `TEAM_WARPING_IDS` and give it the
  `forcedTeamCounts` treatment above. Legion and Riot keep the old branch until
  they get their own audits.
- `characters.json:1251` — no change.
- `night_guide.json` — add a `day`-scoped entry once the schema supports one.

## Tests to add

`engine/src/test/kotlin/com/clocktower/engine/AtheistTest.kt`

1. **Given** a script containing the Atheist and 8 players; **when** `GameActions.randomBag` is called 50 times with a fixed seed; **then** **no** returned bag contains a Demon or a Minion, and every bag contains exactly 8 good characters. *(Fails today.)*
2. **Given** a bag of Atheist + 7 Townsfolk for 8 players; **when** `validateBag`; **then** no issues. *(Passes today for the wrong reason — keep it, but see 3.)*
3. **Given** a bag of Atheist + 6 Townsfolk + 1 Imp for 8 players; **when** `validateBag`; **then** it reports "Demon: 1 in bag, expected 0". *(Fails today — validation is switched off.)*
4. **Given** a bag of Atheist + 5 Townsfolk + 2 Outsiders for 8 players; **when** `validateBag`; **then** no issues (the Outsider count is the ST's free choice — wiki Example 2).
5. **Given** an Atheist game with 2 living players and no Demon; **when** `WinCheck.check`; **then** an advisory `goodWins = false` "good loses" is returned. *(Returns `null` today.)*
6. **Given** an Atheist game with 5 alive and no Demon; **when** `WinCheck.check`; **then** `null`.
7. **Given** a recorded, executed Storyteller nomination and an Atheist in play; **when** `WinCheck.check`; **then** `goodWins = true`.
8. **Given** a recorded, executed Storyteller nomination and **no** Atheist in play; **when** `WinCheck.check`; **then** `goodWins = false`.
9. **Given** a Drunk whose `shownCharacterId == "atheist"` and an executed Storyteller; **then** the advisory is `goodWins = false` with the caution about the Drunk.
10. **Given** 7 living players; **when** the Storyteller nomination tallies 4 votes; **then** `Voting.outcome` reports ABOUT_TO_DIE (4 ≥ ceil(7/2)); with 3 votes, SAFE.
11. **Given** an Atheist and a Riot on a script; **when** `GameData.activeJinxes`; **then** the Atheist–Riot jinx is returned.
12. **Given** an Atheist game, **when** `Setup.modifierFor(atheist)` is called; **then** `forcedTeamCounts == {MINION: 0, DEMON: 0}` and `choiceTeams` no longer includes TRAVELLER/FABLED (guards against the current `Team.entries.toSet()` over-reach).
