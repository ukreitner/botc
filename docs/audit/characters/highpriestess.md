# High Priestess (highpriestess) — experimental (Carousel) townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/High_Priestess>

Current ability text (verbatim):

> "Each night, learn which player the Storyteller believes you should talk to
> most."

`characters.json:1396` matches exactly.

### How to run (verbatim)

> "Each night, wake the High Priestess. Point to a player. Put the High
> Priestess to sleep."

### Summary / clarifications (verbatim)

- "The High Priestess can be shown the same player multiple times in a row, or
  a different player every night."
- "The shown player can be alive or dead."
- "The shown player can be good or evil."
- "There are no official criteria that determine which player the Storyteller
  must show to the High Priestess. It is up to the Storyteller's judgement as
  to what they think will most benefit the High Priestess and the good team in
  general."

### Examples (verbatim summaries)

- Night 1: Julian (the Chef). Night 2: Marianna (the Goblin). Night 3: Doug
  (the Drunk).
- "For three nights in a row, the High Priestess learns Sarah. Sarah is the
  Saint" — the good team keeps trying to execute her; on night 4 the High
  Priestess learns Lewis (the Imp).

### Tips & Tricks (player-facing, but load-bearing for the storyteller)

- A **repeat** is meaningful: "Repeats suggest the previous conversation didn't
  achieve the Storyteller's goal." The storyteller therefore has to know what
  they showed on previous nights, and has to mean something by repeating.
- The player is told to weigh whether the game favours good or evil when
  interpreting the pointer — so the answer should be chosen with that reading
  in mind.

### Timing

- **Every night, including the first.**
- In the app: first-night index 68 (after the Ogre, before the General),
  other-night index 91 (after the Spy, before the General). I could not
  cross-check this against a second source — the High Priestess post-dates the
  `bra1n/townsquare` `roles.json` snapshot (it has no `highpriestess` entry),
  and the wiki page does not state a night-order position. **Flagged as
  unverified**; the relative placement (very late, just before the General and
  Chambermaid) is consistent with every other "storyteller judgement" role, so
  it is probably right.

### Jinxes

None on the page. The app's data agrees (no `highpriestess` jinx entries).

### Not settled by the wiki (flagged)

- Whether the High Priestess can be shown **themselves**. Not addressed.
- What a drunk or poisoned High Priestess is shown. Not addressed. The app's
  `night_guide` asserts "you may point to any player", which is the standard
  convention for a judgement ability with no true answer — flagged as
  convention, not a quoted rule.

## What the app does today

Works, in one line each:

- Night-order position on both nights (`night_and_jinxes.json:363` first,
  `:464` other) — placement plausible, see caveat above.
- `night_guide.json:1026` has `first` and `other` instructions covering the
  wake, the judgement basis, "your answer may change from night to night", and
  the impairment convention.
- `characters.json:1396` carries the official reminder "Point to a player." on
  both nights and no reminder tokens — correct.

Storyteller experience:

- A "High Priestess" row appears every night near the end of the sheet, detail
  "Point to a player."
- Expanding it prints the guide paragraph. `night_guide` gives it
  `shows: []`, so there are **no chips at all** — the panel below the prose is
  empty.
- `InfoCalc.supports` does not include `"highpriestess"`
  (`InfoCalc.kt:29-36`), so there is no player picker, no computed anything, no
  caveats.
- The storyteller decides in their head, physically points across the table,
  and ticks the box. Nothing is recorded, anywhere.

## Defects and gaps

1. **P1 · The choice cannot be recorded, so the storyteller cannot honour the
   "repeat means something" rule.** The character's entire texture is the
   sequence of pointers across nights, and the wiki tells the *player* to read
   repeats as signal. The app stores nothing: `nightStepsDone`
   (`GameState.kt:106`) is a set of ids, `InfoCalc` targets are
   `rememberSaveable(step.id)` UI-local state (`NightScreen.kt:839`) that is
   discarded, and `GameLogDialog` (`GameExtras.kt:46-106`) logs only deaths and
   nominations. The only place to write it is the single undated
   `storytellerNotes` blob (`GameShell.kt:684-705`). Repro: point at Sarah on
   nights 1–3; on night 4 the app cannot tell you that you have shown Sarah
   three times.

2. **P1 · There is no picker, so the choice is not even expressible.** Every
   `InfoCalc`-supported "choose a player" role gets target chips
   (`NightScreen.kt:841-861`); the High Priestess gets nothing, because the
   chips are gated on `InfoCalc.supports(step.id)` (`NightScreen.kt:836`).

3. **P1 · No impairment warning on the step.** Same `InfoCalc.supports` gate.
   A drunk or poisoned High Priestess is flagged nowhere in the UI; the rule
   is one sentence at the end of a prose paragraph.

4. **P1 · No decision support.** "It is up to the Storyteller's judgement as
   to what will most benefit the High Priestess and the good team" — and the
   app holds exactly the state that judgement needs: who is impaired
   (`StatusEffects.isImpaired`), who has been given false information, who is
   mad (`("cerenovus","Mad")` / `("harpy","Mad")` reminders), who is
   misregistering (Spy/Recluse, `InfoCalc.misregistrations`), who is on the
   block, who has already been shown to the High Priestess, and who is
   claiming what (once a day-claims store exists). None of it is surfaced at
   the step.

5. **P2 · No `shows` card.** "Point to a player" is a physical gesture that
   does not survive a dark room and a phone-first PWA. There is no
   seat-oriented `ShowCard` at all — `ShowCard` covers messages, numbers,
   alignments, character tokens, bluffs and character sheets
   (`ShowCards.kt:65-77`) but not "this player". The Choirboy has the same
   need.

6. **P2 · A dead High Priestess still gets a night row.**
   `NightOrder.build` does not filter by `alive` (`NightOrder.kt:142-178`);
   the row is only annotated "All holders are dead — usually skip"
   (`NightScreen.kt:751-757`) and the dawn guard (`GameShell.kt:145-158`)
   still demands a tick.

7. **P2 · The step detail is uselessly terse.** "Point to a player." is the
   whole collapsed row (`NightOrder.kt:147-148`); it does not say what the
   question is, which is the one thing a tired storyteller needs at 2am.

8. **P3 · Night-order placement unverified.** See the caveat above. Worth one
   human check against a current printed night sheet before anyone builds on
   it.

9. **P3 · `first` and `other` guide entries are near-identical.**

## Proposed behaviour (spec)

### A. Night step (structured form)

- **when**: `first` and `other` — every night.
- **wake condition**: the High Priestess is **alive**. Emit no row for a dead
  one.
- **targets**: exactly **1 player**, chosen by the **storyteller** (not the
  player). Constraints:
  - any player may be chosen — **alive or dead, good or evil** (the wiki says
    so explicitly); do not filter;
  - the High Priestess themselves: allow, but flag with
    `"Pointing at the High Priestess themselves is not covered by the rules —
    your call."`;
  - Travellers: allow.
  Picker default/sort — this is where the app earns its keep:
  1. players the High Priestess has **not** been shown yet, ahead of repeats;
  2. within those, players currently carrying something the storyteller might
     want steered: impaired (`isImpaired`), mad (`("cerenovus","Mad")` /
     `("harpy","Mad")`), misregistering (Spy/Recluse), on the block, or holding
     a strong unshared claim;
  3. then everyone else, in seat order.
  Each chip is annotated with what the app knows: `Ben — Empath (poisoned)`,
  `Sarah — Saint · shown N1, N2, N3`, `Doug — Drunk (believes Washerwoman)`.
- **immediate effects**: none — no tokens, no state change other than the
  record below.
- **deferred effects**: none.
- **expiry**: none.
- **information**: nothing to compute. `InfoCalc` should **not** manufacture an
  answer; the step is a judgement with a picker. But the caveat rendering
  (impairment, dead-holder, Vortox-not-applicable) must be lifted out of the
  `InfoCalc.supports` gate so it shows here — see the cross-cutting note.
- **impaired alternative**: head the step
  `"The High Priestess is DRUNK/POISONED (<source>) — point to any player."`
  and keep the same picker; there is no true answer to invert.
- **visibility**: nothing shown to anyone else.
- **day-time inputs consumed**: none required. The claims store (Gossip/claims
  audit) would improve the picker annotations.
- **interactions/jinxes**: none.

### B. Recording the choice

Reuse the shared facility proposed in the General's spec:

```
data class NightRecord(val cycle: Int, val stepId: String, val playerId: Long?, val text: String)
val nightRecords: List<NightRecord> = emptyList()   // on GameState
```

Choosing a player writes `NightRecord(cycle, "highpriestess", hpSeatId,
"Shown: Sarah")` (and the chosen seat id, so the picker can compute repeats).
Render the history inline on the step:
`Shown so far: Sarah ×3 (N1, N2, N3), Julian (N4)`, and add it to
`GameLogDialog`.

This same record is what the Cannibal spec needs if a Cannibal ever gains the
High Priestess ability.

### C. A seat-oriented show card

Add `ShowCard.SeatCard(playerId)` to `ShowCards.kt:65-77`, rendering the
player's **name** in very large type (plus their seat number if the app ever
numbers seats), with no character art — the High Priestess learns a *player*,
not a character. Wire it into the step as `» Show this player`. The Choirboy
needs the identical card; build it once.

### D. UI text for the step

- Title: `High Priestess`
- Detail: `Who should they talk to most? Point to that player — alive or dead,
  good or evil.`
- Under the picker: `Shown before: Sarah (N1, N2, N3)` and
  `A repeat tells them the last conversation didn't land.`
- Impaired: `The High Priestess is poisoned by the No Dashii — any player is
  fine.`
- Dead: no row.

### E. Data changes

- `characters.json:1396`: keep "Point to a player." but consider the fuller
  "Wake the High Priestess and point to the player you think they should talk
  to most." so the collapsed row is self-sufficient.
- `night_guide.json:1026`: add a `shows` entry for the seat card once it
  exists; keep the prose; add "a repeat is a deliberate signal" to the `other`
  entry.
- `night_and_jinxes.json`: verify the first/other placement against a current
  official night sheet (unverified today, see P3-8). No jinx changes.

## Tests to add

1. `high priestess wakes on both nights`
   Given a living High Priestess; Then `nightOrder.firstNight(...)` and
   `nightOrder.otherNight(...)` each contain a `"highpriestess"` step, each
   positioned immediately before the General's slot.

2. `dead high priestess gets no step`
   Given a dead High Priestess; Then neither night sheet contains a
   `"highpriestess"` step.

3. `the choice is recorded per night`
   Given choices of Sarah on nights 1 and 2 and Julian on night 3; Then
   `state.nightRecords.filter { it.stepId == "highpriestess" }` has three
   entries with cycles 1, 2, 3 and the right player ids.

4. `repeats are surfaced`
   Given the state from test 3 on night 4; Then the step's history line reports
   Sarah twice and Julian once, and the picker sorts un-shown players ahead of
   Sarah and Julian.

5. `dead and evil players are selectable`
   Given a dead Chef and a living Goblin; Then both appear in the candidate
   list (guards against anyone copying the Farmer's alive-and-good filter).

6. `impaired high priestess is flagged`
   Given a High Priestess adjacent to a No Dashii as its nearest Townsfolk
   neighbour; Then `StatusEffects.isImpaired` is true and the step's caveats
   contain the No Dashii reason. (Fails today: caveats are gated behind
   `InfoCalc.supports`.)

7. `no vortox caveat on a judgement ability`
   Given a Vortox in play; Then the High Priestess step produces no "Townsfolk
   info must be FALSE" caveat — the ability yields no information.

8. `the record survives undo/redo symmetrically`
   Given a recorded choice; When undo then redo runs; Then
   `state.nightRecords` is identical. (Everything must go through
   `viewModel.update` to stay undoable.)
