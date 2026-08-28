# Playtest findings — the night — agent B

Driven on `emulator-5556`, build `2020fec` (shown on the Home screen), with the
harness in [`tools/emu/`](../../../tools/emu/README.md). Two full games played
from `./emu.sh launch emulator-5556 --fresh`:

| | game A | game B |
|---|---|---|
| bag | Washerwoman · Librarian · Investigator · Chef · Empath / Butler / Poisoner / Imp | Washerwoman · Monk · Undertaker · Ravenkeeper · Fortune Teller / Butler / Poisoner / Imp |
| seats | 1 Ana WW · 2 Ben Poisoner · 3 Cleo Chef · 4 Dan Librarian · 5 Eve Empath · 6 Fay **Imp** · 7 Gus Butler · 8 Hal Investigator | 1 Ana **Imp** · 2 Ben Undertaker *(red herring)* · 3 Cleo Fortune Teller · 4 Dan Ravenkeeper · 5 Eve Poisoner · 6 Fay Monk · 7 Gus Washerwoman · 8 Hal Butler |
| covered | night 1 end to end, poisoned Empath, Imp self-kill | night 1, execution, night 2 (Monk save), night 3 (Ravenkeeper) |

Setup scenario: `tools/emu/scenarios/B_night1_tb.py` (game A's bag).
Helpers written for the runs: `tools/emu/name_seats.py` (rename the eight
seats), `tools/emu/pick.py` (scroll-and-tap a label that is off screen).
All screenshots are under `tools/emu/out/B/`.

Severity per `docs/audit/PLAYTEST-FINDINGS.md`: **P0** crash / stuck / a wrong
rule the storyteller would act on · **P1** a flow you cannot complete · **P2**
cosmetic.

**Counts: 3 P0 · 7 P1 · 10 P2.**

---

## P0

### 1. P0 · Washerwoman / Librarian / Investigator — the info card is flatly wrong

- **Screen / flow** Night 1, the Washerwoman / Librarian / Investigator step,
  `SHOW THEM` block and the primary button.
- **Repro** (game A; reproduced independently in game B with a different deal)
  ```sh
  ./emu.sh launch emulator-5556 --fresh
  ./scenario.py emulator-5556 B_night1_tb          # bag: WW/Lib/Inv/Chef/Empath/Butler/Poisoner/Imp
  # hand out (or "Finish later"), pick bluffs, "Begin night"
  ./ui.py emulator-5556 tap "DONE — NEXT STEP"     # Dusk
  ./ui.py emulator-5556 tap "DONE — NEXT STEP"     # Minion info
  ./ui.py emulator-5556 tap "DONE — NEXT STEP"     # Demon info
  # Poisoner: tap any seat, then the primary
  ./ui.py emulator-5556 find "SHOW"                # ← the Washerwoman step
  ```
- **Expected** *Washerwoman*: "You start knowing that **1 of 2 players** is a
  particular Townsfolk" — the card names **one** Townsfolk character and
  **two** players, one of which really is that character, and **never the
  Washerwoman herself** (<https://wiki.bloodontheclocktower.com/Washerwoman>).
  *Librarian / Investigator*: same shape, 1 of 2 players.
- **Actual** the engine hands the whole candidate set to the card:
  - **Washerwoman** (Ana, seat 1): headline `Townsfolk in play: Ana
    (Washerwoman), Cleo (Chef), Dan (Librarian), Eve (Empath), Hal
    (Investigator)`; the only truthful offer is `SHOW: Ana, Cleo, Dan, Eve, Hal`
    and the primary button is `SHOW "ANA, CLEO, DAN, EVE, HAL" TO ANA`. The card
    that comes up reads **"ONE OF THESE PLAYERS IS THE *Washerwoman*"** over
    five names including Ana herself — the Washerwoman's own token, and no
    information at all.
  - **Librarian** (Dan, seat 4): the card is **"THIS PLAYER IS THE *Butler*" —
    Gus, seat 7**. A flat, unambiguous reveal of the Outsider; the Librarian is
    entitled to a 1-of-2, not to the answer.
  - **Investigator** (Hal, seat 8): identical — `SHOW: Ben`, i.e. "this player
    is the Poisoner".
  - The card body's own instruction line says *"Show one of those character
    tokens, point to that player plus 1 wrong player"* — the engine knows the
    right shape and does not build it.
  - The `LIE ·` alternatives are single bare names (`LIE · SHOW Ben`,
    `LIE · SHOW Fay`, `LIE · SHOW Gus`), also the wrong shape.
- **Screenshots** `tools/emu/out/B/23-washerwoman-top.png`,
  `tools/emu/out/B/24-ww-card.png` (the five-name card),
  `tools/emu/out/B/26-librarian-top.png`, `tools/emu/out/B/27-librarian-card.png`
  (the "THIS PLAYER IS THE Butler — Gus" reveal),
  `tools/emu/out/B/28-investigator.png`, `tools/emu/out/B/52-ww-poisoned.png`
  (game B repro).
- **Suspect** `engine/src/main/kotlin/com/clocktower/engine/InfoCalc.kt:647-664`
  — `startKnowing(ctx, team, label)`, the shared body of `washerwoman` /
  `librarian` / `investigator` (`InfoCalc.kt:132-134`):
  ```kotlin
  val inPlay = ctx.players.filter { ctx.character(it)?.team == team }
  ...
  answer = Answer.Players(inPlay.map { it.id }, inPlay.first().characterId),
  ```
  It never excludes the holder, never picks **one** candidate, and never adds a
  decoy — it emits every candidate plus the *first* candidate's character id.
  With one candidate the result is a full reveal; with five it is nonsense.
  The card prefix then comes from
  `engine/.../ShowCardSpec.kt:66-72` (`pointPrefix`), which is doing the right
  thing with the wrong input.

### 2. P0 · Every night after the first opens on **Dawn**, with "OPEN THE DAY →" as the primary button

- **Screen / flow** Night screen, immediately after "BEGIN NIGHT *n* →".
- **Repro** (reproduced 3×: game A night 2, game B nights 2 and 3)
  ```sh
  # from a finished night 1: phase button → dusk sheet
  ./ui.py emulator-5556 tap "BEGIN NIGHT 2"
  ./ui.py emulator-5556 find "step"       # → "· step 6 / 6"  (game A)
  ./ui.py emulator-5556 find "OPEN THE DAY"
  ```
- **Expected** the sheet opens on step 1 (Dusk), the first unfinished step, as
  it correctly does on night 1.
- **Actual** the header reads `NIGHT 2 · step 6 / 6` and the expanded card is
  **Dawn**, whose primary button is `OPEN THE DAY →` — while every single row
  below is still pending (`·`, not `✓`): Dusk, Poisoner, Monk, Imp, Fortune
  Teller, Undertaker. One tap on the biggest, gold, thumb-level button skips the
  entire night: nobody is poisoned, nobody is protected, **the Demon never
  kills**. The storyteller has to scroll *up* past the whole card to discover
  that steps exist at all.
  Once you do tick a step, "DONE — NEXT STEP" jumps *backwards* to Dusk
  (correct — it is the first pending step), which makes the initial position
  look even more like a bug.
- **Screenshots** `tools/emu/out/B/41-night2.png`, `tools/emu/out/B/43-night2-steps.png`
  (all rows `·`), `tools/emu/out/B/60-night2-open.png` (game B repro),
  `tools/emu/out/B/61-night2-list.png`.
- **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/NightScreen.kt:87`
  and `:101-106`. `activeToken` is `rememberSaveable(state.cycle)`, and the
  `LaunchedEffect` only re-points it when
  `token == null || token in done || plan.steps.none { it.key.token == token }`.
  The token left over from the end of the previous night is `"DAWN"`, the new
  night's plan also contains a `DAWN` step, and it is not in the (freshly
  cleared) `done` set — so all three escape clauses are false and the leftover
  token survives the night change. The fallback
  `?: plan.steps.lastOrNull()?.key?.token` has the same effect if `cycle` does
  not change at the moment the effect runs. Re-pointing whenever
  `state.cycle` changes (or storing `(cycle, token)`) fixes it.

### 3. P0 · Imp self-kill: no star pass, and the app offers "Declare good victory" while a Minion is alive

- **Screen / flow** Night 2, Imp step, target = the Imp themselves.
- **Repro** (game A: 1 Ana WW · 2 Ben Poisoner · 6 Fay Imp)
  ```sh
  # night 2, Imp step
  ./ui.py emulator-5556 find "themselves"      # Fay's own chip
  # tap it, then press-and-hold the primary "FAY DIES"
  ```
- **Expected** *"If you kill yourself this way, a Minion becomes the Imp"*
  (<https://wiki.bloodontheclocktower.com/Imp>). Ben (Poisoner) is alive, so the
  star pass is **mandatory**: the app should prompt for the heir and change
  Ben's character. Good has **not** won.
- **Actual**
  - the consequence preview reads `Fay: Nothing stops it — they die.` and the
    button reads `FAY DIES` — no mention of the star pass *before* the ST
    commits;
  - after confirming, the only thing that appears is a win-check dialog headed
    **"Is the game over?" / "Every Demon is dead — good wins, unless an ability
    says otherwise."** with **"Declare good victory"** as the filled primary
    button and "Keep playing" as the quiet text button. The star pass is a
    two-line red footnote;
  - choosing "Keep playing" produces **nothing at all** — no heir picker, no
    prompt, no reminder token. The grimoire still shows `Seat 2, Ben, Poisoner`
    and `Seat 6, Fay, Imp, dead`. The game is left with no Demon.
- **Screenshots** `tools/emu/out/B/47-imp-self.png` (preview with no star-pass
  note), `tools/emu/out/B/48-starpass.png` (the dialog),
  `tools/emu/out/B/50-grim-after-selfkill.png` (Ben unchanged).
- **Suspect** the engine already does the right thing:
  `engine/.../rules/RulesTroubleBrewing.kt:656-677` (`impStarPass`) emits a
  `Prompt(at = BriefingSlot.NOW, kind = CHOOSE_PLAYER, title = "… a Minion
  becomes the Imp.")`, and `impKilledItself` (`:641-654`) correctly requires a
  living Minion. **Nothing in the UI ever asks for `BriefingSlot.NOW`** —
  `grep -rn "BriefingSlot.NOW" app/src` is empty, while `Briefings.kt:107`
  serves that slot. Wire the NOW briefing into `PhaseFlow.kt` / `GameShell.kt`
  the way DAWN/DUSK are. Separately, `WinCheck.kt:294` should demote
  "good wins" to a caution (or make "Keep playing" the primary) whenever the
  star-pass caution is present.

---

## P1

### 4. P1 · The Discussion timer button sits **on top of** the night card's primary button and steals its taps

- **Screen / flow** Every night step card.
- **Repro**
  ```sh
  # any night step whose primary button is near the bottom of the screen
  ./ui.py emulator-5556 audit
  ./ui.py emulator-5556 tapxy 100 1990     # inside the primary button's bounds
  ```
- **Expected** tapping inside `SHOW "IMP" TO DAN` performs that step.
- **Actual** the discussion-timer chooser opens (1m / 2m / 3m / 5m …). `audit`
  on the Ravenkeeper card:
  ```
  === OVERLAPPING CLICKABLES (2) ===
    37% overlap:
        #64 '<View>' [69,1896][1011,2043] @(540,1969)  click   ← SHOW "IMP" TO DAN
        #71 '<View>' [27,1974][153,2100] @(90,2037)  click     ← Discussion timer
    19% overlap:
        #67 '<View>' [69,2064][529,2127] @(299,2095)  click     ← ‹ back
        #71 '<View>' [27,1974][153,2100] @(90,2037)  click
  ```
  The same FAB also covers the ordinal / skip-reason of whatever collapsed row
  is at the bottom of the list (`91%` overlap with the "Minion info" row on
  night 1; the Butler row's skip reason reads "⏱ — no ability").
- **Screenshots** `tools/emu/out/B/72-fab-over-primary.png` (the clock icon on
  the gold button), `tools/emu/out/B/73-after-fab-tap.png` (the timer opened),
  `tools/emu/out/B/61-night2-list.png` (covering a row).
- **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/GameShell.kt:362-370`.
  The comment says the FAB "docks out of the way of the night sheet's primary
  button" by using `Alignment.BottomStart` on the Night tab — but the night
  card's primary button is full-width (`x 69..1011`), so BottomStart lands
  squarely on it. Either hide the FAB on the Night tab, or reserve space for it
  in the card's bottom padding.

### 5. P1 · `⟳ FLIP` and `HOLD TO CLOSE` are drawn below the safe area on **every** full-screen card — labels cut in half

- **Screen / flow** Any `ShowCard` (info token, character, number, alignment,
  bluffs, point-at-a-seat).
- **Repro**
  ```sh
  # night sheet → "⌄ other outcomes · show a card · how to run this" → "show a card…"
  ./ui.py emulator-5556 tap "THIS IS THE DEMON"
  ./ui.py emulator-5556 audit
  ./ui.py emulator-5556 tap "HOLD TO CLOSE"     # → OFFSCREEN
  ```
- **Expected** the safe-area fix that "just shipped" keeps both controls inside
  `y 136..2316`; the harness's `tap` should reach them.
- **Actual** the row is flush with the physical bottom edge and both labels are
  sliced through the middle:
  ```
  === SAFE-AREA VIOLATIONS (1) ===
    #7 '<View>' [178,2325][441,2400] @(309,2362)  click
        - bottom 84px under the navigation/gesture inset (home indicator)
        - CENTRE UNTAPPABLE: under the bottom navigation / gesture inset (y >= 2316)
  ```
  `HOLD TO CLOSE`'s text node is `[536,2370][848,2400]` — 30 px of a 56 dp
  button. The whole row is 75 px tall where 147 px (56 dp) was laid out, so it
  is being clipped, not just shifted. The gesture strip owns those pixels, so a
  real 1.2 s press there is a home-swipe on most phones. `ui.py tap` refuses it.
  Identical bounds on all seven card types I reached, in both games.
- **Screenshots** `tools/emu/out/B/13-card-this-is-demon.png`,
  `tools/emu/out/B/17-card-bluffs.png`, `tools/emu/out/B/24-ww-card.png`,
  `tools/emu/out/B/16-pointcard-flipped.png`.
- **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/components/ShowCards.kt:186-226`.
  The row asks for `windowInsetsPadding(WindowInsets.systemBars.only(Bottom))`
  plus `bottomActionPadding(safeBottom)` (24 dp + `shellSafeBottomDp()`), which
  should be ≈126 px on this device — the measurement says **0** was applied.
  The `Dialog` is created with `DialogProperties(usePlatformDefaultWidth =
  false)` but **without `decorFitsSystemWindows = false`**, so the dialog's own
  window fits system windows and Compose reports zero insets inside it while the
  content still draws edge to edge. `SafeArea.kt`'s doc-comment describes
  exactly this bug as already fixed; it is not fixed for `FullScreenShow`.
  (Good news, verified: a tap on the card **body** does nothing, and completing
  the hold lands on the privacy cover with the phase caption "First Night ·
  press and hold to open" — `tools/emu/out/B/14-after-hold-close.png`. FLIP does
  rotate the content 180°.)

### 6. P1 · The primary button says `SHOW "x" TO y` and does not show anything, or record anything

- **Screen / flow** Every info step (Washerwoman, Librarian, Investigator, Chef,
  Empath, Fortune Teller, Undertaker, Ravenkeeper).
- **Repro**
  ```sh
  # night 1, Chef step, do NOT touch the "SHOW: 0" chip
  ./ui.py emulator-5556 tap "SHOW “0” TO CLEO"
  # → the sheet advances to the next step; no card was ever displayed
  ```
  Cross-check: on the Ravenkeeper step (game B, night 3) pressing
  `SHOW "IMP" TO DAN` leaves the collapsed row reading `→ Ana` with **no**
  `shown:` entry, whereas tapping the small `SHOW: IMP` chip first produces
  `shown: …`.
- **Expected** per `ux/night-screen.md` §B.7 the primary states the outcome and
  *performs* it. `SHOW "0" TO CLEO` should put the 0 card on screen.
- **Actual** it only ticks the step and advances. The card is *only* reachable
  through the small secondary chip above it. In a real game the Chef gets no
  information and the sheet says the step is done. Collapsed rows show the
  difference: steps where I tapped the chip read `shown: THIS PLAYER IS THE
  Gus`; steps where I only pressed the primary read `done`.
- **Screenshots** `tools/emu/out/B/29-chef.png` (before),
  `tools/emu/out/B/30-after-chef-primary.png` (straight to step 9, no card),
  `tools/emu/out/B/28-investigator.png` (row 7 = `done`, rows 5-6 = `shown: …`).
- **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/night/NightCard.kt`
  (the primary-button `onClick`) — it applies the action and advances but never
  raises `shown`/`ShownCard` the way the chip's `onClick` does.

### 7. P1 · Minion info and Demon info never name the Demon or the Minions

- **Screen / flow** Night 1, steps 2 and 3.
- **Repro** `./ui.py emulator-5556 tap "DONE — NEXT STEP"` from Dusk, then read
  the card.
- **Expected** per `ux/night-screen.md` §D: Minion info offers
  `Message("THIS IS THE DEMON")` **and** a pre-filled `PointCard` naming Fay,
  seat 6; Demon info offers "THESE ARE YOUR MINIONS" + a PointCard naming Ben,
  seat 2, plus the bluffs.
- **Actual**
  - **Minion info** (`Ben · seat 2`): the entire card is one 12-paragraph block
    of generic prose that begins *"Only with 7 or more players — unless a
    Toymaker is in play…"* and goes on about the Poppy Grower, Marionette,
    Magician, Damsel, Lil' Monsta, Storm Catcher, Snitch, Preacher and Hatter —
    **none of which exist in Trouble Brewing**. It says "point to the Demon"
    and never says who the Demon is. The secondary drawer contains only a
    generic `show a card…` and a `HOW TO RUN THIS` block that repeats the same
    prose **verbatim**. No card offer at all.
  - **Demon info** (`Fay · seat 6`): one useful offer, `SHOW: BLUFFS`. The
    Minions are still never named, and there is no "THESE ARE YOUR MINIONS"
    offer.
  - The two classic info tokens do exist — three taps and ~4 screens of
    scrolling away, under `Phrases` in the generic `show a card…` sheet
    (`THIS IS THE DEMON`, `THESE ARE YOUR MINIONS`), together with a very good
    `Point at a seat` grid that would produce exactly the right card. Neither
    step offers them.
- **Screenshots** `tools/emu/out/B/07-minion-info.png`,
  `tools/emu/out/B/08-minion-info-2.png`, `tools/emu/out/B/09-minion-drawer.png`
  (drawer = `show a card…` + the same prose again),
  `tools/emu/out/B/18-demon-info.png`, `tools/emu/out/B/19-demon-info-2.png`.
- **Suspect** `engine/.../CharacterRules.kt:510-600` (the MINION_INFO /
  DEMON_INFO prompt text) has the names available and puts none of them in
  `cards`; the offers list is built in
  `app/.../ui/screens/night/NightCard.kt`. `ShowCardSpec.PointCard` already
  exists (`ShowCardSpec.kt:52-59`) and the renderer works — see
  `tools/emu/out/B/15-pointcard-fay.png`.

### 8. P1 · The game log contains nothing that happened at night

- **Screen / flow** ⋮ menu → **Game log**, after two full nights and a day.
- **Repro**
  ```sh
  ./ui.py emulator-5556 tapxy 1006 220      # ⋮
  ./ui.py emulator-5556 tap "Game log"
  ```
- **Expected** per `ux/night-screen.md` #10 / §E, the log carries the night:
  who the Poisoner chose, who the Monk protected, that the Imp attacked Cleo
  and the Monk saved her, that Cleo was shown YES, that Ben was shown SAINT
  (a lie).
- **Actual** two rows, both from the day:
  ```
  D1  Hal executed
  D1  Ana nominated Hal — 4 votes, reached the block
  ```
  Nothing from night 1 or night 2 — no choices, no protection, no attack, no
  card shown. The per-step summaries in the sheet (`→ Cleo`, `shown: 0`) are
  the only record, and they are scoped to the current night.
- **Screenshot** `tools/emu/out/B/67-gamelog.png`
- **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/GameExtras.kt:59-98`
  — `GameLogDialog` is still the pre-WP3 implementation: it builds its rows
  **only** from `state.deaths` and `state.nominations` and never touches
  `state.ledger`. The engine's own `GameLog.rows(state, lookup)`
  (`engine/.../GameLog.kt:19`) already renders CHOICE / TOLD / WOKE / SPENT /
  MALFUNCTION ledger entries, and `NightPlan.kt:1833, 2042, 2097` writes them.
  The dialog just needs to call it.

### 9. P1 · For an impaired info character the app says "give false info" and then makes the **truth** the primary button

- **Screen / flow** Any poisoned/drunk info step. Seen on the Empath (game A,
  night 1, Eve poisoned) and the Undertaker (game B, night 2, Ben poisoned).
- **Repro**
  ```sh
  # night 1: Poisoner → Eve (the Empath); advance to the Empath step
  ./ui.py emulator-5556 find "POISONED"
  ```
- **Expected** when the card itself prints
  `! Eve is POISONED (Poisoner) — give false info.`, the one full-width gold
  button should not be the true answer.
- **Actual** the card is otherwise excellent — `IMPAIRED — Poisoned by the
  Poisoner (Ben)`, `1 of Eve's alive neighbours is evil`, `Dan (good), Fay
  (evil)`, the red "give false info" line, and `SHOW: 1` / `LIE · SHOW 0` /
  `LIE · SHOW 2` — and then the primary button reads **`SHOW "1" TO EVE`**, the
  truth, in the one place `night-screen.md` §B.7 says the ST is trained to press
  without reading. Same on the Undertaker: `! Ben is POISONED … give false
  info.` above a primary of `SHOW "BUTLER" TO BEN`.
- **Screenshots** `tools/emu/out/B/31-empath-poisoned.png`,
  `tools/emu/out/B/65-undertaker-2.png`
- **Suspect** `app/.../ui/screens/night/NightCard.kt` — the primary is built
  from `info.answer` regardless of `impaired`.

### 10. P1 · One system Back press throws the storyteller out of the running game

- **Screen / flow** Anywhere inside the game (tested on the Grimoire and Night
  tabs, night 3).
- **Repro**
  ```sh
  ./ui.py emulator-5556 tap "Resume game"
  ./ui.py emulator-5556 back
  ./ui.py emulator-5556 find "Clocktower"     # → the Home screen
  ```
- **Expected** Back closes an open sheet, or asks; it should not leave the
  grimoire mid-night. On a gesture-navigation phone an accidental edge swipe is
  one of the easiest gestures to make in the dark.
- **Actual** the app goes straight to Home ("Resume game · Trouble Brewing ·
  8 players · night 3"). Nothing is lost — Resume works — but the ST is two
  taps and a lot of table-facing fumbling from the sheet they were reading.
  It also happened accidentally while dismissing the discussion-timer sheet.
- **Screenshot** `tools/emu/out/B/74-dawn-guard.png`
- **Suspect** `app/src/main/java/com/clocktower/grimoire/MainActivity.kt:113-130`
  — the `Routes.GAME` composable has no `BackHandler`; `grep -rn "BackHandler"
  app/src` returns nothing.

---

## P2

11. **P2 · Marker-step prose is generic to every script and duplicated on the
    card.** Dusk, Minion info, Demon info and Dawn each render a wall of
    `bodyLarge` prose that lists characters that cannot be in the running
    script. Night 1 of an 8-player Trouble Brewing game opens with *"place the
    setup tokens that must already be down (the Drunk's and Marionette's
    identity tokens, the Fortune Teller's Red Herring, the Steward's and
    Knight's and Noble's Know tokens, the No Dashii's two Poisoned tokens, the
    Lycanthrope's Faux Paw, the Toymaker's Final Night: No Attack)"* — nine
    characters, **none in this game**, and it pushes the primary button below
    the fold. Worse, the secondary drawer's `HOW TO RUN THIS` section repeats
    the exact same text a second time on the same card.
    Screenshots `tools/emu/out/B/06-night1-open.png`, `…/07-minion-info.png`,
    `…/09-minion-drawer.png`, `…/55-dim.png`.
    Suspect `engine/.../CharacterRules.kt` marker-step prompts +
    `app/.../night/NightCard.kt` (body and "how to run this" use the same
    string).

12. **P2 · The dim control's glyph is tofu.** The progress strip's brightness
    control reads `⏻ 100 %` in the tree and renders as an empty box + "100 %"
    on screen — U+23FB is not in the bundled font. Screenshot
    `tools/emu/out/B/06-night1-open.png` (top right). Suspect the label in
    `app/.../ui/screens/night/NightControls.kt`.

13. **P2 · Dimming misses the two brightest surfaces.** `⏻ 55 %` / `25 %` dims
    the card area but leaves the top app bar ("Trouble Brewing", the five
    icons) and the bottom navigation bar ("Grimoire / Night / Day / Script") at
    full brightness — the exact complaint in `ux/night-screen.md` #21. Cycling
    is 100 → 55 → 25 → 100 with no off state. Screenshots
    `tools/emu/out/B/55-dim.png`, `…/61-night2-list.png`. Suspect
    `GameShell.kt:372+` (`if (dimming) { … }` is drawn outside the Scaffold but
    apparently still under the bars).

14. **P2 · The collapsed row records the Demon's target but not the outcome.**
    After the Monk saved Cleo, row 4 reads `Imp — Ana … → Cleo` — identical to
    what it would read had Cleo died. `ux/night-screen.md` §B's collapsed list
    wants the *result*. Screenshot `tools/emu/out/B/64-undertaker.png`.
    Suspect `NightScreen.kt` `resultOf(state, step)`.

15. **P2 · The Ravenkeeper card contradicts itself.** The header banner says
    `dead — acts anyway` / *"Dead — this ability fires anyway. Wake them."* and
    then, four lines below the answer, an ember warning says
    *"! Dan is dead — they normally don't act."* Screenshots
    `tools/emu/out/B/70-rk-card.png`, `…/71-rk-answer.png`.

16. **P2 · The "shown" record drops the character.** Row 6 reads
    `shown: THIS PLAYER IS THE Gus` — the character name (`Butler`) that made
    the card meaningful is missing, and the Washerwoman's reads
    `shown: ONE OF THESE PLAYERS IS THE Ana, Cleo, Dan, Eve, Hal`. Screenshot
    `tools/emu/out/B/28-investigator.png`.

17. **P2 · The Undertaker's card prefix is truncated.** It reads
    `THIS CHARACTER` over the token; the physical info token is "THIS CHARACTER
    DIED TODAY". Screenshot: card reached from `…/65-undertaker-2.png`.

18. **P2 · Lie offers outnumber and out-shout the truth on healthy characters.**
    An un-impaired Ravenkeeper is offered `SHOW: IMP` plus **five** ember
    `LIE · SHOW …` chips (Chef, Empath, Investigator, Librarian, Mayor) filling
    three rows above the primary button; the healthy Washerwoman gets three.
    Nothing on the card says why lying is being suggested. Screenshot
    `tools/emu/out/B/72-fab-over-primary.png`.

19. **P2 · The dawn briefing's "your notes" omits standing impairments and what
    was shown.** Night 2's briefing lists `• Cleo is safe from the Demon.` and
    the swept tokens, but not "Ben is poisoned until dusk" (still live at dawn),
    not "the Imp attacked Cleo; the Monk saved her", and not "Ben was shown
    SAINT (lie)" — the three things `ux/night-screen.md` §F names explicitly.
    Screenshot `tools/emu/out/B/66-dawn-n2.png`.

20. **P2 · `show a card…` opens with its search field under the home
    indicator.** On first open the sheet's `Find a character…` field is
    `[53,2287][1027,2400]`, centre `(540,2343)` — `audit` flags
    `CENTRE UNTAPPABLE`. It is reachable after a scroll, so it is only a
    first-impression bug. Screenshot `tools/emu/out/B/10-showcard-sheet.png`.

---

## What WORKED

Most of the WP8 redesign is in and it is good. Verified working:

- **The card layout itself** — token, character name, `Ana · seat 1`, ember
  impairment banner, prompt, one ask, consequence preview, one full-width
  primary at thumb level, `‹ back` / `skip ›`, and a collapsed secondary drawer.
- **Outcome-stating primary buttons**: `EVE — POISONED`, `CLEO — MASTER`,
  `CLEO — SAFE`, `FAY DIES`, `CLEO SURVIVES — NOBODY DIES`, `OPEN THE DAY →`.
  Disabled until a choice is made.
- **The player picker** — 2-column grid in seat order, seat numbers, character
  tokens, `◆ themselves` when self is legal, `⌄ 1 they cannot choose` for the
  Butler/Monk/Ravenkeeper exclusions, `last night` captions, `dead` captions,
  and status pips (`!` poisoned, `·` red herring, `+` Monk-safe).
- **Computed info, checked against the seating**:
  Chef `0 pairs of neighbouring evil players` (evil in seats 2 and 6 — correct);
  Empath `1 of Eve's alive neighbours is evil` with the breakdown
  `Dan (good), Fay (evil)`; after the Imp died in seat 6 the Empath correctly
  read 0 by skipping the dead neighbour;
  Fortune Teller `YES / Ben is the red herring` and `YES / Ana is the Demon`;
  Undertaker `Show: Butler / Hal was executed today`;
  Ravenkeeper `Ana is the Imp`.
- **Skip states with reasons and `[Run anyway]`** — `⊘ 5 Ravenkeeper — Dan …
  skipped · they are alive — this ability only fires on the night they die`;
  `⊘ 7 Undertaker — Ben … skipped · nobody was executed today`;
  `⊘ 8 Butler — Hal … skipped` (dead). The Ravenkeeper row flipped to active
  (`· … 1 pick`) the moment Dan died at night, and back to skipped otherwise.
- **The Monk** — `Cleo: Cleo is safe from the Demon. Nobody dies — the Monk
  protected them.` with a press-and-hold confirm on the destructive button.
- **Poisoner token placement** — the pip lands on the grimoire, the header says
  `2 tokens expire at dusk`, and the dusk sheet lists `Removed: Poisoned
  (Poisoner) from Eve` / `Removed: Safe (Monk) from Cleo` / `Removed: Died Today
  (Undertaker) from Hal`.
- **False info for character-shaped answers** — the poisoned Undertaker is
  offered `LIE · SHOW DRUNK / RECLUSE / SAINT`, i.e. plausible not-in-play
  Outsiders. `ux/night-screen.md` #16's dead end is fixed.
- **The dusk sheet** — `TRUE NOW` previews tonight's step list, `TAKEN OFF THE
  GRIMOIRE` lists the sweep, and `Execute Hal` → `Nothing stops it — they die.`
  → `HAL IS EXECUTED AND DIES` / `Executed — but they don't die`.
- **Dawn briefing** — `SAY OUT LOUD, IN THIS ORDER · Announce: nobody died.`,
  plus a `DO NOT SAY — your notes` section.
- **Show cards** — body taps do **not** dismiss (`ux/night-screen.md` #2 fixed);
  `⟳ FLIP` really rotates 180°; the 1.2 s hold lands on the privacy cover with
  the phase caption `First Night · press and hold to open`. Card types reached
  and rendered correctly: message/phrase, character token, number (220 sp),
  bluffs (3 tokens, wrapped), point-at-a-seat (name + `seat 6`),
  multi-name point card.
- **Night-order numbering on the grimoire** — the Imp correctly carries **no**
  night-1 order pip.
- The `whole sheet` / `hide sheet` toggle and the 11-segment progress strip.

## What I could NOT reach

- **A star-pass heir picker** — see P0 #3; the prompt is generated and never
  surfaced, so there was nothing to test.
- **A dawn guard modal.** I never managed to press the phase button with steps
  outstanding, because the sheet always opens on Dawn (P0 #2) and its own
  button handles the transition. Untested.
- **The "kill sheet" as a separate surface.** The Demon step resolves inline
  (picker → preview → press-and-hold primary); I never saw a `KillSheet` with a
  cause/killer selector from the night side. `KillSheet.kt` exists and the
  execution path uses an `ExecutionSheet`, so the night may simply not use it.
  "Saved by the Monk" is shown as a *preview* and stored as a standing effect
  (`Cleo is safe from the Demon` in the dawn notes), but it is not a ledger row
  — see P1 #8.
- **`[Run anyway]`** on a skipped row — visible and clickable, never pressed.
- **The Chambermaid "does not count" chip** beyond seeing it on Minion/Demon
  info.
- **Landscape, the PWA, and the wake lock.**
- The day screen's vote UI beyond what was needed to produce one execution
  (it is agent D's area) — note that my first four taps on the voter chips
  created four *new nominations* instead of votes, because the page had
  scrolled under them; worth a look from whoever owns the day.
