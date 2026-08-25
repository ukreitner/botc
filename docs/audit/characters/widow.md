# Widow (widow) — exp (Carousel) minion

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Widow>

Current ability text:

> "On your 1st night, look at the Grimoire & choose a player: they are poisoned.
> 1 good player knows a Widow is in play."

`characters.json:1905` matches this text exactly — **no drift**.

### How to run (wiki, verbatim, in order)

- "While preparing the first night, mark a good player with the **KNOW** reminder."
- "On the first night, wake the Widow and show them the Grimoire for as long as they need."
- "The Widow points to a character token in the Grimoire."
- "Mark them with a **POISONED** reminder."
- "They are **poisoned**."
- "Put the Widow to sleep."
- "Wake the player marked **KNOW**."
- "Show them the Widow character token."
- "Put that good player to sleep."

### Key clarifications

- "One good player knows a Widow is in play, but not which player is the Widow, and not which player is poisoned."
- The poison is **open-ended**: "The poisoned player is poisoned until the Widow dies." If the Widow dies, "the poisoned player is no longer poisoned."
- The poison **pauses while the Widow's own ability is off**. Example, verbatim: "The Empath is poisoned due to the Widow. The Widow becomes drunk due to the Innkeeper. The Empath is no longer poisoned. The Innkeeper dies. The Widow is now sober and the Empath is poisoned again."
- "The Widow sees the Grimoire on their first night only" — i.e. once per game, on **their** first night (which is night 1 normally, or the night they enter play).
- Created mid-game: "On the third night, the Pit-Hag turns themselves into the Widow. That night, the good Scapegoat learns that a Widow is in play." So a mid-game Widow runs the whole step (grimoire + poison + KNOW) that night.
- Example: "The Widow sees the Grimoire and points to the Sailor. The Sailor is poisoned this game. The Sailor is sober, but dies when executed." — i.e. the poison switches off the Sailor's "you can't die".

### Jinxes (wiki)

| Partner | Text | In app data? |
|---|---|---|
| Alchemist | "An Alchemist-Widow has no Widow ability & a Widow is in play." | **missing** |
| Damsel | "If the Widow is (or has been) in play, the Damsel is poisoned." | yes, `night_and_jinxes.json:39` |
| Heretic | "Only 1 jinxed character can be in play." | yes, `:69` |
| Magician | "When the Widow sees the Grimoire, the Demon and Magician's character tokens are removed." | yes, `:139` |
| Poppy Grower | "If the Poppy Grower has their ability, the Widow does not see the Grimoire." | yes but **stale text**, `:149` |

## What the app does today

Data paths:
- `characters.json:1905` — text, reminders `["Poisoned","Know"]`, `firstNightReminder` matching the official wording, empty `otherNightReminder`.
- `night_and_jinxes.json:323` — firstNight index 28, between `poisoner` (27) and `courtier` (29). Correct position. **Absent from the otherNight list.**
- `night_and_jinxes.json:39, 69, 139, 149` — Damsel, Heretic, Magician, Poppy Grower jinxes.
- `night_guide.json:1480` — a `first` entry only. Prose covers showing the Grimoire ("cover any reminders you must hide, e.g. a fellow Widow's Know token"), the Poisoned token, "they stay poisoned as long as the Widow lives", and waking the KNOW player. One show card: `THIS CHARACTER IS IN PLAY` with `token: "self"`, i.e. the Widow token — correct.
- `raw_exp_evil_outsiders.json:303` — raw import copy.

No `widow` reference exists in `engine/src/main/kotlin` or `app/src`.

Storyteller experience:
1. Setup: nothing prompts for the KNOW token. The Fortune Teller has a dedicated pre-night-1 prompt (`GameShell.kt:347-376`), the Drunk (`:377-413`), the Lunatic (`:415-440`) and the Marionette (`:442-479`) each have one; the Widow does not.
2. Night 1: the step renders the official prose plus the guide. To show the Grimoire the Storyteller just hands over the phone on the Grimoire tab — there is no "show grimoire" mode, no redaction of the tokens the jinxes require hiding, and `PrivacyCover` (`GameShell.kt:344-346`, `components/PrivacyCover.kt`) only hides the grimoire, it does not present a filtered view.
3. Poison: place `Poisoned` from the tray (`NightScreen.kt:224-295`); because `widow`'s reminder list contains one `"Poisoned"`, `availableCopies <= 1` so `placeExclusiveReminder` is used (`NightScreen.kt:264-265`) — the token correctly moves rather than accumulating. `StatusEffects.isImpaired` (`StatusEffects.kt:36-46`) then matches on `"poison" in label`, so the poison **does** flow into `InfoCalc` caveats. That part works.
4. `Know` token: same tray. Then the guide's show card displays the Widow token full-screen. Works.
5. Expiry: `widow`/`Poisoned` is in neither `EXPIRES_AT_DAWN` nor `EXPIRES_AT_DUSK` (`GameActions.kt:218-242`), which is correct — but nothing removes it when the Widow dies either, and nothing suspends it when the Widow is drunk/poisoned.

## Defects and gaps

1. **P1 · The poison does not end when the Widow dies** — the wiki is explicit ("If the Widow dies, the poisoned player is no longer poisoned"). The app leaves the `widow`/`Poisoned` token in place forever, so from the Widow's death onward `StatusEffects.isImpaired` (`StatusEffects.kt:36-46`) keeps reporting the victim as poisoned and `InfoCalc.impairments` (`InfoCalc.kt:133-153`) keeps telling the Storyteller to give false information. This is a *wrong-information* bug, not just bookkeeping. Repro: night 1 poison Alice, execute the Widow on day 1, open Alice's info step on night 2 — it still says "Alice is POISONED (Widow) — give false info."
2. **P1 · The poison does not pause while the Widow is drunk or poisoned** — the wiki's Innkeeper example walks through exactly this ("The Empath is no longer poisoned… The Widow is now sober and the Empath is poisoned again"). Nothing in `StatusEffects` models a poison whose source can be switched off. `derivedPoison` (`StatusEffects.kt:14-33`) is the right home for it — it already does exactly this shape of computation for the No Dashii — but only handles No Dashii today.
3. **P1 · No setup prompt for the KNOW token** — "While preparing the first night, mark a good player with the KNOW reminder", i.e. *before* night 1 begins, and the choice must be a **good** player. The app has a purpose-built dialog pattern for exactly this (`GameShell.kt:347-376` for the Fortune Teller's red herring, which even filters to good players) and does not use it here. `validateSetupState` (`GameActions.kt:503-561`) also does not require it, so night 1 can begin with no KNOW player chosen.
4. **P1 · No "show the Grimoire" mode** — the character's defining action. The Storyteller must hand the unmodified Grimoire tab to an evil player, which (a) exposes reminder tokens the jinxes require hiding and (b) exposes the app's own bookkeeping (notes, the shown-identity rows in `SeatSheet`, the Drunk's "Believes they are the …" note written at `GameShell.kt:403-407`). The app's own guide text at `night_guide.json:1480` tells the Storyteller to "cover any reminders you must hide" — a physical-grimoire instruction that has no digital equivalent here. The **Spy** has the identical need (`night_guide.json` `spy` entry) so one shared feature serves both.
5. **P1 · The Magician jinx cannot be honoured** — "When the Widow sees the Grimoire, the Demon and Magician's character tokens are removed." There is no way to hide two seats' character tokens in the Grimoire view. Same root cause as defect 4.
6. **P1 · A mid-game Widow (Pit-Hag / Alchemist / Summoner) never gets a step** — `widow` is absent from the otherNight order (`night_and_jinxes.json` otherNight list), so if a Pit-Hag creates a Widow on night 3, `NightOrder.otherNight` produces no Widow row and the Storyteller gets no prompt to run the grimoire-view, the poison and the KNOW reveal that same night. Repro: change a seat to `widow` on night 3 and look at the night sheet.
7. **P1 · The Damsel jinx is not applied** — "If the Widow is (or has been) in play, the Damsel is poisoned." The jinx text is in the data (`night_and_jinxes.json:39`) and shown in `SeatSheet.kt:222-234`, but no token is placed, so the Damsel's info is computed as true. Note the "**or has been**" clause: killing the Widow does *not* un-poison the Damsel — this is deliberately different from the Widow's own poison and must not be folded into the same derived rule.
8. **P2 · The KNOW player is chosen entirely by hand with no guidance** — the app could offer the good players, mark the choice, and drive the reveal card in one flow, the way the Fortune Teller prompt does.
9. **P2 · The Poppy Grower jinx text is stale** — `night_and_jinxes.json:149` says "If the Poppy Grower is in play, the Widow does not see the Grimoire until the Poppy Grower dies"; the wiki text is "If the Poppy Grower **has their ability**, the Widow does not see the Grimoire." The difference matters when the Poppy Grower is drunk or poisoned.
10. **P2 · The Alchemist–Widow jinx is missing** from `night_and_jinxes.json` ("An Alchemist-Widow has no Widow ability & a Widow is in play" — which also means the good player still learns a Widow is in play).
11. **P2 · The "1 good player knows" step is buried inside the Widow's own step** — at the table it is a separate wake-and-show. It deserves its own checkable sub-step so it cannot be forgotten, and the app should name the KNOW holder on the step.
12. **P3 · Two Widows** — the guide mentions hiding a fellow Widow's KNOW token but the exclusive-token logic (`GameActions.placeExclusiveReminder`, `GameActions.kt:194-201`) means the second Widow's `Poisoned` token would *move* the first Widow's token instead of adding a second. With only one `"Poisoned"` label in the reminder list, `NightScreen.kt:260-265` always takes the exclusive path.

## Proposed behaviour (spec)

### Setup

- Add a **KNOW prompt** to `GameShell`'s pre-night-1 prompt chain (alongside `waitingForHerring`/`waitingForDrunk`/`waitingForLunatic`/`waitingForMarionette`, `GameShell.kt:347-479`):
  - condition: `phase == SETUP && players.any { it.characterId == "widow" } && players.none { it.reminders.any { r -> r.sourceId == "widow" && r.label == "Know" } }`
  - dialog: `A Widow is in play — which GOOD player learns it?` listing only `!isEvil(lookup)` seats (the Fortune Teller dialog at `GameShell.kt:360` already filters exactly this way).
  - on pick: `addReminder(p, PlacedReminder("widow","Know"))`.
- `validateSetupState` (`GameActions.kt:503-561`) gains: `Widow: choose the good player who knows a Widow is in play` when the token is absent, mirroring the existing Fortune Teller check (`GameActions.kt:547-559`).
- **Damsel jinx**: if a Damsel is in play alongside a Widow, place a permanent `PlacedReminder("widow","Poisoned (Damsel jinx)")` on the Damsel at setup and never remove it — the jinx says "is (or has been) in play".

### Night step

- **when**: **both** first and other night — add `widow` to the otherNight order (immediately after `poisoner`, mirroring the firstNight slot at index 28). Wake condition: holder alive **AND** they have not yet acted, i.e. no `widow`/`Grimoire seen` marker. This makes "on **your** 1st night" work for a Pit-Hag Widow without changing night 1 behaviour.
- **Poppy Grower jinx**: if a Poppy Grower is in play **and has their ability** (alive, not impaired), suppress the grimoire view and show `Poppy Grower jinx: the Widow does not see the Grimoire.` Re-offer the step on the first night after the Poppy Grower loses their ability.
- **targets**: exactly 1, any player including the Widow themself and dead players (the wiki says "points to a character token in the Grimoire"). Default sort: alive, Townsfolk first.
- **immediate effects**:
  - `placeExclusiveReminder(target, PlacedReminder("widow","Poisoned"))`
  - `addReminder(holder, PlacedReminder("widow","Grimoire seen"))` (spent marker — hidden from the reminder picker, or use the existing generic `No ability`-style marker).
- **grimoire view** (new shared feature, also used by the Spy):
  - A **"Show the Grimoire"** full-screen mode reachable from the step, showing the seat circle with character tokens and reminder tokens, and **nothing else** — no notes, no shown-identity rows, no app chrome, no editing.
  - Per-seat **redaction toggles**, pre-applied from rules:
    - Magician jinx → auto-hide the **Demon's** and the **Magician's** character tokens.
    - Auto-hide every `widow`/`Know` token (so a second Widow cannot see it) and the acting Widow's own `Grimoire seen` marker.
    - A manual "hide this token / hide this seat's character" tap for anything else the Storyteller wants covered.
  - An explicit **"Done — hand the phone back"** button that returns to the Storyteller view (and can raise `PrivacyCover`).
- **deferred effects**: none, but the poison is **conditional and open-ended**, see expiry.
- **expiry**: `widow`/`Poisoned` never expires at dawn or dusk. Instead, move the effect into `StatusEffects.derivedPoison` (`StatusEffects.kt:14-33`):

```
// Widow: the marked player is poisoned only while a Widow with their
// ability is alive.  Wiki: poison ends when the Widow dies, and pauses
// while the Widow is drunk/poisoned.
val widowActive = seats.any { it.characterId == "widow" && it.alive && !isImpairedIgnoringDerived(it) }
if (widowActive) for (p in seats.filter { it.reminders.any { r -> r.sourceId=="widow" && r.label=="Poisoned" } })
    result[p.id] = "Poisoned by the Widow"
```
  Keep the token on the seat as the *record* of the choice, but make the *effect* derived. When the Widow dies or is impaired, `isImpaired` must return false for the victim and `InfoCalc` must stop emitting the "give false info" caveat. Guard the recursion (compute the Widow's own impairment from reminders/character only).
  - The `Poisoned (Damsel jinx)` token is deliberately **not** part of that rule — it stays unconditional.

- **information / visibility**:
  - Sub-step, checkable on its own: `Wake <KNOW player> and show the Widow token.`
  - Show card: the existing `THIS CHARACTER IS IN PLAY` + `token: "self"` card is correct. Add the explicit reminder `They learn a Widow is in play — NOT who, and NOT who is poisoned.`
  - If the KNOW player is dead or was never marked, the step must say so and offer the good-player picker inline.
- **interactions/jinxes to handle explicitly**: Magician (token redaction), Poppy Grower (suppression), Damsel (permanent poison), Alchemist (no Widow ability but "a Widow is in play" — the KNOW reveal still happens), Heretic (only one jinxed character in play — a setup validation note).

### UI text the step should display

- `Show the Grimoire to <name> for as long as they want.` + `[ Show the Grimoire ]`
- `Who do they point at? They are poisoned while the Widow lives.`
- `Wake <KNOW player> — show them the Widow token. They learn only that a Widow is in play.`
- On the day the Widow dies (dawn briefing): `<victim> is no longer poisoned — the Widow died.`

### Data changes

- `night_and_jinxes.json`: add `widow` to the **otherNight** order after `poisoner`; add the Alchemist–Widow jinx; update the Poppy Grower jinx text to the current wiki wording.
- `night_guide.json:1480`: add an `other` section (identical run-book, for a mid-game Widow); replace "cover any reminders you must hide" with a pointer to the redacted grimoire view; add the "not which player is the Widow, not which player is poisoned" line.
- `characters.json:1905`: no change to the official reminders; if a spent marker is wanted, use an app-internal marker rather than inventing an official token.

## Tests to add

1. **Poison ends when the Widow dies** — *Given* Alice holds `widow`/`Poisoned` and a living Widow exists, *Then* `StatusEffects.isImpaired(Alice)` is true; *When* the Widow is killed, *Then* it is false. (Fails today.)
2. **Poison pauses while the Widow is drunk** — *Given* the same setup plus an `innkeeper`/`Drunk` token on the Widow, *Then* `isImpaired(Alice)` is false; *When* the Drunk token is removed, *Then* true again. (Wiki's Innkeeper example.)
3. **`InfoCalc` stops lying after the Widow dies** — *Given* Alice is an Empath holding `widow`/`Poisoned`, *When* the Widow dies, *Then* `InfoCalc.compute(..., "empath", alice)` returns no "POISONED" caveat.
4. **Setup requires a KNOW player** — *Given* a Widow in the bag and no `widow`/`Know` token anywhere, *When* `validateSetupState` runs, *Then* it reports "Widow: choose the good player who knows a Widow is in play".
5. **KNOW player must be good** — *Given* the `Know` token on an evil seat, *Then* `validateSetupState` reports an issue.
6. **Mid-game Widow gets a night step** — *Given* a seat whose `characterId` becomes `widow` on cycle 3 with no `Grimoire seen` marker, *When* `NightOrder.otherNight` is built, *Then* a `widow` step is present; *Given* the marker is present, *Then* it is absent.
7. **Damsel jinx poison is unconditional** — *Given* a Damsel holding `widow`/`Poisoned (Damsel jinx)`, *When* the Widow dies, *Then* the Damsel is still impaired.
8. **The Widow's poison beats the Sailor** — *Given* a Sailor holding `widow`/`Poisoned`, *When* `deathNotes` runs for them, *Then* it does **not** claim "The Sailor can't die." (Today `StatusEffects.kt:73` returns that note regardless of impairment — this is a general bug the Widow exposes.)
9. **Two Widows place two poisons** — *Given* two Widow seats, *When* each poisons a different player, *Then* both `Poisoned` tokens exist. (Fails today: `placeExclusiveReminder` moves the single token.)
