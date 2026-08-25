# Gambler (gambler) — Bad Moon Rising Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Gambler> (fetched 2026-08-25).

Current ability text:

> "Each night*, choose a player & guess their character: if you guess wrong, you die."

How to Run (wiki):

- *"Each night except the first, wake the Gambler. They indicate a player, then point to a
  character icon. If that player is not the chosen character, mark the Gambler as **DEAD** and
  return them to sleep."*
- The guess is two pointings: **a player**, then **a character on the character sheet**.

Edge cases and clarifications:

- *"The Gambler may choose any player, dead or alive, even themself."*
- *"The Gambler does not learn from the Storyteller whether their guess is correct or
  incorrect."* No feedback at all — the death (or its absence) at dawn is the only signal.
- No first-night action.
- Strategy notes: self-guessing is a way to prove your own character; guessing evil players is
  higher risk because you must pick the right Minion/Demon out of several.
- **The page does not address** misregistration (Spy/Recluse), drunk/poisoned Gamblers, or
  whether protection can stop the death. Those are covered below from general rules and from
  the app's own `night_guide.json`, and are **flagged as inference, not quotation**:
  - Drunk/poisoned: a malfunctioning ability does not kill its owner. The app's guide states
    *"If the Gambler is drunk or poisoned, they do not die even if they guess wrong."*
    In strict rules terms a malfunctioning ability is Storyteller-controlled, so this should
    be a **default with an override**, not a hard rule.
  - Protection: the death is an ordinary night death, so abilities that prevent *any* death
    (Innkeeper "Protected", Sailor, Tea Lady, Fool's first death, Lleech host) stop it, while
    Demon-only protections (Soldier, Monk "Safe") do **not**.
  - Misregistration: the Recluse may register as a Minion or Demon and the Spy as a Townsfolk
    or Outsider, so the Storyteller may rule a guess of "Imp" on the Recluse correct. This is a
    judgement call the app should surface rather than decide.
  - The **Drunk**, the **Lunatic** and the **Marionette** genuinely *are* the Drunk / Lunatic /
    Marionette. Guessing the character they are *shown* is **wrong**.

Jinxes:

- **Lycanthrope:** "If the Lycanthrope is alive and the Gambler kills themself at night, no
  one else can die that night." (Present in the app's data.)

Night order: the Gambler is early — after the Courtier, before the Snake Charmer, the Monk and
every Demon. A Gambler who dies from a wrong guess is **already dead when the Demon chooses**,
which changes what the Demon should be told and removes the Gambler from later steps.

## What the app does today

- `characters.json:417-429` — ability text matches. `otherNightReminder`: "The Gambler points
  to a player, and a character on their sheet. If incorrect, the Gambler dies."
  `firstNightReminder` empty (correct). `reminders: ["Dead"]`.
- `night_and_jinxes.json:390` — `gambler` at otherNight index **17**, immediately after the
  Courtier (15) and Wizard (16), well before the Demons (36–54). **Correct.**
- `night_and_jinxes.json:165-166` — the `lycanthrope`/`gambler` jinx is present and will
  surface in `SeatSheet.kt:223-234` and the active-jinx dialog. **Works.**
- `night_guide.json:240-245` — good prose, including the two-pointings flow, the Dead token,
  and the drunk/poisoned caveat.

That is the whole implementation. There is **no code path for the Gambler anywhere**:

- Not in `InfoCalc.supports` (`InfoCalc.kt:29-36`) — correct, it is not an info role, but it
  means the expanded step has no interactive area at all.
- Not in `QuickResolutions` (`NightScreen.kt:462-522`) — no player picker, no character
  picker, no comparison, no kill button.
- The night tray offers the "Sheet" show card (`NightScreen.kt:254-262`) so the Gambler can
  point at a character silently — **this part works and is genuinely useful** — but the
  storyteller's answer to "was that right?" is entirely in their head, and nothing is recorded.
- To kill the Gambler the storyteller must leave the Night tab, open the seat, and press
  "Died at night" (`SeatSheet.kt:270-273`), which records `DeathCause.DEMON` — **the wrong
  cause** for a Gambler self-kill.

## Defects and gaps

1. **P0 · Wrong death cause is the only one available.** `SeatSheet.kt:270-273` hard-codes
   `DeathCause.DEMON` for "Died at night"; `DeathCause.OTHER_NIGHT_DEATH` (`GameState.kt:76`)
   is never produced by any UI path. A Gambler death recorded as `DEMON` corrupts the
   Undertaker/Cannibal history, and if the Gambler happened to be the Grandmother's grandchild
   it makes the Grandmother look like she must die (`StatusEffects.kt:122-127`). Repro: run the
   Gambler step, then kill the Gambler from their seat.

2. **P1 · The guess is never entered, so the app can never check it.** The storyteller has to
   remember which player and which of 171 character tokens were pointed at, compare against
   the grimoire by eye, and decide — while keeping the table silent. The app already knows
   every player's true `characterId` (`GameState.kt:20`) and can do the comparison instantly.
   Repro: open the Gambler step on night 2 — the panel contains prose and a "Sheet" chip and
   nothing else.

3. **P1 · Misregistration is not surfaced.** `InfoCalc.misregistrations` (`InfoCalc.kt:121-130`)
   already produces the exact Spy/Recluse sentences, and `Player.shownCharacterId`
   (`GameState.kt:20-25`) already knows who the Drunk/Lunatic/Marionette are. None of it
   reaches the Gambler step. The nastiest case is silent: a Gambler guesses "Chef" on a player
   who is the **Drunk** shown the Chef token. The correct answer is *wrong guess, the Gambler
   dies*, and a storyteller glancing at the grimoire circle — which renders the seat with its
   shown identity — can easily get it backwards.

4. **P1 · A drunk/poisoned Gambler is not detected.** The rule (and the app's own guide) is
   that they do not die. Nothing in the step checks
   `StatusEffects.isImpaired(state, lookup, holder)` even though it is a one-line call
   (`StatusEffects.kt:36-46`). Repro: put a Courtier "Drunk" or Poisoner token on the Gambler,
   run the step — no warning.

5. **P1 · No protection check on the Gambler's own death.** Every other death path in the app
   at least renders `StatusEffects.deathNotes` (`SeatSheet.kt:250-255`,
   `NightScreen.kt:588-590`). The Gambler step renders none, so an Innkeeper-protected or
   Tea-Lady-protected Gambler is killed without a prompt.

6. **P2 · The "Dead" reminder token is not placed automatically**, and the step is not
   auto-ticked after resolution.

7. **P2 · No record of the guess.** `GameState` has no structured night-choice storage
   (`GameState.kt:94-115`), so the game log (`GameExtras.kt:46-106`) never shows "Mina guessed
   Greta = Grandmother (correct)". The storyteller loses the single most useful piece of
   after-the-fact context for a Gambler game.

8. **P2 · The Lycanthrope jinx is not enforced at the point of use.** The jinx is in the data
   and shows in the seat sheet, but when a living Lycanthrope is in play and the Gambler
   self-guesses wrong, nothing tells the storyteller at the Gambler step that **no one else can
   die tonight** — and the Demon's step, 19 rows later, still offers a full kill panel
   (`NightScreen.kt:518-522`).

9. **P3 · The step does not remind that the Gambler learns nothing.** Storytellers new to the
   character often want to signal the result. The guide says it; the step text does not.

## Proposed behaviour (spec)

- **when:** other nights only. Wake condition: `holder.alive`. (No once-per-game limit; the
  Gambler may guess every night.)
- **targets:** one **player** *and* one **character**.
  - Player constraints: any player, **alive or dead**, **including themselves**. The picker
    must therefore list every seat, with dead seats dimmed but enabled, and the Gambler's own
    seat labelled `(self)` and sorted first-adjacent like `DemonKillPanel` does
    (`NightScreen.kt:559-583`).
  - Character constraints: any character on the script sheet. Picker: the same searchable,
    in-play-first character grid proposed for the Courtier and already implemented for guide
    show cards (`NightScreen.kt:405-434`). **Do not visually distinguish in-play characters in
    this picker beyond ordering** — the storyteller is recording what the player pointed at,
    not being advised.
- **immediate effects** once both are chosen: the engine evaluates and the panel reports one of

  | condition (checked in order) | outcome |
  |---|---|
  | Gambler is impaired (`isImpaired`) | **no death** (default) + `[Kill anyway]` override + Mathematician note |
  | `target.characterId == guess` | **correct** — no death |
  | `target.characterId != guess` but target is Spy/Recluse and `guess` is a plausible misregistration | **Storyteller's call** — show both options explicitly |
  | `target.characterId != guess` | **wrong** — the Gambler dies |

  On "wrong", show `StatusEffects.deathNotes(state, lookup, gamblerId)` before the confirm
  button (Innkeeper/Sailor/Tea Lady/Fool can all stop it), then a single
  **[Mina dies — wrong guess]** button which, in one undoable update:
  - `GameActions.kill(state, gamblerId, DeathCause.OTHER_NIGHT_DEATH, lookup)` — **never
    `DEMON`**;
  - `placeExclusiveReminder(gamblerId, PlacedReminder("gambler", "Dead"))`;
  - records `NightChoice(cycle, "gambler", gamblerId, targetIds = [targetId],
    characterId = guess, note = "wrong")` (see the shared record proposed in `courtier.md`);
  - auto-ticks the step.

  On "correct", the same `NightChoice` is recorded with `note = "correct"` and the step is
  auto-ticked with no other effect.

- **the shown-identity trap (must be explicit):** when the chosen player has a
  `shownCharacterId` (Drunk / Lunatic / Marionette) the panel must lead with, in red:
  `Blake is really the DRUNK (shown the Chef). A guess of "Chef" is WRONG.`
  This uses `Player.characterId` for the comparison and `Player.shownCharacterId` only for the
  warning. `Player.nightRoleId` (`GameState.kt:35-41`) must **not** be used here.
- **deferred effects:** none. The death is immediate and announced at dawn like any other
  night death.
- **expiry:** `gambler:Dead` is a permanent record of how the player died — keep it out of
  `EXPIRES_AT_DAWN` / `EXPIRES_AT_DUSK` (`GameActions.kt:218-242`).
- **information:** the Gambler receives **nothing**. The step must say so, so the storyteller
  does not leak the answer with a reaction. No show cards other than the existing "Sheet".
- **visibility:** nothing is shown to the Demon/Minions/Lunatic. But because the Gambler acts
  at index 17 and the Demons at 36–54, a Gambler who died here is already dead when the Demon
  chooses — the Demon's kill panel already dims dead seats (`NightScreen.kt:571-575`), which is
  correct.
- **day-time inputs:** none. (A Gambler who publicly claims their guess is a `CLAIM` for the
  general statement ledger proposed in `gossip.md`, nothing more.)
- **interactions/jinxes:**
  - **Lycanthrope** (`night_and_jinxes.json:165-166`): when a living Lycanthrope is in play and
    the Gambler dies from a **self**-guess, the engine must place a game-scoped
    "no one else dies tonight" marker and every later kill panel that night — Demons included
    — must show it prominently and default to "No kill". At minimum, surface the jinx text on
    the Gambler step the moment a self-guess is entered.
  - **Spy / Recluse:** show `InfoCalc.misregistrations(ctx, listOf(target))` verbatim and make
    the ruling an explicit two-button choice, not an implicit one.
  - **Zombuul:** a Zombuul registering as dead is still the Zombuul; a guess of "Zombuul" is
    correct.
  - **Pit-Hag / Barber / Snake Charmer:** the comparison is against the character *right now*,
    which `characterId` already reflects after `swapCharacters` (`GameActions.kt:99-115`).
  - **Grandmother:** if the Gambler is the grandchild and dies here, the Grandmother must
    **not** die (wiki example, quoted in `grandmother.md`). Guaranteed by using
    `OTHER_NIGHT_DEATH`.
  - **Mathematician:** an impaired Gambler who guesses wrong and survives is a malfunction.

### UI text the step should display

- Header: `Gambler — they point at a player, then at a character.`
- After the player pick: `Who did Mina say Greta is?`
- Correct: `Correct — Greta IS the Grandmother. Mina lives. Tell her nothing.`
- Wrong: `Wrong — Greta is the Grandmother, not the Innkeeper. Mina dies.`
- Shown-identity trap: `Blake is really the Drunk (shown the Chef). "Chef" is a WRONG guess.`
- Impaired: `Mina is poisoned — her ability doesn't work, so she doesn't die. (Override if you
  prefer.)`
- Always, in small type: `The Gambler never learns whether they were right.`

### Data changes

- `characters.json:417-429` — no change.
- `night_guide.json:240-245` — soften the drunk/poisoned sentence to note that a malfunctioning
  ability is ultimately the Storyteller's call, and add "The Gambler may guess any player,
  dead or alive, including themselves."
- `night_and_jinxes.json` — no change.

## Tests to add

1. **Correct guess: no death.** Given a BMR game on night 2 with a Gambler and a Grandmother,
   When the guess `(target = grandmotherSeat, character = "grandmother")` is resolved, Then no
   `DeathRecord` is added and a `NightChoice` with `note = "correct"` is recorded.

2. **Wrong guess: death with the right cause.** Given the guess
   `(target = grandmotherSeat, character = "innkeeper")`, Then the Gambler is dead, the newest
   `DeathRecord.cause == DeathCause.OTHER_NIGHT_DEATH` (**not** `DEMON`), and the Gambler
   carries `PlacedReminder("gambler", "Dead")`. **Fails today** — no resolver exists and the
   only UI path produces `DEMON`.

3. **Shown identity does not satisfy the guess.** Given a seat with
   `characterId = "drunk"`, `shownCharacterId = "chef"`, When the Gambler guesses `"chef"`,
   Then the resolver reports **wrong**; and when they guess `"drunk"`, Then **correct**.

4. **Self-guess of their own character is correct.** Given the Gambler guesses
   `(self, "gambler")`, Then the resolver reports correct and no death occurs.

5. **Dead players are legal targets.** Given a dead seat, Then the target picker includes it
   and a correct guess against it resolves normally.

6. **Impaired Gambler survives a wrong guess.** Given the Gambler carries
   `courtier:Drunk 1`, When a wrong guess is resolved, Then the default outcome is no death
   and `StatusEffects.isImpaired` is `true`.

7. **Protection blocks the self-kill.** Given the Gambler carries `innkeeper:Protected`, When
   a wrong guess is resolved, Then `StatusEffects.deathNotes` for the Gambler contains the
   "can't die tonight" line and the resolver surfaces it before killing.

8. **Grandchild Gambler dying by their own guess does not kill the Grandmother.** Given the
   `grandmother:Grandchild` token on the Gambler's seat, When the Gambler dies with cause
   `OTHER_NIGHT_DEATH`, Then no Grandmother death is produced.

9. **Lycanthrope jinx surfaces on a self-guess.** Given a living Lycanthrope and a wrong
   self-guess, Then the resolver emits the jinx text and marks the night "no one else dies".

10. **No first-night step.** Given a BMR game with a Gambler, When
    `nightOrder.firstNight(...)` is built, Then no step has `id == "gambler"`.
    (Passes today — regression guard.)
