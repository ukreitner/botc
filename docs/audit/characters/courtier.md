# Courtier (courtier) — Bad Moon Rising Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Courtier> (fetched 2026-08-25).

Current ability text:

> "Once per game, at night, choose a character: they are drunk for 3 nights & 3 days."

How to Run (wiki, quoted):

> "Tonight, mark them with the Courtier's **DRUNK 1** reminder. The next night, replace the
> **DRUNK 1** reminder with the **DRUNK 2** reminder. The next night, replace the **DRUNK 2**
> reminder with the **DRUNK 3** reminder. At dusk on the next night, remove the **DRUNK 3**
> reminder, and **the Courtier loses their ability** — mark them with the **NO ABILITY**
> reminder."

> "After the Courtier chooses a character to make drunk, do not wake the Courtier for the rest
> of the game."

> "The Courtier chooses a character, not a player." … "The Courtier does not learn if they
> were successful or not, so they might choose a character that is not in play."

Key points and edge cases:

- **A character, never a player.** The Courtier points at a character icon on the character
  sheet. If that character is in play, that *player* becomes drunk. If it is not in play,
  **nothing happens but the ability is spent** and the Courtier is not woken again.
- **Duplicates:** *"In other editions, there can be multiple copies of the same character in
  play. The Courtier only makes one of them drunk"* — the Storyteller picks which.
- **Duration is 3 nights & 3 days**, starting the night of the choice. Counting the wiki's
  own token schedule: choose on night N → drunk nights N, N+1, N+2 and days N, N+1, N+2 →
  the token comes off at dusk before night N+3.
- **The NO ABILITY token goes on the Courtier when the drunkenness ends**, not when the
  ability is used — but functionally the Courtier is spent the moment they choose, because
  they are never woken again.
- **A drunk/poisoned Courtier's choice does nothing but is still spent:** *"The Courtier uses
  their ability while drunk. Nothing happens, but they are not woken again."*
- The Courtier **does wake on the first night** — the Courtier is on the official first-night
  sheet (and on the app's, at index 29).
- Strategy notes that matter for storytelling: targeting the Demon blocks its kills for three
  nights; a drunk Assassin/Godfather cannot use their ability; a drunk Grandmother "breaks the
  death connection"; a drunk Tinker avoids the random death.

Jinxes:

- **Summoner:** the app's text is *"If the Summoner is drunk on the 3rd night, the Summoner
  chooses which Demon, but the Storyteller chooses which player becomes that Demon."* The wiki
  summary I fetched paraphrases the Summoner jinx as *"If the living Summoner loses their
  ability, the Storyteller gains it."* These are two different statements; I could not resolve
  which is the current jinx text from the page I fetched. **Flagging as uncertain — verify
  against the official jinx list before changing the data.**
- **Vizier:** *"A Vizier losing their ability learns this fact and cannot die during the day."*
  **This jinx is missing from `night_and_jinxes.json`.**

## What the app does today

- `engine/src/main/resources/botc/data/characters.json:372-386` — ability text matches the
  wiki. `reminders: ["Drunk 3", "Drunk 2", "Drunk 1", "No ability"]`.
- `night_and_jinxes.json:324` (firstNight index 29) and `:388` (otherNight index 15) —
  positions are correct.
- `night_and_jinxes.json:245` — the `summoner`/`courtier` jinx exists (text uncertain, above).
- `night_guide.json:217-226` — good prose covering the not-in-play case, the drunk-Courtier
  case, and the countdown; but it instructs a **countdown** ("reduce the counter (Drunk 3 to
  Drunk 2 to Drunk 1)") while the almanac describes a **count-up** (DRUNK 1 → 2 → 3).
- `NightScreen.kt:263-279` — because `Character.ability` starts with "Once per game", the
  night tray offers a **"Mark spent"** chip that places `PlacedReminder("courtier", "No
  ability")` on the Courtier. That is the one automation the Courtier gets.
- `NightScreen.kt:283-295` — the tray lets the storyteller tap "Drunk 3" and then a seat. All
  four Courtier reminder labels are single-copy, so `placeExclusiveReminder` is used and the
  token moves rather than duplicating (`GameActions.kt:194-201`). **Works.**
- `StatusEffects.isImpaired` (`StatusEffects.kt:36-46`) matches any reminder label containing
  "drunk", so once "Drunk 3" is placed the target is correctly treated as impaired everywhere
  — including the Demon-kill warning (`NightScreen.kt:548-554`) and every `InfoCalc` caveat
  (`InfoCalc.kt:132-153`). **Works, and is the app's best existing behaviour here.**

Nothing else exists. There is no character picker, no "is that character in play?" resolution,
no countdown, no expiry, no suppression of the choice branch once spent.

## Defects and gaps

1. **P0 · The 3-night countdown never advances and never expires.** Neither `EXPIRES_AT_DAWN`
   nor `EXPIRES_AT_DUSK` (`GameActions.kt:218-242`) contains any Courtier token, and
   `advancePhase` (`GameActions.kt:258-263`) has no decrement logic. A player made drunk on
   night 2 stays drunk for the rest of the game unless the storyteller notices and hand-swaps
   tokens every single night. This is the same class of bug as the reported Devil's Advocate
   failure. Repro: night 2, place "Drunk 3" on a seat, then advance Dawn/Dusk repeatedly — the
   token is still there on night 7 and `isImpaired` still returns true.

2. **P0 · "Mark spent" is the only spent-tracking, and nothing consumes it.** After placing
   `courtier:No ability`, the Courtier's night step still renders in full every subsequent
   night with the *whole* conditional sentence including "The Courtier either shows a 'no'
   head signal, or points to a character on the sheet"
   (`characters.json:379`, rendered verbatim by `NightOrder.kt:147-148`). The storyteller is
   invited to wake a Courtier who must never be woken again. Repro: mark spent, advance to the
   next night, open the Courtier step.

3. **P1 · The Courtier chooses a *character*, and the app has no character picker for it.**
   Every night affordance in the app is seat-based: the tray places tokens on seats
   (`NightScreen.kt:283-295`), `InfoCalc` picks seats (`NightScreen.kt:838-861`),
   `ResolutionPicker` picks seats (`NightScreen.kt:643-687`). The storyteller must look at the
   named character, scan the grimoire by eye to find whether it is in play and which seat
   holds it, then place the token. The app already knows the answer. There is also a "Sheet"
   show card (`NightScreen.kt:254-262`) to let the Courtier point — good — but the result of
   the pointing is not captured anywhere.

4. **P1 · Nothing records that the choice happened, or what was chosen.** There is no
   structured night-choice record (`GameState.kt:94-115` has `nightStepsDone: Set<String>`
   and nothing else), so the game log (`GameExtras.kt:46-106`) never mentions the Courtier,
   and undo/redo aside there is no way to see later "who did the Courtier drunk, and when".

5. **P1 · The chose-a-not-in-play-character branch is unsupported.** Rules: nothing happens,
   ability spent, never wake again. The app has no way to express "spent with no effect" other
   than tapping "Mark spent" and remembering not to place a token.

6. **P1 · A drunk/poisoned Courtier's choice is not handled.** Rules: no effect, still spent.
   The step gives no warning that the Courtier is impaired even though
   `InfoCalc.impairments(state, lookup, holder)` (`InfoCalc.kt:132-153`) already computes
   exactly that string — it is only wired into `InfoCalc.compute`, and the Courtier is not an
   info role, so it never runs.

7. **P2 · Token label direction contradicts the almanac.** `characters.json:380-385` and
   `night_guide.json:217-226` count **down** (Drunk 3 → 2 → 1); the wiki counts **up**
   (DRUNK 1 → 2 → 3). Both are playable, but a storyteller cross-checking against physical
   tokens or the official night sheet will be confused. The official night-sheet phrasing
   ("Reduce the remaining number of days the marked player is drunk") is about the remaining
   duration, not the number printed on the token.

8. **P2 · The Vizier/Courtier jinx is missing** from `night_and_jinxes.json`, so it never
   surfaces in `SeatSheet.kt:223-234` or the active-jinx dialog.

9. **P3 · No day-start reminder.** "Elena is Courtier-drunk today (2 days left)" is exactly
   the kind of line the storyteller wants at day start and never gets — there is no day
   briefing surface anywhere in the app.

## Proposed behaviour (spec)

### Structured record

Add a general night-choice record to `GameState.kt` (shared with Exorcist, Gambler,
Chambermaid, Monk, Balloonist — see the cross-cutting note at the end of each file):

```kotlin
@Serializable
data class NightChoice(
    val cycle: Int,
    val sourceId: String,          // "courtier"
    val actorId: Long,             // the Courtier's seat
    val targetIds: List<Long> = emptyList(),   // resolved seat, if any
    val characterId: String? = null,           // the character they pointed at
    val declined: Boolean = false,
    val note: String = "",         // e.g. "not in play"
)
```

`GameState.nightChoices: List<NightChoice> = emptyList()`, with
`GameActions.recordNightChoice(state, choice)` and
`GameActions.lastChoice(state, sourceId, beforeCycle): NightChoice?`.

### Courtier step

- **when:** both first and other nights. Wake condition:
  `holder.alive && holder.reminders.none { it.label == "No ability" } &&
   state.nightChoices.none { it.sourceId == "courtier" }`.
  If spent, the step **still renders** while a countdown token is on the board, but only as a
  read-only status line — never as an invitation to wake.
- **targets:** one **character**, not a player. The picker is a character grid over
  `gameData.resolve(state.script)` (the same source the "Sheet" card uses,
  `NightScreen.kt:254-262`), searchable, sorted **in-play-first** exactly like
  `GuideShowDialog` already does (`NightScreen.kt:405-434`), with in-play entries annotated
  with the holder's name. Plus a **[They shook their head — no choice]** button (the ability
  is then *not* spent; the Courtier may use it another night).
  Where two seats hold the chosen character, the app asks which one.
- **immediate effects** on choosing character `C` (one undoable update):
  - Record `NightChoice(cycle, "courtier", holderId, targetIds, characterId = C)`.
  - If `C` is in play and the Courtier is **not** impaired:
    `placeExclusiveReminder(target, PlacedReminder("courtier", "Drunk 1"))`.
    (Use the official count-up labels; see data changes.)
  - If `C` is not in play, or the Courtier is impaired: place no token; record
    `note = "not in play"` / `note = "Courtier was impaired"`.
  - Always: `placeExclusiveReminder(holderId, PlacedReminder("courtier", "No ability"))` —
    deviating from the almanac's "at the end of the drunkenness" only in that the app marks
    spent immediately, which is what actually gates the wake. Render the token label in the
    grimoire as `Spent` for clarity if the label is confusing.
  - Auto-tick the step.
- **deferred effects / countdown:** the countdown advances at **dusk** (the DAY → NIGHT
  transition in `GameActions.advancePhase`, `GameActions.kt:261-262`), because that is the
  boundary where "another night & day" completes. Add a general table alongside the existing
  expiry tables:

  ```kotlin
  /** Tokens that step to the next label at dusk, and vanish after the last. */
  private val COUNTDOWN_AT_DUSK: Map<Pair<String, String>, String?> = mapOf(
      ("courtier" to "Drunk 1") to "Drunk 2",
      ("courtier" to "Drunk 2") to "Drunk 3",
      ("courtier" to "Drunk 3") to null,   // removed
  )
  ```

  Verification of the schedule: chosen night N → `Drunk 1` placed. Dusk of day N → `Drunk 2`.
  Dusk of day N+1 → `Drunk 3`. Dusk of day N+2 → removed. Drunk on nights N, N+1, N+2 and
  days N, N+1, N+2 = **3 nights & 3 days**. Matches the almanac exactly.
- **expiry:** handled by `COUNTDOWN_AT_DUSK` above; `courtier:No ability` never expires.
- **information:** none — the Courtier learns nothing, ever. No show cards other than the
  existing "Sheet" card so they can point.
- **visibility:** nothing is shown to the Demon/Minions/Lunatic.
- **day-time inputs:** none.
- **status lines the app must derive** (this is the whole point — the ST should input once and
  read consequences forever):
  - On the Courtier's night step once spent, and on every subsequent night:
    `Courtier spent (Night 2, chose the Assassin). Elena is drunk — 2 nights & 2 days left.`
    or `Courtier spent (Night 2, chose the Pacifist — not in play). No effect.`
  - On the drunk player's own night step: the existing impairment machinery already fires via
    `isImpaired`; additionally show `Courtier-drunk — 2 nights & 2 days left` so the ST knows
    when it lifts.
  - At day start (see the cross-cutting day-briefing recommendation):
    `Elena is Courtier-drunk today (day 2 of 3).`
- **interactions/jinxes:**
  - **Demon chosen:** the "Drunk N" token already makes `DemonKillPanel` warn
    (`NightScreen.kt:548-554`). Keep and strengthen: name the Courtier as the source.
  - **Drunk / Lunatic / Marionette:** the Courtier chooses a *character*. The `drunk` player's
    character **is** the Drunk; choosing the Townsfolk token they are shown does nothing (that
    character is not in play). The picker's in-play marking must use `characterId`, **not**
    `shownCharacterId`/`nightRoleId`, or it will lie.
  - **Recluse/Spy:** irrelevant — the Courtier names a character and the Storyteller checks
    the grimoire, so no misregistration applies.
  - **Summoner jinx:** surface `night_and_jinxes.json:245` in the step when both are in play
    (verify the text first — see Sources).
  - **Vizier jinx (to add):** "A Vizier who loses their ability learns this fact and cannot die
    during the day." When a Courtier makes the Vizier drunk, the step must tell the ST to
    inform the Vizier and to block day deaths for them.
  - **Character change while drunk** (Pit-Hag, Barber, Snake Charmer, Fang Gu jump): the
    "Drunk N" token stays with the **seat**, not the character. `swapCharacters`
    (`GameActions.kt:99-115`) already leaves reminders on the seat. **Works.**

### UI text the step should display

- Unspent: `Courtier — they either shake their head, or point at a character on the sheet.`
- Picker header: `Which character did they point at?  (in-play characters first)`
- In play: `Assassin — Elena. She is drunk for 3 nights & 3 days.`
- Not in play: `Pacifist isn't in play. Nothing happens — but the ability is spent.`
- Impaired Courtier: `Felix is poisoned — the choice has no effect, but it is still spent.`
- Spent, running: `Spent Night 2 (Assassin). Elena: 2 nights & 2 days of drunkenness left.`
- Spent, finished: `Spent Night 2. Drunkenness ended at dusk on Day 4. Do not wake the Courtier.`

### Data changes

- `characters.json:380-385` — reorder `reminders` to `["Drunk 1", "Drunk 2", "Drunk 3", "No
  ability"]` to match the almanac's count-up. (If the project prefers the countdown, keep the
  data and fix the wiki-drift note in `night_guide.json` instead — but pick one and make the
  engine's `COUNTDOWN_AT_DUSK` agree.)
- `night_guide.json:217-226` — rewrite the countdown sentence to match, and add:
  *"After the Courtier chooses, never wake them again. The app advances the drunkenness
  counter for you at dusk."*
- `night_and_jinxes.json` — add `{"id1": "courtier", "id2": "vizier", "reason": "A Vizier who
  loses their ability learns this, and cannot die during the day."}`; verify the existing
  `summoner`/`courtier` text (`:245`).

## Tests to add

1. **Countdown advances and expires.** Given a BMR game where seat 3 carries
   `PlacedReminder("courtier", "Drunk 1")` placed on Night 2, When `advancePhase` is applied
   through dusk of day 2, day 3 and day 4, Then the token reads `Drunk 2`, then `Drunk 3`,
   then is **absent**; and `StatusEffects.isImpaired(state, lookup, seat3)` is `true` on
   nights 2, 3 and 4 and `false` on night 5. **Fails today** (no decrement at all).

2. **Exactly 3 nights & 3 days.** Given the choice on Night 1, Then the seat is impaired
   during nights 1–3 and days 1–3 and not on night 4. Assert on both `phase == NIGHT` and
   `phase == DAY` snapshots.

3. **Spent Courtier is not offered a choice.** Given `courtier:No ability` on the Courtier's
   seat and a recorded `NightChoice(sourceId = "courtier")`, When the night sheet is built for
   night 3, Then the Courtier step's detail contains no "points to a character" invitation
   and instead reports the remaining countdown. **Fails today** — `NightOrder.kt:147-148`
   renders `otherNightReminder` verbatim regardless.

4. **Not-in-play choice spends without a token.** Given the Courtier chooses `pacifist` and no
   seat holds it, Then no player gains a Courtier drunk token, a `NightChoice` with
   `note = "not in play"` is recorded, and the Courtier is marked spent.

5. **Impaired Courtier's choice has no effect but is spent.** Given the Courtier carries
   `poisoner:Poisoned`, When they choose an in-play character, Then the target is **not**
   impaired and the Courtier is spent.

6. **Duplicate character disambiguation.** Given two seats hold `villageidiot` and the
   Courtier chooses `villageidiot`, Then the resolver requires an explicit seat and marks
   exactly one of them.

7. **Courtier-drunk Demon fails to kill.** Given a `Drunk 1` token on the Po's seat, Then
   `StatusEffects.isImpaired` is true for the Po (guards the existing
   `DemonKillPanel` warning at `NightScreen.kt:548-554`). Passes today — keep as a regression.

8. **Token follows the seat through a character swap.** Given seat 3 is Courtier-drunk and
   `swapCharacters(state, 3, 5)` runs, Then seat 3 still carries the token and seat 5 does not.
