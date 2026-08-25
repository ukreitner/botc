# Savant (savant) — Sects & Violets Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Savant> (fetched 2026-08-25).

Current ability text:

> "Each day, you may visit the Storyteller to learn 2 things in private: 1 is true
> & 1 is false."

**How to run (quoted):**

> "Once per day, if the Savant requests a private chat with you, take them away
> from the circle so you cannot be overheard. Whisper two pieces of information,
> one true and one false, to the Savant."
>
> "Keep the information you give helpful and related to the game. Avoid saying who
> exactly the Demon is, or it could be a very short game."

**Examples (quoted):**

> - "The Savant learns that 'All players wearing glasses are good' and that 'One
>   player sitting on the black couch is a Minion.'"
> - "The Savant learns that 'A Snake Charmer is in play' and 'Everybody got true
>   information last night.'"
> - "The Savant learns that 'The Demon is a woman' and 'Benjamin is evil.'"
> - "The Savant learns that 'Evin and Amy are the same alignment' and 'There is one
>   Outsider in play.'"

**Clarifications (quoted):**

> - "A drunk or poisoned Savant might get two pieces of true information or two
>   pieces of false information."
> - (Tips, storyteller-relevant) "Pay attention to precise wording — Storytellers
>   choose words carefully"; "Remember: reversing false information requires
>   logical care (e.g. 'Witch chose a good player' reversed doesn't simply mean
>   'chose evil')."

**Not stated on the page — flagged as inference, not fact:**

- **Vortox.** The Savant page says nothing about the Vortox. The Vortox's own
  ability text in this repo (`characters.json`, id `vortox`) is "Townsfolk
  abilities yield false info", so the natural reading is that a Vortox Savant gets
  **two false** pieces. The app should present this as a strong default with an
  override, not as a hard rule.
- **Dead Savant.** The page does not say. The general rule is that dead players
  lose their abilities, so a dead Savant does not visit. Present as a default.
- **Who initiates.** The How-to-Run is explicit that the *Savant* initiates
  ("if the Savant requests a private chat"), so the app must not force a visit —
  it must make recording one instant when it happens.

**Night order / jinxes**

- The Savant has **no** night step: absent from both `firstNight` and `otherNight`
  in `night_and_jinxes.json`. That is correct.
- No jinxes involve the Savant (checked against all 58 entries in
  `night_and_jinxes.json`).

## What the app does today

**Nothing. The Savant exists only as a row of JSON.**

- `characters.json:905-916` — id, name, edition `sv`, team `townsfolk`, ability
  text, `setup:false`, empty `firstNightReminder`/`otherNightReminder`, empty
  `reminders`.
- `night_and_jinxes.json` — absent from both night orders (correct).
- `night_guide.json` — **no `savant` key at all** (116 entries; the Savant is one
  of the missing ones). So even the Reference/Script tab has no how-to-run prose
  for it.
- `grep -rn savant --include=*.kt engine/src/main app/src/main` → **no hits.**
  Not in `InfoCalc.supports` (`InfoCalc.kt:29-36`), not in `StatusEffects`, not in
  any screen.
- The Day tab (`app/src/main/java/com/clocktower/grimoire/ui/screens/DayScreen.kt`)
  is nominations, votes and executions only — there is no concept of a day-time
  storyteller interaction with a player.
- The game log (`screens/GameExtras.kt:46-108`) is derived purely from
  `state.deaths` and `state.nominations`; there is nowhere to store what was said.
- The only adjacent affordance is the free-text "Storyteller notes" dialog
  (`GameShell.kt:685-706`) — one global text box, no per-day structure, no
  reminder, and it lives behind the overflow menu.

**The storyteller's experience.** The Savant is invisible. When the player taps
the ST on the shoulder mid-day, the ST must improvise two statements on the spot,
remember which one was true, remember what they already said on previous days so
the set stays consistent, and remember it all again three days later when the
Savant claims publicly. This is exactly the class of problem the user reported for
the Gossip ("make it easy to write down all the gossips"), and the Savant is worse
because the *content* is invented by the ST rather than announced by the player.

## Defects and gaps

1. **P0 · No way to record what the Savant was told.** Two sentences per day, per
   Savant, for up to five days, with a true/false tag each, must be held in the
   ST's head. There is no data structure (`GameState`, `GameState.kt:93-115`, has
   no day-statement list), no UI, and no log. Repro: run any SV game with a
   Savant — after the second visit there is no place in the app that knows what
   was said.
2. **P1 · No day-start prompt.** `advancePhase` NIGHT→DAY
   (`GameActions.kt:258-263`) only clears dawn tokens; `GameShell` shows no
   morning briefing at all. The ST is never reminded that a Savant is in play, is
   alive, and has not visited today.
3. **P1 · No help composing the two statements.** The grimoire *knows* the true
   answers to every one of the wiki's example statements ("Evin and Amy are the
   same alignment", "There is one Outsider in play", "A Snake Charmer is in play",
   "The Demon is a woman"). The app computes exactly this class of fact for ~30
   characters in `InfoCalc` (`InfoCalc.kt:38-85`) and throws none of it at the
   Savant. This is the single highest-value missing feature for this character.
4. **P1 · No impairment handling.** `StatusEffects.isImpaired`
   (`StatusEffects.kt:36-46`) can already tell the ST that the Savant is drunk or
   poisoned, and the rule then is "two true **or** two false" — a decision the ST
   must make and record. Nothing surfaces it. Repro: poison the Savant, go to the
   Day tab — no indication.
5. **P1 · No consistency checking across days.** Because nothing is stored, the ST
   can (and in practice does) contradict themselves: say "there is one Outsider in
   play" as the *true* statement on day 2 and as the *false* statement on day 4.
   With a stored record the app can flag a repeat and flag a direct contradiction.
6. **P2 · No "visited today" marker.** The ability is once per day; there is no
   token in `characters.json:905-916` and no state, so a second visit is
   undetectable. (Officially the Savant has no reminder token, so this should be
   state, not a grimoire token.)
7. **P2 · No how-to-run text anywhere.** `night_guide.json` has no `savant` key,
   and the Reference tab (`screens/ReferenceScreen.kt`) shows ability text only —
   so the "avoid saying who the Demon is" guidance never reaches the ST.
8. **P2 · Unreachable when gained.** A Philosopher who gains the Savant ability
   (an explicitly recommended pick on the wiki's Philosopher page) has no route to
   any of this; see `docs/audit/characters/philosopher.md`.
9. **P2 · Vortox interaction undecided.** With a Vortox alive, Townsfolk info must
   be false; the app has the Vortox check already (`InfoCalc.kt:161-164`) but no
   Savant to apply it to.
10. **P3 · Nothing to show the player.** The information is whispered, so no show
    card is strictly needed — but on a phone-run game a full-screen two-line card
    (`ShowCard.Message`, `components/ShowCards.kt:66`) read privately is often
    easier than whispering across a noisy room.

## Proposed behaviour (spec)

The Savant needs a **day-phase information feature**, and it should be built as
the first consumer of a general "day statements" store that Gossip, Juggler,
Artist, Fisherman, Slayer, Mutant and Nightwatchman also need.

### State

Add to `GameState` (`GameState.kt:93-115`):

```kotlin
/** Everything said or asked between ST and a player during a day. */
val dayRecords: List<DayRecord> = emptyList(),

@Serializable
data class DayRecord(
    val day: Int,
    val playerId: Long,
    /** Character whose ability this belongs to (acting id, not shown id). */
    val sourceId: String,          // "savant", "gossip", "juggler", ...
    val kind: String,              // "savant_visit" | "gossip_statement" | ...
    val textA: String = "",
    val textB: String = "",
    /** For the Savant: which of A/B was the true one, "A" | "B" | "both" | "neither". */
    val trueSide: String = "A",
    val note: String = "",
)
```

`GameActions.recordDayRecord(state, record)` / `removeDayRecord(state, index)`,
undoable like every other action.

### Day step

- **when**: DAY phase, any time; the Savant initiates. The app must never gate the
  day on it.
- **wake/act condition**: a seat whose `nightRoleId` (see the Philosopher spec) is
  `savant`, alive, and with no `DayRecord{day == cycle, sourceId == "savant",
  playerId == seat}` yet.
- **entry points** (all three, because this happens mid-conversation):
  1. A **Day tab card**, pinned above "New nomination": `Savant — Nia has not
     visited today` with a `Record visit` button.
  2. A **day-start prompt** when NIGHT→DAY advances and a living Savant is in play
     (same dialog pattern as the existing setup prompts,
     `GameShell.kt:347-479`): "Savant in play — Nia may visit you today."
     Dismissible, and it must not block.
  3. The seat sheet (`SeatSheet.kt:156-384`) gets a `Savant visit…` action when
     the seat acts as the Savant.
- **the composer** (the substance of the feature). A two-pane sheet:
  - Pane A "TRUE", pane B "FALSE" (swappable with one tap; the record stores
    `trueSide`).
  - Each pane: a free-text field **plus** a list of one-tap generated candidates,
    computed from the live grimoire. Suggested generators (all derivable from
    existing helpers):
    - `"<X> and <Y> are the same alignment"` / `"different alignments"` — reuse
      `InfoCalc.seamstress` logic (`InfoCalc.kt:356-365`).
    - `"There are N Outsiders in play"` — team counts.
    - `"A <Character> is in play"` / `"No <Character> is in play"` — in-play set.
    - `"N of the players sitting between <X> and <Y> are evil"` — arc count.
    - `"Exactly N players got true information last night"` — from the
      impairment set (`StatusEffects.isImpaired` over all info-holders).
    - `"The Demon is <attribute>"` — free text, seeded with the Demon's name so
      the ST can pick a shared attribute deliberately.
    - `"<X> is evil"` / `"<X> is good"`.
    - `"The nearest Minion to the Demon is N seats away"` — reuse
      `InfoCalc.clockmaker` (`InfoCalc.kt:218-241`).
    - Every candidate is generated in **both** a true and a false variant, and the
      false variant must be a *plausible* perturbation (swap a name, change a
      count by one), never a trivially absurd one.
  - A guard rail from the How-to-Run rendered inline: *"Keep it helpful and
    related to the game. Don't name the Demon outright."*
  - A "previously told" strip listing this Savant's earlier records, with a
    warning when the new text duplicates or contradicts one.
- **impairment**: if `StatusEffects.isImpaired(state, lookup, savantSeat)` or a
  Vortox is alive, the composer switches mode:
  - drunk/poisoned → radio "two TRUE / two FALSE" (per the wiki), `trueSide`
    becomes `"both"` or `"neither"`;
  - Vortox alive → default to "two FALSE" with a caption citing the Vortox's
    "Townsfolk abilities yield false info", and allow the ST to override.
  The banner text: `Nia is POISONED (Poisoner) — give two true or two false
  things, your choice.`
- **immediate effects**: append the `DayRecord`; mark the Day-tab card
  `Visited today`. No grimoire token (the Savant has none officially).
- **deferred effects**: the record is permanent, appears in the game log
  (`screens/GameExtras.kt:46-108` — add `dayRecords` to the entry builder as
  `D<n> · Savant (Nia): TRUE "…" / FALSE "…"`), and is surfaced in the composer on
  later days.
- **expiry**: the once-per-day allowance resets at dawn implicitly, because the
  card keys on `day == state.cycle`.
- **information**: nothing is computed *for* the player; everything is computed to
  *help the ST author* the pair. Nothing is auto-sent.
- **visibility**: only the Savant. Offer a `Show privately` button that renders
  both lines as a `ShowCard.Message` (`components/ShowCards.kt:66`) for a silent
  hand-over, with the true/false tags stripped.
- **day-time inputs the app must let the ST record**: the two statements and which
  was true — that *is* the feature.
- **interactions**:
  - Philosopher gaining Savant → the Day card keys on `nightRoleId`, so it appears
    for the Philosopher's seat automatically.
  - Dead Savant → card hidden by default with a "they're dead — no visit" caption
    (flagged as the general dead-loses-ability rule, not a page citation).
  - Pit-Hag turning someone into the Savant mid-game → the card appears the next
    day automatically.

### UI text

- Day card, not yet visited: `Savant — Nia may visit you today` · button
  `Record what you told them`.
- Day card, visited: `Savant — Nia visited on day 3` · `TRUE: … / FALSE: …` ·
  button `Edit`.
- Composer header: `Two things for Nia — one true, one false.`
- Impaired banner: `Nia is drunk/poisoned — give two true OR two false things.`
- Vortox banner: `Vortox is in play — Townsfolk info must be false. Give two false
  things.`
- Duplicate warning: `You told Nia this on day 2 as the FALSE one.`

### Data changes

- `night_guide.json`: add a `savant` entry. The file is currently keyed by night
  (`first`/`other`), so either extend `NightGuideEntry` (`NightGuide.kt:36-40`)
  with a `day: GuideNight?` field, or add a sibling `day_guide.json`. The prose
  should be the wiki's How-to-Run plus the drunk/poisoned clause.
- `characters.json:905-916`: no change (no reminders is correct).

## Tests to add

1. **Recording a visit.** Given a day-2 state with `savant` on seat 4; When
   `recordDayRecord(state, DayRecord(day=2, playerId=4, sourceId="savant",
   kind="savant_visit", textA="Evin and Amy are the same alignment",
   textB="There is one Outsider in play", trueSide="A"))`; Then
   `state.dayRecords` has one entry and a `savantVisitedToday(state, 4)` helper
   returns true.
2. **Once per day.** Continuing (1); Then `savantVisitedToday(state, 4)` is true
   for day 2 and false after `advancePhase` twice (night 3 → day 3).
3. **Prompt condition.** Given a living Savant and DAY phase with no record for
   today; Then `DayPrompts.pending(state, lookup)` contains a `savant` prompt for
   that seat; and it does **not** when the Savant is dead, or when a record for
   today exists.
4. **Impaired mode.** Given the Savant carries `("poisoner","Poisoned")`; Then the
   Savant prompt payload reports `mode == TWO_TRUE_OR_TWO_FALSE`, and a record
   with `trueSide = "both"` is accepted.
5. **Vortox default.** Given a living Vortox and a sober Savant; Then the prompt
   payload reports `mode == TWO_FALSE` with a caveat naming the Vortox.
6. **Candidate generation is grounded.** Given a known 8-seat grimoire; Then
   `SavantSuggestions.trueStatements(state, lookup)` contains the string
   `"There are 1 Outsiders in play"`-equivalent for the actual outsider count, and
   `SavantSuggestions.falseStatements` never returns a statement that is currently
   true (assert by re-evaluating each generated statement's predicate).
7. **Philosopher-gained Savant.** Given seat 0 is the Philosopher with
   `actingCharacterId == "savant"`; Then the day prompt targets seat 0 and the
   real Savant's absence does not matter.
8. **Log integration.** Given one recorded visit; Then `GameLogDialog`'s derived
   entry list contains a `D2` entry mentioning "Savant".
9. **Contradiction detection.** Given a day-2 record with
   `textA = "A Snake Charmer is in play"`, `trueSide = "A"`; When a day-4 record
   uses the same text as `textB` with `trueSide = "A"`; Then
   `SavantSuggestions.conflicts(state, newRecord)` is non-empty.
