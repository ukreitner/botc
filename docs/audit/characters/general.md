# General (general) — experimental (Carousel) townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/General>

Current ability text (verbatim):

> "Each night, you learn which alignment the Storyteller believes is winning:
> good, evil, or neither."

`characters.json:1384` matches exactly.

### How to run (verbatim)

> "Each night, wake the General. If you believe that the good team is winning,
> give a thumbs up. If you believe that the evil team is winning, give a thumbs
> down. If you don't know which team is winning, give a thumbs to the side. Put
> the General to sleep."

### Summary / clarifications (verbatim)

- "If the good team is winning, the Storyteller gives a thumbs up. If the evil
  team is winning, the Storyteller gives a thumbs down. If neither team is
  winning, **or the Storyteller isn't sure**, the Storyteller gives a thumbs to
  the side."
- "The Storyteller is the judge on which team is winning. Many factors may be
  included, such as how many players of each team are still alive, how much
  information the good team has, how successful the evil team's bluffs seem to
  be, which players the group wants to execute next, or how experienced the
  Demon player is."
- "The Storyteller decides who is winning **at the point that the General
  wakes**. Previous events in the night may affect their decision."

### Examples (verbatim)

1. "There are 5 good players alive and 4 evil players alive. Even though the
   Demon is very suspicious and will probably be executed next, there is a
   Scarlet Woman in play, who is very trustworthy. The Storyteller gives a
   thumbs down."
2. "The Good team has a lot of information, and believes that their false
   information is indeed false. The only Minion is dead. The Storyteller gives
   a thumbs up."
3. "The Po is a very experienced player and is coordinating well with the
   Minions. The Monk is successfully protecting the Savant each night and the
   good team have correctly identified several good players. However, the Po
   will probably kill 3 times tomorrow night, so it is anyone's game. The
   Storyteller gives a thumbs to the side."

### Timing

- **Every night, including the first.** The app agrees (first-night index 69,
  other-night index 92), matching the reference ordering (`townsquare`
  `roles.json`: general 50 / 69, cultleader 48 / 66, chambermaid and
  mathematician after).
- Late in the night, after the Butler and Spy, before the Chambermaid — so the
  answer accounts for tonight's kills, which is exactly what "at the point that
  the General wakes" requires.

### Jinxes

None.

### Not settled by the wiki (flagged)

- Drunk/poisoned General. The page is silent. The app's `night_guide` asserts
  "you may give any signal", which is the standard convention for a judgement
  ability (there is no "true" answer to invert) — flagged as convention.

## What the app does today

Works, in one line each:

- Night-order position on both nights (`night_and_jinxes.json:364` first,
  `:465` other).
- `night_guide.json:978` has `first` and `other` instructions covering the
  three signals, the "base it on your honest read" guidance and the impairment
  convention.
- `characters.json:1384` carries the (terse but official) reminder "Give a
  thumb signal." on both nights, and no reminder tokens — correct.

Storyteller experience:

- A "General" row appears every night near the end of the sheet, detail text
  "Give a thumb signal."
- Expanding it prints the guide paragraph and three chips: `» Good winning`,
  `» Evil winning`, `» Neither`.
- `InfoCalc.supports` does not include `"general"` (`InfoCalc.kt:29-36`), so the
  panel computes nothing, shows no caveats and offers no false-info
  affordances.
- The storyteller decides in their head and gives a thumb, or taps a chip.

## Defects and gaps

1. **P0 · The "Good winning" / "Evil winning" chips show the player a card
   that says "YOU ARE GOOD" / "YOU ARE EVIL".**
   `night_guide.json:978` defines these shows with `kind: "good"` / `"evil"`
   and `text: "GOOD IS WINNING"` / `"EVIL IS WINNING"`. But
   `NightScreen.kt:806-816` ignores `show.text` for those two kinds and routes
   straight to `ShowCard.AlignmentCard(evil = …)`, and
   `ShowCards.kt:107-126` renders that as a giant `GOOD` / `EVIL` above the
   line `"YOU ARE GOOD"` / `"YOU ARE EVIL"`.
   Repro: Carousel game, night 1, expand the General, tap `» Good winning` —
   the full-screen card handed to the player reads **YOU ARE GOOD**. That is a
   different ability (it is the Cult Leader's / Snake Charmer's card) and it
   tells the General a fact about themselves instead of the answer to their
   ability. On a phone-first PWA this card is the primary delivery mechanism.
   **Cross-cutting**: the same defect corrupts the Village Idiot
   (`text: "THIS PLAYER IS GOOD"` → renders "YOU ARE GOOD"), the Evil Twin
   (`text: "This player is EVIL"` → "YOU ARE EVIL") and the Mezepheles
   (`"YOU ARE NOW EVIL"` → "YOU ARE EVIL"). Only the Snake Charmer, Fang Gu and
   Cult Leader happen to want the literal card they get.

2. **P1 · No impairment warning anywhere on the step.** `StepDetailPanel`
   renders caveats only inside `if (InfoCalc.supports(step.id))`
   (`NightScreen.kt:836`), and `"general"` is not supported. A poisoned or
   drunk General therefore gets no red flag at all; the only mention is a
   sentence at the end of a prose paragraph in `night_guide.json`. Repro:
   poison the General, open their night step — nothing is highlighted.

3. **P1 · No decision support, though the app holds every input the wiki
   lists.** The wiki's factors map almost one-to-one onto state the app
   already has: alive good vs alive evil (`Player.isEvil`), which Minions and
   Demons are dead (`state.deaths` + `characterIdAtDeath`), who is on the
   block (`GameActions.aboutToDie`), who is currently impaired
   (`StatusEffects.isImpaired`), and how much of the good team's info was false
   (derivable from the impairment history). None of it is surfaced at the one
   moment the storyteller has to make this judgement — at 2am, ten steps deep
   in a night sheet.

4. **P1 · No record of what was signalled.** The General's information is a
   *series*; the player reads the trend. The storyteller needs last night's
   answer to stay coherent, and needs it again if the Cannibal later gains the
   General's ability. Nothing is recorded: `GameLogDialog`
   (`GameExtras.kt:46-106`) logs only deaths and nominations, and
   `state.storytellerNotes` (`GameShell.kt:684-705`) is one undated blob.

5. **P2 · The three chips are inconsistent in cost.** `» Good winning` and
   `» Evil winning` are one tap (to the wrong card); `» Neither` is
   `kind: "message"`, so it opens `GuideShowDialog` (`NightScreen.kt:364-454`)
   with an editable text field and a confirm button — two taps and a keyboard
   risk, for the answer the storyteller reaches for most often when unsure.

6. **P2 · A dead General still gets a night row.** `NightOrder.build`
   (`NightOrder.kt:142-178`) does not filter by `alive`; the row is only
   annotated "All holders are dead — usually skip"
   (`NightScreen.kt:751-757`), and the dawn guard (`GameShell.kt:145-158`)
   still demands it be ticked.

7. **P2 · The step detail is uselessly terse and duplicated.** The row's
   `detail` is `characters.json`'s "Give a thumb signal."
   (`NightOrder.kt:147-148`), which says nothing about *which* signal or what
   the question is; the useful text is one expand away.

8. **P3 · `first` and `other` guide entries are near-identical**, differing
   only by a sentence. Harmless, but they will drift.

## Proposed behaviour (spec)

### A. Fix the show-card routing (cross-cutting, but blocking here)

`NightScreen.kt:806-816` must honour `show.text`:

```
"good" -> onShow(ShowCard.BigAlignment(evil = false, text = show.text))
"evil" -> onShow(ShowCard.BigAlignment(evil = true,  text = show.text))
```

i.e. extend `ShowCard.AlignmentCard` with a `text: String` (defaulting to
`"YOU ARE GOOD"/"YOU ARE EVIL"` so existing call sites are unchanged) and have
`ShowCards.kt:107-126` render `card.text` as the subtitle under the big
GOOD/EVIL word. For the General the card then reads

```
        GOOD
  GOOD IS WINNING
```

Add a third alignment-ish card for "neither": a big `—` (or "NEITHER") in a
neutral colour with the subtitle `NEITHER TEAM IS WINNING`, so all three
answers cost one tap and look like siblings.

### B. Night step (structured form)

- **when**: `first` and `other` — every night.
- **wake condition**: the General is **alive**. Emit no row for a dead
  General.
- **targets**: none.
- **immediate effects**: none. No tokens.
- **deferred effects**: none.
- **expiry**: none.
- **information**: this is a judgement, not a computation, so `InfoCalc` should
  **not** invent an answer. Instead the step renders a *dashboard* the
  storyteller reads:
  - `Alive: 6 good / 3 evil` (by `isEvil`, so a turned Cult Leader counts
    correctly);
  - `Evil dead: Poisoner (executed D2). Evil alive: Imp, Baron.`
  - `Good dead: 4 — Empath (N2), Chef (D1), …`
  - `On the block yesterday: nobody / <name> (survived)`;
  - `Impaired good players tonight: Ben (Poisoner), Eve (is the Drunk)` — the
    proxy for "how much of the good team's info is false";
  - `Demon bluffs still unclaimed: Soldier, Librarian` (from
    `state.demonBluffIds` vs recorded day claims, if the claims store from the
    Gossip/claims audit lands);
  - `Last night you signalled: EVIL WINNING (N2), NEITHER (N1)`.
  Presented as a compact read-only block, explicitly labelled *"Your call —
  these are just the facts."* The app must not suggest an answer; the wiki
  makes the storyteller the judge.
- **impaired alternative**: when `isImpaired(general)` is true, head the step
  `"The General is DRUNK/POISONED — you may give any signal."` and mark the
  three chips as free choices rather than the honest answer. (Because there is
  no true answer to invert, the existing numeric/YES-NO false-info UI at
  `NightScreen.kt:903-930` is not applicable.)
- **visibility**: nothing shown to the Demon, Minions or anyone else.
- **day-time inputs consumed**: none required; the day claims store, if it
  exists, feeds the "bluffs still unclaimed" line.
- **interactions/jinxes**: none.

### C. Recording the answer

Add a general facility (also needed by the High Priestess and Cult Leader):

```
data class NightRecord(val cycle: Int, val stepId: String, val playerId: Long?, val text: String)
val nightRecords: List<NightRecord> = emptyList()   // on GameState
```

Tapping a General chip writes
`NightRecord(cycle, "general", generalSeatId, "GOOD IS WINNING")`, shows the
card, and ticks the step. Render the last 3 records inline on the step, and
add them to `GameLogDialog` as `N2 · General was shown: EVIL WINNING`.

### D. UI text for the step

- Title: `General` · detail
  `Which team do you believe is winning right now? Thumbs up / down / sideways.`
- Chips: `Good is winning` · `Evil is winning` · `Neither / not sure`
- Under the chips: `Last night: EVIL WINNING`
- Impaired: `The General is poisoned by the Poisoner — any signal is fine.`
- Dead: no row.

### E. Data changes

- `characters.json:1384`: replace the bare `firstNightReminder` /
  `otherNightReminder` "Give a thumb signal." with the official longer form
  used by the reference dataset — "Show the General thumbs up for good
  winning, thumbs down for evil winning or thumb to the side for neither." —
  so the collapsed row is useful on its own.
- `night_guide.json:978`: add a third show for "neither" as a first-class
  card kind rather than `kind: "message"`; keep the prose; make the `first`
  entry mention that night 1 answers are usually "neither".
- `night_and_jinxes.json`: no change.

## Tests to add

1. `general show card carries its own text`
   Given `GuideShow(label = "Good winning", kind = "good", text = "GOOD IS
   WINNING")`; When the chip is activated; Then the produced `ShowCard`
   renders the subtitle `"GOOD IS WINNING"`, not `"YOU ARE GOOD"`. (Pure-engine
   version: assert the mapping function returns a card whose text equals the
   guide's `text`; today the mapping discards it.)

2. `village idiot and evil twin cards are not corrupted by the same path`
   Same assertion for `villageidiot` ("THIS PLAYER IS GOOD") and `eviltwin`
   ("This player is EVIL"). Regression guard for the cross-cutting fix.

3. `general wakes on the first night`
   Given a living General; Then `nightOrder.firstNight(...)` contains a
   `"general"` step, positioned after the Spy's slot and before the
   Chambermaid's.

4. `dead general gets no step`
   Given a dead General; Then neither night sheet contains a `"general"` step.

5. `general dashboard counts a turned cult leader as evil`
   Given 6 good players, a Cult Leader with `alignmentFlipped = true`, and 2
   evil; Then the dashboard reports `5 good / 3 evil`.

6. `general dashboard reflects tonight's kill`
   Given the General's step evaluated after the Demon's step killed a
   Townsfolk on cycle 3; Then the alive-good count excludes that player
   (guards "the Storyteller decides at the point that the General wakes").

7. `impaired general is flagged on the step`
   Given a General with a `("poisoner","Poisoned")` reminder; Then the step's
   caveats contain a POISONED entry. (Fails today: caveats are gated behind
   `InfoCalc.supports`.)

8. `signals are recorded per night`
   Given signals on nights 1 and 2; Then `state.nightRecords` contains two
   `stepId == "general"` entries with the right cycles and texts, and the
   night-3 step surfaces the night-2 text.
