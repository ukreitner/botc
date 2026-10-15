# Night handouts: Android emulator QA, 2026-10-15

Tester: `night_emulator_qa`. Device: **emulator-5554 only**, Android 35 arm64,
1080×2400, gesture navigation. No physical device was used or changed.
Baseline: pre-fix debug APK supplied by parent (same source as ce60478, without
bundled icon art). Fixed: current workspace debug APK supplied by parent,
including official icons. Screenshots are in `tools/emu/out/night-cards-2026-10-15/`.

## Reproducible setup

The saved-game fixture is synthetic, mirroring the reported role relationships.
It has 11 named seats; kira is a Lunatic shown Vigormortis; victor, todor and yan
carry Fake Minion markers; Grandmother todor has marius/Gambler as Grandchild;
marco is Dreamer and Natalie is unused Philosopher. The seed helper is hardcoded
to emulator-5554 and writes only that debug app's private datastore.

```sh
python3 tools/emu/seed-night-cards.py --case base
./tools/emu/ui.py emulator-5554 tap '^Resume game$'
./tools/emu/ui.py emulator-5554 tap '^Close$' # dismiss outstanding Widow setup
./tools/emu/ui.py emulator-5554 tap '^Night$'
```

All taps and holds below use fresh accessibility-tree matches. Each actual card
was visually inspected in addition to the tree and safe-area audit.

## Results

| Case | Baseline | Fixed UI evidence |
|---|---|---|
| Lunatic fake Minions | **P1** Instructions say show Minions, but only SHOW: BLUFFS offered | **PASS** Separate Minions and bluff controls. Player card says THESE ARE YOUR MINIONS and lists victor seat 2, todor seat 4, yan seat 5, without their real roles. |
| Grandmother | **P1** Generic THIS PLAYER IS / Gambler card, missing relationship and player | **PASS** THIS IS YOUR GRANDCHILD + Gambler token + marius seat 8 |
| Dreamer healthy | **P0** Target nova/Imp offered only SHOW: IMP; actual handout contained one token although instruction demanded two | **PASS** SHOW: MONK, IMP displays both token images together, with nova IS ONE OF THESE CHARACTERS |
| Philosopher explicit pass | Baseline explicit They chose nobody did **not** reproduce auto-spend; it stayed available next night | **PASS** With no choice, confirmation is disabled. Explicit pass through actual dawn/day/dusk remains available Night 2, including after force-stop/relaunch. |

### Lunatic

```sh
./tools/emu/ui.py emulator-5554 tap '^Vigormortis — kira'
./tools/emu/ui.py emulator-5554 swipe up 700
./tools/emu/ui.py emulator-5554 tap '^SHOW: THESE ARE YOUR MINIONS —'
./tools/emu/ui.py emulator-5554 audit
```

Baseline suspect: Lunatic handout construction in `engine/.../NightPlan.kt`.
Screenshots: `baseline-lunatic.png`, `fixed-lunatic-minions.png`.
Official expectation: [Lunatic](https://wiki.bloodontheclocktower.com/Lunatic),
run as Demon with false Minions and bluffs.

### Grandmother and Dreamer

From the same night, close handout; select whole sheet; scroll to named rows.

```sh
./tools/emu/ui.py emulator-5554 tap '^TAP TO CLOSE$'
./tools/emu/ui.py emulator-5554 tap '^whole sheet$'
./tools/emu/ui.py emulator-5554 swipe up 1200
./tools/emu/ui.py emulator-5554 tap '^Grandmother — todor$'
./tools/emu/ui.py emulator-5554 swipe up 600
./tools/emu/ui.py emulator-5554 tap '^SHOW: GAMBLER — marius$'
./tools/emu/ui.py emulator-5554 tap '^TAP TO CLOSE$'
./tools/emu/ui.py emulator-5554 tap '^Dreamer — marco$'
./tools/emu/ui.py emulator-5554 tap '^7  nova$'
./tools/emu/ui.py emulator-5554 swipe up 700
./tools/emu/ui.py emulator-5554 tap '^SHOW: MONK, IMP$'
```

Screenshots: `baseline-grandmother-card.png`, `fixed-grandmother.png`,
`baseline-dreamer-card.png`, `fixed-dreamer-pair.png`.
Suspects: `InfoCalc.kt`, `ShowCards.kt`, night handout conversion.
Official expectations: [Grandmother](https://wiki.bloodontheclocktower.com/Grandmother)
knows a good player and their character; [Dreamer](https://wiki.bloodontheclocktower.com/Dreamer)
learns one good and one evil character, one correct while healthy.

### Philosopher

```sh
python3 tools/emu/seed-night-cards.py --case philosopher
# Resume, Close setup sheet, Night
./tools/emu/ui.py emulator-5554 tap '^Philosopher — Natalie$'
./tools/emu/ui.py emulator-5554 swipe up 900
# Unselected DONE — NEXT STEP has DISABLED semantics.
./tools/emu/ui.py emulator-5554 tap '^They chose nobody$'
./tools/emu/ui.py emulator-5554 hold '^THEY CHOSE NOBODY$' 1800
./tools/emu/ui.py emulator-5554 tap '^Dawn$'
./tools/emu/ui.py emulator-5554 tap '^Dawn anyway$'
./tools/emu/ui.py emulator-5554 tap '^OPEN DAY 1'
./tools/emu/ui.py emulator-5554 tap '^DUSK$'
./tools/emu/ui.py emulator-5554 swipe up 850
./tools/emu/ui.py emulator-5554 tap '^Everyone, eyes closed'
./tools/emu/ui.py emulator-5554 tap '^NO EXECUTION — BEGIN NIGHT 2'
./tools/emu/emu.sh launch emulator-5554
# Resume, Close setup sheet, Night; Philosopher remains actionable.
```

Screenshots: `fixed-philosopher-unselected.png`, `fixed-philosopher-night2.png`,
`fixed-philosopher-night2-restored.png`. The baseline explicit-pass screenshot is
`baseline-philosopher-night2.png`. This does not establish the user's precise
accidental-spend trigger; it verifies no-choice and explicit-pass behavior.

## Layout / privacy

Lunatic and Grandmother use the existing point-card tap-to-close behavior;
Dreamer's token pair uses hold-to-close then the closed-grimoire screen.
All three fixed handouts passed safe-area checks (`x 0..1080`, `y 136..2316`)
and had no partial overlaps. Their screenshots show no underlying storyteller
information. Custom Dreamer opens a Characters picker with both preselected.

## Adjacent control regression: latest target and cancelled hold

**PASS** on fixed APK. In Night 2 Imp action, chose nicu then marco. A 250ms
hold did not act (9/9 alive remained). A subsequent 1800ms hold killed marco
only. Grimoire accessibility showed `Seat 1, nicu, Monk, alive` and
`Seat 7, marco, Dreamer, dead`. A read of persisted synthetic datastore confirmed
exactly one death event, player 9/marco, `DEMON_KILL`, killer Imp/player 7.

```sh
python3 tools/emu/seed-night-cards.py --case imp
# Resume, Night, whole sheet, scroll to Imp.
./tools/emu/ui.py emulator-5554 tap '^Imp — nova$'
./tools/emu/ui.py emulator-5554 tap '^1  nicu$'
./tools/emu/ui.py emulator-5554 tap '^7  marco$'
./tools/emu/ui.py emulator-5554 swipe up 650
./tools/emu/ui.py emulator-5554 hold '^MARCO DIES$' 250
./tools/emu/ui.py emulator-5554 find '^Night 2 · 9/9 alive$'
./tools/emu/ui.py emulator-5554 hold '^MARCO DIES$' 1800
./tools/emu/ui.py emulator-5554 tap '^Grimoire$'
```

Screenshot: `fixed-imp-latest-target.png`. Suspect addressed by control agent:
`ui/screens/night/NightControls.kt` retaining an earlier confirmation callback.
This is a fixed-build regression; I did not independently run it on baseline.

## Poisoned Dreamer

**PASS** using `--case dreamer-poisoned`. App explicitly reports Widow poison,
allows true or false information, and disables confirmation until a handout is
chosen. Both true and false presets are good+evil pairs. Picked nova/Imp then
`LIE · SHOW MONK, VIGORMORTIS`; player handout displays both images and names
at once, with no poison warning or true identity leaked.

Screenshot: `fixed-dreamer-poisoned-pair.png`. Safe-area/overlap audit passes.

## Nightwatchman recipient

**PASS core flow** using `--case nightwatchman`: choose marco, confirm, then
app says put nicu to sleep and wake marco separately, with a blocking
`SHOW 1 CARD TO MARCO` action. That action shows `THIS PLAYER IS THE`,
Nightwatchman token, nicu, seat 1. Screenshots:
`fixed-nightwatchman-recipient.png`, `fixed-nightwatchman-card.png`.

**P2 pending-handout wording found:** The queued recipient action appears under
`NOTHING TO DO — spent — this ability is once per game`. This contradicts the
pending show-card instruction. Reported to parent for suppressing the spent
label while the handout is pending; retest on final APK required.
Suspect: `ui/screens/night/NightCard.kt` skipped-gate explanation.

**PASS poisoned Nightwatchman:** `--case nightwatchman-poisoned`; select marco,
hold confirm. No queued recipient card appeared. Persisted `prompts` is empty;
ledger records `CHOICE` with `impaired=true`, source nicu `WOKE`, and
`MALFUNCTION`, without waking marco. Screenshot:
`fixed-nightwatchman-poisoned-no-wake.png`.

Crash buffer checked after the sweep: empty.

## Final APK verification

Installed and verified SHA-256:
`85c74a6f93e444e3db3312af0cdbb5f8569c9dd0b14373404f7762e39f33bc61`.

- **Pending wording PASS:** healthy Nightwatchman recipient handout no longer
  displays the contradictory NOTHING TO DO/spent label. Screenshot:
  `final-nightwatchman-pending.png`.
- **Standard no replay PASS:** SHOW 1 → TAP TO CLOSE → DONE — CARRY ON returns
  to the night list without showing the original card again. Screenshot:
  `final-nightwatchman-standard-done.png`.
- **Custom no replay PASS:** fresh fixture; Nightwatchman chooses marco →
  confirms → Custom choice → deselect `1 nicu`, select `2 victor` → Show custom
  choice. Actual player-facing card says victor/seat2/Nightwatchman. Close then
  DONE — CARRY ON returns to night list without an original nicu card. Persisted
  handout log contains exactly one `PointCard`, recipient actorId 9/marco,
  `playerNames=[victor]`, `seatNumbers=[2]`, `characterId=nightwatchman`;
  prompt 1 is resolved. Screenshots `final-nightwatchman-custom-card.png` and
  `final-nightwatchman-custom-done.png`.

The custom card intentionally differs from the true Nightwatchman solely to
verify the storyteller's override is preserved in this synthetic test game.
The other four character checks and poisoned/hold regressions above ran on the
preceding fixed build; the final APK retest focused on the final changed prompt
behavior. No remaining emulator finding in this tested scope.
