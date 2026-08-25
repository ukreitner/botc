# Ojo (ojo) — Experimental Demon

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Ojo> (fetched 2026-08-25), jinx list
<https://wiki.bloodontheclocktower.com/Djinn>.

**Current ability text (verbatim):**
> "Each night\*, choose a character: they die. If they are not in play, the Storyteller chooses who dies."

**How to Run (verbatim, complete):**
> "Each night except the first, wake the Ojo. The Ojo player points to a character icon on their character sheet. If that character is in play, that player dies—mark them with the 'Dead' reminder. If that character is not in play, choose any player. That player dies—mark them with the 'Dead' reminder. Put the Ojo to sleep.
>
> If the Ojo is on a script with a Demon that kills multiple times per night, such as the Shabaloth, and the Ojo chooses a not-in-play character, you may choose more than one player to kill that night. This helps the Ojo player pretend that a different Demon is in play."

**Examples (verbatim, complete):**
> "The Ojo chooses the Plague Doctor. The Plague Doctor dies. The next night, the Ojo chooses the Poppy Grower. The Poppy Grower dies. The next night, the Ojo chooses the Empath. There is no Empath in play, so the Storyteller chooses that the Shugenja dies instead."

**Clarifications from the wiki's storyteller sections:**
- The choice is a **character, not a player**. "Remind players of this and ensure all
  have character sheets available during night phases."
- "If the Ojo chooses a character that is not in play, the Storyteller will almost
  always kill a living good player. It is possible, but uncommon, for the Storyteller
  to choose a dead player or an evil player to die."
- "If multiple copies of one character exist and Ojo selects that character, only one
  dies." (Village Idiot, Legion, Riot.)
- No first-night action.

**Jinxes:** none. The Ojo appears in no jinx on the Djinn page.

**Uncertain (flagged, not guessed):** the wiki does **not** address
- whether the Drunk/Lunatic/Marionette's *shown* character counts as "in play" (by
  the standard definition of "in play" — the character token is in the grimoire — it
  does **not**: the Drunk's believed Townsfolk is not in play, so naming it is a
  miss and the ST chooses);
- Spy/Recluse misregistration for "in play";
- what happens when the named character's player is already dead (they *are* in
  play, so on a literal reading nothing further happens — the kill is spent);
- what happens if the Ojo names the Ojo, or a Traveller.
The repo's own `night_guide.json:1647` already invents the phrase "(or only
registers as such)", which is a guess. The spec below surfaces these as explicit
storyteller choices rather than silently resolving them.

## What the app does today

**Data — all correct.**
- `characters.json:2060-2072` — ability at `:2064` matches the wiki exactly;
  `setup: false`; `firstNightReminder: ""` (`:2066`); `otherNightReminder: "The Ojo
  chooses a character."` (`:2067`); `reminders: ["Dead"]`.
- `night_and_jinxes.json` — **absent** from `firstNight`, present in `otherNight` at
  index 47 (`:420`), between `vigormortis` and `alhadikhia`. **Works.**
- `night_guide.json:1645-1657` — `other` only, no `first`. **Works.** The prose is
  accurate apart from the invented misregistration clause, and it offers a "CHOOSE A
  CHARACTER TO KILL…" message card.

**Night 2+ (the storyteller's actual experience)**
- The step detail is "The Ojo chooses a character."
- `StepDetailPanel` renders the guide prose and the one message card
  (`NightScreen.kt:792-831`), then `QuickResolutions` (`:462-525`) falls to the
  `else` branch (`:518-523`) and renders **`DemonKillPanel`** (`:534-638`).
- `DemonKillPanel` presents **a row of player chips** ("Demon kill — who did
  <name> choose?", `:543-547`, `:555-584`). There is no way to record, or even
  express, a *character*. The ST must:
  1. hear/see the character the Ojo pointed at,
  2. work out from memory whether it is in play,
  3. work out which seat holds it,
  4. tap that seat.
  Steps 2 and 3 are exactly the lookup the grimoire already knows.
- The `NightToolTray`'s "Sheet" chip (`NightScreen.kt:254-262`) builds
  `ShowCard.SheetCard` from every non-Fabled script character — this is the right
  artefact for the Ojo to point at, and it works — but it is a generic tray button,
  not part of the Ojo's step, and it captures nothing.
- Nothing is recorded anywhere about which character was named. The game log
  (`GameExtras.kt:46-105`) only shows deaths and nominations.
- Multi-kill scripts: no support for "you may choose more than one player" when the
  named character is not in play; `DemonKillPanel` kills exactly one target and then
  clears its selection (`:626-633`).

**What works, in one line:** the night-order position, the absence of a first-night
step, the reminder set, the guide prose, the impaired-demon warning
(`NightScreen.kt:548-554`) and the `deathNotes` protection review (`:588-590`).

## Defects and gaps

1. **P0 · The Ojo's input modality is wrong: the app asks for a player.**
   Rules: "choose a character". The app offers only a player picker
   (`NightScreen.kt:534-638`, reached from `:518-523`). Repro: any Ojo game, Night
   tab, night 2, expand the Ojo step — "Demon kill — who did X choose?" with seat
   chips. The whole not-in-play branch of the ability is invisible.

2. **P0 · The in-play / not-in-play determination is left to the storyteller.**
   The app holds the authoritative answer (`state.players.mapNotNull {
   it.characterId }`, already computed for other purposes at
   `NightScreen.kt:406` and `GameActions.kt:122`) and never uses it here. A tired ST
   at 1am mis-remembering whether the Empath is in play changes who dies. Repro: as
   above — nothing on screen says which characters are in play.

3. **P1 · Nothing records which character the Ojo named.**
   This is the single most useful piece of evidence in an Ojo game (it tells the ST
   what the Ojo believes is in play, drives bluff choices, and is needed to explain
   the game afterwards). There is no per-night structured record — `GameState` has
   only a free-text `storytellerNotes` (`GameState.kt:112`) and per-player `note`
   (`:31`).

4. **P1 · The "Storyteller chooses who dies" fallback has no UI.**
   When the named character is not in play the ST needs a *deliberately different*
   picker: default to living good players, with dead and evil players available but
   visibly discouraged (per the wiki: "almost always … a living good player. It is
   possible, but uncommon, … a dead player or an evil player"). Today it is the same
   undifferentiated chip row.

5. **P1 · Multi-copy characters are not handled.**
   Rules: "If multiple copies of one character exist and Ojo selects that character,
   only one dies." `GameActions.DUPLICABLE` (`GameActions.kt:413`) already names
   `villageidiot`, `legion`, `riot`, so the app knows duplicates are possible; the
   Ojo step must ask *which* copy.

6. **P1 · The multi-kill allowance is unsupported.**
   Rules: on a script with a multi-killing Demon, a not-in-play choice may kill more
   than one player. `DemonKillPanel` kills exactly one. Repro: Ojo + Shabaloth
   script, Ojo names a not-in-play character — the ST must kill twice by hand from
   two different screens.

7. **P2 · The guide invents a rule.**
   `night_guide.json:1647` — "if it is not in play (or only registers as such)". The
   wiki says nothing about misregistration here. Either cite a source or turn it into
   an explicit ST choice.

8. **P2 · Drunk / Lunatic / Marionette shown-characters are ambiguous in the
   picker's eyes.** `Player.nightRoleId` (`GameState.kt:39-44`) deliberately treats
   the Drunk's and Marionette's *shown* character as their night role, and
   `NightScreen.kt:406` computes "in play" from `characterId`. A character picker
   built naively from either field will get the Ojo's answer wrong in one direction
   or the other. The spec below fixes the definition explicitly.

9. **P2 · Holder resolution by first seat index.**
   `NightScreen.kt:467` (`step.playerIds.firstOrNull()`) plus `:520`
   (`holder.alive`). `GameActions.starPass` (`:79-96`) leaves the dying demon's
   `characterId` in place, so a script where the Ojo can arrive via Pit-Hag, Kazali,
   Summoner or a star pass can produce two `ojo` seats; the lower seat index drives
   the panel and, if dead, the Ojo's step offers no tools. Same root cause as the
   cross-cutting star-pass defect.

10. **P3 · The Ojo's step never offers its own "Sheet" card.**
    `ShowCard.SheetCard` (`ShowCards.kt:77`) is exactly the "character sheet" the
    rules tell the Ojo to point at, but it lives in the tray, not in the step.

## Proposed behaviour (spec)

### Night action — structured form

- **when:** other nights only. Wake condition: the Ojo seat is alive and not marked
  `exorcist:Chosen`. (No first-night step — already correct in the data.)
- **targets:** exactly **1 character** (not a player), then conditionally 1..N
  players.
- **picker:** a **character** picker, not a seat picker. It must:
  - list every character on the script (excluding Fabled and Travellers by default,
    with a "show Travellers" toggle), reusing `CharacterPicker`
    (`SeatSheet.kt:388-…`) or the section layout already written for
    `GuideShowDialog` (`NightScreen.kt:392-435`, which partitions "In play" /
    "Not in play");
  - **not** reveal which characters are in play until the ST has committed the
    choice — the ST is transcribing the Ojo's pointing finger, and a picker
    pre-sorted by in-play would leak. Sort alphabetically within team, in the
    script's team order, exactly like the printed sheet the Ojo is looking at;
  - include a "Show the character sheet" button that fires
    `ShowCard.SheetCard(scriptIds)` so the Ojo can point at the phone.
- **in-play definition (explicit):** a character `c` is *in play* iff some seat has
  `player.characterId == c`. The Drunk's and Marionette's `shownCharacterId` do
  **not** count, and neither does a demon bluff. Dead players **do** count. Encode
  this as one engine helper, `InPlay.holdersOf(state, characterId): List<Player>`,
  and use it everywhere (it also fixes the Ojo, Dreamer-style info and the bluff
  picker consistently).
- **immediate effects, branching on `holdersOf(chosen)`:**
  - **exactly one holder** → show the seat, the standing `deathNotes` protection
    review, and one confirm button "<Name> (<Character>) dies". If that holder is
    already **dead**, say so and offer two buttons: "The kill is wasted (no one
    dies)" (default) and "Storyteller chooses another player instead" — flagged in
    the UI as a storyteller ruling, because the wiki does not cover it.
  - **more than one holder** (Village Idiot / Legion / Riot) → "Only one dies —
    which?" with the holders as chips, then confirm.
  - **zero holders (not in play)** → the storyteller-choice picker:
    - heading: *"<Character> is not in play — you choose who dies."*
    - candidates sorted: living good players first (the wiki's "almost always"),
      then living evil, then dead, with the latter two groups under a collapsed
      "uncommon" divider;
    - if the script contains a Demon that kills more than once per night
      (`shabaloth`, `alhadikhia`, `lilmonsta`, `legion`, `yaggababble`, or any
      script character whose ability text matches `/kills? (up to )?\d|two players/i`),
      allow **multi-select** with the note "You may kill more than one so the Ojo can
      look like the <Demon>."
  - the Ojo naming **its own character** resolves through the normal one-holder
    branch (the Ojo dies); surface a red confirmation, since it ends the game for
    evil on most scripts.
- **impaired:** keep the existing warning (`NightScreen.kt:548-554`) and add "the
  attack fails — record the character they named, then choose 'No kill'", because
  the *named character* is still worth recording even when the kill fails.
- **kills:** `GameActions.kill(target, DeathCause.DEMON, lookup)` per victim, after
  the `deathNotes` review.
- **deferred effects:** none. Deaths are announced at dawn by the existing DAWN step
  (`NightOrder.kt:59`).
- **expiry:** none. `ojo:Dead` is a bookkeeping token only.
- **information:** none shown to the Ojo. What the *storyteller* is shown: the
  in-play/not-in-play verdict, the holder(s), and the protection notes.
- **visibility:** nothing extra for Minions or the Lunatic.
- **record (new, and the point of the exercise):** append a structured night action
  to game state —
  `NightAction(cycle, characterId = "ojo", namedCharacterId, resolvedAs =
  IN_PLAY | NOT_IN_PLAY | MULTIPLE | DEAD_HOLDER, victimIds, impaired)`.
  Surface it in the game log (`GameExtras.kt:46-105`) as *"Night 3 — Ojo named the
  Empath (not in play); Storyteller killed Shugenja"*, and show the running list of
  previously named characters inside the Ojo's step: *"Named so far: Plague Doctor
  (hit), Poppy Grower (hit), Empath (miss)."* Nothing in the rules limits repeats,
  but the ST wants the history.

### Day-time inputs
None.

### Interactions/jinxes to handle explicitly
- **None official.** But note for the implementer:
  - **Drunk / Marionette / Lunatic**: their believed character is *not* in play; the
    step must show *"the <Character> token is not in the grimoire — this is a miss"*
    without revealing why.
  - **Spy / Recluse**: the wiki does not extend misregistration to "in play" for the
    Ojo. Do not auto-apply it; instead, when the named character is a Minion/Demon
    and a Recluse is in play (or a Townsfolk/Outsider and a Spy is in play), add one
    grey line: *"Storyteller ruling: you may let the Recluse register as the
    <Character>."* Never resolve it silently.
  - **Exorcist**: `NightOrder.kt:150-154` already appends the "does not act tonight"
    text. Works.
  - **Protection (Monk, Soldier, Innkeeper, Tea Lady, Fool, Sailor)**: reached via
    the existing `StatusEffects.deathNotes`. If the in-play holder is protected, the
    kill fails and **no substitute** is chosen — the Ojo's choice was spent. Say so
    explicitly, because a storyteller will be tempted to re-roll.

### Data changes
- `night_guide.json:1647` — remove "(or only registers as such)"; add the
  multiple-copies rule and the multi-kill allowance verbatim from the wiki.
- No `characters.json` change needed (the entry is already correct).
- No night-order change needed.

### UI text for the step
- Header: **"The Ojo chooses a CHARACTER, not a player."**
- Buttons: "Show the character sheet" · "They pointed at…" (opens the character
  picker).
- After the pick, one of: "<Character> is in play — <Name> dies." /
  "<Character> is in play but already dead." / "<Character> is not in play — you
  choose who dies." / "<Character> is in play twice — only one dies."

## Tests to add

1. `Given` an Ojo game where the Empath is not in play, `When` the Ojo names
   `empath`, `Then` the resolver returns `NOT_IN_PLAY` with an empty holder list and
   a candidate list whose first entries are living good players.
2. `Given` the Plague Doctor is in play and alive, `When` the Ojo names
   `plaguedoctor`, `Then` the resolver returns exactly that seat and killing it
   records `DeathCause.DEMON`.
3. `Given` a Drunk shown the Empath token and no real Empath, `When` the Ojo names
   `empath`, `Then` the resolver returns `NOT_IN_PLAY` (the shown character does not
   count).
4. `Given` three Village Idiots, `When` the Ojo names `villageidiot`, `Then` the
   resolver returns `MULTIPLE` with three holders and killing resolves exactly one.
5. `Given` the named character's only holder is already dead, `When` resolved,
   `Then` `DEAD_HOLDER` is returned and no automatic kill happens.
6. `Given` an Ojo + Shabaloth script and a not-in-play choice, `Then` the resolver
   permits more than one victim; `Given` an Ojo-only script, `Then` it permits one.
7. `Given` the in-play holder is marked `monk:Safe`, `When` the kill is attempted,
   `Then` no one dies and the state records the attempt (no substitute victim).
8. `Given` a first-night state, `Then` `NightOrder.firstNight` contains **no** `ojo`
   step (regression guard on the data).
9. `Given` two seats with `characterId == "ojo"`, one dead, `Then` the step's
   resolved holder is the living one.
10. `Given` the Ojo names a character across three nights, `Then` the night-action
    record contains all three named ids in order.
