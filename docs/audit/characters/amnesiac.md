# Amnesiac (amnesiac) — Experimental Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Amnesiac> (fetched via
`action=parse&prop=wikitext`, 2026-08-25).

Current ability text (verbatim):

> "You do not know what your ability is. Each day, privately guess what it is: you learn how accurate you are."

**Summary bullets (verbatim):**

- "The Storyteller decides what the Amnesiac's ability is. It may be the same ability as another character in Blood On The Clocktower, something similar, or something original."
- "The Amnesiac may wake at any time during the night to learn information or to choose a player, or their ability may be passive—not requiring action from the Amnesiac player."
- "Each day, the Amnesiac talks to the Storyteller in private, and makes a guess as to what their ability is. The Storyteller answers 'cold' if the guess is very wrong, 'warm' if the guess is on the right track, 'hot' if the guess is very close, and 'bingo' if the guess is spot on."
- "Their guess may be specific, such as 'Am I learning two players each night that are the same alignment?', or vague, such as 'Is my ability something to do with dead players?'"

**How to Run (verbatim):**

> During setup, decide what ability the Amnesiac player has. During the game, treat that player as if they had that ability, waking them when needed, prompting them to choose players when needed, or whatever else is appropriate. Use the Amnesiac's **?** reminders if you need to.
>
> Each day, the Amnesiac makes a guess about their ability in private. Answer "Cold", "Warm", "Hot", or "Bingo".
>
> Make the Amnesiac's ability guessable, so that the Amnesiac can figure out what their ability is over time. Learning a piece of information each night, or a power that affects the game in a way that the Amnesiac notices, are both good ideas.
>
> If the Amnesiac guesses their ability, but the wording is different, still tell them they guessed correctly.
>
> You can make the Amnesiac's ability slightly better than a normal Townsfolk's ability. Not knowing what it is, the player will need to work harder to receive its full benefit.

**Examples (verbatim):**

1. "Each night, the Amnesiac wakes and is prompted to point at two players. The Storyteller shakes their head on the first night, and nods on the second. The Amnesiac guesses 'Am I learning if both players are Minions?' The Storyteller says 'Hot' because their ability is that they detect if either of the two players are a Minion."
2. "Each night, the Amnesiac learns a number. The Amnesiac is learning how many of their living neighbours are Townsfolk."

**Bluffing section (verbatim, defines the four answers precisely):**

- "Cold, your question has no relation to your ability."
- "Warm, your question has some relation to your ability."
- "Hot, you're close to guessing your ability."
- "Bingo, you've guessed your ability."

**Jinxes:** none listed on the wiki; none in the app's data.

**Night order:** first night index 45 of 76 (between Damsel and Washerwoman),
other nights index 67 of 96 (between Damsel and Farmer) — `night_and_jinxes.json:340`
and `:440`. This is where the *placeholder* wake sits; the actual ability may
need to fire somewhere else entirely (a Poisoner-like Amnesiac ability belongs
at the Poisoner's slot). The official position is a compromise, not a rule.

## What the app does today

Data:
- `characters.json:1235` — ability text matches the wiki exactly. `setup: false`; both night reminders present ("Do whatever needs to be done to satisfy the Amnesiac's ability."); `reminders: ["?", "?", "?"]` (three copies); `remindersGlobal: []`.
- `night_and_jinxes.json:340` / `:440` — first- and other-night positions present. **Correct.**
- `night_guide.json:849` — `first` and `other` entries with good prose (they explicitly mention deciding the ability at setup, the `?` reminders, "give no explanation", the hot/cold answers, and the impairment rule) and one show card each: `{"label":"Info","kind":"message","text":"You learn…","token":""}`.

Code: **no `amnesiac` string exists anywhere in `engine/src` or `app/src`.**
Generic handling only:
- `NightOrder.build` (`NightOrder.kt:40`) emits an "Amnesiac" step on **every** night with `detail = "Do whatever needs to be done to satisfy the Amnesiac's ability."`.
- `NightScreen.StepDetailPanel` (`NightScreen.kt:770`) renders the guide prose plus a "» Info" chip that opens `GuideShowDialog` (`NightScreen.kt:366`) with a free-text field — so the ST *can* type "3" or a sentence and flash it full screen. This is genuinely useful and works.
- `QuickResolutions` (`NightScreen.kt:462`) has no branch; `InfoCalc.supports("amnesiac")` is false (`InfoCalc.kt:29`).
- Three `?` chips appear in `NightToolTray`; because `availableCopies == 3`, the tray's multi-copy branch (`NightScreen.kt:319–339`) lets all three be placed and recycles the oldest when a fourth is placed. That behaviour is right for a generic marker.
- Nothing expires: `("amnesiac","?")` is in neither `EXPIRES_AT_DAWN` nor `EXPIRES_AT_DUSK` (`GameActions.kt:218/231`). Correct default (the ability is unknown), but there is no way to say "this one is nightly".

Storyteller's actual experience: at setup, nothing asks them to invent an
ability, and there is nowhere to write it down except the single global
`storytellerNotes` blob (`GameState.kt:104`, menu → "Storyteller notes") or the
per-seat `note` (`SeatSheet.kt:369`). Every night an "Amnesiac" row appears
whether or not the invented ability wakes anyone, with a paragraph of generic
prose and no memory of what was done last night. Every day, the ST is
approached in private for a guess and must judge cold/warm/hot/bingo from
memory, with no record of what has already been asked or answered. If the
invented ability is, say, "each night learn how many of your living neighbours
are Townsfolk", the app will not compute it, will not remember the sequence of
numbers given, and will not tell the ST at dawn that the number changed because
someone died.

## Defects and gaps

1. **P0 · The invented ability has nowhere to live.** Rules: "During setup,
   decide what ability the Amnesiac player has… treat that player as if they
   had that ability." App: no field, no prompt, no display. The Drunk, Lunatic,
   Marionette and Fortune Teller all get blocking setup dialogs and
   `validateSetupState` rules (`GameShell.kt:348–478`, `GameActions.kt:514–545`);
   the Amnesiac gets nothing. Repro: deal a bag containing the Amnesiac → tap
   "Begin night" → setup validation passes with the ability undecided.
2. **P0 · The daily guess and the hot/cold answer are not recorded anywhere.**
   Rules: "Each day, the Amnesiac makes a guess about their ability in private.
   Answer 'Cold', 'Warm', 'Hot', or 'Bingo'." The app has no day-time input at
   all. Over a 5-day game the ST must remember five questions, five answers, and
   stay consistent with all of them — the single most-cited Amnesiac
   storytelling failure.
3. **P1 · The night step fires every night regardless of whether the invented
   ability wakes anyone.** Wiki: "their ability may be passive—not requiring
   action from the Amnesiac player." App: `NightOrder.build` emits the row
   unconditionally, and `requestPhaseAdvance` (`GameShell.kt:126`) then
   *blocks dawn* with "Night checklist incomplete" until the ST ticks a step
   that had nothing to do. Repro: passive Amnesiac ability → every night the ST
   must tick a meaningless row.
4. **P1 · The ability cannot be placed at the right point in the night order.**
   If the invented ability is "you poison a player each night", it must fire at
   the Poisoner's slot (other-night index 13), not the Amnesiac's slot (67). The
   app has one fixed position and no override.
5. **P1 · Nothing that the ST *did* for the Amnesiac is recorded.** Wiki
   Example 1 depends on remembering that the ST shook their head on night 1 and
   nodded on night 2 — and the *player* will hold the ST to that sequence. The
   only record is whatever was flashed on a full-screen card and then dismissed
   (`GuideShowDialog`, `NightScreen.kt:366`, keeps nothing).
6. **P1 · No day-start prompt.** Nothing says "the Amnesiac owes you a guess
   today".
7. **P2 · No hot/cold answer affordance.** The four answers are a fixed, tiny
   vocabulary. They should be four buttons that both record the answer and can
   flash it full-screen (the `ShowCard.Message` machinery already exists,
   `ShowCards.kt:66`).
8. **P2 · Impairment is not surfaced.** "If they are drunk or poisoned, the
   ability malfunctions" (the app's own guide text says so). Nothing computes
   `isImpaired` for the Amnesiac's step, so there is no "give false info /
   the ability does nothing tonight" cue and no false-info shortcut like the one
   `InfoCalc`-backed characters get (`NightScreen.kt:903–930`).
9. **P2 · The `?` reminders carry no meaning.** Three identical `?` chips with
   no label. The ST cannot record "? = chose Ana" vs "? = chose Ben". A free-text
   label on placement would make them usable.
10. **P2 · No "how close" progression view.** The ST needs to see the arc —
    day 1 cold, day 2 warm, day 3 hot — to answer consistently and to decide
    whether to nudge. Nothing shows it.
11. **P3 · Reminder count.** The app has three `?`; the older townsquare dump
    has one; the wiki says "Use the Amnesiac's **?** reminders" (plural).
    Three is a reasonable reading; leave as is, but note the divergence.
12. **P3 · No prompt to keep the ability guessable.** The How-to-Run's design
    advice ("Learning a piece of information each night… slightly better than a
    normal Townsfolk's ability") is exactly the kind of thing the setup dialog
    should say once, at the moment the ST is inventing it.

## Proposed behaviour (spec)

### Engine data

On `Player` (or a dedicated `GameState.amnesiac` block if a per-seat field feels
wrong):

```kotlin
/** Storyteller-invented ability text for the Amnesiac. */
val inventedAbility: String = "",
/** Night-order id whose slot this seat's invented ability wakes at, or null
 *  to use the character's own slot; "" / NONE means it never wakes. */
val abilityNightSlotId: String? = null,
val abilityWakesFirstNight: Boolean = true,
val abilityWakesOtherNights: Boolean = true,
```

Reuse the shared `DayStatement` record proposed in `alsaahir.md`, with
`characterId = "amnesiac"`, plus:

```kotlin
/** "cold" | "warm" | "hot" | "bingo" */
val response: String = "",
```

and a night-side record so the ST can see what they actually did:

```kotlin
@Serializable
data class NightAction(
    val night: Int,
    val characterId: String,   // "amnesiac"
    val playerId: Long,
    val targetIds: List<Long> = emptyList(),
    val shown: String = "",     // "3", "YES", "Ravenkeeper", "nodded"
)
```
with `val nightActions: List<NightAction> = emptyList()` on `GameState`. This
record is reusable by every "different from last night" and
"what did I show them?" character (Balloonist, Bounty Hunter, Flowergirl,
Juggler, Fortune Teller).

### Setup

- **when:** SETUP, any seat with `characterId == "amnesiac"` and
  `inventedAbility.isBlank()`.
- Blocking dialog:
  > **The Amnesiac is in play** — Ana is the Amnesiac. Write the ability you are giving them. Make it guessable: a piece of information each night, or a power they will notice. It may be slightly stronger than a normal Townsfolk ability.
  - multiline text field → `inventedAbility`
  - "Wakes at:" dropdown → `abilityNightSlotId`, defaulting to `amnesiac`, with
    every night-order id selectable (labelled by character name) plus
    **"Never wakes (passive)"**
  - two switches: "Wakes on the first night" / "Wakes on later nights"
  - a starter-list of 6–8 suggested abilities the ST can tap to fill the field
    (e.g. "Each night, learn how many of your living neighbours are Townsfolk",
    "Each night, choose 2 players: learn if either is a Minion",
    "Each night, learn a player who is the same alignment as you",
    "Each night\*, choose a player: they are protected from the Demon").
- Add to `GameActions.validateSetupState` (`GameActions.kt:514`):
  `"amnesiac" -> if (inventedAbility.isBlank()) issues += "${player.name}: write the ability you are giving the Amnesiac"`.

### Night action

- **when:** the night whose order contains `abilityNightSlotId`, and
  `abilityWakesFirstNight` / `abilityWakesOtherNights` allows it. If
  `abilityNightSlotId == null` (passive), **emit no step at all** and therefore
  do not block dawn.
- **step title:** `Amnesiac (Ana) — <first 60 chars of inventedAbility>`
- **step body:** the full `inventedAbility` text at the top, in the
  storyteller's own words, above the generic guide prose. Then:
  - a target picker (0–3 seats, free) whose selection is stored in
    `NightAction.targetIds`;
  - the existing free-text "Info" show card, whose shown text is written back
    into `NightAction.shown` when it is displayed;
  - the three `?` reminder chips, each accepting a free-text suffix on
    placement so they read `? chose Ana`, `? night 2`, …;
  - an impairment banner when `isImpaired(amnesiacSeat)`:
    **"Ana is DRUNK/POISONED — their ability malfunctions tonight. Give false info or no effect."**
  - a **"Last nights"** strip listing the previous `NightAction`s for this seat
    (`N1: pointed to Ana, Ben — shook head`, `N2: showed 3`), so the ST stays
    consistent.
- **immediate effects:** whatever the ST places by hand. The engine makes no
  assumptions.
- **deferred effects / expiry:** none by default. Because the ability is
  arbitrary, `?` tokens must **not** be added to `EXPIRES_AT_DAWN`/`DUSK`; give
  the placement dialog a "clears at dawn / at dusk / never" selector that writes
  into a per-token expiry list instead (a generic capability the whole app
  needs).
- **information / visibility:** entirely storyteller-driven; nothing is computed
  and nothing is shown to the Demon, Minions or Lunatic.

### Day-time input the app must offer

- **where:** a card in `DayScreen` (`DayScreen.kt:78`) whenever the script
  contains `amnesiac`, shown above nominations — and, unlike the Alsaahir's, it
  is **private**, so the card should carry a "private — do not read aloud" tint.
- **flow:**
  1. text field: *"What did Ana guess today?"* (pre-filled with the previous
     day's guess for quick editing)
  2. four big buttons: **Cold · Warm · Hot · Bingo**, each with the wiki
     definition as its sublabel ("no relation" / "some relation" / "close" /
     "you've got it")
  3. tapping one records the `DayStatement` and offers
     "Show full-screen" using the existing `ShowCard.Message` path.
- **history strip** directly under it:
  `D1 "something about dead players?" → Cold · D2 "do I learn numbers?" → Warm`
  with the invented ability always visible at the top of the card so the ST can
  judge accuracy without opening a menu.
- **day-start briefing line:** `Amnesiac in play — Ana owes you a private guess today. Their ability: "<inventedAbility>".`
- Add the same "reminder that the wording may differ": a one-line hint under the
  Bingo button — *"If the meaning matches but the wording differs, still say Bingo."*

### Interactions

- **Drunk / poisoned:** the invented ability malfunctions. The ST still answers
  the daily guess truthfully — the guess is about what the ability *is*, not
  about whether it worked tonight. Say this explicitly in the day card, because
  it is a common ST mistake.
- **Vortox:** if the invented ability is an information ability, Vortox makes it
  false. Surface `"VORTOX in play — this Townsfolk-style info must be FALSE"`
  on the Amnesiac's night step, reusing `InfoCalc.commonCaveats`
  (`InfoCalc.kt:158`).
- **Character change (Pit-Hag, Barber, Snake Charmer, Philosopher):** when a
  seat stops being the Amnesiac, `inventedAbility` should be cleared and the
  new Amnesiac (if any) should re-trigger the setup dialog.
- **Marionette/Drunk shown as Amnesiac:** `shownCharacterId == "amnesiac"` on a
  Drunk/Marionette seat means the ST must still invent something and give false
  results. The setup dialog should trigger on
  `characterShownToPlayerId == "amnesiac"`, not only on `characterId`.
- **Jinxes:** none.

### Data changes

- `characters.json:1235` — no change.
- `night_guide.json:849` — keep the prose; add a `shows` entry for a
  number card (`kind:"message"`) and a good/evil card so the common
  "thumbs up / thumbs down" Amnesiac abilities are one tap; extend the guide
  schema with the optional `day` block (see `alsaahir.md`) carrying the
  cold/warm/hot/bingo definitions.
- `night_and_jinxes.json:340`/`:440` — no change; the position becomes the
  *default* for `abilityNightSlotId`.

## Tests to add

`engine/src/test/kotlin/com/clocktower/engine/AmnesiacTest.kt`

1. **Given** a seat with `characterId = "amnesiac"` and `inventedAbility = ""` at SETUP; **when** `validateSetupState`; **then** it reports "write the ability you are giving the Amnesiac".
2. **Given** `abilityNightSlotId = null` (passive); **when** `NightOrder.otherNight`; **then** **no** amnesiac step is produced, and the dawn guard in `GameShell` therefore has nothing to block on.
3. **Given** `abilityNightSlotId = "poisoner"`; **when** `NightOrder.otherNight`; **then** a step for that seat appears at the Poisoner's index (13), not the Amnesiac's index (67), titled with the Amnesiac's name.
4. **Given** `abilityWakesFirstNight = false`; **when** `NightOrder.firstNight`; **then** no amnesiac step appears, but the other-night sheet still has one.
5. **Given** two `NightAction`s recorded on nights 1 and 2; **when** the night 3 step is built; **then** both appear in the "last nights" strip in chronological order.
6. **Given** a `DayStatement(day = 2, response = "warm")`; **when** day 3's card renders; **then** day 2's guess and answer are shown and the day-3 field is pre-filled with the day-2 text.
7. **Given** the Amnesiac holds `poisoner:Poisoned`; **when** the night step is built; **then** its caveats include an impairment warning naming the Poisoner.
8. **Given** a Vortox is alive; **when** the Amnesiac's night step is built; **then** the caveat "VORTOX in play — Townsfolk info must be FALSE" is present.
9. **Given** a Drunk seat with `shownCharacterId = "amnesiac"`; **when** setup validation runs; **then** it also demands an invented ability for that seat.
10. **Given** three `?` reminders placed and a fourth placed; **then** the oldest is recycled and exactly three remain (locks the existing `NightToolTray` multi-copy behaviour, `NightScreen.kt:319`).
11. **Given** an Amnesiac whose `characterId` is changed to `pithag` mid-game; **then** `inventedAbility` is cleared from that seat.
