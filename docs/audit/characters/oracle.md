# Oracle (oracle) — Sects & Violets Townsfolk

## Official rules (sources)

Sources: <https://wiki.bloodontheclocktower.com/Oracle> (fetched 2026-08-25),
<https://wiki.bloodontheclocktower.com/Vortox>.

Current ability text:

> "Each night*, you learn how many dead players are evil."

**How to Run (verbatim):**

> "Each night except the first, wake the Oracle. Show fingers (0, 1, 2, etc.) equaling the
> number of dead evil players. Then, put the Oracle to sleep."

**Examples (verbatim):**

> "During the first day, the Flowergirl is executed. That night, the Demon kills the
> Juggler. The Oracle wakes and learns a "0," because all dead players are good.
>
> Halfway through the game, seven players are dead. Five of them are good and two of them
> are evil. During the day, an evil Traveller is Exiled. That night, the Demon kills one of
> it's Minions. The Oracle wakes and learns a "4," because four dead players are evil."

**Storyteller-relevant timing / edge cases distilled from the above**

- **Each night except the first**, and only while the Oracle is **alive** — a dead Oracle
  does not wake, so they never count themselves.
- **Dead evil Travellers DO count** (example 2: an exiled evil Traveller is one of the
  four). Alignment, not team, is what matters: the count is "dead players who are **evil**",
  which includes evil-aligned good-team characters (a Bounty Hunter's evil Townsfolk, a
  Mezepheles turn, an evil Cult Leader) and excludes a Demon whose alignment was flipped
  good (Snake Charmer swap).
- The count is over the **current** state of the dead: whatever a dead player's alignment is
  *now* is what counts. A player killed as good and later made evil counts as evil.
- **Resurrected players are not dead** and drop out of the count (Professor, Shabaloth
  regurgitation, Bone Collector).
- **Misregistration** (inference from the Spy/Recluse texts — the Oracle page is silent): a
  dead **Recluse** may register as evil (+1); a dead **Spy** may register as good (−1).
  Storyteller's choice.
- **Vortox**: the number **must** be false ("Even if they are drunk or poisoned, it must be
  false"). The Oracle's Tips note the resulting pattern-detection value.
- **Drunk / poisoned Oracle**: the storyteller *may* give a false number.
- **Jinxes: none.**

## What the app does today

| path | what it holds |
|---|---|
| `engine/src/main/resources/botc/data/characters.json:866-877` | Ability text matches. `otherNightReminder`: "Show the hand signal for the number (0, 1, 2, etc.) of dead evil players." `firstNightReminder` empty, `reminders: []`. Correct. |
| `engine/src/main/resources/botc/data/night_and_jinxes.json:453` | Other-night order index 80, after `duchess` and before `seamstress`. Absent from `firstNight`. Correct. |
| `engine/src/main/resources/botc/data/night_guide.json:492-504` | Prose + one `message` show card ("Dead evil players"). "If the Oracle is drunk or poisoned, or the Vortox is in play, give a false number." |
| `engine/src/main/kotlin/com/clocktower/engine/InfoCalc.kt:30` | `oracle` in `supports()`. |
| `engine/src/main/kotlin/com/clocktower/engine/InfoCalc.kt:271-279` | The calculation. |
| `engine/src/main/kotlin/com/clocktower/engine/GameState.kt:49-52` | `Player.isEvil(lookup) = team.isEvil != alignmentFlipped` — the alignment model the count depends on. |
| `app/src/main/java/com/clocktower/grimoire/ui/screens/NightScreen.kt:886-930` | Number card + false-number chips `0..4`. |
| `engine/src/test/kotlin/com/clocktower/engine/FullGamePlaytestTest.kt:946-950, 1010, 1050` | Playtest exercises the Oracle with a poisoned holder ("2 dead" true, shown 1/3/2). |

The calculation (`InfoCalc.kt:271-279`):

```kotlin
val dead = ctx.players.filter { !it.alive }
val evilDead = dead.filter { ctx.isEvil(it) }
return InfoResult(
    headline = "${evilDead.size} dead player${if (evilDead.size == 1) " is" else "s are"} evil",
    detail = if (dead.isEmpty()) "No one is dead" else dead.joinToString { "${ctx.name(it)} (…)" },
    caveats = misregistrations(ctx, dead),
)
```

**What already works — one line each:**

- Counts **all** seats including Travellers, so an exiled evil Traveller is counted —
  matching wiki example 2 (provided the storyteller flipped that Traveller's alignment; see
  defect 2).
- Uses alignment (`ctx.isEvil` → `team.isEvil != alignmentFlipped`), not team, so flipped
  players count correctly.
- Reads live state, so a dead player whose character/alignment changed later is counted as
  they are now — the correct rule.
- Resurrected players (`GameActions.resurrect`, `GameActions.kt:173-181`) become
  `alive = true` and correctly drop out of the count.
- Lists every dead player with their alignment in `detail`, which is genuinely useful for
  double-checking.
- Spy/Recluse **among the dead** produce a caveat (`misregistrations(ctx, dead)`).
- Night order (other nights only) and "each night\*" placement are correct.

**Storyteller's experience today:** expand the Oracle row, read "2 dead players are evil"
with the full dead list, tap "Show 2 full-screen". If a Recluse or Spy is dead you get a
line of prose but no alternative number. If the Oracle is poisoned or a Vortox is out you
get red text and chips 0–4 — which late in a 15-player game may not even contain a
plausible lie.

## Defects and gaps

1. **P1 · Evil Travellers are silently counted as good.**
   `Team.TRAVELLER.isEvil == false` (`Character.kt:16`), so an evil Traveller only counts if
   the storyteller manually used "Flip alignment" (`SeatSheet.kt:315`,
   `GameActions.kt:129-130`). Nothing prompts for a Traveller's alignment when the seat is
   created (`GameActions.addSeat`, `GameActions.kt:19-26`; `GameShell.kt:663-684`), and
   `validateSetupState` (`GameActions.kt:503-561`) does not check it. **Repro:** add a
   Traveller, assign e.g. `scapegoat`, exile them; the Oracle's count does not move, and
   the same silent error corrupts the Chef, Empath, Seamstress, Village Idiot, Shugenja and
   Town Crier. Wiki example 2 is unreproducible without a manual flip.

2. **P1 · Misregistration is prose, never a number.**
   `misregistrations(ctx, dead)` (`InfoCalc.kt:277`) emits "Priya is the Recluse — may
   register as evil / a Minion or Demon." The storyteller then does the arithmetic. The
   engine can produce "2 (true) · 3 if the dead Recluse registers evil · 1 if the dead Spy
   registers good" and offer each as a one-tap card.

3. **P1 · The false-number chips are `0..4`, fixed.**
   `NightScreen.kt:914-921`. In a 13–15 player game the true answer can be 5+, and every
   offered "lie" above the number of corpses is an obvious tell (you cannot have 4 dead
   evil players when only 3 people are dead). The plausible range is `0..deadCount`.

4. **P1 · "Must lie" and "may lie" are indistinguishable.**
   `commonCaveats` (`InfoCalc.kt:158-166`) appends the Vortox line beside impairment lines
   and `NightScreen.kt:903-906` collapses both into one boolean, leaving the truthful
   "Show 2 full-screen" chip as the first and most prominent control even under a Vortox.

5. **P2 · A dead Oracle still gets a step every night.**
   "Each night\*" plus alive-only. The row appears with the generic "All holders are dead —
   usually skip" note (`NightScreen.kt:751-757`) and still blocks the dawn checklist guard
   (`GameShell.kt:147-161`) until ticked.

6. **P2 · The Zombuul's "registers as dead while alive" is unmodelled.**
   `StatusEffects.deathNotes` (`StatusEffects.kt:119-121`) warns "Zombuul: the first time it
   dies, it lives but registers as dead", but there is no state for it. A Zombuul in that
   condition should count toward the Oracle's number while `alive` remains true, and the
   app has no way to express that. (Cross-edition: BMR Zombuul on a mixed script.)

7. **P2 · No range/plausibility guidance.**
   Nothing states that the answer is bounded by the number of dead players, nor shows that
   bound, which is the one thing a storyteller needs when inventing a lie.

8. **P2 · The Vortox caveat is gated on the holder's team.**
   `InfoCalc.kt:160-163` gates the Vortox note on `holderTeam == TOWNSFOLK || null`. A Drunk
   who believes they are the Oracle reaches this step via `nightRoleId`
   (`GameState.kt:39-44`) but has `characterId == "drunk"` (OUTSIDER), so the Vortox line is
   suppressed. Harmless in isolation (they are impaired anyway) but the gate should key on
   the *step's* character, not the holder's.

9. **P3 · `detail` grows unbounded.**
   Late game the dead list is 10+ names on one line
   (`InfoCalc.kt:276`) on a phone screen. Group by alignment and show counts.

10. **P3 · Headline grammar.** "0 dead players are evil" reads oddly as a headline;
    "0 dead players are evil (3 dead, all good)" would be clearer.

## Proposed behaviour (spec)

- **when:** `other` nights only (never first).
- **wake condition:** holder is **alive**. If dead, emit the step collapsed with
  "Oracle is dead — skip" and auto-tick it so the dawn guard does not list it.
- **targets:** none.
- **immediate effects:** none — no tokens.
- **deferred effects:** none.
- **expiry:** nothing to expire.
- **information (structured):**

  ```
  dead        = players where !alive          (Travellers included; resurrected excluded)
  evilDead    = dead where isEvil(p)          (team.isEvil XOR alignmentFlipped)
  answer      = Answer.Count(evilDead.size, min = 0, max = dead.size)
  ```
  `detail`: "**3 dead** — evil: Bo (Vigormortis), Ari (Witch) · good: Hana (Chef)".

- **misregistration handling (numeric, not prose):** for each dead **Recluse**, offer
  `answer + 1` labelled "if the dead Recluse (Priya) registers as evil"; for each dead
  **Spy**, offer `answer - 1` labelled "if the dead Spy (Ari) registers as good". Combine
  when several apply, presenting the resulting range: "true 2 · plausible 1–3".
- **impaired / false alternative:** via `InfoCalc.obligation` (see `artist.md`).
  - `MUST_LIE` (alive Vortox, regardless of impairment): the true chip is demoted behind a
    text button; the false chips are the primary row, drawn from `0..dead.size` minus the
    truth, sorted nearest-first.
  - `MAY_LIE`: both rows, truth first.
  - Never offer a number greater than `dead.size`.
- **visibility:** nothing shown to Demon/Minions/Lunatic.
- **day-time inputs:** none. But the setup/seat flow must acquire a Traveller's alignment
  (see the cross-cutting fix below), because the Oracle silently depends on it.
- **interactions/jinxes to handle explicitly:** none jinxed. Explicitly handle: Travellers
  count; alignment flips count; resurrection removes from the count; dead Recluse/Spy give
  numeric alternatives; Vortox mandatory lie; a Zombuul "registering as dead" should count
  while alive (needs a `zombuul:"Registers dead"` state — flag as a Zombuul-owner decision,
  the Oracle side just needs to read it).

### UI text the step should display

> **Oracle — show the number of dead players who are evil.**
>
> **2** of 5 dead are evil
> Evil: Bo (Vigormortis), Ari (Witch) · Good: Hana, Kai, Marta
> `[ Show 2 full-screen ]`
> `! Priya (Recluse) is dead — she may register as evil. [ Show 3 instead ]`

Under a Vortox:
> **VORTOX — this number MUST be false.** Plausible lies (5 players are dead):
> `[ 1 ] [ 3 ] [ 0 ] [ 4 ] [ 5 ]`  ·  *show the true number 2 anyway*

### Data changes

- `night_guide.json:492-504` — split the obligation: "If the Vortox is in play the number
  **must** be false; if the Oracle is drunk or poisoned you **may** give a false number.
  Dead Travellers count if they are evil. Never show a number larger than the number of
  dead players."
- `characters.json:866-877` — no change.
- `night_and_jinxes.json` — no change.

### Cross-cutting fix this character requires

Traveller alignment must be an explicit, prompted setup step, not an easily-missed "Flip
alignment" button:

- `GameActions.assignCharacter` should take (or `addSeat` should prompt for) the
  Traveller's alignment when `isTraveller = true`.
- `validateSetupState` (`GameActions.kt:503-561`) should raise an issue for any Traveller
  seat whose alignment has never been set explicitly (add a
  `alignmentChosen: Boolean` flag, or a `traveller:"Evil"` / `traveller:"Good"` marker).
- The same fix repairs Chef, Empath, Seamstress, Shugenja, Village Idiot, Town Crier and
  the Clockmaker's Minion exclusion.

## Tests to add

1. **All dead are good → 0 (wiki example 1).**
   *Given* an executed Flowergirl and a Demon-killed Juggler, *then* the Oracle answer on
   the following night is 0. (Currently passes — lock it in.)

2. **Exiled evil Traveller counts (wiki example 2, currently fails without a manual flip).**
   *Given* five dead good players, two dead evil players, a dead Traveller assigned a
   Traveller character **and marked evil at seat creation**, and a dead Minion,
   *then* the answer is 4.

3. **Traveller alignment must be set.**
   *Given* a Traveller seat whose alignment was never chosen,
   *then* `validateSetupState` reports an issue naming that seat.

4. **Alignment flip on a good-team character counts.**
   *Given* a dead Townsfolk with `alignmentFlipped = true`, *then* they are counted evil.
   *And*: a dead Demon with `alignmentFlipped = true` is counted good.

5. **Resurrection removes from the count.**
   *Given* a dead evil player, *when* `GameActions.resurrect` is applied,
   *then* the answer decreases by 1 and the death record remains in the log.

6. **`revive` (mistake undo) also removes them.**
   *Given* a dead evil player, *when* `GameActions.revive` is applied,
   *then* the answer decreases by 1 and the death record is dropped.

7. **Dead Recluse offers +1 (currently fails).**
   *Given* a dead `recluse` and a true count of 2, *then* an alternative answer of 3 is
   offered, attributed to the Recluse.

8. **Dead Spy offers −1 (currently fails).**
   *Given* a dead `spy` counted as evil and a true count of 2, *then* an alternative answer
   of 1 is offered, attributed to the Spy.

9. **False numbers are bounded by the corpse count (currently fails).**
   *Given* 3 dead players and a poisoned Oracle, *then* the generated false-number set is a
   subset of `{0,1,2,3}` minus the truth.

10. **Vortox forces a lie (currently fails).**
    *Given* an alive Vortox, *then* `obligation == MUST_LIE` and the true value is not the
    primary suggestion; *given* the Vortox dead, *then* `TRUTH`.

11. **Vortox outranks poison.**
    *Given* both an alive Vortox and a poisoned Oracle, *then* `obligation == MUST_LIE`.

12. **Dead Oracle does not wake (currently fails).**
    *Given* the Oracle died on day 2, *when* the night-3 sheet is built, *then* the Oracle
    step is marked skipped and does not block the dawn checklist.

13. **Night 1 never shows the step.**
    *Given* an Oracle in play, *when* `firstNight` is built, *then* no `oracle` step exists.
    (Currently passes — lock it in.)
