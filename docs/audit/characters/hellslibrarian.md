# Hell's Librarian (hellslibrarian) — Fabled

## Official rules (sources)

Sources (fetched 2026-08-25):
<https://wiki.bloodontheclocktower.com/Hell%27s_Librarian> and the raw wikitext via
`https://wiki.bloodontheclocktower.com/api.php?action=parse&page=Hell%27s_Librarian&prop=wikitext`,
plus <https://wiki.bloodontheclocktower.com/Fabled>.

Current ability text (matches `characters.json`):

> "Something bad might happen to whoever talks when the Storyteller has asked for silence."

How to Run (quoted from the wikitext):

> "At any time, declare the Hell's Librarian is in play and add the token with the
> **SOMETHING BAD** reminder to the Grimoire. When requesting silence, remind players if
> needed. If a player talks during that period, inform them something bad happens and mark
> their character with the **SOMETHING BAD** reminder."

Suggested penalties (quoted):

> "The 'something bad' is at the Storyteller's discretion. Recommendations include: the
> player dies, loses their ability for a day, or cannot vote for a day. 'A light penalty
> works much better than a severe one.'"

Example (wiki): during a rules explanation with new players the group stayed loud; the ST
invoked the Hell's Librarian; two players kept talking — one died, the other lost their
vote for the day; the group then fell silent.

Tips & tricks (quoted):

> "Like the Angel, the threat of a mysterious penalty is more important than the actual
> penalty. The purpose of this character is to make games run smoothly, not to punish
> minor infringements."

Rules that matter for storytelling:

- **This Fabled can be added (and removed) at any time**, unlike most — the wiki's Fabled
  page says "some Fabled are added at the start of the game, while others can be added and
  removed at any time", and the How to Run explicitly says "at any time".
- The **SOMETHING BAD** token starts in the grimoire next to the Fabled token and is moved
  onto an offending player's character.
- The three canonical penalties are: **death**, **loses their ability for a day**,
  **cannot vote for a day**. The middle and last both need in-game bookkeeping the app
  already has primitives for (a "No ability" reminder; a vote lock).
- Fabled general rules (<https://wiki.bloodontheclocktower.com/Fabled>): cannot be killed,
  immune to all game effects, do not count for the two-alive evil win.
- **No night action**, **no setup flag**, **no jinxes** listed.

## What the app does today

Data:

- `engine/src/main/resources/botc/data/characters.json:2260` — ability matches the wiki;
  `reminders: ["Something Bad"]`.
- Correctly absent from both night order lists; no `night_guide.json` entry.

Engine: nothing references `hellslibrarian` outside the data files.

UI:

- `FabledSheet` (`GameExtras.kt:145-198`) toggles it on. Nothing else.
- The `("hellslibrarian","Something Bad")` token is **unreachable**. `ReminderPicker`
  (`SeatSheet.kt:492-500`) sources its list from `viewModel.gameData.resolve(state.script)`
  and `GameData.resolve` (`GameData.kt:49-52`) returns only `script.characterIds`; the
  built-in scripts are `filter { it.team.isTownResident }` (`GameData.kt:35-42`) so Fabled
  are never in a script. The `NightToolTray` route that makes the Storm Catcher's and
  Toymaker's tokens reachable (`NightScreen.kt:98`, `:283`) only exists for Fabled with a
  night-order entry, which this one does not have. The nearest usable token is the generic
  `"?"` chip (`SeatSheet.kt:502`, `sourceId = ""`).
- **Penalty support that exists but is unconnected:**
  - Death: `SeatSheet` kill-by-cause and `DeathCause.STORYTELLER` (`GameState.kt:74`)
    already exist — this penalty is fully expressible today.
  - "Loses their ability for a day": the app's convention is a `"No ability"` reminder
    (used by `NightScreen.kt:264-279`'s "Mark spent", the Fool at `StatusEffects.kt:73`,
    the Virgin at `StatusEffects.kt:153`). But `"No ability"` is **not** in the generic
    reminder list (`SeatSheet.kt:502` offers Drunk, Poisoned, Dead, Protected, Mad, Good,
    Evil, Used, ?), and nothing expires it at dusk.
  - "Cannot vote for a day": **no support at all.** `Player.ghostVoteUsed`
    (`GameState.kt:27`) models a dead player's single ghost vote, and
    `DayScreen`'s vote tally (`DayScreen.kt:158-250`) is a free-form count with no
    per-player eligibility model. There is no way to mark a living player as vote-barred,
    and nothing in `nominationWarnings` (`StatusEffects.kt:134-166`) would flag it.
- There is no "ask for silence" affordance anywhere — no timer, no full-screen "SILENCE"
  card (`components/ShowCards.kt` has the machinery), no nudge.

Storyteller's actual experience today: turn the Fabled on and then run the entire mechanic
verbally and from memory. If you impose "no vote today", you must remember it yourself
through every nomination for the rest of the day, because the app will happily count that
player's hand.

## Defects and gaps

1. **P1** · The SOMETHING BAD token cannot be placed · `ReminderPicker`
   (`SeatSheet.kt:497`) is script-scoped and Fabled are never in a script
   (`GameData.kt:35-42`, `:49-52`), and this Fabled has no night step to reach the
   `NightToolTray` fallback. Repro: activate Hell's Librarian, open a seat →
   Add reminder — there is no "Hell's Librarian" group and no "Something Bad" token.
2. **P1** · "Cannot vote for a day" is unrepresentable · There is no per-player vote
   eligibility flag; `DayScreen.kt:158-250` counts votes freely and
   `StatusEffects.nominationWarnings` (`StatusEffects.kt:134`) has no hook. The ST must
   police it by hand for a whole day — precisely the manual bookkeeping this audit is
   about.
3. **P1** · "Loses their ability for a day" has no expiry · The app's `"No ability"`
   convention exists but is neither offered in the generic reminder list
   (`SeatSheet.kt:502`) nor listed in `EXPIRES_AT_DUSK` (`GameActions.kt:230-241`), so a
   day-scoped ability loss silently becomes permanent.
4. **P2** · No "ask for silence" tool · The app has full-screen show cards
   (`components/ShowCards.kt`, `FullScreenShow` at `NightScreen.kt:184`) but nothing to
   flash a SILENCE card or arm the Fabled at the moment the ST needs quiet — which,
   per the wiki, is the entire mechanism ("the threat… is more important than the actual
   penalty").
5. **P2** · No penalty picker · The three canonical penalties (die / lose ability today /
   no vote today) should be a one-tap choice that applies the right state, places the
   token, and logs it. Today each is a different manual path (or impossible).
6. **P2** · Nothing is logged · `GameLogDialog` (`GameExtras.kt:46-80`) shows only deaths
   and nominations. A penalty imposed for talking should appear in the log so the ST can
   answer "why can't I vote?" later.
7. **P3** · Reminder label casing · `characters.json:2260` uses `"Something Bad"`; the
   official token is **SOMETHING BAD**. Cosmetic.

## Proposed behaviour (spec)

Night action: **none**. Do not add to either night order.

Availability: this Fabled must be addable and removable **at any time**, from setup and
from any phase — the current `FabledSheet` toggle is already the right shape; it just needs
to also be reachable before `startGame`.

Grimoire-level token:

- The SOMETHING BAD token nominally lives on the Fabled token until it is used. This needs
  the same `grimoireReminders` container proposed in `spiritofivory.md`. Until that exists,
  the minimum fix is to make **active Fabled contribute their reminder tokens to
  `ReminderPicker`**: in `SeatSheet.kt:492-500`, source from
  `gameData.resolve(script) + state.fabledIds.mapNotNull { gameData.character(it) }`.
  That one change also fixes the Spirit of Ivory, Revolutionary and Deus ex Fiasco tokens.

The "silence" tool (the storyteller-facing feature that makes this Fabled worth having):

- A persistent action available in `GameShell`'s overflow (and ideally the top bar while
  the Fabled is active): **"Ask for silence"**.
  - Flashes a full-screen `ShowCard.Message("SILENCE")` styled like the existing show cards
    so it can be held up to the table.
  - Arms a "silence is requested" state for as long as the card is up (or until dismissed),
    during which the grimoire shows a small row of seat chips: tapping a seat opens the
    penalty picker.
- **Penalty picker** — one tap each, all undoable, all logged:
  1. **`<name> dies`** → `GameActions.kill(state, id, DeathCause.STORYTELLER, lookup)`,
     plus the SOMETHING BAD token.
  2. **`<name> loses their ability today`** → `PlacedReminder("hellslibrarian","No ability")`
     (so `isImpaired`/ability checks see it) **plus** the SOMETHING BAD token, expiring at
     the next dusk.
  3. **`<name> can't vote today`** → sets a new day-scoped vote bar (below), plus the token.
  4. **`Just the token (decide later)`** → SOMETHING BAD only.
- Every penalty writes a log line: `Day 2: <name> talked during silence — lost their vote
  (Hell's Librarian)."`

Vote eligibility model (needed by penalty 3, and reusable by Butler, Beggar, Voudon,
Politician-style effects):

- Add `fun canVote(state, lookup, playerId): VoteEligibility` returning
  `Allowed | Barred(reason) | GhostVoteSpent`.
- `DayScreen`'s voter selection (`DayScreen.kt:158-250`) marks barred players' chips as
  disabled with the reason as a caption, and `StatusEffects.nominationWarnings`
  (`StatusEffects.kt:134`) surfaces "<name> cannot vote today (Hell's Librarian)" when a
  nomination opens.
- The bar is a reminder token, so undo/redo and the grimoire both see it.

Expiry:

- Add to `EXPIRES_AT_DUSK` (`GameActions.kt:230-241`):
  `"hellslibrarian" to "No ability"` and `"hellslibrarian" to "No vote"`.
  Both penalties are explicitly "for a day".
- The **SOMETHING BAD** token itself does not expire — it is a record; the ST removes it by
  hand. (The wiki treats it as "for later reference".)

Information / visibility: nothing is shown to players beyond the public announcement that
the Fabled is in play and the ST's spoken warning.

Day-time inputs the app must record: the penalty itself (above). The day briefing should
carry any live penalty: *"<name> has no ability today"*, *"<name> cannot vote today"*.

Interactions to handle explicitly:

- The imposed drunkenness/ability loss must be picked up by `StatusEffects.isImpaired`
  (`StatusEffects.kt:35-45`) — note that `isImpaired` currently matches on the *label*
  containing "poison"/"drunk", so a `"No ability"` label will **not** register as impaired.
  Either use the label `"Drunk"` for penalty 2 or extend `isImpaired` to treat
  `"No ability"` as ability-less (the latter is the correct general fix — the Fool, Virgin
  and "Mark spent" all use `"No ability"` today and none of them feed `isImpaired`).
- A dead player's ghost vote and a Hell's Librarian vote bar must compose: barred wins.
- Fabled immunity: the Hell's Librarian token itself is never a target.
- No jinxes.

UI text:

- Overflow action: `Ask for silence (Hell's Librarian)`
- Full-screen card: `SILENCE`
- Penalty sheet title: `<name> talked. Something bad happens:`
- Options: `They die` · `They lose their ability today` · `They can't vote today` ·
  `Mark them and decide later`
- Footer, quoting the wiki's advice: `A light penalty works better than a severe one.`

Data changes:

- `characters.json:2260` — normalise the reminder label to `"Something bad"` (or keep, but
  pick one casing convention across the dataset).

## Tests to add

1. **Given** `fabledIds = ["hellslibrarian"]`, **when** the reminder-picker source list is
   built, **then** it contains a `("hellslibrarian","Something Bad")` entry (fails today).
2. **Given** a player marked `PlacedReminder("hellslibrarian","No vote")` during DAY 2,
   **when** `canVote` is queried, **then** it returns `Barred` with a reason mentioning the
   Hell's Librarian.
3. **Given** the same player, **when** `advancePhase` runs DAY → NIGHT,
   **then** the `"No vote"` token is gone (it is in `EXPIRES_AT_DUSK`).
4. **Given** a player marked `PlacedReminder("hellslibrarian","No ability")`,
   **when** `StatusEffects.isImpaired` is queried, **then** it returns `true`
   (fails today — `isImpaired` only matches "poison"/"drunk").
5. **Given** the same player, **when** DAY → NIGHT advances, **then** the token is removed.
6. **Given** a nomination where the nominee is vote-barred,
   **when** `nominationWarnings` runs, **then** a warning naming the bar is returned.
7. **Given** a penalty applied and then `undo()`,
   **then** the token, the vote bar and the log entry are all reverted together.
8. **Given** `fabledIds = ["hellslibrarian"]`, **when** either night sheet is built,
   **then** no step with `id == "hellslibrarian"` appears.
