# Steward (steward) — Experimental Townsfolk

## Official rules (sources)

Source: https://wiki.bloodontheclocktower.com/Steward (fetched 2026-08-25).

Current ability text (matches `characters.json` exactly — no drift):

> "You start knowing 1 good player."

How to Run (quoted verbatim):

> "While preparing the first night, put the **KNOW** reminder by any good character token. During the first night, wake the Steward. Point to the player marked **KNOW**. Put the Steward to sleep."

Clarifications (verbatim):
- "The Steward learns a **player**, but not their character."
- "If created mid-game, then the Steward learns their information that night instead."

Examples:
- Steward learns Alex is good; Alex is the Undertaker.
- A Pit-Hag turns the Poppy Grower into a Steward; that Steward learns Abdallah is good — **Abdallah is the Spy**, registering as good. (This is the canonical misregistration case and confirms the Spy is a legal answer.)

Not addressed on the wiki (flagged, not guessed): whether the KNOW reminder may be placed on the Steward themselves (pointless but not forbidden by the text), and whether the shown player must be alive. On night 1 nobody is dead, so it only matters for a mid-game Steward.

Jinxes: **none.** The wiki lists none, and the app's data correctly has none.

Key implementation point: because the KNOW token is placed **during first-night preparation**, the choice is made *before* the Steward wakes — the same class of pre-night hidden choice as the Fortune Teller's red herring, the Drunk's token and the Lunatic's Demon.

## What the app does today

Data / order:
- `characters.json:1577` — ability text current; `firstNightReminder: "Point to the player marked 'Know'."`; `reminders: ["Know"]`. Correct.
- `night_and_jinxes.json:352` — first-night slot 57, between Seamstress and Knight. Correct. No other-night entry, correct.
- `night_guide.json:1211` — accurate prose including the impaired case. `shows: []`.

Runtime:
- `InfoCalc.supports` includes `steward` (`InfoCalc.kt:33`), `targetsNeeded == 0`.
- `InfoCalc.steward` (`InfoCalc.kt:442-450`):
  ```
  val good = ctx.players.filter { !ctx.isEvil(it) }
  return InfoResult(
      headline = "Point to 1 good player",
      detail = "Good players: ${good.joinToString { ctx.name(it) }}",
      caveats = misregistrations(ctx, ctx.players),
  )
  ```
  It lists every good player and attaches Spy/Recluse notes.
- `commonCaveats` adds impairment and the Vortox note.
- Placing the `Know` token is manual: expand the Steward's row, tap the "Know" chip in `NightToolTray`, tap a seat. It is single-copy, so `placeExclusiveReminder` moves it (`GameActions.kt:196-205`, `NightScreen.kt:318-322`).
- `Know` is in **neither** expiry table (`GameActions.kt:218,231`) — correct, it is a permanent record.
- `validateSetupState` (`GameActions.kt:503-560`) enforces the Fortune Teller's red herring, the Drunk's token, the Lunatic's Demon and the Marionette's seat/neighbour — **but not the Steward's KNOW token.**
- `GameShell` has pre-night prompts for herring/Drunk/Lunatic/Marionette (`GameShell.kt:340-478`) — **none for the Steward.**

Storyteller experience today: on night 1 the row says "Point to the player marked 'Know'" — but no player is marked, because nothing ever asked. The ST reads the computed list of good players, picks one in their head, points, and (if they remember) drops the token afterwards. Compared with the rest of the group this is close to working; the gaps are the pre-night choice, the "point at a player" delivery, and the mid-game case.

## Defects and gaps

1. **P1 · The KNOW token is never requested and never validated.** The rules say to place it while preparing the first night; the app's own step text ("Point to the player marked 'Know'") presupposes it. Repro: start any game with a Steward and advance to night 1 — no prompt, no token, and the step tells you to point at a marked player who does not exist. Contrast `GameShell.kt:340-386` (red herring prompt) and `GameActions.kt:544-559` (herring validation), which do exactly the right thing for the Fortune Teller.
2. **P1 · The calculator ignores the token even when it is placed.** `InfoCalc.steward` (`InfoCalc.kt:442-450`) never looks at `steward:Know`; it always prints the full list of good players and never says "you already chose Bo". Repro: place `Know` on a seat, then reopen the Steward row — the headline is still the generic "Point to 1 good player".
3. **P1 · The Spy is excluded from the offered answers although the wiki's own example uses it.** `good = ctx.players.filter { !ctx.isEvil(it) }` uses true alignment, so the Spy — the canonical, wiki-documented Steward answer — is not in the list. The `misregistrations` caveat mentions the Spy exists, but the ST has to work out for themselves that it is a legal pick. The list should include misregistering evil players as a separate, clearly-labelled group.
4. **P1 · No way to point at a player on a phone.** The Steward's answer is "this player", and the app has no full-screen "point to a seat" affordance at all — `ShowCard` (`ShowCards.kt:65-77`) has Message/Character/Number/Alignment/Bluffs/Sheet cards but nothing that identifies a *player*. On a physical table the ST points; on the PWA, at a table where the phone is the grimoire, there is no equivalent. Affects Steward, Noble, Knight, Sage, Investigator, Washerwoman, Librarian, Bounty Hunter, Grandmother, Shugenja.
5. **P1 · A mid-game Steward never gets their info at all.** "If created mid-game, then the Steward learns their information that night instead." `steward` is absent from the other-night order (`night_and_jinxes.json` `otherNight`, verified — it appears only in `firstNight` at :352), so `NightOrder.build(isFirstNight = false)` never even considers it and **no row is emitted**. Repro: a Pit-Hag turns a player into the Steward on night 3; the night sheet has no Steward row, and nothing at dawn or the next dusk mentions that the new Steward is owed their info. Cross-cutting with Pixie/Shugenja/Washerwoman/Librarian/Investigator/Chef/Empath/Clockmaker/Dreamer/Seamstress/Steward/Knight/Noble/Balloonist/Village Idiot and with the user's Professor complaint ("When Professor brings someone back it should remind in the morning and rerun the 1st night for that").
6. **P2 · The Steward themselves is offered as an answer.** `good` includes the holder. Legal by the letter of the rules, useless in play, and a slip waiting to happen. Sort them last and label `(the Steward — pointless)`.
7. **P2 · Dead players are offered with no marker.** For a mid-game Steward the list mixes living and dead seats with no `†`. `InfoCalc.kt:445` uses `ctx.name(it)` only, unlike `startKnowing` which at least appends the character name.
8. **P2 · Impaired Steward gets a dead-end "False info" panel.** Same bug as the Shugenja: `NightScreen.kt:904-930` renders the red "False info to show instead:" heading, but the headline ("Point to 1 good player") has no leading digit and is not YES/NO, so the chip row underneath is **empty**. When the Steward is poisoned the app should be offering the *evil* seats as the lie — it offers nothing.
9. **P2 · The Vortox caveat is right but unusable.** With a Vortox in play the Steward's info must be false, i.e. the ST must point at an **evil** player. The panel says "VORTOX in play — Townsfolk info must be FALSE" and then lists only good players.
10. **P3 · No record of who was shown.** Once the token is placed it is a record; if the ST forgets, nothing else in the app remembers. The same `infoGiven` log proposed for the Shugenja covers this.

## Proposed behaviour (spec)

### Setup / pre-first-night
- Add a Steward prompt to `GameShell`'s pre-night chain (`GameShell.kt:340-478`), gated the same way as the red herring:
  - condition: `state.phase == SETUP && a steward seat exists && no seat carries steward:Know`
  - title `The Steward is in play`
  - body `<StewardName> is the Steward. Which good player do they learn?`
  - options: all **good** players, plus a clearly separated "registers as good" group containing any **Spy** (and, when a Vortox is in play, the whole evil team, since the info must be false).
  - action: `placeExclusiveReminder(state, chosenId, PlacedReminder("steward", "Know"))` and set the Steward's seat note `Knows <Name> is good`.
  - a "Later" escape, like the other prompts.
- Add to `validateSetupState` (`GameActions.kt:503-560`):
  `if (residents.any { it.characterId == "steward" } && no seat carries steward:Know) issues += "Steward: mark one good player with the 'Know' reminder"`.
  Follow the Fortune Teller pattern exactly, including the "the guard only advises" escape hatch (`GameShell.kt:583-592`).

### Night step
- **when:** first night. Also on the night a Steward is **created mid-game** — the generic "run this character's first-night info now" mechanism; the row must be labelled `Steward — first-night info (created tonight)` and must run the KNOW prompt inline rather than at setup.
- **wake condition:** Steward seat alive.
- **targets:** none (the choice is the KNOW token, made before the step).
- **immediate effects:** none beyond the token already placed.
- **expiry:** `steward:Know` never expires; it stays out of both tables.
- **information — computed** (`InfoCalc.steward` rewrite):
  - if a seat carries `steward:Know`: `headline = "Point to <Name>"`, `detail = "<Name> is the <Character> (good)"`, and, when the marked player is **not** good, a loud caveat `! <Name> is EVIL — is that deliberate (Spy / Vortox / poisoned Steward)?`
  - if no seat is marked: `headline = "Mark a good player with 'Know' first"`, `detail` = the good-player list, **including** a separate line `Registers as good: <Spy names>`, with the Steward's own seat sorted last and labelled, and dead seats suffixed `†`.
  - caveats: keep `commonCaveats`; narrow `misregistrations` to the Spy only when the Spy is a *candidate*, and drop the Recluse note entirely (a Recluse registering as evil cannot make a good player wrong here — it only removes an option, which is worth one line: `Recluse <Name> may register as evil — you may treat them as not a legal answer.`).
- **information — shown:** add `ShowCard.PlayerCard(prefix: String, playerId: Long)` to `ShowCards.kt:65-77`, rendering the seat name (and, optionally, the seat position in the circle) full-screen. Give the Steward's guide a card `{"label":"This player is good","kind":"player"}`. This is the missing generic "point at a player" primitive; it pays for itself across ten characters.
- **impaired / false alternative:** when the Steward is impaired or a Vortox is in play, `InfoResult.falseAlternatives` must contain a `PlayerCard` for each **evil** seat, so the "False info to show instead:" panel is populated instead of empty. (Same generic fix as the Shugenja: the calculator supplies the alternatives; `NightScreen.kt:904-930` stops inferring them from the headline string.)
- **visibility:** nothing shown to evil.
- **day-time inputs:** none.
- **interactions:** Spy (may be the answer — supported explicitly); Recluse (may be excluded); Vortox (answer must be false); Drunk/poison (answer may be false); Marionette (a Marionette shown a Steward token is handled by `nightRoleId`, `GameState.kt:36-42` — they wake as the Steward and must get arbitrary info; `InfoCalc.impairments` already flags this at `InfoCalc.kt:141-143`, and the same false-alternative panel serves it).

### UI text the step should display
- Before the token is placed: `Mark a good player with "Know", then point to them.`
- After: `Point to <Name>.` + `» Show "<Name>" full-screen`.
- Impaired: `! <StewardName> is poisoned — point to anyone, including an evil player.` + evil-player chips.

### Data changes
- `night_guide.json:1211` — add `"shows": [{"label":"This player is good","kind":"player"}]`; extend `NightGuide.VALID_KINDS` (`NightGuide.kt:43`) with `"player"`.
- `characters.json:1577`, `night_and_jinxes.json:352` — no changes.

## Tests to add

1. `setup validation demands a steward Know token` — Given a legal 7-player bag containing `steward` and no `steward:Know` reminder anywhere; When `GameActions.validateSetupState(state, lookup)`; Then the issues contain "Steward". (Today: empty.)
2. `steward info names the marked player` — Given `steward:Know` on seat 4; When `InfoCalc.compute(data, state, "steward", holderId = stewardSeat)`; Then the headline is `Point to <seat 4 name>`. (Today: the generic "Point to 1 good player".)
3. `steward warns when the marked player is evil` — Given `steward:Know` on the Spy's seat; Then a caveat flags that the marked player is evil, and the result still permits it (Spy registers as good).
4. `steward lists the spy as a legal answer` — Given a Spy in play and no Know token; Then the detail contains the Spy's name in a "registers as good" group. (Today: the Spy is omitted entirely.)
5. `steward Know token moves rather than accumulating` — Given `Know` placed on seat 4, then on seat 5; Then only seat 5 carries it.
6. `steward Know token survives dawn and dusk` — When `advancePhase` runs through both boundaries; Then the token is still on seat 5.
7. `impaired steward gets evil-player false alternatives` — Given the Steward carries `poisoner:Poisoned`; Then `result.falseAlternatives` is non-empty and contains a card for each evil seat. (Today: the UI renders an empty "False info" row.)
8. `mid-game steward gets a first-night info row` — Given a Steward assigned on cycle 3 (Pit-Hag); When `otherNight(state, lookup)`; Then a `steward` step exists whose detail identifies it as catch-up first-night info, not the raw ability text.
9. `steward night guide exposes a player card` — `NightGuide.forStep("steward", true)!!.shows.any { it.kind == "player" }`.
