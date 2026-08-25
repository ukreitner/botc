# Chambermaid (chambermaid) — Bad Moon Rising Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Chambermaid> (fetched 2026-08-25).

Current ability text:

> "Each night, choose 2 alive players (not yourself): you learn how many woke tonight due to
> their ability."

How to Run (wiki):

- *"Each night, wake the Chambermaid. They point at two alive players except themselves. Show
  fingers (0, 1, or 2) equalling how many chosen characters woke tonight. Put them to sleep."*
- *"Do not wake the Chambermaid if fewer than two players remain alive"* (i.e. fewer than two
  other living players to choose between).

Exactly who counts as "woke tonight" (wiki):

- Players who **woke to use their ability** count.
- The Chambermaid **cannot choose themselves**.
- **Dead players do not count** (and cannot be chosen).
- Players who woke but **did not use their ability** do **not** count.
- Players woken **for other reasons** — Storyteller notifications, **Demon info**, accidental
  waking — do **not** count.
- **Drunk or poisoned players who woke to use their ability still count.**
- The answer is a number (0, 1 or 2) shown on fingers.

Worked examples on the page: detecting an Exorcist + Innkeeper pair (learns "2"); a drunk
Chambermaid; and timing traps with characters like the **Assassin, who does not wake on night
1** — the recurring theme is that "is in play" and "woke tonight" are different questions.

Jinx:

- **Mathematician:** "The Chambermaid learns if the Mathematician wakes tonight or not, even
  though the Chambermaid wakes first." This is decisive for the spec: the Chambermaid's answer
  covers **the whole night**, including wakings that have not happened yet when her own step
  runs. The calculation must be **predictive**, not just a replay of what has already happened.

Night order: the Chambermaid is second-to-last on both sheets (first night index 70 of 73,
other nights index 93 of 96), with only the Mathematician and DAWN after her. **Correct**, and
it is what makes an accurate derivation feasible.

## What the app does today

- `characters.json:360-370` — ability text matches. Both `firstNightReminder` and
  `otherNightReminder` are set ("The Chambermaid points to two players. Show the number signal
  (0, 1, 2, ...) for how many of those players wake tonight for their ability."), so she
  correctly has a step on **every** night. `reminders: []` — correct, she has no tokens.
- `night_and_jinxes.json:365` (firstNight 70) and `:466` (otherNight 93) — positions correct.
- `night_and_jinxes.json:23-27` — the `chambermaid`/`mathematician` jinx is present.
- `night_guide.json:207-216` — good prose; the "other" variant even says *"players woken only
  to be shown information they didn't cause (or not woken at all) don't count"*, which is the
  right rule. The `first` variant omits that sentence.
- `InfoCalc.kt:23,34,76,469-484` — the Chambermaid is an info role needing 2 targets, computed
  by:

  ```kotlin
  private fun chambermaid(ctx: Ctx, targets: List<Long>): InfoResult {
      val chosen = validTargets(ctx, targets, 2)
          ?: return InfoResult("Pick 2 different valid players the Chambermaid chose")
      val order = if (ctx.state.cycle == 1) ctx.data.firstNightOrder else ctx.data.otherNightOrder
      val wakers = chosen.filter { p ->
          p.alive && p.characterId != null && p.characterId in order
      }
      return InfoResult(
          headline = "${wakers.size} of the 2 wake tonight (approximate)",
          detail = chosen.joinToString { "${ctx.name(it)}: ${if (it in wakers) "wakes" else "doesn't wake"}" },
          caveats = listOf("Approximation from the night order — characters that only sometimes wake (Ravenkeeper, once-per-game abilities already spent...) need your judgement."),
      )
  }
  ```

  i.e. **"is this character's id anywhere in tonight's global order list?"** Plus the shared
  `commonCaveats` (impairment + Vortox, `InfoCalc.kt:158-166`), and the full-screen number card
  and false-info row that every numeric role gets (`NightScreen.kt:889-930`). Covered by
  `InfoCalcTest.kt:135`.
- Target selection: `NightScreen.kt:838-861` renders a chip for **every** player in
  `state.players` with no filtering at all.

**Is "who woke tonight" tracked?** Partially, and the answer to the direct question is: the
app has `GameState.nightStepsDone: Set<String>` (`GameState.kt:106`), a set of **step ids**
ticked tonight, reset at every phase change (`GameActions.kt:259,262`) and toggled from the
checklist (`GameActions.kt:265-272`, `NightScreen.kt:88-97`). **`InfoCalc.chambermaid` does not
look at it.** And even if it did, it is the wrong shape — see defect 3.

## Defects and gaps

1. **P0 · Characters that are on the night sheet but never wake are counted as waking.**
   The membership test `p.characterId in order` (`InfoCalc.kt:474`) counts every id present in
   the order list. In Bad Moon Rising this is wrong for at least:
   - **Gossip** (otherNight 57) — the app's own guide says *"The Gossip does not wake"*
     (`night_guide.json:247`). Counted as waking.
   - **Grandmother** (otherNight 71) — *"The Grandmother does not wake"*
     (`night_guide.json:258`). Counted as waking.
   - **Tinker** (otherNight 69) and **Moonchild** (70) — Storyteller bookkeeping, no waking.
   - **Godfather** (56) — only wakes if an Outsider died today.
   - **Assassin** (55) — only wakes for the once-per-game kill; never on night 1 (and correctly
     absent from `firstNight`), and not after it is spent.
   - **Professor** (63) and **Courtier** (15) — once-per-game; after use they never wake again,
     but they stay in the order list forever.

   Repro: BMR, night 3, Chambermaid points at the Gossip and the Grandmother. The app says
   **"2 of the 2 wake tonight"**. The true answer is **0**. That is a wrong number handed
   straight to a good player.

2. **P0 · The Drunk is counted as not waking; the Marionette is counted backwards.**
   The filter uses `p.characterId`, but the app already has the correct field:
   `Player.nightRoleId` (`GameState.kt:35-41`) maps `drunk` and `marionette` to their
   `shownCharacterId`, and `NightOrder.build` uses exactly that when assembling the sheet
   (`NightOrder.kt:46-48`).
   - A **Drunk** shown the Empath: `characterId == "drunk"`, which is in **neither** order list
     → counted as **not waking**. But the storyteller wakes them every night as the Empath, and
     the wiki says drunk players who woke to use their ability **still count**. Wrong answer.
   - A **Marionette** on night 1: `characterId == "marionette"`, which **is** in `firstNight`
     at index 21 → counted as **waking**. But `NightOrder.kt:121-140` builds that row as
     *"Marionette info"*, which wakes the **Demon**, not the Marionette. Wrong answer, in the
     opposite direction.
   - On other nights the Marionette is absent from `otherNight` → counted as not waking, even
     though they are woken as their shown good character.

3. **P1 · `nightStepsDone` exists and is ignored — and is the wrong shape to use directly.**
   The storyteller is already ticking off every step as they run it, and the Chambermaid sits
   at index 93 of 96, so by the time her step opens the app knows almost exactly what happened
   tonight. Nothing consumes it. But it cannot simply be substituted, because:
   - it stores **step ids (character ids)**, not player ids — with duplicate characters
     (Village Idiot ×3, Legion) there is no per-player resolution;
   - "ticked" ≠ "woke": the storyteller ticks the Gossip/Grandmother/Tinker rows too, and ticks
     an Exorcist-silenced Demon's row;
   - the Mathematician (94) and anything the storyteller runs out of order have not been
     ticked yet, and the jinx says they must still be counted.

4. **P1 · An Exorcist-silenced Demon is counted as waking.** The Demon is woken only to be
   shown the Exorcist token — that is a Storyteller notification, not the Demon using their
   ability, so it must **not** count. The app knows the state
   (`NightOrder.kt:149-154` reads `exorcist:Chosen`) but `InfoCalc.chambermaid` does not.
   In a BMR game with both an Exorcist and a Chambermaid — a common pairing — this is a
   frequent wrong answer.

5. **P1 · Night-1 Minion/Demon info is counted as a waking.** The wiki is explicit that "Demon
   info" does not count. On night 1, `MINION_INFO` and `DEMON_INFO` are separate markers
   (`NightOrder.kt:60-119`) so they are not in the per-character membership test — **but** the
   Demon's own character id is also in `firstNight` for several Demons (e.g. `pukka` at 41,
   `lilmonsta` 24, `lleech` 25, `yaggababble` 12), and for those the Demon *does* use an
   ability on night 1 and *should* count. The current code cannot tell the two situations
   apart because it never looks at what the step actually does. The result is right by accident
   for the Pukka and wrong for a Demon whose only night-1 appearance is the info step.

6. **P1 · The target picker ignores every constraint in the ability text.**
   `NightScreen.kt:838-861` lists all players — dead seats, travellers, and the Chambermaid
   herself. The rule is "2 **alive** players, **not yourself**". Repro: open the Chambermaid
   step and tap her own chip; the app accepts it and, because `wakers` filters on `p.alive` but
   not on identity, silently produces a number.

7. **P2 · Dead choices are silently swallowed rather than rejected.** `InfoCalc.kt:473-475`
   filters `p.alive` inside `wakers`, so choosing a dead player yields "doesn't wake" — the
   same output as a living non-waker. The storyteller gets no signal that an illegal choice was
   made.

8. **P2 · The "don't wake her if fewer than two others are alive" rule is not implemented.**
   `NightOrder.build` (`NightOrder.kt:142-178`) includes the step whenever a holder exists.
   `NightStepRow` only shows "All holders are dead — usually skip" (`NightScreen.kt:751-757`).

9. **P2 · The first-night guide text is weaker than the other-night one.**
   `night_guide.json:208-210` omits the "players woken only to be shown information don't
   count" sentence that `:212-214` has — and night 1 is precisely when that sentence matters
   most (Minion info, Demon info, Marionette info).

10. **P3 · The headline says "(approximate)" and pushes the work back to the storyteller.**
    Honest, but it is the exact pattern the audit is meant to eliminate: the app knows the
    answer and asks the storyteller to compute it anyway.

## Proposed behaviour (spec)

### The core primitive: a real "who woke tonight" set

Replace the "is the id in the order list" heuristic with an explicit, per-player predicate,
and back it with a recorded set the storyteller can correct.

**1. Classify what each night step does.** Add to the character data (or a small engine table
keyed by character id and night):

```kotlin
enum class WakeKind {
    ACTS,        // the holder wakes and uses their ability  -> counts
    INFORMED,    // the holder wakes only to be shown something -> does NOT count
    NONE,        // storyteller bookkeeping, nobody wakes     -> does NOT count
    CONDITIONAL, // depends on state; resolved by a predicate
}
```

BMR classifications that are wrong today: `gossip` = **NONE**, `grandmother` (other nights) =
**NONE**, `tinker` = **NONE**, `moonchild` = **NONE**, `assassin` = **CONDITIONAL** (unspent,
and never on night 1), `godfather` (other nights) = **CONDITIONAL** (an Outsider died today),
`professor` / `courtier` = **CONDITIONAL** (once-per-game not spent), `ravenkeeper` =
**CONDITIONAL** (died at night), `marionette` (first night) = **INFORMED for the Demon, NONE
for the Marionette**, `MINION_INFO` / `DEMON_INFO` = **INFORMED**.

The cleanest home for this is a new field in `characters.json` (`"wakes": "acts" | "informed" |
"none" | "conditional"`, per first/other night), with the conditional predicates in the engine.
`night_guide.json` already carries the human-readable version of every one of these facts — the
data is there, it is just not machine-readable.

**2. Record it as it happens.** Add to `GameState`:

```kotlin
/** Player ids who woke tonight to use their own ability. Cleared at each phase change. */
val wokeTonight: Set<Long> = emptySet(),
```

`GameActions.toggleNightStep` (`GameActions.kt:265-272`) gains a companion
`GameActions.markStepWoke(state, stepId, playerIds)`; when the storyteller ticks a step whose
`WakeKind == ACTS` (or a CONDITIONAL that resolved true), the step's `playerIds` are added.
`advancePhase` clears it alongside `nightStepsDone` (`GameActions.kt:259,262`).
Suppressions clear it too: an Exorcist-silenced Demon's step must **not** add its holder.

**3. Predict the rest of the night.** Because the Mathematician jinx requires it, the
Chambermaid's answer is
`wokeTonight ∪ { players whose remaining, not-yet-ticked step will wake them }`, computed with
the same `WakeKind` table over the steps after the Chambermaid's own index.

**4. Let the storyteller override.** The panel shows each of the two chosen players with a
per-player toggle `woke ⇄ didn't wake` and a one-line reason ("Gossip never wakes",
"Exorcist silenced them", "Courtier already spent"). The number updates live. This is the
escape hatch for homebrew, imported scripts, and anything the table cannot know — and it keeps
the storyteller in control while doing the arithmetic for them.

### Chambermaid step

- **when:** first **and** other nights. Wake condition:
  `holder.alive && state.players.count { it.alive && it.id != holder.id } >= 2`.
  When that fails, the step renders `Fewer than 2 other players are alive — do not wake the
  Chambermaid.` and is tickable with no action.
- **targets:** exactly 2 players. Constraints: **alive**, **not the Chambermaid**, and not the
  same player twice. The picker in `NightScreen.kt:838-861` must filter to
  `state.players.filter { it.alive && it.id != holderId }` for the Chambermaid specifically
  (`InfoCalc.targetsNeeded` already returns 2; add a companion `InfoCalc.targetFilter(characterId,
  holderId): (Player) -> Boolean` so this is expressible generically for the Fortune Teller,
  Seamstress, Dreamer, Village Idiot too).
- **immediate effects:** none — the Chambermaid places no tokens (`characters.json:369`).
- **deferred effects:** none.
- **expiry:** n/a.
- **information:**
  - Headline: `1 of the 2 woke tonight` — **no "(approximate)"**.
  - Detail: one line per chosen player: `Cora (Innkeeper): woke — used their ability.` /
    `Nate (Gossip): did not wake — the Gossip never wakes.` /
    `Aurora (Po): did not wake — the Exorcist silenced them.` /
    `Blake (Empath): woke — they are the Drunk, but they still wake and still count.`
  - The existing full-screen number card and the false-info row for an impaired Chambermaid
    (`NightScreen.kt:889-930`) already work and should be kept unchanged.
  - **impaired/false alternative:** unchanged — `commonCaveats` (`InfoCalc.kt:158-166`) already
    flags a drunk/poisoned Chambermaid and a Vortox, and the UI already offers 0–4 as one-tap
    lies.
  - **misregistration:** none applies — the Chambermaid learns a count of wakings, not
    characters or alignments. The Spy/Recluse caveats should **not** be emitted here.
- **visibility:** nothing is shown to the Demon/Minions/Lunatic.
- **day-time inputs:** none.
- **interactions/jinxes:**
  - **Mathematician** (`night_and_jinxes.json:23-27`): the Mathematician always wakes, and sits
    *after* the Chambermaid (94 vs 93). The prediction step above is what makes this correct.
    Surface the jinx text on the step when both are in play.
  - **Exorcist:** a silenced Demon does not count (defect 4).
  - **Drunk / Marionette / Lunatic:** count by `nightRoleId`, not `characterId`; the Lunatic
    genuinely wakes and counts.
  - **Ravenkeeper:** can only be chosen while alive, and only wakes on dying at night, so it
    almost always resolves to "did not wake" — but state the reason rather than guessing.
  - **Travellers:** they can be chosen if alive; most have no night ability.
  - **Zombuul:** registers as dead but is alive for this purpose — it wakes and counts.
    (The Chambermaid may **not** choose a player the app has marked dead, so a Zombuul the
    storyteller has recorded as dead is unselectable. Worth a note in the guide.)

### UI text the step should display

- Header: `Chambermaid — she points at 2 alive players (not herself).`
- Result: `1 — show one finger.`
- Per-player lines as above, each with a `[woke]/[didn't]` toggle.
- Too few alive: `Only 1 other player is alive — do not wake the Chambermaid.`
- Impaired: existing behaviour (`! ... give false info`) plus the 0–4 lie chips.

### Data changes

- `characters.json` — add a machine-readable wake classification per character per night
  (`"firstNightWakes"` / `"otherNightWakes"` ∈ `acts|informed|none|conditional`). At minimum,
  set `none` for `gossip`, `grandmother` (other), `tinker`, `moonchild`, and `conditional` for
  `assassin`, `godfather`, `professor`, `courtier`, `ravenkeeper`.
- `night_guide.json:208-210` — copy the "players woken only to be shown information they didn't
  cause don't count" sentence from the `other` variant into `first`, and add the explicit
  night-1 list: "Minion info, Demon info and Marionette info do not count."
- `night_and_jinxes.json` — no change.

## Tests to add

All in `engine/src/test/kotlin/com/clocktower/engine/InfoCalcTest.kt` unless noted.

1. **Non-waking characters count as 0.** Given a BMR game on night 3 with a Chambermaid, a
   Gossip and a Grandmother, When the Chambermaid chooses the Gossip and the Grandmother, Then
   the headline is `0 of the 2`. **Fails today** — returns `2 of the 2` (`InfoCalc.kt:474`).

2. **The Drunk counts as waking.** Given a seat with `characterId = "drunk"` and
   `shownCharacterId = "empath"`, When the Chambermaid chooses them, Then they count as
   waking. **Fails today** — `"drunk"` is in neither order list.

3. **The Marionette does not count on night 1.** Given a seat with
   `characterId = "marionette"`, When the Chambermaid chooses them on night 1, Then they count
   as **not** waking (the Marionette-info row wakes the Demon). **Fails today** —
   `"marionette"` is at `firstNight` index 21.

4. **An Exorcist-silenced Demon does not count.** Given the Demon's seat carries the Exorcist's
   silence token on night 3, When the Chambermaid chooses the Demon and one waker, Then the
   headline is `1 of the 2`.

5. **Spent once-per-game abilities do not count.** Given a Courtier carrying
   `courtier:No ability` (and a recorded night choice), When the Chambermaid chooses them,
   Then they count as not waking. Same for a spent Professor.

6. **The Mathematician still counts although they wake later.** Given a Mathematician in play,
   When the Chambermaid's step runs before the Mathematician's, Then the Mathematician counts
   as waking. **This is the jinx test** and it forces the prediction path.

7. **Illegal targets are rejected, not silently counted.** Given the Chambermaid chooses
   herself, or a dead player, Then `InfoCalc.compute` returns a "pick 2 alive players other
   than the Chambermaid" prompt rather than a number.

8. **She is not woken with fewer than two other living players.** Given 2 alive players total
   (the Chambermaid + 1), Then the step reports "do not wake".

9. **`wokeTonight` is cleared at each phase change.** Given `wokeTonight` is non-empty at
   night 3, When `advancePhase` runs twice (dawn, then dusk), Then it is empty on night 4.
   (`GameActionsTest.kt`.)

10. **`nightStepsDone` alone is insufficient.** Given the storyteller ticks the Gossip step
    (which wakes nobody), Then the Gossip is **not** added to `wokeTonight`, proving the
    classification is applied rather than the tick being taken at face value.
    (`GameActionsTest.kt`.)
