# Empath (empath) — Trouble Brewing Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Empath>

Current ability text (matches `characters.json`):

> "Each night, you learn how many of your 2 alive neighbours are evil."

How to run (wiki, verbatim):

> "Each night, wake the Empath. Show them fingers (0, 1, or 2) equaling the number of evil players neighbouring the Empath. Put the Empath to sleep."

Edge cases (wiki, verbatim):

- **Dead neighbours:** *"If the Empath is sitting next to a dead player, they do not get info about that dead player. Instead, they get info about the closest alive player in that direction."*
- **Late game:** *"There are only three players left alive: the Empath, the Imp, and the Baron. No matter who is seated where, the Empath learns a '2'."*
- **Travellers:** *"Travellers who neighbour you count for your information."*
- **Misregistration:** *"Beware the Spy! They may register as good for you…Additionally, the Recluse may register as evil."* Both are per-instance storyteller choices (<https://wiki.bloodontheclocktower.com/Recluse>, <https://wiki.bloodontheclocktower.com/Spy>) and both work **even while dead** — though a dead Spy/Recluse is not an alive neighbour, so for the Empath only living ones matter.
- The legal answer space is exactly **{0, 1, 2}**.

**Uncertain:** the wiki does not state what happens with exactly **two** players alive (the Empath and one other), where both "neighbours" resolve to the same person. The app's behaviour (count that player once, so max 1) matches the common reading; I could not find an official ruling and did not want to guess. Flag for the lead to confirm. In practice the game usually ends at 2 alive.

Jinxes: none for the Empath (none in the official list, none in the app data). Correct.

## What the app does today

Data:

- `characters.json` `empath` — ability current; both night reminders read *"Show the finger signal (0, 1, 2) for the number of evil alive neighbours of the Empath."*; `reminders: []`. Correct — the Empath has no tokens.
- `night_and_jinxes.json` — `firstNight` index 50, `otherNight` index 73. Matches the official order. **Works.**
- `night_guide.json` `empath.first` / `.other` — identical, accurate prose that already spells out the skip-the-dead rule and the drunk/poisoned rule. `shows: []`.

Engine:

- `InfoCalc.kt:168-182` `aliveNeighbours(ctx, player)` — scans outward in both directions from the holder's index, skipping dead seats, wrapping the circle, and `distinctBy { it.id }` so a single alive player reached from both directions counts once. Correct for the dead-neighbour and 3-alive rulings, and includes Travellers because it walks `state.players` unfiltered. **Works.**
- `InfoCalc.kt:207-216` `empath(ctx)` — counts `ctx.isEvil(it)` over those neighbours (which respects `alignmentFlipped`, `GameState.kt:49-52`), builds the headline, lists each neighbour with their alignment in `detail`, and attaches `misregistrations(ctx, neighbours)` — correctly scoped to the neighbours rather than the whole table.
- `InfoCalc.kt:158-166` `commonCaveats` adds impairment notes (`impairments`, `InfoCalc.kt:133-153`: the Drunk, the Marionette, any reminder containing "poison"/"drunk", `No ability`, dead, No Dashii derived poison) and the Vortox note.

UI:

- `NightScreen.kt:836-863` — no target picker (`targetsNeeded("empath") == 0`), so the panel goes straight to the computed result using `step.playerIds.firstOrNull()` as the holder.
- `NightScreen.kt:886-895` — the headline starts with a digit, so a **"Show N full-screen"** chip appears and `ShowCard.NumberCard` renders it (`ShowCards.kt:68,100`). **Works.**
- `NightScreen.kt:903-930` — when impaired, a **"False info to show instead"** row offers `for (n in 0..4) if (n != leadingNumber)`.

Storyteller's actual experience: open the Empath row, read e.g. *"1 of Dana's alive neighbours is evil"* with the two neighbours and their alignments listed underneath, tap **Show 1 full-screen**, done. If Dana is poisoned, four extra chips appear offering 0, 2, 3, 4.

## Defects and gaps

1. **P0 · The false-info chips offer numbers the Empath can never legally see.**
   Rules: the answer space is `{0, 1, 2}`. App: `NightScreen.kt:914-921` hard-codes `for (n in 0..4)`, so a poisoned Empath is offered **3** and **4**. Showing a poisoned Empath a "3" is an instant, unrecoverable tell that they are malfunctioning — the opposite of what false info is for. The bound is a property of the character and must come from the calculator, not from a literal in the UI.
   Repro: poison the Empath → open their night row → the "False info to show instead" row contains chips `0 1 2 3 4` minus the true value.

2. **P1 · Misregistration is a warning, never an alternative answer.**
   Rules: the storyteller decides, per instance, whether a neighbouring Recluse registers evil or a neighbouring Spy registers good. App: `InfoCalc.kt:214` attaches text (*"X is the Recluse — may register as evil…"*) and leaves the arithmetic to the storyteller at 1am on a phone. The app already knows every input; it should present *"0 (true) · 1 if the Recluse registers evil"* as tappable answers.

3. **P1 · Nothing records the number that was actually shown.**
   The result is recomputed from live state each time the row is opened and never written anywhere. `GameExtras.kt:40-64` (the log) shows only deaths. Consequences: after a seat dies the storyteller cannot check what the Empath was told two nights ago; a poisoned Empath's lie is not tracked for consistency; the reveal flow (`RevealFlow.kt`) cannot reconstruct the information game; the Mathematician (`InfoCalc.kt:77-80`) explicitly punts to manual tracking for exactly this reason.

4. **P1 · No dawn/day-start note when the Empath's neighbours change.**
   A death at night silently re-points the Empath. Nothing in the dawn or day-start surface (there is none — `advancePhase` at `GameActions.kt:260` just flips the phase) tells the storyteller *"the Empath's neighbours are now Bob and Cara — their number will change tonight."* This is a small thing individually and exactly the class of bookkeeping the brief asks the app to own.

5. **P2 · The Empath row is presented normally when the Empath is dead.**
   `NightOrder.kt:143-145` includes any character with holders. The row shows *"All holders are dead — usually skip."* (`NightScreen.kt:751-757`) plus a caveat *"X is dead — they normally don't act."* (`InfoCalc.kt:150`) — but it still counts as an unchecked step and blocks the "Dawn" guard (`GameShell.kt:153-160`) until manually ticked.

6. **P2 · Impairment detection in the UI is a substring match on caveat strings.**
   `NightScreen.kt:904-906` checks `"POISONED" in it || "DRUNK" in it || "IS the Drunk" in it || "VORTOX" in it || "No Dashii" in it`. This misses the `No ability` caveat (`InfoCalc.kt:147`) and the Marionette caveat (`InfoCalc.kt:139-141`) — both cases where the Empath must be given arbitrary info and the false-info row is exactly what the storyteller needs. It is also silently coupled to the caveat wording. Impairment should be a boolean on `InfoResult`.

7. **P2 · Vortox is treated as "may be false" rather than "must be false".**
   `InfoCalc.kt:162-164` emits *"VORTOX in play — Townsfolk info must be FALSE"* as one caveat among many, and the UI then renders the true number as the primary chip with the falses beneath. For a Vortox game the true number must never be the easy tap.

8. **P2 · Vortox suppression when the Empath is the Drunk.**
   `commonCaveats` (`InfoCalc.kt:160-164`) computes `holderTeam` from `ctx.character(holder)`, which for a Drunk-shown-as-Empath is the **Drunk** (Outsider), so the Vortox caveat is skipped. Harmless in effect (the Drunk gets false info anyway) but the reasoning is wrong and will bite when this helper is reused.

9. **P3 · Two seats sharing the Empath night role compute one answer.**
   `NightScreen.kt:837` uses `step.playerIds.firstOrNull()`. Not reachable in stock TB (`validateSetupState` at `GameActions.kt:517-520` forbids a Drunk shown a token that is already in play) but the panel is generic.

10. **P3 · The 2-alive case is undocumented and untested.**
    `distinctBy { it.id }` at `InfoCalc.kt:181` makes the maximum 1 when only one other player is alive. Whatever the ruling turns out to be, it should be an explicit, commented decision with a test, not an emergent property of a `distinctBy`.

## Proposed behaviour (spec)

### Night action

- **when:** both first and other nights (order positions unchanged). Wake condition: the Empath seat is **alive**. A dead Empath's step must be rendered greyed/auto-complete so it never blocks dawn.
- **targets:** none — the Empath does not choose.
- **immediate effects:** none; no tokens.
- **deferred effects:** none.
- **expiry:** n/a.

### Information

Replace the string-only `InfoResult` for numeric characters with a structured answer the UI can render without parsing:

```
NumericInfo(
  trueValue: Int,
  min: Int, max: Int,                     // Empath: 0..2
  breakdown: List<String>,                // "Bob (evil)", "Cara (good)"
  alternatives: List<Alternative>,        // misregistration outcomes
  impairment: Impairment                  // NONE | MAY_BE_FALSE | MUST_BE_FALSE
)
Alternative(value: Int, reason: String)   // "1 — if the Recluse registers evil"
```

- `trueValue` = evil count among `aliveNeighbours` (unchanged logic).
- `min = 0`, `max = neighbours.size` — which is 2 in the normal case and **1** when only one other player is alive. This makes the false-info range correct by construction and resolves defect 1 and 10 together.
- `alternatives` = the cross-product over neighbouring `recluse` (may count as evil) and `spy` (may count as good) seats, de-duplicated by value, each with a human reason. With one neighbouring Recluse this is a single extra option; with a Recluse and a Spy neighbouring, three.
- `impairment`:
  - `MUST_BE_FALSE` when an alive Vortox is in play and the holder is Townsfolk-facing;
  - `MAY_BE_FALSE` when the holder is drunk / poisoned / the Drunk / the Marionette / has `No ability` / is No-Dashii-poisoned;
  - `NONE` otherwise.

### What the UI shows

- Headline: **"<Empath> learns 1"**, subtitle **"Bob (evil) · Cara (good)"**.
- One primary chip **"Show 1"**.
- When `alternatives` is non-empty, a secondary row: **"Or, if you rule the Recluse registers evil: [Show 2]"** with the reason inline.
- When `impairment != NONE`, a **"False info — show instead"** row containing exactly `min..max` minus `trueValue` (so `0 2` for a poisoned Empath whose true answer is 1), plus, when `MUST_BE_FALSE`, the true chip is demoted to a small text link and the row header reads **"Vortox — you MUST show a false number."**

### Deferred / day-time

- **Dawn briefing note** (shared surface, see `monk.md`): when a death at night changed the Empath's alive neighbours, add *"<Empath>'s neighbours are now <X> and <Y>."*
- **Night log:** `NightRecord(cycle, "empath", [empathId], [], outcome = "<number shown>", impaired)`. The number recorded must be the one the storyteller actually flashed, not the true one — wire the `ShowCard` tap to write the record.
- **Day-time inputs:** none required by the ability. Optional: a place to record the Empath's public claim each day, which the reveal flow can display alongside the true series.

### Data changes

- `characters.json` — none; text is current.
- `night_guide.json` `empath` — no change required; the prose is already correct. Optionally add `shows` entries for 0/1/2 so the cards exist even when the calculator has no holder selected.

## Tests to add

1. `empath false info range is bounded by the character`
   Given a poisoned Empath at seat 3 with a true value of 1.
   Then the info result exposes `min = 0, max = 2` and the offered false values are exactly `{0, 2}` — never 3 or 4.

2. `empath max drops to one when only two players are alive`
   Given only the Empath and one other seat alive.
   Then `neighbours.size == 1`, `max == 1`, and the answer is 0 or 1 (never 2). *(Confirm the intended ruling with the lead before locking this in.)*

3. `empath offers a recluse alternative`
   Given the Empath at seat 3 with an alive `recluse` neighbour and no other evil neighbour.
   Then `trueValue == 0` and `alternatives` contains `(1, reason naming the Recluse)`.

4. `empath offers a spy alternative`
   Given the Empath with an alive `spy` neighbour and no other evil neighbour.
   Then `trueValue == 1` and `alternatives` contains `(0, reason naming the Spy)`.

5. `empath skips dead neighbours` *(extends the existing `InfoCalcTest` case)*
   Given seats 2 and 1 dead, the Empath at seat 3, and seat 0 evil.
   Then `trueValue == 1` and `breakdown` names seat 0, not seat 2.

6. `empath counts a neighbouring traveller`
   Given an evil Traveller seated next to the Empath.
   Then that Traveller is counted.

7. `empath respects flipped alignment`
   Given a good neighbour with `alignmentFlipped = true`.
   Then they count as evil.

8. `vortox marks empath info as must-be-false`
   Given an alive `vortox`. Then `impairment == MUST_BE_FALSE`, distinct from a poisoned Empath's `MAY_BE_FALSE`.

9. `marionette and no-ability empath are treated as impaired by the UI contract`
   Given the Empath seat holds `marionette` (shown Empath), and separately given a `No ability` reminder.
   Then `impairment == MAY_BE_FALSE` in both cases (today `NightScreen.kt:904-906` would not light the false-info row).

10. `empath number shown is written to the night log`
    Given the storyteller flashes a 2 on night 3. Then `state.nightLog` contains `NightRecord(cycle = 3, stepId = "empath", outcome = "2")`.

11. `dead empath step does not block dawn`
    Given a dead Empath on night 4. Then the dawn guard does not list the Empath step as unfinished.
