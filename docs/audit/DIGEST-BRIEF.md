# Phase 2a — character digest brief

You are compressing finished character audits (docs/audit/characters/<id>.md, each
~300-500 lines of rules research + defects + spec) into **canonical requirement cards**
that implementers will consume directly. Read-only otherwise: do NOT edit code/data,
do NOT run gradle, do NOT commit. Write exactly one file:
/Users/ukreitner/botc/docs/audit/digest/<group>.md (absolute path — do not nest).

## First, absorb the shared vocabulary (read these sections in full)
- docs/audit/mechanics/night-engine.md — the "Proposed behaviour (spec)" section
  (StepGate / wake predicates, NightAction (target kinds, constraints incl.
  DIFFERENT_FROM_LAST_NIGHT, allowNone), NightEffect, TokenRule / token lifecycle
  (copies, exclusive, expiry, countdown), ChoiceRecord, StepKey(id, playerId, variant),
  WakeStyle.FIRST_NIGHT re-runs, Reduced gate, DawnReport / DayBriefing / DuskBriefing,
  WakeEvent / MalfunctionEvent).
- docs/audit/mechanics/status-model.md — the spec section (Effect model with
  until/endsWithSource, impairment(player) reasons, killOutcome precedence, ExecutionRecord,
  DeathEvent + Prompt queue with BriefingSlot, on-death trigger table, resurrect semantics).
- docs/audit/mechanics/day-engine.md — the spec section (GameActions.execute funnel,
  checkNomination/recordNomination interceptors, DayRules, executions list, dayLog/DayEntry,
  WinCheck.duskCheck, DayBriefing slots).
- docs/audit/mechanics/records-and-memory.md — the spec section (LedgerEntry/LedgerKind
  as the single record type, Memory.lastChoice, PlacedReminder payload fields).
Use THEIR type names. Where a character file proposes a different shape or name, translate
it into these types and add a one-line "conflict:" note if the translation loses something.

## Card format — one per character, ≤70 lines, no prose padding
```
## <id> — <Name> · <edition> <team> · P0:<n> P1:<n>
today: <one line: what the app does / fails to do>
data:
  - characters.json: <exact field changes, or "ok">
  - night_and_jinxes.json: <missing/stale jinxes with id pairs + corrected reason text; order fixes; or "ok">
  - night_guide.json: <wrong statements to fix, missing entries (first/other/day/setup/reference); or "ok">
setup: <SetupTask rows: id · kind · prompt · candidates · validation; or "none">
identity: <shown vs acting vs registered roles; granted abilities; or "plain">
night.first / night.other:
  gate: <predicate in StepGate terms; dead-but-acts? once-per-game? Reduced cases>
  action: <target kind, count, constraints, sort/defaults, allowNone>
  effects: <tokens (sourceId:label ×copies exclusive? expiry) · status Effects (kind, until, endsWithSource) · kills (cause, respects protection?) · character/alignment changes>
  deferred: <what fires later and when (dawn/day-start/next night/on death)>
  info: <InfoCalc answer type + legal range · false alternatives · misregistration handling>
  show: <cards: kind + text; new card kinds needed>
  visibility: <what Demon/Minions/Lunatic must be shown>
day: <briefing lines (slot) · nomination/vote/execution interceptors · day tools · LedgerKind entries the ST records · vote/nomination rule changes>
death: <protections (blocks which causes; needs sober?) · on-death triggers (condition any/demon-kill/execution; consequence; prompt slot) · win/loss conditions>
ledger: <what must be remembered: lastChoice, statements, told-info, counters>
tests: <3-5 Given/When/Then, one line each, the ones that would fail today>
open: <unresolved rules questions, or "none">
```
Omit a line only if genuinely "none". Keep exact reminder-label spellings and sourceIds.
Preserve every P0 as either a data fix, a gate/action/effect line, or a day/death line —
nothing P0/P1 may be dropped; P2/P3 may be folded or dropped.

At the top of the file put a 10-line "Group notes" block: cross-character patterns,
conflicts between character files and the mechanics specs, and anything the plan must
decide. Final message (return value): the file path, character count, and 5 bullets of
the most consequential decisions the lead must make.
