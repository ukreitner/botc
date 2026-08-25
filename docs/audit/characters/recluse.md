# Recluse (recluse) — Trouble Brewing Outsider

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Recluse> (fetched 2026-08-25),
<https://wiki.bloodontheclocktower.com/Imp>,
<https://wiki.bloodontheclocktower.com/Scarlet_Woman>.

Current ability text:

> "You might register as evil & as a Minion or Demon, even if dead."

How to run — verbatim:

- *"Each time the Recluse is targeted by an ability that detects or affects evil
  characters, choose which character and alignment the Recluse registers as."*
  The decision is made **fresh each time** — the Recluse can register as evil to
  the Empath tonight and good to the Chef tomorrow.
- A Recluse registering as a specific evil character **does not gain** that
  character's ability.
- Registration continues **after death** ("even if dead") — the Undertaker and
  Ravenkeeper are the usual beneficiaries.
- Wiki example, verbatim: *"The Recluse is executed. The Undertaker learns that
  the Imp was executed."*
- Wiki example, verbatim: *"The Slayer uses their ability on the Recluse… the
  Recluse dies."* — a Recluse may register as the Demon to the Slayer and
  genuinely die.
- The wiki mentions the Recluse *"might register as the Demon to the Sage"*, so
  registering as the Demon to any detection ability (Fortune Teller, Sage,
  Undertaker, Ravenkeeper, Dreamer, Village Idiot, Investigator, Empath, Chef,
  Shugenja, Oracle, Clockmaker, Seamstress, Balloonist type) is in scope.
- **Imp star pass:** the Storyteller must pick "an alive Minion" to become the
  Imp. Because the Recluse *might register as a Minion*, a Recluse is a legal
  star-pass heir and genuinely becomes an evil Imp. (The Recluse page does not
  spell this out; it follows from the Imp's "an alive Minion" plus the Recluse's
  registration clause, and is standard Storyteller practice. Flagged as
  derived-not-quoted.)
- The Recluse being executed does **not** satisfy the Godfather's "an Outsider
  died today" unless the Storyteller chooses to register them as an Outsider —
  they are one, so it does by default; the choice is whether they *also* count as
  evil elsewhere. (Not quoted on the page; treat as Storyteller discretion.)

Jinxes: **none** in `night_and_jinxes.json` for `recluse`.

Night order: the Recluse never wakes — absent from both order lists. Correct.

## What the app does today

Data
- `characters.json` — `recluse`: ability text matches the wiki exactly.
  `setup: false`, no reminders, no night reminders. Correct.
- `night_and_jinxes.json` — absent from `firstNight` and `otherNight`. Correct.
- `night_guide.json` — **no entry**. Correct (never wakes), though it means the
  app has nowhere to put "how to run the Recluse" guidance.

Engine
- `InfoCalc.kt:121-130` — the **only** Recluse logic anywhere:
  ```kotlin
  "recluse" -> notes += "${ctx.name(p)} is the Recluse — may register as evil / a Minion or Demon."
  ```
  emitted as a caveat by whichever calculators pass the Recluse into
  `misregistrations(...)`.
- `Player.isEvil` (`GameState.kt:49-52`) always returns the true alignment; every
  computed answer in `InfoCalc` is the true one.
- `GameActions.starPass` (`GameActions.kt:79-96`) has no notion of the Recluse.
- `StatusEffects.kt` has no Recluse branch at all — `deathNotes`,
  `nominationWarnings`, `derivedPoison` all ignore it.
- `WinCheck.kt` ignores it.

UI
- Nothing Recluse-specific. There is no Slayer flow in the app at all
  (`grep -w slayer` over `engine/src/main` and `app/src/main` returns nothing),
  so the Slayer/Recluse interaction has no home.
- `NightScreen.kt:602-607` — the star-pass heir list includes the Recluse only
  incidentally, because it includes *every* alive player, unsorted and unlabelled.

Storyteller experience: the app tells the Storyteller "the Recluse may register
as evil" as a red line under an info result, and computes the *true* answer. The
Storyteller must decide the registration, remember it, apply it by hand (choose a
different number/token to show), and keep it consistent across nights with no
record.

## Defects and gaps

1. **P1 · Every info answer is the true one; the misregistered alternative is
   never computed.**
   `chef` (`InfoCalc.kt:186-205`), `empath` (`:207-216`), `clockmaker`
   (`:218-241`), `shugenja` (`:243-269`), `oracle` (`:271-279`), `seamstress`
   (`:356-365`), `villageidiot` (`:367-374`), `steward` (`:442-449`), `noble`
   (`:451-458`), `knight` (`:433-440`) all compute from `ctx.isEvil(p)` = true
   alignment and then append a caveat. Repro: Empath adjacent to the Recluse →
   headline "0 of X's alive neighbours are evil" plus a red note; the Storyteller
   must work out "or 1" themselves and there is no one-tap "Show 1".
   (Numeric results *do* get the generic false-info chips at
   `NightScreen.kt:903-930`, but only when a *poison/drunk/Vortox* caveat is
   present — a misregistration caveat alone does **not** trigger that block, see
   the `impaired` predicate at `NightScreen.kt:904-906`.)

2. **P1 · `startKnowing` never offers the Recluse as a Minion.**
   `InfoCalc.kt:408-421` lists only players whose true team matches. The
   Investigator's classic "the Recluse is the Baron" is not offered; if no Minion
   were somehow absent the app would say "No Minion in play". Repro: open the
   Investigator step with a Recluse in play → the Recluse is not in the list.

3. **P1 · Undertaker / Ravenkeeper give the true token with no alternative.**
   `InfoCalc.kt:281-293` (`undertaker`) sets `headline = "Show: Recluse"`;
   `InfoCalc.kt:376-384` (`revealCharacter`) sets `"<name> is the Recluse"`. The
   wiki's own example is the opposite ("the Undertaker learns that the Imp was
   executed"). No chip offers an evil token.

4. **P1 · Fortune Teller does not model "the Recluse registers as the Demon".**
   `InfoCalc.kt:325-342` computes YES only from `team == DEMON` or the red-herring
   token. A chosen Recluse yields NO plus a caveat. Because the misregistration
   caveat does not satisfy `NightScreen.kt:904-906`, there is no "Show YES" chip
   either. Repro: Fortune Teller picks the Recluse and a Townsfolk → "NO" in
   gold with a red note, and no way to flash YES full-screen without going to the
   generic show tool.

5. **P1 · The Imp star pass does not identify the Recluse as a legal heir.**
   `NightScreen.kt:602-607` offers all alive players with no labelling, so the
   Recluse is indistinguishable from an illegal Townsfolk heir; and once the
   heir list is correctly restricted to Minions (see `imp.md` defect 1) the
   Recluse must be explicitly re-admitted or a legal play is lost.

6. **P1 · No registration record, so consistency is the Storyteller's problem.**
   Nothing in `GameState` stores "I told the Investigator the Recluse was the
   Baron on night 1". On night 3 the Undertaker asks about the same Recluse and
   the app offers no memory. `GameLogDialog` (`GameExtras.kt:46-105`) logs only
   deaths and nominations.

7. **P2 · No Slayer interaction anywhere.**
   The app has no Slayer resolution flow at all, so the wiki's explicit
   "the Slayer shoots the Recluse and the Recluse dies" case has no home. (Slayer
   itself is out of this scope; note it as the owning auditor's item, but the
   Recluse spec below assumes a Slayer flow exists.)

8. **P2 · `misregistrations()` is not called by several relevant calculators.**
   `balloonist` (`InfoCalc.kt:486-496`) passes no caveats at all — yet the
   Balloonist's "a different character *type*" is exactly where a Recluse
   registering as a Minion matters. `king` (`:397-406`), `sage` (`:423-431`),
   `cultleader` (`:386-395`), `bountyhunter` (`:460-467`) and `mathematician`
   (`:77-80`) likewise omit them; `bountyhunter` in particular ("point to 1 evil
   player") is a natural Recluse target.

9. **P2 · The Recluse is not flagged at nomination or execution time.**
   Executing a Recluse produces a "Show: Recluse" Undertaker answer next night;
   the app should proactively tell the Storyteller at execution "the Undertaker
   will ask about this player — decide now what they register as."

10. **P3 · No `night_guide.json` entry means the Recluse has no reference text in
    the app.** The Storyteller sees the ability string only in the seat sheet
    (`SeatSheet.kt:196-198`) and the Reference tab.

## Proposed behaviour (spec)

### The Recluse never wakes — everything is *reactive*

Model the misregistration as a first-class decision the app asks for at the
moment an ability targets the Recluse, records, and replays.

Shared with the Spy (see `spy.md`): add
`GameState.misregistrations: List<Misregistration>` with
`(playerId, cycle, askedBy, asCharacterId, asEvil)`.

### `InfoCalc` contract change

`InfoResult` gains:
```kotlin
data class InfoResult(
    val headline: String,
    val detail: String = "",
    val caveats: List<String> = emptyList(),
    /** Alternative true-under-a-different-registration answers, ready to show. */
    val alternatives: List<Alternative> = emptyList(),
)
data class Alternative(
    /** e.g. "If the Recluse registers as a Minion" */
    val label: String,
    val headline: String,
    val show: ShowSpec,          // number / YES / NO / character token id
    val playerId: Long,          // the misregistering player
    val asCharacterId: String?,  // what they would register as
    val asEvil: Boolean,
)
```

Per calculator, enumerate the Recluse's (and Spy's) registrations:
- `chef`, `empath`, `oracle`, `seamstress`, `villageidiot`, `shugenja`,
  `clockmaker`, `steward`, `noble`, `knight`, `bountyhunter`, `balloonist`:
  recompute with each misregistering player's alignment flipped.
- `startKnowing` (Washerwoman/Librarian/Investigator): add the Recluse to the
  `MINION` candidate pool and, for each Minion on the script that is not in play,
  offer "Show `<Minion>` and point at `<Recluse>` + 1 wrong player".
- `fortuneteller`: if a chosen player is the Recluse, add an alternative "YES —
  the Recluse registers as the Demon".
- `undertaker`, `revealCharacter` (Ravenkeeper/Grandmother/Dreamer): if the
  subject is the Recluse, offer every Minion and Demon token on the script as a
  one-tap `ShowCard.CharacterCard`.
- `sage`, `king`, `cultleader`, `mathematician`: at minimum start passing the
  relevant players through `misregistrations(...)` so the caveat appears.

### UI change (`NightScreen.kt:878-930`)

Split the current "False info to show instead" block into two:
- **"Impaired — give false info"** (existing behaviour, gated on
  poison/drunk/Vortox);
- **"Misregistration — choose what `<name>` registers as"**, gated on the
  presence of a `spy`/`recluse` in the relevant set, rendering
  `result.alternatives` as one-tap chips. Tapping a chip both shows the card
  full-screen **and** appends a `Misregistration` record.
- When a prior record exists for the same player, prefix with
  "Night 1 you registered `<name>` as the Baron to the Investigator."

### Star pass
`GameActions.impStarPass` (see `imp.md`) accepts a Recluse heir; the UI lists
them after the Minions with the label
"Recluse — may register as a Minion (they really become the Imp)".

### Execution / death hooks
- `StatusEffects.deathNotes` gains, when `id == "recluse"`:
  "Recluse: the Undertaker (and Ravenkeeper, if they die at night) may be shown
  any Minion or Demon token instead — decide now."
- `StatusEffects.nominationWarnings` gains, when the nominee is the Recluse and a
  `virgin` is not involved: nothing; but when the *nominator* is the Recluse and
  the nominee is an un-impaired Virgin, "the Recluse may register as a Minion —
  if you register them good/Townsfolk the Virgin triggers."

### Day start / Slayer
When a Slayer flow is added, the Recluse must appear in its target list with
"may register as the Demon — the shot can kill them".

### UI text
- Caveat (unchanged wording is fine): "`<name>` is the Recluse — may register as
  evil / a Minion or Demon."
- Alternatives header: "What does the Recluse register as, this time?"
- Chip labels: "Evil (1 neighbour)", "As the Baron", "As the Imp", "Good (true)".

### Data changes
- Add a `night_guide.json` entry keyed `recluse` with no `first`/`other` night
  blocks but a shared reference block — or, simpler, surface the ability text and
  a Storyteller crib ("choose fresh every time; they gain no ability; it works
  when dead") in the Reference tab.
- No `characters.json` or night-order changes.

## Tests to add

1. `Given` seats `[imp, poisoner, washerwoman, empath, chef, recluse, spy,
   mayor]` with the Empath at seat 4 (neighbours chef/recluse),
   `When` `InfoCalc.compute("empath", holder = empathSeat)`,
   `Then` `alternatives` contains a headline with the count one higher, labelled
   as the Recluse registering evil.
2. Same seating, `When` `InfoCalc.compute("chef", …)`,
   `Then` `alternatives` include the pair count with the Recluse evil (recluse
   at seat 5 is adjacent to spy at seat 6).
3. `Given` an Investigator and a Recluse in play, `When`
   `InfoCalc.compute("investigator", …)`, `Then` the Recluse appears as a
   possible Minion candidate with at least one not-in-play Minion token offered.
4. `Given` a Fortune Teller choosing the Recluse and a Townsfolk, `When`
   `InfoCalc.compute("fortuneteller", …, targets)`, `Then` the true headline is
   "NO" and `alternatives` contains a "YES" keyed to the Recluse.
5. `Given` a Recluse executed on day 2, `When`
   `InfoCalc.compute("undertaker", …)` on night 3, `Then` `alternatives` offer
   every Minion and Demon token on the script.
6. `Given` an Imp star pass with no alive Minion but an alive Recluse, `When`
   `starPassHeirs` is computed, `Then` the Recluse is returned.
7. `Given` a Storyteller registered the Recluse as the Baron on night 1, `When`
   the Undertaker asks on night 3, `Then` the caveat text includes the prior
   decision.
8. `Given` a Recluse, `When` `StatusEffects.deathNotes(recluse)` is computed,
   `Then` it contains the Undertaker/Ravenkeeper registration prompt.
