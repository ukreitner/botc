# Chef (chef) — Trouble Brewing Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Chef>

Current ability text (matches `characters.json`):

> "You start knowing how many pairs of evil players there are."

How to run (wiki):

> "During night one, show the Chef a number of fingers representing total neighboring evil player pairs."

Mechanics (wiki, verbatim where quoted):

- **Pair counting:** *"Multiple adjacent evil players create multiple pairs—three in a row = 2 pairs, four = 3 pairs, etc. One player can be part of two pairs."*
- **Recluse:** *"The Recluse may register as evil to the Chef despite being good."*
- **Spy:** *"The Spy may not register as evil, potentially giving incorrect counts."*
- **Travellers:** *"Evil Travellers count if they joined before the Chef's first night."*
- Both misregistrations are per-instance storyteller choices (<https://wiki.bloodontheclocktower.com/Recluse>, <https://wiki.bloodontheclocktower.com/Spy>) — and for the Chef the choice is made **once**, for the single number given on night 1.
- The circle wraps (the last seat neighbours the first) — this is the standard seating model and is what "neighbouring" means everywhere else in the game. The wiki page does not restate it.
- The Chef is a **"you start knowing"** character: it fires on night 1 only, when everyone is alive, so the dead-player question does not arise in a stock game.

Jinxes: none for the Chef. Correct — none in the official list, none in the app data.

## What the app does today

Data:

- `characters.json` `chef` — ability current; `firstNightReminder` = *"Show the finger signal (0, 1, 2, …) for the number of pairs of neighbouring evil players."*; `otherNightReminder` empty; `reminders: []`. Correct.
- `night_and_jinxes.json` — `firstNight` index 49 (after Investigator 48, before Empath 50); absent from `otherNight`. Matches the official order. **Works.**
- `night_guide.json` `chef.first` — accurate prose including the "three in a row is 2 pairs" rule and the drunk/poisoned rule. `shows: []`.

Engine — `InfoCalc.kt:186-205`:

```kotlin
private fun chef(ctx: Ctx): InfoResult {
    val seats = ctx.players
    if (seats.size < 2) return InfoResult("0 pairs")
    val evil = seats.map { ctx.isEvil(it) }
    var pairs = 0
    for (i in seats.indices) {
        val j = (i + 1) % seats.size
        if (i == j) continue
        if (evil[i] && evil[j]) { pairs++; pairNames += "…" }
    }
    return InfoResult(headline = "$pairs pair…", detail = "Pairs: …",
                      caveats = misregistrations(ctx, seats))
}
```

- Wraps the circle, counts overlapping pairs correctly (3 in a row → 2), respects `alignmentFlipped` via `ctx.isEvil` (`GameState.kt:49-52`), includes Travellers because it walks `state.players` unfiltered. All correct.
- `misregistrations(ctx, seats)` (`InfoCalc.kt:121-130`) adds one line per Spy and per Recluse anywhere in the game.
- `commonCaveats` (`InfoCalc.kt:158-166`) adds impairment and Vortox notes.

UI:

- `NightScreen.kt:836-863` — no target picker (`targetsNeeded("chef") == 0`); the holder is `step.playerIds.firstOrNull()` and is used only for impairment caveats.
- `NightScreen.kt:886-895` — the headline begins with a digit, so **"Show N full-screen"** appears (including for `0 pairs`). **Works.**
- `NightScreen.kt:903-930` — impaired holders get chips for `0..4` minus the true value.

Storyteller's actual experience: on night 1 they open the Chef row, read *"1 pair of neighbouring evil players"* with *"Pairs: Ana+Bo"* underneath and two red caveat lines about the Spy and the Recluse, then tap **Show 1 full-screen**. Deciding what the number should actually be given the Spy and the Recluse is entirely manual.

## Defects and gaps

1. **P1 · Misregistration is a warning, never an alternative count — and for the Chef this is the whole job.**
   Rules: the Recluse may register evil and the Spy may register good, at the storyteller's choice, for this one number. App: `InfoCalc.kt:203` attaches generic text and leaves the storyteller to recount the circle in their head. In a Baron game with a Recluse and a Spy there are four candidate numbers and the app volunteers none of them. This is the single highest-value fix for the Chef: the app knows every seat's alignment and the seating order, so it should present *"1 (true) · 2 (Recluse registers evil) · 0 (Spy registers good) · 1 (both)"* as tappable answers with the pair names for each.

2. **P1 · The number shown is never recorded.**
   The Chef's number is the one piece of information that anchors an entire Trouble Brewing game, and after night 1 the app has no memory of it. `GameExtras.kt:40-64` (the log) only lists deaths. Nothing supports "what did I tell the Chef?" at day 3, nothing lets the reveal flow show it, and a poisoned Chef's lie is not tracked.

3. **P1 · The caveat list is unscoped.**
   `misregistrations(ctx, seats)` passes **every** seat, so the storyteller gets *"Ana is the Spy — may register as good…"* even when the Spy is nowhere near another evil player and cannot change the count. Compare `empath` (`InfoCalc.kt:214`), which correctly scopes to the neighbours. For the Chef the correct scope is "Spy/Recluse seats that are adjacent to at least one evil (or misregistering) seat", i.e. only the ones that can move the number.

4. **P2 · The false-info chips are a hard-coded `0..4`.**
   `NightScreen.kt:914-921`. For the Chef 0..4 happens to cover most real games, but the bound is arbitrary rather than derived: a large Legion/Riot-style board can legitimately exceed 4, and a 5-player TB game has at most 1 evil pair so 3 and 4 are already implausible tells. The range must come from the calculator (`0 .. maxPossiblePairs`), the same fix the Empath needs more urgently.

5. **P2 · Impairment detection in the UI is a substring match on caveat text.**
   `NightScreen.kt:904-906` misses the `No ability` caveat (`InfoCalc.kt:147`) and the Marionette caveat (`InfoCalc.kt:139-141`). A Marionette-shown-as-Chef gets no false-info row.

6. **P2 · Vortox is a caveat, not a mode.**
   `InfoCalc.kt:162-164` says *"Townsfolk info must be FALSE"* but the UI still makes the true number the primary chip (`NightScreen.kt:890-895`) and the falses secondary.

7. **P2 · `detail` names the true pairs only.**
   `InfoCalc.kt:197,202` builds `"Pairs: Ana+Bo"`. Under misregistration the storyteller also needs the *would-be* pairs (*"Recluse Cara + Imp Dee would be a pair"*) to make a sensible ruling and to remember it later.

8. **P3 · Exactly two seats double-counts a pair.**
   `InfoCalc.kt:191-199`: with `seats.size == 2`, `i = 0 → j = 1` and `i = 1 → j = 0` both fire, so two evil players in a 2-seat grimoire report **2 pairs**. Unreachable in a real game (minimum 5 players) but the guard at `InfoCalc.kt:188` already special-cases `size < 2` and stops one short.

9. **P3 · No handling of a mid-game Chef.**
   `startKnowing`-style "you start knowing" characters can be created mid-game on other scripts (Pit-Hag, Philosopher, Amnesiac). The Chef is only in the `firstNight` list, so a seat that becomes the Chef on night 3 gets **no** night row at all. Out of TB scope, but the generic engine should have an answer.

10. **P3 · Two seats sharing the Chef night role compute one answer.**
    `NightScreen.kt:837` uses `playerIds.firstOrNull()`. Not reachable in stock TB.

## Proposed behaviour (spec)

### Night action

- **when:** **first night only** (order position 49, unchanged). Wake condition: the Chef seat exists. (In a stock game the Chef is necessarily alive on night 1; do not add an alive gate that would break a mid-game Chef.)
- **targets:** none.
- **immediate effects:** none; no tokens.
- **deferred effects:** none.
- **expiry:** n/a.

### Information

Use the same `NumericInfo` shape proposed in `empath.md`:

```
NumericInfo(trueValue, min, max, breakdown, alternatives, impairment)
```

- `trueValue` = wrapping adjacent-evil pair count over **all** seats (unchanged algorithm, plus the `size == 2` fix: iterate `i in 0 until size` and skip the duplicate edge when `size == 2`).
- `min = 0`; `max` = the pair count that results when **every** misregistering seat registers evil — i.e. the largest number the storyteller could legally give. This is the correct bound for both the alternatives list and the false-info chips.
- `breakdown` = the true pairs by name.
- `alternatives` = one entry per subset of *relevant* misregistering seats, where "relevant" means a `recluse` or `spy` seat adjacent to at least one seat that is evil under some considered registration. Each entry carries the resulting count, the pairs it creates or destroys, and a reason string:
  - *"2 — if Cara (Recluse) registers evil: adds Cara+Dee"*
  - *"0 — if Ana (Spy) registers good: removes Ana+Bo"*
  - *"1 — if both"*
  De-duplicate by resulting value, preferring the shortest reason. Cap the list at ~4 options; with more misregistering seats than that, fall back to a min–max range plus a "show any number in 0..N" row.
- `impairment` = `MUST_BE_FALSE` (alive Vortox) / `MAY_BE_FALSE` (drunk, poisoned, the Drunk, Marionette, `No ability`, No-Dashii-poisoned) / `NONE`.

### What the UI shows

- Headline **"<Chef> learns 1 pair"**, subtitle **"Ana+Bo"**.
- Primary chip **"Show 1"**.
- **"Or, depending on how you rule misregistration:"** row with one chip per alternative, each labelled with its number and short reason.
- When impaired, a **"False info — show instead"** row over `min..max` minus the true value, with the Vortox demotion described in `empath.md`.
- A footnote when a Spy or Recluse exists but cannot change the count: **"Cara is the Recluse but has no evil neighbour — registration does not change the number."** (Better than today's unconditional warning.)

### Deferred / bookkeeping

- **Night log:** `NightRecord(cycle = 1, stepId = "chef", holderIds, outcome = "<number shown>", impaired)` plus a `misregistrationRuling` string recording which alternative was chosen. That ruling should then be surfaced on every later step that involves the same Recluse/Spy seat (Empath, Fortune Teller, Undertaker, Ravenkeeper) so the storyteller stays consistent, which is a genuine rules obligation and pure bookkeeping the app can own.
- **Day-time inputs:** none required. Optional: record the Chef's public claim for the reveal flow.

### Data changes

- `characters.json` — none; text is current.
- `night_guide.json` `chef.first` — no change required; optionally add explicit `shows` entries for 0–3 so the number cards exist even before a holder is selected.

## Tests to add

1. `chef counts three in a row as two pairs`
   Given seats 0, 1, 2 evil in a 7-seat circle. Then `trueValue == 2` and `breakdown` lists both pairs.

2. `chef wraps the circle`
   Given the last seat and seat 0 evil and nobody else. Then `trueValue == 1`.

3. `chef does not double count with two seats`
   Given exactly 2 seats, both evil. Then `trueValue == 1` (today: 2).

4. `chef offers a recluse alternative`
   Given `imp` at seat 0, `recluse` at seat 1, no other adjacency.
   Then `trueValue == 0`, `max == 1`, and `alternatives` contains `(1, reason naming the Recluse and the pair it forms)`.

5. `chef offers a spy alternative`
   Given `imp` at 0 and `spy` at 1 (adjacent). Then `trueValue == 1` and `alternatives` contains `(0, reason naming the Spy)`.

6. `chef combines both misregistrations`
   Given `imp` 0, `spy` 1, `recluse` 5, `poisoner` 6 in an 8-seat circle.
   Then the alternatives cover the full reachable set of counts and `max` equals the all-register-evil count.

7. `chef does not warn about an irrelevant spy`
   Given a `spy` with two good, non-misregistering neighbours.
   Then no alternative is produced and the caveat (if any) explicitly says the registration cannot change the number.

8. `chef false info range is derived`
   Given a poisoned Chef with `trueValue == 1` and `max == 2`.
   Then the offered false values are exactly `{0, 2}`.

9. `chef counts evil travellers`
   Given an evil Traveller seated next to the Imp on night 1. Then that pair is counted.

10. `chef respects flipped alignment`
    Given two adjacent good seats, one with `alignmentFlipped = true` next to the Imp. Then the pair count reflects the flip.

11. `chef number shown is written to the night log`
    Given the storyteller flashes a 2 on night 1. Then `state.nightLog` contains `NightRecord(cycle = 1, stepId = "chef", outcome = "2")`.
