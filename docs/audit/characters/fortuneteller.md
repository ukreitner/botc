# Fortune Teller (fortuneteller) — Trouble Brewing Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Fortune_Teller>

Current ability text (matches `characters.json`):

> "Each night, choose 2 players: you learn if either is a Demon. There is a good player that registers as a Demon to you."

How to run (wiki):

- **First-night setup:** *"put the Fortune Teller's RED HERRING reminder token by any good character token, marking that player as the Red Herring."* This happens **while preparing the first night**, before the Fortune Teller wakes.
- **Each night (including the first):** *"The Fortune Teller points at any two players. If either chosen player is a Demon or the Red Herring, nod your head yes. Otherwise, shake your head no."*

Red herring lifecycle (wiki, verbatim):

- Chosen during initial setup and *"is the same player throughout the entire game"*.
- *"This player may be any good player, even the Fortune Teller themself."*
- The Fortune Teller does not know who the red herring is.
- *"Cannot change during gameplay."* It also does not stop working when the red herring dies — the token is never removed.

Targeting:

- *"The Fortune Teller may choose any two players—alive or dead"* and *"or even themself"*.

Misregistration:

- *"Be aware of the Recluse, who may register as the Demon to you. This is not the same as the Red Herring."* So a Recluse pick can legitimately be a **YES**, and it is the storyteller's per-instance choice (<https://wiki.bloodontheclocktower.com/Recluse>: *"Each time the Recluse is targeted by an ability that detects or affects evil characters, choose which character and alignment the Recluse registers as."*).
- The **Spy** registers *"as good & as a Townsfolk or Outsider"* (<https://wiki.bloodontheclocktower.com/Spy>) — it can **never** register as a Demon, so a Spy pick is never a YES on the Spy's account.

Jinxes: none for the Fortune Teller in the official jinx list (and none in the app's data). Correct.

## What the app does today

Data:

- `characters.json` `fortuneteller` — ability text current; identical first/other night reminders; `reminders: ["Red herring"]`.
- `night_and_jinxes.json` — `firstNight` index 51 (after Chef 49, Empath 50, before Butler 52) and `otherNight` index 74 (after Empath 73, before Undertaker 75). Matches the official order. **Works.**
- `night_guide.json` `fortuneteller.first` / `.other` — accurate prose; `.other` correctly says dead players and self are legal targets, `.first` does not. `shows: []` in both.

Engine:

- `InfoCalc.kt:22-26` — `targetsNeeded("fortuneteller") == 2`. **Works.**
- `InfoCalc.kt:325-342` `fortuneTeller(ctx, targets)`:
  - `validTargets(ctx, targets, 2)` rejects fewer/duplicate/stale ids (`InfoCalc.kt:106-111`). **Works.**
  - `demonHit` = chosen whose `characterId` resolves to `Team.DEMON`;
  - `herringHit` = chosen carrying any reminder labelled `Red herring` (case-insensitive, source-agnostic);
  - headline `"YES"` / `"NO"`, detail listing the reasons;
  - caveats: `misregistrations(ctx, chosen)` plus, when no herring exists anywhere, *"No 'Red herring' reminder placed yet — assign one good player as the red herring."*
- `GameActions.kt:547-559` `validateSetupState` — if any resident holds `fortuneteller`, requires **exactly one** seat with a `fortuneteller`/`Red herring` reminder and requires that seat to be good. **Works.**
- `GameActions.kt:218-242` — `Red herring` is in **neither** expiry table, so it survives dawn and dusk forever. **Works.**
- `GameActions.assignCharacter` (`GameActions.kt:46-53`) keeps `reminders` on a character change, so the token survives a Pit-Hag/ST character swap. **Works.**

UI:

- `GameShell.kt:347-376` — a setup dialog **"Fortune Teller red herring"** fires when `state.phase == Phase.SETUP`, a `fortuneteller` seat exists, and no `Red herring` reminder is placed anywhere. It lists `state.players.filter { !it.isEvil(...) }` and places the token via `viewModel.addReminder`. Dismissing or tapping **"Later"** sets `herringPromptDone` and the dialog never returns.
- `NightScreen.kt:836-861` — the step panel shows a 2-player chip picker over `state.players` (alive and dead — correct) and then the computed result.
- `NightScreen.kt:886-901` — `isYes`/`isNo` are detected from the headline, so a one-tap full-screen **YES**/**NO** card is offered.
- `NightScreen.kt:903-930` — when a caveat contains `POISONED`/`DRUNK`/`IS the Drunk`/`VORTOX`/`No Dashii`, a "False info to show instead" row offers the inverted YES/NO. **Works.**

Storyteller's actual experience: at setup a dialog asks them to pick the red herring (good). Each night they open the Fortune Teller row, tap two seats, read **YES**/**NO** in gold, and can flash it full-screen. There is no record of which two seats the FT picked last night, no indication of who the red herring is on this screen, and no way to say "the Recluse registered as the Demon to you tonight" other than by ignoring the app's answer.

## Defects and gaps

1. **P0 · A chosen Recluse produces a flat "NO" with a "Show answer full-screen" button.**
   Rules: the Recluse *may register as the Demon* to the Fortune Teller, per instance, at the storyteller's choice. App: `InfoCalc.kt:328-330` tests only `team == Team.DEMON` and the red-herring token, so the headline is **NO** and `NightScreen.kt:896-901` offers a one-tap "Show answer full-screen" that shows **NO**. The only hint is a caveat line among possibly several. The storyteller who taps the obvious button gives info that may be the opposite of what they intended.
   Repro: 8-seat TB with a Recluse; FT picks the Recluse + a Townsfolk → headline **NO** → "Show answer full-screen" shows NO.

2. **P1 · The Spy caveat is noise on this step and can mislead in the other direction.**
   `misregistrations` (`InfoCalc.kt:121-130`) is generic: choosing the Spy prints *"…may register as good / a Townsfolk or Outsider"*. For the Fortune Teller that is irrelevant (the Spy can never be a Demon) and reads as "you have a choice here". The caveat set must be filtered by what the asking ability actually detects.

3. **P1 · No red-herring prompt when the Fortune Teller enters play after setup.**
   `GameShell.kt:350` requires `state.phase == Phase.SETUP`. Change a seat to the Fortune Teller from `SeatSheet.kt:310` ("Change character") mid-game — or run a script where one is created — and no herring is ever assigned. `validateSetupState` (`GameActions.kt:547`) is only consulted by the setup guard at `GameShell.kt:133-140`, which has already passed. The FT step then answers **NO** on every good player forever, with only a caveat line to say so.

4. **P1 · "Later" permanently silences the only prompt, and the setup guard is overridable.**
   `herringPromptDone` (`GameShell.kt:348`) is a one-way session flag with no re-entry point. The backstop is the setup guard, whose dialog offers **"Start the night anyway"** (`GameShell.kt:584-588`). A storyteller who taps both starts night 1 with no red herring at all.

5. **P1 · The red herring is invisible at the moment it matters.**
   The Fortune Teller step never states who the red herring is. `InfoCalc.fortuneTeller` only names them in `detail` when they happen to have been picked (`InfoCalc.kt:331-334`). On a phone the storyteller must leave the night sheet, open the Grimoire tab and hunt for the token.

6. **P1 · Nothing records what the Fortune Teller chose or was told.**
   `targets` lives in `rememberSaveable(step.id)` at `NightScreen.kt:839` — Compose state only. It is never written to `GameState`, so the log dialog (`GameExtras.kt:40-64`), the reveal flow and the next night's "did they already ask about Bob?" question are all unsupported. This is the single most valuable piece of bookkeeping for a Fortune Teller game.

7. **P2 · The target selection is not keyed to the night.**
   `rememberSaveable(step.id)` at `NightScreen.kt:839` keys only on the character id, while every other night-scoped piece of UI state keys on the cycle (`expandedId` and `pendingReminderLabel` at `NightScreen.kt:91,96`; `DemonKillPanel`'s `targetId` at `NightScreen.kt:540` uses `"$demonId-${state.cycle}"`). Last night's two seats can be restored on top of tonight's step, producing a confident YES/NO for a question nobody asked.

8. **P2 · The setup prompt's candidate list is not restricted to seats with characters.**
   `GameShell.kt:360` uses `!it.isEvil(viewModel::characterById)`, and a seat with `characterId == null` has `team == null` → `isEvil == false`. During setup that means unassigned seats are offered as red herrings. `validateSetupState` catches an evil herring at "Begin night", but the prompt should not offer the choice in the first place.

9. **P2 · The prompt uses `addReminder`, not `placeExclusiveReminder`.**
   `GameShell.kt:364-367`. Combined with a manual placement from the tray this can produce two `Red herring` tokens; the setup guard catches it (`herringSeats.size != 1`) but only at the phase boundary, and only during setup.

10. **P2 · The step is presented normally when the Fortune Teller is dead.**
    `NightOrder.kt:143-145` includes any character with holders. `impairments` adds *"X is dead — they normally don't act."* (`InfoCalc.kt:150`) but the row is still an unchecked step blocking the dawn guard (`GameShell.kt:153-160`).

11. **P2 · The first-night guide text omits the "dead players and themselves are allowed" clause.**
    `night_guide.json` `fortuneteller.first.instructions` has it only in `.other`. On night 1 nobody is dead, but self-selection is legal on night 1 too.

12. **P3 · Multi-holder steps compute for the first seat only.**
    `NightScreen.kt:837` uses `step.playerIds.firstOrNull()`. Two seats sharing a `nightRoleId` (Village Idiot, Philosopher, homebrew) get one answer. Not reachable in stock TB — `validateSetupState` prevents a Drunk-shown-as-Fortune-Teller alongside a real one — but the generic panel should loop over holders.

13. **P3 · No re-validation if the red herring's alignment later changes.**
    `flipAlignment` (`SeatSheet.kt:315`) or a character change can leave the `Red herring` token on an evil seat. The rules chose a good player at setup; a later flip is an edge case, but the app should at least warn.

## Proposed behaviour (spec)

### Red-herring lifecycle

- **Assignment trigger:** any transition into a state where a seat holds `fortuneteller` and **no** seat carries `fortuneteller/Red herring` — not just `Phase.SETUP`. Watch it on every state change, in setup and in game.
- **Prompt:** modal, title **"Fortune Teller red herring"**, body *"Pick the good player who registers as the Demon to the Fortune Teller. This never changes, and it may be the Fortune Teller themselves."*
- **Candidates:** seats with a non-null `characterId` whose `isEvil(lookup)` is false. The Fortune Teller's own seat is included and labelled **"(the Fortune Teller — legal and often good play)"**. Sort: Fortune Teller first, then seat order.
- **Placement:** `placeExclusiveReminder(seatId, PlacedReminder("fortuneteller", "Red herring"))`.
- **Deferral:** the "Later" button must set a *soft* flag that clears at the next phase advance, so the prompt returns before night 1 and again at every dusk while the state is illegal. Add a persistent **"Set red herring"** row to the Fortune Teller night step so there is always an in-context entry point.
- **Setup guard:** keep `validateSetupState`'s two issues. Add a third: *"Fortune Teller: the red herring is on an evil player"* when a token sits on an evil seat at any phase boundary, not just setup.
- **Expiry:** never. Survives death, resurrection, character change, and dawn/dusk. (Already true; add a regression test so nobody adds it to an expiry table.)

### Night action

- **when:** both first and other nights. Wake condition: the Fortune Teller seat is **alive**.
- **targets:** exactly 2, distinct. **All** seats are legal — alive, dead, Travellers, and the Fortune Teller themselves. Picker sorts alive first but must not disable dead seats; label the FT's own chip **"(self — legal)"**.
- **immediate effects:** none; no tokens are placed.
- **information:**

  Compute a structured answer instead of a bare string:

  ```
  FortuneTellerResult(
    trueAnswer: Boolean,               // Demon or red herring among the two
    reasons: List<String>,             // "Bob is the Imp", "Ana is the red herring"
    optionalYes: List<Misregistration> // Recluse picks that could make it YES
  )
  ```

  - `trueAnswer` = any chosen seat is `Team.DEMON` **or** carries `fortuneteller/Red herring`.
  - `optionalYes` contains one entry per chosen seat holding `recluse`: *"<name> is the Recluse — you may choose that they register as the Demon tonight, making this a YES."* Nothing else can turn a NO into a YES for this ability. Explicitly do **not** emit a Spy caveat here.
  - When `trueAnswer` is true because of a Demon, add *"(the red herring is <name>)"* as context so the storyteller keeps the picture.
- **what is shown:** the existing full-screen YES / NO cards. When `optionalYes` is non-empty and `trueAnswer` is false, the answer row must offer **both** buttons side by side: **"Show NO (true)"** and **"Show YES (Recluse registers as the Demon)"**, and record which was chosen.
- **impaired/false alternative:** when the FT is drunk, poisoned, the Drunk, Marionette, No-Dashii-adjacent, or a Vortox is in play, keep today's inverted-answer button but relabel it **"Show <opposite> — false info"** and require it to be the *only* prominent action (demote the true answer). Vortox in particular makes false info mandatory, not optional; the UI currently treats all impairment identically.
- **visibility:** nothing to the Demon or Minions. The Spy sees the `Red herring` token in the grimoire like any other token — no change needed.
- **day-time inputs:** none required by the ability. Optional but valuable: let the storyteller record the FT's public claim so the reveal flow can show it.
- **log:** `NightRecord(cycle, "fortuneteller", [ftId], [a, b], outcome = "YES"/"NO", impaired)` plus a `misregistration` field naming any Recluse ruling made, so the storyteller can stay consistent across nights.

### Step-header context the panel must always show

- **"Red herring: <name>"** (or, in red, **"No red herring assigned — tap to set one"**).
- **"Last night <FT> asked about <X> and <Y> and heard <answer>."** — read from `nightLog`.

### UI text

- Picker header: **"Who did the Fortune Teller point at? (2 players — dead and self are legal)"**
- Answer, true NO with a Recluse chosen: **"NO — unless you rule that <Recluse> registers as the Demon tonight."**
- Answer, YES: **"YES — <reason>."**
- Impaired: **"⚠ <FT> is POISONED — the answer you give must be false. True answer: NO."**

### Data changes

- `night_guide.json` `fortuneteller.first.instructions` — add *"(dead players and themselves are allowed)"* to match `.other`, and move the red-herring sentence into a dedicated setup step so it is not buried in night-1 prose.
- `characters.json` — no changes needed; ability text and reminders are current.

## Tests to add

1. `red herring survives dawn dusk death and character change`
   Given `fortuneteller/Red herring` on seat 4. When the state passes NIGHT→DAY→NIGHT, seat 4 is killed, and seat 4's character is changed.
   Then seat 4 still carries the token and the FT calc still answers YES on seat 4.

2. `fortune teller offers a recluse yes`
   Given a `recluse` at seat 5, `imp` at seat 0, red herring at seat 7, and FT targets `[5, 2]`.
   Then the result's `trueAnswer` is false **and** `optionalYes` names seat 5's Recluse. (Today `caveats` merely mentions the Recluse and the headline is a bare "NO".)

3. `fortune teller does not offer a spy yes`
   Given a `spy` at seat 6 and FT targets `[6, 2]`.
   Then `optionalYes` is empty and no Spy caveat is produced for this character.

4. `red herring is required when the fortune teller arrives mid-game`
   Given a game in `Phase.NIGHT`, cycle 3, with no `fortuneteller`. When seat 3's character is changed to `fortuneteller`.
   Then the "needs a red herring" condition is true and `validateSetupState`-equivalent validation reports it.

5. `fortune teller targets may be dead or self`
   Given a dead seat 5 and the FT at seat 3. When targets are `[3, 5]`.
   Then the calc returns a valid result (not a "Pick 2…" placeholder), and returns YES if either is the Demon or the red herring.

6. `two red herrings is a setup error`
   Given tokens on seats 4 and 6. Then `validateSetupState` reports *"choose exactly one good red herring"*.

7. `red herring on an evil seat is reported at every phase boundary`
   Given the token on the Imp's seat during `Phase.NIGHT`, cycle 2. Then validation reports *"the red herring must be a good player"* (today this is only checked in setup).

8. `fortune teller answer is recorded in the night log`
   Given the FT resolves targets `[0, 7]` on night 2 with answer YES.
   Then `state.nightLog` contains `NightRecord(cycle = 2, stepId = "fortuneteller", targetIds = [0, 7], outcome = "YES")`.

9. `vortox forces false fortune teller info`
   Given a `vortox` alive and a sober FT. Then the result is flagged as *must be false*, distinct from the merely *may be false* drunk/poisoned flag.
