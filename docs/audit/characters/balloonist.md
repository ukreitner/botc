# Balloonist (balloonist) — Experimental Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Balloonist> (fetched via
`action=parse&prop=wikitext`, 2026-08-25).

Current ability text (verbatim):

> "Each night, you learn a player of a different character type than last night. [+0 or +1 Outsider]"

**Summary bullets (verbatim):**

- "Each time the Balloonist learns a player, the player must have a different character type to the previously shown player."
- "The Balloonist does not learn the character type of the player they learn."
- "The shown player can be alive or dead."
- "The shown player can be good or evil."
- "If the Balloonist is drunk or poisoned, they may learn a character of the same type as the previously shown player. When the Balloonist becomes sober and healthy, they must learn a player of a different character type to the previously shown player."
- "During setup, the Storyteller may choose to add an Outsider due to the Balloonist's ability."

**How to Run (verbatim):**

> During setup, you may add an Outsider.
>
> When preparing the first night, mark any player with the **KNOW** reminder. When preparing each night afterwards, mark a character of a different type than the current with the **KNOW** reminder.
>
> Each night, wake the Balloonist. Point to the player marked **KNOW**. Put the Balloonist to sleep.

**Examples (verbatim):**

1. "Abdallah is the Vizier, Lewis is the High Priestess, and Sarah is the Politician. On the first night, the Balloonist learns Abdallah. On the second night, the Balloonist learns Lewis. On the third night, the Balloonist learns Sarah." *(Minion → Townsfolk → Outsider.)*
2. "Julian is the Nightwatchman, Alex is the Sailor, and Lachlan is the Puzzlemaster. On the first night, the Balloonist learns Julian. On the second night, the Poisoner chooses the Balloonist. Because the Balloonist is poisoned, the Storyteller chooses to show the Balloonist another Townsfolk, and the Balloonist learns Alex. On the third night, the Balloonist is sober and healthy, and learns Lachlan, who is a different character type to Alex."

Example 2 pins down the exact semantics of the constraint: it is always
relative to **the player shown last night**, even when that player was shown
illegally because the Balloonist was impaired. It is *not* "the last legal
type" and *not* "a type never yet seen".

**Relevant Tips (storyteller-facing consequences, verbatim):**

- "Look out for misregistration! The Spy and Recluse may show up as different character types. In fact, if your Storyteller is feeling exceptionally devious, they could show you the Recluse every single night (Storytellers, please don't)."
- "Uniquely, if the Demon is a Vortox, then every player you learn must be the same character type as the previous player."

**Jinx (verbatim):**

| With | Text |
|---|---|
| Marionette | "If the Marionette thinks that they are the Balloonist, an Outsider might have been added during setup." |

**Character types** for this ability are the five in `Team` that a seat can
hold: Townsfolk, Outsider, Minion, Demon **and Traveller**.

**Note on ability-text drift.** The retired Balloonist read
*"Each night, you learn 1 player of each character type, until there are no
more types to learn. [+1 Outsider]"* with five reminder tokens
(`Seen Townsfolk`… `Seen Traveller`) — that is still what `bra1n/townsquare`'s
`roles.json` carries. The app's `characters.json` already has the **current**
text and the single `Know` token. Do not regress it from a townsquare-derived
source.

## What the app does today

Data:
- `characters.json:1263` — `ability` matches the current wiki text; `setup: true`; `firstNightReminder: "Point to any player."`; `otherNightReminder: "Point to a player with a different character type to the previously shown player."`; `reminders: ["Know"]`. **All correct and current.**
- `night_and_jinxes.json:355` (first night, index 60) and `:456` (other nights, index 83). **Correct.**
- `night_and_jinxes.json:90` — the Marionette jinx is present, worded "+1 Outsider might have been added" vs the wiki's "an Outsider might have been added". Cosmetic drift.
- `night_guide.json:873` — good `first` and `other` entries that already state the type rule, the impairment relaxation and the +1 Outsider note. `shows: []` in both.

Code:
- `InfoCalc.supports("balloonist")` is true (`InfoCalc.kt:34`) and `InfoCalc.balloonist` (`InfoCalc.kt:486`) is:
  ```kotlin
  val byType = ctx.players.mapNotNull { p -> ctx.character(p)?.let { c -> c.team to p } }
      .groupBy({ it.first }, { it.second })
  InfoResult(
      headline = "Show a player of a DIFFERENT character type than last night",
      detail = byType.entries.joinToString("\n") { (team, ps) -> "${team.displayName}: ${ps.joinToString { ctx.name(it) }}" },
  )
  ```
  i.e. a static roster grouped by team, with the type rule restated as prose.
  It has **no** knowledge of last night, and **no** `misregistrations(...)`
  call — unlike almost every other calculator in the file (`InfoCalc.kt:121`).
- `InfoCalc.targetsNeeded("balloonist")` returns 0 (`InfoCalc.kt:22`), so the step gets no target picker.
- `Setup.modifierFor` (`Setup.kt:121`) parses `[+0 or +1 Outsider]` correctly: `isChoice = true`, `boundedChoiceRegex` yields `choiceDeltas = {OUTSIDER: {0, 1}}`, `choiceTeams = {OUTSIDER}`, and `applyDelta(matches.last())` applies **+1** as the suggested default (`Setup.kt:206`). `allowedDistributions` (`Setup.kt:261`) correctly enumerates both, so `validateBag` accepts either.
- `NightToolTray` (`NightScreen.kt:193`) offers one "Know" chip; a single copy means `placeExclusiveReminder` (`GameActions.kt:194`), so the token moves each night — correct — but the previous night's type is destroyed in the process.
- `("balloonist","Know")` is in neither expiry table (`GameActions.kt:218/231`) — correct, it must persist.

Storyteller's actual experience: at the bag-building step the app silently
assumes **+1 Outsider** and never asks. Each night the Balloonist row shows a
paragraph and a full roster grouped by team; the ST must remember, unaided,
which player they pointed to last night and what type that player was, scan the
roster for a different type, tap "Know", tap a seat, and mentally note tonight's
type for tomorrow. Nothing warns them if they pick the same type twice; nothing
tells them the Balloonist is poisoned and may therefore repeat; nothing flags
that the Recluse they are about to show could be registered as three different
types. By night 4 the Balloonist's whole information chain lives in the ST's
head, and a single slip invalidates the character.

## Defects and gaps

1. **P0 · The "different type than last night" constraint is not enforced or
   even displayed.** Rules: the shown player "must have a different character
   type to the previously shown player". App: `InfoCalc.balloonist`
   (`InfoCalc.kt:486`) lists **all** players including last night's type. Repro:
   night 2 → the roster still offers every Townsfolk after a night-1 Townsfolk,
   with no marker. The single most common Balloonist storytelling error is
   repeating a type, and the app makes it maximally easy.
2. **P0 · Nothing records which player (or type) was shown.** Only the moving
   `Know` token exists, and it is overwritten. There is no night history, so
   after an undo/redo or a phone reload the ST has nothing. Note the app
   *already holds enough to do better today*: the seat currently holding
   `balloonist:Know` is last night's shown player, and its team is last night's
   type — `InfoCalc.balloonist` simply never looks.
3. **P1 · The `[+0 or +1 Outsider]` decision is never made explicitly, and the
   default is +1.** `Setup.kt:206` applies `matches.last()`, so the bag builder
   tells the ST "Need: … 2 outsiders" when the base is 1. Repro: 10 players +
   Balloonist → the "Need:" line (`SetupScreen.kt:373`) shows the +1 variant.
   Validation accepts both, so no error follows — but the ST is nudged toward a
   choice they never made, and later has no record of which they took (which
   matters for their own Outsider count reasoning and for the Marionette jinx).
4. **P1 · Impairment doesn't relax the rule in the UI.** Rules: a drunk/poisoned
   Balloonist may be shown the same type. App: `commonCaveats`
   (`InfoCalc.kt:158`) does emit "X is POISONED — give false info", but the
   phrase is wrong here: the Balloonist is not given *false* info, they are
   given *unconstrained* info. And the step still offers no different-type
   guidance either way.
5. **P1 · No misregistration caveat.** `InfoCalc.balloonist` is one of the very
   few calculators that never calls `misregistrations(ctx, …)`
   (`InfoCalc.kt:121`), even though the Spy (may register as Townsfolk or
   Outsider) and the Recluse (may register as Minion or Demon) are exactly the
   pieces that make Balloonist information interesting and are called out in
   the wiki's Tips. Repro: a Recluse in play → the step lists them only under
   "Outsider".
6. **P1 · Travellers are not called out as a character type.** They are a legal
   fifth type and are the ST's escape hatch when the other types run out.
   `InfoCalc.balloonist`'s `groupBy` will produce a `Traveller:` line, but only
   if a Traveller seat exists, and nothing tells the ST it counts.
7. **P2 · No target picker.** `targetsNeeded("balloonist") == 0`
   (`InfoCalc.kt:22`), so the only way to record the choice is the two-tap token
   tray at the bottom of the screen. Every other "point to a player" character
   in this scope has the same gap; the Balloonist needs it most because the
   choice must be validated.
8. **P2 · No "show the player" affordance.** The Balloonist is shown a *player*
   (the ST points), not a card, so no show-card is needed — but the step should
   say so explicitly, because the roster display looks like an info payload.
9. **P2 · The Vortox interaction is not surfaced.** With a Vortox alive,
   Townsfolk info must be false, which for the Balloonist means the shown
   player must be the **same** type as last night — the exact inverse of the
   normal rule. `commonCaveats` emits the generic "VORTOX in play — Townsfolk
   info must be FALSE" (`InfoCalc.kt:163`), which an ST could easily read as
   "show them a wrong player" rather than "show the same type".
10. **P2 · The Marionette jinx text is stale** (`night_and_jinxes.json:90`) —
    "+1 Outsider might have been added" should be "an Outsider might have been
    added during setup".
11. **P3 · No history view for the ST.** By night 4 the ST is holding a
    four-element sequence in their head that the Balloonist player will quote
    back at them.

## Proposed behaviour (spec)

### Engine data

Reuse the shared `NightAction` record proposed in `amnesiac.md`:

```kotlin
NightAction(
    night = state.cycle,
    characterId = "balloonist",
    playerId = balloonistSeatId,
    targetIds = listOf(shownPlayerId),
    shown = registeredTeam.name,   // the type as SHOWN, which may be a misregistration
)
```

`shown` must hold the **registered** type, not `Character.team`, so that a
Recluse shown as a Minion constrains tomorrow to non-Minion.

Also record the setup decision:

```kotlin
// GameState
val setupChoices: Map<String, String> = emptyMap()   // "balloonist" -> "+1 Outsider"
```

### Setup

- **when:** SETUP, bag contains `balloonist`.
- Non-blocking but sticky prompt (the choice is legal either way, so it should
  not gate "Begin night" — but it must be *made*):
  > **Balloonist: +0 or +1 Outsider?**
  > You may add one extra Outsider (replacing a Townsfolk). Base for 10 players: 1 Outsider.
  > [ Keep 1 Outsider ] [ Add a 2nd Outsider ]
- Write the answer to `setupChoices["balloonist"]`; feed it back into
  `Setup.adjustedDistribution` so the "Need:" line (`SetupScreen.kt:373`) shows
  the chosen variant, not the `matches.last()` default.
- Change `Setup.modifierFor`'s default for bounded choices from
  `matches.last()` to `matches.first()` **only** if no explicit choice is
  recorded, so the neutral option (+0) is the assumed one. (Check the other
  bounded-choice characters — Godfather `[-1 or +1 Outsider]`, Sentinel — before
  changing this globally; it may be safer to leave `modifierFor` alone and have
  the UI consult `setupChoices` first.)
- Surface the answer permanently in the grimoire header or the Balloonist's
  seat note: `Balloonist: +1 Outsider added` — the ST will be asked.

### Night action

- **when:** `both` (first and other nights). Wake condition: Balloonist seat is
  **alive**. (Dead Balloonists do not act.)
- **targets:** exactly **1**. Constraints:
  - may be **alive or dead**;
  - may be **good or evil**;
  - may be a **Traveller**;
  - may be the Balloonist themself (nothing forbids it);
  - **must have a registered character type different from `lastShown.type`**
    on nights ≥ 2, unless the Balloonist is impaired.
  - Picker: chips **grouped by character type** with headings
    `Townsfolk · Outsider · Minion · Demon · Traveller`; last night's type
    section is collapsed and its chips disabled with the reason
    **"same type as last night (Townsfolk)"**; the Spy and Recluse appear
    under **every** type they can register as, each labelled
    "Recluse — may register as Minion / Demon".
  - Default sort: types the Balloonist has never been shown first (a nicety the
    rules don't require but every ST wants).
- **immediate effects:** move exclusive reminder `balloonist:Know` to the
  target; append the `NightAction`.
- **deferred effects:** none.
- **expiry:** never — `balloonist:Know` persists and moves. Do **not** add it to
  either expiry table.
- **information:** the ST **points at a player**; nothing is shown on screen to
  the player. The step must say: **"Point to the marked player. Do NOT tell them the type."**
- **impaired alternative:** when `isImpaired(balloonist)`, replace the
  constraint banner with:
  > **Ana is DRUNK/POISONED — you may show ANY player, including the same type as last night.** Tomorrow's constraint is still measured against tonight's shown player.
  and enable every chip. This is wiki Example 2 exactly.
- **misregistration:** offer, at the moment of selection, a "shown as" type
  override for Spy/Recluse seats, which is what gets written to
  `NightAction.shown`.
- **visibility:** nothing to the Demon, Minions or Lunatic.
- **day-time inputs:** none.

### History strip (part of the step)

```
N1  Abdallah  (Minion)
N2  Lewis     (Townsfolk)
N3  Sarah     (Outsider)
→ tonight: NOT Outsider
```
Rendered above the picker. This is the single highest-value addition for this
character and costs one `NightAction` list.

### Interactions / jinxes

- **Marionette:** update the jinx text to the wiki wording. Additionally, when
  a Marionette's `shownCharacterId == "balloonist"`, the ST must still run a
  full fake Balloonist each night (arbitrary players, no constraint) — the step
  should appear for that seat via `nightRoleId` (`GameState.kt:38` already
  routes Marionette/Drunk through `shownCharacterId`) and carry the banner
  **"This is the Marionette — their information is arbitrary. An Outsider may or may not have been added."**
- **Drunk shown as Balloonist:** same, via the same `nightRoleId` path.
- **Vortox:** replace the generic Vortox caveat for this character with the
  correct instruction:
  **"VORTOX — Balloonist info must be false: show a player of the SAME type as last night."**
- **Spy / Recluse:** as above.
- **Character changes mid-game** (Pit-Hag, Barber, Snake Charmer, Imp
  star-pass, Fang Gu jump, Bounty Hunter evil Townsfolk): the *type* of a seat
  can change after it was shown. The constraint is on the **type as shown at the
  time**, which `NightAction.shown` preserves; do not recompute from live state.

### UI text

- Step one-liner, night 1: `Balloonist — point to any player. They learn nothing about their type.`
- Step one-liner, later: `Balloonist — point to a player of a DIFFERENT type to last night (Lewis, Townsfolk).`
- Impaired banner: `Ana is POISONED — any type is allowed tonight.`
- Setup prompt: `Balloonist: add a second Outsider? (+0 or +1)`
- Seat note: `Balloonist — +1 Outsider added at setup`

### Data changes

- `night_and_jinxes.json:90` — update the Marionette jinx wording.
- `night_guide.json:873` — keep the prose; it is already accurate. Trim the
  first sentence so the collapsed row is phone-readable.
- `characters.json:1263` — no change.
- `InfoCalc.kt:486` — rewrite `balloonist(ctx)` to take the night history and
  return per-type eligibility plus misregistration caveats, and set
  `targetsNeeded("balloonist") = 1` (`InfoCalc.kt:22`).

## Tests to add

`engine/src/test/kotlin/com/clocktower/engine/BalloonistTest.kt`

1. **Given** night 2 and a `NightAction(night = 1, shown = "TOWNSFOLK")`; **when** the Balloonist's eligible targets are computed; **then** every Townsfolk seat is excluded and every Outsider/Minion/Demon/Traveller seat is included.
2. **Given** the same, and the Balloonist holds `poisoner:Poisoned`; **then** **all** seats are eligible and the result carries the "any type allowed" note.
3. **Given** night 3, a night-1 Townsfolk and a night-2 **Townsfolk** shown while poisoned; **when** night 3's eligibility is computed with a sober Balloonist; **then** Townsfolk are excluded (constraint is against *last night*, not against the last legal night) — wiki Example 2.
4. **Given** a dead Minion; **then** they are an eligible target (dead players count).
5. **Given** a Traveller seat; **then** `Traveller` appears as its own type group and is eligible after a Townsfolk night.
6. **Given** a Recluse in play; **when** the step is built; **then** the caveats include the Recluse misregistration note and the Recluse appears under Minion and Demon as well as Outsider. *(No misregistration note is produced today.)*
7. **Given** a Spy in play; **then** the Spy appears under Townsfolk and Outsider as well as Minion.
8. **Given** a `NightAction` recording a Recluse `shown = "MINION"`; **when** the next night's eligibility is computed; **then** Minions are excluded and Outsiders are allowed, i.e. the **registered** type drives the constraint.
9. **Given** an alive Vortox; **when** the step is built; **then** the caveat says the shown player must be the **same** type as last night, not the generic "info must be FALSE".
10. **Given** 10 players and a bag containing the Balloonist; **when** `Setup.allowedDistributions`; **then** it contains both the 1-Outsider and 2-Outsider variants, and `validateBag` accepts a bag of either shape. *(Passes today — lock it.)*
11. **Given** `setupChoices["balloonist"] == "+0"`; **when** the "Need:" distribution is computed for the bag builder; **then** it shows the base Outsider count, not base+1.
12. **Given** a dead Balloonist; **when** the night sheet is built; **then** the row is flagged "all holders are dead — skip" and no eligibility is computed.
