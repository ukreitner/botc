# Digest — exp-demons-B + travellers-B

Characters: lleech, lordoftyphon, ojo, riot, yaggababble (Experimental Demons) ·
barista, bonecollector, butcher, deviant, harlot (SV Travellers) ·
gangster, gnome (Experimental Travellers). **Totals: P0 43 · P1 50.**

## Group notes

1. **The Pukka bug is systemic, not per-character.** All five Demons here fall through
   `QuickResolutions`' `else` branch (`NightScreen.kt:518-523`) into `DemonKillPanel`
   (`:534-638`) and are offered a kill on a night they have none: Lleech N1 (host+poison),
   Lord of Typhon N1 (convert neighbours), Yaggababble N1 (no kill ever, count is 0),
   Riot (never kills at night), Ojo (wrong modality — it picks a *character*). One
   `StepGate` + per-character `NightAction` table deletes all five at once.
2. **`characters.json` has drifted from its own `raw_*.json` sources.** Proven for `riot`
   (superseded ability text **and** `setup: true` vs upstream `false`). Add a repo-wide
   data-parity test — this class of bug is invisible and changes rules the ST reads.
3. **Conflict with `setup-and-identity.md` D.1:** its provisional `riot` `BagShape`
   (`minions = 0..0`, `demons = base.minions+1`) is wrong under the current text. Riot's bag
   is **ordinary** (1 Demon, base Minions, base Outsiders); Minions become Riot on night 3.
   Also drop `riot` from `Setup.TEAM_WARPING_IDS` (`Setup.kt:72`) — it currently disables all
   distribution validation for every Riot script. Update `SetupTest.kt:105-111`.
4. **Conflict with `setup-and-identity.md` D.1:** the `lordoftyphon` shape note says "the 3
   Minions". It is `base.minions + 1`, and the clockwise/anticlockwise **split is a
   storyteller choice** (≥1 each side), not an even one. `lordoftyphon.minions` in the
   SetupTasks table must be `PICK_NUMBER(split)` + `PICK_CHARACTER` per created seat, with a
   uniqueness constraint.
5. **Conflict with `status-model.md` §3 cause table:** it files `harlot` under
   `GOOD_ABILITY`, and omits `gangster` and `gnome` entirely. Travellers may be **evil**.
   Add `DeathKind.TRAVELLER_ABILITY` (or derive from the source seat's alignment); no
   protection in the precedence table keys off GOOD vs EVIL, so this is log/record fidelity,
   but the Gangster/Gnome/Harlot kills need a `KillCause` at all before `execute`/`kill`
   can stamp one.
6. **Multi-holder holder-resolution is a hard prerequisite here, not a nicety.** Riot is
   legitimately multi-seat every single game (every Minion becomes a Riot on day 3);
   Lleech/Ojo/Yaggababble/Lord of Typhon hit it via star-pass, Pit-Hag, Kazali, Summoner.
   `StepKey(id, playerId)` + `Player.canAct` must land before any of these five specs.
7. **Two engine primitives unlock six of these twelve.** (a) `Status.killOutcome`
   intercepting **every** kill site — Lleech life-link, Deviant exile, Gangster/Gnome/Harlot
   day kills, Yaggababble per-victim protection; today `DayScreen.kt:111-114`, `:350-357`,
   `GameShell.kt:596-604` and `SeatSheet.kt:274` all kill unconditionally. (b) the
   first-night re-run (`StepKey(variant = "first")`, `WakeStyle.FIRST_NIGHT`) shared by the
   Bone Collector and the Professor — the user's own request.
8. **Four travellers have literally zero app presence** (butcher, deviant, gangster, gnome:
   `grep` returns only data files). They need `DayBriefing.Slot.DAY_START / NOMINATION /
   EXECUTION`, a day-tools panel (the day-side twin of `QuickResolutions`), and a
   `day_guide.json` — `night_guide.json` is night-only (`NightGuide.kt:56-59`), so their
   entire How-to-Run has nowhere to live.
9. **Open for the lead:** Riot's four "each night\*" jinxes (Banshee / Farmer / Ravenkeeper
   / Sage) grant Riot a nightly "choose an alive good player, different to previous nights"
   that appears nowhere in Riot's own ability text. Same wording as the Leviathan jinxes, so
   it looks deliberate — but verify against a printed script before wiring a Riot night step.

### Shared traveller defects (T1–T8) — apply to all seven travellers in this group

- **T1 · P0 · Alignment is never asked, recorded, or shown.** `Player.isEvil`
  (`GameState.kt:49-52`) returns `Team.TRAVELLER.isEvil == false`, so every traveller counts
  as **good** until the ST remembers "Flip alignment" (`SeatSheet.kt:315`). Chef, Empath,
  Investigator, Fortune Teller, Alsaahir, Gnome candidate lists and every win check silently
  compute wrong numbers. An **evil** traveller must also be shown **who the Demon is** (and
  no other evil characters, no bluffs — Travellers page). `SetupTasks` has
  `traveller.alignment.<seatId>` but only fires at SETUP; travellers overwhelmingly arrive
  mid-game.
- **T2 · P1 · Arrival is a four-screen scavenger hunt.** Menu → "Add seat"
  (`GameShell.kt:254-257`, dialog `:663-682`) collects a name and appends at the **end** of
  the circle — `GameActions.addSeat` accepts `afterId` (`GameActions.kt:19-26`) but the
  dialog never passes it — then Reorder seats → tap seat → Change character → scroll past
  the whole script (`SeatSheet.kt:439-451`) → Flip alignment.
  **Needed: one `ArrivalTask` flow** — name → seat position → character → alignment →
  (if evil) show the Demon → public announcement → hand over the Traveller sheet → any
  character-specific follow-up (Gnome amigo).
- **T3 · P1 · No public-announcement affordance.** A traveller's identity and ability are
  public on arrival; nothing produces that card, and nothing reminds the ST to give the
  Traveller sheet (Minion/Outsider counts).
- **T4 · P0 · Only alive players may call for an exile.** `DayScreen.kt:135-138` gates the
  Nominator chip on `p.alive`. Glossary: *"Any players may support an exile, even dead
  players without a vote token."* Same gate blocks the Riot day-3 chain, the Butcher's extra
  nomination, and any Bone-Collector-restored dead day ability. `DayRules.canNominate` must
  own this.
- **T5 · P2 · The exile flow is hidden inside the nomination card.** `isExile` is only
  derived after a nominator *and* nominee are picked (`DayScreen.kt:161-164`). No "Call for
  exile" entry point; wording stays nomination-flavoured.
- **T6 · P1 · Departure is destructive.** `SeatSheet.kt:317-321` → `GameActions.removeSeat`
  (`:29-30`) deletes the `Player`, orphaning every `Nomination`/`DeathRecord` that references
  the id (the log then prints "?" — `GameExtras.kt:65-66`). Use `Player.leftGame = true`
  (already proposed in `status-model.md` §6) instead.
- **T7 · P1 · Traveller night steps are not gated on alive/spent.**
  `NightOrder.kt:143-145` emits a step whenever any seat holds the id. A dead traveller has
  no ability. Hits barista, bonecollector, harlot. `Gates.aliveHolder` fixes all three.
- **T8 · P2 · Token reachability.** Traveller tokens live only in the `NightToolTray`
  (`NightScreen.kt:283-354`), reachable only while a night step is expanded. Day-only tokens
  (`gnome:Amigo`, `riot:Day N`) are therefore unreachable when they are actually needed, and
  the generic `ReminderPicker` fallback (`SeatSheet.kt:501`, `:529`) writes `sourceId = ""`,
  which can never expire and — for anything containing "poison"/"drunk" — poisons forever.

---

## lleech — Lleech · Experimental Demon · P0:4 P1:6

today: night 1 offers a Demon **kill** instead of a host choice; the host's death does
nothing; the Lleech can be killed while its host lives; the one advisory line
(`StatusEffects.kt:78`) prints unconditionally and is wrong in three states.

data:
- characters.json: `:2037` firstNightReminder → `"The Lleech points to a player. Place the
  Poisoned reminder token."` (today identical to otherNightReminder). Add `"Host"` to
  `reminders` (`:2039-2042`, currently `["Dead","Poisoned"]`). Ability text `:2035` ok.
- night_and_jinxes.json: `:79-82` Heretic jinx text is **stale** — replace with *"Only 1
  jinxed character can be in play."* Add `lleech`/`mastermind`: *"If the Mastermind is alive
  and the Lleech host dies by execution, the Lleech lives but loses their ability."*
  Slayer jinx `:179` ok. Order (first 25, other 49) ok.
- night_guide.json: `:1613` drop *"poisoned for as long as the Lleech lives"* (no such rule);
  add *"A drunk or poisoned Lleech loses the life-link: it can be killed, and it survives its
  host's death."*; add to `other`: *"The Lleech may kill its own host — that kills the Lleech
  too."*

setup: none.

identity: plain. A self-hosted Lleech is permanently `POISONED` → `NO_ABILITY` in effect:
no kill, no life-link.

night.first:
  gate: `Gates.aliveHolder` AND no `lleech:Host` exists anywhere. Fires once per game.
  action: `ChoosePlayers("lleech", "Who is the Lleech's host?", min=1, max=1,
    constraints=[ANY_LIVING_STATE, SELF_ALLOWED], sort=ALIVE_FIRST)`. Self chip present,
    last, labelled *"(self — permanently poisoned, loses all ability)"*. Travellers legal.
  effects: `PlaceToken("lleech","Host", TARGET, exclusive)` ·
    `PlaceToken("lleech","Poisoned", TARGET, exclusive)` **unless** the ST unticks
    *"Host is immune to the poison (Soldier / Goon)"* — host-ness and poison are **separate
    facts** (official Soldier ruling). Effects: `POISONED (until = FOREVER,
    endsWithSource = false)` + `DEATH_TIED_TO` on the Lleech, `linkedPlayerId = host`.
    Goon host → offer one tap placing `goon:Drunk` on the Lleech (Expiry.DUSK) and flipping
    the Goon evil.
  deferred: the life-link (below). **No kill on night 1.**
  info: none. show: existing "CHOOSE YOUR HOST" message card. visibility: Minions are not
    told the host.

night.other:
  gate: `Gates.aliveHolder` · `Gates.notExorcised` → `StepGate.Reduced` (choice half only) ·
    self-hosted ⇒ `Skip("self-hosted — permanently poisoned, no kill")`.
  action: `ChoosePlayers(min=1, max=1, constraints=[ALIVE, SELF_ALLOWED], allowNone=true)`.
  effects: `Attack(TARGET, DeathKind.DEMON_ABILITY)`. If the target **is the host**, lead in
    red: *"That is the Lleech's host — killing them kills the Lleech and ends the game for
    evil."*; confirm reads `<host> dies » the Lleech dies » good wins`.

death: **`StandingRule("lleech")`** emits `DEATH_TIED_TO` on the Lleech seat linked to the
  host. `killOutcome` step 2 (`DEATH_TIED_TO` with a living linked host) → `Blocked`, and the
  guard must be **absolute**: execution, exile, Slayer, Gossip, Godfather, Tinker, Assassin,
  Psychopath, Gambler, its own self-choice, Vigormortis. It is an *ability*, so an impaired
  Lleech loses it (wiki Example 2) — `Effect.endsWithSource`/`abilityWorks` gives this free.
  **DeathTrigger("lleech")** on the *host's* `DeathEvent`, any cause, day or night, firing
  **immediately** in the same action:
  - Lleech impaired → nothing; `Prompt(NOW, ANNOUNCE)`: *"The Lleech is drunk/poisoned — it
    survives its host's death."*
  - living Mastermind AND `cause.kind == EXECUTION` → Lleech lives, place `lleech:No ability`
    (`NO_ABILITY`, `Until.FOREVER`), surface the jinx.
  - else → `kill(lleech, KillCause(TRAVELLER-free: DEMON_ABILITY? no — STORYTELLER,
    sourceCharacterId = "lleech"))` + `WinCheck.Advisory(goodWins = true,
    ruleId = "lleech-host-dead")`.
  `deathNotes`/seat badge becomes conditional and **names the host**: on the Lleech —
  *"Cannot die: the host (<Name>) is alive."* / *"The life-link is off — this Lleech dies
  normally."*; on the host — *"This is the Lleech's host: killing them kills the Lleech
  (<Name>) and good wins."*
  `WinCheck.check` gains a caution on the demons-dead advisory whenever a `lleech:Host` token
  sits on a **living** player: *"A Lleech cannot be dead while its host lives — check that
  death."*

day: no day-time inputs. `DayBriefing` DAY_START while the link is live:
  *"<Lleech> cannot die today — their host <Name> is alive."*

ledger: `LedgerEntry(CHOICE, sourceId = "lleech", slot = "host")` on night 1;
  `RULING` when the ST answers the Scarlet-Woman inheritance question (below).

tests:
- Given `lleech:Host` on a living Noble, When `kill(lleech, EXECUTION)`, Then the Lleech is
  alive and `KillOutcome.Blocked` names the host.
- Given the same, When the Noble is executed, Then both are dead in the **same** state and
  `WinCheck` returns `goodWins = true` citing the Lleech.
- Given `courtier:Drunk` on the Lleech + a living host, When the Lleech is executed, Then it
  dies (wiki Example 2); When the host is killed, Then the Lleech lives.
- Given a living Mastermind and the host executed, Then the Lleech lives holding
  `lleech:No ability`; Given the host dies by `DEMON_ABILITY` instead, Then the Lleech dies.
- Given a Soldier host with `lleech:Host` but **no** `lleech:Poisoned`, Then
  `impairment(soldier)` is empty and the link still blocks the Lleech's death.
- Given a first-night state, Then the built step exposes a host picker and **no** kill action.

open: (a) what happens to the host's poison after the Lleech dies — unruled; (b) whether a
Scarlet Woman who becomes the Lleech inherits the host — **prompt the ST, do not
auto-transfer**, and record the answer as a `RULING`.

---

## lordoftyphon — Lord of Typhon · Experimental Demon · P0:3 P1:4

today: night 1 offers a Demon kill; the bag deals **real Minions to random seats**, so the
character's whole setup is unrepresentable; no validation of the evil line; the neighbours
are never named; conversion is a per-seat Grimoire → SeatSheet → CharacterPicker chore.

data:
- characters.json: `:2050` ability + `:2051 setup: true` ok. `:2052` firstNightReminder →
  "neighbours" spelling + state the **uniqueness** rule. `reminders: ["Dead"]` ok.
- night_and_jinxes.json: order ok (**first night index 1**, before `kazali`, and well before
  MINION_INFO at 14 / DEMON_INFO at 18; other night 45). **Add** `summoner`/`lordoftyphon`:
  *"If a Lord of Typhon is summoned, they must neighbor a Minion & their other neighbor
  becomes an evil Minion."*
- night_guide.json: `:1628-1644` prose is good; add the missing setup sentence (*"During
  setup, remove all Minion tokens and add Townsfolk or Outsider tokens"*), state the split
  need not be even, add `{"label":"Thumbs down","kind":"evil"}` show.

setup: **`BagShape("lordoftyphon")` = `minions 0..0`, `demons 1..1`, outsiders free,
  townsfolk = n − 1 − outsiders.** `randomBag` must draw **zero** Minions and `base+1` extra
  good characters, storing the reserved Minion ids (`GameState.pendingMinionIds` or a per-seat
  `pendingCharacterId`). SetupTask rows:
  - `setup.outsiderChoice` · PICK_NUMBER · *"How many Outsiders?"* · legal range `0..base+2`,
    Townsfolk compensate · required (`Setup.modifierFor` already yields
    `choiceTeams = {OUTSIDER}` — `SetupScreen.kt:374-376` only prints the bracket today).
  - `lordoftyphon.split` · PICK_NUMBER · *"How many Minions anticlockwise?"* · `1..base`,
    with `k + j = base + 1`, `k ≥ 1`, `j ≥ 1` · required.
  - `lordoftyphon.seating` · SEATING · validation in `validateSetupState`: evil seats
    **contiguous** modulo the circle; the Lord of Typhon **strictly interior** (*"cannot sit
    at the end of the line"*); Minion count == base+1; duplicate-Minion check. Offer
    `ReorderSeatsDialog` (`GameExtras.kt:110-143`) inline.

identity: the base+1 neighbours draw and briefly believe a **good** character, then are
**replaced** by Minion tokens on night 1 and hold that Minion's ability from night 1.
Persist each seat's original good character id (`Player.note` + ledger) — those tokens are
now **not in play** and become legal Demon bluffs / Ojo misses.

night.first:
  gate: fires always; **the Lord of Typhon itself does not wake**. Runs before MINION_INFO.
  action: computed targets — the `k` seats clockwise and `j` anticlockwise; the step **names
    them**. Per seat a two-tap row: `ChoosePlayerAndCharacter(pool = MINION,
    requireNotInPlay = true)` (already-assigned Minions disabled → enforces *"a unique Minion
    token"*), then *"Show <Name> their token"*.
  effects: `ShowCardTo(TARGET, "YOU ARE")` (`ShowCard.CharacterCard`) then
    `ShowCardTo(TARGET, "EVIL")` (`ShowCard.AlignmentCard(evil = true)` — the thumbs down) ·
    `BecomeCharacter(TARGET, <minionId>, evil = true, clearOldTokens = true,
    reRunFirstNight = true)` · note + ledger entry with the abandoned character.
  deferred: MINION_INFO / DEMON_INFO run normally straight afterwards and must be **rebuilt
    from post-conversion state** (and re-opened if already ticked). Newly created Minions
    with first-night abilities appear in the plan automatically; warn if any converted Minion
    sits **earlier** in the night order than index 1 (none today — cheap insurance).
    If a converted seat held a Puzzlemaster/Outsider whose ability triggers on becoming
    another character (wiki example 3), surface a one-line note.
  info: none. visibility: converted players see their Minion token + a thumbs-down; they do
    **not** learn the Demon here (MINION_INFO does that). The Demon is not woken.
  UI: *"The Lord of Typhon does not wake. Its neighbours become Minions: <names>. Show each a
    different Minion token and a thumbs-down, then replace their token."*

night.other: gate `Gates.aliveHolder` + `notExorcised`; action `ChoosePlayers(min=1, max=1,
  constraints=[ALIVE], allowNone=true)`; effect `Attack(TARGET, DEMON_ABILITY)`. Nothing else.

day: none. death: no special triggers.

ledger: each conversion as `CHOICE` + `RULING` recording the abandoned good character
(needed for the reveal and for the not-in-play bluff pool).

tests:
- Given a 10-player Lord of Typhon bag, Then `adjustedDistribution` gives minions == 3
  (base 2 + 1) and `validateBag` relaxes Outsiders.
- Given the wiki's 15-player example (0 Outsiders, 4 Minions), Then `validateBag` reports no
  issues.
- Given the Lord of Typhon at the **end** of the evil block, Then `validateSetupState` reports
  *"must have an evil player on both sides"*. **Fails today.**
- Given two evil players separated by a good player, Then a *"one unbroken line"* issue.
- Given a first-night state, Then the step exposes a neighbour-conversion action and **no**
  kill action; When both neighbours become `poisoner` and `godfather`, Then their steps appear
  in tonight's plan and MINION_INFO lists both.
- Given a random Lord of Typhon bag, Then it contains **zero** Minion characters. **Fails today.**

open: what happens if the Lord of Typhon's neighbours change seats mid-game, or a converted
Minion later turns good (Mezepheles, Pit-Hag) — treat the line as a **setup-time constraint
only** and log the break.

---

## ojo — Ojo · Experimental Demon · P0:2 P1:4

today: data, night order (other-nights only, index 47) and guide are all correct — but the
step renders `DemonKillPanel`, i.e. **a player picker for an ability that chooses a
character**. The ST must recall from memory whether the named character is in play and which
seat holds it; nothing is recorded.

data:
- characters.json: `:2060-2072` **ok** (ability, `setup: false`, empty firstNightReminder,
  `reminders: ["Dead"]`).
- night_and_jinxes.json: **ok** (absent from firstNight; otherNight index 47). No jinxes exist.
- night_guide.json: `:1647` — delete the invented *"(or only registers as such)"*; add the
  multiple-copies rule and the multi-kill allowance **verbatim** from the wiki.

setup: none. identity: plain.

night.first: absent (correct). Regression-guard it.

night.other:
  gate: `Gates.aliveHolder` · `Gates.notExorcised` → `Reduced`.
  action: `ChooseCharacter("ojo", "Which character did the Ojo choose?",
    pool = CharacterPool.SCRIPT, allowNone = false)`, then conditionally
    `ChoosePlayers(min = 1, max = N)` — express as `Sequence`.
    The character picker must list the script **exactly like the printed sheet** (alphabetical
    within team, script team order) and must **not** pre-partition by in-play — the ST is
    transcribing a pointing finger and a sorted picker leaks. Fabled/Travellers excluded by
    default with a toggle. Include a *"Show the character sheet"* button firing
    `ShowCard.SheetCard(scriptIds)` (today only in the tray, `NightScreen.kt:254-262`).
  effects (branch on a new engine helper `InPlay.holdersOf(state, characterId)`):
  - **in play, definition is explicit**: `c` is in play iff some seat has
    `characterId == c`. `shownCharacterId` (Drunk/Lunatic/Marionette) does **not** count;
    demon bluffs do not count; **dead players do**. Use this one helper everywhere (it also
    fixes Dreamer-style info and the bluff picker).
  - exactly 1 holder → `Attack(that holder, DEMON_ABILITY)` after the protection review.
    Already dead → `DEAD_HOLDER`: two buttons, *"The kill is wasted"* (default) and
    *"Storyteller chooses another player instead"*, flagged as an ST ruling (unruled).
  - >1 holder (Village Idiot / Legion / Riot — `GameActions.DUPLICABLE:413`) → *"Only one
    dies — which?"* with the holders as chips.
  - 0 holders → the **storyteller-choice** picker: heading *"<Character> is not in play — you
    choose who dies."*, candidates sorted **living good first** (wiki: *"almost always"*),
    then living evil, then dead, the latter two under a collapsed *"uncommon"* divider.
    If the script contains a multi-killing Demon (`shabaloth`, `alhadikhia`, `lilmonsta`,
    `legion`, `yaggababble`, or ability text matching `/kills? (up to )?\d|two players/i`),
    allow **multi-select**: *"You may kill more than one so the Ojo can look like the
    <Demon>."*
  - naming its own character resolves through the one-holder branch (red confirmation).
  deferred: none — deaths announced at the existing DAWN step.
  info: none shown to the Ojo. Shown to the **ST**: in-play verdict, holder(s), protections.
  impaired: keep the existing warning and add *"the attack fails — record the character they
    named, then choose 'No kill'"* (the named character is evidence even when the kill fails).

day: none.

death: `Attack` respects protection. If the in-play holder is protected, the kill **fails and
no substitute is chosen** — the choice was spent. Say so explicitly; an ST will be tempted
to re-roll.

ledger: **the point of the exercise.** `LedgerEntry(kind = CHOICE, sourceId = "ojo",
characterIds = [namedId], targetIds = victims, shown = "IN_PLAY|NOT_IN_PLAY|MULTIPLE|
DEAD_HOLDER", impaired)`. Render in the game log as *"Night 3 — Ojo named the Empath (not in
play); Storyteller killed Shugenja"*, and show the running history **inside the step**:
*"Named so far: Plague Doctor (hit), Poppy Grower (hit), Empath (miss)."*

tests:
- Given the Empath is not in play, When the Ojo names `empath`, Then `NOT_IN_PLAY`, empty
  holders, and the candidate list starts with living good players.
- Given a Drunk shown the Empath token and no real Empath, When `empath` is named, Then
  `NOT_IN_PLAY` (shown characters do not count).
- Given three Village Idiots, Then `MULTIPLE` with three holders and exactly one dies.
- Given an Ojo + Shabaloth script and a not-in-play choice, Then >1 victim is permitted;
  Given an Ojo-only script, Then exactly one.
- Given the in-play holder holds `monk:Safe`, Then nobody dies, the attempt is recorded, and
  **no** substitute victim is offered.
- Given a first-night state, Then `NightPlan` contains no `ojo` step.

open: unruled by the wiki — (a) Spy/Recluse misregistration for "in play" (do **not**
auto-apply; offer one grey ST-ruling line); (b) a named character whose only holder is
already dead; (c) naming a Traveller or the Ojo itself.

---

## riot — Riot · Experimental Demon · P0:5 P1:5

today: `characters.json` carries a **superseded ability and `setup: true`** while the repo's
own `raw_exp_evil_outsiders.json:484-499` has the correct text and `setup: false`; the guide
describes rules that no longer exist (nominees dying on days 1–2; *"after day 3 evil wins"*);
`DemonKillPanel` offers a night kill Riot does not have; and the day-3 chain **cannot be
recorded at all** because dead players cannot nominate.

data:
- characters.json: `:2078` ability → *"On day 3, Minions become Riot & nominees die but
  nominate an alive player immediately. This must happen."* · `:2079` → `"setup": false`.
  `reminders: ["Day 1","Day 2","Day 3"]` ok. `:2081` otherNightReminder should scope
  "wake the Minions" to **night 3 only** (derivable from `state.cycle`).
- night_and_jinxes.json: replace the five stale jinxes (`:208-232`; `riot`/`saint` is not on
  the current list at all, and farmer/ravenkeeper/sage/grandmother carry old wording) with the
  **twelve** current ones: Atheist, Banshee, Exorcist, Farmer, Grandmother, Innkeeper, King,
  Mayor, Monk, Ravenkeeper, Sage, Soldier. Four of them — **Exorcist, Innkeeper, Monk,
  Soldier — are instant good wins that fire mid-chain** and are not even listed in "Jinxes in
  play" (`GameExtras.kt:202-231`) today. Order ok (absent from firstNight; otherNight 53).
- night_guide.json: `:1658-1670` rewrite entirely (see UI text). Leave `first` absent.
- `Setup.kt:72`: `TEAM_WARPING_IDS = setOf("atheist", "legion")` — remove `riot`; today it
  relaxes **every** team count for any Riot script. Update `SetupTest.kt:105-111`. Keep `riot`
  in `GameActions.DUPLICABLE:413`.

setup: **none** — an ordinary distribution. (Conflicts with `setup-and-identity.md`'s
provisional `riot` BagShape; see Group note 3.)

identity: on night 3 **every Minion seat becomes `characterId = "riot"`**, dead ones included
(a dead Riot still nominates on day 3 and still counts for *"until all Riot are dead"*).
Riot is multi-holder by design — `StepKey(id, playerId)` is mandatory.

night.other:
  gate: nights 1–2 → `Skip("Riot does not kill at night")`. Night 3 → `Fire` when ≥1 Minion
    exists. The **Riot is never woken**; this is a storyteller conversion step.
  action: computed — every seat whose character is a Minion (alive **and** dead). Per seat a
    checklist row: `ShowCardTo(TARGET, "YOU ARE" + riot)` then the evil alignment card, then
    `BecomeCharacter(TARGET, "riot", evil = true, reRunFirstNight = false)` writing the
    previous character into the note + ledger. A **Convert all** button. The step is not done
    until every Minion is converted.
  effects/expiry: **replace the seat-bound DAY tokens with a derived banner** — the app knows
    `state.cycle`. Keep the tokens for physical parity but auto-place/auto-expire them:
    `TokenRule("riot","Day 1", countdownNext = "Day 2", countdownAt = DAWN)` → `Day 3` → stop,
    matching `records-and-memory.md`'s `COUNTS_UP_AT_DAWN`. Store them in
    `GameState.storytellerReminders` (grimoire centre), **not** on an arbitrary seat.
  visibility: each Minion sees YOU ARE + the Riot token. The Demon is not woken.
  UI (night 2): *"Riot does not kill at night. Tomorrow is day 2 of 3."*
  UI (night 3): *"Tomorrow is the riot. Wake each Minion in turn, show YOU ARE and the Riot
    token, then replace their character token. Minions: <names>."*
  jinxed night choice (**only if Group note 9 is confirmed**): with banshee/farmer/
    ravenkeeper/sage jinxed, add a nights-2+ step: `ChoosePlayers(min=1, max=1,
    constraints=[ALIVE, GOOD, NOT_CHOSEN_BEFORE])` — *different from every previously chosen
    player*, so `Memory.choicesBy(state,"riot")`, not just `lastChoice`. Then per-character:
    Banshee **dies and gains their ability**; Farmer/Ravenkeeper/Sage **use their ability and
    do not die**.

day: **days 1 and 2 are completely normal** (normal nominations, votes, executions) — the
  current guide text says otherwise and will make an ST kill players on day 1. Day 3 is a
  dedicated **riot mode**:
- `riotActive = state.cycle >= 3 && any seat has characterId == "riot"`.
- `NominationTrigger(kind = AUTO_DEATH, sourceId = "riot")` per the day-engine table: the
  nominee **dies immediately, no vote**, and must nominate again. Persist
  `RiotChain(active, currentNominatorId, links)` on `GameState` so undo and the log work.
- `DayRules.canNominate` must allow **dead** nominators here, and `hasNominatedToday` /
  `hasBeenNominatedToday` (`GameActions.kt:285-289`) must not constrain the chain.
- One screen per link: *"<Name> must nominate — 3… 2… 1…"* wired to the existing
  `DiscussionTimer` (`GameShell.kt:315-320`), with a **"time ran out — Storyteller
  nominates"** fallback (the wiki's own stall rule). A single row of chips: **alive players
  only**, excluding the nominator. No vote UI, no threshold, no tally.
- One undoable action per tap: `recordNomination(via = ExecutionVia.RIOT)` +
  `kill(nominee, KillCause(DeathKind.EXECUTION? no — a new DeathCause.RIOT))` +
  `currentNominatorId = nominee`.
- **Before the kill lands**, run `killOutcome` **and the four instant-win jinxes**:
  Monk-protected / Innkeeper-protected / Exorcist-chosen / the Soldier ⇒ stop and show
  *"Riot nominated a <Monk-protected> player — GOOD WINS"* with a Declare-victory button.
  Nominee holding `grandmother:Grandchild` ⇒ *"The Grandchild died — EVIL WINS"*. These must
  fire at **nomination time** via `checkNomination`, not at death time.
- Termination after every link: no living `riot` seat → *"All Riot are dead — good wins"*;
  exactly 2 alive → *"Two players remain — evil wins"*. **Check the Riot condition first** —
  if the last Riot died on the link that brought the count to 2, good wins.
- **Mayor jinx**: a *"Mayor stops the riot"* button, enabled only with a living Mayor — good
  wins if exactly one Riot is alive, evil wins otherwise.
- **Atheist jinx**: allow "the Storyteller" as a nominee; that link alone takes a normal vote
  and ends the game if it passes.
- **Suppress** while `riotActive`: the block banner (`DayScreen.kt:93-115`), `aboutToDie`
  (`GameActions.kt:296-306`) and the dusk guard (`GameShell.kt:141-146`) — a stale
  ABOUT_TO_DIE from day 2 can otherwise strand the ST.
- Dawn of day 2 briefing: *"Tomorrow the Minions become Riot — wake them tonight."*

death: `WinCheck` gains `riot-day3` (already listed in `day-engine.md` duskCheck) plus the
Grandmother-grandchild-by-execution evil win and the Mayor branch.

ledger: the whole chain in order — *"Day 3 riot — Alex » Lewis (died) » Ben (died) »
Marianna (died) » Lachlan (died). All Riot dead: good wins."*

tests:
- Given `characters.json`, Then `riot`'s `ability` and `setup` equal
  `raw_exp_evil_outsiders.json`. **Fails today** (a general parity test catches the class).
- Given a 10-player bag with a Riot and 2 Minions, Then `validateBag` passes **and actually
  validates** (3 Minions must now be rejected). **Fails today.**
- Given day 3, 6 alive, 3 Riot, When Lewis is riot-nominated, Then Lewis is dead with
  `DeathCause.RIOT`, the next nominator is Lewis, and no tally was required.
- Given the current nominator is dead, Then the engine accepts them as nominator.
- Given a riot nomination of a `monk:Safe` player, Then an instant-good-win advisory is raised
  **before** the kill is applied.
- Given night 3 with three Minions, When conversion runs, Then all three seats are `riot`,
  each retains its previous character in its note, and their Minion steps disappear from the
  plan.
- Given day 1, When a player is nominated, Then the normal vote path applies and the nominee
  does **not** die.

open: the four "each night\*" jinxes (Group note 9) — verify before wiring any Riot night step.

---

## yaggababble — Yaggababble · Experimental Demon · P0:3 P1:4

today: night 1 offers a demon kill (there is no day yet, so the count is zero); the utterance
count is tracked **nowhere**; only one kill can be resolved per night, so victims 2 and 3 get
filed as `DeathCause.STORYTELLER` and corrupt Undertaker/Cannibal/Vortox reasoning; and the
secret phrase is `rememberSaveable` local state (`NightScreen.kt:374`) that is lost when the
dialog closes.

data:
- characters.json: `:2094` ability ok, `setup: false` ok, both night reminders ok. Leave the
  three `"Dead"` tokens for physical parity but **stop treating them as the count** — the tray
  silently recycles the fourth (`NightScreen.kt:325-339`) and the tally is unbounded.
- night_and_jinxes.json: **ok** — first night 12 (before MINION_INFO at 14, so Minions can be
  let in on the phrase), other night 51; `exorcist`/`yaggababble` jinx present at `:288-292`
  with the correct meaning.
- night_guide.json: `:1671-1687` — add the drunk/poison timing rule **verbatim**, add *"you
  may kill fewer than the number of times it was said"*, add the wiki's day-time-kill example,
  and state explicitly in `first` that the Yaggababble **does not kill on the first night**.

setup: `yaggababble.phrase` · free text · *"Write the secret phrase now. Choose one that is
fairly plain, so they can work it into conversation."* · suggestions ("that sounds fishy",
"to be honest", "at the end of the day", "I'm just saying") · **required**: `validateSetupState`
reports an issue when blank. Stored as **`GameState.secrets["yaggababble"]`** (the
`records-and-memory.md` map), not as dialog-local state — so it is re-showable on demand and
the ST never retypes it. `GuideShowDialog` must read/write that key.

identity: plain. The counter lives on `GameState`, **not on the seat**, so a mid-game
Yaggababble (Summoner / Scarlet Woman / Pit-Hag) inherits the day's count for free (botc.me).

night.first:
  gate: `Gates.aliveHolder`.
  action: none. **No kill panel.**
  effects: none.
  show: `ShowCardTo(SOURCE, secrets["yaggababble"])` full-screen, silently, editable, writing
    edits back to the secret.
  visibility: the Yaggababble only; the Minions are not told by rule.
  UI: *"The Yaggababble does not kill tonight. Show them their phrase — silently. Count every
    time they say it publicly tomorrow."*

night.other:
  gate: **the Yaggababble is not woken** — the step exists only so the deaths resolve in night
    order. `Skip` when `charges == 0`; `Reduced`/`Skip("Exorcist chose the Yaggababble")` on
    `exorcist:Chosen` (the jinx — make it a real gate, not the prose at `NightOrder.kt:150-154`);
    `Skip` when `Status.isImpaired(yagg)` **at this moment** with the reason
    *"Drunk/poisoned right now — no one dies, even though the phrase was said <n> times."*
    The sobriety check is at **resolution** time, not speaking time — the opposite of what
    most STs assume, and it must be stated on the step.
  action: `ChoosePlayers(min = 0, max = charges, constraints = [ALIVE] (+ include-dead
    toggle), allowNone = true, noneLabel = "No-one dies tonight")` where
    `charges = phraseCount − phraseSpent`. Constraint text: *"You may kill up to <k>. You may
    kill fewer."*
  effects: `perTarget = [Attack(TARGET, DEMON_ABILITY)]` — **per-victim** protection review, so
    Monk/Soldier/Innkeeper can block one and not another; all in one undoable action;
    `phraseSpent += killed`.
  deferred: deaths announced at DAWN.
  UI: *"The Yaggababble does not wake. They said “<phrase>” <n> times today — up to <k>
    players may die, and you choose who (or fewer, or none)."*

day: **this is the missing half of the character.**
- A persistent phrase counter on the Day tab header **and** reachable from the Grimoire tab
  (the ST is looking at seats when they hear it): the phrase in small type plus a large
  `[ + ] <n> said today` and a `[ − ]` to undo a miscount. One tap, no dialog.
- **"Spend a charge now"** (wiki Example 2, the signature play): pick a player, pick an
  optional disguise from a dropdown built from the characters in play (*"looks like a Witch
  curse"*, *"looks like the Golem's nomination"*, *"no disguise"*), kill, `phraseSpent += 1`,
  log it. Refuse **with the reason** when the Yaggababble is impaired at that moment.
- Counters `phraseCount` / `phraseSpent` reset at **dawn** (`advancePhase`'s `Phase.NIGHT ->`
  branch, `GameActions.kt:260`), so a day's utterances stay spendable through that day and its
  following night, then vanish. The phrase itself never resets.
- The physical **DEAD tally belongs in `GameState.storytellerReminders`** (grimoire centre),
  not on a seat.

death: `KillCause(DEMON_ABILITY, "yaggababble")` with `demonKillUncertain = true` per
`status-model.md` (Sage/Grandmother/Choirboy toggle). **Grandmother**: a day-time charge that
kills the grandchild kills the Grandmother **immediately** (botc.me) — add it to the
Grandmother branch of the on-death table (`StatusEffects.kt:122-127` mentions only the
Demon's night kill).

ledger: `DayEntryKind.YAGGABABBLE` / `LedgerEntry(kind = STATEMENT, sourceId =
"yaggababble", count = n)` per day, plus a `CHOICE`/`SPENT` entry per charge spent with the
victim and the disguise. Same shape as the Gossip gap the user complained about — reuse the
day recorder, do not grow a one-off.

tests:
- Given a first-night state, Then the built step exposes **no** kill action.
- Given `phraseCount = 3, phraseSpent = 0`, Then up to 3 victims may be selected; selecting 2
  kills exactly 2 and leaves `phraseSpent = 2`.
- Given `phraseCount = 3` and the Yaggababble marked `poisoner:Poisoned`, Then **zero** kills
  are permitted and the reason names the poison (sober-when-speaking does not help).
- Given the Yaggababble was poisoned when speaking but is sober now, Then the count **still
  applies**.
- Given `exorcist:Chosen`, Then the step is skipped with the jinx reason and `phraseSpent` is
  unchanged.
- Given `advancePhase` NIGHT→DAY, Then `phraseCount` and `phraseSpent` are both 0.
- Given `validateSetupState` with a blank `secrets["yaggababble"]`, Then an issue is reported.

open: (a) whether a Yaggababble who dies **during the day** still kills that night for phrases
said while alive — unruled; (b) whether a blocked victim frees the charge for someone else —
spec says the charge is spent on the attempt; pin whichever the implementer picks.

---

## barista — Barista · Sects & Violets Traveller · P0:5 P1:4

today: **zero Barista code exists** — `grep` returns only the three data files. Both tokens
are placed with `addReminder` (never exclusive), never expire, and have **no effect on any
computation**: a Barista-sobered player is still reported poisoned, still gets the Vortox
false-info flow forced on them, and still gets Spy/Recluse misregistration warnings.

data:
- characters.json: `:1130` ability ok. `:1132-1135` reminders →
  `["Sober & Healthy", "Acts Twice", "?", "?"]` (rename `"Ability twice"`; the two `?` tokens
  are the official substitutes for a doubled character's own one-of-a-kind reminders). Keep
  `"Ability twice"` as a migration alias.
- night_and_jinxes.json: **ok** — first night `:299` (after `apprentice`), other nights `:374`
  (first after DUSK). No jinxes exist.
- night_guide.json: `:757-803` good; add *"Do not wake the Barista; they learn nothing."* and
  *"SOBER AND HEALTHY beats poison, drunkenness, the Vortox, and Spy/Recluse misregistration
  for this player until dusk."* Rename "Effect 2" → *"Until dusk your ability works twice —
  you ACT TWICE"*.

setup: `traveller.alignment.<seatId>` (T1). identity: plain.

night.first / night.other (**identical — "each night", including night 1**):
  gate: `Gates.aliveHolder`. No once-per-game gate. **The Barista is never woken** — this is a
    storyteller-only step; the *target* is woken. Say so on the step.
  action: `ChoosePlayers(min=1, max=1, constraints=[ANY_LIVING_STATE])` — a dead player with a
    Bone-Collector-restored ability is a legal ACTS TWICE target — **plus a required mode**
    `SOBER_HEALTHY | ACTS_TWICE`. Sort ALIVE_FIRST, annotated:
    (a) holds a drunk/poison mark, sits next to a No Dashii, or a Vortox is in play →
    *suggest SOBER & HEALTHY*; (b) has a choice-making night ability → *suggest ACTS TWICE*;
    (c) grey hint *"gains nothing from acting twice"* for passive info roles — the wiki names
    flowergirl, towncrier, oracle; extend with chef, empath, clockmaker, steward, noble,
    shugenja, knight, bountyhunter, mathematician, balloonist, undertaker, cultleader, king,
    sage.
  effects: `PlaceToken("barista","Sober & Healthy"|"Acts Twice", TARGET, exclusive = true)`
    **and remove the other Barista token from every seat in the same action** — both tokens
    are one-of-a-kind, only one may exist at a time (*"remove previous reminders then put…"*).
    Effects: `SOBER_HEALTHY` or a granted second activation, both `until = DUSK`,
    `endsWithSource = true`.
  deferred:
    - SOBER & HEALTHY → DAY_START briefing *"Ana is sober & healthy until dusk — any info she
      gets today is true, and she cannot become drunk or poisoned."*
    - ACTS TWICE → tonight's plan contains the target's step **twice**, and any **day** ability
      of that target may be used twice today (Butcher two extra nominations, Gangster two
      kills, Klutz two guesses, Gunslinger two shots). DAY_START: *"Ana ACTS TWICE until
      dusk — her day ability may be used twice."*
  expiry: **`Expiry.DUSK` for both** (add to `EXPIRES_AT_DUSK`, `GameActions.kt:231-242`;
    keep the `"Ability twice"` pair too so existing saves get swept). Plus
    `endsWhenSourceLosesAbility = true` — drop both immediately if the Barista dies.
  info: none for the Barista. show: the three existing cards — "THIS CHARACTER SELECTED YOU",
    the Barista token, then one finger / two fingers.
  visibility: nobody but the target learns anything; the **Barista does not learn who or what
    was chosen**.

**The impairment override — the P0 core.** `SOBER_HEALTHY` must be checked **first**, before
the reminder scan and before `derivedPoison`, exactly as `status-model.md` §2 step 4 has it
(*"Barista wins outright"*). Consumers to rewire:
- `StatusEffects.isImpaired` (`:36-46`) → false unconditionally.
- `InfoCalc.impairments` (`:133-153`) → a single **positive** note: *"BARISTA: Ana is sober &
  healthy until dusk — give TRUE info, ignore every drunk/poison mark and any
  misregistration."*
- `InfoCalc.commonCaveats` (`:158-165`) → skip the Vortox caveat; emit *"BARISTA overrides the
  VORTOX — this player gets TRUE info."* This is the **only routine exception** to "all
  Townsfolk info is false".
- `InfoCalc.misregistrations` (`:121-130`) → suppress Spy/Recluse warnings for the receiving
  holder.
- `NightScreen.kt:904-916` → the "False info to show instead:" block must not render.
- New drunk/poison marks on a sobered player must be **refused** (or accepted with a loud
  *"no effect until dusk"*): Poisoner, Courtier, Sailor, Innkeeper, Widow, No Dashii
  adjacency, Goon, Pit-Hag, Sweetheart, Vigormortis, Minstrel, Snake Charmer.

**The ACTS TWICE double step (generic engine change).** `StepKey(id, playerId, variant =
"again")` — the plan emits a second step for any character whose holder carries
`("barista","Acts Twice")`, immediately after the first, titled *"<Name> — 2nd time
(Barista)"*. `nightStepsDone` keys on `StepKey.token`; every `rememberSaveable` keyed on
`step.id` must key on the token so the two occurrences hold independent selections. While
ACTS TWICE is on a player, `placeExclusiveReminder` for **that player's own character** must
**place instead of move**, so a doubled Monk/Witch/Fortune Teller/Poisoner can mark two seats
— label the second copy `"<label> (2nd)"` and expire it identically. (`TokenRule.maxCopies`
is the right home for this.)

day: no inputs; the two DAY_START briefing lines above. death: none.

ledger: `CHOICE` per night with the mode in `shown` — the ST is otherwise never told which of
the two effects they picked, and nothing at dusk says the effect ended.

tests:
- Given `("poisoner","Poisoned")` **and** `("barista","Sober & Healthy")`, Then
  `isImpaired == false`. **Fails today.**
- Given an alive Vortox and a sobered Empath, Then no "VORTOX … must be FALSE" caveat and a
  "BARISTA … TRUE info" caveat. **Fails today.**
- Given a Recluse in play and a sobered Washerwoman, Then no "may register as evil" caveat.
- Given the token at night 2, When NIGHT→DAY, Then it is still present; When DAY→NIGHT, Then
  it is gone. **Fails today (never expires).**
- Given the token on seat A, When the next night places the other token on seat B, Then A
  holds **neither** and B holds exactly one.
- Given a Monk holding `("barista","Acts Twice")`, Then the plan contains two `monk` steps
  with distinct `StepKey`s, and two "Safe" tokens can coexist. **Fails today both ways.**
- Given the Barista dies, Then both tokens are removed from all seats; Given a dead Barista,
  Then no `barista` step is emitted.

open: whether SOBER & HEALTHY sobers a player whose **character is the Drunk** — the ability
text *"a player becomes sober"* reads as yes, but the wiki never says. Surface as an explicit
ST decision line, do not decide silently.

---

## bonecollector — Bone Collector · Sects & Violets Traveller · P0:4 P1:4

today: **zero code.** The step is offered every night regardless of alive/spent/any-dead; the
restored ability is **never actually run** (a first-night-only character produces no row at
all, or a row with the wrong text); `"Has ability"` never expires; and a Bone Collector who
dies does not revoke the grant.

data:
- characters.json: `:1145` ability is missing the `*` → *"Once per game, at night\*, choose a
  dead player: they regain their ability until dusk."* `:1147-1150` reminders →
  `["No Ability", "Has Ability"]` (keep old casing as an alias). `:1146` otherNightReminder:
  append *"The Bone Collector loses their ability — mark them NO ABILITY. The next dusk,
  remove HAS ABILITY."*
- night_and_jinxes.json: `:378` — `otherNight` only, after `plaguedoctor`, before `harlot`.
  Correct that it is other-nights-only; exact relative position **not independently verified**.
  No jinxes.
- night_guide.json: `:805-810` — add *"The chosen player is NOT told the Bone Collector chose
  them."*, *"If the ability is first-night-only (Clockmaker, Washerwoman, Chef…), run its
  FIRST-night version tonight."*, *"If they had already spent a once-per-game ability, they
  may use it again."*, *"If the Bone Collector dies, the restored ability ends at once."*

setup: `traveller.alignment.<seatId>` (T1). identity: plain.

night.other (**other nights only**):
  gate: `Gates.aliveHolder` AND `Gates.notSpent("bonecollector", "No Ability")` AND at least
    one dead player exists. Otherwise `Skip` with the reason
    (*"Spent on night 2. [Undo spend]"*). All three checks are missing today.
  action: `ChoosePlayers(min = 0, max = 1, constraints = [DEAD], allowNone = true,
    noneLabel = "They shook their head no", sort = DEAD_FIRST)`. Sort dead Townsfolk first
    (the wiki's own advice), then Outsiders, Travellers, Minions/Demon; **annotate each with
    the character name and whether its ability is first-night, other-night, day-time or
    passive**, so the ST can see what they are buying.
  effects (one confirmed, undoable action):
    `PlaceToken("bonecollector","Has Ability", TARGET, exclusive)` →
    `Effect(HAS_ABILITY, until = DUSK, endsWithSource = true)` ·
    `PlaceToken("bonecollector","No Ability", SOURCE, exclusive)` → `MarkSpent` ·
    `Defer(kind = "first-night", on = TARGET, dueNight = state.cycle)` →
    `PendingEffect` → the planner emits `StepKey(restoredCharacterId, targetPlayerId,
    variant = "first")` with `style = WakeStyle.FIRST_NIGHT`.
    No kill, no resurrection, no character change. **The player stays dead.**
  deferred:
    - *Tonight*: the restored step runs at the restored character's canonical position if it
      is still ahead of the cursor, otherwise via the **insert-after-cursor** rule badged
      *"out of order — the Bone Collector woke after their normal slot"*.
    - *Tomorrow*: DAY_START briefing — *"<Name> has their <Character> ability back until dusk
      (Bone Collector). They are still dead: no nomination unless the ability grants it, one
      ghost vote as usual."* Day tools must honour it: **Butcher** extra nomination (the
      wiki's own example), Gangster kill, Slayer shot, Artist question, Fisherman advice,
      Gossip statement, Klutz guess, **Virgin** trigger, Golem nomination.
    - *On Bone Collector death*: remove `("bonecollector","Has Ability")` from all seats
      **immediately** (`endsWhenSourceLosesAbility = true` + `reconcileTokens`), with
      *"<Name> loses the restored <Character> ability now."*
  expiry: `Has Ability` → `Expiry.DUSK`. `No Ability` → `Expiry.NEVER` (it is the spend).
  info: the Bone Collector learns nothing and is shown nothing; the chosen player is **not
    told** who chose them. show: splice in the **restored character's own** cards
    (`NightGuide.forStep(restoredId, isFirstNight = runFirstNightVariant)`) — today
    `night_guide.json` has `"shows": []`.
  visibility: nothing to Demon/Minions/Lunatic.

**The restored-step mechanism is the same one the Professor needs** — the user's own request.
`PendingEffect(kind = "first-night")` (night-engine §1.5) rather than a magic reminder;
`StepDetailPanel` must resolve `StepKey.id` (not a suffixed string) before calling
`NightGuide.forStep` / `InfoCalc.supports` / the action table, pass
`isFirstNight = (variant == "first")` rather than `state.cycle == 1` (`NightScreen.kt:787`),
and pass the **restored player's** id as `holderId` so "you start knowing" info is computed
for the right seat.

**Restored once-per-game abilities must ignore their own spent marker while
`("bonecollector","Has Ability")` is on the seat** — `Status.hasAbility` is the single place
to encode it, and it must reach `nominationWarnings`'s Virgin branch
(`StatusEffects.kt:152-157`), the Fool note (`:75-77`), the Professor branch
(`NightScreen.kt:499-520`) and the "Mark spent" gate (`NightScreen.kt:263-281`).

day: see the DAY_START line above; `DayRules.canNominate` must admit a restored dead Butcher.

death: none of its own. `keepsAbilityWhenDead` must include "anything holding a live
`HAS_ABILITY` effect" (already in `status-model.md` §2) and `Gates.actsWhileDead` must
include "any player carrying `bonecollector:Has ability` tonight" (already in
`night-engine.md` §2).

ledger: `SPENT` for the Bone Collector; `CHOICE` naming the restored seat + character;
crucially, the **Juggler case** requires a *dead* Juggler's five day-time guesses to have been
recorded on a previous day and to be retrievable tonight — `LedgerEntry(kind = STATEMENT,
sourceId = "juggler")` with `resolvedCycle` set when scored.

tests:
- Given `("bonecollector","No Ability")` on the holder, Then no `bonecollector` step.
  **Fails today.** Same for a dead Bone Collector, and for no dead players.
- Given a dead Clockmaker chosen on night 3, Then the plan contains a restored Clockmaker step
  whose detail is the Clockmaker's **firstNightReminder** and `InfoCalc` returns the
  Clockmaker's number for that seat. **Fails today (no step at all).**
- Given `Has Ability` at night 3: NIGHT→DAY keeps it, DAY→NIGHT removes it. **Fails today.**
- Given `Has Ability` on seat A, When the Bone Collector is killed, Then A loses it.
  **Fails today.**
- Given a dead Virgin holding `("virgin","No ability")` **and** `Has Ability`, Then the Virgin
  nomination trigger **does** fire. **Fails today (suppressed).**
- Given a dead Butcher holding `Has Ability` and an execution today, Then
  `DayRules.canNominate` includes them. **Fails today.**

open: exact relative night-order position of `bonecollector` vs `harlot` — unverified against
an official sheet, not claimed as drift.

---

## butcher — Butcher · Sects & Violets Traveller · P0:4 P1:4

today: **inert** — no code at all, and three separate parts of the day engine actively block
the correct behaviour. The trigger is *an execution happening*, not a death, and the engine
records **only deaths**, so the trigger is unrepresentable.

data:
- characters.json: `:1155-1166` **ok** — correct text, empty night reminders, no tokens
  (matches the printed character).
- night_and_jinxes.json: correctly absent from both orders. No jinxes.
- night_guide.json: **no entry**, and no day-guide mechanism exists. Needs a
  **`day_guide.json`** (shared with deviant, gangster, gnome, scapegoat, gunslinger, judge,
  bishop, matron, voudon):
  > *"Immediately after any execution — even one where the executed player did not die —
  > remind the Butcher that they may nominate again. Already-nominated players are legal
  > targets, and the Butcher may nominate even if they already nominated today. The tally must
  > reach half the (now smaller) living count but does not have to beat the earlier tally.
  > Exiles are not executions. Only one extra nomination per day."*

setup: `traveller.alignment.<seatId>` (T1). identity: plain. night: **none.**

day:
- **Trigger** = an `ExecutionRecord` exists for `state.cycle` with
  `outcome in {DIED, SURVIVED}` — i.e. `DayRules.executionSpent(state)`. *"If a player is
  executed, even if they do not die"*: Devil's Advocate, Fool, Sailor, Tea Lady, Tinker,
  Pacifist, Mayor bounce, Zombuul first death all still count. **An exile is never an
  execution** and never enables the Butcher (the wiki gives it a dedicated example).
- `DayRules.secondExecutionAllowed(state, lookup)` returns true for the Butcher case;
  `DayRules.canNominate` must return `Right(allowed = true)` for the Butcher **even when they
  already nominated today** (today `DayScreen.kt:135-138` + `hasNominatedToday`
  (`GameActions.kt:285-286`) block it), and `DayRules.canBeNominated` must admit a nominee
  **already nominated today** (today `DayScreen.kt:146` + `hasBeenNominatedToday`
  (`:288-289`) block it — this is the wiki's third example verbatim).
- **The tally rule**: the Butcher's nomination only has to reach the threshold; it does
  **not** have to beat the day's highest tally. `DayScreen.kt:204`'s
  `Voting.outcome(votes, threshold, highest)` (`GameState.kt:147-152`) is wrong here.
  Add `Nomination.butcherExtra: Boolean = false` so `aboutToDie` replay
  (`GameActions.kt:296-306`) stays faithful, and branch:
  `if (butcherExtra) { if (votes >= threshold) ABOUT_TO_DIE else SAFE }`.
  The threshold is recomputed on the **reduced** living count — `DayScreen.kt:71-72` already
  does this live; the header's *"N votes is the tally to beat"* must change for this nomination.
- **Spent by use, not by the execution**: mark used on the Butcher's **post-execution**
  nomination. A failed pre-execution nomination does not spend it (wiki example 3: the Butcher
  nominates the Town Crier, fails, an execution then happens, and the Butcher nominates the
  Town Crier again successfully). Day-scoped flag, cleared at DAY→NIGHT.
  A successful second execution does **not** unlock a third nomination.
- Briefings: `DayBriefing.Slot.EXECUTION` — *"Butcher: <Name> may nominate again — even
  someone already nominated today, and the tally only needs <threshold> votes (it does not
  have to beat <highest>)."* with a **Start Butcher nomination** action that pre-selects the
  Butcher and sets `butcherExtra`. `Slot.DUSK` when only exiles happened: *"No execution
  today — exiles are not executions, so the Butcher may not nominate again."*
  After use: *"Butcher's extra nomination used. No third nomination today."*
  The wiki explicitly permits prompting here (*"Remind them if needed"*) — unlike the Gnome.
- Interactions: dead Butcher ⇒ no ability **unless** `("bonecollector","Has Ability")`.
  Impaired Butcher ⇒ surface `impairment()` in the prompt and let the ST decide. Barista ACTS
  TWICE ⇒ **two** extra nominations today. Mastermind day
  (`GameState.mastermindDayActive`) ⇒ the extra execution can end the game; surface the
  caution. Evil Twin / Saint / Fearmonger nominee warnings still flow through
  `checkNomination`.

death: none.

ledger: the `ExecutionRecord` itself is the load-bearing record. Note that the **"Death
prevented" branch of the seat-sheet dialog (`SeatSheet.kt:340-343`) writes nothing today** —
it must call `execute(outcome = SURVIVED, preventedBy = …)`. The same record unblocks Vortox,
Mayor, Pacifist, Devil's Advocate, Minstrel, Undertaker and Cannibal.

tests:
- Given the Butcher nominated earlier today **and** an execution today, Then
  `DayRules.canNominate(butcher).allowed`. **Fails today.**
- Given X was nominated earlier today and an execution occurred, Then X is in
  `canBeNominated`. **Fails today.**
- Given 8 alive (threshold 4) and today's highest passing tally is 6, When the Butcher's
  nomination tallies 4, Then `ABOUT_TO_DIE`. **Fails today (`SAFE`).**
- Given only exiles today, Then `canNominate` is false and the reason mentions *"exiles are
  not executions"*.
- Given a Devil's-Advocate-protected player is executed and does not die, Then
  `DayRules.executionSpent(state)` is true and the Butcher may nominate. **Fails today
  (nothing recorded).**
- Given the extra nomination is used and a second execution occurs, Then no third nomination.
- Given a dead Butcher: no ability; Given the same Butcher holds
  `("bonecollector","Has Ability")`: ability. Given DAY→NIGHT→DAY, Then the flag is cleared.

open: none.

---

## deviant — Deviant · Sects & Violets Traveller · P0:3 P1:3

today: **inert.** The exile button (`DayScreen.kt:350-357`) calls `kill(id, EXILE)` directly —
no Deviant check, no "exile passes but they survive" option, no `deathNotes` on the path at
all. `StatusEffects.deathNotes` has no Deviant entry even though every other
survive-this-death ability is listed there.

data:
- characters.json: `:1167-1178` **ok** (text correct, no tokens — matches the printed
  character).
- night_and_jinxes.json / night_guide.json: correctly absent / no entry. Needs a
  `day_guide.json` row:
  > *"If the Deviant would be exiled, you may declare they remain alive. Be forgiving — even
  > slightly funny counts. You may agree a different criterion with the player ('creates a
  > positive mood', 'is helpful to others'). Judge fresh every day."*

setup: `traveller.alignment.<seatId>` (T1) · `deviant.criterion` · free text ·
*"What counts for this Deviant?"* · default `"funny"`, offering *"created a positive mood"* /
*"was helpful to others"* · per-game, persisted (the wiki explicitly invites the ST and the
player to negotiate this).

identity: plain. night: **none.**

day:
- **Day-scoped judgement**, re-made every day: `deviantFunny:<playerId>` (keyed per seat — two
  Deviants are possible), cleared on **DAY→NIGHT** alongside `EXPIRES_AT_DUSK`
  (`GameActions.kt:261`). The judgement is made hours before the exile is called, so it must
  survive tab switches and undo — i.e. it belongs in `GameState`, not in
  `storytellerNotes`.
- `DayBriefing.Slot.DAY_START`, severity ACTION: *"Deviant — <Name>. Were they <criterion>
  today? You judge; be forgiving, even slightly <criterion> counts. If yes, they cannot die by
  exile today."* with `Yes` / `Not today` chips, plus a persistent chip in the Day tab header
  so it can be changed any time before the exile.
- `DayBriefing.Slot.EXECUTION` (exile branch): *"<Name> is the Deviant and you marked them
  <criterion> today. The exile still passes — but you may declare they remain alive."*
  Buttons `They survive` · `They die anyway`. If the flag is **not** set: *"You have not judged
  <Name> <criterion> today. Exiling them will kill them."* with an inline
  `Mark them <criterion>` shortcut.
- The exile **decision** still happens normally — the votes are counted and the exile passes;
  the Deviant simply does not **die**. Record it explicitly (`ExecutionRecord`-style
  `outcome = SURVIVED, preventedBy = "deviant"`, or `Nomination.survived = true`) so the log
  distinguishes *"exile survived (Deviant)"* from *"the ST forgot to apply it"* — today both
  render identically (`GameExtras.kt:64-77`).

death: `killOutcome` step 10 (`status-model.md` §3) — **`kind == EXILE` and the target is a
Deviant marked funny → `KillOutcome.Choice`**, never automatic: this is a *"you may declare"*
ability, so the app offers and never forces. Protects against **EXILE only** — not execution,
not the Demon, not the Gangster, the Harlot or the Gnome. The seat sheet must say so
(`SeatSheet.kt:195-197` shows only the raw ability string, and *"cannot die"* reads too
broadly to a tired ST). Not a once-per-game: the flag is **not consumed** by a survived exile,
so a second exile the same day can also be survived.
**Cross-cutting prerequisite:** `deathNotes` needs a `cause: DeathCause?` parameter (the same
change `gangster` and `gnome` need) so the note is EXILE-scoped, and the exile path must route
through the same protection-confirmation dialog the seat sheet already has
(`SeatSheet.kt:325-345`) — today `DayScreen.kt:350-357` and the block-banner Execute
(`:111-114`) bypass it entirely.

ledger: `RULING` per day recording the criterion verdict; `ANNOUNCE`/`STATEMENT` not needed.

tests:
- Given `deviantFunny = true` on day 3 and a passing exile resolved with "They survive", Then
  the Deviant is alive, `deaths` is unchanged, and the record shows the exile passed with
  `survived = true`. **No such path today.**
- Given `deviantFunny = false`, Then survive is not the default and the Deviant dies with
  `EXILE`.
- Given the flag set on day 3, When DAY→NIGHT, Then it is cleared and is still clear on day 4.
- Given a Deviant, Then `deathNotes(..., cause = EXILE)` contains a Deviant entry and
  `cause = EXECUTION` does not. **Fails today for both.**
- Given a Deviant marked funny, When **executed**, or killed by a Gangster/Harlot/Gnome, Then
  they die.
- Given a survived exile, Then a second exile the same day still offers the survive option.
- Given two Deviants, Then marking one sets only that seat's flag.

open: whether a **drunk or poisoned** Deviant may be exiled normally. The Glossary's
*"abilities cannot affect an exile decision"* plus the fact that the **Storyteller** declares
the survival suggests yes, but it is unruled — surface `impairment()` in the prompt and let
the ST decide.

---

## harlot — Harlot · Sects & Violets Traveller · P0:3 P1:5

today: no code. The two deaths are entirely manual and can be applied **asymmetrically**
(the rule is both-or-neither); the revealed character is not computed even though
`InfoCalc.revealCharacter` already exists; and nothing distinguishes the **true** character
from the shown one, so the ST is one tap from flashing a Drunk's believed token.

data:
- characters.json: `:1184` ability ok, `otherNightReminder` matches the wiki. `:1188-1190`
  reminders → `["Dead", "Dead"]` (official is two DEAD tokens, one per possible death).
- night_and_jinxes.json: `:379` — `otherNight` only, after `bonecollector`; correctly absent
  from `firstNight`. Exact relative position unverified. No jinxes.
- night_guide.json: `:811-830` good three-beat prose + two show cards; append *"The Harlot
  learns the CHARACTER only, never the alignment."*, *"For a Drunk, Lunatic or Marionette,
  show their TRUE character."*, *"It is both players or neither — never one."*, *"If the Demon
  reveals, do not end the game by killing them."*

setup: `traveller.alignment.<seatId>` (T1). identity: plain.

night.other (**other nights only**):
  gate: `Gates.aliveHolder` (T7 — a dead Harlot still gets a step today).
  action: `Sequence` of three beats —
  1. `ChoosePlayers(min=1, max=1, constraints=[ALIVE], sort=ALIVE_FIRST)`; self last,
     captioned *"(pointless)"*. Annotate any seat the ST recorded as having **offered a tryst**
     today. **Warn (never block)** when the target is the Demon, or when killing the pair would
     end the game — `WinCheck.check` already knows how to reason about this but is only
     consulted after the fact (`GameShell.kt:509-518`).
  2. `YesNo("harlot", "Do they reveal?", yesLabel = "Yes", noLabel = "No")` — **consent is the
     branch**: on `no` the step ends with no information and no deaths (record it — the Harlot
     will make day-time claims about it); only on `yes` is the Harlot woken a second time.
  3. On `yes`: reveal, then **one confirmed action `Both die`** and a `Neither dies` action.
     **No third option.**
  effects: `Both die` → `Attack(SOURCE)` + `Attack(TARGET)` with
    `KillCause(kind = TRAVELLER_ABILITY (see Group note 5), sourceCharacterId = "harlot")` in
    **one undoable update**, each preceded by its own protection review.
    `DeathCause.OTHER_NIGHT_DEATH`, **never `DEMON`** — today `SeatSheet.kt:271-273` only
    offers "Died at night" → `DEMON`, which would mislead the Undertaker, Ravenkeeper, Sage,
    Godfather and the log.
  deferred: both deaths announced at DAWN. If the target is a Ravenkeeper / Sage / Farmer /
    Moonchild / Sweetheart / Barber / Poppy Grower, their on-death triggers fire — surface the
    `DeathTrigger` table before confirming.
  expiry: none (the DEAD tokens are markers; death lives in `Player.alive`).
  info: **add `"harlot"` to `InfoCalc.supports` (`:29-36`)** with `targetsNeeded = 1`
    (`:22-26`) and reuse `revealCharacter(ctx, targets, "Harlot")` (`:285-292`) —
    the same function the Ravenkeeper and Grandmother already use. Caveats:
    *"The Harlot learns the CHARACTER, not the alignment."* and, for a Drunk/Lunatic/
    Marionette target, *"Show <trueCharacter> — the Harlot learns the true character, not the
    token that player believes."* Use `characterId`, **never** `shownCharacterId`.
    An impaired **Harlot** gets the existing false-info affordance (a wrong character token);
    the **target's** impairment is irrelevant. `("barista","Sober & Healthy")` on the Harlot
    forces true info.
  show: existing "THIS CHARACTER SELECTED YOU" card + a one-tap
    `ShowCard.CharacterCard("THIS PLAYER IS", trueCharacterId)`.
  visibility: the target sees the selection card + the Harlot token; the Harlot sees the
    target's character token; **nobody else learns anything** — the Demon and Minions are told
    nothing.

day: record, per day, the players who publicly **offered to reveal** ("tryst offers") — the
wiki calls out that the Harlot arranges targets during the day and may renege — and surface
them as annotations on the next night's picker. Also worth logging what the Harlot publicly
**claimed** to have learned (an evil Harlot lies; wiki example 2).

death: **Demon-only protections do not apply** — the Monk's `Safe` and the Soldier protect
against `DEMON_ABILITY`, and this is not one. Sailor `CANT_DIE`, Tea Lady `CANT_DIE`,
Innkeeper `CANT_DIE_TONIGHT`, Fool, Lleech `DEATH_TIED_TO`, Zombuul `RegistersDead` **do**
apply; Devil's Advocate (execution-only) does not. If the target cannot die, the honest UI is
*"the protection stops that death — decide what happens"*, not a silent one-sided kill: the
rules only offer both-or-neither.

ledger: `CHOICE` (target + consent) and `TOLD` (the character shown) per night;
`STATEMENT` for tryst offers and for the Harlot's day-time claims.

tests:
- Given a dead Harlot, Then no `harlot` step. **Fails today.**
- Given a target that is the Drunk (`shownCharacterId = "chef"`), Then the Harlot info
  headline names **Drunk**, not Chef. **Fails today — `supports("harlot")` is false.**
- Given any target, Then the caveats include *"learns the CHARACTER, not the alignment"* and
  never mention alignment.
- Given consent = yes and "Both die", Then both are dead with `OTHER_NIGHT_DEATH` in a single
  undoable step (one undo restores both). Given consent = no, Then no deaths and no info.
- Given the target is the Demon, Then a *"do not end the game"* warning is produced.
- Given a poisoned Harlot, Then an impairment caveat + the false-info affordance; adding
  `("barista","Sober & Healthy")` removes it.
- Given `("barista","Acts Twice")` on the Harlot, Then **two** harlot steps with independent
  target/consent state.
- Given a dead Harlot holding `("bonecollector","Has Ability")`, Then a restored step appears
  and "both die" kills only the target.

open: exact `bonecollector`/`harlot` relative night-order position; whether the Harlot may
target themselves (the ability text says *"a living player"*, the How-to-Run says *"any
player"*).

---

## gangster — Gangster · Experimental Traveller · P0:3 P1:4

today: **inert.** No neighbour computation, no agreement capture, no kill action, no
once-per-day gate — every part of the rule is the storyteller's memory. The only correct
`DeathCause` available is `STORYTELLER`, which reads as *"died (storyteller)"* in the log.

data:
- characters.json: `:2105-2116` ok (text correct with British "neighbour" vs the wiki's
  "neighbor"; empty night reminders; **no tokens** — matches the printed character).
- night_and_jinxes.json: correctly absent. No jinxes.
- night_guide.json: no entry. `day_guide.json` row:
  > *"Once per day the Gangster declares they want to use their ability. Ask an alive
  > neighbour if they agree — you must hear it. If one agrees, the other alive neighbour dies.
  > If both agree, the Gangster chooses. If neither agrees, nothing happens and the ability is
  > not used up. Once an agreement is reached the ability is spent for the day, even if the
  > victim survives. Dead neighbours are skipped."*

setup: `traveller.alignment.<seatId>` (T1). identity: plain. night: **none.**

day:
- **Targets are derived, never free.** Lift `InfoCalc.aliveNeighbours` (`:168-181`, private
  today) into `Status.aliveNeighbours(state, playerId): Pair<Player?, Player?>`, made
  **directional** so the two sides can be labelled clockwise / counter-clockwise. Dead seats
  are skipped, and the pair is **recomputed on every state change** — people die mid-day, and
  the **Matron** (traveller) reseats people. Never cache.
- The ST records: (1) which neighbour **agreed** — `<A>` / `<B>` / `Both` / `Neither`; and
  (2) if both agreed, **which one the Gangster chose**. The victim is always *the other alive
  neighbour*. *"The Storyteller must hear and confirm this agreement"* — so this is an explicit
  ST input, not a free-text note.
- **The spend rule is the counter-intuitive one**: the ability is spent by the **agreement**,
  not by the death. The wiki's Fool example is explicit — the victim survives, and the Gangster
  may not use the ability again today. **Both** branches of the protection dialog (*"They die
  anyway"* and *"Death prevented"*) must set the flag. `Neither agrees` ⇒ **not** spent; the
  Gangster may ask the other neighbour later the same day (wiki example 2).
- Day-scoped flag `gangsterUsed:<playerId>`, cleared on DAY→NIGHT
  (`GameActions.kt:261-262`).
- `DayBriefing.Slot.DAY_START`, kept live: *"Gangster (<Name>) — living neighbours: <A>
  (clockwise), <B> (counter-clockwise). Ability unused today."*
- **A Gangster kill is not an execution**: no `ExecutionRecord`, no Butcher trigger, no effect
  on `aboutToDie`, and it does **not** satisfy a Vortox / Mayor "an execution happened" test.
  It does reduce `alivePlayers.size` and therefore the execution threshold shown at
  `DayScreen.kt:71-72` — already recomputed live, so that part is fine. Say all of this on the
  resolver: *"A Gangster kill is not an execution: the day continues, and it does not let the
  Butcher nominate again."*
- Edge case: fewer than 3 alive, or both directions resolving to the **same** player →
  *"only one living neighbour — the ability cannot be used as written"*, ST decides.
- Interactions: dead Gangster ⇒ no ability unless `("bonecollector","Has Ability")` (then it
  is live *until dusk*, i.e. usable on the following day). Barista ACTS TWICE ⇒ two uses
  today. Impaired Gangster ⇒ surface `impairment()` so the ST can decline knowingly.

death: needs a real cause — `DeathCause.DAY_ABILITY` (or `GANGSTER`) with
`DeathRecord.killerSourceId = "gangster"` (`records-and-memory.md` §H.8 already proposes
`killerPlayerId`/`killerSourceId`). `atNight = false` is already correct
(`GameActions.kt:150`), but `GameExtras.kt:53-58` / `:324-328` have no day-death vocabulary.
`deathNotes`/`killOutcome` must be **cause-filtered**: Monk `Safe` and Soldier are
`SAFE_FROM_DEMON` and must **not** raise a spurious protection dialog here; Devil's Advocate
is execution-only. Sailor, Tea Lady, Innkeeper, Fool, Lleech, Zombuul **do** apply.
On-death triggers: Ravenkeeper (night only) and Sage (Demon only) do **not** fire; Farmer,
Moonchild, Sweetheart, Barber, Poppy Grower, Godfather ("an Outsider died today"), Scarlet
Woman (Demon dies with 5+ alive) **do**. Minstrel is execution-only; Saint and the good twin's
execution-only clauses do **not** fire on a Gangster kill (the evil Tips call this out).

ledger: `RULING`/`CHOICE` recording who agreed and who the Gangster chose; optionally who was
*asked* (an ask can be refused without spending the ability).

tests:
- Given seats [G, A(dead), B, C] with G the Gangster, Then `aliveNeighbours` returns `(C, B)`
  — dead A is skipped. **No such public function today.**
- Given neighbour A agrees, Then B dies with `atNight = false` and `gangsterUsed` is set.
- Given the victim is a Fool who does not die and the ST picks "Death prevented", Then the
  victim is alive **and** `gangsterUsed` is still set. *(The least intuitive rule.)*
- Given neither agrees, Then no death and `gangsterUsed` is **not** set.
- Given both agree and the Gangster picks A, Then A dies and B lives.
- Given a Gangster kill of a Soldier, Then the "safe from the Demon" note is **not** produced;
  given a Sailor, *"The Sailor can't die."* **is**.
- Given a Gangster kill, Then `DayRules.executionSpent(state) == false` and the Butcher is not
  enabled. Given DAY→NIGHT→DAY, Then the flag is cleared.

open: none.

---

## gnome — Gnome · Experimental Traveller · P0:4 P1:3

today: the announcement is modelled as a **first-night step** (`night_and_jinxes.json:370`,
last entry, after DAWN), so a Gnome who arrives on day 2 — the wiki's own second example, and
the normal case for a traveller — gets **nothing at all**: no amigo prompt, no announcement,
no token guidance. Nothing fires when the amigo is nominated. T1 is most severe here: the
Gnome's entire ability is defined relative to an alignment the app never asks for.

data:
- characters.json: `:2122` ability ok; `:2126-2128` `reminders: ["Amigo"]` ok.
  **Drift:** `:2123` sets a `firstNightReminder`; current official role data gives the Gnome
  **no night reminder at all** — the announcement is a day/entry action. Clear it, or retitle
  the row *"Gnome — announce the amigo (day action, shown here for a night-1 Gnome)"*.
- night_and_jinxes.json: `:370` — remove `gnome` from `firstNight` **once the day-entry task
  exists**; until then keep it (better than nothing for a night-1 Gnome). No jinxes.
- night_guide.json: `:1688-1700` → move to `day_guide.json` and add *"The Storyteller may NOT
  prompt the Gnome."*, *"The nominator dies before voting, and the vote still happens."*,
  *"Unlimited uses."*, *"If the amigo changes alignment, nothing changes."*

setup / arrival: `traveller.alignment.<seatId>` (T1) is a **hard prerequisite** and must fire
at **arrival**, not only at SETUP. Then `gnome.amigo` · PICK_PLAYER ·
*"Pick a player of the Gnome's alignment and announce them publicly."* · candidates =
**same alignment as the Gnome** per `Player.isEvil` (evil Gnome ⇒ the Demon and Minions; good
Gnome ⇒ every good player), living first, with an override escape hatch + warning · required ·
applies `PlaceToken("gnome","Amigo", TARGET, exclusive)` — today the tray uses `addReminder`
(`GameActions.kt:186-187`) so **two** amigos can be marked. Surfaced as a **persistent Day-tab
task** driven off *"a Gnome is in play and no AMIGO token is placed"*, so a mid-game arrival is
covered by the same code path.

identity: plain. The amigo's alignment is fixed **at the moment of the announcement**:
*"If their amigo changes alignment, the Gnome's alignment does not change"* — the token stays
put, and it survives the amigo's death and any character change.

night: **none.**

day:
- **Announcement** — `ShowCard.Message`: *"<Amigo> is the same alignment as the Gnome
  (<GnomeName>)."* **Public — all players, including the Demon.** This is the one traveller
  effect that is broadcast.
- **Nomination trigger** — `NominationTrigger(kind = CHOICE → AUTO_DEATH, sourceId = "gnome")`,
  which the `day-engine.md` table already lists. Window: **after the nomination is declared,
  before voting starts.** Gate: the Gnome is alive (or holds `("bonecollector","Has
  Ability")`), the nominee holds `("gnome","Amigo")`, the nominator is not already dead.
  **Unlimited uses**, including zero; the only practical limit is that the amigo can only be
  nominated once per day, so at most one trigger per day.
- **The nominator dies immediately, and the vote still happens** — the nomination is not
  cancelled and the nominee can still be executed. Sequencing matters: the kill must be applied
  **before** votes are recorded, because `DayScreen.kt:71-72` recomputes
  `Voting.executionThreshold(alivePlayers.size)` live and the dead nominator drops out of the
  count. The dead nominator keeps a ghost vote (`GameActions.kill` sets
  `ghostVoteUsed = false`, `:145`) — surface that.
- **The Storyteller may NOT prompt the Gnome.** This directly constrains the UI: the
  affordance must be **quiet, ST-private, and never a modal or a banner the ST reads aloud**.
  Show the fact and be ready to apply the consequence; do not nag.
  `checkNomination` note (quiet styling): *"<Nominee> is the Gnome's AMIGO. Do not prompt the
  Gnome. If they declare before voting, <Nominator> dies now and the vote still happens."*
  with a single `<Nominator> dies (Gnome)` button. After: *"<Nominator> died. Votes now need
  <newThreshold> (one fewer player alive). <Nominator> may still use their ghost vote."*
- **Exile calls are not nominations** — an exile against a traveller amigo must **not** trigger
  the Gnome. `DayScreen.kt:161-164` already separates the paths; keep them separate.
- The Gnome may nominate their own amigo and thereby kill themselves (evil Tips).
- Barista ACTS TWICE on the Gnome is a **no-op** (the ability is not "once per" anything) —
  say so rather than leaving the ST guessing. Impaired Gnome ⇒ surface `impairment()`.

death: `KillCause(sourceCharacterId = "gnome")` — `day-engine.md` §J assigns
`DeathCause.NOMINATION` to the Witch/Golem/Gnome/Virgin collateral family; use it rather than
`STORYTELLER` (`SeatSheet.kt:277-279`). Cause-filtered protections: Sailor / Tea Lady /
Innkeeper / Fool / Lleech / Zombuul apply; **Monk `Safe` and Soldier (Demon-only) and Devil's
Advocate (execution-only) do not.** Several nomination triggers can stack on one nomination
(Gnome + Witch-cursed nominator + Virgin) — `checkNomination` must return **all** of them
together.

expiry: `("gnome","Amigo")` → `Expiry.NEVER` (correct today, but by accident — it is in
neither table).

ledger: whether the Gnome **used or declined** on each triggering nomination — the wiki's
second example is a sequence of choices across days, and an ST tracking a suspicious Gnome
wants that history. `LedgerEntry(kind = CHOICE, sourceId = "gnome", verdict = TRUE/FALSE)`.

tests:
- Given a Gnome added on **day 3**, Then the outstanding day tasks include *"announce the
  Gnome's amigo"*. **Fails today — nothing exists.**
- Given an evil Gnome, Then only evil players are offered as amigo candidates. **Fails today.**
- Given a nominee holding `("gnome","Amigo")` and a living Gnome, Then `checkNomination`
  returns a Gnome trigger naming the nominator. **Fails today.**
- Given the Gnome is dead with no restored ability, Then no trigger; given an **exile** call
  against a traveller amigo, Then no trigger.
- Given 9 alive (threshold 5) and a Gnome kill of the nominator, Then 8 alive and the
  in-progress vote's threshold is 4.
- Given a Gnome kill, Then the cause identifies the Gnome (not `STORYTELLER`) and
  `atNight == false`.
- Given the nominator is a Sailor, Then *"The Sailor can't die."*; given a Soldier, Then the
  Demon-only note is **not** produced.
- Given a second amigo choice, Then exactly one `("gnome","Amigo")` token exists.

open: none.
