# Deviant (deviant) — Sects & Violets Traveller

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Deviant>
Exile definition: <https://wiki.bloodontheclocktower.com/Glossary>

Current ability text (wiki, matches `characters.json:1172`):

> "If you were funny today, you cannot die by exile."

Summary clarifications (quoted):

> - "The Deviant can amuse the group in any way they choose. Generally, verbal means such as
>   jokes, funny stories, or witty remarks will suffice."
> - "The Storyteller is the judge of whether the Deviant was funny or not."

How to Run (quoted in full):

> "If the Deviant would be exiled, you may declare that the Deviant remains alive.
>
> It is best to be forgiving and treat even a slightly funny Deviant as funny. It can be
> tough to be funny when one is expected to be, after all."
>
> "If the player would prefer, you may determine different criteria for whether the Deviant
> is exiled. If being 'funny' is difficult, you may reward the Deviant who 'creates a
> positive mood' or 'is helpful to others' instead. The Deviant is not a serious character,
> and it is meant to encourage laughter, lightheartedness, and fun, so adjust the Deviant
> rules to your players' needs and talents."

Examples (quoted in full):

> "The evil Deviant cracks a few jokes, and gets a few laughs, but the players nevertheless
> decide to exile them. Even though there are enough votes, the Storyteller decides to keep
> the Deviant alive.
>
> On the third day, the Deviant was slightly funny, and cannot be exiled. On the fourth day,
> the Deviant was not very funny, and is successfully exiled."

Glossary (relevant):

> "**Exile:** The group decision to kill a Traveller during the day. There may be any number
> of exiles per day, including none. Any players may support an exile, even dead players
> without a vote token. **Abilities cannot affect an exile decision in any way.** Though an
> exile is similar to a vote for execution, the process is not a vote, and an exile is not
> an execution."

Consequences that matter for the app:

- **The Deviant is the exception to "abilities cannot affect an exile decision".** The exile
  *decision* still happens normally — the votes are counted, the exile passes — the Deviant
  simply does not *die* from it. The wiki's example is explicit: "Even though there are
  enough votes, the Storyteller decides to keep the Deviant alive."
- **It is a per-day judgement by the Storyteller**, made afresh each day, based on that day's
  behaviour. Day 3 funny → survives; day 4 not funny → dies. The judgement is therefore
  *state the app must let the ST record during the day*, before the exile is called.
- **The ST is told to be forgiving** ("treat even a slightly funny Deviant as funny").
- **The criteria are negotiable per player** (funny / positive mood / helpful) — the app
  must let the ST rename the criterion for this game.
- The Deviant only resists **exile**. It does nothing against execution, the Demon, the
  Gangster, the Harlot, the Gnome, or any other death.
- It is not a "once per game" ability — it applies on every day the Deviant was funny.
- No night action, no reminder tokens, no jinxes.

## What the app does today

Data:
- `characters.json:1167-1178` — correct ability text; no night reminders; `reminders: []`
  (matches the printed character, which has no tokens).
- `night_and_jinxes.json` — correctly absent from both night orders.
- `night_guide.json` — no entry (there is no day-guide mechanism).

Code: **no Deviant-specific code anywhere.** `grep -rn deviant engine/src app/src` returns
only `characters.json` and `raw_sv_travellers_fabled.json`.

Storyteller's actual experience: the exile path is
`DayScreen.kt:161-164` (isExile derived from `nominee.isTraveller`) →
`DayScreen.kt:197-202` (exile passes on `votes >= exileThreshold`) →
`DayScreen.kt:319-321` (`executable` for a passed exile) →
`DayScreen.kt:350-357` → `viewModel.kill(id, DeathCause.EXILE)`. Nothing between the
passing tally and the death mentions the Deviant. The seat sheet's protection dialog
(`SeatSheet.kt:325-345`) is not on this path at all — it only guards the *seat sheet's* kill
buttons, and `StatusEffects.deathNotes` (`StatusEffects.kt:52-129`) has no Deviant branch
anyway.

Works: the exile threshold (`Voting.exileThreshold`, `GameState.kt:139`), any-number-of-
exiles-per-day (`hasBeenNominatedToday` filters exiles out, `GameActions.kt:288-289`), dead
players may support an exile (`DayScreen.kt:184`), ghost votes are not spent on exiles
(`DayScreen.kt:233-240`), and exiles are kept out of the execution block
(`GameActions.kt:280`, `:298`).

Shared traveller-lifecycle defects **T1–T7** apply — see `barista.md`.

## Defects and gaps

1. **P0 · The exile button kills the Deviant with no Deviant check.**
   `DayScreen.kt:350-357` calls `viewModel.kill(nomineeId, DeathCause.EXILE)` directly.
   Nothing asks "was the Deviant funny today?", nothing offers "exile passes but they
   survive". The one rule the character has is entirely absent.
   *Repro:* seat a Deviant, call an exile, tap "Exile" → they die, funny or not.

2. **P0 · `StatusEffects.deathNotes` has no Deviant entry**
   (`StatusEffects.kt:52-129`). Every other survive-this-death ability is listed there
   (Sailor `:73`, Soldier `:74`, Fool `:75-77`, Tea Lady `:69`/`:81-90`, Devil's Advocate
   `:68`, Lleech `:78`, Zombuul `:119-121`). The Deviant belongs in the same place, scoped
   to `DeathCause.EXILE`. Note that `deathNotes` currently has no `cause` parameter, so the
   note would need to be either cause-scoped (preferred) or worded conditionally.

3. **P0 · The exile path never consults `deathNotes` at all.** Even once a Deviant note
   exists, `DayScreen.kt:350-357` and the block-banner Execute button (`DayScreen.kt:111-114`)
   bypass the protection confirmation dialog that the seat sheet has
   (`SeatSheet.kt:258-345`). Any protection surfaced there is invisible from the Day tab.

4. **P1 · There is nowhere to record "the Deviant was funny today".** The judgement is made
   during the day, possibly hours before the exile is called, and must survive tab switches
   and undo. The only storage today is `GameState.storytellerNotes` (`GameState.kt:112`) —
   one shared free-text blob — or the per-player `note` (`GameState.kt:31`), neither of
   which is day-scoped, prompted, or surfaced at the moment of the exile.

5. **P1 · No day-start prompt.** Since the ability is re-judged every day, the app should
   ask once per day (at day start, or the first time the Deviant is involved in anything)
   and then carry the answer to the exile moment. Nothing in the app has a day-start
   briefing surface at all — `advancePhase` (`GameActions.kt:258-263`) only sweeps tokens.

6. **P1 · The house-rule criterion cannot be captured.** The wiki explicitly invites the ST
   and the Deviant player to agree on "creates a positive mood" or "is helpful to others"
   instead of "funny". The prompt text must be editable per game and persisted.

7. **P2 · The distinction "the exile succeeded but the Deviant survives" is not
   representable.** The `Nomination` record (`GameState.kt:62-72`) stores
   `result = ABOUT_TO_DIE` and the game log (`GameExtras.kt:64-77`) will show "reached the
   block" with no death — indistinguishable from an exile the ST simply forgot to apply.
   There should be an explicit "exile survived (Deviant)" outcome in the record and log.

8. **P2 · Nothing tells the ST that the Deviant's protection is exile-only.** The seat sheet
   shows the raw ability string (`SeatSheet.kt:195-197`) and nothing more; a tired ST could
   read "cannot die" too broadly. The Deviant must still die to execution, the Demon, the
   Gangster, the Harlot and the Gnome.

9. **P3 · No day-guide text.** As with the Butcher/Gangster/Gnome, the character's
   How-to-Run ("be forgiving", "adjust the criteria") has nowhere to live because
   `night_guide.json` is night-only (`NightGuide.kt:56-59`).

## Proposed behaviour (spec)

The Deviant has no night step. Structured form:

- **when**: day phase; evaluated at the moment an exile against the Deviant would kill them.
- **targets**: none.
- **immediate effects**: none.
- **deferred effects**: at the exile-resolution moment, if the "funny today" flag is set,
  the exile **passes** (the record stays `ABOUT_TO_DIE`, marked `survived = true`) and the
  Deviant does **not** die. The ST retains the final say — this is explicitly a
  "you may declare" ability, so the app offers, never forces.
- **expiry**: the "funny today" flag is **day-scoped** — cleared on every DAY→NIGHT
  transition, alongside `EXPIRES_AT_DUSK` (`GameActions.kt:261`).
- **information**: none.
- **visibility**: nothing shown to anyone. The Deviant does not learn the ST's judgement in
  advance (and per the wiki's tone, may well not know until they survive).
- **day-time inputs the app must let the ST record**:
  - a per-day boolean `deviantFunnyToday` (per Deviant seat, in case of two Deviants);
  - a per-game, editable criterion string, default `"funny"` (alternatives offered:
    "created a positive mood", "was helpful to others").
- **interactions/edge cases to handle explicitly**:
  - **Execution**, not exile: no protection.
  - **Gangster**, **Harlot**, **Gnome**, Demon kill, Assassin, Godfather, Witch curse:
    no protection.
  - A Deviant who is **drunk or poisoned** (Barista's SOBER & HEALTHY is the reverse case):
    the wiki does not address it; the Glossary's "abilities cannot affect an exile decision"
    plus the fact that the *Storyteller* declares the survival means the pragmatic reading is
    that a poisoned Deviant may be exiled normally. **Flagged as uncertain** — the app
    should surface the impairment (`StatusEffects.isImpaired`, `StatusEffects.kt:36-46`) in
    the prompt and let the ST decide, not decide for them.
  - **Multiple exiles per day**: the flag is not consumed by a survived exile. The group may
    call for exile again the same day and, if the ST still judges them funny, the Deviant
    survives again.
  - A Deviant with `("bonecollector","Has Ability")` while dead is moot (they are already
    dead); note it is a no-op.

### Implementation shape

1. `GameState` gains a day-scoped map, e.g.
   `val dayFlags: Map<String, String> = emptyMap()` keyed
   `"deviantFunny:<playerId>"` (or a typed `DayJudgements` record), cleared in
   `advancePhase` on DAY→NIGHT (`GameActions.kt:261-262`).
2. `GameState`/`Script` gains `val deviantCriterion: String = "funny"` (per-game setting).
3. `StatusEffects.deathNotes` gains a `cause: DeathCause? = null` parameter and, for
   `EXILE`:
   `"Deviant: if they were <criterion> today, you may declare they survive the exile."`
   plus, when the flag is set, `"You marked <Name> <criterion> today."`
4. `DayScreen`'s exile button (`DayScreen.kt:350-357`) routes through the same protection
   confirmation dialog pattern the seat sheet uses (`SeatSheet.kt:325-345`), with buttons
   **"They die anyway"** / **"They survive (Deviant)"**. Choosing survive records
   `Nomination.survived = true` (new field on `GameState.kt:62-72`) and writes nothing to
   `deaths`.
5. Day-start briefing (new surface, shared with Butcher/Gangster/Gnome/Professor):
   `Deviant (<Name>): decide today whether they were <criterion>.` with `Yes` / `Not today`
   chips that set the flag, plus a persistent chip in the Day tab header showing the current
   state so it can be changed any time before the exile.

### UI text

- Day-start card: `Deviant — <Name>. Were they <criterion> today? You judge; be forgiving,
  even slightly <criterion> counts. If yes, they cannot die by exile today.`
- Exile confirmation: `<Name> is the Deviant and you marked them <criterion> today. The
  exile still passes — but you may declare they remain alive.`
  Buttons: `They survive` · `They die anyway`
- If the flag is not set: `You have not judged <Name> <criterion> today. Exiling them will
  kill them.` with an inline `Mark them <criterion>` shortcut.
- Seat sheet caption: `Protects against EXILE only — not execution, the Demon, the
  Gangster, the Harlot or the Gnome.`

### Data changes

- `characters.json:1167-1178`: no change (text and empty token list are correct).
- Add to the proposed `day_guide.json` (see `butcher.md`):
  > "If the Deviant would be exiled, you may declare they remain alive. Be forgiving — even
  > slightly funny counts. You may agree a different criterion with the player ('creates a
  > positive mood', 'is helpful to others'). Judge fresh every day."

## Tests to add

1. `Given` a Deviant traveller with `deviantFunny=true` for day 3 and a passing exile
   `When` the exile is resolved with "They survive"
   `Then` the Deviant is still alive, `deaths` is unchanged, and the nomination record is
   `ABOUT_TO_DIE` with `survived = true`. *(Fails today: no such path.)*

2. `Given` a Deviant with `deviantFunny=false` `When` the exile is resolved
   `Then` the survive option is not offered as the default and the Deviant dies with
   `DeathCause.EXILE`.

3. `Given` `deviantFunny=true` on day 3 `When` `advancePhase` DAY→NIGHT
   `Then` the flag is cleared, and it is still cleared at the start of day 4.
   *(Fails today: no flag exists.)*

4. `Given` a Deviant `When` `StatusEffects.deathNotes(state, lookup, deviantId,
   cause = EXILE)` `Then` the notes contain a Deviant entry; `When` called with
   `cause = EXECUTION` `Then` they do not. *(Fails today: no entry for either.)*

5. `Given` a Deviant marked funny `When` they are **executed**
   `Then` they die (no protection). And `When` a Gangster kill or a Harlot death targets
   them `Then` they die.

6. `Given` a Deviant marked funny survives an exile
   `When` a second exile is called the same day and also passes
   `Then` the survive option is still available (the flag is not consumed).

7. `Given` two Deviants seated `When` one is marked funny
   `Then` only that seat's flag is set.
