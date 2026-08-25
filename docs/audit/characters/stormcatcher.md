# Storm Catcher (stormcatcher) — Fabled (the wiki now files it under "Loric", experimental)

## Official rules (sources)

Sources (fetched 2026-08-25):
<https://wiki.bloodontheclocktower.com/Storm_Catcher> and the raw wikitext via
`https://wiki.bloodontheclocktower.com/api.php?action=parse&page=Storm_Catcher&prop=wikitext`,
plus <https://wiki.bloodontheclocktower.com/Fabled>.

Current ability text (matches `characters.json`):

> "Name a good character. If in play, they can only die by execution, but evil players
> learn which player it is."

How to Run (quoted / closely paraphrased from the wikitext):

> "The Storyteller declares which good character is favoured. If that character is in
> play, mark them with the Storm Catcher's **SAFE** reminder and show this to evil players
> during the first night. If the character isn't in play, inform all evil players of this
> fact instead.
>
> If the marked character is executed, they die. If they would die by other means, they
> remain alive."

First-night procedure (wiki, confirmed by the search summary of the same page):

- Each evil player wakes **in turn**. If a good character is marked **SAFE**, that evil
  player is shown the **THIS PLAYER IS** info token, the **favoured character's token**,
  and is pointed at the SAFE player.
- If nobody is marked SAFE, the evil player is shown the **THESE CHARACTERS ARE NOT IN
  PLAY** token (i.e. told the named character isn't in play).

Examples (wiki):

- "The Storm Catcher favours the General." The General is in play, survives every night
  attack, and dies only when executed.
- "The Storm Catcher favours the Empath" with no Empath in play — evil players know this
  and the Poisoner is free to bluff Empath.

Edge cases / clarifications that matter for storytelling:

- **The named character is declared publicly at game start** ("Name a good character"),
  before or as the game begins. The *player* holding it is secret from good; only evil
  learns who.
- **Protection is total except execution.** Not just the Demon: Assassin, Gossip, Godfather,
  Witch, Vigormortis, Moonchild, Psychopath, Grandmother-sympathy, Zombuul, Pukka, etc. all
  fail against a SAFE player. Only `DeathCause.EXECUTION` gets through.
- The named character need not be in play — that is a legitimate and useful choice,
  because it hands the evil team a guaranteed-safe bluff.
- Storm Catcher is a Fabled/Loric: it cannot be killed, is immune to drunkenness and
  poison, and does not count towards the "two players alive" evil win
  (<https://wiki.bloodontheclocktower.com/Fabled>).
- **Uncertain / not stated on the wiki:** what happens if the SAFE player later stops
  being the named character (Pit-Hag, Barber swap, Snake Charmer, Imp star-pass onto them,
  Fang Gu jump). The ability says "Name a good character. If in play, *they*…", which
  reads as attaching to the character, not the seat — so the SAFE token should follow the
  character. Flagging this rather than guessing; the implementation should make the token
  movable by hand and prompt the ST when the named character changes seats.
- No jinxes are listed on the page.

Night order: the app places `stormcatcher` at first-night index 8, between `boffin` and
`philosopher` and **before** `MINION_INFO`/`DEMON_INFO`. That matches the official
first-night sheet as far as I could verify;
<https://wiki.bloodontheclocktower.com/Night_Order> returned 404, so I could not confirm
against the canonical order page.

## What the app does today

Data:

- `engine/src/main/resources/botc/data/characters.json:2314` — ability matches the wiki;
  `firstNightReminder`: *"Mark a good player as \"Safe\". Wake each evil player and show
  them the marked player."*; `reminders: ["Safe"]`.
- `engine/src/main/resources/botc/data/night_and_jinxes.json:303` — `stormcatcher` in
  `firstNight` at index 8 (before `MINION_INFO` at index 14). Correct.
- `engine/src/main/resources/botc/data/night_guide.json:188` — a `first` entry with good
  prose ("while they have their ability they can only die by execution, so Demon attacks
  and other killing abilities against them fail…") and one prepared show card
  `{"label": "Show Storm Catcher token", "kind": "token", "token": "self", "text": "The
  player I point to is the Storm Catcher's chosen character"}`.

Engine:

- `NightOrder.kt:144-145` — a Fabled id in `state.fabledIds` produces a step even with no
  holders, so the Storm Catcher row appears on the first-night sheet with
  `playerIds = emptyList()`. **Works.**
- `StatusEffects.kt:64-66` — a reminder labelled `"safe"` produces the note
  `"Marked 'Safe' (<source>) — protected from the Demon."` This is the Monk's wording and
  is **wrong for the Storm Catcher**, which blocks every non-execution death.
- `GameActions.kt:217-223` — `EXPIRES_AT_DAWN` contains `"monk" to "Safe"` only, so a
  `("stormcatcher","Safe")` token correctly survives dawn. **Works.**
- `GameActions.kt:132-153` — `kill()` has no protection logic at all; every death is a
  storyteller decision that the engine records unconditionally.

UI:

- `FabledSheet` (`GameExtras.kt:145-198`) can toggle `stormcatcher` on, and nothing else.
  There is **no field anywhere for the named good character.**
- `NightScreen.kt:98-100` — `activeCharacter = characterById(step.id)` resolves against
  the whole dataset (not the script), so the Storm Catcher step's `NightToolTray`
  (`NightScreen.kt:283-306`) does offer the **Safe** token, and tapping it then a seat
  places `PlacedReminder("stormcatcher","Safe")` exclusively
  (`NightScreen.kt:317-330` → `GameActions.placeExclusiveReminder`). **This part works.**
- `StepDetailPanel` (`NightScreen.kt:781-812`) renders the night_guide prose and the one
  show chip. `GuideShowDialog` (`NightScreen.kt:366-454`) resolves `token: "self"` to
  `stepCharacterId` = `"stormcatcher"`, so the full-screen card shows the **Storm Catcher
  token**, not the favoured character's token — the opposite of what the wiki says to show.
- `QuickResolutions` (`NightScreen.kt:462-467`) returns immediately because
  `step.playerIds` is empty — so there is no tooling on this step beyond the tray.
- `DemonKillPanel` (`NightScreen.kt:534-630`) prints `deathNotes` as red `! …` warnings
  but the **"<name> dies" button stays enabled** (`NightScreen.kt:617-625`). Nothing stops
  the ST from killing a SAFE player.
- `SeatSheet.kt` kill-by-cause and the day-screen execution path never consult
  `deathNotes` at all, so a Gossip/Assassin/Godfather kill on the SAFE player gets no
  warning whatsoever.
- Nothing wakes the evil players one at a time; the ST must remember that this step means
  "wake Poisoner, then Demon, then…" and do it by hand.

Storyteller's actual experience today: toggle Storm Catcher on (only possible after the
game has started), remember privately which character you named, remember to place the
Safe token yourself, wake each evil player by hand while showing them a card with the
wrong token on it, and then remember for the rest of the game that this seat is immune to
everything except execution — with the app cheerfully offering to kill them every night.

## Defects and gaps

1. **P0** · The app will kill a SAFE player without stopping the ST · `GameActions.kill`
   (`GameActions.kt:132`) has no protection check and `DemonKillPanel`'s confirm button
   (`NightScreen.kt:617`) is enabled regardless of `deathNotes`. Repro: mark a seat with
   the Storm Catcher's Safe token, open the Demon step, pick that seat, tap "<name> dies"
   — they die. Rules require the attack to fail.
2. **P0** · The Safe warning describes the wrong rule · `StatusEffects.kt:65` says
   "protected from the Demon". The Storm Catcher's SAFE blocks **all** non-execution
   deaths (Assassin, Gossip, Godfather, Witch, Moonchild, Psychopath, Vigormortis…). A ST
   who reads that note will correctly let a Gossip kill through and break the game. Repro:
   place `stormcatcher:Safe`, then open the seat sheet and kill by cause "Other night
   death" — the only note shown mentions the Demon.
3. **P1** · The favoured character is never recorded · `GameState.fabledIds`
   (`GameState.kt:98`) is a bare list of ids. There is no field for "the Storm Catcher
   favours X", so nothing can (a) auto-place the SAFE token on the seat holding that
   character, (b) build the correct show card, (c) re-place the token when that character
   moves seats, or (d) remind the ST at dawn.
4. **P1** · The first-night show card shows the wrong token · `night_guide.json:188` uses
   `"token": "self"`, so `GuideShowDialog` (`NightScreen.kt:376`) shows the *Storm
   Catcher* token. The wiki says to show **THIS PLAYER IS** + the **favoured character's**
   token (or **THESE CHARACTERS ARE NOT IN PLAY** when it isn't in play).
5. **P1** · No "wake each evil player in turn" sequencing · The step is one undifferentiated
   row. The ST must work out from the grimoire who all the evil players are (including
   converted ones — Fang Gu jumps, Ogre, Bounty Hunter, alignment-flipped seats) and wake
   each one. The app knows exactly who is evil (`Player.isEvil`, `GameState.kt:49`) and
   could list them as a tick-list.
6. **P1** · The SAFE token is not placed automatically · Even though the app is told the
   Fabled is active, the ST must hand-place `stormcatcher:Safe` via the tray. With a named
   character recorded, this is a one-line derivation.
7. **P2** · No dawn/day reminder that a protection fired · When the Demon's attack is
   negated by SAFE, nothing is announced or logged; the ST must remember at dawn that
   "nobody died tonight" was because of the Storm Catcher, and evil will read the silence.
8. **P2** · Storm Catcher can only be turned on after the game starts · `FabledSheet` lives
   in `GameShell` (`GameShell.kt:501`). The wiki requires declaring it at game start; the
   evil-player reveal happens on night 1, so a late toggle silently skips the reveal for a
   game already past night 1.
9. **P3** · Taxonomy drift · The wiki now files Storm Catcher (and Gardener) under
   **Loric**, not Fabled; `characters.json:2314` says `"team": "fabled"`. Cosmetic today
   (the app's grouping still works), but worth noting for the library/reference screens.

## Proposed behaviour (spec)

Configuration (set at setup, before night 1):

- `fabledConfig["stormcatcher"] = StormCatcher(favouredCharacterId: String)` — a picker
  restricted to **good** characters (`team.isTownResident && !team.isEvil`) from the
  current script, sorted in-play-first but explicitly allowing not-in-play picks (the
  wiki calls that a feature). The ST is told to announce the name publicly.
- Derived: `safeSeatId = players.firstOrNull { it.characterId == favouredCharacterId }`.

Night step (first night only):

- **when:** first night, when `"stormcatcher" in fabledIds`. Wake condition: always (the
  Fabled cannot be drunk, poisoned or Exorcised).
- **targets:** none chosen at night — the target is derived from `favouredCharacterId`.
  If the ST has not set a favoured character yet, the step must block with
  "Name a good character first" and an inline picker.
- **immediate effects:** place `PlacedReminder("stormcatcher","Safe")` exclusively on
  `safeSeatId` if it exists. If no seat holds that character, place nothing.
- **evil reveal sub-checklist:** enumerate `players.filter { it.isEvil(lookup) }` in seat
  order as a tick-list. Tapping a name opens the full-screen card:
  - if in play: `ShowCard.CharacterCard("THIS PLAYER IS", favouredCharacterId)` followed
    by a "now point at <name>" line naming the SAFE seat;
  - if not in play: `ShowCard.CharacterCard("THESE CHARACTERS ARE NOT IN PLAY",
    favouredCharacterId)`.
  Each evil player is ticked off individually so the ST can be interrupted mid-step.
- **deferred effects:** none at dawn. The SAFE token persists for the whole game.
- **expiry:** never. Explicitly **not** in `EXPIRES_AT_DAWN` (already correct — do not
  generalise the Monk's `("monk","Safe")` entry to a label-only match).
- **information:** nothing computed for good players.
- **visibility:** every evil player (including players who *became* evil before night 1 —
  Marionette, Evil Twin's evil half, alignment-flipped seats) sees the reveal. The Lunatic
  does **not** (they are good); the Demon-believing Lunatic is not woken for this step.

Protection model (the important part):

- Add to `StatusEffects` a first-class protection query, e.g.
  `fun protections(state, lookup, playerId): List<Protection>` where
  `Protection(sourceId, label, blocks: Set<DeathCause>, text)`.
  Storm Catcher's entry is
  `blocks = DeathCause.entries - DeathCause.EXECUTION` with text
  **"Storm Catcher: <name> can only die by execution — this death does not happen."**
- Every death entry point (`DemonKillPanel` confirm, `SeatSheet` kill-by-cause,
  `DayScreen` execution, `starPass`, Gossip/Assassin/Godfather quick tools when they
  exist) consults it. When a protection blocks the chosen cause the button becomes
  **"Attack fails — <name> survives"**, applies no death, and records a log line. An
  explicit secondary **"Kill anyway (override)"** stays available for storyteller fiat,
  but must never be the default.
- Execution is *not* blocked: `DeathCause.EXECUTION` passes straight through.

Day-time inputs: none.

Interactions/jinxes to handle explicitly:

- **Execution vs. every other cause** — the single discriminator; make sure exile
  (`DeathCause.EXILE`) is treated as *not* an execution and therefore blocked (a Traveller
  can hold a good character, so this is reachable).
- **Character changes** (Pit-Hag, Barber, Snake Charmer, Fang Gu jump, Imp star-pass onto
  the SAFE seat): when `assignCharacter`/`swapCharacters`/`starPass` changes who holds
  `favouredCharacterId`, prompt: "The Storm Catcher favours the <X>. <Old> is no longer
  the <X> — move the Safe token to <New>?" (default yes; the rule text is ambiguous, so
  ask rather than decide).
- **The SAFE player turning evil** (Ogre/Bounty Hunter/Fang Gu conversions): the ability
  names a *good* character; if the seat turns evil, prompt the ST to decide and log it.
- **Fabled immunity**: the Storm Catcher token itself must never be a kill/poison target;
  it is not a seat, so this is already true.

UI text the step should display:

- Title row: `Storm Catcher — favours the <Character>`
- `Mark <name> "Safe". They can only die by execution — every other death fails.`
  (or `The <Character> is not in play — nobody is marked Safe.`)
- `Wake each evil player in turn: <list>. Show "THIS PLAYER IS" + the <Character> token
  and point at <name>.`

Data changes:

- `night_guide.json:188` — replace the single `token: "self"` show with two shows:
  `{"label":"Show favoured character (in play)","kind":"token","token":"config:stormcatcher.favouredCharacterId","text":"THIS PLAYER IS"}`
  and
  `{"label":"Show not-in-play","kind":"token","token":"config:stormcatcher.favouredCharacterId","text":"THESE CHARACTERS ARE NOT IN PLAY"}`.
  This needs `GuideShow.token` to accept a config reference in addition to `"self"` and
  `"pick"` (`NightScreen.kt:375-377`).
- `characters.json:2314` — sharpen `firstNightReminder` to
  *"Mark the favoured good character \"Safe\" — they can only die by execution. Wake each
  evil player in turn and show them who it is."*

## Tests to add

1. **Given** a game with `fabledIds = ["stormcatcher"]` and a seat marked
   `PlacedReminder("stormcatcher","Safe")`,
   **when** `advancePhase` runs from NIGHT to DAY,
   **then** the Safe token is still on the seat (guards against a future label-only
   generalisation of `EXPIRES_AT_DAWN`).
2. **Given** the same seat, **when** `StatusEffects.protections(...)` is queried,
   **then** it returns an entry blocking `DEMON`, `OTHER_NIGHT_DEATH`, `STORYTELLER` and
   `EXILE`, and **not** `EXECUTION`, with text mentioning "only die by execution".
3. **Given** the same seat, **when** a Demon kill is applied through the guarded kill path,
   **then** the player is still alive and no `DeathRecord` is appended.
4. **Given** the same seat, **when** an execution is applied,
   **then** the player is dead with `cause == EXECUTION`.
5. **Given** `favouredCharacterId = "empath"` and no Empath among the players,
   **when** the first-night step is built,
   **then** no Safe token is placed and the step's reveal text is the
   "not in play" variant.
6. **Given** `favouredCharacterId = "general"` held by seat A and a Pit-Hag turning seat B
   into the General, **when** the character change is applied,
   **then** the engine surfaces a "move the Safe token" prompt (or a pure-function
   equivalent returning the mismatch).
7. **Given** `fabledIds = ["stormcatcher"]`, **when** the first night sheet is built,
   **then** a step with `id == "stormcatcher"` exists before the `MINION_INFO` marker even
   though no player holds the character.
8. **Given** three evil players including one whose `alignmentFlipped` is true,
   **when** the reveal list is computed, **then** all three are listed.
