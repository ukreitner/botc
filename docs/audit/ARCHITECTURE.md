# ARCHITECTURE — the canonical design for the Clocktower Grimoire rebuild

**Status:** normative. This file supersedes the type proposals in
`docs/audit/mechanics/*.md`, `docs/audit/ux/*.md` and `docs/audit/characters/*.md`
wherever they disagree. Those documents remain the source for *rules content*
(what each character does, what the wiki says, what the defects are); this
document is the source for *names, shapes, files and sequencing*.

**Precedence:** `docs/audit/DECISIONS.md` (the lead's rulings, D1–D53) outranks this
document. Everything here conforms to it; §1.8 maps each ruling to the section that
implements it, and rows marked *"Reversal"* in §1 are places where this document's first
draft was corrected to match. If you find a remaining contradiction, DECISIONS.md wins and
you should report it.

**Who reads this:** every implementation agent, before writing a line of code.
You should not need to re-read the 60k lines of audits. When you need rules
content for a character, read only that character's file, its group digest in
`docs/audit/digest/`, and §7 of this document.

**Reading guide**

| § | Contents |
|---|---|
| 0 | The seven invariants everything else follows from |
| 1 | Decision table — every naming/shape conflict, resolved (§1.8 = conformance index) |
| 2 | Canonical Kotlin declarations (copy these verbatim) |
| 3 | File layout, UI contract, the shared view-model interface |
| 4 | Work packages WP0–WP12 with disjoint file ownership |
| 5 | Migration & compatibility (saves, undo, PWA localStorage) |
| 6 | Open rules questions still needing a ruling |
| 7 | One-page cheat sheet for the per-character registry packages |

---

## 0. Invariants

These are the load-bearing decisions. Everything in §1–§4 is an application of one of them.

**I1 — The storyteller inputs a choice; the engine computes every consequence.**
No UI screen may contain a character id in a `when` branch. Per-character behaviour
lives in one declarative registry (`CharacterRule`, §2.9) that the generic engine
interprets. This is the direct answer to *"Pukka … offered to kill even though it's
supposed to poison then kill the turn after"*.

**I2 — Effects are the rules; reminder tokens are their rendering.**
`Effect` (typed, dated, sourced) decides who is drunk, poisoned, protected, mad or
spent. `PlacedReminder` is what the storyteller sees and may hand-edit. A token
carries `effectId` back to its effect; a hand-placed token with no effect gets one
projected from `TokenRule`. Never grep a label for `"poison"` again.

**I3 — One append-only ledger for everything that happened or was said.**
`GameState.ledger: List<LedgerEntry>` records night choices, information given,
public statements, private conversations, storyteller rulings, once-per-game
spends, wakes and malfunctions. Token expiry is a *display* rule and must never
double as the app's memory. This is the direct answer to *"make it easy to write
down all the gossips even if Gossip isn't in play"* and to the Devil's Advocate bug.

**I4 — One deferred-obligation queue.**
`GameState.prompts: List<Prompt>` is everything the storyteller still owes:
announce a resurrection, re-run a first night, resolve a deferred kill, choose a
Sweetheart victim. Briefings are *derived views* over prompts + effects + ledger +
deaths, never a parallel store. This is the direct answer to *"When Professor brings
someone back it should remind in the morning and rerun the 1st night for that"*.

**I5 — One kill funnel, one execution funnel.**
Every path that ends a life calls `Deaths.attempt(...)`; every execution calls
`Execution.execute(...)` which calls `Deaths.attempt` internally. Protection,
attribution, on-death triggers and records are evaluated exactly once, in one place.
Five kill sites today; one after WP1.

**I6 — The night sheet is a pure function of tonight's state.**
`NightPlan.build(state, lookup)` returns the plan; nothing is cached, so an
insertion (a resurrected player's first night, a Scarlet Woman promotion, a Pit-Hag
creation) appears automatically the moment state changes. Steps carry a `StepGate`
that says *fire / reduced / conditional / skip, and why*.

**I7 — Identity is layered and never guessed.**
`characterId` is the truth, `shownCharacterId` is what the player believes,
`grants` + `Identity.actingRoles()` is what they act as. `Player.nightRoleId` — the
two-character special case that made the Lunatic, Boffin, Philosopher, Alchemist and
Village Idiot unimplementable — is deleted.

---

## 1. Decision table

Every conflict the parallel specs produced, with the ruling. **Alternatives listed
here are dead — do not implement them, do not reference them in new code.**

### 1.1 Records and memory

| # | Concept | Proposals that conflict | **Canonical** | Rationale |
|---|---|---|---|---|
| D1 | The history list | `LedgerEntry` (records-and-memory), `DayEntry`+`nextDayEntryId` (day-engine), `ChoiceRecord` (night-engine), `NightRecord`/`nightRecords` (ux/night-screen), `PublicStatement` (friction, gossip), `DayAction` (slayer), `DayRecord` (savant), `DayStatement` (alsaahir), `PublicClaim`, `SeatEvent` (ux/grimoire) | **`LedgerEntry` in `GameState.ledger`** with `LedgerKind` (§2.5) | One append-only list is automatically undo-correct (undo restores a whole `GameState`), gives one log dialog, one export, one seat history. Nine record types for one list is unmaintainable. |
| D2 | Fine-grained taxonomy | `DayEntryKind` (20 values), `StatementKind`, `SeatEventKind` | **`LedgerKind` stays coarse (10 values); the fine taxonomy is `LedgerEntry.sourceId`** — a character id (`"gossip"`, `"juggler"`) or a pseudo-source (`"claim"`, `"misregister"`, `"malfunction"`, `"note"`) | Adding a character must never require an enum change. `sourceId == "gossip"` is exactly as queryable as `kind == GOSSIP`. |
| D3 | "different from last night" | `lastNightChoice: Map<String, Long>` (devilsadvocate), `nightChoices: Map<NightKey, List<Long>>` (ux/night-screen), `ChoiceRecord` list | **`Memory.lastChoice(state, sourceId, holderId)` over ledger CHOICE entries** | A map cannot answer "two nights ago", breaks with two Village Idiots, and needs its own rollback discipline. |
| D4 | Wake / malfunction logs | `WakeEvent` + `MalfunctionEvent` as two new `GameState` lists (night-engine) | **`LedgerKind.WOKE` and `LedgerKind.MALFUNCTION` entries** | Chambermaid and Mathematician become two `Memory` queries; no new state. |
| D5 | Seat history | `Player.history: List<SeatEvent>` (ux/grimoire) | **Derived: `Memory.forPlayer(state, id)` merges ledger + deaths + executions + nominations** | Storing per-seat duplicates of ledger facts guarantees divergence. |
| D6 | Deferred obligations | `PendingEffect` (night-engine), `Prompt` (status-model), `Announcement` (fearmonger), `DawnNote` (professor), `insertedNightSteps` (professor) | **Split by nature:** things the storyteller must **say** are `LedgerEntry(kind = ANNOUNCE, announcePending = true)`; things the storyteller must **do or decide** are `Prompt` in `GameState.prompts` (§2.6) | *Lead D42 requires `announcePending` on `LedgerEntry`, so ANNOUNCE stays in the ledger* (an announcement is a fact once delivered). `Prompt` covers RUN_FIRST_NIGHT, RESOLVE_KILL, CHOOSE_PLAYER, DECIDE — obligations with a UI affordance, per lead D7/D17. |
| D7 | Storyteller secrets & setup choices | `secrets: Map<String,String>` (records), `setupChoices: Map<String,String>` (setup-identity), `fabledConfig` (fabled-B) | **`GameState.decisions: Map<String, String>`** for setup choices + secrets (`xaan.X`, `boffin.grant`, `mezepheles.word`, `lunatic.demon`, `teensyville.countTravellers`), typed accessors in `object Decisions`. **Fabled-specific typed keys live in `FabledEntry.config`** per lead D45 | Two string maps, not four, and the Fabled one is scoped to its entry. |
| D8 | Fabled state | `fabledIds: List<String>` (today), `FabledEntry` (fabled-B) | **`GameState.fabled: List<FabledEntry>`** — `FabledEntry(id, playerIds, spentBy, used, note, addedOnCycle, config)` — with `fabledIds` kept as a derived getter and `@SerialName("fabledIds")` migration | *Reversal: lead D21/D45 overrules this document's first draft, which had rejected `FabledEntry`.* Fabled need per-entry state (Ferryman's final day, Toymaker's skip, Sentinel's chosen delta, Storm Catcher's character). |
| D9 | Grimoire-centre tokens | `storytellerReminders` (plaguedoctor, records), a game-level `fanggu.once` flag | **`GameState.storytellerReminders: List<PlacedReminder>`** | One home for Gossip `Dead`, Yaggababble `Dead ×n`, Leviathan `Day N`, Riot `Day N`, Fang Gu `Once`, Minstrel `Everyone Is Drunk`. |
| D10 | Seat notes | `note: String` (today), `notes: List<SeatNote>` (ux/grimoire) | **`Player.notes: List<SeatNote>`**, legacy `note` migrated | Setup prompts currently overwrite storyteller notes; append-only fixes it. |

### 1.2 Status, death and execution

| # | Concept | Proposals that conflict | **Canonical** | Rationale |
|---|---|---|---|---|
| D11 | Impairment / protection model | `Effect` (status-model), `Effect` (friction §4), token-label grep (`StatusEffects.isImpaired`), `derivedPoison`, `deathNotes` prose | **`Effect` in `GameState.effects`, queried through `object Status`** (§2.3). Three disagreeing predicates collapse into `Status.impairment()` / `Status.hasAbility()` | The recursion the rules require ("every effect ends when its source loses their ability") is impossible over strings. |
| D12 | Tokens vs effects | "delete `PlacedReminder`" vs "keep tokens as truth" vs `PlacedReminder.inert` | **Rule-bearing tokens are RENDERED from effects and are not stored on the seat.** `Player.reminders` keeps only storyteller-placed free tokens (which gain payload fields `characterId`, `targetPlayerId`, `note`, `placedCycle`) and **never drive rules by label substring**. `Grimoire.tokensOn(state, player)` = `Effects.rendered(...) + player.reminders`. `PlacedReminder.inert` is **rejected** (lead D3) | Compatibility path: the load migration projects every legacy `PlacedReminder` that matches a `TokenRule` into an `Effect` and drops it from the seat; unmatched tokens survive untouched as free tokens. Zero manual data work, no divergence, and the storyteller keeps direct token control. |
| D13 | Effect display grouping | `ReminderKind` with glyph/priority (ux/grimoire), `EffectKind` (status-model) | **`EffectKind` is the rules classifier; `EffectGroup` (8 values, glyph + priority + colour) is derived from it.** `ReminderKind` is deleted | One classifier, two granularities. |
| D14 | Token lifetime enum | `Expiry { NEVER, DAWN, DUSK, … }` (night-engine `Tokens.kt`), `Until { DAWN, DUSK, … }` (status-model) | **`Until`** (§2.3) | `Until` already carries `DUSK_AFTER_N_DAYS`, `SOURCE_LOSES_ABILITY` and `EVENT`, which `Expiry` cannot express. `Expiry.NEVER` → `Until.FOREVER`. |
| D15 | Kill funnel | `attemptDeath(...) -> DeathOutcome` (friction §1), `killOutcome(...) + applyDeath(...)` (status-model), five ad-hoc call sites | **`Deaths.killOutcome(...)` (pure preview) + `Deaths.attempt(...)` (the funnel, returns `DeathAttempt`)** | Preview and apply must be the same 15-step precedence table; `KillSheet` renders the preview, the button applies it. |
| D16 | Death record | `DeathRecord` (today), `DeathEvent` (status-model) | **`DeathEvent`**, a defaulted superset of `DeathRecord`, with `typealias DeathRecord = DeathEvent` kept for one wave. `GameState.deaths` keeps its field name; `cause: DeathCause` keeps its enum **type**, extended to the lead-D29 taxonomy. `DeathKind` from status-model is **deleted** — `DeathCause` *is* the taxonomy | Old saves decode unchanged (enums serialise by name). `KillCause` = `DeathCause` + source ids + `ignoresProtection`, and is the *input* to the funnel; `killerCharacterId` / `killerPlayerId` are what get stored (lead D24). |
| D17 | Execution record | `ExecutionRecord(day, nomineeId, kind, nominationIndex, diedEventId, preventedBy)` (status-model); `ExecutionRecord(day, outcome, playerId, nominatorId, …, via, tally, threshold)` (day-engine); `executionsByDay: Map` (vortox); `executionSpentDay: Int?` (psychopath); `executionUsedToday: Boolean` (virgin); derive-from-deaths (mayor) | **day-engine's `ExecutionRecord`, plus `diedEventId` from status-model** (§2.7). `ExecutionKind` is renamed `ExecutionVia`. All derive-from-deaths variants are wrong | An execution that kills nobody must still count — Vortox, Mayor, Leviathan, Goblin, Boomdandy and Undertaker all hinge on it. `NO_EXECUTION` is a first-class row so "the day had no execution" is recorded, not inferred from absence. |
| D18 | Day-end flag | `dayEnded` (boomdandy), `executionSpentDay` (psychopath), `executionUsedToday` (virgin), `nominationsClosedOnDay` (day-engine, and this doc's first draft) | **No stored boolean.** The day's `ExecutionRecord` — including a `NO_EXECUTION` row — is the single day-closed signal (lead D30). `DayRules.executionSpent(state)` and `DayRules.nominationsClosed(state)` both derive from `state.executions` | The Butcher exception becomes `DayRules.secondExecutionAllowed(...)`, not a second flag. `Execution.noExecution(state)` is how the storyteller closes a day with no execution — and it is exactly the input the Mayor, Vortox, Zombuul and Godfather need anyway. |
| D19 | Zombuul "alive but dead" | `alive = false` + token (today), `Player.registersDead` (night-engine, and this doc's first draft), `Player.registersAlive` (status-model) | **`alive = false` + `DeathEvent(registeredOnly = true)`** (lead D6). No new `Player` field. `GameState.isTrulyAlive(playerId)` = `player.alive \|\| latestUnresurrectedDeath(playerId)?.registeredOnly == true`; `Deaths.attempt` permits a real second death | *Reversal from this doc's first draft.* Keeping the seat stored-dead means shroud rendering, ghost votes and "dead players are nominatable" all keep working with no change; only `WinCheck`, the Zombuul night gate and the grimoire badge consult the flag. |
| D20 | Nomination triggers | structured `NominationTrigger` (virgin) vs `notes += "…"` strings (goblin, psychopath, vizier, fearmonger) | **Structured `NominationCheck` / `NominationTrigger`** (§2.8). `StatusEffects.nominationWarnings` survives as `checkNomination(...).triggers.map { it.headline }` | Strings cannot kill a nominator or end a day. |
| D21 | Win advisories | `WinCheck.Advisory` (today), `duskCheck` (mayor), `Deaths.kt` (zombuul) | **`WinCheck` owns endings and gains `ruleId`, `blocking`, `duskCheck`, `dawnCheck`. `DayRules` owns day predicates.** No `Deaths.kt` ending logic | Dedupe/dismissal must key on `ruleId`, not on prose. |

### 1.3 Night engine

| # | Concept | Proposals that conflict | **Canonical** | Rationale |
|---|---|---|---|---|
| D22 | Step identity | `NightStep.playerId` + `StepKey(id, playerId, variant)` (night-engine), `NightStep.holderId` + `abilityId` + `key` (setup-identity) | **`StepKey(abilityId, holderId, variant)`** (lead D16, D53). `NightStep` additionally carries **`slotId`** — the night-order position, which defaults to `abilityId` but differs for a Lunatic (`"lunatic"`), an Alchemist-Poisoner (the Poisoner's index) and a Cannibal (the executee's index, chosen dynamically — lead D43) | `holderId` reads correctly next to `holderIds` (group steps); `abilityId` is what makes Boffin / Philosopher / Alchemist / Lunatic / Hermit rows resolvable at all (lead D39). `StepKey.token` is what goes in `nightStepsDone` and degrades to the bare ability id for simple steps, so old saves keep working. Any proposal saying `playerId` means `holderId`. |
| D23 | Step view model | `NightStepView` + `NightHolder` + `NightAsk` + `StepStatus` (ux/night-screen), `NightStep` + `StepGate` + `NightAction` (night-engine) | **One type: `NightStep`.** It carries `gate`, `banner`, `prompt`, `action`, `info`, `cards`, `badges`, `record`. Per-holder rendering is achieved by emitting **one `NightStep` per holder**, not by nesting holders | `NightHolder`, `NightStepView`, `NightAsk`, `StepStatus` are all deleted. The UI renders `NightStep` directly. |
| D24 | Gating | `StepGate { Fire, Reduced, Skip }` (night-engine), `StepStatus { ACT, CONDITIONAL, SKIP, DONE }` (ux) | **`StepGate { Fire, Reduced(reason, allow), Conditional(question, …), Skip(reason) }`.** `DONE` is derived from `nightStepsDone` | `Conditional` is the Godfather's "did an Outsider die today?"; `Reduced` is the Exorcised Pukka (choice suppressed, deferred death still happens). Never `Skip` an Exorcised Demon. |
| D25 | The action model | `NightAction` sealed (night-engine), `NightAsk` sealed (ux), `TargetSpec` (friction §2) | **`NightAction`** sealed interface, `@Serializable`, declared in the registry, interpreted by `NightPlan.resolve` | One picker component in the UI, driven by data. |
| D26 | Deferred night effects | standing token (Pukka), `PendingEffect` queue (night-engine) | **Both, deliberately** — standing token where the physical game uses one (`Until.ON_SOURCE_STEP`), `Prompt` where there is none | The Pukka's poison *is* the memory; Al-Hadikhia's dawn resolution is not. |
| D27 | Character behaviour data | `Character.spentLabel` / `perHolderStep` / `actsWhileDead` in `characters.json` (night-engine); `bagRule` field (setup-and-home) | **`Character.spentLabel: String = ""` is added to the data** (lead D49 — it drives `Gates.notSpent` and deletes the "Once per game" text heuristic). **Everything else stays in the code registry `CharacterRule`**: `perHolder`, `actsWhileDead`, `keepsAbilityWhenDead`, bag shapes, wake predicates, day hooks | `spentLabel` is a *label*, and labels are data (lead D5). Behaviour is code. Keeping behaviour out of `characters.json` is what lets WP5 regenerate the file wholesale from the official repo (lead D31) while WP7 adds registry rows, with no shared file between them. |

### 1.4 Setup and identity

| # | Concept | Proposals that conflict | **Canonical** | Rationale |
|---|---|---|---|---|
| D28 | Bluffs | `demonBluffIds: List<String>` (today), `bluffSets: Map<String, BluffSet>` (setup-identity), `bluffSets: Map<String, List<String>>` (setup-and-home), `lunaticBluffIds` (friction §9) | **`GameState.bluffSets: Map<String, List<String>>`** (lead D20 — a bare list, not a `BluffSet` type), keyed by the **requirement key**, which embeds the source so one seat can hold two sets: `"demon"`, `"lunatic:7"`, `"snitch:7"`, `"summoner:3"`. `demonBluffIds` becomes a derived read-only getter; the stored field migrates via `@SerialName("demonBluffIds")` | *Lead D20 and D38 appear to conflict (Map vs list-of-requirements). Reconciled: storage is the map; `Bluffs.requirements(...)` returns the `List<BluffRequirement>` and each requirement owns its map key.* Source-qualified keys are what make the Legion+Snitch seat (two independent sets on one player) expressible. `shownCycle` is dropped — "shown" is a `LedgerEntry(kind = TOLD)`. |
| D29 | Bluff planning | `Bluffs.requirements()` (setup-identity), `SetupTask(kind = PICK_BLUFF_SET)` (setup-and-home) | **Both, layered:** `Bluffs.requirements(state, lookup): List<BluffRequirement>` computes the requirement list (lead D38); `SetupRequirements` renders each as a checklist row | The requirement list is also needed mid-game (a Pit-Hag-created Snitch, a Kazali-created Minion). |
| D30 | Setup checklist | `SetupRequirement` + `SetupRequirements` (setup-identity), `SetupTask` + `SetupTasks` (setup-and-home) | **`SetupRequirement` / `object SetupRequirements`, in `SetupRequirements.kt`** (lead D48: setup-and-identity's id namespace is canonical — `drunk.token`, `boffin.grant`, `xaan.X`, `mezepheles.word`, `widow.know`, `marionette.token`, `summoner.bluffs`, `snitch.bluffs:<seat>`), carrying the **union** of both field sets: `id`, `characterId`, `kind`, `title`, `prompt`, `problem`, `blocking`, `candidates`, `apply`, `satisfied` | *Name reversal from this doc's first draft, which chose `SetupTask`.* The UI still calls it the "setup checklist". `validateSetupState` becomes `SetupRequirements.unmet(...).filter { it.blocking }`. |
| D31 | Granted abilities | `actingCharacterId`/`actingSourceId` (philosopher), `grantedAbilityId` + `nightSlots` (alchemist), `GameState.boffinGrantId` + `NightStep.abilityId` (boffin), derived `cannibalNightRole` (cannibal), `AbilityGrant` (setup-identity) | **`Player.grants: List<AbilityGrant>` + `GameState.floatingGrants` + `Identity.actingRoles(...)`** (§2.10). `NightStep.abilityId` is adopted verbatim | Deliberately the union of all four. `Identity.derivedGrants` re-derives the Drunk / Marionette / Lunatic / Cannibal / Hermit / Boffin cases so nothing is stored twice. |
| D32 | `Player.nightRoleId` | today's two-character special case | **Deleted** (kept `@Deprecated` for one wave) | It is the reason the Lunatic, Boffin, Philosopher, Alchemist and duplicate Village Idiots cannot be implemented. |
| D33 | Character change | `assignCharacter` + `swapCharacters` + `starPass` + `snakeCharmerSwap` (today), `changeCharacter(reason)` (setup-identity) | **`Identity.changeCharacter(...)` is the single funnel**; `assignCharacter` survives for SETUP-phase editing only; `swapCharacters` becomes two `changeCharacter` calls and **stops swapping `shownCharacterId`**; `starPass` is defined in terms of it and enforces the one-Demon-seat invariant | |
| D34 | Bag shape overrides | `TEAM_WARPING_IDS` (today), `BagShape` (setup-identity) | **`BagShape` + `Setup.bagShapeFor(...)`** | A table row per character instead of a `when`. |

### 1.5 Briefings and phase flow

| # | Concept | Proposals that conflict | **Canonical** | Rationale |
|---|---|---|---|---|
| D35 | Briefing types | `DawnReport` + `DayBriefing` + `DuskBriefing` (night-engine), `DayBriefing.Note` + `Slot` (day-engine), `Briefing.Line` + `forDawn`/`forDay` (records), `BriefingItem` + `BriefingKind` + `BriefingAction` (ux/day-screen), `DawnSummary` (ux/night-screen) | **One type, one function: `Briefing` / `BriefingItem` / `Briefings.at(state, lookup, slot)`** (§2.12) | Five names for one parameterised component. `DawnReport.publicScript` becomes `briefing.of(ANNOUNCE)`. |
| D36 | Briefing timing | computed on demand (day-engine) vs stored `lastDawn` (night-engine) | **Both:** `Briefings.at(...)` is pure and callable any time; `advancePhase` computes the DAWN briefing **before** sweeping tokens and stores it as `GameState.lastDawn` (and `lastDusk`) | The Monk token must still be present when "Bea was saved" is computed. Storing it also makes the dawn sheet undoable and re-openable, and gives the PWA the same behaviour for free. |
| D37 | Slot enum | `BriefingSlot` (night-engine), `DayBriefing.Slot` (day-engine), `PromptWhen` (status-model), `BriefingKind` (ux) | **One enum `BriefingSlot { NOW, TONIGHT, DAWN, DAY_START, NOMINATION, EXECUTION, DUSK }`**, used by `Prompt.at`, `NightEffect.Announce`, `Briefings.at`. `BriefingKind` survives as the *item* classifier (ANNOUNCE / PRIVATE / STANDING_FACT / TODO_ASK / SWEPT) | |
| D38 | Truth/verdict enum | `Verdict` (records), `DayEntryTruth` (day-engine) | **`Verdict { UNJUDGED, TRUE, FALSE, A_TRUE, B_TRUE, BOTH_TRUE, NEITHER_TRUE, ST_CHOICE }`** | Superset. |

### 1.6 Platform

| # | Concept | Proposals that conflict | **Canonical** | Rationale |
|---|---|---|---|---|
| D39 | View-model wrappers | "add a wrapper to both view models" (repeated in 5 specs) | **`interface GameActionsApi`** in `app/.../ui/GameActionsApi.kt` with **default-implemented** wrappers; `GameViewModel` and `WebGameViewModel` implement it and provide only `update`, `characterById`, `gameData` | The web build compiles the same `app/` screens against a hand-written parallel view model (`web/build.gradle.kts:59` excludes the Android one), so a missing wrapper breaks the PWA the user actually plays on. A default-method interface ends the class of drift permanently. |
| D40 | Platform seams | `rememberDictation`, `alertAtTable`, `setBrightness`, wake-lock re-request added ad hoc by several WPs | **All seam functions are declared once in WP0** in `ui/platform/Platform.kt` + `WebUiPlatform.kt`; feature WPs only call them | Two files that four WPs would otherwise all edit. |
| D41 | `Script` inside `GameState` | "move `Script` out of `GameState`" (records §H3) | **Deferred, out of scope.** Flagged as follow-up F-1 (§5.6) | It touches every file and would destroy the disjoint-ownership property of this plan. Its only consumer (persisted undo) is also deferred. |

---

### 1.7 Additional canonical types mandated by `DECISIONS.md`

These had no competing proposal — they are new requirements from the lead's rulings.
They are declared in §2 and must not be re-invented.

| # | Concept | **Canonical** | Source |
|---|---|---|---|
| D42 | Registration is a **set**, not a team | `Registration.registersAs(state, lookup, player): Set<Team>` — Legion = `{DEMON, MINION}`, a Lil' Monsta babysitter adds `DEMON`, Spy/Recluse/Faux Paw/Revolutionary via `REGISTERS_AS` effects. **Every** "is this player a Demon / Minion / evil" query in `InfoCalc`, `WinCheck`, `DayRules` and the registry goes through it | lead D32 |
| D43 | Information obligation | `InfoObligation { TRUTH, MAY_LIE, MUST_LIE }`, computed by the engine. An alive sober Vortox ⇒ `MUST_LIE` and **outranks** impairment (which is only `MAY_LIE`). Available to day abilities too (Artist, Savant, Juggler scoring) | lead D50 |
| D44 | Typed info results | `InfoResult(answer: Answer, alternatives: List<Answer>, obligation, caveats)` with `Answer = Count(n, min, max) \| YesNo \| Characters \| Players \| Message`. The string-matching false-info UI (`NightScreen.kt:903-906`) is deleted | lead D50, D10 |
| D45 | Vote rules snapshot | `VoteRules(eligibleVoterIds, threshold, spendsGhostVotes, weights: Map<Long,Int>, reasons: List<String>)`, computed by `DayRules.voteRules(...)` and **persisted in full on the `Nomination`** so a Voudon exiled mid-day cannot rewrite history. `Nomination.votes` becomes the weighted tally; `voterIds` stays raw; `extraVotes: Map<Long,Int>` carries the Banshee's second hand | lead D27, D44 |
| D46 | Nomination registration snapshot | `Nomination` also snapshots `nominatorCharacterId`, `nominatorTeams: Set<Team>`, `demonIdsAtRecord`, `registersRuling` — Town Crier and Flowergirl read the snapshot, never the live grimoire | lead D51 |
| D47 | Storyteller as a nominee | `const val STORYTELLER_SEAT_ID = -1L`, accepted by `Nomination.nomineeId`, `aboutToDie` and `ExecutionRecord.playerId` (Atheist games) | lead D44 |
| D48 | Cause-filtered protection | Protections declare which `DeathCause`s they block. Monk / Soldier / Innkeeper → `DEMON_KILL` only; Sailor / Tea Lady / Fool → any; Devil's Advocate / Pacifist → `EXECUTION` only; Assassin ignores all. A Gunslinger shot is blocked by Sailor/Tea Lady/Fool, **not** by Monk/Soldier/DA | lead D29 |
| D49 | Demon-cannot-kill effect | `EffectKind.DEMON_CANNOT_KILL` (Lycanthrope, Princess, Exorcist-silenced Demon, Toymaker final night) is enforced **inside the kill funnel** — `Blocked` for `cause == DEMON_KILL` when the *source* player carries it — so deferred Demon kills (Pukka) obey it too. Never by hiding a button | lead D36 |
| D50 | Vote/nominate suppression | `EffectKind.NO_VOTE` and `EffectKind.NO_NOMINATE`, consulted by `DayRules`. "No Ability" tokens feed impairment through `TokenRule.impairs`, never by substring | lead D46 |
| D51 | Token primitives | `TokenRule.mutexGroup` (Flowergirl `Demon Voted`/`Demon Not Voted`, Town Crier `Minions Nominated`/`Minion Nominated` can never coexist); exclusive **groups** (Leviathan `Day 1..5` replace each other); permanent multi-copy tokens (Sweetheart `Drunk`, Al-Hadikhia `1\|2\|3`, Leviathan `Good Player Executed` ×2) never collapse; `Ref.TargetN(i)` addresses individual picks; `NightEffect.SwapCharacters(a, b)` exists (Barber) | lead D33, D52 |
| D52 | Impairment history | `LedgerKind.IMPAIRMENT_SPAN` entries opened/closed whenever a seat's `impairment()` result changes at a state-mutation boundary, so "was seat X impaired during window Y" (Politician, Mathematician, Puzzlemaster, Plague Doctor) is answerable with no new store | lead D41 |
| D53 | End-of-game questions | `WinCheck` gains a final **Heretic inversion pass** (works while dead, suppressed while impaired), a blocking `EndGameQuestion` list (Politician, Fiddler, Cult Leader cult vote) the end-game dialog must answer before "Declare victory", and per-player win/lose results for the reveal sheet | lead D40 |
| D54 | Final day | `GameState.finalDayCycle: Int?`, declared by the storyteller and prompted at 3 alive — serves Ferryman, Angel and Fiddler | lead D47 |
| D55 | Jinx scope | Split into **`scriptJinxes`** (every jinx on the script — the Djinn's read-aloud list) and **`assignedJinxes`** (both characters actually in play). `Jinx` gains `effect: String = ""`, a registry hook id, so a jinx can *change* behaviour (lead D19: Riot/Leviathan night actions) rather than merely being displayed | lead D47, D19 |
| D56 | Team enum | `Team` gains `LORIC` and **deserialisation must tolerate unknown teams** (fall back to a `Team.UNKNOWN` rather than throwing), because the official dataset adds teams over time | lead D31 |
| D57 | Traveller alignment | `Player.alignment: Alignment? = null` — an explicit override that wins over the character's natural team. `alignmentFlipped` is kept only for migration. Traveller arrival is a 5-step flow: seat → traveller → alignment → show card (+ Demon reveal if evil) → announcement | lead D25 |
| D58 | Exile flow | Dead players may call an exile; calling is **not** a nomination; once per traveller per day; vote weights never apply to exiles and no ghost vote is spent | lead D25, D27 |
| D59 | Storyteller-initiated executions | `ExecutionVia.STORYTELLER` for Mutant / Cerenovus madness / Vizier, and it **counts as the day's execution** so Leviathan, Vortox and Undertaker stay consistent | lead D34 |
| D60 | Skipped steps are visible | A gated-off step renders greyed, auto-ticked, with its reason and a `[Run anyway]` affordance — never silently removed. `MINION_INFO` / `DEMON_INFO` come from **one shared builder** that is Magician / Poppy Grower / Snitch / Lunatic / Marionette / Summoner / Lil' Monsta / Legion aware | lead D37 |

### 1.8 Conformance index — where each lead decision lands

| Lead | Implemented in | Lead | Implemented in |
|---|---|---|---|
| D1 | §2.5 `LedgerEntry` (+`byStoryteller`) | D28 | §2.13 `BagShape`, WP4 |
| D2 | §2.7 `ExecutionRecord` | D29 | §2.6 `DeathCause`, §2.7 protection table |
| D3 | §2.3 `Effect`, `StandingRule`, §2.4 rendering | D30 | §2.8 `DayRules.nominationsClosed` |
| D4 | WP7-BMR registry row (Pukka) | D31 | WP5 |
| D5 | §2.4 `Tokens`, §3.4 label rule, WP5 | D32 | §2.3 `Registration` |
| D6 | §2.6 `DeathEvent.registeredOnly` | D33 | §2.4 `TokenRule`, §2.11 `Ref`, `NightEffect` |
| D7 | §2.6 `Deaths.resurrect` | D34 | §2.7 `ExecutionVia.STORYTELLER` |
| D8 | §2.2 alive counts, §6 Q1 | D35 | §2.9 `CharacterRule.deathTriggerGate` |
| D9 | §2.4 `Until.MANUAL` + `spentDisplay` | D36 | §2.7 kill funnel step 6 |
| D10 | §2.3 `REGISTERS_AS`, §2.5 `RULING` | D37 | §2.10 `StepGate.Skip`, §3.2 |
| D11 | §2.12 `InfoObligation`, WP5 guide fix | D38 | §2.13 `BluffRequirement` |
| D12 | WP7-SV registry row (Vigormortis) | D39 | §2.10 `Identity.actingRoles` |
| D13 | §2.10 `NightAction.kind`, `Chambermaid` | D40 | §2.8 `WinCheck`, `EndGameQuestion` |
| D14 | §2.4 `TokenRule.countdown*` | D41 | §2.5 `IMPAIRMENT_SPAN` |
| D15 | WP7-BMR registry row (Minstrel) | D42 | §2.5 `LedgerEntry` fields |
| D16 | §2.10 `StepKey` | D43 | §2.10 `NightStep.slotId`, `Identity` |
| D17 | §2.10 `Identity.changeCharacter` | D44 | §2.8 `Nomination`, `STORYTELLER_SEAT_ID` |
| D18 | §2.13 `BagShape` rows | D45 | §2.2 `FabledEntry` |
| D19 | §2.9 `CharacterRule` jinx gating | D46 | §2.3 `EffectKind`, §2.4 `TokenRule.impairs` |
| D20 | §2.13 `bluffSets` | D47 | §2.2 `finalDayCycle`, `storytellerReminders`, §2.14 jinx scope |
| D21 | §2.2 `FabledEntry` | D48 | §2.13 `SetupRequirement` ids |
| D22 | WP5 | D49 | §2.14 `Character.spentLabel` |
| D23 | WP5, §2.14 guide channels | D50 | §2.12 `InfoObligation`, `Answer` |
| D24 | §2.7 `Deaths.attempt` | D51 | §2.8 `Nomination` snapshot |
| D25 | §2.2 `Player.alignment`, WP10/WP11 | D52 | §2.4 `TokenRule.mutexGroup` |
| D26 | §3.5 `GameActionsApi` | D53 | §2.10 `StepKey` / `NightStep` |
| D27 | §2.8 `VoteRules` | | |

---

## 2. Canonical Kotlin declarations

### 2.1 Conventions (non-negotiable)

1. **Every new `GameState` / `Player` field has a default**, so old saves deserialise.
   Both platforms already use `Json { ignoreUnknownKeys = true; encodeDefaults = true }`
   (`app/.../data/Persistence.kt:15-18`, `web/.../WebApp.kt:15-18`) — do not change that.
2. **Enums are serialised by name.** Appending values is safe; *renaming* a value is a
   breaking change and needs an `@SerialName` on the constant.
3. **Pure functions only.** Every mutator takes a `GameState` and returns a new one.
   No engine function may throw on bad input from the UI — return the state unchanged.
4. **Character lookup is always `lookup: (String) -> Character?`**, threaded as the last
   parameter, defaulting to `{ null }` only where the function genuinely degrades.
5. **All `(sourceId, label)` comparisons are case-insensitive** (lead D5). Use
   `Tokens.key(sourceId, label)` — never `==` on a raw label, never `.lowercase()` inline.
6. **Character ids** are always normalised with `Character.normalizeId`.
7. KDoc every public declaration. One-line KDoc is fine; no KDoc is not.

### 2.2 `GameState.kt` — core state (owned by WP0, then frozen)

```kotlin
package com.clocktower.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Explicit alignment, used where it is a choice rather than a consequence of the character. */
@Serializable
enum class Alignment { GOOD, EVIL }

/** A dated storyteller note on one seat. Append-only: setup prompts must never overwrite. */
@Serializable
data class SeatNote(
    val cycle: Int,
    val phase: Phase,
    val text: String,
)

/**
 * A storyteller-placed grimoire token with no rule attached.
 *
 * Rule-bearing tokens are NOT stored here — they are rendered from [Effect]
 * (see [Grimoire.tokensOn]). This list is the storyteller's own scratch layer:
 * free markers, improvised rulings, and any legacy token the load migration
 * could not match to a [TokenRule].
 *
 * NOTE FOR IMPLEMENTERS: never compare two `PlacedReminder`s with `==` to decide
 * whether "the same token" is already placed — the payload fields make that
 * comparison fail. Compare `Tokens.key(sourceId, label)`. (This is the
 * `NightScreen.kt:326-338` regression.)
 */
@Serializable
data class PlacedReminder(
    /** Character id the token belongs to, or "st" for a storyteller token. Never "". */
    val sourceId: String,
    /** Official Title Case label from `characters.json`. Compared case-insensitively. */
    val label: String,
    /** Character this token points at: Cerenovus's mad character, Courtier's target. */
    val characterId: String? = null,
    /** Seat this token points back at: Harpy's 2nd, Grandmother's grandchild. */
    val targetPlayerId: Long? = null,
    /** Free text for an improvised ruling. Rendered under the token; searchable. */
    val note: String = "",
    /** `state.cycle` when placed — powers "placed N3" and homebrew countdowns. */
    val placedCycle: Int = 0,
)

/** One seat in the grimoire circle. */
@Serializable
data class Player(
    val id: Long,
    val name: String,
    /** THE TRUTH. What this player actually is. Never what they believe. */
    val characterId: String? = null,
    /**
     * The token this player has SEEN — Drunk, Lunatic, Marionette, a mid-change
     * seat whose new token has not been handed over yet, and the real Demon in a
     * Lunatic game (set to "lunatic" at deal, cleared at DEMON_INFO).
     */
    val shownCharacterId: String? = null,
    /**
     * Explicit alignment override. Wins over the character's natural team.
     * Set for Travellers (always asked), Bounty Hunter's evil Townsfolk, an
     * evil-turned good player. Null = derive from the character.
     */
    val alignment: Alignment? = null,
    val alive: Boolean = true,
    val ghostVoteUsed: Boolean = false,
    val isTraveller: Boolean = false,
    /** True once a Traveller has left the game: no seat, no vote, no threshold. */
    val leftGame: Boolean = false,
    /** Storyteller free tokens only — see [PlacedReminder]. */
    val reminders: List<PlacedReminder> = emptyList(),
    /** Abilities this seat exercises in addition to / instead of its own. */
    val grants: List<AbilityGrant> = emptyList(),
    val notes: List<SeatNote> = emptyList(),
    /** Wall-clock millis when this seat's token was last handed to the player. */
    val tokenShownAt: Long? = null,
    /**
     * Effect-id watermark stamped whenever this seat's `characterId` changes.
     * Standing (innate) effects are ordered by it, so "the Poisoner poisoned the
     * No Dashii on night 1" resolves correctly while a Snake-Charmer-created
     * No Dashii on night 4 starts fresh.
     */
    val standingSince: Long = 0L,

    // ---- migration-only, never read by new code ----
    @SerialName("note") internal val legacyNote: String = "",
    @SerialName("alignmentFlipped") internal val legacyAlignmentFlipped: Boolean = false,
) {
    /** The token a "YOU ARE" card must show. Alias of [Identity.believedCharacterId]. */
    val characterShownToPlayerId: String? get() = shownCharacterId ?: characterId

    /** Seats that have left the game are not seats. */
    val seated: Boolean get() = !leftGame

    @Deprecated(
        "Deleted with WP2. Use Identity.actingRoles(state, lookup, player).",
        ReplaceWith("Identity.actingRoles(state, lookup, this).firstOrNull()?.abilityId"),
    )
    val nightRoleId: String? get() = characterId
}

/** A Fabled in play, with the per-Fabled state the rules need. */
@Serializable
data class FabledEntry(
    val id: String,
    /** Seats this Fabled points at (Revolutionary's pair, Angel's protectee, Djinn's none). */
    val playerIds: List<Long> = emptyList(),
    /** Seats that have used the Fabled's once-per-player affordance (Doomsayer). */
    val spentBy: List<Long> = emptyList(),
    /** Once-per-game Fabled effects (Fibbin, Toymaker's skipped night). */
    val used: Boolean = false,
    /** The storyteller's own wording (Djinn's special rule, Bootlegger's house rules). */
    val note: String = "",
    val addedOnCycle: Int = 0,
    /**
     * Typed keys: "sentinel.outsiderDelta", "stormcatcher.favouredCharacterId",
     * "revolutionary.pair", "spiritofivory.baselineEvil", "toymaker.skipUsed".
     */
    val config: Map<String, String> = emptyMap(),
)

@Serializable
enum class Phase { SETUP, NIGHT, DAY }

/** Everything the storyteller tracks for one game. */
@Serializable
data class GameState(
    val script: Script,
    /** Stable id, stamped at newGame — the key for archived games. */
    val id: String = "",
    val players: List<Player> = emptyList(),
    val phase: Phase = Phase.SETUP,
    /** Night N is followed by day N. */
    val cycle: Int = 1,
    val updatedAt: Long = 0L,

    // ---- history (append-only) ----
    val deaths: List<DeathEvent> = emptyList(),
    val nextDeathId: Long = 1L,
    val nominations: List<Nomination> = emptyList(),
    val executions: List<ExecutionRecord> = emptyList(),
    val ledger: List<LedgerEntry> = emptyList(),
    val nextLedgerId: Long = 1L,
    val identityLog: List<IdentityRecord> = emptyList(),

    // ---- live rules state ----
    val effects: List<Effect> = emptyList(),
    val nextEffectId: Long = 1L,
    val prompts: List<Prompt> = emptyList(),
    val nextPromptId: Long = 1L,
    /** Abilities held by no fixed seat: the Boffin's grant, the Plague Doctor's. */
    val floatingGrants: List<FloatingGrant> = emptyList(),
    /** Tokens that live in the centre of the grimoire, on no seat. */
    val storytellerReminders: List<PlacedReminder> = emptyList(),
    val fabled: List<FabledEntry> = emptyList(),

    // ---- storyteller decisions ----
    /** Bluff sets, keyed by BluffRequirement.key ("demon", "lunatic:7", "snitch:7"). */
    val bluffSets: Map<String, List<String>> = emptyMap(),
    /** Setup choices and secrets that must survive the whole game. See [Decisions]. */
    val decisions: Map<String, String> = emptyMap(),
    /** Day the storyteller has declared final (Ferryman, Angel, Fiddler). */
    val finalDayCycle: Int? = null,

    // ---- night progress ----
    /** Holds [StepKey.token] values. Degrades to bare ability ids for simple steps. */
    val nightStepsDone: Set<String> = emptySet(),

    // ---- computed-and-frozen briefings ----
    /** The dawn briefing, computed BEFORE tokens were swept, so saves are re-openable. */
    val lastDawn: Briefing? = null,
    val lastDusk: Briefing? = null,

    val mastermindDayActive: Boolean = false,
    val storytellerNotes: String = "",
    /** Night-screen dim level, 0 = off, 1 = 55%, 2 = 25%. Persisted, not remembered. */
    val dimLevel: Int = 0,

    // ---- migration-only, never read by new code ----
    @SerialName("demonBluffIds") internal val legacyDemonBluffIds: List<String> = emptyList(),
    @SerialName("fabledIds") internal val legacyFabledIds: List<String> = emptyList(),
) {
    // ---- seats ----
    fun player(id: Long): Player? = players.find { it.id == id }
    val seats: List<Player> get() = players.filter { it.seated }
    val alivePlayers: List<Player> get() = seats.filter { it.alive }
    val aliveNonTravellers: List<Player> get() = seats.filter { it.alive && !it.isTraveller }

    /** Alive seats INCLUDING travellers — the Mayor's count (wiki: "Travellers count"). */
    val aliveCountWithTravellers: Int get() = alivePlayers.size
    /** Alive seats EXCLUDING travellers — the evil-wins-at-2 count and Scarlet Woman's 5+. */
    val aliveCountResidents: Int get() = aliveNonTravellers.size

    /**
     * True when the player is alive by the RULES, which a Zombuul's first death is
     * (they are stored dead and register as dead, but the game is not over).
     */
    fun isTrulyAlive(playerId: Long): Boolean {
        val p = player(playerId) ?: return false
        if (p.alive) return true
        return deaths.lastOrNull { it.playerId == playerId && it.resurrectedAtCycle == null }
            ?.registeredOnly == true
    }

    fun updatePlayer(id: Long, transform: (Player) -> Player): GameState =
        copy(players = players.map { if (it.id == id) transform(it) else it })

    /** The two physical neighbours of a seat, over ALL seats including Travellers. */
    fun seatNeighbours(playerId: Long): List<Player> {
        val i = players.indexOfFirst { it.id == playerId }
        if (i < 0 || players.size < 2) return emptyList()
        return listOf(players[(i - 1 + players.size) % players.size], players[(i + 1) % players.size])
    }

    // ---- derived compatibility accessors (read-only) ----
    val fabledIds: List<String> get() = fabled.map { it.id }
    val demonBluffIds: List<String> get() = bluffSets[BluffRequirement.DEMON_KEY].orEmpty()

    /** Votes needed for an execution. Prefer DayRules.voteRules(...) — this ignores abilities. */
    val executionThreshold: Int get() = (aliveCountWithTravellers + 1) / 2
    /** Votes needed for an exile. Never modified by any ability. */
    val exileThreshold: Int get() = (seats.size + 1) / 2

    companion object {
        /** Nominee id used when the STORYTELLER is nominated (Atheist games). */
        const val STORYTELLER_SEAT_ID: Long = -1L
    }
}

/** Typed accessors over [GameState.decisions]. Keys are stable; do not invent new spellings. */
object Decisions {
    const val XAAN_X = "xaan.X"
    const val BOFFIN_GRANT = "boffin.grant"
    const val ALCHEMIST_GRANT = "alchemist.grant"
    const val MEZEPHELES_WORD = "mezepheles.word"
    const val LUNATIC_DEMON = "lunatic.demon"
    const val AMNESIAC_ABILITY = "amnesiac.ability"
    const val OUTSIDER_BRANCH = "setup.outsiderBranch"
    /** "true" = Travellers count towards the 7+ minion/demon-info threshold. */
    const val COUNT_TRAVELLERS_FOR_INFO = "teensyville.countTravellers"

    fun int(state: GameState, key: String): Int? = state.decisions[key]?.toIntOrNull()
    fun bool(state: GameState, key: String, default: Boolean = false): Boolean =
        state.decisions[key]?.toBooleanStrictOrNull() ?: default
    fun set(state: GameState, key: String, value: String): GameState =
        state.copy(decisions = state.decisions + (key to value))
    fun clear(state: GameState, key: String): GameState =
        state.copy(decisions = state.decisions - key)
}
```

### 2.3 `Effects.kt` — the status model (owned by WP1)

```kotlin
package com.clocktower.engine

import kotlinx.serialization.Serializable

@Serializable
enum class EffectKind {
    // impairing
    DRUNK, POISONED, NO_ABILITY,
    // anti-impairing / ability-granting
    SOBER_HEALTHY,          // Barista — beats every impairment
    HAS_ABILITY,            // Bone Collector, Vigormortis-preserved Minion, Pixie
    // protective (see Deaths.PROTECTS — every kind declares which causes it blocks)
    SAFE_FROM_DEMON,        // Monk SAFE, Soldier (innate)
    CANT_DIE_TONIGHT,       // Innkeeper SAFE
    CANT_DIE,               // Sailor (innate, self), Tea Lady CANNOT DIE
    ONLY_EXECUTION_KILLS,   // Storm Catcher STORMCAUGHT
    SURVIVES_EXECUTION,     // Devil's Advocate
    DAY_IMMUNE,             // Vizier (innate)
    DEATH_TIED_TO,          // Lleech -> host (linkedPlayerId = the host)
    // suppression
    DEMON_CANNOT_KILL,      // Lycanthrope, Princess, Exorcist-silenced Demon, Toymaker
    NO_VOTE, NO_NOMINATE,   // Beggar without a token, Golem spent, Butler under secret voting
    // state
    MAD,                    // Cerenovus / Mutant / Harpy / Pixie — payload = characterId
    REGISTERS_AS,           // Spy, Recluse, Faux Paw, Revolutionary — payload = characterId/team
    SPENT,                  // a once-per-game ability has been used
    MARKER,                 // anything with no rule: Know, Correct, Grandchild, Visitor…
}

/** Display bucket, glyph and pip priority. Derived from [EffectKind] — do not store. */
@Serializable
enum class EffectGroup(val glyph: String, val priority: Int) {
    PENDING_DEATH("†", 0), IMPAIRED("!", 1), PROTECTED("+", 2), MADNESS("M", 3),
    IDENTITY("=", 4), ABILITY("O", 5), INFO("i", 6), MARKER("·", 7);
}

val EffectKind.group: EffectGroup get() = when (this) {
    EffectKind.DRUNK, EffectKind.POISONED, EffectKind.NO_ABILITY -> EffectGroup.IMPAIRED
    EffectKind.SAFE_FROM_DEMON, EffectKind.CANT_DIE_TONIGHT, EffectKind.CANT_DIE,
    EffectKind.ONLY_EXECUTION_KILLS, EffectKind.SURVIVES_EXECUTION, EffectKind.DAY_IMMUNE,
    EffectKind.DEATH_TIED_TO, EffectKind.DEMON_CANNOT_KILL -> EffectGroup.PROTECTED
    EffectKind.MAD -> EffectGroup.MADNESS
    EffectKind.REGISTERS_AS -> EffectGroup.IDENTITY
    EffectKind.SOBER_HEALTHY, EffectKind.HAS_ABILITY, EffectKind.SPENT,
    EffectKind.NO_VOTE, EffectKind.NO_NOMINATE -> EffectGroup.ABILITY
    EffectKind.MARKER -> EffectGroup.MARKER
}

/** When an effect stops applying. `SOURCE_LOSES_ABILITY` is additionally applied on top of every value. */
@Serializable
enum class Until {
    DAWN,                 // "tonight"
    DUSK,                 // "until dusk" / "tonight and tomorrow day"
    DUSK_AFTER_N_DAYS,    // Minstrel (n = 1), Courtier (n = 2 => 3 nights and 3 days)
    ON_SOURCE_STEP,       // consumed at the source's next night step (Pukka's poison)
    EVENT,                // untilEvent: "goodDiesByExecution", "hostDies", "poppyGrowerDies"
    FOREVER,
    MANUAL,               // only the storyteller removes it (night-1 "start knowing" tokens)
}

/**
 * A typed, dated, sourced rule applying to one seat. Effects — not tokens — decide
 * who is drunk, poisoned, protected, mad or spent.
 */
@Serializable
data class Effect(
    /** Monotonic from [GameState.nextEffectId]. Doubles as the resolution-order key. */
    val id: Long,
    val kind: EffectKind,
    val targetId: Long,
    /** Lleech host, Grandmother grandchild, Harpy's 2nd. */
    val linkedPlayerId: Long? = null,
    /** Character payload: the Cerenovus's mad character, the REGISTERS_AS character. */
    val characterId: String? = null,
    /** Who created it. "" = storyteller / house rule. */
    val sourceCharacterId: String,
    /** The seat whose ability sustains it. Null = no living source to check. */
    val sourcePlayerId: Long? = null,
    val until: Until,
    /** Absolute cycle at which a DAWN/DUSK/DUSK_AFTER_N_DAYS expiry fires. */
    val untilCycle: Int? = null,
    val untilEvent: String? = null,
    /** False only for effects that explicitly outlive their source (Sweetheart, Puzzlemaster). */
    val endsWithSource: Boolean = true,
    /** Grimoire token text. "" renders no token (Soldier, Sailor, Vizier are innate). */
    val label: String = "",
    /** Storyteller-visible explanation, shown on tap. */
    val note: String = "",
    val createdCycle: Int,
    val createdAtNight: Boolean,
    /** The DeathEvent / night action that created it, for exact rollback by `revive`. */
    val causeEventId: Long? = null,
    /** Storyteller override: keep the token, suppress the rule (the physical "turn it over"). */
    val suspended: Boolean = false,
)

/** One innate rule a character's mere presence creates. Evaluated on every query, never stored. */
class StandingRule(
    val characterId: String,
    val emit: (state: GameState, holder: Player, lookup: (String) -> Character?) -> List<Effect>,
)

/** Why a seat's ability is not working, in storyteller prose. */
data class Reason(val effect: Effect, val text: String)

object Status {

    /** Stored + derived effects on this seat, with expiry and suspension applied. */
    fun effectsOn(state: GameState, lookup: (String) -> Character?, playerId: Long): List<Effect>

    /**
     * Every reason this seat's ability is not working, in creation order.
     * Empty when the ability works. A live `SOBER_HEALTHY` (Barista) always wins
     * and returns an empty list.
     */
    fun impairment(state: GameState, lookup: (String) -> Character?, playerId: Long): List<Reason>

    fun isImpaired(state: GameState, lookup: (String) -> Character?, playerId: Long): Boolean =
        impairment(state, lookup, playerId).isNotEmpty()

    /**
     * True when this seat's ability functions right now: alive (or on the
     * keeps-ability-when-dead list, or holding a live HAS_ABILITY) and unimpaired.
     */
    fun hasAbility(state: GameState, lookup: (String) -> Character?, playerId: Long): Boolean

    /** True when one specific granted/own role works — honours `alwaysFalse` and `worksWhileImpaired`. */
    fun roleWorks(state: GameState, lookup: (String) -> Character?, role: ActingRole): Boolean

    fun protections(state: GameState, lookup: (String) -> Character?, playerId: Long): List<Effect>

    /** SAFE_FROM_DEMON blocks non-kill Demon harm too (No Dashii poison on a Soldier). */
    fun demonHarmBlocked(state: GameState, lookup: (String) -> Character?, playerId: Long): Boolean
}
```

**The recursion (`abilityWorks`), verbatim — implement exactly this.** Every effect ends
when its source loses their ability; that rule is recursive and is the single most
load-bearing thing in the engine.

```
abilityWorks(pid, cap)   // memoised on (pid, cap)
  1. p = player(pid); c = character(p)
  2. if (!p.alive && !keepsAbilityWhenDead(c) && no active HAS_ABILITY on p) return false
  3. actives = effectsOn(pid).filter { it.id < cap && !it.suspended && !expired(it, state) }
  4. if (actives.any { it.kind == SOBER_HEALTHY && active(it, cap) }) return true
  5. return actives.none { it.kind in IMPAIRING && active(it, cap) }

active(e, cap) =
    !expired(e, state) && !e.suspended &&
    (!e.endsWithSource || e.sourcePlayerId == null || abilityWorks(e.sourcePlayerId, min(cap, e.id)))
```

- **Termination**: `cap` strictly decreases (`e.id < cap`), so recursion is bounded by the
  effect count. Memoise per query.
- **Paradox** (two effects with equal ids poisoning each other — only reachable via two
  derived effects created in one swap): mark the query `paradox`, resolve as "both active",
  and raise a `Prompt(kind = DECIDE)`: *"Paradox: A and B poison each other. Tap the one
  whose ability works."* The answer is persisted as `Effect.suspended`.
- `keepsAbilityWhenDead` is a registry flag (`CharacterRule.keepsAbilityWhenDead`), not a
  hard-coded id set: `recluse`, `spy`, `ravenkeeper`, `sweetheart`, `moonchild`, `klutz`,
  `barber`, `hatter`, `poppygrower`, `plaguedoctor`, `heretic`, `atheist`, `politician`,
  `banshee`, `zealot`, `puzzlemaster` — plus anything holding a live `HAS_ABILITY` effect.

**Standing rules to implement** (each is researched in the matching character file):

| Character | Emits |
|---|---|
| `soldier` | `SAFE_FROM_DEMON` on self |
| `sailor` | `CANT_DIE` on self |
| `tealady` | `CANT_DIE` on each **alive** neighbour, only when both register good |
| `vizier` | `DAY_IMMUNE` on self |
| `nodashii` | `POISONED` on the nearest Townsfolk neighbour each way (skipping `demonHarmBlocked`) |
| `vigormortis` | `POISONED` on one Townsfolk neighbour of each seat it marked `HAS_ABILITY` (ST picks — lead D12) |
| `widow` | keeps its stored `POISONED` alive only while the Widow has an ability (falls out of the recursion for free) |
| `xaan` | on `cycle == X`, `POISONED` on every Townsfolk, `until = DUSK` — **only while alive** |
| `lleech` | `DEATH_TIED_TO` on self, linked to the host |
| `lycanthrope` | `DEMON_CANNOT_KILL` on the alive Demon while the Faux Paw seat is chosen |
| `drunk`, `marionette`, `lunatic` | `NO_ABILITY` on self, `endsWithSource = false` |
| `stormcatcher` (fabled) | `ONLY_EXECUTION_KILLS` on the named character's seat |

### 2.3b `Registration.kt` — misregistration as a set (owned by WP1)

```kotlin
/**
 * What a seat registers as, right now, to an asking ability. ALWAYS a set:
 * Legion registers as both DEMON and MINION; a Lil' Monsta babysitter adds DEMON.
 * Every "is this player a Demon / Minion / evil" question goes through here.
 */
object Registration {
    fun registersAs(state: GameState, lookup: (String) -> Character?, player: Player): Set<Team>
    fun registersEvil(state: GameState, lookup: (String) -> Character?, player: Player): Boolean
    /** The character this seat registers as to [askedBy], honouring REGISTERS_AS rulings. */
    fun registersAsCharacter(
        state: GameState, lookup: (String) -> Character?, player: Player, askedBy: String,
    ): String?
    /** True alignment: explicit override, else the character's natural team. */
    fun alignment(state: GameState, lookup: (String) -> Character?, player: Player): Alignment
}
```

### 2.4 `Tokens.kt` — token lifecycle and rendering (owned by WP1)

```kotlin
/**
 * The declarative replacement for `EXPIRES_AT_DAWN` / `EXPIRES_AT_DUSK`.
 * Every rule in this registry MUST name a (sourceId, label) that exists in
 * `characters.json` — a GameDataTest asserts it, case-insensitively.
 */
data class TokenRule(
    val sourceId: String,
    /** Official Title Case label. Matched case-insensitively everywhere. */
    val label: String,
    /** The effect this token renders. Null = a pure marker with no rule. */
    val effect: EffectKind? = null,
    val until: Until = Until.FOREVER,
    /** For DUSK_AFTER_N_DAYS. */
    val untilDays: Int = 0,
    val untilEvent: String = "",
    /** How many physical copies the character owns. From `characters.json`, N-listed. */
    val copies: Int = 1,
    val endsWithSource: Boolean = true,
    /** True when the label alone means "this seat is drunk/poisoned" (lead D46). */
    val impairs: Boolean = false,
    val protects: Boolean = false,
    /** Countdown chain: "Drunk 1" -> "Drunk 2" -> "Drunk 3" -> gone. Advanced at dusk. */
    val countdownNext: String? = null,
    /** Two-state pair that can never coexist: Flowergirl, Town Crier (lead D52). */
    val mutexGroup: String = "",
    /** Tokens in one group replace each other: Leviathan "Day 1".."Day 5" (lead D33). */
    val exclusiveGroup: String = "",
    /** Lives in the centre of the grimoire, not on a seat. */
    val grimoireCentre: Boolean = false,
)

object Tokens {
    /** Canonical case-insensitive key. The ONLY legal way to compare tokens. */
    fun key(sourceId: String, label: String): String =
        Character.normalizeId(sourceId) + "/" + label.trim().lowercase()

    fun rule(sourceId: String, label: String): TokenRule?
    fun rule(r: PlacedReminder): TokenRule? = rule(r.sourceId, r.label)

    /** Derived from the registry — never hand-maintained. */
    val expiringAtDawn: List<TokenRule>
    val expiringAtDusk: List<TokenRule>

    /** Advances every countdown chain. Called from `Phases.advancePhase` at dusk. */
    fun advanceCountdowns(state: GameState, at: Until): GameState
}

object Effects {
    /**
     * Places an effect and (when `TokenRule.label` is non-empty) makes it render as a
     * token. Honours `copies`, `mutexGroup` and `exclusiveGroup`: the oldest copy is
     * displaced, never silently lost — the displaced placement is returned for the
     * snackbar and the undo label.
     */
    fun place(
        state: GameState,
        target: Long,
        kind: EffectKind,
        sourceCharacterId: String,
        sourcePlayerId: Long?,
        until: Until,
        label: String = "",
        note: String = "",
        characterId: String? = null,
        linkedPlayerId: Long? = null,
        endsWithSource: Boolean = true,
        causeEventId: Long? = null,
    ): Placement

    data class Placement(val state: GameState, val effect: Effect, val displaced: Effect? = null)

    fun remove(state: GameState, effectId: Long): GameState
    fun suspend(state: GameState, effectId: Long, suspended: Boolean): GameState
    /** Drops every effect whose `sourceCharacterId` is [characterId] on any seat. */
    fun removeBySource(state: GameState, characterId: String): GameState
    /** Drops every effect and prompt stamped with [causeEventId] — used by `revive`. */
    fun rollback(state: GameState, causeEventId: Long): GameState

    /**
     * Re-evaluates `endsWithSource` teardown and standing rules. Call after every
     * kill, resurrect, character change, and at each phase boundary.
     */
    fun reconcile(state: GameState, lookup: (String) -> Character?): GameState

    /** The tokens to draw for one seat: effect-backed first, then storyteller free tokens. */
    fun rendered(state: GameState, lookup: (String) -> Character?, playerId: Long): List<RenderedToken>
}

/** One token as the grimoire draws it. */
data class RenderedToken(
    val sourceId: String,
    val label: String,
    val group: EffectGroup,
    val effectId: Long? = null,
    /** Null for a storyteller free token. */
    val effect: Effect? = null,
    val expiryText: String = "",       // "expires at dusk", "placed N3"
    val derived: Boolean = false,      // No Dashii-style: no physical token, dotted ring
    val suspended: Boolean = false,
    val note: String = "",
)
```

**Token-lifecycle rules that must be encoded** (from night-engine §4, day-engine §F,
data-accuracy §6.1, lead D5/D9/D14/D33/D52):

- Generic seat-sheet tokens get `sourceId = "st"`, **never `""`**. `st/Poisoned` and
  `st/Drunk` → `Until.DUSK`; `st/Protected` → `DAWN`; `st/Mad` → `DUSK`; everything else
  `FOREVER`. *A `sourceId` of `""` with a "poison" label is the current permanent-poison
  bug and the most likely root cause of the user's Devil's Advocate report.*
- `Until.DAWN` additions: `undertaker/Died Today`, `godfather/Died Today`,
  `zombuul/Died Today`, `flowergirl/*` (reset, not delete — `mutexGroup`),
  `towncrier/*` (same), `juggler/Correct` ×5, `princess/Doesn't Kill`,
  `barber/Haircuts Tonight`, `hatter/Tea Party Tonight`, `poppygrower/Evil Wakes`,
  `po/3 Attacks`, `acrobat/Chosen`, `mezepheles/Turns Evil`, `cacklejack/Not Me`,
  `organgrinder/About To Die`, `legion/About To Die`, `mathematician/Abnormal`.
- `Until.DUSK` additions: `thief/Negative Vote`, `bureaucrat/3 Votes`,
  `barista/Sober & Healthy`, `barista/Acts Twice`, `bonecollector/Has Ability`,
  `goon/Drunk`, `xaan/X`, `organgrinder/Drunk`, `ventriloquist/Mad`,
  `psychopath/Used Today`, `beggar/Vote Token`.
- **Never expire**: `golem/May Not Nominate`, `judge/No Ability`, `banshee/Has Ability`,
  `damsel/Guess Used`, `leviathan/Good Player Executed` (×2, multi-copy),
  `boomdandy/Exploded`, `fanggu/Once` (grimoire centre), every `SPENT` mark.
- **Never swept**: night-1 "start knowing" tokens (`washerwoman/Townsfolk`,
  `washerwoman/Wrong`, `librarian/*`, `investigator/*`) — `Until.MANUAL`, rendered dimmed
  as spent, cleared on demand (lead D9).
- `courtier/Drunk 1 → Drunk 2 → Drunk 3 → gone` counts **up** (official), advanced at dusk.
  Same mechanism for `summoner/Night N`, `xaan/Night N`, `leviathan/Day N`, `riot/Day N`.
- `minstrel/Everyone Is Drunk` is a **grimoire-centre** token (`grimoireCentre = true`),
  never on a seat, and must never be read as an impairment of the seat it sits on.

---

### 2.5 `Ledger.kt` — the one append-only record (owned by WP3)

```kotlin
package com.clocktower.engine

import kotlinx.serialization.Serializable

@Serializable
enum class LedgerKind {
    /** A character chose someone/something at night. Powers "different to last night". */
    CHOICE,
    /** Information actually delivered to a player — true or false. */
    TOLD,
    /** Something said in public during the day (Gossip, Juggler, Slayer, a plain claim). */
    STATEMENT,
    /** A private day-time storyteller conversation (Savant, Artist, Fisherman, Amnesiac). */
    PRIVATE,
    /** A storyteller decision that must stay consistent (misregistration, madness, malfunction). */
    RULING,
    /** Something the storyteller owes the table. `announcePending` until said out loud. */
    ANNOUNCE,
    /** A once-per-game ability was used. */
    SPENT,
    /** A seat woke tonight. `byStoryteller`/`genuine` distinguish own-ability wakes. */
    WOKE,
    /** An ability malfunctioned (Mathematician's count). */
    MALFUNCTION,
    /** Opens/closes an impairment window for one seat (lead D41). */
    IMPAIRMENT_SPAN,
    /** Free text with no other structure. */
    NOTE,
}

/** UNJUDGED until the storyteller rules. TRUE doubles as "correct" for guesses. */
@Serializable
enum class Verdict {
    UNJUDGED, TRUE, FALSE,
    /** Savant / Artist two-sided entries. */
    A_TRUE, B_TRUE, BOTH_TRUE, NEITHER_TRUE,
    /** The storyteller exercised a free choice; neither true nor false. */
    ST_CHOICE,
}

/**
 * ONE record type for everything that happened or was said. Replaces ChoiceRecord,
 * NightRecord, DayEntry, DayAct, PublicStatement, PublicClaim, Announcement,
 * Misregistration, Malfunction, WakeEvent and SeatEvent.
 *
 * The whole ledger MUST work as free text with everything else empty — recording
 * "Bo said Fay is the Imp" in a game with no Gossip in play is the user's literal request.
 */
@Serializable
data class LedgerEntry(
    val id: Long,
    /** Same numbering as [DeathEvent]: day N follows night N. */
    val cycle: Int,
    val atNight: Boolean,
    val kind: LedgerKind,
    /**
     * Character id ("gossip", "devilsadvocate"), a night marker ("DAWN"), or a
     * pseudo-source: "claim", "misregister", "malfunction", "note", "st".
     * This is the fine-grained taxonomy — there is no second enum.
     */
    val sourceId: String = "",
    /** Seat that acted / spoke / was told. Null for storyteller-only entries. */
    val actorId: Long? = null,
    /** Seats the entry is about: the DA's target, the Juggler's guessed seats. */
    val targetIds: List<Long> = emptyList(),
    /** Second seat list where a kind needs two: Alsaahir's Minions vs Demons (lead D42). */
    val targetIdsB: List<Long> = emptyList(),
    /** Characters named. Parallel to [targetIds] where both are used (Juggler guess i). */
    val characterIds: List<String> = emptyList(),
    /** The words: the Gossip's statement, the Artist's question, the Savant's statement A. */
    val text: String = "",
    /** Second half of a two-sided entry: Savant statement B, the Artist's answer. */
    val textB: String = "",
    /** What the storyteller actually showed: "3", "YES", "Ravenkeeper", "warm". */
    val shown: String = "",
    val verdict: Verdict = Verdict.UNJUDGED,
    /** Integer payload: Juggler correct count, Yaggababble phrase count. */
    val count: Int? = null,
    /** Whether the ACTOR's ability was malfunctioning when this happened. Snapshot, not live. */
    val impaired: Boolean = false,
    /** True when the app believes the actor really holds [sourceId]; false for a bluffing claimant. */
    val genuine: Boolean = true,
    /**
     * The STORYTELLER made this choice, not the player. The Goon needs it
     * ("the 1st player to choose you each night") and so does every
     * storyteller-substituted pick. (lead D1)
     */
    val byStoryteller: Boolean = false,
    /** Cycle on which a later step consumed this entry (Gossip resolved, Juggler revealed). */
    val resolvedCycle: Int? = null,
    /** ANNOUNCE only: true while the storyteller still owes the table this sentence. */
    val announcePending: Boolean = false,
)

/** Append-only writers. Each stamps cycle/atNight from [state] and allocates the id. */
object Ledger {
    fun record(state: GameState, entry: LedgerEntry): GameState
    fun choice(state: GameState, sourceId: String, actorId: Long?, targetIds: List<Long>,
               characterIds: List<String> = emptyList(), impaired: Boolean = false,
               byStoryteller: Boolean = false): GameState
    fun told(state: GameState, playerId: Long, sourceId: String, shown: String,
             impaired: Boolean = false, text: String = ""): GameState
    fun statement(state: GameState, speakerId: Long?, sourceId: String, text: String,
                  targetIds: List<Long> = emptyList(), characterIds: List<String> = emptyList(),
                  genuine: Boolean = true): GameState
    fun private(state: GameState, playerId: Long, sourceId: String, text: String, shown: String): GameState
    fun ruling(state: GameState, sourceId: String, playerId: Long?, text: String,
               characterIds: List<String> = emptyList()): GameState
    fun announce(state: GameState, text: String, sourceId: String = ""): GameState
    fun woke(state: GameState, playerId: Long, sourceId: String, ownAbility: Boolean): GameState
    fun malfunction(state: GameState, playerId: Long, sourceId: String, reason: String): GameState
    fun spent(state: GameState, sourceId: String, actorId: Long): GameState

    fun markAnnounced(state: GameState, id: Long): GameState
    fun setVerdict(state: GameState, id: Long, verdict: Verdict): GameState
    fun resolve(state: GameState, id: Long): GameState        // sets resolvedCycle = state.cycle
    fun edit(state: GameState, id: Long, transform: (LedgerEntry) -> LedgerEntry): GameState
    fun delete(state: GameState, id: Long): GameState
}

/** Read-only queries. Nothing here is stored; everything derives from the ledger. */
object Memory {
    /** The most recent CHOICE by [sourceId] (optionally by one holder) strictly before [beforeCycle]. */
    fun lastChoice(state: GameState, sourceId: String, holderId: Long? = null,
                   beforeCycle: Int = state.cycle): LedgerEntry?
    /** Seats [sourceId] may NOT pick tonight because of a "different to last night" clause. */
    fun forbiddenTargets(state: GameState, sourceId: String, holderId: Long? = null): Set<Long>
    /** Every seat [sourceId] has ever chosen — "cannot learn the same evil player twice". */
    fun everChosen(state: GameState, sourceId: String, holderId: Long? = null): Set<Long>
    fun choseNobodyLastNight(state: GameState, sourceId: String, holderId: Long? = null): Boolean
    fun isSpent(state: GameState, sourceId: String, actorId: Long? = null): Boolean
    fun statementsOn(state: GameState, day: Int, sourceId: String? = null,
                     speakerId: Long? = null): List<LedgerEntry>
    fun unresolved(state: GameState, sourceId: String, day: Int): List<LedgerEntry>
    fun pendingAnnouncements(state: GameState): List<LedgerEntry>
    /** Everything ever told to, chosen by, or said by one seat — merged with deaths,
     *  nominations, votes and executions. This is the seat sheet's History section. */
    fun forPlayer(state: GameState, playerId: Long): List<LedgerEntry>
    fun ruling(state: GameState, playerId: Long, askedBy: String): LedgerEntry?
    fun typesSeen(state: GameState, lookup: (String) -> Character?, actorId: Long): List<Team>
    fun cyclesSince(state: GameState, playerId: Long, sourceId: String, label: String): Int?
    /** Was this seat impaired at any point in [fromCycle]..[toCycle]? Reads IMPAIRMENT_SPAN. */
    fun wasImpairedDuring(state: GameState, playerId: Long, fromCycle: Int, toCycle: Int): Boolean
}
```

### 2.6 `Prompts.kt` and `Deaths.kt` — obligations and the kill funnel (owned by WP1)

```kotlin
/** Where an obligation or briefing item surfaces. One enum for prompts, briefings and effects. */
@Serializable
enum class BriefingSlot { NOW, TONIGHT, DAWN, DAY_START, NOMINATION, EXECUTION, DUSK }

@Serializable
enum class PromptKind {
    ANNOUNCE, CHOOSE_PLAYER, CHOOSE_CHARACTER, PLACE_EFFECT,
    RESOLVE_KILL, RUN_FIRST_NIGHT, RUN_STEP, INFO, DECIDE,
}

/** A deferred obligation the engine created. `resolved` retires it. */
@Serializable
data class Prompt(
    val id: Long,
    val at: BriefingSlot,
    val kind: PromptKind,
    /** Character whose ability this is. */
    val sourceId: String,
    val subjectPlayerId: Long? = null,
    val targetIds: List<Long> = emptyList(),
    val characterIds: List<String> = emptyList(),
    /** Imperative, storyteller voice, ready to read or act on. */
    val title: String,
    val detail: String = "",
    /** Cycle it comes due; null = the next occurrence of [at]. */
    val dueCycle: Int? = null,
    /** For `at = TONIGHT`: which night-order slot to insert the step at. */
    val stepSlotId: String = "",
    /** The DeathEvent / action that created it, so `revive` can roll it back exactly. */
    val causeEventId: Long? = null,
    val optional: Boolean = false,
    val resolved: Boolean = false,
    val resolvedCycle: Int? = null,
)

object Prompts {
    fun queue(state: GameState, prompt: Prompt): GameState
    fun resolve(state: GameState, id: Long): GameState
    fun dismiss(state: GameState, id: Long): GameState
    fun due(state: GameState, slot: BriefingSlot): List<Prompt>
    /** Prompts that must become night steps tonight. Consumed by NightPlan. */
    fun forTonight(state: GameState): List<Prompt>
}
```

```kotlin
/**
 * The complete cause taxonomy (lead D29). Serialised by name — append only.
 * The first five values are the legacy set and MUST keep their spelling.
 */
@Serializable
enum class DeathCause {
    EXECUTION,
    @Deprecated("Use DEMON_KILL") DEMON,
    @Deprecated("Use DEMON_KILL / EVIL_ABILITY / DAY_ABILITY") OTHER_NIGHT_DEATH,
    EXILE,
    STORYTELLER,
    // ---- added ----
    /** Any Demon's own ability, including deferred harm (Pukka, No Dashii, Vigormortis). */
    DEMON_KILL,
    /** Assassin, Godfather, Witch, Mezepheles, Harpy, Boomdandy, Fearmonger. */
    EVIL_ABILITY,
    /** Gossip, Lycanthrope, Moonchild, Gambler, Tinker, Harlot, Sage-adjacent. */
    GOOD_ABILITY,
    /** Slayer, Psychopath, Golem, Virgin's collateral, Gangster, Gunslinger, Judge. */
    DAY_ABILITY,
    /** Traveller-only powers where the distinction matters. */
    TRAVELLER_ABILITY,
}

/** The input to the kill funnel. */
@Serializable
data class KillCause(
    val cause: DeathCause,
    val sourceCharacterId: String? = null,
    val sourcePlayerId: Long? = null,
    /** Assassin only: nothing stops it. */
    val ignoresProtection: Boolean = false,
    /**
     * Set on Lil' Monsta / Legion / Riot / Yaggababble / Al-Hadikhia kills, where the
     * wiki does not rule whether Sage / Grandmother / Choirboy fire. The kill panel
     * shows one toggle, defaulting to yes. See §6 Q3.
     */
    val demonKillUncertain: Boolean = false,
)

/** The complete record of one death. Supersedes `DeathRecord` (kept as a typealias). */
@Serializable
data class DeathEvent(
    val id: Long = 0,
    val playerId: Long,
    /** Cycle number; keeps the legacy field name so old saves decode. */
    val day: Int,
    val atNight: Boolean,
    val cause: DeathCause,
    val killerCharacterId: String = "",
    val killerPlayerId: Long? = null,
    /** Snapshots — later character changes must never rewrite a death. */
    val characterIdAtDeath: String? = null,
    val teamAtDeath: Team? = null,
    val evilAtDeath: Boolean = false,
    val abilityImpairedAtDeath: Boolean? = null,
    /** Restored by `revive`. */
    val ghostVoteUsedBeforeDeath: Boolean = false,
    /** Zombuul's first death: stored dead, but the game is not over (lead D6). */
    val registeredOnly: Boolean = false,
    /** Legacy flag, kept for old saves. New code reads [resurrectedAtCycle]. */
    val resurrected: Boolean = false,
    val resurrectedAtCycle: Int? = null,
)

typealias DeathRecord = DeathEvent

/** What the funnel decided. Rendered by KillSheet BEFORE it is applied. */
sealed interface KillOutcome {
    /** Nothing stops it. */
    data class Dies(val reason: String = "") : KillOutcome
    /** Deterministic block. [announce] is the exact line to say out loud. */
    data class Prevented(val by: Effect?, val reason: String, val announce: String) : KillOutcome
    /** The Zombuul's first death: stored dead, registers dead, game continues. */
    data class RegistersDead(val reason: String) : KillOutcome
    /** Mayor bounce, Scapegoat substitution: the death moves. */
    data class Redirect(val to: List<Long>, val reason: String, val mandatory: Boolean) : KillOutcome
    /** A "might" ability — Pacifist, Mayor, Scapegoat, Deviant. The ST decides EVERY time. */
    data class Choice(val question: String, val options: List<KillChoiceOption>) : KillOutcome
    /** The Fool: wraps a Prevented and spends the ability. */
    data class Spends(val inner: KillOutcome, val sourceId: String) : KillOutcome
    /** "A dead player cannot die again." Still counts as the day's execution. */
    data object AlreadyDead : KillOutcome
}

data class KillChoiceOption(val id: String, val label: String, val outcome: KillOutcome)

/** The result of applying the funnel. */
data class DeathAttempt(
    val state: GameState,
    val outcome: KillOutcome,
    /** Null when nobody died. */
    val event: DeathEvent? = null,
    /** Obligations the death created, already queued in [state]. */
    val prompts: List<Prompt> = emptyList(),
)

object Deaths {

    /**
     * PURE preview of what would happen. Rendered by KillSheet, the night step's
     * consequence line and the execution confirmation sheet. No state change.
     */
    fun killOutcome(
        state: GameState,
        lookup: (String) -> Character?,
        targetId: Long,
        cause: KillCause,
    ): KillOutcome

    /**
     * THE kill funnel. Every path that ends a life calls this — day execution,
     * dusk guard, seat sheet, night action, on-death chains. Applies the outcome,
     * writes the DeathEvent (even for a prevented death, as a ledger RULING),
     * runs `Effects.reconcile`, and fires every on-death trigger exactly once.
     *
     * [optionId] answers a previous `KillOutcome.Choice`; pass "" the first time.
     */
    fun attempt(
        state: GameState,
        lookup: (String) -> Character?,
        targetId: Long,
        cause: KillCause,
        optionId: String = "",
    ): DeathAttempt

    /**
     * The player lives again (Professor, Shabaloth, Bone Collector). Rules:
     * they regain their ability, INCLUDING a spent once-per-game (Glossary),
     * except the Virgin's first-nomination flag, which is a historical fact (lead D7).
     * Queues RUN_FIRST_NIGHT for "start knowing" / first-night-only abilities and
     * an ANNOUNCE ledger entry: "X is alive again." (do not say why).
     */
    fun resurrect(state: GameState, lookup: (String) -> Character?, playerId: Long): GameState

    /**
     * Undo a mis-entered death: drops the newest DeathEvent for this seat and every
     * Effect and Prompt whose `causeEventId` matches it; restores `ghostVoteUsed`.
     */
    fun revive(state: GameState, playerId: Long): GameState

    fun toggleGhostVote(state: GameState, playerId: Long): GameState

    /** Which causes each protective effect blocks (lead D29). The table, not prose. */
    val PROTECTS: Map<EffectKind, Set<DeathCause>>
}
```

**`killOutcome` precedence — first match wins. Implement exactly this order.**
Every protection is considered only when `Status.hasAbility(its source)`, which the
recursion in §2.3 gives for free.

```
 0. target not alive, and not a registeredOnly Zombuul      -> AlreadyDead
 1. cause.ignoresProtection                                 -> Dies         (Assassin)
 2. source carries DEMON_CANNOT_KILL and cause == DEMON_KILL-> Prevented    (Lycanthrope, Princess,
                                                                             Exorcised Demon, Toymaker)
 3. DEATH_TIED_TO with a living linked host                 -> Prevented    (Lleech)
 4. DAY_IMMUNE and !atNight                                 -> Prevented    (Vizier)
 5. ONLY_EXECUTION_KILLS and cause != EXECUTION             -> Prevented    (Storm Catcher)
 6. CANT_DIE                                                -> Prevented    (Sailor, Tea Lady)
 7. CANT_DIE_TONIGHT and atNight                            -> Prevented    (Innkeeper)
 8. SAFE_FROM_DEMON and cause == DEMON_KILL                 -> Prevented    (Monk, Soldier)
 9. SURVIVES_EXECUTION and cause == EXECUTION               -> Prevented    (Devil's Advocate)
10. cause == EXECUTION, target registers good,
    a Pacifist has their ability                            -> Choice       (dies / lives)
11. cause == EXILE, target is a Deviant marked funny        -> Choice
12. atNight, cause != EXECUTION, target is a Mayor
    with their ability                                      -> Choice(dies / Redirect(others))
13. cause == EXECUTION, a Scapegoat of the target's
    registered alignment is alive with their ability        -> Choice(dies / Redirect(scapegoat))
14. target is a Zombuul with no prior death, with ability   -> RegistersDead
15. target is a Fool with no SPENT effect, with ability     -> Spends(Prevented)
16. otherwise                                               -> Dies
```

Order rationale, each rules-backed: the Assassin is **first** so nothing else is evaluated;
`DEMON_CANNOT_KILL` is checked on the **source**, before any target protection, so a
deferred Pukka kill obeys the Lycanthrope (lead D36); the Fool is **last** so other
protections take precedence and the once-per-game is not consumed; the Mayor sits *after*
the blocks so a Monk-protected Mayor produces "nobody dies", not a redirect.
`Choice` outcomes render as **buttons, never as advice** — "might" is a decision the app
must *ask*, not *explain*.

**On-death triggers** live in the registry (`CharacterRule.onDeath`, §2.9), not in a
`when` block, and `Deaths.attempt` fires them. Every trigger's gate additionally requires
`Status.hasAbility(holder)` unless the character's text says "even if dead" (lead D35).

---

### 2.7 `Execution.kt` — the execution funnel (owned by WP3)

```kotlin
@Serializable
enum class ExecutionOutcome { DIED, SURVIVED, NO_EXECUTION }

/** How the execution was decided — for the log and for rules that bypass the tally. */
@Serializable
enum class ExecutionVia { VOTE, VIRGIN, VIZIER, JUDGE, PSYCHOPATH, RIOT, STORYTELLER }

/**
 * Every execution, INCLUDING days on which nobody was executed. This list is the
 * single "day is closed" signal (lead D30) — there is no boolean anywhere.
 *
 * An execution that kills nobody is still an execution: Vortox, Mayor, Leviathan,
 * Goblin, Boomdandy and the Undertaker all hinge on the distinction.
 */
@Serializable
data class ExecutionRecord(
    val day: Int,
    val outcome: ExecutionOutcome,
    /** Null only when outcome == NO_EXECUTION. May be [GameState.STORYTELLER_SEAT_ID]. */
    val playerId: Long? = null,
    /** Who nominated them — Fearmonger, Psychopath roshambo, Town Crier, the log. */
    val nominatorId: Long? = null,
    /** Index into `state.nominations` for the nomination this resolved. */
    val nominationIndex: Int? = null,
    /** The DeathEvent this execution produced, when it killed someone. */
    val deathEventId: Long? = null,
    /**
     * Character credited with the save, for SURVIVED: "devilsadvocate", "pacifist",
     * "fool", "sailor", "tealady", "vizier", "zombuul", "psychopath", "mayor",
     * "scapegoat", "alreadyDead". "" for a bare storyteller decision.
     */
    val preventedBy: String = "",
    /** Seat that died instead (Scapegoat). The execution still belongs to [playerId]. */
    val diedInsteadId: Long? = null,
    val via: ExecutionVia = ExecutionVia.VOTE,
    /** Snapshots so later character/alignment changes cannot rewrite history. */
    val characterIdAtExecution: String? = null,
    val wasEvilAtExecution: Boolean? = null,
    val abilityImpairedAtExecution: Boolean? = null,
    /** Weighted tally and threshold at the moment of the decision. */
    val tally: Int = 0,
    val threshold: Int = 0,
)

/** A consequence the storyteller must confirm after an execution resolves. */
@Serializable
data class ExecutionConsequence(
    val sourceId: String,
    /** One imperative line, storyteller voice. */
    val headline: String,
    val detail: String = "",
    val options: List<TriggerOption> = emptyList(),
    /** The ability may not work (drunk/poisoned/dead/spent) — the ST decides anyway. */
    val impaired: Boolean = false,
)

object Execution {

    /**
     * THE execution funnel. Every "Execute" button in the app calls this:
     * DayScreen block banner, DayScreen nomination row, GameShell dusk guard,
     * SeatSheet, and any registry-driven auto-execution (Virgin, Vizier, Judge).
     *
     * Order of operations:
     *  1. Refuse for a Traveller (travellers are exiled, never executed).
     *  2. Refuse when `executionSpent` and not `secondExecutionAllowed`, unless [force].
     *  3. Snapshot character/alignment/impairment/tally/threshold.
     *  4. Append the ExecutionRecord ALWAYS — before any kill, so an aborted kill
     *     still leaves the execution recorded.
     *  5. Route the death through `Deaths.attempt(cause = EXECUTION)`; the funnel's
     *     outcome decides DIED vs SURVIVED and fills `preventedBy`/`diedInsteadId`.
     *  6. Place ("undertaker", "Died Today") on the seat that actually died, if any.
     */
    fun execute(
        state: GameState,
        lookup: (String) -> Character?,
        playerId: Long,
        nominatorId: Long? = null,
        via: ExecutionVia = ExecutionVia.VOTE,
        nominationIndex: Int? = null,
        force: Boolean = false,
    ): GameState

    /** Records that today had no execution. Idempotent; replaced if an execution follows. */
    fun noExecution(state: GameState): GameState

    /** Exile a Traveller. Never an execution; never affected by any ability. */
    fun exile(state: GameState, lookup: (String) -> Character?, playerId: Long): GameState

    /**
     * What the storyteller must confirm now. Covers at minimum: Devil's Advocate,
     * Pacifist, Fool, Sailor, Tea Lady, Zombuul first death, Vizier immunity,
     * Psychopath roshambo, Scapegoat substitution, Mayor bounce, Saint, Goblin claim,
     * Fearmonger, Evil Twin, Minstrel, Mastermind, Leviathan counter, Boomdandy,
     * Cannibal Lunch, Undertaker Died Today, Godfather "an Outsider died today".
     */
    fun consequences(
        state: GameState, lookup: (String) -> Character?, record: ExecutionRecord,
    ): List<ExecutionConsequence>
}
```

### 2.8 `DayRules.kt` — nomination, voting, day predicates, endings (owned by WP3)

```kotlin
/** A frozen snapshot of how ONE nomination was voted. Persisted on the Nomination (lead D27). */
@Serializable
data class VoteRules(
    val eligibleVoterIds: List<Long>,
    val threshold: Int,
    /** False under a sober Voudon and for every exile. */
    val spendsGhostVotes: Boolean,
    /** Per-voter weight. Absent = 1. Bureaucrat 3, Thief -1, Banshee 2. */
    val weights: Map<Long, Int> = emptyMap(),
    /** One line per modifier applied, for the log and the tally explanation. */
    val reasons: List<String> = emptyList(),
) {
    fun weightOf(playerId: Long): Int = weights[playerId] ?: 1
    fun tally(voterIds: Collection<Long>): Int = voterIds.sumOf { weightOf(it) }
}

@Serializable
enum class JudgeForce { PASS, FAIL }

@Serializable
data class Nomination(
    val day: Int,
    val nominatorId: Long,
    /** May be [GameState.STORYTELLER_SEAT_ID] in an Atheist game (lead D44). */
    val nomineeId: Long,
    /** The WEIGHTED tally — what the rules use. */
    val votes: Int = 0,
    /** Raw hands raised, clock order from the nominee's left. Never weighted. */
    val voterIds: List<Long> = emptyList(),
    val result: NominationResult = NominationResult.SAFE,
    val isExile: Boolean = false,
    // ---- added ----
    /** The FULL rules snapshot at the moment of the tally. Never recompute from live state. */
    val voteRules: VoteRules? = null,
    /** Extra hands one voter raised (the awoken Banshee's second). */
    val extraVotes: Map<Long, Int> = emptyMap(),
    /** Registration snapshot (lead D51) — Town Crier and Flowergirl read THIS. */
    val nominatorCharacterId: String? = null,
    val nominatorTeams: Set<Team> = emptySet(),
    val demonIdsAtRecord: List<Long> = emptyList(),
    val registersRuling: String = "",
    /** The nominee publicly claimed Goblin before votes were called. */
    val goblinClaim: Boolean = false,
    val judgeForced: JudgeForce? = null,
    /** Ability triggers that fired on this nomination, for the log. */
    val triggersFired: List<String> = emptyList(),
)

@Serializable
enum class TriggerKind {
    /** The engine kills someone the moment the nomination is declared. */
    AUTO_DEATH,
    /** The engine executes someone immediately (consuming the day's execution). */
    AUTO_EXECUTION,
    /** No more nominations today. */
    END_DAY,
    /** Changes how this vote is tallied or who may vote. */
    VOTE_MODIFIER,
    /** The storyteller must decide something before votes are called. */
    CHOICE,
    /** Information only. */
    WARN,
}

@Serializable
data class TriggerOption(val id: String, val label: String, val isDefault: Boolean = false)

@Serializable
data class NominationTrigger(
    val kind: TriggerKind,
    val sourceId: String,
    val actorId: Long? = null,
    val targetId: Long? = null,
    /** One imperative line, storyteller voice. */
    val headline: String,
    val detail: String = "",
    val options: List<TriggerOption> = emptyList(),
    /** The ability may not work — surfaced as a caution, never as suppression. */
    val impaired: Boolean = false,
)

@Serializable
data class NominationCheck(
    val legal: Boolean,
    /** Hard rule violations: "Dana has already nominated today". */
    val blockers: List<String> = emptyList(),
    /** Legal but unusual: "Nominating a dead player — allowed, no ghost vote at stake". */
    val cautions: List<String> = emptyList(),
    val triggers: List<NominationTrigger> = emptyList(),
)

object DayRules {

    data class Right(val allowed: Boolean, val reason: String = "")

    // ---- who may nominate / be nominated ----
    /** Bishop: only the ST nominates. Butcher: one extra after the day's first execution.
     *  Banshee (awoken): twice per day, and may nominate while dead. Golem: once per game. */
    fun canNominate(state: GameState, lookup: (String) -> Character?, playerId: Long): Right
    /** Anyone not nominated today, DEAD INCLUDED (rules: dead players may be executed). */
    fun canBeNominated(state: GameState, lookup: (String) -> Character?, playerId: Long): Right

    /** Pure pre-flight, called on every chip tap so the UI renders live. */
    fun checkNomination(
        state: GameState, lookup: (String) -> Character?,
        nominatorId: Long?, nomineeId: Long?,
    ): NominationCheck

    /** Applies a trigger the ST accepted (or declined with optionId = "skip"). */
    fun applyTrigger(
        state: GameState, lookup: (String) -> Character?,
        trigger: NominationTrigger, optionId: String,
    ): GameState

    /** Records the nomination. Refuses an illegal one unless [force] — the ST always wins. */
    fun record(state: GameState, lookup: (String) -> Character?,
               nomination: Nomination, force: Boolean = false): GameState

    // ---- voting ----
    /** Computes the snapshot to freeze on the Nomination. */
    fun voteRules(state: GameState, lookup: (String) -> Character?, isExile: Boolean): VoteRules
    /** Zealot seats that must have a hand up (5+ alive). */
    fun mustVote(state: GameState, lookup: (String) -> Character?): List<Long>
    /** A sober living Organ Grinder: eyes-closed voting, tally and block hidden. */
    fun secretVoting(state: GameState, lookup: (String) -> Character?): Boolean
    /** Legion: an execution fails if only evil players voted. */
    fun executionFailsOnlyEvilVoted(
        state: GameState, lookup: (String) -> Character?, voterIds: List<Long>): Boolean

    // ---- derived day state (no stored flags) ----
    fun executionToday(state: GameState): ExecutionRecord? =
        state.executions.lastOrNull { it.day == state.cycle }
    /** True when the day's one execution has been spent. SURVIVED counts. */
    fun executionSpent(state: GameState): Boolean =
        state.executions.any { it.day == state.cycle && it.outcome != ExecutionOutcome.NO_EXECUTION }
    /** Derived from the executions list — there is no stored day-closed boolean (lead D30). */
    fun nominationsClosed(state: GameState, lookup: (String) -> Character?): Boolean
    fun secondExecutionAllowed(state: GameState, lookup: (String) -> Character?): Boolean
    fun immuneToDayDeath(state: GameState, lookup: (String) -> Character?, playerId: Long): Boolean

    // ---- existing helpers, moved here from GameActions ----
    fun highestVotesToday(state: GameState): Int
    fun hasNominatedToday(state: GameState, playerId: Long): Boolean
    fun hasBeenNominatedToday(state: GameState, playerId: Long): Boolean
    fun aboutToDie(state: GameState): Long?
}
```

**Nomination triggers to implement** (the table an implementer codes from; each row is a
`CharacterRule.day.onNomination` entry, not a `when` branch):

| sourceId | Fires when | Kind | Effect |
|---|---|---|---|
| `virgin` | nominee is a sober living Virgin, never nominated **before in the game**, no `No Ability` token, nominator registers Townsfolk | AUTO_EXECUTION + END_DAY | execute the nominator `via = VIRGIN`; place `virgin/No Ability`. Options: `execute` (default) / `spy-registers-good` / `skip` |
| `witch` | nominator holds `witch/Cursed`, 4+ alive, Witch alive and sober | AUTO_DEATH | kill the nominator; the vote continues |
| `golem` | nominator is a Golem with no `May Not Nominate` | AUTO_DEATH + WARN | if the nominee is not the Demon, the nominee dies; place `golem/May Not Nominate`; **vote continues** |
| `gnome` | nominee holds `gnome/Amigo`, a living sober Gnome exists | CHOICE → AUTO_DEATH | the Gnome may kill the nominator; vote continues |
| `fearmonger` | nominee holds `fearmonger/Fear` **and the nominator is the Fearmonger seat** | WARN | the win fires at execution, not here |
| `goblin` | nominee taps "Claims to be the Goblin" | CHOICE | sets `goblinClaim`; adds `goblin/Claimed` **non-exclusively**; records a STATEMENT and an ANNOUNCE |
| `vizier` | any nomination while a living Vizier holds their ability | VOTE_MODIFIER | after the tally, offer "Execute immediately (Vizier)" if ≥1 voter registers good |
| `riot` | day 3 with Riot in play | AUTO_DEATH | the nominee dies immediately and must nominate again; no vote |
| `psychopath` | nominee is a living sober Psychopath | WARN | roshambo at execution; the day ends either way |
| `cerenovus` / `harpy` / `mutant` | nominator or nominee holds `Mad` | WARN | check the claim before this goes further |
| `bishop` | Bishop in play | blocker on every player-initiated nomination | only the ST nominates; the ST must nominate ≥1 opposing player each day |

**Voting rules** (lead D27, day-engine §E): weights `bureaucrat/3 Votes` → 3,
`thief/Negative Vote` → −1, awoken Banshee → 2 via `extraVotes`, everyone else 1.
A sober Voudon sets `threshold = 1`, `eligibleVoterIds` = the Voudon plus every dead
player, and `spendsGhostVotes = false`. **Exiles**: every weight is 1, threshold is half of
all seats rounded up, no ghost vote is spent, and `reasons` records *"exile — abilities do
not apply"*. A Butler whose Master is not voting shows an inline hint and is still
tallied (*"tally the Butler's vote anyway"*) — except under `secretVoting`, where it is
excluded with a one-tap override.

```kotlin
object WinCheck {
    @Serializable
    data class Advisory(
        val goodWins: Boolean?,
        val reason: String,
        val cautions: List<String> = emptyList(),
        /** Stable id for dedupe and dismissal: "demon-dead", "mayor-dusk", "vortox-dusk". */
        val ruleId: String,
        /** True when the ST must answer before the phase can advance. */
        val blocking: Boolean = false,
    )

    /** A question the end-game dialog MUST answer before "Declare victory" (lead D40). */
    @Serializable
    data class EndGameQuestion(
        val id: String,
        val sourceId: String,
        val question: String,
        val options: List<TriggerOption>,
    )

    /** Continuous and cheap; called on any state change. */
    fun check(state: GameState, lookup: (String) -> Character?): Advisory?
    /** DAY -> NIGHT, called BEFORE advancePhase. Ordered; all matches returned. */
    fun duskCheck(state: GameState, lookup: (String) -> Character?): List<Advisory>
    /** NIGHT -> DAY, for endings that resolve at dawn. */
    fun dawnCheck(state: GameState, lookup: (String) -> Character?): List<Advisory>
    fun endGameQuestions(state: GameState, lookup: (String) -> Character?): List<EndGameQuestion>
    /** Per-player win/lose for the reveal sheet, after the questions are answered. */
    fun results(state: GameState, lookup: (String) -> Character?, goodWins: Boolean): Map<Long, Boolean>
}
```

**Ordered dusk rules** (first match wins; *all* matches are shown so a collision is visible):
1. `vortox-dusk` — living sober Vortox and no `ExecutionRecord` today with outcome
   `DIED` or `SURVIVED` → **evil wins**, `blocking = true`.
2. `mayor-dusk` — `aliveCountWithTravellers == 3`, no execution today, living sober Mayor
   → **good wins**. Explicit caution when the Vortox also matches; explicit caution
   *"Travellers count — exile them first"*.
3. `leviathan-day5`, 4. `riot-day3`, 5. `zombuul-night` (a **briefing**, not a win).

**Corrections to `check`** it must ship with: count a Zombuul whose latest death is
`registeredOnly` as an alive Demon; suppress "all Demons dead" while a Summoner has not
yet created one; the ≤2-alive rule uses `aliveCountResidents` and drops the
`aliveDemons.isNotEmpty()` precondition; the Saint rule reads `executions`
(`outcome == DIED`) and honours `abilityImpairedAtExecution`; a final **Heretic inversion
pass** maps over every advisory (works while dead, suppressed while impaired); an
**Atheist** suppresses all evil-win advisories and adds `atheist-storyteller-executed`.

---

### 2.9 `CharacterRules.kt` — the declarative per-character registry (schema: WP2; entries: WP7)

This is the single place per-character behaviour lives. **No screen, and no engine
function outside this registry, may branch on a character id.** A character the registry
does not know still gets a generic step built from `characters.json` + `night_guide.json`,
so the app degrades gracefully for homebrew.

```kotlin
package com.clocktower.engine

/** Everything the engine needs to know about one character, in one value. */
data class CharacterRule(
    val id: String,
    // ---- shape ----
    /** Emit one night step per holder (Village Idiot, Snitch's per-Minion wake, Legion). */
    val perHolder: Boolean = false,
    /** This ability fires even though the holder is dead (Ravenkeeper, Sage, Farmer, Barber…). */
    val actsWhileDead: Boolean = false,
    /** The ability itself survives death (Recluse, Spy, Heretic, Zealot, Politician…). */
    val keepsAbilityWhenDead: Boolean = false,
    /** The DeathCause this character's kills carry. */
    val killCause: DeathCause = DeathCause.STORYTELLER,
    /** The wiki does not rule whether these count as Demon kills — the panel asks. */
    val demonKillUncertain: Boolean = false,
    // ---- night ----
    val firstNight: NightRule? = null,
    val otherNight: NightRule? = null,
    // ---- standing / tokens / death ----
    val standing: StandingRule? = null,
    val tokens: List<TokenRule> = emptyList(),
    val onDeath: List<DeathTrigger> = emptyList(),
    // ---- day ----
    val day: DayRule? = null,
    // ---- setup ----
    val setup: List<SetupRequirement> = emptyList(),
    val bluffs: BluffRule? = null,
    /** Bag override, computed against the base distribution for the player count. */
    val bagShape: ((base: Distribution, playerCount: Int) -> BagShape?)? = null,
    /**
     * Jinx-gated extra behaviour. Key = the other character's id; the rule applies
     * only when that character is on the script (lead D19: Riot/Leviathan x
     * Banshee/Farmer/Ravenkeeper/Sage).
     */
    val jinxRules: Map<String, NightRule> = emptyMap(),
)

/** How this character behaves on one kind of night. */
data class NightRule(
    /** FIRE / REDUCED / CONDITIONAL / SKIP, with a reason the storyteller can read. */
    val gate: WakePredicate = Gates.aliveHolder,
    /** What the storyteller is asked. Null = an information-only or marker step. */
    val action: (NightContext) -> NightAction? = { null },
    /** Imperative, storyteller voice, at most two lines. */
    val prompt: String = "",
    /** Pre-filled cards this step offers — never a search box for an answer we know. */
    val cards: (NightContext) -> List<CardOffer> = { emptyList() },
    /** InfoCalc key. "" = this step computes no information. */
    val infoId: String = "",
    /**
     * Does waking for this step count for the Chambermaid? (lead D13)
     * ACT = yes; INFORMED = woken but not for their own ability (Minion info,
     * an Exorcist's target); NONE = never woken.
     */
    val wakeCounts: WakeCount = WakeCount.ACT,
)

enum class WakeCount { ACT, INFORMED, NONE }

/** Day-phase behaviour. */
data class DayRule(
    /** A once-per-day / once-per-game day power the Day tab offers as a button. */
    val ability: DayAbility? = null,
    val onNomination: ((NominationContext) -> List<NominationTrigger>)? = null,
    val onExecution: ((ExecutionContext) -> List<ExecutionConsequence>)? = null,
    val briefing: ((BriefingContext) -> List<BriefingItem>)? = null,
)

/** One row of the Day tab's abilities strip. */
data class DayAbility(
    val label: String,          // "Slayer shot", "Statement", "Public kill"
    val oncePerGame: Boolean = false,
    val oncePerDay: Boolean = false,
    /** The ledger sourceId this ability writes and later consumes. */
    val recordsAs: String = "",
    val available: (state: GameState, lookup: (String) -> Character?, holder: Player) -> Boolean,
)

/** Fires when someone dies. `gate` must include `Status.hasAbility(holder)` unless
 *  the character's text says "even if dead" (lead D35). */
data class DeathTrigger(
    val gate: (state: GameState, event: DeathEvent, holder: Player) -> Boolean,
    val produce: (state: GameState, event: DeathEvent, holder: Player) -> TriggerResult,
)

data class TriggerResult(val prompts: List<Prompt> = emptyList(), val effects: List<Effect> = emptyList())

object CharacterRules {
    /** Concatenation of the per-edition registry files. Built once, lazily. */
    val all: Map<String, CharacterRule>
    /** The rule for [id], or a generic one derived from `characters.json`. */
    fun of(id: String, character: Character?): CharacterRule
    val standingRules: List<StandingRule>
    val tokenRules: List<TokenRule>
}

// ---- the contexts a registry lambda receives (all read-only) ----

/** Everything a night lambda may look at. Never mutate; return values instead. */
class NightContext(
    val state: GameState,
    val lookup: (String) -> Character?,
    val night: Int,
    val isFirstNight: Boolean,
    /** The seat this row is for. Null for group steps and markers. */
    val holder: Player?,
    val role: ActingRole?,
    val diedTonight: Set<Long>,
    val diedToday: Set<Long>,
    val executedToday: ExecutionRecord?,
    val resurrectedTonight: Set<Long>,
)

class NominationContext(
    val state: GameState,
    val lookup: (String) -> Character?,
    val nominatorId: Long?,
    val nomineeId: Long?,
    /** The seat holding this rule's character. */
    val holder: Player,
)

class ExecutionContext(
    val state: GameState,
    val lookup: (String) -> Character?,
    val record: ExecutionRecord,
    val holder: Player,
)

class BriefingContext(
    val state: GameState,
    val lookup: (String) -> Character?,
    val slot: BriefingSlot,
    val holder: Player,
)
```

**Two notes on types this section leans on:**

- `Nomination`, `NominationResult` and `Voting` **move out of `GameState.kt` into
  `DayRules.kt`** in WP0 (a pure move; §2.8 gives their final shape).
- `CardOffer.card` is of type `ShowCardSpec` — today's `ShowCard` sealed hierarchy in
  `components/ShowCards.kt`, **moved into the engine and made `@Serializable`** by WP2,
  and extended by WP8 with `PointCard`, `MultiTokenCard` and a captioned `AlignmentCard`.
  The UI keeps a thin renderer; the engine decides *what* card to offer, never *how* it looks.

**The complete on-death trigger table** — every row is one `DeathTrigger` in a registry
file. `✗` marks the ones today's `deathNotes` has as prose; `≠` marks the ones it has with
the **wrong** condition.

*The Demon's ability kills this player:* `sage`≠ (TONIGHT: show 2 players, one the Demon) ·
`banshee` (DAWN: announce publicly; grant nominate×2 / vote×2 from now on).
*The Demon's ability kills another player:* `grandmother`✗ (the grandchild dies → the
Grandmother dies too, a second `Deaths.attempt` with `GOOD_ABILITY`) · `choirboy`≠ (the
**King** dies → TONIGHT: show the Demon).
*Any night death of this player:* `ravenkeeper`≠ · `farmer`≠ · (`mayor` is `killOutcome`
step 12, not here).
*Any death of this player:* `sweetheart`✗ (TONIGHT: choose 1 → `DRUNK`, `FOREVER`,
`endsWithSource = false`) · `barber`✗ · `hatter` · `poppygrower`✗ · `klutz` · `moonchild`✗ ·
`plaguedoctor` · `pixie` · `bountyhunter` · `scarletwoman`✗ (Demon dies with ≥5 alive
**residents**) · `imp`✗ (self-kill → star pass) · `fanggu` · `vigormortis`≠ (only a Minion
**this** Vigormortis killed) · `lilmonsta` · `lleech` · `angel` · `shabaloth` ·
`professor`/`bonecollector`.
*Day death of an Outsider:* `godfather`≠. *Any day death:* `zombuul`≠.
*Death **by execution*** (`ExecutionRecord.outcome == DIED`): `undertaker` · `cannibal` ·
`minstrel`≠ · `saint` · `mastermind` · `eviltwin` · `goblin` · `leviathan` · `boomdandy` ·
`princess` · `psychopath`.
*Impairment-change triggered (not a death):* `acrobat` — fed by the same effect engine via
`Effects.reconcile`.

**Registry files must use official Title Case labels and N-copy reminders** (lead D5/D31):
a token the registry names must exist, case-insensitively, in that character's
`reminders + remindersGlobal` in `characters.json`, and a character with three physical
copies (`vigormortis` `Has Ability` ×3, `po` `Dead` ×3, `juggler` `Correct` ×5,
`pukka` `Poisoned` ×2) must declare `TokenRule.copies` to match. `GameDataTest` fails the
build otherwise.

### 2.10 `NightPlan.kt` and `Identity.kt` — the night sheet and who acts (WP2 / WP4)

```kotlin
/** Special (non-character) entries in the night order. Unchanged. */
object NightMarkers {
    const val DUSK = "DUSK"
    const val MINION_INFO = "MINION_INFO"
    const val DEMON_INFO = "DEMON_INFO"
    const val DAWN = "DAWN"
    /** New: per-Minion bluff hand-out (Snitch), and the Demon-only bluff step (Poppy Grower). */
    const val MINION_BLUFFS = "MINION_BLUFFS"
    const val DEMON_BLUFFS_ONLY = "DEMON_BLUFFS_ONLY"
    val all = setOf(DUSK, MINION_INFO, DEMON_INFO, DAWN, MINION_BLUFFS, DEMON_BLUFFS_ONLY)
}

@Serializable
enum class StepVariant {
    /** The normal step for tonight. */
    NORMAL,
    /** Run this seat's FIRST-night version tonight (resurrection, new character). */
    FIRST,
    /** A second run for the same holder tonight (Barista). */
    AGAIN,
}

/** Identity of one night step. `token` is what goes in `nightStepsDone`. */
@Serializable
data class StepKey(
    /** Which ability runs. NOT the night-order slot — see [NightStep.slotId]. */
    val abilityId: String,
    /** The single seat this row is for. Null for group steps and markers. */
    val holderId: Long? = null,
    val variant: StepVariant = StepVariant.NORMAL,
) {
    /** Degrades to the bare ability id for simple steps, so old saves keep working. */
    val token: String get() = buildString {
        append(abilityId)
        holderId?.let { append('#').append(it) }
        if (variant != StepVariant.NORMAL) append('@').append(variant.name.lowercase())
    }
}

@Serializable
enum class WakeStyle { FIRST_NIGHT, OTHER_NIGHT }

/** Whether and how a step runs tonight, and why. */
@Serializable
sealed interface StepGate {
    /** Runs normally. */
    @Serializable data object Fire : StepGate
    /**
     * Runs, but only part of it. `allow` names the halves that still run:
     * "pending" / "passive" for an Exorcised Demon (its deferred death still happens),
     * never including "choose". NEVER use Skip for an Exorcised Demon.
     */
    @Serializable data class Reduced(val reason: String, val allow: Set<String>) : StepGate
    /** The engine cannot decide alone: ask [question] first, then offer the action. */
    @Serializable data class Conditional(val question: String, val yesLabel: String, val noLabel: String) : StepGate
    /** Nothing to do. Rendered collapsed and grey, auto-ticked, with [reason] and [Run anyway]. */
    @Serializable data class Skip(val reason: String) : StepGate
}

/**
 * One row of tonight's sheet. This IS the UI's view model — there is no NightStepView,
 * no NightHolder and no NightAsk. Per-holder rendering is achieved by emitting one
 * NightStep per holder.
 */
@Serializable
data class NightStep(
    val key: StepKey,
    /** Night-order position id. Defaults to `key.abilityId`; differs for the Lunatic,
     *  an Alchemist-Poisoner, a Cannibal at the executee's index (lead D43). */
    val slotId: String,
    /** Sort position. Base list entries get index * 100 so insertions fit between. */
    val order: Double,
    /** "Chambermaid — Ana (via the Boffin)", "Pukka — Cai (LUNATIC — nothing happens)". */
    val title: String,
    val detail: String,
    /** Which grant produced this row ("boffin", "philosopher", "lunatic", "drunk"). */
    val sourceId: String? = null,
    /** Group steps only (MINION_INFO, DEMON_INFO, lilmonsta, legion, riot). */
    val holderIds: List<Long> = emptyList(),
    val style: WakeStyle,
    val gate: StepGate,
    /** The single most important derived fact, shown in ember ABOVE the instructions. */
    val banner: String = "",
    /** Imperative, storyteller voice, at most two lines. */
    val prompt: String = "",
    val action: NightAction? = null,
    /** "died tonight", "spent on night 2", "new character", "out of order". */
    val badges: List<String> = emptyList(),
    /** Pre-filled cards this step offers. */
    val cards: List<CardOffer> = emptyList(),
    /** Prompt this step exists to discharge, if any. */
    val promptId: Long? = null,
) {
    val required: Boolean get() = gate !is StepGate.Skip
    val holderId: Long? get() = key.holderId
    val abilityId: String get() = key.abilityId
}

/** A card the storyteller can show, already populated. Never a picker for a known answer. */
@Serializable
data class CardOffer(
    /** Button text: "SHOW: POISONER", "LIE · SHOW 2 TO BEN". */
    val label: String,
    val card: ShowCardSpec,
    val truthful: Boolean,
    /** Long-press opens the free-text editor. */
    val editable: Boolean = true,
)

@Serializable
data class NightPlan(
    val cycle: Int,
    val isFirstNight: Boolean,
    val steps: List<NightStep>,
) {
    /** Index of the first required step not in `nightStepsDone`. */
    fun cursor(done: Set<String>): Int
    fun unfinished(done: Set<String>): List<NightStep> =
        steps.filter { it.required && it.key.token !in done }
}

/** What the storyteller entered on a step. */
@Serializable
data class NightInput(
    val playerIds: List<Long> = emptyList(),
    val characterIds: List<String> = emptyList(),
    val yes: Boolean? = null,
    val number: Int? = null,
    /** The "they chose nobody / were not woken" answer — a REAL answer, recorded. */
    val none: Boolean = false,
    val optionId: String = "",
    /** True when the storyteller made the choice rather than the player (Goon, lead D1). */
    val byStoryteller: Boolean = false,
)

object NightPlan {
    /** Pure. Rebuild after every mutation; never cache. */
    fun build(state: GameState, lookup: (String) -> Character?): NightPlan

    /**
     * Applies a step's input: validates constraints AT RESOLVE TIME, applies
     * `perTarget` effects ONE TARGET AT A TIME re-deriving impairment, positional
     * poison and protections between each, appends CHOICE / WOKE / MALFUNCTION
     * ledger entries, and ticks the step.
     */
    fun resolve(
        state: GameState, lookup: (String) -> Character?,
        key: StepKey, input: NightInput,
    ): GameState

    fun toggleDone(state: GameState, token: String): GameState

    /** Chambermaid: how many of [targets] woke for their OWN ability tonight (lead D13). */
    fun wokeCount(state: GameState, lookup: (String) -> Character?, targets: List<Long>): Int
    /** Mathematician: how many abilities malfunctioned tonight. Excludes the Mathematician. */
    fun malfunctionCount(state: GameState, night: Int): Int
}
```

**Plan construction, in order:**
1. Pick the base list (`firstNight` when `cycle == 1`, else `otherNight`); stamp
   `order = index * 100`.
2. For every night-order slot, emit one row per `ActingRole` whose `slotId` matches
   (§2.10b), in seat order. Group markers keep `holderId = null` and `holderIds`.
3. Evaluate the registry's `gate` to produce the `StepGate`; `Skip` steps are
   auto-added to `nightStepsDone` by the planner so the progress counter reads
   honestly, and render greyed with `[Run anyway]` (lead D37/D60).
4. **Insert derived steps**: every `Prompt(at = TONIGHT)` becomes a step at
   `Prompt.stepSlotId`; every `StepVariant.FIRST` re-run is placed at that character's
   **first-night** order position scaled into tonight's ordering; a character created
   tonight gets its own step.
5. **Insert-after-cursor rule**: a step whose `order` is earlier than the cursor is
   re-stamped to `cursor + 0.5` and badged *"out of order — this became true after their
   slot"*, which the Abilities page explicitly licenses.
6. `MINION_INFO` / `DEMON_INFO` / `MINION_BLUFFS` / `DEMON_BLUFFS_ONLY` are produced by
   **one shared builder** that knows about the Magician, Poppy Grower, Snitch, Lunatic,
   Marionette, Summoner, Lil' Monsta and Legion (lead D37).

**Wake predicates** — composable, in `Gates`; per-character choice lives in the registry:

```kotlin
class WakeContext(
    val state: GameState, val lookup: (String) -> Character?, val night: Int,
    val holder: Player?, val role: ActingRole?,
    val diedTonight: Set<Long>, val diedToday: Set<Long>,
    val executedToday: ExecutionRecord?, val resurrectedTonight: Set<Long>,
    val residentCount: Int, val totalSeatCount: Int,
)

fun interface WakePredicate { fun gate(ctx: WakeContext): StepGate }

object Gates {
    val aliveHolder: WakePredicate
    val actsWhileDead: WakePredicate
    val hasAbility: WakePredicate
    fun notSpent(): WakePredicate            // reads Character.spentLabel (lead D49)
    fun diedTonight(): WakePredicate
    fun someoneDiedToday(expected: Boolean): WakePredicate   // Zombuul(false), Godfather(true)
    fun executedToday(): WakePredicate
    fun nightIs(n: Int): WakePredicate       // Summoner 3, Xaan X
    fun minPlayers(n: Int): WakePredicate    // the 7+ threshold — see §6 Q1
    fun minAlive(n: Int): WakePredicate      // Chambermaid needs 2 other alive players
    val notExorcised: WakePredicate          // -> Reduced, NEVER Skip
    fun all(vararg p: WakePredicate): WakePredicate
}
```

#### 2.10b `Identity.kt` — grants and acting roles (WP4)

```kotlin
@Serializable
enum class GrantMode {
    /** The seat no longer wakes for its own character (Philosopher, Alchemist, Cannibal, Drunk). */
    REPLACE,
    /** The seat wakes for BOTH (Boffin's Demon, Pixie, Hermit, Bone Collector's target). */
    ADD,
}

@Serializable
data class AbilityGrant(
    /** The ability actually exercised: "chambermaid", "poisoner", "pukka". */
    val abilityId: String,
    /** Who granted it: "philosopher", "alchemist", "boffin", "cannibal", "pixie",
     *  "bonecollector", "hermit", "apprentice", "drunk", "marionette", "lunatic". */
    val sourceId: String,
    val mode: GrantMode = GrantMode.ADD,
    /** Night-order slot to wake at; null = the ability's own slot. */
    val slotId: String? = null,
    /** Boffin, Bone Collector, Ogre: works even while the holder is drunk or poisoned. */
    val worksWhileImpaired: Boolean = false,
    /** Drunk, Marionette, Lunatic: the ability NEVER works; every result is fabricated. */
    val alwaysFalse: Boolean = false,
    val cycle: Int = 0,
    /** Independent once-per-game state for a granted once-per-game ability. */
    val spent: Boolean = false,
)

/** A grant whose holder is derived, not fixed to a seat. */
@Serializable
data class FloatingGrant(
    val abilityId: String,
    val sourceId: String,               // "boffin", "plaguedoctor"
    val holder: GrantHolder,
    val worksWhileImpaired: Boolean = false,
)

@Serializable
enum class GrantHolder { ALIVE_DEMON, STORYTELLER }

/** One thing a seat is woken for. */
data class ActingRole(
    val playerId: Long,
    /** Whose rules to run: night guide entry, InfoCalc key, target count, tokens. */
    val abilityId: String,
    /** Which night-order slot it fires in. */
    val slotId: String,
    /** Null when this is the seat's own character. */
    val sourceId: String?,
    val alwaysFalse: Boolean,
    val worksWhileImpaired: Boolean,
)

@Serializable
enum class ChangeReason {
    DEAL, STAR_PASS, STAR_PASS_TOKEN_SWAP, FANG_GU_JUMP, SCARLET_WOMAN, PIT_HAG, BARBER,
    ENGINEER, HATTER, SNAKE_CHARMER, KAZALI, SUMMONER, LORD_OF_TYPHON, HUNTSMAN_DAMSEL,
    AMNESIAC, DEUS_EX_FIASCO, FARMER, STORYTELLER,
}

@Serializable
data class IdentityRecord(
    val playerId: Long,
    val cycle: Int,
    val atNight: Boolean,
    val fromCharacterId: String?,
    val toCharacterId: String?,
    val fromEvil: Boolean,
    val toEvil: Boolean,
    val reason: ChangeReason,
    /** The player still has to be shown their new token. */
    val pendingReveal: Boolean = true,
    val pendingFirstNightRerun: Boolean = false,
    val notes: List<String> = emptyList(),
)

object Identity {
    /** The token this player has SEEN — what a "YOU ARE" card must show. */
    fun believedCharacterId(p: Player): String? = p.shownCharacterId ?: p.characterId
    /** What this seat IS. Never `shownCharacterId`. */
    fun registersAs(p: Player): String? = p.characterId

    /** Everything this seat is woken for, own ability first. */
    fun actingRoles(state: GameState, lookup: (String) -> Character?, p: Player): List<ActingRole>
    fun allActingRoles(state: GameState, lookup: (String) -> Character?): List<ActingRole>

    /**
     * Grants implied by the grimoire rather than stored — nothing is stored twice:
     *   characterId == "drunk"       -> REPLACE(shownCharacterId, "drunk", alwaysFalse)
     *   characterId == "marionette"  -> REPLACE(shownCharacterId, "marionette", alwaysFalse)
     *   characterId == "lunatic"     -> REPLACE(shownCharacterId, "lunatic", slotId = "lunatic", alwaysFalse)
     *   characterId == "hermit"      -> ADD(every Outsider on the script, "hermit")
     *   cannibal + a "Lunch" token   -> REPLACE(last executee's characterIdAtDeath, "cannibal",
     *                                           slotId = that character's slot)
     *   floatingGrant(ALIVE_DEMON)   -> ADD on the single alive Demon seat
     */
    fun derivedGrants(state: GameState, lookup: (String) -> Character?, p: Player): List<AbilityGrant>

    /**
     * THE single funnel for every character change (lead D17). In order:
     *  1. Alignment: `evil = newEvil ?: currentAlignment` (the Pit-Hag rule —
     *     "alignment persists despite character changes"); set `Player.alignment`.
     *  2. Effects & tokens: remove every Effect and PlacedReminder in the WHOLE
     *     grimoire whose source is the abandoned character; keep foreign ones
     *     (Poisoned, Safe, Mad, Red Herring, Grandchild, Twin, Know).
     *  3. Shown identity, grants sourced by the old character, SPENT marks: cleared.
     *  4. Append an IdentityRecord; queue a pending-reveal Prompt and, when the new
     *     ability is first-night or "start knowing", a RUN_FIRST_NIGHT Prompt.
     *  5. Notes: square-bracket setup text has NO effect mid-game (Abilities page,
     *     verbatim) — say so, never ask the ST to rule; a second Demon warning;
     *     `Bluffs.conflicts(...)`; broken Marionette adjacency / Typhon line.
     */
    fun changeCharacter(
        state: GameState,
        lookup: (String) -> Character?,
        playerId: Long,
        newCharacterId: String?,
        reason: ChangeReason,
        newEvil: Boolean? = null,
        shownCharacterId: String? = null,
        suppressReveal: Boolean = false,
    ): GameState

    /**
     * Demon self-kill that passes the mantle. Official How to Run is a TOKEN SWAP:
     * the corpse takes the heir's old token so exactly ONE seat holds the Demon
     * character afterwards. `DeathEvent.characterIdAtDeath` preserves "died as the Imp".
     */
    fun starPass(state: GameState, lookup: (String) -> Character?,
                 demonPlayerId: Long, heirPlayerId: Long,
                 cause: DeathCause = DeathCause.DEMON_KILL): GameState

    fun swapCharacters(state: GameState, lookup: (String) -> Character?, a: Long, b: Long): GameState

    fun pendingReveals(state: GameState): List<IdentityRecord>
    fun markRevealed(state: GameState, playerId: Long): GameState
    /** Ids that must never be held by two live seats: every Demon character, plus lilmonsta. */
    fun duplicateLiveCharacterIds(state: GameState, lookup: (String) -> Character?): List<String>
}
```

### 2.11 `NightAction.kt` — what the storyteller is asked (WP2)

```kotlin
@Serializable
sealed interface NightAction {
    val sourceId: String
    /** Storyteller-voice imperative: "WHO DID HAL CHOOSE?" */
    val prompt: String
}

@Serializable
data class ChoosePlayers(
    override val sourceId: String, override val prompt: String,
    val min: Int, val max: Int,
    val constraints: List<TargetConstraint> = emptyList(),
    val sort: TargetSort = TargetSort.SEAT_ORDER,
    val allowNone: Boolean = false,
    val noneLabel: String = "They chose nobody",
    /** Applied per target, IN PICK ORDER, re-deriving state between each. */
    val perTarget: List<NightEffect> = emptyList(),
    val onResolve: List<NightEffect> = emptyList(),
    val onNone: List<NightEffect> = emptyList(),
) : NightAction

@Serializable
data class ChooseCharacter(
    override val sourceId: String, override val prompt: String,
    val pool: CharacterPool,
    val allowNone: Boolean = true,
    val onResolve: List<NightEffect> = emptyList(),
) : NightAction

@Serializable
data class ChoosePlayerAndCharacter(     // Pit-Hag, Summoner, Cerenovus, Engineer, Kazali
    override val sourceId: String, override val prompt: String,
    val playerConstraints: List<TargetConstraint> = emptyList(),
    val pool: CharacterPool,
    val requireNotInPlay: Boolean = false,
    val onResolve: List<NightEffect> = emptyList(),
) : NightAction

@Serializable
data class YesNo(                        // Organ Grinder, Po head-shake, Professor pass
    override val sourceId: String, override val prompt: String,
    val yesLabel: String, val noLabel: String,
    val onYes: List<NightEffect> = emptyList(),
    val onNo: List<NightEffect> = emptyList(),
) : NightAction

@Serializable
data class ShowInfo(                     // pure info steps: delegates to InfoCalc
    override val sourceId: String, override val prompt: String,
    val targetsNeeded: Int = 0,
    val constraints: List<TargetConstraint> = emptyList(),
) : NightAction

@Serializable
data class Sequence(                     // Al-Hadikhia: 3 picks, then live/die per pick
    override val sourceId: String, override val prompt: String,
    val stages: List<NightAction>,
) : NightAction

@Serializable
enum class TargetConstraint {
    ALIVE, DEAD, ANY_LIVING_STATE,
    NOT_SELF, SELF_ALLOWED,
    NOT_TRAVELLER, TOWNSFOLK, OUTSIDER, MINION, DEMON, NOT_DEMON, GOOD, EVIL,
    /** Reads Memory.forbiddenTargets — survives token expiry. */
    DIFFERENT_FROM_LAST_NIGHT,
    /** Reads Memory.everChosen — "cannot learn the same evil player twice". */
    NOT_CHOSEN_BEFORE,
    NEIGHBOUR_OF_SOURCE,
}

@Serializable
enum class TargetSort { SEAT_ORDER, ALIVE_FIRST, DEAD_FIRST, DEMON_FIRST, MINION_FIRST, OUTSIDER_FIRST, TOWNSFOLK_FIRST }

@Serializable
enum class CharacterPool { SCRIPT, GOOD, EVIL, TOWNSFOLK, OUTSIDER, MINION, DEMON, NOT_IN_PLAY }

/** Where an effect lands. `TargetN` addresses one specific pick (lead D33). */
@Serializable
sealed interface Ref {
    @Serializable data object Source : Ref
    @Serializable data object Target : Ref
    @Serializable data object AllTargets : Ref
    @Serializable data object PreviousTarget : Ref
    @Serializable data class TargetN(val index: Int) : Ref
    @Serializable data object TownsfolkNeighbourOfTarget : Ref
}

@Serializable
sealed interface NightEffect {
    @Serializable data class PlaceToken(
        val sourceId: String, val label: String, val on: Ref,
        val kind: EffectKind? = null, val until: Until = Until.FOREVER,
    ) : NightEffect
    @Serializable data class RemoveToken(val sourceId: String, val label: String, val from: Ref) : NightEffect
    @Serializable data class Attack(
        val on: Ref, val cause: DeathCause = DeathCause.DEMON_KILL,
        /** false => unstoppable (the Pukka's poisoning itself, Fabled effects). */
        val respectProtection: Boolean = true,
    ) : NightEffect
    @Serializable data class Resurrect(val on: Ref) : NightEffect
    @Serializable data class BecomeCharacter(
        val on: Ref, val characterId: String, val evil: Boolean, val reason: ChangeReason,
    ) : NightEffect
    /** The Barber's swap (lead D33). */
    @Serializable data class SwapCharacters(val a: Ref, val b: Ref) : NightEffect
    @Serializable data class MarkSpent(val sourceId: String) : NightEffect
    @Serializable data class RecordChoice(val slot: String = "target") : NightEffect
    @Serializable data class QueuePrompt(
        val at: BriefingSlot, val kind: PromptKind, val sourceId: String,
        val title: String, val on: Ref? = null, val stepSlotId: String = "",
    ) : NightEffect
    @Serializable data class Announce(val at: BriefingSlot, val text: String) : NightEffect
    @Serializable data class NoteMalfunction(val on: Ref, val reason: String) : NightEffect
    @Serializable data class ShowCardTo(val on: Ref, val card: String) : NightEffect
}
```

**Resolution contract** — `NightPlan.resolve` does exactly this, in order:
1. Validate `input` against `constraints` **at resolve time**, not only at pick time.
2. For `ChoosePlayers`, apply `perTarget` **one target at a time**, re-deriving between
   each: the source's impairment, positional poison (a No Dashii neighbour dying changes
   it), protections on the remaining targets, and the death preview. Surface a per-target
   confirmation so the storyteller sees the recomputed warnings.
3. Every `Attack` goes through `Deaths.attempt` — never `kill` directly.
4. Append a `LedgerEntry(kind = CHOICE)` for the whole action, **including
   `none = true`** (the Po needs "chose nobody" to be a fact, not an absence).
5. Append `WOKE` entries: `ownAbility = true` for the acting holder,
   `ownAbility = false` for anyone woken *by* the action.
6. Append `MALFUNCTION` entries wherever the engine can prove one.

---

### 2.12 `Briefings.kt` and typed information (WP6 / WP2)

```kotlin
@Serializable
enum class BriefingKind {
    /** Say this out loud, in this order. */
    ANNOUNCE,
    /** For the storyteller only — never say it. */
    PRIVATE,
    /** A standing fact that constrains today. */
    STANDING_FACT,
    /** Something the storyteller must still do or collect. */
    TODO_ASK,
    /** A token that was just swept off the grimoire. */
    SWEPT,
}

@Serializable
enum class BriefingSeverity { INFO, ACTION, ALERT }

@Serializable
data class BriefingItem(
    /** Stable key for the ticked-off set; survives recomposition and undo. */
    val key: String,
    val kind: BriefingKind,
    val severity: BriefingSeverity = BriefingSeverity.INFO,
    val sourceId: String = "",
    /** Imperative, storyteller voice, ready to read aloud. */
    val text: String,
    val playerId: Long? = null,
    /** The prompt or ledger entry this discharges, if any. */
    val promptId: Long? = null,
    val ledgerId: Long? = null,
    /**
     * One-tap follow-through, as a stable string the UI maps to a handler:
     * "open-seat:7", "rerun-first-night:7", "record:gossip", "show-card:<spec>",
     * "resolve-prompt:12", "mark-announced:9".
     */
    val actionId: String = "",
)

/** One briefing. Serialisable so `lastDawn` / `lastDusk` can be frozen on the state. */
@Serializable
data class Briefing(
    val slot: BriefingSlot,
    val cycle: Int,
    val items: List<BriefingItem> = emptyList(),
) {
    fun of(kind: BriefingKind): List<BriefingItem> = items.filter { it.kind == kind }
    val announce: List<BriefingItem> get() = of(BriefingKind.ANNOUNCE)
    val private: List<BriefingItem> get() = of(BriefingKind.PRIVATE)
    val standing: List<BriefingItem> get() = of(BriefingKind.STANDING_FACT)
    val todo: List<BriefingItem> get() = of(BriefingKind.TODO_ASK)
}

object Briefings {
    /**
     * Pure. Derived from prompts + effects + ledger + deaths + executions.
     * NOTHING here is stored; `GameState.lastDawn` is a frozen snapshot, not a source.
     */
    fun at(state: GameState, lookup: (String) -> Character?, slot: BriefingSlot): Briefing
}
```

**What each slot must contain** (the union of the four competing proposals):

- **DAWN** — deaths to announce, in seat order, with `registeredOnly` deaths announced as
  real deaths (that is the Zombuul's whole point) and the truth in `PRIVATE`;
  resurrections announced **after** the deaths and **without a reason** (*"Announce: Cai is
  alive again."*); saves the table must not hear (*"Bea was attacked — the Monk saved
  her"*) in `PRIVATE`; every pending `ANNOUNCE` ledger entry; every `Prompt(at = DAWN)`;
  what was swept off the grimoire; owed private tells (Grandmother, Choirboy, Bounty
  Hunter, Damsel, Nightwatchman).
- **DAY_START** — standing protections (*"Ben survives execution today (Devil's
  Advocate)"*), madness **and what they are mad about**, secret voting, Vizier, Psychopath,
  clocks (Leviathan day N of 5, Riot day N of 3, Courtier "2 more days", Mayor at 3 alive),
  abilities lost today, and the **collect list** — one `TODO_ASK` per unrecorded Gossip
  statement, Juggler guess set, Savant visit, Artist question, Fisherman advice, each with
  an `actionId` that opens the right recorder.
- **NOMINATION** — the `NominationTrigger` list for the pending pair.
- **EXECUTION** — the `ExecutionConsequence` list.
- **DUSK** — what expires now, what will wake, conditional wakes (*"Nobody died today —
  the Zombuul kills tonight"*), auto-skipped steps with reasons, countdowns, and every
  blocking `WinCheck.duskCheck` advisory.
- **NOW / TONIGHT** — prompts the current screen must surface immediately, and prompts the
  night plan will turn into steps.

**`InfoCalc` becomes typed** (lead D10/D50). The string-matching false-info UI is deleted.

```kotlin
/** What the engine owes this player tonight. MUST_LIE outranks MAY_LIE. */
@Serializable
enum class InfoObligation { TRUTH, MAY_LIE, MUST_LIE }

@Serializable
sealed interface Answer {
    @Serializable data class Count(val n: Int, val min: Int = 0, val max: Int = 0) : Answer
    @Serializable data class YesNoAnswer(val yes: Boolean) : Answer
    @Serializable data class Characters(val ids: List<String>) : Answer
    @Serializable data class Players(val ids: List<Long>, val characterId: String? = null) : Answer
    @Serializable data class Message(val text: String) : Answer
}

@Serializable
data class InfoResult(
    /** The TRUE answer, always computed. */
    val answer: Answer,
    /** Storyteller-voice headline: "1 of Ana's alive neighbours is evil". */
    val headline: String,
    val detail: String = "",
    /**
     * Plausible alternatives to show instead, generated for EVERY answer shape —
     * a wrong character of the same team, the opposite direction, a different pair,
     * the full 0..alive range. Empty ONLY when the engine genuinely cannot lie,
     * in which case the UI must not render a "false info" heading at all.
     */
    val alternatives: List<Answer> = emptyList(),
    val obligation: InfoObligation = InfoObligation.TRUTH,
    /** Impairment, misregistration and Vortox are THREE distinct obligations — keep them apart. */
    val caveats: List<String> = emptyList(),
    val abilityMalfunctions: Boolean = false,
)
```

`InfoCalc.supports` must grow to cover **Godfather** (which Outsiders are in play),
**Juggler** (count the recorded guesses), **Exorcist** ("is this seat the Demon?"),
**Courtier** ("is that character in play, and where?"), **Savant** (propose true facts),
**Tea Lady** (is the protection on?), and a rewritten **Chambermaid** (`NightPlan.wokeCount`)
and **Mathematician** (`NightPlan.malfunctionCount`).

### 2.13 `SetupRequirements.kt`, `Bluffs.kt`, `Setup.kt` (WP4)

```kotlin
@Serializable
enum class RequirementKind {
    SHOWN_TOKEN,   // pick a character token this player believes
    REMINDER,      // place a token on some seat
    ALIGNMENT,     // set or flip a seat's alignment
    GRANT,         // pick an ability the seat holds, or write a secret
    NUMBER,        // store an integer choice (Xaan's X, an Outsider branch)
    PAIR,          // pick a partner seat (Evil Twin)
    BLUFFS,        // a BluffRequirement
    SEATING,       // an adjacency / line constraint
    INFORM,        // "show every Minion the Damsel token"
    ACK,           // acknowledge a bag rule (Kazali's 0 Minions, Lil' Monsta's 0 Demons)
}

/**
 * One row of the "Before the first night" checklist AND one clause of setup
 * validation. Ids are canonical (lead D48): "drunk.token", "lunatic.token",
 * "lunatic.minions", "lunatic.bluffs", "marionette.token", "marionette.seat",
 * "fortuneteller.herring", "puzzlemaster.drunk", "villageidiot.drunk", "pixie.mad",
 * "widow.know", "grandmother.grandchild", "balloonist.know", "eviltwin.twin",
 * "bountyhunter.evil", "snitch.bluffs:<seat>", "demon.bluffs", "summoner.bluffs",
 * "boffin.grant", "alchemist.grant", "xaan.X", "damsel.minions", "mezepheles.word",
 * "traveller.alignment:<seat>", "kazali.noMinions", "lilmonsta.noDemonSeat",
 * "setup.outsiderBranch".
 */
data class SetupRequirement(
    val id: String,
    val characterId: String,
    val kind: RequirementKind,
    /** Short checklist label. */
    val title: String,
    /** Storyteller-voice imperative for the prompt. */
    val prompt: String,
    /** Message when unmet; "" for advisory-only rows. */
    val problem: String = "",
    /** Blocks "Begin night" (with the existing "start anyway" escape). */
    val blocking: Boolean = true,
    val candidates: (GameState, (String) -> Character?) -> List<Candidate> = { _, _ -> emptyList() },
    val apply: (GameState, Selection) -> GameState = { s, _ -> s },
    val satisfied: (GameState, (String) -> Character?) -> Boolean,
)

data class Candidate(val id: String, val label: String, val playerId: Long? = null,
                     val badge: String = "", val enabled: Boolean = true)
data class Selection(val playerIds: List<Long> = emptyList(),
                     val characterIds: List<String> = emptyList(),
                     val number: Int? = null, val text: String = "")

object SetupRequirements {
    /** Every requirement this game raises RIGHT NOW — re-checkable mid-game, not only at SETUP. */
    fun all(state: GameState, lookup: (String) -> Character?): List<SetupRequirement>
    fun unmet(state: GameState, lookup: (String) -> Character?): List<SetupRequirement> =
        all(state, lookup).filterNot { it.satisfied(state, lookup) }
    /** Replaces `GameActions.validateSetupState`. */
    fun blockingProblems(state: GameState, lookup: (String) -> Character?): List<String> =
        unmet(state, lookup).filter { it.blocking }.map { it.problem }
}
```

```kotlin
/** One set of bluffs this game owes someone. A LIST, not a map (lead D38). */
data class BluffRequirement(
    /** Key into `GameState.bluffSets`. Source-qualified so one seat can hold two sets. */
    val key: String,
    /** Seat that receives them; null for the Demon set in a multi-Demon game. */
    val recipientId: Long?,
    /** "Demon bluffs", "Snitch bluffs — Ana (Poisoner)", "Lunatic bluffs — Bo". */
    val label: String,
    val size: Int = 3,
    /** Only the Lunatic (and an impaired Snitch) may be shown in-play characters. */
    val allowInPlay: Boolean = false,
    /** Night-order step where the card is shown. */
    val stepSlotId: String,
    val sourceId: String,
    /** The rules sentence surfaced under the picker. */
    val reason: String = "",
    /** false = offer it, never block on it (Legion: "bluffs are optional"). */
    val required: Boolean = true,
) {
    companion object { const val DEMON_KEY = "demon" }
}

data class BluffCandidate(
    val character: Character,
    val inPlay: Boolean,
    /** "the Drunk believes this", "the Boffin gave the Demon this", "the Alchemist has this". */
    val inUseBy: String? = null,
)

object Bluffs {
    fun requirements(state: GameState, lookup: (String) -> Character?): List<BluffRequirement>
    fun candidates(state: GameState, script: List<Character>, req: BluffRequirement): List<BluffCandidate>
    fun suggest(state: GameState, script: List<Character>, req: BluffRequirement, random: Random): List<String>
    fun set(state: GameState, key: String, ids: List<String>): GameState
    fun clear(state: GameState, key: String): GameState
    /** "Fisherman is one of the Demon's bluffs and is now in play." */
    fun conflicts(state: GameState, lookup: (String) -> Character?): List<String>
}
```

`requirements` rules, in order: **no bluffs at all** when a `lilmonsta` is in play (How to
Run: skip both info steps) or an `atheist` is; **no Demon set** below 7 residents unless a
Poppy Grower or Summoner is in play; **the Demon set** (`size = 3`,
`stepSlotId = if (poppygrower) DEMON_BLUFFS_ONLY else DEMON_INFO`,
`required = "legion" !in inPlay`); **the Summoner set** replaces it when no Demon seat
exists (`required = "alchemist" !in inPlay` — jinx); **one independent Snitch set per
Minion**, excluding the Marionette (never woken for anything that would confirm they are a
Minion) but **including Legion seats** — that is the official "possibly 6 bluffs" case, as
two separate sets on one seat; **the Lunatic's own set**, `allowInPlay = true`.
**Do not add +3 for a Snitch × Marionette jinx — that jinx is retired** and must be deleted
from the data (lead D38).

```kotlin
/** Bag override for one character, replacing `Setup.TEAM_WARPING_IDS` (lead D28). */
data class BagShape(
    val townsfolk: IntRange? = null,
    val outsiders: IntRange? = null,
    val minions: IntRange? = null,
    val demons: IntRange? = null,
    val requireInBag: Set<String> = emptySet(),
    /** Ids that must NOT be in the bag even though they are "in play" (lilmonsta). */
    val forbidInBag: Set<String> = emptySet(),
    val copies: Map<String, IntRange> = emptyMap(),
    /** Advisory only — warn, never block (Legion). */
    val advisory: Boolean = false,
    val note: String = "",
)
```

| Character | Shape (base = `distributionFor(n)`) |
|---|---|
| `kazali` | `minions = 0..0`, `demons = 1..1`, `outsiders = 0..(base.outsiders + base.minions)` — *"Minions are created on the first night."* |
| `lordoftyphon` | `minions = 0..0`, `demons = 1..1` — *"The 3 Minions and the evil line are created on the first night."* |
| `lilmonsta` | `forbidInBag = {"lilmonsta"}`, `demons = 0..0`, `minions = base.minions + 1`, `townsfolk = base.townsfolk` (10p → **7/0/3/0**, lead D18) |
| `summoner` | `demons = 0..0`, `minions = base.minions`, `townsfolk = base.townsfolk + 1` |
| `atheist` | `minions = 0..0`, `demons = 0..0`, `townsfolk + outsiders = n` |
| `legion` | **advisory**: `minions = 0..0` is firm; the ~"7 Legion to 3 good at 10" ratio warns only |
| `riot` | **no shape** — an ordinary Demon in an ordinary bag. Delete `riot` from `TEAM_WARPING_IDS` and `DUPLICABLE`, and drop `[All Minions are Riot]` from the data (lead D28) |
| `marionette` | in 3-Minion games only, `minions = base.minions - 1` in the bag, with a night-1 requirement to create the missing real Minion |
| `villageidiot` | `copies = {"villageidiot" to 1..3}` |
| `xaan` | `outsiders = X..X` once `Decisions.XAAN_X` is chosen; free before |

### 2.14 Data-file schema changes (types: WP0; content: WP5)

```kotlin
// Character.kt
@Serializable
enum class Team {
    @SerialName("townsfolk") TOWNSFOLK,
    @SerialName("outsider") OUTSIDER,
    @SerialName("minion") MINION,
    @SerialName("demon") DEMON,
    @SerialName("traveler") TRAVELLER,
    @SerialName("fabled") FABLED,
    /** New official team, 2025 (lead D31). */
    @SerialName("loric") LORIC,
    /** Deserialisation fallback — the official dataset adds teams over time. */
    UNKNOWN;
    val isEvil: Boolean get() = this == MINION || this == DEMON
    val isTownResident: Boolean get() = this == TOWNSFOLK || this == OUTSIDER || this == MINION || this == DEMON
}
// NOTE: `Team` needs a tolerant KSerializer that maps any unknown string to UNKNOWN
// instead of throwing, or one bad official id breaks the whole dataset load.

@Serializable
data class Character(
    // … existing fields unchanged …
    /**
     * The exact reminder label that marks this character's once-per-game ability as
     * used, in official spelling ("No Ability", "Used", "Guess Used"). Drives
     * `Gates.notSpent`; the "Once per game" text heuristic is deleted (lead D49).
     */
    val spentLabel: String = "",
)

@Serializable
data class Jinx(
    val id1: String, val id2: String, val reason: String,
    /** Registry hook id, so a jinx can CHANGE behaviour, not merely be displayed. */
    val effect: String = "",
)

// NightGuide.kt — three new channels (lead D23)
@Serializable
data class NightGuideEntry(
    val first: GuideNight? = null,       // first night
    val other: GuideNight? = null,       // other nights
    val setup: GuideNight? = null,       // before night 1: bag changes, token swaps, ST picks
    val day: GuideNight? = null,         // day-phase procedure to run or watch for
    val reference: GuideNight? = null,   // passive/always-on rules, no ST action
)
```

`NightGuide.forStep(abilityId, style)` replaces `forStep(characterId, isFirstNight)` so a
`StepVariant.FIRST` re-run shows the **first-night** run-book and a Boffin-granted row
shows the **granted** character's run-book. `GuideShow` gains `kind = "bluffs"` and
`kind = "number"`, and every `token: "pick"` show the engine can resolve gains
`prefill` so a `CardOffer` is built without a picker.

**Coverage rules a test must enforce:** every id in `characters.json` has at least one of
`first`/`setup`/`day`/`reference`; `first` is present **iff** the id is in the first-night
order and `other` **iff** in the other-night order; every `(sourceId, label)` named by a
`TokenRule` or a registry entry exists (case-insensitively) in that character's
`reminders + remindersGlobal`, N times where N copies are declared.

### 2.15 `Phases.kt` and `GameLog.kt` (WP1 / WP3)

```kotlin
object Phases {
    /**
     * SETUP -> NIGHT 1 -> DAY 1 -> NIGHT 2 -> …
     *
     * NIGHT -> DAY, in this exact order (the ordering is the fix for "the Monk token
     * was already gone when the dawn report was computed"):
     *   1. dawn = Briefings.at(state, lookup, DAWN)          // BEFORE any sweep
     *   2. expire Until.DAWN effects and their tokens
     *   3. Tokens.advanceCountdowns(state, Until.DAWN)
     *   4. Effects.reconcile(...)
     *   5. copy(phase = DAY, lastDawn = dawn)
     *
     * DAY -> NIGHT:
     *   1. dusk = Briefings.at(state, lookup, DUSK)
     *   2. expire Until.DUSK effects; advance dusk countdowns
     *   3. Effects.reconcile(...)
     *   4. copy(phase = NIGHT, cycle = cycle + 1, nightStepsDone = emptySet(), lastDusk = dusk)
     */
    fun advancePhase(state: GameState, lookup: (String) -> Character?): GameState
}

/** One flat, totally ordered transcript, shared by both platforms. */
object GameLog {
    data class Row(val cycle: Int, val atNight: Boolean, val seq: Long, val text: String)
    /** Merges deaths, nominations (with VOTER NAMES), executions, identity changes and
     *  the whole ledger, ordered by (cycle, night-before-day, seq). A total order. */
    fun rows(state: GameState, lookup: (String) -> Character?): List<Row>
    fun toMarkdown(state: GameState, lookup: (String) -> Character?): String
}
```

---

## 3. File layout, UI contract, and the shared view-model interface

### 3.1 Engine file layout

All paths under `engine/src/main/kotlin/com/clocktower/engine/`.

| File | Status | Contents | Owner after WP0 |
|---|---|---|---|
| `GameState.kt` | **changed** | `GameState`, `Player`, `PlacedReminder`, `SeatNote`, `FabledEntry`, `Alignment`, `Phase`, `Decisions`, derived accessors | **WP0 — then FROZEN** |
| `Migrations.kt` | new | `GameState.migrated()`, legacy-field folding, token→effect projection | **WP0 — then FROZEN** |
| `GameActions.kt` | **gutted** | A pure façade of one-line delegates to the objects below, so existing call sites and tests keep compiling. **No new verb is ever added here.** | **WP0 — then FROZEN** |
| `Character.kt` | changed | `Team` (+`LORIC`, `UNKNOWN`, tolerant serializer), `Character.spentLabel`, `Jinx.effect` | WP0 → WP5 |
| `Effects.kt` | new | `EffectKind`, `EffectGroup`, `Until`, `Effect`, `StandingRule`, `Status`, `Effects`, `RenderedToken`, and the moved `addReminder` / `placeExclusiveReminder` / `removeReminder` | WP1 |
| `Registration.kt` | new | `Registration.registersAs` and friends | WP1 |
| `Tokens.kt` | new | `TokenRule`, `Tokens` | WP1 |
| `Deaths.kt` | new | `DeathCause`, `KillCause`, `DeathEvent`, `KillOutcome`, `DeathAttempt`, `Deaths` (incl. the moved `kill`/`revive`/`resurrect`/`toggleGhostVote`) | WP1 |
| `Prompts.kt` | new | `BriefingSlot`, `PromptKind`, `Prompt`, `Prompts` | WP1 |
| `Phases.kt` | new | `advancePhase` and the phase pipeline (moved out of `GameActions`) | WP1 |
| `StatusEffects.kt` | **shrinks to a shim** | `isImpaired` / `derivedPoison` / `deathNotes` / `nominationWarnings` become `@Deprecated` one-line delegates | WP1 |
| `NightPlan.kt` | new (replaces `NightOrder.kt`) | `NightMarkers`, `StepKey`, `StepVariant`, `WakeStyle`, `StepGate`, `NightStep`, `NightPlan`, `NightInput`, `CardOffer`, `WakeContext`, `Gates` | WP2 |
| `NightAction.kt` | new | `NightAction`, `NightEffect`, `Ref`, `TargetConstraint`, `TargetSort`, `CharacterPool` | WP2 |
| `CharacterRules.kt` | new | `CharacterRule`, `NightRule`, `DayRule`, `DayAbility`, `DeathTrigger`, `WakeCount`, `object CharacterRules` (schema + concatenation only) | WP2 |
| `NightOrder.kt` | **deleted** | superseded by `NightPlan.kt` | WP2 |
| `NightGuide.kt` | changed | three new channels, `forStep(abilityId, style)` | WP0 → WP2 |
| `InfoCalc.kt` | changed | `InfoObligation`, `Answer`, typed `InfoResult`, new supported ids, rewritten Chambermaid/Mathematician | WP2 |
| `ShowCardSpec.kt` | new (moved from `app/.../components/ShowCards.kt`) | the `@Serializable` card hierarchy the engine builds `CardOffer`s from; the Compose renderer stays in `app/` | WP2 → extended by WP8 |
| `Ledger.kt` | new | `LedgerKind`, `Verdict`, `LedgerEntry`, `Ledger`, `Memory` | WP3 |
| `DayRules.kt` | new | `VoteRules`, `Nomination`, `NominationResult`, `Voting`, triggers, `DayRules` (incl. the moved `aboutToDie` / `highestVotesToday` / `hasNominatedToday`) | WP3 |
| `Execution.kt` | new | `ExecutionOutcome`, `ExecutionVia`, `ExecutionRecord`, `ExecutionConsequence`, `Execution` | WP3 |
| `WinCheck.kt` | changed | `ruleId`, `blocking`, `duskCheck`, `dawnCheck`, `EndGameQuestion`, `results`, Heretic pass | WP3 |
| `GameLog.kt` | new | `GameLog.rows` / `toMarkdown` | WP3 |
| `Identity.kt` | new | `GrantMode`, `AbilityGrant`, `FloatingGrant`, `GrantHolder`, `ActingRole`, `ChangeReason`, `IdentityRecord`, `Identity` (incl. the moved `starPass` / `swapCharacters` / `snakeCharmerSwap`) | WP4 |
| `Seats.kt` | new | `addSeat`, `removeSeat`, `moveSeat`, `rename`, `assignCharacter`, `setShownCharacter`, `setNote`, `setAlignment`, `deal` (moved out of `GameActions`) | WP4 |
| `SetupRequirements.kt` | new | `RequirementKind`, `SetupRequirement`, `Candidate`, `Selection`, `SetupRequirements` | WP4 |
| `Bluffs.kt` | new | `BluffRequirement`, `BluffCandidate`, `Bluffs`, `setFabled` | WP4 |
| `Setup.kt` | changed | `BagShape`, `bagShapeFor`, `validateBag`, `randomBag`, `DUPLICABLE`; `TEAM_WARPING_IDS` **deleted** | WP4 |
| `Briefings.kt` | new | `BriefingKind`, `BriefingSeverity`, `BriefingItem`, `Briefing`, `Briefings` | WP6 |
| `rules/RulesTroubleBrewing.kt` | new | `internal val TB_RULES: List<CharacterRule>` | WP7-TB |
| `rules/RulesBadMoonRising.kt` | new | `BMR_RULES` | WP7-BMR |
| `rules/RulesSectsAndViolets.kt` | new | `SV_RULES` | WP7-SV |
| `rules/RulesExpTownsfolk.kt` | new | `EXP_TOWNSFOLK_RULES` | WP7-EXP-T |
| `rules/RulesExpOutsiders.kt` | new | `EXP_OUTSIDER_RULES` | WP7-EXP-O |
| `rules/RulesExpMinions.kt` | new | `EXP_MINION_RULES` | WP7-EXP-M |
| `rules/RulesExpDemons.kt` | new | `EXP_DEMON_RULES` | WP7-EXP-D |
| `rules/RulesTravellers.kt` | new | `TRAVELLER_RULES` | WP7-TRAV |
| `rules/RulesFabled.kt` | new | `FABLED_RULES` | WP7-FAB |
| `Script.kt`, `ScriptLink.kt`, `Notes.kt`, `GameData.kt`, `Platform.kt` | unchanged | | — |

Data: `engine/src/main/resources/botc/data/{characters,night_and_jinxes,night_guide}.json`
and the regeneration script under `tools/` — **WP5 exclusively**.

### 3.2 UI contract — what each screen consumes

**`NightScreen`** consumes exactly one thing: `NightPlan.build(state, lookup)`.

- Renders one card per `NightStep`, in `order`. There is no per-holder nesting: a
  multi-holder character produces multiple `NightStep`s, each with its own `holderId`,
  `banner`, `action`, `info` and `cards`.
- Card anatomy, top to bottom: progress strip (cycle · step n/m · segment bar · dim
  control) → who wakes (token, character, **player name and seat number**) →
  `step.banner` in ember at 16 sp → `step.prompt` at 18 sp → `step.action` rendered by
  **one** picker component → consequence preview from `Deaths.killOutcome` → **one**
  full-width 56 dp primary button whose label states the **outcome**, not the verb
  ("EVE SURVIVES — NOBODY DIES", "SHOW 0 TO BEN") → a collapsed secondary drawer
  (other outcomes · show a card · skip · how to run · undo this step).
- The primary button calls `viewModel.resolveNightStep(step.key, input)`, which applies
  the state change, places tokens, writes the ledger, ticks the step and advances to the
  next required step.
- `StepGate.Skip` rows render collapsed, grey, pre-ticked, with the reason and a
  `[Run anyway]` affordance; they are excluded from the dawn guard (lead D37/D60).
- `StepGate.Conditional` renders its question as two buttons before the action appears.
- `step.cards` are **pre-filled**: tap shows, long-press edits. `ShowCardSpec` gains
  `PointCard` (prefix + 1–3 player names at 48 sp + seat numbers), `MultiTokenCard`
  (wrapping FlowRow) and `AlignmentCard(evil: Boolean?, caption: String)`.
- Showing a card writes a `TOLD` ledger entry; the card body is not tappable; exit is a
  1.2 s press-and-hold on a bottom-edge control that lands on the privacy cover.
- **Deleted**: `QuickResolutions`, `DemonKillPanel`, and every character id in the file.

**`DayScreen`** consumes `Briefings.at(state, lookup, DAY_START)`, `DayRules`,
`Execution`, `Memory` and the registry's `DayAbility` rows.

- A day **timeline** of collapsible stages — Dawn · Morning briefing · What was said ·
  Day abilities · Nominations · Dusk — over a fixed bottom bar (`⏱ timer`, `+ Say`,
  `Nominate`). The phase button leaves the top bar.
- **"What was said"** is present in **every** game with nothing in play: tap a seat, type
  or dictate, tap Add. A zero-typing path (`Claims…` → character grid) covers the most
  common statement. Writes `Ledger.statement(...)`.
- Nomination is a **pinned seat ring**: tap 1 = nominator, tap 2 = nominee, and the
  `NominationCheck` card renders between the ring and the vote panel.
- Vote panel renders `DayRules.voteRules(...)`; recording freezes the whole snapshot on
  the `Nomination`.
- Every Execute button calls `Execution.execute(...)` and renders
  `Execution.consequences(...)` in one shared confirmation sheet.
  **"Executed — but they don't die"** is a first-class button.

**`GameShell` / `PhaseFlow`** consume:

```kotlin
sealed interface PhaseRequest {
    /** Setup requirements or unfinished required night steps block the advance. */
    data class Blocked(val title: String, val items: List<BriefingItem>) : PhaseRequest
    data class ConfirmDawn(val briefing: Briefing) : PhaseRequest
    data class ConfirmDusk(val briefing: Briefing, val advisories: List<WinCheck.Advisory>) : PhaseRequest
    data object Advance : PhaseRequest
}
object PhaseFlow { fun request(state: GameState, lookup: (String) -> Character?): PhaseRequest }
```

The dawn sheet is a read-aloud card: `ANNOUNCE` lines first, `PRIVATE` below, `SWEPT`
and `TODO_ASK` under that, and one primary button `OPEN DAY N →`.

**`GrimoireScreen` / `SeatSheet`** consume `Effects.rendered(...)` (status pips coloured
by `EffectGroup`, provenance as a team-colour ring), `Memory.forPlayer(...)` (the History
section), `SetupRequirements.unmet(...)` (amber dots), and a shared **`KillSheet`** that
renders `Deaths.killOutcome(...)` for the selected cause and applies `Deaths.attempt(...)`.
Text below 11 sp is banned; a Board view lists every token in full text.

**`SetupScreen` / `BluffsSheet` / hand-out mode** consume `SetupRequirements.all(...)`
(the checklist), `Bluffs.requirements(...)` + `candidates(...)` + `suggest(...)` (one tab
per requirement key), `Setup.validateBag(...)` with `BagShape`, and
`Identity.pendingReveals(...)`.

### 3.3 The shared view-model interface (ends the two-wrapper problem)

New file `app/src/main/java/com/clocktower/grimoire/ui/GameActionsApi.kt` — pure Kotlin,
no Android imports, so `web/build.gradle.kts` compiles it into the PWA (it excludes only
`GameViewModel.kt`).

```kotlin
package com.clocktower.grimoire.ui

import com.clocktower.engine.*

/**
 * Every engine verb the UI can call, wired ONCE for both platforms.
 *
 * `GameViewModel` (Android) and `WebGameViewModel` (wasm) implement this and provide
 * only [update], [characterById] and [gameData]; every wrapper below is a default
 * method. A new engine verb is added HERE and nowhere else — never again in two files.
 */
interface GameActionsApi {
    val gameData: GameData
    fun update(transform: (GameState) -> GameState)
    fun characterById(id: String?): Character?

    private val lookup: (String) -> Character? get() = ::characterById

    // ---- WP0: existing verbs, moved verbatim ----
    fun addSeat(name: String) = update { Seats.addSeat(it, name) }
    fun removeSeat(playerId: Long) = update { Seats.removeSeat(it, playerId) }
    fun rename(playerId: Long, name: String) = update { Seats.rename(it, playerId, name) }
    fun assign(playerId: Long, characterId: String?, isTraveller: Boolean = false) =
        update { Seats.assignCharacter(it, playerId, characterId, isTraveller) }
    fun setShownCharacter(playerId: Long, characterId: String?) =
        update { Seats.setShownCharacter(it, playerId, characterId) }
    fun advancePhase() = update { Phases.advancePhase(it, lookup) }
    // … the remaining existing wrappers, unchanged in behaviour …

    // ---- WP1: effects, status, deaths ----
    fun placeEffect(target: Long, kind: EffectKind, sourceId: String, sourcePlayerId: Long?,
                    until: Until, label: String = "") =
        update { Effects.place(it, target, kind, sourceId, sourcePlayerId, until, label).state }
    fun removeEffect(effectId: Long) = update { Effects.remove(it, effectId) }
    fun suspendEffect(effectId: Long, suspended: Boolean) = update { Effects.suspend(it, effectId, suspended) }
    fun attemptDeath(targetId: Long, cause: KillCause, optionId: String = "") =
        update { Deaths.attempt(it, lookup, targetId, cause, optionId).state }
    fun resurrect(playerId: Long) = update { Deaths.resurrect(it, lookup, playerId) }
    fun revive(playerId: Long) = update { Deaths.revive(it, playerId) }

    // ---- WP2: night ----
    fun resolveNightStep(key: StepKey, input: NightInput) =
        update { NightPlan.resolve(it, lookup, key, input) }
    fun toggleNightStep(token: String) = update { NightPlan.toggleDone(it, token) }

    // ---- WP3: day, ledger, execution ----
    fun recordStatement(speakerId: Long?, sourceId: String, text: String) =
        update { Ledger.statement(it, speakerId, sourceId, text) }
    fun execute(playerId: Long, nominatorId: Long? = null,
                via: ExecutionVia = ExecutionVia.VOTE, force: Boolean = false) =
        update { Execution.execute(it, lookup, playerId, nominatorId, via, force = force) }
    fun noExecution() = update { Execution.noExecution(it) }
    // … etc …

    // ---- WP4: setup, identity, bluffs ----
    // ---- WP6: prompts and briefings ----
    // ---- WP8/9/10/11: UI-only verbs ----
}
```

**Ownership rule for this file:** WP0 creates it with the existing verbs. After that it is
**append-only in the marked per-WP blocks**. Each work package appends only inside its own
`// ---- WPn: … ----` block, so two packages never touch the same lines and git merges
cleanly. `GameViewModel` and `WebGameViewModel` are each edited exactly once, in WP0, to
add `: GameActionsApi` and delete their duplicated wrapper sections.

### 3.4 Cross-cutting rules every package must obey

1. **Never compare a reminder label with `==` or `equals(..., true)`.** Use
   `Tokens.key(sourceId, label)`. Data is official Title Case (lead D5).
2. **Never call `Deaths.kill`-equivalents directly.** Every death goes through
   `Deaths.attempt`; every execution through `Execution.execute`.
3. **Never branch on a character id outside `engine/rules/`.**
4. **Never store what can be derived.** If you are adding a `GameState` field, check §1
   first — the answer is usually the ledger, the effects list, or a `Memory` query.
5. **Never add a wrapper to `GameViewModel.kt` or `WebGameViewModel.kt`.** Use
   `GameActionsApi.kt`.
6. **Never edit `characters.json` / `night_and_jinxes.json` / `night_guide.json`** outside
   WP5. If a registry entry needs a label or copy-count fix, file it to WP5 and use the
   official label meanwhile.
7. **Never render text below 11 sp**, and never put a rule-bearing control behind a
   horizontally scrolling row.

---

## 4. Work packages

Size key: **XS** < 200 changed lines · **S** 200–500 · **M** 500–1200 · **L** 1200–2500 ·
**XL** > 2500. Each package is intended for one agent in its own git worktree.

### Dependency graph

```
WP0  (alone, must land first)
 ├── WP1  effects / status / tokens / deaths / phases
 ├── WP4  identity / setup / bluffs / seats
 ├── WP5  data regeneration            (independent of all Kotlin work)
 ├── WP2  night plan + registry engine        [signatures from WP0; needs WP1, WP4 merged to test]
 ├── WP3  day engine + ledger + execution      [signatures from WP0; needs WP1 merged to test]
 │
 ├── WP6  briefings + phase flow               [after WP1, WP2, WP3]
 ├── WP7a-i registry entries per edition       [after WP2; parallel with each other]
 │
 ├── WP8  NightScreen        [after WP2, WP6]
 ├── WP9  DayScreen          [after WP3, WP6]
 ├── WP10 Grimoire/SeatSheet [after WP1]
 ├── WP11 Setup/Reveal/Home  [after WP4]
 └── WP12 tests + fixtures   [continuous; owns the existing test files]
```

Waves: **W1** = WP0. **W2** = WP1, WP4, WP5. **W3** = WP2, WP3. **W4** = WP6, WP7a–i,
WP10, WP11. **W5** = WP8, WP9. **W12** runs alongside from W2 onwards.

---

### WP0 — Core types, the great split, and migrations · **M** · no dependencies

The only package that touches shared files. It must land alone and be reviewed carefully,
because everything else assumes its signatures are final.

**Owns (and freezes afterwards):**
`GameState.kt`, `Migrations.kt`, `GameActions.kt`, `Character.kt`,
`app/.../ui/GameActionsApi.kt`, `app/.../ui/GameViewModel.kt`,
`web/.../ui/WebGameViewModel.kt`, `app/.../ui/platform/Platform.kt`,
`web/.../ui/platform/WebUiPlatform.kt`, plus the **creation** of every new engine file
listed in §3.1 as a compiling skeleton.

**Does:**
1. Writes `GameState.kt` exactly as §2.2, and `Character.kt` as §2.14 (including the
   tolerant `Team` serializer).
2. **Mechanically splits `GameActions.kt`** — pure moves, zero logic change — into
   `Seats.kt`, `Effects.kt` (reminder primitives), `Deaths.kt` (kill/revive/resurrect/
   ghost vote), `Phases.kt` (advancePhase + the two expiry tables verbatim),
   `DayRules.kt` (nomination helpers), `Setup.kt` (validateBag/randomBag/
   validateSetupState/DUPLICABLE), `Bluffs.kt` (setBluffs/suggestBluffs/setFabled),
   `Identity.kt` (starPass/swapCharacters/snakeCharmerSwap). `GameActions` is left as a
   façade of one-line delegates so **every existing call site and test still compiles**.
3. Creates every other new file from §3.1 as a **compiling skeleton**: all data classes,
   enums and sealed types written in full exactly as §2; all object functions declared
   with the right signature and a body of `TODO("WP2")`. This is what makes WP1–WP7
   purely additive inside files they exclusively own.
4. Writes `Migrations.kt` (§5) and calls `GameState.migrated()` from
   `SavedDataSerializer.readFrom` and `WebStore.load`.
5. Writes `GameActionsApi.kt` with the existing wrappers as defaults; makes both view
   models implement it and deletes their duplicated wrapper blocks.
6. Adds every platform-seam declaration WP8–WP11 will need — `rememberDictation`,
   `alertAtTable`, `setScreenBrightness`, a wake-lock re-request hook — to **both** seam
   files, returning `null`/no-op where unsupported.
7. Adds the three `NightGuideEntry` channels.

**Implements:** all of §2.1, §2.2, §2.14; the plumbing halves of records-and-memory §A/§H,
setup-and-identity §A.1, day-engine §A, status-model §1, night-engine §0.

**Acceptance:**
- `./gradlew :engine:test` passes with **zero** test edits.
- `./gradlew -p tools/uicheck compileKotlin` and
  `./gradlew -p web wasmJsBrowserDistribution` both pass.
- A `GameState` JSON written by the current app decodes, migrates and re-encodes without
  loss of any field a storyteller can see (new `PersistenceTest`).
- `grep -rn "GameActions\." app/src web/src | wc -l` is unchanged (the façade held).
- No file outside this package's list is modified.

---

### WP1 — Effects, status, tokens, the kill funnel · **L** · after WP0

**Owns:** `Effects.kt`, `Registration.kt`, `Tokens.kt`, `Deaths.kt`, `Prompts.kt`,
`Phases.kt`, `StatusEffects.kt`.
**New tests it owns:** `EffectsTest.kt`, `DeathsTest.kt`, `TokensTest.kt`.

**Implements:** status-model §1–§6 in full; night-engine §4 (token lifecycle);
friction-log §1 and §4; grimoire-and-seats §1 (`EffectGroup`) and §6 (one kill path);
lead D3, D4 (mechanism), D5, D6, D9, D14, D24, D29, D32, D33, D36, D41, D46, D52.

**Acceptance:**
- `Status.impairment` implements the recursion verbatim, memoised, with a paradox path
  that does not stack-overflow.
- The 47 status-model tests and the 16-step `killOutcome` precedence all pass, including:
  a Poisoner killed mid-night un-poisons its victim; the Widow/Innkeeper chain;
  a Soldier is **not** poisoned by the No Dashii; Assassin beats every protection while
  the Fool's ability is **not** spent; Monk-protected Imp self-kill produces **no** star
  pass; Zombuul first death → `RegistersDead`; Storm Catcher, Lleech, Vizier, Tea Lady,
  Pacifist, Scapegoat all behave per the table.
- Every `TokenRule` names a `(sourceId, label)` present case-insensitively in
  `characters.json`, with the declared copy count (`GameDataTest`).
- `Phases.advancePhase` computes the dawn briefing **before** sweeping (assert the Monk
  token is present when `Briefings.at(DAWN)` runs).
- No code path can produce `PlacedReminder(sourceId = "")`.
- Countdowns: `courtier/Drunk 1→2→3→gone` over three dusks, `flowergirl` resets rather
  than deletes, `minstrel/Everyone Is Drunk` sits in `storytellerReminders` and never
  impairs the seat under it.

---

### WP2 — Night plan, registry engine, typed info · **L** · after WP0 (test after WP1/WP4)

**Owns:** `NightPlan.kt`, `NightAction.kt`, `CharacterRules.kt`, `NightGuide.kt`,
`InfoCalc.kt`; **deletes** `NightOrder.kt`.
**New tests:** `NightPlanTest.kt`, `NightActionTest.kt`, `InfoCalcTypedTest.kt`.

**Implements:** night-engine §1–§3 and §6; ux/night-screen §A and §D (the engine halves);
friction-log §2, §7, §10; lead D13, D16, D19, D33, D37, D39, D43, D49, D50, D53.

**Acceptance:**
- `NightPlan.build` is pure and idempotent; rebuilding after every mutation is cheap
  enough to call on every recomposition (assert < 5 ms at 15 seats).
- Dynamic insertion works: a Pit-Hag-created Chef gets `StepKey("chef", saintId, FIRST)`
  positioned after the Pit-Hag; a Professor resurrection inserts a `FIRST` re-run; a
  Summoner-created Lleech acts the same night; a mid-night Scarlet Woman promotion lands
  after the cursor with the "out of order" badge.
- Gating: an alive Ravenkeeper is `Skip` and auto-ticked; an Exorcised Pukka is `Reduced`
  and **still kills its standing victim**; a Vigormortis-preserved Minion is `Fire` while
  dead and `Skip` once the Vigormortis dies; a Zombuul is `Skip` on a day-death day.
- Two Village Idiots produce two steps with distinct `StepKey.token`s; ticking one does
  not tick the other.
- `wokeCount` reproduces the wiki's Exorcist/Shabaloth example (answer: 0) and counts
  `WakeCount.INFORMED` wakes as 0.
- `InfoResult.alternatives` is non-empty for every supported id in at least one state, and
  the UI contract "no lie generated ⇒ no heading" is expressible.
- No character id appears in `NightPlan.kt` or `NightAction.kt`.

---

### WP3 — Day engine, ledger, execution, win check · **L** · after WP0 (test after WP1)

**Owns:** `Ledger.kt`, `DayRules.kt`, `Execution.kt`, `WinCheck.kt`, `GameLog.kt`.
**New tests:** `LedgerTest.kt`, `DayEngineTest.kt`, `WinCheckTest.kt`.

**Implements:** day-engine §A–§G in full; records-and-memory §A, §B, §I; friction-log §3
and §8; lead D1, D2, D27, D30, D34, D40, D42, D44, D51.

**Acceptance:**
- The user's request, tested directly: in a plain 8-player Trouble Brewing game with **no
  Gossip in play**, `Ledger.statement(...)` records a statement, and
  `Memory.statementsOn(day)` returns it.
- `Memory.lastChoice("devilsadvocate")` survives two `advancePhase` calls even though the
  `Survives Execution` token was swept — and `forbiddenTargets` slides forward exactly one
  night. (This is the user's Devil's Advocate bug, as an engine test.)
- An execution that kills nobody still writes an `ExecutionRecord`, still blocks further
  nominations, and still satisfies the Vortox.
- `noExecution()` writes a `NO_EXECUTION` row and `nominationsClosed` derives correctly —
  no stored boolean exists anywhere.
- `VoteRules` is frozen on the `Nomination`; exiling the Voudon afterwards does not change
  a recorded tally.
- Vote weights: Bureaucrat 3, Thief −1, Banshee 2, exile always 1.
- `duskCheck` orders Vortox before Mayor and carries the collision caution; the Heretic
  pass inverts every advisory; advisory dedupe keys on `ruleId`.
- `GameLog.rows` is a total order and names voters.
- The 50 day-engine tests pass, and the four existing `GameActionsTest` regression tests
  (thresholds, `Voting.outcome`, nomination bookkeeping, about-to-die) pass **unmodified**.

---

### WP4 — Identity, setup requirements, bluffs, the bag · **L** · after WP0

**Owns:** `Identity.kt`, `Seats.kt`, `SetupRequirements.kt`, `Bluffs.kt`, `Setup.kt`.
**New tests:** `IdentityTest.kt`, `SetupRequirementsTest.kt`, `BluffsTest.kt`.

**Implements:** setup-and-identity §A–§D in full; setup-and-home §S4 and §S5 (engine
halves); friction-log §9; lead D17, D18, D20, D25, D28, D38, D39, D43, D48.

**Acceptance:**
- `Identity.actingRoles` reproduces the whole table in §2.10b, including the Boffin's
  Demon (own ability **plus** granted, `worksWhileImpaired`), a Lunatic waking at slot
  `"lunatic"` with `alwaysFalse`, a Cannibal at the executee's slot, and a Hermit's many
  Outsider steps.
- `Player.nightRoleId` has no remaining callers.
- `starPass` leaves **exactly one** live seat holding the Demon character; the corpse's
  `characterId` is cleared and it shows the heir's old token;
  `duplicateLiveCharacterIds` is empty in every playtest fixture.
- `changeCharacter` preserves alignment unless told otherwise, removes only the abandoned
  character's own effects and tokens, clears SPENT, and queues the reveal + first-night
  re-run prompts.
- Bag shapes: Lil' Monsta at 10 players validates **7/0/3/0** with no `lilmonsta` token in
  the bag; Kazali and Lord of Typhon accept 0 Minions; Summoner accepts 0 Demons; Riot is
  an ordinary Demon and `TEAM_WARPING_IDS` no longer exists.
- `Bluffs.requirements` produces one set per Minion under a Snitch (excluding the
  Marionette), a separate Lunatic set that allows in-play characters, and **no** set at
  all under a Lil' Monsta or an Atheist.
- `SetupRequirements.blockingProblems` reproduces every check `validateSetupState` did,
  plus the 26-row table, and is re-checkable mid-game.

---

### WP5 — Data regeneration from the official source · **M** · after WP0 · **no Kotlin outside `tools/`**

**Owns:** `engine/src/main/resources/botc/data/*.json`, `tools/**` (new regeneration
script), `engine/src/test/kotlin/.../GameDataTest.kt` extensions.

**Does** (lead D22, D23, D31):
1. Writes a `tools/` script that regenerates `characters.json` and
   `night_and_jinxes.json` from **The Pandemonium Institute's official machine-readable
   data** (`github.com/ThePandemoniumInstitute/botc-release`,
   `resources/data/{roles.json, jinxes.json, nightsheet.json}` — 181 characters,
   131 jinxes), merged with the app's own extra fields (`spentLabel`, art hints).
   Vendor a pinned copy of the source files under `tools/` so the build never needs the
   network, and record the upstream commit.
2. Adds the **10 missing characters** — Wraith (Minion), Cacklejack (Traveller) and the
   eight Loric — and the new `LORIC` team.
3. Applies the official **Title Case labels** and **N-copy reminders** throughout
   (`pukka` `Poisoned` ×2, `po` `Dead` ×3 + `3 Attacks`, `shabaloth` `Dead` ×2 + `Alive`,
   `vigormortis` `Has Ability` ×3 + `Poisoned` ×3, `juggler` `Correct` ×5,
   `tealady` `Cannot Die` ×2, `innkeeper` `Safe` ×2, `leviathan` `Good Player Executed` ×2).
4. Fixes the specific data regressions data-accuracy proved: Riot's ability text and
   `setup = false`, Storm Catcher's night procedure and `Stormcaught` label, Duchess,
   Gardener, Deus ex Fiasco, Bootlegger/Sentinel setup flags, the Minstrel's token moving
   to `remindersGlobal`, the Marionette label casing.
5. Rebuilds the night order from `nightsheet.json` (first night 80 entries, other night
   96), including the six missing/misplaced entries.
6. Fixes the **136 `night_guide.json` defects** from data-accuracy §5.1 — the four P0s
   first (`undertaker`, `vortox`, `shabaloth`, `butler`) — adds `DUSK` / `DAWN` /
   `MINION_INFO` / `DEMON_INFO` / `MINION_BLUFFS` marker entries, and adds the
   `day` / `setup` / `reference` channels for the 55 characters with no entry today.
7. Retires the 7 dead jinxes, **including `snitch × marionette`**.

**Acceptance:** a parity test pins `characters.json` against the vendored official data
(181 ids, per-field equality on ability/team/setup/reminders); jinx count is 131; the
night-order lists match `nightsheet.json` exactly; every character has at least one guide
channel; `first`/`other` channels exist **iff** the id is in the corresponding order list;
the four P0 guide sentences are gone. **Data and the code that reads it land in the same
commit** (lead D5).

---

### WP6 — Briefings and the phase flow · **M** · after WP1, WP2, WP3

**Owns:** `Briefings.kt`, `app/.../ui/screens/PhaseFlow.kt` (new, extracted from
`GameShell` by WP0). **New tests:** `BriefingsTest.kt`.

**Implements:** night-engine §5; day-engine §H; records-and-memory §E; ux/day-screen §B;
ux/night-screen §F; friction-log §5 and §6.

**Acceptance:**
- The user's Professor request, end to end: resurrecting a dead player queues an
  `ANNOUNCE` ledger entry and a `RUN_FIRST_NIGHT` prompt; the dawn briefing says
  *"Announce: Bo is alive again."* after the death lines and without a reason; and the
  night plan contains Bo's first-night step.
- A Zombuul's first death appears in `ANNOUNCE` as a real death and in `PRIVATE` as
  "secretly alive".
- `DAY_START` contains the Devil's Advocate protection, the Cerenovus madness **with the
  character they are mad about**, and a `TODO_ASK` for every unrecorded Gossip statement.
- `DUSK` contains "Nobody died today — the Zombuul kills tonight".
- `state.lastDawn != null` after `advancePhase` and the whole state round-trips through
  `Json`.
- `PhaseFlow.request` returns `Blocked` only for **required** steps.

---

### WP7a–i — Per-character registry entries · **M each** · after WP2 · fully parallel

Nine packages, **one file each**, so they can never conflict. Each adds
`internal val <EDITION>_RULES: List<CharacterRule>` and nothing else.

| Package | File | Scope |
|---|---|---|
| WP7-TB | `rules/RulesTroubleBrewing.kt` | 22 Trouble Brewing characters |
| WP7-BMR | `rules/RulesBadMoonRising.kt` | 22 Bad Moon Rising characters |
| WP7-SV | `rules/RulesSectsAndViolets.kt` | 22 Sects & Violets characters |
| WP7-EXP-T | `rules/RulesExpTownsfolk.kt` | ~32 experimental Townsfolk |
| WP7-EXP-O | `rules/RulesExpOutsiders.kt` | ~12 experimental Outsiders |
| WP7-EXP-M | `rules/RulesExpMinions.kt` | ~16 experimental Minions |
| WP7-EXP-D | `rules/RulesExpDemons.kt` | ~11 experimental Demons |
| WP7-TRAV | `rules/RulesTravellers.kt` | all Travellers |
| WP7-FAB | `rules/RulesFabled.kt` | all Fabled + Loric |

**Each agent's source material** is `docs/audit/characters/<id>.md` (the "Proposed
behaviour (spec)" section) plus its group digest in `docs/audit/digest/`. Read only your
own characters; do not read the mechanics specs — this document is their summary.

**Rules for registry entries:**
- Use **official Title Case labels** and declare `TokenRule.copies` matching the N-copy
  reminders in `characters.json` (lead D5/D31). If the data is wrong, file it to WP5 and
  use the official label anyway.
- Every once-per-game character sets `Character.spentLabel` in the data (WP5) and uses
  `Gates.notSpent()` — never a text heuristic.
- Kills use `NightEffect.Attack`, never a direct kill.
- "Different from last night" is `TargetConstraint.DIFFERENT_FROM_LAST_NIGHT`, never a token.
- Deferred harm is either a standing token with `Until.ON_SOURCE_STEP` (Pukka) or a
  `Prompt` — never a comment in the guide prose.
- **Do not edit any data file.**

**Acceptance per package:** every character in scope has a `CharacterRule`; a table test
asserts each declared token exists in `characters.json`; each package adds one
`Rules<Edition>Test.kt` with at least one Given/When/Then per P0 in its characters' audit
files. The BMR package must specifically prove the user's four reports:
**Pukka** poisons on night N and kills that victim at its night N+1 step (with a second
`Poisoned` token placed first, victim dies still poisoned — lead D4);
**Devil's Advocate** cannot pick the same seat two nights running and its token clears at
dusk; **Gossip** statements are recordable and consumed at the night step; **Professor**
queues the dawn announcement and the first-night re-run.

---

### WP8 — NightScreen redesign · **L** · after WP2, WP6

**Owns:** `app/.../ui/screens/NightScreen.kt`, `GameShell.kt`,
`app/.../ui/components/ShowCards.kt`, and a new `ui/screens/night/` package.
Appends only to the `// ---- WP8` block of `GameActionsApi.kt`.

**Implements:** ux/night-screen §A–§J; night-engine's UI notes; friction-log §12.

**Acceptance:** one card, one question, one primary button whose label states the outcome;
the collapsed list shows result-or-ask per row; skipped rows are grey, pre-ticked and
carry `[Run anyway]`; per-holder steps render as separate cards; cards are pre-filled and
exit by press-and-hold onto the privacy cover; card bodies are not tappable; destructive
primaries need a 400 ms hold; the dim control is on the progress strip and persists in
`GameState.dimLevel`; the scrim covers the top bar; nothing renders below 14 sp;
`QuickResolutions` and `DemonKillPanel` are deleted; **no character id remains in the
file** (`grep -c` in CI).

---

### WP9 — DayScreen redesign · **L** · after WP3, WP6

**Owns:** `app/.../ui/screens/DayScreen.kt`, `app/.../ui/components/Timer.kt`, and a new
`ui/screens/day/` package. Appends only to the `// ---- WP9` block of `GameActionsApi.kt`.

**Implements:** ux/day-screen §0–§K; day-engine §I; records-and-memory §F.

**Acceptance:** the day timeline with a fixed bottom bar; **"What was said" works in a
game with nothing in play, in two taps and a sentence**, with a zero-typing "Claims…"
path; nomination is two taps on a pinned seat ring; the `NominationCheck` card renders
between ring and vote panel; "Executed — but they don't die" is a first-class button;
"No execution today?" is asked at dusk when nobody is on the block; the timer survives a
tab switch; secret voting (Organ Grinder) changes the whole tab.

---

### WP10 — Grimoire, seat sheet, kill sheet · **L** · after WP1

**Owns:** `GrimoireScreen.kt`, `SeatSheet.kt`, a new `KillSheet.kt`,
`components/Tokens.kt`, `components/PrivacyCover.kt`, `components/Zoomable.kt`,
`ui/theme/Theme.kt`. Appends only to the `// ---- WP10` block of `GameActionsApi.kt`.

**Implements:** grimoire-and-seats §1–§15.

**Acceptance:** status pips coloured by `EffectGroup` with a team-colour provenance ring
and a glyph ≥ 11 sp; the spacing-driven seat allocator (no overlap or clipping at 7, 12,
15 and 20 seats — a measured test); a Board view listing every token in full text with
filter chips that show counts; seat sheet v2 with a sticky action bar, in-sheet undo and a
History section from `Memory.forPlayer`; **one** `KillSheet` used by all five kill sites,
rendering `Deaths.killOutcome` grouped into "applies to this death" / "not relevant to this
cause"; "Saved by …" is an action that records, not a dismissal; notes auto-commit on
blur; the privacy cover is the top-most window.

---

### WP11 — Setup, hand-out, home, PWA shell · **L** · after WP4

**Owns:** `SetupScreen.kt`, `RevealFlow.kt`, `HomeScreen.kt`, `BluffsSheet.kt`,
`LibraryScreen.kt`, `ReferenceScreen.kt`, `GameExtras.kt`,
`app/.../data/SavedData.kt`, `app/.../data/Persistence.kt`, `web/.../WebApp.kt`,
`web/.../web/Main.kt`, `web/src/wasmJsMain/resources/{index.html,sw.js,manifest.webmanifest}`.
Appends only to the `// ---- WP11` block of `GameActionsApi.kt`.

**Implements:** setup-and-home §S1–§S9; setup-and-identity §C (reveal); fabled-B's
"Fabled reachable everywhere".

**Acceptance:** one scrolling setup screen with a sticky bag tray; paste-a-list and roster
memory; the data-driven "Before the first night" checklist rendering
`SetupRequirements.all`; per-recipient bluff tabs from `Bluffs.requirements`; hand-out mode
with press-and-hold reveal, progress stored in `Player.tokenShownAt`, correct colour for
the **believed** character and an explicit alignment page (never for the Ogre); the
new-game guard archives instead of destroying; `SavedData` gains `schemaVersion`,
`archivedGames` (last 10) and `recentRosters`; save failures surface a banner; the iOS
`visualViewport` keyboard inset is applied.

---

### WP12 — Tests, fixtures and CI gates · **M** · continuous, from W2

**Owns:** every file under `engine/src/test/kotlin/` **except** the per-package test files
named in WP1–WP7 (each of which is owned by its package). Specifically owns the rewrite of
`GameActionsTest.kt`, `SetupTest.kt`, `StatusEffectsTest.kt`, `InfoCalcTest.kt`,
`GameDataTest.kt` and `FullGamePlaytestTest.kt`.

**Does:** keeps the existing suite green through each merge; writes the **playtest
fixtures** — a scripted Bad Moon Rising 12-player game reproducing the user's reported
session end to end (Pukka, Devil's Advocate, Gossip, Professor, Lunatic), plus one
Trouble Brewing, one Sects & Violets and one Teensyville fixture; adds the CI gates:
`grep` assertions that no character id appears in `NightScreen.kt`/`DayScreen.kt`, that
`GameViewModel.kt`/`WebGameViewModel.kt` contain no `GameActions.` call, and that no label
literal outside `engine/rules/` is compared with `==`.

**Acceptance:** the BMR playtest fixture passes and asserts each of the user's five
complaints is fixed; `./gradlew :engine:test`, `./gradlew -p tools/uicheck compileKotlin`
and `./gradlew -p web wasmJsBrowserDistribution` are all green on every merge.

---

### 4.1 Shared-file hazards and how they are sequenced

| File | Wanted by | Resolution |
|---|---|---|
| `GameState.kt` | everyone | **WP0 only, then frozen.** Any later field addition is a lead-approved amendment to this document, landed as a WP0-style patch between waves. |
| `GameActions.kt` | everyone | Gutted to a façade in WP0 and frozen. New verbs go on the new objects; the UI calls those directly. |
| `GameActionsApi.kt` | WP8–WP11 | One file, **append-only inside per-WP marked blocks**. Never reorder or reformat other blocks. |
| `GameViewModel.kt` / `WebGameViewModel.kt` | everyone, historically | Edited **once**, in WP0. Touching them afterwards is a review failure. |
| `Phases.kt` | WP1 (owner), WP2/3/6 want hooks | WP0 writes the final pipeline shape; WP1 fills it; WP2/WP3/WP6 implement the functions it calls **in their own files**. |
| `InfoCalc.kt` | WP2 (owner), WP7 wants new ids | WP7 files requests to WP2; WP2 lands them in one batch per wave. |
| Registry files | WP7a–i | One file per package; `CharacterRules.kt` (WP2) only concatenates them. |
| Data JSON | WP5 only | Registry packages use official labels and file corrections to WP5. |
| `GameShell.kt` | WP8 (owner) | Phase logic extracted to `PhaseFlow.kt` (WP6) and extras to `GameExtras.kt` (WP11) by WP0, so WP8 owns only tabs, scaffold, top bar and scrim. |
| `Theme.kt` | WP10 only | WP8/WP9 use the tokens WP10 defines; colour additions are filed to WP10. |
| Platform seams | WP0 only | Every function WP8–WP11 needs is declared up front. |
| `engine/src/test/**` | everyone | WP12 owns the existing files; each package owns its own new `*Test.kt`. |

---

## 5. Migration and compatibility

### 5.1 The one migration entry point

```kotlin
// engine/.../Migrations.kt   (WP0, then frozen)

/** Current save schema. Bump only when a migration step is added below. */
const val SCHEMA_VERSION = 2

/**
 * Folds every legacy field into its modern home. Idempotent, pure, and called
 * exactly twice in the whole app: `SavedDataSerializer.readFrom` (Android) and
 * `WebStore.load` (PWA). Never call it from a screen.
 */
fun GameState.migrated(lookup: (String) -> Character?): GameState
```

Steps, in order:

1. **`demonBluffIds` → `bluffSets`.** If `legacyDemonBluffIds` is non-empty and
   `bluffSets` has no `"demon"` key, add it and clear the legacy field.
2. **`fabledIds` → `fabled`.** Each legacy id becomes `FabledEntry(id)`.
3. **`Player.note` → `Player.notes`.** A non-empty legacy note becomes one `SeatNote`
   stamped with the current cycle and phase.
4. **`Player.alignmentFlipped` → `Player.alignment`.** When the flag is set, store the
   explicit opposite of the character's natural alignment.
5. **Tokens → effects.** For every `PlacedReminder` on every seat, look up
   `Tokens.rule(sourceId, label)` **case-insensitively**. If a rule with a non-null
   `effect` exists, mint an `Effect` (stamped `createdCycle = state.cycle`,
   `sourcePlayerId` resolved to the living holder of `sourceId` if there is exactly one)
   and **remove the token from the seat** — it will now be rendered from the effect.
   Tokens with no rule (homebrew, storyteller improvisations) stay on the seat untouched.
   Any token with `sourceId == ""` is rewritten to `sourceId = "st"`.
6. **`DeathRecord` → `DeathEvent`.** Field-compatible: `day`, `atNight`, `cause`,
   `characterIdAtDeath`, `abilityImpairedAtDeath` and `resurrected` keep their serial
   names, so no work is needed beyond stamping `id` from `nextDeathId` and mapping the
   deprecated `DEMON` → `DEMON_KILL` and `OTHER_NIGHT_DEATH` → `DEMON_KILL` when the
   killer was a Demon, else `STORYTELLER`.
7. **`nightStepsDone`.** No migration needed: `StepKey("poisoner").token == "poisoner"`,
   so an old set of bare ids still matches every simple step. Per-holder steps are simply
   unticked on the first night after upgrade, which is correct and harmless.
8. Stamp `GameState.id` if empty, and `SavedData.schemaVersion`.

### 5.2 Serialization rules

- Both platforms keep `Json { ignoreUnknownKeys = true; encodeDefaults = true }`. Do not
  change either flag: `ignoreUnknownKeys` lets a **newer** save load in an older build
  without crashing at the table, and `encodeDefaults` is what makes the legacy
  `@SerialName` fields round-trip.
- **Enums serialise by name.** `DeathCause`, `LedgerKind`, `EffectKind`, `Until`,
  `Phase`, `Team`, `NominationResult`, `ExecutionOutcome`, `ExecutionVia`, `Verdict`,
  `StepVariant`, `BriefingSlot`, `PromptKind`: append new values, never rename an
  existing one without an `@SerialName`. `Team` additionally needs the tolerant
  serializer from §2.14 so an unknown official team does not break the dataset load.
- Sealed types (`StepGate`, `NightAction`, `NightEffect`, `Ref`, `Answer`) are
  polymorphic; declare them `@Serializable` with the default sealed-class discriminator
  and never rename a subclass without `@SerialName`.
- Anything reachable from `GameState` must be `@Serializable`. That includes `Briefing`
  (because `lastDawn`/`lastDusk` are stored) but **not** `CharacterRule`, `NightRule`,
  `StandingRule`, `DeathTrigger` or `SetupRequirement` — those hold lambdas, live only in
  the code registry, and are never serialised or compared.

### 5.3 Undo snapshots

- Undo stays an in-memory `ArrayDeque<GameState>` in each view model. Because migration
  runs at load, every snapshot in the stack is already post-migration — there is no
  mixed-schema hazard.
- The ledger, prompts, effects and executions are all append-only, so undo is
  automatically correct: restoring an older `GameState` restores the smaller
  `nextLedgerId` / `nextEffectId` / `nextPromptId` counters too, and a redo after an
  unrelated edit cannot mint a duplicate id. **Do not** move any counter outside
  `GameState`.
- `MAX_HISTORY` stays 100 in memory. **Persisted undo is out of scope** (it needs the
  script extracted from `GameState` first — follow-up F-1).
- Every mutation should carry a label so undo can be named
  (`update(label = "Removed 'Poisoned' from Dana") { … }`) — a WP10 addition to
  `GameActionsApi`.

### 5.4 PWA localStorage

- `GameState` grows by roughly one `Effect`, one or two `LedgerEntry`s and occasionally a
  `Prompt` per storyteller action — on the order of 100–300 bytes each. A full 15-player
  game should land well inside 1 MB, but the **script is still embedded in every
  `GameState`**, so a large homebrew script dominates the save.
- `WebStore.save` must stop swallowing exceptions: return `Boolean`, expose
  `saveFailed: StateFlow<Boolean>` from `WebGameViewModel`, and show a persistent red
  banner — *"Not saving — browser storage is full. Copy the game log now."* (WP11).
- Call `navigator.storage.persist()` once at boot; if it returns false, show the
  add-to-home-screen hint once (WP11).
- Archived games are capped at the last 10 and are stored **without** their undo history.

### 5.5 Backwards-compatibility guarantees to test

`PersistenceTest.kt` (WP0) must assert all of:
1. A `GameState` JSON captured from the **current shipped app** decodes, migrates and
   re-encodes with every storyteller-visible fact intact.
2. A JSON with **none** of the new fields decodes to sensible defaults.
3. A JSON containing **unknown** future fields decodes without throwing.
4. A `PlacedReminder` with only `sourceId` and `label` decodes.
5. `bluffSets`, `fabled`, `notes` and `alignment` all round-trip after migration, and the
   legacy fields are cleared so the migration is idempotent.

### 5.6 Deferred follow-ups (explicitly out of scope for WP0–WP12)

- **F-1 — extract `Script` from `GameState`** into `SavedData` plus a `scriptId`. It is
  the single biggest save-size win and a prerequisite for persisted undo, but it touches
  every file and would destroy this plan's disjoint ownership. Schedule as its own wave.
- **F-2 — persisted undo/redo** (needs F-1).
- **F-3 — multiple concurrent saved games** beyond the archive list.
- **F-4 — `night_guide.json` → `character_guide.json`** rename (cosmetic; the channel
  additions in WP5 deliver all of the value).

---

## 6. Open rules questions for the lead

`DECISIONS.md` settled most of what the audits flagged. These are what remains genuinely
undecided. Each names the default the engine should ship with, so no package is blocked —
but each default must be **visible and overridable in the UI**, never silent.

| # | Question | Why it is open | Ship-with default | Blocks |
|---|---|---|---|---|
| **Q1** | The **7+ threshold** for Minion/Demon info: do Travellers count? | The text says *"7 or more players"*, not "residents". The wiki gives no ruling. Lead D8 says **non-traveller seats**, which contradicts the literal wording | Count **non-traveller seats** (lead D8), show the count in the step — *"6 residents + 1 Traveller — Minion info OFF. [Count the Traveller]"* — and store the override in `Decisions.COUNT_TRAVELLERS_FOR_INFO` | WP2 gate, WP7-EXP (Lunatic's fake Minions use the same gate) |
| **Q2** | **Minstrel**: does the all-players drunkenness end early if the Minstrel later dies or is impaired? | Lead D15 sets `endsWithSource = false`, which means "no". But the general rule in status-model §B is that every effect ends when its source loses their ability, and the Minstrel is not listed as an exception on the wiki | **No** — it runs to dusk after the next day regardless (lead D15), **plus** a `Prompt` when the Minstrel dies mid-effect so the storyteller can rule otherwise | WP7-BMR |
| **Q3** | Do **Riot**, **Leviathan**, **Lil' Monsta**, **Yaggababble** and **Al-Hadikhia** deaths count as *"the Demon's ability killed you"* for Sage, Grandmother, Choirboy, Ravenkeeper and Farmer? | The wiki does not rule. Indirect evidence: Riot carries explicit jinxes with Sage, Ravenkeeper, Farmer and Banshee, which implies those abilities would otherwise fire | `KillCause.demonKillUncertain = true` on all five; the kill panel shows one toggle *"Counts as a Demon kill?"*, defaulting to **yes** | WP7-EXP-D, WP1 |
| **Q4** | **Riot / Leviathan jinx night actions**: lead D19 makes them jinx-gated `NightAction`s active only when the jinxed character is on the script. Does the Riot/Leviathan then wake **every** night to choose, or only when the jinxed character is alive? | The official text (*"Each night\*, Riot chooses an alive good player (different to previous nights)"*) reads as unconditional, but the character has no other night action | Wake only while the jinxed character is **in play** (not merely on the script); otherwise `StepGate.Skip` with the reason | WP7-EXP-D |
| **Q5** | **Lil' Monsta's `[+1 Minion]`**: 7/0/3/0 with no Demon seat, or 6/0/3/1 with the token dealt to a seat? | Lead D18 rules 7/0/3/0. The current code and two existing tests assert the other reading | **7/0/3/0**, no `lilmonsta` token in the bag, held by a babysitting Minion. `SetupTest.kt:64-71` and `GameActionsTest.kt:219-228` must be **changed**, which is why this needs to be visibly signed off | WP4, WP12 |
| **Q6** | **Exile threshold and ghost votes on an exile.** | Both `/Exile` and `/Traveller` wiki pages 404; only the Butler page's *"exiles are never affected by abilities"* is quotable | Keep today's behaviour: threshold = half of **all** seats rounded up; dead players may support an exile **without** spending a ghost vote; no weights apply. Confirm against the printed rulebook before locking the test | WP3 |
| **Q7** | **Vizier alive at ≤3 → evil wins?** | day-engine could not find a wiki page carrying this clause | Do **not** implement it. Surface as a `WinCheck` caution only | WP3, WP7-EXP-M |
| **Q8** | Does the **Drunk's believed character's setup bracket** apply, the way the Marionette's explicitly does? | Explicit jinxes cover the Marionette's Huntsman and Balloonist. Nothing covers the Drunk | Do **not** apply it; raise an advisory question at setup | WP4 |
| **Q9** | **Legion's bag count.** | The wiki gives a *recommendation* (~7 Legion to 3 good at 10 players), not a bound | `BagShape(advisory = true)` — warn, never block | WP4 |
| **Q10** | **Lleech's `DEATH_TIED_TO`** end condition. | The wiki states no end condition | Treat as "while the Lleech has its ability", which the effect recursion gives for free; flag it in the seat badge | WP1, WP7-EXP-D |
| **Q11** | **Mayor / Vortox dusk collision.** | Both can fire at the same dusk; there is no official jinx | Show **both**, Vortox first, with an explicit collision caution. The storyteller decides | WP3 |
| **Q12** | **Courtier's countdown direction.** | Official data counts **up** (`Drunk 1` → `Drunk 2` → `Drunk 3`); the app counts down. Also: does the Courtier's own step advance it, or dusk? | Count **up** per the official labels; advance at **dusk** (lead D14), with the Courtier's step showing the current value | WP1, WP7-BMR |
| **Q13** | **16–20 player distributions.** | The official table stops at 15; the app extrapolates | Keep the extrapolation but mark it *"Officially, a 16th player is a Traveller."* in the wizard | WP4, WP11 |
| **Q14** | **Vigormortis's poisoned neighbour** when a neighbour is dead. | Lead D12 says offer both Townsfolk neighbours and let the ST pick — but the rule is silent on whether dead seats are skipped | Offer **both**, ST picks (lead D12) | WP7-SV |

---

## 7. Cheat sheet for registry agents (WP7a–i)

Everything a per-character package needs, on one page. If your character needs something
not listed here, it belongs in the engine, not in your file — file it to the owning package.

**Where behaviour goes**

| The audit says… | You write… |
|---|---|
| "wakes on the first night to…" | `firstNight = NightRule(gate = …, action = …, prompt = "…")` |
| "each night\*" | `otherNight = NightRule(...)` |
| "only if alive" | `gate = Gates.aliveHolder` (the default) |
| "even when dead" | `actsWhileDead = true` + `gate = Gates.actsWhileDead` |
| "once per game" | `Character.spentLabel` (data, WP5) + `gate = Gates.all(Gates.aliveHolder, Gates.notSpent())` + `NightEffect.MarkSpent` |
| "choose a player" | `ChoosePlayers(min = 1, max = 1, constraints = listOf(ALIVE, NOT_SELF))` |
| "different to last night" | add `TargetConstraint.DIFFERENT_FROM_LAST_NIGHT` — **never** a token |
| "they die" | `NightEffect.Attack(Ref.Target, cause = <your killCause>)` |
| "they are poisoned" | `NightEffect.PlaceToken(id, "Poisoned", Ref.Target, kind = POISONED, until = …)` |
| "…until dusk" | `Until.DUSK`; "tonight" → `Until.DAWN`; "until my next wake" → `Until.ON_SOURCE_STEP` |
| "safe from the Demon" | `standing = StandingRule { … SAFE_FROM_DEMON … }` (innate) or a `PlaceToken(kind = SAFE_FROM_DEMON)` (chosen) |
| "when they die, …" | `onDeath = listOf(DeathTrigger(gate = …, produce = …))` |
| "at dawn, announce…" | `NightEffect.Announce(BriefingSlot.DAWN, "…")` |
| "tomorrow, …" | `NightEffect.QueuePrompt(at = DAY_START, …)` |
| "the storyteller must remember X said Y" | a `DayAbility(recordsAs = "<id>")` + `NightRule.action` consuming `Memory.unresolved(...)` |
| "learns how many / who" | `NightRule(infoId = "<id>")` and an `InfoCalc` case (file to WP2 if missing) |
| a setup obligation | `setup = listOf(SetupRequirement(id = "<char>.<thing>", …))` |
| a bag change | `bagShape = { base, n -> BagShape(...) }` |
| "if X is also on the script, …" | `jinxRules = mapOf("x" to NightRule(...))` |

**Hard rules**

- Official **Title Case** labels only; declare `TokenRule.copies` to match the N-copy
  reminders in `characters.json`. Never `==` on a label — the engine compares for you.
- Never call a kill directly; never write to `Player.reminders`; never read
  `Player.nightRoleId` (deleted); never branch on another character's id outside
  `jinxRules` or a `StandingRule`.
- "Chose nobody" is a real answer: give the action `allowNone = true` and a
  `noneLabel`, and record it (the Po depends on it).
- If a rule is genuinely ambiguous, do **not** guess: add a
  `Prompt(kind = DECIDE)` or a `StepGate.Conditional` question, and note it in your
  package's final report so the lead can add it to §6.

---

*End of ARCHITECTURE.md. Amendments to §2 or §4 ownership require the lead's sign-off and
must be landed between waves, never inside one.*
