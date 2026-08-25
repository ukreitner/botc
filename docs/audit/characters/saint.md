# Saint (saint) — Trouble Brewing Outsider

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Saint> (fetched 2026-08-25).

Current ability text:

> "If you die by execution, your team loses."

How to run — verbatim:

- *"If the Saint dies by execution, declare that the game ends and evil wins."*
  The game ends **immediately**, before any further nomination or night.
- Alignment-sensitive: the rules note that in editions where alignment can
  change, *an evil Saint's execution means **good** wins instead* — the ability
  says "your team", not "the good team".
- **Only execution counts.** Wiki example, verbatim: *"The Imp is nominated, and
  the players vote. The Gunslinger kills the Saint. The Saint dies, and the game
  continues."* Demon kills, Slayer shots, Gossip/Godfather kills, and Traveller
  exiles do **not** trigger it.
- **The Saint must actually die.** Wiki example: when the Scapegoat's ability
  takes the execution, *"the game continues, because the Saint did not die."*
  The same applies to a Devil's Advocate "survives execution", a Pacifist save,
  a Fool's first death, a Sailor, and a Tea Lady neighbour.
- A drunk or poisoned Saint has no ability, so executing them does **not** end
  the game. (The wiki does not state this explicitly on the Saint page, but it
  is the general "no ability" rule and is how the app already models it.)
- Storyteller advice: the danger is an *accidental* execution — the Saint's
  entire strategy is to be believed, and evil's is to make them look like the
  Demon.

Jinx (from `night_and_jinxes.json`): `riot` × `saint` — "If Riot nominates and
kills the Saint, the good team loses."

Night order: the Saint never wakes — absent from both order lists. Correct.

## What the app does today

Data
- `characters.json` — `saint`: `team: "outsider"`, ability text matches the wiki,
  no reminders, no night reminders, `setup: false`. Correct.
- `night_and_jinxes.json` — absent from both lists. Correct.
- `night_guide.json` — no entry. Correct (never wakes).

Engine
- `WinCheck.kt:51-68` — the **only** Saint logic:
  ```kotlin
  val executedSaint = state.deaths.lastOrNull {
      if (it.cause != DeathCause.EXECUTION) return@lastOrNull false
      val currentPlayer = players.find { player -> player.id == it.playerId }
      val wasSaint = it.characterIdAtDeath?.let { id -> id == "saint" }
          ?: (currentPlayer?.characterId == "saint")
      val wasImpaired = it.abilityImpairedAtDeath
          ?: currentPlayer?.let { StatusEffects.isImpaired(state, lookup, it) } ?: false
      wasSaint && !wasImpaired
  }
  if (executedSaint != null) return Advisory(goodWins = false,
      reason = "The Saint died by execution - the good team loses.")
  ```
  Two things it gets right: it uses the **snapshot** `characterIdAtDeath`
  (`GameState.kt:85`, written by `GameActions.kill` at `GameActions.kt:152`) so a
  later character change cannot rewrite history, and it honours
  `abilityImpairedAtDeath` (`GameActions.kt:153`) so a poisoned Saint does not
  end the game.
- `GameActions.kill` records `cause` faithfully, and `DeathCause.EXILE` is a
  separate value (`GameState.kt:75`), so a Traveller exile can never be mistaken
  for an execution.
- `StatusEffects.deathNotes` (`StatusEffects.kt:52-129`) has **no** Saint branch.
- `StatusEffects.nominationWarnings` (`:132-166`) has **no** Saint branch.

UI
- `GameShell.kt:505-519` — `WinCheck.check` is recomputed on
  `remember(state.players, state.phase)`; killing the Saint changes `players`, so
  the `WinAdvisoryDialog` fires immediately after the death is recorded.
- `GameExtras.kt` `WinAdvisoryDialog` — title "Is the game over?", the reason
  text, and a primary "Declare evil victory" button (because `goodWins == false`)
  plus a "Keep playing" dismiss.
- Execution entry points, none of which show any warning first:
  - `DayScreen.kt:111-114` — the on-the-block banner's "Execute" button;
  - `DayScreen.kt:350-357` — the per-nomination "Execute" button;
  - `GameShell.kt:598-604` — the dusk guard's "Execute & begin night";
  - `SeatSheet.kt:274-276` — the seat sheet's "Executed" button, which *does*
    route through `requestKill` → `protectionNotes` (`SeatSheet.kt:256-265`) but
    that filter only matches protection strings, and `deathNotes` produces none
    for the Saint.

Storyteller experience: it works, but only *after the fact*. You tap Execute, the
Saint dies, and then a dialog says the good team lost. Nothing warned you while
the nomination was open or while the Saint sat on the block.

## Defects and gaps

1. **P0 · The Saint is never flagged before the execution.**
   Rules: the Storyteller's job is to see this coming — it is the one execution
   that ends the game. App: no warning at nomination (`nominationWarnings` has no
   Saint branch, `StatusEffects.kt:132-166`), none on the block banner
   (`DayScreen.kt:93-115`), none in `deathNotes`, none in the dusk guard
   (`GameShell.kt:592-617`, whose text is just "`<name>` is on the block and
   hasn't been executed"). Repro: nominate the Saint, tally enough votes, tap
   Execute → the game is over with no prior signal.

2. **P1 · The evil-Saint case is hard-coded to `goodWins = false`.**
   `WinCheck.kt:63-67` always returns `goodWins = false`, ignoring
   `Player.alignmentFlipped` / `isEvil(lookup)` (`GameState.kt:49-52`) even
   though the app has a "Flip alignment" button (`SeatSheet.kt:315`). The rule is
   "**your** team loses". Repro: flip the Saint to evil (Cult Leader-style on a
   wider script), execute them → the app declares evil the winner when good
   should win.

3. **P1 · Impairment is snapshot at `kill` time, not at execution-resolution
   time, and the fallback path can flip the answer.**
   `GameActions.kill` computes `abilityImpairedAtDeath` from the state *before*
   the death (`GameActions.kt:153`) — correct. But the `?:` fallback at
   `WinCheck.kt:56-60` re-evaluates `isImpaired` against the **current** state for
   any record lacking the snapshot (old saves). If the Poisoner's token has since
   expired at dusk, an execution that should have been harmless is reported as a
   good-team loss, and vice versa.

4. **P1 · The advisory persists for the rest of the game once dismissed-and-
   forgotten.** `WinCheck.kt:51-62` scans **all** `state.deaths` with no cycle
   bound and no `!resurrected` filter. If the Storyteller taps "Keep playing"
   (e.g. because the Saint was actually protected and they recorded the death by
   mistake, then used "Undo death"), `revive` drops the record
   (`GameActions.kt:162-166`) so that is fine — but `resurrect`
   (`GameActions.kt:173-181`) **keeps** it marked `resurrected = true`, and
   `WinCheck` does not filter on that. A resurrected Saint would keep re-arming
   the "good team loses" advisory. Repro: execute the Saint, undo via
   "Resurrect", advance a phase → the advisory returns.

5. **P1 · `mastermindDayActive` short-circuits the Saint entirely.**
   `WinCheck.kt:28-49` returns from the Mastermind branch before the Saint check
   at line 51. If the Saint is executed during the Mastermind's extra day, the
   app resolves it purely by the Mastermind rule ("their team loses" — which
   happens to give the same answer for a good Saint) and never mentions the
   Saint. Worth an explicit combined message.

6. **P2 · The Saint is not surfaced as a protection concern.**
   When a Devil's Advocate `Survives execution` token, a Fool, a Sailor or a Tea
   Lady would save the Saint, the app's protection dialog
   (`SeatSheet.kt:288-307`) fires — but only from the seat sheet, and its dismiss
   button says "Death prevented" without connecting it to "…so the game
   continues". The Day-tab Execute buttons bypass the dialog entirely.

7. **P2 · Nothing distinguishes an exile from an execution in the UI warning
   surface.** The engine does (`DeathCause.EXILE`), but there is no text anywhere
   telling the Storyteller that exiling a Traveller Saint-claimer is safe.

8. **P2 · `riot` × `saint` jinx unimplemented** ("If Riot nominates and kills the
   Saint, the good team loses") — a Riot kill is not an execution, so the generic
   engine would say the game continues.

9. **P3 · Advisory wording.** "The Saint died by execution - the good team loses."
   uses a hyphen where the rest of the codebase uses an em dash, and does not
   name the player.

## Proposed behaviour (spec)

### The Saint has no night step at all
- **when:** never wakes. No entry needed in either night-order list or
  `night_guide.json`.
- **targets / immediate effects / expiry / information / visibility:** none.

### What the app must do instead — three warning points

**1. Nomination time.** Extend `StatusEffects.nominationWarnings`
(`StatusEffects.kt:132-166`) with:
```kotlin
if (nominee?.characterId == "saint" &&
    !StatusEffects.isImpaired(state, lookup, nominee) &&
    !nominee.isTraveller
) {
    notes += "${nominee.name} is the SAINT — if this execution kills them, " +
        "${if (nominee.isEvil(lookup)) "evil" else "good"} loses and the game ends."
}
```
This renders automatically in `DayScreen.kt:154-159`. Add the mirror case for an
impaired Saint as a Storyteller-only note: "`<name>` is the Saint but is
drunk/poisoned — executing them is safe."

**2. On the block.** The block banner (`DayScreen.kt:93-115`) must carry the same
line above the "Execute" button, in `EmberRed`.

**3. Execution confirm.** Every kill entry point must route through one shared
confirmation that renders `StatusEffects.deathNotes(state, lookup, id)`.
Add a Saint branch to `deathNotes`:
```kotlin
if (id == "saint" && cause == EXECUTION) // pass the cause into deathNotes
    notes += "Saint: executing them ends the game — ${teamWord} loses."
```
`deathNotes` currently takes no cause; give it an optional
`cause: DeathCause? = null` so the Saint (and future execution-only abilities)
can be cause-specific. The confirm dialog's buttons become
"Execute — game over" / "Death prevented".

Entry points to converge: `DayScreen.kt:111-114`, `DayScreen.kt:350-357`,
`GameShell.kt:598-604`, `SeatSheet.kt:274-276`.

### `WinCheck` corrections

```kotlin
val executedSaint = state.deaths.lastOrNull { d ->
    d.cause == DeathCause.EXECUTION &&
        !d.resurrected &&
        (d.characterIdAtDeath ?: state.player(d.playerId)?.characterId) == "saint" &&
        (d.abilityImpairedAtDeath == false)   // require an explicit false
}
if (executedSaint != null) {
    val saint = state.player(executedSaint.playerId)
    val saintIsEvil = saint?.isEvil(lookup) ?: false
    return Advisory(
        goodWins = saintIsEvil,   // "your team loses"
        reason = "${saint?.name ?: "The Saint"} was executed as the Saint — " +
            "the ${if (saintIsEvil) "evil" else "good"} team loses.",
    )
}
```
Changes: `!it.resurrected`; `goodWins` derived from the Saint's alignment; the
impairment fallback made conservative (an old save with a null snapshot should
prompt the Storyteller rather than guess — emit
`Advisory(goodWins = null, reason = "…was executed as the Saint. Was their
ability working at the time?")`).

Also move the Saint check **above** the `mastermindDayActive` early return, or
merge the two: if a Saint is executed during a Mastermind day, report
"The Saint was executed on the Mastermind's extra day — the good team loses."

### UI text
- Nomination note: "! `<name>` is the SAINT — executing them ends the game and
  good loses."
- Block banner: "On the block: `<name>` — **the Saint. Executing them ends the
  game.**"
- Execute confirm: "Execute `<name>` (the Saint)? The game ends and the good
  team loses."
- Impaired Saint: "`<name>` is the Saint but drunk/poisoned — executing them is
  safe. Don't tell them."
- Exile of a Traveller: no Saint text (exiles never trigger it).

### Data changes
None. `characters.json`, `night_and_jinxes.json` and `night_guide.json` are all
correct for the Saint.

## Tests to add

1. `Given` a Saint executed on day 2 with no impairment, `When`
   `WinCheck.check`, `Then` `goodWins == false` and the reason names the player
   (regression guard, with the new wording).
2. `Given` a Saint poisoned by the Poisoner and executed, `When`
   `WinCheck.check`, `Then` no Saint advisory is returned.
3. `Given` a Saint killed by the Demon at night, `When` `WinCheck.check`,
   `Then` no Saint advisory (only execution counts).
4. `Given` a Traveller Saint-claimer exiled (`DeathCause.EXILE`), `When`
   `WinCheck.check`, `Then` no Saint advisory.
5. `Given` a Saint executed and then `resurrect`ed, `When` `WinCheck.check`,
   `Then` no Saint advisory (the record is `resurrected`).
6. `Given` a Saint whose `alignmentFlipped == true` executed while un-impaired,
   `When` `WinCheck.check`, `Then` `goodWins == true`.
7. `Given` a Saint who changed character to the Imp *after* being executed as the
   Saint, `When` `WinCheck.check`, `Then` the advisory still fires (snapshot —
   regression guard).
8. `Given` an un-impaired Saint nominated, `When` `nominationWarnings` is
   computed, `Then` it contains a SAINT warning naming the losing team.
9. `Given` an impaired Saint nominated, `When` `nominationWarnings` is computed,
   `Then` it says the execution is safe.
10. `Given` a Saint holding `devilsadvocate:"Survives execution"`, `When`
    `deathNotes(saint, EXECUTION)` is computed, `Then` both the survival note and
    the Saint note appear, and the Saint note is qualified as conditional on the
    death actually happening.
