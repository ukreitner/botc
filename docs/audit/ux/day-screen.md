# Day screen — ease of use on a phone (ux/day-screen)

Scope: the storyteller's whole DAY on an iPhone-sized screen — dawn, the morning
briefing, private chats and timing, capturing what people said, day-time
abilities, nominations, voting, execution, no-execution, exile, and the dusk
hand-off to the night screen.

Files owned here: `app/src/main/java/com/clocktower/grimoire/ui/screens/DayScreen.kt`,
`app/src/main/java/com/clocktower/grimoire/ui/screens/GameShell.kt` (phase
controls + dialogs), `app/src/main/java/com/clocktower/grimoire/ui/screens/GameExtras.kt`
(log, win advisory), `app/src/main/java/com/clocktower/grimoire/ui/components/Timer.kt`.

**Not owned here.** Per-character day rules live in `docs/audit/characters/*.md`
(Gossip, Savant, Slayer, Virgin, Devil's Advocate, Psychopath, Organ Grinder,
Fisherman, Artist, Juggler, Alsaahir, Amnesiac, Damsel, Butler, Banshee,
Boomdandy, Atheist, Cannibal, Barber…), and the rules-engine shape of the day
lives in `docs/audit/mechanics/day-engine.md`. Where a finding below overlaps
one of those, I state only the *interaction* consequence and cite the character
file. Several of those files independently ask for the same three new surfaces
(a day-start briefing, a day-abilities panel, a single execution resolver) —
unifying them into one screen that a storyteller can actually operate with one
thumb is this document's job.

---

## Official rules (sources)

Day-phase ground truth used below. Per-character text is cited in the character
audits, not repeated here.

From <https://wiki.bloodontheclocktower.com/Glossary> (fetched 2026-08-25):

- **Day** — "The game phase in which players have their eyes open, talk with each
  other, and vote for an execution."
- **Dawn** — "The end of a night, just before the next day begins."
- **Dusk** — "The start of a night, just after the players close their eyes."
- **Nomination** — "The act of declaring a group vote to execute a player, which
  is echoed by the Storyteller."
- **Vote** — "Raising a hand when the Storyteller is counting the number of
  players in favor of an execution."
- **About to die** — "The player who has enough votes to be executed **and more
  votes than any other player today**."
- **Execution** — "The group decision to kill a player other than a Traveller
  during the day."
- **Exile** — "The group decision to kill a Traveller during the day."
- **Public / publicly** — "Anything said or done in such a way that most players,
  including the Storyteller, are aware that it happened."
- **Mad / Madness** — "A player who is 'mad' about something is trying to
  convince the group that something is true."

From <https://wiki.bloodontheclocktower.com/Rules_Explanation> (fetched 2026-08-25):

- "To nominate a player, simply say who. For example: 'I nominate Bob.'" — and
  when nominated, "everyone votes on whether or not to execute them."
- Running the vote: "put my arm out like this (point to Bob), and say 'Votes for
  Bob, starting now.' I move my hand in a clockwise direction" — i.e. **the tally
  is counted clockwise starting at the nominee's left**, which is exactly the
  order `DayScreen.kt:167-171` already builds.
- Threshold: "This player needs a vote tally of at least 50% of the living
  players or no execution occurs. **On a tie, neither player is executed.**"
- Dead players: "you may no longer nominate, and you have only one vote for the
  rest of the game, so use it wisely."
- Players may nominate once per day and be nominated once per day; only alive
  players may nominate (per the wiki's nomination summary surfaced in search;
  the dedicated `Nomination` / `Execution` wiki pages return 404 — the rule is
  quoted from `Rules_Explanation` and the Glossary instead).

From <https://wiki.bloodontheclocktower.com/Storyteller_Advice> (fetched 2026-08-25):

- "Step into the circle, completely or in part, to make sure that you are seen
  and heard when doing important things like running a vote or saying **'Last
  call for nominations! 3… 2… 1…'**."
- "It is best to keep the players in the circle while they are playing."

**Consequences for the UI.** The storyteller's hands and eyes are on the *table*,
not the phone, for most of the day: they are pointing at a nominee, sweeping a
hand clockwise, and speaking. Every day-phase interaction must therefore be
(a) reachable with one thumb without scrolling, (b) tappable without looking
closely, and (c) *forgiving* — a mis-tap must be undoable in one gesture, and
nothing consequential may sit next to something routine.

Rules I could **not** confirm from the wiki and have flagged as uncertain in the
findings: whether an exile nomination consumes a player's once-per-day
nomination, and whether an already-dead Traveller may be exiled. Both are
deferred to `docs/audit/mechanics/day-engine.md`.

---

## What the app does today

### A full day, walked through as the storyteller

**1. Dawn.** The last night step is a marker row titled "Dawn" whose entire text
is the static string *"Wait a few seconds. Everyone opens their eyes. Announce
who died."* (`engine/src/main/kotlin/com/clocktower/engine/NightOrder.kt:59`).
It does not name anyone. Tapping the phase button runs `requestPhaseAdvance`
(`GameShell.kt:126-168`), which checks the night checklist (`:147-161`), calls
`GameActions.advancePhase` — clearing the `EXPIRES_AT_DAWN` tokens
(`GameActions.kt:218-225, 258-263`) — and jumps the tab to DAY (`:162-167`).

**2. The morning briefing.** There isn't one. The Day tab opens on
`Text("Day ${state.cycle}", headlineMedium)` and a one-line stat row
(`DayScreen.kt:86-92`). Nothing is said about who died, who was resurrected, who
is mad, who survives execution today, whose Fearmonger token moved, whether
anyone died *yesterday* (Zombuul/Godfather gating), or which day counter this is
(Leviathan/Riot). To learn any of it the storyteller leaves the Day tab for the
Grimoire tab and reads the reminder tokens fanned around each seat
(`GrimoireScreen.kt:148-162`), or opens each seat's sheet.

**3. Private chats.** No support at all beyond `DiscussionTimer`
(`Timer.kt:39-107`), a 44dp icon in the bottom-right corner of the Grimoire and
Day tabs (`GameShell.kt:315-321`). Tapping it expands three preset chips —
1m / 2m / 5m (`Timer.kt:88`) — which set a wall-clock deadline; the running state
renders as a pill whose *entire surface* is a cancel button (`Timer.kt:76-85`).
On expiry it shows the text "Time!" and nothing else happens.

**4. Recording what was said.** Nothing exists. The only free text in the game is
`state.storytellerNotes`, one global blob reachable through the 12-item overflow
menu → "Storyteller notes" (`GameShell.kt:222-225`) → a modal `AlertDialog` with
one `OutlinedTextField` and a Save button (`GameShell.kt:685-706`); and
`Player.note`, reachable at Grimoire tab → seat → scroll the sheet to the bottom
→ "Seat notes" → Save & close (`SeatSheet.kt:366-380`). Neither is per-day, per-
statement, taggable, listable, or visible at night.

**5. Day-time abilities.** No entry point exists for any of them. 25 characters in
`characters.json` have an explicitly day-timed clause (Slayer, Gossip, Savant,
Juggler, Artist, Fisherman, Alsaahir, Amnesiac, Klutz, Moonchild, Damsel,
Goblin, Psychopath, Vizier, Yaggababble, Golem, Banshee, Bishop, Butcher,
Gunslinger, Matron, Gangster, Buddhist, Doomsayer, Duchess), plus Virgin and
Witch which fire *on* a nomination. The Day tab offers nominations, votes and
execution and nothing else (`DayScreen.kt:54-277`). The only "mark spent"
control in the app is a chip in the **Night** tab's tool tray
(`NightScreen.kt:263-279`).

**6. Starting a nomination.** From the Grimoire tab: tap the Day tab, scroll past
the header and the block banner to the "New nomination" `ElevatedCard`
(`DayScreen.kt:126-255`), tap a nominator chip, tap a nominee chip. Both rows are
`PlayerChipRow` (`DayScreen.kt:282-306`) — a `FlowRow` over **every** player
including the dead, with ineligible ones merely disabled
(`:296-303`; nominator gate at `:135-138`, nominee gate at `:146`).

**7. Voting.** Choosing a nominee reveals a divider, a header
*"Vote — tap everyone whose hand is up (N so far, needs T)"* in `titleSmall`
(`DayScreen.kt:173-178`), and a `FlowRow` of every player in clockwise order
starting left of the nominee (`:167-171, 179-196`). `canVote` is
`p.alive || !p.ghostVoteUsed || isExile` (`:184`). The tally is
`orderedVoterIds.size` (`:172, 226`) — one vote per selected chip, never more,
never fewer, never negative. The outcome comes from `Voting.outcome`
(`GameState.kt:147-152`) against `GameActions.highestVotesToday`
(`GameActions.kt:278-282`) and renders as one bold line (`:206-216`). Nothing is
committed until **[Record]** (`:218-247`), which writes the `Nomination` and
spends ghost votes for dead voters (`:232-240`).

**8. Execution.** Two buttons, both of which call `viewModel.kill(id,
DeathCause.EXECUTION)` directly: the block banner's **[Execute]**
(`DayScreen.kt:111-114`) and the per-nomination row's **[Execute]**
(`:350-357`). A third path is the dusk-guard dialog's **[Execute & begin night]**
(`GameShell.kt:598-605`). None of the three consults
`StatusEffects.deathNotes`, and none of them shows the protection-confirmation
dialog that the seat sheet shows for the very same action
(`SeatSheet.kt:255-307`).

**9. No execution / exile.** "No execution" exists only as a dismiss-button inside
the dusk guard, and the dusk guard only appears when somebody is on the block and
still alive (`GameShell.kt:141-146, 606-615`). If nobody reached the block, dusk
advances silently. Exile is inferred from `nominee.isTraveller`
(`DayScreen.kt:163`), uses `Voting.exileThreshold` over **all** players
(`GameState.kt:139`), lets everyone vote including spent ghosts (`:184`), and
does not spend ghost votes (`:233`).

**10. Dusk.** The phase control is a `FilledTonalIconButton` showing a moon glyph
whenever the viewport is narrower than 520dp (`GameShell.kt:172-173, 195-205`) —
which on an iPhone in portrait is *always*. It sits between Redo and the
overflow "⋮". Its only protection is an 800ms debounce (`GameShell.kt:128-130`).
The bottom of the Day tab carries a static sentence: *"When dusk falls, execute
whoever is on the block (from their seat or here), then advance to night."*
(`DayScreen.kt:268-276`).

**11. What the night gets.** Nothing purpose-built. `InfoCalc` re-derives from the
raw lists: Undertaker reads `state.deaths` for an `EXECUTION` on
`relevantDay` (`InfoCalc.kt:281-293, 117-118`), Town Crier reads
`state.nominations` (`:295-305`), Flowergirl reads `Nomination.voterIds`
(`:307-323`). Gossip, Juggler, Savant, Alsaahir, Amnesiac and the Slayer have
nothing to read because nothing was ever recorded.

### What works (one line each)

- Clockwise vote order starting left of the nominee is correct (`DayScreen.kt:167-171`).
- `Voting.outcome` / `highestVotesToday` / `aboutToDie` implement beat-the-highest
  and tie-clears-the-block correctly (`GameState.kt:147-152`, `GameActions.kt:278-306`).
- Execution and exile thresholds are the right formulas over the right
  populations (`GameState.kt:125-131, 136-139`).
- Exiles correctly do not consume the day's execution and do not spend ghost
  votes (`DayScreen.kt:197-205, 233`).
- Draft nominator/nominee/tally survive a tab switch via the shell's
  `SaveableStateHolder` (`GameShell.kt:299`), and reconcile when a seat vanishes
  (`DayScreen.kt:65-69`).
- Ghost votes are spent exactly once, on Record, and are restorable from the seat
  sheet (`DayScreen.kt:232-240`, `SeatSheet.kt:283-285`).
- The night checklist guard before dawn is genuinely useful (`GameShell.kt:147-161, 618-659`).

---

## Defects and gaps

### Dawn and the morning briefing

1. **P0 · Dawn announces nothing; the app knows exactly who died and stays
   silent.** Rules require the storyteller to open the day by announcing the
   night's deaths; the app's Dawn row is a fixed sentence with no names
   (`NightOrder.kt:59`) and the Day tab that opens next has no death list
   (`DayScreen.kt:85-124`). Every death is already in `state.deaths` with `day`
   and `atNight` (`GameState.kt:77-90`). Repro: kill two players at night,
   tap Dawn — the Day tab says "Day 2 · 9 alive · 5 votes to execute" and never
   names the dead.
2. **P0 · A resurrection is never surfaced in the morning.** This is the user's
   verbatim complaint. `GameActions.resurrect` flips `alive` and marks the death
   record (`GameActions.kt:173-181`), and the Professor resolver calls it
   (`NightScreen.kt:500-518`), but nothing at dawn says "X is alive again", and
   nothing prompts the storyteller to re-run that player's first-night
   information. Repro: Professor resurrects the Chef on night 3 → Day 3 opens
   with no mention of it; the table still believes the Chef is dead.
3. **P1 · No private-words checklist at dawn.** Cerenovus/Harpy madness
   instructions, the Fearmonger's "all players know if you choose a new player"
   announcement, a Banshee's public death announcement, the Damsel notification,
   Klutz/Moonchild "when you learn that you died" prompts — all are things the
   storyteller must say to specific people before opening the floor, and none is
   listed anywhere. The state needed is already in reminder tokens.
4. **P1 · No "true today" standing-facts panel.** Devil's Advocate "Survives
   execution", Butler "Master", Minstrel "Everyone is drunk", Sailor/Innkeeper
   "Drunk", Tea Lady "Can not die", Witch "Cursed", Goblin "Claimed", Zealot's
   forced vote, Vizier's undeath-by-day — these change how the *day* runs and
   are visible only as small tokens on another tab (`GrimoireScreen.kt:148-162`).
5. **P1 · Day counters are never placed or shown.** Leviathan ships `Day 1…Day 5`
   reminders and Riot ships `Day 1…Day 3` (`characters.json`); nothing
   auto-advances them at dawn and nothing displays "this is day 3 of 5", even
   though `state.cycle` is the number.
6. **P1 · "Died today" is never tracked.** Zombuul ("if no-one died today") and
   Godfather ("if 1 [Outsider] died today") both ship a `Died today` reminder;
   neither `EXPIRES_AT_DAWN` nor `EXPIRES_AT_DUSK` contains it
   (`GameActions.kt:218-242`), so a hand-placed token persists forever and an
   unplaced one silently mis-gates the demon. The fact is derivable from
   `state.deaths`.

### Recording public statements and claims

7. **P0 · There is no way to record anything anyone says, on any script.** The
   user's headline request ("make it easy to write down all the gossips even if
   Gossip isn't in play") has no implementation surface at all. The Day tab
   contains nominations, votes and execution and nothing else
   (`DayScreen.kt:54-277`). Consumers that need it today: Gossip, Juggler,
   Savant, Slayer, Alsaahir, Amnesiac, Damsel, Goblin, Klutz, Moonchild,
   Mutant, plus every storyteller's need to answer "has that player already
   claimed Slayer?" three days later. Data model already specified in
   `docs/audit/characters/gossip.md` (`PublicStatement`).
8. **P1 · Storyteller notes are hostile on a phone.** One global text blob, eighth
   item in a 12-item dropdown (`GameShell.kt:222-225`), opened as a modal
   `AlertDialog` with a `minLines = 6` field (`:685-706`). On iOS the software
   keyboard covers most of an `AlertDialog`; the dialog has no `imePadding`
   (contrast `SeatSheet.kt:172`), the text is lost unless **Save** is tapped, and
   nothing is timestamped or attributed to a seat.
9. **P2 · Seat notes are three levels deep and require an explicit Save.** Grimoire
   tab → seat → scroll → field → **Save & close** (`SeatSheet.kt:366-380`).
   Nobody does this while a player is mid-sentence.

### Day-time abilities

10. **P1 · No entry point for any day-time ability.** Slayer shot, Psychopath kill,
    Golem nomination, Artist question, Fisherman advice, Savant visit, Juggler
    guess, Alsaahir guess, Amnesiac guess, Damsel guess, Klutz/Moonchild choices,
    Gunslinger shot, Butcher second nomination, Matron seat swaps, Duchess
    visits. Cross-ref `slayer.md`, `psychopath.md`, `golem.md`, `fisherman.md`,
    `savant.md`, `artist.md`. Today the storyteller improvises with a reminder
    token and a mental note.
11. **P2 · "Mark spent" is night-only.** The once-per-game spend chip lives in the
    Night tab's tool tray (`NightScreen.kt:263-279`), so a Slayer shot used at
    noon can only be marked by switching to Night, expanding the Slayer step, and
    tapping there — or by hand-placing a "No ability" token from the seat sheet.

### Nominations

12. **P1 · Starting a nomination costs four taps and two scrolls, with a name hunt
    in the middle.** Day tab → scroll → nominator chip → nominee chip → scroll →
    tally (`DayScreen.kt:126-196`). The chip rows are unordered `FlowRow`s over
    all seats (`:282-306`), so on a 15-player game the storyteller reads ~15
    small labels twice while the table waits. Measured against a 360dp content
    width and 32dp chips, two 15-player `PlayerChipRow`s occupy roughly 230dp
    before the vote row starts.
13. **P1 · Dead players fill half the target area.** `PlayerChipRow` iterates
    `players`, not eligible players (`DayScreen.kt:296`). By day 4 of a 12-player
    game, 5-6 of 12 chips in the nominator row are permanently disabled clutter.
14. **P1 · Legal nominations are hard-blocked with no override and no
    explanation.** `enabled` on the nominator row is `p.alive &&
    !hasNominatedToday` (`DayScreen.kt:135-138`) and on the nominee row
    `p.alive && !hasBeenNominatedToday` (`:146`). That silently makes impossible:
    a dead Banshee nominating (and nominating twice), a Butcher's second
    nomination after the first execution, Riot's immediate re-nomination by the
    nominee, the Bishop's storyteller-only nominations, the Atheist's
    storyteller nomination, and exiling a dead Traveller. A disabled chip gives
    no reason and there is no "allow anyway". Cross-ref `banshee.md`, `atheist.md`.
15. **P1 · Nomination warnings arrive too late, are inert, and then vanish.**
    `StatusEffects.nominationWarnings` is rendered only after *both* chips are
    selected (`DayScreen.kt:154-159`) — i.e. after the nomination has already been
    made publicly — as plain red text with no action. The Virgin trigger, the
    Witch curse death, the Golem death and the Fearmonger loss condition all
    need a *resolution* at that moment, not a sentence. They also disappear the
    instant **[Record]** is pressed, before anyone has acted on them.
16. **P2 · No withdrawal.** `NominationResult.WITHDRAWN` exists in the model
    (`GameState.kt:59`) and is rendered by the log (`GameExtras.kt:71`) and the
    nomination row (`DayScreen.kt:343`) but can never be produced by any control.
17. **P2 · A recorded nomination cannot be edited or deleted.** A mis-tallied vote
    can only be fixed by walking the undo stack back past every reminder placed
    since (`GameViewModel.kt:111-116`). There is no per-row edit.
18. **P2 · Recorded voters are invisible.** `voterIds` is stored
    (`DayScreen.kt:226`) and Flowergirl and Town Crier depend on it
    (`InfoCalc.kt:295-323`) but the nomination row shows only "N votes · result"
    (`DayScreen.kt:339-348`), so the storyteller cannot verify or correct it.

### Voting

19. **P0 · Weighted and negative votes are structurally impossible.** The tally is
    `voters: Set<Long>` and `votes = orderedVoterIds.size`
    (`DayScreen.kt:60, 172, 226`). Bureaucrat ("their vote counts as 3 votes
    tomorrow"), Thief ("counts negatively tomorrow") and an awoken Banshee ("vote
    twice per nomination") therefore produce a wrong tally, which produces a
    wrong about-to-die, which produces a wrong execution. Cross-ref
    `banshee.md`, `bureaucrat` / `thief` traveller audits.
20. **P1 · Vote-eligibility rules are neither enforced nor communicated.** Butler
    ("may only vote if their master is voting too" — the `Master` token is in
    state and expires at dusk, `GameActions.kt:235`), Zealot ("must vote for every nomination"),
    Voudon ("only you and the dead can vote… a 50% majority is not required"),
    Beggar ("must use a vote token"). The chip row's only gate is
    `p.alive || !p.ghostVoteUsed || isExile` (`DayScreen.kt:184`). Cross-ref
    `butler.md`.
21. **P1 · The live tally is unreadable at arm's length.** It is a parenthetical
    inside a `titleSmall` sentence — *"(3 so far, needs 4)"* (`DayScreen.kt:176`).
    While sweeping a hand around a circle of 12, the storyteller needs a large
    running number and a position cursor, not a sentence.
22. **P1 · Ghost votes are invisible on the Day tab.** The count of unspent ghost
    votes is rendered on the *Grimoire* tab (`GrimoireScreen.kt:165-173`) and
    nowhere on the Day tab, even though it changes the arithmetic the storyteller
    is doing. There is also no way to record a hand that went up but must not be
    counted (a spent ghost, a Butler without their master) — the chip is simply
    disabled (`DayScreen.kt:184-187`).
23. **P1 · No secret-vote mode.** With a living, sober Organ Grinder the tally,
    the running count, the pass/fail verdict and the on-block banner must all be
    secret; the app shows them identically to any other game
    (`DayScreen.kt:86-92, 104-123, 173-178, 206-216, 339-348`). Rules and repro
    in `docs/audit/characters/organgrinder.md` defect 2; the interaction design
    is specified below.
24. **P2 · The tie display says nothing useful.** `"Tie — no one is about to die"`
    (`DayScreen.kt:118, 210`) omits who tied and at what count, and the
    "tally to beat" hint is suppressed whenever `highest == 0`
    (`DayScreen.kt:88-89`).
25. **P2 · The vote row is below the fold from ~8 players up** (see finding 12),
    so the storyteller must scroll while counting hands.
26. **P2 · The Day tab shows no reminder tokens.** Whether the nominee is Cursed,
    Mad, protected, Fear-marked or the Virgin is only discoverable by leaving the
    tab. The `nominationWarnings` partially compensate but only for four cases
    (`StatusEffects.kt:132-166`) and only after both chips are picked.

### Execution

27. **P0 · The Day tab's Execute is a dumber path than the seat sheet's.** Both
    `DayScreen.kt:111-114` and `:350-357` call `viewModel.kill(id,
    DeathCause.EXECUTION)` unguarded, while the identical action from a seat runs
    every `StatusEffects.deathNotes` protection through a confirmation dialog
    (`SeatSheet.kt:240-307`). So executing a Devil's-Advocate-protected player,
    a Fool, a Sailor, a Tea Lady neighbour, a Pacifist-eligible good player, a
    Vizier ("cannot die during the day"), a Psychopath (roshambo) or a first-death
    Zombuul kills them outright with no prompt. This is precisely the user's DA
    complaint arriving through the Day screen. Repro: place `devilsadvocate:
    Survives execution` on Alice, nominate and pass a vote on Alice, tap
    **Execute** in the block banner — Alice dies.
28. **P0 · The destructive execution is offered before nominations close.** The
    block banner's **[Execute]** is present the moment anyone passes a vote
    (`DayScreen.kt:93-115`), but per the rules a later nomination that ties the
    highest clears the block. Encouraging the storyteller to execute mid-day
    produces an execution the rules would have cancelled. Repro: nomination 1
    passes with 5 → tap Execute → nomination 2 also gets 5 → `aboutToDie` returns
    null but the player is already dead.
29. **P1 · The block banner never checks whether the player is still alive.**
    Unlike `NominationRow` (`DayScreen.kt:350`), the banner has no `alive` guard
    (`:93-115`), so after the execution it still reads *"On the block: Alice"*
    with a live **[Execute]** button for the rest of the day.
30. **P1 · "An execution happened" is not recorded when nobody dies.** The only
    evidence of an execution is a `DeathRecord` (`GameActions.kt:136-156`), so a
    Devil's Advocate save, a Fool save, a Pacifist save, a Vizier, a surviving
    Psychopath or a Zombuul first death leaves *no trace*. Downstream:
    `InfoCalc.undertaker` reports "No one was executed today"
    (`InfoCalc.kt:281-285`) which is correct for the Undertaker but the same
    absence silently mis-answers Vortox ("if no-one is executed, evil wins"),
    Mayor ("no execution occurs"), Godfather and Zombuul.
31. **P1 · There is no "no execution today" action.** The only such control is a
    dismiss button inside a dialog that only opens when somebody is on the block
    and alive (`GameShell.kt:141-146, 606-615`). A day that simply produced no
    passing vote advances to night with nothing recorded and nothing asked.
32. **P1 · Day-ending win conditions are never evaluated at dusk.** `WinCheck.check`
    covers Saint-executed, all-demons-dead, ≤2 alive and the Mastermind day
    (`WinCheck.kt:18-101`). It does not know about Vortox no-execution, Mayor at
    3 alive with no execution, Leviathan after day 5, Riot after day 3, Goblin
    claimed-and-executed, Fearmonger executed, Atheist executed, Boomdandy,
    Damsel guessed or Alsaahir guessed — all of which resolve at, or just before,
    dusk.
33. **P2 · The win advisory can interrupt a live vote.** `WinCheck.check` is
    recomputed on every change to `state.players` — including every reminder
    placement — and its modal opens immediately (`GameShell.kt:506-519`). Mid-
    nomination it steals focus, and dismissal is keyed on the reason string
    (`:509, 517`) so a slightly different reason re-prompts.

### Exile

34. **P2 · A dead Traveller cannot be exiled** because the nominee gate requires
    `p.alive` (`DayScreen.kt:146`). *(Rule uncertain — the wiki Travellers page
    does not state it; flagged for `mechanics/day-engine.md`.)*
35. **P2 · Nomination bookkeeping treats exiles inconsistently.**
    `hasNominatedToday` and `hasBeenNominatedToday` both exclude exiles
    (`GameActions.kt:285-289`), so a player may nominate an exile *and* make a
    normal nomination the same day, and a Traveller may be exile-nominated
    repeatedly. *(Rule uncertain; deferred.)*
36. **P3 · Exile has no top-level action.** The banner only ever offers Execute
    (`DayScreen.kt:111-114`); the Exile button exists only on the nomination row
    (`:350-357`), so a passed exile is easy to leave unresolved.

### Dusk and the hand-off

37. **P1 · The most destructive control on the screen is a 48dp unlabelled icon
    between Redo and the overflow menu.** On any viewport under 520dp — every
    iPhone in portrait — the phase button collapses to a moon glyph
    (`GameShell.kt:172-173, 195-205`), guarded only by an 800ms debounce
    (`:128-130`). Advancing to night clears the `EXPIRES_AT_DUSK` tokens and
    increments the cycle (`GameActions.kt:261-262`).
38. **P1 · Nothing is handed to the night.** There is no day summary object. Night
    steps that need one (Gossip's statement, the Juggler's guesses, "did anyone
    die today", "was there an execution", "who was executed") either re-derive it
    from raw lists (`InfoCalc.kt:281-323`) or cannot.
39. **P2 · The dusk instruction is a static sentence at the very bottom of the
    scroll** (`DayScreen.kt:268-276`) — the least likely place a storyteller will
    look at the end of a day.

### The discussion timer

40. **P1 · The timer is destroyed by any visit to the Night or Script tab.** The
    KDoc claims it "keeps counting while other tabs are open"
    (`Timer.kt:30-37`), but the composable is invoked inside
    `if (tab == GameTab.GRIMOIRE || tab == GameTab.DAY)` (`GameShell.kt:315-321`)
    and — unlike the tab bodies (`GameShell.kt:299`) — is *outside* the
    `SaveableStateHolder`. When the condition goes false the composable leaves
    composition, its `rememberSaveable` providers unregister, and `endAt`
    (`Timer.kt:41`) is lost. Grimoire↔Day preserves the timer; a trip to Night or
    Script silently resets it to idle. Repro: start a 5m timer on the Day tab,
    tap Night, tap Day — the timer is a fresh icon button.
41. **P1 · Expiry is silent.** On expiry the pill renders the word "Time!"
    (`Timer.kt:78-84`) with no sound, no vibration and no full-screen change. A
    storyteller looking at the table never notices. The platform seam
    (`ui/platform/Platform.kt`, `web/.../WebUiPlatform.kt`) is the natural place
    for a chime/haptic but has no such hook.
42. **P2 · Tapping the running time cancels the timer.** The whole pill is one
    `FilledTonalButton` whose only action is `endAt = 0L` (`Timer.kt:76`). There
    is no pause, no +1 minute, no reset — and glancing at the remaining time is
    the most common reason to touch it.
43. **P2 · Only 1m / 2m / 5m** (`Timer.kt:88`), no custom duration and no memory
    of the last used one. Private-chat time for a 13-player day is routinely
    longer than 5 minutes.
44. **P3 · Ticks four times a second for a one-second display** (`Timer.kt:48`),
    each tick writing a `rememberSaveable` state.

### Log, menu and chrome

45. **P2 · The game log orders same-day deaths before the nominations that caused
    them.** The sort key is `compareBy({ it.day }, { !it.atNight })`
    (`GameExtras.kt:79`); a day-3 execution and a day-3 nomination compare equal,
    and deaths are appended first (`:50-64` before `:65-78`), so a stable sort
    prints *"Alice executed"* above *"Bob nominated Alice — 5 votes, reached the
    block"*.
46. **P2 · The log is a read-only dialog with no filter, no day jump, no
    statements, no executions-without-death, no ability uses, no export**
    (`GameExtras.kt:46-106`), and its `LazyColumn` has no `heightIn` cap, unlike
    the shell's other list dialogs (`GameShell.kt:561, 627`).
47. **P2 · Everything non-nomination is behind one 12-item dropdown**
    (`GameShell.kt:217-270`). Log is the eighth item; on a phone the menu
    scrolls. During a day, the storyteller reaches for log, notes, show-a-card
    and jinxes constantly.
48. **P3 · The Mastermind banner is a floating `Box` at a hardcoded `top = 100.dp`
    outside the `Scaffold`** (`GameShell.kt:520-537`) — it overlaps the Day
    header, is not dismissible, and does not adapt to the top bar's height.

---

## Proposed behaviour (spec)

### 0. Principle

One screen, one *stage*, one primary action. The Day tab becomes a **day
timeline** whose stages appear in the order the storyteller lives them, each
collapsing to a single summary line once complete, plus a **fixed bottom action
bar** that is the only thing the thumb ever needs to find.

Everything consequential — executing, advancing to night — is a wide labelled
button inside its stage, never an icon in the top bar. Everything routine —
recording a statement, starting a nomination, checking the timer — is in the
bottom bar.

### A. Information architecture

```
 ┌────────────────────────────────────────────┐
 │ Trouble Brewing              👁  ↶  ↷   ⋮ │   ← phase button REMOVED
 │ Day 3 · 7/12 alive                         │      from the top bar
 ├────────────────────────────────────────────┤
 │ Day 3 · 7 alive · 4 to execute · 2 ghosts  │   ← sticky stat strip
 │ ◆ Fay is about to die (5 votes)            │
 ├────────────────────────────────────────────┤
 │ ▾ DAWN — 3 things to announce          ①  │
 │ ▸ Morning briefing — 5 standing facts      │
 │ ▸ What was said today (2)                  │
 │ ▸ Day abilities (Slayer · Gossip)          │
 │ ▾ Nominations (2)                          │
 │ ▸ Dusk                                     │
 └────────────────────────────────────────────┘
 │  ⏱ 4:58  │    + Say    │     Nominate      │   ← fixed bottom bar
 └──────────┴─────────────┴───────────────────┘
```

- The sticky stat strip replaces `DayScreen.kt:86-92` and gains the ghost-vote
  count (currently only on the Grimoire tab, `GrimoireScreen.kt:165-173`).
- Stage cards auto-expand in sequence: Dawn is expanded when the day opens and
  auto-collapses when its checklist is complete; Nominations auto-expands on the
  first recorded nomination; Dusk auto-expands when the storyteller taps
  **Nominations closed** or when every living player has nominated.
- The bottom bar is a `NavigationBar`-height `Surface` above the tab bar, always
  present during `Phase.DAY`. It replaces the corner-floating `DiscussionTimer`
  (`GameShell.kt:315-321`) on the Day tab, which removes the need for
  `DayScreen.kt:82`'s 96dp bottom padding hack.

### B. Dawn card and the morning briefing

New engine object, consumed by the Day tab and by the night sheet's DAWN row.

```kotlin
// engine/.../DayBriefing.kt  (new)
@Serializable enum class BriefingKind { ANNOUNCE, PRIVATE_WORD, STANDING_FACT, ASK_FIRST }

data class BriefingItem(
    val kind: BriefingKind,
    /** Stable key for the ticked-off set; survives recomposition and undo. */
    val key: String,
    /** Imperative, storyteller voice, ready to read aloud. */
    val text: String,
    val playerId: Long? = null,
    /** Optional one-tap follow-through (show a card, open a picker). */
    val action: BriefingAction? = null,
)

sealed interface BriefingAction {
    data class ShowCard(val spec: String) : BriefingAction
    data class RerunFirstNight(val playerId: Long) : BriefingAction
    data class OpenSeat(val playerId: Long) : BriefingAction
    data class RecordStatement(val playerId: Long, val kind: StatementKind) : BriefingAction
}

object DayBriefing {
    fun forDay(state: GameState, lookup: (String) -> Character?): List<BriefingItem>
}
```

Derivation rules (all from existing state; nothing new to remember):

| Kind | Source | Text |
|---|---|---|
| ANNOUNCE | `state.deaths` where `day == cycle && atNight && !resurrected` | `"<Name> died in the night."` |
| ANNOUNCE | `state.deaths` where `resurrected` and the resurrection happened last night | `"<Name> is alive again."` |
| ANNOUNCE | a `fearmonger:Fear` token that moved seats since last dawn | `"The Fearmonger has chosen a new player."` |
| ANNOUNCE | a `banshee:Has Ability` token placed last night | `"<Name> was the Banshee — they may now nominate twice and vote twice."` |
| ANNOUNCE | `leviathan` in play | `"Leviathan: this is day <n> of 5."` |
| PRIVATE_WORD | resurrected player whose character has a first-night info step | `"<Name> — re-run their first night."` + `RerunFirstNight` |
| PRIVATE_WORD | `cerenovus:Mad` / `harpy:Mad` tokens | `"<Name> is mad that they are the <X> today — if they don't try to convince the group, they may be executed."` |
| PRIVATE_WORD | `damsel` in play and `Guess Used` absent, on day 1 | `"All Minions know a Damsel is in play."` |
| STANDING_FACT | `devilsadvocate:Survives execution` | `"<Name> survives execution today. Announce the execution, then that they live — never why."` |
| STANDING_FACT | `butler:Master` | `"<Butler> may only vote if <Master> votes."` |
| STANDING_FACT | `minstrel:Everyone is drunk` | `"Everyone except Travellers is drunk until dusk."` |
| STANDING_FACT | `tealady:Can not die` | `"<Name> can't die."` |
| STANDING_FACT | `zealot` alive and `alive >= 5` | `"<Zealot> must vote for every nomination."` |
| STANDING_FACT | `organgrinder` alive and not impaired | `"Eyes closed for every vote. The tally is secret."` |
| ASK_FIRST | `psychopath` alive, unimpaired, unspent | `"Ask the Psychopath before opening nominations."` |
| ASK_FIRST | `zombuul` / `godfather` in play | `"Nobody died today (yet) — the Zombuul may kill tonight."` (live-updating) |

Ticked items are stored in a new `GameState.briefingDone: Set<String>` so the
card survives tab switches, undo, and app restart.

```
 ┌─ DAWN · Day 3 ─────────────────── 3 left ─┐
 │ ANNOUNCE, in this order:                   │
 │  ☑ Kip died in the night.                  │
 │  ☐ Sam is alive again.                     │
 │  ☐ The Fearmonger has chosen a new player. │
 │                                            │
 │ SAY PRIVATELY, before you open the day:    │
 │  ☐ Sam — re-run their first night          │
 │       Empath sees 1        [ show card ]   │
 │  ☐ Dee — mad that she is the Empath        │
 │                                            │
 │ TRUE TODAY:                                │
 │  • Fay survives execution (Devil's Adv.)   │
 │  • Bo may only vote if Ana votes (Butler)  │
 │  • Zealot must vote every nomination       │
 │  • Nobody has died today → Zombuul may kill│
 │                                            │
 │ ASK FIRST:                                 │
 │  ☐ The Psychopath, before nominations      │
 │                                            │
 │           [  Open the day  ]               │
 └────────────────────────────────────────────┘
```

Also replace the static DAWN detail string at `NightOrder.kt:59` with the
ANNOUNCE lines, so the night sheet's last row reads the names too.

### C. "What was said" — statement and claim capture

Adopt the `PublicStatement` model already specified in
`docs/audit/characters/gossip.md` (fields, `GameActions` verbs, both view-model
wrappers). This section specifies only the **capture interaction**, which is the
part the user asked for by name.

Three entry points, all landing in the same composer:

1. **Bottom bar → `+ Say`** — the always-available path.
2. **Long-press a seat on the Grimoire circle** (`GrimoireScreen.kt:159`
   currently only handles `onClick`) → composer with `speakerId` pre-filled.
3. **Briefing / day-ability rows** → composer with `speakerId` *and* `kind`
   pre-filled (e.g. the Gossip's row opens it as `kind = GOSSIP`).

The composer is a **bottom sheet with `imePadding()`** (never an `AlertDialog` —
see finding 8), laid out so the first thing under the thumb is the seat picker:

```
 ┌─ What was said · Day 3 ────────────────────┐
 │ Who said it?                               │
 │  Ana  [Bo]  Cy†  Dee  Eli  Fay  Gus  Hal   │   ← seat order, alive first
 │                                            │
 │ ┌────────────────────────────────────────┐ │
 │ │ Fay is the Imp                       🎤│ │   ← autofocus, dictation
 │ └────────────────────────────────────────┘ │
 │                                            │
 │ ⬤ Claim  ○ Gossip  ○ Juggler  ○ Savant     │   ← kind, smart default
 │ ○ Slayer ○ Guess   ○ Other                 │
 │                                            │
 │ About:  [Ana] [Bo] [Cy] …   As: [ Imp ▾ ]  │   ← optional, collapsed
 │                                            │
 │  [ Add ]                  [ Add & another ]│
 └────────────────────────────────────────────┘
```

Interaction requirements:

- **Two taps and a sentence.** Tap speaker → the text field is already focused →
  type or dictate → **Add**. Everything else has a default.
- **Zero-typing path.** With a speaker selected and the field empty, the
  **Add** button becomes **"Claims…"** and offers a character grid (script
  characters, in-play first) so `"Ana claims Empath"` is recordable in three taps
  and no keyboard. This is the most common statement in every game and must never
  require typing.
- **Dictation.** Add a `rememberDictation(onText: (String) -> Unit): (() -> Unit)?`
  to the existing platform seam (`ui/platform/Platform.kt`,
  `web/.../WebUiPlatform.kt`) — Android `RecognizerIntent`, web
  `webkitSpeechRecognition`. Return `null` where unsupported and hide the mic.
  *Risk to verify before implementing:* Compose-for-wasm renders text fields on a
  canvas, so iOS Safari's built-in keyboard dictation key may not reach the field
  — which is exactly why the explicit mic button and the zero-typing path both
  need to exist.
- **Smart `kind` default**, in priority order: the speaker holds a character with
  a statement-consuming ability and today has no statement of that kind yet →
  that kind; otherwise `CLAIM`.
- **`Add & another`** keeps the sheet open and advances the speaker chip to the
  next living seat clockwise — the storyteller can capture a whole round of
  claims without dismissing.
- **Never blocks.** The sheet is dismissible at any time; a half-typed statement
  is kept as a draft in the stage card so it can be finished later.

The stage card renders today's statements as one row each, with tri-state truth
chips only for kinds where truth matters:

```
 ┌─ WHAT WAS SAID · Day 3 ────────────── 3 ──┐
 │ Bo  » "Fay is the Imp"        ✓  ✗  ?   ✎ │
 │ Ana » claims Empath                     ✎ │
 │ Hal » Gossip: "Two Outsiders have died" ? ✎│
 │                                            │
 │              ▸ earlier days (7)            │
 └────────────────────────────────────────────┘
```

Statements must also appear in the game log (`GameExtras.kt:46-106`) as
`D3  Bo said "Fay is the Imp" (true)`.

### D. Day-abilities strip

Adopt `DayAbilities.forState(state, lookup)` as proposed in
`docs/audit/characters/fisherman.md` — a table of `characterId → DayAbility`,
never ability-text parsing. Spent state = a `PlacedReminder(characterId,
"No ability")` on the seat, matching the existing convention
(`NightScreen.kt:263-279`).

```
 ┌─ DAY ABILITIES ────────────────────────────┐
 │ Slayer · Ben        [ Slayer shot ]  unspent│
 │ Psychopath · Kim    [ Public kill ]  today ✓│
 │ Gossip · Hal        [ Statement   ]  none yet│
 │ Artist · Iris        —               spent d2│
 │ Golem · Ada         [ Nominate    ]  unspent │
 └────────────────────────────────────────────┘
```

Rules for the strip itself (the per-character resolvers belong to the character
audits):

- Rows appear only for characters actually in play, keyed on `nightRoleId` so a
  Drunk/Marionette shown as a Slayer gets the row (and always misses).
- Dead holders are greyed with the reason; impaired holders show a red subline.
- Each row's button opens a sheet, and every sheet ends by writing a
  `DayAbilityUse(day, playerId, characterId, text, impaired)` to a new
  `state.dayAbilityUses` and placing the spend token in the same undoable update.
- A **"Mark spent"** long-press on any row does only the token half, for the
  storyteller who resolved it verbally.
- The strip is the fix for finding 11: spending is reachable without leaving the
  Day tab.

### E. Nominations in two taps

Replace the two `PlayerChipRow`s (`DayScreen.kt:131-152, 282-306`) with a
**compact seat ring** pinned under the stat strip, reusing `CircleLayout` from
`GrimoireScreen.kt` at ~38% of screen height. The ring is *always armed*: the
first tap picks the nominator (gold), the second the nominee (red), and the tally
sheet slides up automatically. That is two taps, no scrolling, and no name hunt —
the spatial layout matches the actual table.

```
 tap 1 = nominator          tap 2 = nominee
 ┌────────────────────────────────────────────┐
 │              (12) Ana                      │
 │      (11) Kip           (1) Bo ◆           │  ◆ = nominator
 │   (10) Sam                   (2) Cy †      │  † = dead
 │  (9) Ida     tap who nominates    (3) Dee  │
 │   (8) Jo                      (4) Eli      │
 │      (7) Hal            (5) Fay ✕          │  ✕ = nominee
 │              (6) Gus                       │
 └────────────────────────────────────────────┘
```

- Ineligible seats are dimmed but **still tappable**; tapping one shows a one-line
  reason and an **[Allow anyway]** chip (fixes finding 14). Reasons come from a
  new `Nominations.eligibility(state, lookup, role, playerId): Eligibility`
  returning `Allowed | Blocked(reason) | Unusual(reason)`. Banshee, Butcher,
  Riot, Bishop and the Atheist's storyteller-nomination are `Unusual`, never
  `Blocked`.
- A **[⟡ Storyteller]** pseudo-seat is added to the ring for the Bishop and the
  Atheist (cross-ref `atheist.md`, `bishop`).
- Long-press a seat = **+ Say** for that speaker (section C).
- The ring collapses to a summary row once nominations close.

**Nomination interceptor.** Before the tally opens, run
`StatusEffects.nominationWarnings` (`StatusEffects.kt:132-166`) *plus* the new
resolvable cases, and render each as a card with buttons rather than red text:

```
 ┌─ Bo nominates Fay ─────────────────────────┐
 │ ! Virgin's first nomination.               │
 │   Is Bo a Townsfolk?                       │
 │   [ Yes — Bo is executed now ]  [ No ]     │
 │ ! Bo is Witch-cursed (4+ alive).           │
 │   [ Bo dies now ]  [ No — Witch impaired ] │
 └────────────────────────────────────────────┘
```

Each button applies its full consequence in one undoable update and writes a log
line. Cross-ref `virgin.md` (interceptor), `witch.md`, `golem.md`,
`fearmonger.md`, `psychopath.md`.

### F. Vote counting

```
 ┌─ Bo » Fay ─────────────────────── needs 4 ─┐
 │                                            │
 │                ┏━━━━━━┓                    │
 │                ┃  3   ┃   of 4             │  ← 48sp, glanceable
 │                ┗━━━━━━┛                    │
 │                                            │
 │  clockwise from Fay's left ▸               │
 │  Gus  Hal  Jo   Ida  Sam  Kip  Ana  Bo     │
 │   ●    ●    ○    ●†   ⊘    ○    ○    ○     │
 │                     ↑ cursor                │
 │  ⊘ Sam — ghost vote already spent          │
 │                                            │
 │  [ ◀ back ]  [ no hand ]  [ HAND UP ▶ ]    │  ← sweep mode
 │                                            │
 │  Fay is SAFE — 3 of 4                      │
 │  [ Lock in ]                    [ cancel ] │
 └────────────────────────────────────────────┘
```

- **Big tally**: a 48sp running count with the threshold beside it, replacing the
  parenthetical at `DayScreen.kt:176`.
- **Sweep mode** (default): a cursor walks clockwise from the nominee's left; the
  storyteller's thumb rests on one large **[HAND UP]** / **[no hand]** pair and
  never has to aim at a specific chip. **[◀ back]** corrects the last call. The
  chip row stays tappable for random access.
- **Weighted votes** (fixes finding 19): replace `voters: Set<Long>` with
  `List<VoteEntry>`.

  ```kotlin
  @Serializable data class VoteEntry(
      val playerId: Long,
      /** 1 normally; 3 for a Bureaucrat target; -1 for a Thief target; 2 for an awoken Banshee. */
      val weight: Int = 1,
      /** False when the hand went up but must not count (spent ghost, Butler without master). */
      val counted: Boolean = true,
      val reason: String = "",
  )
  ```

  `Nomination` gains `votesCast: List<VoteEntry> = emptyList()` beside the
  existing `voterIds` (kept, derived, for save compatibility and
  `InfoCalc.flowergirl` at `InfoCalc.kt:315`). The default weight is derived from
  tokens (`bureaucrat:3 votes`, `thief:Negative vote`, `banshee:Has Ability`) and
  shown on the chip as `×3` / `−1`, editable by long-press.
- **Constraint hints inline** (fixes finding 20): a Butler chip whose master's
  hand is not up renders `counted = false` with the reason
  `"Butler — master's hand is down"`; a Zealot who is not selected shows
  `"Zealot must vote"` in amber; Voudon and Beggar rules switch the eligible set
  and the threshold text.
- **Ghost votes on screen**: the sticky strip shows `2 ghosts`, and a dead
  player's chip carries `†` plus a filled/hollow dot for spent/unspent.
- **Lock in** replaces **[Record]** (`DayScreen.kt:218-247`) with the outcome in
  the label — `"Lock in: Fay is SAFE (3 of 4)"` — so the storyteller confirms a
  *result*, not an abstraction. The recorded row then gains **[edit]** and
  **[withdraw]** (fixes findings 16, 17), producing
  `NominationResult.WITHDRAWN` (`GameState.kt:59`) and un-spending any ghost
  votes it spent.
- **Voter list visible** on each recorded row behind a `▸` expander (fixes
  finding 18).
- **Tie text names names** (fixes finding 24):
  `"Tie at 5 — Fay and Gus. No one is about to die. 6 votes to beat it."`

**Secret-vote mode** (fixes finding 23; rules in `organgrinder.md`). Auto-armed
whenever a living, unimpaired Organ Grinder is in play; also togglable by hand
for house rules. In this mode:

```
 ┌─ Bo » Fay ──────────────────────── SECRET ─┐
 │        EYES CLOSED, EVERYONE               │
 │  ("Why? Because an Organ Grinder           │
 │    is in play.")                           │
 │                ┏━━━━━━┓                    │
 │                ┃ •••  ┃   hold to peek     │
 │                ┗━━━━━━┛                    │
 │  Gus  Hal  Jo   Ida  Sam  Kip  Ana  Bo     │
 │   ●    ●    ○    ●    ○    ○    ○    ○     │
 │                                            │
 │  [ Lock in silently ]           [ cancel ] │
 └────────────────────────────────────────────┘
```

- The count, the outcome line, the "tally to beat" hint, the on-block banner and
  every recorded row's vote count are replaced by `•••`, revealed only on a
  long-press (so the phone can be read by the storyteller alone and never
  glanced at from across the table).
- The stat strip drops `· N votes is the tally to beat`.
- At dusk the Dusk card says `"Declare that Fay is executed."` and nothing else.

### G. Execution, resolved once

A single engine entry point, which every caller uses —
`DayScreen.kt:111-114`, `DayScreen.kt:350-357`, `GameShell.kt:598-605`, and the
Dusk card. This is the convergence point requested independently by
`devilsadvocate.md`, `psychopath.md`, `barber.md`, `boomdandy.md`, `cannibal.md`
and `virgin.md`.

```kotlin
// engine/.../Execution.kt  (new)
sealed interface ExecutionOutcome {
    data class Dies(val playerId: Long, val notes: List<String>) : ExecutionOutcome
    data class Survives(val playerId: Long, val reason: String, val notes: List<String>) : ExecutionOutcome
    data class NeedsDecision(val playerId: Long, val question: String,
                             val options: List<ExecutionOption>) : ExecutionOutcome  // Pacifist, Psychopath roshambo, Scapegoat
    data object NoExecution : ExecutionOutcome
}

object Execution {
    fun resolve(state: GameState, lookup: (String) -> Character?, nomineeId: Long?): ExecutionOutcome
    fun apply(state: GameState, outcome: ExecutionOutcome): GameState
}
```

`apply` **always** appends an `ExecutionRecord`, whether or not anyone dies:

```kotlin
@Serializable data class ExecutionRecord(
    val day: Int,
    /** null = the day ended with no execution at all. */
    val nomineeId: Long? = null,
    val died: Boolean = false,
    /** "Devil's Advocate", "Fool", "Pacifist", "no one on the block", "storyteller". */
    val reason: String = "",
)
val executions: List<ExecutionRecord> = emptyList()   // on GameState
```

This single record is what fixes finding 30 for Vortox, Mayor, Zombuul,
Godfather, Minstrel, Cannibal and the Undertaker at once.

Dusk card, with the resolution shown *before* the button is pressed:

```
 ┌─ DUSK · Day 3 ─────────────────────────────┐
 │ On the block: Fay — 5 votes                │
 │                                            │
 │ Before you execute:                        │
 │  ! Fay is marked SURVIVES EXECUTION        │
 │    (Devil's Advocate) — she will not die.  │
 │                                            │
 │ [      Execute Fay      ]                  │
 │ [ No execution today    ]                  │
 └────────────────────────────────────────────┘
```

Result card, shown full-width after the tap:

```
 ┌────────────────────────────────────────────┐
 │  FAY WAS EXECUTED — AND IS STILL ALIVE     │
 │                                            │
 │  Say "Fay is executed", then "Fay is still │
 │  alive." Never say why.                    │
 │                                            │
 │  Today's execution is spent:               │
 │   · Undertaker learns nothing tonight      │
 │   · Godfather does not trigger             │
 │   · Zombuul: someone died today? NO        │
 │                                            │
 │            [ Everyone, eyes closed ▸ ]     │
 └────────────────────────────────────────────┘
```

Other required behaviour:

- Remove the **[Execute]** button from the block banner (`DayScreen.kt:111-114`)
  — the banner becomes informational, fixing findings 28 and 29. Execution
  happens only in the Dusk card, which is where the rules put it.
- The Dusk card appears when the storyteller taps **Nominations closed** in the
  Nominations stage (UI text: *"Last call for nominations! 3… 2… 1…"* — the
  wiki's own phrasing), or automatically when no living player may still
  nominate.
- **[No execution today]** is always present (fixes finding 31) and writes
  `ExecutionRecord(day, nomineeId = null, died = false, reason = "no execution")`.
- Before writing either, run a **day-end win check** (fixes finding 32):

  ```kotlin
  fun WinCheck.checkAtDusk(state, lookup, execution: ExecutionRecord): Advisory?
  ```

  covering Vortox no-execution, Mayor at 3 alive with no execution, Leviathan
  after day 5, Riot after day 3, Goblin claimed-and-executed, Fearmonger
  executed, Atheist executed, Boomdandy, plus the existing Saint case. Render it
  in the Dusk card as an inline banner, not as the modal that today can interrupt
  a live vote (finding 33) — and gate the existing `WinCheck` modal so it never
  opens while a nomination sheet is up.

### H. Exile

- The ring's nominee tap on a Traveller switches the sheet into **Exile** mode
  with its own threshold text (`"needs 7 of 13 — all players, alive or dead"`),
  no ghost-vote spend, and everyone eligible. This already works
  (`DayScreen.kt:163-205, 233`); it just needs its own visual identity so it is
  never confused with an execution.
- Dead Travellers become tappable as `Unusual("dead Traveller — exile is
  usually still allowed")` pending the rule confirmation in
  `mechanics/day-engine.md` (findings 34, 35).
- A passed exile gets its own action row in the Nominations stage, mirroring the
  Dusk card: `[ Exile Rae ]`.

### I. Dusk transition and the day summary

```kotlin
// engine/.../DaySummary.kt  (new) — derived, not stored
data class DaySummary(
    val day: Int,
    val deathsToday: List<DeathRecord>,          // day-phase deaths, any cause
    val execution: ExecutionRecord?,             // null only for a day never closed
    val executionOccurred: Boolean,
    val someoneDiedToday: Boolean,               // Zombuul / Godfather gate
    val outsiderDiedToday: Boolean,              // Godfather gate
    val nominations: List<Nomination>,
    val exiles: List<Nomination>,
    val statements: List<PublicStatement>,
    val abilityUses: List<DayAbilityUse>,
)

object DaySummaryBuilder { fun forDay(state: GameState, day: Int, lookup: (String) -> Character?): DaySummary }
```

Derived rather than stored, so undo/redo stay trivial and no save migration is
needed beyond the three new list fields (`statements`, `dayAbilityUses`,
`executions`), all defaulting to empty.

Consumers, all of which currently re-derive or cannot:
`InfoCalc.undertaker` (`InfoCalc.kt:281-293`), `InfoCalc.townCrier` (`:295-305`),
`InfoCalc.flowergirl` (`:307-323`), the Gossip night step
(`gossip.md` §C), the Juggler night step, the Zombuul and Godfather wake
conditions, the Cannibal, the Minstrel, and the night sheet's opening DUSK row —
which should read *"Day 3: Fay was executed and did not die. Nobody died today."*

The phase button moves off the top bar (fixes finding 37): during `Phase.DAY`
the only way to night is the Dusk card's **[Everyone, eyes closed ▸]**; during
`Phase.NIGHT` the Night tab gets the equivalent **[Dawn ▸]** as its last row.
The top bar keeps hide-grimoire, undo, redo and the menu.

### J. Timer

```
 collapsed (in the bottom bar):
 │  ⏱ 4:58  │    + Say    │     Nominate      │
        ↑ tap = expand, never cancel

 expanded:
 ┌────────────────────────────────────────────┐
 │   4:58        [ ‖ ]  [ +1m ]  [ ↺ ]  [ × ] │
 │   1m   2m   3m   5m   8m   ⌨ custom        │
 │   ☑ chime   ☑ vibrate   ☑ warn at 1:00     │
 └────────────────────────────────────────────┘
```

- **Hoist the state.** Move `endAt` out of `DiscussionTimer` into `GameShell`
  (or the view model) so it is not destroyed by tab changes (fixes finding 40).
  Rendering it in the persistent bottom bar makes the composable permanent
  during the day anyway; keeping the deadline in `GameViewModel` also makes it
  survive process death on the PWA.
- **Alarm.** Add `fun alertAtTable()` to the platform seam (Android:
  `Vibrator` + a short tone; web: `navigator.vibrate` + a `WebAudio` beep),
  called once on expiry, with the pill turning to a full-width red
  **"TIME — tap to dismiss"** bar (fixes finding 41).
- **Controls.** Pause/resume, +1m, reset, close — four discrete targets, so
  glancing at the time never cancels it (fixes finding 42).
- **Presets** 1/2/3/5/8m plus a custom entry, and the last-used value becomes the
  bottom bar's one-tap default (fixes finding 43).
- **Tick at 1s** when more than 60s remain, 250ms only in the last ten seconds
  (fixes finding 44).
- A **"chats" convention**: starting the timer from the bottom bar during the
  briefing stage automatically advances the day timeline to the *Talk* stage, so
  the storyteller's "you have five minutes" gesture is one tap.

### K. Log and menu

- Fix the log sort (finding 45) by giving every log entry an explicit ordinal.
  The cheapest correct key is a monotonically increasing `seq` stamped by
  `GameViewModel.update` / `WebGameViewModel.update`; failing that, sort day
  entries as `nominations before deaths` within the same `day, !atNight` bucket.
- Extend the log to statements, day-ability uses, executions-without-death,
  resurrections, exiles and briefing announcements — i.e. render it from
  `DaySummaryBuilder` per day, with a day filter and a `heightIn(max = …)` cap
  like the shell's other list dialogs (`GameShell.kt:561, 627`).
- Promote **Log**, **Notes**, **Show a card** and **Jinxes** out of the 12-item
  dropdown (finding 47) into the Day tab's overflow row or a two-column menu.
- Re-parent the Mastermind banner inside the `Scaffold` content as a proper
  top-anchored strip (finding 48), replacing the hardcoded `top = 100.dp`
  (`GameShell.kt:520-537`).

### UI text to display (storyteller voice, short, imperative)

| Where | Text |
|---|---|
| Dawn card empty | `Nobody died last night. Say so.` |
| Dawn card header | `Dawn · Day 3 — 3 things to announce` |
| Resurrection line | `Sam is alive again. Re-run their first night before you open the day.` |
| Fearmonger line | `The Fearmonger has chosen a new player. Announce it.` |
| Psychopath ask | `Ask the Psychopath before opening nominations.` |
| Statement composer empty | `Who said what? Tap a seat, then type or dictate one line.` |
| Statement composer, no text | `Claims…` (opens the character grid) |
| Gossip nudge | `No Gossip statement recorded today.  [ Record it ]` |
| Nomination ring idle | `Tap who nominates, then who they nominate.` |
| Blocked seat | `Cy is dead — dead players can't nominate.  [ Allow anyway ]` |
| Unusual seat | `Ana already nominated today. Banshee? Butcher? Riot?  [ Allow anyway ]` |
| Vote sweep | `Votes for Fay, starting now — clockwise from her left.` |
| Uncounted hand | `Bo's hand is up but doesn't count — the Butler's master voted no.` |
| Tie | `Tie at 5 — Fay and Gus. No one is about to die. 6 to beat it.` |
| Nominations closing | `Last call for nominations! 3… 2… 1…` |
| Dusk, no block | `No one is about to die. There is no execution today.` |
| Dusk, block | `On the block: Fay — 5 votes.` |
| Vortox at dusk | `No execution and a Vortox is in play — evil wins. Check before you continue.` |
| Mayor at dusk | `3 players alive and no execution — good wins if the Mayor is sober.` |
| Execution, survives | `Fay was executed — and is still alive. Never say why.` |
| Execution, dies | `Fay is executed and dies. Announce it.` |
| Secret mode | `Eyes closed, everyone. (If asked: an Organ Grinder is in play.)` |
| Timer expiry | `TIME — tap to dismiss` |
| To night | `Everyone, eyes closed ▸` |

### Data changes

None to `characters.json` or `night_and_jinxes.json` for this topic.

- `night_guide.json` — no change; the DAWN row's dynamic text is built in
  `NightOrder.kt:59`, not in the guide.
- `NightOrder.kt:59` — replace the fixed DAWN detail with the day's ANNOUNCE
  lines from `DayBriefing`.
- `GameState` gains five defaulted fields (`statements`, `nextStatementId`,
  `dayAbilityUses`, `executions`, `briefingDone`) — all with defaults so existing
  saves deserialise unchanged.
- Every new `GameActions` verb needs a wrapper in **both** view models:
  `app/src/main/java/com/clocktower/grimoire/ui/GameViewModel.kt:194-223` and
  `web/src/wasmJsMain/kotlin/com/clocktower/grimoire/ui/WebGameViewModel.kt:172-199`.
  The web build compiles the same `app/` screens against its own hand-written
  view model, so a missing wrapper breaks the PWA the user actually plays on.
- New platform-seam functions (`alertAtTable`, `rememberDictation`) must be added
  to `app/src/main/java/com/clocktower/grimoire/ui/platform/Platform.kt` **and**
  `web/src/wasmJsMain/kotlin/com/clocktower/grimoire/ui/platform/WebUiPlatform.kt`.

---

## Tests to add

Engine tests (`engine/src/test/kotlin/com/clocktower/engine/`), all of which
fail today.

1. **Dawn briefing names the night's dead.**
   Given a game at night 2 where Kip was killed by the Demon;
   When `advancePhase` runs and `DayBriefing.forDay` is called;
   Then it contains an `ANNOUNCE` item whose text names Kip, and no item for a
   player who died on night 1.

2. **Dawn briefing announces a resurrection and asks for a first-night re-run.**
   Given the Professor resurrected the Chef on night 3;
   When `DayBriefing.forDay` runs on day 3;
   Then there is an `ANNOUNCE` "…is alive again" item **and** a `PRIVATE_WORD`
   item with `action = RerunFirstNight(chefId)`.

3. **Standing facts are derived from tokens.**
   Given `devilsadvocate:Survives execution` on Alice and `butler:Master` on Bo;
   When `DayBriefing.forDay` runs;
   Then both appear as `STANDING_FACT` items, and after `advancePhase` to night
   (which clears both, `GameActions.kt:231-242`) the next day's briefing has
   neither.

4. **Execution respects a Devil's Advocate protection and still records the
   execution.**
   Given Alice is on the block and marked `Survives execution`;
   When `Execution.resolve(...)` runs;
   Then it returns `Survives(reason = "Devil's Advocate")`, `apply` adds **no**
   `DeathRecord`, and `state.executions` gains
   `ExecutionRecord(day, aliceId, died = false)`.

5. **A no-execution day is recorded.**
   Given nobody is about to die at dusk;
   When the storyteller chooses "No execution today";
   Then `state.executions` gains `ExecutionRecord(day, nomineeId = null)` and
   `DaySummaryBuilder.forDay(...).executionOccurred` is `false`.

6. **Vortox ends the game on a no-execution day.**
   Given a Vortox in play and a recorded no-execution day;
   When `WinCheck.checkAtDusk` runs;
   Then it returns an advisory with `goodWins = false`.

7. **Mayor is checked at 3 alive with no execution.**
   Given 3 living players, a sober Mayor and a no-execution day;
   When `WinCheck.checkAtDusk` runs;
   Then it returns `goodWins = true`.

8. **Weighted votes reach the threshold.**
   Given 8 alive (threshold 4) and a Bureaucrat's `3 votes` token on Bo;
   When Bo and one other vote;
   Then the tally is 4 and the outcome is `ABOUT_TO_DIE`.

9. **A negative vote lowers the tally.**
   Given a Thief's `Negative vote` token on Bo, and Bo plus four others voting;
   Then the tally is 3, not 5.

10. **An uncounted hand does not count.**
    Given a Butler whose master's hand is down;
    When the Butler's `VoteEntry` is recorded with `counted = false`;
    Then the tally excludes it, and `Nomination.voterIds` (used by
    `InfoCalc.flowergirl`) excludes it too.

11. **Withdrawing a nomination restores the block and the ghost votes.**
    Given nomination A passed with 5 votes (Alice on the block) and a dead voter
    spent a ghost vote;
    When the nomination is withdrawn;
    Then `GameActions.aboutToDie` returns null, `highestVotesToday` returns 0,
    and the dead voter's `ghostVoteUsed` is false again.

12. **The execution record survives a character change.**
    Given Alice was executed on day 2 and became a different character on night 3;
    Then `DaySummaryBuilder.forDay(state, 2)` still reports Alice as the
    day-2 execution (snapshot semantics, matching
    `DeathRecord.characterIdAtDeath`, `GameState.kt:85`).

13. **`someoneDiedToday` gates the Zombuul.**
    Given a day with an execution that killed nobody and no other day deaths;
    Then `DaySummary.someoneDiedToday` is false (so the Zombuul may kill), while
    `executionOccurred` is true (so the Undertaker still learns nothing).

14. **Statements survive to the night that consumes them.**
    Given a `GOSSIP` statement recorded on day 2 and marked true;
    When night 3 begins;
    Then `DaySummaryBuilder.forDay(state, 2).statements` contains it with
    `resolvedOnNight == null` until the Gossip step resolves it.

15. **The log orders a nomination before the execution it caused.**
    Given a day-3 nomination that reached the block and a day-3 execution;
    When the log entries are built;
    Then the nomination precedes the death.

UI-level checks (no Android SDK required —
`./gradlew -p tools/uicheck compileKotlin`):

16. Every new composable type-checks in the multiplatform checker, and both
    `GameViewModel`s expose every new wrapper (otherwise the PWA build breaks).
17. `DiscussionTimer`'s deadline is passed in as a parameter, not held in
    `rememberSaveable` inside the composable — a compile-level guarantee that
    finding 40 cannot regress.
