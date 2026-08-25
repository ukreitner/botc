# Djinn (djinn) — fabled Fabled

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Djinn> (fetched verbatim via
`api.php?action=parse&page=Djinn&prop=wikitext`, 2026-08-25). That page carries the
**complete, authoritative list of Djinn Special Rules** — 131 jinx rules as of this fetch.

Current ability text (verbatim summary line):

> "Use the Djinn's special rule. All players know what it is."

**Summary bullets (verbatim):**

- "Add the Djinn to all games with a jinx icon on the script. The Djinn resolves jinxes by creating a unique rule."
- "When creating a character list using the Script Tool, some character combinations will be marked as unusual. These two characters are jinxed—they have abilities that clash or contradict each other in some way. The Djinn creates a special rule that allows these characters to work well together. Some jinxed characters even work better with the Djinn in play!"
- "The Djinn's special rule is described by the Script Tool online, and is printed out automatically when you create a script with a character combination that is jinxed."
- "There are many different Djinn special rules. Each is tailored to a specific pair of jinxed characters."
- "**If there are jinxed characters on the character sheet, even if there are no jinxed characters in play, the Storyteller tells all players what the Djinn's special rule is at the start of the game.**"
- "The Djinn may have several special rules at once. If there are multiple pairs of jinxed characters on the character sheet, the players learn all the Djinn's special rules."

**How to Run (verbatim):**

> At the start of the game, if there are jinxed characters on the character sheet, declare that the Djinn is in play and inform the group of all Djinn special rules for this game. (*Do this even if there are no jinxed characters in play.*)
>
> Follow the Djinn instructions as listed on the Script Tool printout.

**Examples (verbatim):**

1. "The Pit-Hag and the Heretic are Jinxed. At the start of the game, the Storyteller reads out the Djinn's special rule: 'A Pit-Hag cannot create a Heretic.' Later in the game, the Pit-Hag tries to create a Heretic. The Storyteller shakes their head, and the Pit-Hag must choose another character to create."
2. "The Spy and the Magician are Jinxed. At the start of the game, the Storyteller reads out the Djinn's special rule: 'When the Spy sees the grimoire, the Demon and the Magician's character tokens are removed.' **There is no Spy and no Magician in play, but the Storyteller reads this aloud anyway so that the good team doesn't know which Minion is in play.**"

**Two rules that drive the whole spec:**

- Jinxes are **script-scoped, not in-play-scoped**. Every jinxed *pair on the script* is a
  Djinn rule, announced at the start, whether or not either character is dealt. Computing
  jinxes from the players' assigned characters leaks information (example 2 is explicitly
  about that leak) and hides rules the group is entitled to.
- The Djinn is **the reason** the jinx text must be right. The storyteller reads these
  aloud verbatim and then adjudicates by them. Stale text is not cosmetic here — it is the
  rule of the game for that table.

**Jinxes involving the Djinn itself:** none. `djinn` appears in no pair.
**Night order:** never wakes. Correctly absent from both order lists.

## What the app does today

Data:
- `characters.json:2171-2182` — ability text matches the wiki exactly; `team: fabled`,
  `setup: false`, no reminders, no night reminders. **Works** as data.
- `night_and_jinxes.json` — `jinxes` array with **58 entries** (`Jinx(id1, id2, reason)`,
  `Character.kt:75-81`), plus the two night-order lists. `djinn` correctly absent from both
  order lists.
- `night_guide.json` — no entry. Correct.

Code:
- `GameData.kt:23-26`
  ```kotlin
  fun activeJinxes(ids: Collection<String>): List<Jinx> {
      val set = ids.map { Character.normalizeId(it) }.toSet()
      return jinxes.filter { it.id1 in set && it.id2 in set }
  }
  ```
  A pure "both ids present" filter. There is no distinction between "on the script" and
  "in play" — the caller decides which collection to pass, and the three callers disagree:
- `ReferenceScreen.kt:52-53` — passes `resolve(script).map { it.id }`, i.e. **script-scoped**.
  This is the correct list, shown under Script → "Jinxes (N)" (`ReferenceScreen.kt:60`,
  rendered by `JinxSheet`, `ReferenceScreen.kt:196-215`). **This works and is the one place
  the Djinn's job is actually served** — but nothing in the app tells the storyteller that
  this tab is what they must read out, and nothing links it to the Djinn.
- `GameExtras.kt:200-232` `ActiveJinxesDialog` — passes
  `state.players.mapNotNull { it.characterId } + state.fabledIds`, i.e. **assigned-characters-scoped**,
  and titles itself "Jinxes in play (N)" with the empty state "No jinxed pairs among
  assigned characters." Reached from the main menu, `GameShell.kt:242-245, 502`.
- `SeatSheet.kt:222-233` — same assigned-characters scope, showing jinxes for the seat's
  character inline.
- `FabledSheet` (`GameExtras.kt:167-195`) — the Djinn is one more toggle in the list, with
  no effect on anything. Adding `"djinn"` to `state.fabledIds` feeds into
  `activeJinxes(... + state.fabledIds)` at `GameExtras.kt:207` and `SeatSheet.kt:224`,
  where it matches nothing, since no jinx pair names `djinn`.
- `NightOrder.kt:144-145` — no night step for the Djinn (not in the order lists). Correct.

**Data audit vs the wiki's Djinn Special Rules list** (131 rules parsed from the wiki
section, normalised to compact ids, compared against the 58 rows in
`night_and_jinxes.json`):

| | count |
|---|---|
| Rules on the wiki | **131** |
| Rules in the app | **58** |
| Wiki rules **missing** from the app | **80** |
| Shared pairs whose **text differs** | **38** |
| App pairs **not on the wiki list at all** | **7** |

That is: the app is missing roughly **61%** of the Djinn's rules, and of the 51 it does
share with the wiki, **38 have drifted text** — several with the rule reversed.

## Defects and gaps

1. **P0** · 80 of the 131 official Djinn rules are absent from
   `night_and_jinxes.json`. A storyteller running a script with, say, Legion + Preacher,
   Vizier + Fearmonger, Summoner + Poppy Grower, or any Plague Doctor pairing gets a silent
   "no jinxes" and adjudicates without the rule. Full list under "Missing rules" below.
   *Repro:* import any script containing `boffin` + `ogre` → Script tab → "Jinxes (0)".
2. **P0** · Several shared rules have **drifted so far that the app states the opposite of
   the current rule**:
   - `baron` × `heretic` — app: *"The Baron might only add 1 Outsider, not 2."*
     wiki: **"Only 1 jinxed character can be in play."**
   - `heretic` × `pithag` — app: *"A Pit-Hag can not create a Heretic."*
     wiki: **"Only 1 jinxed character can be in play."**
   - `heretic` × `lleech` — app: *"If the Lleech has poisoned the Heretic, and the Lleech dies, the Heretic remains poisoned."*
     wiki: **"Only 1 jinxed character can be in play."**
   - `grandmother` × `riot` — app: *"If Riot kills the Grandchild, the Grandmother dies too."*
     wiki: **"If Riot is in play and the Grandchild dies by execution, evil wins."**
   - `boffin` × `drunk` — app: *"…the Demon thinks they have been given a different not-in-play Townsfolk ability."*
     wiki: **"The Demon cannot have the Drunk ability."**
   - `plaguedoctor` × `scarletwoman` — app: *"If the Demon dies while the Storyteller has the Scarlet Woman ability, a living Minion becomes the Demon."*
     wiki: **"If the Storyteller would gain the Scarlet Woman ability, a Minion gains it, and learns this."**
   - `courtier` × `summoner` — app: *"If the Summoner is drunk on the 3rd night, the Summoner chooses which Demon, but the Storyteller chooses which player becomes that Demon."*
     wiki: **"If the living Summoner has no ability, the Storyteller has the Summoner ability."**
   - `alchemist` × `summoner` — app: *"The Alchemist can not have the Summoner ability."*
     wiki: **"The Alchemist-Summoner does not get bluffs, and chooses which Demon but not which player. If they die before this happens, evil wins. [No Demon]"**
   - the four Leviathan/Riot "wakes to use their ability" rows (`ravenkeeper`, `sage`,
     `farmer`, and Riot's) are now expressed as a nightly Leviathan/Riot **choice**:
     e.g. wiki `leviathan` × `ravenkeeper`: **"Each night*, the Leviathan chooses an alive player (different to previous nights): a chosen Ravenkeeper uses their ability but does not die."**
   - the three Marionette placement rows (`kazali`, `lilmonsta`, `summoner`) have been
     consolidated on the wiki to **"If there would be a Marionette in play, they enter play after the Demon & must start as their neighbor."**
   The remaining ~25 differences are wording-level but should still be re-synced, since the
   storyteller reads them verbatim.
3. **P1** · The "Jinxes in play" dialog computes the **wrong scope** and its title says so.
   `GameExtras.kt:207` uses assigned characters, so a script whose Pit-Hag/Heretic pair
   wasn't dealt shows "Jinxes in play (0)" — exactly the case wiki example 2 warns about
   ("the Storyteller reads this aloud anyway so that the good team doesn't know which
   Minion is in play"). *Repro:* import a script with Spy and Magician, deal a bag with
   neither → menu → "Jinxes in play" → "No jinxed pairs among assigned characters."
4. **P1** · The Djinn is never auto-added. The wiki says "Add the Djinn to all games with a
   jinx icon on the script"; `GameActions.newGame` (`GameActions.kt:11-16`) starts with an
   empty `fabledIds` and never inspects the script's jinxes.
5. **P1** · Nothing prompts the pre-game announcement. There is no setup-time surface that
   says "read these N rules to the group", and no way to show them to the table —
   `ShowCards.kt:65-77` has no text-list card, so the storyteller reads from the Script tab
   on their own phone, scrolling, while the group waits.
6. **P1** · The Djinn's presence and rules are not shown to the table anywhere. The Script
   tab is the storyteller's reference; the show-card tool
   (`ShowCards.kt`, `ShowToolSheet`) has `SheetCard(characterIds)` for a character sheet
   and nothing for a jinx list.
7. **P2** · 7 rows in `night_and_jinxes.json` name pairs that do not appear in the wiki's
   current Djinn Special Rules list at all: `damsel`×`marionette`,
   `marionette`×`poppygrower`, `marionette`×`snitch`, `gambler`×`lycanthrope`,
   `riot`×`saint`, `poisoner`×`summoner`, `choirboy`×`kazali`. These may be retired jinxes
   (folded into the consolidated Marionette rule, or into base character text) or wiki
   lag — each needs verification before removal, but the app currently announces them as
   Djinn rules.
8. **P2** · The dataset has no provenance, no version and no fetch date. There is no way to
   tell that `night_and_jinxes.json` is a 2023-era community snapshot
   (it matches `bra1n/townsquare` `src/hatred.json` for 39 pairs) rather than the current
   official list. No test asserts a jinx count or spot-checks a rule.
9. **P2** · Jinx text is not surfaced where it is adjudicated. A Pit-Hag night step
   (`NightScreen.StepDetailPanel`, `NightScreen.kt:770-833`) shows the night guide but not
   "Djinn rule: only 1 jinxed character can be in play" — the storyteller has to remember
   the rule exists and go look it up in another tab, mid-night.
10. **P3** · `ActiveJinxesDialog` passes `state.fabledIds` into `activeJinxes`
    (`GameExtras.kt:207`), which is dead code: no jinx pair names a Fabled. Harmless but
    misleading about intent.

### Missing rules (80) — verbatim from the wiki, ready to paste into `night_and_jinxes.json`

Ids are the app's normalised form (`Character.normalizeId`).

**Townsfolk**
- `alchemist` × `boffin` — "If the Alchemist has the Boffin ability, the Alchemist does not learn what ability the Demon has."
- `alchemist` × `marionette` — "An Alchemist-Marionette has no Marionette ability & the Marionette is in play."
- `alchemist` × `mastermind` — "An Alchemist-Mastermind has no Mastermind ability & the Mastermind is not-in-play."
- `alchemist` × `organgrinder` — "If the Alchemist has the Organ Grinder ability, the Organ Grinder is in play. If both are sober, both are drunk."
- `alchemist` × `spy` — "An Alchemist-Spy has no Spy ability & a Spy is in play. After each execution, a living Alchemist-Spy may publicly guess a living player as the Spy. If correct, the Demon must choose the Spy tonight."
- `alchemist` × `wraith` — "An Alchemist-Wraith has no Wraith ability & a Wraith is in play. After each execution, a living Alchemist-Wraith may publicly guess a living player as the Wraith. If correct, the Demon must choose the Wraith tonight."
- `alchemist` × `widow` — "An Alchemist-Widow has no Widow ability & a Widow is in play. After each execution, a living Alchemist-Widow may publicly guess a living player as the Widow. If correct, the Demon must choose the Widow tonight."
- `cannibal` × `princess` — "If the Cannibal nominated, executed, & killed the Princess today, the Demon doesn't kill tonight."
- `mathematician` × `drunk` — "The Mathematician learns if the Drunk's ability yielded false info or failed to work properly."
- `mathematician` × `marionette` — "The Mathematician learns if the Marionette's ability yielded false info or failed to work properly."
- `magician` × `legion` — "If the Magician is in play, during the Demon info step, Legion wake in separate groups. Each group learns which players are good, but does not learn the Magician." *(wiki text has a stray leading "T")*
- `magician` × `marionette` — "If the Magician is alive, the Demon doesn't know which neighbor is the Marionette."
- `magician` × `vizier` — "If the Vizier is in play, the Magician has no ability but is immune to the Vizier's ability."
- `magician` × `wraith` — "After each execution, the living Magician may publicly guess a living player as the Wraith. If correct, the Demon must choose the Wraith tonight."

**Outsiders**
- `plaguedoctor` × `boomdandy` — "If the Storyteller would gain the Boomdandy ability, a player becomes the Boomdandy."
- `plaguedoctor` × `eviltwin` — "If the Storyteller would gain the Evil Twin ability, a player becomes the Evil Twin."
- `plaguedoctor` × `fearmonger` — "If the Storyteller would gain the Fearmonger ability, a Minion gains it, and learns this."
- `plaguedoctor` × `goblin` — "If the Storyteller would gain the Goblin ability, a Minion gains it, and learns this."
- `plaguedoctor` × `marionette` — "If the Storyteller would gain the Marionette ability, one of the Demon's good neighbors becomes the Marionette."
- `plaguedoctor` × `spy` — "If the Storyteller would gain the Spy ability, a Minion gains it, and learns this."
- `plaguedoctor` × `wraith` — "If the Storyteller would gain the Wraith ability, a Minion gains it, and learns this."
- `recluse` × `ogre` — "If the Recluse registers as evil to the Ogre, the Ogre learns that they are evil."
- `recluse` × `sage` — "The Recluse might register as the Demon to the Sage."

**Minions**
- `boffin` × `goon` — "If the Demon has the Goon ability, they can't turn good due to this ability."
- `boffin` × `ogre` — "The Demon cannot have the Ogre ability."
- `boffin` × `politician` — "The Demon cannot have the Politician ability."
- `boffin` × `villageidiot` — "If there is a spare token, the Boffin can give the Demon the Village Idiot ability."
- `mastermind` × `vigormortis` — "A Mastermind that has their ability keeps it if the Vigormortis dies."
- `pithag` × `goon` — "If the Pit-Hag turns an evil player into the Goon, they can't turn good due to their own ability."
- `pithag` × `ogre` — "If the Pit-Hag turns an evil player into the Ogre, they can't turn good due to their own ability."
- `pithag` × `politician` — "If the Pit-Hag turns an evil player into the Politician, they can't turn good due to their own ability."
- `pithag` × `villageidiot` — "If there is a spare token, the Pit-Hag can create an extra Village Idiot. If so, the drunk Village Idiot might change."
- `spy` × `ogre` — "The Spy registers as evil to the Ogre."
- `summoner` × `clockmaker` — "The Summoner registers as the Demon to the Clockmaker."
- `summoner` × `engineer` — "If the living Summoner is removed from play, the Storyteller has the Summoner ability."
- `summoner` × `hatter` — "If the Summoner creates a second living Demon, deaths tonight are arbitrary."
- `summoner` × `kazali` — "If the Summoner creates a second living Demon, deaths tonight are arbitrary."
- `summoner` × `lordoftyphon` — "If a Lord of Typhon is summoned, they must neighbor a Minion & their other neighbor becomes an evil Minion."
- `summoner` × `pithag` — "If the Summoner creates a second living Demon, deaths tonight are arbitrary."
- `summoner` × `poppygrower` — "If the Poppy Grower is alive on the 3rd night, the Summoner chooses which Demon but not which player."
- `summoner` × `preacher` — "If the living Summoner has no ability, the Storyteller has the Summoner ability."
- `summoner` × `pukka` — "The Summoner may summon a Pukka on the 2nd night instead of the 3rd."
- `summoner` × `zombuul` — "If the Summoner summons a dead player into the Zombuul, the Zombuul has already 'died once'."
- `vizier` × `alsaahir` — "The Storyteller doesn't declare the Vizier is in play."
- `vizier` × `courtier` — "If the Vizier loses their ability, they learn this, and cannot die during the day."
- `vizier` × `fearmonger` — "The Vizier wakes with the Fearmonger, learns who they choose and cannot choose to immediately execute that player."
- `vizier` × `investigator` — "The Storyteller doesn't declare the Vizier is in play."
- `vizier` × `politician` — "The Politician might register as evil to the Vizier."
- `vizier` × `preacher` — "If the Vizier loses their ability, they learn this, and cannot die during the day."
- `vizier` × `zealot` — "The Zealot might register as evil to the Vizier."

**Demons**
- `alhadikhia` × `princess` — "If the Princess nominated & executed a player on their 1st day, no one dies to the Al-Hadikhia tonight."
- `alhadikhia` × `mastermind` — "If the Al-Hadikhia dies by execution, and the Mastermind is alive, the Al-Hadikhia chooses 3 good players tonight: if all 3 choose to live, evil wins. Otherwise, good wins."
- `legion` × `engineer` — "If Legion is created, all evil players become Legion. If Legion is in play, the Engineer starts knowing this but has no ability."
- `legion` × `hatter` — "If Legion is created, all evil players become Legion. If Legion is in play, the Hatter has no ability."
- `legion` × `minstrel` — "If Legion died by execution today, Legion keeps their ability, but the Minstrel might learn they are Legion."
- `legion` × `politician` — "The Politician might register as evil to Legion."
- `legion` × `preacher` — "If the Preacher chooses Legion, Legion keeps their ability, but the Preacher might learn they are Legion."
- `legion` × `summoner` — "If Legion is summoned, all evil players become Legion."
- `legion` × `zealot` — "The Zealot might register as evil to Legion."
- `leviathan` × `banshee` — "Each night*, the Leviathan chooses an alive good player (different to previous nights): a chosen Banshee dies & gains their ability."
- `leviathan` × `exorcist` — "If the Leviathan nominates and executes the Exorcist-chosen player, good wins."
- `leviathan` × `grandmother` — "If the Leviathan is in play and the Grandchild dies by execution, evil wins."
- `leviathan` × `hatter` — "The Leviathan cannot enter play after day 5."
- `leviathan` × `innkeeper` — "If the Leviathan nominates and executes an Innkeeper-protected player, good wins."
- `leviathan` × `king` — "If the Leviathan is in play, and at least 1 player is dead, the King learns an alive character each night."
- `leviathan` × `monk` — "If the Leviathan nominates and executes the Monk-protected player, good wins."
- `leviathan` × `pithag` — "The Leviathan cannot enter play after day 5."
- `leviathan` × `soldier` — "If the Leviathan nominates and executes the Soldier, good wins."
- `lilmonsta` × `hatter` — "If the Hatter dies & the Demon chooses Lil' Monsta, they also choose a Minion to become."
- `lilmonsta` × `psychopath` — "If the Psychopath is babysitting Lil' Monsta, they die when executed."
- `lilmonsta` × `vizier` — "If the Vizier is babysitting Lil' Monsta, they die when executed."
- `lleech` × `mastermind` — "If the Mastermind is alive and the Lleech host dies by execution, the Lleech lives but loses their ability."
- `riot` × `atheist` — "During a riot, if the Storyteller is nominated, players vote. If they are 'about to die', the game ends. If not, they nominate again."
- `riot` × `banshee` — "Each night*, Riot chooses an alive good player (different to previous nights): a chosen Banshee dies & gains their ability."
- `riot` × `exorcist` — "If Riot nominates and executes the Exorcist-chosen player, good wins."
- `riot` × `innkeeper` — "If Riot nominates and executes an Innkeeper-protected player, good wins."
- `riot` × `king` — "If Riot is in play, and at least 1 player is dead, the King learns an alive character each night."
- `riot` × `mayor` — "The Mayor may choose to stop the riot. If they do so when only 1 Riot is alive, good wins. Otherwise, evil wins."
- `riot` × `monk` — "If Riot nominates and executes the Monk-protected player, good wins."
- `riot` × `soldier` — "If Riot nominates and executes the Soldier, good wins."

*(Two of these — `riot` × `atheist` and, in the drift list, `leviathan` × `mayor` — are
already specced by other auditors; see `docs/audit/characters/atheist.md`.)*

## Proposed behaviour (spec)

Shares the `FabledEntry` storage introduced in `angel.md`.

- when: never wakes. No night-order entry.
- **`GameData.activeJinxes` must be split into two named functions** so callers cannot pick
  the wrong scope by accident:
  ```kotlin
  /** Every Djinn rule for this script — what the storyteller reads out. */
  fun scriptJinxes(script: Script): List<Jinx>
  /** Jinxes whose BOTH characters are currently assigned — for seat-level hints only. */
  fun assignedJinxes(state: GameState): List<Jinx>
  ```
  `ReferenceScreen.kt:52-53` uses `scriptJinxes`. `GameExtras.ActiveJinxesDialog` is
  retitled **"Djinn rules (N)"**, uses `scriptJinxes`, and marks each row with a small
  "in play" badge when both characters are assigned. `SeatSheet.kt:222-233` keeps
  `assignedJinxes` (it is a per-seat hint, not the announcement) but its text should read
  "Djinn rule with <partner>: …".
- automatic presence: on `newGame` and on script change, if `scriptJinxes(script)` is
  non-empty, add `FabledEntry("djinn")` and render it in `FabledSheet` as
  "In play — this script has N jinxes" (removable, per the wiki's "Add the Djinn to all
  games with a jinx icon", which is advice rather than a hard lock like the Bootlegger's).
- setup announcement gate (shared with the Bootlegger — one dialog, two sections; see
  `bootlegger.md`): when advancing SETUP→NIGHT with `djinn` active, first show

  > **Djinn — read these to the group before the bag goes round**
  > N special rules apply this game, whether or not the characters are in play.
  > • Pit-Hag / Heretic — Only 1 jinxed character can be in play.
  > • Spy / Magician — When the Spy sees the Grimoire, the Demon and Magician's character tokens are removed.
  > …
  > [ Show the group ]  [ Read out — begin night ]  [ Later ]

  "Show the group" opens a new `ShowCard.RulesCard(List<Pair<String,String>>)` rendering the
  pairs full-screen at readable size (`ShowCards.kt:65-77` gains one variant).
- in-play adjudication (fixes defect 9): `NightStepRow` (`NightScreen.kt:690-767`) appends
  to `step.detail`, for each assigned jinx touching that step's character:
  `"DJINN: <reason>"` in the same style as the Exorcist annotation
  (`NightOrder.kt:150-154`). Same for `SeatSheet` and for `nominationWarnings`
  (`StatusEffects.kt:131-166`) where a jinx changes a nomination outcome (the Riot and
  Leviathan nomination rules above).
- tokens / expiry / info / visibility: none. The Djinn places nothing and computes nothing.
- day-time inputs: none.

**Data changes — this is the bulk of the work:**
1. Replace `night_and_jinxes.json`'s `jinxes` array with the 131 rules from the wiki's
   Djinn Special Rules section, verbatim, ids normalised via `Character.normalizeId`.
2. Add provenance to the file: `"jinxSource": "wiki.bloodontheclocktower.com/Djinn"`,
   `"jinxFetchedAt": "<ISO date>"`, `"jinxCount": 131` (`NightAndJinxes`,
   `GameData.kt:92-97`, gains the fields with defaults so old saves still parse).
3. Verify then keep-or-drop the 7 app-only pairs listed in defect 7.
4. Consider a small refresh script under `tools/` that re-parses the wiki section and
   diffs it against the bundled file, so this never silently rots again.

**UI text:**
- Menu item: "Djinn rules" (was "Jinxes in play").
- Empty state: "This script has no jinxes — the Djinn is not needed."
- Setup banner: "Djinn in play — 4 special rules to read out."

## Tests to add

1. `jinx dataset matches the published count`
   Given the bundled dataset, Then `gameData.jinxes.size == 131` and the file declares its
   source and fetch date. *(Fails today: 58, no provenance.)*
2. `spot-check the corrected rules`
   Given `gameData`, Then the reason for (`baron`,`heretic`) is exactly
   `"Only 1 jinxed character can be in play."`, and (`grandmother`,`riot`) is
   `"If Riot is in play and the Grandchild dies by execution, evil wins."`
   *(Both fail today.)*
3. `every jinx names two known characters`
   For every jinx, `gameData.character(id1)` and `gameData.character(id2)` are non-null.
   (Cheap guard against typos when the 131 rows land.)
4. `script jinxes ignore who was dealt`
   Given a script containing `spy` and `magician` and a bag containing neither,
   Then `scriptJinxes(script)` contains that pair.
   *(Fails today via `ActiveJinxesDialog`, which passes assigned characters,
   `GameExtras.kt:207`.)*
5. `assigned jinxes require both characters in play`
   Given the same script with only the Spy dealt,
   Then `assignedJinxes(state)` does not contain the pair, while `scriptJinxes` does.
6. `djinn is auto-added for a jinxed script`
   Given a script with at least one jinx, When `newGame` runs,
   Then `state.fabled.map { it.id }` contains `"djinn"`; and for a jinx-free script it does
   not.
7. `jinx annotations reach the night step`
   Given `pithag` assigned on a script that also lists `heretic`,
   Then the Pit-Hag night step's detail contains `"DJINN:"` and the jinx reason.
8. `djinn adds no night step`
   Given `fabled = [djinn]`, Then neither night order contains a `"djinn"` step.
   *(Passes today — regression guard.)*
