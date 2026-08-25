# Lunatic (lunatic) — Bad Moon Rising Outsider

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Lunatic>
(Demon abilities cross-checked at <https://wiki.bloodontheclocktower.com/Po>,
`/Shabaloth`, `/Pukka`, `/Zombuul`, `/Imp`.)

Current ability text (matches `characters.json`):

> "You think you are a Demon, but you are not. The Demon knows who you are & who you
> choose at night."

How to Run (quoted):

> **Setup** — "While setting up the game, put the Lunatic and Demon tokens in the bag.
> Once all tokens have been returned to you, swap the positions of the Lunatic and
> Demon tokens in the Grimoire."
>
> **First night (Lunatic)** — "During the first night, wake the Lunatic and act as if
> they are the Demon. Show them the **THESE ARE YOUR MINIONS** info token and point to
> a number of players equaling the number of Minions in play." Then show three good
> character tokens as bluffs — **"these may include in-play characters."**
>
> **First night (real Demon)** — "During the first night, wake the Demon. Show them the
> **YOU ARE** info token, then their Demon token." … "Show them the **THIS PLAYER IS**
> info token, then the Lunatic token, then point at the Lunatic player."
>
> **Subsequent nights** — "Each night, **before the Demon wakes to attack**, wake the
> Lunatic to act as if they were that Demon. Put a **CHOSEN** reminder on each player
> they chose, then put them to sleep." … "Wake the real Demon, point at the Lunatic,
> show the Lunatic token to the real Demon, and point at the players that the Lunatic
> chose."

Examples (quoted):

> "The Lunatic, thinking they are the Shabaloth, wakes each night to choose two
> players. The chosen players do not die.
>
> The Lunatic, thinking they are the Zombuul, does not wake often at night. The real
> Zombuul, who is pretending to be the Lunatic's Minion, often attacks the same players
> the Lunatic chooses, to keep up the illusion that the Lunatic is the Demon."

Variant (quoted): "Use two Demon tokens in the bag, replacing one with the Lunatic
token to make them think they're a different Demon than the in-play one."

Key consequences for the app:

- **The Lunatic must be run as one specific Demon, for the whole game.** Everything —
  how many targets, whether they wake on the first night, whether they wake at all
  tonight, whether "self" is a legal choice — is that Demon's rules, not a generic
  "who dies?".
  - **Pukka** — "Each night, choose a player: they are poisoned. The previously
    poisoned player dies then becomes healthy." → wakes on **night 1**, 1 target,
    no asterisk.
  - **Zombuul** — "Each night*, **if no-one died today**, choose a player: they die."
    → the Lunatic is only woken on nights when nobody died today (this is the wiki's
    "does not wake often" example), 1 target.
  - **Shabaloth** — "Each night*, choose **2** players: they die. A dead player you
    chose last night might be regurgitated." → 2 targets, plus a fake regurgitation the
    ST may narrate.
  - **Po** — "Each night*, you **may** choose a player: they die. If your last choice
    was no-one, choose **3** players tonight." → 0 or 1 target normally; **3** targets
    on the night after a night where they chose nobody. This charge state persists
    across nights.
  - **Imp** — "Each night*, choose a player: they die. If you kill yourself this way, a
    Minion becomes the Imp." → 1 target, **self is a legal choice** and the ST must
    fake the star pass.
  - (Off-BMR but reachable on custom scripts: Fang Gu, No Dashii, Vigormortis, Vortox
    — all 1 target, no first night.)
- **The Lunatic's choices do nothing.** No deaths, no poison, no protection changes.
- **The real Demon must be told, every night, who the Lunatic chose**, so they can
  mirror the kills and keep the illusion alive. This is a show-card flow: THIS PLAYER
  IS + Lunatic token + point at the Lunatic, then point at each CHOSEN player.
- **Fake Minions:** exactly as many players as there are real Minions in play; any
  players (may include good players, may include the Lunatic's real allies or not).
  The ST must remember who they pointed at, all game, for consistency.
- **Bluffs:** three *good* character tokens, and unlike the real Demon's bluffs they
  **may be in play**. This is deliberate — it is the main way a Lunatic gets caught out
  ("check if shown bluffs actually exist in play").
- **7+ players gate:** the minion/bluff info follows the normal Demon-info gate.
- **Impaired Lunatic:** nothing changes mechanically (their ability is "you think you
  are a Demon"); the ST may distort what they show. The real Demon still learns who the
  Lunatic is.
- **Dead Lunatic:** stops being woken; the wiki tells the Lunatic that surviving death
  while the game continues is the giveaway.

Jinx (in the app's data at `night_and_jinxes.json:29`, and on the wiki):

> **Lunatic × Mathematician** — "The Mathematician learns if the Lunatic attacks a
> different player(s) than the real Demon attacked."

## What the app does today

Data:
- `characters.json:558-573` — ability text matches. `firstNightReminder` /
  `otherNightReminder` carry the official night-sheet prose. `reminders:
  ["Attack 1","Attack 2","Attack 3"]` (the physical token is CHOSEN; three copies is a
  reasonable stand-in for Po's three).
- `night_and_jinxes.json:311` — firstNight index **16**, between `MINION_INFO` (14) and
  `DEMON_INFO` (18). Correct.
- `night_and_jinxes.json:404` — otherNight index **31**, before `exorcist` (32) and
  before every Demon (imp 37 … lilmonsta 50). **Correct** — matches "before the Demon
  wakes to attack".
- `night_guide.json:291-313` — good prose for both nights; two `shows`:
  `{label:"Show the Lunatic", text:"YOU ARE", token:"pick"}` and
  `{label:"Show the real Demon", text:"THIS PLAYER IS THE LUNATIC", token:"self"}`.

Engine:
- `GameActions.kt:222-224` — `("lunatic","Attack 1"/"Attack 2"/"Attack 3")` are in
  `EXPIRES_AT_DAWN`, so the fake attack markers are swept at dawn. Works.
- `GameActions.kt:522-524` — `validateSetupState` refuses to start the game unless the
  Lunatic's `shownCharacterId` is a **Demon**. Works.
- `GameState.kt:39-44` — `nightRoleId` deliberately returns `"lunatic"` for the Lunatic
  (not the believed Demon), so the Lunatic keeps its own dedicated night row while the
  Drunk/Marionette are folded into their believed role. Correct.
- `NightOrder.kt:89, 110-115` — on the first night, the `DEMON_INFO` step's detail gets
  " Also show the Demon who the LUNATIC is (Name) — the Demon can mirror their fake
  kills." appended.
- `NightOrder.kt:157-172` — on other nights, every Demon's step gets
  " LUNATIC (Name) chose: A, B — show the Demon those choices first." (or, when no
  `sourceId == "lunatic"` reminder is placed, "wake them first for their fake attack…").

UI:
- `GameShell.kt:415-440` — a setup dialog "The Lunatic is in play — which Demon token
  do they see?" lists every Demon on the script; the pick sets `shownCharacterId` and
  writes the seat note "Believes they are the Po".
- `RevealFlow.kt:54-59` — the pass-the-phone reveal correctly shows the Lunatic their
  believed Demon token and an **evil** alignment card.
- Night step: `NightScreen.kt:834` → `QuickResolutions` → `else` branch
  (`NightScreen.kt:518-524`): `characterById("lunatic")?.team` is `OUTSIDER`, **not
  DEMON**, so no `DemonKillPanel` renders. `InfoCalc.supports("lunatic")` is false. The
  Lunatic step therefore offers **no pick UI at all** — only the bottom
  `NightToolTray` with three "Attack" chips, and the two guide show-cards.
- Bluffs: one global `state.demonBluffIds` (`GameState.kt:102`), edited in
  `BluffsSheet.kt:40-45` which lists **only not-in-play** Townsfolk/Outsiders, and
  suggested by `GameActions.suggestBluffs` (`GameActions.kt:121-127`) which also
  excludes in-play characters. `ShowCard.BluffsCard` is offered only on the
  `DEMON_INFO` step (`NightScreen.kt:783-788`) and in `ShowToolSheet`
  (`ShowCards.kt:390`).

Storyteller's actual experience today: pick a Demon token at setup, then on night 1
read a paragraph and improvise — point at "some players" as fake Minions with nothing
recorded, and either reuse the real Demon's bluffs or invent three on the spot with no
UI. On later nights, read a paragraph, then hunt in the bottom tray for "Attack 1/2/3"
regardless of which Demon the Lunatic thinks they are, and hope the Demon's step's
appended sentence is noticed.

## Defects and gaps

1. **P0 · The Lunatic does not act as the specific Demon they believe they are.**
   Rules: Po = 1 or 3 targets with a charge, Shabaloth = 2, Pukka = 1 **and wakes
   night 1**, Zombuul = only on nights when nobody died today, Imp = self is legal.
   App: the step renders no picker at all (`NightScreen.kt:518-524` requires
   `team == Team.DEMON`; the Lunatic is an Outsider), and the step text is the generic
   `characters.json` prose. The ST has to know all five Demons' rules and count
   tokens by hand. Repro: any BMR game with a Lunatic-as-Po; night 2, open the Lunatic
   step — no "choose 3" affordance, no charge tracking.

2. **P0 · The Lunatic has no bluffs of their own.**
   Rules: the Lunatic gets **their own** 3 good tokens, which **may be in play**.
   App: one global `demonBluffIds`; `BluffsSheet.kt:44` filters `it.id !in inPlay`, so
   in-play tokens are not even offerable, and there is no second slot for the Lunatic.
   Consequence: the ST either shows the Lunatic the real Demon's bluffs (which leaks
   information both ways and removes the Lunatic's main tell) or tracks a second set on
   paper. Repro: menu → Demon bluffs → there is exactly one list and no Lunatic tab.

3. **P0 · The fake Minions are never recorded.**
   Rules: point to N arbitrary players, N = number of Minions in play; the ST must be
   consistent about this for the rest of the game (and the Lunatic will try to talk to
   those players). App: nothing. No count is computed, no picker, no reminder token, no
   note. Repro: night 1 Lunatic step — the prose says "point to arbitrary players" and
   offers no way to say which.

4. **P1 · The Pukka case (and any first-night Demon) has no first-night action UI.**
   `characters.json:562` says "If the token received by the Lunatic is a Demon that
   would wake tonight … Allow the Lunatic to do the Demon actions", but nothing in the
   app detects `shownCharacterId == "pukka"` and offers a target picker on night 1. The
   Attack chips are available from the tray, but the ST must know to use them and must
   know Pukka poisons rather than kills.

5. **P1 · Nothing computes or shows "who the Lunatic chose" as a card.**
   `NightOrder.kt:163-167` appends the names to the Demon's *step text* only. There is
   no show-card flow ("THIS PLAYER IS" + Lunatic token, then a card per chosen player)
   and nothing marks that the Demon has been told. `night_guide.json` has a
   "Show the real Demon / THIS PLAYER IS THE LUNATIC" card, but it is attached to the
   **Lunatic's** step, which is where the Lunatic is sitting — the Demon has not been
   woken yet at that point in the order.

6. **P1 · Po's "3 attacks" charge is not modelled.**
   `FullGamePlaytestTest.kt:1149` even adds a reminder labelled `"3 attacks"` for the
   Lunatic — a label that does not exist in `characters.json:568-571`, so the ST cannot
   place it from any picker. The charge must survive to the next night; the Attack
   tokens expire at dawn (`GameActions.kt:222-224`), so nothing persists.

7. **P1 · Zombuul gating is not applied.** A Lunatic-as-Zombuul should only be woken on
   nights when nobody died today. The step always appears and is always on the
   checklist (and `GameShell.kt:147-161` will block dawn until it is ticked).

8. **P2 · The setup dialog does not default to the in-play Demon.**
   `GameShell.kt:421-422` lists every Demon on the script equally. The default rule is
   that the Lunatic thinks they are *the* Demon in play (tokens swapped); the "different
   Demon" variant is opt-in. The in-play Demon should be pre-selected/first with a
   one-line explanation of the variant.

9. **P2 · The Lunatic's step is not skipped/greyed when the Lunatic is dead.**
   `NightStepRow` shows the generic "All holders are dead — usually skip", but the
   Demon's step still says "LUNATIC (Name) is in play — wake them first".

10. **P2 · No Mathematician support for the jinx.** The jinx text is surfaced in
    `ActiveJinxesDialog` / `SeatSheet.kt:226-234`, but nothing compares the Lunatic's
    choices to the real Demon's choices, which is exactly what the Mathematician needs
    (and neither set of choices is stored).

11. **P3 · `night_guide.json:299` show-card text "YOU ARE" uses `token:"pick"`** — the
    ST must search for the believed Demon every time instead of it defaulting to
    `shownCharacterId`.

## Proposed behaviour (spec)

Throughout, let `believed = state.player(lunaticSeat).shownCharacterId` (guaranteed to
be a Demon by `validateSetupState`).

### New engine state

```kotlin
// GameState
val lunaticBluffIds: List<String> = emptyList(),      // may include in-play characters
val lunaticMinionIds: List<Long> = emptyList(),        // the seats pointed at on night 1
val nightChoices: List<NightChoice> = emptyList(),     // see devilsadvocate.md
```

`NightChoice(cycle, characterId = "lunatic", chooserId, targetIds, impaired)` gives Po's
charge ("was last cycle's `targetIds` empty?"), the Mathematician comparison, and the
Demon-notification list, all from one record.

### Setup

- When a Lunatic is dealt, `GameShell`'s existing dialog stays, but:
  - the **in-play Demon is listed first and pre-selected**, with the caption
    "Default: the Lunatic thinks they are the Demon in play. Pick a different Demon for
    the two-Demon-token variant."
  - on pick, additionally seed `lunaticBluffIds` with
    `GameActions.suggestLunaticBluffs(...)` (see below) and set the seat note
    "Believes they are the Po (Lunatic)".
- `GameActions.suggestLunaticBluffs(available, state, random)`: 3 good characters,
  preferring 2 Townsfolk + 1 Outsider, **allowed to include in-play characters**,
  and deliberately including at least one in-play character when one exists (that is
  the flavour of the role) — but never the Lunatic's own believed Demon.
- `BluffsSheet` gains two tabs: **Demon bluffs** (unchanged rules) and **Lunatic
  bluffs** (only shown when a Lunatic is in play; the candidate list is *all* good
  characters on the script, with in-play ones marked "in play — allowed for the
  Lunatic"). Both persist independently.

### First night

- when: `cycle == 1`, Lunatic alive.
- Step title: "Lunatic — runs as the **Po**".
- Sub-steps rendered as a small checklist inside the step:
  1. **Fake Minions** (only if `players.count { !isTraveller } >= 7`):
     "Point to **2** players as the Lunatic's Minions" where 2 =
     `players.count { characterId?.team == MINION && characterId != "marionette" }`.
     A multi-select chip row (exactly that many, any player, dead-first excluded) writes
     `lunaticMinionIds` and places `PlacedReminder("lunatic","Fake minion")` on each
     seat (a **new** reminder label; never expires; visible in the grimoire all game).
     Default suggestion: N players who are **not** real Minions and not the Lunatic.
  2. **Bluffs** (only if 7+): an `AssistChip` "Show the Lunatic's 3 bluffs" →
     `ShowCard.BluffsCard(state.lunaticBluffIds)`, with an inline "edit" link to the
     Lunatic tab of `BluffsSheet`. Must be visually distinct from the Demon's bluffs
     chip so the ST never confuses them.
  3. **Believed-Demon first-night action** — rendered **only if the believed Demon has
     a non-blank `firstNightReminder`** (Pukka on BMR; Lord of Typhon / Kazali on other
     scripts). Uses the same `LunaticActionPanel` as other nights (below), with Pukka's
     "who is poisoned?" wording, and places CHOSEN markers that do nothing.
- **Real Demon notification** stays on the `DEMON_INFO` step (`NightOrder.kt:110-115`)
  and gains two prepared cards there:
  - "THIS PLAYER IS" + Lunatic token (`ShowCard.CharacterCard("THIS PLAYER IS",
    "lunatic")`), followed by "point at `<Lunatic name>`".
  - if the Lunatic marked anyone on night 1: a per-target card / chip list.

### Other nights

- when: `cycle > 1`, Lunatic **alive**, and the believed Demon would act tonight:
  - `believed == "zombuul"` → only if **nobody died today**
    (`state.deaths.none { it.day == state.cycle - 1 && !it.atNight && !it.resurrected }`
    — i.e. no death during the day just ended). Otherwise the step is auto-marked
    "skipped — nobody may wake" and is not blocking.
  - all other Demons → always.
- **`LunaticActionPanel(believed)`** — a per-Demon picker mirroring exactly what the
  real Demon's panel would offer:

  | believed | targets | rules shown |
  |---|---|---|
  | `imp` | 1 | self is legal → offer "Star pass — fake it: tell the Lunatic a Minion becomes the Imp, change nothing" |
  | `zombuul` | 1 | only when nobody died today |
  | `pukka` | 1 | "poison" wording; also acts night 1 |
  | `shabaloth` | 2 | plus "regurgitate a dead player the Lunatic chose last night?" (narrative only) |
  | `po` | 0 or 1, **3 if last night's `targetIds` was empty** | header says "Po is charged — the Lunatic chooses 3 tonight" |
  | `fanggu`/`nodashii`/`vigormortis`/`vortox` | 1 | plain |
  | fallback (unknown/homebrew Demon) | 1 | plain, with the believed Demon's `otherNightReminder` printed |

- **immediate effects:** place `PlacedReminder("lunatic","Chosen")` on each target
  (rename the tokens from "Attack 1/2/3" to a single repeated "Chosen" ×3 to match the
  physical token; keep all three copies in `characters.json` so Po can mark three).
  **Nothing else changes** — no kill, no poison, no protection. The panel must show a
  standing line: "Nobody dies from this. The Lunatic's choices have no effect."
  Record a `NightChoice`.
- **deferred effects:** none.
- **expiry:** CHOSEN markers expire at **dawn** (already the case,
  `GameActions.kt:222-224`; update the labels in that table if the labels are renamed).
  `nightChoices` never expires.

### Showing the real Demon (the P1-5 fix)

On every Demon's night step (`NightOrder.kt:157-172` → move this into a proper panel),
render a **Lunatic briefing block** *above* the kill panel:

```
LUNATIC — Blake (thinks they are the Po)
[ Show "THIS PLAYER IS" + Lunatic token ]     ← full-screen card
Point at Blake.
Blake chose: Cora, Greta, Nate
[ Show Cora ] [ Show Greta ] [ Show Nate ]    ← "point at" helper, one card each
[ ✓ Demon has been shown ]                    ← writes nightStepsDone-style flag
```

The "Demon has been shown" tick should be part of the dawn checklist so
`GameShell.kt:147-161` catches a forgotten notification. If the Lunatic chose nobody,
the block reads "Blake chose nobody tonight — tell the Demon so."
If the Lunatic is dead, the block reads "Blake (Lunatic) is dead — no fake attack
tonight."

### Information / visibility summary

- **Lunatic sees:** their Demon token (YOU ARE), THESE ARE YOUR MINIONS + N arbitrary
  players, 3 good bluffs (in-play allowed), and — every night — the illusion of their
  Demon's action.
- **Real Demon sees:** on night 1, THIS PLAYER IS + Lunatic token + the Lunatic player;
  every night after, the Lunatic token again plus each chosen player.
- **Real Minions see:** nothing about the Lunatic (they see the real Demon at
  `MINION_INFO`).
- **The Lunatic's fake "Minions" are told nothing** — they are ordinary players who will
  be approached by a confused Lunatic.

### Impaired / dead

- Impaired Lunatic: show
  "! The Lunatic is drunk/poisoned — it changes nothing mechanically, but you may
  distort what you show them. The Demon still learns who they are and what they chose."
- Dead Lunatic: auto-skip the step (non-blocking) and suppress the Demon briefing
  block's choice list.

### Mathematician jinx

Because both the Lunatic's and the Demon's choices land in `nightChoices`, the
Mathematician's night step can compute:
`lunaticTargets(cycle) != demonTargets(cycle)` → the Mathematician's number is
incremented by 1 for that night. Add this to `InfoCalc`'s `mathematician` branch as a
caveat line even before full support: "! Lunatic jinx: the Lunatic chose {…}, the Demon
chose {…} — {do/do not} differ."

### UI text for the step

- First night, 7+: "Wake the Lunatic. Show THESE ARE YOUR MINIONS and point at the
  **2** players you picked. Then show their 3 bluffs. Treat them exactly like the Po."
- First night, <7: "Wake the Lunatic. No Minion info and no bluffs in a game this
  small — just run their Demon's first-night action if it has one."
- Other nights: "Wake the Lunatic **before the real Demon**. Run the Po's choice.
  Mark CHOSEN. **Nobody dies.** Then show the real Demon who they picked."
- Zombuul-gated skip: "Somebody died today — the Zombuul would not wake, so the Lunatic
  does not wake either. Skip."

### Data changes

- `characters.json:568-571` — rename `reminders` from `["Attack 1","Attack 2","Attack
  3"]` to `["Chosen","Chosen","Chosen","Fake minion"]` (or keep three distinct labels
  and add "Fake minion"); update `GameActions.EXPIRES_AT_DAWN:222-224` to match, and
  **do not** put "Fake minion" in any expiry table.
- `night_guide.json:296-306` — set the "YOU ARE" show's `token` to a new `"shown"` kind
  meaning "the seat's `shownCharacterId`", so it defaults to the believed Demon; move
  the "Show the real Demon / THIS PLAYER IS THE LUNATIC" card from the Lunatic's entry
  to the `DEMON_INFO` and each Demon's `other` entry; add a "THESE ARE YOUR MINIONS"
  card and a "Lunatic bluffs" card to the Lunatic's `first` entry.
- No night-order changes — indices 16 (first) and 31 (other) are already correct.

## Tests to add

1. `lunatic runs as the believed demon — Po charge`
   Given a Lunatic shown "po"; night 2 the Lunatic chose nobody. When the night-3 step
   is built. Then the required target count is **3**. **Fails today** (no such logic).
2. `lunatic as shabaloth chooses exactly two`
   Given `shownCharacterId == "shabaloth"`. Then the panel's target count is 2 and
   `nightChoices` records both.
3. `lunatic as pukka acts on the first night`
   Given `shownCharacterId == "pukka"`. When the first-night sheet is built. Then the
   Lunatic step includes a choice panel (Pukka's `firstNightReminder` is non-blank).
4. `lunatic as zombuul does not wake when someone died today`
   Given a day-2 execution. When the night-3 sheet is built. Then the Lunatic step is
   marked skippable and is not counted as unfinished by the dawn guard.
5. `lunatic choices kill nobody`
   Given the Lunatic chose Cora. When the step resolves. Then Cora is alive, no
   `DeathRecord` exists, Cora has no poison/drunk reminder, and Cora holds exactly one
   `("lunatic","Chosen")` token.
6. `lunatic chosen markers expire at dawn but the choice record does not`
   When `advancePhase` NIGHT→DAY. Then no `("lunatic","Chosen")` token remains and
   `nightChoices.last().targetIds == listOf(cora.id)`.
7. `lunatic has its own bluff list, separate from the demon's`
   Given `demonBluffIds = [chef, empath, butler]` and
   `lunaticBluffIds = [washerwoman, mayor, saint]` where `mayor` is in play. Then both
   survive a round-trip through serialization and `validateSetupState` raises no issue
   about the in-play Mayor. **Fails today** (field does not exist).
8. `lunatic fake minion count equals the real minion count`
   Given 3 Minions in play (one of them a Marionette). Then the required fake-minion
   count is **2** (Marionette excluded, matching `NightOrder.kt:61-64`'s convention)
   — pin whichever convention is chosen.
9. `demon briefing lists the lunatic's choices`
   Given the Lunatic chose Cora and Greta on night 3. When the Imp's night-3 step is
   built. Then its Lunatic block names Cora and Greta and the Lunatic seat.
10. `dead lunatic does not block dawn`
    Given the Lunatic is dead. When the dawn guard runs with the Lunatic step
    unticked. Then it is not listed as unfinished.
11. `setup rejects a non-demon shown token for the lunatic` — already covered by
    `GameActions.kt:522`; add the positive case that the in-play Demon is the default
    suggestion.
