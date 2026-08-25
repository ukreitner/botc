# Marionette (marionette) — Experimental Minion

## Official rules (sources)

Source: https://wiki.bloodontheclocktower.com/Marionette (Character Text,
Summary, How to Run, Examples, Tips & Tricks, Jinxes), fetched 2026-08-25.

**Current ability text (quote):**
> "You think you are a good character, but you are not. The Demon knows who you are. [You neighbor the Demon]"

`characters.json:1821` matches verbatim. No drift.

**Summary bullets (quotes):**
- "The Marionette draws either a Townsfolk or an Outsider token from the bag, but is secretly the Marionette."
- "The Marionette neighbors the Demon. There are no players sitting in between the Marionette and the Demon."
- "The Demon knows which player is the Marionette."
- "On the first night, the Marionette does not wake to learn the other evil players, and the other Minions do not learn the Marionette."
- **"The good ability that the Marionette thinks they have doesn't work, but the Storyteller pretends it does. It is just as if this player is the Drunk."**
- "The Marionette registers as evil, and as a Minion."
- "The Marionette is not woken due to character abilities that would confirm that they are a Minion eg. Snitch, Preacher…"

**How to Run (quote):**
> "While setting up the game, before putting tokens in the bag, remove the Marionette token and add any Townsfolk token. … During the first night, mark a good player neighboring the Demon with the **IS THE MARIONETTE** reminder. Wake the Demon. Point to the player marked **IS THE MARIONETTE** and show the Marionette character token. Put the Demon to sleep. Treat the Marionette as if they were drunk. **They wake when their good character would wake, may get false information, do not wake during the Minion Info step** etc."

> **Answer to the audit question posed in the assignment:** the Marionette
> **does** wake — at the times their *believed* good character would wake, and
> gets false information there, exactly like the Drunk. They do **not** wake at
> the Minion Info step, do **not** wake for a Marionette-specific step, and are
> not woken by abilities that would confirm them as a Minion (Snitch, Preacher).
> The app's `Player.nightRoleId` (`GameState.kt:39-44`) implements this
> correctly.

> **Caveat on one fetched paragraph.** Two independent fetches of the How to Run
> section also returned a paragraph reading *"During the first night, swap a good
> player's character token with a not-in-play **Minion** character token. Wake
> this player, show them the YOU ARE info token then their Minion character
> token, then … a thumbs down… This player is now an evil Minion."* That text
> contradicts the character's core rule ("You think you are a good character")
> and every summary bullet, and it reads as boilerplate for a *different*
> minion-creating ability. **Do not implement it.** Someone with the printed
> almanac should confirm; this spec follows the unambiguous summary bullets.

**Jinxes (wiki, quotes):**
- Alchemist — "An Alchemist-Marionette has no Marionette ability & the Marionette is in play."
- Balloonist — "If the Marionette thinks that they are the Balloonist, an Outsider might have been added during setup."
- Huntsman — "If the Marionette thinks that they are the Huntsman, the Damsel was added during setup."
- Kazali / Lil' Monsta / Summoner — "If there would be a Marionette in play, they enter play after the Demon & must start as their neighbor."
- **Magician — "If the Magician is alive, the Demon doesn't know which neighbor is the Marionette."**
- Mathematician — "The Mathematician learns if the Marionette's ability yielded false info or failed to work properly."
- Plague Doctor — "If the Storyteller would gain the Marionette ability, one of the Demon's good neighbors becomes the Marionette."

**Night order.** First night index 21, immediately after DEMON_INFO (18), `king`
(19) and `sailor` (20) — `night_and_jinxes.json:316`. Correct. No other-night
entry — correct.

## What the app does today

**Data**
- `characters.json:1821` — correct ability, `setup: true`,
  `firstNightReminder`: "Wake the Demon. Show the 'This player is' & Marionette
  tokens. Point to the Marionette.", `remindersGlobal: ["Is The Marionette"]`.
- `night_guide.json:1382` — accurate first-night prose, explicitly saying the
  Marionette does not wake, and that they are drunk-like.
- `night_and_jinxes.json` — jinx rows present for `damsel` (:44), `balloonist`
  (:89), `poppygrower` (:94), `snitch` (:99), `huntsman` (:104), `lilmonsta`
  (:109), `summoner` (:115, as id2), `kazali` (:260, as id2). **Missing:
  Alchemist, Magician, Mathematician, Plague Doctor.**

**Engine**
- `GameState.kt:39-44` — `nightRoleId` returns `shownCharacterId` for `drunk`
  and `marionette`, so the Marionette appears on the night sheet as a holder of
  their believed character's step. **Works, and matches the rules.**
- `NightOrder.kt:60-80` (MINION_INFO) and `:81-119` (DEMON_INFO) both exclude
  `p.characterId != "marionette"` from the Minion list. **Works.**
- `NightOrder.kt:99-102` — DEMON_INFO appends "Point out the Marionette (name)".
  **Works** for 7+ player games.
- `NightOrder.kt:121-141` — for games with fewer than 7 non-travellers (no
  Minion/Demon info steps) a dedicated "Marionette info" step is synthesised;
  otherwise the `marionette` order entry is `continue`d.
- `GameActions.kt:525-546` (`validateSetupState`) — requires a not-in-play good
  `shownCharacterId` and that a Demon sits in one of the two adjacent seats
  (using all seats, so a Traveller in between correctly breaks adjacency).
  **Works** for standard games.
- `InfoCalc.kt:139-141` — `impairments()` adds "{name} IS the Marionette — their
  shown good ability has no effect; give arbitrary information."
- `Setup.modifierFor` yields a zero-delta modifier carrying the bracket text
  (`SetupTest.kt:122`). **Works.**
- `StatusEffects.isImpaired` (`StatusEffects.kt:36-46`) returns **false** for the
  Marionette (it special-cases only `characterId == "drunk"`).

**UI**
- `GameShell.kt:443-479` — a setup prompt "The Marionette is in play" offering
  not-in-play good characters, setting `shownCharacterId`, adding
  `PlacedReminder("marionette", "Is the Marionette")` and a seat note "Believes
  they are the X".
- `SeatSheet.kt:199-219` shows the "Shown to {name}" panel; `SeatSheet.kt:222-233`
  lists active jinxes involving the seat's character.

**Storyteller experience today:** setup asks which good token the Marionette
believes, validates adjacency, and drops a token. Night 1 the Demon is told who
the Marionette is (inside the Demon-info blurb). Each subsequent night the
Marionette shows up on their believed character's step with a red caveat telling
you to give arbitrary info — but, unlike the Drunk, the app does **not** offer
you the one-tap false answers, so you type/eyeball them yourself. Nothing
handles the Magician jinx, nothing stops the Snitch/Preacher trap, and in a
Lil' Monsta / Summoner / Kazali game the setup guard actively blocks a legal
setup.

## Defects and gaps

1. **P0 · Setup guard blocks legal Lil' Monsta / Summoner / Kazali games.**
   Rules (jinxes): with Lil' Monsta the Marionette neighbours **a Minion**; with
   the Summoner they neighbour the Summoner; with the Kazali they are created
   after the Demon picks. App: `GameActions.kt:540-545` unconditionally demands a
   Demon neighbour and emits "the Marionette must neighbor the Demon". Repro:
   Marionette + Lil' Monsta, seat the Marionette next to a Minion, press
   "Begin night" → the setup guard fires (`GameShell.kt:139-146`). Worse, with a
   Summoner there is **no Demon in play at all** on night 1, so the guard can
   never be satisfied.

2. **P1 · False-info shortcuts are suppressed for the Marionette.**
   `NightScreen.kt:904-906` decides whether to show the "False info to show
   instead:" chips by string-matching the caveats for
   `"POISONED"`, `"DRUNK"`, `"IS the Drunk"`, `"VORTOX"`, `"No Dashii"`.
   The Marionette caveat produced at `InfoCalc.kt:139-141` is
   `"… IS the Marionette — …"`, which matches **none** of them. Repro: Marionette
   believes they are the Empath; open the Empath step on night 2 — you get the
   true count in gold and a red caveat, but no 0/1/2 chips to show instead. The
   Drunk in the identical situation gets them. This is exactly the user's
   Lunatic complaint in another costume.

3. **P1 · `StatusEffects.isImpaired` does not treat the Marionette as drunk.**
   Rules: "It is just as if this player is the Drunk." App:
   `StatusEffects.kt:37` special-cases only `"drunk"`. Consequences:
   `DeathRecord.abilityImpairedAtDeath` is recorded wrong (`GameActions.kt:152`),
   and any future rule that keys off impairment (Mathematician, Vortox handling,
   "their ability didn't work") will get the Marionette wrong.

4. **P1 · Reminder-label mismatch creates duplicate tokens.**
   `GameShell.kt:460,466` uses the label `"Is the Marionette"` (lower-case *the*)
   while `characters.json:1821` declares `"Is The Marionette"`. The guard at
   `GameShell.kt:459-462` is a case-**sensitive** `==`. Repro: place "Is The
   Marionette" from the seat sheet's `ReminderPicker` (`SeatSheet.kt:558-564`,
   which uses `Character.allReminders`), then answer the setup prompt → the seat
   ends up with **two** near-identical tokens. Compare `GameShell.kt:400` where
   the Drunk has the same latent issue.

5. **P1 · Magician jinx unhandled.**
   Rules: "If the Magician is alive, the Demon doesn't know which neighbor is the
   Marionette." App: `NightOrder.kt:99-102` names the Marionette outright in the
   Demon-info step regardless. Repro: Magician + Marionette, 8 players — the
   night sheet tells the ST to point out the exact Marionette, breaking the jinx.
   The jinx row is not in `night_and_jinxes.json` either, so the Seat Sheet
   jinx list (`SeatSheet.kt:222-233`) and the Active Jinxes dialog show nothing.

6. **P2 · In 7+ player games the Marionette's own night-order row is dropped,
   taking its guide with it.**
   `NightOrder.kt:121-141` only synthesises a step when `!infoSteps`
   (fewer than 7 non-travellers). In every normal game the `marionette` entry is
   skipped (`NightOrder.kt:140`), so `night_guide.json:1382` — the only place
   that says "do NOT wake the Marionette", "treat them as drunk", "keep the Is
   The Marionette reminder" — is never displayed. The Demon-info blurb is one
   clause inside a long paragraph.

7. **P2 · No Snitch / Preacher / "confirms a Minion" guard.**
   Rules: "The Marionette is not woken due to character abilities that would
   confirm that they are a Minion." The Snitch jinx exists in data
   (`night_and_jinxes.json:99`) but only as text on the seat sheet; the Snitch
   night step (`night_and_jinxes.json` firstNight index 15) carries no warning
   and the app does not tell the ST which seats to wake for it.

8. **P2 · Missing jinx rows.** Alchemist, Magician, Mathematician, Plague Doctor
   are all absent from `night_and_jinxes.json`.

9. **P2 · Nothing surfaces "the Marionette does not learn X" at info steps.**
   When the Marionette believes they are the Balloonist/Huntsman/Damsel, the
   corresponding jinx changes what happened at **setup** (an Outsider may have
   been added, a Damsel was added). The ST sees the jinx text only if they open
   the Marionette's seat sheet.

10. **P3 · No prompt to double-check adjacency after a mid-game seat change.**
    `GameActions.moveSeat` (`GameActions.kt:33-41`) and `addSeat` can silently
    break the "no players in between" invariant; `validateSetupState` runs only
    at the SETUP→NIGHT boundary (`GameShell.kt:139-146`).

11. **P3 · The Demon-info blurb does not distinguish "Minions" from "the
    Marionette" clearly enough on a phone.** `NightOrder.kt:94-116` builds one
    long run-on sentence that also carries bluffs and the Lunatic note.

## Proposed behaviour (spec)

### Setup

- **when:** SETUP, a seat has `characterId == "marionette"`.
- **prompt (already exists, `GameShell.kt:443-479`):** keep, but
  - use the single canonical label `"Is The Marionette"` (match
    `characters.json:1821`) and compare case-insensitively;
  - offer only not-in-play Townsfolk/Outsider (already correct);
  - after picking, also **suggest the seat**: list the Demon's two neighbours
    and let the ST pick which one is the Marionette, rather than requiring a
    manual reseat.
- **validation (`GameActions.kt:525-546`) — replace the adjacency rule with:**
  ```
  anchor = when {
      any seat is "lilmonsta"      -> nearest seat whose team == MINION
      any seat is "summoner"       -> the summoner seat
      any seat is "kazali"         -> the kazali seat (adjacency is chosen at night 1)
      else                          -> the Demon seat
  }
  ```
  and require the Marionette to be adjacent to `anchor` (all seats counted,
  Travellers included). When the anchor does not exist yet (Summoner games
  before the Demon is created), downgrade to an advisory note rather than a
  blocking issue.
- Setup consequences of the **believed** character: if the believed character is
  the Huntsman, require a Damsel in play (Huntsman jinx); if it is the
  Balloonist, note that an Outsider may have been added. Surface these as setup
  advisories, not errors.

### Night 1

- **The Marionette does not wake.** Keep it out of MINION_INFO and out of any
  Minion-confirming step. (`NightOrder.kt:60-80` already correct.)
- **The Demon is shown the Marionette.** Always render a dedicated
  `marionette` step (drop the `!infoSteps` condition at `NightOrder.kt:122`) so
  the guide prose is reachable in every game size; keep the DEMON_INFO mention as
  a one-liner cross-reference. Step content:
  - `playerIds = demonSeats`
  - `detail = "Wake {Demon}. Show THIS PLAYER IS + the Marionette token, then point at {Marionette}."`
  - **Magician jinx:** if a `magician` seat is alive, replace with
    `"{Magician} is alive — show the Demon BOTH of their neighbours ({a} and {b}); do not reveal which is the Marionette."`
    and set `playerIds` accordingly.
  - **Poppy Grower jinx** (already in data): if a living Poppy Grower is in play,
    suppress the step and add `"Poppy Grower is alive — the Demon does not learn
    the Marionette yet; do it when the Poppy Grower dies."`
- **tokens:** `PlacedReminder("marionette", "Is The Marionette")` on the
  Marionette seat, exclusive, **never expires**.

### Every night thereafter

- The Marionette wakes at their believed character's step (already correct via
  `nightRoleId`). On that step:
  - `InfoCalc.impairments` caveat becomes
    `"{name} IS the Marionette — treat exactly as the Drunk: their ability does not work. Give false information."`
    (i.e. include the word **DRUNK** so the existing gate matches — but better,
    fix the gate itself, below).
  - Fix `NightScreen.kt:904-906` to stop string-sniffing: have
    `InfoCalc.InfoResult` carry a boolean `abilityMalfunctions` set by
    `impairments()`, and gate the false-info chips on that.
  - `StatusEffects.isImpaired` (`StatusEffects.kt:36-46`) must return `true` for
    `characterId == "marionette"` alongside `"drunk"`.
- **The Marionette must never be woken** by Snitch, Preacher, or any step that
  wakes "all Minions". Concretely: any step whose detail enumerates Minions must
  use the same `characterId != "marionette"` filter already used at
  `NightOrder.kt:62,83`, and the Snitch step must gain the jinx text inline.

### Registration

- `Player.isEvil` already returns true (team MINION, `GameState.kt:49-52`) — the
  Marionette correctly counts as evil for the Empath, Chef, Fortune Teller, and
  for `WinCheck`. **Works; add regression tests.**
- The Marionette counts as a Minion for the Vigormortis, Minstrel, Scarlet Woman
  and for the Imp star-pass heir list (`NightScreen.kt:614-625`) — already true
  because those check `team == MINION` on the real `characterId`. **Works.**

### Death / character change

- If the Marionette becomes the Demon (Imp star pass), `GameActions.starPass`
  (`GameActions.kt:78-96`) sets `characterId` and clears `shownCharacterId`
  — correct. Add: remove the "Is The Marionette" reminder, and surface
  `"{name} was the Marionette and is now the Demon — wake them and show the new
  token; they finally learn the truth."`
- If the Demon dies and a new Demon appears elsewhere, the Marionette's
  adjacency is no longer required (it is a setup constraint only). Do not
  re-validate.

### Day-time inputs

None specific to the Marionette. (But the Marionette will make claims based on
false info — the generic "record a claim" facility recommended across this audit
covers it.)

### UI text

- Marionette step (normal): `Wake {Demon} only. THIS PLAYER IS + Marionette token → point at {Marionette}. Do NOT wake {Marionette}.`
- Marionette step (Magician alive): `Magician is alive — show {Demon} both neighbours ({a}, {b}); don't reveal which.`
- Believed-character step banner: `{name} is the Marionette — treat as the Drunk. Give false info.`

### Data changes

- `night_and_jinxes.json`: add Alchemist, Magician, Mathematician, Plague Doctor
  jinx rows with the wiki texts quoted above.
- `characters.json:1821`: keep `"Is The Marionette"`; fix the UI to match.
- `night_guide.json:1382`: add the Magician and Poppy Grower branches, and the
  explicit "never wake for Snitch/Preacher/Minion info" line.

## Tests to add

1. *Given* Marionette believing they are the Empath, *when*
   `NightOrder.otherNight` is built, *then* the Empath step's `playerIds`
   contains the Marionette seat and no `marionette` step exists on other nights.
2. *Given* an 8-player game with a Marionette, *when* `NightOrder.firstNight` is
   built, *then* a step with `id == "marionette"` **exists** (currently it does
   not) and MINION_INFO's `playerIds` excludes the Marionette seat.
3. *Given* a living Magician, *then* the `marionette` step names both of the
   Demon's neighbours and does not name the Marionette alone.
4. *Given* a Marionette, *then* `StatusEffects.isImpaired(marionetteSeat)` is
   `true`.
5. *Given* a Marionette believing they are the Empath, *when*
   `InfoCalc.compute("empath", marionetteSeatId)` runs, *then* the result's
   `abilityMalfunctions` flag is true (so the UI offers false-info chips).
6. *Given* a Marionette + Lil' Monsta with the Marionette adjacent to a Minion
   and not to any Demon, *then* `validateSetupState` produces **no** adjacency
   issue.
7. *Given* a Marionette + Summoner with no Demon in play, *then*
   `validateSetupState` produces no blocking adjacency issue.
8. *Given* a Traveller seated between the Marionette and the Demon, *then*
   `validateSetupState` **does** report the adjacency issue (regression for the
   current, correct behaviour).
9. *Given* the setup prompt has run, *then* the Marionette seat holds exactly one
   reminder whose label equals `"Is The Marionette"` (case-insensitively), even
   if the ST had already placed one from the seat sheet.
10. *Given* an Empath adjacent to the Marionette, *then* `InfoCalc.empath`
    counts the Marionette as evil.
11. *Given* an Imp star-passes to the Marionette, *then* the new Demon seat has
    `characterId == "imp"`, `shownCharacterId == null`, and no "Is The
    Marionette" reminder.
