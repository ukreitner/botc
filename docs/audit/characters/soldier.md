# Soldier (soldier) — Trouble Brewing Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Soldier> (raw wikitext fetched 2026‑08‑25).

**Current ability text (verbatim):**
> "You are safe from the Demon."

**Summary bullets (verbatim):**
- "The Soldier cannot die from the Demon's ability. So, if the Imp attacks the Soldier at
  night, nothing happens. Nobody dies. **The Imp does not get to choose another player to
  attack instead.**"
- "The Soldier can still die by execution, even if the nominator was the Demon. The Soldier is
  protected from the Demon's ability to kill, **not the actions of the Demon player**."

**How to Run (verbatim):**
> "During the night, if the Demon attacks the Soldier, the Soldier remains alive. (At dawn,
> declare that no one died at night.)
>
> In other editions, Demons may have abilities other than killing. The Soldier is also
> protected from **all other harmful effects of the Demon's ability**, such as poisoning or
> turning the Soldier evil."

**Examples (verbatim):**
- "The Imp attacks the Soldier. The Soldier does not die, so nobody dies that night."
- "The Poisoner poisons the Soldier, then the Imp attacks the Soldier. The Soldier dies, since
  they have no ability."
- "The Imp attacks the Soldier. The Soldier dies, because they are actually the Drunk."

**Storyteller-relevant consequences:**
- Protection is unconditional and standing — no wake, no choice, no token.
- It is **negated** by drunkenness/poisoning, and by the seat actually being the Drunk.
- It covers *every* harmful effect of a Demon's ability, not just death: **No Dashii's
  neighbour poisoning, Pukka's poison, Vigormortis, Fang Gu's turn-evil, Po/Shabaloth/
  Al‑Hadikhia effects, Lord of Typhon, Yaggababble** — a Soldier neighbouring a No Dashii is
  **not poisoned**.
- It does **not** cover: execution (even a Demon-led one), Minion kills (Assassin, Godfather,
  Gossip's kill, Witch, Devil's Advocate does not apply), the Imp's star-pass consequences on
  other seats, exile, or storyteller deaths.
- Because the ST "declares that no one died at night", the Soldier's protection is invisible
  to everyone including the Soldier ("You'll never confirm your ability worked — could be Monk
  protection instead").

**Jinxes (verbatim from the wiki jinx table):**
- Leviathan: "If the Leviathan nominates and executes the Soldier, good wins."
- Riot: "If Riot nominates and executes the Soldier, good wins."

## What the app does today

Data
- `engine/src/main/resources/botc/data/characters.json:123-134` — ability text matches the
  wiki. `reminders: []`, both night reminders empty, `setup: false`. Correct.
- `night_and_jinxes.json` — `soldier` is absent from both night order lists (correct) and has
  **zero jinx entries** (the file holds 58 jinxes total; the Soldier↔Leviathan and
  Soldier↔Riot jinxes are missing).
- `night_guide.json` — no `soldier` entry (correct; the Soldier never wakes).

Engine
- `engine/src/main/kotlin/com/clocktower/engine/StatusEffects.kt:74`
  ```kotlin
  if (id == "soldier") notes += "The Soldier is safe from the Demon."
  ```
  This is the **entire** implementation. It is a string in the `deathNotes` list. It is added
  unconditionally: no impairment check, no death-cause check, no alive check.
- `StatusEffects.derivedPoison` (`StatusEffects.kt:14-33`) poisons the No Dashii's nearest
  Townsfolk neighbour in each direction with **no Soldier exemption**.

UI — the two places a Soldier death can be recorded
1. **Night demon kill** — `NightScreen.kt:534-638` (`DemonKillPanel`). Picking the Soldier
   renders the death notes as red text at `:586-590`, then offers
   `FilledTonalButton(enabled = target.alive) { "<Target> dies" }` at `:623-636`.
   **The button is enabled and kills.** The panel *warns*; it does not block, does not default
   to "No kill", and does not present "the Soldier survives — nobody dies tonight" as the
   primary action. Answering the audit question directly: **it warns only.**
2. **Seat sheet** — `SeatSheet.kt:253-307`. Here `protectionNotes` filters `deathNotes` for the
   substrings `"can't die" / "can not die" / "Safe" / "Protected" / "survives" / "safe from" /
   "don't" / "Fool"` (`:258-262`); `"safe from"` matches the Soldier note, so `requestKill`
   routes through a confirmation dialog titled "<Name> might be protected" with
   **"They die anyway"** / **"Death prevented"** (`:288-307`). This is the better of the two
   paths — but it is the path a ST is *less* likely to use during the demon step.

Dawn
- `GameShell.kt:126-168` — `advancePhase()` has no dawn briefing at all. Nothing prompts
  "announce that no one died tonight", which is the one thing the How to Run asks for.

Works: the character data; the seat-sheet protection confirmation dialog; the fact that the
Soldier correctly never appears on the night sheet.

## Defects and gaps

1. **P0 · The Soldier note ignores impairment** — `StatusEffects.kt:74` adds
   "The Soldier is safe from the Demon." even when the Soldier is poisoned, drunk, or *is* the
   Drunk. The wiki's second example is exactly this case, and it is the single most common
   Poisoner line in TB. The ST is shown a protection warning at the precise moment the Soldier
   must die.
   *Repro:* Poisoner poisons the Soldier on night 2 → Imp step → pick the Soldier → the red
   line still reads "The Soldier is safe from the Demon."
   (Note the neighbouring `"fool"` branch at `StatusEffects.kt:75` *does* check its spent
   token, and `WinCheck.kt:56-61` *does* consult `abilityImpairedAtDeath` — the pattern exists,
   it just was not applied here.)

2. **P0 · The Soldier note ignores the death cause** — `deathNotes` is cause-agnostic
   (`StatusEffects.kt:52-56` takes only a `playerId`). Tapping **"Executed"** on a Soldier in
   `SeatSheet.kt:274-276` raises "…might be protected: The Soldier is safe from the Demon",
   inviting the ST to answer "Death prevented" and wrongly save an executed Soldier. Same for
   an Assassin/Godfather kill, which the ST records as "Died at night" (`DeathCause.DEMON`).

3. **P0 · A Soldier neighbouring the No Dashii is wrongly poisoned** —
   `StatusEffects.derivedPoison` (`StatusEffects.kt:14-33`) marks the No Dashii's nearest
   Townsfolk neighbours poisoned. The No Dashii's poison **is the Demon's ability**, so the
   Soldier is safe from it. Because `derivedPoison` feeds `isImpaired` (`:45`) and
   `InfoCalc.impairments` (`:151`), the app then tells the ST to give that Soldier false info
   for *every other ability they have* — a cascading wrong outcome.
   *Repro:* SnV/mixed script, seat the Soldier next to the No Dashii → the Soldier shows as
   poisoned everywhere.

4. **P1 · The night kill panel does not offer the correct outcome** —
   `NightScreen.kt:623-636` offers "<Target> dies" (enabled) and a plain "No kill" that merely
   clears the selection. For a Soldier the correct resolution is a distinct, recorded event:
   *the Demon attacked, the attack failed, nobody dies tonight, and the Demon does not choose
   again*. None of that is expressible.

5. **P1 · The Demon's failed attack is not recorded** — nothing in `GameState` remembers that
   the Demon chose the Soldier tonight. That record is needed for: the ST's own memory at
   dawn, the Mathematician (`InfoCalc.kt:77-80` currently just says "Track malfunctions
   manually"), the Chambermaid's "who woke tonight" count, and post-game review in the log
   (`GameExtras.kt:44-106`, which only lists deaths and nominations).

6. **P1 · No dawn briefing** — the How to Run's only ST instruction is "At dawn, declare that
   no one died at night." The app never prompts the dawn announcement for any character.
   A Soldier-protected night is the case where a ST most easily forgets to say anything.

7. **P1 · The Soldier↔Leviathan and Soldier↔Riot jinxes are missing from
   `night_and_jinxes.json`** — so `GameData.activeJinxes` cannot surface them in
   `SeatSheet.kt:222-234` or `GameExtras.ActiveJinxesDialog` (`GameExtras.kt:202-231`).
   These jinxes are game-**ending** ("good wins"), so silently omitting them can lose a game.

8. **P2 · "Safe from the Demon" is not modelled as a status, only as prose** — `deathNotes`
   returns `List<String>`, and `SeatSheet.kt:258-262` re-derives protection by
   **substring-matching English text**. Any wording change breaks the guard; nothing else in
   the engine (e.g. a Pukka/No Dashii poison step, a Fang Gu turn-evil step) can consult it.

9. **P2 · Non-lethal Demon effects have no protection hook at all** — the wiki explicitly
   extends the Soldier to poisoning and alignment changes by the Demon. There is no code path
   where a Demon's poison/turn is applied, so there is nowhere for the Soldier to intervene
   (the Pukka defect the user reported is the same missing seam).

10. **P3 · The Soldier is a favourite Demon bluff** — `GameActions.suggestBluffs`
    (`GameActions.kt:121-127`) picks 2 townsfolk + 1 outsider at random; the wiki's
    ST tip ("Soldier bluff is easy since they provide no information or actions") suggests
    weighting no-night-action Townsfolk higher for bluffs. Minor.

## Proposed behaviour (spec)

The Soldier is the model case for turning `deathNotes` prose into a **structured protection
system**. Spec it that way so Monk, Innkeeper, Sailor, Tea Lady, Devil's Advocate, Fool,
Lleech, Mayor and Zombuul all share it.

**Structured behaviour**

- **when:** never wakes. No night step on any night. No day action.
- **targets / immediate effects:** none.
- **standing effect (new engine concept):** introduce
  ```kotlin
  data class Protection(
      val sourceId: String,          // "soldier"
      val label: String,             // "Safe from the Demon"
      val blocks: Set<DeathCause>,   // setOf(DeathCause.DEMON)
      val blocksNonLethal: Boolean,  // true: also blocks Demon poison / turn-evil
      val active: Boolean,           // false when impaired
      val reason: String,            // UI text
  )
  ```
  and `StatusEffects.protections(state, lookup, playerId): List<Protection>`, replacing the
  string list in the two consumers.
  For the Soldier: `blocks = {DEMON}` (plus a new `DeathCause.SLAIN`? **no** — a slay is not
  the Demon's ability, but a Slayer never targets a Soldier meaningfully), `blocksNonLethal =
  true`, `active = !StatusEffects.isImpaired(state, lookup, player) && characterId ==
  "soldier"`.
- **impairment:** when `isImpaired` is true — poisoned, drunk-token, `characterId == "drunk"`,
  or derived poison — `active = false` and the UI must say so loudly:
  `"<Name> is the Soldier but is POISONED — they die."`
- **cause-awareness:** the kill flow must pass the intended `DeathCause` into the protection
  check. `SeatSheet.requestKill` (`SeatSheet.kt:266-268`) already has it; it just discards it.
  Execution, exile, Minion kills and storyteller deaths must produce **no** Soldier warning.
- **deferred effects:** on a blocked Demon attack, record it (see below) and set a dawn
  briefing line. The Demon does **not** get to re-choose.
- **expiry:** none — the protection is permanent while the ability functions. No tokens, so
  nothing to add to `EXPIRES_AT_DAWN` / `EXPIRES_AT_DUSK` (`GameActions.kt:218-242`).
- **information:** none to the Soldier, ever.
- **visibility:** the Demon learns nothing beyond "nobody died", which is public.
- **interactions/jinxes to handle explicitly:**
  - **Poisoner / Drunk** — protection off (defect 1).
  - **No Dashii** — `derivedPoison` must skip a Soldier neighbour entirely (defect 3).
  - **Pukka** — the Pukka's poison is the Demon's ability → a Soldier chosen by the Pukka is
    neither poisoned nor killed; nobody dies from that choice.
  - **Fang Gu** — cannot turn a Soldier (Townsfolk), so moot, but the same `blocksNonLethal`
    hook applies to future Demons.
  - **Vigormortis / Al-Hadikhia / Lord of Typhon / Yaggababble / Po / Shabaloth** — all Demon
    abilities; the Soldier is safe from their harmful effects.
  - **Assassin / Godfather / Witch / Gossip** — Minion or Townsfolk kills; the Soldier dies.
    The app must therefore stop recording these as `DeathCause.DEMON`: add
    `DeathCause.MINION_KILL` (or reuse `OTHER_NIGHT_DEATH`) and offer it from the seat sheet.
  - **Execution** — the Soldier dies, "even if the nominator was the Demon".
  - **Imp star pass** — if the Imp chooses the Soldier it is not a self-kill; the attack simply
    fails.
  - **Leviathan / Riot** — add the two jinxes; when either is in play and a Soldier is
    executed off a Leviathan/Riot nomination, `WinCheck` must return `goodWins = true`.
  - **Exorcist** — an exorcised Demon does not attack at all; unrelated.

**UI text**

- Night kill panel, Soldier picked, protection active:
  - primary action becomes `"Attack fails — nobody dies tonight"` (records the attack, no
    death, and marks the step done);
  - the `"<Name> dies"` button becomes secondary/outlined and reads
    `"Kill anyway (override)"`;
  - red line: `"SOLDIER — safe from the Demon. The Demon does not get to choose again."`
- Protection inactive: `"<Name> is the Soldier but is POISONED/DRUNK — the attack kills."`
  with `"<Name> dies"` as the primary action.
- Dawn briefing line (new): `"Announce: nobody died tonight."` (when zero deaths this night)
  or `"Announce: <names> died."`
- Seat sheet, "Executed" on a Soldier: **no** protection dialog.

**Data changes**

- `night_and_jinxes.json` — add:
  ```json
  {"id1": "leviathan", "id2": "soldier", "reason": "If the Leviathan nominates and executes the Soldier, good wins."},
  {"id1": "riot",      "id2": "soldier", "reason": "If Riot nominates and executes the Soldier, good wins."}
  ```
- `characters.json:123-134` — unchanged.
- `GameState.kt:67` — add `MINION_KILL` to `DeathCause` so Assassin/Godfather deaths stop
  being labelled `DEMON`.
- `GameState.kt` — add `nightActions: List<NightAction>` (`day`, `sourceId`, `actorId`,
  `targetId`, `outcome`) so "the Demon attacked the Soldier and it failed" is recorded; feed
  it to the log and to the Mathematician calc.

## Tests to add

1. **Healthy Soldier blocks the Demon**
   Given an alive, unimpaired Soldier
   When `StatusEffects.protections(state, lookup, soldierId)` is queried for
   `DeathCause.DEMON`
   Then an active protection with `sourceId = "soldier"` is returned.

2. **Poisoned Soldier does not block**
   Given the Soldier holds `PlacedReminder("poisoner", "Poisoned")`
   Then no active Soldier protection is returned, and `deathNotes` must **not** contain
   "The Soldier is safe from the Demon." *(fails today — `StatusEffects.kt:74`)*

3. **Drunk-as-Soldier does not block**
   Given a seat with `characterId = "drunk"`, `shownCharacterId = "soldier"`
   Then no Soldier protection is returned. *(today `deathNotes` keys off `characterId`, so this
   one accidentally passes — assert it explicitly, and assert the converse for a real Soldier
   marked with a generic `"Drunk"` token, which fails today.)*

4. **Execution is not blocked**
   Given an alive, unimpaired Soldier
   When protections are queried for `DeathCause.EXECUTION`
   Then the list is empty. *(fails today — the seat sheet raises the "might be protected"
   dialog, `SeatSheet.kt:258-262`.)*

5. **Minion kill is not blocked**
   Given an alive Soldier and an Assassin kill (`DeathCause.MINION_KILL`)
   Then no protection applies.

6. **No Dashii does not poison a Soldier neighbour**
   Given seats `[nodashii, soldier, chef, …]` with the No Dashii alive
   When `StatusEffects.derivedPoison` runs
   Then `soldierId` is **absent** from the result, and `isImpaired(soldier)` is false.
   *(fails today — `StatusEffects.kt:14-33`.)*

7. **Poisoned Soldier neighbouring a No Dashii still dies**
   Given the Soldier is separately poisoned by the Poisoner
   Then the Demon attack kills them (regression guard on the interaction of 2 and 6).

8. **Blocked attack records no death and no re-choice**
   Given the Imp targets an unimpaired Soldier via the demon resolver
   When the resolver applies "attack fails"
   Then `state.deaths` is unchanged, a `NightAction(outcome = "blocked")` exists, and the
   demon step is marked done.

9. **Soldier↔Leviathan jinx present**
   Given `data.activeJinxes(listOf("leviathan", "soldier"))`
   Then exactly one jinx is returned with the official reason. *(fails today — missing data.)*

10. **Executed Soldier under a Leviathan nomination ends the game**
    Given Leviathan in play, the Leviathan nominated the Soldier, and the Soldier was executed
    When `WinCheck.check` runs
    Then `goodWins = true` with the Leviathan jinx as the reason. *(fails today.)*
