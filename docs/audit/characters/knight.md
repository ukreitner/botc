# Knight (knight) — Experimental (Carousel) Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Knight> (fetched 2026-08-25).

**Current ability text (wiki):**
> "You start knowing 2 players that are not the Demon."

`characters.json:1434` matches exactly — **no drift**.

**How to Run (wiki):**
> Mark two non-Demon players with the Knight's **KNOW** reminders while preparing the first
> night. During the first night, wake the Knight, point to the two players marked KNOW, and
> put the Knight to sleep.

Timing / edge cases:

- **First night only.** No other-night step. (Official first-night position 58; the Knight
  is absent from the other-night order.) If a Knight is created mid-game, they act on their
  first night as that character.
- The two players may be **any** non-Demon characters — Townsfolk, Outsider or **Minion**.
  The Knight does not learn which team or character either of them is; they learn only
  "not the Demon".
- **Recluse:** may register as the Demon, so the ST may legitimately treat the Recluse as
  ineligible — or, more interestingly, mark the Recluse as one of the two "not the Demon"
  players (a legal and pointed choice).
- **Spy:** registers as good but never as the Demon, so the Spy is irrelevant to this
  ability's constraint; the Spy is always a legal KNOW target.
- **Vortox:** Townsfolk information is false, and the wiki's own example spells this out —
  > "When a Vortox is the Demon, the Knight must learn the Vortox and another player (ensuring the information is false)."
  So under a Vortox at least one of the two shown players **must be the Demon**.
- **Drunk / poisoned Knight:** point at any 2 players, including the Demon.
- **Jinxes:** none listed on the Knight page, and none in `night_and_jinxes.json`.

## What the app does today

- `characters.json:1434-1448`: `reminders: ["Know","Know"]`, `firstNightReminder`
  "Point to the 2 players marked 'Know'.", `otherNightReminder: ""`.
- Night order: `night_and_jinxes.json:353` (first night index 58). Correctly absent from
  the other-night list.
- `night_guide.json:1084-1089`: `first` only, good prose including the drunk/poisoned case.
  No `shows` (correct — the Knight is pointed at players, not shown cards).
- `InfoCalc.supports("knight")` is true (`InfoCalc.kt:33`), dispatched at `InfoCalc.kt:71`,
  implemented at `InfoCalc.kt:433-440`:
  ```kotlin
  val demons = ctx.players.filter { ctx.character(it)?.team == Team.DEMON }
  return InfoResult(
      headline = "Point to 2 players that are NOT the Demon",
      detail = "Demon: ${demons.joinToString { ctx.name(it) }}",
      caveats = misregistrations(ctx, ctx.players),
  )
  ```
- Reminder placement is manual: `NightToolTray` (`NightScreen.kt:193-352`) shows two "Know"
  chips; tap a chip then a seat. Because `allReminders.count { it == "Know" } == 2`, the
  tray's copy-tracking branch (`NightScreen.kt:316-340`) allows exactly two placements and
  silently recycles the **first-placed** one when a third seat is tapped.
- `GameActions.validateSetupState` (`GameActions.kt:503-561`) validates the Drunk, the
  Lunatic, the Marionette and the Fortune Teller's red herring — **not** the Knight.
- `GameShell.kt:347-375` has a bespoke setup prompt for the Fortune Teller's red herring
  and `:376-...` one for the Drunk. There is no Knight equivalent.

Storyteller experience: nothing happens at setup. On night 1, at step 58, the ST expands
the row, reads "Demon: Ali", mentally picks two other players, opens the tray, taps "Know",
taps a seat, taps "Know", taps another seat, then points at them. Nothing checks the choice.

## Defects and gaps

1. **P1 · The KNOW tokens are never prompted for or validated.**
   The rules place them "while preparing the first night". The app has the exact pattern
   already (`GameShell.kt:347-375` for the red herring, enforced by
   `GameActions.validateSetupState` `:547-559`) but does not use it for the Knight. An ST
   can reach night 1 — or finish the game — with no KNOW tokens placed at all.
   *Repro:* Deal a bag containing a Knight, tap "Begin night". No prompt, no setup issue.

2. **P1 · `InfoCalc.knight` reveals the Demon but does not answer the question.**
   `InfoCalc.kt:433-440` returns a static headline plus "Demon: Ali". It does not:
   list the legal pool (everyone except the Demon), propose a pair, read the KNOW tokens
   already placed, or say which players are currently marked. The one thing it does show —
   the Demon's name — is the one thing the ST already knows from the grimoire.
   *Repro:* Night 1, expand Knight → "Point to 2 players that are NOT the Demon /
   Demon: Ali". No chips, no pair.

3. **P1 · Nothing validates that the marked pair excludes the Demon.**
   The tray happily places both Know tokens on the Demon's seat. There is no check in
   `validateSetupState`, none in `InfoCalc`, and no warning on the step.
   *Repro:* Place "Know" on the Imp's seat twice → no complaint anywhere.

4. **P1 · A Knight created mid-game never wakes.**
   `knight` appears only in `firstNight` (`night_and_jinxes.json:353`) and `NightOrder.build`
   (`NightOrder.kt:130-181`) draws exclusively from those two lists. A Pit-Hag-made Knight,
   an Amnesiac who becomes the Knight, or a Damsel turned into the Knight by the Huntsman,
   gets no step and no information on any night. (Cross-cutting: this is true of every
   "You start knowing" character — see `noble.md`, and the same for Washerwoman/Librarian/
   Investigator/Chef/Steward/Clockmaker.)

5. **P2 · The Vortox case is flagged but not actionable.**
   `commonCaveats` (`InfoCalc.kt:157-166`) adds "VORTOX in play — Townsfolk info must be
   FALSE", but for the Knight "false" has one concrete meaning: **at least one of the two
   shown players is the Demon**. The step never says that, and the "False info to show
   instead" helper (`NightScreen.kt:880-931`) only offers alternative numbers or YES/NO,
   which is inapplicable here, so it renders nothing.

6. **P2 · Misregistration caveats are generic noise.**
   `misregistrations(ctx, ctx.players)` (`InfoCalc.kt:120-130`, called at `InfoCalc.kt:437`)
   prints the Spy line ("may register as good / a Townsfolk or Outsider") for the Knight,
   where the Spy is simply a legal target and the note means nothing. Only the Recluse
   matters, and the useful phrasing is "the Recluse may register as the Demon — you may
   still mark them KNOW, or avoid them".

7. **P2 · No day-time record of the Knight's claim.**
   When the Knight comes out and names two players, there is nowhere to record it. The ST
   later needs it to check consistency (was the Knight poisoned on night 1? did they get
   a Vortox-false pair?). Same gap as Gossip/Juggler/Savant in the user's report.

8. **P3 · The two "Know" tokens rotate silently.**
   `NightScreen.kt:328-336` removes `placed.first()` when both copies are already down and
   a third seat is tapped. It is the right behaviour but there is no feedback about which
   token moved.

9. **P3 · The step stays on the sheet after it is run.** Correct behaviour (night 1 only),
   nothing to fix — noted so it is not mistaken for defect 4.

## Proposed behaviour (spec)

**Setup**

- When a Knight is dealt, raise a setup prompt in the same style as the red-herring dialog
  (`GameShell.kt:347-375`): *"Knight — pick 2 players who are not the Demon."* Present all
  non-Demon seats as a two-of-N picker with a suggested default (see below), and place
  `PlacedReminder("knight","Know")` on both on confirm.
- Add to `GameActions.validateSetupState` (`GameActions.kt:503-561`):
  - if a Knight is in play, exactly two `("knight","Know")` tokens must be placed;
  - neither may sit on a seat whose character's team is `DEMON`, **unless** a Vortox is in
    play, in which case at least one must (see below);
  issue text: `"Knight: mark exactly 2 non-Demon players with Know"`.

**Structured night step**

- **when:** first night only; also the holder's *first* night if the Knight is created later
  (fix defect 4 by letting `NightOrder` include a "start knowing" character on any night the
  seat first holds it — track `learnedOnCycle` per seat, or simply: include the step if the
  seat has no `("<id>","Know")` tokens yet).
  Wake condition: holder alive.
- **targets:** none chosen by the player; the **ST** marks 2 seats.
- **immediate effects:** two `("knight","Know")` reminders. Not exclusive individually —
  cap at 2 copies (the tray already does this).
- **deferred effects / expiry:** none. The tokens are a permanent record and must never be
  swept by `EXPIRES_AT_DAWN`/`EXPIRES_AT_DUSK` (`GameActions.kt:218-242`).
- **information:**
  - Headline: `Point to Bo and Cara — neither is the Demon.` (i.e. read the placed tokens
    back, do not restate the task).
  - If fewer than 2 tokens are placed: `Pick 2 of: <chips for every non-Demon seat>` with a
    one-tap placement, defaulting to a suggested pair — prefer seats that are **not** the
    Recluse, not a Minion the ST wants to protect, and spread around the circle.
  - Detail line: `Demon: Ali (excluded).` plus `Legal: everyone else, including Minions and Outsiders.`
  - **Impaired** (drunk/poisoned/Drunk/Marionette, via `InfoCalc.impairments`): red banner
    "Any 2 players are legal, including the Demon", and the chip list expands to all seats.
  - **Vortox:** red banner "Vortox — the Knight's info must be FALSE: at least one of the
    two must be the Demon", and the suggested pair becomes `(Demon, any other)`.
  - **Misregistration:** replace the generic lines with Knight-specific ones — emit only the
    Recluse note, phrased "Cara is the Recluse — she may register as the Demon, so marking
    her KNOW is a real (and legal) risk". Suppress the Spy note entirely for this character.
- **visibility:** nothing to evil.
- **day-time inputs:** a day-screen "public claim" recorder — `Knight claims: Bo, Cara` —
  which the app can cross-check against the placed KNOW tokens and against the Knight's
  impairment on night 1 (`DeathRecord.abilityImpairedAtDeath` shows the precedent for
  snapshotting impairment; do the same for start-knowing info).
- **interactions/jinxes:** none.

**UI text**

- Setup prompt: `Knight — mark 2 players who are not the Demon.`
- Step (tokens placed): `Wake the Knight (Dan). Point to Bo and Cara. Neither is the Demon.`
- Step (tokens missing): `! No Know tokens placed — pick 2 non-Demon players.`
- Impaired: `! Dan is POISONED (Poisoner) — point at any 2 players, the Demon included.`

**Data changes**

- `night_guide.json:1084-1089` — add: "The 2 players may be any characters other than the
  Demon, including Minions." and "Under a Vortox, one of the two must be the Demon."
- No `characters.json` or night-order change.

## Tests to add

1. `GIVEN` a Knight in play with no `("knight","Know")` tokens `WHEN`
   `GameActions.validateSetupState` runs `THEN` it reports a Knight issue. *Fails today.*
2. `GIVEN` a Knight with both Know tokens placed, one of them on the Imp `THEN`
   `validateSetupState` reports "Knight: Know tokens must not be on the Demon".
   *Fails today.*
3. `GIVEN` a Knight with Know on Bo and Cara `WHEN` `InfoCalc.compute(..., "knight", ...)`
   runs `THEN` the headline names Bo and Cara. *Fails today* (headline is static).
4. `GIVEN` a Vortox in play `THEN` the Knight result's caveats include the "one of the two
   must be the Demon" instruction, not just the generic Vortox line. *Fails today.*
5. `GIVEN` a Recluse and a Spy in play `THEN` the Knight result's caveats mention the
   Recluse and **not** the Spy. *Fails today.*
6. `GIVEN` a poisoned Knight `THEN` the caveats include the poison line and the legal pool
   is all players.
7. `GIVEN` a seat that becomes the Knight on cycle 3 `WHEN` the other-night sheet is built
   `THEN` a Knight step appears for that seat. *Fails today.*
8. `GIVEN` a Knight `WHEN` `advancePhase` runs through a full dawn and dusk `THEN` both
   `("knight","Know")` tokens are still on their seats (regression guard against adding
   them to an expiry table).
