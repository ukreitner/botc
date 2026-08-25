# Mutant (mutant) — Sects & Violets Outsider

## Official rules (sources)

Sources: https://wiki.bloodontheclocktower.com/Mutant (Character Text, How to
Run, Examples, Tips & Tricks), https://wiki.bloodontheclocktower.com/Sweetheart
(example confirming a drunk Mutant is safe),
https://wiki.bloodontheclocktower.com/Cerenovus.

Current ability text (verbatim):

> "If you are \"mad\" about being an Outsider, you might be executed."

How to Run (wiki):

- "The Storyteller may execute the Mutant **at any time — even at night** — if
  they believe the Mutant is attempting to convince the group they are an
  Outsider."
- "When this occurs, **declare the execution to all players and mark them with a
  shroud**."
- "If execution happens during the day **before the standard nomination phase,
  proceed to night**."
- "**Only one execution per day is permitted.**" — a Mutant execution *is* the
  day's execution.
- Scriptorium phrasing of the same rule: "if you judge that they are 'mad' about
  being an Outsider — meaning they are actively and convincingly claiming to be
  an Outsider in a way that could damage the good team — you MAY execute them
  immediately, outside of the normal nomination process."

Definition of madness (wiki):

- "'Madness' means actively attempting to persuade others of something. For the
  Mutant, being 'mad about being an Outsider' means trying to convince people
  they belong to the Outsider category — through verbal hints, meaningful
  silence, or subtle gestures. The Storyteller has sole discretion."

Examples (wiki):

- Open day-one claim → immediate execution, **blocking the normal day execution**.
- Prolonged suspicious silence when questioned → storyteller may execute.
- Private reveal to another player, who reports it → execution.
- Sarcasm/winking while denying Mutant status → execution possible.

Edge cases:

- **A drunk or poisoned Mutant has no ability and therefore cannot be executed
  for madness.** Confirmed by the Sweetheart wiki example: "Sweetheart dies; the
  Mutant becomes drunk, **unaware of their safety** when claiming their role."
- **"Might"** — execution is always the storyteller's option, never mandatory.
- **Cerenovus interaction (no formal jinx, but a real trap):** the Cerenovus can
  make a player mad that they are a good character; if that character is an
  **Outsider**, a Mutant target is squeezed — obeying the Cerenovus means being
  mad about being an Outsider, which the storyteller may execute for. Both
  madnesses are live at once and the storyteller must track both.
- **The Mutant is a day/anytime character — no night action.** It is correctly
  absent from both night order lists.
- **Jinxes:** none listed.

## What the app does today

**The Mutant does not exist in the application beyond its dictionary entry.**

- `engine/src/main/resources/botc/data/characters.json:986-996` — id, name,
  team, ability text (matches the wiki), all night/reminder fields empty.
- `engine/src/main/resources/botc/data/night_and_jinxes.json` — correctly absent
  from both order lists. **Works.**
- `engine/src/main/resources/botc/data/night_guide.json` — **no entry**, and the
  guide system has no day surface, so the How-to-Run text is nowhere in the app.
- No hit under `engine/src/main` or `app/src` (only two incidental mentions in
  `StatusEffectsTest.kt` and as a bluff in `FullGamePlaytestTest.kt`).
- The closest thing to support: the generic `Mad` reminder token in
  `app/.../SeatSheet.kt:502` (`ReminderPicker` generic list), and the "Executed"
  button in `app/.../SeatSheet.kt:274-276`.

Storyteller experience: nothing in the app knows a Mutant is in play. There is
no reminder that the storyteller holds an execution power, no way to record that
they are watching a specific player's claims, no marking of "madness broken", no
enforcement that a Mutant execution consumes the day's execution, and no check
that the Mutant is sober enough for the ability to work.

## Defects and gaps

1. **P0** · A drunk/poisoned Mutant can be wrongly executed · The app never
   tells the storyteller that the Mutant is impaired, so a Sweetheart-drunk or
   Poisoner'd Mutant who claims Outsider looks executable. `StatusEffects.isImpaired`
   (`StatusEffects.kt:36-46`) already computes this but is never consulted for
   the Mutant. This is a wrong-outcome bug the wiki explicitly warns about
   (Sweetheart example). · Repro: place `sweetheart:Drunk` on the Mutant; nothing
   changes anywhere.

2. **P0** · A Mutant execution does not consume the day's execution · "Only one
   execution per day is permitted." The app's day model has no concept of "the
   day's execution has been spent": `DayScreen.kt:126-255` will happily run a
   full nomination and offer Execute again, and `GameActions.aboutToDie`
   (`GameActions.kt:296-306`) is purely nomination-derived. · Repro: execute the
   Mutant from the seat sheet in the morning, then run a nomination and execute
   again — the app allows two executions on the same day.

3. **P1** · No storyteller-facing reminder that a Mutant is in play · Unlike the
   Drunk / Lunatic / Marionette / Fortune Teller, which get setup prompts
   (`GameShell.kt:347-479`), the Mutant produces no prompt, no banner, no
   day-start line. The whole ability lives in the storyteller's head. ·
   `GameShell.kt:347-479`.

4. **P1** · No "execute for madness" affordance · The rules describe an
   out-of-band execution, declared to the table, that skips nominations and ends
   the day. The app only has the seat sheet's generic "Executed" button
   (`SeatSheet.kt:274-276`), which records the death but does not end the day,
   does not mark the day's execution as spent, does not offer "proceed to night",
   and does not produce a declaration to read out. · Repro: try to execute a
   day-one Mutant before nominations.

5. **P1** · No way to record what the Mutant claimed · The storyteller's whole
   job here is judging a running public claim. There is no structured place to
   note "Ivan is hinting Outsider — one more and I execute", only free-text
   `storytellerNotes` (`GameShell.kt:685-706`). · Same gap as Klutz/Gossip.

6. **P1** · Cerenovus × Mutant conflict is invisible · If the Cerenovus makes
   the Mutant mad about an Outsider character, the app shows a `cerenovus:Mad`
   token with no character recorded (see `cerenovus.md`) and no cross-check
   against the Mutant. The storyteller has to spot the double bind unaided. ·
   `StatusEffects.kt:162-164` only prints a generic "is Cerenovus-mad" string at
   nomination time.

7. **P2** · No `night_guide` (or any) entry, so the How-to-Run text is absent ·
   `night_guide.json` is keyed by night phase only (`NightGuide.kt:36-40`:
   `first` / `other`), so a day-only character has no home.

8. **P2** · "Even at night" executions are not modelled · `GameActions.kill`
   (`GameActions.kt:136-156`) stamps `atNight = state.phase == Phase.NIGHT` and
   `day = state.cycle`. A Mutant executed during night *n* is stored as
   `day = n, atNight = true`, which `InfoCalc.undertaker`
   (`InfoCalc.kt:281-292`, with `relevantDay` at `:117-118` returning
   `cycle - 1` at night) will not report to that night's Undertaker but *will*
   report to the next night's. Whether that matches the official Undertaker
   ruling is unverified — flag and resolve before implementing.

9. **P3** · The generic `Mad` token is indistinguishable from the Cerenovus's ·
   `SeatSheet.kt:502` offers `PlacedReminder("", "Mad")`, which
   `nominationWarnings` (`StatusEffects.kt:162-164`) will then describe as
   "Cerenovus-mad" regardless of source, because it matches on the label alone.

## Proposed behaviour (spec)

The Mutant has **no night action**. Everything is storyteller-side judgment
support during the day (and, rarely, at night).

### Setup

- when: a seat holds `mutant` and the phase leaves SETUP.
- Add a persistent, low-key **storyteller banner** on the Day tab (and Grimoire
  header) for as long as a living, unimpaired Mutant is in play:
  `Mutant in play (<name>) — you may execute them at any time for claiming
  Outsider.`
- When the Mutant is impaired, the banner instead reads, in a muted style:
  `Mutant (<name>) is drunk/poisoned — their ability does not work; do NOT
  execute them for madness.` This directly consumes
  `StatusEffects.isImpaired(state, lookup, mutantPlayer)`.

### Day-time input the app must record

Extend the shared `PublicClaim` record proposed in `klutz.md`:

- `kind = "mutant-watch"`, `sourcePlayerId = mutantId`, `text` = free text of
  what they said, `day`.
- The Day tab gets a one-tap `Mutant hinted Outsider` button next to the banner
  that appends a timestamped line, so the storyteller builds an evidence trail
  they can point at when they execute (and can show afterwards).

### The execution action

A single `Execute the Mutant now` action, available in **any** phase, that does
all of this atomically:

- immediate effects:
  - `GameActions.kill(state, mutantId, DeathCause.EXECUTION, lookup)` — the
    cause must be `EXECUTION` so the Undertaker, Saint-style checks and the
    game log all behave;
  - set the new day-state flag `executionUsedToday = true` (see below);
  - record `PublicClaim(kind = "mutant-execution", resolved = true)`;
  - produce a **read-aloud declaration card**
    (`ShowCard.Message`): `"<name> has been executed."`
- deferred effects:
  - if `phase == DAY` **and** no nomination has been recorded today
    (`state.nominations.none { it.day == cycle }`), offer
    `Proceed straight to night` which runs `advancePhase` — this is the wiki's
    "proceed to night";
  - if nominations are already under way, close nominations for the day.
- guard: if `StatusEffects.isImpaired(...)` is true for the Mutant, the button
  is still available (storytellers can always override) but is preceded by a
  confirmation: `<name> is drunk/poisoned — the Mutant's ability does not work.
  Execute anyway?`

### New day-scoped state

Add `GameState.executionUsedToday: Boolean` (reset in `advancePhase` on the
`DAY -> NIGHT` edge, `GameActions.kt:258-263`). Set it from:

- a nomination-driven execution (`DayScreen.kt:111-114`, `:350-357`,
  `GameShell.kt:599-604`), and
- a Mutant execution.

`DayScreen` disables both Execute buttons and shows
`Today's execution has already happened (<name>).` when the flag is set. This is
shared machinery, not Mutant-specific — Virgin, Golem and Witch deaths all care.

### Expiry / duration

- Nothing to expire. The Mutant's ability is permanent while alive and sober.
- The banner disappears on death or when the seat stops being the Mutant.

### Information / visibility

- Nothing shown to players except the public execution declaration.
- The Mutant is **never** told they are being watched, never told they are
  drunk.

### Interactions to handle explicitly

- **Sweetheart / Poisoner / No Dashii / Drunk / Vortox-adjacent** — impairment
  disables the ability; surface it (see above).
- **Cerenovus** — when a `cerenovus:Mad` token lands on the Mutant, the app must
  raise a storyteller note naming the madness character: if it is an Outsider,
  `Ivan (Mutant) is mad as the <Outsider> — obeying the Cerenovus makes them
  executable as the Mutant too. Decide which madness you enforce.` This requires
  the Cerenovus to record its chosen character (see `cerenovus.md`).
- **Pit-Hag / Barber** creating a Mutant mid-game: the banner appears from that
  moment; a Mutant who has already claimed Outsider *as another character* is
  not retroactively liable.
- **Travellers** — a Mutant execution does not use the exile mechanism.
- **Jinxes** — none.

### UI text

- Banner: `Mutant in play — <name>. Execute at any time if they push an Outsider
  claim. Uses today's execution.`
- Impaired banner: `Mutant (<name>) is drunk/poisoned — no ability. Do not
  execute for madness.`
- Button: `Execute <name> for madness`
- Confirmation body: `This is today's execution. Announce it to the table, then
  proceed to night if no nomination has happened.`

### Data changes

- `night_guide.json` — add a `mutant` entry once `NightGuideEntry`
  (`NightGuide.kt:36-40`) grows a `day: GuideNight?` field, carrying the
  How-to-Run and the madness definition above.
- `characters.json:986-996` — ability text matches the wiki; no change.

## Tests to add

1. **Given** a living, unimpaired Mutant, **when** the day starts, **then** the
   day-start briefing includes a "Mutant in play — you may execute them" line.
2. **Given** the Mutant carries `sweetheart:Drunk`, **then** the briefing line
   instead says the ability does not work, and
   `StatusEffects.isImpaired(state, lookup, mutant)` is `true`.
3. **Given** the Mutant is executed by the storyteller on day 1 before any
   nomination, **then** `state.executionUsedToday` is `true`, the death record
   has `cause == DeathCause.EXECUTION` and `day == 1`.
4. **Given** `executionUsedToday == true`, **when** a nomination reaches the
   execution threshold, **then** the Execute action is refused/disabled with the
   "already executed today" reason.
5. **Given** `executionUsedToday == true`, **when** `advancePhase` runs
   `DAY -> NIGHT`, **then** the flag resets to `false`.
6. **Given** a Mutant executed on day 2, **when** the Undertaker's info is
   computed on night 3, **then** it reports the Mutant.
7. **Given** a `cerenovus:Mad` token naming an Outsider character placed on the
   Mutant, **then** the engine produces a conflict note naming both abilities.
8. **Given** a Pit-Hag turns a Townsfolk into the Mutant on night 3, **then**
   the Mutant banner appears from day 3 onward.
9. **Given** a generic `PlacedReminder("", "Mad")` (not from the Cerenovus),
   **then** `nominationWarnings` does **not** claim the player is
   "Cerenovus-mad" (fixes `StatusEffects.kt:162-164` matching on label alone).
