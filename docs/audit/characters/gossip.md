# Gossip (gossip) — Bad Moon Rising Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Gossip> (fetched 2026-08-25).

Current ability text:

> "Each day, you may make a public statement. Tonight, if it was true, a player dies."

How to Run (wiki):

- Each day, if the Gossip makes a **definite, true public statement**, put the Gossip's
  **DEAD** reminder in the centre of the left side of the Grimoire (i.e. a marker meaning
  "the Gossip's ability fires tonight").
- Each night **except the first**, if a true statement was made that day: *"you choose any
  player. The chosen player dies — mark them with the DEAD reminder."*
- The **Storyteller** judges truth, and the Storyteller chooses the victim. Tips:
  *"Prioritise choosing characters who will actually die rather than those protected by
  abilities."*

Edge cases and clarifications gathered from the page:

- **The statement must be public and unambiguous.** The Storyteller and every player must be
  able to hear and understand it, and be aware that the Gossip is using their ability. A
  vague or hedged claim ("I think Alice might be evil") does not qualify; a definite claim
  ("Alice is the Imp") does.
- **The Gossip does not wake at night.** There is no interaction with the Gossip player at
  all during the night — the whole step is Storyteller bookkeeping.
- **Impairment is judged at the moment the ability triggers, i.e. at night, not when the
  statement was made.** Wiki example: *"If the Gossip made a true statement during the day
  while drunk or poisoned, but is sober and healthy when their ability triggers that night,
  the Storyteller still kills a player."* The converse also holds — a healthy Gossip who is
  poisoned by nightfall kills nobody.
- **A Gossip who is dead when the ability would trigger does not kill.** Wiki example: the
  Gossip is killed by the Demon that night, so their earlier true statement causes no death.
  The Gossip's night-order slot (57) is after every Demon (36–54) and after the Assassin (55)
  and Godfather (56), so "did the Gossip survive to their own slot?" is a real check.
- **No kill on night 1** ("each night except the first" — there is no preceding day).
- The victim can be **any player**, including an evil player and including the Gossip
  themselves. The Storyteller should prefer a player who will actually die rather than
  burning the kill on a protected player.
- The Gossip **does not learn** whether their statement was judged true, other than by
  observing whether a death happened.

Jinxes: none for the Gossip in the official jinx list, and none in
`engine/src/main/resources/botc/data/night_and_jinxes.json`. **Works.**

Interactions worth naming (not on the Gossip page, but rule-derived):

- The death is **not a Demon kill**. It must be recorded with a non-Demon cause, otherwise
  the Grandmother would wrongly die alongside a Gossip-killed grandchild, and the
  Sage/Choirboy/Ravenkeeper "the Demon killed me" triggers would wrongly fire.
- Protection: Soldier is safe from the **Demon** only, so a Soldier *can* be Gossip-killed;
  Monk "Safe" likewise protects from the Demon only; Innkeeper "Protected", Sailor, Tea Lady,
  Fool, Lleech-host and Devil's Advocate (execution only) are the ones that actually stop it.
- Vortox does not affect the Gossip: the Gossip's ability produces a death, not information.
- Mathematician: a true statement by a Gossip who is drunk/poisoned at night is an ability
  that malfunctioned and counts for the Mathematician.

## What the app does today

Data and night order (correct):

- `engine/src/main/resources/botc/data/characters.json:431-441` — ability text matches the
  wiki verbatim; `otherNightReminder` = "If the Gossip's public statement was true: Choose a
  player not protected from dying tonight. That player dies."; reminders `["Dead"]`;
  no `firstNightReminder` (so the Gossip correctly has no first-night row).
- `engine/src/main/resources/botc/data/night_and_jinxes.json:430` — `gossip` sits at index 57
  of `otherNight`, after all Demons, Assassin (55) and Godfather (56). **Correct.**
- `engine/src/main/resources/botc/data/night_guide.json:246-251` — a genuinely good prose
  "how to run" for the other nights, including the drunk/poisoned caveat and "you choose who
  dies, and it can be any player, including the Gossip".

Everything else is manual:

- `engine/src/main/kotlin/com/clocktower/engine/NightOrder.kt:142-178` — the Gossip step is
  built by the generic path: a title, the `otherNightReminder` string, and the holder's name.
  Nothing is computed.
- `app/src/main/java/com/clocktower/grimoire/ui/screens/NightScreen.kt:462-522`
  (`QuickResolutions`) has hard-coded cases for `snakecharmer`, `fanggu`, `professor` and a
  generic Demon branch only. **There is no `gossip` case**, so the expanded Gossip step offers
  the guide prose and nothing else — no "was it true?", no victim picker, no kill button.
- `engine/src/main/kotlin/com/clocktower/engine/InfoCalc.kt:29-36` — `gossip` is not in
  `supports`, correctly (it is not an info role), so the info panel is absent too.
- To actually resolve the kill the storyteller must: leave the Night tab → Grimoire tab →
  tap the victim's seat → "Died at night" (`SeatSheet.kt:270-279`), which records
  `DeathCause.DEMON` — **the wrong cause** — then tap back, then place the Gossip "Dead"
  token from the night tray (`NightScreen.kt:283-295`).

There is **no facility anywhere in the app to record a public statement.** The only free-text
surfaces are:

- `GameState.storytellerNotes: String` (`GameState.kt:112`), edited through a single
  `AlertDialog` with one `OutlinedTextField` buried in the overflow menu
  (`GameShell.kt:685-706`). One undifferentiated blob for the entire game, no day stamps, no
  speaker, no truth flag, and it is not surfaced anywhere at night.
- `Player.note: String` (`GameState.kt:31`), a per-seat blob edited in the seat sheet.

`DayScreen.kt` (all 360 lines) is nominations and votes only — there is no place to type
"Bob said Carol is the Empath" during the day at all.

Note the contrast: the **player-notes** side of the app already has exactly the model the
storyteller side lacks — `NoteClaim(characterId, day)`, `NoteLink(from, to, kind, label, day)`
and `NoteInfo(day, text)` in `engine/src/main/kotlin/com/clocktower/engine/Notes.kt:17-48`.
None of it is available to the storyteller.

## Defects and gaps

1. **P0 · No way to record a Gossip statement, so the night step cannot be run correctly.**
   Rules require the Storyteller to remember, at night, exactly what the Gossip said publicly
   that day and to judge it true or false. The app offers no capture at all
   (`DayScreen.kt:1-360` has no notes UI; `GameShell.kt:685-706` is a single game-wide blob).
   Repro: play a BMR game with a Gossip; during Day 2 the Gossip announces "Dave is the
   Ravenkeeper"; open every tab — there is nowhere to write it. At the Gossip night step you
   get the sentence "If the Gossip's public statement was true: Choose a player…" and nothing
   else. This is the user's verbatim complaint ("Gossip was awful").

2. **P0 · The Gossip kill is recorded with the wrong death cause.** The only kill affordance
   is the seat sheet's "Died at night" button, which hard-codes `DeathCause.DEMON`
   (`SeatSheet.kt:270-273`). A Gossip kill is not a Demon kill. Consequences: a Gossip-killed
   Grandchild wrongly triggers the Grandmother death note (`StatusEffects.kt:122-127`), and a
   Gossip-killed Sage/Choirboy/King wrongly appear to have been Demon-killed
   (`StatusEffects.kt:96,102`). `DeathCause.OTHER_NIGHT_DEATH` exists (`GameState.kt:76`) but
   no UI path produces it. Repro: kill the Grandchild via the seat sheet at the Gossip step;
   the death dialog offers the Grandmother consequence, which is wrong.

3. **P1 · The Gossip step offers no victim picker, no protection check and no kill button.**
   Every other "someone dies tonight" step that the app knows about (Demons) gets
   `DemonKillPanel` with chips, `StatusEffects.deathNotes` and a confirm button
   (`NightScreen.kt:534-638`). The Gossip gets nothing (`NightScreen.kt:470-522`), even though
   the official instruction is explicitly "choose a player **not protected from dying
   tonight**" — precisely what `deathNotes` already computes.

4. **P1 · The Gossip's own liveness and impairment at the moment of trigger are not checked.**
   Rules: a Gossip who died earlier tonight (Demon at order 36–54, Assassin 55, Godfather 56 —
   all before the Gossip's 57) causes no death, and a Gossip drunk/poisoned *at night* causes
   no death even if the statement was true. The app never evaluates this; the step text is
   identical whether the Gossip is alive, dead, healthy or poisoned. `NightStepRow` only shows
   a generic "All holders are dead — usually skip" line (`NightScreen.kt:751-757`), which is
   actively misleading here because the reason and the timing matter.

5. **P1 · A recorded statement is never carried from day to night.** Even if the storyteller
   types into `storytellerNotes`, the night step does not display it. The whole point of the
   feature is the day→night handoff.

6. **P1 · No general "record what was said publicly" facility, which the user explicitly
   asked for even when the Gossip is not in play.** The same ledger is needed for Juggler
   guesses, Slayer shots, Artist questions, Fisherman advice, Savant pairs, Mutant madness
   breaches, Nightwatchman reveals, and ordinary character claims that the Storyteller must
   remember for Cerenovus/Pit-Hag/Vortox rulings. Nothing exists.

7. **P2 · No dawn announcement summary.** The DAWN step text is a static string ("Announce who
   died", `NightOrder.kt:59`) — it does not list who actually died tonight, so the storyteller
   who resolved a Gossip kill three steps earlier has to remember it. Cross-cutting, but the
   Gossip is the case where the death has no other on-screen trace.

8. **P2 · The Gossip "Dead" token is not placed automatically.** Physically the token marks
   which death was the Gossip's. It has to be dragged from the tray by hand
   (`NightScreen.kt:283-295`).

9. **P3 · `night_guide.json` says "choose any player who is not protected from dying
   tonight"** — slightly stronger than the almanac, which says the Storyteller *should
   prefer* an unprotected player. If every player is protected, the rule is that nobody dies;
   the guide implies a legal choice always exists.

## Proposed behaviour (spec)

### A. The day ledger (new, general — not Gossip-specific)

New engine types in `GameState.kt`:

```kotlin
@Serializable
enum class StatementTruth { UNJUDGED, TRUE, FALSE }

@Serializable
enum class StatementKind {
    CLAIM,        // "I am the Empath"
    GOSSIP,       // the Gossip's daily public statement
    JUGGLER,      // day-1 juggle guesses
    SLAYER,       // a Slayer shot
    ARTIST, FISHERMAN, SAVANT, NIGHTWATCHMAN,
    MADNESS,      // Cerenovus/Mutant/Harpy compliance
    OTHER,
}

@Serializable
data class PublicStatement(
    val id: Long,
    /** state.cycle on the day it was said. */
    val day: Int,
    /** Seat that said it; null for a Storyteller memo. */
    val speakerId: Long? = null,
    val kind: StatementKind = StatementKind.OTHER,
    /** The statement as the ST wants to remember it. */
    val text: String,
    /** Seats the statement is about — powers "about X" filtering. */
    val subjectIds: List<Long> = emptyList(),
    /** Characters named in the statement (for Juggler/claim tracking). */
    val characterIds: List<String> = emptyList(),
    val truth: StatementTruth = StatementTruth.UNJUDGED,
    /** Night cycle that consumed this statement, once resolved. */
    val resolvedOnNight: Int? = null,
)
```

`GameState` gains `val statements: List<PublicStatement> = emptyList()` and
`val nextStatementId: Long = 1`. Both default so existing saves deserialise unchanged.

New `GameActions`:

- `recordStatement(state, speakerId, kind, text, subjectIds, characterIds): GameState` —
  stamps `day = state.cycle`, allocates `id = nextStatementId`, increments it.
- `editStatement(state, id, transform: (PublicStatement) -> PublicStatement)`
- `setStatementTruth(state, id, truth)`
- `resolveStatement(state, id, night)` — sets `resolvedOnNight`.
- `deleteStatement(state, id)`
- `statementsFor(state, day, kind = null, speakerId = null): List<PublicStatement>` (query
  helper used by night steps).

Both view models need matching wrappers: `app/src/main/java/com/clocktower/grimoire/ui/GameViewModel.kt:194-223`
**and** `web/src/wasmJsMain/kotlin/com/clocktower/grimoire/ui/WebGameViewModel.kt` (the web
build has a parallel hand-written `GameViewModel`; `web/build.gradle.kts:50-51` compiles the
same `app/` screens against it, so any new wrapper must be added twice or the PWA won't
compile).

### B. Day-tab UI for the ledger (phone-first, always present)

`DayScreen.kt` gets a **"What was said today"** card, placed above "New nomination" (it is
consulted more often than the nomination form and must not be below the fold):

- A single always-visible composer row: `[speaker chip row — horizontally scrolling seats,
  alive first] [text field, placeholder "…said what?"] [Add]`.
  Two taps and a short sentence. No dialog, no menu dive.
- Optional second row, collapsed by default: `[About: seat chips] [Character: picker]
  [Kind: chips]`. Kind defaults to `CLAIM`, or to `GOSSIP` when the selected speaker holds
  the Gossip and no Gossip statement is recorded for today yet.
- Below it, today's statements as compact rows:
  `Bob » "Carol is the Empath"   [true] [false] [?]  ✎  🗑`
  The truth tri-state chips are only rendered for kinds where truth matters
  (`GOSSIP`, `JUGGLER`, `SLAYER`, `SAVANT`); other kinds show a plain row.
- A "Previous days" expander showing earlier days grouped by day number.
- The Gossip banner: when a living Gossip is in play and today has no `GOSSIP` statement, the
  card shows one imperative line — *"No Gossip statement recorded today."* with an
  **[Record Gossip statement]** button that pre-selects the Gossip's seat and kind.

Also add an entry point from the circle: `SeatSheet.kt` gains a
**"Record what they said"** button next to "Add reminder" (`SeatSheet.kt:309-315`) that opens
the same composer with `speakerId` pre-filled. This is the fast path when the storyteller is
already looking at the seat.

Statements must appear in the game log (`GameExtras.kt:46-106`) as
`D<n>  Bob said "Carol is the Empath" (true)`.

### C. The Gossip night step

- **when:** other nights only (`firstNightReminder` is empty — already correct).
  The step row is always built (it is Storyteller bookkeeping, the Gossip is not woken), but
  the panel resolves as below.
- **targets:** 1, chosen by the Storyteller, only if the ability fires. Any player, alive or
  dead, including the Gossip. Sort the picker: alive-and-unprotected first, then
  alive-but-protected (greyed with the reason), then dead (disabled).
- **wake condition / gating,** evaluated in this order and rendered as a single decision
  panel:

  1. `statement = statements.lastOrNull { it.day == cycle - 1 && it.kind == GOSSIP }`
     - none → panel shows *"No Gossip statement was recorded for Day ${cycle-1}."* with
       **[Record one now]** (opens the composer, back-dated to `cycle - 1`) and
       **[The Gossip said nothing]** (ticks the step done, no death).
  2. `statement.truth == UNJUDGED` → panel shows the statement verbatim in large type, the
     speaker, the subject seats as chips with their true characters visible, then
     **"Was it true?" [TRUE] [FALSE]**. Recording the answer writes back with
     `setStatementTruth`, so the judgement is preserved for the log.
  3. `truth == FALSE` → *"False statement — nobody dies from the Gossip tonight."* Step
     is tickable, nothing else offered.
  4. `truth == TRUE` but the Gossip seat is `!alive` → *"${name} died earlier tonight — the
     Gossip's ability does not trigger."* No kill. (Cite the wiki example.)
  5. `truth == TRUE` but `StatusEffects.isImpaired(state, lookup, gossipSeat)` →
     *"${name} is drunk/poisoned now — the ability does not trigger even though the
     statement was true. (Impairment is judged now, not when they spoke.)"* Offer a
     **[Kill anyway]** override for the Storyteller who wants to hide the malfunction, but
     default to no kill. Also emit a Mathematician note if a Mathematician is in play.
  6. otherwise → **"True statement — choose who dies."** Victim chips as above; on selection
     render every `StatusEffects.deathNotes(state, lookup, targetId)` line in red exactly as
     `DemonKillPanel` does (`NightScreen.kt:588-590`), plus a **[${name} dies]** confirm and a
     **[Nobody can die — all protected]** escape.

- **immediate effects on confirm** (one undoable `update`):
  - `GameActions.kill(state, targetId, DeathCause.OTHER_NIGHT_DEATH, lookup)` — **never
    `DeathCause.DEMON`.**
  - `GameActions.placeExclusiveReminder(state, targetId, PlacedReminder("gossip", "Dead"))`.
  - `GameActions.resolveStatement(state, statementId, night = state.cycle)`.
  - `GameActions.toggleNightStep(state, "gossip")` (auto-tick the step).
- **deferred effects:** none. The death is immediate and announced at dawn.
- **expiry:** the `gossip:Dead` token is a permanent record of how that player died and must
  **not** be in `EXPIRES_AT_DAWN`/`EXPIRES_AT_DUSK` (`GameActions.kt:218-242`). Statements are
  never auto-deleted; they are the game record.
- **information:** none is given to the Gossip. No show cards.
- **visibility:** nothing is shown to the Demon, Minions or Lunatic about this.
- **day-time inputs the app must let the ST record:** the ledger above. The night step
  consumes exactly the newest `kind == GOSSIP` statement whose `day == cycle - 1`.
- **interactions:**
  - Grandmother: because the cause is `OTHER_NIGHT_DEATH`, a Gossip-killed Grandchild must
    **not** kill the Grandmother. See `grandmother.md`.
  - Fool / Sailor / Tea Lady / Innkeeper "Protected" / Lleech host / Zombuul first death —
    all already produce `deathNotes`; the victim picker must surface them before the kill.
  - Soldier and Monk "Safe" protect from the **Demon only** and must be shown as *not*
    protecting here. `deathNotes` currently phrases the Monk token as "protected from the
    Demon" and the Soldier as "safe from the Demon" (`StatusEffects.kt:66,74`), which is
    correct wording — the panel should render them in a muted colour, not red, for a
    non-Demon kill.
  - Lycanthrope: no interaction (its jinx is with the Gambler).

### D. UI text the step should display (storyteller voice)

- Header: `Gossip — did today's statement come true?`
- No statement: `Nothing recorded for Day 2. Record what the Gossip said, or skip.`
- Judging: `Nate said: "Dorian is the Professor."  Was it true?`
- True, ready: `True. Choose who dies tonight — prefer someone who isn't protected.`
- False: `False. No one dies from the Gossip tonight.`
- Gossip dead: `Nate died earlier tonight. The Gossip's ability does not trigger.`
- Gossip impaired: `Nate is poisoned right now. No death — even though the statement was true.`
- After the kill: `Dorian dies (Gossip). Announce it at dawn.`

### E. Data changes

- `characters.json:431-441` — no change needed; ability text matches the wiki.
- `night_guide.json:246-251` — soften "choose any player who is not protected from dying
  tonight" to "choose any player; prefer one who isn't protected, so the kill isn't wasted. If
  everyone is protected, no one dies." Add the explicit sentence: *"Judge drunkenness and
  death now, at this step — not when the statement was made."*
- `night_and_jinxes.json` — no change (position 57 is correct).

## Tests to add

`engine/src/test/kotlin/com/clocktower/engine/` — all fail today because the types and
resolvers do not exist.

1. **Ledger round-trip.** Given a BMR game on Day 2, When
   `GameActions.recordStatement(state, speakerId = 3, kind = GOSSIP, text = "Dorian is the
   Professor")`, Then `state.statements` has one entry with `day == 2`, `truth == UNJUDGED`,
   and `state.nextStatementId == 2`. And after `advancePhase()` to Night 3 the statement is
   still present (statements never expire).

2. **Statement lookup targets the preceding day.** Given statements recorded on days 1, 2 and
   3, When the state is Night 3 (`cycle == 3`, `phase == NIGHT`), Then the Gossip resolver
   selects the `GOSSIP` statement with `day == 2`, not day 1 or 3.

3. **True statement kills with the right cause.** Given a Gossip whose Day-2 statement is
   marked `TRUE`, When the Gossip resolution kills seat 5, Then seat 5 is dead, the newest
   `DeathRecord.cause == DeathCause.OTHER_NIGHT_DEATH` (**not** `DEMON`), seat 5 carries
   `PlacedReminder("gossip", "Dead")`, and the statement's `resolvedOnNight == 3`.

4. **Gossip-killed Grandchild does not kill the Grandmother.** Given a Grandmother with the
   `grandmother:Grandchild` token on seat 5, When seat 5 dies with cause
   `OTHER_NIGHT_DEATH`, Then the Grandmother-death consequence does **not** fire (and, once
   `grandmother.md`'s spec lands, `GameActions` does not kill the Grandmother).

5. **Dead Gossip does not fire.** Given a true Day-2 statement and a Gossip killed by the
   Demon earlier on Night 3, When the Gossip step is evaluated, Then the resolver reports
   "does not trigger" and offers no victim picker.

6. **Poisoned-at-night Gossip does not fire; poisoned-by-day one does.** Given a true
   statement made while the Gossip carried `poisoner:Poisoned`, and the token removed at dusk
   (`EXPIRES_AT_DUSK`, `GameActions.kt:232`), When the Gossip step is evaluated on the
   following night, Then the ability **fires**. Conversely, given a healthy Gossip poisoned
   during the night, Then it does **not** fire.

7. **False statement produces no death.** Given `truth == FALSE`, Then the resolver offers no
   victim picker and `state.deaths` is unchanged after ticking the step.

8. **No first-night Gossip step.** Given a BMR game with a Gossip, When
   `nightOrder.firstNight(state, lookup)` is built, Then no step has `id == "gossip"`.
   (This passes today — keep it as a regression guard.)

9. **Statements survive serialisation.** Given a state with statements, When it round-trips
   through the save `Json`, Then the statements and `nextStatementId` are preserved, and a
   save written before the field existed still deserialises (default `emptyList()`).
