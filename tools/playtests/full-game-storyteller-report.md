# Full-game storyteller playtest report

Date: 2026-08-28 (rebuild refresh; first written 2026-07-25)
Scope: three deterministic 15-player games, one per bundled base script, plus
the storyteller's own reported Bad Moon Rising session and a Teensyville table.

Canonical executable fixtures:

| Fixture | What it is |
|---|---|
| `engine/src/test/kotlin/com/clocktower/engine/FullGamePlaytestTest.kt` | The three 15-player games below |
| `engine/src/test/kotlin/com/clocktower/engine/BmrSessionPlaytestTest.kt` | The reported session — the five complaints the rebuild exists to answer |
| `engine/src/test/kotlin/com/clocktower/engine/TeensyvillePlaytestTest.kt` | Six players on a pasted homebrew script |

## Method

Each 15-player game starts from the bundled `Script` and a legal 15-character
bag. The fixture assigns every seat, moves every reminder through the real
reminder APIs, records every nomination (including ordered `voterIds`), spends
dead votes, kills and revives through the engine, advances every phase, covers
every generated `NightStep` **in the order `NightPlan.build` returns them**,
calls `InfoCalc` wherever supported, and checks the final `WinCheck` advisory.

The two newer fixtures drive the post-rebuild surface directly:
`NightPlan.build`/`resolve` for every night row, `DayRules.checkNomination` and
`DayRules.record` for the day, `Execution.execute` for the block, `Briefings.at`
for dawn and dusk, and `WinCheck` for the result. The Teensyville fixture greps
its own source to prove it never falls back on the frozen `GameActions` façade
or the WP1-deprecated `Deaths.kill`.

The prose below is the storyteller transcript. The Kotlin fixtures are the
machine-checkable source of truth for exact state, targets, reminders, deaths,
and results — where the two disagree, the fixture is right and this file is
stale.

## Verification

- Complete engine suite: **800 tests, 0 failures, 0 skipped**.
- `FullGamePlaytestTest`: **5 tests**; `BmrSessionPlaytestTest`: **10 tests**;
  `TeensyvillePlaytestTest`: **6 tests**. Nothing in any of them is `@Ignore`d.
- Standalone Compose UI typecheck (`tools/uicheck`): **passed, 81 tests**.
- Android `:app:assembleDebug` and the wasm PWA distribution: **passed**.

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

The other-night order is the official nightsheet's, not the app's old guess:
**Courtier · Innkeeper · Gambler · Devil's Advocate · Lunatic · Exorcist · Po ·
Assassin · Gossip · Professor · Moonchild · Grandmother · Chambermaid**. The
Courtier runs down its countdown *before* the Innkeeper protects, every night.

1. Dusk; night 2 began with Mina dead.
2. Courtier: Assassin drunkenness ticked `Drunk 3` → `Drunk 2`.
3. Innkeeper: Greta and Olive marked `Safe`; Greta also `Drunk`. The label is
   `Safe`, the official spelling — the Innkeeper and the Monk share it and are
   told apart by `sourceId`, never by the word on the token.
4. Gambler: Mina was dead at her row; skipped.
5. Devil's Advocate: protection moved to Aurora (`Survives Execution`).
6. Lunatic: Blake, believing he was the Po, chose nobody and believed he
   charged. No token was placed, because nothing he does has any effect.
7. Exorcist: Kendra chose Hector — not the Demon.
8. Po: Aurora chose nobody and took the `3 Attacks` charge.
9. Assassin: Elena was Courtier-drunk and declined.
10. Gossip: Nate's true day-1 statement killed Dorian.
11. Professor: Olive revived Townsfolk Mina and spent the ability (`No Ability`).
12. Moonchild, Grandmother, Chambermaid: skipped (alive / no Demon kill on the
    grandchild / Dorian died earlier tonight).
13. Dawn: Dorian announced dead; Mina announced alive again.

Day 2 (14 alive, threshold 7):

| Nominator → nominee | Clockwise voters | Total/result |
|---|---|---|
| Olive → Jasper | Olive, Aurora, Blake, Felix, Greta, Inez, Kendra | 7, block |
| Aurora → Cora | Aurora, Elena, Hector, Inez, Leo, Mina | 6, safe |
| Hector → Kendra | Hector, Blake, Felix, Greta, Nate, Olive | 6, safe |

Jasper was executed and selected good Fool Leo.

### Night 3 → Day 3

1. Dusk; the Po held a charge and the Moonchild had selected Leo.
2. Courtier: Assassin drunkenness ticked `Drunk 2` → `Drunk 1`.
3. Innkeeper: last night's `Safe` pair cleared; Kendra and Olive marked `Safe`,
   Olive `Drunk`.
4. Gambler: Mina guessed Greta as the Grandmother, correctly, and survived.
5. Devil's Advocate: Aurora protected again for the coming day.
6. Lunatic: Blake spent his supposed Po charge on Cora, Greta and Nate, and
   each took a `Chosen` token — the official Lunatic label, three copies, one
   per player picked. (The pre-rebuild data called these `Attack 1/2/3`.)
7. Exorcist: Kendra chose Cora — not the Demon.
8. Po: Aurora attacked Greta, Nate and Kendra and spent `3 Attacks`. Greta and
   Nate died. Kendra survived: the pre-death warning on her seat named the
   Innkeeper's **`Safe`** token, and the fixture asserts that word.
9. Assassin: still Courtier-drunk; declined.
10. Gossip, Professor: skipped (Nate died earlier tonight / ability spent).
11. Moonchild: Jasper's target Leo was good, but the Fool's first-death
    protection prevented the death and was spent (`No Ability`).
12. Grandmother: Greta died to the Demon, but her grandchild Mina did not, so
    no extra death. Chambermaid: Dorian remained dead; skipped.
13. Dawn: Greta and Nate announced dead; Kendra and Leo survived; the Lunatic's
    three `Chosen` tokens were swept.

Day 3 (11 alive, threshold 6):

| Nominator → nominee | Clockwise voters | Total/result |
|---|---|---|
| Kendra → Cora | Kendra, Aurora, Blake, Felix, Inez, Mina | 6, block |
| Elena → Olive | Elena, Hector, Inez, Leo, Mina | 5, safe |
| Hector → Aurora | Hector, Blake, Felix, Leo, Olive | 5, safe |

Cora was executed.

### Night 4 → Day 4

1. Dusk; ten alive, and the dead Devil's Advocate's `Survives Execution`
   expired with its source.
2. Courtier: the third night expired and Elena became sober; Felix took
   `No Ability`.
3. Innkeeper: protection moved to Blake and Leo (`Safe`), Blake made `Drunk`.
4. Gambler: Mina guessed Inez as the Innkeeper, correctly, and survived.
5. Devil's Advocate: Cora was dead; skipped.
6. Lunatic: drunk Blake selected Olive as a supposed Po target.
7. Exorcist: Kendra selected Aurora; the Po was marked `Chosen` and silenced.
8. Po: the row was still generated and annotated with the Exorcist's silence —
   an exorcised Demon is REDUCED, never skipped (lead D24) — and Aurora did not
   act.
9. Assassin: now sober, Elena spent the Assassin on Professor Olive, before the
   Professor's own row could be reached.
10. Gossip, Professor, Moonchild, Grandmother, Chambermaid: all skipped.
11. Dawn: Olive announced dead.

Day 4 (9 alive, threshold 5):

| Nominator → nominee | Clockwise voters | Total/result |
|---|---|---|
| Mina → Aurora | Mina, Blake, Felix, Inez, Kendra | 5, block |
| Aurora → Mina | Aurora, Elena, Hector, Leo | 4, safe |
| Hector → Elena | Hector, Blake, Felix, Inez | 4, safe |

Aurora was executed. The engine advised good victory but did include a
Mastermind caution. Hector was alive, so the game correctly continued.

### Mastermind Night 5 → Day 5

1. Dusk; the continuation night began with the engine still showing a good-win
   advisory carrying a Mastermind caution.
2. Courtier: spent; skipped.
3. Innkeeper: protection moved to Mina and Kendra (`Safe`), Kendra made `Drunk`.
4. Gambler: Mina guessed Hector as the Mastermind, correctly, and survived.
5. Devil's Advocate: dead; skipped.
6. Lunatic: Blake selected Elena as a supposed Po target. There was no living
   Demon to receive it, and it made no difference — nothing a Lunatic chooses
   ever does.
7. Exorcist: drunk Kendra selected Blake; no effect.
8. Po, Assassin, Gossip, Professor, Moonchild, Grandmother, Chambermaid: all
   dead or spent; skipped.
9. Dawn: no deaths; the final Mastermind day opened.

Final day (8 alive, threshold 4):

| Nominator → nominee | Clockwise voters | Total/result |
|---|---|---|
| Hector → Mina | Hector, Blake, Elena, Felix | 4, block |
| Mina → Hector | Mina, Inez, Kendra | 3, safe |

Mina was executed. On the Mastermind continuation, the executed player's team
loses. **Evil won on Day 5.**

---

## Game 4: the reported Bad Moon Rising session — the five complaints

Fixture: `BmrSessionPlaytestTest`. Twelve players, and unlike games 1–3 this is
not an invented transcript: it is the game in `docs/audit/ux/friction-log.md`,
the one the storyteller actually ran and complained about. Every complaint is a
test, and all ten tests in the file are live — the five that were `@Ignore`d
through WP12 pass 1, each naming the package that would deliver it, came on in
pass 2.

Seats, in order: Ana Devil's Advocate · Ben Sailor · Cleo Chambermaid ·
Dev Gossip · Erin Grandmother · Gita Professor · Iris Tea Lady · Hal Exorcist ·
Finn Fool · Jonas Lunatic (shown the Po) · Kai Pukka · Lena Godfather.
8/1/2/1 — legal at twelve only because the Godfather took its `[-1 Outsider]`,
and the fixture proves the unmodified two-Outsider bag is still rejected.

| # | The complaint | What the engine does now |
|---|---|---|
| 1 | "Pukka offered to kill on the night it should only poison" | The night-1 Pukka row is a poison step with no `Attack` in `perTarget`. One night-2 choice poisons the new target and kills the old one, in that order, with `abilityImpairedAtDeath` true and the poison cleared afterwards. Night 3 the Exorcist silences the Pukka: the step is `StepGate.Reduced`, never skipped, and the night-2 victim still dies (lead D24). |
| 2 | "The Devil's Advocate token gave no way to honour *different to last night*" | The token is swept at dawn; the CHOICE is not. `Memory.lastChoice` reads it back the next night from the ledger, night 2's picker carries `TargetConstraint.DIFFERENT_FROM_LAST_NIGHT`, and the step's banner says who was chosen. |
| 3 | "Make it easy to write down all the gossips even if the Gossip isn't in play" | `Ledger.statement` records it on the day it was said, `UNJUDGED`; the storyteller sets the verdict later; night 3's Gossip step quotes the day-2 words back. |
| 4 | "When the Professor brings someone back it should remind in the morning and rerun the first night" | The resurrection raises a `RUN_FIRST_NIGHT` prompt, tonight's sheet grows the resurrected seat's FIRST-night step in place (`WakeStyle.FIRST_NIGHT`), and the dawn briefing announces "…is alive again" without ever saying why (lead D7). |
| 5 | "The Lunatic had no bluffs, no fake Minions and no Demon behaviour" | `Bluffs.requirements` owes the Lunatic a set of its own — three characters, in-play allowed — independent of the real Demon's. `SetupRequirements` owes a fake Minion per real Minion. And per lead **D70** the seat's derived grant is `abilityId = <believed demon>` with `slotId = "lunatic"`, so the row sorts at the Lunatic's own place on the nightsheet while running the Po's action as a placebo: charged with `3 Attacks` it takes three targets, places three `Chosen` markers, and kills nobody. |

The first-night sheet this game generates, in official order:
**Dusk · Minion info · Lunatic · Demon info · Sailor · Godfather ·
Devil's Advocate · Pukka · Grandmother · Chambermaid · Dawn**. The Lunatic is
woken between the Minion and Demon info steps — handed the illusion before the
real Demon is told who they are. The Tea Lady, Fool, Gossip, Professor and
Exorcist have no first night at all.

---

## Game 5: Teensyville — six players on a pasted script

Fixture: `TeensyvillePlaytestTest`. A hand-rolled twelve-character script
("Harbour Lights", 6 Townsfolk / 2 Outsiders / 2 Minions / 2 Demons, mixed
edition) loaded the way a storyteller actually loads one: pasted script-tool
JSON with a `_meta` header and snake_case ids, through `ScriptParser`.

Seats: Ada Chef · Bea Empath · Cai Monk · Dot Butler · Eli Poisoner · Fen Imp.
That is 3/1/1/1, which is what six players is — **not** the 7+ formula with
seats removed. Five players is 3/0/1/1: the sixth seat is the only Outsider,
and dropping to five drops the Outsider, not a Townsfolk.

Small games are their own thing, and three consequences are pinned:

- **Nobody is introduced to anybody.** Night 1 is
  **Dusk · Poisoner · Chef · Empath · Butler · Dawn**. There is no MINION INFO
  and no DEMON INFO row: `Gates.minPlayers(7)` skips both below seven players.
- **The Demon is owed no bluffs.** `Bluffs.requirements` raises no demon set at
  six players.
- **A seventh seat turns all of it back on.** The control test adds one
  Fortune Teller to the same script and the same evil team, and both info rows
  and the bluff requirement reappear. The gate is the player count, never the
  script.

The game itself is two nights, one execution, a good win, built round one
interaction:

- **Night 1** the Poisoner poisons the CHEF. `InfoCalc` returns the true count
  together with `MAY_LIE`, the alternative answers, and the caveat naming the
  Poisoner — the storyteller is told what the truth is and that they must not
  tell it. The healthy Empath gets `TRUTH` and no caveat at all. Dawn: nobody
  died.
- **Day 1** six alive, threshold 3. Bea nominates Eli and gets two hands: below
  threshold, nobody on the block. Bea nominating again the same day is refused.
  Dusk lists the `Poisoned` token that is about to come off.
- **Night 2** the Poisoner poisons the MONK. The Monk protects Bea, and
  `Status.protections(Bea)` is **empty** — a poisoned Monk protects nobody — so
  the Imp's kill lands on the seat it was aimed at. The dawn briefing is
  computed before the sweep, so it announces Bea's death *and* names the Monk
  token that failed.
- **Day 2** five alive, threshold 3. Ada nominates Fen and three hands put the
  Imp on the block. `Execution.execute` writes one `ExecutionRecord` — DIED,
  evil at execution, tally 3, threshold 3, via VOTE — and a second execution
  the same day returns the state untouched. `WinCheck` gives `demon-dead`,
  good wins, carrying the Imp's star-pass caution.

---

## What the app still cannot represent faithfully

This list was written against the pre-rebuild app. Most of it is closed;
what closed it is recorded here rather than deleted, because the transcripts
above were produced under the old behaviour and a reader needs to know which
half they are looking at.

### Closed by the rebuild

1. ~~**Resurrection erases death history.**~~ Deaths are an append-only
   `DeathEvent` log with stable ids. A resurrection marks the event
   `resurrectedAtCycle` and leaves it in place — `BmrSessionPlaytestTest`
   asserts the execution is still in the log, flagged resurrected, with the
   seat alive.
2. ~~**No structured action history.**~~ `GameState.ledger` records every
   choice, wake, malfunction, spend, announcement and thing-told, and
   `Memory` reads it back: last night's choice, forbidden targets, spent
   once-per-game abilities, statements by day. That is what makes the Devil's
   Advocate and Gossip complaints answerable at all.
3. ~~**Protection is advisory only.**~~ Every kill goes through
   `Deaths.attempt` and its 15-step precedence table; `Execution.execute` is
   the one funnel behind every "Execute" button. The Teensyville fixture kills
   through it and gets a poisoned Monk's protection correctly ignored.
4. ~~**Once-per-game and countdown state is reminder-convention driven.**~~
   Rules-bearing state is an `Effect` with an expiry, rendered as a token;
   once-per-game spends are `Memory.isSpent`. The tokens carry official Title
   Case labels and are compared by `Tokens.key(sourceId, label)`, never by the
   word (lead D5): `Safe` belongs to both the Monk and the Innkeeper, and only
   the source tells them apart.

### Still open

1. **Mastermind continuation has no state.** `WinCheck` still keeps returning
   “all Demons dead; good wins” with a caution. It cannot mark the extra day as
   pending/resolved or determine the final losing team. A dedicated
   end-condition state/event is needed.
2. **Conditional dead rows are noisy.** The night sheet retains all dead role
   rows, which is useful for Ravenkeeper/Sage and Vigormortis-retained Minions,
   but forces repeated manual “dead, skip” decisions. It does not distinguish
   “must wake on death,” “retains ability,” and “definitely skip.”
3. **Vigormortis poison is half manual.** Wave 7's `NightEffect.When` lets the
   registry ask "was the seat we just killed a Minion?", so the `Has Ability`
   marker is now placed rather than advised — but WHICH Townsfolk neighbour is
   poisoned stays a storyteller choice, by design.
4. **Town Crier/Flowergirl answers depend on complete voter identity.** Vote
   totals alone are insufficient. Ordered `voterIds` must survive exactly.
5. **The calculator gives truth plus caveats, not the actual false signal.**
   This is correct for storyteller agency. `LedgerEntry.shown` now has a place
   to record what was actually shown, but the 15-player transcripts above
   predate it and do not use it.

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

Added with the two newer fixtures:

- A poison step that offers no kill, and a kill deferred to the following night
  (Pukka), including through an Exorcist's silence.
- A night choice read back from the ledger after its token was swept, and next
  night's picker excluding it (Devil's Advocate).
- A public statement recorded on the day it was made and quoted back on a later
  night (Gossip).
- A resurrection that announces at dawn without saying why, and re-runs the
  resurrected seat's FIRST night in tonight's sheet (Professor).
- A believed-Demon placebo: three targets, three `Chosen` markers, nobody dead,
  on the Lunatic's own night slot (lead D70).
- A homebrew script pasted as script-tool JSON, `_meta` header and snake_case
  ids and all.
- The 5- and 6-player distributions, and a six-player game with no MINION INFO,
  no DEMON INFO and no Demon bluffs — with a seven-seat control that turns all
  three back on.
- A poisoned Monk protecting nobody, so the Demon's kill lands.
- One `ExecutionRecord` per day: a second `Execution.execute` the same day
  returns the state untouched.

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
