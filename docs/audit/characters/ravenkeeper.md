# Ravenkeeper (ravenkeeper) — Trouble Brewing Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Ravenkeeper> (raw wikitext fetched
2026‑08‑25 via `action=parse&prop=wikitext`).

**Current ability text (verbatim):**
> "If you die at night, you are woken to choose a player: you learn their character."

**Summary bullets (verbatim):**
- "The Ravenkeeper is woken on the night that they die, and chooses a player immediately."
- "The Ravenkeeper may choose a dead player if they wish."

**How to Run (verbatim):**
> "If the Ravenkeeper died tonight, wake them. They point at any player. Show the
> chosen player's character token to the Ravenkeeper. Put the Ravenkeeper to sleep.
>
> We advise you to discourage or even ban players from talking about what they are
> doing at night as they are doing it."

**Examples (verbatim):**
- "The Ravenkeeper is killed by the Imp, and then wakes to choose a player. After some
  deliberation, they choose Benjamin. Benjamin is the Empath, and the Ravenkeeper learns this."
- "The Imp attacks the Mayor. The Mayor doesn't die, but the Ravenkeeper dies instead, due
  to the Mayor's ability. The Ravenkeeper is woken and chooses Douglas, who is a dead
  Recluse. The Ravenkeeper learns that Douglas is the Scarlet Woman, since the Recluse
  registered as a Minion."

**Storyteller-relevant clarifications from Tips & Tricks:**
- "Remember that you only get your information if you die at night. **Getting killed
  during the day reveals nothing to you.**" → an executed Ravenkeeper does **not** wake.
- "Beware of the Spy and the Recluse. … it is unlikely that you will learn their true
  character if you choose them, owing to their abilities to register as other characters."
  (ST picks the registered character; Recluse example above shows a **dead** Recluse still
  misregistering.)
- "If you learn a player is the Drunk … you know that they are good" → the Ravenkeeper is
  shown the **true** character token, i.e. the Drunk token, not the Townsfolk the Drunk
  believes they are.
- Bluffing section: "The Ravenkeeper would wake only when they die during the night, not
  the day."

Timing facts that follow from the rules and the official night order
(<https://wiki.bloodontheclocktower.com/Night_Order>):
- The Ravenkeeper acts **only on other nights** and only in the night in which the death
  happened, i.e. the step must be inserted *after* whatever killed them.
- Any *night* death triggers it — Demon kill, Mayor bounce, Assassin, Godfather,
  Gossip, Witch, self-inflicted, etc. Not only the Demon.
- Being drunk/poisoned does **not** stop the wake; the ST still wakes them and shows a
  *false* character token.
- The Ravenkeeper is dead when they act, so "dead players have no ability" does not apply —
  the ability explicitly fires on death.

**Jinxes (verbatim from the wiki jinx table):**
- Leviathan: "Each night*, the Leviathan chooses an alive player (different to previous
  nights): a chosen Ravenkeeper uses their ability but does not die."
- Riot: "Each night*, Riot chooses an alive good player (different to previous nights):
  a chosen Ravenkeeper uses their ability but does not die."

## What the app does today

Data
- `engine/src/main/resources/botc/data/characters.json:97-108` — ability text matches the
  wiki. `otherNightReminder` = "If the Ravenkeeper died tonight: The Ravenkeeper points to a
  player. Show that player's character token." `reminders: []`, `firstNightReminder: ""`.
- `engine/src/main/resources/botc/data/night_and_jinxes.json:445` — `ravenkeeper` is at
  index 72 of `otherNight`, after every Demon (imp = 37) and after grandmother (71). It is
  **absent** from `firstNight`. Correct for TB.
- `engine/src/main/resources/botc/data/night_guide.json:60-72` — good prose: "Only act if
  the Ravenkeeper died tonight… If the Ravenkeeper is drunk or poisoned, show a false
  character token instead; note the Spy or Recluse may register falsely." One prepared show
  card, `kind: "token"`, `token: "pick"`, text "The player you chose is this character".

Night sheet
- `engine/src/main/kotlin/com/clocktower/engine/NightOrder.kt:46-48` builds `inPlay` from
  **all** players with a `nightRoleId` — there is **no `alive` filter and no "died tonight"
  condition**. So the Ravenkeeper row is emitted on *every* other night, whether they are
  alive, died tonight, or died three days ago by execution.
  → Answering the audit question directly: the app does **not** dynamically add a step when
  the Ravenkeeper dies tonight; the step is unconditionally always there. It happens to be
  present when needed (and it is correctly positioned after the Demon), but it is also
  present on every night when it must not fire, and the app never tells the ST which case
  they are in.
- `app/src/main/java/com/clocktower/grimoire/ui/screens/NightScreen.kt:84-90` recomputes the
  sheet from `state.players`, so a mid-night death does refresh the row (but only its
  dimming/labels, since the row already existed).

Expanded step
- `NightScreen.kt:792-832` renders the night_guide prose + the "Show their character" card.
- `NightScreen.kt:836-861` renders a 1-target picker (`InfoCalc.targetsNeeded` →
  `InfoCalc.kt:24` returns 1 for `ravenkeeper`).
- `InfoCalc.kt:64` → `revealCharacter(ctx, targets, "Ravenkeeper")` at `InfoCalc.kt:376-384`:
  headline `"<name> is the <Character>"` using **`characterId`** (true character, so the
  Drunk shows as Drunk — correct), plus Spy/Recluse caveats from
  `InfoCalc.kt:121-130`.
- `NightScreen.kt:904-930` offers "false info to show instead" chips — but only for numeric
  or YES/NO headlines. A character-token answer gets **no** false-token helper.

Death handling
- `StatusEffects.kt:95` adds the death note "Ravenkeeper: if dying at night, they wake to
  learn a character." This is shown in `SeatSheet.kt:240-251` and in the Demon kill panel at
  `NightScreen.kt:586-590`. Good, but it is only prose; nothing happens.
- `GameActions.kill` (`GameActions.kt:136-156`) snapshots `atNight`, `cause`, `day`,
  `characterIdAtDeath`, `abilityImpairedAtDeath` — everything needed to derive the wake
  condition. Nothing consumes it for the Ravenkeeper.

Jinx data
- `night_and_jinxes.json:195` (leviathan↔ravenkeeper) and `:220` (riot↔ravenkeeper) carry
  **invented** text ("If Leviathan is in play and the Ravenkeeper dies by execution, they
  wake that night to use their ability.") that does not match the official jinx.

Works: night-order position; true-character reveal ignoring `shownCharacterId`; Spy/Recluse
caveat; the guide prose; the show card.

## Defects and gaps

1. **P0 · The row that must fire is labelled "usually skip"** — the moment the Ravenkeeper
   dies, `NightStepRow` computes `allDead` (`NightScreen.kt:700-702`) and prints
   *"All holders are dead — usually skip."* in error red (`NightScreen.kt:751-757`), dims the
   token (`:720`), and `InfoCalc.impairments` adds the caveat *"<name> is dead — they normally
   don't act."* (`InfoCalc.kt:150`). Both are exactly backwards: this is the one night the
   Ravenkeeper **must** act. The list footer repeats it ("Dead players usually don't act —
   skip them unless their ability says otherwise", `NightScreen.kt:160-169`).
   *Repro:* TB game, Imp kills the Ravenkeeper on night 2 → open the Night tab → the
   Ravenkeeper row is greyed with a red "usually skip".

2. **P0 · No wake condition is computed** — the app never checks whether the Ravenkeeper
   actually died tonight, so the ST gets an identical row on the 80 % of nights where the
   Ravenkeeper must *not* wake, and (because of the unfinished-steps guard at
   `GameShell.kt:147-161`) is forced to tick it off every single night.
   Everything needed is already in state: `state.deaths.any { it.playerId == rk.id &&
   it.atNight && it.day == state.cycle && !it.resurrected }`.
   *Repro:* alive Ravenkeeper, night 3 → row present, prompt identical to the night they die.

3. **P0 · Wrong jinx text for Leviathan and Riot** —
   `night_and_jinxes.json:195,220` say the Ravenkeeper wakes when they die **by execution**.
   The official jinx is the opposite mechanic: the Leviathan/Riot *chooses* a player each
   night and a chosen Ravenkeeper "uses their ability but does not die". A ST reading the
   in-app jinx (`SeatSheet.kt:222-234`, `GameExtras.kt:202-231`) would run the wrong rules.

4. **P1 · No false-token helper when impaired** — a poisoned/drunk Ravenkeeper must be shown
   a *false* character. `NightScreen.kt:904-930` only produces false answers for numbers and
   YES/NO; the Ravenkeeper's answer is a token, so the ST must open the "Show their
   character" dialog and hunt for a plausible lie by hand. The app knows the in-play set, the
   bluff set and the not-in-play set and could propose them.

5. **P1 · No Spy/Recluse "register as" picker** — the misregistration caveat is text only
   (`InfoCalc.kt:121-130`). If the Ravenkeeper picks the Recluse the ST must decide *which*
   Minion/Demon token to show and then remember that choice for the Undertaker / Fortune
   Teller / Empath later. Nothing records it.

6. **P1 · The ST is never told at dawn/day-start that the Ravenkeeper used their ability** —
   there is no dawn briefing at all (`GameShell.kt:126-168` just calls `advancePhase`), so
   "announce X died" and "the Ravenkeeper learned Y" are entirely in the ST's head. Relevant
   because the ST must be ready for the Ravenkeeper's public claim that day.

7. **P1 · A resurrected Ravenkeeper's second death is not distinguished** — `GameActions.resurrect`
   (`GameActions.kt:173-181`) keeps the death record with `resurrected = true`. A wake-condition
   check that ignores `resurrected` would re-fire on the resurrection night; a check that only
   looks at `alive` would miss a Ravenkeeper who died and was resurrected the same night
   (Professor is night-order 63, Ravenkeeper 72 — the Professor can resurrect the Ravenkeeper
   *before* the Ravenkeeper row). Per the rules the Ravenkeeper did die at night, so they still
   act. Nothing in the app handles this ordering.

8. **P2 · The Ravenkeeper cannot wake on night 1** — `ravenkeeper` is absent from
   `firstNight`, which is right for TB but wrong for scripts with a night‑1 kill (Kazali,
   Lord of Typhon, Boffin-granted kills). The generic engine should insert the row on any
   night in which the death happened, not read it off a static list.

9. **P2 · No target constraints / sort** — the picker at `NightScreen.kt:846-860` lists every
   seat in seat order with no marking of alive/dead and no exclusion of the Ravenkeeper
   themselves (self-choice is legal but almost never intended). Dead players are legal
   targets (wiki: "may choose a dead player") so they must stay in the list, but should be
   visually separated.

10. **P2 · `InfoCalc.chambermaid` names the Ravenkeeper as a manual judgement call**
    (`InfoCalc.kt:479-483`) precisely because the app cannot decide whether the Ravenkeeper
    wakes. Fixing defect 2 makes the Chambermaid count exact.

11. **P3 · The Mayor-bounce path is invisible** — the wiki's canonical example is the
    Ravenkeeper dying *instead of* the Mayor. `DemonKillPanel` (`NightScreen.kt:534-638`)
    only lets the ST pick one target and confirm/decline; there is no "the Mayor's ability
    bounced this to <player>" action, so the ST kills the Ravenkeeper via "Other death" and
    the (nonexistent) wake trigger would have to accept `DeathCause.STORYTELLER` too.

## Proposed behaviour (spec)

**Structured night behaviour**

- **when:** `other` nights only (and, in a generic engine, *any* night — including night 1 on
  scripts where a night‑1 death is possible). Wake condition:
  `deaths.any { it.playerId == holder.id && it.atNight && it.day == state.cycle }`
  — regardless of `cause` (Demon, Minion, Mayor bounce, storyteller), regardless of
  `resurrected` (dying and being resurrected the same night still triggers the ability), and
  regardless of impairment. If the condition is false the step must be **collapsed to a
  greyed "does not wake tonight" line that is auto-marked done**, not presented as an action.
  The step must be **inserted dynamically after the death**: if the death record appears
  after the ST has already passed position 72, re-emit the row (unticked) and scroll to it.
- **targets:** exactly 1, any seat, alive **or dead**, self allowed. Picker default sort:
  alive players first, then dead, both in seat order; annotate `†` for dead. No
  "different from last night" constraint.
- **immediate effects:** none. No tokens placed, no kills. (The Ravenkeeper is already dead.)
- **deferred effects:** none. At dawn the briefing should include
  "Ravenkeeper <name> learned <shown character> from <target>" as a private ST reminder.
- **expiry:** n/a — no tokens.
- **information:**
  - True answer: the target's **`characterId`** (never `shownCharacterId`) — the Drunk shows
    as *Drunk*, the Lunatic as *Lunatic*, the Marionette as *Marionette*.
  - Misregistration: if the target is the Spy or the Recluse (alive **or dead**), show a
    "register as" chooser instead of a bare caveat: Spy → any Townsfolk/Outsider on the
    script (default: a not-in-play good character, or the Spy's earlier registration if one
    is recorded); Recluse → any Minion/Demon on the script (default: an in-play evil
    character). Record the chosen registration on the target seat as
    `PlacedReminder("<spy|recluse>", "Registered: <Character>")` for the rest of the game so
    the ST stays consistent, and offer "keep as before" as the default on later prompts.
  - Impaired alternative: if `StatusEffects.isImpaired(holder)` or the holder was impaired at
    death (`DeathRecord.abilityImpairedAtDeath == true`), replace the true answer with a
    **false-token chooser**, pre-populated in this order: the 3 demon bluffs, not-in-play good
    characters on the script, then in-play characters. One tap shows it full screen.
  - Vortox: Townsfolk info must be false → the same false-token chooser, headed
    "VORTOX: this must be false."
- **visibility:** nothing is shown to the Demon or Minions. If a Lunatic "killed" the
  Ravenkeeper, the Ravenkeeper does **not** wake (no real death) — the step must not fire off
  a `lunatic:"Attack N"` token.
- **day-time inputs:** none required, but the day-start briefing should let the ST record
  *what the Ravenkeeper publicly claimed* so the log can show claim vs. truth.
- **interactions/jinxes to handle explicitly:**
  - **Leviathan** — replace the data text with the official jinx and, when Leviathan is in
    play, add an extra "Leviathan chooses a player (different from previous nights)" step
    whose chosen-Ravenkeeper branch runs the Ravenkeeper's ability **without** killing them.
  - **Riot** — same, restricted to alive **good** players.
  - **Mayor bounce** — the kill panel needs a "redirect this death to <player>" action that
    records the redirected death with `atNight = true`; the Ravenkeeper trigger must accept it.
  - **Scarlet Woman / star pass** — if the Ravenkeeper picks the seat that just became the
    Demon, they learn the **new** character (the app already reads live `characterId`).
  - **Exorcist / Monk** — irrelevant to this step, but a Monk-protected Ravenkeeper simply
    never satisfies the wake condition.
  - **Drunk shown as Ravenkeeper** — a `characterId = "drunk"`, `shownCharacterId =
    "ravenkeeper"` seat *does* get a wake row (`Player.nightRoleId`, `GameState.kt:35-42`
    already routes this correctly) and must always get a **false** token.

**UI text the step should display**

- Not triggered: `"Ravenkeeper — no night death. Skip."` (auto-ticked, collapsed.)
- Triggered: `"<Name> died tonight. Wake them; they point at a player."`
  Then: `"Show: <Character> token"` with a `Show full-screen` chip.
- Impaired: `"<Name> was poisoned/drunk — show a FALSE token:"` + chooser chips.
- Misregistering target: `"<Target> is the Recluse — choose what they register as:"` + chips.

**Data changes**

- `characters.json:97-108`: leave the ability text (matches the wiki). Consider adding a
  machine-readable `wake: {"trigger": "diedThisNight"}` field rather than relying on prose.
- `night_and_jinxes.json:195`: replace with
  `"Each night*, the Leviathan chooses an alive player (different to previous nights): a chosen Ravenkeeper uses their ability but does not die."`
- `night_and_jinxes.json:220`: replace with
  `"Each night*, Riot chooses an alive good player (different to previous nights): a chosen Ravenkeeper uses their ability but does not die."`
- `night_guide.json:60-72`: add "They may choose a dead player." and "Execution during the
  day gives them nothing." Add a second show entry for the false-token case.

## Tests to add

1. **Wake condition — died tonight**
   Given a TB game at NIGHT cycle 3 with a Ravenkeeper killed this night by the Imp
   (`DeathCause.DEMON`, `atNight = true`, `day = 3`)
   When the night sheet is built
   Then the `ravenkeeper` step is present and `NightOrder`/a new `wakes` flag reports
   `wakes = true`, and it sits **after** the `imp` step.

2. **Wake condition — alive**
   Given a TB game at NIGHT cycle 3 with the Ravenkeeper alive
   When the night sheet is built
   Then the `ravenkeeper` step reports `wakes = false` (today it is indistinguishable).

3. **Wake condition — died by execution**
   Given the Ravenkeeper died on day 2 with `DeathCause.EXECUTION`, `atNight = false`
   When night 3 is built
   Then `wakes = false` — an executed Ravenkeeper never wakes.

4. **Wake condition — dead since an earlier night**
   Given the Ravenkeeper died at night on cycle 2
   When night 3 is built
   Then `wakes = false` (the app currently shows an identical actionable row).

5. **Wake condition — non-Demon night death**
   Given the Ravenkeeper died at night on cycle 3 with `DeathCause.OTHER_NIGHT_DEATH`
   (Mayor bounce / Assassin)
   Then `wakes = true`.

6. **Resurrected the same night**
   Given the Ravenkeeper died at night on cycle 3 and `GameActions.resurrect` was applied on
   the same cycle (Professor)
   Then `wakes = true` and the death record retains `resurrected = true`.

7. **Info is the true character**
   Given a Drunk seat with `shownCharacterId = "chef"` and the Ravenkeeper choosing it
   When `InfoCalc.compute(..., "ravenkeeper", holder, listOf(drunkId))`
   Then the headline names **Drunk**, not Chef. *(passes today — lock it in)*

8. **Dead Recluse still misregisters**
   Given a dead Recluse chosen by the Ravenkeeper
   Then the result offers a Minion/Demon registration choice (today: only a caveat string).

9. **Impaired Ravenkeeper gets a false-token affordance**
   Given the Ravenkeeper is poisoned and died tonight
   When the info is computed
   Then `caveats` contains the POISONED note **and** the result exposes a non-empty
   `falseAlternatives` list (today the field does not exist).

10. **Jinx text**
    Given `data.activeJinxes(listOf("leviathan", "ravenkeeper"))`
    Then the reason equals the official Leviathan jinx string (fails today).
