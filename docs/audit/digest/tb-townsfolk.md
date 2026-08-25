# tb-townsfolk digest — 13 Trouble Brewing Townsfolk · P0:25 P1:55

## Group notes

- **Six of thirteen never wake** (mayor, slayer, soldier, virgin + monk/ravenkeeper/undertaker's day halves). Four have **no `night_guide.json` entry at all** (mayor, slayer, soldier, virgin) because the guide is keyed off night steps. `NightGuideEntry` (`NightGuide.kt:36-40`) needs `day:` and `passive:` sections, or these characters stay undocumented inside the app forever.
- **One shared `InfoResult` change unblocks 8 cards.** Add `numeric: Int?`, `numericRange: IntRange?`, `boolean: Boolean?`, `candidates: List<Candidate>`, `falseAlternatives`, `impairment: NONE|MAY_BE_FALSE|MUST_BE_FALSE`. `NightScreen.kt:886-930` currently sniffs English (`takeWhile{isDigit}`, `startsWith("NO")` — case-sensitive, so the Librarian's own "No Outsider…" zero is unshowable) and hard-codes false values `0..4` (P0 for the Empath: a poisoned Empath is offered a "3").
- **Misregistration is prose in all 8 info cards and a decision in none.** One mechanism for all: a `LedgerEntry(kind=RULING, sourceId="misregister", playerId, characterIds)` written at the first ruling and re-surfaced by `Memory.ruling(...)` on every later step touching that seat. Four character files independently invented `PlacedReminder("spy","Registered: <X>")` — **reject the token**: that pair does not exist in `characters.json` and night-engine §4 requires every `TokenRule` pair to exist there. Use `Effect(kind=REGISTERS_AS)` + the RULING entry.
- **`DayEntry` (day-engine §A) vs `LedgerEntry` (records-and-memory §A) are two unified record types for the same job.** Every card below writes `LedgerEntry`; the lead must collapse one into the other before implementation (records-and-memory explicitly claims day statements; day-engine's `dayLog` also does).
- **Conflict — night-1 info tokens.** `washerwoman.md` puts `washerwoman:Townsfolk|Wrong` in `EXPIRES_AT_DAWN`; `librarian.md`/`investigator.md` want theirs kept as dimmed "spent" tokens cleared by an explicit **Clear night-1 info tokens** action. Pick one policy for all three (recommend: keep, dim, clear-on-demand — the tokens are the ST's only record until the ledger lands).
- **Conflict — does resurrection restore a spent once-per-game?** Glossary + night-engine §2 + status-model §6 say **yes** (`resurrect` drops `SPENT`); `slayer.md` says the token survives. Affects `slayer:No ability` and `virgin:No ability` (the Virgin's is arguably a historical fact, not a spend). Lead must rule.
- **Conflict — Traveller counting in `WinCheck`.** `mayor.md` wants `WinCheck.kt:19` to count Travellers everywhere; day-engine splits it (`aliveCountWithTravellers` for the Mayor, `aliveCountResidents` for evil-wins-at-2). `mayor.md` test 11 asserts the *opposite* of the day-engine ruling. Lead must rule; the Mayor half is not in dispute.
- **Conflict — Ravenkeeper's wake mechanism.** night-engine §1 treats it as a `StepGate` on the existing other-night row; status-model §5 queues `Prompt(at=TONIGHT, kind=CHOOSE_PLAYER)`. Same for the Undertaker (gate) vs an execution consequence. Prefer the gate; the Prompt queue is only needed for the night-1 insertion case (Kazali/Typhon).
- **Five dead-holder rows still block the Dawn guard** (empath, fortuneteller, monk, ravenkeeper, undertaker) — `NightOrder.kt:143-145` has no alive filter and `GameShell.kt:153-160` demands a tick. `StepGate.Skip` + auto-tick fixes all five; the Ravenkeeper is the **inverse** case (its "All holders are dead — usually skip" red text at `NightScreen.kt:751-757` fires on the one night it must act).
- **Data gaps verified against the files, not the audits:** 5 jinxes missing (`monk`↔leviathan, `monk`↔riot, `soldier`↔leviathan, `soldier`↔riot, `mayor`↔riot) and 2 present with **invented text** (`leviathan`↔`ravenkeeper`, `riot`↔`ravenkeeper` both describe an execution-wake that is not the official jinx). `night_guide.json` `undertaker.other` states the **opposite of the rule** ("even if they did not die from it").
- **Night-order positions are all correct** (washerwoman 46, librarian 47, investigator 48, chef 49, empath 50/73, fortuneteller 51/74, monk 20, ravenkeeper 72, undertaker 75; mayor/slayer/soldier/virgin absent). No order data changes in this group.
- **The nomination interceptor (`checkNomination`/`NominationTrigger`) and the execution funnel (`GameActions.execute` + `ExecutionRecord`) are prerequisites for virgin, undertaker, mayor and slayer.** Four of this group's five P0-heavy cards die without them.

---

## chef — Chef · tb townsfolk · P0:0 P1:3
today: night-1 pair count is correct (wraps the circle, counts overlaps, includes evil Travellers, respects `alignmentFlipped`) but misregistration is an unscoped warning, the false-info chips are a literal `0..4`, and the number shown is never recorded.
data:
  - characters.json: ok — `reminders: []`, `firstNightReminder` current, `setup:false`
  - night_and_jinxes.json: ok — firstNight 49, absent from otherNight, no jinxes (correct)
  - night_guide.json: ok — prose already states the "three in a row = 2 pairs" and drunk/poisoned rules; optionally add `shows` number cards 0-3
setup: none
identity: plain
night.first:
  gate: `Fire` while a `chef` seat exists. **Do not add an alive gate** (chef.md: a Pit-Hag/Amnesiac Chef created mid-game must still be runnable); a dead holder yields `Skip("dead")` only via the generic rule, and the generic engine must be able to emit this step on a later night for a newly-created Chef (night-engine §1 "newly-created characters").
  action: `ShowInfo("chef", targetsNeeded = 0)`
  effects: none — no tokens, no status
  deferred: none
  info: numeric, `min = 0`, `max` = pair count when **every** misregistering seat registers evil (this bounds the false-info chips, replacing `NightScreen.kt:914-921`'s `0..4`). `alternatives` = one per *relevant* subset of `recluse`/`spy` seats — relevant = adjacent to a seat evil under some considered registration — each carrying its resulting count, the pairs it adds/removes, and a reason ("2 — if Cara (Recluse) registers evil: adds Cara+Dee"). De-dupe by value, cap ~4, else fall back to a min–max range. `impairment = MUST_BE_FALSE` under a living Vortox, `MAY_BE_FALSE` for drunk/poisoned/the Drunk/Marionette/`No ability`/No-Dashii. Fix the `size == 2` double count (`InfoCalc.kt:191-199` counts edge 0→1 and 1→0).
  show: existing `ShowCard.NumberCard`; keep the one-tap primary chip
  visibility: none
day: none. Optional `LedgerEntry(kind=STATEMENT, sourceId="claim")` for the Chef's public number.
death: none
ledger: `recordTold(chefId, "chef", shown = "<number actually flashed>", impaired)` — the flashed value, not the true one (wire it to the `ShowCard` tap, records-and-memory §D4). Plus `recordRuling("misregister", …)` for the registration chosen, re-surfaced on empath/fortuneteller/undertaker/ravenkeeper steps.
tests:
  - Given seats 0,1,2 evil in a 7-circle · When chef computes · Then `trueValue == 2` and both pairs are named.
  - Given exactly 2 seats, both evil · Then `trueValue == 1` (today: 2).
  - Given `imp`@0 + `recluse`@1 · Then `trueValue == 0`, `max == 1`, alternatives contain `(1, "…Recluse…")`.
  - Given a poisoned Chef with true 1 and max 2 · Then the offered false values are exactly `{0, 2}`.
  - Given a `spy` with two good non-misregistering neighbours · Then no alternative is produced and any caveat says the registration cannot change the number.
open: none

---

## empath — Empath · tb townsfolk · P0:1 P1:3
today: `aliveNeighbours` (`InfoCalc.kt:168-182`) is correct — skips the dead, wraps, `distinctBy` for the 2-alive case, counts Travellers, honours `alignmentFlipped` — and the caveats are correctly scoped to the neighbours. The P0 is the UI: a poisoned Empath is offered false values of **3 and 4**, which the character can never legally see.
data:
  - characters.json: ok — `reminders: []`, both night reminders current
  - night_and_jinxes.json: ok — firstNight 50, otherNight 73, no jinxes
  - night_guide.json: ok (first/other identical and correct); optionally add `shows` 0/1/2
setup: none
identity: plain
night.first / night.other:
  gate: `Gates.aliveHolder` → `Skip("dead — no ability")`, auto-ticked so it never blocks Dawn
  action: `ShowInfo("empath", targetsNeeded = 0)`
  effects: none
  deferred: none
  info: numeric, `min = 0`, `max = neighbours.size` — **2 normally, 1 when only one other seat is alive**. This single line fixes the P0 and the undocumented 2-alive case together. `alternatives` = cross-product over neighbouring `recluse` (may register evil) and `spy` (may register good), de-duped by value with a reason each. `impairment`: `MUST_BE_FALSE` under a living Vortox; `MAY_BE_FALSE` for drunk/poisoned/the Drunk/**Marionette**/`No ability`/No-Dashii — note `NightScreen.kt:904-906`'s substring test misses the last two, so those two cases get *no* false-info row today. Also fix `commonCaveats` computing `holderTeam` from the Drunk's real character (`InfoCalc.kt:160-164`), which silently suppresses the Vortox line for a Drunk-shown-as-Empath.
  show: `NumberCard`; under `MUST_BE_FALSE` demote the true chip to a text link and head the row "Vortox — you MUST show a false number."
  visibility: none
day: `DayBriefing.Note(DAY_START, INFO, "empath")` when a night death re-pointed the Empath: *"<Empath>'s neighbours are now <X> and <Y>."*
death: none
ledger: `recordTold(empathId, "empath", shown = "<flashed number>", impaired)` each night — this is the series the reveal flow and the Mathematician both need.
tests:
  - Given a poisoned Empath, true value 1 · Then offered false values are exactly `{0, 2}`, never 3 or 4.
  - Given only the Empath and one other seat alive · Then `max == 1`.
  - Given an alive `recluse` neighbour and no other evil neighbour · Then `trueValue == 0` and alternatives contain `(1, "…Recluse…")`.
  - Given the seat holds `marionette` shown as Empath (and separately a `No ability` token) · Then `impairment == MAY_BE_FALSE` in both cases.
  - Given a dead Empath on night 4 · Then the Dawn guard does not list the step as unfinished.
open: the **2-alive ruling** is not stated on the wiki (both "neighbours" resolve to the same seat; the app counts them once, max 1). Confirm before locking the test.

---

## fortuneteller — Fortune Teller · tb townsfolk · P0:1 P1:5
today: 2-target picker over all seats (dead and self legal — correct), YES/NO computed from `Team.DEMON` + any `Red herring` label, one-tap full-screen answer, red herring never expires, survives character change. P0: a chosen **Recluse** yields a flat NO with an obvious "Show answer" button; nothing records the pick; the herring prompt is SETUP-only and permanently silenceable.
data:
  - characters.json: ok — `reminders: ["Red herring"]`, ability text current
  - night_and_jinxes.json: ok — firstNight 51, otherNight 74, no jinxes (correct)
  - night_guide.json: `fortuneteller.first.instructions` — add *"(dead players and themselves are allowed)"* to match `.other`; move the red-herring sentence into a setup-stage string
setup: `SetupTask("fortuneteller.redherring")` · kind = PLACE_TOKEN · prompt *"Pick the good player who registers as the Demon to the Fortune Teller. This never changes, and it may be the Fortune Teller themselves."* · candidates = seats with a **non-null `characterId`** whose `isEvil` is false (today `GameShell.kt:360` offers unassigned seats, because `characterId == null` ⇒ `team == null` ⇒ not evil), FT's own seat first and labelled "(the Fortune Teller — legal and often good play)" · effect `placeExclusiveReminder` (today `addReminder`, `GameShell.kt:364-367`, so a tray placement can create two) · validation: **fires on any state where a `fortuneteller` seat exists and no `fortuneteller:Red herring` is placed — not just `Phase.SETUP`** (`GameShell.kt:350`), re-armed at every phase advance ("Later" must be a soft flag, not the one-way `herringPromptDone`), plus a new issue *"the red herring is on an evil player"* checked at **every** phase boundary.
identity: plain
night.first / night.other:
  gate: `Gates.aliveHolder` → `Skip("dead")`, auto-ticked
  action: `ChoosePlayers("fortuneteller", "Who did the Fortune Teller point at? (2 players — dead and self are legal)", min = 2, max = 2, constraints = [ANY_LIVING_STATE, SELF_ALLOWED], sort = ALIVE_FIRST, allowNone = false)` — dead seats must render **enabled**, the FT's own chip labelled "(self — legal)"
  effects: `RecordChoice()` only. No tokens.
  deferred: none
  info: boolean. `trueAnswer` = any chosen seat is `Team.DEMON` **or** carries `fortuneteller:Red herring`. `optionalYes` = one entry per chosen `recluse` seat ("…you may rule they register as the Demon tonight, making this a YES"). **Emit no Spy caveat on this step** — the Spy can never register as a Demon, and today's generic line reads like an offer. When the answer is YES because of a Demon, append *"(the red herring is <name>)"* for context.
  show: existing YES/NO cards. When `optionalYes` is non-empty and `trueAnswer` is false, show **both** buttons side by side — "Show NO (true)" / "Show YES (Recluse registers as the Demon)" — and record which was used.
  visibility: none. The Spy sees the `Red herring` token in the grimoire as normal.
  step header (always visible): **"Red herring: <name>"** (or red *"No red herring assigned — tap to set one"*) and **"Last night <FT> asked about <X> and <Y> and heard <answer>"** from `Memory.lastChoice`.
day: none
death: none
ledger: `recordChoice("fortuneteller", ftId, [a, b])` + `recordTold(ftId, "fortuneteller", shown = "YES"|"NO", impaired)` + `recordRuling("misregister", …)` for any Recluse ruling. This also deletes `NightScreen.kt:839`'s `rememberSaveable(step.id)`, which is keyed on the character id and not the cycle, so **last night's two seats can be restored on top of tonight's step** and answered confidently.
tests:
  - Given `fortuneteller:Red herring` on seat 4 · When NIGHT→DAY→NIGHT, seat 4 is killed, and seat 4's character is changed · Then the token survives and the calc still answers YES on seat 4.
  - Given a `recluse`@5 and targets `[5, 2]` · Then `trueAnswer == false` **and** `optionalYes` names seat 5.
  - Given a `spy`@6 and targets `[6, 2]` · Then `optionalYes` is empty and no Spy caveat is produced.
  - Given `Phase.NIGHT`, cycle 3, no `fortuneteller` in play · When seat 3 becomes the Fortune Teller · Then validation reports "needs a red herring" (today the prompt is SETUP-only).
  - Given the token on the Imp's seat during `Phase.NIGHT` cycle 2 · Then validation reports "the red herring must be a good player".
open: none

---

## washerwoman — Washerwoman · tb townsfolk · P0:0 P1:4
today: `startKnowing(TOWNSFOLK)` lists every Townsfolk in play and tells the ST to improvise; the Drunk is correctly excluded (it is `Team.OUTSIDER`, which *is* the "the person you see is not the Drunk" rule by construction) and Travellers are excluded. Nothing is chosen, nothing is recorded, no setup prompt, no false pair when impaired.
data:
  - characters.json: ok — `reminders: ["Townsfolk", "Wrong"]`, text current
  - night_and_jinxes.json: ok — firstNight 46, absent from otherNight, no jinxes
  - night_guide.json: extend `washerwoman.first.instructions` — tokens are placed **while preparing the first night**; the decoy may be **any** other player, including the Demon (wiki examples 2 and 3); the **Spy may be shown as the Townsfolk**; the Drunk never can; remove both tokens when convenient
setup: `SetupTask("washerwoman.pair")` · kind = CHOOSE_CHARACTER_AND_TWO_PLAYERS · prompt *"Mark one Townsfolk (the character you'll show) and one other player (the decoy)."* · candidates: TOWNSFOLK = seats whose real `characterId` is a Townsfolk, **excluding the Washerwoman by default** with an "include me" override (the wiki does not settle self-inclusion), automatically excluding the Drunk, **plus every `spy` seat** tagged "Spy — may register as a Townsfolk" with a sub-pick of which Townsfolk token to show; WRONG = any other seat including Minions and the Demon, excluding the TOWNSFOLK seat and (by default) the Washerwoman, warn (do not block) if the decoy is itself the character shown · effects `placeExclusiveReminder("washerwoman","Townsfolk")` + `…("washerwoman","Wrong")` · validation: **soft** `validateSetupState` issue *"Washerwoman: choose the Townsfolk and the decoy"*
identity: plain (the **Recluse can never be the Townsfolk** — it registers only as evil/Minion/Demon; it may be the decoy)
night.first:
  gate: `Fire` on night 1 with an alive holder; **never emitted on other nights** (already correct — absent from `otherNight`). A Philosopher/Boffin-granted Washerwoman must be able to re-invoke the prep flow, so it cannot be setup-only.
  action: `ShowInfo("washerwoman")` replaying the two placed tokens; if they are missing, fall back to an in-step **ordered** 2-slot picker (`targetsNeeded → 2`, slot 1 = the Townsfolk, slot 2 = the decoy — not an unordered pair)
  effects: none beyond the setup tokens
  deferred: none
  info: candidates `TRUE` = real Townsfolk seats; `MISREGISTERED` = each `spy` seat × each Townsfolk on the script; `FALSE` (surfaced when impaired) = a real in-play Townsfolk pointed at two players neither of whom is that character, then a **not-in-play** Townsfolk preferring the Demon's bluffs (so the lie is consistent with what the Demon was told), then an in-play Townsfolk paired with the holder. Point-order should be randomised in the display so the ST does not always point at the real seat first. Scope `misregistrations(...)` to the **two chosen seats**, not `ctx.players` (`InfoCalc.kt:419`).
  show: existing `GuideShowDialog` `token:"pick"` card pre-selected to the chosen character, text "One of the 2 players I point to is this character"
  visibility: a Spy chosen as the Townsfolk **will see the tokens in the grimoire** during its own step — say so in the prompt
day: `DayBriefing` DAY_START line *"You told <Washerwoman>: one of <A>/<B> is the <Character>"* on day 1, and a place to record the Washerwoman's public claim (`LedgerEntry(kind=STATEMENT, sourceId="claim")`) so claim-vs-truth is checkable.
death: none
ledger: `recordTold(wwId, "washerwoman", shown = "<Character> · <A>/<B>", impaired)` + `recordRuling("misregister", spySeat, characterIds = [shownCharacter])`.
tests:
  - Given a Drunk shown as the Chef · Then the Drunk seat is never offered as the TOWNSFOLK.
  - Given a Spy in play · Then it appears as a legal Townsfolk option and choosing it writes a `misregister` RULING.
  - Given the wiki's Imp+Virgin example · Then selecting the Imp as WRONG is accepted.
  - Given a Washerwoman in play with no `washerwoman` tokens · When `validateSetupState` runs · Then a soft issue asks for the pair.
  - Given a Spy and a Recluse in play, neither in the chosen pair · Then the result carries no Spy/Recluse caveat.
open: whether the Washerwoman may be one of the two players is **not** settled by the wiki — default to excluding self, allow an override, do not hard-block.

---

## librarian — Librarian · tb townsfolk · P0:0 P1:6
today: `startKnowing(OUTSIDER)` on the real `characterId`, so a seat holding `drunk` is correctly listed as an Outsider named **Drunk** (the wiki's own example). But the zero answer — the one answer this character is guaranteed to need — **cannot be shown from the step**: `leadingNumber` is null and `isNo` is false because `startsWith("NO")` is case-sensitive against "No Outsider in play…".
data:
  - characters.json: ok — `reminders: ["Outsider", "Wrong"]`, ability text includes the zero clause
  - night_and_jinxes.json: ok — firstNight 47, absent from otherNight, no jinxes
  - night_guide.json: split the setup placement out of `librarian.first.instructions`; add a `shows` entry `{label:"Show 0", kind:"message", text:"0"}` (or rely on the new `numeric` field)
setup: `SetupTask("librarian.pair")` · prompt *"Pick the Outsider the Librarian learns and the two players you will point at — or choose to show a 0."* · candidate sections: **In play** (real Outsiders, the Drunk shown as **Drunk**), **Misregistration** (each `spy` seat × each Outsider on the script — *"Ana (Spy) registering as the Butler"*), **Show 0** (always present; sub-labelled *"only legal if you rule the Recluse registers as a Minion, or the Librarian is impaired"* when an Outsider does exist) · decoy = any other seat, alive-good-non-Outsider first, Librarian's own seat last with a hint, warn if the decoy is also an Outsider, suppressed entirely for the 0 case · effects `placeExclusiveReminder("librarian","Outsider")` + `…("librarian","Wrong")`; the 0 case places no tokens and records the choice · validation: **soft** issue *"Librarian: choose the Outsider and the two players to point at (or a 0)"*
identity: the **Drunk is a legal Outsider to show**, and the token shown is `Drunk`, never the Townsfolk they believe they are.
night.first:
  gate: `Fire` night 1 with an alive holder; never on other nights
  action: `ShowInfo("librarian")` replaying the setup pairing
  effects: none
  deferred: none
  info: candidates `TRUE` = real Outsider seats (Drunk included); `MISREGISTERED` = each `spy` × each Outsider on the script; `FALSE` (impaired only) = Outsiders **not** in play, a suggested wrong pair, and a false **0**. `zeroAllowed = true`, `zeroIsTrue` = no Outsider seat exists; a 0 while `zeroIsTrue == false` must carry its justification (Recluse-registers-as-a-Minion ruling, or impairment) and be recorded as such. Scope the caveats to two Librarian-specific statements instead of one generic line per Spy/Recluse on the board.
  show: **"Show 0"** must be a first-class chip on the step opening `ShowCard.NumberCard(0)` — true, ruled, or lied. Prepared `ShowCard.CharacterCard("One of the 2 players I point to is this character", "<id>")` for the pairing, plus a "Change the pairing" link back into the setup picker.
  visibility: nothing beyond the ordinary grimoire view a Spy gets
day: optional claim recording
death: none
ledger: `recordTold(libId, "librarian", shown = "Butler" | "0", impaired)` with `targetIds = [trueSeat, decoySeat]` (empty for a 0) + any Spy/Recluse `misregister` RULING.
tests:
  - Given no Outsider in play · Then the result carries `numeric == 0` so a **Show 0** chip renders.
  - Given a seat `characterId = "drunk"`, `shownCharacterId = "monk"` · Then it is a TRUE candidate shown as **Drunk**, never Monk.
  - Given a `spy`@6 on a script with Butler/Drunk/Recluse/Saint · Then `candidates` contains a MISREGISTERED entry for each.
  - Given `recluse` as the sole Outsider · Then `zeroAllowed`, `!zeroIsTrue`, and the zero option carries the Recluse justification.
  - Given a poisoned Librarian with `butler` and `drunk` in play · Then the false candidates include `recluse`, `saint` and a false 0, and exclude `butler`/`drunk`.
open: whether the Librarian may be one of the two players is unstated (they can only ever be the decoy — they are Townsfolk).

---

## investigator — Investigator · tb townsfolk · P0:0 P1:5
today: identical shared `startKnowing` path, `Team.MINION`. The Recluse — whose registration as *any* Minion, **including one not in play**, is a canonical wiki example — never appears in the candidate list at all; the only signal is a generic caveat. Impaired holders get an **empty** false-info row.
data:
  - characters.json: ok — `reminders: ["Minion", "Wrong"]`, text current
  - night_and_jinxes.json: ok — firstNight 48, absent from otherNight, no jinxes
  - night_guide.json: move the "during setup" placement into a setup-stage string; keep the night text to *"Point at the two marked players and show the marked character token."*
setup: `SetupTask("investigator.pair")` · prompt *"Which Minion do they learn, and who are the two players?"* · character picker: Minions actually in play, plus a separated **Misregistration** section listing each `recluse` seat — *"Cara (Recluse) may register as any Minion — pick which"* — opening the full Minion list **including Minions not in play** · decoy = second pick over all other seats, alive good non-Minion first, the Investigator's own seat last with a hint, warn (do not block) if the decoy is also a Minion · effects `placeExclusiveReminder("investigator","Minion")` + `…("investigator","Wrong")` · validation: **soft** issue *"Investigator: choose the Minion and the two players to point at"*
identity: the Spy **is** a Minion, so it is a `TRUE` candidate, with a note that the ST may instead choose to hide it. It is never a "may register as good" option here — today's generic caveat reads as an invitation and is the wrong direction for this ability.
night.first:
  gate: `Fire` night 1 with an alive holder; never on other nights
  action: `ShowInfo("investigator")` replaying the setup pairing
  effects: none
  deferred: none
  info: `zeroAllowed = false` — the Investigator has **no** zero clause, so the UI must never offer a "show 0" here. `TRUE` = real Minion seats; `MISREGISTERED` = each `recluse` seat × each Minion on the script (in play or not); `FALSE` (impaired only) = Minions **not** in play, plus a suggested pair of two non-Minion players. The "no Minion in play" branch is reachable only off-script/via Fabled and must offer a fallback rather than a dead end.
  show: primary **"Show Poisoner · point at Cara and Fred"** opening the prepared `ShowCard.CharacterCard`, plus a "Change the pairing" link
  visibility: the Spy sees the `Minion`/`Wrong` tokens in the grimoire — correct and part of the game
day: optional claim recording
death: none
ledger: `recordTold(invId, "investigator", shown = "Poisoner", targetIds = [trueSeat, decoySeat], impaired)` + the Recluse `misregister` RULING, which every later Chef/Empath/Fortune-Teller/Undertaker step must echo.
tests:
  - Given `poisoner`@1 and `baron`@6 · Then the TRUE candidates are exactly those two seats.
  - Given a `recluse`@5 on a script with Poisoner/Spy/Baron/Scarlet Woman · Then `candidates` contains a MISREGISTERED entry for seat 5 for **each** of the four, including ones not in play.
  - Given any board · Then `zeroAllowed == false` and no "0" is suggested.
  - Given a poisoned Investigator with `poisoner`/`baron` in play · Then the false candidates include `spy` and `scarletwoman` and exclude the two in play.
  - Given the Investigator was shown the Recluse as the Baron on night 1 · Then later steps involving that seat surface *"you already ruled this Recluse registers as the Baron"*.
open: whether the Investigator may be one of the two players is unstated — sort self last with a hint, do not forbid.

---

## monk — Monk · tb townsfolk · P0:3 P1:3
today: the token tray places `monk:Safe` exclusively and `EXPIRES_AT_DAWN` sweeps it — that part works. Everything downstream is prose: the demon panel prints a red "protected" line and still offers an **enabled** "<name> dies"; a **poisoned Monk produces the same protected verdict**; a Monk-protected Imp self-kill jumps straight to the star-pass heir chips.
data:
  - characters.json: `monk.otherNightReminder` → drop the stale first sentence (the app already removed last night's token at dawn, so the row's most prominent line contradicts the app) → *"The Monk points to a player other than themselves. Mark that player 'Safe'."*
  - night_and_jinxes.json: **add two missing jinxes** — `{"id1":"monk","id2":"leviathan","reason":"If the Leviathan nominates and executes the Monk-protected player, good wins."}` and `{"id1":"monk","id2":"riot","reason":"If Riot nominates and executes the Monk-protected player, good wins."}`
  - night_guide.json: ok — prose already covers "not themselves" and the drunk/poisoned rule; `shows: []` is correct (the Monk is shown nothing)
setup: none
identity: plain
night.other:
  gate: `Gates.aliveHolder` → `Skip("dead")`, auto-ticked (today the dead-Monk row blocks the Dawn guard)
  action: `ChoosePlayers("monk", "Who did the Monk point at?", min = 1, max = 1, constraints = [NOT_SELF, ANY_LIVING_STATE], sort = ALIVE_FIRST)` — the self chip **disabled** with " — can't protect self"; dead seats allowed but sorted last and dimmed (protecting a corpse is legal and pointless; the Monk may be bluffing). Today there is no picker at all: the tray's seat list is unfiltered `state.players`.
  effects: `PlaceToken("monk", "Safe", TARGET, maxCopies = 1, exclusive = true)` + `Effect(kind = SAFE_FROM_DEMON, targetId = target, sourceCharacterId = "monk", sourcePlayerId = monkId, until = DAWN, endsWithSource = true, label = "Safe")` + `RecordChoice()`
  impaired source: **still place the token** (the grimoire must look normal to a Spy) — the `Effect` is inert automatically because `abilityWorks(monk)` is false, and the token renders with an "inert" badge for the ST
  deferred: none beyond tonight
  expiry: `TokenRule("monk","Safe", Expiry.DAWN, endsWhenSourceLosesAbility = true, protects = true)` — unchanged, but `Briefings.dawn` must run **before** `clearEphemeral` so "saved from death" is still derivable
  info: none — the Monk learns nothing
  show: none
  visibility: nothing to the Demon or Minions
day: none
death: `killOutcome` step 7 — `SAFE_FROM_DEMON` + `kind == DEMON_ABILITY` → `Blocked(announce = "Nobody dies — the Monk protected <name>.")`. Three consequences the character file calls out and the ordering already gives for free: (a) an **Imp self-kill while protected does not star-pass** — do not render heir chips at all; (b) a protected **Mayor** produces "nobody dies", *not* a redirect (block at step 7 precedes the Mayor `Choice` at step 11); (c) protection is Demon-only — execution, Slayer, Godfather/Assassin and ST deaths still kill. `demonHarmBlocked` extends it to non-kill Demon harm (No Dashii/Pukka poison) for other scripts. Delete the label-only `deathNotes` branch at `StatusEffects.kt:64-70`, which fires on any source's `Safe` label.
ledger: `recordChoice("monk", monkId, [targetId], impaired)`; `Memory.lastChoice` then powers the courtesy line "last night: Ada". Dawn: `publicScript` "Announce that nobody died. Do not explain why." plus a private `savedFromDeath` line.
tests:
  - Given `monk:Safe` on the Mayor and a sober Monk · When the Demon kills the Mayor · Then no death, no bounce candidates, resolver returns "nobody dies".
  - Given the Monk is poisoned · When the Demon kills the Safe player · Then they die and the protection reports `effective = false` naming the Monk.
  - Given a Monk-protected Imp with a Scarlet Woman alive · When the Imp targets itself · Then the Imp lives, no star pass, no `DeathEvent`.
  - Given `Safe` on seat 3 · When seat 3 is executed · Then seat 3 dies.
  - Given `activeJinxes(["monk","leviathan"])` and `(["monk","riot"])` · Then each returns the official jinx (both fail today — absent from the data).
open: none

---

## soldier — Soldier · tb townsfolk · P0:3 P1:4
today: the **entire** implementation is `StatusEffects.kt:74` — `notes += "The Soldier is safe from the Demon."` — added unconditionally, with no impairment check, no cause check and no alive check. Three P0s follow directly: a poisoned Soldier is reported protected (the wiki's own second example), tapping **Executed** raises a "might be protected" dialog inviting the ST to save an executed Soldier, and a Soldier neighbouring a No Dashii is wrongly poisoned by `derivedPoison` — which then cascades into false info for every other ability that seat has.
data:
  - characters.json: ok — `reminders: []`, no night reminders, `setup:false`
  - night_and_jinxes.json: **add two missing jinxes** — `{"id1":"leviathan","id2":"soldier","reason":"If the Leviathan nominates and executes the Soldier, good wins."}` and `{"id1":"riot","id2":"soldier","reason":"If Riot nominates and executes the Soldier, good wins."}` (game-**ending** jinxes; omitting them can lose a game)
  - night_guide.json: no entry needed (never wakes) — but the passive/day guidance needs a home
setup: none
identity: `StandingRule("soldier")` → `Effect(kind = SAFE_FROM_DEMON, targetId = self, sourceCharacterId = "soldier", sourcePlayerId = self, until = SOURCE_LOSES_ABILITY, label = "")` — **derived, not stored**, so it tracks seating, character changes and life status; `label = ""` renders no token (the protection is innate).
night.first / night.other: never wakes. No step on any night, no day action. (Correctly absent from both order lists today.)
death:
  - `killOutcome` step 7 blocks `DEMON_ABILITY` only. **Not blocked:** execution (even a Demon-led one), exile, `EVIL_ABILITY` (Assassin/Godfather/Witch), `GOOD_ABILITY` (Gossip), storyteller deaths. `SeatSheet.requestKill` already has the cause and discards it (`SeatSheet.kt:266-268`); the substring match at `SeatSheet.kt:258-262` (`"safe from"`) is what produces the wrong execution dialog.
  - **The blocked attack is a real outcome, not a warning.** The demon panel's primary action becomes *"Attack fails — nobody dies tonight"*, which records the attack, kills nobody, marks the step done, and states **"The Demon does not get to choose again."** "<Name> dies" is demoted to "Kill anyway (override)".
  - `demonHarmBlocked(state, lookup, soldierId)` gates non-kill Demon harm: `derivedPoison`/the No Dashii standing rule **must skip a Soldier neighbour entirely**, and the same hook covers Pukka poison, Vigormortis, Fang Gu turn-evil, Lord of Typhon and Yaggababble.
  - Impaired (poisoned, `Drunk` token, `characterId == "drunk"`, No-Dashii poison) ⇒ `abilityWorks == false` ⇒ no protection, and the UI must say **"<Name> is the Soldier but is POISONED — they die."** with the kill primary.
day: DAWN `publicScript` — *"Announce: nobody died tonight."* This is the one instruction the How to Run gives, and a Soldier-protected night is the case an ST most easily forgets.
ledger: the blocked attack appends a `WakeEvent` for the Demon and a `MalfunctionEvent` (night-engine §6: "every `Attack` that was blocked by protection") so the Mathematician and the Chambermaid become exact and the log stops being deaths-only.
tests:
  - Given an alive unimpaired Soldier · When protections are queried for `DEMON_ABILITY` · Then an active `SAFE_FROM_DEMON` from `soldier` is returned; for `EXECUTION` and `EVIL_ABILITY` the list is empty.
  - Given the Soldier holds `poisoner:Poisoned` · Then no active protection, and `deathNotes` must **not** claim safety.
  - Given seats `[nodashii, soldier, chef, …]` · When the No Dashii standing rule runs · Then the Soldier is **not** poisoned and `isImpaired(soldier)` is false.
  - Given the Imp targets an unimpaired Soldier via the demon resolver · Then `deaths` is unchanged, a blocked-attack record exists, and the demon step is marked done.
  - Given `activeJinxes(["leviathan","soldier"])` · Then exactly one jinx with the official reason (fails today).
open: `soldier.md` proposes a new `DeathCause.MINION_KILL`; status-model already models this as `DeathKind.EVIL_ABILITY` + `sourceCharacterId`. Prefer the latter — but the seat sheet still needs a non-`DEMON` night-kill button so Assassin/Godfather deaths stop being stamped `DEMON`.

---

## ravenkeeper — Ravenkeeper · tb townsfolk · P0:3 P1:4
today: the row is emitted on **every** other night with no wake condition — and on the one night it must fire, the app dims it, prints *"All holders are dead — usually skip"* in error red, and adds the caveat *"they normally don't act"*. Both jinx entries in the data carry **invented** text describing the opposite mechanic.
data:
  - characters.json: ok — text current, `reminders: []`. Add the new `actsWhileDead: Boolean = true` field (night-engine §Data changes) rather than relying on prose.
  - night_and_jinxes.json: **replace both reasons with the official text** — leviathan↔ravenkeeper → *"Each night*, the Leviathan chooses an alive player (different to previous nights): a chosen Ravenkeeper uses their ability but does not die."*; riot↔ravenkeeper → *"Each night*, Riot chooses an alive good player (different to previous nights): a chosen Ravenkeeper uses their ability but does not die."* (today both say the Ravenkeeper wakes when they die **by execution** — a ST reading the in-app jinx runs the wrong rules)
  - night_guide.json: add *"They may choose a dead player."* and *"Dying during the day gives them nothing."*; add a second `shows` entry for the false-token case
setup: none
identity: plain. A `characterId = "drunk"`, `shownCharacterId = "ravenkeeper"` seat **does** get the row (via `nightRoleId`) and must always be shown a false token.
night.other (and night.first on scripts with a night-1 kill):
  gate: `Gates.actsWhileDead` + `diedTonight` — `deaths.any { playerId == holder && atNight && cycle == state.cycle }`, **regardless of cause** (Demon, Mayor bounce, Assassin, Godfather, Gossip, Witch, storyteller), **regardless of `resurrected`** (dying and being resurrected the same night still triggers it — the Professor sits at other-night 63, *before* the Ravenkeeper at 72, so this ordering is reachable), and **regardless of impairment** (they still wake; they are shown a lie). Otherwise `Skip("no night death tonight")`, collapsed and auto-ticked. Badge the firing case **positively**: *"Dead — this ability fires because they died. Wake them."*
  insertion: the step already sits at other-night 72, after every Demon, so normally a gate suffices; when the death lands after the cursor, re-stamp to `cursor + 0.5` and badge *"out of order"*. On night 1 (Kazali, Lord of Typhon, Boffin-granted kills) the step must be **inserted** into the first-night plan — it is absent from `firstNight`.
  action: `ChoosePlayers("ravenkeeper", "They point at a player.", min = 1, max = 1, constraints = [ANY_LIVING_STATE, SELF_ALLOWED], sort = ALIVE_FIRST)` — dead seats are legal and must stay, marked `†` and grouped below
  effects: none — no tokens, no kills (they are already dead)
  deferred: `Announce(BriefingSlot.DAWN_PRIVATE, "Ravenkeeper <name> learned <shown> from <target>")`
  info: the target's **`characterId`**, never `shownCharacterId` — the Drunk shows as *Drunk*, the Lunatic as *Lunatic*, the Marionette as *Marionette*. Misregistration becomes a **chooser, not a caveat**: a chosen `spy` offers any Townsfolk/Outsider on the script (default: a not-in-play good character, or this Spy's recorded registration); a chosen `recluse` offers any Minion/Demon (the wiki's own example is a **dead** Recluse still misregistering — do not gate the chooser on `alive`). Impaired (or `impairedAtDeath`) or Vortox ⇒ a **false-token chooser** seeded in order: the 3 demon bluffs → not-in-play good characters → in-play characters.
  show: `ShowCard.CharacterCard("The player you chose is this character", "<id>")` — one tap, both for the true token and each false option
  visibility: nothing to the Demon or Minions. A **Lunatic's** "kill" is not a real death, so the wake condition must not fire off a `lunatic:Attack N` token.
day: `LedgerEntry(kind=STATEMENT)` for the Ravenkeeper's public claim, so claim-vs-truth is visible in the log.
death: n/a for others; a Monk-protected Ravenkeeper simply never satisfies the wake condition. Fixing the gate also makes `InfoCalc.chambermaid` exact (`InfoCalc.kt:479-483` currently names the Ravenkeeper as a manual judgement call).
ledger: `recordChoice("ravenkeeper", rkId, [targetId])` + `recordTold(rkId, "ravenkeeper", shown = "<Character>", impaired)` + `recordRuling("misregister", targetId, characterIds = [registered])` reused by every later step.
tests:
  - Given the Ravenkeeper was killed this night by the Imp · When the night sheet is built · Then the step gates `Fire` and sits after the `imp` step.
  - Given an alive Ravenkeeper on night 3 / dead since night 2 / executed on day 2 · Then all three gate `Skip` (today all three are indistinguishable actionable rows).
  - Given a night death with cause `OTHER_NIGHT_DEATH` (Mayor bounce, Assassin) · Then the step fires.
  - Given the Ravenkeeper died at night on cycle 3 and was resurrected the same cycle by the Professor · Then the step still fires and the `DeathEvent` keeps `resurrectedAtCycle`.
  - Given `activeJinxes(["leviathan","ravenkeeper"])` · Then the reason equals the official jinx string (fails today).
open: none — but the `PlacedReminder("recluse","Registered: <X>")` proposed in the source file must be re-expressed as `Effect(REGISTERS_AS)` + a RULING entry (that label is not in `characters.json`).

---

## undertaker — Undertaker · tb townsfolk · P0:3 P1:5
today: the day arithmetic (`relevantDay` = `cycle - 1` at night) is right, exiles are correctly ignored, an executed **Drunk shows as Drunk**, and a resurrected executee still gives info. Three P0s: the in-app guide states the **opposite of the rule**, the calc reads the **live** `characterId` instead of the death snapshot, and "executed but did not die" cannot be expressed at all.
data:
  - characters.json: ok — `reminders: ["Died today"]`, `otherNightReminder` current
  - night_and_jinxes.json: ok — otherNight 75 (after `professor` 63 and every Demon), absent from firstNight, no jinxes
  - night_guide.json: **`undertaker.other.instructions` is wrong** — "Only act if a player was executed today (marked Died today), **even if they did not die from it**" contradicts *"The player must have died from execution"* / *"the execution does not cause a death (in which case the Undertaker learns nothing)"*. Rewrite: only act if a player **died** by execution today; show their **true** character token (the Drunk shows as the Drunk); if nobody was executed **or the executed player did not die**, do not wake them; Travellers are exiled, not executed (the Scapegoat is the exception); if the executed player was the Spy or Recluse, show the character they register as; if the Undertaker is drunk or poisoned, show a false token. Add a second `shows` entry for the false token.
setup: none
identity: plain
night.other:
  gate: `holder.alive && executionDeathToday(state) != null` — i.e. an `ExecutionRecord` for `relevantDay` with `outcome == DIED`. `resurrected` is deliberately **ignored** (they still died by execution today). Otherwise `Skip("no execution today — does not wake")`, collapsed and auto-ticked; today the row always renders and the "doesn't wake" answer is only visible after expanding it, while the unfinished-steps guard still demands a tick. A dead Undertaker also `Skip`s.
  action: `ShowInfo("undertaker", targetsNeeded = 0)` — never a picker
  effects: none at this step. The **execution** places the token: on `execute(outcome = DIED)`, auto-place `PlaceToken("undertaker", "Died today", <seat that actually died>, exclusive)` — only when an Undertaker is in play. Today nothing places or removes it; `grep "Died today"` finds only data files.
  deferred: none
  expiry: add `("undertaker","Died today")` to **`EXPIRES_AT_DAWN`** (day-engine §F agrees). Walk it: placed during DAY *N* → DAY→NIGHT clears only dusk tokens, so it survives into NIGHT *N+1* where the Undertaker reads it → NIGHT→DAY removes it at the start of DAY *N+1*. `EXPIRES_AT_DUSK` would sweep it **before** the Undertaker ever sees it.
  info: the answer is `DeathEvent.characterIdAtDeath` (falling back to the live `characterId` only for pre-snapshot saves) — **never** the live seat, **never** `shownCharacterId`. Today `InfoCalc.kt:287` reads the live character, so a Pit-Hag/Barber/Snake-Charmer/star-pass change — or the ST simply correcting a mis-assigned token — retroactively rewrites what the Undertaker learns. Multiple executions today ⇒ present all of them as chips and let the ST choose (the rule is "the Storyteller chooses which character to show"); default to the last. Spy/Recluse ⇒ the same **registration chooser** as the Ravenkeeper, **not gated on `alive`** (their abilities keep working when dead). Impaired or Vortox ⇒ false-token chooser seeded from bluffs → not-in-play good → in-play.
  show: `ShowCard.CharacterCard("This character was executed today", "<id>")`
  visibility: nothing to the Demon or Minions
day: the execution funnel must record an **outcome**, not just a death. `ExecutionRecord(outcome = SURVIVED, preventedBy = "devilsadvocate" | "pacifist" | "fool" | "sailor" | "tealady" | "vizier" | "zombuul")` ⇒ the Undertaker does **not** wake, and the step reads *"<Name> was executed but did not die — the Undertaker learns nothing."* `DayBriefing` DAY_START carries yesterday's execution forward — *"the Undertaker will learn <X> tonight"* — which is what the ST needs while choosing the Poisoner's target.
death: `Scapegoat` deaths must be recorded as `EXECUTION`, not `EXILE` (`DayScreen.kt:319-357` forces `isExile = true` for any traveller nominee, so the Undertaker wrongly learns nothing). **Virgin** deaths must be `EXECUTION` too. Gunslinger/Witch/Slayer day-kills are not executions and must stay distinct.
ledger: `recordTold(utId, "undertaker", shown = "<Character>", impaired)` + the `misregister` RULING for a Spy/Recluse execution.
tests:
  - Given a player executed on day 2 as the Chef whose seat is later changed to the Butler · Then night 3 names **Chef** (fails today).
  - Given an `ExecutionRecord(outcome = SURVIVED)` (Devil's Advocate) · Then the Undertaker does not wake (cannot be expressed today).
  - Given a Traveller exiled on day 2 and no execution · Then the Undertaker does not wake; given a **Scapegoat** killed by execution · Then it does.
  - Given an Undertaker in play and a day-2 execution · Then the dead seat holds `undertaker:Died today`, and after `advancePhase` NIGHT 3 → DAY 3 it is gone (both halves fail today).
  - Given the Spy is executed on day 2 · Then the result exposes a non-empty registration option list, not a caveat string.
open: none

---

## virgin — Virgin · tb townsfolk · P0:5 P1:4
today: the **entire** implementation is one advisory string (`StatusEffects.kt:152-157`), rendered only while both draft chips are selected. Every consequence in the How to Run is manual, and `FullGamePlaytestTest.kt:631-649` scripts the ST doing it by hand. The app knows the nominator's team and the Virgin's impairment and says neither, so it actively advises executing someone who should live.
data:
  - characters.json: ok — `reminders: ["No ability"]`, absent from both night orders, no jinxes
  - night_and_jinxes.json: ok
  - night_guide.json: **no `virgin` entry** — add one under a new `"day"` section (requires `NightGuideEntry.day: GuideNight?`, `NightGuide.kt:36-40`) carrying the How to Run, including the counter-intuitive parts: the day ends, and the ability is spent even on a miss
setup: none
identity: **read the true `characterId` on both sides.** A Drunk-shown-as-Virgin never fires; a Drunk nominator is an **Outsider**, not a Townsfolk (the wiki's own example) — this is exactly the case an ST gets wrong at 1am.
night.first / night.other: never wakes.
day: a **`NominationTrigger`**, evaluated by `checkNomination(...)` at the moment the nomination is declared, **before** any voting UI (day-engine §D already tables this row):
  - condition: `nominee.characterId == "virgin"` && nominee alive && **never nominated before in the game** (`state.nominations.none { it.nomineeId == nominee.id && !it.isExile }` — game-lifetime, not per-day) && no `virgin:No ability` token
  - `abilityWorks = !impaired(virgin) && virgin.characterId == "virgin"`; `nominatorTeam` from the **true** character: `TOWNSFOLK` fires; `OUTSIDER`/`MINION`/`DEMON`/`TRAVELLER` do not; `spy` ⇒ a `CHOICE` (*"registers as a Townsfolk — executed"* / *"registers as evil — nothing happens"*, defaulting to this game's recorded ruling); `recluse` ⇒ never fires (it cannot register as a Townsfolk)
  - fires ⇒ `TriggerKind.AUTO_EXECUTION + END_DAY`: `execute(state, nominatorId, outcome = DIED, nominatorId = nominatorId, via = ExecutionVia.VIRGIN, lookup)` — routed through `killOutcome`, so Devil's Advocate/Fool/Sailor/Tea Lady/Pacifist all get their say and a survivor still consumes the day's execution; place `("undertaker","Died today")` on the seat that died; `placeExclusiveReminder(virginId, ("virgin","No ability"))`; record the nomination `result = WITHDRAWN, votes = 0, voterIds = []`; `closeNominations(state, "The Virgin's ability ended the day")`
  - does **not** fire ⇒ **still spend it** (`("virgin","No ability")`) and continue to the normal vote, showing the private reason: *"<Nominator> is an Outsider — nothing happens."* / *"<Virgin> is poisoned — nothing happens."* / *"<Nominator> is a Traveller — nothing happens."*
  - once closed: `aboutToDie` must stop returning the earlier blocked player, the per-nomination **Execute** buttons disable, and the dusk guard must **not** offer "Execute & begin night" — today all three still fire, so a Virgin day can produce a **second** execution
  - self-nomination fires on the Virgin themselves (they are Townsfolk) — allow it, warn clearly; a dead nominator cannot nominate, the nomination does not count, and the ability is **not** spent (enforced by construction today via `DayScreen.kt:135-138`)
death: the Virgin execution is an **execution** for every downstream consumer — `DeathKind.EXECUTION`, `ExecutionRecord(via = VIRGIN)`, `undertaker:Died today` — so the Undertaker learns the nominator's character that night. Today the ST may reach for "Other death" and silently break it.
expiry: `virgin:No ability` — `Expiry.NEVER`, out of both tables, out of `revive`/`resurrect`.
ledger: one linked `LedgerEntry` per firing — *"D3 — <Nominator> nominated Virgin <Name>; ability fired; <Nominator> executed; day ended."* — plus the Spy `misregister` RULING. `DayBriefing` DAY_START carries "the Virgin still has their ability" / "the Virgin's ability is spent".
tests:
  - Given an alive unimpaired never-nominated Virgin and a Chef nominator · Then the Chef dies by `EXECUTION`, the Virgin holds `No ability`, and the nomination records `WITHDRAWN` with 0 votes.
  - Given a nominator with `characterId = "drunk"`, `shownCharacterId = "chef"` · Then nobody dies **and** the Virgin is spent and the vote proceeds. Same for a Minion and a Traveller nominator.
  - Given the Virgin holds `poisoner:Poisoned` and a Chef nominates · Then the Chef **lives** and the Virgin is spent (today the app still says "they are executed immediately").
  - Given nomination A passed with 4 votes on day 3, then the Virgin fires · Then `aboutToDie` returns null and the dusk guard offers no execution.
  - Given the Virgin fired on day 2 killing a Chef nominator · Then the Undertaker names **Chef** on night 3.
open: (a) a **Traveller** nominator is inferred, not quoted — no execution, ability still spent; label it as inferred in the UI. (b) Does `resurrect` restore the Virgin's ability? Status-model §6 drops every `SPENT` effect on resurrection; the Virgin's is a historical fact ("the 1st time you are nominated" has already happened), so it should probably **not** return — lead must rule.

---

## slayer — Slayer · tb townsfolk · P0:3 P1:5
today: **nothing.** `grep -rn "slayer" app/src engine/src/main` returns one unrelated placeholder string. No day surface, no `night_guide` entry, and — because the generic **"Mark spent"** chip lives inside `NightToolTray` and is keyed off the expanded *night* step — the intended once-per-game affordance is structurally **unreachable** for a character with no night step. Today's flow is 5 taps across 2 screens, entirely from memory, with nothing preventing a second shot.
data:
  - characters.json: ok — `reminders: ["No ability"]`, absent from both night orders. Set the new `spentLabel = "No ability"` (night-engine §2) so `Gates.notSpent` and the spend chip agree on the exact string. Optionally `dayAbility: {kind: "publicChoice", oncePerGame: true}`.
  - night_and_jinxes.json: ok — `lleech`↔`slayer` present and matching the wiki
  - night_guide.json: **no `slayer` entry** — add one with a `"day"` section carrying the How to Run *including* the coaching ("give the group a minute or two to discuss", "act like you're fiddling with tokens in your Grimoire") and two prepared `ShowCard.Message` shows: **"Nothing happens."** and **"<player> dies."**
setup: none
identity: a `characterId = "drunk"`, `shownCharacterId = "slayer"` seat **gets the panel** (the table believes they are the Slayer), always misses, and gets the `No ability` token on *that* seat so the ST is not prompted twice.
night.first / night.other: never wakes.
day: a **day-abilities resolver** on `DayScreen` (day-engine §I item 3), openable at any time during DAY — including while the Slayer is on the block — and openable for **any** seat with `bluff = true`, because the How to Run explicitly tells the ST to play a fake shot straight.
  - availability: holder alive && no `slayer:No ability` && `hasAbility`. When false the panel still opens, pre-set to "Nothing happens", with the private reason.
  - target: 1, any seat. Alive first in seat order; dead below with `†` and the hint *"a dead player can't die again — nothing happens"*. Self allowed.
  - resolution, in **one** confirmed undoable transaction: alive + `Team.DEMON` ⇒ hit; alive `recluse` ⇒ a registration `Choice` (*"registers as the Demon (dies)"* / *"registers as itself (nothing happens)"*), defaulting to this game's recorded ruling; the **Lleech host** ⇒ hit (jinx: the host dies); `spy` ⇒ always a miss, and say so rather than leaving the ST to reason it out; impaired shooter ⇒ **forced miss** with the private banner *"The Slayer is drunk/poisoned — say 'Nothing happens'. The shot is still spent."*; otherwise miss.
  - on a hit: `Attack` through `killOutcome` with `KillCause(kind = GOOD_ABILITY, sourceCharacterId = "slayer")` (a slay is **not** an execution — the Undertaker must not see it), behind the same protection-confirmation sheet as `SeatSheet.kt:288-307` so Scarlet Woman / star pass / Lleech notes are seen. Then run `WinCheck.check` and show the advisory with its Scarlet Woman caution; on a takeover, prompt for the new Demon seat like the star-pass flow.
  - **always**, hit or miss, impaired or not, real or… (bluffs excepted): `MarkSpent("slayer")` ⇒ `placeExclusiveReminder(slayerId, ("slayer","No ability"))`, in the same transaction as the kill.
  - the panel copy **is** the public announcement: *"<Target> dies."* (no character revealed) / *"Nothing happens."* — both offerable full-screen for a noisy room.
  - **bluff mode** resolves as "Nothing happens", touches **no** seat's tokens, and still writes the ledger entry — this is what answers "has that player already claimed Slayer?" three days later.
death: `DeathKind.GOOD_ABILITY`; day-engine adds `DeathCause.SLAIN` so it is distinguishable from a Gunslinger kill, a Witch curse and ST fiat in the log. Scarlet Woman: state the **current alive count** in the panel — 5+ alive hands the Demon over, fewer ends the game.
expiry: `slayer:No ability` — `Expiry.NEVER`. Must never be added to `EXPIRES_AT_DAWN`/`_DUSK`.
ledger: `LedgerEntry(kind = STATEMENT, sourceId = "slayer", speakerId, targetIds = [target], text = announced result, genuine = !bluff)` + `SPENT` + the Recluse `misregister` RULING. `DayBriefing` DAY_START: *"The Slayer still has their shot."* / *"The Slayer publicly claimed today."*
tests:
  - Given DAY 2, Slayer alive and unspent, Imp alive · When they shoot the Imp · Then the Imp dies with the slay cause and the Slayer holds `slayer:No ability`.
  - Given the same board, shooting the Chef · Then nobody dies **and** the Slayer is still marked spent.
  - Given the Slayer holds `poisoner:Poisoned` · When they shoot the Imp · Then the Imp is **alive** and the Slayer is spent.
  - Given a spent Slayer · When `advancePhase` runs DAY→NIGHT→DAY · Then the token is still present.
  - Given the Imp declares a fake Slayer shot with `bluff = true` · Then no seat gains or loses a reminder, nobody dies, and a ledger entry exists.
open: does `resurrect` restore the spent shot? `slayer.md` says no; the Glossary, night-engine §2 and status-model §6 say a resurrected player regains their ability *"even if it was a once per game ability"*. Lead must rule — it changes one test and one line of `resurrect`.

---

## mayor — Mayor · tb townsfolk · P0:3 P1:4
today: the Mayor exists as a name, an ability string and **one caution line** in `WinCheck.kt:88-98` that only fires at `alive.size <= 2` — i.e. one death *after* the window has closed. No `night_guide` entry, no `deathNotes` branch, no dusk check, no redirect anywhere. The dusk dialog's **"No execution"** button is the exact Mayor trigger and it silently advances to night.
data:
  - characters.json: `mayor.reminders` — add `"Died instead"` **only if** the bounce is rendered as a token; night-engine §4 requires every `TokenRule` pair to exist in `characters.json`, and this is not an official token. Alternative: an `Effect(label = "")` with no token. Lead picks.
  - night_and_jinxes.json: **add the missing Mayor↔Riot jinx** — `{"id1":"mayor","id2":"riot","reason":"The Mayor may choose to stop the Riot; good wins if only one player remains when stopped, otherwise evil wins."}`. The Leviathan jinx is present but data-only (nothing tracks "day 5, no execution").
  - night_guide.json: **no `mayor` entry** — add one with no first/other block but a `passive`/`day` section carrying the day-briefing prose
setup: none
identity: plain
night.first / night.other: never wakes. The bounce is **not** a night step — it lives in `killOutcome`.
death:
  - `killOutcome` step 11: `atNight && kind != EXECUTION && target is a Mayor with their ability` ⇒ `Choice("The Mayor would die tonight. Who dies instead?")` with seat chips + `[The Mayor dies]`. **Three** buttons, never two — today `NightScreen.kt:624-635` offers only "<Mayor> dies" and "No kill", so the ST must abandon the panel, switch tabs and kill someone from the seat sheet, risking a double-kill or a wrong cause.
  - ordering is load-bearing: the Mayor `Choice` sits **after** the protection blocks (step 7), so a Monk-protected Mayor yields *"nobody dies"*, not a redirect (wiki: *"Mayor's ability doesn't trigger; nobody dies"*).
  - gate: Mayor alive and unimpaired. A poisoned Mayor neither bounces nor wins at 3 — say so: *"⚠ The Mayor is POISONED — no redirect. They die normally."*
  - the substitute is killed with the **same** `KillCause` and `atNight` flag as the original attack (so the Ravenkeeper trigger fires on a bounced death, which is the wiki's own canonical example). Sort candidates alive-non-Demon → alive-Demon → dead (disabled); annotate protections; annotate the Demon chip *"redirecting onto the Demon is contentious — prefer another seat"*. **Warn, do not block.**
  - covers `DEMON_ABILITY` and any other night death — the text is "if you die at night", not "if the Demon kills you". Never execution, exile or a day-time death.
  - `PlaceToken("mayor","Died instead", substitute)` with `Expiry.DAWN`; the dawn briefing announces the substitute's death **without** explaining it.
  - add the missing `deathNotes`/kill-panel branch: today nothing at all is printed when the Demon picks the Mayor.
day:
  - `WinCheck.duskCheck` ordered rule `mayor-dusk`, consulted by the phase button on DAY→NIGHT **before** `advancePhase`: `aliveCountWithTravellers == 3` (Travellers **count** — the wiki's five-alive-including-two-Travellers example) && **no `ExecutionRecord` for `state.cycle`** with outcome `DIED` **or** `SURVIVED` (an execution that killed nobody still suppresses the win) && a living unimpaired Mayor ⇒ `Advisory(goodWins = true, ruleId = "mayor-dusk", blocking = true)`. Cautions: a matching Vortox rule (ordered first, explicit collision note), *"Travellers count — exile them first"*, and Mayor-is-the-Drunk / `No ability`. Present via the existing `WinAdvisoryDialog` with "Declare good wins" / "Not yet — begin the night".
  - the dusk guard's third state matters here: when nobody is on the block it must still ask **"No execution today?"** and call `noExecution(state)` — that is the input this rule needs.
  - `DayBriefing` DAY_START at 3 alive with a sober living Mayor: **"MAYOR WIN IS LIVE — if nobody is executed at dusk today, good wins."** Plus *"Current tally is a tie — as it stands, good wins at dusk"* (a tie is a no-execution) and, when impaired, *"3 alive, but the Mayor is poisoned — their win condition does not apply today. Do not announce it."*
ledger: `recordChoice("mayor", mayorId, [substituteId])` + a DAWN `ANNOUNCE` for the substitute's death, so the reveal flow can explain why the Ravenkeeper died.
tests:
  - Given 3 alive — sober `mayor`, `imp`, `poisoner` — day 3 with no execution · Then `duskCheck` returns a Mayor win; with an `ExecutionRecord(outcome = SURVIVED)` it does not; with an `EXILE` on day 3 it still does.
  - Given `mayor`, `imp` and one alive **Traveller** · Then `duskCheck` returns a Mayor win (today `WinCheck.kt:19` filters Travellers out and declares **evil** wins).
  - Given the 3-alive board with `poisoner:Poisoned` on the Mayor · Then `duskCheck` returns null.
  - Given `mayor`@2 and `ravenkeeper`@5 on night 3 · When the demon kill is bounced onto seat 5 · Then seat 2 is alive with no `DeathEvent`, seat 5 dies `atNight` with the demon cause, carries `mayor:Died instead`, and the Ravenkeeper's wake condition fires; the token clears at dawn.
  - Given `monk:Safe` on the Mayor and a sober Monk · When the Demon targets the Mayor · Then "nobody dies" and **no** bounce candidates are offered.
open: (a) `mayor.md` test 11 asserts `WinCheck.check`'s evil-wins-at-≤2 must **count** Travellers; day-engine §G says that rule uses `aliveCountResidents`. Direct conflict — lead must rule (the Mayor's own count is not in dispute). (b) the wiki summary's *"a redirected death cannot go to protected players or the Demon"* could not be confirmed as hard rules text — treat as strong guidance, warn only, re-verify before shipping a constraint.
