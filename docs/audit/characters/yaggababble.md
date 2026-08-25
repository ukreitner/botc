# Yaggababble (yaggababble) — Experimental Demon

## Official rules (sources)

Sources: <https://wiki.bloodontheclocktower.com/Yaggababble> (fetched 2026-08-25),
jinx list <https://wiki.bloodontheclocktower.com/Djinn>, rulings index
<https://botc.me/character/yaggababble>.

**Current ability text (verbatim):**
> "You start knowing a secret phrase. For each time you said it publicly today, a player might die."

**How to Run (verbatim, sentence by sentence):**
> "During setup, write a phrase on a piece of paper, or on a phone."
> "During the first night, wake the Demon. Show this phrase, then put them to sleep."
> "Each time Demon says the secret phrase, put a **DEAD** reminder in the center of the left side of the Grimoire, as a reminder to yourself to place it tonight."
> "Each night, you may mark players with these **DEAD** reminders, and add a shroud to each marked player."
> "These players die."
> "Choose a phrase that is fairly plain."

**Storyteller kill authority (verbatim):**
> "The Storyteller chooses which players die."
> "The Storyteller may choose to kill fewer players than the number of times the phrase was said."

**Drunk / poisoned (verbatim — the timing is the opposite of what most people
assume):**
> "If the Yaggababble is drunk or poisoned, players cannot die, even if the Yaggababble was sober and healthy when they said their phrase."
> "If the Yaggababble is sober and healthy, players might die, even if the Yaggababble was drunk or poisoned when they said their phrase."

So the sobriety check happens **at resolution time**, not at speaking time. The
count itself always accrues.

**Examples (verbatim, complete):**
> "The Yaggababble's phrase is 'that sounds fishy'. The Yaggababble says this once during the first day. That night, a player dies. The next day, the Yaggababble says 'that sounds fishy' three times. That night, three players die."
>
> "The Yaggababble has said their phrase twice today. A Witch is in play. When the Heretic nominates, the Heretic dies, even though they were not cursed by the Witch. The Golem nominates the Demon, and the Golem dies. Both players died due to the Yaggababble's ability."

The second example is important and easy to miss: the Storyteller may **spend the
charges during the day**, disguising them as another character's effect (a Witch
curse that was never placed; a Golem nomination that should not have killed). The
botc.me rulings index corroborates this with "If the Yaggababble ability kills the
grandchild during the day, the Grandmother immediately dies."

**Derived timing:**
- Charges accrue during a **day**. There is no day before night 1, so the
  Yaggababble **never kills on night 1**.
- "for each time you said it publicly **today**" — the count is per-day and does not
  carry over. Charges are spendable from the moment they accrue (during that day)
  through that day's following night, and are gone afterwards.
- The Yaggababble is **not woken** on nights 2+. It only has a night-order slot so
  the Storyteller resolves the deaths in the right sequence.

**Jinx (verbatim):**
- Yaggababble / Exorcist: "If the Exorcist chooses the Yaggababble, the Yaggababble
  does not kill tonight."

**Also recorded on botc.me:** if a player becomes the Yaggababble mid-game (Summoner,
Scarlet Woman, Pit-Hag) after having said the phrase earlier that day, "player(s)
might die tonight" — i.e. utterances made before gaining the character still count.

**Uncertain (flagged, not guessed):** whether a Yaggababble who dies during the day
still kills that night for phrases said while alive. Not addressed on the wiki.

## What the app does today

**Data — mostly correct.**
- `characters.json:2090-2104` — ability at `:2094` matches the wiki exactly;
  `setup: false`; `firstNightReminder` "Show the Yaggababble their secret phrase."
  (`:2096`); `otherNightReminder` "For each time the Yaggababble said their phrase
  today, a player might die." (`:2097`); `reminders: ["Dead","Dead","Dead"]`.
- `night_and_jinxes.json` — first night index 12 (`:307`, before MINION_INFO at 14,
  so the Minions can be let in on the phrase), other night index 51 (`:424`).
  **Works.** The exorcist/yaggababble jinx is present (`:288-292`) with the correct
  meaning.
- `night_guide.json:1671-1687` — accurate prose for both nights, and a "Secret
  phrase" message card with starting text "YOUR PHRASE IS…".

**Night 1 (the storyteller's actual experience)**
- The step shows the guide prose, then the "» Secret phrase" chip, which opens
  `GuideShowDialog` (`NightScreen.kt:366-454`): a free-text field the ST edits and
  then shows full-screen as `ShowCard.Message`. That is the right shape — **but the
  text is `rememberSaveable(show.label)` local state (`:374`), never written to
  `GameState`.** The phrase is lost the moment the dialog closes.
- Then `QuickResolutions` (`:462-525`) falls to the `else` branch (`:518-523`) —
  team DEMON, holder alive — and renders **`DemonKillPanel`** (`:534-638`): "Demon
  kill — who did <name> choose?" with a working "<name> dies" button, **on night 1**,
  when the Yaggababble has no kill at all and does not even wake for one.

**Days**
- There is nowhere to count utterances. `GameState` offers a single free-text
  `storytellerNotes` (`GameState.kt:112`, edited at `GameShell.kt:686`) and a
  per-player `note` (`GameState.kt:31`). The ST tallies on paper or in their head —
  the precise complaint the user made about Gossip, applied to a Demon's kill count.
- There is no way to spend a charge during the day (wiki Example 2), so the
  disguised-kill play is unsupported: the ST must kill from the seat sheet
  (`SeatSheet.kt:267-300`) with `DeathCause.STORYTELLER` and remember privately why.

**Nights 2+**
- The step detail correctly reads "For each time the Yaggababble said their phrase
  today, a player might die."
- `DemonKillPanel` renders again and offers **exactly one** kill
  (`NightScreen.kt:624-635`, which clears the selection after a single confirm). The
  ability may kill zero, one, two, three… The ST cannot see the phrase, cannot see
  the count, and must remember both.
- The three `"Dead"` reminder chips in the `NightToolTray` go down the multi-copy
  path (`NightScreen.kt:319-339`): up to three copies are placed, and the fourth
  placement silently **recycles the first one** (`:331-337`). Placing them does not
  kill anyone — `GameActions.kill` is a separate action — so tokens and shrouds can
  disagree. The official token is meant to sit **in the centre of the grimoire, not
  on a player**, and the app has no off-seat token area at all.
- The Exorcist jinx is covered incidentally: `NightOrder.kt:150-154` appends
  "— EXORCIST chose them: the Demon does not act tonight." when the demon seat holds
  `exorcist:Chosen`. **Works.**
- The impaired check in `DemonKillPanel` (`:548-554`) uses
  `StatusEffects.isImpaired` on the holder at the moment the panel renders, which
  happens to be the correct timing for this character (resolution time). **Works**,
  by luck rather than design.

## Defects and gaps

1. **P0 · Night 1 offers a demon kill.**
   The Yaggababble has no night-1 kill (no day has happened, so the count is zero)
   and is only woken to be shown the phrase. `NightScreen.kt:518-523` → `:534-638`.
   Repro: any Yaggababble game, Night tab, first night, expand the step. Same defect
   class as the reported Pukka bug.

2. **P0 · The kill count is not tracked, so the app cannot tell the ST how many may
   die.** Rules: "For each time you said it publicly today, a player might die." The
   app has no counter of any kind. Repro: play a day; at night the step says "For
   each time… a player might die" and offers one player picker and no number. The
   storyteller is doing the app's job entirely by hand.

3. **P0 · Only one kill can be resolved per night.**
   `DemonKillPanel` kills a single target and clears (`NightScreen.kt:626-633`). If
   the phrase was said three times, the ST must kill the second and third victims
   from the Grimoire tab with `DeathCause.STORYTELLER`, which then mislabels them in
   the game log (`GameExtras.kt:54-58` renders that as "died (storyteller)") and in
   `DeathRecord.cause` — corrupting Undertaker/Cannibal/Vortox reasoning downstream.

4. **P1 · The secret phrase is not stored.**
   `GuideShowDialog`'s text field is local `rememberSaveable` state
   (`NightScreen.kt:374`). On night 2 the ST cannot look the phrase up, and on night
   1 they have to invent it live in front of the table (the rules say to write it
   **during setup**). Repro: type a phrase on night 1, show it, reopen the chip —
   the field is back to "YOUR PHRASE IS…".

5. **P1 · There is no setup step for the phrase.**
   Rules: "During setup, write a phrase on a piece of paper, or on a phone."
   `GameShell.kt:347-470` already has the pattern (Fortune Teller herring, Drunk,
   Lunatic, Marionette prompts) and no Yaggababble prompt exists.

6. **P1 · Day-time kills (the wiki's own Example 2) are unsupported.**
   Spending a charge during the day, disguised as a Witch curse or a Golem
   nomination, is the character's signature play. The app has no "spend a
   Yaggababble charge" action outside the night step, and no way to record the
   disguise for the post-game explanation.

7. **P1 · The three DEAD tokens model the wrong thing.**
   `characters.json:2098-2102` gives three seat-bound Dead tokens; the rules use them
   as an **off-seat tally in the centre of the grimoire**, and the tally is unbounded
   (a chatty Yaggababble can say the phrase five times). The tray's recycling
   behaviour (`NightScreen.kt:325-339`) silently loses the fourth. Repro: place a
   fourth "Dead" token — the first one vanishes.

8. **P2 · No expiry for the charge.**
   Whatever represents the count must reset each cycle. Nothing in
   `EXPIRES_AT_DAWN` / `EXPIRES_AT_DUSK` (`GameActions.kt:218-242`) covers it, so
   hand-placed Dead tokens survive into the next day.

9. **P2 · The drunk/poison timing rule is not stated anywhere in the app.**
   It is counter-intuitive (checked at resolution, not at speaking) and is exactly
   the kind of thing the guide should say. `night_guide.json:1684` says nothing about
   it.

10. **P2 · Holder resolution by first seat index.**
    `NightScreen.kt:467` / `:520`. `GameActions.starPass` (`:79-96`) leaves the dead
    Demon's `characterId` intact, so a script where the Yaggababble can be created by
    Pit-Hag, Summoner, Scarlet Woman or a star pass can end up with two
    `yaggababble` seats; the lower seat index wins, and if that seat is dead the step
    offers no tools. The botc.me ruling about *becoming* the Yaggababble mid-game
    makes this a live scenario for this character, not a theoretical one.

11. **P3 · The night-2+ step should not present the Yaggababble as waking.**
    The guide says so in prose (`night_guide.json:1684`) but the step is rendered
    identically to a waking Demon, complete with a "Show token" chip
    (`NightScreen.kt:246-253`).

## Proposed behaviour (spec)

### New state

```
GameState.secretPhrase: String = ""                 // the Yaggababble's phrase
GameState.phraseCount: Int = 0                      // times said, this cycle
GameState.phraseSpent: Int = 0                      // charges already resolved, this cycle
```

Both counters reset at **dawn** (start of the next day), i.e. add the reset to
`GameActions.advancePhase`'s `Phase.NIGHT ->` branch (`GameActions.kt:260`), so that
a day's utterances stay spendable through that day and its following night and then
vanish. `secretPhrase` never resets.

### Setup

A blocking SETUP-phase prompt in the `GameShell.kt:347-470` family:
- title "The Yaggababble is in play"
- explanation "Write the secret phrase now. Choose a phrase that is fairly plain, so
  they can work it into conversation."
- a text field, a few one-tap suggestions ("that sounds fishy", "to be honest",
  "at the end of the day", "I'm just saying"), and a "Later" escape.
- saves to `state.secretPhrase`.
- `GameActions.validateSetupState` (`:503-561`) gains
  `"yaggababble" -> if (state.secretPhrase.isBlank()) issues += "<name>: write the
  Yaggababble's secret phrase"`.

### Night action — structured form

**First night**
- **when:** first night; wake condition: the Yaggababble seat is alive.
- **targets:** none.
- **immediate effects:** none. **No kill panel** — remove `DemonKillPanel` from this
  path.
- **information:** show `ShowCard.Message(state.secretPhrase)` full-screen, silently
  (the guide already says "do not speak"). The card should be pre-filled from
  `secretPhrase` and editable, writing any edit back to `secretPhrase`.
- **visibility:** the Yaggababble only. Minions are not told the phrase by the rules
  (the wiki's Tips suggest the Yaggababble may share it themselves).
- **UI text:** *"The Yaggababble does not kill tonight. Show them their phrase —
  silently — then put them to sleep. Count every time they say it publicly
  tomorrow."*

**Days (this is the missing half of the character)**
- A persistent, always-visible **phrase counter** on the Day tab and in the
  grimoire's day header, showing the phrase in small type and a large
  **[ + ] <n> said today** control, with a [ − ] to undo a miscount. One tap, no
  dialog — the ST is listening to a table, not navigating menus.
- The counter must also be reachable from the Grimoire tab, because the ST will be
  looking at seats when they hear it.
- A **"Spend a charge now"** action next to the counter (wiki Example 2): pick a
  player, pick an optional disguise from a dropdown built from the characters in
  play ("looks like a Witch curse", "looks like the Golem's nomination", "no
  disguise"), then kill with a new `DeathCause.YAGGABABBLE`, increment
  `phraseSpent`, and log *"Day 2 — Yaggababble charge spent on Heretic (disguised as
  a Witch curse)."*
  Guard: refuse (with the reason) when the Yaggababble is impaired at that moment.

**Other nights**
- **when:** other nights; the Yaggababble is **not woken**. The step exists so the
  deaths resolve in night order.
- **wake condition / gate:** skip entirely if `phraseCount - phraseSpent <= 0`, or
  if the Yaggababble holds `exorcist:Chosen` (jinx), or if
  `StatusEffects.isImpaired(yaggababble)` **at this moment** — in the last two cases
  show the reason: *"Drunk/poisoned right now — no one dies, even though the phrase
  was said <n> times."* / *"Exorcist chose the Yaggababble — no kills tonight."*
- **targets:** **0 to (phraseCount − phraseSpent)** players, multi-select, alive
  players only by default with a "include dead" toggle. Constraint text: *"You may
  kill up to <k>. You may kill fewer."*
- **immediate effects:** `GameActions.kill(victim, DeathCause.YAGGABABBLE)` for each
  selected player, each preceded by its own `StatusEffects.deathNotes` review
  (protections apply per victim, so Monk/Soldier/Innkeeper can block one and not
  another), all in a single undoable action; `phraseSpent += killed`.
- **deferred effects:** deaths are announced at the existing DAWN step.
- **expiry:** `phraseCount` / `phraseSpent` reset at dawn.
- **information:** none shown to any player.
- **UI text:** *"The Yaggababble does not wake. They said “<phrase>” <n> times today
  — up to <k> players may die, and you choose who (or fewer, or none)."*

### Day-time inputs the app must record
The utterance count per day, the phrase itself, each charge spent (victim, when, and
the disguise), all in the game log. This is the same shape as the Gossip/Juggler
"record what was said publicly" gap the user complained about, and should reuse
whatever day-record mechanism the mechanics auditors specify rather than growing a
one-off.

### Interactions/jinxes to handle explicitly
- **Exorcist**: gate above. `NightOrder.kt:150-154` already emits the text; make it
  an actual gate, not prose.
- **Monk / Soldier / Innkeeper / Tea Lady / Fool / Sailor / Devil's Advocate**:
  applied **per victim** via `deathNotes`; a blocked victim does **not** free the
  charge for someone else (the charge is spent on the attempt) — flag this as a
  storyteller choice if the implementer disagrees, the wiki is silent.
- **Becoming the Yaggababble mid-game** (Summoner / Scarlet Woman / Pit-Hag): the
  day's `phraseCount` still applies (botc.me). Keep the counter on `GameState`, not
  on the seat, so this falls out for free.
- **Grandmother**: if a spent charge kills the Grandchild during the day, the
  Grandmother dies immediately (botc.me) — add it to `StatusEffects.deathNotes`'s
  Grandmother branch (`StatusEffects.kt:122-127`), which today only mentions the
  Demon's night kill.
- **Vortox**: no interaction.

### Data changes
- `night_guide.json:1671-1687` — add the drunk/poison timing rule verbatim, add "you
  may kill fewer than the number of times it was said", add the day-time-kill
  example, and change the `first` instructions to say explicitly "the Yaggababble
  does not kill on the first night".
- `characters.json:2098-2102` — leave the three Dead tokens for physical parity but
  stop treating them as the count; the counter is state, not tokens.
- No night-order change.

## Tests to add

1. `Given` a first-night state with a Yaggababble, `Then` the built night step
   exposes **no** kill action (assert on the step model).
2. `Given` `phraseCount = 3`, `phraseSpent = 0`, `When` the night step resolves,
   `Then` up to 3 victims may be selected, and selecting 2 kills exactly 2 with
   `DeathCause.YAGGABABBLE` and leaves `phraseSpent = 2`.
3. `Given` `phraseCount = 3` and the Yaggababble is marked `poisoner:Poisoned`,
   `When` the night step resolves, `Then` zero kills are permitted and the reason
   names the poison (wiki: sober-when-speaking does not help).
4. `Given` `phraseCount = 0` because the Yaggababble was poisoned all day but is now
   sober, `Then` still zero kills (nothing was counted) — and, conversely,
   `Given` the Yaggababble was poisoned when speaking but is sober now, `Then` the
   count still applies (wiki, second drunk/poison sentence).
5. `Given` the Yaggababble holds `exorcist:Chosen`, `Then` the night step is skipped
   with the jinx reason and `phraseSpent` is unchanged.
6. `Given` a day with `phraseCount = 2` and one charge spent during the day,
   `Then` the following night permits exactly 1 kill.
7. `Given` `advancePhase` from NIGHT to DAY, `Then` `phraseCount` and `phraseSpent`
   are both 0.
8. `Given` a victim marked `monk:Safe`, `When` selected, `Then` no death is recorded
   and the charge is still consumed (or, if the implementer rules otherwise, the test
   pins whichever choice is made).
9. `Given` `validateSetupState` with a Yaggababble and a blank `secretPhrase`,
   `Then` an issue is reported.
10. `Given` two seats with `characterId == "yaggababble"`, one dead, `Then` the
    step's resolved holder is the living one.
11. `Given` a Grandchild killed by a day-time Yaggababble charge, `Then`
    `deathNotes`/the resolver surfaces "the Grandmother dies too".
