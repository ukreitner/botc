# Vortox (vortox) — Sects & Violets Demon

## Official rules (sources)

Sources:
- https://wiki.bloodontheclocktower.com/Vortox (Character Text, Summary, How to Run, Examples, Jinx)
- https://www.botcscriptorium.com/characters/vortox/ (confirms the current ability wording and the single Banshee jinx)
- https://wiki.bloodontheclocktower.com/Night_Order

Current ability text (wiki, verbatim):

> "Each night*, choose a player: they die. Townsfolk abilities yield false info. Each day, if no-one is executed, evil wins."

`characters.json:1116` carries exactly this string — **no drift**. (A secondary source paraphrases it as "Good abilities yield false info"; the wiki and the Scriptorium both give "Townsfolk abilities". Treat "Townsfolk" as authoritative.)

How to Run (wiki, verbatim):

> "While the Vortox is alive, you must give false information whenever a Townsfolk ability prompts you to give information. Each night except the first, wake the Vortox. They point at any player. That player **dies** - mark them with the **DEAD** reminder. Put the Vortox to sleep. Each dusk, if no player was executed today, the game ends and the evil team wins."

Examples (wiki, verbatim):

> "The Vortox kills the Sage. The Sage learns two players, both of which are not Demons. Nobody voted or nominated today, but the Mutant is executed. That night, both the Flowergirl and the Town Crier learn a 'yes.' The Savant is in play, and learns two pieces of information each day. Both are false. That night, the Dreamer chooses a player who is the Savant, and learns that player is either the Philosopher or the No Dashii. The Pit-Hag turns the Juggler into the Witch. The Juggler learns that they are now the good Witch, because this information comes from the Pit-Hag's ability, not a Townsfolk's ability. Today, a player died from the Witch, two Travellers were exiled, 5 nominations happened, but nobody was executed. Evil wins."

Everything load-bearing in that example:

- **"False" means genuinely false, not arbitrary.** The wiki: *"if you know a Vortox is in play…you can simply take the reverse of that information as gospel."* A Townsfolk who would truly learn "2" must be given something that is **not** 2. A Sage killed by the Demon must be shown a pair with **no** Demon in it. A Flowergirl whose Demon did not vote must be told **YES**.
- It applies to **information given by a Townsfolk ability**, and **only** that. It does not affect:
  - rules explanations,
  - character/alignment **changes** (the Pit-Hag example: the ex-Juggler correctly learns they are now the Witch),
  - non-information Townsfolk abilities — a Monk still protects, a Soldier is still safe, a Slayer still slays, a Snake Charmer's swap still happens (the wiki notes the Snake Charmer's information "can be considered true in a Vortox game"),
  - Outsider, Minion, Demon and Traveller information (the wiki does not extend it there).
- **"Even if they are drunk or poisoned, it must be false."** A droisoned Townsfolk normally gets *arbitrary* info; under a Vortox it must be *false*. This is the single strictest constraint in the game's information model.
- **Day info counts.** The Savant learns two pieces of information each day and **both are false** under a Vortox.
- **The dusk check**: *"Each dusk, if no player was executed today, the game ends and the evil team wins."* From the example: nominations happening, votes happening, a night death, and **two Traveller exiles** all count for nothing — only an **execution** counts. Note the wording is "no player was **executed**", not "no player **died by execution**": an execution that fails to kill (Devil's Advocate `Survives execution`, Fool's first death, Sailor, Tinker, Pacifist, Mayor's 3-player bounce, Lleech host, Golem…) still **is** an execution and still saves the town.
- The wiki does not explicitly address (a) a Vortox that is itself drunk or poisoned, or (b) what to do when a Townsfolk ability has no false answer available. The general rule that a drunk/poisoned character has no ability implies a droisoned Vortox neither falsifies information nor enforces the execution clause; state this as a Storyteller ruling rather than quoted text.
- **Jinx — Banshee**: *"If the Vortox kills the Banshee, all players learn that the Banshee has died."* (The Banshee's own ability is a Townsfolk ability, so without the jinx the announcement would have to be false.)

## What the app does today

Data:
- `characters.json:1112-1124` — team demon, `setup: false`, `otherNightReminder: "The Vortox points to a player. That player dies."`, reminders `["Dead"]`. **Correct.**
- `night_and_jinxes.json:417` — `vortox` in the other-night order between `nodashii` and `lordoftyphon`. **Correct.** No first-night entry. **Correct.**
- `night_and_jinxes.json:184-187` — Banshee jinx present (*"all players still learn that the Banshee has died"*). **Correct.**
- `night_guide.json:751-756` — good prose that names both clauses, but ends with *"If the Vortox is drunk or poisoned, no one dies, but information is still false."* (see defect 6).
- `night_guide.json` mentions the Vortox inside **11 other characters'** instructions (clockmaker `:402`, dreamer `:415,:432`, flowergirl `:451`, juggler `:457`, mathematician `:470,:481`, oracle `:494`, sage `:531`, seamstress `:537,:541`, towncrier `:595`) — always as *"If the … is drunk or poisoned, **or the Vortox is in play**, give a false answer."* **Works, and is good.**

Runtime:
- Night step: generic `else -> DemonKillPanel` (`NightScreen.kt:518-523`). Correct for this Demon — a plain "who dies?" picker with `deathNotes` protections and an impairment warning.
- `InfoCalc.commonCaveats` (`engine/.../InfoCalc.kt:158-166`):
  ```kotlin
  val vortoxInPlay = ctx.players.any { it.characterId == "vortox" && it.alive }
  if (vortoxInPlay && (holderTeam == Team.TOWNSFOLK || holderTeam == null)) {
      notes += "VORTOX in play — Townsfolk info must be FALSE."
  }
  ```
  Correct on the two things that matter most: it requires the Vortox to be **alive**, and it fires only for **Townsfolk** holders. Covered by `InfoCalcTest.kt:112-117`.
- `NightScreen.kt:904-919` — when a caveat contains `"VORTOX"` the step offers **"False info to show instead:"** chips: numbers `0..4` minus the true one when the headline starts with a digit, or the opposite of YES/NO. **Works for the numeric and boolean characters.**
- **The "no execution → evil wins" clause is implemented nowhere.** `WinCheck.kt:18-101` covers executed Saint, all-Demons-dead, and ≤2 alive; there is no Vortox branch. `GameShell.requestPhaseAdvance` (`GameShell.kt:126-168`) only raises the "Dusk falls" dialog when someone is *on the block and still alive* (`GameShell.kt:141-146`); with **no nomination at all, or a tie, or a nomination that failed**, Dusk advances straight into night with no check (`GameShell.kt:162`). The `duskGuard` dialog's "No execution" button (`GameShell.kt:608-612`) is the literal Vortox loss condition and says nothing.
- **Executions that kill nobody are not recorded at all.** The only trace of an execution in `GameState` is a `DeathRecord(cause = EXECUTION)` (`GameActions.kt:136-156`); the "Execute" buttons (`DayScreen.kt:112`, `DayScreen.kt:350-357`, `GameShell.kt:601`) all go straight to `kill`, and `SeatSheet.kt:288-305` lets the ST cancel with "Death prevented", which records **nothing**. So even a correct `deaths`-based Vortox check would wrongly end the game after a Devil's-Advocate-protected execution.

Storyteller's experience: the Vortox's false-info requirement is surfaced well **inside supported night info steps**, and nowhere else. The execution clause is entirely on the storyteller to remember — the exact failure mode the audit brief describes ("DA wasn't automatically removed", "Pukka offered to kill").

## Defects and gaps

1. **P0 · "Each day, if no-one is executed, evil wins" is not implemented anywhere.**
   No branch in `WinCheck.kt`, no check in `GameShell.requestPhaseAdvance` (`GameShell.kt:126-168`). Tapping "Dusk" on a day with no execution silently starts night 3 of a game evil has already won.
   *Repro*: Vortox game, day 2, nobody nominated. Tap **Dusk** → straight to Night 3, no prompt.
   *Second repro*: someone is on the block; tap Dusk; the dialog appears; tap **"No execution"** (`GameShell.kt:608-612`) → night begins, no prompt. The button that literally triggers the Vortox win condition is silent about it.

2. **P0 · The app cannot represent "an execution happened but nobody died".**
   Executions are only recorded as `DeathRecord(cause = EXECUTION)` (`GameActions.kt:147-155`). `SeatSheet.kt:304` offers "Death prevented", which cancels and stores nothing. Under a Vortox this is the difference between the town surviving and evil winning outright (Devil's Advocate, Fool, Sailor, Tinker, Pacifist, Lleech host, Mayor). A `GameState`-level record of "day N had an execution" is required before the Vortox check can be correct — and it also fixes the Undertaker (who wakes on an execution that killed nobody? no — but who *does* need the distinction) and the Vortox/Leviathan/Mayor family generally.

3. **P1 · "False info" is only offered for numeric and YES/NO results.**
   `NightScreen.kt:904-919` builds false alternatives from `leadingNumber` or a `YES`/`NO` prefix. Every other Townsfolk information shape gets the red caveat and **no help at all**:
   - `washerwoman`/`librarian`/`investigator` (`InfoCalc.kt:408-421`) — a character token + two players. Under a Vortox the shown pair must contain **no** player who is that character. The Librarian case is the classic trap: with Outsiders in play the false answer may be **"0 / no Outsiders"**, and with no Outsiders in play the ST must **not** show 0 and must invent a pair.
   - `undertaker` (`:281-293`), `ravenkeeper`/`grandmother` (`:376-384`), `dreamer` (`:344-354`) — must show a character that is **not** the true one.
   - `shugenja` (`:243-269`) — the answer is a direction; the false answer is the other direction (and "equidistant" has no false form, so the ST must pick a direction).
   - `knight` (`:433-440`), `steward` (`:442-449`), `noble` (`:451-458`), `sage` (`:423-431`), `king` (`:397-406`), `balloonist` (`:486-496`), `bountyhunter` (`:460-467`) — "point to N players such that …"; the false version inverts the predicate (the Knight must be shown a pair that **does** contain the Demon; the Steward must be shown an **evil** player; the Noble a set that is **not** 1-evil-2-good; the Sage a pair with **no** Demon).
   - `chef` (`:186-205`) — a number, so chips appear, but only `0..4`.

4. **P1 · Nothing warns about the Vortox during the DAY, where the Savant, Artist, Fisherman and Gossip live.**
   `savant` and `artist` are not in the night order at all (`night_and_jinxes.json` firstNight/otherNight lists) and not in `InfoCalc.supports` (`InfoCalc.kt:29-36`), so in a Vortox game — where the wiki example *specifically* calls out that both of the Savant's daily pieces must be false — the app never mentions it. There is no day-info surface at all.

5. **P1 · No persistent "VORTOX IS IN PLAY" indicator.**
   The caveat only appears once a supported info step is expanded. A storyteller running a 15-player Vortox game has no standing banner, no day-screen reminder, and no dusk reminder. Given that the Vortox changes *every* information decision for the whole game, this should be a fixture of the UI.

6. **P1 · `night_guide.json:753` asserts a rule that is very likely wrong.**
   Verbatim: *"If the Vortox is drunk or poisoned, no one dies, **but information is still false**."* The general rule is that a drunk or poisoned character's ability does not function — the whole ability, including the false-info clause and the execution clause. The wiki's "even if they are drunk or poisoned, it must be false" refers to the **Townsfolk** being droisoned, not the Vortox. I could not find wiki text supporting the guide's claim; flagging rather than asserting. Either way `InfoCalc.commonCaveats` (`InfoCalc.kt:161`) checks only `alive` and ignores the Vortox's own impairment, so the app and the guide at least agree with each other — and probably both disagree with the rules.

7. **P2 · The Vortox caveat does not fire for the Drunk or the Marionette.**
   `holderTeam` comes from `characterId` (`InfoCalc.kt:160`), so a Drunk (Outsider) or Marionette (Minion) who *believes* they are a Townsfolk gets `impairments()`' "arbitrary information" note but no Vortox note. Strictly this is **correct** — they have no Townsfolk ability for the Vortox to falsify — but it is a decision the app makes silently and many storytellers rule the other way for table consistency. It should be stated on the step, not left implicit.

8. **P2 · No cross-check that the info actually given was false.**
   The app knows the true answer and knows a Vortox is alive; it could refuse to offer the true answer's "show full-screen" chip (`NightScreen.kt:884-899`) while a Vortox lives. Today the true-answer chip sits right next to the false ones, one mis-tap from breaking the game.

9. **P2 · The Banshee jinx is data-only.**
   `night_and_jinxes.json:184-187` has the text, but when the Vortox kills the Banshee nothing surfaces it. `StatusEffects.deathNotes` (`StatusEffects.kt:94-103`) has an on-death trigger table that does not include `banshee`.

10. **P2 · No "no execution today" warning during the day, only at dusk.**
    Even once the dusk check exists, the useful moment is earlier: from the first nomination onward the Day screen should carry **"Vortox: someone must be executed today or evil wins."** `DayScreen.kt:88-93` shows the alive count and threshold and would be the natural home.

11. **P3 · The jinx text drifts slightly.**
    App: *"…all players still learn that the Banshee has died."* Official: *"…all players learn that the Banshee has died."* Semantically identical.

12. **P3 · The Vortox has no `Once`-style state, so nothing to expire.**
    `EXPIRES_AT_DAWN`/`EXPIRES_AT_DUSK` (`GameActions.kt:218-242`) correctly contain no Vortox entries.

## Proposed behaviour (spec)

### Night action

- **when**: other nights only. Wake condition: the `vortox` seat is **alive**.
- **targets**: 1, any player (self and Travellers allowed; dead disabled).
- **immediate effects**: `kill(target, DeathCause.DEMON)` after `StatusEffects.deathNotes` protection review; "attack fails" default when the Vortox `isImpaired`. Identical to the generic Demon panel — **the current `DemonKillPanel` is the right tool here**; only the standing-effect banner needs adding.
- **deferred effects**: none from the kill itself. If the target is the **Banshee**, surface the jinx: *"Jinx: all players learn the Banshee has died — announce it truthfully at dawn."*
- **expiry**: none.

### Standing effect 1 — false information

- **when**: continuously while a `vortox` seat is alive (and, per the rules reading in defect 6, **sober and healthy**; make this a settings-free engine rule and note it in the guide).
- **scope**: every piece of information produced by a **Townsfolk** ability, at night **and during the day**.
- **required engine change — `InfoCalc` must be able to produce a FALSE answer, not just a caveat.**
  Add `InfoCalc.falseAlternatives(characterId, trueResult, ctx): List<InfoResult>` returning concrete, *showable* wrong answers per shape:

  | shape | characters | false generator |
  |---|---|---|
  | count 0..N | chef, empath, clockmaker, oracle, mathematician, juggler, chambermaid | every value in the legal range except the true one, nearest-first |
  | yes/no | fortuneteller, seamstress, flowergirl, towncrier, villageidiot (evil/good) | the opposite |
  | direction | shugenja | the other direction; if truly equidistant, both directions are false-eligible — say so |
  | one character token | undertaker, ravenkeeper, grandmother, dreamer | any in-script character that is **not** the true one, preferring in-play-looking bluffs |
  | character + 2 players | washerwoman, librarian, investigator | a (character, playerA, playerB) triple where **neither** player is that character; for the Librarian additionally offer **"show 0"** *iff* an Outsider is actually in play, and **forbid** "show 0" when none is |
  | point to N players with a predicate | knight, steward, noble, sage, bountyhunter, king, balloonist | the negation of the predicate, with the qualifying set listed |

  The night step then shows, when a Vortox is alive: the true answer **collapsed and un-showable**, and the false options as the primary chips.
- **UI**: replace the current conditional block (`NightScreen.kt:900-919`) with: if `vortoxActive`, hide the "Show … full-screen" chip for the true answer entirely, show a red header **"VORTOX — the answer you give MUST be false"**, and list the generated false options.
- **day information**: add a Day-screen "Townsfolk day info" panel for `savant`, `artist`, `fisherman` (and `alsaahir`, `gossip` claims) with the same false-answer machinery, since the wiki example turns on exactly this.
- **misregistration**: the Vortox constraint composes with Spy/Recluse. If a Recluse could register as the Demon, then telling the Fortune Teller "YES" on a Recluse is *arguably* true — so under a Vortox the ST must not lean on misregistration to satisfy the falseness requirement. Surface as a caveat when a Spy/Recluse is among the relevant players.
- **not affected** (say so on the step, because storytellers over-apply it): Monk protection, Soldier, Slayer, Snake Charmer swap, Innkeeper, Exorcist, Professor, character/alignment change announcements ("you are now the Witch"), and all Outsider / Minion / Demon / Traveller information.

### Standing effect 2 — the execution clause

- **model**: add to `GameState` a record of executions independent of deaths. Minimal shape: `val executionsByDay: Map<Int, Long?>` (day → executed player id, `null` never stored) or a `DayRecord` list. Populate it from **every** execution path (`DayScreen.kt:112`, `DayScreen.kt:350-357`, `GameShell.kt:601`, `SeatSheet.kt:274`) **including** the "Death prevented" branch (`SeatSheet.kt:304`), which must record *executed, survived*.
- **check timing**: at **dusk**, i.e. inside `GameShell.requestPhaseAdvance` when `state.phase == Phase.DAY`, *before* `advancePhase`:
  ```
  if (a sober, healthy, living Vortox exists && executionsByDay[state.cycle] == null)
      -> block the transition with an "EVIL WINS" dialog
  ```
  The dialog: **"No-one was executed today. The Vortox's ability ends the game — EVIL WINS."** Buttons: *Declare evil victory* (→ `RevealSheet`), *Record an execution I forgot*, *Override (house rule / Vortox is droisoned)*.
- **during the day**: a standing banner on the Day screen (`DayScreen.kt:88`) — **"VORTOX: an execution must happen today or evil wins."** — turning green the moment an execution is recorded: **"Execution recorded — the town survives the day."**
- **what counts**: any execution, whether or not it killed. A **Traveller exile does not count** (`Nomination.isExile`, `GameState.kt:71`) — the wiki example is explicit.
- **interactions**:
  - **Mayor** ("if only 3 players live and no execution occurs, good wins") directly collides with the Vortox clause. There is no official Vortox/Mayor jinx, so the Vortox's evil win applies at dusk first — flag the collision in the dialog and let the ST rule.
  - **Leviathan/Vortox** cannot both be the Demon.
  - **Scarlet Woman**: if she becomes the Vortox, the clause continues from that moment.
  - **Pit-Hag** creating a Vortox mid-game: the clause applies from that day's dusk onward.
  - If the Vortox is **dead** at dusk, the clause does not apply — the check must use the live state at the moment of dusk, not "was in play".

### UI text

- Night step header: **"Vortox — who did `<name>` choose?"**
- Standing red banner (Night, Day, and the dusk dialog): **"VORTOX ALIVE — every Townsfolk answer must be FALSE. An execution is required each day."**
- On an info step: **"VORTOX — the answer you give MUST be false. Not vague: false."**
- Day screen: **"No execution yet today. If dusk falls with none, evil wins."**

### Data changes

- `night_guide.json:753` — replace *"but information is still false"* with the correct impairment ruling, e.g. *"If the Vortox is drunk or poisoned it has no ability at all: no one dies, Townsfolk information does not have to be false, and a day without an execution does not end the game."* (Mark as a Storyteller ruling if the project prefers to keep the current behaviour — but the two must agree with each other and with `InfoCalc`.)
- `night_guide.json:753` — add *"A Traveller exile is not an execution. An execution that kills nobody (Devil's Advocate, Fool, Sailor, Tinker, Pacifist, Mayor) still counts."*
- `night_and_jinxes.json:186` — align the Banshee jinx wording with the wiki (*"all players learn that the Banshee has died"*).
- `characters.json` — no change.
- Night order — no change.

## Tests to add

1. **Dusk with no execution ends the game.**
   *Given* a living Vortox and a `Phase.DAY` state at cycle 2 with no execution recorded, *when* the dusk check runs, *then* it returns an evil-wins advisory. No such check exists today.

2. **Dusk after an execution that killed nobody does NOT end the game.**
   *Given* a player was executed but survived (Devil's Advocate `Survives execution`), *then* the dusk check passes. Impossible to express today — there is no record of a non-fatal execution.

3. **A Traveller exile does not satisfy the clause.**
   *Given* two Travellers were exiled and killed on day 2 and no execution occurred, *then* the dusk check still returns evil wins.

4. **A dead Vortox imposes nothing.**
   *Given* the Vortox was executed on day 3, *then* at dusk of day 3 the clause **is** satisfied (an execution happened) and at dusk of day 4 with no execution the check returns **no** advisory.

5. **The Vortox caveat requires a living Vortox.**
   *Given* a dead Vortox, *when* `InfoCalc.compute(..., "empath", ...)` runs, *then* no `VORTOX` caveat appears. **Passes today** — assert it (`InfoCalcTest.kt:112` only asserts the positive case).

6. **The Vortox caveat does not fire for a Minion or Outsider holder.**
   *Given* a living Vortox and the Spy holding a would-be info step, *then* no `VORTOX` caveat. **Passes today** — assert it.

7. **False alternatives exist for every supported Townsfolk.**
   *Given* a living Vortox, *for each* id in `InfoCalc.supports` whose character is Townsfolk, *then* `falseAlternatives(...)` returns a non-empty list and **none** of the returned results equals the true result. No such function today.

8. **Librarian with no Outsiders must not be offered "0".**
   *Given* a Vortox game with zero Outsiders, *then* the Librarian's false options must not include "no Outsiders in play" and must include at least one (character, pair) triple.

9. **Librarian with an Outsider may be offered "0".**
   *Given* a Vortox game with a Klutz in play, *then* "show 0 / no Outsiders" is a valid false option.

10. **Knight's false answer contains the Demon.**
    *Given* a Vortox game, *then* every generated false Knight option is a pair of players **including** a Demon.

11. **The true answer cannot be shown while a Vortox lives.**
    *Given* a living Vortox and a Chef whose true count is 1, *then* the step exposes no "Show 1 full-screen" affordance. Fails today (`NightScreen.kt:884-892`).

12. **Banshee jinx surfaces on the kill.**
    *Given* the Vortox targets the Banshee, *then* `StatusEffects.deathNotes` (or the kill panel) includes the jinx reminder. Fails today.
