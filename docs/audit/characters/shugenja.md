# Shugenja (shugenja) — Experimental Townsfolk

## Official rules (sources)

Source: https://wiki.bloodontheclocktower.com/Shugenja (fetched 2026-08-25).

Current ability text (matches `characters.json` exactly — no drift):

> "You start knowing if your closest evil player is clockwise or anti-clockwise. If equidistant, this info is arbitrary."

How to Run (quoted verbatim):

> "During the first night, wake the Shugenja. If the closest evil player is in a clockwise direction, point your finger horizontally in that direction. If the closest evil player is in an anti-clockwise direction, point your finger horizontally in that direction. If the two closest evil players are equidistant, point your finger horizontally in either direction. Put the Shugenja to sleep."

Clarifications (verbatim):
- "The closest evil player is the player with the smallest number of steps from the Shugenja to the evil player."
- "If the evil players are equidistant, the storyteller gives 'arbitrary' information to the Shugenja. This means that the Storyteller chooses whether to tell the Shugenja that the closest evil player is clockwise or anti-clockwise. **The Shugenja doesn't know whether their information is arbitrary or not.**"
- "The Shugenja does not learn how many steps away the evil player is."
- Signal method: the Storyteller points a finger horizontally in the direction.

Examples:
- Organ Grinder 2 steps clockwise, Fearmonger 3 steps anti-clockwise → learns **clockwise**.
- Marionette 1 step clockwise, Widow 1 step anti-clockwise → Storyteller arbitrarily indicates clockwise.

Not addressed on the wiki (flagged, not guessed):
- Whether **dead** players are counted as steps. Irrelevant on night 1 (nobody is dead), relevant only for a Shugenja created mid-game. The general rule that dead players are still players and still occupy a seat argues for counting them; the page does not say so.
- Misregistration. Nothing on the page, but the general rules apply: a **Recluse** may register as evil (and would then become the closest evil player), a **Spy** may register as good (and would then be skipped). The Storyteller chooses per-instance.

Jinxes: **none.** The wiki lists no jinxes for the Shugenja, and the app's data correctly has none.

## What the app does today

Data / order:
- `characters.json:1565` — ability text current, `firstNightReminder: "Point clockwise or anticlockwise."`, `otherNightReminder: ""`, no reminders. All correct.
- `night_and_jinxes.json:356` — first-night slot 61, between Balloonist and Village Idiot. Correct; no other-night entry, correct.
- `night_guide.json:1205` — accurate first-night prose including the equidistant and impaired cases. `shows: []` — **no show cards at all.**

Runtime — this is the best-implemented character in the group:
- `InfoCalc.supports` includes `shugenja` (`InfoCalc.kt:30`), `targetsNeeded == 0`.
- `InfoCalc.shugenja` (`InfoCalc.kt:243-269`) walks outward from the holder's seat in both directions simultaneously and reports the first evil hit each way. It correctly:
  - excludes the Shugenja themselves (loop starts at `step = 1`);
  - wraps around the circle;
  - uses `Player.isEvil(lookup)` so `alignmentFlipped` seats (Bounty Hunter's evil Townsfolk, a turned Cult Leader, an evil Recluse the ST has flipped) count;
  - handles "no evil at all", exact equidistance, and one-sided cases;
  - reports the step counts so the ST can sanity-check.
- `commonCaveats` (`InfoCalc.kt:158-169`) adds drunk/poison/No-Dashii/Drunk-character impairment notes and the Vortox "Townsfolk info must be FALSE" note.
- `misregistrations(ctx, seats)` (`InfoCalc.kt:121-131`) lists every Spy and Recluse in the game.
- The app's clockwise convention (increasing index in `state.players`) matches `DayScreen`'s vote order (`DayScreen.kt:163-168`, "counted clockwise starting left of the nominee"), so "clockwise" is consistent across the app.
- Covered by `InfoCalcTest.kt:68-72` (`shugenja reports nearest evil direction`).

Storyteller experience today: expand the row, read `Closest evil is ANTI-CLOCKWISE (2 steps)`, then physically point. **The computation works.** The gaps are around it.

## Defects and gaps

1. **P1 · No way to actually deliver the answer on a phone.** Every other info role gets a full-screen card (`NumberCard`, `AlignmentCard`, `Message`, `CharacterCard` — `ShowCards.kt:65-77`). The Shugenja gets none: `night_guide.json:1205` has `"shows": []`, and the auto-generated chips in `StepDetailPanel` only fire for a leading digit or a YES/NO headline (`NightScreen.kt:889-903`). The ST must put the phone down and point. On the PWA this is the single most-used delivery affordance in the app, and this character is excluded from it.
2. **P1 · Impaired Shugenja gets a dead-end "False info" panel.** `NightScreen.kt:904-930`: `impaired` is computed from the caveat text and is `true` for a poisoned/drunk/Vortox Shugenja, so the red heading **"False info to show instead:"** renders — but `leadingNumber` is null (the headline starts with "Closest") and `isYes`/`isNo` are false, so the chip row below it is **empty**. The ST is told there is false info to give and offered nothing. Repro: poison the Shugenja, open their night 1 row.
3. **P1 · The equidistant case is a decision the app leaves unrecorded.** The headline says "point either direction", but nothing lets the ST *make* the choice, and nothing records which way they pointed. Two nights later, when the Shugenja publicly claims "anti-clockwise", the ST has no record of what they actually said — and for an arbitrary/false answer that record is the only thing that keeps the game consistent. There is no "info given" log anywhere in the app (cross-cutting; the same gap hits every info role and the user's Gossip complaint).
4. **P2 · Misregistration is listed but not applied.** `misregistrations` prints "Ana is the Recluse — may register as evil / a Minion or Demon." but the calculator does not offer the alternative answer. For the Shugenja this matters more than for most: a Recluse sitting 1 step clockwise **flips the whole answer**, and working that out by hand under time pressure is exactly what the app exists to avoid. `InfoCalc.kt:243-269`.
5. **P2 · The Spy/Recluse notes fire even when they are irrelevant.** `misregistrations(ctx, seats)` is passed **all** seats, so the note appears even when the Recluse is 6 steps away on the far side and could not change the answer. Same over-warning affects `steward`, `knight`, `noble`, `chef`.
6. **P2 · Dead players and Travellers are counted with no explanation.** The loop walks `ctx.players` (all seats, alive or dead, traveller or not). That is almost certainly correct, but the rule is unstated on the wiki, and for a mid-game Shugenja (Pit-Hag/Amnesiac) it silently makes a judgement call the ST cannot see. Add a caveat naming the assumption rather than hiding it. `InfoCalc.kt:245-253`.
7. **P2 · A mid-game Shugenja never gets their info.** "You start knowing…" characters created after night 1 learn on the night they are created. `shugenja` appears only in the `firstNight` order (`night_and_jinxes.json:356`) and not in `otherNight`, so `NightOrder.build(isFirstNight = false)` emits **no row at all** — the calculator is correct but unreachable. Cross-cutting with Steward/Pixie/Washerwoman/etc.
8. **P3 · `index < 0` is not guarded.** `InfoCalc.kt:246` — `seats.indexOfFirst { it.id == holder.id }` can be `-1` if a stale holder id survives a seat removal; the modular arithmetic then reads from the wrong seats rather than returning "select the Shugenja's seat first". Compare `aliveNeighbours` (`InfoCalc.kt:168-171`), which does guard.
9. **P3 · "Equidistant (N steps each way)" is shown even when both directions reach the same single player** (an even-numbered circle with one evil directly opposite). Technically correct, cosmetically odd; worth phrasing as `The only evil player is directly opposite — either direction is true.`

## Proposed behaviour (spec)

### Night step
- **when:** first night only. Also on the night a Shugenja is **created mid-game** (shared "re-run first-night info" mechanism).
- **wake condition:** Shugenja seat exists and is alive.
- **targets:** none.
- **immediate effects:** none; no tokens.
- **expiry:** n/a.
- **information — computed:** keep `InfoCalc.shugenja` as the core, with these additions:
  1. Return a structured result, not just a headline: `direction: CW | CCW | EITHER | NONE`, `cwSteps`, `ccwSteps`, `cwPlayer`, `ccwPlayer`. The UI needs the direction as a value to render a card and to record what was said.
  2. **Misregistration alternatives.** For every Recluse in the circle that is *closer than the current answer in its direction*, and every Spy that *is* the current answer, compute the flipped result and add a caveat of the form:
     `If <Name> (Recluse, 1 step clockwise) registers as EVIL, the answer becomes CLOCKWISE.`
     `If <Name> (Spy, 2 steps anti-clockwise) registers as GOOD, the answer becomes CLOCKWISE (3 steps).`
     Only emit notes for misregistering players that can actually change the answer (fixes D4 and D5 together).
  3. **State the counting assumption** as a caveat when any seat is dead or a Traveller: `Counting all seats, including dead players and Travellers.`
  4. Guard `index < 0` and return `"Select the Shugenja's seat first"`.
  5. Equidistant headline: `Equidistant (<n> steps each way) — YOUR CHOICE. <CWName> clockwise, <CCWName> anti-clockwise.`
- **information — shown:** add two new show cards / one new `ShowCard` variant:
  - `ShowCard.DirectionCard(clockwise: Boolean)` in `ShowCards.kt:65-77` — a full-screen arrow (↻ / ↺) plus the words `CLOCKWISE` / `ANTI-CLOCKWISE`. This is the piece that makes the character usable on a phone.
  - In `night_guide.json:1205`, add `"shows": [{"label":"Clockwise","kind":"clockwise"},{"label":"Anti-clockwise","kind":"anticlockwise"}]` and extend `NightGuide.VALID_KINDS` (`NightGuide.kt:43`) accordingly, so the two chips are always present and the ST can deliberately pick either — which is exactly what the equidistant and impaired cases need.
  - The computed answer should **highlight** the matching chip (`Clockwise ✓ (true answer)`) and leave the other plain, so a deliberate lie is a deliberate tap.
- **impaired / false alternative:** when the Shugenja is drunk, poisoned, No-Dashii-adjacent, is the Drunk, or a Vortox is in play, the panel must show `False info to show instead:` **followed by the opposite direction chip** — never an empty row. Generalise `NightScreen.kt:904-930` so the "false alternative" set is supplied by the calculator (`InfoResult.falseAlternatives: List<ShowCard>`) instead of being inferred from the headline string. That fixes the same empty-panel bug for every non-numeric, non-yes/no info role.
- **record what was said:** when the ST fires a direction card, append to an `infoGiven` log on `GameState`:
  `InfoGiven(cycle, playerId, characterId = "shugenja", text = "ANTI-CLOCKWISE", wasTrue = false)`.
  Surface it in the seat sheet and in the game log (`GameExtras.kt`). This is the generic fix the user is asking for when they say "make it easy to write down all the gossips" — the app should be the notebook.
- **visibility:** nothing is shown to evil players.
- **day-time inputs:** none.
- **interactions/jinxes:** none. Vortox is handled by the existing caveat; Recluse/Spy by the new alternatives.

### UI text the step should display
- Row detail: `Point clockwise or anti-clockwise to the nearest evil player.`
- Computed line: `Closest evil is ANTI-CLOCKWISE (2 steps) — <Name>.` / `Equidistant (1 step each way) — your choice.` / `No evil players in the circle.`
- Chips: `» Clockwise ↻`  `» Anti-clockwise ↺` (true one badged).
- Caveats as specified above.

### Data changes
- `night_guide.json:1205` — add the two direction show cards; keep the prose.
- `NightGuide.VALID_KINDS` — add `"clockwise"`, `"anticlockwise"` (note `ScriptParserTest.kt:150-158` validates every guide entry's kinds against this set, so the test will enforce the pairing).
- `characters.json:1565`, `night_and_jinxes.json:356` — no changes.

## Tests to add

1. `shugenja offers the flipped answer when a recluse could register evil` — Given a 9-seat circle, Shugenja at 0, Recluse at 1 (clockwise), real Minion at 6 (3 steps anti-clockwise); Then the headline is `ANTI-CLOCKWISE (3 steps)` **and** a caveat reads that if the Recluse registers evil the answer becomes CLOCKWISE. (Today: only a generic "may register as evil" note.)
2. `shugenja ignores a far-away recluse` — Same but the Recluse sits 4 steps clockwise with a Minion 1 step anti-clockwise; Then no flip caveat is produced. (Today: a note is produced regardless.)
3. `shugenja offers the flipped answer when the spy is the answer` — Given the closest evil in the answer direction is the Spy; Then a caveat gives the answer with the Spy skipped.
4. `shugenja exposes a structured direction` — `InfoCalc.compute(..., "shugenja", holderId)` returns `direction == CCW` (not just a string), so the UI can render a card.
5. `impaired shugenja gets a false alternative` — Given the Shugenja is poisoned and the true answer is CLOCKWISE; Then `result.falseAlternatives` contains the ANTI-CLOCKWISE card. (Today: the panel renders an empty chip row.)
6. `equidistant shugenja is flagged as a storyteller choice` — Given evil 2 steps each way; Then `direction == EITHER` and both player names appear in the detail.
7. `shugenja with a stale holder id does not read the wrong seats` — Given `holderId` refers to a removed seat; Then the headline is the "Select the Shugenja's seat first" prompt, not a computed direction. (Today: `index == -1` silently offsets the whole scan.)
8. `shugenja counts dead players` — Given the nearest clockwise evil player is dead and the nearest anti-clockwise evil player is alive but further; Then the answer is CLOCKWISE and a caveat states that dead seats were counted.
9. `shugenja night guide exposes direction cards` — `NightGuide.forStep("shugenja", true)!!.shows.map { it.kind }` contains `"clockwise"` and `"anticlockwise"`.
