# Al-Hadikhia (alhadikhia) — exp demon

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Al-Hadikhia> (fetched 2026-08-25).

Current ability text (matches `characters.json`):

> "Each night*, you may choose 3 players (all players learn who): each silently
> chooses to live or die, but if all live, all die."

Summary bullets (quoted):

- "The Al-Hadikhia may choose three players per night. Everyone learns which three were chosen."
- "All players must be silent when the Al-Hadikhia acts at night."
- "If the Al-Hadikhia chooses no one, no announcement is made and nobody dies to the Al-Hadikhia tonight."
- "At night, the Storyteller asks players out loud if they choose to live. If they nod
  their head, they live. If they shake their head, they die. **Players may be brought
  back to life this way.**"
- "If all players choose to live, then they all die instead."

How to Run (quoted / paraphrased tightly):

1. **Each night except the first.** "Wake the Al-Hadikhia. They may point at three
   players. If they do, mark these players with the **1**, **2** and **3** reminders,
   in the order the Al-Hadikhia chose. Put the Al-Hadikhia to sleep."
2. "Wake the player marked **1** and say 'The Al-Hadikhia has chosen' then the name of
   the player, then 'Do you choose to live?'" They nod (live) or shake their head (die).
   Put them to sleep. **If they chose to live, remove their shroud (if any); if they
   chose to die, add a shroud.**
3. Repeat for the player marked **2**, then the player marked **3**. Each is resolved
   and announced *before* the next is woken.
4. "If all three players are alive (*none of them have a shroud*) then add a shroud to
   all three. They die."
5. Declare that the silence has ended. The silence "lasts from when the Storyteller
   first declares that a player has been chosen, until the Storyteller says that it ends."
6. **Each dawn:** "declare which players marked **1**, **2** and **3** are alive and
   which are dead. Do this even if a player's alive or dead status did not change
   during the night."

Examples (quoted):

- *Example 1:* "Al-Hadikhia chooses Evin, Lachlan, and Sarah. Evin chooses to die.
  Lachlan chooses to die. Sarah chooses to live. Result: Evin and Lachlan are dead;
  Sarah is alive."
- *Example 2:* "Al-Hadikhia chooses Alex, Lewis, and **Doug (who is dead)**. All three
  choose life; **Doug becomes alive**. Since all are alive, all three players die."

Key consequences the app must honour:

- **Dead players are legal targets** and a dead target who chooses "live" is
  **resurrected** — then, if all three end up alive, all three die again. A dead player
  can therefore go dead → alive → dead in one night.
- The choice is **optional** ("you may"); choosing no one means *no announcement at all*.
- All three choices are **public in effect** — the ST speaks the names aloud, so at dawn
  the whole table already knows who was chosen; the dawn statement is a *status* report.
- Resolution is strictly **sequential**: target 1 is resolved and announced before target
  2 is woken (this is the whole point of the dilemma).

Jinxes (all three quoted from the wiki):

- **Mastermind:** "If the Al-Hadikhia dies by execution, and the Mastermind is alive, the
  Al-Hadikhia chooses 3 good players tonight: if all 3 choose to live, evil wins."
  (The Mastermind page phrases the other half: otherwise good wins.)
- **Princess:** "If the Princess nominated & executed a player on their 1st day, no one
  dies to the Al-Hadikhia tonight."
- **Scarlet Woman:** "If there would be two Demons, one of which was the Scarlet Woman,
  the Scarlet Woman becomes the Scarlet Woman again."

**Not covered by the wiki** (flagging rather than guessing): whether a drunk/poisoned
Al-Hadikhia still runs the ritual; whether the Al-Hadikhia may choose itself (the Tips
section discusses it, implying yes); whether Monk/Soldier/Innkeeper protection stops a
player who *chose to die* from dying. The app must therefore **surface** protections and
impairment at the moment of resolution and let the storyteller decide, not decide for them.

Night order: other nights only. `night_and_jinxes.json:421` (`otherNight`, between
`ojo` and `lleech`). No first-night entry — correct.

## What the app does today

Data:

- `engine/src/main/resources/botc/data/characters.json:1952` — ability text matches the
  wiki; `reminders: ["1","2","3"]`; `otherNightReminder` = "The Al-Hadikhia chooses 3
  players. In order, wake each target. They nod or shake their head. Put them to sleep
  before waking the next target."
- `engine/src/main/resources/botc/data/night_guide.json:1527` — one `other` entry with a
  good prose run-book and a single show card ("Ask each chosen" → `DO YOU CHOOSE TO LIVE
  OR TO DIE?`). The prose is accurate (it even mentions choosing itself and resolving
  before other dawn announcements) but it is **prose only** — nothing is executable.
- `engine/src/main/resources/botc/data/night_and_jinxes.json:174` — only the Scarlet
  Woman jinx is present, and its text is the app's own paraphrase.

Code path the storyteller actually hits:

- `NightOrder.build` (`engine/.../NightOrder.kt:120-179`) emits one step titled
  "Al-Hadikhia" with `detail` = the `otherNightReminder`.
- `NightScreen.StepDetailPanel` (`app/.../NightScreen.kt:770-833`) renders the guide
  prose + the one show chip, then calls `QuickResolutions`.
- `QuickResolutions` (`app/.../NightScreen.kt:462-525`) has no `alhadikhia` branch, so it
  falls through to `else ->` at line 518: `character.team == Team.DEMON && holder.alive`
  → **`DemonKillPanel`**.
- `DemonKillPanel` (`app/.../NightScreen.kt:534-638`) prints **"Demon kill — who did
  <name> choose?"**, lets the ST pick **one** player, and offers a single
  "<target> dies" button (`GameActions.kill(..., DeathCause.DEMON, ...)`).

So the storyteller's experience today: read a paragraph, then be offered exactly the
wrong tool (a one-player demon kill), then hand-place three tokens from the
`NightToolTray` (`app/.../NightScreen.kt:283-354`), hand-kill/hand-resurrect from each
seat sheet (`SeatSheet.kt:255-282`), and remember the "all live ⇒ all die" rule, the
silence rule and the dawn status report entirely from memory.

Works today: the three reminder labels exist and the tray places them one-per-seat
(each label has one copy, so `placeExclusiveReminder` moves it rather than duplicating).

## Defects and gaps

1. **P0 · The night step offers a single-target demon kill.**
   The rules require *three ordered targets and a live/die choice each*; the app shows
   "Demon kill — who did X choose?" and a one-player kill button.
   `app/.../NightScreen.kt:518-523` → `:534-638`.
   *Repro:* any script with Al-Hadikhia, night 2, tap the Al-Hadikhia row.

2. **P0 · "If all three live, all three die" is never computed or prompted.**
   Nothing in engine or UI detects the all-alive case; if the ST forgets, the game is
   silently wrong. No code path references `alhadikhia` outside the data files
   (`grep -rn alhadikhia engine/src app/src` returns only JSON hits).

3. **P0 · Resurrection of a dead chosen player is not offered.**
   Per the wiki, a dead target who chooses "live" comes back to life ("Players may be
   brought back to life this way"). The kill panel only offers *killing*, and
   `DemonKillPanel` sorts dead players last with a `†` as if they were poor choices
   (`NightScreen.kt:559-561`). `GameActions.resurrect` exists (`GameActions.kt:173`) but
   is only reachable from the seat sheet and only for the Professor resolver.

4. **P0 · Sequential resolution is not enforced or supported.**
   The dilemma depends on target 1's answer being announced before target 2 is woken.
   The app has no per-target sub-steps, no ordering, and no record of who answered what,
   so a storyteller mid-flow has nothing to look at.

5. **P1 · The dawn status announcement is not produced.**
   The rules require declaring, every dawn, the alive/dead status of players 1, 2 and 3
   *even when nothing changed*. The DAWN step text is the generic "Announce who died."
   (`NightOrder.kt:59`). There is no day-start briefing anywhere in the app.

6. **P1 · The `1`/`2`/`3` tokens never expire.**
   `EXPIRES_AT_DAWN` / `EXPIRES_AT_DUSK` (`GameActions.kt:218-242`) contain no
   `alhadikhia` entries. On a night where the Al-Hadikhia chooses no one, last night's
   1/2/3 tokens are still sitting in the grimoire and the ST can easily announce the
   wrong three at dawn.

7. **P1 · The "silence" rule is invisible.**
   Nothing tells the ST to declare silence at the start and its end after resolution.
   This is a table-management rule players cannot follow unless the ST announces it.

8. **P1 · "Choose no one" is not an input.**
   The ability is optional and, when unused, *no announcement is made*. The app offers
   only a "No kill" text button inside the (wrong) kill panel (`NightScreen.kt:634`),
   which leaves no record and no dawn behaviour.

9. **P1 · Protection / impairment guidance is attached to the wrong action.**
   `StatusEffects.deathNotes` is only consulted for the single kill target
   (`NightScreen.kt:588`). With three targets, each "die" answer needs its own
   protection check (Monk `Safe`, Soldier, Innkeeper `Protected`, Tea Lady, Fool,
   Lleech, Grandmother's grandchild, Scarlet Woman on a Demon death…).

10. **P1 · The Mastermind jinx is not implemented and not even present as data.**
    `night_and_jinxes.json:174` has only the Scarlet Woman jinx. The Mastermind extra day
    already has engine support (`GameState.mastermindDayActive:111`,
    `WinCheck.check:28-49`) but that machinery resolves the extra day *by execution*; the
    Al-Hadikhia jinx instead resolves the game **that night**, by whether all 3 chosen
    good players choose to live. Today the ST gets the generic Mastermind-day banner and
    a wrong resolution rule.

11. **P2 · The Princess jinx is missing from the data.**
    "If the Princess nominated & executed a player on their 1st day, no one dies to the
    Al-Hadikhia tonight." The app can derive this exactly (it records nominations with
    day, nominator, nominee and result), but has neither the jinx text nor the check.

12. **P2 · Scarlet Woman jinx text drift.**
    App: "If there are two living Al-Hadikhias, the Scarlet Woman Al-Hadikhia becomes the
    Scarlet Woman again." Wiki: "If there would be two Demons, one of which was the
    Scarlet Woman, the Scarlet Woman becomes the Scarlet Woman again."
    (`night_and_jinxes.json:174-177`.)

13. **P2 · The one show card is generic.**
    `night_guide.json:1527-1537` offers a single "DO YOU CHOOSE TO LIVE OR TO DIE?" card.
    Nothing produces the scripted line "The Al-Hadikhia has chosen <name>. Do you choose
    to live?" with the actual name filled in, nor the "silence begins/ends" cards.

14. **P3 · Step detail wording.** The step subtitle is the raw `otherNightReminder`; it
    never mentions that the choice is optional, that dead players are legal targets, or
    that a "live" answer resurrects.

## Proposed behaviour (spec)

### Night action (structured)

- **when:** other nights only (`night_and_jinxes.json:421` position is correct).
  Wake condition: the Al-Hadikhia is **alive** and not marked `exorcist:Chosen`.
  *Exception:* if the Al-Hadikhia is **dead by execution** and a living Mastermind is in
  play, the step still runs tonight (Mastermind jinx) with the target constraint below.
- **targets:** 0 **or** 3 players, **ordered**. Constraints: any seat, **alive or dead**,
  **including the Al-Hadikhia itself**, no repeats. Under the Mastermind jinx the picker
  is restricted to **good** players. Picker default/sort: seat order (clock order), with
  the current 1/2/3 holders pre-highlighted if re-opened; dead seats are *not* demoted or
  greyed out — a dead pick is a normal, powerful line.
  The panel must offer an explicit **"Chooses no one tonight"** button.
- **immediate effects (phase A — selection):**
  - place `alhadikhia:1`, `alhadikhia:2`, `alhadikhia:3` (each exclusive) on the three
    seats in the chosen order;
  - record the ordered target ids for the night so the resolution UI and the dawn
    briefing can use them (see "State" below);
  - surface a **SILENCE BEGINS** banner + full-screen card.
  - If "chooses no one": place nothing, record `chose_none` for the night, and
    suppress all announcements (including the dawn status line).
- **immediate effects (phase B — resolution, three locked sub-steps):**
  For i = 1, 2, 3 in order, the panel shows only sub-step i until it is answered:
  - Header: **"Wake <name>. Say: 'The Al-Hadikhia has chosen <name>. Do you choose to
    live?'"** with a one-tap full-screen card carrying that exact sentence.
  - Two buttons: **LIVES** and **DIES**.
    - **LIVES** → if the player is dead, `GameActions.resurrect(playerId)` (keeps the
      death record, marks it resurrected) and log "brought back by the Al-Hadikhia".
      If already alive, no state change.
    - **DIES** → show `StatusEffects.deathNotes(...)` for that player first; on confirm,
      `GameActions.kill(playerId, DeathCause.DEMON, lookup)`.
  - After the answer, show the announcement line **"The first/second/third chooses to
    live/die."** as a card, then unlock sub-step i+1.
- **immediate effects (phase C — the trap):** once all three are answered, if all three
  targets are **alive**, the panel automatically shows:
  **"All three chose to live — all three die."** with a single confirm that kills all
  three (`DeathCause.DEMON`), each with its own `deathNotes` warnings listed first.
  Then a **SILENCE ENDS** banner/card.
- **deferred effects:**
  - *Dawn / day start:* a briefing line, always shown when the ability was used:
    **"Al-Hadikhia: <name1> is alive/dead, <name2> is alive/dead, <name3> is
    alive/dead."** — emitted even when nothing changed, and emitted *before* other dawn
    death announcements (the wiki's silence period ends at night, but the status
    declaration is a dawn duty).
  - *Mastermind jinx night:* after phase C, the game ends immediately — if all 3 chose to
    live, **evil wins**; otherwise **good wins**. Route this through `WinCheck` as a
    non-dismissible advisory rather than the generic Mastermind-day banner.
- **expiry:** `alhadikhia:1|2|3` go into **`EXPIRES_AT_DUSK`**, not `EXPIRES_AT_DAWN` —
  they must survive dawn so the day-start briefing can read them, and be gone before the
  next night's selection. (`GameActions.kt:231-242`.)
- **information:** none computed (no `InfoCalc` entry needed). The only "information" is
  the public announcement text, which must be generated with real names.
- **visibility:** everything the Al-Hadikhia does is heard by the whole table. The
  Demon/Minions learn nothing extra. If a **Lunatic** believes they are the Al-Hadikhia,
  the Lunatic must be run through the *same* three-target ritual with no deaths, and the
  real Demon must be shown the Lunatic's three picks (the existing Lunatic annotation in
  `NightOrder.kt:157-172` appends text to the Demon step; it must carry the ordered trio,
  not a single name).
- **day-time inputs:** none of its own, but the **Princess jinx** consumes day data the
  app already has: if a Princess is in play and, on the Princess's first day, she
  nominated a player who was executed, then tonight the step must display
  **"PRINCESS JINX: no one dies to the Al-Hadikhia tonight"** and the DIES/all-die
  confirmations must be replaced by "no death" (the choices are still asked and
  announced — only the deaths are cancelled).
- **interactions/jinxes to handle explicitly:**
  - *Mastermind:* see above (targets restricted to good; game resolves that night).
  - *Princess:* see above.
  - *Scarlet Woman:* if the Al-Hadikhia dies and a Scarlet Woman would become a second
    Al-Hadikhia while one already exists, she reverts to Scarlet Woman. Fix the jinx text.
  - *Exorcist:* an exorcised Al-Hadikhia does not act — the step must self-disable
    (`NightOrder.kt:150-154` already appends the warning text; the panel must honour it).
  - *Drunk/poisoned Al-Hadikhia:* not covered by the wiki. Show the impairment banner and
    an explicit ST choice — "run the ritual with no deaths" vs "resolve normally" —
    rather than the current blunt "the attack fails (choose 'No kill')"
    (`NightScreen.kt:548-554`).
  - *Protection:* Monk `Safe`, Soldier, Innkeeper `Protected`, Tea Lady, Fool, Lleech,
    Grandmother's `Grandchild` — surface per target via `StatusEffects.deathNotes`; do
    not auto-cancel (the wiki does not state the interaction).

### State needed

`GameState` has nowhere to record an ordered multi-target night choice or a per-target
answer. Add a small, generic structure rather than an Al-Hadikhia-specific one:

```kotlin
@Serializable
data class NightChoice(
    val cycle: Int,
    val sourceId: String,          // "alhadikhia"
    val targetIds: List<Long>,     // ordered
    val answers: List<String> = emptyList(), // "live" / "die", parallel to targetIds
    val declinedToAct: Boolean = false,
)
// GameState.nightChoices: List<NightChoice>
```

This same field serves "different from last night" constraints for many other characters
(Leviathan's jinxed nightly pick, Balloonist, Fearmonger…), so it is worth adding once.

### UI text the step should display

- Header: **"Al-Hadikhia — choose 3 players, in order (or no one)."**
- Sub-line: *"Alive or dead. May include the Al-Hadikhia. Everyone will hear the names."*
- Before phase B: **"Declare silence. Nobody may speak until you say it has ended."**
- Per target: **"Wake <name>. 'The Al-Hadikhia has chosen <name>. Do you choose to live?'"**
- After answer: **"Announce: the first/second/third chooses to live/die."**
- Phase C: **"All three chose to live — all three die."**
- End: **"Announce that the silence has ended."**
- Day start: **"Al-Hadikhia's three: <n1> alive, <n2> dead, <n3> alive."**

### Data changes

- `night_and_jinxes.json`: add the **Mastermind** and **Princess** jinxes; correct the
  Scarlet Woman text to the wiki wording.
- `night_guide.json:1527`: replace the single show card with:
  `{"label":"Silence begins","kind":"message","text":"SILENCE — NOBODY MAY SPEAK"}`,
  `{"label":"Ask a chosen player","kind":"message","text":"THE AL-HADIKHIA HAS CHOSEN YOU. DO YOU CHOOSE TO LIVE?"}`,
  `{"label":"Silence ends","kind":"message","text":"THE SILENCE HAS ENDED"}`.
  Update the prose to state that dead players may be chosen and that a "live" answer
  brings them back.
- `characters.json:1952`: `otherNightReminder` is acceptable but should add "(they may
  choose no one; dead players may be chosen and a 'live' answer revives them)".
- `GameActions.EXPIRES_AT_DUSK`: add `"alhadikhia" to "1"`, `"2"`, `"3"`.

## Tests to add

1. **All three live ⇒ all three die.**
   *Given* an Al-Hadikhia game, night 2, three alive targets A, B, C marked 1/2/3;
   *when* all three answer "live"; *then* A, B and C are all dead with
   `DeathCause.DEMON` and three death records dated cycle 2 at night.

2. **Mixed answers kill only the "die" choosers.**
   *Given* the same setup; *when* A="die", B="die", C="live"; *then* A and B are dead,
   C is alive, and no extra deaths are recorded.

3. **A dead target who chooses "live" is resurrected, and can then die again.**
   *Given* C is dead before the night; *when* A="live", B="live", C="live"; *then* C is
   alive after the resolution phase, the all-alive rule fires, and all three end dead —
   with C holding two death records, the first marked `resurrected = true`.

4. **Choosing no one produces no deaths and no tokens.**
   *Given* the Al-Hadikhia declines; *then* no `alhadikhia:1|2|3` reminders exist, no
   death records are added, and the day-start briefing contains no Al-Hadikhia line.

5. **1/2/3 tokens survive dawn and are cleared at dusk.**
   *Given* tokens 1/2/3 placed during night 2; *when* `advancePhase` moves NIGHT→DAY;
   *then* all three are still present; *when* `advancePhase` moves DAY→NIGHT; *then*
   none remain.

6. **Dawn briefing reports unchanged statuses.**
   *Given* A alive→alive, B dead→dead (chose "die" while already dead is impossible;
   use B dead and choosing "die"), C alive→dead; *then* the generated day-start briefing
   lists all three with their current status, including the two that did not change.

7. **Mastermind jinx resolution.**
   *Given* the Al-Hadikhia died by execution on day 3 and a living Mastermind;
   *when* the night-4 step runs and all 3 chosen good players answer "live";
   *then* `WinCheck` returns `goodWins = false` with an Al-Hadikhia/Mastermind reason;
   *and* when any of the three answers "die", `goodWins = true`.

8. **Princess jinx suppresses deaths.**
   *Given* a Princess whose first day was day 1, on which she nominated a player who was
   executed; *when* the night-2 Al-Hadikhia step resolves with two "die" answers;
   *then* no death records are created and the step reports the Princess jinx.

9. **Target picker admits dead players and the Al-Hadikhia itself.**
   *Given* a grimoire with a dead seat and the Al-Hadikhia's own seat; *then* both are
   selectable targets (guards against a future "alive only" regression).
