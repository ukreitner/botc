# bmr-evil-and-outsiders — canonical requirement cards

12 characters · P0: 27 · P1: 48. Types are the mechanics specs' (`night-engine.md` §1-6,
`status-model.md` §1-6, `day-engine.md` §A-H, `records-and-memory.md` §A-I). `conflict:` marks
places where the character file proposed a different shape and the translation lost something.

## Group notes

1. **One root cause behind 9 of 12 cards.** `QuickResolutions` (`NightScreen.kt:462-525`) has
   branches only for snakecharmer/fanggu/professor; the `else` arm renders `DemonKillPanel` iff
   `team == DEMON && holder.alive`. So every Minion/Outsider here gets **no picker at all**, and
   every BMR Demon gets a **generic 1-target kill** that is wrong for all four of them. The whole
   group is unblocked by night-engine §3 `NightAction`/`NightEffect` + a generic `NightActionPanel`.
2. **Second root cause: the four Execute call sites** (`DayScreen.kt:111-114`, `:350-357`,
   `GameShell.kt:599-604`, `SeatSheet.kt:274`) call `kill` with no protection check. That is the
   Devil's Advocate P0 verbatim, and it also mis-arms Moonchild, Godfather, Mastermind and Zombuul.
   `GameActions.execute` + `Status.killOutcome` + `ExecutionRecord` fixes five cards at once.
3. **Third root cause: no cross-night memory.** DA "different to last night", Po's charge,
   Shabaloth's "chosen last night", Moonchild's alignment/impairment snapshot and the
   Lunatic×Mathematician jinx all need it. `LedgerEntry(kind = CHOICE)` + `Memory.lastChoice`
   covers every case. **conflict:** night-engine §3 proposes a *separate* `ChoiceRecord` +
   `GameState.choices` for exactly the same job, and records-and-memory §A explicitly rejects the
   `lastNightChoice: Map` that `devilsadvocate.md` proposed. Collapse to one type — the lead must
   pick, and `TargetConstraint.DIFFERENT_FROM_LAST_NIGHT` must read whichever wins.
4. **Token lifetimes.** Missing from the expiry tables: `("goon","Drunk")`→DUSK;
   `("tinker","Dead")`, `("moonchild","Dead")`, `("assassin","Dead")`, `("godfather","Dead")`,
   `("godfather","Died Today")`, `("zombuul","Died Today")`→DAWN. Must stay `Expiry.NEVER`:
   `("pukka","Poisoned")`, `("po","3 Attacks")`, `("shabaloth","Dead")` (cleared by the
   Shabaloth's own next step), `("shabaloth","Alive")`, `("zombuul","Registers as dead")`,
   `("assassin","No Ability")`. Pin each with a test — several are one line from being wrong.
5. **`data-accuracy.md` overrides the character files on labels.** Official Title Case wins:
   `Survives Execution` (devilsadvocate.md asked for the *lower*-case normalisation — reject),
   `3 Attacks`, `Died Today`, `No Ability`, `Has Ability`; renames `innkeeper` `Protected`→`Safe`
   and `tealady` `Can not die`→`Cannot Die`, which **breaks `StatusEffects.kt:66-69`'s
   label-substring protection matching** — every protection lookup in this group must key on
   `Effect.kind` / `sourceCharacterId`, never on label text.
6. **Pukka copy-count conflict — decide before implementing.** `pukka.md` specifies **one
   exclusive** `pukka:Poisoned`; `data-accuracy.md` D8 says the official token set is
   `["Poisoned","Poisoned","Dead"]`. Two copies is right: the new poison goes down *before* the old
   victim dies, so both exist for one step. `placeExclusiveReminder` is therefore the wrong call
   here and is the mechanism that destroys the pending victim today.
7. **Zombuul is the largest cross-cutting change in the group**: `Player.registersAlive` /
   `DeathEvent.registeredOnly` (status-model §6), `KillOutcome.RegistersDead` (§3 step 13),
   `WinCheck.aliveDemons` counting a secretly-alive Zombuul, dead players nominatable
   (`DayScreen.kt:146`), and a second death that `GameActions.kill` currently no-ops.
8. **The Goon needs a hook no mechanics spec has**: a reactive trigger evaluated on *every*
   resolved target selection, plus a `byStoryteller` flag so Grandmother/Fang Gu/Sweetheart
   ST-picks do not fire it. Neither `LedgerEntry` nor `ChoiceRecord` has that field. Add it.
9. **The Lunatic needs three things that do not exist**: (a) run another character's `NightAction`
   in *placebo* mode (all `NightEffect`s replaced by `PlaceToken("lunatic","Chosen")`);
   (b) a second bluff set (`bluffSets` from ux/setup-and-home) plus fake-Minion seats;
   (c) the believed Demon's gate (Zombuul's died-today, Po's charge, Pukka's night-1 wake).
   Building Po/Pukka/Shabaloth/Zombuul as declarative `NightAction`s gives (a) and (c) for free.
10. **`StepGate.Reduced` is load-bearing for four cards.** Exorcised Pukka: death yes, poison no.
    Exorcised Po: no kill, **no charge and no spend**. Exorcised Shabaloth: no kills, ST decides
    about regurgitation. Exorcised Zombuul: nothing. `Skip` would be wrong in three of the four.

---

## goon — Goon · BMR Outsider · P0:2 P1:4

today: never appears on the night sheet; its DRUNK token is permanent, and choosing the Goon as a
Demon target offers a "Goon dies" button for a kill the rules say must fail.
data:
  - characters.json: ok — `reminders:["Drunk"]`, both night reminders blank (official).
  - night_and_jinxes.json: no order entry (correct). Add two missing jinxes: `boffin`+`goon`
    "If the Demon has the Goon ability, they can't turn good due to this ability."; `pithag`+`goon`
    "If the Pit-Hag turns an evil player into the Goon, they can't turn good due to their own ability."
  - night_guide.json: **no `goon` entry at all**. Needs the new non-night channel
    (data-accuracy §5.2 `reference`/`day`) with the full how-to-run and two `shows` (`kind:"good"`,
    `kind:"evil"`, both already rendered at `NightScreen.kt:809-810`).
setup: none
identity: acting role `goon`, team stays OUTSIDER for every count (Godfather, Fang Gu), but
**registers** as the alignment last copied. Needs an absolute
`GameActions.setAlignment(state, playerId, evil)` to replace the `flipAlignment` toggle
(`GameActions.kt:129-130`); `assignCharacter` must clear `alignmentFlipped` (it does not today).
night.first / night.other:
  gate: **no step, ever** — correctly absent from both order lists. It is a reactive trigger inside
    the `NightAction.resolve` contract (night-engine §3): fires when a resolved action's target list
    contains the living Goon's seat, the chooser is a **player** (`byStoryteller == false`), and no
    `("goon","Drunk")` Effect is currently active (the once-per-night gate, exactly as worded).
  action: none of its own.
  effects: `Effect(kind = DRUNK, sourceCharacterId = "goon", sourcePlayerId = goonId,
    targetId = chooserId, until = Until.DUSK, label = "Drunk")` applied **before** the chooser's own
    effects resolve — so the chooser's ability then fails (Shabaloth example); plus
    `setAlignment(goon, evil = chooser.isEvil)`, applied **even when the chooser was already
    impaired and even when their ability fails**. Resolution contract step 2 (re-derive impairment
    between targets) already gives the Po/Shabaloth mid-sequence behaviour for free.
  deferred: none.
  info: none. show: `ShowCardTo(goon, AlignmentCard(evil))` — thumbs-up/down, card already exists.
  visibility: nothing to the chooser, Demon or Minions.
day: DayBriefing.Slot.DAY_START — "Kira is drunk today (chose the Goon last night). Sober at dusk."
  and "The Goon (Sam) is now evil — remember this for the Empath, Chef, Investigator, Fortune Teller."
death: a dead Goon's alignment is locked and the trigger stops. `killOutcome` needs no Goon branch,
  but every Demon/Assassin/Po/Shabaloth panel must show: "chose the Goon → attacker drunk, nobody
  dies, Goon flips" *before* the kill button.
ledger: `LedgerEntry(kind = RULING, sourceId = "goon", actorId = chooser, targetIds = [goonId],
  shown = "evil"/"good", cycle)` per flip — the Moonchild's "good **when chosen**" rule reads this
  history. **conflict:** neither `LedgerEntry` nor `ChoiceRecord` carries `byStoryteller`; the Goon
  trigger is unimplementable without it.
tests: Given `("goon","Drunk")` at night 2, when NIGHT→DAY→NIGHT, then it is gone and the holder is
  not impaired. · Given a Shabaloth choosing [Goon, Gossip], when resolved, then the Shabaloth is
  drunk and neither target dies. · Given the Goon already drunked the Monk tonight, when the Imp
  also chooses it, then no second DRUNK is placed. · Given a Grandmother grandchild = Goon
  (byStoryteller), then nothing fires. · Given a dead Goon chosen by a Monk, then no flip, no token.
open: does the **alignment** flip also gate on "1st chooser", or only the drunkenness? (Wiki gates
  only the token; the clause says "the 1st player" — pin with a rules source.) Does
  `("goon","Drunk")` end if the Goon dies mid-night (`endsWithSource`)? Assassin×Goon: the wiki
  contradicts itself — implement "no death, ability spent, Goon flips" and show both readings.

---

## lunatic — Lunatic · BMR Outsider · P0:3 P1:4  *(user-reported)*

today: setup picks a believed Demon and the reveal works; after that the ST improvises. The step
renders **no picker of any kind** (Outsider ⇒ no `DemonKillPanel`, `InfoCalc.supports` false), there
is one global bluff list that cannot contain in-play characters, and the fake Minions are recorded
nowhere. The Demon is told about the Lunatic only via a sentence appended to the Demon's step text.
data:
  - characters.json: `lunatic.reminders` `["Attack 1","Attack 2","Attack 3"]` →
    `["Chosen","Chosen","Chosen"]` (official label + copy count, data-accuracy D8/§6). Add a
    non-official `"Fake Minion"` label **and** a `"Charged"` label (Po placebo) — or hold both in
    state instead of tokens; flag the divergence from the official token set. `firstNightReminder`
    is missing **THESE ARE YOUR MINIONS** and **THESE CHARACTERS ARE NOT IN PLAY** (data-accuracy D16).
  - night_and_jinxes.json: firstNight 16 and otherNight 31 are **correct** (before every Demon).
    `mathematician`+`lunatic` text is stale: "a different player(s)" → "a different player".
  - night_guide.json: set the "YOU ARE" show's `token` to a new `"shown"` kind meaning the seat's
    `shownCharacterId`; **move** the "Show the real Demon / THIS PLAYER IS THE LUNATIC" card off the
    Lunatic's entry (the Demon is not awake there) onto `DEMON_INFO` and each Demon's `other`; add a
    THESE ARE YOUR MINIONS card and a Lunatic-bluffs card to `lunatic.first`.
setup: `lunatic-demon` · CHOOSE_CHARACTER · "Which Demon token does the Lunatic see?" · **in-play
  Demon first and pre-selected** (the two-token variant is opt-in) · `validateSetupState` already
  rejects a non-Demon. `lunatic-bluffs` · CHOOSE_CHARACTER ×3 · all good characters on the script,
  **in-play allowed and at least one preferred**, never the believed Demon · stored in
  `bluffSets["lunatic"]`. `lunatic-minions` (7+ only) · CHOOSE_PLAYERS · count = real Minions in play.
identity: shown = `shownCharacterId` (a Demon), acting = `lunatic`, registers = **good Outsider**
  (`nightRoleId` correctly returns `"lunatic"`, unlike the Drunk/Marionette). Standing
  `Effect(kind = NO_ABILITY, endsWithSource = false)` per status-model §1.
night.first:
  gate: `Gates.aliveHolder`; `perHolderStep`. Minion/bluff sub-steps additionally gated on
    `minPlayers(7, countTravellers = ?)` — the same Teensyville predicate as `MINION_INFO`.
  action: composite. (1) `ChoosePlayers("lunatic", "Point to N players as their Minions",
    min = max = realMinionCount)` → `PlaceToken("lunatic","Fake Minion", Ref.ALL_TARGETS,
    maxCopies = 3, exclusive = false)`. (2) the Lunatic's own 3 bluffs. (3) **only if the believed
    Demon has a non-blank `firstNightReminder`** (Pukka on BMR), that Demon's `NightAction` in
    **placebo mode** = every `NightEffect` replaced by `PlaceToken("lunatic","Chosen")` +
    `RecordChoice`. No kills, no poison, no protection change, ever.
  info: `ShowInfo` — THESE ARE YOUR MINIONS + N seats; THESE CHARACTERS ARE NOT IN PLAY + 3 bluffs
    that **may be in play** (that is the tell; `BluffsSheet.kt:44` forbids it today).
  show: `YOU ARE` + believed-Demon token; MINIONS card; bluffs card.
  visibility: on `DEMON_INFO`, the real Demon gets `THIS PLAYER IS` + the Lunatic token + a point at
    the Lunatic seat — **and, on night 1 too**, the Lunatic's choices when the believed Demon acts
    on night 1 (`NightOrder.kt:157` is gated on `!isFirstNight`, which is wrong for Pukka).
night.other:
  gate: `Gates.aliveHolder` **AND the believed Demon's own gate**: `zombuul` ⇒
    `Gates.someoneDiedToday(false)` — otherwise `StepGate.Skip("Somebody died today — the Zombuul
    would not wake")`, non-blocking; every other Demon ⇒ Fire.
  action: the believed Demon's action, in placebo mode: `imp`/`pukka`/`zombuul`/fallback = 1 target
    (self legal for the Imp, "fake the star pass"); `shabaloth` = 2; `po` = `YesNo` then 0/1, or
    **3** when `Memory.lastChoice("lunatic", holder)?.targetIds.isEmpty()`, tracked by a
    non-expiring `("lunatic","Charged")` token; `pukka` uses poison wording, not kill wording.
  effects: `PlaceToken("lunatic","Chosen", maxCopies = 3)` on each pick + `RecordChoice`. A standing
    banner: "Nobody dies from this. The Lunatic's choices have no effect."
  deferred: none. expiry: `("lunatic","Chosen")` → `Expiry.DAWN`; `("lunatic","Fake Minion")` and
    `("lunatic","Charged")` → `Expiry.NEVER`.
  visibility: **the P1 fix** — on every Demon's step render a Lunatic briefing block above the kill
    panel: `THIS PLAYER IS` + Lunatic token, point at the Lunatic, then a card per chosen player,
    then a `[✓ Demon has been shown]` tick that is part of the dawn guard. Dead Lunatic ⇒ "no fake
    attack tonight". Chose nobody ⇒ "tell the Demon so".
day: none. death: dead Lunatic ⇒ `Skip("dead")`, non-blocking, and the Demon block hides the list.
ledger: `LedgerEntry(kind = CHOICE, sourceId = "lunatic", actorId, targetIds, impaired)` every night
  — powers the Po charge, the Demon briefing and the Mathematician jinx
  (`lunaticTargets(n) != demonTargets(n)` ⇒ +1 abnormal). Fake-Minion seats and the bluff set must
  survive the whole game (`bluffSets`, not the single global `demonBluffIds`).
tests: Given believed `po` and an empty choice on night 2, when the night-3 step is built, then it
  demands **3** targets. · Given believed `pukka`, then the **first-night** step has a choice panel.
  · Given believed `zombuul` and a day-2 execution, then the night-3 step is `Skip` and does not
  block dawn. · Given the Lunatic chose Cora, then Cora is alive, no `DeathEvent`, no impairing
  Effect, exactly one `("lunatic","Chosen")`. · Given `bluffSets["demon"]` and `bluffSets["lunatic"]`
  containing an in-play Mayor, then both round-trip and setup validation raises nothing.
open: does the fake-Minion count exclude the Marionette (pin either way)? Is "Fake Minion" an
  acceptable non-official token, or should the seats live in state only? Teensyville threshold —
  count Travellers or not (night-engine §2 says surface the choice, never silently pick).

---

## moonchild — Moonchild · BMR Outsider · P0:0 P1:5

today: one passive `deathNotes` line on two of four death paths; the public choice can be recorded
nowhere; the night step blocks the dawn guard every night for a living Moonchild.
data:
  - characters.json: ok. night_and_jinxes.json: otherNight 70 correct, no first night — ok, no jinxes.
  - night_guide.json: append — the app now snapshots the choice, the target's alignment and the
    Moonchild's impairment at the moment of choosing; **the Monk does not protect against this
    curse** (it is not a Demon kill) while the Sailor, Fool, Tea Lady and Innkeeper do.
setup: none. identity: plain (`keepsAbilityWhenDead` set — status-model §2 lists `moonchild`).
night.first: none (correct).
night.other:
  gate: `Gates.actsWhileDead` + "an unresolved Moonchild curse exists". No curse ⇒
    `StepGate.Skip("The Moonchild is alive — nothing to do")`, auto-ticked, **non-blocking**.
  action: none at night — the target was chosen in daylight. The step is a resolution panel.
  effects: if `targetWasGood && !moonchildImpairedAtChoice` → `Attack(target, cause =
    KillCause(GOOD_ABILITY, "moonchild"), respectProtection = true)` + `PlaceToken("moonchild","Dead")`.
    Otherwise auto-resolve with the reason. `killOutcome` gives the right answer for free:
    `SAFE_FROM_DEMON` (Monk/Soldier) does **not** match `GOOD_ABILITY`, while `CANT_DIE`
    (Sailor/Tea Lady), `CANT_DIE_TONIGHT` (Innkeeper) and the Fool's `Spends` do.
  deferred: announced at dawn with the other deaths, cause unstated.
  expiry: add `("moonchild","Dead")` → `Expiry.DAWN`.
  info: none. show: none. visibility: the choice is public; nothing is hidden.
day: **this is where the character lives.** On any real `DeathEvent` for the Moonchild seat, queue
  `Prompt(at = PromptWhen.NOW, characterId = "moonchild", kind = CHOOSE_PLAYER, subjectPlayerId =
  moonchildId, options = alive seats, title = "Jasper died — he publicly chooses 1 alive player
  now")`; if the death was at night, `at = DAWN` instead (that is when the player learns). Standing
  DayBriefing.DAY_START line while the curse is armed and unrecorded. Recording the choice snapshots
  **target alignment** and **Moonchild impairment right then** — the Goon rule ("good *when
  chosen*") and the drunk/poisoned rule both depend on it.
death: armed by `DeathEvent` only — a `SURVIVED` `ExecutionRecord` (Devil's Advocate, Pacifist,
  Fool, Sailor, Tea Lady) must **not** arm it, and a Zombuul-style `registeredOnly` death must not
  either. Re-arms after `resurrect` (a new `DeathEvent` ⇒ a new curse). status-model's on-death
  table already has the row (`moonchild` → NOW, publicly choose 1 alive player).
ledger: `LedgerEntry(kind = STATEMENT, sourceId = "moonchild", actorId = moonchildId,
  targetIds = [target], impaired = <snapshot>, shown = "target good"/"target evil",
  resolvedCycle)`. **conflict:** `moonchild.md` proposes a bespoke `MoonchildCurse` record;
  `LedgerEntry` has an `impaired` field for the *actor* but no field for the *target's alignment at
  the time* — encoding it in `shown` loses type safety. If the lead prefers, add
  `Effect(kind = MARKER, sourceCharacterId = "moonchild", untilEvent = "moonchildResolves")` instead.
tests: Given a Moonchild killed by execute / Demon / Godfather / seat sheet, then each arms exactly
  one unresolved curse. · Given a Moonchild holding `SURVIVES_EXECUTION` who is executed, then no
  curse is armed. · Given a Goon that was **good** when named and flips evil that night, then the
  Goon dies. · Given the Moonchild sober when choosing and poisoned later, then the target still
  dies; converse leaves them alive. · Given the target holds `("monk","Safe")`, then it still dies;
  given `("innkeeper","Safe")`, then it does not.
open: none.

---

## tinker — Tinker · BMR Outsider · P0:0 P1:4

today: a nightly blocking checklist row saying "The Tinker might die", whose only tool is a `Dead`
tray chip that places a token and **does not kill**. Zero engine code. No day-time affordance.
data:
  - characters.json: ok (`reminders:["Dead"]`). night_and_jinxes.json: otherNight 69 correct, no
    first night, no jinxes — ok.
  - night_guide.json: append — "Killing the Tinker during the **day** counts as an Outsider dying
    today and arms the Godfather; killing them at night does not. Never kill the Tinker when it
    would end the game."
setup: none. identity: plain.
night.first: none. night.other:
  gate: always rendered, **never blocking** — `StepGate` is `Fire` but the step is auto-ticked
    (the Tinker never wakes). Dead Tinker ⇒ `Skip("already dead")`.
  action: not a target picker — a single `YesNo("tinker", "Do you kill the Tinker tonight?",
    yes = "Sam dies tonight", no = "Leave Sam alive")`.
  effects: on yes, `Attack(Ref.SOURCE, cause = KillCause(GOOD_ABILITY, "tinker"),
    respectProtection = true)` + `PlaceToken("tinker","Dead")`.
    **conflict:** `tinker.md` proposes `DeathCause.OTHER_NIGHT_DEATH`/`STORYTELLER`;
    status-model's cause table assigns `tinker` → `GOOD_ABILITY`, which is the one that produces the
    right protection answer (Tea Lady/Innkeeper/Sailor/Fool block; **Monk and Soldier do not**).
    Use `GOOD_ABILITY`; `STORYTELLER` would wrongly bypass nothing and read as ST fiat in the log.
  deferred: announced at dawn with the others, "do not say how".
  expiry: add `("tinker","Dead")` → `Expiry.DAWN`.
  info: none. show: none. visibility: nothing to anyone, ever; the cause is never announced.
day: a persistent **Storyteller levers** card on the Day tab whenever a living Tinker is in play:
  `[ Sam dies now ]` → `killOutcome(...)` → `DeathEvent(atNight = false)`. Because a daytime
  Outsider death arms the Godfather, it must write the Godfather's `Died Today` token and a
  DayBriefing/DuskBriefing line "Sam (Tinker) died today — the Godfather kills tonight."
death: **hard blocks, not warnings** — "The Tinker cannot die from their ability while protected
  from death": any active `CANT_DIE`, `CANT_DIE_TONIGHT`, or Fool `Spends` disables the lever with
  the reason (override still available; the ST always wins). `SAFE_FROM_DEMON` (Monk, Soldier) does
  **not** block. `Status.impairment(tinker)` non-empty ⇒ also disabled (no ability). Soft warning
  when `WinCheck.check(stateAfterKill) != null`: "This would end the game — the rules recommend
  against it." Never a Demon kill ⇒ the Sage and Grandmother do not fire.
ledger: `LedgerEntry(kind = RULING, sourceId = "tinker", targetIds = [tinkerId], text = "ST killed
  the Tinker")` — the one place a Storyteller-fiat death should be explicable after the game.
tests: Given a Tinker at night 3 with the step unticked, then the dawn guard does not report it. ·
  Given `addReminder(tinker, ("tinker","Dead"))`, then the seat is still alive and there is no
  `DeathEvent` (documents today's trap). · Given a day-2 Tinker death and a living Godfather, then
  the Godfather is armed for night 3; given a **night-3** Tinker death, then it is not. · Given
  `("innkeeper","Safe")` on the Tinker, then the lever is disabled; given `("monk","Safe")`, enabled.
  · Given 3 alive (Demon, Tinker, one Townsfolk), then the lever shows the endgame warning.
open: none (the drunk/poisoned exemption is derived, not quoted — the app's own guide already
  asserts it, so ship it and mark it a ruling).

---

## assassin — Assassin · BMR Minion · P0:1 P1:4

today: no picker; the `Dead` tray chip places a token that does not kill; and the one path that
does kill (`SeatSheet.requestKill`) opens a dialog whose dismiss button reads **"Death prevented"**
— the app argues against the one ability in the game that ignores all protection.
data:
  - characters.json: `"No ability"` → `"No Ability"` (data-accuracy §6). Otherwise ok.
  - night_and_jinxes.json: otherNight 55, **no first-night entry** — correct (the asterisk). No jinxes.
  - night_guide.json: best entry in the group; append "The app skips this step once No Ability is
    placed, and marks the ability spent even when the Assassin is drunk or poisoned." The current
    Zombuul claim ("even kills the Zombuul on its first death") is **not** quoted on the wiki —
    label it a Storyteller ruling.
setup: none. identity: plain.
night.first: none (correct — pin it).
night.other:
  gate: `Gates.aliveHolder` AND `Gates.notSpent("assassin", label = "No Ability")` — reading
    `Character.spentLabel` so the placed token and the gate can never drift. Otherwise
    `Skip("already used" / "dead")`, auto-ticked, non-blocking.
  action: `ChoosePlayers("assassin", "The Assassin shakes their head, or points at a player",
    min = 0, max = 1, constraints = [ANY_LIVING_STATE, SELF_ALLOWED], sort = ALIVE_FIRST,
    allowNone = true, noneLabel = "They shook their head 'no'")`.
  effects: on a target — `Attack(Ref.TARGET, cause = KillCause(EVIL_ABILITY, "assassin",
    **ignoresProtection = true**))` (step 1 of `killOutcome`: nothing else is even evaluated) +
    `PlaceToken("assassin","Dead", Ref.TARGET)` + `MarkSpent("assassin")` + `RecordChoice`.
    **When the Assassin is impaired: nobody dies and `MarkSpent` still fires** — the single most
    valuable automation on this card. `onNone`: `RecordChoice` only; **not** spent.
  deferred: announced at dawn with the other deaths, cause unstated.
  expiry: add `("assassin","Dead")` → `Expiry.DAWN`; `("assassin","No Ability")` → `Expiry.NEVER`.
    Note `resurrect(clearSpentMarks = true)` **removes** the spent mark (Glossary) — correct.
  info: none. show: none. visibility: nothing to the Demon or the Assassin.
day: none.
death: this kill is a night death but **not** a Demon kill: the Sage and Grandmother do not fire; a
  Ravenkeeper killed this way **does** wake. `Status.deathNotes`/the panel must render every
  protection line **struck through** under the header "The Assassin ignores all protection", and the
  confirm sheet must lose its "Death prevented" option on this path.
ledger: `LedgerEntry(kind = CHOICE, sourceId = "assassin", targetIds, impaired)`, including the
  empty-target "shook their head" case — that record is what answers "have they used it yet".
tests: Given a target holding `("innkeeper","Safe")`, Soldier and `("monk","Safe")`, when the
  Assassin resolves, then the target is dead with one `DeathEvent`. · Given a poisoned Assassin
  pointing at Alice, then Alice is alive **and** the Assassin holds `("assassin","No Ability")`. ·
  Given a head-shake, then no spent token and night 3 is still armed. · Given the spent token at
  night 4 with the step unticked, then the dawn guard does not report it. · When NIGHT→DAY, then
  `("assassin","Dead")` is gone and `No Ability` remains.
open: the Goon×Assassin contradiction (see goon card) — implement "no death, ability spent, Goon
  flips" and surface both wiki readings.

---

## devilsadvocate — Devil's Advocate · BMR Minion · P0:2 P1:3  *(user-reported)*

today: night 1 the step reads correctly and the tray places the token. Through day 1 it sits on the
seat. Executing that player **from the Day tab kills them**. At dusk the token silently vanishes, so
on night 2 nothing records or blocks last night's pick.
data:
  - characters.json: `"Survives execution"` → `"Survives Execution"` (official Title Case;
    data-accuracy §6 explicitly overrides `devilsadvocate.md`'s P2-8, which asked to normalise the
    other way). `GameActions.kt:236` must change in the same commit — `clearEphemeral` compares
    `(sourceId, label)` **case-sensitively**.
  - night_and_jinxes.json: firstNight 34, otherNight 22 — **correct**. No jinxes (wiki lists none).
  - night_guide.json: `:334,338` "Survives Execution" is already the right case — keep, and align
    `characters.json` to it. Append: "The app removes the old token at dusk and blocks last night's
    player in the picker." `shows: []` stays — the DA shows nothing to anyone.
setup: none. identity: plain.
night.first / night.other (identical shape — the DA acts every night):
  gate: `Gates.aliveHolder`; `Skip("dead — the token was already removed at dusk")` otherwise.
  action: `ChoosePlayers("devilsadvocate", "They point at a living player", min = 1, max = 1,
    constraints = [ALIVE, SELF_ALLOWED, **DIFFERENT_FROM_LAST_NIGHT**], sort = ALIVE_FIRST,
    allowNone = true, noneLabel = "They chose nobody / were not woken")`. Additionally exclude a
    **Zombuul that registers as dead** (`!Player.registersAlive`) — the wiki says so explicitly;
    this is a constraint the enum does not yet have (`NOT_REGISTERS_DEAD`).
  effects: `PlaceToken("devilsadvocate","Survives Execution", Ref.TARGET, maxCopies = 1,
    exclusive = true)` → an `Effect(kind = SURVIVES_EXECUTION, sourceCharacterId =
    "devilsadvocate", sourcePlayerId = daId, until = Until.DUSK, endsWithSource = true)`;
    plus `RecordChoice` **always**, including when impaired and when nobody was chosen.
    **Impaired DA: record the choice, place nothing** (recommended in the audit) — `endsWithSource`
    would also void it automatically, but not placing keeps the execution flow honest.
  deferred: consumed (or wasted) at tomorrow's execution. Nothing at dawn.
  expiry: `("devilsadvocate","Survives Execution")` → `Expiry.DUSK` (already correct). The **choice
    record must not expire** — that is the whole fix for "different to last night", and it is why
    the token cannot be the memory (it is deleted at DAY→NIGHT, *before* the DA's other-night step
    at index 22).
  info: none. show: none. visibility: nothing to the Demon or Minions.
day: **the P0.** All four Execute call sites route through `GameActions.execute(...)`, which calls
  `Status.killOutcome`; step 8 (`SURVIVES_EXECUTION && kind == EXECUTION`) returns `Blocked` with
  the exact announce string *"Say: 'Ana was executed… and remains alive.' Do not say why."*
  `execute` writes an `ExecutionRecord(outcome = SURVIVED, preventedBy = "devilsadvocate")`
  **before** any kill, so: the day's execution is spent, `nominationsClosedOnDay` is set, and the
  Undertaker (`executionDeathToday` = null), Godfather (no `DeathEvent`), Saint, Minstrel and
  Mastermind-entry all correctly do nothing. DayBriefing.Slot.DAY_START: "Ben survives execution
  today (Devil's Advocate)." Only emit the line when the DA seat is alive and was unimpaired at
  choice time. `executionConsequences(...)` must list it in the confirmation sheet.
death: no on-death trigger. Prevented executions must not arm the Moonchild or the Godfather, and
  must not lose the game for a Saint — all of which follow from writing `SURVIVED` instead of a kill.
ledger: `LedgerEntry(kind = CHOICE, sourceId = "devilsadvocate", actorId, targetIds, impaired)`;
  `Memory.forbiddenTargets(state, "devilsadvocate")` drives the disabled chip labelled *"chosen last
  night — not allowed"*. **conflict:** `devilsadvocate.md`'s `lastNightChoice: Map<String, Long>` is
  explicitly rejected by records-and-memory §B (cannot answer "two nights ago", breaks with two
  holders, needs its own undo discipline).
tests: Given `SURVIVES_EXECUTION` on Alice and phase DAY, when `execute(alice)`, then Alice is alive,
  no `DeathEvent`, and `executions.last() == (SURVIVED, preventedBy = "devilsadvocate")`. · Given
  the same on an Outsider, then the Godfather is not armed; on a Saint, then `WinCheck` returns no
  evil win. · Given a night-1 pick of Alice, when DAY→NIGHT, then no seat holds the token **and**
  `Memory.lastChoice("devilsadvocate")?.targetIds == [alice]`. · Given the token on Alice, when
  placed on Bob **by either the tray or the seat sheet**, then exactly one seat holds it
  (`SeatSheet.kt:113` calls the non-exclusive `addReminder` today — the likely cause of the user's
  "DA wasn't automatically removed"). · Given a Zombuul registering as dead, then it is not a legal
  target.
open: none.

---

## godfather — Godfather · BMR Minion · P0:2 P1:4

today: setup works; everything else is manual. `StatusEffects.kt:116-118` announces "Godfather kills
tonight because an Outsider died today" for **any** Outsider death including night deaths — a
rules-breaking false positive that manufactures an extra kill. The first-night info is not computed.
data:
  - characters.json: `"Died today"` → `"Died Today"` (data-accuracy §6). `setup: true` is correct.
  - night_and_jinxes.json: firstNight 32, otherNight 56 — correct. `heretic`+`godfather` jinx present.
  - night_guide.json: `:344` first-night `shows` should become a **computed** card per in-play
    Outsider (keep `token:"pick"` as the misregistration override); `:350` replace "(execution or any
    other daytime death)" with "any death during the **day** — execution, a Storyteller-killed
    Tinker, a Witch curse. Deaths at **night** do not count." + "Only one kill, even if two
    Outsiders died."
setup: `godfather-outsider` · CHOICE · "[-1 or +1 Outsider]" · candidates = {-1, +1} ·
  already implemented and tested (`Setup.kt:121-232`, `GameActionsTest.kt:291-320`) — **works**.
identity: plain.
night.first:
  gate: `Gates.aliveHolder`. action: `ShowInfo("godfather", targetsNeeded = 0)`.
  info: new `InfoCalc` branch — the distinct **Outsider characters** in play (not players, not
    seats), answer type = list of character ids, legal range 0..N. Caveats: impaired ⇒ show a false
    set; a Recluse may register as a Minion/Demon (you may omit or substitute); a Spy may register
    as an Outsider (you may add them); the Drunk/Marionette **are** Outsiders and showing them is a
    large tell but is correct.
  show: one `ShowCardTo(godfather, CharacterCard("THIS OUTSIDER IS IN PLAY", id))` per Outsider,
    pre-filled, plus "show all in sequence". New card kind: a pre-filled multi-token sequence.
  visibility: the Godfather only.
night.other:
  gate: `Gates.aliveHolder` AND `dayDeathsToday().any { teamAtDeath == OUTSIDER }` — computed, never
    remembered, from `DeathEvent(cycle == state.cycle - 1, atNight = false, !resurrected,
    teamAtDeath = OUTSIDER)`. Otherwise `Skip("No Outsider died today")` / `Skip("the Godfather is
    dead")`, auto-ticked, non-blocking. `EXECUTION` and `STORYTELLER` day deaths both qualify.
  action: `ChoosePlayers("godfather", "They point at any player", min = 1, max = 1,
    constraints = [ANY_LIVING_STATE, SELF_ALLOWED], sort = ALIVE_FIRST, allowNone = true)` —
    **exactly one kill even if two Outsiders died**; say so on the panel.
  effects: `Attack(Ref.TARGET, cause = KillCause(EVIL_ABILITY, "godfather"),
    respectProtection = true)` + `PlaceToken("godfather","Dead", Ref.TARGET)` + `RecordChoice`.
    `killOutcome` gets the protection semantics right by kind: `SAFE_FROM_DEMON` (Monk, Soldier)
    does **not** block an `EVIL_ABILITY`; `CANT_DIE`, `CANT_DIE_TONIGHT` and the Fool do. Impaired
    Godfather ⇒ "let them point, nobody dies".
  deferred: announced at dawn with the others, cause unstated.
  expiry: add `("godfather","Died Today")` and `("godfather","Dead")` → `Expiry.DAWN`.
  info: none. visibility: nothing to the Demon or the other Minions.
day: at dusk, place `("godfather","Died Today")` on each qualifying seat and raise a
  DuskBriefing line "An Outsider died today (Sam, Tinker) — the Godfather kills tonight."
  A **`SURVIVED` execution must not arm it** — key off `DeathEvent`, never the Execute button.
death: a Recluse or Spy dying during the day needs a one-tap misregistration ruling recorded on the
  `DeathEvent` (`registersAsTeam: Team?`) so the arming computation is deterministic and the same
  answer is reused later (`Memory.ruling`).
ledger: `LedgerEntry(kind = RULING, sourceId = "misregister", ...)` for the Recluse/Spy call;
  `LedgerEntry(kind = CHOICE, sourceId = "godfather", targetIds)` for the kill.
tests: Given an Outsider **executed** on day 2, then the night-3 step is armed and the seat holds
  `Died Today`; given an Outsider killed by the Demon on night 3, then the night-4 step is **not**
  armed. · Given `kill(tinker, STORYTELLER)` while phase == DAY, then armed. · Given an Outsider
  with `SURVIVES_EXECUTION` who is executed, then no `DeathEvent` and not armed. · Given two
  Outsider deaths on day 2, then exactly one `DeathEvent` is added on night 3. · Given a Recluse and
  a Drunk in play, then the first-night info names both characters and no player names. · Given the
  target holds `("monk","Safe")` and is the Soldier, then it dies; given `("innkeeper","Safe")`, it
  lives.
open: whether protection stops the Godfather is derived, not quoted (high confidence).

---

## mastermind — Mastermind · BMR Minion · P0:2 P1:3

today: the extra-day **resolution** works (`WinCheck.kt:28-49` + `mastermindDayActive`). The **entry
conditions** are wrong (offered when the Demon died any way at all, and when the Mastermind is
dead), the extra day never ends by itself, and there is no procedure text anywhere in the app.
data:
  - characters.json: ok (`reminders: []` is correct — the Mastermind has no tokens).
  - night_and_jinxes.json: no order entry (correct). **Four jinxes missing**: `alchemist`+
    `mastermind`, `alhadikhia`+`mastermind`, `lleech`+`mastermind`, `mastermind`+`vigormortis`
    (exact official wording is already transcribed in data-accuracy §7). The Vigormortis one is a
    genuine rules change that is otherwise invisible.
  - night_guide.json: **no `mastermind` entry**. Needs the new non-night channel (data-accuracy
    §5.2): shroud the Demon normally, say nothing, play one more **night** (everything works; the
    dead Demon cannot attack) and one more day; good player executed ⇒ evil wins; evil player or
    no execution ⇒ good wins; a Scarlet Woman who catches the Demon takes precedence.
setup: none. identity: plain (must be **alive** for the ability to function).
night.first / night.other: none — the Mastermind never wakes. On the extra night, add a night-sheet
  header "Mastermind: the Demon is dead. Run this night normally — the Demon does **not** attack.
  Tomorrow is the last day." and replace the dead Demon step's "usually skip" with that line.
day / win:
  entry (both P0s): the advisory and the "Play the Mastermind day" button require **all** of —
    a **living** Mastermind (today `inPlayIds` has no aliveness filter); the last non-resurrected
    Demon `DeathEvent` has `cause.kind == EXECUTION`; and **no** Scarlet Woman catch (living SW with
    ≥5 alive non-Travellers ⇒ the game never ended, so suppress the whole advisory and say so).
    Otherwise emit an *informative* line, not a button.
  state: replace `mastermindDayActive: Boolean` with
    `MastermindExtra(demonDeathEventId: Long, extraDay: Int = state.cycle + 1)`, keeping the boolean
    as a deprecated computed alias for save compatibility. **conflict:** day-engine defines no home
    for this; it belongs on `GameState` next to `nominationsClosedOnDay`, and `WinCheck.duskCheck`
    must gain an ordered rule for it.
  exits, both automatic and both keyed on `ExecutionRecord`, not `DeathEvent`:
    (a) an execution on `extraDay` ⇒ `Advisory(goodWins = !wasEvilAtExecution, ruleId =
    "mastermind-day")`, read from the record's **snapshot** (`wasEvilAtExecution`), never live
    state; a `SURVIVED` execution **still counts** ("if a player is then executed, their team
    loses") — this is exactly why the record must exist independently of the death.
    (b) `duskCheck` on `extraDay` with `executionSpent(state) == false` ⇒
    `Advisory(goodWins = true, reason = "Mastermind day ended with no execution", blocking = true)`.
    Both exits clear `mastermindExtra`.
  banner: move inside the `Scaffold` (today a bare `Box` at `padding(top = 100.dp)`, over whichever
    tab is open on a phone) and make it phase-aware: `cycle < extraDay` ⇒ "MASTERMIND — the Demon is
    dead. One more night and day."; `cycle == extraDay && phase == DAY` ⇒ "MASTERMIND DAY — whoever
    is executed, their team loses. No execution = good wins." Add a "×" that clears the state.
  WinCheck: the extra day overrides the ≤2-alive ending (already true via the early return — pin it).
death: no on-death trigger of its own. The Zombuul's first "death" must not count — only its second
  (`DeathEvent.registeredOnly == false`).
ledger: `LedgerEntry(kind = ANNOUNCE, sourceId = "mastermind")` for the two declarations, so the
  game log explains why play continued past a dead Demon.
tests: Given the Imp killed by an Assassin at night with a living Mastermind, then no Mastermind
  caution. · Given a Mastermind executed on day 2 and the Imp executed on day 3, then no caution. ·
  Given 6 alive with a living Scarlet Woman and the Imp executed, then the SW path, not a Mastermind
  button. · Given `extraDay == 3` and no execution on day 3, then advancing to night 4 yields
  `goodWins = true` (returns null today). · Given an evil nominee holding `SURVIVES_EXECUTION`
  executed on the extra day, then `goodWins == true` even with no `DeathEvent`.
open: the Zombuul double-execution rule ("triggers only on the second death") depends on how the
  first is recorded — it is deterministic only once the Zombuul card's `registeredOnly` lands.

---

## po — Po · BMR Demon · P0:3 P1:3

today: the generic `DemonKillPanel`. There is **no way to say "the Po chose no-one"** that has any
effect, the impaired banner tells the ST to press "No kill" (which is the *opposite* rule), and a
charged Po gets no 3-kill flow — the `3 attacks` token is hand-placed and never removed.
data:
  - characters.json: `reminders` `["Dead","3 attacks"]` → `["Dead","Dead","Dead","3 Attacks"]`
    (three victims per charged night, official Title Case; data-accuracy D8/§6 — note the case
    change from `po.md`'s "3 attacks").
  - night_and_jinxes.json: otherNight 41 (after Shabaloth, before Fang Gu), **no first night** —
    both correct. No jinxes — correct.
  - night_guide.json: append — "The Po only charges if it chose **no-one**; a kill that fails
    (protected target, drunk Po) is still a choice and does **not** charge. If the Exorcist chose the
    Po, it does not act and does not charge. Resolve the three attacks **in order** — if an attack
    makes the Po drunk (the Goon), the later attacks fail." Add a `first` entry: "The Po does not act
    on the first night, and this does not count as choosing no-one."
setup: none. identity: plain Demon (BMR has no star pass — a Po that kills itself simply dies; only
  the Scarlet Woman catches the mantle at 5+ alive).
night.first: none (correct — pin it).
night.other:
  gate: `Gates.aliveHolder`; `notExorcised` ⇒ **`StepGate.Reduced(allow = {})`**, not `Skip`: the Po
    does not act, does **not** charge, and does **not** spend an existing charge.
  action: two shapes selected by `("po","3 Attacks")`.
    Uncharged: `YesNo("po", "What did the Po do?", yes = "Pointed at a player", no = "Shook their
    head — no-one")`; on yes → `ChoosePlayers(min = 1, max = 1, constraints = [ANY_LIVING_STATE,
    SELF_ALLOWED], sort = ALIVE_FIRST)`.
    Charged: `ChoosePlayers(min = 3, max = 3, ...)` picked **one at a time, in order**; duplicates
    warned, not forbidden; confirm disabled below 3 ("A Po must choose three; they cannot choose
    no-one again").
  effects: head-shake → `PlaceToken("po","3 Attacks", Ref.SOURCE)`, **even while drunk or poisoned**
    (explicit wiki rule). Pointed → `Attack(Ref.TARGET, cause = KillCause(DEMON_ABILITY, "po"))`,
    and **no charge either way** — a failed kill is still a choice. Charged night →
    `perTarget = [Attack(Ref.TARGET), PlaceToken("po","Dead", Ref.TARGET, maxCopies = 3)]` with the
    resolution contract's per-target re-derivation (§3.2), then `RemoveToken("po","3 Attacks")`
    **always** — the charge is spent even when every attack failed.
  deferred: the charge carries to the Po's **next wake**, not the next night — an Exorcised night
    does not consume it.
  expiry: `("po","3 Attacks")` → `Expiry.NEVER`; add a test so nobody adds it to a sweep table.
  info: none. show: none. visibility: night 1 `DEMON_INFO` — works today.
day: none.
death: `killOutcome` runs per target, in order. Two panel cases need explicit copy: the **Goon**
  ("this attack still kills; the ones after it do not, and the Goon turns evil") and the **self-kill
  with attacks remaining** (the wiki is silent — offer `[Stop here]` / `[Continue attacks]` and
  record the ruling). Three deaths in one dawn is the Po's tell: `DawnReport.publicScript` must hand
  the ST the exact sentence naming all three.
ledger: `LedgerEntry(kind = CHOICE, sourceId = "po", targetIds = <in order>, impaired)`, with the
  **empty** `targetIds` for a head-shake — that record, not the token, is the semantic truth of
  "chose no-one" and is what the Lunatic-as-Po placebo mirrors.
tests: Given a poisoned Po and a head-shake, then it holds `3 Attacks` and nobody died. · Given a
  sober Po whose single target is protected, then the target lives **and** no charge is placed. ·
  Given a charged Po picking 2,3,4, then all three die in that order and the token is gone. · Given
  a charged **poisoned** Po picking 2,3,4, then all three live and the token is still gone. · Given
  the Po holds `exorcist:Chosen` **and** `3 Attacks`, then nobody died and it **still** holds
  `3 Attacks`.
open: whether attacks 2 and 3 resolve after a self-kill (wiki silent — prompt, do not decide);
  repeating a player inside one charged night (allowed but wasted).

---

## pukka — Pukka · BMR Demon · P0:3 P1:6  *(user-reported)*

today: **the reported bug.** The Pukka's step renders the generic "Demon kill — who did X choose?"
and kills the chosen player immediately, on every night including night 1. Poison is a manual tray
token whose placement silently erases the previous victim, protection never clears the poison, and
the Exorcist annotation states the opposite of the actual ruling.
data:
  - characters.json: `reminders` `["Poisoned","Dead"]` → `["Poisoned","Poisoned","Dead"]`
    (data-accuracy D8: "Pukka poisons a new target while the old one is still poisoned — exactly the
    Pukka case the user hit"). **conflict:** `pukka.md` specifies a single **exclusive** token and
    `placeExclusiveReminder`; that is the mechanism that destroys the pending victim. Two copies +
    explicit removal is the correct model. Ability text and night reminders otherwise match.
  - night_and_jinxes.json: firstNight 22, otherNight 39 — correct. **Add** the missing jinx
    `summoner`+`pukka`: "The Summoner may summon a Pukka on the 2nd night instead of the 3rd."
  - night_guide.json: append to `other` — "If the Exorcist chose the Pukka, the Pukka does not poison
    tonight, but the previously poisoned player still dies. If the Pukka is drunk or poisoned, no-one
    is poisoned and no-one dies. Protection stops the death but the player still becomes healthy."
    Append to `first`: "If a Lunatic thinks they are the Pukka, they act tonight too."
setup: none. identity: plain Demon.
night.first / night.other: **the same step both nights** (the Pukka is the one Demon that acts on
  night 1) — `WakeStyle` differs only in the copy.
  gate: `Gates.aliveHolder`; `exorcist:Chosen` ⇒ **`StepGate.Reduced(reason = "Exorcised",
    allow = {"pending"})`** — the deferred half still runs. *"The Pukka does not wake to attack
    tonight, but a player still dies because of the Pukka's attack during the previous night."*
    `Skip` would suppress the death and is wrong. Dead Pukka ⇒ `Skip`; the standing poison stays.
  action: `ChoosePlayers("pukka", "They point at any player", min = 1, max = 1,
    constraints = [ANY_LIVING_STATE, SELF_ALLOWED], sort = ALIVE_FIRST)`. Grey-and-label the current
    poisoned holder "already poisoned — dies tonight".
  effects: **the order is load-bearing.**
    1. `Attack(Ref.PREVIOUS_TARGET, cause = KillCause(DEMON_ABILITY, "pukka"), respectProtection =
       true)` fires **first**, so `DeathEvent.impairedAtDeath == true` — *"players that the Pukka
       kills are still poisoned at their time of death"*, which makes the Sage/Ravenkeeper/Moonchild/
       Farmer/Barber/Poppy Grower on-death triggers all fire **malfunctioning**.
    2. Remove the previous victim's `POISONED` Effect **whether they died or were protected** —
       *"the Innkeeper prevents the Pukka from killing a poisoned player, then that player is no
       longer poisoned"*. Generalised from the Innkeeper ruling to every protection; say so once.
    3. `PlaceToken("pukka","Poisoned", Ref.TARGET)` ⇒ `Effect(kind = POISONED,
       sourceCharacterId = "pukka", until = Until.EVENT, untilEvent = "pukkaNextChoice",
       endsWithSource = true)` — status-model §1 already names this event.
    Impaired Pukka **at choice time** ⇒ no poison placed; **at kill time** ⇒ the pending victim does
    not die **and keeps the token** (the ability did not work, so nothing happened). Same-target
    case: the target is poisoned, dies, becomes healthy, and nobody is poisoned going into the next
    night — state this before confirming; do not re-place.
  deferred: the standing `POISONED` Effect **is** the memory; nothing else defers.
  expiry: `("pukka","Poisoned")` → `Expiry.NEVER` — survives dawn, dusk and the Pukka's death. (A
    Scarlet Woman who becomes the Pukka inherits it and kills that player at her next wake.)
  info: none computed; the Effect feeds `Status.impairment` (works today via the label substring —
    switch to `TokenRule.impairs`). show: none. visibility: night 1 `DEMON_INFO`.
day: DayBriefing.Slot.DAY_START — "<Name> is poisoned (Pukka) — all their info and abilities are
  false today, and they die at the Pukka's next wake unless protected." Feeds every day-time
  malfunction (Gossip resolution, Savant, Artist, Fisherman, Slayer shot, Moonchild choice, Juggler).
death: the **poison** is unblockable by anything but the Goon (Goon ⇒ the Pukka is drunk until dusk,
  the target is *not* poisoned, the Goon turns evil). The **death** respects every protection:
  `SAFE_FROM_DEMON` (Monk, Soldier), `CANT_DIE` (Sailor, Tea Lady), `CANT_DIE_TONIGHT` (Innkeeper),
  Fool `Spends`. `SURVIVES_EXECUTION` is irrelevant (execution only). Dawn: `"<Prev> died in the
  night."` / `"Nobody died in the night."` — never name the Pukka, never mention poison.
ledger: `LedgerEntry(kind = CHOICE, sourceId = "pukka", targetIds, impaired)` each night — needed
  for the Lunatic mirror ("Lunatic poisoned X last night — mirror or diverge?") and the log.
tests: Given night 1 and target 2, then seat 2 holds `pukka:Poisoned`, `deaths` is empty, everyone
  alive. · Given seat 2 poisoned at cycle 2 and a new target 3, then seat 2 is dead, has no
  `pukka:Poisoned`, seat 3 has it, and `impairedAtDeath == true`. · Given seat 2 also holds
  `("monk","Safe")`, then seat 2 is alive **and** no longer poisoned. · Given `exorcist:Chosen` on
  the Pukka, then seat 2 dies and **no** `pukka:Poisoned` exists anywhere. · Given the Pukka is
  poisoned, then seat 2 is alive **and still holds** `pukka:Poisoned`. · When NIGHT→DAY→NIGHT, the
  token survives.
open: same-target-two-nights and the Pukka's own death are both INFERRED, not on the wiki — surface
  as a Storyteller choice rather than deciding silently.

---

## shabaloth — Shabaloth · BMR Demon · P0:4 P1:4

today: the generic one-target panel, which **disables the confirm for dead targets**
(`enabled = target.alive`) — so the Shabaloth's signature move, attacking a corpse to set up a
regurgitation, is literally unavailable. There is no regurgitation flow, no order guarantee, and the
night guide states the **opposite** of the rule about what a regurgitated player gets back.
data:
  - characters.json: `reminders` `["Dead","Alive"]` → `["Dead","Dead","Alive"]` (two victims a
    night; data-accuracy D8 agrees).
  - night_and_jinxes.json: otherNight 40 (after Pukka, before Po), no first night — both correct.
    No jinxes — correct.
  - night_guide.json `:375-380`: **P0 rules error — the text is wrong.** "they learn no new
    information and their once-per-game abilities remain as they were" must be replaced: the
    regurgitated player *"regains their ability, even a 'once per game' ability already used"*, and a
    first-night-only / start-knowing ability *"may be used again"*. Rewrite `other` in full
    (regurgitate first, then two ordered kills, dead players are legal targets, dawn wording, a dead
    Shabaloth has no ability).
setup: none. identity: plain Demon.
night.first: none (correct). night.other:
  gate: `Gates.aliveHolder` — a **dead Shabaloth does nothing at all**: no kills and **no
    regurgitation** ("they have no ability when dead"). `exorcist:Chosen` ⇒
    `StepGate.Reduced(allow = {"pending"})`: no attacks; the regurgitation is an open question the
    ST must answer, not a silent decision.
  action: `Sequence` of two stages.
    Stage 1 (Storyteller's choice, **before** the Shabaloth wakes): `ChoosePlayers("shabaloth",
    "Regurgitate?", min = 0, max = 1, constraints = [DEAD], allowNone = true)` restricted to seats
    carrying `("shabaloth","Dead")` from last night; ineligible seats greyed with the reason.
    Stage 2: `ChoosePlayers(min = 2, max = 2, constraints = [ANY_LIVING_STATE, SELF_ALLOWED],
    sort = ALIVE_FIRST)` — **dead seats must be first-class targets**, surfaced in an "already dead —
    mark for regurgitation" group rather than sorted to the bottom. Picked **one at a time, in order**.
  effects: stage 1 → `Resurrect(Ref.TARGET, clearSpentMarks = true)` + `RemoveToken("shabaloth",
    "Dead")` + `PlaceToken("shabaloth","Alive")` + `Defer(kind = "first-night", ...)` /
    `Prompt(at = TONIGHT, kind = RUN_FIRST_NIGHT)` — night-engine §1's re-run table already has the
    Shabaloth row (`FIRST_NIGHT` style, same rule as the Professor). Then clear **all** remaining
    `("shabaloth","Dead")` tokens. Stage 2 → `perTarget = [PlaceToken("shabaloth","Dead",
    maxCopies = 2), Attack(Ref.TARGET, cause = KillCause(DEMON_ABILITY, "shabaloth"))]`, with
    protection **re-derived between the two** (the wiki's Tea Lady example turns on exactly this).
    An impaired Shabaloth kills nobody and regurgitates nobody but **still marks both picks**.
  deferred: the two `Dead` marks define tomorrow's regurgitation menu — that is the only carry-over.
  expiry: `("shabaloth","Dead")` → `Expiry.ON_SOURCE_STEP` (cleared by the Shabaloth's own next
    step, **not** at dawn/dusk — they must survive the day); `("shabaloth","Alive")` →
    `Expiry.NEVER` (it is the permanent public record; render it distinctly in the grimoire).
  info: none for the Shabaloth; the **revived player** may be owed a first-night re-run.
  show: none. visibility: night 1 `DEMON_INFO`.
day: none. Footer chip on the step: "Regurgitations so far: N" (the wiki advises once, at most twice).
death: `killOutcome` per target, re-derived between the two. `resurrect` semantics per status-model
  §6: `alive = true`, `ghostVoteUsed = false`, the `DeathEvent` kept with `resurrectedAtCycle`, all
  `SPENT` Effects dropped, and a `Prompt(at = DAWN, ANNOUNCE)`.
  **Dawn wording is quoted verbatim in the rules and must be reproduced exactly**: deaths first,
  then *"At dawn, after declaring which players died, declare which player is alive again. (Do not
  say why.)"* — `DawnReport.publicScript` ends with "Announce: <A> is alive again."
ledger: `LedgerEntry(kind = CHOICE, sourceId = "shabaloth", targetIds = <in order>)` +
  `LedgerEntry(kind = ANNOUNCE, sourceId = "shabaloth", text = "<A> is alive again")`.
tests: Given a sober Shabaloth picking 2 then 3, then both die in that order and both carry
  `shabaloth:Dead`. · Given seat 4 already dead, when picked, then it carries `shabaloth:Dead` and
  no new `DeathEvent` is added. · Given seat 4 dead with `shabaloth:Dead`, when regurgitated, then it
  is alive, carries `shabaloth:Alive`, `ghostVoteUsed == false`, and a **spent once-per-game mark is
  gone**. · Given a Tea Lady on seat 3 with good neighbours, then order `[3, 2]` kills both and
  order `[2, 3]` leaves seat 2 alive. · Given a poisoned Shabaloth, then nobody dies but both picks
  are still marked.
open: may the ST regurgitate on an **Exorcised** night? (Wiki silent — ask, do not decide.)
  Same-player-twice and self-selection are unaddressed (allow, warn).

---

## zombuul — Zombuul · BMR Demon · P0:5 P1:4

today: the app **declares good the winner** the moment the Zombuul takes its first "death"; the
"dead" Zombuul then gets no night action at all (`DemonKillPanel` is gated on `holder.alive`); the
"if no-one died today" gate is applied nowhere; dead players cannot be nominated, so the town has no
route to killing a Zombuul hiding as a corpse; and the real second death cannot be recorded at all.
data:
  - characters.json: `"Died today"` → `"Died Today"`. Add `"Registers as dead"` (or model it as a
    derived `Effect`/`DeathEvent.registeredOnly` and place no token — see below).
  - night_and_jinxes.json: otherNight 38 (first BMR Demon, after Imp, before Pukka), no first night —
    both correct. **Add** `summoner`+`zombuul`: "If the Summoner summons a dead player into the
    Zombuul, the Zombuul has already 'died once'."
  - night_guide.json: append — "Only deaths during the **day** stop the Zombuul; a player who died at
    night does not count, and executing an already-dead player does not count. If the Zombuul is
    drunk or poisoned when it would die, it dies for real and good wins. If a 'dead' Zombuul becomes
    drunk or poisoned, do not announce that they are alive."
setup: none.
identity: **the crux.** Model the first death as a real `DeathEvent(registeredOnly = true)` plus
  `alive = true` and a new derived `Player.registersAlive == false` (status-model §6). Every
  alive-count must read `registersAlive`, not `alive`: `alivePlayers`, `executionThreshold`, Empath,
  Chambermaid, Tea Lady neighbours, Godfather, nomination eligibility. It keeps **one ghost vote**,
  **cannot nominate**, and is **not an alive neighbour** — all of which fall out for free.
  night-engine §2 requires exactly this (`registersDead` flag on the seat, not `alive = false`).
night.first: none. night.other:
  gate: `Gates.someoneDiedToday(expected = false)` — i.e. `dayDeathsToday().isEmpty()` over the day
    that just ended — **AND** (`holder.alive || registersDead`) **AND** not `exorcist:Chosen`.
    Blocked ⇒ render as a closed, checked note ("<X> died today (executed, day N) — the Zombuul does
    NOT wake tonight. A player who died at **night** does not count."), never hidden: the ST must
    see the rule was applied. **Night deaths do not block it** — that is what makes the Zombuul a
    nightly killer, and it is the rule the app is most likely to get wrong. Traveller exiles (day
    deaths) **do** block it. Executing an already-dead player creates no `DeathEvent`, so it does not.
    The "all holders are dead — usually skip" hint must be suppressed for a secretly-alive holder.
  action: `ChoosePlayers("zombuul", "They point at any player", min = 0, max = 1,
    constraints = [ANY_LIVING_STATE, SELF_ALLOWED], sort = ALIVE_FIRST, allowNone = true)` — the
    wiki's own tip is "kill yourself and hide in plain sight".
  effects: `Attack(Ref.TARGET, cause = KillCause(DEMON_ABILITY, "zombuul"))` + `PlaceToken("zombuul",
    "Dead", Ref.TARGET)` + `RecordChoice`. Impaired ⇒ nobody dies, **and say nothing about it being
    alive** (wiki, verbatim).
  deferred: none — the gate is recomputed each night from `state.deaths`.
  expiry: `("zombuul","Died Today")` → `Expiry.DAWN` (placed on day D, read on night D+1, gone for
    day D+1) or derived and never placed; `("zombuul","Registers as dead")` → `Expiry.NEVER`.
  info: none. show: none. visibility: night 1 `DEMON_INFO`.
day: dead players **must be nominatable** — drop the `p.alive` filter at `DayScreen.kt:146` (keep it
  for the *nominator*), labelled "<Name> † (dead — an execution cannot kill them again)", except a
  secretly-alive Zombuul, where it silently can. DuskBriefing, the highest-value line in the group:
  "Nobody died today — the Zombuul kills tonight." / "The Zombuul does not wake tonight (<X> died
  today)." Grimoire: keep the shroud (the ST's screen must match the town square) but add an ST-only
  red ring + "UNDEAD" badge, restore the wake-order badge (`GrimoireScreen.kt:435`), and append
  "· 1 registers as dead" to the header. Log: "<name> registered as dead (Zombuul — still alive)".
death: `killOutcome` step 13 — a Zombuul with no prior death **and its ability** ⇒
  `KillOutcome.RegistersDead("Declare that the Zombuul died — but do not shroud them")`. If it is
  **drunk or poisoned** when it would die, the clause fails: it dies for real and good wins. The
  second death must be recordable even though `alive == false` (today `kill` early-returns) — a
  `[Zombuul dies for real — good wins]` action on the seat sheet. `WinCheck`: `aliveDemons` must
  count a secretly-alive Zombuul, so "every Demon is dead" cannot fire; the ≤2-alive rule counts
  genuinely-alive residents and must carry the caution quoting the wiki.
ledger: `LedgerEntry(kind = RULING, sourceId = "zombuul", text = "first death — registers as dead")`
  and a second on the real death; both are needed to explain the game afterwards.
tests: Given seat 3 executed on day 2, then the night-3 Zombuul step is blocked. · Given the Zombuul
  killed seat 3 on **night** 2 and nobody died in day 2, then night 3 is available. · Given a
  Traveller exiled on day 2, then blocked. · Given the first death, then the seat is
  `registersAlive == false`, holds one ghost vote, and `WinCheck` does **not** award good the game. ·
  Given a poisoned Zombuul executed, then no registers-dead state and `goodWins == true`. · Given the
  second death, then two `DeathEvent`s for that seat and `goodWins == true`. · Given seat 3 dead,
  then the nominee picker enables it.
open: **flagged, do not auto-resolve** — the only wiki sentence is "the game continues if just two
  other players are alive"; what happens at Zombuul + **one** living player is not stated. Surface as
  an advisory with the quote, never a silent ruling.
