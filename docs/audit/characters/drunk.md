# Drunk (drunk) — Trouble Brewing Outsider

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Drunk> (fetched 2026-08-25).

Current ability text:

> "You do not know you are the Drunk. You think you are a Townsfolk character,
> but you are not. [+1 Outsider]" — the app's `characters.json` omits the bracket
> and carries `setup: true` without any delta (see defect 9).

How to run — verbatim and paraphrased:

- Setup: *"put the Drunk token"* aside *"and add a Townsfolk character token"* to
  the bag instead. Whoever draws that Townsfolk token is really the Drunk. Place
  the **IS THE DRUNK** reminder by that Townsfolk token during first-night setup.
- *"…are now an Outsider, and do not have the ability of this Townsfolk
  character."* The Drunk counts as an **Outsider** for the distribution — the
  Townsfolk token that went into the bag is a stand-in, so the game still has the
  right number of real Townsfolk abilities minus one.
- *"If that character would wake to act at night, the Drunk wakes to act"* — the
  Drunk is run exactly as the character they believe they are, at that
  character's position in the night order.
- Information: *"can give false information to them if you wish"* — the wiki is
  explicit that it is **unreliable**, not always false:
  *"the information you receive will be wrong, but sometimes the Storyteller may
  tell you something that is true."*
- The Drunk is an Outsider to every detection ability: the Undertaker and
  Ravenkeeper see **the Drunk**, not the believed Townsfolk. (The Storyteller may
  choose to show the believed token instead only via another misregistering
  ability — there is none in TB.)
- The Drunk cannot satisfy abilities that require a real Townsfolk (the Virgin
  does **not** trigger on a Drunk nominator).
- A Drunk Slayer's shot fails silently; a Drunk Monk's protection does nothing; a
  Drunk Mayor does not bounce a kill; a Drunk Soldier is not safe.
- The believed character is normally a **not-in-play** Townsfolk (so a second
  copy of a real ability does not appear); the wiki does not state this as a hard
  rule, but every Storyteller guide and the app's own validator assume it.

Jinxes (from `night_and_jinxes.json`):
- `boffin` × `drunk`: "If the Boffin gives the Demon the Drunk ability, the Demon
  thinks they have been given a different not-in-play Townsfolk ability."
- (Mathematician: learns when the Drunk's information malfunctioned — handled by
  that character.)

Night order: `drunk` is absent from both order lists — correct, because the Drunk
wakes under the **believed** character's id.

## What the app does today

Data
- `characters.json` — `drunk`: `team: "outsider"`, `setup: true`,
  `remindersGlobal: ["Is the Drunk"]`, no night reminders. The ability string
  omits the `[+1 Outsider]` bracket that the modern text carries.
- `night_and_jinxes.json` — absent from both order lists. Correct.
- `night_guide.json` — **no `drunk` entry**. The Drunk's night step is rendered
  from the *believed* character's guide, which is the desired behaviour, but it
  means there is no Drunk-specific "give unreliable info" reminder.

Engine
- `GameState.kt:39-44` — `nightRoleId` returns `shownCharacterId` for
  `characterId == "drunk"`, so the Drunk is grouped under the believed character
  in the night order. **This works.**
- `GameActions.kt:517-521` — `validateSetupState` requires the Drunk's
  `shownCharacterId` to be a **not-in-play Townsfolk** or it blocks first night
  with "choose a not-in-play Townsfolk token to show the Drunk". **This works.**
- `StatusEffects.kt:37` — `isImpaired` returns true for `characterId == "drunk"`.
  **This works**, and drives the green "!" badge on the grimoire
  (`GrimoireScreen.kt:332,421-434`).
- `InfoCalc.kt:136-138` — `impairments` emits "`<name>` IS the Drunk — their
  ability malfunctions."
- `Setup.modifierFor` (`Setup.kt:121-124`) — `setup: true` with no bracket yields
  `SetupModifier(id, "Modifies setup")` with **all deltas zero**, so the Drunk
  contributes nothing to the distribution. Given that the Drunk *is* an Outsider
  in the bag and the app models the seat as `characterId = "drunk"`, the
  arithmetic comes out right; the `[+1 Outsider]` bracket in the modern text is
  about the **physical** bag swap, not an extra Outsider slot. Effectively
  correct, but the "Modifies setup" label is shown to the Storyteller with no
  explanation (`SetupScreen.kt:373-375`).

UI
- `GameShell.kt:377-413` — a first-class setup prompt: "The Drunk is in play —
  `<name>` is the Drunk. Which Townsfolk token do they see?", listing all
  not-in-play Townsfolk from the script. On pick it sets `shownCharacterId`,
  places `drunk:"Is the Drunk"`, and writes the seat note "Believes they are the
  `<X>`". **This works well.**
- `RevealFlow.kt:54-58,107-118` — the reveal screen shows
  `characterShownToPlayerId`, so the Drunk is handed their believed token.
  **This works.**
- `SeatSheet.kt:199-221` — a "Shown to `<name>`" panel with a Clear button.
- `NightScreen.kt:836-863` — the info panel computes for
  `holderId = step.playerIds.firstOrNull()`.

Storyteller experience: setup is genuinely good. From night 1 onward the Drunk
appears under their believed character's row, and if that character is one of the
~26 in `InfoCalc.supports`, the panel shows the true answer, the "IS the Drunk"
caveat, and false-info chips. Everything else is manual.

## Defects and gaps

1. **P0 · When the Drunk shares a night row with the real character, only one of
   them gets an info panel.**
   `NightOrder.kt:46-48` groups by `nightRoleId`, so a real Empath and a
   Drunk-who-believes-they-are-the-Empath land in one step with two
   `playerIds`. `StepDetailPanel` then uses
   `step.playerIds.firstOrNull()` (`NightScreen.kt:837`) — the earlier seat wins,
   the other player's info is never computed, and the "IS the Drunk" caveat is
   attached to whichever seat happened to be first. Repro: assign the Drunk
   `shownCharacterId = "empath"` while a real Empath is in play; open the Empath
   step on night 2 → one name is listed twice in the header, one answer is shown,
   and it may be for the wrong player. (The setup validator forbids a *not-in-play*
   duplicate, so this only arises when the Storyteller assigns by hand or a
   Philosopher/Pit-Hag creates a second copy — but it is silent data loss.)

2. **P0 · Tokens the Drunk places are treated as real effects.**
   The tool tray offers the **believed** character's reminders
   (`NightScreen.kt:202`, `activeCharacter = characterById(step.id)` at
   `NightScreen.kt:98-100`). A Drunk Monk places a genuine `monk:"Safe"`, and
   `StatusEffects.kt:66` then reports "Marked 'Safe' (Monk) — protected from the
   Demon", and `SeatSheet.kt:256-265` puts the Demon kill behind a "might be
   protected / Death prevented" dialog. The Drunk's protection is worthless and
   the app says the opposite. Repro: Drunk believes they are the Monk, place
   `Safe` on the Mayor, Imp targets the Mayor.

3. **P1 · No impairment marker on the night step row.**
   `NightStepRow` (`NightScreen.kt:735-742`) prints the holder's name plainly. For
   any believed character outside `InfoCalc.supports` — Monk, Slayer, Soldier,
   Virgin, Mayor, Butler-adjacent — the Storyteller gets **no** signal that the
   holder is the Drunk anywhere on the night sheet.

4. **P1 · No false-info help for the non-numeric believed characters.**
   `NightScreen.kt:903-930` only offers alternatives when the headline starts with
   a digit or YES/NO. A Drunk Washerwoman / Librarian / Investigator / Undertaker
   / Ravenkeeper / Dreamer sees the *true* result plus a red note, and must be
   lied to by hand. `startKnowing` (`InfoCalc.kt:408-421`) does not even suggest
   a plausible false pair.

5. **P1 · The unreliability rule is nowhere in the app.**
   The wiki is explicit that Drunk info should *sometimes* be true. The app's only
   text is "give false info" (`InfoCalc.kt:145-146` for tokens;
   `InfoCalc.kt:137` for the Drunk itself) and "False info to show instead:"
   (`NightScreen.kt:908`), which pushes Storytellers into an always-lie pattern
   that good players learn to detect.

6. **P1 · The Virgin does not know the Drunk isn't a Townsfolk — in both
   directions.**
   `StatusEffects.kt:153-157` keys the Virgin warning on
   `nominee.characterId == "virgin"` and says "if `<nominator>` is a Townsfolk,
   they are executed immediately". If the *Drunk* nominates, the app gives no
   guidance that the Drunk is an Outsider and does **not** trigger the Virgin.
   If the Drunk *believes* they are the Virgin, `characterId` is `"drunk"` so no
   warning fires at all — correct outcome, but the Storyteller is given no note
   explaining why the "Virgin" they are looking at did nothing.

7. **P2 · Demon bluffs can collide with the Drunk's believed character.**
   `GameActions.suggestBluffs` (`GameActions.kt:121-127`) computes
   `inPlay = players.mapNotNull { it.characterId }` — which is `"drunk"`, not the
   believed Townsfolk. So the Drunk's believed character is freely offered as a
   Demon bluff. That is *legal* (the token really is not in play) but it is a
   Storyteller decision that should be flagged, not made silently.

8. **P2 · Changing the seat's character silently discards the Drunk setup.**
   `GameActions.assignCharacter` (`GameActions.kt:46-53`) sets
   `shownCharacterId = null`. Re-assigning the Drunk to a seat (or fixing a
   mis-deal) wipes the believed character with no warning; the `drunk:"Is the
   Drunk"` reminder and the "Believes they are…" note survive and now contradict
   the state.

9. **P2 · `characters.json` ability text is out of date.**
   The current official text is "You do not know you are the Drunk. You think you
   are a Townsfolk character, but you are not. **[+1 Outsider]**". The bracket is
   missing, which is why `Setup.modifierFor` falls through to the generic
   `"Modifies setup"` label at `Setup.kt:124` and the Setup screen shows an
   unexplained "[Modifies setup]" chip.

10. **P2 · No resurrection / character-change re-run.**
    If the Drunk is resurrected (Professor) or their believed character changes,
    nothing re-runs the believed character's first-night info. (Cross-cutting; the
    user called this out for the Professor.)

11. **P3 · `commonCaveats` mis-classifies the Drunk for Vortox.**
    `InfoCalc.kt:160-164` computes `holderTeam` from the **real** character
    (`drunk` → OUTSIDER), so the "VORTOX in play — Townsfolk info must be FALSE"
    caveat never fires for a Drunk who believes they are a Townsfolk. In a Vortox
    game the Drunk's info must still be false, so the outcome is accidentally
    right, but the reasoning is wrong and will break if the rule is refined.

## Proposed behaviour (spec)

### Setup (already good — keep, and add)
- Keep the `GameShell.kt:377-413` prompt.
- After picking, warn if the chosen believed character is one of the three
  demon bluffs (`state.demonBluffIds`) or vice versa: "The Demon is also bluffing
  `<X>` — two players will claim it. Intentional?"
- Guard `assignCharacter`: when the seat currently has `characterId == "drunk"`
  and is being reassigned, also clear `drunk:"Is the Drunk"` and any
  "Believes they are…" note; when a seat is being assigned **to** `"drunk"`,
  immediately re-open the believed-character prompt.

### Night behaviour
- **when:** whenever the **believed** character wakes (already correct via
  `nightRoleId`).
- **targets / immediate effects:** exactly as the believed character, so the
  Drunk's night looks identical to them — **but every token the Drunk places is
  inert.** Implement via the `PlacedReminder.inert` flag proposed in
  `poisoner.md`: the tray sets `inert = true` when the placing holder is
  `isImpaired`. `StatusEffects.deathNotes` and `SeatSheet.protectionNotes` must
  then render "…but the Monk was the Drunk: this does NOT protect."
- **information:** compute the believed character's **true** answer, then present:
  - a prominent "`<name>` IS the Drunk — their ability does nothing" banner at the
    **top** of the panel (not buried in caveats), and
  - a **"What to tell them"** row offering: the true answer (labelled "true — use
    sparingly, it keeps them guessing"), and every plausible false answer
    (numbers, YES/NO, character tokens, player pairs) drawn from the new
    `InfoResult.alternatives` (see `poisoner.md` / `recluse.md`).
- **visibility:** the Drunk sees whatever their believed character would see.
  They are never told anything about being the Drunk.
- **expiry:** `drunk:"Is the Drunk"` never expires.

### Fix the shared-step holder problem
`NightStep` should carry per-holder panels rather than a single
`playerIds.firstOrNull()`:
```kotlin
data class NightStep(
    val id: String,
    val title: String,
    val detail: String,
    val playerIds: List<Long> = emptyList(),
)
```
`StepDetailPanel` must loop over `step.playerIds`, rendering one
`QuickResolutions` + one `InfoCalc` block **per holder**, each headed by that
player's name and impairment state. `QuickResolutions` (`NightScreen.kt:467`)
must take an explicit `holder: Player` rather than deriving it.

### Day-time
- `StatusEffects.nominationWarnings`: when the nominator is the Drunk and the
  nominee is an un-impaired Virgin, emit "`<nominator>` is the Drunk (an
  Outsider) — the Virgin does **not** trigger."
- Day-start briefing: list "`<name>` thinks they are the `<X>`" so the Storyteller
  can follow their public claims.

### UI text
- Banner on the believed character's step:
  "`<name>` is the DRUNK — they think they are the `<X>`. Their ability does
  nothing. Information you give may be true or false; make it plausible."
- After a Drunk places a token: "Placed for show — this `<label>` has no effect."

### Data changes
- `characters.json` `drunk.ability`: append " [+1 Outsider]" to match the current
  official text, and confirm `Setup.modifierFor` still yields a zero net change
  for the app's seat model (the Drunk seat already occupies the Outsider slot —
  add a `SetupTest` pinning this).
- `night_guide.json`: add a `drunk` entry used as an **overlay** on the believed
  character's guide, with text: "This player is the Drunk. Run the step exactly
  as the `<X>` would be run, but their ability has no effect. Give information
  that is usually wrong and occasionally right."

## Tests to add

1. `Given` a real Empath at seat 2 and a Drunk-as-Empath at seat 5, `When` the
   other-night sheet is built, `Then` the `empath` step has two `playerIds` and
   `InfoCalc` is computed for **both** (once the per-holder panel exists).
2. `Given` a Drunk-as-Monk who placed `monk:"Safe"` on the Mayor, `When`
   `deathNotes(mayor)` is computed, `Then` the note states the protection does
   not apply.
3. `Given` a Drunk seat, `When` `StatusEffects.isImpaired` is called,
   `Then` it is `true` (regression guard — works today).
4. `Given` a Drunk whose `shownCharacterId` is a **not-in-play** Townsfolk,
   `When` `validateSetupState` runs, `Then` no issue is reported; `Given` an
   in-play Townsfolk or an Outsider, `Then` an issue is reported (regression
   guard — works today).
5. `Given` a Drunk seat, `When` `assignCharacter(seat, "chef")` is called,
   `Then` `shownCharacterId` is null **and** the `drunk:"Is the Drunk"` reminder
   is gone.
6. `Given` a Drunk who believes they are the Washerwoman, `When`
   `InfoCalc.compute("washerwoman", holder = drunkSeat)`, `Then` `caveats`
   contain "IS the Drunk" and `alternatives` contain at least one false pairing.
7. `Given` a Drunk nominator and an un-impaired Virgin nominee, `When`
   `nominationWarnings` runs, `Then` it says the Virgin does not trigger.
8. `Given` a Drunk-as-Chef in a Vortox game, `When` `InfoCalc.compute("chef",
   holder = drunkSeat)`, `Then` the caveats explain the info must be false
   (whether via the Drunk rule or the Vortox rule).
9. `Given` `demonBluffIds` containing the Drunk's believed character, `When`
   setup validation runs, `Then` an advisory (not a blocker) is produced.
