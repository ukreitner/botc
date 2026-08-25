# Monk (monk) — Trouble Brewing Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Monk>

Current ability text (matches `characters.json`):

> "Each night*, choose a player (not yourself): they are safe from the Demon tonight."

How to run (wiki):

- **Each night except the first:** wake the Monk. They point at any player **except themself** — *"shake head if they point at themselves"*. Put them to sleep. Place the **SAFE** reminder token by the chosen player's token.
- **If the Demon attacks the protected player:** *"The player remains alive—no death occurs. Do not mark them DEAD or add a shroud. At dawn, announce no one died (without explaining why)."*
- **At dawn:** remove the **SAFE** reminder token.

Key rules / edge cases:

- The Monk **cannot** protect themselves.
- Protection blocks *all* harmful Demon effects — killing, poisoning, corruption — not only the kill. (Trouble Brewing only has the Imp kill, but the spec must not hard-code "kill".)
- Protection does **not** stop the protected player being nominated or executed.
- Worked examples from the wiki:
  - Monk protects Fortune Teller; Imp attacks Fortune Teller → **no death**.
  - Monk protects **Mayor**; Imp attacks Mayor → *"Mayor's ability doesn't trigger; nobody dies."* (protection beats the Mayor bounce — the ST must **not** be offered a redirect here).
  - Monk protects **Imp**; Imp kills **themself** → *"Imp stays alive, no new Imp created"* (**the star pass does not happen**).
- Implicit but load-bearing: a **drunk or poisoned Monk** gives no protection at all. The SAFE token is still placed (the Monk must not learn they malfunctioned) but the chosen player dies normally. `night_guide.json` already says this in prose; nothing in the code acts on it.
- Protection is only against **the Demon**. It does not stop execution, Slayer, Godfather/Assassin (BMR), Gossip, or a storyteller death.

Jinxes (wiki, Monk page):

- **Monk & Leviathan:** *"If Leviathan nominates and executes the Monk-protected player, good wins."*
- **Monk & Riot:** *"If Riot nominates and executes the Monk-protected player, good wins."*

Both are **absent** from `engine/src/main/resources/botc/data/night_and_jinxes.json` (a grep of the jinx list for `monk` returns nothing).

## What the app does today

Data:

- `engine/src/main/resources/botc/data/characters.json:~"monk"` — ability text is current; `otherNightReminder` = *"The previously protected player is no longer protected. The Monk points to a player not themself. Mark that player 'Safe'."*; `reminders: ["Safe"]`.
- `engine/src/main/resources/botc/data/night_and_jinxes.json` — `otherNight` index 20, i.e. after Poisoner (13) / Snake Charmer (19) and before Imp (37). Correct. Absent from `firstNight`. Correct.
- `engine/src/main/resources/botc/data/night_guide.json` `monk.other.instructions` — accurate prose, including the "not themselves" rule and the drunk/poisoned rule. `shows: []`.

Engine:

- `GameActions.kt:218-225` `EXPIRES_AT_DAWN` contains `"monk" to "Safe"`, so the token is swept at `advancePhase` NIGHT→DAY (`GameActions.kt:260`). **Works.**
- `StatusEffects.kt:64-70` `deathNotes` emits `"Marked 'Safe' (Monk) — protected from the Demon."` when the seat carries a reminder whose lowercased label is exactly `safe`.
- There is **no** Monk entry in `InfoCalc`, no protection concept in `GameState`, and nothing that gates a kill.

UI:

- `NightScreen.kt:690-765` renders a "Monk" row each night 2+ whenever a seat holds the character (alive or dead), with the `otherNightReminder` as the detail line.
- `NightScreen.kt:834` → `QuickResolutions` (`NightScreen.kt:462-525`) has **no `"monk"` branch** — the Monk gets no picker. The storyteller places protection through the generic `NightToolTray` (`NightScreen.kt:193-357`): tap the **Safe** chip, then tap a seat chip. The seat chip list is `state.players` (`NightScreen.kt:315`) — **every** seat, including the Monk themselves and dead players.
- Placement goes through `GameActions.placeExclusiveReminder` (`NightScreen.kt:324`, because `allReminders.count { it == "Safe" } == 1`), so the token correctly moves rather than accumulating. **Works.**
- `NightScreen.kt:534-638` `DemonKillPanel` — the Imp step. After a target is picked it prints `deathNotes` in red (`NightScreen.kt:588-590`) and then unconditionally offers an **enabled** `"<name> dies"` button (`NightScreen.kt:625-633`).
- `SeatSheet.kt:255-307` — the *seat* kill path **does** gate on protection: `protectionNotes` filters `deathNotes` for `"Safe"`/`"Protected"`/etc. and routes through a confirmation dialog ("They die anyway" / "Death prevented").

Storyteller's actual experience: at night 2 they tap the Monk row, read the prose, tap **Safe**, tap a seat. At the Imp row they tap a target; if that target is protected they see one line of red text and a fully live **"X dies"** button next to a **"No kill"** text button. Nothing tells them that the Monk was poisoned. At dawn the token vanishes silently and nothing tells them to announce "no one died".

## Defects and gaps

1. **P0 · The demon-kill panel only warns about "Safe"; it still offers the kill.**
   Rules: a Monk-protected player attacked by the Demon simply does not die. App: `NightScreen.kt:625-633` renders `FilledTonalButton(enabled = target.alive) { "<name> dies" }` regardless of the `Marked 'Safe'` note printed at `NightScreen.kt:588`. The seat sheet gates the identical action (`SeatSheet.kt:266-268`) — the night panel, which is where this actually happens, does not.
   Repro: night 2 → Monk step → place **Safe** on Bob → Imp step → tap Bob → tap "Bob dies". Bob is dead and a `DeathRecord(cause = DEMON)` is written.

2. **P0 · A drunk/poisoned Monk still produces a "protected" verdict.**
   Rules: a malfunctioning Monk protects nobody. App: `StatusEffects.deathNotes` (`StatusEffects.kt:64-70`) reads only the *label* on the target; it never looks up the seat that holds `sourceId = "monk"` nor calls `StatusEffects.isImpaired` on them. So a Poisoner who poisons the Monk produces exactly the same red "protected from the Demon" line, and the storyteller is actively told the wrong thing.
   Repro: night 2 → Poisoner marks the Monk **Poisoned** → Monk marks Bob **Safe** → Imp picks Bob → panel says Bob is protected.

3. **P0 · Star pass fires through Monk protection.**
   Rules (wiki example): *"Monk protects Imp; Imp kills themself → Imp stays alive, no new Imp created."* App: `NightScreen.kt:591-622` — when `target.id == holder.id && demonId == "imp"` the panel jumps straight to the heir chips and each chip calls `GameActions.starPass` (`NightScreen.kt:610-615`) with no protection check at all. The Imp dies and a Minion is converted.
   Repro: Monk marks the Imp **Safe** → Imp step → tap the Imp (self) → tap any Minion.

4. **P1 · No Monk picker; the "not yourself" rule is unenforced and unmentioned in the UI.**
   Rules: the Monk may not choose themselves ("shake head"). App: the only path is the tray, whose seat list is unfiltered `state.players` (`NightScreen.kt:315`) — the Monk's own chip is offered, as are dead seats. `QuickResolutions` (`NightScreen.kt:470-524`) has no `"monk"` case.

5. **P1 · Nothing tells the storyteller what to announce at dawn.**
   Rules: *"At dawn, announce no one died (without explaining why)."* App: `advancePhase` (`GameActions.kt:260`) sweeps `Safe` and flips the phase; `GameShell.kt:162-167` jumps to the Day tab. There is no dawn summary anywhere, so "the Monk saved someone, so announce no death" is entirely in the storyteller's head — and the swept token means the evidence is gone by the time they look.

6. **P1 · The Safe token is destroyed before anything can read it.**
   `EXPIRES_AT_DAWN` (`GameActions.kt:219`) removes `monk/Safe` at dawn. Nothing has recorded that the Monk protected X on night N, so: the day log (`GameExtras.kt:40-64`) shows nothing, the Mathematician calc (`InfoCalc.kt:77-80`) explicitly punts to manual tracking, and an undo/redo round trip is the only way to see last night's choice.

7. **P2 · Protection is invisible until after the demon's target is chosen.**
   The `DemonKillPanel` chips (`NightScreen.kt:562-583`) carry only a character token, a name and a `†`. A "shielded" marker on protected seats would let the storyteller steer the conversation before committing.

8. **P2 · The Monk row appears (and blocks the dawn checklist) when the Monk is dead.**
   `NightOrder.build` (`NightOrder.kt:143-145`) includes any step whose character has holders, alive or not. The row shows *"All holders are dead — usually skip."* (`NightScreen.kt:751-757`) but is still an unchecked step, so `GameShell.kt:153-160` blocks "Dawn" until the storyteller manually ticks a step that cannot happen.

9. **P2 · The step's detail text contradicts the app's own behaviour.**
   `otherNightReminder` opens with *"The previously protected player is no longer protected."* — but the app already removed that token at dawn. On a phone this is the most prominent line on the row (`NightScreen.kt:744-750`).

10. **P3 · Both official Monk jinxes are missing from the data.**
    `night_and_jinxes.json` has no `monk` jinx; `SeatSheet.kt:225-234` and `ActiveJinxesDialog` therefore show nothing for a Monk + Leviathan or Monk + Riot script.

11. **P3 · `deathNotes` says "protected from the Demon" for a `Safe` token from *any* source.**
    `StatusEffects.kt:66` matches on the label alone. Harmless today, wrong the moment another script uses a `Safe` label with different semantics.

## Proposed behaviour (spec)

### Night action

- **when:** other nights only (`otherNight` position 20 — unchanged). Wake condition: the Monk seat is **alive**. If the Monk is dead, render the row collapsed/greyed and auto-mark it done so it never blocks the dawn guard.
- **targets:** exactly 1. Constraints: **not the Monk themself** (chip disabled with the tooltip "the Monk can't protect themselves"). Alive players sorted first; dead players allowed but sorted last and dimmed (protecting a corpse is legal and pointless — do not block it, the Monk may be bluffing).
  Default/sort: alive, not-self, then by seat order starting clockwise from the Monk.
- **immediate effects:** place `PlacedReminder("monk", "Safe")` **exclusively** (already the behaviour). Additionally set a derived, non-token status `protectedFromDemon(playerId, source = monk seat)`.
- **impaired source:** if `StatusEffects.isImpaired(monk)` is true, still place the token (the grimoire must look normal to a Spy), but the derived status must be **inert**, and the token must render with a strike-through/"inert" badge on the storyteller's grimoire.
- **deferred effects:** none beyond tonight.
- **expiry:** `Safe` expires at **dawn** (unchanged). Before sweeping it, copy it into the night log (below).
- **information:** none — the Monk learns nothing.
- **visibility:** nothing is shown to the Demon or Minions.
- **day-time inputs:** none.

### Protection must be enforced, not narrated

Introduce one engine function and route every kill through it:

```
StatusEffects.protection(state, lookup, targetId, cause): Protection?
  Protection(kind, sourceSeatId, sourceCharacterId, effective: Boolean, reason: String)
```

- Returns a `Protection` for: `monk/Safe` (cause = DEMON only), `innkeeper/Protected`, Soldier (cause = DEMON), Sailor, Fool (first death), Tea Lady, `devilsadvocate/Survives execution` (cause = EXECUTION only), Lleech host, Monk-on-Mayor, etc.
- `effective = false` when the **source seat** is drunk/poisoned/dead-and-shouldn't-act, with `reason` explaining ("the Monk is poisoned — this protection does nothing"). The token still exists; only `effective` differs.
- `deathNotes` keeps producing text but derives it from `protection(...)` so the impaired case reads *"Marked 'Safe' (Monk) — but the Monk is POISONED, so this does NOT protect them."*

`DemonKillPanel` then becomes:

- Protected chips render with a shield glyph, computed from `protection(state, …, cause = DEMON)` for every seat **before** selection.
- When the selected target has an **effective** protection:
  - primary button is **"Nobody dies — <name> is protected by the <source>"** (records the night's outcome, does not kill);
  - the "X dies" button is demoted to a secondary **"Override: they die anyway"** behind the same confirmation dialog pattern already used at `SeatSheet.kt:288-307`.
- When the protection is **not effective** (impaired source), show the red reason and keep the kill primary.
- **Self-kill + protection:** if the Imp targets itself while effectively protected, do **not** show the heir chips at all. Show *"The Imp is Monk-protected — the Imp survives and no new Imp is created. Nobody dies tonight."* with a single "Nobody dies" button.
- **Mayor + protection:** if the target is an effectively-protected Mayor, suppress the Mayor bounce picker (see `mayor.md`) and say *"Nobody dies — the Mayor's ability does not trigger."*

### Night log (needed by this character and by every info role)

Add to `GameState`:

```
@Serializable data class NightRecord(
  val cycle: Int, val stepId: String, val holderIds: List<Long>,
  val targetIds: List<Long> = emptyList(),
  val outcome: String = "",        // "Safe placed on Bob", "YES", "1", …
  val impaired: Boolean = false,
)
val nightLog: List<NightRecord> = emptyList()
```

The Monk step writes `NightRecord(cycle, "monk", [monkId], [bobId], "Bob is safe from the Demon", impaired)`. The dawn sweep reads it to build the dawn briefing; the log dialog (`GameExtras.kt:40-64`) renders it.

### Dawn briefing (cross-cutting; the Monk is the clearest case for it)

`advancePhase` NIGHT→DAY should produce a `DawnReport` the shell shows as a modal *before* the Day tab:

- **Deaths tonight:** names, in seat order, with "announce in this order".
- **No deaths:** *"Announce that nobody died. Do not explain why."*
- Per-death annotations the storyteller must handle at day start (Ravenkeeper woken, Undertaker will learn X tonight, etc.).
- A "resurrections/changes" section (Professor etc.).

### UI text for the Monk step

- Row detail: **"Monk chooses someone to protect from the Demon tonight. Not themselves."**
- Picker header: **"Who did the Monk point at?"**
- Self chip disabled, label suffix **" — can't protect self"**.
- After placement: **"<name> is safe from the Demon tonight."** and, when the Monk is impaired, **"⚠ The Monk is POISONED — <name> is NOT actually protected. Place the token anyway."**
- Dead Monk row: **"Monk is dead — does not act."**

### Data changes

- `characters.json` `monk.otherNightReminder` → drop the stale first sentence: *"The Monk points to a player other than themselves. Mark that player 'Safe'."* (the app removes the previous token itself).
- `night_and_jinxes.json` `jinxes` → add
  `{"id1":"monk","id2":"leviathan","reason":"If the Leviathan nominates and executes the Monk-protected player, good wins."}` and
  `{"id1":"monk","id2":"riot","reason":"If Riot nominates and executes the Monk-protected player, good wins."}`.
- `night_guide.json` `monk.other` — add a `shows` entry? No: the Monk is shown nothing. Leave `shows: []`.

## Tests to add

1. `monk protection blocks a demon kill`
   Given 7 seats with `monk`, `imp`, and `mayor`; night 2; `PlacedReminder("monk","Safe")` on the Mayor.
   When the demon-kill resolver is asked to kill the Mayor with `cause = DEMON`.
   Then no `DeathRecord` is created, the Mayor is alive, and the resolver returns "nobody dies".

2. `poisoned monk does not protect`
   Given the same board plus `PlacedReminder("poisoner","Poisoned")` on the Monk seat.
   When the demon kills the Safe player.
   Then the player dies, and `StatusEffects.protection(...)` returns `effective = false` with a reason naming the Monk.

3. `monk protected imp does not star pass`
   Given `imp` marked `Safe` by the Monk, with a `scarletwoman` alive.
   When the Imp targets itself.
   Then the Imp is still alive, the Scarlet Woman still holds `scarletwoman`, and no `DeathRecord` exists.

4. `monk cannot target itself`
   Given the Monk seat. When the target validator is asked for legal Monk targets.
   Then the Monk's own id is absent and every other seat id is present.

5. `safe token expires at dawn and is logged`
   Given `Safe` on seat 3 during night 2. When `advancePhase` runs.
   Then no seat carries `monk/Safe`, and `state.nightLog` contains a `NightRecord(cycle = 2, stepId = "monk", targetIds = [3])`.

6. `monk protection does not stop an execution`
   Given `Safe` on seat 3. When seat 3 is killed with `cause = EXECUTION`.
   Then seat 3 dies (protection is Demon-only).

7. `monk protection beats the mayor bounce`
   Given a Monk-protected Mayor. When the Demon targets the Mayor.
   Then the resolver reports "nobody dies" and offers **no** bounce candidates.

8. `monk jinxes are present in the data`
   Given `GameData.loadDefault()`. Then `activeJinxes` for a script containing `monk` + `leviathan` returns the Leviathan jinx, and `monk` + `riot` returns the Riot jinx.

9. `dead monk step is not required before dawn`
   Given a dead Monk on night 3. When the night sheet is built.
   Then the Monk step is marked non-blocking (or pre-completed) and the dawn guard does not list it.
