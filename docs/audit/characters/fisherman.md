# Fisherman (fisherman) — experimental (Carousel) townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Fisherman>

Current ability text (verbatim):

> "Once per game, during the day, visit the Storyteller for some advice to help
> your team win."

`characters.json:1370` matches. (The older `townsquare` dataset says "…to help
you win"; the wiki's current wording is "your team".)

### How to run (verbatim)

> "Once per game, the Fisherman will ask to use their ability. In private,
> give them once piece of advice to help them win. Then, put the **No ability**
> reminder by the Fisherman token."

### Summary / clarifications (verbatim)

- "The Fisherman player chooses when to use their ability."
- "When they visit the Storyteller, the Storyteller chooses what piece of
  advice to give."
- "The Storyteller's pieces of advice are not necessarily 'facts'. They are
  strategy tips."
- "If the Fisherman is drunk or poisoned, the Storyteller may give the
  Fisherman bad advice."

### Storyteller guidance (Tips & Tricks, verbatim highlights)

- "It is best to give the Fisherman advice on what to **DO**, not just
  information about what **IS**."
- "If you're not certain… you can revisit the Storyteller and ask them to
  repeat it." — i.e. **the storyteller must be able to reproduce the exact
  advice later in the game.**
- "Pay attention to the specific words… they may be hinting at something
  without outright saying it."
- "Characters that register as different alignments may affect the hint
  given."

### Examples (verbatim summaries)

- The Fisherman learns "You shouldn't trust Ben" (Ben is a poisoned Empath
  spreading false information).
- The Fisherman learns "Keep the players claiming to be Outsiders alive"
  (secretly the Klutz and Fearmonger).
- On the final day, a **drunk** Fisherman is told to "kill Lewis" — Lewis is a
  Townsfolk.

### Timing

- Day phase only. No night order entry on either list — correct, and the app
  agrees (`night_and_jinxes.json` has no `fisherman` entry; `townsquare`
  `roles.json` gives `firstNight: 0, otherNight: 0`).
- The ability is spent when the advice is delivered, regardless of quality.
- Core rules: dead players have no ability, so a dead Fisherman cannot use it.
  (The Fisherman page does not restate this; flagged as the general rule.)

### Jinxes

None.

## What the app does today

**Nothing.** `grep -rn fisherman engine/src app/src` returns exactly two data
hits — `characters.json:1370` and `raw_exp_townsfolk.json:265`. There is:

- no `night_guide.json` entry (correct — it is not a night ability);
- no night-order entry (correct);
- no code anywhere: no `InfoCalc`, no `StatusEffects`, no `GameActions`, no UI.

Storyteller experience:

- The Fisherman token sits in the circle. The `["No ability"]` reminder exists
  in `characters.json` so it can be placed by hand: Grimoire → seat →
  **Add reminder** → "Rest of script"/"In play" → Fisherman → "No ability"
  (`SeatSheet.kt:492-540`), or from the night screen's "All tokens" tray
  (`NightScreen.kt:280`).
- The Day screen (`DayScreen.kt:54-278`) contains nominations, votes,
  executions and exiles — and nothing else. There is no place to note that the
  Fisherman asked, when they asked, or what they were told.
- The only free-text surface is the single game-wide "Storyteller notes" blob
  (`GameShell.kt:684-705`, `GameState.storytellerNotes`), reachable through the
  overflow menu.
- The **"Mark spent"** convenience that exists for once-per-game night
  abilities (`NightScreen.kt:263-279`) is unreachable here, because it lives
  inside `NightToolTray` and is only rendered for the currently expanded
  **night step** — and the Fisherman has no night step.

## Defects and gaps

1. **P1 · There is no day-phase surface for any character ability.** The
   Fisherman is the clearest case in this scope but the gap is general
   (Slayer, Artist, Savant, Juggler's first-day guesses, Gossip's statement,
   Mayor, Klutz, Cult Leader's cult vote — `grep` for those ids in
   `engine/src/main/kotlin` and `app/src/main/java` returns one hit, in
   `WinCheck.kt:90`). Repro: start a Carousel game with a Fisherman, go to the
   Day tab — nothing mentions the Fisherman.

2. **P1 · Once-per-game spending is not tracked or prompted.** The storyteller
   must remember to place "No ability" by hand through a three-level picker.
   Nothing warns "the Fisherman already used their ability" if the same player
   asks twice, and nothing reminds the storyteller at day start that an
   **unspent** Fisherman is in play.

3. **P1 · The advice is not recorded.** The wiki explicitly supports the
   Fisherman coming back to have the advice repeated, and the advice must stay
   consistent with the rest of the game. Today the storyteller must remember
   their own wording verbatim, or type it into the single global notes blob
   with no day stamp and no link to the seat.

4. **P2 · The impairment rule is never surfaced.** "If the Fisherman is drunk
   or poisoned, the Storyteller may give the Fisherman bad advice." The app
   knows: `StatusEffects.isImpaired(state, lookup, fisherman)`
   (`StatusEffects.kt:36-46`). At the moment of use, that is exactly the fact
   the storyteller needs and must not miss — and getting it wrong hands a
   poisoned player a true, game-winning tip.

5. **P2 · Dead-Fisherman use is not blocked or warned.** A dead player has no
   ability; nothing stops the storyteller from giving advice to a dead
   Fisherman, and nothing points it out.

6. **P2 · No storyteller-side help for composing advice.** The wiki's core
   guidance ("advice on what to DO, not information about what IS") appears
   nowhere in the app, and none of the state the storyteller would draw on
   (who is impaired, who is misregistering, which good info was false, who is
   about to be executed) is collected anywhere for this purpose.

7. **P2 · Privacy.** Delivering the advice means handing the phone to a player
   or whispering. The app has a `PrivacyCover` (`components/PrivacyCover.kt`,
   used at `GameShell.kt:359-361`) and full-screen `ShowCard`s, but no
   free-text card. A `ShowCard.Message(title, subtitle)` already exists
   (`ShowCards.kt:66`, `:99`) and would render typed advice full-screen —
   nothing routes to it from the Day tab.

8. **P3 · Ability text drift check.** `characters.json:1370` says "to help
   your team win", matching the wiki. No change needed; noted because the
   widely-mirrored `townsquare` dataset differs and a future re-scrape could
   regress it.

## Proposed behaviour (spec)

The Fisherman has no night step, so the whole spec is day-side. Build it as a
**general day-abilities panel** and register the Fisherman in it; the same
panel serves the Slayer, Artist, Savant, Juggler, Gossip and the Cult Leader's
cult vote.

### A. Day-abilities panel (new section on `DayScreen`)

Rendered above "New nomination". For each living player whose character has a
day-time ability, one row:

```
Fisherman — Sarah        [ Give advice ]        unspent
Slayer — Ben             [ Slayer shot ]        unspent
Artist — Iris            —                      spent (day 2)
```

Derivation: a new `DayAbilities.forState(state, lookup)` in the engine, driven
by a small table (`characterId → DayAbility(label, spendable, kind)`) rather
than by ability-text parsing. Spent state = a
`PlacedReminder(characterId, "No ability")` on that seat, which is the
existing convention (`NightScreen.kt:263-279`, `Setup`-independent).

Rows for **dead** holders are shown greyed with "dead — no ability".

### B. Fisherman flow (structured form)

- **when**: `day`, any time the Day tab is open; the player initiates.
- **wake condition / eligibility**: the Fisherman is **alive** and has no
  `("fisherman","No ability")` reminder. If either fails, the action is
  disabled with the reason shown.
- **targets**: none.
- **immediate effects**, as one undoable action:
  1. Show a private compose sheet (see C).
  2. On confirm: `placeExclusiveReminder(fishermanId,
     PlacedReminder("fisherman","No ability"))`.
  3. Append a record
     `DayAbilityUse(day = state.cycle, playerId, characterId = "fisherman",
     text = advice, impaired = isImpaired(...))` to a new
     `state.dayAbilityUses: List<DayAbilityUse>`.
- **deferred effects**: none.
- **expiry**: `("fisherman","No ability")` never expires — do **not** add it to
  `EXPIRES_AT_DAWN`/`EXPIRES_AT_DUSK` (`GameActions.kt:218-242`).
- **information**: none computed; the advice is the storyteller's own words.
  The compose sheet should show, as context only:
  - `Fisherman: Sarah — alive, not impaired` **or** the impairment banner (see
    below);
  - who is currently on the block (`GameActions.aboutToDie`);
  - alive good / alive evil counts;
  - a one-line reminder of the rule: *advice on what to DO, not what IS*.
- **impaired alternative**: if `StatusEffects.isImpaired(state, lookup,
  fisherman)` is true, the compose sheet is headed, in error colour:
  `"Sarah is DRUNK/POISONED (<source>) — you may give bad advice."` The
  ability is still spent.
- **visibility**: a `Show full-screen` button on the compose sheet routes the
  typed text to `ShowCard.Message(advice)` so the phone can be handed over,
  then `PrivacyCover` on dismiss.
- **day-time inputs the app must record**: the advice text, the day, the
  impairment flag. Surface it again:
  - as a persistent row in the day panel: `Fisherman — advised day 2: "Keep
    the Outsider claims alive"` with a **Repeat** button that re-opens the
    full-screen card verbatim;
  - in `GameLogDialog` (`GameExtras.kt:46-106`) as
    `D2 · Sarah (Fisherman) used their ability`. **Do not** put the advice
    text in the shared log if the log is ever shown to players; keep the text
    behind the same reveal as storyteller notes.
- **interactions/jinxes**: none.

### C. Compose sheet UI text (storyteller voice)

- Title: `Fisherman — Sarah`
- Body hint: `Give ONE piece of advice. Tell them what to DO, not what is
  true. You can be vague; you cannot be asked for a second one.`
- Impaired banner: `Sarah is poisoned by the Poisoner — you may give bad
  advice. The ability is used up either way.`
- Buttons: `Show full-screen` · `Save & mark spent` · `Cancel`
- Once spent, the row reads: `Fisherman — advised on day 2 · Repeat`

### D. Day-start briefing

Add to the (currently non-existent) day-start briefing, alongside the other
day items this audit round will produce:

- `Unspent day abilities: Fisherman (Sarah), Slayer (Ben).`
- `Sarah (Fisherman) is poisoned today — advice you give her may be bad.`

### E. Data changes

- `characters.json:1370`: no change.
- `night_guide.json`: **no** entry (it is not a night ability). If a shared
  "how to run" store is wanted for day abilities, add a parallel
  `day_guide.json` keyed the same way, with the Fisherman's "How to run" and
  Tips & Tricks text.
- `night_and_jinxes.json`: no change.

## Tests to add

1. `fisherman appears in the day abilities panel while unspent`
   Given a living Fisherman on day 1; Then
   `DayAbilities.forState(state, lookup)` contains a `fisherman` row with
   `spent = false` and `enabled = true`.

2. `using the ability places the no-ability reminder`
   Given a living unspent Fisherman; When the use action runs with advice
   "Keep the Outsider claims alive"; Then the seat holds
   `PlacedReminder("fisherman","No ability")` and the row reports `spent`.

3. `the spent marker survives dawn and dusk`
   Given the state from test 2; When `advancePhase` runs four times; Then the
   reminder is still present. (Regression guard against adding it to the
   expiry tables.)

4. `a spent fisherman cannot use the ability again`
   Given the state from test 2; Then the day-panel row is disabled with reason
   "already used".

5. `a dead fisherman cannot use the ability`
   Given a dead Fisherman with no spent marker; Then the row is disabled with
   reason "dead — no ability".

6. `the advice is recorded with its day and impairment`
   Given a Fisherman with a `("poisoner","Poisoned")` reminder on day 3; When
   the ability is used with text "Execute Lewis"; Then
   `state.dayAbilityUses.last() == DayAbilityUse(day = 3, playerId = …,
   characterId = "fisherman", text = "Execute Lewis", impaired = true)`.

7. `the advice can be repeated verbatim later`
   Given the state from test 6 on day 5; Then the panel's Repeat action yields
   exactly `"Execute Lewis"`.

8. `undo restores the unspent state`
   Given the state from test 2; When the view-model undo runs; Then the
   reminder is gone and `state.dayAbilityUses` is empty. (Everything must go
   through `viewModel.update`, `GameViewModel.kt:~120`, to stay undoable.)

9. `no night step is ever generated for the fisherman`
   Given a Fisherman in play; Then neither `nightOrder.firstNight` nor
   `nightOrder.otherNight` contains a step with id `"fisherman"`. (Guards
   against someone "fixing" the missing night entry.)
