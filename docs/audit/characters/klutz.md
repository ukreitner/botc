# Klutz (klutz) — Sects & Violets Outsider

## Official rules (sources)

Source: https://wiki.bloodontheclocktower.com/Klutz (Character Text, How to Run,
Examples, Tips & Tricks).

Current ability text (verbatim):

> "When you learn that you died, publicly choose 1 alive player: if they are
> evil, your team loses."

How to Run (wiki):

- "When declared dead, the Klutz must announce their role and point at any
  player."
- "If they choose an evil player, the game ends immediately with evil winning.
  If they choose a good player, play continues."
- "The Storyteller should give them time to decide and may privately remind them
  if they don't understand the mechanic."

Edge cases and clarifications:

- **The Klutz's *own* team loses, not "good".** Wiki: "If an evil Klutz chooses
  an evil player, the good team wins instead." (A Klutz can be evil via
  Bounty Hunter/Pit-Hag/Barber-created evil alignment situations, or by being a
  good-aligned player who became evil.)
- **The chosen player must be alive.** ("publicly choose 1 **alive** player")
- **The choice is public** — announced at the table, not whispered to the
  storyteller. This is what makes a deliberate Klutz death a good-team
  information play.
- **Timing is "when you *learn* that you died"**, not "when you die":
  - executed during the day → they learn immediately, so they choose there and
    then, before the day ends;
  - killed at night → they learn at dawn when deaths are announced, so they
    choose at the start of the day;
  - a Klutz who is already dead and *becomes* the Klutz never triggers (they did
    not "learn that they died" as the Klutz).
- **No night action.** The Klutz is correctly absent from both night orders.
- **Jinxes:** none.
- **Uncertain:** the wiki's *player* tips claim "Klutz isn't affected by Vortox
  or poisoning". Vortox is clearly irrelevant (the Klutz receives no
  information). Poisoning/drunkenness is not addressed in How to Run; the
  general rule is that a drunk or poisoned player has no ability, so a
  drunk/poisoned Klutz who points at an evil player causes nothing. The
  storyteller should still let them choose publicly so the impairment is not
  revealed. **Flagging this rather than asserting it** — worth a confirmation
  pass before implementing the automatic loss.

## What the app does today

**The Klutz does not exist in the application beyond its dictionary entry.**

- `engine/src/main/resources/botc/data/characters.json:974-984` — id, name,
  team, ability text (matches the wiki), empty `firstNightReminder`,
  `otherNightReminder`, `reminders`, `remindersGlobal`.
- `engine/src/main/resources/botc/data/night_and_jinxes.json` — correctly absent
  from both order lists. **Works.**
- `engine/src/main/resources/botc/data/night_guide.json` — **no entry** (there is
  no night step, but there is also no day-phase guidance anywhere in the app).
- `engine/src/main/kotlin/com/clocktower/engine/StatusEffects.kt:94-103` — the
  `when (id)` block that produces on-death triggers has branches for
  `ravenkeeper`, `sage`, `farmer`, `moonchild`, `sweetheart`, `barber`,
  `poppygrower`, `king`. **No `klutz` branch.**
- `engine/src/main/kotlin/com/clocktower/engine/WinCheck.kt:18-101` — no Klutz
  branch. The only endings modelled are executed Saint, all Demons dead,
  ≤2 alive with a Demon, and the Mastermind day.
- No hit anywhere under `app/src` (only a test uses "Klutz" incidentally).

Storyteller experience: the Klutz is killed; the app records a death and says
nothing. The storyteller must remember unaided (a) that the Klutz ability
exists, (b) that the player must be prompted to choose publicly, (c) that the
choice must be an *alive* player, (d) that an evil pick ends the game against
the Klutz's own team, and (e) to actually end the game. Nothing is recorded.

## Defects and gaps

1. **P0** · A game-ending ability fires with zero storyteller support · The
   rules require the Klutz to be prompted the moment they learn they died and
   for the game to end immediately if they point at an evil player. The app
   produces no prompt, no note, no record and no ending. ·
   `StatusEffects.kt:94-103` (missing branch), `WinCheck.kt:18-101` (missing
   branch) · Repro: kill the Klutz from any surface; observe nothing.

2. **P0** · No death note when the Klutz dies · `deathNotes` is the app's
   established channel for on-death triggers (Sweetheart, Barber, Moonchild,
   Ravenkeeper all have one) and the Klutz is missing from it, so even the seat
   sheet — the one place notes appear — is silent. ·
   `StatusEffects.kt:94-103` · Repro: open the Klutz's seat sheet while alive;
   no note is listed among the death consequences.

3. **P0** · Executing the Klutz surfaces nothing even if a note existed · The
   Day tab's Execute buttons (`DayScreen.kt:111-114`, `:350-357`) and the dusk
   guard (`GameShell.kt:599-604`) call `viewModel.kill(...)` without consulting
   `StatusEffects.deathNotes`. Execution is a very common Klutz death. · Repro:
   put the Klutz on the block, tap Execute in the Day tab.

4. **P1** · No dawn/day-start briefing, so a night-killed Klutz is silently
   forgotten · "When you learn that you died" means the choice happens at the
   start of the day. The app has no day-start briefing at all; the DAWN night
   row is static text ("Wait a few seconds. Everyone opens their eyes. Announce
   who died.", `NightOrder.kt:59`) that does not even name the dead. ·
   Repro: Demon-kill the Klutz at night 2, advance to dawn — nothing mentions
   the Klutz.

5. **P1** · No way to record the public choice · The Klutz's pick is a public
   day-time statement with a game-ending consequence; the app has no structure
   for it (only free-text `storytellerNotes` / per-seat `note`). ·
   `GameState.kt:94-115` has no field for day-time declarations · Repro: try to
   record "Klutz pointed at Dax" anywhere structured.

6. **P1** · The "alive player" constraint is unenforced and unstated · Nothing
   tells the storyteller the target must be alive, and nothing offers a filtered
   picker.

7. **P2** · The evil-Klutz inversion is not modelled · "your team loses" is
   relative to the Klutz's alignment, so an evil Klutz picking evil makes **good
   win**. Even if a manual "declare victory" is used, nothing computes which way.
   `Player.isEvil(lookup)` (`GameState.kt:49-52`) already has the information
   needed. · Repro: flip the Klutz's alignment, kill them — nothing changes.

8. **P2** · Impairment at death is captured but unused ·
   `DeathRecord.abilityImpairedAtDeath` (`GameState.kt:87`) is recorded by
   `GameActions.kill` (`GameActions.kt:153`) and would exactly answer "does this
   Klutz choice do anything?", but no code reads it for the Klutz.

9. **P3** · No spent/used token in the data · `characters.json:980-982` has an
   empty `reminders` list, so there is no way to mark "Klutz has chosen" in the
   grimoire. The official token set has no Klutz reminder either, so a generic
   `Used` token (already in `SeatSheet.kt:502`) is the fallback — but the app
   should place it automatically.

10. **P3** · No entry in `night_guide.json` · Correct that there is no *night*
    step, but the app's guide system is the only place per-character
    storyteller prose lives, so day-triggered Outsiders (Klutz, Mutant) have no
    home for their run-book at all — an architectural gap, see below.

## Proposed behaviour (spec)

The Klutz has **no night action**. Everything here is on-death and day-time.

### State the engine needs

Add a structured record of day-time public declarations to `GameState` — this
is shared with Mutant, Gossip, Juggler, Savant, Artist, Fisherman, Slayer:

```
@Serializable data class PublicClaim(
    val day: Int,
    val sourcePlayerId: Long,
    val kind: String,          // "klutz-choice", "gossip", "slayer", ...
    val targetPlayerId: Long? = null,
    val text: String = "",
    val resolved: Boolean = false,
)
```
`GameState.publicClaims: List<PublicClaim> = emptyList()`.

### Trigger

- when: **on death**, any cause, any phase. Wake condition: n/a.
- The engine raises a **pending obligation** `klutz-choice` for that seat as soon
  as the death is recorded, with a "reveal moment":
  - `phase == DAY` (execution, Witch curse, Mutant execution, Slayer): reveal
    moment is **now**;
  - `phase == NIGHT`: reveal moment is **the next day start**, and the
    obligation is carried into the day-start briefing.
- Do **not** raise the obligation if the seat only became the Klutz after dying.

### Immediate effects

- Place a generic `Used`-style marker only once resolved; place nothing at the
  moment of death (the official token set has no Klutz reminder).
- Show a blocking-but-dismissible prompt with the storyteller-voice text below
  and a picker of **alive** players (all seats, travellers included, excluding
  the Klutz themself — they are dead, so they are already excluded).

### Resolution

On picking a target `T`:

- record `PublicClaim(day, klutzId, "klutz-choice", T, resolved = true)`;
- let `klutzEvil = klutzPlayer.isEvil(lookup)` and
  `targetEvil = T.isEvil(lookup)` **at the moment of the choice**;
- let `worked = !deathRecord.abilityImpairedAtDeath` (see the flagged
  uncertainty above — make this a single named predicate so it is easy to change);
- if `worked && targetEvil` → raise a `WinCheck.Advisory` with
  `goodWins = klutzEvil` and reason
  `"<Klutz name> the Klutz pointed at <T> (evil) — the Klutz's own team
  (<good|evil>) loses."`, cautions listing any drunk/poison marker on the Klutz;
- if `targetEvil && !worked` → do **not** end the game; add a storyteller-only
  line "The Klutz was drunk/poisoned when they died — the choice has no effect.
  Say nothing.";
- otherwise → record and continue, and add a day-log line
  `"Day N: Klutz chose <T> — game continues"` (which is itself public
  information the table can use).
- Offer a "They declined / no valid choice" escape that just records the
  obligation as resolved.

### Deferred effects / expiry

- The obligation persists across phase changes until resolved; it must survive
  `advancePhase` and be re-surfaced at each day start until the storyteller
  clears it. It never expires on its own.

### Information / visibility

- Nothing is shown privately. The Klutz says it out loud. The app's only job is
  to prompt, constrain, record and resolve.

### Day-time inputs the app must let the storyteller record

- The Klutz's chosen player (structured, as above), and the fact that the choice
  has happened, so the ST does not re-prompt.

### Interactions / jinxes

- **None jinxed.** But note explicitly in the guide:
  - Vortox does **not** affect the Klutz (no information involved).
  - Sweetheart/Poisoner/No Dashii drunkness at the moment of death does.
  - A Klutz killed by the **Witch** curse (nominating while cursed) dies during
    the day and chooses immediately, mid-nomination — the nomination still
    proceeds (see `witch.md`).
  - **Zombuul / Vigormortis / "registers as dead"**: the Klutz triggers when
    they *learn* they died, so a player who registers as dead but is not does
    not trigger.

### UI text

Prompt title: `<name> the Klutz died`
Body: `They must now publicly announce the Klutz and point at one ALIVE player.
If that player is evil, the Klutz's team loses immediately.`
Picker label: `Who did they point at?`
Buttons: `<T> — evil, <good|evil> loses` (destructive styling) /
`<T> — good, play continues` / `Skip for now`

### Data changes

- `night_guide.json` — add a `klutz` entry under a new `day` key (see
  architecture note) with the How-to-Run prose above. Requires extending
  `NightGuideEntry` (`NightGuide.kt:36-40`) with `val day: GuideNight? = null`,
  or a sibling `day_guide.json`.
- `characters.json:974-984` — no ability-text drift; leave as is.

### Architectural note (cross-cutting)

Klutz and Mutant are both **day-triggered** characters with no night row, and
the app's entire per-character guidance system is keyed off the night sheet.
Until there is a "Day" run-book surface (a day-start briefing list plus
on-death obligations), these characters are structurally invisible no matter how
good the data is.

## Tests to add

1. **Given** a Klutz killed by the Demon at night 2, **when** `advancePhase`
   moves to day 2, **then** the day-start briefing contains an unresolved
   `klutz-choice` obligation for that seat.
2. **Given** a Klutz executed on day 3, **then** the obligation is raised
   immediately during day 3 (not deferred to day 4).
3. **Given** an unresolved Klutz obligation, **when** the storyteller advances
   two more phases, **then** the obligation is still present.
4. **Given** a good Klutz who chose an evil player and was not impaired at
   death, **then** `WinCheck.check` returns `Advisory(goodWins = false, ...)`
   naming the Klutz.
5. **Given** an **evil** Klutz who chose an evil player, **then**
   `WinCheck.check` returns `Advisory(goodWins = true, ...)`.
6. **Given** a Klutz who chose a good player, **then** `WinCheck.check` returns
   no Klutz advisory and the choice is recorded in `publicClaims`.
7. **Given** a Klutz carrying `sweetheart:Drunk` at the moment of death who then
   chooses an evil player, **then** no advisory is raised and a
   storyteller-only "no effect" note is produced.
8. **Given** a dead player who is turned into the Klutz by the Pit-Hag, **then**
   no `klutz-choice` obligation is raised.
9. **Given** the Klutz choice picker, **then** dead players are not selectable
   and the Klutz's own seat is not offered.
10. **Given** `StatusEffects.deathNotes` for a living Klutz, **then** the result
    contains a line naming the Klutz's on-death public choice.
