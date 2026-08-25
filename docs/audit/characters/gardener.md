# Gardener (gardener) — Fabled in the app; the wiki now files it under "Loric" (experimental)

## Official rules (sources)

Sources (fetched 2026-08-25):
<https://wiki.bloodontheclocktower.com/Gardener> and the raw wikitext via
`https://wiki.bloodontheclocktower.com/api.php?action=parse&page=Gardener&prop=wikitext`,
plus <https://wiki.bloodontheclocktower.com/Fabled>.

**Ability text drift — read this first.** The wiki's current text is:

> "The Storyteller assigns all players' characters."

`characters.json:2248` still carries the older wording:

> "The Storyteller assigns 1 or more players' characters."

The wiki page is authoritative and the difference is material: the current Gardener means
*no bag at all*, every seat assigned; the old wording allowed a partial hand-assignment.
The wiki page also now shows Type: **Loric**, Artist: Z. Benetatos, Revealed 28/04/2023,
and the Gardener does not appear in the Fabled page's list of Fabled.

How to Run (quoted / closely paraphrased from the wikitext):

> "During setup, instead of using a bag for character tokens, arrange them face-down on
> the Town Square (or similar object) in a circle matching the physical seating
> arrangement. Players then take tokens corresponding to their seated positions,
> proceeding clockwise one at a time."

Example (wiki): a Storyteller prepares a nine-character lineup — Wraith, Lord of Typhon,
Cerenovus, Zealot, Monk, Alsaahir, Banshee, High Priestess, Balloonist — laid out
face-down in seating order; Ben receives the Lord of Typhon, Julian the High Priestess.

Rules that matter for storytelling:

- **Normal setup rules still apply.** The team distribution for the player count, all
  bracketed setup modifiers, and character-specific setup requirements (Drunk's shown
  token, Lunatic's shown Demon, Marionette's neighbour-of-the-Demon requirement, Huntsman
  ⇒ Damsel, Choirboy ⇒ King) are unchanged; only the *randomness* is removed.
- **The point is design**: building a specific logic puzzle, a teaching game, or a
  narrative. It is announced publicly, so players know the assignment was deliberate — the
  metagame changes (they cannot argue "the bag wouldn't do that").
- **Seat-to-character mapping matters**, because the layout mirrors the seating circle;
  adjacency-sensitive characters (Empath, Chef, Investigator, No Dashii, Tea Lady, Cult
  Leader, Marionette) are being placed deliberately.
- Fabled/Loric general rules (<https://wiki.bloodontheclocktower.com/Fabled>): cannot be
  killed, immune to all game effects, do not count for the two-alive evil win. This one is
  necessarily declared **before setup**.
- **No night action**, **no reminder tokens**, **no setup bracket**, **no jinxes** listed.

## What the app does today

Data:

- `engine/src/main/resources/botc/data/characters.json:2248` — id `gardener`,
  `team: "fabled"`, ability = the **old** wording, `setup: false`, `reminders: []`.
  Same in `raw_sv_travellers_fabled.json`.
- Correctly absent from both night order lists; no `night_guide.json` entry.

Engine: nothing references `gardener` outside the data files.

UI — the pieces of a Gardener mode already exist, but they are not joined up:

- `SetupScreen.kt:496-498` — **"Start empty (assign in grimoire)"** calls
  `onStart(false, emptyList())`, which runs `viewModel.startGame(...)` with no deal
  (`SetupScreen.kt:110-118`). This is the closest thing to Gardener mode.
- `SeatSheet.kt:388-460` — `CharacterPicker` lets the ST set any seat's character from the
  script (Fabled and Travellers filtered out at `SeatSheet.kt:424`).
- `GameActions.validateSetupState` (`GameActions.kt:503-545`) + the "Begin night" guard
  (`GameShell.kt:130-140`) check the distribution and the Drunk/Lunatic/Marionette shown
  tokens on a manually assigned board, and can be overridden
  ("Start the night anyway", `GameShell.kt:583-587`).
- `RevealFlow.kt:39-80` — a "pass the phone" secret reveal, seat by seat, showing each
  player `characterShownToPlayerId`. Reached from the overflow menu
  ("Reveal characters to players…", `GameShell.kt:231`).
- `FabledSheet` (`GameExtras.kt:145-198`) can toggle `gardener` on — with no effect on
  anything.

Storyteller's actual experience today: pick "Start empty", then open every seat one at a
time and choose a character (a picker per seat, no overview of what's assigned vs. still
needed), then remember to open the overflow menu to reveal. The bag stage's "Need: N
townsfolk · N outsiders…" line is only shown on the *bag* screen, which "Start empty"
skips — so during hand-assignment there is no running target at all. Toggling the Gardener
Fabled on changes nothing.

## Defects and gaps

1. **P1** · Wrong ability text · `characters.json:2248` has the superseded
   "assigns 1 or more players' characters"; the wiki says "assigns **all** players'
   characters". Anyone reading the library/reference screen is told the wrong rule.
2. **P1** · No distribution target during hand-assignment · The "Need: 5 townsfolk ·
   1 outsider · 1 minion · 1 demon (after [+2 Outsiders])" line lives only in `BagStage`
   (`SetupScreen.kt:369-376`). The "Start empty (assign in grimoire)" path
   (`SetupScreen.kt:496`) drops it, so during exactly the workflow the Gardener requires,
   the ST is flying blind until they hit the "Begin night" guard. Repro: Start empty,
   assign seats in the grimoire — nothing anywhere shows how many of each team you still
   need.
3. **P1** · Assignment is one-seat-at-a-time with no overview · `SeatSheet`'s
   `CharacterPicker` (`SeatSheet.kt:388`) has to be opened per seat, and it does not mark
   which characters are already assigned elsewhere (it computes `inPlay`
   at `SeatSheet.kt:400` but the group rendering at `:423-426` does not use it to disable
   or badge duplicates). The Gardener workflow is "lay out N tokens against N seats" — a
   single assignment board.
4. **P2** · Gardener activation is a no-op · Toggling it in `FabledSheet` changes no
   behaviour, no text and no validation. At minimum it should switch the setup flow into
   assign-mode and skip/soften the "Deal randomly" affordances.
5. **P2** · Cannot be declared before the game starts · `FabledSheet` is only reachable
   from `GameShell` (`GameShell.kt:239`, `:501`), i.e. after `startGame`. The Gardener is
   *purely* a setup-time Fabled.
6. **P2** · The reveal flow is buried · `RevealFlow` is the digital equivalent of "players
   take their token from the circle", but it is three taps deep in an overflow menu
   (`GameShell.kt:231`) and is not offered automatically after a hand-assigned setup.
7. **P3** · Taxonomy drift · The wiki now types the Gardener as **Loric**, not Fabled
   (`characters.json:2248` says `"team": "fabled"`). Only affects grouping/labelling.

## Proposed behaviour (spec)

Night action: **none**. Do not add to either night order.

Setup — a first-class "Gardener mode":

- Add a Fabled selector to `SetupScreen` (needed by every Fabled; see the cross-cutting
  note). When `gardener` is active, the bag stage becomes an **assignment board**:
  - A two-column layout: seats (in circle order, with names) on one side, the script's
    characters grouped by team on the other. Tap a seat, tap a character, done; tap an
    assigned pair to clear it.
  - A live target header identical to `BagStage`'s (`SetupScreen.kt:369-376`), driven by
    `Setup.adjustedDistribution(playerCount, assignedCharacters)` so bracket modifiers
    (Baron, Godfather, Huntsman⇒Damsel…) update the target as characters are placed, plus
    the Sentinel expansion when that Fabled is also active.
  - Per-team running counters: `Townsfolk 4/5 · Outsider 1/1 · Minion 1/1 · Demon 0/1`,
    coloured red until satisfied.
  - Already-assigned characters greyed out (the `inPlay` set computed at
    `SeatSheet.kt:400` is exactly what's needed), except the ids in
    `GameActions.DUPLICABLE` (`GameActions.kt:412`).
  - "Randomize the rest" as a convenience — fill unassigned seats legally from the
    remaining pool via `randomBag`-style logic, so a partly designed lineup is still easy.
- The same board should be reachable **mid-setup from the grimoire**, so a partially dealt
  game can be fixed without walking seat by seat.
- `validateSetupState` (`GameActions.kt:503`) is already the right guard; with the Gardener
  active it should be surfaced **continuously** on the assignment board rather than only
  at the "Begin night" press, and the "Start the night anyway" override text should not
  blame a Fabled the ST has already declared.
- Adjacency helper: because the Gardener's whole purpose is designed seating, the board
  should show the circle order and flag adjacency-sensitive placements it can derive —
  Marionette must neighbour the Demon (`GameActions.kt:522-534` already validates the
  Marionette's shown token; add the neighbour check), No Dashii's Townsfolk neighbours,
  Tea Lady's neighbours, Cult Leader's neighbours, Empath/Chef counts.
- After assignment, offer **"Reveal characters to players"** (`RevealFlow.kt:39`) as the
  natural next action instead of hiding it in the overflow menu — this is the digital
  equivalent of the players taking their tokens from the circle.

Immediate effects / deferred effects / expiry / information: none — the Gardener has no
in-game mechanics after setup.

Visibility: the Gardener is announced publicly at game start. Offer the same
"Announce active Fabled" show card described in the Sentinel spec.

Day-time inputs: none.

Interactions to handle explicitly:

- **Every setup-modifying character** still applies; the assignment board must use the same
  `Setup.allowedDistributions` machinery as the bag builder so the two paths cannot
  disagree.
- **Drunk / Lunatic / Marionette / Fortune Teller red herring** — the existing setup
  prompts (`GameShell.kt:348-420` and following) already fire on SETUP; make sure they also
  fire for a hand-assigned board, and ideally inline on the assignment board rather than as
  modal dialogs after the fact.
- **Sentinel** — if both are active, the assignment board's target must accept the ±1
  Outsider range.
- **Revolutionary** — mutually redundant with the Gardener (the ST is assigning anyway);
  if both are active, just enforce the pair's matching alignment as a board warning.

UI text:

- `Gardener — you assign every character. Normal setup rules still apply.`
- Header: `Assign 8 seats — Townsfolk 4/5 · Outsider 1/1 · Minion 1/1 · Demon 1/1`
- Completion: `All seats assigned and the distribution is legal. Reveal characters to
  players?`

Data changes:

- `characters.json:2248` — update the ability to the current wiki text:
  `"The Storyteller assigns all players' characters."`
- Consider a `type`/`category` field distinguishing Fabled from Loric for the reference
  screens (Gardener and Storm Catcher are the two in this audit group that moved).

## Tests to add

1. **Given** `characters.json`, **when** the `gardener` entry is read,
   **then** its ability is `"The Storyteller assigns all players' characters."`
   (fails today).
2. **Given** an 8-player TB game started empty with `fabledIds = ["gardener"]` and seats
   assigned 5 Townsfolk / 1 Outsider / 1 Minion / 1 Demon,
   **when** `GameActions.validateSetupState` runs, **then** it returns no issues.
3. **Given** the same board with 6 Townsfolk and 0 Outsiders,
   **when** validated, **then** exactly one Outsider issue and one Townsfolk issue are
   reported (the assignment board's live counters must consume the same data).
4. **Given** a Baron assigned to a seat, **when** the target distribution is recomputed,
   **then** the required Outsider count rises by 2 and the Townsfolk count falls by 2 —
   i.e. `Setup.adjustedDistribution` drives the assignment board, not
   `Setup.distributionFor`.
5. **Given** a Huntsman assigned with no Damsel on the board,
   **when** validated, **then** the "requires the damsel" issue from
   `GameActions.kt:481-486` is present.
6. **Given** a Marionette assigned to a seat that does not neighbour the Demon,
   **when** validated, **then** a neighbour issue is reported (new check).
7. **Given** `fabledIds = ["gardener"]`, **when** the first- and other-night sheets are
   built, **then** no step with `id == "gardener"` appears.
