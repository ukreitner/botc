# Investigator (investigator) — Trouble Brewing Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Investigator>

Current ability text (matches `characters.json`):

> "You start knowing that 1 of 2 players is a particular Minion."

How to run (wiki, verbatim):

> "During setup, place the Investigator's **MINION** token by a Minion character and the **WRONG** token by another character. On the first night, wake the Investigator, point to both marked players, show the character token marked **MINION**, then put them to sleep. Remove reminder tokens when convenient."

Note the timing: the pair is chosen **during setup**, before the first night begins, and the two tokens live in the grimoire so the storyteller can recover the ruling later.

Examples (wiki):

- *"Amy (Baron) and Julian (Mayor): Investigator learns one is the Baron"*
- *"Angelus (Spy) and Lewis (Poisoner): Investigator learns one is the Spy"*
- *"Brianna (Recluse) and Marianna (Imp): Investigator learns one is the Poisoner, because the Recluse is registering as a Minion"* — **the Recluse can be the "particular Minion", registering as a Minion that is not even in play.**

Clarifications:

- *"Even though one of the players you detect is a Minion, that does not necessarily mean that the other player is good."*
- The **Recluse** may register as evil *and as a Minion or Demon* (<https://wiki.bloodontheclocktower.com/Recluse>), and *"A Recluse that registers as a particular Minion or Demon does not have this character's ability."* The registration is a per-instance storyteller choice.
- The **Spy** registers as good / a Townsfolk or Outsider, so a Spy is a perfectly ordinary Minion for the Investigator — but the storyteller *may* also choose to hide the Spy by not using them.
- The wiki does **not** state whether the Investigator can be one of the two players pointed at. Flag as uncertain; the safe default is to sort the Investigator's own seat last with a hint rather than to forbid it.
- Unlike the Librarian, the Investigator has **no "or that zero are in play"** clause. In Trouble Brewing there is always at least one Minion, so this only matters on other scripts or with Fabled/setup oddities.
- A drunk or poisoned Investigator is given false information — `night_guide.json` already says *"you may show a Minion not in play or point at two wrong players"*.

Jinxes: none for the Investigator. Correct — none in the official list, none in the app data.

## What the app does today

Data:

- `characters.json` `investigator` — ability current; `firstNightReminder` = *"Show the character token of a Minion in play. Point to two players, one of which is that character."*; `otherNightReminder` empty; `reminders: ["Minion", "Wrong"]`. Matches the official tokens. **Works.**
- `night_and_jinxes.json` — `firstNight` index 48 (Washerwoman 46, Librarian 47, Investigator 48, Chef 49); absent from `otherNight`. Matches the official order. **Works.**
- `night_guide.json` `investigator.first` — accurate prose; one `shows` entry: `{label: "Show Minion token", kind: "token", token: "pick", text: "One of the 2 players I point to is this character"}`, which opens `GuideShowDialog` (`NightScreen.kt:366-454`) with an any-character picker, in-play characters sorted first, and editable text. **Works** as a raw display tool.

Engine — `InfoCalc.kt:70` dispatches to the shared helper at `InfoCalc.kt:408-421`:

```kotlin
private fun startKnowing(ctx: Ctx, team: Team, label: String): InfoResult {
    val inPlay = ctx.players.filter { ctx.character(it)?.team == team }
    if (inPlay.isEmpty()) return InfoResult("No $label in play" + …, caveats = misregistrations(ctx, ctx.players))
    return InfoResult(
        headline = "$label in play: " + inPlay.joinToString { "${ctx.name(it)} (${ctx.character(it)?.name})" },
        detail = "Show one of those character tokens, point to that player plus 1 wrong player.",
        caveats = misregistrations(ctx, ctx.players),
    )
}
```

Shared with the Librarian (`InfoCalc.kt:69`) and the Washerwoman (`InfoCalc.kt:68`).

UI:

- `NightScreen.kt:836-863` — no target picker (`targetsNeeded("investigator") == 0`). The panel shows the guide prose, the "Show Minion token" chip, the headline listing every Minion seat, the detail line, and the caveats.
- Token placement is the generic `NightToolTray` (`NightScreen.kt:193-357`): tap **Minion**, tap a seat; tap **Wrong**, tap a seat. Both are single-copy labels so `placeExclusiveReminder` is used (`NightScreen.kt:319-324`) and they move rather than accumulate. **Works.**
- `NightScreen.kt:886-901` — the headline is neither numeric nor `YES`/`NO`-prefixed, so **no** full-screen chip is offered from the calculator.
- `NightScreen.kt:903-930` — the impaired "False info to show instead" block therefore renders an **empty** row: `leadingNumber` is null and `isYes`/`isNo` are false, so both branches produce nothing.

Storyteller's actual experience on night 1: open the Investigator row, read *"Minion in play: Cara (Poisoner), Dee (Baron)"*, decide on the spot which Minion and which decoy, tap **Minion** in the tray, tap Cara, tap **Wrong**, tap Fred, then tap **» Show Minion token**, find "Poisoner" in the picker, and show the card. Six or seven taps of live decision-making, none of it prepared during setup, and the two tokens then sit on the grimoire for the rest of the game.

## Defects and gaps

1. **P1 · The pairing is not offered during setup, where the rules put it.**
   Rules: *"During setup, place the Investigator's MINION token by a Minion character and the WRONG token by another character."* App: there is no setup-time prompt (compare the Drunk / Lunatic / Marionette / red-herring prompts at `GameShell.kt:347-479`), and `validateSetupState` (`GameActions.kt:503-561`) does not check for the tokens. All the decision-making lands at night 1 with the players sitting in silence.

2. **P1 · Misregistration is a warning, never a choice the app can make for you.**
   Rules (wiki example): the Recluse may be shown as *any* Minion, in play or not. App: `InfoCalc.kt:409` filters strictly on `ctx.character(it)?.team == team`, so a Recluse **never** appears in the Investigator's candidate list. The only signal is a generic caveat from `misregistrations` (`InfoCalc.kt:121-130`). The storyteller must know the Recluse rule from memory and then improvise both the character token and the pair.

3. **P1 · The caveat list is unscoped and therefore mostly noise.**
   `misregistrations(ctx, ctx.players)` (`InfoCalc.kt:412,419`) emits a line for **every** Spy and Recluse in the game, whether or not it can affect this ability. For the Investigator the Spy caveat is doubly wrong: the Spy *is* a Minion and registering it as good only ever removes an option, while the printed text (*"may register as good / a Townsfolk or Outsider"*) reads like an invitation.

4. **P1 · A drunk or poisoned Investigator gets no false-info help at all.**
   `NightScreen.kt:903-930` only knows how to falsify numbers and YES/NO. For `startKnowing` characters the impaired block renders an empty `FlowRow`. The guide prose says *"you may show a Minion not in play or point at two wrong players"* — the app knows exactly which Minions are **not** in play and which players are **not** Minions, and offers neither.

5. **P1 · Nothing records what was shown.**
   The `Minion` / `Wrong` tokens are the only trace, they are ambiguous about which character token was displayed, and the official instruction is to remove them "when convenient". After that the game has no memory of the Investigator's information at all — not in the log (`GameExtras.kt:40-64`), not in the reveal flow.

6. **P2 · The headline dumps every Minion instead of proposing a pair.**
   `InfoCalc.kt:416-420` produces *"Minion in play: Cara (Poisoner), Dee (Baron)"*. What the storyteller needs is a one-tap **"Show Poisoner · point at Cara and Fred"** action that places both tokens and prepares the show card in a single gesture.

7. **P2 · The decoy is completely unassisted.**
   Nothing suggests a "Wrong" player, nothing excludes the Investigator's own seat, and nothing warns when the chosen decoy is itself a Minion (legal, but it makes the information strictly weaker and is usually a mistake).

8. **P2 · The `Minion` and `Wrong` tokens never expire and clutter the grimoire for the whole game.**
   They are in neither `EXPIRES_AT_DAWN` nor `EXPIRES_AT_DUSK` (`GameActions.kt:218-242`). The official text says to remove them when convenient; the app offers no "clear night-1 info tokens" action and no visual distinction between a live token and a spent one. On a phone grimoire this is real screen cost from night 2 onward.

9. **P2 · The "no Minion in play" branch has no affordance and no rules basis.**
   `InfoCalc.kt:410-415` returns *"No Minion in play"* for the Investigator with the Librarian's "0 signal" suffix suppressed — correct, because the Investigator has no zero clause — but then leaves the storyteller with a dead end and no suggested fallback. Reachable on non-TB scripts and via Fabled.

10. **P3 · The Investigator's own seat is offered as a decoy.**
    The tray's seat list is unfiltered `state.players` (`NightScreen.kt:315`). Whether the Investigator may be one of the two players is not settled by the wiki (see Sources), so this should be a sort/hint, not a block.

11. **P3 · No path for a mid-game Investigator.**
    "You start knowing" characters created after night 1 (Pit-Hag, Amnesiac, storyteller character change from `SeatSheet.kt:310`) get no night row, because `investigator` is only in the `firstNight` list. Out of TB scope; the generic engine needs an answer, and the brief's Professor case is the same shape.

## Proposed behaviour (spec)

### Setup step (new)

- **when:** during `Phase.SETUP`, once a seat holds `investigator` and every seat has a character.
- Prompt, in the same family as the red-herring / Drunk prompts at `GameShell.kt:347-479`:
  **"Investigator information"** — *"Pick the Minion character the Investigator learns, and the two players you will point at. One of them must be that character."*
- **Character picker:** default list = Minions actually in play. Plus a clearly separated **"Misregistration"** section listing every `recluse` seat with the label *"Cara (Recluse) may register as any Minion — pick which"*, which then opens the full Minion list (including Minions **not** in play, per the wiki's Recluse example).
- **Player pickers:** the "true" seat is fixed by the character choice; the decoy is a second pick over all other seats, sorted: alive good non-Minion seats first, then the rest. The Investigator's own seat is sorted last with the hint *"pointing at the Investigator themselves is unusual"*. Warn (do not block) when the decoy is also a Minion.
- **Effects:** `placeExclusiveReminder("investigator", "Minion")` on the true seat, `placeExclusiveReminder("investigator", "Wrong")` on the decoy, and a `NightRecord`-style pre-record of the intended character.
- **Validation:** add to `validateSetupState` a soft issue *"Investigator: choose the Minion and the two players to point at"* when the tokens are missing. Soft, because the storyteller may legitimately want to decide at the table.

### Night action

- **when:** first night only (order position 48, unchanged). Wake condition: the Investigator seat exists and is alive.
- **targets:** none chosen by the player; the pair was chosen at setup and is simply replayed.
- **information (structured):**

  ```
  StartKnowingInfo(
    candidates: List<Candidate>,    // (playerId, characterId, kind)
    zeroAllowed: Boolean,           // Investigator: false; Librarian: true
    impairment: Impairment,
  )
  Candidate.kind = TRUE | MISREGISTERED | FALSE
  ```

  - `TRUE` candidates: seats whose real character is a Minion.
  - `MISREGISTERED` candidates: every `recluse` seat, paired with each Minion on the script (in play or not) — reason *"the Recluse may register as this Minion"*. Also, for completeness, a `spy` seat is a `TRUE` candidate (it is a Minion) with a note that the storyteller may instead choose to hide it.
  - `FALSE` candidates (only surfaced when `impairment != NONE`): every Minion on the script that is **not** in play, plus any pair of non-Minion players.
  - `zeroAllowed = false` for the Investigator (no zero clause) — the UI must therefore not offer a "show 0" affordance here.
- **what the step shows:** a **"Replay the setup pairing"** primary action: *"Show **Poisoner** · point at **Cara** and **Fred**"* with a single button that opens the prepared `ShowCard.CharacterCard("One of the 2 players I point to is this character", "poisoner")`. Underneath, an **"Change the pairing"** link back into the setup picker.
- **impaired/false alternative:** when `impairment != NONE`, promote a **"False info — show instead"** section listing (a) Minions not in play, one tap each to prepare the card, and (b) a suggested pair of two players who are neither the Minion nor each other's obvious partners. When `MUST_BE_FALSE` (Vortox), demote the true pairing.
- **visibility:** nothing to evil. The Spy sees the `Minion`/`Wrong` tokens in the grimoire, which is correct and part of the game.
- **expiry:** `investigator/Minion` and `investigator/Wrong` become **dimmed "spent"** tokens at the first dawn (rendered smaller/greyed rather than removed), with a one-tap **"Clear night-1 info tokens"** action in the grimoire overflow that removes every `washerwoman|librarian|investigator` × `Townsfolk|Outsider|Minion|Wrong` token at once. Do not auto-remove: the tokens are the storyteller's record.
- **log:** `NightRecord(cycle = 1, stepId = "investigator", holderIds, targetIds = [trueSeat, decoySeat], outcome = "Shown: Poisoner", impaired)` plus the misregistration ruling, so the Recluse's registration stays consistent with later Chef/Empath/Fortune Teller/Undertaker rulings.
- **day-time inputs:** none required. Optional: record the Investigator's public claim for the reveal flow.

### UI text

- Setup prompt title: **"Investigator information"**; body **"Which Minion do they learn, and who are the two players?"**
- Night step, prepared: **"Show the Poisoner token, point at Cara and Fred."**
- Night step, nothing prepared: **"No pairing chosen yet — pick the Minion and the two players."**
- Recluse option label: **"Cara (Recluse) registering as the Poisoner"**.
- Impaired: **"⚠ <Investigator> is POISONED — show false info. Minions not in play: Scarlet Woman, Spy."**

### Data changes

- `characters.json` — none; text and reminders are current.
- `night_guide.json` `investigator.first.instructions` — move the "during setup" placement into a setup-phase string so the app can surface it at the right moment; keep the night text to *"Point at the two marked players and show the marked character token."*

## Tests to add

1. `investigator lists only real minions as true candidates`
   Given `poisoner` at 1 and `baron` at 6. Then `candidates.filter { it.kind == TRUE }` is exactly those two seats.

2. `investigator offers the recluse as any minion`
   Given a `recluse` at seat 5 on a script whose Minions are Poisoner, Spy, Baron, Scarlet Woman.
   Then `candidates` contains a `MISREGISTERED` entry for seat 5 for **each** of those four Minions, including ones not in play. (Today the Recluse never appears at all.)

3. `investigator does not offer a zero`
   Given the Investigator. Then `zeroAllowed == false`, and given a board with no Minion the result does not suggest a "0" signal.

4. `investigator false info lists minions not in play`
   Given a poisoned Investigator with `poisoner` and `baron` in play.
   Then the false-info candidates include `spy` and `scarletwoman` and exclude `poisoner` and `baron`.

5. `investigator caveats are scoped to this ability`
   Given a `spy` in play. Then no caveat tells the storyteller the Spy "may register as good" for the Investigator (it is already a Minion); the Recluse caveat is present and actionable.

6. `investigator setup places both tokens exclusively`
   When the setup pairing chooses seat 1 as the Minion and seat 4 as the decoy, twice with different seats.
   Then exactly one seat carries `investigator/Minion` and exactly one carries `investigator/Wrong`.

7. `night-1 info tokens are cleared by the clear action and not by dawn`
   Given `investigator/Minion` and `investigator/Wrong` placed. When `advancePhase` runs NIGHT→DAY.
   Then both tokens remain. When the clear action runs, both are gone.

8. `investigator information is written to the night log`
   Given the storyteller shows the Poisoner token pointing at seats 1 and 4.
   Then `state.nightLog` contains `NightRecord(cycle = 1, stepId = "investigator", targetIds = [1, 4], outcome = "Shown: Poisoner")`.

9. `investigator step is first night only`
   Given an Investigator on night 2. Then the night sheet contains no Investigator step.

10. `recluse ruling is echoed to later steps`
    Given the Investigator was shown the Recluse as the Baron on night 1.
    Then the Chef / Empath / Fortune Teller steps that involve that Recluse seat surface *"you already ruled this Recluse registers as the Baron"*.
