# Records & Storyteller Memory (`records-and-memory`) — cross-cutting mechanics

Scope: everything the storyteller must remember **across a phase boundary**, and
how the app should hold it. This is the unifying document for a dozen separate
proposals scattered through `docs/audit/characters/*.md`: nine files invented a
`NightChoice`, ten invented a `NightRecord`, six a `NightAction`, twelve a
`DayAct`, one a `PublicStatement`, five a `PublicClaim`, five an `Announcement`.
They are all the same thing. This document specifies **one** model and shows
which per-character proposal maps onto it, so the implementer builds it once.

The three verbatim user complaints this document owns:

> "Gossip was awful, make it easy to write down all the gossips even if Gossip
> isn't in play" — §Defect 3, §Spec B.
> "When Professor brings someone back it should remind in the morning" —
> §Defect 4, §Spec E.
> "DA wasn't automatically removed and allowed two days in a row I think for
> same person" — §Defect 2, §Spec D3.

---

## Official rules (sources)

### The general principle

Blood on the Clocktower's physical grimoire *is* the storyteller's memory: seat
tokens, reminder tokens on seats, and reminder tokens in the **centre of the
grimoire** that belong to no seat. Everything else — public statements, private
conversations, rulings about misregistration, what number you actually showed
the Empath — the storyteller keeps on paper or in their head. A companion app
that only models seats and seat tokens reproduces the *worst* half of the
physical experience while removing the paper.

Two wiki pages state the centre-of-grimoire convention explicitly:

- **Gossip** (<https://wiki.bloodontheclocktower.com/Gossip>) — ability:
  *"Each day, you may make a public statement. Tonight, if it was true, a player
  dies."* How to Run: *"Put the Gossip's **DEAD** reminder in the center of the
  left side of the Grimoire as a reminder."* Also: *"Mumbled words, whispers,
  statements the Storyteller doesn't know are true or false … don't count"* —
  i.e. the storyteller must have **heard and recorded** the exact statement to
  adjudicate it that night. And: *"If the Gossip made a true statement during
  the day while drunk or poisoned, but is sober and healthy when their ability
  triggers that night, the Storyteller still kills a player"* — so the
  *impairment at the moment of speaking* must also be remembered, separately
  from impairment at the moment of resolution.
- **Yaggababble** (<https://wiki.bloodontheclocktower.com/Yaggababble>) —
  *"You start knowing a secret phrase. For each time you said it publicly today,
  a player might die."* How to Run: *"Each time Demon says the secret phrase,
  put a **DEAD** reminder in the center of the left side of the Grimoire … Each
  night, you may mark players with these DEAD reminders."* This is a **tally
  the storyteller increments during the day**, held nowhere near a seat.

Further ground truth gathered for this document:

- **Savant** (<https://wiki.bloodontheclocktower.com/Savant>) — *"Each day, you
  may visit the Storyteller to learn 2 things in private: 1 is true & 1 is
  false."* The visit is private (*"the group can't listen in"*), so the app must
  let the ST compose and **retain** the pair — a drunk/poisoned Savant gets two
  trues or two falses, and the ST must not later contradict what they gave.
- **Mezepheles** (<https://wiki.bloodontheclocktower.com/Mezepheles>) — *"You
  start knowing a secret word."* How to Run: *"While setting up the game, write
  a single word on a piece of paper or on a phone or other device."* The
  storyteller invents content **at setup** that must survive the whole game and
  be re-showable on night 1. The app currently offers no place for it, so the
  wiki's own instruction ("or on a phone") is fulfilled by *another app*.
- **Cerenovus** (<https://wiki.bloodontheclocktower.com/Cerenovus>) — *"Each
  night, choose a player & a good character: they are 'mad' they are this
  character tomorrow, or might be executed."* The ST must carry **both** the
  seat and the character through the night into the following day and judge
  effort (*"If the Storyteller doesn't hear them make an effort, they pay the
  price"*). The app's `("cerenovus","Mad")` token records the seat and **loses
  the character entirely**.
- **Balloonist** (<https://wiki.bloodontheclocktower.com/Balloonist>) — current
  text: *"Each night, you learn a player of a different character type than last
  night. [+0 or +1 Outsider]"* How to Run: *"When preparing each night
  afterwards, mark a character of a different type than the current with the
  **KNOW** reminder."* The constraint is against **last night's type**, which is
  a fact about a token that no longer exists.
- **Devil's Advocate** (see `docs/audit/characters/devilsadvocate.md`, which
  quotes the page) — *"choose a living player (different to last night)"*. The
  constraint is unconditional and applies even when the DA was impaired.

### Inventory: every piece of cross-phase memory the rules require

Derived by scanning all 171 entries of
`engine/src/main/resources/botc/data/characters.json` for the ability-text
patterns that imply memory, cross-checked against the per-character audits.

**(1) Last night's choice — "must differ" or "the previous one resolves now"**
(the choice itself must outlive the token):

| Character | What must be remembered | Why the token isn't enough |
|---|---|---|
| `devilsadvocate` | last night's chosen seat | token is in `EXPIRES_AT_DUSK` (`GameActions.kt:238`) — deleted before the next choice |
| `exorcist` | last night's chosen seat | token is in `EXPIRES_AT_DAWN` (`GameActions.kt:222`) |
| `balloonist` | the **character type** shown each night, cumulatively | `Know` token is a seat mark; the type history is nowhere |
| `po` | whether last night's choice was "no-one" (→ 3 kills tonight) | there is no token for "chose nobody" |
| `pukka` | who was poisoned **last** night (they die tonight) | `Poisoned` moves; the previous holder is lost |
| `cerenovus` | seat **and** good character | `PlacedReminder` has no character field |
| `harpy` | 1st seat (mad) **and** 2nd seat (the accused) | two tokens, no link between them |
| `fearmonger` | previous chosen seat — *"All players know if you choose a new player"* is a dawn announcement conditioned on a comparison | nothing to compare against |
| `preacher` | every Minion chosen so far (three `No Ability` tokens) | tokens survive but the ST must remember which night, for the Minion's own "learns this" |
| `widow` | that the Widow already acted (1st night only) + which good player got `Know` | fine today, but not in the log |
| `courtier` | a 3-night countdown (`Drunk 3/2/1`) + spent flag | the app never decrements it (§Defect 17) |
| `lycanthrope` | last night's choice (and whether the Demon was suppressed) | `Faux Paw` is not cycle-stamped |
| `barista` | which of the two modes was chosen, and for whom | no token distinguishes mode 1 from mode 2 |
| `duchess` | the three visitors chosen **during the day**, and which one gets false info | `Visitor`/`False Info` tokens exist but nothing records the day-time selection |
| `innkeeper` | which of the two chosen players is the drunk one | two tokens, but `Protected`/`Drunk` are separate placements with no pairing |
| `monk`, `poisoner`, `sailor`, `witch`, `snakecharmer`, `pithag`, `organgrinder`, `lleech`, `lordoftyphon`, `ojo`, `kazali`, `vortox`, `nodashii`, `vigormortis`, `fanggu`, `shabaloth`, `imp`, `acrobat`, `gambler`, `chambermaid`, `dreamer`, `fortuneteller`, `villageidiot`, `butler`, `thief`, `bureaucrat`, `harlot` | the nightly choice, for the log and for cross-checking | 36 characters match `Each night*, choose` |

**(2) Public statements & guesses made during the day** (the storyteller must
record the words, then consume them later):

`gossip` (statement → tonight's kill), `juggler` (up to 5 guesses on day 1 →
resolved night 2), `alsaahir` (guess of all Minions+Demon → instant win),
`damsel` (a Minion's public guess, once, → evil wins), `goblin` (public Goblin
claim when nominated → win if executed **that day**), `mutant` (madness about
being an Outsider → may be executed), `slayer` (public shot), `klutz` (public
choice on death), `moonchild` (public choice on death), `psychopath` (public
duels), `yaggababble` (count of public phrase utterances), `vizier` (public
identity + "if good voted, may execute immediately"), `snitch` (three bluffs per
Minion — what was actually handed out), `puzzlemaster` (the guess), `doomsayer`,
`deusexfiasco`, plus the **generic claim log** ("Bo claims Empath") that every
info character's audit asked for (`knight.md`, `noble.md`, `dreamer.md`,
`klutz.md`, `cerenovus.md`, `politician.md`).

**(3) Private storyteller conversations during the day**:
`savant` (2 facts, 1 true 1 false, every day), `artist` (one yes/no question,
once per game), `fisherman` (advice, once per game), `amnesiac` (a daily private
guess + a "how accurate" answer), `wizard` (the wish, its price and its clue),
`philosopher` (the chosen character), `mathematician` (the count of
malfunctions since dawn — see `mathematician.md`'s `Malfunction` proposal).

**(4) Storyteller-invented content that must survive the whole game**:
`mezepheles` word, `yaggababble` phrase, `amnesiac` invented ability (+ whether
and when it wakes), `wizard` wish + price + clue, `savant` prepared statement
pairs, `general`'s nightly war signal, `highpriestess`'s nightly nudge,
`plaguedoctor`'s storyteller-held Minion ability
(`plaguedoctor.md`'s `StorytellerAbility`), `bishop`'s nomination obligations,
`alchemist`'s granted Minion ability, `hermit`'s chosen Outsider abilities.

**(5) Spent once-per-game abilities** — 21 characters match `Once per game`:
`slayer, courtier, professor, assassin, judge, artist, philosopher, seamstress,
bonecollector, engineer, fisherman, huntsman, nightwatchman, golem, wizard,
doomsayer, fibbin, fiddler, revolutionary, toymaker, deusexfiasco`. Seven of
them (`philosopher, golem, wizard, doomsayer, fiddler, toymaker, deusexfiasco`)
ship **no** `No ability`/`Used` reminder token at all, so there is literally
nothing to place. Add `virgin`, `fool`, `damsel` (`Guess Used`), `mezepheles`,
`puzzlemaster`, `pixie`, `preacher` as once-only-in-effect characters.

**(6) Who-was-told-what** (so the ST stays consistent, and so post-game review
is possible): the number shown to a drunk Empath on each night; the Lunatic's
fake results and fake bluffs; every misregistration ruling for `spy` and
`recluse` (29 character audits mention misregistration); every "false info to
show instead" the ST picked at `NightScreen.kt:903-930`; the Balloonist's
sequence; the Undertaker's shown character; the Savant pairs.

**(7) Pending dawn / day-start announcements**: who died (with cause);
resurrections (`professor`, `shabaloth` regurgitation, `bonecollector`);
"no-one died"; `fearmonger`'s "all players know if you choose a new player";
`widow`'s "1 good player knows a Widow is in play"; `vizier` reveal; `damsel`
"all Minions know a Damsel is in play"; `snitch`; `king`'s "the Demon knows you
are the King"; `leviathan`'s "all players know you are in play"; the day's
madness obligations; "X survives execution today" (Devil's Advocate);
Minstrel drunkenness; Mastermind day.

**(8) Day/night counters**: `leviathan` `Day 1..5` (evil wins after day 5),
`riot` `Day 1..3`, `xaan` `Night X`, `summoner` `Night 1..3` (acts on night 3),
`courtier` `Drunk 3/2/1`, `po` `3 attacks`, `lunatic` `Attack 1..3`,
`bureaucrat` `3 votes`, `hermit` `1/2/3`, `alhadikhia` `1/2/3`,
`yaggababble` three `Dead` tokens. Eleven characters ship numbered tokens; the
app treats every one of them as an inert label.

**(9) Improvised rulings** with no token at all: "I'm ruling the Recluse
registers as the Imp for the Fortune Teller but not for the Empath"; "this
counts as the Gossip's statement"; "Bo's madness attempt was good enough today".

---

## What the app does today

### The five things `GameState` remembers across a phase

`engine/src/main/kotlin/com/clocktower/engine/GameState.kt:93-115`:

1. `nominations: List<Nomination>` (`GameState.kt:103`) — day, nominator,
   nominee, vote count, `voterIds` in clock order, result, exile flag. **Works.**
2. `deaths: List<DeathRecord>` (`GameState.kt:104`) — playerId, cycle,
   `atNight`, `cause` (a 5-value enum), `characterIdAtDeath`,
   `abilityImpairedAtDeath`, `resurrected`. Good snapshots; **no killer**.
3. `nightStepsDone: Set<String>` (`GameState.kt:106`) — reset at dusk
   (`GameActions.kt:262`) and at SETUP→NIGHT (`:259`).
4. `storytellerNotes: String` (`GameState.kt:112`) — one global blob.
5. `Player.note: String` (`GameState.kt:31`) and
   `Player.reminders: List<PlacedReminder>` (`GameState.kt:30`), where
   `PlacedReminder` is exactly `(sourceId, label)` — two strings, no cycle, no
   target, no character, no free text (`GameState.kt:6-11`).

Plus `mastermindDayActive: Boolean` (`GameState.kt:111`), the single ad-hoc
"remember this across a phase" flag in the whole engine.

That is the complete cross-phase memory of the app. **There is no record of any
choice, any statement, any ruling, any information given, or anything the
storyteller owes the table.**

### How records are produced and consumed

- **Nominations**: composed in `DayScreen.kt:126-255` with transient
  `rememberSaveable` drafts (`:58-60`), reconciled against removed seats
  (`:65-69`), written via `GameActions.recordNomination` (`GameActions.kt:274`).
  `GameActions.aboutToDie` (`:296-306`) derives the block from the sequence —
  a good example of "derive, don't store". `NominationResult.WITHDRAWN`
  (`GameState.kt:59`) is rendered (`DayScreen.kt:343`, `GameExtras.kt:71`) but
  **never assigned anywhere**.
- **Deaths**: `GameActions.kill` (`:136-156`) snapshots character and
  impairment. `revive` (`:162-166`) **drops** the record; `resurrect`
  (`:173-181`) keeps it and flags `resurrected`. Correct split.
- **Reminder expiry**: two hard-coded tables, `EXPIRES_AT_DAWN`
  (`GameActions.kt:218-225`, 6 entries) and `EXPIRES_AT_DUSK` (`:231-242`,
  10 entries). These *destroy* the only trace of a nightly choice.
- **Night step check-off**: `toggleNightStep` (`GameActions.kt:265`), driven
  from `NightScreen.kt:147`. Goes through `update{}` so it **is** undoable.
- **Per-step target picks**: `NightScreen.kt:839` —
  `var targets by rememberSaveable(step.id) { mutableStateOf(listOf<Long>()) }`.
  UI-only. Never written to `GameState`.
- **Storyteller notes**: menu item `GameShell.kt:222-225` → dialog
  `GameShell.kt:685-708`, a single `OutlinedTextField` over
  `state.storytellerNotes`, saved only on the **Save** button.
- **Seat notes**: `SeatSheet.kt:366-372`, saved only by **"Save & close"**
  (`:373-380`).
- **Game log**: `GameExtras.kt:46-106`. Derives entries from `deaths` and
  `nominations` only, sorts by `compareBy({ it.day }, { !it.atNight })`
  (`:79`), renders in an `AlertDialog`. No copy, no share, no export.
- **Reveal sheet**: `GameExtras.kt:271-350` — final cast list with each
  player's last death. "End game & return home" (`:344`) calls `endGame()`.
- **Showing information to a player**: `ShowCards.kt:65-77` defines the
  `ShowCard` sealed interface; `FullScreenShow` (`:82-...`) displays and
  dismisses. **Nothing is recorded.** The false-info shortcuts
  (`NightScreen.kt:903-930`) are the same — the ST taps "3", the player sees
  "3", and the app forgets it happened.
- **Dawn**: `NightOrder.kt:59` — a constant string,
  `"Wait a few seconds. Everyone opens their eyes. Announce who died."`
  It does not name who died, and there is no day-start briefing anywhere.
- **Grimoire centre**: `GrimoireScreen.kt:98-235` — the centre of the circle
  holds a decorative vignette (`:101-145`) and, when empty, one placeholder
  string (`:228-234`). Nothing else. There is no home for a seat-less token.

### Persistence, undo, platforms

- **Android** `GameViewModel.kt:100-133`: `update{}` pushes the previous state
  onto an in-memory `ArrayDeque` (`:39`, `MAX_HISTORY = 100` at `:265`), clears
  redo, then `setGame` stamps `updatedAt` and launches an **async**
  `dataStore.updateData` (`:130-132`). Serialization is
  `Persistence.kt:14-31` over the whole `SavedData`.
- **Web** `WebGameViewModel.kt:82-113`: identical logic, synchronous
  `WebStore.save` → `localStorage.setItem` (`WebApp.kt:29-35`). The `catch` at
  `WebApp.kt:32-34` **silently swallows quota and serialization failures**.
- **`SavedData`** (`SavedData.kt:10-15`): `game: GameState?` — exactly **one**
  game, plus imported scripts, plus one notes session.
- `endGame()` (`GameViewModel.kt:89-98`, `WebGameViewModel.kt:73-80`) sets
  `game = null` and writes it. Everything is gone.
- `web/build.gradle.kts:45-62` compiles `app/src/main/java` **verbatim** and
  excludes only `GameViewModel.kt`, `Persistence.kt`, `Platform.kt`,
  `IconStore.kt`, and the Android entry points. **Consequence for this spec**:
  any new Composable compiles on both platforms for free, but **every new
  ViewModel wrapper must be written twice** or the PWA build breaks.

### What already works (one line each)

Nomination recording, ghost-vote spending, the derived block, death cause
snapshots, resurrect-vs-revive, night-step check-off undo, the reminder tray's
exclusive/multi-copy placement logic (`NightScreen.kt:318-340`), and the
player-notes mode's record model (`Notes.kt:16-87`) — which is **richer than
the storyteller's**: `NoteClaim(characterId, day)` claim history, `NoteLink`
with kinds and days, `NoteInfo(day, text)` info log, `generalNotes`. The
storyteller side has none of that. That asymmetry is the single clearest
statement of the problem.

---

## Defects and gaps

**P0 · 1 · Night-step target picks are keyed by step id, not by cycle — the app
shows stale information from a previous night.**
`NightScreen.kt:839`: `rememberSaveable(step.id)`. Every other transient pick in
the file is correctly keyed by cycle (`:91`, `:96`, `:540`, `:474`, `:507`), so
this is an oversight, not a convention. Because `LazyColumn` retains item state
by key (`:138`) and `GameShell.kt:299` wraps each tab in a
`SaveableStateProvider`, the two seats picked for the Fortune Teller on night 1
are still selected on night 2 — and `InfoCalc.compute` (`:863`) immediately
renders an answer for them, in bold gold, ready to read out.
*Repro*: night 1, expand Fortune Teller, pick two seats, read the answer. Dawn,
dusk. Night 2, expand Fortune Teller: the same two chips are selected and a
headline is displayed before the FT has pointed at anybody.

**P0 · 2 · There is no memory of last night's choice, so "different to last
night" is unenforceable and the same player can be chosen twice.**
This is the user's DA complaint. `("devilsadvocate","Survives execution")` is in
`EXPIRES_AT_DUSK` (`GameActions.kt:238`), so by the time the DA wakes on night 2
the only trace of night 1 has been deleted by `clearEphemeral` (`:244-251`), and
`GameState` has no other field. Identical for the Exorcist
(`EXPIRES_AT_DAWN`, `:222`) and the Balloonist (whose constraint is on the
character **type**, which was never stored even while the token existed).
`devilsadvocate.md` and `courtier.md` both call for
`GameActions.lastChoice(...)`; nothing exists.
*Repro*: night 1, place `Survives execution` on Ana. Dawn. Dusk (token
disappears). Night 2, the DA step offers Ana with no warning.

**P0 · 3 · Nothing anywhere records a public statement.**
This is the user's Gossip complaint, and it generalises: `gossip`, `juggler`,
`alsaahir`, `damsel`, `goblin`, `mutant`, `slayer`, `klutz`, `moonchild`,
`vizier`, `snitch`, `yaggababble`, `psychopath`, plus plain character claims,
all need the storyteller to write down words during the day. `GameState`
(`GameState.kt:93-115`) has no list for it; `DayScreen.kt` (360 lines) is
nominations only; the sole free-text field in the whole storyteller UI is the
single-blob `storytellerNotes` dialog behind two menu taps
(`GameShell.kt:214-225`, `:685-708`), which is modal, has a **Cancel** button
that discards, and has no day stamp, no speaker and no structure.
The user's phrasing — *"even if Gossip isn't in play"* — is the key requirement:
the recorder must be script-independent and always present.

**P0 · 4 · No pending-announcement queue and no morning briefing.**
This is the user's Professor complaint. `GameActions.resurrect`
(`GameActions.kt:173-181`) flips `alive` and flags the death record — and that
is the entire consequence. The DAWN step text is a constant
(`NightOrder.kt:59`); `DayScreen.kt:85-124` opens with the alive count and the
execution threshold. Neither says "Bo is alive again", "Ana died — Imp",
"Cara is mad that she is the Empath", "Dan survives execution today",
"the Fearmonger chose someone new", or "nobody died". Every one of these is
information the storyteller **owes the table out loud** at that exact moment.

**P0 · 5 · Storyteller-only tokens have nowhere to live.**
`PlacedReminder` only exists inside `Player.reminders` (`GameState.kt:30`), so a
token can only be placed on a seat. The rules explicitly require seat-less
tokens in the centre of the grimoire for the **Gossip** (`DEAD`), the
**Yaggababble** (up to three `DEAD`), and by the same convention the
**Leviathan** `Day 1..5`, **Riot** `Day 1..3`, **Xaan** `Night X`, **Summoner**
`Night 1..3` counters. The app's grimoire centre is empty decoration
(`GrimoireScreen.kt:98-145`, `:228-234`). `plaguedoctor.md` independently
proposed `val storytellerReminders: List<PlacedReminder>` for the same reason.

**P0 · 6 · The web build silently stops saving when localStorage is full or a
value fails to serialize.**
`WebApp.kt:29-35` catches every exception and returns. The user plays on an
iPhone PWA; a full quota (imported scripts + game + notes are all in one key,
`WebApp.kt:19`) means every subsequent action is lost with **no** indication,
and a refresh silently restores an old game. There is no schema version and no
save-failure signal in `SavedData`.

**P1 · 7 · No log of what any player was actually told.**
`FullScreenShow` (`ShowCards.kt:82`) renders and dismisses. The false-info
shortcuts (`NightScreen.kt:903-930`) let the ST show a lie to a drunk Empath and
keep no note of which lie — so on night 3 the ST has no way to stay consistent,
and after the game nobody can reconstruct what happened. `InfoCalc.compute`
already produces the true answer and the caveats (`InfoCalc.kt:38-85`); the
delta between "computed" and "shown" is precisely the record that is missing.

**P1 · 8 · Misregistration rulings are warnings, never decisions.**
`InfoCalc.misregistrations` (`InfoCalc.kt:121-130`, used by 29 characters per
the audits) emits prose caveats. The ST's actual ruling — "the Recluse
registered as the Imp for the Fortune Teller on night 2" — is not stored, so
nothing can warn them when they rule the opposite way for the Empath ten minutes
later. `spy.md` proposed `data class Misregistration(playerId, cycle, askedBy,
asCharacterId, asEvil)`.

**P1 · 9 · The only free-text field is a global blob behind a modal with a
Cancel button.** `GameShell.kt:685-708`. There is no per-day, per-seat, or
per-night structure; nothing appears in the log; the text is invisible unless
the ST opens the menu, taps "Storyteller notes", and reads. During a live night
this is 3 taps to read and 4 to write.

**P1 · 10 · Typed notes are lost on dismiss.** `SeatSheet.kt:366-380` — the
"Seat notes" field only reaches `GameState` via **Save & close**. A
`ModalBottomSheet` (`SeatSheet.kt:75`) is dismissed by swiping down or tapping
the scrim, which is the natural gesture; the typed note is discarded silently.
Same failure shape in `GameShell.kt:704` (Cancel discards). Also
`rememberSaveable(player.id)` (`SeatSheet.kt:167-168`) never re-reads
`player.note` after an undo, so the field can display stale text.

**P1 · 11 · `PlacedReminder` cannot hold what the rules need it to hold.**
`GameState.kt:6-11` is `(sourceId, label)`. Consequences: the Cerenovus's `Mad`
token doesn't say **which character**; the Harpy's `Mad` and `2nd` tokens aren't
linked; the Courtier's `Drunk 3` doesn't know which night it was placed; the
Grandmother's `Grandchild` doesn't point back at the Grandmother; and there is
**no free-text token at all** for an improvised ruling. `ReminderPicker`
(`SeatSheet.kt:502`) offers nine fixed generic labels and no way to type one.

**P1 · 12 · Deaths have no killer attribution.**
`DeathRecord` (`GameState.kt:77-90`) has `cause: DeathCause` — a 5-value enum
(`:75`) in which the Imp, the Pukka's delayed poison, a Gossip statement, a
Lycanthrope, a Godfather and a Witch curse are all "died in the night (other)".
Post-game review cannot answer "what killed Ana on night 3".

**P1 · 13 · Undo/redo is in-memory only and does not survive a reload.**
`GameViewModel.kt:39-40` / `WebGameViewModel.kt:35-36` are plain `ArrayDeque`s,
never persisted. On the PWA a tab reload, an iOS memory eviction, or an app
switch that kills the web view leaves the game restored from localStorage with
**zero** undo history — exactly when the ST most needs it. On Android, process
death does the same. `SavedData.kt:10-15` has no field for it.

**P1 · 14 · One saved game; ending the game destroys the log forever.**
`SavedData.game: GameState?` (`SavedData.kt:11`). `endGame()`
(`GameViewModel.kt:89-98`) nulls it and persists. `RevealSheet`'s "End game &
return home" (`GameExtras.kt:344`) is the normal end-of-evening flow, and it is
the moment the storyteller most wants to review the game with the table. Two
games in one evening is impossible without losing the first.

**P1 · 15 · No export or share of anything.** No `ClipboardManager`, no
`ACTION_SEND`, no `navigator.clipboard` anywhere in `app/src` or `web/src`.

**P1 · 16 · "Mark spent" is string-matched on ability text and marks the wrong
thing.** `NightScreen.kt:204`:
`character?.ability?.startsWith("Once per game", ignoreCase = true)`. Of the 21
once-per-game characters, this misses every one whose text doesn't *start* with
the phrase (e.g. Professor: *"Once per game, at night*, choose…"* does start;
Golem, Wizard, Philosopher, Fiddler, Toymaker, Doomsayer, Deus ex Fiasco do not
match the intended set at all, and seven of them ship no `No ability` token to
place). Worse, the chip places `PlacedReminder(character.id, "No ability")`
(`:270-273`) — semantically "this player has no ability", which is a *different*
game state from "this player's once-per-game is spent" and is what
`StatusEffects` and several `QuickResolutions` guards read (`:504`, `:264`).
And it only appears while that character's night step is expanded, so a
day-time once-per-game (Slayer, Artist, Fisherman) can never be marked from it.

**P1 · 17 · Countdown tokens never count down.** Neither expiry table
(`GameActions.kt:218-242`) nor anything else touches `courtier` `Drunk 3/2/1`,
`leviathan` `Day 1..5`, `riot` `Day 1..3`, `summoner`/`xaan` `Night 1..3`,
`po` `3 attacks`, `lunatic` `Attack 1..3`, `bureaucrat` `3 votes`, `hermit`
`1/2/3`, `alhadikhia` `1/2/3`. Eleven characters ship numbered tokens and the ST
must move all of them by hand every dusk.

**P1 · 18 · The game log is a third of a game log.** `GameExtras.kt:46-106`
covers deaths and nominations. It omits: what was said, what was shown, every
storyteller ruling, night choices, resurrections as events (only as a suffix),
reminder placements, notes, and the **voters' names** — `Nomination.voterIds`
(`GameState.kt:69`) is recorded and then never rendered (`:76` prints only the
count). Ordering is `compareBy({ it.day }, { !it.atNight })` (`:79`), which is
not a total order, so entries within one phase appear in an arbitrary,
list-append order that mixes deaths ahead of the nominations that caused them.

**P1 · 19 · Every keystroke rewrites the whole save, asynchronously.**
`GameViewModel.kt:130-132` launches a coroutine per change that re-serializes
`SavedData` — game + **all imported scripts** + notes session — and there is no
flush on lifecycle stop. A kill between the last tap and the write loses it.

**P2 · 20 · `NominationResult.WITHDRAWN` is dead.** Declared
`GameState.kt:59`, rendered `DayScreen.kt:343` and `GameExtras.kt:71`, never
produced. A withdrawn nomination (a real and common table event, and load-bearing
for the Virgin and the Goblin) cannot be recorded.

**P2 · 21 · `nightStepsDone` is wiped at dusk** (`GameActions.kt:262`) with no
archival, so "did I actually run the Ravenkeeper on night 3?" is unanswerable
the next morning.

**P2 · 22 · Edited show-card text is discarded.** `GuideShowDialog`
(`NightScreen.kt:366-453`) explicitly exists so the ST can type the Cerenovus's
command or the Mezepheles's word into a card
(`rememberSaveable(show.label)`, `:374-378`) — and the moment the dialog closes,
the text is gone. The next night the ST retypes the word from memory.

**P2 · 23 · The seat sheet has no history.** `SeatSheet.kt:156-384` shows the
current character, current reminders, and one note field. It cannot answer "what
has this seat been told, chosen, or said?" — which is the question a storyteller
asks when a player starts behaving oddly.

**P2 · 24 · No "no execution today" / day-summary record.** Derivable from
`deaths` for most purposes, but the ST also needs to record *why* (tie, no
nomination, Pacifist, Mastermind day) for the Zombuul, Godfather, Hatter,
Barber, Politician and Devil's Advocate conversations.

**P2 · 25 · `mastermindDayActive` is a one-way flag.** Set at
`GameShell.kt:515` (in the win-advisory handler), never cleared, carries no day
number. `mastermind.md` flags the same.

**P3 · 26 · `revive` silently deletes history.** `GameActions.kt:162-166`
removes the death record by index. Correct for "I mis-tapped", wrong if the ST
uses it for an in-game effect; there is no trace either way.

**P3 · 27 · Log rows carry no time-of-entry**, so two deaths in one night cannot
be ordered.

---

## Proposed behaviour (spec)

### A. One ledger, not six

Replace all competing proposals with a single append-only list on `GameState`.
Reconciliation table for the lead (proposal → unified):

| Proposed in | Name | Unified as |
|---|---|---|
| `devilsadvocate`, `courtier`, `lunatic`, `alhadikhia`, `goon`, `exorcist`, `assassin`, `gambler`, `grandmother` (9) | `NightChoice` | `LedgerEntry(kind = CHOICE)` |
| `general`, `highpriestess`, `monk`, `cultleader`, `chef`, `empath`, `fortuneteller`, `investigator`, `librarian`, `mayor` (10) | `NightRecord` | `LedgerEntry(kind = TOLD)` |
| `amnesiac`, `banshee`, `balloonist`, `bountyhunter`, `soldier`, `washerwoman` (6) | `NightAction` | `LedgerEntry(kind = CHOICE / TOLD)` |
| `artist`, `juggler`, `dreamer`, `slayer`, `goblin`, `saint`, `pacifist`, `heretic`, `alchemist`, `alhadikhia`, `mastermind`, `plaguedoctor` (12) | `DayAct` | `LedgerEntry(kind = STATEMENT / PRIVATE)` |
| `gossip` | `PublicStatement` | `LedgerEntry(kind = STATEMENT)` |
| `cerenovus`, `klutz`, `mutant`, `puzzlemaster`, `politician` (5) | `PublicClaim` | `LedgerEntry(kind = STATEMENT, sourceId = "claim")` |
| `fearmonger`, `banshee`, `goblin`, `organgrinder`, `vizier` (5) | `Announcement` | `LedgerEntry(kind = ANNOUNCE)` |
| `professor` | `DawnNote` | `LedgerEntry(kind = ANNOUNCE)` |
| `spy` | `Misregistration` | `LedgerEntry(kind = RULING, sourceId = "misregister")` |
| `mathematician`, `marionette` | `Malfunction` | `LedgerEntry(kind = RULING, sourceId = "malfunction")` + `verdict` |
| `plaguedoctor` | `StorytellerAbility` | `LedgerEntry(kind = SPENT/RULING)` + `GameState.secrets` |
| `plaguedoctor` | `storytellerReminders` | **kept verbatim** (§C) |
| `devilsadvocate` | `lastNightChoice: Map<String, Long>` | **rejected** — derived by `Memory.lastChoice` (§B) |
| `mezepheles`, `amnesiac`, `wizard` | `mezephelesWord`, `inventedAbility`, wish | `GameState.secrets: Map<String, String>` (§C) |
| `professor` | `insertedNightSteps` | out of scope here — see `professor.md`; it reads the ledger |

New engine types, all in `GameState.kt` (all fields defaulted so every existing
save deserializes unchanged — `Persistence.kt:16` and `WebApp.kt:16` both set
`ignoreUnknownKeys = true`, and both set `encodeDefaults = true`):

```kotlin
@Serializable
enum class LedgerKind {
    /** A character chose someone/something at night. Powers "different to last night". */
    CHOICE,
    /** Information actually delivered to a player (true or false). */
    TOLD,
    /** Something said in public during the day. */
    STATEMENT,
    /** A private day-time storyteller conversation (Savant, Artist, Fisherman, Amnesiac). */
    PRIVATE,
    /** A storyteller decision that must stay consistent (misregistration, madness, malfunction). */
    RULING,
    /** Something the storyteller owes the table at the next dawn/day start. */
    ANNOUNCE,
    /** A once-per-game ability was used. */
    SPENT,
    /** Free text with no other structure. */
    NOTE,
}

/** UNJUDGED until the storyteller rules; TRUE doubles as "correct" for guesses. */
@Serializable
enum class Verdict { UNJUDGED, TRUE, FALSE, ST_CHOICE }

@Serializable
data class LedgerEntry(
    val id: Long,
    /** Same numbering as [DeathRecord]: day N follows night N. */
    val cycle: Int,
    val atNight: Boolean,
    val kind: LedgerKind,
    /**
     * Character id this belongs to ("gossip", "devilsadvocate"), a night
     * marker ("DAWN"), or a pseudo-source ("claim", "misregister",
     * "malfunction", "" for a plain note).
     */
    val sourceId: String = "",
    /** Seat that acted / spoke / was told. Null for storyteller-only entries. */
    val actorId: Long? = null,
    /** Seats the entry is about: the DA's target, the Juggler's guessed seats. */
    val targetIds: List<Long> = emptyList(),
    /**
     * Characters named. Parallel to [targetIds] where both are used
     * (Juggler guess i = targetIds[i] is characterIds[i]).
     */
    val characterIds: List<String> = emptyList(),
    /** The words: the Gossip's statement, the Artist's question, the wish. */
    val text: String = "",
    /** What the storyteller actually showed/answered: "3", "YES", "Ravenkeeper", "warm". */
    val shown: String = "",
    val verdict: Verdict = Verdict.UNJUDGED,
    /** Whether the ACTOR's ability was malfunctioning when this happened. */
    val impaired: Boolean = false,
    /** True when the app believes the actor really holds [sourceId]; false for a bluffing claimant. */
    val genuine: Boolean = true,
    /** Cycle on which a later step consumed this entry (Gossip resolved, Juggler revealed). */
    val resolvedCycle: Int? = null,
    /** ANNOUNCE only: set once the storyteller has said it out loud. */
    val delivered: Boolean = false,
)
```

```kotlin
data class GameState(
    …,
    val ledger: List<LedgerEntry> = emptyList(),
    val nextLedgerId: Long = 1L,
    /** Tokens that belong in the centre of the grimoire, on no seat. */
    val storytellerReminders: List<PlacedReminder> = emptyList(),
    /**
     * Storyteller-invented secrets that must survive the whole game.
     * Keys: "mezepheles", "yaggababble", "wizard.wish", "wizard.price",
     * "wizard.clue", "amnesiac:<seatId>", "savant:<day>".
     */
    val secrets: Map<String, String> = emptyMap(),
)
```

`PlacedReminder` gains four defaulted fields:

```kotlin
@Serializable
data class PlacedReminder(
    val sourceId: String,
    val label: String,
    /** Character the token points at: Cerenovus's mad character, Courtier's target. */
    val characterId: String? = null,
    /** Seat the token points back at: Harpy's 2nd, Grandmother's grandchild. */
    val targetPlayerId: Long? = null,
    /** Free text for an improvised ruling. Rendered under the token; searchable. */
    val note: String = "",
    /** state.cycle when placed — powers countdowns and "placed 2 nights ago". */
    val placedCycle: Int = 0,
)
```

**Migration hazard to fix in the same change**: `NightScreen.kt:326-338`
compares whole tokens (`if (token == reminder)`) when counting multi-copy
placements. With new fields that comparison stops matching. Change it to compare
`(sourceId, label)`, which is what `placeExclusiveReminder`
(`GameActions.kt:197`) already does.

### B. Derived memory — no duplicated state

`devilsadvocate.md` proposed `lastNightChoice: Map<String, Long>`. Reject it:
a map cannot answer "what did the Exorcist choose two nights ago", breaks with
two Village Idiots, and needs its own undo/rollback discipline. The ledger is
append-only, so `undo()` (which restores a whole `GameState`) is automatically
correct. New object in the engine:

```kotlin
object Memory {
    /** The most recent CHOICE by [sourceId] strictly before [beforeCycle]. */
    fun lastChoice(state: GameState, sourceId: String, beforeCycle: Int = state.cycle): LedgerEntry? =
        state.ledger.lastOrNull {
            it.kind == LedgerKind.CHOICE && it.sourceId == sourceId && it.cycle < beforeCycle
        }

    /** Seats [sourceId] may NOT pick tonight because of a "different to last night" clause. */
    fun forbiddenTargets(state: GameState, sourceId: String): Set<Long> =
        lastChoice(state, sourceId)?.targetIds?.toSet().orEmpty()

    fun choicesBy(state: GameState, sourceId: String): List<LedgerEntry>
    fun isSpent(state: GameState, sourceId: String, actorId: Long? = null): Boolean
    fun statementsOn(state: GameState, day: Int, sourceId: String? = null, speakerId: Long? = null): List<LedgerEntry>
    fun unresolved(state: GameState, sourceId: String, day: Int): List<LedgerEntry>
    fun pendingAnnouncements(state: GameState): List<LedgerEntry>
    /** Everything ever told to, chosen by, or said by one seat — the seat-sheet history. */
    fun forPlayer(state: GameState, playerId: Long): List<LedgerEntry>
    /** The standing ruling for how [playerId] registers to [askedBy], if any. */
    fun ruling(state: GameState, playerId: Long, askedBy: String): LedgerEntry?
    /** Character types the Balloonist has already been shown. */
    fun typesSeen(state: GameState, lookup: (String) -> Character?, actorId: Long): List<Team>
    /** Nights/days elapsed since [sourceId]'s token was placed on [playerId] — Courtier countdown. */
    fun cyclesSince(state: GameState, playerId: Long, sourceId: String, label: String): Int?
}
```

New `GameActions` (each returns a new state, stamps `cycle`/`atNight` from
`state`, allocates `id = state.nextLedgerId` and increments it):

```kotlin
fun record(state: GameState, entry: LedgerEntry): GameState
fun recordChoice(state, sourceId: String, actorId: Long?, targetIds: List<Long>,
                 characterIds: List<String> = emptyList(), impaired: Boolean = false): GameState
fun recordTold(state, playerId: Long, sourceId: String, shown: String,
               impaired: Boolean = false, text: String = ""): GameState
fun recordStatement(state, speakerId: Long?, sourceId: String, text: String,
                    targetIds: List<Long> = emptyList(),
                    characterIds: List<String> = emptyList(), genuine: Boolean = true): GameState
fun recordPrivate(state, playerId: Long, sourceId: String, text: String, shown: String): GameState
fun recordRuling(state, sourceId: String, playerId: Long?, text: String,
                 characterIds: List<String> = emptyList()): GameState
fun announce(state, text: String, sourceId: String = ""): GameState
fun markDelivered(state, id: Long): GameState
fun setVerdict(state, id: Long, verdict: Verdict): GameState
fun resolveEntry(state, id: Long): GameState        // sets resolvedCycle = state.cycle
fun editEntry(state, id: Long, transform: (LedgerEntry) -> LedgerEntry): GameState
fun deleteEntry(state, id: Long): GameState
fun markSpent(state, sourceId: String, actorId: Long): GameState  // ledger SPENT + ("<id>","Used") token
fun addStorytellerReminder(state, reminder: PlacedReminder): GameState
fun removeStorytellerReminder(state, index: Int): GameState
fun setSecret(state, key: String, value: String): GameState
```

Wrapper methods must be added to **both**
`app/src/main/java/com/clocktower/grimoire/ui/GameViewModel.kt:194-223` **and**
`web/src/wasmJsMain/kotlin/com/clocktower/grimoire/ui/WebGameViewModel.kt:170-199`
(`web/build.gradle.kts:52` excludes the Android one). Consider extracting the
common wrappers into a shared `GameViewModelActions` interface implemented by
both to stop this class of drift.

### C. Storyteller-only tokens and secrets

`storytellerReminders` renders in the **centre of the grimoire**
(`GrimoireScreen.kt:228-234`, currently an empty `Box`): a small wrapped row of
tokens with a `+` chip. Tap a token to remove, `+` to add from the same
`ReminderPicker` used by the seat sheet (`SeatSheet.kt:492`). This is where the
Gossip's `DEAD`, the Yaggababble's `DEAD ×n`, the Leviathan's `Day N` and the
Riot's `Day N` live. Because they're `PlacedReminder`s they inherit the new
`note` field, so "the DEAD I owe for the 2nd phrase" is one long-press away.

`secrets` gets a **Setup-screen "Storyteller secrets" card**, shown only when a
relevant character is in the bag (`mezepheles`, `yaggababble`, `amnesiac`,
`wizard`, `savant`, `general`, `highpriestess`, `plaguedoctor`, `alchemist`,
`hermit`), each with a one-line text field and a "Show full-screen" chip that
reuses `ShowCard.Message`. The night step for that character then shows the
stored value inline (so the Mezepheles's word is re-showable on demand and the
ST never retypes it) — closing Defect 22.

### D. Night-step consumption

A new `StepLedgerPanel(viewModel, state, step)` rendered inside
`StepDetailPanel` (`NightScreen.kt:770`), **above** `QuickResolutions`
(`:834`), driven by one table so it works for every character without
per-character UI code:

```kotlin
/** What each night step consumes from, and writes to, the ledger. */
data class StepMemory(
    /** Statement kinds this step must resolve before it can be checked off. */
    val consumes: List<String> = emptyList(),   // sourceIds, e.g. listOf("gossip")
    /** True when this step's pick must differ from last night's. */
    val differentFromLastNight: Boolean = false,
    /** How many seats this step's actor chooses (0 = none). */
    val choiceCount: Int = 0,
    /** True when the actor also names a character (Cerenovus, Courtier, Philosopher). */
    val choosesCharacter: Boolean = false,
    /** Dawn text template, "" for none. */
    val announceOnResolve: String = "",
)
val STEP_MEMORY: Map<String, StepMemory>   // in NightGuide.kt, or night_guide.json
```

**D1 — statement consumption.** For a step whose `consumes` is non-empty, render
each `Memory.unresolved(state, sourceId, state.cycle - 1)` entry as one line
with three chips (the user's Gossip flow, in exactly one tap):

```
Gossip said yesterday:
  “Carol is the Empath”                   [ True ] [ False ] [ Didn't count ]
  (Gossip was poisoned when they said it — the ability still fires tonight.)
```

*True* → `setVerdict(TRUE)` + `resolveEntry` + immediately reveal a "who dies?"
seat-chip row wired to `GameActions.kill(..., DeathCause.STORYTELLER)` (and drop
a `("gossip","Dead")` into `storytellerReminders` if the ST defers). *False* /
*Didn't count* → `setVerdict` + `resolveEntry`, step proceeds. The impairment
line is generated from `entry.impaired`, quoting the wiki rule verbatim. The
step's checkbox (`NightScreen.kt:715`) is **blocked** while any consumable entry
for this source is unresolved, with the sub-label *"Resolve yesterday's Gossip
statement first."* — the same guard pattern already used for unfinished night
steps at `GameShell.kt:147-161`.

Table entries: `gossip` ← `gossip`; `juggler` ← `juggler` (night 2 only);
`towncrier` ← `claim`+nominations; `mutant`/`cerenovus`/`harpy`/`pixie` ←
`madness` rulings; `damsel` ← `damsel`; `alsaahir`, `goblin`, `vizier`,
`yaggababble` are consumed at the moment they're recorded, in the day UI.

**D2 — the choice recorder replaces the transient pick.** For any step with
`choiceCount > 0`, render a seat-chip row labelled *"Who did <Name> choose?"*
whose selection is **written to the ledger** via `recordChoice` on each tap
(not held in `rememberSaveable`). This deletes `NightScreen.kt:839`'s
un-keyed state (Defect 1) and simultaneously creates the memory Defect 2 needs.
`InfoCalc.compute` (`:863`) then reads
`Memory.lastChoice(state, step.id, state.cycle + 1)?.targetIds` instead of the
local variable — one line, and it is now cycle-correct by construction.

**D3 — "different to last night".** When `differentFromLastNight`, chips for
`Memory.forbiddenTargets(state, step.id)` render **disabled** with the
sub-label *"chosen last night"*, plus a headline line
*"Last night: Ana — must be someone else."* Also add a
`"They chose nobody / were not woken"` chip that records
`recordChoice(targetIds = emptyList())`, which is exactly what the Po needs to
know ("If your last choice was no-one, choose 3 players tonight").

**D4 — automatic `TOLD` capture.** Thread an `onShown: (String) -> Unit`
through `FullScreenShow` (`ShowCards.kt:82`) and the `onShow` lambdas
(`NightScreen.kt:157`, `:824`, `:892`, `:898`, `:918`, `:925`). Every card the
storyteller flashes writes
`recordTold(state, holderId, step.id, shown = <card summary>, impaired = …)`.
The false-info shortcuts (`:913-929`) write the same entry, so the log
distinguishes *"showed Bo: 1 (TRUE)"* from *"showed Bo: 3 (false — poisoned)"*.
Cost at the call site: one extra lambda argument.

**D5 — rulings.** Every `InfoCalc` caveat that names a Spy or Recluse
(`InfoCalc.kt:121-130`) gains an inline pair of chips
`[registers as good] [registers as evil]` (or a character picker) that writes
`recordRuling(sourceId = "misregister", playerId = spySeat, characterIds = […])`.
On the next step that asks about the same seat, `Memory.ruling` surfaces
*"You ruled on night 2 that Priya registers as the Imp."*

### E. The morning briefing

Two surfaces, one derivation. New pure function:

```kotlin
object Briefing {
    data class Line(val text: String, val entryId: Long? = null, val urgent: Boolean = false)
    /** Everything the storyteller owes the table this dawn, in speaking order. */
    fun forDawn(state: GameState, lookup: (String) -> Character?): List<Line>
    /** Everything that constrains today, for the top of the Day tab. */
    fun forDay(state: GameState, lookup: (String) -> Character?): List<Line>
}
```

`forDawn` order:
1. Deaths this night, by seat, with the attributed cause
   (*"Ana died — the Imp."* / *"Nobody died."*).
2. Resurrections: **"Bo is alive again."** ← the user's Professor request.
   Sourced from `deaths.filter { it.resurrected && it.day == cycle }` plus the
   `ANNOUNCE` entry the Professor step queues.
3. Undelivered `ANNOUNCE` entries (Fearmonger's new choice, Widow, Damsel,
   Snitch, Vizier, King/Choirboy).
4. Anything the app inserted into tonight's sheet that the ST should mention.

`forDay` order:
1. Madness in force: *"Cara is mad that she is the Empath (Cerenovus) — execute
   her today if she doesn't try."* Reads `("cerenovus","Mad")` **plus its new
   `characterId` field**.
2. Standing protections/rulings: *"Dan survives execution today (Devil's
   Advocate)."*, *"Everyone is drunk until dusk (Minstrel)."*
3. Counters: *"Leviathan — day 3 of 5."*, *"Courtier: Bo is drunk for 2 more
   days."*, *"Riot — day 2 of 3, nominees die."*
4. Owed statements: *"No Gossip statement recorded yesterday."*,
   *"The Juggler has not guessed yet (day 1 only)."*
5. Once-per-game reminders: *"Slayer, Artist and Fisherman are unspent."*

**Where it appears**: (a) `NightOrder.kt:59` — the DAWN step's `detail` becomes
`Briefing.forDawn(...).joinToString("\n")` instead of a constant, so the ST
reads it at the moment they wake the table; (b) a dismissible card at the top of
`DayScreen.kt:85`, above "Day N", each line with a checkbox that calls
`markDelivered`. Lines with no `entryId` are derived and always shown.

### F. The day recorder — "Today at the table"

The direct answer to *"make it easy to write down all the gossips even if Gossip
isn't in play"*. A card inserted in `DayScreen.kt` **above** the "New
nomination" card (i.e. before line 126), because it is consulted more often.
It is **entirely script-independent** — it never checks what's in play.

```
┌ Today at the table ─────────────────────────────────┐
│ [Ana][Bo][Cara][Dan][Eve]…  ← horizontal seat chips │
│ [ …said what?                            ] [ Add ]  │
│ ▾ more:  About: [seat chips]  As: [character] [kind]│
├──────────────────────────────────────────────────── │
│ Bo » “Carol is the Empath”      [T][F][?]   ✎  🗑   │
│ Cara » claims Chef                          ✎  🗑   │
│ ▸ Earlier days (4)                                  │
└─────────────────────────────────────────────────────┘
```

Interaction budget: **tap a seat, type, tap Add — 2 taps.** The optional second
row is collapsed. The truth chips render only for kinds where truth matters
(`gossip`, `juggler`, `slayer`, `savant`, `alsaahir`, `damsel`, `goblin`);
everything else is a plain row. Contextual affordances layered on top, none of
them required:

- If a living Gossip is in play and today has no `gossip` entry, the card shows
  one imperative line — *"No Gossip statement recorded today."* — with a
  **[Record Gossip statement]** button that pre-fills speaker + `sourceId`.
- If a Juggler is in play on day 1 with no `juggler` entry, a
  **[Record Juggler guesses]** button opens a 5-row seat+character picker that
  writes one entry with parallel `targetIds`/`characterIds`.
- Kind defaults to `"claim"`, or to the speaker's own character id when the
  speaker holds a statement character.

Two more entry points, both because the ST is often not on the Day tab:
- `SeatSheet.kt:309-322` gains **"Record what they said"** next to "Add
  reminder", opening the same composer with the speaker pre-filled.
- The Grimoire tab gains a small always-present **"＋ note"** chip next to the
  bluffs row (`GrimoireScreen.kt:176-197`) that opens the composer with no
  speaker — replacing the two-menu-taps `storytellerNotes` dialog for the
  common "write this down right now" case. `storytellerNotes` stays as the
  game-wide scratchpad but its dialog's **Cancel** becomes **Close**, saving on
  dismiss (Defect 9/10).

### G. Seat-sheet history

`SeatSheet.kt` gains a collapsed **"History"** section under the reminders
(`:324-355`), listing `Memory.forPlayer(state, playerId)` newest-first, one
compact row each:

```
N3  told: “Ravenkeeper → the Imp”
D2  said: “I'm the Chef”                     (claim)
N2  chosen by the Poisoner
D1  voted for Bo, Cara
N1  told: “0” (TRUE)
```

Deaths, nominations they made or received, and votes they cast are merged in
from `state.deaths` / `state.nominations` by the same helper, so the row list is
one call. This closes Defect 23 and is the highest-value read surface in the app
during a live game.

### H. Persistence, undo and export

1. **Everything through `update{}`.** Removing the transient picks (§D2) and
   saving notes on dismiss (§F) puts every storyteller decision in `GameState`,
   which means undo covers it automatically. Audit the remaining
   `rememberSaveable`s: `NightScreen.kt:839` (delete), `:374-378` (persist to
   `secrets`), `SeatSheet.kt:167-168` (key on `player.id to player.note`),
   `GameShell.kt:686` (save on dismiss), `DayScreen.kt:58-60` (fine — a
   nomination draft is genuinely transient, and it is already reconciled at
   `:65-69`).
2. **Persist undo history.** `SavedData` gains
   `val undo: List<GameState> = emptyList()` and `val redo: List<GameState> = emptyList()`,
   bounded to the last **20** states (not 100 — 100 full `GameState`s including
   the `Script` will blow the localStorage quota). Both view models restore them
   at init. This is the difference between "the PWA reloaded and I lost my undo"
   and a recoverable mistake.
3. **Deduplicate the script out of the snapshots.** Before the previous point is
   safe, move `Script` out of `GameState` into `SavedData` (or store
   `scriptId` + a side table). Today every undo snapshot carries a full copy of
   the script including all `customCharacters` (`GameState.kt:95`,
   `Script.kt:17-26`). This is the single biggest save-size win and is a
   prerequisite for both multi-save and persisted undo.
4. **Multiple saved games + archive.** `SavedData.game: GameState?` →
   `val games: List<GameState> = emptyList()` + `val currentGameId: String? = null`,
   with `GameState.id: String` (a `newGame` timestamp). `endGame()` stops
   deleting: it clears `currentGameId` and leaves the game in `games`, capped at
   the last 10. `HomeScreen.kt:168-190` grows a "Past games" list that opens the
   log/reveal read-only. Closes Defects 14 and the post-game-review half of 18.
5. **Save failure must be visible.** `WebApp.kt:29-35` returns a `Boolean`;
   `WebGameViewModel` exposes `val saveFailed: StateFlow<Boolean>`;
   `GameShell` shows a persistent red banner *"Not saving — browser storage is
   full. Export the game now."* Add `SavedData.schemaVersion: Int = 1`.
6. **Android flush on stop.** Keep the async write, but add a
   `LifecycleEventObserver` on `ON_STOP` that awaits the pending
   `dataStore.updateData`, and stamp `GameState.revision: Int` (monotonic) so a
   torn save is detectable.
7. **Export / share.** New pure formatter in the engine so both platforms share
   it:
   ```kotlin
   object GameLog {
       data class Row(val cycle: Int, val atNight: Boolean, val seq: Int, val text: String)
       fun rows(state: GameState, lookup: (String) -> Character?): List<Row>
       fun toMarkdown(state: GameState, lookup: (String) -> Character?): String
   }
   ```
   `rows` merges `deaths`, `nominations` (with **voter names**, from the already
   recorded `voterIds`), and the whole `ledger`, ordered by
   `(cycle, atNight desc, seq)` where `seq` is the entry id / list index — a
   total order, fixing Defect 27 and the ordering half of 18.
   `GameLogDialog` (`GameExtras.kt:46`) renders `rows` and gains
   **[Copy]** / **[Share]**; `RevealSheet` (`GameExtras.kt:344`) gains
   **[Copy full game log]** next to "End game".
8. **Killer attribution.** `DeathRecord` gains
   `val killerPlayerId: Long? = null` and `val killerSourceId: String = ""`
   (defaulted). `GameActions.kill` takes them as optional parameters; every
   caller that knows (the `DemonKillPanel` at `NightScreen.kt:628`, the Gossip
   resolution, the Lycanthrope, the Godfather, execution at `DayScreen.kt:112`
   and `:354`) passes them. Closes Defect 12.

### I. Counters (Defect 17)

Add a third expiry-style table beside `EXPIRES_AT_DAWN`/`EXPIRES_AT_DUSK`
(`GameActions.kt:218-242`) — a **countdown** table applied in the same
`advancePhase` pass:

```kotlin
/** (sourceId, label) -> next label, or null to remove. Applied at dusk. */
private val COUNTS_DOWN_AT_DUSK: Map<Pair<String, String>, String?> = mapOf(
    ("courtier" to "Drunk 3") to "Drunk 2",
    ("courtier" to "Drunk 2") to "Drunk 1",
    ("courtier" to "Drunk 1") to null,
)
/** (sourceId, label) -> next label. Applied at dawn, in storytellerReminders. */
private val COUNTS_UP_AT_DAWN: Map<Pair<String, String>, String> = mapOf(
    ("leviathan" to "Day 1") to "Day 2", … ("leviathan" to "Day 4") to "Day 5",
    ("riot" to "Day 1") to "Day 2", ("riot" to "Day 2") to "Day 3",
    ("summoner" to "Night 1") to "Night 2", ("summoner" to "Night 2") to "Night 3",
    ("xaan" to "Night 1") to "Night 2", ("xaan" to "Night 2") to "Night 3",
)
```
The `Day 5` → *nothing* transition, `Riot Day 3`, `Summoner Night 3` and
`Xaan Night X` all raise `Briefing` lines rather than acting. With
`PlacedReminder.placedCycle` (§A), `Memory.cyclesSince` gives a second,
label-independent way to compute any countdown for homebrew.

### UI text (short, imperative, storyteller voice)

- Day recorder placeholder: `…said what?`
- Day recorder empty state: `Nothing recorded today. Anything anyone says in public can go here.`
- Gossip nudge: `No Gossip statement recorded today.` → `[Record it]`
- Gossip night step: `Gossip said: “<text>” — was it true?` → `[True] [False] [Didn't count]`
- Gossip impaired: `The Gossip was poisoned when they said it. If they are sober now, the kill still happens.`
- Different-to-last-night: `Last night: Ana. Choose someone else.` / chip sub-label `chosen last night`
- No-choice chip: `They chose nobody`
- Professor dawn: `Bo is alive again.`
- No deaths: `Nobody died tonight.`
- Madness: `Cara is mad that she is the Empath. Execute her today if she doesn't try.`
- Blocked check-off: `Resolve yesterday's Gossip statement first.`
- Save failure: `Not saving — browser storage is full. Copy the game log now.`
- Seat history header: `Everything about this seat`

### Data changes

- `characters.json` — no changes required.
- `night_guide.json` — each entry gains an optional `"memory"` object mirroring
  `StepMemory` (`consumes`, `differentFromLastNight`, `choiceCount`,
  `choosesCharacter`, `announceOnResolve`). Populating it for `devilsadvocate`,
  `exorcist`, `balloonist`, `po`, `pukka`, `gossip`, `juggler`, `cerenovus`,
  `harpy`, `courtier`, `preacher`, `fearmonger`, `lycanthrope`, `widow`,
  `barista`, `duchess` covers every case in the inventory table above.
- `night_and_jinxes.json` — no changes.

---

## Tests to add

Engine tests, `engine/src/test/kotlin/com/clocktower/engine/` (conventions from
`GameActionsTest.kt:11-17`: `GameData.loadDefault()`, the `tb` script,
`GameActions.newGame(tb, (1..n).map { "P$it" })`). New file
`MemoryTest.kt` unless noted.

1. **`lastChoice survives token expiry`** — *Given* night 1 with a Devil's
   Advocate, *when* `recordChoice(state, "devilsadvocate", da, listOf(ana))`
   then `advancePhase` (dawn) then `advancePhase` (dusk, which runs
   `clearEphemeral(EXPIRES_AT_DUSK)`), *then*
   `Memory.lastChoice(state, "devilsadvocate")?.targetIds == listOf(ana)` even
   though no seat holds a `Survives execution` token. **Fails today** —
   `Memory` does not exist and the information is destroyed at
   `GameActions.kt:238`.

2. **`forbiddenTargets blocks a repeat`** — *Given* the state from (1) at cycle
   2, *then* `Memory.forbiddenTargets(state, "devilsadvocate") == setOf(ana)`
   and, after `recordChoice(..., listOf(bo))` on night 2 and another dusk,
   `forbiddenTargets == setOf(bo)` — the constraint slides forward exactly one
   night.

3. **`lastChoice is cycle-scoped, not just "the last one"`** — *Given* two
   `CHOICE` entries for `"exorcist"` on cycles 2 and 3, *then*
   `Memory.lastChoice(state, "exorcist", beforeCycle = 3)!!.cycle == 2`.
   Guards the off-by-one that Defect 1 is made of.

4. **`an empty choice is recorded, not skipped`** — *Given* a Po, *when*
   `recordChoice(state, "po", po, emptyList())` on night 2, *then*
   `Memory.lastChoice(state, "po")!!.targetIds.isEmpty()` and a helper
   `Memory.choseNobodyLastNight(state, "po")` is true. **Fails today** — there
   is no way to represent "chose nobody".

5. **`a statement is recorded regardless of what is in play`** — *Given* a
   Trouble Brewing game with **no** Gossip, *when*
   `recordStatement(state, bo, "gossip", "Carol is the Empath")`, *then* the
   entry exists with `cycle == state.cycle`, `atNight == false` and
   `verdict == UNJUDGED`. This is the user's literal request.

6. **`statement verdict and resolution`** — *Given* a `gossip` statement on day
   1, *when* night 2 `setVerdict(id, TRUE)` and `resolveEntry(id)`, *then*
   `Memory.unresolved(state, "gossip", day = 1).isEmpty()` and the entry's
   `resolvedCycle == 2`.

7. **`impairment at speaking time is preserved`** — *Given* a poisoned Gossip on
   day 1, *when* the statement is recorded with `impaired = true` and the poison
   is removed before night 2, *then* the stored entry still has
   `impaired == true` (the wiki's "still kills a player" rule needs the *old*
   value, not the current one).

8. **`Juggler guesses round-trip with parallel arrays`** — *Given* a `juggler`
   entry with `targetIds = [a, b, c]` and `characterIds = ["chef","empath","imp"]`,
   *then* the count of correct guesses computed against the grimoire is 2, and
   the entry survives a serialize/deserialize round-trip through
   `Json.encodeToString(GameState.serializer(), …)`.

9. **`resurrection queues a dawn announcement`** (`GameActionsTest.kt`) —
   *Given* a dead Bo at night 3, *when* `GameActions.resurrect(state, bo)` runs,
   *then* `Memory.pendingAnnouncements(state)` contains one entry whose `text`
   is `"Bo is alive again."` and `delivered == false`; *and* after
   `advancePhase` to day 3, `Briefing.forDawn(state, lookup)` contains that
   line. **Fails today** — `resurrect` (`GameActions.kt:173`) has no
   announcement side-effect and `Briefing` does not exist.

10. **`markDelivered clears the briefing line`** — *Given* (9), *when*
    `markDelivered(state, id)`, *then* `pendingAnnouncements` is empty but the
    entry is still in `state.ledger` for the log.

11. **`the dawn briefing names the dead and their cause`** — *Given* Ana killed
    by `kill(..., DeathCause.DEMON, killerSourceId = "imp")` on night 2, *then*
    `Briefing.forDawn` starts with `"Ana died — the Imp."`; *and given* no
    deaths, it starts with `"Nobody died tonight."`

12. **`madness carries its character`** — *Given*
    `PlacedReminder("cerenovus", "Mad", characterId = "empath")` on Cara, *then*
    `Briefing.forDay` contains
    `"Cara is mad that she is the Empath"`. **Fails today** — `PlacedReminder`
    (`GameState.kt:6-11`) cannot hold the character.

13. **`Courtier counts down over three dusks`** (`GameActionsTest.kt`) —
    *Given* `("courtier","Drunk 3")` on Bo at night 2, *when* `advancePhase` is
    run to dusk three times, *then* the label reads `Drunk 2`, then `Drunk 1`,
    then the token is gone; *and* `StatusEffects.isImpaired(state, …, bo)` is
    true for the first two and false after. **Fails today** — no table touches
    it.

14. **`storyteller reminders live off-seat and survive expiry`** — *Given*
    `addStorytellerReminder(state, PlacedReminder("gossip", "Dead"))`, *when*
    `advancePhase` runs through a full dawn and dusk, *then* the token is still
    in `state.storytellerReminders` and no `Player.reminders` changed.

15. **`told entries record the lie, not the truth`** — *Given* a poisoned Empath
    whose true count is 1, *when* the ST shows 3, *then* the ledger holds
    `LedgerEntry(kind = TOLD, sourceId = "empath", shown = "3", impaired = true)`
    and `Memory.forPlayer(state, empathSeat)` returns it.

16. **`a misregistration ruling is recalled for the next asker`** — *Given*
    `recordRuling(sourceId = "misregister", playerId = recluse, characterIds = listOf("imp"), text = "for the Fortune Teller")`
    on night 2, *then* on night 3
    `Memory.ruling(state, recluse, "misregister")!!.characterIds == listOf("imp")`.

17. **`markSpent is idempotent and queryable`** — *Given* a Professor,
    *when* `markSpent(state, "professor", prof)` runs twice, *then* the seat
    holds exactly one `("professor","Used")` token, the ledger holds exactly one
    `SPENT` entry, and `Memory.isSpent(state, "professor", prof)` is true.

18. **`the ledger is undo-transparent`** (`GameActionsTest.kt` style, but the
    view-model behaviour is asserted structurally) — *Given* a state `s0`,
    *when* `recordStatement` produces `s1`, *then* `s1 != s0` and the previous
    state `s0` still has the smaller `nextLedgerId` — i.e. restoring `s0`
    restores the counter, so a redo after an unrelated edit cannot mint a
    duplicate id.

19. **`GameLog.rows is a total order`** — *Given* two deaths and two nominations
    on day 2 plus three ledger entries, *then* `GameLog.rows` returns them in
    strictly non-decreasing `(cycle, night-before-day, seq)` order and is stable
    across two calls. **Fails today** — `GameExtras.kt:79` sorts on two keys
    only.

20. **`GameLog.toMarkdown names voters`** — *Given* a recorded nomination with
    `voterIds = [a, b]`, *then* the markdown contains both player names, not
    just `2 votes`. **Fails today** — `GameExtras.kt:76`.

21. **`old saves deserialize`** (`ScriptParserTest.kt` neighbourhood or a new
    `PersistenceTest.kt`) — *Given* a JSON `GameState` written before this
    change (no `ledger`, no `storytellerReminders`, no `secrets`,
    `PlacedReminder` with two fields), *when* decoded with the app's `Json`
    config, *then* it decodes with empty defaults and re-encodes without loss.

22. **`multi-copy reminder placement ignores the new fields`**
    (`GameActionsTest.kt`) — *Given* a Courtier's three `Drunk` tokens with
    different `placedCycle`s, *when* the tray's placement logic runs, *then*
    copies are counted by `(sourceId, label)` and the fourth placement recycles
    the first. Guards the `NightScreen.kt:326-338` regression called out in §A.

23. **`secrets survive the whole game`** — *Given*
    `setSecret(state, "mezepheles", "clavicle")` at setup, *when* the game runs
    to night 4 through every `advancePhase`, *then* the value is unchanged and
    round-trips through serialization.

24. **`Balloonist type memory`** — *Given* `TOLD` entries for `"balloonist"` on
    nights 1–3 with `characterIds` of a Townsfolk, an Outsider and a Minion,
    *then* `Memory.typesSeen(...)` returns those three teams in order and
    excludes them from night 4's candidate list.
