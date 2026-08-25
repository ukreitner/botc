# Golem (golem) — Experimental Outsider

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Golem>

Current ability text (matches `characters.json:1622`):

> "You may only nominate once per game. When you do, if the nominee is not the Demon, they die."

Summary: "The Golem kills the player they nominate."

How to Run (quoted):

> "If the Golem nominates a non-Demon, that player **dies**, then the vote continues as normal."
>
> "Mark the Golem with the **MAY NOT NOMINATE** reminder."
>
> "If the Golem nominates the Demon, the vote continues as normal. (*Do not say why.*)"
>
> "If the Golem accidentally nominates when they shouldn't, you can either accept or not accept the
> nomination. It is best to not accept the nomination."

Examples (quoted):

> "The Golem nominates the Poppy Grower. The Poppy Grower dies. The Golem may not nominate again
> this game."
>
> "The Golem nominates the Recluse. The Recluse registers as the Demon. Nothing happens, and the
> Storyteller begins counting votes for the Recluse to be executed. The Golem may not nominate
> again this game."

Derived / standard-rules points that matter for the app:

- **Order of events:** nomination is declared → the nominee dies *immediately* (before any votes
  are counted) → the vote proceeds normally on the now-dead nominee. The dead nominee can still be
  voted on and "executed"; in practice the nomination usually fails, but the tally still happens
  and still sets today's highest tally / block state. The MAY NOT NOMINATE token goes on the Golem
  in **both** branches (Demon or not).
- **The death is not an execution.** It is a day-time death from an ability. Therefore: the Saint
  does not lose the game if the Saint is the nominee; the Undertaker learns nothing (nobody was
  executed); the town's one execution is *not* spent; the Godfather **is** triggered if the nominee
  was an Outsider ("an Outsider died today"); Moonchild/Klutz "when you learn that you died"
  triggers fire; the Golem's own kill counts for the Gossip/Farmer-style "died today" clauses.
- **Misregistration:** the Recluse example is explicit — a Recluse nominated by the Golem may
  register as the Demon and survive. This is a Storyteller choice made *before* announcing the
  result. The Spy could equally be chosen to register as a Minion (and so die) — the ST decides.
  Legion registers as the Demon, so a Golem nomination of a Legion player kills nobody.
- **Protection:** the nominee is a normal death, so anything that stops a death stops it
  (Sailor "can't die", Tea Lady, Fool's first death, Innkeeper "Protected" is night-only, Monk/
  Soldier are Demon-only and do **not** apply, Lleech's host rule, Zombuul's first death). The
  wiki page does not enumerate these; they follow from the general death rules.
- **Drunk/poisoned Golem:** the wiki page does not address this. Standard ruling: the ability does
  not function, so nobody dies. Whether the *restriction* ("you may only nominate once per game")
  still binds is a Storyteller call — the app should surface the choice rather than assume.
  **Flagged as uncertain.**
- **Travellers:** a Traveller may be nominated (for exile). The page does not address a Golem
  exile-nomination; treat it the same way (the nominee dies, then the exile vote continues) but
  present it to the ST as a decision rather than automating it. **Flagged as uncertain.**
- **A dead Golem cannot nominate at all** (dead players never nominate), so the ability is
  effectively alive-only.

Jinxes: none. The wiki's Golem page has no Jinxes section, and
`engine/src/main/resources/botc/data/night_and_jinxes.json` lists none for `golem` — consistent.

## What the app does today

Data:
- `characters.json:1619-1631` — correct ability text, `reminders: ["May Not Nominate"]`, no night
  reminders (correct: the Golem never wakes).
- `night_guide.json` — **no `golem` entry**. Correct in that the Golem has no night step, but it
  means there is no run-book anywhere for the day-time behaviour.
- `night_and_jinxes.json` — not in either night order (correct), no jinxes (correct).

Engine:
- `StatusEffects.kt:148-150` is the *only* Golem code in the whole repo:
  ```kotlin
  if (nominator.characterId == "golem") {
      notes += "Golem nominates: if the nominee is not the Demon, the nominee dies; the Golem may only nominate once per game."
  }
  ```
  It is a static string. It does not check whether the ability is spent, does not check whether the
  nominee is the Demon, does not check impairment, and does not name the nominee.
- No engine action kills the nominee, places the token, or enforces the once-per-game lock.

UI:
- `DayScreen.kt:154-159` renders the warning above the nominator/nominee chips once both are picked.
- `DayScreen.kt:131-140` decides who may nominate: `p.alive && !GameActions.hasNominatedToday(state, p.id)`
  (`GameActions.kt:285-286`). This is a **per-day** check that resets every day; the Golem's
  `May Not Nominate` token (`characters.json:1628`) is never consulted. So on day 2 the app happily
  offers the Golem as a nominator again.
- `DayScreen.kt:217-251` "Record" writes the `Nomination` and spends ghost votes. It never kills
  anyone, never places a reminder, and never asks whether the nominee was the Demon.
- To actually kill the nominee the ST must leave the Day tab, tap the seat, and choose "Other
  death" (`SeatSheet.kt:277-279`, `DeathCause.STORYTELLER`) — and only *there* do the
  `StatusEffects.deathNotes` protections appear (`SeatSheet.kt:240-251`), i.e. after the ST has
  already announced the death at the table.
- The "May Not Nominate" token can only be placed via seat sheet → Add reminder → Golem
  (`SeatSheet.kt:492-571`), and nothing reads it afterwards.
- Golem never appears on the night sheet — **works**.

Net storyteller experience: a one-line reminder of the rule, and then every single consequence by
hand, in the wrong screen, with the protection warnings arriving too late to be useful.

## Defects and gaps

1. **P0 · The once-per-game nomination lock is not enforced (or even warned about).**
   Rules: "You may only nominate once per game" + the MAY NOT NOMINATE token. App:
   `DayScreen.kt:135-138` only calls `hasNominatedToday` (`GameActions.kt:285`), which is scoped to
   `it.day == state.cycle`. Repro: Golem nominates on day 1 (record it), advance to day 2 — the
   Golem chip is enabled again and no warning appears. The app actively invites a rules break.

2. **P0 · The nominee's death is not automated and its protections are not surfaced at nomination
   time.** Rules: "that player **dies**, then the vote continues as normal." App: nothing happens
   on Record; the ST must remember, switch screens, and kill by hand — and only discovers
   "Sailor can't die" / "Fool: the first time they die, they don't" / "Tea Lady" after opening the
   seat (`StatusEffects.deathNotes`, `StatusEffects.kt:52-129`, surfaced only at
   `SeatSheet.kt:240-251`). Repro: Golem nominates the Sailor; the app says only the generic Golem
   line and lets the ST announce a death that cannot happen.

3. **P0 · The app cannot tell the ST whether the nominee is the Demon, so it cannot resolve the
   only branch that matters.** Rules: "If the Golem nominates the Demon, the vote continues as
   normal. (*Do not say why.*)" App: the warning text is identical whether or not the nominee is
   the Demon, even though the grimoire knows the answer (`Team.DEMON` lookup is one line).

4. **P1 · The MAY NOT NOMINATE token is not placed automatically and is inert.** Rules: mark the
   Golem in both branches. App: manual two-level picker, and no code path reads
   `golem:May Not Nominate`.

5. **P1 · Misregistration (Recluse/Spy/Legion) is not flagged at nomination.** Rules: the wiki's
   own second example is a Recluse registering as the Demon and surviving. App: `InfoCalc`'s
   `misregistrations` helper (`InfoCalc.kt:120-130`) exists but is only used for info characters;
   `nominationWarnings` never calls it.

6. **P1 · The death is recorded with the wrong semantics if the ST uses "Executed".** The obvious
   button in the seat sheet is "Executed" (`SeatSheet.kt:274-276`), which writes
   `DeathCause.EXECUTION`. That would (a) make `WinCheck` think an executed Saint lost the game
   (`WinCheck.kt:51-68`), (b) feed a false "executed today" to the Undertaker via
   `InfoCalc.undertaker`, and (c) misreport the game log. There is no "died to an ability during
   the day" cause distinct from `STORYTELLER`.

7. **P1 · Downstream day-death triggers are not chained.** A Golem kill of an Outsider must warn
   "Godfather kills tonight because an Outsider died today" and a Golem kill of a Moonchild/Klutz
   must prompt their public choice. `StatusEffects.deathNotes` already computes all of these
   (`StatusEffects.kt:94-118`) — they are simply never shown at the nomination.

8. **P2 · Impaired Golem is not handled.** If the Golem is drunk/poisoned, nobody should die. The
   app has no branch and gives the ST no prompt to decide whether the once-per-game restriction
   still binds.

9. **P2 · No Golem entry in `night_guide.json`,** so there is no run-book text anywhere in the app
   describing the day-time procedure (the night guide is the only prose surface).

10. **P3 · The warning string is impersonal.** It names neither the nominee nor the outcome:
    "Golem nominates: if the nominee is not the Demon, the nominee dies…" instead of
    "Ben is not the Demon — Ben dies now, then the vote continues."

## Proposed behaviour (spec)

The Golem never acts at night. Everything here is **nomination-time**.

### State

- Source of truth for the spent use: `PlacedReminder("golem", "May Not Nominate")` on the Golem
  seat. Add a helper `GameActions.hasGolemNominated(state, playerId)` that also falls back to
  `state.nominations.any { it.nominatorId == golemId && !it.isExile }` so an undo/redo or a manual
  token removal cannot desync it.

### Nominator eligibility (`DayScreen.kt:131-140`)

- Disable the Golem chip once the token is present, with an explanatory line rather than a silent
  disable: **"Golem has already used their one nomination."**
- Keep it overridable (long-press / "allow anyway"), because the wiki explicitly leaves the
  accidental-nomination case to the ST: "you can either accept or not accept the nomination."

### Nomination-time resolution (extend `StatusEffects.nominationWarnings` + a Day-screen panel)

When `nominator.characterId == "golem"` and a nominee is selected, the engine must return a
**structured** result, not a string:

- `golemImpaired: Boolean` — `StatusEffects.isImpaired(golemSeat)`.
- `nomineeIsDemon: Boolean` — `lookup(nominee.characterId)?.team == Team.DEMON`.
- `misregistrationNotes: List<String>` — Recluse ("may register as the Demon — you may rule they
  survive"), Spy ("may register as a Minion — you may rule they die"), Legion ("registers as the
  Demon — nobody dies").
- `deathNotes: List<String>` — `StatusEffects.deathNotes(state, lookup, nomineeId)` verbatim.
- `nomineeIsTraveller: Boolean`.

UI (Day screen, above the vote row, before "Record"):

- If `golemImpaired`: **"Golem is drunk/poisoned — nobody dies."** plus a checkbox "Still spend
  their one nomination?" (default: yes, ST-overridable).
- Else if `nomineeIsDemon`: **"<Nominee> IS the Demon — nothing happens. Do not say why."** and a
  single button `[Mark Golem's nomination used]`.
- Else: **"<Nominee> is not the Demon — they die now, then the vote continues."** with every
  `deathNote` and `misregistrationNote` listed underneath, and two buttons:
  `[<Nominee> dies · mark nomination used]` and `[Death prevented · mark nomination used]`.
- Both buttons place `golem:May Not Nominate` (exclusive) on the Golem seat.
- The "dies" button calls `kill(nomineeId, DeathCause.ABILITY_DAY)` — a **new** `DeathCause` (see
  below) — and leaves the nomination flow intact so the ST still records votes on the dead nominee.
- If `nomineeIsTraveller`, replace the copy with: **"Traveller — the exile vote continues.
  Storyteller's call whether the Golem's ability kills them."**

### New `DeathCause`

Add `DeathCause.ABILITY_DAY` ("died during the day to an ability"). Required so that:
- `WinCheck.kt:51-68` does not treat a Golem-killed Saint as executed;
- `InfoCalc.undertaker` does not report the Golem's victim as executed;
- the game log (`GameExtras.kt:53-59`) reads "Ben died (Golem)";
- the Godfather trigger ("an Outsider died today") still fires.
Back-compat: existing saves keep `STORYTELLER`.

### Deferred effects / briefing

- Immediately after the kill, surface the chained triggers already computed by `deathNotes`:
  "Godfather kills tonight because an Outsider died today", "Moonchild: they publicly choose a
  player who may die tonight", "Klutz: they publicly choose a player", "Sweetheart / Barber /
  Plague Doctor / Hatter died today" — each as a to-do the ST can tick.
- Day-start briefing while a Golem is in play and unspent: **"Golem may still nominate once — the
  nominee dies unless they are the Demon."**

### Expiry

- `golem:May Not Nominate` — **never expires** (must not be added to `EXPIRES_AT_DAWN` or
  `EXPIRES_AT_DUSK` in `GameActions.kt:218-242`).

### Information / visibility

- Nothing is shown to any player. Evil learns nothing about the Golem. The result of the
  nomination is public only insofar as a player visibly dies; the ST must never say why the
  nominee survived (Demon case).

### Data changes

- Add a `golem` entry to `night_guide.json` under a new **day-guide** section (or a `day` key on
  `NightGuideEntry`) with the How-to-Run prose, so the run-book is reachable from the Script tab
  and from the nomination panel.
- No `characters.json` or night-order change.

### Interactions/jinxes to handle explicitly

- **Recluse / Spy / Legion** — misregistration, ST choice, decided before announcing.
- **Sailor / Tea Lady / Fool / Lleech / Zombuul / Innkeeper(night-only) / Monk & Soldier (Demon
  only, do NOT apply)** — surfaced via `deathNotes`.
- **Saint** — a Golem-killed Saint does **not** lose the game (not an execution).
- **Virgin** — if the Golem nominates a Virgin, the Virgin trigger fires on a *Townsfolk*
  nominator; the Golem is an Outsider, so it does not fire. `nominationWarnings`
  (`StatusEffects.kt:153-157`) currently prints the Virgin warning for any nominator — it should
  say "the Golem is an Outsider — the Virgin does not trigger."
- **Witch curse** — a cursed Golem dies for nominating *and* the Golem kill still resolves; both
  warnings must appear together (`StatusEffects.kt:143-147`).
- **Zealot / Butler** — voting rules, unaffected.
- **Hatter/Pit-Hag-created Golem** — a Golem created mid-game has not nominated yet, so its use is
  fresh; the token must not be inherited from the previous character.

### UI text for the panel

- `"Golem nomination — <Nominee> is not the Demon: they die now, then the vote continues."`
- `"Golem nomination — <Nominee> IS the Demon: nothing happens. Do not say why."`
- `"Golem is drunk/poisoned — nobody dies."`
- `"Golem has already used their one nomination."`

## Tests to add

1. `GameActionsTest`: *Given* a Golem who nominated on day 1, *when* day 2 begins, *then* a new
   `canNominate(golem)` helper returns false (and the recorded nomination alone is enough — no
   token needed). Fails today: `hasNominatedToday` is day-scoped.
2. `StatusEffectsTest`: *Given* Golem nominates a Demon, *then* `nominationWarnings` (or its
   structured successor) reports `nomineeIsDemon = true` and text containing "nothing happens".
   Fails today (single static string).
3. `StatusEffectsTest`: *Given* Golem nominates a Sailor, *then* the nomination result includes the
   Sailor's "can't die" death note. Fails today (death notes are not consulted at nomination).
4. `StatusEffectsTest`: *Given* a poisoned Golem nominates a Townsfolk, *then* the result says
   nobody dies. Fails today.
5. `GameActionsTest`: *Given* Golem nominates a non-Demon Outsider and the ST confirms the kill,
   *then* the death record's cause is `ABILITY_DAY` (not `EXECUTION`), the nominee is dead, and the
   Golem carries exactly one `golem:May Not Nominate` token.
6. `WinCheckTest`: *Given* a Saint killed by a Golem nomination (`ABILITY_DAY`), *then*
   `WinCheck.check` returns **no** "good team loses" advisory. Fails today if the ST uses the
   "Executed" button.
7. `GameActionsTest`: *Given* a Golem with `May Not Nominate`, *when* `advancePhase` runs through a
   dawn and a dusk, *then* the token is still present (not in either expiry table).
8. `StatusEffectsTest`: *Given* a Golem nominates a Virgin, *then* the warnings do **not** claim the
   Virgin triggers (the Golem is an Outsider).
