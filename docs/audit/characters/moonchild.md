# Moonchild (moonchild) — Bad Moon Rising Outsider

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Moonchild>

Current ability text (matches `characters.json`):

> "When you learn that you died, publicly choose 1 alive player. Tonight, if it was a
> good player, they die."

How to Run:

> "When you declare the Moonchild's player dead, they publicly choose any alive player.
> If that player is good, mark their token with the **DEAD** reminder. Tonight, the
> marked player dies."
>
> "If the Moonchild doesn't realize they must choose, privately remind them — new
> players may not understand their character."

Timing and edge cases (from the wiki's Key Mechanics / Examples):

- **When the choice happens:** "within a minute or two of learning they died, whether by
  execution, dawn declaration, or night kill." So: executed at dusk → chooses
  immediately, that same day; killed at night → learns at dawn → chooses during that
  day. Either way the choice is made **during the day**, publicly.
- **When the target dies:** "That same night" — i.e. the night that follows the choice.
- **If the chosen player is evil:** nothing happens.
- **Drunk/poisoned:** "If the Moonchild was sober **when choosing** but drunk/poisoned
  at night, the target still dies. If drunk/poisoned **when choosing** but sober at
  night, the target doesn't die." → impairment is evaluated **at the moment of the
  public choice**, not at the moment of resolution.
- **Goon:** "The Moonchild kills the Goon if the Goon was good **when chosen**,
  regardless of alignment changes by night." → alignment is also evaluated at the moment
  of the choice.
- **No death, no choice:** "Pacifist in play: Moonchild is executed but remains alive —
  no choice is made since they didn't die." Same for a Devil's Advocate save, a Fool's
  first death, a Sailor, a Tea Lady neighbour.
- **Can fire more than once:** "Shabaloth eats Moonchild twice: first curse on Assassin
  (evil) fails. Second curse on Gossip (good) succeeds." → after a resurrection the
  ability arms again.
- **Protection blocks the curse:** "Players protected by Sailor, Fool, Tea Lady, or
  Innkeeper won't die from your curse." Note the **Monk is absent from that list** —
  the Monk protects only from the Demon, and the Moonchild's curse is not a Demon kill.
- **Godfather note:** "Godfather kills the same night you curse" — two extra deaths can
  stack in one night; the ST should be aware before announcing at dawn.

Jinxes: none listed on the wiki page and none in the app's data.

## What the app does today

Data:
- `characters.json:574-587` — ability text matches the wiki.
  `otherNightReminder: "If the Moonchild used their ability to target a player today: If
  that player is good, they die."` `reminders: ["Dead"]`.
- `night_and_jinxes.json:443` — otherNight index **70**, after the Demons (37-51), the
  Assassin (55), the Godfather (56) and the Farmer (68), before the Grandmother (71) and
  the Ravenkeeper (72). **Correct.** No first-night entry (correct).
- `night_guide.json:314-319` — an "other"-only entry with accurate prose, including the
  evil-target case and the drunk/poisoned-at-choice case. `shows: []`.

Engine:
- `StatusEffects.kt:98` — `deathNotes` adds "Moonchild: they publicly choose a player who
  may die tonight." whenever the Moonchild seat is about to be killed. This is the app's
  one and only Moonchild automation, and it works.
- No `GameState` field records the Moonchild's public choice; `nominations` and `deaths`
  are the only day-time records that exist (`GameState.kt:103-104`).

UI:
- The warning above appears in `SeatSheet.kt:241-250` (seat sheet death buttons) and in
  `DemonKillPanel` via `NightScreen.kt:588-590`. It is *not* shown by the Day tab's
  Execute buttons (`DayScreen.kt:111-114, 350-357`) or by the dusk guard
  (`GameShell.kt:599-604`), which are the two most likely ways a Moonchild dies.
- Night step: `QuickResolutions` `else` branch — Moonchild is an Outsider, so no panel.
  `InfoCalc.supports("moonchild")` is false. The step therefore renders the prose plus
  the bottom tray with a single "Dead" chip.
- The step is built whenever a Moonchild is *in play*, alive or dead, resolved or not
  (`NightOrder.kt:142-178`), so it appears on every night of the game and must be ticked
  every night or the dawn guard (`GameShell.kt:147-161`) blocks.

Storyteller's actual experience: at the moment of death they may see a one-line note
(only from the seat sheet or the Demon panel). Then everything is manual: remember to
prompt the Moonchild, remember who was chosen, remember whether the Moonchild was sober
at that moment, remember the target's alignment at that moment, remember to check the
target's protections, place a "Dead" token by hand at night, kill by hand, and announce.

## Defects and gaps

1. **P1 · The public choice cannot be recorded anywhere.**
   Rules: the Moonchild names a player publicly during the day and it resolves that
   night. App: no state, no input, no note field designed for it — the ST must use the
   free-text seat note or `storytellerNotes` (`GameShell.kt:685-706`). The night step
   then asks "If the Moonchild used their ability to target a player today…" with
   nothing to answer from.
   Repro: execute the Moonchild on day 2, then look for anywhere to record "she chose
   Nate" — there is none.

2. **P1 · No prompt at the moment of death.**
   The rules require the ST to solicit the choice immediately (and to privately remind a
   player who forgets). The app shows only a passive `deathNotes` line, and only on two
   of the four death paths.

3. **P1 · Two of the four death paths show no Moonchild warning at all.**
   `DayScreen.kt:111-114` (block banner "Execute"), `DayScreen.kt:350-357` (nomination
   row "Execute") and `GameShell.kt:601` (dusk guard) call `viewModel.kill` directly with
   no `deathNotes` consultation. Executing the Moonchild from the Day tab — the normal
   way — produces no prompt whatsoever.

4. **P1 · The night step does not resolve the curse.**
   Rules: check the target's alignment *as of the choice*, check protections, then kill.
   App: no picker, no alignment check, no protection check, no kill button. The ST must
   place the "Dead" token from the tray and then find the seat and kill it manually,
   losing the `deathNotes` protection review in the process.

5. **P1 · Alignment and impairment must be snapshotted at the moment of the choice, and
   nothing snapshots them.**
   The Goon rule ("good **when chosen**") and the drunk/poisoned rule ("sober **when
   choosing**") both depend on state at day-time. By the time the night step runs, the
   Goon may have flipped and the Moonchild may have been poisoned. There is no
   mechanism to capture either.

6. **P2 · The step appears every night regardless of state.** A living Moonchild, or one
   whose curse already resolved, still produces a blocking checklist row. (The app's own
   playtest fixture records "Jasper was alive; skipped." three times —
   `FullGamePlaytestTest.kt:1155, 1238, 1271`.)

7. **P2 · Re-arming after resurrection is not modelled.** A Professor- or
   Shabaloth-resurrected Moonchild who dies again gets a second curse
   (`GameActions.resurrect` at `GameActions.kt:173-181` keeps the death record). Nothing
   tracks "already used this death's choice".

8. **P2 · A prevented execution must not arm the ability.** If a Pacifist, Devil's
   Advocate, Fool, Sailor or Tea Lady stops the death, the Moonchild does not choose.
   Once the execution flow gains a `Survives` outcome (see `devilsadvocate.md`), the
   Moonchild trigger must key off "a `DeathRecord` was actually created", not "the
   execute button was pressed".

9. **P3 · No show-card / announcement helper.** At dawn the ST must announce the extra
   death without saying why; nothing in the app composes that.

## Proposed behaviour (spec)

### New engine state

```kotlin
// GameState
@Serializable data class MoonchildCurse(
    val moonchildId: Long,
    val deathIndex: Int,          // index into state.deaths that armed this curse
    val day: Int,                 // the day the public choice was made
    val targetId: Long?,          // null until the choice is recorded
    val targetWasGood: Boolean,   // snapshot at the moment of choice
    val moonchildImpaired: Boolean, // snapshot at the moment of choice
    val resolved: Boolean = false,
)
val moonchildCurses: List<MoonchildCurse> = emptyList()
```

### Arming (on death)

When a `DeathRecord` is created for a seat whose `characterId == "moonchild"` — from
*any* path: the unified `execute`, `DemonKillPanel`, the seat sheet, the Godfather,
the Assassin, the Tinker — the engine appends
`MoonchildCurse(moonchildId, deathIndex, day = state.cycle, targetId = null, …)` and the
UI raises a **blocking-style prompt**, on the Day tab and on the Grimoire:

> **Moonchild — Jasper died**
> Tell Jasper he must **publicly** choose 1 alive player, now. If he doesn't realise,
> remind him privately.
> `[ chip row of all alive players ]`
> `[ Record choice ]`   `[ He chose nobody / didn't realise ]`

On record, fill in `targetId`, `targetWasGood = !target.isEvil(lookup)` and
`moonchildImpaired = StatusEffects.isImpaired(state, lookup, moonchild)` — **both
snapshotted right there**, which is exactly what the Goon and drunk/poisoned rules
require.

If the Moonchild died at **night**, raise the prompt at dawn instead (as part of the
day-start briefing), because that is when the player learns.

### Night resolution

- **when:** other nights only, at otherNight index 70 (unchanged).
- **wake condition:** there exists an unresolved `MoonchildCurse` whose `day == state.cycle - 1`
  (choice made during the day that just ended) **or** `day == state.cycle` if the
  Moonchild was executed at dusk of the current cycle — in practice: any unresolved
  curse. If none, the step renders as
  "The Moonchild is alive / has not died — nothing to do." and is **auto-ticked, not
  blocking**.
- **targets:** none (the target was chosen during the day).
- **immediate effects:** the step shows a resolution panel:

```
Moonchild curse — Jasper chose Nate.
Nate was GOOD when chosen  →  Nate dies tonight.
! Nate is marked 'Protected' (Innkeeper) — the curse fails.
[ Nate dies ]   [ Curse fails ]
```

  - if `!targetWasGood` → "Nate was **evil** when chosen — nothing happens."
    (auto-resolve, no button)
  - if `moonchildImpaired` → "The Moonchild was drunk/poisoned when choosing — nothing
    happens." (auto-resolve)
  - otherwise run `StatusEffects.deathNotes(target)` and list every protection, with the
    explicit note that **the Monk does not protect against this** (Monk protects only
    from the Demon) while Sailor / Fool / Tea Lady / Innkeeper do.
  - "Nate dies" places `PlacedReminder("moonchild","Dead")` and calls
    `kill(target, DeathCause.OTHER_NIGHT_DEATH)`; either button sets `resolved = true`.
- **deferred effects:** the death is announced at dawn with the others, cause unstated.
- **expiry:** `("moonchild","Dead")` should be added to `EXPIRES_AT_DAWN` so the marker
  is swept once announced (it is currently in neither table).
- **information:** none.
- **visibility:** the choice is *public* — nothing hidden. The Demon learns nothing extra.
- **day-time inputs the app must record:** the public choice (see Arming above). The
  Day tab should also show a standing banner while a curse is armed and unrecorded:
  "Moonchild: Jasper still owes the town a public choice."

### Interactions to handle explicitly

- **Godfather** — both can kill on the same night; the dawn announcement may name two
  or three deaths. The day briefing should list them together.
- **Goon** — resolve on `targetWasGood`, not on the Goon's alignment at night.
- **Prevented executions** — no `DeathRecord`, so no curse is armed.
- **Resurrection** — a new death arms a *new* `MoonchildCurse`; the old one stays
  `resolved`.
- **Zombuul** — a Moonchild "killed" by the Zombuul's first-death registering is not
  actually dead; only a real `DeathRecord` arms the curse.
- **Vortox** — the Moonchild's ability is not information, so the Vortox does not
  affect it.

### UI text for the step

- Armed: "Moonchild curse — **Jasper chose Nate**. Nate was good when chosen, so Nate
  dies tonight unless protected."
- Not armed: "The Moonchild is alive — nothing to do tonight."
- Armed but no choice recorded: "! Jasper died but never named anyone. Ask him now, or
  mark the curse as unused."

### Data changes

- `night_guide.json:314-319` — keep the prose; append "The app records the Moonchild's
  public choice at the moment they die, including whether the target was good and
  whether the Moonchild was sober at that moment. **The Monk does not protect against
  this curse** — only the Sailor, Fool, Tea Lady and Innkeeper do."
- `GameActions.kt:218-225` — add `"moonchild" to "Dead"` to `EXPIRES_AT_DAWN`.
- No `characters.json` or night-order changes.

## Tests to add

1. `moonchild death arms a curse from every death path`
   Given a Moonchild. When killed by (a) `execute`, (b) `DemonKillPanel`'s
   `DeathCause.DEMON`, (c) the Godfather, (d) the seat sheet's "Other death". Then in
   each case `state.moonchildCurses` gains one unresolved entry. **Fails today.**
2. `a prevented execution does not arm a curse`
   Given a Moonchild holding `("devilsadvocate","Survives execution")`. When
   `GameActions.execute` returns `Survives`. Then `moonchildCurses` is empty.
3. `curse kills a good target`
   Given a recorded curse on a good Nate with `moonchildImpaired = false`. When the
   night step resolves. Then Nate is dead with `DeathCause.OTHER_NIGHT_DEATH`.
4. `curse does nothing to an evil target`
   Given `targetWasGood = false`. Then the step auto-resolves and Nate is alive.
5. `alignment is snapshotted at the moment of choice — the Goon case`
   Given the Goon is **good** when the Moonchild names them, and the Goon flips to
   **evil** that night before the Moonchild's step. When the curse resolves. Then the
   Goon **dies**. **Fails today** (no snapshot).
6. `impairment is snapshotted at the moment of choice`
   Given the Moonchild is sober when choosing and poisoned by the Pukka afterwards.
   Then the target still dies. And the converse: poisoned when choosing, sober at
   night → the target lives.
7. `the Monk does not protect against the curse but the Innkeeper does`
   Given the target holds `("monk","Safe")`. Then the curse still kills. Given the
   target holds `("innkeeper","Protected")`. Then the curse fails.
8. `a second death after resurrection arms a second curse`
   Given a Moonchild whose first curse resolved, then `resurrect`, then a second
   death. Then `moonchildCurses.size == 2` with the second unresolved.
9. `the moonchild step is not blocking while the moonchild is alive`
   Given a living Moonchild at night 3. When the dawn guard runs with the step
   unticked. Then it is not reported as unfinished. **Fails today.**
10. `moonchild Dead marker is swept at dawn`
    When `advancePhase` NIGHT→DAY after the curse resolves. Then no seat holds
    `("moonchild","Dead")`.
