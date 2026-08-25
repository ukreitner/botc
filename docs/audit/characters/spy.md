# Spy (spy) — Trouble Brewing Minion

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Spy> (fetched 2026-08-25).

Current ability text:

> "Each night, you see the Grimoire. You might register as good & as a Townsfolk
> or Outsider, even if dead."

How to run — verbatim:

- *"Each night, wake the Spy and show them the Grimoire for as long as they
  need."* Both first night (order index 66) and other nights (index 90), near the
  very end of the order just before Dawn.
- The Spy sees **everything**: *"In the Grimoire, you will not only see who
  everyone is, but the Storyteller reminder tokens."*
- Misregistration: the Storyteller chooses, **each time**, whether the Spy
  registers as good/evil and as which Townsfolk or Outsider.
  *"A Spy that registers as a particular Townsfolk or Outsider does not have this
  character's ability."*
- The misregistration continues **after death** — "even if dead" — although the
  *seeing the Grimoire* half is a night ability and so stops when the Spy dies
  (dead players do not act).
- The wiki does not state what to do for a drunk/poisoned Spy. Common Storyteller
  practice is to show a doctored Grimoire or nothing at all; **the app should not
  assert a rule here**, only prompt the Storyteller to decide.

Which abilities the misregistration can touch, in Trouble Brewing: Washerwoman,
Librarian, Investigator, Chef, Empath, Fortune Teller (as *not* the Demon —
the Spy is not a Demon, but the Spy can register as good so a "Demon?" question
is unaffected; the relevant one is that the Spy registers as good to Empath /
Chef / Undertaker), Undertaker, Ravenkeeper, Virgin (a Spy nominating the Virgin
may register as a Townsfolk and be executed), Slayer (a Spy is not the Demon so
the shot fails regardless), Saint/Recluse interactions, and the Gossip/Godfather
"Outsider died" family on wider scripts.

Jinxes (from `night_and_jinxes.json`):
- `spy` × `damsel`: "If the Spy is (or has been) in play, the Damsel is
  poisoned."
- `spy` × `heretic`: "Only 1 jinxed character can be in play."
- `spy` × `magician`: "When the Spy sees the Grimoire, the Demon's and Magician's
  character tokens are removed."
- `spy` × `poppygrower`: "If the Poppy Grower is in play, the Spy does not see
  the Grimoire until the Poppy Grower dies."

## What the app does today

Data
- `characters.json` — `spy`: ability text matches the wiki. `reminders: []`.
  `firstNightReminder` = `otherNightReminder` = "Show the Grimoire to the Spy for
  as long as they need."
- `night_and_jinxes.json` — `firstNight[66]`, `otherNight[90]`. Correct
  (immediately before `ogre`/`highpriestess`/`general`/`chambermaid`/
  `mathematician` and Dawn).
- `night_guide.json` — `spy.first` and `.other` both present, accurate, and both
  restate the misregistration rule. No show cards.

Engine
- `InfoCalc.kt:121-130` — the **only** Spy logic in the entire codebase:
  ```kotlin
  "spy" -> notes += "${ctx.name(p)} is the Spy — may register as good / a Townsfolk or Outsider."
  ```
  appended as a caveat by whichever calculators pass the Spy into
  `misregistrations(...)`.
- Which calculators do pass it: `chef` (all seats), `empath` (alive neighbours),
  `clockmaker` (all), `shugenja` (all), `oracle` (dead), `undertaker` (the
  executed player), `towncrier` (today's nominators), `flowergirl` (demons only —
  so never the Spy), `fortuneteller` (the 2 chosen), `dreamer` (target),
  `seamstress` (2 chosen), `villageidiot` (target), `revealCharacter`
  (ravenkeeper/grandmother target), `startKnowing` (all seats), `knight`,
  `steward`, `noble`, `balloonist` (**not** passed).

UI
- Nothing. There is **no** "show the Grimoire to the Spy" flow. The night step is
  a plain row with the `night_guide` prose and a tool tray with no reminders.
- `GameShell.kt:186-188` has a "Hide the grimoire" (`PrivacyCover`) button — the
  opposite of what the Spy step needs.
- `NightScreen.kt:836` — `InfoCalc.supports("spy")` is false, so no info panel.

Storyteller experience: the Spy row says "Show the Grimoire to the Spy for as
long as they need." and the Storyteller must, by hand: switch to the Grimoire
tab, hand a phone that has live editing controls, undo/redo, End game, Bluffs,
and the Log to an evil player in a dark room, hope they do not tap anything, and
then take it back and switch to the Night tab and tick the step.

## Defects and gaps

1. **P1 · There is no "show the Grimoire to the Spy" flow at all.**
   Rules require showing the full Grimoire. App: the Storyteller hands over an
   editable app. `SeatSheet` is one tap away from every seat
   (`GrimoireScreen.kt:370` `clickable(onClick = onClick)`), and the top bar
   exposes Undo/Redo (`GameShell.kt:189-194`). Repro: night 1, Spy step, hand the
   phone over → the Spy can change any character, kill anyone, or read the
   Storyteller's private notes.

2. **P1 · The Grimoire the Spy would see is missing the Spy-relevant
   information, and shows Storyteller-only information.**
   `GrimoireScreen.kt:373-386` colours **evil player names in ember red** — a
   Storyteller convenience the Spy is entitled to see, so that is fine — but the
   same screen shows `player.note` only in the seat sheet, and the Bluffs sheet
   and `storytellerNotes` are reachable from the shell. Conversely, only the last
   2–4 reminders per seat are rendered (`GrimoireScreen.kt:459,476-491`,
   `visibleReminders` = 2 or 4 with a "+N" overflow), so the Spy would **not**
   actually see all the reminder tokens the rules say they see.

3. **P1 · Two jinxes that change what the Spy is shown are unimplemented.**
   - `spy` × `magician`: the Demon's and Magician's tokens must be **removed**
     from the Grimoire before showing it.
   - `spy` × `poppygrower`: the Spy sees **nothing** until the Poppy Grower dies.
   Both are only static text in `SeatSheet.kt:222-235`. Repro: run a script with
   the Poppy Grower and the Spy; the app happily instructs "Show the Grimoire".

4. **P1 · Misregistration is a caveat string, never a decision the app records.**
   `InfoCalc.kt:125` produces one sentence. The Storyteller cannot:
   - record "tonight the Spy registers as the Chef to the Washerwoman";
   - see, next night, what they chose last time (consistency matters enormously
     for Spy bluffs);
   - get the *alternative* computed answer (e.g. Chef pairs if the Spy registers
     good, Empath count if the Spy registers good).
   `chef` (`InfoCalc.kt:186-205`) and `empath` (`:207-216`) compute only the true
   alignment and then append a warning.

5. **P1 · `startKnowing` does not offer the Spy as a candidate.**
   `InfoCalc.kt:408-421`: the Washerwoman/Librarian/Investigator calculator lists
   only players whose *true* team matches. The single most common Spy play — "the
   Spy registers as the Librarian's Outsider" or "as the Washerwoman's
   Townsfolk" — is not offered anywhere; if there is genuinely no Outsider, the
   app says *"No Outsider in play — show the 0 signal"* with no hint that the Spy
   can be shown instead. Repro: 8-player game, no Outsider, Spy in play, open the
   Librarian step.

6. **P2 · The Undertaker of an executed Spy gets the true token with no
   alternative.** `InfoCalc.kt:281-293` sets `headline = "Show: Spy"` plus the
   caveat. The Storyteller has to build the lie by hand.

7. **P2 · The Virgin nomination warning ignores Spy misregistration.**
   `StatusEffects.kt:153-157` says "if `<nominator>` is a Townsfolk, they are
   executed immediately" and does not flag that a Spy nominator **may** register
   as a Townsfolk, which is a Storyteller choice with real game consequences.

8. **P2 · A dead Spy still shows the "Show the Grimoire" step.**
   `NightOrder.kt:142-148` emits the row with an "All holders are dead — usually
   skip" annotation (`NightScreen.kt:751-757`). Correct-ish, but the *first* half
   of the ability stops on death while the *second* half continues; the app never
   says the misregistration survives death, on the night sheet or anywhere else.

9. **P2 · Nothing tells the Storyteller a drunk/poisoned Spy needs a decision.**
   The Spy is not in `InfoCalc.supports`, so `InfoCalc.impairments` is never
   consulted for the Spy step; a poisoned Spy gets the plain "Show the Grimoire"
   instruction.

10. **P3 · `spy` × `damsel` ("the Damsel is poisoned") is not auto-applied**;
    no `Poisoned` token is placed on a Damsel when a Spy is in play.

## Proposed behaviour (spec)

### Night step
- **when:** first **and** other nights; wake condition = holder is **alive**, and
  no alive `poppygrower` is in play (jinx). If a Poppy Grower is alive, replace
  the step body with "Poppy Grower is alive — the Spy does **not** see the
  Grimoire tonight." and let the step be checked off.
- **targets:** none.
- **immediate effects:** none to game state. One primary action:
  **"Show the Grimoire to the Spy"**, which opens a dedicated full-screen
  **Spy view**:
  - read-only: no seat taps, no reminder editing, no top bar, no tabs;
  - shows every seat with its true character token, true alignment colouring,
    alive/dead, and **all** reminder tokens (no `+N` truncation);
  - hides Storyteller-private material: `storytellerNotes`, the demon-bluff
    sheet, the game log, per-seat `note` text (a Storyteller scratchpad, not a
    grimoire token);
  - Magician jinx: when an alive `magician` is in play, omit the Demon's and the
    Magician's character tokens (render as face-down);
  - one large "Done — return to the night sheet" button that re-arms
    `PrivacyCover`.
- **deferred effects:** none.
- **expiry:** none.
- **information:** none computed.
- **visibility:** the Spy sees the grimoire; nobody else sees anything.
- **day-time inputs:** none.
- **interactions/jinxes:** Poppy Grower (suppress), Magician (redact), Damsel
  (auto-poison the Damsel for the whole game when a Spy is or has been in play),
  Heretic (setup-time conflict, surface at bag validation).

### Misregistration engine (shared with the Recluse)

Add a first-class, recorded misregistration so the Storyteller inputs a decision
once and the app keeps it consistent:

```kotlin
// GameState.kt
@Serializable
data class Misregistration(
    val playerId: Long,
    /** Night/day cycle this decision was made on. */
    val cycle: Int,
    /** Which ability asked, e.g. "washerwoman" or "empath". */
    val askedBy: String,
    /** What the Storyteller decided they registered as. */
    val asCharacterId: String?,   // null = "as their real character"
    val asEvil: Boolean,
)
// GameState gains: val misregistrations: List<Misregistration> = emptyList()
```

`InfoCalc` changes:
- `misregistrations(ctx, relevant)` keeps producing the warning text, but each
  affected calculator additionally returns
  `alternatives: List<InfoResult>` — the answer under each plausible
  registration. Concretely:
  - `chef`: pairs computed with the Spy good and with the Spy evil;
  - `empath`: count with the Spy good and with the Spy evil;
  - `startKnowing`: add the Spy to the candidate list for `TOWNSFOLK` and
    `OUTSIDER`, labelled "Spy — may register as this";
  - `undertaker` / `revealCharacter`: offer any character token, with the
    in-play good ones surfaced first;
  - `oracle`, `seamstress`, `villageidiot`, `shugenja`, `clockmaker`, `knight`,
    `steward`, `noble`: same alternative-answer treatment.
- The UI records the Storyteller's choice into `misregistrations` and, on later
  nights, prefixes the caveat with "Last time you registered the Spy as
  `<X>` (night N)."

### `StatusEffects.nominationWarnings` change
Add: when `nominator.characterId == "spy"` and the nominee is an un-impaired
Virgin, emit "`<nominator>` is the Spy — you may choose whether they register as
a Townsfolk. If they do, they are executed immediately."

### UI text for the step
- Title: "Spy — show `<name>` the Grimoire"
- Body: "Hand the phone over. They see every character and every reminder token,
  for as long as they need."
- Poppy Grower alive: "Poppy Grower is alive — the Spy sees nothing tonight."
- Magician in play: "The Demon's and the Magician's tokens are hidden."
- Footer (every night): "Remember: the Spy may register as good, and as any
  Townsfolk or Outsider — even after they die."

### Data changes
- `night_guide.json` `spy.first` / `spy.other`: add the two jinx clauses and the
  sentence "A Spy that registers as a Townsfolk does not gain that character's
  ability."
- No `characters.json` or night-order changes.

## Tests to add

1. `Given` a Spy and an alive Poppy Grower, `When` the night sheet is built,
   `Then` the `spy` step's detail says the Spy does not see the Grimoire.
2. `Given` a Spy and an alive Magician, `When` the Spy view's redaction set is
   computed, `Then` it contains the Demon's and the Magician's player ids.
3. `Given` an 8-seat game with no Outsider and a Spy in play, `When`
   `InfoCalc.compute("librarian", …)`, `Then` the result offers the Spy as a
   possible Outsider (alternative), not just "No Outsider in play".
4. `Given` seats `[imp, poisoner, washerwoman, empath, chef, recluse, spy,
   mayor]`, `When` `InfoCalc.compute("chef", …)`, `Then` `alternatives` include
   the pair count with the Spy registering good.
5. `Given` an Empath adjacent to the Spy, `When` `InfoCalc.compute("empath", …)`,
   `Then` `alternatives` include the count with the Spy good.
6. `Given` a dead Spy, `When` `InfoCalc.compute("undertaker", …)` for a night
   after the Spy was executed, `Then` the caveat still says the Spy may register
   as a Townsfolk/Outsider (misregistration survives death).
7. `Given` a Spy nominating an un-impaired Virgin, `When`
   `nominationWarnings` is computed, `Then` it mentions the Storyteller's choice.
8. `Given` a Damsel and a Spy in the same bag, `When` the first night starts,
   `Then` the Damsel holds a poison marker.
