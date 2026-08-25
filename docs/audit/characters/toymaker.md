# Toymaker (toymaker) — Fabled

## Official rules (sources)

Sources (fetched 2026-08-25):
<https://wiki.bloodontheclocktower.com/Toymaker> and the raw wikitext via
`https://wiki.bloodontheclocktower.com/api.php?action=parse&page=Toymaker&prop=wikitext`,
plus <https://wiki.bloodontheclocktower.com/Fabled>.

Current ability text (matches `characters.json`):

> "The Demon may choose not to attack & must do this at least once per game. Evil players
> get normal starting info."

How to Run (quoted from the wikitext):

> - "Declare the Toymaker is in play at game start
> - Add Toymaker token to Grimoire; mark Demon with **FINAL NIGHT: NO ATTACK** reminder
> - Resolve 'Minion info' and 'Demon info' steps on first night despite fewer than seven
>   players
> - Each night the Demon wakes, they may refuse to attack by shaking their head no
>   (removes reminder if chosen)
> - If Demon's attack would end the game but they're marked **FINAL NIGHT: NO ATTACK**,
>   the Demon does not wake or act"

Examples (wiki):

> 1. "Second night (5 players alive): Imp chooses not to attack. Third night (4 players):
>    kills a player."
> 2. "Second night: Imp kills a player. Third night (3 players): Imp cannot attack — final
>    night restriction applies."

Rules that matter for storytelling:

- **Two distinct effects.** (a) The Demon gets a voluntary "no attack" option every night,
  which it must use at least once; (b) evil starting info (Minion info / Demon info) is
  given even under 7 players. The Toymaker exists mainly for 5–6 player Teensyville games,
  where those steps are normally skipped.
- **The reminder is the ledger.** FINAL NIGHT: NO ATTACK sits on the Demon from setup and
  is removed the first time the Demon declines to attack. While it is still there, the
  obligation is unspent.
- **"A night when a Demon attack could end the game"** means a night on which killing one
  more player would satisfy the evil win condition (in practice: 3 alive going into the
  night, so a kill leaves 2 alive with the Demon among them). On such a night, if the
  reminder is still present, the Demon **is not woken at all** — the skip is forced and
  consumed then.
- Fabled general rules (<https://wiki.bloodontheclocktower.com/Fabled>): cannot be killed,
  immune to death/drunkenness/poison, and **does not count as a player for the "two
  players remain alive" evil victory condition**. That matters directly here: the
  "could end the game" test counts real players, not the Fabled token.
- The wiki lists **no jinxes** for the Toymaker.

Night order: the app has `toymaker` in `otherNight` at index 34, between `lycanthrope` and
`princess`/`legion`/`imp` — i.e. immediately before the Demons, which is where a
"the Demon may decline / may not be woken" instruction belongs. I could **not** verify the
canonical sheet: <https://wiki.bloodontheclocktower.com/Night_Order> returns 404. The app
has **no first-night entry** for the Toymaker; the first-night effect (run the info steps
under 7 players) is currently invisible to the ST — see D1.

## What the app does today

Data:

- `engine/src/main/resources/botc/data/characters.json:2328` — ability matches the wiki;
  `otherNightReminder`: *"If it is a night when a Demon attack could end the game, and the
  Demon is marked \"Final night: No Attack,\" then the Demon does not act tonight. (Do not
  wake them.)"*; `firstNightReminder`: `""`; `reminders: ["Final Night: No Attack"]`.
- `night_and_jinxes.json:407` — `toymaker` in `otherNight` only, at index 34.
- `night_guide.json:201` — an `other` entry with correct prose covering both the voluntary
  skip and the forced final-night skip. `shows: []`.

Engine:

- `NightOrder.kt:49` + `:144-145` — the Toymaker produces an other-nights step with no
  holders because it is in `state.fabledIds`. **Works.**
- `NightOrder.kt:51` — **`val infoSteps = state.players.count { !it.isTraveller } >= 7`.**
  This is the *only* gate on `MINION_INFO` and `DEMON_INFO` (`:59`, `:80`) and it does not
  look at `fabledIds`. With a Toymaker active in a 6-player game the app silently omits
  both steps — the Demon never learns their Minion and never gets bluffs.
- `NightOrder.kt:120-137` — the sub-7 Marionette fallback (`isFirstNight && !infoSteps`)
  is written to compensate for exactly that gate, and would need to yield to the Toymaker.
- `GameActions.kt:217-241` — neither `EXPIRES_AT_DAWN` nor `EXPIRES_AT_DUSK` contains the
  Toymaker token, so `("toymaker","Final Night: No Attack")` persists correctly once
  placed. **Works.**
- `WinCheck.kt:93-104` — the "only N players live and the Demon is among them" advisory
  exists, but nothing derives the *pre-emptive* "an attack tonight would end the game"
  condition the Toymaker needs.

UI:

- `FabledSheet` (`GameExtras.kt:145-198`) toggles `toymaker` on. Nothing places the
  FINAL NIGHT: NO ATTACK reminder — the ST must do it by hand.
- The Toymaker's other-nights step renders the `characters.json` `otherNightReminder` plus
  the `night_guide` prose (`NightScreen.kt:781-800`). `QuickResolutions`
  (`NightScreen.kt:466`) bails out immediately (`step.playerIds` is empty), so the step is
  read-only text.
- `NightToolTray` (`NightScreen.kt:283-306`) does offer the **Final Night: No Attack**
  chip on that step (because `activeCharacter` is resolved from the whole dataset,
  `NightScreen.kt:98`), so the ST *can* place and later remove it manually.
- `DemonKillPanel` (`NightScreen.kt:534-630`) always renders "Demon kill — who did <name>
  choose?" and a **"No kill"** `TextButton` (`NightScreen.kt:626`) that merely clears the
  local `targetId` state. It records nothing, removes no token, and marks no obligation as
  spent. There is no way to tell the app "the Demon declined tonight".
- Nothing suppresses the Demon step on a forced-skip night; the app will happily offer the
  game-ending kill.

Storyteller's actual experience today: turn the Toymaker on, hand-place a token on the
Demon, and then — in a 5–6 player game — discover mid-first-night that the app never
offered Minion info or Demon info and improvise. Every subsequent night you must yourself
work out whether tonight is the "could end the game" night, and manually remove the token
the first time the Demon shakes their head.

## Defects and gaps

1. **P0** · Minion info / Demon info are skipped under 7 players even with the Toymaker ·
   `NightOrder.kt:51` gates on seat count alone. The Toymaker's entire second clause
   ("Evil players get normal starting info") is unimplemented, and this is the Fabled's
   headline use case (Teensyville). Repro: 6-player game, activate Toymaker, go to
   First Night — there is no "Minion info" and no "Demon info" row, so the Demon is never
   shown their Minion or three bluffs.
2. **P0** · The forced final-night skip is never enforced or even detected · The rules say
   *do not wake the Demon* on a night where an attack would end the game while the token
   is still on them. `NightOrder.kt` builds the Demon step unconditionally and
   `DemonKillPanel` offers the kill. Repro: Toymaker active, token still on the Imp, 3
   players alive at dusk → next night the app offers the Imp a kill that ends the game.
3. **P1** · "The Demon declined tonight" is not a recordable action · `NightScreen.kt:626`
   "No kill" is pure local UI state. It does not remove the
   `("toymaker","Final Night: No Attack")` token, does not log the night, and does not
   feed the "has the obligation been spent?" question. The ST must remember to strip the
   token by hand via the tray.
4. **P1** · The FINAL NIGHT: NO ATTACK token is not placed automatically · The app knows
   the Fabled is active and knows who the Demon is (`Team.DEMON` holders); placing the
   token at setup is derivable. Today it is a manual tray action, and if the ST forgets,
   defect 2 and 3 both become invisible.
5. **P1** · The Demon step never says the option exists · The Imp/Pukka/etc. step text is
   the plain demon prompt; the Toymaker's "may choose not to attack" is on a *separate*
   row above it that the ST may have already ticked off. The option should be annotated
   onto the Demon's own step, exactly as `NightOrder.kt:154-158` already does for the
   Exorcist and `:161-176` for the Lunatic.
6. **P2** · The token survives a Demon change · Imp star-pass (`GameActions.kt:78-95`) and
   Fang Gu jump move the Demon to a new seat but do not carry
   `("toymaker","Final Night: No Attack")` with it — the obligation is attached to the
   *Demon*, not the player. Repro: place the token on the Imp, star-pass to a Minion; the
   token stays on the corpse.
7. **P2** · No first-night Toymaker row · Even a text-only first-night step
   ("Toymaker: run Minion info and Demon info even though there are fewer than 7 players;
   mark the Demon FINAL NIGHT: NO ATTACK") would make the rule visible. Absent from
   `night_and_jinxes.json`'s `firstNight`.
8. **P3** · Reminder label casing drift · `characters.json:2328` declares the token as
   `"Final Night: No Attack"` while the same file's `otherNightReminder` prose writes
   *"Final night: No Attack"*. Any future string matching must normalise case.

## Proposed behaviour (spec)

Configuration:

- `fabledConfig["toymaker"] = Toymaker(skipUsed: Boolean = false)`. The authoritative
  ledger stays the reminder token on the Demon; `skipUsed` is the derived convenience
  (`skipUsed == demonSeat.reminders.none { it.sourceId == "toymaker" }`).

Setup / first night:

- **when:** at the moment the Toymaker is activated, and again at SETUP → NIGHT.
- **immediate effects:** place `PlacedReminder("toymaker","Final Night: No Attack")`
  exclusively on every seat whose character is `Team.DEMON`. Re-run this whenever the
  Demon changes seat (`starPass`, `swapCharacters`, `assignCharacter`) while the token is
  unspent, so the obligation follows the Demon.
- **info steps:** change `NightOrder.kt:51` to
  `val infoSteps = state.players.count { !it.isTraveller } >= 7 ||
   "toymaker" in state.fabledIds` (normalise ids the same way `GameData` does). The
  sub-7 Marionette fallback at `NightOrder.kt:120` then correctly stops firing, because
  the Marionette is pointed out inside the Demon info step (`NightOrder.kt:99-103`).
- Add a first-night step row for `toymaker` (before `MINION_INFO`) whose text is
  *"Toymaker: evil gets normal starting info even under 7 players. Mark the Demon
  'Final Night: No Attack'."* with a one-tap "place the token" button.

Other nights:

- **when:** every night after the first, while `"toymaker" in fabledIds`.
- **the forced-skip predicate** (put it in the engine, not the UI):
  ```
  fun demonAttackCouldEndGame(state, lookup): Boolean {
      val alive = state.players.filter { it.alive && !it.isTraveller }
      val aliveDemons = alive.filter { lookup(it.characterId).team == DEMON }
      return aliveDemons.isNotEmpty() && alive.size - 1 <= 2
  }
  ```
  Travellers and Fabled are excluded (per the Fabled page). Where a script's win
  condition differs (Mayor at 3 alive, Leviathan, Riot), treat this as advisory and say so
  in the step text rather than hard-blocking.
- **when the predicate is true and the token is still on the Demon:**
  - Suppress the Demon's own night step entirely (mirroring the Exorcist annotation
    mechanism in `NightOrder.kt:151-158`, but as a *replacement* row):
    `"<Demon> does NOT act tonight — the Toymaker's obligatory no-attack night is forced."`
  - Auto-remove the token when the ST ticks that step done, and log
    "Toymaker: forced no-attack night <N>".
- **when the predicate is false:** annotate the Demon's own step with
  `" — Toymaker: the Demon may shake their head for NO ATTACK tonight (obligation unspent)."`
  and add a first-class button in `DemonKillPanel`:
  **"Demon declined — no attack tonight"**, which removes the Toymaker token, records a
  log entry, and marks the step done. When the obligation is already spent, the button
  still exists (the option remains available every night) but no token is removed and the
  annotation reads `"(obligation already used)"`.
- **"No kill" must stop being a no-op**: rename it to "Clear selection" and give the
  deliberate outcome its own button as above.

Deferred effects / dawn: on a night where the Demon declined, the dawn briefing should say
*"No Demon attack tonight (Toymaker)."* so the ST does not misremember it as a failed kill.

Expiry: never — the token is removed only by being spent. Do **not** add it to
`EXPIRES_AT_DAWN`/`EXPIRES_AT_DUSK`.

Information: none computed. Visibility: the Demon is told, in the ordinary way, that they
may decline; the wiki suggests a head-shake as the signal. Minions are not told anything
extra beyond receiving normal starting info.

Day-time inputs: none.

Interactions/jinxes to handle explicitly:

- **Star pass / Fang Gu jump / Demon character change** — move the unspent token with the
  Demon (D6).
- **Exorcist** — an Exorcised Demon does not act at all; that already annotates the Demon
  step (`NightOrder.kt:151-158`). An Exorcised night must **not** consume the Toymaker
  obligation (the Demon did not *choose* to skip), and the forced-skip predicate should
  not double-suppress.
- **Multiple Demons** (Legion, Kazali/Boffin setups, Lil' Monsta): place the token on each
  Demon seat but treat the obligation as one per Demon; flag as an open question in the
  step text rather than guessing.
- **Lunatic** — the Lunatic also "attacks" at night; a Lunatic's fake no-attack must not
  clear the real Demon's token. Key the button off the *real* Demon step only.
- **Teensyville / <7 players** — this is the main configuration; make sure the Snitch and
  other minion-info riders behave once `infoSteps` becomes true.

UI text the step should display:

- First night: `Toymaker — evil get normal starting info even with fewer than 7 players.
  Mark the Demon "Final Night: No Attack".`
- Other nights, unspent + safe: `The Demon may shake their head for no attack. They must
  do this at least once — the token is still on them.`
- Other nights, forced: `Do NOT wake <Demon> tonight. A kill would end the game and their
  obligatory no-attack night is unspent.`
- Other nights, spent: `The Demon may still decline to attack; the obligation is already
  used.`

Data changes:

- `night_and_jinxes.json` — add `"toymaker"` to `firstNight` immediately before
  `"MINION_INFO"`.
- `characters.json:2328` — add a `firstNightReminder`:
  *"Mark the Demon \"Final Night: No Attack\". Run Minion info and Demon info even with
  fewer than 7 players."* and normalise the prose's token casing to
  *"Final Night: No Attack"*.
- `night_guide.json` — add a `first` entry mirroring the above; keep the existing `other`
  entry (it is accurate).

## Tests to add

1. **Given** a 6-player game with `fabledIds = ["toymaker"]`,
   **when** `nightOrder.firstNight(...)` is built,
   **then** the step list contains `MINION_INFO` and `DEMON_INFO`.
2. **Given** the same 6-player game with `fabledIds = emptyList()`,
   **when** the first night is built, **then** neither info marker is present
   (regression guard for the existing rule).
3. **Given** a 6-player game with a Marionette and an active Toymaker,
   **when** the first night is built, **then** the standalone "Marionette info" step is
   **absent** and the Demon info step's detail mentions the Marionette.
4. **Given** 3 alive non-Travellers including the Demon, an active Toymaker and the
   `("toymaker","Final Night: No Attack")` token still on the Demon,
   **when** `nightOrder.otherNight(...)` is built,
   **then** the Demon's step is replaced by a "does not act tonight" step (or is absent),
   and `demonAttackCouldEndGame(state) == true`.
5. **Given** the same board but with the token already removed,
   **when** the night is built, **then** the ordinary Demon kill step is present.
6. **Given** 4 alive non-Travellers plus 2 Travellers and an unspent token,
   **when** `demonAttackCouldEndGame` is evaluated, **then** it is `false`
   (Travellers must not be counted).
7. **Given** an Imp holding the unspent token, **when** `starPass(imp, heir)` runs,
   **then** the heir holds `("toymaker","Final Night: No Attack")` and the dead Imp does not.
8. **Given** an active Toymaker and a Demon step, **when** the "Demon declined — no attack
   tonight" action is applied, **then** the token is gone from every seat, no
   `DeathRecord` is added, and a log entry records the declined night.
9. **Given** a Demon marked `("exorcist","Chosen")` and an unspent Toymaker token,
   **when** the night is built, **then** the Toymaker token is still present at dawn
   (an Exorcised night does not consume the obligation).
