# Slayer (slayer) — Trouble Brewing Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Slayer> (raw wikitext fetched 2026‑08‑25).

**Current ability text (verbatim):**
> "Once per game, during the day, publicly choose a player: if they are the Demon, they die."

**Summary bullets (verbatim):**
- "The Slayer can choose to use their ability at any time during the day, and must declare to
  everyone when they're using it. If the Slayer chooses the Demon, the Demon dies
  immediately. Otherwise, nothing happens."
- "The players do not learn the identity of the dead player. After all, it may have been the Recluse!"
- "A Slayer that uses their ability while poisoned or drunk may not use it again."
- "The Slayer will want to choose an alive player. Even if the Slayer chooses a dead Imp,
  nothing happens, because a dead player can't die again."
- "Players may say whatever they want at any time, so a player who's pretending to be the
  Slayer may pretend to use the Slayer ability."

**How to Run (verbatim):**
> "During the day, the Slayer can declare that they wish to use their ability. If so, the
> Slayer points at any player. If the chosen player is an alive Demon, declare that the chosen
> player dies—put a shroud on their character token in the Grimoire. If the chosen player is
> not an alive Demon, say "Nothing happens." Either way, the Slayer loses their ability—put the
> Slayer's **NO ABILITY** reminder token by the Slayer token.
>
> If a player is bluffing as the Slayer and declares they wish to use their ability, act as if
> they were indeed the Slayer—allow time for discussion, let them make the decision, and act
> like you're fiddling with tokens in your Grimoire, then say "Nothing happens."
>
> When the Slayer declares that they wish to use their ability, give the group a minute or two
> to discuss who the Slayer should choose."

**Examples (verbatim):**
- "The Slayer chooses the Imp. The Imp dies, and good wins!"
- "The Slayer chooses the Recluse. The Storyteller decides that the Recluse registers as the
  Imp, so the Recluse dies, but the game continues."
- "The Imp is bluffing as the Slayer. They declare that they use their Slayer ability on the
  Scarlet Woman. Nothing happens."

**Storyteller-relevant clarifications:**
- Recluse: "Your ability may work on the Recluse, since the Recluse might register as the
  Demon." — **ST's choice**, decided at the moment of the shot.
- Scarlet Woman: "if fewer than five players are alive, then the Scarlet Woman cannot become
  the Demon after you kill it." → a successful slay with 5+ alive hands the Demon to the
  Scarlet Woman rather than ending the game.
- Imp change of hands: "Be aware that the Imp may change players throughout the game."
- The result is announced *publicly* by the ST ("Nothing happens" / "<player> dies"), but the
  dead player's character is **not** revealed.
- A dead Slayer has no ability, so a shot after death does nothing (and a Slayer who used the
  shot is spent forever, including through resurrection).

**Jinx (verbatim):**
- Lleech: "If the Slayer slays the Lleech host, the host dies."

## What the app does today

**Nothing. `grep -rn "slayer" app/src engine/src/main` returns exactly one hit outside data
files** (`SetupScreen.kt:575`, an unrelated placeholder string). The Slayer has no code path
of any kind.

Data
- `engine/src/main/resources/botc/data/characters.json:109-122` — ability text matches the
  wiki; `reminders: ["No ability"]`; both night reminders empty.
- `engine/src/main/resources/botc/data/night_and_jinxes.json` — `slayer` is absent from both
  `firstNight` and `otherNight` (correct: it never wakes) and carries one jinx
  (`lleech`↔`slayer`, text matches the wiki).
- `engine/src/main/resources/botc/data/night_guide.json` — **no `slayer` entry at all**. Since
  `NightGuide.forStep` is only consulted from the night sheet (`NightScreen.kt:792`), there is
  nowhere in the app that explains how to run a Slayer shot.

Consequence: because the Slayer never appears as a night step, it never becomes the
`activeCharacter` of the `NightToolTray` (`NightScreen.kt:98-100, 202-205`), so the
tray's generic **"Mark spent"** chip (`NightScreen.kt:263-279`, gated on
`ability.startsWith("Once per game")` — which the Slayer's text does satisfy) is
**unreachable** for the Slayer.

The storyteller's actual experience today, end to end:
1. Slayer declares a shot at the table. The app shows nothing, prompts nothing, records nothing.
2. ST opens Grimoire → the target's seat → reads the character name off `SeatActions`
   (`SeatSheet.kt:179-198`) to decide if it is the Demon. If it is the Recluse, the ST decides
   registration entirely in their head.
3. If the target dies, the ST taps **"Other death"** (`SeatSheet.kt:277-279`,
   `DeathCause.STORYTELLER`) — there is no "slain" cause. `StatusEffects.deathNotes`
   (`StatusEffects.kt:104-109`) does at least warn "Scarlet Woman becomes the Demon (5+ alive)"
   and "Imp self-kill…" before the confirmation.
4. ST then navigates to the **Slayer's** seat → "Add reminder" (`SeatSheet.kt:314`) →
   `ReminderPicker` (`SeatSheet.kt:492-571`) → scroll to Slayer → tap "No ability".
   Five taps and two screens, entirely from memory, with nothing stopping them from forgetting.
5. Nothing prevents a second shot later: no state marks the Slayer as spent except the token
   the ST may or may not have placed, and nothing reads that token.
6. The game log (`GameExtras.kt:44-106`) records only deaths and nominations — a Slayer shot
   that missed leaves no trace at all, so "who has already been slain-and-survived" (a real
   piece of public information the good team relies on) is lost.
7. `WinCheck.check` (`WinCheck.kt:70-86`) does fire the "Every Demon is dead — good wins"
   advisory with the Scarlet Woman / star-pass cautions once the ST kills the Demon, so the
   *end* of a successful slay is handled. That is the only part that works.

Works: the win advisory after a successful slay; the Scarlet Woman death note; the jinx text.

## Defects and gaps

1. **P0 · No way to record or resolve a Slayer shot** — the single most public, most
   game-deciding day action in Trouble Brewing has zero UI. Everything (is the target the
   Demon? does the Recluse register? does the target die? is the ability spent?) is manual.
   *Repro:* start a TB game with a Slayer, reach Day 1, open the Day tab. There is no Slayer
   affordance anywhere in the app.

2. **P0 · The "spent" mark is unreachable through the intended affordance** — the generic
   once-per-game "Mark spent" chip exists at `NightScreen.kt:263-279` but is only rendered
   inside `NightToolTray`, whose `character` comes from the expanded **night** step
   (`NightScreen.kt:98-100`). The Slayer has no night step, so the chip can never appear.
   The ST must instead hand-place `PlacedReminder("slayer", "No ability")` from the seat sheet.

3. **P0 · Nothing enforces once-per-game** — no state, no guard, no warning. A ST who
   forgets the token will happily let a Slayer shoot twice. The rules are explicit that the
   ability is spent even when the Slayer is drunk/poisoned and even when the shot misses.

4. **P1 · Recluse registration is not offered** — the Slayer working on the Recluse is one of
   the wiki's three canonical examples and is a pure ST decision, but there is no prompt, no
   record, and no consistency check against how the Recluse registered to other characters
   this game. `InfoCalc.misregistrations` (`InfoCalc.kt:121-130`) knows how to *mention* the
   Recluse but is only reachable from night info steps.

5. **P1 · No "alive Demon" check** — the rules require the target to be an **alive** Demon
   ("Even if the Slayer chooses a dead Imp, nothing happens"). The app knows `alive` and
   `team` for every seat and could answer instantly; instead the ST eyeballs the grimoire.

6. **P1 · No death cause for a slay** — `DeathCause` (`GameState.kt:67`) has
   `EXECUTION, DEMON, OTHER_NIGHT_DEATH, EXILE, STORYTELLER`. A day-time slay must be recorded
   as `STORYTELLER`, which then reads "died (storyteller)" in the log (`GameExtras.kt:58`) and,
   crucially, is **not** an execution — correct for the Undertaker, but indistinguishable from
   a Gunslinger kill, a Witch curse death, or an ST fiat death.

7. **P1 · Missing from the day-start briefing (which does not exist)** — a Slayer who has not
   yet shot is a standing fact the ST must hold in their head every day
   ("the Slayer still has their shot"; "the Slayer publicly claimed today, so expect a
   Poisoner move tonight"). `GameShell.kt:126-168` advances DAY with no briefing whatsoever.

8. **P1 · Bluffed Slayer shots cannot be recorded** — the How to Run explicitly tells the ST
   to play along with a fake Slayer. The ST needs a record of *who publicly claimed Slayer and
   shot whom*, because that is the information the whole table is reasoning from and it drives
   the evil team's next move. Today the only place to write it is the free-text
   `storytellerNotes` (`GameState.kt:118`) or a seat note (`SeatSheet.kt:366-372`).

9. **P2 · No `night_guide` / reference entry** — a first-time ST running the Slayer gets no
   in-app instructions at all, not even the "give the group a minute to discuss" and
   "act like you're fiddling with tokens" coaching. Every other TB character with a night
   action has a guide entry (`night_guide.json`).

10. **P2 · Lleech jinx is data-only** — `night_and_jinxes.json` has the text, surfaced in
    `SeatSheet.kt:222-234`, but a Slayer resolution panel would need to apply it (slaying the
    host kills the host, which kills the Lleech).

11. **P3 · Poisoned/drunk Slayer gives no signal** — the shot is spent and nothing happens;
    the ST should be reminded that the Slayer is impaired *before* they announce the result,
    so they say "Nothing happens" with the right face.

## Proposed behaviour (spec)

The Slayer needs a **day action panel**, a new surface the app does not yet have. Model it as
a generic "day abilities" section on `DayScreen` listing every in-play character with a
day-time ability (Slayer, Gossip, Juggler, Artist, Fisherman, Mayor, Savant, Mutant, …), each
with its own resolver — mirroring what `QuickResolutions` (`NightScreen.kt:461-525`) does for
the night.

**Structured behaviour**

- **when:** DAY phase, any time (including while the Slayer is on the block). Not SETUP, not
  NIGHT. Available for **every** seat, not just the real Slayer, because bluffs must be run
  identically (see "bluff mode" below).
- **wake condition (availability):** the real Slayer's resolver is *live* when the holder is
  `alive` and has no `PlacedReminder(sourceId = "slayer", label = "No ability")`. When either
  is false the panel still opens but is pre-set to "Nothing happens" and says why, privately.
- **targets:** exactly 1, any seat. Picker sort: alive first (seat order), dead below with `†`
  and a hint "a dead player can't die again — nothing happens". Self allowed.
- **immediate effects, in one confirmed action:**
  1. Decide the outcome:
     - target is `alive` **and** `character.team == DEMON` → **hit**.
     - target is `alive` **and** `characterId == "recluse"` → present a *Recluse registration*
       choice: "registers as the Demon (dies)" / "registers as itself (nothing happens)",
       defaulting to the previously recorded registration for this Recluse if any.
     - target is `alive` and is the **Lleech host** (holds `PlacedReminder("lleech", "Poisoned")`
       — check the Lleech's actual host token label in data) → hit, per the jinx: the host dies.
     - otherwise → **miss**.
     - Impaired shooter (`StatusEffects.isImpaired(state, lookup, slayer)`, or `characterId ==
       "drunk"`, or a `"No ability"` token) → **forced miss**, with a private banner
       "The Slayer is drunk/poisoned — say 'Nothing happens'. The shot is still spent."
  2. On a hit: `GameActions.kill(state, targetId, DeathCause.SLAIN, lookup)` (new cause; see
     data changes) **after** running `StatusEffects.deathNotes` in a confirmation dialog
     identical to `SeatSheet.kt:288-307`, so Scarlet Woman / star pass / Lleech notes are seen.
  3. Always: `GameActions.placeExclusiveReminder(state, slayerId, PlacedReminder("slayer",
     "No ability"))` — spent regardless of outcome, regardless of impairment, regardless of
     hit or miss. This must be part of the *same* undoable transaction as the kill.
  4. Always: append a `DayAction` record (new; see below) so the log and future days show it.
- **deferred effects:** on a hit that kills the Demon, immediately run `WinCheck.check` and
  show the existing advisory (`GameShell.kt:506-518`) with its Scarlet Woman / Mastermind /
  star-pass cautions. On a Scarlet Woman takeover, prompt for the new Demon seat the same way
  `DemonKillPanel`'s star-pass flow does (`NightScreen.kt:591-622`).
- **expiry:** the `slayer:"No ability"` token **never** expires — it must not be added to
  `EXPIRES_AT_DAWN` or `EXPIRES_AT_DUSK` (`GameActions.kt:218-242`). It survives resurrection
  (`GameActions.resurrect` does not touch reminders — correct).
- **information:** the ST announces publicly, so the panel's copy is the announcement:
  hit → `"<Target> dies."` (no character revealed); miss → `"Nothing happens."` Offer both as
  a full-screen `ShowCard.Message` for a noisy room.
- **visibility:** nothing is shown to any player privately. The Demon learns only what the
  table learns.
- **day-time inputs the app must let the ST record:** (a) who publicly claimed Slayer,
  (b) who they shot, (c) whether it was the real Slayer or a bluff, (d) the announced result.
  A **bluff mode** toggle on the panel resolves as "Nothing happens" without touching any
  seat's tokens, but still writes the day-action record — this is what lets the ST answer
  "wait, has that player already claimed Slayer?" three days later.
- **interactions/jinxes to handle explicitly:**
  - **Recluse** — registration choice, recorded and reused (see Ravenkeeper spec for the
    shared `Registered: <Character>` token).
  - **Spy** — the Spy registers as good/Townsfolk, never as the Demon, so a shot on the Spy is
    always a miss. Say so in the panel rather than leaving the ST to reason about it.
  - **Scarlet Woman** — 5+ alive at the moment of the slay → she becomes the Demon; fewer than
    5 → good wins. The panel must state the current alive count.
  - **Imp star pass** — if the Demon token has moved, the app already reads live `characterId`.
  - **Lleech** — apply the jinx.
  - **Drunk shown as Slayer** — `characterId = "drunk"`, `shownCharacterId = "slayer"` must get
    the panel (players believe they are the Slayer) and always miss; place the "No ability"
    token on that seat too so the ST is not asked twice.
  - **Mayor** — irrelevant (bounce is a night ability), but note that a slain Demon does not
    trigger the Mayor.

**UI text the panel should display**

- Header: `"Slayer shot — <Name>"` with a subline `"Once per game · <spent | available>"`.
- Target list header: `"Who did they publicly choose?"`
- Impaired banner: `"<Name> is drunk/poisoned — the shot fails but is still used up."`
- Result buttons: `"<Target> dies"` / `"Nothing happens"` — the correct one pre-selected and
  the other greyed with the reason (`"not the Demon"`, `"already dead"`).
- Confirmation footer: `"Marks the Slayer NO ABILITY."`

**Data / file changes**

- `characters.json:109-122` — no text change needed. Optionally add
  `"dayAbility": {"kind": "publicChoice", "oncePerGame": true}` so a generic day-action engine
  can find it.
- `night_guide.json` — add a `slayer` entry with a new `"day"` section (requires extending
  `NightGuideEntry`, `NightGuide.kt:36-40`, with a `day: GuideNight?`), containing the How to
  Run text above plus the "act like you're fiddling with tokens" coaching and two prepared
  `ShowCard.Message` shows: "Nothing happens." and "<player> dies."
- `GameState.kt:67` — add `DeathCause.SLAIN` (and render it in `GameExtras.kt:53-59` and
  `SeatSheet`). Keep it distinct from `EXECUTION` so the Undertaker is unaffected.
- `GameState.kt` — add a `dayActions: List<DayAction>` list
  (`DayAction(day, actorId, kind, targetId?, text, bluff: Boolean)`) so Slayer shots, Gossip
  statements, Juggler guesses, Artist questions etc. share one record; render it in
  `GameExtras.GameLogDialog`.

## Tests to add

1. **Hit the Demon**
   Given a TB game on DAY 2, Slayer alive and unspent, Imp alive
   When the Slayer shot resolver targets the Imp
   Then the Imp is dead with `DeathCause.SLAIN`, and the Slayer holds
   `PlacedReminder("slayer", "No ability")`.

2. **Miss a Townsfolk — still spent**
   Given the same setup, targeting the Chef
   Then nobody dies **and** the Slayer holds `PlacedReminder("slayer", "No ability")`.

3. **Poisoned Slayer hits the Demon — nothing happens, still spent**
   Given the Slayer holds `PlacedReminder("poisoner", "Poisoned")`
   When they target the Imp
   Then the Imp is **alive** and the Slayer is marked "No ability".

4. **Drunk-as-Slayer**
   Given a seat with `characterId = "drunk"`, `shownCharacterId = "slayer"`
   When they shoot the Imp
   Then the Imp lives and the *drunk seat* is marked "No ability".

5. **Dead Demon target**
   Given the Imp is already dead
   When the Slayer targets it
   Then nothing happens and the Slayer is spent.

6. **Second shot is refused**
   Given the Slayer already holds `PlacedReminder("slayer", "No ability")`
   When the resolver is queried
   Then it reports `available = false` and any outcome is a forced miss.

7. **Spent survives resurrection**
   Given a spent Slayer who dies and is resurrected via `GameActions.resurrect`
   Then the `"No ability"` token is still on their seat.

8. **Spent survives dawn and dusk**
   Given a spent Slayer, when `advancePhase` runs through DAY→NIGHT→DAY
   Then the `"No ability"` token is still present (guards against it ever being added to
   `EXPIRES_AT_DAWN` / `EXPIRES_AT_DUSK`).

9. **Recluse registers as the Demon**
   Given an alive Recluse and a registration choice of "Demon"
   When the Slayer targets the Recluse
   Then the Recluse dies (`DeathCause.SLAIN`) and `WinCheck.check` returns **null**
   (the real Demon still lives).

10. **Scarlet Woman takeover**
    Given 6 alive players including an Imp and a Scarlet Woman
    When the Slayer slays the Imp
    Then `WinCheck.check` returns `goodWins = true` **with** the caution
    "Scarlet Woman: with 5+ players alive she becomes the Demon instead."
    *(passes today via WinCheck; locks the interaction to the new resolver.)*

11. **Bluff shot leaves the grimoire untouched**
    Given the Imp declares a fake Slayer shot on the Scarlet Woman with `bluff = true`
    Then no seat gains or loses a reminder, nobody dies, and a `DayAction` is recorded.
