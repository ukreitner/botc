# travellers-a — requirement cards

10 characters: apprentice · bishop · judge · matron · voudon (BMR) · beggar · bureaucrat ·
gunslinger · scapegoat · thief (TB). Totals: **P0 31 · P1 36**.

## Group notes

1. **`Player.alignment` is the keystone** — 7 of 10 are undefined without it; `Team.TRAVELLER.isEvil == false` (`Character.kt:16`) silently makes every traveller good. Adopt `SetupRequirement("traveller.alignment:<seat>", ALIGNMENT)` (`setup-and-identity.md`), blocking **on arrival**, not only at setup.
2. **5-step arrival flow** (rulebook): name+seat → character → **alignment (required)** → GOOD/EVIL card + `THIS IS THE DEMON` if evil (Demon only, no Minions, **no bluffs**) → "Inform group" `LedgerEntry(ANNOUNCE)` with character+ability but **never** alignment, plus a <7-alive caution. Today `GameShell.kt:663-682` asks for a name only.
3. **conflict — `VoteRules` snapshot vs live `DayRules`.** voudon/bureaucrat/thief/beggar all propose `VoteRules(isExile, eligibleVoterIds, threshold, spendsGhostVotes, weightOf, reasons)` **stored per nomination**; `day-engine.md §B` keeps it live with only `Nomination.threshold` + `tallyNotes`. The live form loses the mid-day regime change the Voudon needs. Recommend: `DayRules` produces it, the **full** output persists on the `Nomination`.
4. **Exile: unweighted, unrestricted — 3 live bugs.** Threshold = half of **all seats**; any player alive or dead may call and support; dead supporters keep their token; *"not affected by abilities"*. (a) `DayScreen.kt:137` requires `p.alive` for the caller; (b) keep `DayScreen.kt:184`'s `|| isExile`; (c) `voteWeights(isExile = true)` must return **all 1** or the Bureaucrat/Thief fix breaks exiles.
5. **`removeTokensOfSource(state, sourceId)`** — Bureaucrat `3 votes`, Thief `Negative vote`, Bishop `Nominate good/evil` end *immediately* on their traveller's death/exile/impairment. Express as `TokenRule(endsWhenSourceLosesAbility = true)` + `reconcileTokens` (`night-engine.md §4`), never per character.
6. **Traveller tokens unreachable in `ReminderPicker`** — built-in scripts are `.filter { it.team.isTownResident }` (`GameData.kt:39`), so `SeatSheet.kt:489-560` never offers any of the 5 traveller reminders here. Only the night tray reaches them, and only for the 3 with a night step. Same root cause as `fabled-B`.
7. **No day-guide surface.** 7 of 10 have **no** `night_guide.json` entry and 6 never act at night, so the app holds zero how-to-run text for them. Needs a `day` key or a `day_guide.json` — `travellers-b` reached the same conclusion.
8. **`GameActions.execute(...)` (day-engine §C) is the single fix for 4 cards** — Judge (`via = JUDGE`), Scapegoat (`diedInsteadId` + `preventedBy`), Gunslinger (must *not* use it), Voudon (threshold snapshot). Today 4 independent `kill(..., EXECUTION)` sites. conflict: judge/scapegoat's derived `executionLocked` → stored `nominationsClosedOnDay` + `DayRules.executionSpent`.
9. **`status-model.md` cause-table errors:** `judge` is listed `GOOD_ABILITY` — wrong, a forced pass is an **`EXECUTION`**; `gunslinger` is **absent** and needs a row that is neither `DEMON_ABILITY` nor `EXECUTION`. Its §6 `leftGame = true` exile path must not catch an **executed Scapegoat**.
10. **Plan must decide:** (a) Voudon tie ruling `equalKeepsBlock` — *unconfirmed by the wiki*; (b) Beggar `Player.voteTokens: Int` vs day-engine's `toggleGhostVote(donor)` + one reminder; (c) Apprentice granted ability as `AbilityGrant(REPLACE)` (recommended) vs a new `grantedAbilityId`.

---

## apprentice — Apprentice · bmr traveller · P0:4 P1:4
today: night step exists **only** on the first-night sheet, so an Apprentice who joins on day 1+
never wakes at all; following the guide's "replace the token" through `SeatSheet.kt:91-93` assigns
`isTraveller = false` and the seat stops being a traveller everywhere.
data:
  - characters.json: `ability` → wiki text `"On your 1st night, you gain a Townsfolk ability (if
    good) or a Minion ability (if evil)."` (drop the comma). `reminders: ["Is the Apprentice"]` ok.
    `firstNightReminder` must stop saying "replace the Apprentice token" → "leave the Apprentice
    token; place Is the Apprentice beside the granted ability".
  - night_and_jinxes.json: `firstNight[3]` (after `kazali`, before `barista`) is correct and stays.
    **Not** added to `otherNight` — the planner slots the personal first night instead (see gate).
  - night_guide.json: add `apprentice.other` (same body as `first`); amend `first` to say the
    character token is **not** replaced. Keep `shows[0]` = `{"Show the Apprentice", token, "YOU ARE",
    token:"pick"}`.
setup: `traveller.alignment:<seat>` · ALIGNMENT · "Set <name>'s alignment" · GOOD/EVIL · blocking
  (this decides Townsfolk-vs-Minion pool, so the night step is uncomputable without it)
identity: shown = granted token · acting = granted ability · **registered = `apprentice` forever**
  (*"other character abilities detect them as the Apprentice"* — a Gambler guessing "Tea Lady" on a
  Tea-Lady-Apprentice **dies**). Express as `AbilityGrant(<granted>, "apprentice", REPLACE, cycle)`
  on `Player.grants`; `Identity.actingRoles` returns the granted role, `characterId` untouched.
  conflict: `apprentice.md`'s bare `Player.grantedAbilityId` is superseded by `AbilityGrant`.
  `isTraveller` must never be cleared by `assignCharacter` (`GameActions.kt:46-53`).
night.first / night.other:
  gate: `Gates.aliveHolder` AND the seat's **own** first night — `joinedOnCycle` reached and no
    `apprentice` grant yet. Emitted as `StepKey("apprentice", seatId, "first")` with
    `WakeStyle.FIRST_NIGHT` on **any** night, at first-night index 3 (before `MINION_INFO`), via a
    `PendingEffect(kind = "first-night", targetId = seatId, dueNight = joinCycle+…)`; if index 3 has
    already passed tonight, the insert-after-cursor rule re-stamps it and badges *"out of order"*.
    Never re-emitted after the grant.
  action: `ChooseCharacter(sourceId = "apprentice", pool = TOWNSFOLK-on-script if alignment GOOD /
    MINION-on-script if EVIL, allowNone = false)`, filtered to **not in play** (*"only one of each
    token exists"*), in-play entries greyed with that reason. Warn when the pool is empty or
    setup-only (*"this script gives an evil Apprentice only the Baron — the ability is wasted"*).
  effects: `PlaceToken("apprentice","Is the Apprentice", SOURCE, exclusive)` · `AbilityGrant(REPLACE)`
    · `ShowCardTo(SOURCE,"YOU ARE")` + granted token · **no** `BecomeCharacter`, **no** alignment or
    `isTraveller` change.
  deferred: the granted character's own step is inserted **tonight**, with
    `style = FIRST_NIGHT` — *"first-night-only abilities function on the Apprentice's first night
    instead"*, so a night-4 Apprentice granted Washerwoman gets Washerwoman **first-night** info.
    From the next night on it fires at the granted character's **other-night** slot with
    `WakeStyle.OTHER_NIGHT`; no other-night step ⇒ no step. conflict: `night-engine.md §1`'s
    re-run table has no Apprentice row — add one; it is the only entry whose style *changes* after
    the first firing.
  info: whatever the granted ability's `InfoCalc` computes, holder = the Apprentice seat.
    Impairment applies normally (travellers lose abilities when drunk/poisoned). Misregistration:
    every read of this seat (Undertaker, Ravenkeeper, Gambler, Empath alignment, Fang Gu/No Dashii
    team checks) answers **Apprentice**, never the granted character.
  show: `YOU ARE` token card, then the granted character token. No new card kinds.
  visibility: an **evil** Apprentice present at night 1 is part of `MINION_INFO`; one arriving later
    is not — the ST simply shows them the Demon at arrival. They receive **no bluffs**.
day: arrival announcement only (`LedgerEntry(kind = ANNOUNCE)`: "<Name> joined as the Apprentice"
  + ability text, alignment withheld).
death: none of their own. `Is the Apprentice` never expires; the grant is *"retained until death"* —
  on death the seat simply stops waking. Excluded from the evil-wins-at-2 count (`isTraveller`).
ledger: the granted ability id + grant night (`kind = SPENT`/`RULING`); the arrival ANNOUNCE.
tests:
  - Given a BMR game on night 2 with an ungranted Apprentice seat, when the plan is built, then it
    contains `StepKey("apprentice", seat, "first")` positioned before `barista`.
  - Given a granted Apprentice, when the other-night plan is built, then there is no `apprentice`
    step and there **is** a step for the granted ability whose `playerIds` holds the Apprentice seat.
  - Given an Apprentice granted `washerwoman` on night 3, then `InfoCalc` returns a Washerwoman
    **first-night** result that night.
  - Given a grant is applied, then `isTraveller` is still true, `characterId` is still `apprentice`,
    and `WinCheck` with 2 residents + the Apprentice alive does not fire the evil win.
  - Given an evil Apprentice, then the picker offers only not-in-play Minions on the script.
open: once-per-game granted abilities get their own `AbilityGrant.spent` — assume yes. Does an evil
  Apprentice count toward the 7-player Minion-info threshold? (`NightOrder.kt:52` counts residents
  only; the rulebook says "players".)

---

## bishop — Bishop · bmr traveller · P0:4 P1:3
today: zero references in code. Every alive player is an enabled Nominator chip; recording a
"Storyteller" nomination burns a real player's one-per-day and fires nominator-side triggers; the
dusk guard never checks the obligation; neither reminder token is placeable.
data:
  - characters.json: ok (`reminders: ["Nominate good","Nominate evil"]`, ability text correct).
  - night_and_jinxes.json: ok — absent from both orders, no jinxes.
  - night_guide.json: **no entry**; add a `bishop.day` entry with the wiki How-to-Run (dawn token,
    ST-only nominations, ≥1 opposing nominee, 3–5 nominations typical, all living players on the
    final day).
setup: `traveller.alignment:<seat>` · ALIGNMENT · blocking — decides which token is placed.
identity: plain (registers as Bishop; alignment explicit).
night.first / night.other: none — correctly absent from both sheets.
day:
  briefing (DAY_START, `Severity.ACTION`): *"Bishop — you nominate today. Players may not nominate
    (they may still call exiles). You must nominate at least one **evil** player before dusk.
    Nominated so far: Alice (good), Bob (good) — still needed: 1 evil."* Final-day line: *"nominate
    every living player."* On loss of ability: *"Bishop has lost their ability — players nominate
    normally again."*
  interceptors: `DayRules.canNominate(state, lookup, playerId)` returns
    `Right(false, "Bishop: only the Storyteller can nominate")` for **every** seat while a living,
    unimpaired Bishop exists. `checkNomination` emits `NominationTrigger(kind = WARN, sourceId =
    "bishop")` per `day-engine.md §D`. The nominator picker becomes a single pinned **Storyteller**
    chip; `Nomination.nominatorId` becomes **nullable**, `null` = the Storyteller.
    - `hasNominatedToday` must ignore `nominatorId == null`.
    - nominator-side triggers must be **skipped** when `nominatorId == null`: Witch curse, Golem,
      Cerenovus/Harpy madness check, and **Virgin** — a Storyteller is not a Townsfolk, so show
      *"Virgin: the Storyteller is not a player, so nothing happens"* instead of the execute warning.
    - nominee-side triggers fire normally.
  day tools: a per-day "ST nominated:" list, grouped by day with each nominee's true alignment
    (storyteller-only) — this is the Bishop player's entire deduction material.
  dawn placement: at every DAWN, while the Bishop is alive and unimpaired, auto-place exclusive
    `bishop:"Nominate evil"` (good Bishop) / `bishop:"Nominate good"` (evil Bishop) on the Bishop's
    own seat. This is the **first** dawn *placement* in the app — `advancePhase` only sweeps today.
    Remove the token the moment a nomination is recorded whose nominee's **registered** alignment
    opposes the Bishop's. `TokenRule(expiry = DUSK, endsWhenSourceLosesAbility = true)`.
  dusk: `WinCheck.duskCheck` gains a **blocking** `Advisory(ruleId = "bishop-obligation")` while the
    token stands: *"Bishop: nominations can't end until you nominate an evil player."* Buttons
    `Back to nominations` / `Override (rule waived)` — the override is logged.
  vote/nomination rule changes: **voting is entirely unchanged.** Exiles are entirely unchanged —
    any player, alive or dead, may call and support an exile of the Bishop or any traveller. This is
    the single most likely regression when implementing the nomination block.
  LedgerKind: `ANNOUNCE` for the daily obligation, `RULING` for an override.
death: none of their own. The ability ends **immediately** on death, exile, drunk or poison — drop
  the token (`reconcileTokens`), drop the dusk blocker, restore the normal nominator picker with an
  inline notice.
ledger: every ST nomination (day, nominee, nominee's true alignment); any obligation override.
tests:
  - Given an alive unimpaired good Bishop, when NIGHT→DAY, then the seat holds exactly one
    `PlacedReminder("bishop","Nominate evil")` and no `"Nominate good"`; an evil Bishop gets the
    inverse.
  - Given a good Bishop and a recorded ST nomination of a **good** player, then the token stands;
    when an **evil** player is nominated, then it is gone and the dusk blocker clears.
  - Given a nomination with `nominatorId = null`, then `hasNominatedToday` is false for every player
    and no nominator-side trigger fires (specifically: a Witch-cursed player is not flagged).
  - Given a Bishop in play and a Virgin nominee with `nominatorId = null`, then the warning list has
    the "Storyteller is not a player" note and not the execute note.
  - Given a Bishop who is exiled (or gains `Poisoned`), then the token is removed, `duskCheck` is
    clean, and player nominations are allowed again.
  - Given a Bishop in play, when a **dead** player calls an exile on the Bishop, then it is
    recordable with `threshold == (players.size + 1) / 2`.
open: does the obligation follow the nominee's **registered** alignment (Spy/Recluse)? The wiki says
  "alignment opposite the Bishop's" — assume registered, with an ST override, matching Scapegoat.

---

## judge — Judge · bmr traveller · P0:4 P1:3
today: zero references in code. `Voting.outcome`/`aboutToDie` are pure functions of vote counts, so
the only way to force a result is to fake the tally or hand-kill from the seat sheet, which leaves
the day's record wrong and the block stale.
data:
  - characters.json: ok (`reminders: ["No ability"]`, ability text correct).
  - night_and_jinxes.json: ok — absent, no jinxes.
  - night_guide.json: **no entry**; add `judge.day` with the How-to-Run (declare during any
    nomination period, from declaration until the next nomination; pass = execute + go to night;
    fail = votes to zero, day continues; once per game; not on your own nomination).
setup: `traveller.alignment:<seat>` · ALIGNMENT · blocking (affects nothing mechanically here, but
  the grimoire shows every traveller as good today, which misleads the ST).
identity: plain.
night.first / night.other: none.
day:
  briefing (DAY_START, ACTION): *"Judge in play — ability unspent. Once per game, on someone
    else's nomination, they may force the execution to pass (nominee dies, day ends) or fail (votes
    reset to 0, day continues). Not on their own nomination."* → *"Judge — ability spent (day N)."*
  interceptors: `Nomination.judgeForced: JudgeForce? = PASS | FAIL` (`day-engine.md §A`).
    conflict: `judge.md` proposed `forcedBy: String? + forced: ForcedOutcome?` — `judgeForced` is
    the adopted name; `result` is still written to `ABOUT_TO_DIE`/`SAFE` so existing consumers work.
    - `aboutToDie` replay: `PASS` → block = this nominee and **stop replaying** (nothing later
      displaces it); `FAIL` → **ignore this nomination entirely** for both the block and the
      tally-to-beat, which restores the previously-blocked player automatically (wiki Po/Slayer).
    - `highestVotesToday` must skip `FAIL` nominations — *"a pardon means votes don't count"*.
    - **Keep `voterIds` when forcing FAIL; zero only `votes`.** Gunslinger's "player that voted" and
      `InfoCalc.flowergirl` (`InfoCalc.kt:296-313`) both key off raised hands, which happened.
  day tools: two buttons in the vote panel once a nominee **and** a non-Judge nominator are picked,
    and the same two on the most recently recorded nomination row — available **until another
    nomination is recorded**, then they vanish from the older row. Disabled with
    *"Judge can't override their own nomination"* when `nominatorId == judgeSeatId`. Hidden entirely
    when `isExile == true` (*"exiles are not affected by abilities"*).
  execution: force PASS → `GameActions.execute(state, nomineeId, outcome = DIED, via =
    ExecutionVia.JUDGE, nominatorId = …)` through the **same** confirmation sheet as every other
    execution, so `executionConsequences(...)` (Devil's Advocate, Fool, Sailor, Tea Lady, Zombuul,
    Saint, Fearmonger, Mastermind, **Scapegoat substitution**, Undertaker `Died today`) all fire.
    Then `closeNominations(state, "Execution locked by the Judge")` →
    `nominationsClosedOnDay = cycle`; the Day tab disables the pickers and the phase button reads
    *"Dusk (day is over)"*.
    force FAIL → patch `judgeForced = FAIL, votes = 0, result = SAFE`; show *"Nominations continue.
    <PreviousBlockName> is about to die again."* (or "No one is about to die.")
  effects: exclusive `PlaceToken("judge","No ability", SOURCE)`, `TokenRule(expiry = NEVER)` —
    already on `day-engine.md §F`'s never-expire list. Not placed if the Judge loses their ability
    before using it, so a resurrected unspent Judge has it back.
  LedgerKind: `RULING` — *"Judge forced Po's execution to FAIL (2 votes discarded), day 3."*
death: a forced pass is a **genuine execution** — `DeathKind.EXECUTION`, `ExecutionVia.JUDGE`, not
  `GOOD_ABILITY`. conflict: `status-model.md`'s cause table lists `judge` under `GOOD_ABILITY`;
  that would hide it from the Undertaker, the Saint and the Scapegoat. Remove that row.
  Ability lost on death/exile/drunk/poison; controls disappear.
ledger: which nomination, which direction, which day.
tests:
  - Given 9 alive (threshold 5) and a 2-vote nomination of P recorded with `judgeForced = PASS`,
    then `aboutToDie(state) == P`.
  - Given P on the block from a 5-vote nomination, when a later 6-vote nomination of Q is recorded
    with `judgeForced = FAIL`, then `aboutToDie == P` and `highestVotesToday == 5`.
  - Given `judgeForced = FAIL`, then `votes == 0`, `voterIds` unchanged, and `flowergirl` still
    reports YES when the Demon's id is in those `voterIds`.
  - Given a forced PASS today, then `nominationsClosedOnDay == cycle` and no later nomination moves
    the block.
  - Given a forced-pass execution of a **Saint**, then `WinCheck` fires the Saint advisory (it is a
    real execution); given the nominator **is** the Judge, or `isExile`, then the control is absent.
open: does a **Bishop** Storyteller nomination count as *"another player nominated"*? Ruling
  adopted: yes (it is certainly not the Judge's own) — surface it in the UI text.

---

## matron — Matron · bmr traveller · P0:0 P1:4
today: zero references in code. The only reordering tool is `moveSeat(±1)` in a name-only modal
(`GameExtras.kt:108-140`), which **shifts every player in between**; the seat sheet's "Swap
characters" (`GameActions.kt:98-115`) is a trap — it moves `characterId`/`shownCharacterId` only and
leaves reminders, life/death and ghost votes on the old seat.
data:
  - characters.json: ok (`reminders: []`, ability text correct).
  - night_and_jinxes.json: ok — absent, no jinxes.
  - night_guide.json: **no entry**; add `matron.day` with the How-to-Run **and** the daily
    announcement script (*"players may not leave their seats to talk in private"*).
setup: `traveller.alignment:<seat>` · ALIGNMENT · blocking.
identity: plain.
night.first / night.other: none.
day:
  briefing (DAY_START, ACTION): *"Matron in play. Announce: players may not leave their seats to talk
    in private — you may only whisper with your neighbours. The Matron may call up to 3 seat swaps
    today (used: 0). Unused swaps do not carry over."* Dead/impaired → *"Matron — no ability: seating
    is fixed and private conversations are unrestricted again."*
  day tools: **new** `GameActions.swapSeats(state, idA, idB)` — exchange the two elements of
    `state.players`; touch **no** field of either `Player`, so character, shown identity, reminders,
    life/death, ghost vote and notes all travel with the person. No-op when either id is missing or
    `idA == idB`. This is a different primitive from `swapCharacters` (Barber / Snake Charmer),
    which should be relabelled *"Swap character tokens (Barber / Snake Charmer)"* to stop the
    confusion.
    A "Matron — seat swaps (1 of 3 used today)" card with a two-chip picker over **all** seats
    (character token + name + alive/dead + traveller badge; dead seats are legal — they still hold
    a position for Empath/Chef/No Dashii adjacency).
  counter: `GameState.seatSwaps: List<SeatSwap(day, aId, bId)>`;
    `matronSwapsUsedToday(state) = seatSwaps.count { it.day == state.cycle }`. Disable at 3 with
    *"The Matron has used all 3 swaps today."* Resets by counting on `day`, so nothing expires and
    unused swaps never bank. A single player may be in more than one swap.
  consequences: after each swap show `StatusEffects.seatingImpacts(before, after, lookup)` —
    recomputed No Dashii poisoned pair (before/after names), Tea Lady neighbours and whether
    "can't die" changed, whether the Marionette still neighbours the Demon, which
    Empath/Chef/Shugenja/Balloonist/Steward/Clockmaker seats would now read differently, Lunatic
    adjacency. `derivedPoison` (`StatusEffects.kt:14-33`) and the Tea Lady note
    (`StatusEffects.kt:79-91`) already recompute live — keep them derived, never snapshotted.
  vote/nomination rule changes: none directly, but **vote order is clockwise from the nominee**
    (`DayScreen.kt:167-172`) and derives from `state.players`, so a swap silently reorders the count.
  LedgerKind: `NOTE`/`RULING` per swap — *"Matron swapped Alice and Fatima (swap 2 of 3, day 3)"*.
    This is the only seating history the app would have.
death: none. New seating is **permanent** until changed again; nothing expires. Ability lost on
  death/exile/drunk/poison — the swap card disappears (there is nothing to un-swap).
ledger: `seatSwaps` doubles as the record; log each swap.
tests:
  - Given seats `[A,B,C,D,E]`, when `swapSeats(C, E)`, then the order is `[A,B,E,D,C]` and every
    other index is unchanged.
  - Given C holds a `Poisoned` reminder, is dead and has an unspent ghost vote, when swapped with E,
    then C still holds all three (the whole `Player` moved).
  - Given a No Dashii with Townsfolk neighbours, when a swap changes the ring, then `derivedPoison`
    names the new nearest Townsfolk each way.
  - Given three swaps on day 3, then a fourth is rejected; when the phase advances to day 4, then
    `matronSwapsUsedToday == 0`.
  - Given a Marionette adjacent to the Demon, when a swap separates them, then `seatingImpacts`
    contains the adjacency warning (today `validateSetupState` only runs at SETUP→NIGHT).
open: *"players with physical disabilities are immune to repositioning"* — model as a per-seat note,
  not a rule. Whether `swapSeats` should refuse to move a seat mid-nomination is unspecified.

---

## voudon — Voudon · bmr traveller · P0:3 P1:3
today: zero references in code. With a Voudon in play the vote panel is actively wrong on all three
axes: it offers every **living** player as a votable chip, it refuses dead players whose ghost vote
it already (wrongly) spent, and it announces *"needs 5"* when the answer is *"needs 1"*.
data:
  - characters.json: normalise `ability` to the wiki — `"Only you & the dead can vote. They don't
    need a vote token to do so. A 50% majority isn't required."`
  - night_and_jinxes.json: ok — absent, no jinxes.
  - night_guide.json: **no entry**; add `voudon.day` with the How-to-Run and the daily announcement
    script.
setup: `traveller.alignment:<seat>` · ALIGNMENT · blocking.
identity: plain.
night.first / night.other: none.
day:
  briefing (DAY_START, ALERT): *"Voudon in play. Announce before nominations: 'Living players, your
    hands stay down. Only the dead — and the Voudon — vote today. Dead players do not need a vote
    token and may vote on every nomination.' One vote is enough; the highest tally today wins. Alive
    players still nominate; dead players may not nominate (they may still call exiles."* On loss of
    ability: *"Voting is normal again: half of the N living players is needed and dead players spend
    their token. Anyone already about to die stays about to die."*
  vote rule changes — `DayRules`, resolved in this order (the whole card):
    1. **Exile** (`nominee.isTraveller`) always wins: `canVote` = **every** seat alive or dead;
       `threshold = (players.size + 1) / 2`; `spendsGhostVotes = false`; `voteWeights` **empty**.
       *"The process to exile a Traveller is not affected by abilities."*
    2. **Voudon active** (seat `characterId == "voudon"`, alive, `!impairment(...)`):
       `canVote` = dead players **+ the Voudon only**; `threshold = 1`; **no ghost vote is spent**;
       Bureaucrat/Thief weights still apply to whoever legally votes.
    3. **Normal:** alive players + dead players with an unspent token; `threshold =
       (aliveCount + 1) / 2`; ghost votes spent.
  interceptors: on Record, `spendsGhostVotes == false` **skips** the `toggleGhostVote` loop
    (`DayScreen.kt:232-240`) entirely — today it burns a token a dead player never needed and then
    `DayScreen.kt:184` greys them out for the rest of the game.
  nomination: the nominator gate stays `p.alive` for **executions** (correct: *"alive players
    nominate, dead cannot"*) but must be **lifted for exile callers** — today `DayScreen.kt:137`
    blocks the dead from calling the self-exile the wiki explicitly recommends.
  regime change: persist `Nomination.threshold` + `tallyNotes` (e.g. *"Voudon: 1 vote needed"*) so
    undo/redo and the `aboutToDie` replay stay faithful. `highestVotesToday` must **not** mix
    regimes — exclude nominations whose stored `threshold` differs from the current one and tell the
    ST: *"Voudon exiled — voting has returned to normal; earlier tallies used the Voudon rules."*
  ties: `Voting.outcome` gains `equalKeepsBlock: Boolean` (true under the Voudon) so an equal tally
    returns `SAFE` and leaves the existing block, per *"until another nominee receives **more**
    votes"*. Default `false` preserves today's behaviour everywhere else.
  UI: disabled chips must carry their reason — `Alive — can't vote (Voudon)` / `Ghost vote spent` /
    `Exile: everyone may support`. Header: *"only the dead & the Voudon may raise a hand (needs 1,
    best today: 3)"*.
death: none of their own. Ability ends on death/exile/drunk/poison; a player already on the block
  **stays** on the block, and only *subsequent* nominations revert.
ledger: `Nomination.threshold` + `tallyNotes` per nomination is the record.
tests:
  - Given an alive unimpaired Voudon with 12 alive / 3 dead, then for a resident nominee
    `threshold == 1`, `spendsGhostVotes == false`, eligible = dead ids + the Voudon.
  - Same setup, when 3 dead vote, then the outcome is `ABOUT_TO_DIE` (today: `SAFE`).
  - Given a dead player who voted under the Voudon, then `ghostVoteUsed` is still false and they are
    eligible for the next nomination.
  - Given a **traveller** nominee, then eligible = all player ids including the living,
    `threshold == (players.size + 1) / 2`, weights empty; 5 of 12 fails and 6 passes (wiki ex. 2).
  - Given a Voudon exiled after P was blocked, then `aboutToDie == P` still, and the next
    nomination uses the normal alive threshold with `spendsGhostVotes = true`.
  - Given a Voudon with a `Poisoned` reminder, or dead, then the normal regime applies.
open: **tie behaviour under the Voudon is not ruled on by the wiki.** `equalKeepsBlock = true` is
  this audit's reading and must be confirmed with the Pandemonium Institute before shipping.
  Also: `InfoCalc.flowergirl` needs a caveat line — *"Voudon in play: living players could not vote
  today"*, so a NO answer is uninformative.

---

## beggar — Beggar · tb traveller · P0:2 P1:4
today: the entire ability is invisible. `DayScreen.kt:184` makes any **alive** player votable, so a
tokenless Beggar votes freely; there is no vote-token model at all (`ghostVoteUsed` is one boolean
meaningful only when dead); no donation action; no alignment reveal; and a Poisoned token on the
Beggar marks them impaired everywhere despite *"you are sober & healthy"*.
data:
  - characters.json: update `ability` to the wiki — `"You must use a vote token to vote. If a dead
    player gives you theirs, you learn their alignment. You are sober & healthy."` Add
    `reminders: ["Token"]` (the donor's name goes in the `PlacedReminder.note`, not the label, so
    the token art stays legible).
  - night_and_jinxes.json: ok — absent, no jinxes.
  - night_guide.json: **no entry**; add `beggar.day`.
setup: `traveller.alignment:<seat>` · ALIGNMENT · blocking.
identity: plain, but **`EffectKind.SOBER_HEALTHY`** is innate — a `StandingRule("beggar")` emitting
  `SOBER_HEALTHY` on self, which beats every impairment in `Status.impairment` step 4. Keep the
  Poisoner's token **placeable and visible** (the ST wants to see that the Poisoner wasted a night)
  but derive no impairment and paint no badge; add the seat note *"Beggar is sober & healthy — this
  token has no effect."* conflict: `beggar.md` proposed a `SOBER_AND_HEALTHY` id set inside
  `StatusEffects.isImpaired`; `status-model.md`'s `SOBER_HEALTHY` effect is the adopted mechanism.
night.first / night.other: none.
day:
  briefing (DAY_START, ACTION): *"Beggar in play — holds 2 vote tokens (from Monk†, Recluse†). They
    may vote twice today and no more. Dead players may hand over their token at any time during the
    day; when they do, show the Beggar that player's alignment privately. The Beggar can support
    exiles freely without a token. The Beggar cannot be drunk or poisoned."*
  vote rule changes (extends the Voudon's ordering):
    - **exile** → the Beggar is eligible like anyone else and spends **nothing**
      (*"nominating and voting for exiles are unaffected by this ability"*).
    - **alive Beggar, execution** → eligible only while holding ≥1 token; on Record, spend one and
      remove one `beggar:"Token"` reminder. Disabled reason: `Beggar — no vote token`.
    - **dead Beggar** → ordinary ghost-vote rules.
    - Bureaucrat ×3 / Thief −1 apply to a Beggar's vote like anyone's; the token is spent either way.
  day tools: `GameActions.donateVoteToken(state, donorId, beggarId)` — guards: donor **dead** with a
    token, recipient an **alive Beggar**. Effects: donor spends their token, Beggar gains one, a
    non-exclusive `PlacedReminder("beggar","Token", note = donorName, placedCycle = cycle)` lands on
    the Beggar. Reachable from **both** seat sheets ("A dead player gives their token…" /
    "Give vote token to the Beggar"), and it immediately offers the alignment card.
    conflict: `day-engine.md §E` models this as `toggleGhostVote(donor)` + one
    `("beggar","Vote token")` reminder; `beggar.md` needs a **count** (`Player.voteTokens: Int`)
    because the Beggar hoards. Adopt the count, keep
    `val ghostVoteUsed get() = !alive && voteTokens == 0` as a derived shim so `SeatSheet.kt:176-178`
    and `GrimoireScreen` still compile. Migration: `!alive && !ghostVoteUsed → voteTokens = 1`.
    Label must be one of `"Token"` / `"Vote token"` — pick one and put it in `characters.json`.
  info: new `InfoCalc` entry `beggar` (1 target = the donor) → an **alignment** answer rendered as
    `ShowCard.AlignmentCard`. Answer is the donor's alignment **as they register**: Recluse defaults
    **EVIL**, Spy defaults **GOOD** (the wiki's own Recluse example), both ST-overridable with the
    two-button choice `InfoCalc` already uses. `alignmentFlipped` seats use their current alignment.
    **No false answer and no Vortox override** — the Beggar is sober & healthy; say so in the panel.
  LedgerKind: `TOLD` — *"Monk gave their vote token to the Beggar (day 4) — shown EVIL"*, recording
    the value **shown** so the ruling is defensible later.
death: on death the Beggar **loses the whole hoard and gets exactly one** token
  (*"they lose all previous tokens but gain one to use while dead"*) — `voteTokens = 1` and every
  `beggar:"Token"` reminder is removed, inside `GameActions.kill`. Note the donor: a dead player who
  donates has spent their vote for the game and must not be votable afterwards.
ledger: donations (donor, day, shown alignment); the running token count.
tests:
  - Given an alive Beggar with 0 tokens, then they are **not** eligible for a non-exile nomination;
    given the nominee is a traveller, then they **are**, and nothing is spent.
  - Given a dead Monk with a token and an alive Beggar, when `donateVoteToken`, then the Monk has 0,
    the Beggar has 1, and the Beggar's seat carries one `beggar` reminder naming the Monk.
  - Given a Beggar holding 3 tokens, when killed, then `voteTokens == 1` and no donation reminders.
  - Given a dead **Recluse** donating, then the default answer is EVIL with a good/evil override;
    given a **Spy**, GOOD; given a Vortox in play, the answer is still the true registered one.
  - Given a Beggar carrying `poisoner:"Poisoned"`, then `Status.impairment(beggar)` is empty.
  - Given a Voudon in play, then an alive Beggar with 2 tokens is not eligible and keeps both.
open: does a resurrected Beggar keep donated tokens? (`beggar.md`: a living Beggar keeps whatever
  they hold; `resurrect` sets non-Beggars to 0.) Does a **dead** Beggar still receive donations?
  Rules say only that they get one token on death — assume donations stop.

---

## bureaucrat — Bureaucrat · tb traveller · P0:3 P1:4
today: the **night step works** — `NightOrder` emits it on both sheets with the official reminder
text, and the tray's single declared copy routes through `placeExclusiveReminder` so the token moves
rather than accumulating. The **day** is where it fails: the tally is `orderedVoterIds.size`
(`DayScreen.kt:172,176,198,204,225`), so the app says *"3 so far, needs 5 … safe"* when the true
tally is 5 and the nominee is about to die — and `Nomination.votes`, `highestVotesToday`,
`aboutToDie` and the log all inherit the error for the rest of the day.
data:
  - characters.json: `reminders: ["3 votes"]` ok. Change both night reminders to *"points to a
    player **other than themselves**"* (the ability says "not yourself"; the reminder does not).
  - night_and_jinxes.json: `firstNight[5]`, `otherNight[2]` — **correct**, no jinxes.
  - night_guide.json: `first` and `other` both present and good, and already say "other than
    themselves". Drop `other`'s *"Remove the 3 votes reminder from the previously chosen player"* —
    the tray already moves it — and add *"The token comes off by itself at dusk, and immediately if
    the Bureaucrat dies or is exiled."* `shows: []` stays (the Bureaucrat shows nobody anything).
setup: `traveller.alignment:<seat>` · ALIGNMENT · blocking.
identity: plain.
night.first / night.other:
  gate: `Gates.aliveHolder` AND not impaired. Dead/impaired → `StepGate.Skip("Bureaucrat is dead /
    poisoned — no ability tonight; remove the 3 votes token")`, rendered collapsed with `[Run
    anyway]`. Today `NightOrder.kt:142-178` emits the step for any holder regardless of `alive`.
  action: `ChoosePlayers(sourceId = "bureaucrat", min = 1, max = 1, constraints =
    [ANY_LIVING_STATE, NOT_SELF], sort = ALIVE_FIRST, allowNone = false)`. Dead targets are legal
    (their one ghost vote is then worth 3). Repeat targets are legal — no
    `DIFFERENT_FROM_LAST_NIGHT`. The Bureaucrat's own chip is disabled with the reason *"not
    yourself"*.
  effects: `PlaceToken("bureaucrat","3 votes", TARGET, maxCopies = 1, exclusive = true)` as the
    step's **one-tap** action (today it is a token-tap then a seat-tap).
  deferred: **tomorrow**, every vote by the marked player in an **execution** tally counts 3.
  info: none — the Bureaucrat learns nothing. The *group* learns who is marked the moment they vote,
    because the ST is required to count aloud.
  show: none.
day:
  briefing (DAY_START, INFO): *"Bureaucrat: Alice's vote counts as **3 votes** today. Count it out
    loud so the group hears it. Exiles are unaffected — Alice supports an exile only once. If the
    Bureaucrat dies or is exiled the effect ends immediately and the token comes off."*
  vote weights: `DayRules.voteWeights` — start at 1; ×3 while the player carries
    `bureaucrat:"3 votes"` **and** a living unimpaired Bureaucrat is in play; ×(−1) for
    `thief:"Negative vote"` under the same condition; **both ⇒ −3** (*"3 negative votes"*, wiki).
    `isExile == true` ⇒ **weights empty, every supporter counts 1.**
  tally: `Nomination.votes` becomes the **weighted** tally (because `highestVotesToday` and
    `aboutToDie` must compare weighted values); add `handCount` for the record and `tallyNotes` for
    the arithmetic. Ghost votes are spent **per raised hand, never per weight** — a dead marked
    player spends one token and contributes 3.
  presentation: the header must show the arithmetic so the ST can narrate it —
    *"Vote — 4 hands = 6 votes (needs 5, best today 3)"* with a `×3` badge on the marked chip.
death: none of their own. `TokenRule("bureaucrat","3 votes", expiry = DUSK, maxCopies = 1,
  endsWhenSourceLosesAbility = true)` — already on `day-engine.md §F`'s `EXPIRES_AT_DUSK` additions;
  the immediate teardown on death/exile/impairment runs through `reconcileTokens` /
  `removeTokensOfSource`.
ledger: `Nomination.handCount` + weighted `votes` + `tallyNotes` — log line *"3 hands / 5 votes"*.
tests:
  - Given 9 alive (threshold 5), a marked player and a living unimpaired Bureaucrat, when 3 players
    including the marked one vote, then the tally is 5 and the outcome is `ABOUT_TO_DIE`.
  - Given the same marked player and a **traveller** nominee, then the tally is 3 and the threshold
    is the exile threshold.
  - Given a marked **dead** player with one token, when they vote on an execution, then the tally
    gains 3 and exactly one token is spent.
  - Given a marked player also carrying `thief:"Negative vote"`, then their weight is −3.
  - Given a `3 votes` token in play, when the Bureaucrat is killed or exiled, then no player carries
    a `bureaucrat` token and the next tally is unweighted; given the Bureaucrat is merely poisoned,
    then the weight map is empty **while the token stays on the board**.
  - Given the target picker, then the Bureaucrat's own seat is not selectable.
open: none.

---

## gunslinger — Gunslinger · tb traveller · P0:4 P1:4
today: zero references in code. The app records the day's first tally and then falls silent at
exactly the moment the rules require the ST to prompt. To resolve a shot the ST must notice the
window themselves, remember who voted, open the target's seat sheet, press "Other death", and
remember for the rest of the day that the chance is gone.
data:
  - characters.json: add `reminders: ["No ability"]` so the once-per-day marker has a real token
    (today it is `reminders: []` and the ST must use a generic `Used` token).
  - night_and_jinxes.json: ok — absent, no jinxes.
  - night_guide.json: **no entry**; add `gunslinger.day` with the How-to-Run, including *"if
    clarification is needed, ask voting players to raise their hands again"* — which the app can
    beat outright by re-displaying `voterIds`.
setup: `traveller.alignment:<seat>` · ALIGNMENT · blocking.
identity: plain.
night.first / night.other: none.
day:
  briefing (DAY_START, ACTION): *"Gunslinger in play. After today's **first execution vote** is
    tallied, ask <name> whether they want to shoot. They may pick any player who voted; that player
    dies immediately. It is **not** an execution — the day continues and the Undertaker learns
    nothing. The chance is used up after that first tally whether they shoot or not. Exile
    supporters are never targets."*
  window: `firstExecutionVoteToday(state) = state.nominations.firstOrNull { it.day == state.cycle &&
    !it.isExile }`. **Exiles never open the window and their supporters are never targets** — this
    reproduces the wiki's Thief-exile worked example exactly. A **Judge-forced-FAIL** nomination
    still counts as the day's first execution vote and its `voterIds` are still legal targets (the
    hands went up) — which is why `judge.md` keeps `voterIds` when zeroing `votes`.
  day tools: a card immediately below the just-recorded nomination, plus a top-of-tab banner until
    dismissed. Target picker = **exactly `nomination.voterIds`**, in clock order, with character
    tokens and alive/dead state; no other seat selectable. Empty `voterIds` → *"Nobody voted — the
    Gunslinger has no legal target today."*
  execution: **this is not an execution.** Do **not** route through `GameActions.execute`. Use
    `kill(targetId, DeathCause.GUNSLINGER)` — `day-engine.md §J` adds the enum value (append, never
    reorder). conflict: `gunslinger.md` proposed `DeathCause.STORYTELLER`; `GUNSLINGER` is the
    adopted name. `status-model.md`'s cause table has **no** `gunslinger` row — add one that is
    neither `DEMON_ABILITY` nor `EXECUTION`, so `killOutcome` lets Sailor/Tea Lady `CANT_DIE`,
    Fool `Spends`, Lleech `DEATH_TIED_TO` and Zombuul `RegistersDead` block it while
    `SAFE_FROM_DEMON` (Soldier, Monk), `CANT_DIE_TONIGHT` (Innkeeper) and `SURVIVES_EXECUTION`
    (Devil's Advocate) do not. The shot panel must say which protections **do not** apply, by name.
  effects: on confirm **or decline**, place exclusive `PlaceToken("gunslinger","No ability", SOURCE)`
    — *"the Gunslinger cannot kill further that day, whether they used the ability or not."*
    `TokenRule(expiry = DAWN)`; add `("gunslinger","No ability")` to `EXPIRES_AT_DAWN` (it is **not**
    on `day-engine.md §F`'s current list — add it).
  what must NOT happen: the day does not end, `aboutToDie` is untouched (*"the Imp remains the
    execution target"*), further nominations proceed, the Undertaker sees nothing, the Saint does
    not lose the game, the Mastermind day is not triggered, and the Scapegoat is **not** offered
    (only executions trigger it). Say so in the confirmation toast.
  LedgerKind: `RULING` — *"Gunslinger shot <name> (day N) — not an execution"* / *"Gunslinger
    declined to shoot (day N)"*.
death: on-death triggers fire normally on a shot (Moonchild, Sweetheart, Barber, Ravenkeeper,
  Farmer, Poppy Grower) — `deathNotes`/`onDeath` already lists them. Ability lost on
  death/exile/drunk/poison: no window opens.
ledger: whether they shot, whom, which day, with the "not an execution" annotation so a later
  Undertaker dispute is settleable.
tests:
  - Given an exile recorded first and then an execution nomination, then `firstExecutionVoteToday`
    returns the **execution** one (wiki Thief-exile example), and no window opens on the exile.
  - Given two execution nominations today, then the target pool is the **first** one's `voterIds`.
  - Given the Gunslinger shoots a voter, then the death cause is `GUNSLINGER`, `aboutToDie` is
    unchanged, and the Undertaker reports no execution today.
  - Given the Gunslinger shoots the **Saint**, then no Saint advisory; given the **Demon** with a
    Mastermind in play, then `mastermindDayActive` is not set.
  - Given the Gunslinger declines, then `("gunslinger","No ability")` is placed and no further shot
    is offered; when NIGHT→DAY runs, then it is gone.
  - Given a Voudon in play, then the target pool is only the dead voters and the Voudon.
open: is a Gunslinger shot `GOOD_ABILITY` or `EVIL_ABILITY` for `DeathKind` purposes when the
  Gunslinger is evil? Nothing in the rules turns on it today, but Storm Catcher's
  `ONLY_EXECUTION_KILLS` and any future kind-keyed rule will need an answer.

---

## scapegoat — Scapegoat · tb traveller · P0:4 P1:3
today: zero references in code. All three execution affordances kill the on-block player and only
the on-block player; the only route to a substitution is the seat sheet's bare "Executed" button on
the Scapegoat's own seat, after which the Day tab **still** says "On the block: <original nominee>"
with a live Execute button one tap away from an illegal second execution.
data:
  - characters.json: ok (`reminders: []`, ability text correct).
  - night_and_jinxes.json: ok — absent, no jinxes.
  - night_guide.json: **no entry**; add `scapegoat.day`.
setup: `traveller.alignment:<seat>` · ALIGNMENT · **blocking, and the highest-stakes instance in
  this group** — the entire trigger condition is *"a player of **your alignment**"*, and
  `Team.TRAVELLER.isEvil == false` makes every Scapegoat silently good today.
identity: plain, but **registration-aware on the other side**: the trigger compares the Scapegoat's
  alignment to the nominee's **registered** alignment — the wiki's third example pairs a **good**
  Scapegoat with an executed **Spy**. Default `Player.isEvil(lookup)`; for `spy`/`recluse` offer
  `"<Name> registers as GOOD / EVIL for this"` → `LedgerEntry(RULING, sourceId = "misregister")`.
night.first / night.other: none.
day:
  briefing (DAY_START, ACTION): *"Scapegoat in play (good). If a **good** player is executed today,
    you may execute <name> instead — the nominated player survives, the Scapegoat dies as an
    execution (the Undertaker sees Scapegoat), and the day ends. It is entirely your call. A
    misregistering player (Spy, Recluse) may count as either alignment."* Hidden/greyed when the
    Scapegoat is dead or impaired.
  execution: routed through the **one** funnel, `GameActions.execute(state, nomineeId, outcome =
    SURVIVED, preventedBy = "scapegoat", diedInsteadId = scapegoatId, via = …, lookup)`. The
    `ExecutionRecord` still **belongs to the nominee** — that is what makes the Saint safe and the
    Mastermind day not fire — while `diedInsteadId` is the seat that actually dies.
    conflict: `scapegoat.md`'s `ExecutionOptions` + `executeWithScapegoat(...)` are superseded by
    `day-engine.md §C`'s `execute(...) + executionConsequences(...)` plus `status-model.md`'s
    `killOutcome` step 12 (`Choice(dies / Redirect(scapegoat))`) as the decision point.
    Legal substitutes = every `scapegoat` seat that is alive, unimpaired, and of the nominee's
    registered alignment; list each with its alignment when there are several.
  effects: `kill(scapegoatId, DeathCause.EXECUTION)` — **not** `EXILE`, so the Undertaker learns
    *Scapegoat* and the traveller-⇒-exile assumption at `DayScreen.kt:353-355` must be bypassed.
    The Scapegoat must **not** take `status-model.md §6`'s `leftGame = true` exile path.
    The nominee is not killed and stays alive. Then `closeNominations(state, "Scapegoat executed —
    the day is over")` — *"when the Scapegoat dies, it counts as an execution, halting further
    nominations that day."* The block banner becomes *"Executed today: Scapegoat (in place of
    Fortune Teller)"* and the dusk guard goes straight to "Begin night".
  where it must appear: the block banner (`DayScreen.kt:93-115`), the nomination row (`:350-357`),
    the dusk guard (`GameShell.kt:592-616`) and a **Judge forced pass** — all four open the same
    execution sheet, protections first, then the substitution, then the consequences.
  LedgerKind: two linked entries — *"Scapegoat executed in place of Fortune Teller (day 3)"* and
    *"Fortune Teller survived the execution (Scapegoat)"*; plus the misregistration `RULING`.
death: the Scapegoat dies by `EXECUTION` (`ExecutionRecord.diedInsteadId`). Must **not** fire,
  because the nominee did not die: Saint's good-loses, Fearmonger, Mastermind's extra day, Evil Twin.
  **Does** fire: Undertaker (sees `Scapegoat`) and normal on-death triggers. Devil's Advocate resolves
  **first** — if the nominee already survives, no Scapegoat is needed. Ability lost when dead or
  impaired. An exiled Scapegoat dies by `EXILE` (Undertaker learns nothing); a Gunslinger shot is not
  an execution and never triggers the substitution.
ledger: the substitution pair, the registration ruling, and the surviving nominee.
tests:
  - Given a good alive unimpaired Scapegoat and a good nominee on the block, then the Scapegoat is
    offered; given an **evil** Scapegoat and a good nominee, then it is not.
  - Given a good Scapegoat and an executed **Spy**, when the ST rules "registers GOOD", then the
    Scapegoat is offered; when "registers EVIL", then not.
  - Given the substitution is applied, then the nominee is alive, the Scapegoat is dead with
    `DeathCause.EXECUTION`, the Undertaker names *Scapegoat*, `nominationsClosedOnDay == cycle`, and
    no further nomination is recordable today.
  - Given a **Saint** on the block and a good Scapegoat substituted in, then no Saint advisory;
    given the **Demon** with a Mastermind in play, then `mastermindDayActive` is not set and the
    Demon is alive.
  - Given a poisoned or dead Scapegoat, then no substitute is offered; given a Gunslinger shot on a
    player of the Scapegoat's alignment, then none is offered either.
open: does an executed Scapegoat leave the game (`leftGame`) the way an exiled traveller does? They
  *die by execution*, so assume a normal death — but both thresholds and `alivePlayers` hinge on it.
  Multiple same-alignment Scapegoats: the ST picks; no rule orders them.

---

## thief — Thief · tb traveller · P0:3 P1:4
today: identical shape to the Bureaucrat — the **night step works** (correct order position, official
reminder text, single declared copy so the tray moves the token), and the **day** ignores it. With a
marked player voting the app reports a tally 2 too high, and `highestVotesToday`, `aboutToDie` and
the log all inherit the error.
data:
  - characters.json: `reminders: ["Negative vote"]` ok. Change both night reminders to *"points to a
    player **other than themselves**"*.
  - night_and_jinxes.json: `firstNight[6]`, `otherNight[3]` (immediately after `bureaucrat`) —
    **correct**, no jinxes.
  - night_guide.json: `first` and `other` present and good, already say "other than themselves" and
    "(subtract it from the tally)". Drop `other`'s *"Remove the Negative vote reminder from the
    previously chosen player"* — the tray moves it — and add *"The token comes off by itself at
    dusk, and immediately if the Thief dies or is exiled. Exiles are never affected."*
setup: `traveller.alignment:<seat>` · ALIGNMENT · blocking.
identity: plain.
night.first / night.other:
  gate: `Gates.aliveHolder` AND not impaired. Dead/impaired → `StepGate.Skip("Thief is dead /
    poisoned — no ability tonight; remove the Negative vote token")` with `[Run anyway]`.
  action: `ChoosePlayers(sourceId = "thief", min = 1, max = 1, constraints = [ANY_LIVING_STATE,
    NOT_SELF], sort = ALIVE_FIRST, allowNone = false)`. **Dead targets are explicitly allowed and
    must not be filtered out or sorted last** — *"dead players can be chosen, especially useful on
    the final day."* Repeat targets are legal: surface `Memory.lastChoice(state,"thief")` as a hint
    (*"last night: Marianna — the wiki suggests varying the target"*) and do **not** apply
    `DIFFERENT_FROM_LAST_NIGHT`.
  effects: `PlaceToken("thief","Negative vote", TARGET, maxCopies = 1, exclusive = true)` as the
    step's one-tap action.
  deferred: **tomorrow**, every vote by the marked player in an **execution** tally counts −1.
  info: none — the Thief learns nothing; the group learns who is marked because the tally audibly
    dips when they raise their hand.
  show: none.
day:
  briefing (DAY_START, INFO): *"Thief: Marianna's vote counts as **−1** today. Count aloud as normal
    — the tally will dip when she raises her hand. Exiles are unaffected: for an exile her support
    counts +1 as usual. If the Thief dies or is exiled the effect ends immediately."*
  vote weights: shared with the Bureaucrat — 1 by default, ×(−1) with `thief:"Negative vote"` and a
    living unimpaired Thief, ×3 with `bureaucrat:"3 votes"`, **both ⇒ −3**. `isExile == true` ⇒
    **no weights at all**, every supporter counts +1 (the Thief page's own Gunslinger-exile
    example).
  tally: `Σ weight`, **signed and unclamped** — a tally of −1 is legal and informative; it simply
    never reaches the threshold. `Voting.outcome` already behaves correctly for negative inputs
    (`votes < threshold → SAFE`), and `highestVotesToday` only considers `ABOUT_TO_DIE`/`TIED`
    nominations so a negative tally can never lower the bar — confirm both with tests.
    Ghost votes are spent **per raised hand, never per weight**.
  presentation: a **clockwise running total** so the ST can narrate it exactly as the wiki does —
    *"Ali 1 · Bo 2 · Cara 3 · Marianna −1 → 2 · Dan 3 · Eve 4"* — with a `−1` (or `−3`) chip badge.
    This is the wiki's *"1… 2… 3… 2… 3… 4… 5"* example; a single final number cannot express it.
death: none of their own. `TokenRule("thief","Negative vote", expiry = DUSK, maxCopies = 1,
  endsWhenSourceLosesAbility = true)` — on `day-engine.md §F`'s `EXPIRES_AT_DUSK` additions;
  immediate teardown on death/exile/impairment via `reconcileTokens` / `removeTokensOfSource`
  (*"negative vote becomes positive immediately if Thief dies/exiles"*).
ledger: `handCount` + weighted `votes` + `tallyNotes`.
tests:
  - Given 11 alive (threshold 6), a marked player and a living unimpaired Thief, when 6 players
    including the marked one vote, then the tally is 4 and the outcome is `SAFE` (today:
    `ABOUT_TO_DIE`).
  - Given the same marked player and a **traveller** nominee, then their support counts **+1**, the
    tally is the raw hand count, and the threshold is the exile threshold.
  - Given a marked player also carrying `bureaucrat:"3 votes"`, then their weight is −3.
  - Given a `Negative vote` token in play, when the Thief is killed or exiled, then no player carries
    a `thief` token; given the Thief is merely poisoned, then the weight map is empty while the token
    stays on the board.
  - Given a nomination whose weighted tally is −1, then the result is `SAFE` and `highestVotesToday`
    is unchanged by it.
  - Given a marked **dead** player with one token, when they vote on an execution, then the tally
    decreases by 1 and exactly one token is spent.
open: none.
