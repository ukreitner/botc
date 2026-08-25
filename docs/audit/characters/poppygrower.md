# Poppy Grower (poppygrower) — Experimental Townsfolk

## Official rules (sources)

Source: https://wiki.bloodontheclocktower.com/Poppy_Grower (fetched 2026-08-25).

Current ability text (matches `characters.json` exactly — no drift):

> "Minions & Demons do not know each other. If you die, they learn who each other are that night."

How to Run (quoted verbatim from the wiki):

- **First night:** "During the first night, wake the Demon. Show the **THESE CHARACTERS ARE NOT IN PLAY** info token, then any three good character tokens that are not in play. Then, put the Demon to sleep. **Do not do the Minion Info and Demon Info steps.**"
- **The night the Poppy Grower dies:** "If the Poppy Grower dies, mark them with the **EVIL WAKES** reminder. That night, wake the Minions and let them make eye contact. Show the **THIS IS THE DEMON** info token, then point to the Demon. Put the Minions to sleep. Wake the Demon. Show the **THESE ARE YOUR MINIONS** info token, then point to the Minions. Put the Demon to sleep."
- **Optional rule:** "If the Poppy Grower becomes drunk, poisoned, or leaves play, Minions & Demons learn who each other are that night."
- Note the ordering: **Minions first (together, eye contact), then the Demon.** The app's data has this right.
- Wiki example: a drunk Poppy Grower → evil learns each other on night 1 as normal; if that Poppy Grower is killed later, evil does **not** re-learn, because the ability was never on.

Jinxes (wiki, exact text):
- **Lil' Monsta:** "If Lil' Monsta & the Poppy Grower are alive, Minions wake one by one, until one of them chooses to take the Lil' Monsta token."
- **Summoner:** "If the Poppy Grower is alive on the 3rd night, the Summoner chooses which Demon but not which player."
- **Spy:** "If the Poppy Grower is in play, the Spy does not see the Grimoire until the Poppy Grower dies."
- **Widow:** "If the Poppy Grower is in play, the Widow does not see the Grimoire until the Poppy Grower dies."
- **Marionette** (from the Marionette page): "When the Poppy Grower dies, the Demon learns the Marionette but the Marionette learns nothing." The Marionette page adds the general rule: "The Marionette is not woken due to character abilities that would confirm that they are a Minion eg. Snitch, Preacher, Lil' Monsta, **Poppy Grower**, Hatter, Damsel."

Rules point I could not resolve from the wiki, flagged rather than guessed:
- The night-sheet text is "**died today or tonight**", but the Poppy Grower's slot is very early in the other-night order (before every kill). If the Poppy Grower is killed **at night** (Demon, Assassin, Gossip…), the wake happens *after* their own slot has already passed. The wiki says "that night" but does not say where in the night order to run it. The safe implementable reading — and the one the spec below encodes — is: **run it as soon as possible after the death; if the Poppy Grower is already dead when their slot is reached, run it there; if they die later that same night, run it just before DAWN.** Flag this to the user as a judgement call.

## What the app does today

Data / order:
- `characters.json:1521` — ability text current. `firstNightReminder`: "Skip Minion Info and Demon Info. Wake the Demon. Show the 'These characters are not in play' token. Show 3 not-in-play good character tokens." `otherNightReminder`: "If the Poppy Grower died today or tonight, wake all Minions. …". `reminders: ["Evil Wakes"]`. All correct.
- `night_and_jinxes.json:306` — first night slot 11, i.e. **immediately before `MINION_INFO` (14) and `DEMON_INFO` (18)**. Correct position.
- `night_and_jinxes.json:381` — other night slot 8. Correct position.
- Jinxes present: `marionette/poppygrower` (:95), `poppygrower/lilmonsta` (:124), `spy/poppygrower` (:145), `widow/poppygrower` (:150). **Missing: `poppygrower/summoner`.**
- `night_guide.json:1145` — very good prose for both nights, with prepared cards "THESE CHARACTERS ARE NOT IN PLAY", "THIS IS THE DEMON", "THESE ARE YOUR MINIONS".
- `StatusEffects.kt:101` — `"poppygrower" -> notes += "Poppy Grower: minions & demon learn each other tonight."` appears in `deathNotes`. This is the one piece of automation that exists, and it works.

Runtime:
- `NightOrder.build` emits `MINION_INFO` and `DEMON_INFO` **unconditionally** whenever `isFirstNight && state.players.count { !it.isTraveller } >= 7` (`NightOrder.kt:60,81`). The Poppy Grower is not consulted.
- The Poppy Grower's own other-night step is emitted **every night** for as long as the seat exists, alive or dead, because `build`'s `else ->` branch only checks `holders.isEmpty()` (`NightOrder.kt:144-145`).
- Nothing places, moves or reads the `Evil Wakes` token; nothing suppresses the Spy/Widow grimoire steps; nothing changes the Lil' Monsta or Summoner steps.

Storyteller experience today, night 1: the checklist reads
`11 Poppy Grower — "Skip Minion Info and Demon Info. Wake the Demon…"`, then
`14 Minion info — "Wake all Minions (Ana, Bo). They see each other, then point out the Demon (Cy)."`, then
`18 Demon info — "Wake the Demon (Cy). Point out the Minions (Ana, Bo), then show 3 not-in-play good characters as bluffs: …"`.
Two of the three rows are flatly wrong, and the two wrong ones are the ones with the player names filled in and the one-tap "Show bluffs full-screen" chip (`NightScreen.kt:781-786`). A tired ST following the checklist top-to-bottom hands the evil team to each other and loses the game for good on night 1.

## Defects and gaps

1. **P0 · Minion Info and Demon Info are still generated, fully populated, when a Poppy Grower is in play.** Rules require both steps be skipped entirely and replaced by a Demon-only bluffs step. `NightOrder.kt:60-119`. Repro: 8-player game, assign `poppygrower` + any Demon + 1 Minion, advance to night 1, open the Night tab — rows 2 and 3 after the Poppy Grower tell you to introduce the evil team. Also the night-checklist guard (`GameShell.kt:146-160`) will refuse to let you reach dawn until you tick those two wrong rows off.
2. **P0 · The bluffs show-card chip is on the wrong row.** `StepDetailPanel` attaches "Show bluffs full-screen" to `NightMarkers.DEMON_INFO` only (`NightScreen.kt:781-786`). With a Poppy Grower, the bluffs must be shown on the **Poppy Grower's own row**, which has only a plain "THESE CHARACTERS ARE NOT IN PLAY" message card and no way to display the three chosen bluff tokens. Repro: night 1 with a Poppy Grower — expand the Poppy Grower row; there is no bluffs chip.
3. **P0 · The evil-learns-each-other night is never scheduled.** `deathNotes` prints one sentence at the instant of death (`StatusEffects.kt:101`) and then the app forgets. Nothing places `Evil Wakes`, nothing adds the step to that night's sheet, nothing tells the ST at dawn or at the next dusk. Repro: execute the Poppy Grower on day 2; advance to night 3; the Poppy Grower row says "If the Poppy Grower died today or tonight, wake all Minions…" with **no player names, no Demon named, no Minions named, no show cards pre-targeted** — the entire step is unpopulated prose, unlike every other populated row.
4. **P1 · The Poppy Grower's other-night row appears every night, even while they are alive.** `NightOrder.kt:144`. On nights 2..N with a living Poppy Grower the ST sees a step that must be skipped, and the dawn guard nags them to tick it. Conversely, on the night after they die, the row looks identical to all the nights it was a no-op — nothing distinguishes "act now" from "skip".
5. **P1 · `Evil Wakes` is a dead token with no once-only semantics.** Nothing prevents the ST from running the reveal on two consecutive nights, and nothing prevents it running at all when the ability was off (drunk/poisoned Poppy Grower at death, per the wiki example). `characters.json:1521` declares the token; no code reads it.
6. **P1 · The night-1 Demon step doesn't tell the Demon about the Marionette.** The Marionette pointer lives inside the `DEMON_INFO` branch (`NightOrder.kt:98-102`), which will be suppressed by the fix for D1. The jinx says the Demon learns the Marionette **when the Poppy Grower dies** ("the Demon learns the Marionette but the Marionette learns nothing"), so the pointer has to move to the evil-wakes step, and the Marionette must be excluded from the Minions who are woken.
7. **P1 · Spy and Widow grimoire steps aren't suppressed.** The jinxes are in `night_and_jinxes.json:145,150` but only surface in the passive "Jinxes in play" dialog (`GameExtras.kt:200-225`). The Spy's night row (first night slot 66, other slot 90) still says to show the grimoire. Repro: Poppy Grower + Spy, night 1 → the Spy row is unchanged.
8. **P2 · The Poppy Grower/Summoner jinx is missing from the data.** "If the Poppy Grower is alive on the 3rd night, the Summoner chooses which Demon but not which player." Not in `night_and_jinxes.json`; the Summoner's night-3 step is therefore run normally.
9. **P2 · The Lil' Monsta jinx is not applied to the Lil' Monsta step.** "Minions wake one by one, until one of them chooses to take the token" — the jinx text exists (`night_and_jinxes.json:124`) but the Lil' Monsta night row is unchanged.
10. **P2 · The optional "drunk / poisoned / left play" rule isn't offered.** If the Poppy Grower is poisoned on night 1, evil should learn each other that night (optional rule). The `night_guide` prose mentions it for night 1 only; there is no check, no prompt, and no way to record that the optional rule was invoked.
11. **P3 · `deathNotes` wording is imprecise.** "minions & demon learn each other tonight" is right for a night death but says "tonight" for a daytime execution too, where it means "tonight, later". Minor, but it is the only sentence the ST gets.

## Proposed behaviour (spec)

### Derived predicate (engine)
```
fun poppyGrowerActive(state, lookup): Player?   // alive, has ability (not impaired), characterId == "poppygrower"
fun poppyGrowerSuppressesInfo(state, lookup): Boolean  // = poppyGrowerActive != null
fun poppyGrowerRevealPending(state, lookup): Boolean
//   = a poppygrower seat is dead (or impaired/left play, if the optional rule is switched on)
//     AND their ability was working when they died (DeathRecord.abilityImpairedAtDeath == false)
//     AND no seat carries PlacedReminder("poppygrower", "Evil Wakes")
```
`DeathRecord.abilityImpairedAtDeath` already exists (`GameState.kt:78`) and is populated by `GameActions.kill` — use it, don't recompute.

### First night
- **when:** first night, Poppy Grower seat exists.
- **NightOrder change (`NightOrder.kt:60-119`):** when `poppyGrowerSuppressesInfo(state)`, emit **no** `MINION_INFO` and **no** `DEMON_INFO` step. Instead, the Poppy Grower's own row becomes the populated evil step:
  - title `Poppy Grower — Demon bluffs only`
  - detail: `Wake the Demon (<DemonName>). Show "THESE CHARACTERS ARE NOT IN PLAY" and 3 not-in-play good tokens: <bluff names>. Minions learn NOTHING tonight — do not wake them.` (with the same "no bluffs chosen yet! Pick them from the menu" fallback the DEMON_INFO branch has today, `NightOrder.kt:107-111`)
  - `playerIds` = the Demon seats, so the row shows the Demon's name in the header.
- **NightScreen change (`NightScreen.kt:781-786`):** attach the `ShowCard.BluffsCard(state.demonBluffIds)` chip to `step.id == "poppygrower"` on the first night as well as to `DEMON_INFO`.
- **Marionette:** do **not** point out the Marionette on night 1 (the jinx defers it to the Poppy Grower's death). Add an explicit line to the row: `Marionette in play — the Demon does NOT learn them tonight (Poppy Grower jinx).`
- **Lil' Monsta jinx:** when both are in play, replace the Lil' Monsta "wake all Minions together" text with `Wake Minions ONE BY ONE (<names in seat order>) until one takes the Lil' Monsta token — they must not see each other.`
- **Optional drunk/poisoned rule:** if the Poppy Grower is impaired on night 1, do **not** suppress; emit normal `MINION_INFO`/`DEMON_INFO` and add `! Poppy Grower is drunk/poisoned — evil learns each other as normal tonight (optional rule).` to the Poppy Grower row.
- **expiry:** nothing expires.

### Deferred effect — the Poppy Grower dies
- **at the moment of death** (`StatusEffects.deathNotes`): keep the note but make it accurate and actionable:
  - day execution → `Poppy Grower: the Minions & Demon learn each other TONIGHT. The app will add the step.`
  - night death → `Poppy Grower: the Minions & Demon learn each other before dawn — the step has been added to tonight's sheet.`
  - if `abilityImpairedAtDeath == true` → `Poppy Grower was drunk/poisoned — evil already knew each other; no reveal.`
- **automatic bookkeeping on kill:** when a Poppy Grower with a working ability dies, place `PlacedReminder("poppygrower", "Evil Wakes")` on their own seat automatically (the physical token goes on the Poppy Grower). Undoable like any other action.
- **night step insertion:** `NightOrder.build` (other nights) emits the Poppy Grower row **only when** `poppyGrowerRevealPending(state)`. Two insertion points:
  1. at the canonical slot 8, when the Poppy Grower is already dead at the start of the night;
  2. dynamically, **immediately before `DAWN`**, when the Poppy Grower died earlier *this* night (the same mechanism `NightOrder` already uses to splice homebrew steps in before DAWN, `NightOrder.kt:184-207`).
- **the populated step:**
  - title `Poppy Grower — evil meets`
  - detail: `1) Wake the Minions together: <MinionNames> (NOT <MarionetteName> — the Marionette is never woken for this). Eye contact. Show "THIS IS THE DEMON", point to <DemonName>. Sleep. 2) Wake <DemonName>. Show "THESE ARE YOUR MINIONS", point to <MinionNames>` + when a Marionette exists: ` and to <MarionetteName> (Marionette jinx — the Demon learns them now).`
  - `playerIds` = the Minion + Demon seats, so the row header names them.
  - Show cards: the two existing message cards, plus a new `pick`-less **"point to" card** is unnecessary — the names are already in the detail text.
- **once only:** the presence of `poppygrower:Evil Wakes` plus that step being in `nightStepsDone` marks it consumed; `poppyGrowerRevealPending` returns false afterwards. Ticking the step off should also be enough on its own (record it in a small `state.consumedTriggers: Set<String>` keyed `"poppygrower:evilwakes"` if the reminder is removed by hand).
- **visibility:** Minions learn the Demon; the Demon learns the Minions **and** the Marionette; the Marionette learns nothing.

### Spy / Widow suppression
While `poppyGrowerActive(state) != null`:
- the Spy's and Widow's night rows must read `Poppy Grower jinx: do NOT show the grimoire tonight. Wake them, show nothing, put them back to sleep.` and the row should be visually marked as jinx-modified.
- once the Poppy Grower dies, the rows revert to normal automatically.

### Summoner jinx
On night 3, when a Poppy Grower is alive: `Poppy Grower jinx — the Summoner chooses which DEMON but NOT which player. You choose the player.` Add the jinx to `night_and_jinxes.json`.

### UI text the step should display
- First night row: `Demon bluffs only — Minions learn nothing tonight.`
- Suppressed steps: rather than silently dropping `MINION_INFO`/`DEMON_INFO`, consider emitting a single greyed, auto-ticked row `Minion & Demon info — SKIPPED (Poppy Grower)` so the ST can see the app made the decision deliberately. This is important for trust; silent removal looks like a bug.
- Nights while the Poppy Grower is alive: no row at all.
- Reveal night row: `Evil meets — Minions first, then the Demon.`

### Data changes
- `night_and_jinxes.json` — add `{"id1":"poppygrower","id2":"summoner","reason":"If the Poppy Grower is alive on the 3rd night, the Summoner chooses which Demon but not which player."}`.
- `night_guide.json:1145` — the `other` entry's last clause ("it happens only once, even if the Poppy Grower dies while drunk or poisoned it is your judgement whether evil learns each other") is a run-on and contradicts the wiki's clearer rule; rewrite as two sentences and state the drunk/poisoned case as: *if the Poppy Grower's ability was not working when they died, evil already knew each other and nothing happens.*
- `characters.json:1521` — no change.

## Tests to add

1. `poppy grower suppresses minion and demon info on night 1` — Given an 8-player game with `poppygrower`, `imp`, `poisoner`, `baron`; When `nightOrder.firstNight(state, lookup)`; Then no step has `id == MINION_INFO` or `id == DEMON_INFO`. (Today both are present.)
2. `poppy grower first night step names the demon and the bluffs` — Given the above with `demonBluffIds = ["chef","virgin","butler"]`; Then the `poppygrower` step's `detail` contains the Demon's player name and all three bluff names, and its `playerIds` are the Demon seats.
3. `impaired poppy grower does not suppress info steps` — Given the Poppy Grower carries `PlacedReminder("poisoner","Poisoned")`; When `firstNight(...)`; Then `MINION_INFO` and `DEMON_INFO` are both present.
4. `no poppy grower step while alive` — Given a living Poppy Grower on cycle 3; When `otherNight(...)`; Then no step has `id == "poppygrower"`. (Today: present every night.)
5. `poppy grower reveal step appears the night after a day execution` — Given the Poppy Grower is killed by EXECUTION on day 2; When cycle 3 `otherNight(...)`; Then a `poppygrower` step exists at the canonical slot, its `playerIds` contain every Minion and the Demon, and its detail lists the Minion names and the Demon name.
6. `poppy grower killed at night gets a pre-dawn reveal step` — Given cycle 3 NIGHT, Poppy Grower killed by DEMON during that night; When the sheet is rebuilt; Then a `poppygrower` step exists **immediately before** the `DAWN` marker.
7. `reveal happens only once` — Given the reveal step was completed and `poppygrower:Evil Wakes` is placed; When cycle 4 `otherNight(...)`; Then no `poppygrower` step.
8. `impaired-at-death poppy grower triggers no reveal` — Given the Poppy Grower was poisoned when killed (`abilityImpairedAtDeath == true`); Then no reveal step is generated and `deathNotes` says evil already knew each other.
9. `marionette is excluded from the woken minions and revealed to the demon` — Given a Marionette is in play; Then the reveal step's `playerIds` exclude the Marionette seat and the detail names the Marionette only in the "point out to the Demon" clause.
10. `spy step is jinxed while the poppy grower lives` — Given Poppy Grower + Spy alive; When `firstNight(...)`; Then the `spy` step detail contains "do NOT show the grimoire"; and after the Poppy Grower dies it does not.
11. `poppygrower summoner jinx is in the data` — `data.activeJinxes(listOf("poppygrower","summoner")).size == 1`.
