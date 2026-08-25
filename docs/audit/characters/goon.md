# Goon (goon) — Bad Moon Rising Outsider

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Goon>

Current ability text (matches `characters.json`):

> "Each night, the 1st player to choose you with their ability is drunk until dusk.
> You become their alignment."

How to Run (quoted in full):

> "At night, if a player chooses to use their ability on the Goon and nobody is marked
> with the Goon's **DRUNK** reminder, this player immediately becomes **drunk** — mark
> them with the **DRUNK** reminder. Finish resolving this player's ability and put them
> to sleep. If this player's alignment is different from the Goon's alignment, rotate
> the Goon's character token — right side up for good, upside-down for evil — to match
> this player's alignment, then wake the Goon, give them a thumbs-up or a thumbs-down
> (*indicating their new alignment*), then put the Goon to sleep.
>
> The next dusk, the player that the Goon made drunk becomes **sober** — remove the
> Goon's **DRUNK** reminder."

Examples (quoted in full):

> "The Courtier chooses the Goon. The Goon turns good, and the Courtier becomes drunk.
>
> The Shabaloth attacks the Goon, then the Gossip. Since the Shabaloth became drunk as
> soon as they chose the Goon, neither player dies tonight, and the Goon turns evil. The
> next night, the Shabaloth attacks the Gambler then the Goon. The Gambler dies, then
> the Shabaloth becomes drunk again. The Goon is still alive and still evil.
>
> The Chambermaid chooses the Goon and the Minstrel, and learns a "1" because the
> Chambermaid is drunk.
>
> The Tea Lady neighbours the good Goon and the Tinker. The Tinker is executed, but does
> not die. The next day, the Goon is evil. The Tinker is executed again and dies."

Clarifications (quoted):

- "The Goon cannot make a player drunk unless the player **chose** the Goon. The
  Storyteller choosing the Goon due to an ability, such as the Grandmother's, doesn't
  count."
- "The Goon still changes alignment, and makes the player drunk, if the player choosing
  the Goon was already drunk or poisoned."
- A dead Goon's alignment is locked in permanently (the ability stops working).

Consequences that matter for the app:

- **The Goon has no night-order entry.** It is a reactive trigger that fires inside
  whichever step contains the choice. (Confirmed: `goon` is absent from both order lists
  in `night_and_jinxes.json`, which is correct.)
- **Order within the night matters.** "Immediately becomes drunk" — the drunkenness
  applies *before* that player's ability resolves, which is why the Shabaloth's attack
  fails and the Chambermaid learns a wrong number. The How-to-Run's "finish resolving
  this player's ability" means *carry the step to completion*, not *let it succeed*.
- **Only the 1st chooser each night.** Later choosers that night are unaffected —
  gated on "nobody is marked with the Goon's DRUNK reminder", so the mark is what makes
  it once-per-night.
- **Drunk "until dusk"** = the rest of that night and all of the following day, removed
  at the *next* dusk. (Same lifetime as the Sailor's and Innkeeper's drunk tokens,
  which the app already handles.)
- **Alignment change is immediate, permanent until changed again, and is told to the
  Goon** with a thumbs-up / thumbs-down. It happens **even if the chooser's ability
  fails** (the Shabaloth example: attack fails, Goon still turns evil).
- **Alignment change does not change the Goon's character or team.** The Goon is still
  an Outsider (so still counts for the Godfather, still an Outsider for the Fang Gu),
  but registers as evil/good for the Empath, Chef, Investigator, Fortune Teller,
  Undertaker-adjacent reads, Moonchild, Cult Leader, and for win conditions.
- **Moonchild interaction (from the Moonchild page):** "The Moonchild kills the Goon if
  the Goon was good **when chosen**, regardless of alignment changes by night."

**Unresolved point (flagged, not guessed):** the Goon page's Tips summary says
"The Assassin's ability works despite the Goon — the Goon dies but turns evil", while
the Assassin page says "If the Assassin kills the Goon while drunk (no ability), the
Goon doesn't die but turns evil instead." The Shabaloth example on the Goon page
(attack fails because the attacker became drunk on choosing) supports the second
reading. **Recommendation: implement the second reading** (Assassin chooses Goon →
Assassin drunk → no death → Goon turns evil → Assassin's once-per-game is still spent)
and surface *both* readings to the Storyteller as a note, since this is a genuine
wiki inconsistency.

Jinxes (on the wiki, **absent from the app's data**):
- **Boffin × Goon** — the Demon cannot turn good via the Goon's ability.
- **Pit-Hag × Goon** — the Pit-Hag cannot turn an evil player into a Goon who then
  becomes good.
Neither appears in `night_and_jinxes.json` (a grep for `goon` in the jinx list returns
nothing).

## What the app does today

Data:
- `characters.json:544-557` — ability text matches the wiki. `reminders: ["Drunk"]`.
  `firstNightReminder` and `otherNightReminder` are both `""`.
- `night_and_jinxes.json` — **no** first/other-night entry (correct), **no** jinx entry
  (incorrect, see above).
- `night_guide.json` — **no entry for `goon` at all**. So the step-detail panel has no
  how-to-run prose for the Goon anywhere in the app.

Engine:
- `StatusEffects.isImpaired` (`StatusEffects.kt:36-46`) matches any reminder whose
  label contains "drunk", so a `("goon","Drunk")` token on the chooser does correctly
  make them impaired for `InfoCalc` and for `DeathRecord.abilityImpairedAtDeath`.
- `GameActions.EXPIRES_AT_DUSK` (`GameActions.kt:231-242`) contains
  `"sailor" to "Drunk"` and `"innkeeper" to "Drunk"` but **not** `"goon" to "Drunk"`.
- `GameActions.flipAlignment` (`GameActions.kt:129-130`) toggles `alignmentFlipped`;
  `Player.isEvil` (`GameState.kt:49-52`) folds it into every evil check.
- `StatusEffects.deathNotes` (`StatusEffects.kt:52-129`) has **no** Goon entry.

UI:
- The Goon never appears on the night sheet (`NightScreen.kt` builds only from the
  order lists), so its tray/tools are unreachable at night. The only way to place the
  Goon's DRUNK token is Grimoire → seat → **Add reminder** → `ReminderPicker`
  (`SeatSheet.kt:545-568`), which uses `viewModel.addReminder` (non-exclusive), or the
  generic "Drunk" token with `sourceId = ""` (`SeatSheet.kt:529`).
- Alignment: Grimoire → seat → **Flip alignment** (`SeatSheet.kt:315`). No
  "set to good / set to evil", no prompt, no show-card for the thumbs-up/down.
- `DemonKillPanel` (`NightScreen.kt:534-638`) shows `deathNotes` for the target but has
  no Goon-specific line, so choosing the Goon as the Demon's victim offers a plain
  "**Goon dies**" button.

Storyteller's actual experience: entirely manual. Nothing tells the ST the Goon exists
at night, nothing detects that a step's target was the Goon, the DRUNK token has to be
found in the seat sheet, the alignment has to be flipped by hand with a toggle, the
Goon is never told their new alignment, and the DRUNK token then stays on the chooser
for the rest of the game.

## Defects and gaps

1. **P0 · The Goon's DRUNK token never expires.**
   Rules: "The next dusk, the player that the Goon made drunk becomes sober."
   App: `("goon","Drunk")` is missing from `EXPIRES_AT_DUSK` (`GameActions.kt:231-242`),
   so `clearEphemeral` never removes it. The chooser is treated as drunk for the rest of
   the game — `InfoCalc` will keep flagging them impaired and offering false info, and
   `isImpaired` will be wrong in every subsequent `DeathRecord`.
   Repro: place the Goon's "Drunk" token on the Courtier on night 2; advance
   Dawn → Dusk → the token is still there on night 3, 4, 5…
   The generic `PlacedReminder("", "Drunk")` from `SeatSheet.kt:529` is likewise
   permanent — a second, equally reachable way to hit the same bug.

2. **P0 · Choosing the Goon as the Demon's target offers a kill that must fail.**
   Rules (Shabaloth example): the attacker becomes drunk *as soon as they choose*, so
   nobody dies. App: `DemonKillPanel` (`NightScreen.kt:586-636`) lists the Goon like any
   other seat and offers "**Goon dies**" with no warning, because `deathNotes`
   (`StatusEffects.kt:52-129`) has no Goon branch.
   Repro: BMR game with a Goon; night 2 Imp step → pick the Goon → "Goon dies" → Goon
   is dead. The correct outcome is: nobody dies, the Imp is drunk until dusk, and the
   Goon becomes evil.

3. **P1 · Nothing detects that a night step's chosen target was the Goon.**
   Every "the 1st player to choose you" consequence — mark the chooser drunk, flip the
   Goon's alignment, show the Goon a thumb — is left entirely to the ST's memory across
   ~15 night steps. This is the single largest source of missed Goon triggers.

4. **P1 · Alignment change is a manual toggle with no direction and no record.**
   `SeatSheet.kt:315` "Flip alignment" toggles, so the ST must reason about the current
   state instead of saying "the Goon is now evil". Nothing records *who* the Goon
   copied or *when*, which the Moonchild rule ("good **when chosen**") needs.

5. **P1 · The Goon is never told their new alignment.** The rules require a thumbs-up /
   thumbs-down. `ShowCard.AlignmentCard(evil)` already exists
   (`ShowCards.kt:69, 107-126`) and is exactly the right card, but nothing offers it for
   the Goon.

6. **P1 · No once-per-night gate.** After the first chooser is marked, later choosers
   that night must be unaffected. Nothing enforces or even mentions this.

7. **P2 · No `night_guide.json` entry for the Goon.** A character whose whole ruleset is
   about *when other steps fire* has zero in-app how-to-run text.

8. **P2 · The two Goon jinxes (Boffin, Pit-Hag) are missing from
   `night_and_jinxes.json`,** so `ActiveJinxesDialog` and the seat sheet's jinx list
   (`SeatSheet.kt:222-235`) show nothing when a Boffin or Pit-Hag shares the script.

9. **P2 · Storyteller-choice targeting is not distinguished from player choice.**
   The Grandmother's grandchild, the Fang Gu's Outsider-jump target chosen by the ST,
   the Sweetheart's drunk, etc. must **not** trigger the Goon. The app has no notion of
   "who chose whom", so it cannot help the ST get this right.

10. **P3 · Alignment survives a character change.** `GameActions.assignCharacter`
    (`GameActions.kt:46-53`) clears `shownCharacterId` but not `alignmentFlipped`, so a
    turned-evil Goon that a Pit-Hag turns into a Townsfolk stays flipped. (Contrast
    `starPass`/`snakeCharmerSwap`, which both reset it.)

## Proposed behaviour (spec)

The Goon has no night step; it is a **trigger attached to every other step's target
selection**. The clean way to build it is to route all night target choices through one
place.

### Prerequisite: a shared "choice" pipeline

Every night panel that picks players (`DemonKillPanel`, `ResolutionPicker`, the
`InfoCalc` target chips, the new Devil's Advocate / Godfather / Assassin / Lunatic
pickers, and the tray's place-token flow) should call a single engine entry point:

```kotlin
data class NightChoice(
    val cycle: Int, val characterId: String, val chooserId: Long,
    val targetIds: List<Long>, val byStoryteller: Boolean = false,
)

fun GameActions.recordChoice(state: GameState, choice: NightChoice, lookup: (String) -> Character?): GameState
```

`recordChoice` appends to `state.nightChoices` **and** runs the reactive triggers,
of which the Goon is the first.

### Goon trigger (inside `recordChoice`)

```
if (!choice.byStoryteller
    && goonSeat != null && goonSeat.alive
    && goonSeat.id in choice.targetIds
    && no seat currently holds ("goon","Drunk"))            // 1st chooser tonight
then
    addReminder(chooser, PlacedReminder("goon","Drunk"))    // exclusive
    goonBecomesAlignmentOf(chooser)                          // see below
    raise a StorytellerPrompt (see UI below)
```

- `no seat currently holds ("goon","Drunk")` is the once-per-night gate and matches the
  wiki's wording exactly. Because the token expires at dusk, it is naturally empty at
  the start of each night.
- `byStoryteller = true` for Grandmother's grandchild, Fang Gu jump targets, red
  herring, Sweetheart drunk, Snake Charmer's ST choice etc. — set by the calling panel.
- The Goon's alignment: `setAlignment(goonId, evil = chooser.isEvil(lookup))`, a new
  **absolute** action replacing the toggle:

```kotlin
fun setAlignment(state: GameState, playerId: Long, evil: Boolean, lookup: (String) -> Character?): GameState
// sets alignmentFlipped = (evil != character.team.isEvil)
```

- **The chooser's ability then fails.** `recordChoice` must return the new state *and*
  the caller must re-evaluate impairment before resolving. Concretely: `DemonKillPanel`
  re-runs `StatusEffects.isImpaired` after recording the choice and, if the demon is now
  drunk, replaces the "X dies" button with a disabled button plus
  "! The Demon chose the Goon and is now drunk — nobody dies tonight."

### Expiry (the P0-1 fix)

Add to `GameActions.EXPIRES_AT_DUSK`:

```kotlin
"goon" to "Drunk",
```

Also consider making the generic `PlacedReminder("", "Drunk")` / `("", "Poisoned")` from
`ReminderPicker` (`SeatSheet.kt:502, 529`) carry an explicit lifetime chosen by the ST
(dawn / dusk / never) rather than defaulting to permanent — that is a cross-cutting UX
item, see the summary.

### Storyteller prompt (the P1-3/4/5 fix)

When the trigger fires, show a modal in the night screen:

> **The Goon was chosen**
> Kira (Shabaloth) chose the Goon, Sam.
> • Kira is **drunk until dusk** — their ability fails tonight. *(auto-applied)*
> • Sam becomes **evil** (Kira's alignment). *(auto-applied — token rotated)*
> • Wake Sam and give them a **thumbs-down**.  `[ Show EVIL full-screen ]`
> *(Later choosers tonight are unaffected.)*
> `[ Done ]`  `[ Undo — Kira didn't actually choose ]`

The `[ Show EVIL full-screen ]` button emits `ShowCard.AlignmentCard(evil = true)`,
which already exists.

### Day-start briefing line

At dawn, if a `("goon","Drunk")` token is standing:
"**Kira is drunk today** (chose the Goon last night). Sober at dusk."
And if the Goon changed alignment last night:
"The Goon (Sam) is now **evil** — remember this for the Empath, Chef, Investigator and
Fortune Teller."

### Structured summary

- **when:** never on the night sheet; reactive during any other character's step.
- **wake condition:** Goon alive; a *player* (not the ST) chose the Goon; no
  `("goon","Drunk")` token standing tonight.
- **targets:** none of its own.
- **immediate effects:** `("goon","Drunk")` on the chooser (exclusive); the chooser's
  ability fails; the Goon's `alignmentFlipped` set to match the chooser; the Goon is
  woken and shown a thumb.
- **deferred effects:** none.
- **expiry:** `("goon","Drunk")` at **dusk**. The alignment change is **permanent**
  until the Goon is chosen again, and is **locked** once the Goon dies.
- **information:** none computed. Shown: an alignment card to the Goon.
- **visibility:** the chooser is told nothing; the Demon/Minions are told nothing.
- **day-time inputs:** none.
- **interactions to handle explicitly:**
  - **Demon attack** → attack fails, Goon lives, Goon flips (Defect 2).
  - **Assassin** → see the flagged ambiguity above; implement "no death, ability spent,
    Goon flips" and show the ST both readings.
  - **Chambermaid / any info role** that chose the Goon → their number is now false;
    `InfoCalc`'s caveats already fire once the DRUNK token is on them, provided the
    trigger placed it *before* the info is computed.
  - **Moonchild** → the curse resolves on the Goon's alignment *at the time of the
    public choice*, not at night. Record the Goon's alignment history so this can be
    answered.
  - **Monk / Innkeeper / Devil's Advocate** choosing the Goon → protection fails.
  - **Tea Lady** neighbouring the Goon → the Tea Lady's protection depends on both
    neighbours being *good*, so a flipped Goon breaks it. `StatusEffects.kt:81-90`
    already uses `isEvil(lookup)`, so it follows automatically once the flip is applied.
  - **Boffin / Pit-Hag jinxes** — add to `night_and_jinxes.json`.

### Data changes

- `GameActions.kt:231-242` — add `"goon" to "Drunk"` to `EXPIRES_AT_DUSK`.
- `night_and_jinxes.json` jinx list — add
  `{id1:"boffin", id2:"goon", reason:"The Demon can not become good via the Goon's ability."}`
  and `{id1:"pithag", id2:"goon", reason:"The Pit-Hag can not turn an evil player into a Goon who then becomes good."}`
  (verify the exact official wording before committing).
- `night_guide.json` — add a `goon` entry with `other.instructions`:
  "The Goon never wakes on its own. The **first player who chooses the Goon** each
  night becomes drunk until dusk and their ability fails — mark them with the Goon's
  DRUNK token. Rotate the Goon's token to that player's alignment and wake the Goon to
  give a thumbs-up (good) or thumbs-down (evil). Later choosers that night are
  unaffected. The Storyteller choosing the Goon (Grandmother's grandchild, a Fang Gu
  jump) does **not** count. At the next dusk remove the DRUNK token."
  with a `shows` entry each for `kind:"good"` and `kind:"evil"` (both already supported
  at `NightScreen.kt:809-810`).
- No night-order changes.

## Tests to add

1. `goon drunk token expires at dusk`
   Given `PlacedReminder("goon","Drunk")` on the Courtier at NIGHT 2. When
   `advancePhase` NIGHT→DAY then DAY→NIGHT. Then no seat holds it, and
   `StatusEffects.isImpaired(courtier)` is false. **Fails today.**
2. `first chooser of the goon becomes drunk and their ability fails`
   Given a Shabaloth choosing [Goon, Gossip]. When `recordChoice` runs. Then the
   Shabaloth holds `("goon","Drunk")`, `isImpaired(shabaloth)` is true, and neither the
   Goon nor the Gossip is dead.
3. `only the first chooser each night is drunked`
   Given the Goon already made the Monk drunk tonight. When the Imp also chooses the
   Goon. Then the Imp holds no drunk token, the token is still on the Monk, and the
   Goon's alignment is updated to the Imp's alignment anyway *(pin this: the wiki gates
   only the drunkenness on the token, and the alignment clause reads "the 1st player" —
   confirm with a rules source before locking the test either way)*.
4. `goon takes the chooser's alignment`
   Given a good Courtier chooses an evil-flipped Goon. Then `goon.isEvil == false`.
   Given an evil Imp chooses a good Goon. Then `goon.isEvil == true`.
5. `goon still flips when the chooser was already drunk`
   Given the Shabaloth is already Courtier-drunk and chooses the Goon. Then the Goon
   still flips to evil and the Shabaloth is still marked with the Goon's DRUNK token.
6. `storyteller choices do not trigger the goon`
   Given a Grandmother whose grandchild is the Goon (`byStoryteller = true`). Then no
   drunk token is placed and the Goon does not flip.
7. `demon attacking the goon kills nobody`
   Given an Imp choosing the Goon on night 2. When the kill is resolved through
   `recordChoice`. Then the Goon is alive, no `DeathRecord` exists, and
   `deathNotes(goon)` contains a line about the attacker becoming drunk.
8. `dead goon does not change alignment`
   Given the Goon is dead and evil. When a good Monk chooses them. Then
   `goon.isEvil` stays true and no drunk token is placed.
9. `assignCharacter clears a flipped alignment`
   Given a turned-evil Goon. When `assignCharacter(goonSeat, "chef")`. Then
   `alignmentFlipped == false`. **Fails today** (`GameActions.kt:46-53`).
10. `goon jinxes appear when boffin or pit-hag share the script`
    Given a script containing `goon` and `pithag`. Then
    `gameData.activeJinxes(inPlay)` returns the Pit-Hag jinx. **Fails today** (data
    missing).
