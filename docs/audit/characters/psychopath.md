# Psychopath (psychopath) — exp (Carousel) minion

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Psychopath>

Current ability text:

> "Each day, before nominations, you may publicly choose a player: they die.
> If executed, you only die if you lose roshambo."

`characters.json:1865` matches this text exactly — **no drift**.

### How to run (wiki, verbatim, in order)

- "Once per day, before you have called for nominations, the Psychopath may publicly choose a player."
- "That player **dies**."
- "If the Psychopath is executed, the Psychopath and the nominator play a game of Roshambo."
- "If the Psychopath loses, they **die**."
- "If the Psychopath draws or wins, they live."
- "Either way, the day ends, since there is only one execution per day."
- "If the Psychopath is executed due to a self-nomination, then the Psychopath plays Roshambo with you instead."

### Examples (verbatim)

- "The Psychopath chooses to kill the Sailor. The Sailor is sober, so does not die." / "The Psychopath may not use their ability again today."
- "The Psychopath has been nominated by the Barber, and is executed. In Roshambo, the Barber has rock and the Psychopath has rock, so the Psychopath lives."
- "The next day, the Saint nominates and executes the Psychopath. The Saint has paper and the Psychopath has scissors, so the Psychopath lives."
- "The next day, the Barber nominates and executes the Psychopath again. The Barber has rock and the Psychopath has scissors, so the Psychopath dies."

### Consequences that follow from the text and examples

- The day kill is an ordinary death, **not** an execution: normal death-prevention applies (Sailor's "you can't die", Tea Lady, Fool, Innkeeper's `Protected`, Devil's Advocate's execution-only protection does **not** apply because this is not an execution; Monk/Soldier do **not** apply because it is not a Demon kill). The Sailor example confirms protection is checked.
- The ability is **once per day** and **only before nominations open**.
- The Psychopath is a Minion: dead Minions have no ability, so a dead Psychopath cannot kill, and a drunk/poisoned Psychopath's day kill does not happen.
- Being executed and surviving roshambo **still consumes the day's execution** — no further nominations, votes or executions that day, and nobody else dies by execution.
- Roshambo opponent = the **nominator**, except for a self-nomination, where it is the **Storyteller**.

### Jinx (wiki)

- **Lil' Monsta**: "If the Psychopath is babysitting Lil' Monsta, they die when executed."

### Interactions worth calling out

- **Vizier** ("You cannot die during the day") is immune to the Psychopath's day kill.
- The day kill can fire on-death triggers that resolve during the day (Moonchild, Sweetheart, Godfather's "an Outsider died today", Minstrel's execute-only clause does not apply, Barber's swap, Poppy Grower, Zombuul's first death, etc.).
- A Psychopath execution that they survive still ends the day — so a Mastermind day, a Vizier immediate execution, or another pending nomination is all cancelled.

## What the app does today

Data paths — **this is the complete list**:
- `engine/src/main/resources/botc/data/characters.json:1865` — id, name, text, `reminders: []`, empty first/other night reminders.
- `engine/src/main/resources/botc/data/raw_exp_evil_outsiders.json:263` — the same record in the raw import.

That is all. There is:
- **no** entry in `night_guide.json` (correct in the sense that the Psychopath does not wake, but it means the character has no run-book anywhere in the app);
- **no** entry in either night-order list in `night_and_jinxes.json` (correct — no night action);
- **no** jinx involving `psychopath` in `night_and_jinxes.json` (the Lil' Monsta jinx is missing);
- **no** mention of `psychopath` anywhere in `engine/src` Kotlin or `app/src`.

Storyteller experience today, in full:
1. The Psychopath's public day kill: open the Grimoire tab, tap the victim's seat, press **"Other death"** (`SeatSheet.kt:277-279`, `DeathCause.STORYTELLER`). `StatusEffects.deathNotes` runs first and a protection dialog appears if the seat looks protected (`SeatSheet.kt:256-307`) — that part works. The game log then reads "<name> died (storyteller)" (`GameExtras.kt:58`). Nothing records *who* killed them, nothing stops a second Psychopath kill on the same day, nothing checks that nominations have not started, and nothing checks the Psychopath is alive and sober.
2. The Psychopath being executed: `DayScreen.kt:111-114` ("Execute" on the block banner) and `DayScreen.kt:350-357` (per-nomination "Execute") call `viewModel.kill(id, DeathCause.EXECUTION)` **immediately and unconditionally**. There is no roshambo prompt, no survive-the-execution branch, and the Day screen keeps accepting further nominations afterwards. `StatusEffects.nominationWarnings` (`StatusEffects.kt:132-166`) knows about Witch, Golem, Virgin, Fearmonger and Cerenovus but not the Psychopath.
3. The "day ends" consequence is entirely on the Storyteller. `GameShell.kt:141-146` only guards the reverse case (someone on the block who has not died yet).

## Defects and gaps

1. **P0 · Executing the Psychopath kills them outright, with no roshambo** — the rules say they die only if they lose roshambo against the nominator (or the Storyteller on a self-nomination). The app kills them on one tap. `DayScreen.kt:111-114`, `DayScreen.kt:350-357`, `SeatSheet.kt:274-276`. Repro: nominate the Psychopath, record enough votes, tap Execute — they are dead and the game log says "executed".
2. **P0 · The day does not end after a Psychopath execution** — "Either way, the day ends, since there is only one execution per day." After the Psychopath survives, the app still offers new nominations, still lets another player reach the block, and `GameShell.kt:141-146` will happily execute them. `DayScreen.kt:126-255`. Repro: execute the Psychopath, decline the kill, then nominate someone else — the app permits it.
3. **P0 · No support at all for the day kill** — the single most visible thing this character does has no button, no record, no once-per-day gate and no legality check. The Storyteller must know that it must happen *before nominations*, that it is blocked while the Psychopath is dead or drunk/poisoned, and that it cannot touch the Vizier. `SeatSheet.kt:277` "Other death" is the only route and carries none of that.
4. **P1 · No once-per-day tracking** — nothing marks the ability used today, and `characters.json:1865` gives the Psychopath no reminder token at all to mark it with. The wiki example specifically calls this out: "The Psychopath may not use their ability again today."
5. **P1 · The kill is not attributed** — `DeathCause` (`GameState.kt:75`) has no Psychopath-shaped cause, so the log (`GameExtras.kt:53-64`) says "died (storyteller)" and any future Undertaker/Cannibal/Mathematician reasoning has to be reconstructed from memory.
6. **P1 · No day-start reminder that a Psychopath is in play** — the ability window is "before nominations". Nothing prompts the Storyteller to ask the Psychopath (privately or publicly, per table convention) before opening nominations, and nothing tells them the window has closed once the first nomination is recorded.
7. **P1 · The Vizier immunity is not enforced** — a Vizier "cannot die during the day", so the Psychopath cannot kill them. `StatusEffects.deathNotes` (`StatusEffects.kt:52-129`) has no Vizier case, so the app offers no warning and kills them. (Cross-listed in `vizier.md`.)
8. **P2 · The Lil' Monsta jinx is missing from `night_and_jinxes.json`** — "If the Psychopath is babysitting Lil' Monsta, they die when executed." This one is mechanical: it overrides the roshambo clause entirely.
9. **P2 · No `night_guide.json` entry** — every other Carousel minion in this scope has one. Even though the Psychopath does not wake, the run-book (once per day, before nominations, roshambo on execution, day ends) has to live somewhere the Storyteller can reach at the table. A `day` section, or an entry rendered on the Day screen, is needed.
10. **P3 · No roshambo helper** — a table without hands free (PWA on a phone) benefits from a "Rock / Paper / Scissors — who won?" three-button prompt that records the result in the log.

## Proposed behaviour (spec)

The Psychopath is the clearest case in this scope for a **day-phase ability framework** (see the cross-cutting note at the end). Expressed in the brief's structured form, with "night" read as "day":

### Day ability: the public kill

- **when**: DAY phase, `state.cycle >= 1`, and **before the first non-exile nomination of the day is recorded** (`state.nominations.none { it.day == cycle && !it.isExile }`).
- **wake/eligibility condition**: holder alive AND `!StatusEffects.isImpaired(state, lookup, holder)` AND the `psychopath`/`Used today` token is not on them.
- **targets**: exactly 1, any player (alive; travellers allowed; self allowed). Default sort: alive first. **Disable the Vizier** with the inline reason "The Vizier cannot die during the day."
- **immediate effects**:
  - Run `StatusEffects.deathNotes` for the target and show the protection dialog exactly as `SeatSheet.kt:288-307` does — the Sailor example requires this.
  - On confirm: `kill(target, DeathCause.PSYCHOPATH)` (new cause) and `placeExclusiveReminder(holder, PlacedReminder("psychopath", "Used today"))`.
  - Death triggers (Moonchild, Sweetheart, Godfather, Barber, Zombuul first death…) surface through the existing `deathNotes` list — no extra work.
- **deferred effects**: none.
- **expiry**: `psychopath` / `Used today` → **EXPIRES_AT_DUSK** (add to `GameActions.kt:231-242`, so it is clear before the next day).
- **information**: none.
- **visibility**: the choice is public — the app should offer a "announce publicly" line: `<Psychopath name> publicly chooses <target>: they die.`
- **day-time inputs the app must record**: which player was chosen and whether they actually died (protection may have saved them). Log entry: `D<n>: <psychopath> publicly killed <target>` or `… chose <target> — no death (protected)`.

### Execution: roshambo

Hook into the single execution path (both `DayScreen.kt:111-114` and `:350-357` and `SeatSheet.kt:274`). When the player about to be executed is a **living Psychopath with their ability** (alive, not impaired, no `No ability` token):

1. **Do not kill.** Open a **Roshambo dialog**:
   - Header: `<Psychopath> was executed — roshambo against <nominator name>` (or `against you (the Storyteller)` when `nomination.nominatorId == nomination.nomineeId`).
   - Three outcome buttons: `Psychopath lost — they die` / `Draw — they live` / `Psychopath won — they live`.
   - Footnote: `Either way the day ends now — this was the day's one execution.`
2. On "lost": `kill(psychopath, DeathCause.EXECUTION)`.
3. On draw/win: **no death**, but record the execution: a `Nomination`-adjacent fact that the day's execution has been spent (see below).
4. In **both** branches: set a new `GameState` flag `executionSpentDay: Int?` (or record a `DeathRecord`-less "execution happened" marker) so that:
   - the Day screen closes nominations: the "New nomination" card is replaced by `The day is over — the Psychopath's execution was today's one execution.`;
   - `GameShell.requestPhaseAdvance`'s dusk guard (`GameShell.kt:141-146`) does not complain about anyone still on the block;
   - the game log records `D<n>: <psychopath> executed — survived roshambo` / `— lost roshambo, died`.
5. **Lil' Monsta jinx**: if the Psychopath holds the Lil' Monsta token (reminder `lilmonsta`/`Is the Demon` or whatever the babysitting marker is), skip the dialog and kill them normally, with the note `Psychopath is babysitting Lil' Monsta — they die when executed (jinx).`

### Day-start briefing entries

- `Psychopath (<name>) may publicly kill someone — ask BEFORE opening nominations.` (only while the holder is alive and unimpaired and the token is unspent)
- Once the first nomination is recorded: `Psychopath's window has closed for today.`
- If the Psychopath is dead or impaired: `Psychopath has no ability today (<dead / poisoned by X>).`

### Nomination-time warning (`StatusEffects.nominationWarnings`, `StatusEffects.kt:132-166`)

Add: when the nominee is a living, unimpaired Psychopath →
`If executed, <psychopath> plays roshambo with <nominator>; they die only if they lose — and the day ends either way.`
When `nominator == nominee` → `…plays roshambo with YOU (self-nomination).`

### UI text

- Day tool button: `Psychopath: public kill`
- Picker header: `Who does <name> publicly choose? They die.`
- Confirm: `<target> dies` / `Protected — no death`
- Roshambo dialog title: `Psychopath executed — play roshambo`

### Data changes

- `characters.json:1865`: add a reminder token `"Used today"` (this is an app-side bookkeeping token, not an official one — flag it as such in the reminder picker, or hold it as engine state instead if the project prefers not to invent tokens).
- `night_and_jinxes.json`: add `{"id1":"psychopath","id2":"lilmonsta","reason":"If the Psychopath is babysitting Lil' Monsta, they die when executed."}`.
- `night_guide.json`: add a `psychopath` entry carrying the How-to-Run text, rendered on the Day screen rather than the Night screen.
- `GameState.kt:75`: add `DeathCause.PSYCHOPATH` (log text "killed by the Psychopath").

## Tests to add

1. **Roshambo blocks the execution death** — *Given* a living unimpaired Psychopath on the block, *When* the execution is resolved with "draw", *Then* the Psychopath is still `alive` and no `DeathRecord` exists for them.
2. **Roshambo loss kills** — same setup, *When* resolved with "lost", *Then* a `DeathRecord(cause = EXECUTION)` exists.
3. **The day ends either way** — *Given* either outcome above, *Then* the day's execution is marked spent, and attempting to record a further non-exile nomination on the same `cycle` is rejected / the Day screen reports the day closed.
4. **Dead Psychopath executes normally** — *Given* the Psychopath already holds a `No ability` token (or is drunk), *When* executed, *Then* they die with no roshambo prompt.
5. **Lil' Monsta babysitter dies** — *Given* the Psychopath holds the Lil' Monsta token, *When* executed, *Then* they die with no roshambo prompt.
6. **Day kill respects protection** — *Given* a sober Sailor, *When* the Psychopath's day kill targets them, *Then* `deathNotes` returns "The Sailor can't die." and the default action is "no death".
7. **Day kill is once per day** — *Given* the Psychopath used their kill, *Then* the day tool is disabled for the rest of the day; *When* `advancePhase` reaches the next day, *Then* the `Used today` token is gone and the tool is enabled again.
8. **Day kill window closes at the first nomination** — *Given* one non-exile nomination recorded today, *Then* the Psychopath day tool is disabled with the reason "nominations have opened".
9. **Vizier is immune** — *Given* a living Vizier, *When* the Psychopath's target picker is opened, *Then* the Vizier is not selectable (or is selectable only through an explicit override).
10. **Impaired Psychopath has no day kill** — *Given* a `Poisoned` token on the Psychopath, *Then* the day tool is disabled and the day briefing says so.
