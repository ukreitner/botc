# Xaan (xaan) — exp (Carousel) minion

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Xaan>

Current ability text:

> "On night X, all Townsfolk are poisoned until dusk. [X Outsiders]"

`characters.json:1935` matches this text exactly — **no drift**.

### How to run (wiki, verbatim, in order)

- "While setting up the game, before putting character tokens in the bag, add or remove any number of Outsider tokens, including zero."
- "Remove any unnecessary Xaan reminders."
- "On the 1st night, add the **NIGHT 1** Xaan reminder to the Grimoire."
- "On the 2nd night, add the **NIGHT 2** Xaan reminder to the Grimoire."
- "On the 3rd night, add the **NIGHT 3** Xaan reminder to the Grimoire."
- "On the night that equals the number of Outsiders in play when the game began, add the **X** reminder to the Grimoire, and remove it the following dusk."
- "When the **X** reminder is in the Grimoire, all Townsfolk players are **poisoned**."

### Key clarifications (verbatim)

- "If the number of Outsiders changes during the game, the Xaan poisons on the night corresponding to the number of Outsiders during setup." — **X is frozen at setup.**
- "The Xaan needs to be alive in order to poison."
- "This overrides other characters that add or remove Outsiders, such as the Baron." — the Xaan's chosen Outsider count wins over Baron/Godfather/Fang Gu setup modifiers.

### Examples (verbatim)

- "There are 3 Outsiders in play, due to the Xaan. On night 3, the Exorcist chooses the Demon but nothing happens, the Acrobat chooses the Drunk but nothing happens, and the Seamstress gets false information."
- "There is 1 Outsider in play. It is an 11-player game. On the first night, the Xaan poisons all 7 Townsfolk. On the second night, the Pit-Hag creates a Hatter. Even though there are 2 Outsiders in play, all players are healthy tonight."
- "There are no Outsiders in play. The Xaan never poisons anyone." (and the Xaan may bluff as the Zealot so the town wrongly believes night 1 was poisoned)

Note the first example: the poison hits **Townsfolk characters**, so the Drunk (an Outsider) is *not* poisoned — which is why the Acrobat, who *is* poisoned, gets nothing when they choose the Drunk. The poison is by **team of the true character**, not by what the player believes they are.

### Jinxes

**None.** The wiki page has no Jinxes/Bootlegger section.

### Open question

The official reminder set stops at `NIGHT 3` (and the How-to-Run lists only nights 1–3), yet "add or remove any number of Outsider tokens, including zero" permits X = 4 or more. The app should support any X, but should warn the Storyteller when X > 3 that the official token set does not cover it. I could not find a wiki sentence resolving this.

## What the app does today

Data paths — **this is the complete list**:
- `characters.json:1935` — text, `setup: true`, reminders `["Night 1","Night 2","Night 3","X"]`, night reminders matching the official wording.
- `night_and_jinxes.json:321` (firstNight index 26, between `lleech` and `poisoner`) and `:385` (otherNight index 12, between `preacher` and `poisoner`). Both positions are correct.
- `night_guide.json:1517` — first/other prose describing X, the counting tokens, "give them false info accordingly", and "The poison ends at dusk the following day".
- `raw_exp_evil_outsiders.json:345` — raw import copy.
- `Setup.modifierFor` (`Setup.kt:121-232`) parses `[X Outsiders]`: `isChoice` is true because the bracket contains a bare `X` (`Setup.kt:146`), the `\bX\s+(…)s?` regex at `Setup.kt:154-157` yields `Outsider`, so `choiceTeams = {OUTSIDER}`. `deltaRegex` finds no `±n` match, so `outsiderDelta = 0` and `choiceDeltas` is empty (open-ended). `validateBag` (`GameActions.kt:420-496`) then relaxes both OUTSIDER and TOWNSFOLK (`GameActions.kt:435-442`), so **any** Outsider count passes validation. That behaviour is correct, including the "overrides the Baron" clause, because both teams stay relaxed once any open-ended choice is present.

No `xaan` reference exists in `engine/src/main/kotlin` or `app/src`.

Storyteller experience:
1. Setup: the bag builder lets any number of Outsiders through. **X itself is never asked for and never stored anywhere.** The suggested distribution shown to the Storyteller is the unmodified base distribution.
2. Night 1: the step reads "If X is 1, mark the Xaan with the 'X' reminder token." plus the guide prose. Placing `Night 1` or `X` is a manual tray operation (`NightScreen.kt:224-295`).
3. Night N: the step reads "Change the Xaan reminder token to the relevant night…". Nothing changes it. The Storyteller must remember X, compare it to `state.cycle`, and swap tokens by hand.
4. Night X: **nothing at all happens in the app.** The `X` token sits on the Xaan's own seat. `StatusEffects.isImpaired` (`StatusEffects.kt:36-46`) checks only that seat's own reminder labels, character `drunk`, and `derivedPoison` (`StatusEffects.kt:14-33`, No Dashii only). No Townsfolk is marked. Consequently every info character's step that night computes and displays **true** information with **no impairment caveat** (`InfoCalc.commonCaveats`, `InfoCalc.kt:158-166`), and the "False info to show instead" panel (`NightScreen.kt:903-930`) never appears.
5. Dusk: `EXPIRES_AT_DUSK` (`GameActions.kt:231-242`) has no `xaan` entry, so whatever the Storyteller placed by hand persists.

## Defects and gaps

1. **P0 · On night X the app gives the Storyteller true information for every Townsfolk, with no warning** — the ability's entire effect. The `X` token on the Xaan's seat is invisible to `isImpaired` for all other seats, so each Townsfolk info step (`NightScreen.kt:836-931`) shows the true answer as the headline, in gold, with no `!` caveat and no false-info chips. This is the single most damaging defect in this scope: the Storyteller is not just doing manual work, they are being actively told the wrong thing. Repro: 11-player game with a Xaan and 1 Outsider, night 1, open the Empath step — it reports the true neighbour count as if nothing were wrong.
2. **P0 · X is never captured** — the ability is entirely parameterised by X, and X is a setup-time Storyteller choice that "overrides other characters that add or remove Outsiders" and is **frozen even if the Outsider count later changes**. The app stores no X, so it cannot compute the poison night, cannot warn on the right night, and cannot survive a mid-game Outsider change (Pit-Hag Hatter, Fang Gu jump, Baron-adjacent effects). `Setup.kt:121-232` derives `choiceTeams` but discards the actual number.
3. **P1 · The `Night 1/2/3` counter tokens are entirely manual** — `characters.json:1935` defines them, `night_guide.json:1517` says to advance them, and nothing in the codebase touches them (`grep -rn '"Night 1"' engine/src app/src` matches nothing). Identical defect to the Summoner's counter; one shared mechanism fixes both.
4. **P1 · The poison does not expire at dusk** — "add the X reminder to the Grimoire, and remove it the following dusk". `GameActions.EXPIRES_AT_DUSK` (`GameActions.kt:231-242`) has no `xaan` entries, so even a Storyteller who places tokens by hand must sweep them up by hand.
5. **P1 · "The Xaan needs to be alive in order to poison" is not checked** — if the Xaan dies on day 1 and X is 2, nothing happens on night 2 and the app says nothing either way. Same for a drunk/poisoned Xaan (standard rules: a Minion with no ability does nothing; the wiki mentions only "alive" explicitly, so flag the impaired case as a Storyteller decision rather than silently applying it).
6. **P1 · No dawn/day-start statement of what the poison did** — on the morning after night X the Storyteller needs to remember that every Townsfolk got false information (which affects how they read the day) and that the poison lasts until **dusk**, i.e. through the whole day, so any day-time Townsfolk ability also malfunctions. Nothing says so.
7. **P2 · The Xaan has no `Poisoned` reminder token** — `characters.json:1935` lists only `Night 1/2/3` and `X`, so even manually there is no Xaan-sourced poison token to place on the Townsfolk. The Storyteller has to borrow another character's token (which then reads as e.g. "Poisoned (Poisoner)" in `InfoCalc.impairments`, `InfoCalc.kt:143-146`) or use a generic one.
8. **P2 · The setup screen never asks for the Outsider count** — for a `[X Outsiders]` character the Storyteller should be prompted with a number picker at bag-build time, the way the Fortune Teller is prompted for a red herring (`GameShell.kt:347-376`). Today they must know to change the Outsider count themselves and the app's suggested distribution ignores the Xaan entirely.
9. **P2 · X = 0 is a legal and meaningful choice and nothing supports it** — "There are no Outsiders in play. The Xaan never poisons anyone", and the wiki notes the Xaan can then bluff as the Zealot. With no X stored, the app cannot show the Storyteller "X = 0: the Xaan never poisons."
10. **P3 · X > 3 has no token** — see the open question above; the app should at least warn.

## Proposed behaviour (spec)

### Setup

- Add a **setup choice for X**. Generalise: any character whose `SetupModifier.choiceTeams` is non-empty and whose bracket contains a bare `X` should prompt for the chosen count and **store it**. Minimal implementation: `GameState` gains `setupChoices: Map<String, Int>` (characterId → chosen value), written at setup and never changed afterwards.
  - Prompt, in the pre-night-1 chain alongside the Fortune Teller/Drunk/Lunatic/Marionette dialogs (`GameShell.kt:347-479`): `Xaan is in play — how many Outsiders? (this is X)` with a 0..5 number row, defaulting to the base distribution's Outsider count.
  - `validateSetupState` (`GameActions.kt:503-561`) gains: `Xaan: choose X (the number of Outsiders)` when unset, and `Xaan: X is <n> but the bag has <m> Outsiders` when they disagree.
  - Setup note: `The Xaan's Outsider count overrides the Baron and any other setup modifier.`
- Keep `Setup.modifierFor`'s current relaxation behaviour (`Setup.kt:143-171`) — it already lets any Outsider count validate. Once X is stored, `adjustedDistribution` should use it as the concrete Outsider count so the suggested bag is right.

### Night counter (shared with the Summoner)

`GameActions.advanceNightCounter(state, sourceId = "xaan", labels = listOf("Night 1","Night 2","Night 3"))`, run at the start of each night for every Xaan holder: exclusively place `Night <cycle>` on the Xaan (and drop it once `cycle > labels.size`).

### Night X

- **when**: other/first night where `state.cycle == X`. **wake condition**: the Xaan does not wake; the step is a Storyteller action. It fires only when the Xaan is **alive**. If the Xaan is drunk/poisoned, show the step with `! The Xaan is drunk/poisoned — by the usual rules their ability does not work tonight. Your call.` and let the Storyteller apply or skip it.
- **targets**: none — the effect is automatic and global.
- **immediate effects**:
  - Place `PlacedReminder("xaan","X")` on the Xaan (record of the night), **and**
  - make the poison **derived**, not token-by-token, by extending `StatusEffects.derivedPoison` (`StatusEffects.kt:14-33`):

```
// Xaan: while the X reminder is in the grimoire and the Xaan is alive,
// every Townsfolk is poisoned (until dusk).
val xaanActive = seats.any { it.characterId == "xaan" && it.alive &&
        it.reminders.any { r -> r.sourceId == "xaan" && r.label == "X" } }
if (xaanActive) for (p in seats) {
    if (p.characterId?.let(lookup)?.team == Team.TOWNSFOLK)
        result[p.id] = "Poisoned by the Xaan (night X — all Townsfolk, until dusk)"
}
```
  This makes `isImpaired` true for every Townsfolk automatically, which in turn makes `InfoCalc.commonCaveats` (`InfoCalc.kt:158-166`) emit the caveat and makes the "False info to show instead" panel appear (`NightScreen.kt:903-930`) — the whole downstream chain already works once the derived poison exists, exactly as it does for the No Dashii.
  - **Team is by true character**, so the Drunk (Outsider) is correctly excluded and the Marionette/Lunatic (Minion/Outsider) are excluded, matching the wiki's Acrobat/Drunk example. Travellers are excluded (not Townsfolk).
  - If a Townsfolk is created mid-night (Pit-Hag, Summoner conversion) the derived rule picks them up automatically.
- **deferred effects**:
  - **Dawn briefing**: `Night X — every Townsfolk was poisoned. All Townsfolk information tonight was FALSE.` plus the list of poisoned seats.
  - **Day-start briefing**: `Xaan poison lasts until DUSK — every Townsfolk day ability malfunctions today too.`
- **expiry**: `xaan`/`X` → **EXPIRES_AT_DUSK** (`GameActions.kt:231-242`). Because the poison is derived from the token, removing the token at dusk removes the poison from every Townsfolk in one step — no sweeping.
- **information**: none computed for the Xaan itself; the effect is that *every other* info step becomes false. The night sheet should also add, on each poisoned Townsfolk's step that night, `POISONED BY THE XAAN — this info must be false.` (this falls out of `InfoCalc.impairments`, `InfoCalc.kt:133-153`, once `derivedPoison` reports it).
- **visibility**: nothing is shown to any player. The Xaan does not wake at all.
- **interactions to handle explicitly**:
  - Outsider count changing mid-game does **not** move X (wiki example 2). Storing X at setup is exactly what enforces this.
  - X = 0 → the step never fires; night 1's step should say `X = 0 — the Xaan never poisons this game.`
  - X > 3 → warn `X = <n>: the official reminder set only goes to Night 3.`
  - Vortox + Xaan: a Vortox already forces Townsfolk info false (`InfoCalc.kt:161-164`); the two stack harmlessly.
  - Exorcist/Acrobat/Monk etc.: their abilities simply do not work that night — the derived poison covers them without special-casing.

### UI text the steps should display

- Nights 1..X-1: `Night <n> of X=<X> — the Xaan does nothing tonight.`
- Night X: `NIGHT X — ALL TOWNSFOLK ARE POISONED until dusk. Every Townsfolk's information tonight is false.` plus a listed count `<n> Townsfolk poisoned:` and the names.
- Night X when the Xaan is dead: `The Xaan is dead — no poison tonight (the Xaan must be alive to poison).`
- After night X: `The Xaan has no further effect this game.`

### Data changes

- `characters.json:1935`: no change to the official reminders. (The poison is derived, so no Xaan `Poisoned` token is needed — but if the project prefers explicit tokens on each seat, add `"Poisoned"` and place one per Townsfolk, and add `"xaan" to "Poisoned"` to `EXPIRES_AT_DUSK` alongside `"xaan" to "X"`.)
- `GameActions.kt:231-242`: add `"xaan" to "X"` (and `"xaan" to "Poisoned"` if the explicit-token route is taken).
- `night_guide.json:1517`: rewrite both sections around the stored X; state that X is fixed at setup and unaffected by later Outsider changes; state the alive requirement; state that the poison runs through the following **day** until dusk; add the X = 0 case.
- `night_and_jinxes.json`: no jinxes to add.
- New engine state: `GameState.setupChoices: Map<String, Int>` (or an equivalent) — also usable for other `[?]`/`[X]` bracket characters.

## Tests to add

1. **All Townsfolk poisoned on night X** — *Given* an 11-seat game with a Xaan, X = 1, and 7 Townsfolk, *When* the `xaan`/`X` token is on the living Xaan on night 1, *Then* `StatusEffects.derivedPoison` returns exactly those 7 ids and `isImpaired` is true for each.
2. **Non-Townsfolk are unaffected** — in the same state, the Drunk (characterId `drunk`, an Outsider) is **not** in `derivedPoison`'s Xaan entries, the Minions are not, the Demon is not, and Travellers are not.
3. **`InfoCalc` warns on night X** — *Given* the above, *When* `InfoCalc.compute(..., "empath", <a poisoned Empath>)` runs, *Then* its caveats contain a "Poisoned by the Xaan" entry.
4. **Poison ends at dusk** — *Given* the `X` token placed on night X, *When* `advancePhase` runs NIGHT→DAY→NIGHT, *Then* the token is gone and no Townsfolk is poisoned by the Xaan on the next night. **Also**: *When* only NIGHT→DAY has run, *Then* the Townsfolk are still poisoned (the poison lasts through the day, until dusk).
5. **Dead Xaan does not poison** — *Given* the `X` token but a dead Xaan, *Then* `derivedPoison` returns no Xaan entries.
6. **X is frozen at setup** — *Given* `setupChoices["xaan"] == 1` and a Pit-Hag creates a second Outsider on night 2, *Then* the night-2 step reports no poison (wiki example 2).
7. **X = 0 never poisons** — *Given* `setupChoices["xaan"] == 0`, *Then* no night ever places the `X` token and the night-1 step text says the Xaan never poisons.
8. **Setup validation requires X** — *Given* a Xaan in the bag and no stored X, *When* `validateSetupState` runs, *Then* it reports "Xaan: choose X (the number of Outsiders)"; *Given* X = 3 but 2 Outsiders in the bag, *Then* it reports the mismatch.
9. **The Xaan's Outsider count is legal against a Baron** — *Given* a bag with both a Baron and a Xaan and 3 Outsiders on 11 players, *When* `validateBag` runs, *Then* it returns no issues (guards the current relaxation behaviour at `GameActions.kt:435-442`).
10. **Night counter advances** — *Given* X = 3, *Then* the Xaan holds `Night 1` on night 1, `Night 2` on night 2, and `Night 3` + `X` on night 3.
