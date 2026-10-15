# Playtest findings

Bugs found by playing the app on emulators with the harness in
[`tools/emu/`](../../tools/emu/README.md). One section per agent, appended to the
end of this file. **Never rewrite another agent's section.**

Read `tools/emu/README.md` first. The short version:

```sh
cd tools/emu
./emu.sh boot 1
./emu.sh launch emulator-5554
./ui.py emulator-5554 dump
./ui.py emulator-5554 audit      # run this on every new screen you reach
```

## Severity

| | meaning |
|---|---|
| **P0** | crash, stuck state, or a **wrong rule** the storyteller would act on |
| **P1** | a flow you cannot complete |
| **P2** | cosmetic — layout, spacing, wording, safe-area violations |

## Template

Copy this, fill in every part, append it at the end of the file.

```markdown
### <area you played> — <your agent name>

1. **P1 · Night order sheet** — Fabled tokens missing from the first night
   - **Repro**
     ```sh
     ./ui.py emulator-5554 tap "New game"
     ./ui.py emulator-5554 tap "Trouble Brewing"
     ./ui.py emulator-5554 tap "Start empty"
     ./ui.py emulator-5554 tap "Night"
     ```
   - **Expected** Fabled appear at the top of the first-night order
   - **Actual** first-night list starts at Poisoner; no Fabled row
   - **Screenshot** `tools/emu/out/night/emulator-5554/04-tap-night.png`
   - **Suspect** `engine/src/main/kotlin/.../NightOrder.kt`
```

Every finding needs all six parts: severity, screen/flow, exact `ui.py` repro
steps from a freshly launched app, expected vs actual, a screenshot path, and
the engine/UI file you suspect. For rules bugs, cite the rule. If `tap` reports
`OFFSCREEN`, file that as a finding before working around it.

---

### Harness validation (home / new game / grimoire) — emu-harness

Found while validating `tools/emu/`. Both come from `ui.py audit`; neither blocks
a flow.

1. **P2 · Grimoire, top control row** — `fabled +` hit target overlaps the top-right corner of the Search field
   - **Repro**
     ```sh
     ./ui.py emulator-5554 tap "New game"
     ./ui.py emulator-5554 tap "Trouble Brewing"
     ./ui.py emulator-5554 tap "Start empty"
     ./ui.py emulator-5554 tap "^Close$"
     ./ui.py emulator-5554 audit
     ```
   - **Expected** no two clickable nodes fight over the same pixels
   - **Actual** `audit` reports a 40 % overlap; tapping the top-right corner of
     the Search field can activate `fabled +` instead of focusing the field
     ```
     === OVERLAPPING CLICKABLES (1) ===
       40% overlap:
           #10 '<View>'     [935,304][1061,402] @(998,353)  click
           #19 '<EditText>' [493,362][1059,509] @(776,435)  click,long
     ```
   - **Screenshot** `tools/emu/out/new_game/emulator-5554/12-audit.png`
   - **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/GrimoireScreen.kt`
     — `GrimoireHeader` (line 267). The `fabled +` clickable `Row` (309–322) and
     the Search `OutlinedTextField` (370–377) are on two *separate* rows of a
     plain `Column`, with no overlay or offset, so the visuals do not overlap —
     but `fabled +` renders only 98 px tall and Compose expands its hit rect to
     the 48 dp minimum touch target, which reaches down into the field below.
     Adding vertical spacing between the two rows would clear it.

2. **P2 · Grimoire, bottom tab bar** — Grimoire/Night/Day/Script touch targets extend into the gesture inset
   - **Repro** as above, then read the `audit` output
   - **Expected** interactive bounds stop at the safe-area bottom (`y=2316`);
     the last 84 px are the home indicator's swipe strip
   - **Actual** all three right-hand tabs run to `y=2337` — 21 px into the
     gesture inset
     ```
     === SAFE-AREA VIOLATIONS (3) ===
       #94  '<View>' [276,2127][530,2337] @(403,2232)  click
           - bottom 21px under the navigation/gesture inset (home indicator)
       #98  '<View>' [551,2127][805,2337] @(678,2232)  click
       #102 '<View>' [826,2127][1080,2337] @(953,2232)  click
     ```
     The **labels** sit at `y 2245..2303`, above the strip, and the tap centres
     are comfortably safe — so this is a hit-target overrun, not an unreachable
     control. Triage as by-design or pad the row to the gesture inset.
   - **Screenshot** `tools/emu/out/new_game/emulator-5554/12-audit.png`
   - **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/GameShell.kt`
     — the `bottomBar` slot (307–322) calls `NavigationBar { }` with no
     `windowInsets` argument, so it takes the Material3 default
     `WindowInsets.navigationBars` = 63 px. That is exactly why the row ends at
     `y=2337` (`2400-63`), 21 px short of clearing the 84 px
     `mandatorySystemGestures` inset. `WindowInsets.safeGestures` (or
     `.systemGestures`) would close the gap.

### Night handout regression sweep, 2026-10-15 — night_emulator_qa

Full reproducible emulator report: [2026-10-15-night-cards-emulator.md](2026-10-15-night-cards-emulator.md).
Only emulator-5554 was driven; synthetic fixture and strict-serial seed helper
are under `tools/emu/`.

- **P1 · Lunatic first-night handout (fixed-build PASS):** baseline has Minions
  instructions but only a bluff button. Fixed UI shows actual fake-Minions card.
- **P1 · Grandmother relationship caption (fixed-build PASS):** baseline generic
  THIS PLAYER IS. Fixed card has THIS IS YOUR GRANDCHILD + character + player.
- **P0 · Dreamer information cardinality (fixed-build PASS):** baseline offered
  one token; fixed healthy and poisoned cards display simultaneous good/evil pairs.
- **P2 · Nightwatchman pending handout wording:** pending SHOW 1 CARD TO MARCO
  sits beneath NOTHING TO DO / spent. Reported for final APK repair and retest.
- **Regression PASS:** initial Philosopher action disabled without explicit
  choice; pass survives Night2 and process restart; Imp A→B then hold commits B
  only, early release commits nothing; Nightwatchman healthy targets chosen
  recipient, poisoned source creates no recipient wake.

Exact commands, expected versus actual, rule references, suspect files and
screenshots are in the linked report. No physical phone or user save was edited.

Night-emulator final update: pending wording **fixed and UI-verified** on APK
SHA-256 `85c74a6f93e444e3db3312af0cdbb5f8569c9dd0b14373404f7762e39f33bc61`.
Standard and custom Nightwatchman SHOW→close→Done both pass without replay;
persisted custom card remains the only handout record. See final section of the
linked emulator report for screenshots and exact assertions.
