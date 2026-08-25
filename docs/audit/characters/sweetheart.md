# Sweetheart (sweetheart) — Sects & Violets Outsider

## Official rules (sources)

Sources: https://wiki.bloodontheclocktower.com/Sweetheart (Character Text, How
to Run, Examples, Tips & Tricks), https://wiki.bloodontheclocktower.com/Abilities
(character-change consequences).

Current ability text (verbatim):

> "When you die, 1 player is drunk from now on."

How to Run (wiki):

- "The Storyteller selects **any** player to become drunk when the Sweetheart
  dies, marking them with the **DRUNK** reminder."
- "This ability remains active **even after the Sweetheart's death**." — the
  drunkness does not end when the Sweetheart is dead; it is permanent.
- "The Storyteller may target **Townsfolk, Outsiders, Minions, or even the
  Demon**, though Townsfolk are the typical choice."

Examples (wiki):

- Sweetheart dies → the Mathematician becomes drunk and receives false info.
- Sweetheart dies → the **Mutant** becomes drunk, "unaware of their safety when
  claiming their role" (a drunk Mutant cannot be executed for madness).
- Sweetheart dies → the **Demon** becomes drunk, preventing their night kill.

Timing and edge cases:

- The trigger is **"when you die"**, not "the night after you die". A Sweetheart
  executed at 3pm makes someone drunk **immediately**, before any further
  day-time ability resolves (Slayer, Artist, Savant, Fisherman, Gossip, Juggler,
  a Virgin nomination…). The **other-night** night-order slot exists because the
  common case is a Demon kill; it is not the only moment the choice can be made.
- Night-order position: **other nights only**, immediately after the Barber and
  after every Demon attack, before Sage / Professor / Ravenkeeper / Undertaker /
  Empath / Fortune Teller / Dreamer. So a Sweetheart-drunk info role gets false
  info **that same night**.
- **The drunk player is never told.** No card is shown, nobody is woken.
- **A drunk or poisoned Sweetheart has no ability** — nobody becomes drunk.
  (General rule; the page does not restate it.)
- **Not stated on the wiki, flagged rather than asserted:**
  - whether a resurrected Sweetheart who dies a second time makes a *second*
    player drunk (a straight reading of "when you die" says yes, and the first
    drunkness is permanent, so there would then be two);
  - whether the Sweetheart may choose themself (they are dead, so it is legal
    but pointless);
  - whether the drunkness survives the *dead Sweetheart* being turned into
    another character by the Pit-Hag or Barber. The Abilities page says "They
    lose their old ability immediately and **any of its persistent effects
    end**", which would end the drunkness — verify before implementing.
- **Jinxes:** none.

## What the app does today

Data:
- `engine/src/main/resources/botc/data/characters.json:998-1010` — ability text
  matches the wiki; `otherNightReminder` = "Choose a player that is drunk."
  (matches the official night sheet); `reminders: ["Drunk"]`.
- `engine/src/main/resources/botc/data/night_guide.json:618-623` — an accurate
  `other` prose entry, `shows: []` (correct — nobody is woken).
- `engine/src/main/resources/botc/data/night_and_jinxes.json:433` — other-night
  index 60, correctly after the Barber and all Demon kills. **Works.**

Engine:
- `engine/src/main/kotlin/com/clocktower/engine/StatusEffects.kt:99` — the death
  note "Sweetheart: choose 1 player to be drunk from now on."
- `engine/src/main/kotlin/com/clocktower/engine/StatusEffects.kt:36-46` —
  `isImpaired` matches any reminder label containing "drunk", so a placed
  `sweetheart:Drunk` correctly makes the holder impaired everywhere the engine
  checks. **Works.**
- `engine/src/main/kotlin/com/clocktower/engine/GameActions.kt:218-242` —
  `("sweetheart","Drunk")` is in **neither** expiry table, so the token is
  permanent. **Works** (and is the correct reading of "from now on").

UI:
- `app/.../NightScreen.kt:470-524` — `QuickResolutions` has no `"sweetheart"`
  branch; the step offers no dedicated tool.
- `app/.../NightScreen.kt:193-357` — `NightToolTray` does offer the `Drunk`
  chip plus a seat row while the Sweetheart step is expanded, so at night the
  placement is two taps. **Partly works.**
- `app/.../NightScreen.kt:318-340` — but placement goes through
  `placeExclusiveReminder` when only one copy of the label exists, which
  **removes** any existing `sweetheart:Drunk` from another seat.
- `app/.../NightScreen.kt:751-757` — the row prints "All holders are dead —
  usually skip." exactly when the step must run.
- `app/.../SeatSheet.kt:240-251` — the death note appears in the seat sheet only.
- `app/.../DayScreen.kt:111-114`, `:350-357`, `app/.../GameShell.kt:599-604` —
  executions from the Day tab and the dusk guard never consult `deathNotes`.

Storyteller experience: the Sweetheart row appears every night from night 2,
always labelled "usually skip"; when the Sweetheart actually dies at night the
storyteller must notice the row, ignore the skip advice, tap Drunk, tap a seat.
When the Sweetheart dies during the **day** — the most common case, execution —
the app says nothing at all and the drunkness is silently deferred to that
night, so any day-time ability resolved in between is resolved wrongly.

## Defects and gaps

1. **P0** · A day-time Sweetheart death produces no prompt, so the drunkness
   starts hours late · "When you die, 1 player is drunk **from now on**". If the
   Sweetheart is executed and the storyteller then resolves a Slayer shot, an
   Artist question, a Savant, a Fisherman or a Gossip claim, those must already
   account for the new drunk. Nothing prompts until that night's Sweetheart row.
   · `DayScreen.kt:111-114`, `GameShell.kt:599-604` (kill without notes),
   `NightScreen.kt:470-524` (no day path) · Repro: execute the Sweetheart, then
   ask the Artist a question.

2. **P0** · The Sweetheart row tells the storyteller to skip exactly when it
   must run · `allDead` logic in `NightScreen.kt:751-757` prints "All holders
   are dead — usually skip." for any all-dead step, but the Sweetheart step only
   exists because the Sweetheart is dead. Same class of bug as the Barber. ·
   Repro: kill the Sweetheart at night, look at their row on the same night's
   sheet (or the next night's).

3. **P0** · An impaired Sweetheart's death still asks for a drunk · Neither
   `deathNotes` (`StatusEffects.kt:99`) nor the night row checks
   `isImpaired`/`DeathRecord.abilityImpairedAtDeath`, so a Poisoner'd or
   Drunk-token Sweetheart appears to trigger. The engine already records
   `abilityImpairedAtDeath` at `GameActions.kt:153`. · Repro: poison the
   Sweetheart, kill them, read the note.

4. **P1** · The row is emitted every night whether or not the Sweetheart died
   and whether or not the choice was already made · `NightOrder.kt:142-178`
   gates only on "a seat holds `sweetheart`". There is no
   "died today or tonight" condition and no "already resolved" state, so from
   night 2 to the end of the game the storyteller re-reads a step that is almost
   always a no-op — and, worse, after the choice has been made the row invites
   making it a second time. · Repro: resolve the Sweetheart, advance a night.

5. **P1** · Making a second Sweetheart drunk moves the first · The tray uses
   `placeExclusiveReminder` for single-copy labels (`NightScreen.kt:322-325`,
   `GameActions.kt:194-201`), so if a resurrected Sweetheart dies again — or the
   storyteller corrects a mis-tap — the previous permanent drunkness is silently
   removed. A permanent, "from now on" token must never be exclusive. · Repro:
   place `sweetheart:Drunk` on A, then on B; A's token is gone.

6. **P1** · Nothing records *who* is Sweetheart-drunk in a durable, visible way ·
   The token is one of up to N reminders on a seat and the Grimoire seat view
   truncates (`GrimoireScreen.kt:471-487` shows only the last few with a "+n"),
   so the single most consequential standing effect in the game can be hidden
   behind a "+2". · Repro: give a seat three reminders plus the Sweetheart drunk.

7. **P2** · No guidance on *whom* to pick · The wiki is explicit that the
   Demon, a Minion, an Outsider or a Townsfolk are all legal and that the choice
   is a balance lever. The picker is an undifferentiated seat row
   (`NightScreen.kt:314-353`) with no sorting by "has a live information
   ability", no marker for who is already impaired, and no warning that picking
   the Demon disables their kill.

8. **P2** · The drunk player must not be woken, and nothing says so · The guide
   text says it (`night_guide.json:620`), but the tray still offers "Show token"
   and "Sheet" chips (`NightScreen.kt:246-262`) as if a card were expected.

9. **P3** · A resurrected Sweetheart's second death is unspecified in the app ·
   Once the "already resolved" state from defect 4 exists, the engine must
   decide whether a second death re-arms it. Recommend: yes, arm again (each
   death is a separate trigger), with both drunks persisting.

## Proposed behaviour (spec)

### State the engine needs

- `PlacedReminder("sweetheart", "Drunk")` — **permanent, non-exclusive**,
  multiple instances allowed.
- `PlacedReminder("sweetheart", "Chose")` — an internal marker on the dead
  Sweetheart's own seat meaning "this death's choice has been made", so the
  night row can retire itself. (Not an official token; storyteller-only.)

### Trigger (on death, any cause, any phase)

- when: a seat whose `characterId == "sweetheart"` transitions to `alive = false`.
- Condition: the death record's `abilityImpairedAtDeath == false`.
  - If it is `true`, place nothing and brief:
    `<name> the Sweetheart died drunk/poisoned — nobody becomes drunk. Say
    nothing.`
- Effect: raise an **obligation** `sweetheart-drunk` which is surfaced
  **immediately, in whatever phase the death happened**:
  - `phase == DAY` → a modal right away, because the drunkness is retroactive to
    this instant and day abilities depend on it;
  - `phase == NIGHT` → surfaced inline on the Sweetheart night row (which is
    positioned after all Demon kills, so the information is complete) **and**
    carried into the day-start briefing if unresolved.

### Targets

- count: 1. constraints: **any** seat, alive or dead, including the Sweetheart
  themself and the Demon; travellers allowed.
- picker default/sort: living seats first; within those, seats whose character
  has an active information ability (`InfoCalc.supports(characterId)`) first,
  since those are the meaningful choices; annotate each chip with
  `already drunk/poisoned` where `isImpaired` is true, and annotate the Demon
  chip with `picking the Demon stops their kills`.

### Immediate effects

- `GameActions.addReminder(state, targetId, PlacedReminder("sweetheart","Drunk"))`
  — **`addReminder`, not `placeExclusiveReminder`**.
- `addReminder(sweetheartSeat, PlacedReminder("sweetheart","Chose"))`.
- No card, no wake, no announcement. Explicitly print
  `Do not wake them. They are never told.`

### Deferred effects / expiry

- Neither token ever expires: keep both out of `EXPIRES_AT_DAWN` and
  `EXPIRES_AT_DUSK` (`GameActions.kt:218-242`) — already correct for `Drunk`.
- The drunkness must be visible forever: add it to the proposed **standing
  effects** strip (see below) rather than relying on the seat's reminder pile.

### Night step

- when: other nights. Wake condition: **hidden entirely** unless there is an
  unresolved `sweetheart-drunk` obligation. Never show a row that says "skip".
- The row's title should read `Sweetheart — choose the permanent drunk` and must
  not be subject to the "all holders are dead" advice, because a dead holder is
  the precondition.
- If the storyteller resolved it during the day, the row does not appear.

### Information / visibility

- Nothing is shown to any player. The Demon is not told. The drunk is not told.

### Day-time inputs

- None from players. The storyteller may want a note of *why* they chose that
  seat — reuse the seat `note` field.

### Interactions / jinxes

- **No jinxes.**
- **Mutant**: a Sweetheart-drunk Mutant cannot be executed for madness — raise
  the cross-note described in `mutant.md`.
- **Demon**: a Sweetheart-drunk Demon's attack fails. `DemonKillPanel` already
  warns on `isImpaired` (`NightScreen.kt:548-554`) — **works**, and will pick
  the Sweetheart drunk up automatically.
- **No Dashii / Poisoner / Drunk / Vortox**: `isImpaired` already unions them.
- **Pit-Hag / Barber changing the dead Sweetheart's character**: per the
  Abilities page the persistent effect would end. Implement as a *prompt*, not
  an automatic removal, given the uncertainty: `<name> is no longer the
  Sweetheart — does their permanent drunk end? [Keep] [Remove]`.
- **Professor** resurrecting the Sweetheart: the existing drunkness stays; a
  second death arms a second, independent choice.

### UI text

- Death modal title: `<name> the Sweetheart died`
- Body: `Choose one player to be drunk from now on. They are never told. Their
  ability stops working immediately — including for anything you resolve later
  today.`
- Impaired variant: `<name> the Sweetheart died drunk/poisoned — nobody becomes
  drunk.`
- Night row: `Sweetheart — choose the permanent drunk (<name> died <today|
  tonight>)`

### Cross-cutting recommendation

Add a **standing effects strip** rendered on the Grimoire and at day start,
listing every permanent/multi-day effect currently in the grimoire —
Sweetheart drunk, Snake Charmer poison, Fang Gu "Once", Fool/Professor "No
ability", Evil Twin pairing, Witch curse, Cerenovus madness. Reminder tokens
buried in a seat's pile are not a briefing.

### Data changes

- `night_guide.json:618-623` — add to the prose: "You may choose any player,
  including a Minion or the Demon; picking the Demon stops their kills. If the
  Sweetheart was drunk or poisoned when they died, nobody becomes drunk."
- `characters.json:998-1010` — no ability-text drift.

## Tests to add

1. **Given** a living Sweetheart, **when** the other-night sheet is built,
   **then** no `sweetheart` step is present.
2. **Given** the Sweetheart is executed on day 2, **then** an unresolved
   `sweetheart-drunk` obligation exists **during day 2** (not at night 3).
3. **Given** the Sweetheart was carrying `poisoner:Poisoned` when killed,
   **then** no obligation is raised and the briefing says nobody becomes drunk.
4. **Given** `sweetheart:Drunk` on seat A, **when** it is also placed on seat B,
   **then** **both** seats carry it (regression for the exclusive-placement bug).
5. **Given** `sweetheart:Drunk` on a seat, **when** `advancePhase` runs through
   dawn and dusk twice, **then** the token is still there.
6. **Given** `sweetheart:Drunk` on the Empath, **then**
   `InfoCalc.compute(..., "empath", ...)` returns a caveat marking the info as
   unreliable.
7. **Given** `sweetheart:Drunk` on the Demon, **then** `DemonKillPanel`'s
   impairment warning fires (`StatusEffects.isImpaired` is `true`).
8. **Given** the obligation is resolved during the day, **when** the night sheet
   is built, **then** no `sweetheart` step appears.
9. **Given** the Sweetheart is resurrected by the Professor and dies again,
   **then** a second, independent obligation is raised and resolving it leaves
   two `sweetheart:Drunk` tokens on the board.
10. **Given** a Sweetheart step exists because the Sweetheart is dead, **then**
    the step is **not** annotated "All holders are dead — usually skip".
