# Cannibal (cannibal) — experimental (Carousel) townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Cannibal>

Current ability text (verbatim):

> "You have the ability of the recently killed executee. If they are evil, you
> are poisoned until a good player dies by execution."

`characters.json` matches this text exactly — no drift.

### How to run (wiki, verbatim bullets)

- "If a good player dies by execution, mark them with the **LUNCH** reminder,
  and remove the Cannibal's **POISONED** reminder if necessary."
- "The Cannibal now has this good player's ability (*do not say which*), and
  will wake at night when this good character would normally wake."
- "If an evil player dies by execution, mark them with the **LUNCH** reminder
  and mark the Cannibal with the **POISONED** reminder."
- "You may wake them when this evil character would normally wake, and pretend
  that they have a new ability."
- "Pay attention to which character each evil player is bluffing as. If they
  are executed, then their bluffed ability is the best one to pretend that the
  Cannibal has gained."

### Summary / clarifications (wiki, verbatim bullets)

- "If a good player dies by execution, the Cannibal gains that player's
  ability. If an evil player dies by execution, the Cannibal only thinks that
  they gain an ability, since the Cannibal is poisoned."
- "Each time a player dies by execution, the Cannibal loses the ability of the
  previous player."
- "Executing a dead player won't grant the Cannibal an ability. Executing a
  living player who doesn't die won't grant the Cannibal an ability. A player
  must be executed **and die** for the Cannibal to gain their ability."
- "The Cannibal is not told which ability they have gained. They must figure
  that out for themselves."
- "If the Cannibal has an 'even if dead' ability, such as the Recluse, or an
  ability that implies it works while dead, such as the Ravenkeeper or
  Sweetheart, the Cannibal keeps that ability when they die, but loses their
  Cannibal ability."

### Examples (wiki, verbatim)

- "The Clockmaker is executed and dies. That night, the Cannibal learns a '2'
  because the Demon and Minion are two steps apart."
- "It is the third night and the Widow was executed today. Because the Widow
  was bluffing as the Fortune Teller, the Cannibal is prompted to choose 2
  players…"
- "It is the fourth night and the Mutant was executed today. The Cannibal
  doesn't learn anything tonight, because a real Mutant would not wake."

### Timing consequences that follow from the above

- The Cannibal has **no night step of its own** and is not on either global
  night order list. It borrows the wake position of the character it gained.
  This is confirmed by the app's own data (`night_and_jinxes.json` has no
  `cannibal` entry) and by the reference dataset (`bra1n/townsquare`
  `roles.json`: `cannibal` has `firstNight: 0, otherNight: 0`).
- The poison from an evil executee lasts **until a good player dies by
  execution** — not until dawn, not until dusk. It survives nights and days.
- A good execution both (a) grants the new ability and (b) clears the poison.

### Jinxes (wiki, verbatim)

- **Butler**: "If the Cannibal gains the Butler ability, the Cannibal learns
  this."
- **Juggler**: "If the Juggler guesses on their first day and dies by
  execution, tonight the living Cannibal learns how many guesses the Juggler
  got correct."
- **Princess**: "If the Cannibal nominated, executed, & killed the Princess
  today, the Demon doesn't kill tonight."
- **Zealot**: "If the Cannibal gains the Zealot ability, the Cannibal learns
  this."

### Points the wiki does NOT settle (flagged, not guessed)

- Whether an executed player who was drunk/poisoned grants a working ability
  to the Cannibal (i.e. does the impairment travel with the ability?).
- Whether an executed **Traveller** counts (exile is explicitly not execution;
  a Traveller executed by ordinary nomination is not discussed).
- Whether a once-per-game ability already spent by the executee is refreshed
  for the Cannibal.
- Whether a later resurrection (Professor) of the executee revokes the gained
  ability.

## What the app does today

Data:

- `engine/src/main/resources/botc/data/characters.json:1305` — the only
  definition. `firstNightReminder` and `otherNightReminder` are both `""`;
  `reminders: ["Poisoned", "Lunch"]` (matches the official LUNCH/POISONED
  tokens; the older `townsquare` dataset's `"Died today"` is stale, so the
  app's data is the more current one here).
- `engine/src/main/resources/botc/data/night_and_jinxes.json:4,14,19` — the
  Butler, Juggler and Zealot jinxes. **The Princess jinx is missing.**
- `engine/src/main/resources/botc/data/night_guide.json` — **no `cannibal`
  entry at all** (`NightGuide.forStep("cannibal", …)` returns null).

Code: **zero**. `grep -rn cannibal engine/src app/src` returns only the three
data files above. There is no `InfoCalc` support (`InfoCalc.kt:29-36`), no
`StatusEffects` handling (`StatusEffects.kt:94-103` `deathNotes` has no
`cannibal` branch and no execution branch), no `GameActions` expiry entry
(`GameActions.kt:218-242`), and no night-order entry, so `NightOrder.build`
(`NightOrder.kt:40-181`) never emits a Cannibal row.

Storyteller experience today, end to end:

1. Setup: a Cannibal token sits in the circle. No prompt, no note.
2. Day 1: the Clockmaker is executed. `DayScreen.kt:111-114` (or
   `SeatSheet.kt:274`) records `DeathRecord(cause = EXECUTION,
   characterIdAtDeath = "clockmaker", abilityImpairedAtDeath = …)`. Nothing
   else happens: no LUNCH token, no note, no mention of the Cannibal.
3. Night 2: the night sheet (`NightScreen.kt:84-90`) contains no Cannibal row
   and no Clockmaker row (the Clockmaker is dead, so `inPlay["clockmaker"]`
   still exists — the dead Clockmaker's own row *does* still render with an
   "All holders are dead — usually skip" note, `NightScreen.kt:751-757` — but
   it is the dead Clockmaker's row, not the Cannibal's, and it points at the
   wrong seat).
4. The storyteller must remember, unaided: that a Cannibal is in play, who was
   executed, whether they were good, what that character's ability is, where
   in the night order it fires, and what number/token to show the Cannibal.
   They must then recompute the Clockmaker answer by hand even though
   `InfoCalc.clockmaker` (`InfoCalc.kt:218-241`) exists and could answer it.
5. If an evil player is executed, nothing marks the Cannibal poisoned; the
   storyteller must hand-place a generic token, and must then remember for the
   rest of the game to remove it the moment a good player is executed.

The only working piece: `PlacedReminder("cannibal", "Poisoned")` placed by
hand *is* picked up by `StatusEffects.isImpaired` (`StatusEffects.kt:36-46`,
label contains "poison"), so a hand-placed poison token does correctly
impair the Cannibal for any calculator that runs. Everything else is manual.

## Defects and gaps

1. **P0 · The Cannibal never wakes.** Rules: "will wake at night when this
   good character would normally wake." App: the night sheet is built purely
   from `Player.nightRoleId` against the static order lists
   (`NightOrder.kt:46-48`, `142-145`), so no Cannibal row can ever be
   generated. Repro: Carousel script, seat the Cannibal and a Clockmaker,
   execute the Clockmaker on day 1, open Night → night 2 has no Cannibal row.
   The storyteller silently skips the character's entire ability.

2. **P0 · The gained ability is never derived, even though the execution
   record already holds it.** `DeathRecord` (`GameState.kt:77-90`) stores
   `cause = EXECUTION`, `characterIdAtDeath` and `abilityImpairedAtDeath`.
   Nothing reads them for the Cannibal. Repro: execute anyone, open the
   grimoire — the Cannibal seat says nothing about what it now is.

3. **P0 · Evil executee never poisons the Cannibal.** Rules require the
   POISONED reminder immediately on an evil execution and removal on the next
   good execution. App: no code path places or removes it; a storyteller who
   forgets gives the Cannibal true information all game.
   File: `GameActions.kt:136-156` (`kill`) has no post-death hooks at all.

4. **P0 · Poison removal is unmodelled and cannot be expressed.** "Poisoned
   until a good player dies by execution" is a *conditional* duration. The
   only expiry machinery is the dawn/dusk tables
   (`GameActions.kt:218-242`), which cannot express it, so even a diligent
   storyteller has no supported way to make the app track it.

5. **P1 · LUNCH reminder is never placed on the executee.** `reminders`
   contains "Lunch" (`characters.json:1305`) so the token exists in the
   picker, but nothing places it, and after several executions the storyteller
   has no visual record of which death is "the recently killed executee".

6. **P1 · No dawn / day-start briefing.** There is no briefing surface at all
   (`GameShell.kt:130-160` `requestPhaseAdvance` only guards setup, dusk and
   unchecked night steps). The storyteller is never told "Cannibal now has the
   Clockmaker ability" or "Cannibal is poisoned until a good player is
   executed".

7. **P1 · `InfoCalc` cannot be reused for the borrowed ability.**
   `InfoCalc.compute(data, state, characterId, holderId, targets)`
   (`InfoCalc.kt:38-85`) already takes the character id and the holder
   separately, so `compute(data, state, "clockmaker", cannibalSeatId)` would
   produce the right answer today. Nothing calls it that way.

8. **P1 · Missing jinx: Cannibal + Princess.** Present on the wiki, absent
   from `night_and_jinxes.json` (only butler/juggler/zealot at lines 4, 14,
   19). This one has a real consequence (the Demon doesn't kill tonight) and
   is exactly the kind of thing a storyteller forgets.

9. **P1 · The Butler and Zealot jinxes are never surfaced when they fire.**
   They exist in data and render as static text in `SeatSheet.kt:222-236` and
   the "Jinxes in play" dialog, but the rule is an *event*: at the moment the
   Cannibal gains the Butler ability the storyteller must tell the Cannibal.
   Nothing prompts at that moment.

10. **P2 · The dead executee's own night row still shows and points at the
    wrong seat.** `NightOrder` keeps the row for a dead Clockmaker/Empath and
    labels it with the dead player's name; when a Cannibal is in play this is
    actively misleading — the row that should exist is "Cannibal (as
    Clockmaker)" on the Cannibal's seat.

11. **P2 · No `night_guide.json` entry.** Every other character in this scope
    has run-book prose; the Cannibal step (once it exists) has none, and the
    "pretend an ability" guidance for evil executees is nowhere in the app.

12. **P2 · Nothing surfaces the "even if dead" rule.** When the Cannibal dies
    while holding a Recluse/Ravenkeeper/Sweetheart ability they keep it.
    `StatusEffects.deathNotes` (`StatusEffects.kt:52-129`) has no cannibal
    branch, so the storyteller gets no warning at the death that matters.

13. **P2 · Executions that do not kill are indistinguishable from ones that
    do — for the *Cannibal's* purposes this happens to work** (no death ⇒ no
    `DeathRecord` ⇒ no gain), but a resurrected executee
    (`GameActions.resurrect`, `GameActions.kt:173-181`) leaves
    `resurrected = true` on the record and the intended semantics are
    undecided (see open questions). The spec below picks a behaviour; it needs
    a human ruling.

14. **P3 · No storyteller-facing hint about interviewing executees.** The
    wiki's core storytelling advice ("pay attention to which character each
    evil player is bluffing as") has no home in the app.

## Proposed behaviour (spec)

### A. Derived state (engine, pure)

Add to `StatusEffects` (or a new `Cannibal` object in the engine):

```
fun cannibalLunch(state: GameState, lookup: (String) -> Character?): LunchState?
```

`LunchState(executeeId: Long, characterId: String, executeeWasEvil: Boolean,
executeeImpairedAtDeath: Boolean)` derived as:

- Find the **last** `DeathRecord` with `cause == DeathCause.EXECUTION`
  (ignore `EXILE`; a Traveller exile is never an execution).
- Skip records whose `resurrected == true` **only** if the human ruling says
  resurrection revokes the gain; default = **do not skip** (they died by
  execution, the Cannibal ate them). Make this a single named constant so it
  can be flipped.
- `characterId = record.characterIdAtDeath` (snapshot — a later character
  change on that seat must not rewrite history).
- `executeeWasEvil` = alignment of that player **at the time of death**. The
  current `DeathRecord` does not store alignment; **add
  `evilAtDeath: Boolean? = null`** to `DeathRecord` and populate it in
  `GameActions.kill` from `player.isEvil(lookup)` (this matters for the Cult
  Leader, Recluse-misregistration is a storyteller call and stays manual).

Cannibal's effective night role:

```
fun cannibalNightRole(state, lookup): Pair<Player, String>?   // (cannibal seat, borrowed characterId)
```
returns null when there is no living Cannibal or no lunch.

### B. Automatic consequences at execution

Hook into the single place a death is recorded (`GameActions.kill` with
`cause == EXECUTION`) — or, preferably, a new
`GameActions.applyExecutionConsequences(state, lookup)` called by the
view-model right after `kill`, so it stays pure and undoable:

- Place `PlacedReminder("cannibal", "Lunch")` on the executee **exclusively**
  (`placeExclusiveReminder`) — it is a one-of-a-kind token that moves.
- If the executee was **good**: remove every
  `PlacedReminder("cannibal", "Poisoned")` from all seats.
- If the executee was **evil**: `placeExclusiveReminder(cannibalSeat,
  PlacedReminder("cannibal", "Poisoned"))`.
- Set the Cannibal seat's derived label (grimoire subtitle) to
  `"Cannibal — has the <Character> ability"` or
  `"Cannibal — poisoned (ate the evil <Character>)"`. Do **not** write it into
  `Player.note` (that is the storyteller's field); render it from derived
  state.
- Only run any of this when a living Cannibal is in play.

Expiry table: **none**. The Cannibal "Poisoned" token must NOT be added to
`EXPIRES_AT_DAWN` or `EXPIRES_AT_DUSK`; it is removed only by the good-execution
rule above. The "Lunch" token is moved, never expired.

### C. Night step (structured form)

- **when**: `other` and `first` — whichever night the borrowed character would
  wake. Wake condition: a living Cannibal exists **and** `cannibalLunch()` is
  non-null **and** the borrowed character has a non-empty
  `firstNightReminder`/`otherNightReminder` for tonight **and** (for
  once-per-game borrowed abilities) the Cannibal's own spent marker is absent.
  If the borrowed character would not wake tonight (Mutant, Chef on a later
  night, spent Slayer), emit **no row** — matching the wiki's Mutant example.
- **position**: insert the row at the borrowed character's index in the global
  order list. `NightOrder.build` already has the machinery for slotting extra
  rows (`NightOrder.kt:183-207` does it for homebrew); generalise it to a
  `virtualSteps` list computed before the loop, and emit the Cannibal row when
  the loop reaches `borrowedId`.
- **row id**: `"cannibal:<borrowedId>"` so `nightStepsDone` stays stable
  within a night and resets when the borrowed ability changes.
- **title**: `"Cannibal"`; **subtitle**: `"as the <Character> — <that
  character's night reminder text>"`.
- **playerIds**: the Cannibal's seat (never the dead executee's).
- **targets**: exactly `InfoCalc.targetsNeeded(borrowedId)`, with the same
  picker the borrowed character would get.
- **immediate effects**: whatever the borrowed ability does. Reminder tokens
  placed by the borrowed ability should carry `sourceId = borrowedId` so the
  existing expiry tables keep working unchanged (e.g. a borrowed Monk's
  `("monk","Safe")` still expires at dawn).
- **information**: call
  `InfoCalc.compute(data, state, borrowedId, cannibalSeatId, targets)`
  verbatim. Caveats must be computed for the **Cannibal** as holder, so the
  Cannibal's own poison/drunkenness applies.
- **impaired / evil-executee case**: when the lunch was evil, the row must
  still be offered (the wiki says "you *may* wake them … and pretend") but
  headed
  `"FAKE — the Cannibal ate the evil <Character>. They are poisoned; invent an
  answer."`, with the false-info affordances already in
  `NightScreen.kt:903-930` shown unconditionally, plus a chip
  `"Use <evil player>'s bluff instead"` listing the executed evil player's
  claimed character if the app records day claims (see the Gossip/claims
  mechanics audit).
- **visibility**: nothing is shown to the Demon or Minions about the Cannibal.
  The Cannibal is never told which ability they gained — the UI must never
  offer a "show them the <Character> token" card for this step.

### D. Day-time inputs the app must record

- Nothing mandatory. Optional but valuable: a per-execution field "what this
  player claimed to be" (feeds the evil-executee bluff suggestion above). This
  belongs in the shared day-claims store proposed by the Gossip/claims audit,
  not in a Cannibal-specific place.

### E. Briefings

- **At dawn / day start**, if a Cannibal is alive and a lunch exists:
  `"Cannibal: has the <Character> ability"` or
  `"Cannibal: poisoned (ate the evil <Character>) until a good player is
  executed."`
- **At execution time**, immediately after recording the death:
  `"<Name> was executed — the Cannibal now has the <Character> ability"` /
  `"<Name> was evil — the Cannibal is poisoned until a good player is
  executed."`
- **Jinx prompts** at the same moment:
  - Butler in play and lunch == butler → `"Tell the Cannibal they have the
    Butler ability, and ask them to choose a Master."`
  - Zealot in play and lunch == zealot → `"Tell the Cannibal they have the
    Zealot ability."`
  - Juggler in play, lunch == juggler, and today was the Juggler's first day →
    `"Tonight the living Cannibal learns how many of the Juggler's guesses
    were correct."`
  - Princess in play: at execution, if the Cannibal nominated the Princess and
    the Princess died today → `"Princess jinx: the Demon does not kill
    tonight."` (`state.nominations` already stores `nominatorId`, so this is
    derivable — `GameState.kt:63-72`.)
- **On the Cannibal's own death**, add to
  `StatusEffects.deathNotes`: `"Cannibal: they keep an 'even if dead' borrowed
  ability (Recluse / Ravenkeeper / Sweetheart …) and lose the Cannibal
  ability."`

### F. UI text for the step (storyteller voice, imperative)

- Title row: `Cannibal — as the Clockmaker`
- Body: `Ate Maya (executed day 2). Run the Clockmaker's wake for Ben. Do not
  tell them which ability they have.`
- Evil lunch: `Ate Jonas (evil, executed day 3). The Cannibal is POISONED —
  wake them and give a convincing false answer, then put them to sleep.`
- Grimoire seat subtitle: `Cannibal · Clockmaker ability` /
  `Cannibal · poisoned (ate the Widow)`.

### G. Data changes

- `night_and_jinxes.json`: add
  `{"id1": "cannibal", "id2": "princess", "reason": "If the Cannibal
  nominated, executed, & killed the Princess today, the Demon doesn't kill
  tonight."}`
- `night_guide.json`: add a `cannibal` entry with `first` and `other`
  instructions covering: run the executee's wake at their position; never say
  which ability; evil lunch ⇒ poisoned, fake a wake, prefer their bluff;
  LUNCH/POISONED token handling; the Butler/Zealot jinxes.
- `characters.json`: no change (text and reminders are already current).
- Night order lists: **no change** — the Cannibal is correctly absent; the row
  is virtual.

## Tests to add

1. `cannibal gains the last good executee's ability`
   Given a Cannibal and a Clockmaker seated; When the Clockmaker is executed
   and dies on day 1; Then `cannibalLunch(state)` returns
   `(clockmaker seat, "clockmaker", evil = false)` and the night-2 sheet
   contains a step with id `"cannibal:clockmaker"` positioned immediately
   after the Chef's slot in the other-night order, with `playerIds` = the
   Cannibal's seat only.

2. `evil executee poisons the cannibal and grants nothing`
   Given a Cannibal and a Poisoner; When the Poisoner is executed; Then the
   Cannibal seat has `PlacedReminder("cannibal","Poisoned")`,
   `StatusEffects.isImpaired(state, lookup, cannibal)` is true, and the night
   step for `"cannibal:poisoner"` is flagged fake.

3. `a later good execution clears the cannibal poison and replaces the ability`
   Given the state from test 2; When an Empath is executed on day 3; Then the
   Cannibal has no `("cannibal","Poisoned")` reminder, `isImpaired` is false,
   and the night sheet contains `"cannibal:empath"` and not
   `"cannibal:poisoner"`.

4. `cannibal poison survives dawn and dusk`
   Given the state from test 2; When `advancePhase` is called four times
   (dawn, dusk, dawn, dusk); Then the `("cannibal","Poisoned")` reminder is
   still on the Cannibal seat. (Fails today only once the token is placed
   automatically; guards against anyone adding it to the expiry tables.)

5. `exile does not feed the cannibal`
   Given a Cannibal and a Traveller; When the Traveller dies with
   `DeathCause.EXILE`; Then `cannibalLunch(state)` is null.

6. `executing an already dead player grants nothing`
   Given a Cannibal and a dead Chef; When `kill(chef, EXECUTION)` is called;
   Then `kill` returns the state unchanged (it already early-returns on
   `!player.alive`, `GameActions.kt:143`) and `cannibalLunch` is null.

7. `an execution that does not kill grants nothing`
   Given a Cannibal and a Sailor on the block; When the storyteller records
   "death prevented" (no `kill` call); Then `cannibalLunch` is null and no
   `("cannibal","Lunch")` token exists.

8. `lunch token moves rather than accumulating`
   Given two executions on consecutive days; Then exactly one
   `("cannibal","Lunch")` reminder exists in the grimoire, on the most recent
   executee.

9. `cannibal borrows info calculation with itself as holder`
   Given a Cannibal seated between two evil players and an executed Empath;
   Then `InfoCalc.compute(data, state, "empath", cannibalSeatId)` headline
   counts the **Cannibal's** neighbours, not the dead Empath's.

10. `borrowed character that would not wake produces no row`
    Given an executed Mutant; Then the night-2 sheet contains no step whose id
    starts with `"cannibal:"`.

11. `character change on the executee's seat does not rewrite the lunch`
    Given an executed Clockmaker; When that seat is later reassigned (e.g. by
    a Farmer transfer or storyteller correction); Then `cannibalLunch` still
    reports `"clockmaker"` (reads `characterIdAtDeath`).

12. `princess jinx fires only when the cannibal nominated`
    Given a Cannibal, a Princess, a recorded nomination Cannibal→Princess on
    day 2, and the Princess executed and dead that day; Then the day-end
    briefing contains "the Demon doesn't kill tonight"; and given the same
    state but the nomination made by another player, Then it does not.
