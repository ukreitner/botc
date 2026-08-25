# Engineer (engineer) — experimental (Carousel) townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Engineer>

Current ability text (verbatim):

> "Once per game, at night, choose which Minions or which Demon is in play."

`characters.json:1344` matches exactly.

### How to run (verbatim)

> "Each night, wake the Engineer. They either shake their head no or point at
> evil characters on the character sheet.
>
> If they shake their head no, nothing happens. Put the Engineer to sleep.
>
> If they point to a Demon or Minions on their character sheet, put them to
> sleep. Swap all appropriate character tokens with new character tokens. Wake
> each evil player that changed character, show the **YOU ARE** info token,
> then their new character token, then put them to sleep. **The Engineer loses
> their ability** — mark them with the **NO ABILITY** reminder and remove their
> night token from the night sheet."

### Summary / clarifications (verbatim)

- "The Engineer can choose which Minion characters are in play, **or** which
  Demon is in play, **but not both**."
- "When the Engineer creates new in-play characters, the Demon player remains
  the Demon, and the Minion players remain Minions."
- "If the Engineer tries to create an in-play character, that character stays
  as the same player. The Engineer doesn't learn this, and may not use their
  ability again."
- "If creating Minions, the Engineer chooses the same number of Minions that
  should be in play for the number of players."
- "Only characters from the current script may be chosen."
- If they choose too many or too few, "the Storyteller changes as many evil
  players' characters as is fair and feasible."

### Examples (verbatim)

1. "On the second night, the Engineer chooses that the Demon is a Lleech.
   Lewis, who was the Imp, is now the Lleech."
2. "On the first night, the Engineer changes the Baron into the Boomdandy.
   There are still an extra two Outsiders in play."
3. "The Fearmonger and the Psychopath are in play, and causing havoc. The
   Engineer chooses that the Mezepheles and the Spy are in play. The
   Storyteller chooses to change the Fearmonger into the Mezepheles and the
   Psychopath into the Spy."
4. "The Spy, Assassin, and Witch are in play. The Engineer chooses that the
   Spy, Assassin and Mezepheles are in play. The Witch turns into the
   Mezepheles."

Example 2 is load-bearing: **setup abilities do not re-fire mid-game.** The
Baron's two extra Outsiders stay after the Baron is replaced by a Boomdandy.

### Jinxes (verbatim)

- **Legion**: "If Legion is created, all evil players become Legion. If Legion
  is in play, the Engineer starts knowing this but has no ability."
- **Summoner**: "If the living Summoner is removed from play, the Storyteller
  has the Summoner ability."

**Both are missing from `night_and_jinxes.json`** (grep for `engineer` in the
jinx list returns nothing).

### Night order

Both nights, and unusually early: first night after the Marionette and before
the Preacher; other nights after the Sailor and before the Preacher. The app's
data matches the reference ordering (`townsquare` `roles.json`: engineer 13 /
5). Crucially the Engineer acts **after** Minion info and Demon info on night 1
(`MINION_INFO` at index 14, `DEMON_INFO` at 18, `engineer` at 22) and
**before** every evil night action and every good info role.

### Not settled by the wiki (flagged)

- Drunk/poisoned Engineer. The page says nothing. The game's general
  once-per-game convention is that an impaired use is **spent and has no
  effect**; the app's `night_guide` already asserts this. Flagged as
  convention, not a quoted rule.
- Lil' Monsta, Kazali, Lord of Typhon and other Demons with setup or
  positional requirements when created mid-game.

## What the app does today

Works, in one line each:

- Night-order position is correct on both nights (`night_and_jinxes.json:317`
  first, `:383` other).
- `night_guide.json:941` has detailed `first` and `other` instructions, plus a
  `GuideShow(label = "New character", kind = "token", token = "pick")` which
  opens the character picker and can show a full-screen `YOU ARE <token>` card
  (`NightScreen.kt:364-454`, `ShowCards.kt:127-142`).
- `characters.json:1344` carries the official `firstNightReminder` /
  `otherNightReminder` and the `["No ability"]` reminder.
- The "Mark spent" chip in the night tool tray works for this character:
  `oncePerGame` is `ability.startsWith("Once per game")`
  (`NightScreen.kt:204`), so the chip appears and places
  `PlacedReminder("engineer", "No ability")` via `placeExclusiveReminder`
  (`NightScreen.kt:263-279`).
- **The night sheet recomputes when characters change.** `steps` is
  `remember(state.players, …)` (`NightScreen.kt:84-90`) and `NightOrder.build`
  reads `characterId` live, so replacing the Imp with a Lleech mid-night makes
  the Lleech row appear (index 49) and the Imp row disappear (index 37) inside
  the same night — the Engineer's early slot means the new evil characters get
  their turns correctly. This is the one thing the character needs most and it
  already works.
- Setup validation is not re-run after the first night
  (`GameShell.kt:135-142` only calls `validateSetupState` from `Phase.SETUP`),
  so replacing a Baron mid-game does not raise a false "Outsiders: 4 in bag,
  expected 2" alarm — matching wiki example 2.

Storyteller experience:

- The Engineer row appears every night, forever, with the official reminder
  text. There is no "spent" state in the row itself.
- To resolve a use, the storyteller must: read the prose, decide which evil
  seats change, leave the Night tab, open the Grimoire, tap each evil seat,
  tap **Change character** (`SeatSheet.kt:310`), pick, repeat; then come back,
  open the `» New character` card once per changed player and re-pick the
  token in a search box to show it; then tap **Mark spent**.
- Nothing checks the Minion count, the script membership, duplicates, or that
  the Demon stayed the Demon.

## Defects and gaps

1. **P0 · Old character reminder tokens survive the swap.**
   `GameActions.assignCharacter` (`GameActions.kt:46-53`) changes
   `characterId` and clears `shownCharacterId` only. Replacing the Poisoner
   with a Godfather leaves the `("poisoner","Poisoned")` token on its victim,
   who stays impaired for the rest of the game via
   `StatusEffects.isImpaired` (`StatusEffects.kt:36-46`). Same for a Widow's
   "Poisoned", a Witch's "Cursed", a Fearmonger's "Fear", a Devil's Advocate's
   "Survives execution", a Po's "3 attacks", a Zombuul's "Died today".
   Repro: night 1 Poisoner poisons Ben; night 2 Engineer replaces the Poisoner
   with a Godfather; Ben stays "POISONED (Poisoner)" indefinitely.

2. **P0 · Nothing enforces "Minions **or** Demon, not both", the Minion count,
   or script membership.** The storyteller can freely produce an illegal evil
   team through the generic character picker, and the app will happily build a
   night sheet from it. Rules quote: "the same number of Minions that should
   be in play for the number of players"; `Setup.distributionFor`
   (`Setup.kt:86-105`) already computes that number and is not consulted.

3. **P1 · The step never disappears or marks itself spent.** The wiki says
   "remove their night token from the night sheet". App: the row is emitted
   every night regardless of the "No ability" token
   (`NightOrder.kt:142-178` has no spent check), and the dawn guard
   (`GameShell.kt:145-158`) forces the storyteller to tick it every night.

4. **P1 · The whole use is manual and multi-screen.** Compare the Fang Gu jump
   or the Professor resurrection, which each got a single confirmed
   `QuickResolutions` action (`NightScreen.kt:483-517`). The Engineer — the
   single largest state change any Townsfolk can cause — has none.

5. **P1 · The "wake each changed player and show YOU ARE + their token"
   sequence is not driven.** The one `» New character` chip opens a dialog
   with a *search box* and no memory of which seat is being woken
   (`NightScreen.kt:392-435`). With three Minions changed, the storyteller
   does this three times from scratch and must remember the order.

6. **P1 · Missing jinxes: Engineer + Legion and Engineer + Summoner.** Both
   are official and both are rule-changing (Legion: the Engineer has no
   ability at all and *starts knowing* Legion is in play — which is a night-1
   info step that does not exist anywhere in the app). Absent from
   `night_and_jinxes.json`.

7. **P1 · "Chose a character already in play" is unrepresentable.** The rules
   say the ability is **spent with no effect** and the Engineer is not told.
   The app's only "spent" affordance is the same chip used for a successful
   use; there is no way to record "used, nothing happened" other than placing
   the token by hand.

8. **P2 · Demon bluffs are not re-checked after the evil team changes.**
   `state.demonBluffIds` (`GameActions.kt:208-209`) is set once. If the
   Engineer creates a Minion that is one of the Demon's three bluffs, the
   Demon is now bluffing an in-play character and the storyteller should be
   told. Cheap check: `demonBluffIds ∩ inPlayIds`.

9. **P2 · Alignment is not re-normalised on the swap.**
   `assignCharacter` leaves `alignmentFlipped` untouched. If the Engineer
   replaces a Minion who had been flipped good (Pit-Hag, Snake Charmer
   history), the new Minion inherits the flip and silently registers good.
   `starPass` (`GameActions.kt:88-94`) does reset it — `assignCharacter` does
   not.

10. **P2 · Replacing the Marionette or a Demon with a positional requirement
    is unguarded.** `validateSetupState` (`GameActions.kt:503-561`) knows the
    Marionette must neighbour the Demon and that a Lunatic needs a shown Demon
    token; none of those checks run after setup, so an Engineer-created
    Marionette or a Demon change that orphans a Lunatic's shown token goes
    unnoticed.

11. **P2 · No impairment warning on the step.** As with every character
    `InfoCalc` does not support, `StepDetailPanel` renders caveats only inside
    `if (InfoCalc.supports(step.id))` (`NightScreen.kt:836`). A poisoned
    Engineer gets no red flag, and the convention "the use is still spent" is
    only in the guide prose.

12. **P3 · The step detail is the raw reminder text and repeats the guide.**
    `NightOrder.kt:147-148` uses `firstNightReminder` as `detail`, then
    `StepDetailPanel` prints the near-identical guide paragraph.

## Proposed behaviour (spec)

### A. Night step (structured form)

- **when**: `first` and `other`.
- **wake condition**: the Engineer is **alive** and has no
  `("engineer","No ability")` reminder. When spent, emit **no row**.
  If Legion is in play, emit a row on the **first night only** reading
  "Legion jinx: the Engineer starts knowing Legion is in play and has no
  ability — show them the Legion token", then never again.
- **targets**: not players — **characters**. The step needs a character
  picker, in two mutually exclusive modes:
  - `Choose the Demon` — pick exactly 1 Demon from the script.
  - `Choose the Minions` — pick exactly `Setup.distributionFor(n).minions`
    Minions from the script (n = non-Traveller seat count), adjusted by any
    active setup modifiers already in play.
  - `They declined` — no change, no spend, row stays tomorrow.
  Picker constraints and sort:
  - only `gameData.resolve(state.script)` members of the chosen team;
  - already-in-play characters shown but flagged "already in play — this
    choice is wasted"; selecting one is allowed (the rules permit it) and
    still spends the ability;
  - sort: not-in-play first, then alphabetical.
- **immediate effects** (single undoable action
  `GameActions.engineerRebuild(state, engineerId, mode, chosenIds, lookup)`):
  1. Determine the target seats: all Demon-team seats for `Demon` mode, all
     Minion-team seats for `Minions` mode (excluding the Marionette? — see
     open question; default: include, and warn).
  2. Pair chosen characters to seats. Characters already held by a seat in the
     target set keep that seat (rules: "that character stays as the same
     player"); the remainder are assigned in seat order. If counts differ,
     assign as many as possible and surface
     `"You chose N, there should be M — change as many as is fair"`.
  3. For each seat that changes:
     - `assignCharacter(seat, newId)`;
     - **remove every reminder whose `sourceId` is the old character id**, from
       every seat in the grimoire (new helper
       `GameActions.removeRemindersFromSource(state, sourceId)`);
     - reset `alignmentFlipped = false` and `shownCharacterId = null`;
     - keep `isTraveller = false`.
  4. Place `placeExclusiveReminder(engineerId,
     PlacedReminder("engineer","No ability"))` — always, including the
     "declined-but-chose-an-in-play-character" and impaired cases.
  5. If the Engineer is impaired (`StatusEffects.isImpaired`), do **not**
     perform steps 1–3; only step 4.
- **deferred effects**: none.
- **expiry**: `("engineer","No ability")` never expires; do **not** add it to
  `EXPIRES_AT_DAWN`/`EXPIRES_AT_DUSK` (`GameActions.kt:218-242`).
- **information**: none computed. The step is an action, not an info role.
- **visibility**: after the swap, the app must drive a **wake sequence**: a
  list of the changed seats in seat order, each row a single tap that opens
  the full-screen `YOU ARE` + new character token card
  (`ShowCard.CharacterCard("YOU ARE", newId)`), with a tick as each is done.
  Nothing is shown to the Engineer.
- **night-1 note to display**: `"Minion info and Demon info have already run —
  the evil players know each other; you only need to show the new tokens."`

### B. Validation and warnings shown in the step

- `"<n> Minions should be in play for <players> players."`
- `"<Character> is already in play — choosing it wastes the ability (the
  Engineer is not told)."`
- `"<Character> is one of the Demon's bluffs — the Demon is now bluffing an
  in-play character."`
- `"Setup abilities do not re-fire: the Baron's extra Outsiders stay."`
- Marionette / Lunatic / positional demon warnings, reusing
  `validateSetupState`'s per-character checks (`GameActions.kt:513-545`) as a
  reusable `structuralWarnings(state, lookup)` that can be called at any time.
- Summoner jinx: if the removed character was a living Summoner,
  `"Summoner jinx: the Storyteller now has the Summoner ability."`
- Legion jinx: if Legion is chosen, `"All evil players become Legion."` — the
  action should assign `legion` to every evil seat.

### C. UI text for the step

- Title: `Engineer` · badge `once per game`
- Detail: `Wake the Engineer. They shake their head, or point to a Demon, or
  point to <n> Minions.`
- Mode buttons: `They chose the Demon` · `They chose the Minions` ·
  `They declined`
- After choosing: `Cara is now the Godfather · Grace is now the Boomdandy —
  wake each and show YOU ARE + their token.`
- Impaired: `The Engineer is POISONED — nothing changes, but the ability is
  used up.`

### D. Data changes

- `night_and_jinxes.json`: add
  - `{"id1": "engineer", "id2": "legion", "reason": "If Legion is created, all
    evil players become Legion. If Legion is in play, the Engineer starts
    knowing this but has no ability."}`
  - `{"id1": "engineer", "id2": "summoner", "reason": "If the living Summoner
    is removed from play, the Storyteller has the Summoner ability."}`
- `night_guide.json:941`: keep the prose; add the "Minion info already ran"
  note to `first`, and mark the drunk/poisoned sentence as the general
  once-per-game convention rather than an Engineer-specific rule.
- `characters.json:1344`: no change.
- Night order: no change.

## Tests to add

1. `engineer step disappears once spent`
   Given an Engineer with `("engineer","No ability")`; When the night-3 sheet
   is built; Then it contains no `"engineer"` step.

2. `engineer step is absent for a dead engineer`
   Given a dead Engineer with no spent token; Then the night sheet contains no
   `"engineer"` step.

3. `demon swap keeps the seat and clears old demon reminders`
   Given an Imp holding a `("po","3 attacks")`-style reminder and a
   `("imp","Died today")`; When `engineerRebuild(mode = DEMON, ids =
   ["lleech"])` runs; Then that seat's `characterId == "lleech"`, its
   `alignmentFlipped` is false, and no reminder with `sourceId == "imp"`
   remains anywhere in the grimoire.

4. `minion swap removes the old minion's poison from its victim`
   Given a Poisoner with `("poisoner","Poisoned")` on Ben; When the Engineer
   replaces the Poisoner with a Godfather; Then Ben has no reminder with
   `sourceId == "poisoner"` and `StatusEffects.isImpaired(state, lookup, ben)`
   is false.

5. `choosing an already-in-play character spends the ability and changes nothing`
   Given the Spy, Assassin and Witch in play; When the Engineer chooses Spy,
   Assassin and Witch; Then all three seats are unchanged and the Engineer has
   `("engineer","No ability")`.

6. `partial overlap keeps the matching seats`
   Given Spy, Assassin, Witch in play; When the Engineer chooses Spy, Assassin,
   Mezepheles; Then only the Witch's seat changes, to `mezepheles` (wiki
   example 4).

7. `impaired engineer spends the ability with no effect`
   Given a poisoned Engineer; When `engineerRebuild(mode = DEMON, ids =
   ["lleech"])` runs; Then the Demon seat is unchanged and the Engineer has
   `("engineer","No ability")`.

8. `new demon gets its own night step in the same night`
   Given an Imp and an Engineer on night 2; When the Engineer makes the Demon
   a Lleech; Then rebuilding the other-night sheet yields a `"lleech"` step and
   no `"imp"` step, and the Lleech step is positioned after the Al-Hadikhia
   slot. (Guards the existing reactive behaviour.)

9. `setup modifiers do not re-fire mid-game`
   Given a Baron game with 2 extra Outsiders; When the Engineer replaces the
   Baron with a Boomdandy on night 1; Then the Outsider count is unchanged and
   `validateSetupState` is not invoked by the phase advance (wiki example 2).

10. `minion count validation`
    Given 12 non-Traveller players (2 Minions expected) and an Engineer
    choosing 3 Minions; Then `engineerRebuild` returns a warning naming the
    expected count and changes at most the available Minion seats.

11. `summoner jinx warning`
    Given a living Summoner among the Minions; When the Engineer replaces the
    Minions without choosing the Summoner; Then the action's warnings contain
    the Summoner jinx text.

12. `legion choice converts every evil player`
    Given Legion on the script and 3 evil seats; When the Engineer chooses
    Legion; Then all three seats have `characterId == "legion"`.
