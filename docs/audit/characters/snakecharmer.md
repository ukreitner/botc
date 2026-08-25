# Snake Charmer (snakecharmer) — Sects & Violets Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Snake_Charmer> (fetched 2026-08-25).

Current ability text:

> "Each night, choose an alive player: a chosen Demon swaps characters &
> alignments with you & is then poisoned."

**How to run (quoted in full):**

> "Each night, wake the Snake Charmer. They point at any player. If that player is
> not the Demon, nothing happens. Put the Snake Charmer to sleep. If that player
> is the Demon, the old Snake Charmer changes into the new (evil) Demon, and the
> old Demon changes into the new (good) Snake Charmer — swap the Snake Charmer
> token and the Demon's token. The new Snake Charmer is poisoned - mark them with
> the POISONED reminder. Wake the new Demon and show them the YOU ARE info token,
> a thumbs-down, the YOU ARE token, then the Demon's token. (This shows they are
> now evil and the Demon.) Put the new Demon to sleep. Wake the new Snake Charmer
> and show them the YOU ARE info token, a thumbs-up, the YOU ARE info token, then
> the Snake Charmer token. (This shows they are now good and the Snake Charmer.)
> Put the new Snake Charmer to sleep. **In the strange situation that the Snake
> Charmer is evil, or the Demon good, swap their alignments as appropriate.**"

**Examples (quoted in full):**

> "The Snake Charmer chooses a player who is the Pit-Hag, so nothing happens. The
> Snake Charmer simply goes to sleep. The next night, **the Snake Charmer chooses
> themself, so nothing happens.** The Snake Charmer chooses a player who is the
> Vigormortis. The Snake Charmer immediately becomes the evil Vigormortis, and the
> Vigormortis becomes the good Snake Charmer and gets poisoned. **The Pit-Hag turns
> themself into the Snake Charmer. Then, the Snake Charmer chooses a player who is
> the Fang Gu. The Snake Charmer becomes the Fang Gu, while the Fang Gu becomes
> the Snake Charmer and gets poisoned. Both remain evil.**"

Additional page facts:

- The poison on the new Snake Charmer is permanent ("a permanent poisoned
  condition").
- Choosing oneself "produces no effect and safely prevents a potential swap".
- The page does **not** address: whether the Minions learn about the swap, whether
  the new Demon learns who the Minions are, dead players as targets, or a
  drunk/poisoned Snake Charmer. Those are general rules and are flagged as
  inference below, not quoted as fact.

**Night order.** First night position 31 (`night_and_jinxes.json:326`, after
Courtier/Wizard, before Godfather/Organ Grinder/Devil's Advocate); other nights
position 19 (`night_and_jinxes.json:392`, after Acrobat, before Monk). Both are
before every Demon's kill step on other nights (Imp 37 … Kazali 52), which is what
makes the same-night hand-over work. **Correct.**

**Jinxes.** None (checked against all 58 entries in `night_and_jinxes.json`).

## What the app does today

This is one of only three characters with a bespoke resolver, and the core of it
is right.

- `characters.json:931-944` — ability, `reminders: ["Poisoned"]`, first/other night
  reminder prose that matches the wiki.
- `night_and_jinxes.json:326`, `:392` — correct night positions. **Works.**
- `night_guide.json:545-592` — accurate prose plus three prepared show cards:
  `New character` (`kind:"token"`, `token:"pick"`), `You are good`
  (`kind:"good"`), `You are evil` (`kind:"evil"`). Rendered as chips in
  `StepDetailPanel` (`NightScreen.kt:802-831`), where `"good"`/`"evil"` go
  straight to `ShowCard.AlignmentCard` (`NightScreen.kt:808-812`,
  `components/ShowCards.kt:69`). **Works.**
- `QuickResolutions` branch `"snakecharmer"`
  (`app/src/main/java/com/clocktower/grimoire/ui/screens/NightScreen.kt:471-482`):
  a `ResolutionPicker` over `state.players.filter { it.alive && it.id != holder.id }`
  sorted demons-first, with confirm label `"Swap with <name> & poison"`, calling
  `GameActions.snakeCharmerSwap`.
- `GameActions.snakeCharmerSwap` (`engine/src/main/kotlin/com/clocktower/engine/GameActions.kt:64-72`):

  ```kotlin
  var next = swapCharacters(state, charmerId, demonPlayerId)
  next = next.updatePlayer(charmerId)     { it.copy(alignmentFlipped = false, shownCharacterId = null) }
  next = next.updatePlayer(demonPlayerId) { it.copy(alignmentFlipped = false, shownCharacterId = null) }
  return placeExclusiveReminder(next, demonPlayerId, PlacedReminder("snakecharmer", "Poisoned"))
  ```

- `swapCharacters` (`GameActions.kt:99-115`) moves `characterId` and
  `shownCharacterId` between the two seats and nothing else.
- The `("snakecharmer","Poisoned")` token is deliberately absent from
  `EXPIRES_AT_DAWN`/`EXPIRES_AT_DUSK` (`GameActions.kt:218-242`), so the poison is
  permanent — covered by `ScriptParserTest.kt:195-206`. **Works.**
- `StatusEffects.isImpaired` picks it up because the label contains "poison"
  (`StatusEffects.kt:36-46`), so every `InfoCalc` answer for the new Snake Charmer
  is correctly caveated. **Works.**
- The night sheet is rebuilt from `state.players`
  (`NightScreen.kt:84-90`, `NightOrder.kt:46-48`), so after a swap the ex-charmer
  appears at the Demon's step later the same night and the ex-Demon appears at the
  Snake Charmer step from the next night on. **Works.**
- Existing coverage: `ScriptParserTest.kt:128-142` asserts the character swap,
  the good/evil outcome in the *normal* case, and the poison token.

**The storyteller's experience.** Expand the Snake Charmer step, read
"Charm hit the Demon? Pick the Demon…", decide for themselves whether the choice
hit and whether the ability even functions, tap the seat, tap
"Swap with X & poison", then tap through three show-card chips choosing the right
character token by hand in a search dialog each time.

## Defects and gaps

1. **P0 · Alignments are RESET, not SWAPPED.** `GameActions.kt:66-67` sets
   `alignmentFlipped = false` on both seats. The rule is "swaps characters **&
   alignments**", and the wiki spells out the non-default case twice: "In the
   strange situation that the Snake Charmer is evil, or the Demon good, swap their
   alignments as appropriate", and the Example "The Pit-Hag turns themself into
   the Snake Charmer… The Snake Charmer becomes the Fang Gu, while the Fang Gu
   becomes the Snake Charmer and gets poisoned. **Both remain evil.**"
   The app turns that new Snake Charmer **good**, which corrupts Empath, Chef,
   Oracle, Seamstress, Investigator, Balloonist and Cult Leader info and the win
   check (`WinCheck.check` reads `isEvil`, `WinCheck.kt:60-63`).
   Repro: seat 0 Snake Charmer, flip its alignment to evil (Pit-Hag scenario,
   `SeatSheet.kt:315`), seat 1 Fang Gu; run the Snake Charmer step and confirm the
   swap — seat 1 now shows as a **good** Snake Charmer.
2. **P0 · The resolver is offered even when the Snake Charmer cannot use their
   ability.** There is no `StatusEffects.isImpaired` check anywhere in the
   `"snakecharmer"` branch (`NightScreen.kt:471-482`), unlike `DemonKillPanel`
   which warns loudly (`NightScreen.kt:548-554`). A drunk/poisoned Snake Charmer
   who points at the Demon must have **nothing happen**. This bites immediately
   and permanently after a successful swap, because the new Snake Charmer is
   poisoned forever and their step will keep offering a second swap every night.
   Repro: add a `("poisoner","Poisoned")` token to the Snake Charmer, open their
   step — the swap button is offered with no warning at all.
3. **P0 · Character-bound reminder tokens do not follow the swap.**
   `swapCharacters` (`GameActions.kt:99-115`) moves only the two id fields. After
   a swap the ex-Demon's seat keeps its Demon bookkeeping (`("fanggu","Once")`
   placed by the Fang Gu resolver at `NightScreen.kt:496`, Pukka's poison,
   `("lleech","Poisoned")`/host markers, Zombuul's "Died", Al-Hadikhia's tokens)
   while the new Demon's seat has none of them. The grimoire and every derived
   rule then disagree about the same Demon. Repro: Fang Gu jumps (placing
   `("fanggu","Once")`), later the Snake Charmer swaps with the Fang Gu — the
   "Once" token is on the seat that is now a good Snake Charmer.
   *(Note: player-bound tokens — Poisoner "Poisoned", Monk "Safe", Butler
   "Master", Witch "Cursed", Cerenovus "Mad", Exorcist "Chosen", Fortune Teller
   "Red herring" — must correctly stay put. The engine has no way to tell the two
   kinds apart today.)*
4. **P1 · The storyteller, not the app, decides whether the charm hit.** The panel
   title asks "Charm hit the Demon?" and hands the ST an unfiltered alive-player
   list (`NightScreen.kt:473-481`). The app knows who the Demon is. The ST's input
   should be "who did they point at?" and the app should answer "nothing happens"
   or "swap". As written, an ST can swap the Snake Charmer with the Pit-Hag by
   mis-tapping, with no warning.
5. **P1 · Choosing yourself cannot be recorded.** The candidate list filters
   `it.id != holder.id` (`NightScreen.kt:476-478`), but "the Snake Charmer chooses
   themself, so nothing happens" is an explicit official example, and it is the
   standard safe play. There is also no "they chose X, nothing happened" record at
   all, so nothing feeds the Mathematician, the game log or later reasoning.
6. **P1 · The post-swap hand-over is three manual searches.** The wiki prescribes
   an exact sequence per player (YOU ARE → thumbs-down → YOU ARE → Demon token,
   then YOU ARE → thumbs-up → YOU ARE → Snake Charmer token). The app has all
   three cards but as independent chips whose `token:"pick"` opens a search dialog
   (`GuideShowDialog`, `NightScreen.kt:366-454`) — even though after the swap the
   app knows both tokens exactly. Repro: complete a swap, tap "New character" —
   you are asked to search for the character you just assigned.
7. **P1 · Nothing tells the ST what the evil team does and does not learn.** After
   a swap, the new Demon has never been shown their Minions or any bluffs (the
   `DEMON_INFO` step ran on night 1 for a different player,
   `NightOrder.kt:81-119`), and the Minions still believe the old player is the
   Demon. *Uncertain:* the Snake Charmer page does not state this; the general
   rule is that no one is told anything they are not explicitly given, so the
   default should be "no one else is informed", surfaced as an explicit line the
   ST can read rather than a silence.
8. **P1 · The Poisoned token is exclusive, so a second swap un-poisons the first
   victim.** `snakeCharmerSwap` calls `placeExclusiveReminder`
   (`GameActions.kt:68-71`), which strips `("snakecharmer","Poisoned")` from every
   other seat first (`GameActions.kt:194-201`). The poison is permanent and
   per-victim; two swaps in one game (a Pit-Hag-made second Snake Charmer, or a
   Philosopher who gained the ability) would silently cure the first. The tray's
   copy-count logic enforces the same single-copy rule
   (`NightScreen.kt:319-339`) because `characters.json:941-943` declares one.
9. **P2 · Misregistration is never offered.** The Recluse "might register as evil
   & as a Minion or **Demon**" (`characters.json`, id `recluse`), so a Snake
   Charmer who points at the Recluse may, at the ST's discretion, trigger the
   swap — the Snake Charmer becomes the Recluse, the Recluse becomes the poisoned
   Snake Charmer, and (with the alignment fix) both stay good. The resolver gives
   no hint, unlike `InfoCalc.misregistrations` (`InfoCalc.kt:121-130`) which is not
   consulted here at all. *Uncertain:* the Snake Charmer page does not spell this
   out; it follows from the Recluse's text.
10. **P2 · Multi-Demon scripts are unhandled.** With Legion every player is a
    Demon, so a Snake Charmer pointing at any Legion player triggers a swap; with
    Lil' Monsta the "Demon" is a token held by a Minion, not a Demon-team seat;
    Lord of Typhon and Kazali can create several Demons. The resolver's
    `sortedByDescending { teamOf(it) == Team.DEMON }` (`NightScreen.kt:478`) copes
    visually but the rules consequences (which Demon? does the babysitting Minion
    count?) are not addressed. *Uncertain* — flagging rather than guessing.
11. **P2 · A dead Snake Charmer shows an empty panel.** `QuickResolutions` guards
    on `if (holder.alive)` (`NightScreen.kt:471`), so a dead holder gets the row
    with prose but no tools, plus the generic "All holders are dead — usually
    skip" line (`NightScreen.kt:751-757`). The row should simply not be emitted.
12. **P2 · No "already swapped tonight" guard.** `ResolutionPicker`
    (`NightScreen.kt:643-687`) clears its selection after acting, but re-selecting
    and confirming again swaps the two seats back and moves the Poisoned token,
    with no warning. Undo exists, but the state is silently wrong until noticed.
13. **P3 · `shownCharacterId` is unconditionally cleared on both seats**
    (`GameActions.kt:66-67`). Correct in the ordinary case; it would also silently
    erase a Lunatic's or Marionette's shown identity if the ST ever routed those
    seats through this action from the seat sheet's generic "Swap characters"
    (`SeatSheet.kt:118-141`, which calls `swapCharacters` and preserves the field —
    inconsistent between the two paths).
14. **P3 · Ability text vs How-to-Run drift.** The ability says "choose an **alive**
    player"; the How-to-Run says "point at **any** player". The app follows the
    ability text (alive-only filter, `NightScreen.kt:476`), which is the correct
    precedence. Worth a one-line note in the guide so an ST who reads the wiki
    isn't confused.

## Proposed behaviour (spec)

- **when**: both first and other nights (positions 31 / 19, unchanged).
  Wake condition: a seat with `nightRoleId == "snakecharmer"` and **alive**. Do not
  emit the row for a dead holder. Do emit it for a poisoned holder (they still
  wake; nothing happens) — the panel must say so.
- **targets**: exactly 1 seat.
  - Constraints: alive; **the holder themselves must be selectable** (official
    example) and confirming it records "chose self — nothing happens".
  - Picker: seat order, alive only, no demon-first sorting (that leaks the answer
    to a shoulder-surfing player; the app decides the outcome, so the ST does not
    need the hint). Reset the selection at every dusk.
- **immediate effects**: the app computes the outcome from the chosen seat.
  - **If the chosen seat is not a Demon** (and no misregistration is invoked), or
    **the Snake Charmer is impaired** (`StatusEffects.isImpaired`): record
    `SnakeCharmRecord(cycle, holderId, chosenId, swapped = false, reason)` and
    show `Nothing happens.`
  - **If the chosen seat is a Demon and the Snake Charmer's ability works**: run
    the corrected `snakeCharmerSwap`:

    ```kotlin
    fun snakeCharmerSwap(state, charmerId, demonPlayerId, lookup): GameState {
        val charmerWasEvil = state.player(charmerId)!!.isEvil(lookup)
        val demonWasEvil   = state.player(demonPlayerId)!!.isEvil(lookup)
        var next = swapCharacters(state, charmerId, demonPlayerId)   // now moves tokens too
        // Each player's NEW alignment == the OTHER player's OLD alignment.
        next = next.setAlignment(charmerId,     evil = demonWasEvil,   lookup)
        next = next.setAlignment(demonPlayerId, evil = charmerWasEvil, lookup)
        next = next.updatePlayer(charmerId)     { it.copy(shownCharacterId = null) }
        next = next.updatePlayer(demonPlayerId) { it.copy(shownCharacterId = null) }
        return addReminder(next, demonPlayerId, PlacedReminder("snakecharmer", "Poisoned"))
    }
    ```

    where `setAlignment(id, evil, lookup)` sets `alignmentFlipped =
    (evil != naturalTeamIsEvil(characterId))`. In the ordinary case
    (good charmer, evil demon) this reduces to `alignmentFlipped = false` on both,
    so the existing test `ScriptParserTest.kt:128-142` still passes.
  - Use `addReminder`, **not** `placeExclusiveReminder`, for the Poisoned token
    (defect 8), and raise `characters.json:941-943` to allow multiple copies (or
    make the copy-count rule ignore permanent poisons).
  - **Token migration in `swapCharacters`** (defect 3): move every reminder whose
    `sourceId` equals the *outgoing* `characterId` of a seat to the seat that now
    holds that character; leave all others in place. Express it as a single rule
    in `GameActions` so the Barber, Pit-Hag and Philosopher inherit it:

    ```
    for each seat S in {a, b}:
        tokens with sourceId == S.oldCharacterId  ->  move to the seat that now has S.oldCharacterId
        all other tokens                          ->  stay on S
    ```

    The one exception worth hard-coding: `("snakecharmer","Poisoned")` is a
    *player* condition, so it must **not** migrate.
- **deferred effects**:
  - **Same night**: the night sheet already re-derives, so the new Demon's kill
    step appears later tonight. The panel should say so explicitly:
    `<New demon> now acts as the <Demon> tonight — their kill step is further down
    this list.`
  - **From now on**: the new Snake Charmer wakes at the Snake Charmer step every
    night and is permanently poisoned, so their choice never does anything. The
    step must render `Poisoned by their own charm — nothing can happen.` rather
    than offering the swap again.
  - **Not informed**: add an explicit line — `The Minions are NOT told about the
    swap, and the new Demon is NOT shown the Minions or new bluffs.` (Flagged as
    the app's chosen default, with a note in the guide that it is the general rule
    rather than a page quotation.)
  - **Win check**: after a swap, re-evaluate `WinCheck` (`WinCheck.kt:34-101`) —
    the demon count is unchanged, but alignments moved.
- **expiry**: `("snakecharmer","Poisoned")` never expires (verified absent from
  both tables, `GameActions.kt:218-242`, and covered by
  `ScriptParserTest.kt:195-206`). Nothing else is placed.
- **information**: none computed for the Snake Charmer (they learn only by
  elimination). The panel's job is the *outcome*, not an info string.
- **visibility** (staged wizard replacing the three loose chips):
  1. `Show <new demon>: YOU ARE` → `EVIL` (`AlignmentCard(evil = true)`) →
     `YOU ARE` → `<Demon> token` (`CharacterCard`, pre-filled).
  2. `Show <new charmer>: YOU ARE` → `GOOD` → `YOU ARE` →
     `Snake Charmer token`.
  Each stage is one tap; nothing is searched for. Cards come from the existing
  `ShowCard` types (`components/ShowCards.kt:64-78`).
- **day-time inputs**: none.
- **interactions to handle explicitly**:
  - **Impaired Snake Charmer** → nothing happens (defect 2).
  - **Recluse** → offer "let the Recluse register as the Demon" as an explicit
    button, then run the full swap; both players stay good, the ex-Recluse is
    poisoned.
  - **Spy** → a Spy never registers as a Demon; no interaction.
  - **Legion / Lil' Monsta / Lord of Typhon / Kazali** → the panel must name which
    seats currently count as "the Demon" and, for Lil' Monsta, state the ruling
    the ST chose. Flag as open until confirmed against the Lil' Monsta page.
  - **Exorcist** → the Exorcist chose a *player*; if that player is no longer the
    Demon after a swap, the annotation in `NightOrder.kt:150-153` correctly
    disappears. Keep, and add a note to the panel when it happens.
  - **Philosopher gaining Snake Charmer** → the resolver must key on
    `nightRoleId`, and the poison token must be attributable to that seat.
  - **No Dashii** → `derivedPoison` (`StatusEffects.kt:14-33`) keys on
    `characterId`, so the adjacency recomputes around the new No Dashii seat
    automatically. **Works** — just add a regression test.

### UI text the step should display

- Prompt: `Snake Charmer — who did <name> point at?`
- Impaired banner: `<name> is poisoned — whoever they point at, nothing happens.`
- Non-demon result: `<target> is the <Character> — nothing happens.`
- Self result: `They chose themselves — nothing happens.`
- Demon result (before confirming):
  `<target> IS the Demon. Confirm to swap characters AND alignments: <charmer>
  becomes the <Demon> (evil), <target> becomes the Snake Charmer (good) and is
  poisoned for the rest of the game.`
- After confirming: the two-stage show wizard, then
  `<charmer> acts as the <Demon> later tonight. The Minions are not told.`

### Data changes

- `characters.json:941-943`: allow more than one `Poisoned` copy, or move the
  permanent-poison exemption into the tray's copy-count rule
  (`NightScreen.kt:319-339`).
- `night_guide.json:545-592`: add the alignment-swap clause ("if the Snake Charmer
  is evil or the Demon good, swap alignments as appropriate"), the
  impaired-does-nothing clause, the self-choice clause, and the "no one else is
  told" default. Replace the `token:"pick"` shows with `token:"auto"` variants
  that read the post-swap grimoire.
- No night-order change.

## Tests to add

1. **Evil Snake Charmer keeps evil (the wiki's Pit-Hag example).** Given seat 0 is
   the Snake Charmer with `alignmentFlipped = true` (evil) and seat 1 is the Fang
   Gu; When `snakeCharmerSwap(state, 0, 1)`; Then seat 0 is the Fang Gu **and
   evil**, seat 1 is the Snake Charmer **and evil**, and seat 1 is poisoned.
   *(Fails today: seat 1 comes back good.)*
2. **Good Demon keeps good.** Given a Demon seat with `alignmentFlipped = true`
   (turned good) and a good Snake Charmer; When the swap runs; Then the new Demon
   seat is good and the new Snake Charmer seat is good.
3. **Ordinary case is unchanged.** Re-assert `ScriptParserTest.kt:128-142` against
   the new implementation.
4. **Impaired charmer does nothing.** Given the Snake Charmer carries
   `("poisoner","Poisoned")` and points at the Demon; When the resolver runs;
   Then no characters or alignments change and no Poisoned token is added.
5. **Self-choice does nothing but is recorded.** Given the Snake Charmer chooses
   themselves; Then the state is unchanged except for a
   `SnakeCharmRecord(swapped = false)`.
6. **Character-bound tokens migrate.** Given seat 1 is the Fang Gu with
   `("fanggu","Once")` and seat 0 is the Snake Charmer; When the swap runs; Then
   `("fanggu","Once")` is on seat 0 (the new Fang Gu) and not on seat 1.
7. **Player-bound tokens do not migrate.** Given seat 1 (the Demon) carries
   `("monk","Safe")` and `("witch","Cursed")`; When the swap runs; Then both are
   still on seat 1.
8. **Second swap does not cure the first victim.** Given a completed swap
   poisoning seat 1; When a Pit-Hag-made second Snake Charmer on seat 4 swaps with
   the new Demon on seat 0; Then seat 1 still carries
   `("snakecharmer","Poisoned")`.
9. **Poison survives dawn and dusk.** (Already covered by
   `ScriptParserTest.kt:195-206`; keep.)
10. **Night sheet hand-over.** Given a night-2 swap; Then
    `NightOrder.otherNight(state, lookup)` built *after* the swap contains the
    Demon's step with the ex-charmer's seat id, and the `snakecharmer` step with
    the ex-Demon's seat id.
11. **No Dashii adjacency follows the swap.** Given a No Dashii that swaps with the
    Snake Charmer; Then `StatusEffects.derivedPoison` names the new No Dashii
    seat's Townsfolk neighbours, not the old one's.
12. **Dead charmer emits no step.** Given the Snake Charmer is dead; Then no
    `snakecharmer` step is emitted.
13. **Recluse ruling.** Given the ST invokes "the Recluse registers as the Demon";
    When the swap runs against the Recluse; Then both seats are good, seat roles
    are swapped, and the ex-Recluse carries the permanent poison.
