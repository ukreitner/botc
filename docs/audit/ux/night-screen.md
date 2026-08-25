# Night screen (ux/night-screen) — one-handed, in the dark, eyes closed

Scope: `NightScreen.kt` (the sheet, `NightStepRow`, `StepDetailPanel`,
`NightToolTray`, `GuideShowDialog`, `QuickResolutions`, `DemonKillPanel`,
`ResolutionPicker`), `components/ShowCards.kt` (`ShowCard`, `FullScreenShow`,
`ShowToolSheet`), `components/PrivacyCover.kt`, the night-relevant parts of
`GameShell.kt`, and the PWA input path (`web/src/wasmJsMain/**`,
`resources/index.html`).

This document does **not** restate rules content already covered by the
character auditors. It takes their findings as given and asks a different
question: *what must the screen look like so that a storyteller holding a
phone in one hand, in a dark room, at 1 a.m., with twelve people's eyes
closed, can run a night without a mistake and without lighting up the room.*

---

## Official rules (sources)

The physical procedure the screen is emulating, verbatim from the wiki
(<https://wiki.bloodontheclocktower.com/Glossary>,
<https://wiki.bloodontheclocktower.com/Storyteller_Advice>):

- **Night sheet** — "The sheet the Storyteller uses to know which characters
  act in which order at night." It "has one side to use on the first night and
  one side to use on all other nights."
- **Wake** — "A player opening their eyes at night. The Storyteller wakes a
  player by tapping twice on the knee or shoulder."
- **Dusk** — "The start of a night, just after the players close their eyes."
  **Dawn** — "The end of a night, just before the next day begins."
- **Grimoire** — "The box that stores the Clocktower pieces, held and updated
  by the Storyteller. **Players cannot look in the Grimoire.**"
- **Reminder token** — "The small tokens that help the Storyteller remember
  all sorts of things."
- **Info token** — "Rectangular tokens that give information and are sometimes
  shown to players at night. For example, the 'This is the Demon' info token."
- Storyteller Advice, on **hands**: "Hold the Grimoire by the strong center
  pillar from above or underneath. **This way, you can have a free hand to
  move tokens around.**"
- Storyteller Advice, on **secrecy**: "Keep the Grimoire level when moving
  about. The high sides of the Grimoire should keep its contents hidden from
  the players' view as long as you don't tip the Grimoire at a steep angle."
  Players should be "seated below the eye level of the top of the Grimoire in
  order to avoid accidentally seeing inside."
- Storyteller Advice, on **confirming a choice**: "During the night, confirm
  players' choices with a downward finger point." Walk to the player you think
  they chose and "point to them as well, pointing vertically and downwards
  while your hand is above them." The choosing player nods; you nod back.
- Storyteller Advice, on **noise**: "Quietly tap the shoulders or knees of the
  players that need to wake" — if tapping is audible, "press noticeably with
  your hand twice instead."
- Storyteller Advice, on **pace**: "If you relax and take your time when
  setting up each night phase, you'll find that mistakes get less and less
  frequent."
- Night order vs. ability text (<https://wiki.bloodontheclocktower.com/Abilities>):
  "the night sheet lists which characters act in what order, but some
  abilities may take effect earlier or later."

### The constraints those rules impose on a phone app

1. **One hand is the budget.** The other hand taps knees, points downward at
   seats, and holds nothing. Everything the screen asks for must be reachable
   by one thumb without a grip change. The physical grimoire needs no thumb at
   all — the app is *spending* a resource the paper version doesn't.
2. **The screen is the only light source and the only leak.** A phone tipped
   at any angle is a grimoire tipped at a steep angle. There is no "high side"
   — every accidental glance is a full reveal.
3. **The ST is standing and moving**, not seated at a desk. Reading targets
   are at ~50 cm, in the dark, often while walking to the player they are
   about to point at.
4. **Silence.** No audible feedback is available. Confirmation must be visual
   and instant.
5. **A "step" is not one action.** Every real step is: wake → ask → *receive a
   pointed choice* → resolve → place tokens → show something → put to sleep →
   record. The paper sheet only ever covered the first and last of those; the
   app claims to cover all seven, so it has to actually do so in one place.
6. **Night 1 and other nights are different sheets**, not the same sheet with
   different rows.

---

## What the app does today

### Anatomy

| Piece | File:line | Role |
|---|---|---|
| `NightScreen` | `NightScreen.kt:78-185` | builds the step list per cycle, owns `expandedId`, `showCard`, `pendingReminderLabel` |
| step list build | `NightScreen.kt:83-90` | `nightOrder.firstNight/otherNight(state, lookup)` |
| auto-open | `NightScreen.kt:91-93` | first step not in `state.nightStepsDone`, keyed on `state.cycle` |
| re-open on untick | `NightScreen.kt:102-108` | jumps back to a newly unchecked step |
| auto-scroll | `NightScreen.kt:110-117` | `animateScrollToItem(stepIndex + 1)` on expand |
| header | `NightScreen.kt:126-137` | "First Night" / "Night N" + "`x` of `y` steps done · tap a step for details" |
| rows | `NightScreen.kt:138-159` | `NightStepRow` + auto-advance on tick (`147-156`) |
| footer | `NightScreen.kt:160-169` | static italic "Dead players usually don't act — skip them…" |
| bottom tray | `NightScreen.kt:171-179`, `191-357` | reminder tokens, show-token/sheet/spent/all-tokens chips, seat picker |
| row | `NightScreen.kt:690-765` | checkbox, 44dp token, title, holder names, detail, "all dead" line |
| expanded panel | `NightScreen.kt:770-934` | guide prose, guide show chips, `QuickResolutions`, `InfoCalc` block |
| guide card dialog | `NightScreen.kt:364-454` | editable text + character picker → `ShowCard` |
| demon kill | `NightScreen.kt:534-638` | target chips → death notes → "X dies" / "No kill" / star pass |
| bespoke resolvers | `NightScreen.kt:461-525` | snakecharmer, fanggu, professor only |
| full-screen cards | `ShowCards.kt:82-160` | Message / Number / Alignment / Character / Bluffs / Sheet |
| card catalogue | `ShowCards.kt:261-413` | `ShowToolSheet` bottom sheet |
| dawn guard | `GameShell.kt:147-161`, `618-659` | refuses to advance while any step is unticked |
| night dimming | `GameShell.kt:234-237`, `322-330` | `Box(Color(0x66300000))` over the content area only |
| privacy cover | `PrivacyCover.kt:33-71` | black screen, 1.2 s hold to unlock |
| keep awake | `Platform.kt:17-24` (Android), `WebUiPlatform.kt:12-19` (PWA) | `keepScreenOn` / one-shot `navigator.wakeLock` |

### Simulation 1 — Trouble Brewing, 8 players

Seats: 1 Ana **Washerwoman**, 2 Ben **Empath**, 3 Cleo **Fortune Teller**,
4 Dan **Undertaker**, 5 Eve **Monk**, 6 Fay **Butler**, 7 Gus **Poisoner**,
8 Hal **Imp**. Bluffs already chosen; red herring placed at setup via
`GameShell.kt:347-376`.

**Night 1** — `NightOrder.build` emits 9 rows: Dusk, Minion info, Demon info,
Poisoner, Washerwoman, Empath, Fortune Teller, Butler, Dawn.

| # | Row | What the ST actually does | Taps | Must remember |
|---|---|---|---|---|
| 1 | **Dusk** | Row auto-expands. `NightGuide.forStep("DUSK", true)` is null (no `DUSK` key in `night_guide.json`), `QuickResolutions` returns at `NightScreen.kt:467`, `InfoCalc.supports("DUSK")` is false → **the expanded panel is empty**. Tray reads "Open a character step for its tools". | 1 tick | — |
| 2 | **Minion info** | Detail names the minions and the demon. Panel empty (marker id ⇒ no guide, no resolver, no calc). The "THIS IS THE DEMON" info token exists only inside `ShowToolSheet` (`ShowCards.kt:368`): tray "All tokens" → scroll the sheet → tap phrase → show → dismiss → close sheet. Then point at Hal by hand. | ~5 | which phrase to pick; that Gus is a Minion and Hal the Demon (the row text scrolls off once expanded) |
| 3 | **Demon info** | One useful chip, "Show bluffs full-screen" (`NightScreen.kt:783-788`). Minions still have to be shown via `ShowToolSheet`. If bluffs were never chosen the detail says "Pick them from the menu" — leave the Night tab, overflow menu → Demon bluffs → sheet → back. | 3-8 | to point at Gus while the bluff card is up |
| 4 | **Poisoner** | Panel is one paragraph of 12 sp italic prose. No picker. Poisoning is done in the **tray at the bottom of the screen**: tap "Poisoned" (`NightScreen.kt:283-306`), the seat row appears and pushes the layout (`308-354`), horizontally scroll to Ben, tap. | 3 | that this poison lasts until dusk tomorrow; where the token now is (not visible from this tab) |
| 5 | **Washerwoman** | `InfoCalc` prints "Townsfolk in play: Ana (Washerwoman), Ben (Empath), Cleo (Fortune Teller), Dan (Undertaker), Eve (Monk)" — **including the Washerwoman herself** (`InfoCalc.kt:408-421`). No number/YES-NO, so no auto show chip. Guide chip "» Show Townsfolk token" → `GuideShowDialog` → find "Empath" among up to 24 chips → "Show full-screen" → dismiss. Then place `Townsfolk` and `Wrong` (`characters.json` washerwoman reminders) via tray: 4 more taps. Then point at two players by hand. | 9 | which wrong player was pointed at; to exclude Ana from the answer |
| 6 | **Empath** | "0 of Ben's alive neighbours are evil" → "Show 0 full-screen" (`NightScreen.kt:890-895`) → dismiss. | 3 | — |
| 7 | **Fortune Teller** | Two name-only chips (`NightScreen.kt:846-860` — no token, no dead marker, no self hint), "YES"/"NO", "Show answer full-screen". | 5 | who was chosen (nothing records it) |
| 8 | **Butler** | Tray: "Master" → seat. | 3 | — |
| 9 | **Dawn** | Detail: "Wait a few seconds. Everyone opens their eyes. **Announce who died**" (`NightOrder.kt:59`) — *without naming anyone*. Tick, then hit the phase button. | 2 | who died, from memory |

≈ **40 taps**, 9 of them pure bookkeeping ticks, 2 of which (Dusk, Dawn) mark
nothing at all but are enforced by the dawn guard (`GameShell.kt:147-161`).

**Night 2** — 9 rows: Dusk, Poisoner, Monk, Imp, Empath, Fortune Teller,
Undertaker, Butler, Dawn.

- **Poisoner / Monk** — identical two-tap tray dance; the previous night's
  `Poisoned`/`Safe` tokens are invisible from this screen.
- **Imp** — the one genuinely well-built flow: `DemonKillPanel`
  (`NightScreen.kt:534-638`) shows every seat with a token, sorts alive-first
  and self-last, prints `StatusEffects.deathNotes` for the selected target,
  and offers "Fay dies" / "No kill". Its target state is correctly keyed on
  the cycle (`NightScreen.kt:540`). **But** confirming the kill does not tick
  the step, and nothing records that the Imp *chose* Eve and the Monk saved
  her — which the Undertaker, Sage, Mathematician and tomorrow's morning
  briefing all need.
- **Fortune Teller** — `var targets by rememberSaveable(step.id)`
  (`NightScreen.kt:839`) is **not** keyed on `state.cycle`. Last night's two
  chips are still lit and a stale YES/NO headline is displayed as if it were
  tonight's answer. (Same bug on Dreamer, Seamstress, Chambermaid,
  Ravenkeeper, Grandmother, Village Idiot — see `characters/villageidiot.md`
  #5.)
- **Undertaker** — the panel already says "Show: Poisoner", and then the guide
  chip makes the ST hunt for the Poisoner token in a 24-chip picker
  (`NightScreen.kt:392-435`) instead of pre-selecting the answer it just
  computed.

### Simulation 2 — Bad Moon Rising, 12 players

Seats: Grandmother, Sailor, Chambermaid, Exorcist, Innkeeper, Courtier,
Professor (townsfolk); **Lunatic**, Tinker (outsiders); Devil's Advocate,
Godfather (minions); **Pukka** (demon).

**Night 1** — 12 rows: Dusk, Minion info, **Lunatic**, Demon info, Sailor,
Courtier, Godfather, Devil's Advocate, **Pukka**, Grandmother, Chambermaid,
Dawn.

- **Lunatic** (`night_guide.json` lunatic.first = 799 characters) renders as a
  single block of 12 sp italic `onSurfaceVariant` prose with **zero controls**
  — `QuickResolutions` falls through to the `else` branch, sees
  `team == OUTSIDER`, and renders nothing (`NightScreen.kt:518-523`). The
  Lunatic must perform the Demon's action; the screen offers no target picker,
  no "Attack 1/2/3" placement other than the generic tray, and no way to
  record the fake choice that the real Demon must be shown later.
- **Demon info** — the detail string appends the Lunatic annotation
  (`NightOrder.kt:157-172`) so the row text grows to three sentences of
  12 sp copy inside a collapsed row.
- **Courtier** — the tray offers four near-identical chips in a horizontally
  scrolling row: `Drunk 3`, `Drunk 2`, `Drunk 1`, `No ability`
  (`characters.json` courtier). Each is a single copy, so
  `placeExclusiveReminder` *moves* it. Decrementing the counter each night is
  fully manual: find the right chip in the dark, find the right seat, tap.
- **Godfather** — guide chip "Show the Godfather" / text "THIS OUTSIDER IS IN
  PLAY", `token:"pick"`. The app knows the Outsiders in play (Lunatic, Tinker)
  and still makes the ST search for each one, one card at a time, with no way
  to show both.
- **Pukka** — `QuickResolutions` `else` branch, `team == DEMON`, holder alive
  ⇒ **`DemonKillPanel` on night 1**, headed "Demon kill — who did Kim choose?"
  with a "Kim dies" button (`NightScreen.kt:518-523`, `543-547`, `625-633`).
  This is the user's original complaint, and from the UX side the damning
  detail is that the *only prominent, primary-styled control on the step is
  the wrong one*.
- **Grandmother** — needs the grandchild's character token shown **and** the
  grandchild pointed at. The app can do the first (guide chip, manual search)
  and nothing for the second.

**Night 2** — 13 rows: Dusk, Sailor, Innkeeper, Courtier, Devil's Advocate,
Lunatic, Exorcist, Pukka, Godfather, Professor, Grandmother, Chambermaid,
Dawn.

- **Devil's Advocate** — "different from the previous night" is in the prose;
  the tray's seat picker does not exclude, sort or mark last night's target.
- **Exorcist** — one chip, `token:"self"` ⇒ the Exorcist's own token above
  "THIS PLAYER STOPPED YOU TONIGHT". Semantically right, but the Demon needs
  to know *which player*, and the card cannot name a seat.
- **Professor** — the best of the three bespoke resolvers
  (`NightScreen.kt:499-517`): dead players, townsfolk sorted first, one
  confirm button. Still does not tick the step, and nothing schedules the
  first-night re-run the user asked for.
- **Godfather / Grandmother** — both only wake on a condition ("if an Outsider
  died today", "if the grandchild was killed by the Demon"). The rows are
  emitted unconditionally with no "nothing to do tonight" state
  (`NightOrder.kt:142-178`), so two of thirteen steps are noise that must
  still be ticked before dawn.

### Simulation 3 — Sects & Violets, 9 players

Seats: Clockmaker, Dreamer, Snake Charmer, Mathematician, Seamstress
(townsfolk); Sweetheart, Klutz (outsiders); Cerenovus (minion); Vortox (demon).

**Night 1** — 10 rows: Dusk, Minion info, Demon info, Snake Charmer,
Cerenovus, Clockmaker, Dreamer, Seamstress, Mathematician, Dawn.

- **Snake Charmer** — `ResolutionPicker` whose *title* is a 130-character
  paragraph in gold `labelLarge` ("Charm hit the Demon? Pick the Demon — they
  and X swap characters AND alignments…", `NightScreen.kt:471-481`). The
  question the ST is actually answering at the table is "who did they point
  at?"; the app asks "was it the Demon?" and records nothing in the common
  case where it wasn't.
- **Cerenovus** — three chips, each opening `GuideShowDialog`: the Cerenovus
  token, the mad-as character (search + pick), and a typed madness demand.
  Then the `Mad` token must be placed from the tray. Four dialogs/round-trips
  for one step.
- **Clockmaker** — two competing affordances for the same answer: the
  auto-generated "Show 2 full-screen" chip (220 sp `NumberCard`,
  `ShowCards.kt:100-106`) and the guide's "Steps to nearest Minion" chip whose
  text is literally `"…"` (`night_guide.json` clockmaker.first), so the ST
  must type the number into a text field and gets a 52 sp `BigText` instead.
- **Dreamer** — must show **two tokens at once** (one good, one evil). The app
  can only display one `ShowCard` at a time; the guide gives two chips and the
  player has to remember card #1 while card #2 is held up.
- **Mathematician** — headline is an instruction, not an answer ("Count
  abilities that malfunctioned since dawn", `InfoCalc.kt:76-80`), so no number
  chip is generated; the only route is the `"…"` dialog and a 52 sp text card
  where a 220 sp digit is wanted.

**Night 2** — Dusk, Snake Charmer, Cerenovus, Vortox, [Sweetheart], Dreamer,
Seamstress, Mathematician, Dawn.

- **Vortox in play** ⇒ `commonCaveats` adds "VORTOX in play — Townsfolk info
  must be FALSE" to every townsfolk step (`InfoCalc.kt:158-166`), which sets
  `impaired` true in the panel (`NightScreen.kt:903-906`).
  - **Seamstress**: works — the red "False info to show instead:" heading with
    a one-tap inverted YES/NO.
  - **Dreamer**: the red heading renders with an **empty chip row beneath it**
    (`NightScreen.kt:913-929` only produces chips for a leading digit or
    YES/NO). The ST is told there is false info to give and offered nothing —
    identical dead end to `characters/shugenja.md` #2.
- **Sweetheart** — only acts on death; unconditional row again.

---

## Defects and gaps

### P0 — the storyteller is misled, or the screen leaks

1. **P0 · The primary action of a step can be flatly wrong, and it is the only
   button on the card.** Every Demon that is not Fang Gu falls into
   `DemonKillPanel` (`NightScreen.kt:518-523`), so the Pukka's night-1 step,
   the Zombuul's non-killing nights, the Vigormortis, the Lil' Monsta holder
   and the Lunatic-as-Demon all get a filled-tonal "X dies" button as the
   single most prominent control. UX consequence independent of the rules bug:
   **the app teaches the ST that the primary button is safe to press**, then
   makes it wrong on exactly the nights that matter. Repro: BMR, night 1,
   expand Pukka.
2. **P0 · A tap anywhere on a full-screen card dismisses it — including a tap
   by the player it is being shown to.** `ShowCards.kt:91-96` puts
   `clickable(onDismiss)` on the entire `fillMaxSize` box, and dismissal drops
   straight back to the night sheet (every character, every reminder token,
   every holder name) while the phone is still pointing at a player's face.
   There is no cover state between the card and the grimoire. Repro: show any
   card, hand the phone toward a player, let them touch it.
3. **P0 · Stale target selections are presented as tonight's answer.**
   `rememberSaveable(step.id)` at `NightScreen.kt:839` is keyed on the step id
   only, while the rest of the screen is keyed on `state.cycle`
   (`NightScreen.kt:91`, `96`, `540`, `473`). On night 3 the Fortune Teller
   row opens with night 2's two chips lit and a computed YES/NO underneath.
   Nothing distinguishes a fresh answer from a stale one. Repro: pick two
   players on night 2, Dawn, Dusk, reopen the Fortune Teller row.
4. **P0 · One panel, one holder.** `QuickResolutions` (`NightScreen.kt:467`)
   and the info block (`NightScreen.kt:837`) both use
   `step.playerIds.firstOrNull()`. Any step with two or more holders — two
   Village Idiots, Legion, a Pit-Hag/Boffin duplicate, a Drunk or Marionette
   whose shown character matches a real in-play character, a Cannibal or
   Philosopher who has gained the ability — serves the first seat and silently
   discards the others, *including their impairment caveats*. The row title
   even displays both names (`NightScreen.kt:735-742`), so the screen looks
   like it handled both. Detailed repro in `characters/villageidiot.md` #1-2.
5. **P0 · "All holders are dead — usually skip." is the only red text on a
   collapsed row, and it is the least important thing on the screen.**
   `NightScreen.kt:751-757` paints it in `colorScheme.error`; genuinely
   dangerous states (this info holder is poisoned, this Demon's attack fails,
   this once-per-game is already spent, this player is the red herring) get
   **no row-level marker at all** — they are one expand and one scroll deep.
   The colour semantics are inverted: red means "ignore me", and "you are
   about to hand a player a lie" is grey 12 sp body text. The sentence is also
   wrong outright in a Vigormortis game (dead Minions keep their abilities),
   for the Zombuul, and for any dead-but-acting character.
6. **P0 · `kind:"good"` / `kind:"evil"` guide cards discard their text.**
   `NightScreen.kt:806-816` routes to `ShowCard.AlignmentCard`, and
   `ShowCards.kt:107-126` hard-codes "YOU ARE GOOD" / "YOU ARE EVIL". Already
   documented in `characters/general.md` #1; recorded here because the fix
   belongs to this file and because it is the single most-used delivery path
   on the PWA.

### P1 — the ST does bookkeeping the screen could do

7. **P1 · Marking a step done is never a consequence of doing it.** Confirming
   a Demon kill (`NightScreen.kt:625-633`), placing a reminder
   (`NightScreen.kt:318-341`), showing a computed card
   (`NightScreen.kt:890-901`), resurrecting with the Professor
   (`NightScreen.kt:512-515`) — none of them touch `nightStepsDone`. The ST
   must find the small `Checkbox` at the far left of the row
   (`NightScreen.kt:715`) as a separate act, which on a right-handed one-hand
   grip is the hardest pixel on the screen to reach.
8. **P1 · Dusk and Dawn are mandatory checkbox taps that mark nothing.**
   `NightOrder.kt:58-59` always emits them, and the dawn guard
   (`GameShell.kt:147-161`) counts them as unfinished. Pressing the "Dawn"
   phase button before ticking the "Dawn" row raises a modal
   (`GameShell.kt:618-659`) telling you that the Dawn step is incomplete.
9. **P1 · The Dawn row says "Announce who died" and does not say who.**
   `NightOrder.kt:59`. `state.deaths` has the answer; `DayScreen.kt` shows no
   dawn summary either (grep for `deaths|died|dawn` in `DayScreen.kt` and
   `GrimoireScreen.kt` returns nothing). The one moment the ST speaks out loud
   is the one moment the app says nothing.
10. **P1 · Nothing on this screen records what happened.** `GameLogDialog`
    (`GameExtras.kt:45-106`) is derived purely from `state.deaths` and
    `state.nominations`. Who the Fortune Teller chose, what number the Empath
    was shown, which direction the Shugenja was pointed, which way the
    Snake Charmer was aimed when the charm missed, what lie was told to a
    poisoned player — all of it lives only in the ST's head, and the app
    actively deletes the UI state that held it at the next phase change.
11. **P1 · The tray is a second, disconnected screen.** `NightToolTray`
    (`NightScreen.kt:191-357`) is pinned to the bottom while the expanded step
    is auto-scrolled to the top (`NightScreen.kt:110-117`). The step says
    "point to a player, mark them Master"; 400 dp away a separate surface says
    "Poisoner night tools / Tap a reminder, then tap the player who gets it".
    Neither names the other. When placing a token it grows two extra rows and
    shoves the sheet up, then collapses again after the tap.
12. **P1 · The token-placement seat picker is a horizontally scrolling
    `LazyRow` over `state.players` in raw seat order** (`NightScreen.kt:314-353`),
    dead players included and marked only with a trailing "†"
    (`NightScreen.kt:350`). Horizontal scrolling to find seat 11 of 12, in the
    dark, one-handed, is the worst gesture on the screen — and it is the one
    used for every poison, protect, madness and drunk token in the game.
13. **P1 · Three different, inconsistent player pickers.** `FlowRow` of
    `FilterChip` with tokens and dead-dimming (`DemonKillPanel`,
    `NightScreen.kt:555-584`); `FlowRow` of `FilterChip` with tokens
    (`ResolutionPicker`, `NightScreen.kt:657-676`); `FlowRow` of `FilterChip`
    with **names only, no token, no dead marker, no self marker**
    (the info-target picker, `NightScreen.kt:846-860`); plus the `LazyRow` in
    the tray. The info picker — the one used by Fortune Teller, Dreamer,
    Seamstress, Chambermaid, Ravenkeeper, Grandmother, Village Idiot — is the
    least informative of the four.
14. **P1 · Dead targets are selectable and then dead-end.** `DemonKillPanel`
    lists dead players (`NightScreen.kt:559-584`); selecting one enables
    nothing (`enabled = target.alive`, `NightScreen.kt:626`). The ST taps and
    gets a disabled button with no explanation.
15. **P1 · `GuideShowDialog` never pre-selects the answer the app just
    computed.** `NightScreen.kt:375-377` sets `tokenId` only for
    `token == "self"`. The Undertaker panel prints "Show: Poisoner" and then
    hands the ST a search field; the Imp star-pass card, the Washerwoman
    token, the Grandmother's grandchild, the Godfather's Outsiders are all
    known to `InfoCalc`/`GameState` and all require a manual hunt among up to
    24 chips (`NightScreen.kt:412`).
16. **P1 · The false-info shortcut is a dead end for every non-numeric,
    non-YES/NO character.** `NightScreen.kt:903-930`: the red heading
    "False info to show instead:" is gated on the *caveat text* but the chips
    are gated on the *headline shape*. Dreamer, Washerwoman, Librarian,
    Investigator, Undertaker, Shugenja, Balloonist, Steward, Noble, Knight,
    Sage, Cult Leader, Village Idiot all render the heading with nothing under
    it. Under a Vortox this fires on *every* Townsfolk step in the game.
17. **P1 · False numbers stop at 4.** `NightScreen.kt:914-921` iterates
    `0..4`. A poisoned Empath in a 15-player game, a Clockmaker seven steps
    from the Minion, a Chambermaid… the lie the ST wants may not be offered,
    and there is no "any number" escape except the overflow-menu card tool.
18. **P1 · There is no way to show a player at a player.** Washerwoman,
    Librarian, Investigator, Noble, Steward, Knight, Sage, Grandmother, the
    Exorcist's "this player stopped you", the Lunatic reveal to the Demon and
    the "point out the Minions/Demon" info steps all require naming a *seat*.
    `ShowCard` has no such variant (`ShowCards.kt:65-78`), so on every one of
    those the ST puts the phone down and points with a hand they do not have
    free. This is the largest single gap between the paper procedure and the
    app.
19. **P1 · The card catalogue is three taps away and behind the overflow
    menu.** `ShowToolSheet` is reachable from the tray ("All tokens",
    `NightScreen.kt:280`) or menu → "Show a card…" (`GameShell.kt:226-229`).
    The classic info tokens — "THIS IS THE DEMON", "THESE ARE YOUR MINIONS" —
    are needed by the *first two steps of every 7+ player game* and are not
    offered by those steps.
20. **P1 · The PWA wake lock is requested once and never re-acquired.**
    `WebUiPlatform.kt:12-19` calls `navigator.wakeLock.request('screen')`
    inside `LaunchedEffect(Unit)` and drops the promise. Browsers release the
    lock whenever the document is hidden; iOS releases it on any tab switch or
    manual lock. After the first interruption the phone sleeps for the rest of
    the game — in the middle of a night, with everyone's eyes closed. There is
    no `visibilitychange` re-request and no error surface if the request is
    refused.
21. **P1 · No brightness control, and the one dimming lever misses the
    brightest pixels.** `nightScrim` (`GameShell.kt:322-330`) is a
    `Box(Color(0x66300000))` **inside the Scaffold content**, so the
    `TopAppBar` and the `NavigationBar` — the two most saturated surfaces —
    stay at full brightness. It is off by default, buried at position 5 of 13
    in the overflow menu (`GameShell.kt:234-237`), stored in
    `rememberSaveable` rather than in `GameState`, and binary (no level).
22. **P1 · Accidental collapse.** The whole row `Surface` carries
    `clickable(onClick = onExpand)` (`NightScreen.kt:704-712`) and the
    expanded panel is its child. Any tap on the guide prose, on the gap
    between chips, on the caveat lines, or a slightly-off tap at a chip edge
    **closes the step you are working on**, discarding the `activeShow` state
    (`NightScreen.kt:803`, plain `remember`) and clearing
    `pendingReminderLabel` (`NightScreen.kt:110-111`).
23. **P1 · Destructive actions are one unconfirmed tap.** "X dies"
    (`NightScreen.kt:625-633`) kills. "X becomes the Imp"
    (`NightScreen.kt:609-621`) rewrites a character and an alignment. "Mark
    spent" (`NightScreen.kt:263-279`) writes "No ability" onto *every* holder.
    Undo exists but is an unlabeled 24 dp icon in a five-action top bar
    (`GameShell.kt:189-193`) that never says what it will undo, and one tick
    of a checkbox pushes the real mistake one step deeper into the stack.

### P2 — clarity and speed

24. **P2 · Density is uniform, so nothing is prioritised.** Every row is
    checkbox + 44 dp token + `titleMedium` name + `labelMedium` holder names +
    `bodySmall` detail. A step with a 20-character detail ("Give a thumb
    signal.") and a step with a three-sentence Lunatic annotation
    (`NightOrder.kt:157-172`) get the same treatment, and the detail is hidden
    once the step is done unless expanded (`NightScreen.kt:744`).
25. **P2 · "Which step am I on" is signalled only by expansion.** There is no
    accent bar, no number, no dimming of future steps. A done step differs
    from a pending step by `tonalElevation` 0 vs 2 dp and a slightly greyer
    title (`NightScreen.kt:707`, `729-733`) — both invisible under the night
    scrim. The header counter "3 of 9 steps done" (`NightScreen.kt:132-136`)
    is 12 sp and scrolls away.
26. **P2 · There is no explicit "nothing to do tonight" state.** Undertaker
    with no execution, Grandmother with a living grandchild, Godfather with no
    Outsider death, Professor already spent, Ravenkeeper alive, King below
    threshold — the engine can decide all of these, and in a couple of cases
    `InfoCalc` even prints the sentence ("No one was executed today — the
    Undertaker doesn't wake", `InfoCalc.kt:285`) — but it is one expand deep
    and the row still demands a tick.
27. **P2 · No skip.** The only two states are unticked and ticked. "Skipped
    because the holder is dead", "skipped because the ability is spent",
    "deliberately not woken" and "done" are all the same checkmark, so the
    dawn guard's list (`GameShell.kt:618-659`) cannot distinguish "you forgot
    the Monk" from "the Grandmother had nothing to do".
28. **P2 · The dawn-guard modal is a list of titles with no way to act.**
    `GameShell.kt:618-659` prints bullet points; the ST must dismiss, find
    each row, and tick it. The confirm button is "Dawn anyway" and the dismiss
    is "Keep checking" — the safe action is the one that looks like a cancel.
29. **P2 · Night 1 vs later nights is a 24 sp title only.** The card stock is
    identical, and several night-1-only concerns (bluffs not yet chosen, red
    herring, Drunk/Lunatic/Marionette identities, "no minion info under 7
    players") are handled in `GameShell` at SETUP time
    (`GameShell.kt:347-479`) with a "Later" button that permanently silences
    the prompt (`herringPromptDone` etc.), never re-surfacing on the night
    screen where it matters.
30. **P2 · The tray's reminder chips are a colour dot, not a token.**
    `NightScreen.kt:293-301` renders a 12 dp team-coloured circle with the
    comment "A tiny full token is unreadable". `ReminderToken` is imported at
    `NightScreen.kt:64` and never used. Four Courtier chips ("Drunk 3",
    "Drunk 2", "Drunk 1", "No ability") are therefore four identical orange
    dots with 10-12 sp labels in a scrolling row.
31. **P2 · Multi-copy reminder placement silently steals a token.** When all
    copies are placed, `NightScreen.kt:331-337` removes the **first** placed
    copy — an arbitrary seat, chosen by iteration order, with no warning and
    no visible effect on this screen.
32. **P2 · Marker steps have no guide entries.** `night_guide.json` has no
    `DUSK`, `DAWN`, `MINION_INFO` or `DEMON_INFO` keys, so those four rows —
    including two of the three most procedurally involved steps of night 1 —
    expand to an empty panel.
33. **P2 · `BluffsCard` uses a non-wrapping `Row`** (`ShowCards.kt:148-154`);
    a fourth bluff (Boffin, house rules, some Fabled) runs off the screen
    edge.
34. **P2 · The pointable character sheet is the wrong grain.** `SheetCard`
    (`ShowCards.kt:164-225`) renders the whole script at 62 dp with 11 sp
    names for the player to point at, which is right for Pit-Hag/Philosopher,
    but there is no filtered variant ("point at a Townsfolk", "point at a
    Minion") and no way for the ST to see what was pointed at afterwards.
35. **P2 · `ShowToolSheet` leaks.** It sorts in-play characters first and says
    so out loud ("Characters in play are first", `ShowCards.kt:313-316`). It
    is the sheet the ST opens while walking to a player.
36. **P2 · Zero landscape/left-hand consideration.** Everything primary sits
    at the top of a scrolling column; the only fixed bottom element is the
    tray, whose most-used control (the seat picker) is conditional and
    horizontally scrolling.

### P3 — polish

37. **P3 · The phase button on a phone is an unlabeled icon.**
    `compactTopBar = maxWidth < 520.dp` (`GameShell.kt:172-173`) — every phone
    — so "Dawn" becomes a bare ☀ / 🌙 glyph (`GameShell.kt:195-205`) whose
    direction ("it is night" vs "go to night") is ambiguous.
38. **P3 · `tab = when (state.phase)` after `advancePhase`
    (`GameShell.kt:162-167`) reads the pre-advance snapshot** and happens to
    land on the right tab. Correct today, fragile forever.
39. **P3 · `animateScrollToItem` races the expand animation.**
    `NightScreen.kt:110-117` scrolls to `index + 1` while
    `AnimatedVisibility` (`NightScreen.kt:760-762`) is still growing the item,
    so the step often settles somewhere other than the top of the viewport.
40. **P3 · `index.html` sets `touch-action: none` and `user-scalable=no`**
    (`web/src/wasmJsMain/resources/index.html`), so the ST cannot pinch-zoom
    the 10-12 sp run-book text the app depends on.
41. **P3 · `PrivacyCover` renders an empty `Text("", fontSize = 64.sp)`**
    (`PrivacyCover.kt:54`) — a leftover placeholder that reserves a blank
    line above "The grimoire is closed".

---

## Proposed behaviour (spec)

The organising principle: **a night step is a card, a card has exactly one
primary button, and pressing that button advances the night.** Everything the
ST must read is above the button; everything they must decide is a tap on the
same card; nothing is in a tray, a bottom sheet, or another tab.

### A. The engine gives the UI a plan, not a list of titles

The whole redesign hangs on moving the per-step decisions out of the composable
and into a testable engine model. Extend `NightOrder` to produce:

```kotlin
enum class StepStatus { ACT, CONDITIONAL, SKIP, DONE }

data class NightHolder(
    val playerId: Long,
    val name: String,
    val alive: Boolean,
    val impairment: String?,        // "POISONED (Poisoner)" | "IS the Drunk" | null
    val status: StepStatus,
    val skipReason: String?,        // "dead", "ability already spent", "grandchild is alive"
    val prompt: String,             // "Wake Ben. They point at two players."
    val ask: NightAsk?,             // what the ST must input, see B
    val info: InfoCalc.InfoResult?, // computed for THIS holder
    val cards: List<CardOffer>,     // see D, pre-filled from info
    val record: NightRecord?,       // what was already done tonight
)

data class NightStepView(
    val id: String,
    val ordinal: Int,               // 1..n, stable, shown on the card
    val title: String,
    val holders: List<NightHolder>, // ← never collapsed to first()
    val status: StepStatus,         // worst-of holders
    val summary: String,            // one line for the collapsed row
)

data class NightPlan(
    val cycle: Int,
    val isFirstNight: Boolean,
    val steps: List<NightStepView>,
    val dawn: DawnSummary,          // see F
)
```

`StepStatus` replaces the current "emit every row, annotate the dead ones"
model (`NightOrder.kt:142-178`, `NightScreen.kt:751-757`):

- `ACT` — someone wakes and something must be input.
- `CONDITIONAL` — the engine cannot decide alone; the card asks a yes/no
  ("Did an Outsider die today?") before offering the action.
- `SKIP` — the engine knows there is nothing to do, and says why. A `SKIP`
  step is **collapsed to one dim line, pre-ticked, and excluded from the dawn
  guard**. It is still visible (the ST may disagree) and still expandable.
- Dead holders default to `SKIP` **except** where the character or the game
  state says otherwise: Vigormortis in play (dead Minions act), Zombuul,
  Ravenkeeper, Sage, Banshee, Farmer, Moonchild, Lleech host. This inverts the
  current blanket "All holders are dead — usually skip".

### B. One card, one question, one button

```
╔══════════════════════════════════════════════╗
║ NIGHT 2      step 6 / 11     ▮▮▮▮▮▯▯▯▯▯▯ ⏻  ║  ← 1
╠══════════════════════════════════════════════╣
║ ┌────┐                                       ║
║ │IMP │  IMP                      Hal · seat 8║  ← 2  (24sp / 16sp)
║ └────┘                                       ║
║ ⚠ POISONED by the Poisoner — the attack      ║  ← 3  (16sp, ember)
║   fails tonight. Choose "no death".          ║
║                                              ║
║ Wake Hal. They point at a player.            ║  ← 4  (18sp, plain)
║                                              ║
║ WHO DID HAL CHOOSE?                          ║  ← 5
║ ┌───────────┬───────────┬───────────┐        ║
║ │  1  Ana   │  2  Ben   │  3  Cleo  │        ║      64dp rows,
║ ├───────────┼───────────┼───────────┤        ║      seat order,
║ │  4  Dan   │  5  Eve ✋│  6  Fay   │        ║      alive only
║ ├───────────┼───────────┼───────────┤        ║
║ │  7  Gus   │ ▣8  Hal ◆ │           │        ║      ◆ = self
║ └───────────┴───────────┴───────────┘        ║
║ ⌄ 2 dead seats                               ║      collapsed
║                                              ║
║ Eve: marked SAFE (Monk) — cannot die.        ║  ← 6  consequence
╠══════════════════════════════════════════════╣
║ ╭──────────────────────────────────────────╮ ║
║ │      EVE SURVIVES — NOBODY DIES      →   │ ║  ← 7  ONE primary
║ ╰──────────────────────────────────────────╯ ║
║  ⌃ other outcomes · show a card · skip step  ║  ← 8  secondary drawer
╚══════════════════════════════════════════════╝
```

1. **Progress strip** — cycle, ordinal, a segment bar, and a ⏻ brightness
   control (§H). Fixed; never scrolls.
2. **Who wakes** — token + character name + **player name and seat number**.
   Seat numbers matter: the ST is about to walk to a chair.
3. **Status banner** — the single most important derived fact, in ember,
   16 sp, *above* the instructions: impairment, "attack fails", "ability
   already spent", "must differ from last night: chose Fay", "Cleo is the red
   herring". Replaces the caveat list currently buried at the bottom of the
   info block (`NightScreen.kt:878-884`) and fixes the red-means-nothing
   inversion of defect #5.
4. **Prompt** — imperative, storyteller voice, ≤ 2 lines, 18 sp. The long
   `night_guide` prose moves behind a "how to run this" disclosure so the card
   never needs scrolling for the common case.
5. **The ask** — a `NightAsk`, rendered as **one** picker component used
   everywhere (replacing the four in defect #13):

   ```kotlin
   sealed interface NightAsk {
     data class Players(
       val count: Int,
       val alive: Boolean = true,          // dead seats behind a disclosure
       val excludeSelf: Boolean = false,   // ◆ marks self when allowed
       val excludeIds: Set<Long> = emptySet(),
       val differentFromLastNight: Long? = null,   // shown struck through
       val sortBy: Sort = Sort.SEAT,       // SEAT | ALIVE_FIRST | TEAM
       val label: String,                  // "WHO DID HAL CHOOSE?"
     ) : NightAsk
     data class Character(val filter: TeamFilter, val label: String) : NightAsk
     data class YesNo(val label: String) : NightAsk
     data class Number(val range: IntRange, val label: String) : NightAsk
     data object None : NightAsk
   }
   ```

   Rules for the player picker, all currently violated somewhere:
   - **Seat order by default**, laid out as a 2-3 column grid of 64 dp rows —
     never a horizontally scrolling row (defect #12).
   - **Seat number always shown** next to the name.
   - **Alive seats only** by default; dead seats behind a "⌄ N dead seats"
     disclosure, and *selectable* there when the ability allows it (Fortune
     Teller may choose the dead; Undertaker/Ravenkeeper are about the dead).
     Never selectable-then-disabled (defect #14).
   - **Self is shown with ◆** when legal (Fortune Teller, Imp star pass, Sailor)
     and hidden when not (Devil's Advocate, Exorcist, Monk).
   - **Last night's target is struck through** with the caption "last night"
     when the ability says "different from last night" (Devil's Advocate,
     Exorcist, Innkeeper, Monk-variants, Grandmother-variants).
   - **The choice is stored in `GameState`, keyed by `(cycle, stepId,
     holderId)`** — not in `rememberSaveable` (defect #3). That both fixes
     staleness and gives the log and the "different from last night" rule a
     source of truth (defect #10).
6. **Consequence preview** — `StatusEffects.deathNotes` and friends, rendered
   *before* the button, so the button label can state the actual outcome.
7. **The primary button states the outcome, not the verb.** "EVE SURVIVES —
   NOBODY DIES", "FAY DIES", "BEN IS POISONED", "SHOW 0 TO BEN", "RESURRECT
   DAN". Pressing it: applies the state change, places the required reminder
   tokens automatically, writes a `NightRecord`, **marks the step done**, and
   **advances to the next `ACT` step** (defects #7, #8). It is a full-width
   56 dp target at the bottom of the screen — the one place a thumb reaches
   without a grip change.
8. **Secondary drawer**, collapsed: alternate outcomes ("no kill", "star
   pass", "attack fails"), "show a card…", "skip this step (why?)", "how to
   run this", "undo the last thing I did on this step". Two taps for the
   uncommon, one for the common.

The collapsed list, above/below the active card, becomes one line per step:

```
  ✓ 1  Dusk                                   —
  ✓ 2  Poisoner        Gus        → Ben poisoned
  ✓ 3  Monk            Eve        → Fay safe
  ▶ 4  Imp             Hal              choosing
    5  Empath          Ben              answer: 1
    6  Fortune Teller  Cleo               2 picks
  ⊘ 7  Undertaker      Dan     nobody executed
    8  Butler          Fay              1 pick
    9  Dawn                        1 death
```

Left gutter: `✓` done · `▶` current (gold accent bar) · `⊘` skip · blank
pending. Right column: the *result* if done, the *ask* if pending. That is the
information density that is missing today (defect #24) and it makes the current
step unmistakable without relying on elevation (defect #25).

### C. Per-holder sub-panels

When `holders.size > 1` the card body becomes a stack of sub-panels, one per
holder, each with its own status, ask, info and card offers, and the primary
button walks them one at a time — which is exactly what "wake the Village
Idiots one at a time" and "wake all Minions" require.

```
║ VILLAGE IDIOT                    3 in play   ║
║ ┌──────────────────────────────────────────┐ ║
║ │ ✓ seat 1 · Ana          shown "EVIL"     │ ║  done, dim
║ ├──────────────────────────────────────────┤ ║
║ │ ▶ seat 4 · Dan                           │ ║  active, gold
║ │   ⚠ DRUNK (Village Idiot) — must be lied │ ║
║ │     to every night                       │ ║
║ │   chose:  [ 6 Fay ]  change              │ ║
║ │   truth: GOOD          → you must show   │ ║
║ │                          E V I L         │ ║
║ ├──────────────────────────────────────────┤ ║
║ │   seat 9 · Ivy                not woken  │ ║
║ └──────────────────────────────────────────┘ ║
║ ╭──────────────────────────────────────────╮ ║
║ │        SHOW "EVIL" TO DAN            →   │ ║
║ ╰──────────────────────────────────────────╯ ║
```

The same layout serves **Minion info** (one sub-panel per Minion: "shown the
other Minions ✓ / shown the Demon ✓"), **Demon info** (minions → marionette →
bluffs → lunatic, as four ticks inside one card), and any duplicated or
gained ability. `QuickResolutions` and the info block must both take a
`NightHolder`, never `step.playerIds.firstOrNull()` (defect #4).

### D. Cards: offers, not a catalogue

Replace the "guide chips → dialog → search → confirm" chain
(`NightScreen.kt:802-831`, `364-454`) with **pre-filled `CardOffer`s computed
by the engine**:

```kotlin
data class CardOffer(
    val label: String,        // button text: "SHOW: POISONER"
    val card: ShowCard,       // already populated — no picker needed
    val truthful: Boolean,    // false ⇒ rendered in the "lie" group
    val editable: Boolean,    // long-press to open the editor
)
```

- The Undertaker's offer is `CharacterCard("THIS CHARACTER WAS EXECUTED
  TODAY", "poisoner")` — **built from the computed answer** (defect #15). Same
  for the Imp's star-pass token, the Washerwoman's Townsfolk, the Grandmother's
  grandchild, the Godfather's Outsiders (one offer each), the Cerenovus's
  own token.
- **Tap = show the pre-filled card. Long-press = edit it.** The editor
  (today's `GuideShowDialog`) stays for Pixie/Cerenovus/free text, but stops
  being the default path.
- Add the missing card kinds to `ShowCard` (`ShowCards.kt:65-78`):

  ```kotlin
  data class PointCard(                    // ← the big one, defect #18
      val prefix: String,                  // "ONE OF THESE PLAYERS IS THE"
      val playerNames: List<String>,       // 1..3 names, 44-56sp
      val seatNumbers: List<Int>,
      val characterId: String? = null,     // optional token between the two
  ) : ShowCard
  data class MultiTokenCard(               // ← Dreamer, bluffs, Godfather
      val prefix: String,
      val characterIds: List<String>,      // wrapping FlowRow, fixes #33
  ) : ShowCard
  data class AlignmentCard(                // ← fixes #6
      val evil: Boolean?,                  // null = "neither" (General)
      val text: String = defaultFor(evil),
  ) : ShowCard
  ```

  `PointCard` is the app finally doing the job the wiki describes as "point to
  them… pointing vertically and downwards" — with the ST's hand still holding
  the phone. It is legal (the recipient is entitled to the information) and it
  removes the single biggest reason the phone gets put down mid-night.
- **False-info offers are generated for every info shape, not just digits and
  YES/NO** (defects #16, #17): a wrong character for the Dreamer/Undertaker/
  Ravenkeeper (pre-picked plausibly: a not-in-play character of the same team,
  or a bluff), the opposite direction for the Shugenja, a different pair for
  the Washerwoman, a full 0..(alive count) row for numbers. If the engine
  genuinely cannot generate a lie, **the heading must not render** — never a
  red heading over an empty row.
- Lies are visually separated and confirmed differently: truthful offers are
  gold, lie offers are ember with the word **LIE** in the button, so a fumble
  at 1 a.m. is visible before the card is up (this is
  `characters/villageidiot.md` #10's "the correct chip is the wrong one").
- The two classic info tokens belong on the steps that need them: Minion info
  gets `Message("THIS IS THE DEMON")` and `PointCard("THIS IS THE DEMON",
  ["Hal"], [8])`; Demon info gets "THESE ARE YOUR MINIONS" + `PointCard`
  (defect #19).

### E. Showing a card safely

```
      ┌────────────────────────────────┐
      │                                │
      │      ONE OF THESE PLAYERS      │   28sp
      │             IS THE             │
      │           ( EMPATH )           │   token 160dp
      │                                │
      │      A N A        F A Y        │   48sp
      │       seat 1       seat 6      │   14sp
      │                                │
      │                                │
      │ ╭────────────╮   ╭───────────╮ │
      │ │  ⟳ FLIP    │   │ HOLD ⌂    │ │  ← bottom edge only
      │ ╰────────────╯   ╰───────────╯ │
      └────────────────────────────────┘
```

- **Taps on the card body do nothing** (fixes defect #2). Exit is a
  press-and-hold on a bottom-edge control, reusing the 1.2 s gesture already
  proven in `PrivacyCover.kt:41-52`.
- **Releasing the hold lands on the privacy cover, not on the grimoire.** The
  ST turns the phone around and holds again. The night sheet is never visible
  while the phone is pointing away.
- **⟳ FLIP rotates the content 180°** for a card held out to a player sitting
  opposite. Currently every card is upside down to its intended reader.
- The card briefly overrides the night scrim to full brightness (this already
  happens for free — `FullScreenShow` is a `Dialog` and the scrim lives inside
  the Scaffold content, `GameShell.kt:322-330`) and restores the dim level on
  exit.
- A shown card writes a `NightRecord` ("Ben was shown: 1", "Dan was shown:
  EVIL (lie)") so the log and the next night's step can display it (defect
  #10).

### F. The night as a whole

- **Progress strip** (fixed, top): `NIGHT 2 · step 6 / 11` + segment bar
  showing done/skip/pending, tappable to open a full step list.
- **Prev / Next** live at thumb level as a slim bar under the primary button —
  `‹` steps back without unticking, `›` skips forward and marks the step
  `SKIP (deliberate)` rather than `DONE`, which is the distinction the dawn
  guard needs (defect #27).
- **Undo is per-step and named**: the secondary drawer's "undo: Fay's death"
  pops the exact `update` that this step applied, instead of a global unlabeled
  icon (defect #23). The global undo stays in the top bar.
- **The dawn guard becomes actionable** (defect #28): instead of a bullet list,
  it shows the outstanding steps as tappable rows that jump to the card, with
  `SKIP` steps excluded entirely, and the safe action ("keep checking") as the
  filled button.
- **Dusk and Dawn stop being checkboxes** (defect #8). Dusk becomes the screen
  header for step 1; Dawn becomes the primary button of the last card
  ("`OPEN THE DAY →`").
- **Dawn summary** — the missing screen (defect #9), shown when the last step's
  button is pressed, before the Day tab opens:

```
╔══════════════════════════════════════════════╗
║  DAWN · night 2                              ║
╠══════════════════════════════════════════════╣
║  SAY OUT LOUD                                ║
║   ▸ Fay died in the night.                   ║
║   ▸ Nobody else died.                        ║
║                                              ║
║  DO NOT SAY (your notes)                     ║
║   ▸ Eve was attacked; the Monk saved her.    ║
║   ▸ Ben is poisoned until dusk.              ║
║   ▸ Cleo was shown "1" (true).               ║
║                                              ║
║  SWEPT OFF THE GRIMOIRE AT DAWN              ║
║   ▸ Safe (Monk) from Eve                     ║
║                                              ║
║  DURING DAY 2, REMEMBER                      ║
║   ▸ Gus survives execution (Devil's Advocate)║
║   ▸ Dan is mad he is the Saint (Cerenovus)   ║
║   ▸ Ravenkeeper info is still owed to Fay    ║
║                                              ║
║ ╭──────────────────────────────────────────╮ ║
║ │          OPEN DAY 2              →       │ ║
║ ╰──────────────────────────────────────────╯ ║
╚══════════════════════════════════════════════╝
```

  Sources it already has: `state.deaths` filtered to `day == cycle &&
  atNight`, the `EXPIRES_AT_DAWN` / `EXPIRES_AT_DUSK` tables
  (`GameActions.kt:218-242`), reminders currently on seats, and the new
  `NightRecord`s. This is the one screen that turns the user's "the app should
  do the bookkeeping" complaint into something they can see.

### G. Night 1 vs later nights

- Different card stock: night 1 cards carry a small "FIRST NIGHT" rule and a
  warmer accent; the header reads `FIRST NIGHT` (already true,
  `NightScreen.kt:128`).
- Night 1 gets **preflight cards** at the front of the sheet instead of
  setup-time dialogs that can be dismissed forever (defect #29): "Demon bluffs
  — 3 needed, 0 chosen", "Fortune Teller red herring — not placed", "The Drunk
  believes they are…", "The Lunatic believes they are…". Each is a normal card
  with a picker and a primary button; each is `SKIP` once satisfied. `GameShell`
  keeps its setup prompts but they stop being the only chance.
- Steps whose *only* difference is first vs other night (Godfather, Grandmother,
  Snake Charmer, Cerenovus) already resolve through
  `NightGuide.forStep(id, cycle == 1)` (`NightScreen.kt:792`) — keep that, but
  drive `prompt`, `ask` and `cards` from it rather than dumping prose.

### H. Dark room, one hand, awake, no accidents

- **Brightness**: promote dimming to a first-class control on the progress
  strip (⏻), with three levels (100 / 55 / 25 %) persisted in `GameState`, not
  `rememberSaveable`. Move the scrim **outside** the Scaffold content so it
  also covers the top bar and navigation bar (defect #21), and keep it
  pointer-transparent. On Android additionally set
  `WindowManager.LayoutParams.screenBrightness`; on the PWA the scrim is the
  only lever, which is exactly why it must cover everything.
- **Red-shift**: the current `0x66300000` overlay is right in spirit; make the
  tint a token so the card's ember warnings are re-checked for contrast at each
  dim level. Do not let any run-book text below 14 sp survive the redesign
  (defect #24, #40): status banner 16 sp, prompt 18 sp, seat names 16 sp,
  primary button 18 sp.
- **Keep awake**: re-request the wake lock on `visibilitychange` and on
  `pageshow`, hold the sentinel, and re-request on release
  (`WebUiPlatform.kt:12-19`, defect #20). Surface a one-line warning on the
  night screen if the request was refused — better an honest "your phone may
  sleep" than a dark screen mid-night.
- **Accidental taps**:
  - The card body is **not** clickable; only its controls are (defect #22).
    Collapse/expand moves to an explicit chevron and to the ‹ › bar.
  - Destructive primaries (kill, star pass, resurrect, character change,
    "mark spent") use **press-and-hold-to-confirm** (400 ms, with a filling
    progress ring) rather than a tap (defect #23). Non-destructive primaries
    stay a single tap.
  - Minimum target 56 dp for anything on the primary path; the tick, the
    picker cells and the primary button all qualify.
  - "Mark spent" stops writing to every holder at once (`NightScreen.kt:266`)
    and becomes per-holder.
- **Privacy**: `PrivacyCover` becomes the *return path* from every card (§E)
  and gains a bottom-edge "one-handed" hold zone so the gesture works without
  a grip change. Remove the empty 64 sp `Text` at `PrivacyCover.kt:54`.
- **`ShowToolSheet` stops advertising what is in play** (defect #35): sort
  alphabetically by team, drop the "Characters in play are first" caption, and
  keep the in-play shortcut as a separate collapsed "suggested" group that is
  hidden by default.

### I. Data changes

- `night_guide.json`: add `DUSK`, `DAWN`, `MINION_INFO`, `DEMON_INFO` entries
  (defect #32) with real procedures and the classic info-token cards.
- `night_guide.json`: replace `"text": "…"` placeholders (clockmaker,
  mathematician) with a `kind: "number"` show so they produce a 220 sp
  `NumberCard`, not a typed 52 sp `BigText`.
- `night_guide.json`: every `token: "pick"` show that the engine can resolve
  gains `"prefill": "<source>"` (`info.target`, `info.character`,
  `state.demon`, `state.outsidersInPlay`) so `CardOffer`s can be built without
  a picker.
- `characters.json`: nothing required by this document; the terse
  `firstNightReminder`s that make collapsed rows useless (e.g. General's "Give
  a thumb signal.") are covered in the character audits.
- `GameState`: add `nightChoices: Map<NightKey, List<Long>>` and
  `nightRecords: List<NightRecord>` (shared with `characters/general.md` §C),
  plus `dimLevel: Int`.

### J. UI text for the screen (storyteller voice, imperative, short)

| Situation | Text |
|---|---|
| step header | `IMP · Hal · seat 8` |
| impaired holder | `POISONED by the Poisoner — the attack fails tonight.` |
| spent ability | `Already used — the Professor has no ability left.` |
| dead holder, skip | `Hal is dead. Nothing to do tonight.` |
| dead holder, still acts | `Hal is dead — but the Vigormortis keeps dead Minions' abilities. They still act.` |
| conditional | `Did an Outsider die today? The Godfather only wakes if one did.` |
| different from last night | `Not Ben — chosen last night.` |
| picker label | `WHO DID HAL CHOOSE?` / `WHICH TWO DID CLEO POINT AT?` |
| answer, true | `SHOW "1" TO BEN` |
| answer, lie | `LIE · SHOW "2" TO BEN` |
| no death | `EVE SURVIVES — NOBODY DIES` |
| nothing computed | `Your call — the app has no answer for this one.` |
| card exit | `Hold to close` |
| dawn | `OPEN DAY 2` |

---

## Tests to add

Engine-level, against the proposed `NightPlan` model, so the UI stays thin.
All of these fail today (most because the model does not exist; the ones
marked ⚑ fail against the current code as well).

1. **`step statuses are computed, not annotated`** — Given a BMR game with a
   dead Grandmother; When `NightOrder.plan(state, cycle = 2)`; Then the
   grandmother step's `status == SKIP` with `skipReason == "dead"`, and it is
   excluded from `plan.outstanding` (the dawn guard's list). ⚑ today the row
   is `ACT`-equivalent and blocks dawn (`GameShell.kt:147-161`).
2. **`vigormortis inverts the dead-minion skip`** — Given a Vigormortis in play
   and a dead Devil's Advocate; Then the devilsadvocate step's `status == ACT`
   and its holder banner says the ability still works. ⚑ today
   `NightScreen.kt:751-757` says "usually skip".
3. **`every holder gets its own panel`** — Given three Village Idiots, one
   marked drunk (seat 2 of the three); Then `step.holders.size == 3`, each has
   its own `ask`, and holder 2's `impairment` is non-null. ⚑ today
   `NightScreen.kt:837` and `:467` use `firstOrNull()`.
4. **`choices are keyed by cycle`** — Given the Fortune Teller chose seats 3
   and 8 on cycle 2; When the plan is built for cycle 3; Then
   `holder.ask` has no pre-selection and `holder.info == null` until a choice
   is made, while `state.nightChoices[NightKey(2, "fortuneteller", id)]` still
   returns the old pair. ⚑ today `NightScreen.kt:839` restores it as if it
   were current.
5. **`different-from-last-night is enforced by the picker`** — Given the
   Exorcist chose seat 4 on cycle 2; Then the cycle-3 `NightAsk.Players` has
   `differentFromLastNight == seat4Id`.
6. **`confirming a resolution marks the step done`** — Given the Imp step with
   a chosen target; When the primary action is applied; Then
   `state.nightStepsDone` contains `"imp"`, a `NightRecord` exists for
   `(cycle, "imp", holderId)`, and `plan.nextActionable` is the following
   `ACT` step. ⚑ today the tick is a separate manual act.
7. **`dusk and dawn are not checklist items`** — Then `plan.steps` contains no
   step whose completion is required and whose `ask == None && cards.isEmpty()`
   for `DUSK`/`DAWN`; the dawn guard's outstanding list never contains them.
   ⚑ today `NightOrder.kt:58-59` + `GameShell.kt:153-155` require both.
8. **`dawn summary names the dead`** — Given Fay killed by the Demon on
   cycle 2 and Eve saved by the Monk; Then `plan.dawn.announce == ["Fay died
   in the night."]` and `plan.dawn.private` contains the Monk save and the
   Monk token being swept. ⚑ today `NightOrder.kt:59` says "Announce who
   died" and names nobody.
9. **`card offers are pre-filled from computed info`** — Given an execution of
   the Poisoner on day 1; Then the cycle-2 Undertaker holder's `cards`
   contains a `CharacterCard` whose `characterId == "poisoner"` with no picker
   step. ⚑ today `NightScreen.kt:375-377` leaves `tokenId` null.
10. **`alignment cards carry their own text`** — Given
    `GuideShow(kind = "good", text = "GOOD IS WINNING")`; Then the offer's
    card renders that text, not "YOU ARE GOOD". ⚑ (duplicate of
    `characters/general.md` test 1 — the fix lives in this file.)
11. **`a lie is offered for every info shape, or the heading is suppressed`** —
    Given a poisoned Dreamer who chose seat 5; Then `holder.cards` contains at
    least one `truthful = false` offer naming a plausible wrong character;
    Given a holder for whom no lie can be generated; Then no `truthful = false`
    offers exist **and** `holder.lieHeadingVisible == false`. ⚑ today
    `NightScreen.kt:907-929` renders the heading with an empty row.
12. **`false numbers span the legal range`** — Given a poisoned Empath in a
    15-player game whose true answer is 2; Then the lie offers include 0..2+
    beyond 4. ⚑ today `NightScreen.kt:914-921` stops at 4.
13. **`point cards name seats`** — Given a Washerwoman whose Townsfolk is
    seat 2 and whose decoy is seat 6; Then the offer is a
    `PointCard(prefix = "ONE OF THESE PLAYERS IS THE", names = ["Ben","Fay"],
    seats = [2,6], characterId = "empath")`. ⚑ `ShowCard` has no such variant.
14. **`washerwoman-style info excludes the holder`** — Given a Washerwoman and
    four other Townsfolk; Then the computed candidate list omits the
    Washerwoman's own seat. ⚑ `InfoCalc.kt:408-421` includes it.
15. **`multi-copy placement never silently steals`** — Given both `Drunk`
    copies placed and a third placement requested; Then the action is rejected
    with a message naming the seat that would lose its token. ⚑
    `NightScreen.kt:331-337` removes `placed.first()` silently.
16. **`skip is distinguishable from done`** — Given the ST skips the
    Grandmother deliberately; Then `nightStepsSkipped` contains it,
    `nightStepsDone` does not, and the log records "N2 · Grandmother skipped".
17. **`night 1 preflight blocks nothing but is visible`** — Given no demon
    bluffs chosen; Then the first-night plan's first step is the bluffs
    preflight with `status == ACT`, and it becomes `SKIP` once three bluffs
    exist.
18. **`records survive the phase change`** — Given cards shown on cycle 2;
    When `advancePhase` runs twice; Then `state.nightRecords` still holds them
    and `GameLogDialog` renders them under `N2`. ⚑ today nothing is recorded
    and `GameActions.kt:258-263` clears the only per-night state there is.
