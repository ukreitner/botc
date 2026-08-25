# Barber (barber) — Sects & Violets Outsider

## Official rules (sources)

Source: https://wiki.bloodontheclocktower.com/Barber (Character Text, How to Run,
Examples, Tips & Tricks) and https://wiki.bloodontheclocktower.com/Abilities
(the canonical "becomes a new character" rules).

Current ability text (verbatim):

> "If you died today or tonight, the Demon may choose 2 players (not another
> Demon) to swap characters."

How to Run (wiki, condensed to steps):

1. **The moment the Barber dies** (execution, Demon kill, Witch curse, Slayer,
   any cause), mark them with the **HAIRCUTS TONIGHT** reminder.
2. That night — the Barber's slot is *after* every Demon attack — wake the
   **Demon**. Show the "This character selected you" card, then the Barber token.
3. The Demon either **shakes their head "no"** (declines) or **points to two
   players**.
4. If two players were chosen: **swap their character tokens** in the grimoire.
   Then wake **each swapped player individually** and show them the "You are"
   card and their new character token. "If alignment doesn't match token colour,
   flip the token upside-down" (i.e. the token moves, the alignment does not).
5. **Remove the HAIRCUTS TONIGHT reminder** after the Demon has chosen.

Rules and edge cases from the wiki:

- **Alignments never change.** A good player who receives a Minion token stays
  good; an evil player who receives a Townsfolk token stays evil.
- **The Demon may decline** ("shakes their head").
- **"The Demon may choose themself to swap."**
- **The Demon may not swap with another Demon** — neither of the two chosen
  players may be a Demon other than the acting Demon.
- **Dead players may be chosen.** Wiki example: "Barber swaps with a dead player
  (alive and dead character positions exchange)"; "Vigormortis swaps with dead
  Sweetheart; the old Demon becomes evil Sweetheart."
- **"If a player dies then becomes the Barber, the Demon may not swap two
  players' characters tonight."** The trigger requires having *been* the Barber
  at the moment of death.
- Character-change consequences (Abilities page, verbatim):
  - "If a player becomes a new character, they gain the new character's ability
    immediately."
  - "They lose their old ability immediately and **any of its persistent effects
    end**."
  - "If the new ability is a 'once per game' ability and has already been used,
    they may use it again."
  - **"If the new ability normally only functions on the first night, it
    functions tonight."**
  - "If a player becomes a Minion or Demon character, they do not learn who the
    other evil players are."
- **Night position:** other nights only, official slot between the Hatter and
  the Sweetheart — i.e. *after* every Demon kill, *after* Godfather/Gossip, but
  *before* Sage, Professor, Ravenkeeper, Undertaker, Empath, Fortune Teller,
  Dreamer and every other info role. Consequence: a player swapped into an info
  role **wakes again later the same night** for that role's information.
- **Jinxes:** none listed for the Barber.
- **Uncertain / not stated on the wiki:** the page says nothing explicit about a
  drunk/poisoned Barber or about there being no living Demon. The general rules
  apply (a drunk/poisoned Barber has no ability, so no swap happens; with no
  living Demon nobody wakes) — flagged rather than asserted.

## What the app does today

Data:
- `engine/src/main/resources/botc/data/characters.json:960-972` — ability text
  matches the wiki; `otherNightReminder` is a faithful transcription of the
  official night sheet; `reminders: ["Haircuts tonight"]`.
- `engine/src/main/resources/botc/data/night_guide.json:599-617` — a good
  `other` prose entry plus two prepared show cards ("Barber"/self token,
  "New character"/pick token).
- `engine/src/main/resources/botc/data/night_and_jinxes.json:432` — `barber`
  sits at other-night index 59, correctly after all Demons.

Engine:
- `engine/src/main/kotlin/com/clocktower/engine/StatusEffects.kt:100` — the only
  automation: `deathNotes` adds the string "Barber: the Demon may swap two
  players' characters tonight." No token, no state.
- `engine/src/main/kotlin/com/clocktower/engine/GameActions.kt:98-115` —
  `swapCharacters(state, id1, id2)` swaps `characterId` **and**
  `shownCharacterId`; it does not touch `alignmentFlipped` (correct), reminders,
  or death records.
- `engine/src/main/kotlin/com/clocktower/engine/GameActions.kt:218-225` —
  `EXPIRES_AT_DAWN` does **not** contain `("barber","Haircuts tonight")`.
- `engine/src/main/kotlin/com/clocktower/engine/NightOrder.kt:142-178` — the
  Barber row is emitted whenever any seat holds `barber`, with no condition on
  whether the Barber died.

UI:
- `app/src/main/java/com/clocktower/grimoire/ui/screens/NightScreen.kt:470-524` —
  `QuickResolutions` has no `"barber"` branch, so the step offers no tool at all.
- `app/.../NightScreen.kt:751-757` — because all Barber holders are dead exactly
  when the step matters, the row prints **"All holders are dead — usually skip."**
- `app/.../SeatSheet.kt:118-141` — "Swap characters" mode: a flat `TextButton`
  list of every other seat, no Demon exclusion, no dead/alive marking, no
  follow-up.
- `app/.../SeatSheet.kt:240-251` — the Barber death note is shown, but only in
  the seat sheet.
- `app/.../DayScreen.kt:111-114` and `:350-357`, and
  `app/.../GameShell.kt:599-604` — executions triggered from the Day tab or from
  the dusk guard call `viewModel.kill(...)` directly and never surface
  `deathNotes`, so **executing the Barber produces no warning whatsoever**.

Storyteller experience: the Barber row appears every single night from night 2
onward, always saying "usually skip"; nothing marks the death, nothing expires,
nothing swaps, nothing shows the two players their new tokens, and nothing
re-runs the new characters' abilities.

## Defects and gaps

1. **P0** · Barber night row tells the storyteller to skip exactly when it must
   run · The row is only meaningful when the Barber is dead, but
   `NightScreen.kt:751-757` prints "All holders are dead — usually skip." for
   any step whose holders are all dead. Rules require waking the Demon that
   night. · `app/.../NightScreen.kt:751-757`, `NightOrder.kt:142-178` · Repro:
   kill the Barber on day 1, advance to night 2, look at the Barber row.

2. **P0** · Barber row appears on nights when it must not · The step is emitted
   whenever a seat holds `barber`, alive or dead-but-already-resolved. There is
   no "died today or tonight" gate and no reset, so from night 2 to the end of
   the game the row is present and the storyteller has to remember, unaided,
   whether the death was *today*. · `NightOrder.kt:142-178` · Repro: play three
   nights with a living Barber — the row is there every night.

3. **P0** · Swapped players are never shown their new character, and their new
   abilities are never run · The rules require waking each swapped player with
   the "You are" card, and (Abilities page) the new ability works immediately —
   including first-night-only info and a refreshed once-per-game. The app
   swaps two `characterId`s and stops. Nothing prompts the two show cards,
   nothing tells the storyteller "Nia is now the Washerwoman — give Washerwoman
   info tonight", nothing clears a stale `No ability` / `Once` token, nothing
   removes the old character's persistent tokens ("any of its persistent effects
   end"). · `GameActions.kt:98-115`, `NightScreen.kt:470-524` · Repro: swap two
   seats from the seat sheet; observe zero follow-up.

4. **P1** · HAIRCUTS TONIGHT is never placed automatically · `deathNotes`
   produces prose (`StatusEffects.kt:100`) but no token. The reminder exists in
   the data (`characters.json:968-970`) and must be found by hand through
   Grimoire → seat → Add reminder → scroll to Barber. · Repro: kill the Barber;
   no token appears.

5. **P1** · HAIRCUTS TONIGHT never expires · `("barber","Haircuts tonight")` is
   absent from `EXPIRES_AT_DAWN` (`GameActions.kt:218-225`), so a hand-placed
   token stays on the seat for the rest of the game and can never be used as the
   step's gate. · Repro: place it, advance to dawn, it is still there.

6. **P1** · No swap tool in the night step · The only path is Night tab →
   Grimoire tab → tap seat A → Swap characters → scroll → tap seat B → back.
   `QuickResolutions` covers `snakecharmer`, `fanggu`, `professor` and a generic
   `DemonKillPanel`; there is no `barber` branch. ·
   `NightScreen.kt:470-524` · Repro: expand the Barber step at night — the panel
   contains only the guide prose and two generic show chips.

7. **P1** · "Not another Demon" is not enforced and self-swap is not offered ·
   `SeatSheet.kt:118-141` lists every other seat, including a second Demon (Fang
   Gu jump, Pit-Hag-created Demon, Lil' Monsta host). The rule that the acting
   Demon *may* swap themself is nowhere surfaced. · Repro: with two Demons in
   play, the picker offers the illegal pair.

8. **P1** · Executing the Barber gives no warning at all · The Day tab's
   "Execute" (`DayScreen.kt:111-114`, `:350-357`) and the dusk guard
   (`GameShell.kt:599-604`) bypass `StatusEffects.deathNotes`. The Barber's most
   common death is an execution. · Repro: put the Barber on the block, tap
   Execute — nothing mentions the Barber.

9. **P2** · `swapCharacters` silently transplants a hidden identity ·
   `GameActions.kt:98-115` swaps `shownCharacterId` along with `characterId`. If
   the Drunk or Marionette is swapped, the false-identity token moves to the
   other seat with no prompt, so the new Drunk "believes" they are whatever the
   old Drunk believed — a storyteller choice the app makes silently. · Repro:
   swap the Drunk with any Townsfolk and open both seat sheets.

10. **P2** · Barber row still shows with no living Demon · If every Demon is
    dead (or Exorcised) there is nobody to wake, but the row is unchanged. ·
    `NightOrder.kt:142-178`.

11. **P2** · "If a player dies then becomes the Barber, no swap tonight" is not
    modelled · A Pit-Hag or Barber swap that makes a *dead* player the Barber
    must not arm a haircut. Nothing tracks the character held at death, even
    though `DeathRecord.characterIdAtDeath` (`GameState.kt:85`) already records
    it. · Repro: Pit-Hag turns a corpse into the Barber.

12. **P2** · The night sheet can grow above the current position after a swap ·
    `NightScreen.kt:84-90` recomputes `steps` from `state.players`, so a swap
    that creates e.g. a Gossip (other-night 57, *before* the Barber at 59)
    inserts a row the storyteller has already scrolled past, with no signal. ·
    Repro: swap someone into a character earlier in the night order.

13. **P3** · No log entry for a Barber swap · `GameLogDialog`
    (`app/.../GameExtras.kt`) is death/nomination based; a character swap leaves
    no trace, so post-game reconstruction is impossible.

## Proposed behaviour (spec)

### State the engine needs

- Treat `PlacedReminder("barber", "Haircuts tonight")` as the authoritative gate.
- Add `("barber","Haircuts tonight")` to `EXPIRES_AT_DAWN`.
- Add a `swapCharactersForBarber(state, id1, id2, lookup)` engine action distinct
  from the raw `swapCharacters`, returning both the new state and a list of
  follow-up obligations (see below).

### Trigger (on death, any cause, any phase)

- When a player whose `characterId == "barber"` dies and was **not** impaired at
  death (`DeathRecord.abilityImpairedAtDeath == false`), automatically place
  `barber:Haircuts tonight` on that seat and add the dawn/day-start briefing
  line "Nia the Barber died — the Demon may swap two characters tonight."
- If they *were* impaired, place nothing and brief "Nia the Barber died while
  drunk/poisoned — no haircut tonight."
- If a player *becomes* the Barber after dying, do **not** place the token.

### Night step

- when: other nights only. Wake condition: **some seat carries
  `barber:Haircuts tonight`** AND at least one Demon is alive AND that Demon
  does not carry `exorcist:Chosen`. Otherwise the row is **hidden entirely**
  (not shown greyed, not shown "usually skip").
- The row's actor is the **Demon**, not the Barber — title it
  "Barber — haircuts (wake the Demon)" and list the Demon's name as the holder
  so the "all holders are dead" logic never fires on the corpse.
- targets: exactly 2, or an explicit **"Demon declines — no swap"** button.
  Candidates: **all seats, alive or dead**, minus every Demon *except* the
  acting Demon (who is offered first and labelled "(self)"). Default sort:
  living non-Demon seats, then dead seats.
- immediate effects on confirm:
  - swap `characterId` between the two seats;
  - **do not** swap `alignmentFlipped` (alignment never changes);
  - **prompt**, do not assume, for `shownCharacterId` when either seat carries
    one: "Beau now holds the Drunk token — which Townsfolk do they believe they
    are?" (default: keep the incoming shown token);
  - remove each seat's **outgoing** character's persistent reminder tokens
    (any `PlacedReminder` whose `sourceId` equals the character that just left
    the seat), per "any of its persistent effects end";
  - remove `barber:Haircuts tonight`.
- deferred effects: emit two obligations onto the night sheet, inserted
  immediately below the Barber row:
  1. "Wake Beau — show 'You are' + <new character>." (one-tap
     `ShowCard.CharacterCard("YOU ARE", newId)`, pre-filled, no picker.)
  2. same for the other player.
  And, per the Abilities page, a third obligation per swapped seat when the new
  character has a first-night-only ability (`firstNightReminder` non-empty and
  `otherNightReminder` empty) or a once-per-game ability: **"<name> is now the
  <character> — run their first-night ability tonight"** with a direct link to
  that character's `InfoCalc` panel, and clear any `No ability` / `Once` token
  the seat inherited.
- expiry: `barber:Haircuts tonight` at dawn (and immediately on resolution).
- information: none computed by the Barber itself. The consequence is that other
  info rows may need to run — see the obligations above.
- visibility: the Demon sees the "This character selected you" card + Barber
  token (`night_guide.json:604-616` already has this). The two swapped players
  see only their own new token; they are **not** told who the other swapped
  player is.
- day-time inputs: none.
- interactions:
  - Vigormortis/Lil' Monsta/Legion — "the Demon" is whoever currently holds a
    Demon character; with Legion, every Legion is a Demon, so no two Legion
    players may be swapped with each other.
  - Exorcist: an Exorcised Demon does not act, so no haircut.
  - Drunk/Poisoned Demon: the wiki doesn't say; treat as "the Demon still wakes
    and points, but the storyteller may make the swap not happen" and print that
    as a caution rather than blocking.
  - Scarlet Woman / star pass happening the same night: resolve the Demon
    change *before* the Barber row (it already is, order-wise), and re-target
    the Barber row at the new Demon.

### UI text for the step

- Title: `Barber — haircuts (wake <Demon name>)`
- Body: `<Barber name> died. Wake <Demon>, show "This character selected you" +
  the Barber token. They shake their head, or point at two players.`
- Buttons: `Demon declines` | `Swap <A> ↔ <B>`
- After swap: `Wake <A>: "You are the <X>"` · `Wake <B>: "You are the <Y>"` ·
  `<A> is now the <X> — run their first-night ability tonight`

### Data changes

- `night_guide.json:599-617` — keep the prose but change the first sentence to
  name the Demon as the waking player, and add: "Neither chosen player may be a
  Demon other than the Demon themself; the Demon may swap themself. Dead players
  may be chosen. Alignments do not change. Each swapped player gains their new
  ability immediately — including 'you start knowing' info and a fresh
  once-per-game use."
- `characters.json:960-972` — no ability-text drift; leave as is.

## Tests to add

1. **Given** a game where the Barber is alive, **when** the other-night sheet is
   built, **then** no `barber` step is present.
2. **Given** the Barber is killed by execution on day 2, **then**
   `barber:Haircuts tonight` is on their seat and the day-start briefing
   contains "Barber died — the Demon may swap".
3. **Given** the Barber was drunk (Sweetheart drunk token) when killed, **then**
   no `Haircuts tonight` token is placed.
4. **Given** `barber:Haircuts tonight` is placed at night 2, **when**
   `advancePhase` runs to dawn, **then** the token is gone.
5. **Given** a Barber haircut with two candidate lists, **then** the candidate
   list excludes non-acting Demons and includes dead players and the acting
   Demon.
6. **Given** seats A (Washerwoman, `washerwoman:Townsfolk` tokens placed) and
   B (Empath), **when** the Barber swap exchanges them, **then** A's
   `characterId == "empath"`, B's `characterId == "washerwoman"`, neither
   `alignmentFlipped` changed, and A no longer carries any `sourceId ==
   "washerwoman"` token.
7. **Given** a swap that makes B the Washerwoman on night 3, **then** the engine
   reports a "run first-night ability tonight" obligation for B, and
   `InfoCalc.compute(..., "washerwoman", B, ...)` returns a non-null result.
8. **Given** a good player swapped into a Minion character, **then**
   `player.isEvil(lookup)` is still `false` (alignment preserved) — i.e. the
   swap sets `alignmentFlipped = true` where needed, or the engine records the
   pre-swap alignment explicitly.
9. **Given** a dead player is turned into the Barber by the Pit-Hag, **then** no
   `Haircuts tonight` token is placed and no Barber step is emitted.
10. **Given** every Demon is dead, **then** no Barber step is emitted even with
    `Haircuts tonight` on the board.
