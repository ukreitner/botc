# Puzzlemaster (puzzlemaster) — Experimental Outsider

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Puzzlemaster> (fetched 2026-08-25);
interaction cross-check at <https://botc.me/character/puzzlemaster>.

Current ability text: **"1 player is drunk, even if you die. If you guess (once) who it
is, learn the Demon player, but guess wrong & get false info."** (`characters.json`
matches exactly.)

How to Run (wiki bullets, quoted):
- "Mark any player with the Puzzlemaster's **DRUNK** reminder during first night setup.
  This player is drunk."
- "At any time, the Puzzlemaster may guess a player publicly or privately."
- "If the guessed player has the **DRUNK** reminder, tell the Puzzlemaster which player
  is the Demon."
- "If the guessed player does not have the **DRUNK** reminder, tell the Puzzlemaster
  the name of a non-Demon player."
- "Do not indicate whether they guessed correctly."
- "Mark the Puzzlemaster with the **GUESS USED** reminder."
- "While you can make a Minion or the Demon drunk with the Puzzlemaster ability, only do
  this if you have an excellent reason in mind. It is almost always best to select a
  Townsfolk player." (An Outsider is also acceptable per the wiki summary.)

Key constraints and clarifications:
- The guess is **once per game**, and may be made **at any time** — during the day, in
  private, or publicly. It is *not* a night action.
- A **dead Puzzlemaster cannot guess.** The drunk player, however, **stays drunk even
  after the Puzzlemaster dies** — that is the explicit "even if you die" clause.
- Only the drunkenness **caused by the Puzzlemaster** counts as a correct guess. Guessing
  a player who is drunk/poisoned from any other source (the Drunk character, Sailor,
  Innkeeper, Poisoner, No Dashii, Minstrel…) is a **wrong** guess.
- The wrong-guess answer must look identical to the right answer: the Storyteller names a
  player, and never says whether the guess landed.
- The reminder tokens are **DRUNK** and **GUESS USED**.
- The Puzzlemaster may guess **themselves**, and the Storyteller may (technically) place
  the DRUNK token on the Puzzlemaster — botc.me/BGG treat this as legal but pointless.
  *(Flagged: wiki does not say this explicitly. Do not block it; do warn.)*
- Misregistration: the **Recluse** may register as the Demon, so a Puzzlemaster with a
  correct guess may be shown the Recluse ("not recommended", per botc.me). The **Spy**
  may register as good and would not normally be named as the Demon.
- **Vortox does not apply** — Vortox falsifies *Townsfolk* information, and the
  Puzzlemaster is an Outsider. (Rules inference from the Vortox text; flagged as
  inference, not a quoted ruling.)
- botc.me records: "If dead Puzzlemaster is gone from the game, either by being removed
  or by moving the character to another player, the drunk player is sober now." — i.e.
  the drunkenness ends only when the *character* leaves the game (Pit-Hag change,
  Barber swap away), not when the player dies.

Jinxes: **none** documented on the wiki or botc.me.

## What the app does today

Data:
- `engine/src/main/resources/botc/data/characters.json:1714-1727` — id, exp, outsider,
  correct ability text, `setup:false`, `reminders: ["Drunk", "Guess Used"]`, **no**
  first- or other-night reminder.
- Not in the `firstNight`/`otherNight` order lists in `night_and_jinxes.json` — correct,
  the Puzzlemaster has no night action.
- **No `night_guide.json` entry** (the guide is keyed by character id and only contains
  night entries; `engine/.../NightGuide.kt:56-59`).
- No jinxes.

Code: **no Kotlin file mentions `puzzlemaster`.** Everything is generic:
- The DRUNK token can be placed by hand from `ReminderPicker`
  (`app/.../screens/SeatSheet.kt:492-570`, "Rest of script" group) or, once the character
  is in play, from the same picker's "In play" group. There is **no setup step** that
  asks for it.
- Once placed, `StatusEffects.isImpaired` (`engine/.../StatusEffects.kt:36-46`) matches
  any reminder whose lowercased label contains `"drunk"`, so `puzzlemaster:Drunk` **does**
  make the seat drunk for every downstream calculation. *(This part works.)*
- `InfoCalc.impairments` (`engine/.../InfoCalc.kt:133-153`) names the source, so a Fortune
  Teller step will read "…is DRUNK (Puzzlemaster) — give false info." *(Works.)*
- `puzzlemaster:Drunk` is **not** in `EXPIRES_AT_DAWN`/`EXPIRES_AT_DUSK`
  (`engine/.../GameActions.kt:218-242`), so it correctly persists. *(Works.)*
- `GameActions.validateSetupState` (`engine/.../GameActions.kt:503-561`) enforces setup
  choices for `drunk`, `lunatic`, `marionette` and the Fortune Teller's red herring —
  **not** the Puzzlemaster.
- `InfoCalc.supports` (`engine/.../InfoCalc.kt:29-36`) does **not** include
  `puzzlemaster`; `targetsNeeded` (`:22-26`) has no entry either.
- The "Mark spent" chip in `NightToolTray` (`app/.../screens/NightScreen.kt:263-279`)
  only appears for characters whose ability text *starts with* "Once per game" — the
  Puzzlemaster's does not, and in any case there is no Puzzlemaster night step to hang
  it on.
- `app/.../screens/DayScreen.kt` has nomination/vote tooling only — **no place to record
  or resolve a day-time guess.**

Storyteller experience today: nothing tells you the Puzzlemaster needs a DRUNK token at
setup; if you forget, the character silently has no ability and the app will happily
start the game (`validateSetupState` passes). When the Puzzlemaster guesses mid-day you
must remember the rule, find the token in the grimoire, decide the answer, remember not
to reveal correctness, find a way to show a player name (the "Show a card…" tool,
`app/.../screens/GameShell.kt:226-229`), and place a "Guess Used" token by hand from the
seat sheet.

## Defects and gaps

1. **P0 · Setup never asks for the DRUNK token, and the game starts without it.**
   `validateSetupState` (`engine/.../GameActions.kt:503-561`) has branches for
   `drunk`/`lunatic`/`marionette`/`fortuneteller` but not `puzzlemaster`. Repro: build a
   bag containing the Puzzlemaster, tap "Begin night" — the game starts with no
   `puzzlemaster:Drunk` token anywhere, so a whole game's worth of Townsfolk info is
   silently *true* when it should have been false for one player.
2. **P0 · No guess flow at all.** The core interactive half of the ability has zero UI.
   The Storyteller must adjudicate correctness by eye, choose a false answer by hand, and
   avoid tells. Repro: day 2, the Puzzlemaster says "I guess Ana" — there is no screen in
   the app for this.
3. **P0 · Correctness must be judged against the *Puzzlemaster's* token, not "is drunk".**
   The obvious naive implementation (`StatusEffects.isImpaired`,
   `engine/.../StatusEffects.kt:36-46`) would return true for a Sailor-drunk, Innkeeper-
   drunk, Poisoner-poisoned or No Dashii-poisoned seat and produce a **wrong** correct-
   guess. Any implementation must test
   `reminders.any { it.sourceId == "puzzlemaster" && it.label == "Drunk" }` specifically.
   Today there is nothing to get wrong, but the trap is set for the implementer, and the
   grimoire gives the Storyteller no visual distinction between the two kinds of drunk
   beyond reading the token's source.
4. **P1 · "Guess Used" is manual and unenforced.** Nothing marks the guess spent, nothing
   blocks a second guess, and the "Mark spent" affordance
   (`app/.../screens/NightScreen.kt:263-279`) is gated on ability text starting with
   "Once per game" so it would not appear even if a step existed.
5. **P1 · Nothing blocks / warns on a guess by a dead Puzzlemaster.** Rule: a dead
   Puzzlemaster cannot guess. No code path knows.
6. **P1 · No `night_guide.json` entry, so the how-to-run prose is nowhere in the app.**
   The Storyteller cannot look up "what do I say on a wrong guess?" without leaving the
   app. Compare `plaguedoctor` and `snitch`, which do have entries
   (`night_guide.json:1296`, `:1302`).
7. **P1 · The DRUNK token does not survive character removal correctly.** Rule: the
   drunkenness ends if the *Puzzlemaster character* leaves the game (Pit-Hag change,
   Barber swap). `GameActions.assignCharacter` (`engine/.../GameActions.kt:46-53`) and
   `swapCharacters` (`:99-115`) leave the `puzzlemaster:Drunk` token in place on the
   third party. Repro: Pit-Hag turns the Puzzlemaster into a Chef on night 3 — the drunk
   player stays drunk forever.
8. **P2 · No caveat surfacing for misregistration on a correct guess.** Recluse-as-Demon
   and Spy-as-good are exactly the judgement calls the Storyteller needs prompted, and
   `InfoCalc.misregistrationNotes` (`engine/.../InfoCalc.kt:120-131`) already generates
   that text for other characters.
9. **P2 · No warning when the Storyteller drunks a Minion/Demon or the Puzzlemaster
   themselves.** The wiki explicitly says "only do this if you have an excellent reason
   in mind".
10. **P2 · Multi-Demon scripts are unspecified.** With Legion, Riot or a Lil' Monsta
    (token-held Demon), "learn the Demon player" has no single answer. The wiki does not
    address it. Whatever the app does, it must ask rather than pick silently.
11. **P3 · The grimoire cannot visually distinguish "this is the Puzzlemaster's drunk"
    from any other drunk token** at a glance; `GrimoireScreen.kt:471-490` shows only the
    last few reminder labels, all reading "Drunk".

## Proposed behaviour (spec)

The Puzzlemaster has **no night step**. Its spec is a **setup requirement**, a
**persistent status**, and an **any-time day input**.

### Setup

- **when:** SETUP → NIGHT 1 transition.
- **requirement:** add to `GameActions.validateSetupState`
  (`engine/.../GameActions.kt:503-561`):

  ```kotlin
  if (residents.any { it.characterId == "puzzlemaster" }) {
      val drunkSeats = state.players.filter { p ->
          p.reminders.any { it.sourceId == "puzzlemaster" && it.label.equals("Drunk", true) }
      }
      if (drunkSeats.size != 1) issues += "Puzzlemaster: mark exactly one player with the Drunk token"
  }
  ```
- Generalise: the same shape already exists for the Fortune Teller's red herring
  (`:547-559`). Factor both into a table-driven `SETUP_TOKEN_REQUIREMENTS` so Pixie,
  Snitch bluff sets, Puzzlemaster and Fortune Teller all declare their setup needs in one
  place.
- **targets:** any one seat. Picker default/sort: **Townsfolk first** (the wiki's
  recommendation), then Outsiders, then Minions/Demon behind a confirmation
  ("You are drunking an evil player — the wiki advises this only with an excellent
  reason. Continue?"). The Puzzlemaster's own seat is allowed with the same warning.
- **immediate effects:** `PlacedReminder("puzzlemaster", "Drunk")`, placed **exclusively**
  (`placeExclusiveReminder`, `engine/.../GameActions.kt:194-201`) so re-picking moves it
  rather than accumulating.
- **expiry:** never at dawn or dusk. Removed only when the Puzzlemaster character leaves
  play (see below).

### Persistent status

- The token makes the seat drunk for everything — this already works through
  `StatusEffects.isImpaired` (`engine/.../StatusEffects.kt:36-46`) and
  `InfoCalc.impairments` (`engine/.../InfoCalc.kt:133-153`).
- **Survives the Puzzlemaster's death** — no change needed (nothing removes it on death).
- **Removed when the Puzzlemaster character leaves the game.** Hook
  `assignCharacter`/`swapCharacters`: if no seat holds `puzzlemaster` afterwards, strip
  every `puzzlemaster:Drunk` token and raise a dawn/day note
  **"The Puzzlemaster is gone — <Name> is sober now."**
- Grimoire polish: render source-tagged tokens as `Drunk (Puzzlemaster)` when several
  drunk/poison tokens exist on the board, so the Storyteller can adjudicate a guess at a
  glance.

### Day-time input: the guess

New any-phase action, reachable from (a) the Puzzlemaster's seat sheet, (b) a "Day
inputs" section on `DayScreen`, and (c) the claims ledger row if the guess was public.

- **precondition:** the Puzzlemaster seat is **alive** and has no
  `puzzlemaster:Guess Used` token. If dead → the action is disabled with
  **"A dead Puzzlemaster cannot guess."** If already used → **"Guess already used."**
- **targets:** exactly 1 seat, any seat including themselves. Sort: seating order.
- **resolution (engine, `InfoCalc` or a new `DayInfo` object):**

  ```
  correct = target.reminders.any { it.sourceId == "puzzlemaster" && it.label == "Drunk" }
  puzzlemasterImpaired = isImpaired(puzzlemasterSeat)      // Poisoner, No Dashii, another Puzzlemaster…
  if (correct && !puzzlemasterImpaired) answer = the Demon player(s)
  else                                  answer = ANY non-Demon player (Storyteller picks)
  ```
- **UI:** a single panel that shows, Storyteller-only:
  - **"<Target> IS the Puzzlemaster's drunk — the guess is CORRECT."** or
    **"<Target> is NOT the Puzzlemaster's drunk — the guess is WRONG."**
  - the true Demon seat(s), pre-selected when correct;
  - a full seat picker for the answer, with Demons **disabled** on a wrong guess and
    every non-Demon disabled on a correct guess (unless a misregistration caveat is
    accepted — see below);
  - a big **"Show <Name>"** button that puts up `ShowCard.Message("<Name>")` (the existing
    full-screen card machinery, `app/.../components/ShowCards.kt:65-77`) so the answer is
    delivered identically either way;
  - a standing warning: **"Say nothing about whether they were right."**
- **caveats to display** (reuse `InfoCalc.misregistrationNotes`,
  `engine/.../InfoCalc.kt:120-131`):
  - Recluse in play → "The Recluse may register as the Demon — you may name them on a
    correct guess."
  - Spy in play → "The Spy may register as good — naming them as a non-Demon is safe."
  - Puzzlemaster impaired → "<Name> is DRUNK/POISONED (<source>) — you may give any
    answer, right or wrong."
  - Multiple Demons in play (Legion / Riot / Lil' Monsta) →
    "Several players are the Demon — choose which one to name; the wiki does not specify."
  - Vortox in play → "Vortox falsifies **Townsfolk** info only; the Puzzlemaster is an
    Outsider, so this info is not inverted." *(inference — label it as guidance)*
- **immediate effects on confirm:**
  `placeExclusiveReminder(puzzlemasterId, PlacedReminder("puzzlemaster", "Guess Used"))`,
  plus a claims-ledger entry `PublicClaim(cycle, playerId = puzzlemaster, tags = ["puzzlemaster"],
  text = "Guessed <Target>; told <Answer>")` so the game log and the end-game reveal can
  show what happened.
- **deferred effects:** none. Nothing at dawn, dusk or on death.
- **visibility:** only the Puzzlemaster is shown the answer. Nothing is shown to the
  Demon, Minions or Lunatic.

### Day-start briefing

On DAY start, if a Puzzlemaster is alive and unspent, add a one-line briefing item:
**"Puzzlemaster (<Name>) still has their guess."** Same surface as the other day-start
reminders this audit programme is specifying.

### Data changes

- `characters.json:1714-1727` — no ability-text change needed. Consider adding
  `"setup": false` stays, but the new setup-requirement table must list
  `puzzlemaster → ["Drunk"]`.
- `night_guide.json` — add a `puzzlemaster` entry. Since the guide is night-keyed today,
  either (a) add a `"day"`/`"setup"` channel to `NightGuideEntry`
  (`engine/.../NightGuide.kt:36-40`) — preferred, several characters need it — or (b) add
  a `"first"` entry so the prose at least appears somewhere on night 1:

  ```json
  "puzzlemaster": {
    "setup": {
      "instructions": "Before the first night, mark any player with the Puzzlemaster's Drunk reminder — that player is drunk all game, even after the Puzzlemaster dies. A Townsfolk is almost always the right choice; only drunk a Minion or the Demon with an excellent reason.",
      "shows": []
    },
    "day": {
      "instructions": "At any time an alive Puzzlemaster with an unused guess may name a player, publicly or privately. If that player holds the Puzzlemaster's Drunk token, tell the Puzzlemaster which player is the Demon. Otherwise tell them the name of any non-Demon player. Never indicate whether they were right. Mark the Puzzlemaster with Guess Used.",
      "shows": [ {"label":"Name a player","kind":"message","text":"<PLAYER NAME>"} ]
    }
  }
  ```

### UI text

- Setup guard issue: **"Puzzlemaster: mark exactly one player with the Drunk token"**
- Evil-target confirm: **"Drunking a Minion or the Demon is almost never right. Continue?"**
- Guess panel header: **"Puzzlemaster guess (once per game)"**
- Correct: **"CORRECT — <Target> holds the Puzzlemaster's Drunk token. Name the Demon."**
- Wrong: **"WRONG — name any player who is not the Demon."**
- Always: **"Give no sign either way."**
- Spent chip on the seat: **"Guess used"**

## Tests to add

1. *Given* a bag containing the Puzzlemaster and no `puzzlemaster:Drunk` token anywhere,
   *when* `validateSetupState` runs, *then* it reports
   "Puzzlemaster: mark exactly one player with the Drunk token".
2. *Given* two seats both marked `puzzlemaster:Drunk`, *then* `validateSetupState`
   reports the same issue (exactly one required).
3. *Given* a seat with `puzzlemaster:Drunk`, *then* `StatusEffects.isImpaired` is true for
   that seat and false for every other seat.
4. *Given* the Puzzlemaster dies, *then* the `puzzlemaster:Drunk` token is still present
   and `isImpaired` is still true for the drunk seat.
5. *Given* NIGHT→DAY→NIGHT phase advances, *then* `puzzlemaster:Drunk` is **not** cleared
   by `EXPIRES_AT_DAWN`/`EXPIRES_AT_DUSK`.
6. *Given* a seat drunk by the **Sailor** (`sailor:Drunk`) and a guess at that seat,
   *then* the guess resolver returns **wrong** (only the Puzzlemaster's token counts).
7. *Given* a guess at the seat holding `puzzlemaster:Drunk`, *then* the resolver returns
   **correct** and offers exactly the Demon seat(s) as the answer.
8. *Given* a Puzzlemaster who has already guessed (`puzzlemaster:Guess Used` present),
   *then* the guess action is unavailable.
9. *Given* a dead Puzzlemaster, *then* the guess action is unavailable.
10. *Given* a Pit-Hag changes the Puzzlemaster into another character, *then* every
    `puzzlemaster:Drunk` token is removed and the drunk seat's `isImpaired` becomes false.
11. *Given* a Recluse in play and a correct guess, *then* the caveat list includes the
    Recluse misregistration note and the Recluse is selectable as the named "Demon".
12. *Given* a Poisoner-poisoned Puzzlemaster and a correct guess, *then* the caveat list
    says the Storyteller may give any answer, and every seat is selectable.
