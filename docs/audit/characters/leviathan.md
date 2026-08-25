# Leviathan (leviathan) — exp demon

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Leviathan> (fetched 2026-08-25).

Current ability text (matches `characters.json`):

> "If more than 1 good player is executed, evil wins. All players know you are in play.
> After day 5, evil wins."

Summary (quoted):

- "The Leviathan doesn't kill."
- "All players know the Leviathan is in play, even if the Leviathan is created mid-game."
- "All types of execution count, even if the player doesn't die" — e.g. a Virgin's
  execution of the nominator, a Mutant reveal, "An executed player who lives due to the
  Pacifist is still executed."
- Alignment is checked "at the time they were executed".
- There is "no requirement that the Leviathan be alive" for the win conditions.
- "Any number of evil players may be executed."

How to Run (quoted):

- **Day 1:** "Immediately after dawn on the first day, declare that the Leviathan is in
  play. Mark the Leviathan with the **DAY 1** reminder."
- **Each following day:** "At the beginning of each following day, mark the Leviathan with
  **DAY 2**, then **DAY 3**, then **DAY 4**, then **DAY 5** reminders."
- **Day 5 end:** "If a day ends and the Leviathan is marked with the **DAY 5** reminder,
  declare that evil wins." (i.e. evil wins at the end of day 5, as night 6 would begin.)
- **Good player executed:** "If a good player is executed, mark them with the **GOOD
  PLAYER EXECUTED** reminder. If a good player is executed and a player is already marked
  with the **GOOD PLAYER EXECUTED** reminder, declare that evil wins."

Examples (quoted):

- "Day 1: Monk executed. Day 2: Courtier executed. Result: evil wins."
- "Day 2: Scarlet Woman executed. Day 3: Poisoner executed. Day 5: Soldier executed.
  Result: evil wins." (Two evil executions are harmless; the day-5 clock ends it.)

Jinxes — **thirteen**, all quoted from the wiki. Note that four of them give the Leviathan
a **nightly choose-a-player action** it otherwise does not have:

- **Banshee:** "Each night*, the Leviathan chooses an alive good player (different to
  previous nights): a chosen Banshee dies & gains their ability."
- **Exorcist:** "If the Leviathan nominates and executes the Exorcist-chosen player, good
  wins."
- **Farmer:** "Each night*, the Leviathan chooses an alive good player (different to
  previous nights): a chosen Farmer uses their ability but does not die."
- **Grandmother:** "If the Leviathan is in play and the Grandchild dies by execution, evil
  wins."
- **Hatter:** "The Leviathan cannot enter play after day 5."
- **Innkeeper:** "If the Leviathan nominates and executes an Innkeeper-protected player,
  good wins."
- **King:** "If the Leviathan is in play, and at least 1 player is dead, the King learns an
  alive character each night."
- **Mayor:** "If the Leviathan and the Mayor are alive on day 5 & no execution occurs, good
  wins."
- **Monk:** "If the Leviathan nominates and executes the Monk-protected player, good wins."
- **Pit-Hag:** "The Leviathan cannot enter play after day 5."
- **Ravenkeeper:** "Each night*, the Leviathan chooses an alive player (different to
  previous nights): a chosen Ravenkeeper uses their ability but does not die."
- **Sage:** "Each night*, the Leviathan chooses an alive good player (different to previous
  nights): a chosen Sage uses their ability but does not die."
- **Soldier:** "If the Leviathan nominates and executes the Soldier, good wins."

Night order: `night_and_jinxes.json:368` on the **first night, immediately after `DAWN`**
(line 367) — which matches "immediately after dawn on the first day, declare that the
Leviathan is in play"; and `night_and_jinxes.json:427` on other nights. Positions are
correct.

## What the app does today

Data:

- `characters.json:1997` — ability text matches the wiki. `setup: false`.
  `reminders: ["Day 1","Day 2","Day 3","Day 4","Day 5","Good Player Executed"]`.
  `firstNightReminder` = "Announce that the Leviathan is in play."
  `otherNightReminder` = "Change the Leviathan reminder token to the relevant day. You may
  announce that the Leviathan is in play."
- `night_guide.json:1563` — accurate prose for both nights, plus two "Public announce"
  message cards ("THE LEVIATHAN IS IN PLAY. IT IS DAY 1." / "…IT IS DAY…").
- `night_and_jinxes.json:189-207` — only **four** jinxes, three of which have text that
  does not match the current wiki:
  - mayor: app "If Leviathan is in play and no execution occurs on day 5, good wins."
    vs wiki "If the Leviathan **and the Mayor are alive** on day 5 & no execution occurs,
    good wins."
  - ravenkeeper: app "…if the Ravenkeeper **dies by execution**, they wake that night to
    use their ability." vs wiki "Each night*, the Leviathan **chooses an alive player**
    (different to previous nights): a chosen Ravenkeeper uses their ability but does not
    die."
  - sage: same shape of drift as ravenkeeper.
  - farmer: app "…if a Farmer dies by execution, a good player becomes a Farmer that
    night." vs wiki "Each night*, the Leviathan chooses an alive good player (different to
    previous nights): a chosen Farmer uses their ability but does not die."
  Missing entirely: Banshee, Exorcist, Grandmother, Hatter, Innkeeper, King, Monk, Pit-Hag,
  Soldier.

Night:

- `NightOrder.build` emits the Leviathan step in both lists, using the reminder text as the
  detail — **works** (position and wording are right).
- `NightScreen.QuickResolutions` (`NightScreen.kt:462-525`) has no `leviathan` branch, so
  it hits `else ->` at line 518 and — because `team == Team.DEMON && holder.alive` —
  renders **`DemonKillPanel`**: *"Demon kill — who did <name> choose?"* with a working
  "<target> dies" button (`NightScreen.kt:534-638`). This is the exact defect class the
  user reported for the Pukka: the app offers a kill to a Demon that never kills.
- On the **first night**, the Leviathan step sits after `DAWN` in the list, but
  `GameShell.requestPhaseAdvance` (`GameShell.kt:147-161`) refuses to advance to day until
  **every** step — including that post-dawn one — is ticked, so the ST is nagged about a
  step that is meant to happen after dawn.

Day / bookkeeping:

- Nothing advances the day counter. The ST must open the tray, pick "Day 2", tap a seat —
  and because `placeExclusiveReminder` only clears the **same** `(sourceId,label)` pair
  (`GameActions.kt:194-201`), "Day 1" stays where it is. After five days the Leviathan seat
  carries all five tokens.
- Nothing marks or counts executed good players. `GOOD PLAYER EXECUTED` is one label with
  one copy, so the tray's exclusive placement (`NightScreen.kt:318-341`) *moves* it — the
  ST can never have two marks and the app never notices the second execution.
- Executions that do **not** kill (Virgin's nominator, Mutant reveal, Pacifist save) are
  never recorded at all: the only execution record is a `DeathRecord` created by
  `viewModel.kill(..., DeathCause.EXECUTION)` (`DayScreen.kt:112`, `:350-357`;
  `GameShell.kt:599-604`). So even a bespoke Leviathan counter built on `state.deaths`
  would undercount.
- `WinCheck.check` (`WinCheck.kt:18-101`) has no Leviathan branch: no day-5 ending, no
  two-good-executed ending, no Mayor-jinx exception. The only endings that can fire are
  the generic "all Demons dead ⇒ good wins" and "≤2 alive ⇒ evil wins".
- Nothing forces the day-1 public announcement; it is a card the ST has to remember to
  find.

Works today: night-order placement, the two announce cards, the ability text, the six
reminder labels existing at all.

## Defects and gaps

1. **P0 · The Leviathan is offered a night kill.**
   "The Leviathan doesn't kill." The generic demon fallthrough gives it a full kill panel
   and a working kill button. `NightScreen.kt:518-523` → `:534-638`.
   *Repro:* Leviathan script, night 2, tap the Leviathan row → "Demon kill — who did X
   choose?"

2. **P0 · "After day 5, evil wins" is never detected.**
   No day counter, no ending. `WinCheck.kt:18-101` has no Leviathan case.
   *Repro:* play to the end of day 5 and press Dusk — the app happily starts night 6.

3. **P0 · "More than 1 good player executed ⇒ evil wins" is never detected.**
   No count is kept and no advisory fires.

4. **P0 · Executions that do not kill are not recorded.**
   Virgin, Mutant reveal and Pacifist-saved executions all count for the Leviathan
   (explicit on the wiki) but produce no record anywhere in the app, because execution is
   modelled only as a death (`GameState.DeathCause.EXECUTION`, `DayScreen.kt:350-357`).
   This is a cross-cutting modelling gap that Leviathan makes unavoidable.

5. **P1 · The day counter is fully manual and accumulates.**
   `placeExclusiveReminder` only removes the identical label (`GameActions.kt:194-201`),
   so Day 1…Day 5 pile up on the seat. There is no "one of this group" concept.

6. **P1 · The day-1 public announcement is not enforced.**
   "All players know you are in play" is a hard rule, and the announcement must happen
   *immediately after dawn on day 1*. Today it is an optional card the ST may never open.

7. **P1 · No day-start briefing.**
   Every Leviathan day needs "It is day N of 5" and "k good players have been executed" in
   front of the ST. The app has no day-start surface at all.

8. **P1 · Nine jinxes missing, four with drifted text.**
   `night_and_jinxes.json:189-207`. The Ravenkeeper/Sage/Farmer/Banshee jinxes in
   particular are not cosmetic: they give the Leviathan a **nightly choose-an-alive-player
   action with a "different to previous nights" constraint**, which the app cannot express
   at all (no per-night choice history).

9. **P1 · The four "Leviathan nominates and executes X ⇒ good wins" jinxes are
   untracked.** Exorcist, Innkeeper, Monk, Soldier. The app records nominators and
   nominees, so it can detect "the nominator was the Leviathan and the nominee was
   Monk-protected/Soldier/Innkeeper-protected/Exorcist-chosen" precisely — but it does not.

10. **P1 · The Mayor jinx is stated wrongly and unimplemented.**
    The app's text omits "and the Mayor are alive", which turns a conditional good win into
    an unconditional one. `night_and_jinxes.json:189-192`.

11. **P2 · The Grandmother jinx is missing.**
    "If the Leviathan is in play and the Grandchild dies by execution, evil wins." The app
    already tracks the `Grandchild` reminder (`StatusEffects.kt:122-127`), so this is
    derivable.

12. **P2 · The King jinx is missing.**
    "If the Leviathan is in play, and at least 1 player is dead, the King learns an alive
    character each night." `InfoCalc.king` (`InfoCalc.kt:397-406`) gates on
    `dead >= alive` and would wrongly say "the King doesn't wake".

13. **P2 · First-night step ordering vs the night-completion guard.**
    The Leviathan's first-night row is after `DAWN` by design, but
    `GameShell.kt:147-161` demands it be ticked before dawn. Either exclude post-DAWN steps
    from the guard or move them into a "day start" list.

14. **P2 · `alive.size <= 2 ⇒ evil wins` can pre-empt the real ending.**
    In a Leviathan game the generic advisory may fire on day 4 while the Leviathan-specific
    rules are the ones that matter; the reason text will be misleading.

15. **P3 · "Good Player Executed" is a single exclusive token.**
    It needs to exist twice (or be a counter), because the rule is about the *second*
    marked player.

## Proposed behaviour (spec)

### Night action (structured)

- **when:** first night (a "day 1 announcement" step placed after `DAWN`) and every other
  night. Wake condition: **none** — the Leviathan never wakes and the players are never
  woken **unless** a Banshee/Farmer/Ravenkeeper/Sage jinx applies.
- **targets:**
  - Default: **none**. The step must expose **no kill control at all**; add an explicit
    line *"The Leviathan doesn't kill. Nobody dies tonight."*
  - Jinxed variant (only when Banshee, Farmer, Ravenkeeper or Sage is on the script):
    exactly 1 target, **alive**, **different from every previous night's Leviathan choice**,
    and **good** for Banshee/Farmer/Sage (any alive player for Ravenkeeper). The picker
    must grey out previously chosen players and say why.
    Effects: chosen Banshee → dies and gains their ability; chosen Farmer/Sage/Ravenkeeper
    → uses their ability **but does not die** (so the app must run the corresponding
    `InfoCalc` result without a kill).
- **immediate effects:** none by default.
- **deferred effects (this is where the character lives):**
  - **On NIGHT→DAY (`advancePhase`)**, when a Leviathan is in play:
    1. move the day marker: remove every `leviathan:Day *` token from every seat and place
       `leviathan:Day <cycle>` on the Leviathan's seat (or, if the Leviathan is not a seated
       player because it arrived mid-game, on a game-level counter);
    2. push a **day-start briefing**: *"Day N of 5. The Leviathan is in play. Good players
       executed: k of 2."*
    3. on day 1 only, force the public announcement card:
       **"THE LEVIATHAN IS IN PLAY."** — a modal the ST must dismiss, not a chip.
  - **On any execution being recorded**, whether or not the player dies:
    if the executed player was **good at the time of execution**, mark them
    `leviathan:Good Player Executed` and increment the count. If the count reaches **2**,
    raise a win advisory: **evil wins**.
  - **On DAY→NIGHT (`advancePhase`) at the end of day 5**, if a Leviathan is in play:
    raise a win advisory **evil wins** *before* advancing — unless the **Mayor jinx**
    applies (Leviathan and Mayor both alive on day 5 and no execution occurred today), in
    which case the advisory is **good wins**.
  - **On a nomination being recorded** where the nominator is the Leviathan: if the nominee
    is Monk-`Safe`, Innkeeper-`Protected`, Exorcist-`Chosen` or the Soldier, show
    **"Jinx: if this execution goes ahead, GOOD WINS."** at the moment of nomination and
    again at the execution button.
  - **Grandmother jinx:** if a player carrying the `Grandchild` reminder is executed while
    a Leviathan is in play → advisory **evil wins**.
- **expiry:** `leviathan:Day 1..5` form an **exclusive group** (a new concept: at most one
  member of the group anywhere in the grimoire) and never expire on their own — they are
  replaced at each day start. `leviathan:Good Player Executed` never expires.
- **information:** none, except the King jinx (below).
- **visibility:** the Leviathan's presence is **public** from day 1. The app should also
  show a persistent banner during a Leviathan game, in the same slot as the Mastermind-day
  banner (`GameShell.kt:520-537`): **"LEVIATHAN — DAY N OF 5 · GOOD EXECUTED: k/2"**.
- **day-time inputs the app must let the ST record:** the crucial one is **"an execution
  happened but nobody died"** (Virgin, Mutant reveal, Pacifist save). See "State" below.
- **interactions/jinxes:** all thirteen, as above. Also: `WinCheck`'s generic
  `alive.size <= 2` advisory should carry a Leviathan caution ("the Leviathan's own
  conditions are what usually end this game").

### State needed

The Leviathan forces execution to become a first-class event rather than a death cause:

```kotlin
@Serializable
data class ExecutionRecord(
    val day: Int,
    val playerId: Long,
    val nominatorId: Long?,
    val died: Boolean,                 // false for Virgin/Mutant/Pacifist cases
    val wasEvilAtExecution: Boolean,
    val characterIdAtExecution: String?,
)
// GameState.executions: List<ExecutionRecord>
```

Every Leviathan rule reads off this list; so do the Undertaker, Minstrel, Fearmonger and
the Mastermind day, all of which currently reconstruct executions from `deaths`.

Also add an **exclusive reminder group** concept so "Day 1…Day 5" (and similar counters)
replace one another:

```kotlin
fun placeExclusiveGroupReminder(state, playerId, reminder, groupLabels: Set<String>)
```

### UI text the step should display

- First night, after Dawn: **"Announce publicly: THE LEVIATHAN IS IN PLAY. It is day 1."**
- Other nights: **"The Leviathan doesn't kill. Nobody dies tonight."**
  + **"Tomorrow is day N of 5."**
- Jinxed nights: **"Jinx: choose an alive good player, different from every previous night
  — <already chosen: …>."**
- Day start: **"Day N of 5 · good players executed: k of 2."**
- At execution: **"<name> is GOOD — this is good execution #k. Two ends the game."**
- Dusk on day 5: **"Day 5 is over — evil wins."** (or the Mayor-jinx variant).

### Data changes

- `night_and_jinxes.json:189-207`: replace the four existing Leviathan jinxes with the
  current wiki text and add the nine missing ones (Banshee, Exorcist, Grandmother, Hatter,
  Innkeeper, King, Monk, Pit-Hag, Soldier).
- `night_guide.json:1563`: keep the two announce cards; change the `other` instructions to
  lead with "Nobody dies tonight — the Leviathan doesn't kill", and add the jinxed
  choose-a-player procedure.
- `characters.json:1997`: `otherNightReminder` should say "Nobody dies. Advance the day
  reminder." rather than only "Change the Leviathan reminder token to the relevant day."

## Tests to add

1. **No kill tool for the Leviathan.**
   *Given* a Leviathan game on night 2; *then* the Leviathan step exposes no kill action
   (engine-level predicate `demonKills("leviathan") == false`).

2. **Day marker advances and replaces.**
   *Given* a Leviathan game; *when* `advancePhase` runs NIGHT→DAY for cycles 1..5; *then*
   after each transition exactly one `leviathan:Day *` reminder exists in the whole
   grimoire and its label is `Day <cycle>`.

3. **Evil wins at the end of day 5.**
   *Given* the Leviathan is in play and it is day 5; *when* the ST advances DAY→NIGHT;
   *then* `WinCheck` returns `goodWins = false` with an "after day 5" reason.

4. **Mayor jinx overrides day 5.**
   *Given* Leviathan and Mayor both alive on day 5 and no execution recorded that day;
   *when* the day ends; *then* the advisory is `goodWins = true`.

5. **Two good executions end the game; two evil executions do not.**
   *Given* a good Monk executed on day 1 and a good Courtier executed on day 2; *then*
   `goodWins = false`. *Given instead* a Scarlet Woman executed on day 2 and a Poisoner on
   day 3; *then* no advisory fires.

6. **An execution that does not kill still counts.**
   *Given* a Virgin nomination that executes the good nominator, who is saved by a
   Pacifist; *then* an `ExecutionRecord` exists with `died = false`,
   `wasEvilAtExecution = false`, and the Leviathan count is 1.

7. **Alignment is judged at execution time.**
   *Given* a good player executed on day 2 who is later turned evil; *then* the Leviathan
   count still counts that execution as good.

8. **The Leviathan need not be alive.**
   *Given* the Leviathan is dead and a second good player is executed; *then* evil still
   wins.

9. **Leviathan-nominates jinxes.**
   *Given* the Leviathan nominates a Monk-protected player who is then executed; *then*
   `WinCheck` returns `goodWins = true` with the Monk jinx as the reason. Repeat for
   Soldier, Innkeeper-`Protected` and Exorcist-`Chosen`.

10. **Grandmother jinx.**
    *Given* a Leviathan game and a player marked `Grandchild` executed; *then*
    `goodWins = false`.

11. **Jinxed nightly choice cannot repeat.**
    *Given* the Leviathan chose player A on night 2 under the Sage jinx; *then* A is not a
    legal target on night 3, and a chosen Sage produces the Sage's info result with no
    death record.

12. **King jinx.**
    *Given* a Leviathan game with at least one dead player; *then* `InfoCalc.king` returns
    an "alive character to show" result rather than "the King doesn't wake".
