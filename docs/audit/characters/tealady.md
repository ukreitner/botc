# Tea Lady (tealady) — Bad Moon Rising Townsfolk

## Official rules (sources)

Sources:
- https://wiki.bloodontheclocktower.com/Tea_Lady
- https://wiki.bloodontheclocktower.com/Glossary ("Alive neighbours", "Dead", "Execution")
- https://wiki.bloodontheclocktower.com/Assassin (bypass)

**Current ability text (matches `characters.json`):**

> "If both your alive neighbours are good, they can't die."

**Alive neighbours (wiki, quoted):**

> "the two alive players closest to the Tea Lady—one clockwise and one counterclockwise. Skip past
> any dead neighbours."

**Glossary, "Alive neighbours" (quoted):**

> "The two alive players that are sitting closest—one clockwise, one counterclockwise—to the player
> in question, not including any dead players sitting between them."

**How to run (wiki, quoted):**

> "If both alive neighbours of the Tea Lady are good, mark those neighbours' character tokens with
> the Tea Lady's **CANNOT DIE** reminders. If either alive neighbour of the Tea Lady is evil, remove
> these reminders. Update these reminders immediately based on this condition throughout the entire
> game."
>
> "If a player marked **CANNOT DIE** is executed, declare that the marked player is executed but
> remains alive. (_Do not say why._)"

**Key mechanics (wiki):**

- The protection covers death **"whether by demon, godfather, gossip, or execution"** — i.e. every
  cause, day or night.
- **"The Assassin is the sole exception capable of bypassing this protection."**
- "Protection only activates when both alive neighbours are good. If either neighbour is evil,
  protection ceases immediately."
- The condition is **continuously re-evaluated** — every death, seat change, alignment change,
  character change or Traveller arrival can turn it on or off *mid-phase*, "immediately".

Derived rules that matter:

- **Two CANNOT DIE tokens** (the wiki says "reminders", plural — one per neighbour). Physical token
  count: 2.
- **The Tea Lady must be alive** (dead players lose their ability; the Glossary adds "any persistent
  effects of their ability immediately end", so the tokens come off the instant the Tea Lady dies).
- **The Tea Lady must be sober** — drunk or poisoned ⇒ no protection, and the ST should keep or
  remove the tokens as they prefer while knowing they do nothing.
- **The Tea Lady does not protect herself.**
- **Misregistration:** a **Recluse** neighbour "might register as evil… even if dead" — if the ST
  chooses that registration, the protection switches off. A **Spy** neighbour "might register as
  good… even if dead" — the ST may keep the protection on even though a Minion is sitting there.
  Both are Storyteller choices that must be *offered*, not assumed.
- **Travellers** sit in the circle and count as neighbours; an evil Traveller neighbour turns the
  protection off.
- **Jinxes:** none for the Tea Lady in the wiki or in the app's data.

## What the app does today

- `characters.json` — ability text correct; `"reminders": ["Can not die"]` — **one** token label
  (see defect 3). No night reminders (correct: the Tea Lady never wakes).
- `night_and_jinxes.json` — absent from both night orders (correct) and from the jinx list (correct).
- `night_guide.json` — **no entry** (`null`); the character has no run-book anywhere in the app.
- `StatusEffects.kt:69` — a manually placed token yields a note:
  ```kotlin
  "can not die" -> notes += "Tea Lady: can't die."
  ```
- `StatusEffects.kt:79-91` — the only derived logic:
  ```kotlin
  val index = seats.indexOfFirst { it.id == playerId }
  if (index >= 0) {
      val teaLadies = seats.filter { it.characterId == "tealady" && it.alive }
      for (tea in teaLadies) {
          val ti = seats.indexOfFirst { it.id == tea.id }
          val neighbours = listOf((ti - 1 + seats.size) % seats.size, (ti + 1) % seats.size)
          if (index in neighbours && seats[neighbours[0]].let { !it.isEvil(lookup) } &&
              seats[neighbours[1]].let { !it.isEvil(lookup) }
          ) {
              notes += "Tea Lady neighbour with both neighbours good — can't die."
          }
      }
  }
  ```
- `SeatSheet.kt:256-268` — both note strings ("can not die", "can't die") match the protection
  filter, so killing a neighbour **from their seat** raises the confirmation dialog. **Works, when
  the ST uses that path.**
- `DemonKillPanel` (`NightScreen.kt:588-590`) prints the note; the kill button stays enabled.
- `DayScreen.kt:111-114` / `:350-357` / `GameShell.kt:599-604` — execution ignores the note entirely.

Storyteller experience: nothing in the app tracks the Tea Lady. The tokens must be placed by hand
from the seat sheet, and must be moved by hand every time anyone dies — which is precisely the
bookkeeping the app should own. The one derived check that exists is wrong at seat level.

## Defects and gaps

1. **P0 · The neighbour calculation uses raw seat adjacency instead of alive neighbours.**
   Rules: "the two **alive** players closest… skip past any dead neighbours". App:
   `StatusEffects.kt:84` computes `(ti-1)` and `(ti+1)` with no alive filter.
   Two distinct wrong outcomes:
   - **False negative** — Tea Lady at seat 5, seat 4 is a *dead* Townsfolk, seat 3 is an alive good
     player. Seat 3 is the real alive neighbour and is protected; the app never mentions it, so the
     ST kills them.
   - **False positive / wrong toggle** — Tea Lady at seat 5, seat 6 is a *dead Minion*, seat 7 is an
     alive good player. Rules: the dead Minion is skipped, seat 7 is the alive neighbour, both alive
     neighbours are good ⇒ protection **on**. The app reads seat 6 as an evil neighbour and turns
     the protection **off** for seat 4 as well.
   Repro: any BMR game past night 1, once a seat next to the Tea Lady has died.

2. **P0 · A drunk or poisoned Tea Lady still protects.**
   Rules: an impaired player has no ability. App: `StatusEffects.kt:81` filters only on
   `it.alive`. Repro: poison the Tea Lady, open a neighbour's seat sheet — "can't die" is still
   offered and the dialog's dismissive default is **"Death prevented"** (`SeatSheet.kt:303-305`).

3. **P1 · Only one CANNOT DIE token exists in the data.**
   Rules: two neighbours, two reminders. App: `"reminders": ["Can not die"]`, so the night-tray
   placement path (`NightScreen.kt:319-321` → `placeExclusiveReminder`) can only ever hold one, and
   the seat-sheet path (`SeatSheet.kt:109-117` → plain `addReminder`) has no idea two is the maximum.
   Fix is one line of data: `"reminders": ["Can not die", "Can not die"]`.

4. **P1 · Nothing places, moves or removes the tokens automatically.**
   Rules: "Update these reminders **immediately** based on this condition throughout the entire
   game." App: 100% manual, and the condition changes on every death — the busiest moment of the
   ST's night. This is the exact "the app should do the bookkeeping" complaint from the brief.

5. **P1 · Executions ignore the protection entirely.**
   Rules: "If a player marked CANNOT DIE is executed, declare that the marked player is executed but
   remains alive. (Do not say why.)" App: the Day tab's Execute buttons kill outright
   (`DayScreen.kt:111-114`, `:350-357`, `GameShell.kt:599-604`). Repro: mark a neighbour, execute
   them from the Day tab — they die.

6. **P1 · The Demon kill panel offers the kill anyway** (`NightScreen.kt:625-633`), same as for the
   Innkeeper and Sailor.

7. **P2 · The Assassin bypass is not expressible.**
   `deathNotes` returns plain strings and `SeatSheet.kt:258-262` substring-matches them, so there is
   no way to say "this protection is real, except against the Assassin".

8. **P2 · No misregistration prompt for a Recluse or Spy neighbour.**
   `Player.isEvil(lookup)` (`GameState.kt:49-52`) returns a hard boolean; the ST is never offered
   the Recluse/Spy choice that decides whether the protection is on.

9. **P2 · The protection status is invisible in the grimoire.**
   `GrimoireScreen` draws the circle but nothing indicates "the Tea Lady's protection is currently
   ON, covering seats 4 and 6" — the single most useful thing to see at a glance for this character.

10. **P2 · No night guide entry**, so the rule "skip past dead neighbours" — the thing STs get wrong
    at the table — is stated nowhere in the app.

11. **P3 · Two-player edge case.** With a two-seat circle, `(ti-1)` and `(ti+1)` are the same seat;
    the alive-neighbour version must dedupe.

## Proposed behaviour (spec)

The Tea Lady has **no night step**. It is a continuously derived, continuously re-marked positional
protection — architecturally the same shape as `StatusEffects.derivedPoison` (the No Dashii,
`StatusEffects.kt:14-33`), and should be built the same way.

### Derived state

```kotlin
/** Nearest alive player in [dir] from [index], skipping the dead; null if none. */
private fun aliveNeighbour(seats: List<Player>, index: Int, dir: Int): Player?

/**
 * Seats currently protected by an alive, sober Tea Lady, with the reason.
 * Mirrors derivedPoison so the UI can render both the same way.
 */
fun derivedProtection(state: GameState, lookup: (String) -> Character?): Map<Long, String>
```

Algorithm, per alive Tea Lady `T`:
1. If `StatusEffects.isImpaired(state, lookup, T)` → contributes nothing.
2. `cw = aliveNeighbour(seats, ti, +1)`, `ccw = aliveNeighbour(seats, ti, -1)`; if either is null
   (or `cw == ccw` in a degenerate circle) → nothing.
3. `registersEvil(p)` = `p.isEvil(lookup)`, **overridable per seat** by an ST toggle for Recluse
   (default: registers as its true alignment, i.e. good) and Spy (default: registers evil).
4. If `!registersEvil(cw) && !registersEvil(ccw)` → both `cw.id` and `ccw.id` map to
   *"Protected by the Tea Lady ({T.name}'s alive neighbours are both good)"*.

`isImpaired` is unaffected; this is a protection map, not a status effect.

### Automatic token marking

Add `GameActions.syncDerivedTokens(state, lookup)`, called at the end of **every** mutation that can
change the picture — `kill`, `revive`, `resurrect`, `assignCharacter`, `flipAlignment`, `addSeat`,
`removeSeat`, `moveSeat`, `swapCharacters`, `snakeCharmerSwap`, `starPass`, `addReminder`,
`removeReminder`, `advancePhase`:

- remove every `("tealady","Can not die")` token from seats not in `derivedProtection`;
- add one to every seat in `derivedProtection` that lacks it.

Because the tokens then always agree with the derived map, `StatusEffects.kt:69`'s reminder-driven
note keeps working and the grimoire shows the truth without the ST touching anything. Any manual
placement the ST makes is overwritten on the next sync — which is correct for a fully derived
condition; if a manual override is wanted, key it off a separate generic `("","Protected")` token.

### Protection semantics

In the `DeathNote` model proposed in innkeeper.md / pacifist.md:

- `("tealady","Can not die")` ⇒ `BLOCKS` for **every** `DeathCause`: `DEMON`, `OTHER_NIGHT_DEATH`,
  `EXECUTION`, `STORYTELLER`.
- Downgraded to `IGNORED_BY_ASSASSIN` when the death's source is the Assassin.
- For `EXECUTION`, feeds the shared execution dialog as a **forced** "Executed, but survives",
  attributed to `"tealady"` — not the optional Pacifist offer.

### Storyteller-facing surfaces

- **Grimoire:** draw the two protected seats with a shield glyph and a hairline arc back to the Tea
  Lady, plus a one-line status chip: **"Tea Lady protection: ON — {cw} and {ccw}"** or
  **"Tea Lady protection: OFF — {name} (evil) is an alive neighbour"** or
  **"Tea Lady protection: OFF — the Tea Lady is drunk/poisoned"** / **"…is dead"**.
- **On every state change that flips it**, a transient line in the dawn/day briefing:
  **"Tea Lady protection turned ON — {cw} and {ccw} can no longer die."** /
  **"Tea Lady protection turned OFF — {name} is now an alive neighbour."**
- **Recluse/Spy neighbour**, a persistent choice chip on that seat:
  **"{name} is a Recluse next to the Tea Lady — register them as good (protection ON) or evil
  (protection OFF)?"**
- **Death/execution dialogs:** **"{name} is a Tea Lady neighbour — they can't die. Declare
  'executed, but remains alive'. Do not say why."**, and when the Assassin is the source,
  **"…but the Assassin kills even if they could not."**

### Data changes

- `characters.json` → tealady: `"reminders": ["Can not die", "Can not die"]`.
- `night_guide.json` → add a `tealady` entry (day/standing section) carrying the wiki How-to-Run
  text verbatim, especially "skip past any dead neighbours" and "update these reminders immediately".
- `night_and_jinxes.json` → no change.

## Tests to add

1. `alive neighbours skip dead seats`  ← **fails today**
   Given seats [TeaLady, deadTownsfolk, aliveTownsfolk, …, aliveTownsfolk]. Then
   `derivedProtection` protects the *alive* Townsfolk two seats away, not the dead one.

2. `a dead evil seat between the tea lady and a good player does not break protection`  ← **fails today**
   Given seats [aliveGood, TeaLady, deadMinion, aliveGood]. Then both alive neighbours are good and
   both are protected. (`StatusEffects.kt:84` reads the dead Minion and reports no protection.)

3. `an alive evil neighbour switches protection off`
   Given seats [aliveGood, TeaLady, aliveMinion]. Then `derivedProtection` is empty and no
   `("tealady","Can not die")` token remains anywhere.

4. `a drunk tea lady protects nobody`  ← **fails today**
   Given the Tea Lady holds `("poisoner","Poisoned")` with two good alive neighbours. Then
   `derivedProtection` is empty.

5. `a dead tea lady protects nobody and her tokens are removed`
   Given a protecting Tea Lady. When she is killed. Then no `("tealady","Can not die")` token
   remains on any seat.

6. `protection toggles the moment a neighbour dies`
   Given [goodA, TeaLady, evilB, goodC] — protection off. When `evilB` dies. Then `goodC` becomes an
   alive neighbour, both alive neighbours are good, and `goodA` and `goodC` both gain the token
   without any manual placement.

7. `the tea lady does not protect herself`
   `derivedProtection` never contains the Tea Lady's own id.

8. `a protected player survives an execution`
   Given a marked neighbour on the block. When the execution resolves. Then
   `ExecutionRecord(day, id, SURVIVED, preventedBy = "tealady")`, the player is alive, no death
   record is created.

9. `a protected player blocks the demon kill`
   `deathNotes(..., DeathCause.DEMON)` contains a `BLOCKS` note for a marked neighbour.

10. `the assassin kills through tea lady protection`
    `deathNotes(..., source = "assassin")` returns `IGNORED_BY_ASSASSIN` and the kill proceeds.

11. `a spy neighbour registering as good keeps protection on`
    Given [goodA, TeaLady, Spy] with the ST override "Spy registers good". Then both seats are
    protected.

12. `two protected neighbours hold one token each`
    Exactly two `("tealady","Can not die")` tokens exist in the grimoire when protection is on
    (fails today via the tray's exclusive placement path).

13. `two-seat circle does not double count`
    A 2-player circle produces no protection rather than protecting the same seat twice.
