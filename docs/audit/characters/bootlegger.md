# Bootlegger (bootlegger) — fabled Fabled

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Bootlegger> (fetched verbatim via
`api.php?action=parse&page=Bootlegger&prop=wikitext`, 2026-08-25).

Current ability text (verbatim summary line):

> "This script has homebrew characters or rules."

**Type note (drift):** the wiki now types the Bootlegger as **Loric** (categories
`Experimental Characters`, `Loric`; revealed 28/04/2023), not Fabled. The app's
`characters.json` types it `fabled`. In practice it behaves as a Fabled — a token on the
grimoire that announces a rules change — and the wiki's own
[Fabled](https://wiki.bloodontheclocktower.com/Fabled) page does not list it. Worth a
one-line label in the UI ("Loric"), no mechanical consequence.

**Summary bullets (verbatim):**

- "Add the Bootlegger to include homebrew characters or rules."
- "The Bootlegger allows Storytellers to use characters they, or others, have created that are not official characters or allows them to use non-standard rules in the game."
- "If there are homebrew characters on the character sheet, or homebrew rules in effect, the Storyteller tells all players what they are before play begins."
- "The Bootlegger allows for multiple characters or rules to be in effect at once."
- "As long as there is at least one homebrew character on the current script, this Loric will be in play and can only be removed by switching to a script that does not contain any homebrew characters."
- "The Bootlegger is designed for use in the official app only."

**How to Run (verbatim):**

> At the start of the game, if there are homebrew characters on the character sheet or you are running homebrew rules, declare that the Bootlegger is in play and inform the group of all the homebrew characters and/or rules you are using in this game.

**Examples (verbatim):**

1. "The character sheet contains the homebrew character the Peasant. The Storyteller announces that the Bootlegger is in play and then explains how the Peasant works."
2. "The Storyteller has a homebrew or house rule. The Storyteller announces that the Bootlegger is in play and explains what the homebrew rule is and how it will affect the game."

**What this means for an app:** the Bootlegger has no in-game mechanic. It is a
**pre-game announcement obligation** plus **automatic presence**: if the script carries a
homebrew character, the Bootlegger is in play whether the storyteller remembers or not, and
it cannot be removed while that character is on the script. It is the natural home for the
"house rules we're playing with" list — the one thing every storyteller has to say out loud
at the start of a homebrew game.

**Jinxes:** none.
**Night order:** never wakes. Correctly absent from both order lists.

## What the app does today

Data:
- `characters.json:2147-2158` — ability text matches the wiki exactly; `edition: fabled`,
  `team: fabled`, `setup: false`, no reminders, no night reminders. **Works** as data.
- `night_and_jinxes.json` — correctly absent from both order lists. **Works.**
- `night_guide.json` — no entry (nothing to run at night; correct).

Code — the app has real homebrew support, but nothing connects it to the Bootlegger:
- `Script.kt:25` — `Script.customCharacters: List<Character>`;
  `ScriptParser.parse` (`Script.kt:55-81`) turns any script-JSON entry carrying a `team`
  field into a full custom `Character`, including `image`, `reminders`,
  `firstNight`/`otherNight` order numbers.
- `GameData.resolve` (`GameData.kt:49-52`) and `GameData.unknownIds` (`GameData.kt:54-57`)
  merge/report custom ids.
- `SetupScreen.kt:189-190` — a script card shows "· N homebrew"; `SetupScreen.kt:195-200`
  shows "Unknown ids skipped: …" in red.
- `NightOrder.kt:183-207` — custom characters are slotted into the night sheet before
  `DAWN`, sorted by the `firstNight`/`otherNight` numbers from the script JSON, titled
  "<Name> (homebrew)".
- `GameViewModel.kt:190, 260` — `characterById` falls back to the script's custom list.
- `GameShell.kt:571` and `SetupScreen.kt:432` — the setup/dusk guards both say some variant
  of "Running a Fabled or house rule the checker doesn't know? You can start anyway."

Storyteller's experience: import a script with a homebrew character, and the app quietly
does the right thing with it — but the Bootlegger is never added, the group is never
prompted to be told about the homebrew, and there is nowhere in the app to write down "the
house rule this game is using" other than the free-text storyteller notes
(`GameState.storytellerNotes`). At the table, the storyteller has to remember that they
promised to explain the Peasant before the bag goes round.

## Defects and gaps

1. **P1** · The Bootlegger is never auto-added, though the wiki says it *is* in play
   whenever the script has homebrew ("As long as there is at least one homebrew character
   on the current script, this Loric will be in play"). `GameActions.newGame`
   (`GameActions.kt:11-16`) starts with `fabledIds = emptyList()` and never inspects
   `script.customCharacters`. *Repro:* import any script with an inline custom character →
   start a game → the fabled corner of the grimoire reads "fabled +" (empty).
2. **P1** · Nowhere to record the homebrew rules the storyteller must announce. The
   How-to-Run obligation is "inform the group of all the homebrew characters and/or rules
   you are using in this game"; the app has one global free-text field
   (`GameState.storytellerNotes`, `GameShell.kt:236-238`) that is not surfaced at setup and
   is not tied to the Bootlegger.
3. **P1** · A homebrew character that acts at night can silently vanish from the night
   sheet. `NightOrder.kt:190-193` only slots a custom character in if
   `firstNightReminder`/`otherNightReminder` is non-blank **or** `firstNight`/`otherNight`
   > 0. A hand-written script entry with an ability like "Each night, choose a player" but
   no `otherNight` number and no `otherNightReminder` gets no step at all, with no warning.
   *Repro:* import `[{"id":"peasant","team":"townsfolk","name":"Peasant","ability":"Each night, choose a player: they are poisoned."}]`
   → assign it → the Night tab never lists the Peasant.
4. **P2** · No setup checklist item "Announce the Bootlegger and explain: <list>". The app
   has an excellent blocking-prompt pattern for exactly this (Fortune Teller red herring,
   `GameShell.kt:341-375`; Drunk/Lunatic/Marionette identity, `GameShell.kt:377-475`) and
   does not use it here.
5. **P2** · Nothing to show the table. `ShowCards.kt:65-77` has `SheetCard(characterIds)`,
   which would render the homebrew characters full-screen for the group to read, but no
   preset points at it from setup.
6. **P2** · Homebrew characters are visually indistinguishable from official ones in the
   Script tab (`ReferenceScreen.kt:52-87`) and the grimoire; only the night sheet appends
   "(homebrew)" (`NightOrder.kt:199`). A storyteller scanning the character sheet with the
   table cannot point at "these three are the custom ones".
7. **P2** · Custom character art is best-effort remote (`Character.image`,
   `Character.kt:54-59`), so on the PWA/offline phone the homebrew tokens degrade to
   monograms — fine, but the setup announcement is then the *only* way players learn what
   the character does.
8. **P3** · Type label: `characters.json:2149` says `edition: fabled`; the wiki says Loric
   / Experimental.

## Proposed behaviour (spec)

Shares the `FabledEntry` storage introduced in `angel.md` (the `note` field is the
Bootlegger's whole payload).

- when: never wakes. No night-order entry, ever.
- automatic presence: on `newGame` and on any script change, if
  `script.customCharacters.isNotEmpty()`, add `FabledEntry("bootlegger")` if absent, and
  **refuse to remove it** from `FabledSheet` while that condition holds — the toggle row
  renders disabled with the sub-line "In play because this script has homebrew characters."
  (Direct implementation of the wiki bullet "can only be removed by switching to a script
  that does not contain any homebrew characters.")
- manual presence: the storyteller may add the Bootlegger with no homebrew characters at
  all, for a pure house rule. That entry is removable.
- house-rules input: `FabledEntry("bootlegger").note` is a multi-line text field, editable
  from the Fabled sheet and from the setup screen. Pre-seed it, when custom characters
  exist, with one line per custom character: `"<Name> (<team>): <ability>"`.
- setup gate (non-blocking, dismissible, same shape as the setup-guard dialog at
  `GameShell.kt:556-600`): when the Bootlegger is active and the phase is SETUP, the
  "Begin night" action first shows

  > **Bootlegger — announce before the bag goes round**
  > Tell the group: the Bootlegger is in play, and these are the homebrew characters and
  > rules.
  > • Peasant (Townsfolk): Each night, choose a player: they are poisoned.
  > • House rule: <the note text>
  > [ Show the group ]  [ Announced — begin night ]  [ Later ]

  "Show the group" opens `ShowCard.SheetCard(customIds)` full-screen
  (`ShowCards.kt:77`), so the phone can be passed round.
- day/night effects: none. No tokens, no expiry, no info, no win/loss effect.
- visibility: everyone. The Bootlegger is public by definition.
- night-order safety net (fixes defect 3): when a custom character's ability text contains
  "each night" / "at night" / "tonight" but it produces no night step under
  `NightOrder.kt:190-193`, surface it in the setup guard as
  `"Peasant looks like a night character but has no night-order position — it will not appear on the night sheet."`
  Optionally let the storyteller assign a position inline (a numeric field writing
  `Character.otherNight`), stored on the script.
- interactions/jinxes: none. Note that the Bootlegger and the **Djinn** are the two
  "announce this at the start" Fabled and should share one setup announcement screen —
  see `djinn.md`, which specs the jinx half of the same dialog.

**UI text:**
- Fabled sheet row when auto-added: "Bootlegger · in play — this script has 3 homebrew
  characters. Tap to edit the house-rule text."
- Setup banner: "Homebrew in play — announce the Bootlegger before dealing."
- Script tab: badge homebrew characters with a "homebrew" chip, matching the night sheet's
  "(homebrew)" suffix.

**Data changes:** `characters.json:2149` — consider `"edition": "loric"` (or keep `fabled`
and add a display label), purely cosmetic. No night-order or jinx changes.

## Tests to add

1. `importing a script with custom characters activates the bootlegger`
   Given a script whose JSON contains one inline custom character,
   When `GameActions.newGame(script, names)` runs,
   Then `state.fabled.map { it.id }` contains `"bootlegger"`.
   *(Fails today: `newGame` never touches fabled.)*
2. `bootlegger cannot be removed while homebrew is on the script`
   Given the above state, When `setFabled` is asked to drop `"bootlegger"`,
   Then the entry survives (or the action returns an explanatory rejection).
3. `bootlegger is removable when there is no homebrew`
   Given a built-in script and a manually added Bootlegger,
   When it is toggled off, Then `state.fabled` no longer contains it.
4. `house-rule note round-trips`
   Given `FabledEntry("bootlegger", note = "Whispers only in pairs")`,
   Then it survives serialization and appears in the setup announcement payload.
5. `homebrew night character with no order position is reported`
   Given a custom character with `otherNight = 0`, blank `otherNightReminder` and an
   ability containing "Each night",
   When `GameActions.validateSetupState` runs,
   Then the issues list contains a message naming that character and the missing night
   position. *(Fails today: `validateSetupState`, `GameActions.kt:503-561`, checks only
   the bag, Drunk/Lunatic/Marionette and the red herring.)*
6. `bootlegger adds no night step`
   Given `fabled = [bootlegger]` on any script, Then neither `firstNight` nor `otherNight`
   contains a step with id `"bootlegger"`. *(Passes today — a regression guard.)*
