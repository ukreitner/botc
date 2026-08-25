# Evil Twin (eviltwin) — Sects & Violets Minion

## Official rules (sources)

Source: https://wiki.bloodontheclocktower.com/Evil_Twin (Character Text, How to
Run, Key Mechanics, Examples, Tips & Tricks, Jinx).

Current ability text (verbatim):

> "You & an opposing player know each other. If the good player is executed,
> evil wins. Good can't win if you both live."

How to Run (wiki):

- **During setup**, "mark any good character with the **TWIN** reminder to
  designate them as the Good Twin." The storyteller chooses; the twin is any
  good player, adjacency irrelevant.
- On the **first night** (and only the first night):
  1. "Wake both twins together"
  2. "Let them make eye contact"
  3. Point at the Evil Twin and show the **Evil Twin token** to the good twin
  4. Point at the good twin and show **their character token** to the Evil Twin
  5. "Put both to sleep"

  (The official night-sheet wording, transcribed correctly in
  `characters.json`: "Point to the Evil Twin. Show their Evil Twin token to the
  twin player. Point to the twin. Show their character token to the Evil Twin
  player.")

Key mechanics (wiki, verbatim/near-verbatim):

- **"If the Good Twin is executed: Evil wins the game."**
- **"If the Evil Twin is executed: The game continues normally. A dead Evil Twin
  has no ability, so executing them removes the win condition threat."**
- **"Good cannot win while both twins live: This applies even if the Demon is
  killed. Good must execute both the Demon and the Evil Twin to win."**
- **"If a good player becomes an Evil Twin: They remain good-aligned, with an
  evil player becoming their new twin. The good team can safely execute the evil
  twin, but executing the good one results in an evil victory."**
- **"If both twins share alignment: The Storyteller selects a different Good
  Twin."**
- **Pit-Hag final-night clause:** "If created on the final night, make the other
  twin either the Demon player or a dead player to preserve good's winning
  possibility."

Examples (wiki): a Pit-Hag converting the good Sage (the Good Twin) into a
Mutant leaves the *player* as the good twin — executing that Mutant still makes
evil win. So **the bond follows the player, not the character.**

**Jinx** (verbatim): **Plague Doctor** — "If the Storyteller would gain the Evil
Twin ability, a player becomes the Evil Twin."

Night order: **first night only**, official slot between the Devil's Advocate
and the Witch.

## What the app does today

Data:
- `engine/src/main/resources/botc/data/characters.json:1026-1038` — ability text
  matches the wiki; `firstNightReminder` is a faithful transcription;
  `otherNightReminder` correctly empty; `reminders: ["Twin"]`.
- `engine/src/main/resources/botc/data/night_and_jinxes.json:330` — first-night
  index 35, correct position. **Works.**
- `engine/src/main/resources/botc/data/night_guide.json:672-696` — a `first`
  entry with prose and three show cards.
- **No Plague Doctor jinx** in `night_and_jinxes.json` (grep for `eviltwin` in
  the jinx array returns nothing).

Engine:
- `engine/src/main/kotlin/com/clocktower/engine/WinCheck.kt:5` — the file's
  doc comment names the Evil Twin as a reason endings are storyteller calls,
  but `WinCheck.check` (`:18-101`) contains **no Evil Twin logic at all**.
- `engine/src/main/kotlin/com/clocktower/engine/GameActions.kt:503-561` —
  `validateSetupState` has cases for `drunk`, `lunatic`, `marionette` and the
  Fortune Teller's red herring. **No `eviltwin` case.**
- `engine/src/main/kotlin/com/clocktower/engine/GameActions.kt:218-242` —
  `("eviltwin","Twin")` is in neither expiry table, so the token is permanent.
  **Works.**

UI:
- `app/.../GameShell.kt:347-479` — setup prompts exist for the red herring, the
  Drunk, the Lunatic and the Marionette. **There is no Evil Twin prompt.**
- `app/.../NightScreen.kt:792-832` — the three guide show cards are reachable,
  but "Good twin's character" is a `pick` card, so the storyteller must find the
  good twin's character in a search grid at the table.
- `app/.../NightScreen.kt:470-524` — no `eviltwin` branch in `QuickResolutions`.
- `app/.../GameShell.kt:505-519` — the win advisory dialog renders whatever
  `WinCheck` produces.

Storyteller experience: nothing designates the good twin, nothing checks that
they are good, nothing marks them, the first-night step is a text block plus a
character search, and — most seriously — the app will cheerfully tell the
storyteller "good wins" the moment the last Demon dies even with both twins
alive, and will say nothing at all when the good twin is executed.

## Defects and gaps

1. **P0** · "Good can't win if you both live" is not checked · `WinCheck.check`
   returns `Advisory(goodWins = true, "Every Demon is dead — good wins…")` as
   soon as no Demon is alive (`WinCheck.kt:70-86`), with cautions only for the
   Scarlet Woman, Mastermind and Imp. With a living Evil Twin and a living good
   twin the game must continue. The app actively tells the storyteller to end
   the game wrongly. · Repro: Evil Twin + good twin both alive, execute the
   Demon — the "good wins" dialog appears with no Evil Twin caution.

2. **P0** · Executing the good twin does not end the game · "If the good player
   is executed, evil wins." Nothing anywhere detects this — not
   `WinCheck.check`, not `deathNotes`, not the Day tab. The storyteller must
   remember which of thirteen seats carries a `Twin` token they were never
   prompted to place. · `WinCheck.kt:18-101`, `StatusEffects.kt:52-129` ·
   Repro: place `eviltwin:Twin` by hand on a good seat, execute them.

3. **P0** · Nothing designates or validates the good twin · Setup requires
   marking a good player with the TWIN reminder; the app has a prompt pattern
   for exactly this (`GameShell.kt:347-479`) and does not use it, and
   `validateSetupState` (`GameActions.kt:503-561`) does not require it. A game
   can start with the Evil Twin in play and no twin at all. · Repro: deal a bag
   containing the Evil Twin and press "Begin night" — no complaint.

4. **P1** · No check that the twin is of *opposing* alignment · "If both twins
   share alignment: the Storyteller selects a different Good Twin." The app
   allows a `Twin` token on an evil seat and never notices, including after a
   mid-game alignment flip (`GameActions.flipAlignment`, `:129-130`). · Repro:
   put the `Twin` token on the Poisoner.

5. **P1** · The good twin's character token must be found by search, every time
   the card is shown · The guide's "Good twin's character" show is
   `token: "pick"` (`night_guide.json:681-685`), routed through
   `GuideShowDialog`'s search grid (`NightScreen.kt:392-435`). The app already
   knows who the twin is once the token is placed; this should be one tap. ·
   Repro: run the first-night Evil Twin step.

6. **P1** · The bond is not carried when the good twin's character changes ·
   The wiki is explicit that the bond follows the **player**. The app's `Twin`
   token is on the seat, so this works by accident — but the Evil Twin was shown
   a now-stale character token and nothing tells the storyteller. Also
   `GameActions.swapCharacters` (`:98-115`) and `assignCharacter` (`:46-53`) do
   not clear or re-derive anything. · Repro: Pit-Hag turns the good twin into
   the Mutant.

7. **P2** · The `Evil` show card in the guide is wrong · `night_guide.json:686-690`
   includes `{"kind": "evil", "text": "This player is EVIL"}`, but
   `ShowCard.AlignmentCard(evil = true)` renders a full-screen **"EVIL / YOU ARE
   EVIL"** (`ShowCards.kt:107-124`). Held up to the good twin that reads as
   "you are evil". · Repro: tap the "Evil" chip on the Evil Twin step.

8. **P2** · The guide prose misstates what the good twin learns ·
   `night_guide.json:674-676`: "Both players therefore know who the other is,
   but the good twin does not learn which of them is evil beyond this." The good
   twin is shown the **Evil Twin token** for their twin, so they *do* know their
   twin is evil. The sentence invites a storyteller to under-inform.

9. **P2** · The Plague Doctor jinx is missing from the data · No `eviltwin`
   entry exists in `night_and_jinxes.json`'s jinx array, so
   `GameData.activeJinxes` will never surface it. · Repro: put the Plague Doctor
   and Evil Twin on one script and open "Jinxes in play".

10. **P2** · A dead Evil Twin's restriction is not lifted anywhere visible ·
    Once the Evil Twin dies, good may win. Since the restriction is not modelled
    at all (defect 1), neither is its removal — the storyteller has to hold the
    whole condition in their head.

11. **P3** · No Pit-Hag final-night guidance · "If created on the final night,
    make the other twin either the Demon player or a dead player." The Pit-Hag
    step has no character-change flow at all (see `pithag.md`), so this cannot
    even be reached.

## Proposed behaviour (spec)

### Setup

- when: SETUP phase, a seat holds `eviltwin`, and no seat carries
  `eviltwin:Twin`.
- Raise a `HiddenIdentityDialog`-style prompt, matching the existing Drunk /
  Lunatic / Marionette pattern (`GameShell.kt:377-479`), but picking a **player**
  rather than a character:
  - title `The Evil Twin is in play`
  - body `<EvilTwin name> is the Evil Twin. Which good player is their twin?`
  - options: every seat whose `isEvil(lookup) == false`, non-traveller, sorted
    by seat order.
- On pick: `placeExclusiveReminder(twinId, PlacedReminder("eviltwin","Twin"))`
  and `setNote(twinId, "Good twin of <EvilTwin name>")`.
- `validateSetupState` (`GameActions.kt:503-561`) gains:
  - `"eviltwin" ->` exactly one seat must carry `eviltwin:Twin`, and that seat
    must satisfy `!isEvil(lookup)`; otherwise issue
    `"<name>: choose a good player to be the Evil Twin's twin"` /
    `"The Evil Twin's twin must be a good player"`.

### Night step

- when: **first night only**. Wake condition: the Evil Twin is alive and a
  `eviltwin:Twin` token exists.
- targets: none — the pair is already known.
- immediate effects: none (no tokens placed at night).
- information / visibility, as a **one-tap ordered card sequence** (the same
  pattern recommended for the Cerenovus), pre-filled with the actual seats:
  1. `Wake <EvilTwin> and <Twin> together. Let them see each other.`
  2. To the **good twin**: `ShowCard.CharacterCard("THIS PLAYER IS", "eviltwin")`
     while pointing at the Evil Twin.
  3. To the **Evil Twin**: `ShowCard.CharacterCard("YOUR TWIN IS",
     twinPlayer.characterShownToPlayerId)` — resolved automatically from the
     `Twin` token, **no picker**.
  4. `Sleep both.`
  Note `characterShownToPlayerId` (`GameState.kt:33`) is the right field: if the
  good twin is the Drunk, the Evil Twin is shown the Drunk's *believed*
  character, matching the physical grimoire.
- expiry: `eviltwin:Twin` **never** expires — keep it out of both tables.

### Standing win-condition effects (the missing half)

Extend `WinCheck.check` (`WinCheck.kt:18-101`):

1. **Good twin executed → evil wins.** Before the demons-dead branch, scan
   `state.deaths` for the most recent `DeathCause.EXECUTION` whose
   `playerId` carried `eviltwin:Twin` at the time, where the Evil Twin's ability
   was working (Evil Twin alive and not impaired at that moment). Return
   `Advisory(goodWins = false, reason = "<name>, the Evil Twin's good twin, was
   executed — evil wins.")`.
   - This needs the same treatment as the Saint check already at
     `WinCheck.kt:51-68`, which uses `characterIdAtDeath` +
     `abilityImpairedAtDeath` snapshots — extend `DeathRecord` with the twin
     flag, or evaluate from the live token (the token never moves, so the live
     token is adequate).
2. **Good can't win while both twins live.** In the demons-dead branch
   (`WinCheck.kt:70-86`), if a living Evil Twin and a living `eviltwin:Twin`
   holder both exist, **suppress the `goodWins = true` advisory** and instead
   return `Advisory(goodWins = null, reason = "Every Demon is dead, but the Evil
   Twin (<name>) and their twin (<name>) both live — good cannot win yet.")`.
   - "This applies even if the Demon is killed" is the exact wording; this must
     block, not merely caution.
3. Add an Evil Twin **caution** to any other good-wins advisory.
4. `deathNotes` (`StatusEffects.kt:52-129`) gains, for a seat carrying
   `eviltwin:Twin`: `"Evil Twin's twin — if this is an EXECUTION, evil wins
   immediately."` and, for the Evil Twin seat itself: `"Evil Twin dies — good may
   now win once the Demon is dead."`

### Day-time surfacing

- Day-start briefing while both twins live:
  `Evil Twin pair: <A> (Evil Twin) & <B>. Good cannot win while both live.
  Executing <B> hands the game to evil.`
- Nomination-time warning (`StatusEffects.nominationWarnings`,
  `:132-166`): when the **nominee** carries `eviltwin:Twin`, add
  `"<name> is the Evil Twin's twin — executing them makes evil win."` This is the
  same slot the Fearmonger warning already occupies (`:158-160`).
- The dusk guard (`GameShell.kt:592-617`) must show this warning before its
  one-tap "Execute & begin night".

### Mid-game changes

- **Alignment flip on either twin** (`flipAlignment`, `:129-130`): if both twins
  become the same alignment, raise `Both twins are now <good|evil> — pick a new
  twin` and re-open the setup-style picker.
- **A good player becomes the Evil Twin** (Pit-Hag/Barber): they stay good
  (`alignmentFlipped` must be set so `isEvil` is false), and the app must
  prompt for a **new twin among evil players**. Wire this into the Pit-Hag flow
  (see `pithag.md`) and add the wiki's final-night guidance as a caution:
  `On the final night, make the other twin the Demon or a dead player.`
- **The good twin's character changes**: keep the token, and add a note
  `The Evil Twin was shown the <old character> — that information is now stale.`

### Interactions / jinxes

- **Plague Doctor** — add to `night_and_jinxes.json`:
  `{"id1":"eviltwin","id2":"plaguedoctor","reason":"If the Storyteller would
  gain the Evil Twin ability, a player becomes the Evil Twin."}`
- **Drunk / Marionette good twin**: show the Evil Twin the *believed* character
  (see above).
- **Travellers**: not eligible as the twin (the twin must be a good *player* of
  the town; the wiki says "any good character").
- **Mastermind day**: the good-twin execution check must run *before* the
  Mastermind branch (`WinCheck.kt:28-49`), or at least be listed as a caution
  there.

### UI text for the step

- Setup prompt: `Which good player is <EvilTwin>'s twin?`
- Night step title: `Evil Twin — <A> & <B>`
- Body: `Wake both. Let them see each other. Show <B> the Evil Twin token
  (pointing at <A>), then show <A> the <B's character> token (pointing at <B>).
  Sleep both.`
- Standing banner: `Good can't win while <A> and <B> both live.`

### Data changes

- `night_guide.json:672-696` — remove the `{"kind":"evil"}` card (it renders
  "YOU ARE EVIL"); fix the sentence "the good twin does not learn which of them
  is evil beyond this" to "the good twin learns that their twin is the Evil
  Twin; the Evil Twin learns only the good twin's character"; add the two
  win-condition sentences as a standing reminder.
- `night_and_jinxes.json` — add the Plague Doctor jinx.
- `characters.json:1026-1038` — no ability-text drift.

## Tests to add

1. **Given** a bag containing the Evil Twin and no `eviltwin:Twin` token,
   **then** `validateSetupState` reports "choose a good player to be the Evil
   Twin's twin".
2. **Given** the `Twin` token on an evil seat, **then** `validateSetupState`
   reports that the twin must be good.
3. **Given** a living Evil Twin, a living `Twin` holder and no living Demon,
   **then** `WinCheck.check` does **not** return `goodWins = true`; it returns a
   "good cannot win yet" advisory naming both twins.
4. **Given** the same board with the Evil Twin **dead**, **then**
   `WinCheck.check` returns `goodWins = true`.
5. **Given** the good twin is executed while the Evil Twin is alive and sober,
   **then** `WinCheck.check` returns `goodWins = false` naming the twin.
6. **Given** the good twin dies to the **Demon** (not execution), **then** no
   evil-wins advisory is produced.
7. **Given** the good twin is executed while the Evil Twin is poisoned, **then**
   no evil-wins advisory is produced (ability not working).
8. **Given** a nomination whose nominee carries `eviltwin:Twin`, **then**
   `nominationWarnings` warns that executing them makes evil win.
9. **Given** `eviltwin:Twin` placed at setup, **when** the game runs through
   four full phase transitions, **then** the token is still present.
10. **Given** the good twin is the Drunk with `shownCharacterId = "chef"`,
    **then** the Evil Twin's show card names the **Chef**.
11. **Given** the Plague Doctor and Evil Twin are both on a script, **then**
    `GameData.activeJinxes` returns the Evil Twin / Plague Doctor jinx.
