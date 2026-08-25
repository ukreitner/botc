# No Dashii (nodashii) — Sects & Violets Demon

## Official rules (sources)

Sources:
- https://wiki.bloodontheclocktower.com/No_Dashii (Character Text, Summary, How to Run, Examples, clarifications)
- https://wiki.bloodontheclocktower.com/Night_Order

Current ability text (wiki, verbatim):

> "Each night*, choose a player: they die. Your 2 Townsfolk neighbors are poisoned."

`characters.json:1085` carries exactly this string (British "neighbours") — **no drift**.

How to Run (wiki, verbatim quotes):

> "While preparing the first night, the two Townsfolk neighboring the No Dashii become **poisoned** - mark them with **POISONED**"

> "Each night except the first, wake the No Dashii. They point at any player. That player **dies**"

> "If a Townsfolk poisoned by the No Dashii becomes a non-Townsfolk character, or the No Dashii turns into a different character, or if a new player becomes the No Dashii, the new neighbors of the No Dashii become **poisoned**, and the old neighbors become **healthy**"

Examples (wiki, verbatim):

> "At the start of the game, the No Dashii neighbors a Town Crier and a Snake Charmer. They are both poisoned."

> "Clockwise from the No Dashii sits a Philosopher, a Mathematician, then a Sage. Anticlockwise from the No Dashii sits a Witch, a Mutant, then a Seamstress. The Philosopher and the Seamstress are poisoned"

Key clarifications:

- **Two Townsfolk, one each direction.** Scan clockwise and anticlockwise from the No Dashii's seat; the **first Townsfolk found in each direction** is poisoned. Outsiders, Minions, Demons and Travellers are **skipped**, not stopped at. In the second example the clockwise scan stops immediately at the Philosopher (Townsfolk); the anticlockwise scan skips the Witch (Minion) and the Mutant (Outsider) and lands on the Seamstress.
- **Dead players are not skipped.** The wiki states the neighbours are identified regardless of "whether they are alive or dead" — a dead Townsfolk absorbs the poison and the living Townsfolk beyond them stays healthy.
- **Starts before night 1.** The poison is in place while preparing the first night, so night-1 info characters (Clockmaker, Dreamer, Steward, Shugenja, Chef, Empath, Washerwoman…) sitting next to the No Dashii already get false info.
- **It is a standing, self-updating effect**: whenever the seating/character situation changes (a poisoned player becomes a non-Townsfolk, the No Dashii changes character, a new player becomes the No Dashii, a Traveller joins or leaves, seats are reordered), recompute — old neighbours become healthy, new neighbours become poisoned.
- **"If a No Dashii dies or otherwise loses their ability, then those two players become healthy."** So: dead No Dashii → no poison. Drunk or poisoned No Dashii → no poison (that is what "otherwise loses their ability" covers).
- The wiki explicitly does **not** address Spy/Recluse misregistration for "who counts as a Townsfolk" here. Standard misregistration practice: the Storyteller may choose whether a Spy registers as a Townsfolk (and would therefore soak the poison) — treat as ST discretion, not automatic.
- Reminder tokens: **DEAD**, **POISONED** (x2).
- **No jinxes** are listed for the No Dashii.
- The nightly kill is entirely ordinary: choose a player, they die, subject to the usual protections and the No Dashii's own sobriety.

## What the app does today

Data (all correct, no drift):
- `characters.json:1081-1094` — team demon, `setup: false`, `otherNightReminder: "The No Dashii points to a player. That player dies."`, reminders `["Dead","Poisoned"]`, no first-night reminder. **Correct.**
- `night_and_jinxes.json:416` — `nodashii` in the other-night order between `fanggu` and `vortox`. **Correct.** No first-night entry. **Correct.**
- No jinx entries. **Correct** (none exist).
- `night_guide.json:739-744` — good other-night prose that explains the standing poison, though its last clause is garbled: *"…update if seating deaths change nothing but characters change."*

Runtime — this is the app's **best-implemented** positional effect:
- `StatusEffects.derivedPoison` (`engine/.../StatusEffects.kt:14-33`) computes the poison from state every time it is asked: it finds every `characterId == "nodashii" && alive` seat, scans `-1` and `+1` from that seat, and stops at the **first seat whose character's team is `TOWNSFOLK`** — dead seats are **not** skipped. Reason string: `"Poisoned by the No Dashii (<name>'s nearest Townsfolk neighbour)"`.
- `StatusEffects.isImpaired` (`:36-46`) folds `derivedPoison` in, so **everything** that asks "is this player impaired" is automatically right: the Grimoire seat badge (`GrimoireScreen.kt:332,421-434`, a green "!" dot), the death-record snapshot `abilityImpairedAtDeath` (`GameActions.kt:153`), the Saint check in `WinCheck.kt:56-60`, and the Demon-impaired warning on the kill panel (`NightScreen.kt:547-553`).
- `InfoCalc.impairments` (`InfoCalc.kt:151`) appends `"<reason> — give false info."` so the caveat appears on every supported info step, and `NightScreen.kt:904-905` matches on the substring `"No Dashii"` to offer the one-tap **false info** chips.
- Kill panel: the No Dashii falls into the generic `else -> DemonKillPanel` branch (`NightScreen.kt:518-523`) — which is **correct** for this Demon: a plain "who did they choose?" picker with protection notes and an impairment warning.
- `StatusEffectsTest.kt:12-27` already tests the skip-non-Townsfolk behaviour and the dead-No-Dashii case.

Storyteller's experience: the two poisoned neighbours show a small green "!" badge on the Grimoire circle, and their night steps carry the "give false info" caveat with false-answer chips. The poison follows character changes automatically. There is **no POISONED token** on those seats — the effect is derived, which is *better* than tokens because it self-updates, but it also means the ST cannot see *why* a seat is badged without opening it, and cannot override it.

**Summary: the core derived poison works.** The gaps below are about the No Dashii's own sobriety, visibility, and the moments the ST is never told anything.

## Defects and gaps

1. **P0 · A drunk or poisoned No Dashii still poisons its neighbours.**
   `derivedPoison` (`StatusEffects.kt:17`) filters only on `characterId == "nodashii" && it.alive`. The wiki: *"If a No Dashii dies **or otherwise loses their ability**, then those two players become healthy."* A Poisoner-poisoned, Courtier-drunked, Sailor-drunked, Innkeeper-drunked or Philosopher-drunked No Dashii must poison nobody.
   *Repro*: place `PlacedReminder("poisoner","Poisoned")` on the No Dashii seat → its neighbours still show the impaired badge and still get the "give false info" caveat.
   *Implementation note*: naively calling `isImpaired(noDashii)` here recurses (`isImpaired` → `derivedPoison` → `isImpaired`). Use a non-derived predicate for the source: `characterId == "drunk" || reminders match poison/drunk || has "No ability"`.

2. **P1 · Nothing tells the storyteller who is currently No-Dashii-poisoned, or that it changed.**
   The effect only appears as an anonymous green "!" on the circle and as a caveat *inside* an info step. There is no "No Dashii poisons: Ben and Farah" line on the No Dashii's own night step, no dawn/day-start line, and — critically — **no notification when the pair changes** (a poisoned Townsfolk becomes a Klutz via Pit-Hag, the Snake Charmer swaps the No Dashii into another seat, a Traveller joins between the seats, seats are reordered from the menu). The rules require the ST to move the tokens at that instant; the app silently changes the answer with no announcement.

3. **P1 · No POISONED reminder tokens are placed, so the derived poison is invisible to token-driven views.**
   `characters.json:1090` declares a `"Poisoned"` reminder for the No Dashii but the app never places it. The Grimoire seat listing (`GrimoireScreen.kt:346-350`) enumerates `player.reminders` for the accessibility description; a No-Dashii-poisoned player reads as "drunk or poisoned" with **no reminders listed**, so the ST cannot see the source at a glance. Additionally the Night tool tray's `Poisoned` chip for `nodashii` would place a *manual* token that duplicates/conflicts with the derived one.

4. **P1 · No storyteller override for misregistration.**
   Whether a **Spy** registers as a Townsfolk (and therefore soaks the No Dashii's poison, protecting the real Townsfolk behind them) is a Storyteller choice. `derivedPoison` uses the true team only, with no toggle, and no `misregistrations()`-style caveat naming the Spy/Recluse sitting between the No Dashii and its "real" neighbour.

5. **P1 · The No Dashii's own night step doesn't show the standing effect.**
   `NightScreen.kt:518-523` gives it the plain `DemonKillPanel`. The step's guide text (`night_guide.json:741`) describes the poison in prose but the panel never names the two currently-poisoned players — the one piece of state the ST most wants confirmed at that moment.

6. **P2 · A "No ability" reminder on the No Dashii is ignored.**
   `derivedPoison` doesn't check for `("<any>","No ability")` — e.g. from a Fabled, a Philosopher, or a house rule. Same class of bug as #1.

7. **P2 · A poisoned *dead* Townsfolk is marked impaired with no explanation of why it matters.**
   Correct per rules (the poison is "wasted" on a corpse), and correct in code, but the ST is never told: *"the poison lands on `<dead player>`; the living Townsfolk beyond them is healthy"* — the single most-misplayed No Dashii detail.

8. **P2 · `night_guide.json:741` closing sentence is garbled.**
   Verbatim: *"…keep Poisoned reminders on them and update if seating deaths change nothing but characters change."* It should say deaths do **not** change the pair (dead players still count as neighbours) but character changes and seat changes do.

9. **P3 · Both directions can resolve to the same player; the app dedupes silently.**
   `derivedPoison` returns a `Map<Long, String>`, so if there is only one Townsfolk in the circle both scans write the same key and it looks like one poisoned player (which is correct) — but the ST is never told that only one Townsfolk exists to poison.

10. **P3 · No first-night acknowledgement.**
    The poison is live from setup, but the first night has no `nodashii` step (correctly), so nothing in the night-1 flow reminds the ST that two Townsfolk are already poisoned before the Washerwoman/Chef/Empath/Clockmaker steps run. The caveats do appear inside those steps, which mostly covers it.

## Proposed behaviour (spec)

### Standing effect (not a night action)

- **when**: continuously, from character assignment onward — **including before night 1**.
- **source condition**: a seat with `characterId == "nodashii"` that is `alive` **and** has ability (not drunk by reminder/`characterId == "drunk"`, not poisoned by reminder, no `"No ability"` reminder). Do **not** consult `derivedPoison` recursively.
- **targets**: scan clockwise and anticlockwise from the source seat. In each direction, stop at the **first** seat whose character's team is `TOWNSFOLK`, **regardless of alive/dead**. Skip Outsiders, Minions, Demons, Travellers and empty seats. Yield 1 or 2 distinct player ids.
- **immediate effects**: those players are poisoned. Model as a derived status (keep the current `derivedPoison` shape) *plus* a **visible synthetic token** rendered on the seat labelled `Poisoned (No Dashii)` so the circle shows the cause without opening the seat. Do not write it into `player.reminders` — keep it derived so it re-computes.
- **expiry**: never as a token; recomputed on every state read. Ends the instant the source dies, loses their ability, or the neighbour relationship changes.
- **ST override**: a per-target toggle "treat `<Spy>` as a Townsfolk for the No Dashii" persisted as a reminder or a `GameState` override map, consulted by the scan.
- **information**: for every poisoned player's own night step, the existing caveat `"Poisoned by the No Dashii (<name>'s nearest Townsfolk neighbour) — give false info."` (`InfoCalc.kt:151`). Keep the false-info chips (`NightScreen.kt:904-919`) and extend them to non-numeric/non-yes-no results (see the Vortox spec — same machinery).
- **visibility**: the poisoned players are told nothing. The No Dashii is told nothing (they do not learn who is poisoned).

### Night action (other nights only)

- **when**: other nights. Wake condition: the No Dashii is **alive**. (An impaired No Dashii still wakes and points — the attack simply fails.)
- **targets**: 1, any player (self and Travellers allowed, dead disabled).
- **immediate effects**: `kill(target, DeathCause.DEMON)` unless blocked. Run `StatusEffects.deathNotes(target)` first and require an explicit "Protected — no death" confirm when a protection is listed. If the No Dashii is impaired, the panel defaults to "attack fails".
- **deferred effects**: killing a Townsfolk neighbour does **not** move the poison (dead Townsfolk still count) — the step must say so explicitly, because it is the exact case storytellers get wrong. Killing a *non*-Townsfolk between the No Dashii and a Townsfolk also does not move it.
- **UI text on the step**:
  - Header: **"No Dashii — who did `<name>` choose?"**
  - Standing line, always visible: **"Poisoning now: `<A>` (clockwise) and `<B>` (anticlockwise)."** — or **"Poisoning nobody — `<name>` is drunk/poisoned."**
  - If a poisoned neighbour is dead: **"`<A>` is dead — the poison is wasted on them; `<next Townsfolk>` is healthy."**
  - If a Spy/Recluse sits between the No Dashii and its computed neighbour: **"`<Spy>` may register as a Townsfolk — tap to make them the poisoned neighbour instead."**
- **change notifications** (the biggest win): whenever the computed pair differs from the pair at the last dawn/dusk, raise a one-line notice on the Night screen and at day start: **"No Dashii poison moved: `<old>` is healthy, `<new>` is poisoned."** Trigger points: character change, alignment/identity change, seat add/remove/reorder, No Dashii death, No Dashii impairment change.

### Data changes

- `night_guide.json:741`: rewrite the closing clause to *"Deaths never move the poison — a dead Townsfolk still counts as the nearest Townsfolk. Character changes, a new No Dashii, and seating changes DO move it."*
- `characters.json`: no change needed (the `"Poisoned"` reminder entry is right even if the app renders it synthetically).
- Night order: no change.

## Tests to add

1. **Impaired No Dashii poisons nobody.**
   *Given* the No Dashii seat has `PlacedReminder("poisoner","Poisoned")`, *when* `derivedPoison` runs, *then* it returns an empty map. Fails today.

2. **Drunk No Dashii poisons nobody.**
   *Given* the No Dashii seat also holds `PlacedReminder("courtier","Drunk")`, *then* `derivedPoison` is empty. Fails today.

3. **No-ability No Dashii poisons nobody.**
   *Given* `PlacedReminder("philosopher","No ability")` on the No Dashii, *then* `derivedPoison` is empty. Fails today.

4. **No recursion / no stack overflow.**
   *Given* a No Dashii adjacent to a Townsfolk, *when* `isImpaired` is called on the No Dashii itself, *then* it returns based on its own tokens only and terminates. (Guard against a naive fix to #1.)

5. **Dead Townsfolk still absorb the poison.**
   *Given* seats `[nodashii, chef(dead), empath]`, *then* the clockwise poison lands on the **dead Chef** and the Empath is **not** poisoned. Passes today — add the assertion so a future "skip the dead" refactor cannot regress it.

6. **The pair moves when a poisoned player changes character.**
   *Given* the poisoned Clockmaker is turned into the Klutz (Outsider) by a Pit-Hag, *then* `derivedPoison` moves to the next Townsfolk in that direction and the Klutz is healthy. Passes today (derived) — assert it, and assert a change-notice is produced.

7. **The pair moves when the No Dashii moves seats/identity.**
   *Given* a Snake Charmer swap puts the No Dashii character on another seat, *then* the poison recomputes around the new seat and the old neighbours are healthy. Note the Snake Charmer path (`GameActions.snakeCharmerSwap`, `GameActions.kt:64-72`) also poisons the new charmer — assert both effects coexist.

8. **Only one Townsfolk in the circle → one poisoned player.**
   *Given* a circle where only one seat is Townsfolk, *then* `derivedPoison` has exactly one entry.

9. **The nightly kill of a poisoned neighbour does not move the poison.**
   *Given* the No Dashii kills its clockwise poisoned Townsfolk, *then* after the kill `derivedPoison` still names that (now dead) player. Passes today — assert it.

10. **Spy override.**
    *Given* a Spy sits between the No Dashii and a Clockmaker and the ST has set the "register as Townsfolk" override, *then* the Spy is poisoned and the Clockmaker is not. Fails today (no override exists).
