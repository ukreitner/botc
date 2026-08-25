# Scarlet Woman (scarletwoman) — Trouble Brewing Minion

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Scarlet_Woman> (fetched
2026-08-25), <https://wiki.bloodontheclocktower.com/Imp>.

Current ability text:

> "If there are 5 or more players alive & the Demon dies, you become the Demon.
> (Travellers don't count.)"

How to run — verbatim and paraphrased from the wiki:

- Trigger condition: *"If there are five or more players alive just before the
  Demon dies—that is, four or more players left alive after the Demon dies"*.
  The count is taken **immediately before** the Demon's death.
- *"Travellers do not count as players when seeing if the Scarlet Woman's ability
  triggers."*
- Works for **any** Demon death: execution during the day, a night death, an Imp
  self-kill, a Slayer shot, a Gossip/Godfather kill.
- Token work: *"replace the Scarlet Woman token with a spare Imp token, changing
  that player's character from the Scarlet Woman into the Imp"*. Mark her with
  the "IS THE DEMON" reminder.
- Being shown: *"That night, wake the new Imp, show them the YOU ARE info token,
  then show them the Imp token."* This happens at the **Scarlet Woman's** position
  in the other-night order (index 29), which is **before** the Imp's kill (index
  37) — so on the night after a daytime Demon execution she is told, and then
  kills, in the same night.
- **Imp self-kill:** *"If five or more players are alive when the Imp kills
  themself at night, the Scarlet Woman **must** become the new Imp."* The
  Storyteller has no discretion here — she takes precedence over every other
  Minion.
- She becomes the **same** Demon that died and gains that Demon's ability
  (multi-Demon scripts).
- She is still the Scarlet Woman while she is impaired: a drunk or poisoned
  Scarlet Woman does **not** become the Demon.

Jinxes (from `night_and_jinxes.json`):
- `scarletwoman` × `lilmonsta`: "If there are 5 or more players alive and the
  player holding the Lil' Monsta token dies, the Scarlet Woman is given the
  Lil' Monsta token tonight."
- `fanggu` × `scarletwoman`: "If the Fang Gu chooses an Outsider and dies, the
  Scarlet Woman does not become the Fang Gu."
- `alhadikhia` × `scarletwoman`: "If there are two living Al-Hadikhias, the
  Scarlet Woman Al-Hadikhia becomes the Scarlet Woman again."
- `plaguedoctor` × `scarletwoman`: "If the Demon dies while the Storyteller has
  the Scarlet Woman ability, a living Minion becomes the Demon."

## What the app does today

Data
- `characters.json` — `scarletwoman`: ability text matches the wiki including
  the Traveller clause. `reminders: ["Demon"]`. `firstNightReminder: ""`,
  `otherNightReminder: "If the Scarlet Woman became the Demon today: Show the
  'You are' card, then the Demon token."`
- `night_and_jinxes.json` — `otherNight[29]`, absent from `firstNight`. Correct.
- `night_guide.json` — `scarletwoman.other` is accurate prose (mentions
  execution or otherwise, 5+ alive, Travellers don't count) plus one show card
  `{"label":"You are the Demon","kind":"token","token":"pick","text":"YOU ARE"}`.
  There is no `.first` entry (correct).

Engine
- `StatusEffects.kt:104-107` — the **only** automation:
  ```kotlin
  if (character?.team == Team.DEMON) {
      if (seats.any { it.characterId == "scarletwoman" && it.alive } && seats.count { it.alive } >= 5) {
          notes += "Scarlet Woman becomes the Demon (5+ alive)."
      }
  ```
  A text note appended to `deathNotes`, nothing more.
- `WinCheck.kt:70-75` — when all Demons are dead, adds the caution "Scarlet
  Woman: with 5+ players alive she becomes the Demon instead", gated only on
  `"scarletwoman" in inPlayIds` (in play at all, alive or dead).

UI
- `NightScreen.kt:602-607` — the Imp star-pass heir list sorts her first and the
  title says "the Scarlet Woman catches it first if able".
- `SeatSheet.kt:240-251` renders `deathNotes` above the kill buttons, so the note
  appears when the Storyteller opens the Demon's seat.
- `NightScreen.kt:588-590` renders `deathNotes` for the chosen Demon-kill target.
- **Nowhere else.** `DayScreen.kt:111-114` (block-banner Execute) and
  `GameShell.kt:598-604` (dusk-guard Execute) call `viewModel.kill(...)` with no
  notes shown at all.

Storyteller experience: executing the Imp on day 3 from the Day tab gives no hint
that the Scarlet Woman exists. The win advisory then pops with "Every Demon is
dead — good wins" as the primary button and the Scarlet Woman line as small red
text. If the Storyteller does remember, they must open her seat, tap "Change
character", find the Imp, assign it — at which point the `scarletwoman` night row
**disappears from the night sheet** (the sheet is built from `characterId`), so
the "wake her and show YOU ARE + Imp token" prompt is gone. If instead they leave
her as the Scarlet Woman, the `imp` row disappears (no living Imp) and there is
no Demon kill panel at all.

## Defects and gaps

1. **P0 · The 5-alive threshold counts Travellers.**
   `StatusEffects.kt:105` uses `seats.count { it.alive }`. The rule is
   *non-Traveller* alive players; `GameState` already exposes
   `aliveNonTravellers` (`GameState.kt:117`). Repro: 4 real players + 2
   Travellers alive, kill the Imp → the app says the Scarlet Woman catches it;
   the rules say good wins.

2. **P0 · Nothing is automated; the promotion is a text note the Storyteller can
   never see on the main execution path.**
   Executing the Demon from the Day tab (`DayScreen.kt:111-114`), from a
   nomination row (`DayScreen.kt:350-357`), or from the dusk guard
   (`GameShell.kt:598-604`) shows **zero** death notes. Repro: nominate and
   execute the Imp on day 2 with 7 alive and a Scarlet Woman in play → the app
   silently records the death and then offers "Declare good victory".

3. **P0 · Promoting her breaks the night sheet either way.**
   The night sheet groups by `characterId`/`nightRoleId` (`NightOrder.kt:46-48`).
   - If the Storyteller changes her character to `imp`, the `scarletwoman` row
     vanishes and with it the "show YOU ARE + Demon token" prompt that the rules
     require **that same night**.
   - If they don't, the `imp` row vanishes (all `imp` holders dead ⇒ the row
     shows but `QuickResolutions` gates the kill panel on `holder.alive`,
     `NightScreen.kt:520`), so she cannot kill.
   There is no state that represents "she is the Demon and must be told tonight".

4. **P0 · She is promoted even when drunk or poisoned.**
   Nothing checks `StatusEffects.isImpaired` before adding the note or before the
   Imp star-pass sorts her first. A poisoned Scarlet Woman does not gain the
   ability; the app never says so.

5. **P1 · Imp star pass does not enforce her precedence.**
   See `imp.md` defect 2. `NightScreen.kt:602-607` merely sorts her first;
   `GameActions.starPass` (`GameActions.kt:79-96`) accepts any heir.

6. **P1 · The `Demon` reminder token is never placed.**
   `characters.json` declares `reminders: ["Demon"]` — the physical "IS THE
   DEMON" marker — but no code path places it. The Storyteller must find it in
   the tray manually, and only while her step is expanded.

7. **P1 · The `scarletwoman` night row fires every single night.**
   `NightOrder.kt:142-148` emits the row whenever a holder exists, with the
   conditional `otherNightReminder` text as its detail. From night 2 onward the
   Storyteller sees and must check off a Scarlet Woman step that almost always
   does nothing. It should be suppressed unless the promotion actually fired
   today.

8. **P1 · `WinCheck` caution is wrong in both directions.**
   `WinCheck.kt:72` fires on `"scarletwoman" in inPlayIds` regardless of whether
   she is alive or whether 5+ non-Travellers live, and the advisory still
   defaults `goodWins = true` so "Declare good victory" is the primary button
   (`GameExtras.kt` `WinAdvisoryDialog`).

9. **P2 · Multi-Demon scripts and the four jinxes are unhandled.**
   She should become *the Demon that died*, not a hard-coded Imp; the Fang Gu,
   Al-Hadikhia, Lil' Monsta and Plague Doctor jinxes have no code. Jinx text is
   surfaced only as a static list in `SeatSheet.kt:222-235`.

10. **P3 · The `night_guide.json` show card uses `token:"pick"`.**
    The Demon token is knowable from the grimoire; forcing a search box at the
    table is friction.

## Proposed behaviour (spec)

### Trigger (engine, automatic on every Demon death)

Add to `GameActions.kill` a post-step, or better a pure detector used by both
`kill` and the UI:

```kotlin
// StatusEffects.kt
data class ScarletWomanCatch(val scarletWomanId: Long, val demonCharacterId: String)

fun scarletWomanCatch(
    state: GameState,           // state BEFORE the demon dies
    lookup: (String) -> Character?,
    dyingPlayerId: Long,
): ScarletWomanCatch?
```
Returns non-null iff, evaluated on the pre-death state:
- the dying player's character `team == DEMON`;
- some player has `characterId == "scarletwoman"`, is `alive`, and
  `!isImpaired(...)`;
- `state.players.count { it.alive && !it.isTraveller } >= 5`;
- the dying Demon is not a `fanggu` that jumped to an Outsider (jinx).

### Immediate effects when it fires

`GameActions.scarletWomanBecomesDemon(state, catch)`:
- sets the Scarlet Woman's `characterId` to `catch.demonCharacterId`
  (`"imp"` in Trouble Brewing), `shownCharacterId = null`,
  `alignmentFlipped = false`;
- places `scarletwoman:"Demon"` on her (this is the marker the night sheet keys
  off), and it **never expires**;
- places `scarletwoman:"Told"` at the moment she is shown her token — see below.

The promotion happens the instant the Demon dies (day or night), exactly as the
physical game does.

### Night step (rebuilt)

- **when:** other nights, and only if the holder carries
  `scarletwoman:"Demon"` without `scarletwoman:"Told"`. `NightOrder` must
  therefore key the `scarletwoman` row on the **reminder**, not on
  `characterId` — after the promotion her `characterId` is `imp`, so the row must
  be emitted for "a player who holds `scarletwoman:"Demon"` and has not been
  told". Suggested implementation: `NightOrder.build` gains a special case for
  `"scarletwoman"` mirroring the existing `"marionette"` special case at
  `NightOrder.kt:121-141`.
- **targets:** none.
- **immediate effects:** one button, "Show `<name>` YOU ARE + the `<Demon>`
  token", which pushes `ShowCard.CharacterCard("YOU ARE", demonCharacterId)`
  full-screen and then places `scarletwoman:"Told"`.
- **deferred effects:** because she is now the Demon and the Demon row sits later
  in the order (imp at 37 vs scarletwoman at 29), she kills normally the same
  night when the Demon died during the previous **day**. When the Demon died
  **that same night** by an Imp star pass, the new Demon does not act again that
  night (see `imp.md`) — the Demon row must skip her kill panel and say so.
- **expiry:** `scarletwoman:"Demon"` and `"Told"` never expire.
- **visibility:** she alone is shown the YOU ARE + Demon token. Nothing is shown
  to other Minions.

### Day / execution surfacing

- `StatusEffects.deathNotes` must be rendered on **every** kill path, not just
  the seat sheet. Concretely: `DayScreen.kt:111-114`, `DayScreen.kt:350-357` and
  `GameShell.kt:598-604` must route through a shared confirm sheet that lists
  `deathNotes(state, lookup, id)` before recording the death.
- The Scarlet Woman note must be exact and count correctly:
  `"Scarlet Woman: <name> becomes the <Demon> (N non-Traveller players alive)."`
  and, when she is impaired, `"Scarlet Woman <name> is poisoned/drunk — she does
  NOT become the Demon."`
- At **day start** after a night promotion, and at the moment of a daytime
  promotion, the briefing must say (Storyteller-only): "`<name>` is now the
  `<Demon>`. Wake her tonight at the Scarlet Woman step to show YOU ARE + the
  token."

### `WinCheck` change

```
if (demons.isNotEmpty() && aliveDemons.isEmpty()) {
    if (scarletWomanWouldCatch) return Advisory(
        goodWins = null,
        reason = "The Demon is dead, but the Scarlet Woman becomes the Demon " +
                 "(5+ non-Traveller players alive) — the game continues.",
    )
    ...
}
```
with the caution dropped when she is dead, impaired, or the count is below 5.
If the promotion is automated, `aliveDemons` will already be non-empty and the
advisory simply will not fire — which is the correct end state.

### UI text
- Death confirm: "`<Demon name>` dies. **Scarlet Woman `<name>` becomes the
  `<Demon>`** — 6 non-Traveller players are alive."
- Night row title: "Scarlet Woman — she became the Demon"
  detail: "Wake `<name>`. Show the YOU ARE card, then the `<Demon>` token. Put
  her back to sleep. She kills later tonight." (or "She does not act again
  tonight." after a star pass).

### Data changes
- `night_guide.json` `scarletwoman.other`: set the show card
  `"token": "self"` is wrong (that would show the Scarlet Woman token); instead
  keep `"pick"` but have the UI pre-select the dead Demon's character id.
- No `characters.json` or night-order changes.

## Tests to add

1. `Given` 5 alive non-Travellers including an alive Scarlet Woman, `When` the
   Imp is executed, `Then` `scarletWomanCatch` is non-null and after
   `scarletWomanBecomesDemon` her `characterId == "imp"` and she holds
   `scarletwoman:"Demon"`.
2. `Given` 4 alive non-Travellers plus 3 alive Travellers and an alive Scarlet
   Woman, `When` the Imp dies, `Then` `scarletWomanCatch` is `null`.
3. `Given` an alive Scarlet Woman poisoned by the Poisoner, `When` the Imp dies
   with 8 alive, `Then` `scarletWomanCatch` is `null` and `deathNotes` contains
   "does NOT become the Demon".
4. `Given` a dead Scarlet Woman, `When` the Imp dies with 8 alive, `Then`
   `WinCheck.check` advises good wins with **no** Scarlet Woman caution.
5. `Given` the Scarlet Woman has been promoted and holds
   `scarletwoman:"Demon"` but not `"Told"`, `When` the other-night sheet is
   built, `Then` a `scarletwoman` step exists with her as the holder **and** an
   `imp` step exists with her as the holder.
6. `Given` she has been shown her token (`"Told"` placed), `When` the next
   other-night sheet is built, `Then` no `scarletwoman` step is emitted.
7. `Given` an alive Scarlet Woman and 6 alive, `When` `GameActions.starPass` is
   called with a non-Scarlet-Woman Minion heir, `Then` the state is unchanged.
8. `Given` a Fang Gu that jumped to an Outsider and then died, `When`
   `scarletWomanCatch` runs, `Then` it is `null` (jinx).
