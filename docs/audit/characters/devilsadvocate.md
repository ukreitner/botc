# Devil's Advocate (devilsadvocate) — Bad Moon Rising Minion

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Devil%27s_Advocate>

Current ability text (matches `characters.json`):

> "Each night, choose a living player (different to last night): if executed tomorrow, they don't die."

How to Run (quoted):

> "Each night, wake the Devil's Advocate. They point at any player. Put the Devil's
> Advocate to sleep. Mark the chosen player with the **SURVIVES EXECUTION** reminder.
> If a player marked **SURVIVES EXECUTION** is executed, declare that the player was
> executed but remains alive. (*Do not say why.*)"

Constraints (quoted):

> "The Devil's Advocate cannot choose the same player two nights in a row, whether or
> not that player was saved from execution today, and they cannot choose a Zombuul
> that registers as dead."

Derived / standard-rules points that matter for the app:

- **Every night, including the first.** The DA acts on night 1 and on every subsequent
  night while alive.
- **Living player only** (ability text: "choose a *living* player").
- **The DA may choose themselves** — the ability says "a living player", with no
  self-exclusion. (The wiki does not call this out explicitly; the app should permit
  it and not warn.)
- **"different to last night"** is unconditional. The wiki's phrasing —
  "whether or not that player was saved from execution today" — closes the obvious
  loophole. It applies even if the DA was drunk/poisoned last night, because the DA
  still *pointed* at a player. It cannot apply on the DA's first-ever night (no prior
  choice), e.g. a Pit-Hag-made DA on night 4 has a free first pick.
- **The execution still happens.** Only the *death* is prevented. The day's one
  execution is spent, the town does not get another, and the Storyteller announces
  "X was executed" and then that X is alive, *without saying why*. This matters for
  every "executed today" trigger: Undertaker learns nothing (nobody died by
  execution), Godfather is not triggered (no Outsider *died*), Saint does not lose
  the game if a protected Saint is "executed", Minstrel does not fire, Mastermind's
  extra day is not triggered if a protected Demon is "executed".
- **Zombuul exclusion:** the DA cannot choose a Zombuul that currently registers as
  dead (i.e. one that has already "died" once and is playing on).
- **Token lifetime:** placed at night, matters during the *following* day only, then
  moves at the DA's next wake. Physically the ST removes/moves the single
  SURVIVES EXECUTION token each night.
- **Drunk/poisoned DA:** they still point (and are still bound by "different to last
  night" next night), but no protection is created. The Storyteller should still move
  the token *or* not place it — the app should let the ST record the choice and mark
  it inert.
- **Dead DA:** no ability; the standing token expires at the next dusk and no new one
  is placed.

Jinxes: none listed for the Devil's Advocate in
`engine/src/main/resources/botc/data/night_and_jinxes.json`, and the wiki lists none.

## What the app does today

Data:
- `engine/src/main/resources/botc/data/characters.json:617-629` — ability text matches
  the wiki. `reminders: ["Survives execution"]`. `firstNightReminder` /
  `otherNightReminder` are the official night-sheet prose (the other-night one *does*
  say "different from their previous night's choice").
- `engine/src/main/resources/botc/data/night_and_jinxes.json:329` (firstNight index 34)
  and `:395` (otherNight index 22). **Night order position is correct.**
- `engine/src/main/resources/botc/data/night_guide.json:332-340` — good prose for both
  nights, including "Remove the previous Survives Execution reminder token" and
  "different from their previous night's choice". `shows: []`.

Engine:
- `engine/src/main/kotlin/com/clocktower/engine/GameActions.kt:236` —
  `"devilsadvocate" to "Survives execution"` is in `EXPIRES_AT_DUSK`, cleared by
  `clearEphemeral` in `advancePhase` on the DAY→NIGHT transition
  (`GameActions.kt:261`).
- `engine/src/main/kotlin/com/clocktower/engine/StatusEffects.kt:68` — `deathNotes`
  emits "Devil's Advocate: survives execution today." for a seat holding a reminder
  whose lowercased label is `"survives execution"`.

UI:
- Night step: `NightScreen.kt:834` calls `QuickResolutions`, which falls into the
  `else` branch (`NightScreen.kt:518-524`); the DA is a Minion, not a Demon, so **no
  resolver renders**. `InfoCalc.supports("devilsadvocate")` is false
  (`InfoCalc.kt:29-36`), so no info panel either. The only tool is the generic
  `NightToolTray` (`NightScreen.kt:193-357`): tap the "Survives execution" chip, then
  tap a seat. Because `character.allReminders` contains exactly one copy of the label,
  `availableCopies <= 1` and the tray uses `GameActions.placeExclusiveReminder`
  (`NightScreen.kt:323-324`), which *does* move the token off the previous seat.
- Alternate placement path: `SeatSheet.kt` → "Add reminder" → `ReminderPicker`
  (`SeatSheet.kt:562`) also builds `PlacedReminder(c.id, label)` — **the same
  `sourceId` and the same label**, so the expiry table *does* match this path too.
  (The brief's hypothesis of a sourceId mismatch is not borne out; see Defect 4 for
  the real difference between the two paths.)
- Day: `DayScreen.kt:111-114` "Execute" and `DayScreen.kt:350-357` per-nomination
  "Execute" both call `viewModel.kill(id, DeathCause.EXECUTION)` **with no protection
  check at all**. `GameShell.kt:599-604` ("Execute & begin night" in the dusk guard)
  does the same.
- The only place the SURVIVES EXECUTION note is surfaced is the seat sheet
  (`SeatSheet.kt:241-250` and the `pendingKill` confirm dialog at
  `SeatSheet.kt:256-307`, whose filter list contains `"survives"`).

Storyteller's actual experience: on night 1 the step reads correctly and the tray
places the token. Through day 1 the token sits on the seat. If that player is executed
from the **Day tab** they simply die. At dusk the token silently vanishes, so on night
2 there is no record of last night's pick and nothing stops the same player being
chosen again.

## Defects and gaps

1. **P0 · Execution kills a SURVIVES EXECUTION player.**
   Rules: the execution is announced, the player lives. App: `DayScreen.kt:111-114`
   (block banner "Execute"), `DayScreen.kt:350-357` (nomination row "Execute") and
   `GameShell.kt:601` (dusk guard "Execute & begin night") all call
   `viewModel.kill(..., DeathCause.EXECUTION)` unconditionally. No check of
   `player.reminders`, no `deathNotes` call, no confirm dialog.
   Repro: night 1 place "Survives execution" on Alice → day 1 nominate/vote Alice onto
   the block → Day tab → "Execute" → Alice is dead with a shroud.
   (The seat-sheet path at `SeatSheet.kt:274` *does* warn, so the bug is
   path-dependent and easy to hit, since the Day tab is the natural place to execute.)

2. **P0 · "Different to last night" cannot be enforced — the app has no memory of
   night choices.**
   `GameState` (`GameState.kt:93-115`) stores no per-night choice history at all. The
   only trace of last night's DA pick is the token itself, and that token is deleted by
   `EXPIRES_AT_DUSK` at the DAY→NIGHT transition (`GameActions.kt:261`) — i.e. *before*
   the DA's night-2 step runs (otherNight index 22). So at the moment the ST needs the
   constraint the information is already gone.
   Repro: night 1 DA picks Alice → dawn → dusk → night 2 → the DA step offers every
   seat including Alice with no marking, and the tray happily places the token on
   Alice again.

3. **P1 · At dawn/day start nothing tells the ST that someone survives execution
   today.** There is no dawn summary and no day-start briefing anywhere in the app
   (`GameShell.kt` jumps straight to the Day tab; `DayScreen.kt:85-124` shows only
   alive count / threshold / block). The ST must remember to look at the grimoire seat.

4. **P1 · The seat-sheet path can create duplicate tokens.**
   `SeatSheet.kt:113` calls `viewModel.addReminder` (plain
   `GameActions.addReminder`), not `placeExclusiveReminder`. Placing "Survives
   execution" from the seat sheet on two different seats leaves two live tokens, which
   is impossible with the physical single token. Repro: seat A → Add reminder →
   Devil's Advocate → "Survives execution"; repeat on seat B; both keep the token.
   This is very likely what the user hit when reporting "DA wasn't automatically
   removed": the tray path moves the token, the seat path does not.

5. **P1 · No pick UI for the Devil's Advocate step.** The step has no
   `QuickResolutions` branch; the ST must scroll to the bottom tray, find the chip and
   then find the seat in a horizontal `LazyRow` of *all* players. There is no
   "chose nobody" affordance and no record that the step's choice was made (only the
   generic checkbox).

6. **P2 · Dead players are offered as targets.** The tray's seat list is
   `state.players` (`NightScreen.kt:315`), unfiltered. The rules require a *living*
   player. Likewise a Zombuul that registers as dead is not excluded.

7. **P2 · Drunk/poisoned DA is not surfaced.** No `StatusEffects.isImpaired` warning on
   the DA's step (unlike `DemonKillPanel.kt`'s demon check at `NightScreen.kt:548`),
   and no way to record "they pointed, but it does nothing" so that next night's
   "different to last night" still applies.

8. **P2 · Label case drift in the guide prose.** `night_guide.json:334,338` says
   "Survives Execution" while `characters.json:626` and `GameActions.kt:236` say
   "Survives execution", and the physical token reads "SURVIVES EXECUTION". No code
   compares them (all live comparisons are case-insensitive:
   `StatusEffects.kt:65-70`), so this is currently cosmetic — but it is a trap for any
   future exact-match code and should be normalised.

9. **P3 · The step never states the consequence chain.** Nothing tells the ST that the
   day's execution is *spent*, that no "executed today" trigger fires (Undertaker,
   Godfather, Saint, Minstrel, Mastermind), or that they must not explain why the
   player lived.

## Proposed behaviour (spec)

### Night action

- **when:** both (first and other nights). Wake condition: the DA seat exists and is
  **alive**. Skip (grey, auto-checkable) if dead.
- **targets:** exactly 1.
  - constraints: `alive == true`; **must differ from `lastChoice`** (see state below);
    a Zombuul currently registering as dead is excluded (a seat with
    `characterId == "zombuul"` that has a non-resurrected `DeathRecord` but is still
    `alive`). Self-selection is allowed.
  - picker: a `ResolutionPicker`-style chip row inside the step (not the bottom tray),
    sorted alive-first, seat order preserved. Last night's player is rendered
    **disabled** with the sub-label "chosen last night". A "They chose nobody /
    skipped" text button is present.
- **immediate effects:** `placeExclusiveReminder(target, PlacedReminder("devilsadvocate",
  "Survives execution"))`. Record `lastChoice` (see state). If the DA is impaired,
  still record `lastChoice`, still place the token but tag it inert (see below).
- **deferred effects:** the protection is consumed (or wasted) during the following
  day's execution. Nothing at dawn.
- **expiry:** the token expires at **dusk** (unchanged, `EXPIRES_AT_DUSK`). The
  *choice record* must NOT expire — it is what enforces "different to last night".
- **information:** none computed, nothing shown to any player.
- **visibility:** nothing shown to the Demon or Minions.
- **day-time inputs:** none.

### New engine state

Add to `GameState`:

```kotlin
/** Last night's choice per character id, for "different to last night" rules. */
val lastNightChoice: Map<String, Long> = emptyMap()   // "devilsadvocate" -> playerId
```

Written when the DA step resolves; **not** cleared by `advancePhase`. (This is the
generic mechanism; see the cross-cutting note at the end.) The DA step reads
`state.lastNightChoice["devilsadvocate"]`.

Alternative if a fuller history is wanted (preferable, and reusable by Gossip, Juggler,
Mathematician, the Lunatic, etc.):

```kotlin
@Serializable data class NightChoice(
    val cycle: Int, val characterId: String, val chooserId: Long,
    val targetIds: List<Long>, val impaired: Boolean,
)
val nightChoices: List<NightChoice> = emptyList()
```

with `GameActions.recordNightChoice(...)` and
`GameActions.lastChoiceOf(state, characterId, beforeCycle = state.cycle)`.

### Impaired handling

If `StatusEffects.isImpaired(state, lookup, daSeat)` at the moment of choosing, place
`PlacedReminder("devilsadvocate", "Survives execution")` **and** a companion
`PlacedReminder("devilsadvocate", "No ability")`-style marker, or simply do not place
the protective token and show a red line on the step:
"! The Devil's Advocate is drunk/poisoned — they still point (and still can't repeat
next night), but nobody is protected."
Recommendation: do **not** place the protective token (so the execution flow behaves
correctly) and do record the choice.

### Execution flow (the P0 fix — engine, not UI)

Introduce a single execution entry point used by *all three* execute buttons:

```kotlin
sealed interface ExecutionOutcome {
    data class Dies(val state: GameState) : ExecutionOutcome
    data class Survives(val state: GameState, val reason: String) : ExecutionOutcome
}

fun GameActions.execute(state: GameState, playerId: Long, lookup: (String) -> Character?): ExecutionOutcome
```

`execute` must:
1. Record that today's execution has been spent (`executedToday = playerId` on
   `GameState`, cleared at dusk) **regardless of outcome**.
2. If the target holds `("devilsadvocate", "Survives execution")` and the DA seat is
   alive and not impaired → return `Survives(reason = "Devil's Advocate")` with **no
   `kill`**, no `DeathRecord`, and no "died today" side effects.
3. Otherwise apply the other execution-time protections in the same place
   (Pacifist, Fool, Sailor, Tea Lady, Zombuul-first-death, Mastermind trigger…).

`DayScreen.kt:111`, `DayScreen.kt:351` and `GameShell.kt:601` all call it. On
`Survives`, the UI shows a full-width banner and a one-tap show-card:

> **"Alice was executed — and is still alive."**
> Announce the execution and that Alice lives. Do **not** say why.
> Today's execution is spent: no Undertaker info, no Godfather trigger, no Saint loss.

### Day-start briefing (fixes Defect 3)

At the NIGHT→DAY transition, build a "Dawn / Day N briefing" list (new shared engine
function, consumed by a card at the top of `DayScreen`). Devil's Advocate contributes:

> "**Alice survives execution today** (Devil's Advocate). Announce the execution
> normally, then that she lives — never why."

Only include the line if the DA seat is alive and was not impaired when choosing.

### UI text for the night step

Title row detail (replaces the raw `otherNightReminder`):

- First night: "Wake the Devil's Advocate. They point at a **living** player. Place
  SURVIVES EXECUTION. If that player is executed tomorrow, the execution happens but
  they don't die."
- Other nights: "Wake the Devil's Advocate. They point at a **living** player —
  **not `<last night's name>`**. Move SURVIVES EXECUTION to them."
- If the DA is impaired: "! Drunk/poisoned — let them point, then place nothing."
- If the DA is dead: "Dead — skip. The old token has already been removed."

### Data changes

- `night_guide.json:334,338` — change "Survives Execution" → "Survives execution" to
  match `characters.json` and the expiry table; append to the "other" instructions:
  "The app removes the old token at dusk and blocks last night's player in the picker."
- Add a `shows` entry to both nights? No — the DA shows nothing to anyone. Leave
  `shows: []`.
- No night-order changes.

## Tests to add

1. `execution respects Devil's Advocate protection`
   Given a 7-player game, DA alive, `PlacedReminder("devilsadvocate","Survives execution")`
   on Alice, phase DAY. When `GameActions.execute(state, alice)`. Then Alice is alive,
   `state.deaths` is unchanged, the outcome is `Survives("Devil's Advocate")`, and
   `state.executedToday == alice.id`. **Fails today** (no `execute` function; `kill`
   kills).
2. `protected execution does not trigger the Godfather`
   Given the protected player is an Outsider and a Godfather is alive. When executed.
   Then no `("godfather","Died today")` reminder is placed and
   `StatusEffects.deathNotes` produces no Godfather line for that day.
3. `protected Saint execution does not lose the game`
   Given a Saint holding SURVIVES EXECUTION. When executed. Then
   `WinCheck.check(...)` returns no evil-wins advisory.
4. `Devil's Advocate cannot repeat last night's choice`
   Given `lastNightChoice["devilsadvocate"] == alice.id` at cycle 2. When the engine
   builds the DA's legal target list. Then Alice is excluded and every other living
   player is included. **Fails today** (no such state, no such function).
5. `Devil's Advocate choice record survives dusk while the token does not`
   Given the DA chose Alice on night 1. When `advancePhase` DAY→NIGHT. Then no seat
   holds `("devilsadvocate","Survives execution")` **and**
   `state.lastNightChoice["devilsadvocate"] == alice.id`.
6. `Devil's Advocate token moves, never duplicates`
   Given the token on Alice. When placed on Bob by either the tray path or the
   seat-sheet path. Then exactly one seat in the whole grimoire holds it.
   **Fails today** for the seat-sheet path (`SeatSheet.kt:113`).
7. `dead players are not legal Devil's Advocate targets`
   Given Alice is dead. Then Alice is not in the legal target list.
8. `a Zombuul registering as dead is not a legal target`
   Given a Zombuul with one non-resurrected `DeathRecord` but `alive == true`. Then
   the Zombuul is excluded.
9. `impaired Devil's Advocate protects nobody but still burns the choice`
   Given the DA is poisoned on night 2 and points at Bob. Then no SURVIVES EXECUTION
   token exists, and on night 3 Bob is excluded from the legal target list.
10. `day briefing lists the protected player`
    Given the token on Alice at the NIGHT→DAY transition. Then the day-start briefing
    contains a line naming Alice and the Devil's Advocate.
