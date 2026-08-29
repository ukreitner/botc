# Playtest findings — the day, re-test — agent C2

Second-wave re-test of [`C-day.md`](C-day.md) (findings C-1…C-22), driven on
`emulator-5556` against branch tip **`978692b`** (fix waves 1–2 + the wave-2
polish merged). Protocol: [`tools/emu/README.md`](../../../tools/emu/README.md)
and the [`PLAYTEST-FINDINGS`](../PLAYTEST-FINDINGS.md) template. Claims checked
against D74–D82 and the STATUS entries for Fix-C, Fix-F, Fix-G and the polish.

**Games played**

| # | script | seats | what it exercised |
|---|---|---|---|
| 1 | Trouble Brewing | 8 → 9 (traveller) | Butler master, ghost votes, tie, block, execution that kills nobody, Beggar traveller join/exile, Saint execution + ending, Virgin trigger, undo, game log, kill sheet, mid-game character changes |
| 2 | **imported** "C2 Grind" (Organ Grinder · Vizier · Psychopath · Mayor · Saint · Virgin · Butler · Poisoner · Baron · Imp) | 12, then 8 | a **real** Organ Grinder driving secret voting, Vizier day-immunity + nomination row, Psychopath briefing prompt, script import |
| 3 | Sects & Violets | 15 | 15-seat ring, Vortox standing facts, the Vortox "no execution = evil wins" ending at dusk |

**Scenarios written** — all pass from `--fresh` on `emulator-5556`

| file | steps | what it proves |
|---|---|---|
| `tools/emu/scenarios/C2_day_closed.py` | **73/73** | a Virgin execution closes the day; the ring goes DISABLED with an explicit override (C-4, C-13) — and the closed-day blocker is printed **twice** (C2-6) |
| `tools/emu/scenarios/C2_grind_vizier.py` | **96/96** | imports a custom script, drives secret voting from a real sober Organ Grinder, and lands on the Vizier's execution sheet (C2-4, C2-5) |
| `tools/emu/scenarios/C2_vortox_dusk.py` | **57/57** | the Vortox ending is announced at dusk, printed twice, and never offered (C2-1, C2-2) |

**Existing scenarios re-run on the tip, all green:** `C_fix_day` 60/60 ·
`C_fix_ring12` 47/47 · `F_fix_dusk` 42/42 · `F_fix_kill` 24/24 ·
`F_fix_traveller_tokens` 39/39 · `G_fix_game_log` 61/61 ·
`G_fix_secret_votes` 84/84.

Counts: **0 P0 · 2 P1 · 12 P2** (14 new findings).

---

## Verdict on C-1 … C-22

| # | sev | one line | verdict |
|---|---|---|---|
| C-1 | P0 | ineligible hands counted in the tally | **VERIFIED FIXED** |
| C-2 | P1 | Goblin question on every TB nomination / on exiles | **VERIFIED FIXED** |
| C-3 | P1 | illegal Butler hand counted | **VERIFIED FIXED** |
| C-4 | P1 | day live after a Virgin execution, lock-in silently discarded | **VERIFIED FIXED** |
| C-5 | P1 | no Saint warning on the execution sheet | **VERIFIED FIXED** |
| C-6 | P1 | Butler's Master never a standing fact | **VERIFIED FIXED** |
| C-7 | P1 | `[Remove]` on a hand-placed Poisoned did nothing | **VERIFIED FIXED** |
| C-8 | P2 | every nomination warning rendered twice | **PARTLY FIXED** — general case gone; the *closed-day* blocker is still doubled → **C2-6** |
| C-9 | P2 | setup checklist re-opens over the seat sheet | **PARTLY FIXED** — mid-game assignment no longer raises it; SETUP still does (known, fix-wave-3 queue); it now raises mid-game for a traveller under the wrong title → **C2-7** |
| C-10 | P2 | statements inert and absent from the log | **PARTLY FIXED** — they reach the log; the rows are still inert → **C2-8** |
| C-11 | P2 | execution that killed nobody missing from the log | **VERIFIED FIXED** |
| C-12 | P2 | stale traveller announcements never clear | **VERIFIED FIXED** (via D76) |
| C-13 | P2 | "On the block: X" survives the execution | **VERIFIED FIXED** |
| C-14 | P2 | rules-critical lines at 12 sp / 10 sp | **VERIFIED FIXED** |
| C-15 | P2 | vote panel not brought into view after the two ring taps | **STILL BROKEN** (improved but not closed) → **C2-9** |
| C-16 | P2 | dusk sheet leads with the irreversible button | **VERIFIED FIXED** (all three D77 states) |
| C-17 | P2 | kill sheet opens with its primary below the fold | **VERIFIED FIXED** |
| C-18 | P2 | Dusk (moon) button still in the top bar by day | **VERIFIED FIXED** |
| C-19 | P2 | "never say why" caption under both buttons | **VERIFIED FIXED** |
| C-20 | P2 | travellers contribute no reminder tokens | **VERIFIED FIXED** |
| C-21 | P2 | tie line names only one player in the panel | **VERIFIED FIXED** — but the strip now contradicts itself → **C2-10** |
| C-22 | P2 | ✓ on an empty WHAT WAS SAID | **VERIFIED FIXED** |

### Proof, finding by finding

**C-1 · VERIFIED FIXED.** Three ineligible-hand shapes, all refused now.
*Butler whose Master is down:* the chip toggles `CHECKED` and the tally stays
**0 of 4**, with `! Player 1's hand is up but does not count — their Master's
hand is down.` Tapping the Master's chip flips it to **2 of 4** in one step —
the Butler's hand joins the moment it becomes legal.
*Spent ghost vote:* the chip is not merely dimmed, it dumps `DISABLED` —
`#48 '<View>' … click,DISABLED` with the label `Begged ⊘ ×0`, plus
`⊘ Begged — ghost vote already spent.` and
`· Begged (Beggar) has no vote token left to spend.`
Screenshots `tools/emu/out/C2/01-butler-chip.png`,
`tools/emu/out/C2/02-master-votes.png`, `tools/emu/out/C2/30-ghost-tally.png`.

**C-2 · VERIFIED FIXED.** Every Trouble Brewing nomination now reads
`Nothing fires on this nomination.` The exile call on a traveller shows only
`Begged is a Traveller — this is an exile call, not an execution.`
`tools/emu/out/C_fix_day/emulator-5556/56-screenshot.png`,
`tools/emu/out/C2/22-exile-call.png`.

**C-3 · VERIFIED FIXED.** See C-1.

**C-4 · VERIFIED FIXED.** After the Virgin executed the nominator, all nine ring
seats dump `click,long,DISABLED`, the banner reads
`Player 2 was executed — the day is over.` beside **[Nominate anyway]**, and the
in-flight vote panel's **Lock in** dumps `DISABLED` above a **[Record it
anyway]** override — nothing is discarded silently. Proven by
`C2_day_closed` (73/73) and confirmed on the ordinary execution path in
`tools/emu/out/C2/08-nominate-after-close.png`.

**C-5 · VERIFIED FIXED.** `Execution.previewConsequences` is on the sheet before
the button: `! Player 4 was the Saint — EVIL WINS.` and
`! Player 3 (Undertaker) learns Player 4's character tonight.`
`tools/emu/out/C2/39-saint-exec-sheet.png`. The Vizier's day-immunity is
previewed the same way (`tools/emu/out/C2/53-exec-vizier.png`) and the engine
resolves it correctly — the strip afterwards reads *"Player 2 was executed and
survived"* with 12 alive unchanged.

**C-6 · VERIFIED FIXED.** MORNING BRIEFING, TRUE TODAY:
`Player 1 (Butler) may only vote if Player 2 votes — a hand raised alone does
not count.` `tools/emu/out/C_fix_day/emulator-5556/49-screenshot.png`.

**C-7 · VERIFIED FIXED.** `+ Token → Poisoned` on seat 3, then **[Remove]** once:
the row is gone on the next dump (the `Master` row below it is untouched).

**C-8 · PARTLY FIXED.** `Player 3 has already nominated today.` now renders once,
as a card with **[Allow anyway]**, with no duplicate bullet. But the closed-day
blocker is still doubled — filed below as **C2-6**.

**C-9 · PARTLY FIXED.** On day 2 of a running game, `Change… → Undertaker` on
seat 3 left the seat sheet in place with no checklist —
`tools/emu/out/C2/15-midgame-assign.png`. During SETUP it still raises over the
open seat sheet (`C_fix_day` step 23 waits on it) — that is the known
fix-wave-3 item and is **not** re-filed. What *is* new is **C2-7** below.

**C-10 · PARTLY FIXED.** The game log now carries
`Player 3 says: "Player 6 is the Imp"` under `DAY 1`
(`tools/emu/out/C2/11-game-log.png`). The row on the day screen is unchanged:
`#33 'Player 3 » “Player 6 is the Imp”' [69,1040][555,1083]` — no `click` flag,
no `✎`, no delete, no `✓ ✗ ?`. Filed as **C2-8**.

**C-11 · VERIFIED FIXED.** `Player 5 is executed and survives (the storyteller)`
in the log. `tools/emu/out/C2/11-game-log.png`.

**C-12 · VERIFIED FIXED**, via the D76 change rather than by deletion: an
announcement owed from an earlier dawn now renders
`• Announce: Begged is exiled. (still owed from dawn 2 — tick…)` instead of
repeating unmarked. The exiled Beggar's *standing fact* is gone — day 3's
briefing has one standing fact (the Butler), where day 2 had two.
`tools/emu/out/C2/27-dawn3.png`, `tools/emu/out/C2/28-briefing-day3.png`.

**C-13 · VERIFIED FIXED.** After **[Executed — but they don't die]** the strip
reads `Player 5 was executed and survived — the day is over.` with no residual
`On the block:` line; `C2_day_closed` asserts this with an `absent "On the
block"` step. `tools/emu/out/C2/07-exec-no-death.png`.

**C-14 · VERIFIED FIXED.** `grep -n "bodySmall\|labelSmall"
app/.../day/NominationPanel.kt` now returns **nothing** — the ring seat number
and its `† ◆ ✕ ✈` markers (`NominationPanel.kt:257,279`), the `⊘` marker
(`:572`) and every reason line are `labelMedium`/`bodyMedium`. The stat strip is
`bodyMedium` (`DayScreen.kt:330,345`). Only `DayScreen.kt:716` (the voters list
behind `▸ voters`) and `:823` (the unreachable `onDusk == null` fallback) are
still `bodySmall`.

**C-15 · STILL BROKEN** — see **C2-9**.

**C-16 · VERIFIED FIXED.** All three D77 states observed in one game:
nobody on the block → `NO EXECUTION — BEGIN NIGHT 3 →` primary with
`Begin night without recording` demoted (`tools/emu/out/C2/13-dusk-noexec.png`);
someone on the block → `EXECUTE PLAYER 5 & BEGIN NIGHT` with a
`• Player 5 is on the block and has not been executed.` BEFORE-YOU-MOVE-ON line
(`tools/emu/out/C2/05-dusk-sheet-block.png`); execution already spent →
`BEGIN NIGHT 2 →` and no secondary (`tools/emu/out/C2/12-dusk-spent.png`).

**C-17 · VERIFIED FIXED.** The kill sheet opens with
`[Record the death]` at `[53,1896][1027,2043]` and `[Cancel]` at
`[53,2065][1027,2191]`; `audit` clean, no scrolling needed.
`tools/emu/out/C2/16-kill-sheet.png`.

**C-18 · VERIFIED FIXED.** The day top bar is `Hide the grimoire · Undo · Redo ·
Menu`; no moon icon in any of the three games.

**C-19 · VERIFIED FIXED.** The caption is now scoped and sits under the second
button: `If they don't die, say "Player 4 is executed", then "Player 4 is still
alive." Never say why.` `tools/emu/out/C2/06-exec-sheet.png`.

**C-20 · VERIFIED FIXED.** With a Beggar seated, the seat sheet's `+ Token`
picker lists `Beggar → Token` in the **In play** group.
`tools/emu/out/C2/20-token-picker-traveller.png`.

**C-21 · VERIFIED FIXED.** Panel line:
`Tie at 5 — Player 6 and Player 7. No one is about to die. 6 to beat it.` — both
names. `tools/emu/out/C2/32-tie-line.png`. The strip's own counter now
disagrees with it; filed as **C2-10**.

**C-22 · VERIFIED FIXED.** On a fresh day `WHAT WAS SAID` carries no ✓; the tick
appears only once a statement is recorded (`1 recorded`).
`tools/emu/out/C_fix_day/emulator-5556/49-screenshot.png`.

---

## P1

### C2-1. **P1 · Dusk sheet** — the Vortox's "evil wins" is announced and then thrown away: the primary advances to night 2 with no win prompt

The dusk sheet correctly detects the ending and prints it in red twice
(**C2-2**). Tapping the primary **[NO EXECUTION — BEGIN NIGHT 2 →]** records the
no-execution and begins night 2. **No "Is the game over?" dialog is ever
raised**, and nothing on day 2 mentions that evil won on day 1 — the advisory is
gone. Every other ending in the app *is* actionable: executing the Saint raises
`Is the game over? / The Saint died by execution - the good team loses.` with
**[Declare evil victory]** / **[Keep playing]**
(`tools/emu/out/C2/40-saint-win.png`).

Cause: the dusk sheet renders `advisories` as plain `Text` in error colour
(`PhaseFlow.kt:483-492`) and has no per-advisory action, whereas the win dialog
at `GameExtras.kt:355-368` builds a `Declare evil/good victory` button from the
same `WinCheck.Advisory`.

- **Repro**
  ```sh
  ./emu.sh launch emulator-5556 --fresh
  ./scenario.py emulator-5556 C2_vortox_dusk        # 57/57
  ```
  (Sects & Violets, seat 1 = Vortox, night 1 skipped, day 1 closed with no
  execution. Step 56 is an `absent` assertion: nothing matching
  `Is the game over|Declare evil victory` exists anywhere in the tree.)
- **Expected** the ending the sheet just announced is offered where it fires —
  a **[Declare evil victory]** action on the advisory, or the same
  `Is the game over?` dialog the Saint raises, before or immediately after the
  night begins.
- **Actual** night 2 opens (`Everyone closes their eyes. Wait for quiet.`), the
  advisory is not repeated anywhere, and the only route left is
  Menu → *Declare evil victory*.
- **Screenshot** `tools/emu/out/C2_vortox_dusk/emulator-5556/51-audit.png`
  (the sheet) and `…/55-screenshot.png` (night 2, no prompt); also
  `tools/emu/out/C2/57-vortox-dusk.png`, `tools/emu/out/C2/58-vortox-win.png`
- **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/PhaseFlow.kt:483-492`
  (advisories rendered as text only) vs
  `app/src/main/java/com/clocktower/grimoire/ui/screens/GameExtras.kt:355-368`
  (the same advisory type with an action).

### C2-2 is a P2 (below) — the duplicate rendering of the same advisory.

### C2-3. **P1 · Day → dusk** — an exile the table voted for and the storyteller never carried out is invisible at dusk; the night runs with the traveller still alive and the execution threshold wrong

Locking in an exile at or above threshold records `5 votes · about to die` and
puts an **[Exile]** button on the nomination row — the exile itself is a
separate, explicit tap. If it is missed, **nothing anywhere warns about it**:

- the stat strip reads `No one is about to die.`;
- the DUSK card reads `No one is about to die. There is no execution today.`;
- the dusk sheet's **BEFORE YOU MOVE ON** section is *empty* — the same section
  that correctly says `• Player 5 is on the block and has not been executed.`
  for an un-executed execution.

Beginning the night then carries a wrong board: `Night 3 · 9/9 alive` with the
exiled traveller still seated, still holding their vote token and still counted
in `9 alive · 5 to execute` — where the correct board is 8 alive and a threshold
of 4.

- **Repro** (Trouble Brewing, 8 seats, day 2)
  ```sh
  ./ui.py emulator-5556 tap "^Menu$"
  ./ui.py emulator-5556 tap "A traveller joins"      # name + Beggar + Seat them
  ./ui.py emulator-5556 tap "^Day$"
  ./ui.py emulator-5556 tap "^Nominate$"
  # tap a living seat, then the traveller; scroll to the chips, vote to the
  # threshold, then:
  ./ui.py emulator-5556 tap "Lock in"                # "Begged is EXILED (5 of 5)"
  ./ui.py emulator-5556 tap "^DUSK$"
  ./ui.py emulator-5556 tap "Everyone, eyes closed"  # BEFORE YOU MOVE ON is empty
  ./ui.py emulator-5556 tap "NO EXECUTION — BEGIN"
  ./ui.py emulator-5556 find "alive"                 # "Night 3 · 9/9 alive"
  ```
- **Expected** the dusk sheet's BEFORE YOU MOVE ON lists the passed-but-uncarried
  exile the way it lists an un-executed block ("Begged was exiled and has not
  left the game"), or the exile is applied when the day closes.
- **Actual** silence; the traveller survives into the night and skews the alive
  count and the next day's execution threshold.
- **Screenshot** `tools/emu/out/C2/23-dusk-pending-exile.png` (the empty
  BEFORE YOU MOVE ON), `tools/emu/out/C2/25-exile-sheet.png` (the sheet that was
  skipped)
- **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/PhaseFlow.kt`
  — `PhaseGuards.onBlockId` / the dusk request only knows about
  `ExecutionRecord`, not about a `NominationResult.ABOUT_TO_DIE` on an
  `isExile` nomination; and
  `engine/src/main/kotlin/com/clocktower/engine/DayRules.kt` (`nominationsClosed`
  / the dusk briefing builder in `Briefings.kt`) has no "exile owed" fact.

---

## P2

### C2-2. **P2 · Dusk sheet** — the win advisory is printed twice, verbatim

`No execution today and the Vortox is alive and sober — evil wins.` appears as
the sheet's own red headline **and** again as the first `BEFORE YOU MOVE ON`
bullet. `WinCheck.duskCheck` feeds both the `advisories` list rendered at
`PhaseFlow.kt:483-492` and the briefing section rendered immediately below it.

- **Repro** `./scenario.py emulator-5556 C2_vortox_dusk` — step 52's `find`
  returns two nodes:
  ```
  #10 'No execution today and the Vortox is alive and sober — evil…' [183,788][897,895]
  #12 '• No execution today and the Vortox is alive and sober — ev…' [183,995][897,1088]
  ```
- **Expected** one copy.
- **Actual** two, one of them bulleted, ~200 px apart.
- **Screenshot** `tools/emu/out/C2/57-vortox-dusk.png`
- **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/PhaseFlow.kt:483-492`
  — drop the advisory loop, or filter the briefing's BEFORE_YOU_MOVE_ON lines
  whose text an advisory already carries.

### C2-4. **P2 · Execution sheet** — a raw `EffectKind` enum name is shown to the storyteller

`! Player 2 carries DAY_IMMUNE — check whether it stops this execution.`

`Execution.kt:488` builds the line as
`"$name carries ${effect.label.ifEmpty { kind.name }} — …"`; the Vizier's
protection effect has an empty `label`, so the enum constant leaks through.

- **Repro**
  ```sh
  ./emu.sh launch emulator-5556 --fresh
  ./scenario.py emulator-5556 C2_grind_vizier    # 96/96; step 95 asserts it
  ```
- **Expected** the effect's human label ("cannot die during the day"), or the
  row suppressed when the funnel has already credited the save.
- **Actual** `DAY_IMMUNE`.
- **Screenshot** `tools/emu/out/C2/53-exec-vizier.png`,
  `tools/emu/out/C2_grind_vizier/emulator-5556/93-screenshot.png`
- **Suspect** `engine/src/main/kotlin/com/clocktower/engine/Execution.kt:488`
  (and the missing `label` on the Vizier's `DAY_IMMUNE` effect in
  `rules/` / `StatusEffects.kt`).

### C2-5. **P2 · Execution sheet** — the same protection is stated three times

The Vizier's sheet reads, in order:

```
Player 2 cannot die during the day.
Say: 'Player 2 was executed… and remains alive.' Do not say why.
! Player 2 carries DAY_IMMUNE — check whether it stops this execution.
! Player 2 cannot die during the day.
```

Three renderings of one fact on the last, most important confirmation screen.
The dedupe at `Execution.kt:477-482` compares
`normalizeId(effect.sourceCharacterId)` with `normalizeId(record.preventedBy)`
and does not match here, so the protection row is emitted alongside the
`SURVIVED` row *and* the character consequence.

- **Repro** as C2-4; step 96's `find "cannot die during the day"` returns two
  nodes, and `DAY_IMMUNE` is the third copy.
- **Screenshot** `tools/emu/out/C2/53-exec-vizier.png`
- **Suspect** `engine/src/main/kotlin/com/clocktower/engine/Execution.kt:460-505`

### C2-6. **P2 · Nomination check card** — on a closed day the same blocker is printed twice, with two `[Allow anyway]` buttons

`DayRules.checkNomination` appends the blocker from `canNominate`
(`DayRules.kt:187`) and again from `canBeNominated` (`DayRules.kt:247`) — the two
strings are byte-identical — into one un-deduplicated `blockers` list
(`DayRules.kt:262-271`). The panel renders one row per blocker, so the card shows:

```
Nominations are closed today — the day's execution is settled.   [Allow anyway]
Nominations are closed today — the day's execution is settled.   [Allow anyway]
```

The same screen also carries the sentence three more times in other words —
the stat strip, the ring banner beside **[Nominate anyway]**, and
`Nominations are closed. Tap Nominate anyway to take one regardless.` (a `find`
for `Player 2 was executed — the day is over` returns three nodes). And the two
override buttons on the one panel are labelled differently: **[Allow anyway]** on
the check card, **[Record it anyway]** on the vote panel, for the same state.

- **Repro**
  ```sh
  ./emu.sh launch emulator-5556 --fresh
  ./scenario.py emulator-5556 C2_day_closed     # 73/73; steps 71-72 assert it
  ```
- **Expected** one blocker row, one override.
- **Actual** two identical rows and two identical buttons.
- **Screenshot** `tools/emu/out/C2/37-double-closed-warning.png`,
  `tools/emu/out/C2_day_closed/emulator-5556/70-screenshot.png`
- **Suspect** `engine/src/main/kotlin/com/clocktower/engine/DayRules.kt:262-271`
  (`blockers` needs `.distinct()`, or the two callers need distinct wording).

### C2-7. **P2 · Setup checklist** — it raises itself mid-game titled "Before the first night", with setup-only caption

Seating a traveller on **day 2** raises the checklist sheet over the grimoire
with the header `Before the first night` / `0 of 1 done`, the row
`Begged's alignment · Is Begged good or evil?`, and the footnote
`"Begin night" still works with rows outstanding — the guard…`. The obligation is
real; the framing is not — it is day 2, there is no "first night", and the
caption talks about a guard the storyteller passed two phases ago.

The title is a hard-coded string at `GameExtras.kt:606`.

- **Repro**
  ```sh
  # any running game, day 2
  ./ui.py emulator-5556 tap "^Menu$"
  ./ui.py emulator-5556 tap "A traveller joins"
  # name, character, [Seat them]
  ```
- **Expected** a title that fits the phase ("Still to decide", "Setup
  decisions"), and the first-night caption only during SETUP.
- **Actual** `Before the first night` on day 2.
- **Screenshot** `tools/emu/out/C2/19-checklist-midgame.png`
- **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/GameExtras.kt:606`
  (and the caption a few lines below).

### C2-8. **P2 · WHAT WAS SAID** — recorded statement rows are still inert

The composer and the log are both good now, but the row itself is dead:

```
#33 'Player 3 » “Player 6 is the Imp”' [69,1040][555,1083] @(312,1061)
```

no `click` flag, no `✎`, no delete, no `✓ ✗ ?`. A mis-attributed or mistyped
statement can only be left standing. `ux/day-screen.md` §C asks for an edit
affordance on every row and tri-state truth chips where truth matters.

- **Repro**
  ```sh
  ./ui.py emulator-5556 tap "\+ Say"
  ./ui.py emulator-5556 tap "^Player 3$"
  ./ui.py emulator-5556 type "Player 6 is the Imp"
  ./ui.py emulator-5556 tap "^Add$"
  ./ui.py emulator-5556 tap "WHAT WAS SAID"
  ./ui.py emulator-5556 dump          # the row has no click flag
  ```
- **Screenshot** `tools/emu/out/C2/10-said-rows.png`
- **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/day/DayCards.kt`
  (the statement row builder).

### C2-9. **P2 · Nomination panel** — the vote chips and Lock-in are still one swipe below the fold after the two ring taps (C-15 not closed)

`DayScreen.kt:194-199` now does `listState.animateScrollToItem(index)` on the
NOMINATIONS row, which is a real improvement — the check card is fully legible
where it used to be a 26 px sliver. But the pinned ring leaves the LazyColumn a
viewport of only `y 1178..1938` (760 px), and the NOMINATIONS card is far taller
than that, so scrolling its *top* to the viewport top still leaves the countable
part below the fold. Measured on an 8-seat game the instant after the two taps:

```
#37 '<View>' [0,1178][1080,1938]  scroll        <- the whole viewport
    #49 '0'   [455,1632][527,1780]              <- the tally, visible
    #52 '<View>' [69,1925][284,1938]  click     <- first chip row: 13 px of 126
    …                                              Lock in: not rendered at all
```

Every nomination therefore still costs one deliberate swipe between "who
nominates" and a countable tally, and the swipe has to be started inside the
list — `ui.py swipe down` centres on the safe area at `y=1226`, which is *above*
the container, and does nothing (harness note, not an app bug).

- **Repro**
  ```sh
  ./emu.sh launch emulator-5556 --fresh
  ./scenario.py emulator-5556 C_fix_day    # step 56/57: chips at y 1925..1938
  ```
- **Expected** `ux/day-screen.md` §E: "the tally sheet slides up automatically" —
  chips and Lock-in reachable without a swipe (scroll to the *vote panel*, not
  the card; or collapse/shrink the ring once a nominee is picked).
- **Actual** one swipe every time.
- **Screenshot** `tools/emu/out/C_fix_day/emulator-5556/56-screenshot.png`,
  `tools/emu/out/C2/29-vote-panel-clip.png`
- **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/DayScreen.kt:194-199`

### C2-10. **P2 · Stat strip** — "· N to beat" contradicts the tie line "N+1 to beat it" in the same strip

After a tie at 5 the strip reads, on two adjacent lines:

```
8 alive · 4 to execute · 5 to beat
Tie at 5 — Player 6 and Player 7. No one is about to die. 6 to beat it.
```

`DayModel.kt:122` appends `" · $highest to beat"` (the standing high-water),
`DayModel.kt:174` writes `"${highest + 1} to beat it."` (the number a vote must
reach). Same words, different meanings, six lines apart in the same file and one
line apart on screen.

- **Repro** two nominations on the same day with equal tallies, then read the
  strip.
- **Expected** one meaning for "to beat" — either both `highest` or both
  `highest + 1`.
- **Actual** 5 and 6.
- **Screenshot** `tools/emu/out/C2/33-stat-strip-tie.png`
- **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/day/DayModel.kt:122,174`

### C2-11. **P2 · "A traveller joins" dialog** — `[Seat them]` and `[Cancel]` sit underneath the soft keyboard

The Name field autofocuses, so the IME is up for the whole flow (name →
sits-after → character). The dialog *does* shrink for the keyboard — its scroll
container ends at `y=1458` — but the pinned action row lands at
`y 1522..1648`, and the IME starts at `y≈1506`. Both buttons are completely
covered, while uiautomator still reports them as tappable, so a scripted or
mis-aimed tap lands on the keyboard instead. Dismissing the IME (back) moves the
row to `y 1835..1961` and both become reachable.

The `+ Say` composer on the same screen does *not* have this problem: with the
keyboard up its `[Claims…]`/`[Add]` row sits at `y≈1329`, well clear.

- **Repro**
  ```sh
  ./ui.py emulator-5556 tap "^Menu$"
  ./ui.py emulator-5556 tap "A traveller joins"
  ./ui.py emulator-5556 tap "^Name$"
  ./ui.py emulator-5556 type "Begg"
  ./ui.py emulator-5556 tap "^Beggar$"
  ./ui.py emulator-5556 find "Seat them"      # reported at [608,1522][897,1648]
  ./ui.py emulator-5556 screenshot            # …but the keyboard covers it
  ```
- **Expected** the action row clears the IME (`imePadding` on the row, not only
  on the scrolling content), the way the statement composer does.
- **Actual** both buttons are behind the keyboard until the storyteller knows to
  dismiss it.
- **Screenshot** `tools/emu/out/C2/17-traveller-dialog.png` (covered) vs
  `tools/emu/out/C2/18-traveller-nokbd.png` (reachable)
- **Suspect** the traveller dialog in
  `app/src/main/java/com/clocktower/grimoire/ui/screens/GameExtras.kt`
  (the `AlertDialog` confirm/dismiss row).

### C2-12. **P2 · Secret voting** — the panel prints "Player 6 is about to die." in the clear under the `•••` tally

With a sober Organ Grinder the tally is `•••` behind *hold to peek*, the strip
says `Someone is about to die.`, the recorded row says `••• votes · •••`, and
the button is deliberately labelled **[Lock in silently]** rather than
`Lock in: X is ON THE BLOCK (n of m)`. Between the two, the panel's own outcome
line is plain text:

```
#74 'Player 6 is about to die.' [69,1712][510,1761]
```

Anyone who can read the phone learns the outcome without the peek, which is the
one thing the rest of the surface is built to prevent.

- **Repro**
  ```sh
  ./emu.sh launch emulator-5556 --fresh
  ./scenario.py emulator-5556 C2_grind_vizier   # any nomination reaching threshold
  ```
- **Expected** the outcome line concealed like the tally (`•••`, or "the tally
  is in — lock it in silently"), or revealed only by the same hold-to-peek.
- **Actual** plain text.
- **Screenshot** `tools/emu/out/C2/49-secret-outcome.png`
- **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/day/NominationModel.kt`
  (`outcomeLine` is not gated on `secret`, while the tally and the lock-in label
  both are — `NominationPanel.kt:441` documents the intent).

### C2-13. **P2 · Dusk sheet** — the button row wraps badly at its widest state

With the advisory present the sheet's primary wraps its arrow onto a line of its
own and the dismiss button splits across two lines:

```
NO EXECUTION — BEGIN NIGHT 2
→
Begin night without recording        Not
                                     yet
```

`audit` is clean (nothing is off-screen or overlapping), so this is purely the
row being asked to hold two long labels plus a third at the same time.

- **Repro** `./scenario.py emulator-5556 C2_vortox_dusk`, step 51
- **Screenshot** `tools/emu/out/C2/57-vortox-dusk.png`,
  `tools/emu/out/C2_vortox_dusk/emulator-5556/51-audit.png`
- **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/PhaseFlow.kt`
  (the `AlertDialog` confirm/dismiss slots — give the secondary its own row, as
  the on-the-block variant already does).

### C2-14. **P2 · Win check wording** — the Saint's ending uses a hyphen where every other line uses an em dash

```
The Saint died by execution - the good team loses.
```

`WinCheck.kt:383` is the only ` - ` in the file; every neighbouring string uses
`—`. It reads as a typo on the one screen that ends a game.

- **Repro** execute a living Saint; the `Is the game over?` dialog carries the
  line.
- **Screenshot** `tools/emu/out/C2/40-saint-win.png`
- **Suspect** `engine/src/main/kotlin/com/clocktower/engine/WinCheck.kt:383`

---

## `audit` results

`ui.py audit` was run on every new screen. Node counts are quoted so none of
these is a vacuous (0-node) pass.

| screen | nodes | result |
|---|---|---|
| Day screen, nomination panel open (8 seats) | 23 | OK |
| Day screen, nomination ring (12 seats) | 26 | OK |
| Grimoire, 8 seats | 25 | OK |
| Grimoire, 12 seats | 27 | OK |
| Grimoire, 15 seats | 30 | OK |
| Dusk sheet — on the block | 4 | OK |
| Dusk sheet — no execution | 3 | OK |
| Dusk sheet — Vortox advisory | 3 | OK |
| Execution sheet (Saint) | 4 | OK |
| Execution sheet (Vizier) | 15 | OK |
| Exile sheet | 3 | OK |
| Kill sheet | 15 | OK |
| Game log dialog | 1 | OK |
| Seat sheet | 10 | OK |
| Token picker (with a traveller in play) | 20 | OK |
| Traveller-join dialog | 15 | OK |
| Statement composer (`+ Say`) | 19 | OK |
| `Claims…` grid | 27 | OK |
| Day screen after a settled day (`C2_day_closed`) | 22 | OK |
| **Seat sheet → `Change…` character picker** | 14 | **1 violation** — see below |

The one violation is the already-queued `SeatSheet.kt` character picker, so it
is **not** re-filed:

```
=== SAFE-AREA VIOLATIONS (1) ===
  #61 '<View>' [53,2225][1027,2369] @(540,2297)  click
      - bottom 53px under the navigation/gesture inset (home indicator)
```

(the last list row — `Undertaker` — in the `Change…` overlay). The numbers have
moved since the Fix-F report ("top 8 px under the status bar + bottom 21 px")
but it is the same control and the same cause.

The known **8-seat grimoire zoom overlap** did **not** reproduce this run: the
8-seat canvas audited clean at 25 nodes with no floating `[Zoom in]` button in
the tree.

D82 is holding everywhere: the bottom `NavigationBar` tabs end at exactly
`y=2316` on every in-game screen (`#87 [0,2106][255,2316]`), so the 21 px
gesture-inset overrun the first wave saw is gone.

---

## What worked

Almost everything the first wave asked for landed, and several things are now
better than the spec asked.

- **The whole ineligibility model.** An ineligible hand is not merely warned
  about — a spent ghost vote dumps `DISABLED` and cannot be tapped at all, and a
  Butler hand that *is* tappable is tallied at zero with a plain-English reason
  that names the Master. The tally flips the moment the hand becomes legal.
- **D75, end to end.** A closed day disables the ring, the vote panel and the
  lock-in, always with the reason and always with an explicit override
  (**[Nominate anyway]**, **[Allow anyway]**, **[Record it anyway]**). Nothing is
  discarded silently anywhere I could find.
- **The execution sheet as a real preflight.** Saint, Undertaker, Vizier
  day-immunity and the survival script are all on the sheet *before* the button.
- **All three D77 dusk states**, with the record as the primary and the label
  naming it.
- **The Beggar, properly modelled.** `Begged (Beggar) has no vote token left to
  spend.` on the stat strip, on the vote panel and on the chip (`Begged ⊘ ×0`),
  and the briefing spells the rule out before the first vote.
- **A real Organ Grinder drives secret voting** — not just the house-rule
  toggle. `SECRET`, `Eyes closed, everyone. (If asked: an Organ Grinder is in
  play.)`, the `•••` tally, hold-to-peek revealing `6`, **[Lock in silently]**,
  `Someone is about to die.` and `••• votes · •••` in the record.
- **The Vizier** on an imported script: two standing facts in the briefing, a
  nomination row with **[Apply it]** / **[Skip]**, a day-immunity preview on the
  execution sheet, and a correct resolution ("executed and survived", alive count
  unchanged).
- **The Vortox** standing facts and its dusk detection (the ending itself is
  C2-1).
- **Script import.** Pasting a script-tool JSON array into "Import script (paste
  link or JSON)" worked first time and made off-script characters reachable from
  the seat sheet — which is what blocked the first wave's Organ Grinder testing
  entirely.
- **The game log** now reads like a transcript: statements, nominations with
  voter names and thresholds, executions including "executed and survives",
  traveller joins and exiles, identity changes, deaths, grouped `DAY n` /
  `NIGHT n`.
- **Mid-game character changes** are handled properly — assigning an Undertaker
  on day 2 does not raise the checklist, and dawn 4 offered
  `Re-run Player 5's first-night information (Chef)` for a Chef assigned on
  day 3.
- **Undo** across a phase boundary put the game back into day 2 with Redo armed.
- **The timer** starts from the `1m/2m/3m/5m/8m` row and survives
  Day → Grimoire → Script → Day (2:00 → 1:57 → 1:26).
- **Ring geometry** at 8, 9, 12 and 15 seats: thresholds 4 / 5 / 6 / 8, vote
  order clockwise from the nominee's left every time, and `audit` clean at every
  size.

## What I could not reach

- **A 10-seat ring.** Covered at 8, 9 (traveller), 12 and 15; 10 was the first
  wave's size and was not re-driven.
- **Witch, Fearmonger, Goblin, Psychopath *nomination* rows.** The Goblin gate
  is only observable negatively (Trouble Brewing says "Nothing fires"); the
  Psychopath surfaces as a briefing to-do (`Ask Player 3 (Psychopath) before you
  open nominations.` with an **[Open seat]** action), not as a nomination row, so
  I could not check the row itself. A script with a Goblin, Witch and Fearmonger
  is the obvious next import.
- **The Mayor bounce.** The Mayor was seated in game 2 but never on the block and
  never the target of a night kill.
- **The Zombuul**, and an execution under a Vortox (only the no-execution branch
  was driven).
- **Hold-to-peek on a *house-rule* secret vote** (only on a real Organ Grinder;
  `G_fix_secret_votes` covers the house-rule path and passes 84/84).
- **The Virgin's trigger after a mid-game identity change** is worth a ruling
  rather than a bug: `DayRules.kt:338-341` gates on
  `state.nominations.any { it.nomineeId == nominee.id }` — "first time EVER" —
  so a seat that was nominated on day 1 and *becomes* the Virgin later never
  fires, and the check card says only `Nothing fires on this nomination.` with no
  hint that a Virgin is being nominated. Reproduced deliberately; not filed,
  because the reading of the rule is a design decision, but the silence is worth
  a line of copy either way.
