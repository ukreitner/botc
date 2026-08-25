# Mezepheles (mezepheles) — Experimental Minion

## Official rules (sources)

Source: https://wiki.bloodontheclocktower.com/Mezepheles (Character Text,
Summary, How to Run, Examples, Tips & Tricks), fetched 2026-08-25.

**Current ability text (quote):**
> "You start knowing a secret word. The 1st good player to say this word becomes evil that night."

`characters.json:1835` matches verbatim. No drift.

**How to Run (quotes):**
> "While setting up the game, write a single word on a piece of paper or on a phone or other device. During the first night, wake the Mezepheles, show the written word, then put them to sleep."
> "The first time you hear a good player say the secret word, mark them with the **TURNS EVIL** reminder."
> "Each night, if a player is marked with the **TURNS EVIL** reminder, wake them. Show the **YOU ARE** info token then give a thumbs down. Put them to sleep. Turn their character token upside down. (This shows they are now evil.) **The Mezepheles loses their ability** — mark them with the **NO ABILITY** reminder and remove their night token from the night sheet."

**Summary / clarifications (quotes and close paraphrases):**
- The word is revealed on the first night only.
- The word may be said **"publicly or privately"** — a whisper in a private
  conversation counts.
- **"The Mezepheles does not learn if a player turns evil."**
- **"If the Mezepheles is sober and healthy at night, the good player turns evil even if the Mezepheles was drunk or poisoned when the good player spoke the secret word."**
- Conversely, if the Mezepheles is drunk or poisoned **at the moment of the
  night conversion**, the player stays good and the Mezepheles has still "used
  their ability" — they may not turn a player evil later on.
- Choose an unusual word, unlikely to be said by accident.
- Only the **1st good player** ever converts; an evil player saying the word does
  nothing.
- The turned player keeps their character and ability; only their alignment
  changes (the wiki examples have a Barber and a Mayor).

**Examples (wiki):** a Barber turns evil after saying "Rumpelstiltskin" in
public; a Mayor chooses to expose the Mezepheles rather than turn; a Noble stays
good because a Courtier had poisoned the Mezepheles by nightfall.

**Night order.** First night index 40 (after `harpy`, before `pukka`) —
`night_and_jinxes.json:335`. Other nights index 28 (after `harpy`, before
`scarletwoman`) — `night_and_jinxes.json:401`. Both correct.

**Jinxes.** The page as fetched lists **none**.

## What the app does today

- `characters.json:1835` — correct ability; `reminders: ["Turns Evil", "No
  Ability"]`; `firstNightReminder`: "Show the secret word."; `otherNightReminder`:
  "If a good player said the secret word, wake the player. Show the 'You are'
  info token & give a thumbs-down."
- `night_guide.json:1395` —
  - `first`: "Wake the Mezepheles and silently show them their secret word
    (write it beforehand and show it as text - do not speak)…" with a `message`
    show card `"YOUR WORD IS…"` (freely editable in `GuideShowDialog`,
    `NightScreen.kt:363-451`).
  - `other`: "…wake that player, show the 'You are' info token and give a
    thumbs-down… Mark them with the Turns Evil reminder resolved and mark the
    Mezepheles with No Ability… **Consider waking them alongside or before the
    Demon so the evil team can learn its new member**…" with an `evil` show card
    `"YOU ARE NOW EVIL"` (routed to `ShowCard.AlignmentCard(evil = true)` at
    `NightScreen.kt:806`).
- `night_and_jinxes.json:335,401` — correct night-order slots; no jinx rows
  (correct).
- **No other code anywhere.** No storage for the word, no conversion action, no
  once-per-game enforcement, no step suppression.

**Storyteller experience today:** on night 1 you open the Mezepheles step, tap
"» Secret word", type the word into the dialog's text field, and show it
full-screen. The word is then **gone** — the dialog's `text` is local composable
state (`NightScreen.kt:373`) rebuilt from `show.text` each time. So on day 3,
when someone says something that might be the word, you have to remember it, or
you had the foresight to also paste it into Storyteller notes
(`GameShell.kt:686-712`). When you do hear it, you must open the speaker's seat,
add the Turns Evil token, then at night open the Mezepheles step, wake the
player, tap "» To turned player", **manually tap "Flip alignment"** on their
seat (`SeatSheet.kt:315`), manually add "No Ability" to the Mezepheles, and
remember for the rest of the game that the Mezepheles step is now dead weight —
because the app keeps rendering it every night.

## Defects and gaps

1. **P1 · The secret word is not stored anywhere.**
   Rules: the ST writes the word at setup and must recognise it for the rest of
   the game. App: the only place it can live is the free-text dialog at
   `NightScreen.kt:373` (`var text by rememberSaveable(show.label)`), which is
   discarded when the dialog closes, or the global Storyteller-notes blob. Repro:
   set the word on night 1, reopen the step — the field is back to "YOUR WORD
   IS…". This is the same class of failure as the user's Gossip complaint:
   *"make it easy to write down"*.

2. **P1 · Turning a player evil is entirely manual and easy to get half-right.**
   Rules: wake them, thumbs down, flip the token, mark the Mezepheles NO
   ABILITY. App: four unrelated manual actions across two screens
   (`SeatSheet.kt:315` flip, `SeatSheet.kt:109`/`:492` add reminder ×2, plus the show
   card). There is no `QuickResolutions` case (`NightScreen.kt:462-528` covers
   only snakecharmer/fanggu/professor). Repro: convert a player and forget
   "Flip alignment" → `Player.alignmentFlipped` stays false and the Empath,
   Fortune Teller, Chef, and `WinCheck` all keep treating them as good.

3. **P1 · No day-time capture of "who said the word".**
   The word may be said at any moment of any day, publicly or privately. There
   is no day-side control to mark "this player said the word" — the ST must
   navigate Grimoire → seat → Add reminder → scroll to Mezepheles → "Turns Evil".

4. **P1 · Once-per-game is not enforced or tracked.**
   Rules: only the **1st** good player ever converts; after the conversion (and
   also after a *failed* conversion because the Mezepheles was poisoned that
   night) the Mezepheles has "used their ability". App: nothing stops a second
   Turns Evil token being placed, and the "Mark spent" tray chip
   (`NightScreen.kt:263-277`) only appears when the ability text starts with
   "Once per game" (`NightScreen.kt:204`) — the Mezepheles' text does not, so
   there is no one-tap way to place "No Ability" from the night step.

5. **P1 · The Mezepheles step keeps appearing after the ability is spent.**
   Rules: "remove their night token from the night sheet." App:
   `NightOrder.build` (`NightOrder.kt:142-178`) includes any in-play character
   with a non-empty night reminder, with no check for a "No ability" reminder.
   Repro: convert someone on night 3; nights 4, 5, 6 still show a Mezepheles row
   telling you to wake a turned player, and it must be ticked off to satisfy the
   dawn checklist (`GameShell.kt:148-160`).

6. **P2 · The impairment rule is inverted-looking and unexplained.**
   Rules: what matters is whether the Mezepheles is sober **at night**, not when
   the word was spoken; and a poisoned-at-night Mezepheles *still burns* the
   ability. Neither `night_guide.json:1395` nor any caveat says this. Repro: a
   Courtier poisons the Mezepheles on the day the word is said — the app gives
   the ST nothing, and the natural (wrong) reading is that the conversion just
   happens.

7. **P2 · `night_guide.json:1395` invents a non-official step.**
   *"Consider waking them alongside or before the Demon so the evil team can
   learn its new member"* is not in the How to Run, which says only: wake, YOU
   ARE + thumbs down, sleep. Waking the converted player with the Demon leaks
   the whole evil team to a player who, per the rules, learns nothing but their
   own new alignment. Presenting it as guidance in the same voice as the rules
   is a rules-accuracy defect.

8. **P2 · The conversion is invisible in the game log.**
   `GameLogDialog` (`GameExtras.kt:46-104`) derives entries from `deaths` and
   `nominations` only, so "Alice turned evil on night 3" never appears.

9. **P2 · Evil speakers / dead speakers are not distinguished.**
   Only a **good** player converts. Nothing prevents the ST from marking an evil
   player Turns Evil. (A dead good player saying the word: the rules say "the 1st
   good player to say this word becomes evil that night" without an alive
   restriction — flagged as ambiguous; surface it, don't decide it.)

10. **P3 · No `showCard` for the word that persists.** The `"YOUR WORD IS…"`
    message card is right in principle, but the ST retypes the word each time
    they want to re-show it (e.g. re-showing to a resurrected Mezepheles).

## Proposed behaviour (spec)

### New engine state

```kotlin
/** Mezepheles: the secret word, chosen at setup. */
val mezephelesWord: String = "",
```

(Alternatively a general `characterNotes: Map<String, String>` keyed by
character id — the same slot would serve the Gossip/Juggler/Savant claim-capture
the user asked for. Prefer the general form.)

### Setup

- **when:** SETUP, a seat has `characterId == "mezepheles"`.
- Prompt (same family as the Drunk/Lunatic/Marionette dialogs,
  `GameShell.kt:378-479`): **"The Mezepheles is in play"**, single text field
  *"Secret word — pick something no one will say by accident"*, with 6-8
  one-tap suggestions (e.g. *Rumpelstiltskin, Aubergine, Kerfuffle, Marmalade,
  Zeppelin, Persimmon*). Saves to `mezephelesWord`.
- `validateSetupState`: if a Mezepheles is in play and `mezephelesWord.isBlank()`
  → issue `"Mezepheles: write down the secret word"`.

### Night 1

- **when:** first night. **wake condition:** Mezepheles seat exists.
- **targets:** none.
- **effects:** none.
- **information / visibility:** show the word **silently** — the step must
  display it inline (`Secret word: RUMPELSTILTSKIN` in large type) and offer a
  one-tap full-screen `ShowCard.Message(mezephelesWord)` with the word already
  filled in from state (no typing).
- **UI text:** `Wake {Mezepheles}. Show them the word in silence — do not say it out loud. Word: {word}`

### Day (the input the app is missing)

A persistent Day-tab card while the Mezepheles is in play and unspent:

> **Mezepheles — secret word: `{word}`**
> `Someone said it: [ seat chips ]`

Tapping a seat:
- rejects evil seats with `"{name} is evil — only the 1st GOOD player converts."`
  (overridable);
- if a Turns Evil token already exists anywhere, or the Mezepheles holds
  "No Ability", refuses with `"The Mezepheles has already used their ability."`;
- otherwise `placeExclusiveReminder(seat, PlacedReminder("mezepheles", "Turns Evil"))`
  and logs `"{name} said the secret word on day N"`.

The same card also carries `[ Change word ]` (rare, but a resurrected/renamed
game needs it) and shows `SPENT` once the ability is used.

### Other nights

- **when:** other nights. **wake condition:** some seat holds
  `("mezepheles","Turns Evil")` **and** the Mezepheles seat does **not** hold
  `("mezepheles","No Ability")`.
  Otherwise **omit the step entirely** — implement by adding a general
  `NightOrder` rule: skip a character's step when every holder carries a
  `"No ability"` reminder from that same character. (Same fix helps the Fool,
  Professor, Fang Gu, and every once-per-game role.)
- **targets:** the marked player (auto-selected, no picking needed).
- **immediate effects — one confirmed button, `QuickResolutions` case
  `"mezepheles"`:**
  ```
  [ {name} turns evil ]  → flipAlignment(name)
                          + removeReminder(name, "Turns Evil")
                          + placeExclusiveReminder(mezepheles, "No Ability")
                          + log entry "N3 — {name} turned evil (Mezepheles)"
  [ Mezepheles is drunk/poisoned — they stay good ]
                        → removeReminder(name, "Turns Evil")
                          + placeExclusiveReminder(mezepheles, "No Ability")
                          + log entry "N3 — {name} did NOT turn (Mezepheles impaired); ability spent"
  ```
  The second button must be **pre-highlighted** when
  `StatusEffects.isImpaired(mezephelesSeat)` is true, with the caveat text
  quoted from the rules.
- **information / visibility:** show the turned player `YOU ARE` + thumbs down
  only — i.e. `ShowCard.AlignmentCard(evil = true)`. Explicitly instruct:
  `Do NOT show them the other evil players; they learn only that they are evil.`
- **expiry:** neither `("mezepheles","Turns Evil")` nor
  `("mezepheles","No Ability")` may be added to `EXPIRES_AT_DAWN`/`_DUSK`
  (`GameActions.kt:218-242`) — currently correct, keep it that way. "Turns Evil"
  is removed by the resolution above, not by a phase change.

### Registration and downstream effects

- The turned player keeps `characterId` and ability; only `alignmentFlipped`
  changes, which `Player.isEvil` (`GameState.kt:49-52`) already honours — so
  Empath/Chef/Fortune Teller/Undertaker computations pick it up. **Works today**
  provided the ST remembers to flip; the point of the spec is to remove the
  "remember".
- `WinCheck` counts them as evil for the "Fearmonger's team loses" and
  "Goblin/Saint" style rules (see those files) — assert with a test.
- The evil team does **not** learn the new member, and the new member does not
  learn them.

### Data changes

- `night_guide.json:1395`:
  - `first`: replace the free-text card with a state-backed word card.
  - `other`: **delete** the "Consider waking them alongside or before the Demon"
    sentence; add the sober-at-night rule and the "ability is spent either way"
    rule.
- `characters.json:1835` — no change; the two reminders are correct.

### UI text

- Setup: `Write the Mezepheles' secret word. Pick something nobody says by accident.`
- Night 1: `Show {Mezepheles} the word in silence: {WORD}`
- Day card: `Secret word: {WORD} — tap the first GOOD player you hear say it.`
- Night N: `{name} said the word. Wake them, show YOU ARE + thumbs down, turn their token upside down. Nothing else — they do not meet the evil team.`
- Night N (Mezepheles impaired): `{Mezepheles} is drunk/poisoned tonight — {name} stays GOOD, but the Mezepheles' ability is used up either way.`

## Tests to add

1. *Given* a Mezepheles in play and `mezephelesWord == ""`, *then*
   `validateSetupState` reports "write down the secret word".
2. *Given* a marked player and an unspent Mezepheles, *when* the night sheet is
   built, *then* a `mezepheles` step exists.
3. *Given* the Mezepheles holds `("mezepheles","No Ability")`, *then* the night
   sheet contains **no** `mezepheles` step (currently it does).
4. *Given* no player is marked Turns Evil, *then* the other-night sheet contains
   no `mezepheles` step.
5. *Given* Alice marked Turns Evil and a healthy Mezepheles, *when* the "turns
   evil" resolution runs, *then* `Alice.alignmentFlipped == true`,
   `Alice.characterId` is unchanged, Alice has no Turns Evil reminder, and the
   Mezepheles holds exactly one "No Ability" reminder.
6. *Given* the same with a **poisoned** Mezepheles and the "stays good"
   resolution, *then* `Alice.alignmentFlipped == false` **and** the Mezepheles
   still holds "No Ability" (the ability is spent).
7. *Given* a converted Alice who is the Empath's neighbour, *then*
   `InfoCalc.empath` counts her as evil.
8. *Given* a Turns Evil token already placed, *when* the day card is used on a
   second player, *then* the action is refused.
9. *Given* a full NIGHT→DAY→NIGHT cycle, *then* both "Turns Evil" and "No
   Ability" survive `advancePhase` (neither expiry table touches them).
10. *Given* a converted player, *then* the game log contains an entry naming the
    night and the Mezepheles.
