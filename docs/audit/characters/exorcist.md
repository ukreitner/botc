# Exorcist (exorcist) — Bad Moon Rising Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Exorcist> (fetched 2026-08-25).

Current ability text:

> "Each night*, choose a player (different to last night): the Demon, if chosen, learns who
> you are then doesn't wake tonight."

How to Run (wiki):

- *"Each night except the first, wake the Exorcist and have them select a player. Mark that
  selection with a **CHOSEN** reminder token."*
- If the Exorcist picked the Demon: *"Show them the **THIS CHARACTER SELECTED YOU** info token
  and the Exorcist token, then point at the Exorcist player"*, then put the Demon back to
  sleep **without waking them later for their attack**.
- *"The Exorcist cannot select the same player on consecutive nights."*

Edge cases and clarifications:

- **A chosen Demon still wakes for other reasons:** *"A Demon chosen by the Exorcist will not
  wake to use their Demon ability, but will still wake if they need to due to other
  characters' abilities."* (e.g. Barber swap, Poppy Grower reveal, Marionette info.)
- **Dead players are legal targets:** *"If you suspect that a Zombuul is in-play, feel free to
  pick dead players."* (The Zombuul acts while registering as dead.)
- **The Exorcist does not wake on the first night.**
- Worked examples: Shabaloth chosen → no kill that night. **Pukka chosen → the Pukka does not
  wake to poison tonight, but a player poisoned on a previous night still dies.** (Directly
  relevant to the reported Pukka bug — the Exorcist does not cancel already-scheduled deaths.)
  Po chosen after a night where the Po chose nobody → the Po does not act; the next night the
  Po may still take its three kills.
- Whether the Exorcist may choose **themselves** is not stated on the page I fetched. The
  ability says "choose a player" with no self-exclusion, so self-selection is legal by the
  default reading; **flagging as unverified.**
- A **drunk or poisoned Exorcist** has no effect: the Demon wakes and acts normally and learns
  nothing. (Stated in the app's own `night_guide.json` and consistent with the general
  drunk/poison rule; not quoted on the wiki page I fetched.)

Jinxes (verbatim from the page's "Related Jinxes" section — I could not cross-verify the
Leviathan/Riot wording against a second source, so **treat as unconfirmed**):

- **Leviathan:** "If the Leviathan nominates and executes the Exorcist-chosen player, good wins."
- **Riot:** "If Riot nominates and executes the Exorcist-chosen player, good wins."
- **Yaggababble:** "If the Exorcist chooses the Yaggababble, the Yaggababble does not kill tonight."

## What the app does today

- `characters.json:389-399` — ability text matches. `firstNightReminder` is empty (correct —
  no first-night step). `reminders: ["Chosen"]`.
- `night_and_jinxes.json:405` — `exorcist` at otherNight index **32**, before every Demon
  (36–54) and after the Lunatic (31). **Correct ordering** and important: it guarantees the
  token is placed before the Demon's step is reached.
- `night_and_jinxes.json:289-291` — the `exorcist`/`yaggababble` jinx is present.
- `night_guide.json:227-239` — good prose, and a prepared show card
  `{"label": "Show the Demon", "kind": "token", "text": "THIS PLAYER STOPPED YOU TONIGHT",
  "token": "self"}` which renders the Exorcist token full-screen
  (`NightScreen.kt:366-454` → `ShowCard.CharacterCard`). **Works**, except the caption differs
  from the official token ("THIS CHARACTER SELECTED YOU").
- `NightOrder.kt:149-154` — when a Demon's seat carries `exorcist:Chosen`, the Demon's step
  **detail string** gets ` — EXORCIST chose them: the Demon does not act tonight.` appended.
  Covered by `engine/src/test/kotlin/com/clocktower/engine/StatusEffectsTest.kt:57-69`.
- `GameActions.kt:221` — `"exorcist" to "Chosen"` is in `EXPIRES_AT_DAWN`, so the token is
  swept at every dawn (`GameActions.kt:260`).
- Token placement is manual: tap "Chosen" in the night tray, then a seat
  (`NightScreen.kt:283-295`). Because `Chosen` is a single-copy reminder,
  `placeExclusiveReminder` moves it rather than duplicating (`GameActions.kt:194-201`).

## Defects and gaps

1. **P0 · A silenced Demon is still offered a kill.** `QuickResolutions`
   (`NightScreen.kt:518-522`) renders `DemonKillPanel` for *any* step whose character is on
   the Demon team and whose holder is alive. The `exorcist:Chosen` token is never consulted
   there — only in the step's *text* (`NightOrder.kt:149-154`). So the storyteller sees the
   warning sentence and, immediately below it, a full "Demon kill — who did Aurora choose?"
   panel with every seat as a chip and a live "X dies" button. This is exactly the reported
   Pukka failure mode ("it offered to kill even though it's supposed to…"). Repro: BMR, night
   2, place `exorcist:Chosen` on the Po's seat, open the Po step — the kill panel is fully
   live.

2. **P0 · The "different to last night" constraint cannot be honoured, because the app
   deletes the evidence at dawn.** `GameActions.kt:221` sweeps `exorcist:Chosen` in
   `EXPIRES_AT_DAWN`. By the time the Exorcist's step comes round again there is no record of
   last night's pick anywhere in `GameState` — `nightStepsDone` (`GameState.kt:106`) stores
   only step ids and is cleared at every phase change (`GameActions.kt:259,262`). The
   storyteller must remember it themselves, and the app cannot warn on an illegal repeat.
   Repro: night 2 choose Hector; dawn; night 3 — the grimoire is clean and the step says only
   "different from the previous night" with no indication of who that was.

3. **P1 · The app never tells the storyteller whether the chosen player is the Demon**, even
   though it knows. The step is a wall of conditional prose ("If that player is the Demon:
   Wake the Demon…", `characters.json:396`). The storyteller has to check the grimoire by eye
   at the exact moment they are trying to keep the table quiet. The app should resolve the
   branch and show one of two short instructions.

4. **P1 · The "Chosen" token is placed by hand and the show card is a separate hunt.** The
   step needs one flow: tap a player → app says "that's the Demon" → one button that places
   the token, opens the full-screen card, and marks the Demon's step suppressed.

5. **P1 · The Exorcist's own impairment is not surfaced.** A drunk/poisoned Exorcist's choice
   does nothing and the Demon must act normally — but the app would still annotate the Demon's
   step and (once fixed) suppress the kill panel, producing a **wrong outcome**.
   `InfoCalc.impairments` (`InfoCalc.kt:132-153`) already computes the right sentence but is
   only reachable from `InfoCalc.compute`, and the Exorcist is not in `supports`
   (`InfoCalc.kt:29-36`).

6. **P1 · A stale annotation is possible today and would get worse with a persistent token.**
   `NightOrder.kt:150-153` checks only "is the token on this seat", never "was it placed
   tonight". Today the dawn sweep hides this; once the token persists (needed for defect 2),
   a dead Exorcist would leave a permanent "the Demon does not act tonight" annotation on the
   Demon's step forever. Any fix must make the suppression **cycle-scoped**.

7. **P2 · The show card caption drifts from the official token.**
   `night_guide.json:233-237` says "THIS PLAYER STOPPED YOU TONIGHT"; the official info token
   is "THIS CHARACTER SELECTED YOU", shown together with the Exorcist character token.

8. **P2 · Missing Leviathan and Riot jinxes** in `night_and_jinxes.json` (only the
   Yaggababble one is present, `:289-291`), so nothing warns the storyteller in a
   Leviathan/Riot game. (Verify wording first — see Sources.)

9. **P2 · No multi-Demon handling.** Legion, Kazali, Lord of Typhon and Lil' Monsta change what
   "the Demon" means. `NightOrder.kt:150` keys off `character.team == Team.DEMON` on the step's
   holders, which is right for ordinary Demons but wrong for Lil' Monsta (the Demon "acts"
   through the Minion holding the babysitter token, and `lilmonsta` sits at otherNight index
   50 with no seat holder). The step should say what the Exorcist choice means in those games.

10. **P3 · No dawn/day trace.** Nothing records that the Exorcist blocked the Demon on night 3,
    so the game log (`GameExtras.kt:46-106`) shows an unexplained quiet night.

## Proposed behaviour (spec)

- **when:** other nights only. Wake condition: `holder.alive` and the Exorcist is not
  themselves silenced. (There is no once-per-game limit.)
- **targets:** exactly 1 player.
  - Constraints: **must differ from the Exorcist's choice on the immediately preceding
    night.** Dead players are legal (Zombuul). Self-selection is legal by the default reading
    of "choose a player" — allow it, but flag it in the UI as unverified rather than blocking.
  - Picker defaults/sorting: alive first, then dead; **the previous night's target is rendered
    disabled with the label `chosen last night`**, so the constraint is enforced by the UI
    rather than by the storyteller's memory.
- **immediate effects** on confirming target `T`:
  - Record `NightChoice(cycle, "exorcist", holderId, targetIds = [T])` (the general
    night-choice record proposed in `courtier.md`). This is the durable memory that survives
    the dawn sweep and drives the next night's constraint.
  - `placeExclusiveReminder(state, T, PlacedReminder("exorcist", "Chosen"))`.
  - If `T` holds a Demon **and the Exorcist is not impaired**: additionally place
    `PlacedReminder("exorcist", "Silenced tonight")` on `T` (a second, dawn-expiring token
    that is the *only* thing the Demon-step suppression reads). Splitting the two tokens is
    what makes both requirements satisfiable at once: `Chosen` persists for the
    different-to-last-night rule; `Silenced tonight` expires at dawn so the suppression can
    never go stale.
  - Auto-open the "THIS CHARACTER SELECTED YOU" full-screen card when `T` is the Demon.
- **deferred effects:** the Demon's step, later the same night, must be **suppressed** — see
  below. Nothing carries into the day.
- **expiry:**
  - `exorcist:Silenced tonight` → add to `EXPIRES_AT_DAWN` (`GameActions.kt:218-225`).
  - `exorcist:Chosen` → **remove** from `EXPIRES_AT_DAWN`; it now persists until the Exorcist
    moves it (`placeExclusiveReminder` already moves rather than duplicates). If the Exorcist
    dies, the token is left in place as a historical mark and is harmless, because nothing
    reads it for suppression any more.
- **suppression of the Demon's step (the actual fix for defect 1):**
  - `NightOrder.kt:149-154`: change the condition to look for `exorcist:Silenced tonight`, and
    change the appended text to a leading, imperative line:
    `THE EXORCIST CHOSE THEM — do not wake this Demon. No kill tonight.`
  - `NightScreen.kt:518-522` (`QuickResolutions`): when the Demon's holder carries
    `exorcist:Silenced tonight`, **do not render `DemonKillPanel` at all.** Render instead a
    single card: `Silenced by the Exorcist — the Demon does not act tonight.` with a small
    `[Override — they act anyway]` text button for house rules/mistakes.
  - The step should still be tickable so the checklist completes.
  - Deferred deaths from previous nights are **not** cancelled — the Pukka's poisoned victim
    still dies. Add that sentence to the suppression card verbatim, because the wiki calls it
    out and it is the single most common Exorcist misplay.
- **information:** the Exorcist learns nothing. The Demon learns *who the Exorcist is*.
- **visibility:** the Demon sees the "THIS CHARACTER SELECTED YOU" info token + the Exorcist
  character token, and is pointed at the Exorcist's seat. Nothing is shown to Minions. If a
  Lunatic is in play, the Lunatic is **not** affected by the Exorcist (they are not the Demon);
  the real Demon's step already carries the Lunatic annotation (`NightOrder.kt:157-171`) and
  the two annotations must read cleanly together.
- **day-time inputs:** none.
- **interactions/jinxes:**
  - **Pukka:** silencing stops tonight's *poisoning*; the previously poisoned player still
    dies. Must be stated on the suppression card.
  - **Po:** a silenced Po still has whatever "no kill last night" state it had; do not clear it.
  - **Zombuul:** legal to choose a dead player.
  - **Yaggababble** (`night_and_jinxes.json:289-291`): choosing the Yaggababble stops its
    public-phrase kill that night — surface the jinx text on both steps.
  - **Leviathan / Riot:** add the jinxes and surface them at nomination time via
    `StatusEffects.nominationWarnings` (`StatusEffects.kt:131-166`) — the trigger is a
    nomination + execution of the Exorcist-chosen player, which is exactly what that hook is
    for. Verify the wording first.
  - **Lil' Monsta / Legion / Kazali / Lord of Typhon:** the step should state, when those are
    in play, which seat counts as "the Demon" for this purpose. At minimum, when the chosen
    player carries the Lil' Monsta babysitter token, treat the `lilmonsta` step as suppressed.
  - **Mathematician:** an impaired Exorcist whose choice fails counts as a malfunction.

### UI text the step should display

- Header: `Exorcist — choose a player (not Hector, chosen last night).`
- Non-Demon pick: `Blake isn't the Demon. Nothing happens. Put the Exorcist back to sleep.`
- Demon pick: `Aurora IS the Demon. Wake her, show "THIS CHARACTER SELECTED YOU" + the
  Exorcist token, point at Kendra, then sleep. She does not act tonight.`
- Impaired Exorcist: `Kendra is poisoned — the Demon wakes and acts as normal, and learns
  nothing. Place the token anyway so next night's choice must differ.`
- On the Demon's step when silenced: `Silenced by the Exorcist — do not wake. No kill tonight.
  (Deaths already scheduled from earlier nights still happen.)`

### Data changes

- `characters.json:397` — add `"Silenced tonight"` to the Exorcist's `reminders` (or keep it
  engine-internal and out of the tray; either is fine, but the grimoire circle should render it
  so the storyteller can see the block).
- `night_guide.json:233-237` — change the show text to `THIS CHARACTER SELECTED YOU`, and add
  a second show card for the Exorcist token itself if the two are shown separately.
- `night_guide.json:228-232` — add: "Deaths already scheduled from earlier nights (Pukka
  poison, Gossip, Assassin) still happen."
- `night_and_jinxes.json` — add the `exorcist`/`leviathan` and `exorcist`/`riot` jinxes once
  their wording is verified.

## Tests to add

1. **Silenced Demon step is suppressed, not merely annotated.** Given a BMR game with an
   Exorcist and a Po on night 2, When the Exorcist choice is resolved onto the Po's seat, Then
   the Po's `NightStep.detail` starts with the do-not-wake line **and** a new
   `NightOrder`/`StatusEffects` predicate (e.g. `StatusEffects.demonIsSilenced(state, seatId)`)
   returns `true`, which the UI uses to withhold `DemonKillPanel`. Extends
   `StatusEffectsTest.kt:57-69`, which today only asserts the annotation exists.

2. **Suppression is cycle-scoped.** Given `exorcist:Chosen` on the Po's seat left over from
   night 2 and no Exorcist action on night 3 (Exorcist dead), When the night-3 sheet is built,
   Then the Po's step is **not** annotated and `demonIsSilenced` is `false`. **Fails today**
   only because the dawn sweep hides it; fails outright once `Chosen` persists.

3. **Chosen token survives dawn.** Given `exorcist:Chosen` on seat 4, When `advancePhase`
   moves NIGHT → DAY, Then the token is still on seat 4 (regression against
   `GameActions.kt:221`), while `exorcist:Silenced tonight` **is** removed.

4. **Different-to-last-night is derivable.** Given `NightChoice(cycle = 2, sourceId =
   "exorcist", targetIds = [4])`, When the night-3 Exorcist step is prepared, Then the picker's
   disabled set is exactly `{4}`; and given no night-2 choice, Then it is empty.

5. **Impaired Exorcist does not silence.** Given the Exorcist carries `poisoner:Poisoned`,
   When they choose the Demon, Then `exorcist:Chosen` is placed but `Silenced tonight` is not,
   and `demonIsSilenced` is `false`.

6. **Dead players are selectable.** Given a dead Zombuul, Then the Exorcist picker includes it
   and choosing it is legal.

7. **No first-night step.** Given a BMR game with an Exorcist, When
   `nightOrder.firstNight(...)` is built, Then no step has `id == "exorcist"`. (Passes today —
   regression guard.)

8. **Lunatic and Exorcist annotations coexist.** Given a Lunatic and an Exorcist-silenced
   Demon on night 3, Then the Demon's step contains both the do-not-wake line and the Lunatic
   line (`NightOrder.kt:157-171`) and reads unambiguously.
