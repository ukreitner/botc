# Shabaloth (shabaloth) — Bad Moon Rising Demon

## Official rules (sources)

Sources: <https://wiki.bloodontheclocktower.com/Shabaloth>,
<https://wiki.bloodontheclocktower.com/Exorcist>,
<https://wiki.bloodontheclocktower.com/Night_Order>.

**Current ability text (matches `characters.json:691`):**

> "Each night*, choose 2 players: they die. A dead player you chose last night might be regurgitated."

**How to run (wiki, verbatim):**

> "Each night except the first, wake the Shabaloth. They point at any two players, one at a time.
> Put the Shabaloth to sleep."
> "In the order chosen, each chosen player dies—mark them with **DEAD** reminders."
> "Each later night, just before waking the Shabaloth, you can choose one character marked with
> the Shabaloth's **DEAD** reminder, and the chosen player becomes alive again—replace the
> **DEAD** reminder with the Shabaloth's **ALIVE** reminder, and remove their shroud."
> "They wake later tonight if they normally would. If they wake on the first night only, they
> wake now to use their ability."
> "At dawn, after declaring which players died, declare which player is alive again.
> (Do not say why.)"

Reminder tokens: **DEAD** (×2 — two victims a night), **ALIVE**.

**Timing and edge cases:**

- **Regurgitation is the Storyteller's decision, not the Shabaloth's** — "you can choose". The
  Shabaloth never points at anyone for it, and it happens **before** tonight's two attacks
  ("just before waking the Shabaloth").
- **Eligibility = carries the Shabaloth's DEAD reminder from last night.** Because the Shabaloth
  only has two DEAD tokens, that is exactly last night's two picks. Crucially the wiki example
  shows the Shabaloth deliberately attacking an *already dead* player: "Shabaloth attacks the
  alive Courtier (dies) and the dead Exorcist. The next night the Exorcist is regurgitated but
  does not act, because the Exorcist normally acts before the Shabaloth." **Attacking a corpse is
  the Shabaloth's setup move for a resurrection** — the app must support it.
- **What the resurrected player gets back:** "The regurgitated player regains their ability, even
  a 'once per game' ability already used. If they had a 'first night only' or 'start knowing'
  ability, they may use it again." And: "They wake later tonight if they normally would. If they
  wake on the first night only, they wake now to use their ability."
  → the app must **re-run the first night for that player** when their character has only a
  first-night step, and must slot them back into tonight's remaining order otherwise. If their
  night position is *earlier* than the Shabaloth's (`night_and_jinxes.json:372-467`, Shabaloth at
  index 40), they miss tonight.
- **Order of the two kills matters.** Wiki example: "Shabaloth attacks the Tea Lady's neighbour
  (protected, survives), then the Tea Lady (dies)." Had the Tea Lady been chosen first, the
  neighbour would have lost their protection before the second attack. Resolve sequentially and
  re-derive protection between the two.
- **A dead Shabaloth has no ability** — "Since the Shabaloth cannot regurgitate themself—they
  have no ability when dead—it is best to only rarely make the Shabaloth regurgitate." So a dead
  Shabaloth neither kills nor regurgitates.
- **Storyteller guidance on frequency:** "Once per game, maybe twice, is usually sufficient."
  The app should surface how many regurgitations have happened.
- **Dawn wording:** deaths first, then "and <name> is alive again", with **no explanation**.
- **Choosing the same player twice in one night, or choosing itself:** not addressed on the wiki.
  INFERRED: both legal, both wasteful; the picker should warn, not forbid.
- **Exorcist:** the Exorcist stops the Demon's whole ability and the Demon "doesn't wake tonight",
  so there are no attacks. **UNCERTAIN:** whether the Storyteller may still regurgitate on an
  Exorcised night. The regurgitation is the Shabaloth's ability but does not require the
  Shabaloth to wake. The wiki does not say. Ask the Storyteller rather than deciding silently.
- **Night-order position.** Other nights: after Pukka, before Po (`night_and_jinxes.json:413`) —
  correct. No first-night entry — correct.

**Jinxes.** None published for the Shabaloth; `night_and_jinxes.json` has none — correct.

**Lunatic.** A Lunatic shown the Shabaloth token must be asked for **two** names every night
(marking `lunatic:Attack 1` and `Attack 2`) and should occasionally be told at dawn that one of
their victims is "alive again" so the bluff holds together.

## What the app does today

- **Data.** `characters.json:688-701` — ability text and `otherNightReminder` match the wiki.
  `reminders: ["Dead", "Alive"]` has only **one** `Dead` label for a two-kill Demon (P1-6).
  Night-order position correct. Otherwise works.
- **Night guide.** `night_guide.json:375-380` (`other` only). Structure is right — resurrect
  first, then the two kills — but it contains a **rules error**: "they learn no new information
  and their once-per-game abilities remain as they were." The wiki says the exact opposite:
  the regurgitated player "regains their ability, even a 'once per game' ability already used"
  and first-night/start-knowing abilities "may be used again". See P0-2.
- **The night action is the generic demon kill.** `QuickResolutions` (`NightScreen.kt:462-525`)
  has no `shabaloth` branch; the `else` arm renders `DemonKillPanel`
  (`NightScreen.kt:518-523`, panel at `:534-638`). That panel:
  - asks for **one** target ("Demon kill — who did <name> choose?", `NightScreen.kt:544`),
  - sorts **dead players last** (`compareByDescending { it.alive }`, `NightScreen.kt:559-561`),
  - and **disables the confirm button for dead targets** (`enabled = target.alive`,
    `NightScreen.kt:626`). So the Shabaloth's signature move — attacking a corpse to set up a
    regurgitation — is literally unavailable in the UI.
- **No regurgitation flow at all.** `GameActions.resurrect` exists (`GameActions.kt:173-181`) and
  is wired up for the Professor only (`NightScreen.kt:499-517`). For the Shabaloth the ST must go
  to the seat sheet and press "Resurrect" (`SeatSheet.kt:281`), then hand-place the `Alive` token
  from the tray, then remember to announce it at dawn, then remember to re-run the player's
  first-night info.
- **No memory of "chosen last night."** `shabaloth:Dead` is in neither expiry table
  (`GameActions.kt:218-242`), so tokens the ST places by hand persist indefinitely and drift out
  of sync with "last night".
- **Ordering / protection.** `StatusEffects.deathNotes` fires per pick
  (`NightScreen.kt:588-590`) but the two kills are independent select→confirm cycles with no
  order record and no re-derivation of the Tea Lady's protection between them
  (`StatusEffects.kt:79-91` computes it from live state, so it *would* update — but only because
  the ST happened to confirm the first kill before picking the second).
- **Dawn** never names the dead and has no concept of "alive again" (`NightOrder.kt:59`).
- **First-night re-run for a resurrected player** does not exist anywhere in the app (this is the
  same gap the user reported for the Professor).
- **Impairment banner** (`NightScreen.kt:548-554`) is correct for the Shabaloth: a drunk/poisoned
  Shabaloth kills nobody.

## Defects and gaps

1. **P0 · The Shabaloth cannot attack a dead player.** The confirm is `enabled = target.alive`
   (`NightScreen.kt:626`) and dead seats sort last (`:559-561`). The wiki's own example depends on
   attacking a corpse to enable next night's regurgitation.
   *Repro:* Night 3, someone is dead → Shabaloth row → pick the dead seat → the "dies" button is
   greyed out, and no Shabaloth `Dead` token gets placed.
2. **P0 · The night guide states the opposite of the rule about regurgitated abilities.**
   `night_guide.json:378` says once-per-game abilities "remain as they were" and the player
   "learn[s] no new information"; the wiki says they regain the ability, including a spent
   once-per-game, and re-use first-night/start-knowing abilities. A Storyteller following the app
   will get this wrong every time.
3. **P0 · No regurgitation step.** The rules put a Storyteller decision *before* the Shabaloth
   wakes; the app has no prompt, no eligibility list, no `Alive` token placement, and no dawn
   announcement. `NightScreen.kt:462-525` has no `shabaloth` branch.
4. **P0 · Only one kill is offered per confirm, with no "2 of 2" flow.** The ST must run the panel
   twice with no counter and no ordering guarantee; nothing tells them the order matters
   (Tea Lady example). `NightScreen.kt:534-638`.
5. **P1 · No first-night re-run for the regurgitated player.** The rules require it ("If they wake
   on the first night only, they wake now to use their ability"), and there is no way in the app
   to re-open a single character's first-night step, show card or `InfoCalc` result mid-game.
   This is exactly the failure the user reported for the Professor.
6. **P1 · Only one `Dead` reminder label** (`characters.json:699`) — the tray treats it as
   exclusive and moves it (`NightScreen.kt:319-339`), so the two victims can never both be marked,
   which in turn makes "chosen last night" untrackable.
7. **P1 · No dawn announcement, and the resurrection announcement is the one the rules spell out
   word for word** ("At dawn, after declaring which players died, declare which player is alive
   again. (Do not say why.)"). `NightOrder.kt:59` is a static string.
8. **P1 · A dead Shabaloth still gets a kill panel gated only on `holder.alive`**
   (`NightScreen.kt:520`) — that gate is correct, but the step still appears with the "All holders
   are dead — usually skip" hint (`NightScreen.kt:751-757`) rather than saying "a dead Shabaloth
   has no ability: no kills and no regurgitation."
9. **P2 · Exorcist ambiguity is not surfaced.** The step text says "the Demon does not act
   tonight" (`NightOrder.kt:150-154`) but says nothing about whether the ST may still regurgitate.
10. **P2 · No count of how many regurgitations have happened**, though the wiki explicitly advises
    "once per game, maybe twice".
11. **P2 · Lunatic-as-Shabaloth has no two-target placebo flow** and no way to fake an
    "alive again" announcement.

## Proposed behaviour (spec)

### Engine model

- Tokens:
  - `PlacedReminder("shabaloth", "Dead")` — **two copies**, placed on tonight's two picks
    (whether or not they actually die). Cleared at the start of the Shabaloth's *next* step, so
    they always mean "chosen last night".
  - `PlacedReminder("shabaloth", "Alive")` — placed on the regurgitated player. Persists (it is
    the public record of who came back); never expires.
- Derived: `shabalothLastNightPicks(state)` = players carrying `shabaloth:Dead`.

```kotlin
// GameActions.kt
fun shabalothNight(
    state: GameState,
    shabalothPlayerId: Long,
    regurgitateId: Long?,          // must be in shabalothLastNightPicks and currently dead
    targetIds: List<Long>,         // exactly 2, in the order chosen
    prevented: Set<Long> = emptySet(),
    lookup: (String) -> Character?,
): GameState
```

Order of operations:

1. If `regurgitateId != null`: `resurrect(regurgitateId)` (`GameActions.kt:173-181` — keeps the
   death record with `resurrected = true`, restores the ghost vote), then move that player's
   `shabaloth:Dead` token to `shabaloth:Alive`.
2. Clear **all** remaining `shabaloth:Dead` tokens (last night's marks are now spent).
3. For each `targetIds[i]` **in order**: place `shabaloth:Dead` on them; if the Shabaloth is
   impaired or `id in prevented` or the target is already dead → no kill; else
   `kill(id, DeathCause.DEMON)`. Re-derive protection between the two (Tea Lady, Innkeeper,
   Monk, Sailor) from the *current* state.
4. Record the regurgitation for the dawn report and for a running count.

### Structured night action

- **when:** other nights only.
- **wake condition:** holder alive. A dead Shabaloth does nothing at all (no kills, **no
  regurgitation** — "they have no ability when dead"). If Exorcised: no kills; ask about the
  regurgitation (see UI).
- **targets:**
  - regurgitation: 0 or 1, constrained to players carrying `shabaloth:Dead` **and currently
    dead**; show the ineligible ones greyed with the reason.
  - kills: exactly 2, **ordered**, any player **including the dead** (dead picks are the setup
    move — surface them in a separate "already dead — mark for regurgitation" group, not sorted
    to the bottom). Duplicates warned, not forbidden.
- **immediate effects:** as above.
- **deferred effects:** the two `shabaloth:Dead` marks are the only carry-over, and they define
  tomorrow's regurgitation menu.
- **expiry:** `shabaloth:Dead` — cleared by the Shabaloth's own next step (not by
  `EXPIRES_AT_DAWN`/`EXPIRES_AT_DUSK`, because they must survive the day). `shabaloth:Alive` —
  never expires.
- **information:** none for the Shabaloth; but a regurgitated player may need their info re-run
  (below).
- **visibility:** night 1 demon info via `DEMON_INFO` — works today.
- **day-time inputs:** none of its own.

### Night step UI (replaces `DemonKillPanel` for `shabaloth`)

**Heading:** `Shabaloth — regurgitate first, then two kills in order.`

```
1. Regurgitate?  (Storyteller's choice — "once per game, maybe twice")
   Chosen last night:  [ <A> † eligible ]  [ <B> — survived, not eligible ]
   [ No regurgitation ]   [ <A> is alive again ]
   Note when confirmed:
     • "<A> gets their ability back — including a once-per-game ability they already used."
     • if <A>'s night position is AFTER the Shabaloth: "<A> wakes later tonight as normal."
     • if BEFORE, or first-night-only / start-knowing:
         [ Run <A>'s first-night step now ]  ← opens their first-night guide + InfoCalc + shows
     • "Announce at dawn: '<A> is alive again.' Do not say why."

2. Attack 1 of 2:  [ seat chips — Alive | Already dead (mark for regurgitation) ]
   ! <deathNotes>          [ <Name> dies ]  [ Protected — no death ]  [ Already dead — just mark ]
3. Attack 2 of 2:  ... (re-render protection notes from current state)

[ Finish ]
```

- If the Shabaloth is impaired: `! The Shabaloth is drunk/poisoned — nobody dies tonight and
  nobody is regurgitated. Still mark the two players they pointed at.` (INFERRED: the DEAD marks
  are bookkeeping, so keep placing them; flag this to the ST.)
- If Exorcised: `Exorcist chose the Shabaloth — no attacks tonight.` plus
  `The wiki does not say whether you may still regurgitate on an Exorcised night. Your call:`
  `[ Regurgitate anyway ] [ Skip ]`.
- If the Shabaloth is dead: `The Shabaloth is dead — no attacks and no regurgitation.`
- Footer chip: `Regurgitations so far: N` (the wiki advises 1, at most 2).

### First-night re-run (shared machinery, also needed by the Professor and the Pit-Hag)

Add a `NightScreen` entry point: **"Re-run first night for <player>"**. It opens the same
`StepDetailPanel` content (`NightScreen.kt:770-934`) but with `isFirstNight = true` for that one
character: `NightGuide.forStep(id, true)`, first-night show cards, and
`InfoCalc.compute(...)` evaluated against the *current* grimoire. Surface it:

- inside the Shabaloth's regurgitation block, for the revived player;
- inside the Professor's resolution block (`NightScreen.kt:499-517`);
- from the day-start briefing the morning after any resurrection
  (`"<A> is alive again — re-run their first-night info"`).

### Deferred / dawn / day output

- **Dawn (`DawnReport`), in this exact order:**
  1. `"<X> and <Y> died in the night."` (or "Nobody died in the night.")
  2. `"<A> is alive again."` — with an ST-only note `Do not say why.`
- **Day briefing:** `"<A> was regurgitated — they have their ability back, including a spent
  once-per-game ability. Re-run their first-night info if you haven't."`
- **Grimoire:** the `shabaloth:Alive` token should render distinctly (it is a permanent record,
  unlike the nightly `Dead` marks).

### Lunatic pretending to be a Shabaloth

- Two-target placebo picker, marking `lunatic:Attack 1` and `lunatic:Attack 2` (both already
  expire at dawn, `GameActions.kt:222-224`).
- Offer the ST a "fake regurgitation" prompt so the dawn announcement can match the Lunatic's
  expectations when the Storyteller wants the bluff to hold.
- Hand-off block: `Wake the real Demon (<name>). Show the Lunatic token, point at <Lunatic>, then
  point at <A> and <B>.` (`NightOrder.kt:157-172` already appends the choices to the Demon's step
  text — keep it and add the pair.)

### Data changes

- `characters.json:699` — `reminders: ["Dead", "Dead", "Alive"]` (two victims a night).
- `night_guide.json:375-380` — **fix the rules error** and rewrite `other` as:
  "First, you may regurgitate: choose one dead player carrying the Shabaloth's Dead reminder from
  last night. Replace it with the Alive reminder and remove their shroud. They regain their
  ability — including a once-per-game ability they already used — and if their ability is
  'first night only' or 'you start knowing', run it for them now; otherwise they wake later
  tonight if their turn has not passed. Then wake the Shabaloth: they point at two players, one
  at a time, and both die in that order — resolve the first before the second, because
  protection can change in between. The Shabaloth may point at a dead player to mark them for
  tomorrow's regurgitation. At dawn, announce the deaths, then announce who is alive again, and
  do not say why. A dead Shabaloth has no ability: no kills, no regurgitation."
- `night_and_jinxes.json` — no changes.

## Tests to add

1. `shabaloth kills two players in the chosen order`
   Given a sober Shabaloth, cycle 3.
   When `shabalothNight(targets = listOf(2, 3))`.
   Then seats 2 and 3 are dead and `state.deaths` records them in that order, and both carry
   `shabaloth:Dead`.
2. `shabaloth may mark an already dead player`
   Given seat 4 is already dead.
   When `shabalothNight(targets = listOf(2, 4))`.
   Then seat 4 carries `shabaloth:Dead`, `state.deaths` gains exactly one new record (seat 2),
   and seat 4's original death record is untouched.
3. `regurgitation restores life and swaps the token`
   Given seat 4 is dead and carries `shabaloth:Dead`, cycle 4.
   When `shabalothNight(regurgitateId = 4, targets = listOf(2, 3))`.
   Then seat 4 is alive, carries `shabaloth:Alive` and not `shabaloth:Dead`, its death record has
   `resurrected = true`, and `ghostVoteUsed == false`.
4. `only last night's picks are eligible for regurgitation`
   Given seat 4 died two nights ago with no `shabaloth:Dead` token.
   Then the eligibility list from `shabalothLastNightPicks` excludes seat 4.
5. `previous Dead marks are cleared each Shabaloth night`
   Given seats 2 and 3 carry `shabaloth:Dead`.
   When `shabalothNight(targets = listOf(4, 5))`.
   Then only seats 4 and 5 carry `shabaloth:Dead`.
6. `regurgitation clears a spent once-per-game mark`
   Given seat 4 is a dead Professor carrying `professor:No ability` and `shabaloth:Dead`.
   When regurgitated.
   Then seat 4 no longer carries `professor:No ability`. (Wiki: "regains their ability, even a
   'once per game' ability already used.")
7. `tea lady protection is re-derived between the two kills`
   Given a Tea Lady on seat 3 with good neighbours on seats 2 and 4.
   When `shabalothNight(targets = listOf(3, 2))` (Tea Lady first).
   Then seat 3 dies and seat 2 also dies (its protection is gone once the Tea Lady is dead).
   And the reverse order `listOf(2, 3)` leaves seat 2 alive.
8. `impaired shabaloth kills no-one but still marks`
   Given the Shabaloth holds `poisoner:Poisoned`.
   When `shabalothNight(targets = listOf(2, 3))`.
   Then seats 2 and 3 are alive and both carry `shabaloth:Dead`.
9. `dead shabaloth produces no step action`
   Given the Shabaloth is dead. Then the Shabaloth night action reports "no ability" and
   `shabalothNight` is a no-op.
10. `dawn report announces deaths then the revival`
    Given seats 2 and 3 died tonight and seat 4 was regurgitated.
    Then `DawnReport.build(state)` yields the deaths first, then `"<seat 4> is alive again."`
11. `shabaloth Alive token survives dawn and dusk` — `advancePhase` NIGHT→DAY→NIGHT keeps it.
