# Angel (angel) — fabled Fabled

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Angel> (fetched verbatim via
`api.php?action=parse&page=Angel&prop=wikitext`, 2026-08-25).

Current ability text (verbatim summary line):

> "Something bad might happen to whoever is most responsible for the death of a new player."

**Summary bullets (verbatim):**

- "Use the Angel to help new players have fun when there are one or two new players in a group of veterans."
- "Being the only new player in a group can be overwhelming. Being protected by the Angel encourages all players to keep new players alive for as long as possible, which means new players have more fun and contribute to the game more."
- "All players know who is protected by the Angel, but not their alignment or character. Whoever is the single player most responsible for killing a protected player suffers some consequence. For example, if the Demon kills a protected player, the Demon suffers a penalty. If a protected player is executed, the player who suffers a penalty will probably be the one who nominated the protected player."

**How to Run (verbatim):**

> At the start of the game, declare that the Angel is in play. Declare which player or players it is protecting, with their consent. Add the Angel token and their reminders to the Grimoire, and mark each protected player with a **PROTECTED** reminder.
>
> If a player marked **PROTECTED** dies, something bad happens to the player responsible for the death. You may need to mark their character token with the **SOMETHING BAD** reminder, to remind you that they are now poisoned, or mad, or can't vote today, or simply as a reminder to decide on what to do later.
>
> Remove the Angel at any time, declaring when you do so.
>
> *(storyteller box)* The Angel only protects a player if that player wants it to. Ask for their consent before the game begins.
>
> *(storyteller box)* The "something bad" that happens is up to you. However, it is recommended to either make the penalty that the player dies, that the player loses their ability for a day, or that the player may not vote for a day. A light penalty works much better than a severe one.
>
> *(storyteller box)* Remove the Angel on the final day, so that players feel free to execute players protected by the Angel.

**Examples (verbatim):**

1. "The Angel protects Sarah. The Demon attacks and kills her. As punishment, the Demon cannot attack on the next night."
2. "Ben is the Demon and is protected by the Angel. The players do not execute him until the final day, at which point they may execute him without penalty."

**General Fabled rules** (<https://wiki.bloodontheclocktower.com/Fabled>, verbatim):
"The Fabled cannot die or lose their ability." / "Some Fabled are added at the start of the
game, while others can be added and removed at any time. You may add multiple Fabled if you
wish." / Fabled "don't count as players for the 'two players remain alive' evil team victory
condition."

**The load-bearing rule, stated plainly:** the Angel's **PROTECTED** token is *not*
protection. A PROTECTED player dies exactly as normal. The token names a player whose
death triggers a **penalty on someone else**. Any UI that treats "Protected" as
death-prevention is telling the storyteller the opposite of the rule.

**Jinxes:** none. `angel` appears in no jinx pair (grep of `night_and_jinxes.json` and of the
wiki's Djinn Special Rules list, 131 entries, finds no Angel).

**Night order:** the Angel never wakes. Correctly absent from both order lists.

## What the app does today

Data:
- `engine/src/main/resources/botc/data/characters.json:2132-2146` — `id: angel`,
  `edition: fabled`, `team: fabled`, ability text **matches the wiki exactly**,
  `setup: false`, no night reminders, `reminders: ["Protect", "Something Bad"]`.
  (Wiki calls the first token **PROTECTED**; the app data — copied from the community
  dataset, cf. `bra1n/townsquare` `src/fabled.json` — calls it `Protect`. Cosmetic, but see
  defect 2, where the mismatch is load-bearing.)
- `night_and_jinxes.json` — correctly absent from `firstNight`/`otherNight`. **Works.**
- `night_guide.json` — no entry. Correct for the night sheet; there is no day-time
  run-book schema at all, so the Angel has no run-book anywhere.

Code — the Angel has **zero engine awareness**. The complete set of paths that touch it:
- `GameState.kt:98` — `fabledIds: List<String>`; the *only* thing the app stores about a
  Fabled is its id. There is nowhere to record who is protected, who consented, or what
  penalty was chosen.
- `GameActions.kt:211-212` — `setFabled(state, ids)` replaces the whole list.
- `GameExtras.kt:145-198` `FabledSheet` — a flat toggle list of all 17 Fabled, showing
  name + ability. Tapping toggles the id. No follow-up prompt of any kind.
- `GrimoireScreen.kt:197-219` — active fabled render as 30dp tokens in the top-right corner
  of the circle, tappable to reopen `FabledSheet`. Decorative only.
- `GameShell.kt:239-240, 501` — the "Fabled…" menu entry.
- `NightOrder.kt:49, 144-145` — a fabled id in `fabledIds` produces a night step *only if
  that id is in the order list*. Angel isn't, so no step. Correct outcome, but it also means
  the Angel never gets a `NightToolTray`, which is where reminder tokens are placed
  (`NightScreen.kt:202`, `character?.allReminders`).

Storyteller's actual experience of running an Angel game on this app:

1. Setup: toggle "Angel" in the Fabled sheet. Nothing asks who is protected. The
   protected player's name lives only in the storyteller's head or the free-text notes
   field (`GameState.storytellerNotes`, `GameShell.kt` notes dialog).
2. Marking the protected player: open the seat → Add reminder →
   `ReminderPicker` (`SeatSheet.kt:492-500`) builds its list from
   `viewModel.gameData.resolve(state.script)`. For the three built-in scripts,
   `GameData.builtIn()` (`GameData.kt:35-43`) filters `it.team.isTownResident`, which
   **excludes Fabled**. So the Angel's own `Protect` / `Something Bad` tokens **do not
   appear anywhere in the reminder picker**. The only reachable option is the generic
   `"Protected"` chip (`SeatSheet.kt:502`), placed with `sourceId = ""`.
   (An *imported* script that lists `angel` in its JSON does put it in
   `script.characterIds` — `ScriptParser.parse`, `Script.kt:55-70` — and then the tokens do
   show under "Rest of script". So the behaviour silently differs between built-in and
   imported scripts.)
3. That generic `"Protected"` token is then read by `StatusEffects.deathNotes`
   (`StatusEffects.kt:64-71`): `"protected" -> notes += "Marked 'Protected' (?) — can't die
   tonight."` and by `SeatSheet.kt:256-265`, whose `protectionNotes` filter matches the
   substring `"Protected"`. So pressing "Died at night" on the Angel-protected player pops
   a confirmation dialog headed **"<name> might be protected"** listing **"! Marked
   'Protected' (?) — can't die tonight."**
4. When the protected player dies, nothing at all happens: `GameActions.kill`
   (`GameActions.kt:136-156`) records a `DeathRecord` and stops. No prompt, no penalty
   tracking, no note in the dawn announcement, no entry in the game log
   (`GameExtras.kt:46-106` logs deaths and nominations only, never Fabled events).
5. If the storyteller decides the penalty is "may not vote today", nothing enforces it:
   `DayScreen.kt:184` computes `canVote = p.alive || !p.ghostVoteUsed || isExile`.
   And nothing expires it: `EXPIRES_AT_DAWN` / `EXPIRES_AT_DUSK`
   (`GameActions.kt:218-242`) contain no `angel` entries, so a hand-placed "Something Bad"
   token sits on the grimoire for the rest of the game.
6. Nothing ever suggests removing the Angel on the final day.

## Defects and gaps

1. **P0** · The only reachable "protected" token makes the app claim the player *can't
   die*. The Angel's PROTECTED token means the opposite: the player dies normally and
   someone else is punished. Because the Angel's own tokens are unreachable on built-in
   scripts (see defect 2), the storyteller uses the generic `"Protected"` chip
   (`SeatSheet.kt:502`); `StatusEffects.kt:67` then renders "can't die tonight" and
   `SeatSheet.kt:256-267` gates the kill behind a "might be protected" dialog.
   *Repro:* built-in Trouble Brewing → Fabled… → Angel → tap a seat → Add reminder →
   Generic → "Protected" → back on the seat, tap "Died at night" → dialog
   "**<name> might be protected** — ! Marked 'Protected' (?) — can't die tonight."
2. **P0** · The Angel's reminder tokens cannot be placed at all on a built-in script.
   `ReminderPicker` (`SeatSheet.kt:498-500`) sources from `resolve(script)`, and
   `GameData.builtIn()` (`GameData.kt:39-42`) filters out Fabled; the Angel also has no
   night step, so `NightToolTray` (`NightScreen.kt:202`) never offers them either.
   `characters.json:2140-2143` defines `Protect` and `Something Bad`, and neither is
   offerable. *Repro:* Angel active on Bad Moon Rising → any seat → Add reminder → scroll:
   no "Angel" group exists.
3. **P0** · No consequence when a PROTECTED player dies. The wiki's entire mechanic is
   "if a player marked PROTECTED dies, something bad happens to the player responsible".
   `GameActions.kill` (`GameActions.kt:136-156`) fires no trigger, and there is no
   dawn/day-start surface where such a prompt could appear. The storyteller must remember,
   unprompted, mid-night, that this particular death is special.
4. **P1** · Who the Angel protects is not stored. `GameState.fabledIds` (`GameState.kt:98`)
   is a bare list of ids; `FabledSheet` (`GameExtras.kt:167-195`) toggles it and asks
   nothing. Consent, the protected player(s), and the chosen penalty all live in the
   storyteller's memory.
5. **P1** · The app cannot identify "the player most responsible" even though it has the
   data. For an execution it is the nominator: `Nomination.nominatorId` is recorded
   (`GameState.kt:63-72`, written at `DayScreen.kt:221-229`) and
   `GameActions.aboutToDie` (`GameActions.kt:296-306`) already knows which nomination put
   the player on the block. For a night death by the Demon the responsible player is the
   Demon, which `NightScreen.DemonKillPanel` (`NightScreen.kt:534`) has in hand at the
   moment of the kill. Neither is offered.
6. **P1** · "Something Bad" penalties are unenforced and never expire. The two most
   recommended penalties are "loses their ability for a day" and "may not vote for a day".
   The app has no vote-ban concept (`DayScreen.kt:184`), and `EXPIRES_AT_DUSK`
   (`GameActions.kt:231-242`) has no `angel` rows, so a token placed by hand persists
   forever.
7. **P2** · No prompt to remove the Angel on the final day, which the wiki explicitly
   recommends ("Remove the Angel on the final day, so that players feel free to execute
   players protected by the Angel"). The app has no notion of a final day at all
   (see also `ferryman.md`).
8. **P2** · Nothing to show the table. All players are supposed to know who is protected;
   the app's show-card tool (`ShowCards.kt:65-77`) has no card for "these players are
   protected by the Angel", and the reveal/announce surfaces never mention Fabled.
9. **P2** · The game log (`GameExtras.kt:46-106`) records deaths and nominations only.
   Adding/removing the Angel, marking a protected player, and applying a penalty are
   invisible in the log and in the end-game reveal (`GameExtras.kt:268-350`).
10. **P3** · Token label drift: wiki **PROTECTED**, `characters.json:2141` `"Protect"`.
    Whatever the app renders should match the wiki so a storyteller reading the almanac
    finds the same word.

## Proposed behaviour (spec)

**Storage (shared across all Fabled — see the same block in `buddhist.md`,
`doomsayer.md`, `duchess.md`, `fibbin.md`, `fiddler.md`, `ferryman.md`):**

```kotlin
@Serializable
data class FabledEntry(
    val id: String,
    /** Players this Fabled is attached to (Angel: protected; Buddhist: veterans). */
    val playerIds: List<Long> = emptyList(),
    /** Once-per-game marks keyed by player (Doomsayer). */
    val spentBy: Set<Long> = emptySet(),
    /** Global once-per-game mark (Fibbin, Fiddler). */
    val used: Boolean = false,
    /** Free text: Bootlegger house rules, Angel penalty chosen, etc. */
    val note: String = "",
    val addedOnCycle: Int = 1,
)
// GameState gains: val fabled: List<FabledEntry> = emptyList()
// fabledIds stays as a derived convenience: get() = fabled.map { it.id }
```

**Angel-specific:**

- when: never wakes. No night-order entry. No first/other night step.
- setup input (blocking prompt, same pattern as the Fortune Teller red-herring dialog at
  `GameShell.kt:341-375`): when `angel` is added to `fabled` and
  `FabledEntry.playerIds.isEmpty()`, show
  **"Who does the Angel protect?"** — a multi-select of all seats, plus the sentence
  "Ask each player's consent first. They still die normally; whoever is most responsible
  for their death gets a penalty." Confirm writes `playerIds` and places
  `PlacedReminder("angel", "Protected")` on each.
- tokens: rename the data label to `Protected` (`characters.json:2141`) to match the wiki,
  and make Fabled reminder tokens reachable everywhere — `ReminderPicker`
  (`SeatSheet.kt:498-500`) must union `resolve(script)` with
  `state.fabled.mapNotNull { gameData.character(it.id) }`, rendered as a
  "Fabled" group. `"Protected"` needs as many copies as protected players, so
  `characters.json` should list it once per expected protectee or the placement code
  (`NightScreen.kt:319-336`) must treat Fabled tokens as unlimited.
- **`StatusEffects` must stop reading `angel`-sourced tokens as protection.**
  `StatusEffects.kt:64-71` matches on `label.lowercase()` alone; it must match on
  `(sourceId, label)`. `PlacedReminder("angel", "Protected")` produces **no** protection
  note. Instead `deathNotes` gains:
  `"ANGEL: <name> is protected — they die normally, but something bad happens to whoever is most responsible."`
- immediate effects: none. The Angel never prevents, delays or redirects a death.
- deferred effects — **the Angel prompt**. Fire whenever `kill()` is applied to a player
  holding `("angel","Protected")`, i.e. from `SeatSheet` kill buttons, `DemonKillPanel`,
  the `DayScreen` Execute button (`DayScreen.kt:111-113`, `350-357`) and any future
  Doomsayer/Fiddler path. The prompt is:

  > **Angel: <victim> was protected**
  > Who was most responsible?
  > • `<nominator name>` (nominated them) — offered when the death is
  >   `EXECUTION` and `aboutToDie` traces to a nomination
  > • `<demon name>` (the Demon) — offered when the death is `DEMON`
  > • any other seat (list)
  > • Nobody / decide later
  >
  > Penalty: [ Dies ] [ No ability today ] [ Can't vote today ] [ Mark only ]

  Each button applies its consequence atomically:
  - *Dies* → `kill(responsible, DeathCause.STORYTELLER)`.
  - *No ability today* → `addReminder(responsible, PlacedReminder("angel", "No ability"))`,
    which `StatusEffects.isImpaired` must treat as impairing (extend
    `StatusEffects.kt:36-46` to match label `"no ability"` as well as poison/drunk), and
    which expires at dusk.
  - *Can't vote today* → `addReminder(responsible, PlacedReminder("angel", "Can't vote"))`;
    `DayScreen.kt:184` gains `&& p.reminders.none { it.sourceId=="angel" && it.label=="Can't vote" }`
    and the chip renders disabled with the tooltip "Angel penalty"; expires at dusk.
  - *Mark only* → `addReminder(responsible, PlacedReminder("angel", "Something Bad"))`,
    never expires, shows in the day-start briefing until removed.
  A skip is always available; the prompt is undoable like everything else.
- expiry: add to `EXPIRES_AT_DUSK` (`GameActions.kt:231-242`):
  `"angel" to "No ability"`, `"angel" to "Can't vote"`.
  `("angel","Protected")` and `("angel","Something Bad")` never expire.
- information: none computed. No show card needed for the info itself, but add a
  `ShowCard.Message` preset "**These players are protected by the Angel**" listing the
  names, since the table is entitled to know.
- visibility: all players know who is protected; the Demon/Minions learn nothing extra.
- day-time inputs: none beyond the penalty choice above.
- final-day nudge: when `state.aliveNonTravellers.size <= 3` and `angel` is active, the
  day-start briefing shows **"Remove the Angel? On the final day players should be free to
  execute a protected player."** with a one-tap remove that clears the `Protected` tokens.
- interactions:
  - The Angel penalty is applied *by the storyteller*, not by an ability, so it is not
    blocked by the Demon being drunk/poisoned, and "the Demon cannot attack next night"
    (wiki example 1) is expressible as `("angel","No ability")` on the Demon +
    the night sheet showing "ANGEL penalty: the Demon does not attack tonight" on the Demon
    step — mirror the Exorcist annotation at `NightOrder.kt:150-154`.
  - The Angel does **not** interact with Monk/Soldier/Innkeeper/Devil's Advocate
    protection; if a real protection also applies, both notes must show, clearly separated.
  - Fabled cannot die or lose their ability, so nothing (Vortox, poison, the Pit-Hag)
    affects the Angel.

**UI text for the surfaces:**
- Fabled sheet row, when active: "Protecting: Ana, Ben · tap to change".
- Seat sheet, on a protected seat: "ANGEL protected — they die normally; someone gets a penalty."
- Kill confirmation replacement: "Angel: <name> is protected. They still die. Choose who is
  most responsible next."

**Data changes:**
- `characters.json:2141` — `"Protect"` → `"Protected"`; add `"No ability"` and
  `"Can't vote"` to `reminders` so the penalty tokens exist in data.
- `night_guide.json` — add an `angel` entry once the guide schema grows a `day` section
  (currently only `first`/`other`, `NightGuide.kt:36-40`), carrying the How-to-Run text and
  the penalty menu wording.
- night order data: no change.

## Tests to add

1. `angel protected token is not a protection`
   Given a game with `fabled = [angel]` and `PlacedReminder("angel","Protected")` on Ana,
   When `StatusEffects.deathNotes(state, lookup, ana)` is called,
   Then the result contains no note matching `"can't die"` / `"can not die"`, and contains
   a note starting `"ANGEL:"`. *(Fails today: `StatusEffects.kt:67` returns "can't die
   tonight" for any label equal to `"protected"`.)*
2. `generic protected token still reads as protection but angel-sourced does not`
   Given `PlacedReminder("", "Protected")` on Ana and `PlacedReminder("angel","Protected")`
   on Ben, Then Ana gets the "can't die tonight" note and Ben does not. *(Fails today:
   both produce it, because only the label is inspected.)*
3. `killing an angel-protected player raises the responsibility prompt`
   Given Ana holds `("angel","Protected")` and is on the block from a nomination by Ben,
   When she is killed with `DeathCause.EXECUTION`,
   Then the engine surfaces an `AngelPrompt(victim = ana, suggested = ben, reason = "nominated them")`.
4. `angel penalty tokens expire at dusk`
   Given `("angel","Can't vote")` on Ben during DAY 2,
   When `GameActions.advancePhase` runs (DAY→NIGHT),
   Then the token is gone, while `("angel","Something Bad")` on Cara survives.
   *(Fails today: neither is in `EXPIRES_AT_DUSK`.)*
5. `angel cant-vote penalty blocks a vote`
   Given Ben alive with `("angel","Can't vote")`, Then the Day screen's `canVote(ben)` is
   false, and a recorded nomination that includes Ben rejects his vote.
6. `angel reminder tokens are offerable on a built-in script`
   Given script = built-in `bmr` and `fabled = [angel]`,
   When the reminder catalogue for the seat sheet is built,
   Then it contains `("angel","Protected")` and `("angel","Something Bad")`.
   *(Fails today: `resolve(builtIn)` excludes Fabled, `GameData.kt:39-42`.)*
7. `fabled entry stores protected players`
   Given `setFabled` adds `angel` with `playerIds = [ana.id]`,
   Then `state.fabled.single { it.id == "angel" }.playerIds == listOf(ana.id)` survives a
   serialization round-trip. *(Fails today: `GameState` has no such field.)*
