# Grandmother (grandmother) — Bad Moon Rising Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Grandmother> (fetched 2026-08-25).

Current ability text:

> "You start knowing a good player & their character. If the Demon kills them, you die too."

How to Run (wiki):

- **At setup:** *"Mark a good character with the GRANDCHILD reminder."* The Grandchild is a
  good player — a Townsfolk or Outsider. The Grandchild does **not** learn about the
  Grandmother.
- **First night:** *"wake the Grandmother, show the Grandchild's character token, point to that
  player, then return them to sleep."*
- **Other nights:** *"If only the Demon kills the Grandchild, mark the Grandmother DEAD."*

Key rule and edge cases:

- *"The Grandmother dies only when the Demon kills the Grandchild. Execution or other causes
  of death do not trigger this."* Examples given:
  - Demon kills Julian the Professor (the Grandchild) → the Grandmother dies.
  - Lewis the Gambler (the Grandchild) dies **to his own Gambler ability** → the Grandmother
    **stays alive**.
  - The Demon kills Sarah the Tinker (the Grandchild), but the Grandmother is **drunk** (Sailor
    effect) → the Grandmother **stays alive**. So the Grandmother's own impairment is judged
    **at the moment the Grandchild dies**.
- A drunk/poisoned Grandmother on the first night may be shown a **wrong player and/or wrong
  character**.
- The Grandmother does not wake on subsequent nights — the other-night step is pure
  Storyteller bookkeeping (like the Gossip's).

Jinxes (verbatim from the page's Related Jinxes section):

- **Leviathan:** "If the Leviathan is in play and the Grandchild dies by execution, evil wins."
- **Riot:** "If Riot is in play and the Grandchild dies by execution, evil wins."

Night order: the Grandmother's other-night slot must come **after every kill source** so the
Storyteller knows whether the Grandchild died to the Demon tonight. In the app's data she sits
at index 71, after all Demons (36–54), Assassin (55), Godfather (56), Gossip (57) and the
Professor's resurrection (63). **Correct.**

## What the app does today

- `characters.json:445-456` — ability text matches. `firstNightReminder`: "Show the marked
  character token. Point to the marked player." `otherNightReminder`: "If the Grandmother's
  grandchild was killed by the Demon tonight: The Grandmother dies."
  `reminders: ["Grandchild", "Dead"]`. **Works.**
- `night_and_jinxes.json:348` (firstNight 53) and `:444` (otherNight 71) — positions correct.
- `night_guide.json:252-262` — good prose for both nights, including the drunk/poisoned caveat
  on night 1 and "This does not trigger if the grandchild died by other means, or if the
  Grandmother is drunk or poisoned when the grandchild is killed."
- `night_guide.json:256-261` — a prepared show card
  `{"label": "Show the Grandmother", "kind": "token", "text": "THIS IS YOUR GRANDCHILD",
  "token": "pick"}`, which opens the searchable character grid and shows the chosen token
  full-screen (`NightScreen.kt:366-454`). **Works and is good.**
- `InfoCalc.kt:24,65,376-384` — `grandmother` needs 1 target and reuses `revealCharacter`,
  producing `"Mina is the Gambler"` plus `misregistrations` (Spy/Recluse) and `commonCaveats`
  (impairment + Vortox, `InfoCalc.kt:158-166`). Covered by `InfoCalcTest.kt:154`.
- `StatusEffects.kt:122-127` — a death note:

  ```kotlin
  val grandmothers = seats.filter { it.characterId == "grandmother" && it.alive }
  if (grandmothers.isNotEmpty() && player.reminders.any { it.label.equals("Grandchild", true) }) {
      notes += "Grandmother dies too if the Demon killed her grandchild."
  }
  ```

  Rendered in `SeatSheet.kt:250-255` and `NightScreen.kt:588-590`.

Everything else — placing the Grandchild token, defaulting the first-night target, deciding
whether the Grandmother dies, and actually killing her — is manual.

## Defects and gaps

1. **P0 · The Riot jinx text in the data is wrong, and it is a win condition.**
   `night_and_jinxes.json:230-231` says *"If Riot kills the Grandchild, the Grandmother dies
   too."* The official jinx is *"If Riot is in play and the Grandchild dies by execution, evil
   wins."* A storyteller reading the app's text in a Riot game would run the wrong rule and
   miss an evil victory. Repro: BMR/homebrew script with Riot + Grandmother, open the
   Grandmother's seat sheet (`SeatSheet.kt:223-234`) and read the jinx line.

2. **P0 · The Leviathan/Grandmother jinx is missing entirely** from `night_and_jinxes.json`,
   so in a Leviathan game the app never mentions that executing the Grandchild wins the game
   for evil.

3. **P1 · The Grandmother's death is never automated, only hinted at.** The app has everything
   it needs: `state.deaths` carries `DeathRecord(playerId, day, atNight, cause,
   abilityImpairedAtDeath)` (`GameState.kt:69-86`), and the Grandchild is identified by a
   reminder token. At the Grandmother's other-night step the answer to "does she die tonight?"
   is a pure function of state, and the step instead renders the conditional sentence from
   `characters.json:452` and stops. Repro: night 3, Demon kills the Grandchild via
   `DemonKillPanel`; open the Grandmother step — it says "If the grandchild was killed by the
   Demon tonight: The Grandmother dies" and offers no button, no answer, and no indication that
   this is in fact what happened.

4. **P1 · The death note ignores the death cause and the Grandmother's impairment.**
   `StatusEffects.kt:122-127` fires whenever the target holds the Grandchild token and any
   Grandmother is alive — including when the storyteller is about to press **"Executed"** or
   **"Other death"** in the seat sheet (`SeatSheet.kt:274-279`), where the rule explicitly does
   **not** apply. It also never checks
   `StatusEffects.isImpaired(state, lookup, grandmotherSeat)`, so a Sailor-drunk Grandmother is
   still reported as doomed — contradicting the wiki's own worked example.

5. **P1 · The Grandchild is not part of setup validation.**
   `GameActions.validateSetupState` (`GameActions.kt:503-561`) enforces the Drunk's shown
   token, the Lunatic's Demon token, the Marionette's token and neighbour, and *exactly one
   good Fortune Teller red herring* — but has **no Grandmother case**. You can begin the first
   night with a Grandmother in play and no `grandmother:Grandchild` token anywhere, and the app
   will not object. Repro: assign a Grandmother, press "Begin night" — no issue is raised
   (contrast the Fortune Teller at `GameActions.kt:547-559`).

6. **P1 · The first-night info panel does not default to the Grandchild.**
   `InfoCalc.targetsNeeded("grandmother") == 1` (`InfoCalc.kt:24`) and the UI asks the
   storyteller to tap a seat every time (`NightScreen.kt:838-861`), listing **all** players
   including evil ones and the Grandmother herself. The Grandchild token already says who it
   is. The picker should default to the token holder and warn on anything else.

7. **P2 · No constraint that the Grandchild is good and is not the Grandmother.** Nothing
   prevents placing the Grandchild token on a Minion, the Demon, or the Grandmother's own seat.
   `Player.isEvil(lookup)` (`GameState.kt:48-51`) makes the check trivial, and
   `InfoCalc.misregistrations` (`InfoCalc.kt:121-130`) already knows the Spy is the legitimate
   exception (registering as good, so a legal Grandchild).

8. **P2 · Nothing tells the storyteller *at dawn* that two people died and why.** The DAWN
   step is a static string (`NightOrder.kt:59`). A Grandmother death is the classic
   "announce two deaths in the right order" moment and the app gives no summary.

9. **P2 · The `grandmother:Dead` token is not placed automatically**, and the Grandmother's
   death cause has no correct option in the UI (see 10).

10. **P2 · No correct death cause for the Grandmother's own death.** She is not killed by the
    Demon — she dies from her own ability. `DeathCause.OTHER_NIGHT_DEATH` (`GameState.kt:76`)
    is the right value but no UI path produces it (`SeatSheet.kt:270-273` hard-codes `DEMON`).
    **The wiki does not state this explicitly** — flagging as inference, but it matters for
    Sage/Choirboy/Undertaker bookkeeping.

11. **P3 · `night_guide.json:253` says "Choose a good player to be the grandchild"** without
    noting the Townsfolk/Outsider framing or the Spy exception.

## Proposed behaviour (spec)

### Setup

- **when:** setup / before the first night.
- Add to `GameActions.validateSetupState` (`GameActions.kt:503-561`), mirroring the Fortune
  Teller block at `:547-559`:

  ```kotlin
  if (residents.any { it.characterId == "grandmother" }) {
      val grandchildren = state.players.filter { p ->
          p.reminders.any { it.sourceId == "grandmother" && it.label.equals("Grandchild", true) }
      }
      when {
          grandchildren.size != 1 ->
              issues += "Grandmother: mark exactly one good player as the Grandchild"
          grandchildren.single().characterId == "grandmother" ->
              issues += "Grandmother: the Grandchild must be another player"
          grandchildren.single().isEvil(lookup) && grandchildren.single().characterId != "spy" ->
              issues += "Grandmother: the Grandchild must be a good player " +
                        "(the Spy is the one legal exception — they register as good)"
      }
  }
  ```

- The `grandmother:Grandchild` token is exclusive (single copy in `characters.json:453-456`),
  so `placeExclusiveReminder` already prevents duplicates (`GameActions.kt:194-201`). **Works.**

### First night

- **when:** first night only. Wake condition: `holder.alive`.
- **targets:** 1 — but **defaulted to the seat holding `grandmother:Grandchild`**, not chosen
  from scratch. Render it as a pre-selected chip that can be overridden, with any other
  selection flagged `not the marked Grandchild`.
- **immediate effects:** none beyond the token already placed at setup.
- **information:** `"${grandchild.name} is the ${character.name}"` — the **true**
  `characterId`, which `revealCharacter` (`InfoCalc.kt:376-384`) already produces. Show cards:
  the existing `THIS IS YOUR GRANDCHILD` token card, pre-filled with the Grandchild's
  character rather than requiring a search (`night_guide.json:256-261`,
  `NightScreen.kt:405-434`).
- **impaired/false alternative:** when
  `InfoCalc.impairments(state, lookup, grandmotherSeat)` is non-empty, offer a "false info"
  row exactly as the numeric roles get today (`NightScreen.kt:903-930`): a one-tap way to show
  a *different* player and/or a *different* character token. The Grandmother is the one BMR
  role where the lie has two independent axes, so offer both.
- **misregistration:** if the Grandchild is the Spy, keep the `misregistrations` caveat
  (`InfoCalc.kt:121-130`) — the storyteller has deliberately used a legal but sharp option.
  If the Grandchild is the Drunk, warn that showing the "Drunk" token reveals the Drunk.
- **visibility:** nothing to the Demon/Minions/Lunatic. The Grandchild is told nothing, ever.

### Other nights (the trigger)

- **when:** other nights, as a Storyteller-bookkeeping step (the Grandmother is not woken).
  The step renders whenever a `grandmother:Grandchild` token exists **and** a living
  Grandmother holds the character.
- **the derivation (this is the fix):** at this step, evaluate

  ```kotlin
  val grandchild = seat holding grandmother:Grandchild
  val diedTonight = state.deaths.lastOrNull {
      it.playerId == grandchild.id && it.day == state.cycle && it.atNight && !it.resurrected
  }
  val byDemon = diedTonight?.cause == DeathCause.DEMON
  val gmImpaired = StatusEffects.isImpaired(state, lookup, grandmotherSeat)
  ```

  and render exactly one of:

  | state | panel |
  |---|---|
  | no death tonight | `Mina is still alive. Nothing happens.` — tick and move on |
  | death, cause ≠ `DEMON` | `Mina died tonight, but not to the Demon (Gambler). Greta lives.` |
  | death by Demon, Grandmother impaired | `The Demon killed Mina — but Greta is drunk/poisoned, so she does NOT die.` + `[Kill anyway]` override + Mathematician note |
  | death by Demon, Grandmother healthy | **`The Demon killed Mina. Greta dies too.`** + `[Greta dies]` |

  The last case can reasonably be **auto-applied** rather than merely offered — it is a
  deterministic consequence with no Storyteller choice — but it must remain undoable and must
  first run `StatusEffects.deathNotes(state, lookup, grandmotherId)` so a protected Grandmother
  (Sailor, Tea Lady, Fool, Innkeeper) is caught. Recommendation: auto-apply when there are no
  death notes, prompt when there are.
- **immediate effects on confirm** (one undoable update):
  - `GameActions.kill(state, grandmotherId, DeathCause.OTHER_NIGHT_DEATH, lookup)`
    (**not** `DEMON` — flagged as inference above);
  - `placeExclusiveReminder(grandmotherId, PlacedReminder("grandmother", "Dead"))`;
  - auto-tick the step.
- **the impairment timing subtlety:** the wiki judges the Grandmother's drunkenness **at the
  moment the Grandchild dies**, not at her own night slot. Those differ if something between
  the Demon's step (36–54) and the Grandmother's (71) changes her state. `DeathRecord` already
  snapshots `abilityImpairedAtDeath` for the *dying* player (`GameState.kt:82`); the same
  snapshot should be taken for the **Grandmother** at the moment the Grandchild's death is
  recorded — e.g. by having `GameActions.kill` stamp a pending
  `PlacedReminder("grandmother", "Dies at dawn")` (or a `NightChoice`-style record) right
  then, which the Grandmother's step reads. Simplest correct implementation: evaluate
  `isImpaired(grandmother)` inside `kill()` when the victim holds the Grandchild token, and
  store the result.
- **expiry:** `grandmother:Grandchild` is permanent for the whole game — keep it out of both
  expiry tables (`GameActions.kt:218-242`). **Works today.** `grandmother:Dead` is likewise
  permanent.
- **day-time inputs:** none.
- **interactions/jinxes:**
  - **Gossip / Assassin / Godfather / Gambler / Tinker / Moonchild kills** → cause is not
    `DEMON` → the Grandmother lives. This is why `gossip.md` and `gambler.md` insist on
    `OTHER_NIGHT_DEATH`; the two defects compound today.
  - **Professor resurrection** (`NightScreen.kt:499-517`, order 63, before the Grandmother's
    71): if the Grandchild is resurrected before the Grandmother's step, `resurrect` marks the
    `DeathRecord.resurrected = true` (`GameActions.kt:173-181`). The derivation above filters
    on `!it.resurrected`, so a revived Grandchild correctly does not kill the Grandmother.
    **This ordering must be preserved and tested.**
  - **Zombuul** registering as dead: it is the Demon's *kill* that matters, not who looks dead.
  - **Pit-Hag / Barber / Snake Charmer**: the Grandchild token stays with the **seat**;
    `swapCharacters` (`GameActions.kt:99-115`) leaves reminders alone. The Grandmother's
    night-1 information does not update, which is correct — she learned what was true then.
  - **Vortox:** `commonCaveats` already warns that Townsfolk info must be false
    (`InfoCalc.kt:158-166`). **Works.**
  - **Leviathan / Riot:** add/fix the jinxes and surface them at **nomination and execution
    time** via `StatusEffects.nominationWarnings` (`StatusEffects.kt:131-166`) — the trigger is
    the Grandchild being executed, which is a day event, so a night-step mention is useless.

### UI text

- First night: `Grandmother — show Mina's Gambler token, then point at Mina.`
- Other night, nothing: `Mina is alive. Nothing happens tonight.`
- Other night, non-Demon death: `Mina died tonight, but not to the Demon. Greta lives.`
- Other night, trigger: `The Demon killed Mina. Greta dies too — announce both at dawn.`
- Impaired: `Greta is drunk right now, so she does NOT die with her grandchild.`
- Setup guard: `Grandmother: mark exactly one good player as the Grandchild.`

### Data changes

- `night_and_jinxes.json:229-232` — **replace** the Riot text with
  `"If Riot is in play and the Grandchild dies by execution, evil wins."`
- `night_and_jinxes.json` — **add**
  `{"id1": "leviathan", "id2": "grandmother", "reason": "If the Leviathan is in play and the
  Grandchild dies by execution, evil wins."}`
- `night_guide.json:253` — "Choose a good player (Townsfolk or Outsider) to be the grandchild —
  not the Grandmother herself. The Spy is the one evil player who may legally be chosen, since
  they register as good."
- `characters.json:445-456` — no change.

## Tests to add

1. **Setup guard requires exactly one good Grandchild.** Given a BMR game with a Grandmother
   and no `grandmother:Grandchild` token, When `validateSetupState` runs, Then it reports
   "mark exactly one good player as the Grandchild". Given the token on a Minion, Then it
   reports the good-player issue. Given it on the Grandmother's own seat, Then it reports the
   other-player issue. **All fail today** (`GameActions.kt:503-561` has no Grandmother case).

2. **Demon kill of the Grandchild kills the Grandmother.** Given a night-3 state where the
   Grandchild died with `DeathCause.DEMON` this cycle, When the Grandmother resolver runs,
   Then it reports "she dies", and applying it leaves the Grandmother dead with cause
   `OTHER_NIGHT_DEATH` and a `grandmother:Dead` token.

3. **Non-Demon deaths do not trigger.** Given the Grandchild died this cycle with
   `OTHER_NIGHT_DEATH` (Gossip or Gambler), Then the resolver reports "not the Demon" and the
   Grandmother stays alive. Repeat with `EXECUTION` on the preceding day.

4. **Impaired Grandmother survives.** Given the Grandchild is Demon-killed and the Grandmother
   carries `sailor:Drunk`, Then the resolver reports no death by default.

5. **Resurrected Grandchild does not trigger.** Given the Grandchild is Demon-killed on night 3
   and then `resurrect`ed by the Professor (order 63, before the Grandmother's 71), When the
   Grandmother step runs, Then no death is produced. **This is the ordering test — it fails
   today because there is no resolver at all.**

6. **Death from a previous night does not re-trigger.** Given the Grandchild was Demon-killed
   on night 2, When the night-3 Grandmother step runs, Then nothing happens (the derivation is
   scoped to `it.day == state.cycle && it.atNight`).

7. **Death note respects the cause.** Given the Grandchild is about to be **executed**, When
   `StatusEffects.deathNotes` runs, Then it does **not** claim the Grandmother dies too.
   **Fails today** (`StatusEffects.kt:122-127`).

8. **First-night info defaults to the marked Grandchild.** Given the token on seat 5, When the
   Grandmother's first-night step is prepared, Then the default target is seat 5 and the
   headline is "`<name>` is the `<their true character>`".

9. **Jinx data is correct.** Given a script with Riot and Grandmother, Then
   `gameData.activeJinxes` returns the execution/evil-wins text; and given Leviathan and
   Grandmother, Then a jinx is returned at all. **Both fail today.**
