# Night engine (NightOrder / NightScreen / advancePhase / nightStepsDone / NightGuide) — mechanics

Scope: the machinery that decides **which steps exist tonight**, **whether each one
fires**, **what the storyteller inputs**, **what the engine does with that input**, and
**what the app hands over at dawn/dusk**. Per-character rules stay in
`docs/audit/characters/*.md`; this file specifies the generic engine those files need.

Sibling files that overlap and are deliberately *not* duplicated here:
`mechanics/status-model.md` (impairment semantics), `mechanics/day-engine.md`
(nominations/execution), `mechanics/records-and-memory.md` (log, day-time claims),
`ux/night-screen.md` (layout/ergonomics).

---

## Official rules (sources)

### The night sheet is a checklist derived from state, not a fixed list

- Glossary — **Night Sheet**: *"The sheet the Storyteller uses to know which characters act
  in which order at night."* **Dawn**: *"The end of a night, just before the next day
  begins. Characters that act 'at dawn' act after almost all other characters."*
  **Dusk**: *"The start of a night, just after the players close their eyes. Characters
  that act 'at dusk' act before almost all other characters."*
  (<https://wiki.bloodontheclocktower.com/Glossary>)
- Abilities — *"The night sheet serves as a reminder guide, but some abilities may happen at
  a different time than what is listed on the night sheet."* Ability text takes priority
  over the printed order. (<https://wiki.bloodontheclocktower.com/Abilities>)
- Abilities — *"When used, abilities work immediately."* Order of resolution matters, so a
  protection that lands before an attack saves the target.
- Abilities — *"Abilities are lost immediately on death, poisoning, or drunkenness."*
  Exceptions exist only where a character's own text says so ("even if dead", or the
  trigger *is* the death).
- Abilities — *"Upon becoming a new character, players immediately gain the new ability and
  lose the old one, including ending persistent effects."*
- Abilities — *"If a player becomes a new character mid-game whose ability is 'once per
  game', that ability resets and can be used again that game."*
- States — *"A dead player cannot die again."*; *"A drunk or poisoned player has no
  ability"*; *"If a drunk player becomes sober again, or if a poisoned player becomes
  healthy again, they regain their ability."*
  (<https://wiki.bloodontheclocktower.com/States>)
- Glossary — **Resurrected**: *"A dead player becoming alive again. When this happens, the
  player gains their ability back."*

### Steps that only exist because something happened tonight

The official *other-night* reminder text in the bundled dataset is itself written as a
conditional in ~20 places — the condition is data the app already has:

| id | `characters.json` otherNightReminder (abridged) | line |
|---|---|---|
| `ravenkeeper` | "**If the Ravenkeeper died tonight**: …" | `characters.json` |
| `sage` | "**If the Sage was killed by a Demon**: …" | |
| `farmer` | "**If the Farmer died tonight**, wake a living good player…" | |
| `barber` | "**If the Barber died today**: Wake the Demon…" | |
| `poppygrower` | "**If the Poppy Grower died today or tonight**, wake all Minions…" | |
| `scarletwoman` | "**If the Scarlet Woman became the Demon today**: Show the 'You are' card…" | |
| `undertaker` | "**If a player was executed today**: Show that player's character token." | `:135` |
| `godfather` | "**If an Outsider died today**: The Godfather points to a player." | `:631` |
| `zombuul` | "**If no-one died during the day**: The Zombuul points to a player." | `:703` |
| `princess` | "**If the Princess nominated the player who was executed today**, wake the Demon as normal, but no one dies…" | `:1551` |
| `juggler` | "**If today was the Juggler's first day**: Show the hand signal…" | `:838` |
| `professor` | "**If the Professor has not used their ability**: …" | |
| `courtier` | "…**If the Courtier has not yet used their ability**: …" | `:372` |
| `po` | "**If the Po chose no-one the previous night**: The Po points to three players." | `:658` |
| `shabaloth` | "One player that the Shabaloth chose **the previous night** might be resurrected." | `:688` |
| `summoner` | "…**If it is night 3**, the Summoner chooses a player & a Demon character." | `:1877` |
| `xaan` | "…**If it is night X**, mark the Xaan with the 'X' reminder token." | `:1935` |
| `hatter` | "**If the Hatter died**, wake the Minions & Demons…" | |
| `plaguedoctor` | "**If the Plague Doctor died**… If you haven't done this yet, do so now." | |
| `pithag` | "…**If this character is not in play**, wake that player and show them the 'You are' card…" | |

### Steps that must be *inserted* mid-night, in first-night form

- **Professor** — *"At dawn, after declaring which players died, declare which player is
  alive again. (Do not say why.)"* and *"If they wake on the first night only, they wake
  now to use their ability"* immediately upon resurrection.
  (<https://wiki.bloodontheclocktower.com/Professor>)
- **Summoner** — *"The newly created Demon **acts on the same night that it is created**."*
  *"The new Demon does not learn which players are Minions, or vice versa."*
  (<https://wiki.bloodontheclocktower.com/Summoner>, quoted in `characters/summoner.md`)
- **Snitch** — the wiki's own example is *the Pit-Hag creating a Snitch mid-game, with all
  Minions then learning three not-in-play characters* — i.e. a **first-night step re-run on
  night N**. (<https://wiki.bloodontheclocktower.com/Snitch>)
- **Farmer** — *"The new Farmer is awakened the same night as the original Farmer's death…
  Notably, new Farmers do **not** receive first-night information."*
  (<https://wiki.bloodontheclocktower.com/Farmer>) — so "gets a token" and "re-runs their
  first night" are **two different flags**, not one.
- **Pit-Hag / Engineer / Kazali / Fang Gu / Imp star-pass / Hatter / Boffin / Alchemist /
  Philosopher / Bone Collector** — each ends with *"Show the 'You are' info token & their
  new character token"* and, for characters with first-night info, that info is owed.

### Gating rules quoted verbatim

- **7+ players** — `characters.json:564` (`lunatic.firstNightReminder`, straight from the
  official dataset): *"**If 7 or more players**: Show the Lunatic a number of arbitrary
  'Minions'…"*. The same gate is printed on the official Minion-Info / Demon-Info rows of
  every night sheet. The text says *players*, not *residents*; the wiki does not state
  whether Travellers count — **flagged as an open rules question below**.
- **Poppy Grower** — *"The Demon wakes alone and receives three safe bluff characters, but
  the normal Minion Info and Demon Info steps are skipped—minions don't wake at all."*
  On death: *"Mark them with the EVIL WAKES reminder. That night, minions wake to see each
  other, the demon learns who the minions are."* *"If the Poppy Grower is drunk or poisoned
  when they die, evil players still don't learn each other's identities."*
  (<https://wiki.bloodontheclocktower.com/Poppy_Grower>)
- **Magician** — *"First Night – Minions: Show the THIS IS THE DEMON token, then point to
  the Demon **and Magician**. First Night – Demon: Show THESE ARE YOUR MINIONS token,
  pointing to all minions **and the Magician**… This replaces the standard minion and demon
  info steps."* (<https://wiki.bloodontheclocktower.com/Magician>)
- **Snitch** — *"During the first night, wake each Minion individually. Show the 'THESE
  CHARACTERS ARE NOT IN PLAY' token, then display three character tokens that aren't in
  play."* Each Minion may get a different set.
- **Lil' Monsta** — *"During setup, remove Lil' Monsta and add a Minion token. **On the
  first night, skip the MINION INFO and DEMON INFO steps.**"* There is no Demon player;
  the babysitter is marked **IS THE DEMON** each night.
  (<https://wiki.bloodontheclocktower.com/Lil%27_Monsta>, and `characters/lilmonsta.md`)
- **Summoner** — `[No Demon]`; the Summoner gets its own 3 bluffs on night 1 and there is
  no Demon to point at until night 3.
- **Exorcist** — *"Each night*, choose a player (different to last night): the Demon, if
  chosen, learns who you are then doesn't wake tonight."* Crucially: *"Any other Demon
  abilities still function—such as the Zombuul staying alive if killed, **the Pukka killing
  a player they attacked on a previous night**, or the Shabaloth regurgitating a player."*
  and *"The Demon does not wake for their attack ability, but will still wake if they need
  to due to other characters' abilities."* The Exorcist *"may not choose the same player two
  nights in a row."* (<https://wiki.bloodontheclocktower.com/Exorcist>)
- **Vigormortis** — *"Dead Minions retain and use their abilities while the Vigormortis
  remains alive… The Witch, Cerenovus, and Pit-Hag still act each night."* Tokens: **HAS
  ABILITY** *"one per killed Minion"*, **POISONED** *"one per killed Minion"*.
  (<https://wiki.bloodontheclocktower.com/Vigormortis>)
- **Zombuul** — alive but registering as dead; still acts (see `characters/zombuul.md`).
- **Ravenkeeper** — acts *because* they died at night (see `characters/ravenkeeper.md`).

### Who "woke tonight" and what "malfunctioned"

- **Chambermaid** — *"Each night, choose 2 alive players (not yourself): you learn how many
  woke tonight due to their ability."* Counts characters who *"woke in order to use their
  ability"*. Explicitly **does not** include: the Chambermaid herself, *"players woken for
  demon/minion info"*, players woken accidentally, players who woke but didn't use their
  ability. **Does** include drunk/poisoned characters who woke to use their ability.
  Wiki example: *"Next night, Exorcist wakes Shabaloth. Chambermaid chooses Shabaloth and
  Fool, learns '0' (Shabaloth only woke due to another ability)."*
  Also: *"Do not wake the Chambermaid if fewer than two alive players remain."*
  (<https://wiki.bloodontheclocktower.com/Chambermaid>)
- **Mathematician** — *"Each night, you learn how many players' abilities worked abnormally
  (since dawn) due to another character's ability."* How to run: *"Mark each instance where
  a character's ability malfunctions due to another character's interference with an
  ABNORMAL reminder. Each night… show fingers indicating the total count of marked
  reminders. Then put them to sleep and **remove all reminders**."* The Mathematician
  *"doesn't detect their own ability failing."*
  (<https://wiki.bloodontheclocktower.com/Mathematician>)

---

## What the app does today

### Sheet construction

`NightOrder.build` (`engine/.../NightOrder.kt:40-208`) walks a **static** id list from
`night_and_jinxes.json` (`firstNight`, 76 entries, `:294`; `otherNight`, 96 entries, `:372`)
and emits one `NightStep` per id that has holders:

- Holders are `state.players.groupBy { it.nightRoleId }` — `NightOrder.kt:46-49`. **No
  alive filter, no ability filter, no condition filter.**
- `Player.nightRoleId` (`GameState.kt:39-44`) redirects Drunk/Marionette to their shown
  character; everyone else uses `characterId`.
- `holders.isEmpty() && !isFabledActive → continue` (`NightOrder.kt:145`) is the *only*
  gate.
- Step text = `firstNightReminder` / `otherNightReminder`, falling back to `ability`
  (`NightOrder.kt:146-148`).
- Two hard-coded text annotations are appended to the Demon's step: Exorcist
  (`NightOrder.kt:149-154`) and Lunatic (`NightOrder.kt:155-172`).
- `MINION_INFO` / `DEMON_INFO` render only on night 1 and only when
  `state.players.count { !it.isTraveller } >= 7` (`NightOrder.kt:52, 60, 81`). Their bodies
  are prose built from the current grimoire (`:60-119`); `DEMON_INFO` also names the
  Marionette and the Lunatic.
- `marionette` is special-cased into a teensyville-only "Marionette info" row
  (`NightOrder.kt:121-141`).
- Homebrew characters not on either list are appended just before `DAWN`, sorted by the
  script's declared night position (`NightOrder.kt:183-207`).
- `DUSK` and `DAWN` are constant strings (`NightOrder.kt:58-59`). `DAWN` reads
  *"Wait a few seconds. Everyone opens their eyes. Announce who died."* — the app never
  computes who that is.

`NightScreen` rebuilds the sheet on any change to `players`/`fabledIds`/`cycle`/
`demonBluffIds` (`NightScreen.kt:84-90`), so a character *change* does re-key the list —
but only against the same static order list, so a newly created character gets a step only
if its id happens to sit on tonight's list.

### Progress tracking

`GameState.nightStepsDone: Set<String>` (`GameState.kt:105-106`), toggled by
`GameActions.toggleNightStep` (`GameActions.kt:265-272`), cleared on every phase entry into
NIGHT (`GameActions.kt:259, 262`). The set holds **step ids**, i.e. character ids.

### Tools per step

`StepDetailPanel` (`NightScreen.kt:770-934`) renders, in order: a one-tap bluffs card for
`DEMON_INFO` (`:783-788`); `NightGuide` prose + prepared show cards (`:792-832`);
`QuickResolutions` (`:834`); `InfoCalc` output with a target picker (`:836-932`).

`QuickResolutions` (`NightScreen.kt:461-525`) is a four-branch `when` on `step.id`:
`snakecharmer`, `fanggu`, `professor`, and `else →` if the character's team is DEMON and
the holder is alive, `DemonKillPanel`. Everything else gets **no action UI at all**.

`DemonKillPanel` (`NightScreen.kt:534-638`) is the generic "who dies?" panel: an impairment
banner (`:548-554`), a seat picker (`:555-584`), `StatusEffects.deathNotes` for the target
(`:586-590`), an Imp-only star-pass branch (`:591-622`), else `[X dies]` / `[No kill]`
(`:623-636`).

`NightToolTray` (`NightScreen.kt:193-357`) offers the active character's reminder labels;
tapping a label then a seat places it, using `placeExclusiveReminder` when the character
declares one copy and a rotate-the-oldest scheme when it declares more
(`NightScreen.kt:308-354`, copies counted at `:319-321`). A "Mark spent" chip
(`:263-279`) places `PlacedReminder(character.id, "No ability")` on every holder when the
ability text `startsWith("Once per game")` (`:204`).

### Phase advance

`GameActions.advancePhase` (`GameActions.kt:258-263`):
`SETUP→NIGHT 1`; `NIGHT→DAY` clears `EXPIRES_AT_DAWN` (`:218-225`);
`DAY→NIGHT` clears `EXPIRES_AT_DUSK` (`:231-242`), increments `cycle`, resets
`nightStepsDone`. `clearEphemeral` (`:244-251`) removes exact `(sourceId,label)` pairs.

`GameShell.requestPhaseAdvance` (`GameShell.kt:126-168`) debounces, runs the setup guard,
the dusk on-the-block guard, and a night guard that lists every step not in
`nightStepsDone` (`:147-161`) before calling `viewModel.advancePhase()` (`:162`). The
"Night checklist incomplete" dialog (`:618-659`) offers "Dawn anyway".

### What works

- The static order itself matches the official sheets for the ids checked here
  (Poisoner before Washerwoman, Imp between Poisoner and Empath — `GameActionsTest.kt:150`).
- Both expiry tables' 16 `(sourceId,label)` pairs match `characters.json` **exactly,
  including case** — verified programmatically against `reminders + remindersGlobal` for all
  16. No case bugs in the tables as written.
- Rebuilding the sheet on `state.players` change means seat/character edits are reflected
  live within the same night.
- Undo/redo covers every night action because everything goes through
  `GameViewModel.update` (`GameViewModel.kt:101-110`).

---

## Defects and gaps

### A. Dynamic steps

1. **P0 · A character created mid-game never gets its step.** The sheet is the static
   `otherNight` list intersected with in-play ids (`NightOrder.kt:56, 142-145`). A Pit-Hag
   making a Chef, an Engineer making a Poisoner, a Summoner making a Lleech on night 3, a
   Kazali making Minions on night 1, a Fang Gu jump, an Imp star-pass, a Hatter reshuffle,
   a Farmer chain, a Professor resurrection — none of them insert a step. The Summoner case
   is a hard rules break: *"The newly created Demon acts on the same night that it is
   created"* but `lleech`/`imp`/… will only appear on the **next** night's sheet.
   *Repro:* night 3, use the Summoner step to make someone a Lleech (there is no UI for it —
   change the character from the seat sheet). The Lleech's poison/kill step does not appear
   tonight.
2. **P0 · A resurrected / newly-created player's FIRST-night step is never re-run.**
   `NightOrder.firstNight` is only reachable when `state.cycle == 1`
   (`NightScreen.kt:83-89`, `GameShell.kt:148-152`). The Professor page requires *"If they
   wake on the first night only, they wake now to use their ability"*; the Snitch page shows
   a Pit-Hag-made Snitch giving all Minions bluffs mid-game. Neither is possible.
   *Repro:* Grandmother dies night 2, Professor resurrects her night 3 → no Grandmother
   "you start knowing" step. (Also `characters/professor.md` defect 3.)
3. **P0 · Death-triggered steps fire on nights they must not, and are labelled "skip".**
   `ravenkeeper`, `sage`, `farmer`, `barber`, `poppygrower`, `hatter`, `plaguedoctor`,
   `scarletwoman`, `undertaker`, `godfather`, `zombuul`, `princess`, `juggler` all render
   unconditionally every night. Worse, when the trigger *does* fire the holder is usually
   dead, so `NightStepRow` prints *"All holders are dead — usually skip."* in error red
   (`NightScreen.kt:700-702, 751-757`), dims the token (`:720`), and `InfoCalc.impairments`
   adds *"…is dead — they normally don't act."* (`InfoCalc.kt:150`). The list footer repeats
   it (`NightScreen.kt:160-169`). The engine actively advises against the correct play.
4. **P0 · The dawn guard forces the ST to tick steps that cannot fire.**
   `GameShell.kt:153-155` lists **every** step not in `nightStepsDone`. With a
   Ravenkeeper + Sage + Farmer + Professor + Undertaker in play the ST must tick five
   impossible rows every night or press "Dawn anyway", which trains them to ignore the
   guard entirely.
5. **P1 · `nightStepsDone` is keyed by character id only** (`GameState.kt:106`), so a step
   cannot appear twice in one night (a re-run first-night step, a Farmer chain, an
   Engineer creating two Minions), and two holders of the same character (3 Village Idiots,
   two Fang Gu after a jump) share one checkbox.
6. **P1 · Steps that a jinx or setup moves are not moved.** `night_and_jinxes.json` carries
   58 jinxes as prose only; nothing consumes them. The Summoner/Pukka jinx ("may summon a
   Pukka on the 2nd night") is absent from the file entirely
   (`characters/pukka.md`), so neither the text nor the order adapts.

### B. Step gating

7. **P0 · No wake predicate exists at all.** Every gate the rules define — alive, has
   ability, not spent, condition met, not Exorcised, enough players — is absent from
   `NightOrder.build`. The only gate is "somebody holds this character".
8. **P0 · Dead players who must still act are discouraged; dead players who must not act
   are offered tools.** `DemonKillPanel` is gated on `holder.alive`
   (`NightScreen.kt:520`), which is right for most Demons but wrong for the Zombuul (alive,
   registers dead — `characters/zombuul.md` defect 2) and irrelevant for the three real
   exceptions the engine must know: **Ravenkeeper/Sage/Farmer/Barber** (act *because* they
   died), **Vigormortis-killed Minions** (*"Dead Minions retain and use their abilities"*),
   **Zombuul**. Conversely nothing stops a dead Poisoner/Monk/Fortune Teller row from
   presenting its full toolkit.
9. **P0 · `starPass` leaves two Demon seats and holders are picked by seat index.**
   `GameActions.starPass` (`:79-96`) kills the old Demon but never clears its
   `characterId` (`:88-94`), so `NightOrder.kt:46-49` puts **both** seats in
   `step.playerIds` in seat order. `QuickResolutions` takes `playerIds.firstOrNull()`
   (`NightScreen.kt:467`) and `InfoCalc` takes the same (`:837`). If the dead original sits
   at a lower index the kill panel disappears and info is computed for a corpse. Also
   pollutes `InfoCalc.clockmaker/knight/sage/flowergirl` and every "who is the Demon" query
   (see `characters/fanggu.md` defects 1 and 6). Same for `swapCharacters` (`:98-115`) and
   `snakeCharmerSwap` (`:64-72`), which additionally leave the old character's reminder
   tokens on the seat — contradicting *"including ending persistent effects"*.
10. **P1 · Exorcist suppression is a text suffix, not a gate.**
    `NightOrder.kt:149-154` appends *"— EXORCIST chose them: the Demon does not act
    tonight."* to the Demon's step, but `DemonKillPanel` still renders the full kill picker
    (`NightScreen.kt:518-523`). The engine has no notion of a **reduced** step, which is
    exactly what the Exorcist produces: the Pukka's previously-poisoned player *still dies*,
    the Shabaloth *still regurgitates*, the Zombuul *still survives its first death*.
    Nothing distinguishes "Demon does not act" from "Demon's step does not exist".
11. **P1 · Once-per-game "spent" marks are ad hoc.** `NightScreen.kt:204` detects
    once-per-game as `ability.startsWith("Once per game")`, which misses Fool ("The 1st
    time you die"), Virgin, Slayer, Golem, Damsel, Princess, Juggler, Mezepheles, and
    catches nothing for characters whose spent-marker label differs. The chip writes the
    literal lowercase `"No ability"` (`:272`) while `characters.json` uses **`"No Ability"`
    (capital A)** for `engineer`, `fisherman`, `huntsman`, `nightwatchman`, `preacher`,
    `mezepheles`, `bonecollector` and **`"No ability"`** for `slayer`, `virgin`, `fool`,
    `courtier`, `professor`, `assassin`, `artist`, `seamstress`, `judge`. Reads use
    `equals(..., true)` so behaviour mostly survives, but the placed token no longer matches
    the character's declared reminder set, which breaks copy counting
    (`NightScreen.kt:319-321`) and any future exact-match rule (including the expiry
    tables). The chip also loops all holders calling `placeExclusiveReminder`
    (`:268-275`), so with two holders only the last keeps the token.
    No step is ever hidden or greyed once spent (`characters/professor.md` defect 8).
12. **P1 · Poppy Grower does not alter MINION_INFO/DEMON_INFO.** `NightOrder.kt:60-119`
    builds both steps from team membership alone. The rules require: night 1 —
    **skip both**, wake the Demon alone for bluffs; on the night the Poppy Grower dies —
    **insert** the two info steps (unless the Poppy Grower was impaired at death).
13. **P1 · Magician is not applied.** `magician.firstNightReminder` says *"During Minion
    Info, point to the Magician and the Demon. During Demon Info, point to the Magician and
    the Minions."* — but the Magician is a Townsfolk, so `NightOrder.kt:61-67, 82-88` filter
    it out of both lists. The ST gets the true lists and must remember the jinx.
14. **P1 · Snitch has a step but no per-Minion structure.** `snitch` is on the first-night
    list; the step is one row of prose. The rules want one wake **per Minion**, each with
    its own (possibly different) 3 bluffs, and the app has exactly one `demonBluffIds` slot
    (`GameState.kt:102`). Same slot is also the Lunatic's problem
    (`characters/lunatic.md` defect 2).
15. **P1 · No-bag-Demon games render nonsense.** With a Summoner (`[No Demon]`) or
    Lil' Monsta, `NightOrder.kt:65-67, 86-88` find zero Demons and still emit
    *"Wake the Demon . Point out the Minions…"*. Lil' Monsta requires **both info steps
    skipped on night 1**; Summoner requires `DEMON_INFO` skipped and `MINION_INFO` to say
    there is no Demon yet. Legion/Kazali need their own shapes (see the respective character
    files).
16. **P2 · The 7+ threshold counts residents only.** `NightOrder.kt:52` uses
    `state.players.count { !it.isTraveller } >= 7`. The official wording quoted in
    `characters.json:564` is *"If 7 or more **players**"*. A 6-resident Teensyville game
    with a Traveller therefore sits exactly on the ambiguity. **Open rules question** — I
    could not find a wiki statement either way; the app should make the count explicit and
    let the ST override it rather than silently pick a reading.
17. **P2 · The Lunatic's own 7+ gate is ignored.** `characters.json:564` gates the
    Lunatic's fake Minion/bluff info on 7+ players; nothing implements it.
18. **P2 · Chambermaid's "do not wake if fewer than two alive players remain"** is not
    implemented; nor is "not yourself" in the target picker (`NightScreen.kt:846-860`
    lists every seat).

### C. Actions

19. **P0 · Every Demon that is not the Imp/Fang Gu gets a kill panel that is wrong for
    it.** `NightScreen.kt:518-523` routes on `team == DEMON`. Consequences, all reproducible
    in one BMR game: **Pukka** is offered a kill on night 1 and every night, when it should
    poison now and kill the *previously* poisoned player (**the user's headline
    complaint**); **Po** is offered one target with no charge tracking and no 3-target
    mode; **Shabaloth** is offered one target instead of two, with no regurgitation;
    **Zombuul** is offered a kill on nights when someone died today and is denied one
    entirely once "dead" (`holder.alive` gate); **Al-Hadikhia** gets one target instead of
    three plus the live/die sequence; **Ojo** picks a *character*, not a player;
    **Vigormortis** gets no Has-ability/Poisoned follow-through; **Lil' Monsta**, **Legion**,
    **Leviathan**, **Riot**, **Yaggababble** get a kill picker that does not match their
    text at all. And **the Lunatic** — an Outsider — gets *no* picker even though it must
    mimic whichever Demon it thinks it is (`characters/lunatic.md` defect 1).
20. **P0 · "Different from last night" is unenforceable because nothing is remembered.**
    Exorcist (*"different to last night"*) and Devil's Advocate (*"different to last
    night"*) both place tokens that are wiped at dawn/dusk
    (`GameActions.kt:222, 236`), so by the time the ST reaches the next night's step the
    grimoire holds no record of the previous choice. This is precisely the user's report:
    *"DA wasn't automatically removed and allowed two days in a row I think for same
    person."* — the removal half works for a token placed from the character's own tray, but
    the **repeat-target half is simply not checked**, and a token placed from the seat
    sheet's *Generic* row (`SeatSheet.kt:529`, `PlacedReminder("", label)`) has `sourceId
    ""` and therefore **never expires at all**, which reproduces the "wasn't removed" half.
21. **P0 · Deferred effects have no representation.** Pukka's poison→next-night death,
    Shabaloth's regurgitation of *last* night's corpse, Godfather's "an Outsider died
    today", Al-Hadikhia's dawn resolution, Summoner's night-3 countdown, Xaan's night X,
    Courtier's 3-night countdown, Leviathan/Riot day counters — all of it lives only in the
    ST's head or in tokens the app neither places nor advances.
22. **P1 · No re-derivation of impairment between sequential kills.** The Demon panel
    evaluates `StatusEffects.isImpaired` once when the panel composes
    (`NightScreen.kt:548`) and `deathNotes` once per selected target (`:586-590`). For a
    two- or three-target Demon (Shabaloth, Po charged, Al-Hadikhia) each kill can change
    who is impaired or protected — a No Dashii neighbour dying re-derives poison
    (`StatusEffects.derivedPoison` is positional, `StatusEffects.kt:14-33`); an Innkeeper
    dying removes protection; a Vigormortis kill poisons a Townsfolk neighbour who may be
    the next target. The engine must resolve kills one at a time and recompute between them.
23. **P1 · "No kill" is not an outcome, just a cancel.** `NightScreen.kt:634` clears the
    selection. Nothing records that the Demon chose no-one — which is exactly the state
    **Po** needs to charge to 3 attacks, and what the Mathematician and the dawn report need
    to distinguish "no death" from "step skipped".
24. **P1 · Character-target actions have no picker.** Ojo, Cerenovus, Pit-Hag, Courtier,
    Philosopher, Summoner, Engineer all choose a *character*. The only character picker in a
    night step is inside `GuideShowDialog` (`NightScreen.kt:392-434`), which shows a card —
    it does not feed any action.
25. **P1 · Multi-copy tokens are silently single-copy.** `NightScreen.kt:319-321` counts
    copies from `character.allReminders`; the bundled data declares **one** `"Dead"` for
    `po` (needs 3), one for `shabaloth` (needs 2), one `"Dead"`/`"Has ability"`/`"Poisoned"`
    for `vigormortis` (the wiki says *one per killed Minion*, up to 3), one `"Correct"` for
    `juggler` (up to 5) and one `"Abnormal"` for `mathematician` (unbounded). With one
    declared copy the tray calls `placeExclusiveReminder` (`:323-324`), which **moves** the
    token off the previous seat — so the Vigormortis's first zombie Minion silently loses
    its `Has ability` when the second is made. `yaggababble` (3×`Dead`), `knight`,
    `noble`, `preacher` are correct in data and prove the field supports duplicates.
26. **P2 · `InfoCalc` target selections leak across nights.** `NightScreen.kt:839` uses
    `rememberSaveable(step.id)`, keyed by a value that is constant all game, while
    `DemonKillPanel` (`:540`) and `ResolutionPicker` (`:651`) correctly key by
    `state.cycle`. The Fortune Teller's night-2 pair can still be selected on night 3.

### D. Expiry tables

27. **Verified clean:** all 16 pairs in `EXPIRES_AT_DAWN` (`GameActions.kt:218-225`) and
    `EXPIRES_AT_DUSK` (`:231-242`) match `characters.json` labels **exactly, case
    included**. No action needed on the existing entries. (`stormcatcher:Safe` and
    `monk:Safe` share a label but differ in `sourceId`, so the pair key correctly keeps the
    Fabled token permanent.)
28. **P1 · Tokens missing from the tables.** Every one of these is placed by hand today and
    never removed by the engine:

    *Should expire at **dawn** (placed during the day or night, consumed by their own step):*
    `undertaker:Died today`, `godfather:Died today`, `zombuul:Died today`,
    `acrobat:Chosen`, `princess:Doesn't Kill`, `barber:Haircuts tonight`,
    `poppygrower:Evil Wakes`, `juggler:Correct` (*"Remove markers"*),
    `mathematician:Abnormal` (*"remove all reminders"*),
    `flowergirl:Demon voted`/`Demon not voted` and
    `towncrier:Minion nominated`/`Minions not nominated` (the official text is a reset to
    the negative token, not a plain delete).

    *Should expire at **dusk**:* `goon:Drunk` (*"drunk until dusk"*),
    `organgrinder:Drunk` (*"choose if you are drunk until dusk"*),
    `bonecollector:Has ability` (*"regain their ability until dusk"*),
    `lycanthrope:Faux Paw` (per `characters/lycanthrope.md`).

    *Needs a **multi-dusk** timer, not a table:* `minstrel:Everyone is drunk`
    (*"drunk until dusk tomorrow"* — the execution is on day N, so this survives one dusk).
    Flagged for `characters/minstrel.md` to confirm the count.

    *Must **never** expire (correctly absent, listed so nobody adds them):*
    `pukka:Poisoned`, `snakecharmer:Poisoned`, `widow:Poisoned`, `nodashii:Poisoned`,
    `lleech:Poisoned`, `vigormortis:Poisoned`/`Has ability`, `sweetheart:Drunk`,
    `philosopher:Drunk`, `puzzlemaster:Drunk`, `villageidiot:Drunk`, `cannibal:Poisoned`
    (conditional: until a good player is executed), `drunk:Is the Drunk`,
    `marionette:Is The Marionette`, `fortuneteller:Red herring`, `eviltwin:Twin`,
    `mezepheles:Turns Evil`, `stormcatcher:Safe`, `angel:Protect`, `toymaker:Final Night: No Attack`.

    *Needs a **countdown**, not an expiry:* `courtier:Drunk 3/2/1` (the official other-night
    text is literally *"Reduce the remaining number of days the marked player is drunk"*),
    `summoner:Night 1/2/3`, `xaan:Night 1/2/3` (+`X`), `leviathan:Day 1…5`,
    `riot:Day 1/2/3`.
29. **P1 · Generic tokens from the seat sheet are immortal and invisible to the tables.**
    `SeatSheet.kt:502, 529` places `PlacedReminder("", label)` for
    `Drunk, Poisoned, Dead, Protected, Mad, Good, Evil, Used, ?`. `StatusEffects.isImpaired`
    matches on **label substring only** (`StatusEffects.kt:38-42`), so a generic "Poisoned"
    poisons for the rest of the game and no `(sourceId,label)` pair can ever clear it. This
    is the most likely mechanical cause of the user's *"DA wasn't automatically removed"*.
    Character tokens from the same picker use the right `sourceId` (`SeatSheet.kt:562`) —
    but go through `viewModel.addReminder` (`SeatSheet.kt:113`), i.e. **never**
    `placeExclusiveReminder`, so tapping "Poisoned" on a second seat leaves two poisons.
30. **P2 · No conditional teardown.** Nothing removes a token when its source dies, is
    poisoned, or becomes a different character, despite *"Abilities are lost immediately on
    death, poisoning, or drunkenness"* and *"including ending persistent effects"*. The
    per-character answer differs (Vigormortis's `Has ability` explicitly survives until the
    Vigormortis dies; a Monk who dies mid-night loses `Safe`), so the engine needs a
    declared rule per token, not a blanket policy.

### E. Dawn / Dusk

31. **P0 · There is no dawn report.** `DAWN`'s text is a constant
    (`NightOrder.kt:59`) and `advancePhase` (`GameActions.kt:258-263`) carries nothing into
    the day. The ST must remember, unaided: who died, who did *not* die and why, who was
    resurrected (*"At dawn, after declaring which players died, declare which player is
    alive again"* — the user's explicit complaint), that a Zombuul's fake death must be
    announced as a real one, Leviathan's "it is day N of 5", the Fearmonger announcement,
    Damsel/Vizier announcements, and which good player must be told something privately.
32. **P1 · There is no day-start briefing.** `DayScreen.kt:85-124` shows alive count,
    threshold and the block only. Nothing lists standing protections, madness obligations
    (Cerenovus/Harpy/Pixie), `devilsadvocate:Survives execution`, `goblin:Claimed`,
    `witch:Cursed`, Minstrel drunkenness, or the day-time inputs the app needs collected
    (Gossip statement, Juggler guesses, Artist/Savant/Fisherman requests) — the collection
    gap the user called out for Gossip.
33. **P1 · There is no dusk briefing.** Tapping "Dusk" clears tokens with no preview
    (`GameActions.kt:261`), so the ST cannot see what is about to be swept, who will wake
    tonight, or which conditional wakes are armed ("nobody died today → the Zombuul kills
    tonight" is the single most useful pre-night fact and is nowhere).
34. **P1 · The dawn report must be computed before `clearEphemeral`.**
    `advancePhase` deletes `monk:Safe` / `innkeeper:Protected` / `exorcist:Chosen` at the
    moment of transition (`:260`), destroying the evidence for "X was attacked but
    protected" before anything can read it.
35. **P2 · No night-1 preparation pass.** `SETUP→NIGHT` (`GameActions.kt:259`) does not
    place the tokens the official how-to-runs require *before* the first night:
    `summoner:Night 1`, `xaan:Night 1`, `leviathan:Day 1`, `riot:Day 1`.

### F. Chambermaid / Mathematician

36. **P1 · "Who woke tonight" is not tracked.** `InfoCalc.chambermaid` exists
    (`InfoCalc.kt:76`) but can only guess from the static night list; it cannot know that
    the Exorcist suppressed the Demon, that the Ravenkeeper woke because they died, that a
    Po chose no-one, or that Minion/Demon info wakes **do not count**.
37. **P1 · "What malfunctioned tonight" is not tracked.** `InfoCalc.kt:77-80` returns the
    literal placeholder *"Track malfunctions manually"*. `mathematician:Abnormal` is never
    placed and never cleared, and the engine already knows most malfunctions (it computes
    impairment for every info step and prints a caveat).

---

## Proposed behaviour (spec)

### 0. Shape

Five new engine concepts, all pure and serializable, all in `engine/`:

| concept | file | replaces |
|---|---|---|
| `NightPlan` / `NightStep` / `StepGate` | `NightOrder.kt` (rewrite) | the static list walk |
| `NightAction` + `NightEffect` | new `NightAction.kt` | `QuickResolutions` / `DemonKillPanel` |
| `TokenRule` registry | `Tokens.kt` (new), consumed by `GameActions` | `EXPIRES_AT_DAWN/DUSK` sets |
| `ChoiceRecord`, `PendingEffect`, `WakeEvent` | `GameState.kt` (new fields) | the ST's memory |
| `DawnReport`, `DayBriefing`, `DuskBriefing` | new `Briefings.kt` | nothing |

`NightScreen` becomes a **generic renderer**: it draws `step.action` and applies
`step.action.resolve(...)`. No character ids in the UI layer.

### 1. Dynamic night sheet

```kotlin
// GameState.kt
@Serializable
data class StepKey(
    val id: String,               // character id or NightMarkers.*
    val playerId: Long? = null,   // per-holder steps (Village Idiot ×3, Snitch per Minion)
    val variant: String = "",     // "" | "first" | "again" | "reduced"
) {
    /** Stable string used in nightStepsDone; degrades to plain id for simple steps. */
    val token: String get() = buildString {
        append(id); playerId?.let { append('#').append(it) }
        if (variant.isNotEmpty()) { append('@').append(variant) }
    }
}

// GameState gains:
val nightStepsDone: Set<String>      // unchanged type; now holds StepKey.token
val choices: List<ChoiceRecord> = emptyList()
val pending: List<PendingEffect> = emptyList()
val wakes: List<WakeEvent> = emptyList()
```

`StepKey("poisoner").token == "poisoner"`, so existing saves keep working.

```kotlin
enum class WakeStyle { FIRST_NIGHT, OTHER_NIGHT }

@Serializable
data class NightStep(
    val key: StepKey,
    /** Sort position; base list entries get their index * 100 so insertions fit between. */
    val order: Double,
    val title: String,
    val detail: String,
    val playerIds: List<Long>,
    val style: WakeStyle,             // which reminder text and night_guide entry to use
    val gate: StepGate,               // FIRE / REDUCED / SKIP
    val action: NightAction? = null,
    val badges: List<String> = emptyList(),   // "died tonight", "spent", "new character"
) {
    val required: Boolean get() = gate !is StepGate.Skip
}

@Serializable
sealed interface StepGate {
    @Serializable object Fire : StepGate
    /** Runs, but only part of the action (Exorcised Pukka: death yes, poison no). */
    @Serializable data class Reduced(val reason: String, val allow: Set<String>) : StepGate
    /** Rendered collapsed & greyed, auto-ticked, never blocks Dawn. */
    @Serializable data class Skip(val reason: String) : StepGate
}
```

`NightPlan.build(state, lookup, night)`:

1. Choose the base list: `night == 1 ? firstNight : otherNight`.
2. For each id with holders, emit one step per **holder** when the character declares
   `perHolder = true` (Village Idiot, Snitch's per-Minion wake, Lunatic), else one shared
   step.
3. Evaluate `wakePredicate(id)` (§2) to produce the `StepGate`.
4. **Insert derived steps.** After every action is applied the plan is rebuilt (it is a
   pure function of state), so insertions are automatic if the following are derivable:
   - *first-night-style re-runs*: for every player carrying
     `("", "Re-run first night")` — see below — emit `StepKey(id, playerId, "first")` with
     `style = FIRST_NIGHT`, placed at that character's **first-night** order position,
     scaled into tonight's ordering, but never before the current step (see step 6).
   - *newly-created characters*: any player whose `characterId` changed tonight
     (tracked by a `CharacterChange` record, §3) whose character has a first- or
     other-night reminder and whose step is not yet in the plan.
   - *death-triggered*: `ravenkeeper`, `sage`, `farmer`, `barber`, `poppygrower`,
     `hatter`, `plaguedoctor` when the trigger condition holds (they are already on the
     other-night list, so this is a gate, not an insertion — except when the trigger
     happens on night 1, e.g. a Kazali/Lord of Typhon night-1 kill, where the step must be
     inserted into the first-night plan; see `characters/ravenkeeper.md` defect 8).
   - *Scarlet Woman / star-pass heir / Fang Gu jumpee / Summoner's Demon*: the new Demon's
     own step, tonight, at the Demon's order position — **after** the current position when
     the Demon's slot has already passed (Summoner sits at other-night index 30 and every
     Demon from `imp` (37) to `leviathan` (54) is later, so this is usually natural; a
     Scarlet Woman promotion mid-night may still need the "insert-after-cursor" rule).
5. **Re-run marking.** Rather than a magic reminder, `resurrect` / `BecomeCharacter` push a
   `PendingEffect(kind = "first-night", targetId, dueNight = state.cycle)`; the planner
   turns each into a `variant = "first"` step and the effect is consumed when the step is
   ticked. `reRunFirstNight` is **false** for Farmer (wiki: *"new Farmers do not receive
   first-night information"*) and true by default for Professor/Pit-Hag/Engineer/Hatter/
   Summoner/Kazali/star-pass/Fang Gu — each character file states which.
6. **Insert-after-cursor rule.** The plan exposes `cursor = index of the first
   required step not in nightStepsDone`. Any step inserted with `order` earlier than the
   cursor is re-stamped to `cursor + 0.5` and badged *"out of order — this became true
   after their slot"*, which is exactly what the Abilities page licenses (*"some abilities
   may happen at a different time than what is listed on the night sheet"*).

**Which insertions are "first-night-style" for that player**

| trigger | new step | style | notes |
|---|---|---|---|
| Professor resurrection | resurrected player's own step | FIRST_NIGHT **if that character wakes only on night 1** else none | wiki: *"If they wake on the first night only, they wake now"* |
| Shabaloth regurgitation, Bone Collector | same | FIRST_NIGHT, same rule | Bone Collector's *"They may need to be woken tonight to use it"* is in the official text |
| Pit-Hag / Engineer / Hatter / Kazali / Summoner (new character) | new character's step | FIRST_NIGHT | plus the mandatory "YOU ARE" show sequence |
| Imp star-pass / Fang Gu jump / Scarlet Woman | new Demon's step | OTHER_NIGHT | they do **not** get Minion info (Summoner: *"The new Demon does not learn which players are Minions"*) |
| Farmer chain | new Farmer's step | none | wiki says no first-night info |
| Snitch created mid-game | `MINION_BLUFFS` per Minion | FIRST_NIGHT | wiki example is explicit |
| Poppy Grower dies | `MINION_INFO` + `DEMON_INFO` | FIRST_NIGHT | unless the Poppy Grower was impaired at death |
| Alchemist / Cannibal / Pixie / Philosopher gains an ability | the gained character's step, for the *holder* | matches the gained character's own "when" | the gained ability is used at its own night-order position |
| Plague Doctor dies | `STORYTELLER_ABILITY` marker step | FIRST_NIGHT | the ST now holds a Minion ability; the step is a note-to-self plus the Minion's own step from then on |

### 2. Wake predicates

```kotlin
class WakeContext(
    val state: GameState, val lookup: (String) -> Character?, val night: Int,
    val holder: Player?,                     // null for markers
    val diedTonight: Set<Long>, val diedToday: Set<Long>,
    val executedToday: Long?, val resurrectedTonight: Set<Long>,
    val residentCount: Int, val totalSeatCount: Int,
)

fun interface WakePredicate { fun gate(ctx: WakeContext): StepGate }
```

Composable primitives, then a per-character table (`WakeRules.kt`) that character audits
fill in:

```kotlin
object Gates {
    val aliveHolder: WakePredicate                      // Skip("dead") unless…
    val actsWhileDead: WakePredicate                    // ravenkeeper/sage/farmer/barber/zombuul/…
    val hasAbility: WakePredicate                       // no "No ability"/"No Ability" token, not the Drunk,
                                                        //   not poisoned *if the ability is silent when impaired*
    fun notSpent(sourceId: String, label: String = "No ability"): WakePredicate
    fun diedTonight(): WakePredicate
    fun diedTodayOrTonight(): WakePredicate
    fun someoneDiedToday(expected: Boolean): WakePredicate    // Zombuul(false), Godfather(outsider=true)
    fun executedToday(): WakePredicate                        // Undertaker
    fun nightIs(n: Int): WakePredicate                        // Summoner 3, Xaan X
    fun minPlayers(n: Int, countTravellers: Boolean): WakePredicate
    fun minAlive(n: Int): WakePredicate                       // Chambermaid ≥ 2 others
    val notExorcised: WakePredicate                     // Reduced, not Skip — see below
}
```

Rules that must be encoded exactly:

- **Dead holders.** Default `Skip("dead — no ability")`. Overridden by an explicit
  `actsWhileDead` set: `ravenkeeper` (only on the night they died), `sage`, `farmer`,
  `barber`, `poppygrower`, `hatter`, `plaguedoctor` (trigger *is* the death); `zombuul`
  (alive but marked dead — it must be a `registersDead` flag on the seat, not `alive =
  false`, see `characters/zombuul.md`); **any Minion carrying
  `vigormortis:Has ability` while a Vigormortis is alive**; any player carrying
  `bonecollector:Has ability` tonight. Everyone else is skipped, and the current red
  *"All holders are dead — usually skip"* text (`NightScreen.kt:751-757`) becomes a
  **positive** badge on the exception rows: *"Dead — but this ability fires on death."*
- **Exorcist → `StepGate.Reduced`.** `allow = setOf("pending", "passive")`, i.e. the Demon's
  step still runs its **deferred half**: Pukka's previously-poisoned player dies and becomes
  healthy (wiki, verbatim: *"the Pukka killing a player they attacked on a previous
  night"*), Shabaloth regurgitates, Zombuul survives its first death, Vigormortis's zombie
  Minions keep acting. The **choice half** (`allow` excludes `"choose"`) is replaced by a
  banner and a `[Nothing chosen]` button. Never `Skip` — that would suppress the death.
- **Once-per-game spent.** One canonical label per character in a new
  `Character.spentLabel` field (defaulting to the character's own `"No ability"` /
  `"No Ability"` as declared in `characters.json`) so the placed token always matches the
  data. `Gates.notSpent` reads it exactly; the "Mark spent" chip writes it exactly. The
  resurrection rule (*"they gain their ability back… even if it was a once per game
  ability"*, Glossary) means `resurrect` **removes** the spent token — see §3.
- **Poppy Grower.** `MINION_INFO`/`DEMON_INFO` gate:
  `Skip("Poppy Grower")` on night 1 when an unimpaired Poppy Grower lives; a
  `DEMON_BLUFFS_ONLY` step replaces `DEMON_INFO`; on the night the Poppy Grower dies (today
  or tonight) both steps are **inserted** with `style = FIRST_NIGHT`, unless the Poppy
  Grower was impaired at the moment of death (`DeathRecord.abilityImpairedAtDeath`,
  already snapshotted at `GameActions.kt:153`).
- **Magician.** Not a gate but a **content transform** on the two info steps: the Minion
  list shown to the Demon gains the Magician; the Demon list shown to the Minions gains the
  Magician; the ST is told to point in a scrambled order.
- **Snitch.** Replaces the single `snitch` row with `StepKey("MINION_BLUFFS", minionId)`
  per Minion, each carrying its own 3-character slot (§5 `bluffSets`).
- **No-bag-Demon games.**
  `Skip("Lil' Monsta — no Demon player on night 1")` for both info steps when a
  `lilmonsta` token is in play; `Skip("Summoner — no Demon yet")` for `DEMON_INFO` and a
  content note on `MINION_INFO`; Kazali's Minion creation must run **before**
  `MINION_INFO` (it already does — `night_and_jinxes.json` firstNight index 2 vs 14) and
  the info step must be rebuilt from post-Kazali state (it is, thanks to the
  `remember(state.players…)` key, but the plan must also *re-open* it if it was already
  ticked); Legion has no Minion/Demon info at all.
- **Teensyville threshold.** `minPlayers(7, countTravellers = ?)`. Because the rules text is
  *"7 or more players"* and the wiki gives no ruling on Travellers, the engine should
  (a) default to **total seats including Travellers**, matching the literal wording,
  (b) surface the decision in the step: *"6 residents + 1 Traveller = 7 players — Minion
  info ON. Tap to run the Teensyville rule instead."*, and (c) let the ST flip it, storing
  the choice on the game. Never silently pick. Same predicate feeds the Lunatic's fake
  Minion/bluff info (`characters.json:564`).
- **Auto-skip and the Dawn guard.** `GameShell.kt:153-155` filters on
  `step.required`; skipped steps are auto-added to `nightStepsDone` by the planner so the
  progress counter (`NightScreen.kt:132-136`) reads honestly, and are rendered collapsed
  with their `Skip.reason` (*"Nobody died today — the Zombuul does not kill tonight"*).
  A `[Run anyway]` affordance on every skipped row keeps the ST in charge.

### 3. Night action model

```kotlin
// engine/NightAction.kt
@Serializable
sealed interface NightAction {
    val sourceId: String
    val prompt: String            // storyteller-voice imperative
}

@Serializable
data class ChoosePlayers(
    override val sourceId: String,
    override val prompt: String,
    val min: Int,
    val max: Int,
    val constraints: List<TargetConstraint> = emptyList(),
    val sort: TargetSort = TargetSort.ALIVE_FIRST,
    val allowNone: Boolean = false,
    val noneLabel: String = "No choice",
    /** Applied per target, in pick order, re-deriving state between each. */
    val perTarget: List<NightEffect> = emptyList(),
    /** Applied once, after all targets. */
    val onResolve: List<NightEffect> = emptyList(),
    val onNone: List<NightEffect> = emptyList(),
) : NightAction

@Serializable
data class ChooseCharacter(
    override val sourceId: String, override val prompt: String,
    val pool: CharacterPool,               // GOOD, EVIL, MINION, DEMON, NOT_IN_PLAY, SCRIPT
    val allowNone: Boolean = true,
    val onResolve: List<NightEffect> = emptyList(),
) : NightAction

@Serializable
data class ChoosePlayerAndCharacter(          // Pit-Hag, Summoner, Cerenovus, Engineer
    override val sourceId: String, override val prompt: String,
    val playerConstraints: List<TargetConstraint> = emptyList(),
    val pool: CharacterPool,
    val requireNotInPlay: Boolean = false,     // Pit-Hag
    val onResolve: List<NightEffect> = emptyList(),
) : NightAction

@Serializable
data class YesNo(                              // Organ Grinder, Po head-shake, Professor pass
    override val sourceId: String, override val prompt: String,
    val yesLabel: String, val noLabel: String,
    val onYes: List<NightEffect> = emptyList(),
    val onNo: List<NightEffect> = emptyList(),
) : NightAction

@Serializable
data class ShowInfo(                            // pure info steps: delegates to InfoCalc
    override val sourceId: String, override val prompt: String,
    val targetsNeeded: Int = 0,
    val constraints: List<TargetConstraint> = emptyList(),
) : NightAction

@Serializable
data class Sequence(                            // Al-Hadikhia: 3 picks then live/die per pick
    override val sourceId: String, override val prompt: String,
    val stages: List<NightAction>,
) : NightAction
```

```kotlin
@Serializable
enum class TargetConstraint {
    ALIVE, DEAD, ANY_LIVING_STATE,
    NOT_SELF, SELF_ALLOWED,
    NOT_TRAVELLER, TOWNSFOLK, OUTSIDER, MINION, DEMON, NOT_DEMON, GOOD, EVIL,
    DIFFERENT_FROM_LAST_NIGHT,      // Exorcist, Devil's Advocate
    NOT_CHOSEN_BEFORE,              // once-per-game "each a different player"
    NEIGHBOUR_OF_SOURCE,
}

@Serializable
enum class TargetSort { ALIVE_FIRST, DEAD_FIRST, SEAT_ORDER, DEMON_FIRST, MINION_FIRST, OUTSIDER_FIRST, TOWNSFOLK_FIRST }
```

```kotlin
@Serializable
sealed interface NightEffect {
    @Serializable data class PlaceToken(
        val sourceId: String, val label: String, val on: Ref,
        val maxCopies: Int = 1, val exclusive: Boolean = true,
    ) : NightEffect
    @Serializable data class RemoveToken(val sourceId: String, val label: String, val from: Ref) : NightEffect
    @Serializable data class Attack(
        val on: Ref, val cause: DeathCause = DeathCause.DEMON,
        /** false ⇒ unstoppable (Pukka's poisoning, Fabled effects). */
        val respectProtection: Boolean = true,
    ) : NightEffect
    @Serializable data class Resurrect(val on: Ref, val clearSpentMarks: Boolean = true) : NightEffect
    @Serializable data class BecomeCharacter(
        val on: Ref, val characterId: String, val evil: Boolean,
        val clearOldTokens: Boolean = true, val reRunFirstNight: Boolean = true,
    ) : NightEffect
    @Serializable data class SwapCharacters(val a: Ref, val b: Ref) : NightEffect
    @Serializable data class MarkSpent(val sourceId: String) : NightEffect
    @Serializable data class RecordChoice(val slot: String = "target") : NightEffect
    @Serializable data class Defer(val kind: String, val on: Ref, val dueNight: Int, val note: String) : NightEffect
    @Serializable data class Announce(val at: BriefingSlot, val text: String) : NightEffect
    @Serializable data class NoteMalfunction(val on: Ref, val reason: String) : NightEffect
    @Serializable data class ShowCardTo(val on: Ref, val card: String) : NightEffect
}

@Serializable
enum class Ref { SOURCE, TARGET, PREVIOUS_TARGET, ALL_TARGETS, TOWNSFOLK_NEIGHBOUR_OF_TARGET }

@Serializable
enum class BriefingSlot { DAWN_PUBLIC, DAWN_PRIVATE, DAY_START, NOMINATION, EXECUTION, DUSK }
```

**Resolution contract** (`NightAction.resolve(state, step, input, lookup): GameState`) —
one function, in `GameActions`:

1. Validate `input` against `constraints` **at resolve time**, not just at pick time.
2. For `ChoosePlayers`, apply `perTarget` **one target at a time**, and between each target
   re-derive: `StatusEffects.isImpaired` of the source, `StatusEffects.derivedPoison`
   (positional — a No Dashii neighbour dying changes it), protections on the remaining
   targets, and re-run `deathNotes`. Surface a per-target confirmation row so the ST sees
   the recomputed warnings (defect 22).
3. Append a `ChoiceRecord` for the whole action (defect 20).
4. Append a `WakeEvent` for the source and for every player who was woken as part of the
   action (defect 36).
5. Append `NoteMalfunction` entries where the engine can prove one (defect 37).

**"Different from last night" memory** — survives token expiry because it is not a token:

```kotlin
@Serializable
data class ChoiceRecord(
    val night: Int,
    val sourceId: String,
    val holderId: Long,
    val slot: String = "target",
    val playerIds: List<Long> = emptyList(),
    val characterIds: List<String> = emptyList(),
    val chosenNone: Boolean = false,
)

fun GameState.lastChoice(sourceId: String, holderId: Long, slot: String = "target") =
    choices.lastOrNull { it.sourceId == sourceId && it.holderId == holderId && it.slot == slot }
```

`DIFFERENT_FROM_LAST_NIGHT` disables the seat chip whose id appears in
`lastChoice(...)?.playerIds` **when that record's `night == currentNight - 1`**, with the
label *"chosen last night — not allowed"*. Consumers: **Exorcist** and **Devil's Advocate**
(both *"different to last night"*); **Po** (`chosenNone` on night N-1 ⇒ 3 targets tonight —
`characters.json:658`); **Shabaloth** (last night's `playerIds`, filtered to the dead, are
the regurgitation candidates); **Monk/Innkeeper/Sailor/Fortune Teller** get the record for
free and can display *"last night: Ada"* as a courtesy.

**Deferred effects** — two mechanisms, deliberately:

- *Standing token* (preferred where the physical game uses one): Pukka's `Poisoned` is the
  memory; the engine reads it at the next Pukka step. `characters/pukka.md` specifies the
  order of operations. `TokenRule` marks it `Expiry.NEVER`.
- *`PendingEffect` queue* (where there is no token or the timing is not "at my next step"):

```kotlin
@Serializable
data class PendingEffect(
    val id: Long, val kind: String,          // "kill" | "first-night" | "info" | "announce"
    val sourceId: String, val targetId: Long? = null,
    val dueNight: Int, val dueSlot: BriefingSlot? = null,
    val note: String = "",
)
```
  Consumers: Al-Hadikhia's dawn resolution, Godfather's "an Outsider died today ⇒ kill
  tonight", Summoner's night-3 arm, first-night re-runs, and the Fabled.

**Multi-target, character-target, no-kill, examples** (full specs live in the character
files; these show the shape):

```kotlin
// Shabaloth — 2 kills + regurgitation
ChoosePlayers("shabaloth", "Who did the Shabaloth choose? (2 players)",
    min = 2, max = 2, constraints = listOf(ALIVE), sort = ALIVE_FIRST,
    perTarget = listOf(Attack(Ref.TARGET), PlaceToken("shabaloth", "Dead", Ref.TARGET, maxCopies = 2)),
    onResolve = listOf(RecordChoice()))
// its regurgitation is a separate earlier stage, gated on lastChoice(night-1)

// Po — charge
YesNo("po", "Did the Po choose anyone tonight?",
    yesLabel = "Yes — pick targets", noLabel = "No-one",
    onNo = listOf(PlaceToken("po", "3 attacks", Ref.SOURCE), RecordChoice()))
// when po:"3 attacks" is present the action becomes ChoosePlayers(min=3,max=3)
// with onResolve = RemoveToken("po","3 attacks", SOURCE)

// Ojo — character target
ChooseCharacter("ojo", "Which character did the Ojo choose?", pool = CharacterPool.SCRIPT,
    onResolve = listOf(/* if in play → Attack that holder; else ST kills anyone */))

// Pit-Hag
ChoosePlayerAndCharacter("pithag", "Who becomes what?",
    playerConstraints = listOf(ANY_LIVING_STATE), pool = CharacterPool.SCRIPT,
    requireNotInPlay = true,
    onResolve = listOf(BecomeCharacter(Ref.TARGET, "<picked>", evil = false,
        clearOldTokens = true, reRunFirstNight = true),
        ShowCardTo(Ref.TARGET, "YOU ARE")))

// generic Demon "no kill"
ChoosePlayers("imp", "Who did the Imp choose?", min = 1, max = 1,
    constraints = listOf(ANY_LIVING_STATE, SELF_ALLOWED), allowNone = true,
    noneLabel = "No kill (drunk/poisoned, protected, or ST choice)",
    onNone = listOf(RecordChoice(), Announce(BriefingSlot.DAWN_PRIVATE, "The Demon's attack killed no-one.")))
```

**Fixing the two-Demon bug (defect 9).** `starPass` must clear the old seat:

```kotlin
fun starPass(state, demonPlayerId, heirPlayerId, lookup): GameState {
    val demonCharacter = state.player(demonPlayerId)?.characterId ?: return state
    var next = kill(state, demonPlayerId, DeathCause.OTHER_NIGHT_DEATH, lookup)
    next = next.updatePlayer(demonPlayerId) {
        it.copy(characterId = null, shownCharacterId = demonCharacter,  // keep the shroud readable
                reminders = it.reminders.filterNot { r -> r.sourceId == demonCharacter })
    }
    next = becomeCharacter(next, heirPlayerId, demonCharacter, evil = true,
                           clearOldTokens = true, reRunFirstNight = false)
    return next
}
```
`shownCharacterId` keeps the grimoire showing "was the Imp" without making the corpse a
Demon for `NightOrder`, `InfoCalc`, `WinCheck` or `deathNotes`. **Everywhere a holder is
picked, pick the acting holder, not `playerIds.firstOrNull()`**:
`NightScreen.kt:467` and `NightScreen.kt:837` become
`step.playerIds.firstOrNull { state.player(it)?.canAct == true } ?: step.playerIds.firstOrNull()`.
A shared `Player.canAct(state, lookup)` (alive, or on the `actsWhileDead` list) belongs in
`StatusEffects`.

**Where it plugs in**

- `NightScreen.StepDetailPanel` (`:770`) → replace `QuickResolutions(...)` (`:834`) with
  `NightActionPanel(viewModel, state, step)`; delete `QuickResolutions` (`:461-525`) and
  `DemonKillPanel` (`:534-638`) once every character has a declared action.
  `ResolutionPicker` (`:643-687`) survives as the generic pick-then-confirm widget.
- `NightActionPanel` renders `ChoosePlayers` as a chip grid honouring `constraints`
  (disabled chips carry the reason), `ChooseCharacter` as the picker already built for
  `GuideShowDialog` (`:392-434`), `YesNo` as two buttons, `ShowInfo` as today's `InfoCalc`
  block (`:836-932`) — with `rememberSaveable("${step.key.token}-${state.cycle}")` to fix
  defect 26.
- `GameActions` gains `resolveNightAction(state, step, input, lookup)` and the effect
  interpreter. `GameViewModel` needs nothing beyond `update {}`.
- `NightGuide.forStep(id, isFirstNight)` (`NightGuide.kt:56`) becomes
  `forStep(step.key.id, step.style == FIRST_NIGHT)` so a re-run shows the first-night
  run-book.

### 4. Token lifecycle

Replace the two `Set<Pair<String,String>>` tables with a declarative registry:

```kotlin
// engine/Tokens.kt
@Serializable
enum class Expiry { NEVER, DAWN, DUSK, DUSK_AFTER_NEXT, ON_SOURCE_STEP, CONDITIONAL }

@Serializable
data class TokenRule(
    val sourceId: String, val label: String,
    val expiry: Expiry,
    val maxCopies: Int = 1,
    /** Removed when the source player dies / loses their ability. */
    val endsWhenSourceLosesAbility: Boolean = false,
    /** Countdown chain: "Drunk 3" → "Drunk 2" → "Drunk 1" → gone, advanced at [countdownAt]. */
    val countdownNext: String? = null,
    val countdownAt: Expiry? = null,
    /** Two-state pair reset at [expiry] to [resetTo] instead of being deleted. */
    val resetTo: String? = null,
    val impairs: Boolean = false,   // authoritative flag replacing the label substring match
    val protects: Boolean = false,
)

object Tokens {
    val rules: Map<Pair<String, String>, TokenRule>       // built from data + code
    fun rule(r: PlacedReminder) = rules[r.sourceId to r.label]
}
```

Requirements:

- **Every rule's `(sourceId,label)` must exist in `characters.json`.** Add a
  `GameDataTest` that fails the build otherwise (today's tables pass; this keeps them
  passing). Case-sensitive.
- **`maxCopies` moves into the data.** Fix `characters.json` (and the `raw_*.json` sources)
  to declare the real counts: `po` → `["Dead","Dead","Dead","3 attacks"]`, `shabaloth` →
  `["Dead","Dead","Alive"]`, `vigormortis` → `["Dead","Dead","Dead","Has ability","Has
  ability","Has ability","Poisoned","Poisoned","Poisoned"]`, `juggler` → 5×`Correct`,
  `mathematician` → an unbounded `Abnormal` (`maxCopies = Int.MAX_VALUE` in the rule, since
  the token list cannot express it). `NightScreen.kt:319-321` then reads the truth instead
  of guessing, and `placeExclusiveReminder` (`GameActions.kt:194-201`) is only used where
  `maxCopies == 1`.
- **`impairs` replaces the substring match.** `StatusEffects.isImpaired`
  (`StatusEffects.kt:38-42`) currently poisons on any label containing "poison"/"drunk",
  which is why generic seat-sheet tokens are permanent poison. Keep the substring match as
  a fallback for homebrew, but prefer the rule.
- **Generic tokens get a real source.** `SeatSheet.kt:529` should place
  `PlacedReminder("st", label)` with an ST-owned rule set:
  `st:Poisoned → Expiry.DUSK`, `st:Drunk → Expiry.DUSK`, `st:Protected → Expiry.DAWN`,
  `st:Mad → Expiry.DUSK`, everything else `NEVER`. And it must go through
  `placeExclusiveReminder` or an explicit "add another" affordance
  (`SeatSheet.kt:109-113`).
- **Conditional teardown.** `endsWhenSourceLosesAbility` is evaluated in a new
  `GameActions.reconcileTokens(state, lookup)` called after every kill/resurrect/character
  change and at each phase boundary. Per-character answers come from the character files;
  the engine only needs the hook. Known: `vigormortis:Has ability` ends when the
  Vigormortis dies (wiki, and the Mastermind jinx is the stated exception);
  `BecomeCharacter(clearOldTokens = true)` removes every token whose `sourceId ==` the
  abandoned character (*"including ending persistent effects"*).
- **Countdowns.** `advancePhase` runs `Tokens.advanceCountdowns(state, at)` at DAWN and
  DUSK: `courtier:Drunk 3→2→1→∅` (official text says the Courtier's own step reduces it —
  run it at the Courtier's step and fall back to DUSK if the step is skipped),
  `summoner:Night 1→2→3`, `xaan:Night 1→2→3` (+ place `X` on night X),
  `leviathan:Day 1→…→5`, `riot:Day 1→2→3`.
- **Dawn/dusk lists become derived**, not hard-coded:
  `EXPIRES_AT_DAWN = Tokens.rules.values.filter { it.expiry == DAWN }` etc. The 16 existing
  pairs are preserved verbatim; the 20-odd missing ones from defect 28 are added.

### 5. Dawn, day-start and dusk briefings

```kotlin
// engine/Briefings.kt
data class DawnDeath(
    val playerId: Long, val name: String, val cause: DeathCause,
    val announce: Boolean,          // false for a Zombuul fake death? NO — see below
    val characterId: String?,
)

data class DawnReport(
    val night: Int,
    val deaths: List<DawnDeath>,                 // in the order to announce them
    val resurrections: List<Long>,
    val savedFromDeath: List<String>,            // "Ada was attacked but the Monk protected her"
    val publicScript: List<String>,              // literally what to say, in order
    val privateNotes: List<String>,              // what the ST must know but not say
    val privateTells: List<Pair<Long, String>>,  // "wake X and show them Y" owed at dawn
    val dayBriefing: DayBriefing,
)

data class DayBriefing(
    val day: Int,
    val protections: List<String>,        // "Bea survives execution today (Devil's Advocate)"
    val madness: List<String>,            // "Cai is mad they are the Empath (Cerenovus) — execute if unconvincing"
    val announcements: List<String>,      // Fearmonger, Vizier, Damsel, Leviathan "day N of 5", Riot
    val nominationWatch: List<String>,    // Witch cursed, Virgin unspent, Golem, Goblin claim, Vizier
    val executionWatch: List<String>,     // Saint, Minstrel, Zombuul, Mastermind, Fearmonger
    val mustCollect: List<String>,        // "Record the Gossip's statement", "Juggler's ≤5 guesses",
                                          // "Artist question?", "Savant pair", "Fisherman advice?"
    val abilitiesLostToday: List<String>, // Sweetheart drunk, Minstrel drunkenness, Xaan night
)

data class DuskBriefing(
    val night: Int,                        // the night about to start
    val expiringNow: List<String>,         // "Removing: Poisoner Poisoned (Ada), Butler Master (Bo)"
    val willWake: List<String>,            // required steps
    val conditionalWakes: List<String>,    // "Nobody died today → the Zombuul kills tonight"
    val autoSkipped: List<String>,         // with reasons
    val countdowns: List<String>,          // "Courtier: Drunk 1 — last night", "Summoner: night 3 — Demon created tonight"
)

object Briefings {
    fun dawn(state: GameState, lookup: (String) -> Character?): DawnReport
    fun day(state: GameState, lookup: (String) -> Character?): DayBriefing
    fun dusk(state: GameState, lookup: (String) -> Character?): DuskBriefing
}
```

Rules the report must encode:

- **Deaths** = `state.deaths.filter { it.atNight && it.day == night && !it.resurrected }`,
  ordered by seat (or by the ST's preference). A **Zombuul's first death is announced as a
  real death** — that is the whole point — so the report says *"Announce: Dee died"* in
  `publicScript` and *"Dee is secretly alive (Zombuul first death)"* in `privateNotes`.
- **Resurrections** are announced **after** deaths and **without a reason**:
  *"At dawn, after declaring which players died, declare which player is alive again. (Do
  not say why.)"* — verbatim from the Professor page. `publicScript` therefore ends with
  *"Announce: Cai is alive again."*
- **Saved-from-death** entries need the protection tokens, so `Briefings.dawn` runs
  **before** `clearEphemeral` (defect 34). Restructure:
  ```kotlin
  fun advancePhase(state: GameState): GameState = when (state.phase) {
      Phase.NIGHT -> {
          val report = Briefings.dawn(state, lookup)             // computed first
          clearEphemeral(state, dawnTokens)
              .let(Tokens::advanceCountdowns)
              .copy(phase = Phase.DAY, lastDawn = report)        // stored on the state
      }
      …
  }
  ```
  `GameState.lastDawn: DawnReport?` and `lastDusk: DuskBriefing?` make the report undoable
  and re-openable, and the web/PWA build gets it for free (both are `@Serializable`).
- **Private tells owed at dawn**: Grandmother's grandchild death, Choirboy, Bounty Hunter's
  replacement evil player, Damsel's "a Damsel is in play", Nightwatchman confirmations —
  each character file declares an `Announce(BriefingSlot.DAWN_PRIVATE, …)` effect and the
  report just collects them.

**Presentation in `GameShell`.** Tapping **Dawn** must stop being a silent state change.
Replace the direct `viewModel.advancePhase()` at `GameShell.kt:162` with:

1. the existing unfinished-steps guard, now filtered to `step.required` (defect 4);
2. advance the phase (which computes and stores `lastDawn`);
3. open a **Dawn sheet** — a full-screen, read-aloud card:
   > **DAWN — Night 3**
   > Announce: **Ada died.**
   > Announce: **Cai is alive again.**
   > *(private)* Bea was attacked — the Monk protected her.
   > *(private)* Dee is secretly alive (Zombuul).
   > **Before you open the day:** wake Fay and show her the Grandmother card.
   > `[ Copy ]  [ Open Day 3 ]`
4. `DayScreen` (`DayScreen.kt:85-124`) grows a collapsible **"Today"** card above the
   nomination form rendering `DayBriefing`, with the `mustCollect` entries as **taps that
   open the right recorder** (the Gossip statement box, the Juggler guess list) — this is
   the direct fix for *"make it easy to write down all the gossips even if Gossip isn't in
   play"*, delegated to `mechanics/records-and-memory.md` for the storage model.

Tapping **Dusk** likewise shows `DuskBriefing` before committing, with a
`[ Dusk ]` / `[ Not yet ]` pair. This is where "nobody died today, so the Zombuul kills
tonight" and "the Courtier's drunkenness ends tomorrow" finally reach the ST at the moment
they matter.

### 6. Wake log and malfunction log

```kotlin
@Serializable
data class WakeEvent(
    val night: Int,
    val playerId: Long,
    val stepId: String,
    /** True only when they woke to USE THEIR OWN ability. */
    val ownAbility: Boolean,
    /** False when woken as someone else's target (Exorcist's Demon, Pit-Hag's victim). */
    val usedAbility: Boolean = ownAbility,
)

@Serializable
data class MalfunctionEvent(
    val night: Int, val playerId: Long, val characterId: String?,
    val reason: String,              // "poisoned by the Poisoner", "Drunk", "Vortox"
    val sinceDawn: Boolean = true,
)
// GameState: val wakes: List<WakeEvent>, val malfunctions: List<MalfunctionEvent>
```

- Every `NightAction.resolve` appends a `WakeEvent(ownAbility = true)` for the step's
  acting holder, and `ownAbility = false` for anyone woken *by* the action
  (`ShowCardTo(Ref.TARGET, "YOU ARE")` → the target woke, but not for their own ability).
- `MINION_INFO` / `DEMON_INFO` / `MINION_BLUFFS` / `DEMON_BLUFFS_ONLY` append
  `ownAbility = false` events — the wiki is explicit that these do not count for the
  Chambermaid.
- An **Exorcised** Demon: no `WakeEvent` for the choice half; the Exorcist's target *does*
  get a `ownAbility = false` event (*"the Demon… learns who you are"* — they were woken).
  The Chambermaid must read 0 for that Demon, matching the wiki's Shabaloth example.
- **Chambermaid** becomes a real calculation:
  ```kotlin
  fun chambermaidCount(state, targets: List<Long>, night: Int) =
      targets.count { id -> state.wakes.any { it.night == night && it.playerId == id && it.ownAbility } }
  ```
  Because the Chambermaid sits at other-night index 93 of 96 — third from last, just before
  the Mathematician and `DAWN` (`night_and_jinxes.json:372`),
  almost every wake has already happened when her step runs; the step must additionally
  **project** the few later steps (Mathematician) and say so — which is exactly the
  Chambermaid/Mathematician jinx (*"The Chambermaid can detect if the Mathematician will
  wake tonight"*). Gate the step on `alivePlayers.size - 1 >= 2`.
- **Mathematician** becomes:
  ```kotlin
  fun mathematicianCount(state, night: Int) =
      state.malfunctions.filter { it.night == night }.map { it.playerId }.distinct().size
  ```
  `MalfunctionEvent`s are appended automatically wherever the engine already knows:
  every `ShowInfo` step whose `InfoCalc` result carries an impairment caveat
  (`InfoCalc.kt:133-153`), every `Attack` that was blocked by protection, every action
  whose source was impaired, every Vortox-falsified Townsfolk result. A manual
  `[+ Mark abnormal]` chip covers the rest. `mathematician:Abnormal` tokens are then
  **derived and rendered**, not hand-placed, and cleared at DAWN per the official
  *"remove all reminders"*. The `InfoCalc.kt:77-80` placeholder is deleted.

### UI text the steps should display

- Skipped step (collapsed, grey): `Skipped — nobody died today. [Run anyway]`
- Dead-but-acting step: `Dead — this ability fires because they died. Wake them.`
- Exorcised Demon: `Exorcised: no choice tonight. Their pending effects still happen.`
- Spent once-per-game: `Spent on night 2. [Undo spend]`
- Repeat-target block: `Chosen last night — the rules forbid it.`
- Out-of-order insertion: `Out of order — this became true after their slot.`
- Re-run: `First night, again — Cai is alive and owed their first-night info.`
- Teensyville ambiguity: `7 players counting the Traveller — Minion info ON. [Use Teensyville rule]`

### Data changes

- `characters.json` / `raw_*.json`: real reminder-copy counts (Po, Shabaloth, Vigormortis,
  Juggler); no other label edits (all existing labels verified correct).
- `night_guide.json`: add `DUSK`, `DAWN`, `MINION_INFO`, `DEMON_INFO`, `MINION_BLUFFS`
  entries — all four markers currently have **no** guide entry (verified: 116 entries, none
  of the four).
- `night_and_jinxes.json`: add the missing Summoner jinxes (Pukka at minimum) and give each
  jinx an optional `effect` field so order/text changes can be applied rather than merely
  displayed.
- New `Character` fields: `spentLabel: String = ""`, `perHolderStep: Boolean = false`,
  `actsWhileDead: Boolean = false`.

---

## Tests to add

Engine tests, all failing today. `data`/`tb` fixtures as in `GameActionsTest.kt:13-17`.

**Dynamic steps**

1. *Given* a night-3 game where a Pit-Hag turns a Saint into a Chef, *when* the plan is
   rebuilt, *then* it contains `StepKey("chef", saintId, "first")` with
   `style == FIRST_NIGHT`, positioned after the Pit-Hag step, and `nightStepsDone` does not
   contain its token.
2. *Given* a Professor resurrecting a dead Grandmother on night 3, *then* the plan gains a
   `grandmother` step with `style == FIRST_NIGHT`; *and* `resurrect` has removed any
   `("<char>", spentLabel)` token from that seat (Glossary: ability comes back).
3. *Given* a Farmer who dies at night, *then* a `farmer` step exists tonight for the
   **new** Farmer with `style == OTHER_NIGHT` and **no** first-night re-run
   (wiki: new Farmers get no first-night info).
4. *Given* a Summoner with a `Night 3` token, *when* it turns a player into a Lleech,
   *then* the Lleech's step appears **in the same night's plan**, after the Summoner.
5. *Given* an Imp star-pass to a Minion, *then* `state.players.count { char.team == DEMON }
   == 1`, the corpse's `characterId == null`, `shownCharacterId == "imp"`, and the `imp`
   step's `playerIds` contains only the heir.
6. *Given* a Fang Gu jump where the dead original occupies a **lower** seat index than the
   new Fang Gu, *then* the resolved acting holder is the living one
   (the `NightScreen.kt:467` regression).
7. *Given* a Poppy Grower who dies on day 2, *then* night 3's plan contains
   `MINION_INFO` and `DEMON_INFO` with `style == FIRST_NIGHT`; *and* if the Poppy Grower
   was poisoned at death (`abilityImpairedAtDeath == true`) it contains neither.
8. *Given* a Pit-Hag creating a Snitch on night 3 in a 3-Minion game, *then* the plan
   contains three `MINION_BLUFFS` steps, one per Minion.

**Gating**

9. *Given* an alive Ravenkeeper on night 3, *then* the `ravenkeeper` step is
   `StepGate.Skip`, is auto-ticked, and does **not** appear in the Dawn guard's unfinished
   list. *Given* they died tonight, *then* it is `StepGate.Fire` with a "dead but acts"
   badge.
10. *Given* an Exorcist choosing the Pukka, *then* the Pukka step is `StepGate.Reduced`,
    the poison half is unavailable, and resolving the reduced step still kills the standing
    `pukka:Poisoned` holder and removes that token.
11. *Given* an Exorcist choosing the same player two nights running, *then* the target chip
    is rejected by `DIFFERENT_FROM_LAST_NIGHT` even though `exorcist:Chosen` was cleared at
    dawn.
12. *Given* a Devil's Advocate who chose Ada on night 2, *then* on night 3 Ada is disabled;
    *and* `devilsadvocate:Survives execution` is absent from every seat at the start of
    night 3 (regression for the user's report).
13. *Given* a Vigormortis-killed Poisoner carrying `vigormortis:Has ability`, *then* the
    `poisoner` step is `StepGate.Fire` despite the holder being dead; *and* when the
    Vigormortis dies, `reconcileTokens` removes the `Has ability` token and the step
    becomes `Skip`.
14. *Given* a Zombuul in a game where someone died today, *then* its step is
    `Skip("someone died today")`; *given* nobody died, `Fire`.
15. *Given* 6 residents + 1 Traveller, *then* `MINION_INFO` is present under the
    default (players ≥ 7) reading and its detail names the ambiguity; *given* the ST has set
    the Teensyville override, it is absent.
16. *Given* a Lil' Monsta game, *then* night 1 contains neither `MINION_INFO` nor
    `DEMON_INFO`. *Given* a Summoner game, *then* night 1 contains `MINION_INFO` (with a
    "no Demon yet" note) and no `DEMON_INFO`.
17. *Given* a Magician in play, *then* `DEMON_INFO.detail` names the Magician among the
    Minions and `MINION_INFO.detail` names the Magician among the Demons.
18. *Given* a Professor whose seat carries `professor:No ability`, *then* the step is
    `Skip("spent")`; *and* `Gates.notSpent` uses the character's own declared label
    (`"No Ability"` capital-A characters included).
19. *Given* every character in `characters.json` with an ability containing "Once per
    game", *then* `Character.spentLabel` is non-empty **and** appears in that character's
    `reminders`.

**Actions**

20. *Given* a Shabaloth choosing two players where the first kill removes an Innkeeper,
    *then* the second target's `deathNotes` are recomputed without the Innkeeper protection.
21. *Given* a No Dashii and a Po choosing three players where the first kill changes the
    nearest-Townsfolk-neighbour, *then* `derivedPoison` is re-derived between kills.
22. *Given* a Po that chose no-one on night 2, *then* on night 3 its action is
    `ChoosePlayers(min = 3, max = 3)` and resolving it removes `po:"3 attacks"`.
23. *Given* a Demon resolving `[No kill]`, *then* a `ChoiceRecord(chosenNone = true)` is
    appended and the dawn report's `privateNotes` says the attack killed no-one.
24. *Given* a Vigormortis killing three Minions across three nights, *then* all three carry
    `vigormortis:Has ability` simultaneously (the `maxCopies` regression).
25. *Given* a `BecomeCharacter` on a seat carrying `fortuneteller:Red herring` and
    `sweetheart:Drunk`, *then* only tokens whose `sourceId` is the **abandoned** character
    are removed (`Red herring`/`Drunk` belong to other sources and stay).

**Expiry**

26. *For every* `TokenRule`, *assert* `(sourceId, label)` exists in that character's
    `reminders + remindersGlobal`, **case-sensitively** (locks in today's clean state and
    guards the ~20 additions).
27. *Given* `undertaker:Died today`, `godfather:Died today`, `zombuul:Died today`,
    `juggler:Correct`, `mathematician:Abnormal`, `acrobat:Chosen`,
    `princess:Doesn't Kill`, `barber:Haircuts tonight`, `poppygrower:Evil Wakes` placed,
    *when* dawn comes, *then* all are gone.
28. *Given* `goon:Drunk`, `organgrinder:Drunk`, `bonecollector:Has ability` placed at
    night, *then* they survive dawn and are gone after dusk.
29. *Given* a generic ST token placed from the seat sheet
    (`PlacedReminder("st", "Poisoned")`), *then* it expires at dusk; *and* no code path
    can still produce `sourceId == ""`.
30. *Given* `courtier:Drunk 3`, *then* three dawns later the seat has no Courtier token,
    passing through `Drunk 2` and `Drunk 1`.
31. *Given* `summoner:Night 1` at the start of night 1, *then* night 3's plan has
    `summoner` as `Fire` with the night-3 action, and nights 1-2 as `Skip`.
32. *Given* `flowergirl:Demon voted` placed during the day, *then* at dawn it is **reset to
    `Demon not voted`**, not deleted.

**Dawn / dusk**

33. *Given* a night where Ada died, Bea was attacked but Monk-protected, and Cai was
    resurrected, *then* `DawnReport.publicScript == ["Announce: Ada died.", "Announce: Cai
    is alive again."]` and `savedFromDeath` names Bea — *and* the Monk token is still
    present when the report is computed.
34. *Given* a Zombuul's first death, *then* `publicScript` announces the death and
    `privateNotes` records that they are secretly alive.
35. *Given* a Devil's Advocate choice and a Cerenovus madness, *then* `DayBriefing`
    contains both, and `mustCollect` contains a Gossip entry whenever a Gossip is in play.
36. *Given* it is dusk and nobody died today with a Zombuul in play, *then*
    `DuskBriefing.conditionalWakes` contains the Zombuul line.
37. *Given* `advancePhase(NIGHT → DAY)`, *then* `state.lastDawn != null` and the state
    round-trips through `Json` (extend `GameActionsTest.kt:570`).

**Chambermaid / Mathematician**

38. *Given* a night where the Exorcist chose the Shabaloth, the Chambermaid picks the
    Shabaloth and the Fool, *then* the count is **0** (the wiki's own example).
39. *Given* a Chambermaid picking two players who both woke for their own abilities, *then*
    the count is 2; *given* one of them only woke for Minion info, *then* 1.
40. *Given* fewer than two other alive players, *then* the Chambermaid step is
    `Skip("fewer than two alive players to choose")`.
41. *Given* a poisoned Empath and a Vortox-falsified Chef on the same night, *then*
    `mathematicianCount == 2`, and all `mathematician:Abnormal` tokens are cleared at dawn.
42. *Given* the Mathematician's own ability is poisoned, *then* it is **not** counted
    (wiki: *"doesn't detect their own ability failing"*).

**Guard**

43. *Given* a night whose only unticked steps are `Skip`, *then*
    `GameShell`'s unfinished list is empty and Dawn proceeds without a dialog.
44. *Given* two Village Idiots, *then* the plan has two steps with distinct
    `StepKey.token`s and ticking one does not tick the other.
