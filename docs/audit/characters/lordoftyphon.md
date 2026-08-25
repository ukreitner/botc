# Lord of Typhon (lordoftyphon) — Experimental Demon

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Lord_of_Typhon> (fetched 2026-08-25),
jinx list <https://wiki.bloodontheclocktower.com/Djinn>.

**Current ability text (verbatim):**
> "Each night\*, choose a player: they die. [Evil characters are in a line. You are in the middle. +1 Minion. -? to +? Outsiders]"

**How to Run (verbatim, complete):**
> "During setup, remove all Minion tokens and add Townsfolk or Outsider tokens.
>
> During the first night, wake the appropriate number of players directly clockwise and anti-clockwise from the Lord of Typhon. Show each of these players a unique Minion token, and give a thumbs down. Replace these players' good character tokens with these Minion tokens and put these players to sleep. Then, do the Minion Info and Demon Info steps as normal.
>
> Each night except the first, wake the Lord of Typhon. They point at any player. That player **dies**—mark them with the **DEAD** reminder. Put the Lord of Typhon to sleep."

**Seating rule (verbatim):**
> "All evil characters sit next to each other in a continuous line. The Lord of Typhon must have an evil character on both sides. They cannot sit at the end of the line of evil characters."

**Examples (verbatim, complete):**
> "There are two Minions: the Organ Grinder and the Mezepheles. In between them, neighboring them both, sits the Lord of Typhon. The number of Outsiders is normal.
>
> The Vizier neighbors the Harpy, who neighbors the Lord of Typhon, who neighbors the Goblin. There are ten players, and two Outsiders in play, due to the Lord of Typhon ability.
>
> The Fearmonger neighbors the Boomdandy, who neighbors the Lord of Typhon, who neighbors the Poisoner, who neighbors the Mastermind. There are 15 players, but zero Outsiders in play, since the Lord of Typhon removed one Outsider, and the Puzzlemaster became the Boomdandy during the first night."

**What this means operationally (derived, with the reasoning shown):**

- The bag is dealt with **no Minion tokens in it at all**. Minion slots are filled
  with extra Townsfolk/Outsider tokens, so the players who will become Minions
  genuinely draw and briefly believe a good character. The Minions are *created* on
  night 1 by replacing those seats' tokens.
- Minion count = base distribution **+1**. Outsider count is a free storyteller
  choice ("-? to +?"): example 2 has 10 players with 2 Outsiders (base 10p = 0), and
  example 3 has 15 players with 0 (base 15p = 2, minus at least one). Example 3's
  four Minions (Fearmonger, Boomdandy, Poisoner, Mastermind) at 15 players confirms
  base 3 + 1 = 4.
- The evil block is **contiguous**, with the Lord of Typhon **strictly interior**.
  The split need not be even: example 2 is `Vizier · Harpy · LoT · Goblin` — three
  Minions, two anticlockwise and one clockwise. "The appropriate number of players
  directly clockwise and anti-clockwise" is therefore a storyteller choice of split,
  subject to at least one on each side.
- Each converted neighbour gets a **unique** Minion token (no duplicates) and a
  thumbs down (i.e. "you are evil"), and their good token is **replaced** — they are
  that Minion for the rest of the game, with that Minion's ability from night 1.
- The conversion happens **before Minion Info and Demon Info**, which then run
  normally: the new Minions learn each other and the Demon, and the Demon learns
  them plus three bluffs.
- Example 3 shows conversion can hit an Outsider whose own ability then fires
  ("the Puzzlemaster became the Boomdandy during the first night"), and that this
  changes the live Outsider count.
- Nights 2+ are a plain single demon kill.

**Jinx (verbatim):**
- Summoner / Lord of Typhon: "If a Lord of Typhon is summoned, they must neighbor a
  Minion & their other neighbor becomes an evil Minion."

**Uncertain (flagged, not guessed):** the wiki does not say what happens if the Lord
of Typhon's neighbours change seats mid-game, or whether a Minion created this way
that later becomes good (Mezepheles, Pit-Hag…) breaks the line. Treat the line as a
setup-time constraint only.

## What the app does today

**Data**
- `characters.json:2046-2058` — ability at `:2050` matches the wiki; `setup: true`
  (`:2051`). `firstNightReminder` (`:2052`) "Wake the Lord of Typhon's neighbors.
  Show the 'You are' and Minion tokens & give a thumbs-down." `reminders: ["Dead"]`.
- `night_and_jinxes.json` — first night index **1**, immediately after DUSK and
  before `kazali` (`:296`); other night index 45 (`:418`). Both correct: the
  conversion must precede MINION_INFO (index 14) and DEMON_INFO (index 18), and it
  does. **Works.**
- `night_and_jinxes.json` has **no** lordoftyphon jinx, so the Summoner jinx never
  appears in "Jinxes in play" (`GameExtras.kt:202-231`).
- `night_guide.json:1628-1644` — the first-night prose at `:1630` is genuinely good (it names
  the mechanic, says not to wake the Lord of Typhon itself, and says to run
  minion/demon info afterwards) and offers one "To each neighbor" card with
  `token: "pick"`.

**Setup**
- `Setup.modifierFor` (`Setup.kt:121-232`) parses the bracket correctly:
  `isChoice` is true (the bracket contains " to " and "?"), the `?`-branch at
  `:150-153` yields `choiceTeams = {OUTSIDER}`, and `:203-208` applies the last
  concrete delta, "+1 Minion", giving `minionDelta = +1`. `validateBag`
  (`GameActions.kt:420-496`) then relaxes Outsiders and Townsfolk (`:435-442`) and
  checks Minions = base+1 and Demons = 1. **The arithmetic works.**
- **But** `GameActions.randomBag` (`:338-402`) deals real Minion characters into the
  bag, and `GameActions.deal` (`:313-329`) assigns them to random seats. The
  official process — no Minions in the bag, Minions created on night 1 next to the
  Demon — is not representable. There is no seat-arrangement step at any point;
  `validateSetupState` (`:503-561`) special-cases only Drunk, Lunatic, Marionette and
  Fortune Teller.

**Night 1**
- The step's detail is the `firstNightReminder`; the guide prose renders below it
  (`NightScreen.kt:792-801`).
- `QuickResolutions` (`NightScreen.kt:462-525`) has no `lordoftyphon` case, so the
  `else` branch (`:518-523`) fires: team is DEMON and the holder is alive, so
  **`DemonKillPanel` is rendered on the first night** — "Demon kill — who did X
  choose?" with a working kill button (`:534-638`). Same defect class as the
  reported Pukka bug.
- Nothing computes or displays **who** the neighbours are.
- Converting a neighbour requires leaving the Night tab: Grimoire → tap seat →
  "Change character" → `CharacterPicker` (`SeatSheet.kt:388-...`), per neighbour,
  then back to the night sheet. The `token: "pick"` show card
  (`NightScreen.kt:392-435`) only *displays* a token; it does not change the seat.
- `MINION_INFO` / `DEMON_INFO` steps recompute their player lists from current state
  (`NightOrder.kt:60-119`) and `steps` is keyed on `state.players`
  (`NightScreen.kt:84-90`), so once the ST does convert the seats by hand the info
  steps do list the right people. **Works.**

**Nights 2+**
- Plain `DemonKillPanel`. Correct modality.

## Defects and gaps

1. **P0 · Night 1 offers a demon kill.**
   The Lord of Typhon does not kill on night 1 — it converts its neighbours. The app
   shows the kill panel and a live "X dies" button. `NightScreen.kt:518-523`,
   `:534-638`. Repro: any game with a Lord of Typhon, Night tab, expand the step.

2. **P0 · The setup the ability describes cannot be produced by the app.**
   Rules: remove all Minion tokens from the bag; create the Minions on night 1 as the
   Demon's neighbours. App: `randomBag`/`deal` (`GameActions.kt:338-402`, `:313-329`)
   put real Minion characters on random seats. The ST has to hand-build the bag,
   hand-place every seat, and hand-arrange the circle — the exact class of manual
   bookkeeping this audit exists to remove. Repro: SetupScreen → "Random bag" with a
   Lord of Typhon script; observe Minions dealt to arbitrary seats.

3. **P0 · No validation of the evil line.**
   Rules: evil is contiguous and the Lord of Typhon is strictly interior. The app
   starts the game regardless. `GameActions.validateSetupState:503-561` has no
   lordoftyphon branch, and `GameShell.kt:133-140` is the only gate. Repro: assign a
   Lord of Typhon with Minions across the circle → "Begin night" succeeds.

4. **P1 · The neighbours are never identified.**
   The one piece of arithmetic the app is perfectly placed to do — "the seats
   clockwise/anticlockwise of the Lord of Typhon are Bob and Dana" — is absent from
   the step detail (`NightOrder.kt:142-178` uses the static reminder string) and from
   the guide prose (`night_guide.json:1630`).

5. **P1 · Conversion is a multi-screen manual chore with no uniqueness check.**
   Two-to-four seats must each be re-assigned via Grimoire → SeatSheet →
   CharacterPicker, with the ST responsible for (a) picking Minions that are on the
   script, (b) not repeating a Minion, (c) counting to base+1, and (d) remembering
   which good token each player *had* (which matters: those tokens are now
   not-in-play and become legal Demon bluffs / Ojo misses).

6. **P1 · The outsider count choice is invisible.**
   `choiceTeams = {OUTSIDER}` makes `validateBag` permissive but the SetupScreen only
   prints the bracket text (`SetupScreen.kt:374-376`) — it never asks the ST "how
   many Outsiders do you want?", never shows the legal range, and never explains that
   the Townsfolk count moves to compensate.

7. **P1 · Missing Summoner jinx.**
   No lordoftyphon entry in `night_and_jinxes.json`, and the Summoner's night-3
   demon creation (`night_and_jinxes.json` otherNight `summoner`) has no branch that
   would enforce "they must neighbor a Minion & their other neighbor becomes an evil
   Minion".

8. **P2 · The night-1 show card is a display, not an action.**
   `night_guide.json:1631-1638` offers one "To each neighbor" card with a picked
   token. It shows the token full-screen but leaves the grimoire untouched, and it
   must be re-opened and re-picked for the second neighbour with no memory of the
   first.

9. **P2 · No thumbs-down / "you are evil" card is offered.**
   `ShowCard.AlignmentCard(evil = true)` exists (`ShowCards.kt:69`) and the guide
   schema supports `kind: "evil"` (`NightGuide.kt:22-26`), but the Lord of Typhon
   entry does not use it, so the "give a thumbs down" half of the instruction has no
   button.

10. **P2 · Holder resolution by first seat index.**
    `NightScreen.kt:467` takes `step.playerIds.firstOrNull()` and `:520` requires it
    to be alive. `GameActions.starPass` (`:79-96`) leaves the dying demon's
    `characterId` intact, so any script where a Lord of Typhon can arrive by star
    pass, Pit-Hag, Kazali or Summoner can end up with two `lordoftyphon` seats; the
    lower-numbered seat then drives the panel, and if it is the dead one the step
    offers no tools at all. Same root cause as the cross-cutting star-pass defect.

11. **P3 · `firstNightReminder` says "neighbors" (US spelling) while the rest of the
    app uses "neighbour"** (`StatusEffects.kt:25`, `:88`). Cosmetic inconsistency.

## Proposed behaviour (spec)

### Setup (new: a Lord of Typhon setup wizard)

Follow the existing `HiddenIdentityDialog` pattern in `GameShell.kt:347-470` (the
Fortune Teller / Drunk / Lunatic / Marionette prompts) — a blocking SETUP-phase
dialog that cannot be skipped past "Begin night".

- **Bag:** when a Lord of Typhon is in the bag, `randomBag` must draw **zero**
  Minions and instead draw `minionCount` extra good characters (Townsfolk by
  default, or Outsiders at the ST's choice), where `minionCount = base + 1`. Record
  the reserved Minion identities separately in game state (see below) rather than
  dealing them.
- **Outsider choice:** the wizard asks "How many Outsiders?" with the base count
  pre-selected and the full legal range 0..(base+2) offered, adjusting Townsfolk to
  compensate. `Setup.modifierFor` already exposes `choiceTeams = {OUTSIDER}`; the
  SetupScreen must consume it instead of only printing the bracket.
- **Seating:** after the deal, the wizard shows the circle and asks the ST to place
  the Lord of Typhon's seat, then shows the computed neighbour seats and asks for the
  **split** (e.g. "3 Minions: 2 anticlockwise + 1 clockwise" / "1 + 2"). It offers a
  "reorder seats" shortcut into the existing `ReorderSeatsDialog`
  (`GameExtras.kt:110-143`) so the ST can move real people into the line.
- **New state:** `GameState.pendingMinionIds: List<String>` (or a per-seat
  `pendingCharacterId`) holding the Minion tokens reserved for the night-1
  conversion. Persist the converted seats' **original** good character ids in the
  `Player.note` and in the game log, because those tokens are now not-in-play.
- **Validation** — extend `GameActions.validateSetupState:503-561` with a
  `lordoftyphon` branch that reports:
  - "Lord of Typhon: the evil players must sit in one unbroken line" (compute the set
    of evil seats and check contiguity modulo the circle);
  - "Lord of Typhon: it must have an evil player on both sides — it cannot sit at the
    end of the line";
  - "Lord of Typhon: expected <base+1> Minions, found <n>";
  - "Lord of Typhon: <n> Minion tokens are still to be created on the first night"
    (informational, not blocking).

### Night action — structured form

**First night**
- **when:** first night; wake condition: **the Lord of Typhon itself does not wake**.
  The step is a storyteller action on the neighbouring seats. It runs even if the
  Lord of Typhon is dead (it cannot be, on night 1) and always precedes MINION_INFO.
- **targets:** the `k` seats clockwise and `j` seats anticlockwise of the Lord of
  Typhon, where `k + j = base + 1` and `k >= 1`, `j >= 1`. The step computes and
  **names** these seats; the ST may adjust the split inline.
- **immediate effects:** for each neighbour seat in turn, a two-tap row:
  1. pick a Minion character from the script (already-assigned Minions are disabled,
     enforcing "a unique Minion token"; default sort = Minions not yet used);
  2. "Show <Name> their token" → a full-screen `ShowCard.CharacterCard("YOU ARE",
     minionId)` followed by `ShowCard.AlignmentCard(evil = true)` (the thumbs down).
  Confirm applies `GameActions.assignCharacter(seat, minionId)` **and** writes
  `Player.note = "Drew the <OldCharacter>; made a <Minion> by the Lord of Typhon"`
  **and** appends a log entry. Because `NightScreen.kt:84` keys `steps` on
  `state.players`, the MINION_INFO / DEMON_INFO steps below refresh automatically.
- **deferred effects:** none. Minion Info and Demon Info run as normal immediately
  afterwards; the newly created Minions with first-night abilities (Poisoner, Widow,
  Godfather, Evil Twin, Fearmonger, …) appear in the night order automatically once
  their `characterId` is set — the step must warn the ST if a converted Minion sits
  **earlier** in the night order than the Lord of Typhon (none currently do, since
  the Lord of Typhon is index 1, but the check is cheap insurance).
- **expiry:** none.
- **information:** none computed. If a converted seat held an Outsider whose ability
  triggers on becoming another character (Puzzlemaster, per example 3), surface a
  one-line note.
- **visibility:** the two/three/four converted players see their Minion token and an
  evil thumbs-down; they do **not** learn who the Demon is at this step (Minion Info
  does that). The Demon is not woken at this step.
- **UI text:** *"The Lord of Typhon does not wake. Its neighbours become Minions:
  <names>. Show each a different Minion token and a thumbs-down, then replace their
  token. Minion Info and Demon Info run normally afterwards."*

**Other nights**
- **when:** other nights; Lord of Typhon alive, not `exorcist:Chosen`.
- **targets:** 1 alive player, no constraints. Standard `DemonKillPanel`.
- **immediate effects:** `GameActions.kill(target, DEMON)` after the `deathNotes`
  protection review.
- **expiry / deferred / information / visibility:** nothing special.

### Day-time inputs
None.

### Interactions/jinxes to handle explicitly
- **Summoner**: when the Summoner's night-3 step creates a Lord of Typhon, apply the
  jinx: require that the chosen player already neighbours a Minion, and convert their
  **other** neighbour into an evil Minion (ST picks which Minion), with the same
  two-tap show flow as night 1.
- **Marionette**: already legal (`validateSetupState:526-543` requires the Marionette
  to neighbour the Demon, which the line guarantees) — but the Marionette must be one
  of the converted neighbours, so the Marionette setup prompt must run **after** the
  night-1 conversion, not during SETUP. Flag this ordering to the day/setup mechanics
  auditor.
- **Mezepheles / Pit-Hag** turning a converted Minion good: the line breaks; per the
  wiki this is not addressed, so do nothing beyond a note in the log.
- **Exorcist**: standard; `NightOrder.kt:150-154` already appends the "does not act
  tonight" text.

### Data changes
- `night_and_jinxes.json` — add
  `{"id1":"summoner","id2":"lordoftyphon","reason":"If a Lord of Typhon is summoned,
  they must neighbor a Minion & their other neighbor becomes an evil Minion."}`.
- `night_guide.json:1628-1644` — add the missing setup sentence ("During setup,
  remove all Minion tokens and add Townsfolk or Outsider tokens"), state that the
  split need not be even, and add an `{"label":"Thumbs down","kind":"evil"}` show.
- `characters.json:2052` — "neighbours" spelling, and mention the uniqueness rule.

## Tests to add

1. `Given` a 10-player bag containing a Lord of Typhon, `When`
   `Setup.adjustedDistribution(10, bag)` is computed, `Then` minions == 3 (base 2 + 1)
   and the Outsider count is relaxed by `validateBag`.
2. `Given` a 15-player Lord of Typhon bag with 0 Outsiders and 4 Minions,
   `When` `validateBag(bag, 15)`, `Then` no issues (example 3 must be legal).
3. `Given` a seating where the Lord of Typhon sits at the end of the evil block,
   `When` `validateSetupState`, `Then` an issue "must have an evil player on both
   sides" is reported. **Fails today** (no such check).
4. `Given` a seating where two evil players are separated by a good player,
   `When` `validateSetupState`, `Then` a "must sit in one unbroken line" issue.
5. `Given` a first-night state with a Lord of Typhon, `Then` the built night step
   exposes a neighbour-conversion action and **no** kill action.
6. `Given` a first-night state, `When` both neighbour seats are converted to
   `poisoner` and `godfather`, `Then` `NightOrder.firstNight` now contains
   `poisoner` and `godfather` steps and the MINION_INFO step lists both players.
7. `Given` a Lord of Typhon whose two neighbours were assigned the *same* Minion id,
   `Then` `validateSetupState` reports a duplicate-Minion issue.
8. `Given` a randomly generated Lord of Typhon bag, `Then` it contains zero Minion
   characters and `base+1` reserved Minion ids. **Fails today.**
9. `Given` two seats both holding `characterId == "lordoftyphon"` (one dead),
   `Then` the night step's resolved holder is the **living** one.
