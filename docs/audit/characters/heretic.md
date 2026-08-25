# Heretic (heretic) — Experimental Outsider

## Official rules (sources)

Sources:
- <https://wiki.bloodontheclocktower.com/Heretic>
- <https://wiki.bloodontheclocktower.com/Baron>, <https://wiki.bloodontheclocktower.com/Lleech>,
  <https://wiki.bloodontheclocktower.com/Pit-Hag> (jinx wording)

Current ability text (matches `characters.json:1650`):

> "Whoever wins, loses & whoever loses, wins, even if you are dead."

Summary: "The Heretic turns a win into a loss, and a loss into a win."

How to Run (quoted, complete — it is two sentences):

> "If the game ends and the evil team would have won, declare that the good team wins and the evil
> team loses."
>
> "If the game ends and the good team would have won, declare that the evil team wins and the good
> team loses."

Examples (quoted):

> "On the first day, the Heretic publicly claims to be the Heretic. That night, the Demon kills
> themself. Evil wins."
>
> "The Heretic does not reveal their character until the final day, when 3 players are alive. They
> convince the good team to execute a good player, leaving 2 players alive, one of which is the
> Demon. Good wins."
>
> "The Heretic is dead. The Saint is executed. Good wins."
>
> "The Heretic is poisoned. The Assassin kills the Demon. Good wins."
>
> "There are 3 players alive. The Demon is executed. Because there is a Heretic in play, evil wins."

Points that matter for the app:

- **Works while dead.** The ability text says so explicitly ("even if you are dead"), and the third
  example is a dead Heretic reversing a Saint execution.
- **Does not work while drunk or poisoned.** The fourth example is decisive: a poisoned Heretic
  does not reverse the Assassin's Demon kill, so good wins normally. The relevant moment is the
  moment the game ends.
- **It reverses the *declared* result, whatever produced it** — Demon dead, 2 players alive with a
  Demon, executed Saint, Mastermind day, Vortox's "no Townsfolk learned true info" ending,
  Leviathan day 5, Klutz, Damsel guess, Politician, etc. The page states it as a blanket rule on
  "the game ends and X would have won".
- **The Heretic is on the losing team when they reverse a good win** in the sense that good loses
  — but the *Heretic themself* is a good-aligned player whose team is good, so the Heretic loses
  when good "wins" pre-reversal and wins when good loses pre-reversal. In practice: the Heretic
  wants the Demon to survive.
- Tips & Tricks (quoted, the key strategic inversion the ST should understand when adjudicating):
  > "Keep the Demon alive at all costs. Normally, in non-Heretic games, the good team can win at
  > any time (by executing the Demon), while the evil team can only win on the final day (by
  > executing a non-Demon player). In a Heretic game, the evil team can win at any time (by
  > executing the Demon), while the good team can only win on the final day (by executing a
  > non-Demon player)."
- The Heretic **never wakes** and has **no reminder tokens** — correct in `characters.json:1652-1657`.

Jinxes (current official wording, verified on each partner's page):

| Partner | Official text | App text (`night_and_jinxes.json`) |
|---|---|---|
| Baron | "Only 1 jinxed character can be in play." | **"The Baron might only add 1 Outsider, not 2."** (line 56) — stale/wrong |
| Godfather | "Only 1 jinxed character can be in play." | matches (line 61) |
| Lleech | "Only 1 jinxed character can be in play." | **"If the Lleech has poisoned the Heretic, and the Lleech dies, the Heretic remains poisoned."** (line 81) — stale/wrong |
| Pit-Hag | "Only 1 jinxed character can be in play." | **"A Pit-Hag can not create a Heretic."** (line 76) — stale/wrong |
| Spy | "Only 1 jinxed character can be in play." | matches (line 66) |
| Widow | "Only 1 jinxed character can be in play." | matches (line 71) |
| Boffin | "The Demon cannot have the Heretic ability." | matches (line 276) |

"Only 1 jinxed character can be in play" is a **bag/setup constraint**: the Heretic and that partner
must not both be in play. Every one of those partners can poison or replace the Heretic, which is
why the jinx exists.

## What the app does today

Data:
- `characters.json:1647-1657` — correct ability text, `setup: false`, no night reminders, no
  reminder tokens. **Works.**
- `night_guide.json` — no `heretic` entry. Correct (never wakes), but it means the only place the
  Heretic's rule text appears is the Script/Reference tab and the seat sheet's ability line.
- `night_and_jinxes.json` — not in either night order (correct). Seven jinxes present; **three have
  stale text** (Baron, Lleech, Pit-Hag) as tabulated above.

Engine:
- `WinCheck.kt` — **no Heretic branch anywhere**. `check()` returns:
  - `goodWins = false` for an executed Saint (`:51-68`),
  - `goodWins = true` when all Demons are dead (`:70-86`),
  - `goodWins = false` at ≤2 alive with a Demon (`:88-98`),
  - Mastermind-day resolution (`:28-49`).
  None of these consider a Heretic.
- `GameDataTest.kt:76-79` asserts the `lleech`+`heretic` jinx is found — i.e. the stale text is
  currently locked in by a test that only checks the id pair, not the wording.
- Nothing else in the engine mentions `heretic`.

UI:
- `GameShell.kt:506-519` shows `WinAdvisoryDialog` from `WinCheck.check`, whose confirm button reads
  "Declare good victory" / "Declare evil victory" (`GameExtras.kt:253-262`) using
  `advisory.goodWins` directly.
- `GameShell.kt:258-265` also offers manual "Declare good victory" / "Declare evil victory" menu
  items.
- `RevealSheet` (`GameExtras.kt:270-350`) prints "GOOD WINS" / "EVIL WINS" from the boolean it is
  handed.
- The Heretic's presence changes **nothing** in any of these. The seat sheet shows the ability text
  and the jinx lines (`SeatSheet.kt:196-235`), and that is the entire treatment.

So: the app will confidently tell a storyteller "Every Demon is dead — good wins" in a Heretic game
where evil has just won, and offer a one-tap button that declares the wrong winner.

## Defects and gaps

1. **P0 · `WinCheck` never reverses the result for an in-play Heretic.** Rules: "If the game ends
   and the good team would have won, declare that the evil team wins…". App: `WinCheck.kt:70-86`
   returns `Advisory(goodWins = true, "Every Demon is dead — good wins, unless an ability says
   otherwise.")` with cautions for Scarlet Woman / Mastermind / Imp only. Repro: game with a
   Heretic; execute the Demon; the dialog says "Every Demon is dead — good wins" and its primary
   button is "Declare good victory". Following the app breaks the rules.

2. **P0 · The reveal screen declares the pre-reversal winner.** `GameExtras.kt:286-290` prints
   "GOOD WINS"/"EVIL WINS" from the raw boolean, so even a storyteller who remembers the Heretic
   has to mentally invert what the app is showing to the table.

3. **P1 · Nothing checks whether the Heretic is drunk/poisoned at the moment the game ends.**
   Rules: the poisoned-Heretic example is explicit. The machinery exists —
   `StatusEffects.isImpaired` (`StatusEffects.kt:36-46`) and the `abilityImpairedAtDeath` snapshot
   (`GameState.kt:87`) — but no caller.

4. **P1 · Three jinx texts are stale and wrong** (`night_and_jinxes.json:56` Baron, `:76` Pit-Hag,
   `:81` Lleech). Repro: run a Heretic + Pit-Hag script, open "Jinxes in play"
   (`GameExtras.kt:200-232`) — it says a Pit-Hag cannot create a Heretic, when the current rule is
   that the two must not both be in play at all. `GameDataTest.kt:76` pins the id pair but not the
   text, so the drift is invisible to CI.

5. **P1 · The "only 1 jinxed character can be in play" constraint is not enforced at setup.**
   `GameActions.validateBag` (`GameActions.kt:420-496`) checks distribution, companions and
   duplicates. It does not read jinxes at all, so a bag containing Heretic + Baron passes silently.

6. **P2 · The Heretic is invisible during the game.** There is no persistent banner or day-start
   line reminding the ST that this game's win condition is inverted — the equivalent of the
   Mastermind banner that already exists (`GameShell.kt:520-537`). Given the ability is entirely a
   game-end rule, a running reminder is the single highest-value affordance.

7. **P2 · The Boffin jinx is not enforced.** "The Demon cannot have the Heretic ability." Nothing
   in the app models Boffin-granted abilities, so this is text only. (Cross-cutting: the Boffin
   needs its own model of "the Demon also has character X's ability", which would also affect
   `WinCheck`.)

8. **P3 · No `night_guide`/reference prose.** The Heretic never wakes, so the run-book has nowhere
   to live; the two How-to-Run sentences and the poisoned/dead clarifications should be reachable
   from the Script tab and from the win dialog.

## Proposed behaviour (spec)

The Heretic has **no** night step, no targets, no tokens, and no day-time inputs. Its entire
implementation is in `WinCheck` plus setup validation and a persistent reminder.

### Engine: `WinCheck`

Add a final post-processing pass to `WinCheck.check`, applied to **every** advisory it would return
(and to the manual "Declare … victory" menu path too):

```
fun applyHeretic(state, lookup, advisory): Advisory
  hereticSeats = state.players.filter { it.characterId == "heretic" && !it.isTraveller }
  if (hereticSeats.isEmpty()) return advisory
  // dead is fine ("even if you are dead"); impaired is not
  functioning = hereticSeats.filter { !StatusEffects.isImpaired(state, lookup, it) }
  if (functioning.isEmpty())
      return advisory.copy(cautions = advisory.cautions +
          "Heretic is in play but drunk/poisoned — the result is NOT reversed.")
  if (advisory.goodWins == null) return advisory  // "check this" advisories pass through
  return advisory.copy(
      goodWins = !advisory.goodWins,
      reason = advisory.reason + "  HERETIC in play — the result is REVERSED: " +
               (if (!advisory.goodWins) "the evil team wins." else "the good team wins."),
      cautions = advisory.cautions +
          "Heretic reverses this. Check the Heretic is not drunk/poisoned right now."
  )
```

- Apply it **after** the Saint / demons-dead / ≤2-alive / Mastermind branches, so the reversal is
  the last word — which is exactly what the rule says ("If the game ends and X would have won…").
- The Mastermind-day branch (`WinCheck.kt:28-49`) must also be reversed.
- If a Heretic is created mid-game (Pit-Hag is jinxed out, but Amnesiac/Hatter/Boffin scripts
  exist), the same check applies from the moment they hold the character.

### Engine: setup validation

- Extend `GameActions.validateBag` / `validateSetupState` to consult `GameData.activeJinxes` for
  jinxes whose text is "Only 1 jinxed character can be in play" and emit an issue:
  `"Heretic and Baron are jinxed — only 1 of them can be in play"`. Advisory only (the setup guard
  is already overridable, `GameShell.kt:584-589`).

### UI

- **Persistent banner** while a functioning Heretic is in play, modelled on the Mastermind banner
  (`GameShell.kt:520-537`):
  **"HERETIC IN PLAY — whoever wins, loses."** Dimmed/struck-through with
  "(Heretic is poisoned — result NOT reversed)" when `isImpaired` is true.
- **Win dialog** (`GameExtras.kt:236-265`): show both the raw and reversed result so the ST can see
  the reasoning, e.g.
  - line 1: "Every Demon is dead — good would win."
  - line 2 (gold): "**Heretic reverses it — EVIL WINS.**"
  - button: "Declare evil victory".
- **Manual declare** (`GameShell.kt:258-265`): when a functioning Heretic is in play, the two menu
  items should be relabelled to make the reversal explicit, e.g. "Declare good victory (Heretic:
  evil actually wins)". Simpler and safer: route both menu items through the same `applyHeretic`
  helper and confirm with "The Heretic reverses this — declare EVIL WINS to the table?".
- **Reveal sheet** (`GameExtras.kt:286-290`): under "EVIL WINS", add a subtitle line
  "(the Heretic reversed a good win)" so the table sees why.
- **Day-start briefing:** "Heretic is in play — the evil team can win at any time by executing the
  Demon; the good team can only win on the final day."

### Expiry / tokens / information / visibility

- No tokens. No expiry. Nothing is shown to any player at any time. The Heretic learns nothing and
  is shown nothing.

### Day-time inputs

- None required. (The Heretic frequently *claims* publicly — that is just a note the ST may record
  on the seat, which `SeatSheet.kt:366-372` already supports.)

### Data changes (`night_and_jinxes.json`)

- Line 56 (`baron`×`heretic`): replace `"The Baron might only add 1 Outsider, not 2."` with
  `"Only 1 jinxed character can be in play."`
- Line 76 (`pithag`×`heretic`): replace `"A Pit-Hag can not create a Heretic."` with
  `"Only 1 jinxed character can be in play."`
- Line 81 (`lleech`×`heretic`): replace `"If the Lleech has poisoned the Heretic, and the Lleech
  dies, the Heretic remains poisoned."` with `"Only 1 jinxed character can be in play."`
- Add a machine-readable flag to jinx entries so setup validation can act on them, e.g.
  `"kind": "exclusive"` for the "only 1 jinxed character" family.

### Interactions to handle explicitly

- **Saint** — `WinCheck.kt:51-68` returns `goodWins = false`; a Heretic flips it to a **good** win.
  Worth an explicit test: executing the Saint in a Heretic game wins the game for good.
- **Mastermind day** — reversed the same way.
- **Vortox / Leviathan / other alternate endings** — whatever `WinCheck` (or the ST manually)
  concludes is reversed.
- **Politician** — the Politician changes alignment and wins with the other team; combined with a
  Heretic this needs an explicit ST ruling. Surface a caution rather than guessing.
- **Spy / Widow / Baron / Godfather / Lleech / Pit-Hag** — jinxed out of play (see above).
- **Boffin** — the Demon cannot be given the Heretic ability.
- **Poisoner / No Dashii / Sweetheart / Puzzlemaster / Fortune Teller-red-herring-style poison** —
  any of these on the Heretic at the moment the game ends suppresses the reversal;
  `StatusEffects.isImpaired` already detects reminder-based and No Dashii poison.

## Tests to add

1. `WinCheckTest`: *Given* a game with a Heretic and all Demons dead, *then* `check` returns
   `goodWins = false` with a reason mentioning the Heretic. Fails today (returns `goodWins = true`).
2. `WinCheckTest`: *Given* a Heretic and 2 alive players including the Demon, *then* `check`
   returns `goodWins = true`. Fails today.
3. `WinCheckTest`: *Given* a Heretic and an executed (unimpaired) Saint, *then* `check` returns
   `goodWins = true` (good wins because the Saint's loss is reversed). Fails today.
4. `WinCheckTest`: *Given* a **poisoned** Heretic (a `poisoner:Poisoned` reminder on the seat) and
   all Demons dead, *then* `check` returns `goodWins = true` **and** a caution saying the Heretic is
   poisoned and the result is not reversed.
5. `WinCheckTest`: *Given* a **dead** Heretic and all Demons dead, *then* the result is still
   reversed (`goodWins = false`) — "even if you are dead".
6. `WinCheckTest`: *Given* `mastermindDayActive` with a Heretic and an executed evil player, *then*
   the reversal is applied to the Mastermind-day advisory.
7. `GameDataTest`: *Given* `listOf("baron","heretic")`, *then* the jinx reason equals
   "Only 1 jinxed character can be in play." Fails today (stale text). Repeat for `pithag` and
   `lleech`.
8. `GameActionsTest`: *Given* a bag containing both `heretic` and `baron`, *then* `validateBag`
   reports a jinx conflict issue. Fails today (validation ignores jinxes).
