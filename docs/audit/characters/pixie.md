# Pixie (pixie) — Experimental Townsfolk

## Official rules (sources)

Source: https://wiki.bloodontheclocktower.com/Pixie (fetched 2026-08-25).

Current ability text (matches `characters.json` exactly — no drift):

> "You start knowing 1 in-play Townsfolk. If you were mad that you were this character, you gain their ability when they die."

How to Run (wiki, quoted/paraphrased):

- **First night.** "During the first night, mark a Townsfolk character token with the Pixie's **MAD** reminder." Wake the Pixie, show them that Townsfolk character token, put them to sleep.
- The Pixie learns **a character, not a player**. They are never told which player holds it.
- **On the marked Townsfolk's death.** "If the Townsfolk marked **MAD** dies, and you feel that the Pixie player has been sufficiently **mad** that they were this character" → "replace the **MAD** reminder with the **HAS ABILITY** reminder. The Pixie now has this Townsfolk's ability." The Pixie then wakes at night for that ability from that point on.
- **Madness is a Storyteller judgement**, made at the moment the marked Townsfolk dies, based on the whole game up to then. Explicitly claiming "I am the Pixie" breaks madness and forfeits the ability.
- If the marked Townsfolk **changes character and then dies**, the Pixie gains the ability of the **originally-learned** character (the one on the MAD token), not the new one.
- The Pixie is **not told** they have gained the ability — they typically discover it by being woken.
- Once gained, the ability is **permanent** (it is the Pixie's own ability now; it does not stop if the dead player is resurrected).
- **Drunk/poisoned Pixie:** the shown token may be a **not-in-play** Townsfolk (one of the wiki's three examples is exactly "a drunk Pixie learning a non-existent character"). A drunk/poisoned Pixie whose marked character dies does not gain a working ability while impaired.
- **Jinxes: none.** The wiki lists no jinxes for the Pixie, and neither does the app's data (correctly).

Unresolved in the wiki (flagged, not guessed):
- Whether the marked Townsfolk may be a character the *Drunk* is shown, the Pixie's own character, or a dead player's character — the page does not restrict it beyond "a Townsfolk character token" (in practice: an **in-play** Townsfolk, per the ability text).
- What happens if the marked player dies while the **Pixie is dead**. Not addressed. (A dead Pixie has no ability, so the practical reading is: place HAS ABILITY anyway; it does nothing until/unless they are resurrected.)

## What the app does today

Data / order:
- `engine/src/main/resources/botc/data/characters.json:1506` — id/name/ability/first-night reminder ("Show the Townsfolk character token marked 'Mad'.") and reminders `["Mad", "Has Ability"]`. **Text is correct and current.**
- `engine/src/main/resources/botc/data/night_and_jinxes.json:337` — first night only, slot 42 (after Pukka, before Huntsman). Correct position. No other-night entry — correct for the base ability, **wrong once the Pixie has gained an ability** (see D5).
- `engine/src/main/resources/botc/data/night_guide.json:1132` — a good first-night prose entry plus one prepared show card `{"label":"Mad as","kind":"token","text":"YOU ARE MAD THAT YOU ARE","token":"pick"}`.

Runtime:
- `NightOrder.build` (`engine/.../NightOrder.kt:120-181`) emits a plain step whose `detail` is the `firstNightReminder` string. No computation.
- `NightScreen.StepDetailPanel` (`app/.../NightScreen.kt:773-835`) renders the guide prose and the "» Mad as" chip; the chip opens `GuideShowDialog` (`NightScreen.kt:357-458`), which lets the ST pick **any** script character (in-play sorted first) and shows a full-screen "YOU ARE MAD THAT YOU ARE + token" card. **This part works and is genuinely good.**
- `InfoCalc.supports` (`InfoCalc.kt:29`) does **not** include `pixie`, so no true-info panel, no "which Townsfolk are in play" list, no impairment caveat is shown on the step.
- Reminder placement is manual via `NightToolTray` (`NightScreen.kt:186-355`): tap "Mad" or "Has Ability", tap a seat. Both are single-copy, so `placeExclusiveReminder` moves them.
- Neither `Mad` nor `Has Ability` is in `EXPIRES_AT_DAWN`/`EXPIRES_AT_DUSK` (`GameActions.kt:218,231`) — correct, they persist.

Storyteller experience today: on night 1 you read the prose, tap "Mad as", search for a Townsfolk, show the card. Then **everything else is on you** — remembering which character you showed (the app stores nothing), noticing when that player dies, judging madness, swapping the token, and hand-running the gained ability for the rest of the game.

## Defects and gaps

1. **P0 · The gained ability never wakes.** After you place `Has Ability`, the Pixie has (say) the Empath's ability, but `NightOrder` keys every step off `Player.nightRoleId` (`GameState.kt:36-42` → `characterId`), so the Pixie never appears on the Empath's night step and `InfoCalc` is never run for them. Repro: place `pixie:Has Ability` on the Pixie seat after the marked Empath dies; go to Night 3 — no Empath row for the Pixie, and the real Empath row is gone (holder dead). The ST must hand-run the whole ability for the rest of the game. `NightOrder.kt:44-56`, `NightScreen.kt:836-837`.
2. **P0 · Nothing records which Townsfolk was shown.** The show card is transient UI state (`NightScreen.kt:365-370`) and is discarded on dismiss. The only record is if the ST manually drops the `Mad` token on the *seat* of the player holding that character — which is impossible for a drunk Pixie shown a **not-in-play** Townsfolk, and ambiguous if two seats could plausibly hold it. Two nights later the ST cannot answer "what was the Pixie mad about?" from the app. Repro: night 1 → show "Empath" → next day, open the grimoire; nothing anywhere says "Empath".
3. **P1 · No trigger when the marked Townsfolk dies.** `StatusEffects.deathNotes` (`StatusEffects.kt:95-104`) has a `when (id)` list of on-death triggers (Ravenkeeper, Sage, Farmer, Poppy Grower…) but nothing keyed on the `pixie:Mad` reminder. Repro: mark the Empath `Mad`, then kill the Empath from `DemonKillPanel` — no note, no prompt to judge madness, no offer to swap the token. `NightScreen.kt:584-587` shows `deathNotes` for the demon target; the Pixie case is silent.
4. **P1 · No setup / first-night prompt to choose the mad character.** `GameShell` has dedicated pre-night prompts for the Drunk, Lunatic, Marionette and Fortune Teller red herring (`GameShell.kt:340-478`), and `validateSetupState` (`GameActions.kt:503-560`) enforces them. The Pixie's mad character is exactly the same class of mandatory hidden choice and has neither.
5. **P1 · `Has Ability` is a dead token.** Nothing in the engine reads `label == "Has Ability"`. It does not affect the night order, `InfoCalc`, `deathNotes`, or the day briefing.
6. **P2 · False Cerenovus warning at nomination.** `StatusEffects.nominationWarnings` (`StatusEffects.kt:162`) matches on the *label* `"Mad"` regardless of `sourceId`, so the player carrying the Pixie's `pixie:Mad` token gets "X is Cerenovus-mad — check their claim before this goes further." whenever they nominate. Repro: place `pixie:Mad` on any seat, nominate with them on the Day tab.
7. **P2 · No impairment caveat on the Pixie's step.** Because `InfoCalc` doesn't support `pixie`, a poisoned/drunk Pixie produces no "! …give false info" line; only the guide prose mentions it in passing. Compare Steward/Shugenja, which do show it.
8. **P2 · The "Mad as" picker offers Demons/Minions/Travellers.** `GuideShowDialog` filters only `team != FABLED` (`NightScreen.kt:396-399`). The rules say a **Townsfolk** token, and the ability says an **in-play** Townsfolk (unless the Pixie is impaired). The picker should default to in-play Townsfolk and require a deliberate override.
9. **P3 · No day-start reminder that a Pixie madness judgement is pending.** There is no day briefing surface at all (cross-cutting), so "watch whether Alex keeps claiming Empath" is entirely in the ST's head for potentially the whole game.

## Proposed behaviour (spec)

### New persisted state
Reminders alone cannot carry "which character", because the marked character may not be in play. Add either:
- a typed field on `GameState`, e.g. `pixieMadCharacterId: Map<Long, String>` (Pixie seat → character id), **or** (lower-churn, reuses existing plumbing) encode it in the reminder label: `PlacedReminder("pixie", "Mad: Empath")` placed **on the Pixie's own seat**, plus an optional second `PlacedReminder("pixie","Mad")` on the seat believed to hold it.

Recommendation: a typed field. Everything below assumes `state.pixieMad[pixieSeatId] = characterId` and a boolean `state.pixieHasAbility[pixieSeatId]`. Reminders stay as visible mirrors.

### Night step
- **when:** first night only (for the base ability). Wake condition: Pixie seat exists and is alive. If a Pixie is **created mid-game** (Pit-Hag/Amnesiac), run this step on the night they are created — same cross-cutting "re-run first-night info" mechanism the Steward/Shugenja need.
- **targets:** none (no player is chosen). Instead: **1 character pick**, constrained to `team == TOWNSFOLK`; default list = in-play Townsfolk, sorted alphabetically, with a collapsed "not in play" section that is only expanded automatically when the Pixie is impaired.
- **immediate effects:**
  - store `pixieMad[pixie.id] = chosenCharacterId`;
  - place `PlacedReminder("pixie", "Mad")` on the seat of the player holding that character **if exactly one seat holds it**; otherwise place nothing and rely on the stored field;
  - set the Pixie's seat note to `Mad that they are the <Name>` (mirrors the Drunk/Lunatic note pattern at `GameShell.kt:404-408`).
- **information:** show card "YOU ARE MAD THAT YOU ARE" + the chosen token (already implemented — keep it, just pre-select the stored choice).
- **impaired alternative:** if `StatusEffects.isImpaired(pixie)`, the step must say `! Pixie is drunk/poisoned — you may show a NOT-in-play Townsfolk; they will not gain a working ability.` and pre-expand the not-in-play list.
- **expiry:** never. `pixie:Mad` and `pixie:Has Ability` stay out of both expiry tables.
- **visibility:** nothing is shown to the Demon or Minions about the Pixie.

### Deferred effect — the marked character's player dies
- **trigger:** any death (execution, demon, storyteller) of a player whose `characterId` (or whose `characterIdAtDeath`) equals `pixieMad[pixieSeat]`. Use `DeathRecord.characterIdAtDeath` so a later character change cannot rewrite it, and honour the "original character" rule: match on the **stored mad character id**, not on the victim's current character.
- **what the ST is told, and when:** immediately in `StatusEffects.deathNotes` (so it appears in `DemonKillPanel` and in the seat-sheet kill confirmation), text:
  `Pixie (<PixieName>) was mad that they are the <Character>. If they were mad enough, they gain that ability now.`
  and again in the **dawn/day-start briefing** if the death happened at night.
- **ST input the app must offer:** a two-button prompt at that moment — `Mad enough → grant ability` / `Not mad enough`. Granting:
  - sets `pixieHasAbility[pixieSeat] = true`;
  - moves the token: remove `pixie:Mad`, add `PlacedReminder("pixie", "Has Ability")` **on the Pixie's seat**;
  - appends to the Pixie's note `Has the <Character> ability`;
  - is fully undoable (goes through `viewModel.update`).
- **impairment gate:** if the Pixie is impaired *at the moment of the grant*, still allow the grant (the token is a record) but warn `! The Pixie is drunk/poisoned — the gained ability malfunctions while that lasts.`
- **once only:** if `pixie:Has Ability` is already placed, do not re-prompt.

### Night order once the ability is gained
`NightOrder.build` must gain a second source of wake rows. Minimal change:

```
// in build(), after `inPlay` is computed
val granted: Map<String, List<Player>> = state.players
    .filter { it.reminders.any { r -> r.sourceId == "pixie" && r.label == "Has Ability" } }
    .mapNotNull { p -> state.pixieMad[p.id]?.let { it to p } }
    .groupBy({ it.first }, { it.second })
```
…then, in the `else ->` branch, treat `holders = inPlay[id].orEmpty() + granted[id].orEmpty()`, and annotate the granted holders in the row: `"<Name> (Pixie, gained ability)"`. This is the same generic hook the **Boffin**, **Alchemist**, **Cannibal** and **Philosopher** all need — build it once as `GameState.abilityHolders(characterId): List<Player>` and have `NightOrder`, `InfoCalc` (holder selection) and the "All holders are dead — usually skip" hint all consume it.

Consequences that fall out for free once that hook exists:
- the Pixie shows up on the Empath row and `InfoCalc.compute(..., "empath", holderId = pixieSeat)` computes **from the Pixie's seat** (their neighbours, not the dead Empath's) — which is the correct rule;
- once-per-game abilities gained this way get their own "Mark spent" token on the Pixie's seat (`NightScreen.kt:262-277` already keys on holders).

### UI text the step should display
- Title row: `Pixie — <player name>`
- Step detail (first night): `Pick an in-play Townsfolk. Show that token. They must be mad they are it.`
- Below the picker, once chosen: `Mad as: <Character> · held by <PlayerName>` (or `· not in play` when impaired).
- On the death trigger: `<Character> just died. Was <PixieName> mad enough about being the <Character>?  [Grant ability] [No]`
- Once granted, on every later night row for that character: `<PixieName> has this ability (Pixie).`

### Data changes
- `night_guide.json:1132` — add an `other` entry: `{"instructions": "Only if the Pixie has gained an ability (Has Ability token). Run the gained character's night action for the Pixie, from the Pixie's own seat."}`. Today `NightGuide.forStep("pixie", isFirstNight=false)` returns null.
- `characters.json:1506` — no change needed (text is current).
- `night_and_jinxes.json` — no change (Pixie has no jinxes and correctly has no other-night slot; the gained ability wakes on the *gained character's* row, not the Pixie's).
- `StatusEffects.kt:162` — scope the Cerenovus nomination warning to `sourceId in setOf("cerenovus","harpy")` (fixes D6 and the same false positive for the Harpy/Sentinel `Mad` tokens).

## Tests to add

1. `pixie madness choice is recorded` — Given a 7-player game with a Pixie (seat 0) and an Empath (seat 3); When the ST records the Pixie's mad character as `empath`; Then `state.pixieMad[0] == "empath"` and seat 3 carries `PlacedReminder("pixie","Mad")`.
2. `pixie death note fires on the marked character's death` — Given the above; When `GameActions.kill(state, 3, DEMON)`; Then `StatusEffects.deathNotes(state, lookup, 3)` contains a note naming the Pixie and the Empath. (Today: empty for this case.)
3. `pixie death note uses the ORIGINAL character after a character change` — Given seat 3 was the marked Empath and is later `assignCharacter(…, "chef")`; When seat 3 dies; Then the note still says the Pixie gains the **Empath** ability.
4. `granted pixie wakes on the gained character's night row` — Given `pixie:Has Ability` on seat 0 and `pixieMad[0] == "empath"` and the real Empath (seat 3) dead; When `nightOrder.otherNight(state, lookup)`; Then a step with `id == "empath"` exists with `playerIds == listOf(0L)`. (Today: no `empath` step at all.)
5. `granted pixie computes info from their own seat` — Given the above with evil at seats 1 and 7; When `InfoCalc.compute(data, state, "empath", holderId = 0)`; Then the headline counts **seat 0's** alive neighbours.
6. `pixie mad token does not trigger the cerenovus nomination warning` — Given `PlacedReminder("pixie","Mad")` on seat 4; When `StatusEffects.nominationWarnings(state, lookup, nominatorId = 4, nomineeId = 5)`; Then no note contains "Cerenovus". (Today: it does.)
7. `pixie mad and has-ability tokens survive dawn and dusk` — Given both tokens placed; When `advancePhase` twice; Then both remain.
8. `night guide has an other-night entry for pixie` — `NightGuide.forStep("pixie", isFirstNight = false) != null`.
