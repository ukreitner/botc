# Assassin (assassin) — Bad Moon Rising Minion

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Assassin>

Current ability text (matches `characters.json`):

> "Once per game, at night*, choose a player: they die, even if for some reason they
> could not."

How to Run (quoted):

> 1. "Each night except the first, wake the Assassin"
> 2. "They either shake their head no or point at any player"
> 3. "Put the Assassin to sleep"
> 4. "If a player was chosen, that player dies (mark with **DEAD** reminder)"
> 5. "This cannot be prevented in any way, except if the Assassin lacks their ability
>    (drunk/poisoned)"
> 6. "Mark the Assassin with **NO ABILITY** reminder and remove their night token"

Key mechanics (quoted):

- **First night:** "Cannot act (indicated by asterisk)."
- **Protection bypass:** "The ability bypasses all protective abilities (Innkeeper, Tea
  Lady, Fool, Sailor, etc.)" — and by the ability text, anything else that would stop
  the death: Soldier, Monk, Mayor's bounce, Pacifist, a Zombuul's first-death
  registering, a Lleech host.
- **Drunk/poisoned:** "When drunk or poisoned, the ability fails to activate and **is
  still spent**."
- **Reminder tokens:** "DEAD (target) and NO ABILITY (Assassin, after use)."
- **Goon:** "If the Assassin kills the Goon while drunk (no ability), the Goon doesn't
  die but turns evil instead." (See the flagged wiki inconsistency in `goon.md` — the
  Goon page's tips summary says the opposite. The Shabaloth precedent on the Goon page
  supports: choosing the Goon drunks the chooser immediately, so the Assassin's kill
  fails, the Goon turns evil, and the once-per-game is still spent.)

Derived points the app needs:

- **Alive only.** A dead Assassin has no ability.
- The kill is a night death, not a Demon kill: the Sage does not fire, the Grandmother's
  grandchild link does not fire, a Ravenkeeper killed this way **does** wake (they die
  at night).
- The Assassin's kill, the Demon's kill, the Godfather's kill and the Moonchild's curse
  can all land on the same night; the dawn announcement lists them together without
  causes.
- The Assassin is at otherNight position **after** every Demon, so the Demon has already
  chosen when the Assassin acts.

Jinxes: none on the wiki page, none in `night_and_jinxes.json`.

## What the app does today

Data:
- `characters.json:602-616` — ability text matches the wiki.
  `otherNightReminder: "If the Assassin has not yet used their ability: The Assassin
  either shows the 'no' head signal, or points to a player. That player dies."`
  `reminders: ["Dead", "No ability"]`.
- `night_and_jinxes.json:428` — otherNight index **55**, after every Demon and before
  the Godfather (56). **No first-night entry — correct** (the asterisk).
- `night_guide.json:326-331` — the best guide entry of the eight in this scope: it
  spells out the protection bypass, the Zombuul case, and "poisoning does stop the
  kill, but the ability is still spent". `shows: []`.

Engine:
- No `assassin` reference anywhere in `engine/src/main` (grep hits are tests only).
- `("assassin","Dead")` and `("assassin","No ability")` are in **neither** expiry table
  (`GameActions.kt:218-242`).
- `StatusEffects.deathNotes` has no Assassin branch — nothing tells the ST that this
  particular kill ignores every protection it is about to list.

UI:
- Night step: `QuickResolutions` `else` branch — Minion, not Demon → no panel.
  `InfoCalc.supports("assassin")` is false. The only tools are the bottom tray's two
  chips plus a **"Mark spent"** chip.
- **"Mark spent" works.** `NightScreen.kt:204` computes
  `oncePerGame = character.ability.startsWith("Once per game", true)`, which is true for
  the Assassin, and `NightScreen.kt:263-279` places
  `PlacedReminder("assassin","No ability")` exclusively on every holder. Good.
- **The "Dead" chip does not kill.** Like the Tinker, tapping it places
  `PlacedReminder("assassin","Dead")` and nothing else (`NightScreen.kt:317-341`). The
  target keeps their shroud off, their vote, and their place in `alivePlayers`.
- Killing for real requires Grimoire → seat → "Died at night" (`SeatSheet.kt:271`),
  which routes through `requestKill` (`SeatSheet.kt:266-268`) and, if any protection
  note matches the filter at `SeatSheet.kt:258-262`, pops the **"X might be protected"**
  dialog whose dismiss button reads **"Death prevented"**.
- The Assassin step is generated on every night after the first, whether or not it has
  been spent and whether or not the Assassin is alive, and blocks the dawn guard until
  ticked.

Storyteller's actual experience: a nightly row that reads "If the Assassin has not yet
used their ability…" with no indication whether they have; a "Dead" token that looks
like a kill and isn't; and — when the ST does kill the target properly — a confirmation
dialog telling them the target might be protected, for the one ability in the game that
ignores protection entirely.

## Defects and gaps

1. **P0 · The app argues *against* the Assassin's defining rule at the moment of the
   kill.**
   Rules: "they die, even if for some reason they could not… This cannot be prevented in
   any way." App: killing the target from the seat sheet runs `requestKill`
   (`SeatSheet.kt:266-268`), which sees the target's Innkeeper/Tea Lady/Soldier/Fool
   note and opens the "might be protected" dialog (`SeatSheet.kt:288-307`) whose
   dismiss action is literally labelled **"Death prevented"**. A storyteller who trusts
   the app will save a player the Assassin should have killed.
   Repro: Innkeeper protects Alice on night 3; Assassin points at Alice; open Alice's
   seat → "Died at night" → dialog says "Marked 'Protected' (Innkeeper) — can't die
   tonight." with a "Death prevented" button.

2. **P1 · No target picker and no kill on the step.**
   The whole action ("point at a player, that player dies, mark the Assassin spent") is
   three separate manual operations in two different screens. There is no
   `QuickResolutions` branch for `assassin` (`NightScreen.kt:470-524`).

3. **P1 · The "Dead" tray chip places a token that does not kill.** Identical trap to
   the Tinker's. The step's own guide text says "place the Dead reminder token", so the
   ST is actively directed to the non-lethal control.

4. **P1 · Spent state is not reflected in the night sheet.** Once
   `("assassin","No ability")` is on the seat, the step should be auto-skipped and
   non-blocking; instead it appears identically every night and the ST must remember to
   check the seat. `NightOrder.build` has no spent/alive filtering
   (`NightOrder.kt:142-178`).

5. **P1 · "Fails but is still spent" is not automated.** If the Assassin is drunk or
   poisoned when they point, the target lives *and* the ability is gone. The app does
   nothing: no impairment check on the step (unlike `DemonKillPanel`'s at
   `NightScreen.kt:548-554`), and "Mark spent" is a separate deliberate tap the ST is
   unlikely to make when they think the ability did nothing.

6. **P2 · A dead Assassin still gets a step.** No aliveness gate.

7. **P2 · `("assassin","Dead")` never expires.** It should be swept at dawn once
   announced. (`("assassin","No ability")` correctly persists forever, by omission.)

8. **P2 · The Goon interaction is not surfaced.** Choosing the Goon drunks the Assassin
   and (on the reading recommended in `goon.md`) wastes the ability while flipping the
   Goon evil. Nothing warns.

9. **P3 · No way to record "they shook their head no".** The ST has no way to note that
   the Assassin was offered and declined, which matters for the "have they used it yet"
   question and for the post-game log.

10. **P3 · No dawn collation.** The Assassin, the Demon, the Godfather and the Moonchild
    can all kill on the same night; nothing composes the single "these players died"
    announcement.

## Proposed behaviour (spec)

### Night step

- **when:** other nights only (index 55, unchanged — the asterisk is already honoured by
  the absence of a first-night entry: *works*).
- **wake condition:** the Assassin seat is **alive** AND does not hold
  `("assassin","No ability")`. Otherwise the step renders muted, auto-ticked and
  **non-blocking**, with the reason ("already used" / "dead").
- **targets:** 0 or 1. Any player, alive or dead? — alive only in practice; the ability
  says "choose a player", and killing an already-dead player is a no-op, so the picker
  sorts alive-first and greys the dead. Self-selection is legal.
- **the panel** (a new `QuickResolutions` branch, modelled on `DemonKillPanel`):

```
Assassin — Elena.  Once per game: they die, even if they could not.
! Elena is poisoned — the kill fails, but the ability is still spent.   ← when impaired
[ chip row: all players, alive first ]
  → Alice selected
    Protections on Alice: Innkeeper 'Protected', Soldier.
    THE ASSASSIN IGNORES ALL OF THESE. Alice dies.                      ← inverted note
[ Alice dies — ability spent ]   [ They shook their head 'no' ]
```

- **immediate effects of "Alice dies — ability spent":**
  1. `kill(alice, DeathCause.OTHER_NIGHT_DEATH, lookup)` — **bypassing every protection
     check**; the engine must expose a `force = true` path (or the panel simply calls
     `GameActions.kill` directly, which already does no protection checking — the guard
     lives only in `SeatSheet`'s UI).
  2. `addReminder(alice, PlacedReminder("assassin","Dead"))`.
  3. `placeExclusiveReminder(elena, PlacedReminder("assassin","No ability"))`.
  4. Record the `NightChoice`.
- **immediate effects when the Assassin is impaired:** the panel's confirm button reads
  **"Alice survives — ability still spent"**, kills nobody, and still places
  `("assassin","No ability")`. This is the single most valuable automation for this
  character.
- **immediate effects of "they shook their head 'no'":** record a `NightChoice` with an
  empty `targetIds`, tick the step, place nothing. The ability is **not** spent.
- **deferred effects:** announced at dawn with the night's other deaths, cause unstated.
- **expiry:** add `"assassin" to "Dead"` to `EXPIRES_AT_DAWN`. Leave
  `("assassin","No ability")` permanent.
- **information:** none computed, nothing shown.
- **visibility:** the Demon is told nothing; the Assassin is told nothing.
- **day-time inputs:** none.

### The protection-bypass fix (P0)

Two changes, both small:

1. `StatusEffects.deathNotes` gains a `cause`/`source` parameter (or a sibling function
   `deathNotes(state, lookup, playerId, ignoringProtection: Boolean)`), so that a kill
   attributed to the Assassin renders its protection lines as **struck-through
   informational text** with the header "The Assassin ignores all protection" and the
   confirm dialog loses its "Death prevented" option.
2. The seat sheet's `requestKill` gains a fourth button, "Assassin kill (ignores
   protection)", or — better — the ST never needs the seat sheet because the night step
   does the kill.

### UI text for the step

- Available: "Wake the Assassin. They shake their head, or point at a player. **That
  player dies — nothing can stop it.** Then mark the Assassin NO ABILITY."
- Impaired: "! The Assassin is drunk/poisoned. Let them point, kill nobody, **and still
  mark them NO ABILITY** — the ability is spent either way."
- Spent: "The Assassin has already used their ability. Skip."
- Dead: "The Assassin is dead. Skip."
- Goon selected: "! The Assassin chose the Goon: the Assassin becomes drunk, so nobody
  dies — but the ability is spent and the Goon turns evil. *(The wiki is inconsistent
  here; this is the reading consistent with the Shabaloth example.)*"

### Data changes

- `GameActions.kt:218-225` — add `"assassin" to "Dead"` to `EXPIRES_AT_DAWN`.
- `night_guide.json:326-331` — keep; append "The app skips this step once the No Ability
  token is placed, and marks the ability spent even when the Assassin is drunk or
  poisoned."
- No `characters.json` or night-order changes. The Zombuul claim in the current guide
  text ("even kills the Zombuul properly on its first death registering") is a
  reasonable reading of "even if for some reason they could not" but is **not** quoted
  on the wiki — flag it in the text as a Storyteller ruling rather than stating it flat.

## Tests to add

1. `assassin kill ignores every protection`
   Given Alice holds `("innkeeper","Protected")` and is the Soldier, with a Monk 'Safe'
   token for good measure. When the Assassin's kill resolves. Then Alice is dead with
   `DeathCause.OTHER_NIGHT_DEATH` and one `DeathRecord`. **Fails today** only at the UI
   level (`GameActions.kill` already ignores protection) — the test should assert the
   *panel's* contract, i.e. that no "prevented" branch exists on the Assassin path.
2. `impaired assassin kills nobody but spends the ability`
   Given the Assassin holds `("poisoner","Poisoned")` and points at Alice. Then Alice
   is alive, `state.deaths` is empty, and the Assassin holds
   `("assassin","No ability")`. **Fails today.**
3. `declining does not spend the ability`
   Given the Assassin shakes their head on night 2. Then no `("assassin","No ability")`
   token exists and the night-3 step is still armed.
4. `spent assassin step is skipped and non-blocking`
   Given `("assassin","No ability")` on the Assassin at night 4 with the step unticked.
   When the dawn guard runs. Then the step is not reported as unfinished.
   **Fails today.**
5. `dead assassin step is skipped`
   Given the Assassin is dead. Same assertion.
6. `assassin has no first-night step`
   Given an Assassin in play. When the first-night sheet is built. Then no `assassin`
   step exists. *(Passes today — pin it.)*
7. `assassin Dead marker is swept at dawn, No ability is not`
   When `advancePhase` NIGHT→DAY. Then no seat holds `("assassin","Dead")` and the
   Assassin still holds `("assassin","No ability")`.
8. `assassin kill wakes the ravenkeeper`
   Given the target is a Ravenkeeper. Then the Ravenkeeper's night step is armed
   (they died at night).
9. `assassin killing the goon`
   Given the Assassin points at a good Goon. Then (per the recommended reading) the
   Goon is alive and evil, the Assassin holds `("goon","Drunk")` and
   `("assassin","No ability")`. Mark this test as encoding a Storyteller ruling.
