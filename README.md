# Clocktower Grimoire

A digital grimoire for running **Blood on the Clocktower** in person — a
storyteller's companion Android app that replaces the physical grimoire,
night sheets and vote counting for any script.

## Features

**The grimoire**
- Circular seat layout mirroring the physical grimoire, first seat at
  12 o'clock, clockwise — pinch to zoom, drag to pan
- Character tokens with team-coloured rings, shrouds on the dead,
  ghost-vote tracking, traveller badges
- Reminder tokens: every character's official reminders plus generic
  markers (Drunk, Poisoned, Mad, ...), fanned under each seat
- Per-seat actions: kill (by cause), revive, change character, flip
  alignment, rename, seat notes
- Unlimited undo, automatic save & resume (survives app restarts)

**Any script**
- All three base editions bundled: Trouble Brewing, Bad Moon Rising,
  Sects & Violets
- 171 official characters embedded — the full experimental/Carousel roster,
  travellers, and fabled — with abilities, reminders and night prompts
- Import any custom script as JSON from the official script tool
  (script.bloodontheclocktower.com) or botc-scripts, including scripts with
  inline homebrew characters
- Per-script jinx list (the Djinn's rules), auto-filtered to what's in play

**Storyteller intelligence**
- The night sheet computes the TRUE answer for information abilities right
  from the grimoire: Empath neighbour counts, Chef pairs, Clockmaker steps,
  Shugenja direction, Oracle, Undertaker, Town Crier / Flowergirl (from the
  recorded nominations and votes), Fortune Teller (with red-herring
  tracking), Dreamer, Seamstress, Village Idiot, Chambermaid and more —
  including target selection for "choose a player" abilities
- Every computed answer carries the caveats that matter: the holder being
  drunk/poisoned (from reminder tokens or being the Drunk), Spy/Recluse
  misregistration, and the Vortox forcing false townsfolk info
- Minion/demon info steps name the actual evil players and your chosen bluffs
- Full-screen cards to show players silently across the table: YOU ARE /
  THIS PLAYER IS / THIS CHARACTER SELECTED YOU with character tokens,
  number and good/evil signals, the three bluffs, and free editable text

**Running the game**
- Three-stage setup: script → seats → bag, with the official player-count
  distribution, automatic adjustment for setup-modifying characters
  ([+2 Outsiders], [+1 Minion], ...), a legal-bag randomizer and validation
- Night sheet generated for the characters actually in play, in official
  wake order, with storyteller prompts, check-off tracking, and dusk/dawn +
  minion/demon info steps (auto-omitted in teensyville games)
- Day tools: nomination flow with tap-to-tally voting, execution threshold,
  tie handling, ghost-vote spending, traveller exile votes, the day's
  nomination record, and one-tap execution
- Demon bluff picker, storyteller notes, discussion timers, mid-game seats
  for arriving travellers
- Full reference library: character sheets, night order and jinxes for
  every bundled or imported script, browsable outside a game

## Building

Open the project in Android Studio (Ladybug or newer) and run the `app`
configuration, or build from the command line:

```
# Character art is downloaded separately and is intentionally not committed.
bash tools/fetch-icons.sh
./gradlew :app:assembleDebug
# APK lands in app/build/outputs/apk/debug/app-debug.apk
```

Requires JDK 17+. The Android SDK (compileSdk 35) is fetched/managed by
Android Studio as usual. Minimum supported device: Android 8.0 (API 26).
If the icon-fetch step is skipped, the app still builds but uses emoji
fallbacks. On Windows, run the script with Git Bash.

The GitHub Actions workflow also assembles a debug APK on every push —
grab it from the workflow run's artifacts if you don't have Android
Studio handy.

## Project layout

- `engine/` — pure-Kotlin game core: the character/jinx/night-order
  dataset, script parsing (official JSON format incl. homebrew), setup
  distribution math, night-sheet builder, nomination/vote rules and
  immutable game-state transitions. Fully unit-tested on the JVM
  (`./gradlew :engine:test`) with no Android dependency.
- `app/` — Jetpack Compose UI (Material 3, dark "candlelit" theme).
- `tools/uicheck/` — a standalone verification build that type-checks the
  Compose UI against JetBrains' Compose Multiplatform artifacts from Maven
  Central, so UI compile errors are caught even in environments without
  access to Google's Maven repository: `gradle -p tools/uicheck compileKotlin`.

## Data

Character data (abilities, reminder tokens, night order, jinxes) is
embedded at `engine/src/main/resources/botc/data/` and follows the
conventions of the official script tool JSON format. Blood on the
Clocktower is a trademark of Steven Medway and The Pandemonium Institute;
this is an unofficial fan-made storyteller tool.
