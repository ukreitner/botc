# Mastermind (mastermind) — Bad Moon Rising Minion

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Mastermind>

Current ability text (matches `characters.json`):

> "If the Demon dies by execution (ending the game), play for 1 more day. If a player is
> then executed, their team loses."

How to Run (quoted):

> 1. "When the Demon is executed and dies (and the game would end), continue playing
>    instead"
> 2. "Add a shroud normally but do not announce the Demon's death"
> 3. "The next day, if a good player is executed, end the game declaring evil wins"
> 4. "If an evil player or no player is executed, end the game declaring good wins"

Key mechanics (quoted):

- "The dead Demon loses their ability and cannot attack."
- "Other characters' abilities function normally during the **extra night and day**."
  → there *is* an intervening night; the Undertaker, Ravenkeeper, Professor, Assassin,
  Godfather, Tinker, Moonchild etc. all still run.
- "If only two players remain alive after the Demon dies, the game still continues for
  another day (**overriding the normal two-player end condition**)."
- "The ability only triggers if the Demon dies **by execution** specifically."
- "**The Mastermind must be alive** for their ability to function."
- "Demon executed twice (like Zombuul): ability triggers only on the **second** death."
- Exactly **one** extra day. If that day ends with no execution, good wins.

Jinxes (on the wiki; **none of them are in the app's data**):

- **Al-Hadikhia × Mastermind** — if the Al-Hadikhia dies by execution with the
  Mastermind alive, the Al-Hadikhia chooses 3 good players; if all choose to live, evil
  wins, otherwise good wins.
- **Alchemist × Mastermind** — an Alchemist-Mastermind has no ability; the Mastermind is
  not in play.
- **Lleech × Mastermind** — if the Mastermind is alive and the Lleech's host dies by
  execution, the Lleech lives but loses their ability.
- **Vigormortis × Mastermind** — the Mastermind keeps their ability if the Vigormortis
  dies.

Interaction the app must get right: **the Scarlet Woman takes precedence.** If the Demon
is executed with 5+ alive and a living Scarlet Woman, the Scarlet Woman becomes the
Demon, the game does *not* end, and the Mastermind's ability never triggers.

## What the app does today

Data:
- `characters.json:646-656` — ability text matches. `reminders: []` (correct — the
  Mastermind has no reminder tokens).
- `night_and_jinxes.json` — **no** night-order entry (correct, the Mastermind never
  wakes) and **no** jinx entries (four are missing, see above).
- `night_guide.json` — **no entry for `mastermind`.** The character's entire procedure
  has no how-to-run text anywhere in the app.

Engine:
- `GameState.kt:107-111` — `val mastermindDayActive: Boolean = false`, documented as
  "True while the Mastermind's extra day is being played out after the Demon died by
  execution".
- `WinCheck.kt:28-49` — when the flag is set:
  - finds `demonExecIndex` = the last `DeathRecord` with `cause == EXECUTION` whose
    character (snapshotted via `characterIdAtDeath`) is a Demon;
  - finds the last non-resurrected `EXECUTION` death *after* that index;
  - if found, returns `Advisory(goodWins = executedEvil, reason = "Mastermind day: X was
    executed — their team (…) loses.")`;
  - otherwise `return null`, suppressing every other advisory (including the
    two-players-left one, which is exactly the override the rules want).
- `WinCheck.kt:70-86` — when every Demon is dead, adds the caution
  `"Mastermind: if the Demon died by execution, play one more day first."` whenever
  `"mastermind" in inPlayIds`.
- `StatusEffectsTest.kt:105-113` covers the resolution path. **The resolution logic
  works.**

UI:
- `GameShell.kt:506-519` — the `WinAdvisoryDialog` fires whenever `WinCheck.check`
  returns non-null; `onMastermindDay` sets `mastermindDayActive = true`.
- `GameExtras.kt:236-265` — the dialog renders "Play the Mastermind day" **only when**
  `advisory.cautions.any { "Mastermind" in it }`.
- `GameShell.kt:520-537` — a persistent overlay banner "MASTERMIND DAY — whoever is
  executed, their team loses", drawn as a bare `Box` outside the `Scaffold` at
  `padding(top = 100.dp)`.

Storyteller's actual experience: execute the Demon → the "Is the game over?" dialog
appears with a "Mastermind" caution and a "Play the Mastermind day" button → tap it →
a banner appears and the game continues → on the extra day, execute someone → the
dialog reappears with the correct winner. If nobody is executed, nothing happens and
the ST must declare the winner from the menu.

**Verified: `mastermindDayActive` exists and the extra-day resolution works.** The
defects below are around the *entry* conditions, the *end* condition, and the missing
procedure text.

## Defects and gaps

1. **P0 · The extra day is offered when the Demon did not die by execution.**
   Rules: "only triggers if the Demon dies by execution specifically." App:
   `WinCheck.kt:75-77` adds the Mastermind caution whenever `"mastermind" in inPlayIds`,
   with no inspection of `state.deaths`, and `GameExtras.kt:258` turns that caution into
   a button. Repro: Slayer shoots the Imp on day 2 → "Is the game over?" → "Play the
   Mastermind day" is offered → tapping it grants evil a day they are not entitled to.
   Same for an Imp star-pass gone wrong, an Assassin killing the Demon, a
   Storyteller-killed Demon, and a Demon who died at night.

2. **P0 · The extra day is offered when the Mastermind is dead.**
   Rules: "The Mastermind must be alive for their ability to function." App:
   `inPlayIds` (`WinCheck.kt:24`) is built from `players.mapNotNull { it.characterId }`
   with **no aliveness filter**. Repro: execute the Mastermind on day 2, execute the
   Imp on day 3 → the extra day is still offered.

3. **P1 · The extra day never ends on its own.**
   Rules: exactly **one** more day; "if an evil player or no player is executed … good
   wins." App: `mastermindDayActive` records no day number, and `WinCheck.kt:35-46`
   only resolves when an execution is found. With no execution the branch returns null
   forever and the game silently continues into day N+2, N+3… The ST must notice and
   use "Declare good victory" from the menu.
   Repro: execute the Demon on day 2 → play the Mastermind day → no execution on day 3
   → advance to night 4 → nothing tells the ST that good already won at dusk of day 3.

4. **P1 · No procedure text at all.** `night_guide.json` has no `mastermind` entry, and
   the Mastermind never appears in the night order, so nothing in the app ever tells the
   ST: shroud the Demon normally, say nothing about the game ending, run a full extra
   **night** (with all other abilities working and the dead Demon unable to attack), then
   the extra day, then declare.

5. **P1 · The Scarlet Woman takes precedence and the app presents both cautions as
   equals.** `WinCheck.kt:70-86` lists "Scarlet Woman: with 5+ players alive she becomes
   the Demon instead." and the Mastermind caution side by side. If the Scarlet Woman is
   alive with 5+ players the game does not end at all, so the Mastermind question never
   arises — the app should say so rather than offering the button.

6. **P2 · The banner appears one phase too early and is inaccurate during the night.**
   The flag is set at the moment of the Demon's execution, i.e. during day N. The banner
   then reads "MASTERMIND DAY — whoever is executed, their team loses" through the rest
   of day N and all of night N+1, when the statement is not yet true. It should read
   "Mastermind: the Demon is dead — play one more night and day" until day N+1 begins.

7. **P2 · The banner is drawn outside the `Scaffold`** (`GameShell.kt:521-536`) with a
   hard-coded `padding(top = 100.dp)` and no dismiss. On a phone (the user's actual
   device is an iPhone PWA) it will sit over the top of whichever tab is open.

8. **P2 · `mastermindDayActive` is never cleared.** There is no "cancel the Mastermind
   day" action; the only recovery is the undo stack, and the flag survives into the
   reveal.

9. **P2 · The four Mastermind jinxes are missing from `night_and_jinxes.json`,** so
   `ActiveJinxesDialog` (`GameExtras.kt:200-232`) and the seat sheet's jinx list show
   nothing for Al-Hadikhia, Alchemist, Lleech or Vigormortis pairings. The
   Vigormortis one is a genuine rules change ("the Mastermind keeps their ability if the
   Vigormortis dies") that would otherwise be missed.

10. **P2 · The Zombuul double-execution case is not handled.**
    `WinCheck.kt:30-34` takes the *last* Demon `EXECUTION` death record. Whether the
    Zombuul's first (registers-as-dead) execution produces a `DeathRecord` in this app is
    up to how the ST records it, so the "only the second death triggers" rule is not
    reliably enforced.

11. **P3 · The reason string is computed from live state, not a snapshot.**
    `WinCheck.kt:39-40` reads `state.player(executed.playerId)?.isEvil(lookup)` rather
    than the `DeathRecord`'s snapshot, so a post-death character change (Pit-Hag, an
    undone star pass) would rewrite who won.

## Proposed behaviour (spec)

### State

Replace the boolean with an explicit record:

```kotlin
// GameState — replaces `mastermindDayActive: Boolean`
@Serializable data class MastermindExtra(
    val demonDeathIndex: Int,   // index into deaths: the Demon's execution
    val extraDay: Int,          // the cycle number of the one extra DAY
)
val mastermindExtra: MastermindExtra? = null
```

Keep `mastermindDayActive` as a deprecated computed alias for save compatibility
(`get() = mastermindExtra != null`).

### Entry conditions (the P0 fixes)

`WinCheck.check` should only produce the Mastermind caution — and `WinAdvisoryDialog`
should only render the button — when **all** of:

```kotlin
val mastermindAlive = players.any { it.characterId == "mastermind" && it.alive }
val lastDemonDeath  = state.deaths.lastOrNull { d ->
    !d.resurrected &&
    (d.characterIdAtDeath ?: state.player(d.playerId)?.characterId)?.let(lookup)?.team == Team.DEMON
}
val diedByExecution = lastDemonDeath?.cause == DeathCause.EXECUTION
val scarletWomanCatches = players.any { it.characterId == "scarletwoman" && it.alive } && alive.size >= 5
```

`mastermindAlive && diedByExecution && !scarletWomanCatches`.

When `mastermindAlive && !diedByExecution`, replace the caution with the *informative*
line: "Mastermind is in play but the Demon did not die by execution — no extra day."
When `!mastermindAlive`, say "The Mastermind is dead — no extra day."
When `scarletWomanCatches`, suppress the whole advisory and say
"The Scarlet Woman becomes the Demon — the game does not end."

### Starting the extra day

`onMastermindDay` sets

```kotlin
mastermindExtra = MastermindExtra(demonDeathIndex = …, extraDay = state.cycle + 1)
```

(the Demon is executed during day N, so the extra day is day N+1).

### The extra night

Nothing special is needed mechanically — every other ability runs. The app should show
a **night-sheet header**: "Mastermind: the Demon is dead. Run this night normally — the
Demon does **not** attack. Tomorrow is the last day."
Concretely: the dead Demon's step is already rendered with "All holders are dead —
usually skip" (`NightScreen.kt:751-757`); add the explicit "the Demon cannot attack"
line when `mastermindExtra != null`.

### Resolving the extra day (the P1-3 fix)

Two exits, both automatic:

1. **Execution during `extraDay`** — unchanged from today's logic, but keyed to
   `d.day == mastermindExtra.extraDay` rather than "any execution after the demon's",
   and using the `DeathRecord` snapshot for alignment:
   `Advisory(goodWins = executedWasEvil, reason = "Mastermind day: X was executed — the
   <good|evil> team loses.")`
   Note: an execution that **fails** (Devil's Advocate, Pacifist, Fool, Sailor) is still
   "a player is then executed" per the ability text → their team still loses. Pin this
   with the ST as a ruling; the wiki's own example ("evil player executed but survives
   via protection → good wins") supports counting the *execution*, not the death.
   → so key off the **execution event**, not the `DeathRecord`. This requires the
   `executedToday` field proposed in `devilsadvocate.md`.
2. **Dusk of `extraDay` with no execution** — when `advancePhase` is called with
   `phase == DAY && cycle == mastermindExtra.extraDay && executedToday == null`, return
   `Advisory(goodWins = true, reason = "Mastermind day ended with no execution — good
   wins.")` and block the advance behind that dialog.

Both exits clear `mastermindExtra` when the game is declared.

### Banner (P2-6/7)

Move the banner into the `Scaffold` body as a slim top bar inside
`GameShell.kt`'s `Box`, and make the text phase-aware:

- `cycle < extraDay`: "MASTERMIND — the Demon is dead. One more night and day."
- `cycle == extraDay && phase == DAY`: "MASTERMIND DAY — whoever is executed, their
  team loses. No execution = good wins."
- Add a small "×" that clears `mastermindExtra` (with a confirm), for the case where the
  ST tapped the button by mistake.

### Structured summary

- **when:** never at night; the Mastermind has no night action.
- **wake condition:** n/a.
- **targets:** none.
- **immediate effects:** on the Demon's execution death, if the Mastermind is alive:
  the game does **not** end; `mastermindExtra` is set. The Demon is shrouded normally.
- **deferred effects:** one extra night (all abilities normal, no Demon attack), then
  one extra day. First execution of that day ends the game against that player's team;
  no execution ends it in good's favour.
- **expiry:** `mastermindExtra` is cleared when the game is declared.
- **information:** none.
- **visibility:** nothing is shown to anyone; the players are *not* told the Demon died.
- **day-time inputs:** none beyond the normal nomination/execution flow.
- **interactions:** Scarlet Woman takes precedence; Zombuul triggers only on its second
  death; a failed execution on the extra day still counts as an execution; the
  two-players-left end condition is overridden; Vigormortis/Lleech/Alchemist/Al-Hadikhia
  jinxes apply.

### Data changes

- `night_and_jinxes.json` jinx list — add the four Mastermind jinxes (verify exact
  wording against the wiki before committing).
- `night_guide.json` — add a `mastermind` entry. Since the Mastermind has no night
  order position, this needs a home: either extend `NightGuide` to serve
  non-night-order characters (looked up from the seat sheet and the Script tab), or add
  the text to the Script/Reference screen. Proposed text:
  "The Mastermind never wakes. If the **Demon dies by execution** while the Mastermind
  is **alive**, do not end the game: shroud the Demon as normal, say nothing, and play
  one more night and one more day. Everything works normally that night except that the
  dead Demon cannot attack. On that final day: if a **good** player is executed, evil
  wins; if an **evil** player or **no one** is executed, good wins. A Scarlet Woman who
  catches the Demon takes precedence — then the game never ended, so this never applies."
- No `characters.json` changes.

## Tests to add

1. `mastermind day is not offered when the demon died at night`
   Given the Imp killed by an Assassin at night, a living Mastermind. When
   `WinCheck.check`. Then the advisory has no Mastermind caution. **Fails today.**
2. `mastermind day is not offered when the mastermind is dead`
   Given the Mastermind executed on day 2 and the Imp executed on day 3. Then the
   advisory has no Mastermind caution. **Fails today.**
3. `mastermind day is not offered when a scarlet woman catches the demon`
   Given 6 alive, a living Scarlet Woman, the Imp executed. Then `WinCheck.check`
   returns the Scarlet Woman path, not a good-wins advisory with a Mastermind button.
4. `extra day with no execution gives good the win`
   Given `mastermindExtra.extraDay == 3` and no execution on day 3. When advancing to
   night 4. Then an advisory with `goodWins = true` and the reason "no execution" is
   returned. **Fails today** (`WinCheck.kt:48` returns null).
5. `extra day resolves against the executed player's team`
   Given a good player executed on the extra day. Then `goodWins == false`. Given an
   evil player executed. Then `goodWins == true`. *(Covered in spirit by
   `StatusEffectsTest.kt:105`; extend to both directions and to a turned-evil Goon.)*
6. `a failed execution on the extra day still resolves the game`
   Given the evil player on the block holds `("devilsadvocate","Survives execution")`
   and is executed. Then `goodWins == true` (their team loses) even though no
   `DeathRecord` was created. **Fails today.**
7. `the extra day overrides the two-players-left ending`
   Given 2 alive during `mastermindExtra`. Then `WinCheck.check` does not return the
   "only 2 players live" evil-wins advisory. *(Passes today by the early return — pin
   it.)*
8. `resolution uses the death snapshot, not live state`
   Given a good player executed on the extra day whose seat is later re-assigned to an
   evil character. Then the advisory still says the good team loses.
9. `mastermind jinxes are present`
   Given a script with `mastermind` and `vigormortis`. Then
   `gameData.activeJinxes(inPlay)` returns the Vigormortis jinx. **Fails today** (data
   missing).
