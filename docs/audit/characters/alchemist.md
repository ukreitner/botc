# Alchemist (alchemist) — Experimental Townsfolk

## Official rules (sources)

Source: <https://wiki.bloodontheclocktower.com/Alchemist> (fetched via
`action=parse&prop=wikitext`, 2026-08-25).

Current ability text (verbatim):

> "You have a Minion ability. When using this, the Storyteller may prompt you to choose differently."

**Summary bullets (verbatim):**

- "The Alchemist's ability is usually that of a not-in-play Minion, but can duplicate an in-play Minion ability."
- "The Alchemist learns which ability this is on the first night."
- "They are still a good Townsfolk. They win when good wins, and lose when good loses. They register as good and as the Alchemist."
- "The Alchemist does not wake to learn who the other Minions are or who the Demon is, like Minions do."
- "If the Alchemist's Minion ability adds or removes characters during setup, this still occurs during setup."
- "If the Alchemist has an ability where the player chooses something, like the Poisoner or the Vizier, the Storyteller may ask the Alchemist to choose differently. The Alchemist must do so."

**How to Run (verbatim):**

> During the first night, wake the Alchemist. Show the **YOU ARE** info token then the character token of a Minion. Put the Alchemist to sleep. If the Alchemist has a not-in-play Minion ability, mark the Alchemist with the **IS THE ALCHEMIST** reminder and swap the Alchemist token with this Minion token and turn it upside down. (*This shows they are still good.*)
>
> The Alchemist has this Minion ability. They use it as if they were a Minion, and wake at night when that Minion would normally wake to use their ability.
>
> If the Alchemist makes a choice using their ability, you may ask them to choose differently. If this is during the day, ask verbally. If this is during the night, shake your head, point at the Alchemist ability text on the character sheet, and wait for the Alchemist to choose again.

**Examples (verbatim):**

1. "The Alchemist has the Baron ability. There are 2 extra Outsiders in play."
2. "The Alchemist has the Poisoner's ability. On the first night, they wake and poison the Wizard. On the second night, they wake and poison the Alsaahir. On the third night, they wake and try to poison the Lord of Typhon, but the Storyteller prompts them to choose differently. They poison the King instead. The Lord of Typhon is not poisoned."

**Jinxes (verbatim, all eight):**

| With | Text |
|---|---|
| Boffin | "If the Alchemist has the Boffin ability, the Alchemist does not learn what ability the Demon has." |
| Marionette | "An Alchemist-Marionette has no Marionette ability & the Marionette is in play." |
| Mastermind | "An Alchemist-Mastermind has no Mastermind ability & the Mastermind is not-in-play." |
| Organ Grinder | "If the Alchemist has the Organ Grinder ability, the Organ Grinder is in play. If both are sober, both are drunk." |
| Spy | "An Alchemist-Spy has no Spy ability & a Spy is in play. After each execution, a living Alchemist-Spy may publicly guess a living player as the Spy. If correct, the Demon must choose the Spy tonight." |
| Summoner | "The Alchemist-Summoner does not get bluffs, and chooses which Demon but not which player. If they die before this happens, evil wins. [No Demon]" |
| Widow | "An Alchemist-Widow has no Widow ability & a Widow is in play. After each execution, a living Alchemist-Widow may publicly guess a living player as the Widow. If correct, the Demon must choose the Widow tonight." |
| Wraith | "An Alchemist-Wraith has no Wraith ability & a Wraith is in play. After each execution, a living Alchemist-Wraith may publicly guess a living player as the Wraith. If correct, the Demon must choose the Wraith tonight." |

Timing: the Alchemist's own first-night step is **before Minion info** (index 10
of 76 in `night_and_jinxes.json:305`, between Philosopher and Poppy Grower, with
MINION_INFO at 14) — correct, and it must stay that way because the granted
ability may itself need to fire later on the same first night (e.g. Poisoner at
index 27, Widow at 28, Godfather at 32, Organ Grinder at 33, Devil's Advocate at
34, Witch at 36, Cerenovus 37, Fearmonger 38, Harpy 39, Mezepheles 40).

## What the app does today

Data:
- `characters.json:1209` — `ability: "You have a Minion ability. When using this, the Storyteller may prompt you to choose differently."` **Matches current wiki text.** `setup: false`, `firstNightReminder: "Show the 'You are' info token and a Minion token."`, `otherNightReminder: ""`, `reminders: []`, `remindersGlobal: ["Is The Alchemist"]`.
- `night_and_jinxes.json:305` — first-night index 10; **no other-night entry**.
- `night_and_jinxes.json:235` — one jinx: `{"id1":"summoner","id2":"alchemist","reason":"The Alchemist can not have the Summoner ability."}` — this is the **retired** wording. The other **seven** jinxes (Boffin, Marionette, Mastermind, Organ Grinder, Spy, Widow, Wraith) are **absent**.
- `night_guide.json:836` — a good `first` entry with a `{"label":"Minion ability","kind":"token","text":"YOU ARE","token":"pick"}` show card, which drives `GuideShowDialog` (`NightScreen.kt:366`) → a searchable character picker → a full-screen "YOU ARE" + token card. **This part works well.**

Code: **no `alchemist` string exists anywhere in `engine/src/main` or
`app/src/main`.** Everything is generic:
- `NightOrder.build` (`NightOrder.kt:40`) emits one first-night step. Because the night sheet is keyed on `Player.nightRoleId` (`GameState.kt:38`), which returns `"alchemist"`, **no step is ever emitted at the granted Minion's night-order position** — not on night 1, not on any later night.
- `NightToolTray` (`NightScreen.kt:193`) offers only the Alchemist's own reminders: `allReminders` = `[] + ["Is The Alchemist"]` (`Character.kt:62`). The granted Minion's tokens (Poisoner "Poisoned", Widow "Poisoned"/"Spy", Devil's Advocate "Survives execution", Witch "Cursed", Cerenovus "Mad", Fearmonger "Fear", Harpy "Mad"/"2nd", Godfather "Died today"…) are **not** offered on the Alchemist's step.
- `Setup.modifierFor` (`Setup.kt:121`) returns `null` for the Alchemist because `setup == false` — correct for the Alchemist itself, but it means an Alchemist-Baron's `[+2 Outsiders]` is never folded into the bag, and `validateBag` (`GameActions.kt:420`) will then reject a legal Alchemist-Baron game.
- `GameActions.validateSetupState` (`GameActions.kt:503`) has explicit branches for `drunk`, `lunatic`, `marionette` and the Fortune Teller herring, and `GameShell` has matching setup dialogs (`GameShell.kt:348` herring, `:383` Drunk, `:420` Lunatic, `:442` Marionette). **The Alchemist has none** — the ST is never asked which Minion ability was granted, and the choice is never stored.
- `GameData.activeJinxes` (`GameData.kt:23`) only returns jinxes where **both** ids are in play. An Alchemist-Spy is a *virtual* Spy: `spy` is not in `players.mapNotNull { characterId }`, so the Spy jinx would not surface even if the data existed.

Storyteller's actual experience today: on night 1 the Alchemist step appears,
the guide tells them what to do, and the "Minion ability" show card lets them
pick and display a Minion token — good. Then the app forgets. There is nowhere
to record *which* Minion, and from night 1 onward the storyteller must
personally remember to wake the Alchemist at the Poisoner's slot, must remember
the Poisoner's tokens, must place them from the generic "All tokens" sheet, and
must remember that they may veto the choice. In practice this is exactly the
Pukka-class failure the user reported: the app offers no step, so the ability
silently does not happen.

## Defects and gaps

1. **P0 · The granted Minion ability never appears in the night sheet.**
   Rules: "they … wake at night when that Minion would normally wake to use
   their ability." App: `NightOrder.build` (`NightOrder.kt:40`) keys steps on
   `nightRoleId`, which is `"alchemist"`; there is no mechanism for one seat to
   occupy a second night-order slot. Repro: give a player the Alchemist, show
   them the Poisoner on night 1, advance to night 2 — the night sheet contains
   no Poisoner row, and no Poisoner row on night 1 either. Every choosing
   Minion ability (Poisoner, Widow, Devil's Advocate, Witch, Cerenovus,
   Fearmonger, Harpy, Mezepheles, Pit-Hag, Organ Grinder, Snake-charmer-likes,
   Xaan, Boffin, Summoner) is affected.
2. **P0 · The granted ability is never recorded in state.** There is no field
   for it (`Player` has `characterId`/`shownCharacterId`/`note` only,
   `GameState.kt:14`). The `"Is The Alchemist"` global reminder marks *that*
   they are the Alchemist, not *which* ability. Consequently nothing downstream
   (night order, `InfoCalc`, `StatusEffects.deathNotes`, jinx detection,
   `WinCheck`) can know.
3. **P0 · Setup modifiers of the granted Minion are not applied.**
   Rules: "If the Alchemist's Minion ability adds or removes characters during
   setup, this still occurs during setup" (wiki Example 1: Alchemist-Baron =
   2 extra Outsiders). App: `Setup.adjustedDistribution` (`Setup.kt:252`) only
   maps over the characters actually in the bag, and the Alchemist has
   `setup:false`. Repro: 10 players, bag contains the Alchemist, ST intends
   Alchemist-Baron and puts 3 Outsiders in — `validateBag` reports
   "Outsider: 3 in bag, expected 1" and `requestPhaseAdvance`
   (`GameShell.kt:126`) blocks the first night with "Setup isn't legal yet".
4. **P0 · Seven of eight Alchemist jinxes are missing and the eighth is stale.**
   `night_and_jinxes.json:235` says "The Alchemist can not have the Summoner
   ability", which the wiki replaced with a full rule
   ("does not get bluffs, chooses which Demon but not which player … [No Demon]").
   Missing entirely: Boffin, Marionette, Mastermind, Organ Grinder, Spy, Widow,
   Wraith. Repro: menu → "Jinxes in play" with an Alchemist + Spy script shows
   nothing.
5. **P1 · No setup prompt to choose the Minion ability.** Drunk, Lunatic,
   Marionette and Fortune Teller all get a blocking setup dialog
   (`GameShell.kt:348–478`) and a `validateSetupState` rule
   (`GameActions.kt:514–545`). The Alchemist gets neither, so the ST can reach
   night 1 with no decision made and then improvise at the table.
6. **P1 · The night-1 "YOU ARE" card doesn't persist the pick.**
   `GuideShowDialog` (`NightScreen.kt:366`) holds `tokenId` in
   `rememberSaveable` local state and throws it away on dismiss
   (`NightScreen.kt:438–453` calls `onShow` only). The ST shows the token, and
   the app has no memory of it thirty seconds later.
7. **P1 · The granted Minion's reminder tokens are not offered.** `NightToolTray`
   (`NightScreen.kt:202`) uses `character.allReminders` for the *step's*
   character. An Alchemist-Poisoner ST must open "All tokens" (`ShowToolSheet`)
   or the seat sheet's `ReminderPicker` (`SeatSheet.kt:492`) and hunt.
8. **P1 · No "you may prompt them to choose differently" affordance.** The
   ability's second sentence is a live storyteller power used every time the
   Alchemist picks (wiki Example 2). Nothing in the UI mentions it at the
   moment of the choice.
9. **P1 · The Alchemist-Spy / Widow / Wraith jinxes need a day-time public
   guess recorded after **each execution**, which then **forces the Demon's
   choice that night**. There is no day-input mechanism at all (see
   cross-cutting note) and no way to constrain `DemonKillPanel`
   (`NightScreen.kt:534`).
10. **P2 · Alignment/registration is not pinned.** "They register as good and
    as the Alchemist." If an implementer takes the wiki's physical instruction
    literally ("swap the Alchemist token with this Minion token") and sets
    `characterId = "poisoner"`, then `Player.isEvil` (`GameState.kt:47`) flips
    the Alchemist to evil, breaking Empath/Chef/Investigator/`WinCheck`. The
    granted ability must be a **separate field**, never `characterId`.
11. **P2 · The Alchemist is not excluded from Minion info.** It happens to work
    today — `NightOrder.build:61` filters on `team == Team.MINION` and the
    Alchemist is Townsfolk — but there is no test locking it, and a naive fix
    for defect 1 could break it. "The Alchemist does not wake to learn who the
    other Minions are or who the Demon is."
12. **P2 · Not offered as a Demon bluff hazard.** `GameActions.suggestBluffs`
    (`GameActions.kt:121`) excludes in-play ids; with an Alchemist-Poisoner the
    Poisoner is *not* in play and may be suggested as a bluff, which is legal
    but worth a caution line since the Alchemist will be poisoning.
13. **P3 · Reminder-label casing.** `characters.json:1209` uses
    `"Is The Alchemist"`; the wiki uses `IS THE ALCHEMIST`; townsquare uses
    `"Is the Alchemist"`. Harmless, but the string is used as a token label and
    should be normalised once.

## Proposed behaviour (spec)

### Engine data

Add to `Player`:

```kotlin
/** Ability granted by another character (Alchemist's Minion, Philosopher's
 *  copied Townsfolk...). The seat still registers as [characterId]. */
val grantedAbilityId: String? = null,
```

and change `NightOrder.build` to expand each seat into **all** the night-order
slots it occupies:

```kotlin
val nightSlots: List<String> = listOfNotNull(nightRoleId, grantedAbilityId)
```

grouping seats by slot instead of by `nightRoleId` alone. This one change also
serves the Philosopher, Boffin-granted Demon abilities, Cannibal and Amnesiac.

`Player.isEvil`, `team()`, `characterShownToPlayerId` must all keep using
`characterId` — the Alchemist stays a good Townsfolk named Alchemist.

### Setup

- **when:** SETUP, whenever a seat has `characterId == "alchemist"` and
  `grantedAbilityId == null`.
- Blocking dialog, in the same style as `HiddenIdentityDialog`
  (`GameShell.kt:710`):
  > **The Alchemist is in play** — Ana is the Alchemist. Which Minion ability do they have?
  Options: every Minion on the script, **not-in-play ones listed first**
  (that is the normal case); in-play ones in a second "Duplicates an in-play
  Minion" section (legal but unusual). Exclude nothing outright except by jinx
  (see below).
- On pick: `grantedAbilityId = <minionId>`; add global reminder
  `("alchemist","Is the Alchemist")` to the seat; set
  `note = "Has the <Minion> ability (still good)"`.
- Add to `GameActions.validateSetupState` (`GameActions.kt:514`):
  `"alchemist" -> if (grantedAbilityId == null) issues += "${player.name}: choose the Minion ability the Alchemist has"`.
- **Setup modifiers:** `Setup.adjustedDistribution` /
  `Setup.allowedDistributions` / `validateBag` must accept an extra
  "virtual characters" list. Simplest: `validateSetupState` computes
  `bagCharacters + players.mapNotNull { it.grantedAbilityId?.let(lookup) }` and
  passes it to `validateBag`. Alchemist-Baron then legally requires +2
  Outsiders; Alchemist-Godfather legally allows −1/+1; Alchemist-Xaan `[X Outsiders]`
  relaxes the count. Because the Alchemist choice is normally made *after* the
  bag is built, the setup dialog must appear **before** the "Deal randomly &
  start" button resolves, and the bag builder must show the pending modifier
  text next to the distribution line (`SetupScreen.kt:375`).

### Night 1 (Alchemist's own step, index 10)

- **when:** first night, seat alive.
- **targets:** none.
- **information:** show `YOU ARE` + the `grantedAbilityId` token. The existing
  guide show card already does this; change `token: "pick"` to a resolved token
  once `grantedAbilityId` exists, so it is one tap with no picker.
- **visibility:** nothing to the Demon or Minions. Explicitly print in the
  step: *"The Alchemist does NOT wake with the Minions and does not learn the
  Demon."*
- **immediate effects:** none beyond the reminder placed at setup.

### Every night the granted Minion acts

- The seat appears as a **second row** at the granted Minion's night-order
  index, titled e.g. **"Poisoner — via the Alchemist (Ana)"**, using the
  Poisoner's `firstNightReminder`/`otherNightReminder` and
  `night_guide.json["poisoner"]`, and its `allReminders` in the tool tray.
- Prepend a fixed banner to that step's detail:
  > **You may veto this choice.** If the Alchemist picks badly for the good team, shake your head and point at the Alchemist text on the sheet; they must choose again.
- Impairment: the Alchemist's granted ability malfunctions when the Alchemist
  is drunk/poisoned, exactly like a real Minion — `StatusEffects.isImpaired`
  on the Alchemist seat already answers this; the step must surface it.
- Death: when the Alchemist dies, the granted ability stops (unless a
  Vigormortis-style rule applies — Vigormortis only keeps *Minion* abilities,
  and the Alchemist is a Townsfolk, so the ability stops). Mark the row
  "holder is dead — skip".

### Jinxes to encode (all eight, in `night_and_jinxes.json`)

Store with `id1: "alchemist"` and add a **virtual-in-play** rule to
`GameData.activeJinxes` (`GameData.kt:23`) so that `grantedAbilityId` counts as
in play for jinx matching.

| id2 | Behaviour the app must implement |
|---|---|
| `boffin` | Suppress the "learn the Demon's ability" info on the Alchemist's step. |
| `marionette` | Setup picker must offer Marionette only alongside a real in-play Marionette; the Alchemist gets **no** ability (`grantedAbilityId` set but flagged `abilitySuppressed = true`), and the Marionette must be in the bag. |
| `mastermind` | Alchemist gets no ability; Mastermind must be **not**-in-play; suppress `mastermindDayActive` prompts (`WinCheck.kt:28`). |
| `organgrinder` | Organ Grinder must be in the bag; if both are sober, mark **both** drunk (auto-place `organgrinder:Drunk` on each at setup). |
| `spy` / `widow` / `wraith` | Alchemist gets no ability; a real Spy/Widow/Wraith must be in the bag. **After each execution**, offer the ST a day input: "Alchemist-Spy publicly guessed ___ as the Spy" with a computed correct/incorrect verdict. If correct, set a constraint consumed by `DemonKillPanel` that night: *"The Demon MUST choose <name> tonight"*, and disable every other chip. |
| `summoner` | Bag has **no Demon** (`demonDelta = -1` already handled for the Summoner itself at `Setup.kt:131` — extend it to granted abilities). Suppress bluffs for this seat. On the Summoner's night-3 step, the Alchemist chooses **which Demon** but the **ST** chooses the player. If the Alchemist dies before that resolves, **evil wins** — surface this as a `WinCheck` advisory. |

### UI text

- Setup dialog title: `The Alchemist is in play`
- Setup dialog body: `Ana is the Alchemist. Which Minion ability do they have? They stay good and register as the Alchemist.`
- Night-1 step: `Alchemist — show "YOU ARE" then the Poisoner token. They do NOT wake with the Minions.`
- Granted step title: `Poisoner — via the Alchemist (Ana)`
- Granted step banner: `You may make them choose differently — shake your head and point at the Alchemist text.`
- Grimoire seat subtitle: `Alchemist · has the Poisoner ability`

### Data changes

- `characters.json:1209` — no ability-text change. Normalise
  `remindersGlobal` to `["Is the Alchemist"]`.
- `night_and_jinxes.json` — replace the Summoner jinx text with the current
  wording and add the seven missing Alchemist jinxes verbatim.
- `night_guide.json:836` — resolve the `"token": "pick"` card against
  `grantedAbilityId`; add a `"other"` entry that reads
  "The Alchemist has no step of their own on later nights — they wake at the
  \<Minion\>'s place in the order."

## Tests to add

`engine/src/test/kotlin/com/clocktower/engine/AlchemistTest.kt`

1. **Given** a seat with `characterId = "alchemist"`, `grantedAbilityId = "poisoner"`; **when** `NightOrder.firstNight(...)`; **then** the step list contains **both** an `alchemist` step at the Alchemist's index **and** a `poisoner` step at the Poisoner's index, both carrying that seat's id in `playerIds`.
2. Same seat, **when** `NightOrder.otherNight(...)`; **then** the list contains a `poisoner` step and **no** `alchemist` step.
3. **Given** an Alchemist-Poisoner; **then** `player.isEvil(lookup)` is `false` and `player.team(lookup) == Team.TOWNSFOLK`.
4. **Given** a 9-player game whose bag has an Alchemist and 9 seats and `grantedAbilityId = "baron"`; **when** `validateSetupState`; **then** it demands 2 Outsiders exactly as if a Baron were in the bag, and **passes** with 2 extra Outsiders / 2 fewer Townsfolk.
5. **Given** an Alchemist with `grantedAbilityId == null` at SETUP; **when** `validateSetupState`; **then** it reports "choose the Minion ability the Alchemist has".
6. **Given** an Alchemist and a 7+ player game; **when** `NightOrder.firstNight`; **then** the MINION_INFO step's `playerIds` does **not** include the Alchemist and its detail text does not name them.
7. **Given** an Alchemist-Summoner; **then** `validateBag` accepts a bag with **no** Demon, and `WinCheck.check` returns an advisory "evil wins — the Alchemist-Summoner died before summoning" when that seat dies with no Demon in play.
8. **Given** an Alchemist-Spy in play; **when** `GameData.activeJinxes` is asked for the in-play set; **then** the Alchemist-Spy jinx is returned even though `"spy"` is not any seat's `characterId`.
9. **Given** an Alchemist-Organ Grinder with both sober at setup; **when** setup completes; **then** both hold an `organgrinder:Drunk` reminder.
10. **Given** an Alchemist holding `poisoner:Poisoned`; **when** the granted Poisoner step is rendered; **then** `InfoCalc.impairments` / the step's caveats report the Alchemist as impaired so the ST knows the poison fails.
11. **Given** the Alchemist dies; **when** the next night is built; **then** the granted Minion row is present but flagged "all holders are dead — usually skip".
