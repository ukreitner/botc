# Emulator playtest harness

Boot headless Android emulators, drive the Grimoire app from the shell, and catch
layout bugs automatically. Python 3 standard library only — nothing to install.

Three tools:

| file | what it does |
|---|---|
| `emu.sh` | boot / install / launch / kill / status for the emulators |
| `ui.py` | one command per gesture: dump, find, tap, swipe, type, screenshot, wait, **audit** |
| `scenario.py` | replays a list of `ui.py` steps, screenshotting every one |

---

## Quick start

From nothing to "app open, tree dumped" — four commands:

```sh
cd tools/emu
./emu.sh boot 1                  # boots emulator-5554 and installs the debug APK (~30 s)
./emu.sh launch emulator-5554
./ui.py emulator-5554 wait "New game"
./ui.py emulator-5554 dump
```

`boot N` gives you N instances on ports 5554, 5556, 5558, … (serials
`emulator-5554`, `emulator-5556`, …). It waits for `sys.boot_completed`, enables
gesture navigation, zeroes the animation scales, keeps the screen awake, and
installs the APK on each.

If the APK is missing, build it first:

```sh
cd "$(git rev-parse --show-toplevel)" \
  && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
     ./gradlew :app:assembleDebug
```

When you finish: `./emu.sh kill`. To check on the fleet: `./emu.sh status`
(serial, boot state, RSS in MB, foreground activity).

**The app persists the game in progress**, so a plain `launch` resumes it rather
than showing the Home screen. To start a playthrough from scratch:

```sh
./emu.sh launch emulator-5554 --fresh    # pm clear, then start
```

### Why every instance is `-read-only`

The emulator refuses to start a second instance of an AVD unless *all* instances
use `-read-only`, so `boot` passes it to every one. A read-only instance runs
against a throwaway copy of userdata, which means **the APK does not survive a
reboot** — `boot` therefore always reinstalls, and each instance starts with a
clean app state (which is what you want for a playtest anyway).

`install` always uninstalls first: debug and CI builds are signed with different
keys, so a plain `adb install -r` fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.

### How many instances fit

Measured on the 16 GB / 12-core reference machine, per instance:

- **≈1.0–1.4 GB RSS** while actively driven (booted, app running, taps landing)
- **≈0.2–0.7 GB** once idle — macOS compresses and reclaims emulator pages
  aggressively, so `./emu.sh status` swings a lot between samples; the number to
  plan against is the driven one

Budget **1.5 GB per instance**. Leaving ≥4 GB free and ~3 GB for macOS plus the
agents' own processes, **6 instances is the arithmetic ceiling and 4 is the
number to actually run**. Booting two takes ~29 s wall-clock, including installs.

Check `./emu.sh status` and `vm_stat` before going past 4 — a gradle build or a
browser eats the headroom fast. If `PhysMem` already shows several GB in the
compressor, you are past the useful limit no matter what RSS says.

---

## Driving the app: `ui.py`

```
./ui.py <serial> <command> [args]
```

| command | notes |
|---|---|
| `dump` | compact semantics tree; raw XML written to `out/<serial>-dump.xml` |
| `find <regex>` | every match with its centre coords, flagged if untappable |
| `absent <regex>` | the inverse of `find`: exits 0 only when NOTHING matches, and lists the offenders when something does. A scenario asserting a control was *taken away* (the day's top-bar Dusk button, C-18) cannot use `find`, because a step that is meant to fail looks exactly like a broken one |
| `tap <regex>` | taps the centre of the best match — **refuses off-screen taps**, see below |
| `tapxy <x> <y>` | raw coordinate tap |
| `hold <regex> [ms]` | press-and-hold, default 800 ms |
| `swipe up\|down\|left\|right [px]` | scrolls within the safe area |
| `type <text>` | spaces handled; type into a field after tapping it |
| `back` | KEYCODE_BACK |
| `screenshot [file.png]` | with no argument, auto-numbers into `out/<serial>-N.png` |
| `wait <regex> [timeout]` | polls until the text appears (default 15 s); on timeout prints the tree |
| `insets` | status bar / nav bar / gesture / cutout insets and the safe area |
| `audit` | **the layout check — see below** |

Matching is a **case-insensitive regex** tested against each node's `text` and
`content-desc`. Anchor it when a label is a prefix of another: `tap "^Close$"`.
`tap` prefers a clickable node and then the smallest box, so `tap "Trouble
Brewing"` hits the row, not the list around it.

Exit status is 0 on success, 1 on failure (no match, `OFFSCREEN`, timeout), 2 on
a usage error — so `&&` chains do the right thing.

### Reading a dump

```
#10 '<View>' [63,1179][1017,1311] @(540,1245)  click
  #11 'New game (storyteller)' [357,1216][724,1274] @(540,1245)
```

- `#10` — node index within this dump (useful only for talking about the dump)
- `'…'` — the node's `text`, or its `content-desc` if it has no text, or
  `<ClassName>` when it has neither
- `[x1,y1][x2,y2]` — bounds in device pixels; the screen is 1080×2400
- `@(x,y)` — centre, i.e. exactly where `tap` would land
- flags — `click`, `long`, `scroll`, `CHECKED`, `sel`, `focus`, `DISABLED`

Indentation is the view hierarchy. Compose typically nests the label inside the
clickable box, as above: `#11` is what you match on, `#10` is what gets tapped.
Pure layout containers with no label and no interaction are hidden; the footer
line says how many nodes were elided and where the raw XML went.

`DISABLED` is worth attention — a button that should be live but dumps as
`DISABLED` is a rules bug, not a UI one.

**Player seats are addressable by name.** Each token carries a full
content-desc, so you never need coordinates for them:

```
#22 'Seat 1, Player 1, no character assigned, alive' [414,571][666,860] @(540,715)
```

```sh
./ui.py emulator-5554 tap  "^Seat 3,"     # open seat 3
./ui.py emulator-5554 hold "^Seat 3," 900 # long-press seat 3
```

The description updates with state (`alive`/`dead`, the assigned character), so
`find "Seat .*dead"` is a quick way to assert what the grimoire believes.

### `audit` — the safe-area and overlap check

```sh
./ui.py emulator-5554 audit
```

It dumps the tree, reads the real insets off the device, then reports every
clickable node that is

1. **outside the safe area** — under the status bar or display cutout at the top,
   or under the navigation bar / **home indicator** at the bottom, or off the
   sides; and
2. **partially overlapping another clickable node** — two hit targets fighting
   over the same pixels, where neither contains the other.

On the reference device the safe area is `y 136..2316`: 136 px of status
bar/cutout at the top, and 84 px of *mandatory system gesture* inset at the
bottom — which is bigger than the 63 px navigation bar, because the home
indicator swallows swipes in that strip. **A button whose bottom edge crosses
y=2316 is the bug this catches.** Sample output:

```
=== SAFE-AREA VIOLATIONS (1) ===
  #35 '<View>' [53,2229][1027,2355] @(540,2292)  click
      - bottom 39px under the navigation/gesture inset (home indicator)
```

Full-bleed backdrops are excluded from the clipping check — they are *supposed*
to run edge to edge. Two things count as one:

- a **scrim**: a clickable box drawn from the physical top-left corner across
  the full width of the display (the "tap outside to dismiss" surface of a sheet
  or dialog). It is exempt from the centre check too: its centre is wherever the
  sheet below it happened to leave room, and any point in it dismisses. Size is
  not the test — a sheet that finally *fits* leaves a thin strip of scrim, which
  the old ≥40 % rule read as a clipped control;
- anything covering **≥40 % of the screen**, which is still flagged if its own
  centre is untappable.

`audit` exits 1 when it finds anything, so it works as a gate; inside a scenario
a finding is recorded and the run continues.

`audit --xml <saved-dump.xml>` re-checks a dump you already captured, so you can
re-examine a screen without navigating back to it.

### `tap` refuses off-screen taps

This is the point of the harness. If the node you asked for has its centre
outside the safe area, `tap` does **not** tap — it reports `OFFSCREEN`, prints
the bounds and the inset table, and exits 1:

```
OFFSCREEN 'Deal & hand out tokens' centre=(540,2350) bounds=[32,2287][1048,2413]
          under the bottom navigation / gesture inset (y >= 2316)
```

That is a bug to file, not a reason to fall back to `tapxy`. Use `tapxy` only
for canvas gestures (dragging a token round the circle) where there is no node.

---

## Scenarios

A scenario is a Python file listing `(command, arg)` pairs:

```python
# tools/emu/scenarios/new_game.py
STEPS = [
    ("wait",       "New game"),
    ("tap",        "New game"),
    ("wait",       "TABLE"),
    ("tap",        "Trouble Brewing"),
    ("tap",        "Start empty"),
    ("audit",      None),
    ("sleep",      1.5),
]
```

`arg` is a string, `None`, or a list for multi-argument commands
(`("wait", ["TABLE", "30"])`). Run it:

```sh
./scenario.py emulator-5554 new_game
```

Every step is screenshotted into `out/new_game/<serial>/NN-<step>.png`. On the first
failure the run stops, writes the screenshot anyway, prints the current tree, and
exits 1. `audit` findings are collected and listed at the end instead of stopping
the run.

Use scenarios to get to the screen you want to explore, then drive by hand from
there.

---

## Environment

Defaults are correct on the reference machine; override by exporting:

| var | default |
|---|---|
| `ANDROID_SDK` | `/opt/homebrew/share/android-commandlinetools` |
| `EMU_AVD` | `grimoire` |
| `EMU_APK` | `app/build/outputs/apk/debug/app-debug.apk` |
| `EMU_OUT` | `tools/emu/out` (screenshots, raw dumps, emulator logs) |

`out/` is gitignored. Emulator boot logs land in `out/logs/<port>.log` — check
there first if `boot` times out.

### The update banner is off on emulators

An APK built with `-PbuildSha=<sha>` asks GitHub for the rolling `latest-apk`
release on first launch and, when the shas differ, shows a "New build available
(…)" banner **above the bottom action bar**. It steals ~126 px from every
screen, so a scenario written against a build without it misses buttons —
`tap "Start empty"` simply stopped finding anything (STATUS.md, HARNESS NOTE).

**A debug build no longer runs that check on an emulator.**
`UpdateManager.checkOnce()` returns immediately when `BuildConfig.DEBUG` and the
device looks like goldfish/ranchu (`Build.FINGERPRINT` / `Build.HARDWARE` /
`Build.MODEL` / `Build.PRODUCT`), so nothing is fetched and no banner is drawn —
whatever `-PbuildSha` says and whether or not the emulator has network. A real
phone and the web build are untouched, and a release APK always asks.

So all three of these are banner-free now, and the scenarios under `scenarios/`
assume it:

```sh
./gradlew :app:assembleDebug                       # BUILD_SHA=dev: no check at all
./gradlew :app:assembleDebug -PbuildSha=$(git rev-parse HEAD)   # …suppressed on emulators
adb -s emulator-5554 shell svc wifi disable        # …belt and braces: take it offline
adb -s emulator-5554 shell svc data disable
```

If you ever need to *see* the banner on an emulator, build a release APK or
temporarily drop the `BuildConfig.DEBUG && onEmulator` guard in
`app/src/main/java/com/clocktower/grimoire/UpdateBanner.kt`.

---

## Findings protocol

Everything you find goes in **`docs/audit/PLAYTEST-FINDINGS.md`**, one section
per agent, so the fix wave can read a single file.

Add your own section — do not edit anyone else's:

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

Every finding needs all six parts:

- **Severity** — `P0` crash, stuck state, or a wrong rule the storyteller would
  act on · `P1` a flow you cannot complete · `P2` cosmetic
- **Screen / flow** — where you were
- **Repro** — exact `ui.py` commands from a freshly launched app, so anyone can
  paste them and land on the same screen
- **Expected vs actual** — for rules bugs, cite the rule
- **Screenshot** — a path under `tools/emu/out/`
- **Suspect file** — your best guess at the engine or UI file, so the fix wave
  has a starting point

Rules of engagement:

- One section per agent, appended at the end of the file. Never rewrite another
  agent's section.
- Run `audit` on every new screen you reach; paste its output verbatim into P2
  layout findings.
- If `tap` says `OFFSCREEN`, that is a finding in itself — file it before
  working around it.
- Prefer a scenario over a long hand-typed sequence: the numbered screenshots
  are the evidence trail.
