# Village Idiot (villageidiot) — Experimental Townsfolk

## Official rules (sources)

Source: https://wiki.bloodontheclocktower.com/Village_Idiot (fetched 2026-08-25).

Current ability text (matches `characters.json` exactly — no drift):

> "Each night, choose a player: you learn their alignment. [+0 to +2 Village Idiots. 1 of the extras is drunk]"

How to Run (quoted verbatim):

> "While setting up the game, before putting the character tokens in the bag, replace zero, one or two Townsfolk tokens with Village Idiot tokens. While preparing the first night, mark one Village Idiot with the **DRUNK** reminder."
>
> "During each night, wake any Village Idiot. They point to a player. Give a thumbs up or a thumbs down."

Clarifications (verbatim):
- "**If there is only one Village Idiot in play, they are sober.**"
- "If all sober Village Idiots exit play, the remaining drunk Village Idiot **remains drunk**."
- "If a sober Village Idiot becomes drunk or poisoned by other means, the drunk Village Idiot remains drunk."
- "If a Village Idiot is created mid-game, only one is created."
- "When Village Idiots are added to the game during setup, they replace other Townsfolk." (They do **not** add players or change the team distribution.)
- Between one and three Village Idiots can be in play.
- Thumbs **up** = good, thumbs **down** = evil.
- The drunk Village Idiot may still be given true information — the Storyteller chooses.

Jinxes (wiki, exact text):
- **Boffin:** "If there is a spare token, the Boffin can give the Demon the Village Idiot ability."
- **Pit-Hag:** "If there is a spare token, the Pit-Hag can create an extra Village Idiot."

Not addressed on the wiki (flagged, not guessed): whether a Village Idiot created mid-game by a Pit-Hag is drunk or sober. ("Only one is created" is stated; drunkness is not.)

Implementation-critical consequences:
- The DRUNK reminder is assigned **once, at setup, and never moves.** It is not a nightly choice and must not be recycled by an "exclusive reminder" flow that lets a stray tap relocate it.
- Each Village Idiot acts **separately**, one at a time, each choosing their own player and each getting their own answer. With three in play, that is three independent choices, three independent answers, and one of the three answers is systematically unreliable.

## What the app does today

Data / setup:
- `characters.json:1591` — ability text current (bracket included), `setup: true`, `reminders: ["Drunk"]`, both night reminders correct.
- `night_and_jinxes.json:357` (first night slot 62) and `:457` (other night slot 84). Both correct.
- `night_guide.json:1217` — accurate prose for both nights, with prepared `good`/`evil` alignment cards. These fire `ShowCard.AlignmentCard` (`NightScreen.kt:795-799`, `ShowCards.kt:69`) — a working thumbs-up/thumbs-down replacement.
- `GameActions.DUPLICABLE` (`GameActions.kt:413`) contains `villageidiot`, and `validateBag` (`GameActions.kt:487-494`) caps it at 3 with a clear message. Covered by `GameActionsTest.kt:340-357`. **Works.**
- `SetupScreen` renders a count stepper for duplicable characters (`SetupScreen.kt:463-530`), so 2–3 Village Idiots can be built by hand. **Works.**
- `Setup.modifierFor` (`Setup.kt:112-232`) parses the bracket into `choiceTeams = {TOWNSFOLK}` with zero deltas — which is the right answer, since extras replace Townsfolk 1:1 and the total is unchanged. The resulting Townsfolk-count relaxation in `validateBag` (`GameActions.kt:433-443`) is harmless because the bag size and the other three team counts are still checked. **Works.**
- **`Drunk` is in neither expiry table** (`GameActions.kt:218,231`) — correct.
- Neither Village Idiot jinx (Boffin, Pit-Hag) is in `night_and_jinxes.json`.

Runtime:
- `NightOrder.build` groups players by `nightRoleId` (`NightOrder.kt:45-48`) and emits **one** step for all Village Idiots, with `playerIds` = every VI seat. The row header lists all of their names (`NightScreen.kt:739-746`).
- `InfoCalc.supports` includes `villageidiot`; `targetsNeeded == 1` (`InfoCalc.kt:24`).
- `InfoCalc.villageIdiot` (`InfoCalc.kt:367-374`) resolves a single target and returns `"<Name> is EVIL|GOOD"` plus Spy/Recluse notes.
- `StepDetailPanel` (`NightScreen.kt:836-935`) uses **`val holderId = step.playerIds.firstOrNull()`** and a **single** `targets` list.
- `targets` is `rememberSaveable(step.id)` (`NightScreen.kt:839`) — keyed on the step id only, **not** on `state.cycle`, so last night's selection is restored on subsequent nights.
- `NightToolTray` (`NightScreen.kt:186-355`) offers the single "Drunk" chip; because `allReminders.count { it == "Drunk" } == 1`, placement goes through `placeExclusiveReminder`, which **removes the token from every other seat first** (`GameActions.kt:196-205`).
- `validateSetupState` (`GameActions.kt:503-560`) has no Village Idiot case. `GameShell`'s pre-night prompts (`GameShell.kt:340-478`) have no Village Idiot case.
- `GameActions.randomBag` (`GameActions.kt:337-400`) draws each character at most once from a shuffled pool, so it **can never produce a second or third Village Idiot.**

Storyteller experience with three Village Idiots today: one row, `Village Idiot — Ana, Bo, Cy`. One player-picker that holds one selection. One computed answer. The impairment caveat is computed only for Ana. To run the character correctly the ST must, entirely by hand: remember which of the three is drunk (nothing asked them to decide, nothing recorded it unless they thought to place the token), wake each in turn, note each choice somewhere else, work out each answer, and remember to lie to exactly one of them — every night, for the whole game.

## Defects and gaps

1. **P0 · Three Village Idiots share one target picker and one answer.** `NightScreen.kt:836-839` — a single `targets` list and a single `InfoCalc.compute` call for the whole row. Repro: build a 9-player bag with three Village Idiots, deal, go to night 1, expand the Village Idiot row: one "Chosen player (1):" chip row. Selecting a second player replaces the first (`NightScreen.kt:854-858`). Two of the three Village Idiots' answers exist nowhere in the app.
2. **P0 · The impairment caveat is computed for the first Village Idiot only.** `holderId = step.playerIds.firstOrNull()` (`NightScreen.kt:837`) → `commonCaveats` (`InfoCalc.kt:158-169`) sees only that seat. If the drunk one is the 2nd or 3rd VI, the panel shows a clean true answer with **no warning**, for every night of the game. This is the single most dangerous defect in this character: the app confidently gives correct info for the player who is supposed to be receiving false info. Repro: place `villageidiot:Drunk` on the second VI seat and open the row — no "! … is DRUNK" line.
3. **P0/P1 · Nothing asks which Village Idiot is drunk, and nothing validates it.** The rules require the mark to be placed "while preparing the first night". `validateSetupState` (`GameActions.kt:503-560`) checks the Drunk, Lunatic, Marionette and red herring but not this. Repro: deal three Village Idiots and press "Begin night" — no prompt, no warning, no token. The defining mechanic of the character silently does not happen.
4. **P1 · The DRUNK token is trivially moved by accident.** It is a one-copy reminder, so the tray places it with `placeExclusiveReminder`: tapping "Drunk" and then a seat while the Village Idiot row is expanded relocates it, wiping the setup-time decision with no confirmation. The rules say it never moves (it survives the sober VIs leaving play, and other VIs becoming drunk by other means). `NightScreen.kt:318-322`, `GameActions.kt:196-205`.
5. **P1 · Last night's targets are restored.** `rememberSaveable(step.id)` (`NightScreen.kt:839`) is not keyed on `state.cycle`, unlike `expandedId` and `pendingReminderLabel` (`NightScreen.kt:88,92`) and unlike the `DemonKillPanel`'s `rememberSaveable("$demonId-${state.cycle}")` (`NightScreen.kt:541`). Repro: pick a target on night 2, advance to night 3, open the row — the night-2 chip is still selected, and the panel shows a stale answer as if it were tonight's. Affects Dreamer, Ravenkeeper, Grandmother, Fortune Teller, Seamstress and Chambermaid identically.
6. **P1 · No per-Village-Idiot sequencing or check-off.** "Wake the Village Idiots one at a time" is a three-part procedure tracked by one checkbox (`NightScreen.kt:709`). Nothing tells the ST which ones they have already woken tonight.
7. **P2 · The random bag builder can never make extra Village Idiots.** `GameActions.randomBag` (`GameActions.kt:349-361`) iterates a shuffled pool of distinct characters, so duplicates are structurally impossible; `DUPLICABLE` is honoured by the validator but never by the generator. A ST who uses "random bag" on a script containing the Village Idiot will only ever see the one-VI (always sober) version of the character. Repro: generate random bags repeatedly on an Experimental script — never two Village Idiots.
8. **P2 · Both Village Idiot jinxes are missing from `night_and_jinxes.json`** (Boffin, Pit-Hag). The Boffin one matters at setup ("if there is a spare token"), the Pit-Hag one mid-game.
9. **P2 · A mid-game Village Idiot's drunkness is undecided and unrecorded.** "If a Village Idiot is created mid-game, only one is created" — the wiki does not say whether it is drunk. The app should surface the choice to the ST rather than silently doing nothing.
10. **P2 · The alignment show cards are not tied to the computed answer.** `night_guide.json:1217` gives two always-present chips, "Good" and "Evil" (`NightScreen.kt:795-799`), with no indication of which one is the true answer for the currently selected target and no indication that this particular Village Idiot should be lied to. The ST has to read the computed line and then pick the right chip. For the drunk VI the correct chip is the *wrong* one — easy to fumble at 1 a.m.
11. **P3 · `misregistrations` fires on the whole game state, not the target.** Actually correct here (`InfoCalc.kt:372` passes `listOf(target)`), so the Spy/Recluse note only appears when the *chosen* player misregisters. **Works** — noted so it is not "fixed" away.
12. **P3 · No "all dead" nuance.** `NightScreen.kt:702` computes `allDead` across all Village Idiots; with 2 of 3 dead there is no hint that only one still acts.

## Proposed behaviour (spec)

### The general problem this character exposes
The night sheet's unit is a **character**, but the unit of *action* is a **(character, player)** pair. Village Idiot is the only character in the base game where that difference is visible on the standard scripts, but it is also true for Legion, Riot, a Boffin-empowered Demon, and any duplicate created by a Pit-Hag. The right fix is structural, not local:

> `NightStep` gains an optional `perHolder: Boolean`. When true, `NightScreen` renders one **sub-panel per holder** — each with its own target picker (`rememberSaveable("${step.id}-${holder.id}-${state.cycle}")`), its own `InfoCalc.compute(holderId = holder.id)`, its own caveats, its own show-card chips, and its own done-tick. `NightOrder` sets `perHolder = true` whenever `playerIds.size > 1`.

Everything below assumes that.

### Setup
- Add a Village Idiot prompt to the pre-night chain in `GameShell.kt:340-478`:
  - condition: `state.phase == SETUP && count of villageidiot seats >= 2 && no seat carries villageidiot:Drunk`
  - title `Village Idiots are in play`
  - body `Three Village Idiots: Ana, Bo, Cy. One of the extras is drunk — which one?`
  - options: the Village Idiot seats.
  - action: `addReminder(state, chosen, PlacedReminder("villageidiot","Drunk"))` and set that seat's note `Drunk Village Idiot (permanent)`.
- Add to `validateSetupState` (`GameActions.kt:503-560`):
  - `>= 2` Village Idiots and no `villageidiot:Drunk` token → `"Village Idiot: mark exactly one of the <n> Village Idiots with the 'Drunk' reminder"`.
  - `>= 2` Village Idiots and more than one `Drunk` token from `villageidiot` → `"Village Idiot: only one may be marked 'Drunk'"`.
  - exactly 1 Village Idiot and a `villageidiot:Drunk` token → `"Village Idiot: a lone Village Idiot is sober — remove the 'Drunk' reminder"`.
- `randomBag` (`GameActions.kt:337-400`): when the script contains a `DUPLICABLE` character, allow the pool to offer it more than once. Minimal change — after the Townsfolk draw, with some probability (or via a Setup screen toggle "extra Village Idiots: 0/1/2"), swap 0–2 drawn Townsfolk for extra Village Idiot copies, then re-validate. The explicit toggle is better than randomness: it is a Storyteller choice, not a die roll.

### Night step
- **when:** both nights, every night.
- **wake condition:** per holder — that Village Idiot is **alive**. Dead Village Idiots do not act; their sub-panel renders greyed with `Dead — does not act.`
- **targets:** exactly 1 player **per living Village Idiot**, chosen independently. Constraints: any player, including dead ones and including themselves and each other; no "different from last night" rule. Picker default/sort: alive first, then dead with `†`; the previous night's choice for **this** Village Idiot shown as a faint hint (`last night: Bo`) but **not** pre-selected.
- **immediate effects:** none. No tokens are placed by the nightly action.
- **deferred effects:** none.
- **expiry:** `villageidiot:Drunk` never expires and never moves.
- **information — computed** (per holder): `<Target> is GOOD` / `<Target> is EVIL`, from `Player.isEvil(lookup)`, with the target's Spy/Recluse misregistration note (already correct at `InfoCalc.kt:372`).
- **information — shown:** the existing `AlignmentCard` chips, but **badged**: the chip matching the computed answer is labelled `✓ true`, the other `false`. For a Village Idiot the app knows to be impaired (see below), invert the badges and colour the panel: `This Village Idiot is DRUNK — give false info (or true, your choice).`
- **impaired / false alternative:** the impairment source for this character is normally the `villageidiot:Drunk` token, which `StatusEffects.isImpaired` (`StatusEffects.kt:38-48`) and `InfoCalc.impairments` (`InfoCalc.kt:145-149`) already handle — they just never see the right seat today. With per-holder panels this works with no engine change. The rules note that the drunk one *may* be given true info, so the app must **offer** the lie, never force it.
- **visibility:** nothing shown to evil.
- **day-time inputs:** none — but the `infoGiven` log proposed for the Shugenja/Steward is especially valuable here: with three Village Idiots comparing notes publicly, the ST needs a record of exactly which thumb they gave to whom on which night.
- **interactions/jinxes:**
  - **Boffin** — the Demon may hold the Village Idiot ability. Falls out of the same "granted ability" hook the Pixie needs (see `pixie.md`); the Demon then gets their own sub-panel on the Village Idiot row.
  - **Pit-Hag** — may create an extra Village Idiot; the app should then ask whether the new one is drunk (see D9) and note the wiki's silence.
  - **Drunk / Marionette shown a Village Idiot token** — `nightRoleId` (`GameState.kt:36-42`) already routes them into this row; `InfoCalc.impairments` flags them (`InfoCalc.kt:138-143`); with per-holder panels the flag lands on the right sub-panel.
  - **Vortox** — `commonCaveats` already adds the "must be FALSE" note; per-holder it now applies to each.

### UI text the step should display
- Row header: `Village Idiot — Ana, Bo †, Cy` with a sub-line `Wake them one at a time.`
- Per holder: `Ana — who did they point to?` → chips → `Bo is EVIL` → `» Show thumbs-down (true)` `» Show thumbs-up (false)` → a per-holder done tick.
- Drunk holder banner: `! Cy is the DRUNK Village Idiot — their info may be false. Your choice.`
- Setup prompt: `One of the extra Village Idiots is drunk. Pick which. This never changes.`

### Data changes
- `night_and_jinxes.json` — add:
  - `{"id1":"villageidiot","id2":"boffin","reason":"If there is a spare token, the Boffin can give the Demon the Village Idiot ability."}`
  - `{"id1":"villageidiot","id2":"pithag","reason":"If there is a spare token, the Pit-Hag can create an extra Village Idiot."}`
- `night_guide.json:1217` — add the "one Village Idiot in play is sober" rule and "the drunk mark never moves" to both `first` and `other` instructions; keep the two alignment cards.
- `characters.json:1591` — no change.

## Tests to add

1. `setup validation demands exactly one drunk village idiot` — Given a bag with 3 `villageidiot`; When `validateSetupState`; Then an issue names the Village Idiot and the Drunk reminder. Given one seat marked; Then no such issue. Given two seats marked; Then an "only one" issue.
2. `a lone village idiot must not be marked drunk` — Given 1 `villageidiot` carrying `villageidiot:Drunk`; Then `validateSetupState` flags it.
3. `night step is per holder when several village idiots are in play` — Given 3 Village Idiot seats; When `firstNight(state, lookup)`; Then the `villageidiot` step has `playerIds.size == 3` and `perHolder == true`.
4. `each village idiot gets its own impairment caveats` — Given seats 2, 5, 8 are Village Idiots and seat 5 carries `villageidiot:Drunk`; Then `InfoCalc.compute(..., holderId = 5, targets = [0])` has a caveat containing "DRUNK" and `holderId = 2` has none. (Passes in the engine today; the failing part is the UI's `firstOrNull()` — add a UI-level or ViewModel-level test that the step yields three holder ids.)
5. `village idiot answer is per target` — `compute(..., holderId = 2, targets = [demonSeat]).headline` ends with "EVIL"; `targets = [chefSeat]` ends with "GOOD".
6. `drunk reminder survives dawn and dusk` — When `advancePhase` runs through both; Then `villageidiot:Drunk` is still on the same seat.
7. `dead village idiots are excluded from tonight's holders` — Given seat 5 dead; Then the step still lists all three but marks 5 as non-acting.
8. `bag validation still caps village idiots at three` — regression guard for `GameActionsTest.kt:340-357`.
9. `random bag can produce extra village idiots when asked` — Given `extraVillageIdiots = 2`; When `randomBag(...)`; Then the bag contains 3 `villageidiot`. (Today: structurally impossible.)
10. `village idiot jinxes are in the data` — `data.activeJinxes(listOf("villageidiot","boffin")).size == 1` and likewise for `pithag`.
11. `night target selection resets each cycle` — UI/ViewModel test: the saveable key for a Village Idiot's target includes `state.cycle` and the holder id, so advancing the cycle clears it.
