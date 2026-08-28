# Playtest findings — the day — agent C

Driven on `emulator-5558` against build `2020fec`, following
[`tools/emu/README.md`](../../../tools/emu/README.md) and the
[`PLAYTEST-FINDINGS`](../PLAYTEST-FINDINGS.md) template. Specs checked against:
[`ux/day-screen.md`](../ux/day-screen.md) and
[`mechanics/day-engine.md`](../mechanics/day-engine.md) §A–§E.

**Game played:** Trouble Brewing, 10 seats assigned by hand — 1 Virgin, 2 Butler,
3 Mayor, 4 Saint, 5 Poisoner, 6 Imp, 7 Washerwoman, 8 Librarian, 9 Chef,
10 Empath — then five days including a Virgin trigger, a tie, a
no-death execution, four travellers (Voudon, Bureaucrat, Thief, Beggar), an
exile, the Saint-executed ending and the Mayor-3-alive ending.

**Scenarios written**

| file | what it does |
|---|---|
| `tools/emu/scenarios/C_setup10.py` | full 10-player TB game, all ten characters assigned by hand |
| `tools/emu/scenarios/C_setup_rest.py` | generated tail of the above (seats 3–10), used to recover a partial run |
| `tools/emu/scenarios/C_day_repro.py` | **64 steps, all passing from `--fresh`** — the minimal reproduction for C-1, C-3 and C-9 |

Counts: **1 P0 · 6 P1 · 15 P2** (22 findings).

---

## P0

### 1. **P0 · Day tab → vote panel** — a hand the app itself labels "may not vote" is added to the tally and can put a player on the block

The vote chips for ineligible voters are dimmed and carry a reason line, but they
stay tappable **and their vote is counted**. `DayRules.voteRules` computes
`eligibleVoterIds` correctly; `DayRules.tally` then sums every selected id
without filtering against it.

Worst case seen: with a living sober **Voudon** in play the threshold is 1 and
only the Voudon and the dead may vote. Tapping a living non-Voudon chip prints
*"⊘ Player 1 may not vote."* and simultaneously reads
**"Lock in: Player 4 is ON THE BLOCK (1 of 1)"**.

- **Repro** (from `--fresh`; the spent-ghost variant is the cheapest)
  ```sh
  ./emu.sh launch emulator-5558 --fresh
  ./scenario.py emulator-5558 C_day_repro     # gets you a Butler + Master token
  # …then, in a longer game: kill a player, spend their ghost vote in one
  # nomination, open a second nomination and tap their chip again:
  ./ui.py emulator-5558 tap "Nominate"
  ./ui.py emulator-5558 tap "^Player 1$"      # nominator
  ./ui.py emulator-5558 tap "^Player 6$"      # nominee
  # scroll to the vote row and tap the chip marked "Player 9 ⊘"
  ```
- **Expected** an ineligible hand cannot be recorded, or is recorded with
  `counted = false` and excluded from the tally — `ux/day-screen.md` §F
  (`VoteEntry.counted`) and finding 22 of that document.
- **Actual** the chip toggles CHECKED, the big tally increments, the outcome line
  and the **Lock in** label are both computed from the illegal total. Observed
  three ways: a spent ghost vote (`Player 9 ⊘` → "1 of 4"), a Voudon-blocked
  living player (`⊘ Player 1 may not vote.` → "ON THE BLOCK (1 of 1)"), and a
  Butler without their Master (see C-3).
- **Screenshot** `tools/emu/out/C/47-voudon-ineligible-counts.png`,
  `tools/emu/out/C/34-ghost-spent.png`
- **Suspect** `engine/src/main/kotlin/com/clocktower/engine/DayRules.kt:875-899`
  (`tally` / `countedVoters` — only the *secret-voting* branch filters) and
  `app/src/main/java/com/clocktower/grimoire/ui/screens/day/NominationModel.kt:153-165`
  (`ineligible` is built and rendered, then never applied to `tally`).

---

## P1

### 2. **P1 · Nomination check card** — the Goblin question fires on every nomination in a script with no Goblin, and on exiles

Every nomination of any seat that has a character assigned raises
*"! Did Player N claim to be the Goblin?"* with **[They claimed the Goblin]** /
**[No claim]**. Trouble Brewing has no Goblin, so this is pure noise on every
single nomination of every game on the base scripts — and it is *actionable*:
tapping the affirmative places a `goblin:Claimed` token, writes
*"X claims to be the Goblin."* into the ledger, and makes
`Execution.consequences` raise an "…if they are the Goblin, EVIL WINS" advisory
in a game where nobody can be the Goblin. It also fires on an **exile**, where
the Goblin win cannot apply at all (`mechanics/day-engine.md` test 38: *"On an
exile it does not fire."*).

Every sibling trigger in the same function is gated on the character existing
(`vizier != null`, `holderOf("fearmonger")`, …); the Goblin branch is gated only
on `nominee.characterId != null`.

- **Repro**
  ```sh
  ./emu.sh launch emulator-5558 --fresh
  ./scenario.py emulator-5558 C_day_repro
  # step 58 screenshot: "! Did Player 1 claim to be the Goblin?"
  ```
- **Expected** the Goblin check appears only when a Goblin is on the script (or
  at least in play), and never on an exile.
- **Actual** it appears on every nomination and every exile in Trouble Brewing.
- **Screenshot** `tools/emu/out/C_day_repro/emulator-5558/58-screenshot.png`;
  exile case `tools/emu/out/C/49-exile-panel.png`
- **Suspect** `engine/src/main/kotlin/com/clocktower/engine/DayRules.kt:467-484`
  — `if (nominee != null && nominee.characterId != null)`. Needs a
  `holderOf(state, "goblin") != null` / "goblin on script" guard and an
  `!isExile` guard.

### 3. **P1 · Vote panel, Butler** — an illegal Butler hand is counted, so the app offers to lock in a wrong ABOUT-TO-DIE

With `butler:Master` on Player 4 and Player 4 **not** voting, tapping the
Butler's chip shows *"! Player 2's Master is not voting — tally it anyway, then
check."* and increments the tally. Driven to the boundary: 3 legal hands plus the
illegal Butler read **"Player 10 is about to die"** and
**"Lock in: Player 10 is ON THE BLOCK (4 of 4)"**. Legally that is 3 of 4 — SAFE.

The code path is deliberate (`DayRules.countedVoters` drops the Butler only under
secret voting) but it contradicts `ux/day-screen.md` §F, which specifies
`counted = false` with the reason *"Butler — master's hand is down"*, and it
produces a wrong `aboutToDie` at exactly the moment the storyteller is looking at
the table, not the phone.

- **Repro**
  ```sh
  ./emu.sh launch emulator-5558 --fresh
  ./scenario.py emulator-5558 C_day_repro    # last screenshot shows "1 of 4"
  ```
- **Expected** per the wiki (`Butler`: *"you may only vote if they are voting
  too"*) the hand does not count; `ux/day-screen.md` §F test 10.
- **Actual** counted; the outcome line and the Lock-in label are computed from
  the illegal total.
- **Screenshot** `tools/emu/out/C_day_repro/emulator-5558/64-screenshot.png`,
  boundary case `tools/emu/out/C/28-butler-illegal-tally.png`
- **Suspect** `engine/src/main/kotlin/com/clocktower/engine/DayRules.kt:887-899`
  (`countedVoters`).

### 4. **P1 · Nominations after a Virgin execution** — the day is declared over, but nominations and voting stay fully live and a locked-in vote is silently discarded

A Townsfolk nominates the Virgin → **[Execute Player 3]** → the card, the DUSK
card and the stat strip all read *"Player 3 was executed — the day is over."*
Yet:

- the in-flight nomination's vote panel is still rendered with live chips and a
  live **[Lock in: Player 1 is SAFE (0 of 5)]** button;
- tapping **Lock in** clears the draft but records **nothing** — the nomination
  never appears in the NOMINATIONS card and never reaches the game log (the
  three day-2 nominations do), so the storyteller believes it was recorded;
- **[Nominate]** still starts a brand-new nomination, which renders its full
  check card (Saint warning, Goblin question) and a full vote panel with a live
  Lock-in button. The only sign anything is wrong is a `bodySmall` bullet
  *"Nominations are closed today — the day's execution is settled"* at the very
  bottom of the card.

- **Repro**
  ```sh
  # 10-player TB game with a Virgin on seat 1 and a Townsfolk on seat 3
  ./scenario.py emulator-5558 C_setup10        # then night 1 -> day 1
  ./ui.py emulator-5558 tap "Nominate"
  ./ui.py emulator-5558 tap "^Player 3$"
  ./ui.py emulator-5558 tap "^Player 1$"
  ./ui.py emulator-5558 tap "Execute Player 3"
  ./ui.py emulator-5558 tap "Nominate"         # still works
  ```
- **Expected** `mechanics/day-engine.md` §A/§D: a Virgin trigger sets
  `nominationsClosedOnDay`; the nomination ring and any open vote panel are
  dismissed, and further nominations are blocked or at minimum gated behind an
  explicit "Allow anyway".
- **Actual** everything stays live; a locked-in vote vanishes without a trace.
- **Screenshot** `tools/emu/out/C/17-day-over-vote.png`,
  `tools/emu/out/C/21-second-nom-detail.png`, log
  `tools/emu/out/C/40-log2.png`
- **Suspect**
  `app/src/main/java/com/clocktower/grimoire/ui/screens/day/NominationPanel.kt`
  (the panel is rendered regardless of the closed-day state) and
  `engine/.../DayRules.kt` `recordNomination` (silently drops on a closed day).

### 5. **P1 · Execution sheet** — no warning that the player on the block is the Saint

Executing the Saint ends the game for good. The **nomination** check card warns
(*"! Player 4 is the SAINT — if this execution kills them, the good team
loses"*), but the execution sheet — the last, destructive confirmation — reads
**"Before you execute: Nothing stops it — they die."** and nothing else. The
Saint advisory only appears *after* the kill, as an "Is the game over?" modal.

`ux/day-screen.md` §G specifies the dusk/execution surface as the place where the
resolution is *"shown before the button is pressed"*.

- **Repro**
  ```sh
  # Saint on seat 4, alive; put them on the block, then:
  ./ui.py emulator-5558 tap "^DUSK$"
  ./ui.py emulator-5558 tap "Execute Player 4"
  ```
- **Expected** the "Before you execute" block lists the Saint consequence
  (and any other `ExecutionConsequence`), the way the nomination card does.
- **Actual** "Nothing stops it — they die."
- **Screenshot** `tools/emu/out/C/59-saint-execution-sheet.png`, then
  `tools/emu/out/C/60-saint-executed.png`
- **Suspect**
  `app/src/main/java/com/clocktower/grimoire/ui/screens/day/ExecutionSheet.kt`
  — the preview appears to render only `StatusEffects`-style protections, not
  `Execution.consequences`.

### 6. **P1 · Morning briefing** — the Butler's Master is never a standing fact

With a living Butler on seat 2 and `butler:Master` on seat 4, the MORNING
BRIEFING card reads **"Nothing constrains today."** `ux/day-screen.md` §B lists
`butler:Master` → *"`<Butler>` may only vote if `<Master>` votes"* as a
STANDING_FACT, and it is the single most common day-time constraint on the base
scripts. `Briefings.kt` contains no reference to `butler`, `zealot`, `tealady` or
`minstrel` (`grep -ci` = 0 for each); Devil's Advocate is covered generically via
`Status.protections`, and the traveller facts (Voudon, Bureaucrat, Thief, Beggar)
*are* present and read well — the Butler is the gap.

- **Repro**
  ```sh
  ./scenario.py emulator-5558 C_day_repro   # seat 1 Butler, Master on seat 2
  ./ui.py emulator-5558 tap "MORNING BRIEFING"
  ```
- **Expected** "Player 1 may only vote if Player 2 votes (Butler)."
- **Actual** "Nothing constrains today."
- **Screenshot** `tools/emu/out/C/26-briefing-butler.png`
- **Suspect** `engine/src/main/kotlin/com/clocktower/engine/Briefings.kt:350-395`
  (the "voting and nomination rules in force today" block covers
  secret voting / Vizier / Psychopath but not the Butler or the Zealot).

### 7. **P1 · Seat sheet, status effects** — **[Remove]** on a hand-placed *Poisoned* effect does nothing

Blocks a day-screen scenario: to restore a poisoned Voudon's ability you must
clear the poison, and you cannot. Tapped **[Remove]** four times (via `ui.py tap`
and raw `tapxy`), each with a 2–3 s settle; the effect row is unchanged every
time and the Day tab's threshold stays at the poisoned value.

- **Repro**
  ```sh
  ./ui.py emulator-5558 tap "Grimoire"
  ./ui.py emulator-5558 tap "^Seat 11,"
  ./ui.py emulator-5558 tap "\+ Token"
  ./ui.py emulator-5558 tap "^Poisoned$"
  ./ui.py emulator-5558 tap "^Remove$"      # nothing happens
  ./ui.py emulator-5558 find "Poisoned"      # still there
  ```
- **Expected** the effect is removed and the day's vote rules recompute.
- **Actual** the row persists; "Suspend" untested.
- **Screenshot** dump only — `tools/emu/out/emulator-5558-dump.xml` after the
  taps still lists `#18 'Poisoned' … #19 'Poisoner · expires at dusk'`.
- **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/SeatSheet.kt`
  (the status-effect row's Remove handler) — likely keyed on the wrong id for a
  hand-placed effect, since dusk expiry *does* clear it.

---

## P2

### 8. **P2 · Nomination check card** — every warning is rendered twice

Each nomination reason appears once as a card and again as a `·`-bulleted line at
the bottom of the same card. Seen for *"Player 7 has already nominated today."*,
*"Nominating a dead player — allowed, but no ghost vote is at stake"*,
*"Begg is a Traveller — this is an exile call"* and *"Nominations are closed
today"* (twice, verbatim, in the same list).

- **Repro** `tap "Nominate"` → a nominator who has already nominated → the reason
  shows as a card *and* as a bullet.
- **Screenshot** `tools/emu/out/C/31-dead-nominee.png` (nodes #56/#66),
  `tools/emu/out/C/21-second-nom-detail.png` (nodes #66/#67)
- **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/day/NominationPanel.kt:520-545`
  — the trigger list and the `reasons` list are both rendered.

### 9. **P2 · Setup checklist re-opens itself over the seat sheet, mid-game included**

Assigning or changing a character pushes the **"Before the first night"**
checklist bottom sheet on top of the open seat sheet. It happened on day 4 of a
running game (header *"4 of 9 done"*), and resuming a saved day-3 game opened
first that sheet and then the **Demon bluffs** sheet before the grimoire.
It is also what makes any scripted character assignment flaky — `back` closes the
checklist, not the seat sheet.

- **Repro** `./scenario.py emulator-5558 C_day_repro` — step 23 waits on it.
- **Screenshot** `tools/emu/out/C_day_repro/emulator-5558/23-wait-before-the-first-night.png`,
  mid-game `tools/emu/out/C/61-setup-sheet-day4.png`
- **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/GameShell.kt`
  — the setup-requirements sheet is opened on a state change rather than only
  during `Phase.SETUP`.

### 10. **P2 · "What was said"** — recorded statements are inert and never reach the game log

The composer is excellent (two taps and a sentence; the zero-typing
**[Claims…]** grid works and the character grid puts in-play characters first).
But the recorded rows — `Player 3 » "Player 6 is the Imp"`,
`Player 1 » "Claims to be the Slayer"` — carry no `click` flag: no edit, no
delete, no tri-state truth chips. `ux/day-screen.md` §C specifies a `✎` on every
row and `✓ ✗ ?` where truth matters, and *"Statements must also appear in the
game log"*. The log (Menu → Game log) shows deaths and nominations only.

- **Repro**
  ```sh
  ./ui.py emulator-5558 tap "\+ Say"
  ./ui.py emulator-5558 tap "^Player 3$"
  ./ui.py emulator-5558 type "Player 6 is the Imp"
  ./ui.py emulator-5558 tap "^Add$"
  ./ui.py emulator-5558 tap "^Menu$" && ./ui.py emulator-5558 tap "Game log"
  ```
- **Screenshot** `tools/emu/out/C/05-say-added.png`,
  `tools/emu/out/C/40-log2.png`
- **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/day/DayCards.kt`
  (statement rows) and `app/src/main/java/com/clocktower/grimoire/ui/screens/GameExtras.kt`
  (log entry builder).

### 11. **P2 · Game log** — an execution that killed nobody leaves no entry

*"Player 6 was executed and survived"* is shown on the day timeline, but the log
jumps straight from the day-2 nominations to the night-3 death. This is the
record Vortox, Mayor, Zombuul, Godfather and the Undertaker hinge on
(`ux/day-screen.md` finding 30 / §G `ExecutionRecord`), so its absence from the
one screen a storyteller checks after the fact is worth closing.

- **Repro** put a player on the block, **[Execute X]** →
  **[Executed — but they don't die]**, then Menu → Game log.
- **Screenshot** `tools/emu/out/C/39-executed-no-death.png`, `tools/emu/out/C/40-log2.png`
- **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/GameExtras.kt`
  (builds log rows from `deaths` + `nominations`, not from `executions`).

### 12. **P2 · Dawn card** — stale traveller announcements never clear

On day 5 the DAWN card still lists *"Announce: Vee joins the game as the
Voudon"*, *"Announce: Bura joins…"*, *"Announce: Thea joins…"* and
**"Announce: Begg joins the game as the Beggar"** — Begg joined on day 3 and was
exiled on day 3. The MORNING BRIEFING likewise keeps
*"Begg is the Beggar and holds 1 vote token…"* as a standing fact for a dead
traveller.

- **Repro** Menu → "A traveller joins…" on day 3, exile them the same day,
  advance to day 4 and day 5, open DAWN and MORNING BRIEFING.
- **Screenshot** `tools/emu/out/C/56-day4-briefing.png`, `tools/emu/out/C/57-day4-standing.png`
- **Suspect** `engine/src/main/kotlin/com/clocktower/engine/Briefings.kt`
  (`Memory.pendingAnnouncements` never ages out, and the Beggar standing fact has
  no `alive` guard).

### 13. **P2 · Stat strip** — "On the block: X — N votes" survives the execution

After Player 4 was executed and died, the strip still read
*"On the block: Player 4 — 6 votes"* for the rest of the day. Same as
`ux/day-screen.md` finding 29, now on the new banner.

- **Screenshot** `tools/emu/out/C/39-executed-no-death.png` (survivor case) and
  the day-4 dump after the Saint died
- **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/day/DayModel.kt`
  (`blockLine` has no `alive` / `executionSettled` guard).

### 14. **P2 · Day tab, type size** — rules-critical lines render at 12 sp and 10 sp

From `ui/theme/Type.kt`: `bodySmall = 12.sp`, `labelMedium = 12.sp`,
`labelSmall = 10.sp`. On the day screen these carry, among others:

| line | style | sp |
|---|---|---|
| `9 alive · 5 to execute · 1 ghost vote · 5 to beat` (`DayScreen.kt:303`) | bodySmall | 12 |
| `Voudon: only Vee and the dead may vote…` / `Player 7's vote counts 3 times` (`DayScreen.kt:318`, `maxLines = 2` + ellipsis) | bodySmall | 12 |
| `! Player 2's Master is not voting…`, `⊘ Player 9 — ghost vote already spent.`, `· Voudon: …`, `Votes for X, starting now…` (`NominationPanel.kt:480, 523, 527, 534, 540`) | bodySmall | 12 |
| the ring's seat number and its `† ◆ ✕ ✈` markers (`NominationPanel.kt:242`) | labelSmall | **10** |
| the `⊘` marker that distinguishes a spent ghost vote from a live one (`NominationPanel.kt:513`) | labelSmall | **10** |

`ux/day-screen.md` §0 requires the day surface to be readable *"without looking
closely"* while the storyteller's eyes are on the table. Nothing here is above
12 sp except the tally and the outcome line.

- **Screenshot** `tools/emu/out/C/12-virgin-check-full.png`, `tools/emu/out/C/34-ghost-spent.png`
- **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/DayScreen.kt:303,318`;
  `app/src/main/java/com/clocktower/grimoire/ui/screens/day/NominationPanel.kt:242,480,513,523,527,534,540`

### 15. **P2 · Nomination panel** — the vote panel does not come into view after the two ring taps, and the pinned ring hides the check card

`ux/day-screen.md` §E: *"the tally sheet slides up automatically"*. In practice
the two taps leave the check card clipped at the top of the scroll viewport
(`y = 1146`) with the vote panel entirely below the fold; getting from
"who nominates" to a countable tally took **two to three deliberate swipes**
every time, and the first swipe was needed *before* the Virgin/Saint decision
buttons were even legible. Vote chips scrolled just above `y = 1146` are still
reported as clickable but sit under the pinned ring, so a tap on them is
swallowed.

- **Screenshot** `tools/emu/out/C/10-virgin-check.png` (warning clipped to a
  26 px sliver), `tools/emu/out/C/12-virgin-check-full.png` (button cut in half)
- **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/day/NominationPanel.kt`
  — no `bringIntoViewRequester` / `animateScrollToItem` when the nominee is picked.

### 16. **P2 · Dusk sheet** — "BEGIN NIGHT n →" is the primary button, "No execution" is secondary

The sheet that appears when nobody is on the block offers
**[BEGIN NIGHT 4 →]** on its own row above **[No execution]** **[Not yet]**.
The record that Vortox, Mayor and the Zombuul gate on is the *secondary*
control, and the irreversible one is the prominent one. (Tapping **[No
execution]** both records and advances, which the label does not say.)

- **Repro** reach dusk with nobody on the block →
  `./ui.py emulator-5558 tap "Everyone, eyes closed"`
- **Screenshot** `tools/emu/out/C/54-dusk-sheet-noexec.png`
- **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/day/ExecutionSheet.kt`
  (dusk sheet button order).

### 17. **P2 · Kill sheet** — opens with "Record the death" below the fold; `audit` reports an untappable control

`ui.py audit` on the kill sheet as it opens:

```
=== SAFE-AREA VIOLATIONS (1) ===
  #82 '<View>' [53,2346][190,2400] @(121,2373)  click
      - bottom 84px under the navigation/gesture inset (home indicator)
      - CENTRE UNTAPPABLE: under the bottom navigation / gesture inset (y >= 2316)
```

Scrolling brings **[Record the death]** and **[Cancel]** into reach, so the flow
completes — but the sheet's own primary action is off-screen on open.

- **Repro** `tap "^Seat 1,"` → `tap "Kill…"` → `./ui.py emulator-5558 audit`
- **Screenshot** `tools/emu/out/C/62-kill-sheet.png`
- **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/KillSheet.kt`

### 18. **P2 · Top bar** — the Dusk (moon) phase button is still there during the day

`ux/day-screen.md` §I: *"The phase button moves off the top bar… during
`Phase.DAY` the only way to night is the Dusk card's [Everyone, eyes closed ▸]."*
Both exist today: the moon icon between Redo and ⋮ opens the same dusk sheet.
Two paths to the most destructive control, one of them an unlabelled icon.

- **Screenshot** `tools/emu/out/C/01-day1.png`
- **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/GameShell.kt`
  (top-bar action row).

### 19. **P2 · Execution sheet** — the "never say why" caption sits under both buttons

*"Say "Player 6 is executed", then "Player 6 is still alive." Never say why."*
is rendered below **both** [PLAYER 6 IS EXECUTED AND DIES] and
[Executed — but they don't die]; it only applies to the second.

- **Screenshot** `tools/emu/out/C/38-execution-sheet.png`
- **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/day/ExecutionSheet.kt`

### 20. **P2 · Seat sheet token picker** — travellers in play contribute no reminder tokens

With a Voudon, Bureaucrat, Thief and Beggar seated, the picker's "In play" and
"Rest of script" groups end at *Scarlet Woman · Is The Demon*: no
`bureaucrat:3 Votes`, `thief:Negative Vote`, `beggar`, `voudon`. Those tokens
are only placeable through the traveller's night step, so a mid-day correction
("I put the 3-votes token on the wrong seat") is impossible.

- **Repro** Menu → "A traveller joins…" → Bureaucrat → any seat → `+ Token`
- **Screenshot** dump — picker ends at Scarlet Woman
- **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/SeatSheet.kt:797-800`
  — `gameData.resolve(state.script)` excludes travellers, which come from
  `gameData.travellersFor(script)`.

### 21. **P2 · Tie line** — the stat strip names both tied players, the vote panel names only one

In-panel: *"Tie at 4 — Player 10. No one is about to die. 5 to beat it."*
Stat strip after locking in: *"Tie at 4 — Player 10 and Player 5…"*.
`ux/day-screen.md` §F specifies both names in both places.

- **Screenshot** `tools/emu/out/C/32-tie.png`, `tools/emu/out/C/33-after-tie.png`
- **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/day/NominationModel.kt`
  (`outcomeLine`).

### 22. **P2 · Day timeline** — "WHAT WAS SAID" shows a ✓ before anything has been said

On a fresh day the card reads *"WHAT WAS SAID / Tap a seat, then type or dictate
one line."* with a ✓ in the completed slot, next to DAWN's *"1 thing to
announce"* badge. A tick on an empty list reads as "done" rather than
"nothing owed".

- **Screenshot** `tools/emu/out/C/01-day1.png`
- **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/day/DayCards.kt`
  (stage-header completion glyph).

---

## What worked

Most of the `ux/day-screen.md` spec is genuinely built and behaves.

- **The timeline and the fixed bottom bar.** DAWN → MORNING BRIEFING → WHAT WAS
  SAID → NOMINATIONS → DUSK, each collapsing to one summary line, with
  **⏱ · + Say · Nominate** pinned at the bottom of every day. Stage cards
  auto-expand at the right moments.
- **The statement composer.** Two taps and a sentence (`+ Say` → speaker →
  autofocused field → **Add**), a working **[Add & another]**, and the
  zero-typing **[Claims…]** grid with in-play characters first. `audit` clean.
- **The dawn briefing.** Tickable ANNOUNCE items ("Announce: nobody died.",
  "Announce: Player 9 died."), "All said." on completion, and
  TRUE TODAY / SWEPT OFF THE GRIMOIRE sections.
- **Two-tap nominations on the pinned ring**, with `◆` / `✕` / `†` / `✈`
  markers, and correct **Unusual, not Blocked** handling —
  *"Player 7 has already nominated today."* + **[Allow anyway]**, and
  *"Nominating a dead player — allowed, but no ghost vote is at stake."*
- **The Virgin interceptor.** Three correct options (execute / ability spent,
  nothing happens / not a real nomination), it executes the nominator and closes
  the day exactly as `mechanics/day-engine.md` §D specifies.
- **Vote order and thresholds.** Clockwise from the nominee's left, every time.
  10 alive → 5, 9 → 5, 8 → 4, 12 → 6; 14 seats → exile needs 7.
- **The "to beat" tie rule.** *"Tie at 4 … 5 to beat it."*, the block correctly
  cleared, and `· 4 to beat` / `· 5 to beat` carried in the stat strip.
- **Ghost votes.** Spent exactly once on Lock in, counter decrements
  (2 → 1 ghost votes), and the chip renders `⊘` on later nominations.
- **Lock-in labels carry the outcome** — *"Lock in: Player 6 is ON THE BLOCK
  (5 of 4)"*, *"Lock in: TIE at 4 — nobody is about to die"*,
  *"Lock in: Begg is EXILED (7 of 7)"*.
- **Recorded nomination rows** gain **▸ voters** and **Withdraw**.
- **"Executed — but they don't die" is a first-class button** on the execution
  sheet, and it records a real execution: *"Player 6 was executed and survived —
  the day is over."*
- **The Voudon**, end to end: joining via the traveller dialog (alive, alignment
  chosen) flips the stat strip to *"9 alive · 1 to execute"* with **no ghost
  count**, the grimoire header to *"Day 3 · 9 alive · 1 to execute"*, the vote
  panel to only the Voudon and the dead as eligible with the note *"Voudon: only
  Vee and the dead may vote, one vote is enough, and no vote token is spent"* —
  and poisoning them restores *"9 alive · 5 to execute · 1 ghost vote"*
  immediately.
- **Weighted votes.** `Player 7 ×3` and `Player 8 −1` on the chips; tally went
  0 → 3 → 2 exactly as `ux/day-screen.md` tests 8 and 9 require. The briefing
  spells both out, plus the Beggar's vote token.
- **Exile.** Its own identity (*"(exile)"*, *"Begg is a Traveller — this is an
  exile call, not an execution"*), threshold over all 14 seats, every player
  eligible once with no weights (*"Exile — abilities do not apply; every vote
  counts once"*), no ghost spend, its own **[Exile]** action row and exile sheet
  (*"An exile is not an execution: today's execution stays available"*), and it
  correctly did **not** consume the day's execution.
- **"No execution" at dusk** when nobody is on the block.
- **The dusk sheet's who-wakes-tonight preview** — *"Tonight, 6 steps: Dusk ·
  Poisoner — Player 5 · Imp — Player 6 · Empath — Player 10 · Butler — Player 2
  · Dawn"*, shrinking to *"Tonight, 2 steps: Dusk · Dawn"* once the night roles
  were dead — plus TAKEN OFF THE GRIMOIRE listing every expiring token.
- **The timer survives every tab switch** — Day → Night → Day → Script →
  Grimoire → Day, counting down throughout (4:57 → 4:51 → 4:36), and the bottom
  bar shows **⏱ TIME** on expiry. `ux/day-screen.md` finding 40 is fixed.
- **Both endings fired.** Saint: *"The Saint died by execution - the good team
  loses."* with [Declare evil victory] / [Keep playing]. Mayor:
  *"Three players live and nobody was executed — the Mayor wins for good."*,
  with the two cautions that matter — *"Travellers count towards the 3 — exile
  them first if that is your intent"* and *"A tied vote is a no-execution: good
  still wins."*
- **`audit` was clean on every day-screen surface** — statement composer,
  Claims… grid, nomination panel, execution sheet, exile sheet, dusk sheet,
  traveller dialog. The only repeat offender is the bottom tab bar's 21 px
  gesture-inset overrun already filed by `emu-harness`.

## What I could not reach

- **Organ Grinder secret voting.** `DayRules.secretVoting` is true only when a
  living unimpaired Organ Grinder is in play (`DayRules.kt:942`), the Organ
  Grinder is not on any of the three base scripts, and the seat sheet's
  character chooser searches the **script only** — searching "Organ" returns
  nothing. There is no house-rule toggle, though `ux/day-screen.md` §F asks for
  one (*"also togglable by hand for house rules"*). Reaching it needs an
  imported custom script. The plumbing exists and looks right (`secret` is
  threaded through `NominationModel:151`, `ExecutionSheet:91`, `DayModel:113`,
  a `bodySmall` hold-to-peek variant of the Butler hint is in
  `NominationModel:158-161`, and `Briefings.kt:351` has the *"Eyes closed for
  every vote today — the tally is secret"* standing fact), but the hold-to-peek
  tally, the hidden on-block banner and "Lock in silently" were never rendered.
- **The Beggar's own vote-token rule** — Begg was exiled before an execution
  vote, so only the briefing line was observed.
- **Weighted votes placed from the Day tab** — see P2 #20; the `3 Votes` and
  `Negative Vote` tokens had to come from the travellers' night steps.
- **The Zealot** — not on Trouble Brewing; `Briefings.kt` has no `zealot` string,
  so the "must vote for every nomination" standing fact is likely missing too,
  but I did not drive it.
