# Washerwoman (washerwoman) — Trouble Brewing Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Washerwoman> (raw wikitext fetched 2026‑08‑25).

**Current ability text (verbatim):**
> "You start knowing that 1 of 2 players is a particular Townsfolk."

**Summary bullets (verbatim):**
- "During the first night, the Washerwoman is woken, shown two players, and learns the character
  of one of them."
- "They learn this only once and then learn nothing more."

**How to Run (verbatim):**
> "**While preparing the first night**, put the Washerwoman's **TOWNSFOLK** reminder token by
> any Townsfolk character token, and put the Washerwoman's **WRONG** reminder token by any other
> character token.
>
> During the first night, wake the Washerwoman and point to the players marked **TOWNSFOLK** and
> **WRONG**. Show the character token marked **TOWNSFOLK** to the Washerwoman. Put the
> Washerwoman to sleep. **Remove the Washerwoman's reminder tokens when convenient.**"

**Examples (verbatim):**
- "Evin is the Chef, and Amy is the Ravenkeeper. The Washerwoman learns that either Evin or Amy
  is the Chef."
- "Julian is the **Imp**, and Alex is the Virgin. The Washerwoman learns that either Julian or
  Alex is the Virgin." → the WRONG player may be **any** character, including the Demon.
- "Marianna is the **Spy**, and Sarah is the Scarlet Woman. The Washerwoman learns that one of
  them is the Ravenkeeper. Here, the Spy is registering as a Townsfolk—in this case, the
  Ravenkeeper." → **both** shown players may be evil.

**Storyteller-relevant clarifications from Tips & Tricks:**
- "You know that of the two players you are shown, one must be the Townsfolk you are shown.
  Importantly, this means that **you know that the person you see is not the Drunk**." → the
  TOWNSFOLK token must never be placed on the seat whose real character is `drunk`.
- "When the Washerwoman is poisoned or is actually the Drunk, they will often get information
  that is easy to figure out is incorrect" — the ST is expected to fabricate a plausible pair.
- "Beware of the Spy! They may register as a Townsfolk character to you."
- Bluffing section: "You would have received your information on **night one**" — it is a
  first-night-only step; there is no other-night wake.

**Not stated by the wiki:** whether the Washerwoman may be one of the two players pointed to.
The How to Run says "put the WRONG reminder token by **any other** character token", which does
not exclude the Washerwoman's own seat, and the ability text ("1 of 2 players is a particular
Townsfolk") is not violated by it. Common practice is to exclude the Washerwoman from both
slots. **Flagged as unresolved — do not hard-block it; default to excluding self and allow an
override.**

**Recluse:** the Recluse registers only as evil / a Minion or Demon
(<https://wiki.bloodontheclocktower.com/Recluse>), so it can be the WRONG player but can never
be the Townsfolk the Washerwoman learns.

**Jinxes:** none.

## What the app does today

Data
- `characters.json:163-176` — ability text matches the wiki. `firstNightReminder`: "Show the
  character token of a Townsfolk in play. Point to two players, one of which is that character."
  `otherNightReminder: ""`. `reminders: ["Townsfolk", "Wrong"]`. Correct.
- `night_and_jinxes.json:341` — `washerwoman` is at index 46 of `firstNight`, first of the
  info-Townsfolk block (before librarian/investigator/chef/empath) and after every Minion setup
  ability. Absent from `otherNight`. Correct.
  (`GameActionsTest.kt:158-162` already asserts poisoner-before-washerwoman.)
- `night_guide.json:86-98` — instructions: "Wake the Washerwoman. Show them the character token
  of a Townsfolk that is in play, then point at two players: one who is that Townsfolk and one
  decoy (mark them with the Townsfolk and Wrong reminders). Put the Washerwoman back to sleep. If
  the Washerwoman is drunk or poisoned, you may show false information." One show card:
  `kind: "token"`, `token: "pick"`, text "One of the 2 players I point to is this character".

Engine
- `InfoCalc.kt:68` → `startKnowing(ctx, Team.TOWNSFOLK, "Townsfolk")` at `InfoCalc.kt:408-421`:
  ```kotlin
  val inPlay = ctx.players.filter { ctx.character(it)?.team == team }
  if (inPlay.isEmpty()) return InfoResult("No Townsfolk in play", caveats = misregistrations(ctx, ctx.players))
  return InfoResult(
      headline = "Townsfolk in play: " + inPlay.joinToString { "${ctx.name(it)} (${ctx.character(it)?.name})" },
      detail   = "Show one of those character tokens, point to that player plus 1 wrong player.",
      caveats  = misregistrations(ctx, ctx.players),
  )
  ```
  - Uses `characterId`, so the **Drunk is correctly excluded** from the Townsfolk list (the Drunk
    is `Team.OUTSIDER`) — this satisfies the "the person you see is not the Drunk" rule by
    construction. Good.
  - Travellers (`Team.TRAVELLER`) are excluded. Good.
  - `misregistrations` (`InfoCalc.kt:121-130`) is called with **every** player, so on a
    TB board with a Spy and a Recluse the ST sees two caveat lines about players who may be
    irrelevant to the pair they chose.
  - `targetsNeeded("washerwoman")` is 0 (`InfoCalc.kt:22-26`), so there is **no picker** — the
    app cannot be told which two players were pointed to.
- `InfoCalc.impairments` (`:133-153`) supplies the drunk/poison caveat, and `commonCaveats`
  (`:158-166`) adds the Vortox line.

UI
- The step renders through the generic path: night_guide prose + the "Show Townsfolk token"
  dialog (`NightScreen.kt:792-832`, `GuideShowDialog` at `:366-454` — a character picker that
  sorts in-play characters first, which is a good fit here), then the info block
  (`NightScreen.kt:863-902`).
- `NightToolTray` (`NightScreen.kt:283-354`) does offer the two reminder tokens: tap
  **Townsfolk** or **Wrong**, then tap a seat. Placement goes through
  `placeExclusiveReminder` when the label appears once in `allReminders` (`:319-339`), so each
  token moves rather than duplicating. This works.
- The false-info chips (`NightScreen.kt:904-930`) produce nothing for the Washerwoman: the
  headline is neither numeric nor YES/NO.
- Setup: `GameActions.validateSetupState` (`GameActions.kt:503-561`) enforces Drunk/Lunatic/
  Marionette shown-tokens and the Fortune Teller red herring, and `GameShell.kt:347-375` even
  pops a dedicated **"Fortune Teller red herring"** prompt during SETUP. There is **no
  equivalent for the Washerwoman's TOWNSFOLK/WRONG prep**, which the How to Run also places in
  "while preparing the first night".

Works: night-order position; Drunk correctly excluded from the Townsfolk list; Travellers
excluded; the token tray places and moves both reminders; the "Show Townsfolk token" full-screen
card; the impairment and Vortox caveats.

## Defects and gaps

1. **P1 · The app never chooses or records the pair** — `startKnowing` returns a *list of every
   Townsfolk in play* and the instruction "Show one of those character tokens, point to that
   player plus 1 wrong player." The single most fiddly part of the step — picking a good decoy —
   is left entirely to the ST, and the choice is never stored. Later in the game the ST cannot
   answer "what did I tell the Washerwoman?" from the app (only from the two tokens, which the
   How to Run tells them to remove "when convenient").
   *Repro:* night 1 → Washerwoman step → the headline lists 5-6 names and characters with no
   suggestion and no way to commit a choice.

2. **P1 · No first-night prep prompt** — the rules place the TOWNSFOLK/WRONG token placement in
   *setup*, before the night starts. The app has the exact precedent
   (`GameShell.kt:347-375` for the red herring) and `validateSetupState`
   (`GameActions.kt:503-561`) already knows how to demand a setup choice, but neither covers the
   Washerwoman (or the Librarian/Investigator, which share the mechanic).

3. **P1 · Spy-as-the-Townsfolk is not offered as a choice** — the wiki's third example is
   *exactly* this, and it is one of the strongest ST tools in TB. `startKnowing` filters on the
   true `team`, so the Spy never appears in the "Townsfolk in play" list; the ST only gets the
   generic caveat "X is the Spy — may register as good / a Townsfolk or Outsider."
   To use it the ST must ignore the computed answer entirely. The result should include the Spy
   as a legal Townsfolk option, tagged, with the character to register as chosen and **recorded**
   (the same `Registered: <Character>` token proposed in `undertaker.md` / `ravenkeeper.md`, so
   the Spy stays consistent for the Undertaker later).

4. **P1 · No false-info generator when impaired** — a poisoned Washerwoman or a Drunk-shown-as-
   Washerwoman needs a **plausible fabricated pair**: a not-in-play Townsfolk (or an in-play one
   pointed at the wrong seats). `NightScreen.kt:904-930` only fabricates numbers and YES/NO. The
   ST must build the lie from scratch, on night 1, under time pressure, with the whole table
   waiting — and the wiki warns that a sloppy Washerwoman lie is "easy to figure out".

5. **P2 · Misregistration caveats are unfiltered** — `startKnowing` calls
   `misregistrations(ctx, ctx.players)` (`InfoCalc.kt:419`), i.e. every Spy/Recluse on the board,
   rather than the players actually involved. On a board with both, the step shows two red lines
   that mostly are not about the pair the ST picked.

6. **P2 · The Washerwoman is listed as a candidate for their own information** —
   `startKnowing` does not exclude `ctx.holder`, so the headline reads
   "Townsfolk in play: … Ana (Washerwoman) …". Whether that is legal is genuinely unclear (see
   sources), but it should not be the *default* suggestion.

7. **P2 · The "Townsfolk"/"Wrong" tokens never expire** — they are in neither
   `EXPIRES_AT_DAWN` nor `EXPIRES_AT_DUSK` (`GameActions.kt:218-242`). The How to Run says
   "Remove the Washerwoman's reminder tokens when convenient". Left in place they clutter the
   circle (`GrimoireScreen.kt:148-161`) for the rest of the game and, more importantly, `Wrong`
   sitting on a seat looks like a status the ST might misread later.

8. **P2 · No day-1 briefing of what was told** — with no dawn/day-start briefing anywhere
   (`GameShell.kt:126-168`), the ST cannot check the Washerwoman's public claim against what
   they actually showed. This is the ST's main lie-detection tool on day 1.

9. **P3 · "No Townsfolk in play" is unreachable in practice** — `startKnowing`'s empty branch
   (`InfoCalc.kt:410-415`) cannot fire for the Washerwoman, since the Washerwoman is themselves a
   Townsfolk. (For the Librarian it is the meaningful "show the 0 signal" branch.) Harmless, but
   the shared helper should special-case the Washerwoman: if the *only* Townsfolk in play is the
   Washerwoman themselves, the ST has a genuine problem and should be told.

10. **P3 · No "Show 0" / no-information card for a Vortox game** — with a Vortox, Townsfolk info
    must be false, and the app says so (`InfoCalc.kt:162-164`) but offers nothing to show.

## Proposed behaviour (spec)

**Structured night behaviour**

- **when:** `first` night only. Wake condition: `holder.alive` (night 1, so essentially always).
  Never on other nights. The step must not be emitted at all on nights ≥ 2 — `NightOrder`
  already gets this right because `washerwoman` is absent from `otherNight`.
- **prep (SETUP phase, new):** add a Washerwoman prompt alongside the red-herring prompt
  (`GameShell.kt:347-375`), and a `validateSetupState` (`GameActions.kt:503-561`) advisory
  "Washerwoman: choose the Townsfolk and the decoy". The prompt should:
  - list every legal **TOWNSFOLK** option: all seats whose real `characterId` is a Townsfolk,
    **excluding** the Washerwoman by default (with an "include me" override), **excluding** the
    Drunk automatically (already true by team), **plus** the Spy tagged
    `"Spy — may register as a Townsfolk"` with a sub-choice of which Townsfolk token to show;
  - then list every legal **WRONG** option: any other seat, including the Demon and Minions
    (wiki example 2 and 3), excluding the seat already marked TOWNSFOLK and (by default) the
    Washerwoman;
  - suggest a default: a Townsfolk with an ability the good team benefits from confirming, and a
    decoy that is not adjacent in seat order to the real one;
  - place `PlacedReminder("washerwoman", "Townsfolk")` and `PlacedReminder("washerwoman",
    "Wrong")` exclusively (`GameActions.placeExclusiveReminder`).
- **targets:** the step reads the two placed tokens rather than asking again. If they are
  missing when the step is reached, fall back to an in-step 2-slot picker
  (`InfoCalc.targetsNeeded("washerwoman")` → 2, with slot 1 = the Townsfolk and slot 2 = the
  decoy, not an unordered pair).
- **immediate effects:** none beyond the tokens.
- **deferred effects:** record what was shown as a `NightAction(day = 1, sourceId =
  "washerwoman", shownCharacterId, townsfolkSeatId, wrongSeatId, wasFalse)` so the day‑1
  briefing can print "You told <Name>: one of <A>/<B> is the <Character>".
- **expiry:** add `"washerwoman" to "Townsfolk"` and `"washerwoman" to "Wrong"` to
  **`EXPIRES_AT_DAWN`** (`GameActions.kt:218-225`) — the information is given during night 1 and
  the tokens have no further function, matching "remove when convenient". Keep the recorded
  `NightAction` so the ST can still look it up.
- **information:**
  - True answer: the character of the seat marked TOWNSFOLK, read from `characterId`.
  - The pair to point at: the TOWNSFOLK seat and the WRONG seat, in a **randomised display
    order** so the ST does not accidentally always point at the real one first.
  - **Spy option:** if the Spy is chosen as the "Townsfolk", show the chosen registration token
    and persist `PlacedReminder("spy", "Registered: <Character>")`.
  - **Impaired / Vortox alternative:** a one-tap **false pair generator** offering, in order:
    (a) a real in-play Townsfolk pointed at two players *neither* of whom is that character;
    (b) a not-in-play Townsfolk from the script (preferring the demon bluffs, so the lie is
    consistent with what the Demon was told); (c) an in-play Townsfolk paired with the real
    holder. Each option shows the exact card to display and the two seats to point at.
  - Show card: reuse the existing `GuideShowDialog` `token: "pick"` card, pre-selected to the
    chosen character, with text "One of the 2 players I point to is this character".
- **visibility:** nothing to the Demon/Minions, but the ST should be reminded that a Spy chosen
  as the Townsfolk **will see this in the grimoire** during the Spy's own step.
- **day-time inputs:** on day 1, let the ST record the Washerwoman's public claim so the app can
  flag a mismatch with what was actually shown.
- **interactions/jinxes to handle explicitly:**
  - **Drunk** — never the TOWNSFOLK (automatic, since `drunk` is an Outsider). A
    Drunk-shown-as-Washerwoman gets a **false** pair, always.
  - **Spy** — may be the TOWNSFOLK; register-as choice, recorded.
  - **Recluse** — may be the WRONG player; can never be the TOWNSFOLK.
  - **Marionette** — a Marionette shown as the Washerwoman gets arbitrary information
    (`InfoCalc.kt:139-141` already flags this).
  - **Poisoner** — poisons on night 1 *before* the Washerwoman (order 27 vs 46) → false info.
  - **Vortox** — Townsfolk info must be false.
  - **Philosopher/Boffin-granted Washerwoman** — the same step must be runnable on demand for a
    seat that acquires the ability later; the prep flow must therefore be re-invokable, not
    setup-only.
  - No jinxes.

**UI text**

- Setup prompt title: `"Washerwoman: pick the Townsfolk and the decoy"`;
  body `"Mark one Townsfolk (the character you'll show) and one other player (the decoy). The
  Spy may be shown as a Townsfolk. The Drunk never can."`
- Night step, prepared: `"Point to <A> and <B>, then show the <Character> token."` +
  `Show full-screen` chip.
- Night step, not prepared: `"No Townsfolk/Wrong tokens placed — choose them now."` + picker.
- Impaired: `"<Name> is POISONED / is the Drunk — show a FALSE pair:"` + generated options.

**Data changes**

- `characters.json:163-176` — unchanged.
- `night_guide.json:86-98` — extend `first.instructions` with: the tokens are placed **while
  preparing the first night**; the decoy may be any other player, including the Demon; the Spy
  may be shown as the Townsfolk; the Drunk never can; remove both tokens when convenient.
- `GameActions.kt:218-225` — add `"washerwoman" to "Townsfolk"` and `"washerwoman" to "Wrong"`
  to `EXPIRES_AT_DAWN`.
- `InfoCalc.kt:22-26` — `targetsNeeded("washerwoman")` → 2 (ordered: Townsfolk, then decoy).
- `InfoCalc.kt:408-421` — `startKnowing` should take the chosen pair and return a concrete
  answer plus `misregistrations` limited to the two chosen seats, not the whole table.

## Tests to add

1. **Townsfolk list excludes the Drunk**
   Given a 7-player TB game with a Drunk shown as the Chef
   When `InfoCalc.compute(..., "washerwoman", ...)` runs
   Then the Drunk seat is **not** offered as the Townsfolk. *(passes today via team filtering —
   lock it in explicitly; `GameActionsTest.kt:369` sets up this exact board.)*

2. **Townsfolk list excludes the Washerwoman by default**
   Given the Washerwoman holds the ability
   Then the default options do not include their own seat. *(fails today —
   `InfoCalc.kt:409`.)*

3. **Spy is offered as a legal Townsfolk**
   Given a Spy in play
   Then the options include the Spy tagged as a possible Townsfolk registration, and choosing
   it records `PlacedReminder("spy", "Registered: <Character>")`. *(fails today.)*

4. **Recluse is never offered as the Townsfolk**
   Given a Recluse in play
   Then the Recluse appears only among the decoy options.

5. **Decoy may be the Demon**
   Given the wiki example (Imp + Virgin)
   Then selecting the Imp as the WRONG player is accepted and the answer is "one of <Imp seat>
   and <Virgin seat> is the Virgin".

6. **Chosen pair is recorded**
   Given a chosen pair on night 1
   Then a `NightAction` exists on day 1 naming the shown character and both seat ids, and it
   survives `advancePhase()` to DAY 1.

7. **Tokens expire at dawn**
   Given `PlacedReminder("washerwoman", "Townsfolk")` and `…"Wrong"` placed on night 1
   When `advancePhase()` runs NIGHT 1 → DAY 1
   Then both are gone. *(fails today — `GameActions.kt:218-225`.)*

8. **Poisoned Washerwoman gets false alternatives**
   Given the Washerwoman holds `PlacedReminder("poisoner", "Poisoned")`
   Then the result carries the POISONED caveat **and** a non-empty `falseAlternatives` list of
   (character, seatA, seatB) triples. *(the caveat passes today via `InfoCalc.kt:145`; the
   alternatives do not exist.)*

9. **Vortox forces false info**
   Given a Vortox alive
   Then the caveat "VORTOX in play — Townsfolk info must be FALSE." is present and false
   alternatives are offered. *(caveat passes today, `InfoCalc.kt:161-164`.)*

10. **Setup validation asks for the pair**
    Given a Washerwoman in play with no `washerwoman` tokens placed
    When `GameActions.validateSetupState` runs
    Then an issue "Washerwoman: choose the Townsfolk and the decoy" is returned.
    *(fails today — `GameActions.kt:503-561`.)*

11. **Misregistration caveats are scoped to the pair**
    Given a Spy and a Recluse in play but neither in the chosen pair
    Then the result carries **no** Spy/Recluse caveat. *(fails today — `InfoCalc.kt:419`
    passes every player.)*

12. **No other-night step**
    Given a Washerwoman alive on night 2
    When the other-night sheet is built
    Then there is no `washerwoman` step. *(passes today — lock it in.)*
