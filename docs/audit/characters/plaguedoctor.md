# Plague Doctor (plaguedoctor) — Experimental Outsider

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Plague_Doctor> (fetched 2026-08-25),
jinx list cross-checked at <https://botc.me/character/plaguedoctor>.

Current ability text (wiki): **"When the Plague Doctor dies, the Storyteller gains a
Minion ability."** (`characters.json` phrases it in the second person: "When you die,
the Storyteller gains a Minion ability." — same rule, no drift.)

How to Run (wiki bullets):
- "Place a Minion character token in the center of the left side of the Grimoire."
- "Mark it with the Plague Doctor's **STORYTELLER ABILITY** reminder." — alternatively
  mark an *in-play* Minion with this reminder.
- "Add a night token to the night sheet if applicable."  ← **the gained ability runs at
  that Minion's own position in the night order, not at the Plague Doctor's position.**
- "When the Minion acts, the Storyteller makes relevant choices."

Timing and edge cases the wiki states:
- The ability is gained **the moment the Plague Doctor dies** (execution, Demon kill,
  any cause), not at the following night. It then "is in effect for the rest of the game."
- **If the Plague Doctor is drunk or poisoned when they die, the Storyteller gains no
  ability, even if the Plague Doctor is later cured.** (Wiki example: "Plague Doctor
  drunk by Minstrel after death; Storyteller retains Organ Grinder ability gained at
  death" — i.e. impairment is judged at the instant of death.)
- The Storyteller does **not** become evil, does not become a player, gains no vote, and
  cannot be killed or poisoned in respect of that ability.
- The Storyteller does not announce which ability was gained. Wiki example: "The Plague
  Doctor dies; Storyteller gains Poisoner ability and poisons nightly."
- Wiki example: "Executed Plague Doctor gives Storyteller Cerenovus ability; later
  Pit-Hag creates second Cerenovus" — a Minion ability held by the Storyteller does not
  block the same character existing as a real player.
- Tips (storyteller-facing): assume good players will deduce the ability from its
  effects; the Plague Doctor is expected to reveal on death, so the ability's presence
  becomes public knowledge while its identity does not.

Jinxes (nine, per botc.me; only two exist in this repo — see Defect 6):
1. **Spy** — "If the Storyteller would gain the Spy ability, a Minion gains it, and learns this."
2. **Fearmonger** — "If the Storyteller would gain the Fearmonger ability, a Minion gains it, and learns this."
3. **Wraith** — "If the Storyteller would gain the Wraith ability, a Minion gains it, and learns this."
4. **Goblin** — "If the Storyteller would gain the Goblin ability, a Minion gains it, and learns this."
5. **Scarlet Woman** — "If the Storyteller would gain the Scarlet Woman ability, a Minion gains it, and learns this."
6. **Marionette** — "If the Storyteller would gain the Marionette ability, one of the Demon's good neighbors becomes the Marionette."
7. **Evil Twin** — "If the Storyteller would gain the Evil Twin ability, a player becomes the Evil Twin."
8. **Boomdandy** — "If the Storyteller would gain the Boomdandy ability, a player becomes the Boomdandy."
9. **Baron** — "If the Storyteller would gain the Baron ability, up to two players become Outsiders."

Uncertain (say so rather than guess): the wiki does not state what happens if a Plague
Doctor is resurrected (Professor / Bone Collector) and dies a **second** time. Reading
"when you die" literally, each death triggers again; the physical token set implies one.
Recommend prompting the Storyteller again and letting them decline.

## What the app does today

Data:
- `engine/src/main/resources/botc/data/characters.json:1688-1700` — id/name/team/ability
  as above; `reminders: ["Storyteller Ability"]`; `otherNightReminder`: "If the Plague
  Doctor died, the Storyteller gained a Minion ability. If you haven't done this yet, do
  so now."
- `engine/src/main/resources/botc/data/night_and_jinxes.json:377` — `plaguedoctor` sits
  at index 4 of `otherNight` (after DUSK / barista / bureaucrat / thief). Not on
  `firstNight`.
- `engine/src/main/resources/botc/data/night_guide.json:1296-1301` — an *other*-night
  entry only, with correct prose ("Only act if the Plague Doctor has died and you have
  not yet gained a Minion ability… If the Plague Doctor was drunk or poisoned when they
  died, you gain no ability.") and **no** show cards.
- `engine/src/main/resources/botc/data/night_and_jinxes.json:263-273` — exactly two
  jinxes, both with non-official wording:
  - `plaguedoctor`/`baron`: "If the Storyteller gains the Baron ability, up to 2
    Townsfolk players become not-in-play Outsiders."
  - `plaguedoctor`/`scarletwoman`: "If the Demon dies while the Storyteller has the
    Scarlet Woman ability, a living Minion becomes the Demon."

Code: **there is no Kotlin path anywhere that mentions `plaguedoctor`.** The character is
handled entirely by the generic machinery:
- `engine/.../NightOrder.kt:142-178` builds one other-night row whose `playerIds` are the
  seats holding `plaguedoctor` — alive or dead — and whose `detail` is the
  `otherNightReminder` string.
- `app/.../screens/NightScreen.kt:690-765` renders that row. `allDead` is computed at
  line 702 and, at lines 751-757, prints in red: **"All holders are dead — usually
  skip."** — i.e. exactly when the Storyteller must act.
- `app/.../screens/NightScreen.kt:462-532` (`QuickResolutions`) has resolvers only for
  `snakecharmer`, `fanggu`, `professor`; the Plague Doctor row therefore offers no tools.
- `app/.../screens/NightScreen.kt:193-306` (`NightToolTray`) does offer the "Storyteller
  Ability" reminder chip, but it can only be dropped **on a seat** — there is no
  off-circle / "centre of the grimoire" slot.
- `engine/.../StatusEffects.kt:52-129` (`deathNotes`) has a `when (id)` block of
  on-death triggers (ravenkeeper, sage, farmer, moonchild, sweetheart, barber,
  poppygrower, king) — `plaguedoctor` is **absent**, so killing the Plague Doctor from
  the seat sheet says nothing.
- `engine/.../GameData.kt:23-26` (`activeJinxes`) returns only jinxes where **both** ids
  are assigned to seats; the app surfaces them through
  `app/.../screens/GameExtras.kt:200-220` ("Jinxes in play").
- `engine/.../GameActions.kt:136-156` (`kill`) already snapshots
  `abilityImpairedAtDeath` into the `DeathRecord` — the exact fact the Plague Doctor
  needs — but nothing reads it for this character.

Storyteller experience today: every night from night 2 on, a row titled "Plague Doctor"
appears with the seat's name next to it. While the Plague Doctor is alive the row is
noise that must still be ticked (the Dawn button refuses to advance while any step is
unticked — `app/.../screens/GameShell.kt:147-161`). The moment the Plague Doctor dies the
row starts telling the Storyteller to skip it. Choosing the ability, remembering it,
remembering that it must be run at the correct point in the night order, and remembering
the nine jinxes are all 100% manual.

## Defects and gaps

1. **P0 · The night row tells the Storyteller to skip the step precisely when it must be
   run.** `allDead` (`app/.../screens/NightScreen.kt:702`) renders "All holders are dead —
   usually skip." (`:751-757`) for any step whose holders are all dead. The Plague Doctor
   only ever *does* anything once dead. Repro: assign a Plague Doctor, execute them on
   day 1, advance to night 2, look at the Plague Doctor row — red "usually skip" text.
2. **P0 · The gained Minion ability never enters the night order.** The wiki's How to Run
   says to add the Minion's night token to the sheet. `NightOrder.build`
   (`engine/.../NightOrder.kt:40-209`) only emits steps for ids held by a seat
   (`inPlay`), plus Fabled, plus homebrew. A Storyteller-held Poisoner/Witch/Cerenovus/
   Pit-Hag/Devil's Advocate/Assassin/Godfather/Organ Grinder/Harpy/Widow/Mezepheles/
   Summoner/Boffin therefore never gets a row, so its nightly choice, its reminder tokens
   and its dawn/dusk expiry are all off-book. Repro: kill the Plague Doctor, decide you
   have the Poisoner ability, advance to night — no Poisoner row exists.
3. **P0 · Impairment at death is not applied.** The rule "no ability if drunk/poisoned
   when they died" is only prose inside `night_guide.json:1298`. The engine already has
   `DeathRecord.abilityImpairedAtDeath` (`engine/.../GameState.kt:87`, written at
   `GameActions.kt:153`) and never consults it. A Storyteller who poisoned the Plague
   Doctor and then killed them will not be told they gain nothing.
4. **P1 · No on-death prompt.** `StatusEffects.deathNotes` (`engine/.../StatusEffects.kt:94-103`)
   has no `plaguedoctor` branch, so executing the Plague Doctor on day 2 gives no
   "choose a Minion ability now" prompt. The ability is gained *at death*, which matters
   immediately for e.g. a gained Godfather ("an Outsider died today → kill tonight") and
   Scarlet Woman. Repro: seat sheet → kill by execution → no notes.
5. **P1 · Nothing records which ability was gained.** There is no state field; the
   Storyteller must keep it in their head or in free-text `storytellerNotes`
   (`engine/.../GameState.kt:112`). Nothing prevents gaining a second ability by mistake,
   nothing shows it in the grimoire, and the end-game reveal
   (`app/.../screens/GameExtras.kt:270-349`) never mentions it.
6. **P1 · Seven of the nine jinxes are missing, and the two present can never fire.**
   `night_and_jinxes.json:263-273` has only Baron and Scarlet Woman, both with wording
   that differs from the official jinx. Worse: `GameData.activeJinxes`
   (`engine/.../GameData.kt:23-26`) requires *both* characters to be **in play**, but
   every Plague Doctor jinx is about a Minion that is **not** in play (that is the whole
   point of the ability). So "Jinxes in play" will never show them. Repro: Plague Doctor
   + any script containing the Spy → menu → "Jinxes in play" → nothing.
7. **P1 · The "Storyteller Ability" token has nowhere to live.** `NightToolTray`
   (`app/.../screens/NightScreen.kt:283-306`) and `ReminderPicker`
   (`app/.../screens/SeatSheet.kt:492-570`) can only attach a `PlacedReminder` to a
   player seat (`Player.reminders`, `engine/.../GameState.kt:30`). The wiki puts this
   token on a Minion token in the centre of the grimoire — an entity the data model has
   no room for.
8. **P2 · The row is compulsory noise while the Plague Doctor lives.** The Dawn guard
   (`app/.../screens/GameShell.kt:147-161`) blocks the phase change until every step is
   ticked, so the Storyteller ticks a do-nothing Plague Doctor row every night.
9. **P2 · Not on the first-night order and no first-night guide entry.**
   `night_and_jinxes.json` has no `plaguedoctor` in `firstNight`, and
   `night_guide.json:1296` has only an `other` entry. If the Plague Doctor dies during
   night 1 (Kazali-style setups, a Storyteller-adjudicated death, or a Boffin-granted
   ability), the ability must still be gained and its Minion step may fall *later that
   same night* — the app cannot express that.
10. **P2 · The gained ability's setup-time variants are unhandled.** Baron (Outsider
    count), Marionette (a good neighbour of the Demon becomes the Marionette),
    Evil Twin / Boomdandy (a player *becomes* that character) all mutate the grimoire
    mid-game. `GameActions.assignCharacter` (`engine/.../GameActions.kt:46-53`) can do it
    but the Storyteller must know to.
11. **P3 · No show cards.** `night_guide.json:1296-1301` has `"shows": []`. The jinxed
    cases ("a Minion gains it, **and learns this**") require showing that Minion a
    character token — a `ShowCard.CharacterCard` would do it in one tap.

## Proposed behaviour (spec)

### New engine state

```kotlin
// GameState.kt
@Serializable
data class StorytellerAbility(
    val characterId: String,      // the Minion whose ability the ST holds
    val gainedOnCycle: Int,
    val gainedFrom: String = "plaguedoctor",
    /** Set when a jinx redirected the ability to a real player instead. */
    val redirectedToPlayerId: Long? = null,
)
// GameState gains: val storytellerAbilities: List<StorytellerAbility> = emptyList()
```

### Night step

- **when:** other nights (keep index 4 of `otherNight`); **also** insert on first night
  immediately after `DAWN`-preceding steps if the Plague Doctor died during night 1.
- **wake condition:** show the row **only if** the Plague Doctor is dead **and**
  `state.storytellerAbilities.none { it.gainedFrom == "plaguedoctor" }`. Otherwise emit
  no row at all (do not make the Storyteller tick a dead step).
- **targets:** none — the "target" is a *character* pick, not a seat.
- **immediate effects:** none by itself; this row is only the safety net for a death the
  Storyteller resolved without the picker.
- **information:** none is given to any player.
- **UI text (row detail):** `"<Name> is dead and you have not taken a Minion ability yet
  — choose one now."`

### On-death flow (the primary path)

Add to `StatusEffects.deathNotes` a `plaguedoctor` branch, and to the seat-sheet kill
flow an actionable prompt:

- If `abilityImpairedAtDeath == true` →
  `"Plague Doctor died drunk/poisoned — you gain NO Minion ability (not even if cured later)."`
  (read the snapshot, do not re-evaluate `isImpaired` after the fact).
- Else → `"Plague Doctor died — you gain a Minion ability now. [Choose ability…]"`, opening
  a **Storyteller Ability picker**.

### Storyteller Ability picker

- Lists every Minion on the current script, **not-in-play first**, then in-play Minions
  (both are legal), each with its ability text.
- Each entry carries an inline jinx banner where one applies:

  | Picked | Banner + required action |
  |---|---|
  | Spy | "Jinx: a **Minion** gains the Spy ability instead, and learns this." → pick a Minion seat, set `redirectedToPlayerId`, offer a `CharacterCard("YOU NOW HAVE THIS ABILITY", "spy")` show card |
  | Fearmonger | same pattern (`fearmonger`) |
  | Wraith | same pattern (`wraith`) |
  | Goblin | same pattern (`goblin`) |
  | Scarlet Woman | same pattern (`scarletwoman`) |
  | Marionette | "Jinx: one of the Demon's **good neighbours becomes the Marionette**." → pick between the Demon's two good neighbours, then run the normal Marionette conversion (`assignCharacter` + `setShownCharacter` + Demon is told) |
  | Evil Twin | "Jinx: a **player becomes the Evil Twin**." → pick the player, assign the character, run the Evil Twin's night info |
  | Boomdandy | "Jinx: a **player becomes the Boomdandy**." → pick the player, assign |
  | Baron | "Jinx: up to **two players become Outsiders**." → pick 0-2 seats and their new Outsider characters |

- On confirm: append to `storytellerAbilities`, and place `PlacedReminder("plaguedoctor",
  "Storyteller Ability")` on the **Storyteller slot** (see below) or, for the redirected
  jinxes, on the receiving Minion's seat.
- Then show a one-line summary in the grimoire header: `ST ability: Poisoner`.

### The Storyteller slot (data-model change)

Add an off-circle holder so the wiki's "centre of the left side of the Grimoire" is
representable:

```kotlin
// GameState
val storytellerReminders: List<PlacedReminder> = emptyList()
```

Render it in `GrimoireScreen` in the free centre area (next to the existing bluffs
cluster at `app/.../screens/GrimoireScreen.kt:175-197`), showing the gained Minion token
with the "Storyteller Ability" reminder on it. It must be tappable to open the ability's
night tools.

### Deferred effects — running the gained ability

`NightOrder.build` must treat `storytellerAbilities` as pseudo-holders:

- For each `StorytellerAbility` with `redirectedToPlayerId == null`, emit a step at that
  Minion's **own** position in `firstNight`/`otherNight`, with
  `playerIds = emptyList()`, `title = "<Minion> (Storyteller)"`, and
  `detail = <that character's night reminder> + " — you hold this ability; make the choice yourself."`
- The step must get the same `QuickResolutions`/`NightToolTray`/`InfoCalc` treatment as a
  real holder, with the "holder" being the Storyteller. Reminder tokens it places
  (`poisoner:Poisoned`, `cerenovus:Mad`, `devilsadvocate:Survives execution`, …) still go
  on seats and still expire through the existing tables
  (`engine/.../GameActions.kt:218-242`) — no change needed there.
- For `redirectedToPlayerId != null` the ability belongs to a real seat; no pseudo-step —
  the normal machinery already covers it once the character is assigned.
- Non-night Minion abilities (Baron, Mastermind, Psychopath, Marionette, Boomdandy,
  Scarlet Woman, Evil Twin) emit **no** night step; instead they register triggers:
  - Scarlet Woman (only reachable if the jinx put it on a real Minion) — already handled
    by `StatusEffects.deathNotes:104-109`.
  - Mastermind — the existing `mastermindDayActive` machinery
    (`engine/.../WinCheck.kt:28-49`) must also fire when the *Storyteller* holds it: change
    the `"mastermind" in inPlayIds` test at `WinCheck.kt:75-77` to
    `inPlayIds + storytellerAbilities.map{it.characterId}`.
  - Psychopath / Boomdandy — day-phase prompts.
- **Every** `inPlayIds`-style membership test in the engine should consult the same
  helper: `GameState.effectiveInPlayIds` = seat character ids **+** Storyteller ability
  ids. Call sites today: `WinCheck.kt:24`, `GameData.activeJinxes` callers,
  `GameActions.validateBag` (must **not** include them — bag validation stays seat-only).

### Expiry

- `plaguedoctor:Storyteller Ability` never expires. Do **not** add it to
  `EXPIRES_AT_DAWN`/`EXPIRES_AT_DUSK`.
- If the Plague Doctor is resurrected, the ability **stays**. If they then die again,
  re-prompt (with a "you already hold Poisoner — take a second ability?" confirmation);
  this is flagged as a rules-uncertain case, so make it a Storyteller choice, not
  automatic.

### Visibility

- No player is ever told the Storyteller gained an ability, nor which one.
- **Exception:** the five "a Minion gains it, and learns this" jinxes — that Minion must
  be woken and shown the character token. Add these show cards to `night_guide.json`.
- The Plague Doctor player themselves learns nothing.

### Jinx data changes (`night_and_jinxes.json`)

Replace the two existing entries and add seven, using the official wording:

```
plaguedoctor/spy          "If the Storyteller would gain the Spy ability, a Minion gains it, and learns this."
plaguedoctor/fearmonger   "If the Storyteller would gain the Fearmonger ability, a Minion gains it, and learns this."
plaguedoctor/wraith       "If the Storyteller would gain the Wraith ability, a Minion gains it, and learns this."
plaguedoctor/goblin       "If the Storyteller would gain the Goblin ability, a Minion gains it, and learns this."
plaguedoctor/scarletwoman "If the Storyteller would gain the Scarlet Woman ability, a Minion gains it, and learns this."
plaguedoctor/marionette   "If the Storyteller would gain the Marionette ability, one of the Demon's good neighbours becomes the Marionette."
plaguedoctor/eviltwin     "If the Storyteller would gain the Evil Twin ability, a player becomes the Evil Twin."
plaguedoctor/boomdandy    "If the Storyteller would gain the Boomdandy ability, a player becomes the Boomdandy."
plaguedoctor/baron        "If the Storyteller would gain the Baron ability, up to two players become Outsiders."
```

and change `GameData.activeJinxes` (`engine/.../GameData.kt:23-26`) so a jinx also
matches when one side is a **script** character and the other is the Plague Doctor —
i.e. add `fun potentialJinxes(inPlayIds, scriptIds)`. Simplest correct rule: jinxes
involving `plaguedoctor` match on `plaguedoctor in play && other in script`.

### `night_guide.json` changes

- Keep the existing `other` prose but retarget it to the new conditional row.
- Add `"first"` with the same text (for a night-1 Plague Doctor death).
- Add shows:
  - `{"label":"Minion learns the ability","kind":"token","token":"pick","text":"YOU NOW HAVE THIS ABILITY"}`
  - `{"label":"You gain nothing (impaired)","kind":"message","text":"PLAGUE DOCTOR DIED IMPAIRED — NO ABILITY"}` (Storyteller-facing, never shown to a player)

### UI text the step should display

- Row title while alive: *(no row)*
- Row title once dead and unresolved: **"Plague Doctor — take a Minion ability"**
- Detail: **"<Name> is dead. Choose the Minion ability you now hold. It runs at that
  Minion's place in the night order for the rest of the game."**
- Detail when impaired at death: **"<Name> died drunk/poisoned — you gain nothing."**
- Grimoire header chip: **"ST ability: <Minion>"**
- Reveal sheet line: **"Storyteller held the <Minion> ability from night <n>."**

## Tests to add

1. *Given* a Plague Doctor seat that is alive, *when* the other-night sheet is built,
   *then* no `plaguedoctor` step is present.
2. *Given* a Plague Doctor killed by execution on day 1 while **not** impaired, *when*
   the other-night sheet is built for night 2, *then* a `plaguedoctor` step is present
   and its detail asks the Storyteller to choose a Minion ability.
3. *Given* a Plague Doctor killed while holding a `poisoner:Poisoned` reminder, *then*
   `deathNotes` contains "gain NO Minion ability" and the night sheet contains **no**
   `plaguedoctor` step (nothing to resolve).
4. *Given* `storytellerAbilities = [StorytellerAbility("poisoner", 2)]`, *when* the
   other-night sheet is built, *then* a step with id `poisoner` exists, its `playerIds`
   is empty, its title marks it as Storyteller-held, and it sits at the Poisoner's
   canonical index (before the Demon's step).
5. *Given* the same, *when* the Storyteller places `poisoner:Poisoned` on a seat and the
   phase advances NIGHT→DAY→NIGHT, *then* the token is cleared at dusk by the existing
   `EXPIRES_AT_DUSK` table.
6. *Given* `storytellerAbilities` contains `mastermind` and every Demon is dead by
   execution, *then* `WinCheck.check` returns the Mastermind caution.
7. *Given* a Plague Doctor and a Spy both on the script but the Spy not in play, *then*
   the jinx lookup surfaces the Plague Doctor/Spy jinx.
8. *Given* a Plague Doctor resurrected after gaining an ability, *when* they die again,
   *then* the existing `StorytellerAbility` is retained and a second prompt is offered
   (not auto-applied).
9. *Given* a Plague Doctor and no `storytellerAbilities`, *when* `validateBag` runs,
   *then* the Storyteller ability ids do **not** affect team counts.
