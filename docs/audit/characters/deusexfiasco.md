# Deus ex Fiasco (deusexfiasco) — Fabled

## Official rules (sources)

Sources (fetched 2026-08-25):
<https://wiki.bloodontheclocktower.com/Deus_ex_Fiasco> and the raw wikitext via
`https://wiki.bloodontheclocktower.com/api.php?action=parse&page=Deus_ex_Fiasco&prop=wikitext`,
plus <https://wiki.bloodontheclocktower.com/Fabled>.

**Ability text drift.** The wiki's current text is:

> "At least once per game, the Storyteller will make a mistake, correct it, and publicly
> admit to it."

`characters.json:2342` carries:

> "Once per game, the Storyteller will make a mistake, correct it, & publicly admit to it."

"At least once" vs "Once" is material: multiple mistakes are explicitly permitted, and the
ST is *obliged* to make at least one before the game ends.

How to Run (quoted / closely paraphrased from the wikitext):

> "Announce the Deus ex Fiasco at game start. When you make a mistake (accidental or
> deliberate), correct it by bending rules if needed, then declare the error publicly and
> add the **WHOOPSIE** reminder to the Grimoire."

Key rules (from the fetched page):

- **Must be announced at start; cannot be added mid-game.**
- **All mistakes must be corrected.**
- **The exact nature of the mistake need not be revealed** to all players — only that one
  happened.
- Players may bluff about mistakes.
- Multiple mistakes are permitted (accidental, deliberate, or mixed).
- If no mistake has happened naturally, the ST must **deliberately create one before the
  game ends**.

Examples (all seven, condensed from the wikitext):

1. Doug claims the Drunk token; the ST confirms it, then reassigns Doug the Mayor and makes
   the Ravenkeeper the Drunk instead, then announces a mistake was made.
2. The Empath is given "1" instead of "0" because the ST forgot the poisoned Recluse; the
   correct number is given the next night.
3. The Undertaker is deliberately shown the Recluse instead of the Imp, then shown the
   correct token the next night.
4. The ST accidentally declares good has won after a Yaggababble execution, forgetting the
   Scarlet Woman survives; the call is corrected and the game continues.
5. The ST deliberately announces the wrong deaths (Ben and Lewis), then corrects within
   seconds to the real deaths (Amy and Doug).
6. The Poisoner was never woken; the ST explains privately and lets them choose multiple
   players going forward.
7. The Chambermaid illegally chose a dead player; the ST deliberately gave false info, then
   privately corrected the error.

What the examples tell us about the shapes a "mistake" takes: **wrong info given** (2, 3,
7), **a step skipped** (6), **a wrong public announcement** (4, 5), **a wrong character
assignment** (1). All of them need *correction*, and most need a **re-run of something that
already happened**.

Fabled general rules (<https://wiki.bloodontheclocktower.com/Fabled>): cannot be killed,
immune to all game effects, do not count for the two-alive evil win.
**No night action**, **no setup flag**, **no jinxes** listed.
Reminder token: **WHOOPSIE**.

## What the app does today

Data:

- `engine/src/main/resources/botc/data/characters.json:2342` — ability = the **old**
  "Once per game" wording; `reminders: ["Mistake"]` (the official token is **WHOOPSIE**).
- Correctly absent from both night order lists; no `night_guide.json` entry.

Engine: nothing references `deusexfiasco` outside the data files.

UI:

- `FabledSheet` (`GameExtras.kt:145-198`) toggles it on. Nothing else happens, ever.
- The `("deusexfiasco","Mistake")` token is **unreachable from the reminder picker**, for
  the same reason as the Spirit of Ivory's and Hell's Librarian's:
  `ReminderPicker` (`SeatSheet.kt:492-500`) sources from `gameData.resolve(state.script)`,
  `GameData.resolve` (`GameData.kt:49-52`) returns only the script's ids, and built-in
  scripts exclude Fabled (`GameData.kt:35-42`). This Fabled has no night step, so the
  `NightToolTray` fallback (`NightScreen.kt:98`, `:283`) does not apply either.
- **Undo/redo is the one genuinely relevant existing feature.**
  `GameViewModel.update/undo/redo` (`GameViewModel.kt:98-120`) keeps an
  `ArrayDeque<GameState>` history capped at `MAX_HISTORY`, and the top bar exposes Undo and
  Redo buttons (`GameShell.kt:189-194`). It is a *whole-state* undo: correct, atomic and
  already the right primitive for "correct the mistake".
- **The log is nearly useless for this purpose.** `GameLogDialog`
  (`GameExtras.kt:46-80`) renders only `state.deaths` and `state.nominations`. There is no
  record of information given, night steps completed, tokens placed, or storyteller
  decisions — so "what did I tell the Empath on night 2?" is unanswerable, which is exactly
  the question every Deus ex Fiasco correction turns on.
- **Nothing helps re-run a step.** `nightStepsDone` (`GameState.kt:101`) is a `Set<String>`
  cleared each phase (`GameActions.kt:257-262`); a mis-run step can be unticked, but the
  info that was *given* is not stored, so the app cannot say "you told them 1; the truth is
  0".
- There is no end-of-game check that a mistake was made, and no "announce a mistake" card.

Storyteller's actual experience today: toggle it on so you have cover, then do everything
by memory. When you realise you gave the Empath the wrong number, you get no help finding
what you said, no way to record that a correction happened, and no prompt at the end of the
game reminding you that you still owe the table a mistake.

## Defects and gaps

1. **P1** · No record of information given · This is the load-bearing gap. `InfoCalc`
   computes an answer and `NightScreen.kt:857-880` renders it, but nothing is persisted.
   Every Deus ex Fiasco correction ("the Empath got 1, it should have been 0") requires
   knowing what was actually shown. Repro: give a Fortune Teller a YES on night 2, then on
   night 3 try to find out what you told them — the log has only deaths and nominations.
2. **P1** · No storyteller event log · `GameLogDialog` (`GameExtras.kt:46-80`) covers
   deaths and nominations only. Token placements, alignment flips, character changes,
   resurrections, night-step completions and info shown are all invisible after the fact.
3. **P1** · The WHOOPSIE token cannot be placed · `ReminderPicker` is script-scoped
   (`SeatSheet.kt:497` → `GameData.kt:49`); no Fabled reminder is reachable, and the token
   belongs on the *grimoire*, not on a seat, for which there is no container
   (`PlacedReminder` only attaches to a `Player`, `GameState.kt:29`).
4. **P1** · Wrong ability text · `characters.json:2342` says "Once per game"; the wiki says
   "**At least** once per game". The app's reference text tells the ST the wrong obligation.
5. **P2** · No obligation tracking or end-of-game reminder · The ST *must* make at least one
   mistake. Nothing counts them and nothing warns at the end-game/reveal flow
   (`GameShell.kt:539` `RevealSheet`) that the obligation is unmet.
6. **P2** · No "announce a mistake" affordance · The public admission is the point of the
   Fabled. A one-tap full-screen card ("A MISTAKE HAS BEEN MADE") plus a log entry is
   trivial with the existing `ShowCard` machinery (`components/ShowCards.kt`,
   `FullScreenShow` at `NightScreen.kt:184`).
7. **P2** · Undo is unlabelled and history-capped · `GameViewModel.kt:98-107` caps history
   at `MAX_HISTORY` and stores no description, so the Undo button is a blind "step back".
   For "correct it", the ST wants to see *what* they are undoing.
8. **P2** · Undo can silently destroy a *correct* later state · Because undo is
   whole-state, undoing three steps to fix a night-1 info error also reverts the two
   correct actions after it, with no way to re-apply them. A correction usually wants a
   *targeted* edit, not a rewind.
9. **P3** · Reminder label drift · `characters.json:2342` uses `"Mistake"`; the official
   token is **WHOOPSIE**.

## Proposed behaviour (spec)

Night action: **none**. Do not add to either night order.

Availability: selectable **only at setup** (the wiki: "Must be announced at start; cannot
be added mid-game"). Once the game has begun, `FabledSheet` should show it disabled with
that reason, rather than silently letting it be toggled on.

Configuration:

- `fabledConfig["deusexfiasco"] = DeusExFiasco(mistakes: List<Mistake>)` where
  `Mistake(cycle: Int, phase: Phase, note: String, announcedPublicly: Boolean)`.
  At least one entry is required before the game ends.

The three features that actually make this Fabled work in the app:

**1. A storyteller event log (the prerequisite).**

- Add `val log: List<LogEntry>` to `GameState`, appended by `GameActions` (not by the UI),
  with `LogEntry(cycle, phase, kind, text, playerIds)`.
- Record at minimum: characters dealt/changed, reminders placed and removed, kills and
  resurrections with cause, alignment flips, nominations and votes (already modelled),
  night steps ticked done, **and every piece of information the ST showed a player**.
- Info-shown entries are the critical addition: when the ST taps a show card
  (`NightScreen.kt:181-186`, `GuideShowDialog` at `:366`) or ticks an `InfoCalc` step done,
  record `"Night 2 · Empath (Amy) shown: 1"` together with the computed-true value, so a
  later divergence is visible at a glance.
- Extend `GameLogDialog` (`GameExtras.kt:46-80`) to render this, filterable by
  night/day and by player. This log is what every other item here depends on.

**2. A "mistake" workflow.**

- A **"Whoopsie — a mistake was made"** action, available from the overflow menu at any
  time while the Fabled is active:
  1. Optionally jump into the log to find the entry that was wrong (tap it).
  2. Free-text note ("Empath got 1, should have been 0").
  3. **Correct it** — offer both routes explicitly:
     - *Undo to this point* (existing `GameViewModel.undo()`, `GameViewModel.kt:109-115`),
       with the labelled history from item 3 below; and
     - *Fix it in place* — jump to the seat/step involved so the ST can re-place a token,
       re-assign a character, or re-run a step, leaving later state intact. This is the
       right default for examples 1, 2, 3, 6 and 7 on the wiki.
  4. **Announce** — a one-tap full-screen `ShowCard.Message("A MISTAKE HAS BEEN MADE")`.
  5. Append to `fabledConfig["deusexfiasco"].mistakes` and place the WHOOPSIE token on the
     grimoire (`grimoireReminders`, per the container proposed in `spiritofivory.md`).
- **Re-run support**: a "re-run this step tonight/next night" flag on a night step, so
  example 6 (the Poisoner was never woken) and example 2 (wrong Empath number) surface a
  reminder in the next night's sheet: *"Deus ex Fiasco: re-run Empath info for Amy —
  the correct number is 0."* This is the same machinery the Professor's resurrection needs
  ("re-run first-night info for that player"), so build it once.

**3. Labelled, descriptive undo.**

- Change `GameViewModel.update` (`GameViewModel.kt:98-107`) to
  `update(label: String, transform: …)` and keep `(label, state)` pairs on the undo stack.
  The top-bar Undo button (`GameShell.kt:189`) gets a tooltip / long-press showing
  "Undo: Imp kills Ben", and the mistake workflow can present the last N labelled steps as
  a pick-list.
- Raise or remove `MAX_HISTORY` for the game stack, or at least surface when history has
  been truncated, so a night-1 correction on day 3 is still possible.

**End-of-game obligation:**

- In the end-game/reveal path (`GameShell.kt:539` `RevealSheet`, and the win advisory
  dialog), if `fabledIds` contains `deusexfiasco` and `mistakes` is empty, block with:
  *"Deus ex Fiasco: you still owe the table a mistake. Make one now, or record the one you
  made."* with "Record a mistake" and "Skip" buttons.

Immediate effects / deferred effects: none mechanical. Expiry: the WHOOPSIE token never
expires; it is a permanent record for the game.

Information / visibility: only the public "a mistake has been made" announcement. The
nature of the mistake is deliberately **not** revealed — the app must not show mistake
notes on any player-facing card.

Day-time inputs the app must record: the free-text mistake note and whether it was
announced.

Interactions to handle explicitly:

- **Every other Fabled/character with a once-per-game marker** — a correction that undoes a
  spent once-per-game must also restore the marker; whole-state undo does this correctly,
  in-place fixes do not, so the in-place path must warn.
- **Web/PWA parity** — `web/src/wasmJsMain/kotlin/com/clocktower/grimoire/ui/WebGameViewModel.kt:195`
  mirrors `setFabled`; any `update(label, …)` signature change and any new state field must
  land in both view models.
- **Persistence** — the log will grow; cap it per game and make sure it round-trips through
  the DataStore serialisation (`GameState` is `@Serializable`, `GameState.kt:88`).
- No jinxes.

UI text:

- Overflow action: `Whoopsie — record a mistake (Deus ex Fiasco)`
- Sheet: `What went wrong? You must correct it and admit it publicly — you don't have to
  say what it was.`
- Full-screen card: `A MISTAKE HAS BEEN MADE`
- End-game gate: `Deus ex Fiasco is in play and you haven't made a mistake yet.`

Data changes:

- `characters.json:2342` — ability becomes
  `"At least once per game, the Storyteller will make a mistake, correct it, & publicly
  admit to it."`; reminder becomes `"Whoopsie"`.

## Tests to add

1. **Given** `characters.json`, **when** the `deusexfiasco` entry is read,
   **then** the ability starts with "At least once per game" (fails today).
2. **Given** a game with `fabledIds = ["deusexfiasco"]` and an Empath shown a number,
   **when** the state's log is inspected, **then** it contains an entry recording the
   player, the value shown and the computed-true value (fails today — no log exists).
3. **Given** a recorded mistake, **when** the end-game advisory is computed,
   **then** no obligation warning is raised; **given** none, **then** the warning is
   raised.
4. **Given** `fabledIds = ["deusexfiasco"]`, **when** the reminder-picker source list is
   built, **then** a `("deusexfiasco","Whoopsie")` token is available (fails today).
5. **Given** a sequence of labelled updates, **when** `undo()` is called,
   **then** the returned history entry carries the label of the action that was reverted.
6. **Given** the game is past SETUP, **when** the Fabled sheet is rendered,
   **then** `deusexfiasco` is disabled with the "cannot be added mid-game" reason.
7. **Given** a "re-run Empath info for Amy" flag set on night 2,
   **when** the night-3 sheet is built, **then** the Empath step carries the re-run
   annotation.
8. **Given** `fabledIds = ["deusexfiasco"]`, **when** either night sheet is built,
   **then** no step with `id == "deusexfiasco"` appears.
