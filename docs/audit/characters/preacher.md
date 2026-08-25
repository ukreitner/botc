# Preacher (preacher) — Experimental Townsfolk

## Official rules (sources)

Source: https://wiki.bloodontheclocktower.com/Preacher (fetched 2026-08-25).

Current ability text (matches `characters.json` exactly — no drift):

> "Each night, choose a player: a Minion, if chosen, learns this. All chosen Minions have no ability."

How to Run (quoted verbatim):

> "Each night, wake the Preacher. They point to a player. Put the Preacher to sleep. If the Preacher choose a Minion, mark that Minion with a **NO ABILITY** reminder, wake the Minion and show them the **THIS CHARACTER SELECTED YOU** info token then the Preacher token, then put them to sleep.
>
> All Minions marked **NO ABILITY** have no ability **while the Preacher is alive**."

Clarifications (verbatim):
- "If the Preacher chooses a Minion, that Minion is woken to learn that they have been preached."
- "If the Preacher becomes drunk or poisoned, preached Minions regain their abilities until the Preacher is sober and healthy."
- "The Preacher may choose dead players."
- Choosing a non-Minion: nothing happens (nobody is woken, no token placed).

Consequences that follow from the quoted rules and matter for implementation:
- The effect is **cumulative and permanent-ish**: every Minion ever preached by a sober, healthy, living Preacher stays ability-less. There is no "different from last night" constraint and no requirement to re-preach.
- The effect is **conditional on the Preacher's state**: it lapses while the Preacher is drunk/poisoned, and **ends when the Preacher dies** ("while the Preacher is alive"). The tokens stay on the board; they simply stop applying.
- The choice made **while the Preacher is impaired** has no effect at all — that Minion is not woken and gains no token.
- **Marionette:** the Marionette page states "The Marionette is not woken due to character abilities that would confirm that they are a Minion eg. Snitch, **Preacher**, Lil' Monsta, Poppy Grower, Hatter, Damsel." So a preached Marionette loses their ability but is **not woken** and learns nothing.

Jinxes (wiki, exact text):
- **Legion:** "If the Preacher chooses Legion, Legion keeps their ability, but the Preacher might learn they are Legion."
- **Summoner:** "If the living Summoner has no ability, the Storyteller has the Summoner ability."
- **Vizier:** "If the Vizier loses their ability, they learn this, and cannot die during the day."

Not addressed on the wiki (flagged, not guessed): whether a Minion chosen a **second** time is woken again; whether a preached Minion who later becomes a different Minion (Pit-Hag) keeps the NO ABILITY effect. My reading of "All chosen **Minions** have no ability" is that the effect follows the *player*, and lapses if they stop being a Minion — but the wiki does not say so.

## What the app does today

Data / order:
- `characters.json:1535` — ability text current; both night reminders identical and correct; `reminders: ["No Ability", "No Ability", "No Ability"]` (three copies, matching the physical token set).
- `night_and_jinxes.json:318` (first night slot 23, right after `DEMON_INFO`) and `:384` (other night slot 11). Both correct.
- `night_guide.json:1175` — good, accurate prose for both nights, including the impaired case, with a prepared show card `{"label":"To Minion","kind":"token","text":"THIS CHARACTER SELECTED YOU","token":"self"}` (the `self` token resolves to the Preacher's own token — correct).
- **No Preacher jinxes in `night_and_jinxes.json` at all.** All three (Legion, Summoner, Vizier) are missing.

Runtime:
- `NightOrder` emits a plain prose row both nights (`NightOrder.kt:144-181`). No target picker, no Minion list, no automation.
- `InfoCalc.supports` (`InfoCalc.kt:29-35`) does not include `preacher` — correct in the sense that the Preacher receives no information, but it also means the step gets **no impairment caveat** and no computed "these seats are Minions" helper.
- `NightScreen.QuickResolutions` (`NightScreen.kt:465-525`) has hard-coded resolvers only for `snakecharmer`, `fanggu`, `professor` and the generic Demon kill. The Preacher falls into the `else ->` branch and gets nothing.
- Token placement is manual via `NightToolTray` (`NightScreen.kt:186-355`). Because `availableCopies == 3`, the tray's multi-copy path is used (`NightScreen.kt:318-345`): the first three placements accumulate, and a **fourth placement silently removes the token from `placed.first()`** — i.e. it silently un-preaches the earliest Minion, with no warning.
- **`No ability` is read in only four places**, none of which is the night order: `StatusEffects.kt:75` (Fool), `StatusEffects.kt:154` (Virgin), `NightScreen.kt:264` and `:504` (once-per-game "Mark spent" and the Professor). `InfoCalc.impairments` (`InfoCalc.kt:150`) does add a caveat `"<name> has no ability (<source>)."` — but only for the *info holder*, so it never fires for a preached Poisoner.
- Nothing removes or suspends the tokens when the Preacher dies or is poisoned. Nothing is in `EXPIRES_AT_DAWN`/`EXPIRES_AT_DUSK` for `preacher` — correct as a default, wrong as the whole story.

Storyteller experience today: read prose → tap "» To Minion" → show the card → open the tray → tap "No Ability" → tap the Minion's seat. Then, forever after, remember that the Poisoner's night step is a no-op; remember to start honouring it again if the Preacher gets poisoned; remember to stop honouring it when the Preacher dies. The night sheet keeps telling you to wake the preached Poisoner every single night.

## Defects and gaps

1. **P0 · A preached Minion's night step is not suppressed.** `NightOrder.build` (`NightOrder.kt:144-181`) has no notion of "no ability". Repro: place `preacher:No Ability` on the Poisoner's seat; go to Night 3 — the Poisoner row is present, unmarked, and reads "The Poisoner chooses a player…", and the dawn guard (`GameShell.kt:146-160`) refuses to advance until you tick it. The ST is actively led to run an ability that does not exist.
2. **P0 · Preached Minions do not regain their ability when the Preacher dies.** The rule is explicit ("while the Preacher is alive"). Nothing in the engine reacts to the Preacher's death; the tokens sit there and the ST must remember. `StatusEffects.deathNotes`'s `when (id)` (`StatusEffects.kt:95-104`) has no `"preacher"` case. Repro: preach the Poisoner on night 2, execute the Preacher on day 3 — no note anywhere, and nothing changes.
3. **P1 · Preached Minions do not regain their ability while the Preacher is drunk/poisoned.** Same missing mechanism, opposite direction. Repro: preach the Baron, then place `poisoner:Poisoned` on the Preacher — nothing changes anywhere in the UI.
4. **P1 · No target recording and no automatic token placement.** The Preacher chooses a player every single night; the app never asks who, so it cannot tell whether the choice was a Minion, cannot place the token, cannot decide whether to wake the target, and has no history of who was preached when. Every other "choose a player" character in this scope has the same gap, but the Preacher's is the most damaging because the consequence is permanent. `NightScreen.kt:465-525`.
5. **P1 · No prompt to wake the chosen Minion.** The two-token reveal ("THIS CHARACTER SELECTED YOU" then the Preacher token) is a card the ST must find and fire manually; the app cannot know it is needed because it does not know the target. And it must be **suppressed for a Marionette** — which the app has no way to express today.
6. **P1 · Choosing while impaired is not handled.** If the Preacher is drunk/poisoned tonight, the correct behaviour is: *let them point, place nothing, wake nobody*. The app offers no impairment warning on the Preacher's row at all (no `InfoCalc` support ⇒ no `commonCaveats`), so the ST gets no signal. `InfoCalc.kt:29-35`.
7. **P2 · The fourth "No Ability" placement silently un-preaches someone.** `NightScreen.kt:330-341` recycles `placed.first()` when all three copies are out. With three Minions already preached (legal in a 15-player game with a Baron/Xaan-style bag, or with a Summoner), preaching a fourth silently frees the first. Repro: place `No Ability` on four different seats from the Preacher's tray; the first seat loses it with no confirmation.
8. **P2 · All three Preacher jinxes are missing from the data** (`night_and_jinxes.json`): Legion, Summoner, Vizier. The Vizier one in particular is a rules consequence the app should surface at the moment the Vizier is preached ("they learn this, and cannot die during the day").
9. **P2 · `No Ability` label collides with three other meanings.** `professor:No ability`, `<character>:No ability` from the once-per-game "Mark spent" button (`NightScreen.kt:272`), the Fool check (`StatusEffects.kt:75`) and the Virgin check (`StatusEffects.kt:154`) all use the same label with different `sourceId`s and different semantics ("spent" vs "suppressed"). Any generic "does this player have their ability?" predicate must key on `sourceId`, not just the label. Note also the casing inconsistency: `characters.json` declares `"No Ability"` while the code writes `"No ability"` and compares with `equals(..., ignoreCase = true)` — that works, but a new predicate must keep the case-insensitive comparison.
10. **P3 · The Preacher's row does not list who the Minions are.** The ST knows from the grimoire, but every info character's row names its relevant seats; the Preacher's does not, so the ST has to switch tabs to check whether the pointed-at player was a Minion.

## Proposed behaviour (spec)

### Core engine addition (reusable, not Preacher-specific)
```
// StatusEffects.kt
fun hasAbility(state, lookup, player): Boolean
// false when: the player is the Drunk / Marionette;
//             carries a *suppressing* No-ability token whose source is currently active;
//             is dead (for characters that don't act dead)
fun suppressedBy(state, lookup, player): String?   // e.g. "Preacher (Ana)" or null
```
For the Preacher specifically: `preacher:No Ability` on a seat suppresses **iff** some seat with `characterId == "preacher"` is `alive && !isImpaired`. If no such Preacher exists (dead, or turned into something else), or the Preacher is impaired, the token is **dormant** — still visible, not applied.

### Night step
- **when:** both nights, every night. Wake condition: `preacher` seat is `alive`. (A dead Preacher does not act, and their tokens are dormant anyway.)
- **targets:** exactly **1** player. Constraints: **any** player, alive **or dead** ("The Preacher may choose dead players"), including themselves. No "different from last night" rule. Picker default/sort: seats already carrying `preacher:No Ability` sorted **last** and visually marked "already preached" (choosing them again is legal but pointless).
- **immediate effects, when the Preacher is sober, healthy and alive:**
  - if the target's team is `MINION`:
    - place `PlacedReminder("preacher", "No Ability")` on the target (non-exclusive, up to 3 copies — but see the cap fix below);
    - **wake prompt**, unless the target is the **Marionette**: fire the two cards in sequence — `THIS CHARACTER SELECTED YOU` then the **Preacher** character token. The existing `night_guide` `"token":"self"` card covers the second; add the first as a plain message card so the two are one tap each, in order.
    - Marionette target: place the token, show `! Marionette — do NOT wake them. They lose their ability but learn nothing.`
    - Vizier target: additionally show `! Vizier jinx — the Vizier learns they lost their ability and cannot die during the day.` and place a `Vizier: cannot die today` marker.
    - Legion target: show `! Legion jinx — Legion KEEPS their ability. You may tell the Preacher they are Legion.` and place **no** token.
    - Summoner target: show `! Summoner jinx — while the living Summoner has no ability, YOU (the Storyteller) have the Summoner ability on night 3.`
  - if the target is not a Minion: `Nothing happens. Do not wake anyone.` — and the step is complete.
- **immediate effects, when the Preacher is impaired:** `! The Preacher is drunk/poisoned — let them point, then place nothing and wake nobody.` The token-placement action is disabled (not merely discouraged).
- **deferred effects:** none of its own. The suppression is a *standing* condition re-evaluated every time the night sheet is built.
- **expiry:** `preacher:No Ability` never expires. It goes in **neither** `EXPIRES_AT_DAWN` nor `EXPIRES_AT_DUSK`. It is removed only by hand, or by the ST when the preached player stops being a Minion.
- **information:** the Preacher learns nothing. The Minion learns only "the Preacher chose you".
- **visibility:** nothing is shown to the Demon.
- **day-time inputs:** none.

### Suppression of preached Minions' night steps
In `NightOrder.build`'s `else ->` branch (`NightOrder.kt:144-181`), after `holders` is resolved:
```
val active = holders.filter { StatusEffects.hasAbility(state, lookup, it) }
if (active.isEmpty() && holders.isNotEmpty()) {
    // emit a greyed, auto-ticked row rather than dropping it silently
    steps += NightStep(id, character.name,
        "SKIP — ${holders.joinToString { it.name }} has no ability (${StatusEffects.suppressedBy(...)}).",
        playerIds = holders.map { it.id })
    continue   // and mark it done so the dawn guard doesn't nag
}
```
Emitting a visible "SKIP — …" row rather than hiding the step is important: the ST must be able to see that the app made the decision, and to override it. `NightScreen.NightStepRow` already has an "All holders are dead — usually skip." affordance (`NightScreen.kt:751-758`) — reuse that presentation.

The same row must **flip back automatically** the moment the Preacher dies or is poisoned, with the reason shown: `<Minion> has their ability back — the Preacher is dead.` / `… — the Preacher is poisoned.`

### Deferred effect — the Preacher dies or is impaired
- Add `"preacher" -> notes += "Preacher: every Minion marked 'No Ability' by them gets their ability back."` to `StatusEffects.deathNotes` (`StatusEffects.kt:95-104`), listing the affected seats by name.
- At **dawn**, if a Preacher died overnight, the day briefing must say: `<MinionName> (and …) have their abilities back — the Preacher is dead.`
- When a poison/drunk token is placed on the Preacher, the seat sheet should note the same, and the tokens should render dimmed/dormant in the grimoire.

### Fixes to existing behaviour
- `NightScreen.kt:330-341` — when all copies of a multi-copy token are placed, **do not silently recycle**. Either raise the cap (the rules allow as many preached Minions as there are Minions; the 3-token limit is a physical-box limit, and `Character.allReminders` is the wrong source of truth for a logical cap) or confirm: `All 3 "No Ability" tokens are placed. Move the one on <Name>?`
- `InfoCalc.supports` — add `preacher` returning a non-info result so the step inherits `commonCaveats` (impairment + Vortox). Alternatively, and better: hoist `impairments()` out of `InfoCalc` so **every** night row can display the holder's impairment, not just the ~30 info roles. That is the cross-cutting fix.

### UI text the step should display
- `Preacher — <name>. Who did they point to?` + player chips (dead included, already-preached marked).
- Non-Minion target: `<Target> is the <Character> — not a Minion. Nothing happens.`
- Minion target: `<Target> is the <Character> (Minion). Place "No Ability" · Wake them · Show "THIS CHARACTER SELECTED YOU" · Show the Preacher token.` as a 1-tap chain.
- Impaired Preacher: `! Drunk/poisoned — they point, nothing happens, nobody wakes.`
- Standing summary at the bottom of the row: `Currently preached: <names> (active / dormant because the Preacher is dead|poisoned).`

### Data changes
- `night_and_jinxes.json` — add:
  - `{"id1":"preacher","id2":"legion","reason":"If the Preacher chooses Legion, Legion keeps their ability, but the Preacher might learn they are Legion."}`
  - `{"id1":"preacher","id2":"summoner","reason":"If the living Summoner has no ability, the Storyteller has the Summoner ability."}`
  - `{"id1":"preacher","id2":"vizier","reason":"If the Vizier loses their ability, they learn this, and cannot die during the day."}`
- `night_guide.json:1175` — add the Marionette exception and the "while the Preacher is alive" lapse to both `first` and `other` instructions; add a second show card `{"label":"Selected you","kind":"message","text":"THIS CHARACTER SELECTED YOU"}` so the two-card sequence is explicit.
- `characters.json:1535` — no text change. Consider normalising the reminder label casing to `"No ability"` to match every write site in the code.

## Tests to add

1. `preached minion has no night step while the preacher lives` — Given a Preacher (alive, sober) and a Poisoner carrying `PlacedReminder("preacher","No Ability")`; When `nightOrder.otherNight(state, lookup)`; Then the `poisoner` step is marked SKIP (or absent). Today it is a normal, actionable row.
2. `preached minion regains their step when the preacher dies` — Given the above; When `GameActions.kill(state, preacherId, EXECUTION)`; Then the `poisoner` step is a normal actionable row again and its detail does not contain "no ability".
3. `preached minion regains their step while the preacher is poisoned` — Given the above with `PlacedReminder("poisoner","Poisoned")` on the Preacher; Then the `poisoner` step is normal.
4. `preacher death note lists the freed minions` — Given a preached Baron and Poisoner; When `StatusEffects.deathNotes(state, lookup, preacherId)`; Then a note names both. (Today: no `preacher` case at all.)
5. `preacher token never expires` — Given `preacher:No Ability` placed; When `advancePhase` is run through dawn and dusk; Then the token is still there.
6. `hasAbility distinguishes preacher suppression from once-per-game spent` — Given `professor:No ability` on the Professor and `preacher:No Ability` on the Poisoner; Then `hasAbility(professor) == false` for the once-per-game meaning but the Professor's step is still emitted with a "spent" marker, while the Poisoner's is SKIPped. (Guards against the label collision in D9.)
7. `preaching a non-minion places nothing` — Given the Preacher targets a Townsfolk; When the resolver runs; Then no reminder is added anywhere.
8. `impaired preacher's choice has no effect` — Given the Preacher is poisoned and targets the Poisoner; Then no `preacher:No Ability` token is placed and the Poisoner's step stays normal.
9. `preacher jinxes are in the data` — `data.activeJinxes(listOf("preacher","legion")).size == 1`, likewise for `summoner` and `vizier`.
10. `marionette is preached without being woken` — Given the target is the Marionette; Then the token is placed and the resolver's instruction text contains "do NOT wake".
