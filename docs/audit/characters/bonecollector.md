# Bone Collector (bonecollector) — Sects & Violets Traveller

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Bone_Collector>
Traveller rules: <https://wiki.bloodontheclocktower.com/Travellers>,
<https://wiki.bloodontheclocktower.com/Character_Types> (§Traveller)

Current ability text (wiki):

> "Once per game, at night\*, choose a dead player: they regain their ability until dusk."

**Drift:** `characters.json:1145` says "Once per game, at night, choose a dead player:
they regain their ability until dusk." — the `*` (not the first night) is missing.
The night-order data is nevertheless correct (other nights only).

Summary clarifications (quoted):

> - "The Bone Collector must choose a dead player. The chosen player remains dead, but they
>   get their ability to use. If their ability was a 'you start knowing' or a 'once per
>   game' ability—such as the Virgin, Slayer, Clockmaker, Seamstress, or Juggler—they may
>   use it again, even if it was already used, until dusk falls."
> - "When the Bone Collector chooses a player, that player does not learn they were selected
>   by the Bone Collector, although they find out soon enough when they are woken to use
>   their ability."
> - "If the Bone Collector dies, that player no longer has the ability they regained due to
>   the Bone Collector."

How to Run (quoted in full):

> "Each night except the first, wake the Bone Collector. They either shake their head no or
> point at any dead player. Put the Bone Collector to sleep.
>
> If they pointed at a dead player, **the chosen player regains their ability**—mark their
> character token with the Bone Collector's **HAS ABILITY** reminder. (*They may need to be
> woken tonight to use their ability.*) **The Bone Collector loses their ability**—mark them
> with their **NO ABILITY** reminder. The next dusk, **the chosen player loses their
> ability**—remove the **HAS ABILITY** reminder."

Examples (quoted in full):

> "The Bone Collector gives the dead Flowergirl her ability back. That night, the Flowergirl
> learns that the Demon did indeed vote today. The following night, the Flowergirl once
> again has no ability.
>
> The Bone Collector chooses the dead Witch. The Witch wakes and curses the Clockmaker. The
> Clockmaker nominates the following day, and dies.
>
> At night, the Bone Collector chooses the dead Butcher. The following day, after an
> execution has occurred, the Storyteller prompts the Butcher to nominate again.
>
> During the day, the dead Juggler guesses five players' characters. That night, the Bone
> Collector gives the Juggler their ability back. The Juggler learns a '3'."

Consequences that matter for the app:

- **Other nights only**, once per game, optional ("shake their head no").
- **The chosen player stays dead.** They get their ability back, not their life, not their
  vote.
- **The returned ability is live for the rest of tonight AND the following day**, until
  dusk. That is why the Butcher example works (a *day* ability), why the Witch's curse
  persists into the next day, and why the Juggler's *previous day's* guess is scored
  tonight.
- **First-night-only abilities come back too.** Clockmaker, Seamstress, Washerwoman,
  Librarian, Investigator, Chef, Steward, Noble, Shugenja, Balloonist, Grandmother etc. must
  be re-run *on this later night*, using their FIRST-night behaviour. This is the same
  "re-run the 1st night for that player" mechanic the user asked for with the Professor.
- **Once-per-game abilities come back even if already spent** (Slayer, Virgin, Professor,
  Fisherman, Artist, Philosopher, Gambler-style spends). And if not yet spent, they may be
  used twice before dusk.
- **The Bone Collector loses their own ability when they use it** (NO ABILITY token) —
  in practice a once-per-game spent marker.
- **If the Bone Collector dies, the HAS ABILITY effect ends immediately**, even mid-day.
- The chosen player is **not told** they were chosen by the Bone Collector.
- Bone Collector Tips note the possibility of returning a *dead Traveller's* ability.
- No jinxes on the Bone Collector page.

## What the app does today

Data:
- `characters.json:1140-1154` — ability text missing the `*`; `otherNightReminder` present;
  `reminders: ["No ability", "Has ability"]` (official labels are **NO ABILITY** / **HAS
  ABILITY** — casing only).
- `night_and_jinxes.json:378` — `bonecollector` in `otherNight` only, after `plaguedoctor`
  and before `harlot`. Correct that it is other-nights-only. (I could not verify the exact
  relative position against an official night-order sheet — the wiki has no night-order
  page and `script.bloodontheclocktower.com/data/roles.json` is not reachable — so I am
  not claiming drift here.)
- `night_guide.json:805-810` — one `other` entry with good prose ("Only if the Bone
  Collector has not yet used their ability… place the 'Has ability' reminder… They may need
  to be woken later tonight… Then place the 'No ability' reminder on the Bone Collector"),
  and **no show cards**.

Code: **no Bone Collector-specific code anywhere.** `grep -rn bonecollector engine/src
app/src` returns only the data files.

Storyteller's actual experience:
1. A "Bone Collector" row appears on every non-first night (`NightOrder.kt:142-178`),
   whether or not the ability is already spent.
2. Expanding it shows the guide prose. `QuickResolutions` (`NightScreen.kt:462-522`) has no
   branch for it, so there is no dead-player picker.
3. The tray (`NightScreen.kt:193-300`) offers the two labels for tap-then-seat placement,
   plus a **"Mark spent"** chip that fires because the ability text starts with "Once per
   game" (`NightScreen.kt:204`, `:263-281`) and places `("bonecollector","No ability")` on
   the holder. That part works.
4. The ST must then, unaided: find the chosen dead player's character, work out whether it
   has a first-night or other-night step, run it from memory (the night sheet does not add
   a row), remember for the whole next day that the ability is live, and delete the "Has
   ability" token by hand at the following dusk.

Works: other-nights-only placement, both reminder labels, the generic "Mark spent" chip.

Shared traveller-lifecycle defects **T1–T7** apply — see `barista.md`.

## Defects and gaps

1. **P0 · The restored ability is never actually run.** Giving a dead player their ability
   back requires that player's night step to appear *tonight*. `NightOrder.build`
   (`NightOrder.kt:40-181`) walks a fixed global order and emits a step per in-play
   character id — for **other** nights it uses `otherNightReminder`
   (`NightOrder.kt:146-148`), so a character whose ability is first-night-only (Clockmaker,
   Washerwoman, Librarian, Investigator, Chef, Steward, Noble, Shugenja, Seamstress,
   Balloonist first-night, Grandmother) either produces no row at all (absent from the
   `otherNight` list) or a row with the wrong text. There is no way to inject "run
   Clockmaker's FIRST night step for the dead player tonight".
   *Repro:* kill the Clockmaker, then on night 3 have the Bone Collector choose them. The
   night sheet shows no Clockmaker row; the ST must run it entirely from memory.

2. **P0 · "Has ability" never expires.** `EXPIRES_AT_DUSK` (`GameActions.kt:231-242`) does
   not include `("bonecollector","Has ability")`, so the token — and therefore the ST's only
   record of the effect — persists for the rest of the game. Official: "The next dusk, the
   chosen player loses their ability."

3. **P0 · The step is still offered after the ability is spent.** `NightOrder.kt:143-145`
   includes the row whenever any player holds `bonecollector`; nothing checks for the
   `("bonecollector","No ability")` marker. The `night_guide` prose says "Only if the Bone
   Collector has not yet used their ability" but the app does not enforce or even flag it.
   Same failure mode as the user's Pukka complaint: the app offers an action the rules
   forbid.

4. **P0 · A dead Bone Collector still gets the step**, and a Bone Collector who dies after
   using the ability does not revoke it. `NightOrder.kt:143-145` has no alive check, and
   `GameActions.kill` (`GameActions.kt:136-156`) has no on-death cleanup. Official: "If the
   Bone Collector dies, that player no longer has the ability they regained."

5. **P1 · No dead-player picker / one-tap resolution.** Every other fiddly interaction has
   one (`QuickResolutions`, `NightScreen.kt:462-522` — snakecharmer/fanggu/professor). The
   Bone Collector needs: pick a dead player → place HAS ABILITY on them → place NO ABILITY
   on the Bone Collector → schedule the restored step, in one confirmed, undoable action.

6. **P1 · The restored ability is not live during the following day, as far as the app is
   concerned.** Nothing carries the effect into day tools:
   - a restored dead **Butcher** cannot nominate (`DayScreen.kt:135-138` gates the Nominator
     chip on `p.alive`);
   - a restored dead **Slayer/Artist/Fisherman/Gossip/Juggler** has no day affordance;
   - a restored dead **Gangster** cannot use their day kill;
   - a restored dead **Virgin** produces no nomination warning (`StatusEffects.nominationWarnings`,
     `StatusEffects.kt:152-157`, only checks `characterId == "virgin"` and the "No ability"
     token — it would in fact *mis*fire here, since a Virgin marked "No ability" from an
     earlier use should now work again).

7. **P1 · The Juggler case is unrepresentable.** The wiki's own example requires the ST to
   have recorded a *dead* Juggler's five day-time guesses and then score them tonight. There
   is no place in the app to record a day-time public claim/guess at all (the only free text
   is `GameState.storytellerNotes`, `GameState.kt:112`, and per-player `note`,
   `GameState.kt:31`). This is the same gap the user raised about the Gossip.

8. **P1 · Restored once-per-game abilities are blocked by their own spent marker.** The
   generic "Mark spent" flow writes `("<char>","No ability")` (`NightScreen.kt:263-281`), and
   `QuickResolutions`' Professor branch (`NightScreen.kt:499-520`) and
   `StatusEffects.nominationWarnings` (`StatusEffects.kt:152-157`, `:75-77` for the Fool)
   all treat that token as permanent. Under the Bone Collector the spend must be ignored
   until dusk.

9. **P2 · `isImpaired` treats a restored player as normal, but a dead player is not
   otherwise ability-less in the model at all.** `InfoCalc.impairments`
   (`InfoCalc.kt:133-153`) only adds "X is dead — they normally don't act". There is no
   positive "X HAS ABILITY tonight (Bone Collector) — run their step for real" signal, so
   the ST reading the step sees a discouraging caveat instead of a confirmation.

10. **P2 · No show cards.** `night_guide.json:805-810` has `"shows": []`. The chosen player
    "does not learn they were selected", so the correct card set is the *restored
    character's own* cards — the step should splice in `NightGuide.forStep(restoredId,
    isFirstNight = true|false)`.

11. **P3 · Ability text drift**: `characters.json:1145` is missing the `*` in
    "at night\*". Reminder labels should be "No Ability" / "Has Ability" to match the
    printed tokens.

## Proposed behaviour (spec)

### Night step (structured)

- **when**: `other` nights only.
  Wake condition: the Bone Collector holder is **alive** AND does not hold
  `("bonecollector","No ability")` AND at least one player is dead. Otherwise omit the step.
- **targets**: 0 or 1 (the ability is optional — "shake their head no").
  Constraint: the target must be **dead**. Sort: dead Townsfolk first (the wiki's own
  advice), then dead Outsiders, then dead Travellers, then dead Minions/Demon; annotate each
  with the character name and whether its ability is first-night, other-night, day-time or
  passive, so the ST can see what they are buying.
- **immediate effects** (one confirmed action):
  1. `placeExclusiveReminder(target, PlacedReminder("bonecollector", "Has Ability"))`
  2. `placeExclusiveReminder(boneCollector, PlacedReminder("bonecollector", "No Ability"))`
  3. Insert a **restored step** into tonight's night sheet for the target (see below).
  No kill, no resurrection, no character change, no alignment change.
- **deferred effects**:
  - *Tonight*: the restored step runs at the restored character's normal night position if
    that position is still ahead of the current step; otherwise immediately after the Bone
    Collector's own step, with a note "out of order — the Bone Collector woke after their
    normal slot".
  - *Tomorrow (day)*: a day-start briefing line —
    "**<Name> has their <Character> ability back until dusk** (Bone Collector). They are
    still dead: no nomination unless their ability grants it, one ghost vote as usual."
    Day tools must honour it (Butcher extra nomination, Gangster kill, Slayer shot, Artist
    question, Fisherman advice, Gossip statement, Klutz guess, Virgin trigger, Golem
    nomination).
  - *On Bone Collector death*: remove `("bonecollector","Has Ability")` from all seats
    immediately and tell the ST "<Name> loses the restored <Character> ability now."
- **expiry**: `("bonecollector","Has Ability")` → add to `EXPIRES_AT_DUSK`
  (`GameActions.kt:231-242`). `("bonecollector","No Ability")` → **never** expires (it is
  the once-per-game spend). Also removed on Bone Collector death (condition, not table).
- **information**: the Bone Collector learns nothing and is not shown anything. The chosen
  player is not told who chose them. The step's show cards should be the *restored
  character's* cards, spliced from `night_guide.json`.
- **visibility**: nothing to the Demon/Minions/Lunatic. The chosen player only discovers it
  because they are woken.
- **day-time inputs the app must record**: any public statement/guess the restored character
  makes during the following day (Juggler's guesses, Gossip's statement, Artist's question,
  Klutz's guess, Slayer's public shot) — and, for the Juggler specifically, guesses made
  *while dead on a previous day*, which must be retrievable when the ability is restored.
- **interactions/edge cases to handle explicitly**:
  - Restored **once-per-game** character: ignore any `("<char>","No ability")` marker while
    `("bonecollector","Has Ability")` is on that seat, in `nominationWarnings`
    (Virgin, `StatusEffects.kt:152-157`), the Fool note (`StatusEffects.kt:75-77`), the
    Professor branch (`NightScreen.kt:499-520`) and the "Mark spent" chip gate
    (`NightScreen.kt:263-281`).
  - Restored **Butcher**: the extra nomination is allowed even though the Butcher is dead
    (wiki example). See `butcher.md`.
  - Restored **Traveller** ability is legal.
  - Restored **Witch/Cerenovus/Pit-Hag/Poisoner**: their tokens are placed as normal and
    expire on their own schedule (the Witch's Cursed already expires at dusk,
    `GameActions.kt:238`).
  - The restored player is still dead: no vote beyond their ghost vote, no nomination
    unless the restored ability itself grants one.
  - Drunk/poison: a restored player can still be poisoned; `isImpaired` is unchanged.
    A restored player also holding `("barista","Sober & Healthy")` gets true info.
  - Barista ACTS TWICE on the Bone Collector: they may choose two dead players (or use the
    not-yet-spent ability twice before dusk).

### Restored-step mechanism (engine change, generic — shared with the Professor)

Add a state field, e.g.

```
val restoredAbilities: List<RestoredAbility> = emptyList()
// RestoredAbility(playerId, characterId, sourceId = "bonecollector",
//                 runFirstNightVariant: Boolean, cycleGranted: Int)
```

`NightOrder.build` then, for each `RestoredAbility` whose `cycleGranted == state.cycle`,
emits an extra `NightStep` with:
- `id = "<characterId>@restored:<playerId>"` (keeps `nightStepsDone` and LazyColumn keys
  unique, `NightScreen.kt:137`),
- title `"<Character> — <Player> (ability restored)"`,
- detail = the **first-night** reminder when `runFirstNightVariant` is true (i.e. the
  character has a first-night ability, whether or not it also has an other-night one),
  otherwise the other-night reminder,
- inserted at the character's canonical position in **this night's** order list if it
  appears there, otherwise at the character's position in the *first-night* list mapped
  onto tonight's sheet, otherwise directly after the Bone Collector step.

`StepDetailPanel` (`NightScreen.kt:770-934`) must strip the `@restored:<id>` suffix before
calling `NightGuide.forStep`, `InfoCalc.supports`/`compute` and `QuickResolutions`, and pass
`isFirstNight = runFirstNightVariant` to `NightGuide.forStep` rather than
`state.cycle == 1` (`NightScreen.kt:787`). `InfoCalc.compute` must be given the restored
player's id as `holderId` so "you start knowing" info is computed for the right seat.

The same mechanism satisfies the user's Professor request ("When Professor brings someone
back it should remind in the morning and rerun the 1st night for that").

### UI text for the step

- Title row: `Bone Collector — once per game`
- Detail: `They shake their head no, or point at a dead player. If they point: that player
  gets their ability back until dusk (still dead). Mark HAS ABILITY on them and NO ABILITY
  on the Bone Collector. Their step is added to tonight's sheet below.`
- After resolution: `<Name> has the <Character> ability until dusk. Run their step tonight
  and let them use it tomorrow.`
- If already spent: `Spent — the Bone Collector used their ability on night N. No step
  tonight.`

### Data changes

- `characters.json:1145`: ability → `"Once per game, at night*, choose a dead player: they
  regain their ability until dusk."`
- `characters.json:1147-1150`: reminders → `["No Ability", "Has Ability"]`
  (keep the old casing as an alias for existing saves).
- `characters.json:1146` (`otherNightReminder`): append "The Bone Collector loses their
  ability — mark them NO ABILITY. The next dusk, remove HAS ABILITY."
- `night_guide.json:805-810`: add "The chosen player is NOT told the Bone Collector chose
  them.", "If the ability is first-night-only (Clockmaker, Washerwoman, Chef…), run its
  FIRST-night version tonight.", "If they had already spent a once-per-game ability, they
  may use it again.", "If the Bone Collector dies, the restored ability ends at once."
- Night order: no change.

## Tests to add

1. `Given` a Bone Collector holding `("bonecollector","No Ability")`
   `When` `NightOrder.otherNight` is built
   `Then` no `bonecollector` step is present. *(Fails today.)*

2. `Given` a dead Bone Collector `When` `NightOrder.otherNight` is built
   `Then` no `bonecollector` step is present. *(Fails today.)*

3. `Given` no dead players `When` `NightOrder.otherNight` is built
   `Then` no `bonecollector` step is present.

4. `Given` a dead Clockmaker and a Bone Collector resolution choosing them on night 3
   `When` `NightOrder.otherNight` is built
   `Then` the steps contain a restored Clockmaker step whose detail equals the Clockmaker's
   **firstNightReminder**, and `InfoCalc.compute(..., "clockmaker", clockmakerSeatId)`
   returns the Clockmaker's number. *(Fails today: no step at all.)*

5. `Given` `("bonecollector","Has Ability")` on a seat at night 3
   `When` `advancePhase` NIGHT→DAY `Then` the token is still present;
   `When` `advancePhase` DAY→NIGHT `Then` it is gone. *(Fails today: never removed.)*

6. `Given` `("bonecollector","Has Ability")` on seat A and an alive Bone Collector
   `When` the Bone Collector is killed
   `Then` seat A no longer holds the token. *(Fails today.)*

7. `Given` a dead Virgin holding `("virgin","No ability")` and `("bonecollector","Has
   Ability")` `When` `StatusEffects.nominationWarnings(nominee = virgin)`
   `Then` the Virgin trigger warning IS produced. *(Fails today: suppressed by the
   "No ability" token.)*

8. `Given` a dead Butcher holding `("bonecollector","Has Ability")` and an execution
   recorded today `When` the day engine is asked who may still nominate
   `Then` the Butcher is included. *(Fails today: dead players cannot nominate.)*

9. `Given` the Bone Collector resolution is applied
   `Then` the Bone Collector holds exactly one `("bonecollector","No Ability")` and the
   target exactly one `("bonecollector","Has Ability")`, and a second resolution on the same
   night is not offered.
