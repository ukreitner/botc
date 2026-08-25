# Philosopher (philosopher) — Sects & Violets Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Philosopher> (fetched 2026-08-25).

Current ability text:

> "Once per game, at night, choose a good character: gain that ability. If this
> character is in play, they are drunk."

**How to run (quoted from the wiki):**

> "Each night, wake the Philosopher. They either shake their head no or point at
> any Townsfolk icon or any Outsider icon on their character sheet. Put the
> Philosopher to sleep.
>
> If they pointed to an icon of a character **not in play**, swap the Philosopher
> token with the chosen character token and mark them with the IS THE PHILOSOPHER
> reminder.
>
> If they pointed to an icon of a character **in play**, the player of the chosen
> character becomes drunk — mark them with the DRUNK reminder. (You can now use
> the duplicated character's reminders for the Philosopher). **If the Philosopher
> dies, the player made drunk by the Philosopher becomes sober** — remove the
> DRUNK reminder."

**Examples (quoted):**

> - "During the first night, the Philosopher chooses to gain the Dreamer's
>   ability. They gain the Dreamer's ability from now on and act when the Dreamer
>   normally acts."
> - "During the third night, the Philosopher chooses to gain the Clockmaker's
>   ability. That night, they learn the distance from the Demon to their nearest
>   Minion."
> - "An Artist is in play. The Philosopher chooses to gain the Artist's ability.
>   The original Artist becomes drunk. Later, the Philosopher dies, so the
>   original Artist becomes sober again. (The original Artist would also become
>   sober if the Philosopher became drunk.)"

**Clarifications (quoted):**

> - "If the Philosopher chooses a character that is not in play at the time but is
>   in play now, that character is drunk."
> - "If the Philosopher gains an ability that works at night, they wake when that
>   character would wake. **If this ability is used on the first night only, they
>   use it tonight.**"
> - "If the Philosopher's ability works while dead, such as the Klutz's, it works
>   if the Philosopher is dead."
> - "They gain that character's full ability and do not become the character
>   themselves." (Summary section.)
> - Reactivation (Bone Collector / Barista double use): "the Philosopher may
>   select a new ability or repeat their previous choice."

**Timing facts that matter to the app**

- Night order: first night position 9 (`night_and_jinxes.json:304`), i.e. *before*
  MINION_INFO (14) / DEMON_INFO (18) and before every information Townsfolk.
  Other nights position 7 (`night_and_jinxes.json:380`), also very early.
- Consequence: a gained ability whose own slot is later tonight can be run
  tonight; a gained ability whose slot is *earlier* than 7 on other nights
  (barista 1, bureaucrat 2, thief 3, plague doctor 4, bone collector 5, harlot 6)
  starts next night. First-night-only abilities (Clockmaker, Chef, Washerwoman,
  Steward, …) are explicitly run *tonight* per the wiki.
- The choice is optional and repeatable-until-used: the Philosopher wakes **every
  night** until they spend it.

**Jinx**

- Philosopher × Bounty Hunter (`night_and_jinxes.json:159`): "If the Philosopher
  gains the Bounty Hunter ability, a Townsfolk might turn evil." (Matches the
  wiki's Related Jinx section.)

## What the app does today

**Data only — there is zero Philosopher code anywhere.**
`grep -rn philosopher --include=*.kt engine/src/main app/src/main` returns nothing.

- `engine/src/main/resources/botc/data/characters.json:878-892` — id/name/ability,
  `reminders: ["Drunk", "Is the Philosopher"]`, `setup: false`,
  first/other night reminder text that says to swap the token and place Drunk.
- `engine/src/main/resources/botc/data/night_and_jinxes.json:304` (first night 9),
  `:380` (other night 7), `:159` (Bounty Hunter jinx).
- `engine/src/main/resources/botc/data/night_guide.json:505-528` — how-to-run prose
  plus one prepared show card `{label:"Chosen ability", kind:"token",
  text:"You now have this ability…", token:"pick"}`.
- `NightOrder.build` (`engine/src/main/kotlin/com/clocktower/engine/NightOrder.kt:142-178`)
  emits the row whenever a seat's `nightRoleId` is `philosopher`. There is no
  once-per-game gating (`NightOrder.kt:145` only skips rows with no holders).
- `NightScreen.NightToolTray` (`app/src/main/java/com/clocktower/grimoire/ui/screens/NightScreen.kt:193-357`)
  offers the "Drunk" and "Is the Philosopher" chips (tap token → tap seat), a
  "Sheet" card for silent pointing (`NightScreen.kt:254-262`, backed by
  `ShowCard.SheetCard`, `components/ShowCards.kt:71-77` — whose doc comment even
  names the Philosopher), and a "Mark spent" chip because
  `character.ability.startsWith("Once per game")` is true (`NightScreen.kt:204`,
  `:263-279`).

**The storyteller's actual experience.** The Philosopher row appears every night
forever. When the player points at a character, the ST has to:

1. Open the seat sheet → "Change character" → pick the gained character
   (`SeatSheet.kt:88-96` → `GameActions.assignCharacter`, `GameActions.kt:46-53`),
   which overwrites `characterId` and *nulls* `shownCharacterId`; the seat is now,
   as far as the whole engine is concerned, literally that character.
2. Re-open "Add reminder" and hunt for the Philosopher's "Is the Philosopher"
   token under **"Rest of script"** (`SeatSheet.kt:535-569`) — the Philosopher is
   no longer "in play" once the token was changed.
3. Remember, unaided, to place "Drunk" on the duplicate, and remember for the
   rest of the game to remove it if the Philosopher dies or is poisoned.
4. Remember, unaided, which night slot the gained ability runs at when it is a
   first-night-only ability.

Nothing is recorded about what was gained, nothing marks the ability spent in a
way the night sheet respects, and nothing links the two seats.

## Defects and gaps

1. **P0 · The gained ability makes the Philosopher *register* as the gained
   character.** The only way to move the Philosopher's night step is
   `assignCharacter` (`GameActions.kt:46-53`), which changes `characterId`. Every
   consumer of `characterId` then lies: `InfoCalc.startKnowing`
   (`InfoCalc.kt:408-421`) reports the Philosopher as a Washerwoman/Librarian/
   Investigator hit for the gained character; `InfoCalc.dreamer`/`revealCharacter`
   (`InfoCalc.kt:344-354`, `:376-384`) shows the gained character to the Dreamer/
   Ravenkeeper/Grandmother instead of "Philosopher"; a gained *Outsider* ability
   (Klutz, Sweetheart) makes the seat count as an Outsider for the Librarian and
   the Balloonist (`InfoCalc.kt:486-496`); `StatusEffects.derivedPoison`
   (`StatusEffects.kt:14-33`) recomputes the No Dashii's Townsfolk neighbours
   around the changed team. Rules: the Philosopher *does not become* the
   character. Repro: give a seat `philosopher`, use Change character → Klutz, open
   the Librarian night step — the Philosopher is listed as an in-play Outsider.
2. **P0 · Duplicate-character case silently breaks the night sheet.** When the
   chosen character IS in play the correct grimoire has two players associated
   with that character. If the ST follows `night_guide.json:508` ("swap in that
   character's token") the app now has two seats with the same `characterId`;
   `NightOrder` groups both into one row (`NightOrder.kt:46-48, :173-178`) but
   `StepDetailPanel` computes the info for `step.playerIds.firstOrNull()` only
   (`NightScreen.kt:837`). The ST is shown one seat's answer with no indication
   that a second holder exists, and the "Mark spent" chip fires on *both*
   (`NightScreen.kt:268-276`). Repro: Empath in play, Philosopher changed to
   Empath, open the Empath row — one Empath answer, two names in the header.
3. **P0 · The duplicate is never made drunk, and the drunkenness never ends.**
   Nothing places `("philosopher","Drunk")`, and nothing removes it when the
   Philosopher dies or becomes drunk/poisoned — the wiki states both endings
   explicitly. `EXPIRES_AT_DAWN`/`EXPIRES_AT_DUSK` (`GameActions.kt:218-242`)
   correctly do *not* list it (it is not time-scoped), but the engine has no
   *conditional* expiry mechanism at all, so this can only ever be manual.
   Repro: place Drunk on the duplicate, kill the Philosopher — the token stays and
   `StatusEffects.isImpaired` (`StatusEffects.kt:36-46`) keeps returning true, so
   `InfoCalc` keeps telling the ST to give that player false info forever.
4. **P0 · The wiki's in-play rule is contradicted by the app's own guide text.**
   `night_guide.json:507-509` (and `characters.json:882-884`) instruct the ST to
   "swap in that character's token" unconditionally. Officially the token swap
   happens **only when the chosen character is not in play**; when it is in play
   the Philosopher keeps their token and you re-use the duplicated character's
   reminders. Following the app's text produces defect 2.
5. **P1 · No once-per-game gating.** The Philosopher row is emitted every night
   for the rest of the game even after the ability is spent
   (`NightOrder.kt:142-178`), and the "Mark spent" chip writes
   `PlacedReminder("philosopher","No ability")` (`NightScreen.kt:270-274`) — a
   label that is not in the Philosopher's `reminders` array
   (`characters.json:887-890`), so it never appears in the tray, and nothing reads
   it. The ST must remember "already used" for up to five nights.
6. **P1 · Nothing records what was gained or when.** No field, no note, no log
   entry (`GameExtras.GameLogDialog`, `screens/GameExtras.kt:46-108`, derives
   entries only from deaths and nominations). After a Change character the app
   cannot even tell you that this seat *is* the Philosopher.
7. **P1 · Deferred activation is entirely manual.** A first-night-only gained
   ability (Clockmaker, Chef, Steward, Washerwoman, Shugenja…) must be run
   *tonight*, at the Philosopher's early slot, but has no row; a gained
   other-night ability must appear at that character's slot from tonight onwards.
   The app offers neither an inline "run it now" affordance nor an inserted row.
8. **P1 · Gained once-per-game abilities have no independent "spent" state.** A
   Philosopher who gains the Seamstress must get their own use even if the real
   Seamstress already spent hers; the app's spent marker is a token on a seat, so
   this happens to work — but the *converse* (a Philosopher-Seamstress marked
   spent also hiding the real Seamstress's row, once gating from defect 5 is
   implemented) will break unless the gating is per-seat, not per-character.
9. **P1 · Gained abilities with day-time inputs are unreachable.** Savant (no
   day tooling at all — see `docs/audit/characters/savant.md`), Artist, Fisherman,
   Juggler, Gossip all need a day-phase prompt keyed on the *acting* character;
   the app has no concept of an acting character.
10. **P2 · The character picker for the choice is a raw seat action.** There is no
    "Philosopher gains…" picker restricted to script Townsfolk + Outsiders, sorted
    not-in-play first (which is the strategically and mechanically relevant split).
    The existing `CharacterPicker` (`SeatSheet.kt:387-453`) is a full assignment
    picker that also lists Minions, Demons and Travellers.
11. **P2 · Bounty Hunter jinx is not surfaced at the moment of choice.** It is
    shown in the seat sheet's jinx list (`SeatSheet.kt:222-234`) only when both
    characters are already in play — which is exactly not the case here, since the
    Bounty Hunter would be the *gained* character.
12. **P2 · Dead-Philosopher abilities.** "If the Philosopher's ability works while
    dead, such as the Klutz's, it works if the Philosopher is dead." Today a dead
    holder gets the blanket "All holders are dead — usually skip" warning
    (`NightScreen.kt:751-757`).
13. **P3 · Show card ergonomics.** The prepared card
    (`night_guide.json:511-518`) is `token:"pick"`, so the ST must search the
    character list again in `GuideShowDialog` (`NightScreen.kt:366-454`) even
    though the app could already know what was chosen.

## Proposed behaviour (spec)

### Model change (the enabling piece)

Add to `Player` (`GameState.kt:15-53`):

```kotlin
/** Ability this seat currently ACTS with, when it isn't their own
 *  (Philosopher gain, Alchemist, Boffin-granted demon ability…). The seat
 *  still registers as [characterId] for team/alignment/info purposes. */
val actingCharacterId: String? = null,
/** Where the acting ability came from, for grimoire labelling and log. */
val actingSourceId: String? = null,   // e.g. "philosopher"
```

and change `nightRoleId` (`GameState.kt:39-44`) to:

```kotlin
val nightRoleId: String?
    get() = actingCharacterId
        ?: if (characterId == "drunk" || characterId == "marionette") shownCharacterId ?: characterId
        else characterId
```

`team()`, `isEvil()`, `InfoCalc` registration and `Setup`/`WinCheck` keep using
`characterId` — which is what fixes defect 1. `InfoCalc.compute` should be called
with `nightRoleId` for the *calculation* and `characterId` for *what other
characters see*.

### Night step

- **when**: both first and other nights; wake condition = seat's
  `characterId == "philosopher"` **and** `actingCharacterId == null` **and**
  (alive **or** already-gained-a-dead-working-ability). Once
  `actingCharacterId != null` the Philosopher row is not emitted; instead the
  gained character's row is emitted for this seat (see "deferred effects").
- **targets**: 0 seats; 1 *character* from `script ∩ (Townsfolk ∪ Outsider)`,
  or "no choice". Picker default sort: not-in-play first (token swap case), then
  in-play (drunk case) with an inline "⚠ makes <name> drunk" badge. Exclude
  Travellers and evil characters. Choosing is optional every night.
- **immediate effects** (new `GameActions.philosopherGain(state, seatId,
  gainedId, lookup)`):
  - `actingCharacterId = gainedId`, `actingSourceId = "philosopher"`.
  - place `PlacedReminder("philosopher","Is the Philosopher")` on the Philosopher
    seat, exclusive.
  - if any other seat has `characterId == gainedId`: nothing is stored — the
    drunkenness is **derived** (see below) so it self-corrects.
  - append a `GainRecord(day = cycle, seatId, gainedId)` to a new
    `state.abilityGrants` list (also consumed by the game log).
- **derived status effect (replaces a stored token)** — add to
  `StatusEffects`:

  ```
  derivedDrunk(state, lookup): Map<Long,String>
    for each seat P with characterId=="philosopher" && actingCharacterId!=null
        && P.alive && !isImpaired(P):
      for each other seat Q with Q.characterId == P.actingCharacterId:
        Q.id -> "Drunk: the Philosopher (${P.name}) took their ability"
  ```

  and fold it into `isImpaired` (`StatusEffects.kt:36-46`) next to
  `derivedPoison`. This gives, for free: automatic drunkenness when the duplicate
  *enters play later* (Pit-Hag/Alchemist/second Philosopher — the wiki's "is in
  play now" clause), automatic sobriety when the Philosopher dies or is
  poisoned, and automatic re-drunkening if the Philosopher is later cured. Still
  render it in the grimoire as a red "Drunk (Philosopher)" chip so the ST sees it.
- **deferred effects**:
  - From this night on, the seat's night rows come from `nightRoleId`, so
    `NightOrder` places it at the gained character's slot automatically.
  - **Same-night activation.** After `philosopherGain`, if the gained character
    has a non-blank night reminder for tonight *and* its canonical index is
    greater than the Philosopher's index for tonight, the row already appears
    later tonight — do nothing. Otherwise (first-night-only abilities on a later
    night, or an other-night index < 7) insert an **inline sub-step** immediately
    under the Philosopher row: `"Run the <Name> for <player> now"`, rendering the
    same `NightGuide` + `InfoCalc` panel. Implement by letting `NightOrder.build`
    emit an extra `NightStep(id = "philosopher:$gainedId", …)` when
    `grantCycle == state.cycle` and the gained slot has passed.
  - If the gained ability works while dead (Klutz, and the Philosopher's own row
    when re-granted), do not show the "All holders are dead" warning
    (`NightScreen.kt:751-757`) for `philosopher`-sourced rows.
- **expiry**: `("philosopher","Is the Philosopher")` never expires. There is no
  `("philosopher","Drunk")` token any more — the drunkenness is derived. If the
  implementer prefers to keep a token for grimoire realism, add a *conditional*
  expiry pass in `advancePhase` and after every kill: remove
  `("philosopher","Drunk")` when the Philosopher seat is dead or impaired.
- **information**: none of its own. The gained ability's `InfoCalc` runs with
  `holderId` = the Philosopher's seat and `characterId` = `nightRoleId`. All
  existing impairment caveats apply to the Philosopher seat, not the duplicate.
- **visibility**:
  - Show card 1 (already in the guide): "You now have this ability…" + the gained
    character token, pre-filled — no picker needed.
  - The player points silently using the existing full-script `SheetCard`
    ("Sheet" chip, `NightScreen.kt:254-262`) — keep.
  - Demon/Minions are shown nothing. The duplicated player is told nothing.
  - Grimoire: the Philosopher seat shows the gained token with a small
    "PHILOSOPHER" ribbon (do **not** replace `characterId`), matching the physical
    "swap the token + IS THE PHILOSOPHER reminder" convention.
- **day-time inputs**: none for the Philosopher itself; but the day-phase tooling
  for gained abilities (Savant visit, Artist question, Fisherman advice, Juggler
  guess, Gossip statement) must key on `nightRoleId`/`actingCharacterId`, not on
  `characterId`.
- **interactions/jinxes**:
  - Bounty Hunter: when the picker highlights `bountyhunter`, show the jinx text
    inline before confirming, and on confirm prompt "which Townsfolk turns evil?"
    (a seat picker that sets `alignmentFlipped`).
  - Snake Charmer gained: route the Philosopher seat through the same
    `QuickResolutions` "snakecharmer" branch (`NightScreen.kt:471-482`).
  - Sage gained: the Philosopher must wake on their own Demon-kill death.
  - Drunk/Marionette impostor who thinks they are the Philosopher: their choice
    does nothing — the ST must still run the wake and show a plausible card;
    guard `philosopherGain` behind an "this player's ability works" check and
    offer a "fake the swap" path that sets nothing.
  - Bone Collector / Barista re-grant: `philosopherGain` must be callable a
    second time and must overwrite `actingCharacterId`.

### UI text the step should display

- Header when unspent: `Philosopher — once per game. Not used yet.`
- Body: `They shake their head, or point at one Townsfolk or Outsider on the
  sheet. Tap what they pointed at.`
- On a not-in-play pick: `<Name> is NOT in play. <Player> gains the <Name>
  ability from now on and keeps the "Is the Philosopher" marker.`
- On an in-play pick: `<Name> IS in play — <Other player> is drunk for as long as
  <Player> is alive and sober. <Player> gains the ability.`
- After gaining, on later nights the row title reads
  `Empath (Philosopher — Nadia)`.
- If the gained slot already passed tonight:
  `Run the <Name> for <Player> now — their slot has already gone by.`

### Data changes

- `characters.json:882-885`: rewrite `firstNightReminder`/`otherNightReminder` to
  match the wiki's two branches (swap the token **only if not in play**; place
  DRUNK **only if in play**).
- `night_guide.json:505-528`: same correction; add a second show entry
  `{label:"Chosen ability (auto)", kind:"token", token:"gained"}` so the card
  pre-fills from `actingCharacterId`.
- `characters.json:887-890`: keep both reminders; if the derived-drunk approach is
  taken, keep "Drunk" for manual use but mark it advisory.

## Tests to add

1. **Gain a not-in-play character.** Given a 7-player game with `philosopher` on
   seat 0 and no Empath in play; When `philosopherGain(state, 0, "empath")`;
   Then `player(0).characterId == "philosopher"`,
   `player(0).actingCharacterId == "empath"`, `nightRoleId == "empath"`,
   `player(0).reminders` contains `("philosopher","Is the Philosopher")`, and
   `NightOrder.otherNight` contains a step with id `empath` whose `playerIds` is
   `[0]` and **no** step with id `philosopher`.
2. **Gain an in-play character → derived drunk.** Given seat 0 `philosopher`,
   seat 3 `empath`; When `philosopherGain(state, 0, "empath")`; Then
   `StatusEffects.isImpaired(state, lookup, player(3))` is true and
   `isImpaired(player(0))` is false.
3. **Drunkenness ends on the Philosopher's death.** Continuing (2); When
   `kill(state, 0, DeathCause.DEMON)`; Then `isImpaired(player(3))` is false.
4. **Drunkenness ends when the Philosopher is poisoned.** Continuing (2); When a
   `("poisoner","Poisoned")` reminder is added to seat 0; Then
   `isImpaired(player(3))` is false and `isImpaired(player(0))` is true.
5. **Late arrival is drunk.** Given seat 0 gained `empath` while no Empath was in
   play; When seat 3 is assigned `empath` (Pit-Hag); Then `isImpaired(player(3))`
   is true.
6. **Registration is unchanged.** Given seat 0 gained `klutz`; Then
   `InfoCalc.compute(data, state, "librarian", …)` does not list seat 0, and
   `InfoCalc.compute(data, state, "dreamer", holder, targets = listOf(0))`
   headlines "… is the Philosopher".
7. **Once-per-game gating.** Given seat 0 gained anything; Then neither
   `NightOrder.firstNight` nor `NightOrder.otherNight` emits a `philosopher` step
   for that seat.
8. **First-night-only ability gained on night 3.** Given cycle 3 and
   `philosopherGain(state, 0, "clockmaker")`; Then `NightOrder.otherNight`
   contains a step whose id is `philosopher:clockmaker` positioned immediately
   after index 7, and `InfoCalc.compute(data, state, "clockmaker", 0)` returns the
   Demon→Minion distance.
9. **Gained other-night ability whose slot already passed.** Given cycle 3 and
   `philosopherGain(state, 0, "harlot")` (other-night index 6 < philosopher's 7);
   Then the inline sub-step is emitted this night and a normal `harlot` step is
   emitted from cycle 4 onwards.
10. **Two Philosophers / re-grant.** Given seat 0 gained `empath`; When
    `philosopherGain(state, 0, "chef")`; Then `actingCharacterId == "chef"` and
    the previously drunk Empath is sober.
