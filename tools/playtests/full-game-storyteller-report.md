# Full-game storyteller playtest report

Date: 2026-07-25
Scope: three deterministic 15-player games, one per bundled base script
Canonical executable fixture: `engine/src/test/kotlin/com/clocktower/engine/FullGamePlaytestTest.kt`

## Method

Each game starts from the bundled `Script` and a legal 15-character bag. The
fixture assigns every seat through `GameActions`, moves every reminder through
the real reminder APIs, records every nomination (including ordered
`voterIds`), spends dead votes, kills/revives through the engine, advances every
phase, covers every generated `NightStep` in order, calls `InfoCalc` wherever
supported, and checks the final `WinCheck` advisory.

The prose below is the storyteller transcript. The Kotlin fixture is the
machine-checkable source of truth for exact state, targets, reminders, deaths,
and results.

## Verification

- `FullGamePlaytestTest`: **5 tests, 0 failures**.
- Complete engine suite: **81 tests, 0 failures**.
- Standalone Compose UI typecheck: **passed**.
- Android `:app:assembleDebug`: **passed**.
- Because the checkout is inside OneDrive and Kotlin's mapped caches were
  intermittently locked, verification used
  `tools/playtests/isolated-build.init.gradle` to place build output under a
  temporary local directory. No production behavior depends on that helper.

---

## Game 1: Trouble Brewing — “The poisoned circle”

### Setup and bag

The Baron changes the normal 15-player distribution from 9/2/3/1 to
7 Townsfolk, 4 Outsiders, 3 Minions, 1 Demon. `validateBag` must accept it.

| Seat | Player | Actual character | Alignment | Identity shown |
|---:|---|---|---|---|
| 1 | Alice | Imp | Evil | Imp |
| 2 | Ben | Empath | Good | Empath |
| 3 | Cara | Poisoner | Evil | Poisoner |
| 4 | Diego | Fortune Teller | Good | Fortune Teller |
| 5 | Eve | Drunk | Good | Washerwoman |
| 6 | Finn | Investigator | Good | Investigator |
| 7 | Grace | Baron | Evil | Baron |
| 8 | Hugo | Butler | Good | Butler |
| 9 | Iris | Virgin | Good | Virgin |
| 10 | Jonas | Spy | Evil | Spy |
| 11 | Kira | Monk | Good | Monk |
| 12 | Liam | Recluse | Good | Recluse |
| 13 | Maya | Undertaker | Good | Undertaker |
| 14 | Noah | Saint | Good | Saint |
| 15 | Opal | Mayor | Good | Mayor |

Initial markers: Eve `Drunk / Is the Drunk`; Opal `Fortune Teller / Red
herring`. Demon bluffs: Chef, Soldier, Librarian.

### Night 1 → Day 1

Night order and actions:

1. Dusk; all 15 accounted for.
2. Minion info: Cara, Grace, and Jonas saw one another and Alice.
3. Demon info: Alice saw the three Minions and Chef/Soldier/Librarian.
4. Poisoner: Cara poisoned Ben.
5. Investigator: Finn saw Poisoner; pointed to Cara and Kira.
6. Empath: true answer was **2** (Alice and Cara); poisoned Ben saw **0**.
7. Fortune Teller: Alice + red-herring Opal produced **YES**.
8. Butler: Hugo chose Maya as Master.
9. Spy: Jonas saw the grimoire.
10. Manual Drunk wake: Eve, believing Washerwoman, saw Monk and Kira/Opal.
11. Dawn: no deaths.

Day 1 nomination:

| Nominator | Nominee | Clockwise voters | Votes/result |
|---|---|---|---|
| Ben | Iris | none; Virgin interrupted voting | Withdrawn |

Virgin fired because Ben was Townsfolk. Ben was executed immediately; Iris got
`No ability`. This was the day's execution. Phase advanced to Night 2.

### Night 2 → Day 2

1. Dusk; Ben dead, ghost vote unused.
2. Poisoner: poison moved from Ben to Maya.
3. Monk: Kira protected Noah (`Safe`).
4. Imp: Alice killed Kira.
5. Empath: dead Ben skipped.
6. Fortune Teller: Cara + Jonas produced true **NO**, with Spy
   misregistration caveat.
7. Undertaker: true answer for Ben was Empath; poisoned Maya saw Chef.
8. Butler: Master moved to Diego.
9. Spy: Jonas saw the updated grimoire.
10. Dawn: Kira announced dead.

Day 2 nominations (13 alive, threshold 7):

| Nominator → nominee | Clockwise voters | Total/result |
|---|---|---|
| Noah → Grace | Noah, Opal, Liam, Hugo, Iris, Finn | 6, safe |
| Maya → Cara | Maya, Diego, Eve, Finn, Hugo, Iris, Noah, Opal | 8, on block |
| Jonas → Alice | Alice, Cara, Grace, Jonas, Liam, Maya, Noah, Opal | 8, tie; block cleared |
| Liam → Jonas | Ben†, Liam, Maya, Noah, Opal, Diego, Eve, Finn, Hugo | 9, on block |

Ben spent his single ghost vote. Jonas was executed. Phase advanced to Night 3.

### Night 3 → Day 3

1. Dusk; Ben, Kira, Jonas dead.
2. Poisoner: poison moved to Diego.
3. Monk: dead Kira skipped.
4. Imp: Alice killed Finn.
5. Empath: dead Ben skipped.
6. Fortune Teller: Alice + Noah had true **YES**; poisoned Diego saw **NO**.
7. Undertaker: Maya correctly saw Spy for executed Jonas.
8. Butler: Hugo retained Diego as Master.
9. Spy: dead Jonas skipped.
10. Dawn: Finn announced dead.

Day 3 nominations (11 alive, threshold 6):

| Nominator → nominee | Clockwise voters | Total/result |
|---|---|---|
| Cara → Noah | Cara, Grace, Alice, Hugo, Iris, Liam | 6, on block |
| Opal → Cara | Opal, Maya, Diego, Eve, Hugo, Iris, Liam | 7, new block |

Cara was executed. Her poison had to end. Phase advanced to Night 4.

### Night 4 → Day 4

1. Dusk: dead Poisoner's lingering marker was explicitly removed.
2. Poisoner and Monk: dead, skipped.
3. Imp: Alice killed Maya before the Undertaker row.
4. Empath: dead, skipped.
5. Fortune Teller: sober Diego chose Alice + Noah and saw true **YES**.
6. Undertaker: Maya had died earlier in the night; skipped.
7. Butler: Hugo chose Opal as Master.
8. Spy: dead, skipped.
9. Dawn: Maya announced dead.

Day 4 nominations (9 alive, threshold 5):

| Nominator → nominee | Clockwise voters | Total/result |
|---|---|---|
| Noah → Alice | Noah, Opal, Diego, Eve, Hugo | 5, on block |
| Alice → Noah | Alice, Grace, Liam, Iris | 4, safe |

Alice was executed. All Demons were dead and no Scarlet Woman was in play.
**Good won on Day 4.**

---

## Game 2: Sects & Violets — “The dead Cerenovus”

### Setup and bag

Vigormortis removes one Outsider, producing
10 Townsfolk, 1 Outsider, 3 Minions, 1 Demon.

| Seat | Player | Character | Alignment |
|---:|---|---|---|
| 1 | Ada | Vigormortis | Evil |
| 2 | Beau | Dreamer | Good |
| 3 | Cleo | Witch | Evil |
| 4 | Dax | Clockmaker | Good |
| 5 | Esme | Cerenovus | Evil |
| 6 | Farah | Oracle | Good |
| 7 | Gio | Pit-Hag | Evil |
| 8 | Hana | Flowergirl | Good |
| 9 | Ivan | Sweetheart | Good |
| 10 | Juno | Town Crier | Good |
| 11 | Kai | Sage | Good |
| 12 | Luz | Seamstress | Good |
| 13 | Milo | Snake Charmer | Good |
| 14 | Nia | Savant | Good |
| 15 | Otis | Mathematician | Good |

Demon bluffs: Artist, Juggler, Mutant.

### Night 1 → Day 1

1. Minions Cleo/Esme/Gio saw one another and Ada; Ada received bluffs.
2. Snake Charmer: Milo chose Farah, no swap.
3. Witch: Cleo cursed Juno.
4. Cerenovus: Esme made Ivan mad as Mutant.
5. Clockmaker: Dax learned **2**.
6. Dreamer: Beau chose Ada and saw Vigormortis + Artist.
7. Seamstress: Luz used her ability on Beau/Cleo and learned **NO**; marked
   `No ability`.
8. Mathematician: Otis saw **0**.
9. Dawn: no deaths.

Day 1:

| Nominator → nominee | Clockwise voters | Total/result |
|---|---|---|
| Juno → Ivan | Ada, Beau, Dax, Farah, Hana, Kai | 6, safe; Juno died and voting continued |
| Ivan → Cleo | Ivan, Ada, Beau, Dax, Farah, Hana, Kai | 7, on block |
| Cleo → Beau | Cleo, Esme, Gio, Luz, Milo, Nia | 6, safe |
| Dax → Esme | Dax, Beau, Farah, Hana, Kai, Luz | 6, safe |

Cleo was executed. Phase advanced to Night 2.

### Night 2 → Day 2

1. Snake Charmer: Hana, no swap.
2. Dead Witch had no retained ability and was skipped.
3. Cerenovus: Esme made Kai mad as Artist.
4. Pit-Hag: no change.
5. Vigormortis: Ada killed Esme. Esme received `Has ability`; Farah received
   Vigormortis `Poisoned`.
6. Sage/Sweetheart: alive, conditional rows skipped.
7. Dreamer: Beau chose Gio and saw Pit-Hag + Savant.
8. Flowergirl: Ada voted on Day 1, so true **YES**.
9. Town Crier: dead Juno skipped; true answer was **YES** because Cleo
   nominated.
10. Oracle: Cleo and Esme were dead evil; poisoned Farah saw **1** instead of
    true **2**.
11. Seamstress spent; Mathematician saw **1** malfunction.
12. Dawn: Esme announced dead.

Day 2 (12 alive, threshold 6):

| Nominator → nominee | Clockwise voters | Total/result |
|---|---|---|
| Kai → Ivan | Kai, Beau, Dax, Farah, Hana, Luz | 6, on block |
| Hana → Ada | Hana, Ada, Beau, Dax, Milo | 5, safe |
| Gio → Kai | Gio, Esme†, Farah, Luz, Nia | 5, safe |

Esme spent a ghost vote. Ivan was executed; Sweetheart needed a permanent
drunk selection. Phase advanced to Night 3.

### Night 3 → Day 3

1. Snake Charmer: Dax, no swap.
2. Witch dead/skipped.
3. Dead-but-retained Cerenovus: Esme made Beau mad as Artist.
4. Pit-Hag: Gio changed Nia from Savant to Barber.
5. Vigormortis: Ada killed Kai.
6. Sweetheart: Beau became permanently drunk.
7. Sage: Kai saw Ada + Milo, one the Demon.
8. Dreamer: Beau's true Ada result was replaced with Witch + Clockmaker.
9. Flowergirl true **YES**; dead Town Crier's true result **YES**.
10. Oracle true **2**, poisoned result **3**.
11. Mathematician saw **2** (Dreamer and Oracle malfunctions).
12. Dawn: Kai announced dead.

Day 3 (10 alive, threshold 5):

| Nominator → nominee | Clockwise voters | Total/result |
|---|---|---|
| Beau → Gio | Beau, Ada, Dax, Farah, Hana | 5, on block |
| Ada → Beau | Ada, Gio, Hana, Milo | 4, safe |
| Milo → Ada | Milo, Luz, Nia, Otis | 4, safe |

Gio was executed. Nia remained Barber. Phase advanced to Night 4.

### Night 4 → Day 4

1. Snake Charmer: Otis, no swap.
2. Witch dead/skipped.
3. Retained Cerenovus: Esme made Farah mad as Artist.
4. Dead Pit-Hag skipped.
5. Vigormortis: Ada killed Hana before Flowergirl information.
6. Living Barber had no death trigger; Sweetheart/Sage already resolved.
7. Drunk Dreamer received a false pair for Milo.
8. Dead Flowergirl and Town Crier skipped; Town Crier's true answer was NO.
9. Oracle true **3** dead evil, poisoned result **2**.
10. Mathematician saw **2**.
11. Dawn: Hana announced dead.

Day 4 (8 alive, threshold 4):

| Nominator → nominee | Clockwise voters | Total/result |
|---|---|---|
| Farah → Ada | Farah, Beau, Dax, Luz | 4, on block |
| Ada → Farah | Ada, Milo, Otis | 3, safe |

Ada was executed. **Good won on Day 4.**

---

## Game 3: Bad Moon Rising — “The Mastermind's extra day”

### Setup and bag

Normal 15-player distribution: 9 Townsfolk, 2 Outsiders, 3 Minions, 1 Demon.

| Seat | Player | Actual character | Alignment | Identity shown |
|---:|---|---|---|---|
| 1 | Aurora | Po | Evil | Po |
| 2 | Blake | Lunatic | Good | Po |
| 3 | Cora | Devil's Advocate | Evil | Devil's Advocate |
| 4 | Dorian | Chambermaid | Good | Chambermaid |
| 5 | Elena | Assassin | Evil | Assassin |
| 6 | Felix | Courtier | Good | Courtier |
| 7 | Greta | Grandmother | Good | Grandmother |
| 8 | Hector | Mastermind | Evil | Mastermind |
| 9 | Inez | Innkeeper | Good | Innkeeper |
| 10 | Jasper | Moonchild | Good | Moonchild |
| 11 | Kendra | Exorcist | Good | Exorcist |
| 12 | Leo | Fool | Good | Fool |
| 13 | Mina | Gambler | Good | Gambler |
| 14 | Nate | Gossip | Good | Gossip |
| 15 | Olive | Professor | Good | Professor |

Po bluffs: Tea Lady, Sailor, Pacifist. Blake was shown three fake Minions and
Chef/Empath/Mayor.

### Night 1 → Day 1

Courtier made Assassin drunk for three nights; Devil's Advocate protected
Hector; Greta learned Mina/Gambler and marked Grandchild; Chambermaid chose
Cora + Leo and saw **1**. No deaths.

Day 1:

| Nominator → nominee | Clockwise voters | Total/result |
|---|---|---|
| Blake → Cora | Blake, Aurora, Dorian, Felix, Greta, Inez, Jasper | 7, safe |
| Cora → Jasper | Cora, Aurora, Elena, Hector, Inez, Kendra, Leo, Nate | 8, block |
| Mina → Elena | Mina, Blake, Dorian, Felix, Greta, Inez, Olive, Nate | 8, tie |
| Greta → Mina | Greta, Dorian, Felix, Inez, Jasper, Kendra, Leo, Nate, Olive | 9, block |

Mina was executed.

### Night 2 → Day 2

1. Innkeeper protected Greta/Olive and made Greta drunk.
2. Courtier countdown: Assassin `Drunk 2`.
3. Dead Gambler skipped.
4. Devil's Advocate protected Aurora.
5. Lunatic Blake chose nobody and believed he charged.
6. Exorcist chose Hector, not Demon.
7. Po Aurora chose nobody and charged.
8. Drunk Assassin declined.
9. True Gossip killed Dorian.
10. Professor revived Townsfolk Mina and spent the ability.
11. Other conditional rows skipped; dawn announced Dorian dead.

Day 2 (14 alive, threshold 7):

| Nominator → nominee | Clockwise voters | Total/result |
|---|---|---|
| Olive → Jasper | Olive, Aurora, Blake, Felix, Greta, Inez, Kendra | 7, block |
| Aurora → Cora | Aurora, Elena, Hector, Inez, Leo, Mina | 6, safe |
| Hector → Kendra | Hector, Blake, Felix, Greta, Nate, Olive | 6, safe |

Jasper was executed and selected good Fool Leo.

### Night 3 → Day 3

1. Innkeeper protected Kendra/Olive; Olive drunk.
2. Assassin countdown: `Drunk 1`.
3. Mina guessed Greta/Grandmother correctly.
4. Devil's Advocate protected Aurora.
5. Lunatic spent his fake charge on Cora/Greta/Nate.
6. Exorcist chose Cora, not Demon.
7. Po attacked Greta, Nate, and protected Kendra. Greta and Nate died;
   Kendra's `Protected` warning was verified and she survived.
8. Assassin skipped while drunk.
9. Moonchild targeted good Leo. `Fool` first-death warning was verified;
   Leo survived and got `No ability`.
10. Dawn: Greta and Nate announced dead.

Day 3 (11 alive, threshold 6):

| Nominator → nominee | Clockwise voters | Total/result |
|---|---|---|
| Kendra → Cora | Kendra, Aurora, Blake, Felix, Inez, Mina | 6, block |
| Elena → Olive | Elena, Hector, Inez, Leo, Mina | 5, safe |
| Hector → Aurora | Hector, Blake, Felix, Leo, Olive | 5, safe |

Cora was executed.

### Night 4 → Day 4

Innkeeper protected Blake/Leo and made Blake drunk; Courtier expired, making
Assassin sober; Mina guessed correctly; dead Devil's Advocate skipped; Kendra
selected Aurora, and the generated Po row explicitly displayed the Exorcist
silence. Aurora did not act. Elena spent Assassin to kill Olive before the
Professor row. Dawn announced Olive dead.

Day 4 (9 alive, threshold 5):

| Nominator → nominee | Clockwise voters | Total/result |
|---|---|---|
| Mina → Aurora | Mina, Blake, Felix, Inez, Kendra | 5, block |
| Aurora → Mina | Aurora, Elena, Hector, Leo | 4, safe |
| Hector → Elena | Hector, Blake, Felix, Inez | 4, safe |

Aurora was executed. The engine advised good victory but did include a
Mastermind caution. Hector was alive, so the game correctly continued.

### Mastermind Night 5 → Day 5

Innkeeper protected Mina/Kendra and made Kendra drunk; Mina correctly guessed
Hector/Mastermind; Blake sent a fake Po choice that had no living Demon to
receive it; drunk Kendra chose Blake; dead Po and spent/dead conditional rows
were skipped. No deaths.

Final day (8 alive, threshold 4):

| Nominator → nominee | Clockwise voters | Total/result |
|---|---|---|
| Hector → Mina | Hector, Blake, Elena, Felix | 4, block |
| Mina → Hector | Mina, Inez, Kendra | 3, safe |

Mina was executed. On the Mastermind continuation, the executed player's team
loses. **Evil won on Day 5.**

---

## What the app still cannot represent faithfully

### Critical/high-impact

1. **Mastermind continuation has no state.** `WinCheck` can only keep returning
   “all Demons dead; good wins” with a caution. It cannot mark the extra day as
   pending/resolved or determine the final losing team. A dedicated
   end-condition state/event is needed.
2. **Resurrection erases death history.** `GameActions.revive` removes the most
   recent `DeathRecord`. The storyteller transcript must separately remember
   that Mina was executed and then revived. A durable event log should record
   both death and resurrection.
3. **No structured action history.** Night targets, false information shown,
   madness claims, Po charge, conditional non-deaths, and storyteller rulings
   are only inferable from current reminders/notes. The executable fixture
   needs its own narrative event objects because `GameState` stores only final
   reminders, nominations, deaths, and checklist ids.

### Medium-impact / cumbersome

4. **Conditional dead rows are noisy.** The night sheet retains all dead role
   rows, which is useful for Ravenkeeper/Sage and Vigormortis-retained Minions,
   but forces repeated manual “dead, skip” decisions. It does not distinguish
   “must wake on death,” “retains ability,” and “definitely skip.”
5. **Protection is advisory only.** `kill` always kills. Monk, Innkeeper, Fool,
   Devil's Advocate, Exorcist, Sailor, Tea Lady, and related rules require the
   storyteller to read warnings and refrain from tapping death.
6. **Once-per-game and countdown state is reminder-convention driven.**
   Seamstress, Professor, Assassin, Courtier, Fool, Po charge, and similar
   abilities work only if the storyteller manually adds/removes the exact
   expected labels.
7. **Vigormortis poison is manual.** The engine warns on a Minion death but
   cannot choose which Townsfolk neighbour is poisoned or automatically
   distinguish a Minion killed by Vigormortis from another cause.
8. **Town Crier/Flowergirl answers depend on complete voter identity.** Vote
   totals alone are insufficient. Ordered `voterIds` must survive exactly.
9. **The calculator gives truth plus caveats, not the actual false signal.**
    This is correct for storyteller agency, but the app has no durable record
    of whether the poisoned/drunk/Vortox answer shown was 0, 1, YES, NO, or a
    particular token pair.

## Weird edge cases exercised

- Baron and Vigormortis setup modifiers at 15 players.
- Virgin immediate execution before a vote.
- Witch immediate nomination death, plus explicit suppression at 3 alive.
- Ordered voter replay, tie clearing the block, one-time ghost-vote spending.
- Traveller exile separated from the normal execution block.
- A full tied/no-execution day in the focused replay.
- Poison moving between holders and ending when the Poisoner dies.
- Spy and Recluse misregistration caveats.
- A dead Cerenovus retaining its ability under Vigormortis while dead Witch
  and Pit-Hag rows are skipped.
- Mid-game Pit-Hag character replacement.
- Sweetheart permanent drunkenness.
- Po charge and three attacks with one protected target.
- Fool preventing a Moonchild death.
- Professor resurrection after execution.
- Exorcist suppressing the generated Demon row.
- Demon execution followed by a complete Mastermind night/day.
- Saint execution as an immediate evil win.

## Improvements made from the playtests and storyteller feedback

1. Setup now validates the exact adjusted team distribution before dealing or
   starting, and the first-night phase control rechecks manually assigned
   games, mandatory hidden information, and Marionette seating. Bounded
   choices such as Godfather are enforced as exact alternatives, Village
   Idiot is capped at three copies, and all fixed modifiers are aggregated
   before clamping. The reported 15-player Vigormortis case is locked by a
   regression test: 2 Outsiders is rejected and 10/1/3/1 is accepted.
2. `Player` now stores actual and shown identities separately. Drunk,
   Lunatic, and Marionette setup prompts record the token shown to the player;
   reveals use that token; Drunk and Marionette wake in the shown character's
   row. Marionette stays out of normal Minion info and is pointed out
   separately to the Demon, including in 5–6 player games.
3. The night screen now has a persistent token tray: the active character's
   icon, one-tap access to the full visual character grid, and that
   character's reminder tokens ready to place on seats. The checklist opens
   the first unfinished action, advances to the next action, and warns with
   exact missing rows before dawn.
4. `DayScreen` serializes selected voters in displayed clockwise order rather
   than chip-tap order, and clears stale nominator/nominee/voter selections
   when the seating changes.
5. Witch curse warnings are suppressed below four living players.
6. Death records snapshot character and impairment at the moment of death, so
   `WinCheck` reports an executed sober and healthy Saint as an immediate evil
   win while a poisoned Saint remains harmless even after poison moves.
7. `InfoCalc` excludes Traveller-exile support from Flowergirl votes and
   rejects stale, duplicate, or missing calculator targets.
8. The show-token tool is now an immediate searchable icon grid with in-play
   characters first, replacing the slower text-only dropdown flow.
9. The grimoire, setup flow, seat editor, reference search, and compact top bar
   received scrolling, stale-state, contrast, semantics, and small-screen
   reliability fixes.
10. The acceptance fixture locks all of the above rules behavior and all three
    complete transcripts to real engine APIs.

Rules-sensitive rulings were cross-checked against the official
[Witch](https://wiki.bloodontheclocktower.com/Witch),
[Virgin](https://wiki.bloodontheclocktower.com/Virgin), and
[Mastermind](https://wiki.bloodontheclocktower.com/Mastermind),
[Drunk](https://wiki.bloodontheclocktower.com/Drunk),
[Lunatic](https://wiki.bloodontheclocktower.com/Lunatic), and
[Marionette](https://wiki.bloodontheclocktower.com/Marionette) references.
