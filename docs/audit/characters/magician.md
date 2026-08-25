# Magician (magician) — Experimental (Carousel) Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Magician> (fetched 2026-08-25).
Cross-checked: <https://wiki.bloodontheclocktower.com/Poppy_Grower>.

**Current ability text (wiki):**
> "The Demon thinks you are a Minion. Minions think you are a Demon."

`characters.json:1464` matches exactly — **no drift**.

**How to Run (verbatim):**
> "During the first night, wake the Minions. Show them the **THIS IS THE DEMON** info token, then point to the Demon and the Magician."
>
> "During the first night, wake the Demon. Show the **THESE ARE YOUR MINIONS** info token, then point to all Minions and the Magician."
>
> "**Do this instead of the normal Minion Info and Demon Info steps.**"

**Example (verbatim):**
> "The Minions wake to learn that either the Leviathan player or the Magician player is the Demon. The Leviathan player learns that the Fearmonger player, the Assassin player, and the Magician player are the Minions."

Timing / edge cases:

- **The Magician never wakes.** Its official first-night position (13, immediately before
  Minion Info) exists only as a reminder to the ST that the *next two steps change*.
- Minions see **two** candidate Demons and are not told which is which. Demon sees **N+1**
  candidate Minions and is not told which is the Magician. Wiki tip: *"Point to the Demon and
  the Magician in a varying order so evil players can't identify the Magician."*
- The Demon still gets their 3 not-in-play bluffs as normal.
- Wiki tip (Poppy Grower): *"If the Poppy Grower dies and the evil players learn who each
  other are mid-game, rerun the Magician's ability that night."* — i.e. the delayed reveal
  must also include the Magician on both sides.
- **Jinxes (verbatim from the Magician page — this is the complete list):**
  - **Legion:** "If the Magician is in play, during the Demon info step, Legion wake in separate groups."
  - **Lil' Monsta:** "If the Magician is alive, the Storyteller chooses which Minion babysits Lil' Monsta."
  - **Marionette:** "If the Magician is alive, the Demon doesn't know which neighbor is the Marionette."
  - **Spy:** "When the Spy sees the Grimoire, the Demon and Magician's character tokens are removed."
  - **Vizier:** "If the Vizier is in play, the Magician has no ability but is immune to the Vizier's ability."
  - **Widow:** "When the Widow sees the Grimoire, the Demon and Magician's character tokens are removed."
  - **Wraith:** "After each execution, the living Magician may publicly guess a living player as the Wraith." (if correct, the Demon must choose the Wraith that night)

## What the app does today

- `characters.json:1464-1475`: no reminders; `firstNightReminder` = "During Minion Info,
  point to the Magician and the Demon. During Demon Info, point to the Magician and the
  Minions."; `otherNightReminder: ""`.
- Night order: `night_and_jinxes.json:308` (first night, index 13, immediately before
  `MINION_INFO` at 309 and `DEMON_INFO` at 313). Correctly absent from the other-night list.
- `night_guide.json:1096-1101` — accurate prose for the first night; no show cards.
- `NightOrder.build` emits a row titled "Magician — <name>" with the `firstNightReminder`
  text (`NightOrder.kt:130-181`).
- **The Minion Info step** (`NightOrder.kt:66-84`) builds its detail as:
  ```kotlin
  append("Wake all Minions")
  if (minions.isNotEmpty()) append(" (${minions.joinToString { it.name }})")
  append(". They see each other, then point out the Demon")
  if (demon.isNotEmpty()) append(" (${demon.joinToString { it.name }})")
  ```
  `demon` is `players.filter { team == Team.DEMON }` — **the Magician is not included**.
- **The Demon Info step** (`NightOrder.kt:85-129`) builds "Point out the Minions (…)" from
  `players.filter { characterId != "marionette" && team == Team.MINION }` — **the Magician is
  not included**. It also appends "Point out the Marionette (<name>)" when a real Marionette
  exists (`NightOrder.kt:99-104`) and Lunatic guidance (`:117-123`).
- `NightGuide.forStep` (`NightGuide.kt:56-59`) is keyed by character id; `night_guide.json`
  has **no** `MINION_INFO` / `DEMON_INFO` / `DUSK` / `DAWN` entries, so those two steps get no
  prose, no show cards and no place for an override.
- `InfoCalc.supports("magician")` is false → no caveats block (`NightScreen.kt:835-933`),
  no impairment warning.
- Jinx data present: `night_and_jinxes.json:118-121` (Lil' Monsta), `:132-136` (Spy),
  `:137-141` (Widow). Rendered as text in `SeatSheet.kt:222-232` and `GameExtras.kt:200-220`.
- `StatusEffects.kt:101`: killing a Poppy Grower emits "Poppy Grower: minions & demon learn
  each other tonight" — with no Magician clause.

Storyteller experience: the Magician's own row appears first and, if the ST expands it,
gives correct advice. If the ST does what the app tells them at the *next* step — which is
the natural thing to do on a phone at a live table — they point only at the real Demon, and
the Magician's entire ability silently does not happen.

## Defects and gaps

1. **P0 · The Minion Info step instructs the ST to break the Magician's ability.**
   `NightOrder.kt:66-84` produces, verbatim, e.g. `"Wake all Minions (Bo, Cara). They see each
   other, then point out the Demon (Ali)."` With a Magician in play the ST must point at the
   Demon **and** the Magician. Following the step as written hands the Minions the true Demon.
   *Repro:* 9-player game with Magician + Imp + Poisoner + Baron → night 1 → expand "Minion info".

2. **P0 · The Demon Info step omits the Magician from the Minion list.**
   `NightOrder.kt:85-129` produces `"Wake the Demon (Ali). Point out the Minions (Bo, Cara),
   then show 3 not-in-play good characters as bluffs…"`. The Magician must be pointed at as
   a Minion. Same repro, "Demon info" step.

3. **P1 · The `magician + lilmonsta` jinx text in the data is wrong and rules-breaking.**
   `night_and_jinxes.json:118-121` reads:
   > "Each night, the Magician chooses a Minion: if that Minion and Lil' Monsta are alive, that Minion babysits Lil' Monsta."
   The official jinx is:
   > "If the Magician is alive, the Storyteller chooses which Minion babysits Lil' Monsta."
   The app's text tells the ST to **wake the Magician every night** and give them a choice
   they do not have.
   *Repro:* Magician + Lil' Monsta in play → seat sheet on the Magician → the jinx line.

4. **P1 · Four official jinxes are missing from the data.**
   Only Lil' Monsta, Spy and Widow are present. Missing: **Legion**, **Marionette**,
   **Vizier**, **Wraith**. The Vizier one is the most damaging omission — it *turns the
   Magician off*.

5. **P1 · The Vizier jinx is not applied.**
   "If the Vizier is in play, the Magician has no ability but is immune to the Vizier's
   ability." With a Vizier in play, Minion/Demon info must run **normally** (no Magician
   pointing). Nothing in `NightOrder` or `StatusEffects` knows this.

6. **P1 · The Marionette jinx is not applied and the app actively contradicts it.**
   "If the Magician is alive, the Demon doesn't know which neighbor is the Marionette."
   `NightOrder.kt:99-104` appends `". Point out the Marionette (Dan)"` to the Demon Info
   step unconditionally, and the sub-7-player Marionette step (`NightOrder.kt:131-146`)
   shows the Demon the Marionette by name.
   *Repro:* Magician + Marionette + Imp, 9 players → Demon info step names the Marionette.

7. **P1 · No `MINION_INFO` / `DEMON_INFO` guide entries and no show cards.**
   `NightGuide.forStep` (`NightGuide.kt:56-59`) can only return entries keyed by character
   id, and `night_guide.json` has none for the markers. So the two most important steps of
   night 1 have no run-book text, and there is no prepared `THIS IS THE DEMON` /
   `THESE ARE YOUR MINIONS` card even though `ShowCard.CharacterCard` (`ShowCards.kt:67`)
   could render them. This is the structural reason the Magician has nowhere to live.

8. **P1 · The Poppy Grower mid-game reveal drops the Magician.**
   `StatusEffects.kt:101` says "minions & demon learn each other tonight" with no mention of
   re-running the Magician's ability, which the wiki explicitly requires. And the Poppy
   Grower's own rule ("do not do the Minion Info and Demon Info steps") is not modelled in
   `NightOrder` at all, so the two steps still render on night 1 in a Poppy Grower game.

9. **P2 · The Magician's own row reads like a wake step.**
   The row is `"Magician — Ana"` with holder name in primary colour (`NightScreen.kt:730-742`),
   identical in shape to every character that is woken. It should be visually a *note*, and
   its checkbox should not gate the Dawn guard (`GameShell.kt:145-158`) any differently than
   the two steps it modifies.

10. **P2 · Nothing helps the ST vary the pointing order.**
    The wiki tip is to point at the Demon and Magician in a varying order so evil cannot
    deduce the Magician. The step could simply randomise the order in which it lists the two
    names, and say it is doing so.

11. **P2 · The Spy/Widow jinx has no mechanism to hook into.**
    There is no "show the grimoire to the Spy/Widow" mode in the app today, so nothing is
    currently wrong — but when one is added, it must remove the Demon's **and** the
    Magician's character tokens.

12. **P2 · No impairment warning.**
    A drunk/poisoned Magician has no ability, so Minion/Demon info runs normally. There is no
    banner (`InfoCalc.supports("magician")` is false → `NightScreen.kt:835` never runs).
    In practice the Magician is usually only poisoned after night 1, so this is lower impact
    than for other characters — but a Marionette or Drunk *shown* as the Magician cannot
    happen (the shown character is a Townsfolk and `nightRoleId` would route to `magician`),
    and in that case the ability does **not** work while the app would still alter the steps.

13. **P3 · Sub-7-player games.** `NightOrder.kt:52` gates Minion/Demon info on
    `players.count { !it.isTraveller } >= 7`, matching the official rule, so a Magician in a
    6-player game correctly does nothing. **Works** — but the Magician row should then say so
    rather than describing steps that will not appear.

## Proposed behaviour (spec)

**The core change is to the two info markers, not to a Magician step.**

- **when:** first night, `MINION_INFO` and `DEMON_INFO` (built in `NightOrder.kt:66-129`).
  Active condition: a seat holds `characterId == "magician"`, that seat is **alive**, the
  Magician is not drunk/poisoned/"No ability", and **no Vizier is in play**.
- **Minion Info, with an active Magician:**
  - `detail` becomes: `Wake all Minions (Bo, Cara). They see each other, then show "THIS IS
    THE DEMON" and point to TWO players: Ali and Ana — in a different order each time you
    run this. Do not say which is which.`
  - The two names must be emitted in a randomised order (seeded per game so it is stable
    across recompositions).
  - Add a prepared show card: `THIS IS THE DEMON` (message card; the pointing is physical).
- **Demon Info, with an active Magician:**
  - `detail` becomes: `Wake the Demon (Ali). Show "THESE ARE YOUR MINIONS" and point to
    Bo, Cara and Ana (the Magician — the Demon must not be able to tell which). Then show 3
    not-in-play good characters as bluffs: …`
  - The Magician's name must be interleaved into the minion list, not appended.
  - **Marionette jinx:** when an active Magician exists, suppress the
    `". Point out the Marionette (Dan)"` clause (`NightOrder.kt:99-104`) and replace it with
    `The Marionette (Dan) is among the players you point at — the Demon does NOT learn which
    neighbour is the Marionette (Magician jinx).` Same for the sub-7 Marionette step
    (`NightOrder.kt:131-146`).
  - **Legion jinx:** when an active Magician exists and Legion is in play, split the Demon
    info step into one row per Legion group: `Wake Legion in separate groups.`
- **Vizier present:** run both steps exactly as today and add a note to the Magician's own
  row: `Vizier in play — the Magician has NO ability (but is immune to the Vizier). Run
  Minion/Demon info normally.`
- **Magician's own row (position 13):** keep it — the official night sheet has it — but
  retitle it and mark it informational:
  `Magician (Ana) — does not wake. The next two steps change: Minions see two Demons, the
  Demon sees an extra Minion.` Auto-tick it when the two steps it modifies are ticked.
- **immediate effects / tokens / expiry:** none. The Magician places no reminders and nothing
  expires.
- **information:** nothing is computed *for* the Magician; everything is about what evil is
  shown. Add `MINION_INFO` and `DEMON_INFO` entries to `night_guide.json` so this has a home
  (see Data changes).
- **visibility:** this is the whole ability. The Magician themself learns nothing and must
  not be woken.
- **mid-game re-runs:** when a Poppy Grower dies, the "evil wakes" reveal that night must use
  the same Magician-aware text on both halves. Implement it as a shared builder used by both
  the night-1 markers and the Poppy Grower reveal step.
- **day-time inputs:** with a **Wraith** in play, add a day-screen recorder after each
  execution: *"Magician's public Wraith guess: <player>"*, and, if correct, a night-step note
  on the Demon: `The Magician correctly guessed the Wraith — the Demon must choose <player>
  tonight.`
- **Lil' Monsta:** with an alive Magician, the Lil' Monsta babysitter is the **Storyteller's**
  choice — surface a one-tap "who babysits tonight?" picker on the Lil' Monsta step and
  suppress any "Minions choose" wording.

**UI text**

- Minion info (Magician active): `Show "THIS IS THE DEMON", then point to Ana and Ali. Vary the order every game. Do not reveal which is the Magician.`
- Demon info (Magician active): `Show "THESE ARE YOUR MINIONS", then point to Bo, Ana, Cara. One of them is the Magician — do not indicate which.`
- Vizier: `! Vizier in play — the Magician has no ability. Run these steps normally.`
- Magician row: `Does not wake. Alters Minion info and Demon info (below).`

**Data changes**

- `night_and_jinxes.json:118-121` — replace the `magician`+`lilmonsta` reason with
  "If the Magician is alive, the Storyteller chooses which Minion babysits Lil' Monsta."
- `night_and_jinxes.json` — add:
  - `magician` + `legion`: "If the Magician is in play, during the Demon info step, Legion wake in separate groups."
  - `magician` + `marionette`: "If the Magician is alive, the Demon doesn't know which neighbor is the Marionette."
  - `magician` + `vizier`: "If the Vizier is in play, the Magician has no ability but is immune to the Vizier's ability."
  - `magician` + `wraith`: "After each execution, the living Magician may publicly guess a living player as the Wraith. If correct, the Demon must choose the Wraith tonight."
- `night_guide.json` — add `MINION_INFO` and `DEMON_INFO` entries (requires no code change:
  `NightGuide.forStep` is keyed by the step id, and `NightMarkers.MINION_INFO == "MINION_INFO"`),
  each with instructions and show cards `THIS IS THE DEMON` / `THESE ARE YOUR MINIONS` /
  `THESE CHARACTERS ARE NOT IN PLAY`. This unblocks the Poppy Grower, Snitch, Damsel,
  Marionette, Summoner and Lunatic audits as well.
- `night_guide.json:1096-1101` — add the Poppy Grower re-run tip and the Vizier exception.
- `characters.json` — no change.

## Tests to add

1. `GIVEN` an 8-player game with a Magician, an Imp and two Minions `WHEN` the first-night
   sheet is built `THEN` the `MINION_INFO` step's detail contains the Magician's name.
   *Fails today.*
2. Same setup `THEN` the `DEMON_INFO` step's detail contains the Magician's name among the
   Minions. *Fails today.*
3. `GIVEN` the same game plus a **Vizier** `THEN` neither step mentions the Magician, and the
   Magician's own step says the ability is off. *Fails today.*
4. `GIVEN` a Magician and a Marionette `THEN` the `DEMON_INFO` detail does **not** name the
   Marionette. *Fails today.*
5. `GIVEN` a Magician that is **poisoned** on the first night `THEN` both info steps run
   normally.
6. `GIVEN` a Magician in a 6-player game `THEN` no `MINION_INFO`/`DEMON_INFO` steps are
   produced and the Magician's row says the ability has no effect this game.
7. `GIVEN` the shipped data `THEN` the `magician`+`lilmonsta` jinx text equals the official
   wording, and jinxes exist for `magician` with `legion`, `marionette`, `vizier`, `wraith`.
   *Fails today.*
8. `GIVEN` a Magician `WHEN` the first-night sheet is built twice with the same game seed
   `THEN` the order of the two names in `MINION_INFO` is stable (no flicker), but differs
   across different game seeds.
9. `GIVEN` a Poppy Grower dies with a Magician alive `THEN` the mid-game evil-reveal text
   includes the Magician on both halves. *Fails today.*
