# Lycanthrope (lycanthrope) — Experimental (Carousel) Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Lycanthrope> (fetched 2026-08-25).

**Current ability text (wiki, verbatim):**
> "Each night*, choose an alive player. If good, they die & the Demon doesn't kill tonight. One good player registers as evil."

`characters.json:1449` matches exactly — **no drift**.
**Important:** the brief's framing ("if good they die and no other deaths tonight") is the
*older* wording. The current text is narrower: it blocks **the Demon's kill**, not every
death. Deaths from other sources (Gossip, Godfather, Assassin, an execution earlier that
day, the Lycanthrope's own kill) still happen. The app's data is already on the new text.

**How to Run (verbatim):**
> "During setup, mark one good player with the Lycanthrope's **FAUX PAW** reminder.
>
> Each night except the first, wake the Lycanthrope. They point at any player. Put the Lycanthrope to sleep. If the chosen player is good, that player dies—mark them with the Lycanthrope's **DEAD** reminder. Later that night, wake the Demon, as normal, but the Demon cannot kill. If the chosen player is evil, nothing happens."

**Examples (verbatim):**
> "The Lycanthrope attacks the General. The General dies. Later that night, the Imp attacks the Amnesiac. The Amnesiac does not die, because the Imp cannot kill tonight."
>
> "The Lycanthrope attacks the Farmer. The Farmer dies, and another good player becomes a Farmer. The Magician was poisoned by the Pukka last night but does not die tonight, because the Pukka cannot kill tonight."
>
> "The Lycanthrope attacks the Godfather. The Godfather does not die, because the Godfather is evil. The Lycanthrope attacks the Zealot, who is registering as evil due to the Lycanthrope's ability. The Zealot does not die. The Demon attacks the Lycanthrope and the Lycanthrope dies."

Timing / edge cases that matter:

- **Other nights only** (night 2 onwards). Official other-night position 33 — **before every
  Demon** (Imp 37, Pukka 39, No Dashii 43, Vortox 44 …), which is exactly what makes the
  block runnable: the ST knows the Demon is silenced before reaching the Demon's step.
- The **Demon is still woken and still chooses** ("wake the Demon, as normal, but the Demon
  cannot kill") — so the Demon has no idea their kill failed, and Demons with side effects
  (Fang Gu jump, Al-Hadikhia, Po's no-kill night, Vigormortis's Minion poison) still resolve
  their non-kill parts. Only the *kill* fails.
- **The block covers deferred Demon kills.** Example 2 is explicit: the **Pukka's**
  previous-night poison victim does not die, "because the Pukka cannot kill tonight". Any
  implementation that only disables the "X dies" button on tonight's chosen target is wrong.
- **The Lycanthrope's kill is not a Demon kill.** Soldier/Monk ("safe from the Demon") do
  not protect against it; Innkeeper/Sailor/Tea Lady/Fool ("can't die" / "the first time they
  die, they don't") do. The victim is marked with the Lycanthrope's **DEAD** token.
- **FAUX PAW — "One good player registers as evil".** One good player is marked at setup and
  registers as evil. Example 3 confirms the Lycanthrope cannot kill them. Registration is
  unqualified in the ability text, i.e. it applies to every ability that checks alignment
  (Empath, Chef, Investigator, Noble, Cult Leader, Town Crier, Flower Girl, Oracle, Shugenja,
  Steward, Bounty Hunter, Seamstress, Balloonist team, Village Idiot …), while that player
  still **wins and loses with good**. It is the Lycanthrope's ability, so it stops working
  when the Lycanthrope is dead, drunk or poisoned.
  *Confidence: high on "registers as evil to alignment-checking abilities" (the ability text
  is unqualified and the wiki example uses it against the Lycanthrope's own kill); medium on
  the exact behaviour once the Lycanthrope is dead or impaired — flag it in the UI rather
  than silently deciding.*
- **Ability text says "an alive player"; How to Run says "any player".** Treat the ability
  text as binding (alive only) and note the discrepancy.
- **Jinx (in `night_and_jinxes.json:163-167`, Gambler):**
  "If the Lycanthrope is alive and the Gambler kills themself at night, no one else can die
  that night." No Jinxes section appears on the Lycanthrope wiki page itself.

## What the app does today

- `characters.json:1449-1463`: `reminders: ["Faux Paw","Dead"]`, `firstNightReminder: ""`,
  `otherNightReminder: "The Lycanthrope chooses a player."`.
- Night order: `night_and_jinxes.json:406` — other night index 33, correctly before all
  Demons; correctly absent from the first-night list.
- `night_guide.json:1090-1095` — the prose is **accurate and complete**, including the
  Demon-blocked case, the Faux Paw, and the drunk/poisoned case. It is the only place any of
  this exists.
- `NightOrder.build` (`NightOrder.kt:130-181`) emits a plain row: "Lycanthrope — <name>",
  detail "The Lycanthrope chooses a player."
- `QuickResolutions` (`NightScreen.kt:462-527`) has **no** `"lycanthrope"` branch → no target
  picker, no kill, no token, no block.
- `InfoCalc.supports("lycanthrope")` is **false** (`InfoCalc.kt:30-35`) → the whole caveat
  block at `NightScreen.kt:835-933` is skipped, so no drunk/poison/Vortox/Marionette warning
  ever renders on this step.
- `NightToolTray` (`NightScreen.kt:193-352`) offers "Faux Paw" and "Dead" chips for manual
  placement. "Mark spent" does not appear (ability is not once-per-game) — correct.
- `DemonKillPanel` (`NightScreen.kt:534-638`) is reached for every Demon via the `else`
  branch of `QuickResolutions` (`NightScreen.kt:520-526`). Its only kill-blocking warning is
  for an impaired **Demon** (`NightScreen.kt:545-552`). It knows nothing about the Lycanthrope.
- `Player.isEvil` (`GameState.kt:45-48`) is `team.isEvil != alignmentFlipped` — reminders are
  not consulted, so the Faux Paw player is evil to nothing.
- `GameActions.validateSetupState` (`GameActions.kt:503-561`) does not check for a Faux Paw.
- `GameShell.kt:347-375` has a setup prompt for the Fortune Teller's red herring; there is
  no Lycanthrope equivalent even though the token is exactly analogous.
- `SeatSheet.kt:264-282`: the only night-death button is "Died at night" →
  `DeathCause.DEMON`.

Storyteller experience: read the prose, wake the Lycanthrope, remember their choice, decide
by eye whether that seat is good, open the seat sheet and kill them with the wrong cause,
open the tray and place the "Dead" token, then **remember for the next twenty night steps
that the Demon must not kill**, and remember all game that one good player reads as evil.

## Defects and gaps

1. **P0 · The Demon's kill is not blocked.**
   After a successful Lycanthrope kill, `DemonKillPanel` (`NightScreen.kt:534-638`) still
   presents the full player list and an enabled "<name> dies" button, with no banner. The
   single most consequential rule of this character depends entirely on the ST's memory.
   *Repro:* Night 2 → Lycanthrope step (no tools) → scroll to the Imp step → pick anyone →
   "X dies" is live and green.

2. **P0 · The block is not applied to deferred Demon kills either.**
   The wiki's own example is the **Pukka**: a player poisoned last night must not die
   tonight if the Pukka cannot kill. The user's report already flags that the app treats
   the Pukka like a generic Demon; layering a naive Lycanthrope block on top of that (only
   disabling tonight's kill button) still gets the Pukka case wrong.

3. **P0 · No impairment warning on this step.**
   `InfoCalc.impairments` (`InfoCalc.kt:132-153`) knows about poison, drunkenness, the Drunk
   and the Marionette, but is unreachable for non-`supports` characters. A **poisoned
   Lycanthrope** must kill nobody **and must not block the Demon** — the difference between
   one death and two, silently. Nothing on screen indicates it.
   *Repro:* Poisoner poisons the Lycanthrope on night 2; expand the Lycanthrope step — no
   warning, and the guide prose still reads as if the ability works.

4. **P1 · "One good player registers as evil" is not modelled at all.**
   `Player.isEvil` (`GameState.kt:45-48`) ignores reminders, and `Ctx.isEvil`
   (`InfoCalc.kt:97`) simply forwards to it, so every alignment-based calculator in
   `InfoCalc` computes the **true** alignment and hands the ST the **wrong** "true info" to
   relay — Chef (`:189`), Empath (`:210-213`), Shugenja (`:250-251`), Undertaker (`:273-276`),
   Seamstress (`:359-362`), Dreamer/Village Idiot (`:371`), Cult Leader (`:391`),
   Steward (`:443`), Noble (`:452`), Bounty Hunter (`:461`), plus Investigator/Librarian/
   Washerwoman via `startKnowing` and the team-based Balloonist. The precedent for fixing
   this exists: the Fortune Teller special-cases the red-herring reminder.
   *Repro:* Place "Faux Paw" on the Empath's neighbour, then expand the Empath step — it
   reports 0 evil neighbours; the correct answer is 1.

5. **P1 · No Faux Paw setup prompt and no validation.**
   The token must be placed "during setup" on a **good** player. `validateSetupState`
   (`GameActions.kt:503-561`) has no Lycanthrope case, and no dialog offers it. An ST can
   play a whole Lycanthrope game without ever placing it.

6. **P1 · No resolver for the nightly choice.**
   There is no picker, no alive-only constraint, no automatic "is the target good (after
   Faux Paw registration)?" answer, no automatic death, no automatic "Dead" token, no
   "Demon can't kill tonight" marker.

7. **P1 · The Lycanthrope's kill is recorded as a Demon kill.**
   `SeatSheet.kt:270-272` maps "Died at night" to `DeathCause.DEMON`. That corrupts:
   the Choirboy trigger (King killed by the Lycanthrope would look like a Demon kill — see
   `king.md` defect 6), the Sage ("if the Demon killed them"), the death log, and any future
   "who killed whom" reporting. It should be `OTHER_NIGHT_DEATH` with an attributed source.

8. **P1 · Protection notes are Demon-flavoured and will mislead.**
   `StatusEffects.deathNotes` (`StatusEffects.kt:55-140`) emits "The Soldier is safe from
   the Demon" and "Marked 'Safe' (Monk) — protected from the Demon". Both are literally
   correct but, presented at the moment of a **Lycanthrope** kill, an ST will read them as
   "this death is prevented". A Lycanthrope resolver must filter to protections that stop
   *any* death (Sailor, Innkeeper "Protected", Tea Lady, Fool, Lleech host rule) and
   explicitly say the Demon-only ones do **not** apply.

9. **P1 · The Gambler jinx is data-only.**
   `night_and_jinxes.json:163-167` carries the text, surfaced as a line in the seat sheet
   (`SeatSheet.kt:222-232`) and the jinx dialog (`GameExtras.kt:200-220`). Nothing enforces
   "if the Lycanthrope is alive and the Gambler kills themself at night, no one else can die
   that night" — which is a *broader* block than the Lycanthrope's own.

10. **P2 · No alive-only constraint anywhere.** The ability says "an alive player"; there is
    no picker, so nothing can enforce it. Note the How-to-Run/ability wording mismatch in the
    guide.

11. **P2 · The "Dead" token has no defined lifetime.**
    `characters.json:1449` lists it; nothing places or clears it, and it is absent from both
    expiry tables (`GameActions.kt:218-242`). It should be placed by the resolver and persist
    (it is a permanent record of *how* that player died).

12. **P2 · No dawn briefing.** The user's top-level complaint applies squarely here: at dawn
    the ST must announce the Lycanthrope's victim and *not* announce a Demon victim. Nothing
    in `GameShell.advancePhase` (`GameActions.kt:258-263`, `GameShell.kt:126-168`) produces a
    "who died tonight and why" summary.

## Proposed behaviour (spec)

**Setup**

- If a Lycanthrope is dealt, raise a prompt (same shape as `GameShell.kt:347-375`):
  *"Lycanthrope — pick the good player who registers as evil (Faux Paw)."* Candidates:
  `state.players.filter { !it.isEvil(lookup) }`. Place `PlacedReminder("lycanthrope","Faux Paw")`.
- `validateSetupState` addition: with a Lycanthrope in play, exactly one
  `("lycanthrope","Faux Paw")` token must exist and it must sit on a **good** seat.
  Issue text: `"Lycanthrope: mark exactly one good player with Faux Paw"`.

**Registration model (engine change, benefits several characters)**

- Introduce a single registration resolver, e.g.
  `StatusEffects.registersAsEvil(state, lookup, player): Boolean` =
  `player.isEvil(lookup)` **OR** the player holds `("lycanthrope","Faux Paw")` **and** a
  Lycanthrope seat exists that is alive and not impaired.
- Route every alignment-counting calculator in `InfoCalc` through it instead of
  `Ctx.isEvil` (`InfoCalc.kt:97`). Keep `Player.isEvil` as the *true* alignment for
  win checks (`WinCheck.kt`) — the Faux Paw player wins with good.
- Every affected result gains a caveat: `"Cara registers as EVIL (Lycanthrope Faux Paw)."`
  and, when the Lycanthrope is dead/impaired, `"The Lycanthrope is dead — Cara registers as
  good again."` so the ST sees the reasoning rather than a bare number.

**Structured night step**

- **when:** other nights only. Wake condition: holder **alive**. (Impaired holders are still
  woken and still choose — they simply have no effect; the step must say so.)
- **targets:** exactly 1. Constraints: **alive**. Self-selection allowed by the text; sort
  the picker in seat order with dead seats disabled.
- **immediate effects, on confirm:**
  - Compute `targetRegistersEvil = registersAsEvil(target)`.
  - If the Lycanthrope is impaired (`StatusEffects.isImpaired`, or is the Drunk/Marionette,
    or holds a "No ability" token): **nothing happens** — no death, no block. Show
    `! The Lycanthrope is POISONED — no one dies and the Demon kills as normal.`
  - Else if `targetRegistersEvil`: nothing happens. Show
    `Bo registers as evil — nothing happens. The Demon kills as normal.`
    (If Bo is good-but-Faux-Paw, say so: `Bo is the Faux Paw — they register as evil.`)
  - Else (target registers good): in one undoable action —
    1. run the protection check against **non-Demon-specific** protections only; if any
       apply, ask before killing (reuse the `SeatSheet.kt:288-311` confirm dialog);
    2. `kill(target, DeathCause.OTHER_NIGHT_DEATH)` with the source attributed to the
       Lycanthrope (add an optional `sourceCharacterId` to `DeathRecord`, `GameState.kt:74-84`);
    3. place `PlacedReminder("lycanthrope","Dead")` on the victim;
    4. place a **night-scoped** block token, `PlacedReminder("lycanthrope","No demon kill")`,
       on the Lycanthrope's own seat (or as a state flag).
- **deferred effects:** the block token is consumed by every Demon step tonight.
  - `NightOrder.build` (`NightOrder.kt:130-181`) already appends Demon-specific text for the
    Exorcist (`:155-160`) and the Lunatic (`:161-180`). Add the same for the block:
    `" — LYCANTHROPE killed a good player: the Demon does NOT kill tonight."`
  - `DemonKillPanel` (`NightScreen.kt:534-638`) must, when the flag is set, **disable** the
    "<name> dies" button, keep the target chips live (the Demon still chooses and the ST
    still records the choice for Lunatic/mirroring/Ravenkeeper reasoning), and show a red
    banner. The Fang Gu jump, Imp star-pass, Al-Hadikhia sequence and Po's no-kill night
    remain available — only the death is suppressed.
  - **Deferred kills**: the block must be checked at the moment a Demon-sourced death is
    applied, not only at the Demon's step. Concretely: the Pukka's "poisoned last night →
    dies tonight" resolution (whatever form it takes after the Pukka fix) must consult the
    same flag.
- **expiry:** add `"lycanthrope" to "No demon kill"` to `EXPIRES_AT_DAWN`
  (`GameActions.kt:218-225`). `("lycanthrope","Dead")` and `("lycanthrope","Faux Paw")`
  expire **never**.
- **information:** the Lycanthrope learns nothing. State that explicitly so the ST does not
  signal success or failure back to them.
- **visibility:** the Demon is told nothing. The Demon must not learn their kill failed.
- **day-time inputs:** the dawn briefing must list the Lycanthrope's victim as a night death
  and, if the Demon's kill was blocked, must **not** list a second death. Record the
  Lycanthrope's public claim (they very often claim) alongside the other claim recorders.
- **interactions/jinxes:**
  - **Gambler** — if the Lycanthrope is alive and the Gambler self-kills at night, set a
    stronger flag: *no one else can die tonight*. Model it as
    `("lycanthrope","No deaths")` in `EXPIRES_AT_DAWN`, checked by every kill path.
  - **Faux Paw + Recluse/Spy** — a Recluse marked Faux Paw is redundant but legal; a Spy
    cannot be marked (must be a good player).
  - **Vortox** — the Lycanthrope gives no information, so the Vortox does not alter it; but
    the Faux Paw registration still skews everyone else's info and the Vortox already
    requires that info to be false. Surface both caveats together rather than one.
  - **Exorcist** — an Exorcised Demon does not act; the Lycanthrope's block is then moot but
    harmless. Ensure the two banners do not contradict each other.

**UI text**

- Step (idle): `Wake the Lycanthrope (Dan). Who did they choose? (alive players only)`
- Good target: `Bo is good — Bo dies. The Demon does NOT kill tonight.` → button `Bo dies & block the Demon`
- Evil / Faux Paw target: `Bo registers as evil — nothing happens; the Demon kills as normal.`
- Impaired: `! Dan is POISONED (Poisoner) — nothing happens tonight and the Demon kills as normal.`
- Demon step banner: `! Lycanthrope killed Bo — the Demon cannot kill tonight. Record the Demon's choice, then "No kill".`

**Data changes**

- `night_guide.json:1090-1095` — add the Pukka clarification verbatim ("a Demon's deferred
  kill also fails"), the "alive player" constraint, and the note that Soldier/Monk do **not**
  protect against the Lycanthrope.
- `night_and_jinxes.json` — no change needed (Gambler jinx already present, position 33 is
  correct).
- `characters.json` — no change.

## Tests to add

1. `GIVEN` a sober Lycanthrope choosing a good player on cycle 2 `WHEN` the resolution runs
   `THEN` the target is dead with cause `OTHER_NIGHT_DEATH`, holds `("lycanthrope","Dead")`,
   and a `("lycanthrope","No demon kill")` token exists. *Fails today.*
2. `GIVEN` that state `WHEN` a Demon kill is attempted `THEN` it is refused / the panel
   reports the block. *Fails today.*
3. `GIVEN` that state `WHEN` `advancePhase` runs to DAY `THEN` `("lycanthrope","No demon kill")`
   is gone and `("lycanthrope","Dead")` remains.
4. `GIVEN` a Lycanthrope choosing the Faux Paw player `THEN` no one dies and no block token
   is placed.
5. `GIVEN` a Lycanthrope choosing an evil player `THEN` no one dies and no block token.
6. `GIVEN` a **poisoned** Lycanthrope choosing a good player `THEN` no one dies, no block
   token, and the step's caveats include the poison line. *Fails today* (no caveats at all).
7. `GIVEN` a Faux Paw token on the Empath's alive neighbour and a living sober Lycanthrope
   `WHEN` the Empath's info is computed `THEN` the count is 1, with a caveat naming the
   Faux Paw. *Fails today.*
8. `GIVEN` the same state but the Lycanthrope is dead `THEN` the Empath's count is 0.
9. `GIVEN` a Lycanthrope in play and no `("lycanthrope","Faux Paw")` token `WHEN`
   `validateSetupState` runs `THEN` it reports a Lycanthrope issue. *Fails today.*
10. `GIVEN` a Faux Paw token placed on an evil player `THEN` `validateSetupState` reports it.
11. `GIVEN` a Lycanthrope kills the King `THEN` the death's cause is not `DEMON` and the
    Choirboy step is not triggered. *Fails today.*
