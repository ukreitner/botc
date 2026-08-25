# Juggler (juggler) — Sects & Violets Townsfolk

## Official rules (sources)

Sources: <https://wiki.bloodontheclocktower.com/Juggler> (fetched 2026-08-25),
<https://wiki.bloodontheclocktower.com/Cannibal>,
<https://wiki.bloodontheclocktower.com/Vortox>,
<https://wiki.bloodontheclocktower.com/Mathematician>.

Current ability text:

> "On your 1st day, publicly guess up to 5 players' characters. That night, you learn how
> many you got correct."

**How to Run (verbatim):**

> "During the first day, if the Juggler declares that they are using their ability, then
> you enter the circle, holding the Grimoire. The Juggler can make up to five guesses,
> each of any one player and any one character. For each correct guess, mark the Juggler
> with a **CORRECT** reminder. (*Make sure the players don't see how many reminders you're
> placing.*)
>
> That night, wake the Juggler. Show them fingers (*0, 1, 2, etc.*) equaling the number of
> **CORRECT** reminders. Put the Juggler to sleep. Remove the **CORRECT** reminders and
> the Juggler's night token when convenient.
>
> If the Juggler is guessing too fast, prompt them to slow down."
>
> "If an evil player is bluffing as the Juggler, it can help their bluff if you pretend to
> move tokens around the Grimoire as they make their fake guesses."

**Examples (verbatim):**

> "During the first day, the Juggler guesses that Alex is the Town Crier, Mia is the No
> Dashii, and Julian is the Sage. That night, the Juggler learns a "2," meaning two of
> those guesses were correct.
>
> During the fourth night, the Savant gets turned into the Juggler. The next day, the new
> Juggler guesses that Benjamin is the Pit-Hag, that Benjamin is the Witch, and that Amy
> is the Pit-Hag. That night, the Juggler learns a "1.""

**Drunk / poisoned handling (from the same page):**

> "If the Juggler made their guesses while drunk or poisoned, but is sober and healthy
> when their ability triggers that night, then the Storyteller still gives them true
> information."

**Jinx (verbatim):**

> **Cannibal** — "If the Juggler guesses on their first day and dies by execution, tonight
> the living Cannibal learns how many guesses the Juggler got correct."

**Storyteller-relevant timing / edge cases distilled from the above**

- **"Your 1st day"** is the first day on which this player holds the Juggler ability — not
  necessarily day 1 (example 2: a Savant Pit-Hagged into the Juggler on night 4 juggles on
  **day 4** and learns the number on **night 4**… strictly, "the next day" after night 4
  is day 4 in app numbering, and "that night" is night 5).
- **Up to five guesses**, each a (player, character) pair. **Duplicate players and
  duplicate characters are allowed** (example 2 guesses Benjamin twice and Pit-Hag twice).
- Guesses are **public**, made in the circle, with the storyteller physically present.
- The count is **hidden while being recorded** — "make sure the players don't see how many
  reminders you're placing".
- **The ability triggers only once**, on the night after the juggling day. On every other
  night the Juggler does not wake.
- The Juggler must **declare** they are using the ability; a Juggler who never juggles
  never wakes.
- **Drunk/poisoned at guess time but healthy at night → true information.** The impairment
  that matters is the one at the moment the ability *triggers* (the night), not at guess
  time.
- **Vortox**: the number **must** be false ("Even if they are drunk or poisoned, it must be
  false"). The Mathematician page's example confirms that a *drunk* Juggler may still be
  given correct information ("The drunk Juggler gets correct information… the Snake Charmer
  and Juggler's abilities worked as normal").
- **Misregistration** (inference from the Spy/Recluse texts — the Juggler page is silent):
  a guess of an evil character on the **Recluse** may be counted correct; a guess of a
  Townsfolk/Outsider on the **Spy** may be counted correct. Storyteller's choice, and it is
  a real lever.
- **The Drunk / Marionette / Lunatic**: the correct answer is their *actual* character
  (Drunk, Marionette, Lunatic), not the character they believe they are.
- **Cannibal**: if the Juggler juggled on their first day and is then **executed** that same
  day, the living Cannibal (who has eaten them) learns the number that night instead.

## What the app does today

| path | what it holds |
|---|---|
| `engine/src/main/resources/botc/data/characters.json:838-851` | Ability text matches. `otherNightReminder`: "If today was the Juggler's first day: Show the hand signal for the number (0, 1, 2, etc.) of 'Correct' markers. Remove markers." `reminders: ["Correct"]` — **one** copy listed. `firstNightReminder` empty. |
| `engine/src/main/resources/botc/data/night_and_jinxes.json:455` | Other-night order index 82, after `seamstress` and before `balloonist`. Absent from `firstNight`. Correct. |
| `engine/src/main/resources/botc/data/night_and_jinxes.json:14-17` | The Cannibal jinx is present with the correct official text. |
| `engine/src/main/resources/botc/data/night_guide.json:455-467` | Good prose: "Only on the night after the Juggler's first day, and only if they publicly juggled… count the 'Correct' reminder tokens you placed during the day, then remove those reminders… On all later nights the Juggler does not wake." One `message` show card. |
| `engine/src/main/kotlin/com/clocktower/engine/InfoCalc.kt:29-36` | `juggler` is **not** in `supports()`. Nothing is computed; the whole InfoCalc panel (`NightScreen.kt:836-932`) is skipped for this step. |
| `app/src/main/java/com/clocktower/grimoire/ui/screens/DayScreen.kt:54-277` | Nominations and votes only. No facility to record guesses. |
| `app/src/main/java/com/clocktower/grimoire/ui/screens/NightScreen.kt:283-354` | The night tray's reminder placement. |
| `app/src/main/java/com/clocktower/grimoire/ui/screens/NightScreen.kt:319-339` + `GameActions.kt:194-201` | `availableCopies = character.allReminders.count { it == label }`; when `<= 1` it calls `placeExclusiveReminder`, which **removes the token from every seat** before adding it. |
| `app/src/main/java/com/clocktower/grimoire/ui/screens/SeatSheet.kt:109-117` | The seat-sheet path uses plain `addReminder`, so tokens **do** stack there. |
| `engine/src/main/kotlin/com/clocktower/engine/NightOrder.kt:142-178` | Builds a step for any in-play character on the order list, every night, unconditionally. |
| `app/src/main/java/com/clocktower/grimoire/ui/screens/GameShell.kt:147-161` | The dawn guard refuses to advance until every night step is ticked — including the Juggler's, every night. |
| `engine/src/test/kotlin/com/clocktower/engine/FullGamePlaytestTest.kt:1072` | The Juggler appears only as a **demon bluff** in the playtest — never exercised as a real character. |

**What already works — one line each:**

- Night order position and other-night-only placement are correct.
- `night_guide.json`'s prose is accurate and includes the "only if they publicly juggled"
  and "on all later nights the Juggler does not wake" conditions.
- The Cannibal jinx text is present in the data and will show in "Jinxes in play".

**Storyteller's experience today:** during the day the Juggler stands up and fires off
five guesses. The app offers nothing — you memorise them or scribble in the single global
`storytellerNotes` textarea (`GameShell.kt:685-706`). You then work out the score in your
head. That night, the Juggler step appears (as it does every night) with prose but no
number and no chips; you open the tray, tap "Correct", tap the Juggler — and if you tap
"Correct" again for a second correct guess, **the first token disappears**. Then you go to
"All tokens" → Signals to flash the number you computed mentally.

## Defects and gaps

1. **P0 · Only one `Correct` token can exist in the whole grimoire.**
   `NightScreen.kt:319-339` computes `availableCopies` from `character.allReminders.count { … }`
   = 1 for the Juggler, then takes the `placeExclusiveReminder` branch
   (`GameActions.kt:194-201`), which strips the token from **every** seat before adding it.
   The rules need up to **five** on one seat. **Repro:** expand any Juggler step, tap the
   "Correct" chip, tap the Juggler, repeat — the seat never holds more than one token.
   (Workaround exists only via Grimoire → seat → Add reminder → Juggler → Correct, four
   taps per token, which uses `addReminder` and does stack.)

2. **P0 · No way to record the guesses.**
   Up to five (player, character) pairs, declared publicly, must be captured *while the
   Juggler is talking*, then scored. The app has no day-time input of any kind
   (`DayScreen.kt:54-277`). This is the exact complaint the playtest raised about the
   Gossip, applied to the Juggler — and the Juggler is worse, because the guesses must be
   *scored*, not just remembered.

3. **P1 · The number is never computed.**
   `juggler` is absent from `InfoCalc.supports()` (`InfoCalc.kt:29-36`), so the step shows
   no headline, no "Show N full-screen" chip and no false-info chips
   (`NightScreen.kt:836-932` is entirely skipped). Even with five `Correct` tokens on the
   seat, nothing counts them.

4. **P1 · The step appears on every night after the first.**
   `NightOrder.build` (`NightOrder.kt:142-178`) emits a step for every in-play character on
   the order list with no per-night condition. The Juggler should appear on exactly one
   night. Worse, the dawn guard (`GameShell.kt:147-161`) blocks the phase advance until the
   storyteller ticks it — training them to tick a step they should be skipping.

5. **P1 · "Their 1st day" is not tracked.**
   Nothing records when a seat acquired the Juggler ability. Wiki example 2 (a Savant
   Pit-Hagged into the Juggler on night 4) is unrunnable: the app cannot know that day 4 is
   this Juggler's first day, so it cannot know that night 5 is the reveal night.

6. **P1 · Misregistration scoring is unassisted.**
   Guessing "Imp" on the Recluse, or "Chef" on the Spy, may legitimately be scored correct
   — and guessing the Drunk's *believed* character is wrong while guessing "Drunk" is
   right. `InfoCalc.misregistrations` (`InfoCalc.kt:121-130`) already knows who the Spy and
   Recluse are, and `Player.shownCharacterId` (`GameState.kt:23`) already knows what the
   Drunk/Marionette/Lunatic think they are. None of it is offered.

7. **P1 · The Cannibal jinx is inert.**
   The jinx text is in the data (`night_and_jinxes.json:14-17`) and will appear in the
   "Jinxes in play" dialog (`GameExtras.kt:200-232`), but nothing implements it: no
   Cannibal step is created, no number is computed for the Cannibal, and the dead Juggler's
   own step is actively mislabelled **"All holders are dead — usually skip"**
   (`NightScreen.kt:751-757`) on the very night the jinx says someone must learn the number.
   **Repro:** Juggler juggles on day 1 and is executed on day 1; night 2 shows a dead
   Juggler step telling you to skip, and the Cannibal step says nothing about the number.

8. **P1 · The drunk/poisoned rule is inverted by omission.**
   The official rule is that impairment **at guess time** does not spoil the info if the
   Juggler is healthy **at trigger time**. `night_guide.json:455-467` says only "If the
   Juggler is drunk or poisoned, or the Vortox is in play, give a false number", with no
   statement of *when* impairment is measured — and since nothing is computed, no
   impairment check runs at all.

9. **P2 · Vortox obligation not distinguished, and no plausible-lie range.**
   Under a Vortox the number **must** be false; the app says nothing, and even if the
   generic false-chip row fired it would offer `0..4` (`NightScreen.kt:914-921`) rather
   than `0..(number of guesses made)`.

10. **P2 · `Correct` tokens are never cleared.**
    "Remove the CORRECT reminders… when convenient" — `("juggler","Correct")` is in neither
    `EXPIRES_AT_DAWN` (`GameActions.kt:218-225`) nor `EXPIRES_AT_DUSK`
    (`GameActions.kt:231-242`).

11. **P2 · No privacy affordance for guess entry.**
    The storyteller stands **inside the circle holding the Grimoire** while the Juggler
    talks, and the rules say the players must not see how many tokens go down. The app has
    a `PrivacyCover` (`GameShell.kt:186, 344-346`) but the natural entry surface would
    display a running score in plain sight.

12. **P2 · No record of *claimed* Jugglers.**
    The wiki tells the storyteller to fake-move tokens for an evil player bluffing Juggler.
    Nowhere to note which seats juggled falsely.

13. **P3 · `characters.json:838-851` lists `reminders: ["Correct"]` once.**
    Whether or not the copy count is fixed at the UI level, the data should express that
    the Juggler needs five.

## Proposed behaviour (spec)

### Day side — the guess recorder

Reuse the shared `DayAct` model from `docs/audit/characters/artist.md`:

```kotlin
@Serializable
enum class GuessVerdict { CORRECT, WRONG, STORYTELLER_CHOICE }

@Serializable
data class DayGuess(
    val targetPlayerId: Long,
    val characterId: String,
    val verdict: GuessVerdict,
    /** Why the engine could not decide; shown to the storyteller. */
    val note: String = "",
)
// DayAct(kind = JUGGLE, day, playerId = jugglerSeat, genuine, guesses = List<DayGuess>)
```

- Entry surface: a **"Juggler is juggling"** button on the Day tab (and on the Juggler's
  seat sheet), present whenever an alive Juggler exists who has no `JUGGLE` `DayAct` yet.
- The recorder is a repeat of *[player chip row] × [character search]* → **Add guess**,
  capped at 5. Duplicate players and duplicate characters are allowed.
- **Privacy**: the running score is hidden by default behind a "reveal score" toggle; each
  added guess shows only "guess 3 of 5 recorded". The storyteller is standing in the
  circle.
- Auto-verdict per guess, evaluated at record time and re-evaluated when opened:
  - `CORRECT` when `state.player(targetPlayerId)?.characterId == characterId`.
  - `STORYTELLER_CHOICE` with a note when any of:
    - target is `recluse` and `characterId` is a Minion or Demon on the script
      ("Recluse may register as evil");
    - target is `spy` and `characterId` is a Townsfolk or Outsider
      ("Spy may register as good");
    - target's `shownCharacterId == characterId` while `characterId != target.characterId`
      ("they *think* they are the Chef; they are actually the Drunk — normally wrong");
    - target is a Traveller whose alignment has been flipped, or any other case where the
      engine's registration model is unsure.
  - `WRONG` otherwise.
- Each guess row is a three-state toggle so the storyteller can override any verdict.
- On save: write the `DayAct`, and place `count(CORRECT)` copies of
  `PlacedReminder("juggler", "Correct")` on the Juggler's seat via `addReminder` (never
  `placeExclusiveReminder`).
- A **"someone else claimed Juggler"** button records `DayAct(genuine = false)` with the
  fake guesses, places no tokens, and reminds the storyteller to mime moving tokens.

### Night side

- **when:** `other` nights only.
- **wake condition — all must hold:**
  - a `DayAct(kind = JUGGLE, genuine = true, playerId = holder)` exists for day `cycle - 1`;
  - the holder is **alive** *or* the Cannibal jinx applies (below);
  - the ability has not already been revealed (`revealed` flag on the `DayAct`).
  If they do not hold, the step is emitted **collapsed** with the reason ("Marta has not
  juggled" / "already revealed on night 2") and auto-ticked so it never blocks the dawn
  guard.
- **targets:** none.
- **information:** `Answer.Count(n = guesses.count { it.verdict == CORRECT },
  min = 0, max = guesses.size)`; `detail` lists every guess with its verdict, so the
  storyteller can re-check a `STORYTELLER_CHOICE` before showing the number.
- **impairment is measured at trigger time, not guess time.** Explicit spec:
  `obligation(state, lookup, holder)` is evaluated **now**; if the holder was impaired
  when guessing but is healthy now, the obligation is `TRUTH` and the true number is
  shown. The panel should say so out loud when the two differ:
  > "Marta was poisoned when she juggled but is healthy now — give the **true** number."
  This requires storing `impairedAtGuess: Boolean` on the `DayAct`.
- `MUST_LIE` (alive Vortox) → false chips drawn from `0..guesses.size` minus the truth,
  nearest-first; the truthful chip demoted.
- **immediate effects on completion:** remove all `juggler:"Correct"` tokens and mark the
  `DayAct` revealed.
- **expiry:** add `("juggler", "Correct")` to `EXPIRES_AT_DAWN` as a backstop.
- **visibility:** nothing to the Demon/Minions/Lunatic.

### Cannibal jinx

When a `DayAct(kind = JUGGLE, genuine = true)` exists for day D and the Juggler
**died by execution on day D** (`DeathRecord.cause == EXECUTION && day == D`,
`GameState.kt:77-90`), then on night D+1:

- suppress the Juggler's own step (they are dead);
- annotate the **Cannibal's** step with "Juggler jinx — the living Cannibal learns how
  many of Marta's guesses were correct: **2**", with the number card, and apply the
  Cannibal's own poisoned/healthy state to the obligation (a Cannibal poisoned by having
  eaten an evil player gets false info).
- If no Cannibal is in play or the Cannibal is dead, no one learns the number.

### UI text the step should display

Day tab:
> **Juggler — Marta is juggling.** Step into the circle. Record up to 5 guesses.
> Players must not see how many are correct.
> `[ + guess: (player) is the (character) ]` … `3 of 5 recorded` `[ reveal score ]` `[ Done ]`

Night step:
> **Juggler — show the number of correct guesses, then remove the Correct tokens.**
> **2** of 3 guesses correct.
> ✓ Alex = Town Crier · ✗ Mia = No Dashii (she is the Vigormortis) · ✓ Julian = Sage
> `[ Show 2 full-screen ]`
> `! Marta was poisoned when she guessed but is healthy now — give the TRUE number.`

### Data changes

- `characters.json:838-851` — `"reminders": ["Correct","Correct","Correct","Correct","Correct"]`
  (five copies), so the tray's `availableCopies` heuristic (`NightScreen.kt:319-321`) does
  the right thing even before it is replaced. Same fix pattern applies to the
  Mathematician's `Abnormal`.
- `night_guide.json:455-467` — add the impairment-timing rule and split must-lie/may-lie:
  "…If the Juggler was drunk or poisoned when they guessed but is sober and healthy now,
  still give the **true** number. If the Vortox is in play the number **must** be false."
- `night_and_jinxes.json:14-17` — no text change; the jinx needs an implementation, not new
  data.

### Cross-cutting fix this character requires

`NightScreen`'s reminder placement (`NightScreen.kt:319-339`) should not infer
exclusivity from the number of duplicate labels in a JSON array. Move the decision into
the engine: a `ReminderSpec(sourceId, label, copies: Int, exclusive: Boolean)` table, with
`exclusive = true` meaning "one in the whole grimoire, it moves" (Poisoner's Poisoned,
Monk's Safe) and `copies = N` meaning "up to N, they stack" (Juggler's Correct,
Mathematician's Abnormal, Lunatic's Attack 1/2/3).

## Tests to add

1. **Scoring counts exact character matches.**
   *Given* guesses [(Alex, towncrier), (Mia, nodashii), (Julian, sage)] and a grimoire in
   which Alex is the Town Crier and Julian is the Sage, *then* the score is 2 and the
   per-guess verdicts are CORRECT/WRONG/CORRECT. (Wiki example 1.)

2. **Duplicate players and characters are legal.**
   *Given* guesses [(Ben, pithag), (Ben, witch), (Amy, pithag)] with Ben the Witch,
   *then* the recorder accepts all three and the score is 1. (Wiki example 2.)

3. **Cap at five.**
   *Given* five recorded guesses, *then* a sixth is refused.

4. **Recluse guess is storyteller's choice.**
   *Given* a guess of (Priya, imp) where Priya is the `recluse`, *then* the verdict is
   `STORYTELLER_CHOICE` with a Recluse note, and the score changes by 1 when overridden to
   CORRECT.

5. **Spy guess is storyteller's choice.**
   *Given* a guess of (Ari, chef) where Ari is the `spy`, *then* the verdict is
   `STORYTELLER_CHOICE`.

6. **Drunk's believed character is wrong by default.**
   *Given* a guess of (Kai, empath) where Kai is the `drunk` with
   `shownCharacterId = "empath"`, *then* the verdict is `STORYTELLER_CHOICE` with a note;
   *and* a guess of (Kai, drunk) is `CORRECT`.

7. **Five `Correct` tokens stack (currently fails).**
   *Given* five correct guesses, *when* the day act is saved,
   *then* the Juggler's seat holds five `juggler:"Correct"` reminders.

8. **Step only appears on the reveal night (currently fails).**
   *Given* a `JUGGLE` act on day 1, *when* the night-2 sheet is built, *then* a live
   `juggler` step exists; *when* the night-3 sheet is built, *then* the `juggler` step is
   absent or auto-ticked/collapsed.

9. **No juggling → no step (currently fails).**
   *Given* a Juggler in play who never juggled, *then* no live `juggler` step is ever
   emitted and the dawn guard never lists it.

10. **First day is tracked for a mid-game Juggler (currently fails).**
    *Given* a seat that becomes `juggler` during night 4 and juggles on day 4,
    *then* the reveal step appears on night 5, not night 2.

11. **Impaired at guess, healthy at reveal → true info (currently fails).**
    *Given* `impairedAtGuess = true` and no current impairment and no Vortox,
    *then* `obligation == TRUTH` and the panel carries the "give the TRUE number" note.

12. **Healthy at guess, poisoned at reveal → may lie.**
    *Given* the Juggler poisoned on the reveal night, *then* `obligation == MAY_LIE`.

13. **Vortox forces a lie and bounds it.**
    *Given* an alive Vortox and 3 guesses scoring 2, *then* `obligation == MUST_LIE` and
    the suggested false numbers are a subset of `{0,1,3}`.

14. **Cannibal jinx (currently fails).**
    *Given* the Juggler juggled on day 1 and was executed on day 1, and a living Cannibal
    is in play, *when* the night-2 sheet is built, *then* the Cannibal's step carries the
    Juggler number and the Juggler's own step is suppressed.
    *And*: with no living Cannibal, no step carries the number.

15. **Tokens cleared after the reveal.**
    *Given* the Juggler step is completed, *then* no `juggler:"Correct"` reminders remain.
