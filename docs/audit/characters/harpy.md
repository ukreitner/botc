# Harpy (harpy) — Experimental Minion

## Official rules (sources)

Source: https://wiki.bloodontheclocktower.com/Harpy (Character Text, Summary,
How to Run, Tips & Tricks), fetched 2026-08-25.

**Current ability text (quote):**
> "Each night, choose 2 players: tomorrow, the 1st player is mad that the 2nd is evil, or one or both might die."

`characters.json:1806` matches verbatim. No drift.

**How to Run (quote):**
> "Each night, wake the Harpy. The Harpy points to one player, then another player. Mark the first player with the **MAD** reminder and the second player with the **2ND** reminder. Put the Harpy to sleep. Wake the player marked **MAD**. Show the **THIS CHARACTER SELECTED YOU** info token then the Harpy token, then point to the player marked **2ND**. Put the player marked **MAD** to sleep."
> "Tomorrow, if the player marked **MAD** is not mad that the player marked **2ND** is evil, you may kill one or both players."

**Summary / clarifications (quotes):**
- "At night, the Harpy player chooses one player at a time, not two at once."
- **"A player chosen by the Harpy is affected by the ability until the next Harpy choice."**
- "If the Storyteller decides to kill players with the Harpy ability, they do not need to kill both."
- **"The Harpy can choose a dead player."**
- "The order of deaths due to the Harpy ability can be chosen by the Storyteller, should that be important."

Notes:
- The madness the MAD player must perform is *"the 2ND player is evil"* — the
  MAD player knows the Harpy chose them and knows who they must accuse.
- The kill window is **"tomorrow"**, i.e. during the following day, at the ST's
  discretion, and either/both/neither may die. Storytellers often defer the
  death to that night so it lands with the Demon kill; the wiki's wording is
  "tomorrow", so the app should default to a day-time prompt but allow deferral.
- The Harpy may choose themselves or the Demon (the Tips & Tricks explicitly
  suggest choosing yourself, and choosing minions/demons, as cover).
- The page as fetched lists **no jinxes** for the Harpy.

**Night order.** First night index 39 (after `fearmonger`, before `mezepheles`)
— `night_and_jinxes.json:334`. Other nights index 27 (after `fearmonger`, before
`mezepheles`) — `night_and_jinxes.json:400`. Both correct.

## What the app does today

- `characters.json:1806` — correct ability; `reminders: ["Mad", "2nd"]`;
  identical, accurate first/other night reminders describing the two-stage wake.
- `night_guide.json:1358` — good first/other prose. One show card,
  "THIS CHARACTER SELECTED YOU" with the Harpy's own token, for the 1st target.
  The `other` prose says *"If yesterday's madness was broken, one or both of
  yesterday's targets might die **tonight**."* — a drift from the wiki's
  "tomorrow" (see D3).
- `night_and_jinxes.json:334,400` — correct night-order slots. No jinx rows
  (correct: the wiki lists none).
- `GameActions.kt:239-240` — `"harpy" to "Mad"` and `"harpy" to "2nd"` are both
  in `EXPIRES_AT_DUSK`, so `advancePhase` DAY→NIGHT sweeps them
  (`GameActions.kt:261`).
- `StatusEffects.kt:162-164` — nomination warning:
  ```kotlin
  if (nominator?.reminders?.any { it.label.equals("Mad", true) } == true) {
      notes += "${nominator.name} is Cerenovus-mad — check their claim before this goes further."
  }
  ```
  This matches on the **label only**, so a Harpy "Mad" token triggers a message
  that names the *Cerenovus*.
- Token placement is via the generic night tray (`NightScreen.kt:193-357`);
  because each label has exactly one copy, both use `placeExclusiveReminder`
  (`GameActions.kt:194-203`) so they move rather than accumulate. **Works.**

**Storyteller experience today:** the night step tells you to wake the Harpy,
place Mad then 2nd, then wake the 1st target and point at the 2nd. All correct.
From there the app forgets: at dawn nothing tells you who is mad about whom;
during the day nothing reminds you what madness must be performed or offers the
"break the madness → kill" decision; at dusk both tokens are deleted, so on the
**next** night — precisely when the guide tells you to consider killing
yesterday's targets — the grimoire no longer records who they were.

## Defects and gaps

1. **P1 · Mad/2nd tokens are destroyed at dusk, before the ST needs them.**
   Rules: "A player chosen by the Harpy is affected by the ability until the
   next Harpy choice." App: `GameActions.kt:239-240` puts both in
   `EXPIRES_AT_DUSK`, cleared at DAY→NIGHT (`GameActions.kt:261`). Repro: night
   2 place Mad on Alice / 2nd on Bob; day 2 Alice refuses to comply; tap Dusk;
   open night 3 — the Harpy step's own guide says "one or both of yesterday's
   targets might die tonight" and the grimoire shows no Mad or 2nd token to tell
   you who those were. The tokens must survive until the Harpy's next placement
   (which `placeExclusiveReminder` already handles by moving them).

2. **P1 · Nothing surfaces the madness at day start or at nomination.**
   The ST must remember, unaided, that "Alice must be mad that Bob is evil
   today". There is no day-start briefing anywhere in the app (confirmed: no
   briefing surface outside `NotesScreen`), and `nominationWarnings`
   (`StatusEffects.kt:132-166`) has no Harpy-specific text.

3. **P1 · No "madness broken → kill 0/1/2" decision tool.**
   Rules: "you may kill one or both players", ST's choice, and the ST may also
   choose the order of deaths. Nothing in the UI offers this; the ST must open
   two seat sheets and use "Other death". No `QuickResolutions` case exists
   (`NightScreen.kt:462-528` handles only snakecharmer/fanggu/professor).

4. **P1 · The Cerenovus warning misfires on Harpy madness.**
   `StatusEffects.kt:162-164` matches the bare label "Mad" and hard-codes
   "Cerenovus-mad". Repro: Harpy marks Alice Mad; Alice nominates → the ST is
   told Alice is *Cerenovus*-mad. Wrong source, wrong madness content, and the
   generic `"Mad"` token from the `ReminderPicker` generic list
   (`SeatSheet.kt:502`) does the same. It also never fires for the *nominee*,
   and it never says **what** the player must be mad about.

5. **P2 · `night_guide.json:1358` drifts from the rules on kill timing.**
   The wiki says the kill happens **tomorrow** (during the day); the guide says
   "might die tonight". Both are playable, but the guide should state the rule
   and then offer the deferral as an ST option rather than presenting the
   variant as the rule.

6. **P2 · Second-target wake has no dedicated show card.**
   The How to Run requires: SELECTED YOU token → Harpy token → **point at the
   2nd player**. `night_guide.json:1358` supplies only the first card; there is
   no "point at this player" full-screen card, so on a phone the ST must point
   physically in a dark room. `ShowCards.kt` has `CharacterCard` /
   `Message` / `NumberCard` / `BluffsCard` / `SheetCard` / `AlignmentCard` but no
   "this seat" card.

7. **P2 · The picker does not enforce/hint the ordering.**
   The two targets are ordered (1st = mad, 2nd = accused) but the tray is a flat
   list of reminder chips; nothing stops the ST placing "2nd" first or placing
   both on the same seat (the rules do not forbid self-selection but 1st == 2nd
   would be nonsense).

8. **P2 · Dead targets.** "The Harpy can choose a dead player." The tray already
   lists dead seats (`NightScreen.kt:330-350`), so this **works**, but the
   guide should say so — a dead MAD player still has to perform madness (or die
   again, i.e. nothing happens), which is a known ST trap.

9. **P3 · No record of whether madness was satisfied.** The ST has nowhere to
   tick "Alice complied" so the next night's decision has no history.

## Proposed behaviour (spec)

### Night step

- **when:** first **and** other nights.
- **wake condition:** the Harpy seat is alive. (Dead → grey the step: `Dead — no
  Harpy ability; leave the Mad/2nd tokens where they are.`)
- **targets:** exactly 2, **ordered**. Constraints: any player, alive or dead;
  self allowed; the two must be different. Picker: a two-slot control —
  `1st (goes mad): [chip row]` then `2nd (is "evil"): [chip row]`, the second
  row disabling the seat chosen as 1st. Default/pre-selection: last night's pair
  (so "same again", which the Tips & Tricks recommend, is two taps).
- **immediate effects:**
  - `placeExclusiveReminder(t1, PlacedReminder("harpy", "Mad"))`
  - `placeExclusiveReminder(t2, PlacedReminder("harpy", "2nd"))`
  - Record the pair on state (see below) so the next night can reason about it.
  - If the Harpy is impaired (`StatusEffects.isImpaired`), still place the
    tokens but caveat: `Harpy is drunk/poisoned — the madness is not enforced
    and nobody may be killed for breaking it.`
- **show sequence (in order, as chips on the step):**
  1. `» To 1st target — THIS CHARACTER SELECTED YOU` + Harpy token (exists).
  2. **new** `» Point at the 2nd` — a full-screen **seat card** showing the 2nd
     target's name (and seat position), so the ST can show rather than point.
- **deferred effects:** the madness applies **tomorrow** (the day following this
  night). Queue a day-start briefing item (see `fearmonger.md`'s announcement
  queue, but ST-private, not announced):
  `{t1} must act mad that {t2} is evil today, or you may kill one or both.`
- **expiry:** **remove** `"harpy" to "Mad"` and `"harpy" to "2nd"` from
  `EXPIRES_AT_DUSK` (`GameActions.kt:239-240`). The tokens persist until the
  Harpy's next choice moves them (already handled by `placeExclusiveReminder`),
  matching "affected by the ability until the next Harpy choice". If the Harpy
  dies, leave the tokens but mark them inert in the day briefing.
- **information:** none computed; the 1st target learns the Harpy chose them and
  who the 2nd is.
- **visibility:** only the 1st target learns anything. The 2nd learns nothing.

### New engine state (small, enables the day tools)

```kotlin
/** Harpy: (night, 1st, 2nd, satisfied?) — the last entry drives day briefings. */
data class HarpyChoice(val night: Int, val madId: Long, val secondId: Long, val satisfied: Boolean? = null)
val harpyChoices: List<HarpyChoice> = emptyList()
```

`satisfied` is set by a day-tab toggle; it is nullable so "not yet decided" is
distinguishable.

### Day behaviour

Add a Day-tab card (and a day-start briefing line) whenever a current
`HarpyChoice` exists for the previous night:

> **Harpy madness — {t1} must act mad that {t2} is evil**
> `[ Madness satisfied ]  [ Madness BROKEN ]`

Choosing **BROKEN** opens the kill picker:

> `You may kill one or both. Order is yours.`
> `[ {t1} dies ] [ {t2} dies ] [ both die ] [ nobody dies ]`
> plus `[ Defer to tonight ]`, which keeps the decision open and re-raises it on
> the next night's Harpy step.

Deaths use `DeathCause.STORYTELLER`, and each must first run
`StatusEffects.deathNotes` (protection checks) exactly like `SeatSheet.kt:238-300`.

### Nomination-time behaviour

Rewrite `StatusEffects.kt:162-164` to be source-aware and to cover nominee as
well as nominator:

```kotlin
for (p in listOfNotNull(nominator, nominee)) {
    for (r in p.reminders.filter { it.label.equals("Mad", true) }) {
        val what = when (r.sourceId) {
            "cerenovus" -> "mad about the character the Cerenovus chose"
            "harpy" -> {
                val second = state.players.find { s -> s.reminders.any { it.sourceId == "harpy" && it.label.equals("2nd", true) } }
                "mad that ${second?.name ?: "the 2nd player"} is evil (Harpy)"
            }
            "pixie" -> "mad that they are the character the Pixie saw"
            else -> "mad"
        }
        notes += "${p.name} must act $what — if they are not, you may kill them."
    }
}
```

### UI text for the step

- First/other night row: `Harpy — {Harpy} points at the 1st (goes mad), then the 2nd (the accused).`
- After both tokens placed: `Wake {t1}: SELECTED YOU + Harpy token, then point at {t2}.`
- Day briefing: `{t1} is Harpy-mad that {t2} is evil. If they break it, you may kill one, both, or neither.`

### Data changes

- `GameActions.kt:239-240` — delete both Harpy rows from `EXPIRES_AT_DUSK`.
- `night_guide.json:1358` — change the `other` instructions to state the rule
  ("Tomorrow, if the MAD player is not mad that the 2ND is evil, you may kill
  one or both") and note the common ST option to delay the death to that night;
  add the "Point at the 2nd" show card and mention that dead players may be
  chosen and that the tokens stay until the Harpy re-chooses.
- `characters.json:1806` — no change needed; `["Mad","2nd"]` is right. Consider
  renaming the second label to `"2nd"`→`"2nd (is evil)"`? No — keep parity with
  the physical token.

## Tests to add

1. *Given* Mad on Alice and 2nd on Bob at night 2, *when* `advancePhase` runs
   DAY→NIGHT, *then* **both tokens are still present** (regression test for the
   expiry removal).
2. *Given* Mad on Alice / 2nd on Bob, *when* the Harpy chooses Carol then Dan on
   night 3, *then* exactly one Mad token exists (on Carol) and exactly one 2nd
   token exists (on Dan).
3. *Given* Mad on Alice sourced from `"harpy"`, *when*
   `nominationWarnings(nominator = Alice, …)`, *then* the note mentions the Harpy
   and names Bob, and does **not** say "Cerenovus".
4. *Given* Mad on Alice sourced from `"cerenovus"`, *then* the note still names
   the Cerenovus (no regression).
5. *Given* Mad on the nominee (not the nominator), *then* a warning is still
   produced.
6. *Given* the Harpy is poisoned on night 2, *then* the night step's caveats
   include that the madness cannot be punished.
7. *Given* a `HarpyChoice(night = 2, madId = Alice, secondId = Bob)` and the ST
   marks it broken and kills both, *then* two `DeathRecord`s with
   `cause == STORYTELLER` exist and `satisfied == false` is recorded.
8. *Given* the Harpy chooses a dead player as 1st, *then* the tokens place
   without error and the day briefing still names them.
9. *Given* the Harpy dies at night 3, *then* the night sheet greys the Harpy step
   and the day briefing marks the standing madness as no longer enforceable.
