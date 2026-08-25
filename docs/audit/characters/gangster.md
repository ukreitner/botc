# Gangster (gangster) — Experimental Traveller

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Gangster> (revealed 02/05/2021)

Current ability text (wiki):

> "Once per day, you may choose to kill an alive neighbor, if your other alive neighbor
> agrees."

`characters.json:2110` has the same text with British "neighbour" — no meaningful drift.

Summary clarifications (quoted):

> - "The Gangster may kill one of their two living neighbors. Their dead neighbors are
>   skipped over, and do not count."
> - "To use their ability, the Gangster and one of their living neighbors must agree to kill
>   the other living neighbor. The Storyteller must hear and confirm this agreement. The
>   Gangster cannot kill without the Storyteller present."
> - "Each day, the Gangster may say whatever they want, and offer any encouraging words they
>   want to either player. Once an agreement has been reached, then the Gangster may not use
>   their ability again today, even if that player didn't die due to an ability protecting
>   them."
> - "The Gangster's two living neighbors are always one clockwise, and one counter-clockwise."
> - "If both living neighbors want to kill the other, the Gangster decides who dies."

How to Run (quoted in full):

> "Once per day, the Gangster can declare that they wish to use their ability. If so, ask if
> an alive neighbor agrees. If an alive neighbor agrees, the other alive neighbor **dies**.
> If both alive neighbors agree, the Gangster chooses which alive neighbor **dies**. If
> neither alive neighbor agrees, the Gangster may not use their ability today."

Examples (quoted in full):

> "The Gangster neighbors the Saint and the Baron. The Gangster asks the Baron if they want
> to kill the Saint. The Baron agrees and the Saint dies.
>
> The Gangster neighbours the Chambermaid and the Poppy Grower, but they are both dead. The
> Gangster's two living neighbours are the Engineer and the Po. The Gangster talks with the
> Po and offers to kill the Engineer. The Po declines. The Gangster talks with the Engineer
> and the Engineer asks the Gangster to kill the Po. The Gangster agrees, and the Po dies.
> Good wins.
>
> The Gangster neighbours the Fool and the Sage. The Sage and the Gangster kill the Fool but
> the Fool doesn't die, because of the Fool's ability. The Gangster may not use their ability
> again today."

Consequences that matter for the app:

- **Neighbours are the nearest ALIVE players clockwise and counter-clockwise**, skipping over
  dead seats. They change as people die, including mid-day.
- **The kill needs a three-way structure**: Gangster declares → one alive neighbour agrees →
  the *other* alive neighbour dies.
- **If both agree, the Gangster picks.** If neither agrees, nothing happens and the ability
  is **not** spent (the wiki's second example has the Gangster ask one, be refused, then ask
  the other successfully on the same day).
- **The ability is spent by the agreement, not by the death.** The Fool example is explicit:
  the target survives, but the Gangster cannot use the ability again that day.
- **The Storyteller must witness the agreement** — this is an at-the-table confirmation the
  app should capture as an explicit ST input, not a free-text note.
- **A Gangster death is not an execution.** The day continues afterwards (evil Tips: "A death
  by the Gangster's ability doesn't count as an execution, and the day will continue
  afterwards"), the Butcher is not enabled by it, and the Vortox's "no execution today" check
  is unaffected.
- **A dead Gangster has no ability** (standard). It can be restored by the Bone Collector,
  in which case it is usable on the following day (until dusk).
- The tips confirm a Gangster with only **one** living neighbour situation is degenerate but
  do not rule it out; with fewer than 3 alive players the "two living neighbours" may
  coincide.
- No night action, no reminder tokens. No jinxes on the page.

## What the app does today

Data:
- `characters.json:2105-2116` — correct ability text; empty first/other night reminders;
  `reminders: []` (matches the printed character, which has no tokens).
- `night_and_jinxes.json` — correctly absent from both night orders.
- `night_guide.json` — no entry (there is no day-guide mechanism, `NightGuide.kt:56-59`).

Code: **no Gangster-specific code anywhere.** `grep -rn gangster engine/src app/src`
returns only `characters.json` and `raw_sv_travellers_fabled.json`.

Storyteller's actual experience: nothing. To resolve a Gangster kill the ST must
1. work out the current alive neighbours by eye from the circle (`GrimoireScreen.kt`),
2. remember whether the ability was used today,
3. open the victim's seat and press **"Other death"** → `DeathCause.STORYTELLER`
   (`SeatSheet.kt:277-279`) — the only cause that is not wrong, and one that reads as
   "died (storyteller)" in the log (`GameExtras.kt:58`, `:328`),
4. remember that the ability is spent even if the victim survived a protection,
5. remember to clear that memory at dawn.

There *is* one useful piece of infrastructure: `SeatSheet.kt:237-249` runs
`StatusEffects.deathNotes` before the kill and `SeatSheet.kt:325-345` shows a "might be
protected / They die anyway / Death prevented" dialog — so the Fool case is at least
surfaced *if* the ST kills from the seat sheet.

Works: nothing Gangster-specific. Alive-neighbour computation does exist in the engine
(`InfoCalc.aliveNeighbours`, `InfoCalc.kt:168-181`) but is private and unused for this.

Shared traveller-lifecycle defects **T1–T7** apply — see `barista.md`.

## Defects and gaps

1. **P0 · The ability has no representation at all.** No neighbour computation, no
   agreement capture, no kill action, no once-per-day gate. Every part of the rule is the
   storyteller's memory. This is the plainest instance of the user's complaint: the ST must
   do all the bookkeeping the app could do.

2. **P0 · Nothing tracks "used today", and the spend rule is counter-intuitive.** The
   ability is spent by the *agreement*, even when the victim survives (the Fool example). A
   ST who kills from the seat sheet and then hits "Death prevented" (`SeatSheet.kt:340-343`)
   has no record that the Gangster is now spent for the day.

3. **P0 · The alive-neighbour rule is not enforced or displayed.**
   `StatusEffects.derivedPoison` (`StatusEffects.kt:14-33`) already walks the circle
   skipping seats, and `InfoCalc.aliveNeighbours` (`InfoCalc.kt:168-181`) already computes
   exactly "nearest alive neighbour each way", but neither is exposed for the Gangster.
   Nothing stops the ST killing a non-neighbour, and nothing recomputes the neighbours after
   a death.

4. **P1 · There is no correct `DeathCause` for a Gangster kill.**
   `DeathCause` (`GameState.kt:75`) is `EXECUTION, DEMON, OTHER_NIGHT_DEATH, EXILE,
   STORYTELLER`. A daytime, non-execution, ability-caused death has to be filed under
   `STORYTELLER`, which loses the fact that it was the Gangster and reads badly in the log
   (`GameExtras.kt:58`). It also risks being confused with an execution by any future
   "was there an execution today" logic (see `butcher.md` defect 4).

5. **P1 · `DeathRecord.atNight` will be correct but the log has no day-death vocabulary.**
   `GameActions.kill` sets `atNight = state.phase == Phase.NIGHT` (`GameActions.kt:150`),
   so a day kill is recorded as a day event — good — but `GameExtras.kt:53-58` has no label
   better than "died (storyteller)".

6. **P1 · No day-start briefing.** Nothing tells the ST "Gangster is in play — their living
   neighbours today are X and Y; ability unused." As people die during the day the
   neighbours change and the app should keep that line current.

7. **P1 · The protection check is only reachable from the seat sheet.** A Gangster kill
   launched from a day-tools surface must run `StatusEffects.deathNotes`
   (`StatusEffects.kt:52-129`) and the confirm dialog, exactly as `SeatSheet.kt:325-345`
   does, and — critically — must mark the ability spent on *either* branch.

8. **P2 · Demon-only protections must not be offered as blocking a Gangster kill.**
   `deathNotes` currently emits "Soldier is safe from the Demon" (`StatusEffects.kt:74`) and
   "Marked 'Safe' (Monk) — protected from the Demon" (`:66`) without a cause filter, so a
   Gangster kill against a Soldier would raise a spurious protection dialog. `deathNotes`
   needs a `cause` parameter (the same change `deviant.md` asks for).

9. **P2 · Interaction with the day's execution flow is unstated.** A Gangster kill is not an
   execution: it must not touch `aboutToDie` (`GameActions.kt:296-306`), must not enable the
   Butcher, and must not satisfy a Vortox/Mayor "an execution happened" test. It does,
   however, change `alivePlayers.size` and therefore the execution threshold shown at
   `DayScreen.kt:71-72` — which the app already recomputes live, so that part is fine.

10. **P2 · No guidance text.** The Gangster's How-to-Run, the "both agree ⇒ Gangster
    chooses" rule and the "spent even if they survive" rule have nowhere to live
    (`night_guide.json` is night-only).

11. **P3 · Spelling drift.** `characters.json:2110` uses "neighbour"; the current wiki text
    uses "neighbor". Harmless, but the dataset should match the source it claims to mirror.

## Proposed behaviour (spec)

The Gangster has no night step. Structured form:

- **when**: day phase; once per day; the Gangster must be **alive** (or hold
  `("bonecollector","Has Ability")`).
- **targets**: derived, not free. The engine computes
  `aliveNeighbours(gangsterSeat)` = nearest alive player clockwise and counter-clockwise,
  skipping dead seats, recomputed on every state change. The ST then records:
  1. which neighbour **agreed** (or "both", or "neither"), and
  2. if both agreed, which neighbour the **Gangster chose** to kill.
  The victim is always "the other alive neighbour".
  Edge cases: fewer than 3 alive players, or both directions resolving to the same player →
  show "only one living neighbour — the ability cannot be used as written" and let the ST
  decide.
- **immediate effects**:
  - `neither agrees` → nothing, ability **not** spent.
  - `one agrees` → the other alive neighbour dies; ability spent.
  - `both agree` → the Gangster picks one; that one dies; ability spent.
  - The kill runs `StatusEffects.deathNotes(state, lookup, victimId, cause = GANGSTER)` and
    the standard "might be protected" confirm dialog. **Both** branches ("They die anyway"
    and "Death prevented") set the spent flag.
  - Death is recorded as a new `DeathCause.DAY_ABILITY` (or `DeathCause.GANGSTER`) with
    `atNight = false`.
- **deferred effects**: none at night. The day continues normally after the death.
- **expiry**: the spent flag is day-scoped, cleared on DAY→NIGHT in `advancePhase`
  (`GameActions.kt:261-262`).
- **information**: none. The Gangster learns nothing.
- **visibility**: the whole thing is public — the neighbours know, and the ST hears the
  agreement. Nothing is shown to the Demon/Minions/Lunatic.
- **day-time inputs the app must let the ST record**: the agreement itself (which neighbour
  agreed), and the Gangster's choice when both agree. Optionally a note of who the Gangster
  *asked*, since the wiki's second example shows an ask can be refused without spending the
  ability.
- **interactions/edge cases to handle explicitly**:
  - **Not an execution**: no `ExecutionRecord` (see `butcher.md`), no Butcher trigger, no
    effect on `aboutToDie`, no Vortox/Mayor "execution happened" satisfaction.
  - **Protections**: Sailor ("can't die"), Tea Lady, Innkeeper "Protected", Fool (first
    death), Lleech, Zombuul (first death registers dead), Devil's Advocate (execution-only —
    does **not** apply). Monk "Safe" and Soldier are **Demon-only** and do not apply.
  - **On-death triggers** fire normally: Ravenkeeper does **not** (it is
    "if you die at night"), but Sage does not either (Demon-only); Farmer, Moonchild,
    Sweetheart, Barber, Poppy Grower, Godfather (an Outsider died today), Minstrel
    (execution-only — no), Scarlet Woman (if the Demon dies with 5+ alive), Imp star-pass
    (self-kill only — no) all need to be surfaced via `deathNotes`.
  - **Saint / Good Twin**: killing them by Gangster does **not** trigger their
    execution-only clauses. The evil Tips call this out.
  - **Barista ACTS TWICE on the Gangster**: two uses today.
  - **Bone Collector** on a dead Gangster: the ability is live "until dusk", i.e. usable on
    the following day.
  - **Gangster drunk/poisoned**: surface `isImpaired` in the prompt so the ST can decline to
    honour the kill knowingly.
  - **Matron** (traveller) seat swaps and any other seat reordering change the neighbours —
    recompute, never cache.

### Implementation shape

1. `DeathCause` (`GameState.kt:75`) gains `DAY_ABILITY` (label "died (day ability)") — or,
   better, `DeathRecord` gains `sourceCharacterId: String? = null` so the log can say
   "killed by the Gangster". Update the two `when` blocks at `GameExtras.kt:53-58` and
   `:324-328`.
2. `StatusEffects` gains a public
   `fun aliveNeighbours(state, playerId): Pair<Player?, Player?>` (lift the private
   `InfoCalc.aliveNeighbours`, `InfoCalc.kt:168-181`, and make it directional so the two
   sides can be labelled "clockwise"/"counter-clockwise").
3. `StatusEffects.deathNotes` gains `cause: DeathCause?` and filters Demon-only protections
   out for day-ability kills.
4. Day-scoped flags (shared with the Deviant and Butcher): `"gangsterUsed:<playerId>"`.
5. A **Day tools** panel (new, mirroring `QuickResolutions` on the night sheet) hosting the
   Gangster resolver, the Butcher prompt, the Deviant judgement and the Gnome trigger.

### UI text

- Day briefing line, kept live:
  `Gangster (<Name>) — living neighbours: <A> (clockwise), <B> (counter-clockwise).
  Ability unused today.`
- Resolver: `Once per day. Who agreed?` → chips `<A> agrees` · `<B> agrees` · `Both agree` ·
  `Neither agrees`
  - on `<A> agrees` → `Then <B> dies.` + `Confirm kill` button.
  - on `Both agree` → `The Gangster chooses who dies:` → `<A>` · `<B>`.
  - on `Neither agrees` → `No kill. The ability is NOT spent — the Gangster may try again
    today.`
- After a spend: `Gangster ability used today — even if <victim> survived.`
- Warning line: `A Gangster kill is not an execution: the day continues, and it does not let
  the Butcher nominate again.`

### Data changes

- `characters.json:2110`: align spelling with the wiki ("neighbor") or leave and note the
  variance; no functional change.
- Add a `day_guide.json` entry (see `butcher.md`):
  > "Once per day the Gangster declares they want to use their ability. Ask an alive
  > neighbour if they agree — you must hear it. If one agrees, the other alive neighbour
  > dies. If both agree, the Gangster chooses. If neither agrees, nothing happens and the
  > ability is not used up. Once an agreement is reached the ability is spent for the day,
  > even if the victim survives. Dead neighbours are skipped."

## Tests to add

1. `Given` seats [G, A(dead), B, C] in clock order with G the Gangster
   `When` `aliveNeighbours(state, gangsterId)` `Then` the result is `(C, B)` — the dead A is
   skipped. *(Fails today: no such public function.)*

2. `Given` a Gangster resolution where neighbour A agrees
   `When` applied `Then` neighbour B is dead with a day-time death record, `atNight = false`,
   and the day's `gangsterUsed` flag is set.

3. `Given` a Gangster resolution where the victim is a Fool who does not die
   `When` the ST chooses "Death prevented"
   `Then` the victim is alive **and** the `gangsterUsed` flag is still set.
   *(This is the wiki's third example and the least intuitive rule.)*

4. `Given` a Gangster resolution where neither neighbour agrees
   `When` applied `Then` no death and the `gangsterUsed` flag is **not** set.

5. `Given` both neighbours agree `When` the Gangster picks A `Then` A dies and B lives.

6. `Given` `gangsterUsed` is set `When` `advancePhase` DAY→NIGHT→DAY
   `Then` the flag is cleared.

7. `Given` a Gangster kill of a Soldier
   `When` `deathNotes(..., cause = DAY_ABILITY)` `Then` the "safe from the Demon" note is
   **not** produced; `Given` the victim is a Sailor `Then` "The Sailor can't die." **is**
   produced.

8. `Given` a Gangster kill `When` the day engine is queried
   `Then` `anExecutionOccurredToday(state) == false` and the Butcher is not enabled.

9. `Given` a dead Gangster with no restored ability `Then` the resolver is not offered;
   `Given` the Gangster holds `("bonecollector","Has Ability")` `Then` it is.
