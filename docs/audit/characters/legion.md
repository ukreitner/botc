# Legion (legion) — exp demon

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Legion> (fetched 2026-08-25).

Current ability text (matches `characters.json`):

> "Each night*, a player might die. Executions fail if only evil voted. You register as a
> Minion too. [Most players are Legion]"

Summary (quoted / tightly paraphrased):

- "Legion functions as multiple Demons simultaneously."
- Setup: "The recommended number of good and evil players is the reverse of the normal" —
  "roughly seven Legion to three good players in a 10-player game". The remaining players
  are "Townsfolk or Outsiders, in any combination". The standard Demon/Minion distribution
  does **not** apply; all evil players are Legion.
- "Executions fail if only evil players voted."
- "Each Legion registers as both Minion and Demon."
- "The Storyteller selects nightly deaths."
- "Evil wins if only one good player survives."
- "Bluffs are optional."

How to Run (quoted / tightly paraphrased):

- **First night:** "During the first night's Demon Info step, have all Legion players make
  eye contact. Consider identifying non-Legion players so Legion recognizes them."
- **Each subsequent night:** "you may decide that a player dies." (It may be nobody.)
- **Voting:** "When counting votes aloud, if only evil players voted for a nominee with
  sufficient votes, declare the tally zero instead." More precisely: "If at least one good
  player voted for the nomination, and that player is 'about to die', then the execution
  happens as normal. If only evil players vote for a nomination, the vote tally for that
  nominee is zero."
- **Storyteller strategy:** "Prioritize killing Legion players most nights, aiming toward
  three final players — two good and one Legion. If players reach final day without
  executing, kill a good player that night to secure evil's victory."
- **Optional technique:** "If players attempt to identify zero-tallies through forced
  voting, declare votes successful but secretly mark who dies using the **ABOUT TO DIE**
  reminder, executing them after nominations conclude."
- **Win condition:** "If only one good player remains alive, the Storyteller may declare
  that evil wins, since good cannot win."

Examples (quoted):

- "Six Legion and the Slayer vote executing the Fortune Teller (the only other good
  player). Execution succeeds; evil wins."
- "With four alive (three Legion, one good), three Legion plus one good vote for Julian.
  Julian receives zero tally. Alex, with two votes including one from a good player, gets
  executed instead. Evil wins."

Jinxes — **eight**, all quoted from the wiki, and **none of them are in the app's data**:

- **Engineer:** "If Legion is created, all evil players become Legion. If Legion is in
  play, the Engineer starts knowing this but has no ability."
- **Hatter:** "If Legion is created, all evil players become Legion. If Legion is in play,
  the Hatter has no ability."
- **Magician:** "If the Magician is in play, during the Demon info step, Legion wake in
  separate groups. Each group learns which players are good, but does not learn the
  Magician."
- **Minstrel:** "If Legion died by execution today, Legion keeps their ability, but the
  Minstrel might learn they are Legion."
- **Politician:** "The Politician might register as evil to Legion."
- **Preacher:** "If the Preacher chooses Legion, Legion keeps their ability, but the
  Preacher might learn they are Legion."
- **Summoner:** "If Legion is summoned, all evil players become Legion."
- **Zealot:** "The Zealot might register as evil to Legion."

Night order: other nights only — `night_and_jinxes.json:409` (between `princess` and
`imp`). No first-night entry; Legion's first-night presence is at the **DEMON_INFO**
marker. Correct.

## What the app does today

Data:

- `characters.json:1982` — text matches; `setup: true`; `reminders: ["Dead","About To Die"]`
  (so the "secretly mark the real target" technique has its token, at least).
- `night_guide.json:1557` — `other` only; the prose is accurate about the ST choosing the
  kill, the "only evil voted" rule, and Minion registration. Prose only.
- `night_and_jinxes.json` — **zero** Legion jinxes.

Setup:

- `Setup.TEAM_WARPING_IDS` (`Setup.kt:72`) contains `legion`, so `modifierFor` returns
  `choiceTeams = all teams` (`Setup.kt:127-129`) and `validateBag` relaxes every count
  (`GameActions.kt:432-478`). A Legion bag therefore validates — **works**.
- `GameActions.DUPLICABLE` (`GameActions.kt:413`) contains `legion`, so the bag builder
  gives Legion a `+/-` stepper (`SetupScreen.kt:518-526`) — **works**.
- But the requirement line still reads "Need: 7 townsfolk · 0 outsiders · 2 minions ·
  1 demon" (`SetupScreen.kt:372-378`), which is exactly the distribution a Legion game
  must *not* use, and `randomBag` (`GameActions.kt:338-402`) will never produce a Legion
  game (it draws one Demon and the base Minion count).

First night:

- `MINION_INFO` (`NightOrder.kt:60-80`) is emitted whenever there are 7+ seats, even
  though a Legion game has **no Minions**: the row reads "Wake all Minions. They see each
  other, then point out the Demon (A, B, C, D, E, F, G)." with an empty holder list.
- `DEMON_INFO` (`NightOrder.kt:81-119`) reads "Wake the Demon (A, B, C, D, E, F, G).
  Point out the Minions, then show 3 not-in-play good characters as bluffs — **no bluffs
  chosen yet! Pick them from the menu**." So the app nags for bluffs that are optional and
  frames seven Legion players as "the Demon" with Minions to point out.
- Nothing tells the ST to have Legion make eye contact, and nothing lists the non-Legion
  players (the information the ST is told they may reveal).

Other nights:

- One "Legion" step lists every Legion holder (`NightOrder.kt:173-178`).
- `QuickResolutions` (`NightScreen.kt:462-525`) takes `holder = step.playerIds.first()`
  (line 467) and, at `else ->` (line 518), renders `DemonKillPanel` **only if that first
  seat is alive**. The panel is titled "Demon kill — who did <first Legion player>
  choose?" and, if that player is drunk/poisoned, claims "the attack fails"
  (`NightScreen.kt:548-554`) — none of which is true: the *Storyteller* chooses, and no
  individual Legion's impairment stops it.

Day:

- `DayScreen` (`app/.../DayScreen.kt:161-251`) records voters (`voterIds`) and computes
  the outcome with `Voting.outcome` (`GameState.kt:147-152`). There is **no** check for
  "only evil voted"; a tally of all-evil hands puts the nominee on the block normally.
- `GameActions.aboutToDie` (`GameActions.kt:296-306`) derives the block from recorded
  results, so a wrong result propagates to the dusk guard (`GameShell.kt:141-146`).

Win check:

- `WinCheck.check` (`WinCheck.kt:18-101`): "every Demon is dead ⇒ good wins" fires
  correctly when the last Legion dies. But "only one good player remains ⇒ evil wins" is
  absent; the only evil-win rule is `alive.size <= 2` (line 88), which in a Legion game is
  far too late (evil wins at, say, 4 Legion + 1 good = 5 alive).

Information:

- `InfoCalc` treats "Minion" strictly as `team == Team.MINION`
  (`InfoCalc.kt:70`, `:220-241`, `:295-305`, `:408-421`), and misregistration is
  hard-coded to Spy/Recluse only (`InfoCalc.kt:121-130`). In a Legion game the
  Investigator is told "No Minion in play", the Clockmaker "No Minion in the grimoire",
  and the Town Crier always "NO — no Minion nominated today" — all wrong, because every
  Legion registers as a Minion.

Works today: bag duplication and validation; the "every Legion dead ⇒ good wins"
advisory; the `About To Die` reminder token exists.

## Defects and gaps

1. **P0 · "Executions fail if only evil voted" is not implemented.**
   The app puts the nominee on the block from an all-evil tally and the dusk guard then
   offers to execute them. `DayScreen.kt:197-216` / `GameState.kt:147-152`.
   *Repro:* Legion game, nominate a good player, tap only Legion voters, reach the
   threshold — "X is about to die" instead of a zero tally.

2. **P0 · The Legion win condition is never detected.**
   "If only one good player remains alive, evil wins." `WinCheck.kt:88-98` only handles
   `alive.size <= 2`. A Legion game routinely ends with 4–5 alive.

3. **P0 · The nightly kill disappears once the first-seated Legion dies.**
   `QuickResolutions` gates on `holder.alive` where `holder` is
   `step.playerIds.first()` (`NightScreen.kt:467`, `:518-522`). With several Legion in
   play, killing the earliest-seated one removes the only kill tool from the step.
   *Repro:* Legion game, kill the lowest-seat Legion on night 2, then open the Legion step
   on night 3 — no kill panel.

4. **P0 · Legion does not register as a Minion anywhere.**
   Investigator, Clockmaker and Town Crier all give false information
   (`InfoCalc.kt:70`, `:220-241`, `:295-305`, `:408-421`). This is a rules break the
   storyteller is actively misled into.

5. **P1 · The Demon-info step is wrong for Legion.**
   It names Legion players as "the Demon", tells the ST to point out non-existent Minions,
   and demands bluffs that the wiki calls optional (`NightOrder.kt:81-119`). It should
   instead say "all Legion make eye contact" and list the **non-Legion** players so the ST
   can point them out.

6. **P1 · A Minion-info step is shown in a game with no Minions.**
   `NightOrder.kt:60-80` emits it whenever seats ≥ 7 regardless of whether any Minion
   exists. In a Legion game the row is pure noise (and in a Lil' Monsta game it is
   actively wrong — see `lilmonsta.md`).

7. **P1 · No setup help for the inverted distribution.**
   The bag builder's requirement line is the standard distribution, "Randomize" cannot
   produce a Legion game, and nothing suggests the recommended Legion count
   (`SetupScreen.kt:372-378`, `GameActions.kt:338-402`).

8. **P1 · The kill panel misattributes and mis-advises.**
   "Demon kill — who did <player> choose?" implies a player choice; the impairment banner
   claims the attack fails if that one Legion is drunk/poisoned
   (`NightScreen.kt:543-554`). The ST chooses freely, and "nobody dies" must be a
   first-class option, not a "No kill" text button that leaves no record.

9. **P1 · The "secret About To Die" technique is unsupported.**
   The token exists but there is no flow: declare the tally as passing, mark the real
   victim, then execute them after nominations end. The ST must fake the nomination record
   by hand, which corrupts Town Crier / Flowergirl history.

10. **P1 · No storyteller balance guidance at the kill step.**
    The wiki's explicit instruction ("kill Legion most nights, aim for two good + one
    Legion on the final day") is the single most important ST decision in a Legion game and
    appears nowhere in the UI (it is buried in `night_guide.json:1557` prose only in part).

11. **P2 · All eight Legion jinxes are missing from `night_and_jinxes.json`.**
    The Magician jinx in particular changes the first night materially (Legion wake in
    separate groups; each group learns which players are good but not the Magician).

12. **P2 · Politician / Zealot misregistration is not modelled.**
    "The Politician / Zealot might register as evil to Legion." Nothing surfaces this when
    the ST decides who Legion may be shown as good.

13. **P2 · Legion + Minstrel / Preacher retention is not surfaced.**
    "If Legion died by execution today, Legion keeps their ability, but the Minstrel might
    learn they are Legion." `StatusEffects.deathNotes:110-112` prints the Minstrel note
    only for `Team.MINION` characters, so a Legion execution shows nothing.

14. **P3 · Step title.** The step reads "Legion" followed by every holder's name; on a
    phone with seven Legion that line wraps badly and the important content (the ST's kill
    decision) is below it.

## Proposed behaviour (spec)

### Setup

- Add a **"Legion game"** affordance to the bag builder: when a Legion is added, replace
  the requirement line with
  **"Legion game — the usual distribution does not apply. Recommended: <L> Legion and
  <G> good players (Townsfolk/Outsiders in any combination)."** where
  `G = base.minions + base.demons` and `L = playerCount - G` (10 players → 7 Legion,
  3 good; this is the wiki's "reverse of the normal").
- Add a **"Fill a Legion bag"** button that adds `L` copies of Legion and randomly draws
  `G` good characters from the script.
- Keep the existing relaxed validation, but add an advisory (not an error) if the Legion
  count is far from the recommendation.

### First night (DEMON_INFO variant)

- **when:** first night, at the `DEMON_INFO` marker, whenever any seat is Legion.
- **suppress:** the `MINION_INFO` step entirely when there are no `Team.MINION` seats.
- **content:**
  - **"Wake all Legion together: <names>. Let them make eye contact."**
  - **"Non-Legion players: <names>. You may point them out so Legion knows who they are."**
    (This list is computable and is the whole point of the step.)
  - **"Bluffs are optional in a Legion game."** — do not nag when `demonBluffIds` is empty.
  - **Magician jinx:** if a Magician is in play, replace with **"Magician jinx: wake Legion
    in separate groups. Each group learns which players are good, but must not learn the
    Magician (<name>)."** and list the good players per group.

### Other nights (structured)

- **when:** other nights, whenever **at least one Legion is alive** (not "the first-listed
  Legion is alive").
- **targets:** 0 or 1 player. Any seat, alive. The picker must default-sort **Legion
  players first**, with a visible hint: *"Most nights, kill a Legion. Aim for 2 good + 1
  Legion on the final day."*
- **immediate effects:** `kill(target, DeathCause.DEMON, lookup)` after showing
  `StatusEffects.deathNotes`; or an explicit **"Nobody dies tonight"** button that records
  the decision (so the dawn briefing can say "no deaths").
- **impairment:** do **not** show the "the Demon is drunk/poisoned, the attack fails"
  banner. Legion's nightly death is a storyteller choice; at most note which Legion are
  impaired for the ST's own reasoning.
- **deferred effects:** at dawn, the standard death announcement. Additionally, when
  `aliveGoodCount == 2`, show **"One more good death and evil wins — this is the final
  day."**
- **expiry:** `legion:Dead` should follow whatever generic dawn convention the app adopts
  for kill markers; `legion:About To Die` must expire at **dusk** (it is a day-scoped
  marker for the secret-execution technique).
- **information:** none computed for Legion itself, but see Registration below.
- **visibility:** Legion players see each other on night 1 only. No bluffs required.

### Registration (cross-cutting, but Legion is the driver)

Introduce an explicit registration helper the info calculators consult instead of raw
team comparisons:

```kotlin
object Registration {
    fun registersAs(state: GameState, lookup: (String) -> Character?, p: Player): Set<Team>
    // legion            -> {DEMON, MINION}
    // recluse           -> {OUTSIDER, MINION, DEMON}   (ST choice)
    // spy               -> {MINION, TOWNSFOLK, OUTSIDER} (ST choice)
    // lil' monsta holder-> {DEMON}    (see lilmonsta.md)
    // politician/zealot -> may register evil *to Legion* only
}
```

Then: Investigator (`InfoCalc.kt:70`), Clockmaker (`:218-241`), Town Crier (`:295-305`),
Fortune Teller (`:325-342`), Sage (`:423-431`), Knight (`:433-440`), Flowergirl
(`:307-323`) all query `Registration` and list Legion under both Minion and Demon, with a
caveat line naming the ambiguity.

### Day — execution rule

- When recording a nomination, compute `onlyEvilVoted = voterIds.isNotEmpty() &&
  voterIds.all { state.player(it)?.isEvil(lookup) == true }`.
- If a Legion is in play **and** `onlyEvilVoted` **and** the raw tally would put the
  nominee on the block, then:
  - the recorded `result` is `SAFE` with `votes = 0`;
  - the UI shows, before the ST announces anything:
    **"LEGION: only evil voted — announce the tally as ZERO."**
  - the nomination record keeps the real `voterIds` (for Town Crier/Flowergirl accuracy)
    plus a `declaredVotes = 0` field so the log shows both numbers.
- Add the **secret-execution** flow: a "Declare it passed, but mark the real victim"
  action that (a) records the nomination as declared, (b) places `legion:About To Die` on
  the chosen seat, and (c) makes the dusk guard offer to execute the *marked* seat.
- The Politician/Zealot jinxes affect who counts as "evil voted" — surface them as a
  caveat, do not decide automatically.

### Win check

Add to `WinCheck.check`:

- **Evil wins** when a Legion is alive and `aliveGoodPlayers.size <= 1`, reason:
  *"Only one good player is alive and Legion remains — good can no longer win."*
  (Advisory, like every other ending.)
- Keep the existing "every Demon dead ⇒ good wins" (correct for Legion).
- Caution line when a Minstrel or Preacher is in play (Legion may keep its ability).

### UI text the step should display

- **"Legion — you decide whether anyone dies tonight."**
- **"Legion alive: <n>. Good alive: <m>."**
- **"Kill a Legion most nights. Target: 2 good + 1 Legion on the final day."**
- Day banner: **"Only evil voted — the tally is ZERO."**

### Data changes

- `night_and_jinxes.json`: add all **eight** Legion jinxes verbatim.
- `night_guide.json:1557`: add a `first` entry for the Demon-info handling (eye contact,
  point out non-Legion, bluffs optional, Magician jinx) and add the balance guidance to
  the `other` entry.
- `characters.json:1982`: no text change needed.

## Tests to add

1. **Only-evil votes produce a zero tally.**
   *Given* a Legion game, 3 alive, threshold 2; *when* a nomination is voted for by two
   Legion and no good player; *then* the recorded result is `SAFE` with declared votes 0
   and `aboutToDie(state) == null`.

2. **One good voter makes the execution stand.**
   *Given* the same nomination plus one good voter; *then* the nominee is
   `ABOUT_TO_DIE` and `aboutToDie` returns them.

3. **Zero-tally does not clear an existing block.**
   *Given* a nominee already on the block from an earlier mixed vote; *when* a later
   all-evil nomination reaches the threshold; *then* the earlier nominee is still on the
   block (a declared-zero tally is not a tie).

4. **Evil wins at one good player.**
   *Given* 4 alive: 3 Legion + 1 good; *then* `WinCheck.check` returns
   `goodWins = false` with a Legion reason.

5. **Good wins when the last Legion dies.**
   *Given* every Legion dead; *then* `goodWins = true` (regression guard for the existing
   behaviour).

6. **Legion registers as a Minion.**
   *Given* a Legion game with an Investigator; *then* `InfoCalc.compute("investigator")`
   lists Legion players as Minion candidates rather than "No Minion in play".
   *And* the Clockmaker computes a Demon→Minion distance using Legion seats.
   *And* the Town Crier answers YES when a Legion nominated today.

7. **Night kill is available while any Legion lives.**
   *Given* the lowest-seated Legion is dead and two others are alive; *then* the Legion
   night step still exposes the kill action (engine-level: the step's "actor alive"
   predicate is `any`, not `first`).

8. **"Nobody dies tonight" is recordable.**
   *Given* the ST declines the kill; *then* no death record is added for that cycle and
   the dawn briefing reports no deaths.

9. **Minion-info step is suppressed with no Minions.**
   *Given* a 10-seat Legion game with zero `Team.MINION` seats; *then* the first-night
   step list contains no `MINION_INFO` row.

10. **Demon-info step lists non-Legion players.**
    *Given* 7 Legion and 3 good; *then* the `DEMON_INFO` step's detail names the three
    non-Legion players and does not demand bluffs.

11. **`About To Die` expires at dusk.**
    *Given* the marker placed during day 3; *when* `advancePhase` runs DAY→NIGHT; *then*
    it is gone.
