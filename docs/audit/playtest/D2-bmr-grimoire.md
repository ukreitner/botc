# Re-test D2 — the user's Bad Moon Rising session, plus the grimoire

Second playtest fleet. Driven on `emulator-5558` with `tools/emu/`, build
**978692b** (branch tip: fix waves 1–2 + the wave-2 polish; APK built
2026-08-29 01:37, two minutes after the commit).

Read alongside [`D-bmr-grimoire.md`](D-bmr-grimoire.md) — this file **re-tests**
every one of that tester's findings D-1..D-20 and all five of the user's own
complaints, then goes past them. It never edits the first tester's file.

Two games were played by hand through the real UI:

| game | bag | what it was for |
|---|---|---|
| **G1** — 12 seats, the user's own session | Devil's Advocate · Sailor · Chambermaid · Gossip · Grandmother · Professor · Tea Lady · Exorcist · Fool · Lunatic · Pukka · Godfather | the five complaints, D-1..D-20, night 1 → day 3 |
| **G2** — 8 seats | Gossip · Innkeeper · Courtier · Minstrel · Fool · Tinker · Assassin · Zombuul | the characters G1's bag does not hold, and the Gossip gate the first tester could not reach |

plus a ring sweep at **5 / 8 / 12 / 13 / 15 / 16** seats.

Scenarios written (`tools/emu/scenarios/`):

| file | what it does |
|---|---|
| `D2_bmr8_setup.py` | new BMR game, 8 seats pasted, start empty, audits the 8-seat ring |
| `D2_bmr8_assign.sh` | assigns G2's eight characters seat by seat |
| `D2_courtier_leak.py` | **D2-2 (P0)** — walks night 1 to the Courtier and asserts the leak, verbatim |
| `D2_rings.py` | 5 / 15 / 16-seat rings from `--fresh`, audited unzoomed |

Counts: **4 P0 · 0 P1 · 6 P2** (new findings only). Every one of D-1..D-20 is
**VERIFIED FIXED**; none regressed.

---

## Headline

The fix waves did their job. All twenty earlier findings hold, and the fifth
user complaint — the Lunatic, which the first tester could only call "half
works" — is now correct end to end. The Gossip's `Was it true?` gate, the one
thing the first tester explicitly could not separate from a masking gate, is
present and correct.

The four new P0s are all **new ground**, not regressions of D-1..D-20:

* two of them (**D2-2 Courtier**, **D2-3 Exorcist**) are the same one-line
  defect — `NightRows.primaryLabel` turns *any* `InfoResult` into
  `SHOW "…" TO <holder>`, so two storyteller-only calculations are rendered as
  instructions to show the player. The Courtier one hands the Courtier the
  entire grimoire;
* **D2-1** is the preview half of the Exorcised-Pukka rule disagreeing with the
  resolution half, so the gold button says `DEV SURVIVES — NOBODY DIES` and
  then kills Dev;
* **D2-4** is a completed night row re-drawing itself as `skipped · dead — no
  ability`, inviting the storyteller to run it a second time.

---

## Verdict table — the first tester's D-1..D-20

| # | sev | finding | verdict | proof |
|---|---|---|---|---|
| D-1 | P0 | a new night opens on the **Dawn** step | **VERIFIED FIXED** | night 2 opened `NIGHT 2 · step 1 / 12`, night 3 `NIGHT 3 · step 1 / 12` — the Dusk card both times · `out/D2/22-dusk1.png` |
| D-2 | P0 | night-1 Lunatic row auto-skipped | **VERIFIED FIXED** | step 3/11 is a real row: `Po · Jonas · seat 10`, banner *"HAND OVER THE ILLUSION — wake Jonas, act as if they are the Po: THESE ARE YOUR MINIONS, point at Ana, Lena, then their three bluffs."*, primary `DONE — NEXT STEP`, no gate · `out/D2/08-n1-lunatic.png` |
| D-3 | P0 | Demon never told who the Lunatic is | **VERIFIED FIXED** | step 4 Demon info carries *"Also show the Demon who the LUNATIC is (Jonas) — the Demon can mirror their fake kills."* and a `SHOW: THE LUNATIC IS JONAS` card · `out/D2/09-n1-demon-info.png` |
| D-4 | P0 | Godfather's first-night Outsider block repeats | **VERIFIED FIXED** | night-2 card (run anyway) is only *"An Outsider died today. The Godfather points at any player: that player dies. Only ONE kill…"* — no `Outsiders in play:`, no `SHOW:` chips; and the primary `DONE — NEXT STEP` is present after the gate answers · `out/D2/26-n2-godfather.png` |
| D-5 | P1 | `Remove`/`Suspend` do nothing on a hand-placed token | **VERIFIED FIXED** | `+ Token → Mad` on Cleo: `Suspend` → `Mad (turned over)` + button becomes `Restore`; `Restore` → `Mad`; `Remove` → gone, STATUS back to the Sailor's token only |
| D-6 | P1 | seat circle overlaps the vote chips | **VERIFIED FIXED** | ring ends `y=1168`, chips start `y=1193`; `audit` on the nomination panel and on a live tally: `overlap: OK` · `out/D2/19-day1-votes.png` |
| D-7 | P1 | Spy read-only buttons off-screen, `back` swallowed | **VERIFIED FIXED** | both stages `safe area: OK`; `tap` reached `HAND IT OVER` at (442,1906) and `DONE — BACK TO THE SHEET` at (539,1906); `back` in stage 2 exits to the privacy cover · `out/D2/34-spy-stage2.png` |
| D-8 | P1 | Professor re-run sorts after Dawn | **VERIFIED FIXED** | sheet grew 12 → 13; the inserted row is `step 12 / 13` (`out of order — this became true after their slot`), Dawn is 13 · `out/D2/28-n2-rerun-order.png` |
| D-9 | P1 | Pukka's standing victim dies with nothing on the card | **VERIFIED FIXED** | night-2 banner *"Ben dies now — poisoned by the Pukka last night. Shroud them and announce the death at dawn."*, primary `DEV — POISONED · BEN DIES` (press-and-hold) · `out/D2/24-n2-pukka.png`, `out/D2/25-n2-pukka-primary.png`. **But see D2-1** — the exorcised variant now contradicts itself. |
| D-10 | P1 | checklist unreachable; `Fix setup` does not open it | **VERIFIED FIXED** | overflow menu entry **1** is `Before the first night…`; and pressing `Fix setup` on the begin-night guard opens the checklist sheet at the outstanding rows (`0 of 4 done`) |
| D-11 | P2 | checklist `Close` off-screen on first open | **VERIFIED FIXED** | `Close` at `[53,2065][1027,2191]` @(540,2128); sheet `audit`: `safe area: OK` · `out/D2/01-checklist.png` |
| D-12 | P2 | inert effect drawn as a full-strength pip | **VERIFIED FIXED** | seat 3 STATUS reads `Drunk (not in force)` / `Sailor · expires at dusk · doing nothing — Sailor's ability…`, and the content-desc is `Seat 3, Cleo, Chambermaid, alive, good, tokens: Drunk (not …` — no `drunk or poisoned` · `out/D2/13-seat3-inert.png` |
| D-13 | P2 | `Change…` wraps to `Chang / e…` | **VERIFIED FIXED** | one line, `Change…` at `[663,2219][811,2277]`; `+ Token` one line too |
| D-14 | P2 | `Did a Outsider die today?` | **VERIFIED FIXED** | the gate now reads `NOTHING TO DO — nobody died today`; the ungrammatical question is gone |
| D-15 | P2 | off-script `Did Erin claim to be the Goblin?` | **VERIFIED FIXED** | BMR day 1 and day 2 nomination card: `Nothing fires on this nomination.` · `out/D2/18-day1-nominated.png` |
| D-16 | P2 | `Removed: Died Today (Undertaker)` with no Undertaker | **VERIFIED FIXED** | `Execution.diedTodayOwner` (`Execution.kt:378-384`) resolves the owner from the script's token registry — its KDoc names this finding. In the BMR token picker the label sits under **Godfather** and **Zombuul**, never Undertaker · `out/D2/14-token-picker.png` |
| D-17 | P2 | `Announce: X is alive again` repeats every dawn | **VERIFIED FIXED** | dawn 3 renders `• Announce: Ben is alive again. (still owed from dawn 2 — t…)` · `out/D2/33-dawn3.png` |
| D-18 | P2 | Grandchild picker offers the Grandmother | **VERIFIED FIXED** | 8 candidates — Ben, Cleo, Dev, Gita, Iris, Hal, Finn, Jonas. Erin (the holder) absent; the three evil seats absent; the good Outsider (Jonas, Lunatic) correctly present · `out/D2/02-grandchild-picker.png` |
| D-19 | P2 | Grandchild not pre-selected, primary `DISABLED` | **VERIFIED FIXED** | night-1 step 9 opens with Finn already chosen, shows `Finn is the Fool`, `SHOW: FOOL`, and an **enabled** `SHOW “FOOL” TO ERIN` · `out/D2/12-n1-grandmother.png` |
| D-20 | P2 | resolving a step jumps the pointer back to step 1 | **VERIFIED FIXED** | every resolve in ~30 steps across three nights advanced to the next *unfinished row below* (6→7, 7→8, 8→9, 5→6, …) or skipped only gated rows (6→9 with 7/8 both `⊘`); never back to 1 |

**Also verified from the fix waves' own claims:** P-4 (bottom tab bar ends at
`y=2316` exactly, on every in-game screen), P-5 (`fabled +` `[935,315][1061,441]`
and Search `[493,441][1059,588]` share an edge, `overlap: OK`), P-9 (the
Grandmother's own seat is absent from her night picker), E-1 (no bag-legality
rows in the mid-game checklist after a traveller joined on day 3), D69
(below, under "What worked"), D77/D78 (dusk button semantics, checklist opener),
D81 (`[Undo]` on collapsed rows; idempotent primaries).

---

## The five user complaints

| # | complaint | verdict |
|---|---|---|
| 1 | **Pukka** — poison tonight, death next night | ✅ **VERIFIED** in the UI, with one new P0 on the exorcised path (D2-1) |
| 2 | **Devil's Advocate** — protected player survives execution | ✅ **VERIFIED end to end**, including the dusk-path execution the first tester could not reach |
| 3 | **Gossip** — a true public statement kills that night | ✅ **VERIFIED**, including the `Was it true?` gate the first tester could not reach |
| 4 | **Professor** — resurrection once per game | ✅ **VERIFIED**, and the re-run now sorts before Dawn |
| 5 | **Lunatic** — night-1 hand-over | ✅ **VERIFIED** — was "half works"; all three halves are now right |

**1 · Pukka.** Night 1: single-target picker, primary `BEN — POISONED`, no kill
offered. Night 2: banner *"Ben dies now — poisoned by the Pukka last night.
Shroud them and announce the death at dawn. The Lunatic (Jonas) chose Cleo."*,
primary `DEV — POISONED · BEN DIES` behind a press-and-hold; pressing it takes
12 → 11 alive and dawn 2 announces it. Night 3 under an Exorcist the gate is
`PART OF THIS STEP ONLY — The Exorcist silenced them: no choice tonight…` and
Dev **does** die (dawn 3: `Announce: Dev died.`, `Dev dies — killed by the
Demon`) — which is the right rule (lead D63) but **not** what the button on that
card promised. See **D2-1**.

**2 · Devil's Advocate.** Night 1 `LENA — SURVIVES EXECUTION`; the token shows on
the seat. Executing Lena on day 1 gives the kill sheet
*"Lena survives execution today. Say: 'Lena was executed… and remains alive'"*,
the warning `! Lena carries Survives Execution — check whether it stops…`, and
the primary `LENA IS EXECUTED — AND LIVES`; the day closes with
`Lena was executed and survived — the day is over.` and 12/12 still alive.
Dusk 1 sweeps it: `Removed: Survives Execution (Devil's Advocate) from Lena.`
Night 2 the red banner `Chosen last night: Lena — not again tonight.`, the
picker header `WHO DID THEY CHOOSE? (NOT LENA — CHOSEN LAST NIGHT)` and Lena in
a collapsed `⌄ 1 they cannot choose` annotated `last night · chosen last night`.
**New:** the first tester could not confirm the **dusk-path** execution honours
the protection. It does — `DUSK → Execute Erin` opens the same sheet with
`ERIN IS EXECUTED — AND LIVES` · `out/D2/30-dusk2-execute.png`.

**3 · Gossip.** Day 1 nudges `☐ Record Dev's public statement today (Gossip).`
with a `Record it` button; `+ Say → Dev` auto-selects `Recorded as: Gossip`; the
row reads `Dev » Gossip: "There is no Minion sitting next to me"` with `✓ ✗ ?`.
**The gap is closed:** in G2, with a *living, sober* Gossip and the verdict left
unjudged, the night-2 row shows the conditional gate
`Was it true? “Hana is evil”` with `True — a player dies` / `False — nobody
dies`, above the banner `Yesterday's statement: “Hana is evil”`. Answering
`True` opens `WHO DIES? “HANA IS EVIL”` · `out/D2/43-gossip-gate.png`.
The first tester saw the statement asserted true only because a *stronger* gate
(`dead — no ability`) had already fired and `[Run anyway]` bypasses the whole
`Gates.all` chain — `RulesBadMoonRising.kt:420-441` is correct.

**4 · Professor.** Night 2 offers only the dead, hold-to-confirm `CONFIRM: BEN`;
Ben returns (11 → 12 alive) with an `Alive` token, Gita gets `No Ability`, the
sheet grows 12 → 13 and the inserted first-night re-run is **step 12 of 13**,
before Dawn. Dawn 2 says `Announce: nobody died.` + `Announce: Ben is alive
again.` and, under DO NOT SAY, `Do not say why Ben is alive again.`

**5 · Lunatic.** All three halves now hold:
*night-1 hand-over* — step 3/11 is a real row with the illusion banner and
`HAND OVER THE ILLUSION — wake Jonas, act as if they are the Po: THESE ARE YOUR
MINIONS, point at Ana, Lena, then their three bluffs.`;
*the Demon is told who the Lunatic is* — DEMON_INFO carries
`Also show the Demon who the LUNATIC is (Jonas) …` plus a
`SHOW: THE LUNATIC IS JONAS` card;
*the nightly choice is told to the Demon* — the Pukka's night-2 banner reads
`The Lunatic (Jonas) chose Cleo.` and on night 3, before the Lunatic has acted,
`The Lunatic (Jonas) has not chosen anybody yet tonight.`;
*the believed-Demon action never kills* — picker `WHO DID THEY CHOOSE? (NOTHING
HAPPENS)`, `CONFIRM: CLEO`, alive count unchanged at 12/12.

---

## P0

1. **P0 · Night sheet, Exorcised Pukka** — the card says the victim dies, the
   gold button says they survive, and then they die
   - **Repro**
     ```sh
     # G1, night 3. The Pukka poisoned Dev on night 2; the Exorcist
     # (Hal) chooses the Pukka (Kai) at step 5.
     ./ui.py emulator-5558 tap "SHOW “YES” TO HAL"      # step 5 resolves
     # ... step 6 = Pukka, gated Reduced by the Exorcist
     ./ui.py emulator-5558 dump | grep -iE "DIES|SURVIVES"
     ```
   - **Expected** one answer. Lead **D63** (which explicitly narrows D36):
     *"An Exorcised (silenced) Demon's deferred standing attack (Pukka) still
     resolves"* — and the Pukka rule's own KDoc
     (`RulesBadMoonRising.kt:1145-1147`) and the Exorcist's own prompt
     (*"Deaths already scheduled from earlier nights still happen."*) agree.
     So the card should say **Dev dies**, throughout.
   - **Actual** the one card says both, and the button is the half that is
     wrong:
     ```
     banner   The Exorcist silenced them: no choice tonight. Anything they set
              up on a previous night still happens. Dev dies now — poisoned by
              the Pukka last night. Shroud them and announce the death at dawn.
     outcome  Dev: The Demon cannot kill tonight. Nobody dies — the Demon could
              not kill tonight.
     PRIMARY  DEV SURVIVES — NOBODY DIES        [69,1140][1011,1287] @(540,1213)
     ```
     Holding the primary drops 12 → **11 alive**; the grimoire reads
     `Seat 4, Dev, Gossip, dead, good, ghost vote available`, and dawn 3 prints
     `Announce: Dev died.` A storyteller who believed the button would not
     shroud Dev and would announce "nobody died" — the rest of the game runs on
     a wrong board.
   - **Screenshot** `tools/emu/out/D2/31-n3-pukka-exorcised.png`,
     `tools/emu/out/D2/32-n3-pukka-contradiction.png`, `tools/emu/out/D2/33-dawn3.png`
   - **Suspect** the **preview** path, not the resolution path. `NightPlan.kt:1883-1910`
     resolves it correctly (lead D68: SILENCED drops the source seat,
     NO_KILL_TONIGHT keeps it). But `DeferredDeath`
     (`engine/.../NightPlan.kt:105-119`) carries only `playerId`, `cause` and
     `respectProtection` — **not** `NightEffect.Attack.deferred` — and
     `namedDeaths` (`NightPlan.kt:865-873`) drops it. So
     `app/.../ui/screens/night/NightCard.kt:196-215` re-runs the funnel with
     `sourcePlayerId = step.holderId` intact, hits the un-narrowed D36 branch at
     `engine/.../Deaths.kt:209-222` (whose comment still says *"so a deferred
     Pukka kill obeys it too (lead D36)"*), gets `KillOutcome.Prevented`, and
     `NightRows.deathHeadline` (`NightRows.kt:399-401`) turns that into
     `"$name survives — nobody dies"`, which `primaryLabel` (`NightRows.kt:387`)
     wears. Give `DeferredDeath` the `deferred` flag and apply the same D68
     scoping in the preview.

2. **P0 · Night sheet, Courtier** — the Courtier's step tells the Courtier every
   character in play and which seat holds it, Demon and Minion included
   - **Repro**
     ```sh
     ./emu.sh launch emulator-5558 --fresh
     ./scenario.py emulator-5558 D2_bmr8_setup
     zsh tools/emu/scenarios/D2_bmr8_assign.sh emulator-5558
     ./scenario.py emulator-5558 D2_courtier_leak      # asserts all of it
     ```
   - **Expected** the Courtier learns **nothing**. Bad Moon Rising almanac:
     *"Once per game, at night, choose a character: they are drunk for 3
     nights."* The card should ask for the character and stop.
   - **Actual** below the character grid the card prints
     ```
     Whoever they name: these seats hold the characters in play
     Amy (Gossip), Bo (Innkeeper), Cy (Courtier), Di (Minstrel),
     Eli (Fool), Fay (Tinker), Gus (Assassin), Hana (Zombuul)
     SHOW: GOSSIP, INNKEEPER, COURTIER, MINSTREL, FOOL, TINKER, ASSASSIN, ZOMBUUL
     ```
     and the **gold primary** is
     `SHOW “GOSSIP, INNKEEPER, COURTIER, MINSTREL, FOOL, TINKER, ASSASSIN,
     ZOMBUUL” TO CY` at `[69,1400][1011,1547] @(540,1473)`. Pressing it opens a
     full-screen **`THESE CHARACTERS`** card with all eight tokens, to be held
     out to the Courtier. The ledger then records it as fact — Cy's HISTORY
     reads `N1 told "THESE CHARACTERS Gossip, Innkeeper, Courtier, Minstre…"`.
     This is the *default* action on the step, not a drawer option.
   - **Screenshot** `tools/emu/out/D2/40-courtier-leak.png` (the card),
     `tools/emu/out/D2/41-courtier-showcard.png` (the card it opens),
     `tools/emu/out/D2/39-courtier.png`
   - **Suspect** `engine/.../InfoCalc.kt:969-983` — `courtier()` is a
     storyteller aid (its own KDoc: *"Is that character in play, and where?" —
     for the Courtier's pick*) but it is returned as a plain `InfoResult`, and
     `InfoResult` has no "storyteller only, never show" flag. `NightRows.kt:373-377`
     then builds `SHOW “<answer>” TO <holder>` for any non-blank answer. Either
     mark the result storyteller-only (and let `primaryLabel` fall through to
     `CONFIRM: …`), or move this text into the card's `detail`/drawer where the
     Exorcist's `"this is for you"` line already lives.

3. **P0 · Night sheet, Exorcist** — the Exorcist is shown whether their pick was
   the Demon, which the same calculation says must not happen
   - **Repro**
     ```sh
     # G1, night 2 and night 3, step 5 (Exorcist — Hal)
     ./ui.py emulator-5558 dump | grep -i "SHOW “"
     ```
   - **Expected** the Exorcist learns nothing. BMR almanac: *"Each night*,
     choose a player (different to last night): the Demon, if chosen, learns who
     you are, then doesn't wake tonight."* Information flows to the **Demon**.
     The engine knows this — `InfoCalc.kt:964` sets
     `detail = "The Exorcist is not told either way; this is for you."`
   - **Actual** the gold primary is `SHOW “NO” TO HAL` (night 2, Hal chose Ana)
     and `SHOW “YES” TO HAL` (night 3, Hal chose the Pukka) at
     `[69,717][1011,864] @(540,790)`. Pressing it opens the full-screen show card
     for the player. The card's own prompt is correct
     (*"If it is the Demon: wake them, show the Exorcist token and point at the
     Exorcist."*), so the button contradicts the instruction three lines above it.
   - **Screenshot** dumped at `tools/emu/out/emulator-5558-dump.xml` at the time
     of `tools/emu/out/D2/31-n3-pukka-exorcised.png` (the following step); the
     same label is asserted by the first tester's `D_fix_bmr_night1` lineage
   - **Suspect** same as D2-2 — `engine/.../InfoCalc.kt:953-967` +
     `app/.../ui/screens/night/NightRows.kt:373-377`. One flag on `InfoResult`
     fixes both.

4. **P0 · Night sheet** — a step that has already been resolved re-draws itself
   as `skipped · dead — no ability` and offers `[Run anyway]`
   - **Repro**
     ```sh
     # G1, night 2. Step 2 = Sailor (Ben) resolves normally (-> Dev).
     # Step 6 = Pukka, whose standing kill takes Ben.
     ./ui.py emulator-5558 hold "DEV — POISONED · BEN DIES" 1600
     ./ui.py emulator-5558 tap "whole sheet"
     ```
   - **Expected** step 2 keeps `✓ Sailor — Ben · → Dev · [Undo]`. It happened;
     the Drunk on Dev is real and still in force.
   - **Actual** the row becomes
     ```
     ⊘ 2  Sailor — Ben                                   skipped
          Ben
          dead — no ability                       [Run anyway]
     ```
     — the recorded target `→ Dev` disappears, the `[Undo]` disappears with it,
     and the only control offered is the one that runs the step **again** and
     places a second `Drunk`. Rows 3–7 beside it still show `✓ … → X [Undo]`,
     so the sheet reads as "the Sailor never woke". It self-corrects if the
     holder comes back (after the Professor resurrected Ben the ✓ returned), but
     between the two the storyteller's only record of the night says something
     false. Gates are being evaluated against *current* state and painted over a
     row whose answer is already in the ledger.
   - **Screenshot** `tools/emu/out/D2/27-n2-sheet-sailor-skipped.png`
   - **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/night/NightRows.kt`
     (the collapsed-row renderer) / `engine/.../NightPlan.kt` — a row with a
     recorded answer should render `done`, whatever its gate now says; the gate
     text belongs on an *unanswered* row only.

---

## P2

5. **P2 · Bluffs sheet** — the character list is laid out past the safe area, and
   the expanded sheet's tab row sits under the status bar
   - **Repro**
     ```sh
     # any game, setup checklist -> "Lunatic bluffs" (or "Demon bluffs")
     ./ui.py emulator-5558 tap "Choose 3 bluffs"
     ./ui.py emulator-5558 audit
     ```
   - **Expected** every row can be tapped without scrolling first, and the tab
     row clears `y=136`.
   - **Actual** the list's scroll container is `[53,1453][1027,2400]` — it runs
     to the physical bottom of the display:
     ```
     === SAFE-AREA VIOLATIONS (2) ===
       #41 '<View>' [53,2218][1027,2350] @(540,2284)  click
           - bottom 34px under the navigation/gesture inset (home indicator)
       #46 '<View>' [53,2361][1027,2400] @(540,2380)  click
           - bottom 84px under the navigation/gesture inset (home indicator)
           - CENTRE UNTAPPABLE: under the bottom navigation / gesture inset (y >= 2316)
     ```
     Dragging the sheet to full height fixes the bottom but moves the
     `Demon bluffs ✓ / Lunatic bluffs — Jonas ✓` tab row to `[32,127][371,253]`:
     `top 9px under the status bar/cutout`.
   - **Screenshot** `tools/emu/out/D2/03-lunatic-bluffs-offscreen.png`,
     `tools/emu/out/D2/05-bluffs-expanded-top.png`
   - **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/Bluffs.kt`
     (or wherever `SetupChecklistSheet` hosts it) — D82 says overlay lists apply
     their insets as **content** padding; this list has neither top nor bottom.

6. **P2 · Token picker and character picker** — the list box is laid out 21 px
   past the safe-area bottom, so the last row on first open is untappable
   - **Repro**
     ```sh
     ./ui.py emulator-5558 tap "^Seat 3,"
     ./ui.py emulator-5558 tap "\+ Token"     # or "Change…"
     ./ui.py emulator-5558 audit
     ```
   - **Expected** the scroll container stops at `y=2316`.
   - **Actual** both pickers use `[53,127][1027,2337]`. Token picker, first
     open:
     ```
     === SAFE-AREA VIOLATIONS (1) ===
       #65 '<View>' [326,2239][483,2396] @(404,2317)  click
           - bottom 80px under the navigation/gesture inset (home indicator)
           - CENTRE UNTAPPABLE: under the bottom navigation / gesture inset (y >= 2316)
     ```
     — that node is the Tea Lady's `Cannot Die ×2`, and `ui.py tap` refuses it
     until you scroll. Character picker, first open: `#66 [53,2231][1027,2375]`,
     `bottom 59px under the … inset`. Both recover by scrolling, and the top of
     each list scrolls under the status bar by 5–14 px at other positions.
   - **Screenshot** `tools/emu/out/D2/14-token-picker.png`,
     `tools/emu/out/D2/38-char-picker.png`
   - **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/SeatSheet.kt`
     — P-6 gave these lists `contentPadding`, but the container itself is still
     laid out to `2337` (the `navigationBars` 63 px rather than the
     `mandatorySystemGestures` 84 px), exactly the seam D82 closed for the shell.

7. **P2 · Grimoire, zoom** — zooming makes the seat ring overlap the header
   controls and the zoom column (12 and 16 seats; the known 8-seat case is
   unchanged)
   - **Repro**
     ```sh
     # 12-seat grimoire
     ./ui.py emulator-5558 tap "Zoom in"
     ./ui.py emulator-5558 audit
     ```
   - **Expected** the canvas keeps its own lane.
   - **Actual** at **12 seats**:
     ```
     === OVERLAPPING CLICKABLES (3) ===
       42% overlap:
           #27 '<View>' [322,588][557,714] @(439,651)  click        <- "ability 1" chip
           #37 '<View>' [454,592][626,873] @(540,732)  click,long   <- seat 1
       40% overlap:
           #82 '<View>' [0,1576][166,1857] @(83,1716)  click,long   <- seat 9
           #107 '<View>' [32,1806][158,1932] @(95,1869)  click      <- [Zoom in]
       21% overlap:
           #31 '<View>' [573,588][812,714] @(692,651)  click        <- "marks 4" chip
           #37 '<View>' [454,592][626,873] @(540,732)  click,long
     ```
     and at **16 seats**, seven pairs — worst 47 % between the Search field
     `[493,441][1059,588]` and a seat `[448,442][632,675]`, plus 28 % against
     the `Circle/Board` toggle and four against the zoom column. Visually the
     tokens are drawn over the chips and over `[Zoom in]`, and the flanking
     seats are clipped by the display edges.
   - **Screenshot** `tools/emu/out/D2/35-zoom12.png`, `tools/emu/out/D2/46-zoom16.png`
   - **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/GrimoireScreen.kt`
     — the zoom transform scales the ring inside the canvas without shrinking
     its available box, so it grows into the header row above and the floating
     zoom column beside it. This is the same decision FOLLOWUPS already parks
     for the 8-seat case ("reserve a lane for the 102 dp zoom column, or move
     the zoom controls off the canvas") — zoom makes it much larger and adds the
     filter-chip row, so file it with that item rather than as a separate fix.
   - **Note** the known **8-seat** overlap reproduces byte-identically and is
     **not** re-filed: `5% overlap · #46 [134,1554][383,1840] vs #61
     [32,1806][158,1932]` (`out/D2_bmr8_setup/emulator-5558/21-audit.png`).

8. **P2 · Night screen header** — `FIRST NIGHT · step 4 / 5` wraps onto two
   lines and rides over the title
   - **Repro** any game whose first night has a single-digit step count
     (G2: 8 seats, 5 steps)
   - **Expected** one line, as it is on `NIGHT 2 · step 11 / 12`
     (`#9 [198,357][469,415]`, height 58).
   - **Actual** `#8 'FIRST NIGHT' [37,357][288,415]` and
     `#9 '  ·  step 4 / 5' [288,328][441,444]` — the counter is 116 px tall, i.e.
     two lines (`· step` above `4 / 5`), vertically centred so it overhangs the
     title's baseline both ways. `FIRST NIGHT` is the widest phase label, so it
     is the one that triggers it.
   - **Screenshot** `tools/emu/out/D2/40-courtier-leak.png` (top of the frame)
   - **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/NightScreen.kt`
     — the header `Row`; give the counter `maxLines = 1` / `softWrap = false`,
     or let the phase label shrink.

9. **P2 · Setup checklist** — the sheet still explains itself in setup language
   when it is raised mid-game
   - **Repro** G1, day 3: seat a traveller (`Menu → A traveller joins…`), which
     adds the row `Miracle's alignment`; the checklist raises itself.
   - **Expected** on day 3 the footer should not talk about beginning the first
     night.
   - **Actual** the footer is still
     `"Begin night" still works with rows outstanding — the guard…`, and the
     header is still `Before the first night`, on a sheet showing a day-3
     traveller's alignment row.
   - **Screenshot** `tools/emu/out/D2/36-checklist-day3.png`
   - **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/GameExtras.kt`
     `SetupChecklistSheet` — the footer string is unconditional. (The menu row
     `Before the first night…` is deliberate per D78; only the footer is wrong
     out of phase.)

10. **P2 · Add an empty seat** — the checklist auto-raises after every add, so
    adding several seats needs a `Close` between each
   - **Repro**
     ```sh
     # any game in SETUP
     for i in 1 2 3; do
       ./ui.py emulator-5558 tap "^Menu$"
       ./ui.py emulator-5558 tap "Add an empty seat"
       ./ui.py emulator-5558 tap "^Add$"
     done
     ./ui.py emulator-5558 find "^Seat "     # 1 seat added, not 3
     ```
   - **Expected** three seats.
   - **Actual** one. Each add changes the blocking-row set (`Bag has 0
     characters for N players` re-counts), which re-raises the checklist over
     the flow, and the next `Menu` tap lands on the sheet's scrim. Inserting
     `tap "^Close$"` at the top of the loop makes it work every time (that is
     how the 15- and 16-seat rings in this report were built).
   - **Screenshot** `tools/emu/out/D2/44-ring15.png` (the end state, after the
     workaround)
   - **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/GameExtras.kt`
     `SetupIdentityPrompts` — same root as the FOLLOWUPS wave-3 item *"never
     auto-raise the checklist over an open sheet"*; this is the same auto-raise
     firing over a **dialog flow** rather than a seat sheet, so fix them
     together.

---

## `ui.py audit` coverage

Every screen reached was audited; a node count is quoted for each so none of
these is the vacuous 0-node flake (README).

| screen | nodes | result |
|---|---|---|
| setup checklist sheet (6 rows) | 7 | clean |
| Grandchild picker | 9 | clean |
| Lunatic-bluffs sheet | 9 | **D2-5** |
| 12-seat grimoire (setup) | 29 | clean |
| 12-seat grimoire (day 3) | 30 | clean |
| seat sheet (alive, tokens) | 10 | clean |
| seat sheet (dead, Advanced open) | 14 | clean |
| token picker | 21 | **D2-6** |
| character picker (`Change…`) | 14 | **D2-6** |
| night 1 step 4 (Demon info) | 18 | clean (2 scrolled-out dropped) |
| show-card overlay | 1 | clean |
| dawn sheet (1, 2, 3) | 2 | clean |
| day 1 sections | 16 | clean |
| nomination panel | 26 | clean |
| live vote tally | 32 | clean (5 scrolled-out dropped) |
| execution / kill sheet | 4 | clean |
| dusk sheet | — | clean |
| overflow menu (16 rows) | 16 | clean |
| Spy read-only stage 1 | 14 | clean |
| Spy read-only stage 2 | 1 | clean |
| game log | 1 | clean |
| Reorder seats | 25 | clean |
| traveller-join dialog | 19 | clean |
| Board view | 25 | clean |
| statement recorder | 18 | clean |
| begin-night guard | — | clean |
| ring 5 seats | 20 | clean |
| ring 8 seats | — | known 5 % zoom overlap only |
| ring 13 seats (traveller) | 31 | clean |
| ring 15 seats | 30 | clean |
| ring 16 seats | 31 | clean |
| 12-seat ring **zoomed** | — | **D2-7** |
| 16-seat ring **zoomed** | — | **D2-7** |

---

## What worked

Beyond the twenty verdicts and the five complaints above.

- **D69, Innkeeper self-choice — exactly as ruled.** The Innkeeper picking
  themselves second gives Bo `Drunk` (in force, `!` pip, `Innkeeper · expires at
  dusk`) *and* `Safe (not in force)` / `Innkeeper · expires at dawn · doing
  nothing — Innkeeper's a…`; the other pick, Amy, reads
  `tokens: Safe (not in forc…` on the ring. Both effects stand and both `Safe`
  are inert. `out/D2/42-innkeeper-d69.png`
- **Courtier countdown starts.** After the pick the row collapses to
  `spent — this ability is once per game`.
- **Zombuul.** Dusk 1 previews the rule: `Nobody died today — the Zombuul kills
  tonight.` The night card opens `Nobody died today. The Zombuul points at any
  player — killi…`.
- **Fool.** The Zombuul's attack on Eli gives
  `Eli: They survive, and the ability is spent.` / `ELI SURVIVES — AND IT IS SPENT`.
- **Assassin.** Prompt `That player dies — no protection of any kind stops it.`;
  the funnel agrees — `Fay: Nothing can prevent this death.` / `FAY DIES`.
- **Tea Lady.** Gita and Hal automatically carry `Cannot Die` (no physical
  token); the day-1 briefing lists both.
- **Sailor.** `◆ themselves` annotation on the holder's own row; the impairment
  banner appears the moment the Pukka poisons them
  (`IMPAIRED — Poisoned by the Pukka (Kai). Their ability does …`).
- **Chambermaid.** Correct counts on both nights (1 then 2), and the Demon-info
  card carries `does not count for the Chambermaid`.
- **Grandmother.** Her own seat is excluded from the night picker (P-9), the
  Grandchild is pre-selected, and the gate reads
  `the grandchild was not killed by the Demon tonight — the Grandmother lives`.
- **Godfather.** First night shows the Outsider block once
  (`Outsiders in play: Lunatic` + `SHOW: LUNATIC` + `SHOW “LUNATIC” TO LENA`),
  and never again.
- **Show cards.** Closing one lands on the privacy cover and needs a second
  press-and-hold — deliberate and documented
  (`ui/components/ShowCards.kt:163-172`), not a defect.
- **Privacy cover.** Leaves exactly three nodes in the tree; the caption tracks
  the phase (`First night`, `Day 3`).
- **Game log.** Renders the engine's `GameLog.rows` grouped `NIGHT n` / `DAY n`
  with characters named (`Ana (Devil's Advocate) chooses Lena`) — P-1/G-1 hold.
- **Reorder seats** (moveSeat), **traveller join** (13-seat ring, `traveller`
  in the content-desc, alignment row added to the checklist), **Board view**
  with filter chips and counts, **multi-copy tokens** (`×2` on the Tea Lady's
  `Cannot Die`), **storyteller tokens** (`Generic` row: Drunk, Poisoned,
  Protected, Mad, Used, No Ability, Good, Evil, ?), **notes**, **ghost vote**,
  **Resurrect / Undo death / Flip alignment / Swap characters / Set shown
  identity** under `▸ Advanced` — all present and behaving.
- **Dusk sheets** sweep correctly (`Removed: Drunk (Sailor) from Cleo.`,
  `Removed: Survives Execution (Devil's Advocate) from Lena.`) and preview the
  night (`Tonight, 8 steps: …`).
- **Withdraw** a nomination, and re-nominate, leaves an audit trail
  (`Ana » Erin · 0 votes · withdrawn` with `▸ voters`).

## Not reached

- **Po's three-attack night, Shabaloth's regurgitation, Mastermind's extra day,
  Moonchild, Tinker's death trigger, Goon.** The 12-seat bag is pinned to the
  user's fixture and the 8-seat bag could hold only one Demon and one Minion;
  each of these needs its own deal. The Tinker was in G2 but never died while a
  night step could fire; the Zombuul's "dies once" *day* (registering dead after
  its first death) needs a day in which the Zombuul is executed, which G2 did
  not reach.
- **Minstrel.** In G2's bag but never triggered — it needs a Minion to die at
  night, and the Assassin/Zombuul were the ones killing.
- **Assassin beating a live protection.** The Assassin's own card says it does
  and `Deaths` agrees (`Nothing can prevent this death.`), but the only `Safe`
  on the board at that moment was the inert self-drunk Innkeeper's (D69), so
  the *interaction* is untested.
- **Gossip verdict `✗` (False).** The unjudged `?` path and the `True` branch
  are both proven; `False — nobody dies` was offered but not taken.
- **Traveller reminder tokens.** The Beggar has no reminders in the shipped
  data (a known gap, FOLLOWUPS §WP7-TRAV: *"beggar.reminders empty (needs
  'Token')"*), so a Beggar seat's picker has nothing traveller-shaped to show;
  F-5 could not be re-proven from this bag. `F_fix_traveller_tokens` remains the
  evidence for it.
- **`D2_rings.py` end-to-end.** The 5-seat leg passes; the 15/16 legs hit the
  twelve-seat-class empty-tree flake twice on `--fresh` and were completed by
  adding empty seats to the 5-seat game instead (see D2-10). The scenario is
  committed as written; re-run it if the flake is ever fixed.
