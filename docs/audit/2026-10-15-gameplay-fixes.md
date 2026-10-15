# Gameplay fixes — 15 October 2026

Base: `35391eb`, branch `codex/playtest-flow-fixes`.

## Changes

- **Character handout:** a fixed bottom hold target leaves the character and ability above the finger. Quick taps reveal nothing; release or gesture cancellation hides the card. Safe-area padding applies both during setup and inside the in-game dialog. Departed seats are excluded.
- **Phase transitions:** requests read the current game, and confirmations belong to the game, phase and cycle that opened them. Closing a day is one undoable transition. Unanswered execution/exile choices keep the day open and lead to the appropriate choice sheet. Executing a dead player is recorded as an execution.
- **Day/night controls:** draft votes and dialogs are scoped to their cycle. Inactive tabs explain how to open their phase. Day sections can be explicitly collapsed instead of immediately reopening the default. Same-day tab switches retain intentional overrides; changing nomination participants clears the old voters. Self-nomination works with two taps on one seat. Night scrolling targets the active card in the displayed list, and showing an offered card does not force the same card to be shown again when finishing the step.
- **Travellers:** arrival requires a character and explicit alignment. Exile calls work independently of ordinary nomination restrictions and ability triggers. A funny Deviant's survival is a recorded resolution, so it does not block dusk forever. Departure retains history and removes the player from the table, ongoing abilities, voting and information calculations; seat moves skip departed players.
- **Information wording:** drunkenness and poisoning allow a deliberate true or false answer. The text distinguishes that choice from a requirement to give false information. See the [official States rules](https://wiki.bloodontheclocktower.com/States#Drunkenness_and_Poisoning).

## Reproducible checks

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew :engine:test :app:assembleDebug

JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
./gradlew -p tools/uicheck test

JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
./gradlew -p web compileKotlinWasmJs

# Open a covered player card in Hand out tokens first:
python3 tools/emu/check_reveal.py emulator-5554
```

The gesture regression checks a short tap, sustained reveal, fixed control bounds,
card clearance, and hiding on release, using coordinates from the live UI tree.
Evidence is saved under the ignored `tools/emu/out/reveal-regression/` directory.

Session regressions include repeated dawn/dusk callbacks across five cycles with
JSON save/reload, voted execution, execution of a dead nominee, Pacifist survival,
Deviant exile resolution, dead exile callers, closed nominations, departure and
information-neighbor changes. UI regressions cover pending final-night updates,
stale callbacks, self-nomination, night scroll indices, and duplicate card display.

## Live emulator evidence

- Native app on API 35: revealed character and ability visibly above the held control.
- Compact 720×1280 display with 130% font scale: tested reveal remains readable and clear of the finger.
- Short tap stays covered; release hides; interrupting a hold with Home leaves the same card covered on resume.
- Screenshots: `tools/emu/out/reveal-held.png`, `reveal-small-held.png`, and gesture-regression output.
- An independent eight-player Trouble Brewing playthrough exercised real setup, both nights, dawn, nomination, execution, Undo/Redo, traveller arrival, exile after execution, and save/resume after an in-place APK update. It reached Day 2 with nine players, seven alive, the expected night death, and the Bureaucrat's three-vote effect displayed.
- Final screenshot and persisted save: `tools/emu/out/playtest-20261015-day2-complete.png` and `playtest-20261015-final-save.json`. Nonempty UI audits passed and the crash buffer was empty.

## Final validation

- Engine: **973 tests, zero failures/errors/skips**.
- Shared UI verification: **184 tests, zero failures/errors/skips**.
- Android: `:app:assembleDebug` passed.
- Web: `compileKotlinWasmJs` passed (compile validation; no browser playthrough).
- Reveal: gesture regression passed on ordinary character and both Traveller pages; compact display and interruption checks passed.
- Traveller arrival: name plus character left Seat them disabled until an alignment was chosen; the saved seat showed the chosen evil alignment.
- Day controls: the final APK allowed a dead caller to initiate exile after the execution and support it without spending the existing ghost vote; DAWN collapsed and DUSK became directly reachable. Day 1 advanced to Night 2 at its first step, and completing all Night 2 actions opened Day 2 correctly.
- Traveller departure: the seat disappeared, Undo restored it, and Redo removed it again. Screenshot: `tools/emu/out/traveler-left.png`.
- Independent review: phase transitions, saved-state handling, reveal privacy, and Traveller lifecycle reviewed; identified issues were repaired.
- `git diff --check` passed.

The full-suite run first exposed four new test fixtures that left voted nominations
at their default SAFE result. They now derive and supply the real vote outcome;
the complete suite was rerun successfully. No production behavior was weakened
for those tests.

Exile remains distinct from leaving: an exiled Traveller dies and can stay in the
game with a dead vote. Voluntary departure removes the seat. See the original
[rulebook, printed pages 25–26](https://villacuervos.es/static/files/BOTC_Main_Rulebook_-_V1.pdf).

The APK is a local debug build. These checks do not claim exhaustive coverage of
every character combination or a published release.
