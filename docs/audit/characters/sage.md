# Sage (sage) — Sects & Violets Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Sage> (fetched 2026-08-25).

Current ability text:

> "If the Demon kills you, you learn that it is 1 of 2 players."

**How to run (quoted):**

> "If the Sage was killed by the Demon, wake the Sage. Point at two players, one
> who is the Demon that killed the Sage. Put the Sage to sleep."
>
> "If the Sage dies early, you will probably want to show the Sage two alive
> players, which lets the evil team still have a slim chance of winning. If the
> Sage dies on the final night, feel free to show one alive and one dead player."

**Examples (quoted):**

> - "During the second night, the Demon kills the Sage. The Storyteller points at
>   two players, one of whom is the Demon."
> - "During the final night, the Demon kills the Sage, who is drunk because of the
>   Sweetheart. The Storyteller points at a dead player and one of the remaining
>   three alive players. **This information is incorrect.**"
> - "The Pit-Hag creates a Demon. Because the Pit-Hag ability says that 'all
>   deaths tonight are arbitrary,' the Storyteller decides that the old Demon
>   dies, and the Sage dies. **Because the Sage died due to the Pit-Hag, not the
>   Demon, the Sage does not wake to learn anything tonight.**"

**Clarifications (from the page's summary sections):**

- Timing: information triggers **only** from a Demon kill, not from executions or
  other deaths.
- Drunk/poisoned: the information shown is incorrect (the pair need not contain
  the Demon).
- Dead Demon: "the Storyteller determines what two players to show."
- Recluse: "Might register as the Demon to the Sage."
- Leviathan / Riot: "A chosen Sage uses their ability but doesn't die."

**Jinxes** (`night_and_jinxes.json:200`, `:225`; wording matches the official
jinx list):

- Leviathan × Sage: "If Leviathan is in play and the Sage dies by execution, they
  wake that night to use their ability."
- Riot × Sage: "If Riot kills the Sage, the Sage wakes that night to use their
  ability."

**Night order.** Other nights only, position 61 (`night_and_jinxes.json:434`) —
after every Demon (Imp 37 … Kazali 52, Riot 53, Leviathan 54) and after
Assassin/Godfather/Gossip/Hatter/Barber/Sweetheart, before Banshee/Professor.
No first-night step. That ordering is correct in the data.

## What the app does today

- `characters.json:893-904` — ability, no reminders, empty `firstNightReminder`,
  `otherNightReminder` = "If the Sage was killed by a Demon: Point to two players,
  one of which is that Demon."
- `night_and_jinxes.json:434` — other-night index 61. Correct. **Works.**
- `night_guide.json:529-534` — good prose, including the drunk/poisoned/Vortox
  caveat and "The Sage does not wake if they died any other way." No show cards.
- `InfoCalc.supports` includes `sage` (`InfoCalc.kt:33`); dispatch at
  `InfoCalc.kt:72`; the calculator is `InfoCalc.kt:423-431`:
  it lists every seat whose team is DEMON and returns
  `"Point to 2 players: one must be the Demon"` with detail
  `"Demon: <names> — pair with any other player"` and the single caveat
  `"Only if the Demon killed the Sage; other deaths don't wake them."`
- `StatusEffects.deathNotes` (`StatusEffects.kt:96`) adds
  `"Sage: if the Demon killed them, show 2 players, one the Demon."` — this is
  surfaced in `DemonKillPanel` when the Demon's target is selected
  (`NightScreen.kt:586-590`) and in the seat sheet (`SeatSheet.kt:240-251`).
  **Works, and is the single best thing the app does for the Sage.**
- `DeathRecord` already snapshots `cause` and `abilityImpairedAtDeath`
  (`GameState.kt:77-90`), set in `GameActions.kill` (`GameActions.kt:136-156`).
  Nothing reads them for the Sage.

**The storyteller's experience.** From night 2 onwards a "Sage" row is always
present, with the same headline whether or not the Sage is alive. On the one
night it matters — the night the Demon kills them — the row is decorated with
`"All holders are dead — usually skip."` (`NightScreen.kt:702, :751-757`), i.e.
the app actively advises skipping the step at the exact moment the Sage must
wake. The ST must remember the trigger themselves, pick the pair themselves, and
remember on every later night that the Sage is already resolved.

## Defects and gaps

1. **P0 · The row tells the ST to skip the Sage on the only night they act.**
   `NightStepRow` computes `allDead = holders.isNotEmpty() && holders.none { it.alive }`
   (`NightScreen.kt:702`) and renders "All holders are dead — usually skip."
   (`NightScreen.kt:751-757`). The Sage acts *because* they are dead. Repro:
   Demon kills the Sage in the Demon step, scroll down to the Sage row → red
   "usually skip" warning. Same wording problem for Ravenkeeper/Banshee.
2. **P0 · The wake condition is never evaluated.** The row appears on every night
   2+ regardless of whether the Sage died tonight, died at all, or died to
   something other than a Demon; and `InfoCalc.sage` (`InfoCalc.kt:423-431`)
   returns the same live answer in all those cases. The engine already has the
   data (`state.deaths`: `day == cycle`, `atNight`, `cause == DeathCause.DEMON`,
   `!resurrected`). Repro: execute the Sage on day 2, advance to night 3 — the
   Sage row is present and reads "Point to 2 players: one must be the Demon",
   which is exactly the wrong instruction (wiki: executions don't trigger).
3. **P0 · "Died at night" is recorded as `DeathCause.DEMON` for every night
   death.** `SeatSheet.kt:271-273` maps the "Died at night" button to
   `DeathCause.DEMON`, and `DemonKillPanel` also uses `DeathCause.DEMON`
   (`NightScreen.kt:628-630`). So an Assassin kill, a Godfather kill, a Gambler
   self-kill, a Moonchild kill, a Sweetheart-driven death, a Pit-Hag arbitrary
   death or a Lycanthrope kill all land in the bucket the Sage's rule keys on.
   Once defect 2 is fixed, the Sage will wake wrongly on all of them (and the
   wiki's third Example is precisely the Pit-Hag case). The engine needs to know
   *who/what* killed, not just "at night".
4. **P1 · No pair picker and no record.** The ST gets a sentence, not a tool.
   There is no way to select the two players shown, no "one must be the Demon
   that killed them" enforcement, no alive/dead guidance from the How-to-Run
   ("two alive players if the Sage dies early; one may be dead on the final
   night"), and no record of what was shown — so the Sage's public claim can't be
   checked later. `InfoCalc.targetsNeeded("sage")` is 0 (`InfoCalc.kt:22-26`), so
   the target chip row (`NightScreen.kt:841-861`) never renders for the Sage.
5. **P1 · Multiple demons / which demon.** `InfoCalc.sage` lists *all* DEMON-team
   seats and lets the ST pair "any other player" (`InfoCalc.kt:424-429`). The rule
   is "the Demon **that killed the Sage**". With Legion (every Legion player is a
   Demon), Kazali-made extra Demons, a Scarlet Woman who took over, or Lil'
   Monsta (the Demon is a token held by a Minion), the app gives no guidance.
   *Uncertain:* the wiki page does not resolve the Lil' Monsta case; flagging
   rather than guessing.
6. **P1 · Impairment is read from the wrong moment and mixed with noise.**
   `commonCaveats` (`InfoCalc.kt:158-166`) runs `impairments(...)` against the
   *current* state and always adds `"<name> is dead — they normally don't act."`
   (`InfoCalc.kt:150`) for the Sage — the one character for whom that is false.
   Meanwhile `DeathRecord.abilityImpairedAtDeath` (`GameState.kt:87`), which is
   exactly the right snapshot ("was the Sage drunk/poisoned when the Demon killed
   them?"), is ignored here.
7. **P1 · The Leviathan and Riot jinxes are inert.** Both are in the data
   (`night_and_jinxes.json:200, :225`) and are displayed in the seat sheet
   (`SeatSheet.kt:222-234`), but nothing widens the Sage's wake condition: with
   Leviathan in play an *executed* Sage must wake that night; with Riot, a
   Riot-killed Sage wakes. Neither Leviathan nor Riot performs a
   `DeathCause.DEMON` kill in this app, so the naive fix in defect 2 would break
   both jinxes.
8. **P2 · No "already resolved" state.** After the Sage wakes, the row keeps
   coming back every subsequent night with the same live headline (the engine's
   own playtest fixture literally records `"Sage death already resolved;
   skipped."`, `FullGamePlaytestTest.kt:1046`). The Sage should drop out of the
   sheet once used.
9. **P2 · Recluse misregistration is not offered.** The wiki notes the Recluse
   might register as the Demon *to the Sage*, which lets the ST show a pair with
   no real Demon in it. `InfoCalc.sage` never calls `misregistrations(...)`
   (contrast `InfoCalc.kt:339`, `:363`), so the ST is not reminded.
10. **P2 · No show card.** `night_guide.json:531-533` has `shows: []`. Pointing at
    two seats is physical, but on a phone-run game the ST would benefit from a
    "these two" card that names the pair (and from marking the two seats in the
    grimoire while pointing).
11. **P3 · Vortox handling is generic and correct.** `commonCaveats` adds
    "VORTOX in play — Townsfolk info must be FALSE" for Townsfolk holders
    (`InfoCalc.kt:161-164`), which for the Sage means the pair must *not* contain
    the Demon. **Works** — just make sure the pair picker enforces/offers it.

## Proposed behaviour (spec)

- **when**: other nights only. Wake condition (all must hold):
  - a `DeathRecord` exists with `playerId == sage.id`, `day == state.cycle`,
    `atNight == true`, `resurrected == false`, and the kill is attributable to a
    **Demon** (see the new attribution field below); **or**
  - `leviathan` is in play and the Sage's death this cycle has
    `cause == EXECUTION`; **or**
  - `riot` is in play and the Sage died this cycle by a Riot kill;
  - and the step has not already been completed for this death (a
    `("sage","Woke")` marker or `deathRecordId ∈ state.nightStepsDone`).
  - The Sage being *dead* is required, not disqualifying — suppress the
    "All holders are dead" warning for this row.
- **kill attribution (prerequisite, cross-cutting)**: add
  `killerCharacterId: String?` and `killerPlayerId: Long?` to `DeathRecord`
  (`GameState.kt:77-90`) and a `killer` parameter to `GameActions.kill`
  (`GameActions.kt:136-156`). `DemonKillPanel` passes the Demon's seat
  (`NightScreen.kt:628-630`); `SeatSheet`'s "Died at night" opens a small
  "what killed them?" chooser (Demon / Minion ability / other night death /
  storyteller) instead of hard-coding `DeathCause.DEMON` (`SeatSheet.kt:271-273`).
  This unblocks the Sage, the Choirboy, the Grandmother, the Godfather and
  Vortox/Vigormortis rulings at once.
- **targets**: 2 seats, recorded. Constraints and picker behaviour:
  - Default-select the killing Demon plus one other; sort alive players first.
  - If `alivePlayers.size <= 3` (final night), allow and hint at a dead player as
    the second pick, per the How-to-Run.
  - Validate: exactly 2 distinct seats, not the Sage themselves.
  - If the Sage was impaired at death (`abilityImpairedAtDeath == true`) or a
    Vortox is alive, *require* that the pair does **not** contain the killing
    Demon, and label the panel "FALSE info — the pair must not contain the Demon".
- **immediate effects**: place `PlacedReminder("sage","Woke")` on the Sage (a new
  reminder in `characters.json`), append a `SageShown(day, pair)` record to the
  game log; no status effects, no kills.
- **deferred effects**: none. At dawn the ST announces the death as usual; the
  Sage's information is private.
- **expiry**: `("sage","Woke")` never expires. If the Sage is later resurrected
  (Professor) and killed by the Demon again, clear the marker on `resurrect`
  (`GameActions.kt:173-181`) so the ability can fire again for the new death.
  *Uncertain:* the wiki does not address a resurrected Sage dying twice; the
  reading above ("if the Demon kills you" is not once-per-game) is the natural
  one, but flag it in the UI rather than silently deciding.
- **information**:
  - True answer: `{killing demon} + one other player of the ST's choice`.
  - The panel should list the demon(s) with the killer highlighted, and show the
    alive/dead split.
  - False alternative (impaired / Vortox): any two players excluding the killing
    Demon; offer a one-tap "suggest a false pair".
  - Misregistration: if a Recluse is alive, offer "let the Recluse register as the
    Demon" — the pair may then contain the Recluse and no real Demon.
- **visibility**: only the Sage. Add two show cards to
  `night_guide.json:529-534`: `{label:"The pair", kind:"message",
  text:"One of these two is the Demon"}` (the ST then points), and optionally a
  seat-highlight mode in the grimoire that flashes the two chosen seats.
- **day-time inputs**: none required. Optional (nice): a day-phase record of what
  the Sage publicly claimed, for the ST's own tracking — belongs to the general
  "claims" feature, not to the Sage specifically.
- **interactions/jinxes to handle explicitly**:
  - Leviathan: widen the wake condition to executions; the row must be emitted on
    the night after the Sage's execution.
  - Riot: widen to Riot kills; Riot kills happen during the day, so the row must
    be emitted the following night.
  - Recluse: optional misregistration (above).
  - Vortox: pair must not contain the Demon (already caveated).
  - Sweetheart/Poisoner/No Dashii at the moment of death: use
    `abilityImpairedAtDeath`, not the current state.
  - Pit-Hag "all deaths tonight are arbitrary": those deaths must be recorded with
    `killerCharacterId = "pithag"` so the Sage does **not** wake.
  - Scarlet Woman / Imp star-pass: the killer snapshot is the Demon *at the time
    of the kill*; do not re-resolve it from the current grimoire.

### UI text the step should display

- When not triggered: the row is **not emitted at all**.
- When triggered: `<Sage> was killed by <Demon character> tonight — wake them.`
- Body: `Point at two players; one must be <Demon player>.` (or, when impaired:
  `<Sage> was <drunk/poisoned> when they died — the pair must NOT contain the
  Demon.`)
- Confirm chip: `Shown: <A> + <B>` then `Done — Sage back to sleep.`

### Data changes

- `characters.json:893-904`: add `"reminders": ["Woke"]` (or "Used").
- `night_guide.json:529-534`: add the two show cards; add the Leviathan/Riot
  clause to the instructions.
- No night-order change — index 61 is already correct.

## Tests to add

1. **No wake when alive.** Given a night-3 state where the Sage is alive; Then
   `NightOrder.otherNight` contains no `sage` step.
2. **Wake on a Demon kill.** Given the Imp kills the Sage on night 3 via
   `kill(state, sageId, DeathCause.DEMON, killerPlayerId = impId)`; Then the
   `sage` step is emitted, and `InfoCalc.compute(data, state, "sage", sageId)`
   headlines the killing Demon's name (not merely "a Demon").
3. **No wake on execution.** Given the Sage was executed on day 2 and no Leviathan
   is in play; When night 3 is built; Then no `sage` step is emitted.
4. **Leviathan jinx.** Given `leviathan` in play and the Sage executed on day 2;
   Then the `sage` step **is** emitted on night 3.
5. **Riot jinx.** Given `riot` in play and the Sage killed by Riot on day 2; Then
   the `sage` step is emitted that night.
6. **Pit-Hag arbitrary death does not trigger.** Given the Sage killed at night
   with `killerCharacterId = "pithag"`; Then no `sage` step is emitted (asserting
   the wiki's Example 3).
7. **Impaired at death yields the false-pair mode.** Given the Sage carries a
   `("sweetheart","Drunk")` reminder when the Demon kills them; Then the death
   record has `abilityImpairedAtDeath == true` and the Sage `InfoResult` caveats
   contain "must NOT contain the Demon"; and validating a pair that *does*
   contain the Demon returns an error.
8. **Vortox.** Given a living Vortox and a Demon-killed Sage; Then the result
   caveats contain the Vortox note and the false-pair mode is on.
9. **Resolved once.** Given the Sage woke on night 3; When night 4 is built; Then
   no `sage` step is emitted.
10. **Resurrection re-arms.** Given the Sage woke on night 3 and the Professor
    resurrects them on night 4; When the Demon kills them again on night 5; Then
    the `sage` step is emitted again.
11. **No "usually skip" for the Sage.** Given a Demon-killed Sage; Then the night
    step carries a flag (`actsWhileDead = true`) so the UI suppresses the
    "All holders are dead" warning.
