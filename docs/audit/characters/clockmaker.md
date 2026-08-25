# Clockmaker (clockmaker) — Sects & Violets Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Clockmaker> (fetched 2026-08-25);
Vortox rule from <https://wiki.bloodontheclocktower.com/Vortox>.

Current ability text:

> "You start knowing how many steps from the Demon to its nearest Minion."

**How to Run (verbatim):**

> "During the first night, wake the Clockmaker. Show fingers (*1, 2, etc.*) equaling
> the distance in players from the Demon to the nearest Minion, starting with the
> player neighboring the Demon closer to that Minion. Put the Clockmaker to sleep."

**Examples (verbatim):**

> "The Fang Gu is sitting next to the Pit-Hag. During the first night, the Clockmaker
> learns a "1"."
>
> "Clockwise from the No Dashii sits the Dreamer, the Snake Charmer, then the Evil
> Twin. Counterclockwise from the No Dashii sits the Mutant, the Sweetheart, the
> Philosopher, the Sage, then the Witch. Because the Witch is five steps away from
> the Demon, and the Evil Twin is three steps away from the Demon, the Clockmaker
> learns a "3" during the first night."
>
> "The Fang Gu neighbours two Travellers, one good and one evil. Neighboring one of
> these Travellers is a Cerenovus. During the first night, the Clockmaker learns a
> "2", because evil Travellers are not Minions."

**Jinx (verbatim):**

> "The Summoner registers as the Demon to the Clockmaker."

**Storyteller-relevant timing / edge cases distilled from the above**

- **First night only.** "You start knowing" — one number, once. (A player who *becomes*
  the Clockmaker later — Pit-Hag, Philosopher, Cannibal eating an executed Clockmaker —
  learns it on the night they gain the ability; the app has no path for that, see D8.)
- **Distance is measured in seats, the shorter way round.** Adjacent = 1. The hand
  signal starts at "1"; **0 is never a legal answer** (the wiki writes "*1, 2, etc.*",
  unlike the Oracle/Mathematician/Juggler which write "*0, 1, 2, etc.*").
- **Travellers occupy seats but are never Minions** (example 3): the Fang Gu's two
  Traveller neighbours are counted as steps, and the evil Traveller does not stop the
  count — the Cerenovus two seats away is the answer.
- **Nearest Minion, either direction** (example 2): min over all Minions of the circular
  seat distance to the Demon.
- **Summoner jinx**: on a Summoner script there is no Demon on night 1 (the Summoner
  creates one on night 3). The Summoner *is* the Demon for this measurement, so the
  Clockmaker learns the distance from the Summoner's seat to the nearest **other** Minion.
- **Misregistration**: a Recluse may register as a Minion (a nearer "Minion" → smaller
  number); a Spy may register as a Townsfolk/Outsider (the real nearest Minion drops out
  → larger number). The storyteller chooses; both are legal answers.
- **Vortox**: with an alive Vortox the number **must** be false ("Even if they are drunk
  or poisoned, it must be false"), and it should still be a *plausible* number — i.e. in
  the range 1..⌊seats/2⌋.
- **Drunk / poisoned Clockmaker**: the storyteller *may* give a false number.

## What the app does today

| path | what it holds |
|---|---|
| `engine/src/main/resources/botc/data/characters.json:799-810` | Ability text matches the wiki. `firstNightReminder`: "Show the hand signal for the number (1, 2, 3, etc.) of places from Demon to closest Minion." `otherNightReminder` empty. `reminders: []`. Correct. |
| `engine/src/main/resources/botc/data/night_and_jinxes.json:349` | First-night order index 54, between `grandmother` and `dreamer`. Matches the official first-night order. Absent from `otherNight`. Correct. |
| `engine/src/main/resources/botc/data/night_guide.json:400-412` | Guide prose + one `message` show card ("Steps to nearest Minion"). Text says "counting seats in the shorter direction… If the Clockmaker is drunk or poisoned, or the Vortox is in play, give a false number instead." |
| `engine/src/main/kotlin/com/clocktower/engine/InfoCalc.kt:30` | `clockmaker` is in `supports()`. |
| `engine/src/main/kotlin/com/clocktower/engine/InfoCalc.kt:218-241` | The calculation. |
| `engine/src/main/kotlin/com/clocktower/engine/InfoCalc.kt:158-166` | `commonCaveats` adds impairment notes + "VORTOX in play — Townsfolk info must be FALSE." |
| `app/src/main/java/com/clocktower/grimoire/ui/screens/NightScreen.kt:836-932` | Renders headline/detail/caveats, a "Show N full-screen" chip when the headline starts with a digit ≤ 9, and false-info chips 0..4 when a caveat mentions POISONED/DRUNK/VORTOX/No Dashii. |
| `engine/src/test/kotlin/com/clocktower/engine/InfoCalcTest.kt:61-66` | Existing test: "clockmaker measures demon to nearest minion". |

The calculation itself (`InfoCalc.kt:218-241`):

```kotlin
val demonIdx = seats.indexOfFirst { ctx.character(it)?.team == Team.DEMON }
if (demonIdx < 0) return InfoResult("No Demon in the grimoire")
for ((i, p) in seats.withIndex()) {
    if (ctx.character(p)?.team != Team.MINION) continue
    val d = kotlin.math.abs(i - demonIdx)
    val steps = minOf(d, seats.size - d)
    …
}
```

**What already works — one line each:**

- Seat-distance arithmetic, shorter direction, is correct.
- Travellers are counted as seats but excluded as Minions (`team == TRAVELLER ≠ MINION`),
  matching wiki example 3.
- Dead seats still occupy positions (irrelevant on night 1, correct if re-run).
- A Marionette counts as the nearest Minion (it is `team: minion` and neighbours the
  Demon), which is the correct official outcome.
- Night order position and first-night-only placement are correct.

**Storyteller's experience today:** you expand the Clockmaker row, read a headline like
"2 steps from Demon to nearest Minion / Nearest Minion: Dax", tap "Show 2 full-screen",
and hand the phone over. If a Recluse or Spy is in play you get a one-line note but no
alternative number. If the Clockmaker is poisoned or a Vortox is out you get red text
and a row of chips 0–4 — including 0, which is not a legal Clockmaker answer.

## Defects and gaps

1. **P0 · Summoner jinx not implemented and not even listed.**
   Rules: "The Summoner registers as the Demon to the Clockmaker." App: `demonIdx` is
   the first seat with `team == DEMON` (`InfoCalc.kt:220`). On a Summoner script night 1
   there is no Demon seat, so the step reads **"No Demon in the grimoire"** and the
   storyteller is left with nothing. The jinx is also missing from the jinx table
   (`night_and_jinxes.json` has 58 jinxes; `grep summoner` returns Marionette, Alchemist,
   Poisoner, Courtier — no Clockmaker), so the "Jinxes in play" dialog
   (`GameExtras.kt:200-232`) will not warn either. **Repro:** deal a script with Summoner
   + Clockmaker, night 1, expand Clockmaker.

2. **P0 · Only the first Demon seat is measured from.**
   `indexOfFirst` (`InfoCalc.kt:220`) silently picks one Demon. With multiple Demons
   (Legion, Riot, Kazali-created, Lord of Typhon, a Fang Gu mid-jump) the number is
   arbitrary and unexplained. With **Lil' Monsta** — where the Demon is a *token* held by
   a Minion, not a seat — it returns "No Demon in the grimoire". **Repro:** assign two
   seats a Demon character, or run Lil' Monsta, and expand the Clockmaker step.

3. **P1 · Misregistration produces prose, never a number.**
   `misregistrations(ctx, seats)` (`InfoCalc.kt:121-130`, called at `InfoCalc.kt:233,238`)
   emits "Priya is the Recluse — may register as evil / a Minion or Demon." The
   storyteller must then recount seats by hand to know that the Recluse answer would be
   "1" instead of "3". The app has every input needed to compute both.

4. **P1 · The false-number chips offer an illegal answer and too narrow a range.**
   `NightScreen.kt:914-921` offers `0..4` minus the true value. **0 is not a legal
   Clockmaker signal** (the hand signal starts at 1) and is an immediate tell; and in a
   15-player game the legal range runs to 7, so plausible lies are missing. **Repro:**
   poison the Clockmaker, expand the step, look at the chip row.

5. **P1 · "May lie" and "must lie" are the same red text.**
   `commonCaveats` (`InfoCalc.kt:158-166`) appends the Vortox note alongside impairment
   notes, and `NightScreen.kt:903-906` lumps them into one boolean. Under a Vortox the
   true number **must not** be shown, yet the "Show 2 full-screen" chip
   (`NightScreen.kt:890-895`) remains, first, and visually identical. **Repro:** Vortox
   + Clockmaker night 1 — the truthful chip is the most prominent control.

6. **P1 · The Vortox caveat is suppressed for a Drunk Clockmaker.**
   `commonCaveats` gates the Vortox note on `holderTeam == TOWNSFOLK || holderTeam == null`
   (`InfoCalc.kt:160-163`). For a Drunk who *believes* they are the Clockmaker, the night
   step id is `nightRoleId == "clockmaker"` (`GameState.kt:39-44`) but the holder's
   `characterId` is `"drunk"`, whose team is OUTSIDER — so the Vortox line is dropped.
   Harmless here (they are impaired anyway) but the same gate mis-handles a Philosopher
   or Cannibal who has *gained* the Clockmaker ability: `holderTeam` is TOWNSFOLK for the
   Philosopher so it happens to work, but the rule should key on the *step's* character,
   not the holder's team.

7. **P1 · No plausibility guidance for the lie.**
   The storyteller has to know unaided that the answer is bounded by 1..⌊seats/2⌋ and
   that repeating the true number defeats the Vortox. Nothing states the range.

8. **P1 · A Clockmaker created after night 1 can never be run.**
   `NightOrder.otherNight` (`NightOrder.kt:37-38`, order list `night_and_jinxes.json:399-468`)
   has no `clockmaker` entry, and there is no "re-run a first-night step" action anywhere.
   Pit-Hag → Clockmaker, Philosopher choosing Clockmaker, or Cannibal eating an executed
   Clockmaker all leave the storyteller computing seats by hand. (Same class of bug as
   the playtest's "when Professor brings someone back… rerun the 1st night for that".)

9. **P2 · The detail line names one Minion and hides the tie.**
   `InfoCalc.kt:237` prints "Nearest Minion: Dax". When two Minions are equidistant in
   opposite directions the storyteller cannot see it, and cannot see the runner-up
   distance that a Spy/Recluse read would produce.

10. **P2 · No "who is the Demon / who are the Minions" recap in the step.**
    Every other seat-geometry role (Shugenja, Chef, Empath) has the same problem, but the
    Clockmaker is the one where the storyteller most wants to sanity-check the count
    against the circle before showing a number.

11. **P3 · `best!!` non-null assertion.** `InfoCalc.kt:228` — `steps < best!!` inside a
    null-guard; safe but should be a local `val`.

## Proposed behaviour (spec)

- **when:** `first` only, **plus** on the night a seat *becomes* a Clockmaker
  (`characterId` changed to `clockmaker` since the last dawn) — surfaced as an extra
  night row titled "Clockmaker (new — run first-night info)".
- **wake condition:** holder is **alive**. Not gated on anything else; there is no
  once-per-game token.
- **targets:** none.
- **immediate effects:** none. No tokens, no status effects, no kills.
- **deferred effects:** none.
- **expiry:** no tokens; nothing to expire.
- **information (structured):**

  ```
  demonSeats  = seats where team == DEMON
              ∪ seats where characterId == "summoner"        // jinx
  minionSeats = seats where team == MINION, excluding any seat already in demonSeats
  answer      = min over d ∈ demonSeats, m ∈ minionSeats of circularDistance(d, m)
  circularDistance(i, j) = min(|i-j|, seats.size - |i-j|)     // all seats count,
                                                              // incl. dead & Travellers
  ```
  Return `Answer.Count(n, min = 1, max = seats.size / 2)`.

  Degenerate cases, each with its own storyteller-voice message rather than a number:
  - no Demon **and** no Summoner → "No Demon on the grimoire — assign one before running this step."
  - Demon but no Minion → "No Minion in play — the Clockmaker cannot be given a real number; pick any plausible number." (Legion/no-Minion scripts.)
  - more than one Demon → still return the minimum, but add caveat
    "N Demons in play (Ada, Bo) — the number below is the smallest distance from any of
    them; you may use any Demon you like."
  - Lil' Monsta in play → caveat "Lil' Monsta has no seat — measure from whoever is
    holding the token (Ada tonight)." Uncertain: the wiki does not state a Lil' Monsta
    ruling for the Clockmaker; surface it as a storyteller decision rather than guessing.

- **misregistration handling (must be numeric, not prose):** produce a list of
  *alternative* answers with their justification, each a one-tap chip:
  - for each Recluse `r`: `alt = min(answer, min over d of circularDistance(d, r))` →
    "If the Recluse (Priya) registers as a Minion: **1**"
  - for each Spy `s` that is currently the/a nearest Minion: recompute with `s` removed →
    "If the Spy (Ari) registers as good: **4**"
  - Summoner scripts: the jinx answer is the *primary* answer, not an alternative.

- **impaired / false alternative:** `InfoCalc.obligation(...)` (see `artist.md`) returns
  `TRUTH` / `MAY_LIE` / `MUST_LIE`.
  - `MUST_LIE`: hide the true-number chip behind a "show the true number anyway" text
    button; present the false chips as the primary row, drawn from
    `(1..seats.size/2) - trueAnswer`, sorted by closeness to the true answer (the most
    believable lies first).
  - `MAY_LIE`: show both rows, true first.
  - Never offer **0**.

- **visibility:** nothing shown to Demon/Minions/Lunatic.
- **day-time inputs:** none.
- **interactions/jinxes to handle explicitly:**
  - **Summoner** — as above (add the jinx row to the data).
  - **Recluse / Spy** — numeric alternatives.
  - **Vortox** — mandatory lie.
  - **Marionette** — is a Minion; usually yields "1". Add a caveat "The Marionette
    (Kai) neighbours the Demon — the true answer is 1" so the storyteller consciously
    decides whether that is the read they want to give.
  - **Philosopher / Pit-Hag / Cannibal** — the "new Clockmaker" night row.

### UI text the step should display

> **Clockmaker — show the number of seats from the Demon to its nearest Minion.**
> Count the shorter way round the circle. Travellers count as seats but are never Minions.
> The answer is never 0.
>
> **3** · Demon Bo (seat 4) → nearest Minion Dax (seat 7), anticlockwise
> Other Minions: Kai (seat 11), 5 steps.
> `[ Show 3 full-screen ]`
> `! Priya is the Recluse — may register as a Minion. `[ Show 1 instead ]`

### Data changes

- `night_and_jinxes.json` — add:
  ```json
  { "id1": "summoner", "id2": "clockmaker",
    "reason": "The Summoner registers as the Demon to the Clockmaker." }
  ```
- `night_guide.json:400-412` — rewrite the instruction to distinguish obligations:
  "…If the Vortox is in play the number **must** be false. If the Clockmaker is drunk or
  poisoned you **may** give a false number. Never show 0."
  Also add a second show card for the alternatives is unnecessary — the number card is
  generic.
- `characters.json` — no change.

## Tests to add

1. **Adjacent Demon and Minion → 1.**
   *Given* seats `[fanggu, pithag, …]`, *when* clockmaker info is computed, *then*
   headline is "1 step".

2. **Nearest of two Minions, opposite directions (wiki example 2).**
   *Given* seats laid out as the wiki's No Dashii example (Evil Twin 3 clockwise, Witch
   5 anticlockwise), *then* the answer is 3 and the detail names the Evil Twin.

3. **Travellers count as seats but not as Minions (wiki example 3 — currently passes,
   lock it in).**
   *Given* `[fanggu, traveller(good), traveller(evil), cerenovus, …]`, *then* the answer
   is 2.

4. **Summoner registers as the Demon (currently fails).**
   *Given* a night-1 grimoire with a `summoner` and a `poisoner` two seats away and **no**
   Demon, *then* the answer is 2 — not "No Demon in the grimoire".

5. **Summoner + a real Demon.** *Given* both, *then* the answer is the minimum over
   both origins, and a caveat names the ambiguity.

6. **Recluse alternative is offered as a number (currently fails).**
   *Given* a Demon 4 seats from the nearest Minion and a Recluse adjacent to the Demon,
   *then* the result carries an alternative answer of 1 attributed to the Recluse.

7. **Spy alternative is offered as a number (currently fails).**
   *Given* the Spy as the unique nearest Minion at distance 1 and another Minion at
   distance 3, *then* an alternative answer of 3 is offered attributed to the Spy.

8. **0 is never offered.**
   *Given* a poisoned Clockmaker, *then* the generated false-answer set excludes 0 and is
   a subset of `1..seats.size/2`.

9. **Vortox obligation (currently fails).**
   *Given* an alive Vortox, *then* `obligation` is `MUST_LIE` and the true value is not
   the primary suggestion. *Given* the Vortox dead, *then* `TRUTH`.

10. **Multiple Demons are reported.**
    *Given* two seats with Demon characters, *then* the caveats name both and the answer
    is the minimum distance from either.

11. **No Minion in play.**
    *Given* a Demon and zero Minions, *then* the result is the explanatory message, not a
    number, and no "Show N" chip is generated.

12. **New Clockmaker mid-game gets a night row (currently fails).**
    *Given* a seat whose character becomes `clockmaker` during night 3,
    *when* the night-3 sheet is built, *then* it contains a `clockmaker` step.
