# Ferryman (ferryman) — fabled Fabled

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Ferryman> (fetched verbatim via
`api.php?action=parse&page=Ferryman&prop=wikitext`, 2026-08-25). Categories:
`Experimental Characters`, `Fabled`. Revealed 27/07/2023.

Current ability text (verbatim summary line):

> "On the final day, all dead players regain their vote token."

**Summary bullets (verbatim):**

- "Use the Ferryman to create a fun and inclusive climax to the game even if new players have used their vote tokens."
- "If you are running a game for newer players who don't yet grasp the strategy of when to use their dead votes, or have used them when they forgot they were dead, you can add the Ferryman. This will ensure everyone gets a say in the final day's critical votes."
- "**All dead players regain their vote tokens on the final day, regardless of alignment or when they voted.**"
- "**If a dead player still has their vote token, they do not get a second one from the Ferryman.**"
- "**The final day is the day that the Storyteller thinks is most likely to be the last day of the game – the day where, if the Demon is not executed, evil will win. This most likely means the day with only 3 living players remaining.**"
- "**If vote tokens are used on the final day, they aren't returned.**"

**How to Run (verbatim):**

> During the game, when you notice that it would be a good idea to add it, declare that the Ferryman is in play. Add the Ferryman token to the Grimoire.
>
> At the start of the final day, ask the players to return vote tokens to any dead players that do not have one in the Town Square.

**Examples (verbatim):**

1. "Most of the group is new. Two players, Amy and Doug, forgot they were dead in the excitement of voting. The Storyteller puts the Ferryman in play. Later in the game, when there are three players left alive, the Storyteller declares that it is the final day. Amy and Doug regain their vote tokens."
2. "It is the start of the final day. 17 players are dead and three players are alive. 10 dead players have used their vote tokens. In order to create a more fun and engaging final day, the Storyteller adds the Ferryman and those dead players regain their vote tokens."

**Rules distilled:**

| | |
|---|---|
| Trigger | **The start of the final day**, once. |
| "Final day" | A **storyteller declaration**, not a derived fact — "the day the Storyteller thinks is most likely to be the last day", typically 3 alive. |
| Effect | Every **dead** player without a vote token gets one back. Living players are unaffected; a dead player who still holds theirs gets nothing extra (no double vote). |
| After | Votes spent on the final day are **not** returned again. So the restore fires exactly once. |
| Added when | Any time during the game — it is explicitly a mid-game rescue. |

**Jinxes:** none.
**Night order:** never wakes. Correctly absent from both order lists.

## What the app does today

Data:
- `characters.json:2210-2221` — ability text matches the wiki exactly; `team: fabled`,
  `setup: false`, no reminders, no night reminders. **Works** as data.
- `night_and_jinxes.json` — correctly absent from both order lists. **Works.**
- `night_guide.json` — no entry; no day-time run-book schema exists.

Code — **zero** engine awareness for the Ferryman itself. The underlying ghost-vote
machinery it needs, however, is all there:
- `Player.ghostVoteUsed: Boolean` (`GameState.kt:28`) — "Dead players hold one ghost vote
  until they spend it."
- `GameActions.kill` (`GameActions.kt:144-145`) sets `alive = false, ghostVoteUsed = false`
  — a newly dead player correctly starts with a ghost vote.
- `GameActions.revive` / `resurrect` (`GameActions.kt:162-181`) both reset
  `ghostVoteUsed = false` on return to life (harmless; a living player's flag is unused).
- `GameActions.toggleGhostVote` (`GameActions.kt:183-184`) — the only way to give one back,
  exposed as a single per-seat button, "Restore ghost vote" (`SeatSheet.kt:283-285`), which
  is only visible inside a dead player's seat sheet.
- `DayScreen.kt:184` — `canVote = p.alive || !p.ghostVoteUsed || isExile`; a spent dead
  player's chip is disabled.
- `DayScreen.kt:232-240` — on **Record**, every dead voter in the tally has their ghost
  vote spent automatically (skipped for exiles).
- Nothing anywhere defines, stores or displays a "final day".
  `GameState` (`GameState.kt:94-132`) has `phase` and `cycle` and no end-game marker;
  `WinCheck.check` (`WinCheck.kt:88-98`) only fires at **≤2 alive**, i.e. after the final
  day is already lost.

Storyteller's experience: to run the Ferryman on a 15-player game where 10 dead players
have spent their votes, they must open ten seat sheets in turn and press "Restore ghost
vote" ten times — while the table waits for the final day to begin — and they must remember
to do it at all, because nothing prompts them.

## Defects and gaps

1. **P1** · No bulk restore. The rule is one sentence — "all dead players regain their vote
   token" — and executing it costs one seat-sheet round trip per dead player
   (`SeatSheet.kt:283-285`). Wiki example 2 is literally ten of them.
   *Repro:* Fabled… → Ferryman → nothing changes; go restore votes one seat at a time.
2. **P1** · No final-day concept and therefore no trigger. The app never asks "is this the
   final day?", never records the answer, and has no dawn/day-start surface on which to ask
   (`GameActions.advancePhase`, `GameActions.kt:258-263`, only flips phase and sweeps
   tokens; `DayScreen.kt:85-92` opens with a bare "Day N" header). The storyteller must
   remember the Ferryman exists, at exactly the right moment, several hours into a game.
3. **P1** · No prompt at the natural trigger point. `state.alivePlayers.size` dropping to 3
   at dawn is precisely the wiki's "most likely means the day with only 3 living players
   remaining", and the app already computes and displays that number
   (`DayScreen.kt:86-92`, `GameShell.kt:177-180`). It is a one-line condition and it is not
   used.
4. **P2** · Restoring a ghost vote is a *toggle*, not a *restore*
   (`GameActions.toggleGhostVote`, `GameActions.kt:183-184`). A bulk operation built on it
   would flip already-unspent players *into* the spent state. The Ferryman needs an
   idempotent `restoreGhostVotes(state)` that only ever sets `ghostVoteUsed = false` —
   matching "If a dead player still has their vote token, they do not get a second one".
5. **P2** · The final-day restore is not recorded. The log (`GameExtras.kt:46-106`) has no
   Fabled events, so a post-game reconstruction of "why did ten dead players vote on day 6"
   is impossible.
6. **P2** · Nothing marks that the restore has already happened, so a storyteller who taps
   it twice (or undoes and redoes around it) can silently hand out a second round of votes
   after some were spent — contradicting "If vote tokens are used on the final day, they
   aren't returned."
7. **P2** · Travellers. `exileThreshold` counts all players (`GameState.kt:131`) and exile
   votes bypass the ghost-vote check entirely (`DayScreen.kt:184`, `|| isExile`;
   `DayScreen.kt:233` skips spending on exiles). The Ferryman restores *vote tokens*, which
   are the execution-vote resource, so this is consistent — but the Ferryman panel should
   say "affects execution votes; exiles were already unrestricted" so the storyteller
   isn't surprised.
8. **P3** · No show card / announcement for the table ("All dead players get their vote
   back for the final day"). `ShowCards.kt:66` `Message` would serve.
9. **P3** · The Fabled is listed alongside 16 others with no hint about *when* to add it;
   the Ferryman is unusual in being explicitly a mid-game addition ("During the game, when
   you notice that it would be a good idea to add it").

## Proposed behaviour (spec)

Shares the `FabledEntry` storage introduced in `angel.md`; the Ferryman uses `used: Boolean`
(the restore has fired) and the game gains one small piece of state.

- when: never wakes. Its only trigger is **the start of the final day**, once per game.
- new state: `GameState.finalDay: Boolean = false` (or `finalDayCycle: Int? = null`,
  which also records *which* day it was, for the log and the reveal).
  Set by the storyteller, never inferred silently.
- declaring the final day: two entry points, both writing the same flag —
  1. an automatic prompt at dawn: on `advancePhase` NIGHT→DAY, if
     `state.aliveNonTravellers.size <= 3` and `finalDay` is not yet set, the day-start
     briefing leads with

     > **Is this the final day?**
     > 3 players are alive — if the Demon isn't executed today, evil wins.
     > *(Ferryman is in play — declaring the final day returns every dead player's vote.)*
     > [ Yes, final day ] [ Not yet ]

     The prompt appears with or without the Ferryman (other Fabled and the Angel's
     "remove on the final day" advice need it too); the Ferryman line only shows when it
     is active.
  2. a manual "Declare final day" item in the main menu (`GameShell.kt:236-266`), for the
     storyteller who calls it at 4 or 5 alive.
- immediate effect on declaring, when `ferryman` is active and `!entry.used`:
  ```kotlin
  fun restoreGhostVotes(state: GameState): GameState =
      state.copy(players = state.players.map {
          if (!it.alive) it.copy(ghostVoteUsed = false) else it
      })
  ```
  — idempotent, never toggles, never touches living players. Set `entry.used = true`,
  append a log line "D<n>: FINAL DAY — Ferryman returns N vote tokens (Amy, Doug, …)", and
  show a dismissible banner **"FERRYMAN — all dead players have their vote back"** in the
  Mastermind-day banner style (`GameShell.kt:513-531`).
- after the restore: votes spent today are spent for good.
  `DayScreen.kt:232-240` already does this and needs no change; the guard is
  `entry.used`, which prevents a second restore. Undo (`GameViewModel.undo`,
  `GameViewModel.kt:111-116`) still works, because `used` is part of the state snapshot.
- targets/tokens/expiry/information: none. The Ferryman places no reminders and computes
  nothing.
- visibility: entirely public — announce it to the table. Offer
  `ShowCard.Message("FINAL DAY", "All dead players may vote")`.
- day-time inputs: none beyond the declaration.
- mid-game addition: adding the Ferryman **after** the final day has already been declared
  must run the restore immediately (wiki example 2 is exactly this: the storyteller adds it
  "at the start of the final day").
- interactions:
  - **Exiles** already ignore ghost votes (`DayScreen.kt:184, 233`); unchanged.
  - **Resurrection** on the final day (Professor, Zombuul, Bone Collector): a resurrected
    player is alive and votes normally; their `ghostVoteUsed` is reset by
    `GameActions.resurrect` (`GameActions.kt:175`) and is irrelevant while alive.
  - **Travellers** who were exiled are dead and are covered by "all dead players".
  - The Ferryman changes no win condition and interacts with no character.
  - `finalDay` should also drive the **Angel's** "remove the Angel on the final day" nudge
    (see `angel.md`) and could gate a **Fiddler** activation prompt (see `fiddler.md`) —
    one flag, three Fabled.

**UI text:**
- Fabled sheet row: "Ferryman · on the final day, every dead player gets their vote back."
- After firing: "Ferryman used on day 6 — 10 votes returned." (greyed, with an undo hint.)
- Day header once declared: "Day 6 · FINAL DAY · 3 alive · 2 votes to execute".

**Data changes:** none to `characters.json`, `night_and_jinxes.json` or the night order.
Add a `ferryman` entry to `night_guide.json` once a `day` section exists.

## Tests to add

1. `ferryman restores every dead player's ghost vote`
   Given 5 dead players, 3 of whom have `ghostVoteUsed = true`, and `ferryman` active,
   When the final day is declared,
   Then all 5 have `ghostVoteUsed = false` and no living player is modified.
   *(Nothing to fail today — the feature is absent.)*
2. `restore is idempotent, not a toggle`
   Given the state after test 1, When `restoreGhostVotes` runs again,
   Then nothing changes. *(Guards against building it on
   `GameActions.toggleGhostVote`, `GameActions.kt:183-184`, which would flip all 5 back to
   spent.)*
3. `votes spent after the restore are not returned again`
   Given the final day declared and the Ferryman fired, When Amy's ghost vote is spent on a
   nomination and the storyteller re-opens the Ferryman action,
   Then it reports "already used" and Amy stays spent.
4. `adding the ferryman after the final day fires immediately`
   Given `finalDay = true` and a dead player with a spent vote,
   When `ferryman` is added to `state.fabled`, Then that player's vote is restored.
5. `dawn at three alive proposes the final day`
   Given 3 alive non-travellers at a NIGHT→DAY transition and `finalDay = false`,
   Then the day-start briefing contains a final-day prompt; and given 4 alive, it does not.
6. `final day is never inferred without the storyteller`
   Given 3 alive and the prompt dismissed with "Not yet",
   Then `finalDay` stays false and no votes are restored.
7. `ferryman adds no night step`
   Given `fabled = [ferryman]`, Then neither night order contains a `"ferryman"` step.
   *(Passes today — regression guard.)*
