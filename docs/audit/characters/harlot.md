# Harlot (harlot) — Sects & Violets Traveller

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Harlot>
Traveller rules: <https://wiki.bloodontheclocktower.com/Travellers>

Current ability text (wiki, matches `characters.json:1184`):

> "Each night\*, choose a living player: if they agree, you learn their character, but you
> both might die."

Summary clarifications (quoted):

> - "Each night, the Harlot chooses a player. That player has a decision to make: do they
>   reveal their character to the Harlot? If they do, the Storyteller may decide that both
>   this player and the Harlot die tonight."
> - "The Harlot only learns the character of the chosen player, not that player's alignment."
> - "The Harlot may discuss during the day which character they would like to pick at night,
>   and other players may offer to be picked, but they may go back on their word and choose
>   differently when night comes."

How to Run (quoted in full):

> "Each night, wake the Harlot. The Harlot points at any player. Put the Harlot to sleep.
> Wake the chosen player, show them the **THIS CHARACTER SELECTED YOU** info token, then the
> Harlot token. That player either nods their head yes or shakes their head no. Put that
> player to sleep.
>
> If they shook their head no, then nothing happens. Continue with the night phase.
>
> If they nodded their head yes, wake the Harlot and show them the chosen player's character
> token. Put the Harlot to sleep. You may decide that both players **die**—mark them with
> **DEAD** reminders.
>
> When choosing whether to kill players, do what you feel is the most interesting and
> balanced. If the Demon reveals to the Harlot, you should not end the game by killing them."

Examples (quoted in full):

> "The good Harlot wakes and chooses the Philosopher, who chooses to reveal. The next night,
> the Harlot chooses the No Dashii, who chooses not to reveal. The next night, the Harlot
> chooses the Mutant, who chooses to reveal. The Storyteller decides that the Harlot and
> Mutant die tonight.
>
> The evil Harlot chooses the Sage, who reveals. The next day, the Harlot says the Sage is
> actually the Witch."

Consequences that matter for the app:

- **Other nights only** (`night*`), every night, mandatory-ish (the ability text does not
  say "you may"; the How-to-Run has no "or shake their head no" branch for the Harlot
  themselves, unlike the Bone Collector).
- **Three-beat interaction**: Harlot points → chosen player is woken and consents or refuses
  → only on consent is the Harlot woken again and shown the character token.
- **The Harlot learns the character, never the alignment.** For a Drunk/Lunatic/Marionette
  the correct answer is the **true** character, not the shown one (the Harlot "learns their
  character"); this is worth stating in the step because the app carries both
  (`Player.characterId` vs `Player.shownCharacterId`, `GameState.kt:18-23`).
- **The deaths are both-or-neither, and are a Storyteller choice.** "You may decide that
  both players die." There is no partial outcome.
- **Never end the game by killing a revealing Demon.**
- **The deaths happen tonight**, at the Harlot's place in the night order, and are announced
  at dawn like any other night death.
- The target must be **living** (ability text). Self-targeting is not addressed by the wiki;
  the How-to-Run says "any player", the ability text says "a living player".
- **Two DEAD reminder tokens** exist on the character (current official role data lists
  `["Dead","Dead"]`) — one for each of the two possible deaths.
- No jinxes on the Harlot page.

## What the app does today

Data:
- `characters.json:1179-1192` — correct ability text; a full `otherNightReminder` that
  matches the wiki; `reminders: ["Dead"]` (official is two DEAD tokens).
- `night_and_jinxes.json:379` — `harlot` in `otherNight` only, after `bonecollector`.
  Correctly absent from `firstNight`. (I could not verify the exact relative order of
  `bonecollector` vs `harlot` against an official night-order sheet — the wiki has no
  night-order page and the script tool's role data was unreachable — so I am not claiming
  drift.)
- `night_guide.json:811-830` — a good `other` entry with the three-beat prose and two show
  cards: a `self` token card ("This character selected you - do you agree to reveal your
  character?") and a `pick` token card ("This player is…").

Code: **no Harlot-specific code anywhere.** `grep -rn harlot engine/src app/src` returns
only the data files.

Storyteller's actual experience:
1. A "Harlot" row appears on every non-first night, alive or dead
   (`NightOrder.kt:142-178`, `:143-145`).
2. Expanding it shows the guide prose plus the two "»" show chips
   (`NightScreen.kt:786-826`). The `pick` card opens a character picker so the ST can flash
   the revealed character token — that part is genuinely good.
3. `QuickResolutions` (`NightScreen.kt:462-522`) has no Harlot branch, so there is no target
   picker, no consent toggle, and no "both die" button.
4. `InfoCalc.supports` (`InfoCalc.kt:29-36`) does not include `harlot`, so the app never
   tells the ST *which* character to show — the ST must read the grimoire and hand-pick the
   token in the `pick` dialog.
5. To kill both players the ST leaves the night sheet, opens each seat and presses
   "Died at night" twice (`SeatSheet.kt:271-273`).

Works: other-nights-only placement, the three-beat prose, the "This character selected you"
card, the `pick` character card.

Shared traveller-lifecycle defects **T1–T7** apply — see `barista.md`.

## Defects and gaps

1. **P0 · The two deaths are entirely manual and can be applied asymmetrically.** There is
   no resolver. The ST must remember that it is *both or neither*, leave the night sheet,
   and kill two seats one at a time via `SeatSheet.kt:271-273`. Nothing enforces the pairing;
   nothing runs `StatusEffects.deathNotes` for either seat from the night step
   (the seat sheet does, `SeatSheet.kt:237-249`, but only if the ST goes there).
   *Repro:* run a Harlot night; there is no button anywhere that kills the pair.

2. **P0 · The revealed character is not computed.** `InfoCalc.supports`
   (`InfoCalc.kt:29-36`) omits `harlot`, even though `revealCharacter` already exists for
   the Ravenkeeper and Grandmother (`InfoCalc.kt:66-67`, `:285-292`) and does exactly this
   job. The ST has to look the target up and pick the token manually in the `pick` dialog
   (`NightScreen.kt:400-455`), which is both slow and an opportunity to flash the wrong
   token.

3. **P0 · Nothing distinguishes the true character from the shown character.** For a Drunk,
   Lunatic or Marionette target the Harlot must learn the **true** character. The `pick`
   dialog is a free character picker with no default and no hint, so the ST is one tap away
   from showing the Drunk's *shown* Townsfolk token.

4. **P1 · The target is not constrained to living players.** There is no picker at all, so
   nothing enforces "choose a living player", and nothing warns when the Harlot picks
   themselves or a dead player.

5. **P1 · A dead Harlot still gets a night step.** `NightOrder.kt:143-145` includes the row
   whenever any seat holds the id. A dead Traveller has no ability. The row does print
   "All holders are dead — usually skip." (`NightScreen.kt:750-756`) but still counts toward
   the step total and the dawn checklist guard (`GameShell.kt:617-651`).

6. **P1 · The "don't end the game" rule is invisible.** If the target is the Demon (or the
   last living evil player, or a Saint whose death would end it, or the situation is at 3
   alive), the step should say so before the ST taps "both die". `WinCheck.check`
   (`WinCheck.kt:18-101`) already knows how to reason about this but is only consulted after
   the fact (`GameShell.kt:509-518`).

7. **P1 · The daytime "tryst" negotiation is not recordable.** The wiki calls out that the
   Harlot arranges targets during the day and may renege. A ST running this on a phone wants
   a one-line note per day ("Ana offered to reveal") that surfaces on the night step. The
   only storage is the free-text `storytellerNotes` (`GameState.kt:112`) / per-seat `note`
   (`GameState.kt:31`), neither of which is surfaced on the night sheet. Same class of gap
   as the user's Gossip complaint.

8. **P1 · Consent is not modelled.** The whole step branches on a yes/no from the chosen
   player, and the branch determines whether the Harlot is woken a second time. The step
   should carry that toggle so the "wake the Harlot again and show the token" beat and the
   "both may die" beat only appear after a "yes".

9. **P2 · Only one DEAD reminder is declared.** `characters.json:1188-1190` has
   `["Dead"]`; the official token set has two. With `addReminder` (`GameActions.kt:186-187`)
   the tray can place the same label twice, so this is cosmetic, but the count is wrong.

10. **P2 · No protection/trigger check at the moment of the Harlot kill.** These are
    "the Storyteller decides" deaths, not Demon kills — the Soldier is *not* safe, the Monk's
    Safe token does *not* apply (it protects from the Demon), but the Sailor/Tea Lady/
    Innkeeper "Protected"/Fool/Lleech/Zombuul cases do. The night step must surface
    `StatusEffects.deathNotes` for **both** seats before the kill, with a note that
    Demon-only protections do not apply here.

11. **P2 · `DeathCause` has no good value for this.** The pair should be recorded as
    `OTHER_NIGHT_DEATH` (`GameState.kt:75`) with a source, not `DEMON`, so the Undertaker/
    Ravenkeeper/Sage/Godfather logic and the game log (`GameExtras.kt:53-58`) read correctly.
    Today the seat sheet only offers "Died at night" → `DeathCause.DEMON`
    (`SeatSheet.kt:271-273`), which would mislead every downstream check.

## Proposed behaviour (spec)

### Night step (structured)

- **when**: `other` nights only.
  Wake condition: the Harlot holder is **alive**. Otherwise omit the step.
- **targets**: exactly 1, constrained to **living** players. Default sort: living players,
  self last and captioned "(pointless)". Annotate any player the ST recorded as having
  offered a tryst today (see day-time inputs). Warn (do not block) if the target is the
  Demon, or if killing the pair would end the game.
- **consent**: a required second input on the step — `Revealed? yes / no`.
  - `no` → the step ends; no information, no deaths. Record it (for the Harlot's own
    day-time claims and for the ST's memory).
  - `yes` → reveal + optional deaths.
- **immediate effects**:
  - On `yes`: compute and display the target's **true** `characterId` (never
    `shownCharacterId`) and offer a one-tap full-screen `ShowCard.CharacterCard("THIS PLAYER
    IS", trueCharacterId)`.
  - Then a single confirmed action **"Both die"** that kills the Harlot and the target,
    both as `DeathCause.OTHER_NIGHT_DEATH`, in one undoable update; and a **"Neither dies"**
    action that just closes the step. No third option.
- **deferred effects**: both deaths are announced at dawn like any other night death (they
  should appear in whatever dawn-announcement surface is built). If the Harlot's target was
  a Ravenkeeper/Sage/Farmer/Moonchild/Sweetheart/Barber/Poppy Grower, their on-death triggers
  fire — surface `StatusEffects.deathNotes` for the target before confirming.
- **expiry**: none. The Harlot places no lasting tokens (the "Dead" tokens are just death
  markers; the app models death in `Player.alive`).
- **information**: the Harlot learns the target's **character only** — explicitly not the
  alignment. Add `"harlot"` to `InfoCalc.supports` (`InfoCalc.kt:29-36`) with
  `targetsNeeded("harlot") = 1` (`InfoCalc.kt:22-26`) and reuse
  `revealCharacter(ctx, targets, "Harlot")` (`InfoCalc.kt:285-292`), with a caveat line
  `"The Harlot learns the CHARACTER, not the alignment."` and, when the target is a
  Drunk/Lunatic/Marionette, `"Show <trueCharacter> — the Harlot learns the true character,
  not the token that player believes."`
  If the **Harlot** is drunk/poisoned (`isImpaired`, `StatusEffects.kt:36-46`), the existing
  false-info affordance applies: show a wrong character token. Note that the *target's*
  impairment is irrelevant here.
  If the Harlot holds `("barista","Sober & Healthy")`, force true info (see `barista.md`).
- **visibility**: the target sees "THIS CHARACTER SELECTED YOU" + the Harlot token. The
  Harlot sees the target's character token. Nobody else learns anything; the Demon/Minions
  are told nothing.
- **day-time inputs the app must let the ST record**: per day, a short list of players who
  publicly offered to reveal to the Harlot ("tryst offers"), surfaced as annotations on the
  next night's target picker; and, optionally, what the Harlot publicly *claimed* to have
  learned (an evil Harlot lies — worth logging so the ST can follow the fiction).
- **interactions/jinxes to handle explicitly**:
  - No jinxes.
  - **Demon target**: warn "do not end the game by killing a revealing Demon".
  - **Protections**: Demon-only protections (Monk "Safe", Soldier) do **not** apply.
    Sailor, Tea Lady, Innkeeper "Protected", Fool, Lleech, Zombuul, Devil's Advocate
    (execution-only, so no) — surface via `deathNotes` and let the ST decide. If the target
    cannot die, the ST must decide whether the Harlot dies alone; the rules only offer
    both-or-neither, so the honest UI is "the protection stops that death — decide what
    happens" rather than silently killing one.
  - **Vortox**: does not affect this (it is not Townsfolk info), but the Harlot's info is
    still ST-controlled if the Harlot is poisoned.
  - **Barista ACTS TWICE on the Harlot**: two targets tonight, each with its own consent and
    its own possible pair of deaths.
  - **Bone Collector**: a dead Harlot given HAS ABILITY runs this step; the "both die" is
    then only the target (the Harlot is already dead) — surface that explicitly.
  - **Exorcist**: irrelevant (Harlot is not a Demon).

### UI text for the step

- Title row: `Harlot — <Name>`
- Beat 1: `Wake the Harlot. They point at a living player. Put them to sleep.`
- Beat 2: `Wake <Target>. Show THIS CHARACTER SELECTED YOU, then the Harlot token. Do they
  reveal?`  → `Yes` / `No`
- On `No`: `Nothing happens. Continue the night.`
- On `Yes`: `Wake the Harlot. Show <Target>'s character: **<Character>**.` +
  `Show token full-screen` chip. Then:
  `You may now kill BOTH — or neither. Do whatever is most interesting and balanced.`
  Buttons: `Both die` · `Neither dies`
- Demon warning: `<Target> is the Demon — do NOT end the game by killing them.`
- Endgame warning: `Killing both would leave N alive — check the win condition first.`

### Data changes

- `characters.json:1188-1190`: `"reminders": ["Dead", "Dead"]`.
- `night_guide.json:811-830`: append to the instructions —
  "The Harlot learns the CHARACTER only, never the alignment.",
  "For a Drunk, Lunatic or Marionette, show their TRUE character.",
  "It is both players or neither — never one.",
  "If the Demon reveals, do not end the game by killing them."
- Night order: no change.

## Tests to add

1. `Given` a dead Harlot `When` `NightOrder.otherNight` is built
   `Then` no `harlot` step is emitted. *(Fails today.)*

2. `Given` a Harlot and a target that is the Drunk (shownCharacterId = "chef")
   `When` `InfoCalc.compute(..., "harlot", harlotId, listOf(targetId))`
   `Then` the headline names **Drunk**, not Chef. *(Fails today: `supports("harlot")` is
   false, so `compute` returns null.)*

3. `Given` `InfoCalc.compute(..., "harlot", ...)` for any target
   `Then` the caveats include "learns the CHARACTER, not the alignment" and never mention
   the target's alignment.

4. `Given` a Harlot resolution with consent = yes and "Both die"
   `When` applied `Then` both the Harlot and the target are `alive = false` with
   `DeathCause.OTHER_NIGHT_DEATH`, in a single undoable step (one undo restores both).

5. `Given` a Harlot resolution with consent = no
   `When` applied `Then` no deaths are recorded and no information is computed.

6. `Given` a Harlot whose target is the Demon `When` the step is evaluated
   `Then` a warning "do not end the game" is produced.

7. `Given` a poisoned Harlot `When` info is computed
   `Then` an impairment caveat is produced and the false-info affordance is offered;
   `Given` the same Harlot also holds `("barista","Sober & Healthy")`
   `Then` no impairment caveat is produced.

8. `Given` a Harlot holding `("barista","Acts Twice")` `When` the night sheet is built
   `Then` two `harlot` steps appear with independent target/consent state.

9. `Given` a dead Harlot holding `("bonecollector","Has Ability")`
   `When` the night sheet is built `Then` a restored `harlot` step appears and the "both die"
   action kills only the target.
