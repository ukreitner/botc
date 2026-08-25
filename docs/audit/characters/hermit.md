# Hermit (hermit) — Experimental Outsider

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Hermit>

Current ability text (matches `characters.json:1662`):

> "You have all Outsider abilities. [-0 or -1 Outsider]"

Summary/flavour: "The Hermit isn't really here."

How to Run (quoted, complete):

> "During setup, you may remove an Outsider token and add a Townsfolk token."
>
> "Whenever appropriate, treat the Hermit as if they are the other Outsiders, including waking them
> at night, and using other Outsiders' reminder tokens."
>
> "If the Hermit duplicates an in-play Outsider, use the Hermit's **1**, **2** and **3** reminders
> instead."
>
> "If Outsider abilities clash, make up a rule to fix the clash, and tell the group."

Additional rules text (quoted):

> "The Hermit has the abilities of all the other Outsiders on the Script, all at once."
>
> "If a custom script has more than 4 Outsiders, the Hermit has all these Outsider abilities."
>
> "A Hermit with the Drunk ability does not know that they are the Hermit, and their other Outsider
> abilities function as normal."
>
> "If one of the Outsider abilities continues after death, such as the Recluse's, the Hermit keeps
> that ability when they die, but does not keep their other Outsider abilities."
>
> "If an Outsider has a jinx, that jinx applies to the Hermit too."

Examples on the page: a Hermit with Klutz + Butler + Recluse abilities; a Hermit with Drunk +
Mutant + Sweetheart abilities who believes he is the Exorcist.

Derived points that matter for the app:

- The set of borrowed abilities is **script-scoped**, not bag-scoped: every Outsider *on the script*
  (minus the Hermit itself), whether or not that Outsider is in play.
- **Setup:** `[-0 or -1 Outsider]` is a genuine Storyteller choice, made at setup. `-1` means one
  Outsider slot becomes a Townsfolk slot (so the Hermit may be the only Outsider); `-0` leaves the
  count alone.
- The Hermit still **counts as exactly one Outsider** for everything that counts Outsiders
  (Librarian, Balloonist, Godfather's "an Outsider died today", Fang Gu, Baron math, Vigormortis
  adjacency, etc.).
- **On death:** only abilities that explicitly continue after death survive (Recluse's
  misregistration is the wiki's own example; also the Heretic's "even if you are dead" if the
  Heretic is on the script, and Puzzlemaster's "1 player is drunk, even if you die").
- Jinx inheritance is total: e.g. a script with Damsel means Spy/Widow poison the Hermit; a script
  with Ogre means the Pit-Hag/Recluse/Spy/Boffin Ogre jinxes apply to the Hermit.
- No dedicated jinxes of its own on the wiki (and none in the app's data) — correct.

## What the app does today

Data:
- `characters.json:1659-1673` — correct ability text, `setup: true`, `reminders: ["1","2","3"]`,
  **no** `firstNightReminder`/`otherNightReminder`.
- `night_guide.json` — **no `hermit` entry.** There is no run-book text anywhere in the app.
- `night_and_jinxes.json` — the Hermit is **absent from both night-order lists** (`firstNight` and
  `otherNight`), and has no jinxes.

Engine — what works:
- `Setup.modifierFor` (`Setup.kt:121-232`) parses `[-0 or -1 Outsider]` correctly: the bounded-choice
  regex (`Setup.kt:110-113`) matches `-0 or -1 Outsider`, producing
  `choiceTeams = {OUTSIDER}`, `choiceDeltas = {OUTSIDER: {0, -1}}` and a default
  `outsiderDelta = -1`.
- `Setup.allowedDistributions` (`Setup.kt:261-272`) therefore admits **both** the -0 and -1
  distributions, and `GameActions.validateBag` (`:443-478`) accepts either. **Works.**

Engine — what is missing (everything else):
- Grep for `hermit` across `engine/src/main/kotlin` and `app/src/main/java`: **zero hits.** The
  Hermit is pure data.
- `Player.nightRoleId` (`GameState.kt:39-44`) special-cases only `drunk` and `marionette`. For a
  Hermit it returns `"hermit"`, which is not in either night-order list, so
  `NightOrder.build` (`NightOrder.kt:142-145`) emits **no row at all** — on any night, for any
  borrowed ability.
- `StatusEffects.isImpaired` (`:36-46`) treats `characterId == "drunk"` as impaired; a Hermit with
  the Drunk ability is not detected.
- `InfoCalc.misregistrations` (`InfoCalc.kt:120-130`) matches `characterId == "recluse"` only; a
  Hermit with the Recluse ability never raises a misregistration caveat.
- `WinCheck` (`:51-68`) matches `characterIdAtDeath == "saint"` only; executing a Hermit on a
  script with the Saint does not trigger the good-team loss.
- `StatusEffects.nominationWarnings` (`:148`) matches `characterId == "golem"` only; a Hermit on a
  Golem script gets no nomination warning and no once-per-game lock.
- `StatusEffects.deathNotes` (`:94-103`) matches `moonchild`/`sweetheart`/`barber` etc. by
  `characterId`; a Hermit's death fires none of them.
- `validateSetupState` (`GameActions.kt:503-560`) has cases for `drunk`, `lunatic`, `marionette` and
  the Fortune Teller's red herring. Nothing prompts for the Hermit's `-0/-1` choice, and nothing
  requires a `shownCharacterId` when the script has the Drunk.
- `GameActions.randomBag` (`:338-402`) always folds the **default** `-1` modifier, so a random bag
  never produces the `-0` variant; the ST is never asked.

UI:
- No Hermit prompt at setup (`GameShell.kt:347-479` prompts only for Fortune Teller, Drunk, Lunatic,
  Marionette).
- The Hermit's "1"/"2"/"3" reminder tokens are reachable from the seat sheet's reminder picker
  (`SeatSheet.kt:545-568`) as three tokens labelled `1`, `2`, `3` with no indication of which
  borrowed ability each stands for.
- The seat sheet shows the ability text and inherited-jinx **nothing**: `SeatSheet.kt:222-235`
  filters `activeJinxes` to jinxes naming the Hermit's own id, so none of the inherited Outsider
  jinxes appear.

Net storyteller experience: the app treats the Hermit as an inert Outsider token. Every borrowed
ability — waking the Butler-Hermit every night, choosing an Ogre-Hermit's friend on night 1, giving
Minions Snitch bluffs, telling Minions a Damsel is in play, the Sweetheart drunk on death, the
Moonchild's public choice, the Saint's execution loss — is invisible.

## Defects and gaps

1. **P0 · The Hermit never appears on the night sheet, so every waking Outsider ability is silently
   skipped.** Rules: "treat the Hermit as if they are the other Outsiders, **including waking them
   at night**". App: `hermit` is absent from `night_and_jinxes.json`'s `firstNight`/`otherNight`
   lists and `Player.nightRoleId` (`GameState.kt:39-44`) does not expand it, so
   `NightOrder.build` produces nothing. Repro: script with Hermit + Butler; night 1 — no Butler/
   Hermit row; the Hermit is never woken and never picks a Master.

2. **P0 · A Hermit on a Saint script is not caught by the win check.** Rules: the Hermit has the
   Saint ability. App: `WinCheck.kt:51-61` matches `characterIdAtDeath == "saint"`. Repro: execute
   the Hermit on a Trouble-Brewing-plus-Hermit script — no "good team loses" advisory.

3. **P0 · A Hermit with the Drunk ability is not impaired and has no shown identity.**
   Rules: "A Hermit with the Drunk ability does not know that they are the Hermit, and their other
   Outsider abilities function as normal." App: `StatusEffects.isImpaired` (`:36-38`) checks
   `characterId == "drunk"`; `validateSetupState` (`GameActions.kt:517-521`) requires a
   `shownCharacterId` only for `characterId == "drunk"`; `GameShell.kt:378-413` prompts only for the
   real Drunk. Repro: script with Hermit + Drunk; the Hermit sees the Hermit token and all their
   info is computed as true.

4. **P1 · Minion-facing borrowed abilities are never run.** A Hermit on a Snitch script means every
   Minion gets 3 bluffs on night 1; on a Damsel script every Minion learns a Damsel is in play (and
   a correct public Minion guess of the Hermit ends the game). Neither appears at Minion Info
   (`NightOrder.kt:60-80`) or anywhere else.

5. **P1 · Ogre-on-script: the Hermit must choose a friend on night 1 and may become evil.** No
   step, no `Friend` token, no `alignmentFlipped` change. A Hermit that is secretly evil then
   breaks `Player.isEvil` for the Chef/Empath/Fortune Teller/Investigator calculations in
   `InfoCalc`.

6. **P1 · Death-triggered borrowed abilities produce no death notes.** `StatusEffects.deathNotes`
   (`:94-103`) keys on `characterId`, so a dying Hermit never surfaces Sweetheart ("choose 1 player
   to be drunk from now on"), Moonchild ("they publicly choose a player who may die tonight"),
   Barber ("the Demon may swap two players' characters tonight"), Hatter ("tea party tonight"),
   Klutz ("publicly choose 1 alive player; if evil, your team loses") or Plague Doctor.

7. **P1 · Recluse-on-script misregistration is never flagged.** `InfoCalc.misregistrations`
   (`:120-130`) keys on `characterId == "recluse"`, so no info step warns that the Hermit may
   register as evil / a Minion / the Demon — the single most common Hermit ruling.

8. **P1 · Golem/Zealot/Mutant/Politician/Heretic/Puzzlemaster borrowed abilities have no hooks.**
   Each is keyed on `characterId` elsewhere in the codebase (`StatusEffects.kt:148` Golem,
   and — per the other audits — nothing at all for Zealot/Mutant/Politician/Heretic/Puzzlemaster).
   A Hermit on an Experimental script can legitimately hold all of them at once.

9. **P1 · Inherited jinxes are invisible.** Rules: "If an Outsider has a jinx, that jinx applies to
   the Hermit too." App: `GameData.activeJinxes` (`GameData.kt:22-26`) matches literal id pairs, and
   `SeatSheet.kt:222-235` / `GameExtras.kt:200-232` filter on those ids. Repro: script with Hermit +
   Damsel + Spy — the app never tells the ST the Hermit is poisoned all game.

10. **P2 · The `-0 or -1 Outsider` choice is never put to the Storyteller.** `randomBag`
    (`GameActions.kt:359`) always folds the default `-1`; the setup screen offers no toggle. The
    validator accepts both (`Setup.kt:261-272`), so this is a missing prompt rather than a broken
    rule.

11. **P2 · The "1"/"2"/"3" reminder tokens carry no meaning.** Rules: they stand in for a
    duplicated in-play Outsider's tokens. The picker (`SeatSheet.kt:545-568`) shows bare numerals;
    the ST must remember that "2" means "this is the Hermit's Sweetheart-drunk marker".

12. **P2 · No `night_guide.json` entry**, so the four How-to-Run sentences and the death/Drunk
    clarifications appear nowhere in the app.

13. **P3 · No clash tooling.** "If Outsider abilities clash, make up a rule to fix the clash, and
    tell the group." The obvious clashes on an all-Experimental script are Heretic × Politician
    (both rewrite the winner) and Lunatic × anything (the Hermit would have to think they are a
    Demon). The app should at least *list* the borrowed abilities so the ST can spot the clash.

## Proposed behaviour (spec)

### Core model change: borrowed abilities

Add to the engine:

```
fun borrowedOutsiderIds(state, lookup): List<String>   // for a Hermit seat
    = state.script resolved characters
        .filter { it.team == Team.OUTSIDER && it.id != "hermit" }
        .map { it.id }
```

Then introduce a single indirection used by every rule site:

```
fun effectiveCharacterIds(state, lookup, player): List<String>
    = if (player.characterId == "hermit") listOf("hermit") + borrowedOutsiderIds(...)
      else listOfNotNull(player.characterId)
```

and replace the `characterId == "<id>"` tests at these sites with
`"<id>" in effectiveCharacterIds(...)`:

- `StatusEffects.isImpaired` (`StatusEffects.kt:37`) — `"drunk"`.
- `StatusEffects.deathNotes` (`:94-103`) — `moonchild`, `sweetheart`, `barber`, plus new cases for
  `hatter`, `klutz`, `plaguedoctor`, `golem`, `tinker`.
- `StatusEffects.nominationWarnings` (`:148`) — `golem`, and (once implemented) `zealot`, `mutant`.
- `InfoCalc.misregistrations` (`:126-127`) — `recluse`, `spy`.
- `WinCheck` (`:54`) — `saint`; plus `heretic`, `klutz`, `politician`, `damsel` once those exist.
- `Setup`/`validateSetupState` — see below.

This one change is what makes the Hermit implementable at all, and it is cheap because every
consumer already looks up by id.

### Setup

- **New setup prompt** (alongside the Drunk/Lunatic/Marionette prompts in `GameShell.kt:347-479`):
  > "The Hermit is in play. Outsider count: [-0 (keep the normal count)] [-1 (one Outsider becomes a Townsfolk)]"
  Record the choice in state so the bag builder and validator use it instead of the default.
- **Borrowed-ability summary card** on the Hermit seat and at setup: list every Outsider on the
  script with a one-line "what you must do" for each, e.g.
  - Butler → "Wake the Hermit each night to choose a Master."
  - Ogre → "Wake the Hermit on night 1 to choose a friend; they may become evil."
  - Snitch → "Give each Minion 3 bluffs on night 1."
  - Damsel → "Show the Minions the Damsel token; a correct public Minion guess of the Hermit ends the game."
  - Recluse → "The Hermit may register as evil / a Minion / the Demon, even when dead."
  - Saint → "If the Hermit is executed, the good team loses."
  - Drunk → "The Hermit does not know they are the Hermit — set a shown Townsfolk identity."
  - Sweetheart → "When the Hermit dies, 1 player is drunk from now on."
  - Moonchild / Klutz → "When the Hermit learns they died, they publicly choose a player."
  - Mutant → "If the Hermit is mad about being an Outsider, they might be executed."
  - Golem → "The Hermit may only nominate once; the nominee dies unless they are the Demon."
  - Zealot → "With 5+ alive, the Hermit must vote for every nomination."
  - Tinker → "The Hermit might die at any time."
  - Barber / Hatter / Plague Doctor → "When the Hermit dies, run this tonight."
  - Heretic / Politician → "Game-end reversal — **clash risk**, decide a house rule now."
  - Puzzlemaster → "1 player is drunk, even if the Hermit dies; the Hermit may guess once."
  - Lunatic → "**Clash** — the Hermit cannot both know they are the Hermit and think they are a Demon. Decide a house rule."
- If the script contains the **Drunk**, require a `shownCharacterId` (a not-in-play Townsfolk) for
  the Hermit in `validateSetupState`, exactly as for the real Drunk (`GameActions.kt:517-521`), and
  mark the Hermit impaired.
- If the script contains the **Ogre**, require the night-1 friend choice before dawn.

### Night steps

- `Player.nightRoleId` must become `nightRoleIds: List<String>` (or `NightOrder.build` must expand
  Hermit seats), so a Hermit produces a row at **every** night-order position belonging to a
  borrowed Outsider that acts. Concretely, for the current character set that is:
  - first night: `snitch` (15), `damsel` (44 / Minion Info), `butler` (52), `ogre` (67);
  - other nights: `plaguedoctor` (4), `hatter` (58), `barber` (59), `sweetheart` (60),
    `tinker` (69), `moonchild` (70), `butler` (89), `damsel` (66).
  Each row must be titled **"Hermit (as Butler)"**, **"Hermit (as Ogre)"** etc. so the ST is never
  confused about who is being woken, and must carry the borrowed character's night guide text with
  "the Hermit" substituted for the character name.
- Conditional rows obey the borrowed character's own condition (Barber/Sweetheart/Moonchild/Hatter/
  Plague Doctor only when the Hermit has died; Ogre only on the Hermit's first night).
- **After death:** emit rows only for abilities that continue after death (Recluse
  misregistration — not a wake; Puzzlemaster's standing drunk; Heretic). Suppress all other rows.

### Reminder tokens

- When a borrowed ability places a token and the **same Outsider is also in play**, place
  `PlacedReminder("hermit", "1"/"2"/"3")` instead, and keep a state-level mapping
  `hermitTokenMeaning: Map<String, String>` (`"1" -> "Butler: Master"`), rendered under the token in
  the grimoire and in the picker. When the duplicated Outsider is *not* in play, use that
  Outsider's own token as normal ("using other Outsiders' reminder tokens").
- Expiry: a Hermit-numbered token inherits the expiry of the ability it stands for; add the mapping
  to the `EXPIRES_AT_DAWN`/`EXPIRES_AT_DUSK` lookup (`GameActions.kt:218-242`) by resolved meaning,
  not by literal label.

### Information / visibility

- Whatever each borrowed ability shows. Notably: Minions must be shown the Damsel token if Damsel is
  on the script, and each Minion 3 bluffs if Snitch is on the script.
- The Hermit is told nothing about which abilities they have; with the Drunk ability they are shown
  a Townsfolk token and never learn they are the Hermit.

### Day-time inputs

- Golem-on-script: the Hermit's single nomination (see `golem.md`).
- Damsel-on-script: the Minion guess input (see `damsel.md`), resolved against the Hermit seat.
- Klutz/Moonchild-on-script: "the Hermit publicly chose X" when they learn they died.
- Mutant-on-script: record the Hermit's public claim so the ST can decide about madness.

### Jinx inheritance

- `GameData.activeJinxes` should be given an id-expansion hook: when the in-play id set contains
  `hermit`, expand it with `borrowedOutsiderIds(script)` for jinx matching, and label the result
  "via the Hermit" in the UI (`GameExtras.kt:217-228`, `SeatSheet.kt:222-235`).
- The important inherited ones for the current character set: Spy/Widow × Damsel (Hermit poisoned
  all game), Pit-Hag/Recluse/Spy/Boffin × Ogre, Legion/Leviathan/Lil' Monsta/Summoner × Hatter,
  the whole Heretic exclusivity family.

### Data changes

- `night_and_jinxes.json`: no new list entries needed if `NightOrder` expands Hermit seats into the
  borrowed characters' existing slots (preferred). If instead a literal `hermit` entry is added, it
  would have to appear at every borrowed position, which is unmaintainable.
- `night_guide.json`: add a `hermit` entry whose `first`/`other` instructions carry the four
  How-to-Run sentences plus "This row is the Hermit acting as <X> — run <X>'s step for them."
- `characters.json`: no change (ability text matches the wiki).

## Tests to add

1. `SetupTest`: *Given* a 10-player script containing the Hermit, *then*
   `Setup.allowedDistributions` contains both `(7,2,1,1)` and `(8,1,1,1)`, and `validateBag`
   accepts a bag with either 1 or 2 Outsiders. (Should pass today — pins the working behaviour.)
2. `NightOrderTest`: *Given* a script with `hermit` and `butler`, *then* the first-night sheet
   contains a row for the Hermit at the Butler's position, titled "Hermit (as Butler)". Fails today
   (no row at all).
3. `NightOrderTest`: *Given* a script with `hermit` and `ogre`, *then* the first-night sheet
   contains a Hermit row at the Ogre's position and the other-night sheet contains none.
4. `NightOrderTest`: *Given* a script with `hermit` and `sweetheart`, *when* the Hermit is alive,
   *then* there is no Sweetheart-Hermit row; *when* the Hermit died today, *then* there is one.
5. `StatusEffectsTest`: *Given* a script containing `drunk` and a Hermit seat, *then*
   `isImpaired(hermitSeat)` is true. Fails today.
6. `StatusEffectsTest`: *Given* a script containing `sweetheart` and a dying Hermit, *then*
   `deathNotes` contains the Sweetheart note. Fails today.
7. `WinCheckTest`: *Given* a script containing `saint` and an executed, unimpaired Hermit, *then*
   `check` returns `goodWins = false`. Fails today.
8. `InfoCalcTest`: *Given* a script containing `recluse` and a Hermit adjacent to an Empath, *then*
   the Empath's result carries a misregistration caveat naming the Hermit. Fails today.
9. `StatusEffectsTest`: *Given* a script containing `golem` and a Hermit nominating, *then*
   `nominationWarnings` contains the Golem warning. Fails today.
10. `GameDataTest`: *Given* in-play ids `listOf("hermit","spy")` on a script containing `damsel`,
    *then* the expanded jinx lookup surfaces the Spy×Damsel jinx as applying to the Hermit. Fails
    today.
11. `GameActionsTest`: *Given* a script containing `drunk` and a Hermit with no `shownCharacterId`,
    *then* `validateSetupState` reports "choose a not-in-play Townsfolk token to show the Hermit".
    Fails today.
