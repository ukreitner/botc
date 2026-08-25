# Sailor (sailor) — Bad Moon Rising Townsfolk

## Official rules (sources)

Sources:
- https://wiki.bloodontheclocktower.com/Sailor
- https://wiki.bloodontheclocktower.com/Glossary ("Drunk", "Dusk", "Alive")

**Current ability text (matches `characters.json`):**

> "Each night, choose an alive player: either you or they are drunk until dusk. You can't die."

**Summary (wiki):** "The Sailor is either drunk or making someone else drunk. While sober, the
Sailor cannot die."

**How to run (wiki, quoted):**

> "Each night, wake the Sailor. They point at any player. Put the Sailor to sleep. Either the Sailor
> or this chosen player becomes **drunk**—mark them with the **DRUNK** reminder."

**Key mechanics (wiki):**

- "Drunkenness expires at dusk (the following day)" — placed on night N, removed at the dusk that
  ends day N. One dusk.
- **"You can't die" is conditional on being sober**, and it covers **execution** as well as night
  deaths: *"If the sober Sailor would die, the Sailor remains alive. If the sober Sailor is
  executed, declare that this player is executed but remains alive."*
- **"This protection does NOT apply if the Sailor is drunk."** (And, by the Glossary's "Drunk"
  entry, the same holds if the Sailor is poisoned — a poisoned player has no ability.)
- "If a dead player is chosen, the Storyteller prompts for a new choice" — the target must be alive.
- "When choosing non-Townsfolk, the Storyteller typically makes the Sailor drunk instead" — the
  **Storyteller** decides which of the two is drunk, every night, and this is the Sailor's main
  balancing lever.

**Timing:** every night including the first. In the bundled order the Sailor sits at `firstNight[20]`
and `otherNight[9]` — very early, before every killing role, so the Sailor's own drunkenness (if the
ST chooses it) is already in place when the Demon acts that night.

Derived rules that matter:

- **The Sailor stays alive, they do not "survive the attack" invisibly** — for a night kill the
  Demon simply does not kill them; for an execution the ST declares "executed, but remains alive"
  without saying why.
- **The Assassin** ("they die, even if for some reason they could not") kills the sober Sailor.
- **Uncertain:** whether the Sailor may point at *themselves*. The ability says "choose an alive
  player"; the wiki says "They point at any player" and does not exclude self. It is degenerate
  (the Sailor is drunk either way), so allow it and default the ST's drunk-choice to the Sailor.
- **Jinxes:** none for the Sailor in the wiki or in the app's data.

## What the app does today

- `characters.json` — ability, first/other night reminders and `reminders: ["Drunk"]` all correct.
  **Works.**
- `night_and_jinxes.json` — `firstNight[20]`, `otherNight[9]`. **Works.**
- `night_guide.json` (sailor → first and other) — good prose, and it already states
  "While sober, the Sailor cannot die" and "if the Sailor is drunk, they can die".
  The first-night entry adds "If the Sailor is already drunk or poisoned when acting, neither player
  becomes drunk from this choice" — correct.
- `GameActions.kt:233` — `("sailor","Drunk")` in `EXPIRES_AT_DUSK`, swept on DAY→NIGHT
  (`GameActions.kt:261`). **Works** — one dusk, exactly right.
- `NightScreen.kt:308-354` — the tray places the Drunk token; `allReminders.count("Drunk") == 1` so
  it uses `placeExclusiveReminder` (`GameActions.kt:194-201`), which correctly *moves* the token
  each night. **Works.**
- `StatusEffects.kt:36-46` — `isImpaired` picks up the "Drunk" label on whoever holds it. **Works.**
- `StatusEffects.kt:73`:
  ```kotlin
  if (id == "sailor" && player.alive) notes += "The Sailor can't die."
  ```
  This is the whole of the Sailor's protection logic, and it is the line the brief flagged.
- `SeatSheet.kt:256-268` — the string "can't die" matches the protection filter, so killing the
  Sailor *from their seat* raises the "might be protected" dialog. `DemonKillPanel`
  (`NightScreen.kt:588-590`) prints the note but the kill button stays enabled
  (`:625-633`). `DayScreen`'s Execute buttons show nothing at all.
- No Sailor resolver in `QuickResolutions` (`NightScreen.kt:470-524`); the ST places the token by
  hand and decides who gets it with no prompt.

## Defects and gaps

1. **P0 · "The Sailor can't die" is unconditional — a drunk or poisoned Sailor is still reported as
   immortal.**
   Rules: the protection is void while the Sailor is drunk or poisoned. App: `StatusEffects.kt:73`
   checks only `player.alive`. Repro: night 2, make the Sailor themselves drunk (which the ST does
   roughly half the time — it is the core of the character), open the Demon step, tap the Sailor —
   the app prints *"! The Sailor can't die."* and, from the seat sheet, opens a dialog whose
   dismissive default is **"Death prevented"** (`SeatSheet.kt:303-305`). The Sailor should die.
   **This is the highest-impact single-line bug in this scope: the app tells the Storyteller to
   break the rules in the Demon's favour's opposite direction.**
   Fix: `if (id == "sailor" && player.alive && !isImpaired(state, lookup, player))`.

2. **P0 · The execution path never consults the Sailor's protection.**
   Rules: "If the sober Sailor is executed, declare that this player is executed but remains alive."
   App: `DayScreen.kt:111-114` and `:350-357` and `GameShell.kt:599-604` call
   `viewModel.kill(id, DeathCause.EXECUTION)` with no `deathNotes` lookup and no alternative
   outcome. Repro: put a sober Sailor on the block, tap Execute — they die.

3. **P0 · The Demon kill panel offers the kill anyway.**
   Even sober, `DemonKillPanel` prints the note and still enables **"{name} dies"**
   (`NightScreen.kt:625-633`). A one-tap rules break.

4. **P1 · No Sailor resolver: the nightly Storyteller decision is unprompted.**
   The whole character is "the ST chooses who is drunk". The app never asks; the ST must remember
   to place the token and must remember that they get the choice at all (the guide text is behind
   an expand). Every night, including night 1.

5. **P1 · The target's alive-ness is not enforced.**
   Rules: "If a dead player is chosen, the Storyteller prompts for a new choice." App: the tray's
   seat list (`NightScreen.kt:314-352`) includes dead players, dimmed but selectable.

6. **P1 · No record of "who was drunk last night".**
   The Sailor may repeat, but the ST loses the previous night's choice at dusk. A "last night: {name}"
   line costs nothing and answers a table question every night.

7. **P2 · `deathNotes` does not distinguish "blocks" from "warns".**
   `SeatSheet.kt:258-262` does a substring match on note text (`"can't die"`, `"Safe"`, `"Fool"` …)
   to decide whether to show the confirmation dialog. It is a stringly-typed filter over
   human-readable prose; the Assassin bypass cannot be expressed at all.

8. **P2 · The step is offered for a dead Sailor** (`NightOrder.kt:142-178` has no alive filter) with
   the full tray.

9. **P3 · Redundant instruction.** `otherNightReminder` begins "The previously drunk player is no
   longer drunk", but `advancePhase` already swept that token at the preceding dusk
   (`GameActions.kt:233, 261`).

10. **P3 · The Sailor's protection is never explained to the ST at the moment they choose.**
    Making the Sailor drunk is exactly the act that removes their immortality for the night; the
    step should say so out loud.

## Proposed behaviour (spec)

### Night action

- **when:** both first and other nights. Wake condition: `holder.alive`.
- **targets:** exactly 1. Constraint: **alive**; self allowed (default the drunk-choice to the
  Sailor if they pick themselves). Dead seats are disabled with the badge *"dead — pick again"*.
  Sort: alive players, Sailor last-but-selectable.
- **immediate effects** — `GameActions.sailorChoose(state, sailorId, targetId, drunkId, lookup)`:
  - if `StatusEffects.isImpaired(sailor)` **before** acting (e.g. Poisoner got there first, or a
    Minstrel effect is running): place **no** token, show *"The Sailor is already drunk/poisoned —
    their ability does nothing tonight, and they can die."*;
  - else `placeExclusiveReminder(drunkId, PlacedReminder("sailor","Drunk"))` where `drunkId` is
    either `sailorId` or `targetId`, chosen by the **Storyteller** in the step.
- **deferred effects:** none; the standing "can't die" is derived, not a token.
- **expiry:** `("sailor","Drunk")` → `EXPIRES_AT_DUSK` (already correct). Never `EXPIRES_AT_DAWN`.
- **information:** none computed for the Sailor. The chosen player is told nothing.
- **visibility:** nothing shown to the Demon/Minions.
- **day-time inputs:** none.
- **standing protection (the part that must become enforcement):**
  ```
  sailorCannotDie(player) = player.characterId == "sailor"
                         && player.alive
                         && !StatusEffects.isImpaired(state, lookup, player)
  ```
  Applies to **every** death cause: `DEMON`, `OTHER_NIGHT_DEATH`, `EXECUTION`, `STORYTELLER`.
  In the `DeathNote` model proposed in the Innkeeper and Pacifist files this is a `BLOCKS` note for
  all causes, downgraded to `IGNORED_BY_ASSASSIN` when the source is the Assassin.
  For `EXECUTION` it must feed the shared execution dialog's **forced** "Executed, but survives"
  outcome attributed to `"sailor"` — never the optional Pacifist offer.
- **interactions:**
  - **Assassin** kills the sober Sailor.
  - **Innkeeper** may protect the Sailor (redundant while sober; meaningful while drunk).
  - **Minstrel** global drunkenness ⇒ the Sailor is drunk ⇒ mortal. Falls out of the `isImpaired`
    change for free.
  - **Vortox / No Dashii / Poisoner / Courtier / Sweetheart** — all already funnel through
    `isImpaired`, so the fix covers them.
  - **Zombuul / Godfather / Gossip / Moonchild / Pukka** night kills — all blocked while sober.
  - **Gambler** — a Sailor who is also gambling is not a thing; but a *sober Sailor* who would die
    from any self-inflicted source still does not die.
  - **Lycanthrope / Riot / Leviathan** — not jinxed with the Sailor; treat the Riot's "nominees die"
    as a death that a sober Sailor survives, and flag it to the ST rather than deciding silently.

### UI text the step should display

- Header: **"Sailor — they point at an alive player. You choose who is drunk until dusk tomorrow."**
- Two chips after the pick: **"{target} is drunk"** (default when the target is a Townsfolk) ·
  **"{sailor} is drunk"** (default when the target is not a Townsfolk, per the wiki's guidance).
- Under the choice: **"While sober, the Sailor can't die — including by execution. Make the Sailor
  drunk and they can be killed tonight and executed tomorrow."**
- If already impaired: **"! The Sailor is already drunk/poisoned — nothing happens tonight, and
  they can die."**
- Context line: **"Last night: {name} was drunk."**
- On the death/execution dialogs: **"{name} is the sober Sailor — they can't die. Declare
  'executed, but remains alive'. Do not say why."** and, when the source is the Assassin:
  **"…but the Assassin kills even if they could not."**

### Data changes

- `characters.json` — none.
- `night_guide.json` — keep; append to both entries *"The Sailor can't die while sober — this
  includes execution. If you make the Sailor drunk, they lose that protection until dusk."*
- `night_and_jinxes.json` — none.

## Tests to add

1. `a sober sailor blocks every death cause`
   Given an alive Sailor with no impairing reminder. Then
   `deathNotes(state, lookup, sailorId, cause)` contains a `BLOCKS` note for `DEMON`,
   `OTHER_NIGHT_DEATH`, `EXECUTION` and `STORYTELLER`.

2. `a drunk sailor blocks nothing`  ← **fails today**
   Given the Sailor holds `("sailor","Drunk")`. Then `deathNotes(...)` contains **no** Sailor
   protection note for any cause. (Today `StatusEffects.kt:73` still emits "The Sailor can't die.")

3. `a poisoned sailor blocks nothing`  ← **fails today**
   Given the Sailor holds `("poisoner","Poisoned")`. Same expectation as (2).

4. `a sailor drunk by the minstrel effect blocks nothing`
   Given a running Minstrel global effect (see minstrel.md). Then no Sailor protection note.

5. `the sailor's drunk token moves rather than accumulates`
   Given the token on A on night 2. When placed on B on night 3. Then exactly one
   `("sailor","Drunk")` exists in the whole grimoire.

6. `the sailor's drunk token expires at dusk, not dawn`
   Given the token placed on night 2. When `advancePhase` (dawn) the token remains and the holder is
   still impaired all through day 2. When `advancePhase` (dusk) it is gone.

7. `the sailor may not choose a dead player`
   `sailorTargets(state)` excludes dead seats (or `sailorChoose` with a dead target returns the
   state unchanged plus a validation message).

8. `a sober executed sailor produces a survived execution record`
   Given a sober Sailor on the block. When the execution dialog resolves. Then
   `state.executions` holds `ExecutionRecord(day, sailorId, SURVIVED, preventedBy = "sailor")`,
   the Sailor is alive, and `state.deaths` is unchanged.

9. `the assassin kills the sober sailor`
   `deathNotes(..., source = "assassin")` returns `IGNORED_BY_ASSASSIN` for the Sailor note and the
   kill proceeds.

10. `an impaired sailor's night choice places no token`
    Given the Sailor is already poisoned when their step runs. When `sailorChoose(...)`.
    Then no `("sailor","Drunk")` token exists anywhere.
