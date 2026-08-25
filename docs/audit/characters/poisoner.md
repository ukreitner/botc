# Poisoner (poisoner) — Trouble Brewing Minion

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Poisoner> (fetched 2026-08-25).

Current ability text:

> "Each night, choose a player: they are poisoned tonight and tomorrow day."

How to run — verbatim:

- *"Each night, wake the Poisoner. They point at any player. The chosen player
  becomes poisoned—put the **POISONED** reminder token by the chosen player's
  character token."*
- *"Each dusk, the poisoned player becomes healthy—remove their **POISONED**
  reminder."*
- *"If their ability would give them information, you can give false information
  to them if you wish."* (Information is *unreliable*, not necessarily false.)
- *"If a poisoned player uses a 'once per game' ability while poisoned, they
  cannot use their ability again."* — the ability is spent and wasted.
- The wiki does not restrict the target: any player, including themselves, the
  Demon, and (in practice) a dead player. It does not require a different target
  from last night.
- A poisoned player still **wakes and acts normally**; the Storyteller keeps up
  the illusion. Their ability simply does nothing.
- Poison persists through the day even if the Poisoner dies; it ends at dusk.
- Night order: `firstNight` index 27, `otherNight` index 13 — early, before every
  Townsfolk info role, so the Storyteller knows who is poisoned before computing
  any information.

Jinx (from `night_and_jinxes.json`): `summoner` × `poisoner` — "If the Summoner
is poisoned on the 3rd night, the Summoner chooses which Demon, but the
Storyteller chooses which player becomes that Demon."

## What the app does today

Data
- `characters.json` — `poisoner`: ability matches the wiki. `reminders:
  ["Poisoned"]`. `firstNightReminder` / `otherNightReminder` are the official
  night-sheet strings; the "other" one still begins "The previously poisoned
  player is no longer poisoned." even though the engine already removed it.
- `night_and_jinxes.json` — `firstNight[27]`, `otherNight[13]`. Correct.
- `night_guide.json` — `poisoner.first` and `.other` both present with accurate
  prose. No show cards (correct — the Poisoner is shown nothing).

Engine
- `GameActions.kt:231-242` — `EXPIRES_AT_DUSK` contains `"poisoner" to
  "Poisoned"`.
- `GameActions.kt:258-263` — `advancePhase` clears `EXPIRES_AT_DUSK` on the
  DAY → NIGHT transition. **This is correct**: dusk is exactly that boundary, so
  the token is gone before the Poisoner picks again.
- `GameActions.kt:194-201` — `placeExclusiveReminder` moves a single-copy token,
  so the tray's "Poisoned" chip behaves like the physical one-of-a-kind marker.
- `StatusEffects.kt:36-46` — `isImpaired` returns true when any reminder label
  contains `"poison"` (case-insensitive), so `poisoner:"Poisoned"` counts
  everywhere `isImpaired` is consulted.
- `InfoCalc.kt:133-153` — `impairments()` emits `"<name> is POISONED (Poisoner) —
  give false info."` for the info holder.
- `InfoCalc.kt:158-166` — `commonCaveats` prepends those to every computed
  result.

UI
- `NightScreen.kt:283-306` — the tool tray shows the Poisoner's "Poisoned" chip
  while the Poisoner step is expanded; tap the chip, then tap a seat.
- `NightScreen.kt:878-884` — caveats render in ember red under the info headline.
- `NightScreen.kt:903-930` — because the caveat string contains `"POISONED"`, a
  "False info to show instead:" row of alternative numbers / inverted YES-NO
  appears. **This works** for the numeric and yes/no info roles.
- `GrimoireScreen.kt:332`, `:421-434` — a poisoned seat gets a small green "!"
  badge on the token and ", drunk or poisoned" in its accessibility label.
- `NightScreen.kt:548-554` — `DemonKillPanel` warns when the **Demon** is
  impaired.

What works, in one line: the token, its exclusivity, its dusk expiry, the
impairment badge on the grimoire, and false-info chips for numeric/yes-no info
roles all work correctly.

## Defects and gaps

1. **P0 · Protection notes ignore a poisoned protector.**
   `StatusEffects.kt:64-78` reports, unconditionally:
   - `"Marked 'Safe' (Monk) — protected from the Demon."` (line 66)
   - `"Marked 'Protected' (Innkeeper) — can't die tonight."` (line 67)
   - `"The Soldier is safe from the Demon."` (line 74)
   - `"The Sailor can't die."` (line 73), `"Fool: the first time they die, they
     don't."` (line 75), `"Tea Lady…"` (lines 80-90)
   None of these check whether the *protector* is drunk or poisoned. Poisoning
   the Monk or the Soldier is the single most common Poisoner play, and the app
   then tells the Storyteller their target is protected. Worse,
   `SeatSheet.kt:256-307` treats those strings as `protectionNotes` and puts the
   kill behind a "they might be protected" dialog whose dismiss button is
   labelled **"Death prevented"**. Repro: poison the Soldier, Imp targets the
   Soldier → "The Soldier is safe from the Demon" with no mention of the poison.

2. **P0 · A poisoned Virgin still fires the nomination warning.**
   `StatusEffects.kt:153-157` emits "Virgin's first nomination: if `<X>` is a
   Townsfolk, they are executed immediately" without checking
   `isImpaired(virgin)`. Repro: poison the Virgin on night 1, nominate her on day
   1 → the app tells the Storyteller to execute the nominator.

3. **P1 · No impairment flag on the night sheet.**
   `NightStepRow` (`NightScreen.kt:690-765`) prints the holder's name in plain
   primary colour with no poison marker. The impairment is only surfaced *inside*
   the expanded panel and only for the ~26 characters in `InfoCalc.supports`
   (`InfoCalc.kt:29-36`). A poisoned Monk, Soldier, Slayer, Butler, Mayor,
   Virgin, Chambermaid-target etc. gets **no warning anywhere on the night
   sheet**. Repro: poison the Monk, open the Monk step → no indication.

4. **P1 · Placing the Monk's `Safe` token from a poisoned Monk creates a real
   protection in the engine.**
   The tray places `monk:"Safe"` (`NightScreen.kt:308-353`), and
   `deathNotes` then reads it back as genuine protection (defect 1). The engine
   has no notion of "token placed but the source malfunctioned". Physical
   Storytellers do place the token; the app must place it *and* know it is inert.

5. **P1 · There is no Poisoner resolution panel.**
   `QuickResolutions` (`NightScreen.kt:462-525`) has branches for
   `snakecharmer`, `fanggu`, `professor` and a generic Demon fallback — nothing
   for the Poisoner. The Storyteller must notice the tray, tap the chip, then tap
   a seat, with no target list, no "same as last night?" hint, and no record of
   who was chosen on which night.

6. **P1 · No poison history / no "who did the Poisoner choose" log.**
   `GameLogDialog` (`GameExtras.kt:46-105`) logs deaths and nominations only.
   After a Poisoner dies mid-game the Storyteller has no record of past targets,
   which matters for reconstructing whether earlier info was false.

7. **P1 · Once-per-game abilities burned while poisoned are not tracked.**
   Rules: the ability is **spent**. The app has a manual "Mark spent" chip
   (`NightScreen.kt:263-279`) that places `<char>:"No ability"`, but nothing
   prompts the Storyteller to use it when a poisoned Slayer/Professor/Fang Gu
   uses their shot.

8. **P2 · No false-info help for the non-numeric info roles.**
   `NightScreen.kt:903-930` only offers alternatives when the headline starts
   with a digit or with YES/NO. A poisoned Washerwoman / Librarian /
   Investigator / Undertaker / Ravenkeeper / Dreamer / Balloonist gets the true
   answer plus a red caveat and no assistance building the lie. `startKnowing`
   (`InfoCalc.kt:408-421`) does not even list plausible false pairs.

9. **P2 · The `otherNightReminder` and `night_guide` prose tell the Storyteller
   to do work the app already did.**
   Both begin "Remove the Poisoned reminder from the previously poisoned player",
   but `advancePhase` cleared it at dusk. Confusing at the table.

10. **P2 · Target picker has no constraints or defaults.**
    Any seat can receive the token including a dead player; there is no note that
    a dead target wastes the poison, and no "the Demon is `<name>` — don't
    double up" hint.

11. **P3 · A poisoned Poisoner is not called out.** (A Poisoner poisoned by
    another source still places a token that does nothing; the app would treat it
    as real.) Same root cause as defect 4.

## Proposed behaviour (spec)

### Core engine change: impairment-aware effects

Introduce a single predicate and use it wherever a *source* ability's effect is
read back:

```kotlin
// StatusEffects.kt
/** Is the ability that placed [reminder] currently working? */
fun sourceAbilityWorks(state: GameState, lookup: (String) -> Character?, reminder: PlacedReminder): Boolean
```
Implementation: find the alive holder of `reminder.sourceId`; return
`holder != null && !isImpaired(state, lookup, holder)` — but evaluate impairment
**at the time the token was placed**, which is why the token itself should carry
that fact. Preferred concrete design:

- extend `PlacedReminder` with `val inert: Boolean = false`;
- the tray sets `inert = isImpaired(state, lookup, sourceHolder)` when it places
  a token whose source is a character in play;
- `deathNotes` renders an inert protection as
  `"Marked 'Safe' (Monk) — but the Monk was poisoned: this does NOT protect."`
  and `SeatSheet.kt:256-265` must not count inert notes as `protectionNotes`;
- `Soldier` / `Sailor` / `Fool` / `Tea Lady` / `Lleech` standing protections at
  `StatusEffects.kt:73-90` gain the same guard, keyed on
  `isImpaired(state, lookup, thatPlayer)`.

This is the single highest-value change in this scope: it makes the Poisoner
actually *work* inside the app.

### Night step (both first and other nights)

- **when:** both; wake condition = holder alive. (A dead Poisoner does not act —
  the row already says "All holders are dead — usually skip".)
- **targets:** exactly 1, any player. Picker sorts alive first, then dead
  (labelled "dead — poison is wasted"), annotates the previous night's target
  with "poisoned last night" (allowed, not forbidden), and annotates the Demon's
  seat with "your Demon".
- **immediate effects:** `placeExclusiveReminder(target, PlacedReminder(
  "poisoner", "Poisoned"))`. Record the choice in a per-night action log.
- **deferred effects:** none at dawn. At **day start** the Storyteller briefing
  must include: "`<target>` is poisoned all day — any ability they use today does
  nothing (Slayer shot, Virgin trigger, Mayor bounce, Butler restriction…)."
- **expiry:** at **dusk** (already correct via `EXPIRES_AT_DUSK`).
- **information:** none for the Poisoner. For the *target*, every night step and
  every day trigger involving them must show
  `"<name> is POISONED (Poisoner) — their ability does nothing."`
- **visibility:** nothing shown to anyone.
- **day-time inputs:** none.

### Surfacing the poison everywhere (the real fix)

1. `NightStepRow` (`NightScreen.kt:735-742`): append a red "· poisoned" /
   "· drunk" chip after any holder name for which `isImpaired` is true, on
   **every** step, not only `InfoCalc`-supported ones.
2. `StepDetailPanel` (`NightScreen.kt:770-932`): render
   `InfoCalc.impairments(state, lookup, holder)` unconditionally at the top of
   the panel, before `QuickResolutions`, for every character — today it is only
   reached through `commonCaveats` inside `InfoCalc.compute`.
3. `StatusEffects.nominationWarnings`: guard the Virgin note with
   `!isImpaired(state, lookup, nominee)`, and when the Virgin *is* impaired emit
   the Storyteller-only note "Virgin `<name>` is poisoned/drunk — nothing
   happens."
4. Day-start briefing (new, shared with `devilsadvocate`, `butler`, `cerenovus`
   etc.): list every player currently holding a "tonight and tomorrow day" token.

### False information helper

Extend the "False info to show instead" block (`NightScreen.kt:903-930`) so each
`InfoCalc` calculator can return `alternatives: List<InfoResult>` — plausible
lies computed from the grimoire:
- `startKnowing` → each (wrong character token, wrong pair of players) combo;
- `undertaker` → any not-in-play character token, plus each in-play one;
- `revealCharacter` → any character token;
- `fortuneteller`/`empath`/`chef` → already covered by the numeric/yes-no path.

### Data changes
- `characters.json` `poisoner.otherNightReminder`: drop the leading "The
  previously poisoned player is no longer poisoned." sentence (the app does it)
  or reword to "(the app removed last night's Poisoned token at dusk)".
- `night_guide.json` `poisoner.other`: same edit.
- Add to both guide entries: "A poisoned player still wakes and acts as normal —
  keep up the illusion. If they use a once-per-game ability tonight, it is spent."

### UI text
- Step header: "Poisoner — who did `<name>` point to?"
- After placing: "`<target>` is poisoned tonight and all of tomorrow. The token
  clears itself at dusk."
- On a dead target: "`<target>` is dead — the poison is wasted. Confirm anyway?"

## Tests to add

1. `Given` a Monk who is poisoned and has placed `monk:"Safe"` on the Mayor,
   `When` `deathNotes(mayor)` is computed, `Then` the note says the protection
   does **not** apply (and `SeatSheet` protection filtering excludes it).
2. `Given` a poisoned Soldier, `When` `deathNotes(soldier)` is computed,
   `Then` "The Soldier is safe from the Demon" is absent or explicitly negated.
3. `Given` a poisoned Virgin nominated on day 1, `When`
   `nominationWarnings(nominator, virgin)` is computed, `Then` the "executed
   immediately" warning is absent.
4. `Given` `poisoner:"Poisoned"` on seat 3 during DAY 2, `When`
   `advancePhase()` runs, `Then` the token is gone and `isImpaired(seat3)` is
   false at the start of night 3.
5. `Given` `poisoner:"Poisoned"` on seat 3 during NIGHT 2, `When`
   `advancePhase()` runs to DAY 2, `Then` the token is **still present**
   (poison lasts "tonight and tomorrow day").
6. `Given` the Poisoner poisons seat 3 on night 2 and seat 5 on night 3,
   `When` night 3's placement happens, `Then` seat 3 holds no
   `poisoner:"Poisoned"` (exclusive token).
7. `Given` a poisoned Empath, `When` `InfoCalc.compute("empath", …)`,
   `Then` `caveats` contains "is POISONED" (regression guard — this works today).
8. `Given` a poisoned Washerwoman, `When` `InfoCalc.compute("washerwoman", …)`,
   `Then` the result exposes at least one `alternatives` entry the UI can offer
   as a lie.
9. `Given` the Poisoner is executed on day 2 while their night-2 target is
   poisoned, `When` day 2 continues, `Then` the target is still `isImpaired`
   until `advancePhase` to night 3.
