# Vigormortis (vigormortis) — Sects & Violets Demon

## Official rules (sources)

Sources:
- https://wiki.bloodontheclocktower.com/Vigormortis (Character Text, Summary, How to Run, Examples, Tips, Jinx)
- https://wiki.bloodontheclocktower.com/Mastermind (jinx text)
- https://wiki.bloodontheclocktower.com/Night_Order

Current ability text (wiki, verbatim):

> "Each night*, choose a player: they die. Minions you kill keep their ability & poison 1 Townsfolk neighbor. [-1 Outsider]"

`characters.json:1100` carries exactly this string (British "neighbour") — **no drift**.

How to Run (wiki, verbatim):

> "While setting up the game, before putting the character tokens in the bag, remove one Outsider character token and add one Townsfolk character token. (*If there are no Outsider tokens to remove, do not add a Townsfolk token.*)
>
> Each night except the first, wake the Vigormortis. They point at any player. Put the Vigormortis to sleep. If the chosen player isn't a Minion, that player **dies** - mark them with a **DEAD** reminder.
>
> If the chosen player is a Minion, that player **dies** - mark them with a **DEAD** reminder and a **HAS ABILITY** reminder. The closest clockwise or closest counterclockwise Townsfolk to the Minion becomes **poisoned** - mark them with a **POISONED** reminder."

Clarifications that matter:

- **Reminder tokens: DEAD, HAS ABILITY, POISONED — and they accumulate.** Every Minion the Vigormortis kills gets its own HAS ABILITY token and produces its own POISONED token. A Vigormortis with three Minions can end the game with three HAS ABILITY and three POISONED tokens on the board simultaneously ("All dead Minions keep poisoning active simultaneously").
- **Which neighbour is the Storyteller's choice**: "The closest clockwise **or** closest counterclockwise Townsfolk to the Minion" — the ST picks the direction. The scan skips Outsiders, Minions, Demons and Travellers, exactly like the No Dashii.
- **Dead Minions keep acting.** They wake **at their normal position in the night order** from the next night onward — Witch, Cerenovus, Pit-Hag, Poisoner, Fearmonger, Devil's Advocate, Evil Twin (passive), Mezepheles, Harpy etc. all continue. Note that in the standard other-night order nearly every Minion acts **before** the Demon, so the effect starts the night *after* the kill.
- **Only Minions the Vigormortis kills.** A Minion executed, Slayer-shot, Gossip-killed, Godfather-killed or Storyteller-killed does **not** keep their ability and does **not** poison anyone.
- **If the Vigormortis dies or loses their ability**: "*those players become healthy again*" — every Vigormortis POISONED ends, and the dead Minions lose their abilities (the HAS ABILITY tokens come off).
- **Character transformation**: a dead Minion turned into a non-Minion character (Pit-Hag, Philosopher…) stops poisoning and loses the ability. A dead Minion who is themselves drunk or poisoned loses their ability until sober and healthy.
- **Once-per-game Minion abilities** that were already spent stay spent; unspent ones can still be used while dead.
- **Setup**: `[-1 Outsider]` — remove one Outsider, add one Townsfolk. If there are 0 Outsiders in the distribution, do nothing.
- **Jinx — Mastermind** (from the Mastermind wiki page, verbatim): *"A Mastermind that has their ability keeps it if the Vigormortis dies."* This is the **only** Vigormortis jinx.

## What the app does today

Data:
- `characters.json:1096-1109` — team demon, `setup: true`, `otherNightReminder: "The Vigormortis points to a player. That player dies. If a Minion, they keep their ability and one of their Townsfolk neighbours is poisoned."`, reminders `["Dead","Has ability","Poisoned"]` — **one copy each** (see defect 3).
- `night_and_jinxes.json:419` — `vigormortis` in the other-night order after `lordoftyphon`. **Correct** per the official order. No first-night entry. **Correct.**
- `night_and_jinxes.json` — **no Vigormortis jinx entry at all**; the official Mastermind jinx is missing.
- `night_guide.json:745-750` — the prose is essentially correct and even says *"wake them at their usual point each night from now on"*, so the run-book knows the rule the app does not implement.
- `Setup.kt:121` handles `[-1 Outsider]`; `SetupTest.kt:47,57,87` and `GameActionsTest.kt:258,269` cover the modifier including the "0 Outsiders → don't go negative" case. **Works.**

Runtime — **there is no automation at all**; every consequence is a text note or nothing:
- Night step: falls into the generic `else -> DemonKillPanel` branch (`NightScreen.kt:518-523`). The panel asks "who did `<name>` choose?" and offers `<name> dies` / `No kill`.
- `StatusEffects.deathNotes` (`engine/.../StatusEffects.kt:113-115`) adds one line when the dying player is a Minion and a living Vigormortis exists: *"Vigormortis kill: the Minion keeps their ability and one Townsfolk neighbour is poisoned."* — a **text warning only**. Nothing is placed, nothing is tracked.
- `HAS ABILITY` / `POISONED` tokens must be placed by hand through the `NightToolTray` (`NightScreen.kt:305-345`), which calls `placeExclusiveReminder` when `character.allReminders.count { it == label } <= 1` — i.e. **for the Vigormortis, always** (defect 3).
- Night order for dead Minions: `NightOrder.build` (`NightOrder.kt:46-49`) groups **all** players regardless of `alive`, so a dead Vigormortis-killed Minion **does still get a night row**. But `NightStepRow` computes `allDead = holders.none { it.alive }` (`NightScreen.kt:702`) and prints, in the error colour, **"All holders are dead — usually skip."** (`NightScreen.kt:749-757`), plus the screen footer says *"Dead players usually don't act — skip them unless their ability says otherwise."* (`NightScreen.kt:161-166`).
- Nothing removes the tokens or the abilities when the Vigormortis dies. The repo's own playtest fixture records this as observed behaviour: `FullGamePlaytestTest.kt:973` — *"Night 3 began; dead Esme still had a Vigormortis 'Has ability' marker."*

Storyteller's experience: kill the Minion through the ordinary demon panel, read one red sentence, then remember for the rest of the game to (a) hand-place two tokens, (b) wake that dead Minion every night against the app's own advice to skip them, (c) pick and re-pick the poisoned neighbour when characters change, and (d) tear it all down if the Vigormortis dies.

## Defects and gaps

1. **P0 · The "Vigormortis kill" note fires on *any* Minion death, not on Vigormortis kills.**
   `StatusEffects.kt:113` tests only `character?.team == Team.MINION && seats.any { it.characterId == "vigormortis" && it.alive }`. `deathNotes` is rendered in `SeatSheet.kt:241-258` (the kill-by-cause sheet) and in `NightScreen.kt:588-590`. So **executing** a Minion during the day, or a Slayer shot, or a Gossip kill, tells the storyteller the Minion keeps their ability — which is flatly wrong ("Minions **you kill**").
   *Repro*: Vigormortis game, day 2, execute the Witch → open her seat → the note appears.

2. **P0 · Dead, ability-retaining Minions are labelled "All holders are dead — usually skip."**
   `NightScreen.kt:749-757`. The one night sheet in the game where a dead Minion **must** wake, the app tells the storyteller in red to skip them, and the footer repeats it. Nothing reads the `Has ability` token.
   *Repro*: kill the Witch with the Vigormortis on night 2; place `Has ability` by hand; night 3 → the Witch row is greyed with the red "usually skip" warning.

3. **P0 · Only one `Has ability` and one `Poisoned` token can exist — the second Minion kill silently erases the first.**
   `Character.allReminders` (`Character.kt:62`) returns `["Dead","Has ability","Poisoned"]`; the tray computes `availableCopies = allReminders.count { it == label } == 1` and therefore calls `GameActions.placeExclusiveReminder` (`NightScreen.kt:319-324`, `GameActions.kt:194-201`), which **removes the token from every other seat first**. Killing a second Minion moves the first Minion's `Has ability` marker onto the new one and moves the first poisoned Townsfolk's `Poisoned` marker away. The rules require them to accumulate.
   *Repro*: Vigormortis kills the Witch (night 2, place both tokens), then the Cerenovus (night 3, place both tokens) → the Witch's `Has ability` and the first poisoned Townsfolk's `Poisoned` are gone.

4. **P1 · Nothing happens automatically when a Minion is killed by the Vigormortis.**
   The correct resolution is a five-step sequence (kill; place `Has ability`; compute the two candidate Townsfolk neighbours; let the ST pick one; place `Poisoned`). Today it is one generic "dies" button plus a sentence. Compare `snakecharmer`/`fanggu`/`professor`, which do have resolvers (`NightScreen.kt:471-517`).

5. **P1 · The poisoned neighbour is not computed — the ST must count seats by hand.**
   `StatusEffects.derivedPoison` already implements exactly this scan for the No Dashii (`StatusEffects.kt:14-33`) but is hard-coded to `characterId == "nodashii"`. The Vigormortis case is the same algorithm anchored on the dead Minion's seat, with a Storyteller direction choice.

6. **P1 · The poison is a static token, so it never follows a character change.**
   Rules: a poisoned player who stops being a Townsfolk, or a dead Minion who stops being a Minion, ends the poison / the ability. A hand-placed `("vigormortis","Poisoned")` token stays where it is forever.

7. **P1 · When the Vigormortis dies, nothing is cleaned up.**
   No expiry entry exists for `("vigormortis","Has ability")` or `("vigormortis","Poisoned")` in `EXPIRES_AT_DAWN`/`EXPIRES_AT_DUSK` (`GameActions.kt:218-242`) — correctly, since they are not phase-scoped — but there is also no conditional teardown on the Vigormortis's death. The dead Minions keep waking and the Townsfolk keep being poisoned. Documented in `FullGamePlaytestTest.kt:973`.

8. **P1 · The Vigormortis's own night step doesn't show its standing state.**
   It should list: which dead Minions currently have abilities, which Townsfolk each is poisoning, and which Minions are still alive (and therefore worth killing). Today the ST reads it off the circle.

9. **P1 · No day-start or dawn briefing.**
   The morning after a Vigormortis kill the ST must announce the Minion's death like any other, and must remember from then on that a dead player is still acting. Nothing surfaces it.

10. **P2 · The Mastermind jinx is missing from the data.**
    Official: *"A Mastermind that has their ability keeps it if the Vigormortis dies."* No entry exists in `night_and_jinxes.json`, so the "Jinxes in play" dialog (`GameExtras.kt` → `ActiveJinxesDialog`) shows nothing for a Vigormortis + Mastermind script.

11. **P2 · Impaired dead Minions are not handled.**
    A dead Minion with an ability who is *also* drunk/poisoned (Snake Charmer's leftover, a Fabled, a second Vigormortis-poisoned scenario) loses the ability until healthy. `isImpaired` would catch the tokens, but nothing joins that to "should this dead Minion wake tonight?".

12. **P2 · No record of *which* Vigormortis killed / which Minion has ability, beyond a token label.**
    Because the flag is a plain `PlacedReminder("vigormortis","Has ability")` with no link to the death record, undo/redo and manual token edits can desynchronise it from `state.deaths`.

13. **P3 · The night-order rows for *all* dead characters are shown, not just ability-retaining ones.**
    `NightOrder.kt:46-49` has no alive filter. This is what accidentally makes Vigormortis work at all, but in every other game it produces dead-character noise on the night sheet (a dead Poisoner, a dead Fortune Teller…). The fix should be principled: filter on "does this seat still act tonight?" rather than on `alive`.

## Proposed behaviour (spec)

### Night action

- **when**: other nights only. Wake condition: the seat holding `vigormortis` is **alive**.
- **targets**: 1, any player (self, Travellers allowed; dead disabled).
- **immediate effects** — one resolver, branching on the target's team:

  1. Run `StatusEffects.deathNotes(target)`; if a protection applies, require an explicit "Protected — no death" confirm. If the Vigormortis `isImpaired`, default to "attack fails".
  2. Target is **not** a Minion → `kill(target, DeathCause.DEMON)`. Done.
  3. Target **is** a Minion →
     - `kill(target, DeathCause.DEMON)`;
     - add (not `placeExclusive`) `PlacedReminder("vigormortis", "Has ability")` to that seat;
     - compute the two candidate poison targets: from the **Minion's** seat, scan `-1` and `+1` and stop at the first seat whose team is `TOWNSFOLK` (skip Outsider/Minion/Demon/Traveller; **do not** skip the dead, matching the No Dashii convention — flag: the wiki does not state this explicitly for the Vigormortis, so make it an ST-visible choice with both candidates shown);
     - present both candidates as chips with their direction and character, ST taps one;
     - add `PlacedReminder("vigormortis", "Poisoned")` to the chosen seat, **linked** to the Minion (see data changes).

- **deferred effects**:
  - **From the next night on**, the dead Minion wakes at its normal night-order position. The night sheet must show that row as **active**, styled positively: **"DEAD but still acting (Vigormortis) — WAKE THEM."** Suppress the "All holders are dead — usually skip" line whenever a holder carries `Has ability`.
  - **On the Vigormortis's death** (any cause): raise a mandatory prompt — **"The Vigormortis is dead. `<n>` dead Minions lose their abilities and `<n>` players become healthy. Remove the markers?"** with a one-tap "Do it" that removes every `("vigormortis","Has ability")` and `("vigormortis","Poisoned")`. Exception: a **Mastermind** with `Has ability` keeps it (jinx) — offer to keep that one.
  - **On character change**: if a `Has ability` holder stops being a Minion, or a `Poisoned` holder stops being a Townsfolk, prompt to remove/re-target that pair.
- **expiry**: `Has ability` and `Poisoned` from `vigormortis` **never** expire at dawn or dusk. They expire only on the conditions above. Do **not** add them to `EXPIRES_AT_DAWN`/`EXPIRES_AT_DUSK`.
- **information**: none given by the Vigormortis. The poisoned Townsfolk get the standard "give false info" caveat via `InfoCalc.impairments` (already works for reminder-based poison, `InfoCalc.kt:142-148`).
- **visibility**: the killed Minion is told nothing extra by the rules (they simply keep waking). Recommended ST convenience card, not a rules requirement: none. The other Minions and the Demon learn nothing.
- **day-time inputs**: none.
- **interactions/jinxes to handle explicitly**:
  - **Mastermind**: *"A Mastermind that has their ability keeps it if the Vigormortis dies."* Add the jinx to `night_and_jinxes.json` and special-case the teardown.
  - **Scarlet Woman**: if she is Vigormortis-killed she keeps her ability, so she can still become the Demon when the Vigormortis dies — **while dead**. Surface this on the Vigormortis's death prompt.
  - **Witch / Cerenovus / Pit-Hag / Fearmonger / Devil's Advocate / Mezepheles / Harpy**: all keep acting; their steps must not be greyed out.
  - **Poisoner** killed by the Vigormortis: keeps poisoning nightly from the grave.
  - **Minstrel**: only triggers on a Minion *execution*, not on this kill — make sure `deathNotes` distinguishes cause (it currently does not, `StatusEffects.kt:110-111`).
  - **Godfather**: `deathNotes` fires on Outsider deaths (`StatusEffects.kt:116-118`) — unrelated but shares the same cause-blindness bug.
  - **Drunk/poisoned dead Minion**: no ability until healthy.

### UI text the step should display

- Header: **"Vigormortis — who did `<name>` choose?"**
- Standing state, always visible on the step:
  **"Dead Minions still acting: `<Witch (Cleo)>` poisoning `<Beau>`, `<Cerenovus (Dov)>` poisoning `<Esme>`. Living Minions left: `<…>`."**
- On selecting a Minion: **"`<name>` is a Minion — they die but KEEP their ability and wake every night from now on. Choose which Townsfolk neighbour they poison:"** with two direction chips.
- On the Minion's own night row, every later night: **"Dead — but has the Vigormortis's 'Has ability' marker. WAKE THEM."**
- On the Vigormortis's death: **"Vigormortis dead — `<n>` dead Minions lose their abilities, `<n>` players become healthy."**

### Data changes

- `night_and_jinxes.json` — add:
  `{ "id1": "vigormortis", "id2": "mastermind", "reason": "A Mastermind that has their ability keeps it if the Vigormortis dies." }`
- `characters.json:1105-1107` — the reminder list should express multiplicity. Either duplicate the labels (`"Has ability","Has ability","Has ability","Poisoned","Poisoned","Poisoned"`) so the existing `availableCopies` logic in `NightScreen.kt:319-324` stops treating them as exclusive, **or** (preferred) mark these tokens non-exclusive in code and leave the data alone.
- `night_guide.json:747` — add the teardown rule (*"If the Vigormortis ever dies or loses their ability, remove every 'Has ability' and 'Poisoned' marker — except a Mastermind's, by jinx"*) and the "only Minions the Vigormortis itself kills" restriction.
- `StatusEffects.deathNotes` must take the death **cause** (or the caller must), so the Minion note fires only for `DeathCause.DEMON` from a Vigormortis attack.
- `PlacedReminder` would benefit from an optional `linkedPlayerId: Long?` so a `Poisoned` token knows which dead Minion sourced it (needed for per-Minion teardown).

## Tests to add

1. **The "keeps their ability" note must not fire on an execution.**
   *Given* a living Vigormortis and a Witch, *when* the Witch is killed with `DeathCause.EXECUTION`, *then* `deathNotes` contains **no** "Vigormortis kill" line. Fails today (`StatusEffects.kt:113`).

2. **Two killed Minions keep two markers.**
   *Given* the Vigormortis kills the Witch on night 2 and the Cerenovus on night 3, *then* both seats hold `("vigormortis","Has ability")` and two distinct seats hold `("vigormortis","Poisoned")`. Fails today (exclusive placement erases the first).

3. **Poison neighbour computation.**
   *Given* seats `[vigormortis, witch, mutant(Outsider), clockmaker, evil twin, sage]` and the Witch is killed, *then* the candidate set is exactly `{sage (anticlockwise, skipping nothing), clockmaker (clockwise, skipping the Mutant)}`. No such function exists today.

4. **Dead Minion still appears as an active night step.**
   *Given* a Witch with `Has ability` who is dead, *when* the other-night sheet is built, *then* the Witch step exists **and** is not flagged "usually skip". The step exists today; the flag is wrong today.

5. **Vigormortis death tears everything down.**
   *Given* two `Has ability` Minions and two `Poisoned` Townsfolk, *when* the Vigormortis dies, *then* all four tokens are removable in one action and `isImpaired` returns false for both Townsfolk. No such action today.

6. **Mastermind jinx survives the teardown.**
   *Given* a Vigormortis-killed Mastermind with `Has ability`, *when* the Vigormortis dies, *then* the Mastermind keeps `Has ability`. No jinx data today.

7. **Killed Minion who stops being a Minion loses the ability and the poison.**
   *Given* a Pit-Hag turns the dead Witch into the Klutz, *then* the `Has ability` and its linked `Poisoned` are flagged for removal.

8. **A Minion killed by something else keeps nothing.**
   *Given* a Slayer shoots the Baron in a Vigormortis game, *then* no `Has ability` is suggested and no poison is offered.

9. **Setup.** *Given* an 11-player Vigormortis game (base 7/2/1/1), *then* the required distribution is 8/1/1/1; *given* a 10-player game (base 7/0/2/1), *then* the distribution is unchanged. Both already covered by `SetupTest.kt:47,87` / `GameActionsTest.kt:258` — **passes today**.

10. **Poisoned Townsfolk gets false info.**
    *Given* the Clockmaker holds `("vigormortis","Poisoned")`, *then* `InfoCalc.compute(..., "clockmaker", ...)` carries a "POISONED" caveat and the false-info chips are offered. **Passes today** — assert it so the token rework does not regress it.
