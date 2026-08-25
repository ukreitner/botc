# Seamstress (seamstress) — Sects & Violets Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Seamstress> (fetched 2026-08-25).

Current ability text:

> "Once per game, at night, choose 2 players (not yourself): you learn if they are
> the same alignment."

**How to run (quoted / paraphrased from the page's How to Run):**

> - Each night, wake the Seamstress.
> - They either shake their head (pass) or point at two other players.
> - Respond with a nod (same alignment) or a head shake (different alignment).
> - After using the ability, mark them with the NO ABILITY reminder and remove
>   their night token (i.e. they stop waking).

**Clarifications (quoted):**

> - "Usable only once per game."
> - "Cannot choose yourself."
> - "Can select alive or dead players, **including Travellers**."
> - "'Same alignment' means both good or both evil — not necessarily 'good'."

**Storyteller-relevant tips**: the Seamstress may deliberately pair a trusted good
player with a suspect to convert "same/different" into "good/evil"; evil bluffs
work by inverting the answer.

**Night order.** First night position 56 (`night_and_jinxes.json:351`, right after
Dreamer 55 and before Steward 57); other nights position 81
(`night_and_jinxes.json:454`, after Oracle 80, before Juggler 82). Both correct.

**Jinxes.** None (checked against all 58 entries in `night_and_jinxes.json`).
Note that the Vortox and Spy/Recluse interactions are general rules, not jinxes.

## What the app does today

This is the best-supported character in this group; most of the machinery exists.

- `characters.json:917-930` — ability, `reminders: ["No ability"]`, and
  first/other night reminder prose.
- `night_and_jinxes.json:351` / `:454` — correct positions. **Works.**
- `night_guide.json:535-544` — full prose for both nights, including "then place
  the 'No ability' reminder — this was their once per game use" and "If the
  Seamstress is drunk or poisoned, or the Vortox is in play, give a false answer
  (the ability is still spent)." **Rules-accurate.**
- `InfoCalc.targetsNeeded("seamstress") == 2` (`InfoCalc.kt:23`), in
  `supports` (`InfoCalc.kt:31`), dispatched at `InfoCalc.kt:62`, computed at
  `InfoCalc.kt:356-365`:
  compares `ctx.isEvil(a) == ctx.isEvil(b)`, headline `"YES — same alignment"` /
  `"NO — different alignments"`, detail naming both players and their alignments,
  caveats from `misregistrations` (Spy/Recluse, `InfoCalc.kt:121-130`).
  Alignment flips are honoured because `Player.isEvil` folds `alignmentFlipped`
  (`GameState.kt:49-52`) — covered by `InfoCalcTest.kt:120-133`. **Works.**
- `commonCaveats` (`InfoCalc.kt:158-166`) adds drunk/poisoned/Drunk-character/
  Marionette/dead/Vortox notes. **Works.**
- `StepDetailPanel` (`NightScreen.kt:836-931`) renders a 2-seat chip picker, the
  headline, the caveats, a `Show answer full-screen` YES/NO card, and — when the
  holder is impaired — a one-tap `Show NO`/`Show YES` inversion
  (`NightScreen.kt:903-929`). **Works.**
- `NightToolTray` offers `Mark spent` because the ability starts with "Once per
  game" (`NightScreen.kt:204, :263-279`), placing
  `PlacedReminder("seamstress","No ability")` exclusively.

**The storyteller's experience.** Tap two chips, read YES/NO, flash the answer,
then remember to scroll back up to the tray and tap "Mark spent". On every
subsequent night the Seamstress row appears again exactly as before, and the two
chips from last night are still selected.

## Defects and gaps

1. **P1 · Spending the ability is a separate, easy-to-miss action.** Giving the
   answer and marking the ability spent are unrelated UI gestures: the answer is
   in `StepDetailPanel` (`NightScreen.kt:836-931`), the `Mark spent` chip is in
   the tray at the bottom of the screen (`NightScreen.kt:263-279`) and only while
   this step is the expanded one. Repro: pick two players, show the answer,
   tap the step's checkbox, advance the night — no "No ability" token was placed.
2. **P1 · A spent Seamstress keeps waking.** `NightOrder.build` emits the row
   whenever a holder exists (`NightOrder.kt:142-178`; `:145` only skips when
   `holders.isEmpty()`), so even with `("seamstress","No ability")` placed, the
   row reappears every night with a live info panel and no "spent" styling. The
   How-to-Run says to remove their night token. The engine's own playtest fixture
   documents the workaround three times: `"Luz had spent her once-per-game
   ability; skipped."` (`FullGamePlaytestTest.kt:952, :1011, :1051`).
   The dawn guard even counts the row as an unfinished step
   (`GameShell.kt:147-161`), so the ST must tick a step that shouldn't exist.
3. **P1 · The picker lets the ST choose the Seamstress themselves.** The chip row
   iterates `state.players` unfiltered (`NightScreen.kt:847`) and
   `InfoCalc.seamstress` only requires two distinct, resolvable ids
   (`validTargets`, `InfoCalc.kt:106-111`). "not yourself" is part of the ability
   text. Repro: expand the Seamstress row, tap the Seamstress's own chip plus one
   other — a confident YES/NO appears.
4. **P1 · Stale targets carry over between nights.** `var targets by
   rememberSaveable(step.id) { mutableStateOf(listOf<Long>()) }`
   (`NightScreen.kt:839`) is keyed only on the step id, which is constant across
   cycles, and `LazyColumn` preserves saveable state per item key. So on night 3
   the Seamstress row opens with night 2's two players still selected and a stale
   YES/NO already rendered. For a once-per-game role this mostly hides the bug;
   for the Fortune Teller and Chambermaid (same code path,
   `InfoCalc.kt:22-26`) it is a live misinformation risk. Fix belongs here because
   this is the shared widget. Cross-cutting.
5. **P1 · An unflagged Traveller silently reads as good.** `Team.TRAVELLER.isEvil`
   is false (`Character.kt:22`) and `Player.isEvil` only flips it via
   `alignmentFlipped` (`GameState.kt:49-52`), but nothing in the seat flow
   (`CharacterPicker` → `assignCharacter(…, isTraveller = true)`,
   `SeatSheet.kt:448-450`, `GameActions.kt:46-53`) ever asks for the Traveller's
   alignment. The wiki explicitly allows the Seamstress to choose Travellers, so
   an ST who forgot to hit "Flip alignment" gets a confidently wrong YES/NO.
   Repro: add a Traveller seat, assign Scapegoat, run the Seamstress on that
   Traveller and a known good player → "YES — same alignment".
6. **P2 · Nothing records what was asked or answered.** No `DayRecord`/night
   record, nothing in the game log (`screens/GameExtras.kt:46-108`, derived from
   deaths and nominations only). When the Seamstress claims on day 4, the ST has
   no way to check what they were actually told, and after an undo/redo the
   answer is gone.
7. **P2 · Impairment does not force the spend.** The rules (and the app's own
   guide text, `night_guide.json:539`) say a drunk/poisoned Seamstress still
   spends the ability while getting a false answer. The `Show NO`/`Show YES`
   inversion chips exist (`NightScreen.kt:923-928`) but the app never marks the
   use, so an ST who follows the on-screen flow leaves the ability un-spent.
8. **P2 · Misregistration is advisory only, with no way to record the ruling.**
   `misregistrations` (`InfoCalc.kt:121-130`) lists Spy/Recluse among the chosen
   pair, but the ST's decision ("tonight the Recluse registers as evil") is not
   stored, so a later Seamstress-adjacent question (or an undo) can silently flip
   it. Also, the note fires even when the answer is unaffected — e.g. a Recluse
   paired with a real Minion is "same alignment" either way only if the Recluse
   registers evil, so the app could compute *whether the answer depends on the
   ruling* and say so.
9. **P2 · A Philosopher/Alchemist-gained Seamstress shares the real Seamstress's
   spent state once gating is added.** Any "hide the row when spent" logic must be
   per-seat (`("seamstress","No ability")` on *that* seat), not per-character —
   see `docs/audit/characters/philosopher.md`.
10. **P3 · Data text has a stray space.** `characters.json:921` /`:922`:
    "If the Seamstress chose players , nod 'yes'…" (space before the comma), in
    both the first- and other-night reminder. Cosmetic.
11. **P3 · Dead players are legal choices but not signposted.** The app allows
    them (no filter) which is correct per the wiki; a one-line hint would save the
    ST looking it up.

## Proposed behaviour (spec)

- **when**: both first and other nights. Wake condition: a seat with
  `nightRoleId == "seamstress"`, **alive**, and **not** carrying
  `("seamstress","No ability")`. If the seat is dead or spent, do not emit the
  row at all (the How-to-Run says to remove their night token).
- **targets**: exactly 2 seats.
  - Constraints: distinct; **not the holder**; alive or dead; Travellers allowed.
  - Picker: exclude the holder's own chip entirely; sort alive-before-dead, keep
    seat order otherwise; badge Travellers whose alignment has never been set with
    a red `alignment not set` marker and a jump-to-seat action.
  - The picker must reset to empty at every dusk (key the saveable on
    `step.id + state.cycle`).
  - A `They passed (no choice)` button that marks the step done without spending
    the ability.
- **immediate effects** on confirming an answer (one button, not two gestures):
  - `placeExclusiveReminder(state, holderId, PlacedReminder("seamstress","No ability"))`
  - append a night record `{cycle, holderId, targets, answerGiven: Boolean,
    trueAnswer: Boolean, impaired: Boolean}` to the general record store proposed
    in `docs/audit/characters/savant.md` (`state.dayRecords` generalised to
    `state.abilityRecords`), so the game log can show
    `N2 · Seamstress (Luz) asked Beau + Cleo — shown NO (true answer NO)`.
  - No other state changes; no kills, no status effects.
- **deferred effects**: none. The row must never appear again for this seat.
- **expiry**: `("seamstress","No ability")` never expires — verify it is absent
  from `EXPIRES_AT_DAWN` / `EXPIRES_AT_DUSK` (`GameActions.kt:218-242`). It is.
- **information**:
  - True answer: `isEvil(a) == isEvil(b)` (existing `InfoCalc.kt:356-365`) — keep.
  - Detail must keep naming both players with their alignment, since that is what
    lets the ST sanity-check a Traveller or a flipped player.
  - Impaired / Vortox: show the inverted answer prominently and *still* spend the
    ability. The panel should read `TRUE: YES · GIVE: NO (poisoned)`.
  - Misregistration: compute the answer under both readings; if they differ, say
    `Answer depends on whether the Recluse registers as evil — you choose` and
    offer two buttons that each record the ruling. If they agree, say nothing.
- **visibility**: the Seamstress only. Keep the existing full-screen YES/NO card
  (`NightScreen.kt:896-901`). No token is shown.
- **day-time inputs**: none.
- **interactions/jinxes**:
  - No jinxes.
  - Vortox: answer must be false (already caveated, `InfoCalc.kt:161-164`).
  - Spy / Recluse: as above.
  - Drunk / Marionette holder: `impairments` already reports both
    (`InfoCalc.kt:136-141`); the ability is still spent.
  - Philosopher/Alchemist-gained Seamstress: per-seat spend marker.
  - Travellers as targets: legal; alignment must be explicit.

### UI text the step should display

- Row title while unspent: `Seamstress — once per game, not used yet`.
- Body: `They shake their head, or point at two players other than themselves.`
- After picking two: `TRUE ANSWER: YES — same alignment (Beau evil, Cleo evil)`.
- Confirm button: `Show YES and spend the ability` / (impaired)
  `Show NO (false) and spend the ability`.
- Pass button: `They passed — keep the ability`.
- Traveller warning: `Ivy is a Traveller with no alignment set — set it before
  answering.`

### Data changes

- `characters.json:921-922`: fix "chose players , nod" → "chose players, nod".
- `night_guide.json:535-544`: add the "not themselves" constraint and the
  "dead players and Travellers are legal choices" clarification to both
  instruction strings.
- No night-order change.

## Tests to add

1. **Self-choice is rejected.** Given seat 3 is the Seamstress; When
   `InfoCalc.compute(data, state, "seamstress", holderId = 3, targets = listOf(3, 7))`;
   Then the result is the guidance placeholder ("Pick 2 different valid players
   … other than the Seamstress"), not a YES/NO. (Today it returns a confident
   answer — this test fails.)
2. **Spent Seamstress does not wake.** Given `("seamstress","No ability")` on
   seat 3; When `NightOrder.otherNight(state, lookup)` is built; Then it contains
   no `seamstress` step. (Fails today.)
3. **Dead Seamstress does not wake.** Given seat 3 is dead and unspent; Then no
   `seamstress` step is emitted.
4. **Answering spends the ability.** Given an unspent Seamstress; When
   `GameActions.seamstressAnswer(state, 3, listOf(0, 7), gaveYes = false)`; Then
   seat 3 carries exactly one `("seamstress","No ability")` reminder and an
   ability record exists for this cycle.
5. **Spend survives dusk and dawn.** Continuing (4); When `advancePhase` runs
   twice; Then the reminder is still present (assert it is in neither expiry
   table).
6. **Traveller alignment.** Given a Traveller seat with `alignmentFlipped = false`
   and a good Townsfolk; Then `InfoCalc.seamstress` returns "same alignment" and
   the caveats contain a Traveller-alignment warning. (The warning is new.)
7. **Flipped alignment is honoured.** (Already covered by
   `InfoCalcTest.kt:120-133`; keep and extend to a flipped *Traveller*.)
8. **Misregistration changes the answer.** Given the pair is `recluse` + a real
   Minion; Then the result reports that the answer differs by ruling
   (`YES` if the Recluse registers evil, `NO` otherwise) rather than a single
   headline.
9. **Impaired still spends.** Given the Seamstress carries
   `("poisoner","Poisoned")`; When the answer is given; Then the ability record
   has `impaired = true`, the shown answer is the inverse of the true answer, and
   the "No ability" reminder is placed.
10. **Targets reset each night.** Given targets were chosen on night 2; When the
    state advances to night 3; Then the step's target selection is empty (test at
    the state level once the selection moves out of `rememberSaveable` into
    per-cycle state, or assert the saveable key includes `state.cycle`).
