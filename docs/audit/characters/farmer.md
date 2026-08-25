# Farmer (farmer) — experimental (Carousel) townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Farmer>
Jinx cross-checks: <https://wiki.bloodontheclocktower.com/Leviathan>,
<https://wiki.bloodontheclocktower.com/Riot>

Current ability text (verbatim):

> "When you die at night, an alive good player becomes a Farmer."

`characters.json:1358` matches exactly. (The older `townsquare` dataset's
"If you die at night…" is stale; the app's text is current.)

### How to run (verbatim)

> "If the Farmer died tonight, wake an alive good player. Show them the
> **YOU ARE** info token and a Farmer character token, then put them to sleep.
> Replace their previous character token with a Farmer character token."

### Clarifications

- The new Farmer is chosen **that same night**, at the Farmer's night-order
  position, by the **Storyteller** (the ability names no chooser and the
  Farmer is dead).
- "When a player becomes a Farmer, they are no longer their old character, and
  do not have that ability."
- "Farmers that die during the day, such as by execution, do not create more
  Farmers." Night deaths only.
- Multiple Farmers can exist simultaneously; each new one has the full
  ability.
- The new Farmer must be an **alive good player**. Wiki example: "Evin could
  not become the Farmer, because he is evil."
- The new Farmer need not be a Townsfolk. Wiki example 2: "the Heretic becomes
  a Farmer", and "there is now no Heretic and no Choirboy in play".

### Examples (verbatim)

1. "Julian is the Farmer. The Demon kills him at night. Evin is the
   Fearmonger, and Sarah is the Alchemist. Sarah becomes the Farmer that
   night. Evin could not become the Farmer, because he is evil."
2. "On the 2nd night, the Farmer dies. The Choirboy becomes the Farmer. On the
   3rd night, the new Farmer dies, and the Heretic becomes a Farmer. There is
   now no Heretic and no Choirboy in play, and there are three Farmers in
   play, two of which are dead."

### Jinxes — current official text (verbatim)

- **Leviathan + Farmer**: "Each night\*, the Leviathan chooses an alive good
  player (different to previous nights): a chosen Farmer uses their ability but
  does not die."
- **Riot + Farmer**: "Each night\*, Riot chooses an alive good player
  (different to previous nights): a chosen Farmer uses their ability but does
  not die."

These give the Leviathan/Riot a nightly *choice* they otherwise do not have,
and make the chosen Farmer create a new Farmer **without dying**.

### Not settled by the wiki (flagged)

- Whether a **drunk or poisoned** Farmer's night death creates a new Farmer.
  The general rule (a malfunctioning ability does nothing) says no; the app's
  own `night_guide` already asserts no. Flagged as convention, not a quoted
  rule.
- Whether the Drunk may be chosen as the new Farmer (they are good and alive);
  and if so, whether they become a genuinely sober Farmer.
- What happens when no alive good player exists.

### Night order

Other nights only, after the Amnesiac and before the Tinker. The app's data
matches the reference ordering (`townsquare` `roles.json`: farmer 48, choirboy
44, professor 43). Never on the first night.

## What the app does today

Works, in one line each:

- Night-order position (`night_and_jinxes.json:441`, other nights only).
- `night_guide.json:965` has a good `other` entry — including the
  "only act if the Farmer died tonight" condition, the token replacement, and
  the impairment convention — plus a
  `GuideShow(label = "New Farmer", kind = "token", token = "self")`, which
  resolves to the Farmer token (`NightScreen.kt:375-377`) and renders a
  full-screen `YOU ARE` + Farmer card (`ShowCards.kt:127-142`). That card is
  genuinely one tap.
- `StatusEffects.deathNotes` (`StatusEffects.kt:97`) prints
  `"Farmer: a living good player becomes a Farmer tonight."` when the
  storyteller is about to kill a Farmer — and it fires from both kill paths
  (`SeatSheet.kt:238-252` and the Demon kill panel, `NightScreen.kt:588`).
- `DeathRecord` (`GameState.kt:77-90`) already stores `atNight`,
  `characterIdAtDeath` and `abilityImpairedAtDeath` — every input the trigger
  needs.

Storyteller experience:

- A "Farmer" row appears every night from night 2, with all Farmer holders
  listed in the title (`NightScreen.kt:735-742`), dead ones marked `†`. It
  appears whether or not a Farmer died, and the dawn guard
  (`GameShell.kt:145-158`) forces it to be ticked.
- With three Farmers (two dead) the row reads "Farmer — Julian †, Kira †,
  Sarah" and gives no clue which one died tonight.
- To transfer, the storyteller leaves the Night tab, opens the Grimoire, taps
  the chosen seat, taps **Change character** (`SeatSheet.kt:310`) and picks
  the Farmer, then returns to the Night tab to show the card. Nothing filters
  the picker to alive good players.
- `InfoCalc.supports` does not include `"farmer"` (`InfoCalc.kt:29-36`), so the
  step shows no computed candidates and — importantly — **no impairment
  caveats at all** (`NightScreen.kt:836` gates all caveats behind
  `InfoCalc.supports`).

## Defects and gaps

1. **P0 · The step fires unconditionally and gives no way to know whether it
   should.** Rules: "If the Farmer died tonight…". App: `NightOrder.build`
   emits the row whenever any seat holds `farmer` (`NightOrder.kt:142-178`).
   All the data to decide is in `state.deaths` and unused. Repro: seat a
   Farmer, run night 3 with no deaths — the Farmer row is present and must be
   ticked.

2. **P0 · Execution deaths are not excluded.** "Farmers that die during the
   day, such as by execution, do not create more Farmers." App: nothing
   distinguishes them; if the storyteller half-remembers the rule they may
   create a Farmer after a day execution. `DeathRecord.atNight` and
   `cause` already answer this.

3. **P0 · The transfer is entirely manual and unguarded.** The picker
   (`CharacterPicker` reached from `SeatSheet.kt:310`) offers every character
   on the script for every seat: an evil player, a dead player, or the Farmer's
   own corpse can all be made the new Farmer. The rules restrict it to an
   **alive good** player.

4. **P1 · A drunk/poisoned Farmer's death still shows the same step.**
   `abilityImpairedAtDeath` is recorded at the moment of death
   (`GameActions.kt:153`) and never read. The step should say "the Farmer was
   poisoned when they died — no new Farmer" and emit no action.

5. **P1 · The old character is not removed from play cleanly.**
   `GameActions.assignCharacter` (`GameActions.kt:46-53`) changes
   `characterId` and clears `shownCharacterId` only. If the Heretic becomes
   the Farmer, the Heretic's own reminders stay in the grimoire, and if the
   **Drunk** is chosen the `("drunk","Is the Drunk")` reminder placed by the
   setup dialog (`GameShell.kt:392-400`) stays, so
   `StatusEffects.isImpaired` keeps reporting them drunk forever.

6. **P1 · No dawn/day-start briefing.** The storyteller must remember, next
   morning, that a character left play (no more Heretic, no more Choirboy) —
   which changes what other characters can truthfully be told. Nothing
   surfaces it, and `GameLogDialog` (`GameExtras.kt:46-106`) logs only deaths
   and nominations.

7. **P1 · Jinx data is drifted from the current official text.**
   `night_and_jinxes.json:205` reads "If Leviathan is in play and a Farmer dies
   by execution, a good player becomes a Farmer that night." and `:215` reads
   "If Riot kills the Farmer, a good player becomes a Farmer tonight."
   The current official texts (quoted above) instead give the Leviathan/Riot a
   nightly choice of an alive good player, and make a chosen Farmer use their
   ability **without dying**. Same drift affects
   `leviathan×ravenkeeper` (`:190`-ish), `leviathan×sage`, `riot×ravenkeeper`,
   `riot×sage` and `riot×grandmother` (official: "If Riot is in play and the
   Grandchild dies by execution, evil wins."). Whoever owns the jinx dataset
   should re-scrape; flagged here because the Farmer is the character whose
   behaviour changes most.

8. **P2 · Multiple Farmers make the row unreadable and ambiguous.** The title
   concatenates every holder; there is no "who died tonight" and no way to
   check off "this Farmer's transfer is done" separately from another's.

9. **P2 · No impairment warning on the step at all** (the
   `InfoCalc.supports` gate, `NightScreen.kt:836`). Neither the dead Farmer's
   impairment nor the *new* Farmer's situation is flagged.

10. **P2 · The one-tap card is right but the seat is not.** The `» New Farmer`
    card shows `YOU ARE` + Farmer token, which is exactly right, but the app
    never records or highlights *which* seat it was shown to, so there is no
    check that the grimoire token was actually changed.

11. **P2 · Nothing handles "no alive good player".** With no eligible target
    the step should say so and be a no-op, not silently invite the storyteller
    to pick an evil player.

12. **P3 · Ability text vs step detail duplication.** `NightOrder.kt:147-148`
    uses `otherNightReminder` as `detail`; `StepDetailPanel` then repeats the
    guide paragraph (`NightScreen.kt:792-801`).

## Proposed behaviour (spec)

### A. Trigger derivation (engine, pure)

```
data class FarmerTrigger(val deadFarmerId: Long, val impaired: Boolean)

fun farmerTriggers(state: GameState): List<FarmerTrigger> =
    state.deaths.filter {
        it.day == state.cycle &&
        it.atNight &&
        it.characterIdAtDeath == "farmer" &&
        !it.resurrected
    }.map { FarmerTrigger(it.playerId, it.abilityImpairedAtDeath ?: false) }
```

Notes:
- `atNight` is set from `state.phase == Phase.NIGHT` at kill time
  (`GameActions.kt:150`), so an execution during the day is excluded
  automatically. Add an explicit `cause != EXECUTION` guard as belt and braces
  (a storyteller can record an execution at night in odd situations).
- A resurrection later in the same night (Professor) does **not** undo the
  transfer — the Farmer did die. `resurrected` is set by
  `GameActions.resurrect` (`GameActions.kt:173-181`); the trigger should be
  evaluated from the record's *cause*, so use `!it.resurrected` only if the
  human ruling says otherwise. Default: **ignore `resurrected`** — keep the
  trigger. (Both branches are one boolean; make it a named constant.)
- Jinx: when a Leviathan or Riot is in play, add a trigger for the player they
  chose tonight if that player is a Farmer, with **no death**.

### B. Night step (structured form)

- **when**: `other` only.
- **wake condition**: `farmerTriggers(state).isNotEmpty()`. When empty, emit
  **no row**. One row per trigger when several Farmers die in one night
  (Al-Hadikhia, Riot), with ids `"farmer:<deadFarmerId>"` so each is ticked
  separately.
- **targets**: 1 player. Constraints, enforced in the picker:
  - `alive == true`;
  - `!player.isEvil(lookup)` (**alignment**, not team — an evil-turned Cult
    Leader is not eligible);
  - not the dead Farmer;
  - not already a Farmer (pointless but harmless — allow with a warning).
  Sort: Townsfolk first, then Outsiders; within each, seats whose character has
  already used its ability (spent once-per-game, no-ability marked) first,
  since those cost the good team least. Show each candidate's current
  character so the storyteller sees what is being spent.
- **immediate effects**, as one undoable action
  `GameActions.becomeFarmer(state, newFarmerId, lookup)`:
  1. `removeRemindersFromSource(state, oldCharacterId)` across the grimoire
     (kills the stale `("drunk","Is the Drunk")`, `("marionette","Is the
     Marionette")`, `("fortuneteller","Red herring")` on that seat, etc.);
  2. `assignCharacter(newFarmerId, "farmer")` — which already clears
     `shownCharacterId`;
  3. leave `alignmentFlipped` alone (they were good and stay good);
  4. leave `alive`, `ghostVoteUsed`, `note` alone.
- **impaired case**: when `trigger.impaired`, the row is still emitted but with
  no picker and the text `"The Farmer was drunk/poisoned when they died — no
  one becomes a Farmer."`, so the storyteller sees the rule applied rather
  than a missing row.
- **deferred effects**: none.
- **expiry**: no tokens; nothing expires.
- **information**: none to compute. `InfoCalc` need not support the Farmer;
  instead the caveat rendering must be lifted out of the
  `InfoCalc.supports` gate (see cross-cutting note) so impairment warnings
  appear here.
- **visibility**: show the new Farmer `YOU ARE` + Farmer token
  (`ShowCard.CharacterCard("YOU ARE", "farmer")`) — keep the existing
  `GuideShow`, but bind it to the chosen seat so the step can mark it shown.
  Nothing is shown to the Demon, Minions or the dead Farmer.

### C. Briefings

- **At the kill**, keep `deathNotes`' existing line but make it conditional and
  specific: `"Farmer: they die at night, so an alive good player becomes a
  Farmer tonight."` / `"Farmer is poisoned — no new Farmer."` /
  `"Executed — no new Farmer."`
- **At dawn**, after a transfer: `"<Name> is now the Farmer. The <old
  character> is no longer in play."` This is storyteller-private.
- **Script-level note** when a Farmer is in the bag: `"Characters can leave
  play mid-game — re-check any info that depended on the <old character>."`

### D. UI text for the step

- Title: `Farmer — Julian died tonight`
- Detail: `Choose an alive good player. Wake them, show YOU ARE and the Farmer
  token, and swap their token in the grimoire.`
- Candidate chip: `Sarah — Alchemist` / `Iris — Heretic (leaves play)`
- After acting: `Sarah is the Farmer. The Alchemist is no longer in play.`
- Impaired: `Julian was POISONED when he died — no one becomes a Farmer.`

### E. Data changes

- `night_and_jinxes.json:205` and `:215`: replace with the current official
  Leviathan/Riot Farmer jinx texts (quoted above). Same pass should fix
  `leviathan×ravenkeeper`, `leviathan×sage`, `riot×ravenkeeper`, `riot×sage`
  and `riot×grandmother`.
- `night_guide.json:965`: keep the prose; add the "day deaths don't count"
  sentence and the eligibility rule (alive **and** good by alignment).
- `characters.json:1358`: no change.
- Night order: no change.

## Tests to add

1. `no farmer step when no farmer died tonight`
   Given a living Farmer and no deaths; When the night-3 sheet is built; Then
   it contains no step whose id starts with `"farmer"`.

2. `farmer step appears after a night death`
   Given a Farmer killed at night on cycle 2; Then the night-2 sheet contains
   `"farmer:<id>"`, positioned between the Amnesiac's and the Tinker's slots.

3. `execution does not trigger the farmer`
   Given a Farmer executed on day 2; When the night-3 sheet is built; Then no
   farmer step exists. (`DeathRecord.atNight == false`.)

4. `impaired farmer death creates no new farmer`
   Given a Farmer with a `("poisoner","Poisoned")` reminder killed at night;
   Then the trigger's `impaired` is true and the step exposes no target
   picker.

5. `only alive good players are eligible`
   Given a night Farmer death with an evil Minion, a dead Townsfolk and an
   alive Heretic in play; Then the candidate list contains only the Heretic.

6. `an evil-turned cult leader is not eligible`
   Given a Cult Leader with `alignmentFlipped = true` (team still TOWNSFOLK);
   Then they are excluded from the candidate list. (Guards against filtering
   on `team` instead of `isEvil`.)

7. `becoming the farmer removes the old character's reminders`
   Given the Drunk (with `("drunk","Is the Drunk")` and a
   `shownCharacterId`); When `becomeFarmer` targets them; Then their
   `characterId == "farmer"`, `shownCharacterId == null`, no reminder with
   `sourceId == "drunk"` remains, and
   `StatusEffects.isImpaired(state, lookup, them)` is false.

8. `three farmers can coexist`
   Given two successive night Farmer deaths and two transfers; Then three
   seats hold `characterId == "farmer"`, two of them dead, and the night sheet
   emits a step only for the Farmer who died that night.

9. `two farmer deaths in one night produce two steps`
   Given an Al-Hadikhia killing two Farmers on cycle 4; Then the night sheet
   contains `"farmer:<a>"` and `"farmer:<b>"` as separate, separately
   tickable rows.

10. `no eligible player yields a no-op step`
    Given a night Farmer death where every other living player is evil; Then
    the step reports "no alive good player" and offers no action.

11. `leviathan jinx creates a farmer without a death`
    Given a Leviathan in play that chose the living Farmer tonight; Then a
    farmer step is emitted, the chosen Farmer stays alive, and a new Farmer can
    be selected.
