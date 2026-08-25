# Cult Leader (cultleader) — experimental (Carousel) townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Cult_Leader>

Current ability text (verbatim):

> "Each night, you become the alignment of an alive neighbor. If all good
> players choose to join your cult, your team wins."

`characters.json:1332` matches exactly.

### How to run (verbatim)

> "Each night, turn the Cult Leader character token right side up (if both
> alive neighbors are good) or upside down (if both alive neighbors are evil)
> or either (if one alive neighbor is good and the other alive neighbor is
> evil). If the Cult Leader's alignment changes, wake the Cult Leader and give
> a thumbs up or a thumbs down, then put the Cult Leader to sleep. If the Cult
> Leader's alignment doesn't change, do not wake them."

> "During the day, the Cult Leader may declare that they wish to use their
> ability. If so, enter the circle and run a vote in the same way that you
> would for an Exile. If all good players raise their hand, declare which team
> has won. If not all good players raise their hand, nothing happens."

### Summary / clarifications (verbatim)

- "At the end of each night, the Cult Leader becomes the alignment of a living
  neighbor."
- "Once per day, the Cult Leader may publicly choose to form a cult. If all
  good players vote to join the cult, the game ends immediately and the Cult
  Leader's team wins."
- "Voting to join a cult does not require a vote token."

Consequences worth stating explicitly for an implementer:

- **Deterministic when the two alive neighbours agree**: both good ⇒ good;
  both evil ⇒ evil. Storyteller choice **only** when they differ.
- The neighbours that count are the nearest **alive** players in each
  direction, so deaths change the answer without anyone acting.
- The Cult Leader's **character type stays Townsfolk** whatever their
  alignment (the wiki lists it as Townsfolk unconditionally). An evil Cult
  Leader is still a Townsfolk for the No Dashii, the Undertaker, the Baron
  count, etc.
- "Your team wins" — an **evil** Cult Leader who forms a cult wins for
  **evil**. This is the "everyone is now a cult" ending.
- The cult vote is run like an exile vote: hands, not vote tokens, and dead
  good players may raise their hands without spending a ghost vote.
- Only **good** players' hands matter. An evil Cult Leader does not need to
  raise their own hand; evil players other than the Cult Leader are ignored.

### Examples (verbatim)

1. "On day 3, the good Cult Leader's living neighbors are the good Town Crier
   and the evil Goblin. The Cult Leader requests to form a cult, and all good
   players vote to join the cult. The game ends and the good team wins!"
2. "The Cult Leader neighbors the No Dashii. On day 2, the Cult Leader attempts
   to form a cult. All players vote to join the cult, but a cult is not formed,
   because the Cult Leader is poisoned."
3. "The Cult Leader's living neighbors are the evil Poisoner and the good
   Fortune Teller. The Poisoner chooses the Cult Leader, and the Kazali kills
   the Fortune Teller. The Cult Leader's living neighbors are now the evil
   Poisoner and the evil Wizard. While the Cult Leader's living neighbors are
   both evil, the Cult Leader doesn't turn evil, because they cannot change
   alignment while poisoned."

**Impairment rule (from example 2 and 3): a drunk/poisoned Cult Leader neither
changes alignment nor forms a cult.** Note this is *not* "give false info" —
the ability simply does not happen.

### Jinxes (verbatim)

- **Boffin**: "If the Demon has the Cult Leader ability, they can't turn good
  due to this ability."
- **Pit-Hag**: "If the Pit-Hag turns an evil player into the Cult Leader, they
  can't turn good due to their own ability."

### Night order

Both nights, late — first night after the Nightwatchman and before the Spy;
other nights after the Nightwatchman and before the Butler. The app's data
matches the reference ordering (`townsquare` `roles.json`: cultleader 48 /66,
butler 39/67, general 50/69).

### Not settled by the wiki (flagged)

- What happens with fewer than two distinct alive neighbours (3 players alive,
  or the Cult Leader as one of two survivors). The natural reading is "the
  single alive neighbour decides"; treat as storyteller call.
- Whether an evil Cult Leader learns the Demon or wakes with the Minions
  (nothing says they do — assume not).

## What the app does today

Works, in one line each:

- Night-order position, first and other nights
  (`night_and_jinxes.json:360`, `:461`).
- `night_guide.json:905` has strong `first` and `other` run-book prose,
  including the impairment rule and a reminder of the cult win.
- `InfoCalc.supports` includes `"cultleader"` (`InfoCalc.kt:32`), and
  `InfoCalc.cultLeader` (`InfoCalc.kt:386-395`) correctly uses
  `aliveNeighbours` (`InfoCalc.kt:169-182`, nearest alive in each direction,
  de-duplicated) and prints each neighbour's true alignment.
- The two `GuideShow`s (`kind: "good"` / `"evil"`) map to
  `ShowCard.AlignmentCard` (`NightScreen.kt:809-811`), which renders
  "GOOD" / "YOU ARE GOOD" and "EVIL" / "YOU ARE EVIL"
  (`ShowCards.kt:107-126`). For this character that is exactly the right card.
- `Player.alignmentFlipped` (`GameState.kt:25`, `:49-52`) models the change,
  and the grimoire circle renders a turned-evil player's name in ember red
  (`GrimoireScreen.kt:374-384`), while `SeatSheet.kt:188` writes
  "· turned evil". `isEvil` therefore feeds the Empath, Chef, Seamstress,
  Village Idiot, Bounty Hunter, Noble and Steward calculators correctly.
- Both jinxes are in the data (`night_and_jinxes.json:85` pithag,
  `:280` boffin) and render in the seat sheet.

Storyteller experience:

- The Cult Leader row appears every night. Expanded, it prints the guide prose
  and a line like `Alive neighbours: Ben (good), Cara (evil)`, plus
  `The Cult Leader becomes the alignment of one of them (your choice).`
- To actually change the alignment the storyteller must leave the Night tab,
  open the Grimoire, tap the seat, and tap **Flip alignment**
  (`SeatSheet.kt:315` → `GameActions.flipAlignment`, `GameActions.kt:129-130`).
- Nothing in the app knows about the cult vote, and `WinCheck`
  (`WinCheck.kt:18-101`) has no cult branch. The "Declare good victory" /
  "Declare evil victory" menu items (`GameShell.kt:257-264`) are the only exit.

## Defects and gaps

1. **P0 · The alignment change is a manual toggle, and the toggle is the wrong
   primitive.** `GameActions.flipAlignment` (`GameActions.kt:129-130`) flips
   `alignmentFlipped`. The Cult Leader's rule is "become the alignment of a
   neighbour" — an assignment, not a flip. A storyteller who taps it twice, or
   who taps it on a night where the alignment was already evil, silently
   reverts the Cult Leader to good. There is no `setAlignment(evil: Boolean)`
   anywhere. Repro: night 2 both neighbours evil → tap Flip (now evil); night
   3 both neighbours still evil → tap Flip again (now good — wrong).

2. **P0 · The deterministic cases are presented as a free choice.** The rules
   leave the storyteller a choice **only** when the two alive neighbours
   differ. `InfoCalc.cultLeader` always says "your choice"
   (`InfoCalc.kt:393`). A storyteller who follows the app can leave a Cult
   Leader good while both neighbours are evil — a rules break that decides
   games.

3. **P0 · The cult win condition does not exist in the app.** No day action,
   no vote surface, no `WinCheck` advisory, no record. "If all good players
   choose to join your cult, your team wins" is half the character. Repro:
   there is no path in the UI to run a cult vote; the storyteller must run it
   in their head and then use "Declare … victory".

4. **P1 · The impairment instruction is wrong.**
   `InfoCalc.commonCaveats` → `impairments` (`InfoCalc.kt:132-153`) emits
   "…is POISONED (…) — give false info." For the Cult Leader the correct
   instruction is "their alignment does not change tonight; do not wake them."
   Giving false info here would mean showing a thumb the player shouldn't get.

5. **P1 · The Vortox caveat is emitted and is wrong.**
   `commonCaveats` (`InfoCalc.kt:158-166`) adds "VORTOX in play — Townsfolk
   info must be FALSE" for every Townsfolk-held step. The Cult Leader's
   ability yields no information, so the Vortox does not apply. Repro: seat a
   Vortox and a Cult Leader; the Cult Leader step tells the storyteller to lie
   about an alignment change. (Same class of bug will hit every non-info
   Townsfolk that `InfoCalc` supports.)

6. **P1 · No one-tap "becomes good / becomes evil / no change" on the step.**
   Every other multi-step interaction got a `QuickResolutions` entry
   (`NightScreen.kt:461-525`: snakecharmer, fanggu, professor). The Cult
   Leader — which requires a state change *every single night* — did not.

7. **P1 · No record of the Cult Leader's alignment history.** The storyteller
   needs to know whether the alignment *changed* (that is the wake condition).
   Today they must remember last night's value; nothing is stored per night.

8. **P1 · Nothing tracks who has publicly joined the cult.** The wiki's day
   procedure needs a hand count of good players; the app has a perfectly good
   vote-tally widget (`DayScreen.kt:161-252`) that is not reachable for this.

9. **P2 · The Boffin and Pit-Hag jinxes are inert.** Both say "can't turn good
   due to this ability" — i.e. the night step must stop offering "becomes
   good" for that player. They render as text in the seat sheet only.

10. **P2 · Fewer than two alive neighbours is unhandled.**
    `aliveNeighbours` returns a 1-element list when only one other player is
    alive and `distinctBy` collapses the wrap-around, which is the right data,
    but the headline then reads "Alive neighbours: Ben (evil)" with no
    guidance that the answer is now forced.

11. **P2 · A dead Cult Leader still gets a night row.** `NightOrder.build`
    does not filter by `alive` (`NightOrder.kt:142-178`); the row is only
    annotated "All holders are dead — usually skip"
    (`NightScreen.kt:751-757`).

12. **P2 · The alignment change is invisible to the log.** `GameLogDialog`
    (`GameExtras.kt:46-106`) logs deaths and nominations only. An alignment
    flip — arguably the single most consequential non-death state change in
    the game — leaves no trace.

13. **P3 · "the thumbs up good signal of the thumbs down evil signal"** — the
    reference dataset's typo. The app's own `night_guide` prose is fine; just
    make sure the official `firstNightReminder` in `characters.json:1332`
    ("The Cult Leader might change alignment. If so, show the 'You are' info
    token & give a thumb signal.") stays the displayed text.

## Proposed behaviour (spec)

### A. Engine primitives

```
fun GameActions.setAlignment(state, playerId, evil: Boolean, lookup): GameState
```
sets `alignmentFlipped` so that `player.isEvil(lookup) == evil`. Keep
`flipAlignment` for manual corrections; use `setAlignment` for every
rule-driven change (Cult Leader, Snake Charmer, Pit-Hag, Bounty Hunter…).

```
object CultLeader {
    data class Neighbours(val list: List<Player>, val forced: Boolean?, /* true=evil, false=good, null=choice */)
    fun state(state: GameState, lookup): Neighbours?
    fun blockedFromTurningGood(state, lookup, cultLeader): String?   // Boffin / Pit-Hag jinx
}
```

Record the nightly result: add
`state.nightRecords: List<NightRecord(cycle, stepId, playerId, text)>` (a
general facility — see the cross-cutting note) and write
`"Cult Leader is now evil"` / `"stayed good"` each night.

### B. Night step (structured form)

- **when**: `first` and `other`.
- **wake condition**: the Cult Leader is **alive**. (Do not emit the row for a
  dead Cult Leader.) The row is always emitted for a living Cult Leader,
  because the storyteller must resolve the alignment even when no wake
  follows.
- **targets**: none picked by the player. The storyteller picks the *outcome*.
- **immediate effects**: `setAlignment(cultLeaderId, evil)`.
  - Both alive neighbours good ⇒ **forced good**; both evil ⇒ **forced evil**;
    mixed ⇒ storyteller choice between the two.
  - If `isImpaired(cultLeader)` ⇒ **no change at all**; the step shows a single
    disabled state and the instruction "do not wake them".
  - If `blockedFromTurningGood(...)` (Boffin: the Demon has the Cult Leader
    ability; Pit-Hag: an evil player was turned into the Cult Leader) ⇒ the
    "becomes good" option is removed with the jinx text as the reason.
- **wake**: the Choirboy-style rule — wake **only if the alignment changed**.
  The step must state, before the storyteller acts, "Currently: GOOD" and
  after the action, "Changed — wake them and show the thumb" or "No change —
  do not wake."
- **tokens**: none. (The character has no reminders in `characters.json`,
  which is correct.)
- **expiry**: none.
- **information**: keep `InfoCalc.cultLeader`, but rewrite the output:
  - `headline`: one of
    `"Both alive neighbours are GOOD — the Cult Leader must be good"`,
    `"Both alive neighbours are EVIL — the Cult Leader must be evil"`,
    `"Neighbours differ — your choice: good (Ben) or evil (Cara)"`,
    `"Only one alive neighbour (Ben, good) — the Cult Leader takes their
    alignment"`.
  - `detail`: `"Currently good. Last night: good."`
  - `caveats`: **suppress the Vortox caveat** for non-info abilities. Add a
    `InfoCalc` flag (e.g. `fun yieldsInformation(id: String): Boolean`) and
    gate `commonCaveats`' Vortox line on it; `cultleader` returns false.
  - `caveats` for impairment must read "drunk/poisoned — the alignment does
    **not** change tonight; do not wake them", not "give false info". Add a
    per-character override to `impairments` (a `style` parameter:
    `FALSE_INFO` vs `NO_EFFECT`).
- **shows**: keep the two alignment cards; only show the one matching the new
  alignment, and only when a change happened.
- **visibility**: nothing to the Demon or Minions. Explicitly: an evil Cult
  Leader is **not** woken with the Minions and does not learn the Demon.

### C. Day-time inputs the app must record — the cult vote

New Day-screen action `Form a cult` (visible when a living Cult Leader is
seated, and at most once per day):

- Pre-flight banner: `"<Name> is the Cult Leader and is currently <good/evil>.
  If every good player raises their hand, <good/evil> wins."`
- Reuse the exile-style tally UI (`DayScreen.kt:161-252`) but with the
  candidate list = **all good players, alive and dead** (dead good players may
  raise a hand; no vote token is spent — do **not** call `toggleGhostVote`).
- Live readout: `"7 of 9 good players have joined — 2 missing: Iris, Noah."`
- On completion, record a `CultVote(day, cultLeaderId, joinerIds,
  allGoodJoined, cultLeaderEvil)` in state and add it to `GameLogDialog`.
- **Blocked when the Cult Leader is impaired**: still run the vote (the
  players must not learn the Cult Leader is poisoned), but the result panel
  tells the storyteller privately: `"The Cult Leader is poisoned — no cult
  forms. Say nothing."` (wiki example 2).
- On success, hand off to `WinCheck` / the reveal flow with
  `goodWins = !cultLeaderIsEvil` and the reason
  `"Every good player joined the cult — the Cult Leader's team (<good/evil>)
  wins."`

### D. WinCheck

Add a branch before the demons-dead branch:

```
if (lastCultVote?.allGoodJoined == true && !cultLeaderImpairedAtVote) {
    return Advisory(goodWins = !cultLeaderEvil,
        reason = "Every good player joined <Name>'s cult — the <good/evil> team wins.")
}
```
plus a caution when a Cult Leader is alive and evil:
`"An evil Cult Leader can end the game by forming a cult — good players must
not all raise their hands."`

### E. UI text for the step

- `Cult Leader — currently GOOD`
- `Both alive neighbours are evil. The Cult Leader becomes EVIL.`
  Buttons: `Becomes evil` (primary) · `No change (house rule)`.
- Mixed: `Neighbours differ — choose.` Buttons: `Becomes good (Ben)` ·
  `Becomes evil (Cara)`.
- After acting: `Changed to EVIL — wake them and show the thumbs-down card.`
  or `No change — do not wake them.`
- Impaired: `POISONED by the No Dashii — the alignment does not change and the
  Cult Leader does not wake.`

### F. Data changes

- `characters.json:1332`: no ability change.
- `night_guide.json:905`: keep the prose; add to both `first` and `other` a
  sentence that the change is forced when both alive neighbours match, and
  move the cult-win sentence into a day briefing.
- `night_and_jinxes.json:280`: the Boffin jinx text says "due to their own
  ability"; the wiki says "due to this ability". Cosmetic, but worth aligning.

## Tests to add

1. `cult leader with two good alive neighbours is forced good`
   Given the Cult Leader between two alive good players; Then
   `CultLeader.state(...).forced == false` and the night step offers no "become
   evil" action.

2. `cult leader with two evil alive neighbours is forced evil`
   Given the Cult Leader between an alive Poisoner and an alive Baron; Then
   `forced == true`; When the step is resolved; Then
   `state.player(cl).isEvil(lookup)` is true.

3. `dead neighbours are skipped`
   Given seats [CL, dead Empath, alive Poisoner] and [CL, dead Saint, alive
   Chef] on the other side; Then the neighbours are the Poisoner and the Chef
   and `forced == null`.

4. `setAlignment is idempotent where flipAlignment is not`
   Given an evil-turned Cult Leader; When `setAlignment(evil = true)` runs
   twice; Then the player is still evil. (Today `flipAlignment` twice returns
   them to good — this is the regression guard.)

5. `poisoned cult leader does not change alignment`
   Given a good Cult Leader with both alive neighbours evil and a
   `("poisoner","Poisoned")` reminder; When the step is resolved with the
   forced outcome; Then the alignment is unchanged and the step reports "do
   not wake".

6. `no dashii adjacency poisons the cult leader`
   Given a Cult Leader adjacent to a No Dashii as its nearest Townsfolk
   neighbour; Then `StatusEffects.isImpaired(cultLeader)` is true and the step
   reports no change (mirrors wiki example 2).

7. `evil cult leader is still a townsfolk`
   Given an evil-turned Cult Leader; Then `character.team == TOWNSFOLK`,
   `player.isEvil(lookup)` is true, and `InfoCalc.empath` for an adjacent
   Empath counts them as evil.

8. `cult vote by an evil cult leader wins for evil`
   Given an evil Cult Leader and a recorded `CultVote` where every good player
   (alive and dead) joined; Then `WinCheck.check(...)?.goodWins == false` with
   the cult reason.

9. `cult vote missing one good player does nothing`
   Given the same state with one good player absent from `joinerIds`; Then
   `WinCheck.check(...)` returns no cult advisory.

10. `cult vote does not spend ghost votes`
    Given a dead good player who joins the cult; Then their `ghostVoteUsed`
    is unchanged.

11. `vortox does not add a caveat to the cult leader step`
    Given a Vortox and a Cult Leader in play; Then
    `InfoCalc.compute(data, state, "cultleader", clId)!!.caveats` contains no
    "VORTOX" entry.

12. `boffin jinx removes the become-good option`
    Given a Demon with the Cult Leader ability (Boffin); Then
    `CultLeader.blockedFromTurningGood(...)` is non-null and the step offers
    only "becomes evil"/"no change".
