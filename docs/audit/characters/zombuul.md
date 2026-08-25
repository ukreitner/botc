# Zombuul (zombuul) — Bad Moon Rising Demon

## Official rules (sources)

Sources: <https://wiki.bloodontheclocktower.com/Zombuul>,
<https://wiki.bloodontheclocktower.com/Exorcist>,
<https://wiki.bloodontheclocktower.com/Night_Order>.

**Current ability text (matches `characters.json:706`):**

> "Each night*, if no-one died today, choose a player: they die. The 1st time you die, you live but register as dead."

**How to run (wiki, verbatim, in order):**

> "The first time the Zombuul would die, they remain alive."
> "Declare that they died, but do not add a shroud to the Zombuul."
> "(Flip the life token on the Town Square, as normal.)"
> "From now on, the Zombuul registers as dead."
> "Each day, if a player dies, mark them with the **DIED TODAY** reminder."
> "(If the Zombuul 'dies' by execution, they register as dead, so mark the Zombuul with the
> **DIED TODAY** reminder.)"
> "Each night except the first, if any player is marked **DIED TODAY**, do not wake the Zombuul."
> "Each night except the first, if no player is marked **DIED TODAY**, wake the Zombuul."
> "They point at any player."
> "Put the Zombuul to sleep."
> "The chosen player dies—mark them with the **DEAD** reminder."

Reminder tokens: **DIED TODAY**, **DEAD**.

**"Died today" is a DAY-death test, not a 24-hour test.**
Tips & tricks: "You only have the ability to kill at night if nobody has died during the day."
So the Zombuul's own night victim, announced at dawn, **does not** stop it the following night —
the Zombuul is a nightly killer that only an *execution* (or another day death: Tinker,
Witch curse, Virgin trigger, a Traveller exile) shuts off. This is the character's whole design:
the town must execute every day or take a body every night.

**"Registers as dead" — exactly what changes:**

- "The next time they vote, they lose their vote token." → they hold **one ghost vote**, like any
  dead player.
- "They cannot nominate."
- "they're not an alive neighbour for the Tea Lady" — and, by the same logic, not alive for the
  Empath, Chambermaid, Godfather, execution threshold, or any other alive-count.
- "The only differences are that the game continues, the Zombuul still attacks, and the game
  continues if just two other players are alive."
- **A dead player can be nominated and executed** — the wiki says so directly: "If a dead player
  is executed, the player can't die again, so the Zombuul would still wake." Executing the corpse
  is how the town kills a Zombuul that has gone to ground.
- "The second time the Zombuul dies, they die for real and good wins."
- "If a drunk or poisoned Zombuul dies, good wins." (The "1st time you die, you live" clause is
  an ability, so it fails when the Zombuul is impaired.)
- "If a 'dead' Zombuul becomes drunk or poisoned, do not announce that the player is alive." —
  they simply lose the kill that night, silently.
- Tips: "Kill yourself, and hide in plain sight as a dead player!"

**Win condition — flagged as UNCERTAIN.** The only wiki sentence is "the game continues if just
two other players are alive." Read literally: with the "dead" Zombuul plus two living players,
evil has **not** yet won. What happens at Zombuul + **one** living player is not stated. Do not
auto-resolve this; surface it as an advisory with the quote.

**Jinx.** Summoner: "If the Summoner summons a dead player into the Zombuul, the Zombuul has
already 'died once'." **Missing from `night_and_jinxes.json`.**

**Night-order position.** Other nights: first of the Bad Moon Rising demons, after Imp and before
Pukka (`night_and_jinxes.json:411`) — correct. No first-night entry — correct.

**Lunatic.** A Lunatic shown the Zombuul token believes they may only kill on nights following a
death-free day, and believes they survive their first death. The ST has to run both illusions by
hand.

## What the app does today

- **Data.** `characters.json:703-716` — ability text, `otherNightReminder` and reminders
  `["Died today", "Dead"]` all match the wiki. Night-order position correct. Works.
- **Night guide.** `night_guide.json:381-386` (`other` only) is accurate prose: gate on "did
  anyone die today", and a clear statement of the register-as-dead rule. It is text only —
  nothing is enforced or computed.
- **The night action is the generic demon kill.** `QuickResolutions` (`NightScreen.kt:462-525`)
  has no `zombuul` branch; the `else` arm renders `DemonKillPanel` **only when
  `holder.alive`** (`NightScreen.kt:520`). A Zombuul that has taken its first "death" has
  `alive == false`, so **the kill panel disappears entirely** — the storyteller cannot record
  the Zombuul's kills for the rest of the game from the night sheet.
- **No "died today" gate.** The panel is shown on every night regardless of whether anyone died
  during the day; the ST must remember the rule and look back through the log
  (`GameExtras.kt:44-90`) to check. All the data needed is already in `state.deaths`
  (`GameState.kt:76-88`: `day`, `atNight`, `cause`) — nothing consumes it.
- **The `Died today` token is manual.** The ST would have to hand-place it from the tray
  (`NightScreen.kt:283-353`) each time anyone dies during the day, and hand-remove it: it is in
  neither expiry table (`GameActions.kt:218-242`). The identically-named Godfather token
  (`characters.json`, `godfather.reminders = ["Died today", "Dead"]`) has the same problem.
- **The first "death" is a plain death.** The ST presses "Executed" in the seat sheet
  (`SeatSheet.kt:274-276`) and `GameActions.kill` (`GameActions.kt:136-156`) sets
  `alive = false`. Everything downstream then treats the Zombuul as an ordinary corpse.
  Consequences today:
  - `WinCheck.check` (`WinCheck.kt:70-86`) fires **"Every Demon is dead — good wins"** with
    cautions for the Scarlet Woman / Mastermind / Imp but **nothing about the Zombuul**. The
    Storyteller is told the game is over when it is not. This is a P0 wrong-outcome.
  - `StatusEffects.deathNotes` has the right warning — `"Zombuul: the first time it dies, it lives
    but registers as dead."` (`StatusEffects.kt:119-121`) — but it is gated on
    `state.deaths.none { it.playerId == playerId }`, and `SeatSheet.kt:240` only renders death
    notes while the player is **alive**. So the warning shows once, before the death, and is gone
    forever afterwards.
  - The night sheet marks the step "All holders are dead — usually skip."
    (`NightScreen.kt:751-757`).
  - The grimoire hides the wake-order badge for dead players (`GrimoireScreen.kt:435`), so the
    Zombuul loses its only visual "this seat still acts" cue.
  - Nothing distinguishes the seat from a real corpse: same shroud (`GrimoireScreen.kt:394-400`),
    same "ghost vote" label (`:464-468`).
- **Votes/threshold/nomination all come out right by accident**, because modelling the fake death
  as a real death is exactly what "registers as dead" means:
  `alivePlayers`/`executionThreshold` (`GameState.kt:110-124`) exclude it; `kill` grants a ghost
  vote (`GameActions.kt:145`); `DayScreen.kt:137` blocks it from nominating; ghost votes are spent
  on record (`DayScreen.kt:232-240`). Works.
- **But a dead player cannot be nominated** (`DayScreen.kt:146`, `enabled = { p -> p.alive && ... }`).
  That blocks the town's only route to killing a hiding Zombuul — see P0-4.
- **Second death is a no-op.** `GameActions.kill` returns the state unchanged when
  `!player.alive` (`GameActions.kt:143`), so there is no way to record the Zombuul's real death
  at all.
- **Dawn** never names the victim (`NightOrder.kt:59`).
- **Exorcist** annotation is appended correctly for the Zombuul (`NightOrder.kt:150-154`, and
  `StatusEffectsTest.kt:57-69` covers it) but is text only; the panel still offers a kill.

## Defects and gaps

1. **P0 · The app declares good the winner when the Zombuul takes its first "death".**
   `WinCheck.kt:70-86` returns `goodWins = true, "Every Demon is dead"` with no Zombuul caution.
   *Repro:* BMR game, Zombuul executed on day 2 → the win advisory (`GameExtras.kt`, win advisory
   section) tells the ST good has won.
2. **P0 · A "dead" Zombuul gets no night action.** `NightScreen.kt:520` gates `DemonKillPanel` on
   `holder.alive`, so the Zombuul's step shows guide prose and "All holders are dead — usually
   skip" (`NightScreen.kt:751-757`) and offers no way to record the kill.
   *Repro:* kill the Zombuul from the seat sheet, advance to the next night, open its row.
3. **P0 · The "if no-one died today" gate is not applied anywhere.** The app offers a Zombuul kill
   on every night, including the night after an execution, and offers no reminder of the rule at
   the moment of decision. All the data is in `state.deaths`.
4. **P0 · Dead players cannot be nominated** (`DayScreen.kt:146`). The rules allow it — the wiki's
   Zombuul page relies on it ("If a dead player is executed…") — and it is the only way the town
   can execute a Zombuul hiding as a corpse. As written, a Zombuul that reaches its "dead" state
   is unkillable through the app's day screen.
5. **P0 · The Zombuul's real (second) death cannot be recorded.** `GameActions.kill` early-returns
   for a dead player (`GameActions.kt:143`); "Undo death" (`SeatSheet.kt:282`) would delete the
   first death record and misrepresent the history.
6. **P1 · There is no grimoire marker for "secretly alive".** After the fake death the seat is
   visually identical to a corpse (shroud `GrimoireScreen.kt:394-400`, ghost-vote label `:464-468`,
   wake badge suppressed `:435`). The one warning that exists
   (`StatusEffects.kt:119-121`) stops rendering the moment it becomes relevant
   (`SeatSheet.kt:240` gates death notes on `player.alive`).
7. **P1 · "Died today" must be tracked by hand.** No auto-placement, no auto-expiry
   (`GameActions.kt:218-242`), and no derived view. Shared with the Godfather.
8. **P1 · "If a drunk or poisoned Zombuul dies, good wins" is not modelled.** `deathNotes` says
   the opposite unconditionally (`StatusEffects.kt:119-121`) — it does not check
   `StatusEffects.isImpaired`.
9. **P1 · No dawn announcement** (`NightOrder.kt:59`), and no day-start reminder that "nobody died
   today ⇒ the Zombuul kills tonight", which is the single most useful thing the ST can know at
   dusk.
10. **P2 · Missing Summoner jinx** ("If the Summoner summons a dead player into the Zombuul, the
    Zombuul has already 'died once'.") — absent from `night_and_jinxes.json`.
11. **P2 · Exorcist on the Zombuul is text-only** (`NightOrder.kt:150-154`); the panel should
    collapse to a "does not act" state.
12. **P2 · Lunatic-as-Zombuul** has no placebo gate ("no-one died today, so you may kill") and no
    way to stage a fake first death for the Lunatic.

## Proposed behaviour (spec)

### Engine model

Model the fake death as a **real death record plus a secret-life token**. This is the modelling
choice that makes every downstream rule ("registers as dead") come out right for free.

- `PlacedReminder("zombuul", "Registers as dead")` — placed on the Zombuul at its first death;
  never expires; removed only on the real second death.
- Derived helpers:

```kotlin
// StatusEffects.kt or a new Deaths.kt
/** True when this seat is a Zombuul that has spent its one free death and is secretly alive. */
fun isSecretlyAlive(p: Player) =
    p.characterId == "zombuul" && !p.alive &&
    p.reminders.any { it.sourceId == "zombuul" && it.label == "Registers as dead" }

/** Players who actually died during the previous day phase (the Zombuul's gate). */
fun diedToday(state: GameState): List<DeathRecord> =
    state.deaths.filter { it.day == state.cycle - 1 && !it.atNight }   // during NIGHT cycle
```

  Notes on the derivation:
  - `GameActions.kill` already records `day = state.cycle` and `atNight = phase == Phase.NIGHT`
    (`GameActions.kt:148-151`), and already **no-ops on an already-dead player**
    (`GameActions.kt:143`) — which is exactly the "If a dead player is executed, the player can't
    die again, so the Zombuul would still wake" ruling, for free.
  - Traveller exiles (`DeathCause.EXILE`) are day deaths and **do** count.
  - Deaths later undone with `resurrect` still count (they died today).

- New actions:

```kotlin
fun zombuulFirstDeath(state, zombuulId, cause, lookup): GameState  // kill + place the token
fun zombuulRealDeath(state, zombuulId, cause, lookup): GameState   // remove token, record 2nd death
fun zombuulNight(state, zombuulId, targetId: Long?, prevented: Boolean, lookup): GameState
```

`zombuulFirstDeath` must refuse (and instead call `zombuulRealDeath`) when
`StatusEffects.isImpaired(zombuul)` — "If a drunk or poisoned Zombuul dies, good wins."

### Structured night action

- **when:** other nights only.
- **wake condition:** `diedToday(state).isEmpty()` **AND** (holder alive **OR**
  `isSecretlyAlive(holder)`) **AND** the holder does not carry `exorcist:Chosen`.
  If someone died today, render the step as a closed, checked-off note — do **not** hide it, so
  the ST can see the rule was applied.
- **targets:** exactly 0 or 1; any player ("They point at any player"), including itself
  (the wiki's own tip). Picker sorts alive first; dead seats greyed with "already dead".
- **immediate effects:** `kill(target, DeathCause.DEMON)` subject to the usual protection checks
  via `StatusEffects.deathNotes`.
- **deferred effects:** none; the gate is recomputed each night from `state.deaths`.
- **expiry:** `zombuul:Registers as dead` never expires. A derived (not placed) `Died today`
  badge is recomputed every phase; if a real token is preferred for the physical metaphor,
  auto-place it on every day death and add `"zombuul" to "Died today"` **and**
  `"godfather" to "Died today"` to `EXPIRES_AT_DAWN` (`GameActions.kt:218-225`) — dawn is the
  correct expiry because the token is placed on day D, read on night D+1, and must be gone for
  day D+1.
- **information:** none.
- **visibility:** night 1 demon info via `DEMON_INFO` — works today.
- **day-time inputs:** none beyond the death record the app already keeps.

### Night step UI (replaces `DemonKillPanel` for `zombuul`)

**Gate satisfied — heading:** `Zombuul — nobody died today, so it kills.`

```
Nobody died during day <N>.  The Zombuul wakes.
Who did <Zombuul name> point at?
[ seat chips ]     ! <deathNotes>
[ <Name> dies ]    [ Protected — no death ]
```

**Gate blocked:**

```
<X> died today (executed, day <N>) — the Zombuul does NOT wake tonight.
[ Understood ]          (checks the step off)
Note: a player who died at NIGHT does not count. Only deaths during the day stop the Zombuul.
```

**Secretly alive:**

> banner, always on: `This Zombuul registers as dead but is alive. It still wakes and kills. It
> has a ghost vote, cannot nominate, and is not an alive neighbour. The next time it dies, it
> dies for real and good wins.`

**Exorcised:** `Exorcist chose the Zombuul — it does not act tonight.`

**Impaired:** `! The Zombuul is drunk/poisoned — nobody dies tonight. Say nothing about it being
alive.` (wiki: "If a 'dead' Zombuul becomes drunk or poisoned, do not announce that the player is
alive.")

### Death handling (seat sheet + execution paths)

Intercept every death of a Zombuul (`SeatSheet.kt:266-307`, `DayScreen.kt:112` and `:352-356`,
`GameShell.kt:599-604`):

- **First death, Zombuul sober:** dialog —
  `<Name> is the Zombuul. They do NOT die. Announce the death exactly as normal, flip their life
  token, and keep playing them: they still wake at night, hold one ghost vote, cannot nominate,
  and are not an alive neighbour.`
  `[ Register as dead ]` → `zombuulFirstDeath` (kill + `Registers as dead` token).
- **First death, Zombuul drunk/poisoned:** dialog —
  `<Name> is the Zombuul but is drunk/poisoned — the survival clause fails. They die for real and
  GOOD WINS.` `[ They die for real ]` → `zombuulRealDeath`.
- **Second death (seat carries `Registers as dead`):** the seat sheet must offer
  `[ Zombuul dies for real — good wins ]` even though `alive == false`
  (today `SeatSheet.kt:280-286` only offers Resurrect / Undo death / ghost vote).
- **Executing a corpse:** allow nominating dead players (`DayScreen.kt:146`) with the label
  `<Name> † (dead — an execution cannot kill them again)`, except when the nominee is a
  secretly-alive Zombuul, where it silently *can*.

### Win-check changes (`WinCheck.kt`)

```kotlin
val aliveDemons = demons.filter { it.alive || StatusEffects.isSecretlyAlive(it) }
```

- Good-wins-because-demons-are-dead (`WinCheck.kt:70-86`) must not fire while a secretly-alive
  Zombuul exists; add the caution
  `"Zombuul: the first time it dies it lives but registers as dead — check whether this was its
  first death."` to the existing caution list whenever `"zombuul" in inPlayIds`.
- Evil-wins-at-two-alive (`WinCheck.kt:88-98`) must count only genuinely alive players (which
  already excludes the "dead" Zombuul) and, when a secretly-alive Zombuul is on the board, attach
  the caution, quoting the wiki:
  `"Zombuul registers as dead: the wiki says 'the game continues if just two other players are
  alive'. What happens when only the Zombuul and one other player live is not stated — your
  call."`
- When a Zombuul dies for real, return `goodWins = true, "The Zombuul died a second time — good
  wins."`

### Grimoire display of a "dead" Zombuul

- Keep the shroud (the ST's screen must match the town square) but add an unmistakable ST-only
  overlay: a red ring around the token plus a small "Z" / "UNDEAD" badge in the corner where the
  wake number sits.
- Restore the wake-order badge for secretly-alive seats: change `GrimoireScreen.kt:435` to
  `if (wakeNumber != null && (player.alive || StatusEffects.isSecretlyAlive(player)))`.
- Header line (`GrimoireScreen.kt:165-167`): `"<N> alive · <T> to execute · <G> ghost votes"` →
  append `" · 1 registers as dead"` when a secretly-alive seat exists. The execution threshold
  must stay computed from genuinely-alive players (it already is).
- Seat accessibility text (`GrimoireScreen.kt:341-346`) should say "dead (registers as dead —
  secretly alive)".
- Night sheet: suppress "All holders are dead — usually skip" (`NightScreen.kt:751-757`) for a
  secretly-alive holder.

### Deferred / dawn / day output

- **Dawn (`DawnReport`):** `"<X> died in the night."` / `"Nobody died in the night."` Nothing
  Zombuul-specific is announced.
- **Day-start briefing:** nothing yet (the day has just begun).
- **Dusk briefing — this is the high-value one.** When the ST presses "Dusk"
  (`GameShell.kt:124`, `:592-616`), show:
  - if nobody died today: `! Nobody died today — the Zombuul kills tonight.`
  - if someone died today: `The Zombuul does not wake tonight (<X> died today).`
  This turns the Zombuul's whole rule into one line at the moment it matters.
- **Log (`GameExtras.kt:44-90`):** render the Zombuul's first death as
  `"<name> registered as dead (Zombuul — still alive)"` rather than "executed".

### Lunatic pretending to be a Zombuul

- Placebo panel with the same gate text the real Zombuul sees, driven by
  `shownCharacterId == "zombuul"`: on nights after a day death, tell the Lunatic they don't wake
  (i.e. simply don't wake them) — that alone makes the illusion consistent.
- One target, marked `lunatic:Attack 1` (expires at dawn, `GameActions.kt:222`).
- Offer the ST a "stage a fake first death for the Lunatic" helper if the Lunatic is executed:
  the Lunatic *does* die, and the ST must decide how to handle their expectation of surviving —
  surface a note rather than a mechanic.

### Data changes

- `night_and_jinxes.json` — add
  `{"id1":"summoner","id2":"zombuul","reason":"If the Summoner summons a dead player into the Zombuul, the Zombuul has already 'died once'."}`.
- `night_guide.json:381-386` — append: "Only deaths during the DAY stop the Zombuul; a player who
  died at night does not count, and executing an already-dead player does not count. If the
  Zombuul is drunk or poisoned when it would die, it dies for real and good wins. If a 'dead'
  Zombuul becomes drunk or poisoned, do not announce that they are alive."
- `characters.json:703-716` — no changes needed.

## Tests to add

1. `zombuul does not wake after a day death`
   Given a BMR game, day 2, seat 3 executed (`kill(..., EXECUTION)` while `phase == DAY`,
   `cycle == 2`). When `advancePhase` to night 3.
   Then `Deaths.diedToday(state)` is non-empty and the Zombuul night action reports "blocked".
2. `zombuul wakes after a death-free day`
   Given day 2 with no deaths. When night 3 begins.
   Then `Deaths.diedToday(state)` is empty and the Zombuul action is available.
3. `a night death does not block the zombuul`
   Given the Zombuul killed seat 3 on night 2, and nobody died during day 2.
   When night 3 begins. Then the Zombuul action is available.
   (This is the rule the app is most likely to get wrong; it is what makes the Zombuul a nightly
   killer.)
4. `executing an already-dead player does not block the zombuul`
   Given seat 3 is already dead; when the ST records an execution of seat 3 during day 2.
   Then `state.deaths` gains no record (`GameActions.kt:143`) and the Zombuul wakes on night 3.
5. `a traveller exile blocks the zombuul`
   Given a Traveller exiled during day 2 (`DeathCause.EXILE`). Then the Zombuul does not wake.
6. `first zombuul death registers as dead`
   When `zombuulFirstDeath(zombuulId, EXECUTION)`.
   Then the seat is `alive == false`, `ghostVoteUsed == false`, carries
   `zombuul:Registers as dead`, and `state.deaths` has one record for it.
7. `win check does not award good the game on the zombuul's first death`
   Given the state from (6). Then `WinCheck.check(...)` does **not** return
   `goodWins = true` for "Every Demon is dead"; the advisory (if any) carries the Zombuul
   caution. **Fails today** (`WinCheck.kt:70-86`).
8. `zombuul kills while registering as dead`
   Given the state from (6) and a death-free day.
   When `zombuulNight(target = 2)`. Then seat 2 is dead.
9. `impaired zombuul dies for real`
   Given the Zombuul holds `poisoner:Poisoned` and is executed.
   Then it has no `Registers as dead` token and `WinCheck` returns `goodWins = true`.
10. `second zombuul death is recordable and ends the game`
    Given the state from (6). When `zombuulRealDeath(zombuulId, EXECUTION)`.
    Then the `Registers as dead` token is gone, `state.deaths` has two records for that seat, and
    `WinCheck.check` returns `goodWins = true` with reason mentioning the second death.
    **Fails today** — `GameActions.kill` no-ops on a dead player.
11. `dead players may be nominated`
    Given seat 3 is dead. Then the nominee picker enables seat 3.
    **Fails today** (`DayScreen.kt:146`).
12. `registering-as-dead zombuul does not count toward the execution threshold`
    Given 6 seats, one of them a secretly-alive Zombuul.
    Then `state.executionThreshold == Voting.executionThreshold(5)`. (Passes today — pins it.)
13. `registering-as-dead zombuul is not an alive Tea Lady neighbour`
    Given a Tea Lady neighbouring the secretly-alive Zombuul.
    Then `StatusEffects.deathNotes` for the Tea Lady's other neighbour does **not** claim Tea Lady
    protection from the Zombuul side.
14. `zombuul night step is present and actionable while registering as dead`
    Given the state from (6) at night 3 with a death-free day 2.
    Then the night sheet contains a `zombuul` step that is not flagged "all holders dead".
