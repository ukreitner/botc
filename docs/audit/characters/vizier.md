# Vizier (vizier) — exp (Carousel) minion

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Vizier>

Current ability text:

> "All players know you are the Vizier. You cannot die during the day.
> If good voted, you may choose to execute immediately."

`characters.json:1893` matches this text exactly — **no drift**.

### How to run (wiki, verbatim, in order)

- "When the first night has ended, declare that the Vizier is in play, and which player it is."
- "If a vote has just been tallied, and one or more good players voted, and the Vizier declares that the nominee is executed, that player is executed and dies."
- "No more nominations, votes, or executions occur today."

And: "This counts as the 1 execution allowed each day."

### Examples (verbatim)

- "The King has been nominated. Five people vote, but the Vizier does not use their ability. The Boomdandy is nominated and eight people vote. The Vizier uses their ability and the Boomdandy is executed immediately."
- "The Demon has seven votes against them, and is 'about to die'. The Vizier nominates Bill, the Barber. Two evil players and one good player vote. The Vizier declares that Bill is executed. The Demon survives today."
- "The town nominates and executes the Vizier. The Vizier does not die. That night, The Demon kills the Vizier."

Notes drawn from the examples:
- The immediate execution **does not need to reach the execution threshold** — the second example executes Bill on three votes in a game where seven was the tally to beat. The only gate is "one or more good players voted".
- The immediate execution **overrides whoever is currently on the block**.
- The Vizier surviving their own execution is a *day* immunity only; they die normally at night.

### Jinxes (wiki — all 9; **none of them are in the app's dataset**)

| Partner | Text |
|---|---|
| Alsaahir | "The Storyteller doesn't declare the Vizier is in play." |
| Courtier | "If the Vizier loses their ability, they learn this, and cannot die during the day." |
| Fearmonger | "The Vizier wakes with the Fearmonger, learns who they choose and cannot choose to immediately execute that player." |
| Investigator | "The Storyteller doesn't declare the Vizier is in play." |
| Lil' Monsta | "If the Vizier is babysitting Lil' Monsta, they die when executed." |
| Magician | "If the Vizier is in play, the Magician has no ability but is immune to the Vizier's ability." |
| Politician | "The Politician might register as evil to the Vizier." |
| Preacher | "If the Vizier loses their ability, they learn this, and cannot die during the day." |
| Zealot | "The Zealot might register as evil to the Vizier." |

The Politician/Zealot jinxes matter mechanically: "if good voted" is a **registration** question, so the app must not compute it from raw alignment when either is on the script.

The wiki page contains **no** sentence about the Vizier being drunk or poisoned, about Travellers, about exile, or about a "3 or fewer players alive" restriction — I looked specifically and found none.

## What the app does today

Data paths — **this is the complete list**:
- `characters.json:1893` — text, `firstNightReminder: "Announce which player is the Vizier."`, empty `otherNightReminder`, **no reminder tokens**.
- `night_and_jinxes.json:369` — firstNight index 74, i.e. **after** `DAWN` (index 72) and after `leviathan`. This is correct and matches "when the first night has ended". The Vizier is absent from the otherNight list, also correct.
- `night_guide.json:1467` — a `first` entry only, with instructions covering the announcement, the day immunity and the immediate-execution power, and one show card `THE VIZIER IS…` (`kind: "message"`).
- `raw_exp_evil_outsiders.json:291` — raw import copy.

There is **no** `vizier` reference anywhere in `engine/src/main/kotlin` or `app/src`.

Storyteller experience:
1. Night 1, after Dawn: a step titled "Vizier" appears with "Announce which player is the Vizier." plus the guide prose and a "Public announce" show card that reads `THE VIZIER IS…` — the Storyteller must edit the text to add the name (`GuideShowDialog`, `NightScreen.kt:366-395`). It works, but the name is not pre-filled even though `step.playerIds` holds it.
2. Every subsequent night: no Vizier step at all, so the two day-long rules are never restated.
3. Day: `DayScreen.kt` has no Vizier awareness. After recording a nomination the Storyteller sees only the standard "about to die / tie / safe" verdict; there is no "Vizier executes now" action. To do it by hand: open the nominee's seat and press "Executed" (`SeatSheet.kt:274-276`), then remember to stop taking nominations.
4. The Vizier's day immunity is not modelled: `StatusEffects.deathNotes` (`StatusEffects.kt:52-129`) knows Sailor, Soldier, Fool, Lleech, Tea Lady, Devil's Advocate and the `Safe`/`Protected` tokens, but nothing about the Vizier, so the "might be protected" confirmation (`SeatSheet.kt:288-307`) never appears and "Execute" on the Day screen (`DayScreen.kt:111-114`, `:350-357`) kills them on one tap.
5. `Nomination.voterIds` (`GameState.kt:69`) is recorded in clock order, so "did a good player vote?" **is already computable** from existing state — the hook exists, it is simply unused.

## Defects and gaps

1. **P0 · The app kills the Vizier when they are executed** — "You cannot die during the day". `DayScreen.kt:111-114` and `:350-357` call `viewModel.kill(id, DeathCause.EXECUTION)` with no check; `SeatSheet.kt:274-276` does the same; `StatusEffects.deathNotes` (`StatusEffects.kt:52-129`) offers no warning so not even the confirmation dialog fires. Repro: nominate the Vizier, record a passing tally, tap Execute — they die. The wiki's third example is exactly this case and says "The Vizier does not die."
2. **P0 · The Vizier's "execute immediately" power does not exist in the app** — this is the character's whole day identity, and there is no button, no eligibility check ("one or more good players voted"), and no enforcement of "No more nominations, votes, or executions occur today". `DayScreen.kt:217-251` records a nomination and moves on. Repro: any nomination in a Vizier game.
3. **P0 · Every day death is applied to the Vizier, not just executions** — "cannot die during the day" covers the Psychopath's public kill, a Witch curse triggering on a nomination, a Golem nomination, a Virgin's immediate execution, an exile-adjacent death, and the Storyteller's "Other death" button. All of these go straight through `SeatSheet.kt:266-268`. (Cross-referenced from `psychopath.md`.)
4. **P1 · The night-1 announcement is not pre-filled and never repeated** — the show card text is the literal string `THE VIZIER IS…` (`night_guide.json:1467`) with the name left to typing, even though `NightStep.playerIds` (`NightOrder.kt:23`) already names the holder. And because the Vizier is absent from the otherNight order, nothing reminds the Storyteller on later days.
5. **P1 · No day-start briefing** — the two rules that govern the whole day ("the Vizier cannot die today" and "after any tally with a good voter, the Vizier may execute") are stated once, on night 1, and never again. This is the exact class of failure the user reported for the Devil's Advocate.
6. **P1 · All 9 official jinxes are missing from `night_and_jinxes.json`** — `grep` finds no jinx with `vizier` in either slot. Four are mechanical:
   - **Alsaahir / Investigator**: the night-1 announcement must **not** happen — the app would tell the Storyteller to announce it anyway.
   - **Fearmonger**: the Vizier wakes with the Fearmonger (a night-order change) and cannot immediately execute the Fearmonger's target.
   - **Magician**: the Magician is immune to the immediate execution.
   - **Lil' Monsta**: a babysitting Vizier dies when executed — overriding the day immunity.
7. **P1 · "If good voted" is computed nowhere, and misregistration is not considered** — with a Politician or a Zealot on the script the answer is a Storyteller judgement call ("might register as evil to the Vizier"), and with a Spy or Recluse in play the same question arises in the other direction. `InfoCalc.misregistrations` (`InfoCalc.kt:118-131`) has this pattern for Spy/Recluse but nothing consumes it on the Day screen.
8. **P2 · The Vizier has no reminder tokens at all** (`characters.json:1893`), so there is nothing to mark "the Vizier's ability is lost" (Courtier/Preacher) or "cannot be immediately executed" (Fearmonger).
9. **P2 · The game log does not distinguish a Vizier execution** — `GameExtras.kt:53-64` would record it as a plain "executed", losing the fact that it bypassed the tally.
10. **P3 · The Vizier step sits after `DAWN` in the night sheet with no visual cue that it is a *day* announcement** — the Storyteller checking off night steps sees it as another night action.

## Proposed behaviour (spec)

### Night 1 step (announcement)

- **when**: first night only, after `DAWN`; the current night-order position (`night_and_jinxes.json:369`) is correct — keep it.
- **wake condition**: none (the Vizier does not wake). Suppress the step entirely if an **Alsaahir** or **Investigator** is in play (jinx) and instead show `Alsaahir/Investigator jinx: do NOT declare the Vizier.`
- **targets**: none.
- **immediate effects**: none.
- **show cards**: pre-fill the holder's name — `THE VIZIER IS <name>` (`kind: "message"`, name substituted from `step.playerIds`). Add a second card `<name> IS THE VIZIER` styled for the whole table if the Storyteller prefers to pass the phone.
- **visibility**: public, to everybody including the good team.

### Day-phase rules (new day-ability framework — see the cross-cutting note)

Define `DayRules.vizier(state, lookup)` returning the living Vizier holder with their ability (alive, no `No ability` token). Two consequences:

**(a) Day immunity**

- `StatusEffects.deathNotes` (`StatusEffects.kt:52-129`) gains:
  `if (id == "vizier" && state.phase == Phase.DAY) notes += "The Vizier cannot die during the day."`
  This automatically routes every day death through the existing "might be protected" confirmation (`SeatSheet.kt:256-307`) whose **default** button is "Death prevented".
- The Day screen's Execute buttons (`DayScreen.kt:111-114`, `:350-357`) must, for a living Vizier nominee, replace "Execute" with **"Execute (Vizier survives)"** which records that the day's execution happened **without** killing anyone, and then closes the day (same "execution spent" state the Psychopath needs).
- Overrides: **Lil' Monsta jinx** (babysitting Vizier dies when executed) and the Courtier/Preacher jinx note (`If the Vizier loses their ability, they learn this, and cannot die during the day` — i.e. the immunity *survives* ability loss, which the implementation must not "fix" by tying immunity to having the ability).

**(b) Execute immediately**

Attached to the nomination record flow (`DayScreen.kt:217-251`), after a tally is recorded:

- **eligibility**: a living Vizier with their ability exists **AND** `nomination.voterIds` contains at least one player who registers as **good**.
  - Compute goodness with `Player.isEvil(lookup)` (`GameState.kt:49-52`).
  - If a **Politician** or **Zealot** voted, or a **Spy**/**Recluse** voted, add the caveat `<name> might register as evil to the Vizier — your call.` and let the Storyteller toggle each ambiguous voter's registration before the check.
  - Explicitly **not** gated on reaching `executionThreshold` — the wiki's Barber example executes on 3 votes against a 7-vote block.
- **UI**: immediately under the recorded nomination row, a distinct button
  `VIZIER: execute <nominee> now` with subtitle `<n> good player(s) voted. This ends the day.`
  Suppress it (with the reason shown) when:
  - no good player voted → `No good player voted — the Vizier cannot execute this nomination.`
  - the nominee is the **Magician** → `Magician jinx: immune to the Vizier's ability.`
  - the nominee holds the Fearmonger's `Fear` token → `Fearmonger jinx: the Vizier cannot immediately execute this player.`
- **effect on tap**:
  1. `kill(nominee, DeathCause.EXECUTION)` — unless the nominee is themselves protected (`deathNotes` confirmation applies, e.g. the nominee is the Vizier or a Sailor).
  2. Mark the day's execution spent, clear any `aboutToDie` block, and close nominations for the day: the "New nomination" card becomes `The Vizier ended the day — <nominee> was executed.`
  3. Log entry: `D<n>: Vizier executed <nominee> immediately (<votes> votes, <k> good voters) — day ended.`
- **deferred effects**: none beyond the day ending.
- **expiry**: nothing; the power is unlimited (once per nomination, and it ends the day).

### Day-start briefing entries (every day)

- `Vizier (<name>) is public. They cannot die during the day.`
- `After any tally with at least one good voter, the Vizier may declare the nominee executed — that ends the day.`
- If the Vizier lost their ability (Courtier/Preacher): `<name> has lost the Vizier ability and knows it — but still cannot die during the day.`

### Nomination-time warning (`StatusEffects.nominationWarnings`, `StatusEffects.kt:132-166`)

Add: when the nominee is a living Vizier → `The Vizier cannot die during the day — an execution here uses up the day's execution but kills nobody.`

### Data changes

- `night_and_jinxes.json`: add all 9 Vizier jinxes verbatim.
- `characters.json:1893`: add reminder tokens `"No ability"` (Courtier/Preacher) — or, if inventing tokens is unwanted, rely on the generic `No ability` marker the app already places (`NightScreen.kt:204-220`).
- `night_guide.json:1467`: substitute the holder's name into the show card; add a `day` section (or a `day_briefing` field) carrying the two standing rules so a day-start briefing can render them; add the Alsaahir/Investigator suppression note.

## Tests to add

1. **Vizier survives execution** — *Given* a living Vizier on the block on day 2, *When* the execution is resolved, *Then* the Vizier is still `alive`, no `DeathRecord` for them exists, and the day's execution is marked spent.
2. **Vizier survives every day death** — *Given* the Vizier is targeted by a Psychopath day kill / a Witch-curse nomination death / a Golem nomination, *Then* `deathNotes` contains "The Vizier cannot die during the day."
3. **Vizier dies at night** — *Given* the Vizier alive on night 3, *When* the Demon kills them, *Then* they die. (Wiki example 3.)
4. **Immediate execution needs a good voter** — *Given* a tally whose `voterIds` are all evil, *Then* `DayRules.vizierCanExecute(nomination) == false`; *Given* one good voter, *Then* true.
5. **Immediate execution ignores the threshold** — *Given* 8 alive players (threshold 5), a prior nomination on the block with 7 votes, and a new nomination with 3 votes including 1 good voter, *When* the Vizier executes, *Then* the 3-vote nominee dies and the 7-vote player does not.
6. **Immediate execution ends the day** — after the above, recording a further non-exile nomination on the same cycle is rejected / the Day screen reports the day closed.
7. **Magician immunity** — *Given* the nominee is a Magician, *Then* the Vizier button is suppressed with the jinx reason.
8. **Fearmonger jinx** — *Given* the nominee holds `fearmonger`/`Fear`, *Then* the Vizier button is suppressed.
9. **Lil' Monsta jinx** — *Given* the Vizier holds the Lil' Monsta token, *When* executed, *Then* they die.
10. **Announcement suppressed by jinx** — *Given* an Alsaahir (or Investigator) in play, *When* `NightOrder.firstNight` is built, *Then* the Vizier step's detail says not to declare the Vizier.
11. **Politician caveat** — *Given* a Politician among the voters, *Then* the eligibility result carries the caveat "might register as evil to the Vizier".
