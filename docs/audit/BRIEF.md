# Clocktower Grimoire — Phase 1 audit brief (read this fully before starting)

You are one of ~37 auditors reviewing a storyteller companion app for
**Blood on the Clocktower**. The user (a real storyteller) ran a Bad Moon Rising
game on the app last night and reported, verbatim:

> some problems out of many (pick up on the idea and generalize). we did BMR.
> Pukka didn't work automatically, it offered to kill even though it's supposed
> to poison then kill the turn after. DA wasn't automatically removed and
> allowed two days in a row I think for same person. Gossip was awful, make it
> easy to write down all the gossips even if Gossip isn't in play. When
> Professor brings someone back it should remind in the morning and rerun the
> 1st night for that. Lunatic needs work: should have its own bluffs, should
> fit the correct demon it thinks it is, should show the real demon who they
> chose, etc. etc.

The common thread: **the storyteller should only ever INPUT choices; the app
must do every consequence, reminder, expiry and bookkeeping itself, exactly
as the official rules say, and surface what the storyteller needs at the
moment they need it (at night, at dawn, at day start, at nomination, at
execution).** Your job is to find every place where the app fails that
standard for your assigned scope, with the official rules as ground truth,
and to write an implementation-ready spec.

This phase is **research + specification only. Do NOT modify any source or
data file. Do NOT run gradle/builds. Do NOT commit.** Write only into
`docs/audit/…` as described under "Output".

## The app in one screen

Kotlin. `engine/` is a pure, immutable, unit-tested rules core; `app/` is a
Jetpack Compose UI; `web/` compiles the SAME engine+app sources to a PWA
(the user plays on an iPhone via the PWA, so phone ergonomics matter).

Engine (`engine/src/main/kotlin/com/clocktower/engine/`):
- `GameState.kt` — `GameState`, `Player` (characterId, shownCharacterId used
  for Drunk/Lunatic/Marionette, alive, reminders: `List<PlacedReminder(sourceId,label)>`,
  note), `Nomination`, `DeathRecord`, `Phase` SETUP/NIGHT/DAY, `cycle`.
- `GameActions.kt` — all state transitions: kill/revive/resurrect, reminders
  (`addReminder`, `placeExclusiveReminder` = one-of-a-kind token that moves),
  `advancePhase` with `EXPIRES_AT_DAWN` / `EXPIRES_AT_DUSK` tables of
  (sourceId,label) tokens auto-removed, starPass, swapCharacters,
  snakeCharmerSwap, nominations, deal, bag validation (`validateBag`,
  `validateSetupState`).
- `StatusEffects.kt` — `isImpaired` (drunk/poisoned by reminder text containing
  "poison"/"drunk", or being the Drunk, or No Dashii adjacency), `deathNotes`
  (protections + on-death triggers surfaced as text warnings), `nominationWarnings`.
- `NightOrder.kt` — builds the night sheet from the global order lists in
  `night_and_jinxes.json`; special markers DUSK / MINION_INFO / DEMON_INFO / DAWN;
  Lunatic/Exorcist annotations are appended as text to the Demon's step.
- `NightGuide.kt` + `resources/botc/data/night_guide.json` — per-character
  "how to run" prose + prepared show cards (116 entries).
- `InfoCalc.kt` — computes TRUE info for ~30 info characters, with caveats
  (impaired, Spy/Recluse misregistration, Vortox).
- `WinCheck.kt`, `Setup.kt` (distribution math + setup modifiers), `Notes.kt`
  (player-mode notes), `Script.kt`/`ScriptLink.kt` (import), `Character.kt`,
  `GameData.kt` (loads `characters.json` (171 chars), `night_and_jinxes.json`).
- Tests in `engine/src/test/kotlin/...` (run with
  `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :engine:test`
  — but NOT in this phase).

UI (`app/src/main/java/com/clocktower/grimoire/ui/`):
- `screens/GameShell.kt` — tabs Grimoire / Night / Day / Script, phase button
  ("Dawn"/"Dusk"), end-game, log, notes, bluffs, fabled, reveal.
- `screens/NightScreen.kt` — the night sheet. Each step row expands into
  `StepDetailPanel`: night_guide prose + show-card chips, `QuickResolutions`
  (hard-coded per-character resolvers: snakecharmer, fanggu, professor; every
  other Demon gets the generic `DemonKillPanel` "who dies?" — THIS is why the
  Pukka wrongly offers a kill), then `InfoCalc` true-info with target chips.
  A bottom `NightToolTray` lets you tap a reminder token then a seat.
- `screens/DayScreen.kt` — nominations, votes, execution, exile.
- `screens/GrimoireScreen.kt` + `SeatSheet.kt` — the circle, per-seat sheet
  (kill by cause, revive, change character, shown identity, reminders, notes),
  `CharacterPicker`, `ReminderPicker`.
- `screens/SetupScreen.kt`, `BluffsSheet.kt`, `GameExtras.kt` (log, fabled,
  jinxes, win advisory, reveal), `RevealFlow.kt`, `components/ShowCards.kt`
  (full-screen cards shown to players), `HomeScreen.kt`, `LibraryScreen.kt`,
  `ReferenceScreen.kt`, `NotesScreen.kt` (player mode, out of scope unless assigned).
- `ui/GameViewModel.kt` — `update { state -> ... }` with undo/redo + persistence.

Data (`engine/src/main/resources/botc/data/`): `characters.json` (id, name,
edition, team, ability, setup flag, first/otherNightReminder, reminders,
remindersGlobal), `night_and_jinxes.json` (jinxes + firstNight/otherNight id
lists), `night_guide.json` (per id: first/other → instructions + shows).

## Ground truth: research before you judge

For EVERY character in your scope, fetch the official wiki page
`https://wiki.bloodontheclocktower.com/<Name_With_Underscores>` (e.g.
`Devil's_Advocate`, `Fortune_Teller`, `No_Dashii`, `Lil'_Monsta`, `Pit-Hag`,
`Al-Hadikhia`, `Lord_of_Typhon`, `Deus_ex_Fiasco`) with WebFetch, and read at
minimum: **Character Text, Examples, How to Run, Tips & Tricks (storyteller
sections), and any Jinx text**. If a fetch fails, WebSearch for the page or use
the botc-scripts / script-tool data. Also consult the wiki's night order pages
(`https://wiki.bloodontheclocktower.com/Night_Order`) when order matters.
Character ability text has been revised over the years — the wiki's current
text is authoritative; note any drift from `characters.json`.

Then trace the app's actual behaviour for that character:
`grep -rn "<id>" engine/src app/src` and read every hit, the
`night_guide.json` entry, the `characters.json` entry, the position in
`night_and_jinxes.json`, and whatever generic path applies (e.g. all Demons
fall into `DemonKillPanel`; all info roles into `InfoCalc.supports`;
reminder placement via the tray; expiry tables in `GameActions`).
Walk the character through a full game **as the storyteller would use the UI**:
setup → night 1 (what wakes, what is shown, what tokens get placed) → day 1
(what the ST must remember/announce/track) → night 2 (deferred effects,
expiries, "different from last night", once-per-game) → death/resurrection/
character change edge cases → interactions with Drunk/poison, Spy/Recluse,
Vortox, protection, Exorcist, Lunatic, travellers, and each listed jinx.

Ask, for each character: *what does the storyteller currently have to remember,
write down, count, look up or do by hand that the app could do for them?*
That includes: reminders at dawn ("announce X died / X is alive again"),
day-start briefings ("Y survives execution today", "Z is mad about being the
Empath"), nomination-time triggers, execution-time triggers, deferred kills,
token expiry, re-running first-night info for a resurrected/changed player,
choices that must differ from last night, once-per-game spent marks, what the
Demon/Minions must be shown, what the Lunatic must be shown, false info to
give when impaired, and public statements/claims that must be recorded during
the day (Gossip, Juggler, Savant, Artist, Fisherman, Slayer, Mutant, ...).

## Output — one file per character (or topic), implementation-ready

Character auditors: write `docs/audit/characters/<id>.md` for each assigned id.
Mechanics/UX auditors: write `docs/audit/<mechanics|ux>/<topic>.md`.
Do not write anywhere else. Use exactly this structure:

```
# <Name> (<id>) — <edition> <team>

## Official rules (sources)
Current ability text (quote). Bullet the How-to-Run steps, timing, and every
edge case/clarification from the wiki that affects storytelling. Jinxes.
Cite the URL(s).

## What the app does today
File:line references for every code/data path that touches this character,
and a plain description of the storyteller's experience with it (night step
text, tools offered, tokens, expiry, info computed, show cards, day handling).

## Defects and gaps
Numbered list. Each: **P0/P1/P2/P3** · one-line title · what happens vs what
the rules require · file:line · how to reproduce in the UI.
P0 = wrong outcome / storyteller misled or rules broken; P1 = ST must do manual
bookkeeping the app could do; P2 = missing convenience / clarity; P3 = polish.

## Proposed behaviour (spec)
A precise spec an implementer can code without re-researching. Where the
character acts at night, express it in this structured form so a generic
engine can be built from many specs:
- when: first / other / both; wake condition (alive? dead-but-acts? once-per-game not spent? not Exorcised? …)
- targets: count, constraints (alive, not self, different from last night, etc.), what the picker should default/sort to
- immediate effects: tokens placed (sourceId:label, exclusive?), status effects (poison/drunk/protect/mad… with duration), kills (with protection checks), character/alignment changes
- deferred effects: what happens at dawn / day start / next night / on death / on execution / on nomination, and what the ST must be told and when
- expiry: which tokens expire at dawn / dusk / never / on condition
- information: what is computed, what is shown (show cards), what the impaired/false alternative is, misregistration handling
- visibility: what the Demon / Minions / Lunatic / others must be shown about this
- day-time inputs the app must let the ST record (statements, claims, guesses) and how the night step consumes them
- interactions/jinxes to handle explicitly
Also list UI text the step should display (short, imperative, storyteller voice)
and any changes to `characters.json` / `night_guide.json` / night order data.

## Tests to add
Concrete engine test cases (Given/When/Then) that would fail today.
```

Be thorough and concrete; vague findings are worthless. Prefer more, smaller,
verifiable items. Do not pad with things that already work — say "works" in
one line under "What the app does today" and move on. When you are unsure of
a rule, say so and cite what you found rather than guessing.

Your final message (the return value) must be a compact summary: per
character/topic, the P0/P1 count and the three most important findings, plus
any cross-cutting architectural recommendation you noticed. The lead collects
these into the implementation plan.
