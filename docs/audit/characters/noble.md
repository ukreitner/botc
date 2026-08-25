# Noble (noble) — Experimental (Carousel) Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Noble> (fetched 2026-08-25).

**Current ability text (wiki):**
> "You start knowing 3 players, 1 and only 1 of which is evil."

`characters.json:1490` matches exactly — **no drift**.

**How to Run (quoted):**
> "While preparing the first night, mark two good players' character tokens with Noble **KNOW** reminders. Mark any evil player's character token with the third **KNOW** reminder. During the first night, wake the Noble. Point to all three players marked **KNOW**. Put the Noble to sleep."

Timing / edge cases:

- **First night only** (official position 59). Absent from the other-night order. If created
  mid-game, they act on their first night as the Noble.
- The Noble learns **three players and that exactly one is evil** — not which one, not which
  characters. The order of pointing is deliberately meaningless.
- **Recluse:** may register as evil, so the Recluse can legitimately be used as the "1 evil".
- **Spy:** registers as good, so the Spy can legitimately be used as one of the "2 good".
  (Using both is the classic double bluff.)
- **Drunk / poisoned Noble:** point at any 3 players — 0, 2 or 3 of them evil is fine.
- **Vortox:** Townsfolk information must be false, so the three shown players must **not**
  contain exactly one evil player (0, 2 or 3 evil).
- **Jinxes:** none listed on the Noble page, and none in `night_and_jinxes.json`.

## What the app does today

- `characters.json:1490-1504`: `reminders: ["Know","Know","Know"]`, `firstNightReminder`
  "Point to the 3 players marked 'Know'.", `otherNightReminder: ""`.
- Night order: `night_and_jinxes.json:354` (first night, index 59), correctly absent from
  the other-night list.
- `night_guide.json:1126-1131`: `first` only, correct prose including the drunk/poisoned case.
  No `shows` — correct, the Noble is pointed at players.
- `InfoCalc.supports("noble")` is true (`InfoCalc.kt:33`), dispatched at `InfoCalc.kt:74`,
  implemented at `InfoCalc.kt:451-458`:
  ```kotlin
  val evil = ctx.players.filter { ctx.isEvil(it) }
  return InfoResult(
      headline = "Point to 3 players: exactly 1 evil, 2 good",
      detail = "Evil players: ${evil.joinToString { ctx.name(it) }}",
      caveats = misregistrations(ctx, ctx.players),
  )
  ```
- Reminder placement is manual via `NightToolTray` (`NightScreen.kt:193-352`): three "Know"
  chips; because `allReminders.count { it == "Know" } == 3`, the copy-tracking branch
  (`NightScreen.kt:316-340`) permits three placements and silently recycles the
  **first-placed** token when a fourth seat is tapped.
- `GameActions.validateSetupState` (`GameActions.kt:503-561`) validates the Drunk, Lunatic,
  Marionette and Fortune Teller — **not** the Noble.
- `GameShell.kt:347-375` has a bespoke setup dialog for the Fortune Teller's red herring;
  there is no Noble equivalent.

Storyteller experience: identical to the Knight's — nothing at setup, then at first-night
step 59 the ST reads "Evil players: Ali, Bo", chooses a triple in their head, opens the
tray, taps "Know" + seat three times, and points. Nothing validates that the triple is legal.

## Defects and gaps

1. **P1 · The three KNOW tokens are never prompted for or validated.**
   The rules place them "while preparing the first night". The pattern exists
   (`GameShell.kt:347-375` + `GameActions.kt:547-559` for the red herring) but is not applied
   to the Noble. An ST can reach night 1 with zero, one or five KNOW tokens.
   *Repro:* Deal a bag containing a Noble, tap "Begin night" — no prompt, no setup issue.

2. **P1 · Nothing validates "exactly 1 evil".**
   This is the Noble's whole ability and it is trivially checkable from the grimoire. The
   tray will happily let the ST mark three good players or two evil players, and nothing —
   not `validateSetupState`, not `InfoCalc.noble`, not the step — complains.
   *Repro:* Place all three "Know" tokens on good players → expand the Noble step → the
   headline still reads "Point to 3 players: exactly 1 evil, 2 good" with no error.

3. **P1 · `InfoCalc.noble` restates the task instead of answering it.**
   `InfoCalc.kt:451-458` returns a fixed headline plus a list of evil players. It does not
   read the placed KNOW tokens, does not name the three players to point at, and does not
   propose a legal triple. The only computed content — "Evil players: Ali, Bo" — is a plain
   grimoire read.

4. **P1 · A Noble created mid-game never wakes.**
   `noble` appears only in `firstNight` (`night_and_jinxes.json:354`) and `NightOrder.build`
   (`NightOrder.kt:130-181`) draws exclusively from those lists. A Pit-Hag-made Noble, an
   Amnesiac becoming the Noble, or a Damsel turned into the Noble by the Huntsman, gets no
   step and no information. (Same cross-cutting bug as `knight.md` defect 4 and every other
   "You start knowing" character.)

5. **P2 · Misregistration caveats are generic and undirected.**
   `misregistrations(ctx, ctx.players)` (`InfoCalc.kt:120-130`) prints
   "X is the Spy — may register as good / a Townsfolk or Outsider" and
   "Y is the Recluse — may register as evil / a Minion or Demon". For the Noble the useful
   statements are the concrete ones: *"You may use the Recluse (Y) as your 1 evil"* and
   *"You may use the Spy (X) as one of your 2 good"* — which is exactly the choice the ST is
   making at that moment.

6. **P2 · Vortox is flagged but not actionable.**
   `commonCaveats` (`InfoCalc.kt:157-166`) adds "VORTOX in play — Townsfolk info must be
   FALSE". For the Noble that means the shown triple must contain 0, 2 or 3 evil players.
   The step never says this, and the "False info to show instead" helper
   (`NightScreen.kt:880-931`) only handles numbers and YES/NO, so it renders nothing.

7. **P2 · No day-time record of the Noble's claim.**
   The Noble almost always comes out and names three players. There is nowhere to record
   that, nowhere to cross-check it against the placed KNOW tokens, and nowhere to note that
   the Noble was poisoned on night 1 (making their claim false). Same class of gap as the
   user's Gossip complaint.

8. **P3 · Silent token rotation.** `NightScreen.kt:328-336` removes `placed.first()` when all
   three copies are down and a fourth seat is tapped. Correct, but unannounced.

9. **P3 · Shared "Know" label with the Knight.** `PlacedReminder.sourceId` distinguishes them
   (`GameState.kt:6-11`), so this is not a correctness bug — noted so it is not mistaken for
   one. The seat display should still show the source character so a Knight+Noble game is
   readable at a glance.

## Proposed behaviour (spec)

**Setup**

- When a Noble is dealt, raise a setup dialog in the shape of `GameShell.kt:347-375`:
  *"Noble — pick 3 players: exactly 1 evil, 2 good."* Two-panel picker (good list, evil list)
  with a suggested default triple, or a single list with live validation
  (`2 good ✓ / 1 evil ✓`). On confirm place three `PlacedReminder("noble","Know")`.
- Add to `GameActions.validateSetupState` (`GameActions.kt:503-561`): with a Noble in play,
  exactly three `("noble","Know")` tokens must be placed, and — using
  registration-aware alignment (see below) — **exactly one** must be on an evil player,
  unless a **Vortox** is in play, in which case exactly one must *not* be.
  Issue text: `"Noble: mark 3 players — exactly 1 evil"`.

**Structured night step**

- **when:** first night; also the seat's first night if the Noble is created later (fix
  defect 4: include a "start knowing" step whenever the seat holds the character and has no
  `("<id>","Know")` tokens yet). Wake condition: holder alive.
- **targets:** none chosen by the player; the ST marks 3 seats.
- **immediate effects:** three `("noble","Know")` reminders, capped at 3 copies.
- **deferred effects / expiry:** none. The tokens must never be added to
  `EXPIRES_AT_DAWN`/`EXPIRES_AT_DUSK` (`GameActions.kt:218-242`).
- **information:**
  - Headline when tokens are placed: `Point to Bo, Cara and Dan. Exactly 1 of them is evil.`
  - Headline when tokens are missing: `Pick 3: 2 good + 1 evil.` with two chip rows
    (`Good: …` / `Evil: …`) and one-tap placement.
  - Detail: `Evil in play: Ali (Poisoner), Eve (Imp).` and
    `Registering evil: also Yara (Recluse) if you choose.`
  - **Misregistration**, Noble-specific:
    - Recluse present → `You may use Yara (Recluse) as the 1 evil.`
    - Spy present → `You may use Ali (Spy) as one of the 2 good.`
    Suppress the generic lines for this character.
  - **Impaired** (drunk/poisoned/Drunk/Marionette, via `InfoCalc.impairments`): red banner
    `Any 3 players are legal — 0, 2 or 3 evil is fine.` and the validator stops complaining.
  - **Vortox:** red banner `Vortox — the Noble's info must be FALSE: the 3 must NOT contain
    exactly 1 evil (use 0, 2 or 3).`, and the suggested triple flips accordingly.
  - **Lycanthrope Faux Paw:** once registration is modelled (see `lycanthrope.md`), a
    Faux Paw player counts as **evil** for the Noble and the step must say so:
    `Cara registers as evil (Lycanthrope Faux Paw) — she can be your 1 evil.`
- **visibility:** nothing to evil.
- **day-time inputs:** a claim recorder — `Noble claims: Bo, Cara, Dan` — cross-checked
  against the KNOW tokens, with an impairment note if the Noble was drunk/poisoned on the
  night the info was given. Snapshot the Noble's night-1 impairment at the moment the info is
  given (the same idea as `DeathRecord.abilityImpairedAtDeath`, `GameState.kt:80`).
- **interactions/jinxes:** none.

**UI text**

- Setup prompt: `Noble — mark 3 players: exactly 1 evil, 2 good.`
- Step (tokens placed): `Wake the Noble (Fay). Point to Bo, Cara and Dan. Exactly one of them is evil.`
- Step (tokens missing/illegal): `! Noble's 3 marked players contain 2 evil — the Noble's info must have exactly 1.`
- Impaired: `! Fay is POISONED (Poisoner) — any 3 players are legal.`

**Data changes**

- `night_guide.json:1126-1131` — add: "The Recluse may be used as your 1 evil; the Spy may be
  used as one of your 2 good." and "Under a Vortox, the 3 must not contain exactly 1 evil."
- No `characters.json` or night-order change.

## Tests to add

1. `GIVEN` a Noble in play with no `("noble","Know")` tokens `WHEN`
   `GameActions.validateSetupState` runs `THEN` it reports a Noble issue. *Fails today.*
2. `GIVEN` three `("noble","Know")` tokens all on good players `THEN` `validateSetupState`
   reports "exactly 1 evil". *Fails today.*
3. `GIVEN` three KNOW tokens with two on evil players `THEN` `validateSetupState` reports it.
4. `GIVEN` a legal triple `WHEN` `InfoCalc.compute(..., "noble", ...)` runs `THEN` the
   headline names the three players. *Fails today* (headline is static).
5. `GIVEN` a Recluse and a Spy in play `THEN` the Noble's caveats contain the Noble-specific
   phrasings ("may be used as your 1 evil" / "as one of your 2 good"). *Fails today.*
6. `GIVEN` a Vortox in play `THEN` the Noble result instructs 0/2/3 evil and the validator
   accepts a triple with 0 evil. *Fails today.*
7. `GIVEN` a poisoned Noble `THEN` the caveats include the poison line and the "exactly 1
   evil" validation is suppressed.
8. `GIVEN` a seat that becomes the Noble on cycle 3 `WHEN` the other-night sheet is built
   `THEN` a Noble step appears for that seat. *Fails today.*
9. `GIVEN` a Noble and a living sober Lycanthrope with a Faux Paw on a good player `THEN`
   that player counts as evil for the Noble's "exactly 1 evil" check. *Fails today.*
10. `GIVEN` a Noble `WHEN` `advancePhase` runs through dawn and dusk `THEN` all three
    `("noble","Know")` tokens remain (regression guard against the expiry tables).
