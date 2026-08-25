# Godfather (godfather) — Bad Moon Rising Minion

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Godfather>

Current ability text (matches `characters.json`):

> "You start knowing which Outsiders are in play. If 1 died today, choose a player
> tonight: they die. [-1 or +1 Outsider]"

How to Run (quoted in full):

> "While setting up the game, before putting the character tokens in the bag, either
> remove one Townsfolk and add one Outsider or remove one Outsider and add one
> Townsfolk. During the first night, wake the Godfather. Show them the character tokens
> of all Outsiders in play. Put the Godfather to sleep. If an Outsider dies **during the
> day**, mark them with the **DIED TODAY** reminder. That night, wake the Godfather.
> They point at any player. Put the Godfather to sleep. The chosen player dies — mark
> them with the **DEAD** reminder."

Clarifications:

- **"died today" means during the day.** "Outsiders that die at night don't count."
- **One kill, not two.** "If two Outsiders died today, the Godfather still only kills
  one player tonight."
- **Characters, not players.** "Godfather learns which Outsider *characters* exist, not
  player identities." The tokens are shown one at a time; if no Outsiders are in play,
  show none.
- **Setup:** exactly ±1 Outsider (with the compensating Townsfolk).
- A **non-lethal execution does not trigger it** (the wiki's example: a Devil's Advocate
  protected the executed Outsider, so nothing arms).

Points the wiki page does **not** state, flagged rather than guessed:

- Whether protection stops the Godfather's kill. It is an ordinary night kill, so by the
  standard rules **yes** for the Innkeeper, Sailor, Tea Lady, Fool, Pacifist-style
  effects, and **no** for the Monk and Soldier (both of which protect only from the
  *Demon*). Confidence: high, but derived.
- Whether the Godfather's ability works while drunk/poisoned or dead: no, by the
  standard rules. The app's own guide already says so (`night_guide.json:350`).
- Misregistration: a Recluse dying during the day may register as a Minion or Demon
  (Storyteller's choice) and so may fail to arm the Godfather; a Spy dying during the
  day may register as an Outsider and so may arm it. Storyteller discretion — the app
  should offer the choice, not decide it.
- **The Drunk and the Marionette are Outsiders**, so the Godfather's first-night info
  reveals that they are in play (which is a large tell). That is correct and
  intentional.

Jinx (in the app's data at `night_and_jinxes.json:59`):

> **Godfather × Heretic** — "Only 1 jinxed character can be in play."

## What the app does today

Data:
- `characters.json:631-645` — ability text matches. `setup: true`.
  `reminders: ["Died today", "Dead"]`.
- `night_and_jinxes.json:327` — firstNight index **32**; `:429` — otherNight index
  **56**, after the Assassin (55) and before the Gossip (57). **Correct.**
- `night_guide.json:342-353` — first night has a prepared show card
  `{label:"Show the Godfather", text:"THIS OUTSIDER IS IN PLAY", kind:"token",
  token:"pick"}`; other nights has accurate prose, including the drunk/poisoned case
  and the Died Today token.
- Setup: `Setup.modifierFor` parses `[-1 or +1 Outsider]` into a bounded choice
  (`Setup.kt:121-232`, `choiceDeltas[OUTSIDER] = {-1,+1}`), and
  `GameActionsTest.kt:291-320` already covers both legal counts and rejects others.
  **Setup works.**

Engine:
- `StatusEffects.kt:116-118`:
  ```kotlin
  if (character?.team == Team.OUTSIDER && seats.any { it.characterId == "godfather" && it.alive }) {
      notes += "Godfather kills tonight because an Outsider died today."
  }
  ```
  This is the only Godfather logic in the engine.
- `InfoCalc.supports` (`InfoCalc.kt:29-36`) does **not** include `godfather`, so the
  first-night information is not computed.
- `("godfather","Died today")` and `("godfather","Dead")` are in **neither** expiry
  table (`GameActions.kt:218-242`).

UI:
- First night: the step shows the guide prose plus one "» Show the Godfather" chip,
  which opens `GuideShowDialog` (`NightScreen.kt:366-454`) with `token:"pick"` — the ST
  must **search for and select each Outsider by hand**, one dialog per Outsider, from a
  list of the whole script (in-play ones are sorted first, `NightScreen.kt:406-413`).
- Other nights: `QuickResolutions` `else` branch — Minion, not Demon → **no panel**.
  The tray offers "Died today" and "Dead" chips, neither of which kills.
- Killing for real is the seat sheet's "Died at night".
- The step is emitted every night regardless of whether an Outsider died, whether the
  Godfather is alive, and whether the Godfather is impaired, and blocks the dawn guard.

Storyteller's actual experience: setup is handled. On night 1 they read the grimoire
themselves to find the Outsiders, then search each one up in a dialog. Every later night
they get an identical row asking a question ("did an Outsider die today?") the app
already knows the answer to, and if the answer is yes they must place a token, remember
who, place a second token, and kill a seat in a different screen.

## Defects and gaps

1. **P0 · The "Godfather kills tonight" warning fires on night deaths too.**
   Rules: "Outsiders that die at night don't count." App: `StatusEffects.kt:116` checks
   only `team == OUTSIDER` and `godfather.alive` — never the phase or the day.
   Repro: night 3, Demon kills the Recluse → `DemonKillPanel` shows
   "! Godfather kills tonight because an Outsider died today." → the ST arms a kill that
   the rules do not grant. This is a **rules-breaking false positive** and directly
   creates an extra death.

2. **P0 · The trigger is never actually tracked.** Nothing places
   `("godfather","Died today")`, nothing clears it, and the night step's condition ("if
   an Outsider died today") is left to the ST's memory across a whole day of nominations.
   The app owns `state.deaths` with `day`, `atNight` and `characterIdAtDeath`
   (`GameState.kt:77-90`) — everything needed to compute this exactly — and does not
   use it.

3. **P1 · The first-night information is not computed.** `InfoCalc` supports 26
   characters but not the Godfather, so the one piece of pure look-it-up-in-the-grimoire
   information this character gets is left to the ST, who must then hand-pick each
   token in a search dialog. Repro: night 1 → Godfather step → "» Show the Godfather" →
   a search box, not "Recluse, Drunk — show each".

4. **P1 · No target picker and no kill on the step** (same shape as the Assassin and the
   Tinker): the "Dead" tray chip places a token that leaves the target alive.

5. **P1 · The step is not gated.** It appears and blocks the dawn checklist every night,
   including nights where no Outsider died, where the Godfather is dead, and where the
   Godfather is impaired.

6. **P1 · "Two Outsiders died — still only one kill" is not enforced or explained.**

7. **P2 · Neither Godfather token expires.** `("godfather","Died today")` should be swept
   at dawn once the Godfather has acted; `("godfather","Dead")` likewise once announced.

8. **P2 · Non-lethal executions are indistinguishable from lethal ones.** Once the
   execution flow gains a `Survives` outcome (see `devilsadvocate.md`), the Godfather
   trigger must key off an actual `DeathRecord`, not off the execute button.

9. **P2 · Misregistration is not offered.** A Recluse or Spy dying during the day should
   prompt the ST: "Does the Recluse register as an Outsider for the Godfather?"

10. **P3 · The setup choice is not surfaced at the right moment.** The bag validator
    accepts either count, but nothing on the first-night Godfather step says "you chose
    +1 Outsider this game" — the guide text (`night_guide.json:344`) only says "Remember
    the Godfather changed setup by plus or minus one Outsider."

## Proposed behaviour (spec)

### First night

- **when:** first night, Godfather **alive**.
- **targets:** none.
- **information (new `InfoCalc` branch):**

```kotlin
"godfather" -> {
    val outsiders = state.players
        .filter { !it.isTraveller }
        .mapNotNull { it.characterId }
        .filter { lookup(it)?.team == Team.OUTSIDER }
        .distinct()
    InfoResult(
        headline = if (outsiders.isEmpty()) "No Outsiders are in play"
                   else "${outsiders.size} Outsider${...}: ${outsiders.joinToString { lookup(it)!!.name }}",
        detail = "Show each token in turn. Do NOT reveal who holds them.",
        caveats = buildList {
            if (impaired) add("POISONED/DRUNK — show a false set of Outsider tokens instead.")
            if (recluseInPlay) add("The Recluse may register as a Minion or Demon — you may omit or substitute them.")
            if (spyInPlay) add("The Spy may register as an Outsider — you may add them to the set.")
            if (drunkInPlay || marionetteInPlay) add("The Drunk/Marionette are Outsiders: showing them is a large tell, but it is correct.")
        },
    )
}
```

  Note `InfoCalc.targetsNeeded("godfather") == 0`.

- **shows:** a chip per Outsider, pre-filled — `ShowCard.CharacterCard("THIS OUTSIDER IS
  IN PLAY", id)` — plus a "show all in sequence" option. The `token:"pick"` search
  dialog stays available as an override for misregistration.
- **visibility:** the Godfather only. Nothing else changes.

### Arming the kill (the P0 fix)

Compute, do not remember. At the DAY→NIGHT transition (or as a derived function
evaluated when the night sheet is built):

```kotlin
fun godfatherArmed(state: GameState, lookup: (String) -> Character?): List<DeathRecord> =
    state.deaths.filter { d ->
        d.day == state.cycle - 1 &&           // the day that just ended
        !d.atNight &&                          // DAYTIME deaths only
        !d.resurrected &&
        (d.characterIdAtDeath ?: state.player(d.playerId)?.characterId)
            ?.let(lookup)?.team == Team.OUTSIDER
    }
```

(`DeathCause.EXECUTION` and `DeathCause.STORYTELLER` both qualify, matching the wiki's
"If an Outsider dies during the day" — a Storyteller-killed Tinker counts.)

When this is non-empty at dusk, place `PlacedReminder("godfather","Died today")` on each
qualifying seat (visual parity with the physical grimoire) and add a day/night briefing
line: "**An Outsider died today (Sam, Tinker) — the Godfather kills tonight.**"

Fix `StatusEffects.kt:116-118` to only emit its note when the death being contemplated
is a **daytime** death:

```kotlin
if (character?.team == Team.OUTSIDER && state.phase == Phase.DAY &&
    seats.any { it.characterId == "godfather" && it.alive }) { … }
```

and reword it to "If this Outsider dies **today**, the Godfather kills tonight."

### Other nights

- **when:** other nights, index 56 (unchanged).
- **wake condition:** `godfatherArmed(...)` is non-empty **AND** the Godfather is alive.
  Otherwise the step renders muted with the reason ("no Outsider died today" / "the
  Godfather is dead") and is **auto-ticked, non-blocking**.
- **targets:** exactly 1, any player (the wiki says "any player" — including
  themselves and including dead players, though the latter is pointless; sort alive
  first). **Exactly one, even if two Outsiders died** — state this on the panel.
- **the panel:** a `DemonKillPanel`-shaped resolver that:
  - shows the impairment line when
    `StatusEffects.isImpaired(state, lookup, godfather)`:
    "! The Godfather is drunk/poisoned — let them point, nobody dies."
  - shows `StatusEffects.deathNotes(target)` with the **correct** semantics for a
    non-Demon night kill: Innkeeper / Sailor / Tea Lady / Fool **do** protect;
    **Monk ('Safe') and Soldier do NOT** — the Godfather is not the Demon. This must be
    an explicit line, because `deathNotes` currently phrases both as blanket protection
    (`StatusEffects.kt:66, 74`).
  - confirms with `kill(target, DeathCause.OTHER_NIGHT_DEATH)` +
    `addReminder(target, PlacedReminder("godfather","Dead"))`, and offers "No kill".
- **expiry:** add `"godfather" to "Died today"` and `"godfather" to "Dead"` to
  `EXPIRES_AT_DAWN`.
- **deferred effects:** the death is announced at dawn with the others, cause unstated.
- **visibility:** nothing shown to the Demon or the other Minions.
- **day-time inputs to record:** for a Recluse or Spy dying during the day, a one-tap
  "registers as an Outsider for the Godfather? yes / no" recorded on the `DeathRecord`
  (a new `registersAsTeam: Team?` field) so the arming computation is deterministic.

### UI text

- First night: "Wake the Godfather. Show each Outsider token in play, one at a time:
  **Recluse, Drunk**. Do not say who holds them."
- First night, none: "No Outsiders are in play — show the Godfather nothing (or a
  'zero' signal)."
- Other nights, armed: "**Sam (Tinker) died today.** Wake the Godfather; they point at
  one player, who dies. One kill only, even if two Outsiders died."
- Other nights, not armed: "No Outsider died today — the Godfather does not wake."
- Impaired: "! The Godfather is drunk/poisoned. Let them point, then kill nobody."

### Data changes

- `GameActions.kt:218-225` — add `"godfather" to "Died today"` and
  `"godfather" to "Dead"` to `EXPIRES_AT_DAWN`.
- `night_guide.json:344` — the first-night `shows` entry should become a computed list
  rather than a single `token:"pick"` card; keep the pick card as a fallback.
- `night_guide.json:350` — replace "(execution or any other daytime death)" with the
  sharper "any death during the **day** — execution, a Storyteller-killed Tinker, a
  Witch curse. Deaths at **night** do not count." and add "Only one kill, even if two
  Outsiders died."
- No `characters.json` or night-order changes.

## Tests to add

1. `godfather arms only on daytime outsider deaths`
   Given an Outsider executed on day 2. When the night-3 sheet is built. Then the
   Godfather step is armed and the seat holds `("godfather","Died today")`.
   Given instead an Outsider killed by the Demon on night 3. When the night-4 sheet is
   built. Then the Godfather step is **not** armed. **Fails today**
   (`StatusEffects.kt:116`).
2. `a Storyteller-killed Tinker during the day arms the godfather`
   Given `kill(tinker, DeathCause.STORYTELLER)` while `phase == DAY`. Then the
   Godfather is armed the following night.
3. `a prevented execution does not arm the godfather`
   Given an Outsider holding `("devilsadvocate","Survives execution")` who is executed.
   Then no `DeathRecord` exists and the Godfather is not armed.
4. `two outsider deaths still grant one kill`
   Given two Outsiders died on day 2. When the night-3 step resolves with a target.
   Then exactly one `DeathRecord` is added and the step becomes done.
5. `godfather first-night info lists every outsider character`
   Given a Recluse and a Drunk in play. Then
   `InfoCalc.compute(data, state, "godfather", godfatherId)` returns a headline naming
   both, and no player names. **Fails today** (`godfather` is not in `supports`).
6. `impaired godfather gets a caveat and kills nobody`
   Given the Godfather is poisoned. Then the info result carries a POISONED caveat and
   the night panel's confirm button is replaced by "nobody dies".
7. `dead godfather does not wake`
   Given the Godfather is dead and an Outsider died today. Then the step is
   non-blocking and shows "the Godfather is dead".
8. `godfather tokens are swept at dawn`
   When `advancePhase` NIGHT→DAY after the Godfather kills. Then no seat holds
   `("godfather","Died today")` or `("godfather","Dead")`.
9. `godfather setup accepts either outsider count` — already covered by
   `GameActionsTest.kt:291-320`; keep.
10. `monk and soldier do not protect against the godfather`
    Given the target holds `("monk","Safe")` and is the Soldier. When the Godfather's
    kill resolves. Then the target is dead. Given instead
    `("innkeeper","Protected")`. Then the target lives.
