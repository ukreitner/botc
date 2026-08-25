# Politician (politician) — Experimental Outsider

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Politician> (fetched 2026-08-25);
jinx/interaction cross-check at <https://botc.me/character/politician>.

Current ability text: **"If you were the player most responsible for your team losing,
you change alignment & win, even if dead."** (`characters.json` matches exactly.)

How to Run (wiki):
- "When the game ends, if good lost and you feel that this was significantly due to the
  words and actions of the Politician player, declare that the Politician turns evil and
  wins too."
- "In rare cases where the Politician is evil and plays a major role in losing for evil,
  declare the Politician turns good and wins with the good team."

Key rules and clarifications:
- The alignment change happens **at game end only** — never mid-game. Nothing during the
  game registers the Politician as the other alignment.
- The bar is high: the Politician must have been "**very influential**" in the loss.
  Merely spreading false information is usually not enough. Wiki example 2: a Politician
  who voted for the Empath and argued they were evil, where the group then executed the
  Saint, does **not** win — the group's own decision caused the loss.
- Wiki example 1: the Politician convinces the group not to execute; evil wins off a
  Minion's Mayor bluff; the Politician turns evil and wins too.
- Wiki example 3: the Politician bluffs as the Atheist and the Storyteller is executed;
  evil wins; the Politician wins too.
- A **drunk or poisoned Politician cannot change alignment.** (The Storyteller judges
  this over the period in which the influential play happened.)
- A **dead** Politician can still change alignment and win ("even if dead").
- The Politician can also simply win with their original team, as normal. Their ability
  never *causes* a team to lose — it only re-assigns the Politician's personal result.
- Strategy note relevant to the Storyteller: the Politician is *supposed* to make
  outlandish claims and waste good abilities, so a Politician's chaos is expected play,
  not a mistake to correct.

Jinxes / documented interactions:
- **Boffin** (official, quoted on the wiki page): "The Demon can not have the Politician
  ability."
- **Heretic** (interaction): if the Heretic flips which team won, and the Politician was
  most responsible for their team losing *after* that flip, the Politician still changes
  alignment and wins.
- **Legion** (botc.me, treat as lower-confidence than the wiki): "The Politician might
  register as evil to Legion."
- **Vizier** (botc.me): "The Politician might register as evil to the Vizier."
- **Pit-Hag** (botc.me): "If the Pit-Hag turns an evil player into the Politician, they
  can't turn good due to their own ability." *(This reads like the Boffin/Cult Leader
  pattern; I could not confirm it on the wiki — flag as uncertain.)*

## What the app does today

Data:
- `engine/src/main/resources/botc/data/characters.json:1702-1712` — id, exp, outsider,
  correct ability text, `setup:false`, **no** night reminders and **no** reminder tokens.
- Not present in `night_and_jinxes.json` `firstNight` or `otherNight` (correct — no
  night action).
- **No jinx entry at all** in `night_and_jinxes.json` (the Boffin jinx is missing; the
  file has three `boffin` jinxes — heretic, cultleader, drunk — but not politician).

Code: **no Kotlin file mentions `politician`.** Nothing computes, prompts or reminds.

What exists that *could* serve the Politician:
- `Player.alignmentFlipped` (`engine/.../GameState.kt:25`) and
  `Player.isEvil` (`:49-52`) already model "alignment differs from the character's
  default"; `GameActions.flipAlignment` (`engine/.../GameActions.kt:129-130`) and the
  seat sheet expose it.
- `WinCheck.check` (`engine/.../WinCheck.kt:18-101`) produces an `Advisory(goodWins,
  reason, cautions)`; the Saint, Scarlet Woman, Mastermind, Imp star-pass and Mayor all
  get cautions. There is **no Politician caution**.
- `app/.../screens/GameShell.kt:505-518` shows `WinAdvisoryDialog`; `:258-265` offers
  "Declare good victory" / "Declare evil victory" straight from the menu.
- `app/.../screens/GameExtras.kt:236-265` (`WinAdvisoryDialog`) renders reason +
  cautions, then a single "Declare … victory" button.
- `app/.../screens/GameExtras.kt:270-349` (`RevealSheet`) lists every player with
  `p.isEvil(...)` colouring and their death; there is **no per-player win/lose marker**.
- `engine/.../GameState.kt:112` `storytellerNotes` is the only place to record what the
  Politician actually said during the game — one free-text blob, no per-day structure.

Storyteller experience today: the Politician is a token in the bag and nothing else. At
the end of the game the app asks "Is the game over?" with a team answer, and the reveal
declares GOOD WINS / EVIL WINS. Nothing asks whether the Politician flipped; nothing
records the evidence needed to make that judgement; and if the Storyteller does flip the
Politician (via the seat sheet), the reveal simply recolours the name with no
explanation, and the big banner still says only one team won.

## Defects and gaps

1. **P0 · The end-game flow never asks the Politician question.** `WinCheck.check`
   (`engine/.../WinCheck.kt:18-101`) returns advisories with cautions for Saint / Scarlet
   Woman / Mastermind / Imp / Mayor but has no branch for a Politician in play, and
   `WinAdvisoryDialog` (`app/.../screens/GameExtras.kt:236-265`) has one button that ends
   the game. Repro: play a game with a Politician, get to "Every Demon is dead — good
   wins", tap "Declare good victory" — the game ends with the Politician silently on the
   good team, ability unresolved. This is a rules-breaking omission: the Politician's
   only ability is at this exact moment.
2. **P0 · The reveal cannot express "evil won *and* this good-seated player won too".**
   `RevealSheet` (`app/.../screens/GameExtras.kt:285-290`) prints a single
   GOOD WINS/EVIL WINS banner and colours each player by `isEvil`. A flipped Politician
   just looks like they were evil all along, which misrepresents the game to the table
   during the reveal.
3. **P1 · Impairment is not tracked over time, so the "drunk or poisoned Politician
   cannot change teams" rule cannot be checked.** `StatusEffects.isImpaired`
   (`engine/.../StatusEffects.kt:36-46`) is instantaneous only; nothing logs when a seat
   became/stopped being drunk. At end of game the Storyteller has to recall it. (The
   engine *does* snapshot impairment for deaths — `DeathRecord.abilityImpairedAtDeath`,
   `engine/.../GameState.kt:87` — the same idea is needed as a running log.)
4. **P1 · No day-time record of what the Politician said or did.** The whole ability is a
   judgement about "the words and actions of the Politician player" across the game, and
   the app gives the Storyteller one undifferentiated `storytellerNotes` string
   (`engine/.../GameState.kt:112`, edited via `GameViewModel.setStorytellerNotes`,
   `app/.../ui/GameViewModel.kt:223`). This is the same complaint the user raised about
   Gossip: the app should make it trivial to jot a dated, attributed claim.
5. **P1 · The Boffin jinx is missing from the data.** `night_and_jinxes.json` has three
   boffin jinxes but none for the Politician, so a Boffin script can legally hand the
   Demon the Politician ability with no warning. File: `night_and_jinxes.json` (boffin
   block near line 273+).
6. **P2 · No nomination/execution-time hint.** `StatusEffects.nominationWarnings`
   (`engine/.../StatusEffects.kt:132-166`) covers Witch, Golem, Virgin, Fearmonger and
   Cerenovus madness. A Politician who is about to talk the town out of the correct
   execution is exactly the moment worth a note — currently nothing.
7. **P2 · The Legion / Vizier "might register as evil" jinxes are absent** from
   `night_and_jinxes.json`, so the Legion's and Vizier's own resolutions get no hint.
8. **P2 · Nothing prevents / warns about flipping the Politician early.** The seat sheet
   exposes `flipAlignment` at any time. If a Storyteller flips the Politician on day 3
   the app will treat them as evil for `Player.isEvil`, which feeds `WinCheck`
   (`WinCheck.kt:40`), `InfoCalc` evil counts (Empath/Chef/etc.) and Tea Lady logic
   (`StatusEffects.kt:85`) — silently corrupting information. The flip must be an
   end-game-only action for this character.
9. **P3 · The Politician's own bluffing behaviour ("outlandish claims") is expected
   play**, and the app has no place to note "this claim is Politician noise, not a
   Mutant/Cerenovus break". Polish.

## Proposed behaviour (spec)

The Politician never acts at night. The spec is an **end-game flow**, a **claims
ledger**, and **data fixes**.

### End-game checklist (generalisable)

Extend `WinCheck.Advisory` with an explicit list of end-game resolutions the Storyteller
must make before the result is final:

```kotlin
data class EndGameQuestion(
    val id: String,                  // "politician"
    val playerId: Long?,             // the Politician's seat
    val prompt: String,
    val options: List<String>,       // labelled outcomes
    val defaultOption: Int,
)
// Advisory gains: val questions: List<EndGameQuestion> = emptyList()
```

`WinCheck.check` emits a Politician question **whenever a `politician` seat exists**
(alive or dead) and the advisory has a non-null `goodWins`:

- If `goodWins == false` and the Politician is **good**:
  prompt **"Was <Name> (Politician) the player most responsible for good losing?"**
  options `["No — they lose with good", "Yes — they turn EVIL and win too"]`, default 0.
- If `goodWins == true` and the Politician is **evil** (Pit-Hag / Storyteller change):
  prompt **"Was <Name> (Politician) the player most responsible for evil losing?"**
  options `["No — they lose with evil", "Yes — they turn GOOD and win too"]`, default 0.
- If the Politician's team **won**, emit no question.

Alongside the prompt, show the decision aids the Storyteller needs and currently has to
remember:
- **"Bar: they must have been very influential. Spreading false info alone is not
  enough — if the group's own decision caused the loss, the Politician loses."** (wiki)
- **Impairment banner** when the impairment log (below) shows the Politician was drunk or
  poisoned during the relevant window: **"<Name> was poisoned by the Poisoner on days
  2-3 — a drunk/poisoned Politician cannot change alignment."**
- A compact replay of the Politician's recorded claims/actions (claims ledger, below),
  plus their nomination and vote record, which the app already stores
  (`GameState.nominations`, `Nomination.voterIds`, `engine/.../GameState.kt:63-72`).

Selecting "Yes" performs `GameActions.flipAlignment(politicianId)` **and** records
`politicianFlipped = true` so the reveal can explain it.

### Reveal changes

`RevealSheet` (`app/.../screens/GameExtras.kt:270-349`) must show a per-player result,
not just a team banner:
- Banner unchanged (GOOD WINS / EVIL WINS).
- Each row gains a small "won" / "lost" tag derived from
  `playerWins(state, goodWins) = (player.isEvil(lookup) != goodWins)`.
- The Politician row, when flipped, reads:
  **"Politician · turned EVIL at the end — wins with evil"** (or GOOD), with the original
  alignment shown struck through so the table understands what happened.

### Claims ledger (cross-cutting, also serves Gossip/Juggler/Savant/Slayer/Mutant)

Add to `GameState`:

```kotlin
@Serializable
data class PublicClaim(
    val id: Long,
    val cycle: Int,
    val phase: Phase,
    val playerId: Long?,             // who said it (null = the group)
    val text: String,
    val tags: List<String> = emptyList(),   // "gossip", "juggler", "slayer", "politician"…
)
// GameState gains: val claims: List<PublicClaim> = emptyList()
```

Day screen gets a persistent one-line "record a claim" field with a seat chip; each claim
is timestamped with `cycle`. The Politician end-game question filters
`claims.filter { it.playerId == politicianId }` into its decision aid. This is the same
store the Gossip fix needs, so build it once.

### Impairment log (cross-cutting)

Add:

```kotlin
@Serializable
data class ImpairmentSpan(val playerId: Long, val fromCycle: Int, val fromPhase: Phase,
                          val toCycle: Int? = null, val source: String = "")
// GameState gains: val impairments: List<ImpairmentSpan> = emptyList()
```

Appended whenever a drunk/poison reminder is placed or expires (`addReminder`,
`placeExclusiveReminder`, `clearEphemeral` in `engine/.../GameActions.kt:186-251`). The
Politician question, the Mathematician, the Puzzlemaster guess and the Plague Doctor
death check all read it.

### Guard against early alignment flips

In the seat sheet, flipping alignment on a `politician` seat while `phase != null` and no
end-game question is open must warn:
**"The Politician only changes alignment when the game ends. Flip now only if another
ability (Pit-Hag, Storyteller fiat) genuinely changed their alignment."**

### Data changes

`night_and_jinxes.json` — add:
```
boffin/politician  "The Demon can not have the Politician ability."
legion/politician  "The Politician might register as evil to Legion."          (lower confidence)
vizier/politician  "The Politician might register as evil to the Vizier."      (lower confidence)
```
`characters.json:1702-1712` — no change needed; ability text is current.
`night_guide.json` — no night entry needed. Optionally add a **day**/end-game guide
channel (new key, e.g. `"end"`) carrying the How-to-Run text so the end-game dialog can
render the official wording verbatim.

### UI text

- End-game question title: **"Politician"**
- Prompt: **"Was <Name> the player most responsible for good losing?"**
- Helper: **"Only if they were very influential. Spreading false info isn't enough — if
  the group's decision lost the game, the Politician loses with them."**
- Option labels: **"No — loses with good"** / **"Yes — turns evil and wins too"**
- Reveal tag: **"turned evil at the end — wins"**

## Tests to add

1. *Given* a game with a good `politician` seat and every Demon alive at 2 players left
   (evil wins advisory), *then* `WinCheck.check(...).questions` contains a
   `politician` question with the "turn evil" option.
2. *Given* the same board but with the Politician's team **winning** (all Demons dead),
   *then* no Politician question is emitted.
3. *Given* an evil-flipped `politician` seat and a good-wins advisory, *then* the
   question offers "turns good and wins too".
4. *Given* a Politician carrying a `poisoner:Poisoned` reminder, *then* the question's
   impairment banner is present and the default option is "No".
5. *Given* answering "Yes", *then* `player.isEvil` becomes true and `RevealSheet`'s
   per-player result marks the Politician as a winner while the banner still says
   GOOD WINS.
6. *Given* a `politician` + `boffin` script, *then* the active-jinx lookup returns
   "The Demon can not have the Politician ability."
7. *Given* claims recorded on days 1-3 attributed to the Politician, *then* the end-game
   question surfaces exactly those claims in cycle order.
8. *Given* a Politician flipped on day 2 by hand, *then* an `Empath` adjacent to them is
   **not** silently told they have an evil neighbour without a warning being raised
   (regression guard for defect 8).
