# Undertaker (undertaker) — Trouble Brewing Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Undertaker> (raw wikitext fetched 2026‑08‑25).

**Current ability text (verbatim):**
> "Each night*, you learn which character died by execution today."

**Summary bullets (verbatim):**
- "**The player must have died from execution** for the Undertaker to learn who they are.
  Deaths during the day for other reasons, such as the Gunslinger choosing a player to kill,
  or the exile of a Traveller, do not count."
- "The Undertaker wakes each night except the first, as there have been no executions yet."
- "If nobody died today, the Undertaker learns nothing. The Storyteller either does not wake
  the Undertaker at night, or wakes them but does not show a token."
- "If the Drunk is executed, the Undertaker is shown the **Drunk** character token, not the
  token for the Townsfolk that the Drunk player thought they were."

**How to Run (verbatim):**
> "If a player dies by execution, put the Undertaker's **DIED TODAY** reminder token by the
> dead player's character token.
>
> Each night except the first, if any player died by execution today, wake the Undertaker.
> Show the character token marked **DIED TODAY** to the Undertaker. Put the Undertaker to
> sleep. Remove the Undertaker's reminder token when convenient.
>
> In Trouble Brewing, there can only be one execution per day, and every execution causes a
> player to die. In other editions, there may be more than one execution per day (in which
> case the Storyteller chooses which character to show the Undertaker) or **the execution does
> not cause a death (in which case the Undertaker learns nothing)**."

**Examples (verbatim):**
- "The Mayor is executed today. That night, the Undertaker is shown the Mayor token."
- "The Drunk, who thinks they are the Virgin, is executed today. At night, the Undertaker is
  shown the Drunk token, because the Undertaker learns a player's true character, as opposed
  to the one they believe they are."
- "The Spy is executed. Two Travellers are exiled. That night, the Undertaker is shown the
  Butler token, because the Spy is registering as the Butler, and because the exiles are not
  executions."
- "Nobody was executed today. That night, the Undertaker does not wake."

**Storyteller-relevant clarifications from Tips & Tricks:**
- "You do not learn the identity of Travellers; they are exiled, not executed. The only
  exception to this is the **Scapegoat**, since they are explicitly executed by their character
  ability. However, you do not learn their alignment, only their character."
- "**A player who dies because of the Virgin is considered executed** and will react to your
  ability, so you will learn who they are that night."
- "Beware the Spy and the Recluse! They will likely register to you as good and evil characters
  respectively, as their abilities **continue to function even when they are dead**."

Corroborating ruling on execution-without-death: the player must have died from the execution;
a player executed but saved (Devil's Advocate, and in other editions the Pacifist/Mayor bounce)
gives the Undertaker **nothing** — see the Undertaker Summary above and
<https://wiki.bloodontheclocktower.com/Devil%27s_Advocate>.

**Jinxes:** none.

## What the app does today

Data
- `characters.json:135-148` — ability text matches the wiki. `otherNightReminder`:
  "If a player was executed today: Show that player's character token." `reminders:
  ["Died today"]`. Correct.
- `night_and_jinxes.json:448` — `undertaker` is at index 75 of `otherNight`, after the demons
  and after `professor` (63); absent from `firstNight`. Correct.
- `night_guide.json:73-85` — instructions read:
  > "Only act if a player was executed today (marked Died today), **even if they did not die
  > from it**. Wake the Undertaker and show the executed player's character token…"
  The bolded clause is **contrary to the current rules** (see defect 1). One prepared show card
  ("This character was executed today", `token: "pick"`).

Engine
- `InfoCalc.kt:57` → `undertaker(ctx)` at `InfoCalc.kt:281-293`:
  ```kotlin
  val day = relevantDay(ctx.state)                       // cycle - 1 during NIGHT
  val executed = ctx.state.deaths.lastOrNull {
      it.cause == DeathCause.EXECUTION && it.day == day
  } ?: return InfoResult("No one was executed today — the Undertaker doesn't wake")
  val player = ctx.state.player(executed.playerId)
  val character = player?.let { ctx.character(it) }      // <- live characterId, NOT the snapshot
  return InfoResult(
      headline = "Show: ${character?.name ?: "?"}",
      detail  = "${player?.name ?: "?"} was executed today",
      caveats = player?.let { misregistrations(ctx, listOf(it)) } ?: emptyList(),
  )
  ```
  - `relevantDay` (`InfoCalc.kt:117-118`) correctly resolves "today" to `cycle - 1` at night.
  - `ctx.character(p)` reads `p.characterId`, so an executed **Drunk shows as Drunk** — the
    wiki's second example is handled correctly.
  - `lastOrNull` implements "the ST chooses which to show" as "the most recent" when multiple
    executions exist.
  - Travellers exiled use `DeathCause.EXILE` (`DayScreen.kt:350-357`) and are correctly ignored.
  - `misregistrations` (`InfoCalc.kt:121-130`) adds a Spy/Recluse caveat string.
- `GameActions.resurrect` (`GameActions.kt:173-181`) keeps the death record with
  `resurrected = true`, so an executed-then-resurrected player is still found by the filter.
  The doc-comment at `:168-171` explicitly says "Undertaker/Cannibal history survives" — this
  is right per the rules (they *did* die by execution today).
- `GameActions.revive` (`:162-166`) **drops** the death record — correct, since that is the
  "undo a mistake" path.

UI
- The step renders through the generic path: night_guide prose + show card
  (`NightScreen.kt:792-832`), then the info block (`NightScreen.kt:836-932`). No target picker
  (`InfoCalc.targetsNeeded` → 0). Impairment caveats come from `InfoCalc.impairments`
  (`InfoCalc.kt:133-153`) and the false-info chips at `NightScreen.kt:904-930` do **not**
  produce a false *token* (only numbers and YES/NO).
- `NightOrder.build` (`NightOrder.kt:46-48`) emits the row with no `alive` filter, so a dead
  Undertaker still gets a row (flagged "All holders are dead — usually skip",
  `NightScreen.kt:751-757`) — and so does an Undertaker on a night when nobody was executed.

Works: the day arithmetic; Drunk-shows-as-Drunk; exiles excluded; resurrection preserved;
the "no one was executed" headline; night-order position.

## Defects and gaps

1. **P0 · `night_guide.json:75` states the opposite of the rule** — "even if they did not die
   from it". Current rules: "The player **must have died** from execution"; "the execution does
   not cause a death (in which case the Undertaker learns nothing)". A ST following the in-app
   guide will wrongly show a token after a Devil's-Advocate-saved execution, handing the good
   team a fabricated confirmation. This looks like drift from the older
   *"you learn which character was executed today"* wording.
   *Repro:* expand the Undertaker step on any other night — the prose is right there.

2. **P0 · `InfoCalc.undertaker` reads the live `characterId`, not `characterIdAtDeath`** —
   `InfoCalc.kt:287`. `DeathRecord.characterIdAtDeath` exists precisely for this
   (`GameState.kt:72-73`: "Snapshots prevent later character changes from rewriting a death")
   and `WinCheck` already uses it (`WinCheck.kt:32, 54`). If the executed seat's character
   changes between the execution and the Undertaker's wake — Pit-Hag, Barber swap, Snake
   Charmer, Fang Gu jump, Imp star-pass onto a dead… or, in the ST's own workflow, simply
   correcting a mis-assigned token later — the Undertaker is shown the **wrong** character.
   *Repro:* execute a player on day 2; before the Undertaker step, use SeatSheet → "Change
   character" on that dead seat; the Undertaker headline changes retroactively.

3. **P0 · No execution-without-death handling** — the calc keys purely off
   `DeathCause.EXECUTION` death records, which is *almost* right, but the app has no way to
   record "X was executed and survived" at all: `DayScreen.kt:111-115` and `:350-357` only
   offer "Execute", which calls `viewModel.kill(...)`. A Devil's-Advocate-protected player who
   is executed must produce a public execution with **no** death and **no** Undertaker info;
   today the ST either kills them (wrong) or records nothing (and the DA token expiry at
   `GameActions.kt:236` silently sweeps the protection at dusk).

4. **P1 · The "Died today" reminder token is never placed or removed** —
   `grep -rn "Died today"` finds only data files. The token exists in `characters.json:144` and
   in `ReminderPicker` (`SeatSheet.kt:545-568`), but nothing places it on execution and nothing
   expires it. The How to Run makes it step 1. Consequence: the grimoire circle
   (`GrimoireScreen.kt:148-161`) gives the ST no visual cue of today's execution, and the token
   would linger forever if hand-placed (it is in neither `EXPIRES_AT_DAWN` nor
   `EXPIRES_AT_DUSK`, `GameActions.kt:218-242`).

5. **P1 · Spy/Recluse misregistration is a caveat string, not a decision** —
   `InfoCalc.kt:121-130` yields "X is the Spy — may register as good / a Townsfolk or
   Outsider." The wiki's third example requires the ST to *pick* a token (Butler) and, per Tips
   & Tricks, keep registering consistently even though the Spy is now dead. There is no
   chooser, no default, no record, and no cross-night consistency. The ST must open the
   "Show executed character" dialog (`NightScreen.kt:366-454`) and hunt for the token.

6. **P1 · The step is not suppressed when nobody was executed** — `NightOrder` always emits
   the row; the "doesn't wake" answer is only visible **after** expanding it
   (`InfoCalc.kt:285`), and the unfinished-steps guard (`GameShell.kt:147-161`) still demands a
   tick. The rule is "the Undertaker does not wake."

7. **P1 · A dead Undertaker still gets an actionable row** — same `NightOrder.kt:46-48` gap.
   The red "All holders are dead — usually skip" is right here (unlike the Ravenkeeper), but it
   should be an auto-ticked, collapsed "does not wake" line.

8. **P1 · No false-token helper when impaired** — a poisoned Undertaker must be shown a false
   character. `NightScreen.kt:904-930` only generates false numbers/YES-NO. The
   `FullGamePlaytestTest` scenario at `FullGamePlaytestTest.kt:684-690` literally scripts
   "True token was Empath …; poisoned Maya was instead shown Chef" — i.e. the *test* knows the
   ST does this by hand.

9. **P2 · Multiple executions: silent policy, no choice** — `lastOrNull`
   (`InfoCalc.kt:283`) picks the most recent. The rule is "the Storyteller chooses which
   character to show". Rare in TB (one execution per day) but real with the Scapegoat, and the
   app should present the set.

10. **P2 · No day-start briefing carrying yesterday's execution forward** — `GameShell` has no
    briefing surface at all, so "the Undertaker will learn <X> tonight" is never surfaced when
    the ST is planning the Poisoner's target.

11. **P2 · Scapegoat is not distinguished** — a Scapegoat is *executed* (so the Undertaker
    learns their character) while ordinary Travellers are *exiled*. `DayScreen.kt:319-357`
    forces `isExile = true` for any `isTraveller` nominee, so a Scapegoat's death is recorded as
    `EXILE` and the Undertaker wrongly learns nothing.

12. **P3 · Virgin-caused executions** — per the wiki, "A player who dies because of the Virgin
    is considered executed and will react to your ability." Since the Virgin trigger is entirely
    manual today (see `virgin.md`), whether the Undertaker learns depends on whether the ST
    happened to use the "Executed" button rather than "Other death". The Virgin spec must
    mandate `DeathCause.EXECUTION`.

## Proposed behaviour (spec)

**Structured night behaviour**

- **when:** `other` nights only. Wake condition:
  ```
  holder.alive
  && executionsToday(state).isNotEmpty()
  ```
  where
  ```kotlin
  fun executionsToday(state) = state.deaths.filter {
      it.cause == DeathCause.EXECUTION && it.day == relevantDay(state)
  }
  ```
  (`relevantDay` already exists, `InfoCalc.kt:117-118`.) `resurrected` is deliberately
  **ignored** — the player still died by execution today. When the condition is false, render a
  collapsed, auto-ticked `"Undertaker — no execution today. Does not wake."` line.
- **targets:** 0. Never a picker.
- **immediate effects:** none at the Undertaker's step. The **execution** action is where the
  token goes: on any `kill(cause = EXECUTION)`, auto-place
  `PlacedReminder("undertaker", "Died today")` on the executed seat (exclusive — only one
  copy), *only when an Undertaker is in play*.
- **deferred effects:** none.
- **expiry:** add `"undertaker" to "Died today"` to **`EXPIRES_AT_DAWN`**
  (`GameActions.kt:218-225`). Walk the transitions in `advancePhase` (`GameActions.kt:258-263`):
  the token is placed during DAY *N*; DAY→NIGHT clears only `EXPIRES_AT_DUSK`, so it survives
  into NIGHT *N+1* where the Undertaker reads it; NIGHT→DAY clears `EXPIRES_AT_DAWN`, removing
  it at the start of DAY *N+1* — after the wake, before the next execution. `EXPIRES_AT_DUSK`
  would be **wrong**: it would sweep the token before the Undertaker ever sees it.
- **information:**
  - True answer: `DeathRecord.characterIdAtDeath` (fall back to the live `characterId` only for
    saves predating the snapshot). **Never** `shownCharacterId` — Drunk shows as Drunk,
    Lunatic as Lunatic.
  - Multiple executions today → present all of them as chips and let the ST choose which token
    to show; default to the last.
  - **Misregistration chooser** (shared with the Ravenkeeper / Slayer / Washerwoman specs):
    if the executed player is the Spy, offer any Townsfolk/Outsider on the script (default: a
    plausible not-in-play good character, or this Spy's previously recorded registration); if
    the Recluse, offer any Minion/Demon. Persist the choice as
    `PlacedReminder("<spy|recluse>", "Registered: <Character>")` and prefer it next time. The
    ability keeps working when they are dead — do **not** gate the chooser on `alive`.
  - **Impaired alternative:** when `isImpaired(holder)` or Vortox is in play, replace the
    answer with a false-token chooser seeded from: the 3 demon bluffs, not-in-play good
    characters, then in-play characters. One tap → `ShowCard.CharacterCard`.
  - **Execution without death:** if an execution is recorded with `died = false`, the
    Undertaker does **not** wake, and the step must say
    `"<Name> was executed but did not die — the Undertaker learns nothing."`
- **visibility:** nothing to the Demon or Minions.
- **day-time inputs the app must let the ST record:** the execution itself, with an explicit
  *died / survived* outcome. Add to `Nomination`/`DeathRecord` an `executed: Boolean` +
  `died: Boolean` pair, or record a separate `Execution(day, playerId, died)` event so
  "executed but survived" is expressible for the Devil's Advocate, Pacifist, Mayor bounce,
  Zombuul, Fool and Sailor.
- **interactions/jinxes:**
  - **Virgin** — the Virgin-triggered death **must** be recorded with `DeathCause.EXECUTION`
    so this step picks it up.
  - **Scapegoat** — a Scapegoat death must be recorded as `EXECUTION`, not `EXILE`
    (`DayScreen.kt:319-357` needs a Scapegoat branch). The Undertaker learns the *character*,
    not the alignment.
  - **Traveller exile** — `EXILE`, ignored. Already correct.
  - **Professor** — resurrects at night-order 63, before the Undertaker at 75. The Undertaker
    still learns the resurrected player's character (they died by execution today). Assert this.
  - **Scarlet Woman** — if the executed player was the Demon, the token shown is the Demon's;
    the Scarlet Woman's takeover does not change what the Undertaker sees (another reason to
    read `characterIdAtDeath`).
  - **Gunslinger / Witch / Slayer day-kills** — not executions, ignored. The proposed
    `DeathCause.SLAIN` (see `slayer.md`) keeps them distinct.
  - **Vortox** — Townsfolk info must be false.

**UI text the step should display**

- Not triggered: `"Undertaker — no execution today. Does not wake."`
- Triggered: `"<Executed player> was executed today. Show the <Character> token."`
  with a `Show full-screen` chip pre-bound to that token.
- Impaired: `"<Name> is POISONED — show a FALSE token:"` + chooser chips.
- Spy/Recluse: `"<Executed player> is the Spy — choose the token they register as:"` + chips,
  with the previous registration highlighted.
- Executed-but-survived: `"<Name> was executed but did not die — the Undertaker learns nothing."`

**Data changes**

- `night_guide.json:73-85` — rewrite the `other.instructions` to:
  > "Only act if a player **died** by execution today (marked *Died today*). Wake the
  > Undertaker and show that player's **true** character token — the Drunk shows as the Drunk —
  > then put them back to sleep. If nobody was executed, or the executed player did not die, do
  > not wake the Undertaker. Travellers are exiled, not executed (the Scapegoat is the
  > exception). If the executed player was the Spy or the Recluse, show the character they
  > register as. If the Undertaker is drunk or poisoned, show a false character token instead."
  Add a second show entry for the false token.
- `characters.json:135-148` — unchanged.
- `GameActions.kt:218-225` — add `"undertaker" to "Died today"` to `EXPIRES_AT_DAWN`.

## Tests to add

1. **Reads the snapshot, not the live character**
   Given a player executed on day 2 as the Chef, whose seat is later changed to the Butler
   When `InfoCalc.compute(..., "undertaker", ...)` runs on night 3
   Then the headline names **Chef**. *(fails today — `InfoCalc.kt:287`.)*

2. **Drunk executed shows the Drunk token**
   Given a seat `characterId = "drunk"`, `shownCharacterId = "virgin"` executed on day 2
   Then night 3 shows **Drunk**. *(passes today — lock it in.)*

3. **Nobody executed → does not wake**
   Given no `EXECUTION` death on day 2
   When the night-3 sheet is built
   Then the `undertaker` step reports `wakes = false`. *(today it reports a step with a
   "doesn't wake" headline only after expanding.)*

4. **Traveller exile is ignored**
   Given a Traveller exiled on day 2 (`DeathCause.EXILE`) and no execution
   Then the Undertaker does not wake.

5. **Scapegoat counts**
   Given a Scapegoat killed on day 2 with `DeathCause.EXECUTION`
   Then the Undertaker learns the Scapegoat character.

6. **Resurrected executee still gives info**
   Given a player executed on day 2 and `GameActions.resurrect`ed on night 3 (Professor)
   Then the Undertaker still learns their character on night 3.

7. **Executed but did not die → no info**
   Given an execution recorded with `died = false` (Devil's Advocate)
   Then the Undertaker does not wake. *(cannot be expressed today.)*

8. **`Died today` token auto-placed and auto-removed**
   Given an Undertaker in play and a day-2 execution
   Then the executed seat holds `PlacedReminder("undertaker", "Died today")`;
   after `advancePhase()` NIGHT 3 → DAY 3 the token is gone. *(fails today, both halves.)*

9. **Spy execution offers a registration chooser**
   Given the Spy is executed on day 2
   Then the result exposes a non-empty `registrationOptions` of Townsfolk/Outsider ids
   (today: only a caveat string).

10. **Multiple executions offer a choice**
    Given two `EXECUTION` deaths on day 2
    Then the result lists both and defaults to the later one.

11. **Dead Undertaker does not wake**
    Given the Undertaker is dead and someone was executed on day 2
    Then the step reports `wakes = false`.

12. **Virgin trigger feeds the Undertaker**
    Given the Virgin resolver executed the nominator on day 2 with `DeathCause.EXECUTION`
    Then the Undertaker learns the nominator's character on night 3.
