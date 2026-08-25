# Fiddler (fiddler) — fabled Fabled

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Fiddler> (fetched verbatim via
`api.php?action=parse&page=Fiddler&prop=wikitext`, 2026-08-25).

Current ability text (verbatim summary line):

> "Once per game, the Demon secretly chooses an opposing player: all players choose which of these 2 players win."

**Summary bullets (verbatim):**

- "Use the Fiddler to decide a winner if the game must end due to time constraints or a stalemate."
- "Sometimes there won't be enough time to finish a game. Maybe the venue you are playing at needs to close. Maybe some players need to leave unexpectedly and the game cannot continue without them. Maybe the Townsfolk refuse to execute and the Demon refuses to kill."
- "The Storyteller can add and activate the Fiddler at any time. To do so, all players close their eyes while the Demon chooses a good player to challenge to a fiddle contest. Then, after a minute or two, all players will raise their hands to vote on which of these two players wins. The game ends, and the winning player's entire team wins too."
- "**Like an exile, this group decision on who wins the game is not affected by abilities, and the dead may vote normally. The Thief cannot steal votes, the Voudon has no effect, and so on.**"
- "**Players cannot use their abilities once the Fiddler has been activated. The Slayer cannot choose to slay a player, the Artist cannot ask their question, and so on.**"
- "**If this fiddle contest is a tie, evil wins.**"

**How to Run (verbatim):**

> At any time, if you expect to run out of time, declare that the Fiddler is in play and declare the time when the game will end. Add the Fiddler token to the Grimoire.
>
> When the game needs to end, declare that you are using the Fiddler ability. Put all players to sleep. Wake the Demon. They point at any good player. (*If the Demon is good, they must choose an evil player instead.*) Wake all players and declare that the Demon player and the chosen player are in a fiddle contest. (*Do not say what their characters are.*) After a minute or two, run a "vote" for each player in the fiddle contest. (*This is not an execution vote.*) All players may vote. The player with the most votes wins, their team wins, and the game ends. On a tie, the evil team wins.

**Examples (verbatim):**

1. "The game begins but will need to end in 45 minutes due to a freak lightning storm approaching the neighbourhood, so the Storyteller adds the Fiddler. After 40 minutes, the Fiddler activates. The players choose the good player to win, so good wins."
2. "There are just four players left alive. Each day, nobody nominates. Each night, the Demon chooses a dead player to kill. Since this could go on indefinitely, the Storyteller adds the Fiddler so that the game can end."

**The procedure, as a state machine an app can drive:**

| Step | What happens |
|---|---|
| 0. Add | Storyteller declares the Fiddler is in play **and declares the end time**. Any point in the game, day or night. |
| 1. Activate | Storyteller declares the ability is being used. **All abilities stop working from this moment.** |
| 2. Sleep | All players close their eyes. |
| 3. Demon picks | Wake the Demon; they point at any **good** player (or, if the Demon is somehow good, any **evil** player). Secret. |
| 4. Announce | Wake everyone; name the two contestants. **Do not reveal their characters.** |
| 5. Discuss | "A minute or two." |
| 6. Vote | One vote per contestant. **All players may vote — living and dead, ghost votes irrelevant, no ability may affect it.** |
| 7. Resolve | Most votes wins; **that player's whole team wins**; game ends. **Tie → evil wins.** |

**Jinxes:** none.
**Night order:** never wakes in the normal sense — the Demon's pick is an ad-hoc wake
during the activation flow, not a night-order step. Correctly absent from both order lists.

## What the app does today

Data:
- `characters.json:2236-2247` — ability text matches the wiki exactly; `team: fabled`,
  `setup: false`, **no reminders**, no night reminders. **Works** as data.
- `night_and_jinxes.json` — correctly absent from both order lists. **Works.**
- `night_guide.json` — no entry. The Fiddler's How-to-Run is a seven-step procedure and it
  is nowhere in the app.

Code — **zero** engine awareness. The Fiddler is a toggle
(`GameExtras.kt:167-195` → `GameActions.setFabled`, `GameActions.kt:211-212`) and a token
in the grimoire corner (`GrimoireScreen.kt:215-218`).

The pieces the flow would need, all present and unconnected:
- Put everyone to sleep / hide the grimoire: `PrivacyCover` (`components/PrivacyCover.kt`),
  driven by `grimoireLocked` (`GameShell.kt:502-505`), and the red night scrim
  (`GameShell.kt:322-330`).
- Show something full-screen to one player: `FullScreenShow` / `ShowCard`
  (`ShowCards.kt:65-77`), already used for the Demon's bluffs
  (`NightScreen.kt:775-780`).
- A one-to-two-minute timer: `DiscussionTimer` presets 1m/2m/5m (`Timer.kt:88`).
- A tap-to-count vote tally with clockwise ordering:
  `DayScreen.kt:161-251` — but it is welded to `Nomination`.
- Declaring a winner and showing the cast: menu items "Declare good victory" /
  "Declare evil victory" (`GameShell.kt:257-265`) → `RevealSheet`
  (`GameExtras.kt:268-350`).

So the storyteller running a Fiddler ending today must: remember the seven steps from the
almanac; lock the grimoire by hand; work out who the Demon is and whether they are evil;
hand the phone over or use the show-card tool to take the Demon's pick; announce; start a
timer; run **two** vote tallies with no tool that fits (using `DayScreen`'s nomination
form would write bogus `Nomination` records into `state.nominations`, corrupting
`aboutToDie` (`GameActions.kt:296-306`), `highestVotesToday` (`GameActions.kt:278-282`),
the one-nomination-per-day guards (`GameActions.kt:285-289`) and the game log
(`GameExtras.kt:65-78`), and would spend dead players' ghost votes at
`DayScreen.kt:232-240` — all of which the rules forbid); count by hand; apply the tie rule
from memory; then pick the right "Declare … victory" menu item.

## Defects and gaps

1. **P1** · No activation flow at all. A seven-step, rules-exact procedure — including a
   secret Demon choice and a special vote — is left entirely to the storyteller's memory at
   the most time-pressured moment of the evening (the venue is closing; that is the
   Fiddler's whole premise).
2. **P1** · No two-candidate vote tool. The only tally in the app is the nomination form
   (`DayScreen.kt:161-251`), and using it is actively harmful: it writes a `Nomination`
   into `state.nominations` (`GameActions.recordNomination`, `GameActions.kt:274-275`),
   which then feeds `aboutToDie`, `highestVotesToday`, `hasNominatedToday`,
   `hasBeenNominatedToday` and the log. *Repro:* try to run a fiddle vote on the Day tab —
   the nominee chip list disables dead players (`DayScreen.kt:146`, `p.alive`), which is
   exactly backwards for a contest the dead must be able to vote in.
3. **P1** · Ghost votes would be wrongly consumed. The rules say the fiddle vote is
   "like an exile … the dead may vote normally"; `DayScreen.kt:232-240` spends the ghost
   vote of every dead voter on a non-exile nomination, and `DayScreen.kt:184` disables the
   chip of any dead player who has already spent theirs. A fiddle vote must ignore
   `ghostVoteUsed` entirely, in both directions.
4. **P1** · The tie rule is not encoded. "On a tie, the evil team wins" is a hard rule and a
   very easy one to get wrong under time pressure; the app's only tie handling is
   `Voting.outcome` (`GameState.kt:147-152`), which is about execution blocks and does not
   apply.
5. **P1** · Nothing stops abilities after activation. "Players cannot use their abilities
   once the Fiddler has been activated" — the app keeps offering
   `QuickResolutions` (`NightScreen.kt:461-527`), `DemonKillPanel`
   (`NightScreen.kt:534`), nomination warnings (`StatusEffects.kt:131-166`) and the
   Slayer/Artist/etc. reference as if the game were live.
6. **P2** · The Demon's choice has no picker and no constraint. The app knows who the Demon
   is (`lookup(characterId)?.team == Team.DEMON`, used at `NightOrder.kt:81-88` and
   `WinCheck.kt:21-22`) and knows every player's alignment including flips
   (`Player.isEvil`, `GameState.kt:49-52`), so "list the opposing-alignment players for the
   Demon to point at" is a three-line filter that does not exist.
7. **P2** · No "declare the end time" affordance. The How-to-Run's step 0 is *announce when
   the game will end* — wiki example 1 is a 45-minute deadline. The app's timer maxes out
   at 5 minutes (`Timer.kt:88`) and is not tied to any Fabled.
8. **P2** · Multiple Demons are unhandled. Legion, Riot, a Fang Gu jump or an
   Al-Hadikhia/Scarlet Woman split can leave several Demon-team players; the rules say
   "the Demon" and a real flow needs the storyteller to choose which one fiddles.
9. **P2** · No record. The reveal (`GameExtras.kt:268-350`) would show "GOOD WINS" with no
   trace that the game ended in a fiddle contest, who the contestants were, or the tally.
10. **P3** · No show cards for the contest ("You are in a fiddle contest", the two names,
    "Vote for who wins"), though `ShowCard.Message` (`ShowCards.kt:66`) would serve.

## Proposed behaviour (spec)

Shares the `FabledEntry` storage introduced in `angel.md`; the Fiddler uses `used: Boolean`
and `note` (the declared end time, then the result). The flow also needs a small piece of
game state so it survives tab switches, backgrounding and undo:

```kotlin
@Serializable
data class FiddleContest(
    val demonId: Long,
    val challengedId: Long,
    /** Ids of everyone whose hand is up for each contestant. */
    val votesForDemon: List<Long> = emptyList(),
    val votesForChallenged: List<Long> = emptyList(),
    val resolved: Boolean = false,
)
// GameState gains: val fiddle: FiddleContest? = null
```

- when: **any time**, day or night, at the storyteller's declaration. Never a night-order
  step; do not add `fiddler` to either order list.
- **Step 0 — add.** On adding the Fiddler, prompt:
  > **Fiddler added — when will the game end?**
  > Announce this to the group now. [time field / "+45 min"] → stored in `note`, shown as a
  > persistent banner "Game ends at 21:30 (Fiddler)". Optional; skippable.
- **Step 1 — activate.** A prominent "Use the Fiddler now" action in the main menu
  (`GameShell.kt:236-266`) and on the Fabled sheet row, guarded by a confirm
  ("This ends the game. Abilities stop working now."). On confirm:
  - set `fabled["fiddler"].used = true`;
  - set a global **abilities-off** flag that suppresses `QuickResolutions`
    (`NightScreen.kt:461`), `DemonKillPanel` (`NightScreen.kt:534`), the Fibbin offer, the
    nomination form (`DayScreen.kt:126-255`) and `StatusEffects.nominationWarnings`, each
    replaced by "Fiddler activated — abilities no longer work.";
  - engage `PrivacyCover` (`GameShell.kt:502-505`) — "Everyone close your eyes."
- **Step 2/3 — the Demon's pick.** Present the Demon-team players
  (`lookup(p.characterId)?.team == Team.DEMON`); if more than one, the storyteller picks
  which fiddles. Then show a picker of **opposing-alignment** players:
  `state.players.filter { it.isEvil(lookup) != demon.isEvil(lookup) }` — good players for
  an evil Demon, evil players for a good Demon (`Player.isEvil`, `GameState.kt:49-52`,
  already honours `alignmentFlipped`). Alive **and dead** are legal targets: the rules say
  "any good player" with no life restriction. Hand the phone to the Demon, or use a
  full-screen picker; record `FiddleContest(demonId, challengedId)`.
- **Step 4 — announce.** `ShowCard.Message("FIDDLE CONTEST", "Ana  vs  Ben")` — names only,
  **never characters** ("Do not say what their characters are"). Release the privacy cover.
- **Step 5 — discuss.** Offer the timer at 1m/2m, started from the flow.
- **Step 6 — the vote.** A dedicated two-column tally, modelled on `DayScreen.kt:179-205`
  but with different rules:
  - every seat appears in **both** columns and may be tapped in exactly one;
  - **dead players are enabled** regardless of `ghostVoteUsed`, and voting here **never**
    calls `toggleGhostVote` (contrast `DayScreen.kt:232-240`);
  - travellers vote;
  - the two contestants may vote (the rules do not exclude them);
  - no ability, jinx or nomination warning is consulted;
  - live totals with the standing result, and the tie rule stated on screen:
    **"A tie means evil wins."**
- **Step 7 — resolve.** One button:
  ```
  winner = when {
      votesForChallenged.size > votesForDemon.size -> challenged
      votesForDemon.size > votesForChallenged.size -> demon
      else -> the evil-team contestant      // tie -> evil wins
  }
  goodWins = !winner.isEvil(lookup)
  ```
  Then open `RevealSheet(goodWins)` (`GameExtras.kt:268-350`) directly — **not** the
  `WinAdvisoryDialog` (`GameExtras.kt:236-265`), because this outcome is not advisory.
  Write a log line: "Fiddle contest: Ana (Imp) 4 — Ben (Soldier) 6 → GOOD WINS", and add a
  line to the reveal header, "The game ended in a fiddle contest."
- targets/constraints summary: 1 Demon-team contestant (storyteller-chosen if several),
  1 opposing-alignment challenger (Demon-chosen, alive or dead).
- immediate effects: no deaths, no tokens, no character changes. The Fiddler kills nobody.
- deferred effects/expiry: none — the game is over.
- information: none computed; the contestants' characters stay hidden until the reveal.
- visibility: the two names are public; the Demon's identity is **not** revealed by being a
  contestant (the whole point of "do not say what their characters are"). The app must not
  render team colours or character tokens on the contest cards — note that
  `RevealSheet` colours names by alignment (`GameExtras.kt:307-311`), which is correct
  *after* the game ends but must not leak beforehand.
- day-time inputs: none.
- interactions:
  - **Abilities are off** from activation (rule quoted above): Slayer, Artist, Thief,
    Voudon, Butler's master restriction, Organ Grinder's eyes-closed voting, Bureaucrat and
    every vote-weight effect are all inert. Because the app doesn't model vote weights
    today this is mostly a matter of *not* reusing the nomination path.
  - **Ghost votes** are untouched, in both directions.
  - **Win conditions**: the fiddle result overrides everything —
    `WinCheck.check` (`WinCheck.kt:18-101`) must return null (or be bypassed) once
    `fiddle?.resolved == true`, so no Saint/Mastermind/Demons-dead advisory contradicts it.
  - **Mastermind day** (`GameState.mastermindDayActive`, `GameState.kt:111`) is cancelled
    by a fiddle resolution.
  - **A good Demon** (Snake Charmer swap at `GameActions.kt:64-72` leaves the ex-Demon
    good; Cult Leader; `alignmentFlipped`) is handled by the alignment-difference filter
    rather than a hard-coded "good player" list — this is the wiki's parenthetical
    "(If the Demon is good, they must choose an evil player instead.)"
  - **No Demon alive/in play at all** (Kazali edge cases, an all-Legion board): the flow
    must degrade gracefully — let the storyteller pick both contestants manually, with a
    warning.
  - **Fabled immunity**: nothing can stop the Fiddler.

**UI text:**
- Fabled sheet row: "Fiddler · ends the game on your word. Announce the end time when you
  add it."
- Activation confirm: "Use the Fiddler? This ends the game now. Abilities stop working."
- Contest card: "FIDDLE CONTEST — Ana vs Ben. Vote for who wins."
- Vote screen footer: "All players vote, alive or dead. Abilities do not apply. A tie means
  evil wins."

**Data changes:**
- `characters.json`: none (text already matches).
- `night_and_jinxes.json`: none — the Fiddler must stay out of both order lists.
- `night_guide.json`: add a `fiddler` entry once the schema has a non-night section,
  carrying the seven How-to-Run steps verbatim; it is the one Fabled whose procedure a
  storyteller genuinely reads out step by step.

## Tests to add

1. `fiddle vote resolves to the majority's team`
   Given a `FiddleContest` with the Imp (evil) at 4 votes and the Soldier (good) at 6,
   Then the resolution reports `goodWins = true`.
2. `a tied fiddle vote is won by evil`
   Given 5 votes each, Then `goodWins = false`, whichever contestant is listed first.
3. `dead players may vote in a fiddle contest without spending a ghost vote`
   Given a dead player with `ghostVoteUsed = true`, When they vote in the contest,
   Then the vote counts and `ghostVoteUsed` is unchanged.
   *(Contrast `DayScreen.kt:232-240`, which would spend it on a nomination.)*
4. `the fiddle vote is not a nomination`
   Given a resolved contest, Then `state.nominations` is unchanged,
   `GameActions.aboutToDie(state)` is unaffected, and the game log shows a fiddle entry
   rather than a nomination.
5. `the demon may only choose an opposing-alignment player`
   Given an evil Imp, Then the challenger candidates are exactly the good players
   (alive and dead); given a good ex-Demon after a Snake Charmer swap, the candidates are
   exactly the evil players.
6. `activation stops abilities`
   Given the Fiddler activated, Then the night screen offers no `QuickResolutions` or
   `DemonKillPanel`, and the day screen offers no nomination form.
7. `a resolved fiddle suppresses the win advisory`
   Given `fiddle.resolved = true` and a board that would otherwise trigger
   "Every Demon is dead", Then `WinCheck.check` returns null.
8. `fiddler adds no night step`
   Given `fabled = [fiddler]`, Then neither night order contains a `"fiddler"` step.
   *(Passes today — regression guard.)*
