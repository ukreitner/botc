# Virgin (virgin) — Trouble Brewing Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Virgin> (raw wikitext fetched 2026‑08‑25).

**Current ability text (verbatim):**
> "The 1st time you are nominated, if the nominator is a Townsfolk, they are executed immediately."

**Summary bullets (verbatim):**
- "If a Townsfolk nominates the Virgin, then that Townsfolk is executed immediately. **Because
  there can only be one execution per day, the nomination process immediately ends, even if a
  player was about to die.**"
- "Only Townsfolk are executed due to the Virgin's ability. If an **Outsider, Minion, or Demon**
  nominates the Virgin, nothing happens, and voting continues."
- "After being nominated for the first time, the Virgin loses their ability, **even if the
  nominator did not die, and even if the Virgin was poisoned or drunk**."

**How to Run (verbatim):**
> "If the first player to ever nominate the Virgin is a Townsfolk, immediately declare that the
> nominating player is executed. That player **dies**—put a shroud on their character token in
> the Grimoire. **The Virgin loses their ability**—put the Virgin's **NO ABILITY** reminder
> token by the Virgin token. End the nomination process and proceed to the night phase.
> (No one else can be executed today.)
>
> If the first player to ever nominate the Virgin is not a Townsfolk, continue the vote as
> normal. **The Virgin loses their ability**—put the Virgin's **NO ABILITY** reminder token by
> the Virgin token."

**Examples (verbatim):**
- "The Washerwoman nominates the Virgin. The Washerwoman is immediately executed and the day ends."
- "The Drunk, who thinks they are the Chef, nominates the Virgin. The Drunk remains alive, and
  the Virgin loses their ability. Players may now vote on whether or not to execute the Virgin.
  (This happens because the Drunk is not a Townsfolk.)"
- "A dead player nominates the Virgin. The dead, however, cannot nominate. The Storyteller
  declares that the nomination does not count. **The Virgin does not lose their ability.**"

**Storyteller-relevant clarifications from Tips & Tricks:**
- "An Outsider who nominates you will not be executed. Your ability requires your nominator to
  be a **Townsfolk**, not merely good."
- "**You can nominate yourself and trigger your ability**, proving you are the Virgin!"
- "Once your ability activates, the nominator is immediately executed, **ending the day** (even
  if someone else had the votes and was due to be executed)."
- "Your ability only triggers the first time you are nominated. Even if you were poisoned or
  nominated by a non-Townsfolk, **your ability is already spent**."
- "Beware the **Spy**! It is the only evil character that can activate your ability, since it
  registers as a Townsfolk." (Registration is a "might", so it remains an ST choice.)
- "If a Virgin is nominated, but **either** player is the Drunk, the Virgin ability does not
  trigger, since either the Virgin is the Drunk (not the Virgin), or the nominating player is
  an Outsider (not a Townsfolk)."

**Cross-character ruling:** per the Undertaker page's Tips & Tricks,
"A player who dies because of the Virgin is **considered executed** and will react to your
ability" — the Virgin death must be recorded as an execution.

**Travellers:** a Traveller is neither Townsfolk nor Outsider (`Team.TRAVELLER`), so a Traveller
nominating the Virgin does **not** trigger the execution, but the Virgin's ability is still
spent. The wiki does not state this explicitly; it follows from "if the nominator is a
Townsfolk" plus "loses their ability… even if the nominator did not die". **Flagged as inferred,
not quoted.**

**Recluse:** the Recluse "might register as evil & as a Minion or Demon" — never as a Townsfolk
(<https://wiki.bloodontheclocktower.com/Recluse>) — so a Recluse nominating the Virgin never
triggers the execution.

**Jinxes:** none.

## What the app does today

Data
- `characters.json:149-162` — ability text matches the wiki. `reminders: ["No ability"]`,
  both night reminders empty, `setup: false`. Correct.
- `night_and_jinxes.json` — `virgin` absent from both night orders (correct); no jinxes (correct).
- `night_guide.json` — **no `virgin` entry** (the Virgin never wakes, so there is nowhere in the
  app that explains how to run the trigger).

Engine — the only Virgin code in the repository
- `engine/src/main/kotlin/com/clocktower/engine/StatusEffects.kt:152-157`:
  ```kotlin
  if (nominee.characterId == "virgin" &&
      nominee.reminders.none { it.label.equals("No ability", true) }
  ) {
      notes += "Virgin's first nomination: if ${nominator?.name ?: "the nominator"} is a Townsfolk, they are executed immediately."
  }
  ```
  That is the whole implementation: one advisory string, produced by
  `StatusEffects.nominationWarnings`.

UI
- `app/.../screens/DayScreen.kt:154-159` renders those warnings as red text, **only while both
  the nominator and nominee chips are selected in the draft form**. Nothing persists once the ST
  taps **Record** (`DayScreen.kt:217-251`), which only appends a `Nomination` and spends ghost
  votes.
- `DayScreen.kt:131-152` gates the nominator to `p.alive && !hasNominatedToday(...)` and the
  nominee to `p.alive && !hasBeenNominatedToday(...)`. Self-nomination is possible (the two
  chip rows are independent), so the Virgin's self-nomination trick is at least expressible.
- `DayScreen.kt:161-252` then presents the ordinary vote panel: tap voters, see
  `Voting.outcome`, tap Record. There is no branch that suppresses the vote.
- `DayScreen.kt:93-115` shows the "On the block: <name>" banner with an **Execute** button,
  derived from `GameActions.aboutToDie` (`GameActions.kt:296-306`), which reads only nomination
  results. Nothing can clear the block because of a Virgin trigger.
- `GameShell.kt:141-146` — the dusk guard: if someone is still on the block at dusk it offers
  "Execute & begin night". After a Virgin trigger this would execute a **second** player today.
- `NominationResult.WITHDRAWN` exists (`GameState.kt:59`) and is rendered
  (`DayScreen.kt:343`, `GameExtras.kt:72`) but **is unreachable from the UI** — `Voting.outcome`
  (`GameState.kt:143-149`) only ever returns `SAFE`/`ABOUT_TO_DIE`/`TIED`.

What the storyteller actually has to do today, in order:
1. Notice the red warning while the chips are selected.
2. Decide *themselves* whether the nominator is a Townsfolk (the app knows and does not say).
3. Decide *themselves* whether a Spy nominator registers as a Townsfolk.
4. Decide *themselves* whether a poisoned/drunk Virgin's ability fires.
5. Record the nomination anyway (as "safe, 0 votes" — the only representable outcome).
6. Open the nominator's seat → tap **Executed** (`SeatSheet.kt:274-276`).
7. Open the Virgin's seat → **Add reminder** → scroll to Virgin → tap **No ability**.
8. Remember, unaided, that no one else may be executed today, and dismiss/ignore the on-the-block
   banner and the dusk guard if a prior nomination had already passed.

`FullGamePlaytestTest.kt:631-649` scripts exactly this manual sequence
(`NominationResult.WITHDRAWN`, `kill("Ben", DeathCause.EXECUTION)`, `add("Iris","virgin","No ability")`)
— confirming that the test suite encodes the *storyteller* doing the work, not the app.

Works: the warning text is correct and correctly gated on the "No ability" token; nominator and
nominee are restricted to living players (so the "dead nominations don't count" ruling is
enforced by construction); self-nomination is possible.

## Defects and gaps

1. **P0 · Nothing is automated — the trigger is a sentence** — `StatusEffects.kt:152-157`
   produces a hint and stops. No Townsfolk check, no execution, no token, no vote suppression,
   no day-end. Every consequence in the How to Run is manual.
   *Repro:* Day tab → pick a Townsfolk nominator and the Virgin as nominee → the red hint
   appears → tap Record → nothing happens.

2. **P0 · The nominator's team is never evaluated, even though the app knows it** — the warning
   says "if <name> is a Townsfolk" when `viewModel.characterById(nominator.characterId)?.team`
   is one line away. The wiki's own trap (the **Drunk**, an Outsider, who *thinks* they are a
   Townsfolk) is exactly the case where a ST at 1 a.m. gets it wrong.
   The check must read the **true** `characterId`, never `shownCharacterId`/`nightRoleId`.

3. **P0 · A poisoned/drunk Virgin still gets the "they are executed immediately" warning** —
   `nominationWarnings` (`StatusEffects.kt:152-157`) has no impairment check, while
   `StatusEffects.isImpaired` (`:36-46`) is right there and is already used elsewhere in the same
   file. Per the rules a poisoned Virgin's ability **does not fire** (but is still spent). The
   app actively tells the ST to execute someone who should live.
   *Repro:* Poisoner poisons the Virgin on night 2; on day 2 a Townsfolk nominates → the app
   still says "they are executed immediately".

4. **P0 · The ability is not marked spent — in any branch** — the rules spend it on the
   **first nomination, unconditionally**: wrong-team nominator, poisoned Virgin, self-nomination,
   Traveller nominator. Nothing places `PlacedReminder("virgin", "No ability")`. If the ST
   forgets (there is no prompt), the app will happily warn again on the second, third and fourth
   nomination — the "allowed two days in a row for the same person" failure mode the user
   reported for the Devil's Advocate, reproduced here.

5. **P0 · The day is not ended and a second execution is possible** — "Because there can only be
   one execution per day, the nomination process immediately ends, even if a player was about to
   die." `GameActions.aboutToDie` (`:296-306`) keeps returning the previously blocked player, the
   banner keeps its **Execute** button (`DayScreen.kt:111-114`), the per-nomination Execute
   button stays live (`DayScreen.kt:350-357`), and the dusk guard offers to execute them
   (`GameShell.kt:141-146`, `:592-616`).
   *Repro:* Day 3 — nomination A passes with 4 votes (A is on the block). Then a Townsfolk
   nominates the Virgin. Execute the nominator by hand. The banner still reads "On the block: A"
   with an Execute button, and pressing Dusk offers "Execute & begin night".

6. **P1 · The vote is not suppressed** — after a trigger, `DayScreen.kt:161-252` still shows the
   full vote panel for the Virgin nomination. The rules say the nomination ends *before any votes
   are cast*.

7. **P1 · The nomination outcome cannot be recorded correctly** —
   `NominationResult.WITHDRAWN` is display-only; `Voting.outcome` (`GameState.kt:143-149`)
   cannot produce it. The ST must record the Virgin nomination as "safe, 0 votes", which then
   pollutes `highestVotesToday` reasoning and the game log (`GameExtras.kt:65-78`).

8. **P1 · No Spy registration prompt** — the Spy is "the only evil character that can activate
   your ability", and it is a "might", i.e. a live ST decision at that instant. The app offers
   no prompt, no default, and no record for later consistency (Undertaker, Washerwoman, Fortune
   Teller all need the same Spy registration).

9. **P1 · The Virgin execution's death cause is up to the ST** — the Undertaker ruling requires
   it to be `DeathCause.EXECUTION`. Today the ST might reach for "Other death"
   (`SeatSheet.kt:277-279`), silently breaking the Undertaker's information the same night.

10. **P2 · No day-start briefing** — "the Virgin still has their ability" and "the Virgin's
    ability is spent" are standing facts the ST must hold. `GameShell.kt:126-168` has no
    briefing surface at all.

11. **P2 · Travellers are not handled** — a Traveller nominator is `Team.TRAVELLER`, not
    Townsfolk, so no execution but the ability is spent. Not modelled, not stated.

12. **P2 · No `night_guide` / reference entry** — nothing in the app explains the Virgin's
    procedure, including the counter-intuitive parts (day ends, ability spent even on a miss).

13. **P3 · No record of the day the Virgin was "used"** — the log
    (`GameExtras.kt:44-106`) shows the execution and the nomination separately, with no causal
    link, so post-game review cannot see that the Virgin fired.

## Proposed behaviour (spec)

The Virgin needs a **nomination-time interceptor**: a hook that runs when the ST declares a
nomination, before the vote panel appears. Design it generically — the Witch curse
(`StatusEffects.kt:142-147`), the Golem (`:148-150`) and the Fearmonger (`:158-160`) all need
the same seam, and today they are all advisory strings too.

**Structured behaviour**

- **when:** DAY phase, at the moment a nomination is *declared* (nominator + nominee chosen),
  **before** any voting UI. Not a night ability; the Virgin never wakes.
- **trigger condition:**
  ```
  nominee.characterId == "virgin"                        // TRUE character, not shownCharacterId
  && nominee.alive
  && state.nominations.none { it.nomineeId == nominee.id && !it.isExile }   // 1st time EVER, not just today
  && nominee.reminders.none { it.label.equals("No ability", true) }
  ```
  Note the "1st time you are nominated" is **game-lifetime**, not per-day — derive it from the
  full `nominations` history, and treat the `"No ability"` token as the authoritative record so
  a hand-placed token still works.
- **resolution, computed by the engine and shown to the ST as a decision card:**
  1. `abilityWorks = !StatusEffects.isImpaired(state, lookup, virgin)` and
     `virgin.characterId == "virgin"` (a Drunk-shown-as-Virgin never works).
  2. `nominatorTeam = lookup(nominator.characterId)?.team` — **true** character.
     - `TOWNSFOLK` → fires.
     - `OUTSIDER` (incl. the **Drunk**), `MINION`, `DEMON`, `TRAVELLER` → does not fire.
     - `nominator.characterId == "spy"` → present an explicit choice
       *"Spy registers as a Townsfolk (executed)"* / *"Spy registers as evil (nothing happens)"*,
       defaulting to whatever registration was recorded earlier this game.
     - `nominator.characterId == "recluse"` → never fires; say so ("the Recluse cannot register
       as a Townsfolk").
  3. If `abilityWorks && nominatorTeam == TOWNSFOLK` → **fire**.
- **immediate effects when it fires (one confirmed, undoable transaction):**
  1. `GameActions.kill(state, nominatorId, DeathCause.EXECUTION, lookup)` — run
     `StatusEffects.deathNotes` first in a confirmation dialog (Devil's Advocate "survives
     execution", Tea Lady, Fool, Sailor, Mayor all apply to a Virgin execution).
  2. Auto-place `PlacedReminder("undertaker", "Died today")` on the nominator if an Undertaker
     is in play (see `undertaker.md`).
  3. `GameActions.placeExclusiveReminder(state, virginId, PlacedReminder("virgin", "No ability"))`.
  4. Record the nomination with `result = NominationResult.WITHDRAWN`, `votes = 0`,
     `voterIds = emptyList()` — and make `WITHDRAWN` reachable (see data changes).
  5. Set a new `executionUsedToday: Boolean` (or derive it: any `EXECUTION` death with
     `day == state.cycle`) so the day's execution slot is closed.
- **immediate effects when it does NOT fire:**
  1. `placeExclusiveReminder(virginId, PlacedReminder("virgin", "No ability"))` — **still spent**.
  2. Continue to the normal vote panel unchanged.
  3. Show the reason privately: `"<Nominator> is an Outsider — nothing happens."` /
     `"<Virgin> is poisoned — nothing happens."` / `"<Nominator> is a Traveller — nothing happens."`
- **deferred effects / day-end:** once `executionUsedToday` is true:
  - `GameActions.aboutToDie` must return `null` (or the banner must be suppressed) — nobody else
    can be executed today, "even if a player was about to die";
  - the per-nomination **Execute** buttons (`DayScreen.kt:350-357`) must be disabled;
  - the dusk guard (`GameShell.kt:141-146`, `:592-616`) must **not** offer an execution;
  - the Day screen should show a banner: `"The Virgin's ability ended the day — no further
    execution today."` and the Dusk button should read `"Dusk (day is over)"`.
  - Whether further *nominations* may be declared is ST discretion; the wiki says "End the
    nomination process and proceed to the night phase", so default to closing the New-nomination
    card with an override link ("continue nominating anyway").
- **expiry:** `virgin:"No ability"` **never** expires — keep it out of `EXPIRES_AT_DAWN` and
  `EXPIRES_AT_DUSK` (`GameActions.kt:218-242`), and out of `GameActions.revive`/`resurrect`
  (which do not touch reminders — correct).
- **information:** none is given to any player privately. The execution is public and immediate.
- **visibility:** nothing shown to the Demon/Minions/Lunatic.
- **day-time inputs the app must let the ST record:** the nomination itself (nominator, nominee),
  the Spy registration decision, and the outcome. Add these to the game log as one linked entry:
  `"D3 — <Nominator> nominated Virgin <Name>; ability fired; <Nominator> executed; day ended."`
- **interactions to handle explicitly:**
  - **Drunk as nominator** — Outsider, no execution, Virgin still spent (wiki example 2).
  - **Drunk as the Virgin** (`characterId = "drunk"`, `shownCharacterId = "virgin"`) — never
    fires; place the "No ability" token on that seat anyway so the ST is not prompted again.
  - **Poisoner** — does not fire, still spent.
  - **Spy** — the only evil trigger; ST choice, recorded.
  - **Recluse** — never fires.
  - **Traveller nominator** — no execution, still spent (inferred; label it as such in the UI).
  - **Self-nomination** — the Virgin is a Townsfolk, so it fires on themselves: the Virgin is
    executed. Allow it and warn clearly.
  - **Dead nominator** — impossible in the UI (`DayScreen.kt:135-138`), and correct per the wiki
    (the nomination doesn't count and the ability is **not** spent).
  - **Undertaker** — the Virgin death is an execution and must be visible to the Undertaker.
  - **Devil's Advocate / Fool / Sailor / Tea Lady / Mayor** — the Virgin execution goes through
    the same protection confirmation as any other execution; if the nominator survives, the day
    still ends (an execution occurred) and the Undertaker still learns nothing (no death).
  - **Minstrel** — if the nominator was a Minion, it was not a Townsfolk, so this cannot arise.
  - **Golem / Witch curse** — the same nomination interceptor; if a Witch-cursed Townsfolk
    nominates the Virgin, both effects apply (they die from the curse *and* the Virgin fires —
    one death, ST call). Surface both.

**UI text the interceptor should display**

- Fires: `"VIRGIN — <Nominator> is a Townsfolk. They are executed immediately, and the day ends."`
  Primary button: `"Execute <Nominator> & end the day"`. Secondary: `"Override — nothing happens"`.
- Does not fire (team): `"VIRGIN — <Nominator> is an Outsider/Minion/Demon/Traveller. Nothing
  happens, but the Virgin's ability is spent."` Primary: `"Mark spent & continue to the vote"`.
- Does not fire (impaired): `"VIRGIN — <Name> is POISONED/DRUNK. Nothing happens, but the
  ability is still spent."`
- Spy: `"<Nominator> is the Spy — do they register as a Townsfolk?"` → `Yes, execute them` /
  `No, nothing happens`.
- After firing: day banner `"Execution used: <Nominator> (Virgin). No further execution today."`

**Data / file changes**

- `characters.json:149-162` — unchanged.
- `night_guide.json` — add a `virgin` entry under a new `"day"` section (extend
  `NightGuideEntry`, `NightGuide.kt:36-40`) with the How to Run text above.
- `GameState.kt:143-149` (`Voting.outcome`) — leave as is; instead let the nomination recorder
  pass an explicit `NominationResult.WITHDRAWN` so the Virgin path can use it.
- `StatusEffects.nominationWarnings` (`:131-166`) — replace the Virgin string with a structured
  `NominationTrigger` (kind, actor, target, decision options, consequences) so `DayScreen` can
  render a decision card instead of red prose. Do the same for Witch, Golem and Fearmonger.

## Tests to add

1. **Townsfolk nominator fires**
   Given day 2, an alive unimpaired Virgin never nominated before, and a Chef nominator
   When the nomination interceptor resolves
   Then the Chef is dead with `DeathCause.EXECUTION`, the Virgin holds
   `PlacedReminder("virgin", "No ability")`, and the nomination is recorded `WITHDRAWN` with 0 votes.

2. **Outsider (Drunk) nominator does not fire but spends the ability**
   Given the nominator has `characterId = "drunk"`, `shownCharacterId = "chef"`
   Then nobody dies **and** the Virgin holds `"No ability"`, and voting is allowed to proceed.

3. **Minion nominator does not fire but spends the ability**
   Given a Poisoner nominator → nobody dies, Virgin spent, vote proceeds.

4. **Poisoned Virgin does not fire but is spent**
   Given the Virgin holds `PlacedReminder("poisoner", "Poisoned")` and a Chef nominates
   Then the Chef is **alive** and the Virgin holds `"No ability"`.
   *(Today `nominationWarnings` still tells the ST to execute — assert the warning text no longer
   claims an execution.)*

5. **Drunk-shown-as-Virgin never fires**
   Given a seat with `characterId = "drunk"`, `shownCharacterId = "virgin"` nominated by a Chef
   Then nobody dies and that seat is marked `"No ability"`.

6. **Second nomination does not re-trigger**
   Given a Virgin already holding `"No ability"`, nominated again on day 3 by a Chef
   Then no trigger is offered and the Chef lives. *(`StatusEffects` already gates on the token —
   lock it in; `StatusEffectsTest.kt:52-53` covers the string, not the consequence.)*

7. **First-nomination memory is game-lifetime**
   Given the Virgin was nominated on day 2 (ability spent, token later removed by hand)
   When nominated on day 3
   Then the interceptor still reports `spent = true` from the nomination history.

8. **The day's execution is closed**
   Given nomination A passed with 4 votes on day 3 (A on the block), then the Virgin fires
   Then `GameActions.aboutToDie(state)` returns `null` and the dusk guard offers no execution.
   *(fails today — `GameActions.kt:296-306`.)*

9. **Undertaker sees the Virgin execution**
   Given the Virgin fired on day 2 killing a Chef nominator
   When the Undertaker's info is computed on night 3
   Then the headline names **Chef**.

10. **Self-nomination fires on the Virgin**
    Given the Virgin nominates themselves
    Then the Virgin is executed (`DeathCause.EXECUTION`) and marked `"No ability"`.

11. **Traveller nominator does not fire but spends**
    Given a Traveller (`isTraveller = true`, `Team.TRAVELLER`) nominates the Virgin
    Then nobody dies and the Virgin is marked `"No ability"`.

12. **Spy nominator offers a choice**
    Given a Spy nominates the Virgin
    Then the interceptor returns two options ("registers as Townsfolk — executed" /
    "registers as evil — nothing happens"), and either branch marks the Virgin spent.

13. **"No ability" survives the phase tables**
    Given a spent Virgin, when `advancePhase` cycles DAY→NIGHT→DAY
    Then the token remains (guard against it ever being added to `EXPIRES_AT_DAWN`/`_DUSK`).
