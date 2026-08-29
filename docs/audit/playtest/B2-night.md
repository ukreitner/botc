# Re-test findings — the night — agent B2

Second playtest fleet, driven on `emulator-5554` against the tip of
`claude/clocktower-grimoire-android-0bz090` at **978692b** (fix waves 1–2 +
wave-2 polish merged). Harness: [`tools/emu/`](../../../tools/emu/README.md).

This file **re-tests every finding in
[`B-night.md`](B-night.md) (B-1 … B-20)** and then plays past them. It never
edits the first tester's file.

Severity per [`docs/audit/PLAYTEST-FINDINGS.md`](../PLAYTEST-FINDINGS.md):
**P0** crash / stuck / a wrong rule the storyteller would act on · **P1** a flow
you cannot complete · **P2** cosmetic.

## Games played

| | game 1 | game 2 | game 3 | game 4 |
|---|---|---|---|---|
| script | Trouble Brewing | Trouble Brewing | Bad Moon Rising | Bad Moon Rising |
| seats | 8 | **15** | 12 | 8 |
| bag | `B_fix_night1`'s (WW/Lib/Inv/Chef/Empath/Butler/Poisoner/Imp) | Washerwoman · Librarian · Investigator · Chef · Empath · Fortune Teller · Undertaker · Monk · Ravenkeeper / **Drunk** · **Recluse** / Poisoner · **Spy** · **Scarlet Woman** / Imp | playtest D's fixture (DA · Sailor · Chambermaid · Gossip · Grandmother · Professor · Tea Lady · Exorcist · Fool · Lunatic · Pukka · Godfather) | **Innkeeper** · **Courtier** · Exorcist · Sailor · Professor / Tinker / Godfather / **Zombuul** |
| covered | night 1 end to end, poisoned Empath, night 2, **Imp self-kill + star pass** | night 1 end to end (all nine waking Townsfolk, 12 steps), execution, night 2 (poisoned Monk, Imp kill, Ravenkeeper, Undertaker) | night 1 (Grandmother/Grandchild, Lunatic hand-over, Godfather, DA, Pukka, Chambermaid), night 2 (**Pukka deferred kill**, **Grandmother death**) | night 1 (Minion/Demon info, Sailor, **Courtier**, Godfather), night 2 (**Innkeeper**, **Exorcist**, Zombuul) |

New scenarios written for this run (replayable evidence):

- `tools/emu/scenarios/B2_tb15_setup.py` — 203 steps, **all pass**: builds the
  15-seat Trouble Brewing table (8 → 15 seats from the TABLE card) and ticks the
  fifteen-character bag through the BAG search field.
- `tools/emu/scenarios/B2_bmr8_setup.py` — 94 steps, **all pass**: the 8-seat
  Bad Moon Rising table carrying Innkeeper / Courtier / Zombuul, which playtest
  D's 12-player fixture does not.

Existing scenarios replayed on the tip build:

| scenario | result |
|---|---|
| `B_fix_night1` | **119/119 pass**, `audit` clean |
| `B_fix_showcard` | **22/22 pass**, `audit` clean on every card |
| `B_fix_starpass` | **27/27 pass** |
| `D_bmr_setup` + `D_bmr_assign.sh` | pass (12 seats assigned) |
| `E_fix_night_pointer` | 62/87, then completed by hand — see "Harness notes" |
| `E_fix_pukka_death` | 9/63, then completed by hand — see "Harness notes" |

Screenshots referenced below are under `tools/emu/out/B2/`; the scenario runs
keep their own numbered trails under `tools/emu/out/<scenario>/emulator-5554/`.

**Counts: 4 P0 · 2 P1 · 9 P2.**

---

## Verdicts on B-1 … B-20

| # | first tester's finding | verdict |
|---|---|---|
| B-1 | P0 · Washerwoman/Librarian/Investigator card is flatly wrong | **VERIFIED FIXED** |
| B-2 | P0 · every night after the first opens on Dawn | **VERIFIED FIXED** |
| B-3 | P0 · Imp self-kill: no star pass, "Declare good victory" offered | **VERIFIED FIXED** — but see **B2-1** (a *new* P0 on the same path) |
| B-4 | P1 · discussion-timer FAB sits on the primary button | **VERIFIED FIXED** |
| B-5 | P1 · `⟳ FLIP` / `HOLD TO CLOSE` below the safe area | **VERIFIED FIXED** |
| B-6 | P1 · `SHOW "x" TO y` shows nothing and records nothing | **VERIFIED FIXED** |
| B-7 | P1 · Minion info / Demon info never name anybody | **VERIFIED FIXED** |
| B-8 | P1 · the game log contains nothing from the night | **VERIFIED FIXED** — residual wording, **B2-8/B2-9** |
| B-9 | P1 · impaired info step makes the truth the primary | **VERIFIED FIXED** for info rows; **REGRESSED DIFFERENTLY** for protection rows — **B2-5** |
| B-10 | P1 · one Back press leaves the running game | **VERIFIED FIXED** |
| B-11 | P2 · marker-step prose generic + duplicated on the card | **VERIFIED FIXED** |
| B-12 | P2 · the dim control's glyph is tofu | **VERIFIED FIXED** |
| B-13 | P2 · dimming misses the top and bottom bars | **VERIFIED FIXED** |
| B-14 | P2 · collapsed row records the target, not the outcome | **VERIFIED FIXED** for kills; residual for the Fortune Teller — **B2-13** |
| B-15 | P2 · the Ravenkeeper card contradicts itself | **VERIFIED FIXED** |
| B-16 | P2 · the "shown" record drops the character | **VERIFIED FIXED** |
| B-17 | P2 · the Undertaker's card prefix is truncated | **VERIFIED FIXED** for the Undertaker; the same defect survives for `revealCharacter` — **B2-10** |
| B-18 | P2 · lie offers outnumber and out-shout the truth | **VERIFIED FIXED** |
| B-19 | P2 · the dawn briefing omits impairments and what was shown | **VERIFIED FIXED** |
| B-20 | P2 · `show a card…` opens with its search field under the home indicator | **VERIFIED FIXED** |

### Proof, one line each

- **B-1** `B_fix_night1` steps 117–118 assert the SHAPE and pass:
  `1 of 2 players is the Empath — Player 1 really is, Player 2…` over a primary
  of `SHOW “EMPATH — PLAYER 1, PLAYER 2” TO PLAYER 6`. Reproduced three more
  times in the 15-seat game with all three characters — Washerwoman
  `RAVENKEEPER — Player 1, Player 2`, Librarian `RECLUSE — Player 10, Player 11`,
  Investigator `SCARLET WOMAN — Player 2, Player 3` — never the holder, never
  the whole candidate set. (`out/B2/17-ww-poisoned.png`.)
- **B-2** night 2 opened on `step 1 / 6` (game 1), `step 1 / 11` (game 2),
  `step 1 / 12` and `step 1 / 10` (games 3 and 4), the Dusk card in every case.
  `out/B2/04-night2-open.png`.
- **B-3** `B_fix_starpass` 27/27: the card itself asks
  `Player 2 killed themselves — a Minion becomes the Imp.` with the legal heirs
  as chips, `PLAYER 8 BECOMES THE IMP` press-and-hold, no win dialog, and the
  grimoire afterwards reads `Seat 8, Player 8, Imp, alive, evil`.
- **B-4** the timer is now a `TIMER` chip in the progress strip
  (`[469,323][619,449]`), and `audit` on every night card in four games reports
  `overlap: OK`.
- **B-5** `audit` on the message, character-token, number, point, multi-name
  point and bluffs cards all report
  `safe area: OK — every clickable node is fully inside x 0..1080, y 136..2316`;
  `FLIP` sits at `[178,2073][441,2220]` and `ui.py hold "HOLD TO CLOSE"` lands.
  `out/B2/01-librarian-card.png`, `out/B2/13-pointcard-demon.png`,
  `out/B2/15-minions-card.png`, `out/B2/16-bluffs-card.png`.
- **B-6** `B_fix_showcard` 22/22 — the primary puts the card on screen and the
  collapsed row afterwards reads
  `shown: ONE OF THESE PLAYERS IS THE Empath — Player 1, Playe…`.
- **B-7** Minion info now reads `Player 2 · seat 2 · Player 6 · seat 6 ·
  Player 11 · seat 11` with a pre-filled
  `SHOW: THIS IS THE DEMON — Player 4`; Demon info offers
  `SHOW: THESE ARE YOUR MINIONS — Player 2, Player 6, Player 11` **and**
  `SHOW: BLUFFS`. `out/B2/12-minion-info.png`, `out/B2/14-demon-info.png`.
- **B-8** ⋮ → Game log now opens on `NIGHT 1` with the ledger under it.
- **B-9** a poisoned Empath's primary is `DISABLED` and reads
  `PICK WHAT TO SHOW — PLAYER 1 IS IMPAIRED`; picking `LIE · SHOW 1` ticks the
  chip and arms the primary as `SHOW “1” TO PLAYER 1`.
  `out/B2/02-empath-poisoned.png`.
- **B-10** `back` from the game log closes the dialog; a second `back` raises
  **"Leave the game?" / Stay in the game / Back to home**.
- **B-11** Dusk is now `Everyone closes their eyes. Wait for quiet.` and Minion
  info is `Wake all Minions (Player 2, Player 6, Player 11). They see each
  other, then point out the Demon (Player 4).` — no Marionette, no Toymaker, no
  duplication.
- **B-12/B-13** the control reads `DIM 100 %` (no tofu) and at 25 % the overlay
  covers the top app bar and the bottom navigation bar as well as the card.
  `out/B2/11-dim25.png`.
- **B-14** `✓ 3 Imp — Player 8 … → Player 3 died`, `✓ 5 Imp — Player 4 …
  → Player 1 died`.
- **B-15** the Ravenkeeper card carries only `dead — acts anyway` /
  *"Dead — this ability fires anyway. Wake them."*; the contradicting ember line
  is gone. `out/B2/30-ravenkeeper.png`.
- **B-17** the Undertaker's card is headed **`THIS CHARACTER DIED TODAY`**
  (`InfoCalc.kt:521`). `out/B2/27-undertaker-card.png`.
- **B-18** a healthy card shows one truthful chip; the lies live behind
  `⌄ other outcomes` under a `FALSE INFO YOU COULD SHOW INSTEAD` heading.
- **B-19** the dawn briefing's notes now carry
  `• Player 6 (Washerwoman) is shown ONE OF THESE PLAYERS IS T…`,
  `• Player 1's Empath ability malfunctioned…` and a `TRUE NOW` block with
  `• Player 1: Poisoned by the Poisoner (Player 8) — this does…`.
  `out/B2/03-dawn-n1.png`.
- **B-20** on first open the sheet's `Find a character…` field is
  `[53,1087][1027,1234]`, centre `(540,1160)` — well inside the safe area.
  `out/B2/09-showcard-sheet.png`.

---

## P0

### B2-1. P0 · After a star pass the **new** Imp is given a kill on the same night — a second death, against the app's own card text

- **Screen / flow** Trouble Brewing, night 2, the Imp step, target = the Imp
  themselves; then the row the plan inserts for the heir.
- **Repro** (game 1; `B_fix_starpass` leaves the app exactly here)
  ```sh
  ./emu.sh launch emulator-5554 --fresh
  ./scenario.py emulator-5554 B_fix_night1
  ./scenario.py emulator-5554 B_fix_showcard
  # …finish night 1, open day 1, close it from the Day tab's DUSK card, BEGIN NIGHT 2
  ./scenario.py emulator-5554 B_fix_starpass     # Imp kills itself, Player 8 becomes the Imp
  ./ui.py emulator-5554 tap  "^Night$"
  ./ui.py emulator-5554 find "Imp — Player 8"    # a SECOND Imp row, "1 pick"
  ```
- **Expected** the Imp's night action for this night has been spent — on itself.
  *"If you kill yourself this way, a Minion becomes the Imp"*
  (<https://wiki.bloodontheclocktower.com/Imp>) hands over the token; the new
  Demon does not then kill as well. The app knows this: the hand-over card says
  in so many words **"The new Demon does not act tonight."**
  (`Identity.kt:450`).
- **Actual** the plan inserts a fresh, fully live `Imp — Player 8 · 1 pick` row
  for the heir, badged `new character` / `out of order — this became true after
  their slot`. Opening it gives the normal Demon picker; picking Player 3
  previews **`Player 3: Nothing stops it — they die.`** and the primary reads
  **`PLAYER 3 DIES`**. Holding it kills Player 3 for real: the header goes
  `Night 2 · 6/8 alive`, the row collapses to `✓ 3 Imp — Player 8 · → Player 3
  died`, and two players are dead on a night the Demon should have killed once.
  The storyteller is not warned; the only contrary text is on the previous card,
  which they have already ticked past.
- **Screenshots** `tools/emu/out/B2/07-newimp-card.png` (the heir's live kill
  card with both badges), `tools/emu/out/B2/08-newimp-kill-preview.png`
  (`PLAYER 3 DIES`), `tools/emu/out/B2/05-newimp-acts.png` (the row in the list
  next to the hand-over card that says the new Demon does not act).
- **Node bounds** the row `#40 '<View>' [32,1711][1048,1869] @(540,1790)`;
  the primary `#52 'PLAYER 3 DIES' [380,1693][701,1751] @(540,1722)`.
- **Suspect** `engine/src/main/kotlin/com/clocktower/engine/NightPlan.kt:1533-1565`
  — `createdStep`. `PROMOTIONS` (`:1568-1574`) already contains
  `ChangeReason.STAR_PASS`, but it is only used to suppress the *first-night*
  information:
  ```kotlin
  val promotion = record.reason in PROMOTIONS
  val firstNightRules = !promotion && character.firstNightReminder.isNotBlank()
  ...
  val acts = if (firstNightRules) { … } else {
      character.otherNightReminder.isNotBlank() || role.slotId in otherNightOrder
  }
  if (!acts) return null
  ```
  For a star pass on a later night `firstNightRules` is false, so `acts` falls
  through to the Imp's normal other-night entry and a kill row is created. A
  promotion whose `ChangeReason` is `STAR_PASS` / `STAR_PASS_TOKEN_SWAP` should
  create **no** acting step on the night of the pass (the hand-over card, which
  already exists and already carries `SHOW: YOU ARE Imp`, is the whole row).
  The note is written in `engine/.../Identity.kt:450`; the planner does not read
  it.

### B2-2. P0 · Storyteller-only answers are offered as cards **to show the player** — the Courtier's primary reveals the entire grimoire, the Exorcist's tells them who the Demon is

- **Screen / flow** Bad Moon Rising, the Courtier's row (night 1 or any night)
  and the Exorcist's row (night 2+).
- **Repro**
  ```sh
  ./emu.sh launch emulator-5554 --fresh
  ./scenario.py emulator-5554 B2_bmr8_setup
  ./ui.py emulator-5554 tap "Deal anyway"      # Godfather's [±1 Outsider] bag warning
  ./ui.py emulator-5554 tap "Finish later"
  ./ui.py emulator-5554 tap "Demon bluffs" ; ./ui.py emulator-5554 tap "Suggest 3"
  ./ui.py emulator-5554 back ; ./ui.py emulator-5554 tap "^Close$"
  ./ui.py emulator-5554 tap "Begin night" ; ./ui.py emulator-5554 tap "Start the night anyway"
  ./ui.py emulator-5554 tap "whole sheet"
  ./ui.py emulator-5554 tap "Courtier — Player 6"
  ./ui.py emulator-5554 find "SHOW “"
  ```
- **Expected**
  - *Courtier*: "Once per game, at night, choose a character: they are drunk for
    3 nights & 3 days" (<https://wiki.bloodontheclocktower.com/Courtier>). The
    Courtier **names** a character and is told **nothing**. There is no card to
    show.
  - *Exorcist*: "the Demon, if chosen, learns who you are, then doesn't act
    tonight" (<https://wiki.bloodontheclocktower.com/Exorcist>). The **Demon**
    is woken; the Exorcist learns nothing. The card itself says so:
    *"The Exorcist is not told either way; this is for you."*
- **Actual** both rows put the storyteller's own crib sheet behind a
  show-the-player offer **and** an armed, full-width gold primary:
  - **Courtier** (Player 6, seat 6) — the card prints
    `Whoever they name: these seats hold the characters in play` /
    `Player 1 (Tinker), Player 2 (Innkeeper), Player 3 (Professor), Player 4
    (Sailor), Player 5 (Zombuul), Player 6 (Courtier), Player 7 (Exorcist),
    Player 8 (Godfather)`, offers
    `SHOW: TINKER, INNKEEPER, PROFESSOR, SAILOR, ZOMBUUL, COURTIER, EXORCIST,
    GODFATHER` and its primary reads verbatim
    **`SHOW “TINKER, INNKEEPER, PROFESSOR, SAILOR, ZOMBUUL, COURTIER, EXORCIST,
    GODFATHER” TO PLAYER 6`**. Pressing it (or the `THEY CHOSE NOBODY` primary —
    the same card is attached to the head-shake branch) puts a full-screen
    multi-token card headed **`THESE CHARACTERS`** in front of the Courtier with
    every character in the game on it, Demon and Minion included. One tap ends
    the game.
  - **Exorcist** (Player 7, seat 7) — choosing the Zombuul gives
    `YES — Player 5 is the Demon; they do not act tonight`, the line
    `The Exorcist is not told either way; this is for you.` and then a chip
    `SHOW: YES` over a primary of **`SHOW “YES” TO PLAYER 7`** — Player 7 being
    the Exorcist. The card contradicts its own button.
- **Screenshots** `tools/emu/out/B2/40-courtier.png` (the ask),
  `tools/emu/out/B2/41-courtier-reveals-grimoire.png` (the primary),
  `tools/emu/out/B2/42-courtier-card-shown.png` (the `THESE CHARACTERS` card on
  screen), `tools/emu/out/B2/44-exorcist.png`.
- **Node bounds** Courtier primary
  `#30 'SHOW “TINKER, INNKEEPER, …” TO PLAYER 6' [69,1227][1011,1374] @(540,1300)`;
  Exorcist primary `#39 'SHOW “YES” TO PLAYER 7' [69,1301][1011,1448] @(540,1374)`.
- **Suspect** the answers are correct and correctly documented as
  storyteller-facing — `engine/.../InfoCalc.kt:953-967` (`exorcist`, whose
  `detail` is literally *"The Exorcist is not told either way; this is for
  you."*) and `engine/.../InfoCalc.kt:969-983` (`courtier`, whose KDoc is
  *"Is that character in play, and where? — for the Courtier's pick"*). What is
  missing is any way to say "this answer is not for the holder":
  `engine/.../NightPlan.kt:1145-1156` (`cardsFor`) turns **every** `InfoResult`
  into `CardOffer("SHOW: …", …, truthful = true)`, and
  `app/.../ui/screens/night/NightCard.kt:130-161` + `NightRows.kt:332`
  (`primaryLabel`) then make that offer the primary. Either add an
  `InfoResult.storytellerOnly` flag that suppresses the card offer and the
  `SHOW … TO …` primary, or drop `infoId` from the two rows
  (`rules/RulesBadMoonRising.kt:301` exorcist, `:536` courtier) and render the
  answer as plain card body.

### B2-3. P0 · The Grandmother's "your grandchild was killed" row grows a *second* "reveal a player's character" ask she has no ability for — and answering it replaces her death on the button

- **Screen / flow** Bad Moon Rising, the night after the Demon kills the
  Grandchild, the `Grandmother — Erin` row.
- **Repro** (game 3 — playtest D's fixture; Erin seat 5 is the Grandmother,
  Finn seat 9 the marked Grandchild, Kai seat 11 the Pukka)
  ```sh
  ./emu.sh launch emulator-5554 --fresh
  ./scenario.py emulator-5554 D_bmr_setup
  zsh scenarios/D_bmr_assign.sh emulator-5554
  ./scenario.py emulator-5554 E_fix_night_pointer     # night 1; the Pukka poisons Finn
  # …finish night 1, open day 1, close it, BEGIN NIGHT 2, resolve the Pukka
  # (Finn dies now, poisoned by the Pukka last night)
  ./ui.py emulator-5554 tap  "Grandmother — Erin"
  ./ui.py emulator-5554 find "WHO DID THEY CHOOSE"
  ```
- **Expected** Bad Moon Rising's Grandmother is *"You start knowing a good
  player & their character. If the Demon kills them, you die too."*
  (<https://wiki.bloodontheclocktower.com/Grandmother>). On later nights she is
  never woken and never chooses anything — the row is the storyteller's own
  bookkeeping, and the registry says exactly that: the `otherNight` rule
  (`rules/RulesBadMoonRising.kt:154-195`) has `wakeCounts = WakeCount.NONE`,
  **no `action`**, **no `infoId`**, and only a `pending` list that attacks the
  holder.
- **Actual** the card opens correctly — prompt *"The Demon killed the
  grandchild. The Grandmother dies too — announce both deaths at dawn, in seat
  order."* and a primary of `ERIN DIES` — and then, below the prompt, asks
  **`WHO DID THEY CHOOSE?`** over a full twelve-seat picker. Tapping any seat
  computes that seat's character and rewrites the card: picking Ben (seat 2)
  gives `Ben is the Sailor`, a chip `SHOW: SAILOR` and a primary of
  **`SHOW “SAILOR” TO ERIN`**. So a storyteller who touches the picker the card
  invites them to touch (a) hands a live Grandmother a free character reveal she
  has no ability for and (b) loses `ERIN DIES` from the button they are trained
  to read.
- **Screenshots** `tools/emu/out/B2/35-grandmother-n2.png` (the picker under the
  death prompt), `tools/emu/out/B2/37-grandmother-n2-reveal.png`
  (`SHOW “SAILOR” TO ERIN`).
- **Node bounds** the ask `#28 'WHO DID THEY CHOOSE?' [69,748][553,804] @(311,776)`;
  the rewritten primary `#41 'SHOW “SAILOR” TO ERIN. Press and hold to confirm.'
  [69,1227][1011,1374] @(540,1300)`.
- **Suspect** `engine/.../NightPlan.kt:1108-1117`:
  ```kotlin
  private fun infoAction(abilityId: String, nightRule: NightRule?): NightAction? {
      val infoId = nightRule?.infoId ?: abilityId          // ← falls back to the CHARACTER
      if (!InfoCalc.supports(infoId)) return null
      …
  }
  ```
  called from `:765` as `nightRule?.action?.invoke(nightCtx) ?: infoAction(...)`.
  Any night rule with no `action` **and** no `infoId` silently inherits its
  character's whole info ask whenever the id is in `InfoCalc.supportedIds` —
  here `grandmother` → `InfoCalc.revealCharacter` (`InfoCalc.kt:636-645`), which
  needs one target. The fallback should key off `nightRule.infoId` only (a rule
  that wants its character's info says so), or `createdStep`/`roleStep` should
  not synthesise an ask for a `WakeCount.NONE` row. Worth a sweep: the same
  fallback fires for every rule with no action whose id is in
  `InfoCalc.supportedIds`.

### B2-4. P0 · A death recorded through the seat sheet's kill funnel with cause **Execution** never reaches the Undertaker — the row is auto-skipped "nobody was executed today" while its own card says the opposite

- **Screen / flow** Trouble Brewing. Grimoire → seat → **Kill…** → cause
  **Execution** → *Record the death*; then night 2's Undertaker row.
- **Repro** (game 2)
  ```sh
  ./emu.sh launch emulator-5554 --fresh
  ./scenario.py emulator-5554 B2_tb15_setup
  # …answer the three setup rows, Finish later, play night 1 to the dawn, OPEN DAY 1
  ./ui.py emulator-5554 tap  "^Grimoire$"
  ./ui.py emulator-5554 tap  "^Seat 14,"
  ./ui.py emulator-5554 tap  "Kill…"
  ./ui.py emulator-5554 tap  "^Execution$"
  ./ui.py emulator-5554 tap  "Record the death"
  ./ui.py emulator-5554 back
  ./ui.py emulator-5554 tap  "^Day$"          # …swipe to the DUSK card
  ./ui.py emulator-5554 tap  "^DUSK$"
  ./ui.py emulator-5554 tap  "Everyone, eyes closed"
  ./ui.py emulator-5554 tap  "NO EXECUTION — BEGIN NIGHT 2"
  ./ui.py emulator-5554 tap  "whole sheet"
  ./ui.py emulator-5554 find "Undertaker"
  ```
- **Expected** one storyteller-visible truth about who was executed today. The
  seat sheet already believes it: after recording, seat 14 reads
  **`executed D1 · ghost vote available`**. The Undertaker
  (*"Each night\*, you learn which character died by execution today"*) must
  therefore wake.
- **Actual** the app holds two disagreeing answers at once.
  - The **dusk card** says `No one is about to die. There is no execution
    today.` and its primary is `NO EXECUTION — BEGIN NIGHT 2 →`, which stamps a
    NO_EXECUTION record over a day that had one.
  - The night-2 list shows `⊘ 9 Undertaker — Player 7 · skipped ·
    nobody was executed today · [Run anyway]`. A storyteller reading the list —
    which is the whole point of the list — never wakes the Undertaker.
  - Opening the row anyway shows a card that **contradicts its own banner**:
    `NOTHING TO DO — nobody was executed today` at the top, and four lines below
    it `Show: Drunk` / **`Player 14 was executed today`** / `SHOW: DRUNK` /
    `RUN IT ANYWAY`. Running it produces the correct
    `THIS CHARACTER DIED TODAY` + `Drunk` card.
- **Screenshots** `tools/emu/out/B2/23-dusk-no-execution.png`,
  `tools/emu/out/B2/25-undertaker-skipped.png`,
  `tools/emu/out/B2/26-undertaker-contradiction.png` (banner and answer on one
  screen), `tools/emu/out/B2/27-undertaker-card.png`.
- **Node bounds** the skipped row
  `#50 '<View>' [32,1533][1048,1813] @(540,1673)` with
  `#56 'nobody was executed today' [53,1707][763,1755]`; the banner
  `#55 'NOTHING TO DO — nobody was executed today' [101,1513][860,1561]` above
  `#58 'Player 14 was executed today' [69,1849][546,1899]`.
- **Suspect** two sources for one fact.
  - the **gate** reads the day's `ExecutionRecord`:
    `engine/.../NightPlan.kt:2501-2508`
    ```kotlin
    fun executedToday(): WakePredicate = WakePredicate { ctx ->
        val record = ctx.executedToday
        if (record != null && record.outcome == ExecutionOutcome.DIED) StepGate.Fire
        else StepGate.Skip("nobody was executed today")
    }
    ```
    (wired at `rules/RulesTroubleBrewing.kt:148`);
  - the **answer** reads the death event:
    `engine/.../InfoCalc.kt:504-511`
    ```kotlin
    val executed = ctx.state.deaths.lastOrNull { it.cause == DeathCause.EXECUTION && it.day == day }
    ```
  `KillSheet` writes the death with `DeathCause.EXECUTION` but no
  `ExecutionRecord` (D30 puts that on `ExecutionSheet`), so the two diverge.
  Either make the gate fall back to `state.deaths` the way `InfoCalc` does, or
  make the KillSheet's `Execution` cause write the record (and make the dusk
  card notice it, since it currently offers to record NO_EXECUTION over the top
  of a real one).

---

## P1

### B2-5. P1 · An impaired Monk's outcome-stating primary asserts a protection the engine itself knows is inert

- **Screen / flow** Trouble Brewing, night 2, the Monk step, Monk poisoned.
- **Repro** (game 2 — Player 8 is the Monk, Player 6 the Poisoner)
  ```sh
  # night 2, step 2: the Poisoner chooses the Monk
  ./ui.py emulator-5554 tap "8  Player 8"
  ./ui.py emulator-5554 tap "PLAYER 8 — POISONED"
  # step 3, the Monk:
  ./ui.py emulator-5554 find "IMPAIRED"
  ./ui.py emulator-5554 find "SAFE"
  ```
- **Expected** the same treatment the info rows now get (B-9's fix): when the
  card's own banner says `IMPAIRED — Poisoned by the Poisoner (Player 6). Their
  ability…`, the one full-width gold button must not state an outcome that will
  not happen. An info row in this state reads
  `PICK WHAT TO SHOW — PLAYER 1 IS IMPAIRED` and is `DISABLED`.
- **Actual** the Monk's primary reads **`PLAYER 1 — SAFE`**, flat, enabled, with
  no qualifier — directly under the IMPAIRED banner. (The *engine* is right:
  two steps later the Imp's card correctly previews
  `Player 1: Nothing stops it — they die.` and Player 1 dies.) The wrong half is
  the button and the collapsed row, which are what a storyteller running a table
  in the dark actually reads; on this night they say the Ravenkeeper was
  protected and then he dies.
- **Screenshots** `tools/emu/out/B2/28-monk-poisoned.png` (banner + button),
  `tools/emu/out/B2/29-imp-vs-poisoned-monk.png` (the engine's correct verdict).
- **Node bounds** `#40 'IMPAIRED — Poisoned by the Poisoner (Player 6)…'
  [69,1091][1011,1203]`, `#59 'PLAYER 1 — SAFE' [69,1658][1011,1805] @(540,1731)`.
- **Suspect** `app/.../ui/screens/night/NightRows.kt:332` (`primaryLabel`) and
  `:420` (`placedLabels`) — the label is built from the effect's token label
  (`Safe`) with no reference to `info.abilityMalfunctions` /
  `Status.hasAbility`, whereas the answer half of the same function already
  routes through `owesFalseInfo` in `NightCard.kt:222-228`. Marking the placed
  token as inert in the label (`PLAYER 1 — “SAFE” (ability not working)`) would
  match what the card already says two lines above.

### B2-6. P1 · While the star-pass hand-over card is open, no other row in the sheet can be opened — taps are silent no-ops

- **Screen / flow** Trouble Brewing, night 2, immediately after the Imp kills
  itself and a Minion becomes the Imp.
- **Repro** (game 1, straight after `B_fix_starpass`)
  ```sh
  ./ui.py emulator-5554 tap "^Night$"
  ./ui.py emulator-5554 find "step "            # → "step 2 / 6", the dead Imp's hand-over card
  ./ui.py emulator-5554 tapxy 540 1616          # the collapsed "Empath — Player 1" row
  ./ui.py emulator-5554 find "step "            # → still "step 2 / 6"
  ./ui.py emulator-5554 tapxy 540 1790          # the collapsed "Imp — Player 8" row
  ./ui.py emulator-5554 find "step "            # → still "step 2 / 6"
  ```
- **Expected** a collapsed row is a navigation control everywhere else in the
  sheet (E-6's whole point, and `E_fix_night_pointer` relies on it) — tapping
  one opens it.
- **Actual** every collapsed row is drawn normally, dumps as `click`, is fully
  on screen (verified against the screenshot, so this is not the harness's
  scrolled-out case), and does nothing at all. The header stays on `step 2 / 6`.
  The `show them` chip on the row does nothing either. Only after the hand-over
  card's own `DONE — THEY HAVE SEEN IT` is pressed do row taps start working
  again (verified immediately afterwards: `tap "Imp — Player 8"` →
  `step 4 / 6`). Nothing on screen says the sheet is pinned, and the card that
  pins it carries no "you must do this first" wording.
- **Screenshot** `tools/emu/out/B2/06-rowtap-noop.png` — the rows are plainly
  visible at the bounds that were tapped.
- **Node bounds** `#34 '<View>' [32,1537][1048,1695] @(540,1616) click` (Empath
  row), `#40 '<View>' [32,1711][1048,1869] @(540,1790) click` (Imp row).
- **Suspect** `app/src/main/java/com/clocktower/grimoire/ui/screens/NightScreen.kt`
  — the `activeToken` re-point guard added for E-6/D81. The hand-over row is the
  dead Imp's step, whose `StepKey` shares its `abilityId` with the heir's new
  `Imp — Player 8` row; if the row list addresses steps by token, two `IMP` rows
  collide and every click resolves back to the first. That would explain the Imp
  row, but the *Empath* row is inert too, so more likely a `LaunchedEffect` is
  re-asserting the token while an unresolved `BriefingSlot.NOW`-style prompt is
  live. Either way: if the sheet is deliberately pinned, say so on the card;
  if not, the rows should navigate.

---

## P2

### B2-7. P2 · The Investigator (and any multi-word character) gets **two** identical truth offers, one of them labelled with the raw character id

- **Repro** game 2, night 1, step 7:
  ```sh
  ./ui.py emulator-5554 find "SHOW: SCARLET"
  ```
  ```
  #30 'SHOW: SCARLETWOMAN — Player 2, Player 3'  [101,798][843,846] @(472,822)
  #32 'SHOW: SCARLET WOMAN — Player 2, Player 3' [101,940][852,988] @(476,964)
  ```
- **Expected** one truthful offer.
- **Actual** two, differing only in that the first prints the engine id.
  Choosing it makes the **primary** read
  `SHOW “SCARLETWOMAN — PLAYER 2, PLAYER 3” TO PLAYER 12` — a raw id on the
  button the storyteller reads out. (The card itself renders `Scarlet Woman`
  correctly.) Invisible for one-word characters, so every Trouble Brewing
  playtest so far has missed it; it will show on Scarlet Woman, Fortune Teller,
  Devil's Advocate, Tea Lady, Snake Charmer, Pit-Hag…
- **Screenshots** `tools/emu/out/B2/19-investigator-dupe.png`,
  `tools/emu/out/B2/21-primary-rawid.png`,
  `tools/emu/out/B2/20-scarletwoman-rawid-card.png`.
- **Suspect** `engine/.../NightPlan.kt:1125-1134` — `infoCards` calls
  `cardsFor(ctx.state, result)` with the **default** `nameOf = { it }`
  (`:1145-1156`, `labelFor` at `:1158-1175` uppercases the id), while
  `app/.../ui/screens/night/NightCard.kt:138-141` calls the same function with a
  real name resolver. Both lists are concatenated at `NightCard.kt:143-147` and
  `distinctBy { it.label }` cannot collapse them because the labels differ.
  Pass the lookup in `infoCards`, or build the offers in one place.

### B2-8. P2 · Game log and dawn notes print `Player 8 wakes (shown to)` — a dangling parenthetical

- Every Minion-info / Demon-info wake logs as `Player 2 wakes (shown to)`,
  `Player 4 wakes (shown to)`. Reproduced in both the dawn briefing and ⋮ →
  Game log in games 1 and 2. Screenshot `tools/emu/out/B2/03-dawn-n1.png`.
- **Suspect** `engine/.../GameLog.kt:184`
  ```kotlin
  LedgerKind.WOKE ->
      "${name(entry.actorId)} wakes" + if (entry.genuine) " for $source" else " (shown to)"
  ```
  `source` is empty for a marker step, so the else-branch has nothing to name.
  `" (Minion info)"` / `" — shown the Demon"` would read.

### B2-9. P2 · Game log and dawn notes invent a choice for info-only steps: `Player 6 (Washerwoman) chooses nobody`

- Every "start knowing" / count / yes-no step writes a CHOICE row with no
  targets, so the log reads `Player 6 (Washerwoman) chooses nobody`,
  `Player 7 (Chef) chooses nobody`, `Player 3 (Investigator) chooses nobody`.
  Five of the twelve night-1 log lines in game 2 are this. Screenshot
  `tools/emu/out/B2/03-dawn-n1.png`.
- **Suspect** `engine/.../NightPlan.kt:2219-2244` — `recordChoice` bails out
  only when `step.action == null`:
  ```kotlin
  if (step.action == null && targets.isEmpty() && !input.none) return state
  ```
  A `ShowInfo` with `targetsNeeded = 0` is a non-null action with no targets and
  no `input.none`, so it still writes a row. Add `action is ShowInfo &&
  targetsNeeded == 0` to the bail-out, or render an empty CHOICE as nothing in
  `GameLog.kt:144-153`.

### B2-10. P2 · The Ravenkeeper's and Grandmother's card prefix reads `THIS CHARACTER` — B-17's defect, unfixed for `revealCharacter`

- Ravenkeeper (game 2, night 2) and Grandmother (game 3, night 1) both display a
  full-screen card headed **`THIS CHARACTER`** over the token, and the ledger
  records `Player 1 (Ravenkeeper) is shown THIS CHARACTER Imp`.
  Screenshot `tools/emu/out/B2/33-grandmother-card.png`.
- **Suspect** `engine/.../InfoCalc.kt:636-645` — `revealCharacter` sets no
  `cardPrefix`, so the renderer falls back to the generic stem. `undertaker`
  (`:504-524`) got `cardPrefix = "THIS CHARACTER DIED TODAY"` in the last wave;
  this one wants `"THIS PLAYER IS"` (or the Grandmother's own wording).

### B2-11. P2 · Minion info's primary names only the first Minion

- The card wakes three (`Wake all Minions (Player 2, Player 6, Player 11)`) and
  the primary reads `SHOW “THIS IS THE DEMON — PLAYER 4” TO PLAYER 2`.
  Screenshot `tools/emu/out/B2/12-minion-info.png`.
- **Suspect** `app/.../ui/screens/night/NightRows.kt:332` `primaryLabel(holder =
  holderName, …)` — a group step's `holderName` is the first of `step.wakes`.
  `TO THE MINIONS` would do.

### B2-12. P2 · The Spy's night card offers no way to show the grimoire

- The card is `Show the Grimoire to the Spy for as long as they need.` with a
  bare `DONE — NEXT STEP`; the drawer holds only `show a card…` and
  `HOW TO RUN THIS`. The feature exists — ⋮ → *Show the grimoire to a player…* —
  but the one row that always needs it does not link to it.
  Screenshot `tools/emu/out/B2/22-spy.png`.
- **Suspect** `app/.../ui/screens/night/NightCard.kt` (the drawer's action list)
  / `GameShell.kt` (which owns the Spy read-only mode added by Fix-D).

### B2-13. P2 · The Fortune Teller's collapsed row records the picks but not the answer

- `✓ 10 Fortune Teller — Player 9 · Player 9 · → Player 1, Player 5` — no
  `shown: YES`, where the Chef's row on the same screen reads `shown: 0`.
  Screenshot `tools/emu/out/B2/22-spy.png` (the list behind the card).
- **Suspect** `app/.../ui/screens/night/NightRows.kt` — `resultOf` prefers the
  target list over the shown card when both exist.

### B2-14. P2 · The Innkeeper's primary does not say which of the two is drunk

- `PLAYER 1, PLAYER 3 — SAFE + DRUNK` reads as though both are safe *and* drunk.
  The card's prompt is explicit (*"the SECOND one you tap is the drunk one"*),
  so the information exists; the button throws it away. Screenshot
  `tools/emu/out/B2/43-innkeeper.png`.
- **Suspect** `app/.../ui/screens/night/NightRows.kt:420` `placedLabels` — it
  joins the placed-token labels without their targets.

### B2-15. P2 · A "start knowing" card offers exactly one true option; choosing *which* Townsfolk to show is not on the card

- With nine Townsfolk in play the Washerwoman's card offers one truthful chip
  (`SHOW: RAVENKEEPER — Player 1, Player 2`) and, in the drawer, five variants of
  **the same character** with different decoys plus two out-of-play lies. The
  choice of which real Townsfolk to reveal — one of the storyteller's main
  levers — is only reachable by leaving the card for `show a card…`.
  Screenshot `tools/emu/out/B2/17-ww-poisoned.png`.
- **Suspect** `engine/.../InfoCalc.kt` `startKnowing` — it commits to one
  candidate and generates alternatives by moving the decoy, not by changing the
  character.

---

## `ui.py audit` on every screen

Run on: the setup TABLE/BAG cards, HAND OUT TOKENS, the setup checklist sheet,
the 8- and 15-seat grimoires, every night card kind (marker, info, choose-player,
choose-character, Options, kill), every full-screen show card (message, character
token, number, alignment, bluffs, point-at-a-seat, multi-name point card), the
privacy cover, the dawn briefing, the dusk sheet, the game-log dialog, the
`show a card…` sheet, the seat sheet and the KillSheet.

**Everything reported `safe area: OK` and `overlap: OK`** except:

1. **the known 8-seat zoom overlap** (already in FOLLOWUPS' fix-wave-3 queue —
   not re-filed):
   ```
   === OVERLAPPING CLICKABLES (1) ===
     5% overlap:
         #48 '<View>' [134,1554][383,1840] @(258,1697)  click,long
         #63 '<View>' [32,1806][158,1932] @(95,1869)  click
   ```
2. **a false positive on the `show a card…` sheet**, reported here so the next
   tester does not re-file it. `audit` flags four character tiles:
   ```
   === SAFE-AREA VIOLATIONS (4) ===
     #72 '<View>' [53,2208][253,2337] @(153,2272)  click
         - bottom 21px under the navigation/gesture inset (home indicator)
     …#74, #76, #78 identically
   ```
   The tiles' **scroll container** is `#10 '<View>' [53,127][1027,2274]`, i.e. it
   ends 42 px *above* the safe-area edge, so nothing is actually drawn under the
   gesture strip — these are uiautomator's unclipped layout bounds. The centres
   (y = 2272) are inside the container, so the harness's scrolled-out filter
   does not drop them. Judged an artefact, not a bug.
3. **the seat sheet's `Change…` character picker**, which FOLLOWUPS' fix-wave-3
   queue already carries — **not re-filed**, but the measurement has moved and
   the fix wave should know: the "top 8 px under the status bar" half is gone,
   the bottom half is not, and it is bigger than the 21 px recorded there.
   ```
   === SAFE-AREA VIOLATIONS (1) ===
     #64 '<View>' [53,2231][1027,2375] @(540,2303)  click
         - bottom 59px under the navigation/gesture inset (home indicator)
   ```
   Unlike (2) this one is real: the picker's scroll container is
   `#10 '<View>' [53,127][1027,2337]`, which itself runs 21 px past the
   safe-area edge, so the last row genuinely draws into the gesture strip.
   Screenshot `tools/emu/out/B2/45-change-picker.png`.

No `tap` reported `OFFSCREEN` anywhere in four games. Two `CLIPPED` refusals were
correct (rows scrolled behind the card).

---

## What worked

Everything B-1…B-20 claimed, plus:

- **The 1-of-2 shape is right in every direction.** In the 15-seat game the
  Washerwoman was never offered the Drunk (an Outsider holding a Soldier token)
  as her Townsfolk, the Librarian was offered the Recluse with the Spy as decoy,
  and none of the three was ever offered the holder.
- **Recluse and Spy caveats are on the card.** Every affected info card carries
  `! Player 10 is the Recluse — may register as evil / a Minion…` and
  `! Player 11 is the Spy — may register as good / a Townsfolk…`, including the
  Chef's and the Fortune Teller's.
- **Computed info checked by hand against a 15-seat ring, all correct**: Chef 0
  (evil in seats 2, 4, 6, 11 — no adjacent pair); Empath 0 then 1 with the
  breakdown naming the live neighbours and skipping the dead; Empath correctly
  walking past *two* dead seats after the Ravenkeeper and the Drunk died;
  Fortune Teller YES on the red herring and YES on the Imp with the Recluse
  caveat; Ravenkeeper `Player 4 is the Imp`; Undertaker `Show: Drunk` for an
  executed Drunk; Godfather `Outsiders in play: Tinker`.
- **The wake order matches the official night sheet exactly** on all four
  tables, checked against `tools/data/nightsheet.json`:
  TB first night `Dusk · Minion info · Demon info · Poisoner · Washerwoman ·
  Librarian · Investigator · Chef · Empath · Fortune Teller · **Spy** · Dawn`
  (the Spy really is last, after the Butler); TB other nights
  `Dusk · Poisoner · Monk · Scarlet Woman · Imp · Ravenkeeper · Empath ·
  Fortune Teller · Undertaker · Spy · Dawn`; BMR first night
  `Dusk · Minion info · Demon info · Sailor · Courtier · Godfather · Dawn`;
  BMR other nights `Dusk · Sailor · Courtier · Innkeeper · Exorcist · Zombuul ·
  Godfather · Professor · … · Dawn`, with the Lunatic's row correctly sitting at
  the **lunatic** slot and titled `Po — Jonas (via the Lunatic)`.
- **Skip gates and their reasons**: `⊘ Scarlet Woman — they have not become the
  Demon`, `⊘ Ravenkeeper — they are alive — this ability only fires on the night
  they die` (and it flipped to live the moment he was killed), `⊘ Godfather`,
  `⊘ Gossip`, `⊘ Grandmother — the grandchild was not killed by the Demon
  tonight`, each with `[Run anyway]`.
- **E-1…E-6 all hold.** E-2 the Grandchild picker excludes the Grandmother
  (`⌄ 1 they cannot choose` → `5 Erin`); E-3 `[Undo]` is on the collapsed rows
  and nowhere else; E-4 `DEV — POISONED · FINN DIES` with
  `Finn: Nothing stops it — they die.` above it; E-5 the Grandmother opens
  pre-armed on `Finn is the Fool` / `SHOW “FOOL” TO ERIN`; E-6 finishing step 6
  opened 7, step 8 opened 9, step 10 wrapped back to the owed step 7 rather than
  to Dawn, and finishing the last row wrapped to the still-owed Dusk instead of
  auto-opening Dawn. A mid-game character change raises no setup checklist (E-1).
- **The setup gates.** The Drunk cannot be handed out until the storyteller
  picks the token they see, and the picker offers only **not-in-play** Townsfolk
  (Mayor/Slayer/Soldier/Virgin) — after which the bluff list annotates Soldier
  with `the Drunk believes this`. The Godfather's `[±1 Outsider]` bracket really
  blocks a 5/1/1/1 bag with `Townsfolk: 5 in bag, expected 4 or 6` and
  `Deal anyway — I know what I'm doing`.
- **The Innkeeper's ordering convention is documented on the card**
  (*"the SECOND one you tap is the drunk one, until dusk tomorrow"*), and the
  Courtier's spend rule is too (*"Pointing at anyone spends the ability — even a
  character not in play"*).
- **The kill funnel is consistent** — a poisoned Monk's `Safe` token is placed
  (so the grimoire looks normal to a Spy) and the Imp's card still correctly
  reads `Nothing stops it — they die.`
- Show cards: body taps still do not dismiss, `⟳ FLIP` rotates, the 1.2 s hold
  lands on the privacy cover captioned `First night · press and hold to open`,
  and the multi-token card renders 8 tokens legibly.

## What I could NOT reach

- **The Zombuul's kill, and its "first time you die you live but register as
  dead"** — the row was reached and correctly live (`Zombuul — Player 5 ·
  1 pick`, nobody having died that day) but not resolved; the "someone died
  today ⇒ no Zombuul kill" gate is untested.
- **Shabaloth and Po** — one Demon per table; the Po's charged 3-kill and the
  Shabaloth's regurgitation are untested. (The Lunatic's *believed* Po row was
  exercised.)
- **The Gossip's day statement → night death**, **the Professor's
  resurrection**, **the Tea Lady**, **the Devil's Advocate's execution
  protection** — all present as rows in games 3 and 4, none resolved.
- **The Scarlet Woman promotion** (the row was correctly skipped
  `they have not become the Demon`; the Imp never died with 5+ alive in game 2).
- **Baron, Butler, Saint** — no room left in either Trouble Brewing bag.
- **A drunk info malfunction** — the Drunk's believed character (Soldier) has no
  night action, so only the poisoned path was exercised. The Drunk's own
  malfunction on an info role remains untested.
- **The `[Run anyway]` override on a row that is genuinely finished**, the day
  screen's vote UI (agent C's area — note that my eight taps on the voter ring
  silently cancelled the nomination instead of voting), landscape, the PWA.

## Harness notes (not findings)

- `E_fix_night_pointer` and `E_fix_pukka_death` each failed once at a
  `hold "The grimoire is closed" 1200` that did not open the privacy cover; a
  hand-run `hold … 1200` immediately afterwards worked. Non-deterministic, ~1 in
  3 on this instance. Both runs were completed by hand from the failure point
  and every assertion after it held. Consider 1500 ms in those scenarios.
- Choosing the script on the New game screen **auto-expands the TABLE card**.
  A scenario that then taps the TABLE row to expand it actually collapses it —
  cost me three runs of `B2_tb15_setup` before I read the screenshots.
  Noted in that scenario's comments.
- `ui.py swipe down 2000` does nothing useful (larger than the safe area);
  repeated `swipe down 900` is the way back to the top of a long card.
