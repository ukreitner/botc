# Mathematician (mathematician) — Sects & Violets Townsfolk

## Official rules (sources)

Sources: <https://wiki.bloodontheclocktower.com/Mathematician> (fetched 2026-08-25),
<https://wiki.bloodontheclocktower.com/Vortox>.

Current ability text:

> "Each night, you learn how many players' abilities worked abnormally (since dawn) due to
> another character's ability."

**How to Run (verbatim):**

> "Each time a character's ability works abnormally due to another character's ability,
> mark them with an **ABNORMAL** reminder. Each night, wake the Mathematician. Show fingers
> (0, 1, 2, etc.) equaling the number of characters with **ABNORMAL** reminders. Put the
> Mathematician to sleep. Remove all **ABNORMAL** reminders."

**Examples (verbatim):**

> "The poisoned Oracle learns that two dead players are evil, when three dead players are
> actually evil. All other character abilities work normally. Later that night, the
> Mathematician learns a "1".
>
> The poisoned Snake Charmer chooses a Townsfolk player, and nothing happens. The drunk
> Juggler gets correct information. The Savant learns two pieces of true information.
> Later that night, the Mathematician learns a "1" because the Snake Charmer and Juggler's
> abilities worked as normal, whilst one of the Savant's facts was true when it should
> have been false.
>
> A Vortox is in play. Five good players got false information. The Witch is drunk, and
> when their cursed player nominated, nothing happened. Even though six abilities worked
> abnormally, the Mathematician learns a "4" due to the Vortox's ability."

**Detection rules (from the page's summary sections):**

- Detects when abilities produce false information or fail to activate **due to another
  character's ability**.
- **Does not detect their own ability failing.**
- Does not directly detect drunkenness or poisoning, but recognises when those conditions
  cause an ability to *fail*.
- Named examples of detected malfunctions: "a Recluse registering as evil to the Chef"
  (the **Chef** is the one marked), "a poisoned Soldier dying from the Imp's attack" (the
  **Soldier** is marked).

**Jinxes (verbatim):**

> **Chambermaid** — "The Chambermaid can detect if the Mathematician will wake tonight."
> **Drunk** — "The Mathematician learns if the Drunk's ability yielded false info or failed to work properly."
> **Lunatic** — "The Mathematician learns if the Lunatic attacks a different player than the real Demon attacked."
> **Marionette** — "The Mathematician learns if the Marionette's ability yielded false info or failed to work properly."

**Storyteller-relevant timing / edge cases distilled from the above**

- **Every night, first and other.** The window is "since dawn"; on night 1 there is no
  preceding dawn, so the window is the whole first night up to the Mathematician's step.
- **Counts *characters/players*, not events.** The How-to-Run counts reminder tokens on
  players; a player who malfunctions twice in one window is still one token, one count.
- **The window resets every night**: all ABNORMAL reminders are removed after the reveal.
- **"Due to another character's ability" is the load-bearing clause.** Being the **Drunk**
  or the **Marionette** is not another character's ability — hence the two jinxes that
  explicitly opt them in.
- **An ability that would have produced the same result anyway does not count** (example 2:
  the poisoned Snake Charmer picked a Townsfolk, so nothing would have happened either way;
  the drunk Juggler was given the correct number).
- **Misregistration counts, and it is the *reader* who is marked** ("a Recluse registering
  as evil to the Chef" → mark the Chef).
- **Blocked/forced deaths count** ("a poisoned Soldier dying from the Imp's attack" → mark
  the Soldier).
- **Under a Vortox the Mathematician's own number must be false** (example 3), while the
  Vortox simultaneously inflates the true count because every Townsfolk who got information
  got false information.

## What the app does today

| path | what it holds |
|---|---|
| `engine/src/main/resources/botc/data/characters.json:852-865` | Ability text matches. Both night reminders: "Show the hand signal for the number (0, 1, 2, etc.) of players whose ability malfunctioned due to other abilities." `reminders: ["Abnormal"]` — **one** copy. |
| `engine/src/main/resources/botc/data/night_and_jinxes.json:366` (first, idx 71) and `:467` (other, idx 94) | Last character step before `DAWN` on both nights. Correct — the Mathematician must see the whole night. |
| `engine/src/main/resources/botc/data/night_and_jinxes.json:24-32` | Two of the four official jinxes present: Chambermaid and Lunatic. **Drunk and Marionette jinxes are missing.** |
| `engine/src/main/resources/botc/data/night_guide.json:468-491` | Prose for both nights, mentioning "such as drunk or poisoned players getting false information", one `message` show card. |
| `engine/src/main/kotlin/com/clocktower/engine/InfoCalc.kt:34` | `mathematician` is in `supports()`… |
| `engine/src/main/kotlin/com/clocktower/engine/InfoCalc.kt:77-80` | …but the implementation is a **static stub**: `headline = "Count abilities that malfunctioned since dawn"`, `caveats = ["Track malfunctions manually — drunk/poisoned players whose ability 'worked' abnormally count."]`. |
| `app/src/main/java/com/clocktower/grimoire/ui/screens/NightScreen.kt:886-901` | The full-screen number chip requires `result.headline.takeWhile { it.isDigit() }.toIntOrNull() != null`. The stub headline starts with "C", so **no number chip is generated**. |
| `app/src/main/java/com/clocktower/grimoire/ui/screens/NightScreen.kt:903-930` | The false-info block needs a leading number or YES/NO. Never fires for this stub, even under a Vortox. |
| `app/src/main/java/com/clocktower/grimoire/ui/screens/NightScreen.kt:319-339` + `GameActions.kt:194-201` | Placing "Abnormal" from the night tray takes the `placeExclusiveReminder` branch (one copy in `allReminders`), so **only one Abnormal token can exist in the whole grimoire**. |
| `engine/src/main/kotlin/com/clocktower/engine/GameActions.kt:218-242` | `("mathematician","Abnormal")` is in neither `EXPIRES_AT_DAWN` nor `EXPIRES_AT_DUSK`. |
| `engine/src/main/kotlin/com/clocktower/engine/StatusEffects.kt:14-46` | `derivedPoison` (No Dashii adjacency) and `isImpaired` — the raw material a ledger would need, already present. |
| `engine/src/test/kotlin/com/clocktower/engine/FullGamePlaytestTest.kt:876-879, 953, 1012, 1052` | The playtest harness asserts headline "Count abilities" and then has the *test script* supply the number by hand ("One malfunction (poisoned Oracle) was tracked; Otis was shown 1"). The engine computes nothing. |

**What already works — one line each:**

- Night order (both nights, last before dawn) is correct.
- The Chambermaid jinx is satisfied by accident: `chambermaid()` (`InfoCalc.kt:469-484`)
  counts wakers from the night-order list, and the Mathematician is on it.
- `characters.json` ability text matches the current official wording.

**Storyteller's experience today:** the Mathematician step opens with a gold line that is
not an answer ("Count abilities that malfunctioned since dawn") and a red line that tells
you to do the job yourself. There is no number, no chip to flash a number, no list of what
went wrong tonight, and no false-number options even under a Vortox. To show the answer you
leave the step, open the menu → "All tokens" → Signals → tap a digit
(`ShowCards.kt:380-395`). The "Abnormal" token is placeable exactly once, grimoire-wide,
and never clears.

## Defects and gaps

1. **P0 · The app tells the storyteller to do the entire job by hand while holding all the
   inputs.** `InfoCalc.kt:77-80` returns a fixed string. The engine already knows, at the
   moment the step is opened: every impaired player (`StatusEffects.isImpaired`,
   `StatusEffects.kt:36-46`), everyone poisoned positionally (`derivedPoison`,
   `StatusEffects.kt:14-33`), everyone carrying a "No ability" token, whether a Vortox is
   alive (`InfoCalc.kt:161`), which info steps ran and what their caveats were, and which
   deaths were blocked (`StatusEffects.deathNotes`, `StatusEffects.kt:52-129`). None of it
   is used.

2. **P0 · No number can be flashed from the step.**
   Because the stub headline is not numeric, `NightScreen.kt:886-895` never renders a
   "Show N full-screen" chip and `NightScreen.kt:903-930` never renders false-number chips.
   The single most-used control in every other numeric step is missing here.
   **Repro:** expand the Mathematician step on any night.

3. **P0 · A Vortox does not force a false number.**
   Under a Vortox the Mathematician's own number must be false (wiki example 3). The
   `commonCaveats` Vortox line (`InfoCalc.kt:160-164`) is appended and displayed in red,
   but with no number to invert and no chips, the storyteller gets an instruction they
   cannot act on inside the app.

4. **P1 · Only one `Abnormal` token can exist grimoire-wide.**
   `NightScreen.kt:319-339` → `placeExclusiveReminder` (`GameActions.kt:194-201`). The
   run-book requires one per malfunctioning player, on that player's seat, potentially
   many at once. The seat-sheet path (`SeatSheet.kt:112-114`) stacks correctly, so the two
   entry points disagree. **Repro:** tray → "Abnormal" → seat A, then tray → "Abnormal" →
   seat B: seat A's token vanishes.

5. **P1 · `Abnormal` tokens never expire.**
   "Remove all ABNORMAL reminders" after the reveal; nothing in `GameActions.kt:218-242`
   does. Tokens from night 2 will still be there on night 5, silently inflating the count
   a diligent storyteller makes by hand.

6. **P1 · Two of the four official jinxes are missing from the data.**
   `night_and_jinxes.json:24-32` has Chambermaid and Lunatic; **Drunk** and **Marionette**
   are absent. These two are precisely the jinxes that override the "another character's
   ability" clause — without them the storyteller correctly concludes that a Drunk's false
   info does *not* count, which is wrong on a script with both. The "Jinxes in play"
   dialog (`GameExtras.kt:200-232`) will therefore stay silent.

7. **P1 · The Lunatic jinx is inert.**
   "The Mathematician learns if the Lunatic attacks a different player than the real Demon
   attacked." The app already tracks the Lunatic's fake attacks as
   `lunatic:"Attack 1/2/3"` reminders (`GameActions.kt:222-224`, surfaced in the Demon's
   step detail at `NightOrder.kt:157-172`) and the real kill goes through `DemonKillPanel`
   (`NightScreen.kt:534-638`). Nothing compares them.

8. **P1 · The "not your own ability" rule is never stated.**
   Neither `characters.json:852-865` nor `night_guide.json:468-491` says the Mathematician
   does not count their own malfunction. A poisoned Mathematician being told to "give a
   false number" and *also* to count themselves is a common table error.

9. **P1 · The "counts players, not events" rule is never stated.**
   The app's phrasing "number of players whose ability malfunctioned" is right;
   `night_guide` and the InfoCalc caveat both say "abilities", which invites double
   counting.

10. **P2 · The "since dawn" window is undefined for night 1.**
    Nothing tells the storyteller that on night 1 the window is the night so far.

11. **P2 · Nothing that *would* be a malfunction is logged when it happens.**
    Every night step that produces a caveat (misregistration chosen, impaired info given,
    Vortox lie) is exactly a malfunction candidate, and it is discarded the moment the
    panel closes. Same for a blocked death in `DemonKillPanel` (`NightScreen.kt:586-590`
    prints `deathNotes` and then throws them away).

12. **P3 · `characters.json` lists `reminders: ["Abnormal"]` once**; the character needs
    several.

## Proposed behaviour (spec)

The Mathematician is the character that forces the app to keep a **malfunction ledger**.
The ledger is proposed by the engine and confirmed by the storyteller — never decided
silently, because several of the official cases are judgement calls ("would the ability
have done the same thing anyway?").

### Engine model

```kotlin
// GameState.kt — new
@Serializable
data class Malfunction(
    val id: Long,
    /** The night this window belongs to (the Mathematician's next reveal). */
    val cycle: Int,
    /** Whose ability worked abnormally — this is who gets the ABNORMAL token. */
    val playerId: Long,
    val reason: String,
    /** ENGINE = auto-proposed, STORYTELLER = added by hand. */
    val source: MalfunctionSource,
    /** Storyteller's ruling; PENDING candidates are shown but not counted. */
    val status: MalfunctionStatus,   // PENDING | CONFIRMED | DISMISSED
)
// GameState gains: val malfunctions: List<Malfunction> = emptyList()
```

### Auto-proposal (`Malfunctions.propose(state, lookup): List<Malfunction>`)

Every proposal is `PENDING` until the storyteller taps it. Candidates, each with a
one-line reason in storyteller voice:

| trigger the engine can already see | proposed player | reason text |
|---|---|---|
| `StatusEffects.isImpaired(p)` **and** `p` has a step on tonight's sheet **and** `p` is alive | `p` | "Marta (Oracle) is poisoned — her number was false." |
| `StatusEffects.derivedPoison` entry | that player | "Bo is poisoned by the No Dashii." |
| `p` carries a `"No ability"` token placed by *another* character (Fearmonger, Fool spent, Professor spent…) | `p` | "Kai has no ability (Fearmonger)." |
| an alive `vortox` and `q` is a Townsfolk who was given information tonight | `q` (each) | "Vortox: Hana (Empath) was given false information." |
| a misregistration option was *taken* in an info step (Spy/Recluse) | the **info holder** | "The Recluse registered as evil to the Chef." |
| a death was attempted and blocked, or occurred despite protection, per `deathNotes` | the **protected player** | "The poisoned Soldier died to the Imp." |
| an alive `drunk` seat whose believed ability ran tonight | the Drunk | "Drunk jinx: Kai's information was false." *(only when the Drunk×Mathematician jinx is in play)* |
| an alive `marionette` seat whose believed ability ran tonight | the Marionette | "Marionette jinx: Ari's information was false." |
| `lunatic:"Attack N"` targets ≠ the real Demon's kill target | the Lunatic | "Lunatic jinx: the Lunatic attacked Bo, the Demon attacked Hana." |

Explicitly **not** proposed (and stated in the UI as "these do not count on their own"):
- an impaired player whose ability would have had the same effect anyway
  (the storyteller must add it by hand or, better, dismiss the auto-proposal — hence
  `PENDING`, e.g. the poisoned Snake Charmer who chose a Townsfolk);
- the **Mathematician's own** malfunction — the Mathematician is filtered out of the
  proposal list entirely;
- a player being simply drunk or poisoned with no ability that fired.

De-duplicate by `playerId` within a cycle: the count is players, not events.

### Night step

- **when:** `both` (first and other night), last character step before `DAWN`.
- **wake condition:** holder is **alive**.
- **targets:** none.
- **information:** `Answer.Count(n = malfunctions.count { it.cycle == state.cycle &&
  it.status == CONFIRMED && it.playerId != holderId }, min = 0, max = state.players.size)`.
  `detail` lists the confirmed entries; `caveats` lists the still-`PENDING` ones so the
  storyteller resolves them before showing a number ("2 candidates still undecided").
- **immediate effects on completion:** place one `mathematician:"Abnormal"` token on each
  confirmed player's seat (so the grimoire matches the run-book), then, when the step is
  ticked, remove them all and close the window.
- **expiry:** add `("mathematician","Abnormal")` to `EXPIRES_AT_DAWN` (`GameActions.kt:218-225`)
  as a backstop, and clear confirmed/dismissed `Malfunction` rows for cycles < current at
  dawn.
- **impaired / false alternative:** via `InfoCalc.obligation` (see `artist.md`).
  `MUST_LIE` under an alive Vortox — and note in the UI that the Vortox *also* inflates the
  true count, so the lie should not be "0" if half the town got false info.
  False-number chips drawn from `0..(true + 3)` minus the truth, nearest-first.
- **visibility:** nothing shown to Demon/Minions/Lunatic.
- **day-time inputs:** the ledger must be addable during the **day** too — the window is
  "since dawn", and day-time malfunctions are real (a poisoned Slayer's shot failing, a
  drunk Gossip's statement not killing, a Witch curse that did nothing, an Artist answered
  falsely). Surface an **"Ability malfunctioned…"** button on the Day tab and on each seat
  sheet that appends a `STORYTELLER`-sourced `Malfunction` in two taps.
- **interactions/jinxes to handle explicitly:** Chambermaid (already satisfied), Lunatic
  (implement the comparison), Drunk and Marionette (add the jinx data **and** the
  proposals), Vortox (mandatory lie + inflated true count).

### UI text the step should display

> **Mathematician — how many players' abilities worked abnormally since dawn?**
> Count players, not events. Never count the Mathematician's own ability.
>
> **2**
> ✓ Marta (Oracle) — poisoned, her number was false
> ✓ Bo (Soldier) — poisoned, died to the Imp
> ? Kai (Snake Charmer) — poisoned, but chose a Townsfolk: **did anything change?**
>   `[ counts ] [ doesn't count ]`
> `[ Show 2 full-screen ]`  `[ + add a malfunction ]`

Under a Vortox:
> **VORTOX — this number MUST be false.** The Vortox also makes every Townsfolk's
> information false, so the true count is high (**6**). `[ 4 ] [ 5 ] [ 7 ] [ 3 ]`

### Data changes

- `night_and_jinxes.json` — add:
  ```json
  { "id1": "drunk", "id2": "mathematician",
    "reason": "The Mathematician learns if the Drunk's ability yielded false info or failed to work properly." },
  { "id1": "marionette", "id2": "mathematician",
    "reason": "The Mathematician learns if the Marionette's ability yielded false info or failed to work properly." }
  ```
- `characters.json:852-865` — `"reminders": ["Abnormal","Abnormal","Abnormal","Abnormal","Abnormal"]`
  until the `ReminderSpec` refactor lands (see `juggler.md`).
- `night_guide.json:468-491` — rewrite both entries:
  > "Wake the Mathematician. Show the hand signal for the number of **players** (0, 1, 2,
  > etc.) whose ability worked abnormally since dawn **because of another character's
  > ability**. A player who malfunctioned twice still counts once. Never count the
  > Mathematician's own ability. An ability that would have had the same effect anyway does
  > not count. On the first night the window is the night so far. Remove the Abnormal
  > tokens afterwards. If the Vortox is in play the number **must** be false; if the
  > Mathematician is drunk or poisoned you **may** give a false number."

## Tests to add

1. **Poisoned Oracle counts (wiki example 1).**
   *Given* an Oracle with a `poisoner:"Poisoned"` token and every other ability normal,
   *when* `Malfunctions.propose` runs on that night, *then* it proposes exactly one entry,
   for the Oracle; *and* the Mathematician answer with it confirmed is **1**.

2. **Poisoned-but-no-difference is a candidate, not a count (wiki example 2).**
   *Given* a poisoned Snake Charmer who chose a Townsfolk and a drunk Juggler given the
   true number, *then* both are proposed as `PENDING`, and with both dismissed the answer
   is 0 (plus 1 if the Savant entry is confirmed).

3. **The Mathematician never counts themselves.**
   *Given* the Mathematician is poisoned, *then* no proposal names the Mathematician, and
   the answer excludes them.

4. **Players, not events.**
   *Given* two separate reasons for the same player in one cycle,
   *then* the count contributes 1.

5. **Window resets.**
   *Given* confirmed malfunctions for cycle 2, *when* `advancePhase` reaches night 3,
   *then* the cycle-3 count starts at 0 and no `mathematician:"Abnormal"` reminders remain
   from cycle 2 (currently fails on the reminder half).

6. **Abnormal tokens stack (currently fails).**
   *Given* three confirmed malfunctions on three seats, *then* three
   `mathematician:"Abnormal"` reminders exist, one per seat.

7. **Vortox inflates the truth and forces a lie (wiki example 3).**
   *Given* an alive Vortox and five Townsfolk who received information plus a drunk Witch
   whose curse failed, *then* the true count is 6 (or 5, excluding the Mathematician if
   they are among the five — assert the exact expectation the implementation chooses) and
   `obligation == MUST_LIE`.

8. **Recluse misregistration marks the reader, not the Recluse.**
   *Given* a Chef whose count used the Recluse as evil, *then* the proposal names the
   **Chef**.

9. **Poisoned Soldier dying marks the Soldier.**
   *Given* a Soldier with a Poisoned token killed by the Demon, *then* the proposal names
   the Soldier.

10. **Drunk and Marionette jinxes (currently fail — data missing).**
    *Given* `drunk` + `mathematician` in play, *then* `activeJinxes` contains the pair,
    *and* a Drunk whose believed ability ran tonight is proposed.
    Same for `marionette`.

11. **Lunatic jinx (currently fails).**
    *Given* `lunatic:"Attack 1"` on Bo and the real Demon killing Hana,
    *then* one proposal names the Lunatic; *given* both attacked Hana, *then* none does.

12. **Numeric answer enables the show chip (currently fails).**
    *Given* any Mathematician step, *then* `InfoCalc.compute` returns a headline whose
    leading token is a digit, so `NightScreen`'s number chip is generated.

13. **Day-time additions land in the right window.**
    *Given* a `STORYTELLER` malfunction added during day 2, *then* it is counted by the
    night-3 Mathematician (the "since dawn" window covers day 2 and night 3).
