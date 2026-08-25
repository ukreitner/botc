# Sentinel (sentinel) — Fabled

## Official rules (sources)

Sources (fetched 2026-08-25):
<https://wiki.bloodontheclocktower.com/Sentinel> and the raw wikitext via
`https://wiki.bloodontheclocktower.com/api.php?action=parse&page=Sentinel&prop=wikitext`,
plus <https://wiki.bloodontheclocktower.com/Fabled>.

Current ability text (matches `characters.json`):

> "There might be 1 extra or 1 fewer Outsider in play."

How to Run (quoted):

> "At the start of the game, declare that the Sentinel is in play. Add the Sentinel
> token to the Grimoire. While setting up the game, before putting character tokens in
> the bag, add an Outsider token and remove a Townsfolk token, remove an Outsider token
> and add a Townsfolk token, or do neither."

Examples (paraphrased from the wiki):

- A 7-player game with no Outsider-adding characters: the Demon bluffs Saint, and the
  good team cannot disprove it because the Sentinel might have added the Saint slot.
- A 9-player game with the Baron on the script but a Witch actually in play: the Sentinel
  hides the fact that there are fewer Outsiders than the script suggests.

Timing / edge cases that matter for storytelling:

- **The decision is made once, during setup, before tokens go in the bag.** It is never
  re-decided and never announced; the whole point is that nobody knows which of the three
  options was taken.
- **It stacks on top of the bracket math**, not instead of it. Baron `[+2 Outsiders]` on
  9 players gives 3 Outsiders; a Sentinel makes 2, 3 or 4 legal.
- **`-1 Outsider` cannot take the count below 0**; at a base of 0 Outsiders only "+1" and
  "no change" are available.
- Fabled general rules (<https://wiki.bloodontheclocktower.com/Fabled>): Fabled "cannot
  be killed and they alter the game itself"; they are "immune to all game effects,
  including death, drunkenness, and poisoning"; and they "do not count as players for the
  'two players remain alive' victory condition for the evil team". The Sentinel is in the
  "Custom Scripts" group and is one of the Fabled that must be **added at the start of
  the game**.
- The wiki page lists **no reminder tokens** (only the Sentinel character token) and
  **no jinxes**. The Sentinel has **no night action** in either night order.

## What the app does today

Data:

- `engine/src/main/resources/botc/data/characters.json:2288` — id `sentinel`, team
  `fabled`, ability matches the wiki, `"setup": false`, `reminders: []`.
- `engine/src/main/resources/botc/data/raw_sv_travellers_fabled.json` — the *same*
  character in the raw source file carries `"setup": true`. The flag was lost in the
  file that the app actually loads (see D4).
- Correctly absent from both order lists in `night_and_jinxes.json`.
- No `night_guide.json` entry (correct — nothing to run at night).

Engine:

- `engine/src/main/kotlin/com/clocktower/engine/GameActions.kt:443-455` — `validateBag`
  expands `Setup.allowedDistributions` by ±1 Outsider traded against Townsfolk and
  filters out negative counts when `"sentinel" in fabledIds`. **This is correct and
  works**, including the ≥0 clamp and stacking on top of bracket modifiers.
- `GameActions.kt:503-511` — `validateSetupState` passes `state.fabledIds` through, so
  the "Begin night" guard in `GameShell.kt:134` honours an active Sentinel. **Works.**
- `engine/src/test/kotlin/com/clocktower/engine/ScriptParserTest.kt:209` — `SentinelTest`
  already covers the relax-by-one case.

UI:

- The only way to activate a Fabled is `FabledSheet`
  (`app/src/main/java/com/clocktower/grimoire/ui/screens/GameExtras.kt:145-198`), a flat
  toggle list of `gameData.allFabled`, reachable from the overflow menu
  (`GameShell.kt:239`) or the grimoire's top-right chip row (`GrimoireScreen.kt:198-219`).
  Both live inside `GameShell`, i.e. **only after the game has already started**.
- `SetupScreen.kt:356` — the bag builder calls
  `GameActions.validateBag(selected, playerCount, allowAnyDuplicates = allowDuplicates)`
  **without `fabledIds`**, so the Sentinel is invisible to the step where it actually
  matters.
- `SetupScreen.kt:333-357` — the "Need: N townsfolk · N outsiders · …" line comes from
  `Setup.adjustedDistribution` and never mentions the Sentinel.
- `GameActions.kt:397` — `randomBag` calls `validateBag(bag, playerCount)` with no
  `fabledIds`, so "Randomize" can never produce a Sentinel-shifted bag.
- Escape hatches exist: `SetupScreen.kt:488-492` ("Deal anyway (I know what I'm doing)")
  and `GameShell.kt:571-588` ("Start the night anyway"), both of which name the Fabled in
  their explanatory text.

Storyteller's actual experience today: you cannot declare the Sentinel until the game has
begun; while building the bag the app fights you with "Outsider: 2 in bag, expected 1" and
you must click a scary override button; nothing ever records *which* of the three options
you took, and nothing reminds you that the option had to be exercised before the bag was
sealed.

## Defects and gaps

1. **P1** · Sentinel cannot be declared before or during setup · The wiki requires the
   Sentinel to be declared "at the start of the game… before putting character tokens in
   the bag", but `FabledSheet` is only reachable from `GameShell`
   (`GameShell.kt:239`, `GameShell.kt:501`), which does not exist until `onStart` has
   dealt a bag. `SetupScreen.kt` has no Fabled UI at all. Repro: Home → new game → pick
   script → names → bag; there is no Fabled control anywhere in the three setup stages.
2. **P1** · Bag validation ignores the Sentinel · `SetupScreen.kt:356` calls
   `validateBag` without `state.fabledIds`, so an intentionally Sentinel-shifted bag is
   reported as illegal and the primary "Deal randomly & start" button is disabled
   (`SetupScreen.kt:485`). The engine already supports the correct behaviour
   (`GameActions.kt:443`) — only this call site is wrong. Repro: 8 players, TB, build
   6 Townsfolk / 0 Outsiders / 1 Minion / 1 Demon → red "Outsider: 0 in bag, expected 1".
3. **P1** · "Randomize" cannot use the Sentinel · `GameActions.kt:397` validates with no
   `fabledIds`, and `randomBag` has no `fabledIds` parameter at all, so the randomiser
   always produces the un-shifted distribution. The ST who wants the Sentinel's
   uncertainty has to hand-build the bag.
4. **P2** · Data drift: `setup` flag lost · `raw_sv_travellers_fabled.json` has
   `"setup": true` for `sentinel`; `characters.json:2288` has `"setup": false`. Harmless
   today because `Setup.modifierFor` is never asked about a Fabled, but it will bite the
   moment a fabled-carrying imported script puts `sentinel` into `resolve(script)`
   (`GameData.kt:49`) — and it means the Setup screen cannot render a `[±1 Outsider]`
   bracket chip for it.
5. **P2** · The ±1 choice is never recorded · `GameState` has no place to store which
   option was taken (`GameState.kt:98` is a bare `fabledIds: List<String>`). If the ST is
   later asked "how many Outsiders are in play?" or needs to re-derive the Baron/Sentinel
   arithmetic mid-game, the app cannot tell them, and undo/redo can silently lose it.
6. **P3** · No storyteller-facing explanation at the point of use · The bag screen never
   says "Sentinel active: 0, 1 or 2 Outsiders are all legal" — the ST has to remember why
   the checker is quiet.

## Proposed behaviour (spec)

Night action: **none** (no entry in either night order — leave as is).

Setup:

- Add a **Fabled stage to `SetupScreen`** (or a persistent "Fabled…" button on the bag
  stage) that writes `state.fabledIds` before the bag is built. Fabled selection must be
  possible with no players dealt.
- Extend `GameState` with a per-Fabled configuration payload (see the cross-cutting note
  in every fabled file):
  `val fabledConfig: Map<String, FabledConfig> = emptyMap()`, where for the Sentinel
  `FabledConfig.Sentinel(outsiderDelta: Int)` with `outsiderDelta ∈ {-1, 0, +1}`,
  default `0` and **the ST must pick before the first night**.
- Bag stage with Sentinel active:
  - `validateBag(selected, playerCount, state.fabledIds, allowDuplicates)` — pass the ids
    (one-line fix).
  - Header line becomes: `Need: 5 townsfolk · 1 outsider · 1 minion · 1 demon —
    Sentinel: 0, 1 or 2 outsiders are all legal`, computed from the same
    `allowedDistributions` set the validator uses.
  - Three explicit chips **-1 Outsider / no change / +1 Outsider**; tapping one sets
    `outsiderDelta` and re-targets the "Need:" line to that exact distribution, so the
    ST builds against a concrete target instead of a range.
  - `randomBag` gains a `fabledIds: Collection<String>` parameter; when the Sentinel is
    active it picks the delta from `outsiderDelta` (or uniformly at random from the legal
    options if the ST left it unset) and validates with the same ids.
- The chosen delta is written into the game log and shown in the setup summary line, so a
  later "how many Outsiders?" question is answerable from the app.

Immediate effects: none. Deferred effects: none. Expiry: never (the Sentinel is active for
the whole game). Information: none.

Visibility: nothing is shown to any player beyond the public announcement that the
Sentinel is in play. The app should offer a one-tap **"Announce active Fabled"** card
(reusing `components/ShowCards.kt`) that lists the Fabled tokens in play — the wiki
requires the Fabled to be declared publicly.

Interactions to handle explicitly:

- Stacks with Baron/Godfather/Xaan/Kazali brackets — already correct in
  `Setup.allowedDistributions` + the Sentinel expansion; keep that composition.
- `-1 Outsider` at a 0-Outsider base must be filtered out (already done at
  `GameActions.kt:452`); the UI's three chips must hide the illegal one.
- Fabled must never appear in the bag pool: `SetupScreen.kt:339`
  (`filter { it.team.isTownResident }`) already excludes them — keep.

UI text for the setup step (storyteller voice):

- "Sentinel — the Outsider count is a secret. Choose: one fewer / as printed / one extra."
- "Announce to the table that the Sentinel is in play. Do not say which way you went."

Data changes:

- `characters.json:2288` — set `"setup": true` to match the raw source, and consider
  giving the ability text the conventional bracket `[±1 Outsider]` used elsewhere for
  setup modifiers. (Do **not** let `Setup.modifierFor` apply it automatically to the bag;
  the Sentinel is never *in* the bag.)

## Tests to add

1. **Given** a TB game, 8 players, `fabledIds = ["sentinel"]`, and a bag of
   6 Townsfolk / 0 Outsiders / 1 Minion / 1 Demon,
   **when** `GameActions.validateBag(bag, 8, listOf("sentinel"))` is called,
   **then** the issue list is empty. (Passes today for the engine; add the mirrored UI-level
   test that the *bag screen* path passes the ids.)
2. **Given** the same game with `fabledIds = emptyList()`,
   **when** validated, **then** exactly one issue mentioning `Outsider` is produced.
3. **Given** 5 players (base 3/0/1/1) and an active Sentinel,
   **when** `allowedDistributions` is expanded, **then** `-1 Outsider` variants are absent
   and the legal Outsider counts are `{0, 1}` only.
4. **Given** a Baron in the bag on 9 players and an active Sentinel,
   **when** validated, **then** Outsider counts 1, 2 and 3 are all accepted and 0 and 4
   are rejected.
5. **Given** an active Sentinel, **when** `randomBag(available, 8, fabledIds =
   listOf("sentinel"))` is called 200 times, **then** every returned bag passes
   `validateBag(..., listOf("sentinel"))` and at least one bag has a non-base Outsider
   count.
6. **Given** `characters.json`, **when** the `sentinel` entry is read, **then**
   `setup == true` (guards the drift found in D4).
