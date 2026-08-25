# Acrobat (acrobat) — Experimental Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Acrobat> (fetched via the MediaWiki
`action=parse&prop=wikitext` API, 2026-08-25).

Current ability text (verbatim):

> "Each night*, choose a player: if they are or become drunk or poisoned tonight, you die."

**How to Run (verbatim):**

> Each night except the first, wake the Acrobat. They point to a player. Put the Acrobat to sleep.
>
> If the player the Acrobat pointed to is drunk or poisoned, or becomes drunk or poisoned at any time tonight, the Acrobat **dies**.

**Summary bullets / clarifications (verbatim):**

- "Each night except the first, the Acrobat chooses a player. If the chosen player is sober and healthy, nothing happens. If the player is drunk or poisoned, the Acrobat dies."
- "If the Acrobat is drunk or poisoned, they cannot die to their own ability."
- "The Acrobat may choose any player, dead or alive, even themself."
- "If the chosen player is sober and healthy at the time the Acrobat picks, but becomes drunk or poisoned later in the night, the Acrobat dies."
- "The Acrobat does not learn if the player they selected was drunk, or poisoned, or both."
- "The Drunk registers as drunk to the Acrobat."

**Examples (verbatim):**

1. "The Sailor chooses the Assassin, and the Storyteller makes the Sailor drunk. The Acrobat chooses the Sailor, and dies because the Sailor is drunk."
2. "The Acrobat chooses the Tinker, who is sober and healthy. Nothing happens."
3. "The Acrobat chooses the Preacher. Later that night, the Pukka poisons the Preacher. The Acrobat dies, because the Preacher is no longer healthy."

**Jinxes:** none listed on the wiki, and none in the app's data.

**Timing consequences that matter enormously for implementation.** The Acrobat's
step sits early in the other-night order (position 18 of 96 in
`night_and_jinxes.json:391`, between Gambler and Snake Charmer), but the
resolution condition — "*or become* drunk or poisoned tonight" — cannot be
evaluated until the night is over. Everything below is ordered *after* the
Acrobat and can flip the answer:

| Later step | Index | Effect |
|---|---|---|
| Snake Charmer | 19 | poisons the new Snake Charmer |
| Organ Grinder | 21 | drunkenness |
| Pit-Hag | 25 | can create a Drunk |
| Lunatic / Exorcist / Lycanthrope | 31–33 | |
| Pukka | 39 | poisons its target tonight |
| No Dashii | 43 | poisons its 2 nearest Townsfolk (adjacency can change when someone dies mid-night) |
| Vortox / Vigormortis | 44 / 46 | Vigormortis poisons a Townsfolk neighbour on a Minion kill |
| Sweetheart | 60 | on death, a player is drunk from now on |
| Philosopher (first night only) | — | drunks the character it duplicates |

Wiki example 3 is exactly this case (Pukka poisons *after* the Acrobat picks).

**Note on ability-text drift.** `bra1n/townsquare`'s `roles.json` (an older
public dump) still carries the retired wording *"Each night\*, if either good
living neighbour is drunk or poisoned, you die"*. The app's `characters.json`
already carries the **current** "choose a player" text, so the data is correct
here — but any implementer copying from townsquare-derived sources will
regress it.

## What the app does today

Data:
- `engine/src/main/resources/botc/data/characters.json:1194` — id/name/team/ability all match the current wiki text. `otherNightReminder: "The Acrobat chooses a player."`, `reminders: ["Dead", "Chosen"]`, `firstNightReminder: ""`, `setup: false`. **Correct.**
- `engine/src/main/resources/botc/data/night_and_jinxes.json:391` — other-night order index 18; no first-night entry. **Correct.**
- `engine/src/main/resources/botc/data/night_guide.json:830` — an `other` entry only, with good prose that already states the deferred rule ("or became drunk or poisoned at any point tonight… mark them with the Dead reminder and announce the death at dawn"). `shows: []`.

Code: **there is no `acrobat` string anywhere in `engine/src/main` or
`app/src/main`.** (`grep -rn acrobat engine/src app/src` returns only
`ScriptParserTest.kt:33`, an unrelated `acrobat_2` homebrew-id fixture.) The
Acrobat is therefore entirely generic:

- `NightOrder.build` (`engine/src/main/kotlin/com/clocktower/engine/NightOrder.kt:40`) emits a plain step titled "Acrobat" with `detail = "The Acrobat chooses a player."`.
- `NightScreen.StepDetailPanel` (`app/src/main/java/com/clocktower/grimoire/ui/screens/NightScreen.kt:770`) renders the guide prose, then `QuickResolutions` (`NightScreen.kt:462`) — which has no `acrobat` branch and whose `else` arm only fires for Demons — then nothing, because `InfoCalc.supports("acrobat")` is false (`engine/src/main/kotlin/com/clocktower/engine/InfoCalc.kt:29`).
- `NightToolTray` (`NightScreen.kt:193`) offers the "Dead" and "Chosen" chips; tapping "Chosen" then a seat calls `GameActions.placeExclusiveReminder` (single copy, so it moves each night — correct).
- `"acrobat" to "Chosen"` is **not** in `EXPIRES_AT_DAWN` (`GameActions.kt:218`) nor `EXPIRES_AT_DUSK` (`GameActions.kt:231`), so the token never clears.

Storyteller's actual experience: at the Acrobat's step they read a paragraph,
tap "Chosen" and a seat, and then must personally hold the thought "if anything
later tonight poisons or drunks that seat, kill the Acrobat" across ~78 more
night steps. At dawn, nothing reminds them; nothing checks; nothing announces.
Yesterday's "Chosen" token is still sitting on the board next to tonight's.

`StatusEffects.isImpaired` (`engine/src/main/kotlin/com/clocktower/engine/StatusEffects.kt:36`)
already computes exactly the predicate the Acrobat needs (reminder label
containing "poison"/"drunk", `characterId == "drunk"`, No Dashii adjacency), and
`deathNotes` (`StatusEffects.kt:52`) already surfaces protections — but neither
is wired to the Acrobat.

## Defects and gaps

1. **P0 · The Acrobat's death is never computed, so it is routinely missed.**
   Rules: if the chosen player is (or becomes) drunk/poisoned tonight, the
   Acrobat dies and the death is announced at dawn. App: no code path evaluates
   this; `QuickResolutions` (`NightScreen.kt:462`) has no `acrobat` case, and
   nothing runs at dawn (`GameActions.advancePhase`, `GameActions.kt:258`, only
   sweeps reminder tokens). Repro: Acrobat step → place "Chosen" on the seat
   holding the Poisoner's "Poisoned" token → tap Dawn → nobody dies.
2. **P0 · Even a manual check at the Acrobat's step is wrong, because the step
   fires at index 18 of 96.** Everything from Snake Charmer (19) to Sweetheart
   (60) can make the chosen player drunk/poisoned afterwards. Repro: Acrobat
   chooses Amy (sober); later the Pukka poisons Amy; the "Poisoned" token
   appears after the Acrobat's row is already ticked and scrolled past. This is
   the wiki's own Example 3 and the app cannot get it right without a dawn pass.
3. **P0 · The Acrobat's own impairment is not checked.** "If the Acrobat is
   drunk or poisoned, they cannot die to their own ability." The app offers no
   check at all, so an ST who *does* remember the rule may kill a poisoned
   Acrobat by hand. `StatusEffects.isImpaired(state, lookup, acrobatSeat)`
   returns the right answer today; it is simply never called for this character.
4. **P1 · The "Chosen" token never expires.** `GameActions.kt:218/231` — after
   three nights the grimoire has one "Chosen" (it is exclusive, so it moves),
   but it lingers through the day with no meaning, and it is indistinguishable
   from Exorcist's "Chosen" and Acrobat's own next-night choice in the seat
   sheet. It should be swept at dawn *after* the Acrobat's death is resolved.
5. **P1 · No dawn announcement.** The night_guide text tells the ST to
   "announce the death at dawn", but the DAWN step's `detail` is the generic
   "Wait a few seconds. Everyone opens their eyes. Announce who died."
   (`NightOrder.kt:59`). There is no dawn briefing listing *who* died and *why*.
6. **P1 · The chosen player is not recorded in state.** Only a reminder token
   records the choice, and it is keyed `("acrobat","Chosen")` with no night
   number. A rules engine cannot re-evaluate "was the Acrobat's target impaired
   at any point during night N" from that, and undo/redo across the night
   loses ordering.
7. **P2 · The target picker is missing.** Every other character that picks
   (Fortune Teller, Dreamer, Monk-like) either gets `InfoCalc.targetsNeeded`
   chips (`InfoCalc.kt:22`) or a `ResolutionPicker`. The Acrobat gets neither —
   the only way to record the choice is the generic token tray, two taps down
   the screen, with no "dead players allowed / self allowed" affordance.
8. **P2 · No live "this choice kills you" feedback.** The ST should see, the
   instant they place "Chosen", whether that seat is currently impaired — and
   see it update if it becomes impaired later in the same night.
9. **P3 · No note that the Acrobat learns nothing.** The step reads like an
   info role; the guide clarifies it, but the step's own one-liner doesn't say
   "the Acrobat learns nothing — do not show them anything."
10. **P3 · The Drunk/Marionette distinction isn't spelled out.** The Drunk
    registers as drunk (already true via `isImpaired`); the Marionette does
    **not** (also already true — `isImpaired` doesn't special-case marionette,
    only `InfoCalc.impairments` does). Worth an explicit test so a refactor
    doesn't accidentally make the Marionette register as drunk.

## Proposed behaviour (spec)

### Night action

- **when:** `other` nights only. Wake condition: the Acrobat seat is **alive**.
  (A dead Acrobat does not act — nothing in the ability says otherwise.) Skip
  if the seat holds a `"No ability"` reminder.
- **targets:** exactly **1**. Constraints: **none** — any player, alive or
  dead, including the Acrobat themself, including Travellers. Picker should
  list all seats in clock order, alive first but dead **enabled**, with the
  Acrobat's own seat labelled "(self)". Default: nothing pre-selected.
- **immediate effects:** place exclusive reminder `acrobat:Chosen` on the
  target. Record `AcrobatChoice(night = cycle, targetId)` in state (see
  "engine data" below). No kill yet — never resolve at this step.
- **deferred effects:** at **DAWN**, before the death announcement is
  assembled, evaluate:
  ```
  acrobatAlive && !isImpaired(acrobat) && impairedAtAnyPointTonight(target)
      -> kill(acrobat, cause = OTHER_NIGHT_DEATH)  // NOT DeathCause.DEMON
  ```
  where `impairedAtAnyPointTonight(target)` is true if `isImpaired(target)` was
  true at **any** state snapshot between the DUSK transition and the DAWN
  transition (see "impairment watermark" below). Run the standard protection
  gate (`StatusEffects.deathNotes`) before confirming — e.g. an Acrobat marked
  `monk:Safe` is *not* protected (Monk only stops the Demon), but an Innkeeper
  "Protected" or a Tea Lady adjacency does stop it, so the ST must be shown the
  notes and confirm.
- **expiry:** `acrobat:Chosen` expires at **dawn, after** the Acrobat
  resolution runs. Add `"acrobat" to "Chosen"` to `EXPIRES_AT_DAWN`
  (`GameActions.kt:218`) and make the dawn pipeline ordered:
  `resolveDeferredNightEffects()` → `clearEphemeral(EXPIRES_AT_DAWN)` →
  `phase = DAY`. `acrobat:Dead` is a marker for the corpse; it does not expire.
- **information:** none. The Acrobat learns nothing — not even whether their
  target was drunk or poisoned. There is no false-info branch; when the Acrobat
  is impaired the *only* difference is that they cannot die.
- **visibility:** nothing is shown to anyone. The Demon/Minions/Lunatic learn
  nothing about the Acrobat.
- **day-time inputs:** none.

### Impairment watermark (the mechanism this character needs)

The rule "*or become* drunk or poisoned tonight" requires a night-scoped
high-water mark, not a point-in-time query. Cheapest correct implementation:

- Add `nightImpaired: Set<Long>` to `GameState` (cleared on every
  `SETUP→NIGHT` and `DAY→NIGHT` transition in `advancePhase`).
- Every mutation that can change impairment — `addReminder`,
  `placeExclusiveReminder`, `removeReminder`, `assignCharacter`,
  `swapCharacters`, `snakeCharmerSwap`, `kill` (No Dashii adjacency changes
  when a seat dies) — ends with
  `nightImpaired += players.filter { isImpaired(it) }.map { it.id }` while
  `phase == NIGHT`.
- The Acrobat's dawn check is then `target.id in state.nightImpaired`.

This same watermark is reusable by any future "at any point tonight" rule and
is far more robust than trying to order the night steps.

### UI text the step should display

Step one-liner (replaces the current `detail`):

> **Acrobat — point to any player (alive or dead, may be themself). They learn nothing.**

Inside the expanded panel, a target picker plus a live verdict line:

- target not impaired: `Ana is sober & healthy right now — but check again at dawn: anything tonight can change this.`
- target impaired: `Ana is DRUNK/POISONED (Poisoner) — the Acrobat dies at dawn.`
- Acrobat impaired: `Ben is drunk/poisoned — the Acrobat CANNOT die to their own ability tonight. Choose freely.`

At **Dawn**, in the dawn briefing (see cross-cutting recommendation):

> **Ben (Acrobat) dies** — they chose Ana, who was poisoned by the Poisoner tonight. Announce the death.

or, when nothing happened:

> Acrobat: Ana stayed sober & healthy all night — the Acrobat survives. Say nothing.

### Data changes

- `night_guide.json:830` — keep the prose; add `shows: []` is already right.
  Add a short `"other"` headline field if the schema gains one; otherwise
  shorten the first sentence so the step row is readable on a phone.
- `characters.json:1194` — no change needed. Optionally reorder
  `reminders` to `["Chosen", "Dead"]` so the tray's first chip is the one used
  every night.
- `night_and_jinxes.json:391` — no change; index 18 is correct, and the fix is
  the dawn pass, not a reorder.

## Tests to add

`engine/src/test/kotlin/com/clocktower/engine/AcrobatTest.kt`

1. **Given** night 2, Acrobat alive and sober, `acrobat:Chosen` on Ana who holds
   `poisoner:Poisoned`; **when** `advancePhase()` (dawn); **then** the Acrobat
   is dead with a `DeathRecord(cause = OTHER_NIGHT_DEATH, atNight = true)` and
   `acrobat:Chosen` has been removed.
2. **Given** night 2, `acrobat:Chosen` placed on Ana while Ana is sober, and
   *then* `placeExclusiveReminder(Ana, pukka:Poisoned)` later in the same night;
   **when** dawn; **then** the Acrobat dies. (This is wiki Example 3 and fails
   today even under any point-in-time implementation.)
3. **Given** the same as (2) but the poison token is added and then removed
   before dawn; **when** dawn; **then** the Acrobat still dies (the target
   *became* poisoned tonight).
4. **Given** the Acrobat holds `poisoner:Poisoned` and chooses a poisoned Ana;
   **when** dawn; **then** the Acrobat survives.
5. **Given** the Acrobat chooses the seat whose `characterId == "drunk"`;
   **when** dawn; **then** the Acrobat dies ("The Drunk registers as drunk").
6. **Given** the Acrobat chooses the seat whose `characterId == "marionette"`
   and that seat has no drunk/poison reminder; **when** dawn; **then** the
   Acrobat survives (the Marionette is not drunk).
7. **Given** a No Dashii adjacent to the Acrobat's chosen Townsfolk; **when**
   dawn; **then** the Acrobat dies (exercises `derivedPoison`).
8. **Given** a No Dashii whose nearest Townsfolk neighbour *changes* mid-night
   because the previous neighbour was killed by the Demon; **when** dawn;
   **then** the newly-poisoned player counts, i.e. an Acrobat who chose them
   dies.
9. **Given** the Acrobat chooses themself while sober; **when** dawn; **then**
   the Acrobat survives (they are sober, so nothing happens) — and **given**
   they choose themself while poisoned; **then** they also survive (impaired
   Acrobats can't die to their own ability). Both directions matter.
10. **Given** night 1; **then** no Acrobat step is emitted by
    `NightOrder.firstNight` and no dawn resolution runs.
11. **Given** a dead Acrobat; **then** the other-night step is marked
    "all holders dead — skip" and no dawn resolution runs.
12. **Given** the Acrobat is `monk:Safe`; **when** the dawn check fires; **then**
    the Acrobat still dies (Monk protects only from the Demon) and the ST is
    shown the protection note for confirmation rather than the death being
    silently suppressed.
