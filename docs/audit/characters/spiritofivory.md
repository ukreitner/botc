# Spirit of Ivory (spiritofivory) — Fabled

## Official rules (sources)

Sources (fetched 2026-08-25):
<https://wiki.bloodontheclocktower.com/Spirit_of_Ivory> and the raw wikitext via
`https://wiki.bloodontheclocktower.com/api.php?action=parse&page=Spirit_of_Ivory&prop=wikitext`,
plus <https://wiki.bloodontheclocktower.com/Fabled>.

Current ability text (matches `characters.json`):

> "There can't be more than 1 extra evil player."

How to Run (quoted from the wikitext):

> "At the start of the game, declare that the Spirit of Ivory is in play. Add the Spirit
> of Ivory token and their reminder token to the Grimoire. At all times, if there is an
> extra evil character in play, mark the Spirit of Ivory with the **NO MORE EVIL**
> reminder. (If any character becomes good, remove the reminder.) If a player would become
> evil and the Spirit of Ivory is marked **NO MORE EVIL**, that player stays good."

Example (quoted):

> "The Fang Gu attacks an Outsider and creates an evil player. The Devil's Advocate
> chooses the Goon at night. Normally, the Goon would turn evil, but the Goon remains good
> because there is already 1 more evil character than normal in play."

Rules that matter for storytelling:

- The reference point is **the number of evil characters the setup started with** (the
  distribution's Minions + Demons). "1 extra evil player" means at most one conversion
  beyond that baseline is allowed to stand at any time.
- **The token lives on the Spirit of Ivory's own token, not on a player.** It is a
  grimoire-level flag, placed the moment an extra evil exists and removed the moment any
  character becomes good again (which frees the cap for a later conversion).
- The block is on *becoming evil*, not on the ability itself: the Fang Gu still jumps, the
  Devil's Advocate still chooses, the Ogre still picks — the target simply stays good.
- Characters that can create an extra evil player and are therefore gated by this Fabled:
  **Fang Gu** (Outsider becomes an evil Fang Gu), **Ogre** (becomes the alignment of the
  chosen player), **Bounty Hunter** (a Townsfolk turns evil at setup — note this one makes
  the extra evil exist from night 1), **Cult Leader** (turns to a neighbour's alignment),
  **Mezepheles** (the good player who says the word turns evil), **Pit-Hag** (creating a
  Minion/Demon out of a good player), **Devil's Advocate + Goon**, **Snitch/Summoner**
  edge cases, **Politician** (turns evil at the end of the game — the wiki does not list
  it, and it happens at scoring rather than in play, so treat with care),
  **Kazali**, **Boffin**-granted evil abilities, and Storyteller fiat.
- Conversely, characters that turn someone **good** (Snake Charmer swap, Goon flipping
  back, Politician's team change, Ogre following a good player) remove the marker.
- Fabled general rules (<https://wiki.bloodontheclocktower.com/Fabled>): cannot be killed,
  immune to all game effects, do not count for the two-alive evil win. It is one of the
  "Custom Scripts" Fabled and is declared **at the start of the game**.
- The wiki page lists **no jinxes**, **no night action** and **no setup flag**.

## What the app does today

Data:

- `engine/src/main/resources/botc/data/characters.json:2300` — ability matches the wiki;
  `reminders: ["No extra evil"]`. Note the label differs from the official
  **NO MORE EVIL** wording.
- Correctly absent from both night order lists in `night_and_jinxes.json`.
- No `night_guide.json` entry.

Engine:

- `GameState.kt:25` — `Player.alignmentFlipped: Boolean`; `GameState.kt:49-52` —
  `isEvil(lookup) = (character team is evil) != alignmentFlipped`. This is the only
  alignment model.
- `GameActions.kt:129-130` — `flipAlignment(state, playerId)` is a bare toggle with **no
  rule checks of any kind**.
- `GameActions.kt:78-95` — `starPass` (used for both the Imp star-pass and the Fang Gu
  jump) sets `alignmentFlipped = false` on the heir and gives them the Demon's character —
  i.e. it creates an extra evil player without consulting anything.
- There is **no count of "extra evil players"** anywhere in the engine, and no reference
  baseline (`Setup.distributionFor(...).minions + .demons` is never compared against the
  live board).
- `GameActions.kt:184-195` — reminders can only be attached to a `Player`; there is no
  container for a token that belongs to a Fabled/the grimoire itself.

UI:

- `FabledSheet` (`GameExtras.kt:145-198`) toggles `spiritofivory` on. Nothing else happens.
- `SeatSheet.kt:315` — a plain `OutlinedButton("Flip alignment")`; `SeatSheet.kt:188`
  renders "· turned evil"/"· turned good". No warning, no cap, no prompt.
- `NightScreen.kt:484-497` — the Fang Gu quick resolution ("Fang Gu jump (once per game) —
  chose an Outsider? The Fang Gu dies and the Outsider becomes an evil Fang Gu") applies
  `starPass` + a `("fanggu","Once")` token with no Spirit-of-Ivory check.
- The `("spiritofivory","No extra evil")` token is **unreachable from the reminder
  picker**: `ReminderPicker` (`SeatSheet.kt:492-500`) builds its list from
  `viewModel.gameData.resolve(state.script)`, and `GameData.resolve` (`GameData.kt:49-52`)
  only returns `script.characterIds`; built-in scripts are `filter { it.team.isTownResident }`
  (`GameData.kt:35-42`), which excludes Fabled. Because the Spirit of Ivory has no night
  step, the `NightToolTray` route that rescues the Storm Catcher and Toymaker tokens
  (`NightScreen.kt:98`) does not exist here either. The only usable token is the generic
  `"Evil"`/`"Good"` chip (`SeatSheet.kt:502`), placed on a player.
- The grimoire renders active Fabled as small non-interactive chips
  (`GrimoireScreen.kt:198-219`) with no reminder slots.

Storyteller's actual experience today: switch the Fabled on, then track "how many extra
evil players do I have?" entirely in your head, and remember on your own to refuse the
next Devil's Advocate/Ogre/Fang Gu conversion. The app never mentions the Fabled again.

## Defects and gaps

1. **P0** · The cap is not enforced, or even warned about, anywhere · `flipAlignment`
   (`GameActions.kt:129`), `starPass` (`GameActions.kt:78`) and the Fang Gu quick
   resolution (`NightScreen.kt:484`) all create extra evil players unconditionally.
   Repro: activate Spirit of Ivory, resolve a Fang Gu jump onto an Outsider, then use
   "Flip alignment" on a Goon for the Devil's Advocate — the app allows a second extra
   evil, breaking the Fabled's only rule.
2. **P1** · The NO MORE EVIL marker has nowhere to live · `PlacedReminder` can only be
   attached to a `Player` (`GameState.kt:29`, `GameActions.kt:184`). The official token
   goes on the *Fabled token*. Today there is literally no legal place to put it, and the
   token is unreachable from `ReminderPicker` anyway (`SeatSheet.kt:497` →
   `GameData.kt:49`).
3. **P1** · No baseline evil count is ever computed · Nothing compares the live evil count
   to `Setup.distributionFor(n).minions + .demons` adjusted by setup modifiers, so the app
   cannot answer "is there already an extra evil?" — which is the trigger condition for
   the whole Fabled. (`Setup.kt:254` `adjustedDistribution` already gives the baseline; it
   is simply never used at runtime.)
4. **P1** · Alignment changes are invisible to the rules layer · `flipAlignment` is a
   toggle with no cause, no timestamp and no log entry, so even a "you have N extra evil"
   readout cannot distinguish a deliberate conversion from a correction of a mis-tap.
5. **P2** · Reminder label drift · `characters.json:2300` uses `"No extra evil"`; the
   official token is **NO MORE EVIL**. Cosmetic but it makes the app's grimoire not match
   a physical one on the table.
6. **P2** · The Fabled cannot be declared before the game starts · `FabledSheet` is only
   reachable from `GameShell` (`GameShell.kt:239`, `:501`), but the Bounty Hunter creates
   its extra evil player *during setup*, so the cap can already be relevant before night 1.
7. **P3** · No "what counts as an extra evil?" reference in-app · The ST has to know the
   conversion character list from memory; the app has all the data
   (`characters.json` abilities) to surface "Ogre, Cult Leader, Devil's Advocate, Fang Gu,
   Mezepheles, Pit-Hag are in this script and are capped by the Spirit of Ivory".

## Proposed behaviour (spec)

Night action: **none** (leave out of both order lists). This is a *rule modifier* that must
hook into every alignment-changing code path.

Core engine addition — an evil-count model:

```
object Alignment {
    /** Evil seats the setup started with: minions + demons from the adjusted distribution. */
    fun baselineEvilCount(state, lookup): Int

    /** Live evil seats among non-Travellers (Player.isEvil, so flips count). */
    fun currentEvilCount(state, lookup): Int

    fun extraEvilCount(state, lookup): Int = currentEvilCount - baselineEvilCount

    /** True when the Spirit of Ivory currently forbids another conversion. */
    fun noMoreEvil(state, lookup): Boolean =
        "spiritofivory" in state.fabledIds && extraEvilCount(state, lookup) >= 1
}
```

Notes on the baseline: compute it **once at SETUP → NIGHT 1** and store it in
`fabledConfig["spiritofivory"] = SpiritOfIvory(baselineEvil: Int)` rather than
recomputing, because deaths, Travellers arriving and character changes all perturb the
live distribution. Travellers are excluded from both counts (they are not part of the
distribution). Note that a Bounty Hunter's setup conversion happens *before* night 1 and
therefore lands **above** the baseline — the marker should already be on at dawn of night 1.

Grimoire-level reminders:

- Extend the state with `val grimoireReminders: List<PlacedReminder> = emptyList()` (or
  `fabledReminders: Map<String, List<String>>`) so tokens that belong to a Fabled token
  rather than a seat have a home. Render them under the Fabled chip row
  (`GrimoireScreen.kt:198-219`), which should become tappable per-Fabled rather than
  opening the global sheet.
- Place `PlacedReminder("spiritofivory","No more evil")` automatically whenever
  `extraEvilCount >= 1`, and remove it automatically when it drops to 0. Derived state —
  never hand-managed.

Gating the conversion paths:

- Every path that can make a player evil must route through one guarded helper:
  ```
  fun becomeEvil(state, playerId, cause: String, lookup): Result
  // Result.Blocked when Alignment.noMoreEvil(state) — the player stays good.
  ```
  Call sites to convert: `flipAlignment` (`GameActions.kt:129`), `starPass`
  (`GameActions.kt:78`, used by Imp star-pass **and** Fang Gu jump), the Fang Gu quick
  resolution (`NightScreen.kt:484-497`), and any future Ogre / Cult Leader / Bounty Hunter
  / Mezepheles / Devil's Advocate / Pit-Hag resolvers.
- **Important asymmetry**: the Imp star-pass does *not* create an extra evil (a Minion
  becomes the Demon — evil count is unchanged), while the Fang Gu jump does (an Outsider
  becomes evil). `starPass` is shared by both, so the guard must be applied at the caller,
  or `starPass` must take an `createsExtraEvil: Boolean` flag.
- When blocked, the UI must say so in storyteller voice and still let the ability *happen*:
  `"Spirit of Ivory: there is already 1 extra evil player — <name> stays GOOD.
   The Devil's Advocate's choice still happened; nothing else changes."`
  Plus an explicit **"Override (storyteller call)"** escape.
- When a character becomes good (Snake Charmer swap `GameActions.kt:62-71`, Goon flip
  back, Ogre following a good player), re-derive the count so the marker lifts and the
  next conversion is allowed again.

Deferred effects / day: none. Expiry: the marker never expires on a phase boundary; it is
purely a function of the live evil count. Do **not** add it to `EXPIRES_AT_DAWN`/`DUSK`.

Information / visibility: nothing is shown to players; the Fabled's presence is announced
publicly at game start.

Day-time inputs the app must record: none, but the **game log** should get a line for
every alignment change ("Night 3: <name> turned EVIL (Fang Gu)" / "Night 4: <name> would
have turned evil — blocked by the Spirit of Ivory"), because "how many extra evil are
there" is exactly the question the ST loses track of.

Interactions to handle explicitly:

- **Fang Gu** — the jump is the canonical trigger; block the alignment change but still
  kill the Fang Gu? **No** — if the conversion is blocked the jump does not happen at all
  (the Outsider simply dies as a normal attack). The wiki example only covers the "already
  1 extra" case for the *second* conversion, so surface a decision prompt rather than
  silently choosing; flagged as an open rules question.
- **Bounty Hunter** — creates its extra evil at setup; make sure the baseline snapshot is
  taken *before* that conversion, or the cap will be off by one.
- **Ogre / Cult Leader** — these follow a neighbour's alignment and can flip *both* ways;
  they must go through `becomeEvil` on the evil direction and through the un-marking path
  on the good direction.
- **Politician** — turns evil at the end of the game for scoring; the wiki does not list it
  and it does not create an in-play evil player. Leave out of the cap and say so in the
  step text.
- **Legion / Riot** — team-warping scripts where "baseline evil" is meaningless; when
  `legion` or `riot` is in play, disable the automatic cap and show a note instead.

UI text:

- Fabled chip tooltip / grimoire caption: `Spirit of Ivory — 1 extra evil player allowed.
  Currently: <k> extra.` with the chip turning red when `k >= 1`.
- Blocked conversion dialog: `Spirit of Ivory: <name> stays good. There is already 1 more
  evil player than the game started with.`

Data changes:

- `characters.json:2300` — rename the reminder to `"No more evil"` to match the official
  token.

## Tests to add

1. **Given** a 9-player TB game (baseline 1 Minion + 1 Demon = 2 evil) with
   `fabledIds = ["spiritofivory"]` and no conversions,
   **when** `Alignment.extraEvilCount` is computed, **then** it is `0` and
   `noMoreEvil == false`.
2. **Given** the same game after one good player is flipped evil,
   **when** the count is recomputed, **then** `extraEvilCount == 1`,
   `noMoreEvil == true`, and the grimoire holds
   `PlacedReminder("spiritofivory","No more evil")`.
3. **Given** `noMoreEvil == true`, **when** `becomeEvil(secondPlayer)` is called,
   **then** the returned state has `secondPlayer.isEvil == false` and the result reports
   `Blocked`.
4. **Given** `noMoreEvil == true`, **when** a Snake Charmer swap turns the former Demon
   good and the count drops to 0, **then** the marker is removed and a subsequent
   `becomeEvil` succeeds.
5. **Given** an Imp star-pass to a Minion with `fabledIds = ["spiritofivory"]` and
   `extraEvilCount == 1`, **when** the star pass resolves, **then** it is **not** blocked
   (the evil count is unchanged).
6. **Given** a Fang Gu jump onto an Outsider with `extraEvilCount == 1`,
   **when** the resolution is attempted, **then** it is blocked and the Fang Gu keeps its
   character.
7. **Given** a Bounty Hunter game, **when** the baseline is snapshotted at SETUP → NIGHT,
   **then** the Bounty Hunter's converted Townsfolk counts as the one permitted extra evil
   and the marker is present from night 1.
8. **Given** 2 Travellers, one of them evil, **when** the counts are computed,
   **then** Travellers are excluded from both baseline and current counts.
