# Complex night actions: Android emulator QA, 2026-10-15

Agent: `multistage_emulator_qa`. Device: **emulator-5556 only**, read-only `grimoire`
AVD, Android 35, 1080×2400, gesture navigation. No physical device, push, or
commit was used. Synthetic setup is loaded through `seed-complex-night.py`;
every action under test is then selected and confirmed in the actual app UI.
Tap and hold coordinates come from `tools/emu/ui.py`'s accessibility-tree lookup.

Baseline APK SHA-256:
`40c8bd188133d828bd846e9c29f19d8982eb89efc79d15eb8a4396342bc4e407`.
The baseline already contains the earlier information-card and hold fixes. It
does not contain the new complex-input renderer.

## Reproduction setup

From the repository root, install the intended APK on `emulator-5556`, then:

```sh
python3 tools/emu/seed-complex-night.py gambler
python3 tools/emu/ui.py emulator-5556 tap '^Resume game$'
python3 tools/emu/ui.py emulator-5556 tap '^Night$'
python3 tools/emu/ui.py emulator-5556 tap '^Gambler — Actor$'
python3 tools/emu/ui.py emulator-5556 dump
python3 tools/emu/ui.py emulator-5556 audit
```

Replace `gambler` and the row name with `engineer` / `Engineer — Actor`,
`alhadikhia` / `Al-Hadikhia — Actor`, `courtier` / `Courtier — Actor`, or
`imp` / `Imp — Actor` for the other cases. Scroll and re-dump when needed.
Engineer is already the open card after Night, so its row tap is unnecessary.
The fixture deliberately includes seven seats, three evil seats, alternate evil
characters on the script, and a dead Bob for the Al-Hadikhia resurrection case.
Evidence below is under `tools/emu/out/complex-night/` (gitignored artifacts).

## Baseline findings

1. **P1 — Gambler cannot enter the required choices.** The card displays
   “WHO, AND WHICH CHARACTER?” followed directly by “Custom choice…” and an
   enabled hold button. No player picker, character picker, or Wrong/Correct
   decision exists, including after scrolling. Holding DONE with no input
   advances to Imp and persists `gambler#1` in `nightStepsDone`.
   Expected: record player, guessed character, and explicit correctness before
   completing the action; this is also the contract in
   `RulesBadMoonRising.kt:gambler`.
   Evidence: `baseline/gambler.png`, `baseline/gambler.xml`,
   `baseline/gambler-after-empty-hold.json`.
   Suspect: `NightCard.kt` input dispatch omits `Sequence`.

2. **P1 — Engineer cannot assign characters to evil seats.** The card goes
   directly from “WHICH EVIL SEATS BECOME WHICH CHARACTERS?” to “Custom choice…”.
   DONE is enabled and is a plain tap despite the transformation/spend action.
   Scrolling reveals no assignment or decline controls. Expected: one character
   per selected evil seat, or explicit decline without spending; see
   `RulesExpTownsfolk.kt:engineer`.
   An empty DONE tap does not advance or spend the ability; it leaves the same
   card on screen without explaining the missing input.
   Evidence: `baseline/engineer.png`, `baseline/engineer.xml`,
   `baseline/engineer-after-empty-tap.json`.
   Suspect: `NightCard.kt` omits `ChoosePlayersAndCharacters`.

3. **P1 — Al-Hadikhia cannot enter its ritual.** The card offers three show
   cards and Custom choice, but no three-player selection and no independent
   live/die answers. After scrolling, DONE is already enabled as a plain tap.
   Expected: ordered players and each independent answer, or explicit no-one
   selection; see `RulesExpDemons.kt:alHadikhiaRitual`.
   Evidence: `baseline/alhadikhia.png`, `baseline/alhadikhia.xml`,
   `baseline/alhadikhia-bottom.png`, `baseline/alhadikhia-bottom.xml`.
   Suspect: `NightCard.kt` omits `Sequence` and `Options`.

4. **P1 — General cannot record its branch.** All three show-card controls
   exist, so the storyteller can communicate an answer, but there is no
   selected Good / Evil / Neither branch and DONE is enabled without one.
   Expected: choose and persist the `Options` answer before completing.
   Repro: seed `general`, open Night, tap `whole sheet`, then `General — Actor`;
   scroll and re-dump. Evidence: `baseline/general.png`, `baseline/general.xml`.
   Suspect: `NightCard.kt` omits `Options`.

## Baseline controls that passed

- **Courtier character-only use:** tap Chef, scroll to confirmation, hold
  `CHEF — DRUNK 1` for 1100 ms. No player selection is needed. Persisted state
  contains SPENT / No Ability on Actor and DRUNK / Drunk 1 on Alice, and
  `courtier#1` is completed. Evidence: `baseline/courtier-character.png`,
  `baseline/courtier-confirm.png`, `baseline/courtier-confirm.xml`,
  `baseline/courtier-after.json`.
- **Latest target and cancelled hold:** in the Imp case, tap `2  Alice`, then
  `3  Bob`, scroll 400 px, hold `BOB DIES` for 200 ms. All seven remain alive
  and `imp#1` is incomplete. Hold it for 1100 ms: Bob alone dies, Alice remains
  alive, and the collapsed row says “Bob died”. Evidence:
  `baseline/imp-latest-target.png`, `baseline/imp-latest-target.xml`,
  `baseline/imp-cancelled-hold.json`, `baseline/imp-confirmed-hold.json`.
- All captured baseline screens audited above had nonzero clickable counts
  and passed safe-area and partial-overlap checks. Scrolled-out rows were
  excluded by the harness, not counted as valid visible controls.

## Updated APK verification

**All targeted updated-build cases passed.** APK SHA-256:
`85c74a6f93e444e3db3312af0cdbb5f8569c9dd0b14373404f7762e39f33bc61`.
This was installed with `adb -s emulator-5556 install -r -t …`; the original
APK and updated APK are preserved alongside their evidence. The crash buffer
was empty at the end of both runs.

Updated runs use:

```sh
python3 tools/emu/seed-complex-night.py gambler --focused
python3 tools/emu/ui.py emulator-5556 tap '^Resume game$'
python3 tools/emu/ui.py emulator-5556 tap '^Night$'
```

`--focused` marks unrelated Poisoner, Imp, and Empath rows complete so the
target opens directly. Substitute the case name for each row below. Cerenovus
was added to the script for the updated run to allow two distinct new Minion
characters without taking a character away from its current owner.

| Case and UI action | Verified result | Evidence under `tools/emu/out/complex-night/updated/` |
| --- | --- | --- |
| Gambler: choose Bob and Chef, leave verdict unanswered | `CONFIRM: BOB, CHEF` disabled | `gambler-incomplete.png`, `gambler-incomplete.xml` |
| Gambler: select Wrong, change guessed character to Monk | Verdict cleared; `CONFIRM: BOB, MONK` disabled | `gambler-reset.txt`, `gambler-reset.xml` |
| Gambler: Bob + Chef + Wrong, hold ACTOR DIES 1100 ms | Actor dies; Bob and all other seats stay alive; `gambler#1` completed | `gambler-wrong-confirm.png`, `gambler-wrong-after.json` |
| Gambler: Alice + Chef + Correct, tap confirmation | All seats remain alive, no effects, `gambler#1` completed | `gambler-correct-confirm.png`, `gambler-correct-confirm.xml`, `gambler-correct-after.json` |
| Engineer: select Eve and Finn before assigning characters | Separate labelled grids; confirmation disabled; Eve cannot pick Finn's existing Baron, Finn cannot pick Eve's existing Poisoner | `engineer-incomplete.png`, `engineer-incomplete.xml` |
| Engineer: Eve → Scarlet Woman and Finn → Cerenovus, hold confirmation 1100 ms | Both independent transformations persisted; Dan stays Imp; Actor has SPENT / No Ability; `engineer#1` completed | `engineer-confirm.png`, `engineer-confirm.xml`, `engineer-after.json` |
| Engineer: Dan → Fang Gu and Eve → Scarlet Woman | Explicit “Choose the Minions or the Demon, never both.”; confirmation disabled | `engineer-mixed-complete.png`, `engineer-mixed-complete.xml` |
| Engineer: choose Declined after the invalid mixed set, then tap THEY CHOSE NOBODY | Assignment grids and payload clear; all original characters retained, no effects and no SPENT; step completed | `engineer-decline-confirm.txt`, `engineer-decline-after.json` |
| Al-Hadikhia: pick Bob, Alice, Cara in that order | Visible `1. Bob → 2. Alice → 3. Cara`; independent 1st/2nd/3rd answer sections | `alhadikhia-ordered.png`, `alhadikhia-ordered.txt` |
| Al-Hadikhia: first Live, second Die, third Live; hold ALICE DIES 1100 ms | Dead Bob revives; Alice dies; Cara remains alive. Numbered markers 1/2/3 target Bob/Alice/Cara respectively | `alhadikhia-confirm.png`, `alhadikhia-confirm.xml`, `alhadikhia-after.json` |
| Al-Hadikhia: select the same three, answer first Live, then choose no one and confirm | Ordered selection and answer sections clear; Bob stays dead, others stay alive, no effects; `alhadikhia#1` completed | `alhadikhia-none-confirm.png`, `alhadikhia-none-confirm.xml`, `alhadikhia-none-after.json` |
| General: no answer, then select GOOD is winning and tap CONFIRM CHOICE | Initial DONE disabled; selection enables confirmation. Ledger persists CHOICE `good` and TOLD `GOOD IS WINNING`; `general#1` completed | `general-incomplete.txt`, `general-confirm.png`, `general-confirm.xml`, `general-after.json` |

Every selection/confirmation above was a UI action. JSON files are read-back
evidence from `files/datastore/grimoire.json`, not simulated engine results.
The normal tap/hold commands locate controls by fresh UI-tree semantics. Where
separate stages reuse the same label (Al-Hadikhia Live/Die) or character name
(Engineer's two grids), the distinct stage/header and node bounds in the saved
tree were used for `adb shell input tap`, then the resulting selected label or
summary was rechecked. An initially selected second-player Live during the
Al-Hadikhia test was explicitly corrected to Die before confirmation; the final
summary and persisted state verify Live / Die / Live.

Screenshots of the Gambler baseline and Courtier confirmation, plus the updated
Engineer assignment confirmation, were visually inspected. Updated Gambler,
Engineer, Al-Hadikhia, and General screens each had nonzero layout-audit control
counts and passed safe-area/partial-overlap checks. Scrolling was necessary for
the long typed actions; the tested controls and confirmations remained reachable.

Scope: these are targeted ordinary-action regressions, not an exhaustive rules
or character-interaction audit. Al-Hadikhia's all-three-die branch, special
per-victim protection/death-choice interactions, and Engineer's storyteller
override were not exercised in this run. Courtier and the target-change/short-
hold checks were verified on the baseline APK, not repeated on the updated APK.
No implementation files were modified by this QA agent.

Fixture note: baseline captures used the legacy lowercase `dusk` marker, which
left Dusk visibly uncompleted; the helper now uses the engine's actual `DUSK`
token. All tested abilities were explicitly opened, so this does not change
their input or effect evidence.
