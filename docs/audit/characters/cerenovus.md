# Cerenovus (cerenovus) — Sects & Violets Minion

## Official rules (sources)

Sources: https://wiki.bloodontheclocktower.com/Cerenovus (Character Text, How to
Run, Examples, Key Edge Cases, Jinx),
https://www.botcscriptorium.com/characters/cerenovus/ (madness-enforcement
wording), https://wiki.bloodontheclocktower.com/Mutant (madness definition).

Current ability text (verbatim):

> "Each night, choose a player & a good character: they are \"mad\" they are
> this character tomorrow, or might be executed."

How to Run (wiki, verbatim structure):

- "Each night, wake the Cerenovus. They select **any player** and **any
  Townsfolk or Outsider character**."
- "Mark the chosen player with the **MAD** reminder."
- "Show the mad player the **THIS CHARACTER SELECTED YOU** token, then the
  **Cerenovus token**, then the **selected character token**."
- "During the next day **or night**, if the mad player hasn't convincingly tried
  to persuade the group they are that character, **you can execute them**."
- "**This counts as the one execution allowed per day.**"

Key edge cases (wiki):

- **Effort required** — "Simply claiming the character isn't enough; sincere
  effort to convince others is mandatory."
- **Mad evil players** — "May be executed, but the Storyteller can choose not to
  prevent evil from winning."
- **Dead players** — "Can be targeted and executed, which counts as the day's
  sole execution."
- **Duration** — "Madness lasts until the mad player breaks it or the Storyteller
  executes them." In practice the Cerenovus re-chooses every night, so the token
  moves; the enforcement window is the following day **and the following night
  up to the Cerenovus's next choice**.
- **Enforcement stops silently** (Scriptorium): the Storyteller must "silently
  stop enforcing the madness condition" if the Cerenovus **leaves the game,
  becomes drunk**, or the madness transfers — "without notifying the maddened
  player." The player is never told they are off the hook.
- The chosen character **need not be in play**, and the target **may be the
  Cerenovus themself or another evil player**.
- Madness definition (Mutant page): "actively attempting to persuade others of
  something… through verbal hints, meaningful silence, or subtle gestures. The
  Storyteller has sole discretion."

**Jinx** (verbatim): **Goblin** — "The Cerenovus may choose to make a player mad
that they are the Goblin." (The only case in which the chosen character may be
evil.)

Night order: **both** first night and other nights. Official slot on the first
night is between the Witch and the Fearmonger; on other nights between the Witch
and the Pit-Hag.

## What the app does today

Data:
- `engine/src/main/resources/botc/data/characters.json:1012-1024` — ability text
  matches the wiki; `firstNightReminder` and `otherNightReminder` are faithful
  transcriptions of the official night sheet; `reminders: ["Mad"]`.
- `engine/src/main/resources/botc/data/night_guide.json:624-671` — the best
  guide entry in my scope: both `first` and `other`, accurate prose, and three
  prepared show cards ("Cerenovus"/self token, "Mad as…"/pick token, "Madness
  demand"/message).
- `engine/src/main/resources/botc/data/night_and_jinxes.json:332` (first night
  index 37) and `:397` (other night index 24) — both correct. **Works.**
- `engine/src/main/resources/botc/data/night_and_jinxes.json:154` — the Goblin
  jinx is present with correct text. **Works.**

Engine:
- `engine/src/main/kotlin/com/clocktower/engine/GameActions.kt:238` —
  `("cerenovus","Mad")` is in `EXPIRES_AT_DUSK`, so a token placed on night *N*
  survives day *N* and is swept at the `DAY -> NIGHT` transition.
- `engine/src/main/kotlin/com/clocktower/engine/StatusEffects.kt:162-164` — the
  only consumer: at nomination time, if the **nominator** carries a reminder
  labelled `Mad`, print "<name> is Cerenovus-mad — check their claim before this
  goes further."

UI:
- `app/.../NightScreen.kt:193-357` — the tray offers the `Mad` chip and a seat
  row; placement uses `placeExclusiveReminder` (one copy), so the token moves
  each night. **Works.**
- `app/.../NightScreen.kt:364-454` — `GuideShowDialog` renders the "Mad as…"
  pick card: the storyteller types/searches for the character **again**, and the
  chosen `tokenId` is local `rememberSaveable` state that is **discarded on
  dismiss**.
- `app/.../NightScreen.kt:470-524` — no `cerenovus` branch in `QuickResolutions`.

Storyteller experience: the night step is well documented and the token
placement is quick, but **the app never learns which character the madness is
about**. The storyteller picks it twice (once mentally, once in the show-card
picker), it is stored nowhere, and from that moment the app can only say
"someone is mad" — never "Ivan is mad as the Mutant". There is no day-start
briefing, no execution affordance, no expiry beyond a blanket dusk sweep, and no
handling of the Cerenovus dying or being poisoned mid-day.

## Defects and gaps

1. **P0** · The chosen character is not recorded anywhere · The ability is
   "choose a player **& a good character**"; the app models only the player. The
   `Mad` reminder has no payload (`PlacedReminder(sourceId, label)`,
   `GameState.kt:6-11`) and the show-card picker's selection is thrown away
   (`NightScreen.kt:375-377`, local state). Consequence: at 3pm the next day the
   storyteller cannot ask the app what Ivan is supposed to be claiming, cannot
   be reminded to check, and cannot judge the execution. This is the single
   biggest gap for this character. · Repro: run the Cerenovus step, place `Mad`,
   advance to day — open any screen and try to find the character.

2. **P0** · No day-time enforcement surface · "During the next day or night, if
   the mad player hasn't convincingly tried to persuade the group they are that
   character, you can execute them." The app offers no day-start briefing, no
   banner, no "execute for broken madness" action, and no accounting that such
   an execution **uses the day's execution**. The only mention is a nomination
   warning that fires only if the mad player happens to be the *nominator*. ·
   `StatusEffects.kt:162-164`, `DayScreen.kt:54-278` · Repro: make someone mad,
   advance to day, look at the Day tab.

3. **P1** · The nomination warning only fires for the nominator, and only for
   nominations · If the mad player is *nominated*, or is never involved in a
   nomination at all, nothing surfaces. Madness is about their whole day, not
   their nominations. · `StatusEffects.kt:162-164`.

4. **P1** · The warning matches the label `Mad` regardless of source · The
   generic reminder list (`SeatSheet.kt:502`) includes a plain `Mad` token, and
   the Harpy also uses `("harpy","Mad")` (`GameActions.kt:239`). Any of these
   makes the app claim the player is "Cerenovus-mad". ·
   `StatusEffects.kt:162-164` · Repro: place the generic `Mad` token, start a
   nomination from that seat.

5. **P1** · Showing the three cards is a four-dialog chore, every night · The
   official sequence is one wake with three cards in order. The app makes the
   storyteller open `GuideShowDialog` per card, and for "Mad as…" re-find the
   character in a search field and a chip grid (`NightScreen.kt:392-435`) even
   though they just chose it. · Repro: run the step and count the taps.

6. **P1** · The "Madness demand" card is a placeholder · The prepared text is
   literally `YOU ARE MAD THAT YOU ARE… CAMPAIGN OR BE EXECUTED`
   (`night_guide.json:640-645`), so the storyteller types the character name in
   the dark, at the table, every night.

7. **P1** · "Good character" is not enforced, and the Goblin exception is not
   offered · The picker (`NightScreen.kt:406-435`) lists every script character
   including Demons and Minions. The Goblin jinx is in the data
   (`night_and_jinxes.json:154`) but is only visible as a passive line in the
   seat sheet (`SeatSheet.kt:222-235`) and the jinx dialog — it never relaxes
   the picker.

8. **P2** · Madness does not stop when the Cerenovus dies or is poisoned mid-day
   · The rules say to stop enforcing silently; the app keeps the `Mad` token
   until the blanket dusk sweep (`GameActions.kt:238, 258-263`) and gives the
   storyteller no signal. · Repro: execute the Cerenovus at noon on day 2; the
   `Mad` token they placed on night 2 is still there and still generates the
   nomination warning.

9. **P2** · Dusk expiry is a coincidence, not the rule · "Or night" madness (the
   window between dusk and the Cerenovus's next choice) is dropped. Correct
   modelling is: the token is replaced when the Cerenovus chooses again, and
   removed when the Cerenovus loses their ability — not swept blindly at dusk.
   The dusk sweep also silently removes the token when the Cerenovus is dead and
   will never re-place it, hiding the fact that madness has ended.

10. **P2** · A dead Cerenovus with a retained ability is unsupported ·
    `NightScreen.kt:751-757` prints "All holders are dead — usually skip." A
    Vigormortis-killed Cerenovus keeps their ability and must still act; the app
    has no representation of that (`FullGamePlaytestTest.kt:976` describes
    exactly this scenario in prose only). · Repro: Vigormortis kills the
    Cerenovus, advance a night.

11. **P2** · No madness state on the target's grimoire seat beyond one token ·
    The mad player is often also the Mutant, the Goblin, or a Pixie; the app
    cannot show the interaction because it does not know the character. See
    `mutant.md` defect 6.

12. **P3** · An impaired Cerenovus still gets the full run-book · The guide
    (`night_guide.json:626-630`) never mentions that a drunk/poisoned Cerenovus's
    madness is unenforceable and that the storyteller should still show all the
    cards to hide it.

## Proposed behaviour (spec)

### State the engine needs

The `Mad` reminder must carry the character it is about. Two options; recommend
(a) because it is the smallest change that also fixes Pixie/Goblin/Harpy:

- (a) Add an optional payload to `PlacedReminder`:
  `data class PlacedReminder(val sourceId: String, val label: String, val characterId: String? = null, val targetPlayerId: Long? = null)`
  — nullable and defaulted, so existing saves deserialize unchanged.
- (b) Encode it in the label (`"Mad: Mutant"`) — rejected: breaks the expiry
  tables, the exclusive-placement matching and the label-based `isImpaired`
  heuristics.

### Night step

- when: **both** first and other nights.
- wake condition: a seat holds `cerenovus` **and** (that seat is alive **or**
  carries a "retains ability" marker, e.g. `vigormortis:...`). Never show
  "usually skip" for a retained-ability minion.
- targets:
  - **target player**: 1, any seat — alive or dead, good or evil, including the
    Cerenovus themself and travellers. Sort: living good players first.
  - **madness character**: 1, from the script. Constrained to
    `team in {TOWNSFOLK, OUTSIDER}`, **plus the Goblin when the Goblin jinx is
    active** (`GameData.activeJinxes` already computes this,
    `SeatSheet.kt:225-227`). Sort: not-in-play characters that make a plausible
    claim first, then in-play. Show "different from last night" as a hint, not a
    constraint (the rules do not require it).
- immediate effects:
  - `placeExclusiveReminder(target, PlacedReminder("cerenovus", "Mad", characterId = chosen))`
    — exclusive is correct: one Cerenovus, one madness at a time.
  - Queue the three show cards **as an ordered, one-tap sequence**, all
    pre-filled:
    1. `ShowCard.Message("THIS CHARACTER SELECTED YOU")`
    2. `ShowCard.CharacterCard("", "cerenovus")`
    3. `ShowCard.CharacterCard("YOU ARE MAD THAT YOU ARE", chosenId)` with the
       subtitle `CAMPAIGN OR BE EXECUTED`
    A "Show all three" button that advances on tap is the right ergonomic here
    (this is a phone, in the dark, mid-night).
  - If the Cerenovus is impaired, still run the whole sequence (to hide it) but
    mark the token as ineffective (see below) and print a storyteller-only line:
    `Cerenovus is drunk/poisoned — show everything, but do NOT execute for
    broken madness.`
- deferred effects (this is the part that is entirely missing today):
  - **Day-start briefing line**: `<target> is mad that they are the <character>
    — if they don't campaign convincingly, you may execute them (uses today's
    execution).`
  - A persistent **Day-tab banner** with two buttons:
    `They're campaigning — fine` (dismiss for today) and
    `Execute <target> for broken madness` (runs the Mutant-style execution from
    `mutant.md`: `DeathCause.EXECUTION`, sets `executionUsedToday`, produces a
    read-aloud declaration).
  - **Nomination-time note**, for both nominator *and* nominee:
    `<name> is mad that they are the <character>.` (fixes defect 3).
- expiry:
  - Remove the token the moment the Cerenovus **dies without retaining their
    ability**, or becomes drunk/poisoned — and say so in the log:
    `Madness on <target> is no longer enforced (Cerenovus lost their ability).
    Do not tell them.`
  - Otherwise the token is replaced by the next night's choice
    (`placeExclusiveReminder` already does this).
  - **Remove `("cerenovus","Mad")` from `EXPIRES_AT_DUSK`**
    (`GameActions.kt:238`) and replace it with the two conditions above, so the
    "or night" window is modelled and so the token does not vanish when the
    Cerenovus is dead.
- information: none computed. The storyteller judges.
- visibility: only the mad player sees anything, and only the three cards.
  The Demon and other Minions learn nothing.
- day-time inputs to record: reuse the `PublicClaim` structure from `klutz.md`
  with `kind = "cerenovus-madness-check"` so the storyteller can log
  "claimed it at 14:05, convincingly" and defend the decision afterwards.

### Interactions / jinxes to handle explicitly

- **Goblin** (jinxed): relax the "good character" constraint to allow `goblin`
  when the Goblin is on the script. Surface the jinx text inline on the picker.
- **Mutant**: if the target is the Mutant and the chosen character is an
  **Outsider**, raise the double-bind note described in `mutant.md`.
- **Pixie**: if the target is the Pixie, note that the Pixie's own madness rules
  interact — the app should at minimum print both tokens' characters.
- **Harpy**: `("harpy","Mad")` also uses the label `Mad`; all consumers must
  match on `sourceId`, not label.
- **Vortox / Drunk / Poisoned Cerenovus**: madness unenforceable; show cards
  anyway.
- **Dead target**: legal, and an execution of a dead player still uses the day's
  execution.
- **Evil target**: legal; add the caution "executing a mad evil player may hand
  the game to good — you are not required to execute."

### UI text for the step

- Title: `Cerenovus — make a player mad`
- Body: `Wake <Cerenovus>. They point to a player, then to a good character.
  Sleep them, wake the target, show the three cards.`
- Character picker header: `Mad as which character? (Townsfolk or Outsider<, or
  the Goblin — jinxed>)`
- After confirm: `<target> is mad as the <character> until you say otherwise.
  Check on them tomorrow.`

### Data changes

- `night_guide.json:624-671` — replace the `Madness demand` placeholder text
  with a templated `YOU ARE MAD THAT YOU ARE {character}` and add: "You may
  choose a dead player, an evil player, or the Cerenovus themself. If the
  Cerenovus dies or becomes drunk, silently stop enforcing the madness — never
  tell the player."
- `characters.json:1012-1024` — no ability-text drift.

## Tests to add

1. **Given** the Cerenovus chooses Ivan and the Mutant, **then** Ivan carries
   `PlacedReminder("cerenovus","Mad", characterId = "mutant")` and no other seat
   does.
2. **Given** that state, **when** the day starts, **then** the day-start
   briefing contains "Ivan is mad that they are the Mutant".
3. **Given** that state, **when** a nomination is declared with Ivan as the
   **nominee**, **then** `nominationWarnings` names Ivan and the Mutant.
4. **Given** a plain `PlacedReminder("", "Mad")` on a seat, **then**
   `nominationWarnings` does **not** describe them as Cerenovus-mad.
5. **Given** a `PlacedReminder("harpy","Mad")`, **then** the Cerenovus text does
   not fire.
6. **Given** madness on Ivan and a living Cerenovus, **when** `advancePhase`
   runs `DAY -> NIGHT`, **then** the token is **still present** until the
   Cerenovus's new choice replaces it (regression against the current blanket
   dusk sweep).
7. **Given** madness on Ivan, **when** the Cerenovus is executed at noon,
   **then** the token is removed and the log records that madness is no longer
   enforced.
8. **Given** madness on Ivan, **when** the Cerenovus gains `poisoner:Poisoned`,
   **then** the day banner reports the madness as unenforceable.
9. **Given** the Goblin is on the script, **then** the madness-character picker
   includes `goblin`; **given** it is not, **then** the picker contains only
   Townsfolk and Outsiders.
10. **Given** the storyteller executes Ivan for broken madness, **then** the
    death has `cause == DeathCause.EXECUTION` and `executionUsedToday` is set.
11. **Given** a Cerenovus killed by the Vigormortis, **then** the night step is
    still emitted and is not annotated "usually skip".
12. **Given** the Cerenovus targets the Mutant with an Outsider character,
    **then** the engine emits a Mutant double-bind note.
