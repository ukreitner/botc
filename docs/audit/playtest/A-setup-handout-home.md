# Playtest A — Home → new game → setup → hand-out → reference / archive

Driven on `emulator-5554`, build `2020fec`, ~90 min of play.
Harness: `tools/emu/` (`ui.py`, `scenario.py`). Protocol and severity scale:
[`docs/audit/PLAYTEST-FINDINGS.md`](../PLAYTEST-FINDINGS.md).

Scenarios written (replayable from `--fresh`):

| file | what it reproduces |
|---|---|
| `tools/emu/scenarios/A_handout_actions_offscreen.py` | A-2 (hand-out action row off-screen), A-10 (seat-chip overlap) |
| `tools/emu/scenarios/A_empty_start_checklist.py` | gets to the "Before the first night" sheet in 18 steps (A-3 needs one more row — see its repro) |

Counts: **1 P0 · 9 P1 · 10 P2**.

The two findings already filed by `emu-harness` (grimoire `fabled +` overlap, bottom
tab bar gesture inset) are **not** repeated here.

---

## P0

### A-1 · P0 · Hand-out mode — the Drunk is shown the **Drunk** token

**Screen / flow** Setup → "Deal & hand out tokens" → hand-out mode → any seat
holding the Drunk (or Marionette), before the matching "believes" checklist row
is answered.

- **Repro**
  ```sh
  ./emu.sh launch emulator-5554 --fresh
  ./ui.py emulator-5554 tap "New game"
  ./ui.py emulator-5554 tap "^Trouble Brewing$"
  # paste 12 names, Randomize until the bag contains the Drunk
  # (or PIN the Drunk, then Randomize), then:
  ./ui.py emulator-5554 tap "Deal & hand out"
  ./ui.py emulator-5554 wait "HAND OUT TOKENS"
  ./ui.py emulator-5554 tap "<the Drunk's name>"      # or "Next: …" through to them
  ./ui.py emulator-5554 hold "HOLD to reveal" 1500
  ```
- **Expected** The Drunk is handed the **not-in-play Townsfolk token they
  believe they are** (wiki `Drunk`; `docs/audit/ux/setup-and-home.md` §1 row 2,
  §S6). If that token has not been chosen yet, the hand-out must refuse the seat
  or force the choice first — it must never print the word "Drunk" on a card the
  player holds.
- **Actual** The card reads **"YOU ARE / Drunk / You do not know you are the
  Drunk. You think you are a Townsfolk character, but you are not."** The very
  same screen already lists *"• The Drunk believes — Sam is the Drunk. Which
  Townsfolk token do they see?"* under **STILL TO RUN BEFORE THE FIRST NIGHT** —
  so the app knows the task is unanswered, and walks you through it anyway with
  no warning and no block. "Deal & hand out tokens" is the *primary* button on
  the setup screen, so the default path through the app blows the Drunk's cover.
  Answering the checklist row afterwards fixes the card (verified: it then reads
  "YOU ARE / Chef") and correctly clears `tokenShownAt` so the seat is re-raised —
  the machinery is all there, only the ordering guard is missing.
- **Screenshots** `tools/emu/out/A/handout-01.png` (task listed as outstanding),
  `tools/emu/out/A/handout-03-sam-drunk.png` (the leak),
  `tools/emu/out/A/handout-04-sam-chef.png` (correct card once answered)
- **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/RevealFlow.kt`
  — `HandOutMode` builds `queue` from every seat with a `characterId` (`:130-138`)
  and never consults `SetupRequirements.unmet(...)` when deciding whether a seat
  is *ready* to hand out; it only prints the unmet rows as text (`:229-247`).
  Gate the per-seat card (and the "Next:" button) on
  `RequirementKind.SHOWN_TOKEN` being satisfied for that seat, and/or open the
  picker from the hand-out screen.

---

## P1

### A-2 · P1 · Hand-out mode re-entered from the overflow menu — "Start over" and "Finish later" are unreachable

- **Repro** `./scenario.py emulator-5554 A_handout_actions_offscreen`
  (fresh launch first). Step 29's audit is clean at the bottom; step 40's is not.
- **Expected** Every control inside the safe area (`y 136..2316`).
- **Actual** Entered straight from the deal the row sits at `y 2043..2296` — fine.
  Re-entered from ⋮ → "Reveal characters to players…" the whole column is ~200 px
  lower and `ui.py audit` reports:
  ```
  === SAFE-AREA VIOLATIONS (3) ===
    #51 '<View>' [42,2241][1037,2367] @(539,2304)  click       # "Next: …"
        - bottom 51px under the navigation/gesture inset (home indicator)
    #54 '<View>' [42,2368][264,2400] @(153,2384)  click        # "Start over"
        - CENTRE UNTAPPABLE: under the bottom navigation / gesture inset
    #57 '<View>' [795,2368][1037,2400] @(916,2384)  click      # "Finish later"
        - CENTRE UNTAPPABLE: under the bottom navigation / gesture inset
  ```
  Both labels dump as `bounds [0,0][0,0]` — they are not drawn at all. The screen
  has no other exit: the only escape is the hardware Back key, which drops you on
  the privacy cover ("The grimoire is closed. Press and hold to open."). A
  storyteller mid-hand-out who wants to stop is stuck.
- **Screenshot** `tools/emu/out/A/handout-06-buttons-offscreen.png`,
  `tools/emu/out/A_handout_actions_offscreen/emulator-5554/41-screenshot.png`
- **Suspect** `RevealFlow.kt:94-107` — the `Dialog` wrapper pads with
  `overlaySafeAreaPadding()` *and* `HandOutMode` pads again with
  `safeDrawingPadding()` (`:162-168`). Inside a `Dialog` window the second one
  resolves to zero, so the column is laid out against the full 2400 px while the
  comment at `:97-100` assumes the opposite. The setup-screen entry point does
  not go through the `Dialog`, which is why only the menu path breaks.

### A-3 · P1 · "Before the first night" sheet pushes its own **Close** button under the home indicator

- **Repro** (6 checklist rows are the trigger; 5 still fit)
  ```sh
  ./emu.sh launch emulator-5554 --fresh
  ./ui.py emulator-5554 tap "New game"
  ./ui.py emulator-5554 tap "Import script (paste"
  ./ui.py emulator-5554 tapxy 540 1264
  adb -s emulator-5554 shell "input text '[{id:_meta,name:PlaytestA},ogre,lilmonsta,marionette,washerwoman,librarian,investigator,chef,empath,fortuneteller,undertaker,monk,ravenkeeper,virgin,slayer,soldier,mayor,butler,drunk,recluse,saint,poisoner,spy,scarletwoman,baron,imp]'"
  ./ui.py emulator-5554 tap "^Import$"
  ./ui.py emulator-5554 tap "^PlaytestA$"
  ./ui.py emulator-5554 tap "Start empty"
  ./ui.py emulator-5554 wait "Before the first night"
  ./ui.py emulator-5554 audit
  ./ui.py emulator-5554 tap "^Close$"      # -> OFFSCREEN
  ```
- **Expected** The sheet's only dismissal button is tappable at any row count
  (scroll the list, not the button, off the bottom).
- **Actual** With 6 rows the sheet does not scroll; `Close` lands at
  `@(540,2349)`:
  ```
  === SAFE-AREA VIOLATIONS (1) ===
    #39 '<View>' [53,2299][1027,2400] @(540,2349)  click
        - bottom 84px under the navigation/gesture inset (home indicator)
        - CENTRE UNTAPPABLE: under the bottom navigation / gesture inset (y >= 2316)
  ```
  and `ui.py tap "^Close$"` refuses with `OFFSCREEN`. Dragging the sheet handle
  down still dismisses it, so it is escapable — but the documented control is
  gone, and the row count grows with the script (a Snitch/Evil Twin/Lunatic
  script will exceed 6 routinely).
- **Screenshot** `tools/emu/out/A/checklist-close-offscreen.png`
- **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/GameExtras.kt:375-…`
  (the `SetupChecklistSheet`) — the row list needs its own
  `verticalScroll`/`weight(1f)` so the pinned footer stays above
  `WindowInsets.safeDrawing`.

### A-4 · P1 · Once dismissed, the "Before the first night" checklist cannot be reopened

- **Repro** Deal a game → the checklist sheet auto-opens → Close → ⋮
- **Expected** The checklist is the storyteller's setup contract; it must have a
  permanent entry point (spec §S4: *"and as a re-openable sheet at any time"*).
- **Actual** The 15-item overflow menu has no checklist entry (Demon bluffs,
  Storyteller notes, Show a card…, Reveal characters…, Screen dimming, Show the
  grimoire…, Fabled…, Jinxes in play, Game log, Reorder seats, A traveller
  joins…, Add an empty seat, Declare good/evil victory, Back to home). Worse,
  **"Begin night" → "Setup isn't legal yet" → "Fix setup" is a no-op**: it just
  closes the dialog and returns to the grimoire; it does not open the checklist.
  The only way back is the side effect of assigning a character to a seat, which
  re-opens the sheet by accident.
- **Screenshots** `tools/emu/out/A/menu-01.png`,
  `tools/emu/out/A/beginnight-guard.png`
- **Suspect** `GameShell.kt` — the overflow menu (~`:218-260`) and the
  "Fix setup" branch of the begin-night guard.

### A-5 · P1 · The bag "Need:" line and the four progress bars disagree with the validator

- **Repro (Sentinel)**
  ```sh
  ./emu.sh launch emulator-5554 --fresh
  ./ui.py emulator-5554 tap "New game"; ./ui.py emulator-5554 tap "^Trouble Brewing$"
  # paste 12 names; card 4 -> Sentinel; card 3 -> Randomize;
  # then swap one Outsider for a Townsfolk (6 TF / 3 OUT / 2 MIN / 1 DEM)
  ```
- **Expected** One model. If the validator accepts 3–5 Outsiders because the
  Sentinel is in play, the header must say "3 or 4 or 5 outsiders" and the bar
  must not read as incomplete.
- **Actual** The validator is Sentinel-aware — it prints
  `Outsider: 2 in bag, expected 3 or 4 or 5` — but the header still says
  `Need: 5 townsfolk · 4 outsiders · 2 minions · 1 demon` and the bars read
  `TF 6/5` and `OUT 3/4` for a bag the app then happily deals ("12 ready").
  On a Lil' Monsta script it is worse: with the "Lil' Monsta is a token" box
  ticked the header still demands `1 demon`, and one observed state showed **all
  four bars at target (TF 4/4, OUT 1/1, MIN 2/2, DEM 1/1) while the issue list
  underneath said `Townsfolk: 4 in bag, expected 5` and the deal was blocked** —
  the same screen contradicting itself twice.
- **Screenshots** `tools/emu/out/A/setup-12-sentinel-header-mismatch.png`,
  `tools/emu/out/A/setup-26-need-vs-issue.png`
- **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/SetupScreen.kt:745-790`
  — `Setup.allowedDistributions(playerCount, selected)` is called **without the
  fabled ids and without the setup acknowledgements**, while `validateBag`
  receives both. The bars additionally collapse the branch set to
  `allowed.map { it.count(team) }.maxOrNull()` (`:772`), so a legal lower branch
  always renders as "incomplete".

### A-6 · P1 · "seat N of M" on the hand-out card is the shuffle index, not the seat number

- **Repro** `./scenario.py emulator-5554 A_handout_actions_offscreen`, then
  ```sh
  ./ui.py emulator-5554 tap "Next: <first name>"
  ./ui.py emulator-5554 find "seat .* of"
  ```
- **Expected** "seat 11 of 12" for the player sitting in seat 11 — the number a
  storyteller uses to hand the phone to the right person.
- **Actual** Observed with roster
  `Uri, Dana, Ari, Sam, Mia, Jon, Lea, Tom, Ben, Ivy, Max, Zoe`:
  Max (**seat 11**) → "seat 1 of 12"; Sam (**seat 4**) → "seat 7 of 12";
  traveller Gus (**seat 13**) → "seat 4 of 13". The default hand-out order is
  shuffled (correctly, per §S6/#25) and the label reports the queue position
  under the word "seat". In "Seat order" mode the two coincide, hiding the bug.
- **Screenshot** `tools/emu/out/A/handout-02-passto.png` (Max, "seat 1 of 12")
- **Suspect** `RevealFlow.kt:152` (`position = queue.indexOfFirst { … } + 1`) and
  `:374` (`"seat $position of $total"`). Either pass `player.seatNumber` or
  reword to "hand-over 1 of 12".

### A-7 · P1 · A traveller's alignment is never asked for, but the hand-out asserts "YOU ARE GOOD"

- **Repro**
  ```sh
  # in any running game
  ./ui.py emulator-5554 tap "^Menu$"
  ./ui.py emulator-5554 tap "A traveller joins"
  # type a name, pick a character, "Seat them"
  ./ui.py emulator-5554 tap "^Menu$"; ./ui.py emulator-5554 tap "Reveal characters to players"
  ./ui.py emulator-5554 tap "<traveller name>"
  # hold twice: card 1 = character, card 2 = alignment
  ```
- **Expected** A traveller's alignment is a storyteller choice (wiki
  `Travellers`; spec §1 row 21, §S4 `traveller.alignment.<seatId>`,
  §S6 *"travellers → ask the storyteller"*). The alignment page must appear only
  **after** it has been set.
- **Actual** The "A traveller joins…" dialog offers Name / Sits after /
  Character and nothing else — no Good/Evil control. The hand-out screen's
  outstanding-task list did not gain a traveller row after seating them either
  (it still showed only "• Demon bluffs"); I did not re-open the full checklist
  sheet to confirm the row is absent there too. The hand-out then shows the
  traveller a second page reading
  **"YOU ARE GOOD — You are a Traveller. This is the side you play for."**
  The app has decided the alignment on the storyteller's behalf and told the
  player. (The Ogre exception is implemented correctly — see "What worked".)
- **Screenshots** `tools/emu/out/A/traveller-02.png` (dialog, no alignment),
  `tools/emu/out/A/handout-09-traveller-alignment.png`
- **Suspect** `GameExtras.kt` traveller dialog + `engine/.../SetupRequirements.kt`
  (no per-traveller alignment row) + `RevealFlow.kt` page builder.

### A-8 · P1 · Randomize / "Fill the rest" produce illegal bags on a Lil' Monsta script

- **Repro** Import the `PlaytestA` script from A-3, 10 seats, expand BAG:
  1. tick "Lil' Monsta is a token, not a seat…" → the validator switches to
     `Minion: expected 3`, `Demon: expected 0`. Tap **Randomize** →
     the bag comes back **7/0/2/1** (a Demon in a seat, one Minion short),
     immediately rejected by the app's own validator.
  2. Untick it, tap **Randomize** → Lil' Monsta is put in the bag but the
     acknowledgement is not ticked, so the bag is rejected with the only
     guidance being "Lil' Monsta is a token, not a seat. Put 3 Minions and no
     Demon in the bag."
  3. On an 8-seat table, **Fill the rest** produced `IN THE BAG · 9 / 8`.
- **Expected** The one-tap bag builders must respect the acknowledgement they
  are shown next to, and must never overshoot the seat count.
- **Actual** as above. The escape is "Deal anyway — I know what I'm doing",
  which disables all checking.
- **Screenshots** `tools/emu/out/A/setup-23-lilmonsta-bag.png`,
  `tools/emu/out/A/setup-24-fillrest-bug.png`,
  `tools/emu/out/A/setup-25-fillrest-overfill.png`
- **Suspect** `engine/.../GameActions.randomBag` / `Setup.kt` — `randomBag` is
  not given `setupChoices` / the seatless-token rule; `SetupScreen.kt:833-834`
  passes only `keepCurrent`.

### A-9 · P1 · The bag list jumps under your finger every time the issue list resizes

- **Repro** Any script; expand BAG; tick a setup-modifying character (Baron,
  Lil' Monsta) and then immediately tick the row below it.
- **Expected** Ticking a character does not move the rows you are about to tap.
- **Actual** The "Need:" line, the four bars and the issue list all sit **above**
  the scrolling character list in the same scroll container, and their combined
  height changes by 40–160 px whenever the bag's legality changes. Every such
  change shifts the whole list; the next tap lands on a different character.
  This cost me three mis-built bags during the session and is the reason several
  hand-checked bags below came out wrong.
- **Screenshot** `tools/emu/out/A/setup-24-fillrest-bug.png` (compare with
  `setup-10-randomized.png` — same screen, list 300 px lower)
- **Suspect** `SetupScreen.kt` — the bag card and the character list share one
  scroll; either give the issue block a fixed height, or move the character list
  into its own `LazyColumn` with the card pinned.

### A-10 · P1 · The hand-out roster's name chips overlap by up to 36 %

- **Repro** `./scenario.py emulator-5554 A_handout_actions_offscreen` (step 29)
- **Expected** No two hit targets fight over the same pixels — tapping a name
  **re-shows that player's identity**, so a mis-tap shows the wrong person's
  character to whoever is holding the phone.
- **Actual**
  ```
  === OVERLAPPING CLICKABLES (8) ===
    36% overlap:
        #12 '<View>' [42,327][205,453] @(123,390)  click     # Max
        #30 '<View>' [42,408][195,532] @(118,470)  click     # Sam
    27% overlap: … 23% … 22% … 20% … 12% … 10% … 8%
  ```
  The chips are 43 px tall in a `FlowRow` with 6 dp spacing; Compose expands each
  to the 48 dp minimum touch target and the two rows collide.
- **Screenshot** `tools/emu/out/A/handout-01.png`
- **Suspect** `RevealFlow.kt:191-218` — `FlowRow(horizontalArrangement = spacedBy(6.dp))`
  with no `verticalArrangement`; give the rows ≥48 dp height and vertical spacing.

---

## P2

### A-11 · P2 · The bag has no search field
`ReferenceScreen` searches names *and* abilities; the bag builder has none at
all, so on a 25-character import you scroll a 25-row list to find one character.
(The pre-rewrite `SetupScreen` had a name search — this is a regression.)
Screenshot `tools/emu/out/A/setup-10-randomized.png`.
Suspect `SetupScreen.kt` `BagStage`.

### A-12 · P2 · The traveller toggle is an unlabelled em-dash
Each seat row in TABLE has a control whose `content-desc` is literally `—` until
you turn it on, when it becomes `TRAV`. Nothing on screen says what it does until
you press it; the explanatory line ("Seats marked TRAV are Travellers…") is at
the bottom of the card, below "Add seat". Dump:
`#31 '<View>' [738,626][890,752] @(814,689) click / #32 '—'`.
Screenshots `tools/emu/out/A/setup-02-tb.png`, `setup-03-dash-tap.png`.
Suspect `SetupScreen.kt` seat row.

### A-13 · P2 · Plural grammar throughout setup
`"7 seats + 1 travellers · 5/0/1/1"`, `"Need: … 1 outsiders · 1 minions · 1 demon"`,
`"Bag has 1 characters for 8 players"`.
Screenshots `setup-03-dash-tap.png`, `setup-26-need-vs-issue.png`.
Suspect `SetupScreen.kt:718, 752`, `GameActions.validateBag` messages.

### A-14 · P2 · Card 4 is called "FABLED & HOUSE RULES" but holds no house rules
The only house rule ("allow duplicates of any character") lives in card 3 (BAG).
Card 4 contains the Fabled list and nothing else; its collapsed summary reads
`none` / `OK`. Screenshot `tools/emu/out/A/setup-11-fabled.png`.

### A-15 · P2 · A completed checklist row does not show the answer
After choosing the Drunk's token the row still reads *"The Drunk believes — Sam
is the Drunk. Which Townsfolk token do they see?"* with a ✓. The spec's own
mock-up shows `✓ Drunk sees  Chambermaid`. You must reopen the picker to find out
what you picked. Screenshot `tools/emu/out/A/grimoire-01.png`.
Suspect `GameExtras.kt` checklist row.

### A-16 · P2 · Five identical "The bag is not legal yet" rows
"Start empty · assign by hand" produces a checklist whose first five rows all
carry the same title, differing only in the subtitle
(`Bag has 0 characters for 8 players` / `Townsfolk: 0 in bag, expected 5` / …).
For a deliberately empty start this is also the wrong framing — the storyteller
just told the app they are assigning by hand.
Screenshot `tools/emu/out/A/checklist-startempty.png`.

### A-17 · P2 · "Lil' Monsta is in play" is asserted for a game with an empty bag
Same screen as A-16 on the `PlaytestA` script: the checklist states
*"Lil' Monsta is in play — Lil' Monsta is a token, not a seat: one extra Minion,
no Demon in the bag"* for a game with **0 characters assigned**. The row is
raised from the *script*, not from the bag.
Screenshot `tools/emu/out/A/checklist-close-offscreen.png`.
Suspect `engine/.../SetupRequirements.kt`.

### A-18 · P2 · The hand-out screen's "STILL TO RUN" list is a subset with no "+N more"
Immediately after the deal the hand-out screen listed 2 rows (Drunk, Demon
bluffs) while the checklist sheet reached from the same screen listed 4
(Drunk, Fortune Teller red herring, Demon bluffs, Sentinel). The filter is
deliberate (`HANDOVER_KINDS`, `RevealFlow.kt:280-285`) but nothing tells the
storyteller they are looking at a subset.
Screenshots `tools/emu/out/A/handout-01.png` vs `tools/emu/out/A/grimoire-01.png`.

### A-19 · P2 · A successful script import says nothing and sorts to the bottom
Pasting valid JSON closes the dialog with no confirmation; the new script is
appended **below** the three built-ins (spec §S8 asks for top-of-list plus a
"new" badge and a success snackbar). It also shows no author line where the
built-ins do.
Screenshots `tools/emu/out/A/import-01.png`, `import-02-done.png`.
Suspect `SetupScreen.kt` `ScriptStage` + `GameViewModel.importScript`.

### A-20 · P2 · Character rows clipped by the sticky "IN THE BAG" tray swallow taps
A row whose bounds are clipped at the tray's top edge (e.g.
`#74 '<CheckBox>' [33,1808][159,1836] @(96,1822)`, tray starts at `y=1836`)
did not toggle when tapped at its centre, even though the centre is 14 px above
the tray. Scrolling it clear made the same tap work. Low confidence on the exact
mechanism — flagged so the fix wave can check the tray's touch bounds.
Screenshot `tools/emu/out/A/setup-26-need-vs-issue.png`.

---

## What worked (safe ground)

Exercised and correct — no need to re-test these:

- **Home** — all three entries, `build 2020fec` footer, `audit` clean. Resume
  card shows script · player count · phase · **relative timestamp**
  ("saved just now" / "saved 1 minute ago") — spec #45 fixed.
- **New-game guard** — "A game is still in progress… Starting a new game
  archives it." with `[Archive & start new] [Resume instead] [Cancel]`, exactly
  as §S9 specifies. Spec #1 fixed.
- **Archive** — "PAST GAMES" section on Home with `[Open] [Resume]`. "Open"
  gives a read-only sheet listing every seat with character and
  `Drunk (shown Chef)`, plus `Reopen this game` / `Delete…`. Two archived games
  survived across three new games. Spec #38 fixed.
- **Setup wizard → one screen** — the four collapsible cards (SCRIPT / TABLE /
  BAG / FABLED) with per-card `OK` badges and a persistent action bar; `audit`
  clean on every state I reached. Spec §S1 shipped.
- **Paste-a-list** — comma/newline/semicolon split, live preview
  ("12 seats: Uri, Dana, …"), `Use these 12 seats`. Grew 8 seats → 12 correctly.
- **Roster memory** — `Last table (12)` chip on the next new game restored all
  12 names. Spec §S2 shipped.
- **Distributions** — 8 → `5/1/1/1`, 10 → `7/0/2/1`, 12 → `7/2/2/1`;
  Baron correctly moved 12p to `5/4/2/1`; marking a seat TRAV correctly
  dropped 12 → `7 seats + 1 travellers · 5/0/1/1`.
- **PIN / BAN** — PIN highlights and is honoured by Randomize (the tray shows
  `PIN Drunk`).
- **Sentinel** — selectable from card 4 *before* the bag (spec #10 fixed) and
  honoured by the validator (`expected 3 or 4 or 5`). Only the header lags (A-5).
- **Lil' Monsta deal** — with the acknowledgement ticked, `deal` correctly seats
  10 players with **no Demon** and 3 Minions; the Lil' Monsta token is not
  dealt to a seat. Spec #8 fixed at the deal level.
- **Script import (paste JSON)** — a 25-id array imported and became a fully
  playable script including Experimental characters.
- **Hand-out mode** — press-and-hold reveal (release hides immediately),
  shuffled order with a `Seat order` toggle, `✓ / ▶ / ○` progress that is real
  state (survives leaving and re-entering), tap-a-name to re-show one seat, and
  `tokenShownAt` correctly cleared when a seat's identity changes. Spec §S6
  shipped apart from A-1/A-2/A-6.
- **Believed-character colour rule** — an alignment-flipped Saint's card is
  painted in the **Outsider** colour, not red. Spec #18 fixed.
- **Ogre exception** — a flipped Ogre gets exactly **one** card ("seat 1 of 1",
  no `card 1 of 2`), i.e. no alignment page. Verified against a flipped Saint,
  which *does* get a second "YOU ARE EVIL" page. This is the rules-critical case
  and it is right. `tools/emu/out/A/handout-12-ogre-passto.png`, `handout-13-ogre-card.png`.
- **Begin-night guard** — "Setup isn't legal yet" lists the unmet rows and offers
  `[Fix setup] [Start the night anyway]`; the escape hatch survives (only
  "Fix setup" itself is a no-op, A-4).
- **In-game Script tab** — search over names *and* abilities, an `In play only`
  filter, and an `IN PLAY · seat 1 (Player 1)` badge on each row. Spec #26 fixed.
- **Character detail page** — ability, in-play seat, FIRST NIGHT / OTHER NIGHTS
  prose from `night_guide.json`, REMINDERS, JINXES. Spec #31 fixed.
- **Night order tab** — numbered, with the sheet reminder text and a
  `not in play` badge per row. Spec #27 fixed.
- **Library** — scrollable script tabs plus an `Import` action in the top bar.
  Spec #29 fixed. `audit` clean once scrolled to either end (the mid-scroll
  violation is just a partially-drawn list row, not a bug).

## Not reached, and why

- **15 players** — I checked 8 / 10 / 12 and the traveller-adjusted 7; 15 was
  dropped for time. The distribution label is a pure function of the seat count
  (`SetupScreen.kt:1085`), so 15 is very likely fine.
- **Bad Moon Rising specifically** — every setup mechanic I wanted (Baron,
  Sentinel, Drunk, Lil' Monsta, Ogre, Marionette) was reachable on Trouble
  Brewing or the imported script, and BMR's script card behaves identically. No
  BMR-only path was exercised.
- **Marionette prompt** — I never got a Marionette *dealt*: on the imported
  script the bag builder kept substituting Lil' Monsta (A-8) and the list jumped
  under my taps (A-9). The Marionette checklist row exists in
  `SetupRequirements`, but I did not see it fire.
- **"Duplicates" house rule** — the checkbox is present and toggles, but I never
  built a duplicate bag to see whether the validator and the deal honour it.
- **Import from file (.json)** — needs the Android SAF picker; skipped as
  out of budget for headless driving.
- **Import a share LINK (`?script=`)** — not tested; the paste dialog advertises
  it (spec #33/#34 territory).
- **Two same-named imports overwriting each other** (spec #32) — not tested.
- **Rotation / landscape** — impossible headless, as briefed.
