# Hatter (hatter) — Experimental Outsider

## Official rules (sources)

Sources:
- <https://wiki.bloodontheclocktower.com/Hatter>
- <https://wiki.bloodontheclocktower.com/Marionette> (the Marionette is never woken for it)

Current ability text (matches `characters.json:1636`):

> "If you died today or tonight, the Minion & Demon players may choose new Minion & Demon characters
> to be."

Summary: "The Hatter allows the Minions & Demon to change characters."

How to Run (quoted):

> "If the Hatter dies, mark them with the **TEA PARTY TONIGHT** reminder."
>
> "During that night, wake the Minions and Demon."
>
> "Show them the **THIS CHARACTER SELECTED YOU** info token, then the Hatter token."
>
> "Each player either shakes their head no or points to another character of the same type as their
> current character."
>
> "If a second player would end up with the same character as another player, shake your head no
> and gesture for them to choose again."
>
> "Put them to sleep. Remove the **TEA PARTY TONIGHT** reminder. Change each player to the character
> they chose."
>
> "**Optional rule**: if the Hatter dies, allow the Demon to become a Minion, and a Minion to become
> a Demon."

Clarifications (quoted):

- "If a player becomes a new character, they gain the new ability, even if it was a 'you start
  knowing' ability or a once per game ability that had already been used."
- "Once a player has changed character, their previous character ability has no further effect on
  the game."
- "Once a character has been chosen, a second player cannot choose the same character. If it is
  already in play, the player with that character must choose a new character."
- "If a player dies then becomes the Hatter, the evil players do not change characters tonight."
- Marionette page: "The Marionette is not woken due to character abilities that would confirm that
  they are a M[inion] eg. Snitch, Preacher, Lil' Monsta, Poppy Grower, Hatter, Damsel."

Timing, derived from the above and the night order:

- The Hatter's slot is on the **other-nights** sheet only, at `night_and_jinxes.json:431` (index 58,
  between `gossip` and `barber`) — i.e. **after** every Demon kill and after Assassin/Godfather.
  So a Hatter killed by the Demon tonight triggers the tea party **the same night**; a Hatter
  executed today triggers it the following night. That is exactly "died today or tonight".
- The tea party happens **once**, on that one night; the TEA PARTY TONIGHT token is removed
  afterwards.
- The wiki does **not** state what happens if the Hatter is drunk/poisoned when they die. Standard
  ruling (and what `night_guide.json:1279` already says) is that the ability does not function and
  nobody changes. **Flagged as a standard ruling, not a quoted rule.**

Jinxes (wiki):

| Partner | Text |
|---|---|
| Legion | "If Legion is created, all evil players become Legion. If Legion is in play, the Hatter has no ability." |
| Leviathan | "The Leviathan cannot enter play after day 5." |
| Lil' Monsta | "If the Hatter dies & the Demon chooses Lil' Monsta, they also choose a Minion to become." |
| Summoner | "If the Summoner creates a second living Demon, deaths tonight are arbitrary." |

**None of these four jinxes exist in `night_and_jinxes.json`** (grep for `hatter` in the jinx list
returns nothing).

## What the app does today

Data:
- `characters.json:1633-1645` — correct ability text; `reminders: ["Tea Party Tonight"]`;
  `otherNightReminder` = "If the Hatter died, wake the Minions & Demons. Each may choose a new
  character. If they do, show the 'You are' token & their new character token."
- `night_guide.json:1277-1289` — a genuinely good prose run-book, including the duplicate rule, the
  "Minions choose Minion characters" constraint and the impaired case. It has **one** show card:
  "» New character" (`kind:"token", token:"pick"`) — a single card, for what may be up to four
  players.
- `night_and_jinxes.json:431` — other-night order position, correct. No `first` guide entry
  (correct).
- No `hatter` jinxes (see above).

Engine: **zero** Hatter-specific code. Grep for `hatter` in `engine/src/main/kotlin` returns nothing.
The character-change primitives that exist are `GameActions.assignCharacter`
(`GameActions.kt:46-53`) and `GameActions.swapCharacters` (`GameActions.kt:99-115`); neither is
wired to the Hatter.

UI walk-through as the storyteller experiences it:
1. **Hatter dies** (executed on day 2, say, via `DayScreen.kt:111-114` or `SeatSheet.kt:274`).
   Nothing happens. No TEA PARTY TONIGHT token is placed; `StatusEffects.deathNotes`
   (`StatusEffects.kt:94-103`) has cases for Ravenkeeper/Sage/Farmer/Moonchild/Sweetheart/Barber/
   Poppy Grower/King — but **not** the Hatter. The ST gets no warning at all that this death has a
   consequence.
2. **Night 3, step "Hatter".** The row appears — but it appears on **every** other night from night
   2 onwards, alive Hatter or dead, because `NightOrder.kt:142-145` emits any in-play character's
   row unconditionally.
3. Worse, `NightScreen.kt:702` computes `allDead` and `NightScreen.kt:751-757` prints
   **"All holders are dead — usually skip."** in red on that row. For the Hatter this is exactly
   backwards: the row only matters *because* the Hatter is dead. The same inverted advice hits the
   Barber, Sweetheart, Moonchild and Plague Doctor rows.
4. **Resolution.** `QuickResolutions` (`NightScreen.kt:462-525`) has cases for `snakecharmer`,
   `fanggu`, `professor` and "any Demon" — nothing for the Hatter. The ST gets the prose plus one
   "» New character" pick-a-token card, then must:
   - work out for themselves which seats are Minions and which is the Demon;
   - remember which characters are already in play to police duplicates;
   - open each evil seat's sheet → "Change character" → scroll a full script list
     (`SeatSheet.kt:88-96`, `SeatSheet.kt:388-453`) — the picker shows *every* team, with no
     filter to "Minion characters only" / "Demon characters only", and only a faint "in play"
     label rather than a block on duplicates;
   - re-open the show card for each player individually;
   - remember to give the new character's "you start knowing" info and reset spent once-per-game
     tokens.
5. **Stale reminders.** `assignCharacter` (`GameActions.kt:46-53`) keeps `reminders` untouched, so
   a Poisoner who becomes a Godfather leaves a live `poisoner:Poisoned` token on a victim, and
   `StatusEffects.isImpaired` (`StatusEffects.kt:36-46`) keeps returning true — directly
   contradicting "their previous character ability has no further effect on the game".
6. **Bag validation.** `GameActions.validateBag` runs only at setup, so a post-Hatter duplicate is
   never caught.
7. **Lunatic / Demon-info coupling.** `NightOrder.kt:157-171` appends the Lunatic annotation to
   "the Demon's step" by looking up the current Demon character id — that keeps working after a
   Demon change, which is fine. But nothing prompts the ST to re-show the new Demon to a Lunatic's
   real-Demon step or to update a Marionette's neighbour requirement.

## Defects and gaps

1. **P0 · Nothing tells the storyteller that the Hatter's death has any consequence.**
   Rules: "If the Hatter dies, mark them with the TEA PARTY TONIGHT reminder." App:
   `StatusEffects.deathNotes` (`StatusEffects.kt:94-103`) has no `hatter` case, so killing the
   Hatter — from the Day screen, the seat sheet, or the Demon kill panel — produces no note, no
   token and no dawn/day-start reminder. Repro: execute the Hatter on day 2; nothing anywhere
   mentions a tea party.

2. **P0 · The Hatter night row appears on every other night, whether or not the Hatter has died,
   and is labelled "usually skip" precisely when it matters.** Rules: the step exists only on the
   night after (or of) the Hatter's death. App: `NightOrder.kt:142-145` + `NightScreen.kt:751-757`.
   Repro: Hatter alive, night 2 — a "Hatter" row appears saying "If the Hatter died, wake the
   Minions & Demons"; Hatter dead, night 3 — the same row now also says "All holders are dead —
   usually skip." An ST following the app skips the step.

3. **P0 · Four official jinxes are missing from the data.** Legion ("If Legion is in play, the
   Hatter has no ability"), Leviathan, Lil' Monsta and Summoner are absent from
   `night_and_jinxes.json`. The Legion one is a whole-ability negation: an ST running Legion +
   Hatter will run a tea party that should not happen, and the "Jinxes in play" dialog
   (`GameExtras.kt:200-232`) will show nothing.

4. **P1 · The character change is fully manual and unconstrained.** Rules: Minions choose Minion
   characters, the Demon chooses a Demon character, no duplicates, "if it is already in play, the
   player with that character must choose a new character." App: the generic `CharacterPicker`
   (`SeatSheet.kt:388-453`) offers the whole script, groups by team but does not restrict, and
   marks in-play characters only with a grey "in play" label.

5. **P1 · Old character reminders survive a character change.** Rules: "their previous character
   ability has no further effect on the game." App: `GameActions.assignCharacter` (`:46-53`) does
   not sweep `reminders` on the changed seat **or** tokens elsewhere sourced from that character
   (e.g. `poisoner:Poisoned` on a victim, `devilsadvocate:Survives execution`, `witch:Cursed`,
   `fearmonger:Fear`, `cerenovus:Mad`). Repro: Poisoner poisons Ana on night 2, Hatter dies, the
   Poisoner becomes a Baron on night 3 — Ana is still flagged poisoned by `isImpaired`.

6. **P1 · New "you start knowing" info and reset once-per-game abilities are not re-run.**
   Rules: quoted above. App: no mechanism; `InfoCalc` is only reachable from that character's own
   night row, and a first-night-only role (Washerwoman, Librarian, Investigator, Chef, Steward,
   Noble, Balloonist's first look, Bounty Hunter, Snitch, Grandmother, Clockmaker, Shugenja,
   High Priestess…) has **no** row on an other-night sheet at all, so the ST cannot even reach the
   calculator. This is the same defect the user reported for the Professor.

7. **P1 · Spent once-per-game tokens are not cleared.** A Minion who becomes an Assassin/Godfather/
   Mastermind with an existing `<old>:No ability` reminder keeps a token that
   `NightScreen.kt:263-265` and `InfoCalc.impairments` (`InfoCalc.kt:143`) read as "no ability".

8. **P1 · The Marionette must not be woken, but the app treats them as a Minion for this step.**
   Rules (Marionette page): "The Marionette is not woken due to character abilities that would
   confirm that they are a Minion eg. … Hatter …". App: there is no Hatter step logic at all, and
   the night guide says "Wake the Minions and the Demon together" with no exclusion.
   (`NightOrder.kt:61-63` already knows how to exclude the Marionette from Minion Info — the same
   filter is needed here.)

9. **P2 · "If a player dies then becomes the Hatter, the evil players do not change characters
   tonight."** No branch; an ST using a Pit-Hag/Amnesiac to make a dead player the Hatter gets no
   guidance.

10. **P2 · One show card for up to four players.** `night_guide.json:1284-1288` has a single
    "» New character" card; the ST re-opens the dialog and re-picks a token per player, and there
    is no "THIS CHARACTER SELECTED YOU" + Hatter-token card at all (the wiki's first card).

11. **P2 · The optional rule (Demon↔Minion swap) is not offered.** It changes team counts and the
    win condition, so it needs to be a deliberate toggle rather than an unwritten house rule.

12. **P3 · `characters.json:1640` says "Minions & Demons" (plural Demons)** — matches the official
    almanac text but reads oddly in a single-Demon game; the guide text is better.

## Proposed behaviour (spec)

### On the Hatter's death (any cause, any phase)

- `StatusEffects.deathNotes` gains: `"hatter" -> "Hatter: the Minions & Demon may each choose a new character tonight."`
- Automatically place `PlacedReminder("hatter", "Tea Party Tonight")` on the Hatter seat when the
  death is recorded (same hook that will place Barber's "Haircuts tonight" and Sweetheart's Drunk).
- Snapshot `abilityImpairedAtDeath` — `DeathRecord` already stores it (`GameState.kt:87`,
  written at `GameActions.kt:153`). Use that snapshot, not the current state, to decide whether the
  tea party functions.
- If the Hatter dies during the **day**, add a dusk/day-start line: **"Tea party tonight — the evil
  team may change characters."**

### Night step

- **when:** other nights only; emit the row **only if** a seat carries `hatter:Tea Party Tonight`
  (equivalently: a Hatter death exists whose `day == state.cycle` and which has not yet been
  resolved). Never on the first night.
- **wake condition:** skip entirely (and say why) if:
  - the Hatter's `abilityImpairedAtDeath == true` → "Hatter was drunk/poisoned — nobody changes.";
  - a Legion is in play → "Legion jinx — the Hatter has no ability.";
  - the player who died had only just become the Hatter after dying → ST-confirmed skip.
- **participants:** every seat whose current character's team is MINION or DEMON, **excluding** any
  seat with `characterId == "marionette"`. Present them as an ordered checklist: Demon first, then
  Minions in seat order.
- **immediate effects, per participant** (a `QuickResolutions` case, `NightScreen.kt:470`):
  - Show `[THIS CHARACTER SELECTED YOU] + Hatter token` (new show card).
  - `[No change]` or `[Choose new character]`.
  - The picker is filtered to the participant's **own team** on this script (Demon → Demon
    characters; Minion → Minion characters), minus every character currently in play
    (`state.players.mapNotNull { it.characterId }`), minus characters already picked by another
    participant tonight. Duplicates are impossible by construction rather than by ST vigilance.
  - Optional-rule toggle "Allow Demon↔Minion swaps" widens the filter to Minion+Demon for all
    participants and warns that team counts change.
  - On confirm, a new engine action `GameActions.becomeCharacter(state, playerId, newId)`:
    - sets `characterId = newId`, `shownCharacterId = null`, keeps `alignmentFlipped` (evil stays
      evil);
    - removes every reminder on **this** seat whose `sourceId == oldId`;
    - removes every reminder on **every** seat whose `sourceId == oldId` (the old ability has no
      further effect) — with a listed summary the ST can undo;
    - returns the list of swept tokens so the UI can say "Removed: Poisoned (Poisoner) from Ana".
  - Show `[YOU ARE] + new character token` to that player.
- **after all participants:** remove `hatter:Tea Party Tonight`.

### Re-running the new characters' information

For each player who changed, the app must queue **"new-character info owed"**:
- If the new character has a first-night-only info ability, insert a row into **tonight's** sheet
  (immediately after the Hatter row, or at that character's canonical other-night position if it
  has one) titled `"<Name> — new character (first-night info)"`, running `InfoCalc.compute` with
  the *first-night* semantics.
- If the new character has an other-night ability that is positioned **later** tonight (e.g.
  Assassin at index 55 is before the Hatter at 58 — so it is already past), the row must be
  inserted right after the Hatter step with a note "out of order — the Hatter changed them after
  their slot."
- Clear any `<oldId>:No ability` token so a once-per-game ability is genuinely fresh.
- Surface unresolved ones in the dawn/day-start briefing: "Owed: give Ben (now the Godfather) his
  new-character information."

### Expiry

- `hatter:Tea Party Tonight` — removed by the step itself; also force-removed at dawn so a skipped
  step cannot linger (add `"hatter" to "Tea Party Tonight"` to `EXPIRES_AT_DAWN`,
  `GameActions.kt:218-225`).

### Visibility

- Each evil player sees: THIS CHARACTER SELECTED YOU → Hatter token → (if they change) YOU ARE →
  new character token. They are woken **together** per the wiki ("wake the Minions and Demon"), but
  the app should let the ST run them one at a time and tick each off.
- The Marionette is never woken and never changes.
- Good players learn nothing. The Lunatic learns nothing; if the Demon changed, the Demon's step
  annotation (`NightOrder.kt:157-171`) follows the new character automatically.

### Data changes

- `night_and_jinxes.json` — add the four missing jinxes:
  - `hatter`×`legion`: "If Legion is created, all evil players become Legion. If Legion is in play, the Hatter has no ability."
  - `hatter`×`leviathan`: "The Leviathan cannot enter play after day 5."
  - `hatter`×`lilmonsta`: "If the Hatter dies & the Demon chooses Lil' Monsta, they also choose a Minion to become."
  - `hatter`×`summoner`: "If the Summoner creates a second living Demon, deaths tonight are arbitrary."
- `night_guide.json:1277` — add a second show card
  `{"label":"Hatter selected you","kind":"token","text":"THIS CHARACTER SELECTED YOU","token":"self"}`
  before the existing "New character" card, and add the Marionette exclusion sentence.

### UI text for the step

- Title: **"Hatter — tea party"**.
- Detail: **"<Hatter> died. Wake the Demon and each Minion (not the Marionette). Each may become a
  new character of their own type. No duplicates."**
- Skip banners: **"Hatter was drunk/poisoned when they died — nobody changes."** /
  **"Legion is in play — the Hatter has no ability."**

## Tests to add

1. `StatusEffectsTest`: *Given* an alive Hatter, *when* `deathNotes` is asked about that seat,
   *then* it contains "tea party" / "may each choose a new character tonight". Fails today.
2. `GameActionsTest`: *Given* the Hatter is executed on day 2, *then* the Hatter seat carries
   `hatter:Tea Party Tonight` immediately after the kill. Fails today.
3. `NightOrderTest`: *Given* an **alive** Hatter, *when* the night-2 sheet is built, *then* there is
   **no** `hatter` row. Fails today.
4. `NightOrderTest`: *Given* a Hatter who died on day 2, *when* the night-3 sheet is built, *then*
   there **is** a `hatter` row and its detail names the Demon and the Minions but **not** the
   Marionette.
5. `NightOrderTest`: *Given* a dead Hatter and a Legion in play, *then* the `hatter` row (if
   emitted) says the Hatter has no ability.
6. `GameActionsTest` (`becomeCharacter`): *Given* a Poisoner who poisoned Ana, *when* the Poisoner
   becomes the Baron, *then* Ana has no `poisoner:Poisoned` token and
   `StatusEffects.isImpaired(Ana)` is false. Fails today.
7. `GameActionsTest`: *Given* a Minion with `assassin:No ability`, *when* they become the
   Godfather, *then* the seat has no leftover `assassin:*` tokens.
8. `GameActionsTest`: *Given* a tea party where the Demon picks a character a Minion also picked,
   *then* the second pick is rejected (the candidate list excludes it).
9. `GameActionsTest`: *Given* a Hatter who was poisoned at the moment of death
   (`abilityImpairedAtDeath == true`), *then* the tea party step reports "nobody changes".
10. `GameDataTest`: *Given* `listOf("hatter","legion")`, *then* `activeJinxes` is non-empty. Fails
    today (jinx missing from data).
