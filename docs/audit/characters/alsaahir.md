# Alsaahir (alsaahir) — Experimental Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Alsaahir> (fetched via
`action=parse&prop=wikitext`, 2026-08-25).

Current ability text (verbatim):

> "Each day, if you publicly guess which players are Minion(s) and which are Demon(s), good wins."

**Summary bullets (verbatim) — these are the resolution rules:**

- "The Alsaahir's guesses need to be public, and they need to be during the day. They don't have to guess every day."
- "Other players may pretend to be the Alsaahir and make a guess. Like the Juggler or the Gossip, the Storyteller will briefly pretend that player is the Alsaahir."
- "If the Alsaahir guesses the Demon player as the Demon, and the Minion players as Minions, the game ends immediately. The Alsaahir must guess all Demon and Minion players."
- "The Alsaahir doesn't need to guess specific minion characters, nor specific Demon characters."
- "If there is more than one Demon in play, all Demons must be guessed, including dead Demons."
- "If a player is a Minion and Demon, such as Legion, the Alsaahir must guess this player as a Demon."
- "Once a guess is made, the Alsaahir cannot change their mind later that day and guess again."
- "The Alsaahir needs to guess Minions and Demons, **even if they are good**, but need not guess which Travellers are evil."
- "If the evil team has changed during the game, the Alsaahir must guess the current evil team, not the starting evil team."

Two of those bullets are easy to implement wrong: the test is on **character
type** (Minion / Demon), not on alignment — a Minion who has turned good still
has to be named as a Minion — and **Travellers are excluded entirely**.

**How to Run (verbatim):**

> Each day, once only, if the Alsaahir declares that they wish to use their ability, prompt them to guess which player is the Demon, and which player(s) are Minions. If incorrect, nothing happens and the game continues. If correct, declare that good wins.
>
> **Optional rule**: like the Juggler and the Gossip, the Storyteller may limit the number of players that make an Alsaahir guess to three players per day. Only use this optional rule if so many players are bluffing as the Alsaahir that the game slows down and ceases to be fun.

**Examples (verbatim):**

1. "The Alsaahir guesses four good players. Nothing happens."
2. "The Alsaahir guesses that Doug is the Demon, and Ben and Sarah are Minions. Doug is the Demon, and Ben and Sarah are Minions. Good wins immediately."
3. "The drunk Alsaahir guesses that Doug is the Demon, and Ben and Sarah are Minions. Doug is the Demon, and Ben and Sarah are Minions. Nothing happens and the game continues. The next day, the sober Alsaahir guesses that Ben is the Demon and Doug and Sarah are Minions. Nothing happens and the game continues."

A drunk/poisoned Alsaahir's correct guess therefore **does nothing at all** —
and the ST must not react in any way that reveals it was correct.

**Jinx (verbatim):**

| With | Text |
|---|---|
| Vizier | "The Storyteller doesn't declare the Vizier is in play." |

(The Vizier is normally announced publicly at the start of the game; with an
Alsaahir on the script that announcement is suppressed, because it would hand
the Alsaahir a free Minion.)

**Night order:** the Alsaahir never wakes. Correctly absent from both order
lists in `night_and_jinxes.json`.

## What the app does today

Data:
- `characters.json:1223` — `ability`, `team`, `edition` all match the current wiki text. `setup:false`, both night reminders `""`, `reminders: []`, `remindersGlobal: []`. **Correct as far as it goes.**
- `night_and_jinxes.json` — correctly absent from `firstNight`/`otherNight`. **But** the Alsaahir–Vizier jinx is **not present** in the `jinxes` array (a grep of the file for `alsaahir` finds nothing).
- `night_guide.json` — **no entry.** `NightGuide.forStep("alsaahir", …)` returns `null` (`NightGuide.kt:56`).

Code: **no `alsaahir` string exists anywhere in `engine/src` or `app/src`.**

Storyteller's actual experience: the Alsaahir appears on the Script tab and in
the character picker; nothing else in the app knows it exists. During the day
the ST hears a public guess, works out on their own whether it names every
Demon and every Minion (including dead ones, including a Minion who has turned
good, excluding Travellers), remembers whether this player has already guessed
today, remembers whether the Alsaahir is drunk or poisoned, and — if correct —
uses the menu item "Declare good victory" (`GameShell.kt:259`). Nothing is
recorded, so the day's guesses are gone the moment the conversation moves on.
This is precisely the "Gossip was awful, make it easy to write down all the
gossips even if Gossip isn't in play" complaint, one character over.

`WinCheck.check` (`WinCheck.kt:18`) has no Alsaahir branch and no concept of a
day-time win trigger.

## Defects and gaps

1. **P0 · No way to record or resolve a guess; the ability is 100% manual.**
   Rules: a correct public guess ends the game immediately. App: no UI, no
   state, no check. Repro: play a game with an Alsaahir → Day tab shows only
   nominations. The single most powerful good ability on the script is invisible
   to the app.
2. **P0 · The correctness test is genuinely error-prone by hand, and the app
   already holds the answer.** The ST must simultaneously apply: all Demons
   including dead ones; all Minions including dead ones; Minions/Demons who have
   turned **good** still count; a Legion-style Minion-and-Demon counts as a
   **Demon**; Travellers are excluded; the **current** evil team, not the
   starting one. Every one of those facts is already in `GameState.players`
   (`characterId` → `Character.team`). Nothing computes it.
3. **P1 · "Once only per day" is not tracked.** Rules: "Once a guess is made,
   the Alsaahir cannot change their mind later that day and guess again." The
   app tracks one-per-day only for nominations
   (`GameActions.hasNominatedToday`, `GameActions.kt:285`). Repro: the Alsaahir
   guesses, gets told "no", and thirty seconds later guesses a different
   combination — the app has no basis to stop them.
4. **P1 · Bluffers' guesses are not recordable.** Rules: "Other players may
   pretend to be the Alsaahir … the Storyteller will briefly pretend that
   player is the Alsaahir." A bluffer's guess must be adjudicated and answered
   the same way (so the real Alsaahir is not outed by the ST's behaviour), and
   the optional 3-guessers-per-day cap needs a counter. The app has neither.
   Recording them also matters for the good team's own deduction — that is the
   user's "write down all the gossips even if Gossip isn't in play" point.
5. **P1 · Impairment is not surfaced at guess time.** A drunk or poisoned
   Alsaahir's correct guess does nothing (wiki Example 3), and the ST must give
   the *same* outward response as for a wrong guess.
   `StatusEffects.isImpaired` (`StatusEffects.kt:36`) answers this instantly and
   is never consulted.
6. **P1 · The Alsaahir–Vizier jinx is missing from `night_and_jinxes.json`.**
   With both on a script, the ST must **not** publicly declare the Vizier.
   Repro: menu → "Jinxes in play" with Alsaahir + Vizier → nothing listed.
7. **P2 · No day-start reminder.** "Each day, once only, if the Alsaahir
   declares that they wish to use their ability, prompt them." Nothing prompts.
8. **P2 · No history view.** Over a 5-day game the Alsaahir makes up to 5
   guesses and bluffers make more; the good team reasons from that history and
   the ST is repeatedly asked "what did they guess on day 2?". The game log
   (`GameExtras.kt:45`) records only deaths and nominations.
9. **P2 · No guidance in the app about *how many* Minions to expect.** The
   Tips section calls this out ("If you normally play in a group of 12, you
   might forget to guess a 3rd minion in games of 13 or more"), and the app
   already knows the adjusted distribution (`Setup.adjustedDistribution`,
   `Setup.kt:252`). The guess panel should show
   "This game has 1 Demon and 2 Minions" **to the storyteller** as a sanity
   check (never to the players).
10. **P3 · No `night_guide.json` entry.** Characters that never wake still
    deserve a day-time run-book entry, and the guide schema has no place for
    one (`NightGuideEntry` is `first`/`other` only, `NightGuide.kt:37`).

## Proposed behaviour (spec)

### Engine data (new; shared with Amnesiac, Gossip, Juggler, Savant, Artist, Slayer, Alchemist-Spy)

```kotlin
@Serializable
data class DayStatement(
    val day: Int,
    /** Seat that spoke — the real Alsaahir or a bluffer. */
    val speakerId: Long,
    /** Character ability being claimed, e.g. "alsaahir". */
    val characterId: String,
    /** Free-text record, always kept for the log. */
    val text: String = "",
    /** Structured payload for abilities the engine can resolve. */
    val demonIds: List<Long> = emptyList(),
    val minionIds: List<Long> = emptyList(),
    /** Engine verdict at the time it was recorded. */
    val correct: Boolean? = null,
    val tookEffect: Boolean = false,
)
```
plus `val dayStatements: List<DayStatement> = emptyList()` on `GameState`.

### Resolution

```kotlin
fun alsaahirCorrect(state, lookup, demonIds, minionIds): Boolean {
    val residents = state.players.filter { !it.isTraveller }   // Travellers excluded
    val trueDemons  = residents.filter { lookup(it.characterId)?.team == Team.DEMON }.map { it.id }.toSet()
    val trueMinions = residents.filter { lookup(it.characterId)?.team == Team.MINION }.map { it.id }.toSet()
    // A player who is both (Legion) must be guessed as a Demon.
    val minionsOnly = trueMinions - trueDemons
    return demonIds.toSet() == trueDemons && minionIds.toSet() == minionsOnly
}
```
Notes an implementer must not "simplify" away:
- use `Character.team`, **never** `Player.isEvil` — a Minion turned good still
  counts as a Minion, and a good Demon (rare) still counts as a Demon;
- include **dead** players on both sides;
- exclude `isTraveller` seats from both the truth set and the guess;
- Legion-style seats appear in both true sets → the demon test wins.

**Uncertain edge case, flag rather than guess:** Lil' Monsta is a Demon
character with no player seat (a Minion "babysits" it). What the Alsaahir must
name in a Lil' Monsta game is not stated on the Alsaahir wiki page. The panel
should therefore always show the computed verdict **plus** a manual
"Correct / Incorrect" override, and when the script contains
`lilmonsta`/`legion`/`riot`/`summoner`/`boffin` show a caution:
*"Unusual evil team — check the computed answer before declaring."*

### Day input the app must offer

- **where:** a card at the top of `DayScreen` (`DayScreen.kt:78`), above
  "New nomination", visible **only** while `phase == DAY` and the script
  contains `alsaahir` (regardless of whether the Alsaahir is in play — bluffers
  need it, and it must not leak whether the Alsaahir is in the bag).
- **flow (three taps):**
  1. *Who is guessing?* — chip row of all living non-Traveller seats. Seats
     that already guessed today are **disabled** with the sublabel
     "already guessed today".
  2. *Demon(s)* — chip row; multi-select; the count of Demons in the adjusted
     distribution is shown as a hint to the ST only.
  3. *Minion(s)* — chip row; multi-select; same.
- **verdict shown to the ST immediately, before anything is committed:**
  - `CORRECT — good wins` (green) when `alsaahirCorrect(...)`,
    the speaker's `characterId == "alsaahir"`, **and**
    `!StatusEffects.isImpaired(speaker)`.
  - `Correct, but <name> is DRUNK/POISONED — say nothing happens.` (red)
    when the guess is right but the Alsaahir is impaired.
  - `Correct, but <name> is not the Alsaahir — say nothing happens.` when a
    bluffer happens to nail the evil team.
  - `Incorrect — say nothing happens.` otherwise, with a ST-only breakdown
    listing what was missed ("Sarah is a Minion and was not named",
    "Doug was named as a Minion but is the Demon").
- **commit:** records a `DayStatement`. When the verdict is a real win, the
  button reads **"Good wins — end the game"** and calls the existing
  `RevealSheet` path (`GameShell.kt:538`, `revealGoodWins = true`).
- **cap:** a counter line `2 of 3 guesses used today (optional rule)` with a
  settings toggle for the optional 3-per-day limit; when on, the speaker row is
  disabled after the third guess with the reason spelled out.

### Interactions / jinxes

- **Vizier:** add
  `{"id1":"alsaahir","id2":"vizier","reason":"The Storyteller doesn't declare the Vizier is in play."}`
  to `night_and_jinxes.json`. Surface it in "Jinxes in play"
  (`GameExtras.ActiveJinxesDialog`) **and** as a setup-time warning, since the
  Vizier announcement otherwise happens before anyone opens that dialog.
- **Drunk / Marionette:** a Drunk-as-Alsaahir or Marionette-as-Alsaahir guess
  never wins. `isImpaired` covers the Drunk; the Marionette needs the same
  treatment as in `InfoCalc.impairments` (`InfoCalc.kt:139`) — the check should
  be `speaker.characterId == "alsaahir" && !isImpaired && characterId != "marionette"`,
  which is automatic once the Marionette is never assigned `alsaahir` as its
  `characterId`.
- **Vortox:** no interaction — the Alsaahir receives no information.
- **Mid-game team changes** (Pit-Hag, Kazali, Snake Charmer, Fang Gu jump, Imp
  star-pass, Scarlet Woman, Mezepheles, Bounty Hunter's evil Townsfolk): the
  truth set is recomputed from live state on every render, so these are handled
  for free — **and** the Bounty Hunter's evil Townsfolk must **not** appear in
  the truth set, because they are a Townsfolk, not a Minion. Add a test.
- **No effect from being dead**: the Alsaahir must be **alive** to speak, so the
  speaker chip row is restricted to living players.

### UI text

- Card title: `Alsaahir guess`
- Speaker row label: `Who is guessing? (anyone may claim Alsaahir)`
- Verdict, correct + sober: `CORRECT — the whole evil team. Good wins immediately.`
- Verdict, correct + impaired: `Correct — but Ana is POISONED (Poisoner). Say "nothing happens" exactly as you would for a wrong guess.`
- Verdict, wrong: `Incorrect — say "nothing happens". Missed: Sarah (Baron).`
- Day-start briefing line: `Alsaahir in play — prompt them once today if they want to guess.`

### Data changes

- `night_and_jinxes.json` — add the Alsaahir–Vizier jinx.
- `night_guide.json` — extend `NightGuideEntry` (`NightGuide.kt:37`) with an
  optional `day: GuideNight?` and add an `alsaahir` entry with the How-to-Run
  text and the optional-rule note.
- `characters.json:1223` — no change.

## Tests to add

`engine/src/test/kotlin/com/clocktower/engine/AlsaahirTest.kt`

1. **Given** 1 Imp + 2 Minions (one dead) + an Alsaahir; **when** the guess names the Imp as Demon and both Minions as Minions; **then** `correct == true`.
2. **Given** the same board; **when** the guess omits the **dead** Minion; **then** `correct == false`.
3. **Given** a Minion whose `alignmentFlipped == true` (turned good by a Mezepheles/Snake Charmer style effect); **when** the guess names them as a Minion; **then** `correct == true` — the test is on character type, not alignment.
4. **Given** a Traveller who is evil; **when** the guess omits them; **then** `correct == true`.
5. **Given** a Traveller who is evil; **when** the guess names them as a Minion; **then** `correct == false` (they are not part of the required sets).
6. **Given** a Legion seat (Minion **and** Demon); **when** it is guessed as a Demon; **then** `correct == true`; **when** guessed as a Minion; **then** `correct == false`.
7. **Given** a Bounty Hunter game with one evil Townsfolk; **when** the guess names the true Minion and Demon but **not** the evil Townsfolk; **then** `correct == true` (an evil Townsfolk is neither a Minion nor a Demon).
8. **Given** an Imp star-pass has made a former Minion the Imp; **when** the guess names the *new* Imp as Demon and the dead former Imp as Demon; **then** `correct == true` (all Demons, including dead ones).
9. **Given** the Alsaahir holds `poisoner:Poisoned`; **when** a fully correct guess is recorded; **then** the engine returns `correct = true, tookEffect = false` and no win advisory is raised.
10. **Given** a bluffing Empath makes a fully correct guess; **then** `tookEffect == false`.
11. **Given** the Alsaahir already has a `DayStatement` for `day == cycle`; **when** a second guess is attempted; **then** the engine rejects it / the UI disables the speaker.
12. **Given** an Alsaahir and a Vizier on the script; **when** `GameData.activeJinxes` runs over the in-play ids; **then** the Alsaahir–Vizier jinx is returned.
