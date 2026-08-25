# Doomsayer (doomsayer) — fabled Fabled

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Doomsayer> (fetched verbatim via
`api.php?action=parse&page=Doomsayer&prop=wikitext`, 2026-08-25).

Current ability text (verbatim summary line):

> "If 4 or more players live, each living player may publicly choose (once per game) that a player of their own alignment dies."

**Summary bullets (verbatim):**

- "Use the Doomsayer to make large games take less time."
- "The Doomsayer allows players to sacrifice their allies in order to gain information, which shortens the game."
- "Only alive players may use the Doomsayer ability, and each may do so only once per game. It is their responsibility to remember to not use it again."
- "If a player says something like 'I use the Doomsayer ability,' then the Storyteller chooses which player to kill, but they must kill an alive player of the same alignment as the player who used the Doomsayer ability. So, if a good player uses the ability, then a good player dies. If an evil player uses the ability, then an evil player dies."
- "Once three players are left alive, the Doomsayer ability may no longer be used."

**How to Run (verbatim):**

> At any time, declare that the Doomsayer is in play. Add the Doomsayer token to the Grimoire.
>
> At any time during the day, if four or more players are alive, a player can declare that they wish to use the Doomsayer ability. When this happens, choose one player of the same alignment. The chosen player dies.
>
> You won't want to kill the Demon this way, unless the game can continue afterwards for some reason, such as if a Scarlet Woman is in play.

**Examples (verbatim):**

1. "The Monk uses the Doomsayer ability, and the Washerwoman dies. Later that day, the Poisoner uses the Doomsayer ability, and the Baron dies."
2. "An evil Thief uses the Doomsayer ability, and the Scarlet Woman dies. **Later, the Spy uses the Doomsayer ability, and the good Gunslinger dies.** Later, the Demon uses the Doomsayer ability, and the Spy dies."

**Explanation (verbatim, adds one line the How-to-Run omits):**

> "Sometimes, it may be best to kill the player who uses the Doomsayer ability. Such cases are rare, but occasionally it may be the best decision to not grant either team a large advantage."

**Rules distilled into constraints an app can enforce:**

| | |
|---|---|
| Timing | **Day only**, at any point in the day. Not at night. |
| Who may invoke | An **alive** player. Travellers are players and are alive, so they qualify. |
| Frequency | **Once per game, per player.** The player is responsible for remembering — but an app can simply track it. |
| Gate | **4 or more players alive.** "Once three players are left alive, the ability may no longer be used." |
| Victim | Chosen by the **storyteller**, from **alive** players of the **same alignment** as the invoker. |
| Self-kill | Permitted and sometimes recommended ("it may be best to kill the player who uses the Doomsayer ability"). |
| Registration | Example 2 has the **Spy** invoke and a **good** player die. The Spy is evil but registers as good, so the storyteller may follow registration rather than true alignment. The wiki does not state a hard rule; treat it as **storyteller choice, offered but not forced**. |
| Demon | "You won't want to kill the Demon this way, unless the game can continue afterwards… such as if a Scarlet Woman is in play." Advisory, not a prohibition. |
| Death type | Not an execution, not a night death. A plain storyteller kill during the day. |

**Jinxes:** none.
**Night order:** never wakes. Correctly absent from both order lists.

## What the app does today

Data:
- `characters.json:2183-2194` — ability text matches the wiki exactly; `team: fabled`,
  `setup: false`, **no reminders**, no night reminders. (Matches the community dataset;
  the official app tracks usage in software, so no physical token exists.)
- `night_and_jinxes.json` — correctly absent from both order lists. **Works.**
- `night_guide.json` — no entry; there is no day-time run-book schema at all
  (`NightGuideEntry` has only `first`/`other`, `NightGuide.kt:36-40`).

Code — **zero** engine awareness. The Doomsayer is a toggle
(`GameExtras.kt:167-195` → `GameActions.setFabled`, `GameActions.kt:211-212`) and a 30dp
token in the corner of the grimoire (`GrimoireScreen.kt:215-218`). Nothing else in
`engine/` or `app/` mentions it.

What the storyteller actually does at the table, per invocation:

1. Hear "I use the Doomsayer ability."
2. Count alive players by hand, or read `state.alivePlayers.size` off the Day tab header
   (`DayScreen.kt:86-92`, "N alive · M votes to execute") — and remember that the gate is
   4, not 3.
3. Remember whether *this specific player* has already used it. Nothing in the app records
   it; the closest storage is the per-seat free-text note (`Player.note`,
   `GameActions.setNote`, `GameActions.kt:132-133`) or a hand-placed generic `"Used"`
   reminder chip (`SeatSheet.kt:502`), neither of which is prompted or checked.
4. Work out the invoker's alignment. `Player.isEvil(lookup)` exists
   (`GameState.kt:49-52`, honouring `alignmentFlipped`) and is used for the reveal screen
   (`GameExtras.kt:300`) and the Fortune Teller herring prompt (`GameShell.kt:360`) — but
   there is no alignment filter anywhere on a day-time player list.
5. Pick a victim by eye, open their seat sheet, and press **"Other death"**
   (`SeatSheet.kt:277`, `DeathCause.STORYTELLER`). That routes through
   `SeatSheet.requestKill` (`SeatSheet.kt:266-268`), which first pops the
   "might be protected" dialog if any `deathNotes` protection matches — appropriate here,
   since Soldier/Monk protection does **not** stop a Doomsayer death (they are not Demon
   kills), so the dialog is a false alarm the storyteller must click through.
6. The game log (`GameExtras.kt:46-106`) records "<name> died (storyteller)" with no
   indication that the Doomsayer caused it or who invoked it.

## Defects and gaps

1. **P1** · No once-per-game-per-player tracking. This is the single thing the wiki hands
   to the players ("It is their responsibility to remember to not use it again") and the
   single thing an app is best at. Nothing in `GameState` can express it; the storyteller
   must remember up to 15 separate flags. *Repro:* Fabled… → Doomsayer → nothing is
   offered; no seat shows a "Doomsayer used" mark.
2. **P1** · No alignment-filtered victim picker. The rule is "kill an alive player of the
   same alignment as the invoker"; the app makes the storyteller compute that mentally
   while the table watches, on a phone screen showing a circle of names. `isEvil(lookup)`
   is already implemented (`GameState.kt:49-52`) and unused for this.
3. **P1** · No gate on the alive count. At 3 alive the ability is illegal; the app will
   happily let the storyteller kill anyway, and offers no warning.
4. **P1** · No Doomsayer death path. The only route is the generic "Other death" button
   (`SeatSheet.kt:277`), which (a) mislabels the cause in the log and reveal
   (`GameExtras.kt:58, 328` render `DeathCause.STORYTELLER` as "died (storyteller)" /
   "died day N"), and (b) fires the protection confirmation dialog
   (`SeatSheet.kt:256-268`) for protections that cannot apply.
5. **P2** · The "don't kill the Demon" advisory is never shown, even though the app knows
   exactly who the Demon is and whether a Scarlet Woman is alive — `WinCheck.check`
   already reasons about precisely this pair of facts (`WinCheck.kt:70-86`,
   "Scarlet Woman: with 5+ players alive she becomes the Demon instead").
6. **P2** · Misregistration is not surfaced. The wiki's own example has the Spy killing a
   *good* player. `InfoCalc` already models Spy/Recluse misregistration for info roles
   (`InfoCalc.kt:5-8` doc comment, and the per-character caveats) — the same knowledge
   should tint the Doomsayer's candidate list rather than being unavailable.
7. **P2** · No log entry. "Ana used the Doomsayer; Ben died" is exactly the kind of line a
   storyteller reconstructs at the reveal, and the log has no Fabled events at all.
8. **P2** · The alive-count gate should be explicit about travellers. `GameState` exposes
   both `alivePlayers` and `aliveNonTravellers` (`GameState.kt:116-117`), and the app uses
   `alivePlayers.size` for the execution threshold (`GameState.kt:125`) but
   `count { !it.isTraveller } >= 7` for minion/demon info (`NightOrder.kt:52`). The wiki
   says "players live", which includes travellers; the spec below uses `alivePlayers`, and
   the UI should say which it counted.
9. **P3** · No way to record the *public* declaration. The Doomsayer is used publicly; a
   storyteller reviewing the day wants "who claimed what, when" — the same day-statement
   recording gap the user hit with Gossip.

## Proposed behaviour (spec)

Shares the `FabledEntry` storage introduced in `angel.md`; the Doomsayer uses
`spentBy: Set<Long>`.

- when: **day only**, any time during DAY phase. No night step, no night-order entry.
  Hard-disable the panel during NIGHT and SETUP.
- entry point: a **Doomsayer card on the Day tab**, above "New nomination"
  (`DayScreen.kt:126-255`), visible only while `doomsayer` is in `state.fabled`:

  > **Doomsayer** — 4+ alive, once per player
  > Who is using it? [chips: every alive player who is not in `spentBy`]
  > *(disabled with "3 alive — the Doomsayer can no longer be used" when
  > `state.alivePlayers.size < 4`)*

- targets: after an invoker is chosen, the panel lists **alive players whose alignment
  matches the invoker's** (`p.alive && p.isEvil(lookup) == invoker.isEvil(lookup)`),
  including the invoker themselves, each as a confirm button
  `"<name> dies"`. Sorting/annotation:
  - the invoker is listed with the hint "(the wiki notes it is sometimes best to kill the
    player who used it)";
  - the Demon is listed last, with a red caution
    `"Killing the Demon usually ends the game"` — suppressed to
    `"Scarlet Woman is alive — the game can continue"` when a living `scarletwoman` exists
    and `state.alivePlayers.size >= 5` (mirror the logic at `WinCheck.kt:72-74`);
  - below the list, a secondary row **"Registration"**: any alive player who registers as
    the invoker's alignment but is truly the other (`spy` registering good, `recluse`
    registering evil, `alignmentFlipped` seats) is offered under the heading
    "The Spy registers as good — the wiki's own example kills a good player here."
    Chosen deliberately, never silently.
- immediate effects: one confirm applies, atomically and undoably:
  1. `kill(victim, DeathCause.DOOMSAYER)` — **add a new `DeathCause` value** rather than
     reusing `STORYTELLER`, so the log, reveal and any future Undertaker/Cannibal logic can
     tell them apart (`GameState.kt:75`, and the four `when` blocks over `DeathCause` at
     `GameExtras.kt:52-59`, `GameExtras.kt:322-330`);
  2. `spentBy += invoker.id`, and place `PlacedReminder("doomsayer","Used")` on the
     invoker's seat so the grimoire shows it (requires adding `"Used"` to
     `characters.json` `reminders` for `doomsayer`, currently `[]`);
  3. append a log entry "D<n>: Ana used the Doomsayer — Ben dies".
- protection: **skip the "might be protected" confirmation** for this cause. Soldier, Monk,
  Innkeeper and Devil's Advocate all protect against specific sources (the Demon, or
  execution) and none of them stops a Doomsayer death. Do still surface *on-death triggers*
  from `StatusEffects.deathNotes` (`StatusEffects.kt:93-127`) — Ravenkeeper (day death, so
  it does **not** wake — say so explicitly), Farmer, Moonchild, Sweetheart, Barber, Godfather,
  Scarlet Woman, Minstrel, Vigormortis — because those do fire.
- **not an execution**: the death must not touch the Saint check
  (`WinCheck.kt:51-68` keys off `DeathCause.EXECUTION`), the Fearmonger's "if executed from
  this nomination" (`StatusEffects.kt:158-160`), the Devil's Advocate's
  "survives execution" token, the Mastermind day, or `aboutToDie`. Using a distinct
  `DeathCause` makes all of those correct by construction.
- deferred effects: none. The kill resolves immediately.
- expiry: `("doomsayer","Used")` **never** expires — do not add it to
  `EXPIRES_AT_DAWN`/`EXPIRES_AT_DUSK` (`GameActions.kt:218-242`).
- information: none computed.
- visibility: entirely public. Nothing hidden from anyone; no show card needed beyond an
  optional `ShowCard.Message("<Ben> has died")` for the group.
- day-time inputs the app must record: the invoker (public declaration) and the resulting
  death, both in the log.
- interactions:
  - **Travellers** may invoke and may be killed; they are players of an alignment.
    An exiled traveller is dead and cannot invoke.
  - **Alignment changes** (Snake Charmer swap `GameActions.kt:64-72`, Pit-Hag, Cult Leader,
    `alignmentFlipped`) must be read at the moment of invocation, not cached.
  - **Resurrection**: a player who used the Doomsayer, died, and was resurrected
    (`GameActions.resurrect`, `GameActions.kt:173-181`) has still used it — `spentBy` is
    keyed by player id and never cleared.
  - **Fabled cannot be killed**, so the Doomsayer never targets a Fabled; the app has no
    Fabled seats, so this is automatic.

**UI text:**
- Card header: "Doomsayer — a player may sacrifice an ally (once each)".
- Gate: "Only 3 players are alive — the Doomsayer can no longer be used."
- After use: "Ana has used the Doomsayer" on the seat and in the invoker chip row (greyed).
- Demon caution: "Ben is the Demon — killing them here usually ends the game."

**Data changes:**
- `characters.json:2192` — `"reminders": []` → `"reminders": ["Used"]`.
- `GameState.kt:75` — `DeathCause` gains `DOOMSAYER`.
- `night_guide.json` — add a `doomsayer` day entry once the guide schema has one.
- night order data: no change.

## Tests to add

1. `doomsayer is blocked below four alive`
   Given 3 alive players and `doomsayer` active,
   Then the Doomsayer action reports `false`/rejects for every invoker.
   *(Nothing to fail today — the feature is absent.)*
2. `doomsayer victims match the invoker's alignment`
   Given a good Monk invoking with a Washerwoman, a Poisoner and a Baron alive,
   Then the candidate list is exactly the alive good players (including the Monk) and
   excludes the Poisoner and Baron.
3. `spy invoking may kill a good player via registration`
   Given the Spy (evil, registers good) invoking,
   Then the primary candidate list is the alive evil players **and** a separately labelled
   registration list containing the alive good players.
4. `doomsayer marks the invoker spent`
   Given Ana invokes and Ben dies, Then `fabled["doomsayer"].spentBy` contains Ana's id,
   Ana holds `("doomsayer","Used")`, and Ana is absent from the invoker list afterwards.
5. `a resurrected invoker stays spent`
   Given Ana invoked, then died, then `GameActions.resurrect(ana)`,
   Then Ana is still in `spentBy`.
6. `doomsayer death is not an execution`
   Given the Saint alive and sober, When the Saint is killed as a Doomsayer victim,
   Then `WinCheck.check` returns no "The Saint died by execution" advisory.
   *(Fails today if the storyteller uses the Execute button; guards the new cause.)*
7. `doomsayer death skips protection prompts but keeps triggers`
   Given the Soldier alive, When the Soldier is a Doomsayer victim,
   Then no protection confirmation is raised; and given the Farmer alive as victim,
   Then the death notes still include the Farmer trigger.
8. `the used token never expires`
   Given `("doomsayer","Used")` on Ana, When phases advance through a full day and night,
   Then the token is still there.
