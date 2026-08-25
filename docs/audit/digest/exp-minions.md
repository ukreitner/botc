# Digest — Experimental Minions (14 characters)

## Group notes

1. **One funnel unblocks five characters.** boomdandy, fearmonger, goblin, psychopath, vizier are pure execution characters sharing one P0: there is no `GameActions.execute` funnel and no way to record *"executed but did not die"*. day-engine §C `execute(...) ` + `ExecutionRecord` + `executionConsequences(...)` closes 9 P0s at once; schedule nothing else in this group first.
2. **conflict — two `ExecutionRecord`s in the mechanics specs.** status-model §4 (`kind: ExecutionKind`, `diedEventId`, `preventedBy`) vs day-engine §A (`outcome: ExecutionOutcome`, `via`, `diedInsteadId`, `tally`, `threshold`, `abilityImpairedAtExecution`). Cards use day-engine's, the superset; the lead must merge them.
3. **conflict — `LedgerEntry` (records-and-memory §A, the brief's canon) vs `DayEntry` (day-engine §A).** Every character-file record type here (`Announcement`, `PublicStatement`, `HarpyChoice`, `Ruling`, `Misregistration`) is translated to `LedgerEntry`; day-engine's `DayEntryKind.GOBLIN_CLAIM / PSYCHOPATH / MEZEPHELES / MADNESS / ANNOUNCEMENT` must become `LedgerKind + sourceId` or the two lists collide.
4. **conflict — `SetupTask` (ux/setup-and-home) vs `SetupRequirement` (mechanics/setup-and-identity).** Cards use setup-and-identity's **ids** (`boffin.grant`, `xaan.X`, `mezepheles.word`, `widow.know`, `marionette.token`, `marionette.seat`, `summoner.bluffs`) with the brief's field names. One shape, one id namespace.
5. **All bespoke setup state collapses into `setupChoices: Map<String,String>`** (setup-and-identity §D.3): boffin's `boffinGrantId`, xaan's `setupChoices["xaan"]: Int`, mezepheles' `mezephelesWord`. Xaan's is load-bearing — X is frozen at setup and can never be recomputed from the live grimoire.
6. **summoner + xaan share a `Night 1→2→3` counter no code touches today.** night-engine §4 `TokenRule.countdownNext / countdownAt` is the mechanism; both files propose `GameActions.advanceNightCounter(state, sourceId, labels)` — same thing, pick one. Xaan also places `X` on night X, `Expiry.DUSK`.
7. **mezepheles, wizard, summoner all need "a spent marker removes this step from the sheet"** — `Gates.notSpent(sourceId, spentLabel)` + `Character.spentLabel`. Today's heuristic (`ability.startsWith("Once per game")`, `NightScreen.kt:204`) matches the Wizard and **not** the Mezepheles or Summoner, so `spentLabel` must come from data, never from the ability text.
8. **widow + xaan need conditional poison that ends/pauses with its source.** status-model §1 already lists `widow` and `xaan` `StandingRule`s; both files' `derivedPoison` patches translate 1:1 to `Effect(kind = POISONED, endsWithSource = true)` + the recursive `abilityWorks` of §2. Never a sweepable seat token.
9. **Madness has no source today.** `StatusEffects.kt:162-164` matches a bare `"Mad"` label and hard-codes *"Cerenovus-mad"*, so Harpy madness names the wrong character and never says what must be claimed. Needs `PlacedReminder.characterId` / `targetPlayerId` (records-and-memory §A).
10. **Data debt, this group alone:** ~35 missing or stale jinx rows (vizier 9, summoner 10 + 1 stale, boffin 5 + 1 stale, marionette 4, fearmonger 2, widow 1 + 1 stale, goblin/psychopath/boomdandy/organgrinder 1 each), and boomdandy, goblin and psychopath have **no** `night_guide.json` entry *and* no night step to hang one on — they need a `day` key or a Day-tab run-book.

---

## boffin — Boffin · exp Minion · P0:3 P1:3

today: night 1 says "wake the Boffin and the Demon" and opens a character picker whose result is
discarded on dismiss; the granted ability never appears on any later night sheet, nothing validates
the pick, and nothing reacts when the Boffin dies or is poisoned.
data:
  - characters.json: `:1754` add `remindersGlobal: ["Demon has this ability"]` (today `reminders: []`,
    `remindersGlobal: []` — the tray shows "No reminder tokens"). Ability text ok.
  - night_and_jinxes.json: firstNight index 7 ok, no otherNight entry ok. Fix stale `boffin×drunk`
    (`:284`) → "The Demon cannot have the Drunk ability." Add `boffin×alchemist` ("If the Alchemist has
    the Boffin ability, the Alchemist does not learn what ability the Demon has."), `boffin×goon` ("If
    the Demon has the Goon ability, they can't turn good due to this ability."), `boffin×ogre`,
    `boffin×politician` ("The Demon cannot have the Ogre/Politician ability."), `boffin×villageidiot`
    ("If there is a spare token, the Boffin can give the Demon the Village Idiot ability.").
  - night_guide.json: `:1315` second show card's `token: "pick"` → read `setupChoices["boffin.grant"]`,
    no picker. No `other` entry needed — the injected step carries the grant's own guide via `abilityId`.
setup: `boffin.grant` · GRANT/PICK_CHARACTER · "Choose the not-in-play good ability the Demon has" ·
  candidates = script TOWNSFOLK+OUTSIDER, not in play, minus `drunk`/`heretic`/`ogre`/`politician`
  (`villageidiot` allowed, labelled "needs a spare token"), **sorted alphabetically — never
  in-play-first, which is what `NightScreen.kt:409` does today** · validation (blocking): unset;
  granted id in play; jinx-forbidden id; the granted character's own setup bracket applied through
  `virtualSetupCharacters` (Choirboy→King, Huntsman→Damsel).
identity: Boffin registers as itself. The alive Demon gains a second acting role:
  `FloatingGrant(abilityId = setupChoices["boffin.grant"], sourceId = "boffin", holder = ALIVE_DEMON,
  worksWhileImpaired = true)`, `GrantMode.ADD` (the Demon keeps its own step). setup-and-identity §B
  adopts this file's `NightStep.abilityId` verbatim; `boffinGrantId` as a bespoke field is dropped.
night.first:
  gate: `Fire` when a `boffin` seat exists (alive or not — it is night 1). Not per-holder.
  action: none — the grant is chosen at setup; offer a re-pick chip only.
  effects: `PlaceToken("boffin", "Demon has this ability", Ref.TARGET = demon seat, exclusive)`, NEVER.
  show: `ShowCardTo(SOURCE, "THIS CHARACTER SELECTED YOU")` + Boffin token, then
    `ShowCardTo(SOURCE, "THE DEMON HAS THIS ABILITY")` + the granted token, prefilled from state.
  visibility: `playerIds = boffinSeats + demonSeats` — both are woken, and the step row must name both
    (today only the Boffin is listed, so the tray and chips are Boffin-only).
night.other: the Boffin has none. The **grant** is an inserted step:
  gate: `Fire` when `boffinActive` = Boffin seat alive AND `Status.impairment(boffin).isEmpty()` AND an
    alive Demon exists; else `Skip("Boffin is dead/poisoned")` **plus** a banner on the Demon's own
    step: "No Boffin ability tonight — {Boffin} is {dead/poisoned}."
  action/effects: the granted character's own, at the granted character's night-order index,
    `StepKey(id = "boffin:<grant>", playerId = demonSeatId)`, `abilityId = <grant>`.
  info: `InfoCalc.compute(characterId = grant, holderId = demonSeat)` returns the TRUE answer even
    when the Demon is poisoned (`worksWhileImpaired`); this must **suppress** the normal "Demon is
    poisoned → give false info" path and emit instead *"via the Boffin — works while the Demon is
    drunk or poisoned; fails only if the Boffin is drunk, poisoned or dead."*
day: none. Offer `setupChoices["boffin.grant"]` in `bluffSets["demon"]` tagged "Boffin grant — legal
  bluff" (wiki: *"may be 1 of the Demon's 3 bluffs"*).
death: Boffin dies → `Announce(DAWN_PRIVATE, "Boffin died — the Demon loses the <Grant> ability")`, and
  the injected step is gone from that night on (falls out of the gate). New Demon (star-pass, Scarlet
  Woman, Fang Gu, Pit-Hag, Barber, Kazali) → the grant follows automatically (`holder = ALIVE_DEMON`)
  plus `Prompt(NOW, CHOOSE_CHARACTER, "New Demon — keep <Grant>, or choose a different not-in-play good
  ability?")`. Demon dies → keep the stored grant; suppress the step unless the grant works while dead.
ledger: `setupChoices["boffin.grant"]` (permanent); `boffin:Demon has this ability` → `Expiry.NEVER`;
  `LedgerEntry(kind = RULING, sourceId = "boffin")` when the ST re-grants to a new Demon.
tests:
  - Given Boffin+Imp and `boffin.grant == "chambermaid"`, when the other-night plan is built, then a
    step `StepKey("boffin:chambermaid", impSeat)` sits at the Chambermaid's index with `playerIds ==
    [impSeat]`. (Fails: no step is ever produced.)
  - Given the same, when the Boffin dies, then no `boffin:*` step exists and the dawn report names the
    lost ability.
  - Given the Imp is poisoned but the Boffin is healthy, then the step still fires and `InfoCalc`
    returns the TRUE count with the Boffin caveat, not the poisoned-Demon caveat.
  - Given `boffin.grant == "drunk"` / an in-play id / `"choirboy"` with no King, then setup validation
    reports the jinx / the in-play violation / the missing King.
  - Given Boffin+Imp+Scarlet Woman, when the Imp star-passes, then the grant is preserved and a
    re-grant prompt is queued for the new Demon.
open: which granted abilities survive the **Demon's** death (`banshee`, `sweetheart`, … — no
  authoritative list); whether a `sailor` grant self-drunkens the Demon (this file says the drawback
  binds the granted ability only).

---

## boomdandy — Boomdandy · exp Minion · P0:2 P1:4

today: nothing. Executing the Boomdandy records exactly one death and the game continues as if a
normal Minion died. No guide entry, no reminder token, zero engine references.
data:
  - characters.json: `:1766` add `"reminders": ["Exploded"]` (today none). Ability text ok.
  - night_and_jinxes.json: correctly absent from both night lists. Add the Plague Doctor jinx: "If the
    Storyteller would gain the Boomdandy ability, a player becomes the Boomdandy."
  - night_guide.json: **no `boomdandy` entry at all**. The guide is only surfaced from night steps
    (`NightScreen.kt:792`), so the run-book must live in a `day` key or in the explosion-flow strings —
    the character file prefers the latter.
setup: none.
identity: plain.
night.first / night.other: none — the Boomdandy never wakes. Correctly absent from both lists.
day: this is the whole character, and it hangs off `GameActions.execute`.
  - **trigger:** `ExecutionRecord.playerId` is a `boomdandy` seat, **regardless of `outcome`** — a
    Devil's-Advocate-protected Boomdandy (`outcome == SURVIVED`) still explodes; that is the headline
    rule. Gate on `Status.impairment(boomdandy).isEmpty()`, with an explicit ST override, because the
    wiki does not rule on a poisoned Boomdandy.
  - `executionConsequences(...)` returns `ExecutionConsequence(sourceId = "boomdandy", headline =
    "{name} was the BOOMDANDY — declare that they have exploded", impaired = <bool>)` and the UI opens
    a blocking, undoable four-stage flow:
    1. **kill down to 3** — every alive seat listed in seat order rotating from the Boomdandy (the
       physical "hand out and rotate" method), one tap = `Attack(Ref.TARGET, cause = KillCause(
       EVIL_ABILITY, "boomdandy"), respectProtection = true)`; live counter "N alive — kill until 3
       remain"; the Demon's row is badged `Demon — must survive` and disabled behind a long-press
       override; when a target `killOutcome` returns `Blocked`, show the wiki's escape hatch *"{name}
       can't die — you may rule that four players remain alive."*
    2. **countdown** — `Timer.kt` gains `countFrom = 10` with per-second rendering and a `FREEZE`
       screen (today only 1m/2m/5m silent presets exist, `Timer.kt:88-97`).
    3. **tally** — a +/- counter per seat for **every** player, alive *and* dead ("even dead players
       who have no vote token may point"); show the leader; on a tie show `TIE — nobody dies, the day
       ends now.`
    4. **resolve** — `"{leader} dies"` (`KillCause(EVIL_ABILITY, "boomdandy")`) or `"Tie — end the day"`,
       then `closeNominations(state, "The Boomdandy exploded — the day is over.")` →
       `nominationsClosedOnDay = cycle`.
  - `WinCheck.check` must be **suppressed while the flow is open** and re-evaluated exactly once at the
    end (the ≤2-alive advisory otherwise fires mid-procedure).
  - Non-execution deaths (Golem, Psychopath, Witch, Demon kill) → **no** explosion. Exile → no explosion.
death: `PlacedReminder("boomdandy", "Exploded")` on the Boomdandy seat so a resumed session knows the
  flow ran; never expires (day-engine's never-expire list).
ledger: `LedgerEntry(kind = ANNOUNCE, sourceId = "boomdandy")` for the declaration; one
  `LedgerEntry(kind = NOTE)` per stage so the flow is auditable and undoable.
conflict: this file proposed `ExecutionRecord(day, playerId, died, nominatorId)` and a `dayEnded` flag;
  translated to day-engine's `ExecutionRecord(outcome = DIED|SURVIVED, …)` and `nominationsClosedOnDay`.
tests:
  - Given an 11-player game and `execute(boomdandySeat, DIED)`, then `executionConsequences` contains
    the Boomdandy explosion with `playersToKeepAlive = 3`.
  - Given `execute(boomdandySeat, SURVIVED, preventedBy = "devilsadvocate")`, then the explosion is
    **still** returned and the `ExecutionRecord` shows `SURVIVED`.
  - Given the Boomdandy dies to a Demon kill at night, then no explosion consequence exists.
  - Given a poisoned Boomdandy is executed, then no explosion fires and the advisory says the ST may
    override.
  - Given a two-way tie in the tally, then nobody dies and `nominationsClosedOnDay == cycle`.
open: does a poisoned Boomdandy explode? (wiki silent — implemented as "no, with override").

---

## fearmonger — Fearmonger · exp Minion · P0:3 P1:3

today: the night step tells you to place `Fear` and announce; nothing tells you whether the target
changed, nothing reminds you at dawn, the day warning fires for **any** nominator, and the win is never
detected.
data:
  - characters.json: `:1850`-adjacent `:1778` carries "…All players know if you choose a new player."
    while the fetched wiki page shows only the first sentence. Reconcile against the official script
    tool before editing either — **open**, not a defect. `reminders: ["Fear"]` ok.
  - night_and_jinxes.json: firstNight 38 / otherNight 26 both correct. **No Fearmonger jinx rows at
    all** — add `fearmonger×vizier` ("The Vizier wakes with the Fearmonger, learns who they choose and
    cannot choose to immediately execute that player.") and `fearmonger×plaguedoctor` ("If the
    Storyteller would gain the Fearmonger ability, a Minion gains it, and learns this.").
  - night_guide.json: `:1334` add to `other` the explicit *"executed but survives still loses"* clause
    and the dead/poisoned-Fearmonger case.
setup: none.
identity: plain.
night.first / night.other (identical):
  gate: `aliveHolder` — `Skip("Dead — the Fearmonger has no ability; leave the Fear token where it is")`
    when dead. Impaired holder still `Fire`s (the announcement is about *acting*, not about working).
  action: `ChoosePlayers("fearmonger", "Who does the Fearmonger threaten?", min = 1, max = 1,
    constraints = [ANY_LIVING_STATE, SELF_ALLOWED], sort = SEAT_ORDER, allowNone = false)` — any player,
    alive or dead, evil or good, self included. **Default-select the seat that currently holds `Fear`**
    so "same as last night" is one tap.
  effects: `PlaceToken("fearmonger", "Fear", Ref.TARGET, maxCopies = 1, exclusive = true)` +
    `RecordChoice()`. `changed = lastChoice("fearmonger", holderId)?.playerIds != [target]`.
    If `changed` → `Announce(BriefingSlot.DAWN_PUBLIC, "The Fearmonger has chosen a player.")` **and** a
    step banner `ANNOUNCE NOW`. If unchanged → `Same player as last night — say nothing.`
    If impaired → caveat *"the mark does nothing tonight; announce anyway"* (flag as an ST judgement
    call, do not decide silently).
  info: none.
  show: existing `message` card "CHOOSE A PLAYER TO THREATEN…" is correct.
  visibility: the whole table hears the announcement; nobody learns who was chosen.
day: `DayBriefing`/`Briefing.forDay` line (ST-private): *"{Fearmonger} can end the game by nominating
  {target}."* Nomination trigger (day-engine §D row exists, `WARN`): the check must read **both**
  `nomineeId == fearSeat` **and** `nominatorId == fearmongerSeat`, with three-way text —
  (a) live+sober Fearmonger nominating → "FEARMONGER NOMINATION — if {target} is executed by this
  nomination, their team LOSES and the game ends, even if they survive the execution";
  (b) Fearmonger nominating while dead/impaired → "the Fear mark does nothing";
  (c) anyone else nominating → "only a nomination BY {Fearmonger} triggers it — this nomination is
  normal". Today `StatusEffects.kt:158-160` ignores `nominatorId` entirely and prints (a) in all three
  cases. Same three-way text on the about-to-die banner and in the Execute confirmation.
death/win: `ExecutionRecord.playerId == fearSeat && nominatorId == fearmongerSeat` and the Fearmonger
  was alive+unimpaired at nomination time → `WinCheck.Advisory(goodWins = !targetIsEvil, ruleId =
  "fearmonger", reason = "…{target}'s team loses")`. Fires whether `outcome == DIED` **or**
  `SURVIVED` — the wiki is explicit. Alignment is the **current** one (`Player.isEvil(lookup)`, honouring
  `alignmentFlipped`), so a Mezepheles-turned player counts as evil. Exile is not an execution → nothing.
ledger: `fearmonger:Fear` → `Expiry.NEVER` (already correct — in neither expiry table). `LedgerEntry(kind
  = ANNOUNCE, sourceId = "fearmonger", delivered)` per changed choice; `LedgerEntry(kind = CHOICE)` every
  night, which also powers the changed/unchanged test.
conflict: status-model's cause table lists `fearmonger` under `DeathKind.EVIL_ABILITY`. The Fearmonger
  never kills — it produces a **win**, not a death. Remove it from that table or the cause is unreachable.
tests:
  - Given Fear on Alice and Bob is the Fearmonger, when Carol nominates Alice, then the note says the
    nomination is normal and never says "their team loses". (Fails today.)
  - Given the same, when Bob nominates Alice, then the note contains "team LOSES"; when Bob is poisoned
    or dead, then it says the mark does nothing.
  - Given Bob nominates Alice and `execute(alice, SURVIVED)`, then the Fearmonger advisory still fires.
  - Given Fear re-placed on the same seat, then no `ANNOUNCE` ledger entry is queued; on a new seat,
    exactly one is.
  - Given a full NIGHT→DAY→NIGHT cycle, then `fearmonger:Fear` is still on the seat.
open: reconcile the second ability sentence against the official script tool.

---

## goblin — Goblin · exp Minion · P0:3 P1:3

today: nothing in the Day tab reacts to a Goblin claim; marking it costs ~6 taps across two tabs
mid-nomination; executing a Claimed Goblin just records a death.
data:
  - characters.json: `:1792` ok (`reminders: ["Claimed"]`, text verbatim).
  - night_and_jinxes.json: correctly absent from both night lists. `cerenovus×goblin` present at `:155`
    and correct. Add `goblin×plaguedoctor` ("If the Storyteller would gain the Goblin ability, a Minion
    gains it, and learns this.").
  - night_guide.json: **no `goblin` entry.** Add one under a `day` key (or ship as Day-tab strings)
    covering: the claim window, today-only, false claims, and the executed-but-survived question.
setup: none.
identity: plain.
night.first / night.other: none — the Goblin never wakes.
day: the claim is captured **in the nomination card**, above the vote tally (the claim must precede
  voting):
  - `☐ Claims to be the GOBLIN` visible whenever a nominee is selected. Sets `Nomination.goblinClaim =
    true`; adds `("goblin","Claimed")` **non-exclusively** (day-engine §J: several players may claim on
    the same day, so call `addReminder` with a per-seat de-dupe, never `placeExclusiveReminder` — today
    `characters.json` declares one `Claimed`, which makes the night tray treat it as exclusive);
    records `LedgerEntry(kind = STATEMENT, sourceId = "goblin", speakerId = nominee)` and
    `LedgerEntry(kind = ANNOUNCE, "…{name} has publicly claimed to be the Goblin.")`.
  - ST-only inline verdict: `Real Goblin ✓` or `Not the Goblin — pretend, and note it.` (wiki: a
    non-Goblin claimant must be publicly announced and pretend-marked).
  - Day-tab strip: `Claimed Goblin today: Alice, Dan`.
  - Nomination triggers (day-engine §D `goblin` row = CHOICE) plus two warnings today's
    `nominationWarnings` lacks: nominee **is** a sober living Goblin → "if they claim now (before votes)
    and are executed today, evil wins"; nominee already holds `Claimed` → "executing them ends the game
    if they really are the Goblin".
death/win: `ExecutionRecord.playerId` holds `("goblin","Claimed")` placed **today**, the seat's
  `characterIdAtExecution == "goblin"`, and `abilityImpairedAtExecution == false` →
  `Advisory(goodWins = false, ruleId = "goblin-claim", reason = "{name} claimed to be the Goblin today
  and was executed — evil wins.")`. `outcome == SURVIVED` → raise the advisory **with** the caution
  *"executed but did not die — the wiki does not settle this; the Boomdandy's parallel ruling says the
  execution still counts"* and let the ST decide. Impaired Goblin → a **caution**, not a win. A
  non-Goblin marked `Claimed` → produce nothing plus a quiet ST note. Exile is not an execution → nothing.
  With `mastermindDayActive`, the Goblin advisory takes precedence and only one reveal is offered.
ledger: `goblin:Claimed` stays in `EXPIRES_AT_DUSK` (`GameActions.kt:241` — already correct, and it is
  exactly what implements *"must have claimed today"*). `LedgerEntry(kind = STATEMENT)` per claimant,
  with `genuine = (characterId == "goblin")` so a Cerenovus-mad claimant is distinguishable.
conflict: this file's P0-2 — *"the Day-screen execute path never runs `deathNotes`"* — is not a Goblin
  bug at all; it is the cross-cutting reason **every** execution-triggered rule is skipped on the path
  the ST actually uses at dusk. It is subsumed by day-engine §F ("the four call sites all become
  `GameActions.execute`") and must not be dropped in the merge.
tests:
  - Given a Goblin holding `Claimed` today, when `execute(goblinSeat, DIED)`, then `WinCheck` returns
    `goodWins == false` naming the Goblin.
  - Given a Goblin claimed on day 1, when DAY→NIGHT runs, then `Claimed` is gone and executing them on
    day 2 produces no advisory.
  - Given an Artist marked `Claimed` is executed, then no advisory; given a **poisoned** Goblin, then a
    caution, not a win.
  - Given `execute(goblinSeat, SURVIVED)`, then an advisory with the executed-but-survived caution.
  - Given two players claim on the same day, then both hold a `Claimed` token simultaneously.
open: does an execution that kills nobody still win for the Goblin? (inference from the Boomdandy's
  explicit ruling — surface as an ST choice, do not decide).

---

## harpy — Harpy · exp Minion · P0:0 P1:4

today: the night step is correct and complete; everything after it is lost. The Mad/2nd tokens are
swept at dusk — **before** the night the guide tells you to use them — nothing surfaces the madness by
day, there is no kill tool, and the nomination warning names the Cerenovus.
data:
  - characters.json: `:1806` ok (`reminders: ["Mad","2nd"]`, text verbatim). Keep the labels as printed.
  - night_and_jinxes.json: firstNight 39 / otherNight 27 both correct. No jinx rows — correct, the wiki
    lists none.
  - night_guide.json: `:1358` `other` says the targets "might die **tonight**"; the wiki says
    **tomorrow**. State the rule, then offer the deferral as an ST option. Add the "point at the 2nd"
    show card, that dead players may be chosen, and that the tokens stay until the Harpy re-chooses.
setup: none.
identity: plain.
night.first / night.other (identical):
  gate: `aliveHolder` — dead → `Skip("Dead — no Harpy ability; leave the Mad/2nd tokens where they are")`.
  action: `ChoosePlayers("harpy", "1st (goes mad), then 2nd (the accused)", min = 2, max = 2,
    constraints = [ANY_LIVING_STATE, SELF_ALLOWED], sort = SEAT_ORDER)` — **ordered**, rendered as two
    slots, the 2nd row disabling the seat picked as 1st (1st == 2nd is nonsense even though the rules do
    not forbid self-selection). Default-select last night's pair; the Tips & Tricks recommend repeating.
  effects: `perTarget` in pick order — `PlaceToken("harpy","Mad", target1, exclusive)` and
    `PlaceToken("harpy","2nd", target2, exclusive)`; `RecordChoice()`. The `Mad` token must carry
    `PlacedReminder.targetPlayerId = target2` so the day briefing and the nomination warning can say
    *what* the madness is. If impaired: place anyway, caveat *"the madness is not enforced and nobody
    may be killed for breaking it."*
  deferred: the madness binds **tomorrow**. `Defer(kind = "madness", dueNight = cycle + 1)` →
    `DayBriefing.madness` line: *"{t1} must act mad that {t2} is evil today, or you may kill one or both."*
  info: none computed.
  show: (1) to t1, `THIS CHARACTER SELECTED YOU` + Harpy token (exists); (2) **new card kind needed** —
    a "point at this seat" full-screen seat card naming t2, so the ST can show rather than point across
    a dark room (`ShowCards.kt` has CharacterCard/Message/NumberCard/BluffsCard/SheetCard/AlignmentCard,
    no seat card).
  visibility: only t1 learns anything; t2 learns nothing.
day: Day-tab card + `DayBriefing.Note(DAY_START, ACTION, "harpy")`:
  *"Harpy madness — {t1} must act mad that {t2} is evil"* · `[ Madness satisfied ] [ Madness BROKEN ]`.
  BROKEN opens the kill picker: `[ {t1} dies ] [ {t2} dies ] [ both die ] [ nobody dies ]` +
  `[ Defer to tonight ]` (re-raises on the next Harpy step). Each kill is `KillCause(EVIL_ABILITY,
  "harpy")` routed through `Status.killOutcome` so protections are honoured; the ST chooses the order
  of deaths, which the wiki explicitly allows.
  Nomination-time: rewrite `StatusEffects.kt:162-164` to be **source-aware** and to cover the nominee as
  well as the nominator — `cerenovus` → "mad about the character the Cerenovus chose"; `harpy` → "mad
  that {2nd} is evil (Harpy)"; `pixie` → "mad that they are the character the Pixie saw". Today a Harpy
  `Mad` token produces a message naming the Cerenovus, and it never fires for the nominee.
death: no on-death triggers. If the Harpy dies, leave the tokens but mark the standing madness inert in
  the day briefing.
ledger: **remove** `("harpy","Mad")` and `("harpy","2nd")` from `EXPIRES_AT_DUSK`
  (`GameActions.kt:239-240`) → `Expiry.NEVER`; they move only when the Harpy re-chooses, which
  `placeExclusiveReminder` already does. `LedgerEntry(kind = CHOICE, targetIds = [t1, t2])` each night;
  `LedgerEntry(kind = RULING, sourceId = "harpy", verdict = TRUE|FALSE)` for "was the madness satisfied?"
  (replaces this file's proposed `HarpyChoice(night, madId, secondId, satisfied)` list).
tests:
  - Given `Mad` on Alice and `2nd` on Bob at night 2, when DAY→NIGHT runs, then **both tokens are still
    present**. (Fails today.)
  - Given that pair, when the Harpy chooses Carol then Dan on night 3, then exactly one `Mad` (Carol)
    and one `2nd` (Dan) exist.
  - Given `Mad` on Alice sourced from `"harpy"`, then the nomination note names the Harpy and Bob and
    does **not** say "Cerenovus"; sourced from `"cerenovus"`, then it still names the Cerenovus.
  - Given `Mad` on the **nominee** (not the nominator), then a warning is still produced.
  - Given the ST marks the madness broken and kills both, then two death events with `KillCause(
    EVIL_ABILITY, "harpy")` exist and the ruling entry records `verdict = FALSE`.
open: none.

---

## marionette — Marionette · exp Minion · P0:1 P1:4

today: setup asks which good token the Marionette believes and validates Demon adjacency; night 1 the
Demon is told who they are; every later night the Marionette appears on their believed character's step
with a red caveat but **no false-info chips**, unlike the Drunk in the identical situation. Lil' Monsta /
Summoner / Kazali games are unstartable.
data:
  - characters.json: `:1821` keep `"Is The Marionette"` (capital T) in `remindersGlobal`; fix
    `GameShell.kt:460,466`, which writes `"Is the Marionette"` and compares case-**sensitively** at
    `:459-462`, producing two near-identical tokens when one was already placed from the seat sheet.
  - night_and_jinxes.json: firstNight index 21 correct, no otherNight entry correct. Present: damsel,
    balloonist, poppygrower, snitch, huntsman, lilmonsta, summoner, kazali. **Missing: alchemist,
    magician, mathematician, plaguedoctor** — the Magician one is mechanical.
  - night_guide.json: `:1382` add the Magician and Poppy Grower branches and the explicit "never wake
    for Snitch / Preacher / Minion info" line.
setup: `marionette.token` · SHOWN_TOKEN · "Choose the not-in-play good token the Marionette believes" ·
  candidates = not-in-play TOWNSFOLK/OUTSIDER · blocking. Plus `marionette.seat` · SEATING · **replace**
  the unconditional Demon-adjacency rule (`GameActions.kt:540-545`) with setup-and-identity's
  `marionetteNeighbourOk`: Summoner → neighbour the Summoner; Lil' Monsta → neighbour any Minion; Kazali
  → always true (created night 1); no Demon in play → advisory, not blocking; else → neighbour the
  Demon, over **all** seats so a Traveller in between still breaks adjacency. Also **suggest the seat**
  (list the anchor's two neighbours) rather than requiring a manual reseat. The believed character's own
  setup bracket applies via `virtualSetupCharacters`: Huntsman → Damsel required, Balloonist → an
  Outsider may have been added — advisories, not errors.
identity: registers as **evil** and as a **Minion** (true `characterId`); shown = the believed good
  character. Acting role = `[believed @slot believed, sourceId = "marionette", alwaysFalse = true]` via
  `Identity.derivedGrants` — `GrantMode.REPLACE`, exactly the Drunk; `nightRoleId` (`GameState.kt:39-44`)
  is deleted in favour of `actingRoles`. **`Status.impairment` must return NO_ABILITY for the
  Marionette**, which `StatusEffects.kt:37` does not (it special-cases only `"drunk"`) — so
  `abilityImpairedAtDeath`, the Mathematician and every future impairment rule get it wrong today.
night.first:
  gate: **drop the `!infoSteps` condition at `NightOrder.kt:122`** so a dedicated `marionette` step
    exists in every game size — today it is synthesised only below 7 residents, so in every normal game
    `night_guide.json:1382` (the only place saying "do NOT wake the Marionette", "treat them as drunk",
    "keep the reminder") is unreachable. Poppy Grower alive & unimpaired → `Skip("Poppy Grower — the
    Demon does not learn the Marionette yet")`, re-inserted with `style = FIRST_NIGHT` when they die.
  action: none. `playerIds = demonSeats` (the **Demon** is woken, not the Marionette).
  effects: `PlaceToken("marionette", "Is The Marionette", Ref.<marionette seat>, exclusive)`, NEVER.
  show: `THIS PLAYER IS` + Marionette token, then point at the Marionette.
  visibility: **Magician jinx** — a living Magician means the detail becomes *"show the Demon BOTH of
    their neighbours ({a} and {b}); do not reveal which is the Marionette"* and `playerIds` follows.
    Today `NightOrder.kt:99-102` names the Marionette outright regardless, breaking the jinx.
    MINION_INFO / DEMON_INFO already exclude the Marionette — keep that, and apply the same
    `characterId != "marionette"` filter to **any** step that enumerates Minions (Snitch, Preacher):
    *"not woken due to abilities that would confirm that they are a Minion."*
night.other: no `marionette` step; the Marionette wakes at the believed character's step (correct today),
  where the caveat becomes *"{name} IS the Marionette — treat exactly as the Drunk: their ability does not
  work. Give false information."* The real fix: `InfoCalc.InfoResult` gains `abilityMalfunctions: Boolean`
  and `NightScreen.kt:904-906` gates the false-info chips on that flag instead of string-sniffing for
  "POISONED"/"DRUNK"/"IS the Drunk"/"VORTOX"/"No Dashii", none of which the Marionette caveat matches.
day: none specific. The Marionette will make claims from false info — the generic claim recorder
  (`LedgerEntry(kind = STATEMENT, sourceId = "claim")`) covers it.
death: on `BecomeCharacter` (e.g. an Imp star-pass to the Marionette) `clearOldTokens = true` removes
  `marionette:Is The Marionette`; add *"{name} was the Marionette and is now the Demon — wake them and
  show the new token; they finally learn the truth."* Adjacency is a **setup** constraint only — never
  re-validate it after a Demon change.
ledger: `marionette:Is The Marionette` → `Expiry.NEVER`; `setupChoices["marionette.token"]`.
tests:
  - Given an 8-player game with a Marionette, when the first-night plan is built, then a `marionette`
    step **exists** and MINION_INFO's `playerIds` excludes the Marionette seat. (Fails today.)
  - Given a living Magician, then the step names both of the Demon's neighbours and not the Marionette.
  - Given a Marionette, then `Status.impairment(marionetteSeat)` is non-empty and
    `InfoCalc.compute("empath", marionetteSeat).abilityMalfunctions` is true.
  - Given Marionette + Lil' Monsta adjacent to a Minion and no Demon neighbour, then setup produces
    **no** adjacency issue; given a Traveller between Marionette and Demon normally, then it **does**.
  - Given the setup prompt runs after a token was already placed from the seat sheet, then exactly one
    reminder matching `"Is The Marionette"` case-insensitively exists on the seat.
open: the How-to-Run fetch also returned a paragraph describing a *"swap a good player's token for a
  not-in-play Minion token … thumbs down … now an evil Minion"* flow that contradicts the character's
  core rule and every summary bullet. **Do not implement it**; confirm against the printed almanac.

---

## mezepheles — Mezepheles · exp Minion · P0:0 P1:5

today: night 1 you type the secret word into a throwaway dialog and it is gone. Conversion is four
manual actions across two screens, once-per-game is unenforced, and the step keeps appearing forever.
data:
  - characters.json: `:1835` ok (`reminders: ["Turns Evil","No Ability"]`, text verbatim). Add
    `spentLabel = "No Ability"` (group note 7 — the ability text does not start with "Once per game", so
    today's heuristic never offers a "Mark spent" chip here).
  - night_and_jinxes.json: firstNight 40 / otherNight 28 correct; no jinx rows — correct, wiki lists none.
  - night_guide.json: `:1395` `first` → replace the free-text card with a state-backed word card.
    `other` → **delete** *"Consider waking them alongside or before the Demon so the evil team can learn
    its new member"*: it is not in the How to Run and it leaks the whole evil team to a player who, per
    the rules, learns nothing but their own new alignment. Add the sober-at-night rule and the
    "ability is spent either way" rule.
setup: `mezepheles.word` · GRANT/free-text · "Write the Mezepheles' secret word — pick something nobody
  says by accident" · candidates = 6-8 one-tap suggestions (Rumpelstiltskin, Aubergine, Kerfuffle,
  Marmalade, Zeppelin, Persimmon) · validation (blocking): blank → "Mezepheles: write down the secret
  word". Stored in `GameState.secrets["mezepheles"]` (records-and-memory §C), where the step re-reads it.
identity: plain. The converted player keeps their `characterId` and ability — only `alignmentFlipped`
  changes.
night.first:
  gate: `Fire` while a `mezepheles` seat exists. action: none.
  info: show the word **silently** — the step renders `Secret word: RUMPELSTILTSKIN` inline in large type
    and offers a one-tap `ShowCardTo(SOURCE, Message(secrets["mezepheles"]))`, prefilled, no typing, and
    re-showable on demand (which also serves a resurrected Mezepheles).
night.other:
  gate: `Fire` **only when** some seat holds `("mezepheles","Turns Evil")` **and** the Mezepheles seat
    does not hold `("mezepheles","No Ability")` — i.e. `Gates.notSpent("mezepheles", "No Ability")`
    composed with a token-exists predicate. Otherwise **omit the step entirely** (wiki: *"remove their
    night token from the night sheet"*). Today `NightOrder.build` renders it every night regardless, and
    it must be ticked to satisfy the dawn checklist.
  action: none — the marked player is auto-selected. Two confirmed buttons:
    `[ {name} turns evil ]` → `flipAlignment(target)` + `RemoveToken("mezepheles","Turns Evil", TARGET)`
      + `MarkSpent("mezepheles")` (places `("mezepheles","No Ability")`) + ledger entry.
    `[ Mezepheles is drunk/poisoned — they stay good ]` → **no** flip, same token removal, same
      `MarkSpent`. **Pre-highlight this button** when `Status.impairment(mezephelesSeat)` is non-empty.
  effects: what matters is whether the Mezepheles is sober **at night**, not when the word was spoken —
    *"If the Mezepheles is sober and healthy at night, the good player turns evil even if the Mezepheles
    was drunk or poisoned when the good player spoke the secret word."* And a poisoned-at-night
    Mezepheles **still burns the ability**. Neither rule appears anywhere in the app today.
  show: `ShowCardTo(TARGET, AlignmentCard(evil = true))` — `YOU ARE` + thumbs down, nothing else.
    Explicit line: *"Do NOT show them the other evil players; they learn only that they are evil."*
  visibility: the Mezepheles **does not learn** whether anyone turned.
day: persistent Day-tab card while the Mezepheles is in play and unspent —
  *"Mezepheles — secret word: `{word}`. Someone said it: [seat chips]"*. Tapping a seat rejects evil
  seats ("only the 1st GOOD player converts", overridable), refuses if a `Turns Evil` token exists
  anywhere or the Mezepheles holds `No Ability` ("already used their ability"), else places
  `("mezepheles","Turns Evil")` exclusively and records `LedgerEntry(kind = STATEMENT, sourceId =
  "mezepheles", speakerId = seat)`. Card also carries `[ Change word ]` and shows `SPENT`. The word may
  be said **publicly or privately** on **any** day — which is why this cannot live on the night step.
death: none.
ledger: `secrets["mezepheles"]`; neither `mezepheles:Turns Evil` nor `mezepheles:No Ability` may be
  added to either expiry table (currently correct — keep it). `LedgerEntry(kind = SPENT)` +
  `LedgerEntry(kind = RULING)` recording turned/not-turned, so `GameLogDialog` (which today derives only
  from `deaths` and `nominations`) can show "Alice turned evil on night 3".
conflict: status-model's cause table lists `mezepheles` under `DeathKind.EVIL_ABILITY`. The Mezepheles
  never kills anyone — remove the row.
tests:
  - Given a Mezepheles in play and no stored word, then setup reports "write down the secret word".
  - Given the Mezepheles holds `("mezepheles","No Ability")`, then the night plan contains **no**
    `mezepheles` step (today it does); given no seat is marked `Turns Evil`, likewise none.
  - Given Alice marked `Turns Evil` and a healthy Mezepheles, when "turns evil" resolves, then
    `alignmentFlipped == true`, `characterId` unchanged, no `Turns Evil` token, and exactly one
    `No Ability` on the Mezepheles.
  - Given the same with a **poisoned** Mezepheles and "stays good", then `alignmentFlipped == false`
    **and** the Mezepheles still holds `No Ability` (the ability is spent either way).
  - Given a converted Alice adjacent to an Empath, then `InfoCalc.empath` counts her as evil.
open: may a **dead** good player's saying of the word convert them? The text says "the 1st good player
  to say this word" with no alive restriction — surface it, do not decide it.

---

## organgrinder — Organ Grinder · exp Minion · P0:2 P1:4

today: the nightly nod/shake is two taps in two different screens, the Drunk token never expires, and
the Day tab publicly displays the running tally, the votes-to-beat number, the "about to die" verdict
and the block banner — on the screen the ST reads aloud from.
data:
  - characters.json: `:1850` ok (`reminders: ["About To Die","Drunk"]`, text verbatim).
  - night_and_jinxes.json: firstNight 33 / otherNight 21 both correct. `butler×organgrinder` present at
    `:10` and correct. Add `alchemist×organgrinder` ("If the Alchemist has the Organ Grinder ability,
    the Organ Grinder is in play. If both are sober, both are drunk.").
  - night_guide.json: `:1419` replace "add or remove the Drunk reminder accordingly" with a pointer to
    the two-button resolver; add *"if they ask why, tell them an Organ Grinder is in play"* and the
    deferred ghost-token rule.
setup: none.
identity: plain. Note the inversion: **drunk switches the eyes-closed rule OFF**, so a nodded-yes Organ
  Grinder gives the town a normal public day.
night.first / night.other (identical):
  gate: `aliveHolder`. Dead → `Skip("dead")`.
  action: `YesNo("organgrinder", "Is the Organ Grinder drunk tonight?", yesLabel = "Nods — drunk until
    dusk", noLabel = "Shakes — sober", onYes = [PlaceToken("organgrinder","Drunk", Ref.SOURCE)],
    onNo = [RemoveToken("organgrinder","Drunk", Ref.SOURCE)], plus RecordChoice() on both)`. The chosen
    answer must be **visibly latched** so re-opening the step shows what was picked. Today there is no
    `organgrinder` resolver at all; the only tool is the generic tray, and un-nodding means a separate
    trip to the seat sheet.
  effects: `organgrinder:Drunk` on **self**, `Expiry.DUSK`.
  info / show: none computed; the existing `DRUNK TODAY? NOD YES / SHAKE NO` ask card is right.
  visibility: nothing shown to any other player.
day: `DayRules.secretVoting(state, lookup)` = an alive Organ Grinder with
  `Status.impairment(...).isEmpty()`. When true the whole Day tab switches (day-engine §E):
  - Banner: **"EYES CLOSED — ask everyone to close their eyes, then count hands silently. Announce
    nothing."** with the footnote *"If asked why: an Organ Grinder is in play."*
  - Header drops the "tally to beat"; the running voter count becomes a press-and-hold **Peek**; the
    verdict line becomes a neutral **"Tally recorded"**; the on-block banner becomes a collapsed
    **"Block — tap to reveal"**; per-nomination rows read `— votes hidden —`.
  - **Close nominations** then reveals the block and offers Execute / No execution — matching *"When
    nominations are closed, declare that the player marked ABOUT TO DIE is executed."*
  - **Butler jinx** (data present, never enforced): while secret voting is on, a Butler hand counts only
    if their `Master` also voted. Inline `! Butler's vote does not count — their master has not voted
    (Organ Grinder jinx)` and **exclude from the tally** with a one-tap override. (Outside secret
    voting the standard rule applies: tally the illegal Butler vote anyway, hint only.)
  - **Ghost votes**: keep `ghostVoteUsed` for the one-vote-per-game rule but defer the *visible* removal
    to dusk (the token vanishing otherwise leaks who voted), and allow re-tapping a spent dead voter
    labelled "hand raised, not counted" so the ST can record what happened without changing the count.
  - Day briefing (DAY_START): either *"SECRET VOTING today — the Organ Grinder is sober…"* or
    *"Normal voting today — the Organ Grinder chose to be drunk."*
  - `organgrinder:About To Die` — the block is derived by `GameActions.aboutToDie`, which is fine, but
    the derived block must render **privately**; a physical-grimoire ST also wants a cue to place the token.
death: none.
ledger: **add `("organgrinder","Drunk")` to `EXPIRES_AT_DUSK`** (`GameActions.kt:231-242`) — this is P0-1
  and the single highest-value one-line fix in the group: today the token is permanent, so from night 2
  on `isImpaired` reports the Organ Grinder drunk forever and the ST runs eyes-open voting on a day when
  they actually shook their head. day-engine also adds `("organgrinder","About To Die")` to
  `EXPIRES_AT_DAWN`. `LedgerEntry(kind = CHOICE)` per night records the nod/shake.
tests:
  - Given `("organgrinder","Drunk")` placed on night 1, when NIGHT→DAY→NIGHT runs, then the token is
    gone. (Fails today.)
  - Given an alive unimpaired Organ Grinder, then `DayRules.secretVoting` is true; given they hold their
    own `Drunk` token / are dead / are poisoned by a Poisoner, then false in all three cases.
  - Given secret voting and a Butler whose `Master` is Alice, when the tally includes the Butler but not
    Alice, then the counted votes exclude the Butler; when both, then it counts.
  - Given a sober Organ Grinder at day start, then the briefing contains "EYES CLOSED"; given a
    self-drunk one, then "vote normally".
open: whether the deferred ghost-token removal grants a second vote (read here as a secrecy measure
  only, hence P1 not P0).

---

## psychopath — Psychopath · exp Minion · P0:3 P1:4

today: nothing. The day kill is a manual "Other death"; executing the Psychopath kills them outright
with no roshambo; the day keeps accepting nominations afterwards. Zero engine references, no guide
entry, no reminder token.
data:
  - characters.json: `:1865` add `"reminders": ["Used today"]` — an app-side bookkeeping token, not an
    official one; flag it as such in the picker, or hold it as engine state instead. Text verbatim.
  - night_and_jinxes.json: correctly absent from both night lists. Add `psychopath×lilmonsta` ("If the
    Psychopath is babysitting Lil' Monsta, they die when executed.") — mechanical: it overrides the
    roshambo clause entirely.
  - night_guide.json: **no `psychopath` entry.** Add one carrying the How-to-Run (once per day, before
    nominations, roshambo on execution, day ends), rendered on the **Day** screen.
setup: none.
identity: plain.
night.first / night.other: none — the Psychopath never wakes.
day: two distinct mechanics, both day-phase.
  **(a) the public kill** —
  - when: DAY, `cycle >= 1`, and **before the first non-exile nomination of the day**
    (`state.nominations.none { it.day == cycle && !it.isExile }`). Once one is recorded the window is
    closed and the briefing must say so.
  - eligibility: holder alive AND `Status.impairment(holder).isEmpty()` AND no `("psychopath","Used
    today")` token.
  - targets: exactly 1, any alive player, Travellers and self allowed, alive-first sort. **Disable the
    Vizier** inline: *"The Vizier cannot die during the day."*
  - effects: route through `Status.killOutcome(target, KillCause(EVIL_ABILITY, "psychopath"))` and render
    its result as buttons, exactly as the seat sheet's protection dialog does — the wiki's Sailor example
    requires it. This is an ordinary death, **not** an execution: the Devil's Advocate does not apply,
    and Monk/Soldier do not apply because it is not a Demon kill. On confirm, kill +
    `PlaceToken("psychopath","Used today", Ref.SOURCE)`.
  - visibility: the choice is **public** — offer the line *"{Psychopath} publicly chooses {target}: they
    die."* Death triggers (Moonchild, Sweetheart, Godfather's "an Outsider died today", Barber, Zombuul's
    first death) fire through the normal `onDeath` table.
  **(b) roshambo on execution** — hook `GameActions.execute`. When the executee is a living Psychopath
  with their ability:
  1. **Do not kill.** Open the roshambo dialog: *"{Psychopath} was executed — roshambo against
     {nominator}"*, or *"against you (the Storyteller)"* when `nominatorId == nomineeId`. Buttons:
     `Psychopath lost — they die` / `Draw — they live` / `Psychopath won — they live`. Footnote:
     *"Either way the day ends now — this was the day's one execution."*
  2. lost → `execute(playerId, DIED, via = PSYCHOPATH)`; draw/win → `execute(playerId, SURVIVED,
     preventedBy = "psychopath", via = PSYCHOPATH)`.
  3. **Both branches** consume the day's execution: `DayRules.executionSpent(state)` becomes true and
     `closeNominations(...)` sets `nominationsClosedOnDay = cycle`. The dusk guard must stop complaining
     about anyone still on the block, and a Mastermind day / a pending Vizier execution / another
     nomination are all cancelled. **Lil' Monsta jinx**: a babysitting Psychopath skips the dialog and
     dies normally.
  Nomination warning: nominee is a living unimpaired Psychopath → *"If executed, {name} plays roshambo
  with {nominator}; they die only if they lose — and the day ends either way."* Self-nomination →
  *"…with YOU."* (day-engine §D has the `psychopath` WARN row.)
  Day-start briefing: *"Psychopath ({name}) may publicly kill someone — ask BEFORE opening nominations."*
  / *"Psychopath's window has closed for today."* / *"Psychopath has no ability today (dead/poisoned)."*
death: `DeathCause`/`KillCause` gains a Psychopath cause (day-engine §J lists `PSYCHOPATH` among the new
  `DeathCause` values; serialised enums must be **appended**, never reordered) so the log stops saying
  "died (storyteller)" and the Undertaker/Cannibal/Mathematician can reason about it.
ledger: `("psychopath","Used today")` → `EXPIRES_AT_DUSK`; `LedgerEntry(kind = STATEMENT, sourceId =
  "psychopath")` for the public kill (recording whether the target actually died — protection may have
  saved them) and `LedgerEntry(kind = RULING)` for the roshambo result.
conflict: this file proposed `executionSpentDay: Int?`; day-engine derives `executionSpent(state)` from
  `executions` and stores only `nominationsClosedOnDay`. Use day-engine's.
tests:
  - Given a living unimpaired Psychopath on the block resolved as "draw", then they are still alive, no
    death event exists, and `DayRules.executionSpent(state)` is true.
  - Given the same resolved as "lost", then an execution death exists — and in **both** cases a further
    non-exile nomination on the same cycle is refused.
  - Given the Psychopath holds a `No ability` token or the Lil' Monsta babysitting token, when executed,
    then they die with no roshambo prompt.
  - Given a sober Sailor as the day-kill target, then `killOutcome` returns `Blocked` and the default is
    "no death"; given the Vizier as the target, then the chip is not selectable.
  - Given one non-exile nomination recorded today, then the day tool is disabled ("nominations have
    opened"); given the ability was used, then it is disabled until the next dawn.
open: none.

---

## summoner — Summoner · exp Minion · P0:3 P1:6

today: the bag correctly has no Demon, then night 1 tells the ST to wake a Demon that does not exist; the
`Night 1/2/3` counter is 100% manual; the night-3 conversion is four manual operations across two screens; and
a Summoner executed on day 1 produces no win advisory at all.
data:
  - characters.json: `:1877` ok (`setup: true`, `reminders: ["Night 1","Night 2","Night 3"]`); add `spentLabel`.
  - night_and_jinxes.json: firstNight 17 / otherNight 30 correct. **Only 4 of 14 jinxes present** (marionette,
    alchemist, poisoner, courtier), and `:114`'s Marionette text is stale → "If there would be a Marionette in
    play, they enter play after the Demon & must start as their neighbor." Add clockmaker, engineer, hatter,
    kazali, legion, lordoftyphon, pithag, poppygrower, pukka, zombuul — four **mechanical**: poppygrower
    ("chooses which Demon but not which player"), pukka ("may summon on the 2nd night instead of the 3rd" —
    moves the night the step fires), legion ("all evil become Legion"), lordoftyphon (reseat + a new Minion).
  - night_guide.json: `:1443` rewrite `other` — split nights 2 and 3; **drop** the "no Demon is created… evil
    loses without a Demon" default, which contradicts the app's own Poisoner/Courtier jinx rows; add "The new
    Demon does not learn which players are Minions"; add the `YOU ARE` + evil card as a second `shows` entry.
setup: `summoner.bluffs` · BLUFFS(3) · "Choose the Summoner's 3 bluffs" · not-in-play good · blocking (advisory
  under the Alchemist-Summoner jinx, which removes the bluffs). Plus a positive bag check — `summoner` in play ⇒
  **exactly 0 Demons** (`BagShape(demons = 0..0, townsfolk = base + 1)`; `[No Demon]` works today only as a side
  effect of the distribution maths) — and **skip** the Marionette adjacency rule when the bag has no Demon (see
  `marionette.seat`), emitting *"Marionette enters play on night 3, next to the new Demon (jinx)."*
identity: registers as itself, but **as the Demon to the Clockmaker** (jinx). The summoned player gets
  `BecomeCharacter(target, demonId, evil = true, clearOldTokens = true, reRunFirstNight = false)`; night-engine gives such a Demon `OTHER_NIGHT` style and **no** Minion info — exactly the wiki's rule.
night.first:
  gate: `Fire` while the holder is alive; auto-place `PlaceToken("summoner","Night 1", Ref.SOURCE)`. action:
    none. info: show the 3 bluffs — **lift the bluff-card gate** at `NightScreen.kt:783` from `step.id ==
    DEMON_INFO` to any step whose guide declares `kind: "bluffs"`, and relabel the feature "Summoner bluffs"
    while a Summoner is in play and no Demon is (`GameShell.kt:219`).
  visibility: **MINION_INFO** with no Demon → *"They see each other. There is no Demon yet — do not point one
    out."*; **DEMON_INFO** with no Demon → `Skip("Summoner — no Demon yet")`, the bluffs going to the Summoner.
    Today both are emitted for 7+ seats, producing "Wake the Demon ." and an empty Minion parenthesis.
night.other:
  gate: nights 1-2 `Skip("Night N of 3 — the Summoner does not act tonight")`; `Fire` when the holder carries
    `summoner:Night 3` (`Gates.nightIs(3)` ∘ `notSpent`), the counter advanced by `TokenRule(countdownNext =
    "Night 1"→"Night 2"→"Night 3", countdownAt = DUSK)` (group note 6). **Pukka jinx**: also offer the resolver
    on night 2 as `Summon a Pukka early (jinx)`. An **impaired** Summoner is `StepGate.Reduced(allow =
    ["choose-character"])`, never `Skip` — per the app's own Poisoner/Courtier rows the Summoner names the Demon
    and the **Storyteller** picks the player.
  action: `ChoosePlayerAndCharacter("summoner", "Who becomes what?", playerConstraints = [ANY_LIVING_STATE,
    SELF_ALLOWED], pool = CharacterPool.DEMON)` — any player alive or dead, **including the Summoner
    themselves**; Demon pool restricted to the script. A dead target shows the Zombuul jinx ("already died once").
  effects: one atomic undoable action — `BecomeCharacter(...)` + `ShowCardTo(TARGET, "YOU ARE")` +
    `MarkSpent("summoner")` (replacing `Night 3`). Jinx follow-ups as checkboxes: **Legion** (one tap converts
    every evil seat), **Lord of Typhon** (reseat + Minion picker for the other neighbour), **Marionette** (pick
    the neighbouring seat → `characterId = marionette` + a shown good token), **Hatter/Kazali/Pit-Hag** (a second
    living Demon now exists → banner *"Deaths tonight are arbitrary (jinx)."*).
  deferred: **the new Demon acts the same night** — Demons sit at otherNight 36-54, after the Summoner at 30, so
    the step appears naturally, but the plan must re-scroll to it and badge *"{name} is now the {Demon}."*
  show: `YOU ARE` + the chosen Demon token (existing `token: "pick"` machinery), then `YOU ARE` + thumbs down.
  visibility: *"Do NOT show the new Demon who the Minions are, nor the Minions their new Demon."*
day: days 1-2 briefing *"No Demon is in play yet — nobody can die to a Demon tonight."* Dawn after night 3:
  *"{name} is now the {Demon}"*, plus for a Leviathan the public *"the Leviathan is in play; day 3 of 5."*
death/win: `WinCheck.check` gains, **before** the "every Demon is dead" branch: Summoner in play,
  `demons.isEmpty()`, Summoner dead or gone → `Advisory(goodWins = true, ruleId = "summoner-nodemon")`, with
  cautions for the Courtier/Preacher/Engineer jinx ("the Storyteller has the Summoner ability instead") and for
  an Alchemist-Summoner or a Pit-Hag who could still make a Demon. Today `WinCheck.kt:70-86` requires
  `demons.isNotEmpty()` to fire at all, so a day-1 Summoner execution produces **no advisory whatsoever**;
  conversely "all Demons dead" must **not** fire before the Summoner has created one (day-engine §G).
ledger: `summoner:Night 1/2/3` countdown at DUSK; spent marker never expires; `bluffSets["summoner"]`;
  `LedgerEntry(kind = CHOICE, targetIds = [target], characterIds = [demon])`.
tests:
  - Given an 8-seat Summoner game on night 1, then the plan has no `DEMON_INFO` step and MINION_INFO's detail
    does not say "point out the Demon"; and a Summoner + Marionette bag with no Demon raises no adjacency issue.
  - Given no Demon and a dead Summoner, then `WinCheck.check` returns `goodWins = true`; Summoner alive → null.
  - Given `Night 1` at night 1, then `Night 2` only at night 2, `Night 3` only at night 3.
  - Given the summon resolves as (Snitch seat, `lleech`), then that seat is an evil `lleech` with
    `shownCharacterId == null`, the Summoner is spent, and the rebuilt plan has a `lleech` step **after** it.
open: the wiki's Courtier/Preacher jinxes say *"the Storyteller has the Summoner ability"* (ST picks both), the
  app's Poisoner row says the Summoner picks the Demon and the ST picks the player, and the wiki lists no
  Poisoner jinx at all. Surface **both**; either way an impaired Summoner must mean *something happens*.

---

## vizier — Vizier · exp Minion · P0:3 P1:4

today: one night-1 announcement step whose card is not pre-filled, and after that nothing. The app kills
the Vizier when executed, the "execute immediately" power does not exist, and every day death lands.
Zero `vizier` references in engine or app code.
data:
  - characters.json: `:1893` add `"reminders": ["No ability"]` (Courtier/Preacher), or rely on the
    generic marker. Text verbatim.
  - night_and_jinxes.json: firstNight index 74, **after** `DAWN` — correct, matching *"when the first
    night has ended"*; absent from otherNight — correct. **All 9 jinxes missing**; add alsaahir,
    courtier, fearmonger, investigator, lilmonsta, magician, politician, preacher, zealot. Four are
    mechanical: alsaahir/investigator (**do not** declare the Vizier), fearmonger (the Vizier wakes with
    the Fearmonger — a night-order change — and cannot immediately execute their target), magician
    (immune to the immediate execution), lilmonsta (a babysitting Vizier **dies** when executed).
  - night_guide.json: `:1467` substitute the holder's name into the show card; add a `day` section with
    the two standing rules for the day briefing; add the Alsaahir/Investigator suppression.
setup: none.
identity: `EffectKind.DAY_IMMUNE` on self as a `StandingRule` (status-model §1 already lists `vizier`).
  **Critical:** the Courtier/Preacher jinx says *"If the Vizier loses their ability, they learn this, and
  cannot die during the day"* — so the immunity must **not** be gated on `Status.hasAbility`; set
  `endsWithSource = false` and do not "fix" it by tying it to the ability.
night.first:
  gate: `Fire` on the first night only, at its post-DAWN slot; `Skip("Alsaahir/Investigator jinx — do NOT
    declare the Vizier")` when either is in play. action: none — the Vizier does not wake.
  show: `THE VIZIER IS {name}` — pre-filled from `step.playerIds` (today the ST types the name into
    `GuideShowDialog` even though the holder is already known). Offer a second table-facing card.
  visibility: **public, to everybody**, including the good team.
night.other: none — but the two day-long rules must be restated every day (see `day:`), which is exactly
  the failure the user reported for the Devil's Advocate.
day: `DayRules.vizier(state, lookup)` = the living Vizier holder. Two consequences.
  **(a) day immunity** — `Status.killOutcome` step 3 (`DAY_IMMUNE and !atNight → Blocked`) covers
  execution, the Psychopath's public kill, a Witch curse on a nomination, a Golem nomination, a Virgin's
  collateral execution, and the ST's "Other death"; overridden only by the **Lil' Monsta** jinx. The Day
  screen's Execute button for a living Vizier nominee becomes **"Execute (Vizier survives)"** →
  `execute(vizierId, SURVIVED, preventedBy = "vizier")`, which records the execution, kills nobody, and
  closes the day. Nomination warning: *"The Vizier cannot die during the day — an execution here uses up
  the day's execution but kills nobody."*
  **(b) execute immediately** — attached to the nomination record flow, after a tally is recorded
  (day-engine §D `vizier` row = VOTE_MODIFIER):
  - eligibility: a living Vizier with their ability **and** `nomination.voterIds` contains ≥1 voter who
    **registers** as good. Explicitly **not** gated on `executionThreshold` — the wiki's Barber example
    executes on 3 votes in a game whose tally to beat was 7 — and it **overrides whoever is on the block**.
  - registration: a Politician or Zealot among the voters *"might register as evil to the Vizier"*, and a
    Spy/Recluse raises the mirror question. Surface per-voter toggles and a `LedgerEntry(kind = RULING,
    sourceId = "misregister")` so the ruling stays consistent; never compute it from raw alignment when
    either is on the script.
  - suppressed, with the reason shown, when no good player voted, the nominee is the **Magician** (jinx),
    or the nominee holds `("fearmonger","Fear")` (jinx).
  - effect: `execute(nomineeId, DIED, via = ExecutionVia.VIZIER)` through `killOutcome` (so a Sailor or
    the Vizier themself is still protected), clear the block, `closeNominations(...)` — *"No more
    nominations, votes, or executions occur today"* and *"This counts as the 1 execution allowed each day."*
  Day-start briefing, **every day**: *"Vizier ({name}) is public. They cannot die during the day."* ·
  *"After any tally with at least one good voter, the Vizier may declare the nominee executed — that ends
  the day."* · when the ability is lost: *"{name} has lost the Vizier ability and knows it — but still
  cannot die during the day."*
death: the Vizier dies normally **at night** (wiki example 3: executed, survives, Demon kills them that night).
ledger: `ExecutionRecord(via = VIZIER, tally, threshold)` so the log distinguishes a Vizier execution
  from a plain one; `LedgerEntry(kind = ANNOUNCE, sourceId = "vizier")` for the night-1 declaration.
tests:
  - Given a living Vizier on the block on day 2, when the execution resolves, then they are still alive,
    no death event exists, and `DayRules.executionSpent(state)` is true.
  - Given the Vizier targeted by a Psychopath day kill or a Witch-curse nomination death, then
    `killOutcome` returns `Blocked`; given the Demon attacks them at night, then they die.
  - Given 8 alive (threshold 5), a 7-vote nominee on the block, and a new 3-vote nomination with one
    good voter, when the Vizier executes, then the 3-vote nominee dies and the 7-vote player does not —
    and a further non-exile nomination that cycle is refused.
  - Given all voters register evil, then the Vizier control is unavailable; given the nominee is the
    Magician or holds `fearmonger:Fear`, then it is suppressed with the jinx reason.
  - Given an Alsaahir (or Investigator) in play, then the first-night Vizier step is skipped.
open: the wiki page says nothing about a drunk/poisoned Vizier, about Travellers, or about exile — the
  immunity's independence from ability loss is the Courtier/Preacher jinx, not an inference.

---

## widow — Widow · exp Minion · P0:0 P1:7

today: the tokens place correctly and the poison flows into `InfoCalc` — but it never ends when the
Widow dies, never pauses when the Widow is impaired, there is no setup prompt for KNOW, no way to show a
redacted grimoire, and a mid-game Widow gets no step at all.
data:
  - characters.json: `:1905` ok (`reminders: ["Poisoned","Know"]`); the multi-Widow case needs
    `maxCopies = 2` on `Poisoned` (see `open:`), and "grimoire seen" should be an app-internal marker.
  - night_and_jinxes.json: firstNight index 28 correct; **absent from otherNight — add `widow` there,
    immediately after `poisoner`**, so a Pit-Hag/Alchemist/Summoner-created Widow gets a step. Present:
    damsel `:39`, heretic `:69`, magician `:139`, poppygrower `:149`. Fix the **stale** Poppy Grower text
    (`:149` "…until the Poppy Grower dies" → "If the Poppy Grower **has their ability**, the Widow does
    not see the Grimoire" — the difference matters when they are drunk or poisoned). Add
    `alchemist×widow` ("An Alchemist-Widow has no Widow ability & a Widow is in play").
  - night_guide.json: `:1480` add an `other` section (same run-book, for a mid-game Widow); replace
    "cover any reminders you must hide" with a pointer to the redacted grimoire view; add *"not which
    player is the Widow, and not which player is poisoned"*.
setup: `widow.know` · REMINDER/PICK_PLAYER · "Mark the good player who knows a Widow is in play" ·
  candidates = `!isEvil(lookup)` seats (the Fortune Teller dialog at `GameShell.kt:360` already filters
  this way) · validation (blocking): token absent, or token on an evil seat. Plus the **Damsel jinx**
  applied at setup — a Damsel alongside a Widow gets a permanent `PlacedReminder("widow","Poisoned
  (Damsel jinx)")` that is **never** removed, because the jinx says *"is (or **has been**) in play"*, so
  it must **not** be folded into the derived rule below.
identity: plain.
night.first / night.other (same step, both lists):
  gate: `Fire` when the holder is alive **and** has not yet acted (no `widow:Grimoire seen` marker) —
    this makes *"on **your** 1st night"* work for a mid-game Widow without changing night-1 behaviour.
    Poppy Grower alive **and with their ability** → suppress the grimoire view with *"Poppy Grower jinx:
    the Widow does not see the Grimoire"*, and re-offer the step on the first night after they lose it.
  action: `ChoosePlayers("widow", "Who do they point at?", min = 1, max = 1, constraints =
    [ANY_LIVING_STATE, SELF_ALLOWED], sort = TOWNSFOLK_FIRST)` — the Widow points at a **character token
    in the grimoire**, so dead seats and the Widow themself are legal.
  effects: `PlaceToken("widow","Poisoned", Ref.TARGET, exclusive)` + `PlaceToken("widow","Grimoire seen",
    Ref.SOURCE)`. The token is the **record**; the effect is a `StandingRule` — `Effect(kind = POISONED,
    sourceCharacterId = "widow", sourcePlayerId = widowSeat, until = SOURCE_LOSES_ABILITY,
    endsWithSource = true)`. status-model §1 already lists the `widow` rule as *"keeps the stored POISONED
    alive only while the Widow has an ability"*, and the recursive `abilityWorks` of §2 then gives both
    required behaviours free: the poison **ends** when the Widow dies and **pauses** while the Widow is
    drunk or poisoned (the wiki's Innkeeper example), resuming when they recover.
  info: none computed for the Widow. The victim's own steps must stop showing a "give false info" caveat
    the moment the Widow dies — that is a wrong-information bug, not just bookkeeping.
  show: (1) the **redacted grimoire view** — a new shared full-screen mode (the **Spy** needs the
    identical feature): the seat circle with character and reminder tokens and **nothing else** — no
    notes, no shown-identity rows, no app chrome, no editing. Per-seat redaction toggles pre-applied
    from rules: **Magician jinx** auto-hides the Demon's and the Magician's character tokens; every
    `widow:Know` token and the acting Widow's own marker are auto-hidden; manual hide for anything else;
    an explicit "Done — hand the phone back". Today the ST hands over the unredacted Grimoire tab,
    exposing the app's own bookkeeping. (2) to the KNOW player, `THIS CHARACTER IS IN PLAY` +
    `token: "self"` (correct today) plus *"They learn a Widow is in play — NOT who, and NOT who is
    poisoned."*
  visibility: the KNOW reveal is its own **separately checkable sub-step** naming the holder, so it
    cannot be forgotten; if the KNOW player is dead or was never marked, say so and offer the picker inline.
day: none.
death: Widow dies → `Announce(DAWN_PRIVATE, "{victim} is no longer poisoned — the Widow died")`; the
  `Poisoned (Damsel jinx)` effect survives regardless.
ledger: `widow:Poisoned` and `widow:Know` in **neither** expiry table (already correct — keep it);
  `widow:Grimoire seen` never expires. `LedgerEntry(kind = CHOICE, sourceId = "widow")`.
tests:
  - Given Alice holds `widow:Poisoned` and a living healthy Widow, then `Status.isImpaired(Alice)` is
    true; when the Widow is killed, then false. (Fails today.)
  - Given the same plus an `innkeeper:Drunk` token on the Widow, then Alice is not impaired; when that
    token is removed, then she is again. (The wiki's Innkeeper example.)
  - Given a Damsel holding `widow:Poisoned (Damsel jinx)`, when the Widow dies, then she is still impaired.
  - Given a seat whose `characterId` becomes `widow` on cycle 3 with no `Grimoire seen` marker, then the
    other-night plan contains a `widow` step; with the marker, then it does not.
  - Given a Sailor holding `widow:Poisoned`, when `killOutcome` runs for them, then it does **not** block
    the death — `StatusEffects.kt:73` returns "The Sailor can't die" regardless of impairment today,
    a general bug the Widow exposes.
open: two Widows must place **two** `Poisoned` tokens, but `characters.json` declares one label so
  `placeExclusiveReminder` moves the first instead of adding a second — `maxCopies` must come from the
  `TokenRule`, per night-engine §4.

---

## wizard — Wizard · exp Minion · P0:0 P1:4

today: a night step reading "Do whatever needs to be done…", a working "Mark spent" chip, and two
reminder tokens both literally labelled `?`. The wish, the price, the clue and whether it was announced
all live in the ST's head or in the single global notes blob.
data:
  - characters.json: `:1920` keep the two `?` tokens but the reminder system must carry a **free-text
    label** — `PlacedReminder` gains `note: String = ""` (records-and-memory §A already adds exactly
    this field, plus `characterId`, `targetPlayerId` and `placedCycle`), rendered under the token and
    searchable. The Wizard is the driving case; every improvised ST ruling wants it.
  - night_and_jinxes.json: firstNight 30 / otherNight 16 both correct. **No jinxes** — correct, the wiki
    page has none.
  - night_guide.json: `:1493` rewrite both sections around the three actions below; add the wiki's five
    worked example wishes (see the Grimoire · all good players drunk · become a Demon · win the game ·
    declined as too awkward) as one-tap starting points; add *"the effects of your wish stay in play
    even after you are dead"*; add the decline-and-re-wish rule; add a second show entry for the
    declined card.
setup: none.
identity: plain.
night.first / night.other (identical):
  gate: `Gates.notSpent("wizard", spentLabel)` — `Fire` while no `wizard:Wish granted` marker exists and
    the holder is alive. Once a wish has been **granted** the row collapses to a single line *"Wizard —
    wish already granted ({wish})"*. Today `NightOrder.build` has no spent check at all, so the step
    renders every night forever; only the tray chip reacts to the marker.
    Note the existing `oncePerGame = ability.startsWith("Once per game")` heuristic
    (`NightScreen.kt:204`) happens to be right here, but it cannot distinguish **declined** (still
    available) from **granted** (spent) — which is the one distinction this character has.
  action: three buttons — `[ No wish tonight ]` (just ticks the step) · `[ Wish declined ]` (records the
    wish with `verdict = FALSE`; the step stays available on later nights — *"prompt the Wizard to wish
    again"*) · `[ Wish GRANTED… ]` (opens the Wish sheet).
  effects: the Wish sheet captures `What did they wish for?` (multiline) · `Price (optional)` ·
    `Clue for the good team (optional)` + `☐ Announce publicly at the next day start`, with the five
    example wishes as chips. On save: `MarkSpent("wizard")` placing `("wizard","Wish granted")`, store
    the three strings in `secrets["wizard.wish" / "wizard.price" / "wizard.clue"]`, and show
    `YOUR WISH IS GRANTED.`
  deferred: when `Announce publicly` was ticked, `Announce(BriefingSlot.DAY_START, "Announce: the Wizard
    has made a wish. Clue — \"{clue}\"")` with a one-tap `ShowCard.Message` and a "said it" tick that
    marks the entry `delivered`. Ongoing effects are tracked as `?` tokens carrying the ST's own `note`
    text and listed in the day briefing as *"Wish in effect: {wish}"* for the rest of the game.
  info: none computed.
  show: `YOUR WISH IS GRANTED.` / `YOUR WISH IS MY COMMAND.` / `YOUR WISH IS DECLINED — WISH AGAIN?`
  visibility: the wish is secret; the clue is public. Offer a `Show to the evil team` card (Tips:
    *"Tell the rest of your evil team what you wished for!"*).
day: **a wish may be made at any time, verbally or by note** — so the Day screen needs a
  `Wizard wished (note passed)` action opening the same Wish sheet. A note passed at 11am must not have
  to wait for the night step, which is precisely when the ST is busiest.
death: **the effects of a granted wish survive the Wizard's death.** The night sheet must therefore not
  show the generic *"All holders are dead — usually skip"* line (`NightScreen.kt:751-757`) for a dead
  Wizard whose wish is in effect; it must read *"Dead — but the granted wish stays in play."* Any `?`
  tokens on other seats carry no owner-alive condition.
ledger: `secrets["wizard.wish"/"wizard.price"/"wizard.clue"]`; `LedgerEntry(kind = RULING, sourceId =
  "wizard", text = wish, verdict = TRUE|FALSE)` per wish (granted/declined), `LedgerEntry(kind =
  ANNOUNCE, delivered)` for the clue, `LedgerEntry(kind = SPENT)` on grant. `wizard:Wish granted` and the
  `?` tokens → `Expiry.NEVER`.
conflict: this file proposed `GameState.rulings: List<Ruling>(sourceId, cycle, body, publicClue,
  announced)`; that is `LedgerEntry(kind = RULING) + secrets` and must not be added as a sixth list.
tests:
  - Given the Wizard holds `wizard:Wish granted`, when the other-night plan is built, then no interactive
    `wizard` step is returned (or it is returned `Skip`ped). (Fails today.)
  - Given a **declined** wish recorded on night 1, then night 2's plan still offers the wish actions.
  - Given a granted wish and then the Wizard is killed, then the wish record is still in effect and the
    day briefing still lists it.
  - Given `phase == DAY` and the day-side "Wizard wished" action saves a granted wish, then the marker
    and the ledger entry exist and night 2's step is suppressed.
  - Given `PlacedReminder("wizard", "?", note = "All good players are drunk")`, then the note survives a
    JSON round-trip and both `?` tokens can coexist on two seats.
open: none.

---

## xaan — Xaan · exp Minion · P0:2 P1:4

today: X is never asked for and never stored; the counter tokens are manual; on night X **nothing
happens** — every Townsfolk info step shows the true answer in gold, with no caveat and no false-info
chips. The app is not merely leaving work to the ST, it is actively telling them the wrong thing.
data:
  - characters.json: `:1935` ok (`setup: true`, `reminders: ["Night 1","Night 2","Night 3","X"]`).
    status-model §8 also asks for a `"Poisoned"` reminder; unnecessary if the poison is derived — add it
    only if the project prefers an explicit token per Townsfolk (then `("xaan","Poisoned")` also goes to
    `EXPIRES_AT_DUSK`).
  - night_and_jinxes.json: firstNight 26 / otherNight 12 both correct. **No jinxes** — correct.
  - night_guide.json: `:1517` rewrite both sections around the stored X: X is fixed at setup and
    unaffected by later Outsider changes; the Xaan must be **alive** to poison; the poison runs through
    the following **day** until dusk; add the X = 0 case.
setup: `xaan.X` · NUMBER/PICK_NUMBER · "Choose X — the Outsider count, and the night the Xaan poisons" ·
  candidates = 0…5, defaulting to the base distribution's Outsider count · validation (blocking): unset →
  "Xaan: choose X"; `X != bag outsider count` → mismatch issue. Stored as
  `setupChoices["xaan.X"]`; `BagShape(outsiders = X..X)` once chosen, free before. Setup note: *"The
  Xaan's Outsider count overrides the Baron and any other setup modifier."* Keep `Setup.modifierFor`'s
  current relaxation (`Setup.kt:143-171`) — it already lets any Outsider count validate, which is what
  makes a Baron + Xaan bag legal — and use the stored X to make `adjustedDistribution` right.
identity: plain.
night.first / night.other:
  gate: `Gates.nightIs(X)` — nights 1..X-1 are `Skip("Night {n} of X={X} — the Xaan does nothing
    tonight")`. On night X, `Fire` only when the Xaan is **alive** (*"The Xaan needs to be alive in order
    to poison"*); dead → `Skip("The Xaan is dead — no poison tonight")`. Impaired Xaan → `Fire` with
    *"! The Xaan is drunk/poisoned — by the usual rules their ability does not work tonight. Your call."*
    (the wiki mentions only "alive", so this is an ST decision, not a silent one). After night X:
    *"The Xaan has no further effect this game."*
  action: none — the Xaan does not wake at all; the effect is automatic and global.
  effects: `PlaceToken("xaan","X", Ref.SOURCE)` as the **record**, plus a `StandingRule` (status-model §1
    already lists `xaan`): while the `X` token is in the grimoire and the Xaan is alive, emit
    `Effect(kind = POISONED, targetId = <every Townsfolk>, sourceCharacterId = "xaan", until = DUSK)`.
    **Team is by the true character**, so the Drunk (an Outsider) is *not* poisoned — which is exactly why
    the wiki's example has the Acrobat, who *is* poisoned, get nothing when they choose the Drunk. The
    Marionette (Minion) and the Lunatic are excluded for the same reason, as are Travellers. A Townsfolk
    created mid-night (Pit-Hag, Summoner conversion) is picked up automatically because the rule is derived.
  deferred: dawn briefing *"Night X — every Townsfolk was poisoned. All Townsfolk information tonight was
    FALSE"* plus the seat list; day-start briefing *"Xaan poison lasts until DUSK — every Townsfolk day
    ability malfunctions today too."*
  info: none computed for the Xaan. Every **other** Townsfolk info step gains the impairment caveat and
    the "False info to show instead" chips automatically once the effect exists — the whole downstream
    chain already works, exactly as it does for the No Dashii. Vortox + Xaan stack harmlessly.
  show: none. Nothing is shown to any player.
day: none. Counter advanced by `TokenRule(sourceId = "xaan", countdownNext = "Night 1"→"Night 2"→
  "Night 3", countdownAt = DUSK)` (shared with the Summoner, group note 6).
death: none.
ledger: **add `("xaan","X")` to `EXPIRES_AT_DUSK`** — because the poison is derived from the token,
  removing the token at dusk removes the poison from every Townsfolk in one step, with no sweeping.
  `setupChoices["xaan.X"]` is permanent and must never be recomputed from the live Outsider count.
tests:
  - Given an 11-seat game, X = 1, 7 Townsfolk, and the `xaan:X` token on the living Xaan on night 1, then
    exactly those 7 seats are poisoned by the Xaan and `isImpaired` is true for each — and the Drunk (an
    Outsider), the Minions, the Demon and the Travellers are not. (Fails today: nothing is poisoned.)
  - Given the above, then `InfoCalc.compute("empath", <a poisoned Empath>)` carries a "Poisoned by the
    Xaan" caveat and the false-info chips appear.
  - Given the `X` token placed on night X, when NIGHT→DAY has run, then the Townsfolk are **still**
    poisoned; when DAY→NIGHT has also run, then the token is gone and nobody is poisoned by the Xaan.
  - Given `setupChoices["xaan.X"] == 1` and a Pit-Hag creating a second Outsider on night 2, then night 2
    reports no poison (X is frozen).
  - Given a Xaan in the bag and no stored X, then setup validation reports "Xaan: choose X"; given X = 3
    but 2 Outsiders in the bag, then it reports the mismatch.
open: the official reminder set stops at `NIGHT 3` while *"add or remove any number of Outsider tokens"*
  permits X ≥ 4. Support any X, but warn *"X = {n}: the official reminder set only goes to Night 3."*
