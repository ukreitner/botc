# Artist (artist) — Sects & Violets Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Artist> (fetched 2026-08-25);
Vortox interaction from <https://wiki.bloodontheclocktower.com/Vortox>.

Current ability text:

> "Once per game, during the day, privately ask the Storyteller any yes/no question."

Summary line: *"The Artist may ask any 1 question, and get an honest answer."*

**How to Run (verbatim):**

> "During any day, the Artist can request a private chat with you. Take them away
> from the circle so you cannot be overheard. They will ask you a question. Whisper
> "Yes," "No," or "I don't know," to them, or if you cannot answer in one of these
> ways, prompt the Artist to ask again in a different way. **The Artist loses their
> ability** - mark them with the **NO ABILITY** reminder."
>
> "Like the Savant, evil players bluffing as the Artist may request a private chat
> with you and pretend to ask a question. To help them bluff, you can pretend to
> give an answer by nodding or shaking your head."

**Examples (verbatim):**

1. "Is the Demon sitting in a brown chair?" Answer: "No"
2. "Is David the Evil Twin?" Answer: "Yes"
3. "How many Minions are alive?" Answer: "Please ask another question. I cannot answer that with a yes, no, or I don't know."
4. "Are we winning?" Answer: "I don't know"

**Storyteller-relevant timing / edge cases**

- The trigger is **during the day, at the player's request**. There is no night step
  and no wake row; the Artist is never on the night order (correctly absent from
  both order lists in `night_and_jinxes.json`).
- The ability is **spent by use**, not by day: it is once per *game*, usable on any
  day, and it is marked with the **NO ABILITY** reminder as soon as it is answered.
  A refused/unanswerable question ("please ask another way") does **not** spend it —
  the Artist asks again.
- Three legal answers: **Yes / No / I don't know**. "I don't know" is a legitimate
  storyteller answer (example 4) and is *not* a cop-out only for impossible questions.
- The Artist can ask about anything, including out-of-game facts (example 1, brown
  chair) — the app must not constrain the question.
- **Vortox** (<https://wiki.bloodontheclocktower.com/Vortox>): "Anytime a Townsfolk
  player gets information from their ability, they get false information. **Even if
  they are drunk or poisoned, it must be false.**" The Artist is a Townsfolk whose
  ability yields information, so with an alive Vortox the answer **must** be a lie.
  The Artist wiki's Tips section says exactly this from the player side: don't ask
  "is there a Vortox", ask something you can verify as true and see if you are lied to.
- **Drunk / poisoned**: the storyteller *may* give a false answer (discretionary), and
  the ability is still spent. (The wiki does not carry a dedicated Artist drunk/poisoned
  section; this follows the general drunk/poisoned rule. Flagged as an inference, not a quote.)
- **Misregistration**: the Artist's questions frequently hinge on registration
  ("Is David the Evil Twin?"). A Recluse may register as a Minion/Demon, a Spy as a
  Townsfolk/Outsider, so the honest answer may legitimately be either — the storyteller
  chooses, per the standard misregistration rule.
- **Jinxes: none.**

## What the app does today

| path | what it holds |
|---|---|
| `engine/src/main/resources/botc/data/characters.json:785-796` | Artist entry. Ability text matches the wiki exactly. `firstNightReminder` / `otherNightReminder` empty, `reminders: ["No ability"]`. Correct data. |
| `engine/src/main/resources/botc/data/night_and_jinxes.json` | Artist appears in **neither** night order list. Correct — no night step. |
| `engine/src/main/resources/botc/data/night_guide.json` | **No `artist` key at all.** `NightGuide.forStep("artist", …)` (`engine/src/main/kotlin/com/clocktower/engine/NightGuide.kt:56-59`) returns null. |
| `engine/src/main/kotlin/com/clocktower/engine/InfoCalc.kt:29-36` | `supports()` does not include `artist`. Nothing is computed. |
| `app/src/main/java/com/clocktower/grimoire/ui/screens/DayScreen.kt:54-277` | The whole Day tab: nominations, tap-to-vote, execution. There is no facility to record any day-time statement, question, claim or once-per-game use. |
| `app/src/main/java/com/clocktower/grimoire/ui/screens/NightScreen.kt:202-204, 263-279` | The only "Mark spent" affordance in the app. It is inside `NightToolTray`, driven by `activeCharacter = expandedId` (`NightScreen.kt:98-100`) — i.e. the currently-expanded **night step**. The Artist has no night step, so this chip is unreachable for the Artist. |
| `app/src/main/java/com/clocktower/grimoire/ui/screens/SeatSheet.kt:109-117` + `SeatSheet.kt:492-570` | The only path to the Artist's "No ability" token: Grimoire → tap seat → Add reminder → scroll the "In play"/"Rest of script" list to *Artist* → tap the token. Uses plain `addReminder`, so it stacks silently if tapped twice. |
| `engine/src/main/kotlin/com/clocktower/engine/InfoCalc.kt:147` | "No ability" is only ever *read* as a night-info caveat, and only when a night step for that character exists. |
| `app/src/main/java/com/clocktower/grimoire/ui/components/ShowCards.kt:363-395` | Phrase/signal cards. There is **no** "YES", "NO" or "I DON'T KNOW" phrase card; the yes/no cards are only synthesised inside the night InfoCalc panel (`NightScreen.kt:896-901`). |

**Storyteller's experience today:** the Artist is functionally invisible to the app.
Mid-day a player pulls you aside; you answer from your own head, then must remember —
possibly for the rest of the game — to go Grimoire → seat → Add reminder → find
Artist → No ability. Nothing warns you that a Vortox is in play and the answer must
be false, nothing warns you that the asker is poisoned, nothing records the question
or the answer, and nothing distinguishes the real Artist asking from an evil player
bluffing.

## Defects and gaps

1. **P0 · A Vortox in play does not force a false Artist answer.**
   Rules: with an alive Vortox, all Townsfolk information must be false, "even if they
   are drunk or poisoned". App: the Vortox caveat lives only in `InfoCalc.commonCaveats`
   (`InfoCalc.kt:158-166`), which is only reached from a night step's InfoCalc panel
   (`NightScreen.kt:836-863`). The Artist has no night step and is not in `supports()`
   (`InfoCalc.kt:29-36`), so the storyteller is never told. **Repro:** run S&V with
   Vortox + Artist; on day 1 the Artist asks "is 2+2=4?"; nothing anywhere in the app
   says the answer must be "No".

2. **P0 · A drunk/poisoned Artist is not flagged at the moment of the question.**
   `StatusEffects.isImpaired` (`StatusEffects.kt:36-46`) and `InfoCalc.impairments`
   (`InfoCalc.kt:133-153`) already know the answer, but nothing surfaces it during the
   day. **Repro:** Poisoner poisons the Artist on night 1; on day 1 the Artist asks a
   question; the Grimoire tab shows a "Poisoned" token on their seat but the storyteller
   is mid-conversation away from the circle and gets no prompt.

3. **P1 · No way to record that the ability was used, at the moment it was used.**
   Rules require the NO ABILITY reminder placed immediately. App: the only path is a
   4-tap detour through the seat sheet (`SeatSheet.kt:109-117, 492-570`), and the
   "Mark spent" chip that exists for night once-per-game characters
   (`NightScreen.kt:263-279`) is structurally unreachable for a day ability.
   **Repro:** answer an Artist question; there is no button anywhere on the Day tab.

4. **P1 · No record of the question or the answer.**
   Nothing to consult when the Artist later reports what they asked and the town
   argues about the exact wording, and nothing to feed the Mathematician ledger
   (a poisoned Artist getting a false answer is an "abnormal" ability, see
   `docs/audit/characters/mathematician.md`). The only writable field is the single
   global free-text `storytellerNotes` blob (`GameState.kt:112`,
   `GameShell.kt:685-706`) — a modal dialog you must retype into.

5. **P1 · Stale "No ability" survives a character change.**
   `GameActions.assignCharacter` (`GameActions.kt:46-53`) rewrites `characterId` /
   `shownCharacterId` but leaves `reminders` untouched. A spent Artist who is
   Pit-Hagged into another character keeps an `artist:"No ability"` token, which
   `StatusEffects.deathNotes` (`StatusEffects.kt:75`), `nominationWarnings`
   (`StatusEffects.kt:153-157`) and `InfoCalc.impairments` (`InfoCalc.kt:147`) then
   read as "this player has no ability". Conversely a *new* Artist created by a
   Pit-Hag gets no fresh-use tracking. **Repro:** mark an Artist spent, then Seat sheet
   → Change character → Empath. The Empath's night step now says "has no ability".

6. **P2 · No how-to-run text anywhere in the app.**
   `night_guide.json` has no `artist` key, and the guide is only rendered from the
   night sheet (`NightScreen.kt:792-832`). The Script/Reference tab shows the one-line
   ability only. The storyteller never sees "whisper Yes / No / I don't know", "prompt
   them to rephrase", or "pretend to answer bluffing evil players".

7. **P2 · No YES / NO / I DON'T KNOW show cards.**
   `ShowCards.kt:367-377` offers "DID YOU NOMINATE TODAY?" etc. but no answer cards.
   In a loud room the private chat is easier done on the phone screen.

8. **P2 · No misregistration prompt.**
   The Artist's canonical example question ("Is David the Evil Twin?") is exactly the
   case where a Recluse/Spy makes both answers defensible. `InfoCalc.misregistrations`
   (`InfoCalc.kt:121-130`) already produces this text but is never invoked for the Artist.

9. **P2 · No tracking of *claimed* Artists.**
   The wiki explicitly tells the storyteller to fake-answer bluffing evil players. The
   app has nowhere to note "Kai also claimed Artist and I nodded at them on day 2",
   which is exactly the "make it easy to write down all the Gossips even if Gossip
   isn't in play" complaint from the playtest, applied to the Artist.

10. **P3 · The once-per-game detector is a string prefix match.**
    `NightScreen.kt:204` — `ability.startsWith("Once per game", ignoreCase = true)`.
    Fragile, and misses "Once per game, at night*…" phrasings placed differently. A
    structured `oncePerGame` flag on `Character` would be more robust; noted here
    because the Artist's spec depends on it.

## Proposed behaviour (spec)

The Artist is the archetype of a **day-time storyteller input**. The spec below
introduces the shared day-record model that the Juggler, Gossip, Savant, Fisherman,
Slayer, Mutant and Klutz auditors all need; the Artist is its simplest consumer.

### Shared model (engine)

```kotlin
// GameState.kt — new
@Serializable
enum class DayActKind { ARTIST, JUGGLE, GOSSIP, SAVANT, FISHERMAN, SLAYER, CLAIM, OTHER }

@Serializable
data class DayAct(
    val id: Long,
    val day: Int,
    val kind: DayActKind,
    /** Seat that used/claimed the ability. */
    val playerId: Long,
    /** True when the app believes this seat really holds the character. */
    val genuine: Boolean,
    /** Free text: the Artist's question, the Gossip's statement, ... */
    val text: String = "",
    /** Storyteller's answer/verdict: "YES" | "NO" | "IDK" | ... */
    val answer: String = "",
    /** Structured payload for guess-style abilities (Juggler). */
    val guesses: List<DayGuess> = emptyList(),
    /** Truth obligation at the time it was answered, for the log. */
    val obligation: InfoObligation = InfoObligation.TRUTH,
)
// GameState gains:  val dayActs: List<DayAct> = emptyList()
```

```kotlin
// InfoCalc.kt — new, usable from day UI as well as night UI
enum class InfoObligation { TRUTH, MAY_LIE, MUST_LIE }

fun obligation(state: GameState, lookup: (String) -> Character?, holder: Player?): InfoObligation
```

- `MUST_LIE` when any player with `characterId == "vortox"` is **alive** — this outranks
  everything ("even if they are drunk or poisoned, it must be false").
- `MAY_LIE` when `impairments(state, lookup, holder)` is non-empty (Drunk, Marionette,
  a reminder containing "poison"/"drunk", a `"No ability"` token, or
  `StatusEffects.derivedPoison` — i.e. No Dashii adjacency).
- `TRUTH` otherwise.

### Artist specifics

- **when:** DAY only. No night row; the Artist must never appear in `firstNight` /
  `otherNight` order lists.
- **wake/eligibility condition:** an **alive** seat whose `characterId == "artist"` and
  which carries no `PlacedReminder("artist", "No ability")`.
- **targets:** none.
- **immediate effects on answering:** append a `DayAct(kind = ARTIST, day = state.cycle,
  playerId, genuine = true, text = question, answer, obligation)` **and**
  `placeExclusiveReminder(state, playerId, PlacedReminder("artist", "No ability"))`,
  in one undoable transaction.
- **the "ask again" path spends nothing:** a fourth control, "Couldn't answer — ask
  again", closes the panel with no `DayAct` and no token.
- **deferred effects:** none. No dawn/dusk consequence, nothing to announce.
- **expiry:** `artist:"No ability"` **never** expires. It must, however, be dropped when
  the seat's character changes (see the cross-cutting fix below).
- **information:** the answer is chosen by the storyteller, not computed. The panel
  must show, above the answer buttons:
  - the obligation banner (see below),
  - `InfoCalc.misregistrations(state, allPlayers)` output, so "Is David the Evil Twin?"
    comes with "Priya is the Recluse — may register as evil / a Minion or Demon."
- **visibility:** nothing is shown to the Demon, Minions or Lunatic. Optionally offer
  full-screen `YES` / `NO` / `I DON'T KNOW` cards for the private chat.
- **day-time inputs the app must record:** the question text (optional, one line), the
  answer, and the fact that the ability is now spent. Also a **"someone else claimed
  Artist"** button that records `DayAct(genuine = false)` with no token — that is the
  storyteller's own record of which evil player they fake-answered.
- **interactions:** no jinxes. Vortox and drunk/poison as above. Pit-Hag/Philosopher/
  Cannibal can create a *new* Artist mid-game whose ability is unspent — driven entirely
  off the `characterId` + absence of the token, so it works for free once the stale-token
  bug is fixed.

### UI text the Day tab should display

A card in the Day tab, present whenever an unspent Artist exists (and also reachable
from the seat sheet):

> **Artist — Marta has not asked yet**
> Take them away from the circle. Whisper Yes, No, or I don't know.
> If you can't answer that way, ask them to rephrase — the ability is not spent.
> `[ Question… (optional) ]`
> `[ YES ]  [ NO ]  [ I DON'T KNOW ]  [ Couldn't answer — ask again ]`
> `[ Someone else claimed Artist… ]`

Obligation banner, above the buttons:

- `MUST_LIE`: **"VORTOX IS IN PLAY — your answer MUST be false."** (ember red)
- `MAY_LIE`: **"Marta is POISONED (Poisoner) — you may answer falsely."** (ember red)
- `TRUTH`: **"Marta's ability is working — answer honestly."** (muted)

Once spent, the card collapses to a log line:
> **Artist — spent on day 2.** "Is the Demon sitting to my left?" → **NO** *(answered truthfully)*

### Data changes

- `night_guide.json`: add an `artist` entry under a new `day` slot (or a parallel
  `day_guide.json` keyed the same way), carrying the How-to-Run prose above plus the
  three answer show-cards:
  ```json
  "artist": { "day": { "instructions": "…", "shows": [
    {"label":"YES","kind":"message","text":"YES"},
    {"label":"NO","kind":"message","text":"NO"},
    {"label":"I don't know","kind":"message","text":"I DON'T KNOW"}] } }
  ```
  This requires `NightGuideEntry` (`NightGuide.kt:36-40`) to gain a `day: GuideNight?`
  field and a `NightGuide.forDay(id)` accessor.
- `ShowCards.kt:367-377`: add `"YES"`, `"NO"`, `"I DON'T KNOW"` to the phrase list.
- `characters.json`: **no change needed** — the entry is already correct.

### Cross-cutting fix this character requires

`GameActions.assignCharacter` (`GameActions.kt:46-53`) must drop reminders whose
`sourceId` equals the *outgoing* character id (they belong to an ability the seat no
longer has), while keeping tokens placed by *other* characters (Poisoner's "Poisoned",
Butler's "Master", …). Same for `swapCharacters` (`GameActions.kt:99-115`).

## Tests to add

1. **Vortox forces a lie.**
   *Given* an alive `vortox` seat and an unspent `artist` seat,
   *when* `InfoCalc.obligation(state, lookup, artistSeat)` is called,
   *then* it returns `MUST_LIE`. *And* with the Vortox dead it returns `TRUTH`.

2. **Poison alone is discretionary.**
   *Given* an `artist` seat with `PlacedReminder("poisoner","Poisoned")` and no Vortox,
   *then* `obligation` returns `MAY_LIE`.

3. **Vortox outranks poison.**
   *Given* both an alive Vortox and a poisoned Artist,
   *then* `obligation` returns `MUST_LIE` (not `MAY_LIE`).

4. **Answering spends the ability exactly once.**
   *Given* an unspent Artist, *when* the answer action is applied twice,
   *then* the seat holds exactly one `artist:"No ability"` reminder and `dayActs`
   contains exactly one `ARTIST` record for that day (the second call is a no-op).

5. **"Ask again" spends nothing.**
   *Given* an unspent Artist, *when* the "couldn't answer" action is applied,
   *then* no reminder is placed and `dayActs` is unchanged.

6. **Eligibility.**
   *Given* an Artist seat with `artist:"No ability"`, *then* the Artist is reported as
   spent; *given* a **dead** Artist with no token, *then* the Artist is reported as
   ineligible (dead players have no day ability).

7. **Character change clears the spent mark (currently fails).**
   *Given* a seat that is a spent Artist, *when* `assignCharacter(seat, "empath")`,
   *then* the seat has no `artist`-sourced reminders, and
   `InfoCalc.impairments(...)` for that seat no longer reports "has no ability".

8. **Fresh Artist from a Pit-Hag is unspent.**
   *Given* a seat that was an Empath and becomes `artist` mid-game,
   *then* the Artist is reported eligible on the next day.

9. **Claim recording does not spend.**
   *Given* an evil seat, *when* a `genuine = false` ARTIST `DayAct` is recorded,
   *then* no reminder is placed on that seat and the real Artist remains eligible.
