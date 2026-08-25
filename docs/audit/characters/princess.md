# Princess (princess) — Experimental Townsfolk

## Official rules (sources)

Source: https://wiki.bloodontheclocktower.com/Princess (fetched 2026-08-25).

Current ability text (matches `characters.json` exactly — no drift):

> "On your 1st day, if you nominated & executed a player, the Demon doesn't kill tonight."

How to Run (quoted verbatim):

> "If the Princess nominates and executes a player, mark the Demon with the **DOESN'T KILL** reminder. Tonight, if the Demon would wake to choose a player, wake them as normal. The Demon does not kill."

Clarifications (verbatim):
- "The executed player does not have to die for the Princess ability to work."
- "If the Princess is drunk during the day, then sober at night, they prevent the Demon from killing. If the Princess is sober during the day, but drunk at night, they do not."
- "If a Princess is created mid-game, and they nominate and execute a player on their 1st day as a Princess, the Demon doesn't kill that night."

Additional points from the page:
- The Princess must be the **nominator**, and that nomination must be the one that led to the execution.
- **Exiles do not count.**
- **Non-Demon night kills still happen** (Assassin, Gossip, Godfather, Witch, Fearmonger, Vigormortis…). Only the Demon's *kill* is stopped.
- The Demon still wakes, still chooses, and all of the Demon's **other** effects still happen (No Dashii poisoning, Vortox's false info, Po's "no kill tonight" bookkeeping, Fang Gu's jump, etc.).
- "1st day" = the first day on which the player is the Princess, so a mid-game Princess gets a fresh window.

Jinxes (wiki, exact text):
- **Al-Hadikhia:** "If the Princess nominated & executed a player on their 1st day, no one dies to the Al-Hadikhia tonight."
- **Cannibal:** "If the Cannibal nominated, executed, & killed the Princess today, the Demon doesn't kill tonight."

Not resolved on the wiki (flagged, not guessed): whether the Princess must still be **alive** at night. The clarifications discuss only drunk/sober, never alive/dead. The drunk-at-night clarification implies the ability is evaluated **at night**, which would suggest a dead Princess loses it — but the Cannibal jinx (where the Princess is *killed* that same day and the ability still fires via the Cannibal) points the other way. Surface this as a Storyteller decision rather than hard-coding it.

## What the app does today

Data / order:
- `characters.json:1551` — ability text current. `firstNightReminder: ""`, `otherNightReminder: "If the Princess nominated the player who was executed today, wake the Demon as normal, but no one dies to the Demon's ability."`, `reminders: ["Doesn't Kill"]`. All correct.
- `night_and_jinxes.json:408` — other-night slot 35, immediately **before** Legion/Imp/Zombuul/Pukka/Shabaloth/Po/Fang Gu/No Dashii/Vortox/… Correct position (the marker must be placed before the Demon acts). No first-night entry — correct.
- `night_guide.json:1199` — an `other`-only entry with accurate prose, but no show cards and no computation.
- **Neither Princess jinx (Al-Hadikhia, Cannibal) is in `night_and_jinxes.json`.**

Runtime:
- `NightOrder` emits the plain prose row every night from night 2 on, for as long as a `princess` seat exists (`NightOrder.kt:144-181`), regardless of whether it is the Princess's first day, whether they nominated anyone, or whether anyone was executed.
- **No engine logic exists.** `grep -rn princess engine/src app/src` returns only the data files and the night-order list. `InfoCalc` does not support it (correct — no information), but that also means the row carries no impairment caveat.
- `DemonKillPanel` (`NightScreen.kt:532-635`) is the generic Demon resolver every non-Snake-Charmer/Fang Gu Demon gets. It warns about the Demon being impaired (`NightScreen.kt:544-551`) and prints `StatusEffects.deathNotes` for the chosen target (`NightScreen.kt:584-587`), but knows nothing about the Princess. It offers a live `"<Target> dies"` button.
- `"Doesn't Kill"` appears in **no** Kotlin file. Nothing places it, nothing reads it, and it is in neither `EXPIRES_AT_DAWN` nor `EXPIRES_AT_DUSK` (`GameActions.kt:218,231`), so a hand-placed token would persist for the rest of the game.
- The day side does record everything needed: `Nomination(day, nominatorId, nomineeId, result, isExile)` (`GameState.kt:57-66`) and `DeathRecord(playerId, day, atNight, cause = EXECUTION, …)` (`GameState.kt:72-84`). `InfoCalc.townCrier` (`InfoCalc.kt:296-305`) already demonstrates the query pattern. **The Princess's condition is fully computable from existing state and is simply never computed.**

Storyteller experience today: nothing at nomination time, nothing at execution time, nothing at dusk. At night you get an unpopulated prose row telling you to check something you may have forgotten, and then the very next rows hand you a live "kill this player" button with no warning.

## Defects and gaps

1. **P0 · The Demon kill panel offers a kill on the Princess night with no warning.** `DemonKillPanel` (`NightScreen.kt:532-635`) has no Princess check; the ST who taps through the checklist kills someone the rules say cannot die. This is exactly the class of bug the user hit with the Pukka. Repro: 8-player game, Princess seat 2, Imp seat 0. Day 1: nominate with the Princess, pass the vote, execute. Night 2: the Imp row's panel says "Demon kill — who did Cy choose?" and the `"<name> dies"` button is enabled and unqualified.
2. **P0 · The Princess condition is never evaluated, though all the data is there.** The trigger is `nominations.any { it.day == princessFirstDay && !it.isExile && it.nominatorId == princessSeat && it.nomineeId == executedTodayId }`. Nothing computes it. `NightOrder.kt:146-148` just prints the reminder string. Repro: as above — the app never tells you the condition is met.
3. **P1 · The row appears on every night, not only the one that matters.** `NightOrder.kt:144-145` emits the step whenever a Princess seat exists. On night 5 the ST still sees "If the Princess nominated the player who was executed today…" and must tick it off to satisfy the dawn guard (`GameShell.kt:146-160`). Noise that trains the ST to tick without reading — which is how the real bug happens.
4. **P1 · "1st day" is not enforced.** Even if a ST reads the row carefully, the app's text (and `characters.json`'s reminder) omits "1st day" entirely — it says only "if the Princess nominated the player who was executed today". A Princess who nominates-and-executes on day 3 will look like a trigger. `characters.json:1551`.
5. **P1 · No execution-time or dusk-time trigger.** The moment worth catching is when the execution happens on day 1 — `DayScreen`'s block banner "Execute" button (`DayScreen.kt:110-114`) and the per-nomination "Execute" button (`DayScreen.kt:346-354`), plus the dusk guard's "Execute & begin night" (`GameShell.kt:598-604`). All three kill the nominee with zero Princess awareness. The `Doesn't Kill` token should be placed on the Demon automatically at that instant.
6. **P1 · `Doesn't Kill` has no expiry.** It applies for exactly one night. It is absent from `EXPIRES_AT_DAWN` (`GameActions.kt:218-226`), so a hand-placed token would silently suppress the Demon kill for the rest of the game — or, more likely, be quietly ignored because nothing reads it.
7. **P2 · Both Princess jinxes are missing from `night_and_jinxes.json`.** Al-Hadikhia ("no one dies to the Al-Hadikhia tonight" — a broader effect than "the Demon doesn't kill") and Cannibal ("If the Cannibal nominated, executed, & killed the Princess today, the Demon doesn't kill tonight" — an *alternative trigger path* the app would otherwise miss entirely).
8. **P2 · Drunk/poison timing is not surfaced.** The rule is unusual and counter-intuitive (day state irrelevant, night state decisive). `night_guide.json:1199` states the **opposite** of the wiki: it says *"If the Princess was drunk or poisoned during that day, the Demon kills as normal"* — but the wiki says a Princess drunk during the day and sober at night **does** prevent the kill. This is a factual error in the shipped guide text.
9. **P2 · No day-1 briefing.** Nothing tells the ST at the start of day 1 "a Princess is in play — watch who nominates", and nothing at dusk says "the Princess's window closes tonight".
10. **P3 · The row has no show cards and no `first` guide entry** — correct (the Princess never wakes and learns nothing), so `NightGuide.forStep("princess", true) == null` is fine.

## Proposed behaviour (spec)

### Derived predicate (engine, `StatusEffects.kt` or a new `DayTriggers.kt`)
```
data class PrincessTrigger(val princessId: Long, val nomineeId: Long, val day: Int)

fun princessTrigger(state, lookup): PrincessTrigger? {
    val princess = state.players.firstOrNull { it.characterId == "princess" } ?: return null
    val firstDay = state.princessFirstDay[princess.id] ?: 1   // 1, or the day they became the Princess
    val day = if (state.phase == Phase.NIGHT) state.cycle - 1 else state.cycle
    if (day != firstDay) return null
    val executed = state.deaths.lastOrNull { it.cause == DeathCause.EXECUTION && it.day == day && !it.atNight }
        ?: state.aboutToDieResolvedToday()          // the executed player, even if they did not die
    val nom = state.nominations.lastOrNull {
        it.day == day && !it.isExile &&
        it.nominatorId == princess.id && it.nomineeId == executed
    } ?: return null
    return PrincessTrigger(princess.id, executed, day)
}
```
Two subtleties the implementation must honour:
- **"does not have to die"**: the trigger is *executed*, not *died*. A Devil's-Advocate-protected or Sailor nominee still triggers it. Today `DeathRecord` is the only execution record, so an execution that killed nobody leaves **no trace at all** in state. That is a gap in the model: add `Nomination.executed: Boolean` (or an `executions: List<Execution>` log) so "who was executed today" is answerable independently of who died. Several other characters (Undertaker, Cannibal, Fisherman, Mastermind) need the same distinction.
- **`princessFirstDay`**: for a mid-game Princess, record the day they became one. Needed for Pit-Hag/Amnesiac/Barber cases. Default 1.

### Day-time behaviour
- **At nomination time**, when the nominator is the Princess on their 1st day: `StatusEffects.nominationWarnings` (`StatusEffects.kt:139-166`) adds
  `Princess nominates on their 1st day — if <Nominee> is executed today, the Demon does not kill tonight.`
  This gives the ST the information at the moment they can act on it, exactly like the Virgin and Witch warnings already there.
- **At execution time** (all three call sites: `DayScreen.kt:110-114`, `DayScreen.kt:346-354`, `GameShell.kt:598-604`), when `princessTrigger()` becomes satisfied:
  - place `PlacedReminder("princess", "Doesn't Kill")` on the **Demon's** seat (exclusive — `placeExclusiveReminder`, `GameActions.kt:196-205`);
  - toast/inline confirmation: `Princess: the Demon does not kill tonight. "Doesn't Kill" placed on <DemonName>.`
- **At dusk**, the day summary should repeat it: `Tonight: the Demon does not kill (Princess).`

### Night step
- **when:** other nights only. **Wake condition: emit the row ONLY when `princess:Doesn't Kill` is placed** (equivalently, when `princessTrigger()` is satisfied for the preceding day). No row on any other night.
- **targets:** none.
- **immediate effects:** none of its own — it is a modifier on the Demon's step.
- **the row's job is to alter the Demon's step:**
  - `NightOrder.build`'s `else ->` branch already appends Exorcist and Lunatic annotations to Demon steps (`NightOrder.kt:150-171`) — add the same for the Princess:
    `detail += " — PRINCESS: wake them and let them choose as normal, but NOBODY DIES to the Demon's kill. Other Demon effects (poison, false info, jumps) still happen."`
  - `DemonKillPanel` (`NightScreen.kt:532-635`) must, when the Demon's seat carries `princess:Doesn't Kill`:
    - show a red banner `! Princess — the Demon does not kill tonight. Record their choice, then choose "No kill".`
    - **disable** the `"<name> dies"` button (leave the chip picker enabled so the ST can record the Demon's choice for the Lunatic/Mathematician/Fang Gu logic), and relabel the other button `No kill (Princess)`.
  - **Al-Hadikhia jinx:** the banner becomes `! Princess jinx — NO ONE dies to the Al-Hadikhia tonight (not just the kill).` and every death button in the Al-Hadikhia resolver is disabled.
  - **Fang Gu / Imp star-pass:** the *jump* and the *star pass* are not the kill; the Fang Gu resolver's jump path (`NightScreen.kt:487-501`) must stay enabled. Say so explicitly in the banner.
  - **Non-Demon kills stay enabled** — Assassin, Godfather, Gossip, Witch, Fearmonger, Vigormortis panels are untouched.
- **impairment:** evaluate `StatusEffects.isImpaired(princess)` **at night, at the moment the step is reached** (per the wiki's day-drunk/night-sober clarification). If impaired now: `! The Princess is drunk/poisoned NOW — their ability fails; the Demon kills as normal.` and re-enable the kill button. Do **not** check the Princess's day-time state.
- **alive check:** the wiki does not resolve it. Emit an advisory, not a rule: if the Princess is dead at night, `? The Princess is dead. The wiki does not settle whether the ability still applies — your call.` with both buttons enabled and the "no kill" one pre-selected.
- **expiry:** `("princess", "Doesn't Kill")` goes into **`EXPIRES_AT_DAWN`** (`GameActions.kt:218-226`). It applies to exactly one night.
- **information / visibility:** the Princess learns nothing; the Demon is told nothing about why (they simply wake and choose as normal). The app must **not** show the Demon a card.

### Cannibal alternative trigger
Add: if a living Cannibal nominated, executed and killed the Princess today, place the same `Doesn't Kill` token on the Demon. Implement as a second branch of `princessTrigger()` so the downstream behaviour is identical. Add the jinx to the data.

### UI text the step should display
- Row title: `Princess — the Demon does not kill tonight`
- Row detail: `<PrincessName> nominated <Nominee>, who was executed on day <n>. Wake the Demon, let them choose, then confirm "No kill".`
- Demon row banner: `! PRINCESS — no one dies to the Demon's kill tonight. Their other effects still happen.`

### Data changes
- `characters.json:1551` — change `otherNightReminder` to include the day-1 restriction: `"If the Princess nominated the player who was executed on their 1st day, wake the Demon as normal, but no one dies to the Demon's ability."`
- `night_guide.json:1199` — **fix the factual error**: replace *"If the Princess was drunk or poisoned during that day, the Demon kills as normal"* with *"What matters is the Princess's state now, at night: a Princess who was drunk during the day but is sober now still stops the kill; a Princess who was sober during the day but is drunk now does not."* Also add: *"The executed player does not have to have died. Exiles do not count. Non-Demon kills still happen."*
- `night_and_jinxes.json` — add:
  - `{"id1":"princess","id2":"alhadikhia","reason":"If the Princess nominated & executed a player on their 1st day, no one dies to the Al-Hadikhia tonight."}`
  - `{"id1":"princess","id2":"cannibal","reason":"If the Cannibal nominated, executed, & killed the Princess today, the Demon doesn't kill tonight."}`

## Tests to add

1. `princess trigger fires on day 1 nomination and execution` — Given an 8-player game, Princess seat 2, Imp seat 0; day 1: `recordNomination(day=1, nominator=2, nominee=5, result=ABOUT_TO_DIE)` then `kill(5, EXECUTION)`; When `princessTrigger(state)`; Then it returns `(princessId=2, nomineeId=5, day=1)`.
2. `princess trigger does not fire when someone else nominated` — same but `nominator = 3`; Then null.
3. `princess trigger does not fire on day 2` — Given the same nomination + execution on day 2; Then null.
4. `princess trigger does not fire for an exile` — Given `isExile = true`; Then null.
5. `princess trigger fires when the executed player did not die` — Given the nominee was Devil's-Advocate-protected and `kill` was never called; Then the trigger still fires. (Fails today: nothing records a death-less execution.)
6. `doesn't-kill token is placed on the demon at execution` — When the day-1 execution is recorded; Then the Imp's seat carries `PlacedReminder("princess","Doesn't Kill")` and no other seat does.
7. `doesn't-kill token expires at dawn` — Given the token is on the Demon during NIGHT; When `advancePhase(state)`; Then the token is gone.
8. `princess row is absent on nights with no trigger` — Given a Princess in play but no qualifying nomination; When `otherNight(...)` on cycle 3; Then no step has `id == "princess"`. (Today: present every night.)
9. `demon step is annotated when the princess triggered` — Given the token is on the Imp; When `otherNight(...)`; Then the `imp` step's `detail` contains "PRINCESS" and "no one dies".
10. `impaired-at-night princess does not stop the kill` — Given the trigger fired and the Princess carries `PlacedReminder("poisoner","Poisoned")` at night; Then the Demon step is **not** annotated and the kill stands.
11. `princess jinxes are in the data` — `data.activeJinxes(listOf("princess","alhadikhia")).size == 1` and likewise for `cannibal`.
