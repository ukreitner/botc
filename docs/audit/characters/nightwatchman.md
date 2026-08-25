# Nightwatchman (nightwatchman) — Experimental (Carousel) Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Nightwatchman> (fetched 2026-08-25).

**Current ability text (wiki):**
> "Once per game, at night, choose a player: they learn you are the Nightwatchman."

`characters.json:1476` matches exactly — **no drift**. Note it says "choose a player",
**not** "a living player" (contrast the Huntsman) — a dead player is a legal target.

**How to Run (wiki):**
> Each night, wake the Nightwatchman. They either shake their head or point at any player,
> then are put to sleep. If they pointed at someone, wake that player and show them the
> Nightwatchman token while pointing to the Nightwatchman player. Put the chosen player to
> sleep. The Nightwatchman loses their ability — mark them with a **NO ABILITY** reminder and
> remove their night token.

Timing / edge cases:

- **Both nights** (first-night position 64, other-night position 87). The player chooses
  which night to spend it.
- **The two players never make eye contact** — they are woken separately, Nightwatchman
  first, then the target.
- **The ability is spent the moment they point**, and the night token comes off the sheet.
- **Drunk or poisoned Nightwatchman:** "A drunk Nightwatchman has no ability, so chosen
  players don't wake or learn anything." The use is still spent.
- **Vortox:** "If Vortox is in play, the chosen player receives false information about
  identity" — i.e. show the chosen player a *different* character token.
- The target learns **which player** is the Nightwatchman, so naming/indicating the seat is
  the point of the step, not an accident.
- **Jinxes:** none listed on the page, and none in `night_and_jinxes.json`.
- Bluffing note (relevant to day handling): the character is hard to bluff because the
  *chosen player* initiates the reveal during the day, not the claimant.

## What the app does today

- `characters.json:1476-1489`: `reminders: ["No Ability"]`; first- and other-night reminders
  both read "The Nightwatchman might choose a player. Put the Nightwatchman to sleep. Wake
  the target. Show the 'This player is' & Nightwatchman tokens, then point to the Nightwatchman."
- Night order: `night_and_jinxes.json:359` (first night, index 64) and `:460` (other nights,
  index 87). Both match the official sheet.
- `night_guide.json:1102-1125` — good prose for both nights, including the drunk/poisoned
  case, plus a show card
  `{"label":"Show target","kind":"token","text":"THIS PLAYER IS","token":"self"}` which
  `GuideShowDialog` (`NightScreen.kt:366-451`) pre-fills with the Nightwatchman token
  (`NightScreen.kt:377-379`, `token == "self"` → `stepCharacterId`).
- `NightToolTray` (`NightScreen.kt:193-352`) shows a **"Mark spent"** chip, because the
  ability text starts with "Once per game" (`NightScreen.kt:207`, `:259-273`); it places
  `PlacedReminder("nightwatchman","No ability")`. It also shows a "Show token" chip that
  produces `ShowCard.CharacterCard("THIS PLAYER IS", "nightwatchman")` (`NightScreen.kt:249-254`)
  — functionally the same card as the guide's.
- `QuickResolutions` (`NightScreen.kt:462-527`) has no `"nightwatchman"` branch.
- `InfoCalc.supports("nightwatchman")` is false (`InfoCalc.kt:30-35`), so the caveats block
  (`NightScreen.kt:835-933`) never renders for this step.
- `NightStepRow` (`NightScreen.kt:749-755`) does show "All holders are dead — usually skip."
  when the Nightwatchman is dead — **works**.

Storyteller experience: every night, a Nightwatchman row appears. The ST wakes them, notes
the choice mentally, taps "Mark spent", closes the sheet, walks to the chosen player, wakes
them, taps "» Show target" (or "Show token"), shows the phone, and points. On night 2 the
row is back, unchecked, with the same text and the "Mark spent" chip gone (correctly hidden
by `NightScreen.kt:265-267`), and the ST has to remember why.

## Defects and gaps

1. **P1 · The step keeps appearing after the ability is spent.**
   The rules say "remove their night token from the night sheet". `NightOrder.build`
   (`NightOrder.kt:130-181`) does not look at reminders, so a spent Nightwatchman produces an
   unchecked row every night, and the Dawn guard (`GameShell.kt:145-158`) refuses to advance
   until it is ticked.
   *Repro:* Night 1 tap "Mark spent"; Dawn; Dusk → the Nightwatchman row is back.

2. **P1 · No resolver — the target is never captured.**
   The two halves of the ability (spend, then show the target) are three separate manual
   actions in two different UI surfaces, with no record of **who** was chosen. When the
   target reveals it during the day, the ST has nothing to check against; after an
   undo/redo the choice is gone entirely.

3. **P1 · No impairment warning on the step.**
   `InfoCalc.impairments` (`InfoCalc.kt:132-153`) already covers poison, drunkenness, the
   Drunk, the Marionette and "No ability" tokens, but it is only reachable through
   `InfoCalc.compute` for `supports()` characters. A **poisoned Nightwatchman** must not wake
   the target at all — the app shows no warning and the guide prose is buried below the
   fold.
   *Repro:* Poison the Nightwatchman, expand the step — no red banner; the "Show target" card
   is offered exactly as usual.

4. **P2 · The show card cannot point at a seat.**
   The whole point is that the target learns *which player* is the Nightwatchman, but
   `ShowCard.CharacterCard("THIS PLAYER IS", "nightwatchman")` shows only the character token
   (`ShowCards.kt:67`). On a phone handed to the chosen player, the ST then has to point at
   a seat in a dark room. The card should read `Ana is the Nightwatchman` (seat name +
   token), which leaks nothing the target is not meant to know.

5. **P2 · Vortox is not handled.**
   With a Vortox in play the chosen player must be shown a *false* identity. There is no
   caveat (defect 3's root cause) and no "show a different character" affordance. The generic
   "False info to show instead" helper (`NightScreen.kt:880-931`) only offers numbers and
   YES/NO, so it produces nothing here even if the caveats block did run.

6. **P2 · Nothing says the target may be dead.**
   `characters.json:1476` and the wiki both say "a player", but the guide prose
   (`night_guide.json:1104`) says "point at a player" without noting that dead players are
   legal and are woken. STs routinely assume "alive" by default.

7. **P2 · No day-time record of the reveal.**
   When the chosen player comes out with "I was woken and shown the Nightwatchman token, it's
   Ana", the ST needs to confirm from their own records whether that is true (and whether the
   Nightwatchman was poisoned that night, making it false). There is no claim recorder.

8. **P3 · Two near-duplicate show affordances.**
   The tray's "Show token" chip (`NightScreen.kt:249-254`) and the guide's "» Show target"
   card (`night_guide.json:1108-1114`) render essentially the same card. Keep one.

9. **P3 · "Mark spent" is offered without a target.**
   The chip appears for the whole step, so it is easy to tap it after the Nightwatchman
   *declines* — an irreversible-feeling mistake (recoverable only via the seat sheet's
   tap-to-remove reminder list, `SeatSheet.kt:318-330`).

## Proposed behaviour (spec)

**Structured night step**

- **when:** both first and other nights.
  Wake condition: holder **alive** and holder has no `("nightwatchman","No ability")`
  reminder. If spent, emit no row (or a collapsed auto-done row reading
  `Nightwatchman — ability used on night 2 (showed Bo)`), matching "remove their night token".
- **targets:** 0 or 1. Constraint: **any player, alive or dead**. Picker default: alive seats
  first in seat order, dead seats shown but visibly marked `†` and still selectable
  (`NightScreen.kt:610-620` already renders dead chips this way). Offer an explicit
  `Declined — no choice` button that ticks the step and leaves the ability intact.
- **immediate effects, on confirm:**
  1. place exclusive `("nightwatchman","No ability")` on the Nightwatchman;
  2. record `(cycle, targetPlayerId)` for the log and the step's spent-row text;
  3. if the Nightwatchman is impaired (`StatusEffects.isImpaired`, the Drunk, the Marionette,
     or already holding a "No ability" token): stop, and show in red
     `! The Nightwatchman is POISONED — do NOT wake Bo. They learn nothing. The use is spent.`
  4. else present the reveal card in one tap.
- **information / show cards:**
  - Normal: `ShowCard` reading `THIS PLAYER IS` + the Nightwatchman token, **plus the seat
    name** — e.g. a subtitle "Ana". Instruction line: `Wake Bo (do not let them see Ana wake).
    Show this, then point to Ana.`
  - **Vortox in play:** red caveat `VORTOX — the Nightwatchman's info must be FALSE: show Bo a
    different character token (and/or point at a different player).` and offer a token picker
    over the script (not-in-play characters first — the opposite of the current in-play-first
    sort at `NightScreen.kt:405-412`).
  - **Impaired:** no card is offered at all; only the "do not wake the target" banner.
- **deferred effects:** none.
- **expiry:** `("nightwatchman","No ability")` never expires — do not add it to
  `EXPIRES_AT_DAWN` / `EXPIRES_AT_DUSK` (`GameActions.kt:218-242`).
- **visibility:** the chosen player learns the Nightwatchman's identity. Nothing is shown to
  the Demon, the Minions or the Lunatic. If the chosen player is evil, that is the
  Nightwatchman's problem — the app must not warn or hesitate.
- **day-time inputs:**
  - a claim recorder for "X says they were shown the Nightwatchman is Y", with a one-line
    verdict from the app: `Matches your record (night 2, Ana → Bo)` /
    `Does not match — no Nightwatchman reveal has happened` /
    `Matches, but the Nightwatchman was poisoned that night`;
  - because the reveal is initiated by the target, the day briefing should remind the ST on
    the morning after the reveal: `Bo learned who the Nightwatchman is last night.`
- **interactions/jinxes:** none.

**UI text**

- Idle: `Wake the Nightwatchman (Ana). They may point to any player — alive or dead — or decline. Any choice spends the ability.`
- After pick: `Ana chose Bo. Put Ana to sleep first, then wake Bo.` → `» Show Bo: "Ana is the Nightwatchman"`
- Impaired: `! Ana is DRUNK — do not wake Bo. The use is still spent.`
- Spent row: `Nightwatchman — used on night 2 (Bo learned Ana).`

**Data changes**

- `night_guide.json:1102-1125` — add: "The chosen player may be dead." and "Wake them
  separately — the two must not make eye contact." and the Vortox clause.
- `night_guide.json:1108` / `:1120` — change the show card so it carries the Nightwatchman's
  seat name as well as the token (needs a small `GuideShow` extension, or simply build the
  card in the resolver rather than from the guide).
- `characters.json` — no change.
- Night order — no change.

## Tests to add

1. `GIVEN` a Nightwatchman marked `("nightwatchman","No ability")` `WHEN` the night sheet is
   built for cycle 2 `THEN` no `nightwatchman` step is produced (or it is flagged spent).
   *Fails today.*
2. `GIVEN` a sober Nightwatchman choosing a player `WHEN` the resolver runs `THEN` the
   Nightwatchman holds exactly one `("nightwatchman","No ability")` reminder and the choice
   is recorded with the cycle.
3. `GIVEN` a poisoned Nightwatchman choosing a player `THEN` the ability is still marked
   spent, the result reports "target learns nothing", and no reveal card is produced.
   *Fails today* (no impairment path at all).
4. `GIVEN` a Nightwatchman `WHEN` the step's caveats are computed while the holder is
   poisoned `THEN` the caveats contain the poison line. *Fails today* —
   `InfoCalc.compute(..., "nightwatchman", ...)` returns null.
5. `GIVEN` a Vortox in play `THEN` the step's caveats include the "must be false" instruction
   and the offered token list excludes the Nightwatchman by default.
6. `GIVEN` a Nightwatchman choosing a **dead** player `THEN` the action is accepted (no
   alive-only constraint).
7. `GIVEN` a Nightwatchman who declines `THEN` no "No ability" reminder is placed and the
   step reappears the following night.
8. `GIVEN` a spent Nightwatchman `WHEN` `advancePhase` runs through dawn and dusk `THEN` the
   "No ability" reminder is still present (regression guard against the expiry tables).
