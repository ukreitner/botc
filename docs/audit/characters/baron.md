# Baron (baron) — Trouble Brewing Minion

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Baron> (fetched 2026-08-25).

Current ability text:

> "There are extra Outsiders in play. [+2 Outsiders]"

How to run — verbatim:

- *"remove any two Townsfolk character tokens and add any two Outsider character
  tokens"* to the bag during setup. The Storyteller picks **which** Outsiders.
- *"This change happens during setup, and it does not revert if the Baron dies."*
  It equally does not revert if the Baron is drunk or poisoned — a setup ability
  has already resolved before the game begins.
- The added characters are always Outsiders replacing Townsfolk, never other
  types.
- If the script has fewer Outsider types than needed, the Storyteller adds what
  they can (the classic case being the Drunk, whose token is a Townsfolk stand-in
  plus an IS THE DRUNK reminder).
- The Baron **never wakes** at night. Its whole contribution is at setup, plus
  the social game.
- Worked example from the wiki: at 7 players the base is 5 Townsfolk /
  0 Outsiders / 1 Minion / 1 Demon; with the Baron it becomes
  **3 / 2 / 1 / 1**.

Jinxes (from `night_and_jinxes.json`):
- `baron` × `heretic`: **"The Baron might only add 1 Outsider, not 2."**
- `plaguedoctor` × `baron`: "If the Storyteller gains the Baron ability, up to 2
  Townsfolk players become not-in-play Outsiders."

Night order: absent from both `firstNight` and `otherNight`. Correct.

## What the app does today

Data
- `characters.json` — `baron`: `team: "minion"`, ability `"There are extra
  Outsiders in play. [+2 Outsiders]"`, `setup: true`, no reminders, no night
  reminders. Matches the wiki exactly.
- `night_and_jinxes.json` — absent from both order lists; both jinxes present.
- `night_guide.json` — **no entry**. Correct (never wakes).

Engine
- `Setup.modifierFor` (`Setup.kt:121-231`) parses `[+2 Outsiders]` via
  `deltaRegex` → `SetupModifier("baron", "+2 Outsiders", outsiderDelta = 2)`.
  `isChoice` is false (no "or"/"to"/"?"/"X"), so it is a hard delta.
- `Distribution.plus` (`Setup.kt:21-32`) applies it as
  `outsiders += 2; townsfolk -= 2`, clamped at zero. Verified against the wiki
  example: 7 players `5/0/1/1` → `3/2/1/1`. 5 players `3/0/1/1` → `1/2/1/1`.
- `Setup.adjustedDistribution` (`:252-255`) and `Setup.allowedDistributions`
  (`:261-272`) fold it in; `combine` (`:292-300`) sums deltas order-independently.
- `GameActions.randomBag` (`GameActions.kt:338-402`) folds the modifier as the
  Baron is drawn (demons → minions → outsiders → townsfolk order at
  `GameActions.kt:345`), then reconciles counts over 4 passes.
- `GameActions.validateBag` (`:420-496`) rejects a bag whose team counts do not
  match an allowed distribution, with a per-team message
  ("Outsider: 0 in bag, expected 2").
- `SetupTest.kt:37-61` already pins "baron adds two outsiders from townsfolk" and
  order-independence with the Vigormortis.
- `GameActionsTest.kt:94-106` pins that `randomBag` produces a legal Baron bag
  across 60 seeds.

UI
- `SetupScreen.kt:356-375` — the bag builder header prints
  "Need: 3 townsfolk · 2 outsiders · 1 minions · 1 demon  (after [+2 Outsiders])",
  which is exactly the right affordance.
- `SetupScreen.kt:388-397` — "Randomize" uses `randomBag`.
- `SetupScreen.kt:418-431` — validation issues are listed in red with a
  "Deal anyway (I know what I'm doing)" escape at `:489-493`.
- `GameShell.kt:133-140` — `validateSetupState` re-checks at the SETUP → NIGHT
  boundary, so a hand-assigned grimoire cannot skip the check.
- `SeatSheet.kt:222-235` — the Baron's jinxes are listed on its seat when the
  partner is in play.

Storyteller experience for the Baron is, in short, **good**: the setup maths is
right, the bag builder tells you the adjusted target, the randomiser respects it,
and the boundary guard catches manual mistakes. Everything below is refinement.

## Defects and gaps

1. **P1 · The `baron` × `heretic` jinx is not applied to bag validation.**
   The jinx says the Baron *might* add only 1 Outsider. `Setup.modifierFor`
   produces a fixed `outsiderDelta = 2` with an empty `choiceTeams`, so
   `allowedDistributions` (`Setup.kt:261-272`) yields exactly one distribution
   and `validateBag` rejects the legal +1 bag with "Outsider: 1 in bag, expected
   2". Repro: build a bag containing both the Baron and the Heretic with +1
   Outsider → a legal setup is flagged as illegal, and "Deal anyway" is the only
   way past. `GameData.activeJinxes` already exists (used at
   `SeatSheet.kt:225`); `validateBag` never consults it.

2. **P1 · The Storyteller is never told *which* Outsiders the Baron's slots
   should be, or warned when the script cannot supply them.**
   If a script has 0 or 1 Outsider types, `randomBag` silently returns `null`
   after 200 attempts (`GameActions.kt:401`) and the UI just does nothing useful
   on "Randomize". There is no message explaining "this script only has 1
   Outsider — the Baron cannot add 2; use the Drunk or a Fabled".

3. **P2 · No "the Baron's effect is permanent" reassurance.**
   Storytellers regularly ask whether the Outsider count reverts when the Baron
   dies or is poisoned. The app never says. The Baron has no night step and no
   `night_guide.json` entry, so there is nowhere the answer currently lives.

4. **P2 · `validateBag` is called without `fabledIds` from the Setup screen.**
   `SetupScreen.kt:356` calls
   `GameActions.validateBag(selected, playerCount, allowAnyDuplicates = allowDuplicates)`
   — the `fabledIds` parameter (`GameActions.kt:423`) defaults to empty, so the
   Sentinel's ±1 Outsider relaxation (`GameActions.kt:444-455`) is not applied at
   bag-build time even though it *is* applied at the SETUP → NIGHT boundary via
   `validateSetupState` (`GameActions.kt:511`). With a Baron plus a Sentinel the
   builder and the boundary guard disagree. (Not Baron-specific, but the Baron is
   the character that makes Outsider-count relaxations matter.)

5. **P2 · The Baron creates the game's Outsider-heavy shape, and nothing helps
   the Storyteller reason about it later.**
   A Baron game usually contains a Drunk and/or a Recluse; both need setup
   decisions (`GameShell.kt:377-413` handles the Drunk well). There is no
   post-setup summary saying "Baron game: 2 Outsiders — `<X>` and `<Y>`", which
   is the single most useful line for tracking Librarian/Investigator info later.

6. **P3 · `plaguedoctor` × `baron` jinx unimplemented** (Storyteller-gained Baron
   ability turning up to 2 Townsfolk into not-in-play Outsiders mid-game).

7. **P3 · No Baron-specific text anywhere in-app.** The seat sheet shows the
   ability string (`SeatSheet.kt:196-198`) and the Reference tab lists it; there
   is no "how to run" prose because `night_guide.json` is keyed on night steps.

## Proposed behaviour (spec)

### Setup-only character — no night step
- **when:** never wakes. No entries in the night-order lists or
  `night_guide.json` night blocks.
- **targets / immediate effects / expiry / information / visibility:** none at
  night.

### Setup behaviour (the whole spec)

1. **Jinx-aware setup modifiers.** `Setup.modifierFor` must become jinx-aware, or
   `validateBag` must post-process. Preferred: add an optional parameter and a
   jinx table:
   ```kotlin
   // Setup.kt
   fun modifierFor(character: Character, inPlayIds: Set<String> = emptySet()): SetupModifier?
   ```
   with a rule: if `character.id == "baron" && "heretic" in inPlayIds`, return the
   modifier with `choiceDeltas = mapOf(Team.OUTSIDER to setOf(1, 2))` and
   `choiceTeams = setOf(Team.OUTSIDER)`. `Setup.allowedDistributions` then
   naturally yields both `+1` and `+2` shapes and `validateBag` accepts either.
   Thread `inPlayIds` through `adjustedDistribution`, `allowedDistributions`,
   `validateBag` and `randomBag`.

2. **Script capacity check.** Before offering "Randomize", compute
   `availableOutsiders = script.characters.count { it.team == OUTSIDER }` and, if
   `adjusted.outsiders > availableOutsiders`, show a blocking-but-overridable
   message: "This script has only `N` Outsider(s); the Baron needs `M`. Add
   another Outsider to the script, run the Sentinel, or deal manually."
   Also make `randomBag` returning `null` produce a visible reason rather than a
   silent no-op at `SetupScreen.kt:388-397`.

3. **`validateBag` call site fix.** Pass `state.fabledIds` (or the setup screen's
   selected Fabled) from `SetupScreen.kt:356` so the builder and the boundary
   guard agree.

4. **Setup summary line.** After dealing, print in the grimoire/day-start
   briefing: "Setup modifiers: Baron [+2 Outsiders] → Outsiders in play: `<X>`,
   `<Y>`. This does not change if the Baron dies or is poisoned."

5. **Reference text.** Add a `baron` entry to `night_guide.json` using a new
   optional `reference` block (or a parallel `character_guide.json`) so
   setup-only and passive characters have somewhere for their how-to-run prose:
   > "Setup only. Remove any two Townsfolk tokens from the bag and add any two
   > Outsider tokens. The change is permanent — it does not revert when the Baron
   > dies, and it is unaffected by poison or drunkenness. With the Heretic in
   > play you may add only one Outsider instead of two."
   The same mechanism serves the Recluse and the Saint (see those files).

### UI text
- Bag builder (unchanged, already good): "Need: 3 townsfolk · 2 outsiders ·
  1 minions · 1 demon  (after [+2 Outsiders])".
- Heretic present: "Baron + Heretic: 1 **or** 2 extra Outsiders are both legal."
- Capacity failure: "Only 1 Outsider on this script — the Baron needs 2."
- Post-deal summary: "Baron game — Outsiders: `<X>`, `<Y>`."

### Data changes
- No change to `characters.json` (`baron` is correct today).
- No night-order change.
- Add a reference block for `baron` (see point 5).

## Tests to add

1. `Given` 7 players and a bag containing the Baron, `When`
   `Setup.adjustedDistribution(7, bag)`, `Then` it is `3/2/1/1`
   (regression guard for the wiki example).
2. `Given` 5 players and a bag containing the Baron, `When`
   `Setup.adjustedDistribution(5, bag)`, `Then` it is `1/2/1/1`.
3. `Given` a bag containing the Baron **and** the Heretic with 1 Outsider,
   `When` `validateBag(bag, playerCount)`, `Then` **no** Outsider-count issue is
   reported (jinx relaxation).
4. Same bag with 2 Outsiders, `Then` also no issue (both are legal).
5. `Given` a bag containing the Baron and 0 Outsiders, `When` `validateBag`,
   `Then` an issue naming the expected Outsider count is reported.
6. `Given` a script with only one Outsider type and a Baron, `When`
   `randomBag(characters, 8)`, `Then` it returns `null` and the caller surfaces a
   capacity message (assert on a new `randomBagOrReason` API).
7. `Given` a Baron game where the Baron is executed on day 1, `When`
   `Setup.adjustedDistribution` is recomputed from the surviving bag, `Then` the
   Outsider count is unchanged (permanence — assert nothing in the engine
   re-derives the distribution mid-game).
8. `Given` a Baron and a Sentinel Fabled, `When`
   `validateBag(bag, playerCount, fabledIds = ["sentinel"])`, `Then` `+1`, `+2`
   and `+3` Outsider shapes are all accepted.
