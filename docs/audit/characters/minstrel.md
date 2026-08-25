# Minstrel (minstrel) — Bad Moon Rising Townsfolk

## Official rules (sources)

Sources:
- https://wiki.bloodontheclocktower.com/Minstrel
- https://wiki.bloodontheclocktower.com/Legion (jinx)
- https://wiki.bloodontheclocktower.com/Glossary ("Drunk", "Dead", "Dusk")

**Current ability text (matches `characters.json`):**

> "When a Minion dies by execution, all other players (except Travellers) are drunk until dusk tomorrow."

**How to run (wiki, quoted):**

> "During daytime, when a Minion dies by execution, all other players except Travellers become
> drunk. Place the Minstrel's **'EVERYONE IS DRUNK'** reminder token in the center of the left side
> of the Grimoire. At dusk the following day, remove this reminder as all affected players become
> sober."

**Non-triggers (wiki):** the ability does **not** trigger if

- a Minion dies **at night**;
- a **dead** Minion is executed;
- a Minion is executed but **doesn't die** (Devil's Advocate, Pacifist does not apply to evil, Tinker-style saves);
- the **Minstrel is drunk or poisoned** when the Minion dies.

Add, from the Glossary: a **dead** Minstrel has no ability, so a Minion executed after the Minstrel
died does not trigger it either.

**Examples (wiki):**

- "The Pacifist dies on Day 1 (not a Minion, so no effect). Day 2: Judge executes Godfather. That
  night, everyone is drunk, preventing deaths. Day 3: A Minion protected by Devil's Advocate is
  executed and dies; everyone becomes drunk again."
- "Assassin is executed (everyone drunk). Next day, Godfather is executed (everyone drunk again).
  The Demon couldn't kill either night. Next day, evil Apprentice Mastermind is exiled (no effect,
  since Travellers don't trigger this)."
- "Assassin dies by execution (everyone drunk). Following day, Zombuul is executed and dies for the
  first time. Good wins because Zombuul is drunk and has no ability."

**Duration — this is the subtle part.** "Until dusk **tomorrow**": a Minion executed on day N makes
everyone drunk for the *rest of day N*, all of *night N+1*, and all of *day N+1*, ending at the dusk
that closes day N+1. **The effect survives one dusk and is removed at the second.** The third wiki
example is decisive: the Assassin is executed on day N and the Zombuul, executed on day N+1, is
*still drunk* and so loses its "first time you die, you live" ability.

**Scope of "all other players":**

- The **Minstrel is not drunk** ("all *other* players").
- **Travellers are excluded.**
- Everyone else, alive or dead, evil or good, including the Demon and the remaining Minions
  (examples 1 and 2 turn on exactly this: the drunk Demon cannot kill).

**Jinx (missing from the app's data):**

> **Legion:** "If Legion died by execution today, Legion keeps their ability, but the Minstrel might
> learn they are Legion."

**Uncertain:** whether the drunkenness ends early if the *Minstrel* dies while it is running. The
Glossary's "Dead" entry says "any persistent effects of their ability immediately end", which would
end it; the Minstrel page says only "at dusk the following day, remove this reminder". The only way
this comes up is the Minstrel being executed on the second day of their own effect (nobody can kill
them at night — everyone is drunk). **Recommendation: do not decide silently — prompt the ST.**

## What the app does today

- `characters.json` — ability text is the current wording ("dies by execution", not the older
  "is executed"). Reminder label `["Everyone is drunk"]` (community data says "Everyone drunk";
  cosmetic). **Works.**
- `night_and_jinxes.json` — correctly absent from both night orders (the Minstrel never wakes).
  **No jinx entry** for minstrel/legion (verified: 58 jinxes, zero mention minstrel).
- `night_guide.json` — **no entry at all** (`null`). Nothing anywhere in the app explains how to run
  this character.
- `StatusEffects.kt:110-112` — the only code that knows the Minstrel exists:
  ```kotlin
  if (character?.team == Team.MINION && seats.any { it.characterId == "minstrel" && it.alive }) {
      notes += "Minstrel: if executed, everyone (but Travellers) is drunk until dusk tomorrow."
  }
  ```
  A text hint in `deathNotes`, surfaced in the seat sheet (`SeatSheet.kt:241-250`) and the demon
  kill panel (`NightScreen.kt:588-590`).
- **Nothing else.** No global reminder, no drunkenness applied, no expiry, no day briefing.

Storyteller experience: you execute the Assassin, the app kills them, and that is it. If you happen
to open the Minion's seat sheet *before* executing, you get one grey sentence. You then have to
remember for the next 24 in-game hours that literally everyone's ability is broken — including
remembering that the Demon cannot kill tonight and that the Chambermaid/Gambler/Fortune Teller must
all be given false info. The app will cheerfully compute and display **true** info for all of them.

## Defects and gaps

1. **P0 · The drunkenness is never applied. Every ability in the game keeps working.**
   Rules: all other non-Traveller players are drunk. App: `isImpaired`
   (`StatusEffects.kt:36-46`) only inspects that player's *own* reminders, so nothing is impaired.
   Repro: execute a Minion, advance to night, open the Demon step — `DemonKillPanel` does **not**
   show the "! The Demon is drunk/poisoned — the attack fails" line (`NightScreen.kt:548-554`) and
   happily kills. Open the Fortune Teller step — `InfoCalc` returns true info with no impairment
   caveat, and the "False info to show instead" chips (`NightScreen.kt:903-930`) never appear.
   **This silently breaks every other character in the game for two phases.**

2. **P0 · No trigger at all on execution.**
   Rules: the effect fires the moment the Minion dies by execution. App: execution is
   `viewModel.kill(id, DeathCause.EXECUTION)` from `DayScreen.kt:111-114` / `:350-357` /
   `GameShell.kt:601`; nothing inspects the victim's team or the Minstrel's presence. The
   `deathNotes` hint is only visible if the ST manually opened that seat first — and the Day tab's
   Execute button never shows death notes at all.

3. **P0 · No expiry model can express "until dusk tomorrow".**
   Rules: survives one dusk, removed at the second. App: `EXPIRES_AT_DAWN` / `EXPIRES_AT_DUSK`
   (`GameActions.kt:218-242`) are flat sets of `(sourceId,label)` swept on a single transition
   (`:258-263`). There is no way to say "two dusks". Adding `("minstrel","Everyone is drunk")` to
   `EXPIRES_AT_DUSK` would be **wrong** — it would end the effect at the dusk that begins the very
   night it is supposed to cover.

4. **P1 · No global / grimoire-centre reminder concept.**
   Rules: one token in the centre of the grimoire. App: `PlacedReminder` only exists on a `Player`
   (`GameState.kt:6-11, 30`); `Character.remindersGlobal` (`Character.kt:46, 62`) is merged into
   `allReminders` and still placed on a seat. Putting "Everyone is drunk" on the Minstrel's own seat
   would make `isImpaired` return true for **the one player who must stay sober**.

5. **P1 · The Minstrel's own preconditions are unchecked.**
   Rules: no trigger if the Minstrel is dead, drunk or poisoned, if the executed Minion was already
   dead, or if the Minion was executed but survived. App: `StatusEffects.kt:110` checks only
   `it.alive` on the Minstrel — not `isImpaired`, not the victim's alive-ness, not whether the
   execution actually killed.

6. **P1 · "Executed but didn't die" is invisible to the engine.**
   The only record of an execution is a `DeathRecord` with `cause = EXECUTION`
   (`GameState.kt:77-90`). A Devil's-Advocate-saved Minion leaves no trace at all, so neither the
   Minstrel trigger nor the Undertaker nor the Zombuul's "if no-one died today" can be evaluated.
   (Cross-cutting — see the Pacifist file for the same finding.)

7. **P2 · No day-2 briefing.** Even with the token placed, the ST has to remember on the *following*
   day that everyone is still drunk. Nothing in `DayScreen.kt` says so.

8. **P2 · No night guide entry.** `night_guide.json` has no `minstrel` key, so the character has
   zero run-book text in the app, unlike 116 other entries.

9. **P2 · The Legion jinx is missing** from `night_and_jinxes.json`.

10. **P3 · `deathNotes` phrasing is stale/incomplete.** "Minstrel: if executed, everyone (but
    Travellers) is drunk" omits "except the Minstrel", omits that a dead-Minion execution doesn't
    count, and appears even when the Minstrel is poisoned.

## Proposed behaviour (spec)

The Minstrel has **no night step**; it is a day-time trigger plus a two-phase global status effect.

### Trigger

- **when:** the moment an execution kills a player, i.e. inside the shared `confirmDeath(...)`
  path with `cause == EXECUTION`.
- **condition (all must hold):**
  - the victim's `characterId` team is `MINION` (judge the true character; a **Recluse** executed
    while registering as a Minion **may** trigger it — Storyteller's choice, offer a toggle);
  - the victim was **alive** immediately before the execution;
  - the execution actually **killed** them (not a Devil's Advocate / Tinker-style survival);
  - a Minstrel is **in play, alive, and not impaired** (`!StatusEffects.isImpaired(minstrel)`);
  - the victim is **not a Traveller** (Travellers are exiled, not executed — belt and braces).
- **immediate effects:** set a global effect
  ```kotlin
  @Serializable
  data class GlobalEffect(
      val sourceId: String,          // "minstrel"
      val label: String,             // "Everyone is drunk"
      val sourcePlayerId: Long?,     // the Minstrel's seat, for the exemption + death check
      val startedOnDay: Int,         // state.cycle when it fired
      val expiresAfterDay: Int,      // startedOnDay + 1  → removed at THAT day's dusk
  )
  // on GameState:
  val globalEffects: List<GlobalEffect> = emptyList()
  ```
  Render it as a token in the grimoire centre (`GrimoireScreen`) and as a persistent banner on both
  the Day and Night tabs.
- **status effect:** extend `StatusEffects.isImpaired` (`StatusEffects.kt:36-46`):
  ```kotlin
  if (state.globalEffects.any { it.sourceId == "minstrel" } &&
      !player.isTraveller &&
      player.id != minstrelEffect.sourcePlayerId) return true
  ```
  This one change makes the Demon's attack fail in `DemonKillPanel` (`NightScreen.kt:548-554`),
  makes every `InfoCalc` result carry a DRUNK caveat and light up the "False info to show instead"
  chips (`NightScreen.kt:903-930`), and breaks the Zombuul/Fool/Sailor/Tea Lady standing abilities.
- **expiry:** at every DAY→NIGHT transition (`GameActions.kt:261`), drop
  `globalEffects.filter { state.cycle > it.expiresAfterDay }`. With `expiresAfterDay = startedOnDay
  + 1`, the effect survives the dusk of day N and is dropped at the dusk of day N+1 — exactly
  "until dusk tomorrow", and exactly the wiki's Zombuul example.
- **re-trigger:** a second Minion execution while the effect is running **replaces** it (refreshes
  `startedOnDay`/`expiresAfterDay`), matching "everyone becomes drunk again".
- **on the Minstrel's death:** prompt, do not decide —
  *"The Minstrel has died while their drunkenness is running. Glossary: a dead player's persistent
  effects end immediately. End it now, or let it run to dusk?"* with both buttons. Default
  highlighted: "End it now".
- **information / visibility:** nothing is shown to any player. The town learns only that a Minion
  was executed (which they saw) — the Minstrel's identity stays hidden.
- **day-time inputs:** none.
- **interactions/jinxes:**
  - **Legion** — add the jinx; when Legion is executed and dies, prompt: Legion keeps its ability,
    but the Minstrel *might* learn they are Legion (ST choice whether the effect fires).
  - **Travellers** are never drunk from this; the Apprentice/Mastermind exile case in the wiki's
    second example must not trigger it (`isExile` / `DeathCause.EXILE`).
  - **Recluse** executed and registering as a Minion — offer the toggle.
  - **Vortox** — while everyone is drunk, the Vortox's "all Townsfolk info is false" is moot but
    the caveat ordering must not double-count; the drunk caveat wins.
  - **Zombuul** — the wiki's third example is a direct test case.

### UI text

- Confirmation on the triggering execution: **"{name} was a Minion and died by execution — the
  Minstrel makes everyone else drunk until dusk tomorrow. Apply?"** with **Apply** / **Not this
  time (Minstrel is drunk, poisoned or dead)**.
- Persistent banner, day and night: **"EVERYONE IS DRUNK (Minstrel) — except {Minstrel name} and
  Travellers. Ends at dusk on day {expiresAfterDay}."**
- On the Demon's night step: **"! Everyone is drunk (Minstrel) — the Demon's attack fails."**
- Day {expiresAfterDay} briefing line: **"Still drunk from the Minstrel — this is the last day.
  Give false info to everyone but {Minstrel name}."**
- Day {expiresAfterDay + 1} briefing line: **"The Minstrel's drunkenness ended at dusk. Everyone is
  sober."**

### Data changes

- `characters.json` → no change needed (label `"Everyone is drunk"` is fine; mark it as a global
  token by moving it to `"remindersGlobal": ["Everyone is drunk"], "reminders": []` once
  `remindersGlobal` actually means "centre of the grimoire").
- `night_guide.json` → add a `minstrel` entry with a `"day"` section (new key — the guide currently
  only models `first`/`other`), or at minimum surface the wiki How-to-Run text in the day briefing.
- `night_and_jinxes.json` → add
  ```json
  { "id1": "minstrel", "id2": "legion",
    "reason": "If Legion died by execution today, Legion keeps their ability, but the Minstrel might learn they are Legion." }
  ```

## Tests to add

1. `executing an alive minion with a sober minstrel makes everyone else drunk`
   Given BMR, day 2, alive sober Minstrel, alive Assassin. When the Assassin is executed and dies.
   Then `state.globalEffects` holds a minstrel effect with `startedOnDay == 2`, and
   `isImpaired(demon) == true`, `isImpaired(minstrel) == false`.

2. `the minstrel effect survives the first dusk`
   Continuing (1): `advancePhase` (dusk into night 3) — the effect is still present and
   `isImpaired(demon)` is still true, so the Demon cannot kill on night 3.

3. `the minstrel effect ends at the second dusk`
   Continuing (2): `advancePhase` (dawn into day 3) — still drunk; `advancePhase` (dusk into
   night 4) — `state.globalEffects` is empty and `isImpaired(demon) == false`.
   (Encodes the wiki's Zombuul example: on day 3 an executed Zombuul is still drunk.)

4. `travellers and the minstrel are never drunk from this`
   `isImpaired(traveller) == false` and `isImpaired(minstrel) == false` throughout (1)-(3).

5. `a minion dying at night does not trigger the minstrel`
   Given a Minion killed with `DeathCause.DEMON` at night. Then no global effect.

6. `executing a dead minion does not trigger the minstrel`
   Given an already-dead Minion "executed". Then no global effect (and `kill` is a no-op —
   `GameActions.kt:143`).

7. `a minion who survives execution does not trigger the minstrel`
   Given a Devil's-Advocate-protected Minion executed with the outcome "executed but survives".
   Then no global effect (requires the execution-outcome record from the Pacifist spec).

8. `a poisoned minstrel does not trigger`
   Given the Minstrel holds `("poisoner","Poisoned")`. When a Minion is executed. Then no effect.

9. `a dead minstrel does not trigger`
   Given the Minstrel is dead. When a Minion is executed. Then no effect.

10. `a second minion execution refreshes the window`
    Given an effect from day 2 and a Minion executed on day 3. Then exactly one effect remains with
    `startedOnDay == 3` and `expiresAfterDay == 4`.

11. `exiling an evil traveller does not trigger`
    Given an evil Traveller exiled (`DeathCause.EXILE`). Then no effect (wiki example 2).

12. `minstrel legion jinx data is present`
    `GameData.jinxesFor("minstrel")` returns the Legion entry (fails today: 0 results).
