# Innkeeper (innkeeper) — Bad Moon Rising Townsfolk

## Official rules (sources)

Sources:
- https://wiki.bloodontheclocktower.com/Innkeeper
- https://wiki.bloodontheclocktower.com/Leviathan (jinx)
- https://wiki.bloodontheclocktower.com/Riot (jinx)
- https://wiki.bloodontheclocktower.com/Glossary ("Drunk", "Dusk", "Dawn")

**Current ability text (matches `characters.json`):**

> "Each night\*, choose 2 players: they can't die tonight, but 1 is drunk until dusk."

**How to run (wiki, quoted):**

> "Each night except the first, wake the Innkeeper. They point at two players. Mark both with
> **SAFE** reminders. One becomes **DRUNK**—mark them with the **DRUNK** reminder. Players marked
> **SAFE** cannot die that night. Remove **SAFE** reminders at dawn. Remove **DRUNK** reminder at dusk."

**Key mechanics (wiki):**

- "Protects from death by Demon, Outsiders, Minions, Townsfolk, and Travellers" — i.e. **all**
  night deaths, not just the Demon's (Godfather, Gossip, Assassin*, Moonchild, Gambler-adjacent,
  Zombuul, Pukka, Shabaloth, Po …).
- "Protection only applies at night, not during the day" — an Innkeeper-protected player who is
  **executed** dies normally. `SAFE` is gone by then anyway (removed at dawn).
- "One protected player becomes drunk for that night and the following day."
- "If Innkeeper protects themselves, they may become drunk, negating protection for both players."

**Examples (wiki):**
1. Innkeeper protects Fool and Chambermaid; Fool becomes drunk and dies when executed.
2. Innkeeper protects Assassin and Po; Assassin becomes drunk, ability fails.
3. Innkeeper protects self and Pacifist; Innkeeper becomes drunk; Pacifist dies to the Demon.

Derived rules that matter:

- **Which of the two is drunk is the Storyteller's choice**, not the Innkeeper's.
- **Two protection tokens, one drunk token.** The wiki says "Mark **both** with SAFE reminders"
  (plural). Physical token count: 2 × protection, 1 × drunk.
- **The drunk one is still protected.** "they can't die tonight, but 1 is drunk" — the drunkenness
  removes that player's *ability*, not their protection. (Unless the drunk one is the Innkeeper —
  then the Innkeeper's own ability fails and *neither* player is protected, per example 3 and the
  app's own night guide.)
- **Drunk duration:** placed at night N, removed at the dusk that ends day N. One dusk.
- **Protection duration:** tonight only, removed at dawn.
- **A drunk/poisoned Innkeeper protects nobody and makes nobody drunk** (the app's
  `night_guide.json` states this explicitly and it follows from the Glossary's "Drunk" entry).
- **\*Assassin bypass:** the Assassin's "they die, even if for some reason they could not" cuts
  through Innkeeper protection.
- **Jinxes (both missing from the app's data):**
  - Leviathan: *"If the Leviathan nominates and executes an Innkeeper-protected player, good wins."*
  - Riot: *"If Riot nominates and executes an Innkeeper-protected player, good wins."*
- **Uncertain:** whether the Innkeeper may point at dead players. The ability does not say "alive";
  the wiki says only "They point at two players". Treat as legal but pointless, and warn.

## What the app does today

- `characters.json` — ability, `otherNightReminder` and expiry semantics correct;
  `"reminders": ["Protected","Drunk"]` — **one** Protected label (see defect 1).
- `night_and_jinxes.json` — `otherNight[14] = "innkeeper"`, absent from `firstNight`: correct.
  **No jinx entries at all** for the Innkeeper (verified: 58 jinxes, zero mention innkeeper).
- `night_guide.json` (innkeeper → other) — prose is correct and complete, including the
  impaired-Innkeeper rule. Prose only; nothing is enforced.
- `GameActions.kt:220` — `("innkeeper","Protected")` in `EXPIRES_AT_DAWN`. **Works.**
- `GameActions.kt:234` — `("innkeeper","Drunk")` in `EXPIRES_AT_DUSK`, cleared on the DAY→NIGHT
  transition (`GameActions.kt:261`). **Works** — the expiry timing the user asked about is right.
- `StatusEffects.kt:36-46` — `isImpaired` matches any reminder label containing "drunk", so the
  Innkeeper's Drunk token correctly impairs its holder. **Works.**
- `StatusEffects.kt:67` — `deathNotes` renders `"Marked 'Protected' (Innkeeper) — can't die tonight."`
- `SeatSheet.kt:239-307` — killing from a seat routes through a "might be protected" dialog
  offering **"They die anyway"** / **"Death prevented"**. **Works** (this is the good pattern).
- `NightScreen.kt:534-638` — `DemonKillPanel` shows the same notes as red text (`:588-590`) but the
  **"{name} dies"** button (`:625-633`) is enabled regardless.
- `NightScreen.kt:308-354` — the night tool tray. `availableCopies =
  character.allReminders.count { it == pendingReminderLabel }` (`:319-321`); when that is `≤ 1` it
  calls `placeExclusiveReminder`, which **strips the token from every other seat first**
  (`GameActions.kt:194-201`).
- There is no Innkeeper-specific resolver in `QuickResolutions` (`NightScreen.kt:470-524`); the ST
  places all three tokens by hand.

## Defects and gaps

1. **P0 · You cannot place two Protected tokens from the night sheet.**
   Rules: mark **both** chosen players. App: `characters.json` lists `"Protected"` once, so
   `availableCopies == 1` (`NightScreen.kt:319-321`) and the tray uses `placeExclusiveReminder`
   (`GameActions.kt:194-201`) — tapping Protected on the second player **removes it from the
   first**. Repro: night 2, open the Innkeeper step, tap "Protected" → player A, tap "Protected"
   → player B. A is now unprotected. The ST must leave the night sheet and use the seat sheet's
   "Add reminder" (`SeatSheet.kt:109-117`, plain `addReminder`) to get two tokens down.
   Fix is one line of data: `"reminders": ["Protected","Protected","Drunk"]`.

2. **P0 · The Demon kill panel lets you kill a Protected player with one tap.**
   Rules: a SAFE player cannot die tonight. App: `NightScreen.kt:588-590` prints the warning,
   `:625-633` still enables **"{name} dies"** with no confirmation. Contrast `SeatSheet.kt:266-268`,
   which gates it. Repro: protect A, open the Demon step, tap A, tap "A dies" — A dies.

3. **P0 · Innkeeper protection is not applied to non-Demon night deaths.**
   Rules: SAFE stops death from any source at night. App: the only place the note is honoured is
   the seat sheet dialog; the Gossip/Godfather/Assassin/Moonchild resolutions all go through the
   generic seat-sheet or `DemonKillPanel` paths, and `deathNotes` never distinguishes "the Assassin
   ignores this" from "this stops the kill".

4. **P1 · No Innkeeper resolver — three manual token taps every night.**
   The app knows the whole action (2 targets, ST picks one to be drunk). There is no
   `QuickResolutions` branch (`NightScreen.kt:470-524`), so the ST does: tap Protected → seat,
   tap Protected → seat (broken, see 1), tap Drunk → seat. Every other multi-step interaction
   (Snake Charmer, Fang Gu, Professor) has a one-tap resolver.

5. **P1 · A drunk/poisoned Innkeeper's tokens are placed as if they worked.**
   Rules and the app's own guide: impaired ⇒ no protection, no drunkenness. App: nothing checks
   `StatusEffects.isImpaired(holder)` on this step. The ST places real Protected tokens that
   `deathNotes` will then quote back at them as genuine protection.

6. **P1 · Self-protection trap not detected.**
   Rules (wiki example 3): if the Innkeeper is the one made drunk, *neither* chosen player is
   protected. App: places two Protected tokens plus a Drunk on the Innkeeper and then reports both
   as protected in `deathNotes` — actively misleading the ST into preventing a legal kill.

7. **P1 · Both Innkeeper jinxes are missing from the data.**
   `night_and_jinxes.json` contains 58 jinxes and none mention the Innkeeper. The Leviathan and
   Riot jinxes are *game-ending* ("good wins") and the app is the only place the ST would see them —
   `SeatSheet.kt:225-234` renders jinxes per seat, so adding the data is enough to surface them.

8. **P2 · The step is shown for a dead Innkeeper with full tools.**
   `NightOrder.kt:142-178` has no alive filter; the row only gets a grey "All holders are dead —
   usually skip" line (`NightScreen.kt:751-757`) while the tray still offers the tokens.

9. **P2 · No "same as last night?" context.** The Innkeeper *may* repeat targets (unlike the
   Exorcist/Devil's Advocate), but the ST has no record of last night's choice once the tokens
   expire. A one-line "last night: A & B (B drunk)" would remove a common table question.

10. **P3 · Token label drift.** The wiki now names the token **SAFE**; the app (and the community
    data) use "Protected". Keep "Protected" — `StatusEffects.kt:66-67` keys off the lowercase label
    and the Monk already owns "Safe" — but say "Protected (SAFE)" in the guide text.

11. **P3 · The step detail duplicates work the engine already did.** `otherNightReminder` opens with
    "The previously protected and drunk players lose those markers", but `advancePhase` already
    swept both (dawn and dusk respectively) before the Innkeeper wakes.

## Proposed behaviour (spec)

### Night action

- **when:** other nights only. Wake condition: `holder.alive`.
- **targets:** exactly 2, distinct. Constraints: any player (self allowed); sort alive first and
  badge dead candidates *"already dead — protection does nothing"*. Then a **second, Storyteller-only
  choice**: which of the two is drunk (default: the non-Innkeeper one; a single "make {name} drunk
  instead" toggle).
- **immediate effects** — one engine call `GameActions.innkeeperProtect(state, innkeeperId, aId,
  bId, drunkId, lookup)`:
  - Clear any surviving `("innkeeper","Protected")` and `("innkeeper","Drunk")` tokens (defensive;
    expiry should already have).
  - `addReminder(a, PlacedReminder("innkeeper","Protected"))`, same for `b` — **two independent
    tokens**, never `placeExclusiveReminder`.
  - `addReminder(drunkId, PlacedReminder("innkeeper","Drunk"))` (exclusive within this source).
  - If `StatusEffects.isImpaired(innkeeper)` **before** this action: place no tokens at all; show
    *"The Innkeeper is drunk/poisoned — nobody is protected and nobody is made drunk."* (Offer an
    ST override that places the tokens marked "(no effect)" if they want the bluff-preserving
    bookkeeping.)
  - If `drunkId == innkeeperId`: place the Drunk token but **not** the Protected tokens, and show
    *"The Innkeeper made themselves drunk — their ability fails: neither player is protected."*
- **deferred effects:** none at dawn beyond expiry; the drunk player's abilities silently fail for
  the rest of tonight and all of tomorrow (handled by `isImpaired`).
- **expiry:** `("innkeeper","Protected")` → `EXPIRES_AT_DAWN` (already correct);
  `("innkeeper","Drunk")` → `EXPIRES_AT_DUSK` (already correct).
- **information:** none.
- **visibility:** nothing shown to anyone. The chosen players are not told.
- **day-time inputs:** none.
- **interactions to handle explicitly:**
  - **Assassin** — must be able to kill through Protected. The death panel should say
    *"Innkeeper-protected — but the Assassin kills even if they could not."*
  - **Execution** — Protected never applies; the token is gone by dawn anyway.
  - **Leviathan / Riot jinx** — if the nominee holds `("innkeeper","Protected")` and the nominator
    is the Leviathan/Riot, the nomination warning must read *"JINX: if this nomination executes
    them, GOOD WINS."* Wire into `StatusEffects.nominationWarnings` (`StatusEffects.kt:132-166`).
  - **Innkeeper protecting the Demon / a Minion** — legal and common (wiki example 2); the panel
    must not warn it away.

### Protection must become enforcement, not a note

`StatusEffects.deathNotes` currently returns flat strings. Split it:

```kotlin
enum class DeathModifier { BLOCKS, MAY_BLOCK, TRIGGERS, IGNORED_BY_ASSASSIN }
data class DeathNote(val text: String, val modifier: DeathModifier, val sourceId: String)
fun deathNotes(state, lookup, playerId, cause: DeathCause): List<DeathNote>
```

`("innkeeper","Protected")` returns `BLOCKS` for every night cause and is simply absent for
`EXECUTION`. Every kill entry point — `DemonKillPanel` (`NightScreen.kt:624-635`), the Day tab's
Execute button (`DayScreen.kt:111-114`, `:350-357`), the dusk guard (`GameShell.kt:599-604`) and
`SeatSheet` (`:266-268`) — routes through one shared `confirmDeath(...)` that defaults to
**"Death prevented"** when a `BLOCKS` note is present.

### UI text the step should display

- **"Innkeeper — they point at 2 players. Both are safe tonight; you choose which one is drunk."**
- After picking two: **"{A} and {B} can't die tonight. Who is drunk until dusk tomorrow?"**
- **"! The Innkeeper is drunk/poisoned — no protection, no drunkenness."**
- **"! {name} chose themselves and you made them drunk — their ability fails, so neither player is
  protected."**
- **"Both are safe from every night death — except the Assassin."**
- Dead candidate badge: **"already dead"**.

### Data changes

- `characters.json` → innkeeper: `"reminders": ["Protected", "Protected", "Drunk"]`.
- `night_and_jinxes.json` → add:
  ```json
  { "id1": "innkeeper", "id2": "leviathan",
    "reason": "If the Leviathan nominates and executes an Innkeeper-protected player, good wins." },
  { "id1": "innkeeper", "id2": "riot",
    "reason": "If Riot nominates and executes an Innkeeper-protected player, good wins." }
  ```
- `night_guide.json` → keep the text; append *"Protection lasts only tonight; the drunkenness lasts
  through tomorrow until dusk. The Assassin kills through this protection."*

## Tests to add

1. `innkeeper places two independent protected tokens`
   Given an Innkeeper and two targets. When `innkeeperProtect(a, b, drunk = b)`.
   Then both A and B hold `("innkeeper","Protected")` (this fails today via the tray path, which
   leaves only B).

2. `protected tokens expire at dawn and the drunk token at dusk`
   Given tokens placed on night 2. When `advancePhase` (dawn) — both Protected are gone, the Drunk
   remains and `isImpaired(drunkPlayer)` is still true through day 2. When `advancePhase` (dusk) —
   the Drunk token is gone.

3. `a drunk innkeeper protects nobody`
   Given the Innkeeper holds `("poisoner","Poisoned")`. When `innkeeperProtect(...)`.
   Then neither target holds a Protected token and no Drunk token is placed.

4. `an innkeeper who makes themselves drunk protects nobody`
   Given `drunkId == innkeeperId`. Then the Innkeeper holds `("innkeeper","Drunk")` and no seat
   holds `("innkeeper","Protected")`.

5. `a protected player blocks a demon kill`
   Given B holds `("innkeeper","Protected")`. When `deathNotes(state, lookup, B, DeathCause.DEMON)`.
   Then it contains a note with `modifier == BLOCKS`.

6. `a protected player does not block an execution`
   Same setup, `DeathCause.EXECUTION` ⇒ no `BLOCKS` note.

7. `the assassin ignores innkeeper protection`
   `deathNotes(..., cause = OTHER_NIGHT_DEATH, source = "assassin")` returns
   `IGNORED_BY_ASSASSIN` for the Innkeeper token.

8. `leviathan nominating an innkeeper-protected player warns that good wins`
   Given a Leviathan nominator and a protected nominee, `nominationWarnings` contains "good wins".

9. `innkeeper jinx data is present`
   `GameData.jinxesFor("innkeeper")` returns entries for leviathan and riot (fails today: 0 results).
