# Professor (professor) — Bad Moon Rising Townsfolk

## Official rules (sources)

Sources:
- https://wiki.bloodontheclocktower.com/Professor
- https://wiki.bloodontheclocktower.com/Glossary (entries "Resurrected", "Dead", "Alive", "Dawn")
- https://wiki.bloodontheclocktower.com/Spy (misregistration "even if dead")

**Current ability text (matches `characters.json`):**

> "Once per game, at night\*, choose a dead player: if they are a Townsfolk, they are resurrected."

**How to run (wiki, quoted):**

> "Each night except the first, wake the Professor. The Professor either shakes their head no or points to a dead player."
>
> "If the Professor chose a dead Townsfolk, the chosen player becomes **alive** again—mark them with the Professor's **ALIVE** reminder and remove their shroud."
>
> "(_They wake later tonight if they normally would. If they wake on the first night only, they wake now to use their ability._)"
>
> "At dawn, after declaring which players died, declare which player is alive again. (_Do not say why._) **The Professor loses their ability**—mark them with the **NO ABILITY** reminder and remove their night token from the night sheet."

**Wiki examples (quoted):**

> "The Professor chooses a dead player who is claiming to be the Tea Lady. The player is actually the Lunatic. Nobody is resurrected."
>
> "The Professor resurrects the Grandmother, who learns a good player's character."
>
> "At dawn, all players learn the Grandmother player is alive, but not that the player is the Grandmother."

**Glossary (quoted):**

> **Resurrected/Regurgitated/Reborn/Raised** — "A dead player becoming alive again. When this happens, the player gains their ability back, even if it was a 'once per game' ability that had been used."
>
> **Dead** — "…When a player dies … they immediately lose their ability, and any persistent effects of their ability immediately end."
>
> **Dawn** — "The end of a night, just before the next day begins."

Derived rules that matter for implementation:

- **Timing.** Other nights only (`at night*`). Professor sits at index 63 of the 96-entry
  other-night order (`night_and_jinxes.json`), i.e. after every killing role and before the
  bulk of the info roles (Grandmother, Ravenkeeper, Empath, Fortune Teller, Undertaker, …).
- **Target legality.** Any *dead* player may be pointed at. The resurrection only happens if
  they **are a Townsfolk**. Outsider / Minion / Demon ⇒ *nothing happens*, **and the ability
  is still spent** (the wiki's Lunatic example; the app's own `night_guide.json` says the same).
- **Team is judged on the true character**, not the shown one — a dead **Drunk** or **Lunatic**
  is an Outsider and is not resurrected even though they hold a Townsfolk / Demon token.
- **Spy exception.** The Spy "might register as good & as a **Townsfolk** or Outsider, **even if
  dead**." A dead Spy may therefore be resurrected, at Storyteller discretion. A dead Recluse
  never registers as Townsfolk, so it can never be resurrected.
- **Ability restored.** The resurrected player "gains their ability back, even if it was a
  'once per game' ability that had been used" (Glossary). Their spent-marker must be removed.
- **Waking again tonight** — the crux the user complained about:
  - The resurrected player wakes later tonight **if their normal wake position is after the
    Professor's** (e.g. Grandmother's death check, Empath, Ravenkeeper, Undertaker, Fortune Teller).
  - If their ability is a **first-night-only / "you start knowing"** ability, they wake **now**,
    immediately after the Professor goes back to sleep, and use it again — the wiki's Grandmother
    example, and the search-surfaced wording: *"If a resurrected player had a 'first night only'
    or 'you start knowing' ability, they immediately wake to use it again, as soon as the
    Professor goes to sleep."*
  - If their normal wake position is **before** the Professor's (Sailor, Innkeeper, Monk,
    Exorcist, Courtier, Gambler, Preacher …), they simply missed tonight and act from tomorrow night.
- **Dawn announcement** is mandatory and separate from the death announcement: after "these
  players died", say "…and *N* is alive again", without saying why.
- **Uncertain / to confirm:** the exact *content* of a re-run "you start knowing" ability. The
  wiki example ("who learns a good player's character") reads as a genuine fresh use of the
  ability, so the natural reading is *new information, true for the game state right now*
  (a Chef count recomputed on the current circle, a fresh Grandmother grandchild, etc.).
  I could not find an explicit official statement; the app should default to fresh, currently-true
  info and let the ST override.
- **Jinxes:** none for the Professor in the wiki or in `night_and_jinxes.json`.

## What the app does today

- `characters.json` — ability text, `otherNightReminder`, reminders `["Alive","No ability"]`: all
  correct and matching the official data. **Works.**
- `night_and_jinxes.json` — `otherNight[63] = "professor"`, absent from `firstNight`: correct
  position. **Works.**
- `night_guide.json` (professor → other) — the prose is *excellent* and already states every rule
  the code fails to implement: "If the chosen player is not a Townsfolk, nothing happens but the
  ability is still spent… place the Alive reminder token… at dawn announce that the player is now
  alive… If the Professor is drunk or poisoned, the resurrection does not happen but the ability
  is spent." The **code does none of this**; the guide is prose only.
- `NightScreen.kt:499-517` — the `"professor"` branch of `QuickResolutions`. Builds
  `deadCandidates = state.players.filter { !it.alive }` sorted Townsfolk-first, shows a
  `ResolutionPicker` titled *"Professor used their once-per-game — pick a dead Townsfolk to
  resurrect."*, and on confirm runs `GameActions.resurrect(state, target.id)` then
  `placeExclusiveReminder(holder, PlacedReminder("professor","No ability"))`.
  Hidden once the Professor already has a "No ability" token (`:504`).
- `GameActions.resurrect` (`GameActions.kt:173-181`) — sets `alive = true`, `ghostVoteUsed = false`,
  and flags the newest un-resurrected `DeathRecord` as `resurrected = true`. Undertaker/Cannibal
  history is preserved. **Works.**
- `NightOrder.kt:40-208` — the night sheet is a static projection of the global order list. There
  is **no mechanism to insert a step mid-night**; `NightScreen.kt:138` keys the list on `step.id`
  and `NightScreen.kt:792` / `:836` look up the guide and `InfoCalc` by `step.id`, so a step id and
  a character id are currently the same string.
- `NightOrder.kt:59` — the DAWN row is a constant: *"Wait a few seconds. Everyone opens their eyes.
  Announce who died."* It never names who died and never mentions a resurrection.
- `GameShell.kt:126-168` — `requestPhaseAdvance` on NIGHT→DAY only checks unfinished night steps,
  then calls `advancePhase`. There is **no dawn/day-start briefing surface anywhere in the app**.

Storyteller's actual experience last night: pick a dead player, tap "Resurrect X", the shroud
disappears — and that is the entire consequence. Nothing tells them at dawn to announce it,
nothing re-runs the player's first-night info, nothing removes that player's spent-ability token,
and the "Alive" reminder the physical game uses is never placed.

## Defects and gaps

1. **P0 · Non-Townsfolk targets are resurrected anyway.**
   Rules: choosing an Outsider/Minion/Demon does nothing (ability still spent). App: the picker
   offers *every* dead player and the confirm button always calls `resurrect`
   (`NightScreen.kt:500-515`). Repro: kill the Lunatic, open the Professor step night 2, tap the
   Lunatic, tap "Resurrect" — they come back alive. This is exactly the wiki's counter-example.

2. **P0 · No dawn announcement of the resurrection.**
   Rules: "At dawn, after declaring which players died, declare which player is alive again."
   App: the DAWN step text is the constant at `NightOrder.kt:59`; `advancePhase`
   (`GameActions.kt:258-263`) carries nothing forward into the day. Repro: resurrect anyone, tap
   "Dawn" — the app is silent. **This is the user's headline complaint.**

3. **P0 · The resurrected player's first-night step is never re-run.**
   Rules: "If they wake on the first night only, they wake now to use their ability" — the wiki's
   own Grandmother example. App: the night sheet is fixed at build time (`NightOrder.kt:40-208`)
   and has no insertion path. Repro: Grandmother dies night 2, Professor resurrects her night 3 —
   nothing prompts you to wake her, and no "you start knowing a good player & their character"
   step appears. **The user's second headline complaint.**

4. **P0 · A drunk/poisoned Professor still resurrects.**
   Rules (and the app's own `night_guide.json`): impaired ⇒ no resurrection, ability still spent.
   App: `QuickResolutions` never calls `StatusEffects.isImpaired` (contrast `DemonKillPanel`,
   which does at `NightScreen.kt:548`). Repro: poison the Professor, use the step, the target
   comes back to life.

5. **P1 · The "Alive" reminder token is never placed.**
   Rules: "mark them with the Professor's ALIVE reminder". App: only `("professor","No ability")`
   is placed (`NightScreen.kt:514`). The `"Alive"` label exists in `characters.json` but nothing
   ever uses it, so the grimoire loses the record of who was resurrected and by what.

6. **P1 · The resurrected player's spent once-per-game marker is not cleared.**
   Rules (Glossary "Resurrected"): they get the ability back "even if it was a 'once per game'
   ability that had been used". App: `resurrect` (`GameActions.kt:173-181`) touches only `alive`,
   `ghostVoteUsed` and the death record. Repro: a Slayer/Nightwatchman/Fisherman/Courtier who has
   used their shot dies, is resurrected — the "No ability" token is still on their seat and the
   night tray's "Mark spent" chip (`NightScreen.kt:263-279`) keeps them hidden.

7. **P1 · The dead Spy special case is not offered.**
   Rules: the Spy "might register as good & as a Townsfolk or Outsider, **even if dead**", so the
   ST may legally resurrect a dead Spy. App: no prompt; the picker's Townsfolk-first sort even
   buries them.

8. **P2 · The Professor's step is neither skipped nor greyed once spent, and is shown for a dead
   Professor.**
   Rules: "remove their night token from the night sheet"; dead players don't act. App:
   `NightOrder.build` has no alive/spent filter (`NightOrder.kt:142-178`), so the row remains and
   must be checked off manually every night. Only the resolver's UI is hidden
   (`NightScreen.kt:503-505`), leaving a step with prose and no tools.

9. **P2 · Nothing tells the ST what will happen next for the resurrected player.**
   The ST has to know the night order by heart to answer "does she wake again tonight?". The app
   already owns both order lists and could just say it.

10. **P3 · The picker's title lies.** *"Professor used their once-per-game — pick a dead Townsfolk
    to resurrect"* is shown before the choice is made, and offers non-Townsfolk anyway.

11. **P3 · No log entry.** `GameExtras.kt:51-58` renders deaths only; a resurrection leaves the
    log reading as though the player simply died, with no "alive again on night N" line.

## Proposed behaviour (spec)

### Night action

- **when:** other nights only. Wake condition: `holder.alive` **and** the Professor has no
  `("professor","No ability")` token. If either fails, the step is rendered collapsed, struck
  through, auto-marked done, with the reason ("Professor is dead" / "ability already spent").
- **targets:** exactly 0 or 1. Candidates: `state.players.filter { !it.alive }`. Sort dead
  Townsfolk first, then dead Spy (badged "may register as a Townsfolk"), then the rest (badged
  "not a Townsfolk — nothing will happen"). Also offer an explicit **"Shook their head — no
  choice"** button that just marks the step done and spends nothing.
- **immediate effects**, resolved by the engine in one `GameActions.professorResurrect(state,
  professorId, targetId, lookup)` call:
  1. Always: `placeExclusiveReminder(professorId, PlacedReminder("professor","No ability"))`
     — the ability is spent on *any* pointed-at player, success or not, impaired or not.
  2. If the Professor `isImpaired` **or** the target's true `characterId` team is not
     `TOWNSFOLK` (with the Spy override switch off): stop. Show the ST the line
     *"Nothing happens — the ability is spent."* No token, no announcement, no re-run.
  3. Otherwise: `resurrect(target)`; `addReminder(target, PlacedReminder("professor","Alive"))`;
     **clear the target's own spent markers** — remove every reminder on that seat with
     `sourceId == target.characterId && label in setOf("No ability","Used","Spent")`;
     queue the dawn note; queue the wake-again step (below).
- **deferred effects:**
  - *Dawn:* push `DawnNote(cycle = state.cycle, kind = "alive",
    text = "${target.name} is alive again.")`. Rendered in the DAWN night step, in the day-start
    briefing, and in the log.
  - *Tonight:* insert a re-run step if required (below).
- **expiry:** `("professor","No ability")` never expires. `("professor","Alive")` never expires
  (it is the historical record). Nothing goes into `EXPIRES_AT_DAWN` / `EXPIRES_AT_DUSK`.
- **information:** none computed for the Professor itself.
- **visibility:** the Demon and Minions are shown nothing. At dawn the town learns *that* a player
  is alive again, never who resurrected them or which character they are.
- **day-time inputs:** none.
- **interactions:** Spy (may register Townsfolk even when dead — ST toggle); Drunk / Lunatic /
  Marionette (judge on `characterId`, never `shownCharacterId`); Undertaker (the resurrected
  player *still* died by execution today, so the Undertaker — who wakes after the Professor —
  still learns their character; do not clear the Undertaker's "Died today" marker); Zombuul
  ("if no-one died today" is evaluated on the day, before this resurrection, so it is unaffected);
  Fool (per the Glossary the resurrected Fool gets their death-save back — flag to the ST rather
  than silently deciding).

### Dynamic night-sheet insertion (the general mechanism)

This is the piece the app is missing entirely, and several other characters need it too
(Farmer, Barber, Sage, Ravenkeeper on a mid-night death, Shabaloth regurgitation, Bone Collector).
Build it once, generically.

**Engine — `GameState.kt`:**

```kotlin
@Serializable
data class InsertedNightStep(
    /** Unique step id, e.g. "rerun:grandmother:3:7" (kind:character:cycle:playerId). */
    val stepId: String,
    /** Character whose guide / InfoCalc / tokens this step runs. */
    val characterId: String,
    val playerId: Long,
    /** Night this step belongs to; dropped when the cycle moves on. */
    val cycle: Int,
    /** Insert immediately AFTER this step id (the Professor's step). */
    val afterStepId: String,
    val kind: String = "rerunFirstNight",
    /** Storyteller-facing reason, shown on the row. */
    val reason: String = "",
)

@Serializable
data class DawnNote(val cycle: Int, val text: String, val kind: String = "info")

data class GameState(
    …,
    val insertedNightSteps: List<InsertedNightStep> = emptyList(),
    val dawnNotes: List<DawnNote> = emptyList(),
)
```

**Engine — `NightOrder.kt`:**

- `NightStep` gains `val characterId: String? = null` (null for markers; equal to `id` for normal
  character rows) and `val kind: String = "normal"` and `val note: String = ""`.
- At the end of `build(...)`, for each `state.insertedNightSteps.filter { it.cycle == state.cycle }`,
  construct
  ```kotlin
  NightStep(
      id = ins.stepId,
      title = "${lookup(ins.characterId)?.name} — first-night ability again",
      detail = lookup(ins.characterId)?.firstNightReminder.orEmpty(),
      playerIds = listOf(ins.playerId),
      characterId = ins.characterId,
      kind = ins.kind,
      note = ins.reason,           // "Resurrected by the Professor — re-run their first night."
  )
  ```
  and splice it directly after `steps.indexOfFirst { it.id == ins.afterStepId }` (append before
  DAWN if the anchor is missing).
- Add a helper the resolver calls:
  ```kotlin
  enum class ResurrectionWake { WAKES_LATER_TONIGHT, RERUN_FIRST_NIGHT, ACTS_FROM_TOMORROW, NEVER_WAKES }

  fun resurrectionWake(character: Character): ResurrectionWake
  ```
  computed from the two order lists plus the character record:
  - `firstNightOnlyInfo = firstNightReminder.isNotBlank() && (otherNightReminder.isBlank()
      || ability.startsWith("You start knowing", ignoreCase = true)
      || ability.contains("On your 1st night", ignoreCase = true))` ⇒ `RERUN_FIRST_NIGHT`.
    (Verified against the bundled data: this selects Washerwoman, Librarian, Investigator, Chef,
    Clockmaker, Shugenja, Steward, Noble, Pixie, Bounty Hunter and — matching the wiki example —
    **Grandmother**, while correctly rejecting Empath, Fortune Teller, Sailor, Chambermaid,
    Balloonist, Snake Charmer, Ravenkeeper, Undertaker.)
  - else if `id` appears in `otherNightOrder` at an index **greater** than the Professor's ⇒
    `WAKES_LATER_TONIGHT`.
  - else if `id` appears in `otherNightOrder` at a **smaller** index ⇒ `ACTS_FROM_TOMORROW`.
  - else ⇒ `NEVER_WAKES`.
  Allow a per-character override in `characters.json` (`"resurrectionWake": "rerunFirstNight"`)
  for homebrew and future exceptions.

**Engine — `GameActions.kt`:**
- `advancePhase` NIGHT→DAY: keep `dawnNotes` (the day briefing consumes them) and drop
  `insertedNightSteps` whose `cycle != state.cycle`; DAY→NIGHT: clear `dawnNotes` of previous
  cycles and clear `insertedNightSteps` outright.

**UI — `NightScreen.kt`:**
- Replace every `step.id` used for *content* lookup with `step.characterId ?: step.id`:
  `NightGuide.forStep` (`:792`), `viewModel.characterById` (`:718`), `InfoCalc.supports/compute`
  (`:836,:863`), `QuickResolutions`'s `when` (`:470`). Keep `step.id` for the LazyColumn key
  (`:138`), `nightStepsDone` and `toggleNightStep` — inserted steps must have their own
  completion state.
- A `kind == "rerunFirstNight"` row renders with a distinct accent and the `note` line, and
  `NightGuide.forStep(characterId, isFirstNight = true)` — i.e. the **first-night** run-book even
  though it is night 3, plus `InfoCalc.compute(..., characterId, …)` for fresh, currently-true info.
- When a re-run step is inserted while the sheet is open, auto-expand it (the existing
  `LaunchedEffect(state.nightStepsDone)` scroll machinery already handles focus changes).

**UI — DAWN step & day briefing:**
- DAWN step detail becomes dynamic: *"Announce the deaths: {names who died tonight, or 'nobody
  died'}"* plus one line per `dawnNote` — *"Then announce: Mina is alive again. Do not say why."*
- Advancing NIGHT→DAY opens a one-screen **Dawn briefing** sheet listing the same lines with a
  "Announced" acknowledge button. (Cross-cutting; shared with the Gossip/Devil's Advocate/Minstrel
  auditors.)

### UI text the step should display

- Header, unspent & alive: **"Professor — wake them. They shake their head, or point at a dead player."**
- Chip: **"Shook their head — no choice"**.
- Candidate badges: **"Townsfolk"** / **"not a Townsfolk — nothing will happen"** /
  **"Spy: may register as a Townsfolk even while dead"**.
- Confirm, Townsfolk: **"Resurrect {name} — spend the ability"**.
- Confirm, non-Townsfolk: **"Point at {name} — nothing happens, ability spent"**.
- After success: **"{name} is alive again. Place the ALIVE token. Announce at dawn: '{name} is
  alive again' — do not say why."**
- Wake-again line, one of:
  - **"{name} wakes again later tonight in the normal order — their step is below."**
  - **"{name}'s ability is first-night only — wake them now. Their step has been added below."**
  - **"{name} already woke earlier tonight — they act from tomorrow night."**
  - **"{name} has no night ability."**
- If impaired: **"! The Professor is drunk/poisoned — nobody is resurrected, but the ability is
  still spent."**
- Spent: **"Ability already used — skip the Professor tonight."**

### Data changes

- `characters.json` — no ability/reminder changes needed. Optionally add
  `"resurrectionWake": "rerunFirstNight"` to the "you start knowing" characters as an explicit
  override rather than relying on the derived predicate.
- `night_guide.json` — the professor/other text is already correct; add
  `"shows": []` → keep, and append one sentence: *"If they woke on the first night only, wake them
  again now, right after the Professor sleeps."*
- `night_and_jinxes.json` — no change.

## Tests to add

1. `professor picking a dead Outsider spends the ability but does not resurrect`
   Given a BMR game, night 2, a dead Lunatic and an alive Professor.
   When `GameActions.professorResurrect(state, professorId, lunaticId, lookup)`.
   Then the Lunatic is still `alive == false`, the Professor carries `("professor","No ability")`,
   and `state.dawnNotes` is empty.

2. `professor resurrecting a dead Townsfolk marks Alive and queues the dawn announcement`
   Given a dead Grandmother, an alive unspent Professor, night 3.
   When resurrecting her.
   Then she is alive, `ghostVoteUsed == false`, she carries `("professor","Alive")`, her
   `DeathRecord.resurrected == true`, and `state.dawnNotes` contains a note for cycle 3 whose text
   names her.

3. `a drunk professor resurrects nobody but still spends the ability`
   Given the Professor holds a `("poisoner","Poisoned")` token.
   When resurrecting a dead Chef.
   Then the Chef is still dead and the Professor carries `("professor","No ability")`.

4. `resurrection restores a spent once per game ability`
   Given a dead Slayer carrying `("slayer","No ability")`.
   When the Professor resurrects them.
   Then that reminder is gone and the seat has no `"No ability"` token from its own character.

5. `resurrecting a first-night-only character inserts a re-run step right after the professor`
   Given a dead Grandmother, night 3.
   When resurrecting her, then `nightOrder.otherNight(state, lookup)` contains a step whose
   `characterId == "grandmother"`, `kind == "rerunFirstNight"`, whose index is exactly
   `indexOf(professorStep) + 1`, and whose id is **not** `"grandmother"` (so the existing
   Grandmother death-check row and the re-run row have independent checkboxes).

6. `resurrecting an each-night character that wakes after the professor inserts nothing`
   Given a dead Empath resurrected on night 3.
   Then no `insertedNightSteps` entry exists and `resurrectionWake(empath) == WAKES_LATER_TONIGHT`.

7. `resurrecting a character that wakes before the professor reports acts-from-tomorrow`
   Given a dead Sailor (other-night index 9 < 63) resurrected on night 3.
   Then `resurrectionWake(sailor) == ACTS_FROM_TOMORROW` and no step is inserted.

8. `inserted steps are dropped when the night ends`
   Given an inserted re-run step on night 3. When `advancePhase` twice (dawn, then dusk).
   Then `state.insertedNightSteps` is empty and the night-4 sheet is the plain order.

9. `dawn notes survive to the day and are cleared at dusk`
   Given a dawn note queued on night 3. When `advancePhase` (dawn) then the day briefing reads it,
   then `advancePhase` (dusk). Then `state.dawnNotes` no longer contains the cycle-3 note.

10. `resurrectionWake predicate classification` — a table test over every bundled character
    asserting the four buckets for at least: grandmother/chef/washerwoman/clockmaker/shugenja
    (RERUN_FIRST_NIGHT), empath/fortuneteller/ravenkeeper/undertaker/balloonist
    (WAKES_LATER_TONIGHT), sailor/innkeeper/monk/exorcist/courtier (ACTS_FROM_TOMORROW),
    mayor/soldier/tealady/pacifist/minstrel (NEVER_WAKES).
