# Fool (fool) — Bad Moon Rising Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Fool> (fetched 2026-08-25).

Current ability text:

> "The 1st time you die, you don't."

How to Run (wiki):

- If the Fool would die, they **remain alive**. *"Either way, the Fool loses their ability —
  mark them with the **NO ABILITY** reminder"* — the token is placed **when the ability
  activates**.
- If the Fool is executed: *"declare that the player was executed but remains alive. (Do not
  say why.)"*
- *"They don't learn that their ability saved their life."*

Edge cases and clarifications:

- **Drunk or poisoned Fool dies.** *"But they die if they are drunk or poisoned"* — and the
  ability is spent in the process (they perish and the ability is gone).
- **A different protection takes precedence and the Fool's ability is NOT consumed:**
  *"If another character's ability protects the Fool from death, the Fool does not use their
  ability."* So resolution order is: other protections first → Fool's ability only if the
  death would otherwise land.
- **Second and subsequent deaths are real.** Wiki example: executed Day 1 → survives; executed
  Day 4 → dies.
- The ability is confirmed for **execution** and **Demon attack**. The page does not
  enumerate Gossip / Assassin / Godfather / Witch / Moonchild / Gambler deaths, but the
  ability text is unconditional ("the 1st time you die"), so it applies to **any** death.
  **Flagging that the wiki page does not spell these out.**
- Bluffing notes: good players avoid executing a claimed Fool (it wastes the ability); the
  Devil's Advocate can simulate a Fool survival, which is why the announcement must not
  explain itself.

Jinxes: none listed, and none in `night_and_jinxes.json`. **Works.**

Night order: the Fool never wakes and is correctly absent from both order lists in
`night_and_jinxes.json`. **Works.**

## What the app does today

- `characters.json:403-415` — ability text matches the wiki; `reminders: ["No ability"]`;
  both night reminders empty. **Works.**
- `night_guide.json` — **there is no `fool` entry at all** (the lookup returns `null`).
  Harmless for the night sheet (no night step) but it means there is no prose anywhere in the
  app telling the storyteller what to say when the Fool survives an execution.
- `StatusEffects.kt:75-77` — the only Fool logic in the codebase:

  ```kotlin
  if (id == "fool" && player.reminders.none { it.label.equals("No ability", true) }) {
      notes += "Fool: the first time they die, they don't."
  }
  ```

  This is a **text note only**. It appears in two places:
  - `SeatSheet.kt:250-255` renders every `deathNotes` line above the kill buttons.
  - `SeatSheet.kt:256-268` filters `deathNotes` for the literal substrings
    `"can't die", "can not die", "Safe", "Protected", "survives", "safe from", "don't",
    "Fool"` and, if any match, routes the kill through a confirmation dialog
    (`SeatSheet.kt:288-307`) with **[They die anyway]** / **[Death prevented]**.
  - `NightScreen.kt:588-590` renders the same notes in red inside `DemonKillPanel`.

So the Fool is a warning string, nothing more.

## Defects and gaps

1. **P0 · Executing from the Day tab bypasses the Fool check entirely.** There are three
   execution buttons and **none** of them consults `deathNotes`:
   - `DayScreen.kt:111-114` — the "On the block" banner's **Execute** button calls
     `viewModel.kill(onBlock.id, DeathCause.EXECUTION)` directly.
   - `DayScreen.kt:350-357` — the per-nomination **Execute** button, same.
   - `GameShell.kt:598-605` — the dusk guard's **"Execute & begin night"**, same.

   Repro: BMR, Fool on the block, tap Execute on the Day tab. The Fool is marked dead with no
   prompt, no note, no mention that they should have survived. The storyteller only catches it
   if they happen to execute from the seat sheet instead. This is the single most likely place
   in the app to break a game outright.

2. **P0 · A drunk or poisoned Fool is wrongly reported as protected.**
   `StatusEffects.kt:75-77` does not consult `StatusEffects.isImpaired` (which is right there
   at `StatusEffects.kt:36-46`). A Fool carrying `poisoner:Poisoned` still produces the note
   "Fool: the first time they die, they don't", and the seat-sheet confirmation dialog offers
   **[Death prevented]** as the natural-looking answer. The rules say the poisoned Fool dies.
   Repro: put a Poisoned token on the Fool, then "Executed" from the seat sheet.

3. **P1 · The ability is never marked spent.** Choosing **[Death prevented]**
   (`SeatSheet.kt:304-306`) simply dismisses the dialog. It does **not** place
   `PlacedReminder("fool", "No ability")`, does not record anything in `state.deaths`, and
   leaves no trace in the game log (`GameExtras.kt:46-106`). The next time the Fool is
   attacked, `StatusEffects.kt:75` fires the same note again — the app will happily let the
   Fool survive twice. The storyteller must remember to open the seat, tap "Add reminder", and
   find "No ability" by hand.

4. **P1 · Protection precedence is not modelled.** The rules require: *other* protections
   resolve first, and only if the death still lands does the Fool's ability trigger (and get
   spent). `deathNotes` returns a flat unordered `List<String>` (`StatusEffects.kt:52-129`),
   so a Fool who is also Monk-protected produces two notes with no indication that the Monk
   applies and the Fool's ability must be preserved. Repro: Fool with `monk:Safe`, Demon
   attack, `DemonKillPanel` shows both notes side by side.

5. **P1 · Nothing tells the storyteller what to announce.** The rules are specific: announce
   that the execution happened and that the player is alive, and do not say why. There is no
   day-start briefing surface, no dawn summary, and no `night_guide.json` entry for the Fool,
   so this line exists nowhere in the app.

6. **P2 · `DemonKillPanel` shows the Fool note but still kills unconditionally.**
   `NightScreen.kt:624-637` renders the notes in red and then offers a plain
   **"${target.name} dies"** button that calls `GameActions.kill` regardless. It is strictly
   less careful than the seat sheet, which at least confirms.

7. **P2 · The protection filter is a substring match on English prose.**
   `SeatSheet.kt:256-265` matches `"don't"` and `"Fool"` against note text. It is coincidental
   that "Fool: the first time they die, they don't." matches; rewording the note in
   `StatusEffects.kt:76` would silently disable the confirmation. Protections need to be
   structured data, not greppable sentences.

8. **P2 · No `fool` entry in `night_guide.json`.** Even though the Fool has no night step, the
   guide is the natural home for the "what to say" text, and `NightGuide.forStep` is the only
   per-character prose channel in the app.

9. **P3 · No log entry for a survived death.** "Leo was executed and survived (Fool)" should
   appear in the game log; today the log only lists actual deaths (`GameExtras.kt:51-64`).

## Proposed behaviour (spec)

The Fool is not a night character, so the structured form below is expressed around the
**death pipeline**, which is where it belongs.

- **when:** never at night. The Fool has no night step in either order list — keep it that way.
- **targets:** none.
- **immediate effects:** none at setup or night 1.
- **the death pipeline (new, general):** replace the ad-hoc call sites with a single engine
  entry point that every kill in the app routes through:

  ```kotlin
  sealed interface DeathOutcome {
      data class Dies(val state: GameState) : DeathOutcome
      data class Prevented(
          val state: GameState,        // ability spent / tokens updated
          val by: String,              // "fool", "monk", "sailor", …
          val announcement: String,    // exactly what the ST should say
      ) : DeathOutcome
      data class NeedsStoryteller(
          val reasons: List<DeathReason>,   // structured, not prose
      ) : DeathOutcome
  }

  fun GameActions.resolveDeath(
      state: GameState,
      playerId: Long,
      cause: DeathCause,
      lookup: (String) -> Character?,
  ): DeathOutcome
  ```

  `StatusEffects.deathNotes` becomes a thin renderer over a new structured
  `StatusEffects.deathReasons(state, lookup, playerId): List<DeathReason>` where

  ```kotlin
  data class DeathReason(
      val sourceId: String,
      val kind: Kind,                  // PROTECTION, LAST_CHANCE, ON_DEATH_TRIGGER
      val appliesTo: Set<DeathCause>,  // Soldier/Monk = DEMON only; Fool = all
      val consumesAbility: Boolean,    // Fool = true, Monk = false
      val priority: Int,               // lower resolves first
      val text: String,
  )
  ```

  The Fool contributes
  `DeathReason("fool", LAST_CHANCE, appliesTo = all causes, consumesAbility = true,
  priority = 100, text = "First death — they don't die. Mark No ability. Don't say why.")`
  with a **high** priority number so every ordinary protection (priority < 100) is considered
  first, satisfying *"if another character's ability protects the Fool, the Fool does not use
  their ability."*

  The Fool's reason is **suppressed** when:
  - `player.reminders.any { it.label.equals("No ability", true) }` (already spent), or
  - `StatusEffects.isImpaired(state, lookup, player)` is `true` (drunk/poisoned Fool dies).

- **applying the Fool outcome:** when the storyteller (or the engine, if no ambiguity remains)
  chooses "the Fool's ability saves them", `resolveDeath` returns
  `Prevented(state = addReminder(state, foolId, PlacedReminder("fool", "No ability")), by =
  "fool", announcement = …)`, i.e. **the token is placed automatically, in the same undoable
  step**. It must also append a non-death entry to the game log.
- **deferred effects:** none. The next death is real.
- **expiry:** `fool:No ability` is permanent — it must **not** be added to `EXPIRES_AT_DAWN` /
  `EXPIRES_AT_DUSK` (`GameActions.kt:218-242`).
- **information:** the Fool learns nothing. No show cards.
- **visibility:** nothing is shown to the Demon/Minions/Lunatic. In particular, when the Demon
  attacks a Fool the Demon is told nothing — they simply see no death at dawn.
- **day-time inputs the app must let the ST record:** none for the Fool itself, but the
  announcement should be offered as a one-tap entry into the public-statement ledger proposed
  in `gossip.md` (`StatementKind.OTHER`, speaker = the storyteller), so the record shows that
  an execution produced no death.
- **call sites to change (all of them):**
  - `DayScreen.kt:111-114`, `DayScreen.kt:350-357`, `GameShell.kt:598-605` — route through
    `resolveDeath`; when it returns `NeedsStoryteller` or `Prevented`, show the same
    confirmation UI the seat sheet uses.
  - `SeatSheet.kt:266-268` (`requestKill`) — route through `resolveDeath`; **[Death
    prevented]** must call back into the engine so the ability is spent, not just dismiss.
  - `NightScreen.kt:624-637` (`DemonKillPanel`) — route through `resolveDeath`.
  - `GameViewModel.kill` (`GameViewModel.kt:207-208`) and the parallel
    `WebGameViewModel` — either replace with `resolveDeath` or keep `kill` as the raw
    "override" path used only after the storyteller has confirmed.

### UI text

- Execution confirmation: `Leo is the Fool and hasn't used it. Executed but alive?`
  Buttons: `[They survive — announce the execution, don't say why]` / `[They die anyway]`.
- After it fires: `Leo survived. Fool marked No ability — the next death is real.`
- Poisoned Fool: `Leo is the Fool but is poisoned — the ability does not save them.`
- Fool + another protection: `The Monk already protects Leo tonight. The Fool's ability is NOT
  used — they keep it.`

### Data changes

- Add a `fool` entry to `night_guide.json` with a `"day"`-style block (or reuse `"other"`
  purely as prose the Reference screen shows), containing the announcement wording and the
  precedence rule.
- `characters.json:403-415` — no change.
- `night_and_jinxes.json` — no change.

## Tests to add

1. **Execution of a healthy Fool does not kill.** Given a BMR game, a Fool alive with no
   `No ability` token, When `resolveDeath(state, foolId, EXECUTION, lookup)` runs, Then the
   result is `Prevented(by = "fool")`, the Fool is still `alive`, `state.deaths` is unchanged,
   and the Fool now carries `PlacedReminder("fool", "No ability")`. **Fails today** — the
   Day tab kills unconditionally.

2. **Second death is real.** Given a Fool already carrying `fool:No ability`, When
   `resolveDeath(..., EXECUTION, ...)` runs, Then the outcome is `Dies` and the Fool is dead.

3. **Poisoned Fool dies and produces no Fool reason.** Given a Fool carrying
   `poisoner:Poisoned`, When `StatusEffects.deathReasons` is computed, Then no reason has
   `sourceId == "fool"`, and `resolveDeath` returns `Dies`. **Fails today** —
   `StatusEffects.kt:75-77` ignores impairment.

4. **Another protection preserves the Fool's ability.** Given a Fool carrying `monk:Safe` and
   a `DEMON` death, When resolved as prevented-by-Monk, Then the Fool does **not** gain
   `fool:No ability`.

5. **Cause-scoped protections do not apply to non-Demon deaths.** Given a Fool who is also
   Monk-protected and a `EXECUTION` death, Then the Monk reason is filtered out by
   `appliesTo`, and the Fool's reason is the one that fires.

6. **The Fool is absent from both night orders.** Given a BMR game with a Fool, Then neither
   `nightOrder.firstNight` nor `nightOrder.otherNight` produces a step with `id == "fool"`.
   (Passes today — regression guard.)

7. **Survived execution appears in the log.** Given a Fool survives an execution on Day 3,
   Then the derived log contains an entry for Day 3 mentioning the execution and that no one
   died. **Fails today** — `GameExtras.kt:51-64` only iterates `state.deaths`.

8. **Undo restores the unspent Fool.** Given the Fool's ability fires, When `undo()` is
   applied, Then the `fool:No ability` token is gone and the Fool is unspent again (guards
   that the token placement and the prevented-death happen in one `update`).
