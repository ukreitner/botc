# Witch (witch) — Sects & Violets Minion

## Official rules (sources)

Sources: https://wiki.bloodontheclocktower.com/Witch (Character Text, How to Run,
Key Rules & Edge Cases, Strategic Tips),
https://www.botcscriptorium.com/characters/witch/ (jinx check).

Current ability text (verbatim):

> "Each night, choose a player: if they nominate tomorrow, they die. If just 3
> players live, you lose this ability."

How to Run (wiki):

- "Each night, wake the Witch to point at any player, then mark them with a
  **CURSED** reminder."
- "If that cursed player nominates the next day, **immediately declare that they
  die. (_Nominations continue as normal._)**"
- "**The curse lasts only one day.**"
- "Once three players remain alive, **the Witch's curse is immediately removed,
  and the Witch acts no more**."

Key rules and edge cases (wiki):

- **Nomination resolution:** "The cursed player dies immediately upon
  nominating, but **their nomination still counts and voting proceeds
  normally**."
- **Self-cursing:** "The Witch may curse themselves."
- **Role changes:** "If a cursed player's character changes (e.g., Klutz becomes
  Fang Gu), **the curse transfers to the new character**." — i.e. the curse is
  attached to the *player/seat*, not the character.
- **Three-player threshold:** "The curse is removed **immediately** when only
  three players remain alive." Not "at dusk", not "next night".
- **Exiles:** "Ability-triggering restrictions don't apply to exiles — **a cursed
  player calling for a Traveller exile doesn't die**."
- Strategy notes confirm cursing **dead** players is legal (used to hide the
  ability).

**Jinxes:** none. (Checked both the wiki and Scriptorium.)

Night order: **first night and other nights**, official slot between the Devil's
Advocate and the Cerenovus. The official other-night sheet line is conditional:
"If there are 4 or more players alive: The Witch points to a player…"

**Not stated on the wiki, flagged rather than asserted:**
- Whether the curse ends when the **Witch dies**. It follows from the general
  rule that a dead player has no ability (and from the wiki's own "a dead Evil
  Twin has no ability" phrasing for the analogous case) that the curse should
  stop working, and the corresponding token should come off. Verify before
  implementing the automatic removal; implement the *behaviour* (no death on
  nomination) regardless, since the ability plainly is not working.
- A drunk/poisoned Witch's curse has no effect (general rule; the app's own
  guide already says this).

## What the app does today

Data:
- `engine/src/main/resources/botc/data/characters.json:1052-1064` — ability text
  matches the wiki; both night reminders are faithful transcriptions, including
  the "If there are 4 or more players alive:" conditional; `reminders:
  ["Cursed"]`.
- `engine/src/main/resources/botc/data/night_guide.json:710-719` — `first` and
  `other` entries, both accurate: they state that the nomination still proceeds,
  the 3-alive shutdown, and that a drunk/poisoned Witch's curse has no effect.
  **Good data.**
- `engine/src/main/resources/botc/data/night_and_jinxes.json:331` (first night
  index 36) and `:396` (other night index 23) — both correct. **Works.**

Engine:
- `engine/src/main/kotlin/com/clocktower/engine/GameActions.kt:237` —
  `("witch","Cursed")` is in `EXPIRES_AT_DUSK`, so a token placed on night *N*
  survives day *N* and is swept at the `DAY -> NIGHT` transition. **Correct
  duration** ("the curse lasts only one day").
- `engine/src/main/kotlin/com/clocktower/engine/StatusEffects.kt:142-147` — the
  entire automation:
  ```
  if (state.alivePlayers.size >= 4 &&
      nominator.reminders.any { it.label.equals("Cursed", true) }
  ) {
      notes += "${nominator.name} is Witch-cursed — they die immediately for nominating (if 4+ alive)."
  }
  ```
  An advisory **string**. Nothing dies.

UI:
- `app/.../DayScreen.kt:154-159` — the warning is rendered in red under the
  nominator/nominee chips as soon as both are selected.
- `app/.../NightScreen.kt:193-357` — the tray offers the `Cursed` chip and a
  seat row; `placeExclusiveReminder` correctly moves the single token.
  **Works.**
- `app/.../NightScreen.kt:470-524` — no `witch` branch in `QuickResolutions`.
- `app/.../SeatSheet.kt:269-287` — to kill the cursed player the storyteller
  must leave the Day tab, open the seat, and choose among "Died at night" /
  "Executed" / "Other death" with no guidance about which is correct.

Storyteller experience: cursing is quick and the token expires correctly. But at
the moment of the nomination the app shows a sentence and then does nothing —
the storyteller has to announce the death, switch tabs, find the seat, guess a
death cause, come back, and re-tally the vote against a threshold that has just
changed. Exactly the "Devil's Advocate wasn't automatically removed" complaint
in the brief, applied to a death.

## Defects and gaps

1. **P0** · The curse never kills anyone · "Immediately declare that they die."
   The app only prints a warning string
   (`StatusEffects.kt:142-147`, rendered at `DayScreen.kt:154-159`). The
   storyteller must perform the death by hand, and if they miss the red line —
   which appears only once *both* nominator and nominee are selected — the
   player simply does not die. · Repro: place `witch:Cursed`, select that player
   as nominator in the Day tab.

2. **P0** · A cursed player calling an **exile** is wrongly warned (and would
   wrongly die if automated) · "Ability-triggering restrictions don't apply to
   exiles." `nominationWarnings` (`StatusEffects.kt:132-147`) receives only
   `nominatorId`/`nomineeId`; the exile determination lives in the UI at
   `DayScreen.kt:163` (`nominee?.isTraveller == true`) and is never passed in. ·
   Repro: with a Traveller seated, select a cursed nominator and the Traveller
   as nominee — the death warning appears.

3. **P0** · The curse fires even when the Witch's ability is not working ·
   Nothing checks that the Witch is alive, nor
   `StatusEffects.isImpaired(state, lookup, witch)` — although the app's own
   guide text says a drunk/poisoned Witch's curse has no effect
   (`night_guide.json`). A curse placed on night 2 by a Witch who is executed at
   noon on day 2 still produces the warning. · `StatusEffects.kt:142-147` ·
   Repro: poison the Witch, curse someone, nominate.

4. **P1** · The Witch step is not gated on 4+ alive · The official other-night
   line is conditional and `NightOrder.build` (`NightOrder.kt:142-178`) has no
   condition; the row is emitted with 3 alive and the storyteller must read the
   conditional out of the step text. · Repro: reduce to 3 alive, advance to
   night.

5. **P1** · The "3 players live" shutdown is not immediate · "The curse is
   removed **immediately** when only three players remain alive." The token is
   only swept at dusk (`GameActions.kt:237, 258-263`), so the grimoire keeps
   showing a live-looking `Cursed` token that no longer means anything. The
   `>= 4` guard in `nominationWarnings` masks the behaviour but not the display.
   · Repro: curse someone, then kill players down to 3 alive during the day.

6. **P1** · No guidance on the death cause, and the wrong one corrupts the
   Undertaker · The seat sheet offers "Died at night" (`DeathCause.DEMON`),
   "Executed" (`DeathCause.EXECUTION`) and "Other death"
   (`DeathCause.STORYTELLER`) (`SeatSheet.kt:269-287`). A Witch death is **not**
   an execution; picking "Executed" feeds
   `InfoCalc.undertaker` (`InfoCalc.kt:281-292`) a false execution and, once
   `executionUsedToday` exists (see `mutant.md`), would consume the day's
   execution. Nothing steers the storyteller. · Repro: kill a cursed nominator
   with the "Executed" button, then run the Undertaker.

7. **P1** · The vote must be re-thresholded after the death, and nothing says so
   · `DayScreen.kt:71-72` computes `threshold` from the live
   `state.alivePlayers.size`, so the numbers *are* right — but only if the
   storyteller records the death **before** tallying, and nothing tells them
   that order matters. The now-dead nominator also gains a ghost vote they may
   spend on their own nomination (`DayScreen.kt:184`, `:232-240`). ·
   Repro: tally first, then kill.

8. **P2** · The warning text is self-contradicting · "…they die immediately for
   nominating **(if 4+ alive)**" — the parenthetical is dead text, because the
   condition is already `state.alivePlayers.size >= 4`
   (`StatusEffects.kt:143`). It reads as a caveat the storyteller must evaluate.

9. **P2** · The warning matches the label `Cursed` from any source ·
   `StatusEffects.kt:144` matches `label.equals("Cursed", true)` without
   checking `sourceId == "witch"` — the same class of bug as the `Mad` label
   (see `cerenovus.md`).

10. **P2** · Nothing carries the curse through a character change · The wiki is
    explicit that the curse follows the player. It happens to work because the
    token is on the seat — but the proposed `becomeCharacter` action in
    `pithag.md` removes tokens by `sourceId` of the **outgoing** character, so
    the Witch's own transformation must remove the curse while the *victim's*
    transformation must not. Needs a deliberate test.

11. **P2** · The warning only appears once both nominator and nominee are
    selected · `DayScreen.kt:154-156` passes both ids; a storyteller who taps
    the nominator and then looks up at the table sees nothing yet.

12. **P3** · No log entry distinguishing a Witch death · The game log records a
    `DeathCause`, so a Witch kill recorded as `STORYTELLER` is
    indistinguishable from any other storyteller death.

## Proposed behaviour (spec)

### Night step

- when: **both** first and other nights.
- wake condition: a seat holds `witch`, that seat is **alive** (or retains its
  ability), **and** `state.alivePlayers.size >= 4`. Below 4, the row is hidden
  entirely and a one-line note is added to the night sheet:
  `Witch: only 3 players live — the Witch has lost their ability.`
- targets: 1, **any** seat — alive or dead, good or evil, including the Witch
  themself and travellers. Sorting hint: living players who nominated on a
  previous day first (derive from `state.nominations`), then loud/likely
  nominators — the wiki's own strategy advice.
- immediate effects:
  `placeExclusiveReminder(target, PlacedReminder("witch","Cursed"))`. Exclusive
  is correct (one token, moves each night). No card is shown; the cursed player
  is **not** woken and **not** told.
- impairment: if `isImpaired(witch)`, still run the step (to hide it) but mark
  the placed token internally as ineffective and print
  `The Witch is drunk/poisoned — the curse will not kill. Place the token
  anyway.`
- expiry:
  - keep `("witch","Cursed")` in `EXPIRES_AT_DUSK` (correct, "lasts only one
    day");
  - **additionally** remove it immediately when `state.alivePlayers.size` drops
    to 3, from wherever the death is recorded (`GameActions.kill`), with a log
    line `Witch curse removed — only 3 players live.`;
  - **additionally** remove it immediately when the Witch dies or stops being
    the Witch (flagged as needing confirmation above), with a log line
    `Witch curse removed — the Witch lost their ability.` If the ruling turns
    out to be that the token stays, keep the token but mark it ineffective; the
    *behaviour* (no death) is not in doubt.

### Nomination-time trigger (the automation the brief asks for)

`StatusEffects.nominationWarnings` must grow the context it needs, and the Day
tab must act on it rather than narrate it.

- Change the signature to take the full nomination context:
  `nominationWarnings(state, lookup, nominatorId, nomineeId, isExile: Boolean)`.
  `DayScreen.kt:154-156` already knows `isExile` at `:163` — pass it.
- Add a structured result alongside the human strings, so the UI can act:
  ```
  data class NominationTrigger(
      val kind: String,           // "witch-curse", "virgin", "golem", "fearmonger"
      val playerId: Long,
      val autoResolvable: Boolean,
      val text: String,
  )
  ```
- The Witch trigger fires when **all** of:
  - the nominator carries `PlacedReminder("witch","Cursed")` (match on
    `sourceId`, not just the label);
  - `state.alivePlayers.size >= 4`;
  - a Witch exists who is alive (or retains their ability) and is **not**
    impaired;
  - **`!isExile`**;
  - the nominator is alive (a dead player cannot nominate anyway).
- UI behaviour at `DayScreen.kt:126-255`: the moment the **nominator** is
  selected (before the nominee, fixing defect 11), show a prominent banner and a
  single primary button:

  `<name> is Witch-cursed. Announce: "<name> dies." Nominations continue as
  normal.`  →  **`Kill <name> now`**

  Tapping it performs `GameActions.kill(state, nominatorId, DeathCause.WITCH,
  lookup)` (a new cause; see below), records the log line, and leaves the
  nomination in progress. The vote panel then re-renders with the new
  `executionThreshold` and the nominator's fresh ghost vote — with an explicit
  line `Threshold is now <n> (<name> died).`
- Add `DeathCause.WITCH` (or, if the enum is to stay small, keep
  `OTHER_NIGHT_DEATH`/`STORYTELLER` but tag the death record with a `source`
  string). What matters is that it is **not** `EXECUTION`, so the Undertaker and
  the day's-execution accounting stay correct.
- If the storyteller dismisses the banner, keep it visible for the rest of that
  nomination; it must not be possible to record the nomination with an
  unresolved Witch trigger without an explicit `They don't die (override)`.

### Deferred effects / day-start

- Day-start briefing while a curse is live and effective:
  `<name> is Witch-cursed today — if they nominate, they die immediately
  (nominations continue).`
- Nothing at dawn beyond that; the curse itself is silent.

### Information / visibility

- Nothing is shown to any player at any point. The cursed player is never told.
  The Demon and other Minions learn nothing.

### Interactions to handle explicitly

- **Exile** — never triggers (wiki, verbatim). This is the one hard rule the app
  currently gets wrong.
- **Virgin** — a cursed player nominating the Virgin: they die from the curse,
  and the Virgin's ability still resolves (they were a Townsfolk when they
  nominated). Both notes must appear together; `nominationWarnings` already
  produces the Virgin line at `StatusEffects.kt:152-157`.
- **Golem** — same stacking; the Golem's own nomination consequence is separate.
- **Klutz** — a cursed Klutz who nominates dies during the day and must then
  make their public choice immediately (see `klutz.md`). Wire the Klutz
  obligation into this death path.
- **Sweetheart** — a cursed Sweetheart who nominates dies during the day, so the
  permanent-drunk prompt must fire right there (see `sweetheart.md`).
- **Barber** — a cursed Barber who nominates arms the haircut for that night.
- **Saint / Mayor** — a Witch death is not an execution; the Saint does not lose
  the game and the Mayor's "no execution" condition is unaffected.
- **Character change** — the curse follows the *player*. When the **victim**
  changes character, keep the token; when the **Witch** changes character,
  remove it.
- **Travellers** — a Traveller may be cursed and dies for nominating (only
  *exiles* are exempt, not the cursed traveller's own nominations).
- **Jinxes** — none.

### UI text

- Night step: `Witch — curse a player` /
  `Wake <Witch>. They point at any player, alive or dead. Move the Cursed token.
  Say nothing to the cursed player.`
- Below-4 note: `Witch: only 3 players live — no ability, do not wake.`
- Day banner: `<name> is Witch-cursed — nominating kills them.`
- Trigger button: `Kill <name> now` · secondary `They don't die (override)`
- After the kill: `Announce the death, then continue the vote. Threshold is now
  <n>.`

### Data changes

- `characters.json:1052-1064` — no ability-text drift.
- `night_guide.json` (`witch`) — add: "The curse is removed the moment only 3
  players are alive, and the moment the Witch loses their ability. A cursed
  player who calls for a Traveller **exile** does not die."

## Tests to add

1. **Given** `witch:Cursed` on an alive nominator, 5 alive, an alive sober
   Witch, and a non-Traveller nominee, **then** `nominationWarnings` yields a
   `witch-curse` trigger with `autoResolvable = true`.
2. **Given** the same board but the nominee **is a Traveller** (exile), **then**
   no Witch trigger is produced. (Regression for the exile rule.)
3. **Given** the same board but the Witch is **dead**, **then** no Witch trigger
   is produced.
4. **Given** the same board but the Witch carries `poisoner:Poisoned`, **then**
   no Witch trigger is produced.
5. **Given** exactly 3 alive players, **then** no Witch trigger is produced
   **and** no `witch` step appears on the night sheet.
6. **Given** `witch:Cursed` on a seat and 4 alive, **when** a player dies and
   3 remain, **then** the `witch:Cursed` token is removed immediately (not at
   dusk).
7. **Given** `witch:Cursed` placed on night 2, **when** `advancePhase` runs
   `NIGHT -> DAY`, **then** the token survives; **when** it runs `DAY -> NIGHT`,
   **then** it is gone.
8. **Given** a `PlacedReminder("", "Cursed")` from the generic token list,
   **then** no Witch trigger is produced (match on `sourceId`).
9. **Given** the Witch trigger is resolved, **then** the death record's cause is
   **not** `DeathCause.EXECUTION`, and `InfoCalc.undertaker` reports no
   execution that day.
10. **Given** a 7-alive board where the cursed nominator dies from the curse,
    **then** `state.executionThreshold` drops from 4 to 3 and the dead
    nominator has an unspent ghost vote.
11. **Given** a cursed **Klutz** who nominates, **then** the Witch death also
    raises the Klutz public-choice obligation.
12. **Given** a cursed **Sweetheart** who nominates, **then** the Witch death
    also raises the Sweetheart permanent-drunk obligation.
13. **Given** the cursed player is turned into another character by the Pit-Hag,
    **then** the `witch:Cursed` token is still on that seat.
14. **Given** the **Witch** is turned into another character, **then** the
    `witch:Cursed` token is removed from the board.
