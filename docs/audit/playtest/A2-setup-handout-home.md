# Playtest A2 (re-test) — Home → new game → setup → hand-out → reference / archive

Second-fleet re-test of the area first played in
[`A-setup-handout-home.md`](A-setup-handout-home.md). Driven on
`emulator-5560`, build `978692b` (fix waves 1–2 + wave-2 polish merged; the APK
reports `build dev`). Protocol and severity scale:
[`../PLAYTEST-FINDINGS.md`](../PLAYTEST-FINDINGS.md).

**Counts: 3 P0 · 1 P1 · 6 P2.** All twenty of A-1 … A-20 are **VERIFIED FIXED**;
two of them (A-5, A-19) are fixed for the case that was filed and leak in a
neighbouring case, which is filed here as new work.

Scenarios written (all replayable from `./emu.sh launch emulator-5560 --fresh`):

| file | steps | what it proves |
|---|---|---|
| `tools/emu/scenarios/A2_lilmonsta_baron.py` | 72/72 | **A2-1** — Lil' Monsta's bag shape swallows the Baron's `[+2 Outsiders]` |
| `tools/emu/scenarios/A2_lilmonsta_ack_off.py` | 74/74 | **A2-2** — unticking the seatless box changes nothing on screen, and deals a game its own checklist rejects |
| `tools/emu/scenarios/A2_lilmonsta_ghost.py` | 76/76 | **A2-3** — taking Lil' Monsta OUT of the bag deals an 8-player table with **no Demon anywhere** |
| `tools/emu/scenarios/A2_import_overwrite.py` | 34/34 | **A2-4 / A2-5** — a second unnamed import destroys the first; the success banner goes stale |
| `tools/emu/scenarios/A2_checklist_over_seat_sheet.py` | 32/32 | characterises the known wave-3 item (checklist raises over the open seat sheet) |

The first tester's scenarios were re-run on this build before anything else and
**all pass**: `A_fix_a1_drunk_gate` 49/49, `A_fix_a3_checklist_close` 27/27,
`A_fix_a5_sentinel_header` 30/30, `A_fix_a8_lilmonsta_bag` 36/36,
`A_fix_a9_stable_bag_list` 25/25, `A_handout_actions_offscreen` 41/41,
`A_empty_start_checklist` 19/19, `G_fix_bag_tray` 27/27 — every `audit` inside
them clean.

---

## Verdict on A-1 … A-20

| # | verdict | proof |
|---|---|---|
| **A-1** Drunk shown the Drunk token | **VERIFIED FIXED** | `A_fix_a1_drunk_gate` 49/49. By hand on a 12-seat A2Monsta deal: the roster opens "2 seats cannot be handed out yet · Lea — Lea is the Drunk…", the chips are red `!`, and after answering, Lea's card reads `YOU ARE / Librarian` (`out/A2/drunk-card-librarian.png`). The same gate covers the **Lunatic** ("• Jon — Which Demon token does the Lunatic see?") and the **Marionette** ("• Player 6 — Which not-in-play good token does the Marionette see?"). |
| **A-2** hand-out actions off-screen from ⋮ | **VERIFIED FIXED** | `A_handout_actions_offscreen` 41/41. Step-40 `audit` (menu re-entry) reports 17 clickables, "safe area: OK". `Finish later` tappable at `@(917,2233)`, `Start over` `@(153,2233)`. |
| **A-3** checklist `Close` under the home indicator | **VERIFIED FIXED** | `A_fix_a3_checklist_close` 27/27 on the 25-id `PlaytestA` import; `Close` at `@(540,2128)`, audit clean. Also clean at 9 rows on the A2Monsta deal. |
| **A-4** checklist not reopenable / "Fix setup" a no-op | **VERIFIED FIXED** | Overflow menu's **first** row is `Before the first night… · 5 to do` `@(720,220)`; hand-out has a `Checklist` button `@(530,2233)` and an `Answer these now` shortcut; and `Begin night → Setup isn't legal yet → Fix setup` `@(761,1531)` now opens the checklist sheet. |
| **A-5** header vs validator disagree | **VERIFIED FIXED** *(but see **A2-1**)* | `A_fix_a5_sentinel_header` 30/30. BMR-10 with a Godfather renders the bracket properly: `Need: 6 or 7 townsfolk · 0 or 1 outsider · 2 minions · 1 demon`. The Lil' Monsta case the finding also mentioned is now self-consistent (`7 townsfolk · 2 outsiders · 3 minions · 0 demons`, bars at target, no contradicting issue list) — but it is **self-consistently wrong** when a Baron is in the same bag: A2-1. |
| **A-6** "seat N of M" is the shuffle index | **VERIFIED FIXED** | Roster `Uri,Dana,Ari,Sam,Mia,Jon,Lea,Tom,Ben,Ivy,Max,Zoe`: Jon → `seat 6 of 12`, Lea → `seat 7 of 12`, the traveller seated after Zoe → `seat 13 of 13 · card 1 of 2`. |
| **A-7** traveller alignment asserted, never asked | **VERIFIED FIXED** | Seating a traveller raises a checklist row (`His's alignment — Is His good or evil?`) and the hand-out **gates** the seat: "1 seat cannot be handed out yet · His — Is His good or evil?". Only after answering `Evil` does card 2 appear, reading `YOU ARE EVIL / You are a Traveller. This is the side you play for.` (`out/A2/traveller-alignment-card.png`). |
| **A-8** Randomize / Fill produce illegal Lil' Monsta bags | **VERIFIED FIXED** | `A_fix_a8_lilmonsta_bag` 36/36. At 12 seats with the box ticked, Randomize → `7/2/3/0`, `12 / 12`, card 3 `OK`. A roll that draws Lil' Monsta now ticks the box itself. `Fill the rest` never overshoots (8/8 on an 8-seat table, checked twice). *(The **box itself** is broken in both directions — A2-2, A2-3 — but the builders are not.)* |
| **A-9** bag list jumps under your finger | **VERIFIED FIXED** | `A_fix_a9_stable_bag_list` 25/25; ticking the Baron/Lil' Monsta by hand no longer moved the rows below in any of ~15 hand-built bags this session. |
| **A-10** hand-out name chips overlap | **VERIFIED FIXED** | `audit` "overlap: OK" on 7-, 8-, 12-, 13- and 15-name rosters (`out/A2/handout-15.png`). |
| **A-11** no search in the bag | **VERIFIED FIXED** | `Search characters and abilities` `@(392,1430)` in card 3, with a `Clear the search` trailing button. |
| **A-12** traveller toggle is an unlabelled em-dash | **VERIFIED FIXED** | `#65 'Mark seat 4 as a Traveller' [738,1247][890,1352]` / label `TRAV`. |
| **A-13** plural grammar | **VERIFIED FIXED** | `Bag has 1 character for 8 players`, `Need: 5 townsfolk · 1 outsider · 1 minion · 1 demon`, `7 seats + 1 traveller · 5/0/1/1`, `Need: … 0 demons`. |
| **A-14** card 4 has no house rules | **VERIFIED FIXED** *(residual → **A2-8**)* | Card 4 now carries a `HOUSE RULES` section with `Secret votes` `@(164,1103)` above the `FABLED` list, and its summary reads `no fabled · by the book`. One house rule (`allow duplicates`) is still in card 3. |
| **A-15** answered row does not show the answer | **VERIFIED FIXED** | `✓ The Drunk believes / Chef`, `✓ The Lunatic believes / Imp`, `✓ The Marionette believes / Washerwoman`, `✓ His's alignment / Evil`, `✓ Lil' Monsta is in play / Confirmed`. |
| **A-16** five identical "bag is not legal yet" rows | **VERIFIED FIXED** | "Start empty" now yields distinct titles — `Bag has 0 characters for 8 players` / `Townsfolk: 0 in bag, expected 4 or 5 or 6` / `Minion: 0 in bag, expected 1` / `Demon: 0 in bag, expected 1` — and the empty-start subtitle is reframed to `Assign a character to every seat, or deal a bag from setup.` |
| **A-17** "Lil' Monsta is in play" asserted for an empty bag | **VERIFIED FIXED** | `Start empty` on the A2Monsta (Lil' Monsta) script at 7 seats raises 5 rows and **no** Lil' Monsta row. |
| **A-18** hand-out "STILL TO RUN" is a silent subset | **VERIFIED FIXED** | `#59 '+ 6 more on the checklist — nothing to hand over for them.' [42,1290][776,1320]`. |
| **A-19** import says nothing, sorts to the bottom, no author | **VERIFIED FIXED** *(new gap → **A2-4/A2-5**)* | `Imported "A2Monsta" — 27 characters, now selected.`; the row sorts **above** the three built-ins with a `•` badge and `by ReTesterA2 · 27 characters`, plus a `Delete script` button. The gap is the **overwrite** path. |
| **A-20** rows clipped by the sticky tray swallow taps | **VERIFIED FIXED** | `G_fix_bag_tray` 27/27 (the Imp row toggles at its own centre, 8/8 → 7/8) and `ui.py` now refuses scrolled-out taps. |

---

## P0

### A2-1 · P0 · Setup → BAG — Lil' Monsta's bag shape **swallows the Baron's `[+2 Outsiders]`**

**Screen / flow** New game → any script holding both Lil' Monsta and a
setup-modifying character → card 3 BAG, 12 players.

- **Repro**
  ```sh
  ./emu.sh launch emulator-5560 --fresh
  ./scenario.py emulator-5560 A2_lilmonsta_baron      # 72/72, and every assert is the bug
  ```
  By hand: import the A2Monsta script (the scenario's `SCRIPT_CHUNKS`), 12 seats,
  card 3, tick **Baron** and **Lil' Monsta** by hand, then `Fill the rest`.
- **Expected** Both setup brackets apply. At 12 players the printed distribution
  is `7/2/2/1`; Lil' Monsta ("*a Minion has the Lil' Monsta token, no Demon
  player*") makes it `7/2/3/0`; the Baron (`[+2 Outsiders]`, paid for by
  Townsfolk) makes it **`5/4/3/0`**. The header should ask for 4 Outsiders, and a
  Baron bag holding only 2 should be rejected.
- **Actual** The header stops after the first step and the validator agrees with
  it, so a Baron bag with the **printed** 2 Outsiders passes:
  ```
  #35 'Need: 7 townsfolk · 2 outsiders · 3 minions · 0 demons' [64,779][935,872] @(499,825)
  #41 '  2/2'   (OUT bar, at target)                          [958,934][1016,964] @(987,949)
  #33 'OK'      (card 3 badge)                                [900,692][937,722] @(918,707)
  #118 'Deal & hand out tokens  (12 ready)'                   [264,2087][816,2145] @(540,2116)
  ```
  `Fill the rest` builds exactly that bag (tray: `Lil' Monsta · Organ Grinder ·
  Spy · Baron · Butler · Drunk · Ravenkeeper · …`) and the deal seats it. With
  Lil' Monsta **absent** from the same bag the Baron is honoured correctly
  (`Outsider: 0 in bag, expected 4`), so this is specifically the shape
  overriding the modifier.
  A storyteller running this gets a 12-player Baron game with two Outsiders — a
  wrong table, silently.
- **Screenshots** `tools/emu/out/A2_lilmonsta_baron/emulator-5560/68-screenshot.png`
  (the bag), `71-audit.png`, `72-screenshot.png`
- **Suspect** `engine/src/main/kotlin/com/clocktower/engine/Setup.kt:838-847` —
  `"lilmonsta" -> BagShape(townsfolk = base.townsfolk..base.townsfolk,
  outsiders = base.outsiders..base.outsiders, minions = …, demons = 0..0)` pins
  **all four** teams to the *printed* numbers. `Setup.kt:514` then does what its
  own comment says — "A BagShape REPLACES the distribution check for the teams it
  pins" — so `checkedTeams` (`:528`) comes out empty and
  `allowedDistributions(…)`, which is where the Baron lives, is never consulted.
  `bagTargets` takes the same branch at `:685` (`pinned != null ->
  TeamTarget(pinned.toList())`), which is why the header agrees.
  The Lil' Monsta shape should pin only `minions`/`demons` and express the
  Townsfolk/Outsider halves as a *delta on the allowed distributions*, so other
  modifiers still compose.

### A2-2 · P0 · Setup → BAG — the "Lil' Monsta is a token" tick-box is **inert**, and unticking it deals a game the app itself rejects

**Screen / flow** New game → Lil' Monsta script → card 3 → the acknowledgement
row, with Lil' Monsta already in the bag.

- **Repro**
  ```sh
  ./emu.sh launch emulator-5560 --fresh
  ./scenario.py emulator-5560 A2_lilmonsta_ack_off   # 74/74
  ```
  (12 seats; tick Lil' Monsta, `Fill the rest` — which ticks the box for you —
  then tap the box at `@(128,1136)` to untick it.)
- **Expected** The box is a decision. Turning it off must either put the
  distribution back to `7/2/2/1` and flag the now-illegal bag, or refuse to turn
  off while the token is in the bag. Whatever the screen says at the moment you
  press Deal must be what the dealt game believes.
- **Actual** Unticking changes **nothing** on screen — same header, same bars,
  same badge, same button:
  ```
  #49 '<CheckBox>' [65,1073][191,1199] @(128,1136)  click        <- no CHECKED flag
  #35 'Need: 7 townsfolk · 2 outsiders · 3 minions · 0 demons'   <- unchanged
  #47 '  0/0'  (DEM bar, "at target")                            <- unchanged
  #93 'IN THE BAG · 12 / 12'                                     <- unchanged
  #118 'Deal & hand out tokens  (12 ready)'                      <- unchanged
  ```
  …because `Setup.shapesFor` reads the shape off the **bag**, not off the box.
  But the box is the *only* thing that writes the decision into the game, so the
  dealt game does not know Lil' Monsta is seatless. Two taps later the checklist
  opens on four blocking rows for the bag that was "OK" a second earlier:
  ```
  #16 'Townsfolk: 7 in bag, expected 5'   #24 'Minion: 3 in bag, expected 2'
  #20 'Outsider: 2 in bag, expected 4'    #28 'Demon: 0 in bag, expected 1'
  #32 'Lil' Monsta is in play'  (unanswered)
  ```
  (The Townsfolk/Outsider pair only appears when the roll drew a Baron — that is
  A2-1 seen from the other side.) It is recoverable — confirming the
  "Lil' Monsta is in play" row clears all four — but nothing says so, and the
  storyteller has just been told their legal bag is illegal in four ways.
- **Screenshots** `tools/emu/out/A2/a2-ack-off-still-ok.png` (untick, nothing
  moves), `tools/emu/out/A2/a2-2-illegal-after-deal.png` (four rows),
  `tools/emu/out/A2_lilmonsta_ack_off/emulator-5560/74-screenshot.png`
- **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/SetupScreen.kt:208`
  `val seatlessIds = if (seatlessAck) seatlessCandidates.map { it.first.id } else emptyList()`
  versus `engine/.../Setup.kt:919`
  `val ids = (bag.map { it.id } + inPlayIds)…` — the engine raises the shape from
  the bag OR the ack, the deal (`SetupScreen.kt:568-570`) from the ack alone.
  Make one of them the single source: either derive `seatlessAck` from
  `lilmonsta in bagIds` (a label, not a control), or exclude bag membership from
  `shapesFor` for `forbidInBag` ids.

### A2-3 · P0 · Setup → BAG — take Lil' Monsta **out** of the bag and the app deals a table with **no Demon at all**

**Screen / flow** Same row, the other direction: the box stays ticked after the
token leaves the bag.

- **Repro**
  ```sh
  ./emu.sh launch emulator-5560 --fresh
  ./scenario.py emulator-5560 A2_lilmonsta_ghost     # 76/76
  ```
  8 seats on the A2Monsta script: tick Lil' Monsta → `Fill the rest` (which ticks
  the acknowledgement) → search `monsta` again and **untick the Lil' Monsta row**
  → `Fill the rest`.
- **Expected** Removing the Lil' Monsta token from the bag removes Lil' Monsta
  from the game: back to `Need: 5 townsfolk · 1 outsider · 1 minion · 1 demon`,
  and a bag with no Demon must be rejected.
- **Actual** The acknowledgement is bound to the **script**, not the bag
  (`seatlessCandidates` is built from `characters`), so the tick survives the
  token's removal and the whole screen goes on pretending:
  ```
  #35 'Need: 5 townsfolk · 1 outsider · 2 minions · 0 demons' [64,779][916,872] @(490,825)
  #47 '  0/0'  (DEM bar)                                      [958,1026][1016,1056] @(987,1041)
  #49 '<CheckBox>' [65,1073][191,1199] @(128,1136)  click,CHECKED
  #33 'OK'                                                    [900,692][937,722] @(918,707)
  #117 'Deal & hand out tokens  (8 ready)'                    [275,2087][806,2145] @(540,2116)
  ```
  The tray holds eight ordinary characters and **no Lil' Monsta chip**, and the
  deal produces a table with no Demon anywhere:
  ```
  #26 'Seat 1, Player 1, Scarlet Woman, alive, evil'
  #31 'Seat 2, Player 2, Poisoner, alive, evil'
  #36 'Seat 3, Player 3, Drunk, alive, good, drunk or poisoned…'
  #42 'Seat 4, Player 4, Chef, alive, good'      … 8 seats, no Imp, no Lil' Monsta
  ```
  The checklist then reports `✓ Lil' Monsta is in play — Confirmed` and the first
  night still schedules `3. Lil' Monsta · 1 pick` for a token that was never
  dealt and is not in the bag. Two Minions, no Demon, and a night order that
  wakes them for a Demon nobody holds: the storyteller would run this table.
- **Screenshots** `tools/emu/out/A2/a2-3-no-demon-legal.png` (the setup screen
  calling it legal), `tools/emu/out/A2_lilmonsta_ghost/emulator-5560/68-screenshot.png`
  (checklist "Confirmed"), `72-screenshot.png` (seats), `76-screenshot.png`
  (night order)
- **Suspect** `SetupScreen.kt:196-208` — `seatlessCandidates` is
  `characters.mapNotNull { … bagShapeFor(c.id, …) }`, i.e. every `forbidInBag`
  character **in the script**, and `seatlessAck` is a bare `rememberSaveable`
  boolean that nothing resets. Same one-line root cause as A2-2; fixing that
  fixes this. Belt and braces: `Setup.validateBag` should reject
  `demons == 0` when no `forbidInBag` token is actually present.

---

## P1

### A2-4 · P1 · Script import — two imports without `_meta` collide, and the second **silently destroys** the first

- **Repro**
  ```sh
  ./emu.sh launch emulator-5560 --fresh
  ./scenario.py emulator-5560 A2_import_overwrite    # 34/34
  ```
  i.e. New game → `Import script (paste link or JSON)` → paste a **bare id
  array** (`[washerwoman,librarian,…,imp]`, 22 ids — the exact form the dialog's
  own placeholder advertises: `… or ["wash…`) → Import. Then import a different
  bare array (12 ids).
- **Expected** Two imports, two scripts — or, if overwrite-by-name is the design,
  a warning ("a script called *Imported script* already exists — replace it?")
  and a way out.
- **Actual** Both are filed under the fallback name `Imported script`, both get
  the id `imported-importedscript`, and the second replaces the first with no
  prompt, no snackbar and no undo. The script list ends with one row:
  ```
  #18 'Imported script · 12 characters' [122,380][546,417] @(334,398)
  #23 'Imported script  •'              [85,546][363,589] @(224,567)
  #24 '12 characters'                   [85,589][274,626] @(179,607)
  ```
  The 22-character script is gone. The same thing happens across the Library's
  own `Import` action. Importing two custom scripts is a flow you cannot
  complete unless both happen to carry a `_meta` name.
- **Screenshot** `tools/emu/out/A2_import_overwrite/emulator-5560/20-screenshot.png`
  (after the first) and `34-screenshot.png` (after the second)
- **Suspect** `engine/src/main/kotlin/com/clocktower/engine/Script.kt:39` +
  `:73` — `fun parse(text, fallbackName = "Imported script")` and
  `id = "imported-" + Character.normalizeId(name)`, so every unnamed import
  shares an id; then
  `app/src/main/java/com/clocktower/grimoire/ui/GameViewModel.kt:265`
  `val others = saved.importedScripts.filterNot { it.id == script.id }` drops the
  old one without telling anybody. Either salt the fallback id (timestamp /
  content hash) or return a "replacing X" signal the dialog can confirm.

---

## P2

### A2-5 · P2 · Script import — after an **overwriting** import the success banner is stale and lies about the character count
Immediately after the second import in A2-4, card 1 says one thing and the
green confirmation above the list says another:
```
#18 'Imported script · 12 characters'                            @(334,398)
#21 'Imported "Imported script" — 22 characters, now selected.'  @(461,467)
```
The banner is the **previous** import's message, left in place.
`SetupScreen.kt:158` looks for the new script with
`imported.lastOrNull { it.id !in knownScriptIds }`; an overwrite reuses the id,
so `added` is `null`, `importNotice` is never rewritten and `scriptId = added.id`
never runs either — meaning that re-importing a script while a *different* one is
selected produces **no confirmation at all** and no selection change, so the
import looks like it did nothing.
Screenshot `tools/emu/out/A2_import_overwrite/emulator-5560/34-screenshot.png`.
Suspect `SetupScreen.kt:158-166`.

### A2-6 · P2 · "A traveller joins…" — `Cancel` / `Seat them` sit **under the software keyboard**
Open ⋮ → `A traveller joins…`, tap the **Name** field, type a name. The dialog
takes `imePadding()` and shrinks, which moves its action row from
`#75 'Seat them' [671,1869][834,1927] @(752,1898)` (no keyboard) to
`#75 'Seat them' [671,1556][834,1614] @(752,1585)` — and the keyboard's top edge
on the reference device is ≈`y=1506`, so both buttons are completely behind it.
`ui.py tap "Seat them"` reports success and the tap lands on the keyboard; the
traveller is not seated. `audit` cannot catch this (the nodes are inside the safe
area and inside their scroll container) — it is the IME twin of the phantom band
`ui.py` learned about in P-7. Dismissing the keyboard first works, so the flow is
completable, hence P2 rather than P1.
Screenshots `tools/emu/out/A2/a2-traveller-seatthem-under-ime.png` (covered),
`tools/emu/out/A2/traveller-dialog-nokbd.png` (not covered).
Suspect `GameExtras.kt` traveller dialog — pin the action row outside the
scrolling body, or give the dialog `windowInsets = WindowInsets.ime` handling
that keeps the buttons visible.

### A2-7 · P2 · Library — imported scripts sort **last**, and the Library's own Import says nothing
The setup screen puts imports at the top of the list with a `•` badge (A-19's
fix); the Library's tab row keeps the three built-ins first and appends imports
after them, so the script you just imported is off the right-hand edge and the
tab is clipped by the screen:
```
#23 '<View>' [796,304][1047,430] @(921,367)   #24 'A2Monsta' [838,338][1005,396]
```
It is also the only import path with no confirmation of any kind — the setup
screen prints `Imported "X" — N characters, now selected.`, the Library prints
nothing (the selected tab does change, which is the only feedback).
Screenshot `tools/emu/out/A2/library-import-2.png`.
Suspect `LibraryScreen.kt` (tab order + no import notice).

### A2-8 · P2 · Card 4 is "FABLED & HOUSE RULES" but one house rule still lives in card 3
Card 4 gained a real `HOUSE RULES` section (`#41 'HOUSE RULES' @(180,965)`,
`#44 'Secret votes' @(164,1103)`), which is A-14's fix — but
`#64 'House rule: allow duplicates of any character' [190,1564][812,1601] @(501,1582)`
is still inside card 3 (BAG), so the storyteller has to look in two cards for
"house rules" and card 4's summary (`no fabled · by the book`) can read
"by the book" while a house rule is on.
Suspect `SetupScreen.kt:1021` (the checkbox in `BagHeader`) vs `:473-481`
(card 4's section).

### A2-9 · P2 · Archive — a Lil' Monsta game reads back as a game with no Demon
Home → `PAST GAMES` → `Open` gives a read-only sheet listing **seats only**:
```
#10 'A2Monsta · 12 players · setting up'
#11 '1 Uri  Chef  survived'   …   #22 '12 Zoe  Recluse  survived'
```
For the archived Lil' Monsta game (`lilmonsta.noDemonSeat` set) nothing on the
sheet mentions Lil' Monsta, so the record shows twelve players and no Demon at
all. A seatless-token line ("Lil' Monsta — centre token") would close it.
Screenshot: the sheet reached by `tap "^Open$"` on Home.
Suspect the archive sheet in `HomeScreen.kt` / `GameExtras.kt`.

### A2-10 · P2 · Four roster chips all called "Last table (N)"
After four games the TABLE card offers
```
#29 'Last table (10)'  #32 'Last table (15)'  #35 'Last table (7)'  #38 'Last table (12)'
```
— four chips whose only distinguishing mark is the seat count, so two tables of
the same size are indistinguishable and only one of them can be "last". The
first name in each roster (or a relative time) would disambiguate.
Suspect `SetupScreen.kt:812` (`label = { Text("Last table (${roster.size})") }`).

---

## Fix-wave-3 queue item — confirmed, and characterised

> *"During SETUP the 'Before the first night' checklist raises itself over the
> OPEN seat sheet whenever an assignment introduces a new requirement row."*

**Still open on 978692b.** `./scenario.py emulator-5560 A2_checklist_over_seat_sheet`
(32/32) reproduces it: Trouble Brewing → `Start empty` → seat 1 → `Change…` →
assign the **Drunk**; the picker closes back onto the seat sheet, and ~1 s later
the checklist slides up on top of it. Three things the fix wave will want:

1. **The trigger is a NEW requirement row, not any assignment.** On a table with
   the Sentinel chosen, assigning a **Butler** (Outsiders already legal at 0–2)
   raised nothing and left the seat sheet alone; assigning the **Drunk**, which
   adds `The Drunk believes — Player 1 is the Drunk…`, raised it every time.
2. **It costs a tap, not the work.** `back` dismisses the *checklist*, and the
   seat sheet is still underneath and still on the seat you were editing
   (`#12 'Drunk · Outsider' [258,189][515,232] @(386,210)`, `Change…` still
   present). It is an interruption, not a dead end — so the harness fallback the
   queue mentions ("dismiss whatever sheet is on top") is enough to unblock
   `C_setup10` / `C_setup_rest` while the app fix waits.
3. **`audit` on the stacked sheets is clean** (7 clickables, safe area OK,
   overlap OK) — nothing is off-screen, so the covering itself is the whole bug.

Screenshots `tools/emu/out/A2/setup-checklist-over-sheet.png`,
`tools/emu/out/A2_checklist_over_seat_sheet/emulator-5560/25-audit.png`.

---

## `audit` results, screen by screen

Every screen reached was audited. **Clean** (safe area OK, overlap OK) on:
Home (with and without a resume card and PAST GAMES), the new-game guard dialog,
the setup screen in every card state (SCRIPT / TABLE / BAG / FABLED expanded and
collapsed, 7 / 8 / 10 / 12 / 15 seats), the paste-list dialog, the import
dialog, the bag with a 27-character list and the sticky tray, the
"Before the first night" sheet at 2 / 4 / 5 / 6 / 9 rows, every checklist
answer picker, the seat sheet, the character picker, the overflow menu (16
rows), the "A traveller joins…" dialog, hand-out mode entered from the deal
**and** from ⋮ at 7 / 8 / 10 / 12 / 13 / 15 seats, the hand-out card and the
alignment card, the begin-night guard, the archive read-only sheet, the Library
(Characters / Night order / Jinxes on a built-in and on an import), and the
grimoire at 7 / 12 / 15 seats.

Two things `audit` reports that are **not** findings, recorded so nobody chases
them again:

- **the known 8-seat zoom overlap.** Reproduced exactly as the wave-3 queue
  describes it, and it also fires at **7 seats** (6 seated + 1 traveller), which
  the queue entry does not mention:
  ```
  === OVERLAPPING CLICKABLES (1) ===
    5% overlap:
        #47 '<View>' [134,1554][383,1840] @(258,1697)  click,long   # seat 6
        #62 '<View>' [32,1806][158,1932] @(95,1869)    click        # Zoom in
  ```
- **mid-scroll rows in the character picker.** `Change…` → the picker audits
  "bottom 53px under the navigation/gesture inset" at the top of the list and
  "top 59px under the status bar/cutout" at the bottom of it. Both are the
  half-drawn row at whichever end of a `LazyColumn` you are looking at: P-6
  deliberately made the picker's insets `contentPadding` rather than layout
  padding (`266e0aa`), so the viewport runs edge to edge (`#10 <View>
  [53,127][1027,2337] scroll`) and the **ends of the scroll** clear the insets.
  Scrolled to either end, no control is clipped. Same class as the Library's
  known mid-scroll artefact.

---

## What worked (safe ground — no need to re-test)

- **Every one of A-1 … A-20**, per the table above.
- **Scripts and player counts.** Trouble Brewing at 7 (`5/0/1/1`), 8
  (`5/1/1/1`), 12 (`7/2/2/1`) and 15 (`9/2/3/1`); Bad Moon Rising at 10
  (`7/0/2/1`), whose Godfather correctly widens the header to
  `6 or 7 townsfolk · 0 or 1 outsider`; Sects & Violets at 12 (`7/2/2/1`).
  Randomize produced a legal bag first time on all of them, and the deal, the
  hand-out and the grimoire were clean at every count.
- **Every hand-out card kind.** Drunk → `YOU ARE / Librarian`; Lunatic →
  `YOU ARE / Imp` with the Imp's ability and **no** alignment page (correct: no
  explicit alignment override); Marionette → `YOU ARE / Washerwoman`; Ogre →
  a **single** card `YOU ARE / Ogre`, `seat 1 of 8`, no `card 1 of 2`
  (`NEVER_TOLD_ALIGNMENT` doing its job); traveller → two cards, the second only
  after the alignment row is answered. Press-and-hold reveal, `release to hide`,
  the `Seat order` toggle, `✓ / ▶ / ○ / !` progress and tap-a-name re-show all
  behave.
- **The gate.** Drunk, Lunatic, Marionette and un-aligned traveller seats are all
  blocked with a red `!` and an `Answer these now` / `Answer now` shortcut, and
  released the moment the row is answered.
- **Checklist rows.** `The Marionette's seat — Seat Player 6 next to the Demon`
  (confirm-only), `The Lunatic's "Minions"` (optional), `Lunatic bluffs — Jon`,
  `Fortune Teller red herring`, `Demon bluffs` — and, correctly, **no** Demon
  bluffs row on a Lil' Monsta game (no Demon player to bluff to).
- **Import formats.** `_meta` with `name` + `author` (author line renders); a
  bare id-array with no `_meta` (named `Imported script`); both from the setup
  screen and from the Library's `Import`. Imported scripts survive archiving and
  four subsequent games, carry Jinxes (5 on A2Monsta) and a full night order in
  the Library, and offer `Delete script`.
- **Archive / resume.** `Archive & start new` → `Resume instead` → `Cancel`
  guard; PAST GAMES with relative times (`4 minutes ago`); `Open` gives the
  read-only seat list with `Reopen this game` / `Delete…`; `Resume` and the Home
  resume card (`Trouble Brewing · 7 players · setting up · saved just now`).
  Archived four games in a row without losing one.
- **Roster memory.** Four `Last table (N)` chips, most recent first, restoring
  all names.
- **The wave-2 polish.** The bottom tab bar now ends exactly at `y=2316`
  (`#94 [276,2106][530,2316]`) — P-4; the grimoire header's `fabled +` no longer
  overlaps `Search` — P-5; `ui.py` refuses scrolled-out rows — P-7.

## Not reached, and why

- **Import from a share LINK (`?script=…`)** — the dialog advertises it and
  `ScriptLink.decode` is on the path, but a link long enough to be real is past
  what `adb shell input text` will carry reliably; needs a clipboard injection
  the harness does not have yet.
- **Import from file (.json)** — needs the SAF picker, as before.
- **The "allow duplicates" house rule end to end** — the checkbox toggles and
  moves the validator's duplicate message, but I never dealt a duplicate bag to
  see the deal and the grimoire honour it.
- **`Secret votes`** — seen and toggled in card 4 (A-14/G-2 confirmed present);
  the voting behaviour itself is `G_fix_secret_votes`' ground and another
  tester's area.
- **Delete script / Delete archived game** — both buttons found and audited, not
  pressed (they destroy the fixtures the rest of the session needed).
- **Rotation / landscape** — impossible headless, as briefed.
- **The 8-seat zoom overlap fix** — excluded by the brief as a known wave-3 item;
  only the extra datum that it also fires at 7 seats is recorded above.
