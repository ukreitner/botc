# Pacifist (pacifist) — Bad Moon Rising Townsfolk

## Official rules (sources)

Sources:
- https://wiki.bloodontheclocktower.com/Pacifist
- https://wiki.bloodontheclocktower.com/Glossary ("Execution", "Dead", "Drunk")

**Current ability text (matches `characters.json`):**

> "Executed good players might not die."

**How to run (wiki, quoted):**

> "If a good character is executed, declare either that they die or they remain alive. (Do not say
> why.) Then, begin the night phase. (Whether the player lived or died, this was the one execution
> for the day.)"

**Wiki notes:**

- The **Storyteller** decides, every time a good player is executed, whether the ability fires. The
  group "learns only what happened, not why".
- "An execution that results in the player remaining alive still counts as that day's execution —
  no further nominations occur."
- Frequency guidance: "The Storyteller should trigger the ability approximately once per game,
  though frequency can vary… Occasionally, the ability may never trigger to maintain suspicion."

**Examples (wiki):** the Innkeeper is executed but survives; a game where five good players were
executed over seven days and it never fired; the Lunatic survives execution (a drunk Pacifist later
confirms), the Professor dies the next day, a Bishop dies **by exile — not an execution — so the
Pacifist does not apply**, and the Pacifist survives their own execution.

Derived rules that matter:

- **Good players only** — judged by *alignment*, not team, so a good player who is the Drunk,
  Lunatic, Recluse or a turned/flipped seat all qualify; a Recluse **registering as evil** may be
  excluded at ST discretion.
- **The Pacifist themself qualifies** (wiki example).
- **Travellers are never executed** (Glossary: "The group decision to kill a player other than a
  Traveller"), so exiles never involve the Pacifist.
- **A drunk or poisoned Pacifist** has no ability, so executed good players die normally. (A drunk
  Pacifist may still *appear* to work if the ST wants — but the correct default is: no save.)
- **A dead Pacifist** has no ability.
- **Downstream consequences of a save the app must know about:**
  - the day's one execution has happened → no more nominations, dusk follows;
  - **nobody died today** → the **Zombuul** ("Each night\*, if no-one died today, choose a player:
    they die") becomes active, and the **Godfather**'s "if an Outsider died today" does not fire;
  - the **Undertaker** learns nothing tonight (no character died by execution);
  - the **Minstrel** does not trigger (but the Pacifist only saves *good* players, so this can only
    matter via a different save).
- **Jinxes:** none for the Pacifist.

## What the app does today

**Nothing.** The Pacifist is inert data.

- `characters.json` — ability text correct, `reminders: []`, no night reminders. **Correct data.**
- `night_and_jinxes.json` — absent from both night orders (correct; the Pacifist never wakes) and
  from the jinx list (correct).
- `night_guide.json` — **no entry** (`null`).
- `engine/src` — **zero references**. `grep -rn "pacifist" engine/src app/src` finds only
  `FullGamePlaytestTest.kt:1293`, where it is used as a demon *bluff*.
- Execution is a plain kill with no outcome choice:
  - `DayScreen.kt:111-114` — the "On the block" banner's **Execute** button calls
    `viewModel.kill(onBlock.id, DeathCause.EXECUTION)` directly.
  - `DayScreen.kt:350-357` — the per-nomination **Execute** button, same.
  - `GameShell.kt:599-604` — the dusk guard's **"Execute & begin night"**, same.
  - `viewModel.kill` → `GameActions.kill` (`GameActions.kt:136-156`) → the player is dead.
    No `deathNotes`, no confirmation, no alternative outcome.
  - The only path with any protection awareness is the seat sheet (`SeatSheet.kt:239-307`), which
    the ST is unlikely to use for an execution — and `deathNotes` has no Pacifist entry anyway.

Storyteller experience: there is no moment in the app at which "this player is good and about to be
executed — do you want the Pacifist to save them?" is ever asked. The ST must remember the Pacifist
is in play, remember each execution to consider it, and then simulate the save by *not pressing
Execute* — which leaves the app believing the day had no execution at all, corrupting the Zombuul,
Undertaker and nomination state.

## Defects and gaps

1. **P0 · There is no "executed but does not die" outcome anywhere in the app.**
   Rules: "declare either that they die or they remain alive". App: execution is a single
   irreversible kill (`DayScreen.kt:111-114`, `:350-357`, `GameShell.kt:599-604`). Repro: put a
   good player on the block with a Pacifist in play — the only buttons are **Execute** and, at
   dusk, **"No execution"** (`GameShell.kt:608-612`), which is a *different* game state (no
   execution happened at all). There is no way to record the correct outcome.

2. **P0 · The engine cannot represent "an execution happened but nobody died".**
   The sole record of an execution is a `DeathRecord(cause = EXECUTION)` (`GameState.kt:77-90`).
   With a Pacifist save there is no death, therefore no record, therefore:
   - `GameActions.aboutToDie` (`:296-306`) still reports the player on the block, so
     `GameShell.kt:141-146` will nag "…is on the block and hasn't been executed" at dusk;
   - the Zombuul's "if no-one died today" cannot be distinguished from "no execution today";
   - the Undertaker's night info (`InfoCalc.undertaker`, `InfoCalc.kt:281+`) reads the death list
     and cannot know an execution occurred;
   - the log (`GameExtras.kt:51-58`) shows nothing happened that day.

3. **P0 · The Pacifist is never brought to the Storyteller's attention.**
   Rules: the ST must make a live decision at each good execution. App: nothing prompts. The
   character is invisible to `StatusEffects.deathNotes` (`StatusEffects.kt:52-129`) — contrast the
   Fool (`:75-77`), Sailor (`:73`), Tea Lady (`:81-90`) and Devil's Advocate (`:68`), which all get
   a line.

4. **P1 · No day-start briefing that the Pacifist is in play and unused.**
   The ST has no reminder that they have this lever, nor any record of how often they have used it
   ("approximately once per game" is explicit guidance the app could support).

5. **P1 · Alignment, not team, is the test — and the app has the answer.**
   `Player.isEvil(lookup)` (`GameState.kt:49-52`) already folds in `alignmentFlipped`, so the app
   can decide "is this an executed **good** player?" perfectly and never asks.

6. **P2 · The Pacifist's own impairment is not considered.**
   A drunk or poisoned Pacifist should not save. `StatusEffects.isImpaired` exists and is unused
   here.

7. **P2 · No record of a save for later characters.** The wiki example has a drunk Pacifist
   "confirming" a survival later; the log should carry "Day 3: {name} was executed and survived".

8. **P3 · No night guide entry**, so the ST has no in-app statement of the rule ("do not say why",
   "it still counts as today's execution").

## Proposed behaviour (spec)

The Pacifist has no night step. Everything happens at the **execution** moment.

### Engine: make the execution a first-class, outcome-bearing event

```kotlin
@Serializable
enum class ExecutionOutcome { DIED, SURVIVED, NO_EXECUTION }

@Serializable
data class ExecutionRecord(
    val day: Int,
    val playerId: Long,
    val outcome: ExecutionOutcome,
    /** Character id credited with the save: "pacifist", "sailor", "tealady",
     *  "devilsadvocate", "fool", "" for a bare Storyteller decision. */
    val preventedBy: String = "",
)

// on GameState:
val executions: List<ExecutionRecord> = emptyList()
```

`GameActions.execute(state, playerId, outcome, preventedBy, lookup)` records the `ExecutionRecord`
and, for `DIED`, also performs `kill(..., DeathCause.EXECUTION)`. **Every** execution entry point
routes through it: `DayScreen.kt:111-114`, `DayScreen.kt:350-357`, `GameShell.kt:599-604`.

Downstream consumers gain a correct answer:
- `GameActions.aboutToDie` / the dusk guard treat a `SURVIVED` record as "the day's execution has
  happened" — no nag, no further nominations;
- Zombuul: "no-one died today" = no death record for `state.cycle`, regardless of executions;
- Undertaker: "died by execution today" = an `ExecutionRecord` with `outcome == DIED`;
- Minstrel: only `DIED` can trigger it;
- log & reveal: render "executed, survived".

### The execution dialog (shared with Sailor / Tea Lady / Devil's Advocate / Fool)

- **when:** the ST taps Execute on any player.
- **the app computes and shows, before any state changes:**
  - the nominee's alignment (`isEvil(lookup)`), alive-ness, and every applicable
    `deathNotes(..., cause = EXECUTION)` entry;
  - a **Pacifist line** if a Pacifist is in play, alive and not impaired **and** the nominee is
    good: *"Pacifist ({name}) is in play — you may declare that {nominee} survives. Do not say why.
    Either way this is today's execution."* Plus, from the log,
    *"You have used it {n} times this game."*
- **buttons:** **"{name} dies"** (default) · **"Executed, but survives"** (with a small
  attribution picker pre-filled from the applicable notes: Pacifist / Sailor / Tea Lady / Devil's
  Advocate / Fool / Storyteller) · **Cancel**.
- **immediate effects:**
  - `DIED` → `kill(cause = EXECUTION)` and all the usual on-death triggers (Minstrel, Scarlet
    Woman, Mastermind, Godfather-Outsider, Zombuul first death…).
  - `SURVIVED` → no death; the day's execution is recorded; **no reminder token is placed** (the
    Pacifist has none, correctly — `characters.json` `reminders: []`).
- **deferred effects:** at dawn/day start the briefing should carry
  *"Nobody died last night"* + the previous day's *"{name} was executed and survived"* only in the
  ST's private log, never as an announcement — the app must not generate a public statement about
  the reason.
- **expiry:** nothing to expire.
- **information / visibility:** the town is told only "executed, and they die" or "executed, and
  they remain alive". Nothing is shown to the Demon or Minions.
- **day-time inputs:** none beyond the outcome choice.
- **interactions:**
  - **Ordering with other saves.** If the nominee is *also* Devil's-Advocate-protected, Tea-Lady
    protected, a sober Sailor, or an unspent Fool, the survival is *forced*, not optional — the
    dialog should preselect "Executed, but survives" and attribute it to the forced source, keeping
    the Pacifist unused. Only offer the Pacifist as the reason when no forced save applies.
  - **Recluse** executed while registering as evil — offer *"treat as evil (Pacifist cannot save)"*.
  - **Mastermind day** (`GameState.mastermindDayActive`, `GameState.kt:111`): a survived execution
    is not "a player is executed" for the Mastermind's "their team loses" clause — flag this to the
    ST rather than deciding.
  - **Leviathan** ("if more than 1 good player is executed, evil wins"): a *survived* execution
    still counts as an execution of a good player. Flag it.
  - **Virgin / Golem / Witch / Fearmonger** deaths are not executions and must not use this path.

### UI text

- Dialog title: **"Execute {name}?"**
- Alignment line: **"{name} is good"** / **"{name} is evil"**.
- Pacifist line: **"Pacifist ({pacifist name}) — you may declare they survive. Do not say why.
  Either way, this is today's execution. Used {n}× so far."**
- If the Pacifist is impaired or dead: **"Pacifist ({name}) is drunk/poisoned/dead — no save."**
- Buttons: **"{name} dies"** · **"Executed, but survives"** · **"Cancel"**.
- After a survival: **"Announce: '{name} is executed… and remains alive.' Say nothing else. Move to
  dusk — no more nominations today."**
- Day-start briefing, when a Pacifist is in play: **"Pacifist in play — executed good players might
  not die. Used {n}× this game."**

### Data changes

- `characters.json` — none.
- `night_guide.json` — add a `pacifist` entry (needs a new `"day"` section, as for the Minstrel and
  Gossip) carrying the wiki How-to-Run sentence verbatim.
- `night_and_jinxes.json` — none.

## Tests to add

1. `a survived execution still counts as the day's execution`
   Given a good player on the block on day 2. When `execute(playerId, SURVIVED, "pacifist")`.
   Then the player is alive, `state.executions` has one record for day 2 with `outcome == SURVIVED`,
   and `aboutToDie(state)` no longer blocks the dusk guard.

2. `a survived execution creates no death record`
   Continuing (1): `state.deaths` is empty, so the Zombuul's "no-one died today" is satisfied.

3. `the undertaker learns nothing after a survived execution`
   Given (1), when computing `InfoCalc.compute(..., "undertaker", …)` on night 3, the result says
   nobody died by execution.

4. `a survived execution of a minion does not trigger the minstrel`
   Given a Minstrel in play and a Minion executed with `SURVIVED`. Then no global drunk effect.

5. `the pacifist offer only appears for good, executable targets`
   `ExecutionOptions(state, lookup, nomineeId)` returns `pacifistOffer == true` for a good nominee
   with an alive sober Pacifist; `false` when the nominee is evil, when the Pacifist is dead, when
   the Pacifist is impaired, and when the Pacifist is not in play.

6. `the pacifist can save themselves`
   Given the Pacifist is the nominee. Then `pacifistOffer == true` (wiki example).

7. `an exile is never a pacifist case`
   Given a good Traveller exiled. Then no `ExecutionRecord` is produced and `pacifistOffer` is not
   consulted.

8. `a forced save takes precedence over the pacifist`
   Given the nominee holds `("devilsadvocate","Survives execution")`. Then the dialog model reports
   `forcedSurvival = "devilsadvocate"` and `pacifistOffer == false`.

9. `execution outcomes survive serialization`
   Round-trip a `GameState` with `executions` through the persistence layer.
