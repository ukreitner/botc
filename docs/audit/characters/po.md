# Po (po) — Bad Moon Rising Demon

## Official rules (sources)

Sources: <https://wiki.bloodontheclocktower.com/Po>,
<https://wiki.bloodontheclocktower.com/Exorcist>,
<https://wiki.bloodontheclocktower.com/Goon>,
<https://wiki.bloodontheclocktower.com/Night_Order>.

**Current ability text (matches `characters.json:661`):**

> "Each night*, you may choose a player: they die. If your last choice was no-one, choose 3 players tonight."

**How to run (wiki, verbatim):**

> "Each night except the first, wake the Po. They either shake their head no or point at any
> player. Put the Po to sleep. If they shook their head no, mark the Po with the **3 ATTACKS**
> reminder. If they chose a player, that player **dies**—mark them with a **DEAD** reminder.
> If the Po wakes to act while marked **3 ATTACKS**, they point at any three players, one at a
> time. In the order chosen, each chosen player **dies**—mark them with **DEAD** reminders.
> Remove the **3 ATTACKS** reminder."

Reminder tokens: **3 ATTACKS**, **DEAD**.

**Timing and edge cases:**

- **Does not act on night 1**, and "this night does not count as a night where the Po 'chose no
  one'" — so the Po never wakes to three attacks on night 2.
- **"Chose no-one" is the trigger, not "nobody died."** Choosing a player who survives
  (protected, Soldier, Sailor…) is still *choosing a player*: no charge. Only a head-shake
  charges the Po. This is the distinction the app currently collapses (see P0-2).
- **Charging works while impaired:** "If the Po was drunk or poisoned when they chose nobody last
  night, they still choose three players tonight."
- **Spending is mandatory:** "A Po must choose three players when prompted to do so. They cannot
  choose no one again." So a charged Po can never bank a second charge; after a 3-attack night
  the token is removed and the Po is back to normal.
- **Order matters, and impairment is re-evaluated between kills.** Wiki example, verbatim:
  "The Po attacks the Moonchild, then the Goon, then the Grandmother. Only the Moonchild dies,
  because the Po became drunk when they attacked the Goon." (Goon: "Each night, the 1st player to
  choose you with their ability is drunk until dusk. You become their alignment." — so choosing
  the Goon mid-sequence drunkens the Po *and flips the Goon to evil*, and kills 2 and 3 fail.)
  The three picks must therefore be resolved sequentially, not as a set.
- **Exorcist:** "If the Exorcist selects the Po, the Po does not act, but this night does not
  count as a night where the Po 'chose no one.'" So: no kill, **no** 3 ATTACKS marker.
  INFERRED (not on the wiki): if the Po was *already* marked 3 ATTACKS and is then Exorcised, the
  marker stays (they didn't act, so they didn't spend it) — the Po attacks three next night.
- **Wholly poisoned 3-attack night:** wiki example — "The Po chooses to attack nobody, but is
  drunk. The next night, the Po is poisoned. They choose three players, but none of them die.
  The following night, the Po is sober and healthy and attacks a player, who dies." Note the
  charge is still *spent*: a poisoned Po that picks three loses the marker for nothing.
- **Choosing dead players / choosing itself:** not addressed on the wiki. "Any player" is the
  literal text. INFERRED: legal but pointless for a dead target; a Po choosing itself simply
  dies (Bad Moon Rising has no star pass — only the Scarlet Woman can catch the mantle, at 5+
  alive). Also INFERRED and worth a Storyteller prompt: whether attacks 2 and 3 still resolve
  after a Po kills itself with attack 1.
- **Repeating a player within one 3-attack night:** not addressed. INFERRED: allowed but wasted;
  the picker should discourage it rather than forbid it.
- **Night-order position.** Other nights: after Shabaloth, before Fang Gu
  (`night_and_jinxes.json:414`) — correct. No first-night entry — correct.

**Jinxes.** None published for the Po. `night_and_jinxes.json` has none — correct.

**Lunatic.** A Lunatic shown the Po token believes they act every night *except the first*, and
believes they can charge. The ST must let them shake their head "no" and then, the night after,
point at three players, marking `lunatic:Attack 1/2/3` — this is exactly why the Lunatic carries
three Attack reminders (`characters.json:558`, `reminders: ["Attack 1","Attack 2","Attack 3"]`).

## What the app does today

- **Data.** `characters.json:658-671` — ability text, `otherNightReminder` and reminders
  `["Dead", "3 attacks"]` match the wiki (only one `Dead` label, though — see P2-7).
  Night-order position correct. Works.
- **Night guide.** `night_guide.json:359-364` (`other` only) has accurate prose describing both
  branches and the 3 Attacks reminder. It omits the "order matters / re-check drunkenness between
  kills" rule and the Exorcist clarification.
- **The night action is the generic demon kill.** `QuickResolutions`
  (`NightScreen.kt:462-525`) has no `po` branch; the `else` arm renders `DemonKillPanel`
  (`NightScreen.kt:518-523`, panel at `:534-638`). The ST sees "Demon kill — who did <name>
  choose?", a flat seat picker, and one **"<Name> dies"** button plus a **"No kill"** text button
  that merely clears the selection (`NightScreen.kt:634`) — it records nothing.
- **The 3 ATTACKS token is entirely manual.** The ST must open the `NightToolTray`, tap the
  `3 attacks` chip and tap the Po's own seat (`NightScreen.kt:283-353`); nothing places it, and
  nothing removes it after the three kills are taken. It is in neither expiry table
  (`GameActions.kt:218-242`), which is correct behaviour but leaves removal to the ST.
- **Impairment banner is actively misleading for the Po.** `NightScreen.kt:548-554` prints
  "! The Demon is drunk/poisoned — the attack fails (choose 'No kill')." For the Po, "No kill"
  and "chose no-one" are *different things* with opposite consequences (see P0-2).
- **Killing three players** is possible only by repeating select→confirm three times; after each
  confirm `targetId` resets to null (`NightScreen.kt:631`). There is no count ("kill 2 of 3"), no
  ordering record, and no re-evaluation of the Po's drunkenness between kills.
- **Protection warnings** do fire per target via `StatusEffects.deathNotes`
  (`NightScreen.kt:588-590`) — that part works, once per pick.
- **Self-kill:** `DemonKillPanel` labels the Po's own seat "(self)" and sorts it last
  (`NightScreen.kt:559-581`), but the star-pass branch is hard-coded to `demonId == "imp"`
  (`NightScreen.kt:591`), so a Po self-kill just kills the Po — which is right for BMR, though
  the Scarlet Woman note only appears via `StatusEffects.deathNotes` (`StatusEffects.kt:104-108`).
  Works.
- **Dawn** never names the victims (`NightOrder.kt:59`); with three deaths this is the night the
  ST most needs a list.
- **Lunatic-as-Po** has no action UI at all (no `lunatic` branch in `QuickResolutions`), so the
  ST hand-places `Attack 1/2/3` from the tray. Those three tokens do expire correctly at dawn
  (`GameActions.kt:222-224`).

## Defects and gaps

1. **P0 · No "charge" option.** The rules' first branch — head-shake → mark 3 ATTACKS — has no
   button. "No kill" (`NightScreen.kt:634`) only clears the picker and records nothing, so the
   Po's charge is lost unless the ST separately remembers to place the token from the tray.
   *Repro:* Night 2, Po in play → Night tab → Po row → there is no way to say "the Po chose
   no-one" that has any effect.
2. **P0 · "The attack fails" is conflated with "chose no-one".** The impaired banner
   (`NightScreen.kt:548-554`) tells the ST to press "No kill" when the Po is drunk/poisoned. If
   the Po actually *pointed at someone*, that is **not** a no-one night and must **not** charge;
   if the Po actually shook its head, it **does** charge even while drunk. The app gives one
   button for two opposite rules and no way to record which happened.
3. **P0 · A charged Po gets no "kill 3" flow.** No prompt, no counter, no ordered resolution, no
   automatic removal of `po:3 attacks`. `NightScreen.kt:534-638` knows nothing about the token.
   *Repro:* place `po:3 attacks` on the Po by hand, advance a night — the panel is identical to
   an ordinary night.
4. **P1 · Order-dependent resolution is not modelled.** The wiki's own example (Moonchild → Goon
   → Grandmother) requires re-checking `StatusEffects.isImpaired(po)` after each kill; the app
   resolves each pick independently with no re-check and no warning when a pick is the Goon.
5. **P1 · Exorcist handling is wrong in effect.** The step text says "the Demon does not act
   tonight" (`NightOrder.kt:150-154`) but the panel still offers a kill, and nothing prevents the
   ST from concluding "no kill happened → mark 3 ATTACKS", which the rules explicitly forbid
   ("this night does not count as a night where the Po 'chose no one'").
6. **P1 · No dawn announcement of the night's deaths** — worst case three names
   (`NightOrder.kt:59`).
7. **P2 · Only one `Dead` reminder label** (`characters.json:668-671`). The tray's copy-count
   logic (`NightScreen.kt:319-339`) therefore treats `po:Dead` as exclusive and *moves* it, so
   the ST cannot mark all three victims with Po tokens. Cosmetic only (the app has real
   alive/dead state), but it breaks the physical-grimoire metaphor the tray is built on.
8. **P2 · No prompt for the "Po killed itself with attack 1 of 3" case** — the ST must decide
   whether attacks 2 and 3 resolve, with no note that the wiki is silent.
9. **P2 · Night guide omits the two rulings most likely to be got wrong** (Exorcist does not
   charge; drunkenness is re-checked between the three kills) — `night_guide.json:359-364`.
10. **P2 · Lunatic-as-Po has no charge/3-attack placebo flow.** The ST must remember that a
    Lunatic-Po who shook their head last night should be asked for three names tonight, and must
    place `Attack 1/2/3` by hand.

## Proposed behaviour (spec)

### Engine model

- Token: `PlacedReminder("po", "3 attacks")` — **exclusive**, placed on the Po's own seat,
  **never expires** at dawn or dusk; removed only by spending it.
- Derived: `poIsCharged(state) = po seat holds po:"3 attacks"`.

```kotlin
// GameActions.kt
sealed interface PoChoice {
    object NoOne : PoChoice                              // head-shake -> charge
    data class One(val targetId: Long) : PoChoice        // ordinary kill
    data class Three(val targetIds: List<Long>) : PoChoice // spends the charge, in order
}

fun poNight(
    state: GameState,
    poPlayerId: Long,
    choice: PoChoice,
    /** ids the ST marked as protected/saved; those picks are skipped, not re-ordered. */
    prevented: Set<Long> = emptySet(),
    lookup: (String) -> Character?,
): GameState
```

Semantics:

- `NoOne` → `placeExclusiveReminder(poPlayerId, po:"3 attacks")`. **Applies even if the Po is
  drunk or poisoned** (explicit wiki rule).
- `One(t)` → if the Po is impaired **or** `t in prevented`, no death; **either way remove no
  charge and place no charge** (they chose a player).
- `Three(list)` → iterate in order; for each id: recompute
  `StatusEffects.isImpaired(currentState, lookup, poSeat)`; if impaired or `id in prevented`,
  skip; else `kill(id, DeathCause.DEMON)`. After the loop, **always** remove `po:"3 attacks"`
  (spent even when nothing died).
- Never auto-charge from a failed kill.

### Structured night action

- **when:** other nights only.
- **wake condition:** holder alive AND not carrying `exorcist:Chosen`. If Exorcised: show a
  read-only note (below) and complete the step; **do not** place `po:3 attacks`; **do not**
  remove an existing one.
- **targets:**
  - uncharged night: 0 or 1 target. Any player (alive first in the picker, self last and labelled
    "(self) — the Po simply dies; no star pass in Bad Moon Rising").
  - charged night: exactly 3 targets, **ordered**, chosen one at a time; duplicates allowed but
    warned ("already chosen — this attack is wasted"); the confirm is disabled below 3.
- **immediate effects:** deaths in order with a protection check per target; `po:3 attacks`
  placed or removed as above.
- **deferred effects:** the charge is the only carry-over, and it carries to *the Po's next wake*
  (not "next night") — an Exorcised night does not consume it.
- **expiry:** `po:3 attacks` never expires; add an explicit engine test so nobody adds it to
  `EXPIRES_AT_DAWN`/`EXPIRES_AT_DUSK` (`GameActions.kt:218-242`).
- **information:** none.
- **visibility:** night 1 demon info via `DEMON_INFO` (`NightOrder.kt:81-119`) — works today.
- **day-time inputs:** none of its own.

### Night step UI (replaces `DemonKillPanel` for `po`)

**Uncharged night — heading:** `Po — kill one, or charge for three.`

```
What did <Po name> do?
[ Pointed at a player ]     [ Shook their head — no-one ]

(if "pointed")   Who?  [ seat chips, alive first, self last ]
                 ! <deathNotes for the pick>
                 [ <Name> dies ]   [ Protected — no death ]

(if "no-one")    [ Charge: the Po attacks THREE players tomorrow night ]
                 -> places po:"3 attacks"
```

Impaired banner must be split, not shared:

> `! The Po is drunk/poisoned. A player they point at does NOT die — but they still do not
> charge. A head-shake still charges them for three attacks.`

**Charged night — heading:** `Po is charged — it MUST attack three players tonight, in order.`

```
Attack 1 of 3:  [ seat chips ]   ! <deathNotes>   [ <Name> dies ] [ Protected ]
Attack 2 of 3:  ...   (banner if the Po became drunk: "! The Po is now drunk — attacks 2 and 3 fail")
Attack 3 of 3:  ...
[ Finish — remove the 3 Attacks token ]      (enabled once three picks are resolved)
```

- After each resolved attack, re-render the impairment banner from the *current* state.
- If a pick is the **Goon**: `! Goon — the Po becomes drunk until dusk and the Goon becomes evil.
  This attack still kills; attacks after it do not.`
- If the Po picks itself: `! The Po dies. Bad Moon Rising has no star pass — check the Scarlet
  Woman (5+ alive). The wiki does not say whether the remaining attacks resolve; decide and note
  it.` with `[ Stop here ]` / `[ Continue attacks ]`.
- **Exorcised panel:** `Exorcist chose the Po — the Po does not act tonight. This does NOT count
  as choosing no-one, so no charge.` plus, if already charged, `The 3 Attacks token stays — the
  Po attacks three at its next wake.` One `[ Done ]` button.
- **Dead Po:** step collapses to `The Po is dead — no attack.`

### Deferred / dawn / day output

- **Dawn (`DawnReport`):** `"<A>, <B> and <C> died in the night."` (or "Nobody died in the
  night."). Never explain why. A three-death dawn is the Po's tell and the app should hand the ST
  the exact sentence.
- **Night-step badge:** when `po:3 attacks` is on the board, show a persistent chip on the Po's
  night row and on its grimoire seat: `CHARGED — 3 attacks next wake`.
- **Day briefing:** nothing required.

### Lunatic pretending to be a Po

- The Lunatic's step must render this exact panel in placebo mode, driven by
  `player.shownCharacterId == "po"`.
- Placebo charge: place `lunatic:"3 attacks"`-equivalent state. Since the Lunatic's reminder set
  is `Attack 1/2/3` (`characters.json:558`), track the fake charge with a non-expiring
  `lunatic:Charged` token (new label) so the Lunatic is asked for three names the following
  night; `Attack 1/2/3` keep their dawn expiry (`GameActions.kt:222-224`).
- After the Lunatic's picks are marked, show the hand-off block: `Wake the real Demon (<name>).
  Show the Lunatic token, point at <Lunatic>, then point at <A>, <B>, <C>.`
- The real Po's step already gets the Lunatic's choices appended (`NightOrder.kt:157-172`) —
  keep that, and add the count so the Po can mirror a three-kill night.

### Data changes

- `characters.json:668-671` — change `reminders` to `["Dead", "Dead", "Dead", "3 attacks"]` so the
  tray can mark three victims (`NightScreen.kt:319-339`). (Verify against the physical BMR token
  sheet before shipping; the app only needs the label count.)
- `night_guide.json:359-364` — append: "The Po only charges if it chose no-one; a kill that fails
  (protected player, drunk Po) is still a choice and does not charge. If the Exorcist chose the
  Po, it does not act and does not charge. Resolve the three attacks in order — if an attack
  makes the Po drunk (the Goon), the later attacks fail." Add a `first` entry: "The Po does not
  act on the first night, and this does not count as choosing no-one."
- `night_and_jinxes.json` — no changes.

## Tests to add

1. `po head shake charges even while poisoned`
   Given a Po holding `poisoner:Poisoned`, cycle 3, NIGHT.
   When `poNight(choice = PoChoice.NoOne)`.
   Then the Po seat holds `po:"3 attacks"` and nobody died.
2. `po kill that is prevented does not charge`
   Given a sober Po and target seat 2 holding `monk:Safe`.
   When `poNight(choice = One(2), prevented = setOf(2))`.
   Then seat 2 is alive **and** the Po holds no `po:"3 attacks"`.
3. `impaired po pointing at a player does not charge and does not kill`
   Given a Po holding `poisoner:Poisoned`.
   When `poNight(choice = One(2))`.
   Then seat 2 is alive and the Po holds no `3 attacks` token.
4. `charged po kills three in order and spends the charge`
   Given the Po holds `po:"3 attacks"`.
   When `poNight(choice = Three(listOf(2,3,4)))`.
   Then seats 2, 3, 4 are dead with `DeathCause.DEMON`, in that order in `state.deaths`, and the
   `3 attacks` token is gone.
5. `charge is spent even when every attack fails`
   Given the Po holds `po:"3 attacks"` **and** `poisoner:Poisoned`.
   When `poNight(choice = Three(listOf(2,3,4)))`.
   Then all three are alive and the `3 attacks` token is gone.
6. `po becoming drunk mid-sequence stops later attacks`
   Given the Po holds `po:"3 attacks"`, seat 3 is the Goon.
   When `poNight(choice = Three(listOf(2,3,4)))` and the Goon rule places `goon:Drunk` on the Po
   after attack 2.
   Then seat 2 is dead, seat 3 is dead (the Goon still dies from the attack that chose it),
   seat 4 is alive. (Mirrors the wiki's Moonchild/Goon/Grandmother example.)
7. `exorcised po neither kills nor charges nor spends`
   Given the Po holds `exorcist:Chosen` and `po:"3 attacks"`.
   When the Po step resolves as Exorcised.
   Then nobody died and the Po **still** holds `po:"3 attacks"`.
8. `3 attacks token survives dawn and dusk`
   Given the Po holds `po:"3 attacks"`.
   When `advancePhase` NIGHT→DAY→NIGHT.
   Then the token is still there.
9. `po does not appear on the first night order`
   Given a BMR game with a Po. Then `nightOrder.firstNight(...)` contains no step with id `po`.
   (Passes today — pins `night_and_jinxes.json`.)
10. `dawn report lists all three victims`
    Given three players died this night.
    Then `DawnReport.build(state)` names all three in seat order and says nothing else.
