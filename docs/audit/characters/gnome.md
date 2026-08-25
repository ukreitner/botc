# Gnome (gnome) — Experimental Traveller

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Gnome> (revealed 01/11/2024)

Current ability text (wiki, matches `characters.json:2122`):

> "All players start knowing a player of your alignment. You may choose to kill anyone who
> nominates them."

Summary clarifications (quoted in full):

> - "The Gnome starts as the same alignment as one other player - their 'amigo'. The
>   Storyteller publicly announces which player this is."
> - "When their amigo is nominated, it is the Gnome's responsibility to speak up. **The
>   Storyteller may not prompt them to use their ability.**"
> - "If their amigo changes alignment, the Gnome's alignment does not change."
> - "The Gnome may use their ability any number of times over the course of the game,
>   including zero. Their amigo may still only be nominated once per day."
> - "When the Gnome uses their ability, and the Storyteller confirms it, the nominator dies
>   immediately. Voting for execution still occurs."
> - "Regardless of what the group wants, it is always the individual player's decision
>   whether they wish to nominate or not, and always the Gnome player's decision on whether
>   they wish to use their ability or not. If the Storyteller feels that a player is being
>   pressured into nominating or using their ability when they don't want to, the Storyteller
>   may not recognize that nomination or ability use."

How to Run (quoted in full):

> "During the day, as soon as the Gnome has entered play, mark a player of the same
> alignment with the **AMIGO** reminder. Declare that this player is the same alignment as
> the Gnome.
>
> If this player is nominated, but before you have started the voting process, if the Gnome
> player declares that they wish to use their ability, the nominator **dies**."

Examples (quoted in full):

> "The Gnome starts the game at the same time as the rest of the players, and is good. Amy
> is the Alsaahir. Before the first night, the Storyteller announces that the Gnome is the
> same alignment as Amy. The Engineer nominates Amy on day 3. The Gnome does not use their
> ability.
>
> The Gnome enters the game on the 2nd day, and is evil. Lewis is the Demon. At this time,
> the Storyteller announces that the Gnome is the same alignment as Lewis. On the 2nd day,
> the Boffin nominates Lewis, and is killed by the Gnome. On the 3rd day, the Zealot
> nominates Lewis, and is killed by the Gnome. On the 4th day, the Village Idiot nominates
> Lewis, and the Gnome does not use their ability."

Consequences that matter for the app:

- **The announcement is a DAY-time entry event, not a night step.** "As soon as the Gnome has
  entered play" — before the first night if the Gnome starts the game, otherwise on the day
  they arrive. It is **public**: all players learn who the amigo is.
- **The amigo's alignment matches the Gnome's at the moment of the announcement**, and is
  fixed there: "If their amigo changes alignment, the Gnome's alignment does not change."
  (The AMIGO token stays where it is.)
- **The trigger window is precise**: after the nomination is declared, **before voting
  starts**. The nominator dies immediately, and **the vote still happens** — the nomination
  is not cancelled, the nominee can still be executed.
- **The Storyteller may NOT prompt the Gnome.** This is unusual and directly constrains the
  app's UI: any Gnome affordance must be storyteller-private and must not be phrased as a
  prompt to be relayed to the table. The app should show the fact (the amigo was nominated)
  and be ready to apply the consequence, not nag.
- **Unlimited uses**, including zero. There is no once-per-day or once-per-game limit on the
  Gnome's side. The only limit is the ordinary one-nomination-per-nominee-per-day rule
  ("Their amigo may still only be nominated once per day"), so at most one trigger per day
  in practice.
- The Gnome may nominate their own amigo and kill themselves (evil Tips).
- Only **one** AMIGO token. The Gnome has no other tokens.
- No jinxes on the Gnome page.

## What the app does today

Data:
- `characters.json:2117-2129` — correct ability text;
  `firstNightReminder: "Publicly announce which player is of the same alignment as the
  Gnome."`; `otherNightReminder: ""`; `reminders: ["Amigo"]`.
  **Drift:** the current official role data gives the Gnome **no** night reminder at all —
  the announcement is a day/entry action. The app has modelled it as a first-night step.
- `night_and_jinxes.json:370` — `gnome` is the last entry of `firstNight`, placed **after**
  `DAWN` (alongside `leviathan` and `vizier`). This is a defensible rendering of "announce
  publicly at the start of day 1", but it only ever fires on night 1.
- `night_guide.json:1688-1700` — a `first` entry only, with sensible prose ("At dawn (or
  when the Gnome enters play), publicly announce a player who is the same alignment as the
  Gnome and mark them with the Amigo reminder… During the day, if anyone nominates the
  Amigo, the Gnome may choose to kill that nominator immediately.") and one message show
  card ("THIS PLAYER SHARES THE GNOME'S ALIGNMENT…").

Code: **no Gnome-specific code anywhere.** `grep -rn gnome engine/src app/src` returns only
the data files.

Storyteller's actual experience:
1. **Only if the Gnome is in play on night 1**: a "Gnome" row appears at the very bottom of
   the first-night sheet, after Dawn (`NightOrder.kt:142-178`). Expanding it shows the guide
   prose and the announcement card. The ST then places the "Amigo" token via the tray
   (`NightScreen.kt:282-300`).
2. For the rest of the game: nothing. When the amigo is nominated,
   `StatusEffects.nominationWarnings` (`StatusEffects.kt:131-166`) produces no Gnome entry,
   so `DayScreen.kt:154-159` shows nothing. The ST must notice the token themselves, wait
   for the Gnome to speak, and then kill the nominator by opening their seat and pressing
   "Other death" → `DeathCause.STORYTELLER` (`SeatSheet.kt:277-279`).

Works: the "Amigo" reminder label exists and can be placed; the announcement show card
exists; the first-night placement is at least in the right part of the sheet.

Shared traveller-lifecycle defects **T1–T7** apply — see `barista.md`. **T1 is especially
severe here**: the Gnome's whole ability is defined relative to their alignment, and the app
never asks for it.

## Defects and gaps

1. **P0 · A Gnome who joins mid-game never gets the announcement step.** The step lives only
   in the **first-night** order (`night_and_jinxes.json:370`), and
   `NightOrder.firstNight` is only built when `state.cycle == 1`
   (`NightScreen.kt:82-88`). Travellers overwhelmingly arrive mid-game — the wiki's own
   second example has the Gnome entering on day 2 — so in the common case the app gives the
   ST nothing at all: no prompt to pick an amigo, no announcement card, no token guidance.
   *Repro:* on day 3, add a seat, assign the Gnome. Nothing happens anywhere in the app.

2. **P0 · Nothing fires when the amigo is nominated.**
   `StatusEffects.nominationWarnings` (`StatusEffects.kt:131-166`) covers the Witch's
   Cursed, the Golem, the Virgin and the Fearmonger's Fear, but has no Gnome branch. The
   Gnome's entire day ability is invisible at exactly the moment it matters.
   *Repro:* place the "Amigo" token, select that player as Nominee in the Day tab
   (`DayScreen.kt:141-152`) → no warning is shown.

3. **P0 · The alignment relationship is never established or checked.** The AMIGO must be a
   player of the **same alignment as the Gnome**. Since a traveller's alignment is never
   recorded (T1 — `Player.isEvil`, `GameState.kt:49-52`, defaults travellers to good), the
   app cannot validate the choice, cannot suggest candidates, and cannot warn when the
   chosen amigo is the wrong alignment.

4. **P0 · The kill has no correct path.** As with the Gangster, a daytime ability kill can
   only be recorded as `DeathCause.STORYTELLER` (`SeatSheet.kt:277-279`,
   `GameState.kt:75`), losing the cause. Worse, the kill must happen **before voting
   starts** and the vote must then proceed — the app's nomination card
   (`DayScreen.kt:161-252`) is a single form with no "kill the nominator, then continue
   voting" seam, and killing the nominator mid-form changes `alivePlayers.size` and
   therefore the execution threshold (`DayScreen.kt:71-72`) for that very vote.

5. **P1 · The threshold consequence is unhandled and easy to get wrong.** The nominator
   dies *before* the vote, so the vote is tallied against the reduced living count. The app
   recomputes `Voting.executionThreshold(state.alivePlayers.size)` live
   (`DayScreen.kt:71-72`), so it will do the right thing **if** the kill is applied before
   the votes are recorded — but nothing sequences that, and nothing tells the ST.
   The dead nominator also loses their own vote in that tally (they get a ghost vote as a
   dead player — `GameActions.kill` sets `ghostVoteUsed = false`, `GameActions.kt:145`) —
   worth surfacing.

6. **P1 · The "do not prompt the Gnome" rule is not respected by the design.** Any
   implementation must be careful: a big red banner the ST reads aloud, or a modal that
   blocks the nomination until answered, would break the rule. The affordance must be
   passive and ST-private.

7. **P1 · The AMIGO token has no lifetime rules.** It should never expire (it is not in
   `EXPIRES_AT_DAWN`/`EXPIRES_AT_DUSK` — correct today by accident), it is one-of-a-kind
   (should use `placeExclusiveReminder`, `GameActions.kt:194-201`, but the tray uses
   `addReminder`, `GameActions.kt:186-187`, so two amigos can be marked), and it must stay
   in place even if the amigo changes alignment or dies.

8. **P2 · Modelling the announcement as a night step is wrong in principle.**
   `characters.json:2123` sets a `firstNightReminder`, and `night_and_jinxes.json:370`
   places `gnome` in the first-night order. The official data has no night entry. It happens
   to render acceptably (after DAWN) for a night-1 Gnome, but it is the reason for defect 1.

9. **P2 · No record of which nominations the Gnome declined.** The wiki's second example is a
   sequence of choices across days; a ST tracking a suspicious Gnome wants that history. The
   game log (`GameExtras.kt:46-77`) records nominations and deaths but has no notion of a
   Gnome trigger fired or declined.

10. **P3 · Announcement card text.** `night_guide.json:1693-1697` reads "THIS PLAYER SHARES
    THE GNOME'S ALIGNMENT…" — fine, but it should name the player and be reusable at any
    point in the game, not only at dawn of day 1.

## Proposed behaviour (spec)

The Gnome has **no night step**. Two day-time behaviours:

### A. Entry announcement (fires when the Gnome enters play)

- **when**: the moment a seat is assigned the `gnome` character (setup or mid-game). If the
  Gnome is present at setup, the announcement is made before the first night; otherwise on
  the day they arrive.
- **prerequisite**: the Gnome's **alignment must be chosen first** (T1). The entry flow must
  ask "Is this traveller good or evil?" and record it (`alignmentFlipped`,
  `GameState.kt:25`, or better a dedicated `travellerAlignment` field).
- **targets**: exactly 1 amigo. Constraint: **same alignment as the Gnome**, per
  `Player.isEvil(lookup)` (`GameState.kt:49-52`). The picker must list only same-alignment
  players by default (with an "override" escape hatch and a warning), sorted living-first.
  For an evil Gnome the Demon and Minions are the candidates; for a good Gnome, every good
  player.
- **immediate effects**:
  `placeExclusiveReminder(amigo, PlacedReminder("gnome", "Amigo"))` — exclusive, so a second
  choice moves the token.
- **information**: a full-screen public announcement card:
  `<Amigo> is the same alignment as the Gnome (<GnomeName>).`
  Reuse `ShowCard.Message` (`ShowCards.kt:66`).
- **visibility**: **public — all players**, including the Demon. This is the one traveller
  effect that is broadcast.
- **expiry**: never. The token survives the amigo's death, any alignment change of the amigo,
  and any character change.

### B. Nomination trigger

- **when**: day phase, at the moment a nomination against the amigo is declared, **before
  voting begins**. Unlimited uses.
- **wake condition**: the Gnome is **alive** (or holds `("bonecollector","Has Ability")`),
  the nominee holds `("gnome","Amigo")`, and the nominator is not already dead.
- **immediate effects** (ST-confirmed, never prompted):
  the **nominator dies immediately**, recorded as a day-time ability death
  (`DeathCause.DAY_ABILITY` / `sourceCharacterId = "gnome"`; see `gangster.md`), before any
  votes are recorded. The nomination then proceeds: votes are tallied against the **new**
  living count, and the dead nominator votes only with their ghost vote if they choose.
- **deferred effects**: none.
- **expiry**: none.
- **information**: none to anyone.
- **day-time inputs the app must record**: whether the Gnome used or declined the ability on
  each triggering nomination, for the game log.
- **interactions/edge cases to handle explicitly**:
  - The Gnome may nominate their own amigo and then kill themselves.
  - The amigo may be nominated only once per day, so at most one trigger per day.
  - **Exile** calls against the amigo are not nominations — if the amigo is a traveller, an
    exile call must **not** trigger the Gnome (`DayScreen.kt:161-164` already separates the
    two paths; keep them separate).
  - Killing the nominator does not withdraw or cancel the nomination.
  - `StatusEffects.deathNotes(..., cause = DAY_ABILITY)` must run for the nominator:
    Sailor / Tea Lady / Innkeeper "Protected" / Fool / Lleech / Zombuul apply; Monk "Safe"
    and Soldier (Demon-only) and Devil's Advocate (execution-only) do not.
  - If the nominator is the **Virgin's** nominator, or a Witch-cursed nominator, several
    death triggers can stack in one nomination — surface all of them together
    (`StatusEffects.nominationWarnings`, `StatusEffects.kt:131-166`).
  - Gnome drunk/poisoned: surface `isImpaired` (`StatusEffects.kt:36-46`) so the ST can
    decline the kill knowingly.
  - Barista ACTS TWICE on the Gnome: the ability is not "once per" anything, so doubling is
    a no-op; say so rather than leaving the ST guessing.
  - A dead amigo can still be nominated? No — `DayScreen.kt:146` correctly restricts
    nominees to the living, which matches the rules.

### Implementation shape

1. Add a Gnome branch to `StatusEffects.nominationWarnings` (`StatusEffects.kt:131-166`),
   keyed on the nominee holding `("gnome","Amigo")` and a living Gnome existing:
   ```
   notes += "<Gnome> is the Gnome and <Nominee> is their AMIGO. Do NOT prompt them. " +
            "If the Gnome declares before voting starts, <Nominator> dies immediately — " +
            "then run the vote against the reduced living count."
   ```
2. `DayScreen` gains, next to the nomination warnings (`DayScreen.kt:154-159`), a small
   ST-private `Gnome kills <Nominator>` action that applies the death and leaves the vote
   form open with the recomputed threshold.
3. Traveller entry flow (T1/T2) gains a Gnome-specific follow-up: choose alignment → choose
   amigo → announce.
4. Move the Gnome out of the first-night order; drive the announcement off "the Gnome is in
   play and no AMIGO token is placed", surfaced as a persistent day-tab task.

### UI text

- Entry task (Day tab, persistent until done):
  `Gnome (<Name>) — no AMIGO yet. Pick a player of the Gnome's alignment and announce them
  publicly.`
- Announcement card: `<Amigo> is the same alignment as the Gnome.`
- Nomination-time note (ST-private, quiet styling, never a modal):
  `<Nominee> is the Gnome's AMIGO. Do not prompt the Gnome. If they declare before voting,
  <Nominator> dies now and the vote still happens.`
  with a single `<Nominator> dies (Gnome)` button.
- After the kill: `<Nominator> died. Votes now need <newThreshold> (one fewer player alive).
  <Nominator> may still use their ghost vote.`

### Data changes

- `characters.json:2123`: clear `firstNightReminder` (the Gnome has no night action) — or,
  if the first-night row is kept as a convenience, retitle it "Gnome — announce the amigo
  (day action, shown here for a night-1 Gnome)".
- `night_and_jinxes.json:370`: remove `gnome` from `firstNight` once the day-entry task
  exists; until then, keep it (it is better than nothing for a night-1 Gnome).
- `night_guide.json:1688-1700`: move to the proposed `day_guide.json` and add
  "The Storyteller may NOT prompt the Gnome.", "The nominator dies before voting, and the
  vote still happens.", "Unlimited uses.", "If the amigo changes alignment, nothing changes."
- `characters.json:2126-2128`: reminders stay `["Amigo"]` — correct.

## Tests to add

1. `Given` a Gnome added on day 3 `When` the app's outstanding day tasks are computed
   `Then` an "announce the Gnome's amigo" task is present. *(Fails today: nothing exists.)*

2. `Given` an evil Gnome `When` amigo candidates are computed
   `Then` only evil players are offered by default. *(Fails today.)*

3. `Given` a nominee holding `("gnome","Amigo")` and a living Gnome
   `When` `StatusEffects.nominationWarnings(state, lookup, nominatorId, nomineeId)`
   `Then` a Gnome warning naming the nominator is produced. *(Fails today: none.)*

4. `Given` the same setup but the Gnome is dead and has no restored ability
   `Then` no Gnome warning is produced.

5. `Given` an exile call against a traveller amigo
   `Then` no Gnome warning is produced (exiles are not nominations).

6. `Given` 9 alive (execution threshold 5) and a Gnome kill of the nominator
   `When` the kill is applied `Then` `alivePlayers.size == 8` and the threshold for the
   in-progress vote is 4.

7. `Given` a Gnome kill `When` the death is recorded `Then` its cause identifies the Gnome
   (not `STORYTELLER`) and `atNight == false`.

8. `Given` a Gnome kill whose nominator is a Sailor
   `When` `deathNotes(..., cause = DAY_ABILITY)` `Then` "The Sailor can't die." is produced;
   `Given` the nominator is a Soldier `Then` the Demon-only note is **not** produced.

9. `Given` an amigo whose alignment is later flipped
   `Then` the `("gnome","Amigo")` token is still on them and the Gnome's own alignment is
   unchanged.

10. `Given` a second amigo choice `When` applied
    `Then` only one `("gnome","Amigo")` token exists in the grimoire.
