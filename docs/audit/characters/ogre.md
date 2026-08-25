# Ogre (ogre) — Experimental Outsider

## Official rules (sources)

Sources:
- <https://wiki.bloodontheclocktower.com/Ogre>
- <https://wiki.bloodontheclocktower.com/Pit-Hag> (jinx wording)

Current ability text (matches `characters.json:1678`):

> "On your 1st night, choose a player (not yourself): you become their alignment (you don't know
> which) even if drunk or poisoned."

Summary/flavour: "The Ogre is someone's best friend."

How to Run (quoted):

> "During the first night, wake the Ogre. The Ogre points to a player. Put the Ogre to sleep. If the
> Ogre pointed to an evil player, flip the Ogre's character token upside down to represent that the
> Ogre is evil."
>
> "Optional rule: Mark the Ogre's chosen player with a FRIEND reminder. The Ogre is always the same
> alignment as their friend. If the Ogre's friend changes alignment, the Ogre changes alignment too,
> but the Ogre does not learn this."
>
> "This is only recommended for games of 15 players or more, so that there are not too many evil
> players."

Examples (quoted):

> "On the first night, the Ogre chooses the Summoner. The Ogre becomes evil, and stays evil for the
> rest of the game."
>
> "On the first night, the Ogre chooses the Banshee. The Ogre stays good. On the third night, the
> Mezepheles turns the Banshee evil. The Ogre remains good."

Key mechanics (quoted):

> "The Ogre's chosen player does not change, even if the Ogre is drunk or poisoned when they chose."
>
> "The Ogre becomes the same alignment as their chosen player immediately on the first night, even if
> the Ogre is drunk or poisoned."
>
> "The Ogre is not told their alignment at the beginning of the game."
>
> "If the Ogre changes alignment by other means, the Ogre learns their new alignment, as normal."
>
> "If an Ogre is created mid-game, the Ogre chooses a player that night, and becomes their alignment."

Points that matter for the app:

- **Base rule: the alignment is fixed once, on the Ogre's first night.** The FRIEND reminder and
  "tracks the friend forever" behaviour is the **optional** 15+-player variant. Example 2 is the
  proof: the friend turning evil later does *not* turn the Ogre evil under the base rule.
- **The Ogre still wakes and still becomes an alignment even when drunk or poisoned** — this is one
  of the very few abilities that explicitly survives impairment. The app must **not** show the usual
  "drunk/poisoned — give false info / the ability fails" warning here.
- **The Ogre is never told.** No signal, no card, no tell of any kind. They are still an **Outsider**
  (team unchanged); only their alignment flips. An evil Ogre wins with evil, is not woken with the
  Minions, does not learn who the Demon is, and the evil team does not learn about them.
- **"not yourself"** is a hard constraint.
- The Ogre wakes on the **first night only** (`otherNightReminder` is empty) — except when created
  mid-game, in which case they choose on their first night as the Ogre.
- If the Ogre's alignment later changes by some *other* ability (Mezepheles, Politician…), they
  **do** learn, as normal.

Jinxes (wiki):

| Partner | Text |
|---|---|
| Boffin | "The Demon cannot have the Ogre ability." |
| Pit-Hag | "If the Pit-Hag turns an evil player into the Ogre, they can't turn good due to their own ability." |
| Recluse | "If the Recluse registers as evil to the Ogre, the Ogre learns that they are evil." |
| Spy | "The Spy registers as evil to the Ogre." |

**None of these four exist in `night_and_jinxes.json`.** The Recluse one is unusual and important:
if the Ogre picks a Recluse and the Storyteller rules it registers evil, the Ogre *is told* they are
evil — the only case where the Ogre learns their alignment on night 1.

## What the app does today

Data:
- `characters.json:1675-1687` — correct ability text; `firstNightReminder: "The Ogre chooses a player."`;
  empty `otherNightReminder`; `reminders: ["Friend"]`.
- `night_guide.json:1290-1298` — good prose:
  > "Wake the Ogre and have them point to a player other than themself, then put them back to sleep.
  > Place the Friend reminder on the chosen player: the Ogre becomes that player's alignment but does
  > not learn which alignment it is, so give no signal. This works even if the Ogre is drunk or
  > poisoned."
  `shows: []` — no cards, correctly (nothing is shown to the Ogre).
- `night_and_jinxes.json:362` — firstNight index 67, immediately after `spy` (66) and before
  `highpriestess`. Matches the official sheet. Not on the other-night list — correct for the base
  case.
- No jinxes for `ogre`.

Engine: **zero** Ogre-specific code (grep `ogre` in `engine/src/main/kotlin` → nothing).
The one relevant primitive is `Player.alignmentFlipped` (`GameState.kt:25`) consumed by
`Player.isEvil` (`GameState.kt:49-52`), flipped by `GameActions.flipAlignment`
(`GameActions.kt:129-130`).

UI walk-through:
1. **Night 1, step "Ogre"** appears at the right place with the correct guide prose
   (`NightScreen.kt:792-801`).
2. The tool tray (`NightScreen.kt:283-306`) offers a `Friend` chip; tapping it then a seat places
   `PlacedReminder("ogre","Friend")` exclusively (`NightScreen.kt:318-340` →
   `GameActions.placeExclusiveReminder`). **Works**, and is genuinely two taps.
3. The seat list in the tray (`NightScreen.kt:315`) is `state.players` — **the Ogre themself is
   offered**, contradicting "not yourself".
4. **Nothing flips the alignment.** `QuickResolutions` (`NightScreen.kt:462-525`) has no `ogre`
   case, and the night step never tells the ST that the chosen player is evil. The ST must open the
   grimoire, work out whether the chosen seat is evil (the circle does colour evil names red,
   `GrimoireScreen.kt:374-382`), open the Ogre's seat sheet, and tap "Flip alignment"
   (`SeatSheet.kt:315`) — with no confirmation of what that did beyond a "· turned evil" suffix
   (`SeatSheet.kt:188`).
5. **The Friend token never expires and never does anything.** It is not in `EXPIRES_AT_DAWN`/
   `EXPIRES_AT_DUSK` (`GameActions.kt:218-242`) — correct — but nothing reads it, so the optional
   "tracks the friend" rule cannot be run either.
6. **Impairment.** The Ogre has no `InfoCalc` support, so no false-info panel appears — good by
   accident. But nothing states positively "this works even if drunk/poisoned" outside the guide
   prose, and the generic advice in the night screen footer ("Dead players usually don't act")
   is unhelpful noise.
7. **Mid-game Ogre.** A Pit-Hag/Amnesiac-created Ogre gets **no** night row ever, because `ogre` is
   only on the first-night list and `NightOrder.build` (`NightOrder.kt:40-45`) is driven purely by
   which sheet is being built.
8. **Alignment leak.** `RevealFlow.kt:53-58, 108-115` colours the player's own "YOU ARE" card red
   when `player.isEvil(...)` is true. Once the ST has flipped an evil Ogre, re-running "Reveal
   characters to players…" (`GameShell.kt:230-233`) would show that Ogre their character name in
   **red** — telling them they are evil, which the rules forbid.
9. **Jinxes.** Nothing: no Recluse/Spy handling, so the ST is never told that a chosen Spy always
   registers evil, or that a Recluse registering evil means the Ogre *is* told.

## Defects and gaps

1. **P0 · The alignment flip is entirely manual and unprompted.** Rules: "If the Ogre pointed to an
   evil player, flip the Ogre's character token upside down." App: no `ogre` case in
   `QuickResolutions` (`NightScreen.kt:470-524`); the step never says whether the chosen player is
   evil, even though the grimoire knows. Repro: night 1, Ogre picks the Poisoner — the app places a
   Friend token and says nothing; the Ogre stays good in state, so every subsequent Empath/Chef/
   Fortune Teller/Investigator/Undertaker calculation in `InfoCalc` is **wrong**. This is a silent
   wrong-information bug, the worst class in this app.

2. **P0 · Four official jinxes are missing from the data** (`night_and_jinxes.json` has none for
   `ogre`): Boffin, Pit-Hag, Recluse, Spy. The Recluse and Spy ones change the outcome of the
   Ogre's night-1 choice, and the Recluse one is the single exception to "the Ogre is not told".

3. **P1 · The Ogre can be offered as their own target.** Rules: "choose a player (**not yourself**)".
   App: the tray's seat list is unfiltered (`NightScreen.kt:315`).

4. **P1 · A mid-game Ogre never gets a night step.** Rules: "If an Ogre is created mid-game, the
   Ogre chooses a player that night, and becomes their alignment." App: `ogre` is absent from the
   other-night order (`night_and_jinxes.json` otherNight list), so no row is ever produced.

5. **P1 · The Friend token has no meaning attached and the optional rule cannot be run.** Rules:
   the FRIEND reminder is specifically the marker for the optional "the Ogre tracks their friend"
   variant (15+ players). App: the token is placed as if it were the base rule
   (`night_guide.json:1292`) and then nothing consumes it — the base rule and the optional rule are
   conflated, and neither is automated.

6. **P1 · Nothing prevents or flags the alignment being wrong afterwards.** Because
   `alignmentFlipped` is a manual boolean, an undo/redo, a character change, or a `starPass`
   (`GameActions.kt:88-94` resets `alignmentFlipped = false`) can silently desync the Ogre's
   alignment from the rule that produced it. There is no record of *why* a player is flipped.

7. **P2 · Re-running the reveal flow can tell an evil Ogre they are evil.**
   `RevealFlow.kt:53-58` derives `evil` from `player.isEvil(...)` and colours the name red
   (`:108-115`). Repro: flip the Ogre evil on night 1, then use "Reveal characters to players…" —
   the Ogre sees a red card.

8. **P2 · No positive "this works while drunk/poisoned" affordance in the step UI.** The guide text
   says it, but the app's general pattern is a red impairment warning, and an ST who is used to that
   pattern may hesitate. The Ogre step should state it in the step detail itself, not only in the
   collapsible prose.

9. **P2 · No day-start/end-of-game surface for a flipped Ogre.** The ST must remember for the whole
   game that an evil Ogre exists (for win checks, for the Empath/Chef numbers, for the reveal). The
   reveal sheet (`GameExtras.kt:298-311`) does colour the Ogre by `isEvil`, which is right, but
   nothing during the game reminds the ST.

10. **P3 · `characters.json:1681` first-night reminder is terse** ("The Ogre chooses a player.")
    compared with the official sheet, which also carries the "flip the token if evil" instruction.

## Proposed behaviour (spec)

### Night step

- **when:** the Ogre's **first night as the Ogre** — i.e. night 1 if in the starting bag, otherwise
  the first night after they became the Ogre. Implement as: emit the `ogre` row on **both** the
  first-night and other-night sheets, gated on "this seat has no `ogre:Friend` choice recorded yet".
  Add `ogre` to the `otherNight` list in `night_and_jinxes.json` at the position matching the first
  night (after `spy`, before `highpriestess`).
- **wake condition:** alive; **not** gated on impairment ("even if drunk or poisoned"). Suppress the
  standard impairment warning for this step.
- **targets:** exactly 1, **alive**, **not self**. Picker should list all other seats in seat order
  with no evil/good visual cue whatsoever in the picker itself (the ST must not be nudged), then
  reveal the consequence after the choice is locked in.

### Immediate effects (a `QuickResolutions` case)

On confirming the chosen seat:

1. Place `PlacedReminder("ogre", "Friend")` on the chosen seat (exclusive).
2. Compute `friendIsEvil = chosen.isEvil(lookup)` with jinx overrides:
   - chosen is the **Spy** → always evil ("The Spy registers as evil to the Ogre") — no ST choice.
   - chosen is the **Recluse** → offer the ST the misregistration choice explicitly:
     `[Registers as good] [Registers as evil — and the Ogre LEARNS they are evil]`.
   - chosen is a **Traveller** → their alignment is whatever it is; no special case.
3. If `friendIsEvil`, set `alignmentFlipped = true` on the Ogre seat (a new action
   `GameActions.setOgreAlignment(state, ogreId, evil)` rather than the toggle
   `flipAlignment`, so it is idempotent and undo-safe).
4. Record the reason on the seat note / a structured field: `"Ogre — friend is <name> (evil)"`, so
   the flip is auditable and survives an accidental toggle.
5. Display the result **to the storyteller only**, in plain words:
   - **"<Ogre> is now EVIL (their friend <name> is evil). Give no signal — the Ogre does not learn
     this."**
   - or **"<Ogre> stays GOOD (their friend <name> is good). Give no signal."**
   - Recluse-evil branch only: **"Recluse registers evil — wake the Ogre and show the EVIL card."**
     (with a one-tap `ShowCard.AlignmentCard(evil = true)`, which
     `NightScreen.kt:808-812` already supports via a `"evil"` guide show.)

### Optional rule (15+ players)

- A per-game toggle "Ogre tracks their friend's alignment" (default **off**, auto-suggested when
  `players.size >= 15`).
- When **on**: whenever the friend's `isEvil` changes (alignment flip, character change, star pass),
  recompute the Ogre's alignment and tell the ST: "<Friend> changed alignment — <Ogre> is now
  evil/good. The Ogre does not learn this."
- When **off** (the default and the rule as written): the Friend token is a memory aid only; later
  changes to the friend do nothing. Say so in the step text so the ST does not over-apply it.

### Expiry

- `ogre:Friend` — **never** expires (keep it out of both tables in `GameActions.kt:218-242`).

### Information / visibility

- The Ogre is shown **nothing** — with the single Recluse-jinx exception above.
- The evil team is told nothing about the Ogre; an evil Ogre is **not** added to Minion Info
  (`NightOrder.kt:60-80` filters on `Team.MINION`, so this is already correct — keep it that way and
  do not let `isEvil` leak into that list).
- The Ogre remains an **Outsider** for every count (Librarian, Baron math, Godfather's "an Outsider
  died today", Fang Gu). Verify `alignmentFlipped` never changes `team`.
- **RevealFlow must not colour an Ogre's own card by alignment.** Either derive the reveal colour
  from the *character's* natural team (`character?.team?.isEvil`) rather than `player.isEvil`, or
  special-case any seat whose alignment was set by the Ogre ability. Fix at `RevealFlow.kt:53-58`.

### Day-time inputs

- None.

### Briefings

- Day-start (storyteller-only), while an Ogre is in play and flipped: **"<Ogre> is an evil Outsider
  (Ogre) — they do not know."** This keeps the Chef/Empath numbers honest and stops the ST from
  forgetting at the win check.
- End-of-game reveal: annotate the Ogre line with "(Ogre — became evil via <friend>)".

### Data changes

- `night_and_jinxes.json`:
  - add `ogre` to the `otherNight` list (same relative position) so a mid-game Ogre gets a step;
  - add the four missing jinxes:
    - `boffin`×`ogre`: "The Demon cannot have the Ogre ability."
    - `pithag`×`ogre`: "If the Pit-Hag turns an evil player into the Ogre, they can't turn good due to their own ability."
    - `recluse`×`ogre`: "If the Recluse registers as evil to the Ogre, the Ogre learns that they are evil."
    - `spy`×`ogre`: "The Spy registers as evil to the Ogre."
- `characters.json:1681-1682`: extend `firstNightReminder` to
  "The Ogre chooses a player (not themself). If that player is evil, flip the Ogre's token — the
  Ogre is not told." and add the same text as `otherNightReminder` (for the mid-game case).
- `night_guide.json:1290`: split the base rule from the optional rule; add an `"evil"` show card for
  the Recluse-jinx branch.

### UI text for the step

- Detail: **"Wake the Ogre. They point at another player (not themself). Put them to sleep. Give no
  signal — they do not learn their alignment. This works even if the Ogre is drunk or poisoned."**
- After the pick: **"Friend: <name> (evil) — <Ogre> is now EVIL."** / **"Friend: <name> (good) —
  <Ogre> stays GOOD."**

### Interactions/jinxes to handle explicitly

- **Spy** — always registers evil to the Ogre (no ST choice).
- **Recluse** — ST chooses; if evil, the Ogre **is** told (show the EVIL card).
- **Pit-Hag** — a Pit-Hag-made evil Ogre cannot turn good by their own ability; also, a mid-game
  Ogre must get a night step (see above).
- **Boffin** — the Demon cannot have the Ogre ability.
- **Mezepheles / Politician / other alignment changers** — "If the Ogre changes alignment by other
  means, the Ogre learns their new alignment, as normal", and (base rule) a friend who later changes
  alignment does **not** move the Ogre.
- **Snake Charmer / star pass** — `GameActions.snakeCharmerSwap` (`:64-72`) and `starPass` (`:88-94`)
  hard-reset `alignmentFlipped = false`. If either touches the Ogre seat, the Ogre's ability-derived
  alignment must be recomputed, not wiped.
- **Cult Leader / Goon** — both change alignment; interacts with the "learns their new alignment"
  clause.

## Tests to add

1. `NightOrderTest`: *Given* an Ogre in play on night 1, *then* the first-night sheet contains an
   `ogre` row whose detail mentions "not themself". *Given* the same Ogre on night 2 with a Friend
   token already placed, *then* the other-night sheet contains **no** `ogre` row.
2. `NightOrderTest`: *Given* a player who becomes the Ogre on night 4 (no Friend token), *then* the
   night-4 sheet **does** contain an `ogre` row. Fails today (`ogre` absent from the other-night
   list).
3. `GameActionsTest` (`setOgreAlignment`): *Given* an Ogre who chooses the Poisoner, *then* the Ogre
   seat has `alignmentFlipped = true`, `isEvil` is true, the chosen seat carries `ogre:Friend`, and
   the Ogre's `team` is still `OUTSIDER`. Fails today (no such action).
4. `GameActionsTest`: *Given* an Ogre who chooses a good player, *then* `alignmentFlipped` is false
   and calling the resolver twice does not toggle it (idempotence — `flipAlignment` fails this).
5. `GameActionsTest`: *Given* an Ogre with a Friend token, *when* `advancePhase` runs through dawn
   and dusk, *then* the `ogre:Friend` token survives.
6. `InfoCalcTest`: *Given* an Ogre made evil sitting between an Empath's alive neighbours, *then*
   the Empath's count includes the Ogre as evil. (Pins that `alignmentFlipped` propagates —
   currently only reachable by a manual flip.)
7. `GameDataTest`: *Given* `listOf("ogre","spy")`, *then* `activeJinxes` is non-empty and the reason
   is "The Spy registers as evil to the Ogre." Fails today (jinx missing). Repeat for `recluse`,
   `pithag`, `boffin`.
8. `StatusEffectsTest`: *Given* a **poisoned** Ogre, *then* the Ogre night step still resolves and
   still sets the alignment (no impairment gate).
9. UI-level (or `NightOrder` detail assertion): the Ogre's target list excludes the Ogre's own seat.
