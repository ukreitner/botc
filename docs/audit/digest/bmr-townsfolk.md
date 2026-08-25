# Digest — Bad Moon Rising Townsfolk (13 characters)

Sources: `docs/audit/characters/{chambermaid,courtier,exorcist,fool,gambler,gossip,grandmother,
innkeeper,minstrel,pacifist,professor,sailor,tealady}.md`. Vocabulary: `mechanics/night-engine.md`
(StepGate / NightAction / NightEffect / TokenRule / StepKey / WakeEvent), `mechanics/status-model.md`
(Effect / Until / killOutcome / DeathEvent / Prompt), `mechanics/day-engine.md`
(execute / ExecutionRecord / DayRules / DayBriefing), `mechanics/records-and-memory.md`
(LedgerEntry / Memory / PlacedReminder).

## Group notes

1. **31 P0 and 46 P1 across 13 cards, and they collapse into five engine gaps**: (a) no execution
   funnel with a `SURVIVED` outcome, (b) protection is prose (`deathNotes`) that every kill button
   ignores, (c) no durable night memory, (d) no countdown / multi-dusk expiry, (e) no dawn or
   day-start briefing. Nine of the thirteen need nothing beyond those five.
2. **Every record type these files invented is already covered.** `NightChoice` (courtier, exorcist,
   gambler, grandmother) → `LedgerEntry(kind = CHOICE)` + `Memory.lastChoice`; `PublicStatement`
   (gossip) → `LedgerEntry(kind = STATEMENT)` + `Verdict`; `DawnNote` / `InsertedNightStep`
   (professor) → `Prompt(at = DAWN|TONIGHT)` + `StepKey(variant = "first")`; `GlobalEffect`
   (minstrel) → N `Effect(DRUNK)` rows; `DeathNote` / `DeathReason` / `DeathOutcome` (innkeeper,
   sailor, tealady, fool) → `killOutcome`; `wokeTonight` (chambermaid) → `WakeEvent.ownAbility`.
   **No new record types are needed for this group.**
3. **`ExecutionRecord` has two incompatible shapes** — `day-engine §A` (`outcome`, `via`,
   `diedInsteadId`, snapshots) vs `status-model §4` (`kind`, `diedEventId`). These cards use
   day-engine's. The lead must pick one before anything is written.
4. **The Courtier countdown cannot advance where night-engine §4 says it does.** That spec runs
   `Drunk 3→2→1` "at the Courtier's step"; after the Courtier chooses they are never woken again, so
   the step does not exist. Use `Expiry.DUSK` (records-and-memory §I already has the table). The data
   also counts **down** while the wiki counts **up** — pick one direction.
5. **The Innkeeper's self-protection trap and the Sailor/Tea Lady sobriety rules fall out of
   `endsWithSource` + `abilityWorks` for free** — no per-character code. The Minstrel is the one
   effect in this group where `endsWithSource` is genuinely undecided (see its card).
6. **Five of these characters have no night step at all** (fool, minstrel, pacifist, tealady, plus the
   Sailor's standing protection). They are invisible in the app today purely because `DayBriefing` and
   `killOutcome` do not exist; they become the cheapest wins the moment those land.
7. **`SeatSheet`'s substring match over `deathNotes` prose ("can't die", "Safe", "Fool") is the
   current protection mechanism for five of these cards.** Any rewording silently disables it.
   `killOutcome` deletes the whole pattern; nothing else should be built on top of it.
8. **Assassin bypass** is required by innkeeper / sailor / tealady and is already
   `KillCause.ignoresProtection` at `killOutcome` step 1 — zero per-character work.
9. **The Chambermaid needs a machine-readable per-character wake classification that no mechanics
   spec provides.** `WakeEvent` records the past; the Mathematician jinx forces *projection* over the
   not-yet-run tail of the `NightPlan`. Decide where `acts|informed|none|conditional` lives.
10. **Six jinx rows are missing or wrong in this group** (grandmother ×2, innkeeper ×2, minstrel ×1,
    courtier ×1); two of them are win conditions and one — grandmother↔riot — currently states the
    **wrong rule** in shipped data.

---

## chambermaid — Chambermaid · BMR Townsfolk · P0:2 P1:4

today: `InfoCalc.chambermaid` (`:469-484`) asks "is this character id anywhere in tonight's order
  list", so Gossip/Grandmother/Tinker/Moonchild count as waking (answers 2 where the truth is 0), the
  Drunk counts as not-waking and the Marionette counts backwards; the picker (`NightScreen:838-861`)
  allows self, dead seats and duplicates; the headline says "(approximate)".
data:
  - characters.json: ok (`reminders: []`). Needs the new wake classification if it lands in data —
    `firstNightWakes` / `otherNightWakes` ∈ `acts|informed|none|conditional`; at minimum `none` for
    gossip, grandmother (other), tinker, moonchild, and `conditional` for assassin, godfather,
    professor, courtier, ravenkeeper.
  - night_and_jinxes.json: ok — first 70, other 93; chambermaid↔mathematician present (`:23-27`).
  - night_guide.json: copy the `other` sentence *"players woken only to be shown information they
    didn't cause don't count"* into `first`, and add *"Minion info, Demon info and Marionette info do
    not count."*
setup: none
identity: plain — but the count is per SEAT and must read `Player.nightRoleId` (Drunk, Marionette,
  Lunatic), never `characterId`. A Lunatic genuinely wakes and counts.
night.first / night.other: same action both nights; `WakeStyle` only changes which guide text shows.
  gate: `Gates.aliveHolder` AND `alivePlayers.size - 1 >= 2`, else `StepGate.Skip("fewer than 2 other
    players are alive — do not wake the Chambermaid")`. Not `actsWhileDead`, not once-per-game.
  action: `ShowInfo("chambermaid", targetsNeeded = 2, constraints = [ALIVE, NOT_SELF])`; two distinct
    seats; `sort = ALIVE_FIRST`; `allowNone = false`. Illegal picks return the pick prompt, never a
    number.
  effects: none — no tokens, no `Effect`s, no kills.
  deferred: none.
  info: answer type `Int`, legal range **0..2**. Truth = `targets.count { id ->
    state.wakes.any { it.night == night && it.playerId == id && it.ownAbility } ||
    a NightPlan step after the cursor will produce such a WakeEvent for id }`. The projection half is
    mandatory: she sits at other-night 93 and the Mathematician at 94 — that IS the jinx. Must read 0
    for `StepGate.Skip`ped steps, for the marker steps (`MINION_INFO` / `DEMON_INFO` /
    `MINION_BLUFFS` append `ownAbility = false`), for an Exorcised Demon (`StepGate.Reduced` emits no
    WakeEvent for the choice half), and for spent once-per-game holders. Must read 1 for a
    drunk/poisoned holder who still woke. Per-target `[woke] / [didn't]` override chips with a
    one-line reason stay as the escape hatch. False alternatives: existing 0–4 lie chips.
    Misregistration: **none applies** — suppress the Spy/Recluse caveats here.
  show: existing full-screen number card; no new card kinds.
  visibility: nothing to Demon / Minions / Lunatic.
day: none.
death: none. A Zombuul with `registersDead` is alive for this purpose but is unselectable while the
  app shows it dead — say so in the guide.
ledger: `WakeEvent(night, playerId, stepId, ownAbility)` is the entire memory. conflict: the
  character file's `GameState.wokeTonight: Set<Long>` is dropped — `wakes` is per-player and survives.
tests:
  - Given night 3, Chambermaid picks the Gossip and the Grandmother · Then the answer is 0 (today: 2).
  - Given a seat `characterId = "drunk"`, `shownCharacterId = "empath"` · Then it counts as waking.
  - Given a Marionette picked on night 1 · Then it does NOT count (that step wakes the Demon).
  - Given a Mathematician whose step has not run yet · Then it counts (projection — the jinx).
  - Given she picks herself or a dead seat · Then `compute` returns the pick prompt, not a number.
open: where `acts|informed|none|conditional` lives — a new `characters.json` field, or derived from
  the `NightPlan` (`StepGate` + `NightAction` kind). No mechanics spec answers this, and the
  Chambermaid cannot be made correct without it.

---

## courtier — Courtier · BMR Townsfolk · P0:2 P1:4

today: tokens are placed by hand and **never expire** — no Courtier pair is in `EXPIRES_AT_DAWN` or
  `EXPIRES_AT_DUSK` and `advancePhase` has no decrement, so a night-2 target stays drunk for the whole
  game; "Mark spent" places `courtier:No ability` but nothing consumes it, so the full "points to a
  character" invitation renders every later night; there is no character picker anywhere in the app.
data:
  - characters.json: `reminders` are `["Drunk 3","Drunk 2","Drunk 1","No ability"]` (count-DOWN); the
    wiki counts UP (`DRUNK 1→2→3`). Pick one and make the `TokenRule` chain agree. Add
    `spentLabel = "No ability"`.
  - night_and_jinxes.json: add `{courtier, vizier, "A Vizier who loses their ability learns this, and
    cannot die during the day."}`. **Verify** the existing summoner↔courtier text (`:245`): the app's
    wording and the wiki summary state two different rules — flagged unverified by the auditor.
  - night_guide.json: rewrite the countdown sentence to match the chosen direction; add *"After the
    Courtier chooses, never wake them again — the app advances the counter at dusk."*
setup: none
identity: plain. The picker's in-play marking must use `characterId`, never `shownCharacterId` —
  choosing the token a Drunk was shown does nothing, because that character is not in play.
night.first / night.other: same action both nights (first 29, other 15).
  gate: `Gates.aliveHolder` AND `Gates.notSpent("courtier", "No ability")` AND no
    `LedgerEntry(kind = CHOICE, sourceId = "courtier")` exists. Once spent:
    `StepGate.Skip("spent on night N — never wake the Courtier again")`, rendered as a read-only
    countdown line, never as an invitation.
  action: `ChooseCharacter("courtier", pool = CharacterPool.SCRIPT, allowNone = true)` with
    `noneLabel = "They shook their head — no choice"` (shaking their head does NOT spend it).
    In-play characters first, each annotated with the holder's name; two holders of one id ⇒ ask
    which seat.
  effects: always `RecordChoice(slot = "character")` and `MarkSpent("courtier")` — the ability is
    spent on any pointed-at character, in play or not, impaired or not. If in play AND the Courtier
    has their ability: `PlaceToken("courtier", "Drunk 3", Ref.TARGET, maxCopies = 1, exclusive =
    true)` + `Effect(DRUNK, sourceCharacterId = "courtier", sourcePlayerId = holder,
    until = DUSK_AFTER_N_DAYS(2))`. Not in play or impaired: no token; record the reason on the
    ledger entry.
  deferred: `TokenRule` countdown `Drunk 3 → Drunk 2 → Drunk 1 → ∅` at `Expiry.DUSK`. Schedule check:
    chosen night N ⇒ drunk nights N, N+1, N+2 and days N, N+1, N+2, removed at the dusk closing day
    N+2 = 3 nights & 3 days. **conflict:** night-engine §4 says the Courtier's own step reduces the
    counter — impossible, the Courtier is never woken again; use `Expiry.DUSK` only.
  info: none — the Courtier learns nothing, ever, including whether the choice landed.
  show: existing "Sheet" card so the player can point. No new card kinds.
  visibility: nothing to Demon / Minions / Lunatic.
day: `DayBriefing.Note(DAY_START, INFO, "courtier", "Elena is Courtier-drunk today — day 2 of 3.")`.
death: none of its own. A Courtier-drunk Demon's `Attack` fails through the normal impairment path.
  The Vizier jinx makes a Courtier-drunk Vizier immune to day death — surface it on the step.
ledger: `LedgerEntry(kind = CHOICE, sourceId = "courtier", characterIds = [picked],
  targetIds = [resolved seat] or empty, impaired, note = "not in play" | "Courtier was impaired")`
  plus `LedgerEntry(kind = SPENT)`. `Memory.cyclesSince` powers "2 nights & 2 days left".
tests:
  - Given `courtier:Drunk 3` placed night 2 · When three dusks pass · Then the labels step 3→2→1→gone
    and `impairment()` is non-empty on nights 2–4 and empty on night 5. **Fails today.**
  - Given a spent Courtier · When the night-3 plan is built · Then the step is `Skip` and its detail
    contains no "points to a character" invitation.
  - Given the Courtier picks a character no seat holds · Then no token is placed, the ledger entry
    notes "not in play", and the Courtier is spent.
  - Given the Courtier is poisoned · When they choose an in-play character · Then the target is not
    impaired and the Courtier is still spent.
  - Given a Courtier-drunk seat · When `swapCharacters` runs · Then the token stays with the seat.
open: countdown direction (data 3→1 vs wiki 1→3) and the correct summoner↔courtier jinx text.

---

## exorcist — Exorcist · BMR Townsfolk · P0:2 P1:4

today: the Demon's step gets an appended sentence (`NightOrder:149-154`) while `QuickResolutions`
  still renders a fully live `DemonKillPanel` for the silenced Demon — this is the reported Pukka
  failure. And `exorcist:Chosen` is swept in `EXPIRES_AT_DAWN`, so "different to last night" has no
  evidence to work from on the following night.
data:
  - characters.json: `reminders: ["Chosen"]` ok. The auditor's proposed second token
    `"Silenced tonight"` is **not needed** once the Reduced gate reads tonight's choice record.
  - night_and_jinxes.json: other 32 is correct (before every Demon, after the Lunatic).
    exorcist↔yaggababble present (`:289-291`); add exorcist↔leviathan and exorcist↔riot once the
    wording is verified — the auditor could not cross-check them.
  - night_guide.json: show text must read **"THIS CHARACTER SELECTED YOU"** (currently "THIS PLAYER
    STOPPED YOU TONIGHT"); add *"Deaths already scheduled from earlier nights (Pukka poison, Gossip,
    Assassin) still happen."*
setup: none
identity: plain.
night.other only — there is no first-night step; keep it that way.
  gate: `Gates.aliveHolder`. No once-per-game limit.
  action: `ChoosePlayers("exorcist", min = 1, max = 1, constraints = [ANY_LIVING_STATE, SELF_ALLOWED,
    DIFFERENT_FROM_LAST_NIGHT], sort = ALIVE_FIRST, allowNone = false)`. Dead seats are explicitly
    legal (Zombuul). Self is legal on the default reading — allow it, badge it as unverified rather
    than blocking. The previous night's seat renders disabled: *"chosen last night — not allowed"*.
  effects: `RecordChoice()` + `PlaceToken("exorcist", "Chosen", Ref.TARGET)` (keep `Expiry.DAWN`).
    When the target is the Demon and the Exorcist has their ability: `ShowCardTo(Ref.TARGET,
    "THIS CHARACTER SELECTED YOU")` plus a `WakeEvent(ownAbility = false)` for that Demon.
  deferred: the Demon's own step, later tonight, resolves to
    `StepGate.Reduced("Exorcised", allow = setOf("pending", "passive"))` — **never Skip**. The choice
    half is replaced by a banner and a `[Nothing chosen]` button; the deferred half still runs
    (Pukka's earlier victim dies, Shabaloth regurgitates, Zombuul survives its first death,
    Vigormortis's zombie Minions keep acting). Predicate: a `LedgerEntry(kind = CHOICE, sourceId =
    "exorcist", cycle == tonight, impaired == false)` whose `targetIds` contain that seat — cycle
    scoped by construction, so a stale token can never silence anyone.
  info: none for the Exorcist. The Demon learns who the Exorcist is.
  show: the info token plus the Exorcist character token, then point at the Exorcist's seat.
  visibility: Demon only; Minions see nothing. A Lunatic is not the Demon and is unaffected — the
    real Demon's step must read cleanly with both the Lunatic line and the Exorcised banner.
day: none.
death: none. Explicitly: the Exorcist cancels nothing that was already scheduled.
ledger: `LedgerEntry(kind = CHOICE, sourceId = "exorcist", targetIds = [T], impaired)`. An **impaired**
  Exorcist still records the choice (tomorrow's constraint binds) but produces no Reduced gate.
tests:
  - Given an Exorcist choice onto the Po on night 2 · Then the Po's gate is `Reduced`, no kill panel
    renders, and its pending effects still resolve. **Fails today.**
  - Given `exorcist:Chosen` left on the Po from night 2 and a dead Exorcist on night 3 · Then the
    night-3 gate is `Fire` (suppression is cycle-scoped).
  - Given a night-2 choice of seat 4 · Then the night-3 picker's disabled set is exactly `{4}`; with
    no night-2 choice it is empty.
  - Given a poisoned Exorcist who chooses the Demon · Then `Chosen` is placed, the gate stays `Fire`,
    and a `MalfunctionEvent` is recorded.
  - Given a BMR game · Then `firstNight` contains no `exorcist` step (regression).
open: is self-selection legal (not stated on the page); leviathan/riot jinx wording; what "the Demon"
  means for Lil' Monsta (the babysitter-token holder), Legion, Kazali and Lord of Typhon.

---

## fool — Fool · BMR Townsfolk · P0:2 P1:3

today: one prose note (`StatusEffects:75-77`) that ignores impairment, plus a substring-matched
  confirmation dialog reachable only from the seat sheet. All three Day-tab execution buttons kill
  outright, and `[Death prevented]` places no token and records nothing — so the app will let the Fool
  "survive" twice, and will let a poisoned Fool survive once.
data:
  - characters.json: ok (`reminders: ["No ability"]`); add `spentLabel = "No ability"`.
  - night_and_jinxes.json: ok — no jinxes, and correctly absent from both night orders.
  - night_guide.json: **no `fool` entry exists**. Add one carrying the announcement wording and the
    precedence rule; it is the only per-character prose channel in the app.
setup: none
identity: plain.
night.first / night.other: no step, ever. Do not add one.
day: `DayBriefing.Note(DAY_START, INFO, "fool", "The Fool's ability is unspent.")`, becoming
  *"The Fool's ability is spent."* after it fires. `EXECUTION` slot carries the announcement script.
death: `killOutcome` **step 14** — `Spends(inner = Blocked, mark = Effect(SPENT, sourceCharacterId =
  "fool", until = FOREVER))`. Deliberately last so every other protection resolves first and the
  ability is **not** consumed (*"If another character's ability protects the Fool, the Fool does not
  use their ability."*). Applies to every `DeathKind` — the ability text is unconditional; flagged:
  the wiki page only enumerates execution and Demon attack. Suppressed when the seat already carries
  the `SPENT` effect or `Status.hasAbility(fool)` is false (a drunk or poisoned Fool dies, and the
  ability is spent in the dying). On fire, in one undoable update: place `fool:No ability`
  (`Expiry.NEVER`, never in either expiry table), and for an execution write
  `ExecutionRecord(outcome = SURVIVED, preventedBy = "fool")` and emit *"Say: 'Ana was executed… and
  remains alive.' Do not say why."*
ledger: `LedgerEntry(kind = SPENT, sourceId = "fool")` plus the execution row, so the game log shows
  "executed, survived" instead of a day where nothing happened.
tests:
  - Given a healthy unspent Fool executed · Then `killOutcome` returns `Spends(Blocked)`, the Fool is
    alive, no `DeathEvent` exists, and `fool:No ability` is on the seat. **Fails today.**
  - Given a Fool already carrying `fool:No ability` · Then the outcome is `Dies`.
  - Given a poisoned Fool · Then no Fool branch is reached and the outcome is `Dies`. **Fails today.**
  - Given a Fool carrying `monk:Safe` hit by a Demon attack · Then the Monk blocks and the Fool does
    NOT gain `No ability`.
  - Given the ability fires · When `undo()` runs · Then the token is gone and the Fool is unspent.
open: none — the "any death" reading is an inference from the ability text; state it in the guide.

---

## gambler — Gambler · BMR Townsfolk · P0:1 P1:4

today: nothing exists. No resolver, no player picker, no character picker, no comparison, no kill
  button. The only way to kill the Gambler is the seat sheet's "Died at night", which hard-codes
  `DeathCause.DEMON` — corrupting Undertaker history and making the Grandmother look doomed.
data:
  - characters.json: ok (`reminders: ["Dead"]`, no first-night reminder).
  - night_and_jinxes.json: ok — other 17, and lycanthrope↔gambler present (`:165-166`).
  - night_guide.json: soften the drunk/poisoned sentence to note a malfunctioning ability is
    ultimately the Storyteller's call, and add *"The Gambler may guess any player, dead or alive,
    including themselves."*
setup: none
identity: the comparison is against `Player.characterId` and nothing else. `shownCharacterId` is used
  **only** for the red warning line; `nightRoleId` must never be used here. A Drunk shown the Chef is
  the Drunk: a guess of "Chef" is WRONG and a guess of "Drunk" is CORRECT.
night.other only.
  gate: `Gates.aliveHolder`. No once-per-game limit — they may guess every night.
  action: `ChoosePlayerAndCharacter("gambler", playerConstraints = [ANY_LIVING_STATE, SELF_ALLOWED],
    pool = CharacterPool.SCRIPT, requireNotInPlay = false)`. Every seat listed, dead dimmed but
    enabled, own seat labelled `(self)`. Character grid searchable, in-play-first ordering **only**
    — no other in-play highlighting, or the picker advises instead of recording.
  effects: `RecordChoice()` always. Then, in order: (1) Gambler impaired ⇒ no death by default plus
    `[Kill anyway]` and a `MalfunctionEvent`; (2) exact match ⇒ no death; (3) mismatch where the
    target is a Spy or Recluse and the guess is a plausible misregistration ⇒ a two-button ruling
    written as `LedgerEntry(kind = RULING, sourceId = "misregister")`; (4) mismatch ⇒
    `Attack(Ref.SOURCE, cause = KillCause(GOOD_ABILITY, "gambler"), respectProtection = true)` +
    `PlaceToken("gambler", "Dead", Ref.SOURCE)`.
  deferred: none — the death is immediate. Because the Gambler acts at other-night 17 and the Demons
    at 36–54, a Gambler who dies here is already dead when the Demon chooses.
  info: **none, ever.** The step must say so in small type so the ST does not leak the answer with a
    reaction.
  show: existing "Sheet" card. No new card kinds.
  visibility: nothing to Demon / Minions / Lunatic.
day: none. A publicly claimed guess is an ordinary `LedgerEntry(kind = STATEMENT)`.
death: the self-kill runs through `killOutcome` like any other. `GOOD_ABILITY` means Monk/Soldier
  (`SAFE_FROM_DEMON`) do **not** block it, while Innkeeper (`CANT_DIE_TONIGHT`), Sailor/Tea Lady
  (`CANT_DIE`) and the Fool do — render those before the confirm button. `gambler:Dead` never expires.
  A grandchild Gambler dying this way must NOT kill the Grandmother (cause is not `DEMON_ABILITY`).
ledger: `LedgerEntry(kind = CHOICE, sourceId = "gambler", targetIds = [T], characterIds = [guess],
  verdict = TRUE|FALSE, impaired)` — the single most useful post-game line for a Gambler game.
tests:
  - Given a correct guess · Then no `DeathEvent` and a ledger entry with `verdict = TRUE`.
  - Given a wrong guess · Then the Gambler is dead with `KillCause(GOOD_ABILITY, "gambler")` (never
    `DEMON_ABILITY`) and carries `gambler:Dead`. **Fails today.**
  - Given `characterId = "drunk"`, `shownCharacterId = "chef"` · Then "chef" is wrong and "drunk" is
    correct.
  - Given an impaired Gambler and a wrong guess · Then the default outcome is no death.
  - Given the grandchild token on the Gambler · When they die from their own guess · Then no
    Grandmother death is produced.
open: the wiki page is silent on misregistration, impairment and protection for this character — all
  three are inference. The lycanthrope jinx ("no one else can die that night" after a fatal
  self-guess) needs a night-scoped marker that no spec currently defines.

---

## gossip — Gossip · BMR Townsfolk · P0:2 P1:4

today: the step is prose and nothing else — no "was it true?", no victim picker, no kill button. There
  is **nowhere in the app to record a public statement** (the only free text is one game-wide
  `storytellerNotes` blob behind the overflow menu), which is the user's verbatim complaint. Killing
  via the seat sheet stamps `DeathCause.DEMON`, which wrongly dooms the Grandmother and fakes the
  Sage/Choirboy triggers.
data:
  - characters.json: ok — text matches verbatim, `reminders: ["Dead"]`, no first-night reminder.
  - night_and_jinxes.json: ok — other 57, after every Demon, the Assassin (55) and Godfather (56).
  - night_guide.json: soften *"choose any player who is not protected"* to *"choose any player; prefer
    one who isn't protected. If everyone is protected, no one dies."* Add *"Judge drunkenness and
    death now, at this step — not when the statement was made."* Add `memory: { consumes: ["gossip"] }`.
setup: none
identity: plain.
night.other only — the Gossip is **never woken**; this step is pure Storyteller bookkeeping.
  gate: the row is always built. `StepGate.Fire`, then the panel resolves in order: no statement
    recorded → offer `[Record one now]` (back-dated) / `[The Gossip said nothing]`; verdict
    `UNJUDGED` → show the statement verbatim and `[True] [False] [Didn't count]`; `FALSE` → no death;
    Gossip not alive → *"died earlier tonight — the ability does not trigger"*; Gossip impaired **now**
    → no death by default plus `[Kill anyway]` and a `MalfunctionEvent`. The step checkbox is
    **blocked** while any `gossip` entry for yesterday is unresolved (StepMemory `consumes`).
  action: `ChoosePlayers("gossip", min = 1, max = 1, constraints = [ANY_LIVING_STATE, SELF_ALLOWED],
    allowNone = true, noneLabel = "Nobody can die — all protected")`. Sort: alive-and-unprotected
    first, alive-but-protected greyed with the reason, dead disabled.
  effects: `Attack(Ref.TARGET, cause = KillCause(GOOD_ABILITY, "gossip"))` +
    `PlaceToken("gossip", "Dead", Ref.TARGET)` + resolve the ledger entry (`resolvedCycle`) +
    `RecordChoice()`.
  deferred: none — announced at dawn with the other deaths.
  info: none is given to the Gossip; they learn only from whether a death happens.
  show: none.
  visibility: nothing to anyone.
day: this character is the reason the day ledger exists. `DayBriefing.Note(DAY_START, ACTION,
  "gossip", "Record Cara's statement when she makes it.")` with `actionId` opening the composer; a
  *"No Gossip statement recorded today."* nudge with `[Record it]`. The composer must work with **no
  Gossip in play** — that is the user's explicit request.
death: `GOOD_ABILITY`, so Monk `Safe` and the Soldier do **not** block (render muted, not red) while
  Innkeeper `Protected`, Sailor, Tea Lady, Fool and the Lleech host do. Never `DEMON_ABILITY` — a
  Gossip-killed grandchild must not kill the Grandmother. `gossip:Dead` never expires.
ledger: `LedgerEntry(kind = STATEMENT, sourceId = "gossip", speaker = the Gossip's seat, text,
  targetIds, verdict, impaired, resolvedCycle)`. The night step consumes the newest such entry with
  `cycle == state.cycle - 1`. **Impairment is stored as it was when spoken but judged again now** —
  the wiki example turns on exactly this.
tests:
  - Given statements on days 1, 2 and 3 · When night 3 resolves · Then the day-2 `gossip` entry is
    selected.
  - Given a `TRUE` day-2 statement · When seat 5 is chosen · Then seat 5 dies with
    `KillCause(GOOD_ABILITY, "gossip")`, carries `gossip:Dead`, and the entry's `resolvedCycle == 3`.
  - Given the grandchild token on seat 5 killed this way · Then the Grandmother trigger does not fire.
  - Given a Gossip killed by the Demon earlier tonight · Then the resolver reports "does not trigger"
    and offers no victim picker.
  - Given a true statement made while poisoned, with the poison gone by night · Then the ability
    **fires**; and the converse (healthy by day, poisoned by night) does not.
open: none. (The auditor's `PublicStatement` / `StatementKind` types are dropped in favour of
  `LedgerEntry` / `LedgerKind`; nothing is lost — `kind` becomes `sourceId = "gossip"`.)

---

## grandmother — Grandmother · BMR Townsfolk · P0:2 P1:4

today: the first-night info works (`revealCharacter`) but the picker lists every seat instead of
  defaulting to the marked grandchild; the other-night step renders the conditional sentence and
  stops — no answer, no button, even though the answer is a pure function of state; the `deathNotes`
  hint fires regardless of cause **and** regardless of the Grandmother's impairment; and there is no
  setup validation that a grandchild was marked at all.
data:
  - characters.json: ok — `reminders: ["Grandchild","Dead"]`, both night reminders present.
  - night_and_jinxes.json: **P0 data bug** — `:229-232` says *"If Riot kills the Grandchild, the
    Grandmother dies too."* The real jinx is *"If Riot is in play and the Grandchild dies by
    execution, evil wins."* Replace it. **Add** `{leviathan, grandmother, "If the Leviathan is in play
    and the Grandchild dies by execution, evil wins."}` — missing entirely. Both are win conditions.
  - night_guide.json: `:253` → *"Choose a good player (Townsfolk or Outsider) to be the grandchild —
    not the Grandmother herself. The Spy is the one evil player who may legally be chosen, since they
    register as good."*
setup: `SetupTask` row — id `grandmother.grandchild` · kind `PLACE_TOKEN` · prompt *"Mark exactly one
  good player as the Grandchild"* · candidates: all seats except the Grandmother · validation: exactly
  one `grandmother:Grandchild` token; not on the Grandmother's own seat; holder is not evil (the Spy
  is the one legal exception). Mirrors the Fortune Teller red-herring block; **no Grandmother case
  exists in `validateSetupState` today**.
identity: the info is the grandchild's **true** `characterId`. If the grandchild is the Drunk, warn
  that showing the Drunk token reveals them; if the Spy, keep the misregistration caveat.
night.first:
  gate: `Gates.aliveHolder`, first night only.
  action: `ShowInfo("grandmother", targetsNeeded = 1)` **pre-selected** to the seat holding
    `grandmother:Grandchild`; any other pick is badged *"not the marked Grandchild"*.
  effects: none — the token was placed at setup.
  info: `"<name> is the <character>"`. False alternatives: the Grandmother is the one BMR role whose
    lie has **two independent axes** — offer a different player AND a different character token.
  show: existing `THIS IS YOUR GRANDCHILD` token card, pre-filled rather than searched.
  visibility: nothing to Demon / Minions / Lunatic; the grandchild is never told.
night.other:
  gate: the row exists whenever a `grandmother:Grandchild` token exists and a living Grandmother holds
    the character. The Grandmother is **not woken** — Storyteller bookkeeping (other-night 71, after
    every kill source and after the Professor's 63).
  action: none, or a single confirm button. Four exclusive outcomes: no death tonight ⇒ nothing;
    death with `cause.kind != DEMON_ABILITY` ⇒ *"died tonight, but not to the Demon"*; Demon death but
    the Grandmother lacked her ability **at the moment the grandchild died** ⇒ no death by default +
    `[Kill anyway]` + `MalfunctionEvent`; Demon death and she had her ability ⇒ she dies.
  effects: `Attack(Ref.SOURCE, cause = KillCause(GOOD_ABILITY, "grandmother"))` +
    `PlaceToken("grandmother", "Dead", Ref.SOURCE)`. Auto-apply when `killOutcome` returns a clean
    `Dies`; prompt when it returns `Blocked`/`Choice` (a Sailor/Tea Lady/Innkeeper/Fool Grandmother).
  deferred: this is a `DeathTrigger("grandmother")` on *the grandchild's* `DeathEvent`, not a wake —
    it fires as a `Prompt(at = TONIGHT)` at the Grandmother's night-order position.
day: `DayBriefing` NOMINATION/EXECUTION notes for the Riot and Leviathan jinxes — the trigger is the
  **grandchild being executed**, a day event, so a night-step mention is useless.
death: the impairment snapshot must be taken **when the grandchild dies**, not at her own step (the
  wiki's Sailor-drunk example): stamp it on the `DeathEvent`/`Prompt` at `onDeath` time. A resurrected
  grandchild does not trigger it (`resurrectedAtCycle != null`), which is why the Professor at 63 must
  stay ahead of 71. `grandmother:Grandchild` and `grandmother:Dead` both never expire.
ledger: nothing beyond the `DeathEvent` pair and the dawn script — but the dawn report must announce
  **both** deaths in seat order.
tests:
  - Given a Grandmother and no grandchild token · Then setup validation reports it; on a Minion it
    reports the good-player issue; on her own seat, the other-player issue. **All fail today.**
  - Given the grandchild died this cycle with `DEMON_ABILITY` · Then the Grandmother dies with
    `GOOD_ABILITY/grandmother` and gains `grandmother:Dead`.
  - Given the grandchild died with `GOOD_ABILITY` (Gossip/Gambler) or by execution · Then she lives.
  - Given the grandchild was Demon-killed then resurrected by the Professor on the same night · Then
    no death is produced (**the ordering test**).
  - Given the grandchild is about to be executed · Then no "the Grandmother dies too" note appears.
    **Fails today.**
open: the Grandmother's own death cause is inference (the wiki does not name it); `GOOD_ABILITY` is
  the choice here, and it matters for Sage/Choirboy/Undertaker bookkeeping.

---

## innkeeper — Innkeeper · BMR Townsfolk · P0:3 P1:4

today: `characters.json` declares one `"Protected"`, so the night tray uses `placeExclusiveReminder`
  and the second tap **removes the token from the first player**; the Demon kill panel kills a
  Protected player with one tap; nothing checks the Innkeeper's own impairment or the
  self-protection trap; and both jinxes (game-ending) are absent from the data.
data:
  - characters.json: `"reminders": ["Protected", "Protected", "Drunk"]` — two Protected. One-line fix,
    and `maxCopies` then reads the truth instead of guessing.
  - night_and_jinxes.json: add `{innkeeper, leviathan, "If the Leviathan nominates and executes an
    Innkeeper-protected player, good wins."}` and the same for `riot`. The file has 58 jinxes and
    none mention the Innkeeper.
  - night_guide.json: append *"Protection lasts only tonight; the drunkenness lasts through tomorrow
    until dusk. The Assassin kills through this protection."*
setup: none
identity: plain.
night.other only (other 14; correctly absent from `firstNight`).
  gate: `Gates.aliveHolder`.
  action: `ChoosePlayers("innkeeper", min = 2, max = 2, constraints = [ANY_LIVING_STATE,
    SELF_ALLOWED], sort = ALIVE_FIRST)` — two distinct seats, dead badged *"already dead — protection
    does nothing"* — followed by a **Storyteller-only** `YesNo`/chip choice of which of the two is
    drunk (default: the non-Innkeeper one). Protecting the Demon or a Minion is legal and common;
    do not warn it away.
  effects: `PlaceToken("innkeeper", "Protected", Ref.ALL_TARGETS, maxCopies = 2, exclusive = false)`
    — never `placeExclusiveReminder` — plus `Effect(CANT_DIE_TONIGHT, until = DAWN,
    sourcePlayerId = innkeeper, endsWithSource = true)` on both; and
    `PlaceToken("innkeeper", "Drunk", <chosen>)` + `Effect(DRUNK, until = DUSK)`.
    If the Innkeeper lacked their ability **before** acting: place nothing and say so (offer a
    bluff-preserving override that marks the tokens "(no effect)").
  deferred: none. The drunk player's abilities silently fail for the rest of tonight and all tomorrow.
  info: none. Nothing is shown to anyone; the chosen players are not told.
  show: none.
  visibility: nothing.
day: `DayBriefing.Note(DAY_START, INFO, "innkeeper", "Bo is Innkeeper-drunk until dusk.")`.
  NOMINATION note when the nominee holds `Protected` and the nominator is a Leviathan/Riot: *"JINX:
  if this nomination executes them, GOOD WINS."*
death: `EffectKind.CANT_DIE_TONIGHT` at `killOutcome` **step 6** — blocks every night cause, absent
  for `EXECUTION` (and the token is gone by dawn anyway). `KillCause.ignoresProtection` (Assassin)
  wins at step 1. **The self-protection trap needs no special case**: the effects carry
  `sourcePlayerId = innkeeper` and `endsWithSource = true`, so an Innkeeper who made themselves drunk
  fails `abilityWorks` and both `CANT_DIE_TONIGHT` effects go inactive automatically — wiki example 3
  falls out of `status-model §2` for free. Expiry: `Protected` → DAWN, `Drunk` → DUSK (both correct
  today).
ledger: `LedgerEntry(kind = CHOICE, sourceId = "innkeeper", targetIds = [a, b],
  text = "drunk: <name>")` — the Innkeeper may repeat targets, so this is courtesy context
  (*"last night: A & B, B drunk"*), not a constraint.
tests:
  - Given two targets · When the action resolves · Then **both** hold `innkeeper:Protected`.
    **Fails today** via the tray path.
  - Given tokens placed night 2 · Then `Protected` is gone at dawn while `Drunk` survives all of day 2
    and is gone at dusk.
  - Given a poisoned Innkeeper · Then no tokens are placed and no effects exist.
  - Given `drunkId == innkeeperId` · Then neither target is protected (`killOutcome` returns `Dies`).
  - Given a protected player and an Assassin kill · Then the kill proceeds (`ignoresProtection`).
open: may the Innkeeper point at dead players? The ability does not say "alive"; treat as legal but
  pointless and badge it.

---

## minstrel — Minstrel · BMR Townsfolk · P0:3 P1:3

today: one grey `deathNotes` sentence, visible only if the ST happened to open that Minion's seat
  first. The drunkenness is **never applied** — so after a Minion execution the Demon still kills, the
  Fortune Teller still gets true info, and the app silently contradicts the rules for two phases.
  There is also no expiry shape that can express "until dusk tomorrow" (two dusks, not one).
data:
  - characters.json: move `"Everyone is drunk"` from `reminders` to `remindersGlobal` — it is a
    grimoire-centre token and must never be read as an impairment on the seat it sits on. Requires
    `remindersGlobal` to actually mean "centre of the grimoire" (see `records-and-memory §C`
    `GameState.storytellerReminders`).
  - night_and_jinxes.json: add `{minstrel, legion, "If Legion died by execution today, Legion keeps
    their ability, but the Minstrel might learn they are Legion."}` — missing.
  - night_guide.json: **no `minstrel` entry at all**. Add one with a day/standing section carrying the
    How-to-Run text; today the character has zero run-book prose in an app that has 116 entries.
setup: none
identity: judged on the **true** character's team. A Recluse executed while registering as a Minion
  *may* trigger it — Storyteller's choice, offered as a toggle, recorded as
  `LedgerEntry(kind = RULING, sourceId = "misregister")`.
night.first / night.other: no step, ever. Correctly absent from both order lists.
day: the whole character. Trigger fires inside the execution funnel when **all** hold: the victim's
  true team is `MINION`; they were alive immediately before; the execution actually killed them
  (`ExecutionRecord.outcome == DIED`, not `SURVIVED`); the victim is not a Traveller (exile is not
  execution); and a Minstrel is in play, alive and has their ability.
  `DayBriefing.Note(DAY_START, ALERT, "minstrel", "EVERYONE IS DRUNK (Minstrel) — except <Minstrel>
  and Travellers. Ends at dusk on day N+1.")`, repeated on the second day as *"this is the last
  day — give false info to everyone but <Minstrel>."*
death: not a protection. It **removes** protections: while it runs, the Sailor and Tea Lady are drunk
  and mortal, the Fool loses its save and a Zombuul loses "the first time you die, you don't" — the
  wiki's third example is exactly this and is the acceptance test.
effects: on trigger, emit one `Effect(DRUNK, sourceCharacterId = "minstrel",
  sourcePlayerId = <Minstrel seat>, until = DUSK_AFTER_N_DAYS(1), untilCycle = day + 1,
  label = "")` per non-Traveller player **except the Minstrel** — alive **and** dead — plus the
  centre token in `storytellerReminders`. A second qualifying execution **replaces** the set
  (refreshed `untilCycle`), matching *"everyone becomes drunk again"*. Duration proof: executed day N
  ⇒ drunk for the rest of day N, all of night N+1 and all of day N+1, removed at the dusk closing
  day N+1 — it survives one dusk and dies at the second.
  conflict: `endsWithSource` defaults to `true`, which would silently end the whole effect if the
  Minstrel is later poisoned or dies. The auditor's ruling is *do not decide silently* — set
  `endsWithSource = false` and raise a `Prompt` on the Minstrel's death (*"Glossary says persistent
  effects end; the Minstrel page says remove at dusk tomorrow. End it now, or let it run?"*).
ledger: `LedgerEntry(kind = RULING, sourceId = "minstrel")` when the ST resolves the Recluse or Legion
  question; the execution row already carries the rest.
tests:
  - Given an alive sober Minstrel and an Assassin executed on day 2 · Then every non-Traveller except
    the Minstrel is impaired, and the Minstrel is not.
  - Given that · When dusk into night 3 passes · Then it still runs (the Demon cannot kill); when dusk
    into night 4 passes · Then it is gone.
  - Given a Minion killed at night, or an already-dead Minion "executed", or a Minion whose execution
    outcome is `SURVIVED` · Then nothing fires (the third requires `ExecutionRecord`).
  - Given a poisoned or dead Minstrel · Then nothing fires.
  - Given an evil Traveller exiled · Then nothing fires.
open: does the effect end when the Minstrel dies mid-run? Glossary says yes, the character page
  implies no. Prompt, do not decide. Also: a second execution the same day, and whether a Legion
  execution fires it at all.

---

## pacifist — Pacifist · BMR Townsfolk · P0:3 P1:2

today: **zero references in `engine/src`.** There is no moment in the app at which "this player is
  good and about to be executed — should the Pacifist save them?" is ever asked, and no way to record
  the outcome: the ST simulates a save by *not pressing Execute*, which leaves the app believing the
  day had **no execution**, corrupting the Zombuul, Undertaker, Vortox, Mayor and nomination state.
data:
  - characters.json: ok — text correct, `reminders: []`, no night reminders.
  - night_and_jinxes.json: ok — no jinxes, correctly absent from both night orders.
  - night_guide.json: no `pacifist` entry; add one (needs the new day-section shape) carrying the
    How-to-Run sentence verbatim, including *"do not say why"* and *"this was the one execution for
    the day"*.
setup: none
identity: judged on **alignment**, not team — `Player.isEvil(lookup)` already folds in
  `alignmentFlipped`, so a good Drunk, Lunatic or Recluse all qualify. A Recluse registering as evil
  may be excluded at ST discretion (offer it, record it as a `RULING`).
night.first / night.other: no step, ever.
day: `DayBriefing.Note(DAY_START, INFO, "pacifist", "Pacifist in play — executed good players might
  not die. Used N× this game.")`; the "approximately once per game" guidance is supportable because
  the count is derivable from `state.executions`.
death: `killOutcome` **step 9** — `Choice("<name> is good and was executed. Do they die?", [They die]
  / [They survive — say nothing])`, offered only when the target registers good and a Pacifist is
  alive with their ability. Must render as **buttons, never advice**. Ordering: any *forced* save
  (Devil's Advocate `SURVIVES_EXECUTION` at step 8, Tea Lady/Sailor `CANT_DIE` at step 5, the Fool at
  14) pre-empts it, so the dialog preselects the forced source and the Pacifist stays unused.
  A survival writes `ExecutionRecord(day, playerId, outcome = SURVIVED, preventedBy = "pacifist",
  via = VOTE, nominatorId, tally, threshold)` — **the execution still happened**: no further
  nominations, `DayRules.executionSpent` is true, the dusk guard stops nagging, no `DeathEvent`
  exists (so the Zombuul's "no-one died today" is satisfied), the Undertaker learns nothing and the
  Minstrel does not fire. No token — the Pacifist correctly has none.
ledger: the `ExecutionRecord` is the record; the log must read *"Day 3: <name> was executed and
  survived"*. The reason is **never** announced — the app must not generate a public statement about
  why.
tests:
  - Given a good player on the block on day 2 · When `execute(outcome = SURVIVED,
    preventedBy = "pacifist")` · Then they are alive, one day-2 `ExecutionRecord` exists, and the dusk
    guard no longer blocks.
  - Given that · Then `state.deaths` is unchanged and the Zombuul's "no-one died today" is true.
  - Given that · Then the Undertaker learns nothing tonight.
  - Given an evil nominee, a dead Pacifist, or an impaired Pacifist · Then no Pacifist choice is
    offered; given the Pacifist is themselves the nominee · Then it **is** offered (wiki example).
  - Given the nominee holds `devilsadvocate:Survives execution` · Then the survival is forced and
    attributed to `devilsadvocate`, not the Pacifist.
open: does a *survived* execution count for the Mastermind's "their team loses" clause and for
  Leviathan's "more than 1 good player executed"? Flag both to the ST rather than deciding.

---

## professor — Professor · BMR Townsfolk · P0:4 P1:3

today: the resolver resurrects **any** dead player including Outsiders, Minions and Demons; a drunk or
  poisoned Professor still resurrects; no `Alive` token is placed; the target's own spent-ability
  marker is not cleared; nothing is announced at dawn; and the resurrected player's first-night step
  is never re-run. The last two are the user's headline complaints.
data:
  - characters.json: ok (`reminders: ["Alive","No ability"]`, other-night only). Add
    `spentLabel = "No ability"`; optionally `resurrectionWake: "rerunFirstNight"` as an explicit
    override on the "you start knowing" characters instead of relying on the derived predicate.
  - night_and_jinxes.json: ok — other 63, absent from `firstNight`, no jinxes.
  - night_guide.json: the prose is already correct and complete; append *"If they woke on the first
    night only, wake them again now, right after the Professor sleeps."*
setup: none
identity: team is judged on the **true** `characterId`, never `shownCharacterId` — a dead Drunk or
  Lunatic is an Outsider and is not resurrected even while holding a Townsfolk or Demon token. The
  dead **Spy** may register as a Townsfolk *even while dead*, so it is a legal target at ST
  discretion; a dead Recluse never can.
night.other only.
  gate: `Gates.aliveHolder` AND `Gates.notSpent("professor", "No ability")`. Otherwise
    `StepGate.Skip("ability already used" / "Professor is dead")`, auto-ticked and collapsed — the
    wiki says to remove their token from the night sheet.
  action: `ChoosePlayers("professor", min = 0, max = 1, constraints = [DEAD], sort = DEAD_FIRST,
    allowNone = true, noneLabel = "Shook their head — no choice")`. Candidate badges: *"Townsfolk"* /
    *"not a Townsfolk — nothing will happen"* / *"Spy: may register as a Townsfolk even while dead"*.
    Shaking their head spends nothing.
  effects: pointing at anyone always `MarkSpent("professor")` +
    `PlaceToken("professor", "No ability", Ref.SOURCE)`. Then, only if the Professor has their ability
    **and** the target is a Townsfolk: `Resurrect(Ref.TARGET, clearSpentMarks = true)` +
    `PlaceToken("professor", "Alive", Ref.TARGET)`. Otherwise stop at *"Nothing happens — the ability
    is spent."* `clearSpentMarks` is the Glossary rule (*"they gain their ability back, even if it was
    a once-per-game ability that had been used"*), and it must drop the seat's own `No ability` /
    `Used` / `Spent` effects.
  deferred: `Prompt(at = DAWN, kind = ANNOUNCE, "Announce that <name> is alive again")` — spoken
    **after** the deaths and **without a reason**. Plus, tonight, a `PendingEffect(kind =
    "first-night", targetId, dueNight = cycle)` which the planner turns into
    `StepKey(id, playerId, variant = "first")` with `style = WakeStyle.FIRST_NIGHT`, placed at that
    character's first-night order position but never before the cursor (insert-after-cursor rule).
    Only for characters that wake on night 1 **only** — derived as `firstNightReminder.isNotBlank() &&
    (otherNightReminder.isBlank() || ability startsWith "You start knowing" || contains "On your 1st
    night")`, which selects Grandmother, Chef, Washerwoman, Clockmaker, Shugenja … and rejects Empath,
    Fortune Teller, Ravenkeeper, Undertaker. Slots after 63 simply wake later tonight; before 63, they
    act from tomorrow night; the step must say which of the four cases applies. The re-run uses the
    **first-night** guide and fresh, currently-true info.
  info: none computed for the Professor itself.
  show: none for the Professor; the re-run step shows whatever that character shows.
  visibility: nothing to Demon or Minions. The town learns only *that* a player is alive again.
day: none beyond the dawn announcement.
death: `Resurrect` sets `alive = true`, `ghostVoteUsed = false`, stamps `resurrectedAtCycle` on the
  newest un-resurrected `DeathEvent` (keep it — the Undertaker already learned it, and the Zombuul's
  "died today" was a fact about that day), and drops that death's effects and the Dead-family markers.
  `professor:Alive` and `professor:No ability` never expire.
ledger: the `Prompt`/announce pair plus the `DeathEvent` mutation; the game log must show
  "alive again on night N" instead of reading as a plain death.
tests:
  - Given a dead Lunatic · When the Professor points at them · Then they stay dead, the Professor is
    spent, and no dawn announcement is queued.
  - Given a dead Grandmother resurrected on night 3 · Then she is alive with `professor:Alive`, her
    `DeathEvent.resurrectedAtCycle == 3`, and a `DAWN` announce prompt names her.
  - Given a poisoned Professor · Then nobody is resurrected and the ability is still spent.
  - Given a dead Slayer carrying `slayer:No ability` · When resurrected · Then that token is gone.
  - Given a resurrected Grandmother · Then the plan contains a step with `variant = "first"`,
    `style = FIRST_NIGHT`, immediately after the Professor's, with its own checkbox; a resurrected
    Empath inserts nothing (she wakes later tonight anyway); a resurrected Sailor inserts nothing
    (order 9 < 63 — acts from tomorrow).
open: what a re-run "you start knowing" ability actually shows — the wiki example reads as a genuine
  fresh use, so default to currently-true info and let the ST override. Also flag (do not decide) the
  resurrected Fool regaining its death-save.

---

## sailor — Sailor · BMR Townsfolk · P0:3 P1:3

today: `StatusEffects.kt:73` emits *"The Sailor can't die."* whenever the Sailor is alive — with no
  sobriety check. Since making the Sailor themselves drunk is roughly half of what this character
  does, the app tells the Storyteller to break the rules on a coin flip. Executions never consult it
  at all, and the Demon kill panel offers the kill regardless.
data:
  - characters.json: ok (`reminders: ["Drunk"]`, both night reminders present).
  - night_and_jinxes.json: ok — first 20, other 9, no jinxes.
  - night_guide.json: append to both entries *"The Sailor can't die while sober — this includes
    execution. If you make the Sailor drunk, they lose that protection until dusk."*
setup: none
identity: plain.
night.first / night.other: identical action, every night.
  gate: `Gates.aliveHolder`.
  action: `ChoosePlayers("sailor", min = 1, max = 1, constraints = [ALIVE, SELF_ALLOWED],
    sort = ALIVE_FIRST)` — dead seats **disabled** with *"dead — pick again"* (the wiki says the ST
    prompts for a new choice) — then a Storyteller chip pair choosing who is drunk: `[<target> is
    drunk]` (default when the target is a Townsfolk) / `[<Sailor> is drunk]` (default otherwise, per
    the wiki's guidance, and the default when the Sailor picks themselves).
  effects: `PlaceToken("sailor", "Drunk", <chosen>, exclusive = true)` + `Effect(DRUNK, until = DUSK,
    sourcePlayerId = sailor)`. If the Sailor **already** lacked their ability when the step runs
    (Poisoner got there first, Minstrel effect running): place nothing and say *"their ability does
    nothing tonight, and they can die."*
  deferred: none — the standing "can't die" is derived, never a token.
  info: none. The chosen player is told nothing.
  show: none.
  visibility: nothing.
day: `DayBriefing.Note(DAY_START, INFO, "sailor", "Bo is Sailor-drunk until dusk.")`; when the Sailor
  themselves is drunk, an ALERT: *"The Sailor is drunk today — they can be executed."*
death: `StandingRule("sailor")` emits `Effect(CANT_DIE, targetId = self, label = "")` — innate,
  derived, no token — evaluated at `killOutcome` **step 5**, blocking **every** cause: `DEMON_ABILITY`,
  `EVIL_ABILITY`, `GOOD_ABILITY`, `EXECUTION`, `STORYTELLER`. Sobriety is not a special case: the
  standing rule is not emitted while `Status.hasAbility(sailor)` is false, so a drunk, poisoned or
  Minstrel-drunk Sailor blocks nothing. `KillCause.ignoresProtection` (Assassin) still kills at step 1.
  For `EXECUTION` this feeds the shared dialog as a **forced** `SURVIVED` attributed to `"sailor"` —
  never the optional Pacifist offer — with *"Declare 'executed, but remains alive.' Do not say why."*
  `sailor:Drunk` → `Expiry.DUSK` (correct today); never DAWN.
ledger: `LedgerEntry(kind = CHOICE, sourceId = "sailor", targetIds = [T], text = "drunk: <name>")` so
  the step can show *"Last night: <name> was drunk."* No constraint — repeats are legal.
tests:
  - Given an alive sober Sailor · Then `killOutcome` returns `Blocked` for DEMON, GOOD_ABILITY,
    EXECUTION and STORYTELLER causes.
  - Given the Sailor holds `sailor:Drunk` (or `poisoner:Poisoned`, or a running Minstrel effect) ·
    Then nothing is blocked. **Fails today.**
  - Given the token on A on night 2 and B on night 3 · Then exactly one `sailor:Drunk` exists.
  - Given the token placed night 2 · Then it survives dawn, impairs all through day 2, and is gone at
    dusk.
  - Given a sober Sailor on the block · When the execution resolves · Then
    `ExecutionRecord(SURVIVED, preventedBy = "sailor")`, the Sailor is alive, no `DeathEvent`.
open: may the Sailor point at themselves? Not excluded by the text; allow it and default the drunk
  choice to the Sailor. Riot's "nominees die" against a sober Sailor — flag, do not decide.

---

## tealady — Tea Lady · BMR Townsfolk · P0:2 P1:4

today: the one derived check uses raw seat adjacency (`StatusEffects.kt:84`) instead of **alive**
  neighbours, producing both false negatives (a real alive neighbour is never protected, so the ST
  kills them) and false positives (a *dead* Minion between the Tea Lady and a good player wrongly
  switches the protection off); a drunk or poisoned Tea Lady still protects; only one `Can not die`
  label exists in the data; nothing places, moves or removes the tokens; and executions ignore it.
data:
  - characters.json: `"reminders": ["Can not die", "Can not die"]` — two, one per neighbour. One-line
    fix (`status-model §8` asks for the same).
  - night_and_jinxes.json: ok — no jinxes, correctly absent from both night orders.
  - night_guide.json: **no `tealady` entry**. Add one (day/standing section) carrying the How-to-Run
    text verbatim, especially *"skip past any dead neighbours"* and *"update these reminders
    immediately"* — the rule STs get wrong at the table is stated nowhere in the app.
setup: none
identity: registration-aware. A **Recluse** neighbour might register evil (protection off), a **Spy**
  neighbour might register good (protection stays on) — *even if dead*, though dead seats are skipped
  for neighbour-finding anyway. Both are Storyteller choices that must be **offered** per seat, not
  assumed, and stored as `LedgerEntry(kind = RULING, sourceId = "misregister")`.
night.first / night.other: no step, ever. Correctly absent from both order lists.
day: `DayBriefing.Note(DAY_START, INFO, "tealady", "Tea Lady protection: ON — <cw> and <ccw>.")`, and
  a transient line whenever it flips: *"Tea Lady protection turned OFF — <name> is now an alive
  neighbour."* The grimoire should show a shield glyph on the two protected seats.
death: `StandingRule("tealady")` emits `Effect(CANT_DIE, label = "Can not die")` on **each alive
  neighbour** — nearest alive player clockwise and counter-clockwise, skipping the dead — but only
  when the Tea Lady is alive, has her ability, both neighbours exist, both register good, and
  `cw != ccw` (dedupe the degenerate two-seat circle). Never on herself. Because it is a
  `StandingRule` it is re-evaluated on every query, so it tracks deaths, seat changes, alignment
  flips, character changes and Traveller arrivals *immediately* — which is exactly the wiki's
  requirement and removes the need for the auditor's `syncDerivedTokens`. **conflict:** the character
  file proposes storing/removing tokens on every mutation; `status-model §1` says innate effects are
  derived and tokens are their rendering — adopt the latter, keep the two data labels so the derived
  effects have something to draw.
  `killOutcome` **step 5** blocks every cause including `EXECUTION`; the Assassin still kills at step
  1. For `EXECUTION` it is a **forced** `SURVIVED` attributed to `"tealady"` (*"declare that the
  marked player is executed but remains alive — do not say why"*), never the Pacifist's optional
  choice. Effects vanish the instant the Tea Lady dies or is impaired (`endsWithSource`).
ledger: only the misregistration rulings; nothing else to remember.
tests:
  - Given `[TeaLady, deadTownsfolk, aliveTownsfolk, …]` · Then the *alive* player two seats away is
    protected, not the dead one. **Fails today.**
  - Given `[aliveGood, TeaLady, deadMinion, aliveGood]` · Then both alive neighbours are good and both
    are protected. **Fails today** — the dead Minion currently switches it off.
  - Given the Tea Lady is poisoned, or dead · Then nothing is protected and no token remains.
  - Given `[goodA, TeaLady, evilB, goodC]` · When `evilB` dies · Then `goodA` and `goodC` are both
    protected with no manual placement.
  - Given a marked neighbour on the block · Then `ExecutionRecord(SURVIVED, preventedBy = "tealady")`,
    the player is alive, and no `DeathEvent` exists.
open: none, other than the standing Recluse/Spy registration question, which is a Storyteller choice
  by design.
