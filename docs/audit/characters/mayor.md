# Mayor (mayor) — Trouble Brewing Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Mayor>

Current ability text (matches `characters.json`):

> "If only 3 players live & no execution occurs, your team wins. If you die at night, another player might die instead."

How to run (wiki):

- *"During the night, if the Mayor would die, you choose if the Mayor actually dies, or if the Mayor remains alive and another character **dies** instead."*
- *"At dusk, if exactly three players are alive and no player was executed today, declare that the game ends and good wins."*

Examples (wiki):

- *"The Imp attacks the Mayor. The Storyteller chooses that the Ravenkeeper dies instead."*
- *"There are three players alive. There are no nominations for execution today. Good wins."*
- *"There are five players alive, including two Travellers. Both Travellers are exiled, and the vote is tied between the remaining players…good wins."* — i.e. **Travellers count towards the "3 players live" total**, and **exiles are not executions**.

Clarifications that matter for the app:

- The Mayor must be **alive** for the 3-alive win to apply, and their ability must be working — a **drunk or poisoned Mayor neither wins at 3 nor bounces a night death**.
- Fabled do not count as players.
- A **tie** prevents execution, so a tie on the final day is a Mayor win.
- The bounce applies to *any* night death ("if you die at night"), not only the Demon's kill.
- Protection beats the bounce: from the Monk page, *"Monk protects Mayor; Imp attacks Mayor → Mayor's ability doesn't trigger; nobody dies."*
- The wiki summary asserts that a redirected death *"cannot go to protected players or the Demon"*. I could not find that phrased as a hard rule in the character text; treat it as **strong storyteller guidance** (warn, do not hard-block) and re-verify before shipping a hard constraint.

Jinxes:

- **Mayor & Leviathan** — *"If Leviathan is in play and no execution occurs on day 5, good wins."* (present in the app data).
- **Mayor & Riot** — *"Mayor may stop the riot; good wins if only one player remains when stopped, otherwise evil wins."* (**absent** from the app data).

## What the app does today

**The Mayor exists in the app as a name and an ability string, and nothing else.** A full-repo grep for `mayor` outside tests returns exactly three production hits:

- `engine/src/main/resources/botc/data/characters.json:71-…` — the character entry. `firstNightReminder` and `otherNightReminder` are empty, `reminders: []`, `setup: false`. Correct: the Mayor never wakes.
- `engine/src/main/resources/botc/data/night_and_jinxes.json:~190` — the single `leviathan`/`mayor` jinx. Not present in either night-order list. Correct.
- `engine/src/main/kotlin/com/clocktower/engine/WinCheck.kt:88-98` — the **only** logic:

```kotlin
if (alive.size <= 2 && aliveDemons.isNotEmpty()) {
    val cautions = mutableListOf<String>()
    if ("mayor" in inPlayIds) {
        cautions += "Mayor: at 3 alive with no execution, good wins instead — check before it drops to 2."
    }
    return Advisory(goodWins = false, reason = "Only ${alive.size} players live and the Demon is among them — evil wins.", …)
}
```

There is no `night_guide.json` entry (confirmed missing), no `InfoCalc` support (not in the `supports` set at `InfoCalc.kt:29-36`), no `deathNotes` branch (`StatusEffects.kt:94-128` has no `"mayor"` case), no dusk logic, and no redirect anywhere.

Storyteller's actual experience:

- **Night, Demon picks the Mayor.** `NightScreen.kt:534-638` `DemonKillPanel` shows the target chips, prints `deathNotes` (which say nothing about the Mayor), and offers exactly two actions: **"<Mayor> dies"** and **"No kill"** (`NightScreen.kt:624-635`). *There is no way in the kill panel to make a different player die.* To bounce, the storyteller must abandon the panel, switch to the Grimoire tab, open the substitute's seat sheet and press **"Died at night"** (`SeatSheet.kt:271-273`) — and remember not to press it on the Mayor.
- **Day with 3 alive.** `DayScreen.kt:85-92` shows "3 alive · 2 votes to execute". Nothing mentions the Mayor.
- **Dusk with 3 alive and no execution.** `GameShell.kt:141-146` only guards "someone is on the block and hasn't died"; `GameShell.kt:592-617` offers "Execute & begin night" / "No execution" / "Cancel". Pressing **No execution** — the exact Mayor trigger — silently advances to night 4.
- The Mayor caution then appears one death later, when the board is already at 2 alive and the game is over the wrong way.

`WinCheck.kt:19` also computes `alive` from `state.players.filter { !it.isTraveller }`, so Travellers are excluded from the count — the opposite of the Mayor rule, which counts them.

## Defects and gaps

1. **P0 · The storyteller is never told the Mayor win is live, and the app helps them walk past it.**
   Rules: at dusk with exactly 3 alive and no execution, good wins. App: the dusk transition (`GameShell.kt:126-168`, dialog at `GameShell.kt:592-617`) has a "No execution" button and no Mayor check. `WinCheck.check` (`WinCheck.kt:88-98`) only mentions the Mayor at `alive.size <= 2` — after the window has closed.
   Repro: 3 alive (Mayor, Imp, Poisoner), day 3, no nomination passes → tap **Dusk** → tap **No execution** → night 4 begins with no prompt.

2. **P0 · The "3 players live" count excludes Travellers.**
   Rules: Travellers count as players (wiki example: *"five players alive, including two Travellers"*). App: `WinCheck.kt:19` filters `!it.isTraveller` before counting `alive`, so a board of Mayor + Imp + Traveller reads as 2 alive and the app declares **evil wins**.

3. **P0 · No way to redirect a night kill from the kill panel.**
   Rules: *"you choose if the Mayor actually dies, or if the Mayor remains alive and another character dies instead."* App: `NightScreen.kt:624-635` offers only "dies" / "No kill". There is no third path. The storyteller must leave the night sheet, kill someone else from `SeatSheet.kt:271`, and mentally match it to the Demon's choice — with the very real risk of double-killing or of the night log recording the wrong cause.

4. **P1 · Nothing flags the Mayor as a bounce candidate when the Demon targets them.**
   `StatusEffects.deathNotes` (`StatusEffects.kt:94-128`) has cases for Ravenkeeper, Sage, Farmer, Moonchild, Sweetheart, Barber, Poppy Grower, King, Zombuul, Grandmother — but not the Mayor. So the panel prints nothing at all when the Mayor is chosen and a storyteller who has forgotten the Mayor is in play simply kills them.

5. **P1 · The drunk/poisoned Mayor is not distinguished, in either half of the ability.**
   A poisoned Mayor cannot bounce and cannot win at 3. `WinCheck` never calls `StatusEffects.isImpaired` on the Mayor seat, and there is no bounce path to gate.

6. **P1 · "No execution occurred today" is never derived, though the data is there.**
   `state.nominations` records every nomination with its `NominationResult`, and `state.deaths` records `DeathCause.EXECUTION` with `day`. Deriving *"no execution occurred on day N"* is one predicate, and nothing computes it. Note the subtlety the app must get right: a `DeathCause.EXILE` on a Traveller is **not** an execution, and `GameActions.aboutToDie` returning `null` (tie or nothing passed) is exactly the "no execution" case.

7. **P1 · No day-start briefing when the Mayor win becomes reachable.**
   `DayScreen.kt:85-124` shows the alive count and the block banner only. At 3 alive with a sober living Mayor the storyteller should be told, before the first nomination, that a tie or a quiet day ends the game.

8. **P2 · The Leviathan/Mayor jinx is data-only.**
   `night_and_jinxes.json` carries it and `SeatSheet.kt:225-234` / `ActiveJinxesDialog` display it, but nothing tracks "day 5, no execution" the way it would need to be tracked to actually fire.

9. **P2 · The Mayor/Riot jinx is missing from the data.**

10. **P2 · No `night_guide.json` entry for the Mayor.**
    Every other in-scope character has one. Because the Mayor never wakes there is no night step to hang it on — which is precisely why the guidance needs a home elsewhere (day briefing + kill panel), not why it should be absent.

11. **P3 · No reminder token to record a bounce.**
    The official Mayor token has no reminders, so this is an app-level nicety, but after a bounce the grimoire has no trace of *why* the Ravenkeeper died, which matters when the storyteller reconstructs the game at reveal (`RevealFlow.kt`).

## Proposed behaviour (spec)

The Mayor is a **passive** character with two storyteller-facing triggers. Neither belongs on the night sheet; both belong to the generic engine.

### A. Night-death redirect (bounce)

- **when:** any time a death with `atNight = true` is about to be recorded against a seat holding `mayor`. This covers `DeathCause.DEMON` and `DeathCause.OTHER_NIGHT_DEATH` — **not** execution, exile, or a day-time storyteller death.
- **gate:** the Mayor is alive and `!StatusEffects.isImpaired(mayor)`. If impaired, no bounce is offered and a note explains why.
- **precedence:** an **effective** protection on the Mayor (Monk `Safe`, Soldier, Innkeeper `Protected`) wins outright — per the Monk wiki example, *nobody dies* and the bounce is not offered. Resolve protection first, then the bounce.
- **targets:** 1, from all other players. Sort: alive non-Demon first; then alive Demon; then dead (disabled). Annotate each chip with any protection, and annotate the Demon chip with *"redirecting onto the Demon is contentious — prefer another seat"*. Do **not** hard-block (see the sources note above).
- **immediate effects:** the Mayor stays alive with no death record; the chosen player is killed with the **same** cause and `atNight` flag as the original attack; a `PlacedReminder("mayor", "Died instead")` is placed on the substitute for the night.
- **the third option:** the storyteller may also choose that the **Mayor dies normally**. Three buttons, not two.
- **expiry:** `mayor/Died instead` → `EXPIRES_AT_DAWN`.
- **visibility:** nothing is shown to anyone. The dawn briefing announces the substitute's death **without** explaining it (wiki: *"announce the redirected player's death without explaining how they died"*).
- **log:** `NightRecord(cycle, "mayor", [mayorId], [substituteId], "Mayor bounce: <substitute> died instead")`.

### B. Dusk win check

New engine entry point, called by the phase button on DAY→NIGHT **before** `advancePhase`:

```
WinCheck.duskCheck(state, lookup): Advisory?
```

Fires when **all** of:

- `state.players.count { it.alive } == 3` — **all** seats, Travellers included, Fabled excluded (Fabled are not seats in this model, so no filter needed);
- no `DeathRecord` with `cause == EXECUTION && day == state.cycle && !resurrected`;
- a seat holds `mayor`, is `alive`, and `!StatusEffects.isImpaired(state, lookup, mayorSeat)`.

Returns `Advisory(goodWins = true, reason = "Mayor: only 3 players live and nobody was executed today — good wins.", cautions = [...])` with cautions for anything that could overturn it (Mayor is the Drunk / has `No ability`; a Vortox-style script; an Atheist game).

`GameShell.requestPhaseAdvance` (`GameShell.kt:126-168`) must consult it in the DAY branch, before the on-the-block guard, and present the existing `WinAdvisoryDialog` (`GameExtras.kt:237`) with "Declare good wins" / "Not yet — begin the night".

Also fix `WinCheck.check`: count Travellers for the alive-count conditions (the Scarlet Woman's *"Travellers don't count"* stays a Scarlet-Woman-specific rule, applied where the Scarlet Woman is evaluated, not globally at `WinCheck.kt:19`).

### C. Day-start briefing

`DayScreen` gains a briefing card above "New nomination", fed by a new `DayBriefing.build(state, lookup): List<Note>`:

- **Mayor note, shown when `alivePlayers.size == 3` and the Mayor is alive and sober:**
  **"MAYOR WIN IS LIVE — if nobody is executed at dusk today, good wins."**
- A second line when a tie is currently standing: **"Current tally is a tie — as it stands, good wins at dusk."**
- If the Mayor is alive at 3 but **impaired**: **"3 alive, but the Mayor is poisoned — their win condition does not apply today. Do not announce it."**

The same briefing surface is where every other character's day-start note lands (see the Fortune Teller / Empath specs), so this is one shared component.

### UI text

- Kill panel, Mayor targeted: header **"<Mayor> is the MAYOR — you may send this death somewhere else."**
  Buttons: **"<Mayor> dies"** · **"Someone else dies instead…"** (opens the substitute chips) · **"No kill"**.
- After choosing: **"<substitute> dies instead. Announce their death at dawn without explaining it."**
- Impaired Mayor: **"⚠ The Mayor is POISONED — no redirect. They die normally."**
- Dusk dialog: **"Only 3 players live and nobody was executed. The Mayor wins the game for good."**

### Data changes

- `night_and_jinxes.json` → add `{"id1":"mayor","id2":"riot","reason":"The Mayor may choose to stop the Riot; good wins if only one player remains when stopped, otherwise evil wins."}`.
- `characters.json` `mayor.reminders` → add `"Died instead"` (app-level bookkeeping token; note in the data file that this is not an official token if the project cares about fidelity).
- `night_guide.json` → add a `mayor` entry with **no** `first`/`other` night block but a new optional `passive` section carrying the day-briefing prose, or keep the prose in code. Prefer the data file so all storyteller prose lives in one place.

## Tests to add

1. `mayor dusk win at three alive with no execution`
   Given 3 alive seats — `mayor` (sober), `imp`, `poisoner` — on day 3 with no execution recorded for day 3.
   When `WinCheck.duskCheck` runs.
   Then it returns `goodWins = true` naming the Mayor.

2. `mayor dusk win counts travellers`
   Given `mayor`, `imp`, and one alive **Traveller** (3 alive total), no execution today.
   Then `duskCheck` returns a Mayor win. And given a 4th alive Traveller, it returns `null`.

3. `mayor dusk win is suppressed by an execution`
   Given the same 3-alive board plus `DeathRecord(cause = EXECUTION, day = 3)`.
   Then `duskCheck` returns `null`. (Add the mirror case: a `DeathCause.EXILE` on day 3 does **not** suppress it.)

4. `poisoned mayor does not win at three`
   Given the 3-alive board with `PlacedReminder("poisoner","Poisoned")` on the Mayor.
   Then `duskCheck` returns `null` (or an advisory explicitly saying the ability does not apply).

5. `dead mayor does not win at three`
   Given 3 alive players none of whom is the Mayor, while a dead Mayor is in the grimoire.
   Then `duskCheck` returns `null`.

6. `mayor bounce moves a night death`
   Given `mayor` at seat 2 and `ravenkeeper` at seat 5, night 3.
   When the demon kill is resolved against seat 2 with a bounce onto seat 5.
   Then seat 2 is alive with no `DeathRecord`, seat 5 is dead with `cause = DEMON, atNight = true`, and seat 5 carries `mayor/Died instead`.

7. `mayor bounce token expires at dawn`
   Continuing (6), `advancePhase` clears `mayor/Died instead`.

8. `protected mayor does not bounce`
   Given `PlacedReminder("monk","Safe")` on the Mayor and a sober Monk.
   When the Demon targets the Mayor.
   Then the resolver reports "nobody dies", offers no bounce candidates, and no `DeathRecord` exists.

9. `poisoned mayor does not bounce`
   Given a poisoned Mayor targeted at night. Then no bounce candidates are offered and the Mayor dies.

10. `mayor bounce is not offered for an execution`
    Given the Mayor is executed on day 2. Then the bounce path is not offered and the Mayor dies.

11. `wincheck evil-win advisory counts travellers`
    Given 2 alive non-Travellers plus 1 alive Traveller and a living Demon.
    Then `WinCheck.check` does **not** declare evil wins (3 players live).
