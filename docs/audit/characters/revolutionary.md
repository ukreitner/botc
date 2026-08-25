# Revolutionary (revolutionary) — Fabled

## Official rules (sources)

Sources (fetched 2026-08-25):
<https://wiki.bloodontheclocktower.com/Revolutionary> and the raw wikitext via
`https://wiki.bloodontheclocktower.com/api.php?action=parse&page=Revolutionary&prop=wikitext`,
plus <https://wiki.bloodontheclocktower.com/Fabled>.

Current ability text (`characters.json` says "one of them" where the wiki says "1 of them"
— cosmetic):

> "2 neighboring players are known to be the same alignment. Once per game, 1 of them
> registers falsely."

How to Run (quoted from the wikitext):

> "Before gameplay, declare the Revolutionary is active and identify the two neighboring
> Revolutionary players. Add the Revolutionary token to the Grimoire and mark both players
> with the **REGISTER FALSELY?** reminder between their character tokens.
>
> One Revolutionary draws first; the Storyteller notes their token, selects a
> same-alignment token from the bag, and gives it to the other Revolutionary. Remaining
> players then draw.
>
> Once per game, the Storyteller can make one marked player 'register as a different
> character and alignment, then remove the **REGISTER FALSELY?** reminder.'"

Examples (wiki):

- A deaf player and a hearing player pair up (Poisoner and Imp — both evil) so they can
  sign to each other while scheming.
- A twelve-year-old pairs with her father (Ravenkeeper and Fortune Teller — both good) so
  she can learn the game.

Rules that matter for storytelling:

- **This is an accessibility Fabled.** The pair is public knowledge — the whole table knows
  those two seats share an alignment — and consent from both players is required before
  the game.
- **Setup is a constrained deal, not a free deal.** The two neighbouring seats must receive
  same-alignment characters. Mechanically: draw for seat A, then hand seat B a token of the
  matching alignment, then deal the rest normally.
- **The pair must be *neighbours*** in the seating circle.
- **The false registration is once per game, storyteller-timed**, and can be a different
  character, a different alignment, or both. It applies to *one* of the two marked players
  and, once used, the REGISTER FALSELY? reminders come off (the wiki's How to Run removes
  the reminder from the used player; in practice the "once per game" is spent for the pair
  as a whole). It exists precisely so the public "these two are the same alignment" fact
  can be broken once — otherwise the pair is a free solve for the town.
- Fabled general rules (<https://wiki.bloodontheclocktower.com/Fabled>): cannot be killed,
  immune to all game effects, do not count for the two-alive evil win. Revolutionary is in
  the "Social Interactions & Accessibility" group and is added **at the start of the game**.
- **No night action**, **no setup bracket**, **no jinxes** listed.

## What the app does today

Data:

- `engine/src/main/resources/botc/data/characters.json:2274` — ability matches (modulo
  "one" vs "1"); `reminders: ["Used"]`. The official token is **REGISTER FALSELY?**, and
  there are **two** of them (one per marked player), not one "Used".
- Correctly absent from both night order lists.
- No `night_guide.json` entry.

Engine:

- Nothing whatsoever. `grep -rn "revolutionary" engine/src app/src` returns only the
  `characters.json` / `raw_sv_travellers_fabled.json` entries.
- `GameActions.deal` (`GameActions.kt:311-330`) shuffles the whole bag and assigns it to
  non-Traveller seats in seat order with **no constraints**. There is no hook for "these
  two seats must match alignment".
- `GameActions.validateSetupState` (`GameActions.kt:503-545`) validates the Drunk's shown
  token, the Lunatic's, the Marionette's, and the Fortune Teller's red herring — but has no
  concept of a Revolutionary pair.
- Misregistration is a **comment-only** feature: `InfoCalc.misregistrations`
  (`InfoCalc.kt:121-131`) hard-codes exactly two cases:
  ```
  "spy"     -> "… may register as good / a Townsfolk or Outsider."
  "recluse" -> "… may register as evil / a Minion or Demon."
  ```
  It emits *caveat strings*; the computed answers (`InfoCalc.compute`, called at
  `NightScreen.kt:857`) always use the true alignment via `Player.isEvil`
  (`GameState.kt:49`). There is no mechanism to make a specific player register falsely for
  a specific piece of info.

UI:

- `FabledSheet` (`GameExtras.kt:145-198`) toggles `revolutionary` on. There is no pair
  picker, no consent prompt, no reminder placement.
- The `("revolutionary","Used")` token is **unreachable from the reminder picker** —
  `ReminderPicker` (`SeatSheet.kt:492-500`) builds its list from
  `gameData.resolve(state.script)` and `GameData.resolve` (`GameData.kt:49`) returns only
  the script's character ids; built-in scripts exclude Fabled
  (`GameData.kt:35-42`, `filter { it.team.isTownResident }`). Because the Revolutionary has
  no night step, the `NightToolTray` fallback that rescues the Storm Catcher's token
  (`NightScreen.kt:98`) does not apply. Only the generic `"Used"` chip
  (`SeatSheet.kt:502`) is available, with `sourceId = ""`.
- No day-time or night-time affordance ever mentions the pair again.

Storyteller's actual experience today: you announce the pair verbally, you deal the bag
*by hand* (because "Deal randomly & start" will happily give the pair opposite alignments),
you place a generic "Used" token if you remember, and you track the once-per-game false
registration entirely in your head — including remembering to apply it manually every time
you compute Empath/Investigator/Fortune Teller/Undertaker info for the rest of the game.

## Defects and gaps

1. **P0** · Random dealing can break the Fabled's core promise · `GameActions.deal`
   (`GameActions.kt:311`) is an unconstrained shuffle. Repro: activate the Revolutionary,
   use "Deal randomly & start" (`SetupScreen.kt:485`) — the two neighbouring players are
   as likely as not to end up on opposite teams, and the publicly announced fact is a lie.
   Because the app has no idea which two seats are the pair, it cannot even warn.
2. **P1** · The pair is never recorded · `GameState.fabledIds` (`GameState.kt:98`) is a
   bare id list with no payload. Nothing stores "seats 3 and 4 are the Revolutionaries",
   so no downstream feature (dealing, info, day briefing, log) can use it.
3. **P1** · The once-per-game false registration is untracked and unenforceable ·
   The official REGISTER FALSELY? tokens are unreachable (see above) and the app's own
   `("revolutionary","Used")` label is the wrong token with the wrong semantics. Nothing
   stops the ST from "using it twice", and nothing reminds them it is still available.
4. **P1** · `InfoCalc` cannot express a false registration · `InfoCalc.kt:121-131` only
   knows Spy/Recluse and only produces prose caveats; every computed number
   (`InfoCalc.kt:189`, `:210`, `:250`, `:359`, `:371`…) reads the true alignment. Even if
   the pair and the once-per-game flag were recorded, the night sheet would still hand the
   ST the *true* Empath/Chef/Investigator/Fortune Teller answer with no "…and here is the
   number if you spend the Revolutionary's false registration on <name>" alternative.
5. **P2** · No neighbour validation · Nothing checks that the two chosen seats are actually
   adjacent in the circle, nor re-checks after `moveSeat`/`addSeat`/`removeSeat`
   (`GameActions.kt:18-40`) shuffles the seating.
6. **P2** · Reminder token drift · `characters.json:2274` declares one `"Used"` token;
   the official set is two **REGISTER FALSELY?** tokens, one on each pair member, removed
   when spent.
7. **P2** · Cannot be declared before the game starts · `FabledSheet` lives in `GameShell`
   (`GameShell.kt:501`), but the Revolutionary's entire mechanism is a *setup* procedure
   ("Before players draw tokens…").
8. **P3** · Ability text drift · `characters.json:2274` says "one of them"; the wiki says
   "1 of them".

## Proposed behaviour (spec)

Configuration (set during setup, before the bag is dealt):

- `fabledConfig["revolutionary"] = Revolutionary(seatA: Long, seatB: Long, falseRegistrationUsed: Boolean = false, falseRegistrationOn: Long? = null, registersAs: String? = null)`.
- A setup picker: tap two seats; the app validates they are neighbours in `state.players`
  order (wrapping), and shows an explicit consent checkbox
  ("Both players have agreed to play as a pair").

Setup / dealing:

- `GameActions.deal` gains a constraint parameter, or a dedicated
  `dealWithRevolutionary(state, bag, pair, random)`:
  1. Draw a random token for `seatA`.
  2. Filter the remaining bag to characters whose team alignment matches `seatA`'s
     (`Team.isEvil` equality), pick one at random, assign to `seatB`. If no matching token
     remains, retry the whole deal (bounded attempts) and report a clear failure:
     *"This bag can't give the Revolutionary pair a matching alignment — add another
     evil/good character or change the pair."*
  3. Deal the remainder normally.
- `validateSetupState` (`GameActions.kt:503`) gains a check:
  *"<A> and <B> are the Revolutionary pair but are not the same alignment"* and
  *"…are not neighbours"*.
- On assignment, place `PlacedReminder("revolutionary","Register falsely?")` on **both**
  seats.

Once-per-game false registration:

- **when:** any time the storyteller chooses — most usefully at the moment a piece of info
  is being computed.
- **where the ST invokes it:** two entry points.
  1. From the seat sheet: a "Revolutionary: register falsely" action on either marked seat,
     opening a picker for *what* they register as — a character (any in the dataset) and/or
     an alignment.
  2. Inline on any `InfoCalc` night step: when one of the two marked seats is relevant to
     the answer, offer a chip **"Spend the Revolutionary's false registration on <name>"**
     that recomputes the headline with the override applied and shows both numbers
     side by side before the ST commits.
- **immediate effects:** set `falseRegistrationUsed = true`, `falseRegistrationOn`,
  `registersAs`; remove the REGISTER FALSELY? reminders from both seats; write a log line.
- **duration:** the wiki says "registers falsely" once — a single registration event, not a
  permanent state. Implement it as a **one-shot override consumed by the next info
  computation the ST applies it to**, and record in the log which piece of info it was
  spent on, so a later Undertaker/Empath contradiction is explainable.
  (The wiki is not explicit about whether it lasts for one piece of info or one night;
  flagging this rather than guessing — expose the choice as "spend on this info only" vs
  "for the rest of tonight".)
- **expiry:** the tokens come off when spent and never come back; nothing expires at
  dawn/dusk. Do not add to `EXPIRES_AT_DAWN`/`EXPIRES_AT_DUSK`.

Generalised registration model (the reusable part):

`InfoCalc` needs a `registrationOverride: Map<Long, Registration>` in its `Ctx`
(`InfoCalc.kt:97`), where `Registration(characterId: String?, evil: Boolean?)`, consulted
by `isEvil(p)` and by every character-identity read (Undertaker, Ravenkeeper, Investigator,
Librarian, Washerwoman, Fortune Teller, Dreamer, Oracle, Empath, Chef, Clockmaker,
Balloonist, Bounty Hunter, Town Crier…). The Spy and Recluse should migrate onto the same
mechanism instead of the prose-only `misregistrations` list at `InfoCalc.kt:121` — one
model serves Spy, Recluse, Revolutionary, and Storyteller fiat.

Information / visibility:

- Public: the table is told the two seats are the same alignment. Offer a one-tap
  **"Announce the Revolutionary pair"** show card naming both seats.
- The pair members themselves are not told anything extra beyond their own characters.

Day-time inputs the app must record: none, but the **day briefing** should carry a
standing line while the token is unspent: *"Revolutionary: <A> & <B> are the same
alignment. False registration still available."*

Interactions to handle explicitly:

- **Seat changes** (`moveSeat`, `addSeat`, `removeSeat`, `GameActions.kt:18-40`) —
  re-validate adjacency and warn if the pair is split.
- **Character changes** (Pit-Hag, Barber, Snake Charmer, star pass) can flip one member's
  alignment; the public claim then becomes false. The app should surface this, not fix it:
  *"<A> is now evil and <B> is good — the Revolutionary's public claim no longer holds."*
- **Drunk / Lunatic / Marionette** on a pair member — alignment is unaffected; the pair
  claim still holds.
- **Spirit of Ivory** — a Revolutionary member turning evil counts towards that cap.
- No jinxes.

UI text:

- Setup step: `Revolutionary — pick two neighbouring seats who will play as a pair.
  They will draw the same alignment. Get both players' consent first, and tell the table.`
- Seat sheet on a marked seat: `Revolutionary pair with <other>. False registration:
  available / spent on <info> (night N).`

Data changes:

- `characters.json:2274` — replace `reminders: ["Used"]` with
  `reminders: ["Register falsely?", "Register falsely?"]` (or a single label plus a count),
  and change "one of them" to "1 of them".
- `night_guide.json` — add a `first` entry (or a setup-guide entry, if one is introduced)
  describing the constrained deal, since this is the step the ST most needs walking through.

## Tests to add

1. **Given** an 8-player game, `fabledIds = ["revolutionary"]`, pair = seats 2 and 3, and a
   legal TB bag, **when** the constrained deal runs 500 times,
   **then** in every deal `player(2).isEvil == player(3).isEvil`.
2. **Given** a bag with exactly one evil character and a pair,
   **when** the constrained deal runs, **then** it either produces a good-good pair or
   reports the "can't match alignment" failure, never a mismatched pair.
3. **Given** a pair on seats 2 and 5 (non-adjacent),
   **when** `validateSetupState` runs, **then** an issue mentioning "neighbours" is
   returned.
4. **Given** a dealt pair, **when** the game starts,
   **then** both seats carry `PlacedReminder("revolutionary","Register falsely?")`.
5. **Given** an Empath adjacent to a marked evil pair member, and the false registration
   applied to that member as `evil = false`,
   **when** `InfoCalc.compute("empath", …)` runs with the override,
   **then** the headline number is one lower than the un-overridden result.
6. **Given** the false registration is spent, **when** the state is inspected,
   **then** neither seat carries a REGISTER FALSELY? reminder and a second spend attempt
   is rejected.
7. **Given** a spent-and-then-undone false registration (`viewModel.undo()`),
   **then** both reminders are back and `falseRegistrationUsed == false`.
8. **Given** a pair split by `moveSeat`, **when** the state is validated,
   **then** an adjacency warning is produced.
