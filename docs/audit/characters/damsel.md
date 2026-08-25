# Damsel (damsel) — Experimental Outsider

## Official rules (sources)

Sources:
- <https://wiki.bloodontheclocktower.com/Damsel>
- <https://wiki.bloodontheclocktower.com/Huntsman> (the `[+the Damsel]` companion)

Current ability text (matches `characters.json:1609`):

> "All Minions know a Damsel is in play. If a Minion publicly guesses you (once), your team loses."

How to Run (quoted):

> "During the first night, wake each Minion. Show the Damsel token. Put each Minion to sleep."
>
> "At any time during the game, if a Minion publicly guesses which player is the Damsel and is
> incorrect, mark the Damsel with the GUESS USED reminder."
>
> "Future guesses by Minion players have no effect."
>
> "At any time during the game, if a Minion publicly guesses which player is the Damsel and is
> correct, the game ends."
>
> "Declare that the evil team wins."

Examples (quoted):

> "Marianna is the Damsel. She is bluffing as the Lycanthrope. The Witch guesses that Marianna is
> the Damsel. Evil wins."
>
> "Doug is the Damsel. The Boomdandy guesses that Julian is the Damsel. Nothing happens, and the
> game continues."
>
> "The Goblin guesses that Doug is the Damsel. Nothing happens, and the game continues."

Clarifications from the page (quoted where the wiki states them):

- **One guess for the whole evil team, not one per Minion.** "No matter how many Minions are in
  play, they only get one guess, total." The Boomdandy/Goblin example above is the canonical
  illustration: the first (wrong) Minion guess burns the team's single guess.
- **Only a Minion's guess counts.** "If the Demon pretends to be a Minion making a guess, that
  doesn't count as a guess. Minions may still make a guess and win." (Travellers are not Minions
  either.)
- **The guess must be public.** A whispered/private guess does nothing and does not burn the guess.
- **A dead Damsel is safe.** "If the Damsel dies, they are no longer at risk of being guessed by a
  Minion, since the Damsel loses their ability when dead." So a correct guess after the Damsel has
  died does nothing. (It is a Storyteller call whether it burns the team's guess; the page does not
  say. Treat it as *not* burning the guess, since the ability is not functioning.)
- **Huntsman transformation:** "If the Huntsman chooses the Damsel at night, the Damsel becomes a
  not-in-play Townsfolk, and is no longer the Damsel. The Damsel learns which Townsfolk and has
  that Townsfolk ability from then on." From the Huntsman page: "wake the Damsel, show the YOU ARE
  info token, then a not-in-play Townsfolk token, then put the Damsel to sleep… replace the Damsel
  character token with this Townsfolk character token." The Huntsman example makes clear the new
  character's ability is live *immediately*: "The Damsel becomes the Undertaker and learns which
  player died today."
- **Timing of the Minion reveal:** the official first-night reminder (`characters.json:1611`) is
  "During Minion Info, show the Minions the Damsel token. If you haven't done this yet, do so now."
  — i.e. the reveal belongs to the **Minion Info** step; the Damsel's own night-order slot exists
  only as the catch-up point and as the slot where a Huntsman-chosen Damsel is shown their new
  character.

Jinxes (wiki; app data at `night_and_jinxes.json:33-53`):

| Partner | Text | In app? |
|---|---|---|
| Spy | "If the Spy is (or has been) in play, the Damsel is poisoned." | yes (line 35) |
| Widow | "If the Widow is (or has been) in play, the Damsel is poisoned." | yes (line 40) |
| Marionette | "The Marionette does not learn that a Damsel is in play." | yes (line 45) |
| Pit-Hag | "If a Pit-Hag creates a Damsel, the Storyteller chooses which player it is." | yes (line 50) |

Consequence of the Spy/Widow jinx that the app never surfaces: a **permanently poisoned** Damsel
has no ability at all, so a correct Minion guess does **nothing** — the game does not end. This is
a whole-game standing state set at setup, not a nightly token.

Related: `huntsman` × `marionette` jinx (line 105) — "If the Marionette thinks that they are the
Huntsman, the Damsel was added."

## What the app does today

Data:
- `characters.json:1605-1617` — correct current ability text, `reminders: ["Guess Used"]`, and both
  night reminders.
- `night_guide.json:1253-1276` — a good, accurate prose run-book for both nights, including "The
  Damsel themself is not woken" and the Poppy Grower catch-up case.
- `night_and_jinxes.json:339` (firstNight index 44, immediately after `huntsman` at 338) and
  `night_and_jinxes.json:439` (otherNight index 66, immediately after `huntsman` at 438). Order
  matches the official sheet.

Engine:
- `Setup.kt:76` `COMPANIONS["huntsman"] = "damsel"`, `Setup.kt:219` adds the extra Outsider slot for
  `[+the Damsel]`, `GameActions.kt:368-372` forces the Damsel into a random bag,
  `GameActions.kt:480-485` reports "huntsman requires the damsel in the bag" during validation.
  **Works** — covered by `SetupTest.kt:114` and `GameActionsTest.kt:241`.
- Nothing else in the engine mentions the Damsel. `WinCheck.check` (`WinCheck.kt:18-101`) has no
  Damsel branch. `StatusEffects` has no Damsel branch. `InfoCalc.supports` does not include it.

UI (storyteller's actual experience):
1. **Night 1, Minion info step.** `NightOrder.kt:60-80` builds the step text as "Wake all Minions
   (…). They see each other, then point out the Demon (…)." — **no mention of the Damsel at all**,
   even though the Damsel is in play and this is the moment the token must be shown.
   `NightScreen.kt:783-788` adds a "Show bluffs" chip only for `DEMON_INFO`.
2. **Night 1, step 44 "Damsel".** The row appears with the Damsel's own `firstNightReminder` text,
   plus the night-guide prose and a "» To Minions" show-card chip that renders "THIS CHARACTER IS
   IN PLAY" over the Damsel token (`night_guide.json:1257-1264`, rendered by
   `NightScreen.kt:802-831`). So the ST *is* eventually told — roughly 30 checklist rows after the
   moment they needed it, and the show card has no per-Minion loop and no Marionette exclusion.
3. **Every other night.** The Damsel row is emitted unconditionally (`NightOrder.kt:142-178`
   only checks that a holder exists), showing "If the Damsel was chosen by the Huntsman…". If the
   Huntsman is not in play, or has already spent their ability, the row is pure noise the ST must
   tick off every single night, and the "Night checklist incomplete" guard
   (`GameShell.kt:147-161`) will block dawn until they do.
4. **The guess itself.** There is no day-time input anywhere. `DayScreen.kt` has nominations/votes
   only. To record a wrong guess the ST must open the seat sheet → "Add reminder" → find "Damsel"
   in the "In play" list → tap "Guess Used" (`SeatSheet.kt:492-571`). Nothing reads that token
   afterwards: a second Minion guess produces no warning, and a *correct* guess produces no
   end-of-game advisory — the ST must remember the rule and use the menu's "Declare evil victory"
   (`GameShell.kt:262-265`).
5. **Spy/Widow jinx.** Visible only as text in the seat sheet's jinx list (`SeatSheet.kt:222-235`)
   and the "Jinxes in play" dialog (`GameExtras.kt:200-232`). No poison token is placed, so
   `StatusEffects.isImpaired` returns false for the Damsel and the app would happily let the ST
   declare an evil win on a guess that should have done nothing.
6. **Huntsman resolution.** The Huntsman step has a "» Damsel becomes" pick-a-token show card
   (`night_guide.json:1041-1046`) and the tray's "Mark spent" chip fires because the Huntsman's
   ability starts with "Once per game" (`NightScreen.kt:204, 263-279`). But changing the Damsel's
   actual character is a separate manual trip to the seat sheet → "Change character"
   (`SeatSheet.kt:310`), and `GameActions.assignCharacter` (`GameActions.kt:46-53`) leaves the old
   `Guess Used` reminder in place and gives no first-night/that-night info for the new character.

## Defects and gaps

1. **P0 · A correct Minion guess produces no win advisory.**
   Rules: "if a Minion publicly guesses which player is the Damsel and is correct, the game ends.
   Declare that the evil team wins." App: `WinCheck.check` (`WinCheck.kt:18`) knows nothing about
   the Damsel; the only path is the menu's manual "Declare evil victory". Repro: play a Damsel
   game, have a Minion guess correctly — nothing in the UI changes.

2. **P0 · The Spy/Widow jinx (permanently poisoned Damsel) is not applied, so the app will confirm
   a win that should not happen.** Rules: "If the Spy is (or has been) in play, the Damsel is
   poisoned" ⇒ no ability ⇒ a correct guess does nothing. App: the jinx is text only
   (`night_and_jinxes.json:35,40`); no reminder is placed at setup, `StatusEffects.isImpaired`
   (`StatusEffects.kt:36-46`) sees nothing. Repro: bag with Spy + Damsel, start the game — the
   Damsel seat shows no poison; nothing warns the ST when a guess is called.

3. **P0 · The Minion Info step never tells the storyteller to show the Damsel token.**
   Rules: "During the first night, wake each Minion. Show the Damsel token." App:
   `NightOrder.kt:68-79` builds Minion Info text with no Damsel clause and no show card; the
   instruction only appears 30 rows later at the Damsel's own step
   (`night_and_jinxes.json:339`). Repro: 8-player game with Damsel + 1 Minion, open Night 1 → the
   "Minion info" row says nothing about the Damsel. This is exactly the class of bug the brief
   describes: the app knows the fact but does not surface it at the moment it is needed.

4. **P1 · No day-time input to record a Damsel guess.** The ST must know the rule, decide whether
   the guess was public and by a Minion, then hand-place a reminder token from a two-level picker.
   There is no "a Minion guessed X" action anywhere (`DayScreen.kt` has none), and nothing records
   *who* guessed or *when* for the log (`GameExtras.kt:46-106`).

5. **P1 · The "Guess Used" token is inert.** `placeExclusiveReminder`/`addReminder` store it
   (`GameActions.kt:186-201`) and nothing ever reads it. A second guess is neither blocked nor
   flagged; a correct second guess would be treated exactly like the first.

6. **P1 · Nothing knows a dead Damsel is safe.** Rules: "the Damsel loses their ability when dead."
   The app has no branch at all, so an ST who does not remember the ruling will end the game on a
   guess against a dead Damsel.

7. **P1 · The Damsel's other-night row fires every night regardless of the Huntsman.** Rules: this
   slot only matters if the Huntsman chose the Damsel *tonight*. App: `NightOrder.kt:142-145`
   emits any in-play character's row unconditionally; `GameShell.kt:147-161` then refuses "Dawn"
   until it is ticked. Repro: Damsel in play, Huntsman not in play (Pit-Hag-made Damsel, or a
   script with Damsel but no Huntsman) — a dead row appears on every night.

8. **P1 · The Huntsman→Damsel transformation is not automated end-to-end.** Rules: the Damsel
   *becomes* the not-in-play Townsfolk and "has that Townsfolk ability from then on", and gets that
   character's information that same night (wiki example: becomes the Undertaker and "learns which
   player died today"). App: three disconnected manual steps (show card, seat-sheet character
   change, remember to run the new character's night step). `assignCharacter` (`GameActions.kt:46`)
   also leaves stale Damsel reminders (`Guess Used`) on the seat.

9. **P2 · The Damsel show card is a single full-screen card with no per-Minion loop and no
   Marionette exclusion.** Rules + jinx: each Minion is woken and shown the token; "The Marionette
   does not learn that a Damsel is in play." App: one `ShowCard.CharacterCard` chip
   (`night_guide.json:1257-1264`), no list of which seats to show it to.

10. **P2 · Nothing distinguishes "a Minion guessed" from "the Demon guessed".** Rules: "If the
    Demon pretends to be a Minion making a guess, that doesn't count as a guess." The app cannot
    help because it never takes the guess as input.

11. **P3 · Night-order data drift risk.** `characters.json:1611` correctly carries "During Minion
    Info…", but the app's night sheet renders that sentence at the Damsel's own late slot, which
    makes the data read as if the Damsel step *is* the reveal moment.

## Proposed behaviour (spec)

### Setup

- When the bag is dealt and the Damsel is in play, if **Spy or Widow is in the bag**, place a
  standing `PlacedReminder("spy"/"widow", "Poisoned")` on the Damsel seat and record it in
  `state.storytellerNotes`-equivalent structured state so `isImpaired` returns true for the whole
  game. This token must **not** be in `EXPIRES_AT_DAWN`/`EXPIRES_AT_DUSK`.
  - Surface at setup: "Spy is in play — the Damsel is poisoned all game. A correct Minion guess
    does nothing."
  - If a Spy/Widow *enters* play later (Pit-Hag, Hatter, Amnesiac), apply the same token then and
    say so ("has been in play" is enough).
- Add `damselGuessUsed: Boolean` (or reuse the `damsel:Guess Used` token as the single source of
  truth) to game state so day-time logic can read it.

### Night 1 — Minion Info (not a separate Damsel step)

- when: first night, at the `MINION_INFO` marker, whenever any seat's `characterId == "damsel"`.
- visibility: append to the Minion Info step detail and offer a show card:
  - detail: `"Also show each Minion the DAMSEL token: <Minion names, excluding the Marionette>."`
  - show card: `kind:"token", token:"damsel", text:"THIS CHARACTER IS IN PLAY"`, presented as a
    per-seat checklist so the ST can tick off each Minion.
- Marionette exclusion is automatic (`NightOrder.kt:61-63` already filters `marionette` out of the
  Minion list — reuse that list).
- If the Minion Info step is skipped (Poppy Grower in play, or <7 players), raise the same prompt
  at the first night on which Minion Info actually happens, and show it in the day-start briefing
  as an outstanding task ("Minions have not yet been shown the Damsel token").

### The Damsel's own night-order slot

- when: first *and* other nights, but **only if** `huntsmanChoseDamselTonight` is true (set by the
  Huntsman step's resolver). Otherwise the row must not be emitted at all.
- Rename the row to "Damsel — Huntsman transformation" so the purpose is obvious.

### Huntsman resolution (one-tap, in the Huntsman step)

- targets: living players, sorted with the Damsel *not* highlighted (no tell). On confirm:
  1. place `PlacedReminder("huntsman","No ability")` on the Huntsman (already possible via the
     tray's "Mark spent", but must be automatic and must fire even when the Huntsman is impaired —
     "the use is still spent");
  2. if the chosen seat is the Damsel **and** neither Huntsman nor Damsel is impaired: open a
     not-in-play-Townsfolk picker, set the Damsel's `characterId` to it, clear `shownCharacterId`,
     **drop every reminder whose `sourceId == "damsel"`**, and clear the Damsel poison token if it
     came from the Spy/Widow jinx (they are no longer the Damsel);
  3. show the "YOU ARE" + new-character card;
  4. **re-run that character's night step for this player tonight**: insert the new character's
     row into tonight's sheet (first-night text if the character only has a first-night ability,
     e.g. Washerwoman/Chef, otherwise the current night's text) and jump to it. This is the same
     "re-run the first night for a changed player" behaviour the user asked for on the Professor.
- If either is impaired: spend the use, show nothing, leave the Damsel as the Damsel, and tell the
  ST: "Huntsman is drunk/poisoned — the use is spent but the Damsel does not change."

### Day-time input the app must let the ST record

Add a **"Damsel guess"** action to the Day screen (and to the day-start briefing while a Damsel is
in play and the guess is unused):

- Input: `guesser` (chip row of seats, evil seats first) and `guessed` (chip row of all seats).
- The engine resolves and tells the ST what happens, and the ST confirms:
  - guesser is not a Minion (Demon, Traveller, or good) → "Not a Minion — no effect, the guess is
    NOT used." (Cite: "If the Demon pretends to be a Minion making a guess, that doesn't count.")
  - Damsel is dead → "The Damsel is dead and has no ability — no effect."
  - Damsel is poisoned/drunk (Spy/Widow jinx or any poison token) → "The Damsel is poisoned — no
    effect." (Storyteller may still choose to burn the guess; default is not to.)
  - guess already used (`damsel:Guess Used` present) → "Evil already used their one guess — no
    effect."
  - guessed ≠ Damsel, everything functioning → place `damsel:Guess Used` on the Damsel seat and
    announce "Wrong guess — evil has used their one guess. Nothing happens publicly."
  - guessed == Damsel, everything functioning → return a `WinCheck.Advisory(goodWins = false,
    reason = "A Minion publicly guessed the Damsel — the evil team wins.")` and open the reveal
    flow.
- Log every guess in the game log (`GameExtras.kt:46`): "D2 — Ana (Goblin) guessed Ben is the
  Damsel — wrong, guess used."

### Expiry

- `damsel:Guess Used` — never expires.
- Spy/Widow-derived Damsel poison — never expires (remove only if the Damsel stops being the
  Damsel).

### Information / visibility

- Minions: the Damsel token at Minion Info (excluding the Marionette).
- Demon: nothing.
- Damsel player: nothing on night 1; on transformation, "YOU ARE" + the new Townsfolk token.

### Day-start briefing lines the app should show

- While a Damsel is alive and the guess is unused: "Damsel in play — a Minion's one public guess is
  still available." (Storyteller-only.)
- After a wrong guess: "Evil's Damsel guess is spent — further guesses do nothing."
- If Spy/Widow ever in play: "Damsel is poisoned (Spy) — guesses do nothing."
- If the Minions have not yet been shown the Damsel token: "Owed: show the Minions the Damsel
  token."

### UI text for the steps

- Minion Info step, extra line: **"Show each Minion the DAMSEL token (not the Marionette). They do
  not learn who it is."**
- Damsel step (only when triggered): **"Huntsman chose the Damsel. Show 'YOU ARE' and a not-in-play
  Townsfolk token, then swap their token."**

### Data changes

- `night_guide.json:1253` `damsel.first` — retarget the prose at the Minion Info step and drop
  "During Minion Info… if you haven't done this yet" from the *Damsel row* (the row should only
  exist for the Huntsman case).
- No `characters.json` change needed; the ability text matches the wiki.
- Add the missing consequence of the Spy/Widow jinx to `night_and_jinxes.json:36,41` reasons, e.g.
  "…the Damsel is poisoned — a correct Minion guess does nothing."

### Interactions/jinxes to handle explicitly

- **Spy / Widow** — permanent poison (above).
- **Marionette** — excluded from the Damsel reveal; also, if the Marionette believes they are the
  Huntsman, the Damsel *was* added at setup (`night_and_jinxes.json:105`) — the setup validator
  should allow that Outsider count.
- **Pit-Hag** — if a Pit-Hag creates a Damsel, the ST chooses which player; the app should offer
  the "who becomes the Damsel" picker and then run the owed Minion reveal that night.
- **Huntsman** — see above; also the Huntsman may only choose a **living** player.
- **Hatter** — a Minion who becomes a different Minion still shares the same single team guess; a
  Minion who becomes the Demon can no longer guess.
- **Vortox / Exorcist / protection** — no interaction.

## Tests to add

1. `WinCheckTest`: *Given* a game with an alive, unpoisoned Damsel and a Goblin, *when* the engine
   is told the Goblin publicly guessed the Damsel, *then* `WinCheck.check` returns
   `Advisory(goodWins = false, reason contains "Damsel")`. Fails today (no branch).
2. `WinCheckTest`: *Given* the same game but the Damsel is dead, *when* the correct guess is
   recorded, *then* no advisory is produced and no `Guess Used` token is placed.
3. `StatusEffectsTest`: *Given* a bag containing `spy` and `damsel`, *when* the game starts,
   *then* `StatusEffects.isImpaired(damselSeat)` is true. Fails today.
4. `GameActionsTest`: *Given* a Damsel with a `damsel:Guess Used` token, *when* a second Minion
   guess (correct) is recorded, *then* the result is "no effect" and no advisory is produced.
5. `NightOrderTest`: *Given* a Damsel in play in an 8-player game, *when* the first-night sheet is
   built, *then* the `MINION_INFO` step detail contains "DAMSEL token" and lists the Minion names
   excluding any Marionette. Fails today.
6. `NightOrderTest`: *Given* a Damsel in play and **no** Huntsman, *when* the other-night sheet is
   built, *then* it contains **no** `damsel` row. Fails today.
7. `GameActionsTest`: *Given* a Huntsman who chooses the Damsel while sober, *when* the resolver
   runs with a chosen not-in-play Townsfolk, *then* the Damsel seat's `characterId` is that
   Townsfolk, all `sourceId == "damsel"` reminders are gone, and the Huntsman carries
   `huntsman:No ability`.
8. `GameActionsTest`: *Given* a **poisoned** Huntsman who chooses the Damsel, *then* the Damsel's
   `characterId` is unchanged **and** `huntsman:No ability` is still placed.
9. `GameDataTest`: *Given* `listOf("huntsman","damsel","marionette")`, *then* `activeJinxes`
   includes both `marionette×damsel` and `marionette×huntsman`.
