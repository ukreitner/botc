# Tinker (tinker) — Bad Moon Rising Outsider

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Tinker>

Current ability text (matches `characters.json`):

> "You might die at any time."

How to Run (quoted):

> "At any time, you may decide that the Tinker dies. If this is during the day,
> immediately declare that the Tinker has died. If this is during the night, mark the
> Tinker with the **DEAD** reminder and wait until dawn to declare which players died
> during the night. (*Do not say how.*)"

Storyteller guidance (quoted / from the page):

- "The Tinker cannot die from their ability while protected from death." — the Tea
  Lady, Innkeeper, Sailor and Fool all stop it. (Example 2: "Tea Lady protection
  prevents Tinker death". Example 3: "Innkeeper protection prevents demon-caused death,
  but Storyteller later kills Tinker anyway".)
- "We recommend that you never kill the Tinker when it would end the game. Players
  should win or lose by their own efforts, not Storyteller fiat."
- It is "most interesting if the Tinker dies at night" — players then wonder which Demon
  is in play or whether an extra killer exists.
- The death is a death "for any reason", not a Demon kill: it does not fire the Sage,
  the Grandmother's grandchild link, or anything else that keys on the Demon.

Points the wiki page does **not** address, flagged rather than guessed:

- Whether a drunk/poisoned Tinker can still be killed by their own ability. The
  standard rule ("a drunk or poisoned player's ability does not work") implies **no**,
  and the app's own `night_guide.json:322` already asserts this. Confidence: high, but
  it is a derivation, not a quoted line.
- Whether a Storyteller Tinker-death counts as a "night death" for the Undertaker
  (no — the Undertaker only learns about executions) or wakes the Ravenkeeper (only
  relevant if the *Ravenkeeper* dies, so not applicable).

Cross-character consequence the app must know: **the Tinker is an Outsider.** A Tinker
killed during the **day** is "an Outsider died today" and therefore arms the
**Godfather** (see `godfather.md`). A Tinker killed at night does not.

Jinxes: none on the wiki page and none in the app's data.

## What the app does today

Data:
- `characters.json:588-601` — ability text matches. `otherNightReminder: "The Tinker
  might die."` `reminders: ["Dead"]`.
- `night_and_jinxes.json:442` — otherNight index **69**, between the Farmer (68) and
  the Moonchild (70). No first-night entry. **Correct.**
- `night_guide.json:320-325` — an "other"-only entry with accurate prose, including the
  drunk/poisoned exemption. `shows: []`.

Engine:
- Nothing. There is no `tinker` string anywhere in `engine/src/main` (grep returns only
  the data files). `StatusEffects.deathNotes` has no Tinker branch.

UI:
- Night step: `QuickResolutions` `else` branch — the Tinker is an Outsider, so no panel
  renders. `InfoCalc.supports("tinker")` is false. The step shows the prose and the
  bottom tray with a single "Dead" chip.
- **The "Dead" chip only places a reminder token.** `NightToolTray`
  (`NightScreen.kt:317-341`) calls `placeExclusiveReminder`; it does **not** call
  `kill`. The seat stays `alive = true`, keeps its vote, and produces no `DeathRecord`.
- Killing for real requires Grimoire → seat → "Died at night" (`DeathCause.DEMON`) or
  "Other death" (`DeathCause.STORYTELLER`) at `SeatSheet.kt:271-279`.
- `("tinker","Dead")` is in neither `EXPIRES_AT_DAWN` nor `EXPIRES_AT_DUSK`
  (`GameActions.kt:218-242`), so once placed it stays for the rest of the game.
- The Tinker step is generated on every night the Tinker is in play, alive or dead, and
  the dawn guard (`GameShell.kt:147-161`) blocks until it is ticked.

Storyteller's actual experience: a nightly checklist row saying "The Tinker might die"
with a token that looks like it kills but doesn't, and no support for the day-time half
of the ability at all.

## Defects and gaps

1. **P1 · The one tool offered on the step does not do what it looks like it does.**
   Tapping "Dead" in the tray places `PlacedReminder("tinker","Dead")` and nothing else
   (`NightScreen.kt:317-341`). The seat is still alive, still votes, still counts toward
   `executionThreshold` and `alivePlayers`, and `WinCheck` still sees them.
   Repro: night 3 → Tinker step → tray → "Dead" → tap the Tinker's seat → the grimoire
   shows a DEAD token on a living, un-shrouded seat.

2. **P1 · No protection check.** Rules: "The Tinker cannot die from their ability while
   protected from death." App: `StatusEffects.deathNotes` has no Tinker branch, and the
   token path bypasses `deathNotes` entirely. Even the seat-sheet path only warns
   because of the *target's* generic protections, and its filter
   (`SeatSheet.kt:256-265`) does catch Tea Lady / Innkeeper / Sailor / Fool lines — so
   the seat path is the only safe one, and it is not the one the step points at.

3. **P1 · No day-time affordance.** Half the ability is "if this is during the day,
   immediately declare that the Tinker has died". Nothing on the Day tab or the
   Grimoire surfaces the Tinker as a lever the ST holds. The ST must remember the
   character exists and navigate to the seat.

4. **P1 · No "this would end the game" guard.** The wiki explicitly recommends never
   killing the Tinker when it would end the game. The app can compute this
   (`WinCheck.check` on the hypothetical post-death state) but does not; the advisory
   only appears *after* the death, at `GameShell.kt:506-519`.

5. **P2 · `("tinker","Dead")` never expires.** Unlike the other on-death markers it is
   in no expiry table, so a Tinker marked at night keeps a DEAD token forever, cluttering
   the seat and confusing later reads.

6. **P2 · The step blocks dawn every night for a character that never wakes.**
   `NightOrder.build` emits it unconditionally and `GameShell.kt:153-160` counts it as
   unfinished. The correct behaviour is an always-available, never-blocking control.

7. **P2 · Drunk/poisoned Tinker is not surfaced.** The guide prose says it, but nothing
   on the step checks `StatusEffects.isImpaired` or greys the kill.

8. **P2 · The Godfather link is invisible.** A **daytime** Tinker death arms the
   Godfather. `StatusEffects.kt:116-118` does emit "Godfather kills tonight because an
   Outsider died today" for any Outsider death — but it fires for *night* deaths too
   (see `godfather.md`, Defect 1), so it is right for the wrong reason here and wrong
   elsewhere.

9. **P3 · No dawn announcement helper.** "Announce which players died, do not say how"
   is a script the app could compose.

## Proposed behaviour (spec)

### Night step

- **when:** other nights only (index 69, unchanged). **Never blocking** — the step is
  auto-ticked and rendered in a muted style, because the Tinker does not wake.
- **wake condition:** none. Render whenever a living Tinker is in play; when the Tinker
  is dead, render "The Tinker is already dead — nothing to decide."
- **targets:** none.
- **the control:** a single explicit button on the step, not a tray token:

```
Tinker — Sam.  You may kill the Tinker at any time, for any reason.
! Sam is a Tea Lady neighbour with both neighbours good — can't die.     ← deathNotes
! Killing Sam now would end the game (only 2 would remain alive with the Demon).
[ Sam dies tonight ]      [ Leave Sam alive ]
```

  - "Sam dies tonight" runs the full death path: `deathNotes` review → confirm →
    `kill(sam, DeathCause.OTHER_NIGHT_DEATH)` → place
    `PlacedReminder("tinker","Dead")` → add to the dawn announcement list.
  - Both buttons tick the step.

### Day-time control (the P1-3 fix)

Add the same control to the **Day tab**, in a small "Storyteller levers" card that only
appears when a living Tinker is in play:

> **Tinker — Sam.** You may kill Sam right now; announce it immediately.
> `[ Sam dies now ]`

`[ Sam dies now ]` runs `deathNotes` → confirm → `kill(sam, DeathCause.STORYTELLER)`
(phase DAY → `DeathRecord.atNight = false`) → and, because the Tinker is an Outsider
dying during the day, **automatically place `PlacedReminder("godfather","Died today")`
on Sam** when a living Godfather is in play, and add a day-briefing line
"Sam (Tinker) died today — the Godfather kills tonight."

### Protection and end-of-game guards

Both controls must:
1. Call `StatusEffects.deathNotes(state, lookup, tinkerId)` and render every line.
   Add a Tinker branch to `deathNotes`:
   `"tinker" -> notes += "Tinker: the Storyteller may kill them at any time — but not while protected from death."`
2. Refuse-with-override if any protection line is present ("Tea Lady", "Innkeeper",
   "Sailor", "Fool", "Can not die") — the rules say the Tinker **cannot** die while
   protected, so this is a hard block, not a soft warning, unlike the seat sheet's
   generic "They die anyway".
3. Refuse-with-override if `StatusEffects.isImpaired(tinker)` — a drunk/poisoned Tinker
   has no ability.
4. Warn (soft) if `WinCheck.check(stateAfterKill, lookup) != null`:
   "! This would end the game. The rules recommend against killing the Tinker here."

### Structured summary

- **when:** any time — day or night. On the night sheet as a non-blocking row at index
  69; on the Day tab as a persistent lever.
- **targets:** none (the Tinker is the only subject).
- **immediate effects:** `kill(tinker, OTHER_NIGHT_DEATH | STORYTELLER)` plus
  `PlacedReminder("tinker","Dead")` when killed at night.
- **deferred effects:** announced at dawn if killed at night ("do not say how"), and
  immediately if killed during the day. A daytime death arms the Godfather.
- **expiry:** add `"tinker" to "Dead"` to `EXPIRES_AT_DAWN` — the marker exists only to
  survive until the dawn announcement.
- **information:** none.
- **visibility:** nothing shown to anyone; the cause is never announced.
- **day-time inputs:** none to record; one lever to offer.
- **interactions:** Tea Lady / Innkeeper / Sailor / Fool / Monk-style protection blocks
  it (the Monk protects only from the Demon, so the Monk does **not** block a Tinker
  death — pin this in the copy); drunk/poisoned Tinker cannot die from it; a daytime
  death arms the Godfather; a night death does not; it is never a Demon kill, so the
  Sage and Grandmother do not fire.

### UI text

- Night step: "The Tinker does not wake. You may decide Sam dies tonight — mark DEAD
  and announce at dawn without saying how."
- Day lever: "You may decide Sam dies right now. Announce it immediately, without
  saying why."
- Blocked: "Sam is protected from death — the Tinker's ability cannot kill them."
- Endgame warning: "This would end the game. The rules recommend letting players win or
  lose by their own efforts."

### Data changes

- `GameActions.kt:218-225` — add `"tinker" to "Dead"` to `EXPIRES_AT_DAWN`.
- `night_guide.json:320-325` — append "**Killing the Tinker during the day counts as an
  Outsider dying today and arms the Godfather.** Killing them at night does not.
  Never kill the Tinker when it would end the game."
- No `characters.json` or night-order changes.

## Tests to add

1. `tinker step is never blocking`
   Given a Tinker in play at night 3 and the step unticked. When the dawn guard runs.
   Then the Tinker step is not reported as unfinished. **Fails today.**
2. `placing the tinker Dead token does not kill`
   *(Documents the current trap; the fix is that the step no longer offers a bare
   token.)* Given `addReminder(tinker, ("tinker","Dead"))`. Then `tinker.alive` is
   still true and `state.deaths` is empty.
3. `tinker night death is recorded as a night death and swept at dawn`
   Given the Tinker is killed at NIGHT 3. Then the `DeathRecord` has `atNight = true`,
   and after `advancePhase` NIGHT→DAY no seat holds `("tinker","Dead")`.
4. `tinker daytime death arms the godfather`
   Given a living Godfather and a Tinker killed during DAY 2. Then the Tinker holds
   `("godfather","Died today")` and the night-3 Godfather step is armed.
   **Fails today.**
5. `tinker night death does not arm the godfather`
   Given a living Godfather and a Tinker killed during NIGHT 3. Then no
   `("godfather","Died today")` token exists and the Godfather step is not armed.
   **Fails today** (`StatusEffects.kt:116` fires on any Outsider death).
6. `protected tinker cannot die from their own ability`
   Given the Tinker holds `("innkeeper","Protected")`. When the Tinker lever is
   evaluated. Then it is disabled with the protection reason. Repeat for a Tea Lady
   neighbour and a Sailor.
7. `the Monk does not protect the tinker`
   Given the Tinker holds `("monk","Safe")`. Then the lever is enabled — the Monk
   protects only from the Demon.
8. `drunk tinker cannot die from their own ability`
   Given `("poisoner","Poisoned")` on the Tinker. Then the lever is disabled.
9. `the lever warns when the kill would end the game`
   Given 3 alive: Demon, Tinker, one Townsfolk. Then the lever shows the endgame
   warning and still allows an override.
