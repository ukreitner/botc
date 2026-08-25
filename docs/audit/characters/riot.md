# Riot (riot) — Experimental Demon

## Official rules (sources)

Sources: <https://wiki.bloodontheclocktower.com/Riot> (fetched 2026-08-25), jinx
list <https://wiki.bloodontheclocktower.com/Djinn>, cross-checked against
<https://www.botcscriptorium.com/characters/riot/> and
<https://wiki.bloodontheclocktower.com/Farmer>.

**Current ability text (verbatim):**
> "On day 3, Minions become Riot & nominees die but nominate an alive player immediately. This must happen."

This is **not** the text in `characters.json`. See D1.

**How to Run (verbatim, complete):**
> "On the 1st day, add the **DAY 1** reminder to the Grimoire. On the 2nd day, add the **DAY 2** reminder to the Grimoire. On the 3rd day, add the **DAY 3** reminder to the Grimoire.
>
> On the 3rd night, wake each Minion. Show the **YOU ARE** info token, then the Riot token, then put each Minion to sleep. Either now, or later during the 3rd day, replace all Minion tokens with Riot tokens.
>
> During the 3rd day, each time a player is nominated, they die. Declare that they are dead, and add a shroud immediately. Tell them to nominate again and publicly count down '3... 2... 1...' After they nominate, repeat this process until all Riot are dead, or just 2 players are alive. Declare which team has won.
>
> If the players do not nominate at all on the 3rd day, or if a nominated player does not nominate before their time runs out, nominate a player – they die and must then nominate again."

**Example (verbatim):**
> "Alex nominates Lewis. Lewis dies and nominates Ben. Ben dies and nominates Marianna. Marianna dies and nominates Lachlan. Lachlan dies. All Riot players are dead. Good wins."

**Rules that follow:**
- Days 1 and 2 are **completely normal** days: normal nominations, normal votes,
  normal executions. Nothing on the wiki says nominees die before day 3. The Riot's
  only day-1/day-2 job is the DAY reminder token.
- Minions are dealt as **normal Minions** and play their own abilities for two full
  days and three nights. They become Riot on **night 3 / day 3**. There is therefore
  **no setup modifier** — the current ability text has no square bracket, and the
  repo's own upstream file `raw_exp_evil_outsiders.json:484-499` records
  `"setup": false`.
- On day 3 there are **no votes and no execution**: a nomination itself kills.
  The chain runs nomination → death → forced re-nomination by the dead player →
  … The dying player nominates an **alive** player.
- Terminating conditions: **all Riot dead → good wins**; **exactly 2 players alive →
  evil wins** (the remaining Riot is one of them). The wiki adds that if the last
  Riot dies at the moment only two players remain, they do not nominate and good
  wins.
- Dead players nominate. This is the only place in the game where that happens.
- The Storyteller must nominate if the players stall or a countdown expires.
- Riot is one of the ids that may legally appear multiple times in a bag (all the
  Minions become copies of it on day 3).

**Jinxes (verbatim, from the Djinn page; confirmed on the Riot and Farmer pages):**
- Riot / Atheist: "During a riot, if the Storyteller is nominated, players vote. If
  they are 'about to die', the game ends. If not, they nominate again."
- Riot / Banshee: "Each night\*, Riot chooses an alive good player (different to
  previous nights): a chosen Banshee dies & gains their ability."
- Riot / Exorcist: "If Riot nominates and executes the Exorcist-chosen player, good
  wins."
- Riot / Farmer: "Each night\*, Riot chooses an alive good player (different to
  previous nights): a chosen Farmer uses their ability but does not die."
- Riot / Grandmother: "If Riot is in play and the Grandchild dies by execution, evil
  wins."
- Riot / Innkeeper: "If Riot nominates and executes an Innkeeper-protected player,
  good wins."
- Riot / King: "If Riot is in play, and at least 1 player is dead, the King learns an
  alive character each night."
- Riot / Mayor: "The Mayor may choose to stop the riot. If they do so when only 1
  Riot is alive, good wins. Otherwise, evil wins."
- Riot / Monk: "If Riot nominates and executes the Monk-protected player, good wins."
- Riot / Ravenkeeper: "Each night\*, Riot chooses an alive good player (different to
  previous nights): a chosen Ravenkeeper uses their ability but does not die."
- Riot / Sage: "Each night\*, Riot chooses an alive good player (different to
  previous nights): a chosen Sage uses their ability but does not die."
- Riot / Soldier: "If Riot nominates and executes the Soldier, good wins."

**Flagged as surprising — verify before implementing the four "each night\*"
jinxes.** Riot has no night ability in its own text, yet the Banshee / Farmer /
Ravenkeeper / Sage jinxes grant it a nightly "choose an alive good player (different
to previous nights)" kill. The identical wording is used for the **Leviathan**
jinxes with the same four characters, so these read like a deliberate pattern for
day-only Demons rather than a transcription error — but all three of my sources are
the same wiki content, so I could not corroborate them independently. The repo
carries the **older** form of the same four jinxes (e.g. "If Riot kills the Farmer, a
good player becomes a Farmer tonight", `night_and_jinxes.json:214-217`). An
implementer should confirm against a printed script/official script tool before
wiring a Riot night kill.

## What the app does today

**Data — stale and self-inconsistent.**
- `characters.json:2074-2088` carries the **superseded** ability
  (`:2078`, "Nominees die, but may nominate again immediately (on day 3, they must).
  After day 3, evil wins. [All Minions are Riot]") and `"setup": true` (`:2079`).
  The repo's own upstream file `raw_exp_evil_outsiders.json:484-499` has the current
  text and `"setup": false` — so `characters.json` has drifted from its own source.
  Reminders `["Day 1","Day 2","Day 3"]` are correct.
- `night_and_jinxes.json` — **absent** from `firstNight` (correct); present in
  `otherNight` at index 53 (`:426`), between `kazali` and `leviathan`. Jinxes present:
  riot/saint (`:208-212`), riot/farmer (`:213-217`), riot/ravenkeeper (`:218-222`),
  riot/sage (`:223-227`), riot/grandmother (`:228-232`) — five, all in the older
  wording, and riot/saint is not on the current wiki jinx list at all. Eight current
  jinxes are missing (Atheist, Banshee, Exorcist, Innkeeper, King, Mayor, Monk,
  Soldier).
- `night_guide.json:1658-1670` — the `other` instructions describe the **old**
  ruleset: "During the day, each nominee dies immediately and may (on day 3, must)
  nominate another player straight away; if day 3 ends with good not having won, evil
  wins." Under the current rules nominees do **not** die on days 1-2 and "after day 3
  evil wins" no longer exists.
- `Setup.kt:72` — `TEAM_WARPING_IDS = setOf("atheist", "legion", "riot")`, so
  `modifierFor` (`:127-129`) returns a modifier with `choiceTeams = Team.entries`,
  relaxing **every** team count in `validateBag` (`GameActions.kt:432-478`). Under
  the current rules Riot has no setup modifier at all, so this silently disables all
  distribution validation for any Riot script. `SetupTest.kt:105-111` asserts the
  current (wrong) behaviour.
- `GameActions.DUPLICABLE` (`:413`) includes `riot` — still needed, because on day 3
  every Minion seat becomes `characterId = "riot"`.

**Night 2+ (the storyteller's actual experience)**
- The step detail is the `otherNightReminder` (`characters.json:2081`), which is
  correct advice.
- `QuickResolutions` (`NightScreen.kt:462-525`) has no `riot` case, so the `else`
  branch (`:518-523`) fires — team is DEMON, holder is alive — and renders
  **`DemonKillPanel`** (`:534-638`): "Demon kill — who did <name> choose?" with a
  live kill button, on **every night**. Riot has no night kill.
- The DAY 1/2/3 tokens can only be placed from the `NightToolTray`
  (`NightScreen.kt:283-354`) while the Riot step is expanded, one tap for the chip
  and one for a seat — and they are seat-bound tokens for a fact about the *day*, so
  the ST has to park them on an arbitrary player. They are in neither expiry table
  (`GameActions.kt:218-242`), so DAY 1 and DAY 2 accumulate unless removed by hand.
- Night 3's "wake each Minion, show YOU ARE + Riot token" is prose only. There is no
  action that converts the Minion seats, and `night_guide.json:1661-1668` offers a
  single `token: "self"` show card that must be re-opened per Minion.

**Day 3 — the part that matters most, and is blocked**
- `DayScreen.kt:131-140`: the **Nominator** chips are enabled only for
  `p.alive && !GameActions.hasNominatedToday(state, p.id)`. The Riot chain requires
  the just-killed nominee to nominate. **A dead player can never be selected as a
  nominator**, so the chain cannot be recorded at all.
- `DayScreen.kt:141-152`: **Nominee** chips require `p.alive` too, which is right,
  and `!hasBeenNominatedToday`, which happens to be harmless (a Riot nominee is dead
  afterwards).
- `DayScreen.kt:161-252`: selecting a nominee opens the vote tally, computes a
  threshold (`Voting.executionThreshold`, `GameState.kt:125`), a "tally to beat"
  (`GameActions.highestVotesToday`, `:278-282`) and an about-to-die/tie verdict
  (`Voting.outcome`, `GameState.kt:147-152`). **None of this applies on day 3** —
  the nomination itself kills, with no vote. Recording a nomination without ticking
  any voter yields `SAFE`, and the nominee stays alive.
- `GameActions.aboutToDie` (`:296-306`) and the "On the block" banner
  (`DayScreen.kt:93-115`) are meaningless during a riot, and the **dusk guard**
  (`GameShell.kt:141-146`) will refuse to advance the phase if a stale
  ABOUT_TO_DIE record exists.
- Nothing forces the next nomination, counts down, or tells the ST that the chain
  must continue.
- `WinCheck.check` (`WinCheck.kt:88-98`) does return "evil wins" at 2 alive with a
  living demon, which coincidentally matches one Riot ending, and `:70-86` returns
  "good wins" when every Riot is dead — but neither is worded for a riot and neither
  knows the chain is still running.

**Holder resolution — broken by construction for Riot.**
`NightScreen.kt:467` takes `step.playerIds.firstOrNull()` as *the* holder and `:520`
requires it to be alive. Riot is the one character that is *supposed* to occupy
several seats at once (every Minion becomes a Riot). `NightToolTray`'s
`holders` (`:205`) does collect all of them, but `QuickResolutions` and
`DemonKillPanel` are single-holder by design. The `allDead` banner
(`NightScreen.kt:702`, `:751-757`) is the only multi-holder-aware piece. This is the
same defect family as the reported `GameActions.starPass` bug (`:79-96` leaves the
old Demon's `characterId` in place, producing two Demon-team seats picked by seat
index) — Riot just hits it every single game.

## Defects and gaps

1. **P0 · `characters.json` carries a superseded ability and a wrong `setup` flag.**
   Wiki: "On day 3, Minions become Riot & nominees die but nominate an alive player
   immediately. This must happen." / `setup: false`.
   App: `characters.json:2078-2079` has the pre-revision text and `setup: true`. The
   repo's own `raw_exp_evil_outsiders.json:487-488` already has the correct values,
   so this is pure drift. Consequence: the ST reads the wrong rule off the Script
   tab (`ReferenceScreen.kt`), and setup validation is disabled (D3).

2. **P0 · The day-3 nomination chain cannot be recorded: dead players cannot
   nominate.** Rules: "Tell them to nominate again". App: `DayScreen.kt:135-138`
   enables the nominator chip only for `p.alive`. Repro: day 3 of a Riot game,
   nominate Lewis, kill Lewis, then try to select Lewis as the next nominator — his
   chip is disabled. The ST must abandon the app for the entire endgame.

3. **P0 · Nominations on day 3 do not kill; the app demands a vote.**
   Rules: "each time a player is nominated, they die … add a shroud immediately."
   App: `DayScreen.kt:161-252` runs the full vote/threshold/tie machinery and records
   `SAFE` for an unvoted nomination; nobody dies until the ST separately taps
   Execute. Repro: day 3, record a nomination with zero voters — the nominee lives.

4. **P0 · `DemonKillPanel` offers a night kill that Riot does not have.**
   `NightScreen.kt:518-523` → `:534-638`, every night. Repro: Riot game, night 2,
   expand the Riot step. (If the four "each night\*" jinxes above are confirmed, a
   *jinxed* Riot does gain a night choice — but with completely different targeting
   rules: alive, good, different from previous nights. The generic panel is wrong
   either way.)

5. **P0 · The `night_guide` text states two rules that no longer exist.**
   `night_guide.json:1660` — nominees dying on days 1-2, and "if day 3 ends with good
   not having won, evil wins". A storyteller following this guide will kill players
   on day 1 who should not die.

6. **P1 · Setup validation is silently disabled for every Riot script.**
   `Setup.kt:72` puts `riot` in `TEAM_WARPING_IDS`; `:127-129` then relaxes all four
   team counts. Under the current rules Riot's bag is a completely ordinary
   distribution (1 Demon, base Minions, base Outsiders) and should be validated
   normally. `SetupTest.kt:105-111` locks in the wrong behaviour and must change with
   it.

7. **P1 · Night 3 Minion→Riot conversion is manual and unguided.**
   Rules: wake each Minion, show YOU ARE + Riot, replace all Minion tokens with Riot
   tokens. App: prose plus one re-openable show card
   (`night_guide.json:1661-1668`); each conversion is Grimoire → seat → change
   character. Nothing lists the Minions, nothing tracks which have been shown, and
   nothing records their original characters (needed for the reveal at the end).

8. **P1 · The DAY 1/2/3 reminder is a manual, seat-bound, non-expiring token.**
   It is a fact about the current day, not about a player. It must be placed by hand
   from the night tray and removed by hand (`GameActions.kt:218-242` has no entry for
   it), so by day 3 the grimoire carries three stale tokens.

9. **P1 · Eight current jinxes are missing and five are stale.**
   `night_and_jinxes.json:208-232` holds riot/saint (not on the current list) and
   older wordings for farmer/ravenkeeper/sage/grandmother. Missing: Atheist, Banshee,
   Exorcist, Innkeeper, King, Mayor, Monk, Soldier. Four of these
   (Exorcist/Innkeeper/Monk/Soldier) are **instant good wins** that fire in the middle
   of the day-3 chain — the ST must be told at the moment of nomination, and today
   they are not even listed in "Jinxes in play" (`GameExtras.kt:202-231`).

10. **P1 · No riot-aware win detection.**
    `WinCheck.kt:70-98` knows nothing about the chain. It cannot say "keep going, 3
    Riot still alive", it does not fire the Grandmother jinx ("If Riot is in play and
    the Grandchild dies by execution, evil wins"), and it offers no Mayor "stop the
    riot" branch.

11. **P2 · The dusk guard blocks the phase on a stale block record.**
    `GameShell.kt:141-146` refuses to advance while `aboutToDie` names a living
    player. During a riot the block concept does not exist, so any leftover
    ABOUT_TO_DIE from day 2 can strand the ST.

12. **P2 · No countdown timer wired to the chain.**
    A `DiscussionTimer` already exists (`GameShell.kt:315-320`) but is not connected
    to the mandatory "3… 2… 1…" per link of the chain, nor to the "if their time runs
    out, the Storyteller nominates" rule.

13. **P2 · Multi-holder steps are single-holder in the UI.**
    `NightScreen.kt:467`, `:520`. See the holder-resolution paragraph above.

14. **P3 · `otherNightReminder` still says "you may wake the Minions" as if optional
    on any night**; the official text scopes it to night 3 only, which the app could
    derive from `state.cycle`.

## Proposed behaviour (spec)

### Data corrections (do these first)

- `characters.json:2078` → `"On day 3, Minions become Riot & nominees die but
  nominate an alive player immediately. This must happen."`
- `characters.json:2079` → `"setup": false`.
- `Setup.kt:72` → `TEAM_WARPING_IDS = setOf("atheist", "legion")`; update
  `SetupTest.kt:105-111` to match, and add a test that a Riot bag is validated with
  the normal distribution.
- Keep `riot` in `GameActions.DUPLICABLE` (`:413`) — still required for day 3.
- `night_and_jinxes.json` — replace the five stale Riot jinxes with the twelve
  current ones quoted above (subject to the verification note).
- `night_guide.json:1658-1670` — rewrite per the UI text below; add a `first`
  entry? **No** — Riot has no first-night action; leave `first` absent.

### Night action — structured form

- **when:** other nights. The Riot itself is **not woken** on nights 1-2.
- **wake condition (night 3 only):** the game reaches night 3 and at least one
  Minion is alive.
- **targets (night 3):** every seat whose character is a Minion (computed, not
  typed). Dead Minions are included — a dead Riot still nominates on day 3 and still
  counts for "until all Riot are dead", so their tokens must be replaced too.
- **immediate effects (night 3):** for each Minion seat, a checklist row with
  "Show YOU ARE + Riot" (fires `ShowCard.CharacterCard("YOU ARE", "riot")` then the
  existing evil alignment card) and "Convert" (applies
  `GameActions.assignCharacter(seat, "riot")`, writes the previous character into
  `Player.note` and the log). A "Convert all" button does the lot. The step is not
  done until every Minion is converted.
- **day marker:** replace the seat-bound DAY 1/2/3 tokens with a derived banner. The
  app already knows `state.cycle`; the Riot step and the Day tab should simply read
  **"Riot: day <cycle> of 3"** / **"Riot: the riot begins tomorrow"** /
  **"Riot: THE RIOT IS TODAY"**. Keep the tokens in `characters.json` for
  physical-grimoire parity but auto-place and auto-expire them: add
  `("riot","Day 1")`, `("riot","Day 2")` to `EXPIRES_AT_DUSK`
  (`GameActions.kt:231-242`) and place the new one at dawn.
- **jinxed night choice (only if the four "each night\*" jinxes are confirmed):**
  when `riot` is jinxed with any of banshee / farmer / ravenkeeper / sage, add a
  night step on nights 2+ with: 1 target, **alive**, **good**, **different from every
  previously chosen player** (persist the history), then apply the per-character
  jinx outcome (Banshee dies and gains ability; Farmer/Ravenkeeper/Sage use their
  ability and do **not** die). Otherwise no night action at all.
- **expiry:** DAY tokens as above; nothing else.
- **visibility:** on night 3 each Minion sees YOU ARE + the Riot token. The Demon is
  not woken.
- **UI text (night 2, i.e. before the riot):** *"Riot does not kill at night.
  Tomorrow is day <n> of 3."*
  **(night 3):** *"Tomorrow is the riot. Wake each Minion in turn, show YOU ARE and
  the Riot token, then replace their character token with the Riot token. Minions:
  <names>."*

### Day 3 — a dedicated riot mode for `DayScreen`

Detect `riotActive = state.cycle >= 3 && any seat has characterId == "riot"`, and
replace the normal nomination card with a **Riot chain** card:

- **Chain state (new):** `RiotChain(active, currentNominatorId, links: List<(nominator,
  nominee)>)`, persisted in `GameState` so undo/redo and the log work.
- **Start:** "Begin the riot" button, or the first nomination of day 3. If the
  players stall, a "Storyteller nominates" button picks the first nominee (per the
  wiki's fallback rule).
- **Each link:** one screen, one tap.
  1. It states who must nominate: *"<Name> must nominate — 3… 2… 1…"* with a live
     countdown wired to the existing `DiscussionTimer` and a "time ran out —
     Storyteller nominates" fallback.
  2. A single row of chips: **alive players only**, excluding the nominator.
     Nothing else — no vote UI, no threshold, no tally.
  3. Tapping a chip, in **one undoable action**: records the nomination
     (`Nomination(day, nominator, nominee, votes = 0, result = ABOUT_TO_DIE,
     isExile = false)` — or better, a new `NominationKind.RIOT` so the vote-derived
     helpers ignore it), kills the nominee with a new `DeathCause.RIOT`, and sets
     `currentNominatorId = nominee`.
  4. Before the kill lands, run `StatusEffects.deathNotes` **and the four
     instant-win jinxes**: if the nominee is Monk-protected, Innkeeper-protected,
     Exorcist-chosen, or the Soldier, stop and show *"Riot nominated a
     <Monk-protected> player — GOOD WINS"* with a "Declare good victory" button.
     If the nominee holds the `grandmother:Grandchild` token, show *"The Grandchild
     died — EVIL WINS"*.
  - Dead players **must** be selectable as nominators here; the `p.alive` predicate
    at `DayScreen.kt:135-138` must be bypassed in riot mode.
  - `hasNominatedToday` / `hasBeenNominatedToday` (`GameActions.kt:285-289`) must not
    constrain the chain: each player nominates exactly once anyway, but the guard
    should be explicitly disabled so a Storyteller-forced repeat is possible.
- **Termination, checked after every link:**
  - no seat with `characterId == "riot"` is alive → **"All Riot are dead — good
    wins."**
  - exactly 2 players alive → **"Two players remain — evil wins."** (Per the wiki,
    if the last Riot died on the link that brought the count to 2, good wins; check
    the Riot condition first.)
  - otherwise → *"<n> Riot still alive, <m> players alive — the riot continues."*
- **Mayor jinx:** a "Mayor stops the riot" button, enabled only while a living Mayor
  is in play, resolving to good wins if exactly one Riot is alive and evil wins
  otherwise.
- **Atheist jinx:** allow "the Storyteller" as a nominee; that link alone takes a
  vote, using the normal threshold, and ends the game if it passes.
- **Suppress** the block banner (`DayScreen.kt:93-115`), the dusk guard
  (`GameShell.kt:141-146`) and `GameActions.aboutToDie` while `riotActive`.

### Days 1 and 2
Completely normal. The only additions are the derived banner ("Riot: day 1 of 3")
and, at dawn of day 2, a briefing line *"Tomorrow the Minions become Riot — wake them
tonight."*

### Day-time inputs the app must record
The full chain, in order, in the game log: *"Day 3 riot — Alex » Lewis (died) »
Ben (died) » Marianna (died) » Lachlan (died). All Riot dead: good wins."* This is
the record the ST needs to explain the ending.

### Interactions/jinxes to handle explicitly
All twelve above. In particular the four instant-win checks must fire **at the
moment of nomination**, extending `StatusEffects.nominationWarnings`
(`StatusEffects.kt:132-166`) with a riot-aware branch, not just at death time.

## Tests to add

1. `Given` `characters.json`, `Then` the `riot` entry's `ability` and `setup` equal
   the values in `raw_exp_evil_outsiders.json` (a general data-parity test would
   catch this class repo-wide). **Fails today.**
2. `Given` a 10-player bag with a Riot and 2 normal Minions, `When`
   `validateBag(bag, 10)`, `Then` no issues **and** the distribution is actually
   checked (add a negative case: 3 Minions must now be rejected). **Fails today** —
   `TEAM_WARPING_IDS` relaxes everything.
3. `Given` day 3 with 6 alive and 3 Riot, `When` a riot nomination of Lewis is
   applied, `Then` Lewis is dead with `DeathCause.RIOT`, the chain's next nominator
   is Lewis, and no vote tally was required.
4. `Given` the chain's current nominator is a dead player, `Then` the engine accepts
   them as a nominator (today's `hasNominatedToday`/alive guards must not block).
5. `Given` day 3 and the chain kills the last living Riot, `Then` `WinCheck` returns
   `goodWins = true` with reason "All Riot are dead".
6. `Given` day 3 and the chain reduces the count to 2 alive with a Riot among them,
   `Then` `WinCheck` returns `goodWins = false`.
7. `Given` a riot nomination of a player holding `monk:Safe`, `Then` an
   instant-good-win advisory is raised **before** the kill is applied.
8. `Given` a riot nomination of the player holding `grandmother:Grandchild`,
   `Then` an evil-win advisory is raised.
9. `Given` night 3 with three Minions, `When` the conversion action runs, `Then` all
   three seats have `characterId == "riot"`, each retains its previous character in
   its note, and `NightOrder.otherNight` no longer contains their Minion steps.
10. `Given` night 2, `Then` the Riot night step exposes **no** kill action.
11. `Given` day 1 of a Riot game, `When` a player is nominated, `Then` the normal
    vote/threshold path applies and the nominee does **not** die on nomination.
12. `Given` day 2 → dusk, `Then` the `("riot","Day 2")` token has expired and
    `("riot","Day 3")` is placed at the next dawn.
