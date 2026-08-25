# King (king) — Experimental (Carousel) Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/King> (fetched 2026-08-25).
Companion: <https://wiki.bloodontheclocktower.com/Choirboy>.
Also checked: <https://wiki.bloodontheclocktower.com/Poppy_Grower>.

**Current ability text (wiki):**
> "Each night, if the dead equal or outnumber the living, you learn 1 alive character. The Demon knows you are the King."

`characters.json:1422` matches exactly — **no drift**.

**How to Run (quoted):**
> "During the first night, wake the Demon. Show them the **THIS PLAYER IS** info token, then the King token, then point at the King player."
>
> "When the number of dead players equals or exceeds the number of alive players, add a night token to the King's entry on the night sheet."
>
> "Each night, if the King has a night token on the night sheet, wake the King. Show one alive character token."

Timing / edge cases:

- **Wake condition is `dead >= alive`** — equal counts already qualify. Below that the King
  does not wake at all. The King is **not** woken on night 1 (the night-1 step is the
  *Demon* being shown the King).
- **The Demon learns the King at the start of the game**, and the wiki adds:
  > "If a King is created mid-game, the Demon learns who the King is that night."
- **The ST chooses which alive character to show.** It may be good or evil, may repeat
  between nights, and may be the King's own character. The King learns a *character*, not
  which player holds it.
- **Example (verbatim):**
  > "Amy is the King. There are 12 players alive, and one dead player. On the second night, she learns nothing. On the third night, she learns nothing. On the fourth day, there are 7 dead players and 6 alive players. On the fourth night, Amy learns that the Snitch is alive. On the fifth night, she learns that the Witch is alive."
- **Jinxes (both quoted from the King page):**
  - Leviathan: "If the Leviathan is in play, and at least 1 player is dead, the King learns an alive character each night."
  - Riot: same wording with Riot.
  - (Data also carries `kazali + choirboy`: "The Kazali can not choose the King to become a Minion." — that is the Choirboy's jinx, correctly filed.)
- **The Poppy Grower does *not* appear on the King page.** The Poppy Grower's own How to
  Run (verbatim) removes *"the Minion Info and Demon Info steps"* only; the King step sits
  after Demon Info in the official order and is not one of them. See defect 9.

**What the Choirboy needs (from the Choirboy page):**
> "If the Demon kills the King, you learn which player is the Demon. [+the King]"
> "Each night except the first, if the Demon kills the King, put the Demon to sleep then wake the Choirboy. Point to the Demon player then put the Choirboy to sleep."

- The Choirboy learns the **player**, not the character.
- It does **not** trigger on: an attack that fails to kill; the King being executed; the
  King being killed by a Minion (e.g. Assassin) or by anything that is not the Demon's kill.
- A dead or poisoned Choirboy does not wake; a drunk Choirboy is pointed at the wrong player.
- **Uncertain / not stated on the wiki:** whether Travellers count towards "dead" and
  "living" for the King. Travellers are players, so the plain reading counts them; the app
  should at least show the ST the breakdown rather than hiding the arithmetic.

## What the app does today

- `characters.json:1422-1433`: `setup: false`, no reminders, first-night reminder =
  "Wake the Demon: Show the 'This player is' & King tokens, then point to the King.",
  other-night reminder = "If the dead equal or outnumber the living, show the character
  token of a living player."
- Night order: `night_and_jinxes.json:314` (first night, index 19 — right after Demon Info)
  and `:458` (other nights, index 85). Both match the official sheet.
- `night_guide.json:1060-1083`: good prose for both nights, plus two show cards
  (`THIS PLAYER IS` + self token for night 1; `THIS CHARACTER IS ALIVE` + pick token for
  later nights).
- `InfoCalc.supports("king")` is true (`InfoCalc.kt:32`), dispatched at `InfoCalc.kt:67`,
  implemented at `InfoCalc.kt:397-405`:
  ```kotlin
  val alive = ctx.players.count { it.alive }
  val dead = ctx.players.size - alive
  if (dead < alive) return InfoResult("Dead ($dead) don't outnumber living ($alive) — the King doesn't wake")
  ```
  The `dead < alive` guard is **correct** for `dead >= alive` — this is the one thing the
  brief asked to verify and it holds. Its message text ("don't outnumber") is loose but the
  arithmetic is right.
- `Setup.kt:77` `COMPANIONS["choirboy"] = "king"` with no distribution delta — correct,
  since the King is a Townsfolk replacing a Townsfolk.
- `StatusEffects.kt:102`: killing a King surfaces the note
  "Choirboy (if in play) learns the Demon when the King dies to it." — the only Choirboy
  support in the app. It renders in `SeatSheet.kt:240-250` and in `DemonKillPanel`
  (`NightScreen.kt:592-594`).
- `choirboy` is at `night_and_jinxes.json` other-night index 64, with prose at
  `night_guide.json:899`. `InfoCalc.supports("choirboy")` is **false**.

Storyteller experience: on every night from 2 on, the King row shows the conditional
sentence; the ST must expand it to find out whether the condition is met, then use the
"Alive character" card's search field to find a character to show. Nothing tracks that the
King has started waking. When the King dies to the Demon, one red line in the seat sheet
reminds the ST that a Choirboy might exist; the Choirboy's own step, later that same night,
does not know it fired.

## Defects and gaps

1. **P1 · The first-night King step displays a contradictory headline.**
   On night 1 the step is *the Demon learning the King*, but `InfoCalc.compute` is still
   called with `"king"` (`NightScreen.kt:835-847`), so the panel prints in bold gold:
   `Dead (0) don't outnumber living (12) — the King doesn't wake`. An ST skimming the
   headline can reasonably conclude there is nothing to do and skip telling the Demon.
   `InfoCalc.kt:397-400` is not night-aware.
   *Repro:* Night 1 → expand the King step.

2. **P1 · The wake condition is computed only when the row is expanded.**
   `NightOrder.build` (`NightOrder.kt:130-181`) emits the same conditional sentence every
   night. The row should say, in its subtitle, `Wakes tonight — 7 dead vs 6 alive` or
   `Does not wake — 1 dead vs 11 alive`, and the "does not wake" case should auto-tick so
   the Dawn checklist guard (`GameShell.kt:145-158`) does not demand attention for a
   non-event.

3. **P1 · The Leviathan and Riot jinxes are missing from the data and from the condition.**
   No `king`+`leviathan` or `king`+`riot` entry exists in `night_and_jinxes.json` (the only
   scope-adjacent jinx there is `kazali`+`choirboy`). `InfoCalc.king` (`InfoCalc.kt:399`)
   therefore reports "doesn't wake" in a Leviathan game with 1 dead and 9 alive, when the
   King should be learning a character every night.
   *Repro:* Leviathan + King, kill one player, go to night 3 → the app says the King sleeps.

4. **P1 · A King created mid-game never triggers the Demon reveal.**
   Nothing watches for a seat becoming the King (Pit-Hag, Amnesiac, a Huntsman turning the
   Damsel into the King, a Barber/Snake Charmer swap). The wiki requires the Demon to learn
   the new King *that night*. `GameActions.assignCharacter` (`GameActions.kt:46-53`) and
   `swapCharacters` (`GameActions.kt:99-115`) fire no hooks.

5. **P1 · The Choirboy trigger is prose, not logic.**
   The app holds everything needed — `DeathRecord(cause = DEMON, characterIdAtDeath = "king",
   day = cycle, atNight = true)` (`GameState.kt:74-84`, written by `GameActions.kill`
   `GameActions.kt:136-156`) — yet the Choirboy step (other-night index 64) is a static row
   and `InfoCalc` has no `"choirboy"` branch (`InfoCalc.kt:30-35`). The ST must remember,
   across the whole demon phase, that the King they killed twenty steps ago now needs the
   Choirboy woken.
   *Repro:* Night 3, Demon kills the King via `DemonKillPanel`; scroll to the Choirboy row —
   it still reads "Only act if the Demon killed the King tonight" with no answer.

6. **P1 · Night deaths are all recorded as `DeathCause.DEMON`.**
   `SeatSheet.kt:270-272` maps the only night-death button ("Died at night") to
   `DeathCause.DEMON`. So a King killed by a Lycanthrope, Gossip, Godfather, Assassin or
   Vigormortis is indistinguishable from a Demon kill in `state.deaths`. Any automatic
   Choirboy trigger built on `cause == DEMON` would fire wrongly, and the Choirboy is
   explicitly *not* meant to trigger on Minion kills.

7. **P2 · Traveller handling in the dead/alive count is silent.**
   `InfoCalc.kt:398-399` counts `ctx.players` wholesale, including Travellers, while
   `GameState` exposes `aliveNonTravellers` (`GameState.kt:110`) for cases that need the
   other reading. The wiki does not settle it. At minimum the detail line must show the
   breakdown ("7 dead / 6 alive, including 1 Traveller") so the ST can make the call.

8. **P2 · `StatusEffects.kt:102` fires on every King death, for every cause, whether or not
   a Choirboy exists.** Executing the King prints "Choirboy (if in play) learns the Demon".
   It should be conditional on a living, unpoisoned Choirboy and on a Demon night-kill, and
   should name the Choirboy's seat.

9. **P2 · The night-1 guide's Poppy Grower instruction appears to be wrong.**
   `night_guide.json:1062` says "Skip this step if a Poppy Grower is in play and alive."
   The Poppy Grower's own How to Run only removes "the Minion Info and Demon Info steps",
   and the King page never mentions the Poppy Grower. I could not find wiki support for
   delaying the King reveal. Recommend removing the sentence (or restating it as an
   explicitly-optional ST convention) after confirming with the official Discord/FAQ.

10. **P2 · The "alive character" choice is a search box, not a list of the actual answers.**
    `InfoCalc.king` already computes `In play & alive: <names>` (`InfoCalc.kt:401-404`) but
    that is plain text; showing one takes a separate dialog (`NightScreen.kt:801-833`) whose
    picker is a free search over the whole script. One tap per alive character should
    produce the card.

11. **P2 · The invented info token.** `night_guide.json:1076` shows the text
    `THIS CHARACTER IS ALIVE`. There is no such info token in the game; the ST simply shows
    the character token. Harmless (the text is editable) but it teaches the wrong ritual.

12. **P3 · Message wording.** "Dead (1) don't outnumber living (11)" is technically the
    negation of `>=`, but reads as if strict outnumbering were the rule. Prefer
    "1 dead vs 11 alive — the King needs dead ≥ alive".

## Proposed behaviour (spec)

**Structured night step — first night**

- **when:** first night, unconditionally, if a King is in play. This step is about the
  **Demon**, not the King.
- **targets:** none.
- **immediate effects:** none.
- **information:** show the Demon `THIS PLAYER IS` + the King token and point at the King.
  Provide a one-tap card that also names the seat.
- **visibility:** the Demon (only). If more than one Demon seat exists (Legion/Riot/Kazali),
  show each of them.
- **UI text:** `Wake the Demon (Ali). Show "THIS PLAYER IS" + the King token, then point at Amy.`
  Suppress the InfoCalc headline entirely for cycle 1.

**Structured night step — other nights**

- **when:** other nights; wake condition
  `deadCount >= aliveCount` **OR** (a Leviathan or Riot is in play **and** `deadCount >= 1`).
  Holder must be **alive**. Counting rule: all seats, Travellers included, with the
  breakdown displayed.
- **targets:** none from the player; the **ST** picks one alive player whose character is
  shown.
- **immediate effects:** none. No token is placed.
- **deferred effects:** the first night the condition becomes true, surface a one-line
  day-start / dusk note: *"From tonight the King wakes every night (dead ≥ alive)."* This is
  the app's equivalent of the physical "add a night token to the King's entry".
- **expiry:** nothing.
- **information:** the true answer is "any alive player's character". Render every alive
  player as a chip labelled `<name> — <character>`; tapping one shows
  `ShowCard.CharacterCard("", characterId)` full-screen. Sort: characters not shown on a
  previous night first (track shown-character history so the ST can vary), then seat order.
- **impaired / false alternative:** if the King is drunk, poisoned, the Drunk or the
  Marionette, banner in red and switch the chip list to **every script character**, in-play
  ones deprioritised, so the ST can show a character that is *not* alive. Under a **Vortox**,
  the shown character must be false in the same way — the existing
  "VORTOX in play — Townsfolk info must be FALSE" caveat (`InfoCalc.kt:161-164`) must be
  accompanied by the "show a not-alive character" chip list, not just numbers.
- **misregistration:** none applies — the King learns a character, not an alignment, so the
  Spy/Recluse lines currently produced by other calculators are correctly absent here.
- **visibility:** nothing to any other player.
- **day-time inputs:** record what the King was shown each night (`night N: Snitch`) so the
  ST can avoid accidental repeats and can answer challenges during the day.

**Choirboy coupling (implement here, cross-referenced from `choirboy.md`)**

- Split `DeathCause.DEMON` into a cause that means *the Demon's own kill*
  (`DEMON`) versus *any other night death* (`OTHER_NIGHT_DEATH`, already in the enum,
  `GameState.kt:69`). Change `SeatSheet.kt:270-272` from a single "Died at night" button to
  "Demon kill" / "Other night death"; `DemonKillPanel` already records `DEMON` correctly.
- On the other-night sheet, the Choirboy row's wake condition becomes:
  `state.deaths.any { it.day == cycle && it.atNight && it.cause == DEMON &&
   it.characterIdAtDeath == "king" && !it.resurrected }`, plus a living Choirboy.
  When it is met, the row must read
  `Wake the Choirboy (Bo). Point to Ali — the Demon.` with a red banner if the Choirboy is
  drunk/poisoned instructing the ST to point at a wrong player or not wake at all.
- When the King dies to the Demon, also raise it in the dawn/day-start briefing:
  *"Amy (King) was killed by the Demon — the Choirboy learned who the Demon is."*
- Keep `StatusEffects.kt:102` but gate it on `cause == DEMON` and on an alive Choirboy, and
  name them.

**Data changes**

- `night_and_jinxes.json` — add:
  - `{"id1":"king","id2":"leviathan","reason":"If the Leviathan is in play, and at least 1 player is dead, the King learns an alive character each night."}`
  - `{"id1":"king","id2":"riot","reason":"If Riot is in play, and at least 1 player is dead, the King learns an alive character each night."}`
- `night_guide.json:1062` — delete/soften the Poppy Grower sentence (defect 9); add
  "The King is not woken on the first night" (already present — keep) and
  "The King may learn the same character on different nights."
- `night_guide.json:1076` — change the card text from `THIS CHARACTER IS ALIVE` to an empty
  prefix (just the character token).

## Tests to add

1. `GIVEN` 6 alive and 6 dead players `WHEN` `InfoCalc.compute(..., "king", ...)` runs
   `THEN` the result is the "show 1 alive character" headline (equality qualifies).
   `GIVEN` 7 alive and 6 dead `THEN` the result is the "does not wake" headline.
2. `GIVEN` cycle 1 `WHEN` the King step's info is computed `THEN` the headline describes
   the Demon learning the King, **not** the dead/alive count. *Fails today.*
3. `GIVEN` a Leviathan in play and exactly 1 dead player out of 10 `WHEN` the King's wake
   condition is evaluated on night 4 `THEN` the King wakes. *Fails today.*
4. `GIVEN` a Riot in play and 0 dead players `THEN` the King does not wake.
5. `GIVEN` a Demon kills the King at night on cycle 3 with a living Choirboy `WHEN` the
   other-night sheet is built `THEN` the Choirboy step is marked as triggered and names the
   Demon's seat. *Fails today.*
6. `GIVEN` the King is killed by an Assassin (recorded `OTHER_NIGHT_DEATH`) `THEN` the
   Choirboy step is **not** triggered.
7. `GIVEN` the King is executed `THEN` the Choirboy step is not triggered, and
   `StatusEffects.deathNotes` does not emit the Choirboy line.
8. `GIVEN` a Choirboy in play `THEN` the bag contains a King (`Setup.modifierFor("choirboy")
   .requiredCompanionId == "king"`). *(Already covered by `SetupTest.kt:118` — keep.)*
9. `GIVEN` a poisoned King on a night where dead ≥ alive `THEN` the computed caveats include
   the poison line and the false-info chip list contains characters that are **not** alive.
