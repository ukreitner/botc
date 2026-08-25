# Digest — Sects & Violets Townsfolk (13 characters)

Sources: `docs/audit/characters/{artist,clockmaker,dreamer,flowergirl,juggler,mathematician,
oracle,philosopher,sage,savant,seamstress,snakecharmer,towncrier}.md`.
Types are those of `mechanics/night-engine.md`, `mechanics/status-model.md`,
`mechanics/day-engine.md`, `mechanics/records-and-memory.md`, plus
`mechanics/setup-and-identity.md` §B for identity. Totals: **P0 22 · P1 59**.

## Group notes

1. **`InfoCalc.obligation` is the biggest cross-character gap and is in NO mechanics spec.** 9 of 13 files
   independently ask for `enum InfoObligation { TRUTH, MAY_LIE, MUST_LIE }` +
   `InfoCalc.obligation(state, lookup, role)`, where an **alive Vortox ⇒ MUST_LIE, outranking impairment** ("even if
   they are drunk or poisoned, it must be false"). Today `commonCaveats` (`InfoCalc.kt:158-166`) mashes "may lie" and
   "must lie" into one red string and `NightScreen.kt:903-906` collapses both to one boolean, so the truthful "Show N"
   chip stays the primary control under a Vortox. It belongs beside `Status.impairment` and must be reachable from the
   **day** too (Artist, Savant, Juggler scoring).
2. **`InfoResult` must become typed, with a legal range.** Every numeric SV Townsfolk is broken by
   `NightScreen.kt:914-921`'s hard-coded `0..4`: the Clockmaker's answer is never 0 and runs to `seats/2`; the
   Oracle's is bounded by the corpse count; the Juggler's by the guess count; the Mathematician's is unbounded.
   Propose `Answer.Count(n, min, max)` / `YesNo` / `CharacterPair` / `PlayerPair` / `Message`, plus
   `InfoResult.alternatives: List<Alt(value, reasonText, rulingSourceId)>` so misregistration stops being prose and
   becomes a one-tap card. Combine with `InfoResult.abilityMalfunctions` (already required by
   `setup-and-identity.md` §B.2).
3. **`Nomination` needs registration snapshots that `day-engine.md` does not give it.** The Flowergirl reads "was a
   Demon among the voters" and the Town Crier "was a Minion the nominator", both judged **at the moment of the
   vote/nomination**; both read the live grimoire today and are silently flipped by Fang Gu jumps, Scarlet Woman
   promotion, Pit-Hag and Snake Charmer. Add `nominatorCharacterId`, `nominatorTeam`, `demonIdsAtRecord: List<Long>`,
   `registersRuling: Map<Long, String>`, falling back to the live lookup when null as `WinCheck` does for
   `characterIdAtDeath`.
4. **Two-state token pairs need a mutex group.** `TokenRule.resetTo` covers *"reset at dawn to the other label"* —
   exactly Flowergirl `Demon voted` ⇄ `Demon not voted` and Town Crier `Minion nominated` ⇄ `Minions not nominated`.
   It does not express *"these two may never coexist on a seat"*, and `placeExclusiveReminder` keys on
   `(sourceId,label)`, so today both halves of each pair can sit on one seat saying opposite things. Add
   `TokenRule.mutexGroup: String`, or make `resetTo` imply mutual exclusion.
5. **`ledger` vs `dayLog` must be decided before any of this ships.** Eight of these 13 need a day-time record (Artist
   question, Savant pair, Juggler guesses, claims, malfunctions, misregistration rulings). `records-and-memory.md`
   rules `LedgerEntry` the single record type; `day-engine.md` §A independently defines `DayEntry`/`dayLog`. **This
   digest uses `LedgerEntry`.** conflict: `DayEntry.count` (Juggler correct count) has no `LedgerEntry` field — put it
   in `shown`; `DayEntry.subjectIdsB` is unnecessary, since `targetIds` and `characterIds` are already parallel.
6. **Identity: adopt `setup-and-identity.md` §B, not `philosopher.md`'s `actingCharacterId`.**
   `AbilityGrant(abilityId, sourceId, mode = REPLACE, spent)` + `Identity.actingRoles` +
   `NightStep.abilityId/holderId/sourceId` already reconciles the Philosopher, and its `nightRoleId` deletion is what
   stops a gained ability from making the seat *register* as that character. conflict: that doc's `NightStep(id,
   abilityId, holderId, sourceId)` and `night-engine.md`'s `NightStep(key = StepKey(id, playerId, variant))` are two
   different row types; the union is `StepKey` + `abilityId` + `sourceId` (their key strings already agree).
7. **"Once per game" must become per-seat data, not a string prefix.** `NightScreen.kt:204` detects it with
   `ability.startsWith("Once per game")` and writes a guessed label — `philosopher:"No ability"` is not even in the
   Philosopher's `reminders`, so it is invisible and unread. Use `Character.spentLabel` + `Gates.notSpent` +
   `Effect(kind = SPENT)`, and honour `resurrect(clearSpentMarks = true)`. Affects artist, seamstress, philosopher, sage.
8. **Kill attribution is the Sage's blocker and everyone else's too.** `SeatSheet.kt:271-273` and
   `NightScreen.kt:628-630` both stamp `DeathCause.DEMON` for *any* night death, so once the Sage's wake condition is
   implemented it fires on Assassin, Godfather, Gambler, Moonchild, Sweetheart and Pit-Hag deaths.
   `status-model.md`'s `KillCause(kind, sourceCharacterId, sourcePlayerId)` is the fix; "Died at night" must open a
   cause chooser.
9. **Traveller alignment is never asked, and it silently corrupts the Oracle and the Seamstress** (and Chef, Empath,
   Shugenja, Village Idiot, Town Crier). Already covered by `setup-and-identity.md`'s required
   `traveller.alignment.<seatId>` SetupTask row — nothing further is needed from this group, but both cards depend on it.
10. **`night_guide.json` has no `day` slot, and 2 of these 13 have no entry at all** (`artist`, `savant`; 116 entries
    total). Extend `NightGuideEntry` with `day: GuideNight?` + `NightGuide.forDay(id)`, or add a sibling
    `day_guide.json`. Four more have `shows: []` (`flowergirl`, `sage`, `seamstress`, `towncrier`).

## artist — Artist · sv Townsfolk · P0:2 P1:3

today: zero code — not in `InfoCalc.supports`, no `night_guide` entry, no Day-tab surface; the only
route to the spent token is Grimoire → seat → Add reminder → Artist → "No ability" (4 taps, plain
`addReminder`, stacks silently), and nothing warns that a Vortox forces a false answer or that the
Artist is poisoned at the moment they pull you aside.
data:
  - characters.json: ok (`reminders: ["No ability"]`); set `spentLabel = "No ability"`.
  - night_and_jinxes.json: ok — absent from both order lists (correct), no jinxes.
  - night_guide.json: **missing entirely.** Add an `artist` entry with a new `day` slot: How-to-Run
    prose (whisper Yes / No / I don't know; if you cannot answer that way, prompt them to rephrase
    and the ability is NOT spent; fake-answer evil players bluffing as the Artist) + shows
    `[{YES, message}, {NO, message}, {I DON'T KNOW, message}]`. Add the same three to
    `ShowCards.kt:367-377`'s phrase list.
setup: none
identity: plain. The day card must key on `Identity.actingRoles(...).any { it.abilityId == "artist" }`,
  not `characterId`, so a Philosopher/Cannibal/Pixie-granted Artist gets it (and a granted Artist has
  its own `AbilityGrant.spent`, independent of the real Artist's).
night.first / night.other: **no step, ever.** Must never be added to either order list.
day:
  gate: an ActingRole `artist` on an **alive** seat with no active `Effect(kind = SPENT,
    sourceCharacterId = "artist")`.
  briefing: `DayBriefing.Note(DAY_START, ACTION, "artist", "Artist — Marta has not asked yet.
    Take them away from the circle; whisper Yes, No, or I don't know.", actionId = "artist.ask")`.
  obligation banner above the buttons: MUST_LIE → "VORTOX IS IN PLAY — your answer MUST be false";
    MAY_LIE → "Marta is POISONED (Poisoner) — you may answer falsely"; TRUTH → "answer honestly".
    Also render `InfoCalc.misregistrations(...)` — the canonical example ("Is David the Evil Twin?")
    is exactly the Recluse/Spy case.
  on answer (one undoable transaction):
    `recordPrivate(state, holderId, "artist", text = question, shown = "YES"|"NO"|"IDK")` +
    `Effect(kind = SPENT, sourceCharacterId = "artist", targetId = holder, label = "No ability",
     until = FOREVER, endsWithSource = false)`.
  on "Couldn't answer — ask again": nothing written, nothing spent.
  on "Someone else claimed Artist": `recordStatement(speakerId = x, sourceId = "artist",
    genuine = false)` — no token, real Artist still eligible. This is the ST's record of who they
    fake-nodded at.
death: none. A dead Artist is ineligible (general rule).
ledger: `LedgerEntry(PRIVATE, sourceId = "artist", actorId, text = question, shown = answer,
  impaired)`; `Memory.isSpent(state, "artist", holderId)`. The `impaired` flag feeds the
  Mathematician's window (a poisoned Artist answered falsely = one abnormal ability).
tests:
  - Alive Vortox ⇒ MUST_LIE; Vortox dead ⇒ TRUTH; poisoned + no Vortox ⇒ MAY_LIE; both ⇒ MUST_LIE.
  - Answering twice leaves exactly one SPENT effect and one PRIVATE entry; "couldn't answer" writes neither.
  - A dead Artist, and one with an active SPENT effect, are both ineligible for the day card.
  - Spent Artist → `changeCharacter(seat,"empath")` leaves no `artist` effect and no "has no ability"
    (**fails today**: `assignCharacter`, `GameActions.kt:46-53`, never clears reminders).
  - A `genuine = false` Artist claim places nothing and leaves the real Artist eligible.
open: the wiki has no dedicated Artist drunk/poisoned section; MAY_LIE is the general-rule inference.

## clockmaker — Clockmaker · sv Townsfolk · P0:2 P1:6

today: `InfoCalc.kt:218-241` measures from `indexOfFirst { team == DEMON }` — one arbitrary Demon —
and returns the literal string "No Demon in the grimoire" on a Summoner or Lil' Monsta script;
misregistration is prose; the false-number chips offer **0**, which is never a legal Clockmaker
signal, and cap at 4 when a 15-player game runs to 7.
data:
  - characters.json: ok (`reminders: []`, first-night reminder text correct).
  - night_and_jinxes.json: first idx 54 correct, absent from otherNight correct. **Add the missing
    jinx**: `{"id1":"summoner","id2":"clockmaker","reason":"The Summoner registers as the Demon to
    the Clockmaker."}` — `grep summoner` currently returns Marionette/Alchemist/Poisoner/Courtier
    only, so "Jinxes in play" (`GameExtras.kt:200-232`) stays silent too.
  - night_guide.json: rewrite `clockmaker.first` — split the obligation ("If the Vortox is in play
    the number **must** be false; if drunk or poisoned you **may** give a false number"), add
    "Travellers count as seats but are never Minions" and "**never show 0**".
setup: none
identity: plain; step keys on `ActingRole.abilityId == "clockmaker"` so a Philosopher gain works.
night.first:
  gate: `Gates.aliveHolder`. Also fires as a `variant = "first"` re-run
    (`WakeStyle.FIRST_NIGHT`) on any night a seat *becomes* the Clockmaker — Pit-Hag, Philosopher,
    Cannibal eating an executed Clockmaker. This is `night-engine.md` §1's "newly-created
    characters" insertion; the guide entry must be the first-night one.
  action: `ShowInfo("clockmaker", targetsNeeded = 0)`.
  effects: none. no tokens, no kills.
  info: `Answer.Count(n, min = 1, max = seats.size / 2)` where
    `demonSeats = {team == DEMON} ∪ {characterId == "summoner"}` (jinx),
    `minionSeats = {team == MINION} \ demonSeats`,
    `n = min over (d, m) of circularDistance(d, m)`, `circularDistance(i,j) = min(|i-j|, size-|i-j|)`
    over **all** seats including dead and Travellers.
    Degenerate cases return `Answer.Message`, not a number: no Demon and no Summoner → "assign a
    Demon first"; Demon but zero Minions → "pick any plausible number"; >1 Demon → return the
    minimum **plus** a caveat naming every Demon; Lil' Monsta → "no seat — measure from the token
    holder (Ada tonight)".
    alternatives (numeric, one-tap): per dead-or-alive Recluse `r`,
    `min(n, min over d of circularDistance(d, r))` labelled "if the Recluse (Priya) registers as a
    Minion"; per Spy `s` that is currently the unique nearest Minion, recompute with `s` removed.
    Marionette caveat: "the Marionette (Kai) neighbours the Demon — the true answer is 1."
  false alternatives: `(1..seats.size/2) - n`, sorted by closeness to `n`. **Never 0.** MUST_LIE
    demotes the true chip behind a text button; MAY_LIE shows both rows, truth first.
  show: existing number card. Detail must name the winning Minion **and** the runner-up distance,
    and flag a tie in opposite directions.
  visibility: nothing to Demon/Minions/Lunatic.
night.other: none, except the re-run variant above.
day: none.
death: none.
ledger: `recordTold(state, holderId, "clockmaker", shown = "3", impaired)`; any Recluse/Spy
  alternative taken writes `recordRuling(sourceId = "misregister", playerId = recluseSeat)` so a
  later step (Empath, Town Crier) surfaces "you ruled on night 2 that Priya registers evil".
tests:
  - `summoner` + a `poisoner` two seats away and **no** Demon ⇒ 2, not "No Demon in the grimoire" (**fails**).
  - Demon 4 seats from the nearest Minion + a Recluse adjacent ⇒ an alternative of 1, credited to the Recluse (**fails**).
  - Poisoned Clockmaker ⇒ the false set excludes 0 and ⊆ `1..seats.size/2` (**fails**: it is `0..4`).
  - Two Demon seats ⇒ the minimum over both, with caveats naming both. Zero Minions ⇒ a message, no number chip.
  - A seat that becomes `clockmaker` on night 3 ⇒ the night-3 plan holds `StepKey("clockmaker", seat, "first")` (**fails**).
open: no wiki ruling for Lil' Monsta + Clockmaker — surface as a storyteller decision, do not guess.

## dreamer — Dreamer · sv Townsfolk · P0:2 P1:4

today: the answer is computed (`InfoCalc.kt:344-354`) but the picker (`NightScreen.kt:841-861`)
lists **every** seat unfiltered — you can pick the Dreamer themselves or a Traveller — the decoy is
never proposed, and the false-info block (`NightScreen.kt:903-930`) fires only for numeric/yes-no
headlines, so under a Vortox the ST is told to lie and given nothing to lie with.
data:
  - characters.json: ok. Optionally tighten both night reminders to "Show 1 Townsfolk/Outsider and
    1 Minion/Demon token".
  - night_and_jinxes.json: ok — first idx 55, other idx 76, no jinxes.
  - night_guide.json: **P0 text fix.** Both `first` and `other` currently say *"If the Dreamer is
    drunk or poisoned, or the Vortox is in play, neither token needs to be correct."* The Vortox
    rule is the opposite — the wiki's own Example 4 is explicit that the information **must** be
    false. Rewrite: target may not be self or a Traveller (dead players ARE legal); "good"/"evil"
    here mean the character's **type** (Townsfolk/Outsider vs Minion/Demon), **not** alignment;
    Vortox ⇒ neither token may be their character; drunk/poisoned ⇒ you **may** give a false pair.
    Replace the two `token:"pick"` shows with one paired card
    `{"label":"Show both tokens","kind":"tokenPair","token":"pick2"}` (new `GuideShow`/`ShowCard`
    variant, `NightGuide.kt:22-27`, `ShowCards.kt:65-77`).
setup: none
identity: plain. The token shown is always the **target's** `Identity.registersAs(p)` — never their
  granted ability (wiki: a Philosopher who gained the Flowergirl is still shown as the Philosopher).
night.first / night.other: identical.
  gate: `Gates.aliveHolder`.
  action: `ChoosePlayers("dreamer", "Who did the Dreamer point at?", min = 1, max = 1,
    constraints = [ANY_LIVING_STATE, NOT_SELF, NOT_TRAVELLER], sort = ALIVE_FIRST,
    allowNone = false, onResolve = [RecordChoice()])`. Illegal chips render disabled with the reason.
  effects: none — no tokens, no status effects, no kills.
  info: `Answer.CharacterPair(trueId = target.characterId, trueIsGoodHalf = target.team in
    {TOWNSFOLK, OUTSIDER}, decoyId)`. Validator: the pair must contain **exactly one**
    Townsfolk/Outsider token and **exactly one** Minion/Demon token. TRUTH/MAY_LIE ⇒ `trueId` must
    be one of the two; MUST_LIE ⇒ **neither** may equal `trueId` and the true character is excluded
    from both pickers, greyed with "excluded — Vortox".
    Ranked decoy suggestions (the main new value, one-word rationale on each chip):
    (1) if the target is evil, the character they are bluffing as — `bluffSets["demon"]` plus any
    `LedgerEntry(STATEMENT, sourceId = "claim")` naming them, (2) the four characters the wiki names
    as "secretive" when on the script — Snake Charmer, Sage, Mutant, Klutz, (3) not-in-play
    characters of the required half, (4) in-play characters of the required half.
    alternatives: a chosen Recluse offers the "registers as evil" pair (a Minion/Demon token as the
    **true** half); symmetrically for a chosen Spy.
  show: one paired token card; the `detail` must say "Minion or Demon token", not "evil character
    token" — with alignment flips in play the current wording (`InfoCalc.kt:351`) is misleading.
  visibility: nothing to Demon/Minions/Lunatic.
day: none, but the decoy chooser reads `Memory.statementsOn(state, day)` so "who claimed what today"
  is one tap away.
death: none.
ledger: `recordTold(state, holderId, "dreamer", shown = "Vigormortis + Artist",
  targetIds = [target], characterIds = [trueId, decoyId], impaired)` — night-to-night consistency
  is the Dreamer's whole strategy and today nothing is stored at all.
tests:
  - Alive `vortox` ⇒ the validator rejects any pair containing the target's real character (**fails**).
  - Targeting the Dreamer themselves, or a Traveller, is refused; a dead non-Traveller target is accepted (**fails**).
  - A target with `alignmentFlipped = true` ⇒ the true half is still decided by **team**, not alignment.
  - A `philosopher` target carrying a Flowergirl grant ⇒ the true token is `philosopher` (wiki example 2).
  - `bluffSets["demon"] = [artist, juggler, mutant]` + evil target ⇒ those rank above other not-in-play decoys (**fails**).
open: none.

## flowergirl — Flowergirl · sv Townsfolk · P0:0 P1:4

today: genuinely good — the answer is **derived from the recorded votes** (`InfoCalc.kt:307-323`)
rather than from tokens; exiles excluded, failed nominations counted, dead Demons' ghost votes
counted, multiple Demons handled. Two real gaps: `demonIds` is read from the *current* grimoire, and
the two reminder tokens the app's own guide tells you to place are never placed, never cleared, and
can both sit on the seat at once.
data:
  - characters.json: ok (`reminders: ["Demon voted", "Demon not voted"]`).
  - night_and_jinxes.json: ok — other idx 77, absent from firstNight, no jinxes.
  - night_guide.json: split the obligation (Vortox ⇒ answer **must** be inverted; drunk/poisoned ⇒
    you **may**); drop the hand-place-the-tokens instruction once the engine does it; `shows: []`
    today → add `[{YES, message}, {NO, message}, {Ask the Demon, message, "DID YOU VOTE TODAY?"}]`.
    Add `"DID YOU VOTE TODAY?"` to `ShowCards.kt:367-377` — the wiki names it as the official
    recovery path and only "DID YOU NOMINATE TODAY?" exists.
setup: none
identity: plain.
night.other:
  gate: `Gates.aliveHolder` — otherwise `StepGate.Skip("Flowergirl is dead")`, auto-ticked so it
    never blocks the dawn guard. Never on night 1.
  action: `ShowInfo("flowergirl")`, no targets.
  effects: none at night; on completion `RemoveToken("flowergirl", "Demon voted", SOURCE)`.
  info: `Answer.YesNo(today.any { n -> n.voterIds.any { it in n.demonIdsAtRecord } })` where
    `today = nominations.filter { day == cycle - 1 && !isExile }`. **`Nomination.demonIdsAtRecord`
    is the fix** (group note 3): a Fang Gu that jumps onto a good Outsider who voted yesterday
    currently turns the answer into a false YES, because `fanggu` sits at other-night 42 and the
    Flowergirl at 77. Fall back to the live lookup + a caveat for saves without the field.
    `detail` names the evidence — "Bo (Vigormortis) voted on nomination 2 of 3 (Hana → Ari)" — and
    on NO names how many nominations were recorded, so a missing one is visible.
    alternatives: every **Recluse** in today's non-exile `voterIds` → "Priya (Recluse) voted today —
    she may register as the Demon, so YES is also legal" with a one-tap YES card. (Not surfaced
    today: `InfoCalc.kt:318` passes only current Demon-team players to `misregistrations`.)
  false alternatives: MUST_LIE ⇒ the inverted card is primary, the truthful one demoted behind a
    text button; MAY_LIE ⇒ both, truth first.
  show: YES / NO, plus **"Ask the Demon: DID YOU VOTE TODAY?"**, offered automatically when the day
    is flagged uncertain.
  visibility: only the Flowergirl, except the recovery path which wakes the Demon.
day:
  tokens (the run-book, automated): at dawn place `("flowergirl","Demon not voted")` on the
    Flowergirl seat; on every recorded non-exile nomination whose voters include a Demon,
    replace it with `("flowergirl","Demon voted")`. Express as
    `TokenRule("flowergirl", "Demon voted", expiry = DAWN, resetTo = "Demon not voted",
     mutexGroup = "flowergirl.vote")` — see group note 4; both labels must never coexist.
  briefing: `DayBriefing.Note(DAY_START, INFO, "flowergirl", "Flowergirl: the Demon has not voted
    today")`, updating live as nominations are recorded, with a **[Votes not recorded today]**
    escape hatch that writes `recordRuling(sourceId = "flowergirl", text = "votes not recorded")`
    so the night step says "uncertain" rather than silently answering NO.
death: none.
ledger: `Nomination.demonIdsAtRecord` (snapshot) · the uncertainty RULING · `recordTold` on reveal.
tests:
  - Outsider Hana votes on day 2, becomes the Fang Gu on night 2 ⇒ **NO**; the Imp votes then star-passes ⇒ **YES** (**fails**).
  - A `recluse` in `voterIds` with no Demon voter ⇒ NO, with a YES alternative credited to the Recluse (**fails**).
  - Dawn leaves exactly one `flowergirl` token, "Demon not voted"; after a Demon vote, exactly one, "Demon voted" (**fails**).
  - An execution on day 2 with no nomination recorded ⇒ an "uncertain" caveat and the "ask the Demon" card.
  - Exile-only day with every player supporting ⇒ NO (wiki example 3, passes — lock it in). Dead Flowergirl ⇒ `Skip`.
open: none.

## juggler — Juggler · sv Townsfolk · P0:2 P1:6

today: nothing works. There is no way to record the guesses (no day surface at all), `juggler` is
absent from `InfoCalc.supports` so no number is computed and no chip is generated, the step appears
on **every** night and blocks the dawn guard, and the night tray's `availableCopies` heuristic
(`NightScreen.kt:319-339`) reads `reminders: ["Correct"]` as 1 copy and routes to
`placeExclusiveReminder`, which strips the token from every seat — so the grimoire can hold exactly
**one** `Correct` token when the rules need five on one seat.
data:
  - characters.json: `"reminders": ["Correct","Correct","Correct","Correct","Correct"]` (five copies)
    — `night-engine.md` §4 already lists this fix; the authoritative count is
    `TokenRule("juggler","Correct", expiry = DAWN, maxCopies = 5, exclusive = false)`.
  - night_and_jinxes.json: ok — other idx 82, absent from firstNight; the Cannibal jinx is present
    with correct text. The jinx needs an **implementation**, not new data.
  - night_guide.json: add the impairment-timing rule verbatim — "If the Juggler was drunk or poisoned
    when they guessed but is sober and healthy now, still give the **true** number" — and split
    must-lie/may-lie.
setup: none
identity: `AbilityGrant`-aware. "Your 1st day" = the first day this **seat** holds the Juggler
  ability, not day 1 (wiki example 2: a Savant Pit-Hagged into the Juggler on night 4 juggles on
  day 4 and learns the number on night 5). Derive from `AbilityGrant.cycle` / the character-change
  ledger entry, never from `cycle == 1`.
day: the guess recorder — this is the P0.
  gate: an **alive** ActingRole `juggler` with no `LedgerEntry(STATEMENT, sourceId = "juggler",
    genuine = true, actorId = holder)`. Entry points: a Day-tab card and the seat sheet.
  recorder: repeat `[player chip row] × [character search] → Add guess`, capped at **5**; duplicate
    players and duplicate characters are both legal (wiki example 2). **Privacy:** the running score
    is hidden behind a "reveal score" toggle and each row reads only "guess 3 of 5 recorded" — the ST
    is standing inside the circle holding the phone.
  auto-verdict per guess, overridable with a three-state toggle: CORRECT when
    `player(target).characterId == characterId`; ST_CHOICE + note when the target is `recluse` and
    the guess is a Minion/Demon on the script, or `spy` and the guess is a Townsfolk/Outsider, or
    `target.shownCharacterId == characterId` while `characterId != target.characterId` ("they
    *think* they are the Chef; they are the Drunk"); FALSE otherwise.
  on save: one `LedgerEntry(STATEMENT, sourceId = "juggler", actorId, targetIds = [seats],
    characterIds = [guesses] (parallel), shown = "<correct count>", verdict, impaired = <at guess
    time>)`, plus `count(CORRECT)` copies of `("juggler","Correct")` via `addReminder` — never
    `placeExclusiveReminder`. A **[Someone else claimed Juggler]** button writes `genuine = false`,
    places no tokens, and reminds the ST to mime moving tokens (the wiki says to help the bluff).
night.other:
  gate: all of — a genuine `juggler` STATEMENT entry exists for day `cycle - 1`; the holder is alive
    **or** the Cannibal jinx applies; the entry has no `resolvedCycle`. Otherwise
    `StepGate.Skip("Marta has not juggled" / "already revealed on night 2")`, auto-ticked.
    Today `NightOrder.build` emits it unconditionally every night and the dawn guard demands a tick.
  action: `ShowInfo("juggler")`.
  info: `Answer.Count(n = guesses.count { CORRECT }, min = 0, max = guesses.size)`; `detail` lists
    every guess with its verdict so an ST_CHOICE can be re-checked before the number is shown.
  **obligation is measured at trigger time, not guess time** — the official rule. If
    `entry.impaired && !currentlyImpaired && no Vortox`, obligation is TRUTH and the panel must say
    so out loud: "Marta was poisoned when she juggled but is healthy now — give the **true** number."
  false alternatives: `0..guesses.size` minus the truth, nearest-first (not the hard-coded `0..4`).
  effects on completion: `RemoveToken("juggler","Correct", SOURCE)` ×all + `resolveEntry`.
  expiry: `TokenRule("juggler","Correct", expiry = DAWN)` as a backstop — in neither table today.
  visibility: nothing to Demon/Minions/Lunatic.
death: **Cannibal jinx.** When a genuine juggle exists for day D and
  `ExecutionRecord(day = D, outcome = DIED, playerId = juggler)`, then on night D+1 suppress the
  Juggler's own step (today it is decorated "All holders are dead — usually skip" on the one night
  the jinx fires) and annotate the **Cannibal's** step with the number, applying the Cannibal's own
  obligation (a Cannibal poisoned by eating an evil player gets false info). No living Cannibal ⇒
  nobody learns it.
ledger: the guesses entry; `Memory.unresolved(state, "juggler", day)` gates the night step's
  checkbox (`StepMemory.consumes = ["juggler"]`).
tests:
  - Five correct guesses ⇒ five `juggler:"Correct"` reminders on the one seat (**fails**: one, grimoire-wide).
  - [(Ben,pithag),(Ben,witch),(Amy,pithag)] with Ben the Witch ⇒ all accepted, score 1; a sixth is refused (wiki ex. 2).
  - A juggle on day 1 ⇒ night 2 emits a live step, night 3 emits none; never juggled ⇒ never a live step (**fails**).
  - A seat that becomes `juggler` on night 4 and juggles on day 4 ⇒ reveal on night 5, not night 2 (**fails**).
  - `impaired` at guess, healthy now, no Vortox ⇒ obligation TRUTH + the "give the TRUE number" note (**fails**).
  - Juggle + execution on day 1 + a living Cannibal ⇒ the Cannibal step carries the number, the Juggler step is
    suppressed; no living Cannibal ⇒ nobody learns it (**fails**).
open: none.

## mathematician — Mathematician · sv Townsfolk · P0:3 P1:6

today: `InfoCalc.kt:77-80` is a **static stub** — headline "Count abilities that malfunctioned since
dawn", caveat "Track malfunctions manually". Because the headline is not numeric, `NightScreen`
generates neither the "Show N" chip (`:886-895`) nor any false-number chips (`:903-930`) — so under
a Vortox the ST gets an instruction they cannot act on. Only one `Abnormal` token can exist
grimoire-wide, and it never expires.
data:
  - characters.json: leave `reminders: ["Abnormal"]` — per `night-engine.md` §4 the count is
    unbounded and cannot be expressed in the token list:
    `TokenRule("mathematician","Abnormal", expiry = DAWN, maxCopies = Int.MAX_VALUE,
     exclusive = false)`. conflict: `mathematician.md` asked for 5 literal copies in the data; adopt
    the rule instead, keeping the one label so the `(sourceId,label)`-exists build check passes.
  - night_and_jinxes.json: **add two missing official jinxes** —
    `{"id1":"drunk","id2":"mathematician","reason":"The Mathematician learns if the Drunk's ability
     yielded false info or failed to work properly."}` and the same for `marionette`. These are
    exactly the two that override the "due to another character's ability" clause; without them an
    ST correctly concludes a Drunk's false info does not count, which is wrong on those scripts.
    Drift note: the repo's chambermaid jinx reads "…learns if the Mathematician wakes tonight or not,
    even though the Chambermaid wakes first"; the official list reads "The Chambermaid can detect if
    the Mathematician will wake tonight". Same effect.
  - night_guide.json: rewrite both slots — count **players**, not events; a player who malfunctions
    twice counts once; **never count the Mathematician's own ability**; an ability that would have
    had the same effect anyway does not count; on night 1 the window is the night so far; remove the
    Abnormal tokens afterwards; Vortox ⇒ **must** be false, drunk/poisoned ⇒ **may**.
setup: none
identity: plain.
night.first / night.other: identical; last character step before DAWN on both (first 71, other 94) —
  correct, and it is what satisfies the Chambermaid jinx.
  gate: `Gates.aliveHolder`.  action: `ShowInfo("mathematician")`, no targets.
  info: `Answer.Count(n, min = 0, max = players.size)` where `n` = distinct **players** with a
    malfunction in this window whose `verdict == TRUE`, **excluding the Mathematician's own seat**.
    `night-engine.md` §6 gives `MalfunctionEvent(night, playerId, characterId, reason, sinceDawn)`
    and `mathematicianCount`; unify its tri-state with `LedgerEntry(RULING, sourceId = "malfunction",
    actorId = whoMalfunctioned, verdict)` — UNJUDGED = proposed, TRUE = counted, FALSE = dismissed.
    Nothing counts silently: several official cases are judgement calls.
  auto-proposals the engine can already make (each UNJUDGED, one line of storyteller voice): an impaired holder of a
    step that ran tonight ("Marta (Oracle) is poisoned — her number was false") · `derivedPoison` / No Dashii adjacency
    · a `NO_ABILITY` effect from **another** character (Fearmonger, spent Fool) · alive Vortox ⇒ one per Townsfolk
    given information · a misregistration ruling **taken** in an info step, marking the **reader** not the Recluse
    (wiki: "a Recluse registering as evil to the Chef" → mark the Chef) · a death blocked by, or occurring despite,
    protection (wiki: "a poisoned Soldier dying from the Imp's attack" → mark the Soldier) · Drunk/Marionette whose
    believed ability ran, **only when that jinx is in play** · Lunatic jinx: `lunatic:"Attack N"` ≠ the Demon's target.
  never proposed: the Mathematician's own malfunction (filtered out entirely); a player merely drunk or poisoned with
    no ability that fired; an ability that would have done the same thing anyway (the wiki's poisoned Snake Charmer who
    chose a Townsfolk — proposed, then **dismissed**). De-duplicate by `playerId`: the count is players, not events.
  effects on completion: place one `("mathematician","Abnormal")` per counted seat so the grimoire
    matches the run-book, then remove them all and close the window.
  false alternatives: `0..(n+3)` minus `n`, nearest-first. Note that a Vortox also **inflates** the
    true count (wiki example 3: six abnormal abilities, the Mathematician learns 4), so the lie
    should not be 0 when half the town got false info.
  visibility: nothing to Demon/Minions/Lunatic.
day: the window is "since dawn", so **day-time malfunctions are real** — a poisoned Slayer's shot
  failing, a drunk Gossip's statement not killing, a Witch curse that did nothing, an Artist answered
  falsely. Add an **[Ability malfunctioned…]** action on the Day tab and each seat sheet writing a
  storyteller-sourced RULING in two taps. conflict: `night-engine.md`'s `mathematicianCount` filters
  `it.night == night` and would drop these — the window must be *dawn N → the Mathematician's step
  on night N+1*, not "tonight".
death: none.
ledger: `LedgerEntry(RULING, sourceId = "malfunction")` ×n per window, cleared for cycles < current
  at dawn; `recordTold` on reveal.
tests:
  - Poisoned Oracle, everything else normal ⇒ exactly one proposal, for the Oracle; confirmed ⇒ 1 (wiki ex. 1).
  - Poisoned Snake Charmer who chose a Townsfolk + drunk Juggler given the truth ⇒ both UNJUDGED; dismissed ⇒ 0 (ex. 2).
  - A poisoned Mathematician is never proposed and never counted; two reasons for one player count once.
  - Three counted malfunctions ⇒ three `mathematician:"Abnormal"` reminders, one per seat (**fails**: one, grimoire-wide);
    none survive to the next window (**fails**: in neither expiry table).
  - `lunatic:"Attack 1"` on Bo + the real Demon killing Hana ⇒ one proposal names the Lunatic; same target ⇒ none (**fails**).
  - Every Mathematician step returns a numeric `Answer.Count`, so the number chip is generated (**fails**: static stub).
open: whether a Vortox-inflated count includes the Mathematician themselves when they are among the
  Townsfolk given false information — assert whichever the implementation picks.

## oracle — Oracle · sv Townsfolk · P0:0 P1:4

today: the calculation (`InfoCalc.kt:271-279`) is right — it counts all seats including Travellers,
uses alignment (`team.isEvil != alignmentFlipped`) not team, reads live state so a dead player made
evil later counts, and drops resurrected players. The gaps are all around it: an unflagged evil
Traveller silently reads good, misregistration is prose, the false chips are a fixed `0..4`, and a
dead Oracle still gets a step that blocks the dawn guard.
data:
  - characters.json: ok (`reminders: []`).
  - night_and_jinxes.json: ok — other idx 80, absent from firstNight, no jinxes.
  - night_guide.json: split the obligation; add "Dead Travellers count if they are evil" and
    "never show a number larger than the number of dead players".
setup: `traveller.alignment.<seatId>` (kind ACK good/evil, required per Traveller) — already in
  `setup-and-identity.md`'s SetupTask table. Without it `Team.TRAVELLER.isEvil == false`
  (`Character.kt:16`) and wiki example 2 is unreproducible; the same silent error corrupts Chef,
  Empath, Seamstress, Village Idiot, Shugenja and Town Crier.
identity: plain.
night.other:
  gate: `Gates.aliveHolder` — otherwise `StepGate.Skip("Oracle is dead")`, auto-ticked. Never night 1.
  action: `ShowInfo("oracle")`, no targets.
  effects: none.
  info: `Answer.Count(evilDead.size, min = 0, max = dead.size)` where `dead = players.filter
    { !it.alive }` (Travellers in, resurrected out) and `evilDead = dead.filter { isEvil(it) }`.
    `detail`: "**3 dead** — evil: Bo (Vigormortis), Ari (Witch) · good: Hana (Chef)", grouped by
    alignment rather than one 10-name line.
    alternatives (numeric): per dead **Recluse**, `n + 1` labelled "if the dead Recluse (Priya)
    registers as evil"; per dead **Spy**, `n - 1` labelled "if the dead Spy (Ari) registers as
    good"; combine into a range — "true 2 · plausible 1–3".
  false alternatives: `0..dead.size` minus `n`, nearest-first. **Never a number greater than the
    corpse count** — offering "4 dead evil players" when only 3 are dead is an instant tell.
  show: existing number card; MUST_LIE demotes the true chip behind a text button.
  visibility: nothing to Demon/Minions/Lunatic.
day: none.
death: reads `!alive`. A Zombuul with a `registeredOnly` DeathEvent (`status-model.md` §6) must count
  toward this number while `alive` is still true — the Oracle side only needs to read
  `Player.registersAlive`, the state itself belongs to the Zombuul.
ledger: `recordTold(state, holderId, "oracle", shown = "2", impaired)`; any Recluse/Spy alternative
  taken writes `recordRuling(sourceId = "misregister")` so the ruling is consistent across steps.
tests:
  - Executed Flowergirl + Demon-killed Juggler ⇒ 0 (wiki ex. 1, passes — lock it in). Night 1 emits no step.
  - 5 dead good, 2 dead evil, a dead Traveller **marked evil at seat creation**, a dead Minion ⇒ 4 (ex. 2, **fails**).
  - A Traveller seat whose alignment was never chosen is reported by `SetupTasks.activeFor`.
  - `resurrect` on a dead evil player drops the count by 1 and keeps the DeathEvent; `revive` drops both.
  - Dead `recluse`, true count 2 ⇒ an alternative of 3; a dead `spy` counted evil ⇒ an alternative of 1 (**both fail**).
  - 3 dead + poisoned Oracle ⇒ the false set ⊆ `{0,1,2,3}` minus the truth (**fails**: `0..4`). Dead Oracle ⇒ `Skip`.
open: none.

## philosopher — Philosopher · sv Townsfolk · P0:4 P1:5

today: **zero Philosopher code exists** — `grep -rn philosopher --include=*.kt` returns nothing. The only way to move
the seat's night step is `assignCharacter` (`GameActions.kt:46-53`), which rewrites `characterId`, so the Philosopher
**registers** as the gained character to the Washerwoman, Librarian, Investigator, Dreamer, Ravenkeeper, Balloonist and
No Dashii adjacency — the exact opposite of "they gain that character's full ability and do not become the character
themselves". The row is emitted every night forever, the duplicate is never made drunk, and that drunkenness never ends.
data:
  - characters.json: rewrite both night reminders into the wiki's **two branches** — swap the token **only if the
    chosen character is not in play**; place DRUNK **only if it is in play**. The current text (and
    `night_guide.json:507-509`) says "swap in that character's token" unconditionally, which is what produces the
    two-seats-one-characterId bug. Keep `reminders: ["Drunk","Is the Philosopher"]`; set `spentLabel = ""` — the gate
    is the grant, not a token. (Today "Mark spent" writes `philosopher:"No ability"`, a label absent from the
    character's own `reminders`, so it never appears in the tray and nothing reads it.)
  - night_and_jinxes.json: first idx 9, other idx 7 — correct and deliberately early. Bounty Hunter
    jinx present and correct.
  - night_guide.json: same two-branch correction; add a show
    `{label:"Chosen ability (auto)", kind:"token", token:"gained"}` pre-filled from the grant.
setup: none
identity: **the defining case.** `grants += AbilityGrant(abilityId = gained, sourceId = "philosopher",
  mode = REPLACE, spent = false, cycle)` per `setup-and-identity.md` §B.1; `Identity.registersAs(p)` stays
  `characterId` (**the P0 fix**); `actingRoles` returns `[gained @slot gained, source philosopher]`;
  `NightStep.sourceId` badges the row `"Empath — Nadia (via the Philosopher)"`. `nightRoleId` is deleted. A granted
  once-per-game ability uses the grant's **own** `spent` flag, so a Philosopher-Seamstress and the real Seamstress are
  independent.
night.first / night.other:
  gate: `characterId == "philosopher"` **and** no `philosopher` grant yet **and** (alive, or the
    gained ability works while dead — wiki: "If the Philosopher's ability works while dead, such as
    the Klutz's, it works if the Philosopher is dead"). Once granted the `philosopher` row is **not
    emitted**; the gained character's row is emitted for this seat instead.
  action: `ChooseCharacter("philosopher", "What did they point at? (or nothing)",
    pool = SCRIPT ∩ (TOWNSFOLK ∪ OUTSIDER), allowNone = true)`. Sort **not-in-play first** (the
    token-swap branch), then in-play with an inline "⚠ makes Bo drunk" badge. Exclude Travellers and
    every evil character. Optional and repeatable until spent — they wake every night until used.
  effects: the grant · `PlaceToken("philosopher","Is the Philosopher", SOURCE, exclusive)` · `RecordChoice()` ·
    `ShowCardTo(SOURCE, "gained ability")`. **No stored Drunk token:** the duplicate's drunkenness is a `StandingRule`
    (`status-model.md` §1) emitting `Effect(kind = DRUNK, targetId = <every other seat whose characterId ==
    grant.abilityId>, sourceCharacterId = "philosopher", sourcePlayerId = philSeat, until = SOURCE_LOSES_ABILITY,
    endsWithSource = true, label = "Drunk")`. §2's `active(e, cap)` recursion then gives, free: the duplicate goes
    sober when the Philosopher **dies** or is **poisoned** (both on the wiki), drunk again if the Philosopher is cured,
    and drunk automatically when the duplicate **enters play later** (the wiki's "is in play now" clause — Pit-Hag,
    Alchemist, a second Philosopher).
  deferred — same-night activation (wiki: "If this ability is used on the first night only, they use it tonight"): if
    the gained slot is later tonight, `NightPlan` re-derives and the row appears. Otherwise (a first-night-only ability
    gained later, or an other-night slot < 7 — barista 1, bureaucrat 2, thief 3, plaguedoctor 4, bonecollector 5,
    harlot 6) insert at `cursor + 0.5` per `night-engine.md` §1's insert-after-cursor rule, badged *"Out of order —
    this became true after their slot"*, with `WakeStyle.FIRST_NIGHT` for first-night-only abilities.
  expiry: `("philosopher","Is the Philosopher")` — `Expiry.NEVER`; `Drunk` is advisory rendering of the derived effect.
  info: none of its own. The gained ability's `InfoCalc` runs with `abilityId = grant.abilityId`, `holderId` = the
    Philosopher's seat; impairment caveats attach to the Philosopher, not the duplicate.
  show: `YOU NOW HAVE THIS ABILITY` + the gained token, **pre-filled**; the player points silently at the existing
    full-script `SheetCard` (`NightScreen.kt:254-262`). visibility: Demon/Minions and the duplicate are told nothing.
day: none for the Philosopher itself — but every day surface (Savant visit, Artist question, Fisherman advice, Juggler
  guesses, Gossip statement) must gate on `Identity.actingRoles`, not `characterId`, or a Philosopher who gains any of
  them is unreachable.
death: the Philosopher's own death ends the derived DRUNK automatically. A gained Sage means the Philosopher must wake
  on their own Demon-kill death; a gained Klutz works while dead.
ledger: `recordChoice(state, "philosopher", holderId, targetIds = [], characterIds = [gained])`;
  `AbilityGrant.spent` and `.cycle` carry the rest.
tests:
  - Gain a not-in-play `empath`: `characterId` stays `"philosopher"`, `actingRoles(0) == [empath @empath
    source philosopher]`, the seat holds `("philosopher","Is the Philosopher")`, and the plan emits an
    `empath` row for seat 0 and **no** `philosopher` row (once-per-game gating).
  - Gain an in-play `empath` (seat 3) ⇒ `isImpaired(3)` true, `isImpaired(0)` false; killing **or** poisoning
    seat 0 makes `isImpaired(3)` false; a later-arriving Empath is drunk automatically.
  - Gained `klutz` ⇒ `librarian` does **not** list seat 0 as an Outsider and the Dreamer sees "Philosopher"
    (**the P0 registration fix**).
  - `philosopherGain(0, "clockmaker")` on cycle 3 ⇒ a `clockmaker` step for seat 0, `WakeStyle.FIRST_NIGHT`,
    inserted after the cursor; a gained `harlot` (slot 6 < 7) gets the same treatment tonight, normal rows after.
  - A Bone Collector re-grant to `chef` overwrites the grant and the Empath becomes sober.
open: a Drunk/Marionette who believes they are the Philosopher must still be run — their choice does
  nothing. Guard `philosopherGain` behind `roleWorks(role)` and offer a "fake the swap" path that
  changes no state (`AbilityGrant.alwaysFalse`).

## sage — Sage · sv Townsfolk · P0:3 P1:4

today: `StatusEffects.deathNotes` (`StatusEffects.kt:96`) surfacing "Sage: if the Demon killed them,
show 2 players, one the Demon" in `DemonKillPanel` is the single best thing the app does for this
character. Everything else is wrong: the row appears every night from night 2 regardless of whether
the Sage died, or how; `InfoCalc.kt:423-431` returns the same live answer in all those cases; and on
the one night it fires the row is decorated **"All holders are dead — usually skip."**
(`NightScreen.kt:702, :751-757`) — the app actively advises skipping the step at the exact moment
the Sage must wake.
data:
  - characters.json: add `"reminders": ["Woke"]` and `spentLabel = "Woke"` (the `(sourceId,label)`
    build check requires the label to exist in the data).
  - night_and_jinxes.json: other idx 61 correct (after every Demon, after Assassin/Godfather/
    Gossip/Hatter/Barber/Sweetheart, before Banshee/Professor); no first-night entry, correct.
    Leviathan and Riot jinxes both present with correct text — they need implementations.
  - night_guide.json: `shows: []` today → add `{label:"The pair", kind:"message",
    text:"One of these two is the Demon"}`. Add the Leviathan/Riot clause to the instructions.
setup: none
identity: plain; `actsWhileDead = true` on the `Character` (`night-engine.md` §Data changes), so the
  UI shows the **positive** badge *"Dead — this ability fires because they died. Wake them."*
night.other:
  gate: a `DeathEvent` with `playerId == sage`, `cycle == state.cycle`, `atNight`,
    `resurrectedAtCycle == null`, and `cause.kind == DeathKind.DEMON_ABILITY`; **or** `leviathan` in
    play and this cycle's Sage death was `EXECUTION`; **or** `riot` in play and the Sage died to a
    Riot kill this cycle (Riot kills during the day, so the row is the following night); **and** no
    active `Effect(kind = SPENT, sourceCharacterId = "sage")`. Otherwise the row is **not emitted at
    all**. Dead is required, not disqualifying.
  **prerequisite:** kill attribution. `SeatSheet.kt:271-273`'s "Died at night" and
    `NightScreen.kt:628-630` both hard-code `DeathCause.DEMON`, so Assassin, Godfather, Gambler,
    Moonchild, Sweetheart, Lycanthrope and Pit-Hag deaths all land in the bucket this gate keys on.
    `status-model.md`'s `KillCause(kind, sourceCharacterId, sourcePlayerId)` is the fix, and the
    "Died at night" button must open a cause chooser. Wiki example 3 is precisely the Pit-Hag case:
    *"Because the Sage died due to the Pit-Hag, not the Demon, the Sage does not wake."*
  action: `ChoosePlayers("sage", "Point at two players — one must be <Demon>.", min = 2, max = 2,
    constraints = [ANY_LIVING_STATE, NOT_SELF], sort = ALIVE_FIRST,
    onResolve = [RecordChoice(), MarkSpent("sage")])`. Default-select the **killing** Demon
    (`cause.sourcePlayerId`) plus one other; when `alivePlayers.size <= 3`, hint that a dead player
    is a legal second pick (the How-to-Run's "final night" guidance).
  effects: `Effect(kind = SPENT, sourceCharacterId = "sage", label = "Woke")`. No kills, no status.
  info: the pair is chosen, not computed. The panel lists the Demon(s) with the **killer**
    highlighted — `InfoCalc.kt:424-429` lists every DEMON-team seat and lets the ST pair "any other
    player", which is wrong on Legion, a Kazali-made second Demon, a Scarlet Woman promotion or a
    star pass. Use `cause.sourcePlayerId`, snapshotted at the kill, never re-resolved live.
    obligation: use `DeathEvent.impairedAtDeath` — *"was the Sage drunk/poisoned when the Demon
    killed them"* — **not** the current state, which is what `commonCaveats` reads today. Impaired at
    death, or an alive Vortox ⇒ **require** that the pair excludes the killing Demon, label the panel
    "FALSE info", offer a one-tap "suggest a false pair". alternatives: an alive Recluse ⇒ "let the
    Recluse register as the Demon", so the pair may contain the Recluse and no real Demon.
    The generic caveat *"<name> is dead — they normally don't act"* (`InfoCalc.kt:150`) must be
    suppressed here — the Sage is the one character for whom it is false.
  visibility: only the Sage.
day: none.
death: the trigger *is* a death. `DeathTrigger("sage")` per `status-model.md` §5 — matches on
  `cause.kind == DEMON_ABILITY && event.playerId == holder`, produces
  `Prompt(at = TONIGHT, kind = CHOOSE_PLAYER, characterId = "sage", title = "Marta was killed by the
  Imp — wake her and point at two players")`, inserted at night-order position 61.
ledger: `recordChoice(state, "sage", holderId, targetIds = [a, b], impaired = event.impairedAtDeath)`
  so the Sage's later public claim can be checked.
tests:
  - An alive Sage emits no step; an Imp kill on night 3 emits one whose panel names the **killing** Demon.
  - Executed on day 2 with no Leviathan ⇒ no step; with `leviathan` in play ⇒ a step. Riot kill ⇒ a step that night.
  - Killed at night with `cause.sourceCharacterId = "pithag"` ⇒ no step (wiki example 3).
  - `("sweetheart","Drunk")` at death ⇒ `impairedAtDeath`, false-pair mode, and a pair containing the Demon rejected.
  - Woke on night 3 ⇒ night 4 emits none; after a Professor resurrection and a second Demon kill it is emitted again.
open: the wiki does not address a resurrected Sage dying twice. Treating "if the Demon kills you" as
  not-once-per-game is the natural reading, but flag it in the UI rather than deciding silently.
  No wiki ruling for Lil' Monsta + Sage (the Demon is a token, not a seat).

## savant — Savant · sv Townsfolk · P0:1 P1:4

today: **invisible.** `grep -rn savant --include=*.kt` returns nothing; no `night_guide` entry (so
even the Reference tab has no how-to-run); no Day-tab concept of a private storyteller conversation.
The ST invents two statements on the spot, remembers which was true, and remembers what they already
said on earlier days so the set stays consistent — for up to five days, in their head.
data:
  - characters.json: ok — `reminders: []` is correct (the Savant officially has no token; "visited
    today" must be **state**, not a grimoire token).
  - night_and_jinxes.json: ok — absent from both order lists (correct), no jinxes among all 58.
  - night_guide.json: **missing entirely.** Add a `savant` entry under the new `day` slot: the
    How-to-Run plus *"Keep the information helpful and related to the game. Avoid saying who exactly
    the Demon is, or it could be a very short game"* and the drunk/poisoned clause.
setup: optional `secrets["savant:<day>"]` seeding is unnecessary — the ledger holds it.
identity: keys on `Identity.actingRoles(...).any { it.abilityId == "savant" }`, so a
  Philosopher-gained Savant gets the day card automatically (the wiki's Philosopher page recommends
  exactly this pick).
night.first / night.other: **no step, ever.** Correctly absent from both order lists.
day:
  gate: an **alive** ActingRole `savant` with no `LedgerEntry(PRIVATE, sourceId = "savant",
    actorId = seat, cycle = state.cycle)`. Once per day; the **Savant initiates**, so the app must
    never gate the day on it — it must only make recording instant.
  entry points (three, because it happens mid-conversation): a `DayBriefing.Note(DAY_START, ACTION,
    "savant", "Savant — Nia may visit you today", actionId = "savant.visit")`; a pinned Day-tab card
    above "New nomination"; and a **"Savant visit…"** action on the seat sheet.
  the composer (the substance): two panes, **TRUE** and **FALSE**, swappable in one tap. Each pane
    has a free-text field **plus** one-tap candidates generated from the live grimoire — the app
    already computes exactly this class of fact for ~30 characters in `InfoCalc` and throws none of
    it at the Savant:
    "<X> and <Y> are the same alignment" (reuse `InfoCalc.seamstress`) · "There are N Outsiders in
    play" · "A <Character> is in play" / "No <Character> is in play" · "N of the players between <X>
    and <Y> are evil" · "Exactly N players got true information last night" (from the malfunction
    window) · "The nearest Minion to the Demon is N seats away" (reuse `InfoCalc.clockmaker`) ·
    "<X> is evil" / "<X> is good". Every candidate is generated in a true **and** a plausible false
    variant — a false variant must be a perturbation (swap a name, change a count by one), never
    trivially absurd. Render the "don't name the Demon" guard rail inline.
  a **"previously told"** strip lists this Savant's earlier entries and warns on a duplicate or a
    direct contradiction — the practical failure mode is saying "there is one Outsider in play" as
    the TRUE statement on day 2 and the FALSE one on day 4.
  obligation: impaired ⇒ the wiki's rule is **two true OR two false** (ST's choice) — the composer
    switches to a radio and the verdict becomes `BOTH_TRUE`/`NEITHER_TRUE`. An alive Vortox ⇒ default
    to two false with an override, not a hard rule (see `open:`).
  on save: `recordPrivate(state, holderId, "savant", text = statementA, shown = statementB)` with
    `verdict` recording which side was true, plus `impaired`. No grimoire token.
  a **[Show privately]** button renders both lines as a `ShowCard.Message` with the true/false tags
    stripped, for a silent hand-over in a loud room.
death: none. A dead Savant does not visit (general rule) — card hidden with a caption, not removed.
ledger: `LedgerEntry(PRIVATE, sourceId = "savant", actorId, text, shown, verdict, impaired)`;
  `Memory.statementsOn(state, day, "savant")` drives the card, the composer's history strip and the
  game log (`D2 · Savant (Nia): TRUE "…" / FALSE "…"`).
tests:
  - A day-2 PRIVATE `savant` entry ⇒ "visited today" true for day 2, false again after two `advancePhase` calls.
  - Living Savant, DAY, no entry ⇒ a `savant` DAY_START note; dead, or already visited ⇒ none.
  - `("poisoner","Poisoned")` on the Savant ⇒ the composer offers "two true or two false" and accepts `BOTH_TRUE`.
  - On a known 8-seat grimoire, re-evaluating each generated candidate's predicate confirms every true one true
    and every false one false.
  - A `savant` grant on seat 0 ⇒ the day card targets seat 0; a day-4 entry reusing a day-2 TRUE text as its FALSE
    side is flagged as a contradiction.
open: the Savant page says **nothing** about the Vortox. "Two false" is the natural reading of the
  Vortox's own text, but present it as a strong default with an override, not a rule. The page is
  also silent on a dead Savant.

## seamstress — Seamstress · sv Townsfolk · P0:0 P1:5

today: the best-supported character in this group — the calculation (`InfoCalc.kt:356-365`),
alignment flips, misregistration caveats, the 2-seat picker, the YES/NO card and the impaired
inversion chip all work. What is missing is the *bookkeeping*: giving the answer and spending the
ability are unrelated gestures, a spent Seamstress keeps waking forever, the picker lets you choose
the Seamstress herself, and last night's two chips are still selected tonight.
data:
  - characters.json: `reminders: ["No ability"]` ok; set `spentLabel = "No ability"`. Fix the stray
    space in both night reminders: `"chose players , nod"` → `"chose players, nod"` (`:921`, `:922`).
  - night_and_jinxes.json: ok — first idx 56, other idx 81, no jinxes.
  - night_guide.json: rules-accurate already (including "the ability is still spent" when impaired);
    add the "not themselves" constraint and "dead players and Travellers are legal choices" to both
    instruction strings. `shows: []` → add YES / NO.
setup: `traveller.alignment.<seatId>` (shared with the Oracle) — the wiki explicitly allows the
  Seamstress to choose Travellers, and an unflagged Traveller reads good, producing a confidently
  wrong YES/NO.
identity: per-seat spend. A Philosopher/Alchemist-gained Seamstress must get their own use even if
  the real Seamstress spent hers — so the gate reads `AbilityGrant.spent` / a seat-scoped
  `Effect(SPENT)`, never a per-character flag.
night.first / night.other: identical.
  gate: `Gates.aliveHolder` **and** `Gates.notSpent("seamstress", "No ability")`. If dead or spent,
    **do not emit the row** — the How-to-Run says to remove their night token, and today the row
    reappears every night with a live info panel and still counts against the dawn guard
    (`GameShell.kt:147-161`). The engine's own playtest fixture documents the workaround three times.
  action: `ChoosePlayers("seamstress", "They shake their head, or point at two players other than
    themselves.", min = 2, max = 2, constraints = [ANY_LIVING_STATE, NOT_SELF], sort = ALIVE_FIRST,
    allowNone = true, noneLabel = "They passed — keep the ability",
    onResolve = [RecordChoice(), MarkSpent("seamstress")], onNone = [RecordChoice()])`.
    The holder's own chip is excluded entirely (today `NightScreen.kt:847` iterates `state.players`
    unfiltered and `InfoCalc` happily answers). Travellers with no alignment set are badged red with
    a jump-to-seat action. Selection must reset every dusk — `rememberSaveable(step.id)`
    (`NightScreen.kt:839`) is keyed only on the constant step id, so night 3 opens with night 2's
    picks and a stale YES/NO still rendered. Same widget, same bug, for the Fortune Teller and
    Chambermaid, where it is a live misinformation risk.
  effects: `Effect(kind = SPENT, sourceCharacterId = "seamstress", label = "No ability",
    until = FOREVER)` — placed by the **same button** that shows the answer, not by a separate tray
    chip that only exists while this row is expanded (`NightScreen.kt:263-279`).
  expiry: never. Correctly absent from both expiry tables today.
  info: `Answer.YesNo(isEvil(a) == isEvil(b))`. `detail` keeps naming both players **with their
    alignment** — that is what lets the ST catch a Traveller or a flipped player.
    obligation: impaired or Vortox ⇒ the inverted answer is primary **and the ability is still
    spent** (wiki + the app's own guide text); the panel reads `TRUE: YES · GIVE: NO (poisoned)`.
    alternatives: compute the answer under **both** misregistration readings. If they agree, say
    nothing — today a Recluse in the pair always produces a caveat even when the answer cannot
    change. If they differ, say "Answer depends on whether the Recluse registers as evil — you
    choose" and offer two buttons, each writing `recordRuling(sourceId = "misregister")`.
  show: existing full-screen YES/NO card.
  visibility: the Seamstress only.
day: none.
death: none. A dead Seamstress does not wake.
ledger: `recordChoice(state, "seamstress", holderId, targetIds = [a, b], impaired)` +
  `recordTold(state, holderId, "seamstress", shown = "NO", impaired)` so the log reads
  `N2 · Seamstress (Luz) asked Beau + Cleo — shown NO (true answer NO)`.
tests:
  - Given seat 3 is the Seamstress, `targets = [3, 7]` returns the guidance placeholder, not a
    confident YES/NO (**fails today**).
  - Given `("seamstress","No ability")` on seat 3, `NightPlan` emits no `seamstress` step
    (**fails today**); same for a dead Seamstress.
  - Given an unspent Seamstress, answering places exactly one SPENT effect and one ledger entry, and
    the effect survives two `advancePhase` calls.
  - Given a Traveller with no alignment set paired with a good Townsfolk, the result is "same
    alignment" **and** carries a Traveller-alignment warning (the warning is new).
  - Given the pair is `recluse` + a real Minion, the result reports that the answer differs by
    ruling rather than a single headline.
  - Given targets chosen on night 2, the night-3 step's selection is empty (**fails today**).
open: none.

## snakecharmer — Snake Charmer · sv Townsfolk · P0:3 P1:5

today: one of three bespoke resolvers and the core is right — the swap, the permanent poison (correctly in neither
expiry table), and the sheet re-deriving so the ex-charmer reaches the Demon's step later the same night. Three things
corrupt the grimoire: alignments are reset instead of swapped, character-bound tokens do not follow the swap, and
impairment is never checked.
data:
  - characters.json: `reminders: ["Poisoned"]` — the poison is **per victim and permanent**:
    `TokenRule("snakecharmer","Poisoned", expiry = NEVER, maxCopies = 5, exclusive = false,
     impairs = true)`. Today `snakeCharmerSwap` uses `placeExclusiveReminder` (`GameActions.kt:68-71`)
    which strips the token from every other seat, so a second swap (Pit-Hag-made charmer, Philosopher
    gain) silently **cures the first victim**.
  - night_and_jinxes.json: ok — first idx 31, other idx 19, both before every Demon's kill (which is
    what makes the same-night hand-over work); no jinxes among all 58.
  - night_guide.json: add the alignment-swap, impaired-does-nothing, self-choice and "no one else is
    told" clauses; replace the three `token:"pick"` shows with `token:"auto"` — after a swap the app
    knows both tokens exactly yet makes the ST search for the character it just assigned.
setup: none · identity: keys on `Identity.actingRoles`; `swapCharacters` must stop clearing
  `shownCharacterId` unconditionally (`GameActions.kt:66-67`) — the seat-sheet swap
  (`SeatSheet.kt:118-141`) preserves it, and the disagreement would erase a Lunatic's identity.
night.first / night.other: identical.
  gate: `Gates.aliveHolder`. **Do** emit for a poisoned holder (they wake, nothing happens — say so);
    do **not** emit for a dead one (today: prose, no tools).
  action: `ChoosePlayers("snakecharmer", "Who did <name> point at?", min = 1, max = 1,
    constraints = [ALIVE, SELF_ALLOWED], sort = SEAT_ORDER, onResolve = [RecordChoice()])`. **Seat order, not
    demon-first** — `NightScreen.kt:478` leaks the answer to a shoulder-surfing player, and the app decides the
    outcome so the ST needs no hint. The holder **must** be selectable: *"the Snake Charmer chooses themself, so
    nothing happens"* is an official example and the standard safe play; `NightScreen.kt:476-478` filters it out today.
  outcome, computed by the app: not a Demon (no misregistration invoked) **or** `Status.isImpaired(holder)` ⇒ record
    the choice, show `Nothing happens.` There is **no impairment check anywhere** in the `"snakecharmer"` branch,
    unlike `DemonKillPanel` — and it bites permanently after a successful swap, because the new charmer is poisoned
    forever yet their step keeps offering a second swap every night. Demon + working ability ⇒
    `SwapCharacters(SOURCE, TARGET)` + alignment swap + `PlaceToken("snakecharmer","Poisoned", TARGET, exclusive=false)`.
  **P0 — alignments are swapped, not reset.** `GameActions.kt:66-67` sets `alignmentFlipped = false`
    on both. The rule is "swaps characters **& alignments**", stated twice on the wiki (*"In the
    strange situation that the Snake Charmer is evil, or the Demon good, swap their alignments as
    appropriate"*; the Example *"…becomes the Fang Gu… **Both remain evil.**"*). Fix: each seat's
    **new** alignment is the **other**'s **old** one —
    `setAlignment(charmerId, evil = demonWasEvil)`, `setAlignment(demonId, evil = charmerWasEvil)`,
    with `setAlignment` setting `alignmentFlipped = (evil != naturalTeamIsEvil(characterId))`. The
    ordinary case reduces to `false` on both, so `ScriptParserTest.kt:128-142` still passes. Today the
    new charmer is forced **good**, corrupting Empath, Chef, Oracle, Seamstress, Investigator,
    Balloonist, Cult Leader and `WinCheck`.
  **P0 — character-bound tokens must follow the swap.** `swapCharacters` (`GameActions.kt:99-115`) moves only the two
    id fields, so the ex-Demon's seat keeps `("fanggu","Once")`, Pukka's poison, Lleech host markers, Zombuul "Died"
    and Al-Hadikhia tokens while the new Demon's seat has none. One rule in `GameActions`, inherited by the Barber,
    Pit-Hag and Philosopher: *tokens whose `sourceId` equals a swapped seat's outgoing `characterId` move to the seat
    that now holds that character; every other token stays.* Player-bound tokens must **not** move — Poisoner
    "Poisoned", Monk "Safe", Butler "Master", Witch "Cursed", Cerenovus "Mad", Exorcist "Chosen", Fortune Teller "Red
    herring" — and `("snakecharmer","Poisoned")` is the hard-coded exception, a player condition.
  deferred: the sheet re-derives so the new Demon's kill step is later tonight — **say so**. The new charmer then wakes
    every night permanently poisoned; their row must read `Poisoned by their own charm — nothing can happen.` rather
    than offering the swap again. Add: *"The Minions are NOT told about the swap, and the new Demon is NOT shown the
    Minions or new bluffs."*  info: none computed — the charmer learns only by elimination.
  show: a **staged wizard** replacing the three loose chips, one tap per stage, nothing searched — new Demon
    `YOU ARE → EVIL → YOU ARE → <Demon> token`; new charmer `YOU ARE → GOOD → YOU ARE → Snake Charmer token`.
    visibility: those two only.
day: none. death: re-evaluate `WinCheck` after a swap (Demon count unchanged, alignments moved); No
  Dashii `derivedPoison` recomputes around the new seat for free.
ledger: `recordChoice(state, "snakecharmer", holderId, targetIds = [chosen])` **including the
  no-effect cases** (self-choice, impaired choice), which feed the Mathematician window and the log.
  Today nothing is recorded when nothing happens.
tests:
  - Evil charmer (seat 0, flipped) + Fang Gu (seat 1) ⇒ seat 0 the Fang Gu **and evil**, seat 1 the charmer **and
    evil** and poisoned (**fails**: seat 1 comes back good); symmetrically for a Demon flipped good.
  - A poisoned charmer pointing at the Demon changes nothing; choosing themselves changes nothing but the CHOICE entry.
  - `("fanggu","Once")` on seat 1 ends on seat 0; `("monk","Safe")` and `("witch","Cursed")` stay on seat 1 (**fails**).
  - A second charmer's swap leaves the first victim still poisoned (**fails**: exclusive placement cures them).
  - A No Dashii that swaps ⇒ `derivedPoison` names the **new** No Dashii's Townsfolk neighbours. Dead charmer ⇒ no step.
open: multi-Demon scripts — Legion, Lil' Monsta (the Demon is a token held by a Minion, not a seat), Lord of Typhon,
  Kazali — are not addressed by the page; the panel must name which seats count as "the Demon" and let the ST rule.
  The Recluse "might register as evil & as a Minion or **Demon**", so pointing at one may trigger the swap at the ST's
  discretion — offer it as an explicit button. Ability text ("choose an **alive** player") and How-to-Run ("point at
  **any** player") disagree; the app follows the ability text, which is the correct precedence — note it in the guide.

## towncrier — Town Crier · sv Townsfolk · P0:0 P1:3

today: the calculation (`InfoCalc.kt:295-305`) is one of the better ones — right day, exiles excluded per the wiki's
second Example, Marionette counted, dead players already barred from nominating, and the impaired/Vortox inversion
chip works. Two structural gaps: the nominator's team is read from the **current** grimoire, and the two reminder
tokens the app instructs the ST to place are never placed and never read.
data:
  - characters.json: `reminders: ["Minions not nominated","Minion nominated"]` ok. Fix the prose
    drift at `:951` — "Place the 'Minion not nominated' marker" (singular) → "Minions not
    nominated", matching the labels at `:947-950` and the wiki's MINIONS NOT NOMINATED.
  - night_and_jinxes.json: ok — other idx 78, no first-night entry, no jinxes among all 58.
  - night_guide.json: add the exile rule verbatim (*"Exiles are never affected by character
    abilities"*), the "any nomination, seconded or not" clause, and the DID YOU NOMINATE TODAY?
    fallback. `shows: []` → add YES / NO and that phrase card (it exists at `ShowCards.kt:374` but
    only inside the global "All tokens" sheet, three taps deep).
setup: none
identity: plain; keys on `ActingRole.abilityId == "towncrier"`.
night.other:
  gate: `Gates.aliveHolder` — otherwise `StepGate.Skip("Town Crier is dead")`, auto-ticked. Never on
    night 1. Today a dead Town Crier renders "All holders are dead — usually skip" **plus** a
    confident live YES/NO panel; the playtest fixture records `informationShown = "SKIPPED (dead)"`
    three times.  action: `ShowInfo("towncrier")`, no targets.
  effects on completion: `RemoveToken("towncrier","Minion nominated", SOURCE)`.
  info: from `nominations.filter { day == cycle - 1 && !isExile }`, using the **snapshot** of the
    nominator's registration taken at record time — `Nomination.nominatorCharacterId` /
    `nominatorTeam` (group note 3). Without it a Pit-Hag (other-night 25, long before 78) turning the
    nominating Minion into a Townsfolk, a Scarlet Woman promoted to Demon after the Demon was
    executed that same day, or a Snake Charmer swap all silently flip the answer.
    Headline `YES — Cleo (Witch) nominated on day 2` / `NO — no Minion nominated on day 2`; `detail`
    names the Minion nominators **and the number of nominations considered**, so a missing record is
    visible.
    alternatives: compute the answer twice — with each ambiguous nominator (Spy, Recluse) registering as a Minion and
    without. **If they agree, show nothing**: today `misregistrations(...)` flags every Spy or Recluse who nominated
    even when a real Minion also did and the answer is YES either way. If they differ, show "Depends on whether Ivy
    registers as a Minion" with two buttons, each writing `recordRuling(sourceId = "misregister")` onto the
    `Nomination` so the ruling survives undo and matches the one made for the Empath two rows earlier.
    fallback: a step chip **[Ask the Minions]** listing the living Minions and showing `DID YOU NOMINATE TODAY?` one
    seat at a time, with a per-Minion yes/no toggle that overrides tonight's derived answer and is stored — the wiki's
    own recovery path, on the step where it is needed rather than in a global sheet.
  obligation: MUST_LIE ⇒ the inverted card is primary; label the answer `TRUE: YES · GIVE: NO`.
  visibility: the Town Crier only; the phrase card is shown to Minions in the fallback flow.
day: tokens (make the app's own instructions true) —
  `TokenRule("towncrier","Minion nominated", expiry = DAWN, resetTo = "Minions not nominated",
   mutexGroup = "towncrier.nom")`: at dawn place `Minions not nominated`; on the first recorded
  non-exile nomination whose snapshotted nominator registers as a Minion, replace it. The tokens stay
  **display** state; the answer stays derived from the nomination records, so undo can never
  desynchronise them. Nomination-time: a `NominationTrigger(kind = WARN, sourceId = "towncrier")` in
  `checkNomination` — *"Town Crier: this nomination means tonight's answer is YES"*, and for a
  Spy/Recluse nominator *"Town Crier: decide now whether Ivy registers as a Minion."* Today the ST
  never notices during the day that tonight's answer just changed. Briefing:
  `DayBriefing.Note(DAY_START, INFO, "towncrier", "Town Crier in play — note who nominates today.")`
death: none.
ledger: the `Nomination` snapshots + any misregistration RULING + `recordTold` on reveal.
tests:
  - A Minion + a Townsfolk nomination on day 2 ⇒ night 3 headlines YES and the detail names the Minion; night 1 never
    emits a step; a Minion nominating a Traveller with `isExile = true` ⇒ NO (wiki example 2).
  - A Minion nominated on day 2, that seat reassigned to `chef` on night 3 ⇒ still YES (**fails**); same for a Scarlet
    Woman promoted to Demon that day.
  - The `marionette` seat nominating ⇒ YES (guards future `actingRoles` refactors).
  - Recluse **and** a real Minion nominated ⇒ no Recluse caveat; Recluse alone ⇒ both readings and a required ruling (**fails**).
  - Recording a Minion nomination leaves `("towncrier","Minion nominated")` only; the next dawn reverses it (**fails**:
    neither is ever placed).
open: does a **withdrawn** nomination count? `NominationResult.WITHDRAWN` exists (`GameState.kt:59`) with no UI to set
  it, and `InfoCalc.townCrier` counts every non-exile record. The wiki's "any Minion nomination" suggests yes — the app
  should state its choice on screen rather than decide silently. Minor: `relevantDay` returns `state.cycle` during DAY,
  so opening the row from the Night tab mid-day shows today's still-accumulating answer.
