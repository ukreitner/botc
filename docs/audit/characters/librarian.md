# Librarian (librarian) — Trouble Brewing Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Librarian>

Current ability text (matches `characters.json`):

> "You start knowing that 1 of 2 players is a particular Outsider. (Or that zero are in play.)"

How to run (wiki, verbatim):

> "While preparing the first night, put the Librarian's **OUTSIDER** reminder token by any Outsider character token, and put the Librarian's **WRONG** reminder token by any other character token."
>
> "During the first night, wake the Librarian and point to the players marked **OUTSIDER** and **WRONG**. Show the character token marked **OUTSIDER** to the Librarian."

Examples and clarifications (wiki):

- **Zero case:** *"When there are no Outsiders: The Librarian learns a '0'."* And from Tips: *"If receiving a zero: you know for sure there are no Outsiders in the game."*
- **The Drunk counts as an Outsider:** *"The Librarian learns the true character. The Drunk is Abdallah's true character, not the Monk."* So a seat whose `characterId` is `drunk` is a legal Outsider to show, and the token shown is **Drunk**, not the Townsfolk they believe they are. (Confirmed from the other side on the Washerwoman page: *"you know that the person you see is not the Drunk"* — the Drunk never registers as a Townsfolk.)
- **Self-knowledge:** *"If you see two players as the Drunk, remember that you might be the real drunk!"*
- The wiki does not state whether the Librarian may be one of the two players pointed at. Flag as uncertain; the Librarian is a Townsfolk so they can never be the *Outsider* half, only the decoy.
- **Recluse:** may register as evil *and as a Minion or Demon* (<https://wiki.bloodontheclocktower.com/Recluse>), per instance, at the storyteller's choice. That means a game whose only Outsider is a Recluse can legitimately give the Librarian a **0** — the Librarian page does not spell this out but the Recluse's registration rule reaches every ability that detects character type.
- **Spy:** *"If any character has an ability that detects Townsfolk or Outsiders, then the Spy might register as a specific Townsfolk or Outsider to that player"* (<https://wiki.bloodontheclocktower.com/Spy>) — so the Spy can be shown to the Librarian as, say, the Butler, and *"A Spy that registers as a particular Townsfolk or Outsider does not have this character's ability."*
- A drunk or poisoned Librarian is given false information (a wrong pair, a wrong Outsider token, or a false 0).

Jinxes: none for the Librarian. Correct — none in the official list, none in the app data.

## What the app does today

Data:

- `characters.json` `librarian` — ability current, including the "(Or that zero are in play.)" clause; `firstNightReminder` = *"Show the character token of an Outsider in play. Point to two players, one of which is that character."*; `otherNightReminder` empty; `reminders: ["Outsider", "Wrong"]`. Matches the official tokens. **Works.**
- `night_and_jinxes.json` — `firstNight` index 47 (Washerwoman 46, Librarian 47, Investigator 48); absent from `otherNight`. Matches the official order. **Works.**
- `night_guide.json` `librarian.first` — accurate prose, including the 0 case and the drunk/poisoned case; one `shows` entry `{label: "Show Outsider token", kind: "token", token: "pick", …}` which opens the editable any-character picker at `NightScreen.kt:366-454`. **Works** as a display tool.

Engine — `InfoCalc.kt:69` dispatches to the shared `startKnowing` helper (`InfoCalc.kt:408-421`), which is also used by the Washerwoman (`:68`) and Investigator (`:70`):

- Non-empty case: headline *"Outsider in play: Fred (Butler), Gil (Drunk)"*, detail *"Show one of those character tokens, point to that player plus 1 wrong player."*, caveats `misregistrations(ctx, ctx.players)`.
- Empty case (`InfoCalc.kt:410-415`): headline *"No Outsider in play — show the 0 signal"*, same unscoped caveats.
- The team test is `ctx.character(it)?.team == Team.OUTSIDER` on the **real** `characterId`, so a seat holding `drunk` is correctly listed as an Outsider named *Drunk*, and a Drunk-shown-as-Monk is never offered as a Townsfolk elsewhere. **Works — this matches the wiki example exactly.**

UI:

- `NightScreen.kt:836-863` — no target picker (`targetsNeeded("librarian") == 0`); the panel shows guide prose, the "Show Outsider token" chip, the headline, the detail, and the caveats.
- Token placement is the generic `NightToolTray` (`NightScreen.kt:193-357`): tap **Outsider**, tap a seat; tap **Wrong**, tap a seat. Single-copy labels so `placeExclusiveReminder` is used. **Works.**
- `NightScreen.kt:886-901` — no full-screen chip is offered: `leadingNumber` is null, and the zero-case headline *"No Outsider in play…"* does **not** satisfy `headline.startsWith("NO")` because that test is case-sensitive (`NightScreen.kt:888`).
- `NightScreen.kt:903-930` — the impaired "False info to show instead" block renders an empty row for the same reasons.

Storyteller's actual experience on night 1: open the Librarian row, read *"Outsider in play: Fred (Butler), Gil (Drunk)"*, decide the Outsider and the decoy at the table, place two tokens from the tray, then tap **» Show Outsider token**, search "Butler", and show the card. In a no-Outsider game they read *"show the 0 signal"* and then have to leave the step, open **All tokens** (`NightScreen.kt:280` → `ShowToolSheet`, `ShowCards.kt:384`) and find the 0 chip.

## Defects and gaps

1. **P1 · The Librarian's "0" answer has no one-tap card, in the one place it is guaranteed to be needed.**
   Rules: the Librarian learns *"a 0"*. App: the zero headline is *"No Outsider in play — show the 0 signal"*; `leadingNumber` is null (`NightScreen.kt:886`) and `isNo` is false because `startsWith("NO")` is case-sensitive against `"No Outsider…"` (`NightScreen.kt:888`). So the calculator's own answer cannot be shown from the calculator. The storyteller must navigate to a different sheet mid-wake.
   Repro: 7-player TB with zero Outsiders in the bag → night 1 → Librarian step → no "Show 0" chip.

2. **P1 · The pairing is not offered during setup, where the rules put it.**
   Rules: *"While preparing the first night, put the Librarian's OUTSIDER reminder token by any Outsider…"* App: no setup prompt exists (compare the red-herring / Drunk / Lunatic / Marionette prompts at `GameShell.kt:347-479`), and `validateSetupState` (`GameActions.kt:503-561`) does not check for these tokens. Every decision lands live at night 1.

3. **P1 · Misregistration is a warning, never an option the app can build.**
   Rules: the Spy may be shown as a specific Outsider; a Recluse registering as a Minion can turn the answer into a legitimate **0**. App: `InfoCalc.kt:409` filters strictly on the real team, so the Spy never appears as an Outsider candidate, and there is no path to a "0" when an Outsider seat exists. The only signal is a generic caveat from `misregistrations` (`InfoCalc.kt:121-130`).

4. **P1 · The caveat list is unscoped.**
   `misregistrations(ctx, ctx.players)` (`InfoCalc.kt:412,419`) prints one line for every Spy and Recluse in the game with wording that is generic to all abilities. For the Librarian the actionable statements are narrow and specific: *"the Spy may be shown as any Outsider"* and *"if the Recluse registers as a Minion and no other Outsider is in play, you may show a 0"*.

5. **P1 · A drunk or poisoned Librarian gets no false-info help at all.**
   `NightScreen.kt:903-930` only knows how to falsify numbers and YES/NO, so for a `startKnowing` character the impaired block is an empty `FlowRow`. The guide prose says *"you may show false information"*; the app knows exactly which Outsiders are **not** in play and which players are **not** Outsiders, and suggests neither. A false **0** — the most natural lie for this character — is likewise unoffered.

6. **P1 · Nothing records what was shown.**
   The `Outsider` / `Wrong` tokens are the only trace, they do not record *which* character token was displayed, and the official instruction is to remove them when convenient. After that the game has no memory of the Librarian's information at all — not in the log (`GameExtras.kt:40-64`), not in the reveal flow (`RevealFlow.kt`).

7. **P2 · The headline dumps every Outsider instead of proposing a pair.**
   `InfoCalc.kt:416-420`. In a Baron game that is three Outsiders and the storyteller must pick one, pick a decoy, place two tokens and open a character picker by hand. A one-tap **"Show Butler · point at Fred and Ana"** action is the whole feature.

8. **P2 · The decoy is unassisted.**
   Nothing suggests a "Wrong" player, nothing excludes the Librarian's own seat (`NightScreen.kt:315` lists all seats), and nothing warns when the decoy is itself an Outsider — legal, but usually a mistake and worth a nudge.

9. **P2 · The `Outsider` and `Wrong` tokens never expire and clutter the grimoire all game.**
   Neither is in `EXPIRES_AT_DAWN` nor `EXPIRES_AT_DUSK` (`GameActions.kt:218-242`), and there is no "clear night-1 info tokens" action. On a phone this is real screen cost from night 2 onward.

10. **P2 · The "0" case does not distinguish "genuinely zero" from "zero because you ruled the Recluse evil".**
    Both are legal answers with very different downstream consequences (the Recluse ruling should then be honoured by the Chef, Empath, Undertaker and Ravenkeeper), and neither is recorded.

11. **P3 · Case-sensitive YES/NO detection is a latent trap beyond this character.**
    `NightScreen.kt:887-888` uses `startsWith("YES")` / `startsWith("NO")` against calculator headlines that are written in ordinary sentence case elsewhere. Detection should be a field on `InfoResult`, not string sniffing.

12. **P3 · No path for a mid-game Librarian.**
    `librarian` is only in the `firstNight` list, so a seat that becomes the Librarian later (storyteller change via `SeatSheet.kt:310`, Pit-Hag, Amnesiac on other scripts) never learns anything.

## Proposed behaviour (spec)

### Setup step (new)

- **when:** during `Phase.SETUP`, once a seat holds `librarian` and every seat has a character.
- Prompt, in the same family as `GameShell.kt:347-479`:
  **"Librarian information"** — *"Pick the Outsider the Librarian learns and the two players you will point at — or choose to show a 0."*
- **Character picker sections:**
  - **In play** — every seat whose real character is an Outsider, including a seat holding `drunk` shown as **Drunk** (per the wiki example).
  - **Misregistration** — every `spy` seat, expandable to any Outsider on the script: *"Ana (Spy) registering as the Butler"*.
  - **Show 0** — always available, with the label *"Zero Outsiders"* and, when an Outsider does exist, the sub-label *"only legal if you rule the Recluse registers as a Minion, or the Librarian is impaired"*.
- **Player pickers:** the "true" seat follows the character choice; the decoy is a second pick over all other seats, sorted alive-good-non-Outsider first, the Librarian's own seat last with a hint. Warn (do not block) if the decoy is also an Outsider. Suppressed entirely when **Show 0** is chosen.
- **Effects:** `placeExclusiveReminder("librarian", "Outsider")` and `placeExclusiveReminder("librarian", "Wrong")`; for the 0 case, record the choice in the log with no tokens.
- **Validation:** add a **soft** `validateSetupState` issue *"Librarian: choose the Outsider and the two players to point at (or a 0)"*.

### Night action

- **when:** first night only (order position 47, unchanged). Wake condition: the Librarian seat exists and is alive.
- **targets:** none chosen by the player; the setup pairing is replayed.
- **information (structured — shared with Investigator/Washerwoman):**

  ```
  StartKnowingInfo(
    candidates: List<Candidate>,   // (playerId, characterId, kind)
    zeroAllowed: Boolean,          // Librarian: true
    zeroIsTrue: Boolean,           // no Outsider in play at all
    impairment: Impairment,
  )
  Candidate.kind = TRUE | MISREGISTERED | FALSE
  ```

  - `TRUE`: seats whose real character is an Outsider (the Drunk included, shown as **Drunk**).
  - `MISREGISTERED`: every `spy` seat × every Outsider on the script, reason *"the Spy may register as this Outsider"*.
  - `FALSE` (surfaced when `impairment != NONE`): every Outsider on the script that is **not** in play, plus a suggested pair of two non-Outsider players, plus a false **0**.
  - `zeroAllowed = true`; `zeroIsTrue` = no Outsider seat exists. A "0" while `zeroIsTrue == false` must be labelled with its justification (Recluse ruling, or impairment).
- **what the step shows:**
  - Primary: **"Show Butler · point at Fred and Ana"** — one tap opens the prepared `ShowCard.CharacterCard("One of the 2 players I point to is this character", "butler")`.
  - Zero case: a first-class **"Show 0"** chip that opens `ShowCard.NumberCard(0)` directly from the step. This must exist whether the 0 is true, a Recluse ruling, or a lie.
  - A **"Change the pairing"** link back into the setup picker.
- **impaired/false alternative:** a promoted **"False info — show instead"** section: Outsiders not in play (one tap each), a suggested wrong pair, and **Show 0**. When `MUST_BE_FALSE` (Vortox), demote the true pairing.
- **visibility:** nothing to evil beyond the ordinary grimoire view the Spy gets.
- **expiry:** as for the Investigator — `librarian/Outsider` and `librarian/Wrong` become dimmed "spent" tokens at the first dawn, cleared by a single **"Clear night-1 info tokens"** action rather than automatically.
- **log:** `NightRecord(cycle = 1, stepId = "librarian", holderIds, targetIds = [trueSeat, decoySeat] (empty for a 0), outcome = "Shown: Butler" | "Shown: 0", impaired)` plus any Recluse/Spy ruling, so later steps can stay consistent.
- **day-time inputs:** none required. Optional: record the Librarian's public claim.

### Fix the generic string sniffing

Add explicit fields to `InfoResult` so the UI stops parsing English:

```
InfoResult(
  headline, detail, caveats,
  numeric: Int? = null,          // a value that can be flashed as a NumberCard
  numericRange: IntRange? = null,// legal false values for this character
  boolean: Boolean? = null,      // YES/NO answers
  impairment: Impairment = NONE,
)
```

The Librarian's zero case sets `numeric = 0, numericRange = 0..0`. `NightScreen.kt:886-930` then keys off these fields instead of `takeWhile { it.isDigit() }` and `startsWith("NO")`.

### UI text

- Setup prompt: **"Librarian information"** / *"Which Outsider do they learn, and who are the two players? Or show a 0."*
- Night step, prepared: **"Show the Butler token, point at Fred and Ana."**
- Zero: **"Show a 0 — no Outsiders are in play."**
- Zero with an Outsider present: **"Show a 0 — you are ruling that Cara (Recluse) registers as a Minion."**
- Impaired: **"⚠ <Librarian> is POISONED — show false info. Outsiders not in play: Saint, Recluse. Or show a false 0."**

### Data changes

- `characters.json` — none; text and reminders are current.
- `night_guide.json` `librarian.first.instructions` — split the setup placement out of the night text, and add a `shows` entry `{label: "Show 0", kind: "message", text: "0"}` (or rely on the new `numeric` field) so the zero card exists as data.

## Tests to add

1. `librarian zero case exposes a showable zero`
   Given no Outsider in play. Then the result carries `numeric == 0` so the UI can render a **Show 0** chip. (Today `leadingNumber` is null and `isNo` is false.)

2. `librarian counts the drunk as an outsider and names them Drunk`
   Given a seat with `characterId = "drunk"` and `shownCharacterId = "monk"`.
   Then that seat is a `TRUE` candidate and the character shown is **Drunk**, never Monk.

3. `washerwoman does not see the drunk as a townsfolk` *(same shared helper, opposite direction)*
   Given the same seat. Then the Washerwoman's `TRUE` candidates exclude it.

4. `librarian offers the spy as an outsider`
   Given a `spy` at seat 6 on a script whose Outsiders are Butler, Drunk, Recluse, Saint.
   Then `candidates` contains a `MISREGISTERED` entry for seat 6 for each of those Outsiders. (Today the Spy never appears.)

5. `librarian may show zero when the only outsider is a recluse`
   Given `recluse` as the sole Outsider. Then `zeroAllowed == true`, `zeroIsTrue == false`, and the zero option carries the Recluse justification.

6. `librarian false info lists outsiders not in play`
   Given a poisoned Librarian with `butler` and `drunk` in play.
   Then the false candidates include `recluse` and `saint`, exclude `butler` and `drunk`, and include a false 0.

7. `librarian caveats are scoped to this ability`
   Given a Spy and a Recluse in play. Then the caveats are the two Librarian-specific statements, not the generic per-player lines produced today by `misregistrations(ctx, ctx.players)`.

8. `librarian setup places both tokens exclusively`
   When the pairing is chosen twice with different seats. Then exactly one `librarian/Outsider` and one `librarian/Wrong` exist.

9. `night-1 info tokens survive dawn and are cleared by the clear action`
   Given `librarian/Outsider` and `librarian/Wrong`. When `advancePhase` runs NIGHT→DAY, both remain; when the clear action runs, both are gone.

10. `librarian information is written to the night log`
    Given the storyteller shows the Butler token pointing at seats 4 and 6.
    Then `state.nightLog` contains `NightRecord(cycle = 1, stepId = "librarian", targetIds = [4, 6], outcome = "Shown: Butler")`.

11. `librarian step is first night only`
    Given a Librarian on night 2. Then the night sheet contains no Librarian step.
