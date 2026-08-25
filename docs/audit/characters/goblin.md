# Goblin (goblin) — Experimental Minion

## Official rules (sources)

Source: https://wiki.bloodontheclocktower.com/Goblin (Character Text, Summary,
How to Run, Examples, Tips & Tricks, Jinxes), fetched 2026-08-25.

**Current ability text (quote):**
> "If you publicly claim to be the Goblin when nominated & are executed that day, your team wins."

`characters.json:1792` matches verbatim. No drift.

**How to Run (quotes):**
> "If the Goblin claims to be the Goblin when nominated (and before voting begins), declare that this player has claimed to be the Goblin, so that all the group hears. Put the **CLAIMED** reminder by the Goblin token."
> "If the Goblin is executed, and they are marked **CLAIMED**, then declare that evil wins."
> "If a non-Goblin player claims to be the Goblin when nominated, act as if they are the Goblin. Declare to the group that they have claimed to be the Goblin and pretend to move a reminder token in the Grimoire."

**Summary / clarifications (quotes):**
- "If the Goblin is executed, evil wins."
- "…but for this to happen the Goblin needs to tell the group that they are the Goblin when they are nominated, but before votes happen, and to do so in a way that everyone hears. The good players need to know the risk."
- "If the Goblin is executed without telling the group that they are the Goblin when nominated, the Goblin dies and the game continues as normal."
- **"The Goblin must have claimed to be the Goblin today for their ability to work. Telling the group yesterday, or even every previous day, doesn't count."**
- "Any player may claim to be the Goblin when nominated."

**Examples (wiki):** a Goblin who claims and is executed → evil wins; an Artist
who claims Goblin and is executed → game continues; a Goblin who claimed
*yesterday* but not today → executed, game continues.

Points the text implies and the app must respect:
- The claim window is **nomination → before voting starts**, on the **same day**
  as the execution.
- The trigger is **execution**, not death per se. *The wiki does not state
  whether an execution that fails to kill (Devil's Advocate) still wins;
  by the wording "are executed that day" it should, matching the Boomdandy's
  explicit ruling — flagged as an inference, surface it as an ST choice.*
- A drunk/poisoned Goblin's ability does not work (general rules; the wiki page
  does not spell it out — **flagged as inference**).
- Non-Goblin claims must be publicly announced and *pretend*-marked, so the
  Grimoire tooling should let the ST record a claim by anyone, and only the real
  unimpaired Goblin's claim wins.

**Night order.** None — the Goblin never wakes. Correctly absent from both
lists.

**Jinxes (quotes):**
- Cerenovus — "The Cerenovus may choose to make a player mad that they are the Goblin."
- Plague Doctor — "If the Storyteller would gain the Goblin ability, a Minion gains it, and learns this."

## What the app does today

- `characters.json:1792` — correct ability, `reminders: ["Claimed"]`.
- `night_guide.json` — **no `goblin` entry** (correct in the sense that the
  Goblin has no night action, but it also means the app never shows the ST *any*
  Goblin how-to-run prose during play, because the guide is only surfaced from
  night steps at `NightScreen.kt:792`).
- `night_and_jinxes.json:155` — the Cerenovus/Goblin jinx is present:
  `{"id1":"cerenovus","id2":"goblin","reason":"The Cerenovus may choose to make a player mad that they are the Goblin, instead of a good character."}` — **works**.
  The Plague Doctor jinx is missing.
- `GameActions.kt:241` — `"goblin" to "Claimed"` is in `EXPIRES_AT_DUSK`, and
  `advancePhase` DAY→NIGHT clears it (`GameActions.kt:261`). This correctly
  implements "must have claimed **today**". **Works.**
- Nothing else. No nomination-time affordance, no execution consequence, no
  `WinCheck` branch, no `deathNotes` entry.

**Storyteller experience today:** when a player claims Goblin during a
nomination, nothing in the Day tab reacts. To mark it, the ST must leave the
nomination form, open the Grimoire tab, tap the seat, tap "Add reminder", scroll
the `ReminderPicker` (`SeatSheet.kt:492-571`) to the Goblin row, tap "Claimed",
close the sheet, and go back to the Day tab — all while the table waits for a
vote. On execution the app just kills the player
(`DayScreen.kt:111-114` / `:350-357`); `StatusEffects.deathNotes`
(`StatusEffects.kt:52-129`) has no `goblin`/`Claimed` case, and the Day-screen
execute path does not call `deathNotes` at all. The ST must remember the rule,
notice the token, and end the game via the menu.

## Defects and gaps

1. **P0 · Executing a Claimed Goblin does not end the game.**
   Rules: "If the Goblin is executed, and they are marked CLAIMED, then declare
   that evil wins." App: `WinCheck.kt:18-101` has no Goblin case; the execute
   buttons call `viewModel.kill` directly. Repro: place "Claimed" on the Goblin,
   put them on the block, tap **Execute** → a death is recorded, the game
   continues, evil has actually won.

2. **P0 · The Day-screen execute path never runs `deathNotes`.**
   `DayScreen.kt:111-114` (block banner) and `DayScreen.kt:350-357` (per-nomination
   row) and `GameShell.kt:604-610` (dusk guard) all call `viewModel.kill(...)`
   with no consequence check. Only `SeatSheet.kt:238-300` consults
   `StatusEffects.deathNotes`. So *every* execution-triggered rule (Goblin,
   Saint, Boomdandy, Devil's Advocate protection, Minstrel) is silently skipped
   on the path the storyteller actually uses at dusk. Cross-cutting P0.

3. **P0 · No "executed but did not die" state.**
   The Goblin marked Claimed and protected by a Devil's Advocate is executed:
   the ST taps "Death prevented" (`SeatSheet.kt:296-299`) and **nothing is
   recorded**. Whether the Goblin wins in that case is arguably an ST call, but
   the app cannot even ask. Same root cause as `boomdandy.md` D2 /
   `fearmonger.md` D3.

4. **P1 · No claim capture at nomination time.**
   Rules require the claim to be public, at nomination, before voting. The
   `New nomination` card (`DayScreen.kt:126-255`) has nominator/nominee chips and
   a vote tally but no "claimed Goblin" toggle. Repro: any Goblin claim requires
   ~6 taps across two tabs mid-nomination.

5. **P1 · No way to record a *false* Goblin claim.**
   Rules: "If a non-Goblin player claims to be the Goblin when nominated, act as
   if they are the Goblin… pretend to move a reminder token." The
   `ReminderPicker` will happily let the ST put the real `("goblin","Claimed")`
   token on an Artist, which then looks identical to a real claim in the
   grimoire and would (once D1 is fixed) wrongly end the game. There is no
   "claimed, but not the Goblin" record and no announcement queue entry.

6. **P1 · No nomination warning for the Goblin.**
   `StatusEffects.nominationWarnings` (`StatusEffects.kt:132-166`) warns about
   Witch-cursed nominators, the Golem, the Virgin and the Fearmonger's mark, but
   says nothing when the **nominee is the Goblin** ("they may claim now") or when
   the nominee is already marked Claimed ("executing them ends the game").

7. **P2 · Impairment is not considered.**
   A drunk or poisoned Goblin's claim should not win. Nothing checks
   `StatusEffects.isImpaired` for the Goblin seat.

8. **P2 · No `night_guide`/day guide entry, so the rule is invisible in-app.**
   The ST's only in-app source is the ability string on the seat sheet
   (`SeatSheet.kt:196-198`) and the Reference tab. The claim-window rule ("today
   only, at nomination, before votes") appears nowhere.

9. **P2 · Missing Plague Doctor jinx** in `night_and_jinxes.json`.

10. **P3 · The `Claimed` token is invisible outside the seat.**
    The Day tab shows no per-player token state; the ST cannot see at a glance
    who claimed today while running votes.

## Proposed behaviour (spec)

The Goblin is a pure **day + execution** character; no night work.

### Day-time input the app must record

Add to the nomination flow (`DayScreen.kt`, the `New nomination` card, directly
under the nominee chips and **above** the vote tally, because the claim must
precede voting):

> `☐ Claims to be the GOBLIN` — visible whenever a nominee is selected.

Tapping it:
- places `PlacedReminder("goblin", "Claimed")` on the nominee (non-exclusive:
  more than one player may claim on the same day, per "Any player may claim");
- queues an `Announcement(day, "{name} has publicly claimed to be the Goblin.")`
  for immediate delivery (see the dawn/announcement queue proposed in
  `fearmonger.md`) and shows an inline `ANNOUNCE: "{name} claims to be the
  Goblin."` banner;
- shows, only to the ST, `Real Goblin ✓` or `Not the Goblin — pretend, and note
  it.` based on `nominee.characterId == "goblin"`.

Store the claim on the `Nomination` too, so the log is honest:

```kotlin
data class Nomination(… , val goblinClaim: Boolean = false)
```

### Execution consequence

Through the shared `GameActions.execute(state, playerId, nominatorId, died)`
funnel (see `boomdandy.md`):

- **trigger:** the executed seat has `characterId == "goblin"`, holds
  `("goblin","Claimed")` **placed today**, and the Goblin is not impaired
  (`StatusEffects.isImpaired`).
- **effect:** `WinCheck` advisory
  `Advisory(goodWins = false, reason = "{name} claimed to be the Goblin today and was executed — evil wins.")`,
  opening the reveal flow.
- **when `died == false`** (Devil's Advocate etc.): still raise the advisory but
  add the caution `"They were executed but did not die — the wiki does not
  settle this case; the Boomdandy's parallel ruling says the execution still
  counts."` so the ST decides.
- **when the Goblin is impaired:** produce a *caution*, not a win:
  `"{name} is the Goblin and claimed today, but is drunk/poisoned — by the
  general rules their ability does not work."`
- **when a non-Goblin marked Claimed is executed:** produce **nothing** (and, at
  execution time, a quiet ST-only note `"{name} claimed Goblin but is the
  {Character} — the game continues."`).

### Nomination warnings (`StatusEffects.nominationWarnings`)

Add:

```kotlin
if (nominee?.characterId == "goblin" && !StatusEffects.isImpaired(state, lookup, nominee)) {
    notes += "${nominee.name} IS the Goblin — if they claim Goblin now (before votes) and are executed today, evil wins."
}
if (nominee?.reminders?.any { it.sourceId == "goblin" && it.label.equals("Claimed", true) } == true) {
    notes += "${nominee.name} already claimed Goblin today — executing them ends the game if they really are the Goblin."
}
```

### Tokens and expiry

- `("goblin","Claimed")` stays in `EXPIRES_AT_DUSK` (`GameActions.kt:241`) —
  this is already correct and implements "today only".
- It must be placeable more than once per day (multiple claimants), so it must
  **not** go through `placeExclusiveReminder`. `characters.json:1792` declares a
  single "Claimed" reminder, which makes the night tray treat it as exclusive
  (`NightScreen.kt:318-326`); the Day-side toggle should call
  `GameActions.addReminder` directly and de-duplicate per seat.
- A visible Day-tab strip listing today's claimants: `Claimed Goblin today:
  Alice, Dan`.

### Interactions / jinxes

- **Cerenovus** (already in data, `night_and_jinxes.json:155`): a player made mad
  that they are the Goblin will claim; their claim must be recorded and must
  **not** win. The Day toggle already handles this because it checks
  `characterId`.
- **Plague Doctor**: add the jinx row.
- **Mastermind day**: if `mastermindDayActive` (`GameState.kt:111`) and a Claimed
  Goblin is executed, both rules point the same way (evil wins) — assert the
  Goblin advisory takes precedence and only one reveal is offered.
- **Traveller exile** is not an execution: no Goblin win.

### UI text

- Nomination card: `Claims to be the GOBLIN (must be said out loud before voting)`
- After ticking: `ANNOUNCE: "{name} has publicly claimed to be the Goblin."`
- Execute confirmation: `{name} claimed Goblin today. Executing them ends the game — EVIL WINS.`

### Data changes

- `night_and_jinxes.json`: add the Goblin/Plague Doctor jinx.
- `night_guide.json`: add a `goblin` entry under a new `day` key (or ship the
  prose in the Day-tab strings) covering: claim window, today-only, false
  claims, and the executed-but-survived question.

## Tests to add

1. *Given* a Goblin marked `("goblin","Claimed")` today, *when*
   `GameActions.execute(goblinSeat, died = true)`, *then* `WinCheck.check`
   returns `goodWins == false` with a reason naming the Goblin.
2. *Given* the same Goblin **without** the Claimed token, *then* no Goblin
   advisory is produced.
3. *Given* a Goblin claimed on day 1, *when* the game advances DAY→NIGHT
   (`advancePhase`), *then* the Claimed token is gone; *and* executing the Goblin
   on day 2 produces no advisory. (Locks in the `EXPIRES_AT_DUSK` behaviour.)
4. *Given* an Artist marked Claimed is executed, *then* no advisory is produced.
5. *Given* a poisoned Goblin marked Claimed is executed, *then* the result is a
   caution, not a `goodWins = false` advisory.
6. *Given* a Goblin marked Claimed is executed but `died = false`, *then* an
   advisory with the "executed but survived" caution is produced.
7. *Given* a Goblin is the nominee, *then* `nominationWarnings` mentions the
   Goblin claim opportunity; *given* the nominee is a Claimed non-Goblin, *then*
   it does not promise a win.
8. *Given* two players claim Goblin on the same day, *then* both hold a Claimed
   token simultaneously (no exclusivity).
