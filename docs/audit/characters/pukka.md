# Pukka (pukka) — Bad Moon Rising Demon

## Official rules (sources)

Sources: <https://wiki.bloodontheclocktower.com/Pukka>,
<https://wiki.bloodontheclocktower.com/Exorcist>,
<https://wiki.bloodontheclocktower.com/Lunatic>,
<https://wiki.bloodontheclocktower.com/Night_Order>.

**Current ability text (matches `characters.json`):**

> "Each night, choose a player: they are poisoned. The previously poisoned player dies then becomes healthy."

**How to run (wiki, verbatim):**

> "Each night, wake the Pukka. They point at any player. Put the Pukka to sleep. The chosen
> player is **poisoned**—mark them with a **POISONED** reminder. Each night except the first,
> the other player marked **POISONED** dies—mark them with a **DEAD** reminder, then remove
> their **POISONED** reminder."

Reminder tokens: **POISONED**, **DEAD**.

**Timing and edge cases (all wiki-verbatim unless marked INFERRED):**

- **Acts on night 1.** "Unlike other Demons, the Pukka acts during the first night." On night 1
  it *only* poisons — nobody dies, because there is no previously-poisoned player.
- **Order within the step: poison first, then the death.** "The next night, just after the
  Pukka attacks again, that player dies." The new POISONED marker goes down, *then* the old
  marked player dies. This is why the how-to-run says "the **other** player marked POISONED".
- **The victim dies poisoned.** "Players that the Pukka kills are still poisoned at their time
  of death." → "For example, if the Pukka kills the Sage, the Sage may get false information
  due to being poisoned by the Pukka." Same for the Ravenkeeper, Moonchild's public choice,
  Farmer, Sweetheart, Barber, Poppy Grower — any on-death trigger fires *impaired*.
- **The poison is unblockable except by the Goon.** "Your poison cannot be blocked by anything
  but the wily Goon." No protection, no Monk, nothing stops the *poisoning* — only the death.
  (Goon: "Each night, the 1st player to choose you with their ability is drunk until dusk. You
  become their alignment." So a Pukka that poisons the Goon becomes drunk — and therefore the
  Goon is *not* poisoned — and the Goon turns evil.)
- **Protection stops the death but the target still becomes healthy.** "The Innkeeper prevents
  the Pukka from killing a poisoned player, then that player is no longer poisoned." So a
  protected victim: does not die, loses the POISONED marker anyway. INFERRED-BY-ANALOGY: the
  same for Monk `Safe`, Soldier, sober Sailor, Tea Lady `Can not die`, Fool's first death,
  Innkeeper `Protected`. The wiki only states the Innkeeper case explicitly.
- **Drunk/poisoned Pukka, at choice time:** "If the Pukka is drunk and chooses a player, that
  player does not become poisoned." (No poison is applied; nothing to kill next night.)
- **Drunk/poisoned Pukka, at kill time:** "If the Pukka was sober when they chose a player the
  previous night, but is drunk at night, that player does not die." The standing POISONED
  marker is *not* cleared by this (INFERRED: the Pukka's ability didn't work, so nothing
  happened — the marked player stays poisoned and is the one who dies on the next working
  night).
- **Exorcist.** Pukka page: "The Exorcist prevents the Pukka from waking to poison a player."
  Exorcist page, explicitly: **"The Pukka does not wake to attack tonight, but a player still
  dies because of the Pukka's attack during the previous night."** This is the single most
  counter-intuitive Pukka ruling: an Exorcised Pukka places **no new poison** but the
  previously-poisoned player **still dies and becomes healthy**. After an Exorcised night the
  grimoire holds no POISONED marker, so nobody dies the following night either.
- **Pukka choosing the same player two nights running:** not addressed on the wiki.
  INFERRED reading of the ability text: they are (re)poisoned, then as "the previously poisoned
  player" they die and become healthy — net result: dead, healthy, and no standing poison
  going into the next night. Flag this as a Storyteller choice in the UI rather than silently
  deciding it.
- **Pukka dies:** not addressed on the wiki. INFERRED: a dead Pukka has no ability, so the
  standing POISONED player never dies from it — but they are also never made healthy, so they
  stay poisoned unless another effect removes it. (If the Scarlet Woman becomes the Pukka, the
  new Pukka inherits the standing POISONED marker and kills that player on their next night.)
- **First-night position.** Official first-night order puts the Pukka after Mezepheles and
  before Pixie; app data matches (`night_and_jinxes.json:336`). Other nights: after Zombuul,
  before Shabaloth (`night_and_jinxes.json:412`). Both are correct today.

**Jinx.** Summoner: "The Summoner may summon a Pukka on the 2nd night instead of the 3rd."
(Because the Pukka needs a poison down a night before it can kill.) **This jinx is missing
from `night_and_jinxes.json`** (58 jinxes, none referencing `pukka`).

**Lunatic.** Ability: "You think you are a Demon, but you are not. The Demon knows who you are &
who you choose at night." Lunatic first-night reminder (`characters.json:558`): "If the token
received by the Lunatic is a Demon that would wake tonight: Allow the Lunatic to do the Demon
actions." A Lunatic shown the Pukka token therefore **wakes on night 1 and every night**, points
at a player, and the ST places Lunatic `Attack 1` markers; nothing happens. The real Demon is
then shown who the Lunatic chose.

## What the app does today

- **Data.** `characters.json:673-686` — ability text, first/other night reminders and reminders
  `["Poisoned", "Dead"]` all match the wiki. Night-order positions correct
  (`night_and_jinxes.json:336`, `:412`). Works.
- **Night guide.** `night_guide.json:365-374` has good, accurate first- and other-night prose
  (it even says "protection prevents the death but not the poisoning"). Works as *text*.
  It is wrong on one point: it says nothing about the Exorcist case, and the generic
  Exorcist annotation (below) contradicts the real ruling.
- **Night step construction.** `NightOrder.build` (`NightOrder.kt:142-178`) renders the step
  title/detail from `otherNightReminder`. Because `character.team == Team.DEMON`, two generic
  annotations are appended:
  - `NightOrder.kt:150-154`: if the holder carries `exorcist:Chosen`, appends
    "— EXORCIST chose them: the Demon does not act tonight." **This is wrong for the Pukka**
    (see P0-2).
  - `NightOrder.kt:157-172`: on non-first nights, if a Lunatic is in play, appends the Lunatic's
    marked choices. Not appended on night 1 — but the Pukka *does* act on night 1.
- **The night action.** `NightScreen.QuickResolutions` (`NightScreen.kt:462-525`) has hard-coded
  branches for `snakecharmer`, `fanggu`, `professor` only. Everything else falls to
  `else -> if (character?.team == Team.DEMON && holder.alive) DemonKillPanel(...)`
  (`NightScreen.kt:518-523`). So the Pukka's step shows **"Demon kill — who did <name>
  choose?"** (`NightScreen.kt:544`), a seat picker, and a **"<name> dies"** button that calls
  `GameActions.kill(target, DeathCause.DEMON)` (`NightScreen.kt:625-633`). **This is the bug the
  user reported.** There is no poison step, no delayed death, no night-1 special case.
- **Poison placement is fully manual.** The ST must open the `NightToolTray`
  (`NightScreen.kt:193-357`), tap the `Poisoned` chip, then tap a seat. That path *does* do the
  right thing mechanically: `character.allReminders.count { it == "Poisoned" } == 1`, so it uses
  `GameActions.placeExclusiveReminder` (`NightScreen.kt:323-324`, `GameActions.kt:194-201`),
  which moves the single token. But moving it silently erases the only record of who was due to
  die — so if the ST places the new poison before recording the kill, the previous victim is
  lost.
- **Impairment.** `StatusEffects.isImpaired` (`StatusEffects.kt:36-46`) matches any reminder
  label containing "poison", so `pukka:Poisoned` correctly marks the target as impaired for
  `InfoCalc`, and the green "!" badge shows on the seat (`GrimoireScreen.kt:421-433`). Works.
- **Expiry.** `pukka:Poisoned` is in neither `EXPIRES_AT_DAWN` (`GameActions.kt:218-225`) nor
  `EXPIRES_AT_DUSK` (`GameActions.kt:231-242`), so it correctly persists across dawn and dusk.
  Works.
- **Death record.** `GameActions.kill` (`GameActions.kt:136-156`) snapshots
  `abilityImpairedAtDeath = StatusEffects.isImpaired(state, ...)` **from the pre-kill state**, so
  if the ST kills the victim *before* moving the poison token, "died poisoned" is recorded
  correctly. If they move the token first, it is recorded wrongly. Nothing enforces the order.
- **Dawn.** The DAWN step is a static string: "Wait a few seconds. Everyone opens their eyes.
  Announce who died." (`NightOrder.kt:59`). It never says *who*. There is no dawn report
  anywhere in the app.
- **Day.** Nothing surfaces "X is poisoned by the Pukka today" at day start; the ST must
  remember to give false info to that player's day-time-triggered abilities and to notice the
  green badge in the grimoire.
- **Exorcist gating.** The `exorcist:Chosen` token only changes the step's *text*
  (`NightOrder.kt:150-154`); the kill panel is still offered.
- **Lunatic-as-Pukka.** The Lunatic's own step exists on night 1 (`night_and_jinxes.json:311`)
  with good guide prose (`night_guide.json:291`), but there is **no action UI** for it: no
  picker, no automatic `Attack 1` placement, and no per-demon shaping (a Lunatic-Pukka should be
  asked to *poison*, not to *kill*). `QuickResolutions` has no `lunatic` branch, and the Lunatic
  is Team.OUTSIDER so the demon fallback never fires.

## Defects and gaps

1. **P0 · The Pukka's night step offers a generic kill instead of poison-then-delayed-death.**
   Rules require: choose a player → they are poisoned now → the *previously* poisoned player dies
   now and becomes healthy. App shows "Demon kill — who did X choose?" and kills the chosen
   player immediately. `NightScreen.kt:518-523` → `NightScreen.kt:534-638`.
   *Repro:* BMR game, Pukka in play, Night 2 → Night tab → tap the Pukka row → the panel says
   "Demon kill". Picking a player and confirming kills the wrong player on the wrong night.
2. **P0 · Exorcist on the Pukka suppresses the wrong half of the ability (in the text) and
   nothing in the code.** The step text says "the Demon does not act tonight"
   (`NightOrder.kt:150-154`), so a rules-following ST skips the step entirely — but the
   previously-poisoned player **still dies** ("The Pukka does not wake to attack tonight, but a
   player still dies because of the Pukka's attack during the previous night", Exorcist wiki).
   The app both mis-states the rule and offers no way to run the half-step.
   *Repro:* place `exorcist:Chosen` on the Pukka seat, open night 3.
3. **P0 · Night 1 offers a kill.** On night 1 the Pukka may only poison. The first-night step
   detail is correct prose, but `QuickResolutions` still renders `DemonKillPanel` (the demon
   branch is not gated on `state.cycle`), inviting a night-1 death.
   `NightScreen.kt:518-523`.
4. **P1 · The pending victim is invisible and is destroyed by placing the next poison.**
   There is one `pukka:Poisoned` token and `placeExclusiveReminder` moves it
   (`GameActions.kt:194-201`, `NightScreen.kt:318-340`). The grimoire never says "this player
   dies at the Pukka's next wake", and once the ST places tonight's poison the previous holder is
   unrecoverable except by undo.
5. **P1 · Death-while-poisoned ordering is left to the ST.** "Players that the Pukka kills are
   still poisoned at their time of death" — the snapshot in `GameActions.kill:153` is only right
   if the ST kills before moving the token. The app should do both in one atomic action.
6. **P1 · Protection does not clear the poison.** Even when the ST correctly decides "protected,
   no death", nothing removes the victim's `pukka:Poisoned` token, yet the rules say they become
   healthy ("The Innkeeper prevents the Pukka from killing a poisoned player, then that player is
   no longer poisoned"). The ST must remember to clear it manually — and if they don't, the app's
   `isImpaired` will keep feeding that player false info forever.
7. **P1 · No dawn announcement.** The DAWN step never names the Pukka's victim
   (`NightOrder.kt:59`); no dawn report exists anywhere.
8. **P1 · No day briefing for the poisoned player.** The ST must remember all day that the
   POISONED player's abilities malfunction (Gossip statement resolution, Savant, Fisherman,
   Artist, Slayer shot, Moonchild choice, Juggler guess…). Only a small green dot in the grimoire
   hints at it (`GrimoireScreen.kt:421-433`).
9. **P1 · The Lunatic who thinks they are the Pukka has no night action at all.** No picker, no
   automatic `Attack 1` placement, no night-1 wake prompt shaped as "poison", and no automatic
   hand-off of the choice to the real Demon on night 1 (`NightOrder.kt:157` is gated on
   `!isFirstNight`).
10. **P2 · Pukka is not in `InfoCalc`/protection logic as a Demon-kill source.**
    `StatusEffects.deathNotes` (`StatusEffects.kt:52-129`) is only consulted when the ST picks a
    target in `DemonKillPanel`. With a proper Pukka panel it must be consulted for the *pending
    victim*, at the moment of death, one night later.
11. **P2 · Missing Summoner jinx** ("The Summoner may summon a Pukka on the 2nd night instead of
    the 3rd") — absent from `night_and_jinxes.json` (`jinxes` array, 58 entries).
12. **P2 · Goon interaction unmodelled.** The Goon is the only thing that blocks the Pukka's
    poison, and it flips the Goon's alignment to evil and makes the Pukka drunk until dusk.
    Nothing warns the ST when the Pukka's target is the Goon.
13. **P3 · Night-guide text omits the Exorcist and the same-target case**
    (`night_guide.json:365-374`).

## Proposed behaviour (spec)

### Engine model

Introduce one exclusive token and one atomic action.

- Token: `PlacedReminder("pukka", "Poisoned")` — **exclusive** (exactly one in the grimoire),
  never expires at dawn or dusk, removed only by the Pukka's own action or by the ST.
- Derived state: `pukkaPendingVictim(state) = state.players.firstOrNull { it.reminders.any { r -> r.sourceId == "pukka" && r.label == "Poisoned" } }`.

```kotlin
// GameActions.kt
fun pukkaNight(
    state: GameState,
    pukkaPlayerId: Long,
    newTargetId: Long?,      // null = the Pukka did not act (Exorcised / dead / drunk-skip)
    applyPoison: Boolean,    // false when the Pukka is drunk/poisoned at choice time
    resolveDeath: Boolean,   // false when the Pukka is drunk/poisoned at kill time
    deathPrevented: Boolean, // true when protection saved the pending victim
    lookup: (String) -> Character?,
): GameState
```

Order of operations inside `pukkaNight` (this order is load-bearing):

1. `victim = pukkaPendingVictim(state)`.
2. If `victim != null && resolveDeath && !deathPrevented` → `kill(victim, DeathCause.DEMON)`
   **before** touching any token, so `abilityImpairedAtDeath` snapshots `true`
   (`GameActions.kt:153`) — "still poisoned at their time of death".
3. If `victim != null && resolveDeath` (die *or* protected) → remove `pukka:Poisoned` from the
   victim ("then becomes healthy" — the Innkeeper ruling says this happens even when protected).
   If `!resolveDeath` (Pukka impaired tonight) → leave the token where it is.
4. If `newTargetId != null && applyPoison` → `placeExclusiveReminder(newTargetId, pukka:Poisoned)`.
   Note step 3 already cleared the old one; exclusivity makes this idempotent.
5. Same-target case (`newTargetId == victim.id`): after step 2/3 the target is dead and healthy.
   Do **not** re-place the token (there is no "previously poisoned player" for next night). The
   UI must state this before confirming.

### Structured night action

- **when:** both (first night *and* other nights).
- **wake condition:** holder alive; skip if the holder is dead. If the holder carries
  `exorcist:Chosen`, run a **reduced step** (death only, no new poison) — do not skip.
- **targets:** exactly 1, any player including dead ones and including the Pukka itself
  (the wiki says "any player"). Picker default sort: alive first, then seat order; grey out
  and label the current `pukka:Poisoned` holder as "already poisoned — will die tonight".
- **immediate effects:**
  - place `pukka:Poisoned` (exclusive) on the chosen player — *unless* the Pukka is impaired or
    Exorcised;
  - kill the previous `pukka:Poisoned` holder (`DeathCause.DEMON`), subject to protection;
  - remove `pukka:Poisoned` from that previous holder.
- **deferred effects:** none beyond the standing token — the whole point is that tonight's poison
  is next night's death.
- **expiry:** `pukka:Poisoned` never expires. It survives dawn, dusk, and the Pukka's death.
- **information:** none computed for the Pukka; but the token must feed `isImpaired`
  (already does) and must be visible on the day briefing.
- **visibility:** night 1 — the Pukka is the Demon, sees Minions + 3 bluffs via `DEMON_INFO`
  (`NightOrder.kt:81-119`). If a Lunatic is in play, show the Demon the Lunatic and the Lunatic's
  night-1 choice **on night 1 too**.
- **day-time inputs:** none of its own.

### Night step UI (replaces `DemonKillPanel` for `pukka`)

**Night 1 panel — heading:** `Pukka — poison only. Nobody dies on the first night.`

```
Who did <Pukka name> point at?
[ seat chips, alive first ]
→ [ Poison <Name> ]        (single confirm; places pukka:Poisoned)
Footer: "No death tonight — <Name> dies at the Pukka's next wake."
```

**Other nights, normal panel — heading:** `Pukka — poison one, kill the last one.`

```
Dying tonight: <Prev>  (poisoned since night N-1)
   ! <deathNotes for Prev, from StatusEffects.deathNotes>
   [ <Prev> dies ]   [ Protected — no death ]
   (both buttons clear <Prev>'s Poisoned token: "then becomes healthy")

Who does <Pukka name> poison tonight?
[ seat chips ]  → [ Poison <Name> ]
```

- Present the two halves as one confirm when possible: `[ Poison <New> · <Prev> dies ]`.
- If the ST taps the *same* seat as `<Prev>`, swap the confirm to
  `[ <Prev> is poisoned again, dies, and becomes healthy — no-one is poisoned tomorrow ]`.
- If `StatusEffects.isImpaired(pukka)` → banner:
  `! The Pukka is drunk/poisoned: <New> is NOT poisoned, and <Prev> does NOT die. Leave the
  Poisoned token where it is.` with a `[ Nothing happens ]` button that completes the step.
- If the Pukka's target is the Goon → banner:
  `! Goon: the Pukka is drunk until dusk (so <Name> is NOT poisoned) and the Goon becomes evil.`
- If the holder carries `exorcist:Chosen` → **reduced panel**:
  `Exorcist chose the Pukka. No new poison tonight — but <Prev> still dies from last night's
  poison.` with `[ <Prev> dies ]` / `[ Protected — no death ]`. After it resolves, note:
  `No-one is poisoned now, so no-one dies at the Pukka's next wake.`
- If the Pukka is dead → step collapses to
  `The Pukka is dead — no poison, no death. <Prev> stays poisoned.`

### Deferred / dawn / day output

- **Dawn (engine `DawnReport`, consumed by the DAWN step and by a day-start banner):**
  - `"<Prev> died in the night."` — plus, when nobody died, the explicit
    `"Nobody died in the night."` The Pukka is *not* named; nothing about poison is announced.
  - If the victim was protected: no announcement at all (still say "nobody died" if applicable).
- **Day briefing (day-start card):**
  - `"<Name> is poisoned (Pukka) — all their info and abilities are false today, and they die at
    the Pukka's next wake unless protected."`
  - If the Pukka's victim died and had an on-death trigger (Ravenkeeper/Sage/Moonchild/Farmer/…)
    remind: `"<Name> died poisoned — their death trigger malfunctions."`
- **Nomination time:** the poisoned player's Virgin/Slayer/Gossip etc. malfunction — reuse
  `StatusEffects.nominationWarnings` with a new entry keyed off any `*:Poisoned` token.

### Protection checks (the death half only — never the poison half)

Consult `StatusEffects.deathNotes(state, lookup, victimId)` at kill time and require an explicit
choice. Applicable protections and the right ST wording:

| Source | Effect on the Pukka's pending death | Poison cleared? |
|---|---|---|
| Monk `Safe` on the victim | No death ("safe from the Demon") | Yes |
| Innkeeper `Protected` | No death (explicit wiki ruling) | Yes |
| Soldier (character) | No death | Yes |
| Sailor, sober | No death | Yes |
| Tea Lady `Can not die` / both neighbours good | No death | Yes |
| Fool, first death, has ability | No death; mark Fool `No ability` | Yes |
| Devil's Advocate `Survives execution` | **Irrelevant** — execution only, not night deaths | — |
| Goon | Blocks the *poison*, not the death (Pukka becomes drunk, Goon becomes evil) | n/a |

The "poison cleared" column is INFERRED for everything except the Innkeeper; the panel should
say so once: `"Protected — they survive and become healthy (Innkeeper ruling; applied to all
protections)."`

### Lunatic pretending to be a Pukka

- The Lunatic's night step must render **the same panel as the demon in `shownCharacterId`**,
  in "placebo" mode: same wording, same picker, but the confirm places `lunatic:Attack 1` on the
  chosen seat and changes **nothing else** — no poison, no death, no `pukka:Poisoned` token.
- Because a Lunatic shown the Pukka token believes they act on night 1, the Lunatic step must
  offer the panel on **night 1** as well (data already wakes the Lunatic first night:
  `night_and_jinxes.json:311`).
- Immediately after, surface a hand-off block on the **Lunatic's own step** (not the Demon's):
  `Wake the real Demon (<name>). Show the Lunatic token, point at <Lunatic>, then point at
  <chosen player>. Put them back to sleep.` Then auto-remove the `Attack 1` marker when the step
  is checked off (or keep the existing `EXPIRES_AT_DAWN` entries at `GameActions.kt:222-224`).
- Extend `NightOrder.kt:157` to run on the **first** night too when the Lunatic's shown Demon
  wakes on night 1 (Pukka, Lord of Typhon, Kazali, Lil' Monsta, Yaggababble).
- Lunatic bluffs: the Lunatic must get its **own** 3 bluffs and its own fake Minion set,
  independent of `state.demonBluffIds` (`NightOrder.kt:90`, `BluffsSheet.kt`) — the ST currently
  has one bluff list for the whole game. (Cross-cutting; also in the lunatic auditor's scope.)
- If the Lunatic-Pukka's "victims" never die, that is expected and the ST needs no prompt; but
  the real Pukka's panel should show `Lunatic (<name>) poisoned <X> last night — mirror or
  diverge?` (today it appends only on non-first nights, `NightOrder.kt:163-170`).

### Data changes

- `night_and_jinxes.json` — add jinx
  `{"id1":"summoner","id2":"pukka","reason":"The Summoner may summon a Pukka on the 2nd night instead of the 3rd."}`.
- `night_guide.json:365-374` — append to the `other` instructions:
  "If the Exorcist chose the Pukka, the Pukka does not poison tonight, but the previously
  poisoned player still dies. If the Pukka is drunk or poisoned, no-one is poisoned and no-one
  dies. Protection stops the death but the player still becomes healthy."
  Append to `first`: "If a Lunatic thinks they are the Pukka, they act tonight too."
- `characters.json:673-686` — no changes needed; ability text and reminders already match.

## Tests to add

1. `pukka night 1 poisons and kills no-one`
   Given a BMR game, Pukka on seat 0, cycle 1, NIGHT.
   When `pukkaNight(pukka=0, newTarget=2, applyPoison=true, resolveDeath=true)`.
   Then seat 2 holds `pukka:Poisoned`, `state.deaths` is empty, and every player is alive.
2. `pukka night 2 kills the previously poisoned player and moves the poison`
   Given seat 2 holds `pukka:Poisoned`, cycle 2.
   When `pukkaNight(newTarget=3, ...)`.
   Then seat 2 is dead with `DeathCause.DEMON`, seat 2 has **no** `pukka:Poisoned`, seat 3 has it,
   and exactly one token named `Poisoned` with `sourceId == "pukka"` exists in the grimoire.
3. `pukka victim is recorded as impaired at death`
   Same as (2). Then `state.deaths.last().abilityImpairedAtDeath == true`.
   (Fails today if the ST moves the token first — this test pins the ordering.)
4. `protected pukka victim survives but becomes healthy`
   Given seat 2 holds `pukka:Poisoned` and `monk:Safe`.
   When `pukkaNight(newTarget=3, deathPrevented=true)`.
   Then seat 2 is alive, has no `pukka:Poisoned`, and `state.deaths` is unchanged.
5. `exorcised pukka still kills but does not poison`
   Given the Pukka holds `exorcist:Chosen`, seat 2 holds `pukka:Poisoned`.
   When `pukkaNight(newTarget = null, applyPoison = false, resolveDeath = true)`.
   Then seat 2 is dead and **no** `pukka:Poisoned` token exists anywhere in the grimoire.
6. `impaired pukka neither poisons nor kills`
   Given the Pukka holds `poisoner:Poisoned`, seat 2 holds `pukka:Poisoned`.
   When the Pukka step resolves with `applyPoison=false, resolveDeath=false`.
   Then seat 2 is alive **and still holds** `pukka:Poisoned`; seat 3 holds nothing.
7. `pukka poison survives dawn and dusk`
   Given seat 2 holds `pukka:Poisoned`.
   When `advancePhase` NIGHT→DAY→NIGHT.
   Then seat 2 still holds it. (Guards against someone adding it to the expiry tables.)
8. `pukka choosing its previous target ends the poison chain`
   Given seat 2 holds `pukka:Poisoned`, cycle 3.
   When `pukkaNight(newTarget = 2)`.
   Then seat 2 is dead and no `pukka:Poisoned` exists.
9. `pukka poison makes the target impaired for InfoCalc`
   Given the Empath on seat 2 holding `pukka:Poisoned`.
   Then `InfoCalc.compute(..., "empath", 2, ...)` returns a caveat containing "POISONED".
   (Should pass today — pins `StatusEffects.kt:38-42` against label renames.)
10. `dawn report names the pukka victim`
    Given seat 2 died this night by the Pukka.
    Then `DawnReport.build(state)` contains `"<name> died in the night"` and does not mention
    poison. (New API.)
