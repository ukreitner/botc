# Apprentice (apprentice) — Bad Moon Rising Traveller

## Official rules (sources)

Sources (fetched 2026-08-25):
- <https://wiki.bloodontheclocktower.com/Apprentice>
- <https://wiki.bloodontheclocktower.com/Travellers>
- <https://wiki.bloodontheclocktower.com/Character_Types>
- Official rulebook, "Travelers" chapter (PDF mirror
  <https://www.web3us.com/sites/default/files/Rulebook.pdf>) — quoted below for
  the traveller-arrival procedure and the exile rules.

Current ability text:

> "On your 1st night, you gain a Townsfolk ability (if good) or a Minion ability
> (if evil)."

`characters.json` has "On your 1st night, you gain a Townsfolk ability (if good),
or a Minion ability (if evil)." — an extra comma only. **No meaningful drift.**

How to run (wiki, verbatim/near-verbatim):

- "During the first night after the Apprentice enters play, wake them. Show the
  YOU ARE token, then a Townsfolk or Minion token. Replace the Apprentice token
  in the Grimoire with that character token, marked with IS THE APPRENTICE
  reminder. The player remains the Apprentice but gains that character's ability.
  Choose a not-in-play character ability, as only one of each token exists."
- "A good Apprentice receives a Townsfolk ability, while an evil Apprentice
  receives a Minion ability, retained until death."
- "The ability is learned on the first night, allowing action that same night if
  the source character would act."
- "Only abilities on the character sheet may be gained." (i.e. only characters on
  the script in play.)
- **"First-night-only abilities function on the Apprentice's first night
  instead."** So an Apprentice who gains the Washerwoman/Librarian/Investigator/
  Chef/Empath/Clockmaker/Shugenja/Steward/Noble/Bounty Hunter etc. gets that
  character's *first-night* information on the Apprentice's first night, even if
  that is night 4 of the game.
- **"The Apprentice remains a Traveller (exilable but not executable) and doesn't
  count toward evil's two-player victory condition. Other character abilities
  detect them as the Apprentice."**
  - Example from the wiki: "When the Gambler guesses the Apprentice is the Tea
    Lady, the Gambler dies because the Apprentice is the Apprentice, not the Tea
    Lady."
- Examples: "An evil Apprentice gains the Assassin ability and kills the Fool
  that night. A good Apprentice gains the Chambermaid ability, learning who wakes
  nightly."
- Edition caution: "if you add an evil Apprentice to Trouble Brewing, and the
  only Minion that the Apprentice can become is the Baron, the Apprentice ability
  is wasted."

Traveller framework that this character depends on (rulebook, verbatim):

- Arrival, five steps: "1) Let the Player Choose a Traveler… 2) **Choose
  Alignment. Tell the Traveler player in private whether they are good or evil.
  If you made the Traveler evil, they learn which player is the Demon, but not
  which players are the Minions.** 3) Adjust Grimoire… Put evil Traveller tokens
  upside-down, and good Traveller tokens right-way-up. 4) Place Life Token…
  5) **Inform Group. Declare that a Traveler is now in play, which player and
  which character it is, and what their ability is. (Do not declare their
  alignment.)**"
- "Travelers may enter the game at any time… Consider adding a Traveler only if
  there are seven or more players alive."
- "Travelers can join the game before the first night, if you wish. **They will
  act on the first night if so.**"
- "Make Travelers good most of the time… travelers should be evil only about a
  third of the time."
- "Travelers… **lose their abilities when dead or drunk or poisoned**, and even
  get a vote token when they die."
- "Travelers do not count toward the evil team winning when just two players
  remain alive."
- Exile: "**If at least half of the players support the exile, it succeeds, and
  the exiled Traveller dies. This counts the total number of players in the game,
  not the number of alive players.**" · "Any player, even dead ones, may support
  the exile… Dead players that support an exile do not lose their vote token." ·
  "The process to exile a Traveler is **not affected by abilities**." · "Calling
  for an exile is not a nomination, so a player who calls for an exile may also
  nominate someone on the same day." · "each Traveler can only be called to exile
  once per day."

Jinxes: none listed for the Apprentice in `night_and_jinxes.json` (58 jinxes, no
traveller ids) and none on the wiki page.

Night order: official first-night sheet has the Apprentice early (after Kazali,
before Barista/Bureaucrat/Thief), i.e. **before Minion info / Demon info** — so
an evil Apprentice who joins for night 1 is already the gained Minion when the
Minions see each other. There is no "other nights" entry, because the Apprentice
wakes exactly once — *on their own first night*, whichever night that is.

## What the app does today

Data
- `characters.json` — `apprentice`, team `traveler`, `reminders: ["Is the
  Apprentice"]`, first-night reminder text matching the official one. Correct.
- `night_and_jinxes.json` — `firstNight[3]` (after `kazali`, before `barista`).
  **Not present in `otherNight`** — matching the printed sheet but not the rule.
- `night_guide.json` — `apprentice.first` only, with a good full instruction
  paragraph and one show card `{label:"Show the Apprentice", kind:"token",
  text:"YOU ARE", token:"pick"}`. **No `other` entry.**

Engine
- `NightOrder.build` (`engine/src/main/kotlin/com/clocktower/engine/NightOrder.kt:40-209`)
  emits the step only when building the **first-night** list. On other nights,
  `apprentice` is not in `otherNightOrder`, so it falls into the homebrew
  fallback at `NightOrder.kt:186-207`, which then drops it because
  `otherNightReminder` is blank and `Character.otherNight == 0`.
  **Net effect: an Apprentice who joins on day 1 or later never gets a night step.**
- `GameActions.assignCharacter` (`GameActions.kt:46-53`) takes an `isTraveller`
  flag and overwrites it on every assignment.
- `Player.nightRoleId` (`GameState.kt:36-42`) is `characterId` except for
  drunk/marionette — there is no concept of "is X but has Y's ability".
- `Player.isEvil` (`GameState.kt:45-51`) is `team.isEvil != alignmentFlipped`;
  `Team.TRAVELLER.isEvil == false` (`Character.kt:16`), so **every traveller
  defaults to good** until the storyteller manually taps "Flip alignment".
- `WinCheck.check` (`WinCheck.kt:19`) drops `isTraveller` seats — correct, and
  the reason the `isTraveller` flag must survive.
- `NightOrder.kt:52` — minion/demon info steps require `players.count { !isTraveller } >= 7`.
- `InfoCalc.supports` (`InfoCalc.kt:29-36`) is keyed on `characterId`; there is
  no route from an Apprentice seat to (say) the Chambermaid calculator.

UI
- Adding the traveller: menu → "Add seat (traveller joins)"
  (`GameShell.kt:255`, dialog at `GameShell.kt:663-682`) asks for a **name only**.
  Then the seat sheet → "Change character" → the Travellers group at the bottom
  of `CharacterPicker` (`SeatSheet.kt:439-451`) assigns with `isTraveller = true`.
- Alignment: only the generic "Flip alignment" button (`SeatSheet.kt:315`). The
  seat header prints "· turned evil" (`SeatSheet.kt:188`) when flipped. Nothing
  prompts for it; nothing shows the player a GOOD/EVIL card at that moment
  (the card exists, but only via menu → "Show a card…" → Signals →
  Good/Evil, `ShowCards.kt:105-127`, `ShowCards.kt:389-391`).
- Night 1 only: the Apprentice step appears with the night-guide prose and a
  "Show the Apprentice" chip that opens `GuideShowDialog` → an unfiltered
  character picker (`NightScreen.kt:360-455`), then a full-screen "YOU ARE
  <token>" card. That part **works**.
- The `Is the Apprentice` reminder is reachable from the night tray while the
  Apprentice step is expanded (`NightScreen.kt:283-352`), but **not** from the
  seat sheet's `ReminderPicker` (`SeatSheet.kt:489-560`), which only lists
  reminders of characters in `gameData.resolve(script)`; built-in scripts are
  built with `.filter { it.team.isTownResident }` (`GameData.kt:39`), so no
  traveller tokens are ever offered there.
- Following the guide's "replace the Apprentice token with that character token"
  through the UI calls `CharacterPicker.onPick(c, false)` → `viewModel.assign(id,
  c.id, isTraveller = false)` (`SeatSheet.kt:91-93`), silently clearing the
  traveller flag.

Storyteller's real experience: they add a seat, pick Apprentice, remember on
their own that they must choose an alignment and tell the player, remember which
Minion/Townsfolk they granted, and then either (a) leave the Apprentice token in
place, in which case the gained character never wakes again and no info is ever
computed, or (b) swap the token, in which case the app stops treating the seat as
a traveller at all.

## Defects and gaps

1. **P0** · An Apprentice who joins after night 1 never gets a night step ·
   Rules: the Apprentice wakes "during the first night after the Apprentice
   enters play"; travellers "may join the game at any time". App: the step only
   exists in `firstNight`, and the other-night fallback filter discards it ·
   `NightOrder.kt:186-193`, `night_and_jinxes.json` `otherNight`,
   `night_guide.json` (`apprentice` has no `other`) · Repro: start a 10-player
   BMR game, advance to day 1, menu → Add seat, assign Apprentice, press Dusk →
   the night 2 sheet has no Apprentice row and no guidance anywhere.

2. **P0** · Following the guide destroys the traveller flag · The guide and the
   wiki both say to replace the token; doing that through "Change character"
   assigns with `isTraveller = false`, so the seat now counts for `WinCheck`
   ("two players alive" evil win, `WinCheck.kt:19,88-98`), can be nominated for
   execution instead of exiled (`DayScreen.kt:163`), changes the 7-player
   threshold for Minion/Demon info (`NightOrder.kt:52`), loses the "T" badge
   (`GrimoireScreen.kt:409-421`) and would be dealt a character by
   `GameActions.deal` (`GameActions.kt:313-329`) · `SeatSheet.kt:91-93`,
   `GameActions.kt:46-53` · Repro: Apprentice seat → Change character →
   Chambermaid → the seat is now a Townsfolk in every derived rule.

3. **P0** · No model for "is the Apprentice, has X's ability" · The rules are
   explicit that other abilities "detect them as the Apprentice" (Gambler,
   Ravenkeeper, Undertaker, Empath alignment reads, Undertaker on an exile, Fang
   Gu/No Dashii team checks), while the *night* behaviour must be X's. The engine
   can express only one of the two · `GameState.kt:19-51`, `NightOrder.kt:46-48`,
   `InfoCalc.kt:29-36`.

4. **P0** · Alignment is never chosen, recorded or shown · Rulebook step 2
   requires the ST to choose the alignment and tell the player in private, and an
   evil traveller "learns which player is the Demon". The app defaults every
   traveller to good (`Character.kt:16`, `GameState.kt:45-51`) and offers only a
   bare "Flip alignment" toggle with no prompt · This is what decides whether the
   Apprentice is shown a Townsfolk or a Minion token, so the app's own night step
   cannot be correct without it · `GameShell.kt:663-682`, `SeatSheet.kt:315`.

5. **P1** · The granted-character picker is unfiltered · The rules require a
   **not-in-play** character **from the script**, **Townsfolk if good / Minion if
   evil**. `GuideShowDialog`'s token picker shows the whole script with no team
   or in-play filtering and no alignment awareness · `NightScreen.kt:360-455`.

6. **P1** · Gained first-night-only information is never computed · "First-night-only
   abilities function on the Apprentice's first night instead." Even in the
   night-1 case, `InfoCalc` is keyed to `step.id == "apprentice"`, which it does
   not support, so the ST gets no Washerwoman/Chef/Empath/Investigator/Clockmaker
   computation and must work it out by hand · `NightScreen.kt:834-880`,
   `InfoCalc.kt:29-36`.

7. **P1** · No "evil Apprentice learns the Demon" step · Rulebook: an evil
   traveller "learn[s] which player is the Demon, but not which players are the
   Minions". Nothing in the app surfaces or prompts this · no code path exists.

8. **P1** · No "inform the group" prompt · Rulebook step 5: declare who the
   traveller is, which character, and their ability, but **not** their alignment.
   Nothing in the app produces this announcement, and there is no public/day log
   entry that a traveller joined · `GameShell.kt:663-682`.

9. **P2** · `Is the Apprentice` token unreachable outside the night tray ·
   `ReminderPicker` never lists traveller reminders because built-in scripts
   exclude travellers · `GameData.kt:33-43`, `SeatSheet.kt:489-560`.

10. **P2** · No misregistration warning · Nothing tells the ST that Gambler
    guesses, Undertaker reads, Ravenkeeper reads and Pit-Hag/Boffin style effects
    see **Apprentice**, not the granted character · `StatusEffects.kt`,
    `InfoCalc.kt`.

11. **P3** · Ability text has a stray comma vs the wiki ("(if good), or a Minion")
    · `characters.json` `apprentice.ability`.

## Proposed behaviour (spec)

### Engine model (needed by this character above all others)

Add to `Player`:

```kotlin
val grantedAbilityId: String? = null   // Apprentice / Cannibal / Philosopher-style
val alignment: Alignment? = null       // explicit; null = derive from team
val joinedOnCycle: Int? = null         // traveller arrival, for "1st night" logic
val firstNightDone: Boolean = false    // has this seat had its personal first night
```

- `nightRoleId` becomes `grantedAbilityId ?: (drunk/marionette shown) ?: characterId`.
- Everything that asks "what character is this player" for *registration*
  (`InfoCalc` reveals, Undertaker, Ravenkeeper, Gambler, team checks, `WinCheck`)
  keeps using `characterId` — so the seat still registers as the Apprentice.
- `isTraveller` is never cleared by `assignCharacter` unless the caller passes an
  explicit new value; granting an ability must not touch it.

### Night step

- **when:** the seat's **own first night** — i.e. the first NIGHT phase in which
  the seat exists with `characterId == "apprentice"` and
  `firstNightDone == false`. This must be emitted on *first and other* night
  sheets alike, at the Apprentice's canonical position (immediately after
  `kazali`, before `barista`, and **before** `MINION_INFO`).
  Wake condition: alive, not yet granted.
- **targets:** one character, not a player. Picker constraints:
  - `alignment == GOOD` → Townsfolk on the script, not in play;
  - `alignment == EVIL` → Minion on the script, not in play;
  - sort not-in-play first; grey out in-play ones with the reason "only one token
    of each exists";
  - warn if the pool is empty or all-Baron-like (setup-only abilities): "This
    script gives an evil Apprentice only <X> — the ability will do nothing."
- **immediate effects:** set `grantedAbilityId`; place `apprentice:"Is the
  Apprentice"` on the seat (exclusive); set `firstNightDone = true`; **do not**
  change `characterId` or `isTraveller`.
- **same-night follow-through:** after granting, the night sheet must
  *immediately* insert the granted character's own step at that character's
  canonical night position **using its FIRST-night text and info calc**, even
  when the game is on night 4. Concretely: build the sheet with the granted
  character's `firstNightReminder`, `night_guide[<granted>].first`, and
  `InfoCalc.compute(<granted>, holder = apprentice seat)`.
  - If the granted character's first-night position has already passed tonight,
    append the step right before `DAWN` with the text "Out of order — <X> acts
    now; the Apprentice only just gained it."
- **subsequent nights:** the seat wakes at the granted character's **other-night**
  position with its other-night text/info. If the granted character has no
  other-night step, no step is emitted.
- **expiry:** `Is the Apprentice` never expires. `grantedAbilityId` persists
  until death ("retained until death") — on death the seat stops waking, like any
  other character.
- **information:** whatever the granted character computes, with the Apprentice
  seat as holder. Impairment applies normally (travellers "lose their abilities
  when… drunk or poisoned").
- **visibility:** the Apprentice sees `YOU ARE` + the granted token, nothing else.
  If the Apprentice is evil, they are shown the Demon on arrival (see below), and
  the Demon/Minions are **not** told about them beyond what the Minion-info step
  already gives (an evil Apprentice present at night 1 is a Minion for the "see
  each other" step; one arriving later is not — the ST simply tells them who the
  Demon is).

### Traveller arrival flow (shared; the Apprentice needs it most)

Replace the bare "Add seat" dialog with a 5-step arrival sheet mirroring the
rulebook:

1. Name + seat position (insert after seat N — `GameActions.addSeat` already
   takes `afterId`, the UI never passes it).
2. Traveller character (list from `GameData.travellersFor(script)`).
3. **Alignment — required.** Two big buttons GOOD / EVIL, with the rulebook's
   balance hint inline: "Make travellers good about two thirds of the time; an
   evil traveller gives evil an extra vote." Sets `alignment` explicitly.
4. If EVIL: a "Show them the Demon" card — `THIS IS THE DEMON` + the Demon's
   token — and an `EVIL` alignment card. If GOOD: a `GOOD` alignment card.
   (Both card types already exist: `ShowCards.kt:105-127`, `ShowCards.kt:143-170`.)
5. "Announce to the group" panel with pre-written text: "<Name> has joined as the
   **Apprentice**: *On your 1st night, you gain a Townsfolk ability (if good) or a
   Minion ability (if evil).*" — and an explicit "do not announce their
   alignment" note. Push a log entry "<Name> joined as the Apprentice (day N)".
   Warn if `alivePlayers.size < 7` ("the rulebook suggests not adding travellers
   below 7 alive").

### Day-time inputs

None for this character beyond the arrival flow.

### UI text for the step

- Title: `Apprentice — <name>'s first night`
- Body: "Wake <name>. Show YOU ARE, then a <Townsfolk|Minion> token. They keep
  that ability for the rest of the game. They are still the Apprentice — Gambler,
  Undertaker and Ravenkeeper all see *Apprentice*."
- After a grant: "<name> now has the **<X>** ability. Their <X> step is on
  tonight's sheet below."

### Data changes

- `night_and_jinxes.json`: no change to the printed lists; instead
  `NightOrder` gains a rule that a seat whose personal first night is tonight is
  slotted at its **first-night** index even when building an other-night sheet.
- `night_guide.json`: add an `apprentice.other` entry with the same text as
  `first` (so the panel renders on any night), and adjust the `first` text to say
  "…and place the Is The Apprentice reminder **without changing their character
  token** — they are still the Apprentice."
- `characters.json`: normalise the ability text to the wiki's.

## Tests to add

1. `Given` a 10-player BMR game on night 2, `when` a seat with
   `characterId="apprentice"`, `isTraveller=true`, `joinedOnCycle=1`,
   `firstNightDone=false` exists, `then`
   `nightOrder.otherNight(state)` contains a step with id `apprentice` at the
   index corresponding to its first-night position (before `barista`).
2. `Given` an Apprentice who has already been granted (`firstNightDone=true`,
   `grantedAbilityId="chambermaid"`), `when` building the other-night sheet,
   `then` there is **no** `apprentice` step and there **is** a `chambermaid`
   step whose `playerIds` contains the Apprentice seat.
3. `Given` an Apprentice granted `washerwoman` on night 3,
   `when` building night 3's sheet after the grant, `then` a `washerwoman` step
   appears and `InfoCalc.compute(data, state, "washerwoman", apprenticeSeatId)`
   returns a non-null Washerwoman result (first-night-only ability runs on the
   Apprentice's first night).
4. `Given` an Apprentice seat, `when` a granted ability is applied,
   `then` `player.isTraveller` is still `true` and `player.characterId` is still
   `"apprentice"`, and `WinCheck.check` with 2 non-traveller players alive plus
   the Apprentice returns `goodWins = false` (traveller excluded).
5. `Given` an evil-flagged Apprentice, `when` the granted-ability picker is
   built, `then` it offers only Minion characters from the script that are not in
   play; `given` a good Apprentice, only such Townsfolk.
6. `Given` an Apprentice granted `tealady`, `when` a Gambler guesses that seat is
   the Tea Lady, `then` the engine's registration lookup for that seat returns
   `apprentice` (the guess is wrong).
7. `Given` a traveller seat created through the arrival action with
   `alignment = EVIL`, `then` `player.isEvil(lookup)` is `true` without any
   `alignmentFlipped` toggling, and the arrival log records "joined as Apprentice".
