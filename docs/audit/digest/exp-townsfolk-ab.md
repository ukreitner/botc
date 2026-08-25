# Experimental Townsfolk A+B — requirement cards

16 characters: acrobat, alchemist, alsaahir, amnesiac, atheist, balloonist, banshee,
bountyhunter, cannibal, choirboy, cultleader, engineer, farmer, fisherman, general,
highpriestess. Totals **P0: 40 · P1: 65**. Types are those of `mechanics/night-engine.md`
(StepKey/StepGate/NightAction/NightEffect/TokenRule/Briefings), `mechanics/status-model.md`
(Effect/EffectKind/Until/KillCause/killOutcome/DeathEvent/Prompt/DeathTrigger),
`mechanics/day-engine.md` (ExecutionRecord/DayRules/NominationTrigger/execute funnel),
`mechanics/records-and-memory.md` (LedgerEntry/LedgerKind/Memory) and
`mechanics/setup-and-identity.md` (SetupRequirement/BagShape/AbilityGrant/ActingRole/setupChoices).

## Group notes

1. **Granted abilities (alchemist, cannibal, amnesiac)** — three files, three invented fields (`grantedAbilityId`, `cannibalNightRole()`, `inventedAbility`). All collapse into `AbilityGrant` + `Identity.actingRoles` + per-holder `StepKey`; amnesiac's text into `setupChoices`. Largest shared prerequisite in the group.
2. **Six invented record types** — `DayStatement`, `NightAction`, `NightRecord`, `DayAbilityUse`, `CultVote`, `AcrobatChoice` → all `LedgerEntry`. **conflict:** day-engine calls the same list `DayEntry` with `subjectIdsB`/`count`/`bluff`; records-and-memory calls it `LedgerEntry`. Lead must pick one; these cards use `LedgerEntry` and lose `subjectIdsB` (alsaahir needs it) and `bluff` (→ `genuine=false`).
3. **`InfoCalc.supports` gates ALL caveats** (`NightScreen.kt:836`): acrobat, cannibal, choirboy, engineer, farmer, general, highpriestess show **no impairment warning at all**. Moving caveats to `Status.impairment` closes 7 P1s at once.
4. **"Give false info" is wrong for judgement abilities.** `commonCaveats` emits Vortox/false-info for cultleader, general, highpriestess (no info produced) and the *inverse* of the truth for balloonist. Needs `InfoCalc.yieldsInformation(id)` + an impairment *style* (`FALSE_INFO` / `NO_EFFECT` / `UNCONSTRAINED`).
5. **`ShowCard` routing discards `GuideShow.text`** (`NightScreen.kt:806-816`): the General's "GOOD IS WINNING" chip shows the player **"YOU ARE GOOD"**. Also corrupts `villageidiot`, `eviltwin`, `mezepheles`. P0, one-file fix.
6. **`DeathCause.DEMON` means "died at night"** (`SeatSheet.kt:271`) — banshee, choirboy, farmer are all blocked until `KillCause(DeathKind.DEMON_ABILITY, sourcePlayerId)` lands. Choirboy also needs *attacked-but-survived* recorded.
7. **`assignCharacter` never clears the old character's tokens** (`GameActions.kt:46-53`) — engineer leaves permanent poison, farmer leaves a Drunk permanently drunk. Use `changeCharacter(clearOldTokens = true)`.
8. **Two break the day engine's shape.** Banshee: `Nomination.extraVotes: Map<Long,Int>` (voters are a `Set<Long>`, so a double vote is *arithmetically impossible*) + a dead nominator. Atheist: a **non-seat nominee** `STORYTELLER_SEAT_ID = -1L` (`ExecutionRecord.playerId` is already `Long?`; `Nomination.nomineeId`/`aboutToDie` are not).
9. **Acrobat needs a mechanism nothing else has** — "or *become* drunk/poisoned tonight" is a night-scoped high-water mark (`GameState.nightImpaired: Set<Long>`, reset at each NIGHT entry), not a point-in-time query. status-model already flags it as needing `onImpairmentChanged`.
10. **Data debt: 15 jinxes missing, 4 wrong, in this group alone.** Missing: 7×alchemist, alsaahir+vizier, atheist+riot, banshee+leviathan/riot, cannibal+princess, engineer+legion/summoner. Wrong: **bountyhunter+kazali is inverted**, alchemist+summoner / farmer+leviathan / farmer+riot are retired text. Also: six character files wrote `"No ability"` where `characters.json` has **`"No Ability"`** (engineer, fisherman) — `Character.spentLabel` must carry the real spelling.

---

## acrobat — Acrobat · Experimental Townsfolk · P0:3 P1:3

today: places a `Chosen` token from the tray and nothing else — the death is never computed,
the token never expires, and the ST must hold "if anything later poisons that seat, kill the
Acrobat" across ~78 remaining night steps.
data:
  - characters.json: ok. Optional: reorder `reminders` to `["Chosen","Dead"]` (nightly one first).
  - night_and_jinxes.json: ok — otherNight idx 18, no jinxes. Do **not** reorder; the fix is a dawn pass.
  - night_guide.json: `other` prose is accurate; trim the first sentence for the collapsed row.
setup: none
identity: plain
night.other:
  gate: `Gates.aliveHolder` + `Gates.notSpent` is N/A → `Fire` iff holder alive. Never first night.
  action: `ChoosePlayers("acrobat", "Point to any player — they learn nothing", min=1, max=1,
    constraints = [ANY_LIVING_STATE, SELF_ALLOWED], sort = SEAT_ORDER, allowNone = false)`.
    Travellers allowed. Dead seats **enabled**. No default selection.
  effects: `PlaceToken("acrobat","Chosen", Ref.TARGET, maxCopies=1, exclusive=true)` ·
    `RecordChoice()` → `LedgerEntry(kind=CHOICE, sourceId="acrobat", targetIds=[t])`. **No kill here.**
  deferred: at DAWN, **before** `clearEphemeral`: if `holder.alive && Status.impairment(acrobat)
    .isEmpty() && target.id in nightImpaired` → `killOutcome(acrobat, KillCause(GOOD_ABILITY,
    "acrobat"))`. Monk `SAFE_FROM_DEMON` does **not** block it; Innkeeper `CANT_DIE_TONIGHT`
    and Tea Lady `CANT_DIE` do. Show the `deathNotes`/`KillOutcome` to the ST for confirmation.
  info: none — the Acrobat learns nothing, not even which of drunk/poisoned. No false alternative;
    when the Acrobat is impaired the *only* change is that they cannot die. No misregistration.
  show: none. Step must say "they learn nothing — do not show them anything."
  visibility: nothing to Demon/Minions/Lunatic.
day: none.
death: on-death triggers none. The Acrobat's own death is the deferred effect above.
ledger: `Memory.lastChoice(state,"acrobat")` for the target; `nightImpaired` watermark (§group 9)
  must record a seat that became impaired **and then un-impaired** before dawn — the Acrobat
  still dies.
tests:
  - N2, Chosen on a seat holding `poisoner:Poisoned`; dawn ⇒ Acrobat dies, `acrobat:Chosen` gone.
  - Chosen while target sober, Pukka poisons them later the same night; dawn ⇒ Acrobat dies (wiki Ex 3).
  - Target poisoned then un-poisoned before dawn ⇒ Acrobat still dies.
  - Acrobat poisoned, target poisoned ⇒ Acrobat survives; Acrobat chooses self sober ⇒ survives.
  - Target `characterId == "drunk"` ⇒ dies; target `characterId == "marionette"` (no token) ⇒ survives.
  - No Dashii whose nearest-Townsfolk neighbour *changes* mid-night ⇒ the newly-poisoned seat counts.
  - Acrobat holds `monk:Safe` ⇒ still dies (Monk blocks only DEMON_ABILITY).
open: none — the rules are fully specified; only the watermark mechanism is a design choice.

---

## alchemist — Alchemist · Experimental Townsfolk · P0:4 P1:5

today: night-1 "YOU ARE + Minion token" show card works; then the app forgets. The granted
ability never gets a night slot, is never stored, its setup bracket never reaches `validateBag`
(an Alchemist-Baron bag is **rejected**), and 7 of 8 jinxes are missing.
data:
  - characters.json: ok. Normalise `remindersGlobal` `"Is The Alchemist"` → `"Is the Alchemist"` (P3).
  - night_and_jinxes.json: firstNight idx 10 ok, no otherNight ok. **Replace** the retired
    `summoner+alchemist` ("The Alchemist can not have the Summoner ability") with the current text
    ("The Alchemist-Summoner does not get bluffs, and chooses which Demon but not which player. If
    they die before this happens, evil wins. [No Demon]"). **Add all 7 missing**: `alchemist+boffin`,
    `+marionette`, `+mastermind`, `+organgrinder`, `+spy`, `+widow`, `+wraith` (verbatim texts in
    `characters/alchemist.md`).
  - night_guide.json: `first` entry good; resolve its `{"token":"pick"}` card against
    `setupChoices["alchemist.grant"]` so it is one tap. Add an `other` entry: "no step of their own —
    they wake at the <Minion>'s place."
setup: `SetupRequirement(id="alchemist.grant", characterId="alchemist", kind=GRANT,
  label="Choose the not-in-play Minion ability the Alchemist has", blocking=true)`.
  candidates: every Minion on the script, **not-in-play first**, in-play under a second
  "duplicates an in-play Minion" heading. Writes `setupChoices["alchemist.grant"]` +
  `AbilityGrant(abilityId=<minion>, sourceId="alchemist", mode=GrantMode.ADD)`. Bag: the grant
  joins `virtualSetupCharacters(...)` (already specified) so `[+2 Outsiders]` from an
  Alchemist-Baron reaches `validateBag`/`randomBag` — **P0 fix**.
identity: registers **good** and as the **Alchemist** always. `characterId` must never be set to
  the Minion id (that would flip `Player.isEvil`). `Identity.actingRoles` yields two roles for the
  seat: `alchemist` (first night only) and the granted Minion at **its own** slot, both nights.
  Excluded from MINION_INFO/DEMON_INFO (works today by team filter — needs a locking test).
night.first:
  gate: holder alive. `StepKey("alchemist", seat)`.
  action: none. effects: none (the grant is placed at setup).
  info/show: `ShowCardTo(Ref.SOURCE, "YOU ARE")` + the granted Minion's character token.
  visibility: print "The Alchemist does NOT wake with the Minions and does not learn the Demon."
night.first/other (granted role):
  gate: a **second** step at the granted Minion's night-order index, `StepGate.Fire` iff holder
    alive and `Status.hasAbility(alchemistSeat)`; `Skip("dead")` otherwise. Title
    `"Poisoner — via the Alchemist (Ana)"`; uses the Minion's own reminder text, `night_guide`
    entry and `allReminders` in the tray (today the tray offers only `Is The Alchemist`).
  action/effects: the granted Minion's own `NightAction`, unchanged, with `sourceId` = the
    Minion id so its `TokenRule`s (expiry, exclusivity) keep working.
  banner (mandatory, every pick): **"You may veto this choice — shake your head and point at the
    Alchemist text on the sheet. They must choose again."**
  info: impairment is the **Alchemist's** (`Status.impairment(alchemistSeat)`), not a Minion's.
day: alchemist+spy / +widow / +wraith jinxes: **after each execution**, a `LedgerEntry(kind=
  STATEMENT, sourceId="alchemist")` recording the public guess + a computed correct/incorrect
  verdict. If correct → a constraint on tonight's Demon step: *"The Demon MUST choose <name>"*
  (disable every other chip) — implement as a `NightEffect`-level target restriction.
death: when the Alchemist dies the granted ability stops (they are a Townsfolk, so Vigormortis
  does not preserve it). Alchemist-Summoner who dies before summoning → `WinCheck` advisory
  "evil wins".
ledger: `setupChoices["alchemist.grant"]`; every granted-role choice as `LedgerEntry(kind=CHOICE,
  sourceId=<minionId>, actorId=alchemistSeat)`.
tests:
  - grant="poisoner" ⇒ firstNight plan has **both** an `alchemist` step (idx 10) and a `poisoner`
    step (idx 27) carrying the same seat; otherNight has `poisoner` only.
  - `player.isEvil == false`, `team == TOWNSFOLK` with a Minion grant.
  - grant="baron" ⇒ `validateBag` demands 2 extra Outsiders and passes (fails today).
  - grant == null at SETUP ⇒ `SetupRequirements.unmet` contains `alchemist.grant`.
  - `GameData.activeJinxes` returns `alchemist+spy` even though no seat holds `"spy"`
    (virtual-in-play matching).
  - MINION_INFO's `playerIds`/detail never name the Alchemist.
open: does an Alchemist-Marionette/Mastermind/Spy/Widow/Wraith grant leave the seat with **no**
  ability at all (jinx text says so) — model as `AbilityGrant(abilityId=<x>, mode=?)` with an
  explicit "suppressed" state, or as no grant plus a note? Needs a lead ruling.

---

## alsaahir — Alsaahir · Experimental Townsfolk · P0:2 P1:4

today: the app does not know this character exists outside the script list. No day input, no
correctness check, no once-per-day gate, no win trigger. The ST adjudicates the hardest
correctness test on the script from memory.
data:
  - characters.json: ok.
  - night_and_jinxes.json: correctly absent from both night orders. **Add** `alsaahir+vizier`:
    "The Storyteller doesn't declare the Vizier is in play." — and surface it at **setup**, since
    the Vizier announcement happens before anyone opens the jinx dialog.
  - night_guide.json: no entry. Needs the `day` block the schema does not yet have
    (`NightGuideEntry` is first/other only, `NightGuide.kt:37`).
setup: none (the Vizier-suppression warning is an advisory `SetupRequirement`, `blocking=false`).
identity: plain. Never wakes.
day:
  briefing (DAY_START): *"Alsaahir in play — prompt them once today if they want to guess."*
  tool: a Day-screen card, shown whenever the **script** contains `alsaahir` (never leaking whether
    it is in the bag). 3 taps: speaker chip row (living non-Travellers; already-guessed seats
    disabled) → Demon multi-select → Minion multi-select.
  correctness (`Character.team`, **never** `Player.isEvil`):
    `demonIds == {residents with team==DEMON}` (incl. dead) and
    `minionIds == {residents with team==MINION} - {team==DEMON}` (Legion counts as **Demon**);
    Travellers excluded from both truth set and guess.
  verdicts the ST is shown before committing: `CORRECT — good wins` only when correct **and**
    speaker.characterId=="alsaahir" **and** `Status.impairment(speaker).isEmpty()`; otherwise
    `Correct, but <name> is POISONED — say "nothing happens"` / `Correct, but <name> is not the
    Alsaahir` / `Incorrect — say "nothing happens". Missed: Sarah (Baron).`
  LedgerKind: `STATEMENT`, `sourceId="alsaahir"`, `targetIds=demons`, second list = minions
    (**conflict:** `LedgerEntry` has one `targetIds`; day-engine's `DayEntry` has
    `subjectIds`+`subjectIdsB`. Alsaahir needs two lists — either add `targetIdsB` to
    `LedgerEntry` or encode minions in `characterIds`), `verdict`, `genuine=false` for bluffers.
  rules: once per speaker per day (hard); optional 3-guessers-per-day cap behind a setting.
  win: `WinCheck` advisory `alsaahir-correct` keyed on the LedgerEntry → `RevealSheet`,
    `revealGoodWins = true`.
death: none.
ledger: every guess, including bluffers' — the good team reasons from the history and the ST is
  asked "what did they guess on day 2?". Show a per-day history strip.
tests:
  - guess omitting a **dead** Minion ⇒ incorrect; naming all incl. dead ⇒ correct.
  - a Minion turned good (alignment flipped) still must be named as a Minion ⇒ correct.
  - an evil **Traveller** omitted ⇒ correct; named as a Minion ⇒ incorrect.
  - Legion seat guessed as Demon ⇒ correct; as Minion ⇒ incorrect.
  - Bounty Hunter's evil Townsfolk omitted ⇒ correct (neither Minion nor Demon).
  - impaired Alsaahir, fully correct ⇒ verdict correct, `tookEffect=false`, **no** win advisory.
open: what must be named in a **Lil' Monsta** game (a Demon character with no seat)? Not stated on
  the wiki. Always show the computed verdict **plus** a manual Correct/Incorrect override, and a
  caution when the script has `lilmonsta`/`legion`/`riot`/`summoner`/`boffin`.

---

## amnesiac — Amnesiac · Experimental Townsfolk · P0:2 P1:4

today: an "Amnesiac" row fires every night with generic prose whether or not the invented ability
wakes anyone (and **blocks dawn** until ticked); the invented ability has nowhere to live; the
daily private guess and hot/cold answer are recorded nowhere.
data:
  - characters.json: ok. `reminders: ["?","?","?"]` — three copies confirmed; keep.
  - night_and_jinxes.json: firstNight idx 45, otherNight idx 67 — keep as the **default**
    `slotId`, not a fixed position.
  - night_guide.json: prose good. Add a `day` block carrying the four answer definitions
    ("no relation"/"some relation"/"close"/"you've got it"); add a number card and a good/evil card
    to `shows` so the common Amnesiac abilities are one tap.
setup: `SetupRequirement(id="amnesiac.<seatId>", kind=GRANT,
  label="Write the ability you are giving the Amnesiac. Make it guessable.", blocking=true)`.
  Stores `setupChoices["amnesiac.<seatId>"]` (free text) plus
  `AbilityGrant(abilityId="", sourceId="amnesiac", slotId=<night-order id or null>)` where
  `slotId == null` means **passive — emits no step**. Two switches: wakes first night / wakes
  later nights. Offer 6-8 tappable starter abilities. Must trigger on
  `characterShownToPlayerId == "amnesiac"` too, so a Drunk/Marionette shown the Amnesiac also
  gets an invented ability.
identity: acts as an ST-authored ability at `slotId`'s position; registers as the Amnesiac.
night.first / night.other:
  gate: `Fire` iff holder alive **and** `slotId != null` **and** the matching wakes-switch is on.
    Passive ⇒ **no step at all**, so the dawn guard has nothing to block on (P1 fix).
  action: `ChoosePlayers("amnesiac", <inventedAbility first 60 chars>, min=0, max=3,
    constraints=[], allowNone=true)` — deliberately unconstrained; the engine assumes nothing.
  effects: `PlaceToken("amnesiac","?", Ref.TARGET, maxCopies=3, exclusive=false)`. Each `?` must
    accept a **free-text suffix on placement** (`PlacedReminder.note`, already specified) so the ST
    can read `? chose Ana` vs `? night 2`. `RecordChoice()`.
  deferred: none by default.
  info: nothing computed. The free-text "Info" show card writes its shown value back as
    `LedgerEntry(kind=TOLD, sourceId="amnesiac", shown=<text>)`.
  show: existing free-text `ShowCard.Message`, plus number and good/evil cards.
  banner: `Status.impairment` non-empty ⇒ *"Ana is DRUNK/POISONED — their ability malfunctions
    tonight. Give false info or no effect."* Vortox ⇒ only if the invented ability is an **info**
    ability; the ST said which at setup, so gate the caveat on that flag.
  strip: **"Last nights"** — `Memory.forPlayer(seat)` rendered as `N1: pointed to Ana, Ben — shook
    head · N2: showed 3`. Wiki Ex 1 depends on the ST reproducing that sequence.
expiry: `("amnesiac","?")` must **not** go in any expiry table (the ability is unknown). The
  placement dialog needs a per-token "clears at dawn / at dusk / never" selector — a generic
  `TokenRule` override the whole app wants.
day: a **private-tinted** card whenever the script contains `amnesiac`: text field "What did Ana
  guess today?" (pre-filled with yesterday's) + four buttons **Cold · Warm · Hot · Bingo**, each
  with its wiki definition as sublabel, each recording `LedgerEntry(kind=PRIVATE,
  sourceId="amnesiac", text=<guess>, shown=<answer>)` and offering "Show full-screen".
  History strip: `D1 "…dead players?" → Cold · D2 "…numbers?" → Warm`, with the invented ability
  pinned at the top so the ST can judge accuracy. Hint under Bingo: *"If the meaning matches but
  the wording differs, still say Bingo."* DAY_START briefing: *"Ana owes you a private guess today.
  Their ability: '<text>'."*
death: none.
ledger: the invented ability (setupChoices), every night action, every daily guess+answer.
tests:
  - `slotId == null` ⇒ no amnesiac step on either night; dawn guard unaffected.
  - `slotId == "poisoner"` ⇒ the step appears at the Poisoner's index (13), not 67, titled with the
    Amnesiac's name.
  - `wakesFirstNight == false` ⇒ absent from the first-night plan, present on other nights.
  - two recorded night actions ⇒ both appear in the "last nights" strip in order.
  - a fourth `?` placed ⇒ the oldest recycles, exactly three remain.
  - `characterId` changed away from amnesiac mid-game ⇒ the invented-ability choice is cleared.
open: none blocking. Note the ST must answer the daily guess **truthfully even while poisoned** —
  the guess is about what the ability *is*, not whether it worked. Say so in the day card.

---

## atheist — Atheist · Experimental Townsfolk · P0:4 P1:4

today: `Setup.TEAM_WARPING_IDS` switches distribution checking **off entirely** (an Atheist bag
with 4 Outsiders and 2 Demons validates clean), `randomBag` still deals a Demon and Minions,
`WinCheck` fires nothing (both branches require Demons), and the Storyteller cannot be nominated —
the character's single win condition is unreachable.
data:
  - characters.json: ok (`setup: true`).
  - night_and_jinxes.json: correctly absent from both night orders. **Add** `atheist+riot`:
    "During a riot, if the Storyteller is nominated, players vote. If they are 'about to die',
    the game ends. If not, they nominate again."
  - night_guide.json: no entry; needs the `day` block.
setup: replace the blunt `TEAM_WARPING_IDS` branch with
  `BagShape(minions = 0..0, demons = 0..0, townsfolk + outsiders = n)`, Townsfolk/Outsider split
  **free** (wiki Ex 2 relies on running the "wrong" number of Outsiders). `randomBag` must consult
  the shape **before** its team loop (`GameActions.kt:353`) — it currently draws Demon then Minions
  and never removes them. Blocking one-time briefing dialog (the wiki calls this character
  "recommended for experienced Storytellers"), stating all four rule changes: no evil players ·
  good wins if **you** are executed · good loses at 2 alive · you may break any rule.
  Bag builder: grey out evil characters with reason `Not allowed — the Atheist is in the bag`;
  the "Need:" line becomes `Need: 8 good characters — no Minions, no Demon [No evil characters]`.
identity: plain (the Atheist is a normal Townsfolk; the *game* changes, not the seat).
night: none — never wakes.
day:
  nominee: a sentinel `STORYTELLER_SEAT_ID = -1L` chip **⟡ The Storyteller**, shown whenever the
    **script** contains `atheist` (never leaking whether it is in the bag). Nominator row unchanged
    (living players only). Threshold is the ordinary `DayRules.threshold(isExile=false)` —
    `(alive+1)/2` already equals "50% or more" for both parities, **no new maths**.
  execute: goes through `GameActions.execute(playerId = STORYTELLER_SEAT_ID, via = ExecutionVia.
    VOTE)`; `ExecutionRecord.playerId` is already `Long?`. `aboutToDie` and `Nomination.nomineeId`
    must both learn to carry the sentinel.
  ExecutionConsequence: Atheist in play (alive **or** dead) ⇒ *"The Storyteller is executed — GOOD
    WINS."*; **no** Atheist in play (or the "Atheist" is the Drunk) ⇒ a confirmation, not a
    surprise: *"No Atheist is in play — executing the Storyteller means EVIL WINS. Confirm?"*
  simulate-evil palette (Day screen + seat sheet), because in an Atheist game the evil characters
    are not in play and therefore never appear in any in-play-keyed reminder picker: Demon attack
    (`kill` with `KillCause(DEMON_ABILITY)` so the log reads like a real game) · `poisoner:Poisoned`
    · `witch:Cursed` · `devilsadvocate:Survives execution` · `cerenovus:Mad` · change character.
    No new engine code — they need to be **offered**.
  header at 3 alive: *"3 alive — with an Atheist in play, good loses at 2."*
death: `WinCheck` (ordered, before the Demon branches):
  `atheist-storyteller-executed` → `goodWins = (atheistSeat != null)`, caution when a Drunk holds
  `shownCharacterId == "atheist"` ("there IS a hidden evil team and evil wins").
  Atheist in play && no Demons: `alive <= 2` ⇒ **good loses** (the Glossary rule the app currently
  gates on `aliveDemons.isNotEmpty()`); `alive == 3` ⇒ advisory "one more death and good loses";
  otherwise **return null** — suppress both Demon-based branches. (status-model/day-engine already
  say Atheist suppresses all evil-win advisories.)
ledger: the Storyteller nomination and its execution, so the log reads
  `D3 Ana nominated the Storyteller — 5 votes, executed — good wins`.
tests:
  - `randomBag` ×50 with the Atheist ⇒ **no** bag contains a Demon or Minion (fails today).
  - Atheist + 6 TF + 1 Imp ⇒ `validateBag` reports "Demon: 1 in bag, expected 0" (fails today).
  - Atheist + 5 TF + 2 Outsiders ⇒ no issues (free split, wiki Ex 2).
  - 2 alive, no Demon, Atheist ⇒ advisory `goodWins = false` (returns `null` today).
  - executed Storyteller nomination + Atheist ⇒ `goodWins = true`; without an Atheist ⇒ `false`.
  - `bagShapeFor("atheist", …).demons == 0..0` and no longer over-reaches into TRAVELLER/FABLED.
open: none.

---

## balloonist — Balloonist · Experimental Townsfolk · P0:2 P1:4

today: `InfoCalc.balloonist` prints a **static roster grouped by team** with the rule restated as
prose — no knowledge of last night, no picker, no misregistration call. The setup bracket silently
defaults to **+1 Outsider** and is never asked. The moving `Know` token destroys the only record of
last night's type.
data:
  - characters.json: ok (current text + single `Know` token — do **not** regress to the retired
    five-token version still in townsquare dumps).
  - night_and_jinxes.json: firstNight 60 / otherNight 83 ok. Fix the `marionette+balloonist` jinx
    wording: "+1 Outsider might have been added" → "an Outsider might have been added during setup."
  - night_guide.json: prose already accurate; trim the first sentence for the collapsed row.
setup: `SetupRequirement(id="balloonist.outsiders", kind=NUMBER, blocking=false,
  label="Balloonist: add a second Outsider? (+0 or +1)")` → `setupChoices["balloonist.outsiders"]`.
  Feed it into `Setup.adjustedDistribution` so the "Need:" line shows the **chosen** variant, not
  `matches.last()`. Also `SetupRequirement(id="balloonist.know", kind=REMINDER,
  label="Mark the first player the Balloonist learns", blocking=true)` — the wiki's "when
  preparing the first night, mark any player with KNOW". Surface the +0/+1 answer permanently in
  the seat note: `Balloonist — +1 Outsider added at setup` (the ST **will** be asked).
identity: plain. A Drunk/Marionette shown as Balloonist routes through `nightRoleId` already and
  gets the same step with an arbitrary (unconstrained) pick + banner.
night.first / night.other:
  gate: `Fire` iff holder alive. Both nights.
  action: `ChoosePlayers("balloonist", "Point to the marked player", min=1, max=1,
    constraints = [ANY_LIVING_STATE, SELF_ALLOWED] + (`DIFFERENT_TYPE_FROM_LAST_NIGHT`),
    sort = SEAT_ORDER)`. **conflict:** `TargetConstraint` has `DIFFERENT_FROM_LAST_NIGHT` (same
    *player*); this character needs *different registered **type***. Add
    `DIFFERENT_TYPE_FROM_LAST_NIGHT` to the enum, or the constraint cannot be expressed.
    Picker = chips **grouped by type** (Townsfolk · Outsider · Minion · Demon · **Traveller** —
    Travellers are a legal fifth type and the ST's escape hatch); last night's type section
    collapsed and disabled with reason *"same type as last night (Townsfolk)"*; Spy and Recluse
    appear under **every** type they can register as, each labelled "Recluse — may register as
    Minion / Demon". Sort types never yet shown first.
  effects: move `PlaceToken("balloonist","Know", Ref.TARGET, maxCopies=1, exclusive=true)` ·
    `RecordChoice()` → `LedgerEntry(kind=CHOICE, sourceId="balloonist", targetIds=[t],
    shown=<REGISTERED type>)`. `shown` must hold the **registered** type, not `Character.team`, so
    a Recluse shown as a Minion constrains tomorrow to non-Minion. Offer a "shown as" override at
    pick time for Spy/Recluse seats.
  deferred: none. expiry: `("balloonist","Know")` — `Expiry.NEVER`. Correctly absent today.
  info: the ST **points at a player**; nothing is shown on screen to the player, and the Balloonist
    does **not** learn the type. Step must say so — the roster display looks like an info payload.
    Impaired ⇒ constraint **relaxed, not falsified**: *"Ana is DRUNK/POISONED — you may show ANY
    player, including the same type as last night. Tomorrow's constraint is still measured against
    tonight's shown player."* (wiki Ex 2 — this is `NO_EFFECT`-adjacent, not `FALSE_INFO`).
    Vortox ⇒ replace the generic caveat with the **inverse** rule: *"VORTOX — show a player of the
    SAME type as last night."*
  show: none (a physical point). A `ShowCard.SeatCard(playerId)` — see highpriestess — would help.
  history strip (highest-value single addition): `N1 Abdallah (Minion) · N2 Lewis (Townsfolk) ·
    N3 Sarah (Outsider) → tonight: NOT Outsider`.
  visibility: nothing to Demon/Minions/Lunatic.
day: none.
death: none.
ledger: per-night `(targetId, registeredType)`; the setup +0/+1 choice.
tests:
  - N1 shown TOWNSFOLK ⇒ N2 excludes every Townsfolk, includes Outsider/Minion/Demon/Traveller.
  - Balloonist poisoned ⇒ all seats eligible + "any type allowed" note.
  - N1 TF, N2 TF (shown while poisoned), N3 sober ⇒ Townsfolk excluded (constraint is against
    **last night**, not the last legal night) — wiki Ex 2.
  - Recluse recorded `shown = "MINION"` ⇒ next night excludes Minions, allows Outsiders.
  - a Recluse/Spy in play ⇒ the step carries misregistration caveats and lists them under every
    type they can register as (**none produced today**).
  - Vortox alive ⇒ caveat says **same** type, not "info must be FALSE".
  - `setupChoices["balloonist.outsiders"] == "+0"` ⇒ the "Need:" line shows the base count.
open: none.

---

## banshee — Banshee · Experimental Townsfolk · P0:5 P1:3

today: nothing anywhere. The trigger is never detected or announced; a dead Banshee **cannot be
selected as a nominator at all** (chip greyed, no override); double votes are structurally
impossible because voters are a `Set<Long>`; the ghost-vote rule locks them out after one vote.
Tallies are silently understated by one, which changes who is on the block.
data:
  - characters.json: ok. `reminders: ["Has Ability"]` — **capital A**.
  - night_and_jinxes.json: otherNight idx 62 ok. Vortox jinx present (fix "all players **still**
    learn" → "all players learn"). **Add** `leviathan+banshee` and `riot+banshee`: "Each night*,
    the Leviathan/Riot chooses an alive good player (different to previous nights): a chosen
    Banshee dies & gains their ability." Both need the same `DIFFERENT_FROM_LAST_NIGHT` machinery.
  - night_guide.json: `other` entry good. Add a `shows` entry with a ready
    `ShowCard.Message("THE BANSHEE HAS AWOKEN")`.
setup: none.
identity: plain, but gains **day rights** on death (see death).
night.other:
  gate: this is not really a wake — it is a checkpoint. `StepGate.Fire` iff a `DeathEvent` this
    cycle has `playerId == bansheeSeat`, `cause.kind == DeathKind.DEMON_ABILITY` and
    `impairedAtDeath == false`; else `Skip`.
  effects: `PlaceToken("banshee","Has Ability", Ref.SOURCE)` · clear `ghostVoteUsed` and mark the
    seat as never needing one · `Announce(BriefingSlot.DAWN_PUBLIC, "The Banshee has awoken.")`.
  expiry: `("banshee","Has Ability")` — `Expiry.NEVER` (day-engine already lists it).
death: implement as a `DeathTrigger`, **not** a night step, so it fires the moment the kill lands:
  - matches: `cause.kind == DEMON_ABILITY && characterIdAtDeath == "banshee" && !impairedAtDeath
    && no NO_ABILITY effect`. `DeathEvent.impairedAtDeath` **already exists and is already
    populated** (`GameActions.kt:153`, used by the Saint check) and is simply never read.
  - produce: the effects above + `Prompt(at = DAWN, kind = ANNOUNCE)`.
  - three ST-facing texts, all needed: at the kill (`deathNotes`) *"Banshee: if this kill lands and
    they are sober, announce it publicly at dawn — they gain double nominations & votes."*;
    impaired *"Ana was POISONED when the Demon killed them — say NOTHING. Normal dead player."*
    (wiki Ex 2); non-Demon cause *"Ana died to the Lycanthrope, not the Demon — say nothing."*
    (wiki Ex 3).
day (all `DayRules`):
  - `canNominate`: `(alive || awoken) && nominationsToday < limit`, `limit = if (awoken) 2 else 1`.
    Generalise `GameActions.hasNominatedToday` (a hard boolean gate today) into
    `nominationsToday(state, playerId): Int` / `nominationsAllowed(state, lookup, playerId): Int`
    so Butcher, Travellers and the Bounty Hunter's evil Townsfolk fit the same shape.
  - `voteWeights`: awoken Banshee ⇒ **2**. Needs `Nomination.extraVotes: Map<Long,Int>` (day-engine
    already specifies `voteWeights`/`tally`, so this fits) — voters are a `Set<Long>` today, so
    `votes = voterIds.size` cannot express it. Vote chip becomes tri-state `— · ✋ · ✋✋`; tally
    line reads *"5 votes (Ana raised both hands)"*.
  - `canVote`: `alive || !ghostVoteUsed || isExile || awoken`, and the ghost-vote spend loop skips
    awoken Banshees. **Unlimited** double votes, every nomination, every day (wiki Ex 1 has three
    in one day).
  - DAY_START briefing: *"Ana (Banshee) is awoken — 2 nominations today, double votes, no vote
    token."* Plus a "1 of 2 nominations used" counter (the wiki says it is the player's
    responsibility; the counter costs nothing and prevents arguments).
  - WinCheck caution on the `alive <= 2` branch: *"An awoken Banshee can still nominate while dead
    — the game continues even with no good players alive."*
ledger: the awakening as `LedgerEntry(kind=ANNOUNCE, delivered=…)`; nomination counts derive from
  `state.nominations`.
tests:
  - sober Banshee killed with `DeathKind.DEMON_ABILITY` ⇒ awoken + `banshee:Has Ability` placed.
  - poisoned at death ⇒ **not** awoken, no token, `impairedAtDeath == true` (wiki Ex 2).
  - killed by Lycanthrope / Assassin / Godfather / execution ⇒ nothing triggers (wiki Ex 3).
  - awoken dead Banshee: `nominationsAllowed == 2`, then 0 after two, back to 2 next day.
  - `voterIds = [a,b,c,banshee]` + `extraVotes = {banshee:1}` ⇒ `votes == 5`; with threshold 5 ⇒
    `ABOUT_TO_DIE` (today the app computes 4 and says SAFE — **this decides games**).
  - awoken Banshee votes on nomination 1 ⇒ still eligible for nomination 2, `ghostVoteUsed` untouched.
  - Vortox kills the Banshee ⇒ trigger still fires (keys on the cause kind, not the Demon's id).
open: (a) does an awoken Banshee keep the ability after `resurrect`? The text says "from now on";
  lock the intended behaviour with a test either way. (b) does the double vote apply to a
  **Traveller exile**? The wiki does not say — show the control with a one-line caution rather than
  silently deciding.

---

## bountyhunter — Bounty Hunter · Experimental Townsfolk · P0:3 P1:5

today: `[1 Townsfolk is evil]` is never enforced, prompted or recorded, so setup validates a fully good town; the evil Townsfolk is **never told they are evil** (`RevealFlow` just tints the name red); the other-night wake condition is not evaluated so the row fires every night and blocks dawn; `InfoCalc.bountyHunter` lists every evil player including ones already learned.
data:
  - characters.json: ok. Consider adding a `bountyhunter:Evil Townsfolk` reminder label so the grimoire circle can show it.
  - night_and_jinxes.json: firstNight 63 / otherNight 86 ok; `philosopher+bountyhunter` ok. **`kazali+bountyhunter` is the INVERSE of the rule** — file says "An evil Townsfolk is only created if the Kazali chooses the Bounty Hunter"; wiki says "If the Kazali turns the Bounty Hunter into a Minion, an evil Townsfolk is **not** created." **P0 data fix.**
  - night_guide.json: prose accurate. Add a `shows` entry `{"label":"You are evil","kind":"evil"}`.
setup: `SetupRequirement(id="bountyhunter.evil", kind=ALIGNMENT, blocking=true, label="Turn one Townsfolk evil, and mark an evil player KNOW")` — already in the table. Two chained prompts: (1) which Townsfolk is secretly evil → `alignmentFlipped = true`, `alignmentFlippedBy = "bountyhunter"`, seat note *"Evil Townsfolk (Bounty Hunter setup) — does not know the other evil players"*, plus a visible reminder; (2) which evil player the BH starts knowing → `bountyhunter:Know`, exclusive. Validation: exactly one `Know`, on an **evil** seat. **Key on `characterId`, never `shownCharacterId`** — a Drunk/Marionette who believes they are the Bounty Hunter creates **no** evil Townsfolk (wiki Ex 3).
identity: the evil Townsfolk keeps `team == TOWNSFOLK` and their own ability; only alignment moves — **not** a Minion for Alsaahir or `WinCheck`. `RevealFlow` must render an explicit `ShowCard.AlignmentCard(evil = true)` panel after the character card for **any** seat whose alignment differs from its team — *"YOU ARE EVIL — you win with the evil team. You keep the Chef ability. You do not know who the other evil players are."* (also correct for Mezepheles, Cult Leader, Ogre). **P0.**
night.first:
  gate: `Fire` iff holder alive. action: none to decide — the `Know` seat was chosen at setup;
  the panel simply **names** them: *"Point to Abdallah."*
  info: the BH learns the **player, not the character** — say so on the step.
  impaired: *"Ana is POISONED — you may point to any player, including a good one. The Bounty
  Hunter re-learns when **that** player dies."* (wiki Ex 2 — the retrigger now hangs on a **good**
  player's death, which is the specific part the generic "give false info" caveat gets wrong.)
  effects: `RecordChoice()` → `LedgerEntry(kind=CHOICE, sourceId="bountyhunter", targetIds=[shown])`.
night.other:
  gate (computed, not remembered): `Fire` iff the seat holding `bountyhunter:Know` is dead **and**
    a `DeathEvent` for them has `cycle == state.cycle` (tonight) or `cycle == state.cycle - 1 &&
    !atNight` (today). Otherwise `Skip("Abdallah (marked Know) is still alive")` — collapsed,
    auto-ticked, excluded from the dawn guard. Same class of fix as the Pukka.
  action: `ChoosePlayers("bountyhunter", "Point to a new evil player", min=1, max=1)`. Candidates
    in order: (1) evil players **not yet learned** (from the CHOICE ledger); (2) already-learned
    evil players **disabled**, reason "already learned on night 2"; (3) when impaired, all players
    with good ones enabled + banner. Each chip shows the seat's character so the ST can see the
    evil Townsfolk for what they are. Identity is **by seat**, not by character — an Imp star-pass
    onto a previously-learned Minion keeps that seat marked "already learned".
  effects: move `PlaceToken("bountyhunter","Know", Ref.TARGET, exclusive=true)` + `RecordChoice()`.
  expiry: `Expiry.NEVER` (correct today).
  edge case: **every** evil player already learned ⇒ *"All evil players have been learned — the
    Bounty Hunter learns nothing tonight."* Offer the good-player escape only if actually impaired.
  misregistration: `InfoCalc.bountyHunter` never calls `misregistrations(...)`. A Recluse may
    register as evil and is a legitimate target; a Spy registers good but *is* evil.
day: DUSK/day-end line when the Known player dies during the day: *"Abdallah (marked Know) died —
  the Bounty Hunter learns a new evil player tonight."* Day header from final four down, with an
  evil Townsfolk in play (ST-only): *"3 alive · 2 evil (including the evil Townsfolk)."*
death: none of its own; the trigger is the **Known player's** death (any cause).
ledger: the learn history (which seats have been shown) drives both the "already learned" gate and
  the wake condition.
tests:
  - no `alignmentFlippedBy == "bountyhunter"` seat ⇒ `SetupRequirements.unmet` reports it (silent today).
  - no `Know` / `Know` on a good seat ⇒ reported.
  - a Drunk shown as bountyhunter with no real BH ⇒ **no** evil-Townsfolk requirement (wiki Ex 3).
  - evil Townsfolk: `isEvil == true`, `team == TOWNSFOLK`, absent from Investigator's Minion list,
    counted as evil by the Empath.
  - N3 with a **living** Known seat ⇒ `Skip`, excluded from the dawn guard; Known died by execution
    on D2 ⇒ `Fire`; Known died **tonight** to the Demon ⇒ `Fire` in the same pass (Demon at idx
    37-54, BH at 86 — the order already supports it).
  - already-learned seats excluded; all-learned ⇒ the "learned nothing" message, not an empty list.
  - poisoned BH on a wake night ⇒ good players offered + the "they re-learn when that player dies" note.
  - `activeJinxes` returns the **corrected** Kazali text (fails today).
open: does the evil Townsfolk get an explicit **wake** on night 1, or only the reveal? The current
  wiki How-to-Run has no wake (an older townsquare dump did). Treat the wake as optional and the
  notification as mandatory.

---

## cannibal — Cannibal · experimental (Carousel) Townsfolk · P0:4 P1:5

today: **zero code.** The Cannibal never wakes (no night-order entry; it borrows the executee's slot — a concept the sheet cannot express); the gained ability is never derived even though `DeathEvent` already holds `characterIdAtDeath`; an evil executee never poisons the Cannibal; and "poisoned until a good player dies by execution" is a **conditional duration** the dawn/dusk tables cannot express at all.
data:
  - characters.json: ok — `reminders: ["Poisoned","Lunch"]` matches the official LUNCH/POISONED tokens (townsquare's "Died today" is stale).
  - night_and_jinxes.json: **no `cannibal` night-order entry — correct, keep it.** Butler/Juggler/Zealot jinxes present. **Add** `cannibal+princess`: "If the Cannibal nominated, executed, & killed the Princess today, the Demon doesn't kill tonight."
  - night_guide.json: **no entry at all.** Add `first`+`other`: run the executee's wake at their position; never say which ability; evil lunch ⇒ poisoned, fake a wake, prefer their bluff; LUNCH/POISONED handling; the Butler/Zealot jinxes.
setup: none.
identity: `AbilityGrant(abilityId = <lastExecutee.characterIdAtExecution>, sourceId = "cannibal", mode = GrantMode.REPLACE)`, **re-derived from `state.executions` on every query, never stored**: the last `ExecutionRecord` with `outcome == DIED` (never `EXILE` — an exile is not an execution), resolved to `diedInsteadId ?: playerId`, reading its `characterIdAtExecution` / `wasEvilAtExecution` **snapshots** so a later character change on that seat cannot rewrite history. day-engine's `ExecutionRecord` already carries all three fields — this character is the reason `wasEvilAtExecution` exists.
night.first / night.other (borrowed step):
  gate: `Fire` iff a living Cannibal **and** a lunch exists **and** the borrowed character would wake tonight **and** (for once-per-game borrowed abilities) the Cannibal's own spent marker is absent. Borrowed character that would not wake (Mutant; Chef on a later night; a spent Slayer) ⇒ **no row** (the wiki's Mutant example).
  key: `StepKey("cannibal", cannibalSeat, variant = borrowedId)` — resets when the ability changes, and sits at the **borrowed** character's order index (generalise the homebrew insertion at `NightOrder.kt:183-207` into a `virtualSteps` list). Title/subtitle `Cannibal` / `as the <Character> — <its night reminder text>`. `playerIds` = the **Cannibal's** seat, never the dead executee's.
  action: exactly the borrowed character's `NightAction`, with `sourceId = borrowedId` so its `TokenRule`s keep working (a borrowed Monk's `("monk","Safe")` still expires at dawn).
  info: `InfoCalc.compute(data, state, borrowedId, cannibalSeatId, targets)` **verbatim** — the signature already takes character and holder separately, so this works today and is simply never called that way. Caveats computed for the **Cannibal** as holder.
  evil-lunch variant: still offer the row (*"you may wake them … and pretend"*), headed **"FAKE — the Cannibal ate the evil <Character>. They are poisoned; invent an answer."**, with the false-info affordances shown unconditionally plus a chip *"Use <evil player>'s bluff instead"* from that player's recorded day claim.
  visibility: the Cannibal is **never told which ability they gained** — never offer a "show them the <Character> token" card here. Nothing to the Demon/Minions.
day (as `executionConsequences(...)`, not inside `execute`):
  - `PlaceToken("cannibal","Lunch", Ref.TARGET, exclusive=true)` on the seat that actually died.
  - executee **good** ⇒ `RemoveToken("cannibal","Poisoned")` from every seat. Executee **evil** ⇒ `Effect(kind = POISONED, sourceCharacterId = "cannibal", until = Until.EVENT, untilEvent = "goodDiesByExecution")` on the Cannibal — status-model already lists exactly this `untilEvent`.
  - jinx prompts at that moment: Butler lunch ⇒ *"Tell the Cannibal they have the Butler ability, and ask them to choose a Master."*; Zealot lunch ⇒ *"Tell the Cannibal they have the Zealot ability."*; Juggler lunch on the Juggler's first day ⇒ *"Tonight the living Cannibal learns how many guesses were correct."*; Princess ⇒ derivable from `Nomination.nominatorId`, already stored.
  - DAY_START briefing: *"Cannibal: has the <Character> ability"* / *"…poisoned (ate the evil <Character>) until a good player is executed."* Grimoire seat subtitle **derived, not written into `Player.note`**: `Cannibal · Clockmaker ability` / `Cannibal · poisoned (ate the Widow)`.
expiry: `("cannibal","Poisoned")` in **neither** table — removed only by the good-execution rule. `("cannibal","Lunch")` moves, never expires.
death: `deathNotes` on the Cannibal's own death: *"Cannibal: they keep an 'even if dead' borrowed ability (Recluse / Ravenkeeper / Sweetheart …) and lose the Cannibal ability."*
ledger: which execution fed the Cannibal (derived); the executee's recorded day claim, feeding the evil-lunch bluff suggestion.
tests:
  - Clockmaker executed D1 ⇒ N2 plan has a Cannibal step at the Clockmaker's index, `playerIds` = the Cannibal's seat.
  - Poisoner executed ⇒ `cannibal:Poisoned` placed, `Status.isImpaired` true, step flagged fake. Empath executed later ⇒ poison gone, step is now the Empath's.
  - Cannibal poison survives four `advancePhase` calls (guards against anyone adding it to a table).
  - Traveller `EXILE` ⇒ no lunch. Executed **already-dead** player ⇒ no lunch. `ExecutionOutcome.SURVIVED` ⇒ no lunch, no `Lunch` token. Two executions ⇒ exactly one `Lunch` token, on the latest.
  - `InfoCalc.compute(..., "empath", cannibalSeatId)` counts the **Cannibal's** neighbours. Executed Mutant ⇒ no cannibal step. Later character change on the executee's seat ⇒ the lunch still reports the snapshot.
open: four unsettled rules — (a) does a drunk/poisoned executee grant a **working** ability? (b) does an executed **Traveller** count? (c) is a once-per-game ability already spent by the executee refreshed? (d) does a later Professor resurrection revoke the gain? Make (d) one named constant; default = keep.

---

## choirboy — Choirboy · experimental (Carousel) Townsfolk · P0:2 P1:4

today: the setup companion (`[+the King]`) genuinely works, and `deathNotes` already warns
*"Choirboy (if in play) learns the Demon when the King dies to it."* But the step fires **every
night** with no way to know whether it should, and the trigger cannot be derived at all because
`DeathCause.DEMON` is the catch-all for "died at night" — Assassin, Godfather and Gossip kills are
indistinguishable from Demon kills, and the rules explicitly exclude Minion kills.
data:
  - characters.json: ok. Consider changing `otherNightReminder` to the official phrasing "If the
    Demon killed the King, wake the Choirboy and point to the Demon player." (current omits the wake).
  - night_and_jinxes.json: otherNight only, between Professor and Huntsman — ok.
    `kazali+choirboy` present and correctly worded ("The Kazali can not choose the King to become
    a Minion") — though data-accuracy flags it as possibly **retired**; verify before deleting.
  - night_guide.json: `other` entry good (includes the drunk/poisoned instruction). Add `shows`
    (see below); move the "the King is added at setup" sentence into a setup briefing.
setup: `[+the King]` already enforced via `requiredCompanionId` in `Setup.modifierFor`,
  `validateBag` and `randomBag` — **works, keep**. Two fixes: the guard message
  `"choirboy requires the king in the bag [+the King]"` is raw ids shown verbatim to the ST →
  *"The Choirboy adds the King — put the King in the bag in place of another Townsfolk."*; and add
  a setup briefing *"Choirboy is in play — the King is in the bag. The Demon learns who the King is
  on night 1."*
identity: plain.
night.other (never first):
  gate: `Fire` iff a living Choirboy **and** a `DeathEvent` this cycle with
    `characterIdAtDeath == "king"` and `cause.kind == DeathKind.DEMON_ABILITY` and not
    `registeredOnly`. Otherwise emit **no row** (the sheet gets shorter — that is the point).
    Always offer a `[Run anyway]` override for house rules / unusual Demons.
    **Prerequisite:** `KillCause.sourcePlayerId`/`sourceCharacterId` must be populated. The Demon
    kill panel already knows the holder, so this is free there; the seat sheet's "Died at night"
    button must ask *who killed them?* (in-play evil characters + Storyteller/other).
    Also needs **attacked-but-survived** recorded: an `Attack(targetId, killerPlayerId,
    killed=false)` list cleared at dawn, written by "No kill" / "Death prevented" — the rules hinge
    on *"If the Demon attacks the King but doesn't kill the King, the Choirboy doesn't learn"*, and
    that is exactly the state the ST must hold across two steps.
  action: none to pick — the app computes the answer.
  effects: none, no tokens, nothing deferred, nothing expires.
  info: `InfoCalc.supports("choirboy") = true`; returns `headline = "Point to <Name>"`,
    `detail = "<Name> is the <Demon character>. The Choirboy learns the PLAYER, not the character."`,
    `caveats = impairment + misregistration`.
    - multiple Demons alive ⇒ `"Point to one of: <names>"` + caveat *"More than one Demon is alive
      — your call."*
    - Demon changed player tonight (star-pass / Fang Gu jump) ⇒ caveat *"<Old> was the Demon when
      the King died; <New> is the Demon now. Show whoever is the Demon at this moment unless you
      rule otherwise."*
  impaired: they **still wake** and learn a **wrong** player (wiki: "The drunk Choirboy wakes and
    wrongly learns that the General is the Demon"). The generic false-info UI only offers numbers
    0-4 and YES/NO — this needs a `Show a WRONG player` chip row over every non-Demon seat, sorted
    good-and-plausible first.
  show: a `ShowCard.SeatCard(playerId)` rendering the **name** in very large type (the Choirboy
    learns a player, not a character). Same card the High Priestess needs — build it once.
  visibility: nothing to the Demon or Minions.
day: none. Dawn (ST-private, not announced): *"Choirboy learned the Demon last night."*
death: the trigger is the **King's** death, above. Also surface the Kazali jinx at the **Kazali's
  night-1 minion-creation flow**, not only in the seat sheet and jinx dialog.
ledger: the attack/kill attribution; what was shown, as `LedgerEntry(kind=TOLD)`.
tests:
  - King alive, no deaths ⇒ **no** `choirboy` step on N2/N3.
  - King killed by an Assassin (`killerCharacterId = "assassin"`) ⇒ no step.
  - King attacked but Monk-protected, recorded `killed = false` ⇒ no step.
  - King killed by the Imp ⇒ step exists between Professor and Huntsman; headline names the Imp's seat.
  - never present on the first-night plan.
  - poisoned Choirboy on a firing night ⇒ step still exists, caveats contain a POISONED entry.
  - Imp kills the King then star-passes the same night ⇒ headline names the **new** Demon, caveats
    mention the change.
open: multiple Demons (Legion, Lord of Typhon, Kazali-made) — which player is pointed at, and does
  a second Demon's kill count? Not settled; surface as an ST call, never auto-pick.

---

## cultleader — Cult Leader · experimental (Carousel) Townsfolk · P0:3 P1:5

today: `InfoCalc.cultLeader` correctly computes alive neighbours and their alignments — then always says *"your choice"*, even when both neighbours are evil and the rules **force** the outcome. The only way to apply it is `flipAlignment`, a **toggle**, which silently reverts an already-evil Cult Leader on the second consecutive evil night. The cult win condition does not exist in the app.
data:
  - characters.json: ok. Keep the official `firstNightReminder` as the displayed text.
  - night_and_jinxes.json: both positions ok. `pithag+cultleader` and `boffin+cultleader` present; align the Boffin wording ("due to their own ability" → "due to this ability").
  - night_guide.json: prose good. Add to both entries that the change is **forced** when both alive neighbours match; move the cult-win sentence into a day briefing.
setup: none.
identity: **an evil Cult Leader is still a Townsfolk** — `team == TOWNSFOLK` unconditionally, for the No Dashii, the Undertaker, the Baron count and the Alsaahir's Minion set. Only alignment moves. An evil Cult Leader is **not** woken with the Minions and does not learn the Demon.
night.first / night.other:
  gate: `Fire` iff the Cult Leader is **alive** (no row for a dead one). Always emitted for a living one — the ST must resolve the alignment even when no wake follows.
  action: the ST picks the **outcome**, not a target. Three-way: both alive neighbours good ⇒ **forced good**; both evil ⇒ **forced evil** (single primary button + a "No change (house rule)" escape); mixed ⇒ two buttons naming them, `Becomes good (Ben)` · `Becomes evil (Cara)`. `Status.impairment(cultLeader)` non-empty ⇒ **no change at all**, single disabled state, *"do not wake them"* (wiki Ex 2 & 3 — `NO_EFFECT`, **not** false info). Boffin jinx (Demon has the CL ability) or Pit-Hag jinx (an evil player was turned into the CL) ⇒ the **"becomes good"** option is removed, jinx text as the reason.
  effects: `GameActions.setAlignment(state, playerId, evil)` — a new **idempotent assignment** primitive setting `alignmentFlipped` so `isEvil == evil`. Keep `flipAlignment` for manual corrections only; every rule-driven change (Cult Leader, Snake Charmer, Pit-Hag, Bounty Hunter, Mezepheles) uses `setAlignment`. **P0 — the wrong primitive is the bug.** No tokens (correct — the character has no reminders); nothing expires.
  wake rule (Choirboy-shaped): wake **only if the alignment changed**. The step states *"Currently: GOOD"* before, and *"Changed — wake them and show the thumb"* / *"No change — do not wake"* after.
  info: rewrite the headline to one of *"Both alive neighbours are GOOD — the Cult Leader must be good"* / *"…must be evil"* / *"Neighbours differ — your choice: good (Ben) or evil (Cara)"* / *"Only one alive neighbour (Ben, good) — the Cult Leader takes their alignment"*; detail *"Currently good. Last night: good."* **Suppress the Vortox caveat** (`yieldsInformation` false) and give the impairment caveat a `NO_EFFECT` style.
  show: keep the two `AlignmentCard`s; show only the one matching the **new** alignment, and only when a change happened.
day: **the cult vote** — a `Form a cult` action, once per day, while a living Cult Leader is seated. Pre-flight: *"<Name> is the Cult Leader and is currently <good/evil>. If every good player raises their hand, <good/evil> wins."* Reuse the exile-style tally UI with candidates = **all good players, alive and dead**; dead good players may raise a hand and **no ghost vote is spent** (do not call `toggleGhostVote`). Live readout *"7 of 9 good players have joined — 2 missing: Iris, Noah."* Impaired CL ⇒ **still run the vote** (the table must not learn they are poisoned), then privately *"The Cult Leader is poisoned — no cult forms. Say nothing."* Record as `LedgerEntry(kind=STATEMENT, sourceId="cultleader", targetIds=joiners, verdict=TRUE/FALSE)`. **An evil Cult Leader who forms a cult wins for EVIL** — "your team wins".
death: `WinCheck` branch before the demons-dead branch: `allGoodJoined && !impairedAtVote` ⇒ `Advisory(goodWins = !cultLeaderEvil, ruleId = "cultleader-cult")`. Plus a standing caution while a living **evil** Cult Leader exists: *"An evil Cult Leader can end the game by forming a cult — good players must not all raise their hands."*
ledger: the nightly result (`"Cult Leader is now evil"` / `"stayed good"`) — the ST needs last night's value because *the wake condition is the change*. Alignment flips are invisible to `GameLogDialog` today (deaths and nominations only), yet are arguably the most consequential non-death state change in the game.
tests:
  - two good alive neighbours ⇒ `forced == false`, no "become evil" offered; two evil ⇒ `forced == true` and resolving makes `isEvil` true.
  - dead neighbours skipped: [CL, dead Empath, alive Poisoner] ⇒ the Poisoner is the neighbour.
  - `setAlignment(evil=true)` twice ⇒ still evil (today `flipAlignment` twice returns them to good).
  - poisoned CL with both neighbours evil ⇒ alignment unchanged, step reports "do not wake".
  - evil-turned CL ⇒ `team == TOWNSFOLK`, `isEvil == true`, an adjacent Empath counts them as evil.
  - cult vote by an **evil** CL with every good player joining ⇒ `goodWins == false`; one good player missing ⇒ no advisory; a dead good joiner's `ghostVoteUsed` unchanged.
  - Vortox in play ⇒ the cultleader step's caveats contain **no** VORTOX entry.
open: fewer than two distinct alive neighbours (3 alive, or CL as one of two survivors) — natural reading is "the single alive neighbour decides"; treat as an ST call and say so in the headline.

---

## engineer — Engineer · experimental (Carousel) Townsfolk · P0:2 P1:5

today: the thing this character most needs already works — the sheet is `remember(state.players, …)` and reads `characterId` live, so replacing the Imp with a Lleech mid-night makes the Lleech row appear and the Imp row disappear **in the same night**. Everything else is manual: no legality checks, no "spent" state (the row fires forever and blocks dawn), and `assignCharacter` leaves the **old character's reminder tokens in place**, so an Engineer-replaced Poisoner's victim stays poisoned for the rest of the game.
data:
  - characters.json: ok. `reminders: ["No Ability"]` — **capital A** (the character file wrote "No ability"; `Character.spentLabel` must carry the real spelling).
  - night_and_jinxes.json: both positions ok, and load-bearing — the Engineer acts **after** MINION_INFO (14) and DEMON_INFO (18) on night 1 and **before** every evil night action and every good info role. **Add two missing jinxes**: `engineer+legion` ("If Legion is created, all evil players become Legion. If Legion is in play, the Engineer starts knowing this but has no ability.") and `engineer+summoner` ("If the living Summoner is removed from play, the Storyteller has the Summoner ability.").
  - night_guide.json: detailed entries + a working `{"token":"pick"}` YOU ARE card. Add the "Minion info already ran" note to `first`; mark the drunk/poisoned sentence as the general once-per-game convention, not an Engineer-specific rule.
setup: none.
identity: plain. Its **effect** is identity changes on other seats.
night.first / night.other:
  gate: `Fire` iff the Engineer is **alive** and has no `("engineer","No Ability")` token. When spent, emit **no row** — the wiki says "remove their night token from the night sheet". Legion in play ⇒ a **first-night-only** row: *"Legion jinx: the Engineer starts knowing Legion is in play and has no ability — show them the Legion token"*, then never again.
  action: `ChooseCharacter` in two **mutually exclusive** modes plus a decline — `They chose the Demon` (exactly 1 Demon from the script) · `They chose the Minions` (exactly `Setup.distributionFor(residents).minions`) · `They declined` (no change, **no spend**, row returns tomorrow). Pool = `gameData.resolve(state.script)`, chosen team only. Already-in-play characters are **shown but flagged** *"already in play — this choice is wasted"*; selecting one is legal and **still spends** the ability (the Engineer is not told). Sort not-in-play first.
  effects (one undoable `GameActions.engineerRebuild(state, engineerId, mode, chosenIds, lookup)`):
    1. target seats = all Demon-team seats (Demon mode) or all Minion-team seats (Minions mode);
    2. characters already held by a seat in the target set **keep that seat** ("that character stays as the same player"); the rest assign in seat order; count mismatch ⇒ assign as many as possible + *"You chose N, there should be M — change as many as is fair"*;
    3. per changed seat `BecomeCharacter(Ref.TARGET, newId, evil = true, clearOldTokens = true)` — which **must remove every reminder whose `sourceId` is the old character id, from every seat in the grimoire**, and reset `alignmentFlipped = false` / `shownCharacterId = null`. **P0.**
    4. `MarkSpent("engineer")` → `PlaceToken("engineer","No Ability", Ref.SOURCE)` — **always**, including the wasted-choice and impaired cases; an impaired Engineer skips 1-3 but still runs 4.
  deferred: the new characters' own steps appear in the same night's plan (already works; lock it). expiry: `("engineer","No Ability")` — `Expiry.NEVER`.
  visibility: drive a **wake sequence** — changed seats in seat order, each one tap opening `ShowCard.CharacterCard("YOU ARE", newId)`, ticked as it is done. Today the single `» New character` chip opens a search box with **no memory of which seat is being woken**; with three Minions changed the ST repeats it from scratch three times. Nothing is shown to the Engineer. Night-1 note: *"Minion info and Demon info have already run — the evil players know each other; you only need to show the new tokens."*
  warnings in the step: expected Minion count · "<Character> is already in play — choosing it wastes the ability" · "<Character> is one of the Demon's bluffs" (`demonBluffIds ∩ inPlayIds`, cheap and currently unchecked) · *"Setup abilities do not re-fire: the Baron's extra Outsiders stay"* (wiki Ex 2 — `validateSetupState` correctly is not re-run, so this already holds) · Marionette/Lunatic/positional-Demon structural warnings, by lifting `validateSetupState`'s per-character checks into a `SetupRequirements.unmet(state, lookup)` callable at any time · Summoner jinx · Legion jinx (choosing Legion assigns `legion` to **every** evil seat).
day: none. death: none.
ledger: `LedgerEntry(kind=CHOICE, sourceId="engineer", characterIds=chosen)` + `kind=SPENT`.
tests:
  - spent Engineer ⇒ no `engineer` step on N3; dead Engineer ⇒ no step.
  - Demon swap: seat becomes `lleech`, `alignmentFlipped == false`, **no reminder with `sourceId == "imp"` remains anywhere**. Poisoner→Godfather ⇒ the victim has no `sourceId == "poisoner"` reminder and is no longer impaired.
  - choosing Spy+Assassin+Witch when exactly those are in play ⇒ nothing changes, ability spent. Spy+Assassin+Mezepheles over Spy/Assassin/Witch ⇒ only the Witch's seat changes (wiki Ex 4).
  - impaired Engineer ⇒ no seat changes, `("engineer","No Ability")` placed.
  - new Demon gets its step in the **same** night's plan (regression guard on today's behaviour).
  - 12 residents (2 Minions expected) + 3 chosen ⇒ warning names the expected count. Choosing Legion ⇒ all three evil seats become `legion`.
open: does "the Minions" include the **Marionette**? Default to including and warn (a Marionette replaced by an ordinary Minion breaks its neighbour constraint). A mid-game Kazali / Lord of Typhon / Lil' Monsta with setup or positional requirements is unhandled.

---

## farmer — Farmer · experimental (Carousel) Townsfolk · P0:3 P1:4

today: `deathNotes` already warns *"Farmer: a living good player becomes a Farmer tonight"* from both kill paths, and `DeathEvent` already stores `atNight`/`characterIdAtDeath`/`impairedAtDeath` — every input the trigger needs, none of them read. The row fires every night and blocks dawn; the transfer is a multi-screen manual `Change character` with **no filter at all**, so an evil player, a dead player, or the Farmer's own corpse can be made the new Farmer.
data:
  - characters.json: ok — "When you die at night, an alive good player becomes a Farmer" is the current text (townsquare's "If you die at night…" is stale). No reminders — correct.
  - night_and_jinxes.json: otherNight only, between Amnesiac and Tinker — ok. **Both Farmer jinxes are drifted**: file says "If Leviathan is in play and a Farmer dies by execution, a good player becomes a Farmer that night" / "If Riot kills the Farmer, a good player becomes a Farmer tonight"; current official text is "Each night*, the Leviathan/Riot chooses an alive good player (different to previous nights): a chosen Farmer uses their ability **but does not die**." Same drift affects `leviathan+ravenkeeper`, `leviathan+sage`, `riot+ravenkeeper`, `riot+sage`, `riot+grandmother` — one re-scrape pass.
  - night_guide.json: `other` entry good, with a working `{"token":"self"}` YOU ARE + Farmer card. Add "day deaths don't count" and the eligibility rule (alive **and** good **by alignment**).
setup: none.
identity: the new Farmer **stops being their old character entirely** and does not have that ability; they need **not** be a Townsfolk. Wiki Ex 2: a Choirboy then a Heretic become Farmers — *"there is now no Heretic and no Choirboy in play"*, which changes what other characters can truthfully be told.
night.other (never first):
  gate: `Fire` iff `farmerTriggers(state).isNotEmpty()`, a trigger being a `DeathEvent` with `cycle == state.cycle && atNight && characterIdAtDeath == "farmer" && cause.kind != EXECUTION`. `atNight` already excludes day executions (set from the phase at kill time); keep the cause guard as belt and braces. Empty ⇒ **no row**. **One row per trigger** — `StepKey("farmer", deadFarmerId)` — so an Al-Hadikhia or Riot night killing two Farmers produces two separately-tickable rows; today the title concatenates every holder (`"Farmer — Julian †, Kira †, Sarah"`) with no clue which one died. Jinx: a Leviathan/Riot-chosen Farmer produces a trigger **with no death**.
  action: `ChoosePlayers("farmer", "Choose an alive good player", min=1, max=1, constraints=[ALIVE, GOOD])` where GOOD is `!player.isEvil(lookup)` — **alignment, not team**; an evil-turned Cult Leader is not eligible. Exclude the dead Farmer; already-a-Farmer is pointless but harmless (allow with a warning). Sort Townsfolk then Outsiders; within each, seats whose ability is already spent first (they cost the good team least). Each chip shows the current character so the ST sees what is being spent: `Sarah — Alchemist`, `Iris — Heretic (leaves play)`.
  effects: `BecomeCharacter(Ref.TARGET, "farmer", evil = false, clearOldTokens = true, reRunFirstNight = **false**)` — night-engine rules this explicitly: *"new Farmers do **not** receive first-night information"*. `clearOldTokens` is load-bearing: the Drunk's `("drunk","Is the Drunk")`, a Marionette's token, a red herring must all go, or `Status.impairment` reports them impaired forever. Leave `alignmentFlipped`, `alive`, `ghostVoteUsed`, `note` alone.
  impaired case: when the dead Farmer was impaired at death, **still emit the row** with no picker and the text *"The Farmer was drunk/poisoned when they died — no one becomes a Farmer."*, so the ST sees the rule applied rather than a missing row. No eligible player ⇒ *"No alive good player — nobody becomes a Farmer."*, no action.
  info: none computed. `InfoCalc` need not support the Farmer — instead lift caveat rendering out of the `InfoCalc.supports` gate so impairment warnings appear here at all (§group 3).
  show: keep the `YOU ARE` + Farmer card but **bind it to the chosen seat**, so the step can mark it shown and the ST can verify the grimoire token actually changed. Nothing to the Demon, Minions or the dead Farmer.
day: none. Dawn (ST-private): *"<Name> is now the Farmer. The <old character> is no longer in play."* Script-level note when a Farmer is in the bag: *"Characters can leave play mid-game — re-check any info that depended on the <old character>."*
death: `DeathTrigger(characterId = "farmer")`, night deaths only. `deathNotes` should say which branch applies: *"they die at night, so an alive good player becomes a Farmer tonight"* / *"Farmer is poisoned — no new Farmer"* / *"Executed — no new Farmer."*
ledger: which seat became a Farmer and which character left play (invisible to the log today).
tests:
  - living Farmer, no deaths ⇒ no step whose id starts with `"farmer"`. Killed at night on cycle 2 ⇒ `"farmer:<id>"` between Amnesiac and Tinker. Executed on D2 ⇒ no step (`atNight == false`).
  - poisoned Farmer's night death ⇒ trigger `impaired`, no target picker exposed.
  - candidate list excludes evil, dead, and an `alignmentFlipped` Cult Leader (guards against filtering on `team` instead of `isEvil`).
  - the Drunk becomes the Farmer ⇒ `characterId == "farmer"`, `shownCharacterId == null`, no `sourceId == "drunk"` reminder anywhere, `Status.isImpaired == false`.
  - three Farmers coexist (two dead); only the one who died tonight emits a step. Two Farmer deaths in one night ⇒ two separate rows.
  - Leviathan jinx: the chosen Farmer stays **alive** and a new Farmer can still be selected.
open: (a) does a **resurrected** Farmer's death still transfer? One named constant; default = keep the trigger (they did die). (b) may the **Drunk** be chosen, and do they become a genuinely sober Farmer? Both are the same class of question as the Cannibal's.

---

## fisherman — Fisherman · experimental (Carousel) Townsfolk · P0:0 P1:3

today: **nothing** — two data hits and no code. The Day screen has nominations, votes, executions
and exiles and no surface for any character ability. The "Mark spent" convenience exists only
inside `NightToolTray`, which renders for the currently expanded **night step**, and the Fisherman
has none. The only free-text surface is one undated game-wide notes blob.
data:
  - characters.json: ok — "to help **your team** win" matches the current wiki (townsquare says
    "to help you win"; a future re-scrape could regress it). `reminders: ["No Ability"]` —
    **capital A**.
  - night_and_jinxes.json: correctly absent from both night orders and from `jinxes` (there are
    none). **Keep it absent** — add a test guarding against anyone "fixing" the missing entry.
  - night_guide.json: correctly **no** entry (not a night ability). Day-side run-book text
    ("How to run" + Tips) needs a parallel `day_guide.json` keyed the same way.
setup: none.
identity: plain.
night: **none, ever.**
day:
  surface: a general **day-abilities panel** on `DayScreen`, above "New nomination", one row per
    living holder of a day-time ability, driven by a small table (`characterId → DayAbility(label,
    spendable, kind)`) rather than by ability-text parsing. Registering the Fisherman in it is one
    row; the same panel serves Slayer, Artist, Savant, Juggler, Gossip, Alsaahir, Amnesiac, Klutz,
    Moonchild, Mayor and the Cult Leader's cult vote. Dead holders render greyed, *"dead — no
    ability"*. Spent state = a `("<id>","No Ability")` token on the seat, the existing convention.
    ```
    Fisherman — Sarah        [ Give advice ]        unspent
    Artist — Iris            —                      spent (day 2)
    ```
  flow: eligibility = alive **and** no `("fisherman","No Ability")` token; otherwise disabled with
    the reason shown. Private compose sheet → on confirm, `MarkSpent("fisherman")` +
    `LedgerEntry(kind=PRIVATE, sourceId="fisherman", text=<advice>, impaired=<bool>)`.
  compose sheet context (read-only): `Fisherman: Sarah — alive, not impaired` or the impairment
    banner; who is on the block; alive good / alive evil counts; and the rule the wiki leads with —
    ***advice on what to DO, not information about what IS***.
  impaired: `Status.impairment(fisherman)` non-empty ⇒ error-coloured header *"Sarah is
    DRUNK/POISONED (<source>) — you may give bad advice."* The ability is spent either way. Getting
    this wrong hands a poisoned player a true, game-winning tip.
  privacy + repeat: a `Show full-screen` button routes the typed text to `ShowCard.Message`, then
    `PrivacyCover` on dismiss. The wiki explicitly supports the player **coming back to have the
    advice repeated**, so the panel keeps a persistent row *"Fisherman — advised day 2: '…'"* with
    a **Repeat** button replaying it verbatim. This is the whole reason the text must be stored.
  log: `D2 · Sarah (Fisherman) used their ability` in `GameLogDialog`. Keep the **advice text**
    behind the same reveal as storyteller notes if the log is ever shown to players.
  DAY_START briefing: *"Unspent day abilities: Fisherman (Sarah), Slayer (Ben)."* ·
    *"Sarah (Fisherman) is poisoned today — advice you give her may be bad."*
expiry: `("fisherman","No Ability")` — `Expiry.NEVER`.
death: none. A dead Fisherman has no ability (general rule; the page does not restate it).
ledger: the advice text, its day, and the impairment flag at the time it was given.
tests:
  - living unspent Fisherman ⇒ a `fisherman` row with `spent = false`, `enabled = true`.
  - using it places `("fisherman","No Ability")` and flips the row to spent.
  - the spent marker survives four `advancePhase` calls (guards against the expiry tables).
  - spent ⇒ row disabled, reason "already used"; dead ⇒ disabled, reason "dead — no ability".
  - advice recorded with `day = 3` and `impaired = true` when the seat holds `poisoner:Poisoned`.
  - Repeat yields the text **verbatim** on day 5.
  - undo restores the unspent state (everything must go through `viewModel.update`).
  - neither night plan ever contains a `"fisherman"` step.
open: none.

---

## general — General · experimental (Carousel) Townsfolk · P0:1 P1:3

today: the row appears every night with the detail "Give a thumb signal." and three chips. Tapping
**`» Good winning` shows the player a full-screen card reading "YOU ARE GOOD"** — a different
ability's card, telling the General a fact about themselves instead of the answer. No impairment
warning (the `InfoCalc.supports` gate), no decision support, no record of what was signalled.
data:
  - characters.json: replace the bare `firstNightReminder`/`otherNightReminder` "Give a thumb
    signal." with the fuller official form — "Show the General thumbs up for good winning, thumbs
    down for evil winning or thumb to the side for neither" — so the collapsed row is useful alone.
    No reminders — correct.
  - night_and_jinxes.json: firstNight 69 / otherNight 92 ok, late (after Butler and Spy, before
    Chambermaid) — which is exactly what *"the Storyteller decides who is winning **at the point
    that the General wakes**"* requires. No jinxes.
  - night_guide.json: prose good. Make **"neither"** a first-class card kind instead of
    `kind: "message"` (today it opens a dialog with an editable text field and a confirm button —
    two taps and a keyboard risk for the answer the ST reaches for most when unsure). Note in
    `first` that night-1 answers are usually "neither".
setup: none.
identity: plain.
night.first / night.other (every night):
  gate: `Fire` iff the General is **alive**. Emit no row for a dead one.
  action: none — a judgement, not a computation. `InfoCalc` must **not** invent an answer.
  effects: none, no tokens, nothing deferred, nothing expires.
  show (**P0, cross-cutting**): `NightScreen.kt:806-816` discards `GuideShow.text` for
    `kind: "good"|"evil"` and routes to `ShowCard.AlignmentCard(evil = …)`, which renders
    "YOU ARE GOOD"/"YOU ARE EVIL". Fix: extend `AlignmentCard` with `text: String` defaulting to
    the current subtitles, render `card.text` under the big GOOD/EVIL word, and pass `show.text`.
    The General's card then reads **GOOD / GOOD IS WINNING**. Add a neutral third card (big `—` or
    "NEITHER" with subtitle "NEITHER TEAM IS WINNING") so all three answers cost one tap and look
    like siblings. **The same discard corrupts `villageidiot` ("THIS PLAYER IS GOOD"), `eviltwin`
    ("This player is EVIL") and `mezepheles` ("YOU ARE NOW EVIL").**
  info (a *dashboard*, explicitly labelled **"Your call — these are just the facts"**; every input
    is already in state and none of it is surfaced at 2am, ten steps deep in a night sheet):
    `Alive: 6 good / 3 evil` (by `isEvil`, so a turned Cult Leader counts correctly) ·
    `Evil dead: Poisoner (executed D2). Evil alive: Imp, Baron.` · `Good dead: 4 — …` ·
    `On the block yesterday: …` · `Impaired good players tonight: Ben (Poisoner), Eve (is the
    Drunk)` — the proxy for "how much of the good team's info is false" · `Demon bluffs still
    unclaimed: Soldier, Librarian` (from `demonBluffIds` vs recorded day claims) ·
    `Last night you signalled: EVIL WINNING (N2), NEITHER (N1)`.
  impaired: *"The General is DRUNK/POISONED — you may give any signal."* The three chips become
    free choices. Because there is **no true answer to invert**, the numeric/YES-NO false-info UI
    does not apply, and the Vortox caveat must be suppressed (`yieldsInformation("general") == false`).
  visibility: nothing to the Demon, Minions or anyone else.
day: none. The day-claims store, once it exists, feeds the "bluffs still unclaimed" line.
death: none.
ledger: `LedgerEntry(kind=TOLD, sourceId="general", actorId=generalSeat, shown="GOOD IS WINNING")`
  on each chip tap. Render the last 3 inline on the step and add them to the game log
  (`N2 · General was shown: EVIL WINNING`). The General's information **is a series** — the player
  reads the trend, and the ST must stay coherent with it (and reproduce it if a Cannibal later
  gains the ability).
tests:
  - a `GuideShow(kind="good", text="GOOD IS WINNING")` maps to a card whose subtitle is
    `"GOOD IS WINNING"`, not `"YOU ARE GOOD"` (**today the mapping discards it**).
  - same assertion for `villageidiot` and `eviltwin` — regression guard for the cross-cutting fix.
  - a living General has a step on **both** night plans, after the Spy and before the Chambermaid.
  - a dead General has a step on neither.
  - the dashboard counts an `alignmentFlipped` Cult Leader as evil (`5 good / 3 evil`).
  - evaluated after the Demon's step killed a Townsfolk on cycle 3, the alive-good count excludes
    them (guards *"at the point that the General wakes"*).
  - a General holding `poisoner:Poisoned` ⇒ the step's caveats contain a POISONED entry
    (**fails today** — caveats are gated behind `InfoCalc.supports`).
open: what a drunk/poisoned General is shown. The wiki is silent; "you may give any signal" is the
  standard convention for a judgement ability with no true answer — flagged as convention.

---

## highpriestess — High Priestess · experimental (Carousel) Townsfolk · P0:0 P1:4

today: the row appears every night with the detail "Point to a player.", `shows: []` so **the panel
below the prose is empty**, and `InfoCalc.supports` excludes it so there is no picker, no caveats
and no record. The ST decides in their head, points across the table, and ticks the box. Nothing is
stored anywhere — which makes the character's defining texture (the sequence, and what a **repeat**
means) impossible to honour.
data:
  - characters.json: keep "Point to a player." or lengthen to "Wake the High Priestess and point to
    the player you think they should talk to most." so the collapsed row is self-sufficient. No
    reminders — correct.
  - night_and_jinxes.json: firstNight 68 (after Ogre, before General) / otherNight 91 (after Spy,
    before General). **Unverified** — the High Priestess post-dates the townsquare snapshot and the
    wiki page states no position. The relative placement (very late, just before the General and
    Chambermaid) matches every other ST-judgement role, so it is probably right; worth one human
    check against a current printed night sheet. No jinxes.
  - night_guide.json: prose good. Add a `shows` entry for the seat card once it exists; add "a
    repeat is a deliberate signal" to the `other` entry.
setup: none.
identity: plain.
night.first / night.other (every night):
  gate: `Fire` iff the High Priestess is **alive**. Emit no row for a dead one.
  action: `ChoosePlayers("highpriestess", "Who should they talk to most?", min=1, max=1,
    constraints = [ANY_LIVING_STATE, SELF_ALLOWED])` chosen by the **Storyteller**, not the player.
    **Do not filter**: alive or dead, good or evil, Travellers included — the wiki says so
    explicitly. Self is allowed but flagged *"not covered by the rules — your call"*.
  picker sort — this is where the app earns its keep: (1) players **not yet shown**, ahead of
    repeats; (2) within those, seats carrying something worth steering — impaired, mad
    (`("cerenovus","Mad")` / `("harpy","Mad")`), misregistering (Spy/Recluse), on the block, or
    holding a strong unshared claim; (3) everyone else in seat order. Each chip annotated with what
    the app knows: `Ben — Empath (poisoned)`, `Sarah — Saint · shown N1, N2, N3`,
    `Doug — Drunk (believes Washerwoman)`.
  effects: none — no tokens, no state change beyond the ledger entry. Nothing expires.
  info: nothing computed; `InfoCalc` must **not** manufacture an answer. But caveat rendering must
    be lifted out of the `InfoCalc.supports` gate so impairment and dead-holder warnings appear
    (§group 3), and the Vortox caveat must be suppressed (`yieldsInformation` false).
  impaired: *"The High Priestess is DRUNK/POISONED (<source>) — point to any player."* Same picker;
    there is no true answer to invert.
  show: `ShowCard.SeatCard(playerId)` — the player's **name** in very large type, no character art,
    because the High Priestess learns a *player*. `ShowCard` today covers messages, numbers,
    alignments, character tokens, bluffs and character sheets but **not "this player"**. The
    Choirboy needs the identical card; build it once.
  history (rendered inline on the step): `Shown so far: Sarah ×3 (N1, N2, N3), Julian (N4)` plus
    *"A repeat tells them the last conversation didn't land."* The wiki tells the **player** to read
    repeats as signal, so the ST must know what they showed and must mean something by repeating.
  visibility: nothing shown to anyone else.
day: none. A day-claims store would improve the picker annotations.
death: none.
ledger: `LedgerEntry(kind=CHOICE, sourceId="highpriestess", actorId=hpSeat, targetIds=[shown])`
  each night, surfaced in the step history and in the game log. Same record the Cannibal needs if a
  Cannibal ever gains this ability.
tests:
  - a living High Priestess has a step on both plans, each immediately before the General's slot.
  - a dead one has a step on neither.
  - choices on N1/N2 (Sarah) and N3 (Julian) ⇒ three CHOICE entries with the right cycles and ids.
  - on N4 the history reports Sarah twice and Julian once, and un-shown players sort ahead of both.
  - a dead Chef and a living Goblin are **both** selectable (guards against copying the Farmer's
    alive-and-good filter).
  - a High Priestess poisoned by No Dashii adjacency ⇒ caveats contain the No Dashii reason
    (**fails today** — the `InfoCalc.supports` gate).
  - a Vortox in play ⇒ **no** "Townsfolk info must be FALSE" caveat.
  - undo then redo leaves `ledger` identical.
open: (a) may the High Priestess be shown **themselves**? Not addressed. (b) what is a
  drunk/poisoned High Priestess shown? Not addressed — "any player" is the convention for a
  judgement ability. (c) night-order placement unverified (see data).
