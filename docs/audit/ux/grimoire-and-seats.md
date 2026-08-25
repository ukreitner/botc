# Grimoire screen & seat sheet on a phone (ux/grimoire-and-seats)

Scope: `GrimoireScreen.kt`, `SeatSheet.kt` (incl. `CharacterPicker` /
`ReminderPicker`), `components/Tokens.kt`, `components/Zoomable.kt`,
`components/PrivacyCover.kt`, `components/Timer.kt`, and the `GameShell.kt`
overlays that sit on top of the circle. Judged as *the storyteller's day-time
instrument*: a phone held in one hand, in a dim room, at 15 players.

Inputs taken as given from the character auditors (their rules content is NOT
repeated here; only the screen-design consequences are):

- seat sheet's "Died at night" hard-codes `DeathCause.DEMON` (`SeatSheet.kt:271`);
- generic "Dead" chip places a token without killing (`SeatSheet.kt:502`);
- the night tray's exclusive-vs-additive heuristic loses tokens (`NightScreen.kt:319-339`);
- reminders truncate to 2–4 per seat with "+N" (`GrimoireScreen.kt:359,476-491`);
- the Spy needs a read-only "show grimoire" mode (`docs/audit/characters/spy.md:160-180`);
- the seat sheet is the only kill path that surfaces protections (`SeatSheet.kt:240-307`).

---

## Official rules (sources)

The physical object this screen is imitating, and what it is *for*:

- **Grimoire** — "The box that stores the Clocktower pieces, held and updated by
  the Storyteller. **Players cannot look in the Grimoire.** The Grimoire shows
  the actual states of all the characters, such as who is alive or dead, who is
  poisoned, who is acting at night, etc."
  ([Glossary](https://wiki.bloodontheclocktower.com/Glossary))
- **Reminder token** — "The small tokens that help the Storyteller remember all
  sorts of things. Reminder tokens are specific to a certain character." The
  green **leaves** on a character token state how many reminder tokens it owns:
  "If there's one leaf at the top, add one reminder. If there are three at the
  top, add three." ([Glossary](https://wiki.bloodontheclocktower.com/Glossary),
  [Setup](https://wiki.bloodontheclocktower.com/Setup))
- **Suspending, not deleting, a token** — "If a player becomes drunk or
  poisoned, you can remove their reminder tokens. However, it is sometimes more
  helpful to **not remove them and instead turn them upside-down**, in case the
  player becomes sober and healthy again."
  ([Abilities](https://wiki.bloodontheclocktower.com/Abilities))
- **Shroud** — "The black and grey banner-shaped token used in the Grimoire to
  indicate that a player is dead."
  ([Glossary](https://wiki.bloodontheclocktower.com/Glossary))
- **Handling** — "Keep the Grimoire level when moving about. The high sides of
  the Grimoire should keep its contents hidden from the players' view…" and
  "Hold the Grimoire by the strong center pillar… This way, you can have a
  **free hand to move tokens around**."
  ([Storyteller Advice](https://wiki.bloodontheclocktower.com/Storyteller_Advice))
- **Bluffing with the Grimoire** — "you can move your hands around the Grimoire
  to make it look like you are putting the character's 'No ability' reminder
  token by the character token."
  ([Storyteller Advice](https://wiki.bloodontheclocktower.com/Storyteller_Advice))
- **Travellers** — "Traveller players may join the game at any time, and may
  leave the game at any time."
  ([Travellers](https://wiki.bloodontheclocktower.com/Travellers))

Three properties of the physical grimoire that the app is measured against here:

1. **All tokens are visible simultaneously.** A physical grimoire never hides a
   token behind a "+2". The storyteller's whole method is *pattern recognition
   across the open box*: green Poisoner token here, Monk's Safe there.
2. **Tokens are legible at reading distance without magnification**, because
   they are ~25 mm discs with ~7 pt text, held ~35 cm away.
3. **Placing/removing a token is one hand movement, not a menu walk.**

### The seven questions the day-phase grimoire must answer instantly

Simulated storyteller loop between two nominations, 15 players, Bad Moon Rising:

| # | Question | How often | Physical grimoire | This app today |
|---|---|---|---|---|
| Q1 | Who is poisoned / drunk right now? | every info call, every death | glance | green "!" badge, conflates poison & drunk, no source |
| Q2 | Who is protected, and against what? | every death | glance | must open each seat sheet |
| Q3 | Who is mad, about what? | every nomination | glance | token text at 6 sp, or open the seat |
| Q4 | Who is dead, of what, on what day? | constantly | shroud + memory | shroud only; cause is in a separate Log dialog |
| Q5 | Who has used a once-per-game ability? | nomination / night | "No ability" token | token text at 6 sp behind "+N" |
| Q6 | What did I tell the Empath / Chef / FT? | every day, all game | ST's own memory or notes | **not recorded anywhere** |
| Q7 | Who is on the block right now? | during the day | table state | **not shown on the grimoire at all** |

Q6 and Q7 have no answer in the current screen. Q1–Q5 have answers that require
either reading 6 sp text or a 2-tap sheet round-trip per seat.

---

## What the app does today

### Works (one line each, no further comment)

- Equal-**arc** seat distribution around an ellipse, so seats don't bunch at the
  flat top/bottom of a tall screen (`GrimoireScreen.kt:296-317`) — good, and the
  decorative ring shares the same ellipse (`GrimoireScreen.kt:125-146`).
- Evil names in ember red, good in parchment, dimmed when dead
  (`GrimoireScreen.kt:373-386`).
- Per-seat merged `contentDescription` that names character, shown identity,
  alignment, alive/dead, ghost vote, impairment and every reminder
  (`GrimoireScreen.kt:333-352`) — screen-reader users actually get *more* than
  sighted users.
- Standing counters line: alive / execution threshold / ghost votes
  (`GrimoireScreen.kt:165-173`).
- Wake-order badge on each seat during the night (`GrimoireScreen.kt:83-96,435-450`).
- Bluffs (left) and Fabled (right) as always-visible one-tap corners
  (`GrimoireScreen.kt:176-219`).
- Zoom state survives tab switches (`rememberZoomState` inside
  `SaveableStateProvider`, `GameShell.kt:299`).
- Seat sheet dismisses itself safely if the seat disappears under it
  (`SeatSheet.kt:66-71`).

### Seat rendering: measured geometry

`SeatGeometry.childMax` (`GrimoireScreen.kt:286-290`) picks the seat box from a
fixed divisor of `min(w,h)`; `CircleLayout` (`GrimoireScreen.kt:248-276`)
measures each seat with `Constraints(maxWidth = childMax, maxHeight = childMax*2)`
and places it **centred** on the ellipse. A seat's Column
(`GrimoireScreen.kt:361-493`) stacks: name (12 sp) → token
(74/62/56 dp, `:354-358`) → character name (2 lines, `:453-463`) → ghost-vote
line (dead only, `:464-470`) → reminder row (26/22 dp, `:471-492`).

Computed for an iPhone 15 Pro in portrait (393×852 dp; top bar 64, nav bar 80,
circle padding 30/12 ⇒ a 393×666 dp circle area), all values in dp:

| seats | arc spacing between neighbours | card height, alive, 1-line char name | + reminder row | dead + 2-line name | measure budget (`childMax*2`) |
|---|---|---|---|---|---|
| 7  | 185.3 | 109.7 | 137.7 | 167.4 | 225 |
| 10 | 136.7 | 109.7 | 137.7 | 167.4 | 179 |
| 12 | **113.9** | 109.7 | **137.7** | **167.4** | 179 |
| 13 | 109.1 | 95.5 | 119.5 | **147.0** | **146** |
| 15 | **94.5** | **95.5** | **119.5** | **147.0** | **146** |
| 18 | 78.8 | **88.4** | **112.4** | **138.7** | 146 |
| 20 | **70.9** | **88.4** | **112.4** | **138.7** | 146 |

Because the ellipse is roughly twice as tall as it is wide (`rx≈152`, `ry≈289`
at 15 seats), **most seats sit on the left/right flanks**, where consecutive
seats are separated almost purely vertically — so "arc spacing" *is* the
vertical gap for them. Two consequences, both visible in the table:

- **From 12 players up, a seat that has any reminder token is taller than the
  gap to its neighbour.** At 12 seats the overlap is 24 dp; at 20 seats a
  112 dp card sits in a 71 dp slot — a 41 dp overlap, i.e. one seat's reminder
  row is drawn on top of the next seat's name. Children are placed in list
  order, so later seats paint over earlier ones.
- **At 13–16 seats a dead player's card (147.0 dp) exceeds the measure budget
  (146 dp).** Compose's `Column` measures children with `maxHeight = remaining`,
  so the *last* child — the reminder row — is measured with `maxHeight = 0`;
  `ReminderToken`'s `Modifier.size(size)` is clamped by the incoming
  constraints, so the tokens collapse to zero height. The Column is also
  `.clip(RoundedCornerShape(12.dp))` (`GrimoireScreen.kt:365`), so anything that
  does overflow is cut. **The tokens silently vanish** — there is no "+N", no
  ellipsis, nothing.

### Reminder token legibility

`ReminderToken` (`Tokens.kt:154-180`) sets
`fontSize = max(size/5, 6).sp`, `maxLines = 2`, 2 dp padding.

| where | size | font size | usable inner width | chars that fit (2 lines) |
|---|---|---|---|---|
| circle, ≤12 seats (`GrimoireScreen.kt:360`) | 26 dp | **6.0 sp** | ~20 dp | ~10 |
| circle, 13+ seats (`GrimoireScreen.kt:360`) | 22 dp | **6.0 sp** (floor) | ~16 dp | ~8 |
| seat sheet (`SeatSheet.kt:343`) | 44 dp | 8.8 sp | ~38 dp | ~16 |
| reminder picker (`SeatSheet.kt:530`) | 48 dp | 9.6 sp | ~42 dp | ~18 |

6 sp is roughly half Material's smallest text style (`labelSmall` = 11 sp) and
about a third of the 16 sp body minimum. Against the real label corpus in
`characters.json` (91 distinct labels, 184 placements, mean length 7.5 chars,
**42 of the 91 distinct labels longer than 8 characters, 31 longer than 10**,
longest `"Final Night: No Attack"` = 22), the
circle's tokens are ellipsised noise. Concretely, at 15 players `"Survives
execution"` renders as `Surv…` in 6 sp white-on-red inside a 22 dp disc.

Zoom does not rescue this: `zoomTransform` is a `graphicsLayer` scale
(`Zoomable.kt:71-76`) — it magnifies an already-rasterised layout rather than
re-laying it out, and is capped at 2.5× (`Zoomable.kt:37`), giving an effective
15 sp of blurred text and *the same overlap*, now magnified.

### Colour coding

`ReminderToken`'s colour is the **source character's team colour**
(`GrimoireScreen.kt:479`, `SeatSheet.kt:342`, `SeatSheet.kt:563`), and generic
tokens are hard-coded `BloodRed` (`SeatSheet.kt:530`, and the `?: BloodRed`
fallback at `GrimoireScreen.kt:480`). Team palette at `theme/Theme.kt:27-42`.

The result is that colour encodes **provenance, not effect**:

| token | source | rendered colour | what the eye reads |
|---|---|---|---|
| Monk "Safe" | Townsfolk | `#4E8FD9` blue | same as… |
| Empath "Know" | Townsfolk | `#4E8FD9` blue | …a pure-bookkeeping mark |
| Butler "Master" | Outsider | `#3FB8AE` teal | — |
| Poisoner "Poisoned" | Minion | `#D97B4E` orange | — |
| **generic "Protected"** | — | `#9C2B2B` **BloodRed** | ≈ `#C93B3B` DemonRed → reads as a Demon token |
| **generic "Poisoned"** | — | `#9C2B2B` BloodRed | identical to generic "Protected" |

So the two most safety-critical statuses in the game — *poisoned* and
*protected* — are the **same colour** when placed generically, and *protected*
is the same colour as *you told them something* when placed from a Townsfolk.
There is no colour, glyph, or ordering signal for effect kind anywhere.

### Overflow ordering

`player.reminders.takeLast(visibleReminders)` (`GrimoireScreen.kt:476`) with
`visibleReminders` = 4 (≤12 seats) or **2** (13+ seats) (`:359`). Reminders are
appended in placement order (`GameActions.kt:186-187`), so **the newest win and
the oldest are hidden**. The oldest tokens are exactly the long-lived ones:
`"Is the Drunk"` (placed at setup, `GameShell.kt:398-401`), `"Red herring"`
(`GameShell.kt:365-368`), `"Is The Marionette"`, `"No ability"`. At 15 players
a Drunk who is also Poisoned and has a Chambermaid "Know" and a Butler "Master"
shows **two** tokens and `+2`; the `+2` is a non-tappable `Text`
(`GrimoireScreen.kt:484-490`).

### Two different meanings for placing the same token

| path | code | semantics |
|---|---|---|
| seat sheet → Add reminder | `SeatSheet.kt:109-117` → `GameViewModel.kt:212` → `GameActions.addReminder` (`GameActions.kt:186`) | **always appends** — a second "Poisoned" simply piles up |
| night tray → chip → seat | `NightScreen.kt:316-341` | copy-counting heuristic: ≤1 copy ⇒ `placeExclusiveReminder` (moves); >1 ⇒ append or evict `placed.first()` |

The *same* Poisoner "Poisoned" token therefore moves or duplicates depending on
which screen the storyteller happened to be on. Nothing in either UI says which
is happening.

### Death, protection and attribution

`SeatSheet.kt:240-307`:

- `StatusEffects.deathNotes(...)` is computed **twice** — once for the red "!"
  wall printed on every open (`:241-251`) and again to build `protectionNotes`
  (`:256-265`).
- `protectionNotes` is a **keyword grep** over the prose notes:
  `listOf("can't die","can not die","Safe","Protected","survives","safe from","don't","Fool")`
  (`:259`). It is **cause-blind**: executing a Soldier pops
  *"Barry might be protected — The Soldier is safe from the Demon."*
  Training a storyteller to dismiss the one dialog that matters.
- The dismiss button reads **"Death prevented"** (`:304`) but does nothing at
  all: no death record, no log line, no token spent, no note. The next time the
  Monk's protection is queried the app has no idea it was used.
- Three buttons cover five causes (`GameState.kt:75`):
  `DEMON`("Died at night"), `EXECUTION`, `STORYTELLER`("Other death").
  `OTHER_NIGHT_DEATH` and `EXILE` are unreachable from a seat — the only
  producers are `GameActions.kt:87` (star pass) and `DayScreen.kt:354` (exile).
- `DeathRecord` (`GameState.kt:78-90`) has no `killerId` field, so "who killed
  whom" is unrecorded everywhere in the app.

### Tap counts today (phone, 15 players, day phase)

| action | taps | notes |
|---|---|---|
| answer "who is poisoned?" | 0 taps, but reading 15 × 16 dp badges that conflate drunk/poison/No-Dashii | `GrimoireScreen.kt:421-434` |
| answer "who is protected?" | **30** (open + close 15 sheets) | protection is sheet-only |
| answer "what did I tell the Empath?" | ∞ | not recorded |
| place a reminder (from the circle) | **3** + 2 scrolls | seat → *Add reminder* → hunt the character row; picker has no search (`SeatSheet.kt:492-571`) |
| place a reminder (night tray) | 2 | but only for the currently expanded night step |
| remove a reminder | **2** | `SeatSheet.kt:330-338`; no confirm, no undo in reach |
| move a token from seat A to B | **5** | remove, close, open B, add, find |
| kill (night death) | 1 (+1 if the protection dialog fires) | wrong cause baked in |
| change a character mid-game | **3** + scrolling ~25 unsearchable rows | `SeatSheet.kt:388-453` |
| rename a player | 1 + keyboard + **1 (Save & close)** | silently discarded if dismissed otherwise |
| write a note on a player | 1 + scroll + keyboard + **1 (Save & close)** | same discard hazard |
| reorder one seat by *k* places | 2 (menu) + **k** arrow taps + 1 | `GameExtras.kt:110-140`, `moveSeat` is ±1 |
| **add a traveller mid-game** | 2 (menu) + name + 1 (Add) + 2 (menu → Reorder) + **k** arrows (up to 7) + 1 (Done) + 3 (seat → Change character → scroll to the Travellers group at the bottom) ≈ **9 + k ≈ 16** | `GameShell.kt:254-257,663-684`; `addSeat` appends |
| undo a mistake made in a sheet | **3** (dismiss sheet → undo → reopen) | see below |

### Undo

`viewModel.update` pushes every non-identical state onto a 100-deep stack
(`GameViewModel.kt:101-109,265`) — mechanically sound. But:

- Undo/redo live **only** in the top app bar (`GameShell.kt:189-194`). Every
  `ModalBottomSheet` and `AlertDialog` renders in its own window with a scrim
  over that bar, so **while a seat sheet is open, undo is physically
  unreachable**, and dismissing the sheet to reach it is itself a gesture.
- There is no snackbar, no toast, and no label: the undo button cannot tell you
  *what* it will undo. Undoing after a phase change silently un-expires every
  dawn/dusk token (`GameActions.kt:218-243`).
- Destructive taps (remove a reminder, `SeatSheet.kt:330-338`) have no confirm
  and no per-item restore.

### Privacy cover

`PrivacyCover.kt:33-71`: full-screen black, 1.2 s hold to unlock (`:47`),
engaged from the top bar (`GameShell.kt:186-188`) and automatically after the
reveal flow (`:336-342`). Two structural problems:

- It is composed at `GameShell.kt:344-346`, **before** the mastermind banner
  (`:520-537`), the win-advisory dialog (`:509`), the seat sheet (`:481`), the
  bluffs sheet (`:489`), the log (`:500`) and the setup/dusk/night dialogs
  (`:551-659`). Sibling composables draw in declaration order and dialogs get
  their own window, so **anything declared after line 346 paints on top of the
  cover** — the "MASTERMIND DAY — whoever is executed, their team loses" banner
  literally renders over the closed grimoire, and any dialog open at lock time
  stays visible.
- The 1.2 s hold has no progress feedback beyond a text swap (`:64`), so a
  storyteller in a hurry lifts early and thinks it is broken.

### Zoom / pan at 15–20 seats

`Zoomable.kt`: `detectTransformGestures` feeds `zoomBy`/`panBy` (`:63-68`);
`graphicsLayer` applies `scale` about the **layer centre** and an unbounded
translation (`:71-76`); scale clamped to 0.6…2.5 (`:37`); pan unbounded (`:40-43`).

- **Zoom is centre-anchored, not centroid-anchored** — pinching on the top seat
  drags it away from your fingers toward the centre.
- **A one-finger drag pans the whole circle** (pan is applied unconditionally),
  so a slightly-slipped tap on a seat scrolls the grimoire instead.
- **Pan is unbounded**: the circle can be flung entirely off-screen; recovery is
  the reset button, which is only rendered when `!isDefault` (`:97-107`) — it
  does appear, but it is a 44 dp button at the bottom-left, under the bottom-left
  seat's footprint.
- Zoom does **not** re-layout, so it cannot fix the overlap or the 6 sp text; it
  magnifies both.

### Timer

`DiscussionTimer` (`Timer.kt:39-107`) holds `endAt` in a `rememberSaveable`
inside itself, and is mounted **conditionally** at `GameShell.kt:315-321` (only
on the Grimoire and Day tabs, outside the `SaveableStateHolder` used for tabs at
`:299`). Switching to the Night or Script tab removes it from composition and
**discards the running deadline** — the classic "I checked the night sheet for
five seconds and lost the 2-minute timer". Expanded, it is a wide `Surface` at
`BottomEnd` (`:316-320`) sitting on top of the bottom seat of the circle. There
is no audible or haptic alert.

### Seat sheet: too much and too little

Order of content in `SeatActions` (`SeatSheet.kt:170-383`), on a sheet that
opens at **half height** (`ModalBottomSheet` default partial expansion,
`SeatSheet.kt:75`):

| position | content | verdict |
|---|---|---|
| 1 | token + name + team + dead flag (`:179-195`) | keep |
| 2 | full ability text (`:196-198`) | already on the Script tab; pushes everything down |
| 3 | shown-identity card (`:199-221`) | keep |
| 4 | every active jinx for this character (`:222-235`) | setup/night material, not day material |
| 5 | red "!" wall of *all* death notes (`:240-251`) | fires on every open, for seats you never intended to kill |
| 6 | kill / revive row (`:269-287`) | keep, but see causes above |
| 7 | 6 equal-weight buttons: Change character, Set shown identity, **Add reminder**, Flip alignment, Swap characters, Remove seat (`:309-322`) | the single most-used action has the same weight as *Flip alignment*; wraps to 3–4 rows at 393 dp |
| 8 | reminder tokens, tap-to-remove (`:324-355`) | usually **below the fold** |
| 9 | name field, notes field, **Save & close** (`:359-382`) | manual save in an otherwise live sheet |

Missing entirely: seat number and neighbours (needed constantly for
Empath/Chambermaid/No Dashii adjacency), the death record (cause + day — the
header only appends `" · dead"`, `:189`), token expiry ("this expires at dusk"),
what the player was shown at reveal, their nomination/vote history, whether they
are on the block, and any per-player information history.

### Notes and history

`Player.note` is a single free-text `String` (`GameState.kt:31`), edited through
one `OutlinedTextField` with manual save (`SeatSheet.kt:366-372`). It is
**overwritten wholesale** by the setup prompts — `GameShell.kt:403-407,430-434,
470-474` call `GameActions.setNote(...)` with `"Believes they are the X"`,
destroying anything already typed. There is no timestamp, no append, no
structure, and the note is invisible on the circle.

There is **no** record of information given to a player. Note that the
*player-facing* notes mode already has exactly this concept —
`Notes.kt:77` `val infoLog: List<NoteInfo>`, appended at `Notes.kt:211` — so the
storyteller side is the only one without it. The Game Log
(`GameExtras.kt:46-106`) derives entries from deaths and nominations only.

### Read-only / Spy mode

Does not exist. The only "hide" primitive is the binary `PrivacyCover`. Handing
the phone to the Spy today means handing over Undo, Redo, "Declare evil
victory", the demon bluffs, the storyteller notes and one-tap character
reassignment on every seat.

---

## Defects and gaps

**P0 — the storyteller is misled or the grimoire is unreadable**

1. **P0 · Seat cards overlap their neighbours from 12 players up.**
   A seat with any reminder token is 137.7 dp tall at 12 seats where the gap is
   113.9 dp, and 112.4 dp at 20 seats where the gap is 70.9 dp; seats are placed
   centred with no collision handling, later seats painting over earlier ones.
   `GrimoireScreen.kt:248-276,286-290,354-360`.
   *Repro:* 15-player game, place one token on every seat, look at the left
   flank of the circle — each seat's token row sits on the next seat's name.

2. **P0 · At 13–16 seats a dead player's reminder row is measured to zero and
   disappears silently.** Card height 147.0 dp vs a `childMax*2` = 146 dp
   budget; `Column` gives the last child the leftover (0 dp) and
   `ReminderToken`'s `Modifier.size` is clamped to it; the Column is clipped
   anyway (`GrimoireScreen.kt:257,365,471-492`).
   *Repro:* 15 players, kill someone whose character name wraps to two lines
   ("Devil's Advocate"), place a token on them — the token is not drawn and no
   "+N" appears.

3. **P0 · Reminder text on the circle renders at 6 sp.**
   `Tokens.kt:170` — `max(size/5, 6).sp` with size 22–26 dp. 42 of the 91
   distinct labels are longer than 8 characters and are ellipsised inside a
   22 dp disc. The storyteller cannot read their own grimoire without opening a
   sheet. `GrimoireScreen.kt:360`, `Tokens.kt:154-180`.

4. **P0 · Colour encodes the source's team, not the effect.**
   `GrimoireScreen.kt:479-481`, `SeatSheet.kt:342,530`, `theme/Theme.kt:27-42`.
   Generic "Poisoned" and generic "Protected" are the *same* `BloodRed`, which
   is also within 12% of `DemonRed`; Monk "Safe" and Empath "Know" are the same
   blue. The two statuses that decide whether a player lives are visually
   indistinguishable.

5. **P0 · The overflow hides the wrong tokens.**
   `takeLast(2)` at 13+ seats (`GrimoireScreen.kt:359,476`) shows the newest and
   hides the standing ones — "Is the Drunk", "Red herring", "No ability" — which
   are precisely the tokens a storyteller must never forget. The `+N` is inert
   text (`:484-490`).

6. **P0 · The same token behaves differently depending on which screen placed it.**
   Seat sheet always appends (`SeatSheet.kt:113` → `GameActions.kt:186`); the
   night tray moves-or-evicts (`NightScreen.kt:319-339`). Nothing indicates
   which. *Repro:* place Poisoner "Poisoned" on Ana from the night tray, then on
   Ben from Ben's seat sheet → two live "Poisoned" tokens.

7. **P0 · The protection confirmation is cause-blind and its "Death prevented"
   branch records nothing.** `SeatSheet.kt:256-268,288-307`. Executing a Soldier
   raises a Demon-protection warning; choosing "Death prevented" leaves zero
   trace in state, the log, or the tokens. *Repro:* Soldier alive, open their
   seat, tap "Executed" → irrelevant warning; tap "Death prevented" → nothing
   happened anywhere.

8. **P0 · The privacy cover is not the top-most layer.**
   `GameShell.kt:344-346` is declared before the mastermind banner (`:520-537`),
   the seat sheet (`:481`), and every dialog (`:509,551,592,618`). *Repro:*
   trigger the Mastermind day, then tap "Hide the grimoire" — the banner is
   drawn on the black cover. Any dialog open at lock time stays on screen.

**P1 — bookkeeping the app could do but makes the storyteller do**

9. **P1 · No record of what any player was told.** Q6 above. The engine already
   models this on the player side (`Notes.kt:77,211`) but not on the storyteller
   side; `GameExtras.kt:46-106` logs only deaths and nominations.

10. **P1 · Undo is unreachable exactly when it is needed.** The bar is behind
    the sheet scrim (`GameShell.kt:189-194` vs `SeatSheet.kt:75`), there is no
    snackbar undo, and the button cannot say what it will revert
    (`GameViewModel.kt:101-123`).

11. **P1 · Name and Seat-notes are lost silently.** `SeatSheet.kt:359-382` —
    typed text is committed only by "Save & close"; swipe-down, scrim tap, or
    Back discards it. Every other control in the same sheet commits instantly,
    so the model is inconsistent as well as lossy.

12. **P1 · Setup prompts overwrite the seat note.**
    `GameShell.kt:403-407,430-434,470-474` call `setNote` with a fixed string,
    wiping anything the storyteller typed earlier.

13. **P1 · Adding a traveller costs ~16 taps and lands them in the wrong seat.**
    `GameShell.kt:254-257,663-684` → `GameViewModel.kt:196` →
    `GameActions.addSeat(state, name)` appends, although `addSeat` **already
    supports `afterId`** (`GameActions.kt:19-26`) and no caller passes it. Then
    `ReorderSeatsDialog` moves ±1 per tap (`GameExtras.kt:110-140`), and the
    traveller character is at the bottom of an unsearchable picker
    (`SeatSheet.kt:439-451`).

14. **P1 · Kill UI cannot express the cause or the killer.** Three buttons, five
    causes, no `killerId` in `DeathRecord` (`SeatSheet.kt:269-287`,
    `GameState.kt:75-90`). `OTHER_NIGHT_DEATH` and `EXILE` are unreachable from
    a seat.

15. **P1 · No list/board view and no filters.** Answering "who is poisoned /
    protected / mad / has used their ability" requires 15 sheet round-trips
    (30 taps). There is no view in the app that shows every seat's full token
    set at once.

16. **P1 · The seat sheet omits the facts the storyteller asks for most.**
    No seat number, no neighbours, no death cause/day, no token expiry, no
    on-the-block flag (`SeatSheet.kt:179-195`).

17. **P1 · The discussion timer dies on tab switch.** `Timer.kt:41-43` inside
    the conditional mount at `GameShell.kt:315-321`, outside the tab
    `SaveableStateHolder` (`:299`).

18. **P1 · Neither picker has a search box, although one in the same codebase
    does.** `CharacterPicker` (`SeatSheet.kt:388-453`) and `ReminderPicker`
    (`SeatSheet.kt:492-571`) are plain lists; `ShowToolSheet` has search +
    in-play-first sorting (`components/ShowCards.kt:269-282`).

19. **P1 · No read-only grimoire mode.** Required by
    `docs/audit/characters/spy.md`; the only primitive is the all-or-nothing
    `PrivacyCover`.

**P2 — missing convenience / clarity**

20. **P2 · Zoom is centre-anchored, pan is unbounded, one-finger drag pans, no
    double-tap-to-fit, capped at 2.5×, and does not re-layout.**
    `Zoomable.kt:36-76`.

21. **P2 · Priority inversion in the seat sheet's action row.** "Add reminder"
    sits between "Set shown identity" and "Flip alignment"
    (`SeatSheet.kt:309-322`); the reminder list itself is below the fold at half
    sheet height.

22. **P2 · `deathNotes` is computed twice per open and printed as a red wall
    regardless of intent.** `SeatSheet.kt:241,257`.

23. **P2 · Tokens cannot be dragged between seats and cannot be placed on
    several seats at once.** Moving one token is 5 taps; Minstrel-style
    "everyone is drunk" is 15 × 3 taps.

24. **P2 · The grimoire never shows who is on the block, or that a Mastermind
    day is running, or the nomination state.** `GameActions.aboutToDie` exists
    (`GameActions.kt:296`) and is used only by `DayScreen.kt:74` and
    `GameShell.kt:142,593`.

25. **P2 · Tap targets below the 48 dp guideline**: reminder tokens in the sheet
    44 dp (`SeatSheet.kt:343`), zoom buttons 44 dp (`Zoomable.kt:86,92,100`),
    timer 44 dp (`Timer.kt:55`), traveller/impaired/wake badges 16–18 dp and not
    individually tappable (`GrimoireScreen.kt:409-450`).

26. **P2 · The privacy hold gives no progress feedback.** `PrivacyCover.kt:47,64`.

**P3 — polish**

27. **P3 · Player names ellipsise from 13 seats** (`childMax` = 72.8 dp ⇒ ~10
    characters at 12 sp; `GrimoireScreen.kt:384-385`).

28. **P3 · Bottom-corner controls sit on the bottom seats.** `ZoomControls` at
    `BottomStart` is 3 × 44 dp + gaps ≈ 144 dp tall (`GrimoireScreen.kt:221-226`);
    the timer at `BottomEnd` (`GameShell.kt:316-320`).

29. **P3 · Label casing is inconsistent in the data** — `"No ability"` ×10 vs
    `"No Ability"` ×8, `"Has ability"` vs `"Has Ability"` — so any effect-kind
    classifier must be case-insensitive (`characters.json`; cf. exact-pair
    matching at `GameActions.kt:218-243`).

30. **P3 · The wake badge is hidden for dead seats** (`GrimoireScreen.kt:435`),
    although several characters act while dead.

---

## Proposed behaviour (spec)

The organising idea: **the circle is for recognition, the board is for reading,
the sheet is for acting.** Stop trying to render token *text* on the circle at
15 players — render *status*, and put the text where there is room for it.

### 1. Effect kinds — one classifier, used everywhere

New engine file `engine/.../ReminderKind.kt`:

```kotlin
enum class ReminderKind(val glyph: String, val priority: Int) {
    PENDING_DEATH("†", 0),   // Dead, Died today, About To Die, Attack 1-3, 3 attacks, Lunch, Faux Paw
    IMPAIRED     ("!", 1),   // Poisoned, Drunk, Drunk 1-3, Everyone is drunk, False Info
    PROTECTED    ("+", 2),   // Safe, Protected, Protect, Can not die, Survives execution,
                             // Doesn't Kill, Sober & Healthy, No extra evil
    MADNESS      ("M", 3),   // Mad, 2nd, Claimed, Fear, Master, May Not Nominate,
                             // Nominate good/evil, Negative vote, 3 votes
    IDENTITY     ("=", 4),   // Is the Drunk, Is The Marionette, Is the Philosopher,
                             // Is The Alchemist, Is the Apprentice, Is The Demon, Twin, Turns Evil
    ABILITY      ("O", 5),   // No ability, Has ability, Used, Guess Used, Once,
                             // Ability twice, Storyteller Ability
    INFO         ("i", 6),   // Know, Wrong, Correct, Red herring, Townsfolk, Outsider,
                             // Minion, Demon, Grandchild, Chosen, Friend, Amigo, Visitor
    MARKER       ("·", 7),   // 1/2/3, Night 1-3, Day 1-5, X, Evil Wakes, Tea Party Tonight,
                             // Haircuts tonight, Final Night: No Attack, Something Bad, Mistake, Alive
}

object ReminderKinds {
    /** Exact (sourceId,label) overrides first, then a case-insensitive label table,
     *  then a substring heuristic, then MARKER. */
    fun of(r: PlacedReminder): ReminderKind
}
```

Colours (new, in `theme/Theme.kt`, all ≥ 4.5:1 on `NightSky` and mutually
distinguishable under deuteranopia because each also carries a glyph):

| kind | colour | name |
|---|---|---|
| PENDING_DEATH | `#C93B3B` | DemonRed (reuse) |
| IMPAIRED | `#6FA84E` | PoisonGreen |
| PROTECTED | `#4E8FD9` | ShieldBlue |
| MADNESS | `#A46FD1` | MadnessViolet |
| IDENTITY | `#E0B84F` | IdentityGold |
| ABILITY | `#8A8296` | SpentGrey |
| INFO | `#3FB8AE` | InfoTeal |
| MARKER | `#6B6478` | MarkerGrey |

Provenance is preserved as a **2 dp ring in the source's team colour** around
the pip, not as the fill. Rule: *fill = what it does, ring = who put it there.*

`ReminderToken` gains `kind` and renders label text **only at ≥ 36 dp**; below
that it renders the kind glyph at `max(size/2.2, 11).sp`. Hard floor: no text
in this app is ever rendered below 11 sp (`Tokens.kt:170` must change).

### 2. The circle: status pips, not micro-text

```
        Dana                       ← 12sp bold, ember red if evil
       ╭──────╮
   ⑦  │  FT  │                     ← wake badge (also for dead-but-acts seats)
       ╰──────╯
    ● ● ●  +2                       ← status pips, 16dp, glyph ≥11sp, priority order
      Fortune Teller                ← only when the height budget allows

   dead seat:
        Ben                         ← dimmed
       ╭──────╮
       │▓▓▓▓▓▓│  ← shroud
       │  CH  │
       ╰──────╯
    ● ●   ghost                     ← "ghost"/"no vote" merges into the pip row
```

Rules:

- Pips are ordered by `ReminderKind.priority`, never by placement order; the
  `+N` shows only lower-priority overflow and is **tappable** → opens the seat's
  token list.
- Pip = 16 dp disc, kind colour fill, team-colour ring, glyph in white ≥ 11 sp.
  Up to 5 pips at ≤12 seats, 4 above.
- The existing green "!" impaired badge (`GrimoireScreen.kt:421-434`) is
  **replaced** by the IMPAIRED pip, so there is one impairment signal, not two,
  and derived poison (No Dashii, `StatusEffects.derivedPoison`) produces a pip
  with a dotted ring to mark "derived, no physical token".
- Long-press a seat → **token peek**: a popover listing every token in full text
  with its source and expiry, plus per-token "remove"; no sheet, no scroll.
  This is the one-hand "move a token" gesture the physical grimoire has.
- Drag a pip onto another seat → move that token (with a "moved Poisoned from
  Ana to Ben" snackbar). Long-press-drag from a seat's pip row is the whole
  interaction; no picker involved.
- **Suspend, don't delete.** The physical convention is to turn a token
  upside-down when its owner goes drunk/poisoned rather than remove it
  ([Abilities](https://wiki.bloodontheclocktower.com/Abilities)). The token peek
  offers `Suspend` alongside `Remove`; a suspended pip renders hollow (kind
  colour as a ring only, glyph dimmed) and is excluded from `isImpaired`-style
  queries but survives until explicitly restored. This is what a storyteller
  needs when a Chambermaid's "Know" mark must be kept but must not count.

### 3. Seat geometry that cannot overlap or clip

Replace `SeatGeometry.childMax` (`GrimoireScreen.kt:286-290`) with a
spacing-driven allocator:

```
rx, ry, angles   as today (equal arc length — keep)
spacing          = arcLength(rx, ry) / n
budgetH          = min(spacing * 0.96, min(w,h) / 2.2)      // never overlap
budgetW          = min(childMax(w), spacing * 1.7)          // flanks are vertical
tokenSize        = (budgetH - nameH(16) - pipRowH(18) - 4).coerceIn(40.dp, 96.dp)
showCharacterName= budgetH - (16 + tokenSize + 18 + 4) >= 14.dp
```

and measure the seat with an explicit `Layout` that measures the **pip row
first** so it can never be squeezed to zero; the character-name line is the only
optional child. Add `Modifier.heightIn(max = budgetH)` and drop the `.clip` on
the seat Column so nothing is silently cut.

Numbers this yields on the 393×666 dp circle:

| seats | spacing | budgetH | tokenSize | character name shown |
|---|---|---|---|---|
| 7  | 185 | 178 | 96 (cap) | yes (2 lines) |
| 12 | 114 | 109 | 71 | yes (1 line) |
| 15 | 94  | 91  | 53 | no |
| 20 | 71  | 68  | 40 (floor) | no |

At 20 seats the floor is hit; the circle then shows tokens + pips + names only,
and the app offers a one-tap switch: *"20 seats — the Board reads better."*

### 4. Board view (the at-a-glance status board)

A segmented control in the grimoire's top area — **Circle | Board** — sharing
one selection/filter state. `LazyColumn`, one 56–72 dp row per seat, no
truncation of anything.

```
┌────────────────────────────────────────────────────────────┐
│ Day 3 · 7 alive · 4 to execute · 2 ghost votes · Ben ⚑block │
│ [ Circle │ ▓Board▓ ]        ⌕   [!poison][+safe][M mad][O]  │
├────────────────────────────────────────────────────────────┤
│ 1 (EM) Ana            Empath                          alive │
│        ! Poisoned · Poisoner · N3 → dusk                    │
│        i Know 0  ·  told "0" N3  (false — poisoned)         │
├────────────────────────────────────────────────────────────┤
│ 2 (CH) Ben  ⚑        Chef                             alive │
│        i told "1" N1 (true)                                 │
├────────────────────────────────────────────────────────────┤
│ 3 (FT) Cara           Fortune Teller                  alive │
│        + Safe · Monk · tonight     i Red herring (setup)    │
├────────────────────────────────────────────────────────────┤
│ 4 (··) Dana †         Ravenkeeper      killed N2 by Pukka   │
│        O No ability · used N2                               │
├────────────────────────────────────────────────────────────┤
│ 5 (SC) Eli            Scarlet Woman  ·evil·           alive │
│        = shown as Chambermaid    note: "claimed Chambermaid"│
└────────────────────────────────────────────────────────────┘
```

- Row = seat number, character token 32 dp, name (ember red if evil), character
  name, life state with **cause and day** for the dead.
- Second line = every token in **full text**, kind-coloured, with source and
  expiry, plus the most recent "told" event and the note preview.
- Filter chips at the top toggle a highlight that applies to **both** views —
  one tap answers Q1/Q2/Q3/Q5. A filter chip shows its count
  (`! poison 2`), so "who is poisoned" is answered before you even tap.
- Search box filters by player name, character name, or token label.
- Tap a row → the same seat sheet. Long-press → the same token peek.
- Sort: seat order (default) · alive-first · by kind. Never re-sorts under the
  finger while a filter is active.

This is also the surface the Spy view reuses (§8).

### 5. Seat sheet v2

Full-height sheet (`skipPartiallyExpanded = true`), fixed header, scrolling
body, **sticky action bar**:

```
╭──────────────────────────────────── seat 7 of 15 ─╮
│ (token) Dana                                alive │
│         Fortune Teller · Townsfolk                │
│         between Cara (6) and Eli (8)     ⚑ block  │
├───────────────────────────────────────────────────┤
│ STATUS                                            │
│  ! Poisoned    Poisoner · placed N3 · expires dusk│
│  i Red herring Fortune Teller · placed at setup   │
│  + Safe        Monk · expires at dawn             │
│                                        [+ token]  │
├───────────────────────────────────────────────────┤
│ HISTORY                                           │
│  setup  shown: Fortune Teller (true)              │
│  N1     told "No"  (Ana, Ben)              true   │
│  D2     nominated Ben — 3 votes, safe             │
│  N3     told "Yes" (Cara, Dana)      FALSE (pois) │
│  D3     note: "claims FT, sat by Ben all game"    │
│                                         [+ note]  │
├───────────────────────────────────────────────────┤
│ Name  [ Dana                    ]  (saves on blur)│
│ ▸ Advanced  (shown identity · flip alignment ·    │
│              swap characters · remove seat)       │
╰───────────────────────────────────────────────────╯
│  [ Kill… ]   [ + Token ]   [ Change… ]      ⤺ Undo│
╰───────────────────────────────────────────────────╯
```

- **Sticky bar**: the three actions that make up ~90% of day usage, plus an
  in-sheet **Undo** that works while the sheet is open (fixes P1-10 without
  fighting the scrim).
- Ability text and jinxes move behind a collapsed "▸ About this character".
- The red death-note wall moves into the Kill sheet (§6) — the seat sheet shows
  only *standing* facts.
- Name/notes **auto-commit on blur and on dismiss** (`onDismiss` flushes) —
  "Save & close" becomes just "Close" (fixes P1-11). Notes become an append-only
  list of dated entries so setup prompts can add without destroying (fixes
  P1-12): `Player.note: String` → `Player.notes: List<SeatNote(cycle, phase, text)>`,
  with a migration that wraps any existing string as one entry.
- Header carries the facts from P1-16: seat index, both neighbours, block flag,
  and for the dead `"killed N2 by Pukka"` / `"executed D3"`.

### 6. One kill path, cause-aware, with attribution

A shared `KillSheet` used by **every** kill site — seat sheet
(`SeatSheet.kt:266-287`), day execution (`DayScreen.kt:112,354`), demon kill
panel (`NightScreen.kt:629`) and the dusk guard (`GameShell.kt:601`) — so
protections and attribution can never be skipped by taking a different route.

```
╭─ Dana dies ───────────────────────────────────────╮
│ Cause                                             │
│  ( ) Demon attack        by [ Imp — Fred      ▾ ] │
│  ( ) Other night death   by [ Pukka — Gil     ▾ ] │
│  (•) Execution           day 3                    │
│  ( ) Exile               (travellers only)        │
│  ( ) Storyteller         why [                  ] │
├───────────────────────────────────────────────────┤
│ Protections that apply to an EXECUTION:           │
│   + Devil's Advocate — survives execution today   │
│ Not relevant to this cause:                       │
│   + Safe (Monk) — only blocks a Demon attack      │
├───────────────────────────────────────────────────┤
│ On this death:  Ravenkeeper wakes to learn a      │
│ character · Godfather kills tonight               │
├───────────────────────────────────────────────────┤
│ [ Record the death ]     [ Saved by the DA ]      │
╰───────────────────────────────────────────────────╯
```

- `StatusEffects.deathNotes` gains a `cause: DeathCause` parameter and returns
  structured notes: `DeathNote(kind = PROTECTION|TRIGGER, appliesTo: Set<DeathCause>, text, sourceId)`.
  The keyword grep at `SeatSheet.kt:259` is deleted.
- "Saved by …" is an **action**, not a dismissal: it appends a
  `SeatEvent(kind = SAVED, sourceId = …)`, marks the protecting token spent
  where the rules say so, and writes a log line. (Fixes P0-7.)
- `DeathRecord` gains `killerPlayerId: Long?` and `killerCharacterId: String?`;
  the "by" dropdown defaults to the alive Demon (or the character whose night
  step is open) and is required for `DEMON` / `OTHER_NIGHT_DEATH`.
- All five causes are reachable; the Exile row appears only for travellers.

### 7. Token placement v2

```
╭─ Token on Dana ───────────────────── [ ⌕ search ]╮
│ RECENT     ! Poisoned   + Safe   O No ability     │
│ THIS STEP  (night 3 · Poisoner)                   │
│            ! Poisoned (Poisoner)                  │
│ IN PLAY                                           │
│   Monk           + Safe                           │
│   Fortune Teller i Red herring                    │
│   Chambermaid    i Know                           │
│ GENERIC    ! Poisoned  ! Drunk  + Protected       │
│            M Mad  O Used  i ?                     │
│                                                   │
│ ⚠ Want to mark someone dead? That's a state:      │
│   [ Kill Dana… ]  — the "Dead" token only marks   │
│   a pending kill.                                 │
├───────────────────────────────────────────────────┤
│ Also place on:  [Ana][Ben][Cara][Eli] …  (multi)  │
╰───────────────────────────────────────────────────╯
```

- Search + in-play-first ordering, copied from the pattern that already works at
  `components/ShowCards.kt:269-282` (fixes P1-18); same treatment for
  `CharacterPicker`.
- **Recents** (last 6 placed labels this game) as the first row — the single
  biggest tap saver during a night.
- Multi-seat placement in one pass (fixes P2-23's second half).
- **One placement semantic, in the engine, not in the UI.** Delete the copy-count
  heuristic at `NightScreen.kt:319-339` and the raw append at `SeatSheet.kt:113`;
  both call one function:

```kotlin
/** Places [reminder] on [playerId], respecting the number of physical copies
 *  the character owns (green leaves). Oldest copy is displaced, never lost:
 *  the displaced placement is returned for the snackbar/undo label. */
fun placeReminder(state, playerId, reminder, copies = character.copiesOf(label)): Placement
```

  and the sheet/tray both show the outcome in one line before committing:
  *"Poisoner has 1 Poisoned token — this moves it from Ana."* (fixes P0-6).

### 8. Read-only grimoire (Spy) and the privacy cover

- Extract the circle/board renderer into
  `GrimoireBoard(state, mode: GrimoireMode, redactions: Set<Long>)` with
  `GrimoireMode { STORYTELLER, READ_ONLY }`. In `READ_ONLY`: no taps, no
  long-press, no zoom controls needed (pinch still allowed), **no `+N`
  truncation** (the Board view is used, so every token is shown in full text),
  and storyteller-private material — seat notes, bluffs, storyteller notes, log,
  top bar, tabs — is not composed at all. Redaction set covers the Magician
  jinx per `docs/audit/characters/spy.md`.
- One large "Done — back to the night sheet" button that re-arms the cover.
- **Privacy cover becomes the top-most window**: render it as
  `Dialog(properties = DialogProperties(usePlatformDefaultWidth = false,
  dismissOnBackPress = false, dismissOnClickOutside = false))` and, on engage,
  close any open sheet and suppress banners (fixes P0-8). Add a circular
  progress ring around the hold (fixes P2-26), and show only
  `"Night 3 · hold to open"` on the cover so the storyteller can still orient.
- Optional auto-cover after N seconds idle, and auto-cover on app background.

### 9. Zoom / pan

```kotlin
detectTransformGestures { centroid, pan, zoom, _ ->
    val old = scale
    val new = (old * zoom).coerceIn(0.8f, 4f)
    // keep the point under the fingers fixed
    offset = centroid - (centroid - offset) * (new / old) + pan
    scale = new
    clampPanToContent()
}
```

- Centroid-anchored zoom; pan clamped so ≥ 60% of the seat ring stays on screen.
- One-finger drag pans only when `scale > 1.02`; below that, drags are inert so
  taps on seats are unambiguous.
- Double-tap toggles fit ⇄ 2× anchored at the tap point.
- Raise the cap to 4× (the pips and 11 sp glyphs make this useful rather than a
  blur-magnifier).
- Move `ZoomControls` to a single collapsible FAB so it stops occupying 144 dp
  of the bottom-left seat's space (P3-28).

### 10. Traveller join, reorder, and seat identity

```
╭─ A traveller joins ───────────────────────────────╮
│ Name       [ Fred                              ]  │
│ Sits       between [ Cara (6) ▾ ] and Dana (7)    │
│ Character  [ ⌕ Scapegoat                      ▾ ] │
│ Alignment  ( ) Good        (•) Evil               │
│ Announce   "Fred joins as the Scapegoat"          │
│                                    [ Seat them ]  │
╰───────────────────────────────────────────────────╯
```

One dialog, one confirm — 4 inputs instead of ~16 taps (fixes P1-13). It calls
the **existing** `GameActions.addSeat(state, name, afterId)` (`GameActions.kt:19-26`)
plus `assignCharacter` and `flipAlignment` in a single `update {}` so undo is one
step. Reorder gets drag handles (`LazyColumn` + `detectDragGesturesAfterLongPress`)
and shows the character token; the ±1 arrows remain as an accessibility fallback.

### 11. Standing status line and the block

Extend `GrimoireScreen.kt:165-173` to a two-line header:

```
Day 3 · 7 alive · 4 to execute · 2 ghost votes
Ben is on the block (3 votes) · 2 tokens expire at dusk
```

Derived from `GameActions.aboutToDie` (`GameActions.kt:296`) and the
`EXPIRES_AT_DUSK` table (`GameActions.kt:231-243`). The on-block seat gets a gold
ring on the circle and a ⚑ in the board. Mastermind day becomes a line here
instead of a floating banner drawn over the privacy cover.

### 12. Undo with a name

- `GameViewModel.update(label: String) { … }`; keep `lastActionLabel` alongside
  `canUndo`.
- Every mutation raises a 5 s snackbar hosted **above** sheets:
  *"Removed 'Poisoned' from Dana"* + **UNDO**.
- Sticky in-sheet undo (§5) and undo/redo in the board's app bar.
- The reminder sheet keeps a "recently removed" row for one-tap restore.

### 13. Timer

Hoist `endAt` out of `DiscussionTimer` into `GameShell` (or `GameState`, so it
survives process death) and mount the composable on all tabs — collapsed to a
chip in the top bar while running (fixes P1-17), expanded only on Grimoire/Day.
Add a 30 s and 10 s haptic, and a distinct expired state. Dock it away from the
bottom seat.

### 14. Data changes

`GameState.kt`:

```kotlin
@Serializable
data class SeatEvent(
    val cycle: Int,
    val phase: Phase,
    val kind: SeatEventKind,
    val text: String,
    val sourceId: String = "",        // character that caused it
    val targetIds: List<Long> = emptyList(),
    val truthful: Boolean? = null,    // false when impaired/Vortox info was given
)
enum class SeatEventKind {
    SHOWN, TOLD, TOKEN_ADDED, TOKEN_REMOVED, DIED, SAVED,
    RESURRECTED, CHARACTER_CHANGED, MADNESS_INSTRUCTED, NOTE,
}
@Serializable
data class SeatNote(val cycle: Int, val phase: Phase, val text: String)

data class Player(
    …,
    val notes: List<SeatNote> = emptyList(),   // replaces `note: String`
    val history: List<SeatEvent> = emptyList(),
)
data class DeathRecord(
    …,
    val killerPlayerId: Long? = null,
    val killerCharacterId: String? = null,
)
data class PlacedReminder(
    val sourceId: String,
    val label: String,
    val placedCycle: Int = 0,        // for "placed N3" and expiry display
    val derived: Boolean = false,    // No Dashii-style, no physical token
    val suspended: Boolean = false,  // the physical "turn it upside-down" state
)
```

Every `GameActions` mutation appends the matching `SeatEvent`; `NightScreen`
appends `TOLD`/`SHOWN` when a show card is displayed or an `InfoCalc` result is
delivered, with `truthful` set from `StatusEffects.isImpaired`. That single
change is what makes Q6 answerable, and it makes the Game Log
(`GameExtras.kt:46-106`) a real transcript rather than deaths + nominations.

`characters.json`: normalise `"No Ability"` → `"No ability"` and
`"Has Ability"` → `"Has ability"` (P3-29) — or, if the data is left alone, the
`ReminderKinds` table and the `EXPIRES_AT_*` tables at `GameActions.kt:218-243`
must both be made case-insensitive.

### 15. UI text (storyteller voice, imperative, short)

- Board tab label: `Board` · circle tab label: `Circle`.
- Filter chips: `poisoned 2` · `protected 1` · `mad 1` · `used 3` · `dead 4`.
- Token peek header: `Tokens on Dana — tap to remove, drag to move`.
- Placement confirmation: `Poisoner has 1 Poisoned token — this moves it from Ana.`
- Kill sheet title: `Dana dies` · buttons `Record the death` / `Saved by the Monk`.
- Protection grouping headers: `Applies to this death` / `Not relevant to this cause`.
- Traveller dialog: `A traveller joins` / `Seat them`.
- Reminder picker warning: `"Dead" only marks a pending kill — to kill Dana, use Kill…`.
- Privacy cover: `Night 3 · press and hold to open` (progress ring), and
  `Keep holding…` while pressed.
- Spy view: `Showing the Grimoire — no edits are possible` /
  `Done — back to the night sheet`.
- 20-seat hint: `20 seats — the Board view reads better on a phone.` `[Switch]`

---

## Tests to add

Layout/geometry (pure functions, testable in `:engine` or `tools/uicheck`):

1. **Given** a 393×666 dp circle area and n = 12…20, **when**
   `SeatGeometry.allocate(n, w, h)` runs, **then** `budgetH <= spacing * 0.96`
   for every n — no two neighbouring seats can overlap.
2. **Given** n = 15 and a dead player with a two-line character name,
   **when** the seat is measured, **then** the pip row's measured height is
   ≥ 16 dp (today it is 0).
3. **Given** any n in 7…20, **then** the computed `tokenSize` is within
   [40 dp, 96 dp] and `showCharacterName` is false whenever the name would not
   fit on one line.
4. **Given** `ReminderToken(size = 22.dp)`, **then** the rendered font size is
   ≥ 11 sp (today 6 sp).

Kind classification:

5. **Given** each of the 91 distinct labels in `characters.json`, **when**
   `ReminderKinds.of` runs, **then** none returns `MARKER` for a label in the
   curated tables, and `"No ability"` / `"No Ability"` map to the same kind.
6. **Given** generic `PlacedReminder("", "Protected")` and
   `PlacedReminder("", "Poisoned")`, **then** their kinds — and therefore their
   colours — differ (today both are `BloodRed`).

Ordering / overflow:

7. **Given** a seat with tokens placed in the order
   `["Is the Drunk", "Know", "Poisoned", "Master"]` and a 2-pip budget,
   **when** pips are computed, **then** the visible pips are `IMPAIRED` and
   `IDENTITY` (priority order), not `Poisoned`+`Master` (placement order).

Placement semantics:

8. **Given** Poisoner "Poisoned" on Ana, **when** the same reminder is placed on
   Ben **from the seat sheet**, **then** Ana no longer holds it and exactly one
   copy exists — identical to placing it from the night tray.
9. **Given** a character with 3 copies of a label (Courtier "Drunk 1/2/3"),
   **when** a 4th placement is made, **then** the **oldest** placement is
   displaced and the returned `Placement` names it for the undo label
   (today `placed.first()` is evicted with no feedback).

Death / protection:

10. **Given** an alive Soldier, **when** `deathNotes(state, lookup, id, EXECUTION)`
    is called, **then** the Soldier's Demon protection is returned with
    `appliesTo = {DEMON}` and is **not** in the "applies to this death" group.
11. **Given** a Monk-protected player and `cause = DEMON`, **when** the
    storyteller chooses "Saved by the Monk", **then** state gains a
    `SeatEvent(kind = SAVED, sourceId = "monk")`, the player is still alive, and
    the game log shows a line for it (today: nothing changes).
12. **Given** a demon kill recorded from the night screen, **then**
    `DeathRecord.killerPlayerId` is the demon's seat id and
    `killerCharacterId` is the demon's character (today: no such field).

History / notes:

13. **Given** the Empath's night-3 info is delivered while the Empath is
    poisoned, **then** the Empath's `history` contains
    `SeatEvent(TOLD, cycle = 3, truthful = false)` with the delivered number in
    `text`.
14. **Given** a seat with a typed note, **when** the Drunk setup prompt runs
    `setNote`, **then** both entries exist in `Player.notes` (today the typed
    note is destroyed — `GameShell.kt:403-407`).
15. **Given** a seat sheet with an edited name and note, **when** the sheet is
    dismissed by the scrim rather than "Save & close", **then** both edits are
    persisted (today both are lost).

Traveller / seating:

16. **Given** 15 seats, **when** a traveller joins "between seat 6 and seat 7"
    with character `scapegoat` and evil alignment, **then** one `update {}` call
    produces a state with 16 seats in the right order, the character assigned
    and `alignmentFlipped` set, and **one** undo step reverts all of it.

Privacy:

17. **Given** `mastermindDayActive = true` and the privacy cover engaged,
    **then** no other composable is drawn above the cover (structural test in
    `tools/uicheck`, or an assertion that the cover is hosted in a `Dialog`).
18. **Given** `GrimoireMode.READ_ONLY`, **then** seat taps, long-press, the top
    bar, bluffs, storyteller notes and seat notes are all absent from the
    composition, and every reminder is rendered with its full label (no `+N`).

Timer:

19. **Given** a running 2-minute timer on the Day tab, **when** the storyteller
    switches to the Night tab and back, **then** the remaining time is
    unchanged (today it resets to idle).
