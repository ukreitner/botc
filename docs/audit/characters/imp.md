# Imp (imp) — Trouble Brewing Demon

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Imp> (fetched 2026-08-25),
<https://wiki.bloodontheclocktower.com/Scarlet_Woman>,
<https://wiki.bloodontheclocktower.com/Night_Order>.

Current ability text:

> "Each night*, choose a player: they die. If you kill yourself this way, a Minion becomes the Imp."

How to run:

- **First night:** the Imp does *not* act. In a 7+ player game the Imp is woken
  during the shared **Demon info** step: they learn who the Minions are and are
  shown 3 not-in-play good characters as bluffs. In games of 6 or fewer, there is
  no Minion/Demon info step at all.
- **Other nights:** wake the Imp; they point at any player. That player dies.
  Mark them with the DEAD reminder. Put the Imp back to sleep. Announce the death
  at dawn.
- **Dead targets are legal.** Wiki: *"If the Imp attacks a dead player at night,
  let them do so… any player—alive or dead—can be chosen."* Nothing happens, but
  the choice is allowed (it is a common Imp bluff-protection play).
- **Star pass:** if the Imp chooses themselves, the Imp dies and the Storyteller
  *"choose[s] an alive Minion and replace[s] their character token with a spare
  Imp token."* Then wake the new Imp, show them the **YOU ARE** info token and
  the **Imp** token, and put them to sleep.
  - Wiki, verbatim: *"This new Imp does not act that same night, but is now the
    Imp in every other way—they kill each night."*
  - The heir must be **alive**. Dead Minions cannot receive the mantle.
  - **The Storyteller chooses which Minion**, not the Imp.
- **Scarlet Woman precedence (hard rule).** Scarlet Woman wiki, verbatim:
  *"If five or more players are alive when the Imp kills themself at night, the
  Scarlet Woman **must** become the new Imp."* Travellers do not count toward
  the 5. So on a star pass with an alive Scarlet Woman and 5+ non-Traveller
  players alive, the Storyteller has **no choice** — it is her.
- **Recluse:** the Recluse *"might register as evil & as a Minion or Demon"*, so
  the Storyteller may choose a Recluse as the "alive Minion" that becomes the Imp
  (they then genuinely become an evil Imp). This is a well-known Storyteller
  option and should be offered, clearly labelled.
- Protection (Monk `Safe`, Soldier, Innkeeper, Tea Lady, Fool, Sailor…) can stop
  the kill; a drunk/poisoned Imp's attack fails entirely (nobody dies, and no
  star pass occurs even if they pointed at themselves).
- If the Imp dies and no Demon remains alive, good wins — **unless** the Scarlet
  Woman catches it (5+ alive) or a Mastermind extends the day.

Jinxes: the dataset lists **no** jinxes for the Imp (`night_and_jinxes.json`
jinx search for `imp` returns none). Relevant cross-character rules instead come
from Scarlet Woman, Mastermind and Recluse.

Night order position: `otherNight` index 37 (after `scarletwoman` at 29,
`exorcist` 32, `lycanthrope` 33), absent from `firstNight`. This matches the
official sheet.

## What the app does today

Data
- `engine/src/main/resources/botc/data/characters.json` — `imp`: ability text
  matches the wiki; `otherNightReminder` correctly describes the star pass;
  `reminders: ["Dead"]`.
- `engine/src/main/resources/botc/data/night_and_jinxes.json` — `otherNight[37]`
  = `imp`; not in `firstNight`. Correct.
- `engine/src/main/resources/botc/data/night_guide.json` — `imp.other` has good
  prose (mentions "the Scarlet Woman if able", poisoned Imp = nobody dies) and
  one show card: `{"label":"You are the Imp (star pass)","kind":"token",
  "token":"pick","text":"YOU ARE"}`. There is **no** `imp.first` entry (correct —
  the Imp has no first-night step of its own).

Engine
- `engine/src/main/kotlin/com/clocktower/engine/GameActions.kt:79-96` —
  `starPass(state, demonPlayerId, heirPlayerId, lookup)`: kills the demon with
  `DeathCause.OTHER_NIGHT_DEATH`, then sets the heir's `characterId` to the
  demon's character, clears `shownCharacterId` and `alignmentFlipped`. No
  validation that the heir is a Minion, alive, or non-Traveller; no reminder
  token; no Scarlet Woman precedence.
- `StatusEffects.kt:104-109` — when a Demon is the kill target,
  `deathNotes` appends "Scarlet Woman becomes the Demon (5+ alive)." and
  "Imp self-kill: a Minion becomes the Imp."
- `WinCheck.kt:70-86` — when all Demons are dead the advisory says "Every Demon
  is dead — good wins", with cautions naming the Scarlet Woman and the Imp
  star-pass if those ids are anywhere in play.
- `NightOrder.kt:46-48,142-178` — the Imp row is built by grouping players on
  `nightRoleId`; **dead holders are included** in `playerIds`.

UI
- `app/.../screens/NightScreen.kt:518-524` — every Demon with no bespoke
  resolver falls through to `DemonKillPanel`.
- `NightScreen.kt:534-638` — `DemonKillPanel`: chips for every player (alive
  first, self last), an impairment warning at 548-554, `deathNotes` for the
  chosen target at 588-590, then either the star-pass branch (591-622) or a
  "`<name>` dies" / "No kill" row (623-636).
- `NightScreen.kt:591-622` — star pass: heir chips are
  `state.players.filter { it.alive && it.id != holder.id }` sorted Scarlet Woman
  first, then Minions. Tapping one calls `GameActions.starPass`.
- `NightScreen.kt:467` + `520` — `QuickResolutions` takes
  `holder = step.playerIds.firstOrNull()` and only renders `DemonKillPanel`
  when `holder.alive`.

Storyteller experience: the Imp row appears from night 2, expands to the guide
prose plus a "Demon kill — who did X choose?" chip row. Picking a normal target
shows protections/triggers and a one-tap kill. Picking "self" swaps to a
star-pass chip row over *every alive player*, one tap of which kills the Imp and
crowns the heir. Nothing is shown to the new Imp automatically; the "You are the
Imp (star pass)" show card is a separate chip elsewhere in the panel and needs a
manual token pick.

## Defects and gaps

1. **P0 · Star pass lets any alive player become the Imp.**
   Rules: the heir must be an **alive Minion** (or a Recluse registering as a
   Minion). App: `NightScreen.kt:602-607` offers every alive player — Townsfolk,
   Outsiders, and **Travellers** included — and `GameActions.starPass`
   (`GameActions.kt:79-96`) validates nothing. Repro: night 2, expand the Imp
   step, tap the Imp themself, tap any Townsfolk chip → that Townsfolk is now an
   evil Imp.

2. **P0 · Scarlet Woman precedence is only prose, never enforced.**
   Rules: with an alive Scarlet Woman and 5+ non-Traveller players alive she
   **must** become the Imp. App: the SW is merely sorted first in the chip list
   (`NightScreen.kt:605`) and the panel title says "the Scarlet Woman catches it
   first if able" — the Storyteller can tap anyone else. Repro: 8 alive, SW in
   play, star pass, tap the Poisoner → wrong outcome, silently accepted.

3. **P0 · After a star pass (or Scarlet Woman promotion) the Imp step can lose
   its kill panel entirely.**
   `NightOrder` groups holders by `nightRoleId` including dead ones, and
   `QuickResolutions` uses `step.playerIds.firstOrNull()`
   (`NightScreen.kt:467`) then gates on `holder.alive` (`:520`). After a star
   pass there are **two** players with `characterId == "imp"` — the dead original
   and the live heir. If the dead one sits earlier in the circle, `holder` is
   dead, `DemonKillPanel` is never rendered, and the Storyteller has no way to
   resolve the Demon kill from the night sheet on every subsequent night. Repro:
   seat 0 = Imp, seat 3 = Poisoner; star pass to seat 3; advance to night 3;
   open the Imp step → the "Demon kill" picker is gone.

4. **P0 · Nothing is shown to the new Imp; no "does not act tonight" guard.**
   Rules: wake the new Imp, show YOU ARE + the Imp token; they do **not** act
   that night. App: `starPass` does no UI follow-up. The show card exists in
   `night_guide.json` but is an independent chip requiring a manual token pick,
   and the panel gives no cue to use it. Nothing marks that the new Imp is
   dormant tonight.

5. **P1 · A poisoned/drunk Imp can still be star-passed.**
   `NightScreen.kt:548-554` warns "the attack fails (choose 'No kill')", but the
   star-pass branch at 591-622 renders regardless of impairment and has **no**
   "No kill" escape button (the `TextButton("No kill")` only exists in the else
   branch at 634). Repro: poison the Imp, Imp step, tap self → only heir chips,
   no way to say "nothing happens" except deselecting the target.

6. **P1 · `starPass` leaves the heir's old state behind.**
   `GameActions.kt:88-94` keeps the heir's `reminders` and `note`. A Poisoner
   heir keeps nothing marking that they are no longer the Poisoner; a Marionette
   heir keeps "Is the Marionette"; the Scarlet Woman keeps no `Demon` token. No
   `imp:"Dead"` token is placed on the dying Imp either, although the character
   declares one.

7. **P1 · Kills are not recorded as choices; there is no dawn announcement.**
   `GameActions.kill(..., DeathCause.DEMON)` records a `DeathRecord` but there is
   no per-night log of *who the Imp chose*, no "attack failed because X was
   protected" record, and no dawn briefing anywhere in `GameShell.kt`
   (`advancePhase` at `GameActions.kt:258-263` just flips the phase). The
   Storyteller must remember what to announce.

8. **P1 · "Every Demon is dead — good wins" is the default action even when the
   Scarlet Woman will catch it.**
   `WinCheck.kt:70-86` returns `goodWins = true` and `GameExtras.kt`
   `WinAdvisoryDialog` makes "Declare good victory" the primary button; the
   Scarlet Woman caution is small red text. The caution also fires when the SW is
   already dead or when fewer than 5 are alive (`WinCheck.kt:72` only tests
   `"scarletwoman" in inPlayIds`).

9. **P2 · Protection notes ignore whether the protector works.**
   `StatusEffects.kt:64-78` reports "Marked 'Safe' (Monk) — protected from the
   Demon" and "The Soldier is safe from the Demon" without checking whether the
   Monk/Soldier is drunk or poisoned. During the Imp kill this actively misleads
   the Storyteller. (Detailed under `poisoner.md` defect 1.)

10. **P2 · Killing a dead player is not modelled as a legal Imp choice.**
    Selecting a dead chip shows a disabled "`<name>` dies" button
    (`NightScreen.kt:626`). The rules say to let them do it; the panel should
    offer an explicit "nothing happens — they are already dead" acknowledgement
    that still checks the step off and records the choice.

11. **P2 · No Recluse-as-heir affordance.** The Storyteller may make the Recluse
    the new Imp; the app neither offers it distinctly nor labels it.

12. **P3 · Star-pass death cause.** `starPass` uses
    `DeathCause.OTHER_NIGHT_DEATH` (`GameActions.kt:87`). The Imp killed itself
    with its own Demon attack; `DeathCause.DEMON` is the truer record and matters
    to any future "died to the Demon" query (Sage, Choirboy, Godfather).

## Proposed behaviour (spec)

### Night step
- **when:** other nights only; wake condition = holder is **alive** and not
  marked `exorcist:"Chosen"` and not `isImpaired` (if impaired, still wake them
  for the bluff, but the resolution defaults to "no kill").
- **targets:** exactly 1, any player **alive or dead, including self**. Picker
  sorts alive-first, self last, dead greyed but selectable, and annotates each
  chip with any standing protection.
- **immediate effects:**
  - normal target, not protected → `kill(target, DeathCause.DEMON)`, place
    `imp:"Dead"` on them.
  - normal target, protected or Imp impaired → record "attack failed (reason)",
    no death, step still completes.
  - dead target → record the choice, no death, step completes.
  - self → the **star-pass flow** below.
- **star-pass flow (replaces `NightScreen.kt:591-622`):**
  1. If the Imp is impaired: show "The Imp is drunk/poisoned — the attack fails.
     Nobody dies and there is no star pass." with a single "Nothing happens"
     button. Stop.
  2. Compute `heirs = players.filter { alive && !isTraveller && (team == MINION ||
     characterId == "recluse") }`.
  3. If an alive `scarletwoman` is among them **and**
     `players.count { alive && !isTraveller } >= 5`: **force** her. Render a
     single confirm button: "Star pass — Imp dies, `<SW name>` becomes the Imp
     (Scarlet Woman, mandatory at 5+ alive)". Do not offer other heirs.
  4. Else render only `heirs`, Minions first, Recluse last and labelled
     "Recluse — may register as a Minion".
  5. If `heirs` is empty: show "No alive Minion — the Imp dies and good wins
     unless another ability says otherwise", offer the plain kill, and let
     `WinCheck` take over.
  6. On confirm run the new engine action `GameActions.impStarPass` (below), then
     immediately push the full-screen **YOU ARE + Imp token** card
     (`ShowCard.CharacterCard("YOU ARE", "imp")`) with no intermediate picker,
     and place the token `imp:"Is the Imp (tonight only — does not act)"`, which
     expires at dawn.
- **deferred effects:** at dawn, the dawn briefing must include "Announce:
  `<name>` died" for each night death, and "`<heir>` is the Imp from tonight" as
  a Storyteller-only line.
- **expiry:** `imp:"Dead"` never expires. The new "does not act tonight" marker
  expires at dawn (`EXPIRES_AT_DAWN`).
- **information:** none computed. The Imp receives no info after night 1.
- **visibility:** the new Imp is shown YOU ARE + the Imp token that same night.
  Other Minions are told nothing. The Lunatic annotation already appended by
  `NightOrder.kt:157-172` stays.
- **day-time inputs:** none required.

### New engine API
```kotlin
// GameActions.kt — replaces the bare starPass for the Imp path
fun impStarPass(
    state: GameState,
    impPlayerId: Long,
    heirPlayerId: Long,
    lookup: (String) -> Character?,
): GameState
```
Contract:
- returns `state` unchanged if the heir is dead, a Traveller, or neither a
  Minion nor the `recluse`;
- returns `state` unchanged if an alive `scarletwoman` exists,
  `aliveNonTravellers.size >= 5`, and `heirPlayerId` is not her;
- kills the Imp with `DeathCause.DEMON`;
- sets heir `characterId = "imp"`, `shownCharacterId = null`,
  `alignmentFlipped = false`;
- strips the heir's own former-character reminders
  (`reminders.filterNot { it.sourceId == formerCharacterId }`) and clears any
  `"Is the Marionette"` / `"Is the Drunk"` marker on them;
- clears the heir's `note` if it starts with "Believes they are";
- places `imp:"Dead"` on the dead Imp.

Also add a pure helper used by both the UI and `WinCheck`:
```kotlin
fun starPassHeirs(state: GameState, lookup: (String) -> Character?): List<Player>
fun scarletWomanMustCatch(state: GameState, lookup: (String) -> Character?): Boolean
```

### `WinCheck` change
When every Demon is dead, if `scarletWomanMustCatch` is true return
`Advisory(goodWins = null, reason = "The Demon is dead, but the Scarlet Woman
becomes the Demon (5+ alive, Travellers don't count) — the game continues.")`
so the dialog's primary button is not "Declare good victory". Drop the
Scarlet Woman caution when she is dead or fewer than 5 non-Travellers are alive.

### UI text for the step
- Header: "Imp — who did `<name>` choose?"
- Impaired: "Drunk/poisoned — the attack fails. Nobody dies."
- Self, SW forced: "Star pass. The Imp dies and **`<SW>` must become the Imp**
  (Scarlet Woman, 5+ alive). Show her YOU ARE, then the Imp token."
- Self, free choice: "Star pass. The Imp dies. Choose an **alive Minion** to
  become the Imp. They do **not** act again tonight."
- After the pass: "Show `<heir>` the YOU ARE card, then the Imp token."

### Data changes
- `night_guide.json` `imp.other`: change the show card `token` from `"pick"` to
  `"self"` so it renders the Imp token with no manual search, and add a second
  show `{"label":"Nothing happens (dead target)","kind":"message","text":"…"}`.
- No change needed to `characters.json` or the night order.

## Tests to add

1. `Given` an alive Imp, an alive Scarlet Woman and 6 alive non-Travellers,
   `When` `impStarPass(imp, poisonerSeat)` is called,
   `Then` the state is unchanged (SW precedence blocks the pass).
2. Same setup, `When` `impStarPass(imp, scarletWomanSeat)`,
   `Then` the Imp is dead with `DeathCause.DEMON`, the SW's `characterId ==
   "imp"`, and the SW holds no `scarletwoman:*` reminders.
3. `Given` 4 alive non-Travellers plus 3 Travellers and an alive Scarlet Woman,
   `When` `scarletWomanMustCatch` is queried, `Then` it is `false` (Travellers
   don't count).
4. `Given` an Imp and a dead Poisoner, `When` `impStarPass(imp, poisoner)`,
   `Then` the state is unchanged.
5. `Given` an Imp and a Recluse (no Minions alive), `When` `starPassHeirs`,
   `Then` the Recluse is returned.
6. `Given` an Imp and no alive Minion or Recluse, `When` `starPassHeirs`,
   `Then` the list is empty and `WinCheck.check` after killing the Imp advises
   `goodWins = true` with no Scarlet Woman caution.
7. `Given` seat 0 is a dead ex-Imp and seat 3 is the live Imp after a star pass,
   `When` the night sheet is built for night 3, `Then` the `imp` step's
   `playerIds` puts the **alive** holder first (or the step exposes an
   `activeHolderId` that is alive).
8. `Given` an Imp poisoned by the Poisoner, `When` the Imp targets themself,
   `Then` no death and no character change occur.
9. `Given` a star pass on night 3, `When` `advancePhase` runs to dawn,
   `Then` the "does not act tonight" marker on the new Imp is gone.
