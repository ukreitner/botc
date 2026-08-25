# Fearmonger (fearmonger) — Experimental Minion

## Official rules (sources)

Source: https://wiki.bloodontheclocktower.com/Fearmonger (Character Text,
Summary, How to Run, Jinxes), fetched 2026-08-25.

**Ability text as shown on the wiki page fetched:**
> "Each night, choose a player: if you nominate & execute them, their team loses."

`characters.json:1778` carries a longer variant:
> "Each night, choose a player: if you nominate & execute them, their team loses. All players know if you choose a new player."

The second sentence is consistent with the wiki's own summary bullets (below),
so it is a restatement rather than a rules error — but the two texts should be
reconciled against the official script tool. **Flagged as an open data question,
not a defect.**

**Summary / clarifications (quotes):**
- "During the first night, when the Fearmonger selects a player, all players learn this."
- "During other nights, each time the Fearmonger selects a **new** player, all players learn this. If the Fearmonger selects the same player as previously, the players learn nothing."
- "The players only learn that the Fearmonger has acted, not which player was selected."
- "If the Fearmonger nominates their chosen player, and that nomination results in their execution, the chosen player loses, their team loses, and the game ends."
- "Only the currently chosen player is susceptible to the Fearmonger's ability. Previously chosen players don't count."
- **"If the chosen player is executed but does not die, the chosen player's team still loses."**

**How to Run (quotes):**
> "Each night, wake the Fearmonger. They point at any player. Put the Fearmonger to sleep. Mark the chosen player with the **FEAR** reminder. If the Fearmonger chose a player who wasn't already marked with the **FEAR** reminder, declare that 'The Fearmonger has chosen a player.' (This informs the group that the Fearmonger is alive and has chosen a new player.)"
> "If the Fearmonger nominates the player marked **FEAR**, and that nomination results in their execution, declare that the game is over and which team has won."

Notes that follow from the text:
- **The nominator must be the Fearmonger.** Any other player nominating the
  marked player is harmless.
- The **execution** is what triggers it, and it triggers **even if the executed
  player does not die**.
- The chosen player may be **any** player — including evil ones, including the
  Fearmonger themselves (whose team would then lose), including dead players
  ("they point at any player"). The tactical point is that the marked player's
  *team* loses, so marking an evil player is a real (if unlikely) option.
- The announcement is a public declaration made at the moment of the choice
  (eyes closed), which is why it "informs the group that the Fearmonger is
  alive". Practically the ST wants it available **both** at the night step and
  again in the morning.
- Standard rules: a drunk/poisoned Fearmonger's ability does not work; a **dead**
  Fearmonger has no ability *and* cannot nominate, so the trigger is only live
  while the Fearmonger is alive and sober. *(The wiki page does not spell this
  out; it follows from the general rules — flagged as inference.)*

**Jinxes (quotes):**
- Plague Doctor — "If the Storyteller would gain the Fearmonger ability, a Minion gains it, and learns this."
- **Vizier — "The Vizier wakes with the Fearmonger, learns who they choose and cannot choose to immediately execute that player."**

**Night order.** First night index 38 (after `cerenovus`, before `harpy`) —
`night_and_jinxes.json:333`. Other nights index 26 (after `cerenovus`, before
`harpy`) — `night_and_jinxes.json:399`. Both correct.

## What the app does today

- `characters.json:1778` — ability text (long variant); `reminders: ["Fear"]`;
  first-night reminder "The Fearmonger chooses a player. Announce that the
  Fearmonger has chosen a player."; other-night reminder "…If the target is
  different to last night, announce that the Fearmonger has chosen a player."
  Both are good prose.
- `night_guide.json:1334` — accurate first/other instructions, including the
  "same player → no announcement" rule and the win condition. One show card:
  a `message` card "CHOOSE A PLAYER TO THREATEN…".
- `night_and_jinxes.json:333,399` — correct night-order slots. **No Fearmonger
  jinx rows at all** (Plague Doctor and Vizier both missing).
- `StatusEffects.kt:158-160` — the only executable logic anywhere:
  ```kotlin
  if (nominee.reminders.any { it.label.equals("Fear", true) }) {
      notes += "Fearmonger chose ${nominee.name}: if executed from this nomination, their team loses."
  }
  ```
  surfaced by `DayScreen.kt:154-159` when the ST has selected a nominee.
- `"fearmonger" to "Fear"` appears in **neither** `EXPIRES_AT_DAWN`
  (`GameActions.kt:218-225`) nor `EXPIRES_AT_DUSK` (`GameActions.kt:231-242`) —
  correct, the token persists until moved.
- The token is placed through the generic night tray
  (`NightScreen.kt:193-357`); because `characters.json` declares exactly one
  "Fear" reminder, `availableCopies <= 1` and the tray uses
  `placeExclusiveReminder` (`NightScreen.kt:318-326` → `GameActions.kt:194-203`),
  so the token correctly *moves* rather than accumulating. **Works.**
- `WinCheck.check` (`WinCheck.kt:18-101`) has **no** Fearmonger branch.

**Storyteller experience today:** the night step tells you to wake the
Fearmonger and announce; you tap "Fear" in the tray and then the seat. Nothing
tells you whether that was the *same* player as last night, so you must remember
whether to announce. At dawn nothing reminds you to announce anything. During
the day, *if* you happen to select the marked player as the nominee, you get a
red warning — but you get the same warning no matter who the nominator is. If
the Fearmonger's nomination does execute the target, you must notice, remember
the rule, and end the game manually via the menu.

## Defects and gaps

1. **P0 · The nomination warning fires for the wrong nominator.**
   Rules: only a nomination *by the Fearmonger* triggers the loss. App:
   `StatusEffects.kt:158-160` ignores `nominatorId` entirely. Repro: mark Alice
   with Fear; select Bob (a Townsfolk) as nominator and Alice as nominee — the
   app warns "if executed from this nomination, their team loses". A storyteller
   trusting that warning will end the game on a nomination that does nothing.
   *(Inverse risk too: the same text appears for the Fearmonger's own nomination,
   so the ST cannot tell the two cases apart.)*

2. **P0 · The win is never detected or applied.**
   Rules: on execution "declare that the game is over and which team has won".
   `WinCheck.kt:18-101` has no Fearmonger case, and the Execute buttons
   (`DayScreen.kt:111-114`, `DayScreen.kt:350-357`, `GameShell.kt:604-610`) call
   `viewModel.kill` with no consequence hook at all. Repro: Fearmonger nominates
   the Fear-marked Townsfolk, they are executed → app just records a death;
   good has lost and the app shows nothing.

3. **P0 · "Executed but does not die" cannot be recorded.**
   Rules: "If the chosen player is executed but does not die, the chosen
   player's team still loses." The app has no notion of an execution that does
   not kill: `DeathRecord` is only created inside `GameActions.kill`
   (`GameActions.kt:136-157`), and the `SeatSheet` protection dialog's "Death
   prevented" branch (`SeatSheet.kt:296-299`) records **nothing**. Repro:
   Fear-marked player is a Devil's-Advocate-protected Sailor; Fearmonger
   nominates, execution passes, ST taps "Death prevented" → evil should have won
   and nothing at all is stored.

4. **P1 · No "new target?" detection, so the announcement is manual.**
   Rules: announce **only** when the choice changed. The app can compute this
   trivially (the Fear token's current seat before placement) but does not:
   `placeExclusiveReminder` (`GameActions.kt:194-203`) silently clears the old
   copy and returns no information. Repro: on night 3 place Fear on the same
   player as night 2 — the step text still says "announce that the Fearmonger
   has chosen a player", with a conditional the ST must resolve from memory.

5. **P1 · No dawn/day-start announcement queue.**
   The DAWN night step says only "Announce who died"
   (`NightOrder.kt:59`), and there is no day-start briefing anywhere in the UI
   (confirmed: no "briefing"/"announce" surface outside `NotesScreen`). The
   Fearmonger announcement is the archetypal "public statement the ST must not
   forget" — it belongs in a dawn briefing list.

6. **P1 · Nothing warns at nomination time that the *Fearmonger* is nominating.**
   The warning is attached to the nominee only. The ST needs the trigger
   surfaced the moment the nominator chip is tapped, and again on the
   about-to-die banner (`DayScreen.kt:93-115`) and on the Execute button.

7. **P2 · Impairment and death are not accounted for.**
   A dead or poisoned Fearmonger has no ability. `StatusEffects.kt:158-160` does
   not check the Fearmonger seat's state at all, so a stale Fear token from
   before the Fearmonger died still produces a red "their team loses" warning.

8. **P2 · Both Fearmonger jinxes are missing from `night_and_jinxes.json`.**
   The Vizier jinx in particular has real night-order consequences ("The Vizier
   wakes with the Fearmonger, learns who they choose") that the night sheet
   should express.

9. **P3 · Ability-text drift.** `characters.json:1778` carries "All players know
   if you choose a new player."; the wiki page as fetched shows only the first
   sentence. Confirm against the official script tool before changing either.

## Proposed behaviour (spec)

### Night step

- **when:** both first and other nights.
- **wake condition:** the Fearmonger seat is **alive**. If dead, render the step
  greyed with `Dead — the Fearmonger has no ability; leave the Fear token where
  it is (it no longer does anything).`
- **targets:** exactly 1; constraint: *any* player (alive or dead, self allowed,
  evil allowed). Picker default: the seat that currently holds the Fear token,
  pre-selected, so "same as last night" is one tap. Sort: seat order.
- **immediate effects:**
  - `placeExclusiveReminder(target, PlacedReminder("fearmonger", "Fear"))`.
  - Compute `changed = previousFearSeatId != target.id`.
  - If `changed`, push an **announcement** onto a new dawn/announcement queue
    (below) *and* show the step banner
    `ANNOUNCE NOW: "The Fearmonger has chosen a player."` If unchanged, show
    `Same player as last night — say nothing.`
  - If the Fearmonger is drunk/poisoned (`StatusEffects.isImpaired`), still let
    the ST place the token but add the caveat
    `Fearmonger is drunk/poisoned — the mark does nothing tonight; announce anyway.`
    *(The announcement is about the Fearmonger acting, not about the ability
    working, so the ST should still announce — flag this as a judgement call in
    the UI rather than deciding it silently.)*
- **deferred effects:** none at dawn beyond the announcement; the mark is
  consumed only by an execution.
- **expiry:** the Fear token **never** expires — do not add it to
  `EXPIRES_AT_DAWN`/`EXPIRES_AT_DUSK`. It moves when the Fearmonger re-chooses.
  When the Fearmonger dies, keep the token but mark it inert in tooltips.
- **information:** nothing is shown to the Fearmonger; the "CHOOSE A PLAYER TO
  THREATEN…" message card (`night_guide.json:1334`) is right.
- **visibility:** the whole table hears the announcement; nobody learns who.

### New engine state

```kotlin
/** Public statements the ST owes the table at dawn / day start. */
data class Announcement(val day: Int, val text: String, val delivered: Boolean = false)
val announcements: List<Announcement> = emptyList()
```

Dawn (`advancePhase` NIGHT→DAY, `GameActions.kt:260`) surfaces these in a new
**Dawn briefing** sheet: deaths first, then each queued announcement with a
"said it" checkbox. The Fearmonger contributes
`"The Fearmonger has chosen a player."`

### Nomination-time behaviour (`StatusEffects.nominationWarnings`)

Replace `StatusEffects.kt:158-160` with:

```kotlin
val fearmonger = state.players.find { it.characterId == "fearmonger" }
val fearSeat = state.players.find { p -> p.reminders.any { it.sourceId == "fearmonger" && it.label.equals("Fear", true) } }
if (fearmonger != null && fearSeat != null && nominee?.id == fearSeat.id) {
    val live = fearmonger.alive && !StatusEffects.isImpaired(state, lookup, fearmonger)
    when {
        nominator?.id == fearmonger.id && live ->
            notes += "FEARMONGER NOMINATION — if ${nominee.name} is executed by this nomination, ${nominee.name}'s team LOSES and the game ends immediately (even if they survive the execution)."
        nominator?.id == fearmonger.id ->
            notes += "${fearmonger.name} is the Fearmonger but is ${if (!fearmonger.alive) "dead" else "drunk/poisoned"} — the Fear mark does nothing."
        else ->
            notes += "${nominee.name} is marked Fear, but only a nomination BY ${fearmonger.name} (the Fearmonger) triggers it — this nomination is normal."
    }
}
```

The same three-way text must appear on the about-to-die banner
(`DayScreen.kt:93-115`) and in the execute confirmation.

### Execution consequence

Route every execution through the shared `GameActions.execute(state, playerId,
nominatorId, died)` funnel proposed in `boomdandy.md`, then:

- **trigger:** `ExecutionRecord.playerId == fearSeat.id` **and**
  `ExecutionRecord.nominatorId == fearmongerSeat.id` **and** the Fearmonger was
  alive and unimpaired at the time of the nomination. Fires whether or not
  `died`.
- **effect:** `WinCheck` returns a non-overridable-looking advisory:
  `Advisory(goodWins = !executedPlayerIsEvil, reason = "Fearmonger: {Fearmonger} nominated {target}, who was executed — {target}'s team loses.")`
  and the app opens the reveal flow.
- Note the alignment used must be the **current** alignment
  (`Player.isEvil(lookup)`, honouring `alignmentFlipped` — a Mezepheles-turned
  player counts as evil).

`DayScreen` must also stop losing the nominator: `Nomination.nominatorId` is
already stored (`GameState.kt:63-72`), so the execute button can find the
nomination that put the nominee on the block (`GameActions.aboutToDie`,
`GameActions.kt:295-305`) and pass its `nominatorId` through.

### Interactions / jinxes to add

- **Vizier** — "The Vizier wakes with the Fearmonger, learns who they choose and
  cannot choose to immediately execute that player." Night sheet: when both are
  in play, merge the Vizier into the Fearmonger step (`playerIds` includes both)
  and add `Wake the Vizier with the Fearmonger and show them the chosen player.`
  Also mark the chosen player as ineligible in any future Vizier
  "execute immediately" tool.
- **Plague Doctor** — "If the Storyteller would gain the Fearmonger ability, a
  Minion gains it, and learns this."
- Marked player becomes a Traveller / is exiled: exile is not an execution, so
  nothing triggers. Assert this in a test.

### UI text for the step

- First night: `Wake {Fearmonger}. They point at any player — mark them FEAR. Announce out loud: "The Fearmonger has chosen a player."`
- Other nights, changed: `New target — announce: "The Fearmonger has chosen a player."`
- Other nights, unchanged: `Same target as last night — announce nothing.`
- Day banner while a Fear mark exists and the Fearmonger is live:
  `{Fearmonger} (Fearmonger) can end the game by nominating {target}.` — but
  only in the storyteller-facing Day tab, never on a shown card.

### Data changes

- `night_and_jinxes.json`: add `{"id1":"fearmonger","id2":"vizier", …}` and
  `{"id1":"fearmonger","id2":"plaguedoctor", …}` with the wiki texts above.
- `night_guide.json:1334`: add to the `other` instructions the explicit
  "executed but survives still loses" clause, and the dead/poisoned Fearmonger
  case.

## Tests to add

1. *Given* Fear on Alice and a Fearmonger (Bob) alive, *when*
   `nominationWarnings(nominator = Carol, nominee = Alice)`, *then* the note says
   the nomination is **normal** and does not say "their team loses".
2. *Given* the same, *when* `nominationWarnings(nominator = Bob, nominee = Alice)`,
   *then* the note contains "team LOSES".
3. *Given* Bob (Fearmonger) is poisoned, *when* Bob nominates Alice, *then* the
   note says the mark does nothing.
4. *Given* Bob is dead, *then* the same (and note dead players cannot nominate
   at all — `DayScreen.kt:135-138` already blocks it; assert the engine agrees).
5. *Given* Fear on Alice, Bob nominates, Alice is executed and **dies**, *then*
   `WinCheck.check` returns an advisory naming the Fearmonger with
   `goodWins == false` when Alice is good.
6. *Given* Fear on Alice (an evil Minion), Bob (Fearmonger) nominates and Alice
   is executed, *then* the advisory has `goodWins == true`.
7. *Given* Fear on Alice, Bob nominates, execution passes but Alice survives
   (`execute(..., died = false)`), *then* the advisory still fires.
8. *Given* Fear on Alice and a night step that re-places Fear on Alice, *then*
   no `Announcement` is queued; *given* it is placed on Dan, *then* exactly one
   `Announcement("The Fearmonger has chosen a player.")` is queued for the
   following day.
9. *Given* Fear on Alice, *when* `advancePhase` runs through a full
   NIGHT→DAY→NIGHT cycle, *then* the Fear reminder is still on Alice (not swept
   by either expiry table).
10. *Given* Alice is a Traveller marked Fear and is **exiled** by the Fearmonger,
    *then* no Fearmonger advisory fires.
