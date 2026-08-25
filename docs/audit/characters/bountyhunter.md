# Bounty Hunter (bountyhunter) — Experimental Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Bounty_Hunter> (fetched via
`action=parse&prop=wikitext`, 2026-08-25).

Current ability text (verbatim):

> "You start knowing 1 evil player. If the player you know dies, you learn another evil player tonight. [1 Townsfolk is evil]"

**Summary bullets (verbatim):**

- "The Bounty Hunter starts knowing one evil player. When that player dies, they learn another evil player."
- "The Bounty Hunter only learns the evil player, not their character."
- "If the Bounty Hunter is drunk or poisoned when they should learn a new player, the Storyteller may show them a good player. When the recently shown player dies, the Bounty Hunter learns a new player that night."
- "The Bounty Hunter cannot learn the same evil player twice."
- "If the Bounty Hunter is in the game at setup, one Townsfolk is evil. The Bounty Hunter may learn the evil Townsfolk."

**How to Run (verbatim):**

> During setup, turn one Townsfolk character token upside down, to represent that they are evil. Mark one evil player with the **KNOW** reminder.
>
> During the first night, wake the Bounty Hunter. Point to the player marked **KNOW**. Put the Bounty Hunter to sleep.
>
> Each time the player marked **KNOW** dies, mark a new evil player with the **KNOW** reminder. That night, wake the Bounty Hunter, point to the player marked **KNOW**, then put the Bounty Hunter to sleep.

**Examples (verbatim):**

1. "Alex is the Bounty Hunter, Ben is the Harpy, and Abdallah is the Tea Lady. During setup, the Storyteller decides that Abdallah will be the Evil Tea Lady. On the first night, Alex learns Ben. On day 3, Ben is executed. That night, Alex learns Abdallah."
2. "On the first night, the Bounty Hunter learns Julian, who is the evil Baron. When Julian dies, the Poisoner targets the Bounty Hunter. That night, the Bounty Hunter learns Evin, who is the good Magician."
3. "Lachlan is the Drunk who thinks he is the Bounty Hunter. **No evil Townsfolk was added at setup, because the Bounty Hunter is not in play.** On the first night, Lachlan learns Marianna, who is the good Empath. When Marianna dies, Lachlan learns Doug, who is the good Flowergirl."

Example 3 is the setup rule in negative: the `[1 Townsfolk is evil]` bracket
belongs to the *real* Bounty Hunter, not to a Drunk or Marionette who believes
they are one.

**Storyteller-facing consequence from Tips (verbatim):** "Remember you've created
an evil Townsfolk in this game! Not only is this an extra evil player with an
extra nomination and an extra vote, but also they have a free bluff in the
character token they actually received… there's an extra evil player if the
game goes to a final three."

**Jinxes (verbatim):**

| With | Text |
|---|---|
| Kazali | "If the Kazali turns the Bounty Hunter into a Minion, an evil Townsfolk is not created." |
| Philosopher | "If the Philosopher gains the Bounty Hunter ability, a Townsfolk might turn evil." |

**How the evil Townsfolk learns they are evil.** The physical game communicates
it by the token itself: the ST turns the token upside down before it goes in
the bag, so the player draws an upside-down token, which is the standard
"you are evil" signal. The app has no physical token, so it must say so
explicitly. (Corroborating, an older public data dump — `bra1n/townsquare`
`roles.json` — carried the first-night reminder *"Point to 1 evil player. Wake
the townsfolk who is evil and show them the 'You are' card and the thumbs down
evil sign."*, i.e. an explicit wake. The current wiki How-to-Run does not
include that wake, so **treat the wake as optional and the notification as
mandatory** — flag this rather than hard-coding a night step.)

**Night order:** first night index 63 of 76 (`night_and_jinxes.json:358`,
between Village Idiot and Nightwatchman); other nights index 86 of 96
(`night_and_jinxes.json:459`, between King and Nightwatchman). **Correct.**

## What the app does today

Data:
- `characters.json:1291` — ability text matches the wiki exactly. `setup: true`; `firstNightReminder: "Point to the player marked 'Know'."`; `otherNightReminder: "If the player marked 'Know' died today or tonight, point to the new player marked 'Know'."`; `reminders: ["Know"]`. **Correct and current.**
- `night_and_jinxes.json:358` / `:459` — night-order positions correct.
- `night_and_jinxes.json:160` — Philosopher jinx present, wording matches the wiki.
- `night_and_jinxes.json:250` — **Kazali jinx is wrong.** The file says *"An evil Townsfolk is only created if the Kazali chooses the Bounty Hunter."* The wiki says *"If the Kazali turns the Bounty Hunter into a Minion, an evil Townsfolk is **not** created."* These are near-inverses: the app's text tells the ST to create an evil Townsfolk in exactly the case where the rules say not to, and to skip it in the normal case.
- `night_guide.json:889` — `first` and `other` entries with accurate prose (including the impaired case and the "only act if the Know player died" condition).

Code:
- `InfoCalc.supports("bountyhunter")` is true (`InfoCalc.kt:33`); `InfoCalc.bountyHunter` (`InfoCalc.kt:460`):
  ```kotlin
  val evil = ctx.players.filter { ctx.isEvil(it) }
  InfoResult(
      headline = "Point to 1 evil player (mark them 'Known')",
      detail   = "Evil players: ${evil.joinToString { "${ctx.name(it)} (${ctx.character(it)?.name})" }}",
      caveats  = listOf("Remember: 1 Townsfolk is evil in a Bounty Hunter game."),
  )
  ```
  `ctx.isEvil` honours `alignmentFlipped` (`GameState.kt:47`), so **if** the ST
  remembered to flip a Townsfolk, that seat does appear in the list. Good.
- `InfoCalc.targetsNeeded("bountyhunter")` is 0 (`InfoCalc.kt:22`) — no picker.
- `Setup.modifierFor` (`Setup.kt:121`) parses `[1 Townsfolk is evil]` as a
  zero-delta modifier (no `+`/`-` sign, so `deltaRegex` finds nothing and
  `isChoice` is false), which is **correct** — the distribution genuinely does
  not change — and `SetupScreen.kt:375` shows "(after [1 Townsfolk is evil])"
  next to the Need line. That is the one piece of setup support that exists.
- `GameActions.validateSetupState` (`GameActions.kt:503`) has **no**
  `bountyhunter` branch: nothing requires a Townsfolk to be flipped evil and
  nothing requires the `Know` reminder to be placed before night 1 — unlike the
  Fortune Teller's red herring, which *is* enforced (`GameActions.kt:547`).
- `GameShell` has setup dialogs for the herring, Drunk, Lunatic and Marionette
  (`GameShell.kt:348–478`) — **none** for the Bounty Hunter.
- `RevealFlow` (`RevealFlow.kt:55`) computes `evil` and colours the character
  **name** red (`RevealFlow.kt:114`) but never says the word "evil". The
  ability text shown is the Townsfolk's own (`RevealFlow.kt:118`).
- The `Know` token is a single copy, so the tray uses `placeExclusiveReminder`
  (`GameActions.kt:194`) and it moves — correct. It is in neither expiry table
  (`GameActions.kt:218/231`) — also correct.

Storyteller's actual experience: they build a bag, see "[1 Townsfolk is evil]"
appended to a line of numbers, and are never asked which Townsfolk. If they
remember, they open the seat sheet and tap "Flip alignment"
(`SeatSheet.kt:315`) — a button with no explanation of what it is for and no
record of why. That player is then never told they are evil: the reveal flow
shows them "Chef" in red text. Night 1, the ST places `Know` by hand on an evil
player of their choice. Every subsequent night the Bounty Hunter row appears
whether or not it should, showing the same flat roster of evil players, with no
indication of who currently holds `Know`, whether that player has died, or who
has already been learned.

## Defects and gaps

1. **P0 · The Kazali jinx text in the app is the inverse of the rule.**
   `night_and_jinxes.json:250` says an evil Townsfolk is created **only if** the
   Kazali chooses the Bounty Hunter; the wiki says it is **not** created if the
   Kazali turns the Bounty Hunter into a Minion. Repro: menu →
   "Jinxes in play" on a script with both — the ST is told to do the opposite
   of the rule.
2. **P0 · `[1 Townsfolk is evil]` is never enforced, prompted or recorded.**
   Rules: "During setup, turn one Townsfolk character token upside down."
   App: `Setup.modifierFor` yields a zero-delta modifier and
   `validateSetupState` (`GameActions.kt:503`) says nothing. Repro: deal a bag
   containing the Bounty Hunter → tap "Begin night" → setup validates with a
   fully-good town. The Bounty Hunter's whole premise (and the evil team's
   headcount at final three) silently vanishes.
3. **P0 · The evil Townsfolk is never told they are evil.** In the physical
   game the upside-down token does it. In the app, `RevealFlow`
   (`RevealFlow.kt:101–128`) shows "YOU ARE / \<token\> / \<Townsfolk ability\>"
   with the name tinted red, and no evil statement. Repro: flip a Chef's
   alignment, run "Reveal characters to players…" — the Chef is shown a normal
   Chef card in red text and will play as a good Chef.
4. **P1 · The other-night wake condition is not evaluated.** Rules: the Bounty
   Hunter only acts on a later night **if the player marked KNOW died** (today
   or tonight). App: `NightOrder.build` (`NightOrder.kt:40`) emits the row on
   every night, and `requestPhaseAdvance` (`GameShell.kt:126`) then blocks dawn
   until the ST ticks it. Repro: night 3 with a living Known player — a
   pointless row the ST must tick, with a paragraph telling them not to act.
   The app already has everything needed to decide: the seat holding
   `bountyhunter:Know`, `Player.alive`, and `state.deaths`.
5. **P1 · "Cannot learn the same evil player twice" is not tracked.**
   `InfoCalc.bountyHunter` (`InfoCalc.kt:460`) lists every evil player,
   including everyone already shown. Repro: after the first Known player dies,
   the roster still offers them.
6. **P1 · The impaired branch is missing and is *specific* here.** Rules:
   "If the Bounty Hunter is drunk or poisoned when they should learn a new
   player, the Storyteller may show them a **good** player. When the recently
   shown player dies, the Bounty Hunter learns a new player that night."
   App: `commonCaveats` (`InfoCalc.kt:158`) emits the generic "give false info",
   and the roster shown is *evil players only* — the ST has no list of good
   candidates and no cue that the retrigger now hangs on a **good** player's
   death (wiki Example 2).
7. **P1 · No target picker and no state record.** `targetsNeeded == 0`
   (`InfoCalc.kt:22`); the choice exists only as a moving token, so the
   already-learned history cannot be reconstructed.
8. **P1 · No dawn/day reminder when the Known player dies.** The moment the
   Known player is executed on day 3 (wiki Example 1) is the moment the ST must
   decide the next target; nothing surfaces it at dusk or at the following
   night's step.
9. **P2 · No misregistration caveat.** `InfoCalc.bountyHunter` never calls
   `misregistrations(...)` (`InfoCalc.kt:121`). A Recluse may register as evil
   and is a legitimate (and cruel) Bounty Hunter target; the ST is not told.
10. **P2 · "Flip alignment" is an unexplained generic button.**
    `SeatSheet.kt:315` toggles `alignmentFlipped` with no label about why, no
    reminder token, and no seat note. The Bounty Hunter's evil Townsfolk should
    be a first-class, self-documenting state.
11. **P2 · The extra evil body is not surfaced at final three.** The Tips
    section stresses it, and the Day header (`DayScreen.kt:88`) shows only
    "N alive · M votes to execute". With an evil Townsfolk in play the ST
    should see "3 alive — 2 are evil" when judging a final-three execution.
12. **P2 · No indication of who currently holds `Know`.** The step's detail
    text says "point to the player marked 'Know'" but the panel never names
    them; the ST must switch to the Grimoire tab and find the token.
13. **P3 · The Drunk/Marionette-as-Bounty-Hunter case isn't flagged.** Wiki
    Example 3: no evil Townsfolk is created. If setup enforcement is added
    (defect 2), it must key on `characterId == "bountyhunter"`, never on
    `shownCharacterId`.

## Proposed behaviour (spec)

### Engine data

```kotlin
// Player
/** Set at setup for the Bounty Hunter's [1 Townsfolk is evil]. */
// (already representable as alignmentFlipped — but give it a reason)
val alignmentFlippedBy: String = "",     // "bountyhunter"
```
plus the shared `NightAction` record from `amnesiac.md` for the learn history:
`NightAction(night, "bountyhunter", playerId = bhSeat, targetIds = [shownId])`.

### Setup

- **when:** SETUP, bag contains `bountyhunter` (by `characterId`, **not**
  `shownCharacterId` — wiki Example 3).
- Blocking dialog, in the `HiddenIdentityDialog` style (`GameShell.kt:710`):
  > **Bounty Hunter: 1 Townsfolk is evil**
  > Pick the Townsfolk who is secretly evil. They keep their Townsfolk ability and character, but they win with evil. They do **not** know who the other evil players are.
  - options: every seat whose character is a Townsfolk (exclude the Bounty
    Hunter themself, exclude the Drunk — a Drunk-turned-evil is a legal but
    confusing choice; allow it behind a "show all" toggle rather than by
    default).
  - on pick: `alignmentFlipped = true`, `alignmentFlippedBy = "bountyhunter"`,
    seat note `"Evil Townsfolk (Bounty Hunter setup) — does not know the other evil players"`,
    and place a visible reminder `bountyhunter:Evil Townsfolk` so the grimoire
    circle shows it.
- Second, chained prompt:
  > **Which evil player does the Bounty Hunter start knowing?**
  > Any evil player, including the evil Townsfolk you just created.
  - places `bountyhunter:Know` exclusively on the chosen seat.
- Add to `GameActions.validateSetupState` (`GameActions.kt:514`), alongside the
  existing Fortune Teller block (`GameActions.kt:547`):
  ```kotlin
  if (residents.any { it.characterId == "bountyhunter" }) {
      if (residents.none { it.alignmentFlippedBy == "bountyhunter" })
          issues += "Bounty Hunter: choose the Townsfolk who is evil [1 Townsfolk is evil]"
      val known = state.players.filter { p -> p.reminders.any { it.sourceId == "bountyhunter" && it.label.equals("Know", true) } }
      when {
          known.size != 1 -> issues += "Bounty Hunter: mark exactly one evil player with 'Know'"
          !known.single().isEvil(lookup) -> issues += "Bounty Hunter: the 'Know' player must be evil"
      }
  }
  ```
- **Reveal flow.** `RevealFlow` (`RevealFlow.kt:101`) must, for any seat with
  `isEvil(...) == true` whose character team is **not** evil, render an explicit
  panel after the character card:
  > **YOU ARE EVIL** — you win with the evil team. You keep the \<Chef\> ability. You do **not** know who the other evil players are.
  reusing `ShowCard.AlignmentCard(evil = true)` (`ShowCards.kt:69`, already
  implemented) as the visual. This is required for the Bounty Hunter, and also
  correct for any other alignment change (Mezepheles, Cult Leader, Bounty
  Hunter, Ogre).

### Night 1 (first-night step, index 63)

- **when:** first night, Bounty Hunter alive.
- **targets:** 1 — the seat marked `Know` (already chosen at setup); the panel
  should simply **name** them: "Point to **Abdallah**."
- **information:** the ST **points at a player**. The Bounty Hunter learns the
  player, **not** their character — say so on the step.
- **impaired alternative:** when `isImpaired(bountyHunter)`, show good players
  as well, with the banner
  **"Ana is DRUNK/POISONED — you may point to any player, including a good one. Whoever you show, the Bounty Hunter re-learns when *that* player dies."**
- **immediate effects:** record `NightAction(1, "bountyhunter", targetIds = [shown])`.

### Later nights (other-night step, index 86)

- **wake condition (computed, not remembered):**
  ```kotlin
  val known = seat holding bountyhunter:Know
  val wakes = known != null && !known.alive &&
      state.deaths.any { it.playerId == known.id && !it.resurrected &&
          (it.day == state.cycle || (it.day == state.cycle - 1 && !it.atNight)) }
  ```
  i.e. the Known player died **tonight or today**. When `wakes == false`:
  - render the row **collapsed and pre-ticked**, with the text
    **"No wake — Abdallah (marked Know) is still alive."**, and
  - **do not** count it in the dawn "night checklist incomplete" guard
    (`GameShell.kt:150–160`). This is the same class of fix the Pukka needs.
- **targets:** 1. Candidate list, in this order:
  1. **evil players not yet learned** (from `NightAction` history) — the normal
     answer;
  2. evil players already learned — **disabled**, reason
     "already learned on night 2";
  3. when impaired, **all** players with good ones enabled and a banner.
  Each chip shows the seat's character name so the ST can see the evil
  Townsfolk for what they are.
- **immediate effects:** move `bountyhunter:Know` (exclusive) to the chosen
  seat; append the `NightAction`.
- **deferred effects:** none tonight. The *next* trigger is that seat's death.
- **expiry:** `bountyhunter:Know` never expires.
- **edge case to encode:** if **every** evil player has already been learned and
  the Known player dies, there is no legal new target. The step should say so
  (**"All evil players have been learned — the Bounty Hunter learns nothing tonight."**)
  and offer the impaired-style "show a good player" escape only if the Bounty
  Hunter is actually impaired.

### Day / dusk

- When the Known player is executed or otherwise dies during the day, add a
  dusk/day-end line:
  **"Abdallah (marked Know) died — the Bounty Hunter learns a new evil player tonight."**
- Day header, from final four downwards, with an evil Townsfolk in play:
  **"3 alive · 2 evil (including the evil Townsfolk)"** — storyteller-only.

### Interactions / jinxes

- **Kazali:** correct the text to the wiki's, and implement it: if the Kazali's
  night-1 choice turns the Bounty Hunter into a Minion, **do not** create the
  evil Townsfolk — i.e. the setup prompt must be deferrable to after the Kazali
  step, or offer an "undo the evil Townsfolk" action on that step.
- **Philosopher:** a Philosopher who takes the Bounty Hunter ability may cause a
  Townsfolk to turn evil mid-game. Surface the jinx and offer the same
  "make a Townsfolk evil" action from the Philosopher's step.
- **Drunk / Marionette believing they are the Bounty Hunter:** **no** evil
  Townsfolk is created (wiki Example 3). Setup enforcement must key on
  `characterId`. Their night steps still run, showing arbitrary (typically
  good) players.
- **Recluse:** may register as evil; add `misregistrations(ctx, evilCandidates + recluses)`
  to the calculator so the ST is told, and allow the Recluse as a target.
- **Spy:** registers as good; a Spy shown to the Bounty Hunter is legal
  (they *are* evil) — no special handling, but the misregistration note should
  mention it cuts the other way for other characters.
- **Vortox:** Townsfolk info must be false → show a **good** player. The generic
  caveat (`InfoCalc.kt:163`) says "info must be FALSE", which happens to be the
  right instruction here; make it concrete: "show a GOOD player".
- **Alignment changes mid-game** (Imp star-pass, Fang Gu jump, Snake Charmer,
  Mezepheles, Cult Leader): the evil set is recomputed live, so a player who
  becomes evil after the Bounty Hunter's earlier learns becomes a valid new
  target automatically. Add a test.
- **Win conditions:** the evil Townsfolk is neither a Minion nor a Demon, so
  `WinCheck` (`WinCheck.kt:18`) is unaffected — and, importantly, the Alsaahir
  must **not** be required to name them (see `alsaahir.md`).

### UI text

- Setup dialog 1: `Bounty Hunter: 1 Townsfolk is evil — pick who.`
- Setup dialog 2: `Which evil player does the Bounty Hunter start knowing?`
- Reveal panel: `YOU ARE EVIL — you win with the evil team. You keep the Chef ability. You don't know who the other evil players are.`
- Night 1 step: `Bounty Hunter — point to Abdallah (marked Know). They learn the PLAYER, not the character.`
- Later night, no wake: `No wake — Abdallah (Know) is still alive.`
- Later night, wake: `Abdallah died — point to a new evil player. Already learned: Ben (N1).`
- Impaired: `Ana is POISONED — you may show a GOOD player. The Bounty Hunter re-learns when that player dies.`
- Grimoire seat chip: `Evil Townsfolk (Bounty Hunter)`

### Data changes

- `night_and_jinxes.json:250` — **fix the Kazali jinx text** to
  "If the Kazali turns the Bounty Hunter into a Minion, an evil Townsfolk is not created."
- `characters.json:1291` — no change (consider adding a
  `bountyhunter:Evil Townsfolk` reminder label so the grimoire can show it).
- `night_guide.json:889` — keep the prose; add a `shows` entry with
  `{"label":"You are evil","kind":"evil"}` for telling the evil Townsfolk.
- `InfoCalc.kt:460` — rewrite `bountyHunter(ctx)` to take the learn history and
  the Known seat, to compute the wake condition, and to add misregistration
  caveats; set `targetsNeeded("bountyhunter") = 1` (`InfoCalc.kt:22`).

## Tests to add

`engine/src/test/kotlin/com/clocktower/engine/BountyHunterTest.kt`

1. **Given** a bag containing the Bounty Hunter and no seat with `alignmentFlippedBy == "bountyhunter"`; **when** `validateSetupState`; **then** it reports "choose the Townsfolk who is evil". *(No issue is reported today.)*
2. **Given** the Bounty Hunter in play and no `bountyhunter:Know` reminder; **when** `validateSetupState`; **then** it reports "mark exactly one evil player with 'Know'".
3. **Given** a `bountyhunter:Know` reminder on a **good** player and a sober Bounty Hunter; **when** `validateSetupState`; **then** it reports that the Known player must be evil.
4. **Given** a Drunk whose `shownCharacterId == "bountyhunter"` and no real Bounty Hunter; **when** `validateSetupState`; **then** **no** evil-Townsfolk requirement is raised (wiki Example 3).
5. **Given** a Townsfolk with `alignmentFlipped = true`; **then** `player.isEvil(lookup) == true`, `player.team(lookup) == Team.TOWNSFOLK`, and `InfoCalc.startKnowing(Team.MINION)` (Investigator) does **not** list them.
6. **Given** the same seat; **when** the Empath's neighbours are computed; **then** they count as evil (locks the `alignmentFlipped` path through `InfoCalc.empath`).
7. **Given** night 3 and a **living** player holding `bountyhunter:Know`; **when** the Bounty Hunter step is built; **then** it is flagged "no wake" and is excluded from the dawn checklist guard.
8. **Given** night 3, the Known player died by execution on day 2; **then** the step wakes.
9. **Given** night 3, the Known player died **tonight** to the Demon; **then** the step wakes on the same night (the order puts the Demon at index 37–54 and the Bounty Hunter at 86, so this must work in one pass).
10. **Given** a learn history `[Ben (N1)]` and Ben now dead; **when** candidates are computed; **then** Ben is excluded and the remaining evil players are offered.
11. **Given** every evil player has been learned; **then** the step reports "all evil players learned" rather than offering an empty list.
12. **Given** a poisoned Bounty Hunter on a wake night; **then** good players are offered as candidates and the result carries the "show a good player; they re-learn when that player dies" note (wiki Example 2).
13. **Given** a Recluse in play; **then** the calculator's caveats include the Recluse misregistration note. *(Not produced today.)*
14. **Given** an Imp star-pass has made a Minion the Imp after the Bounty Hunter learned that Minion; **then** that seat is still marked "already learned" (identity is by seat, not by character).
15. **Given** the Bounty Hunter and a Kazali on the script; **when** `GameData.activeJinxes`; **then** the returned reason is the wiki text, not the current inverted text. *(Fails today.)*
