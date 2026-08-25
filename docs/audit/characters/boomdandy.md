# Boomdandy (boomdandy) — Experimental Minion

## Official rules (sources)

Source: https://wiki.bloodontheclocktower.com/Boomdandy (Character Text,
Summary, How to Run, Jinx), fetched 2026-08-25.

**Current ability text (quote):**
> "If you are executed, all but 3 players die. After a 10 to 1 countdown, the player with the most players pointing at them, dies."

`characters.json:1766` matches verbatim. No drift.

**Summary / clarifications (quotes):**
- "If the Boomdandy is executed, the Storyteller kills other players, one at a time, until only three are left alive."
- "The Demon will be one of the remaining three players (otherwise, the game would be over)."
- "Even dead players who have no vote token may point."
- "Players may change who they are pointing at up until the countdown ends, at which point their decision is final."
- "The Boomdandy only explodes due to an execution. Deaths by other means, such as via a Golem or a Psychopath, don't count."
- **"If the Boomdandy is executed but doesn't die (due to a Devil's Advocate etc.), they still explode."**
- "If it is a tie, the day ends (and evil probably wins due to the Demon killing that night)."
- If a character cannot die, "the Storyteller may rule that four players remain alive".

**How to Run (quotes):**
> "If the Boomdandy is executed, declare that the Boomdandy has exploded. Enter the circle, put your hand out towards the Boomdandy, and rotate around the circle…"
> "When your hand reaches a living player, say 'You live' or say 'You die' as that player dies."
> After the countdown, "ask all players to immediately freeze…while you count the number of players who are pointing at each player."

So the sequence is: **execution → (Boomdandy may or may not die) → ST chooses,
one at a time, who dies until 3 players remain alive (the Demon must be one of
them) → ST counts 10→1 aloud → everyone freezes pointing → the most-pointed-at
player dies → the day ends.**

**Jinx (quote):**
- Plague Doctor — "If the Storyteller would gain the Boomdandy ability, a player becomes the Boomdandy."

**Night order.** None — the Boomdandy never wakes. Correctly absent from both
lists in `night_and_jinxes.json`.

## What the app does today

- `characters.json:1766` — correct ability text, `setup:false`, **no reminder
  tokens**, no first/other night reminders.
- `night_guide.json` — **no `boomdandy` entry at all** (verified by key lookup).
- `night_and_jinxes.json` — no Boomdandy jinx entry; not in either night list
  (correct for the night lists).
- **Zero references in `engine/src/main/kotlin` and `app/src/main/java`.**

**Storyteller experience today:** nothing. The Boomdandy is a token you can put
on a seat and an ability string in the Reference tab. When you execute them the
app kills exactly one player (`DayScreen.kt:111-114` → `viewModel.kill(id,
EXECUTION)`, or `GameShell.kt:604-610` from the dusk guard, or
`SeatSheet.kt:272-277`) and offers no hint that 3-player-remaining carnage is
supposed to follow. You must remember the whole procedure, kill each player by
hand through their seat sheet, count "10…1" out loud yourself (the built-in
timer only offers 1m/2m/5m presets, `Timer.kt:88-97`), tally the pointing by
eye, and kill the winner by hand. Nothing prevents you from killing the Demon
along the way, and nothing knows the Boomdandy explodes even when the execution
does not kill them.

## Defects and gaps

1. **P0 · Executing the Boomdandy has no consequence in the app.**
   Rules: all but 3 players die, then a pointed-at player dies. App: exactly one
   death is recorded. `DayScreen.kt:111-114` and `GameShell.kt:604-610` call
   `viewModel.kill` directly and never consult
   `StatusEffects.deathNotes` (`StatusEffects.kt:52-129`) — which has no
   `boomdandy` case anyway. Repro: put the Boomdandy on the block, tap
   **Execute** — one death, no prompt, no banner, game continues as if a normal
   Minion died.

2. **P0 · "Executed but doesn't die" cannot be expressed.**
   Rules: a Devil's-Advocate-protected Boomdandy *still explodes*. The app models
   execution only as `kill(..., DeathCause.EXECUTION)`; if the ST answers "Death
   prevented" in the protection dialog (`SeatSheet.kt:284-300`) **nothing at all
   is recorded** — no `DeathRecord`, no "was executed today" fact. So neither the
   Boomdandy explosion, nor the Saint, nor the Goblin can key off "was executed".
   This is a cross-cutting engine gap (see the Goblin file for the same issue).
   Repro: mark the Boomdandy "Survives execution" (Devil's Advocate), execute →
   the app records nothing and offers nothing.

3. **P1 · No guided "kill down to 3" tool.**
   The ST must open each seat sheet and press "Other death" repeatedly, with no
   running count of how many are still alive and no guard that the Demon must
   survive. Repro: 12-player game, Boomdandy executed → 8 manual kills through 8
   bottom sheets while the table waits.

4. **P1 · No 10→1 countdown.** `Timer.kt:88-97` offers only "1m", "2m", "5m"
   presets, and it is a *silent* count-down chip, not an audible/visible 10→1.

5. **P1 · No pointing tally.** The ST must count fingers and remember the
   winner. Nothing records who pointed at whom, nothing detects the tie ("the
   day ends") and nothing enforces "dead players may point too".

6. **P1 · No day-end enforcement.** After the Boomdandy resolves, the day ends
   immediately (no further nominations). The app happily lets more nominations
   be recorded (`DayScreen.kt:126-255`).

7. **P2 · No `night_guide.json` entry.** Every other complex Minion has ST
   prose available in-app; the Boomdandy's procedure lives only on the wiki. But
   note the guide is only surfaced from *night* steps (`NightScreen.kt:792`), so
   the Boomdandy needs a **day**-side home for its instructions.

8. **P2 · Missing reminder tokens.** No token for "exploded / resolved", so a
   half-finished explosion (phone locked, undo) leaves no trace.

9. **P2 · Missing Plague Doctor jinx** in `night_and_jinxes.json`.

10. **P3 · `WinCheck` is not told the game state changed drastically.**
    After the explosion, `alive.size <= 2` may be reached in one step; the
    advisory dialog (`GameShell.kt:504-519`) will fire mid-procedure and can be
    confusing. It should be suppressed until the explosion is resolved.

## Proposed behaviour (spec)

The Boomdandy is a **day/execution** character. The engine needs an
execution-resolution hook, not a night step.

### New engine concepts (shared with Goblin / Fearmonger / Saint)

```kotlin
/** An execution happened, whether or not the executed player died. */
data class ExecutionRecord(
    val day: Int,
    val playerId: Long,
    val died: Boolean,
    val nominatorId: Long?,     // for the Fearmonger
)
val executions: List<ExecutionRecord> = emptyList()
```

and a single funnel `GameActions.execute(state, playerId, nominatorId, lookup)`
that (a) appends the `ExecutionRecord`, (b) applies the death unless the ST said
it was prevented, (c) returns a list of `ExecutionConsequence`s for the UI.
Every "Execute" button (`DayScreen.kt:111`, `DayScreen.kt:350-357`,
`GameShell.kt:604`, `SeatSheet.kt:272`) must route through it.

### Boomdandy consequence

- **trigger:** `ExecutionRecord.playerId` is a seat whose `characterId ==
  "boomdandy"`, regardless of `died`, **and** the Boomdandy is not
  drunk/poisoned (`StatusEffects.isImpaired`). *Rule note: the wiki does not
  spell out the drunk/poisoned case; standard BOTC rules make a poisoned
  Minion's ability fail, so gate on `!isImpaired` and say so in the UI so the ST
  can override.*
- **immediate:** open a full-screen **Boomdandy explosion** flow (blocking, all
  steps undoable):
  1. Banner: `"{name} was the BOOMDANDY — declare that they have exploded."`
  2. **Step 1 — kill down to 3.** A list of every alive player (excluding the
     three that must remain) in seat order starting from the Boomdandy and
     rotating, matching the physical "put your hand out and rotate" method.
     Each row is a single tap = "You die" (records
     `kill(..., DeathCause.STORYTELLER)`); tapping again undoes. A live counter
     shows `N alive — kill until 3 remain`. The Demon's seat is highlighted with
     `Demon — must survive` and its kill button is disabled by default
     (long-press to override, for the "4 remain" ruling). When a player who
     cannot die is encountered, show the wiki's escape hatch:
     `"{name} can't die — you may rule that four players remain alive."`
  3. **Step 2 — countdown.** A big `10 9 8 …` countdown button with 1-second
     ticks and a final `FREEZE` screen. (Reuse `Timer.kt` machinery with a
     `countFrom = 10` mode and per-second rendering.)
  4. **Step 3 — tally.** Chips for every player (alive *and* dead — "even dead
     players who have no vote token may point"), each with a +/- counter for how
     many fingers point at them. Show the current leader; if two or more tie,
     show `TIE — no one dies, the day ends now.`
  5. **Step 4 — resolve.** One button `"{leader} dies"` (or `"Tie — end the
     day"`), which kills with `DeathCause.STORYTELLER`, marks the day ended and
     advances the UI to the Dusk prompt.
- **deferred:** set a `dayEnded` flag for the current cycle so `DayScreen`
  disables the nomination card with `"The Boomdandy exploded — the day is over."`
- **expiry:** the whole flow is one atomic day event; nothing persists past dusk
  except the deaths. Place `PlacedReminder("boomdandy", "Exploded")` on the
  Boomdandy seat so a resumed session knows the flow already ran; it never
  expires.
- **information / visibility:** nothing is shown to players privately; the whole
  thing is public. The Demon must not be told to survive — the ST just must not
  kill them.
- **interactions to handle:**
  - Devil's Advocate / any "survives execution" → `died = false`, explosion
    still runs (this is the headline rule).
  - Golem, Psychopath, Witch, Demon kill → **no** explosion (execution only).
  - `WinCheck.check` must be suppressed while the explosion flow is open, and
    re-evaluated once, at the end.
  - Plague Doctor jinx: "If the Storyteller would gain the Boomdandy ability, a
    player becomes the Boomdandy." — add to jinx data; no engine work beyond a
    prompt to assign the character.

### UI text

- Execution confirm: `{name} is the Boomdandy — executing them makes all but 3 players die. Run the explosion?`
- Step 1: `Rotate from {Boomdandy}: tap "dies" for each player until 3 are left. The Demon must be one of the 3.`
- Step 2: `Count 10 to 1 out loud. Players point at whoever they want dead and may change until you reach 1.`
- Step 3: `Freeze! Count the fingers pointed at each player.`
- Step 4 (tie): `Tie — nobody dies. The day ends now.`

### Data changes

- `characters.json:1766`: add `"reminders": ["Exploded"]`.
- `night_and_jinxes.json`: add the Plague Doctor jinx.
- `night_guide.json`: add a `boomdandy` entry — but since the guide is only read
  from night steps today, either (a) extend `NightGuide` with a `day` key and
  surface it from a new **Day briefing** panel, or (b) put the prose directly in
  the explosion flow strings. (b) is simpler and preferred.

## Tests to add

1. *Given* an 11-player game with a Boomdandy alive, *when*
   `GameActions.execute(boomdandySeat, died = true)`, *then* the returned
   consequences contain `BoomdandyExplosion(playersToKeepAlive = 3)`.
2. *Given* the Boomdandy is marked "Survives execution" (Devil's Advocate) and is
   executed with `died = false`, *then* the explosion consequence is **still**
   returned and `state.executions` records `died = false`.
3. *Given* the Boomdandy dies to a Demon kill at night, *then* no explosion
   consequence is produced.
4. *Given* the Boomdandy is poisoned, *when* executed, *then* no explosion
   consequence is produced (and the advisory text explains the ST may override).
5. *Given* an explosion flow that has killed down to 3 alive, *then* the alive
   set still contains the Demon, and attempting to kill the Demon through the
   flow is rejected without an explicit override flag.
6. *Given* the tally has a two-way tie, *then* the resolve step produces no
   death and sets `dayEnded` for the current cycle.
7. *Given* `dayEnded` is set, *then* `GameActions.recordNomination` for that day
   is refused (or the UI disables it — assert whichever layer owns it).
