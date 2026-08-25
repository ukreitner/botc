# Kazali (kazali) — exp demon

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Kazali> (fetched 2026-08-25).

Current ability text (matches `characters.json`):

> "Each night*, choose a player: they die. [You choose which players are which Minions.
> -? to +? Outsiders]"

Summary bullets (quoted):

- "The Kazali chooses their own Minions."
- "If a Kazali is created mid game, the Kazali does not choose new Minion players."
- "The Storyteller can give the Minions' original good characters as bluffs to the Demon,
  since they are not in play."
- "Only Minions that are on the script may be chosen. Duplicate Minion characters are not
  allowed."
- "The Kazali can make whatever player they want into a Minion, regardless of that
  player's character ability e.g. Soldier, Goon, Damsel, King."

How to Run (quoted):

- **Setup:** "remove all Minion tokens and add Townsfolk or Outsider tokens."
- **First night:** "wake the Kazali. The Kazali points at a player and a Minion on the
  character sheet. Replace their old character token with the Minion token, show them the
  'You Are' info token then the Minion character token, and give a thumbs down. **Repeat
  until the normal number of Minions exist.**"
- **Each night\*:** "wake the Kazali. They point at any player. That player dies."
- Balance note: "Depending on the script, adding more than one Outsider can put the good
  team at a significant disadvantage."

What this means for the bag (and this is the part the app gets wrong):

- The bag contains **no Minions at all**. The normal Minion slots are refilled with
  Townsfolk or Outsider tokens, at the storyteller's choice — that is exactly what
  "-? to +? Outsiders" encodes.
- So for N players: `demons = 1` (the Kazali), `minions = 0`, and
  `townsfolk + outsiders = N - 1`, with the Outsider count a free storyteller choice
  (bounded in practice by `0 … baseOutsiders + baseMinions`).
- The **minion count is not the Kazali's choice** — it is "the normal number of Minions"
  for the player count, created on night 1.

Jinxes (quoted from the wiki's Kazali page):

- **Bounty Hunter:** "If the Kazali turns the Bounty Hunter into a Minion, an evil
  Townsfolk is not created."
- **Marionette:** "If there would be a Marionette in play, they enter play after the
  Demon & must start as their neighbor."
- **Summoner:** "If the Summoner creates a second living Demon, deaths tonight are
  arbitrary."

*Uncertain / to verify:* the app also carries a **Choirboy** jinx ("The Kazali can not
choose the King to become a Minion.", `night_and_jinxes.json:254`). It is **not listed**
in the wiki's Kazali jinx section, and the wiki Summary explicitly says the Kazali *can*
convert the King. The Choirboy page fetch returned no jinx section at all, so I cannot
confirm or refute it — flagging rather than asserting.

*Uncertain / not covered by the wiki:* whether a Kazali-created Minion's **setup** ability
fires (Baron's +2 Outsiders, Godfather's ±1 Outsider, Widow, Boffin, Xaan). The general
BOTC rule is that setup abilities apply only during setup, which is already over when the
Kazali chooses; the "-? to +? Outsiders" bracket exists precisely so the storyteller can
pre-compensate. The app should *ask* rather than decide.

Night order: **first night, position 2** — `night_and_jinxes.json:297`, immediately after
`DUSK` and `lordoftyphon`, and crucially **before** `MINION_INFO` (line 309) and
`DEMON_INFO` (line 313). Other nights: `night_and_jinxes.json:425`. Both correct.

## What the app does today

Data:

- `characters.json:1968` — text matches; `setup: true`; `reminders: ["Dead"]`;
  `firstNightReminder` = "The Kazali chooses which players are which Minions. Wake each
  target. Show the 'You are' and Minion tokens & give a thumbs-down."
- `night_guide.json:1540` — a good `first` prose entry and one show card
  ("To each chosen", `kind: token`, `token: "pick"` → opens the character picker), plus a
  plain `other` entry.
- `night_and_jinxes.json:249,254,259` — bountyhunter, choirboy, marionette jinxes. The
  **Summoner** jinx is missing.

Setup path:

- `Setup.modifierFor` (`engine/.../Setup.kt:121-232`) parses the bracket
  "You choose which players are which Minions. -? to +? Outsiders": the `?` branch at
  line 150-153 yields `choiceTeams = {OUTSIDER}` and **zero deltas** (`matches` is empty
  because there is no `[+-]\d+` token, so the `isChoice && matches.isNotEmpty()` branch at
  line 203 never runs). Minion and Demon counts stay at the base values.
- `GameActions.validateBag` (`GameActions.kt:420-496`) then relaxes only OUTSIDER (+
  TOWNSFOLK via line 435-442) and **still checks MINION**. A legal Kazali bag (0 Minions)
  is reported as `"Minion: 0 in bag, expected 2"`.
- `GameActions.randomBag` (`GameActions.kt:338-402`) draws `dist.count(MINION)` real
  Minions, so "Randomize" produces an **illegal** Kazali bag every time.
- `validateSetupState` (`GameActions.kt:503-561`) re-runs `validateBag`, so pressing
  "Begin night" pops the "Setup isn't legal yet" dialog
  (`GameShell.kt:551-591`) with the same false error. There is a "Start the night anyway"
  escape and a "Deal anyway (I know what I'm doing)" button
  (`SetupScreen.kt:489-495`), so the ST *can* proceed — after being told their correct
  setup is wrong.
- The bag builder header ("Need: 7 townsfolk · 0 outsiders · 2 minions · 1 demon",
  `SetupScreen.kt:372-378`) tells the storyteller to do the wrong thing.

Night 1 path:

- `NightOrder.build` emits the "Kazali" step at position 2 with the `firstNightReminder`
  as detail. Correct position, correct prose.
- `NightScreen.StepDetailPanel` shows the guide text and the one "To each chosen" show
  card, then `QuickResolutions` (`NightScreen.kt:462-525`) falls through to `else ->`
  and, because `character.team == Team.DEMON && holder.alive`, renders
  **`DemonKillPanel`** — on the **first night**. The storyteller is asked
  "Demon kill — who did <Kazali> choose?" during the minion-creation step.
- To actually create the Minions the ST must, per Minion: open the seat → Change
  character → pick the Minion (`SeatSheet.kt:88-96`, `CharacterPicker` at
  `SeatSheet.kt:388`), then separately open the night step's show card, retype/pick the
  token, show it, then find the "evil" alignment card in the show tool. Nothing records
  the player's original character.

Other nights: the generic `DemonKillPanel` is exactly right — **works**.

Downstream effects that happen to work once the ST has done the conversions by hand:
`MINION_INFO` and `DEMON_INFO` are computed from live `state.players`
(`NightOrder.kt:60-119`) and recompose, so they list the newly created Minions correctly;
and `GameActions.suggestBluffs` (`GameActions.kt:121-127`) excludes only *currently*
in-play characters, so the converted players' original characters do become bluff
candidates — provided the ST re-rolls the bluffs after conversion.

## Defects and gaps

1. **P0 · A legal Kazali bag is rejected as illegal.**
   `validateBag` still requires the base Minion count; a Kazali bag has 0 Minions.
   `GameActions.kt:456-478`, driven by `Setup.modifierFor`'s failure to zero the Minion
   count (`Setup.kt:143-171`).
   *Repro:* new game → any Kazali script → 10 players → add Kazali + 9 good characters →
   "Minion: 0 in bag, expected 2" and the start button is disabled.

2. **P0 · "Randomize" builds an illegal Kazali bag.**
   `randomBag` draws Minions for the Kazali distribution (`GameActions.kt:352-362`), so
   the dealt game has 2 real Minions *and* a Kazali who is supposed to create them.
   *Repro:* pick a Kazali script, press Randomize — a Kazali plus Minions appears.

3. **P0 · The first-night step offers a demon kill.**
   The Kazali does not kill on night 1; it creates Minions. The generic fallthrough gives
   it "Demon kill — who did X choose?" (`NightScreen.kt:518-523`). A storyteller who
   trusts the app kills someone on night one.

4. **P0 · No Minion-creation tool exists.**
   The single most complex first-night procedure in the game — repeated (player, Minion
   character) pairs, each with a token reveal and a thumbs-down — is entirely manual
   across three different screens. There is no count of how many Minions are still owed,
   no duplicate prevention, no script-legality check.

5. **P1 · The Outsider count choice is never offered.**
   "-? to +? Outsiders" is a storyteller decision made at setup. The bag builder shows a
   fixed "0 outsiders" requirement (`SetupScreen.kt:372-378`) and gives no control.

6. **P1 · Original characters are lost.**
   Nothing records what a converted player used to be, so the wiki's explicit advice
   ("The Storyteller can give the Minions' original good characters as bluffs") depends on
   the ST's memory. `assignCharacter` (`GameActions.kt:46-53`) overwrites `characterId`
   and clears `shownCharacterId` with no history.

7. **P1 · The Marionette case is not handled.**
   Per the jinx, a Kazali-created Marionette must be one of the Kazali's **alive
   neighbours**, must **not** be woken, and must keep believing they are their original
   good character. The generic "change character" path would set `characterId=marionette`
   with `shownCharacterId=null`, so the app would then treat them as a Marionette with no
   believed identity; `validateSetupState:526-543` would also demand the Marionette
   neighbour the Demon at *setup*, which is not when this Marionette appears.

8. **P1 · The Bounty Hunter jinx is data-only.**
   "If the Kazali turns the Bounty Hunter into a Minion, an evil Townsfolk is not
   created." `InfoCalc.bountyHunter` (`InfoCalc.kt:460-467`) unconditionally prints
   "Remember: 1 Townsfolk is evil in a Bounty Hunter game." — wrong in this case.

9. **P1 · The setup guard fires on a correct game.**
   `validateSetupState` inherits the bag error, so the "Setup isn't legal yet" dialog
   (`GameShell.kt:551`) appears at "Begin night" for a correctly-built Kazali game.

10. **P2 · The Summoner jinx is missing from the data.**
    "If the Summoner creates a second living Demon, deaths tonight are arbitrary."

11. **P2 · The Choirboy jinx text is unverified and may contradict the wiki.**
    `night_and_jinxes.json:254`. See "Uncertain" above.

12. **P2 · Created Minions with setup abilities are not addressed.**
    Baron / Godfather / Widow / Boffin / Xaan chosen by the Kazali: the app does nothing
    and says nothing. At minimum the creation panel should warn.

13. **P2 · No prompt to refresh the demon bluffs after conversion.**
    The right moment to pick bluffs is *after* the Kazali has chosen, since the freed-up
    good characters are the best bluffs. Bluffs are chosen from a menu with no timing cue
    (`GameShell.kt:218-221`, `BluffsSheet.kt`).

14. **P3 · Step title/detail don't say how many Minions are owed.**
    "Repeat until the normal number of Minions exist" is a count the app knows
    (`Setup.distributionFor(nonTravellerCount).minions`) and should display.

## Proposed behaviour (spec)

### Setup

- `Setup.modifierFor("kazali")` must return a modifier that:
  - forces `minions = 0` (express as `choiceTeams += MINION` with
    `choiceDeltas[MINION] = { -baseMinions }`, or more simply a new
    `minionsToZero = true` flag consumed by `adjustedDistribution`);
  - leaves `demons = 1`;
  - keeps OUTSIDER (and therefore TOWNSFOLK) as a free storyteller choice, bounded to
    `0 … baseOutsiders + baseMinions`.
- The bag builder must render a Kazali-aware requirement line:
  **"Kazali game: no Minions in the bag. Need 1 Demon (Kazali) + N-1 good characters —
  you choose how many are Outsiders (0–<max>)."** with an Outsider-count stepper that
  drives validation.
- `randomBag` must special-case a drawn Kazali: draw 0 Minions and fill those slots from
  the Townsfolk/Outsider pools according to the chosen Outsider count.
- `validateBag` must accept `minions == 0` when a Kazali is in the bag, and must reject
  any bag that contains both a Kazali and a Minion.

### Night 1 — Minion creation (structured)

- **when:** first night only, at the existing position (before Minion info / Demon info).
  Wake condition: a living Kazali that **entered play at setup**. A Kazali created
  mid-game (Pit-Hag, etc.) does **not** run this step — the app must suppress it
  ("If a Kazali is created mid game, the Kazali does not choose new Minion players").
- **targets:** exactly `Setup.distributionFor(nonTravellerSeats).minions` pairs of
  *(player, Minion character)*.
  - Player constraints: any seat except the Kazali itself and any seat already converted
    tonight; Travellers excluded. Alive-only in practice (night 1). The picker should sort
    by seat order and show each candidate's current character.
  - Minion constraints: **on the current script**, **no duplicates**, not already used
    tonight. The picker lists script Minions only.
- **immediate effects per pair:**
  1. record the player's previous `characterId` (see "State" below);
  2. `assignCharacter(playerId, minionId)` — team-derived alignment makes them evil, so no
     `alignmentFlipped` needed;
  3. clear any reminders the old character owned;
  4. queue a two-card reveal for that player: **"YOU ARE"** + the Minion token
     (`ShowCard.CharacterCard`), then the **evil alignment card**
     (`ShowCard.AlignmentCard(evil = true)`) as the thumbs-down.
  - **Marionette exception:** if the chosen Minion is the Marionette, restrict the player
    picker to the Kazali's two **alive neighbours**, set `characterId = "marionette"` and
    `shownCharacterId = <their original character>`, add the `marionette:Is the
    Marionette` reminder, set the seat note "Believes they are the <original>", and
    **show nothing** to that player. The panel must say so explicitly.
  - **Setup-ability warning:** if the chosen Minion has `setup == true` (Baron, Godfather,
    Widow, Boffin, Xaan…), show
    **"<Minion> normally changes setup. Setup is over — decide now whether to apply it and
    note your ruling."** and offer a free-text ST note. Do not silently change counts.
  - **Bounty Hunter:** if the converted player was the Bounty Hunter, record
    `kazali:no evil townsfolk` so `InfoCalc.bountyHunter` drops its "1 Townsfolk is evil"
    caveat.
- **progress:** the panel header is **"Minions still to create: k of n"** and the step
  cannot be ticked done until `k == 0` (or the ST explicitly overrides).
- **deferred effects:**
  - After the last conversion, prompt: **"Pick demon bluffs now — <original characters of
    the converted players> are no longer in play and make excellent bluffs."** with those
    ids pre-selected in `BluffsSheet`.
  - `MINION_INFO` and `DEMON_INFO` then run normally off the updated grimoire (already
    works).
- **expiry:** none — the conversions are permanent.
- **information:** none computed.
- **visibility:** each converted player sees only their own "You are <Minion>" + evil
  cards. The Kazali is shown nothing extra (it already knows). The Minions learn each
  other at the normal `MINION_INFO` step.

### Other nights

- Keep the existing `DemonKillPanel` — it is correct — but suppress it on the **first
  night** for every Demon whose first-night step is not a kill (Kazali, Lil' Monsta,
  Lord of Typhon). Concretely: only render `DemonKillPanel` when
  `!isFirstNight || demonHasFirstNightKill(id)`.

### State needed

```kotlin
// Player
val originCharacterId: String? = null   // set whenever assignCharacter replaces a
                                        // character mid-game (Kazali, Pit-Hag, Barber…)
```

This is generically useful (Undertaker/Ravenkeeper history, bluff suggestions,
"what were they before" in the log) and is the minimum required here.

### UI text the step should display

- **"Kazali — create the Minions (n needed). Setup left this game with no Minions."**
- **"Point at a player, then at a Minion on the sheet. Script Minions only, no duplicates."**
- Per conversion: **"Wake <name>. Show 'YOU ARE' + <Minion>. Thumbs down."**
- Marionette: **"Marionette must be a neighbour of the Kazali. Do NOT wake them — they
  still think they are the <original>."**
- After the last: **"All Minions created. Pick bluffs from the freed-up good characters."**

### Data changes

- `night_and_jinxes.json`: add the **Summoner** jinx; verify or remove the Choirboy jinx.
- `night_guide.json:1540`: keep the prose but add a second show `{"label":"Thumbs
  down (evil)","kind":"evil"}` and mention the Marionette exception and the
  no-duplicates/script-only constraints.
- `characters.json:1968`: `firstNightReminder` should name the count —
  "…until the normal number of Minions exist."

## Tests to add

1. **Kazali bag validates with zero Minions.**
   *Given* 10 seats and a bag of Kazali + 9 Townsfolk/Outsiders; *when*
   `validateBag(bag, 10)`; *then* the result is empty. (Fails today with
   `"Minion: 0 in bag, expected 2"`.)

2. **A Kazali bag containing a Minion is rejected.**
   *Given* Kazali + 1 Poisoner + 8 good; *then* `validateBag` reports a Kazali/Minion
   conflict.

3. **`randomBag` with a Kazali draws no Minions.**
   *Given* a pool containing the Kazali; *when* `randomBag(pool, 10, seed)`; *then* the
   bag has exactly 1 Demon (the Kazali) and 0 Minions and 10 characters total.

4. **Outsider choice is honoured.**
   *Given* 10 seats, Kazali, and an ST-chosen Outsider count of 2; *then*
   `validateBag` accepts 1 Demon / 0 Minions / 2 Outsiders / 7 Townsfolk and rejects
   totals that do not sum to 10.

5. **`validateSetupState` accepts a correct Kazali game.**
   *Given* a dealt Kazali game with no Minions; *then* `validateSetupState` returns no
   issues (so "Begin night" does not pop the guard dialog).

6. **Conversion records the original character.**
   *Given* a Soldier seat; *when* the Kazali converts them to the Poisoner; *then*
   `characterId == "poisoner"`, `originCharacterId == "soldier"`, the player registers as
   evil, and `suggestBluffs` may return `"soldier"`.

7. **Marionette conversion sets a believed identity and neighbours the Kazali.**
   *Given* the Kazali at seat 3; *when* the Marionette is created on seat 4; *then*
   `characterId == "marionette"`, `shownCharacterId == <seat 4's original character>`,
   the `Is the Marionette` reminder exists, and creating it on a non-neighbour seat is
   rejected.

8. **Duplicate Minion characters are rejected.**
   *Given* the Kazali already created a Poisoner; *then* the Poisoner is not offered again
   and an attempt to assign it a second time is refused.

9. **No demon-kill tool on the Kazali's first night.**
   *Given* first night; *then* the Kazali step exposes no kill action (engine-level:
   `demonHasFirstNightKill("kazali") == false`).

10. **Mid-game Kazali does not re-create Minions.**
    *Given* a Pit-Hag turns a player into the Kazali on night 3; *then* the night-3 and
    night-4 Kazali steps offer only the kill, never the creation panel.

11. **Bounty Hunter jinx.**
    *Given* the Kazali converts the Bounty Hunter; *then* the Bounty Hunter info result no
    longer carries the "1 Townsfolk is evil" caveat.
