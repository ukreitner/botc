# Barista (barista) — Sects & Violets Traveller

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Barista>
Traveller rules: <https://wiki.bloodontheclocktower.com/Travellers>,
<https://wiki.bloodontheclocktower.com/Character_Types> (§Traveller),
<https://wiki.bloodontheclocktower.com/Glossary> (Exile, Traveller, Execution, Vote)

Current ability text (wiki):

> "Each night, until dusk, 1) a player becomes sober, healthy & gets true info, or
> 2) their ability works twice. They learn which."

`characters.json:1130` has the same text with "and" for "&" — no drift.

Summary clarifications (quoted):

> - "The Storyteller chooses which player the Barista affects each night, and which one of
>   the two Barista abilities is in effect. The Barista does not know who or what the
>   Storyteller chooses, but the affected player does."
> - "If the affected player is acting twice, then they do so at the normal time. If they
>   would normally wake at night, they act, go to sleep, then wake to act again. If they
>   have already used a 'once per game' ability, they may use that ability again. If they
>   have a 'once per game' ability but have not used it yet, they may use it twice before
>   dusk."
> - "If the Barista makes a player sober and healthy, their drunkenness and poisoning, if
>   any, is removed, and they may not become drunk or poisoned until dusk. This player must
>   get true information, even if a Vortox is in play."

How to Run (quoted in full):

> "Each night, remove previous reminders then put the Barista's **SOBER AND HEALTHY**
> reminder or their **ACTS TWICE** reminder by any character token. Wake that character's
> player and show them the **THIS CHARACTER SELECTED YOU** info token, the Barista token,
> then one finger (*to show they are sober and healthy*) or two fingers (*to show they act
> twice*). Put that player to sleep.
>
> A player marked **SOBER AND HEALTHY** is sober and healthy (even if they're also marked
> **DRUNK** or **POISONED**) and always gets true information (*even if an ability would
> make them drunk or poisoned*).
>
> A player marked **ACTS TWICE** acts twice at the appropriate time. (*If the ability is
> optional, they may use it twice. If it is mandatory, they must use it twice.*) Use the
> Barista's **?** reminders if needed, to substitute for the character's own reminders."
>
> "Some characters are better off knowing they are sober and healthy, as they gain no
> benefit from acting twice, such as the Flowergirl, Town Crier, or Oracle."
>
> "The Barista ensures players get true information even if an ability causes false
> information, such as a Fortune Teller, Spy, or Recluse."

Examples (quoted in full):

> "The Barista makes the Sage sober and healthy.
>
> The Klutz acts twice. They die and must choose two players. If either is evil, evil wins.
> The next night, the Barista makes the Witch act twice. Two players are cursed."

Consequences that matter for the app:

- **The Barista wakes on EVERY night, including night 1** ("Each night"). Night order:
  first night immediately after the Apprentice; other nights first after Dusk. This is
  correct in `night_and_jinxes.json:299` and `:374`.
- **The Barista player is not woken and never learns anything.** The ST wakes the
  *target*. The night step is purely a storyteller choice + a show-card to a third party.
- **The effect lasts until dusk**, i.e. through the following day. It is placed at night N
  and swept at dusk of day N. It is *not* a dawn-expiring token.
- **SOBER AND HEALTHY beats every drunk/poison source**, including reminder-based poison,
  the Drunk itself? — the wiki says "even if they're also marked DRUNK or POISONED", and
  "even if an ability would make them drunk or poisoned", i.e. it also blocks *new*
  drunkenness/poisoning until dusk. (The wiki does not explicitly address a player whose
  character *is* the Drunk; the ability text "a player becomes sober" reads as covering it,
  and the Barista's Tips explicitly cover a No Dashii neighbour. I have flagged the Drunk
  case as an ST toggle rather than guessing.)
- **SOBER AND HEALTHY overrides the Vortox**: this player gets true information even in a
  Vortox game. This is the only routine exception to "all Townsfolk info is false".
- **SOBER AND HEALTHY also defeats misregistration in the info the target receives** — the
  wiki explicitly names Spy and Recluse: the target "must get true information".
- **ACTS TWICE means the target's whole ability runs twice** — two wakes, two picks, two
  results. For a Demon that means two kill choices; for a Poisoner, two poisons; for a
  Klutz (a day/on-death ability), two guesses. Optional = may; mandatory = must.
- **The "?" tokens exist purely because reminder tokens are one-of-a-kind at a physical
  table.** In software the equivalent is: allow a second copy of the target character's own
  reminders while ACTS TWICE is on them.
- **Both Barista tokens are one-of-a-kind and move each night** ("remove previous reminders
  then put...").
- No jinxes on the Barista page.

## What the app does today

Data:
- `engine/src/main/resources/botc/data/characters.json:1125-1139` — ability text correct;
  `firstNightReminder`/`otherNightReminder` both set; `reminders: ["Sober & Healthy",
  "Ability twice"]`.
- `engine/src/main/resources/botc/data/night_and_jinxes.json:299` (first night, after
  `apprentice`) and `:374` (other nights, first after `DUSK`) — order is correct.
- `engine/src/main/resources/botc/data/night_guide.json:757-803` — a good `first` and
  `other` entry with three show cards: the "This character selected you" token card, and
  two message cards ("Until dusk you are sober, healthy and get true info" / "Until dusk
  your ability works twice").

Code: **there is no Barista-specific code anywhere.** `grep -rn barista engine/src app/src`
returns only the three data files above.

Storyteller's actual experience:
1. The night sheet shows a "Barista" row with the prompt text (`NightOrder.kt:142-178`).
2. Expanding it shows the guide prose and three "»" chips that flash full-screen cards
   (`NightScreen.kt:786-826`).
3. The bottom tray (`NightScreen.kt:193-300`) offers the two reminder labels; the ST taps
   one, then taps a seat. `addReminder` is used, **not** `placeExclusiveReminder`
   (`GameViewModel.kt` → `GameActions.addReminder`, GameActions.kt:186-187).
4. Nothing else happens, ever. The token is never removed; the token has no effect on any
   computation.

Works: night-order position (both nights), the guide prose, the two show cards, the
reminder labels being present in the tray.

## Defects and gaps

1. **P0 · "Sober & Healthy" does not make anyone sober or healthy.**
   `StatusEffects.isImpaired` (`engine/src/main/kotlin/com/clocktower/engine/StatusEffects.kt:36-46`)
   returns true for any reminder whose label contains "poison"/"drunk", for the Drunk, and
   for `derivedPoison` (No Dashii). It has no knowledge of the Barista token. So a
   Barista-protected player who is also Poisoner-poisoned is still reported as impaired.
   *Repro:* place Poisoner "Poisoned" and Barista "Sober & Healthy" on the same seat →
   open that character's night step → InfoCalc still prints "X is POISONED — give false
   info." Rules require true info.

2. **P0 · "Sober & Healthy" does not override the Vortox.**
   `InfoCalc.commonCaveats` (`engine/src/main/kotlin/com/clocktower/engine/InfoCalc.kt:158-165`)
   appends "VORTOX in play — Townsfolk info must be FALSE" for every Townsfolk holder with
   no Barista exception. `NightScreen.kt:904-907` then flips the UI into "False info to show
   instead:" mode and offers only wrong numbers. The ST is actively steered into breaking
   the rule.
   *Repro:* Vortox + Barista in play, mark the Empath "Sober & Healthy", open the Empath
   step → app demands false info.

3. **P0 · "Sober & Healthy" does not suppress misregistration.**
   `InfoCalc.misregistrations` (`InfoCalc.kt:121-130`) always warns "may register as evil"
   for the Spy/Recluse. Under the Barista the target "must get true information", so the
   ST must be told to ignore misregistration for that one target tonight.

4. **P0 · Neither Barista token ever expires.** `EXPIRES_AT_DUSK`
   (`GameActions.kt:231-242`) does not contain `barista to "Sober & Healthy"` or
   `barista to "Ability twice"`. The ST must delete them by hand or they accumulate on
   every seat the Barista ever touched. This is exactly the "Devil's Advocate wasn't
   automatically removed" class of bug the user reported.
   *Repro:* place "Sober & Healthy" on night 1, advance Dawn → Dusk → Dawn. The token is
   still there, and a second one is now on another seat.

5. **P0 · Tokens are additive, not exclusive.** The tray uses `addReminder`
   (`GameActions.kt:186-187`); nothing calls `placeExclusiveReminder` for the Barista.
   Official: "remove previous reminders then put...". Two seats can hold "Sober & Healthy"
   simultaneously, and one seat can hold both Barista tokens at once (which is not a legal
   state — the Barista does one or the other).

6. **P1 · "Ability twice" does nothing to the night sheet.** There is no second wake, no
   second target picker, no second info result. `NightOrder.build`
   (`NightOrder.kt:40-181`) emits exactly one `NightStep` per character id, and `NightStep`
   ids are used as `LazyColumn` keys (`NightScreen.kt:137`) and as the `nightStepsDone` key
   (`GameActions.kt:265-272`), so a duplicate row is not even representable today. The ST
   must remember, unprompted, to run the step twice.

7. **P1 · Reminder-token collision when a target acts twice.** `placeExclusiveReminder`
   (`GameActions.kt:194-201`) *moves* one-of-a-kind tokens, so a Fortune Teller acting twice
   cannot hold two "Red herring"-style marks, a Monk acting twice cannot mark two "Safe"
   players, a Witch acting twice cannot hold two "Cursed" marks (the wiki's own Klutz/Witch
   example). The official workaround is the Barista's two **?** tokens; the app's
   `characters.json:1132-1135` does not list them, so they are not in the tray at all
   (the generic "?" token in `ReminderPicker` (`SeatSheet.kt:501`) is the only fallback and
   carries `sourceId = ""`, so it can never expire automatically either).

8. **P1 · The ST is never told which of the two effects they chose.** The token is the only
   record and it is silent — the grimoire seat shows a token, but the night step gives no
   "tonight: Ana is SOBER & HEALTHY" recap, no day-start briefing, and nothing at dusk says
   "the Barista effect on Ana ends now".

9. **P1 · No target guidance.** The wiki's How-to-Run explicitly names characters that gain
   nothing from acting twice (Flowergirl, Town Crier, Oracle — passive/no-choice info
   roles) and calls out the value of Sober & Healthy next to a No Dashii or under a Vortox.
   The step gives no hint, so the ST must know this from memory.

10. **P2 · A dead Barista still gets a step.** `NightOrder.kt:143-145` includes the step
    whenever any player holds the id, alive or dead. The row does print "All holders are
    dead — usually skip." (`NightScreen.kt:750-756`) but the step still counts in the
    "N of M steps done" header and in the dawn "night checklist incomplete" guard
    (`GameShell.kt:617-651`). A dead Traveller has no ability at all.

11. **P3 · Token label drift.** Official label is **ACTS TWICE**; `characters.json:1134`
    says "Ability twice". Current official data (newer role datasets) lists the Barista's
    reminders as `["Sober & Healthy", "Acts Twice", "?", "?"]`.

**Shared traveller-lifecycle defects (T1–T7) — these apply to all seven travellers in this
batch and are stated once here.** Other files in this batch reference them by number.

- **T1 · P0 · A traveller's alignment is never chosen, recorded as deliberate, or shown to
  the player.** `Player.isEvil` (`GameState.kt:49-52`) computes `Team.TRAVELLER.isEvil ==
  false`, so every traveller silently counts as GOOD until the ST remembers to press "Flip
  alignment" (`SeatSheet.kt:315`). Nothing prompts for it at entry; nothing shows the
  traveller their alignment card (`ShowCard.AlignmentCard` exists at
  `ShowCards.kt:69` but is only reachable from a night-guide `"good"`/`"evil"` show entry);
  nothing shows an evil traveller who the Demon is, which the rules require
  ("If they are evil, they learn who the Demon is; they do not learn any additional evil
  characters or receive any bluffs" — Travellers page). Consequence: Chef/Empath/
  Investigator/Fortune Teller etc. compute *wrong* numbers for a traveller the ST meant to
  be evil but never flipped.
- **T2 · P1 · Adding a traveller is a four-screen scavenger hunt.** Menu →
  "Add seat (traveller joins)" (`GameShell.kt:254-257`, dialog at `:663-682`) collects only
  a name and appends the seat at the END of the circle — `GameActions.addSeat` accepts
  `afterId` (`GameActions.kt:19-26`) but the dialog never passes it, so the ST must then
  open "Reorder seats" and walk the seat around. Then: tap the seat → "Change character" →
  scroll past the entire script to the Travellers group (`SeatSheet.kt:439-451`) →
  "Flip alignment" if evil. There is no single "a traveller joins" flow.
- **T3 · P1 · No public-announcement affordance.** A traveller's identity and ability are
  known to all players on arrival (Character Types §Traveller). Nothing in the app produces
  that announcement card, and nothing reminds the ST to give the arriving traveller the
  Traveller sheet (Minion/Outsider counts).
- **T4 · P1 · Only alive players may call for an exile.** `DayScreen.kt:135-138` gates the
  Nominator chip on `p.alive`. Glossary: "Any players may support an exile, even dead
  players without a vote token"; a call for exile is not a nomination and is not restricted
  to the living.
- **T5 · P2 · The exile flow is hidden inside the nomination card.** The ST must pick a
  "Nominator" and then a nominee before `isExile` is derived (`DayScreen.kt:161-164`) and
  the card relabels itself "Exile vote". There is no "Call for exile" entry point, and the
  wording ("Nominator", "Record") stays nomination-flavoured.
- **T6 · P1 · Departure is destructive.** The only way to remove a departed traveller is
  "Remove seat" (`SeatSheet.kt:317-321`) → `GameActions.removeSeat` (`GameActions.kt:29-30`)
  which deletes the `Player` outright, orphaning every `Nomination` and `DeathRecord` that
  references the id (the game log then prints "?" — `GameExtras.kt:65-66`).
- **T7 · P2 · Night steps for travellers are not gated on alive/spent** — see defect 10
  above; the same applies to the Bone Collector and Harlot.

## Proposed behaviour (spec)

### Night step (structured)

- **when**: `both` (first night and every other night).
  Wake condition: the Barista holder is **alive**. No once-per-game gate. The Barista is
  never woken; this is a storyteller-only step.
  If no living Barista, omit the step entirely.
- **targets**: exactly 1, any player (alive or dead — a dead player with a Bone
  Collector-restored ability is a legal ACTS TWICE target; the wiki does not restrict).
  Plus a required **mode** choice: `SOBER_HEALTHY | ACTS_TWICE`.
  Picker default/sort: living players first; annotate each candidate with
  a) whether they currently hold any drunk/poison mark or sit next to a No Dashii, or a
  Vortox is in play → suggest SOBER & HEALTHY;
  b) whether they have a choice-making night ability → suggest ACTS TWICE;
  c) grey-hint "gains nothing from acting twice" for passive info roles
  (flowergirl, towncrier, oracle, chef, empath, clockmaker, steward, noble, shugenja,
  knight, bountyhunter, mathematician, balloonist, undertaker, cultleader, king, sage).
- **immediate effects**:
  - `placeExclusiveReminder(target, PlacedReminder("barista", "Sober & Healthy"))` **or**
    `placeExclusiveReminder(target, PlacedReminder("barista", "Acts Twice"))`, and remove
    the *other* Barista token from every seat in the same action (both tokens are
    one-of-a-kind, and only one may exist at a time).
  - No kill, no character change, no alignment change.
- **deferred effects**:
  - ACTS TWICE: tonight's night sheet must contain the target's step **twice** (see below),
    and any day-time ability of that target may be used twice today (Butcher would get two
    extra nominations, Gangster two kills, Klutz two guesses, Gunslinger two shots).
    Surface this in the day-start briefing: "Ana ACTS TWICE until dusk — her day ability
    may be used twice."
  - SOBER & HEALTHY: day-start briefing "Ana is sober & healthy until dusk — any info she
    gets today is true, and she cannot become drunk or poisoned."
- **expiry**: both tokens are `EXPIRES_AT_DUSK`. Add
  `"barista" to "Sober & Healthy"` and `"barista" to "Acts Twice"` to
  `GameActions.EXPIRES_AT_DUSK` (`GameActions.kt:231-242`). Also drop both tokens
  immediately if the Barista dies (the ability stops) — treat as a death trigger, not a
  timed expiry. Migration: keep `"barista" to "Ability twice"` in the table too so existing
  saves get swept.
- **information**: the target is shown, in order, (1) "THIS CHARACTER SELECTED YOU",
  (2) the Barista token, (3) one finger / two fingers. Keep the three existing show cards
  in `night_guide.json:757-803` (rename "Effect 2" to "Until dusk your ability works
  twice — you ACT TWICE"). No information is computed for the Barista themselves.
- **visibility**: nobody but the target learns anything. The Barista does **not** learn who
  or what was chosen — the step must say so explicitly so the ST doesn't wake them.
- **day-time inputs**: none required, but the day-start briefing must carry the standing
  effect (above).
- **interactions to handle explicitly**:
  - `StatusEffects.isImpaired`: return `false` unconditionally when the player holds
    `("barista","Sober & Healthy")`, *before* the reminder scan and before `derivedPoison`.
  - `InfoCalc.impairments` (`InfoCalc.kt:133-153`): when the holder is Barista-sobered,
    return a single positive note instead of the impairment list:
    "BARISTA: Ana is sober & healthy until dusk — give TRUE info, ignore every drunk/poison
    mark and any misregistration."
  - `InfoCalc.commonCaveats` (`InfoCalc.kt:158-165`): skip the Vortox caveat for a
    Barista-sobered holder and instead emit
    "BARISTA overrides the VORTOX — this player gets TRUE info."
  - `InfoCalc.misregistrations` (`InfoCalc.kt:121-130`): suppress Spy/Recluse warnings when
    the *receiving* holder is Barista-sobered.
  - `NightScreen.kt:904-916`: the "False info to show instead:" block must not render when
    the holder is Barista-sobered.
  - New drunk/poison marks placed on a Barista-sobered player must be refused (or accepted
    with a loud "no effect until dusk — the Barista protects them" note). Sources to cover:
    Poisoner, Courtier, Sailor, Innkeeper, Widow, No Dashii adjacency, Goon, Pit-Hag,
    Sweetheart, Vigormortis, Minstrel, Snake Charmer.
  - The Drunk: if the target's `characterId == "drunk"`, show an explicit ST decision line
    ("the wiki does not settle whether the Barista sobers the Drunk itself; the ability text
    says 'a player becomes sober' — decide and note it") rather than silently picking.
  - Exorcist: an Exorcised Demon that is also ACTS TWICE still does not act.
  - Bone Collector: a dead player given ACTS TWICE uses the restored ability twice.

### The ACTS TWICE double step (engine change, generic)

`NightStep` must gain an `occurrence: Int = 1` and a stable composite key
`"$id#$occurrence"`. `NightOrder.build` emits a second `NightStep` for any character id
whose holder carries `("barista","Acts Twice")`, immediately after the first, titled
`"<Name> — 2nd time (Barista)"` with detail "They act a second time. Optional abilities
may be used again; mandatory ones must be." `nightStepsDone` keys on the composite key, and
`NightScreen.kt:137` uses it as the LazyColumn key. Every resolver keyed on `step.id`
(`QuickResolutions`, `NightScreen.kt:462-522`; `InfoCalc.compute`,
`NightScreen.kt:836-863`) must key its `rememberSaveable` state on the composite key so the
two occurrences hold independent target selections.

While ACTS TWICE is on a player, `placeExclusiveReminder` for **that player's own
character** must place instead of move (allow a second copy), so a doubled Monk/Witch/
Fortune Teller/Poisoner can mark two seats. Label the second copy `"<label> (2nd)"` so it
is visually distinguishable, and expire it exactly like the first.

### UI text for the step

- Title row: `Barista — you choose, the Barista learns nothing`
- Detail: `Pick a player and pick one effect. Wake them, show THIS CHARACTER SELECTED YOU,
  the Barista token, then 1 finger (sober & healthy) or 2 fingers (acts twice). Do not wake
  the Barista.`
- After a choice: `Until dusk: Ana is SOBER & HEALTHY — true info, immune to drunk/poison.`
  or `Until dusk: Ana ACTS TWICE — her step now appears twice tonight.`

### Data changes

- `characters.json:1132-1135`: `"reminders": ["Sober & Healthy", "Acts Twice", "?", "?"]`
  (rename "Ability twice" → "Acts Twice"; add the two `?` substitute tokens).
- `night_guide.json:757-803`: add to both `first` and `other` instructions —
  "Do not wake the Barista; they learn nothing." and "SOBER AND HEALTHY beats poison,
  drunkenness, the Vortox, and Spy/Recluse misregistration for this player until dusk."
- Night order: no change (`night_and_jinxes.json:299`, `:374` are correct).

## Tests to add

1. `Given` a player holds `("poisoner","Poisoned")` and `("barista","Sober & Healthy")`
   `When` `StatusEffects.isImpaired` is called
   `Then` it returns `false`. *(Fails today: returns true.)*

2. `Given` an alive Vortox and an Empath holding `("barista","Sober & Healthy")`
   `When` `InfoCalc.compute(..., "empath", empathId)`
   `Then` the caveats contain no "VORTOX ... must be FALSE" entry and contain a
   "BARISTA ... TRUE info" entry. *(Fails today.)*

3. `Given` a Recluse in play and a Washerwoman holding `("barista","Sober & Healthy")`
   `When` info is computed `Then` no "may register as evil" caveat is produced.

4. `Given` `("barista","Sober & Healthy")` on seat A at night 2
   `When` `advancePhase` NIGHT→DAY then DAY→NIGHT
   `Then` seat A no longer holds the token. *(Fails today: never expires.)*
   And: after only NIGHT→DAY the token is still present (it lasts through the day).

5. `Given` `("barista","Sober & Healthy")` on seat A
   `When` the Barista's next-night choice places `("barista","Acts Twice")` on seat B
   `Then` seat A holds neither Barista token and seat B holds exactly one.

6. `Given` a Monk holding `("barista","Acts Twice")`
   `When` `NightOrder.otherNight` is built
   `Then` the returned steps contain two entries whose character id is `monk`, with
   distinct keys, the second titled with "2nd time". *(Fails today: one step.)*

7. `Given` a Monk holding `("barista","Acts Twice")` and "Safe" placed on two different
   seats `When` the grimoire is read `Then` both "Safe" tokens are present.
   *(Fails today: `placeExclusiveReminder` moves the first.)*

8. `Given` the Barista dies at night `When` the death is recorded
   `Then` both Barista tokens are removed from all seats.

9. `Given` a dead Barista `When` `NightOrder.otherNight` is built
   `Then` no `barista` step is emitted.
