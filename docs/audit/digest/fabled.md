# Fabled — requirement cards

## Group notes (read before any Fabled work package)
1. **`fabledIds: List<String>` is the root cause of 14 of 17 cards.** Adopt **both** competing proposals: `fabled: List<FabledEntry>` (`id`, `playerIds`, `spentBy: Set<Long>`, `used`, `note`, `addedOnCycle`) for the generic payload, **and** `fabledConfig: Map<String, FabledConfig>` for typed config (Sentinel `outsiderDelta`, Storm Catcher `favouredCharacterId`, Revolutionary pair, Spirit of Ivory `baselineEvil`, Toymaker `skipUsed`, Deus ex Fiasco `mistakes`). `fabledIds` survives as `get() = fabled.map { it.id }`.
2. **Fabled reminder tokens are unreachable on built-in scripts** — `ReminderPicker` (`SeatSheet.kt:492-500`) sources `resolve(script)`, and `GameData.builtIn()` (`GameData.kt:35-42`) filters `isTownResident`. Only Duchess/Storm Catcher/Toymaker escape, via `NightToolTray`, because they have night steps. One-line fix serving 8 cards: union in `state.fabled.mapNotNull { gameData.character(it.id) }` as a "Fabled" group. Imported scripts already behave differently today.
3. **Grimoire-level reminders have no container** — `PlacedReminder` only attaches to a `Player` (`GameState.kt:29`), but WHOOPSIE, NO MORE EVIL, SOMETHING BAD and the Fibbin's NO ABILITY belong on the *Fabled token*. Use `records-and-memory.md §A`'s `GameState.storytellerReminders`, rendered in the grimoire centre (`GrimoireScreen.kt:228-234`).
4. **Fabled cannot be selected at setup** — `FabledSheet` lives in `GameShell` (`:239, :501`), i.e. after `startGame`. Sentinel, Gardener, Revolutionary, Storm Catcher, Spirit of Ivory and Deus ex Fiasco are all setup-time; Deus ex Fiasco may not legally be added after setup at all. `SetupScreen` needs a Fabled stage writing `state.fabled` before the bag is built.
5. **`validateBag` callers drop `fabledIds`** — the engine's Sentinel support (`GameActions.kt:443-455`) is correct and complete, but `SetupScreen.kt:356` and `randomBag` (`GameActions.kt:397`) both omit the ids, so the one working Fabled is invisible where it matters. Two-line fix + a `fabledIds` parameter on `randomBag`.
6. **`finalDay` is one flag serving three Fabled** — `GameState.finalDayCycle: Int? = null`, set by storyteller declaration, never inferred, prompted at dawn when `aliveCountResidents <= 3`. Ferryman fires its restore on it, Angel shows its "remove me" nudge on it, Fiddler can be offered on it.
7. **Jinx dataset: 58 rows vs the wiki's 131** — 80 missing, 38 drifted (several *reversed*: `baron×heretic`, `grandmother×riot`, `boffin×drunk`, `plaguedoctor×scarletwoman`, `courtier×summoner`, `alchemist×summoner`), 7 app-only pairs to verify. **`docs/audit/characters/djinn.md` §"Missing rules (80)" holds all 80 verbatim and id-normalised, paste-ready.** A data work package on its own, and the Djinn's entire point.
8. **Conflicts for the lead to rule on.** (a) *No `EffectKind` expresses a vote ban* — Angel and Hell's Librarian both need "can't vote today"; add `EffectKind.NO_VOTE` (until = DUSK) and make `DayRules.canVote` read it. (b) *Two `ExecutionRecord` shapes* — cards use `day-engine.md §A`'s (the Angel needs its `nominatorId`), not `status-model.md §4`'s. (c) *`DayEntry` vs `LedgerEntry`* — cards use `LedgerEntry`/`LedgerKind` per the brief; `DayEntryKind` maps onto `LedgerKind` + `sourceId`. (d) *Storm Catcher is already a `StandingRule`* in `status-model.md §1`, but that rule needs `fabledConfig.favouredCharacterId`, which does not exist yet.
9. **`night_guide.json` has no day/setup section** — `NightGuideEntry` is `first`/`other` only (`NightGuide.kt:36-40`), and 13 of 17 Fabled are day- or setup-only, so their How-to-Run text has nowhere to live. Duchess is the only Fabled with a guide entry today.
10. **Fabled immunity is a cross-cutting suppression, not a per-card note** — "cannot die or lose their ability" and "do not count for the two-alive evil win" must be honoured by `Status.impairment` (no reason ever returned for a Fabled-sourced effect), by `WinCheck`'s alive counts, and by `InfoCalc`'s impairment caveats (the Duchess's number is true even for a poisoned visitor; Vortox does not invert it).

---

## angel — Angel · fabled fabled · P0:3 P1:3
today: a bare toggle; the only reachable "protected" chip makes the app say the player **can't
die** — the exact inverse of the rule — and a PROTECTED death triggers nothing at all.
data:
  - characters.json: `angel.reminders` `["Protect","Something Bad"]` →
    `["Protected","Protected","Protected","Something Bad","No ability","Can't vote"]`
    (wiki token is **PROTECTED**; needs one copy per protectee; the two penalty tokens must
    exist in data before they can be placed)
  - night_and_jinxes.json: ok — no jinx pair names `angel`; absent from both order lists (keep)
  - night_guide.json: no `angel` entry; needs the new day/setup section carrying How-to-Run +
    the penalty menu wording
setup: `angel.protectees` · MULTI_SELECT_SEATS · "Who does the Angel protect? Ask each
  player's consent first. They still die normally; whoever is most responsible for their death
  gets a penalty." · candidates = all seats · validation: none blocking (empty = warn "Angel is
  in play but protects nobody"). Confirm writes `FabledEntry.playerIds` and places
  `PlacedReminder("angel","Protected")` on each.
identity: plain — the Angel has no seat; its tokens are seat-attached, its penalties are too.
night: never wakes — no `StepKey("angel")` in either `NightPlan` (regression guard).
day: `DayBriefing.Note(DAY_START, INFO, "angel", "Angel protects Ana, Ben — they die normally")`
  · one ACTION note per live penalty ("Cai has no ability today (Angel)") ·
  `DayRules.canVote(...)` must return `Right(false, "Angel penalty")` for a seat holding an
  `angel` `NO_VOTE` effect, and the vote chip renders disabled with that reason ·
  final-day nudge when `finalDayCycle != null` (or `aliveCountResidents <= 3`):
  "Remove the Angel? On the final day players should be free to execute a protected player."
  with a one-tap remove that clears every `angel:Protected` token ·
  ledger: `LedgerEntry(kind = RULING, sourceId = "angel")` for each penalty chosen.
death: **no protection.** `killOutcome` must never read `angel:Protected` as protective —
  it maps to `EffectKind.MARKER`, not `SAFE_FROM_DEMON`. Today `StatusEffects.kt:64-71`
  matches on `label.lowercase()` alone, so it must match on `(sourceId, label)`.
  `DeathTrigger("angel")`: matches any `DeathEvent` whose `playerId` holds `angel:Protected`
  (any cause, night or day, execution included); produces
  `Prompt(at = NOW, kind = CHOOSE_PLAYER, title = "Angel: <victim> was protected — who was
  most responsible?")` with candidates pre-seeded from
  `ExecutionRecord.nominatorId` when `cause.kind == EXECUTION`, and from the Demon's holder
  when `cause.kind == DEMON_ABILITY`, plus every other seat and "Nobody / decide later".
  Penalty options, each applied atomically and undoably:
  `Dies` → `killOutcome(responsible, KillCause(STORYTELLER))` ·
  `No ability today` → `Effect(NO_ABILITY, sourceCharacterId="angel", sourcePlayerId=null,
  until=DUSK, endsWithSource=false, label="No ability")` ·
  `Can't vote today` → `Effect(NO_VOTE, …, until=DUSK, label="Can't vote")` — **new
  EffectKind, see group note 8a** · `Mark only` → `angel:Something Bad`, `Until.FOREVER`.
  TokenRules: `angel:Protected` NEVER · `angel:Something Bad` NEVER ·
  `angel:No ability` DUSK (`impairs = true`) · `angel:Can't vote` DUSK.
  A real protection (Monk/Soldier/Innkeeper/Devil's Advocate) on the same seat still applies
  and both notes must render, clearly separated.
ledger: who is protected + consent asked; the responsible player and penalty per victim;
  Angel added/removed with the cycle; every penalty expiry.
tests:
  - Given `angel:Protected` on Ana, When `deathNotes`/`killOutcome` runs, Then no "can't die"
    note and no `Blocked` outcome; a note beginning "ANGEL:" is present.
  - Given `PlacedReminder("","Protected")` on Ana and `("angel","Protected")` on Ben, Then Ana
    reads as protected and Ben does not (today both do — label-only match).
  - Given Ana holds `angel:Protected` and is executed off Ben's nomination, Then a Prompt is
    queued with `suggested = ben`, reason "nominated them".
  - Given `angel:Can't vote` on Ben during DAY 2, Then `DayRules.canVote(ben)` is false, and
    after DAY→NIGHT the effect is gone while `angel:Something Bad` on Cara survives.
  - Given script = built-in `bmr` and `angel` active, Then the reminder catalogue contains
    `("angel","Protected")` and `("angel","Something Bad")`.
open: what "most responsible" means for a Gossip/Slayer/Godfather kill — the prompt offers
  every seat, so nothing is decided for the storyteller; confirm that is acceptable.

## bootlegger — Bootlegger · fabled fabled (wiki: **Loric**) · P0:0 P1:3
today: the app has real homebrew support (`Script.customCharacters`, night-order slotting,
"N homebrew" chips) and never connects any of it to the Bootlegger; a homebrew night character
with no order number silently vanishes from the night sheet.
data:
  - characters.json: text matches the wiki; consider `"edition": "loric"` or a display label
    (P3, cosmetic)
  - night_and_jinxes.json: ok — absent from both lists, must stay absent (regression guard)
  - night_guide.json: add a setup entry with the How-to-Run announcement text
setup: `bootlegger.auto` · DERIVED · on `newGame` and on any script change, add
  `FabledEntry("bootlegger")` when `script.customCharacters.isNotEmpty()`; the FabledSheet row
  renders **disabled** with "In play because this script has homebrew characters" (wiki:
  "can only be removed by switching to a script that does not contain any homebrew
  characters"). A manually added Bootlegger with no homebrew *is* removable.
  · `bootlegger.houseRules` · TEXT (multi-line) · "What homebrew characters or rules are you
  using?" · pre-seeded one line per custom character (`"<Name> (<team>): <ability>"`) ·
  stored in `FabledEntry.note` · validation: none.
  · `bootlegger.announce` · GATE before SETUP→NIGHT · non-blocking, dismissible:
  "Bootlegger — announce before the bag goes round" listing homebrew characters + the note
  text, `[Show the group]` → `ShowCard.SheetCard(customIds)` · `[Announced — begin night]` ·
  `[Later]`. **Share one dialog with the Djinn's announcement** (two sections, one screen).
  · `validateSetupState` gains: a custom character whose ability text contains
  "each night"/"at night"/"tonight" but which produces no night step under
  `NightOrder.kt:190-193` is reported by name, with an inline field to assign a position.
identity: plain
night: never wakes. But `NightPlan.build` must keep slotting `script.customCharacters`
  (today `NightOrder.kt:183-207`) and must surface the "looks like a night character, has no
  position" case rather than dropping it.
day: none.
death: none.
ledger: `LedgerEntry(kind = RULING, sourceId = "bootlegger")` holding the house-rule text, and
  an `ANNOUNCE` entry with `delivered` set once the group has been told.
tests:
  - Given a script with one inline custom character, When `newGame` runs, Then
    `state.fabled.map { it.id }` contains `"bootlegger"`.
  - Given that state, When `setFabled` drops `"bootlegger"`, Then the entry survives (or the
    action returns an explanatory rejection); on a built-in script a manual one is removable.
  - Given `FabledEntry("bootlegger", note = "Whispers only in pairs")`, Then it round-trips
    and appears in the setup announcement payload.
  - Given a custom character with `otherNight = 0`, blank `otherNightReminder` and an ability
    containing "Each night", When `validateSetupState` runs, Then an issue names it.
  - Given `fabled = [bootlegger]`, Then neither night plan contains a `"bootlegger"` step.
open: whether the Bootlegger should be typed `loric` in data or only labelled in the UI.

## buddhist — Buddhist · fabled fabled · P0:0 P1:2
today: a toggle and a corner token. Who the veterans are is never stored, and the two-minute
silence — which fires at **every** dawn — is never started, prompted or announced.
data:
  - characters.json: `buddhist.reminders` `[]` → `["Silent","Silent","Silent"]`.
    The label must avoid the substrings "poison"/"drunk" so the `StatusEffects` fallback
    (`StatusEffects.kt:38-42`) can never read it as impairment; `Silent` is safe.
  - night_and_jinxes.json: ok — absent from both lists (regression guard)
  - night_guide.json: add a day entry with the How-to-Run text and the consent script verbatim
setup: `buddhist.veterans` · MULTI_SELECT_SEATS + per-seat "consent asked" checkbox ·
  "Who is a Buddhist? They must stay silent for the first 2 minutes of each day. Ask their
  consent first: 'Do you mind if I make you a Buddhist for this game?'" · candidates = all
  seats · validation: none blocking; empty selection makes the dawn trigger say "Buddhist is
  in play but no players are selected." Confirm writes `FabledEntry.playerIds` and places
  `("buddhist","Silent")` on each.
identity: plain
night: never wakes — no step in either `NightPlan`.
day: the whole card. On every NIGHT→DAY transition while `playerIds` is non-empty:
  (1) start a 120 s wall-clock-anchored countdown whose end time lives **in `GameState`**
  (`buddhistTimerEndsAt: Long`), not in `rememberSaveable` — so it survives tab switches,
  undo/redo, and PWA process death, and is visible on every tab (today `DiscussionTimer` is
  rendered only on the Grimoire and Day tabs, `GameShell.kt:314-320`);
  (2) `DayBriefing.Note(DAY_START, ACTION, "buddhist", "BUDDHIST — Lachlan, Lewis silent ·
  1:47")`, replaced at zero by "…time is up, they may talk" until dismissed, rendered in the
  Mastermind-day banner style (`GameShell.kt:513-531`);
  (3) `ShowCard.Message("SILENCE", "Buddhists: 2 minutes")` one tap away;
  (4) banner buttons `[Restart 2 min]` and `[End early]` (wiki licenses ST judgment).
  Explicitly **not** wired into `checkNomination` — a silent player may still nominate, and a
  storyteller who wants to stop them mid-silence does it verbally.
death: none. `buddhist:Silent` is `EffectKind.MARKER`, `Until.FOREVER`, removed only when the
  Fabled is removed or the player deselected; **not** in `EXPIRES_AT_DAWN`/`DUSK`.
ledger: the veteran list + consent; an `ANNOUNCE` entry per day with `delivered`;
  a `RULING` entry when the Buddhist is added or removed mid-game (the wiki requires the
  removal to be declared).
tests:
  - Given `setFabled` adds `buddhist` with two `playerIds`, Then both ids round-trip and each
    seat holds `("buddhist","Silent")`.
  - Given phase NIGHT cycle 2 with two veterans, When `advancePhase` runs, Then
    `buddhistTimerEndsAt ≈ now + 120_000` and the DAY_START briefing names both players.
  - Given `buddhist` active with empty `playerIds`, When dawn breaks, Then no timer starts and
    the briefing says "Buddhist is in play but no players are selected."
  - Given `("buddhist","Silent")` on Lachlan, Then `Status.impairment(lachlan)` is empty.
  - Given two seats holding the token, When the Fabled is removed, Then no seat holds it and
    the ledger records the removal.
open: none.

## djinn — Djinn · fabled fabled · P0:2 P1:4
today: the app ships 58 jinxes against the wiki's 131 and computes the "in play" list from
**assigned characters**, so a script whose jinxed pair wasn't dealt shows "Jinxes in play (0)"
— precisely the information leak the wiki's own example 2 warns about.
data:
  - night_and_jinxes.json: **replace the 58-row `jinxes` array with the wiki's 131, verbatim.**
    The 80 missing rules are listed in full, id-normalised and paste-ready, in
    `docs/audit/characters/djinn.md` §"Missing rules (80)". Reversed rules to correct at
    minimum: `baron×heretic`, `heretic×pithag`, `heretic×lleech` → "Only 1 jinxed character
    can be in play." · `grandmother×riot` → "If Riot is in play and the Grandchild dies by
    execution, evil wins." · `boffin×drunk` → "The Demon cannot have the Drunk ability." ·
    `plaguedoctor×scarletwoman` → "If the Storyteller would gain the Scarlet Woman ability, a
    Minion gains it, and learns this." · `courtier×summoner` → "If the living Summoner has no
    ability, the Storyteller has the Summoner ability." · `alchemist×summoner` → "The
    Alchemist-Summoner does not get bluffs, and chooses which Demon but not which player. If
    they die before this happens, evil wins. [No Demon]" · the four Leviathan/Riot
    "wakes to use their ability" rows become the nightly-choice wording · the three Marionette
    placement rows consolidate to "If there would be a Marionette in play, they enter play
    after the Demon & must start as their neighbor." Verify-then-keep-or-drop the 7 app-only
    pairs: `damsel×marionette`, `marionette×poppygrower`, `marionette×snitch`,
    `gambler×lycanthrope`, `riot×saint`, `poisoner×summoner`, `choirboy×kazali`.
    Add provenance: `jinxSource`, `jinxFetchedAt`, `jinxCount` (defaulted on `NightAndJinxes`).
    Give each jinx an optional `effect` field so order/text changes can be *applied*, not just
    displayed (`night-engine.md §Data changes` asks for the same).
  - characters.json: ok
  - night_guide.json: no entry needed — never wakes
setup: `djinn.auto` · DERIVED · add `FabledEntry("djinn")` on `newGame`/script change when
  `scriptJinxes(script)` is non-empty; FabledSheet row reads "In play — this script has N
  jinxes" and is removable (the wiki's "add the Djinn to all games with a jinx icon" is
  advice, not the Bootlegger's hard lock).
  · `djinn.announce` · GATE before SETUP→NIGHT · "Djinn — read these to the group before the
  bag goes round. N special rules apply this game, whether or not the characters are in play."
  listing every pair + reason · `[Show the group]` → new `ShowCard.RulesCard(List<Pair<String,String>>)`
  · `[Read out — begin night]` · `[Later]`. **Shares the Bootlegger's dialog.**
  · **Scope split (the P1 fix):** `GameData.activeJinxes` becomes two named functions —
  `scriptJinxes(script)` (what the ST reads out; used by `ReferenceScreen.kt:52-53` and by the
  retitled **"Djinn rules (N)"** dialog) and `assignedJinxes(state)` (per-seat hints only;
  used by `SeatSheet.kt:222-233`). `ActiveJinxesDialog` (`GameExtras.kt:200-232`) currently
  passes assigned characters — that is the bug. Drop the dead `+ state.fabledIds` at
  `GameExtras.kt:207` (no jinx pair names a Fabled).
identity: plain
night: never wakes. **But**: `NightStep.detail` must append `"DJINN: <reason>"` for every
  assigned jinx touching that step's character, in the same style as the Exorcist annotation
  (`NightOrder.kt:150-154`) — a Pit-Hag step must show "Only 1 jinxed character can be in
  play" at the moment the ST adjudicates, not two tabs away.
day: `NominationCheck.cautions` gains the jinx text wherever a jinx changes a nomination
  outcome (the Riot and Leviathan nomination rules); `SeatSheet` shows "Djinn rule with
  <partner>: …".
death: none.
ledger: `LedgerEntry(kind = ANNOUNCE, sourceId = "djinn")` per rule read out, with `delivered`.
tests:
  - `gameData.jinxes.size == 131`, and the file declares its source and fetch date.
  - The reason for (`baron`,`heretic`) is exactly "Only 1 jinxed character can be in play.";
    (`grandmother`,`riot`) is "If Riot is in play and the Grandchild dies by execution, evil
    wins."
  - Every jinx names two known characters (`gameData.character(id)` non-null for both).
  - Given a script containing `spy` and `magician` and a bag containing neither, Then
    `scriptJinxes(script)` contains the pair and `assignedJinxes(state)` does not.
  - Given `pithag` assigned on a script also listing `heretic`, Then the Pit-Hag step's detail
    contains "DJINN:" and the jinx reason.
open: the 7 app-only pairs may be retired jinxes folded into base character text, or wiki lag
  — each needs a source check before removal, since the app currently announces them as rules.

## doomsayer — Doomsayer · fabled fabled · P0:0 P1:4
today: a toggle and a corner token. Once-per-player usage, the 4-alive gate, the
same-alignment victim filter and the death path are all storyteller memory; the only route is
the generic "Other death" button, which mislabels the cause and pops a protection dialog for
protections that cannot apply.
data:
  - characters.json: `doomsayer.reminders` `[]` → `["Used"]`
  - night_and_jinxes.json: ok — absent from both lists (regression guard)
  - night_guide.json: add a day entry (needs the new day section)
  - `DeathCause`/`DeathKind`: add a **`DOOMSAYER`** cause (`GameState.kt:75`, plus the `when`
    blocks at `GameExtras.kt:52-59` and `:322-330`); serialised enums append, never reorder.
    In `status-model.md` terms: `KillCause(kind = STORYTELLER, sourceCharacterId = "doomsayer")`
    is the minimum, but a distinct value keeps the log and reveal honest.
setup: none (add at any time).
identity: plain
night: never wakes — hard-disable the panel during NIGHT and SETUP.
day: the whole card. A **Doomsayer card on the Day tab**, above "New nomination"
  (`DayScreen.kt:126`), visible only while `doomsayer` is in `state.fabled`:
  gate — `state.aliveCountWithTravellers >= 4`; below that the card is disabled with
  "Only 3 players are alive — the Doomsayer can no longer be used." (wiki counts *players*,
  so Travellers count; the UI must say which count it used).
  invoker — chips for every alive seat **not** in `FabledEntry.spentBy`.
  victims — alive seats where `p.isEvil(lookup) == invoker.isEvil(lookup)`, **including the
  invoker** (wiki: "sometimes it may be best to kill the player who uses the ability").
  The Demon is listed last with a red caution "Killing the Demon usually ends the game",
  softened to "Scarlet Woman is alive — the game can continue" when a living `scarletwoman`
  exists and `aliveCountResidents >= 5` (mirror `WinCheck.kt:72-74`).
  registration row — separately headed: alive seats that *register* as the invoker's alignment
  but truly are not (Spy, Recluse, `alignmentFlipped`), offered deliberately, never silently.
  Wiki example 2 has the Spy invoke and a **good** player die.
  Confirm applies atomically: `killOutcome(victim, KillCause(DOOMSAYER))` ·
  `spentBy += invoker.id` · `("doomsayer","Used")` on the invoker · a `LedgerEntry(kind =
  STATEMENT, sourceId = "doomsayer", actorId = invoker, targetIds = [victim])`.
death: **not an execution and not a night death.** Because the cause is distinct, the Saint
  check (`WinCheck`), the Fearmonger's "executed from this nomination", the Devil's Advocate's
  `SURVIVES_EXECUTION`, `ExecutionRecord`, the Mastermind day and `aboutToDie` are all correct
  by construction. `killOutcome` must **skip** the "might be protected" confirmation —
  `SAFE_FROM_DEMON`, `CANT_DIE_TONIGHT` and `SURVIVES_EXECUTION` do not block a Doomsayer
  death (`CANT_DIE` and `ONLY_EXECUTION_KILLS` still do). On-death triggers **do** fire —
  Ravenkeeper (it is a *day* death, so it does **not** wake: say so explicitly), Farmer,
  Moonchild, Sweetheart, Barber, Godfather, Scarlet Woman, Minstrel, Vigormortis.
  `("doomsayer","Used")` is `Expiry.NEVER` — a resurrected invoker stays spent
  (`spentBy` is keyed by player id and never cleared).
ledger: `spentBy` per player; one entry per invocation ("D3: Ana used the Doomsayer — Ben
  dies"); the public declaration itself, since it is spoken aloud.
tests:
  - Given 3 alive and `doomsayer` active, Then the action rejects for every invoker.
  - Given a good Monk invoking with a Washerwoman, a Poisoner and a Baron alive, Then the
    candidate list is exactly the alive good players (Monk included).
  - Given the Spy (evil, registers good) invoking, Then the primary list is the alive evil
    players and a separately labelled registration list holds the alive good players.
  - Given Ana invokes and Ben dies, then Ana is resurrected, Then Ana is still in `spentBy`.
  - Given the Saint killed as a Doomsayer victim, Then `WinCheck` raises no Saint advisory.
  - Given the Soldier as victim, Then no protection prompt; given the Farmer, Then the
    on-death trigger still fires.
open: whether misregistration *forces* the victim's alignment or merely offers it — the wiki
  gives an example, not a rule. The card offers, never decides.

## duchess — Duchess · fabled fabled · P0:4 P1:5
today: the **only** Fabled with a night step, and its `characters.json` night text states the
wrong count and omits 0 from the range; only one `Visitor` token exists so marking a second
visitor silently un-marks the first; and `night_guide.json` contradicts `characters.json` in
the same expanded step.
data:
  - characters.json: `duchess.otherNightReminder` → the wiki text verbatim: "Wake each player marked \"Visitor\" or \"False Info\" one at a time. Show them the Duchess token, then fingers (0, 1, 2, or 3) equaling the number of evil players marked either \"Visitor\" or \"False Info\". If the woken player is marked \"False Info\", show them any number of fingers except the correct number." (today: "evil players marked \"Visitor\"", excluding the False Info player — wiki example 3 disproves it; and "fingers (1, 2, 3)", which leaves an all-good trio with no legal answer — example 1 disproves it). `duchess.reminders` `["Visitor","False Info"]` → `["Visitor","Visitor","False Info"]` so `maxCopies == 2` and `placeExclusiveReminder` stops firing.
  - night_and_jinxes.json: `otherNight` slot between `towncrier` and `oracle` (`:452`) is **unverified** — the wiki's Night_Order page 404s. Absent from `firstNight` (correct, keep).
  - night_guide.json:175-186 — the prose is **correct** ("among the marked visitors"); keep it, add the 0–3 range explicitly, add per-visitor show entries, drop the trailing "clear the reminders" sentence once dawn expiry does it.
setup: none — declared at game start, configured daily.
identity: plain — no holder. `NightStep.playerIds` must carry tonight's three visitors so the row reads "Duchess — Ana, Ben, Cara (Cara: false info)" instead of showing no names.
night.other:
  gate: `StepGate.Fire` only when exactly three seats hold Duchess tokens (2×`Visitor` + 1×`False Info`); otherwise `StepGate.Skip("No visitors tonight")` — collapsed, auto-added to `nightStepsDone`, never blocking the Dawn guard (today the step appears every other night regardless and must be ticked: `NightOrder.kt:144-145`, `GameShell.kt:147-160`). Never on the first night. Fabled: never dead, never spent, never `Reduced` by the Exorcist.
  action: `ShowInfo("duchess", "Wake each visitor one at a time…", targetsNeeded = 0)` — targets come from the tokens, not chips. Three-item sub-checklist in seat order so an interrupted ST knows who is left; each tick appends `WakeEvent(ownAbility = false)`.
  effects: none. No kill, no `Effect`, no impairment — visitors keep and use their own abilities normally. `duchess:Visitor` / `duchess:False Info` are `EffectKind.MARKER`.
  info: **new `InfoCalc` support for `duchess`** (`InfoCalc.kt:30-36` does not list it). `visitors` = seats holding either token; answer = `visitors.count { it.isEvil(lookup) }`, legal range **0..3**, counting the False Info player and the woken player themselves. False alternatives = `{0,1,2,3} - truth`. Caveats: `visitors.size != 3` → "Only N players are marked — the Duchess does not act tonight." · a Recluse/Spy/`alignmentFlipped` visitor is **named**, decided by the ST, never auto-applied · a visitor who died earlier tonight is named, not silently dropped · standing line "Visitors' own abilities are unaffected. A poisoned visitor still gets the true number." **Suppress the generic impairment caveats here** — the one `InfoCalc` step where `NightScreen.kt:904-907`'s detection must not fire, because a Fabled cannot lose its ability and Vortox affects Townsfolk abilities.
  show: per visitor in wake order — `ShowCard.CharacterCard("This many of the Duchess's visitors are evil","duchess")` then `ShowCard.NumberCard(n)`. **New card kind wanted:** one combined token-above-number card, which is what happens at the table.
  visibility: who visits is public (negotiated aloud); **which visitor holds False Info is storyteller-only and must never appear on a show card.**
day: the missing half. A **Duchess card on the Day tab** above "New nomination" (`DayScreen.kt:126`): "Duchess — who is visiting tonight? Pick exactly 3. One of them gets false info." → seat chips (default alive, dead allowed with a note), refuses to confirm at any count ≠ 3, then "False info goes to:" among the three. Confirm places the tokens and writes `LedgerEntry(kind = CHOICE, sourceId = "duchess", targetIds = [3 seats], text = "false info: Cara")`. `[Nobody agreed — no visit tonight]` is a first-class button. The card must also be reachable at night for corrections, because `ReminderPicker` cannot offer Fabled tokens during the day at all (group note 2).
death: none. Removing the Duchess mid-game clears its tokens and logs the removal.
ledger: tonight's three visitors + which got false info; a `LedgerKind.TOLD` entry per visitor recording the number actually shown, so tomorrow's contradiction is explainable. TokenRules: `duchess:Visitor` **DAWN** (`maxCopies = 2`) · `duchess:False Info` **DAWN** — today neither is in `EXPIRES_AT_DAWN`, so yesterday's visitors are silently reused.
tests:
  - Given `Visitor` on the Mastermind and Minstrel and `False Info` on the Imp, Then the computed number is **2** (wiki example 3).
  - Given three good visitors, Then the number is **0** and the false options are {1,2,3}; given a truth of 2, the false options are exactly {0,1,3}.
  - When `Visitor` is placed on Ana then Ben, Then both seats hold it.
  - Given all three tokens placed during NIGHT 3, When NIGHT→DAY advances, Then no seat holds either token.
  - Given 0, 2 or 4 marked seats, Then the step gates to `Skip("No visitors tonight")`; and `duchess` never appears in the first-night plan.
  - Given a visitor holding `poisoner:Poisoned`, Then the count is unchanged and no "give false info" instruction appears.
open: the night-order slot is unverified. Whether a dead player may volunteer is unaddressed by the wiki (default alive, allow dead with a note). Whether a visitor who dies earlier tonight still counts and is still woken — surfaced as a caveat, not decided.

## ferryman — Ferryman · fabled fabled · P0:0 P1:3
today: zero engine awareness, though every primitive exists. Running it on a 15-player game
means opening ten seat sheets and pressing "Restore ghost vote" ten times while the table
waits — and remembering to do it at all, because nothing prompts.
data:
  - characters.json: ok — text matches the wiki exactly
  - night_and_jinxes.json: ok — absent from both lists (regression guard)
  - night_guide.json: add a day entry once the schema has one
setup: none (explicitly a mid-game addition: "During the game, when you notice that it would
  be a good idea to add it").
identity: plain
night: never wakes.
day: **`GameState.finalDayCycle: Int? = null`** — the shared flag from group note 6, set only
  by storyteller declaration, never inferred. Two entry points, one flag:
  (1) a dawn prompt on NIGHT→DAY when `aliveCountResidents <= 3` and it is unset —
  `DayBriefing.Note(DAY_START, ALERT, "", "Is this the final day? 3 players are alive — if the
  Demon isn't executed today, evil wins.")` with `[Yes, final day]` / `[Not yet]`; the Ferryman
  sub-line ("declaring the final day returns every dead player's vote") shows only when active.
  The prompt appears with or without the Ferryman — the Angel needs it too.
  (2) a manual "Declare final day" menu item for an ST who calls it at 4 or 5 alive.
  On declaration, when `ferryman` is active and `!entry.used`:
  ```kotlin
  fun restoreGhostVotes(state) = state.copy(players = state.players.map {
      if (!it.alive) it.copy(ghostVoteUsed = false) else it })
  ```
  **idempotent — never built on `toggleGhostVote`** (`GameActions.kt:183-184`), which would
  flip already-unspent dead players *into* the spent state. Then `entry.used = true`, a
  `LedgerEntry(kind = ANNOUNCE)` "FINAL DAY — Ferryman returns N vote tokens (Amy, Doug, …)",
  and a dismissible banner. Adding the Ferryman **after** the final day is already declared
  fires the restore immediately (wiki example 2 is exactly that).
  Day header becomes "Day 6 · FINAL DAY · 3 alive · 2 votes to execute".
  Votes spent after the restore stay spent — `DayScreen.kt:232-240` needs no change; the guard
  is `entry.used`, which survives undo because it is part of the state snapshot.
  Exiles already ignore ghost votes in both directions; the panel should say so
  ("affects execution votes; exiles were already unrestricted").
death: none — restoring a ghost vote is not a resurrection. A player resurrected on the final
  day is alive and votes normally; their `ghostVoteUsed` is irrelevant while alive.
ledger: `finalDayCycle`; `entry.used`; the list of restored players, for the post-game "why did
  ten dead players vote on day 6" question.
tests:
  - Given 5 dead, 3 with `ghostVoteUsed = true`, and `ferryman` active, When the final day is
    declared, Then all 5 have `ghostVoteUsed = false` and no living player is modified.
  - Given that state, When `restoreGhostVotes` runs again, Then nothing changes.
  - Given the restore fired and Amy's ghost vote then spent, When the action is re-opened,
    Then it reports "already used" and Amy stays spent.
  - Given `finalDayCycle != null` and a dead player with a spent vote, When `ferryman` is
    added, Then that player's vote is restored.
  - Given 3 alive at NIGHT→DAY and the prompt dismissed with "Not yet", Then `finalDayCycle`
    stays null and no votes are restored.
open: none.

## fibbin — Fibbin · fabled fabled · P0:0 P1:4
today: zero engine awareness, and the one-tap false-info chips already in `NightScreen` are
gated on the info holder being **impaired** — so for a sober Empath, the single case the
Fibbin exists for, the shortcut is hidden.
data:
  - characters.json: `fibbin.reminders` `["Used"]` → `["No ability"]`, matching the wiki's
    **NO ABILITY** token and the label the existing "Mark spent" code already writes
    (`NightScreen.kt:271-274`). Ability wording: wiki says "incorrect information", the app
    says "false information" (P3, align if the project is wiki-first).
  - night_and_jinxes.json: ok — absent from both lists (regression guard)
  - night_guide.json: add a non-night entry carrying How-to-Run + the "information only" rule
setup: none.
identity: plain — but the NO ABILITY mark belongs on the **Fabled token**, not a seat. Cheapest
  home: `FabledEntry.used = true` renders as a NO ABILITY chip on the Fabled token in the
  grimoire corner and in the FabledSheet row. (Alternative: `storytellerReminders`, group
  note 3.) Do **not** park a generic `"Used"` chip on an unrelated player — it reads as *that
  player's* once-per-game being spent.
night: never wakes. Its trigger is **every info step whose holder is good**, on any night,
  plus on-death info (Ravenkeeper, Sage — the wiki's own example) and day-time verbal info.
  Change to `StepDetailPanel`: the false-info chip block (`NightScreen.kt:904-928`) changes its
  gate from "is impaired" to "is impaired **or** an unspent Fibbin is available and the holder
  is good", rendering a distinct row:
  "**Fibbin available (once per game)** — give incorrect information instead? [0][1][3][4]"
  with the guard line "*Information only — the Fibbin never makes an ability fail.*"
  Tapping a value shows `ShowCard.NumberCard(n)` (or the flipped YES/NO), sets `used = true`,
  writes `note`, and records the entry. Evil holders get the row greyed: "The Fibbin only
  affects good players." **The inverse offer**: when the holder *is* impaired and the Fibbin is
  unspent, add `[Use the Fibbin: show the TRUE answer]` — this is the wiki's "Some characters
  get false information due to their ability. The Fibbin can make this information true."
  Never offer a Fibbin affordance on a `ChoosePlayers`/`YesNo` action step — the Virgin's
  trigger, the Monk's protection, the Slayer's slay and the Gambler's guess are explicitly
  out of scope (wiki example 2 is about storytellers over-applying it).
day: the same offer must reach Artist / Savant / Fisherman once a day-info recorder exists;
  today those have no recording surface at all.
death: none. The Fibbin places nothing on a seat and creates **no `Effect`** —
  `Status.impairment` must stay untouched, or the Mathematician, Chambermaid and Vortox logic
  all change. Critically, a Fibbin-lied player is **not** a `MalfunctionEvent` and must not be
  counted by the Mathematician; add that line to the Mathematician's caveat.
ledger: `used: Boolean` (permanent, never expires) + `LedgerEntry(kind = TOLD, sourceId =
  "fibbin", actorId = holder, shown = "1", text = "truth 0")` so the reveal can explain it.
tests:
  - Given `fabled = [fibbin]` unspent and a sober Empath with a true count of 0, Then a Fibbin
    offer is present with candidate values {1,2,3,4}.
  - Given the same state but the holder is the evil Village Idiot, Then no offer.
  - Given an unspent Fibbin used on the Empath, Then `used` is true, the offer disappears from
    every subsequent info step, and the ledger records step, truth and shown value.
  - Given the Fibbin used on Ana, Then `Status.impairment(ana)` is empty and Ana holds no new
    reminder.
  - Given a poisoned Empath and an unspent Fibbin, Then a "show the TRUE answer" action exists
    and taking it marks the Fibbin spent.
open: nothing stops a storyteller applying it to an evil info role; the card greys the offer
  but does not hard-block (P3).

## fiddler — Fiddler · fabled fabled · P0:0 P1:5
today: zero engine awareness for a seven-step, rules-exact procedure the storyteller must run
from memory at the most time-pressured moment of the evening. The only tally in the app is the
nomination form, and using it is **actively harmful** — it writes a `Nomination`, corrupts
`aboutToDie`/`highestVotesToday`/the once-per-day guards, and spends dead players' ghost votes,
all of which the rules forbid.
data:
  - characters.json: ok — text matches
  - night_and_jinxes.json: ok — **must stay out of both order lists**; the Demon's pick is an
    ad-hoc wake during the activation flow, not a night-order step
  - night_guide.json: add a `fiddler` entry once the schema has a non-night section, carrying
    the seven How-to-Run steps verbatim. This is the one Fabled a storyteller reads out
    step by step.
setup: `fiddler.endTime` · TEXT/TIME · "Fiddler added — when will the game end? Announce this
  to the group now." · stored in `FabledEntry.note` · optional, skippable · shows a persistent
  banner "Game ends at 21:30 (Fiddler)".
identity: plain
night: never wakes as a night step.
day: the whole card is a state machine. New state:
  ```kotlin
  @Serializable data class FiddleContest(
      val demonId: Long, val challengedId: Long,
      val votesForDemon: List<Long> = emptyList(),
      val votesForChallenged: List<Long> = emptyList(),
      val resolved: Boolean = false)
  // GameState gains: val fiddle: FiddleContest? = null
  ```
  1 **Activate** — "Use the Fiddler now" in the main menu and on the FabledSheet row, behind a
    confirm ("This ends the game. Abilities stop working now."). Sets `used = true` and a
    global **abilities-off** flag that suppresses every `NightAction` panel, the Demon kill
    panel, the Fibbin offer, the nomination form and `checkNomination`, each replaced by
    "Fiddler activated — abilities no longer work." Engages `PrivacyCover`.
  2/3 **Demon picks** — Demon-team holders listed (ST chooses if several: Legion, Riot, a Fang
    Gu jump, an Al-Hadikhia/Scarlet Woman split); then a picker of **opposing-alignment**
    seats, `players.filter { it.isEvil(lookup) != demon.isEvil(lookup) }`, alive **and dead**
    (the rules say "any good player", with no life restriction). This filter is exactly the
    wiki's parenthetical "(If the Demon is good, they must choose an evil player instead.)"
  4 **Announce** — `ShowCard.Message("FIDDLE CONTEST", "Ana vs Ben")`. **Names only, never
    characters.** The app must not render team colours or character tokens here.
  5 **Discuss** — offer the 1m/2m timer from the flow.
  6 **Vote** — a dedicated two-column tally, **not** the nomination path: every seat appears in
    both columns and may be tapped in exactly one; **dead players are enabled regardless of
    `ghostVoteUsed` and voting never spends one**; Travellers vote; the two contestants vote;
    no `DayRules` weight, jinx or warning is consulted (wiki: "like an exile … the Thief cannot
    steal votes, the Voudon has no effect"); the tie rule is printed on screen.
  7 **Resolve** — most votes wins, their whole team wins; **a tie is won by the evil
    contestant**. Opens `RevealSheet(goodWins)` directly, not the advisory dialog, since this
    outcome is not advisory. Writes "Fiddle contest: Ana (Imp) 4 — Ben (Soldier) 6 → GOOD WINS"
    and a reveal header line.
death: no deaths, no tokens, no character changes; the Fiddler kills nobody.
  `WinCheck.check` must return null (or be bypassed) once `fiddle?.resolved == true`, so no
  Saint/Mastermind/Demons-dead advisory contradicts the result; `mastermindDayActive` is
  cancelled by a fiddle resolution.
ledger: the declared end time; the two contestants; the full tally; the outcome.
tests:
  - Given the Imp (evil) at 4 and the Soldier (good) at 6, Then `goodWins = true`.
  - Given 5 votes each, Then `goodWins = false`, whichever contestant is listed first.
  - Given a dead player with `ghostVoteUsed = true`, When they vote in the contest, Then the
    vote counts and `ghostVoteUsed` is unchanged.
  - Given a resolved contest, Then `state.nominations` is unchanged and `aboutToDie` is
    unaffected.
  - Given an evil Imp, Then the challenger candidates are exactly the good seats (alive and
    dead); given a good ex-Demon after a Snake Charmer swap, exactly the evil seats.
  - Given `fiddle.resolved = true` on a board that would trigger "Every Demon is dead", Then
    `WinCheck.check` returns null.
open: no Demon alive/in play at all (Kazali edge cases, an all-Legion board) — degrade to a
  manual two-contestant pick with a warning rather than failing.

## gardener — Gardener · fabled fabled (wiki: **Loric**) · P0:0 P1:3
today: activating it is a no-op. The "Start empty (assign in grimoire)" path is the closest
thing to Gardener mode, and it drops the "Need: N townsfolk · …" target line entirely — so
during exactly the workflow the Gardener requires, the storyteller is flying blind until the
"Begin night" guard.
data:
  - characters.json:2248 — ability is the **superseded** wording "The Storyteller assigns 1 or
    more players' characters."; the wiki says "The Storyteller assigns **all** players'
    characters." Material: the current text means no bag at all. Consider a
    `type`/`category` field distinguishing Fabled from Loric (Gardener + Storm Catcher moved).
  - night_and_jinxes.json: ok — absent from both lists (regression guard)
  - night_guide.json: no night entry; a setup run-book entry once the schema has one
setup: the whole card. `gardener.mode` · DERIVED from activation · when active the bag stage
  becomes an **assignment board**:
  - two columns — seats in circle order with names, and the script's characters grouped by
    team; tap a seat, tap a character; tap an assigned pair to clear.
  - a live target header driven by `Setup.adjustedDistribution(playerCount, assigned)` so
    bracket modifiers (Baron, Godfather, Huntsman⇒Damsel…) update as characters are placed,
    **plus the Sentinel's ±1 range when that Fabled is also active**.
  - per-team counters `Townsfolk 4/5 · Outsider 1/1 · Minion 1/1 · Demon 0/1`, red until met.
  - already-assigned characters greyed (the `inPlay` set at `SeatSheet.kt:400` is exactly what
    is needed and is currently computed but unused), except `GameActions.DUPLICABLE` ids.
  - `[Randomize the rest]` fills unassigned seats legally from the remaining pool.
  - `validateSetupState` runs **continuously** on the board, not only at the "Begin night"
    press, and the override text must not blame a Fabled the ST has already declared.
  - adjacency helper, because designed seating is the Gardener's entire purpose: flag
    Marionette-must-neighbour-the-Demon (**new check** — `GameActions.kt:522-534` validates
    only the shown token), No Dashii's Townsfolk neighbours, Tea Lady's neighbours, Cult
    Leader's neighbours, Empath/Chef counts.
  - the same board must be reachable mid-setup from the grimoire, so a partly dealt game can be
    fixed without walking seat by seat.
  - the existing Drunk/Lunatic/Marionette/red-herring prompts must fire for a hand-assigned
    board too, ideally inline on the board rather than as modal dialogs after the fact.
  - on completion, offer **"Reveal characters to players"** (`RevealFlow.kt:39`) as the natural
    next action — the digital equivalent of players taking their tokens from the circle —
    instead of burying it three taps deep in the overflow menu.
identity: plain — normal setup rules still apply in full; only the randomness is removed.
night: never wakes.
day: none.
death: none.
ledger: an `ANNOUNCE` entry that the Gardener was declared (it is public, and it changes the
  metagame — players know the assignment was deliberate).
tests:
  - Given `characters.json`, Then `gardener.ability == "The Storyteller assigns all players'
    characters."`
  - Given an 8-player TB game started empty with `gardener` active and 5/1/1/1 assigned, Then
    `validateSetupState` returns no issues; with 6/0/1/1, exactly one Outsider and one
    Townsfolk issue.
  - Given a Baron assigned, Then the required Outsider count rises by 2 — i.e. the board is
    driven by `Setup.adjustedDistribution`, not `Setup.distributionFor`.
  - Given a Huntsman with no Damsel, Then the "requires the damsel" issue is present.
  - Given a Marionette not neighbouring the Demon, Then a neighbour issue is reported.
  - Given `gardener` active, Then no `"gardener"` step appears in either night plan.
open: whether Gardener + Revolutionary should be mutually exclusive (redundant: the ST is
  assigning anyway) or just cross-validated for the pair's matching alignment.

## hellslibrarian — Hell's Librarian · fabled fabled · P0:0 P1:3
today: a toggle. The SOMETHING BAD token is unreachable, "cannot vote for a day" is
unrepresentable, and "loses their ability for a day" has no expiry — so a day-scoped penalty
silently becomes permanent.
data:
  - characters.json: `hellslibrarian.reminders` `["Something Bad"]` — normalise casing across
    the dataset (official token is **SOMETHING BAD**); add `"No ability"` and `"No vote"` so
    the penalties exist in data
  - night_and_jinxes.json: ok — absent from both lists (regression guard)
  - night_guide.json: add a day entry with How-to-Run + the "a light penalty works much better
    than a severe one" line verbatim
setup: none — addable **and removable at any time**, from setup and from any phase. Today
  `FabledSheet` is only reachable after `startGame` (group note 4).
identity: plain — the SOMETHING BAD token nominally lives on the Fabled token until used, so it
  wants `storytellerReminders` (group note 3). Minimum viable fix is group note 2's picker
  union, which also rescues Spirit of Ivory, Revolutionary and Deus ex Fiasco.
night: never wakes.
day: the whole card. A persistent **"Ask for silence"** action in `GameShell`'s overflow (and
  the top bar while active):
  - flashes a full-screen `ShowCard.Message("SILENCE")` to hold up to the table;
  - arms a "silence requested" state; while armed the grimoire shows a row of seat chips, and
    tapping a seat opens the **penalty picker**, one tap each, all undoable, all logged:
    (1) `<name> dies` → `killOutcome(id, KillCause(STORYTELLER))` + SOMETHING BAD;
    (2) `<name> loses their ability today` → `Effect(NO_ABILITY, sourceCharacterId =
        "hellslibrarian", until = DUSK, endsWithSource = false, label = "No ability")` +
        SOMETHING BAD;
    (3) `<name> can't vote today` → `Effect(NO_VOTE, …, until = DUSK, label = "No vote")` +
        SOMETHING BAD — **needs the new `EffectKind.NO_VOTE`, group note 8a**;
    (4) `Just the token (decide later)` → SOMETHING BAD only, `Until.FOREVER`.
  - `DayRules.canVote(state, lookup, playerId, isExile)` must return
    `Right(false, "Hell's Librarian — no vote today")`; the vote chip renders disabled with the
    reason as a caption; `checkNomination(...).cautions` surfaces
    "<name> cannot vote today (Hell's Librarian)". A dead player's ghost vote and a Hell's
    Librarian bar compose: **barred wins**.
  - `DayBriefing.Note(DAY_START, INFO, "hellslibrarian", …)` carries every live penalty.
death: `killOutcome` handles penalty 1 normally (no special cause needed). TokenRules:
  `hellslibrarian:No ability` **DUSK** (`impairs = true`) · `hellslibrarian:No vote` **DUSK** ·
  `hellslibrarian:Something Bad` **NEVER** (it is a record; the ST removes it by hand).
ledger: one `RULING` entry per penalty — "Day 2: <name> talked during silence — lost their vote
  (Hell's Librarian)" — so "why can't I vote?" is answerable later.
tests:
  - Given `hellslibrarian` active, Then the reminder-picker source contains
    `("hellslibrarian","Something Bad")`.
  - Given `("hellslibrarian","No vote")` on a seat during DAY 2, Then `DayRules.canVote`
    returns barred with a reason naming the Hell's Librarian; after DAY→NIGHT the token is gone.
  - Given `("hellslibrarian","No ability")`, Then `Status.impairment` is non-empty (fails today
    — `StatusEffects.kt:38-42` matches only "poison"/"drunk", so **`No ability` must become an
    impairment reason via `TokenRule.impairs`, not a substring**).
  - Given a nomination whose nominee is vote-barred, Then `checkNomination` returns a caution.
  - Given a penalty applied then `undo()`, Then token, vote bar and ledger entry revert together.
  - Given `hellslibrarian` active, Then no `"hellslibrarian"` step in either night plan.
open: none. (The `"No ability"` impairment gap is shared with the Fool, Virgin and "Mark spent",
  none of which feed `isImpaired` today — fix it once, in `TokenRule.impairs`.)

## revolutionary — Revolutionary · fabled fabled · P0:1 P1:3
today: "Deal randomly & start" is an unconstrained shuffle, so the two neighbouring players are
as likely as not to end up on opposite teams — the publicly announced fact becomes a lie, and
the app cannot even warn, because it has no idea which two seats are the pair.
data:
  - characters.json:2274 — `reminders` `["Used"]` → `["Register falsely?","Register falsely?"]`
    (the official set is two **REGISTER FALSELY?** tokens, one per pair member, removed when
    spent); ability "one of them" → "1 of them" (P3)
  - night_and_jinxes.json: ok — absent from both lists (regression guard)
  - night_guide.json: add a setup entry describing the constrained deal — the step the ST most
    needs walking through
setup: `revolutionary.pair` · SELECT_TWO_SEATS + consent checkbox ·
  "Revolutionary — pick two neighbouring seats who will play as a pair. They will draw the same
  alignment. Get both players' consent first, and tell the table." ·
  candidates = all seats · validation: **must be neighbours** in `state.players` order
  (wrapping), re-validated after `moveSeat`/`addSeat`/`removeSeat`; and after the deal, both
  seats must satisfy `isEvil(a) == isEvil(b)`.
  Stored as `fabledConfig["revolutionary"] = Revolutionary(seatA, seatB,
  falseRegistrationUsed = false, falseRegistrationOn: Long?, registersAs: String?)`.
  **Constrained deal (the P0):** `GameActions.deal` gains a constraint parameter, or a
  dedicated `dealWithRevolutionary(state, bag, pair, random)` — draw for `seatA`; filter the
  remaining bag to characters whose team alignment matches, pick one for `seatB`; deal the
  rest normally. If no matching token remains, retry (bounded) then report clearly: "This bag
  can't give the Revolutionary pair a matching alignment — add another evil/good character or
  change the pair." On assignment, place `("revolutionary","Register falsely?")` on **both**.
identity: **registration override, not a shown/acting split.** Once per game the ST may make
  one marked player register as a different character and/or alignment. `InfoCalc`'s `Ctx`
  (`InfoCalc.kt:97`) gains `registrationOverride: Map<Long, Registration>` where
  `Registration(characterId: String?, evil: Boolean?)`, consulted by `isEvil(p)` and by every
  character-identity read (Undertaker, Ravenkeeper, Investigator, Librarian, Washerwoman,
  Fortune Teller, Dreamer, Oracle, Empath, Chef, Clockmaker, Balloonist, Bounty Hunter, Town
  Crier…). **Migrate Spy and Recluse onto the same mechanism** — today `InfoCalc.kt:121-131`
  hard-codes them as prose caveats only, and every computed number reads the true alignment.
  One model then serves Spy, Recluse, Revolutionary and storyteller fiat.
night: never wakes. But the spend is invoked *at* an info step: any `ShowInfo` step where a
  marked seat is relevant offers a chip **"Spend the Revolutionary's false registration on
  <name>"** that recomputes the headline with the override and shows **both numbers side by
  side** before the ST commits. Second entry point: a "Revolutionary: register falsely" action
  on either marked seat sheet, with a character and/or alignment picker.
day: `DayBriefing.Note(DAY_START, INFO, "revolutionary", "<A> & <B> are the same alignment.
  False registration still available.")` while unspent.
death: none. Spending sets `falseRegistrationUsed = true`, removes **both** REGISTER FALSELY?
  tokens and writes a ledger line; nothing expires at dawn or dusk.
ledger: the pair + consent; whether the spend is still available; **which piece of info it was
  spent on** (`LedgerEntry(kind = RULING, sourceId = "misregister", targetIds = [seat],
  characterIds = [registersAs])`) so a later Undertaker/Empath contradiction is explainable.
tests:
  - Given an 8-player game, pair = seats 2 and 3, and a legal TB bag, When the constrained deal
    runs 500 times, Then `player(2).isEvil == player(3).isEvil` every time.
  - Given a bag with exactly one evil character, Then the deal produces a good-good pair or the
    "can't match alignment" failure — never a mismatched pair.
  - Given a pair on seats 2 and 5, When `validateSetupState` runs, Then a "neighbours" issue.
  - Given a dealt pair, Then both seats carry `("revolutionary","Register falsely?")`.
  - Given an Empath adjacent to a marked evil pair member and the override applied as
    `evil = false`, Then the headline is one lower than the un-overridden result.
  - Given the spend used then `undo()`, Then both reminders are back and
    `falseRegistrationUsed == false`; a second spend attempt is rejected.
open: **the wiki does not say whether "registers falsely" lasts for one piece of info or for
  one night.** Expose the choice ("spend on this info only" / "for the rest of tonight") rather
  than guessing. Also: a Pit-Hag/Barber/Snake-Charmer flip can make the public pair claim false
  — surface it ("<A> is now evil and <B> is good — the Revolutionary's public claim no longer
  holds"), do not fix it.

## sentinel — Sentinel · fabled fabled · P0:0 P1:3
today: the **only** Fabled whose rule the engine implements correctly — and the two call sites
that matter both drop it. `validateBag` gets no `fabledIds` from the setup screen, so an
intentionally shifted bag is reported illegal and the primary "Deal randomly & start" button is
disabled; and the Sentinel cannot be declared until after the game has begun.
data:
  - characters.json:2288 — `"setup": false`, but `raw_sv_travellers_fabled.json` has
    `"setup": true` for the same character. The flag was lost in the file the app loads.
    Set it to `true` to match the raw source (harmless today because `Setup.modifierFor` is
    never asked about a Fabled, but it bites the moment an imported script puts `sentinel` into
    `resolve(script)`, and it is what lets the setup screen render a `[±1 Outsider]` chip).
    Fabled must never enter the bag pool — `SetupScreen.kt:339`'s `isTownResident` filter
    already guarantees that; keep it.
  - night_and_jinxes.json: ok — absent from both lists (regression guard)
  - night_guide.json: no entry needed
setup: the whole card. `sentinel.outsiderDelta` · CHOICE(-1, 0, +1) · "Sentinel — the Outsider
  count is a secret. Choose: one fewer / as printed / one extra." · candidates: the `-1` chip is
  **hidden** when the base Outsider count is 0 (already filtered in the engine at
  `GameActions.kt:452`) · validation: none — all three are legal; the decision is made once,
  before tokens go in the bag, and is never re-decided or announced.
  Stored as `fabledConfig["sentinel"] = Sentinel(outsiderDelta: Int)`.
  Required call-site fixes: `SetupScreen.kt:356` must pass `state.fabledIds` into
  `validateBag(...)`; `randomBag` (`GameActions.kt:397`) gains a
  `fabledIds: Collection<String>` parameter and validates with the same ids, picking the delta
  from `outsiderDelta` (or uniformly from the legal options when unset).
  Bag header becomes "Need: 5 townsfolk · 1 outsider · 1 minion · 1 demon — Sentinel: 0, 1 or 2
  outsiders are all legal", computed from the same `allowedDistributions` the validator uses;
  once a chip is tapped, re-target the line to that concrete distribution so the ST builds
  against a number, not a range.
identity: plain
night: never wakes.
day: none. The chosen delta belongs in the setup summary and the game log so "how many
  Outsiders are in play?" is answerable mid-game.
death: none. Never expires — active for the whole game.
ledger: the chosen `outsiderDelta`, which today has nowhere to live and can be lost by undo.
tests:
  - Given 8 players, `fabledIds = ["sentinel"]`, and a bag of 6/0/1/1, When
    `validateBag(bag, 8, listOf("sentinel"))` runs, Then no issues (passes in the engine today
    — **add the mirrored test that the bag *screen* path passes the ids**).
  - Given the same bag with no Fabled, Then exactly one Outsider issue.
  - Given 5 players (base 3/0/1/1) and an active Sentinel, Then the legal Outsider counts are
    `{0, 1}` only — `-1` variants are absent.
  - Given a Baron on 9 players plus a Sentinel, Then Outsider counts 1, 2 and 3 are accepted
    and 0 and 4 rejected.
  - Given an active Sentinel, When `randomBag(available, 8, fabledIds = ["sentinel"])` runs 200
    times, Then every bag passes `validateBag(..., ["sentinel"])` and at least one has a
    non-base Outsider count.
  - Given `characters.json`, Then `sentinel.setup == true`.
open: none.

## spiritofivory — Spirit of Ivory · fabled fabled · P0:1 P1:3
today: the cap is not enforced, or even warned about, anywhere. `flipAlignment`, `starPass` and
the Fang Gu quick resolution all create extra evil players unconditionally, and the NO MORE
EVIL marker has literally no legal place to live.
data:
  - characters.json:2300 — reminder `"No extra evil"` → `"No more evil"`, matching the official
    **NO MORE EVIL** token so the app's grimoire matches a physical one
  - night_and_jinxes.json: ok — absent from both lists (regression guard)
  - night_guide.json: no night entry; a setup/reference entry listing which in-script characters
    are capped
setup: `spiritofivory.baselineEvil` · DERIVED · snapshot **once at SETUP→NIGHT 1** into
  `fabledConfig["spiritofivory"] = SpiritOfIvory(baselineEvil: Int)` — the adjusted
  distribution's minions + demons — rather than recomputing, because deaths, arriving Travellers
  and character changes all perturb the live distribution. **Travellers are excluded from both
  counts.** A Bounty Hunter's setup conversion happens *before* night 1, so it lands **above**
  the baseline and the marker must already be on at dawn of night 1 — take the snapshot before
  that conversion.
identity: this is a **rule modifier on becoming evil**, not a registration or shown/acting
  split. New engine object:
  ```kotlin
  object Alignment {
      fun baselineEvilCount(state, lookup): Int      // from fabledConfig
      fun currentEvilCount(state, lookup): Int       // Player.isEvil, non-Travellers
      fun extraEvilCount(state, lookup) = currentEvilCount - baselineEvilCount
      fun noMoreEvil(state, lookup) =
          "spiritofivory" in state.fabledIds && extraEvilCount(state, lookup) >= 1
  }
  ```
  Every path that can make a player evil routes through one guarded helper
  `becomeEvil(state, playerId, cause, lookup): Result`, returning `Blocked` when
  `noMoreEvil` — **the player stays good and the ability still happens**. Call sites to
  convert: `flipAlignment` (`GameActions.kt:129`), `starPass` (`GameActions.kt:78`), the Fang
  Gu quick resolution (`NightScreen.kt:484-497`), and every future Ogre / Cult Leader / Bounty
  Hunter / Mezepheles / Devil's-Advocate-on-Goon / Pit-Hag / Kazali / Boffin resolver.
  **Important asymmetry:** the Imp star-pass does **not** create an extra evil (a Minion becomes
  the Demon — the count is unchanged), while the Fang Gu jump does. `starPass` is shared by
  both, so the guard belongs at the caller, or `starPass` takes `createsExtraEvil: Boolean`.
night: never wakes. When blocked, the night step says, in storyteller voice: "Spirit of Ivory:
  there is already 1 extra evil player — <name> stays GOOD. The Devil's Advocate's choice still
  happened; nothing else changes." with an explicit **[Override (storyteller call)]**.
day: nothing beyond the grimoire caption "Spirit of Ivory — 1 extra evil player allowed.
  Currently: <k> extra.", the chip turning red when `k >= 1`.
death: none directly. `PlacedReminder("spiritofivory","No more evil")` is placed **automatically
  and derived** whenever `extraEvilCount >= 1` and removed automatically when it drops to 0 —
  never hand-managed. It lives in `storytellerReminders` (group note 3), not on a seat. Not in
  `EXPIRES_AT_DAWN`/`DUSK`: it is a pure function of the live evil count, so a Snake Charmer
  swap or a Goon flipping back lifts it and frees the next conversion.
ledger: `baselineEvil`; a line for **every** alignment change with its cause ("Night 3: <name>
  turned EVIL (Fang Gu)") and for every block ("Night 4: <name> would have turned evil —
  blocked by the Spirit of Ivory"), because "how many extra evil are there" is exactly the
  question the ST loses track of. Today `flipAlignment` is a bare toggle with no cause, no
  timestamp and no log entry.
tests:
  - Given a 9-player TB game (baseline 2 evil) with `spiritofivory` and no conversions, Then
    `extraEvilCount == 0` and `noMoreEvil == false`.
  - After one good player is flipped evil, Then `extraEvilCount == 1`, `noMoreEvil == true`, and
    `storytellerReminders` holds `("spiritofivory","No more evil")`.
  - Given `noMoreEvil`, When `becomeEvil(second)` is called, Then the result is `Blocked` and
    that player is still good.
  - Given `noMoreEvil` and a Snake Charmer swap dropping the count to 0, Then the marker is
    removed and a subsequent `becomeEvil` succeeds.
  - Given an Imp star-pass with `extraEvilCount == 1`, Then it is **not** blocked; given a Fang
    Gu jump onto an Outsider, Then it **is**.
  - Given 2 Travellers, one evil, Then Travellers are excluded from both counts.
open: **when a Fang Gu jump is blocked, does the jump happen at all?** The wiki example covers
  only the second conversion. Surface a decision prompt rather than choosing silently.
  Also: with Legion or Riot in play "baseline evil" is meaningless — disable the automatic cap
  and show a note. The Politician turns evil at scoring, not in play — leave out of the cap and
  say so in the step text.

## stormcatcher — Storm Catcher · fabled fabled (wiki: **Loric**) · P0:2 P1:4
today: the app will happily kill a SAFE player — `kill` has no protection logic and the Demon
kill panel's confirm stays enabled — and the one warning it does show describes the **Monk's**
rule ("protected from the Demon") when the Storm Catcher blocks *every* non-execution death.
data:
  - characters.json:2314 — sharpen `firstNightReminder` to "Mark the favoured good character \"Safe\" — they can only die by execution. Wake each evil player in turn and show them who it is."; `reminders: ["Safe"]` is correct.
  - night_and_jinxes.json:303 — `stormcatcher` at `firstNight` index 8, between `boffin` and `philosopher` and **before** `MINION_INFO`/`DEMON_INFO`. Correct as far as verifiable; the canonical Night_Order page 404s.
  - night_guide.json:188 — replace the single `token: "self"` show (which renders the **Storm Catcher's own** token, the opposite of the rule) with two entries whose token is `"config:stormcatcher.favouredCharacterId"` and whose text is `"THIS PLAYER IS"` / `"THESE CHARACTERS ARE NOT IN PLAY"`. Needs `GuideShow.token` to accept a `config:` reference alongside `"self"`/`"pick"` (`NightScreen.kt:375-377`).
setup: `stormcatcher.favouredCharacterId` · SELECT_CHARACTER · "Name a good character. Announce it publicly." · candidates = good characters from the current script (`isTownResident && !team.isEvil`), sorted in-play-first but **explicitly allowing a not-in-play pick** — the wiki calls that a feature (it hands evil a guaranteed-safe bluff) · validation: must be set before the first night; the night step blocks with "Name a good character first" + an inline picker. Stored in `fabledConfig`; derived `safeSeatId = players.firstOrNull { it.characterId == favouredCharacterId }`.
identity: plain — no seat. The SAFE token attaches to whoever holds the named **character**.
night.first:
  gate: `StepGate.Fire` whenever `stormcatcher` is active. Fabled: never dead, never impaired, never spent, never `Reduced` by the Exorcist. First night only; no other-night step.
  action: `ShowInfo("stormcatcher", …, targetsNeeded = 0)` with a derived **evil-player tick-list** — `players.filter { it.isEvil(lookup) }` in seat order, ticked off individually so the ST can be interrupted mid-step. Includes players who *became* evil before night 1 (Marionette, the Evil Twin's evil half, `alignmentFlipped` seats). Today `QuickResolutions` bails on this step because `step.playerIds` is empty.
  effects: `PlaceToken("stormcatcher","Safe", on = safeSeatId, exclusive = true)` — **derived and auto-placed**, not hand-placed; nothing is placed when the named character is not in play. In `status-model` terms this is the `stormcatcher` `StandingRule` already in the spec: `Effect(kind = ONLY_EXECUTION_KILLS, sourceCharacterId = "stormcatcher", sourcePlayerId = null, until = FOREVER, endsWithSource = false, label = "Safe")`.
  deferred: none — the token persists for the whole game. info: none computed for good players.
  show: in play → `ShowCard.CharacterCard("THIS PLAYER IS", favouredCharacterId)` then "now point at <name>"; not in play → `ShowCard.CharacterCard("THESE CHARACTERS ARE NOT IN PLAY", favouredCharacterId)`.
  visibility: **every evil player, one at a time.** The Lunatic is good and is **not** woken.
day: none.
death: `killOutcome` step 4 — `ONLY_EXECUTION_KILLS` blocks everything where `kind != DeathKind.EXECUTION`. That is **not just the Demon**: Assassin, Gossip, Godfather, Witch, Vigormortis, Moonchild, Psychopath, Zombuul, Pukka and storyteller kills all fail. **`EXILE` must be blocked too** — a Traveller can hold a good character, so it is reachable. Blocked line: "Storm Catcher: <name> can only die by execution — this death does not happen." The button becomes "Attack fails — <name> survives", with an explicit secondary "Kill anyway (override)" that is never the default. `("stormcatcher","Safe")` is `Expiry.NEVER` — **do not generalise `EXPIRES_AT_DAWN`'s `("monk","Safe")` to a label-only match** (today it is correctly source-scoped).
ledger: the favoured character; the SAFE seat; **every attack the SAFE token blocked**, so the dawn briefing can say why nobody died and the ST is not left reconstructing it.
tests:
  - Given a seat marked `("stormcatcher","Safe")`, When NIGHT→DAY advances, Then the token is still there.
  - Given the same seat, When protections are queried, Then an entry blocks `DEMON_ABILITY`, `EVIL_ABILITY`, `GOOD_ABILITY`, `STORYTELLER` and `EXILE`, and **not** `EXECUTION`, with text mentioning "only die by execution".
  - Given the same seat, When a Demon kill is applied through the guarded path, Then the player is alive and no `DeathEvent` is appended; When an execution is applied, Then they die with `kind == EXECUTION`.
  - Given `favouredCharacterId = "empath"` and no Empath in play, Then no Safe token is placed and the reveal text is the "not in play" variant.
  - Given `stormcatcher` active, Then a first-night step exists **before** `MINION_INFO` even though no player holds the character.
  - Given three evil players including one `alignmentFlipped`, Then all three are in the list.
open: **what happens when the SAFE player stops being the named character** (Pit-Hag, Barber swap, Snake Charmer, star pass onto them, Fang Gu jump) — the ability text reads as attaching to the *character*, so the token should follow it, but the wiki does not say. Make the token hand-movable and **prompt** on every character change: "The Storm Catcher favours the <X>. <Old> is no longer the <X> — move the Safe token to <New>?" (default yes). Same when the SAFE player turns evil: the ability names a *good* character — prompt and log, do not decide.

## toymaker — Toymaker · fabled fabled · P0:2 P1:3
today: in a 5–6 player game — the Toymaker's headline use case — the app silently omits
`MINION_INFO` and `DEMON_INFO`, so the Demon never learns their Minion and never gets bluffs;
and the forced final-night skip is neither enforced nor detected, so the app offers the Demon a
game-ending kill it is forbidden to make.
data:
  - characters.json:2328 — add a `firstNightReminder`: "Mark the Demon \"Final Night: No Attack\". Run Minion info and Demon info even with fewer than 7 players." The `otherNightReminder` is correct; normalise its prose casing to "Final Night: No Attack" to match the declared token (`reminders: ["Final Night: No Attack"]`) — any string matching must be case-normalised.
  - night_and_jinxes.json — **add `"toymaker"` to `firstNight` immediately before `"MINION_INFO"`.** Today it is in `otherNight` only, at index 34 between `lycanthrope` and the Demons, which is the right place for the other-night half (unverified; the canonical sheet 404s).
  - night_guide.json:201 — the existing `other` entry is accurate; add a matching `first` entry.
setup: `toymaker.markDemon` · DERIVED · at activation and again at SETUP→NIGHT, place `PlacedReminder("toymaker","Final Night: No Attack")` exclusively on every `Team.DEMON` seat. Re-run whenever the Demon changes seat (`starPass`, `swapCharacters`, `assignCharacter`) while unspent — **the obligation belongs to the Demon, not the player** (today a star pass leaves the token on the corpse). `fabledConfig["toymaker"] = Toymaker(skipUsed = false)`, derived: `skipUsed == demonSeat.reminders.none { it.sourceId == "toymaker" }`.
identity: plain
night.first:
  gate: Fire whenever `toymaker` is active (text-only step, before `MINION_INFO`).
  effects: the token placement above, with a one-tap "place the token" button.
  info: **the P0 fix** — `NightOrder.kt:51`'s gate becomes `state.players.count { !it.isTraveller } >= 7 || "toymaker" in state.fabledIds` (id-normalised). The sub-7 Marionette fallback (`NightOrder.kt:120-137`) then correctly stops firing, because the Marionette is pointed out inside the Demon info step.
  show: none of its own; it enables `MINION_INFO` / `DEMON_INFO` / `MINION_BLUFFS`.
night.other:
  gate: Fire every night after the first while active. New engine predicate `demonAttackCouldEndGame(state, lookup)` = `alive = players.filter { it.alive && !it.isTraveller }`; true when `alive.any { lookup(it.characterId)?.team == DEMON } && alive.size - 1 <= 2`. Travellers **and Fabled** are excluded (the Fabled page is explicit that Fabled do not count for the two-alive win). Where a script's win condition differs (Mayor at 3, Leviathan, Riot), treat it as **advisory** and say so in the step text rather than hard-blocking.
  action: predicate true **and token still on the Demon** → the Demon's own step is replaced by `StepGate.Skip("<Demon> does NOT act tonight — the Toymaker's obligatory no-attack night is forced")`; ticking it auto-removes the token and logs "Toymaker: forced no-attack night N". Predicate false → **annotate the Demon's own step** (the Exorcist mechanism at `NightOrder.kt:151-158`) with " — Toymaker: the Demon may shake their head for NO ATTACK tonight (obligation unspent)", plus a first-class button on the Demon kill action: **"Demon declined — no attack tonight"** → `allowNone` resolution removing the token, appending `ChoiceRecord(chosenNone = true)` and marking the step done. Today `NightScreen.kt:626`'s "No kill" is pure local UI state that records nothing — rename it "Clear selection" and give the deliberate outcome its own button. Once spent, the button remains (the option is available every night) but removes nothing; the annotation reads "(obligation already used)".
  effects: `("toymaker","Final Night: No Attack")` — `Expiry.NEVER`, removed only by being spent; **not** in `EXPIRES_AT_DAWN`/`DUSK` (already correct).
  deferred: on a declined night, `DawnReport.privateNotes` must say "No Demon attack tonight (Toymaker)" so the ST does not misremember it as a failed kill.
day: none.
death: none of its own — it *prevents* a kill.
ledger: `skipUsed`; the night the obligation was spent, and whether it was voluntary or forced.
tests:
  - Given a 6-player game with `toymaker`, Then the first-night plan contains `MINION_INFO` and `DEMON_INFO`; with no Fabled, neither appears.
  - Given a 6-player game with a Marionette and an active Toymaker, Then the standalone "Marionette info" step is **absent** and the Demon info step mentions the Marionette.
  - Given 3 alive non-Travellers including the Demon and the token still on them, Then the Demon's step gates to "does not act tonight" and `demonAttackCouldEndGame == true`.
  - Given 4 alive non-Travellers plus 2 Travellers and an unspent token, Then `demonAttackCouldEndGame == false`.
  - Given an Imp holding the unspent token, When `starPass(imp, heir)` runs, Then the heir holds it and the dead Imp does not.
  - Given the "Demon declined" action, Then the token is gone from every seat, no `DeathEvent` is added, and the ledger records the declined night.
  - Given a Demon marked `("exorcist","Chosen")` and an unspent token, Then the token is still present at dawn — **an Exorcised night must not consume the obligation** (the Demon did not *choose* to skip), and the forced skip must not double-suppress.
open: **multiple Demons** (Legion, Kazali/Boffin setups, Lil' Monsta) — place the token on each Demon seat, but is the obligation one per Demon or one per game? Flag in the step text rather than guessing. A Lunatic's fake no-attack must never clear the real Demon's token: key the button off the real Demon step only.

## deusexfiasco — Deus ex Fiasco · fabled fabled · P0:0 P1:4
today: a toggle and nothing else, ever. The load-bearing gap is that **nothing records the
information the storyteller gave** — and every Deus ex Fiasco correction turns on exactly that
("the Empath got 1, it should have been 0").
data:
  - characters.json:2342 — ability is the **superseded** "Once per game, …"; the wiki says
    "**At least** once per game, the Storyteller will make a mistake, correct it, & publicly
    admit to it." Material: multiple mistakes are permitted and at least one is *obligatory*.
    Reminder `["Mistake"]` → `["Whoopsie"]` (official token is **WHOOPSIE**).
  - night_and_jinxes.json: ok — absent from both lists (regression guard)
  - night_guide.json: add a setup/day entry with the How-to-Run text
setup: `deusexfiasco.availability` · GATE · selectable **only at setup** — the wiki says it must
  be announced at start and cannot be added mid-game. Past SETUP, the FabledSheet row renders
  disabled with that reason rather than silently toggling on.
  `fabledConfig["deusexfiasco"] = DeusExFiasco(mistakes: List<Mistake>)` where
  `Mistake(cycle, phase, note, announcedPublicly)`; at least one entry required before the game
  ends.
identity: plain
night: never wakes. **Re-run support** is the night-side requirement: a "re-run this step
  tonight / next night" flag on a night step, so wiki example 6 (the Poisoner was never woken)
  and example 2 (wrong Empath number) surface in the next night's sheet as
  "Deus ex Fiasco: re-run Empath info for Amy — the correct number is 0." This is exactly the
  `PendingEffect(kind = "first-night")` / `Prompt(at = TONIGHT, kind = RUN_FIRST_NIGHT)`
  machinery the Professor's resurrection needs (`night-engine.md §1`) — **build it once.**
day: the **"Whoopsie — a mistake was made"** action, available from the overflow at any time
  while active: (1) optionally jump into the ledger and tap the wrong entry; (2) free-text note;
  (3) correct it by **both** routes, offered explicitly — *Undo to this point* (labelled
  history, below) **and** *Fix it in place* (jump to the seat/step so the ST can re-place a
  token, re-assign a character or re-run a step, leaving later state intact — the right default
  for wiki examples 1, 2, 3, 6, 7); (4) announce via
  `ShowCard.Message("A MISTAKE HAS BEEN MADE")`; (5) append to `mistakes` and place the WHOOPSIE
  token in `storytellerReminders` (group note 3).
  **End-of-game obligation**: in the reveal/win path, if `deusexfiasco` is active and `mistakes`
  is empty, block with "Deus ex Fiasco: you still owe the table a mistake. Make one now, or
  record the one you made." with `[Record a mistake]` / `[Skip]`.
death: none. The WHOOPSIE token never expires — it is a permanent record for the game.
ledger: **this card is the ledger card.** It needs the full `LedgerEntry` surface from
  `records-and-memory.md §A/§D`, and specifically:
  - `LedgerKind.TOLD` on **every** card the ST flashes (`ShowCards.kt:82`'s `onShown` hook,
    `NightScreen.kt:157/824/892/898/918/925`), recording `shown` **and** the computed-true
    value, so a divergence is visible at a glance. Today nothing is persisted — "what did I tell
    the Empath on night 2?" is unanswerable.
  - `CHOICE`, `RULING`, `SPENT` and `ANNOUNCE` entries for token placements, kills and
    resurrections with cause, alignment flips, character changes and night steps ticked done.
  - `GameLogDialog` (`GameExtras.kt:46-80`, deaths + nominations only) becomes
    `GameLog.rows(...)`, filterable by cycle and by player.
  - **Labelled undo**: `GameViewModel.update(label, transform)` keeping `(label, state)` pairs,
    so the top-bar Undo shows "Undo: Imp kills Ben" and the mistake workflow can present the
    last N labelled steps as a pick-list. Raise or remove `MAX_HISTORY`, or surface truncation,
    so a night-1 correction on day 3 is still possible. Whole-state undo is the correct
    primitive for "correct it", but it also reverts *correct* later actions — the in-place path
    must warn, and must warn again when it would leave a spent once-per-game marker unrestored.
tests:
  - Given `characters.json`, Then the ability starts with "At least once per game".
  - Given an Empath shown a number, Then the ledger holds an entry recording the player, the
    value shown and the computed-true value.
  - Given a recorded mistake, Then the end-game advisory raises no obligation warning; given
    none, Then it does.
  - Given `deusexfiasco` active, Then the reminder-picker source contains
    `("deusexfiasco","Whoopsie")`.
  - Given a sequence of labelled updates, When `undo()` is called, Then the history entry
    carries the label of the reverted action.
  - Given the game is past SETUP, Then `deusexfiasco` is disabled in the Fabled sheet with the
    "cannot be added mid-game" reason.
  - Given a "re-run Empath info for Amy" flag set on night 2, Then the night-3 plan carries the
    re-run annotation.
open: how large the ledger may grow before it threatens the localStorage quota on the PWA —
  `records-and-memory.md §H3` (move `Script` out of `GameState`) is a prerequisite.
