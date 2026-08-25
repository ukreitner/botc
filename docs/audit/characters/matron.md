# Matron (matron) — Bad Moon Rising Traveller

## Official rules (sources)

Sources (fetched 2026-08-25):
- <https://wiki.bloodontheclocktower.com/Matron>
- <https://wiki.bloodontheclocktower.com/Travellers>
- Official rulebook, "Travelers" chapter (mirror
  <https://www.web3us.com/sites/default/files/Rulebook.pdf>).

Current ability text (matches `characters.json` exactly):

> "Each day, you may choose up to 3 sets of 2 players to swap seats. Players may
> not leave their seats to talk in private."

How to run (wiki, near-verbatim):

- "**Players away from their seats cannot discuss the game.** Each day, the Matron
  declares two players to swap positions. **Their tokens and reminders exchange in
  the Grimoire.** Maximum three swaps daily, executed consecutively."
- Summary clarifications:
  - "**Up to three player swaps per day, with new positions permanent unless
    changed again.**"
  - "**Individual players can be moved multiple times.**" (a single player may be
    part of more than one of the three swaps)
  - "Players with physical disabilities are immune to repositioning."
  - "**Private conversations restricted to immediate neighbors while seated.**"
  - "Players leaving seats cannot discuss gameplay."
  - "**If fewer than three swaps occur in a day, no additional swaps happen
    later.**" (the allowance does not bank; it resets each day)
- Examples: "An evil Matron seats herself adjacent to the Tea Lady to coordinate
  secretly. A good Matron separates suspected Demon and Minion players to prevent
  collaboration."
- Strategy notes that reveal what the ST must watch: "Strategically seat
  position-dependent characters (**Tea Lady, Lunatic, Empath**)…" — every
  neighbour-based ability re-evaluates after a swap.

Traveller framework (rulebook, verbatim):
- "**Choose Alignment. Tell the Traveler player in private whether they are good
  or evil. If you made the Traveler evil, they learn which player is the Demon…**"
  and "**Inform Group… (Do not declare their alignment.)**"
- "Travelers… **lose their abilities when dead or drunk or poisoned**."
- Exile: "**If at least half of the players support the exile, it succeeds**…
  This counts the total number of players in the game, not the number of alive
  players." · "Any player, even dead ones, may support the exile… Dead players
  that support an exile do not lose their vote token." · "not affected by
  abilities."

Jinxes: none for the Matron in `night_and_jinxes.json` or on the wiki.

Night order: the Matron never acts at night; correctly absent from both lists.

Seat-order-dependent rules the Matron perturbs (worth listing because the app
derives several of them):
- **No Dashii** — poisons its nearest Townsfolk neighbour each way.
- **Empath / Chef / Shugenja / Balloonist / Clockmaker / Steward** — neighbour and
  distance information.
- **Tea Lady** — both living neighbours good ⇒ they cannot die.
- **Marionette** — must neighbour the Demon (a setup requirement, but the grimoire
  should still show the truth).
- **Sailor / Innkeeper / Godfather / Lunatic** — Lunatic seating matters for the
  fake attacks; Godfather kills an Outsider neighbour in some scripts.
- The **exile vote order** and the **execution vote order** both start clockwise
  from the nominee, so swaps change the counting order too.

## What the app does today

Data
- `characters.json` — `matron`, team `traveler`, ability text correct,
  `reminders: []`, no night reminders. Correct.
- `night_guide.json` — **no `matron` entry**.
- `night_and_jinxes.json` — absent from both orders. Correct.

Engine
- Nothing references `matron`. `grep -rn "matron" engine/src app/src` matches only
  `characters.json`.
- `GameActions.moveSeat(state, playerId, delta)` (`GameActions.kt:32-41`) removes
  the `Player` from the list and reinserts it `delta` positions away, wrapping.
  The whole `Player` (name, character, reminders, alive, ghost vote) travels, so
  it is the right primitive — but it **shifts everyone in between** rather than
  swapping two seats.
- `GameActions.swapCharacters(state, id1, id2)` (`GameActions.kt:98-115`) swaps
  only `characterId` and `shownCharacterId`. **This is the wrong operation for
  the Matron** — it leaves reminders, life/death, ghost votes and names where they
  are, and is intended for the Barber and Snake Charmer.
- There is **no `swapSeats`** action.
- `StatusEffects.derivedPoison` (`StatusEffects.kt:14-33`) recomputes the No
  Dashii's poisoned neighbours from the live seat list every time — so it does
  follow a reorder correctly.
- `StatusEffects.deathNotes` recomputes Tea Lady adjacency live
  (`StatusEffects.kt:79-91`) — also correct after a reorder.
- `GameActions.validateSetupState` checks Marionette adjacency
  (`GameActions.kt:530-543`) but is only called at the SETUP→NIGHT boundary, so a
  mid-game reseat that breaks it is never reported.

UI
- Menu → "Reorder seats" opens `ReorderSeatsDialog`
  (`GameShell.kt:250-253` region, `GameExtras.kt:108-140`): a plain list with
  up/down arrows calling `moveSeat(±1)`. To swap two players three seats apart you
  need six taps and you disturb the two players between them.
- Seat sheet → "Swap characters" (`SeatSheet.kt:118-142`) is the
  `swapCharacters` primitive with the explanatory text "Barber cuts, Snake Charmer
  swaps — both seats trade tokens." A storyteller looking for the Matron will
  reach for this and get the wrong result.
- No swap counter, no per-day reset, no day-start briefing, no announcement text
  for "players may not leave their seats to talk in private".
- Alignment: defaults to good like every traveller (`Character.kt:16`,
  `GameState.kt:45-51`); bare "Flip alignment" only (`SeatSheet.kt:315`).

Storyteller experience today: they must count the Matron's three swaps in their
head, translate each swap into repeated up/down taps in a modal that shows names
only (no character tokens, no alive/dead), and then re-check by eye whether any
neighbour-based ability changed.

## Defects and gaps

1. **P1** · No swap-two-seats action · Rules: "the Matron declares two players to
   swap positions. Their tokens and reminders exchange in the Grimoire." App: only
   `moveSeat(±1)` (which shifts everyone between) and `swapCharacters` (which
   swaps the wrong fields) · `GameActions.kt:32-41`, `GameActions.kt:98-115`,
   `GameExtras.kt:108-140` · Repro: Matron declares seats 2 and 7 swap; you must
   press "down" five times on one and "up" four times on the other, moving five
   uninvolved players.

2. **P1** · "Swap characters" is a trap for this character · The seat sheet's
   swap moves `characterId`/`shownCharacterId` only, leaving reminders, death
   state and ghost votes attached to the seat rather than the player · Using it
   for a Matron swap silently corrupts the grimoire (e.g. a Poisoned token stays
   with the old seat, an Innkeeper "Protected" token detaches from its player) ·
   `SeatSheet.kt:118-142`, `GameActions.kt:98-115`.

3. **P1** · The three-swaps-per-day allowance is not tracked · Rules: "up to 3
   sets of 2 players… If fewer than three swaps occur in a day, no additional
   swaps happen later." App: unlimited reordering at any time, no counter, no
   dawn reset · no code exists.

4. **P1** · No day-start briefing and no announcement helper · The Matron's second
   clause ("Players may not leave their seats to talk in private") is a **public
   rule change the ST must announce and police** every day. Nothing in the app
   mentions it · `DayScreen.kt:85-124`.

5. **P2** · Nothing re-checks seat-dependent state after a reseat · Marionette
   adjacency is validated only at setup (`GameActions.kt:530-543`); nothing warns
   "this swap moved the Marionette away from the Demon", "this swap changed the
   No Dashii's poisoned pair" (the poison itself does follow, via
   `StatusEffects.kt:14-33`), or "the Tea Lady's neighbours changed".

6. **P2** · The reorder dialog is name-only · No character token, no alive/dead
   marker, no traveller badge, and on a phone it is a cramped modal list — the
   worst possible ergonomics for the one control the Matron needs three times a
   day · `GameExtras.kt:108-140`.

7. **P2** · Swaps are not logged · The game log (`GameExtras.kt:44-90`) records
   deaths and nominations only. After three swaps a day for four days nobody can
   reconstruct the seating history, which matters for retrospective Empath/Chef
   reasoning · `GameExtras.kt:44-90`.

8. **P2** · Loss of ability is unmodelled · A dead, exiled, drunk or poisoned
   Matron has no ability; the app never restricts reordering anyway, so there is
   nothing to remove, but the briefing must reflect it.

9. **P3** · No day-guide entry, so the Matron's how-to-run text is nowhere in the
   app.

## Proposed behaviour (spec)

The Matron has no night step; it needs a first-class **seat swap** action plus a
day-scoped counter.

### Engine

Add to `GameActions`:

```kotlin
/**
 * Exchanges two players' positions in the circle. The whole Player travels —
 * character, shown identity, reminders, life/death, ghost vote, notes — which
 * is exactly what a Matron seat swap does at the table.
 */
fun swapSeats(state: GameState, idA: Long, idB: Long): GameState
```

Implementation: find both indices in `state.players` and exchange the two
elements (do **not** touch any field of either `Player`). Return `state` unchanged
if either id is missing or `idA == idB`.

Add day-scoped bookkeeping. Two options; prefer the first:

1. A `SeatSwap` record list on `GameState`:
   ```kotlin
   data class SeatSwap(val day: Int, val aId: Long, val bId: Long)
   val seatSwaps: List<SeatSwap> = emptyList()
   ```
   `matronSwapsUsedToday(state) = state.seatSwaps.count { it.day == state.cycle }`.
   This also gives a free seating history for the log.
2. Reminder tokens `matron:"Swap 1/2/3"` expiring at dusk — worse, because the
   Matron's allowance is not attached to any seat.

### Day control

- **when:** DAY, a Matron seat exists, alive, not impaired.
- **where:** a "Matron — seat swaps" card in the Day tab (and mirrored as a chip
  in the Grimoire tab), showing `Swaps used today: 1 of 3`.
- **interaction:** tap two seats (a two-chip picker over `state.players`, showing
  each player's character token, name, alive/dead, and traveller badge), then
  `Swap seats`. Apply `swapSeats`, append the `SeatSwap`, log
  `"Matron swapped Alice and Fatima (swap 2 of 3, day 3)"`.
- **limits:** disable the button at 3 swaps today with the reason "The Matron has
  used all 3 swaps today." Do **not** carry unused swaps forward (`the allowance
  resets at dawn`), which falls out of counting by `day`.
- **alive/dead:** the wiki does not restrict swaps to living players (dead players
  still occupy seats and still matter for Empath/Chef/No Dashii adjacency), so
  allow any seat. Flag "physically unable to move" via a per-seat note rather than
  a rule.
- **after each swap, show the consequences** (this is the "app does the
  bookkeeping" part):
  - recomputed No Dashii poisoned pair, with before/after names;
  - Tea Lady neighbours before/after and whether the "can't die" condition
    changed;
  - whether the Marionette still neighbours the Demon;
  - any seat whose Empath/Chef/Shugenja/Balloonist/Steward/Clockmaker reading
    would now differ (these are night calcs, so a plain "these seats' neighbour
    info changed" note is enough);
  - Lunatic adjacency note if a Lunatic is in play.
  Build this from a new `StatusEffects.seatingImpacts(before, after, lookup):
  List<String>`, so the Night tab can reuse it.

### Day-start briefing (shared panel)

> **Matron in play.** Announce: *players may not leave their seats to talk in
> private — you may only whisper with your neighbours.* The Matron may call up to
> **3** seat swaps today (used: 0). Unused swaps do not carry over.

When the Matron is dead, exiled or impaired, the panel becomes
"Matron — no ability (dead/poisoned): seating is fixed and private
conversations are unrestricted again."

### Deferred effects / expiry

- New seating is **permanent** until changed again — nothing expires.
- The swap **allowance** resets at dawn (derived from `cycle`, so no expiry table
  entry is needed).

### Interactions to handle explicitly

- **Exile vote order and execution vote order** are computed clockwise from the
  nominee (`DayScreen.kt:167-172`); after a swap the order changes automatically
  because it derives from `state.players`. Verify with a test.
- **No Dashii / Tea Lady** — already derived live; keep it that way.
- **Marionette** — add a mid-game adjacency warning (currently setup-only,
  `GameActions.kt:530-543`).
- **Bishop / Voudon / Judge** — unaffected.
- **Exile** — a Matron swap is an ability, and abilities do not affect exiles, but
  swapping does not touch exiles anyway.

### UI text

- Card title: `Matron — seat swaps (1 of 3 used today)`
- Instruction: `Tap the two players who swap seats.`
- Confirm: `Swap Alice ⇄ Fatima`
- Consequence header: `What this swap changed`
- Exhausted: `All 3 swaps used today.`

### Data changes

- `characters.json`: none.
- Add a day-guide entry for `matron` carrying the How to Run text and the
  "no leaving seats" announcement.
- Replace/relabel the seat-sheet "Swap characters" button as
  `Swap character tokens (Barber / Snake Charmer)` to stop it being mistaken for a
  Matron swap.

## Tests to add

1. `Given` seats `[A,B,C,D,E]`, `when` `GameActions.swapSeats(state, C, E)`,
   `then` the order is `[A,B,E,D,C]` and every other player's index is unchanged.
2. `Given` C holds a `Poisoned` reminder and is dead with an unspent ghost vote,
   `when` swapped with E, `then` C still holds the reminder, is still dead, and
   still has the unspent ghost vote (the whole `Player` moved).
3. `Given` a No Dashii at seat 0 with Townsfolk at seats 1 and 4, `when` seats 1
   and 3 (a Minion) are swapped, `then` `StatusEffects.derivedPoison` names the
   new nearest Townsfolk neighbours.
4. `Given` a Tea Lady with one evil neighbour, `when` a Matron swap gives them two
   good neighbours, `then` `StatusEffects.deathNotes` for each neighbour now
   contains the "Tea Lady neighbour… can't die" note.
5. `Given` day 3 with two swaps already recorded on day 3, `then`
   `matronSwapsUsedToday(state) == 2` and a third is allowed; `given` three,
   `then` a fourth is rejected.
6. `Given` two swaps used on day 2, `when` the phase advances to day 3, `then`
   `matronSwapsUsedToday(state) == 0` (no carry-over of the unused one, and no
   carry-over of the used count).
7. `Given` a Marionette adjacent to the Demon, `when` a swap separates them,
   `then` `StatusEffects.seatingImpacts(before, after, lookup)` contains a
   Marionette adjacency warning.
8. `Given` a nominee at index 4, `when` a swap changes the circle, `then` the
   clockwise vote order recomputed from `state.players` starts at index 5 of the
   **new** order.
9. `Given` a dead Matron, `then` the day-start briefing reports no ability and the
   swap control is unavailable.
