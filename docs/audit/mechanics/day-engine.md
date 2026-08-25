# Day engine (nomination · voting · execution · win-check · day ledger) — mechanics

Scope: `DayScreen.kt`, the DAY branches of `GameShell.kt`, `GameActions` nomination /
vote / execution helpers, `Voting`, `WinCheck.kt`, `StatusEffects.nominationWarnings`,
and the day-side record the night engine has to read back.

This document **unifies** the day-facing requests made by the 132 character audits in
`docs/audit/characters/`. It does not repeat their per-character reasoning; where a
character rule is settled there, this file only states the engine contract that
satisfies it, and resolves the places where two audits proposed incompatible shapes.

---

## Official rules (sources)

### Nomination

- <https://wiki.bloodontheclocktower.com/Rules_Explanation> — *"To nominate a player,
  simply say who. For example: 'I nominate Bob.'"*
- <https://wiki.bloodontheclocktower.com/Glossary> — **Nomination:** *"The act of
  declaring a group vote to execute a player, which is echoed by the Storyteller."*
- <https://wiki.bloodontheclocktower.com/Glossary> — **Alive:** *"Alive players have
  their ability, may vote as many times as they wish, and **may nominate players**."*
  **Dead:** *"Dead players may only vote once more during the game."*
- <https://wiki.bloodontheclocktower.com/Rules_Explanation> — *"If you die… you may no
  longer nominate, and you have only one vote for the rest of the game."*
- Players may nominate once per day, and may be nominated once per day.

**The rule the app gets backwards:** the restriction is on **nominating**, not on
**being nominated**. Nothing in the rules stops the town nominating a dead player —
and several characters make it necessary (a Zombuul hiding behind its fake death,
Vigormortis minions, a Lleech host, or simply a town that wants an Undertaker read).

### Voting

- <https://wiki.bloodontheclocktower.com/Rules_Explanation> — *"I will put my arm out
  like this (point to Bob), and say 'Votes for Bob, starting now.' I move my hand in a
  clockwise direction… if your hand is up when I get to you, that's a vote."*
- <https://wiki.bloodontheclocktower.com/Rules_Explanation> — *"This player needs a vote
  tally of at least 50% of the living players or no execution occurs. On a tie, neither
  player is executed."*
- <https://wiki.bloodontheclocktower.com/Glossary> — **About to die:** *"The player who
  has enough votes to be executed **and more votes than any other player today**."*
- Ghost vote: a dead player has exactly one vote for the rest of the game; using it
  spends the token.

### Execution

- <https://wiki.bloodontheclocktower.com/Glossary> — **Execution:** *"The group decision
  to kill a player **other than a Traveller** during the day. There is a **maximum of
  one execution per day**."*
- An execution **happens** whether or not the executed player dies. Every
  "executed but survives" character (Devil's Advocate, Pacifist, Fool, Tea Lady, Sailor,
  Zombuul's first death, Vizier, Psychopath winning roshambo, Mayor bounce) still
  consumes the day's one execution and still counts as "an execution occurred today".
- <https://wiki.bloodontheclocktower.com/Scapegoat> — *"The Scapegoat being killed still
  counts as an execution, so no more nominations occur today."*

### Exile

- <https://wiki.bloodontheclocktower.com/Glossary> — **Exile:** *"The group decision to
  kill a Traveller during the day. There may be **any number of exiles per day**,
  including none."*
- <https://wiki.bloodontheclocktower.com/Butler> — *"Because **exiles are never affected
  by abilities**, the Butler can vote freely for an exile."* This is the general
  principle: no ability modifies an exile.
- <https://wiki.bloodontheclocktower.com/Mayor> — *"Remember that **exiles are not
  executions**."*
- **UNCERTAIN, flagged:** I could not fetch a wiki page stating the exile threshold or
  the ghost-vote treatment verbatim (`/Exile` and `/Traveller` both 404;
  `/Travellers` carries only flavour text). The app's current behaviour — threshold =
  half of **all** seats rounded up, dead may vote without spending a ghost vote — is the
  standard ruling and is consistent with "exiles are never affected by abilities", but
  an implementer should confirm against the printed rulebook before locking it in a test.

### Travellers

- <https://wiki.bloodontheclocktower.com/Glossary> — **Win:** *"Good wins when the Demon
  dies. Evil wins when there are only two alive players, **not including Travellers**."*
- <https://wiki.bloodontheclocktower.com/Mayor> — *"**Travellers count as players for
  the Mayor's victory**, so must be exiled first."*
  → Travellers are excluded from the evil-wins-at-2 count and **included** in the
  Mayor's 3-alive count. These are two different counts and the app has one.
- Travellers may nominate and may be nominated (they are exiled, not executed).

### Day-phase character rules (all quotes from the linked wiki pages)

| Character | Rule text | Affects |
|---|---|---|
| [Virgin](https://wiki.bloodontheclocktower.com/Virgin) | *"The 1st time you are nominated, if the nominator is a Townsfolk, they are executed immediately."* | nomination → auto-execution → day ends |
| [Witch](https://wiki.bloodontheclocktower.com/Witch) | *"…if they nominate tomorrow, they die. If just 3 players live, you lose this ability."* | nomination → auto-death |
| [Golem](https://wiki.bloodontheclocktower.com/Golem) | *"You may only nominate once per game. When you do, if the nominee is not the Demon, they die."* — *"the vote continues as normal"* | nomination → auto-death, vote continues |
| [Fearmonger](https://wiki.bloodontheclocktower.com/Fearmonger) | *"Each night, choose a player: if **you** nominate & execute them, their team loses."* | nomination (nominator-specific) + execution → win |
| [Gnome](https://wiki.bloodontheclocktower.com/Gnome) | *"All players start knowing a player of your alignment. You may choose to kill anyone who nominates them."* — *"the nominator dies immediately. Voting for execution still occurs afterward."* | nomination → auto-death |
| [Riot](https://wiki.bloodontheclocktower.com/Riot) | *"On day 3, Minions become Riot & nominees die but nominate an alive player immediately."* — *"No voting phase… supersedes typical one-execution-per-day structure"* | replaces the whole day |
| [Vizier](https://wiki.bloodontheclocktower.com/Vizier) | *"All players know you are the Vizier. **You cannot die during the day.** If good voted, you may choose to execute immediately."* — *"counts as the 1 execution allowed each day… No more nominations, votes, or executions occur today."* | execution + day end + day-death immunity |
| [Bishop](https://wiki.bloodontheclocktower.com/Bishop) | *"Only the Storyteller can nominate. At least 1 opposing player must be nominated each day."* | who may nominate |
| [Butcher](https://wiki.bloodontheclocktower.com/Butcher) | *"Each day, after the 1st execution, you may nominate again."* — *"The second nomination follows standard voting requirements"* | extra nomination **after** an execution |
| [Banshee](https://wiki.bloodontheclocktower.com/Banshee) | *"If the Demon kills you, all players learn this. From now on, you may nominate twice per day and vote twice per nomination."* (while **dead**) | nomination count + vote weight |
| [Gunslinger](https://wiki.bloodontheclocktower.com/Gunslinger) | *"Each day, after the 1st vote has been tallied, you may choose a player that voted: they die."* — *"this is not an execution, so the day continues"* | post-tally kill |
| [Judge](https://wiki.bloodontheclocktower.com/Judge) | *"Once per game, if another player nominated, you may choose to force the current execution to pass or fail."* | overrides the tally |
| [Butler](https://wiki.bloodontheclocktower.com/Butler) | *"…tomorrow, you may only vote if they are voting too."* — *"It is not the Storyteller's responsibility to monitor the Butler."* / Organ Grinder jinx: *"the Butler may raise their hand… but their vote is only counted if their master voted too."* | vote legality (trusted; **enforced** under Organ Grinder) |
| [Zealot](https://wiki.bloodontheclocktower.com/Zealot) | *"If there are 5 or more players alive, you must vote for every nomination."* | forced vote |
| [Organ Grinder](https://wiki.bloodontheclocktower.com/Organ_Grinder) | *"All players keep their eyes closed when voting and the vote tally is secret."* — *"do not reveal how many players voted, nor if the nominee is 'about to die'"* | whole-day display mode |
| [Voudon](https://wiki.bloodontheclocktower.com/Voudon) | *"Only you & the dead can vote. They don't need a vote token to do so. **A 50% majority isn't required.**"* | who may vote + threshold |
| [Bureaucrat](https://wiki.bloodontheclocktower.com/Bureaucrat) | *"…their vote counts as 3 votes tomorrow."* | vote weight ×3 |
| [Thief](https://wiki.bloodontheclocktower.com/Thief) | *"…their vote counts negatively tomorrow."* — *"count it as subtracting one vote"* | vote weight −1 |
| [Beggar](https://wiki.bloodontheclocktower.com/Beggar) | *"You must use a vote token to vote. If a dead player gives you theirs, you learn their alignment."* | vote legality + token transfer |
| [Scapegoat](https://wiki.bloodontheclocktower.com/Scapegoat) | *"If a player of your alignment is executed, you might be executed instead."* | execution substitution |
| [Boomdandy](https://wiki.bloodontheclocktower.com/Boomdandy) | *"If you are executed, all but 3 players die. After a 10 to 1 countdown, the player with the most players pointing at them, dies."* | execution → mass death + day end |
| [Psychopath](https://wiki.bloodontheclocktower.com/Psychopath) | *"Each day, before nominations, you may publicly choose a player: they die. If executed, you only die if you lose roshambo."* | pre-nomination window + execution |
| [Mayor](https://wiki.bloodontheclocktower.com/Mayor) | *"At dusk, if exactly three players are alive and no player was executed today, declare that the game ends and good wins."* — *"Because a tied vote means neither player is executed, good wins."* | dusk win |
| [Vortox](https://wiki.bloodontheclocktower.com/Vortox) | *"Each dusk, if no player was executed today, the game ends and the evil team wins."* | dusk loss |
| [Leviathan](https://wiki.bloodontheclocktower.com/Leviathan) | *"If more than 1 good player is executed, evil wins… After day 5, evil wins."* | execution counter + day counter |
| [Legion](https://wiki.bloodontheclocktower.com/Legion) | *"Executions fail if only evil voted."* | tally → execution validity |
| [Goblin](https://wiki.bloodontheclocktower.com/Goblin) | *"If you publicly claim to be the Goblin **when nominated** & are executed that day, your team wins."* | nomination-time claim + execution → win |
| [Saint](https://wiki.bloodontheclocktower.com/Saint) | *"If you die by execution, your team loses."* | execution → win |
| [Minstrel](https://wiki.bloodontheclocktower.com/Minstrel) | *"When a Minion dies by execution, all other players (except Travellers) are drunk until dusk tomorrow."* | execution → status |
| [Mastermind](https://wiki.bloodontheclocktower.com/Mastermind) | *"If the Demon dies by execution (ending the game), play for 1 more day. If a player is then executed, their team loses."* | execution → extra day |
| [Damsel](https://wiki.bloodontheclocktower.com/Damsel) | *"If a Minion publicly guesses you (once), your team loses."* | day statement → win |
| [Klutz](https://wiki.bloodontheclocktower.com/Klutz) | *"When you learn that you died, publicly choose 1 alive player: if they are evil, your team loses."* | day statement → win |
| [Alsaahir](https://wiki.bloodontheclocktower.com/Alsaahir) | *"Each day, if you publicly guess which players are Minion(s) and which are Demon(s), good wins."* | day statement → win |
| [Atheist](https://wiki.bloodontheclocktower.com/Atheist) | *"The Storyteller can break the game rules, and if executed, good wins, even if you are dead."* | execution of the ST → win |
| [Evil Twin](https://wiki.bloodontheclocktower.com/Evil_Twin) | *"If the good player is executed, evil wins. Good can't win if you both live."* | execution → win |
| [Heretic](https://wiki.bloodontheclocktower.com/Heretic) | *"Whoever wins, loses & whoever loses, wins, even if you are dead."* | inverts every advisory |
| [Politician](https://wiki.bloodontheclocktower.com/Politician) | *"If you were the player most responsible for your team losing, you change alignment & win, even if dead."* | end-of-game ST call |
| [Summoner](https://wiki.bloodontheclocktower.com/Summoner) | *"On the 3rd night, choose a player: they become an evil Demon of your choice. [No Demon]"* | pre-night-3 there is **no Demon** — the "all Demons dead" advisory must not fire |
| [Zombuul](https://wiki.bloodontheclocktower.com/Zombuul) | *"The 1st time you die, you live but register as dead."* | fake death breaks "all Demons dead" |
| [Gossip](https://wiki.bloodontheclocktower.com/Gossip) / [Juggler](https://wiki.bloodontheclocktower.com/Juggler) / [Savant](https://wiki.bloodontheclocktower.com/Savant) / [Artist](https://wiki.bloodontheclocktower.com/Artist) / [Slayer](https://wiki.bloodontheclocktower.com/Slayer) / [Mezepheles](https://wiki.bloodontheclocktower.com/Mezepheles) / [Moonchild](https://wiki.bloodontheclocktower.com/Moonchild) / [Undertaker](https://wiki.bloodontheclocktower.com/Undertaker) / [Cannibal](https://wiki.bloodontheclocktower.com/Cannibal) | day-time public statements/choices that a **night step consumes** | the day ledger |

---

## What the app does today

**Works, one line each:** the clock-order vote list (`DayScreen.kt:167-172`); the
about-to-die derivation from the nomination sequence including ties
(`GameActions.aboutToDie`, `GameActions.kt:296-306`, tested at
`GameActionsTest.kt:186-205`); the execution and exile thresholds
(`Voting.executionThreshold`/`exileThreshold`, `GameState.kt:134-139`, tested at
`GameActionsTest.kt:59-65`); ghost-vote spending on `Record` for non-exile votes
(`DayScreen.kt:232-240`); the dusk guard that notices someone is still on the block
(`GameShell.kt:141-146`, `:592-617`); the exile path recording `DeathCause.EXILE`
and being excluded from `highestVotesToday` / `aboutToDie` / the one-nomination
counters (`GameActions.kt:278-306`); the Undertaker reading today's execution
(`InfoCalc.kt:281-293`); the Town Crier reading today's nominators
(`InfoCalc.kt:295-300`).

**The shape of the day today:**

- `DayScreen.kt` is 360 lines and is *entirely* nominations, votes and executions.
  There is no other day surface anywhere in the app.
- Nomination entry (`DayScreen.kt:126-255`): two chip rows, then a vote chip row, then
  a `Record` button that appends a `Nomination` and spends ghost votes.
  Eligibility is expressed **only** as Compose `enabled` predicates:
  - nominator `p.alive && !GameActions.hasNominatedToday(state, p.id)` (`:135-138`)
  - nominee `p.alive && !GameActions.hasBeenNominatedToday(state, p.id)` (`:146`)
  - and both are escapable — `enabled(p) || selected == p.id` (`:299`).
  `GameActions.recordNomination` (`GameActions.kt:274-275`) applies no guard at all.
- Rule triggers at nomination time are four advisory **strings**
  (`StatusEffects.nominationWarnings`, `StatusEffects.kt:132-166`) rendered as red text
  (`DayScreen.kt:154-159`). Nothing is automated.
- The tally is `orderedVoterIds.size` — a raw headcount (`DayScreen.kt:172`, `:204`).
- Executions are `viewModel.kill(id, DeathCause.EXECUTION)` at four call sites:
  `DayScreen.kt:111-114` (block banner), `DayScreen.kt:350-357` (per-nomination row),
  `GameShell.kt:599-604` (dusk guard), `SeatSheet.kt:274` (seat sheet). Only the last
  consults `StatusEffects.deathNotes` (`SeatSheet.kt:256-307`).
- `GameActions.kill` (`GameActions.kt:136-156`) is the only thing that writes history,
  so "an execution happened" is only ever inferable from a `DeathRecord`.
- `WinCheck.check` (`WinCheck.kt:18-101`) implements exactly four conditions and is
  invoked from `remember(state.players, state.phase)` (`GameShell.kt:506-508`).
- The day log (`GameExtras.GameLogDialog`, `GameExtras.kt:46-106`) renders deaths and
  nominations only.
- Traveller day characters are fully present in data and completely unused in code:
  `characters.json` carries `bishop` (`Nominate good`/`Nominate evil`), `bureaucrat`
  (`3 votes`), `thief` (`Negative vote`), `gnome` (`Amigo`), `judge` (`No ability`),
  `voudon`, `butcher`, `gunslinger`, `scapegoat`, `beggar`, `deviant`, `gangster`,
  `matron`, `apprentice`, `barista`, `harlot` — a `grep` for any of those ids across
  `engine/src/main` and `app/src` returns **zero** hits.
- The same is true of every day-input character: `gossip`, `juggler`, `savant`,
  `artist`, `alsaahir`, `yaggababble`, `damsel`, `klutz`, `mezepheles`, `psychopath`,
  `cannibal`, `banshee`, `zealot`, `organgrinder`, `vizier`, `boomdandy`, `riot`,
  `politician`, `summoner`, `atheist`, `eviltwin`, `leviathan`, `legion` — none appear
  in any Kotlin file (only `moonchild` and `minstrel` appear, as `deathNotes` strings,
  `StatusEffects.kt:98`, `:110-112`). `InfoCalc.supports` (`InfoCalc.kt:29-44`) does
  not include `gossip`, `juggler`, `savant`, `artist` or `alsaahir`, so their night
  steps have nothing to compute from either.

**The storyteller's experience.** At dusk the ST taps `Execute`. The player dies. No
protection is checked, no ability fires, nothing is announced, nothing is recorded
beyond a death row, and the app will happily let them execute a second player the same
day. If nobody is executed the ST taps `No execution` and the app forgets it happened —
which is the exact input the Mayor, the Vortox, the Zombuul and the Godfather all need.

---

## Defects and gaps

### Nomination

1. **P0 · Dead players cannot be nominated.** `DayScreen.kt:146` gates the nominee row
   on `p.alive`. The rules restrict *nominating*, not *being nominated*
   ([Glossary](https://wiki.bloodontheclocktower.com/Glossary)). This removes the
   town's only route to executing a Zombuul hiding behind its fake death, and blocks
   ordinary Undertaker-fishing on a corpse. *Repro:* Day tab → any dead seat's chip in
   the Nominee row is greyed out.
2. **P0 · No nomination-time interceptor.** Virgin, Witch, Golem, Gnome and Fearmonger
   all resolve *at the moment of nomination* and all the app does is print a sentence
   (`StatusEffects.kt:132-166` → `DayScreen.kt:154-159`). The ST must switch to the
   Grimoire tab, open the seat sheet, kill by hand, and remember that the day is now
   over. Nothing marks the day over. *Repro:* nominate a Virgin — a red line appears,
   the vote panel opens as normal, `Record` works, and the dusk guard will later offer
   to execute a second player.
3. **P0 · Fearmonger warning ignores the nominator.** `StatusEffects.kt:158-160` fires
   on *any* nomination of the Fear-marked player. The rule is *"if **you** nominate &
   execute them"*. The warning is therefore wrong in the common case and misleads the
   ST into ending the game. The data is already available — `nominatorId` is a
   parameter of the same function (`StatusEffects.kt:135`).
4. **P0 · One-nomination rules are UI-only and escapable.** `hasNominatedToday` /
   `hasBeenNominatedToday` (`GameActions.kt:285-289`) are consulted only by chip
   `enabled` predicates, which are overridden by `enabled(p) || selected == p.id`
   (`DayScreen.kt:299`). `recordNomination` (`GameActions.kt:274`) accepts anything.
   There is also no way to express the legal exceptions (Butcher, Banshee, Riot,
   Bishop), so the correct rule and the correct exceptions are both unavailable.
5. **P1 · `NominationResult.WITHDRAWN` is unreachable.** Declared
   (`GameState.kt:59`), rendered (`DayScreen.kt:343`, `GameExtras.kt:72`), and never
   produced: `Voting.outcome` (`GameState.kt:147-152`) cannot return it and
   `DayScreen.kt:197-205` never passes it. A withdrawn nomination still consumes both
   players' once-per-day rights, so it must be recordable.
6. **P1 · Bishop / Butcher / Banshee / Gunslinger / Judge / Riot have no support at
   all.** Their reminder tokens exist in `characters.json` and are never placed or read.
   Bishop inverts *who may nominate*; Butcher and Banshee add nominations; Riot replaces
   the day.
7. **P2 · Warnings are shown in one place only.** `nominationWarnings` renders inside
   the "New nomination" card (`DayScreen.kt:154-159`) and nowhere else — not on the
   about-to-die banner (`DayScreen.kt:93-115`), not in the execute confirmation, not in
   the dusk guard (`GameShell.kt:592-617`), which are the moments the ST actually acts.
8. **P2 · The nominator is lost by execution time.** `Nomination.nominatorId` is stored
   (`GameState.kt:65`) but the Execute buttons take only a player id
   (`DayScreen.kt:111-114`), so the Fearmonger trigger and the Psychopath's roshambo
   opponent cannot be resolved from the execution.

### Voting

9. **P0 · The tally is a headcount; no vote weights exist.** `orderedVoterIds.size`
   (`DayScreen.kt:172`, `:204`). Bureaucrat (×3), Thief (−1), Banshee (×2) and Voudon
   ("a 50% majority isn't required") are unrepresentable. `Nomination.votes`
   (`GameState.kt:66`) is likewise an `Int` with no record of how it was reached.
10. **P0 · Organ Grinder: the Day screen structurally leaks the tally and the block.**
    The live count (`DayScreen.kt:174-178`), the verdict line (`:206-216`), the block
    banner (`:93-115`) and the nomination history (`:339-348`) are all unconditionally
    visible. Under the Organ Grinder the ST must reveal *none* of it until nominations
    close. There is no display mode to switch to.
11. **P1 · Butler restriction not modelled.** Only the `("butler","Master")` token
    exists (`GameActions.kt:235`). Trusted-to-the-player is correct per the wiki, but
    the Organ Grinder jinx makes it **Storyteller-enforced**, and in every game a
    vote-time hint ("Ben's master has not voted") is free given the token is in state.
12. **P1 · Zealot never forced or flagged.** With 5+ alive the Zealot must vote on
    every nomination; nothing reminds the ST, and a missing Zealot hand is exactly the
    kind of thing that decides a game.
13. **P1 · Voudon / Beggar vote *legality* unmodelled.** Under a sober Voudon, living
    non-Voudon players may not vote at all and dead players vote without spending a
    ghost vote — the opposite of `DayScreen.kt:184`'s
    `canVote = p.alive || !p.ghostVoteUsed || isExile`.
14. **P1 · Judge has no hook.** A once-per-game forced pass/fail cannot be recorded, and
    "the nominee receives zero votes" cannot be expressed given `result` is derived from
    the tally.
15. **P1 · Gunslinger has no hook.** "After the 1st vote has been tallied, choose a
    player that voted: they die" needs the voter list of the day's first non-exile
    nomination, which is stored (`Nomination.voterIds`) and never surfaced.
16. **P1 · Boomdandy's countdown has no timer.** `Timer.kt:88` offers only silent
    1m/2m/5m presets; the Boomdandy needs a visible per-second 10→1 countdown.
17. **P2 · A recorded nomination cannot be corrected.** `Record` is one-shot
    (`DayScreen.kt:217-247`); the only remedy is global undo, which also unwinds the
    ghost votes and anything else done since.
18. **P2 · "Safe" is overloaded.** `Voting.outcome` correctly returns `SAFE` both for
    "below threshold" and "at/above threshold but below today's highest"
    (`GameState.kt:147-152`), but `DayScreen.kt:211` prints "X is safe" for both. The
    second case is materially different information for the ST and the table.
19. **P2 · Ghost votes are spent only via `Record`.** If the ST executes from the seat
    sheet or abandons a half-entered nomination the tokens desync; and a dead voter's
    chip is disabled the moment the token is spent (`DayScreen.kt:184`) so the ST
    cannot even record that a hand went up illegally.

### Execution

20. **P0 · Three of the four execute call sites never consult `deathNotes`.**
    `DayScreen.kt:111-114`, `DayScreen.kt:350-357` and `GameShell.kt:599-604` call
    `viewModel.kill(...)` directly; only `SeatSheet.kt:256-307` checks. The dusk-guard
    path (`GameShell.kt:599-604`) is the one the ST actually uses, so *every*
    execution-time rule — Devil's Advocate protection, Fool, Sailor, Tea Lady, Pacifist,
    Zombuul first death, Saint, Goblin, Minstrel, Boomdandy, Mastermind — is silently
    skipped. This is the single highest-leverage defect in the day engine.
21. **P0 · "Executed but survived" is unrepresentable.** An execution exists only as a
    `DeathRecord` written inside `GameActions.kill` (`GameActions.kt:136-156`), and
    `SeatSheet.kt:303-305`'s "Death prevented" branch writes **nothing**. Consequences:
    the Vortox sees "no execution today" and wins; the Mayor sees "no execution" and
    wins; the Leviathan miscounts; the Goblin, Saint, Fearmonger and Boomdandy never
    fire; the Undertaker/Cannibal source is a death rather than an execution.
22. **P0 · No "one execution per day" enforcement.** Nothing marks the day's execution
    spent. After executing, the "New nomination" card is still live
    (`DayScreen.kt:126-255`) and the dusk guard (`GameShell.kt:141-146`) will offer to
    execute whoever is on the block — a second execution.
23. **P0 · "No execution today" is never recorded.** `GameShell.kt:608-612`'s
    `No execution` button calls `advancePhase` and forgets. That button *is* the
    Vortox's evil win, the Mayor's good win, the Zombuul's night trigger and the
    Godfather's non-trigger.
24. **P0 · No dusk hook.** `advancePhase` (`GameActions.kt:258-263`) goes DAY→NIGHT with
    no rule evaluation, and `GameShell.requestPhaseAdvance` (`GameShell.kt:126-168`)
    checks only the block guard. Every "at dusk" rule in the game is therefore missing.
25. **P1 · Travellers can be executed.** `SeatSheet.kt:274`'s "Executed" button works on
    a traveller seat; an execution is by definition *"the group decision to kill a player
    other than a Traveller"*.
26. **P1 · Scapegoat unsupported.** No way to record "A was about to die, the Scapegoat
    died instead, and it still counts as A's execution".
27. **P1 · Undertaker/Cannibal source will be wrong the moment (21) is fixed.**
    `InfoCalc.kt:281-285` reads `state.deaths`; once executions can be survived it must
    read the execution record filtered to "actually died", and the Cannibal (no code
    at all) needs the same source plus the executee's alignment.
28. **P2 · `DeathCause` cannot distinguish day kills.** `GameState.kt:75` has
    `EXECUTION, DEMON, OTHER_NIGHT_DEATH, EXILE, STORYTELLER`. A Slayer shot, a
    Gunslinger kill, a Witch curse, a Golem nomination, a Psychopath kill and an
    ST-fiat death all collapse into `STORYTELLER`, and the log prints "died
    (storyteller)" for all of them (`GameExtras.kt:58`).

### WinCheck

29. **P0 · Only four conditions exist.** `WinCheck.kt:18-101` implements: Mastermind
    day, executed Saint, all Demons dead, ≤2 alive. Table of what is missing:

    | Condition | Rule | Status |
    |---|---|---|
    | Demon dead | good wins | ✅ `WinCheck.kt:70-86` |
    | ≤2 alive | evil wins | ⚠️ `WinCheck.kt:88-98` — gated on `aliveDemons.isNotEmpty()`, which is wrong for a hidden Zombuul / Lil' Monsta / pre-summon Summoner |
    | Saint executed | evil wins | ⚠️ `WinCheck.kt:51-68` — ignores `resurrected`, and will be wrong once "executed but survived" exists |
    | Mastermind extra day | either | ✅ `WinCheck.kt:28-49` |
    | Scarlet Woman | caution only | ⚠️ `WinCheck.kt:72-74` — advisory text, never applied |
    | Mayor 3-alive no-execution | good wins | ❌ missing (caution string only, `WinCheck.kt:90-92`) |
    | Vortox no-execution | evil wins | ❌ missing |
    | Goblin claimed + executed | evil wins | ❌ missing |
    | Fearmonger nominated + executed | nominee's team loses | ❌ missing |
    | Evil Twin good twin executed | evil wins; good can't win while both live | ❌ missing |
    | Heretic | inverts the result | ❌ missing |
    | Atheist | ST executed → good wins; no evil-win conditions apply | ❌ missing |
    | Damsel guessed by a Minion | good loses | ❌ missing |
    | Klutz choice is evil | good loses | ❌ missing |
    | Alsaahir correct guess | good wins | ❌ missing |
    | Leviathan: 2nd good executed / after day 5 | evil wins | ❌ missing |
    | Legion: execution fails if only evil voted | — | ❌ missing |
    | Riot day 3 resolution | either | ❌ missing |
    | Politician alignment flip at game end | ST call | ❌ missing |
    | Summoner in play, no Demon yet | must suppress "all Demons dead" | ❌ missing |
    | Zombuul fake death | must suppress "all Demons dead" | ❌ missing |
    | Boomdandy explosion | must suppress the advisory mid-procedure | ❌ missing |
    | Vizier alive at ≤3 | evil wins (script-dependent clause; **UNCERTAIN**, the wiki page I fetched does not carry it) | ❌ missing |

30. **P0 · Traveller filtering is applied to the wrong count.** `WinCheck.kt:19` strips
    travellers from `players` for everything. Correct per the Glossary for the
    evil-wins-at-2 rule; **wrong** for the Mayor, where the wiki says *"Travellers count
    as players for the Mayor's victory"*. Two counts are needed, not one.
31. **P1 · The advisory never re-evaluates on nominations.**
    `remember(state.players, state.phase)` (`GameShell.kt:506-508`) omits
    `state.nominations`, `state.deaths`, `state.cycle` and `state.mastermindDayActive`,
    so nomination- and execution-driven endings can be missed until an unrelated seat
    edit happens to invalidate the key.
32. **P1 · The advisory has no rule identity.** `Advisory` (`WinCheck.kt:10-16`) carries
    only prose, so `dismissedAdvisory` (`GameShell.kt:101`, `:509`) dedupes on the
    *reason string* — an advisory whose wording changes re-fires, and one whose wording
    repeats for a different reason is suppressed.

### The day ledger (the user's Gossip complaint)

33. **P1 · There is no way to record anything said during the day.** The only writable
    prose is `storytellerNotes` — one game-wide string behind an overflow menu
    (`GameState.kt:112`, `GameShell.kt:685-706`) — and `Player.note`
    (`GameState.kt:31`, `SeatSheet.kt`). The user asked, verbatim, to *"make it easy to
    write down all the gossips even if Gossip isn't in play."* Nothing in the Day tab
    does this. It is the same gap for Juggler guesses, Slayer shots and claims, Savant
    visits, Artist questions, Alsaahir guesses, Damsel guesses, Goblin claims, Moonchild
    and Klutz choices, Mezepheles's word, madness compliance and ordinary character
    claims.
34. **P1 · There is no day-side ability surface.** `NightScreen` has `QuickResolutions`
    per character; `DayScreen` has nothing equivalent, so a day-only character
    (Slayer, Gossip, Artist, Savant, Alsaahir, Psychopath, Damsel, Klutz, Moonchild)
    has no home in the app at all — including no way to mark a once-per-game ability
    spent, because the "Mark spent" chip lives on a night step (`NightScreen.kt:263-279`).
35. **P1 · Night steps that consume day input have nothing to consume.** `Gossip`,
    `Juggler`, `Savant`, `Artist`, `Alsaahir` are not in `InfoCalc.supports`
    (`InfoCalc.kt:29-44`) and no day record exists for them to read.
36. **P2 · No announcement queue.** Deaths at dawn, Banshee's awakening, "the Vizier is
    X", Fearmonger's "a new player was chosen", Leviathan's "I am in play" — the ST
    owes the table specific sentences at specific moments and the app tracks none of them.
37. **P2 · The game log is thin.** `GameExtras.kt:46-106` merges deaths and nominations
    only, with no causal link (which nomination produced which execution), no
    statements, and no "no execution today" row.

---

## Proposed behaviour (spec)

### Design decisions that resolve the audits' disagreements

The character audits proposed several incompatible shapes for the same concept. These
are the rulings this spec adopts, so implementers do not have to re-litigate them.

| Concept | Proposals | Adopted |
|---|---|---|
| "an execution happened" | `ExecutionRecord(day, playerId, died, nominatorId)` (boomdandy, adopted by goblin+fearmonger); `executionsByDay: Map<Int, Long?>` (vortox); `executionSpentDay: Int?` (psychopath); `executionUsedToday: Boolean` / derive from deaths (virgin, mayor) | **`ExecutionRecord` list.** The `derive from deaths` variants are wrong: an execution that kills nobody must still count, which is exactly what Vortox, Mayor, Leviathan, Goblin and Boomdandy hinge on. |
| survived vs died | `died: Boolean` (boomdandy) vs `ExecutionOutcome` enum (pacifist) vs `sealed interface ExecutionOutcome { Dies, Survives }` (devilsadvocate) | **`enum ExecutionOutcome { DIED, SURVIVED, NO_EXECUTION }`** on the record, so a no-execution day is a first-class row rather than an absence. |
| execute funnel signature | `execute(state, playerId, nominatorId, lookup)` vs `execute(state, playerId, nominatorId, died)` | **All four**: `nominatorId`, `outcome`, `preventedBy`, `lookup`, plus `via` and `scapegoatId`. |
| day-end flag | `dayEnded` (boomdandy) / `executionSpentDay` (psychopath) / `executionUsedToday` (virgin) | **Two separate concepts**, because they genuinely differ: `executionSpent(state)` is *derived* from `executions`; `nominationsClosedOnDay: Int?` is *stored*, because a Virgin/Vizier/Boomdandy/Judge day ends by rule while a Butcher day continues past an execution. |
| day ledger | `PublicStatement`+`StatementKind` (gossip); `DayAction(day, actorId, kind, targetId, text, bluff)` (slayer); `DayRecord(sourceId, kind, textA, textB, trueSide)` (savant); `DayStatement(speakerId, characterId, demonIds, minionIds, correct)` (alsaahir) | **One `DayEntry`** that is a superset: free `text` always, plus optional typed payload (`subjectIds`, `subjectIdsB`, `characterIds`, `textB`, `count`, `truth`, `bluff`, `resolvedOnNight`). Neither original was a superset; four record types for one list is not maintainable. |
| announcements | separate `Announcement(day, text, delivered)` (fearmonger) | **Folded into `DayEntry`** as `kind = ANNOUNCEMENT` + `announcePending: Boolean`. One list, one log dialog, one undo history. |
| nomination triggers | structured `NominationTrigger` (virgin) vs more `notes += "…"` strings (goblin, psychopath, vizier, fearmonger) | **Structured.** Strings cannot automate a death or end a day, which is the whole point of the user's complaint. `nominationWarnings` becomes a thin `map { it.headline }` shim so nothing else breaks during migration. |
| nominee liveness | dead nominees allowed (zombuul) vs current alive gate (virgin) | **Dead nominees allowed.** The virgin audit's "enforced by construction" refers to the *nominator* gate, which stays. |
| day-phase predicate home | `DayRules` (organgrinder, vizier), `WinCheck.duskCheck` (mayor), `Deaths.kt` (zombuul) | **`DayRules` owns day predicates** (who may nominate/vote, vote weights, thresholds, secret voting, day-closed). **`WinCheck` owns endings** and gains `duskCheck`. No `Deaths.kt`. |
| Mayor/Vortox dusk collision | vortox flags it, mayor ignores it | **Ordered dusk rules** with the Vortox before the Mayor, and an explicit collision caution in the dialog. There is no official jinx; the ST decides. |

### A. Engine types

All fields default, so existing saves deserialise unchanged.

```kotlin
// ---------- GameState additions ----------
@Serializable
data class GameState(
    // … existing …
    /** Every execution, including days on which nobody was executed. */
    val executions: List<ExecutionRecord> = emptyList(),
    /** Everything said or publicly done during a day, plus owed announcements. */
    val dayLog: List<DayEntry> = emptyList(),
    val nextDayEntryId: Long = 1,
    /** Cycle whose nominations a rule has forcibly closed (Virgin, Vizier, Psychopath, Boomdandy, Judge-pass, Riot end). */
    val nominationsClosedOnDay: Int? = null,
    val nominationsClosedReason: String = "",
) {
    /** Alive seats INCLUDING travellers — the Mayor's count. */
    val aliveCountWithTravellers: Int get() = players.count { it.alive }
    /** Alive seats EXCLUDING travellers — the evil-wins-at-2 count. */
    val aliveCountResidents: Int get() = players.count { it.alive && !it.isTraveller }
}
```

```kotlin
// ---------- Execution ----------
@Serializable
enum class ExecutionOutcome { DIED, SURVIVED, NO_EXECUTION }

/** How the execution was decided — for the log and for rules that bypass the tally. */
@Serializable
enum class ExecutionVia { VOTE, VIRGIN, VIZIER, JUDGE, PSYCHOPATH, RIOT, STORYTELLER }

@Serializable
data class ExecutionRecord(
    val day: Int,
    val outcome: ExecutionOutcome,
    /** Null only when outcome == NO_EXECUTION. */
    val playerId: Long? = null,
    /** Who nominated them — Fearmonger, Psychopath roshambo, Town Crier, the log. */
    val nominatorId: Long? = null,
    /** Nomination this execution resolved, when there was one. */
    val nominationIndex: Int? = null,
    /** Character credited with the save: "devilsadvocate", "pacifist", "fool",
     *  "sailor", "tealady", "vizier", "zombuul", "psychopath", "mayor";
     *  "" for a bare Storyteller decision. Only meaningful for SURVIVED. */
    val preventedBy: String = "",
    /** Seat that died instead (Scapegoat). The execution still belongs to [playerId]. */
    val diedInsteadId: Long? = null,
    val via: ExecutionVia = ExecutionVia.VOTE,
    /** Snapshots so later character/alignment changes cannot rewrite history. */
    val characterIdAtExecution: String? = null,
    val wasEvilAtExecution: Boolean? = null,
    val abilityImpairedAtExecution: Boolean? = null,
    /** Weighted tally and threshold at the moment of the decision. */
    val tally: Int = 0,
    val threshold: Int = 0,
)
```

```kotlin
// ---------- Day ledger ----------
@Serializable
enum class DayEntryKind {
    CLAIM,           // "I am the Empath" — the everyday case, works with no character in play
    GOSSIP,          // the Gossip's daily public statement
    JUGGLER,         // day-1 juggle guesses
    SLAYER,          // a Slayer shot (real or bluffed)
    ARTIST,          // yes/no question + answer
    FISHERMAN,       // advice request
    SAVANT,          // two statements, one true
    NIGHTWATCHMAN,
    ALSAAHIR,        // Minion/Demon guess
    AMNESIAC,        // guess + cold/warm/hot/bingo
    DAMSEL_GUESS,    // a Minion publicly guessing the Damsel
    GOBLIN_CLAIM,    // "I am the Goblin", said when nominated
    MOONCHILD, KLUTZ,// on-death public choices
    MEZEPHELES,      // the secret word was said
    PSYCHOPATH,      // the pre-nomination kill
    YAGGABABBLE,     // phrase said, count
    JUDGE, GUNSLINGER,
    MADNESS,         // Cerenovus / Mutant / Harpy compliance
    ANNOUNCEMENT,    // something the ST owes the table
    OTHER,           // free-text memo
}

@Serializable
enum class DayEntryTruth {
    UNJUDGED, TRUE, FALSE,
    /** Savant/Artist two-sided entries. */
    A_TRUE, B_TRUE, BOTH_TRUE, NEITHER_TRUE,
}

@Serializable
data class DayEntry(
    val id: Long,
    /** state.cycle when recorded. */
    val day: Int,
    val kind: DayEntryKind = DayEntryKind.OTHER,
    /** Seat that said/did it. Null = a Storyteller memo or announcement. */
    val speakerId: Long? = null,
    /** Character ability this belongs to ("gossip", "slayer", …). "" for a plain claim. */
    val sourceId: String = "",
    /** ALWAYS present, ALWAYS enough on its own. The whole ledger must work as
     *  free text with everything else empty — that is the user's actual request. */
    val text: String = "",
    /** Second half of two-sided entries: Savant statement B, Artist's answer. */
    val textB: String = "",
    /** Seats the entry is about. Per-kind: SLAYER target, KLUTZ/MOONCHILD choice,
     *  ALSAAHIR demons, DAMSEL_GUESS the guessed seat, GOSSIP subjects. */
    val subjectIds: List<Long> = emptyList(),
    /** Second seat list where a kind needs two: ALSAAHIR minions, JUGGLER seats. */
    val subjectIdsB: List<Long> = emptyList(),
    /** Characters named: JUGGLER guesses (parallel to subjectIdsB), CLAIM, DAMSEL_GUESS. */
    val characterIds: List<String> = emptyList(),
    /** Storyteller verdict, where a rule needs one. */
    val truth: DayEntryTruth = DayEntryTruth.UNJUDGED,
    /** Integer payload: Juggler correct count, Yaggababble phrase count. */
    val count: Int? = null,
    /** The speaker is bluffing this character (a fake Slayer shot, a fake Gossip). */
    val bluff: Boolean = false,
    /** Night cycle that consumed this entry, once resolved. */
    val resolvedOnNight: Int? = null,
    /** True while the ST still owes the table this sentence. */
    val announcePending: Boolean = false,
)
```

```kotlin
// ---------- Nomination (extended; existing fields unchanged) ----------
@Serializable
data class Nomination(
    val day: Int,
    val nominatorId: Long,
    val nomineeId: Long,
    /** WEIGHTED tally — what the rules use. Unchanged meaning for existing saves. */
    val votes: Int = 0,
    /** Hands raised, clock order from the nominee's left. */
    val voterIds: List<Long> = emptyList(),
    val result: NominationResult = NominationResult.SAFE,
    val isExile: Boolean = false,
    // new:
    /** Threshold at the moment of the tally (Voudon days differ). */
    val threshold: Int = 0,
    /** Why the tally differs from voterIds.size — one line per modifier applied. */
    val tallyNotes: List<String> = emptyList(),
    /** The nominee publicly claimed Goblin before votes were called. */
    val goblinClaim: Boolean = false,
    /** A Judge forced the outcome. */
    val judgeForced: JudgeForce? = null,
    /** Ability triggers that fired on this nomination, for the log. */
    val triggersFired: List<String> = emptyList(),
)

@Serializable
enum class JudgeForce { PASS, FAIL }
```

```kotlin
// ---------- Nomination pre-flight ----------
@Serializable
enum class TriggerKind {
    /** The engine kills someone the moment the nomination is declared. */
    AUTO_DEATH,
    /** The engine executes someone immediately (consumes the day's execution). */
    AUTO_EXECUTION,
    /** No more nominations today. */
    END_DAY,
    /** Changes how this vote is tallied or who may vote. */
    VOTE_MODIFIER,
    /** The Storyteller must decide something before votes are called. */
    CHOICE,
    /** Information only. */
    WARN,
}

@Serializable
data class TriggerOption(val id: String, val label: String, val isDefault: Boolean = false)

@Serializable
data class NominationTrigger(
    val kind: TriggerKind,
    /** Character whose ability this is. */
    val sourceId: String,
    val actorId: Long? = null,
    val targetId: Long? = null,
    /** One imperative line, storyteller voice. */
    val headline: String,
    val detail: String = "",
    val options: List<TriggerOption> = emptyList(),
    /** The ability may not work (drunk/poisoned/dead/spent) — the ST decides. */
    val impaired: Boolean = false,
)

@Serializable
data class NominationCheck(
    val legal: Boolean,
    /** Hard rule violations, e.g. "Dana has already nominated today". */
    val blockers: List<String> = emptyList(),
    /** Legal-but-unusual, e.g. "Nominating a dead player — allowed, but no ghost vote is at stake". */
    val cautions: List<String> = emptyList(),
    val triggers: List<NominationTrigger> = emptyList(),
)
```

```kotlin
// ---------- Vote weighting ----------
@Serializable
data class VoteWeight(val playerId: Long, val weight: Int, val reason: String = "")
```

### B. `DayRules` — the day-phase predicate object (new file `DayRules.kt`)

```kotlin
object DayRules {

    // --- who may nominate / be nominated -------------------------------
    data class Right(val allowed: Boolean, val reason: String = "")

    /**
     * Bishop: only the Storyteller nominates. Riot day 3: the last nominee must.
     * Butcher: one extra nomination after the day's first execution.
     * Banshee (awoken): two nominations per day, and may nominate while dead.
     * Golem: only once per game.
     */
    fun canNominate(state: GameState, lookup: (String) -> Character?, playerId: Long): Right

    /** Anyone may be nominated who has not been nominated today. Dead included. */
    fun canBeNominated(state: GameState, lookup: (String) -> Character?, playerId: Long): Right

    fun nominationsClosed(state: GameState): Boolean =
        state.nominationsClosedOnDay == state.cycle

    // --- voting --------------------------------------------------------
    /** Voudon: only the Voudon and the dead. Beggar: needs a token. Butler: master must vote (Organ Grinder jinx only). */
    fun canVote(state: GameState, lookup: (String) -> Character?, playerId: Long, isExile: Boolean): Right

    /** Bureaucrat ×3, Thief −1, Banshee ×2, plain +1. Exiles: everyone is +1. */
    fun voteWeights(state: GameState, lookup: (String) -> Character?, voterIds: List<Long>, isExile: Boolean): List<VoteWeight>

    fun tally(state: GameState, lookup: (String) -> Character?, voterIds: List<Long>, isExile: Boolean): Int =
        voteWeights(state, lookup, voterIds, isExile).sumOf { it.weight }

    /** Execution: half of alive, rounded up — or 1 under a sober Voudon.
     *  Exile: half of ALL seats, rounded up, never modified by any ability. */
    fun threshold(state: GameState, lookup: (String) -> Character?, isExile: Boolean): Int

    /** Zealot seats that must have a hand up (5+ alive). */
    fun mustVote(state: GameState, lookup: (String) -> Character?): List<Long>

    /** A sober living Organ Grinder → eyes-closed voting, tally and block hidden. */
    fun secretVoting(state: GameState, lookup: (String) -> Character?): Boolean

    /** Legion: an execution fails if only evil players voted. */
    fun executionFailsOnlyEvilVoted(state: GameState, lookup: (String) -> Character?, voterIds: List<Long>): Boolean

    // --- executions ----------------------------------------------------
    fun executionToday(state: GameState): ExecutionRecord? =
        state.executions.lastOrNull { it.day == state.cycle }

    /** True when the day's one execution has been spent (survived counts). */
    fun executionSpent(state: GameState): Boolean =
        state.executions.any { it.day == state.cycle && it.outcome != ExecutionOutcome.NO_EXECUTION }

    /** Butcher exception: a second execution is legal today. */
    fun secondExecutionAllowed(state: GameState, lookup: (String) -> Character?): Boolean

    /** The Vizier cannot die during the day, by any means. */
    fun immuneToDayDeath(state: GameState, lookup: (String) -> Character?, playerId: Long): Boolean

    /** Living unimpaired Vizier holder, or null. */
    fun vizier(state: GameState, lookup: (String) -> Character?): Player?
}
```

### C. `GameActions` — the two funnels

Every execution and every nomination goes through exactly one function. This is the
architectural fix that unblocks roughly half the character audits.

```kotlin
/** Pure pre-flight. Called on every chip tap so the UI can render live. */
fun checkNomination(
    state: GameState,
    nominatorId: Long?,
    nomineeId: Long?,
    lookup: (String) -> Character?,
): NominationCheck

/** Applies a trigger the ST accepted (or declined via optionId = "skip"). */
fun applyNominationTrigger(
    state: GameState,
    trigger: NominationTrigger,
    optionId: String,
    lookup: (String) -> Character?,
): GameState

/** Records the nomination. Refuses when checkNomination().legal is false unless
 *  [force] (the ST always wins an argument with the app). */
fun recordNomination(
    state: GameState,
    nomination: Nomination,
    force: Boolean = false,
): GameState

/** THE execution funnel. Every "Execute" button in the app calls this. */
fun execute(
    state: GameState,
    playerId: Long,
    outcome: ExecutionOutcome = ExecutionOutcome.DIED,
    nominatorId: Long? = null,
    preventedBy: String = "",
    via: ExecutionVia = ExecutionVia.VOTE,
    /** Scapegoat: this seat dies, [playerId] survives, execution still belongs to [playerId]. */
    diedInsteadId: Long? = null,
    lookup: (String) -> Character? = { null },
): GameState

/** Records that today had no execution. Idempotent; replaced if an execution follows. */
fun noExecution(state: GameState): GameState

/** Closes the day (Virgin, Vizier, Psychopath, Boomdandy, Judge-pass, Riot). */
fun closeNominations(state: GameState, reason: String): GameState

// --- day ledger ---
fun recordDayEntry(state: GameState, entry: DayEntry): GameState   // stamps day + id
fun editDayEntry(state: GameState, id: Long, transform: (DayEntry) -> DayEntry): GameState
fun deleteDayEntry(state: GameState, id: Long): GameState
fun resolveDayEntry(state: GameState, id: Long, night: Int): GameState
fun announce(state: GameState, text: String, sourceId: String = ""): GameState
fun markAnnounced(state: GameState, id: Long): GameState

object DayLog {
    fun entries(state: GameState, day: Int? = null, kind: DayEntryKind? = null,
                speakerId: Long? = null, unresolvedOnly: Boolean = false): List<DayEntry>
    fun pendingAnnouncements(state: GameState): List<DayEntry>
}
```

**`execute` semantics, in order:**

1. Reject if `playerId` is a traveller (travellers are exiled, never executed).
2. Reject with a caution if `DayRules.executionSpent(state)` and not
   `secondExecutionAllowed` — overridable by the caller, never silently.
3. Snapshot `characterIdAtExecution`, `wasEvilAtExecution`,
   `abilityImpairedAtExecution`, `tally`, `threshold`.
4. Append the `ExecutionRecord`, **always** — before any kill, so an aborted kill still
   leaves the execution recorded.
5. If `outcome == DIED`: `kill(diedInsteadId ?: playerId, DeathCause.EXECUTION, lookup)`.
   If `SURVIVED`: no kill, no `DeathRecord`.
6. Place `("undertaker","Died today")` on the seat that actually died, if any.
7. Set `nominationsClosedOnDay` unless a Butcher/Riot exception applies.

**Deferred consequences are *not* applied inside `execute`.** They are returned to the
UI as a list so the ST can confirm each, and applied by `applyNominationTrigger`-style
handlers:

```kotlin
@Serializable
data class ExecutionConsequence(
    val sourceId: String,
    val headline: String,
    val detail: String = "",
    val options: List<TriggerOption> = emptyList(),
    val impaired: Boolean = false,
)

fun executionConsequences(state: GameState, record: ExecutionRecord, lookup: (String) -> Character?): List<ExecutionConsequence>
```

Covering, at minimum: Devil's Advocate `Survives execution`; Pacifist; Fool; Sailor; Tea
Lady; Zombuul first death; Vizier immunity; Psychopath roshambo; Scapegoat substitution;
Mayor bounce (night only, but the ST asks here too); Saint; Goblin claim; Fearmonger;
Evil Twin; Minstrel drunk-until-dusk; Mastermind extra day; Leviathan good-executed
counter; Boomdandy explosion; Cannibal `Lunch`; Undertaker `Died today`; Godfather
"an Outsider died today".

### D. Nomination triggers to implement (the table an implementer codes from)

| sourceId | Fires when | Kind | Effect |
|---|---|---|---|
| `virgin` | nominee is a sober living Virgin, never nominated **before in the game**, no `No ability` token, nominator registers as Townsfolk | AUTO_EXECUTION + END_DAY | execute nominator via `ExecutionVia.VIRGIN`; place `("virgin","No ability")`; close nominations. Options: `execute` (default) / `spy-registers-good` / `skip` |
| `witch` | nominator holds `("witch","Cursed")` and 4+ alive and Witch alive & sober | AUTO_DEATH | kill nominator; vote continues |
| `golem` | nominator is a Golem with no `May Not Nominate` token | AUTO_DEATH + WARN | if nominee is not the Demon, nominee dies; place `("golem","May Not Nominate")`; *vote continues as normal* |
| `gnome` | nominee holds `("gnome","Amigo")`, a living sober Gnome exists | CHOICE → AUTO_DEATH | Gnome may kill the nominator; vote continues |
| `fearmonger` | nominee holds `("fearmonger","Fear")` **and nominator is the Fearmonger seat** | WARN | three-way text: live / dead-or-impaired / wrong-nominator. Fires the win at execution, not here |
| `goblin` | nominee taps "Claims to be the Goblin" | CHOICE | sets `Nomination.goblinClaim`; adds `("goblin","Claimed")` **non-exclusively**; records a `GOBLIN_CLAIM` DayEntry and an `ANNOUNCEMENT` |
| `vizier` | any nomination while a living Vizier holds their ability | VOTE_MODIFIER | after the tally, if ≥1 voter registers good, offer "Execute immediately (Vizier)" |
| `riot` | day 3 with Riot in play | AUTO_DEATH + END-less | nominee dies immediately, must nominate again; no vote |
| `psychopath` | nominee is a living sober Psychopath | WARN | roshambo happens at execution; the day ends either way |
| `cerenovus`/`harpy` | nominator or nominee holds `Mad` | WARN | check the claim before this goes further |
| `bishop` | Bishop in play | blocker on every player-initiated nomination | only the ST nominates; ST must nominate ≥1 opposing player each day |

Impairment is checked with `StatusEffects.isImpaired` for **every** trigger and surfaced
as `impaired = true` with a caution rather than suppressing the trigger — the ST rules.

### E. Voting spec

- **Threshold.** `(aliveCount + 1) / 2` for executions (unchanged); `1` under a sober
  living Voudon; `(players.size + 1) / 2` for exiles, never modified.
- **Tally.** `DayRules.tally(...)` over `voterIds`. Weights: `bureaucrat:3 votes` → 3;
  `thief:Negative vote` → −1; awoken Banshee → 2 (and may raise two hands); everyone
  else → 1. Exiles: every weight is 1, and `tallyNotes` records "exile — abilities do
  not apply".
- **Ghost votes.** Spent automatically on `Record` for non-exile votes (as today,
  `DayScreen.kt:232-240`) — but the spend must be attributed on the `Nomination` so undo
  of a single nomination restores exactly the right tokens. A sober Voudon suppresses the
  spend entirely (*"They don't need a vote token to do so"*). The Beggar spends a token
  it received from a dead player: model as `toggleGhostVote` on the donor plus a
  `("beggar","Vote token")` reminder on the Beggar.
- **Butler.** Always show an inline hint when a Butler's hand is up and their `Master`
  is not in `voters`. Under `DayRules.secretVoting` the vote is **excluded from the
  tally** with a one-tap override; otherwise it is counted with the hint only (*"If the
  Butler accidentally votes illegally, tally the Butler's vote anyway."*).
- **Zealot.** With 5+ alive, a Zealot not in `voters` shows a blocking-ish caution on
  `Record`: *"Zealot must vote — Ben's hand should be up."* Never auto-added: the ST may
  have a reason.
- **Organ Grinder.** `DayRules.secretVoting` switches the whole Day tab:
  header drops the "tally to beat"; the running count is replaced by a press-and-hold
  **Peek**; the verdict line becomes **"Tally recorded"**; the block banner becomes
  **"Block — tap to reveal"**; per-nomination rows show `— votes hidden —`. A
  **Close nominations** action then reveals the block and offers Execute / No execution.
  `("organgrinder","Drunk")` must be added to `EXPIRES_AT_DUSK` (`GameActions.kt:231`).
- **Ties.** Unchanged (`Voting.outcome`), and a tie is explicitly a *no-execution* for
  the Mayor: *"Because a tied vote means neither player is executed, good wins."*
- **"About to die" wording.** Split `SAFE` in the UI into "below the threshold" and
  "passed, but below today's tally of N".
- **Withdrawn.** A `Withdraw` button next to `Record` writes
  `Nomination(result = WITHDRAWN, votes = 0, voterIds = emptyList())`. It still consumes
  both once-per-day rights; `highestVotesToday` and `aboutToDie` already ignore it.
- **Judge.** A once-per-game control on the nomination card: **Force pass** →
  `judgeForced = PASS`, result `ABOUT_TO_DIE`, then immediate `execute(via = JUDGE)` and
  close the day. **Force fail** → `judgeForced = FAIL`, `votes = 0`, result `SAFE`.
  Place `("judge","No ability")`; it never expires.
- **Gunslinger.** After the day's first non-exile tally is recorded, a card offers the
  Gunslinger a target chosen from that nomination's `voterIds`. Result: `kill(...,
  DeathCause.GUNSLINGER)`. Not an execution; the day continues.
- **Legion.** On `Record`, if every voter registers evil, show
  *"Only evil voted — this execution fails (Legion)."* and set result `SAFE`.
- **Boomdandy.** `Timer.kt` gains a `countFrom = 10` visible per-second mode used by the
  explosion flow; `WinCheck` is suppressed while the flow is open and re-evaluated once
  at the end.

### F. Execution spec

- The four call sites (`DayScreen.kt:111-114`, `DayScreen.kt:350-357`,
  `GameShell.kt:599-604`, `SeatSheet.kt:274`) all become `GameActions.execute(...)`,
  and all render `executionConsequences(...)` in the same confirmation sheet
  (`SeatSheet.kt:288-307`'s dialog, promoted to a shared composable).
- The block banner's Execute passes `nominatorId` from the nomination that produced the
  block: `state.nominations.last { it.day == cycle && !it.isExile && it.nomineeId == onBlockId && it.result == ABOUT_TO_DIE }`.
- **`SURVIVED` is a first-class button.** The confirmation sheet's dismiss action becomes
  **"Executed — but they don't die"**, which calls `execute(outcome = SURVIVED,
  preventedBy = <the protection the ST picked>)`. Today it records nothing
  (`SeatSheet.kt:303-305`).
- **Dusk guard** (`GameShell.kt:592-617`) gains a third state: when nobody is on the
  block, it must still ask **"No execution today?"** and call
  `GameActions.noExecution(state)` on confirm — that is the input the Mayor, Vortox,
  Zombuul and Godfather need.
- **Day-closed state.** When `DayRules.nominationsClosed(state)`, the "New nomination"
  card is replaced by a banner carrying `nominationsClosedReason`, the per-nomination
  Execute buttons disable, the dusk guard stops warning about the block, and the phase
  button reads **"Dusk (day is over)"**. An **Override** link reopens nominations.
- **Undertaker/Cannibal source** becomes
  `state.executions.last { it.day == day && it.outcome == DIED }`, resolved to the seat
  that actually died (`diedInsteadId ?: playerId`), with its
  `characterIdAtExecution` snapshot. The Cannibal additionally needs
  `wasEvilAtExecution` to decide the poison.
- **Expiry-table additions** (`GameActions.kt:218-242`):
  `EXPIRES_AT_DAWN` += `("undertaker","Died today")`, `("zombuul","Died today")`,
  `("organgrinder","About To Die")`, `("legion","About To Die")`.
  `EXPIRES_AT_DUSK` += `("organgrinder","Drunk")`, `("bureaucrat","3 votes")`,
  `("thief","Negative vote")`, `("psychopath","Used today")`, `("beggar","Vote token")`.
  Never expire: `("golem","May Not Nominate")`, `("judge","No ability")`,
  `("banshee","Has Ability")`, `("damsel","Guess Used")`,
  `("leviathan","Good Player Executed")`, `("boomdandy","Exploded")`.

### G. WinCheck spec

```kotlin
object WinCheck {
    data class Advisory(
        val goodWins: Boolean?,
        val reason: String,
        val cautions: List<String> = emptyList(),
        /** Stable id for dedupe/dismissal: "demon-dead", "mayor-dusk", "vortox-dusk", … */
        val ruleId: String,
        /** True when the ST must answer before the phase can advance (dusk rules). */
        val blocking: Boolean = false,
    )

    /** Continuous, cheap; called on any state change. */
    fun check(state: GameState, lookup: (String) -> Character?): Advisory?

    /** DAY → NIGHT, called by requestPhaseAdvance BEFORE advancePhase. Ordered. */
    fun duskCheck(state: GameState, lookup: (String) -> Character?): List<Advisory>

    /** NIGHT → DAY, for endings that resolve at dawn. */
    fun dawnCheck(state: GameState, lookup: (String) -> Character?): List<Advisory>
}
```

**Ordered dusk rules** (first match wins, but all matches are shown so the ST can see a
collision):

1. `vortox-dusk` — living sober Vortox and no `ExecutionRecord` for `state.cycle` with
   outcome `DIED` or `SURVIVED` → **evil wins**. Buttons: *Declare evil victory* /
   *Record an execution I forgot* / *Override*.
2. `mayor-dusk` — `state.aliveCountWithTravellers == 3`, no execution today, living
   sober Mayor → **good wins**. Explicit caution when a Vortox also matches; explicit
   caution *"Travellers count — exile them first"*.
3. `leviathan-day5` — Leviathan alive at the end of day 5 → **evil wins**.
4. `riot-day3` — Riot day 3 and all Riot dead / 2 alive → resolve.
5. `zombuul-night` — not a win: a dusk **briefing** ("nobody died today — the Zombuul
   kills tonight").

**Corrections to `check`:**

- `aliveDemons` must count a Zombuul that is `!alive` but holds its
  `Registers as dead` token, and must not fire "all Demons dead" while a Summoner is in
  play and has not yet created the Demon (`[No Demon]` setup).
- The ≤2-alive rule uses `aliveCountResidents` and drops the
  `aliveDemons.isNotEmpty()` precondition (`WinCheck.kt:88`) — the Glossary states it
  unconditionally.
- The Mayor rule uses `aliveCountWithTravellers`.
- The Saint rule reads `executions` (`outcome == DIED`, not `resurrected`) rather than
  `deaths`, and honours `abilityImpairedAtExecution`.
- New advisories keyed on `ExecutionRecord`: `goblin-claim`, `fearmonger`,
  `eviltwin-good-executed`, `atheist-storyteller-executed`, `leviathan-two-good`,
  `mastermind` (existing), `saint` (existing).
- New advisories keyed on `DayEntry`: `damsel-guessed`, `klutz-choice`,
  `alsaahir-correct`.
- **Heretic** wraps every advisory: if a living/dead Heretic is in play, invert
  `goodWins` and prefix the reason. Implement as a final `map` over the result so no
  individual rule has to know.
- **Atheist** suppresses *all* evil-win advisories and adds
  `atheist-storyteller-executed`.
- `GameShell.kt:506-508`'s `remember` key becomes
  `remember(state.players, state.phase, state.cycle, state.deaths, state.executions, state.nominations, state.dayLog, state.mastermindDayActive)`,
  and `dismissedAdvisory` dedupes on `ruleId`, not on the reason string.

### H. `DayBriefing` — the briefing surface (resolves four competing proposals)

One component, four slots. The audits asked for a dawn sheet (fearmonger), a day-start
card (mayor, gossip), a nomination-time panel (virgin) and a dusk sheet (zombuul).
These are the same component parameterised by slot.

```kotlin
object DayBriefing {
    enum class Slot { DAWN, DAY_START, NOMINATION, EXECUTION, DUSK }
    enum class Severity { INFO, ACTION, ALERT }

    data class Note(
        val slot: Slot,
        val severity: Severity,
        val sourceId: String,
        /** Short, imperative, storyteller voice. */
        val text: String,
        /** Optional one-tap action id the Day screen can wire to a resolver. */
        val actionId: String? = null,
    )

    fun build(state: GameState, lookup: (String) -> Character?, slot: Slot): List<Note>
}
```

Representative notes (each is a character audit's day-start request, satisfied here):

- DAWN — deaths to announce; `DayLog.pendingAnnouncements` each with a "said it" tick;
  "re-run first-night info for <resurrected player>".
- DAY_START — *"Ben survives execution today (Devil's Advocate)."* ·
  *"SECRET VOTING today — the Organ Grinder is sober."* · *"Vizier: Ann can execute
  immediately if any good player votes."* · *"Psychopath may kill before nominations."* ·
  *"Gossip: record Cara's statement when she makes it."* · *"Juggler: Dan may guess up to
  5 characters today."* · *"Savant: Nia may visit you today."* · *"Mayor: 3 alive — no
  execution means good wins at dusk."*
- NOMINATION — the `NominationTrigger` list.
- EXECUTION — the `ExecutionConsequence` list.
- DUSK — *"Nobody died today — the Zombuul kills tonight."* · *"No execution today —
  the Vortox wins."* · *"Leviathan: day 5 ends tonight."*

### I. Day screen layout (one-tap entry points)

Top to bottom, phone-first:

1. **Briefing strip** — `DayBriefing.build(…, DAY_START)`, collapsible, alerts first.
2. **"What was said today"** — the ledger card. One always-visible composer row:
   `[speaker chips, alive first] [text field "…said what?"] [Add]`. Two taps and a
   sentence, no dialog. A collapsed second row adds `About:`/`Character`/`Kind`. Truth
   chips appear only for kinds that need them. **This card is present in every game,
   with no character in play** — that is the user's explicit request.
3. **Day abilities** — one row per in-play day-acting character (Slayer, Gossip, Juggler,
   Artist, Savant, Alsaahir, Psychopath, Damsel, Klutz, Moonchild, Fisherman, Mayor,
   Amnesiac), each opening its resolver, each also openable for **any** seat with
   `bluff = true`. This is `QuickResolutions` (`NightScreen.kt:462-525`) for the day.
4. **On the block** banner (or the secret-voting variant).
5. **New nomination** — with the trigger card between the chips and the vote panel.
6. **Today's nominations** — with a per-row `Withdraw` and `Edit`.
7. **Day end** — `Execute` / `Executed but survives` / `No execution today` /
   `Close nominations`.

`SeatSheet` gains **"Record what they said"** (`SeatSheet.kt:309-315`) as a second entry
point into the ledger.

### J. Cross-cutting implementation notes

- **Every new `GameActions` helper needs two view-model wrappers.**
  `app/src/main/java/com/clocktower/grimoire/ui/GameViewModel.kt:194-223` **and**
  `web/src/wasmJsMain/kotlin/com/clocktower/grimoire/ui/WebGameViewModel.kt:175-200` —
  `web/build.gradle.kts:46-56` compiles the same `app/` screens against a parallel
  hand-written view model and excludes the Android one, so a missing wrapper breaks the
  PWA build the user actually plays on.
- **`StatusEffects.nominationWarnings` stays** as
  `checkNomination(...).triggers.map { it.headline } + cautions`, so nothing that calls
  it today breaks while the structured path is wired in.
- **New `DeathCause` values**: `SLAIN` (Slayer), `GUNSLINGER`, `PSYCHOPATH`, `NOMINATION`
  (Witch/Golem/Gnome/Virgin's collateral) added to `GameState.kt:75`, with matching
  strings in `GameExtras.kt:53-59` and `:324-327`. Serialised enums must be appended, not
  reordered.
- **`("goblin","Claimed")` must be non-exclusive** — several players may claim Goblin on
  the same day, so the Day toggle calls `addReminder` with a per-seat de-dupe, not
  `placeExclusiveReminder` (`GameActions.kt:194-203`).

---

## Tests to add

All in `engine/src/test/kotlin/com/clocktower/engine/`; `DayEngineTest.kt` unless noted.
Every one of these fails today.

**Nomination**

1. *Dead players may be nominated.* Given a day-2 state with seat 3 dead; When
   `checkNomination(state, nominatorId = 0, nomineeId = 3, lookup)`; Then `legal` is
   true and `blockers` is empty. (Today `DayScreen.kt:146` forbids it in UI.)
2. *Dead players may not nominate.* Same state, `nominatorId = 3`; Then `legal` is false
   with a blocker naming the death.
3. *One nomination each way, enforced in the engine.* Given seat 0 already nominated
   seat 3 today; Then `checkNomination(state, 0, 4).legal` is false and
   `checkNomination(state, 1, 3).legal` is false; and
   `recordNomination(state, illegalNomination)` returns `state` unchanged while
   `recordNomination(..., force = true)` records it.
4. *Butcher exception.* Given a Butcher seat and one `ExecutionRecord` today; Then
   `DayRules.canNominate(state, lookup, butcherId).allowed` is true even though the
   Butcher already nominated today.
5. *Banshee nominates twice while dead.* Given a Banshee holding `("banshee","Has
   Ability")` and dead; Then `canNominate` allows two nominations today and refuses a
   third.
6. *Bishop.* Given a Bishop in play; Then `canNominate` is false for every player and
   true only for `ExecutionVia.STORYTELLER` nominations.
7. *Virgin fires on the first nomination ever, not the first today.* Given a Virgin
   nominated on day 1 (safe) and nominated again on day 2; Then day 2's
   `checkNomination` yields **no** `virgin` trigger; and with no prior nomination it
   yields one with `kind == AUTO_EXECUTION`.
8. *Virgin ends the day.* Given the day-1 Virgin trigger applied with `optionId =
   "execute"`; Then the nominator has an `ExecutionRecord(via = VIRGIN, outcome = DIED)`,
   `DayRules.executionSpent` is true, `DayRules.nominationsClosed` is true, and
   `aboutToDie` is null.
9. *Fearmonger warning is nominator-specific.* Given seat 5 holds `("fearmonger","Fear")`
   and the Fearmonger is seat 2; Then `checkNomination(state, 1, 5)` contains a
   `fearmonger` trigger whose headline says the nomination is normal, and
   `checkNomination(state, 2, 5)` contains one whose `kind` marks the game-ending case.
10. *Witch curse kills the nominator at 4+ alive and not at 3.* Two states, opposite
    results.
11. *Golem kills the nominee and the vote continues.* Then the nominee is dead, the
    Golem holds `May Not Nominate`, and `nominationsClosed` is **false**.
12. *Withdrawn nominations are recordable and consume the rights.* Given a nomination
    recorded with `result = WITHDRAWN`; Then `hasNominatedToday` and
    `hasBeenNominatedToday` are both true and `highestVotesToday` is 0.

**Voting**

13. *Bureaucrat triples one vote.* Given seat 4 holds `("bureaucrat","3 votes")` and 3
    hands are up including seat 4; Then `DayRules.tally` is 5.
14. *Thief negates one vote.* Given seat 4 holds `("thief","Negative vote")` and 3 hands
    including seat 4; Then `tally` is 1.
15. *Exiles ignore vote modifiers.* Same grimoire, `isExile = true`; Then `tally` is 3
    and `tallyNotes` records why.
16. *Voudon threshold and voters.* Given a living sober Voudon; Then
    `DayRules.threshold(..., isExile = false)` is 1; `canVote` is false for a living
    non-Voudon and true for every dead player; and recording the nomination spends **no**
    ghost votes.
17. *Awoken Banshee votes twice.* Then `tally` counts the Banshee as 2.
18. *Butler under the Organ Grinder.* Given a sober Organ Grinder, a Butler with a
    `Master` who is not voting; Then `DayRules.secretVoting` is true and the Butler's
    weight is 0 with a reason; without the Organ Grinder the weight is 1 with a hint.
19. *Zealot must vote.* Given 5 alive and a Zealot not in `voterIds`; Then
    `DayRules.mustVote` contains the Zealot; at 4 alive it does not.
20. *Legion: only-evil votes fail the execution.* Then
    `executionFailsOnlyEvilVoted` is true and the recorded result is `SAFE`.
21. *Ghost votes are spent exactly once and only on non-exiles.* Extends the existing
    `nomination bookkeeping` test (`GameActionsTest.kt:137-149`).

**Execution**

22. *An execution that kills nobody is still an execution.* When
    `execute(state, 3, outcome = SURVIVED, preventedBy = "devilsadvocate")`; Then
    `state.deaths` is unchanged, `state.executions` has one row with
    `outcome == SURVIVED`, and `DayRules.executionSpent` is true.
23. *No execution today is recorded.* When `noExecution(state)`; Then `executions` has a
    `NO_EXECUTION` row for `state.cycle` and `executionSpent` is false.
24. *Only one execution per day.* Given one execution today; When `execute` is called
    again without `force`; Then the state is unchanged. With a Butcher in play it
    succeeds.
25. *Travellers cannot be executed.* When `execute(state, travellerId)`; Then the state
    is unchanged.
26. *Scapegoat substitution.* When `execute(state, 3, diedInsteadId = scapegoatId)`;
    Then seat 3 is alive, the Scapegoat is dead with `DeathCause.EXECUTION`, the
    `ExecutionRecord.playerId` is 3, and `executionSpent` is true.
27. *Undertaker reads the execution, not the death.* Given an execution with
    `outcome == SURVIVED`; Then `InfoCalc.compute("undertaker", …)` reports that nobody
    died by execution. Given `diedInsteadId = scapegoatId`; Then it shows the
    **Scapegoat's** character.
28. *Consequences are produced for every protection.* For each of `devilsadvocate`
    (`Survives execution`), `pacifist`, `fool`, `sailor`, `tealady`, `zombuul` (first
    death), `vizier`, `psychopath`: `executionConsequences` contains a row naming that
    source. (Today `DayScreen.kt:111-114` produces none of them.)
29. *Day closes on a Vizier execution.* Then `nominationsClosedOnDay == state.cycle`,
    `nominationsClosedReason` mentions the Vizier, and `aboutToDie` is null.
30. *`("undertaker","Died today")` is placed on execution and cleared at dawn.*

**WinCheck**

31. *Mayor at dusk with 3 alive and no execution.* Given exactly 3 alive **including a
    traveller** and `noExecution` recorded; Then `duskCheck` contains
    `ruleId == "mayor-dusk"` with `goodWins = true`. (Today `WinCheck.kt:19` excludes
    the traveller and never checks at dusk.)
32. *Mayor on a tie.* Given a tie today and 3 alive; Then the Mayor advisory fires.
33. *Vortox at dusk with no execution.* Then `duskCheck.first().ruleId == "vortox-dusk"`,
    `goodWins = false`, `blocking = true`; and with an `ExecutionRecord(SURVIVED)` today
    it does **not** fire.
34. *Vortox before Mayor.* Given both conditions; Then `duskCheck` returns the Vortox
    first and the Mayor advisory carries a collision caution.
35. *Zombuul fake death does not end the game.* Given the only Demon is a Zombuul that is
    `!alive` and holds `Registers as dead`; Then `check` returns no "every Demon is
    dead" advisory.
36. *Summoner suppresses "all Demons dead".* Given a Summoner in play and no Demon seat
    on night 1; Then `check` returns null.
37. *Two alive without a Demon seat still ends the game.* Given 2 alive residents and no
    living Demon seat (Lil' Monsta); Then the ≤2 advisory fires. (Today
    `WinCheck.kt:88`'s `aliveDemons.isNotEmpty()` suppresses it.)
38. *Goblin claim + execution.* Given `Nomination(goblinClaim = true)` on a sober Goblin
    and `execute(...)` for that nominee; Then `check` returns `ruleId == "goblin-claim"`,
    `goodWins = false`. With `outcome = SURVIVED` it still fires with a caution. With the
    Goblin poisoned it returns a caution, not a win. On an **exile** it does not fire.
39. *Fearmonger win requires the Fearmonger as nominator.* Two executions, same nominee,
    different `nominatorId`; only one fires.
40. *Saint who was executed but survived does not lose the game.*
41. *Heretic inverts everything.* Given a Heretic in play and the demons-dead advisory;
    Then `goodWins` is false and the reason names the Heretic.
42. *Leviathan.* Second good player executed → `evil`; and Leviathan alive at
    `duskCheck` on day 5 → `evil`.
43. *Advisory dedupe is by ruleId.* Two advisories with different prose and the same
    `ruleId` dedupe; same prose with different `ruleId` do not.

**Day ledger**

44. *A statement is recordable with nothing in play.* Given a plain 8-player game; When
    `recordDayEntry(state, DayEntry(id = 0, day = 0, kind = CLAIM, speakerId = 2, text =
    "Carol is the Empath"))`; Then the entry is stamped with `day = state.cycle` and
    `id = 1`, `nextDayEntryId` is 2, and `DayLog.entries(state, day = state.cycle)` has
    one row. (This is the user's Gossip complaint, tested directly.)
45. *Gossip statements are queryable by day and judged.* Record two `GOSSIP` entries on
    days 1 and 2; set day 2's `truth = TRUE`; Then
    `DayLog.entries(state, day = 2, kind = GOSSIP).single().truth == TRUE`, and after
    `resolveDayEntry(id, night = 3)` its `resolvedOnNight` is 3 and it no longer appears
    in `unresolvedOnly` queries.
46. *A bluffed Slayer shot is recorded and changes nothing.* Then the target is alive,
    no reminder was placed, and the entry has `bluff = true`.
47. *Announcements queue and clear.* `announce(state, "The Banshee has awoken.")` →
    `DayLog.pendingAnnouncements` has one row; `markAnnounced(id)` empties it; it stays
    in `dayLog` for the game log.
48. *Ledger survives round-trip serialisation.* A `GameState` with `dayLog`,
    `executions` and the new `Nomination` fields serialises and deserialises unchanged,
    and a JSON payload **without** any of the new fields still deserialises (default
    coverage for existing saves).
49. *Juggler consumes the day record.* Given a `JUGGLER` entry on day 1 with
    `subjectIdsB`/`characterIds` pairs; Then the night-2 Juggler step reports the correct
    count without the ST re-entering anything.

**Regression guards**

50. *Existing behaviour preserved.* `GameActionsTest.kt:59-65` (thresholds),
    `:67-79` (`Voting.outcome`), `:137-149` (nomination bookkeeping) and `:186-205`
    (about-to-die sequence) must all still pass unmodified after `votes` becomes the
    weighted tally, because with no vote modifiers in play the weighted tally equals the
    headcount.
