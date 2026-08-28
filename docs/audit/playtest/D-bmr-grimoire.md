# Playtest D — the user's Bad Moon Rising session, plus the grimoire

Driven on `emulator-5560` with `tools/emu/`, build `2020fec`.

The 12-player game from
`engine/src/test/kotlin/com/clocktower/engine/BmrSessionPlaytestTest.kt` was
set up by hand through the real UI (12 named seats, 12 characters, Lunatic
shown the Po, Grandchild on Finn, both bluff sets, two fake Minions) and then
played **Night 1 → Day 3**. Every screen and sheet reached was `audit`ed and
every screenshot was read.

Scenarios written:

| file | what it does |
|---|---|
| `tools/emu/scenarios/D_bmr_setup.py` | new BMR game, 12 seats pasted, start empty, audits |
| `tools/emu/scenarios/D_bmr_assign.sh` | assigns the 12 friction-log characters seat by seat |
| `tools/emu/scenarios/D_repro_grimoire.py` | from `--fresh`: token Remove/Suspend, Spy mode, nomination overlap |

Counts: **4 P0 · 6 P1 · 10 P2**.

---

## P0

1. **P0 · Night screen** — a new night opens on the **Dawn** step, so pressing
   "Begin night" and then following the card skips the entire night
   - **Repro**
     ```sh
     # from the BMR game, at the end of night 1
     ./ui.py emulator-5560 tap "OPEN THE DAY"      # night 1, step 11/11 = Dawn
     ./ui.py emulator-5560 tap "OPEN DAY 1"
     ./ui.py emulator-5560 tap "Dusk"              # top bar
     ./ui.py emulator-5560 tap "BEGIN NIGHT 2"
     ./ui.py emulator-5560 dump | head -3
     ```
   - **Expected** night 2 opens on step 1 (Dusk)
   - **Actual** `NIGHT 2 · step 12 / 12` — the Dawn card, with `OPEN THE DAY →`
     as the primary button. Steps 1–11 are all still `·` (pending). Reproduced
     again at the start of night 3 (`NIGHT 3 · step 12 / 12`). The current-step
     index is carried over from the previous night instead of being reset.
     Because the card *is* Dawn, tapping its `OPEN THE DAY →` button goes
     straight through — the "Night checklist incomplete" guard only fires when
     you press **Dawn** in the top bar, which a storyteller on the Dawn card has
     no reason to do. A whole night can be skipped with two taps and no warning.
     Getting back to step 1 needs ~12 presses of `‹ back`; the step list only
     scrolls back three steps, so there is no other route.
   - **Screenshot** `tools/emu/out/D/26-night2-state.png`
   - **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/NightScreen.kt`
     — the `rememberSaveable` current-step index is not reset on `state.cycle`
     change (or on `NightPlan` identity change).

2. **P0 · Night 1, Lunatic** — the Lunatic's own row is auto-skipped, so the
   storyteller never wakes them and the whole illusion is never handed over
   - **Repro**
     ```sh
     ./scenario.py emulator-5560 D_bmr_setup
     zsh tools/emu/scenarios/D_bmr_assign.sh emulator-5560
     # finish the setup checklist, then:
     ./ui.py emulator-5560 tap "Begin night"
     ./ui.py emulator-5560 tap "DONE — NEXT STEP"   # step 1 Dusk  -> step 2
     ./ui.py emulator-5560 tap "DONE — NEXT STEP"   # step 2 Minion info
     ./ui.py emulator-5560 dump | head -3
     ```
   - **Expected** step 3 is the Lunatic. `BmrSessionPlaytestTest` pins the
     night-1 order as `DUSK · MINION_INFO · lunatic · DEMON_INFO · …` and
     `docs/audit/characters/lunatic.md` quotes the almanac: *"During the first
     night, wake the Lunatic and act as if they are the Demon. Show them the
     THESE ARE YOUR MINIONS info token … Then show three good character tokens
     as bluffs."* The Lunatic **must** be woken on night 1.
   - **Actual** the sheet jumps **from step 2 straight to step 4 (Demon info)**.
     Step 3 renders as
     `⊘ 3 · Po — Jonas (via the Lunatic) · skipped · no ability on this night`
     with a `[Run anyway]` chip. The card itself is right when you force it open
     (banner *"Everything here is an illusion — nothing they choose has any
     effect."*, `SHOW: BLUFFS`, the Lunatic's own bluff-set name in the detail) —
     it is the **gate** that is wrong: the Po having no first-night *action* is
     being read as the Lunatic having nothing to do, when the row's whole point
     on night 1 is the hand-over.
   - **Screenshot** `tools/emu/out/D/12-n1-lunatic-card.png` (the card),
     `tools/emu/out/D/08-n1-lunatic.png` (the skip in the sheet)
   - **Suspect** `engine/src/main/kotlin/com/clocktower/engine/rules/RulesBadMoonRising.kt`
     (the `lunatic` rule's first-night gate) / `Identity.derivedGrants` — the
     first-night step needs `StepGate.None`, not the believed Demon's
     "no ability on this night".

3. **P0 · Night 1, Demon info** — the real Demon is never shown who the Lunatic is
   - **Repro** as above, then land on step 4 and read the card, or
     `grep -o "Lunatic[^\"]*" tools/emu/out/emulator-5560-dump.xml`
   - **Expected** `docs/audit/characters/lunatic.md` (almanac, first night, real
     Demon): *"Show them the THIS PLAYER IS info token, then the Lunatic token,
     then point at the Lunatic player."* The audit file even names the shipped
     implementation it expected — `NightOrder.kt:110-115`, appending
     *" Also show the Demon who the LUNATIC is (Name) — the Demon can mirror
     their fake kills."*
   - **Actual** the Demon-info card is the plain BMR text (`Wake the Demon.
     Show the THESE ARE YOUR MINIONS info token … Put the Demon back to sleep.`)
     plus `SHOW: BLUFFS`. The string "Lunatic" does not appear anywhere on the
     card, in its `HOW TO RUN THIS` drawer, or in the dumped tree — and
     `grep -rn "is the Lunatic" engine/src/main/kotlin` finds nothing. The
     other-nights half **is** implemented (the Pukka's night-2 banner reads
     *"The Lunatic (Jonas) chose Cleo."*), so only the first-night half is missing.
   - **Screenshot** `tools/emu/out/D/09-n1-lunatic-runanyway.png`,
     `tools/emu/out/D/10-n1-demon-info-bottom.png`
   - **Suspect** `engine/src/main/kotlin/com/clocktower/engine/NightPlan.kt`
     (the `DEMON_INFO` step's `detail` builder)

4. **P0 · Nights 2+, Godfather** — the first-night "these Outsiders are in play"
   info block is rendered again every night
   - **Repro**
     ```sh
     # BMR game, night 2, step 7
     ./ui.py emulator-5560 find "Outsiders in play"
     ```
   - **Expected** the Godfather learns the Outsiders **only on the first night**.
     On later nights the step is *"If an Outsider died today, choose a player:
     they die"* and nothing else.
   - **Actual** on night 2 **and** night 3 the card carries the conditional gate
     (`Did a Outsider die today?` / `NOTHING TO DO — nobody died today`) *and*,
     underneath it, `Outsiders in play: Lunatic`, `Show each of those character
     tokens.`, `SHOW: LUNATIC`, `LIE · SHOW GOON`, `LIE · SHOW MOONCHILD`,
     `LIE · SHOW TINKER`. A storyteller working down the card would show Lena the
     Outsider tokens a second and third time, handing evil free information.
     On night 3 the step is gated to `NOTHING TO DO — nobody died today` and
     *still* shows the four SHOW buttons. Separately, once the night-2 gate is
     answered `No — skip` the card is left with **no primary button at all** —
     the only way past it is `skip ›`.
   - **Screenshot** `tools/emu/out/D/35-n2-godfather.png`
   - **Suspect** `engine/src/main/kotlin/com/clocktower/engine/rules/RulesBadMoonRising.kt`
     — the `godfather` rule shares one `info`/card block between `firstNight`
     and `otherNight`.

---

## P1

5. **P1 · Seat sheet + token peek** — `Remove` and `Suspend` do nothing for a
   hand-placed token; there is no way to take a token off a seat
   - **Repro** (from `--fresh`, reproduced by `D_repro_grimoire`)
     ```sh
     ./emu.sh launch emulator-5560 --fresh
     ./ui.py emulator-5560 tap "New game"
     ./ui.py emulator-5560 tap "Trouble Brewing"
     ./ui.py emulator-5560 tap "Start empty"
     ./ui.py emulator-5560 tap "^Close$"
     ./ui.py emulator-5560 tap "^Seat 1,"
     ./ui.py emulator-5560 tap "\+ Token"
     ./ui.py emulator-5560 tap "^Drunk$"
     ./ui.py emulator-5560 tap "^Remove$"
     ./ui.py emulator-5560 tap "^Suspend$"
     ./ui.py emulator-5560 tap "^Remove$"
     ```
   - **Expected** `Remove` deletes the token (STATUS goes back to
     `No tokens on this seat.`); `Suspend` flips the label to `Restore` and the
     token reads `Drunk (turned over)`.
   - **Actual** nothing happens, for either button, however many times you press
     them, from the seat sheet **or** from the long-press `Tokens on {Name}`
     dialog. Closing and reopening the sheet shows the token unchanged. The only
     way to remove it is the global `Undo` in the top bar, which also rewinds
     whatever else you did. Engine-placed tokens (e.g. the Sailor's `Drunk`) *do*
     remove correctly, so the break is specific to tokens placed through
     `+ Token`.
   - **Screenshot** `tools/emu/out/D_repro_grimoire/emulator-5560/20-sleep-1-5.png`
   - **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/GameActionsApi.kt:586`
     `removeRenderedToken` — a hand-placed `PlacedReminder` that projects into an
     `Effect` is rendered from the **effect** (so `token.effectId != null`), so
     the function takes the `Effects.remove(state, effectId)` branch, deletes the
     effect and leaves the reminder on the player; `Effects.rendered`
     (`Effects.kt:1109-1137`) then re-projects it on the next recomposition.
     `suspendEffect` loses the flag the same way.

6. **P1 · Day screen, nominations** — the seat circle's hit targets overlap the
   vote-tally chips, so tapping a vote silently changes the nominee
   - **Repro**
     ```sh
     # BMR game, day 2
     ./ui.py emulator-5560 tap "Nominate"
     ./ui.py emulator-5560 tapxy 540 579     # Ana nominates
     ./ui.py emulator-5560 tapxy 108 832     # ... Jonas
     ./ui.py emulator-5560 swipe up 900
     ./ui.py emulator-5560 audit
     ```
   - **Expected** the vote chips are the only thing under your finger once the
     nominee is chosen
   - **Actual** `audit` reports **10** overlapping clickable pairs, the worst at
     41 %:
     ```
     === OVERLAPPING CLICKABLES (10) ===
       41% overlap:
           #30 '<View>' [438,1021][643,1146] @(540,1083)  click,long
           #56 '<View>' [391,1083][573,1198] @(482,1140)  click
       26% overlap:
           #32 '<View>' [222,987][427,1113] @(324,1050)  click,long
           #53 '<View>' [222,1083][375,1198] @(298,1140)  click
       22% overlap:
           #30 '<View>' [438,1021][643,1146] @(540,1083)  click,long
           #59 '<View>' [589,1083][724,1198] @(656,1140)  click
       …
     ```
     In practice: while tallying six votes for Jonas, two of my taps on the top
     row of vote chips landed on the **circle seat** behind them. Each time the
     nomination silently became `Ana » Iris` and the tally reset — no
     confirmation, no undo prompt. I had to redo the nomination three times and
     only got through it by voting exclusively from the second chip row. The
     defect reproduces from `--fresh` in an 8-player Trouble Brewing game too
     (34 % overlap between seat 5 and the tally row).
   - **Screenshot** `tools/emu/out/D/48-day2-vote-overlap.png`
   - **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/DayScreen.kt`
     — the nominate circle and the vote-chip `FlowRow` are stacked without
     enough vertical padding; the circle's 48 dp minimum touch targets reach
     down into the first chip row.

7. **P1 · "Show the grimoire to a player…" (Spy read-only)** — every button in
   both stages is under the gesture inset, and back-press is refused
   - **Repro** (from `--fresh`, reproduced by `D_repro_grimoire` step 27)
     ```sh
     ./ui.py emulator-5560 tap "^Menu$"
     ./ui.py emulator-5560 tap "Show the grimoire to a player"
     ./ui.py emulator-5560 audit
     ./ui.py emulator-5560 tap "HAND IT OVER"
     ```
   - **Expected** `HAND IT OVER` / `Cancel` sit inside the safe area
   - **Actual** both stages put the action row at `y 2346..2400`, entirely below
     the safe-area bottom (`2316`), with only a ~4 px sliver of the button
     visible on screen:
     ```
     === SAFE-AREA VIOLATIONS (2) ===
       #33 '<View>' [42,2346][842,2400] @(442,2373)  click
           - bottom 84px under the navigation/gesture inset (home indicator)
           - CENTRE UNTAPPABLE: under the bottom navigation / gesture inset (y >= 2316)
       #36 '<View>' [863,2347][1037,2400] @(950,2373)  click
           - CENTRE UNTAPPABLE …
     ```
     `ui.py tap` refuses with `OFFSCREEN 'HAND IT OVER' centre=(442,2395)`.
     Stage 2 (the player's view) is worse: it has exactly **one** clickable node
     in the whole screen, `DONE — BACK TO THE SHEET`, and it is off-screen too —
     and the dialog sets `dismissOnBackPress = false`, so `back` does nothing.
     A player handed the phone is stuck. I only escaped by `tapxy`-ing the 4 px
     sliver at `y=2360`. Content-wise the mode is correct (nothing tappable, full
     token text, `†` on the dead), it is purely the button row.
   - **Screenshot** `tools/emu/out/D/63-spy-stage1.png`,
     `tools/emu/out/D/64-spy-stage2.png`,
     `tools/emu/out/D_repro_grimoire/emulator-5560/27-audit.png`
   - **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/GameShell.kt`
     — the Spy `Dialog` uses `usePlatformDefaultWidth = false` with no
     `WindowInsets.safeDrawing`/`safeGestures` padding on its content column.

8. **P1 · Professor re-run** — the inserted first-night step sorts **after** Dawn
   - **Repro**
     ```sh
     # BMR night 2: the Professor resurrects Erin (the Grandmother)
     ./ui.py emulator-5560 hold "CONFIRM: ERIN" 1400
     ./ui.py emulator-5560 tap "whole sheet"
     ```
   - **Expected** the re-run of the Grandmother's first night happens *during*
     the night, before Dawn.
   - **Actual** the sheet grows from 12 to 13 steps (good), but the order ends
     `… 11 Chambermaid — Cleo · 12 Dawn · 13 Grandmother — Erin`. The Dawn card
     is reached first and its primary button is `OPEN THE DAY →`. The card does
     label itself `first night, again` and `out of order — this became true after
     their slot`, and the checklist guard does catch it if you use the top-bar
     **Dawn** button — but combined with P0-1 (a night that opens *on* Dawn) the
     step is very easy to walk straight past.
   - **Screenshot** `tools/emu/out/D/42-n2-order-bug.png`,
     `tools/emu/out/D/43-n2-gm-rerun-card.png`
   - **Suspect** `engine/src/main/kotlin/com/clocktower/engine/NightPlan.kt`
     — the inserted `RUN_FIRST_NIGHT` step is appended rather than sorted before
     the `DAWN` marker.

9. **P1 · Pukka, night N+1** — the standing victim dies with nothing on the card
   saying so
   - **Repro**
     ```sh
     # BMR night 2, Pukka step: Ben was poisoned on night 1
     ./ui.py emulator-5560 find "^BEN|dies"      # nothing
     ./ui.py emulator-5560 tap "DEV — POISONED"
     ./ui.py emulator-5560 find "alive"          # 11/12 -> 10/12
     ```
   - **Expected** per `docs/audit/ux/night-screen.md` the card's outcome line
     reads `Ben dies` (`NightRows.deathHeadline`), and the primary button carries
     the death, so the storyteller knows to shroud Ben and announce him at dawn.
   - **Actual** the card shows only the prompt and the picker. The primary button
     reads `DEV — POISONED`. Nothing names Ben; the word "Ben" does not appear on
     the card at all. Tapping it kills Ben silently (alive count drops 11 → 10)
     and the step summary in the sheet reads `Pukka — Kai → Dev`. Same on night 3
     with the Exorcised Pukka: the button reads `DONE — NEXT STEP` and Dev dies.
     The **rule** is right (the deferred kill lands, and still lands through an
     Exorcist) — it is the reporting that is missing.
   - **Screenshot** `tools/emu/out/D/33-n2-pukka-confirm.png`,
     `tools/emu/out/D/54-n3-pukka-exorcised.png`
   - **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/night/NightRows.kt`
     — `deathHeadline` is not fed the deferred `pukka` death.

10. **P1 · Setup** — the "Before the first night" checklist cannot be re-opened
    on demand, and the guard's "Fix setup" button does not open it
   - **Repro**
     ```sh
     ./scenario.py emulator-5560 D_bmr_setup    # dismisses the checklist
     zsh tools/emu/scenarios/D_bmr_assign.sh emulator-5560
     ./ui.py emulator-5560 tap "^Menu$"          # no checklist entry
     ./ui.py emulator-5560 back
     ./ui.py emulator-5560 tap "Begin night"
     ./ui.py emulator-5560 tap "Fix setup"
     ```
   - **Expected** `Fix setup` opens the checklist at the outstanding rows.
   - **Actual** `Fix setup` just closes the dialog and switches to the Grimoire
     tab. The overflow menu has 14 entries (Demon bluffs, Storyteller notes,
     Show a card…, Reveal characters…, Screen dimming, Show the grimoire to a
     player…, Fabled…, Jinxes in play, Game log, Reorder seats, A traveller
     joins…, Add an empty seat, Declare good/evil victory, Back to home) and
     **none** of them is the setup checklist. `SetupIdentityPrompts`
     (`GameExtras.kt:386-418`) only re-raises the sheet when the *set* of blocking
     row ids changes, and it remembers the set you dismissed — so once dismissed
     with the same rows outstanding, the Lunatic's Demon token, the Lunatic's
     bluffs and the Grandchild are unreachable until something else changes the
     set. I got back in only by satisfying `Demon bluffs` from the `+ bluffs`
     corner, which changed the key and re-raised the sheet by luck.
   - **Screenshot** `tools/emu/out/D/03-setup-checklist-full.png`
   - **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/PhaseFlow.kt:274-277`
     (the `Fix setup` confirm button only calls `onTab(GameTab.GRIMOIRE)`) and
     `GameShell.kt` (no menu entry).

---

## P2

11. **P2 · Setup checklist sheet** — the `Close` button is off-screen on first open
    - **Repro** BMR game with 6 checklist rows → the sheet auto-opens →
      `./ui.py emulator-5560 tap "^Close$"`
    - **Expected** the sheet's only action button is tappable
    - **Actual**
      ```
      === SAFE-AREA VIOLATIONS (1) ===
        #41 '<View>' [53,2341][1027,2400] @(540,2370)  click
            - bottom 84px under the navigation/gesture inset (home indicator)
            - CENTRE UNTAPPABLE: under the bottom navigation / gesture inset (y >= 2316)
      ```
      `ui.py` reports `OFFSCREEN 'Close' centre=(540,2387)`. Only the top third
      of the word "Close" is painted. Scrolling the sheet brings it back, so it
      is recoverable — but the sheet does not look scrollable.
    - **Screenshot** `tools/emu/out/D/04-setup-checklist-6rows.png`
    - **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/GameExtras.kt:430`
      `SetupChecklistSheet` — no `navigationBarsPadding()`/`safeGestures` on the
      sheet column.

12. **P2 · Grimoire** — an inert effect still draws as a full-strength impairment pip
    - **Repro**
      ```sh
      # BMR night 1: the Sailor (Ben) makes Cleo drunk at step 5;
      # the Pukka poisons Ben at step 8, which switches the Sailor off.
      ./ui.py emulator-5560 find "^Seat 3,"
      ./ui.py emulator-5560 tap  "^Seat 3,"
      ```
    - **Expected** something says the token is not doing anything — the engine is
      right that Cleo is **not** impaired (the Sailor's ability is off, so the
      effect it sustains is inert), and the morning briefing agrees: it lists
      *"Ben's ability does not work — Poisoned by the Pukka (Kai)."* and
      *"Jonas's ability does not work — No ability by the Lunatic."* but nothing
      about Cleo.
    - **Actual** the grimoire draws Cleo with a solid green `!` IMPAIRED pip and
      the seat sheet's STATUS row reads `Drunk / Sailor · expires at dusk` with
      no qualifier, while the seat's own content-desc is
      `Seat 3, Cleo, Chambermaid, alive, good, tokens: Drunk` — note the missing
      `drunk or poisoned`. A storyteller reading the circle would feed the
      Chambermaid false information. Compare seat 2:
      `Seat 2, Ben, Sailor, alive, good, drunk or poisoned, tokens…`. The
      grimoire spec already has a visual language for this (the `derived` /
      `suspended` pip styles); an inert sustained effect should use one.
    - **Screenshot** `tools/emu/out/D/19-seat3-cleo.png`
    - **Suspect** `engine/src/main/kotlin/com/clocktower/engine/Effects.kt`
      `rendered()` — it does not mark effects whose sustaining source has stopped
      working.

13. **P2 · Seat sheet action bar** — `Change…` wraps to two lines as `Chang / e…`
    - **Repro** open any seat sheet and read the sticky bar
    - **Expected** one line
    - **Actual** the third button renders `Chang` above `e…`. `Kill…` and
      `+ Token` (which itself wraps to `+` / `Token`) are fine-ish; `Change…` is
      the ugly one. Present in both the BMR game and a fresh Trouble Brewing game.
    - **Screenshot** `tools/emu/out/D/19-seat3-cleo.png`,
      `tools/emu/out/D_repro_grimoire/emulator-5560/20-sleep-1-5.png`
    - **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/SeatSheet.kt`
      `SeatActions` sticky bar — four equal-weight buttons do not fit 1080 px.

14. **P2 · Godfather step** — `Did a Outsider die today?` should be `an Outsider`
    - **Screenshot** `tools/emu/out/D/35-n2-godfather.png`
    - **Suspect** `engine/.../rules/RulesBadMoonRising.kt` — the conditional
      gate question is built as `"Did a ${team} die today?"`.

15. **P2 · Day screen, nominations** — an off-script caveat: `! Did Erin claim to
    be the Goblin?` in a Bad Moon Rising game
    - **Repro** BMR day 1, nominate anyone, read the nominations card
    - **Expected** only caveats for characters on the chosen script. BMR's
      Minions are Godfather, Devil's Advocate, Assassin, Mastermind — no Goblin.
    - **Screenshot** `tools/emu/out/D/21-day1-vote.png`
    - **Suspect** the nomination-caveat builder in `DayScreen.kt` / `Briefings.kt`
      — not filtered by `script.characterIds`.

16. **P2 · Dawn sheet** — an off-script source label: `Removed: Died Today
    (Undertaker) from Erin.` with no Undertaker in the game
    - **Screenshot** `tools/emu/out/D/45-dawn2-sheet.png`
    - **Suspect** `engine/.../Tokens.kt` — the generic "died today" marker is
      registered with `sourceId = "undertaker"`.

17. **P2 · Dawn sheet** — `Announce: Erin is alive again.` repeats every dawn
    - **Repro** resurrect on night 2, do not tap `Said it` on day 2, open dawn 3
    - **Expected** the announcement is a one-off for the dawn after the
      resurrection
    - **Actual** it is re-listed under `SAY OUT LOUD, IN THIS ORDER` at dawn 3
      (and would keep repeating), because the day-2 row `Erin » announce: "Erin
      is alive again." still owed to the table` was never ticked. Defensible, but
      there is no hint on the dawn card that it was already said once.
    - **Screenshot** `tools/emu/out/D/60-dawn3.png`
    - **Suspect** `engine/.../Briefings.kt` — the `ALIVE_AGAIN` announce item is
      not scoped to the dawn of the night it happened.

18. **P2 · Setup checklist, The Grandchild** — the picker offers the Grandmother
    herself
    - **Repro** checklist → `The Grandchild` → scroll the list
    - **Expected** the Grandmother knows *another* good player; Erin should not be
      in her own list. Evil seats are filtered out correctly (Ana, Kai, Lena are
      absent), so the filter exists — it just does not exclude the holder.
    - **Screenshot** `tools/emu/out/D/04-setup-checklist-6rows.png`
    - **Suspect** `engine/src/main/kotlin/com/clocktower/engine/SetupRequirements.kt`
      — the `grandmother.grandchild` requirement's candidate list.

19. **P2 · Night 1, Grandmother** — the Grandchild marked at setup is not
    pre-selected, and `DONE — NEXT STEP` starts `DISABLED`
    - **Repro** mark Finn as the Grandchild in the setup checklist, then reach
      night 1 step 9
    - **Expected** the seat already carrying the `Grandchild` token is
      pre-selected (the card even says *"Show the **marked** Grandchild's
      character token"*)
    - **Actual** `WHICH PLAYER IS THE GRANDCHILD?` opens with nothing selected
      and the primary button disabled; you have to pick Finn a second time.
    - **Screenshot** no dedicated capture; the dump of the step is in
      `tools/emu/out/emulator-5560-dump.xml` at the time of
      `tools/emu/out/D/14-n1-pukka.png` (the next step), and the disabled button
      shows as `DONE — NEXT STEP … click,DISABLED`
    - **Suspect** `engine/.../rules/RulesBadMoonRising.kt` `grandmother` rule —
      no `preselect` from the placed `grandmother:Grandchild` reminder.

20. **P2 · Night screen** — resolving a step sometimes jumps the pointer back to
    step 1
    - **Repro** night 3, jump to the Exorcist (step 5) by tapping its row in
      `whole sheet`, resolve it with `SHOW "YES" TO HAL`
    - **Expected** the sheet advances to step 6
    - **Actual** `NIGHT 3 · step 1 / 12` (the Dusk card). Four more taps of
      `DONE — NEXT STEP` were needed to walk back to step 6. Same family as P0-1
      — the current-step index is not derived from the plan.
    - **Screenshot** `tools/emu/out/D/53-n3-pukka-reduced.png` (the sheet after
      the jump; the header reads `NIGHT 3 · step 1 / 12` in the dump taken with it)
    - **Suspect** `app/.../ui/screens/NightScreen.kt`

---

## What WORKED — complaint by complaint

**1. Pukka — poison, then kill.** ✅ **Works, in the UI.**
Night 1 the step is `The Pukka points at a player: that player is POISONED. The
player poisoned on the previous night dies now, still poisoned, then becomes
healthy.` with a single-target picker `WHO DID THEY CHOOSE?` and a primary button
`BEN — POISONED` — **no kill is offered**. Night 2 the same one-tap choice
(`DEV — POISONED`) also kills Ben; Ben's seat becomes `dead, good, ghost vote
available` and dawn announces `Announce: Ben died.`. Night 3, with the Exorcist
on the Pukka, the gate is
`PART OF THIS STEP ONLY — The Exorcist silenced them: no choice tonight. Anything
they set up on a previous night still happens.` — a **Reduced** gate, never a
skip, with no picker — and **Dev still dies** (11 → 10 alive). Only the
*reporting* of those deferred deaths is missing (P1-9).
Bonus: `Ben` is tagged `last night` in the night-2 picker.
Screenshots `D/14-n1-pukka.png`, `D/31-n2-pukka.png`, `D/54-n3-pukka-exorcised.png`.

**2. Devil's Advocate — different to last night.** ✅ **Works, in the UI, end to end.**
Night 1: plain `WHO DID THEY CHOOSE?`, button `LENA — SURVIVES EXECUTION`.
Night 2: red banner `Chosen last night: Lena — not again tonight.`, picker header
`WHO DID THEY CHOOSE? (NOT LENA — CHOSEN LAST NIGHT)`, and Lena is moved into a
collapsed `⌄ 2 they cannot choose` list annotated `last night · chosen last
night` (Erin appears there too, annotated `dead`). Night 3 repeats it for Jonas.
The token shows on the grimoire (`Seat 12, Lena, …, tokens: Survives Exe…`), the
day briefing carries `Lena survives execution today (Devil's Advocate). Announce
the execution, then that they live — never why.`, and the dusk sheet sweeps it:
`TAKEN OFF THE GRIMOIRE · Removed: Survives Execution (Devil's Advocate) from
Lena.` Executing the protected player gives the survives path:
`Jonas survives execution today. / Say: 'Jonas was executed… and remains alive.'
Do not say why.` with the button `JONAS IS EXECUTED — AND LIVES`, and the day
records `Jonas was executed and survived — the day is over.`
Screenshots `D/28-n2-devils-advocate.png`, `D/49-dusk2.png`, `D/50-killsheet-jonas.png`.

**3. Gossip — record the statement by day.** ✅ **Works** (with one gap).
Day 1 already nudges: `STILL TO DO ☐ Record Dev's public statement today
(Gossip).` with a `Record it` button, and the day card header reads
`WHAT WAS SAID · 0 recorded · 1 still to collect`. `+ Say` → tap Dev is **two
taps**, and selecting the Gossip's own seat auto-selects `Recorded as: Gossip`
(the default is otherwise `Claim`); type the line, `Add`. The row then reads
`Dev » Gossip: "There is no Minion sitting next to me."` with `✓ ✗ ?` verdict
buttons. Night 3 the Gossip step quotes it back:
`Yesterday's statement: "There is no Minion sitting next to me."` and the picker
is `WHO DIES? "THERE IS NO MINION SITTING NEXT TO ME."` — true → a player dies.
**Gap:** with the verdict left `?` (unjudged) I never saw the
`Was it true?` / `True — a player dies` / `False — nobody dies` gate the spec
describes; the card asserted `The Gossip's statement was true.` and went straight
to the kill picker. In my game the Gossip had died that night, so the
`dead — no ability` gate took priority and I had to use `RUN IT ANYWAY` — I could
not separate the two. Worth re-testing with a living, unjudged Gossip.
Screenshots `D/46-day2-say.png`, `D/47-day2-said.png`, `D/59-n3-gossip-runanyway.png`.

**4. Professor — resurrect, announce, re-run.** ✅ **Works** (ordering aside, P1-8).
The night-2 step offers only the dead (`2 Ben · dead`, `5 Erin · dead`) with
`10 they cannot choose`, and a hold-to-confirm `CONFIRM: ERIN`. Erin comes back
(10 → 11 alive), Gita gets the `No Ability` token (`O` pip) and her history reads
`N2 spent Professor` / `N2 chose Erin (Professor)`. The night sheet **grows from
12 to 13 steps** with the Grandmother's first night inserted, badged
`first night, again` and `out of order — this became true after their slot`.
Dawn says exactly what the fixture demands:
`SAY OUT LOUD, IN THIS ORDER · Announce: Ben died. · Announce: Erin is alive
again.` and `DO NOT SAY — your notes · Do not say why Erin is alive again.`
Erin's seat reads `Seat 5, Erin, Grandmother, alive, good, tokens: Alive`.
Screenshots `D/39-n2-after-professor.png`, `D/45-dawn2-sheet.png`.

**5. Lunatic.** ⚠️ **Half works.**
*Setup works.* The "Before the first night" checklist raises four Lunatic-related
rows: `The Lunatic believes / Which Demon token does the Lunatic see?` (Po,
Pukka, Shabaloth, Zombuul), `The Lunatic's "Minions" / Point out players as
Jonas's Minions.` (marked `optional`, satisfied at exactly 2 = the real Minion
count, placing `Fake Minion` on Ana and Lena), `Lunatic bluffs — Jonas / Choose 3
bluffs. The Lunatic's bluffs MAY include in-play characters.` — a **second tab**
in the bluffs sheet, independent of `Demon bluffs`, with in-play characters
offered and badged `IN PLAY`. The seat reads `shown as Po`.
*Night 2+ works.* The row is titled `Po`, holder `Jonas · seat 10`, chip
`nothing they do has any effect`, banner `Everything here is an illusion —
nothing they choose has any effect.`, a real picker
`WHO DID THEY CHOOSE? (NOTHING HAPPENS)`, button `CONFIRM: CLEO` — and **nobody
dies** (alive count unchanged, `Chosen` marker lands on Cleo). The real Demon's
step then says `The Lunatic (Jonas) chose Cleo.`
*Night 1 is broken* — see P0-2 (the row is auto-skipped) and P0-3 (the Demon is
never told who the Lunatic is).

**6. Grimoire & seat sheet.** ✅ **Mostly works.**
- **Status pips by group with the provenance ring** — yes: `!` green IMPAIRED,
  `+` blue PROTECTED, `O` grey ABILITY, `·` grey MARKER, and the ring takes the
  source character's team colour (orange on the Devil's Advocate's `Fake Minion`,
  blue on Tea Lady tokens). Visible in the night pickers too.
- **Board view with filter chips and counts** — yes: `protected 2`, `marks 3`,
  each with its pip; rows read `1 · Devil's Advocate · Ana · Devil's Advocate ·
  Minion · alive` plus full token lines
  (`Fake Minion · Lunatic · placed N1`,
  `Cannot Die · Tea Lady · placed N1 · no physical token — Tea Lady (Iris): …`).
- **Seat sheet v2** — sticky bar `Kill… / + Token / Change… / ⤺ Undo` (the in-sheet
  undo enables as soon as you change something), `STATUS` and `HISTORY` sections
  with real content (`N1 Drunk by the Sailor (Ben) · impaired`,
  `N1 chose Gita, Jonas (Chambermaid)`, `N1 woke`, `D3 "claims Tea Lady,
  neighbours good"`), `▸ About this character`, `▸ Advanced`. Only `Remove`/
  `Suspend` are broken (P1-5).
- **KillSheet from a seat** — everything the spec asks for: title `Gita dies`,
  the full `Cause` radio list with blurbs, the `What happens` preview
  (`Gita can't die. / Say: 'Gita was executed… and remains alive…'`),
  `Applies to this death · + Marked 'Cannot Die' — Tea Lady: can't die.`,
  the `Nothing can prevent this` toggle, and the buttons
  `Saved by the Tea Lady` / `They die anyway` / `Cancel`. Tapping
  `Saved by the Tea Lady` **does** record — Gita's HISTORY gains
  `D2 Gita can't die.` (no sign of the old "death prevented, nothing logged" bug).
- **Notes auto-commit** — yes: typing a note and dismissing by tapping the scrim
  persisted it as `D3 "claims Tea Lady, neighbours good"` with a `Delete` button.
  There is no `Save & close` button anywhere.
- **Privacy cover topmost** — yes: engaging it leaves exactly three nodes in the
  tree (`The grimoire is closed. Press and hold to open.` /
  `The grimoire is closed` / `Day 3 · press and hold to open`); nothing of the
  grimoire, header or tab bar survives. `hold 1500` reopens it; short taps do not.
- **12-seat circle, no overlap** — yes. `audit` finds no overlap among the seats,
  and the screenshot shows clean gaps on both flanks with the name above each
  token. Character names are (correctly, per the layout formula) dropped at 12
  seats.
- **Token picker with copy counts** — yes: `Token on Dev`, generic row
  (`Drunk`, `Poisoned`, `Protected`, `Mad`, `Used`, `No Ability`, `Good`, `Evil`,
  `?`), then `In play` grouped by character, and the Tea Lady's chip carries the
  `×2` overlay.
- **Traveller-join dialog** — opens clean (`A traveller joins`, `Name`,
  `Sits after` chips defaulting to the last seat, the traveller list with 24 dp
  tokens, `Cancel` / `Seat them` disabled while the name is blank) and `audit`
  reports no safe-area or overlap problems.
- **Spy read-only mode** — content correct, buttons unreachable (P1-7).

Other things checked that were right:
- **Tea Lady neighbours** — Gita and Hal automatically carry
  `Cannot Die · Tea Lady · no physical token`, and the day briefing says
  `Gita can't die (Tea Lady).` / `Hal can't die (Tea Lady).`
- **Night-1 order** matches the fixture exactly: 11 steps,
  `Dusk · Minion info · Po (via the Lunatic) · Demon info · Sailor · Godfather ·
  Devil's Advocate · Pukka · Grandmother · Chambermaid · Dawn`, with the Tea
  Lady, Fool, Gossip, Professor and Exorcist correctly absent.
- **Night-2 order** puts `devilsadvocate < exorcist < pukka < godfather < gossip
  < professor < grandmother < chambermaid`, as the fixture requires.
- **Impairment banner** — the night-2 Sailor card is topped with
  `IMPAIRED — Poisoned by the Pukka (Kai). Their ability does …`.
- **Gate reasons** are accurate and update live: `dead — no ability`,
  `no Gossip statement was recorded yesterday — record one on the Day tab`
  → `their ability is not working` once the Gossip is poisoned,
  `spent — this ability is once per game`, `nobody died today`,
  `the grandchild was not killed by the Demon tonight — the Grandmother lives`,
  `the Grandmother is already dead`.
- **Setup guard** lists exactly the outstanding rows, including
  `Jonas: choose the Demon token shown to the Lunatic` and
  `Lunatic bluffs — Jonas: choose 3 bluffs`.
- **Bag legality** — `8 seats · 5/1/1/1` updated to 12 correctly; the checklist's
  `Which setup branch you chose` row records the Godfather's
  `[-1 or +1 Outsider]` choice (advisory).
- **Nomination + execution** — `Lock in: Erin is ON THE BLOCK (7 of 6)`,
  the day header goes `12 alive · 6 to execute` → `11 alive · 6 to execute ·
  1 ghost vote`, and the kill sheet's `Before you execute: Nothing stops it —
  they die.` is honest.
- The already-filed harness findings (`fabled +` overlapping Search; the bottom
  tab bar's 21 px overrun) reproduce on every screen; not re-filed here.

## What I could not reach

- **Innkeeper (Safe ×2 + Drunk)**, **Courtier countdown 3→2→1→gone**,
  **Minstrel "Everyone Is Drunk" centre token**, **Assassin beating protection**,
  **Zombuul "registers dead"**, **Mastermind day** — none of these characters is
  in the user's 12-seat bag, and the brief pinned the bag to the fixture. They
  would need a second game with a different deal; the KillSheet's cause blurbs do
  already name the Innkeeper and the Assassin, and the "Nothing can prevent this /
  The Assassin, and any storyteller override." toggle is present.
- **The Gossip's `Was it true?` gate** — masked by the `dead — no ability` gate
  (see complaint 3 above).
- **A second Devil's Advocate day-execution where the protected player is the one
  swept at dusk** — the dusk sheet lists `Removed: Survives Execution … from
  Jonas` in the same sheet that offers `EXECUTE JONAS & BEGIN NIGHT`; I executed
  through the Day tab instead, so I did not confirm whether the dusk-path
  execution still honours the protection. Worth a targeted test.
- **The Lunatic's charged (`3 Attacks`) Po night** — the fixture places that token
  by hand; the picker was `1 pick` throughout my game.
