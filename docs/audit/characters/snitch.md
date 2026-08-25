# Snitch (snitch) — Experimental Outsider

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Snitch> (fetched 2026-08-25);
interaction cross-check at <https://botc.me/character/snitch>.

Current ability text: **"Each Minion gets 3 bluffs."** (`characters.json` matches.)

Summary (wiki): "The Snitch accidentally gives information to the evil team. The Minions
learn three not-in-play characters at the start of the game, just like the Demon does."

How to Run (wiki, quoted):
- "During the first night, wake a Minion. Show the **THESE CHARACTERS ARE NOT IN PLAY**
  info token, then show three not-in-play character tokens. Put the Minion to sleep.
  Repeat this process until all Minions have learnt three not-in-play characters."

Examples (wiki, quoted):
- "On the first night, the Demon and its two Minions all learn that the Sage, Innkeeper,
  and Golem are not in play."
- "On the first night, the Demon learns that the Fool, Monk, and Saint are not in play.
  The Mastermind learns that the Fool, Monk, and Saint are not in play. The Witch learns
  that the Fool, Flowergirl, and Barber are not in play. The Fearmonger learns that the
  Noble, Amnesiac, and Heretic are not in play."
- "On the fourth night, the Pit-Hag creates a Snitch. All Minions learn three not-in-play
  characters."

What those examples establish:
- Each Minion's three bluffs are chosen **independently**. They **may** match the
  Demon's set and **may** match each other, or all be different — Storyteller's choice.
- The Snitch's own step is on the **first night**, positioned **after MINION INFO and
  before DEMON INFO** (official night sheet order: … Magician, MINION INFO, **Snitch**,
  Lunatic, Summoner, DEMON INFO …).
- The Demon still gets its own 3 bluffs from the DEMON INFO step; the Snitch does not
  replace them.
- If a Snitch is **created mid-game** (Pit-Hag night 4), all Minions learn three
  not-in-play characters **then** — so the ability has an other-night path.

Storyteller tips (wiki): encourage "all players to share information about which
character they are" early; "there is less benefit to good players lying" when the Snitch
is in play; "be as active as you can" and "get the group communicating". When the Snitch
reveals matters strategically.

Jinxes / interactions:
- **Marionette** (official jinx, present in this repo): "The Marionette does not learn 3
  not in-play characters. The Demon learns an extra 3 instead." → the Demon receives
  **six** bluffs in a Snitch + Marionette game.
- **Recluse** (botc.me interaction, "yes, but don't"): "Recluse can get three bluffs but
  it's not recommended." — the Recluse may register as a Minion, so the Storyteller *may*
  wake them for Snitch bluffs.
- A drunk or poisoned Snitch: the wiki does not spell this out. The general rule is that
  the ability malfunctions — the Storyteller may show bluffs that are actually **in
  play**, or none at all. The repo's `night_guide.json` asserts the stronger "the Minions
  do not receive bluffs"; that is one legal choice, not the only one. *(Flagged.)*
- Whether a Minion **created after night 1** (Pit-Hag, Summoner, Kazali) gets bluffs while
  a Snitch is already in play is not stated on the wiki. *(Flagged as uncertain;
  recommend offering the Storyteller the option rather than deciding silently.)*

## What the app does today

Data:
- `engine/src/main/resources/botc/data/characters.json:1729-1740` — id, exp, outsider,
  ability "Each Minion gets 3 bluffs.", `setup:false`,
  `firstNightReminder: "Wake each Minion. Show the 'These characters are not in play'
  token. Show 3 not-in-play character tokens."`, no reminder tokens.
- `engine/src/main/resources/botc/data/night_and_jinxes.json:310` — `snitch` sits between
  `MINION_INFO` (line 309) and `DEMON_INFO` (line 313) in `firstNight`. **Correct
  position.** Not in `otherNight`.
- `engine/src/main/resources/botc/data/night_and_jinxes.json:98-102` — the
  `marionette`/`snitch` jinx, correct text.
- `engine/src/main/resources/botc/data/night_guide.json:1302-1316` — a `first` entry with
  good prose ("Before the Demon receives its bluffs, wake each Minion one at a time, show
  the 'These characters are not in play' info token and 3 not-in-play character tokens as
  bluffs… Each Minion may be shown different bluffs. If the Snitch is drunk or poisoned,
  the Minions do not receive bluffs.") and one show card, a `message` reading
  "THESE CHARACTERS ARE NOT IN PLAY".

Code: **no Kotlin file mentions `snitch`.** The step is produced by the generic branch of
`NightOrder.build` (`engine/.../NightOrder.kt:120-179`):
- `holders = inPlay["snitch"]` → the **Snitch's own seat**, so the row displays the
  Snitch player's name (`app/.../screens/NightScreen.kt:735-742`) and the
  `NightToolTray` targets the Snitch.
- `detail` is the raw `firstNightReminder` string — it names no Minion and no bluff.

The bluff model is a single global list:
- `GameState.demonBluffIds: List<String>` (`engine/.../GameState.kt:102`).
- `GameActions.setBluffs` **hard-caps it at three**: `bluffIds.take(3)`
  (`engine/.../GameActions.kt:208-209`).
- `GameActions.suggestBluffs` (`:121-127`) picks 2 Townsfolk + 1 Outsider not in play.
- `BluffsSheet` (`app/.../screens/BluffsSheet.kt:35-112`) is titled "Demon bluffs", shows
  "n/3 chosen", and refuses a fourth (`if (current.size < 3) current + c.id else current`,
  `:84-88`).
- `NightOrder` DEMON_INFO (`engine/.../NightOrder.kt:81-119`) appends the three names to
  the Demon step's detail.
- `NightScreen.kt:783-788` gives DEMON_INFO a "Show bluffs full-screen" chip
  (`ShowCard.BluffsCard`, `app/.../components/ShowCards.kt:70`, rendered at `:143`).
- `GrimoireScreen.kt:186-196` shows the three bluff tokens in the grimoire corner.
- MINION_INFO (`engine/.../NightOrder.kt:60-80`) says only "Wake all Minions (…). They see
  each other, then point out the Demon (…)." — **no mention of the Snitch and no bluffs.**

Storyteller experience today: on night 1 you get a row titled "Snitch" with the *Snitch
player's* name beside it and a sentence telling you to wake each Minion and show three
not-in-play characters — with no list of who the Minions are, no bluff sets to show, no
show card carrying actual character tokens, and no way to record what each Minion was
told. You choose one global trio for the Demon and then improvise per-Minion sets on
paper. Nothing knows about the Marionette jinx's extra three.

## Defects and gaps

1. **P0 · The data model cannot hold per-Minion bluffs.** `GameState.demonBluffIds` is one
   list (`engine/.../GameState.kt:102`) and `setBluffs` truncates to three
   (`engine/.../GameActions.kt:209`). With a Snitch in play the Storyteller must track
   3 × (number of Minions) additional characters entirely outside the app. Repro: Snitch
   + 2 Minions, open menu → "Demon bluffs" — you can pick exactly three, for everyone.
2. **P0 · The MINION_INFO step never mentions the Snitch or shows bluffs.**
   `engine/.../NightOrder.kt:60-80` builds the Minion-info detail with no Snitch branch.
   The brief's requirement — "the MINION_INFO step must show 3 bluffs per Minion, chosen
   in setup like the Demon bluffs" — is entirely unmet. Repro: Snitch in play, night 1,
   read the "Minion info" row.
3. **P0 · The Marionette jinx (Demon gets an extra 3) cannot be represented.** The jinx
   text is in the data (`night_and_jinxes.json:98-102`) and will even surface in "Jinxes
   in play" (`engine/.../GameData.kt:23-26`, both characters in play), but the Demon's
   bluff list is capped at three, so the six-bluff case is impossible to record or show.
4. **P1 · The Snitch step targets the wrong seats.** `NightOrder.build:143`/`:177` sets
   `playerIds = holders` = the Snitch. The players who wake are the **Minions**. The row
   header names the wrong person and the tool tray offers the wrong targets. It also
   means `allDead` (`app/.../screens/NightScreen.kt:702`) would grey the row out if the
   Snitch were dead, which is irrelevant to whether the Minions get bluffs.
5. **P1 · No show card carries the actual bluff tokens for a Minion.** The guide's only
   card is the text "THESE CHARACTERS ARE NOT IN PLAY"
   (`night_guide.json:1307-1313`); the Storyteller then has to fumble for three physical
   tokens or use the generic "All tokens" sheet. `ShowCard.BluffsCard`
   (`app/.../components/ShowCards.kt:70`) already renders exactly the right card — it is
   simply not wired to anything but the Demon.
6. **P1 · No record of what each Minion was told.** Nothing in `GameState` remembers it,
   so on day 3 the Storyteller cannot check a Minion's claim against the bluffs they
   actually gave, and the end-game reveal cannot show it.
7. **P2 · No other-night path for a Snitch created mid-game.** `snitch` is absent from the
   `otherNight` list, so the wiki's own example (Pit-Hag creates a Snitch on night 4)
   produces no step. Repro: change a seat to `snitch` on day 3, go to night 4 — no row.
8. **P2 · No path for a Minion created mid-game to receive bluffs** while a Snitch is
   already in play (Pit-Hag/Summoner/Kazali). Uncertain in the rules, but the app should
   at least offer it.
9. **P2 · The guide's drunk/poisoned rule is stated too strongly.**
   `night_guide.json:1306` says "the Minions do not receive bluffs." A malfunctioning
   ability more usually means *false* bluffs (characters that **are** in play). The prose
   should offer both.
10. **P2 · `BluffsSheet` excludes in-play characters from the candidate list**
    (`app/.../screens/BluffsSheet.kt:40-45`), which is right for a working Snitch but
    blocks the impaired-Snitch case above, and blocks the Recluse-as-Minion case where a
    Storyteller may deliberately want an odd set.
11. **P3 · The Snitch row's title and the Minion-info row are two separate rows that must
    both be ticked**, though in practice one wake serves both (you wake the Minions once,
    show them each other and the Demon, then show bluffs). The night sheet should let
    them be run as one combined wake.
12. **P3 · Teensyville (<7 players).** `NightOrder.build:52` gates MINION_INFO/DEMON_INFO
    on `players.count { !it.isTraveller } >= 7`. The Snitch row is **not** gated, which is
    correct — the Snitch's bluffs do not depend on player count — but with the info steps
    gone there is now no place at all where any bluff is chosen. The Snitch step must
    carry its own bluff picker so the small-game case works.

## Proposed behaviour (spec)

### Data-model change (the core of this spec)

Replace the single list with a keyed map, keeping a compatibility accessor:

```kotlin
// GameState.kt
/** Bluff sets by recipient seat id; key -1L is the Demon's shared set. */
val bluffSets: Map<Long, List<String>> = emptyMap()

/** Back-compat: the Demon's set. */
val demonBluffIds: List<String> get() = bluffSets[DEMON_BLUFF_KEY].orEmpty()

companion object { const val DEMON_BLUFF_KEY = -1L }
```

```kotlin
// GameActions.kt — replaces setBluffs(state, ids)
fun setBluffs(state: GameState, recipientId: Long, ids: List<String>, max: Int = 3): GameState
fun suggestBluffsFor(available: List<Character>, state: GameState, recipientId: Long, random: Random): List<String>
```

- `max` is 3 normally, and **6** for the Demon when a `snitch` and a `marionette` are both
  in play (the jinx).
- Serialisation: keep reading the old `demonBluffIds` field for saved games and migrate it
  into `bluffSets[-1L]`.

### Night step — first night

- **when:** first night, at its existing position `night_and_jinxes.json:310`
  (after MINION_INFO, before DEMON_INFO). Keep it.
- **wake condition:** a seat holds `snitch` **and** at least one seat is a Minion.
  (Do **not** gate on the Snitch being alive — the ability is a setup-time effect. Do not
  gate on player count.)
- **who wakes:** every Minion, one at a time. `playerIds` must be the **Minion seats**,
  not the Snitch's — mirror the MINION_INFO branch (`engine/.../NightOrder.kt:61-64`),
  including its `characterId != "marionette"` exclusion for the jinx.
- **targets:** none (no seat is chosen).
- **immediate effects:** none in the grimoire; three characters are shown per Minion.
- **information / show cards:** for each Minion seat, the step panel lists a row:
  `<Minion name> — <3 tokens> [Show]`, where `[Show]` puts up
  `ShowCard.BluffsCard(bluffSets[minionId])` preceded (or headed) by the
  "THESE CHARACTERS ARE NOT IN PLAY" caption. Each row has an "Edit"/"Suggest 3" affordance.
- **impaired Snitch:** if the Snitch is drunk/poisoned (`StatusEffects.isImpaired`,
  `engine/.../StatusEffects.kt:36-46`), the panel shows
  **"<Snitch> is drunk/poisoned — their ability malfunctions. Give bluffs that ARE in
  play, or skip the Minions entirely."** and the bluff picker for that game lifts the
  "not in play" filter.
- **Marionette jinx:** if a `marionette` seat exists, the step shows
  **"Marionette jinx: the Marionette gets NO bluffs. The Demon learns an extra 3 (six in
  total)."**, excludes the Marionette from the Minion list, and raises the Demon's bluff
  cap to 6.
- **Recluse:** if a `recluse` seat exists, an optional row
  **"Recluse (may register as a Minion) — give bluffs? Not recommended."**
- **UI text (row detail):**
  **"Snitch: wake each Minion one at a time, show 'These characters are not in play', then
  their three tokens. Each Minion may get a different set."**

### Night step — other nights (new)

- Add `snitch` to `otherNight` in `night_and_jinxes.json`, positioned analogously
  (immediately after the point where a newly-created Minion would learn its team).
- **wake condition:** a `snitch` seat exists **and** some Minion seat has no entry in
  `bluffSets` yet (i.e. the Snitch or that Minion arrived mid-game).
- Same panel; the row detail reads **"<Minion> has not been given bluffs yet — the Snitch
  is in play. Wake them and show three not-in-play characters."**
- Add a matching `"other"` entry to `night_guide.json`.

### MINION_INFO step change

`engine/.../NightOrder.kt:60-80` — when a `snitch` seat exists, append to the detail:
**" A SNITCH is in play: before you put each Minion back to sleep, show them their three
not-in-play characters."** and list each Minion's chosen set inline (mirroring how
DEMON_INFO already lists the Demon's, `:103-109`). This lets the Storyteller run one wake
instead of two.

### Bluffs sheet change

`app/.../screens/BluffsSheet.kt` becomes a multi-recipient sheet:
- A recipient selector at the top: **Demon**, then one chip per Minion (only when a
  `snitch` is in play), each showing `n/3` (`n/6` for the Demon under the Marionette
  jinx).
- "Suggest 3 for me" per recipient; plus **"Suggest for everyone"** which fills every
  empty set (independent draws, so sets differ — matching wiki example 2).
- Candidate filter stays "good characters not in play" by default, with a toggle
  **"allow in-play characters"** for the impaired-Snitch case.
- Grimoire corner (`app/.../screens/GrimoireScreen.kt:186-196`) keeps showing the Demon's
  set and gains a small "+N" badge when Minion sets exist; tapping opens the sheet.

### Setup validation

Extend `GameActions.validateSetupState` (`engine/.../GameActions.kt:503-561`): with a
`snitch` seat and ≥1 non-Marionette Minion seat, require a 3-character set for **each**
Minion before the first night can begin — the same class of setup requirement as the
Fortune Teller's red herring at `:547-559`. Issue text:
**"Snitch: choose 3 bluffs for <Minion name>"**.

### Expiry / persistence

Bluff sets never expire. They are a record, kept for the whole game and shown in the
end-game reveal (**"<Minion> was bluffed: Sage, Innkeeper, Golem"**), which also answers
the "what did I tell them?" problem.

### Visibility

- Each Minion sees only their own three.
- The Demon sees only its own set (3, or 6 under the Marionette jinx).
- The Lunatic sees nothing extra from the Snitch.
- Good players learn nothing.

### Data changes summary

- `night_and_jinxes.json`: add `"snitch"` to `otherNight`.
- `night_guide.json:1302-1316`: soften the impaired sentence to
  "…their ability malfunctions: show bluffs that are in play, or none at all."; add an
  `"other"` entry; add a `{"label":"Bluffs for this Minion","kind":"message"}` companion
  to the existing card.
- `characters.json:1729-1740`: consider tightening `firstNightReminder` to
  "Wake each Minion in turn. Show 'These characters are not in play', then that Minion's
  3 tokens." (no rules change).

## Tests to add

1. *Given* a 10-player game with a Snitch and two Minions, *when* the first-night sheet is
   built, *then* the `snitch` step's `playerIds` are the **two Minion seats**, not the
   Snitch's seat.
2. *Given* the same, *when* `validateSetupState` runs with no Minion bluff sets, *then*
   it reports one issue per Minion.
3. *Given* `bluffSets` holds different trios for two Minions, *then* both trios survive a
   serialise/deserialise round-trip and neither overwrites the other.
4. *Given* a Snitch **and** a Marionette in play, *then* the Demon's bluff cap is 6, the
   Marionette is absent from the Snitch step's `playerIds`, and the step detail contains
   the Marionette jinx sentence.
5. *Given* a Snitch in play, *then* the MINION_INFO step's detail mentions the Snitch and
   lists each Minion's bluffs.
6. *Given* **no** Snitch in play, *then* MINION_INFO's detail is byte-identical to today's
   (regression guard).
7. *Given* a saved game that contains only the legacy `demonBluffIds` field, *when* it is
   loaded, *then* `bluffSets[-1L]` equals that list.
8. *Given* a 6-player (teensyville) game with a Snitch and one Minion, *then* MINION_INFO
   and DEMON_INFO are absent but the `snitch` step is present and can hold a bluff set.
9. *Given* a Snitch created on night 4 (seat reassigned during day 3), *when* the
   other-night sheet for night 4 is built, *then* a `snitch` step is present.
10. *Given* a Snitch marked `poisoner:Poisoned`, *then* the step detail contains the
    malfunction guidance and the bluff candidate list is allowed to include in-play
    characters.
11. *Given* bluff sets recorded for each Minion, *then* the end-game reveal lists them.
